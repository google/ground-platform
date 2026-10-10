/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.devtools.prototypeapp.data.repository

import kotlinx.coroutines.flow.Flow
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.LocalStore
import org.groundplatform.v2.devtools.prototypeapp.domain.model.CachedProfile
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MembershipStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationMember
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationRole
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.OrganizationRepository

/** [OrganizationRepository] backed by the [LocalStore]. */
class OrganizationRepositoryImpl(private val store: LocalStore) : OrganizationRepository {
  override fun observeOrganizations(): Flow<List<Organization>> = store.observeOrganizations()

  override suspend fun getOrganizations(): List<Organization> = store.transaction {
    organizations()
  }

  override suspend fun getOrganization(organizationId: String): Organization? = store.transaction {
    organization(organizationId)
  }

  override suspend fun getOrganizationsFor(email: String): List<Organization> = store.transaction {
    organizations().filter { it.isMember(email) }
  }

  override suspend fun createOrganization(
    organization: Organization,
    creatorEmail: String,
    creatorProfile: CachedProfile?,
  ): Organization? = store.transaction {
    if (organization(organization.id) != null) return@transaction null
    val creator =
      OrganizationMember(
        email = creatorEmail,
        role = OrganizationRole.MANAGER,
        status = MembershipStatus.ACTIVE,
        profile = creatorProfile,
      )
    val others = organization.members.filterNot { it.email.equals(creatorEmail, ignoreCase = true) }
    organization.copy(members = listOf(creator) + others).also { upsertOrganization(it) }
  }

  override suspend fun updateOrganization(
    organizationId: String,
    transform: (Organization) -> Organization,
  ): Organization? = store.transaction {
    // Profile edits must not change the identity or membership through this path, and the
    // synthetic "All users" organization always stays unlisted.
    updateOrganization(organizationId) { existing ->
      val updated = transform(existing).copy(id = existing.id, members = existing.members)
      if (existing.isSynthetic) updated.copy(isListed = false, isSynthetic = true) else updated
    }
  }

  override suspend fun deleteOrganization(organizationId: String) {
    store.transaction { deleteOrganization(organizationId) }
  }

  override suspend fun inviteMember(
    organizationId: String,
    email: String,
    role: OrganizationRole,
    token: String,
  ): Organization? = store.transaction {
    // Everyone is implicitly a member of "All users"; only Managers are invited explicitly.
    if (organization(organizationId)?.isSynthetic == true && role != OrganizationRole.MANAGER) {
      return@transaction null
    }
    editMembers(organizationId) { members ->
      if (members.any { it.email.equals(email, ignoreCase = true) }) return@editMembers null
      members +
        OrganizationMember(
          email = email,
          role = role,
          status = MembershipStatus.INVITED,
          inviteToken = token,
        )
    }
  }

  override suspend fun acceptInvite(
    organizationId: String,
    email: String,
    userId: String,
    profile: CachedProfile,
  ): Organization? =
    editMember(organizationId, email) { member ->
      if (member.status != MembershipStatus.INVITED) return@editMember null
      member.copy(
        status = MembershipStatus.ACTIVE,
        inviteToken = null,
        userId = userId,
        profile = profile,
      )
    }

  override suspend fun requestToJoin(
    organizationId: String,
    email: String,
    profile: CachedProfile?,
  ): Organization? = store.transaction {
    // Nobody asks to join "All users": everyone is implicitly a member.
    if (organization(organizationId)?.isSynthetic == true) return@transaction null
    editMembers(organizationId) { members ->
      if (members.any { it.email.equals(email, ignoreCase = true) }) return@editMembers null
      members +
        OrganizationMember(
          email = email,
          role = OrganizationRole.MEMBER,
          status = MembershipStatus.REQUESTED,
          profile = profile,
        )
    }
  }

  override suspend fun approveRequest(organizationId: String, email: String): Organization? =
    editMember(organizationId, email) { member ->
      if (member.status != MembershipStatus.REQUESTED) return@editMember null
      member.copy(status = MembershipStatus.ACTIVE)
    }

  override suspend fun setMemberRole(
    organizationId: String,
    email: String,
    role: OrganizationRole,
  ): Organization? = store.transaction {
    val org = organization(organizationId) ?: return@transaction null
    val member = org.member(email) ?: return@transaction null
    if (member.isActiveManager && role != OrganizationRole.MANAGER && org.managers.size == 1) {
      return@transaction null
    }
    // "All users" has explicit Managers only; everyone else is implicitly a member.
    if (org.isSynthetic && role != OrganizationRole.MANAGER) return@transaction null
    updateOrganization(organizationId) { o ->
      o.copy(members = o.members.map { if (it === member) it.copy(role = role) else it })
    }
  }

  override suspend fun removeMember(organizationId: String, email: String): Organization? =
    store.transaction {
      val org = organization(organizationId) ?: return@transaction null
      val member = org.member(email) ?: return@transaction null
      if (member.isActiveManager && org.managers.size == 1) return@transaction null
      updateOrganization(organizationId) { o -> o.copy(members = o.members - member) }
    }

  /** Applies [transform] to the member list; a `null` result leaves the organization unchanged. */
  private suspend fun editMembers(
    organizationId: String,
    transform: (List<OrganizationMember>) -> List<OrganizationMember>?,
  ): Organization? = store.transaction {
    val org = organization(organizationId) ?: return@transaction null
    val members = transform(org.members) ?: return@transaction null
    updateOrganization(organizationId) { it.copy(members = members) }
  }

  /** Applies [transform] to one member; a `null` result leaves the organization unchanged. */
  private suspend fun editMember(
    organizationId: String,
    email: String,
    transform: (OrganizationMember) -> OrganizationMember?,
  ): Organization? =
    editMembers(organizationId) { members ->
      val member =
        members.firstOrNull { it.email.equals(email, ignoreCase = true) } ?: return@editMembers null
      val updated = transform(member) ?: return@editMembers null
      members.map { if (it === member) updated else it }
    }
}
