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
package org.groundplatform.v2.devtools.prototypeapp.organization

import org.groundplatform.v2.devtools.prototypeapp.domain.model.MembershipStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationMember
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationRole

/**
 * Sections of the web organization page, in the order they appear in its left panel. Everyone sees
 * all three; Details is read-only for anyone who isn't a Manager.
 */
enum class OrganizationTab(val label: String) {
  DETAILS("Organization details"),
  SURVEYS("Surveys"),
  MEMBERS("Members"),
}

/** How the signed-in user relates to an organization on the organizations list. */
enum class OrganizationRelation(val label: String) {
  MANAGER("You manage"),
  MEMBER("Member"),
  INVITED("Invited"),
  REQUESTED("Requested to join"),
  NONE(""),
}

/** The Members tab, split into the groups it renders. */
data class OrganizationMembersView(
  val requests: List<OrganizationMember>,
  val invited: List<OrganizationMember>,
  val active: List<OrganizationMember>,
)

/** Pure view logic for the organizations pages, shared with tests. */
object OrganizationPages {

  fun relationOf(organization: Organization, email: String): OrganizationRelation {
    val member = organization.member(email) ?: return OrganizationRelation.NONE
    return when (member.status) {
      MembershipStatus.ACTIVE ->
        if (member.role == OrganizationRole.MANAGER) OrganizationRelation.MANAGER
        else OrganizationRelation.MEMBER
      MembershipStatus.INVITED -> OrganizationRelation.INVITED
      MembershipStatus.REQUESTED -> OrganizationRelation.REQUESTED
    }
  }

  /** Organizations [email] is an active member of, Managers first, then by name. */
  fun mine(organizations: List<Organization>, email: String): List<Organization> =
    organizations
      .filter { it.isMember(email) }
      .sortedWith(compareBy({ !it.isManager(email) }, { it.name.lowercase() }))

  /**
   * Listed organizations [email] isn't an active member of (including ones they've been invited to
   * or asked to join), by name. Unlisted organizations are only reachable by invite.
   */
  fun discoverable(organizations: List<Organization>, email: String): List<Organization> =
    organizations.filter { it.isListed && !it.isMember(email) }.sortedBy { it.name.lowercase() }

  /** Case-insensitive match on name and description. */
  fun search(organizations: List<Organization>, query: String): List<Organization> {
    val q = query.trim()
    if (q.isEmpty()) return organizations
    return organizations.filter {
      it.name.contains(q, ignoreCase = true) || it.description.contains(q, ignoreCase = true)
    }
  }

  /** Whether [email] can edit the organization's details and delete it. */
  fun canEditDetails(organization: Organization, email: String): Boolean =
    organization.isManager(email)

  /**
   * Members grouped for display: pending join requests (Managers only), pending invites (Managers
   * only), then active members with Managers first and the signed-in user at the top of their
   * group.
   */
  fun membersView(organization: Organization, viewerEmail: String): OrganizationMembersView {
    val isManager = organization.isManager(viewerEmail)
    val byName = compareBy<OrganizationMember> { it.displayName.lowercase() }
    return OrganizationMembersView(
      requests = if (isManager) organization.pendingRequests.sortedWith(byName) else emptyList(),
      invited =
        if (isManager) {
          organization.members.filter { it.status == MembershipStatus.INVITED }.sortedWith(byName)
        } else {
          emptyList()
        },
      active =
        organization.activeMembers.sortedWith(
          compareBy<OrganizationMember> { it.role != OrganizationRole.MANAGER }
            .thenBy { !it.email.equals(viewerEmail, ignoreCase = true) }
            .then(byName)
        ),
    )
  }

  /** Whether [member]'s role can be changed or they can be removed by a Manager. */
  fun canChange(organization: Organization, member: OrganizationMember): Boolean =
    !(member.isActiveManager && organization.managers.size == 1)
}
