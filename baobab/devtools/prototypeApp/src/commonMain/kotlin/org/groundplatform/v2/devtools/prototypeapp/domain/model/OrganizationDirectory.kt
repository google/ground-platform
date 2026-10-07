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
package org.groundplatform.v2.devtools.prototypeapp.domain.model

/** How a person relates to an [Organization], as shown on the organizations directory. */
enum class OrganizationRelation(val label: String) {
  MANAGER("You manage"),
  MEMBER("Member"),
  INVITED("Invited"),
  REQUESTED("Requested to join"),
  NONE(""),
}

/** An organization's members split into the groups the Members page renders. */
data class OrganizationMembersView(
  val requests: List<OrganizationMember>,
  val invited: List<OrganizationMember>,
  val active: List<OrganizationMember>,
)

/** [email]'s relation to this organization. Everyone manages the synthetic organization. */
fun Organization.relationTo(email: String): OrganizationRelation {
  if (isSynthetic) return OrganizationRelation.MANAGER
  val member = member(email) ?: return OrganizationRelation.NONE
  return when (member.status) {
    MembershipStatus.ACTIVE ->
      if (member.role == OrganizationRole.MANAGER) OrganizationRelation.MANAGER
      else OrganizationRelation.MEMBER
    MembershipStatus.INVITED -> OrganizationRelation.INVITED
    MembershipStatus.REQUESTED -> OrganizationRelation.REQUESTED
  }
}

/**
 * Organizations [email] is an active member of (plus the synthetic `"All users"` organization if
 * present), with synthetic first, then the ones they manage, then by name.
 */
fun List<Organization>.organizationsOf(email: String): List<Organization> = filter {
  it.isSynthetic || it.isMember(email)
}
  .sortedWith(compareBy({ !it.isSynthetic }, { !it.isManager(email) }, { it.name.lowercase() }))

/**
 * Listed organizations [email] isn't an active member of (including ones they've been invited to or
 * asked to join), by name. Unlisted and synthetic organizations are excluded.
 */
fun List<Organization>.discoverableBy(email: String): List<Organization> = filter {
  !it.isSynthetic && it.isListed && !it.isMember(email)
}
  .sortedBy { it.name.lowercase() }

/** Organizations whose name or description contains [query] (case-insensitive, trimmed). */
fun List<Organization>.searchOrganizations(query: String): List<Organization> {
  val q = query.trim()
  if (q.isEmpty()) return this
  return filter {
    it.name.contains(q, ignoreCase = true) || it.description.contains(q, ignoreCase = true)
  }
}

/**
 * Members grouped for display to [viewerEmail]: pending join requests (Managers only), pending
 * invites (Managers only), then active members with Managers first and the viewer at the top of
 * their group.
 */
fun Organization.membersViewFor(viewerEmail: String): OrganizationMembersView {
  val isManager = isManager(viewerEmail)
  val byName = compareBy<OrganizationMember> { it.displayName.lowercase() }
  return OrganizationMembersView(
    requests = if (isManager) pendingRequests.sortedWith(byName) else emptyList(),
    invited =
      if (isManager) {
        members.filter { it.status == MembershipStatus.INVITED }.sortedWith(byName)
      } else {
        emptyList()
      },
    active =
      activeMembers.sortedWith(
        compareBy<OrganizationMember> { it.role != OrganizationRole.MANAGER }
          .thenBy { !it.email.equals(viewerEmail, ignoreCase = true) }
          .then(byName)
      ),
  )
}

/**
 * Whether [member]'s role can be changed or they can be removed: everyone except the last active
 * Manager.
 */
fun Organization.canChangeMember(member: OrganizationMember): Boolean =
  !(member.isActiveManager && managers.size == 1)
