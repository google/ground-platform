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

import groundplatform.v2.survey.MembershipStatus
import groundplatform.v2.survey.Organization
import groundplatform.v2.survey.OrganizationMember
import groundplatform.v2.survey.OrganizationRole
import groundplatform.v2.survey.PeerDataVisibility
import groundplatform.v2.survey.Role
import groundplatform.v2.survey.SharingPolicy
import groundplatform.v2.survey.SurveyAcl
import groundplatform.v2.survey.SurveyDef
import io.grpc.Status
import io.grpc.StatusException
import org.openforis.collectone.v2.core.mutation.SurveyPatches

/**
 * A caller's access to surveys and organizations (`docs/technical/backend/spanner-schema.md`,
 * "Sharing Policies and Access"), evaluated on [reader]'s snapshot.
 *
 * A survey role is the highest of: owner, the caller's ACL entry, the survey's sharing role when
 * its policy is `ORGANIZATION` and the caller is an active member of its organization, and the
 * sharing role when the policy is `PUBLIC` or `ANYONE_WITH_LINK`. Administrators (the development
 * fallback caller, callers the authenticator marks as admins, and active managers of the all-users
 * organization) may do anything.
 *
 * A null caller (calls made directly on the backend, as in tests) is unrestricted.
 */
internal class Access(private val reader: UserDataReader, private val caller: Caller?) {

  /** A caller's capability on a survey, from none to owner. */
  enum class SurveyRole {
    NONE,
    VIEWER,
    DATA_COLLECTOR,
    SURVEY_ORGANIZER,
    OWNER;

    fun atLeast(other: SurveyRole) = ordinal >= other.ordinal
  }

  val isAdmin: Boolean by lazy {
    caller == null ||
      caller.isDefault ||
      caller.isAdmin ||
      reader.organization(UserDataBackend.ALL_USERS_ORGANIZATION_ID)?.members.orEmpty().any {
        isCaller(it.user_id, it.email) &&
          it.status == MembershipStatus.ACTIVE &&
          it.role == OrganizationRole.MANAGER
      }
  }

  /** The caller's user ID, or empty for a null caller. */
  val userId: String
    get() = caller?.userId.orEmpty()

  /** Whether [userId] or [email] identify the caller (emails only once verified). */
  fun isCaller(userId: String, email: String): Boolean {
    val c = caller ?: return false
    return (userId.isNotBlank() && userId == c.userId) ||
      (c.emailVerified && email.isNotBlank() && email.trim().lowercase() == c.normalizedEmail)
  }

  /** The caller's role on survey [survey] with ACL [acl]. */
  fun surveyRole(survey: SurveyDef?, acl: SurveyAcl?): SurveyRole {
    if (isAdmin) return SurveyRole.OWNER
    if (survey == null) return SurveyRole.NONE
    if (
      isCaller(survey.owner_id, survey.owner_id) ||
        isCaller(acl?.owner_id.orEmpty(), acl?.owner_id.orEmpty())
    ) {
      return SurveyRole.OWNER
    }
    val sharingRole =
      acl?.default_role?.takeIf { it in SurveyPatches.SHARING_ROLES } ?: Role.DATA_COLLECTOR
    val grants = buildList {
      acl?.entries.orEmpty().filter { isCaller(it.user_id, it.email) }.forEach { add(it.role) }
      when (acl?.sharing_policy) {
        SharingPolicy.ORGANIZATION -> if (isActiveMember(survey.organization_id)) add(sharingRole)
        SharingPolicy.PUBLIC,
        SharingPolicy.ANYONE_WITH_LINK -> add(sharingRole)
        else -> {}
      }
    }
    return grants.maxOfOrNull { it.toSurveyRole() } ?: SurveyRole.NONE
  }

  /** The caller's role on survey [surveyId]. */
  fun surveyRole(surveyId: String): SurveyRole =
    surveyRole(reader.survey(surveyId), reader.acl(surveyId))

  /**
   * Whether survey [survey] belongs in the caller's survey list: owned, on the ACL, or shared with
   * the caller's organization. Link and public surveys aren't listed.
   */
  fun listsSurvey(survey: SurveyDef, acl: SurveyAcl?): Boolean {
    if (isAdmin) return true
    if (
      isCaller(survey.owner_id, survey.owner_id) ||
        isCaller(acl?.owner_id.orEmpty(), acl?.owner_id.orEmpty())
    ) {
      return true
    }
    if (acl?.entries.orEmpty().any { isCaller(it.user_id, it.email) }) return true
    return acl?.sharing_policy == SharingPolicy.ORGANIZATION &&
      isActiveMember(survey.organization_id)
  }

  /** Fails with `PERMISSION_DENIED` unless the caller has at least [role] on survey [surveyId]. */
  fun requireSurvey(surveyId: String, role: SurveyRole): SurveyRole {
    val actual = surveyRole(surveyId)
    if (!actual.atLeast(role)) throw denied("survey $surveyId")
    return actual
  }

  /**
   * Whether the caller sees only records it created in survey [surveyId] (`OWN_ONLY` peer data
   * visibility, below organizer).
   */
  fun seesOwnRecordsOnly(surveyId: String, role: SurveyRole): Boolean =
    !role.atLeast(SurveyRole.SURVEY_ORGANIZER) &&
      reader.acl(surveyId)?.peer_data_visibility == PeerDataVisibility.OWN_ONLY

  /** The caller's membership of organization [organization], if any. */
  fun membership(organization: Organization?): OrganizationMember? =
    organization?.members.orEmpty().firstOrNull { isCaller(it.user_id, it.email) }

  fun isActiveMember(organizationId: String): Boolean =
    organizationId.isNotBlank() &&
      membership(reader.organization(organizationId))?.status == MembershipStatus.ACTIVE

  /** Whether the caller manages organization [organization] (or is an administrator). */
  fun manages(organization: Organization?): Boolean =
    isAdmin ||
      membership(organization)?.let {
        it.status == MembershipStatus.ACTIVE && it.role == OrganizationRole.MANAGER
      } == true

  /** Whether the caller can see organization [organization]'s members and private settings. */
  fun belongsTo(organization: Organization?): Boolean = isAdmin || membership(organization) != null

  /**
   * [organization] as the caller may see it: complete for administrators and active members,
   * otherwise with only the caller's own membership.
   */
  fun viewOf(organization: Organization): Organization =
    if (isAdmin || membership(organization)?.status == MembershipStatus.ACTIVE) organization
    else organization.copy(members = organization.members.filter { isCaller(it.user_id, it.email) })

  /** Whether the caller can read the library of organization [organizationId]. */
  fun readsLibrary(organizationId: String): Boolean =
    organizationId.isBlank() ||
      organizationId == UserDataBackend.ALL_USERS_ORGANIZATION_ID ||
      isAdmin ||
      isActiveMember(organizationId)

  companion object {
    fun denied(what: String) =
      StatusException(Status.PERMISSION_DENIED.withDescription("No access to $what"))

    private fun Role.toSurveyRole(): SurveyRole =
      when (this) {
        Role.VIEWER -> SurveyRole.VIEWER
        Role.DATA_COLLECTOR -> SurveyRole.DATA_COLLECTOR
        Role.SURVEY_ORGANIZER -> SurveyRole.SURVEY_ORGANIZER
        else -> SurveyRole.NONE
      }
  }
}
