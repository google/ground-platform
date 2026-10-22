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
package org.openforis.collectone.v2.server.userdata

import com.squareup.wire.Instant
import io.grpc.Status
import io.grpc.StatusException
import openforis.collectone.v1.data.AcquirePlotLockRequest
import openforis.collectone.v1.data.AcquirePlotLockResponse
import openforis.collectone.v1.data.AuditInfo
import openforis.collectone.v1.data.AuditRecord
import openforis.collectone.v1.data.ConflictResolutionStatus
import openforis.collectone.v1.data.DataMutation
import openforis.collectone.v1.data.DataMutationResult
import openforis.collectone.v1.data.DataServiceServer
import openforis.collectone.v1.data.EntityCluster
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.GetAppliedMutationsRequest
import openforis.collectone.v1.data.GetAppliedMutationsResponse
import openforis.collectone.v1.data.GetEntityRequest
import openforis.collectone.v1.data.GetEntityResponse
import openforis.collectone.v1.data.GetImpactAggregatesRequest
import openforis.collectone.v1.data.GetImpactAggregatesResponse
import openforis.collectone.v1.data.GetMediaDownloadUrlRequest
import openforis.collectone.v1.data.GetMediaDownloadUrlResponse
import openforis.collectone.v1.data.GetSubmissionRequest
import openforis.collectone.v1.data.GetSubmissionResponse
import openforis.collectone.v1.data.ImpactAggregate
import openforis.collectone.v1.data.ImpactEvent as ImpactEventProto
import openforis.collectone.v1.data.ListAuditRecordsRequest
import openforis.collectone.v1.data.ListAuditRecordsResponse
import openforis.collectone.v1.data.ListEntitiesRequest
import openforis.collectone.v1.data.ListEntitiesResponse
import openforis.collectone.v1.data.ListEntityClustersRequest
import openforis.collectone.v1.data.ListEntityClustersResponse
import openforis.collectone.v1.data.ListImpactEventsRequest
import openforis.collectone.v1.data.ListImpactEventsResponse
import openforis.collectone.v1.data.ListMutationLogsRequest
import openforis.collectone.v1.data.ListMutationLogsResponse
import openforis.collectone.v1.data.ListPlacesRequest
import openforis.collectone.v1.data.ListPlacesResponse
import openforis.collectone.v1.data.ListSubmissionsRequest
import openforis.collectone.v1.data.ListSubmissionsResponse
import openforis.collectone.v1.data.MutateDataRequest
import openforis.collectone.v1.data.MutateDataResponse
import openforis.collectone.v1.data.MutationLogRecord
import openforis.collectone.v1.data.PlotLock
import openforis.collectone.v1.data.PrepareMediaUploadRequest
import openforis.collectone.v1.data.PrepareMediaUploadResponse
import openforis.collectone.v1.data.QueueFilterMode
import openforis.collectone.v1.data.RecordImpactEventsRequest
import openforis.collectone.v1.data.RecordImpactEventsResponse
import openforis.collectone.v1.data.ReleasePlotLockRequest
import openforis.collectone.v1.data.ReleasePlotLockResponse
import openforis.collectone.v1.data.SubmissionRecord
import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.GeoPoint
import openforis.collectone.v1.library.ConceptDef
import openforis.collectone.v1.library.ExportProfileDef
import openforis.collectone.v1.library.FormTemplateDef
import openforis.collectone.v1.library.GetLibraryRequest
import openforis.collectone.v1.library.GetLibraryResponse
import openforis.collectone.v1.library.GetResolvedLibraryRequest
import openforis.collectone.v1.library.GetResolvedLibraryResponse
import openforis.collectone.v1.library.LibraryBundle
import openforis.collectone.v1.library.LibraryServiceServer
import openforis.collectone.v1.library.ListLibrariesRequest
import openforis.collectone.v1.library.ListLibrariesResponse
import openforis.collectone.v1.library.MutateLibraryRequest
import openforis.collectone.v1.library.MutateLibraryResponse
import openforis.collectone.v1.library.OrganizationLibraryRecord
import openforis.collectone.v1.library.OrganizationLibrarySettings
import openforis.collectone.v1.library.PurposePackDef
import openforis.collectone.v1.library.SearchConceptsRequest
import openforis.collectone.v1.library.SearchConceptsResponse
import openforis.collectone.v1.survey.AcceptSurveyInviteRequest
import openforis.collectone.v1.survey.AcceptSurveyInviteResponse
import openforis.collectone.v1.survey.AclEntry
import openforis.collectone.v1.survey.BatchGetSurveyOverviewsRequest
import openforis.collectone.v1.survey.BatchGetSurveyOverviewsResponse
import openforis.collectone.v1.survey.CachedProfile
import openforis.collectone.v1.survey.GetOrganizationRequest
import openforis.collectone.v1.survey.GetOrganizationResponse
import openforis.collectone.v1.survey.GetSurveyAclRequest
import openforis.collectone.v1.survey.GetSurveyAclResponse
import openforis.collectone.v1.survey.GetSurveyRequest
import openforis.collectone.v1.survey.GetSurveyResponse
import openforis.collectone.v1.survey.JoinOrganizationRequest
import openforis.collectone.v1.survey.JoinOrganizationResponse
import openforis.collectone.v1.survey.ListOrganizationsRequest
import openforis.collectone.v1.survey.ListOrganizationsResponse
import openforis.collectone.v1.survey.ListSurveyOverviewsRequest
import openforis.collectone.v1.survey.ListSurveyOverviewsResponse
import openforis.collectone.v1.survey.MembershipStatus
import openforis.collectone.v1.survey.MutateOrganizationRequest
import openforis.collectone.v1.survey.MutateOrganizationResponse
import openforis.collectone.v1.survey.MutateSurveyRequest
import openforis.collectone.v1.survey.MutateSurveyResponse
import openforis.collectone.v1.survey.Organization
import openforis.collectone.v1.survey.OrganizationMember
import openforis.collectone.v1.survey.OrganizationRole
import openforis.collectone.v1.survey.OrganizationServiceServer
import openforis.collectone.v1.survey.PeerDataVisibility
import openforis.collectone.v1.survey.QuotaLimits
import openforis.collectone.v1.survey.QuotaTier
import openforis.collectone.v1.survey.Role
import openforis.collectone.v1.survey.SearchOrganizationsRequest
import openforis.collectone.v1.survey.SearchOrganizationsResponse
import openforis.collectone.v1.survey.SearchPublicSurveysRequest
import openforis.collectone.v1.survey.SearchPublicSurveysResponse
import openforis.collectone.v1.survey.SharingPolicy
import openforis.collectone.v1.survey.SurveyAcl
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyOverview
import openforis.collectone.v1.survey.SurveyOverviewResult
import openforis.collectone.v1.survey.SurveyServiceServer
import openforis.collectone.v1.survey.SurveyState
import org.openforis.collectone.v2.core.mutation.DataPatches
import org.openforis.collectone.v2.core.mutation.LibraryPatches
import org.openforis.collectone.v2.core.mutation.OrganizationPatches
import org.openforis.collectone.v2.core.mutation.SurveyPatches

/**
 * The user data services ([SurveyServiceServer], [OrganizationServiceServer],
 * [LibraryServiceServer], and [DataServiceServer]) over a [UserDataStore]. The backend owns the
 * business rules; the store ([SpannerUserDataStore]) reads and writes records atomically.
 *
 * Implements the server-side contract defined in `docs/technical/sync-engine.md`:
 * - Monotonically increasing authoritative `commit_timestamp` assigned to every initial record and
 *   every subsequent mutation.
 * - Record versions that strictly increase on every write to a record, which clients compare to
 *   tell which copy is newer: `revision` for entities and submissions (including deletes and plot
 *   lock changes), `version` for organizations and libraries (one per request), and the survey's
 *   shared `record_version` on its saved revision, working copy, ACL, and overview.
 * - Unary `List*` sync RPCs with query and delta modes selected by an opaque `sync_token`
 *   ([SyncLists]). Delta mode returns every record in scope committed after the token, ordered by
 *   `(commit_timestamp, id)` and including tombstones; query mode filters, sorts, and pages a
 *   snapshot and ends with a `next_sync_token` for the scope. Tokens are bound to the store's data
 *   epoch ([UserDataStore.dataEpoch]), so tokens from before a reset fail with
 *   `FAILED_PRECONDITION` and clients run a full sync.
 * - `SearchOrganizations` directory queries with offset-based paging.
 * - Soft-delete tombstones (`is_deleted = true` or `state = DELETED`), returned in delta mode and
 *   in query mode only when `include_deleted = true`.
 * - Idempotent mutations: the highest applied `client_seq` per `(client_id, scope)` is stored with
 *   the changes and reported by `GetAppliedMutations`. A mutation at or below it is a replay and is
 *   skipped, so a stale retry can't overwrite newer writes. Clients send a scope's mutations in
 *   `client_seq` order; mutations without a `client_id` aren't deduplicated.
 * - Last-writer-wins field patches. `base_revision` / `base_version` only guard whole-record
 *   replacements (`upsert_*`), which fail with `FAILED_PRECONDITION` when stale; stale field
 *   patches on data records report `AUTO_MERGED`.
 * - Atomic `Mutate*` requests: a request that fails leaves no partial changes.
 * - 5-minute cooperative plot locks.
 */
class UserDataBackend(private val store: UserDataStore) :
  SurveyServiceServer, OrganizationServiceServer, LibraryServiceServer, DataServiceServer {

  private val syncLists = SyncLists { store.dataEpoch }

  /** Current highest committed server timestamp watermark. */
  fun currentWatermark(): Instant = store.watermark()

  /** Identity of one client mutation, used for replay detection and `client_seq` tracking. */
  private data class MutationIdentity(val clientId: String, val clientSeq: Long)

  /**
   * Recognizes replays in [scope]: a mutation is one when its `client_seq` is at or below its
   * client's watermark, or below a mutation earlier in the same request.
   */
  private class ReplayFilter(private val reader: UserDataReader, private val scope: String) {
    private val highest = HashMap<String, Long>()

    /** Whether [identity] is a replay. If it isn't, it raises its client's watermark here. */
    fun isReplay(identity: MutationIdentity): Boolean {
      if (identity.clientId.isBlank() || identity.clientSeq <= 0L) return false
      val watermark =
        highest.getOrPut(identity.clientId) {
          reader.appliedWatermarks(identity.clientId).firstOrNull { it.scope == scope }?.client_seq
            ?: 0L
        }
      if (identity.clientSeq <= watermark) return true
      highest[identity.clientId] = identity.clientSeq
      return false
    }
  }

  /** The [mutations] that aren't replays in [scope]. */
  private fun <T> UserDataReader.newMutations(
    scope: String,
    mutations: List<T>,
    identityOf: (T) -> MutationIdentity,
  ): List<T> {
    val replays = ReplayFilter(this, scope)
    return mutations.filterNot { replays.isReplay(identityOf(it)) }
  }

  /**
   * Fails with `FAILED_PRECONDITION` (via [IllegalStateException]) when a whole-record replacement
   * was based on a stale version. Unset base versions (`0` / blank) and new records always pass.
   */
  private fun checkBaseVersion(baseVersion: Long, currentVersion: Long?, what: String) {
    if (baseVersion == 0L || currentVersion == null) return
    check(baseVersion == currentVersion) {
      "Stale base version for $what: based on $baseVersion, current is $currentVersion"
    }
  }

  private fun checkBaseVersion(baseVersion: String, currentVersion: String?, what: String) {
    if (baseVersion.isBlank() || currentVersion == null) return
    check(baseVersion == currentVersion) {
      "Stale base version for $what: based on $baseVersion, current is $currentVersion"
    }
  }

  /**
   * The next shared record version of survey [surveyId]: one more than the highest version on its
   * saved revision, working copy, and ACL (`SurveyDef.record_version`).
   */
  private fun UserDataReader.nextSurveyRecordVersion(surveyId: String): Long =
    maxOf(
      survey(surveyId)?.record_version ?: 0L,
      workingCopy(surveyId)?.record_version ?: 0L,
      acl(surveyId)?.record_version ?: 0L,
    ) + 1L

  /**
   * Records [identities] as applied in [scope], in the same transaction as the changes they made,
   * so a failed request can be retried.
   */
  private fun UserDataWriter.recordApplied(scope: String, identities: List<MutationIdentity>) {
    identities
      .filter { it.clientId.isNotBlank() && it.clientSeq > 0L }
      .groupBy { it.clientId }
      .forEach { (clientId, ids) -> recordApplied(scope, clientId, ids.maxOf { it.clientSeq }) }
  }

  /** Resets all state to the store's initial data and starts a new data epoch. */
  fun reset() = store.reset()

  /**
   * Registers [caller] before its RPC: on its first call, or when its email, verification, or
   * profile changed, upserts its [UserAccount]. For a verified email, it also binds ACL entries and
   * organization invites addressed to that email to the caller's user ID, which changes the
   * caller's access (so its access epoch increases).
   */
  suspend fun registerCaller(caller: Caller) {
    val email = caller.normalizedEmail
    val profile = CachedProfile(display_name = caller.displayName, avatar_url = caller.photoUrl)
    val existing = store.read { it.user(caller.userId) }
    if (
      existing != null &&
        existing.email == email &&
        existing.emailVerified == caller.emailVerified &&
        existing.profile == profile
    ) {
      return
    }
    store.write { tx ->
      val access = AccessChanges()
      if (caller.emailVerified && email.isNotEmpty()) tx.bindInvites(caller.userId, email, access)
      val user =
        (tx.user(caller.userId) ?: UserAccount(caller.userId, createTime = tx.now)).copy(
          email = email,
          emailVerified = caller.emailVerified,
          profile = profile,
        )
      tx.applyAccessChanges(access, pending = user)
    }
  }

  /** Sets [userId] on unbound ACL entries and organization invites addressed to [email]. */
  private fun UserDataWriter.bindInvites(userId: String, email: String, access: AccessChanges) {
    fun unbound(entryEmail: String, entryUserId: String) =
      entryUserId.isBlank() && entryEmail.trim().lowercase() == email
    for (surveyId in surveyIdsWithAclEmail(email)) {
      val acl = acl(surveyId) ?: continue
      if (acl.entries.none { unbound(it.email, it.user_id) }) continue
      val entries =
        acl.entries.map { if (unbound(it.email, it.user_id)) it.copy(user_id = userId) else it }
      putAclWithRecordVersion(surveyId, acl.copy(entries = entries), access)
    }
    for (organizationId in organizationIdsWithMemberEmail(email)) {
      val org = organization(organizationId) ?: continue
      if (org.members.none { unbound(it.email, it.user_id) }) continue
      val members =
        org.members.map { if (unbound(it.email, it.user_id)) it.copy(user_id = userId) else it }
      putOrganizationTracked(org.copy(members = members, version = org.version + 1L), access)
    }
  }

  /** Users whose ACL entries or memberships a transaction changes, by user ID or email. */
  private class AccessChanges {
    val userIds = HashSet<String>()
    val emails = HashSet<String>()

    /**
     * Records the principals whose grant differs between [before] and [after], each a list of (user
     * ID, email, grant) triples.
     */
    fun record(
      before: List<Triple<String, String, Any>>,
      after: List<Triple<String, String, Any>>,
    ) {
      fun key(userId: String, email: String) = email.trim().lowercase().ifEmpty { "uid:$userId" }
      val old = before.associateBy { key(it.first, it.second) }
      val new = after.associateBy { key(it.first, it.second) }
      for (k in old.keys + new.keys) {
        val o = old[k]
        val n = new[k]
        if (o == n) continue
        listOfNotNull(o, n).forEach { (userId, email) ->
          if (userId.isNotBlank()) userIds += userId
          if (email.isNotBlank()) emails += email.trim().lowercase()
        }
      }
    }
  }

  /**
   * Increases the access epoch of every user [changes] names, and writes [pending] (a user being
   * registered in the same transaction).
   */
  private fun UserDataWriter.applyAccessChanges(
    changes: AccessChanges,
    pending: UserAccount? = null,
  ) {
    val affected = LinkedHashMap<String, UserAccount>()
    fun current(user: UserAccount) = if (user.userId == pending?.userId) pending else user
    changes.userIds.forEach { id ->
      val user = if (id == pending?.userId) pending else user(id)
      user?.let { affected[it.userId] = it }
    }
    changes.emails.forEach { email ->
      usersByEmail(email).forEach { affected.putIfAbsent(it.userId, current(it)) }
      if (pending != null && pending.email == email) affected.putIfAbsent(pending.userId, pending)
    }
    affected.values.forEach { putUser(it.copy(accessEpoch = it.accessEpoch + 1L)) }
    if (pending != null && pending.userId !in affected) putUser(pending)
  }

  /** The single verified user with normalized email [email], if known. */
  private fun UserDataReader.verifiedUserByEmail(email: String): UserAccount? =
    email
      .trim()
      .lowercase()
      .takeIf { it.isNotEmpty() }
      ?.let { e -> usersByEmail(e).filter { it.emailVerified }.singleOrNull() }

  /**
   * Writes [updated], binding invites for known verified users and recording membership changes in
   * [access]. Returns the written organization.
   */
  private fun UserDataWriter.putOrganizationTracked(
    updated: Organization,
    access: AccessChanges,
  ): Organization {
    val bound =
      updated.copy(
        members =
          updated.members.map { m ->
            if (m.user_id.isBlank())
              verifiedUserByEmail(m.email)?.let { m.copy(user_id = it.userId) } ?: m
            else m
          }
      )
    val before = organization(updated.organization_id)?.members.orEmpty()
    access.record(
      before.map { Triple(it.user_id, it.email, it.role to it.status) },
      bound.members.map { Triple(it.user_id, it.email, it.role to it.status) },
    )
    putOrganization(bound)
    return bound
  }

  /** Like [putOrganizationTracked], for a survey's ACL entries. */
  private fun UserDataWriter.putAclTracked(updated: SurveyAcl, access: AccessChanges): SurveyAcl {
    val bound =
      updated.copy(
        entries =
          updated.entries.map { e ->
            if (e.user_id.isBlank())
              verifiedUserByEmail(e.email)?.let { e.copy(user_id = it.userId) } ?: e
            else e
          }
      )
    val before = acl(updated.survey_id)?.entries.orEmpty()
    access.record(
      before.map { Triple(it.user_id, it.email, it.role) },
      bound.entries.map { Triple(it.user_id, it.email, it.role) },
    )
    putAcl(bound)
    return bound
  }

  /**
   * Writes [acl] for survey [surveyId] with the survey's next shared record version, restamping its
   * saved revision and working copy. Returns the written saved revision and ACL.
   */
  private fun UserDataWriter.putAclWithRecordVersion(
    surveyId: String,
    acl: SurveyAcl,
    access: AccessChanges,
  ): Pair<SurveyDef?, SurveyAcl> {
    val recordVersion = nextSurveyRecordVersion(surveyId)
    val written = putAclTracked(acl.copy(record_version = recordVersion), access)
    val saved = survey(surveyId)?.copy(record_version = recordVersion)
    saved?.let { putSurvey(it) }
    workingCopy(surveyId)?.let { wc ->
      putWorkingCopy(surveyId, wc.copy(record_version = recordVersion))
    }
    return saved to written
  }

  /**
   * The caller's access epoch, carried by caller-dependent list tokens, plus the caller's identity
   * and whether the authenticator made it an administrator (so signing in or switching a simulated
   * role resyncs); empty without a caller.
   */
  private fun UserDataReader.accessEpochOf(caller: Caller?): String {
    caller ?: return ""
    val epoch = user(caller.userId)?.accessEpoch?.toString().orEmpty()
    val role = if (caller.isAdmin || caller.isDefault) "+admin" else ""
    return "${caller.userId}:$epoch$role"
  }

  /**
   * The user ID of the authenticated caller, which takes precedence over [requested] request
   * fields; [requested] for the development fallback caller.
   */
  private suspend fun callerUserId(requested: String): String =
    currentCaller()?.takeUnless { it.isDefault }?.userId ?: requested

  /**
   * Purges surveys whose `trash_time` is strictly before [olderThan] (30 days ago in the daily
   * job): removes their data and replaces each with a permanent `DELETED` tombstone so downloaded
   * copies receive a tombstone on their next delta. Returns the IDs of the purged surveys.
   */
  fun purgeTrashedSurveys(olderThan: Instant): List<String> =
    store
      .write { tx ->
        tx
          .surveys()
          .filter { s ->
            val trashedAt = s.trash_time
            s.state != SurveyState.DELETED && trashedAt != null && trashedAt < olderThan
          }
          .map { s ->
            tx.purgeSurvey(
              SurveyDef(
                survey_id = s.survey_id,
                organization_id = s.organization_id,
                owner_id = s.owner_id,
                state = SurveyState.DELETED,
                record_version = tx.nextSurveyRecordVersion(s.survey_id),
              )
            )
            s.survey_id
          }
      }
      .value

  // ---------------------------------------------------------------------------
  // Timestamp comparison and paging helpers (sync-engine.md)
  // ---------------------------------------------------------------------------

  private val instantComparator =
    Comparator<Instant?> { a, b ->
      when {
        a == null && b == null -> 0
        a == null -> -1
        b == null -> 1
        a.epochSecond != b.epochSecond -> a.epochSecond.compareTo(b.epochSecond)
        else -> a.nano.compareTo(b.nano)
      }
    }

  /** Applies `page_size` / `page_token` offset paging to an already filtered and sorted list. */
  private fun <T> page(items: List<T>, pageSize: Int, pageToken: String): Pair<List<T>, String> {
    val offset = pageToken.toIntOrNull() ?: 0
    if (pageSize <= 0) return items.drop(offset) to ""
    val end = minOf(offset + pageSize, items.size)
    val next = if (end < items.size) end.toString() else ""
    return items.subList(minOf(offset, items.size), end) to next
  }

  /** Query that matches [matches] in commit order (the default for full syncs). */
  private fun <T> commitOrderQuery(
    commitTimestamp: (T) -> Instant?,
    matches: (T) -> Boolean = { true },
  ) =
    QuerySpec(
      matches = matches,
      sortKey = { record: T -> SyncLists.timestampSortKey(commitTimestamp(record)) },
    )

  /**
   * Query sorted by `order_by`: [stringKeys] maps sortable field names to string sort keys;
   * `commit_timestamp` (the default) is always supported. Unknown fields fall back to commit order.
   */
  private fun <T> orderedQuery(
    orderBy: String,
    commitTimestamp: (T) -> Instant?,
    stringKeys: Map<String, (T) -> String>,
    fingerprint: String,
    matches: (T) -> Boolean,
  ): QuerySpec<T> {
    val parts = orderBy.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val field = parts.firstOrNull().orEmpty()
    val descending = parts.getOrNull(1) == "desc"
    val key: (T) -> String =
      stringKeys[field] ?: { record -> SyncLists.timestampSortKey(commitTimestamp(record)) }
    return QuerySpec(matches, key, descending, fingerprint)
  }

  // ---------------------------------------------------------------------------
  // OrganizationServiceServer
  // ---------------------------------------------------------------------------

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
    val (existing, toApply) =
      store.read { r ->
        val org = r.organization(orgId)
        if (org != null && !Access(r, caller).manages(org)) {
          throw Access.denied("organization $orgId")
        }
        org to
          r.newMutations("organizations/$orgId", request.mutations) {
            MutationIdentity(it.client_id, it.client_seq)
          }
      }
    if (toApply.isEmpty()) {
      return MutateOrganizationResponse(
        organization_id = orgId,
        organization = existing,
        commit_timestamp = existing?.commit_timestamp ?: store.watermark(),
      )
    }
    if (toApply.any { it.upsert?.organization_id?.isNotEmpty() == true && it.delete == null }) {
      checkBaseVersion(request.base_version, existing?.version, "organization $orgId")
    }

    val committed = store.write { tx ->
      val stored = tx.organization(orgId)
      if (stored != null && !Access(tx, caller).manages(stored)) {
        throw Access.denied("organization $orgId")
      }
      var current: Organization = stored ?: Organization(organization_id = orgId, version = 0L)
      for (mutation in toApply) {
        val del = mutation.delete
        val up = mutation.upsert
        when {
          mutation.delete_organization || OrganizationPatches.isRootDelete(del, up) ->
            current = current.copy(is_deleted = true)
          // The server owns the version; it's bumped once for the whole request below.
          up != null && up.organization_id.isNotEmpty() && del == null ->
            current =
              up.copy(
                organization_id = orgId.ifBlank { up.organization_id },
                version = current.version,
              )
          else -> current = OrganizationPatches.apply(current, del, up)
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
      current
    }
    val organization = committed.value.copy(commit_timestamp = committed.commitTimestamp)
    return MutateOrganizationResponse(
      organization_id = organization.organization_id,
      organization = organization,
      commit_timestamp = committed.commitTimestamp,
    )
  }

  override suspend fun JoinOrganization(
    request: JoinOrganizationRequest
  ): JoinOrganizationResponse {
    if (store.read { it.organization(request.organization_id) } == null) {
      return JoinOrganizationResponse(organization = null, commit_timestamp = store.watermark())
    }
    val caller = currentCaller()?.takeUnless { it.isDefault }
    val email = caller?.email?.takeIf { it.isNotBlank() } ?: request.email
    require(email.isNotBlank()) { "email is required" }
    val status =
      if (request.invite_token.isNotBlank()) MembershipStatus.ACTIVE else MembershipStatus.REQUESTED
    val newMember =
      OrganizationMember(
        user_id = caller?.userId ?: request.user_id,
        email = email,
        role = OrganizationRole.MEMBER,
        status = status,
        invite_token = request.invite_token,
      )
    val committed = store.write { tx ->
      val current = checkNotNull(tx.organization(request.organization_id))
      val updated =
        current.copy(
          members =
            current.members.filterNot { it.email.equals(email, ignoreCase = true) } + newMember,
          version = current.version + 1L,
        )
      val access = AccessChanges()
      val written = tx.putOrganizationTracked(updated, access)
      tx.applyAccessChanges(access)
      written
    }
    return JoinOrganizationResponse(
      organization = committed.value.copy(commit_timestamp = committed.commitTimestamp),
      member = newMember,
      commit_timestamp = committed.commitTimestamp,
    )
  }

  // ---------------------------------------------------------------------------
  // LibraryServiceServer
  // ---------------------------------------------------------------------------

  /** Fails with `PERMISSION_DENIED` unless the caller can read [organizationId]'s library. */
  private suspend fun requireLibraryReader(organizationId: String) {
    val caller = currentCaller()
    store.read { r ->
      if (!Access(r, caller).readsLibrary(organizationId)) {
        throw Access.denied("library $organizationId")
      }
    }
  }

  override suspend fun GetLibrary(request: GetLibraryRequest): GetLibraryResponse {
    requireLibraryReader(request.organization_id)
    val record =
      store.read { it.library(request.organization_id) }
        ?: OrganizationLibraryRecord(
          organization_id = request.organization_id,
          bundle = LibraryBundle(),
          settings = OrganizationLibrarySettings(organization_id = request.organization_id),
          version = 1L,
          commit_timestamp = store.watermark(),
        )
    return GetLibraryResponse(library = record)
  }

  override suspend fun GetResolvedLibrary(
    request: GetResolvedLibraryRequest
  ): GetResolvedLibraryResponse {
    requireLibraryReader(request.organization_id)
    val (globalRecord, orgRecord) =
      store.read { r ->
        r.library(ALL_USERS_ORGANIZATION_ID) to
          if (request.organization_id.isNotBlank()) r.library(request.organization_id) else null
      }
    val mergedConcepts = LinkedHashMap<String, ConceptDef>()
    val mergedTemplates = LinkedHashMap<String, FormTemplateDef>()
    val mergedPacks = LinkedHashMap<String, PurposePackDef>()
    val mergedProfiles = LinkedHashMap<String, ExportProfileDef>()

    globalRecord?.bundle?.let { b ->
      b.concepts.forEach { mergedConcepts[it.id] = it }
      b.form_templates.forEach { mergedTemplates[it.id] = it }
      b.purpose_packs.forEach { mergedPacks[it.id] = it }
      b.export_profiles.forEach { mergedProfiles[it.id] = it }
    }
    orgRecord?.bundle?.let { b ->
      b.concepts.forEach { mergedConcepts[it.id] = it }
      b.form_templates.forEach { mergedTemplates[it.id] = it }
      b.purpose_packs.forEach { mergedPacks[it.id] = it }
      b.export_profiles.forEach { mergedProfiles[it.id] = it }
    }
    return GetResolvedLibraryResponse(
      resolved_library =
        OrganizationLibraryRecord(
          organization_id = request.organization_id,
          bundle =
            LibraryBundle(
              concepts = mergedConcepts.values.toList(),
              form_templates = mergedTemplates.values.toList(),
              purpose_packs = mergedPacks.values.toList(),
              export_profiles = mergedProfiles.values.toList(),
            ),
          settings = orgRecord?.settings ?: globalRecord?.settings,
          version = orgRecord?.version ?: 1L,
          commit_timestamp = store.watermark(),
        )
    )
  }

  override suspend fun ListLibraries(request: ListLibrariesRequest): ListLibrariesResponse {
    val orgId = request.organization_id
    val caller = currentCaller()
    val page = store.read { r ->
      val access = Access(r, caller)
      syncLists.list(
        params = ListParams(request.page_size, request.page_token, request.sync_token),
        scopeKey = "libraries|organization_id=$orgId",
        records = r.libraries().filter { orgId.isBlank() || it.organization_id == orgId },
        commitTimestamp = OrganizationLibraryRecord::commit_timestamp,
        id = OrganizationLibraryRecord::organization_id,
        query = commitOrderQuery(OrganizationLibraryRecord::commit_timestamp) { !it.is_deleted },
        queryFieldsSet = emptyList(),
        watermark = r.snapshotTimestamp,
        accessEpoch = r.accessEpochOf(caller),
        visible = { access.readsLibrary(it.organization_id) },
      )
    }
    return ListLibrariesResponse(
      libraries = page.items,
      next_page_token = page.nextPageToken,
      next_sync_token = page.nextSyncToken,
    )
  }

  override suspend fun SearchConcepts(request: SearchConceptsRequest): SearchConceptsResponse {
    requireLibraryReader(request.organization_id)
    val q = request.query.trim().lowercase()
    // The global library, overridden by the organization's (as in GetResolvedLibrary).
    val libraries =
      listOf(ALL_USERS_ORGANIZATION_ID) +
        listOfNotNull(request.organization_id.takeIf { it.isNotBlank() })
    val matches =
      store
        .read { it.searchConcepts(libraries, q) }
        .filter { c ->
          (request.data_type == DataType.DATA_TYPE_UNSPECIFIED ||
            c.data_type == request.data_type) &&
            (q.isEmpty() ||
              c.id.lowercase().contains(q) ||
              c.label?.values?.values?.any { it.lowercase().contains(q) } == true)
        }
    return SearchConceptsResponse(concepts = matches)
  }

  override suspend fun MutateLibrary(request: MutateLibraryRequest): MutateLibraryResponse {
    val orgId = request.organization_id
    val caller = currentCaller()
    val (existing, toApply) =
      store.read { r ->
        if (!Access(r, caller).manages(r.organization(orgId))) {
          throw Access.denied("library $orgId")
        }
        r.library(orgId) to
          r.newMutations("libraries/$orgId", request.mutations) {
            MutationIdentity(it.client_id, it.client_seq)
          }
      }
    if (toApply.isEmpty()) {
      return MutateLibraryResponse(
        organization_id = orgId,
        library = existing,
        commit_timestamp = existing?.commit_timestamp ?: store.watermark(),
      )
    }
    if (toApply.any { it.upsert?.organization_id?.isNotEmpty() == true && it.delete == null }) {
      checkBaseVersion(request.base_version, existing?.version, "library $orgId")
    }

    val committed = store.write { tx ->
      val stored = tx.library(orgId)
      var current =
        stored
          ?: OrganizationLibraryRecord(
            organization_id = orgId,
            bundle = LibraryBundle(),
            settings = OrganizationLibrarySettings(organization_id = orgId),
            version = 0L,
          )
      for (mutation in toApply) {
        val delLib = mutation.delete
        val upLib = mutation.upsert
        if (upLib != null && upLib.organization_id.isNotEmpty() && delLib == null) {
          // The server owns the version; it's bumped once for the whole request below.
          current = upLib.copy(organization_id = orgId, version = current.version)
        } else {
          current = LibraryPatches.apply(current, delLib, upLib)
        }
      }
      // Every write bumps the version by exactly one, however many mutations the request has.
      current = current.copy(version = (stored?.version ?: 0L) + 1L)
      tx.putLibrary(current)
      tx.recordApplied(
        scope = "libraries/$orgId",
        identities = toApply.map { MutationIdentity(it.client_id, it.client_seq) },
      )
      current
    }
    return MutateLibraryResponse(
      organization_id = orgId,
      library = committed.value.copy(commit_timestamp = committed.commitTimestamp),
      commit_timestamp = committed.commitTimestamp,
    )
  }

  // ---------------------------------------------------------------------------
  // SurveyServiceServer
  // ---------------------------------------------------------------------------

  override suspend fun ListSurveyOverviews(
    request: ListSurveyOverviewsRequest
  ): ListSurveyOverviewsResponse {
    val orgId = request.organization_id
    val caller = currentCaller()
    val page = store.read { r ->
      val access = Access(r, caller)
      // Surveys the caller can't list appear as tombstones, so a delta removes surveys whose
      // sharing
      // policy no longer grants access.
      val overviews =
        r.listCandidates(access, caller)
          .filter { s -> orgId.isBlank() || s.organization_id == orgId }
          .map { s ->
            val overview = r.overviewOf(s)
            if (access.listsSurvey(s, r.acl(s.survey_id))) overview
            else
              SurveyOverview(
                survey_id = s.survey_id,
                state = SurveyState.DELETED,
                commit_timestamp = overview.commit_timestamp,
                record_version = overview.record_version,
              )
          }
      syncLists.list(
        params = ListParams(request.page_size, request.page_token, request.sync_token),
        scopeKey = "surveys|organization_id=$orgId",
        records = overviews,
        commitTimestamp = SurveyOverview::commit_timestamp,
        id = SurveyOverview::survey_id,
        query =
          commitOrderQuery(SurveyOverview::commit_timestamp) { it.state != SurveyState.DELETED },
        queryFieldsSet = emptyList(),
        watermark = r.snapshotTimestamp,
        accessEpoch = r.accessEpochOf(caller),
      )
    }
    return ListSurveyOverviewsResponse(
      overviews = page.items,
      next_page_token = page.nextPageToken,
      next_sync_token = page.nextSyncToken,
    )
  }

  /**
   * Surveys that may belong in [caller]'s survey list, read through the per-user indexes: owned, on
   * the ACL (by user ID or verified email), or in an organization the caller belongs to.
   * Administrators get every survey. [Access.listsSurvey] then decides between an overview and a
   * tombstone (for example, after an organization survey stops being shared with members).
   *
   * Surveys the caller lost access to through other changes (ACL entries removed, organizations
   * left) aren't candidates any more; those changes bump the caller's access epoch instead, which
   * invalidates the caller's list tokens (`docs/technical/backend/spanner-schema.md`, "Access
   * Changes").
   */
  private fun UserDataReader.listCandidates(access: Access, caller: Caller?): List<SurveyDef> {
    if (caller == null || access.isAdmin) return surveys()
    val email = if (caller.emailVerified) caller.normalizedEmail else ""
    val candidates = LinkedHashMap<String, SurveyDef>()
    surveysOwnedBy(caller.userId, email).forEach { candidates[it.survey_id] = it }
    val aclSurveyIds =
      surveyIdsWithAclUser(caller.userId) +
        (if (email.isBlank()) emptyList() else surveyIdsWithAclEmail(email))
    for (id in aclSurveyIds.distinct()) {
      if (id !in candidates) survey(id)?.let { candidates[id] = it }
    }
    for (organizationId in memberOrganizationIds(caller, email)) {
      surveysInOrganization(organizationId).forEach { candidates.putIfAbsent(it.survey_id, it) }
    }
    return candidates.values.toList()
  }

  /** Organizations that may belong in [caller]'s organization list (all for administrators). */
  private fun UserDataReader.organizationCandidates(
    access: Access,
    caller: Caller?,
  ): List<Organization> {
    if (caller == null || access.isAdmin) return organizations()
    val email = if (caller.emailVerified) caller.normalizedEmail else ""
    return memberOrganizationIds(caller, email).mapNotNull(::organization)
  }

  /** Organizations with a membership (or invitation) for [caller]'s user ID or verified [email]. */
  private fun UserDataReader.memberOrganizationIds(caller: Caller, email: String): List<String> =
    (organizationIdsWithMemberUser(caller.userId) +
        (if (email.isBlank()) emptyList() else organizationIdsWithMemberEmail(email)))
      .distinct()

  override suspend fun GetSurvey(request: GetSurveyRequest): GetSurveyResponse {
    val caller = currentCaller()
    return store.read { r -> r.getSurvey(request, Access(r, caller)) }
  }

  private fun UserDataReader.getSurvey(
    request: GetSurveyRequest,
    access: Access,
  ): GetSurveyResponse {
    val r = this
    val role =
      access.requireSurvey(
        request.survey_id,
        if (request.working_copy != null) Access.SurveyRole.SURVEY_ORGANIZER
        else Access.SurveyRole.VIEWER,
      )
    r.checkNotTrashed(request.survey_id)
    val saved = r.survey(request.survey_id)
    val requestedVersion = request.saved_version
    val survey =
      when {
        request.working_copy != null -> r.workingCopy(request.survey_id)
        !requestedVersion.isNullOrBlank() -> r.surveyRevision(request.survey_id, requestedVersion)
        else -> saved
      }
    return GetSurveyResponse(survey = survey, acl = r.acl(request.survey_id)?.visibleTo(role))
  }

  override suspend fun GetSurveyAcl(request: GetSurveyAclRequest): GetSurveyAclResponse {
    val caller = currentCaller()
    return store.read { r ->
      val role = Access(r, caller).requireSurvey(request.survey_id, Access.SurveyRole.VIEWER)
      GetSurveyAclResponse(
        acl = (r.acl(request.survey_id) ?: SurveyAcl(survey_id = request.survey_id)).visibleTo(role)
      )
    }
  }

  /** The ACL as a caller with [role] may see it: collaborator entries only for organizers. */
  private fun SurveyAcl.visibleTo(role: Access.SurveyRole): SurveyAcl =
    if (role.atLeast(Access.SurveyRole.SURVEY_ORGANIZER)) this else copy(entries = emptyList())

  private fun UserDataReader.resolveEffectiveQuota(
    survey: SurveyDef?,
    organizationId: String?,
  ): QuotaLimits {
    val global = organization(ALL_USERS_ORGANIZATION_ID)?.quota_limits
    val org = organizationId?.takeIf { it.isNotBlank() }?.let { organization(it)?.quota_limits }
    val s = survey?.quota_limits

    val effectiveTier =
      when {
        s?.tier != null && s.tier != QuotaTier.QUOTA_TIER_UNSPECIFIED -> s.tier
        org?.tier != null && org.tier != QuotaTier.QUOTA_TIER_UNSPECIFIED -> org.tier
        organizationId?.isNotBlank() == true -> QuotaTier.SPONSORED
        global?.tier != null && global.tier != QuotaTier.QUOTA_TIER_UNSPECIFIED -> global.tier
        else -> QuotaTier.BASIC
      }

    val isSponsored = effectiveTier == QuotaTier.SPONSORED
    val defaultMaxSurveysPerOwner = if (isSponsored) 100 else 5
    val defaultMaxFormsPerSurvey = if (isSponsored) 10 else 5
    val defaultMaxQuestionsPerForm = if (isSponsored) 50 else 10
    val defaultMaxPhotoQuestionsPerForm = if (isSponsored) 10 else 1
    val defaultMaxPredefinedEntities = if (isSponsored) 10_000 else 1_000
    val defaultMaxAclUsers = if (isSponsored) 50 else 10
    val defaultMaxSurveysPerUser = if (isSponsored) 100 else 5
    val defaultMaxAdhocEntitiesPerUser = if (isSponsored) 200 else 100
    val defaultMaxSubmissionsPerEntityPerUser = if (isSponsored) 50 else 10
    val defaultMaxGeneratedEntities = if (isSponsored) 100_000 else 10_000
    val defaultMaxAreaVertices = if (isSponsored) 50_000 else 5_000

    fun resolveInt(sVal: Int?, orgVal: Int?, gVal: Int?, default: Int): Int =
      when {
        sVal != null && sVal > 0 -> sVal
        orgVal != null && orgVal > 0 -> orgVal
        gVal != null && gVal > 0 -> gVal
        else -> default
      }

    return QuotaLimits(
      tier = effectiveTier,
      max_surveys_per_owner =
        resolveInt(
          s?.max_surveys_per_owner,
          org?.max_surveys_per_owner,
          global?.max_surveys_per_owner,
          defaultMaxSurveysPerOwner,
        ),
      max_forms_per_survey =
        resolveInt(
          s?.max_forms_per_survey,
          org?.max_forms_per_survey,
          global?.max_forms_per_survey,
          defaultMaxFormsPerSurvey,
        ),
      max_questions_per_form =
        resolveInt(
          s?.max_questions_per_form,
          org?.max_questions_per_form,
          global?.max_questions_per_form,
          defaultMaxQuestionsPerForm,
        ),
      max_photo_questions_per_form =
        resolveInt(
          s?.max_photo_questions_per_form,
          org?.max_photo_questions_per_form,
          global?.max_photo_questions_per_form,
          defaultMaxPhotoQuestionsPerForm,
        ),
      max_predefined_entities =
        resolveInt(
          s?.max_predefined_entities,
          org?.max_predefined_entities,
          global?.max_predefined_entities,
          defaultMaxPredefinedEntities,
        ),
      max_acl_users =
        resolveInt(s?.max_acl_users, org?.max_acl_users, global?.max_acl_users, defaultMaxAclUsers),
      max_surveys_per_user =
        resolveInt(
          s?.max_surveys_per_user,
          org?.max_surveys_per_user,
          global?.max_surveys_per_user,
          defaultMaxSurveysPerUser,
        ),
      max_adhoc_entities_per_user =
        resolveInt(
          s?.max_adhoc_entities_per_user,
          org?.max_adhoc_entities_per_user,
          global?.max_adhoc_entities_per_user,
          defaultMaxAdhocEntitiesPerUser,
        ),
      max_submissions_per_entity_per_user =
        resolveInt(
          s?.max_submissions_per_entity_per_user,
          org?.max_submissions_per_entity_per_user,
          global?.max_submissions_per_entity_per_user,
          defaultMaxSubmissionsPerEntityPerUser,
        ),
      max_generated_entities =
        resolveInt(
          s?.max_generated_entities,
          org?.max_generated_entities,
          global?.max_generated_entities,
          defaultMaxGeneratedEntities,
        ),
      max_area_vertices =
        resolveInt(
          s?.max_area_vertices,
          org?.max_area_vertices,
          global?.max_area_vertices,
          defaultMaxAreaVertices,
        ),
    )
  }

  override suspend fun MutateSurvey(request: MutateSurveyRequest): MutateSurveyResponse {
    val surveyId = request.survey_id
    val caller = currentCaller()
    // Anyone signed in may create a survey; changing one takes an organizer.
    fun UserDataReader.requireOrganizerIfExists() {
      if (survey(surveyId) != null) {
        Access(this, caller).requireSurvey(surveyId, Access.SurveyRole.SURVEY_ORGANIZER)
      }
    }
    val toApply = store.read { r ->
      r.requireOrganizerIfExists()
      r.newMutations("surveys/$surveyId", request.mutations) {
        MutationIdentity(it.client_id, it.client_seq)
      }
    }
    if (toApply.isEmpty()) {
      return store.read { r ->
        val existingSaved = r.survey(surveyId)
        MutateSurveyResponse(
          survey_id = surveyId,
          survey =
            if (request.working_copy) r.workingCopy(surveyId) ?: existingSaved else existingSaved,
          acl = r.acl(surveyId),
          commit_timestamp = existingSaved?.commit_timestamp ?: store.watermark(),
        )
      }
    }
    require(
      toApply.none {
        it.acl_upsert?.default_role == Role.SURVEY_ORGANIZER
      }
    ) {
      "default_role can't be SURVEY_ORGANIZER; organizers must be named on the ACL"
    }

    val committed = store.write { tx ->
      if (
        toApply.any {
          (it.upsert?.survey_id?.isNotEmpty() == true && it.delete == null) ||
            (it.acl_upsert?.survey_id?.isNotEmpty() == true && it.acl_delete == null)
        }
      ) {
        val baseTarget =
          if (request.working_copy) tx.workingCopy(surveyId) ?: tx.survey(surveyId)
          else tx.survey(surveyId)
        checkBaseVersion(request.base_version, baseTarget?.version, "survey $surveyId")
      }
      tx.requireOrganizerIfExists()
      val isNewSurvey = tx.survey(surveyId) == null
      var saved =
        tx.survey(surveyId)
          ?: SurveyDef(
            survey_id = surveyId,
            state = SurveyState.PUBLISHED,
            saved_revision_number = 1L,
          )
      var workingCopy = tx.workingCopy(surveyId)
      var currentAcl = tx.acl(surveyId) ?: SurveyAcl(survey_id = surveyId)
      var savedRevisionChanged = false

      // Field and snapshot mutations apply to the revision selected by `request.working_copy`.
      fun target(): SurveyDef = if (request.working_copy) workingCopy ?: saved else saved
      fun setTarget(def: SurveyDef) {
        if (request.working_copy) workingCopy = def else saved = def
      }

      for (mutation in toApply) {
        val delSurvey = mutation.delete
        val upSurvey = mutation.upsert
        val delAcl = mutation.acl_delete
        val upAcl = mutation.acl_upsert
        if (mutation.trash_survey) {
          if (saved.trash_time == null) saved = saved.copy(trash_time = tx.now)
        }
        if (mutation.restore_survey) {
          saved = saved.copy(trash_time = null)
        }
        if (upSurvey != null && upSurvey.survey_id.isNotEmpty() && delSurvey == null) {
          setTarget(upSurvey.copy(survey_id = surveyId))
        } else if (delSurvey != null || upSurvey != null) {
          setTarget(SurveyPatches.apply(target(), delSurvey, upSurvey))
        }
        if (upAcl != null && upAcl.survey_id.isNotEmpty() && delAcl == null) {
          currentAcl = upAcl.copy(survey_id = surveyId)
        } else if (delAcl != null || upAcl != null) {
          currentAcl = SurveyPatches.applyAcl(currentAcl, delAcl, upAcl)
        }
        if (mutation.save_working_copy) {
          val toSave = workingCopy
          if (toSave != null) {
            val number = saved.saved_revision_number + 1L
            saved = toSave.copy(saved_revision_number = number, version = number.toString())
            workingCopy = null
            savedRevisionChanged = true
          }
        }
        if (mutation.discard_working_copy) {
          workingCopy = null
        }
      }

      // A new survey belongs to its creator unless the request names an owner.
      val creator = caller?.takeUnless { it.isDefault }
      if (isNewSurvey && creator != null && saved.owner_id.isBlank()) {
        saved = saved.copy(owner_id = creator.userId)
      }

      if (saved.state != SurveyState.DELETED) {
        val limits = tx.resolveEffectiveQuota(target(), target().organization_id)

        if (isNewSurvey) {
          val owner = target().owner_id.ifBlank { currentAcl.owner_id }
          if (owner.isNotBlank() && limits.max_surveys_per_owner > 0) {
            val ownerSurveys =
              tx.surveysOwnedBy(userId = owner, email = "").count {
                it.survey_id != surveyId && it.state != SurveyState.DELETED
              }
            if (ownerSurveys >= limits.max_surveys_per_owner) {
              throw StatusException(
                Status.RESOURCE_EXHAUSTED.withDescription(
                  "Survey quota exceeded: maximum surveys per owner reached (${limits.max_surveys_per_owner})"
                )
              )
            }
          }
        }

        if (limits.max_forms_per_survey > 0 && target().forms.size > limits.max_forms_per_survey) {
          throw StatusException(
            Status.RESOURCE_EXHAUSTED.withDescription(
              "Form quota exceeded: survey has ${target().forms.size} forms, exceeding maximum of ${limits.max_forms_per_survey}"
            )
          )
        }

        for (form in target().forms) {
          val questionsCount = form.model?.bindings?.size ?: form.view?.components?.size ?: 0
          if (limits.max_questions_per_form > 0 && questionsCount > limits.max_questions_per_form) {
            throw StatusException(
              Status.RESOURCE_EXHAUSTED.withDescription(
                "Question quota exceeded: form \"${form.form_id}\" has $questionsCount questions, exceeding maximum of ${limits.max_questions_per_form}"
              )
            )
          }
          val photoQuestions =
            form.model?.bindings?.count {
              it.type == openforis.collectone.v1.forms.DataType.TYPE_BINARY ||
                it.field_path.lowercase().contains("photo") ||
                it.field_path.lowercase().contains("picture") ||
                it.field_path.lowercase().contains("image")
            } ?: 0
          if (
            limits.max_photo_questions_per_form > 0 &&
              photoQuestions > limits.max_photo_questions_per_form
          ) {
            throw StatusException(
              Status.RESOURCE_EXHAUSTED.withDescription(
                "Photo question quota exceeded: form \"${form.form_id}\" has $photoQuestions photo questions, exceeding maximum of ${limits.max_photo_questions_per_form}"
              )
            )
          }
        }
        if (limits.max_acl_users > 0 && currentAcl.entries.size > limits.max_acl_users) {
          throw StatusException(
            Status.RESOURCE_EXHAUSTED.withDescription(
              "ACL quota exceeded: survey has ${currentAcl.entries.size} collaborators, exceeding maximum of ${limits.max_acl_users}"
            )
          )
        }

        val surveyArea = target().map_config?.survey_area
        if (surveyArea != null && limits.max_area_vertices > 0) {
          val totalVertices = surveyArea.parts.sumOf { it.points.size }
          if (totalVertices > limits.max_area_vertices) {
            throw StatusException(
              Status.RESOURCE_EXHAUSTED.withDescription(
                "Survey area vertex quota exceeded: survey area has $totalVertices vertices, exceeding maximum of ${limits.max_area_vertices}"
              )
            )
          }
        }
      }

      // Always write the saved revision so overview delta polls see working-copy and access
      // changes, and bump the survey's shared record version on all of its parts.
      val recordVersion = tx.nextSurveyRecordVersion(surveyId)
      saved = saved.copy(record_version = recordVersion)
      val acl = currentAcl.copy(record_version = recordVersion)
      val finalWorkingCopy = workingCopy?.copy(record_version = recordVersion)
      tx.putSurvey(saved)
      if (savedRevisionChanged) tx.putSurveyRevision(saved)
      val access = AccessChanges()
      val writtenAcl = tx.putAclTracked(acl, access)
      tx.applyAccessChanges(access)
      tx.putWorkingCopy(surveyId, finalWorkingCopy)
      tx.recordApplied(
        scope = "surveys/$surveyId",
        identities = toApply.map { MutationIdentity(it.client_id, it.client_seq) },
      )
      Triple(saved, finalWorkingCopy, writtenAcl)
    }
    val ts = committed.commitTimestamp
    val (saved, workingCopy, acl) = committed.value
    return MutateSurveyResponse(
      survey_id = surveyId,
      survey =
        (if (request.working_copy) workingCopy ?: saved else saved).copy(commit_timestamp = ts),
      acl = acl.copy(commit_timestamp = ts),
      commit_timestamp = ts,
    )
  }

  override suspend fun AcceptSurveyInvite(
    request: AcceptSurveyInviteRequest
  ): AcceptSurveyInviteResponse {
    val userId = callerUserId(request.user_id)
    val committed = store.write { tx ->
      val acl = tx.acl(request.survey_id) ?: SurveyAcl(survey_id = request.survey_id)
      val updatedEntries =
        acl.entries.map { entry ->
          if (entry.invite_token == request.invite_token) {
            entry.copy(
              invitation_status = AclEntry.InvitationStatus.ACCEPTED,
              user_id = userId.ifBlank { entry.user_id },
              profile = request.profile ?: entry.profile,
            )
          } else {
            entry
          }
        }
      val access = AccessChanges()
      val written =
        tx.putAclWithRecordVersion(request.survey_id, acl.copy(entries = updatedEntries), access)
      tx.applyAccessChanges(access)
      written
    }
    val ts = committed.commitTimestamp
    return AcceptSurveyInviteResponse(
      survey = committed.value.first?.copy(commit_timestamp = ts),
      acl = committed.value.second.copy(commit_timestamp = ts),
      commit_timestamp = ts,
    )
  }

  override suspend fun BatchGetSurveyOverviews(
    request: BatchGetSurveyOverviewsRequest
  ): BatchGetSurveyOverviewsResponse {
    require(request.survey_ids.size <= MAX_BATCH_SURVEY_IDS) {
      "At most $MAX_BATCH_SURVEY_IDS survey_ids per request"
    }
    val caller = currentCaller()
    return store.read { r ->
      val access = Access(r, caller)
      BatchGetSurveyOverviewsResponse(
        results =
          request.survey_ids.map { id ->
            val saved = r.survey(id)
            when {
              saved == null ->
                SurveyOverviewResult(survey_id = id, error_code = Status.Code.NOT_FOUND.value())
              // A purged survey's tombstone tells every former holder it's gone.
              saved.state != SurveyState.DELETED &&
                access.surveyRole(saved, r.acl(id)) == Access.SurveyRole.NONE ->
                SurveyOverviewResult(
                  survey_id = id,
                  error_code = Status.Code.PERMISSION_DENIED.value(),
                )
              else -> SurveyOverviewResult(survey_id = id, overview = r.overviewOf(saved))
            }
          }
      )
    }
  }

  override suspend fun SearchPublicSurveys(
    request: SearchPublicSurveysRequest
  ): SearchPublicSurveysResponse {
    val q = request.query.trim().lowercase()
    val matching = store.read { r ->
      r.searchPublicSurveys(q)
        .filter { s ->
          r.acl(s.survey_id)?.sharing_policy == SharingPolicy.PUBLIC &&
            s.trash_time == null &&
            s.state != SurveyState.DELETED &&
            (q.isEmpty() ||
              s.title.lowercase().contains(q) ||
              s.description.lowercase().contains(q))
        }
        .sortedBy { it.survey_id }
        .map { s -> r.overviewOf(s) }
    }
    val (items, nextPageToken) = page(matching, request.page_size, request.page_token)
    return SearchPublicSurveysResponse(overviews = items, next_page_token = nextPageToken)
  }

  /** Fails with `FAILED_PRECONDITION` when survey [surveyId] is in Trash or was purged. */
  private fun UserDataReader.checkNotTrashed(surveyId: String) {
    val s = survey(surveyId)
    check(s?.trash_time == null && s?.state != SurveyState.DELETED) {
      "Survey $surveyId is in Trash"
    }
  }

  // ---------------------------------------------------------------------------
  // DataServiceServer
  // ---------------------------------------------------------------------------

  /**
   * Which records of a survey a caller sees: all, or only those it created (`OWN_ONLY` below
   * organizer). [key] goes into entity and submission tokens, so a token issued under another
   * visibility (after a `peer_data_visibility` flip, a role change, or for another user) fails with
   * `FAILED_PRECONDITION` and the client runs a full sync of the survey.
   */
  private class DataVisibility(val key: String, private val ownerId: String?) {
    operator fun invoke(createdBy: String): Boolean = ownerId == null || createdBy == ownerId
  }

  /** The caller's [DataVisibility] for survey [surveyId], after checking it can view the survey. */
  private fun Access.dataVisibility(surveyId: String): DataVisibility {
    val role = requireSurvey(surveyId, Access.SurveyRole.VIEWER)
    return if (seesOwnRecordsOnly(surveyId, role)) DataVisibility("own:$userId", userId)
    else DataVisibility("all", null)
  }

  override suspend fun GetEntity(request: GetEntityRequest): GetEntityResponse {
    val caller = currentCaller()
    val entity =
      store.read { r ->
        val visible = Access(r, caller).dataVisibility(request.survey_id)
        r.entity(request.survey_id, request.entity_id)?.takeIf {
          visible(it.audit_info?.created_by.orEmpty())
        }
      } ?: EntityRecord(survey_id = request.survey_id, entity_id = request.entity_id)
    return GetEntityResponse(entity = entity)
  }

  override suspend fun ListEntities(request: ListEntitiesRequest): ListEntitiesResponse {
    val queryFieldsSet = buildList {
      if (request.dataset_id.isNotBlank()) add("dataset_id")
      if (request.s2_cell_tokens.isNotEmpty()) add("s2_cell_tokens")
      if (request.include_deleted) add("include_deleted")
      if (request.bbox != null) add("bbox")
      if (request.filter.isNotEmpty()) add("filter")
      if (request.queue_filter_mode != QueueFilterMode.QUEUE_FILTER_MODE_UNSPECIFIED) {
        add("queue_filter_mode")
      }
      if (request.order_by.isNotBlank()) add("order_by")
    }
    val query =
      orderedQuery(
        orderBy = request.order_by,
        commitTimestamp = EntityRecord::commit_timestamp,
        stringKeys = mapOf("label" to EntityRecord::label),
        fingerprint =
          "dataset_id=${request.dataset_id}|include_deleted=${request.include_deleted}|" +
            "filter=${request.filter.toSortedMap()}|order_by=${request.order_by.trim().lowercase()}",
      ) { e ->
        (request.dataset_id.isBlank() || e.dataset_id == request.dataset_id) &&
          (request.include_deleted || !e.is_deleted) &&
          request.filter.all { (key, value) -> e.properties[key]?.string_value == value }
      }
    // `s2_cell_tokens` is a query-only filter, but the backend doesn't index entities by S2 cell
    // yet, so it doesn't narrow the results.
    val caller = currentCaller()
    val page = store.read { r ->
      val visible = Access(r, caller).dataVisibility(request.survey_id)
      r.checkNotTrashed(request.survey_id)
      syncLists.list(
        params = ListParams(request.page_size, request.page_token, request.sync_token),
        scopeKey = "entities|survey_id=${request.survey_id}",
        changedAfter = { after, limit -> r.entitiesChangedAfter(request.survey_id, after, limit) },
        queryRecords = {
          r.entities(request.survey_id, request.dataset_id, request.include_deleted)
        },
        commitTimestamp = EntityRecord::commit_timestamp,
        id = EntityRecord::entity_id,
        query = query,
        queryFieldsSet = queryFieldsSet,
        watermark = r.snapshotTimestamp,
        accessEpoch = visible.key,
        visible = { visible(it.audit_info?.created_by.orEmpty()) },
      )
    }
    return ListEntitiesResponse(
      entities = page.items,
      next_page_token = page.nextPageToken,
      next_sync_token = page.nextSyncToken,
    )
  }

  override suspend fun ListEntityClusters(
    request: ListEntityClustersRequest
  ): ListEntityClustersResponse {
    val caller = currentCaller()
    val entities = store.read { r ->
      val visible = Access(r, caller).dataVisibility(request.survey_id)
      r.entities(request.survey_id, request.dataset_id, includeDeleted = false).filter {
        visible(it.audit_info?.created_by.orEmpty())
      }
    }
    if (entities.isEmpty()) return ListEntityClustersResponse()
    val cluster =
      EntityCluster(
        s2_cell_token = "10d96b",
        centroid = GeoPoint(latitude = -0.4192, longitude = 36.9498),
        total_count = entities.size,
      )
    return ListEntityClustersResponse(clusters = listOf(cluster))
  }

  override suspend fun GetSubmission(request: GetSubmissionRequest): GetSubmissionResponse {
    val caller = currentCaller()
    val sub =
      store.read { r ->
        val visible = Access(r, caller).dataVisibility(request.survey_id)
        r.submission(request.survey_id, request.submission_id)?.takeIf {
          visible(it.audit_info?.created_by.orEmpty())
        }
      } ?: SubmissionRecord(survey_id = request.survey_id, submission_id = request.submission_id)
    return GetSubmissionResponse(submission = sub)
  }

  override suspend fun ListSubmissions(request: ListSubmissionsRequest): ListSubmissionsResponse {
    val queryFieldsSet = buildList {
      if (request.form_id.isNotBlank()) add("form_id")
      if (request.entity_id.isNotBlank()) add("entity_id")
      if (request.include_deleted) add("include_deleted")
      if (request.filter.isNotEmpty()) add("filter")
      if (request.order_by.isNotBlank()) add("order_by")
    }
    val query =
      orderedQuery(
        orderBy = request.order_by,
        commitTimestamp = SubmissionRecord::commit_timestamp,
        stringKeys = mapOf("collector_name" to SubmissionRecord::collector_name),
        fingerprint =
          "form_id=${request.form_id}|entity_id=${request.entity_id}|" +
            "include_deleted=${request.include_deleted}|filter=${request.filter.toSortedMap()}|" +
            "order_by=${request.order_by.trim().lowercase()}",
      ) { sub ->
        (request.form_id.isBlank() || sub.form_id == request.form_id) &&
          (request.entity_id.isBlank() || sub.entity_id == request.entity_id) &&
          (request.include_deleted || !sub.is_deleted) &&
          request.filter.all { (key, value) ->
            when (key) {
              "collector_email" -> sub.collector_email == value
              "collector_name" -> sub.collector_name == value
              else -> true
            }
          }
      }
    val caller = currentCaller()
    val page = store.read { r ->
      val visible = Access(r, caller).dataVisibility(request.survey_id)
      r.checkNotTrashed(request.survey_id)
      syncLists.list(
        params = ListParams(request.page_size, request.page_token, request.sync_token),
        scopeKey = "submissions|survey_id=${request.survey_id}",
        changedAfter = { after, limit ->
          r.submissionsChangedAfter(request.survey_id, after, limit)
        },
        queryRecords = {
          r.submissions(request.survey_id, request.form_id, request.include_deleted)
        },
        commitTimestamp = SubmissionRecord::commit_timestamp,
        id = SubmissionRecord::submission_id,
        query = query,
        queryFieldsSet = queryFieldsSet,
        watermark = r.snapshotTimestamp,
        accessEpoch = visible.key,
        visible = { visible(it.audit_info?.created_by.orEmpty()) },
      )
    }
    return ListSubmissionsResponse(
      submissions = page.items,
      next_page_token = page.nextPageToken,
      next_sync_token = page.nextSyncToken,
    )
  }

  override suspend fun MutateData(request: MutateDataRequest): MutateDataResponse {
    val surveyId = request.survey_id
    val scope = "surveys/$surveyId/data"
    val caller = currentCaller()
    val hasNew = store.read { r ->
      Access(r, caller).requireSurvey(surveyId, Access.SurveyRole.DATA_COLLECTOR)
      val replays = ReplayFilter(r, scope)
      request.mutations.any { !replays.isReplay(MutationIdentity(it.client_id, it.client_seq)) }
    }
    if (!hasNew) {
      // Every mutation is a replay: report the records' current state without writing.
      return store.read { r ->
        MutateDataResponse(
          results =
            request.mutations.map { r.duplicateDataResult(surveyId, it, emptyMap(), emptyMap()) },
          commit_timestamp = store.watermark(),
        )
      }
    }
    val committed = store.write { tx ->
      // The transaction reads data as of its start, so changes made by earlier mutations of this
      // request are tracked here.
      val entities = LinkedHashMap<String, EntityRecord>()
      val submissions = LinkedHashMap<String, SubmissionRecord>()
      val applied = mutableListOf<MutationIdentity>()
      val replays = ReplayFilter(tx, scope)
      val results = mutableListOf<Pair<DataMutationResult, Boolean>>()
      val access = Access(tx, caller)
      val role = access.requireSurvey(surveyId, Access.SurveyRole.DATA_COLLECTOR)
      val ownRecordsOnly = access.seesOwnRecordsOnly(surveyId, role)
      // New records are attributed to the authenticated caller.
      val creator = caller?.takeUnless { it.isDefault }?.userId
      for (mutation in request.mutations) {
        val identity = MutationIdentity(mutation.client_id, mutation.client_seq)
        if (replays.isReplay(identity)) {
          results += tx.duplicateDataResult(surveyId, mutation, entities, submissions) to false
          continue
        }
        val em = mutation.entity_mutation
        val sm = mutation.submission_mutation
        val existingCreator =
          when {
            em != null ->
              (entities[em.entity_id] ?: tx.entity(surveyId, em.entity_id))?.let {
                it.audit_info?.created_by.orEmpty()
              }
            sm != null ->
              (submissions[sm.submission_id] ?: tx.submission(surveyId, sm.submission_id))?.let {
                it.audit_info?.created_by.orEmpty()
              }
            else -> null
          }
        if (ownRecordsOnly && existingCreator != null && existingCreator != access.userId) {
          throw Access.denied("records other collectors created in survey $surveyId")
        }
        var result = tx.applyDataMutation(surveyId, mutation, entities, submissions)
        if (existingCreator == null && creator != null) {
          result = attributeTo(creator, result, entities, submissions)
        }
        results += result to true
        applied += identity
      }
      entities.values.forEach(tx::putEntity)
      submissions.values.forEach(tx::putSubmission)
      tx.recordApplied(scope, identities = applied)
      results
    }
    val ts = committed.commitTimestamp
    return MutateDataResponse(
      results =
        committed.value.map { (result, written) ->
          if (!written) result
          else
            result.copy(
              entity = result.entity?.copy(commit_timestamp = ts),
              submission = result.submission?.copy(commit_timestamp = ts),
              commit_timestamp = ts,
            )
        },
      commit_timestamp = ts,
    )
  }

  /** Sets `created_by` to [userId] on the record [result] created, in the result and buffers. */
  private fun attributeTo(
    userId: String,
    result: DataMutationResult,
    entities: LinkedHashMap<String, EntityRecord>,
    submissions: LinkedHashMap<String, SubmissionRecord>,
  ): DataMutationResult {
    fun AuditInfo?.by() = (this ?: AuditInfo()).copy(created_by = userId)
    result.entity?.let { e ->
      val attributed = e.copy(audit_info = e.audit_info.by())
      entities[e.entity_id] = attributed
      return result.copy(entity = attributed)
    }
    result.submission?.let { sub ->
      val attributed = sub.copy(audit_info = sub.audit_info.by())
      submissions[sub.submission_id] = attributed
      return result.copy(submission = attributed)
    }
    return result
  }

  /** Result for a replayed `mutation_id`: the target record's current state, unchanged. */
  private fun UserDataReader.duplicateDataResult(
    surveyId: String,
    mutation: DataMutation,
    entities: Map<String, EntityRecord>,
    submissions: Map<String, SubmissionRecord>,
  ): DataMutationResult {
    val entity =
      mutation.entity_mutation?.let { entities[it.entity_id] ?: entity(surveyId, it.entity_id) }
    val submission =
      mutation.submission_mutation?.let {
        submissions[it.submission_id] ?: submission(surveyId, it.submission_id)
      }
    return DataMutationResult(
      entity = entity,
      submission = if (entity == null) submission else null,
      conflict_status = ConflictResolutionStatus.CONFLICT_RESOLUTION_STATUS_CLEAN,
      commit_timestamp = entity?.commit_timestamp ?: submission?.commit_timestamp,
      duplicate = true,
    )
  }

  /**
   * Conflict status for a field patch based on [baseRevision] against the stored [currentRevision]:
   * patches always apply (last writer wins), but report `AUTO_MERGED` when based on an older
   * revision.
   */
  private fun patchConflictStatus(baseRevision: Long, currentRevision: Long) =
    if (baseRevision in 1L until currentRevision) {
      ConflictResolutionStatus.CONFLICT_RESOLUTION_STATUS_AUTO_MERGED
    } else {
      ConflictResolutionStatus.CONFLICT_RESOLUTION_STATUS_CLEAN
    }

  /**
   * Applies [mutation] on top of the stored records and this request's earlier changes ([entities]
   * / [submissions], which it updates). The store stamps the commit timestamp.
   */
  private fun UserDataReader.applyDataMutation(
    surveyId: String,
    mutation: DataMutation,
    entities: LinkedHashMap<String, EntityRecord>,
    submissions: LinkedHashMap<String, SubmissionRecord>,
  ): DataMutationResult {
    val em = mutation.entity_mutation
    val sm = mutation.submission_mutation
    return when {
      em != null -> {
        val stored = entities[em.entity_id] ?: entity(surveyId, em.entity_id)
        val existing =
          stored
            ?: EntityRecord(
              entity_id = em.entity_id,
              dataset_id = em.dataset_id,
              survey_id = surveyId,
              revision = 0L,
            )
        val emDelete = em.delete
        val emUpsert = em.upsert
        val isEntityWholeUpsert =
          emUpsert != null && emUpsert.entity_id.isNotEmpty() && emDelete == null
        if (isEntityWholeUpsert) {
          checkBaseVersion(em.base_revision, stored?.revision, "entity ${em.entity_id}")
          if (existing.revision == 0L) {
            val s = survey(surveyId)
            val limits = resolveEffectiveQuota(s, s?.organization_id)
            if (emUpsert.is_field_created) {
              val userId = emUpsert.audit_info?.created_by.orEmpty()
              val allEntities = entities(surveyId).associateBy { it.entity_id } + entities
              val userAdhoc =
                allEntities.values.count {
                  !it.is_deleted &&
                    it.is_field_created &&
                    (userId.isBlank() || it.audit_info?.created_by == userId)
                }
              if (
                limits.max_adhoc_entities_per_user > 0 &&
                  userAdhoc >= limits.max_adhoc_entities_per_user
              ) {
                throw StatusException(
                  Status.RESOURCE_EXHAUSTED.withDescription(
                    "Ad-hoc entity quota exceeded: maximum ad-hoc entities per user reached (${limits.max_adhoc_entities_per_user})"
                  )
                )
              }
            } else {
              val allEntities = entities(surveyId).associateBy { it.entity_id } + entities
              val predefined = allEntities.values.count { !it.is_deleted && !it.is_field_created }
              if (
                limits.max_predefined_entities > 0 && predefined >= limits.max_predefined_entities
              ) {
                throw StatusException(
                  Status.RESOURCE_EXHAUSTED.withDescription(
                    "Predefined entity quota exceeded: maximum predefined entities reached (${limits.max_predefined_entities})"
                  )
                )
              }
            }
          }
        }
        val updated: EntityRecord =
          when {
            DataPatches.isEntityRootDelete(emDelete, emUpsert) ->
              EntityRecord(
                entity_id = existing.entity_id,
                dataset_id = existing.dataset_id,
                survey_id = surveyId,
                audit_info = existing.audit_info,
                revision = existing.revision + 1L,
                is_deleted = true,
                sync_status_key = "SYNCED",
              )
            isEntityWholeUpsert ->
              emUpsert.copy(
                entity_id = em.entity_id.ifBlank { emUpsert.entity_id },
                dataset_id = em.dataset_id.ifBlank { emUpsert.dataset_id },
                survey_id = surveyId,
                revision = existing.revision + 1L,
                sync_status_key = "SYNCED",
              )
            else ->
              DataPatches.applyEntity(existing, emDelete, emUpsert)
                .copy(revision = existing.revision + 1L, sync_status_key = "SYNCED")
          }
        entities[updated.entity_id] = updated
        DataMutationResult(
          entity = updated,
          conflict_status = patchConflictStatus(em.base_revision, existing.revision),
        )
      }
      sm != null -> {
        val stored = submissions[sm.submission_id] ?: submission(surveyId, sm.submission_id)
        val existing =
          stored
            ?: SubmissionRecord(
              submission_id = sm.submission_id,
              form_id = sm.form_id,
              survey_id = surveyId,
              revision = 0L,
            )
        val smDelete = sm.delete
        val smUpsert = sm.upsert
        val isSubmissionWholeUpsert =
          smUpsert != null && smUpsert.submission_id.isNotEmpty() && smDelete == null
        if (isSubmissionWholeUpsert) {
          checkBaseVersion(sm.base_revision, stored?.revision, "submission ${sm.submission_id}")
          if (existing.revision == 0L) {
            val s = survey(surveyId)
            val limits = resolveEffectiveQuota(s, s?.organization_id)
            if (smUpsert.entity_id.isNotBlank()) {
              val userId = smUpsert.audit_info?.created_by.orEmpty()
              val allSubmissions =
                submissions(surveyId).associateBy { it.submission_id } + submissions
              val countForEntityAndUser =
                allSubmissions.values.count {
                  !it.is_deleted &&
                    it.entity_id == smUpsert.entity_id &&
                    (userId.isBlank() || it.audit_info?.created_by == userId)
                }
              if (
                limits.max_submissions_per_entity_per_user > 0 &&
                  countForEntityAndUser >= limits.max_submissions_per_entity_per_user
              ) {
                throw StatusException(
                  Status.RESOURCE_EXHAUSTED.withDescription(
                    "Submission quota exceeded: maximum submissions per entity per user reached (${limits.max_submissions_per_entity_per_user})"
                  )
                )
              }
            }
          }
        }
        val updated: SubmissionRecord =
          when {
            DataPatches.isSubmissionRootDelete(smDelete, smUpsert) ->
              SubmissionRecord(
                submission_id = existing.submission_id,
                form_id = existing.form_id,
                survey_id = surveyId,
                entity_id = existing.entity_id,
                audit_info = existing.audit_info,
                revision = existing.revision + 1L,
                is_deleted = true,
                sync_status_key = "SYNCED",
              )
            isSubmissionWholeUpsert ->
              smUpsert.copy(
                submission_id = sm.submission_id.ifBlank { smUpsert.submission_id },
                form_id = sm.form_id.ifBlank { smUpsert.form_id },
                survey_id = surveyId,
                revision = existing.revision + 1L,
                sync_status_key = "SYNCED",
              )
            else ->
              DataPatches.applySubmission(existing, smDelete, smUpsert)
                .copy(revision = existing.revision + 1L, sync_status_key = "SYNCED")
          }
        submissions[updated.submission_id] = updated
        DataMutationResult(
          submission = updated,
          conflict_status = patchConflictStatus(sm.base_revision, existing.revision),
        )
      }
      else ->
        DataMutationResult(
          conflict_status = ConflictResolutionStatus.CONFLICT_RESOLUTION_STATUS_CLEAN
        )
    }
  }

  /** Fails with `PERMISSION_DENIED` unless the caller has at least [role] on [surveyId]. */
  private suspend fun requireSurveyRole(surveyId: String, role: Access.SurveyRole) {
    val caller = currentCaller()
    store.read { r -> Access(r, caller).requireSurvey(surveyId, role) }
  }

  override suspend fun AcquirePlotLock(request: AcquirePlotLockRequest): AcquirePlotLockResponse {
    requireSurveyRole(request.survey_id, Access.SurveyRole.DATA_COLLECTOR)
    val userId = callerUserId(request.user_id)
    val committed = store.write { tx ->
      val existing = tx.plotLock(request.survey_id, request.entity_id)
      val nowSec = tx.now.epochSecond
      if (
        existing != null &&
          existing.locked_by_user_id != userId &&
          (existing.expires_at?.epochSecond ?: 0L) > nowSec
      ) {
        return@write AcquirePlotLockResponse(
          entity = tx.entity(request.survey_id, request.entity_id),
          lock = existing,
        ) to false
      }
      val lock =
        PlotLock(
          entity_id = request.entity_id,
          locked_by_user_id = userId,
          locked_at = tx.now,
          expires_at = Instant.ofEpochSecond(nowSec + 300L, 0L),
        )
      tx.putPlotLock(request.survey_id, request.entity_id, lock)
      val withLock =
        tx.entity(request.survey_id, request.entity_id)?.let { ent ->
          ent.copy(active_lock = lock, revision = ent.revision + 1L).also(tx::putEntity)
        }
      AcquirePlotLockResponse(entity = withLock, lock = lock) to true
    }
    val (response, written) = committed.value
    return if (written) {
      response.copy(entity = response.entity?.copy(commit_timestamp = committed.commitTimestamp))
    } else {
      response
    }
  }

  override suspend fun ReleasePlotLock(request: ReleasePlotLockRequest): ReleasePlotLockResponse {
    requireSurveyRole(request.survey_id, Access.SurveyRole.DATA_COLLECTOR)
    store.write { tx ->
      tx.putPlotLock(request.survey_id, request.entity_id, null)
      tx.entity(request.survey_id, request.entity_id)?.let { ent ->
        tx.putEntity(ent.copy(active_lock = null, revision = ent.revision + 1L))
      }
    }
    return ReleasePlotLockResponse(released = true)
  }

  override suspend fun PrepareMediaUpload(
    request: PrepareMediaUploadRequest
  ): PrepareMediaUploadResponse {
    requireSurveyRole(request.survey_id, Access.SurveyRole.DATA_COLLECTOR)
    return PrepareMediaUploadResponse(
      upload_url =
        "https://storage.collectone.groundplatform.org/upload/${request.survey_id}/${request.sha256_hash}",
      media_uri = "gs://collect-one-media/${request.survey_id}/${request.sha256_hash}",
      already_exists = false,
    )
  }

  override suspend fun GetMediaDownloadUrl(
    request: GetMediaDownloadUrlRequest
  ): GetMediaDownloadUrlResponse {
    requireSurveyRole(request.survey_id, Access.SurveyRole.VIEWER)
    return GetMediaDownloadUrlResponse(
      download_url =
        "https://storage.collectone.groundplatform.org/download/${request.survey_id}/${request.media_uri}"
    )
  }

  /**
   * Whether the caller may see reports (impact events, audit records) about survey [surveyId] or
   * organization [organizationId]: as an administrator, a survey organizer, or an organization
   * manager. Memoized per ID for one request.
   */
  private class ReportAccess(private val reader: UserDataReader, private val access: Access) {
    private val surveys = HashMap<String, Boolean>()
    private val organizations = HashMap<String, Boolean>()

    fun allows(surveyId: String, organizationId: String = ""): Boolean =
      access.isAdmin ||
        (surveyId.isNotBlank() &&
          surveys.getOrPut(surveyId) {
            access.surveyRole(surveyId).atLeast(Access.SurveyRole.SURVEY_ORGANIZER)
          }) ||
        (organizationId.isNotBlank() &&
          organizations.getOrPut(organizationId) {
            access.manages(reader.organization(organizationId))
          })
  }

  override suspend fun RecordImpactEvents(
    request: RecordImpactEventsRequest
  ): RecordImpactEventsResponse {
    if (request.events.isEmpty()) {
      return RecordImpactEventsResponse(commit_timestamp = store.watermark())
    }
    val committed = store.write { tx -> request.events.forEach(tx::putImpactEvent) }
    val ts = committed.commitTimestamp
    return RecordImpactEventsResponse(
      events = request.events.map { it.copy(commit_timestamp = ts) },
      commit_timestamp = ts,
    )
  }

  override suspend fun ListImpactEvents(
    request: ListImpactEventsRequest
  ): ListImpactEventsResponse {
    val caller = currentCaller()
    val page = store.read { snapshot ->
      val reports = ReportAccess(snapshot, Access(snapshot, caller))
      syncLists.list(
        params = ListParams(request.page_size, request.page_token, request.sync_token),
        scopeKey =
          "impact_events|survey_id=${request.survey_id}|organization_id=${request.organization_id}",
        records =
          snapshot.impactEvents().filter { ev ->
            (request.survey_id.isBlank() || ev.survey_id == request.survey_id) &&
              (request.organization_id.isBlank() || ev.organization_id == request.organization_id)
          },
        commitTimestamp = ImpactEventProto::commit_timestamp,
        id = ImpactEventProto::event_id,
        query = commitOrderQuery(ImpactEventProto::commit_timestamp),
        queryFieldsSet = emptyList(),
        watermark = snapshot.snapshotTimestamp,
        accessEpoch = snapshot.accessEpochOf(caller),
        visible = { reports.allows(it.survey_id, it.organization_id) },
      )
    }
    return ListImpactEventsResponse(
      events = page.items,
      next_page_token = page.nextPageToken,
      next_sync_token = page.nextSyncToken,
    )
  }

  override suspend fun GetImpactAggregates(
    request: GetImpactAggregatesRequest
  ): GetImpactAggregatesResponse {
    val caller = currentCaller()
    val activeSurveys =
      store
        .read { r ->
          val reports = ReportAccess(r, Access(r, caller))
          r.surveys().filter { reports.allows(it.survey_id, it.organization_id) }
        }
        .filter { s ->
          s.state != SurveyState.DELETED &&
            s.trash_time == null &&
            (request.survey_id.isBlank() || s.survey_id == request.survey_id) &&
            (request.organization_id.isBlank() || s.organization_id == request.organization_id)
        }
    var totalEntities = 0L
    var totalAreaHa = 0.0
    for (s in activeSurveys) {
      val ents = store.read { it.entities(s.survey_id, includeDeleted = false) }
      totalEntities += ents.size
      totalAreaHa += ents.sumOf { it.area_hectares }
    }
    return GetImpactAggregatesResponse(
      aggregates =
        listOf(
          ImpactAggregate(
            run_id = "run-static-1",
            pipeline_version = 1,
            computed_at = store.watermark(),
            scope_type =
              if (request.survey_id.isNotBlank()) ImpactAggregate.ScopeType.SURVEY
              else if (request.organization_id.isNotBlank()) ImpactAggregate.ScopeType.ORGANIZATION
              else ImpactAggregate.ScopeType.GLOBAL,
            scope_id = request.survey_id.ifBlank { request.organization_id },
            metric = ImpactAggregate.Metric.FEATURES,
            feature_count = totalEntities,
            area_ha = totalAreaHa,
          )
        )
    )
  }

  override suspend fun ListAuditRecords(
    request: ListAuditRecordsRequest
  ): ListAuditRecordsResponse {
    val caller = currentCaller()
    val page = store.read { snapshot ->
      val reports = ReportAccess(snapshot, Access(snapshot, caller))
      syncLists.list(
        params = ListParams(request.page_size, request.page_token, request.sync_token),
        scopeKey = "audit_records|survey_id=${request.survey_id}|target_id=${request.target_id}",
        records =
          snapshot.auditRecords().filter { a ->
            (request.survey_id.isBlank() || a.survey_id == request.survey_id) &&
              (request.target_id.isBlank() || a.target_id == request.target_id)
          },
        commitTimestamp = AuditRecord::commit_timestamp,
        id = AuditRecord::audit_id,
        query = commitOrderQuery(AuditRecord::commit_timestamp),
        queryFieldsSet = emptyList(),
        watermark = snapshot.snapshotTimestamp,
        accessEpoch = snapshot.accessEpochOf(caller),
        visible = { reports.allows(it.survey_id) },
      )
    }
    return ListAuditRecordsResponse(
      audit_records = page.items,
      next_page_token = page.nextPageToken,
      next_sync_token = page.nextSyncToken,
    )
  }

  override suspend fun ListPlaces(request: ListPlacesRequest): ListPlacesResponse {
    val q = request.query.trim().lowercase()
    val filtered =
      if (q.isEmpty()) {
        store.read { it.places() }
      } else {
        store
          .read { it.places() }
          .filter { it.name.lowercase().contains(q) || it.region_subtitle.lowercase().contains(q) }
      }
    return ListPlacesResponse(places = filtered)
  }

  override suspend fun ListMutationLogs(
    request: ListMutationLogsRequest
  ): ListMutationLogsResponse {
    val page = store.read { snapshot ->
      syncLists.list(
        params = ListParams(request.page_size, request.page_token, request.sync_token),
        scopeKey = "mutation_logs|survey_id=${request.survey_id}",
        records =
          snapshot.mutationLogs().filter {
            request.survey_id.isBlank() || it.survey_id == request.survey_id
          },
        commitTimestamp = MutationLogRecord::commit_timestamp,
        id = MutationLogRecord::id,
        query = commitOrderQuery(MutationLogRecord::commit_timestamp),
        queryFieldsSet = emptyList(),
        watermark = snapshot.snapshotTimestamp,
      )
    }
    return ListMutationLogsResponse(
      mutation_logs = page.items,
      next_page_token = page.nextPageToken,
      next_sync_token = page.nextSyncToken,
    )
  }

  override suspend fun GetAppliedMutations(
    request: GetAppliedMutationsRequest
  ): GetAppliedMutationsResponse {
    require(request.client_id.isNotBlank()) { "client_id is required" }
    val scopes = request.scopes.toSet()
    return GetAppliedMutationsResponse(
      watermarks =
        store
          .read { it.appliedWatermarks(request.client_id) }
          .filter { scopes.isEmpty() || it.scope in scopes }
    )
  }

  /**
   * Builds the catalog overview of [surveyDef]. Its `commit_timestamp` is the later of the survey's
   * and [acl]'s, so access changes also reach `ListSurveyOverviews` delta polls. A trashed survey's
   * overview is a tombstone (`state == DELETED`) carrying `trash_time`, so lists drop it.
   */
  private fun UserDataReader.overviewOf(surveyDef: SurveyDef): SurveyOverview {
    val acl = acl(surveyDef.survey_id)
    return SurveyOverview(
      survey_id = surveyDef.survey_id,
      title = surveyDef.title,
      description = surveyDef.description,
      organization_id = surveyDef.organization_id,
      owner_id = surveyDef.owner_id,
      state = if (surveyDef.trash_time != null) SurveyState.DELETED else surveyDef.state,
      trash_time = surveyDef.trash_time,
      version = surveyDef.version,
      saved_revision_number = surveyDef.saved_revision_number,
      location_label = surveyDef.location_label,
      coordinates_label = surveyDef.coordinates_label,
      offline_size_label = surveyDef.offline_size_label,
      is_downloaded = surveyDef.is_downloaded,
      thumbnail_theme = surveyDef.thumbnail_theme,
      entity_count = surveyDef.entity_count,
      survey_area = surveyDef.map_config?.survey_area,
      outcome = surveyDef.outcome,
      closed_at = surveyDef.closed_at,
      closed_at_iso = surveyDef.closed_at_iso,
      commit_timestamp =
        listOfNotNull(surveyDef.commit_timestamp, acl?.commit_timestamp)
          .maxWithOrNull(instantComparator),
      record_version = maxOf(surveyDef.record_version, acl?.record_version ?: 0L),
      has_working_copy = workingCopy(surveyDef.survey_id) != null,
      sharing_policy = acl?.sharing_policy ?: SharingPolicy.SHARING_POLICY_UNSPECIFIED,
      peer_data_visibility =
        acl?.peer_data_visibility ?: PeerDataVisibility.PEER_DATA_VISIBILITY_UNSPECIFIED,
      collector_consent_required = acl?.collector_consent_required ?: false,
      collector_consent_text = acl?.collector_consent_text.orEmpty(),
      default_role =
        acl?.default_role?.takeIf { it in SurveyPatches.SHARING_ROLES } ?: Role.DATA_COLLECTOR,
    )
  }

  companion object {
    /** Largest number of IDs served by one `BatchGetSurveyOverviews` request. */
    const val MAX_BATCH_SURVEY_IDS: Int = 100

    /** Organization whose library is the global library shared by all users. */
    const val ALL_USERS_ORGANIZATION_ID: String =
      org.openforis.collectone.v2.core.impact.ALL_USERS_ORGANIZATION_ID
  }
}
