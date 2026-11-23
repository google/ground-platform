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
import io.grpc.StatusException
import openforis.collectone.v1.survey.AclEntry
import openforis.collectone.v1.survey.CachedProfile
import openforis.collectone.v1.survey.MembershipStatus
import openforis.collectone.v1.survey.Organization
import openforis.collectone.v1.survey.OrganizationMember
import openforis.collectone.v1.survey.OrganizationRole
import openforis.collectone.v1.survey.Role
import openforis.collectone.v1.survey.SurveyAcl
import openforis.collectone.v1.survey.SurveyDef
import org.openforis.collectone.v2.core.access.AccessCaller
import org.openforis.collectone.v2.core.access.AccessControl
import org.openforis.collectone.v2.core.access.SurveyRole
import org.openforis.collectone.v2.core.impact.ALL_USERS_ORGANIZATION_ID
import org.openforis.collectone.v2.server.auth.Caller
import org.openforis.collectone.v2.server.data.UserDataReader

typealias SurveyRole = org.openforis.collectone.v2.core.access.SurveyRole

/**
 * A caller's access to surveys and organizations (`docs/technical/backend/spanner-schema.md`,
 * "Sharing Policies and Access"), evaluated on [reader]'s snapshot.
 *
 * Delegates role evaluation, membership, and policy checks to [AccessControl].
 */
internal class Access(private val reader: UserDataReader, private val caller: Caller?) {

  private fun matchesCaller(userId: String, email: String): Boolean {
    val c = caller ?: return false
    return (userId.isNotBlank() && userId == c.userId) ||
      (c.emailVerified && email.isNotBlank() && email.trim().lowercase() == c.normalizedEmail)
  }

  val isAdmin: Boolean by lazy {
    caller == null ||
      caller.isDefault ||
      caller.isAdmin ||
      reader.organization(ALL_USERS_ORGANIZATION_ID)?.members.orEmpty().any {
        matchesCaller(it.user_id, it.email) &&
          it.status == MembershipStatus.ACTIVE &&
          it.role == OrganizationRole.MANAGER
      }
  }

  val accessCaller: AccessCaller by lazy {
    AccessCaller(
      userId = caller?.userId.orEmpty(),
      email = caller?.email.orEmpty(),
      emailVerified = caller?.emailVerified ?: true,
      isAdmin = isAdmin,
    )
  }

  /** The caller's user ID, or empty for a null caller. */
  val userId: String
    get() = caller?.userId.orEmpty()

  /** Whether [userId] or [email] identify the caller (emails only once verified). */
  fun isCaller(userId: String, email: String): Boolean = accessCaller.matches(userId, email)

  /** The caller's role on survey [survey] with ACL [acl]. */
  fun surveyRole(survey: SurveyDef?, acl: SurveyAcl?): SurveyRole =
    AccessControl.surveyRole(
      survey = survey,
      acl = acl,
      caller = accessCaller,
      organization =
        survey?.organization_id?.takeIf { it.isNotBlank() }?.let { reader.organization(it) },
    )

  /** The caller's role on survey [surveyId]. */
  fun surveyRole(surveyId: String): SurveyRole =
    surveyRole(reader.survey(surveyId), reader.acl(surveyId))

  /**
   * Whether survey [survey] belongs in the caller's survey list: owned, on the ACL, or shared with
   * the caller's organization. Link and public surveys aren't listed.
   */
  fun listsSurvey(survey: SurveyDef, acl: SurveyAcl?): Boolean =
    AccessControl.listsSurvey(
      survey = survey,
      acl = acl,
      caller = accessCaller,
      organization =
        survey.organization_id.takeIf { it.isNotBlank() }?.let { reader.organization(it) },
    )

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
    AccessControl.seesOwnRecordsOnly(reader.acl(surveyId)?.peer_data_visibility, role)

  /** The caller's membership of organization [organization], if any. */
  fun membership(organization: Organization?): OrganizationMember? =
    AccessControl.membership(organization, accessCaller)

  fun isActiveMember(organizationId: String): Boolean =
    organizationId.isNotBlank() &&
      AccessControl.isActiveMember(reader.organization(organizationId), accessCaller)

  /** Whether the caller manages organization [organization] (or is an administrator). */
  fun manages(organization: Organization?): Boolean =
    AccessControl.managesOrganization(organization, accessCaller)

  /** Whether the caller can see organization [organization]'s members and private settings. */
  fun belongsTo(organization: Organization?): Boolean =
    AccessControl.belongsToOrganization(organization, accessCaller)

  /**
   * [organization] as the caller may see it: complete for administrators and active members,
   * otherwise with only the caller's own membership. When the authenticator marks a non-default
   * caller as an administrator ([Caller.isAdmin]), the view includes them as an active manager so
   * the client UI reflects their administrative access.
   */
  fun viewOf(organization: Organization): Organization {
    if (!isAdmin && membership(organization)?.status != MembershipStatus.ACTIVE) {
      return organization.copy(
        members = organization.members.filter { isCaller(it.user_id, it.email) }
      )
    }
    val c = caller
    if (
      c == null ||
        c.isDefault ||
        !isAdmin ||
        c.email.isBlank() ||
        membership(organization)?.let {
          it.status == MembershipStatus.ACTIVE && it.role == OrganizationRole.MANAGER
        } == true
    ) {
      return organization
    }
    val existingIdx = organization.members.indexOfFirst { isCaller(it.user_id, it.email) }
    val adminMember =
      OrganizationMember(
        user_id = c.userId,
        email = c.email,
        role = OrganizationRole.MANAGER,
        status = MembershipStatus.ACTIVE,
        profile = CachedProfile(display_name = c.displayName, avatar_url = c.photoUrl),
      )
    val updatedMembers =
      if (existingIdx >= 0) {
        organization.members.toMutableList().apply { set(existingIdx, adminMember) }
      } else {
        organization.members + adminMember
      }
    return organization.copy(members = updatedMembers)
  }

  /**
   * [acl] as a caller with [role] may see it: collaborator entries only for organizers, and
   * including a non-default [Caller.isAdmin] as an accepted survey organizer so the client UI
   * allows editing personal and organization surveys.
   */
  fun viewOf(acl: SurveyAcl, role: SurveyRole): SurveyAcl {
    if (!role.atLeast(SurveyRole.SURVEY_ORGANIZER)) return acl.copy(entries = emptyList())
    val c = caller
    if (
      c == null ||
        c.isDefault ||
        !isAdmin ||
        c.email.isBlank() ||
        isCaller(acl.owner_id, acl.owner_id) ||
        acl.entries.any {
          isCaller(it.user_id, it.email) &&
            it.invitation_status == AclEntry.InvitationStatus.ACCEPTED &&
            it.role == Role.SURVEY_ORGANIZER
        }
    ) {
      return acl
    }
    val existingIdx = acl.entries.indexOfFirst { isCaller(it.user_id, it.email) }
    val adminEntry =
      AclEntry(
        user_id = c.userId,
        email = c.email,
        role = Role.SURVEY_ORGANIZER,
        invitation_status = AclEntry.InvitationStatus.ACCEPTED,
        profile = CachedProfile(display_name = c.displayName, avatar_url = c.photoUrl),
      )
    val updatedEntries =
      if (existingIdx >= 0) {
        acl.entries.toMutableList().apply { set(existingIdx, adminEntry) }
      } else {
        acl.entries + adminEntry
      }
    return acl.copy(entries = updatedEntries)
  }

  /** Whether the caller can read the library of organization [organizationId]. */
  fun readsLibrary(organizationId: String): Boolean =
    AccessControl.readsLibrary(
      organization = reader.organization(organizationId),
      organizationId = organizationId,
      caller = accessCaller,
      allUsersOrgId = ALL_USERS_ORGANIZATION_ID,
    )

  companion object {
    fun denied(what: String) =
      StatusException(Status.PERMISSION_DENIED.withDescription("No access to $what"))
  }
}
