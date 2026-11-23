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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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

class AccessControlTest {

  private val owner = AccessCaller(userId = "uid-owner", email = "owner@example.org")
  private val manager = AccessCaller(userId = "uid-mgr", email = "manager@example.org")
  private val member = AccessCaller(userId = "uid-mem", email = "member@example.org")
  private val requester = AccessCaller(userId = "uid-req", email = "requester@example.org")
  private val collector = AccessCaller(userId = "uid-col", email = "collector@example.org")
  private val viewer = AccessCaller(userId = "uid-view", email = "viewer@example.org")
  private val invitee = AccessCaller(userId = "uid-inv", email = "invitee@example.org")
  private val stranger = AccessCaller(userId = "uid-stranger", email = "stranger@example.org")
  private val admin =
    AccessCaller(userId = "uid-admin", email = "admin@example.org", isAdmin = true)

  private val organization =
    Organization(
      organization_id = "org-1",
      name = "Test Org",
      listed = true,
      members =
        listOf(
          OrganizationMember(
            user_id = manager.userId,
            email = manager.email,
            role = OrganizationRole.MANAGER,
            status = MembershipStatus.ACTIVE,
          ),
          OrganizationMember(
            user_id = member.userId,
            email = member.email,
            role = OrganizationRole.MEMBER,
            status = MembershipStatus.ACTIVE,
          ),
          OrganizationMember(
            user_id = requester.userId,
            email = requester.email,
            role = OrganizationRole.MEMBER,
            status = MembershipStatus.REQUESTED,
          ),
        ),
    )

  private val baseSurvey =
    SurveyDef(
      survey_id = "survey-1",
      owner_id = owner.userId,
      organization_id = organization.organization_id,
    )

  private val baseAcl =
    SurveyAcl(
      survey_id = "survey-1",
      owner_id = owner.userId,
      sharing_policy = SharingPolicy.RESTRICTED,
      entries =
        listOf(
          AclEntry(
            user_id = collector.userId,
            email = collector.email,
            role = Role.DATA_COLLECTOR,
            invitation_status = AclEntry.InvitationStatus.ACCEPTED,
          ),
          AclEntry(
            user_id = viewer.userId,
            email = viewer.email,
            role = Role.VIEWER,
            invitation_status = AclEntry.InvitationStatus.ACCEPTED,
          ),
          AclEntry(
            user_id = invitee.userId,
            email = invitee.email,
            role = Role.SURVEY_ORGANIZER,
            invitation_status = AclEntry.InvitationStatus.PENDING,
          ),
        ),
    )

  @Test
  fun accessCaller_sameUserId_matchesWithoutEmail() {
    assertTrue(verifiedCaller.matches("uid-1", ""))
  }

  @Test
  fun accessCaller_sameUserId_matchesDespiteDifferentEmail() {
    assertTrue(verifiedCaller.matches("uid-1", "other@example.org"))
  }

  @Test
  fun accessCaller_verifiedEmail_matchesCaseInsensitively() {
    assertTrue(verifiedCaller.matches("", "Test@Example.org"))
  }

  @Test
  fun accessCaller_differentUserIdAndEmail_doesNotMatch() {
    assertFalse(verifiedCaller.matches("uid-2", "other@example.org"))
  }

  @Test
  fun accessCaller_blankUserIdAndEmail_doesNotMatch() {
    assertFalse(verifiedCaller.matches("", ""))
  }

  @Test
  fun accessCaller_unverifiedEmail_stillMatchesByUserId() {
    val unverified = verifiedCaller.copy(emailVerified = false)

    assertTrue(unverified.matches("uid-1", ""))
  }

  @Test
  fun accessCaller_unverifiedEmail_doesNotMatchByEmail() {
    val unverified = verifiedCaller.copy(emailVerified = false)

    assertFalse(unverified.matches("", "Test@Example.org"))
  }

  private val verifiedCaller =
    AccessCaller(userId = "uid-1", email = "test@example.org", emailVerified = true)

  @Test
  fun surveyRole_atLeast_ranksOwnerAboveOrganizerAboveCollectorAboveViewerAboveNone() {
    assertTrue(SurveyRole.OWNER.atLeast(SurveyRole.SURVEY_ORGANIZER))
    assertTrue(SurveyRole.SURVEY_ORGANIZER.atLeast(SurveyRole.DATA_COLLECTOR))
    assertTrue(SurveyRole.DATA_COLLECTOR.atLeast(SurveyRole.VIEWER))
    assertTrue(SurveyRole.VIEWER.atLeast(SurveyRole.NONE))
  }

  @Test
  fun surveyRole_canManage_onlyFromSurveyOrganizerUp() {
    assertTrue(SurveyRole.OWNER.canManage)
    assertTrue(SurveyRole.SURVEY_ORGANIZER.canManage)
    assertFalse(SurveyRole.DATA_COLLECTOR.canManage)
  }

  @Test
  fun surveyRole_canCollectData_onlyFromDataCollectorUp() {
    assertTrue(SurveyRole.DATA_COLLECTOR.canCollectData)
    assertFalse(SurveyRole.VIEWER.canCollectData)
  }

  @Test
  fun surveyRole_canView_onlyFromViewerUp() {
    assertTrue(SurveyRole.VIEWER.canView)
    assertFalse(SurveyRole.NONE.canView)
  }

  @Test
  fun effectiveSharingRole_viewerOrDataCollector_isKept() {
    assertEquals(Role.VIEWER, AccessControl.effectiveSharingRole(Role.VIEWER))
    assertEquals(Role.DATA_COLLECTOR, AccessControl.effectiveSharingRole(Role.DATA_COLLECTOR))
  }

  @Test
  fun effectiveSharingRole_surveyOrganizer_isCappedAtDataCollector() {
    assertEquals(Role.DATA_COLLECTOR, AccessControl.effectiveSharingRole(Role.SURVEY_ORGANIZER))
  }

  @Test
  fun effectiveSharingRole_unset_defaultsToDataCollector() {
    assertEquals(Role.DATA_COLLECTOR, AccessControl.effectiveSharingRole(null))
  }

  @Test
  fun surveyRole_surveyOwner_isOwner() {
    assertEquals(
      SurveyRole.OWNER,
      AccessControl.surveyRole(baseSurvey, baseAcl, owner, organization),
    )
  }

  @Test
  fun surveyRole_admin_isOwner() {
    assertEquals(
      SurveyRole.OWNER,
      AccessControl.surveyRole(baseSurvey, baseAcl, admin, organization),
    )
  }

  @Test
  fun surveyRole_acceptedAclCollector_isDataCollector() {
    assertEquals(
      SurveyRole.DATA_COLLECTOR,
      AccessControl.surveyRole(baseSurvey, baseAcl, collector, organization),
    )
  }

  @Test
  fun surveyRole_acceptedAclViewer_isViewer() {
    assertEquals(
      SurveyRole.VIEWER,
      AccessControl.surveyRole(baseSurvey, baseAcl, viewer, organization),
    )
  }

  @Test
  fun surveyRole_pendingAclInvitee_isNone() {
    // A pending collaborator does not get access until accepted.
    assertEquals(
      SurveyRole.NONE,
      AccessControl.surveyRole(baseSurvey, baseAcl, invitee, organization),
    )
  }

  @Test
  fun surveyRole_organizationManager_isSurveyOrganizer() {
    assertEquals(
      SurveyRole.SURVEY_ORGANIZER,
      AccessControl.surveyRole(baseSurvey, baseAcl, manager, organization),
    )
  }

  @Test
  fun surveyRole_restrictedPolicy_activeOrganizationMemberIsNone() {
    assertEquals(
      SurveyRole.NONE,
      AccessControl.surveyRole(baseSurvey, baseAcl, member, organization),
    )
  }

  @Test
  fun surveyRole_organizationPolicy_activeMemberGetsDefaultRole() {
    assertEquals(
      SurveyRole.DATA_COLLECTOR,
      AccessControl.surveyRole(baseSurvey, orgAclDefaultCollector, member, organization),
    )
  }

  @Test
  fun surveyRole_organizationPolicy_requestedMemberIsNone() {
    assertEquals(
      SurveyRole.NONE,
      AccessControl.surveyRole(baseSurvey, orgAclDefaultCollector, requester, organization),
    )
  }

  @Test
  fun surveyRole_organizationPolicy_nonMemberIsNone() {
    assertEquals(
      SurveyRole.NONE,
      AccessControl.surveyRole(baseSurvey, orgAclDefaultCollector, stranger, organization),
    )
  }

  @Test
  fun surveyRole_organizationPolicyWithViewerDefault_activeMemberCanViewButNotCollect() {
    val orgAclDefaultViewer =
      baseAcl.copy(sharing_policy = SharingPolicy.ORGANIZATION, default_role = Role.VIEWER)

    val viewerRole = AccessControl.surveyRole(baseSurvey, orgAclDefaultViewer, member, organization)

    assertEquals(SurveyRole.VIEWER, viewerRole)
    assertTrue(viewerRole.canView)
    assertFalse(viewerRole.canCollectData)
  }

  private val orgAclDefaultCollector =
    baseAcl.copy(sharing_policy = SharingPolicy.ORGANIZATION, default_role = Role.DATA_COLLECTOR)

  @Test
  fun surveyRole_linkOrPublicPolicyWithCollectorDefault_strangerIsDataCollector() {
    for (policy in listOf(SharingPolicy.ANYONE_WITH_LINK, SharingPolicy.PUBLIC)) {
      val linkAclCollector =
        baseAcl.copy(sharing_policy = policy, default_role = Role.DATA_COLLECTOR)

      assertEquals(
        SurveyRole.DATA_COLLECTOR,
        AccessControl.surveyRole(baseSurvey, linkAclCollector, stranger, organization = null),
        "policy $policy",
      )
    }
  }

  @Test
  fun surveyRole_linkOrPublicPolicyWithViewerDefault_strangerCanViewButNotCollect() {
    for (policy in listOf(SharingPolicy.ANYONE_WITH_LINK, SharingPolicy.PUBLIC)) {
      val linkAclViewer = baseAcl.copy(sharing_policy = policy, default_role = Role.VIEWER)

      val evaluatedRole =
        AccessControl.surveyRole(baseSurvey, linkAclViewer, stranger, organization = null)

      assertEquals(SurveyRole.VIEWER, evaluatedRole, "policy $policy")
      assertTrue(evaluatedRole.canView, "policy $policy")
      assertFalse(evaluatedRole.canCollectData, "policy $policy")
    }
  }

  @Test
  fun surveyRole_highestRoleWins() {
    // If a manager is also explicitly given DATA_COLLECTOR on the ACL, manager's SURVEY_ORGANIZER
    // wins.
    val managerAsCollectorAcl =
      baseAcl.copy(
        entries =
          baseAcl.entries +
            AclEntry(
              user_id = manager.userId,
              email = manager.email,
              role = Role.DATA_COLLECTOR,
              invitation_status = AclEntry.InvitationStatus.ACCEPTED,
            )
      )
    assertEquals(
      SurveyRole.SURVEY_ORGANIZER,
      AccessControl.surveyRole(baseSurvey, managerAsCollectorAcl, manager, organization),
    )
  }

  @Test
  fun listsSurvey_owner_isListed() {
    assertTrue(AccessControl.listsSurvey(baseSurvey, baseAcl, owner, organization))
  }

  @Test
  fun listsSurvey_acceptedAclCollaborator_isListed() {
    assertTrue(AccessControl.listsSurvey(baseSurvey, baseAcl, collector, organization))
  }

  @Test
  fun listsSurvey_organizationManager_isListed() {
    assertTrue(AccessControl.listsSurvey(baseSurvey, baseAcl, manager, organization))
  }

  @Test
  fun listsSurvey_pendingAclInvitee_isNotListed() {
    assertFalse(AccessControl.listsSurvey(baseSurvey, baseAcl, invitee, organization))
  }

  @Test
  fun listsSurvey_restrictedPolicy_activeOrganizationMemberIsNotListed() {
    assertFalse(AccessControl.listsSurvey(baseSurvey, baseAcl, member, organization))
  }

  @Test
  fun listsSurvey_stranger_isNotListed() {
    assertFalse(AccessControl.listsSurvey(baseSurvey, baseAcl, stranger, organization))
  }

  @Test
  fun listsSurvey_organizationPolicy_activeMemberIsListed() {
    val orgShared = baseAcl.copy(sharing_policy = SharingPolicy.ORGANIZATION)

    assertTrue(AccessControl.listsSurvey(baseSurvey, orgShared, member, organization))
  }

  @Test
  fun listsSurvey_organizationPolicy_requestedMemberIsNotListed() {
    val orgShared = baseAcl.copy(sharing_policy = SharingPolicy.ORGANIZATION)

    assertFalse(AccessControl.listsSurvey(baseSurvey, orgShared, requester, organization))
  }

  @Test
  fun listsSurvey_linkPolicy_strangerIsNotListed() {
    val linkAcl = baseAcl.copy(sharing_policy = SharingPolicy.ANYONE_WITH_LINK)

    assertFalse(AccessControl.listsSurvey(baseSurvey, linkAcl, stranger, organization))
  }

  @Test
  fun seesOwnRecordsOnly_ownOnlyVisibility_restrictsCollectorsAndViewers() {
    assertTrue(
      AccessControl.seesOwnRecordsOnly(PeerDataVisibility.OWN_ONLY, SurveyRole.DATA_COLLECTOR)
    )
    assertTrue(AccessControl.seesOwnRecordsOnly(PeerDataVisibility.OWN_ONLY, SurveyRole.VIEWER))
  }

  @Test
  fun seesOwnRecordsOnly_ownOnlyVisibility_doesNotRestrictOrganizersOrOwners() {
    assertFalse(
      AccessControl.seesOwnRecordsOnly(PeerDataVisibility.OWN_ONLY, SurveyRole.SURVEY_ORGANIZER)
    )
    assertFalse(AccessControl.seesOwnRecordsOnly(PeerDataVisibility.OWN_ONLY, SurveyRole.OWNER))
  }

  @Test
  fun seesOwnRecordsOnly_allVisibility_doesNotRestrictCollectors() {
    assertFalse(AccessControl.seesOwnRecordsOnly(PeerDataVisibility.ALL, SurveyRole.DATA_COLLECTOR))
  }
}
