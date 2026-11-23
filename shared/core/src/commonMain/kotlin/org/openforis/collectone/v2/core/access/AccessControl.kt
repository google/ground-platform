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
package org.openforis.collectone.v2.core.access

import openforis.collectone.v1.survey.AclEntry
import openforis.collectone.v1.survey.MembershipStatus
import openforis.collectone.v1.survey.Organization
import openforis.collectone.v1.survey.OrganizationMember
import openforis.collectone.v1.survey.OrganizationRole
import openforis.collectone.v1.survey.PeerDataVisibility
import openforis.collectone.v1.survey.Role
import openforis.collectone.v1.survey.SharingPolicy
import openforis.collectone.v1.survey.SurveyAcl
import openforis.collectone.v1.survey.SurveyDef
import org.openforis.collectone.v2.core.mutation.SurveyMutations

/**
 * Caller identity used for evaluating access control across backend services and frontend clients.
 */
data class AccessCaller(
  val userId: String = "",
  val email: String = "",
  val emailVerified: Boolean = true,
  val isAdmin: Boolean = false,
) {
  val normalizedEmail: String = email.trim().lowercase()

  /**
   * Whether [targetUserId] or [targetEmail] identify this caller. Email matches are only honored
   * when [emailVerified] is true.
   */
  fun matches(targetUserId: String?, targetEmail: String?): Boolean {
    if (!targetUserId.isNullOrBlank() && userId.isNotBlank() && targetUserId == userId) {
      return true
    }
    if (emailVerified && !targetEmail.isNullOrBlank() && normalizedEmail.isNotBlank()) {
      return targetEmail.trim().lowercase() == normalizedEmail
    }
    return false
  }
}

/** Survey access capability level from least permissive to owner. */
enum class SurveyRole {
  NONE,
  VIEWER,
  DATA_COLLECTOR,
  SURVEY_ORGANIZER,
  OWNER;

  fun atLeast(other: SurveyRole): Boolean = ordinal >= other.ordinal

  val canView: Boolean
    get() = atLeast(VIEWER)

  val canCollectData: Boolean
    get() = atLeast(DATA_COLLECTOR)

  val canManage: Boolean
    get() = atLeast(SURVEY_ORGANIZER)
}

/**
 * Shared, cross-platform access control evaluation logic for surveys, organizations, and datasets.
 */
object AccessControl {

  const val ALL_USERS_ORGANIZATION_ID: String =
    org.openforis.collectone.v2.core.impact.ALL_USERS_ORGANIZATION_ID

  fun Role.toSurveyRole(): SurveyRole =
    when (this) {
      Role.VIEWER -> SurveyRole.VIEWER
      Role.DATA_COLLECTOR -> SurveyRole.DATA_COLLECTOR
      Role.SURVEY_ORGANIZER -> SurveyRole.SURVEY_ORGANIZER
      else -> SurveyRole.NONE
    }

  /**
   * Resolves the effective sharing role from [defaultRole], defaulting to [Role.DATA_COLLECTOR] if
   * unspecified or invalid.
   */
  fun effectiveSharingRole(defaultRole: Role?): Role =
    defaultRole?.takeIf { it in SurveyMutations.SHARING_ROLES } ?: Role.DATA_COLLECTOR

  /** Resolves the effective sharing role as a [SurveyRole]. */
  fun effectiveSharingSurveyRole(defaultRole: Role?): SurveyRole =
    effectiveSharingRole(defaultRole).toSurveyRole()

  /** The caller's membership in [organization], if any. */
  fun membership(organization: Organization?, caller: AccessCaller): OrganizationMember? =
    organization?.members.orEmpty().firstOrNull { caller.matches(it.user_id, it.email) }

  /** Whether the caller is an active member or manager of [organization]. */
  fun isActiveMember(organization: Organization?, caller: AccessCaller): Boolean =
    caller.isAdmin || membership(organization, caller)?.status == MembershipStatus.ACTIVE

  /** Whether the caller is an active manager of [organization], or a platform administrator. */
  fun managesOrganization(organization: Organization?, caller: AccessCaller): Boolean =
    caller.isAdmin ||
      membership(organization, caller)?.let {
        it.status == MembershipStatus.ACTIVE && it.role == OrganizationRole.MANAGER
      } == true

  /**
   * Whether the caller belongs to [organization] (active, invited, or requested), or is an
   * administrator.
   */
  fun belongsToOrganization(organization: Organization?, caller: AccessCaller): Boolean =
    caller.isAdmin || membership(organization, caller) != null

  /** Whether the caller can view the public or private details of [organization]. */
  fun readsOrganization(organization: Organization?, caller: AccessCaller): Boolean =
    caller.isAdmin || organization?.listed == true || belongsToOrganization(organization, caller)

  /** Whether the caller can read the library of organization [organizationId]. */
  fun readsLibrary(
    organization: Organization?,
    organizationId: String,
    caller: AccessCaller,
    allUsersOrgId: String = ALL_USERS_ORGANIZATION_ID,
  ): Boolean =
    organizationId.isBlank() ||
      organizationId == allUsersOrgId ||
      caller.isAdmin ||
      isActiveMember(organization, caller)

  /**
   * Evaluates the caller's [SurveyRole] on [survey] governed by [acl] and optional [organization].
   */
  fun surveyRole(
    survey: SurveyDef?,
    acl: SurveyAcl?,
    caller: AccessCaller,
    organization: Organization? = null,
  ): SurveyRole {
    if (caller.isAdmin) return SurveyRole.OWNER
    if (survey == null) return SurveyRole.NONE
    if (
      caller.matches(survey.owner_id, survey.owner_id) ||
        caller.matches(acl?.owner_id.orEmpty(), acl?.owner_id.orEmpty())
    ) {
      return SurveyRole.OWNER
    }
    val sharingRole = effectiveSharingRole(acl?.default_role)
    val grants = buildList {
      acl
        ?.entries
        .orEmpty()
        .filter {
          caller.matches(it.user_id, it.email) &&
            it.invitation_status != AclEntry.InvitationStatus.PENDING
        }
        .forEach { add(it.role) }
      if (survey.organization_id.isNotBlank() && managesOrganization(organization, caller)) {
        add(Role.SURVEY_ORGANIZER)
      }
      when (acl?.sharing_policy) {
        SharingPolicy.ORGANIZATION -> if (isActiveMember(organization, caller)) add(sharingRole)
        SharingPolicy.PUBLIC,
        SharingPolicy.ANYONE_WITH_LINK -> add(sharingRole)
        else -> {}
      }
    }
    return grants.maxOfOrNull { it.toSurveyRole() } ?: SurveyRole.NONE
  }

  /**
   * Whether survey [survey] belongs in the caller's survey list: owned, on the ACL, or shared with
   * the caller's organization. Link and public surveys are not listed in the personal library.
   */
  fun listsSurvey(
    survey: SurveyDef,
    acl: SurveyAcl?,
    caller: AccessCaller,
    organization: Organization? = null,
  ): Boolean {
    if (caller.isAdmin) return true
    if (
      caller.matches(survey.owner_id, survey.owner_id) ||
        caller.matches(acl?.owner_id.orEmpty(), acl?.owner_id.orEmpty())
    ) {
      return true
    }
    if (
      acl?.entries.orEmpty().any {
        caller.matches(it.user_id, it.email) &&
          it.invitation_status != AclEntry.InvitationStatus.PENDING
      }
    ) {
      return true
    }
    if (survey.organization_id.isNotBlank() && managesOrganization(organization, caller)) {
      return true
    }
    return acl?.sharing_policy == SharingPolicy.ORGANIZATION && isActiveMember(organization, caller)
  }

  /** Whether the caller sees only records they created in the survey. */
  fun seesOwnRecordsOnly(peerDataVisibility: PeerDataVisibility?, role: SurveyRole): Boolean =
    !role.atLeast(SurveyRole.SURVEY_ORGANIZER) && peerDataVisibility == PeerDataVisibility.OWN_ONLY
}
