/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.openforis.collectone.v2.server.service

import io.grpc.Status
import openforis.collectone.v1.survey.GetOrganizationRequest
import openforis.collectone.v1.survey.GetOrganizationResponse
import openforis.collectone.v1.survey.JoinOrganizationRequest
import openforis.collectone.v1.survey.JoinOrganizationResponse
import openforis.collectone.v1.survey.ListOrganizationsRequest
import openforis.collectone.v1.survey.ListOrganizationsResponse
import openforis.collectone.v1.survey.MembershipStatus
import openforis.collectone.v1.survey.MutateOrganizationRequest
import openforis.collectone.v1.survey.MutateOrganizationResponse
import openforis.collectone.v1.survey.Organization
import openforis.collectone.v1.survey.OrganizationMember
import openforis.collectone.v1.survey.OrganizationRole
import openforis.collectone.v1.survey.OrganizationServiceServer
import openforis.collectone.v1.survey.SearchOrganizationsRequest
import openforis.collectone.v1.survey.SearchOrganizationsResponse
import org.openforis.collectone.v2.core.mutation.OrganizationMutations
import org.openforis.collectone.v2.server.auth.Caller
import org.openforis.collectone.v2.server.auth.currentCaller
import org.openforis.collectone.v2.server.data.ListParams
import org.openforis.collectone.v2.server.data.SyncLists
import org.openforis.collectone.v2.server.data.UserDataStore
import org.openforis.collectone.v2.server.messaging.InviteNotifier
import org.openforis.collectone.v2.server.messaging.OrganizationInviteNotification

/** `OrganizationService`: the organization directory, memberships, and organization invitations. */
internal class OrganizationServiceImpl(
  private val store: UserDataStore,
  private val syncLists: SyncLists,
  private val inviteNotifier: InviteNotifier,
) : OrganizationServiceServer {

  override suspend fun GetOrganization(request: GetOrganizationRequest): GetOrganizationResponse {
    val caller = currentCaller()
    val org = store.read { r ->
      val org = r.organization(request.organization_id) ?: return@read null
      val access = Access(r, caller)
      when {
        access.belongsTo(org) -> access.viewOf(org)
        org.listed && !org.is_deleted -> org.copy(members = emptyList())
        else -> throw Access.denied("organization ${request.organization_id}")
      }
    }
    return GetOrganizationResponse(
      organization = org ?: Organization(organization_id = request.organization_id)
    )
  }

  override suspend fun SearchOrganizations(
    request: SearchOrganizationsRequest
  ): SearchOrganizationsResponse {
    val q = request.query.trim().lowercase()
    val matches =
      store
        .read { it.searchOrganizations(q) }
        .filter { org ->
          org.listed &&
            !org.is_deleted &&
            (q.isEmpty() ||
              org.name.lowercase().contains(q) ||
              org.description.lowercase().contains(q))
        }
        .sortedBy { it.name.lowercase() }
    val (items, next) = page(matches, request.page_size, request.page_token)
    return SearchOrganizationsResponse(organizations = items, next_page_token = next)
  }

  override suspend fun ListOrganizations(
    request: ListOrganizationsRequest
  ): ListOrganizationsResponse {
    val caller = currentCaller()
    val page = store.read { r ->
      val access = Access(r, caller)
      syncLists
        .list(
          params = ListParams(request.page_size, request.page_token, request.sync_token),
          scopeKey = "organizations",
          records = r.organizationCandidates(access, caller),
          commitTimestamp = Organization::commit_timestamp,
          id = Organization::organization_id,
          query = commitOrderQuery(Organization::commit_timestamp) { !it.is_deleted },
          queryFieldsSet = emptyList(),
          watermark = r.snapshotTimestamp,
          accessEpoch = r.accessEpochOf(caller),
          visible = { access.belongsTo(it) },
        )
        .let { it.copy(items = it.items.map(access::viewOf)) }
    }
    return ListOrganizationsResponse(
      organizations = page.items,
      next_page_token = page.nextPageToken,
      next_sync_token = page.nextSyncToken,
    )
  }

  override suspend fun MutateOrganization(
    request: MutateOrganizationRequest
  ): MutateOrganizationResponse {
    val orgId = request.organization_id
    val caller = currentCaller()
    val committed = store.write { tx ->
      val stored = tx.organization(orgId)
      if (stored != null && !Access(tx, caller).manages(stored)) {
        throw Access.denied("organization $orgId")
      }
      // Replays are recognized in the transaction that records new mutations as applied, so
      // concurrent attempts of one mutation apply it once.
      val toApply =
        tx.newMutations("organizations/$orgId", request.mutations) {
          MutationIdentity(it.client_id, it.client_seq)
        }
      if (toApply.isEmpty()) return@write null
      if (
        toApply.any {
          it.upsert?.organization_id?.isNotEmpty() == true &&
            it.upsert?.is_deleted != true &&
            it.delete == null
        }
      ) {
        checkBaseVersion(request.base_version, stored?.version, "organization $orgId")
      }
      var current: Organization = stored ?: Organization(organization_id = orgId, version = 0L)
      for (mutation in toApply) {
        val del = mutation.delete
        val up = mutation.upsert
        when {
          // The server owns the version; it's bumped once for the whole request below.
          up != null && up.organization_id.isNotEmpty() && !up.is_deleted && del == null ->
            current =
              up.copy(
                organization_id = orgId.ifBlank { up.organization_id },
                version = current.version,
              )
          else -> current = OrganizationMutations.apply(current, del, up)
        }
      }
      val oldEeSources = (stored?.imagery_sources ?: emptyList()).filter { it.earth_engine != null }
      val newEeSources = current.imagery_sources.filter { it.earth_engine != null }
      if (oldEeSources != newEeSources) {
        val access = Access(tx, caller)
        if (!access.isAdmin) {
          throw Status.PERMISSION_DENIED.withDescription(
              "Only platform administrators may configure Earth Engine imagery sources"
            )
            .asRuntimeException()
        }
      }
      for (source in newEeSources) {
        val ee = source.earth_engine
        val expr = ee?.expression
        if (ee == null || expr == null || expr.result.isBlank() || expr.values.isEmpty()) {
          throw Status.INVALID_ARGUMENT.withDescription(
              "Earth Engine expression must have a non-empty result and values map"
            )
            .asRuntimeException()
        }
      }
      // Every write bumps the version by exactly one, however many mutations the request has.
      current = current.copy(version = (stored?.version ?: 0L) + 1L)
      val access = AccessChanges()
      current = tx.putOrganizationTracked(current, access)
      tx.applyAccessChanges(access)
      tx.recordApplied(
        scope = "organizations/${current.organization_id}",
        identities = toApply.map { MutationIdentity(it.client_id, it.client_seq) },
      )
      val previousInviteTokensByEmail =
        stored
          ?.members
          .orEmpty()
          .filter { it.email.isNotBlank() }
          .associate { it.email.trim().lowercase() to it.invite_token }
      val newInvites =
        current.members
          .filter { member ->
            member.status == MembershipStatus.INVITED &&
              member.email.isNotBlank() &&
              member.invite_token.isNotBlank() &&
              previousInviteTokensByEmail[member.email.trim().lowercase()] != member.invite_token
          }
          .map { member ->
            val normalizedEmail = member.email.trim().lowercase()
            OrganizationInviteNotification(
              idempotencyKey =
                "org:${current.organization_id}:$normalizedEmail:${member.invite_token}",
              organizationId = current.organization_id,
              organizationName = current.name,
              recipientEmail = member.email.trim(),
              role = member.role,
              inviteToken = member.invite_token,
              inviterEmail = caller?.email.orEmpty(),
              inviterDisplayName = caller?.displayName.orEmpty(),
            )
          }
      Access(tx, caller).viewOf(current) to newInvites
    }
    // Every mutation is a replay: report the current state.
    val (updatedOrg, newOrgInvites) = committed.value ?: return currentOrganization(orgId, caller)
    val organization = updatedOrg.copy(commit_timestamp = committed.commitTimestamp)
    if (newOrgInvites.isNotEmpty()) {
      inviteNotifier.notifyOrganizationInvites(newOrgInvites)
    }
    return MutateOrganizationResponse(
      organization_id = organization.organization_id,
      organization = organization,
      commit_timestamp = committed.commitTimestamp,
    )
  }

  /**
   * The organization's current state, as `MutateOrganization` reports it when it changes nothing.
   */
  private suspend fun currentOrganization(
    orgId: String,
    caller: Caller?,
  ): MutateOrganizationResponse {
    // Return the caller's view (see Access.viewOf), so administrators keep their implicit Manager
    // membership in the client's copy instead of losing it on every write.
    val existing = store.read { r -> r.organization(orgId)?.let { Access(r, caller).viewOf(it) } }
    return MutateOrganizationResponse(
      organization_id = orgId,
      organization = existing,
      commit_timestamp = existing?.commit_timestamp ?: store.watermark(),
    )
  }

  override suspend fun JoinOrganization(
    request: JoinOrganizationRequest
  ): JoinOrganizationResponse {
    if (store.read { it.organization(request.organization_id) } == null) {
      return JoinOrganizationResponse(organization = null, commit_timestamp = store.watermark())
    }
    val authenticated = currentCaller()
    val caller = authenticated?.takeUnless { it.isDefault }
    val email = caller?.email?.takeIf { it.isNotBlank() } ?: request.email
    require(email.isNotBlank()) { "email is required" }
    val userId = caller?.userId ?: request.user_id
    val token = request.invite_token
    // Invites are bound to the invited email, so accepting one needs a verified email.
    if (token.isNotBlank() && caller != null && !caller.emailVerified) {
      throw Access.denied("invite to organization ${request.organization_id}")
    }
    val committed = store.write { tx ->
      val current = checkNotNull(tx.organization(request.organization_id))
      val existing = current.members.firstOrNull { it.email.equals(email, ignoreCase = true) }
      val newMember =
        when {
          // Joining again never downgrades an active membership.
          existing?.status == MembershipStatus.ACTIVE -> null
          token.isNotBlank() -> {
            // Only a pending invite with this exact token, sent to this email, grants membership,
            // and it grants the role the Manager invited them with.
            val invite =
              existing?.takeIf {
                it.status == MembershipStatus.INVITED &&
                  it.invite_token.isNotBlank() &&
                  it.invite_token == token
              } ?: throw Access.denied("invite to organization ${request.organization_id}")
            invite.copy(user_id = userId, status = MembershipStatus.ACTIVE, invite_token = "")
          }
          else ->
            OrganizationMember(
              user_id = userId,
              email = email,
              role = OrganizationRole.MEMBER,
              status = MembershipStatus.REQUESTED,
            )
        }
      val written =
        if (newMember == null) {
          current
        } else {
          val updated =
            current.copy(
              members =
                current.members.filterNot { it.email.equals(email, ignoreCase = true) } + newMember,
              version = current.version + 1L,
            )
          val access = AccessChanges()
          tx.putOrganizationTracked(updated, access).also { tx.applyAccessChanges(access) }
        }
      // Return only what the caller may see (see Access.viewOf): a pending requester gets their
      // own membership, not the organization's member list.
      Access(tx, authenticated).viewOf(written) to (newMember ?: existing)
    }
    val (organization, member) = committed.value
    return JoinOrganizationResponse(
      organization = organization.copy(commit_timestamp = committed.commitTimestamp),
      member = member,
      commit_timestamp = committed.commitTimestamp,
    )
  }
}
