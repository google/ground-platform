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
package org.groundplatform.v2.devtools.prototypeapp.surveyeditor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.devtools.prototypeapp.domain.model.InvitationStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MembershipStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationMember
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationRole
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.Collaborator
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.CollaboratorRole
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SharingPolicy
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SharingSettings

class SurveyAccessTest {
  private val owner = "owner@example.org"
  private val manager = "manager@example.org"
  private val member = "member@example.org"
  private val requester = "requester@example.org"
  private val collector = "collector@example.org"
  private val invitee = "invitee@example.org"
  private val stranger = "stranger@example.org"

  private val organization =
    Organization(
      id = "org",
      name = "Org",
      members =
        listOf(
          OrganizationMember(manager, OrganizationRole.MANAGER),
          OrganizationMember(member, OrganizationRole.MEMBER),
          OrganizationMember(requester, OrganizationRole.MEMBER, MembershipStatus.REQUESTED),
        ),
    )

  private val sharing =
    SharingSettings(
      ownerEmail = owner,
      collaborators =
        listOf(
          Collaborator(collector, CollaboratorRole.DATA_COLLECTOR, InvitationStatus.ACCEPTED),
          Collaborator(invitee, CollaboratorRole.SURVEY_ORGANIZER, InvitationStatus.PENDING),
        ),
    )

  @Test
  fun owner_isAlwaysOrganizer_evenInAnotherCase() {
    assertEquals(
      CollaboratorRole.SURVEY_ORGANIZER,
      SurveyAccess.roleOf("Owner@Example.org", sharing, organization = null),
    )
  }

  @Test
  fun acceptedCollaborators_getTheirRole_pendingOnesDoNot() {
    assertEquals(
      CollaboratorRole.DATA_COLLECTOR,
      SurveyAccess.roleOf(collector, sharing, organization = null),
    )
    assertNull(SurveyAccess.roleOf(invitee, sharing, organization = null))
  }

  @Test
  fun organizationManagers_inheritOrganizer_membersDoNot_whenRestricted() {
    assertEquals(
      CollaboratorRole.SURVEY_ORGANIZER,
      SurveyAccess.roleOf(manager, sharing, organization),
    )
    assertNull(SurveyAccess.roleOf(member, sharing, organization))
    assertTrue(SurveyAccess.canManage(manager, sharing, organization))
    assertFalse(SurveyAccess.canManage(member, sharing, organization))
  }

  @Test
  fun organizationPolicy_letsActiveMembersCollect_butNotPendingRequestsOrStrangers() {
    val shared = sharing.copy(policy = SharingPolicy.ORGANIZATION)
    assertEquals(CollaboratorRole.DATA_COLLECTOR, SurveyAccess.roleOf(member, shared, organization))
    assertNull(SurveyAccess.roleOf(requester, shared, organization))
    assertNull(SurveyAccess.roleOf(stranger, shared, organization))
    // Without an organization the policy grants nothing.
    assertNull(SurveyAccess.roleOf(member, shared, organization = null))
  }

  @Test
  fun linkAndPublicPolicies_letAnyoneCollect() {
    for (policy in listOf(SharingPolicy.ANYONE_WITH_LINK, SharingPolicy.PUBLIC)) {
      assertEquals(
        CollaboratorRole.DATA_COLLECTOR,
        SurveyAccess.roleOf(stranger, sharing.copy(policy = policy), organization = null),
        policy.name,
      )
    }
  }

  @Test
  fun strongestRoleWins() {
    // A manager who is also an accepted data collector is still an organizer.
    val withManagerOnAcl =
      sharing.copy(
        collaborators =
          sharing.collaborators +
            Collaborator(manager, CollaboratorRole.DATA_COLLECTOR, InvitationStatus.ACCEPTED)
      )
    assertEquals(
      CollaboratorRole.SURVEY_ORGANIZER,
      SurveyAccess.roleOf(manager, withManagerOnAcl, organization),
    )
  }

  @Test
  fun blankEmail_neverMatchesOwnerOrOrganization() {
    val ownerless = sharing.copy(ownerEmail = "")
    assertNull(SurveyAccess.roleOf("", ownerless, organization))
  }

  @Test
  fun issues_flagOrganizationPolicyWithoutOrganization() {
    val shared = sharing.copy(policy = SharingPolicy.ORGANIZATION)
    assertEquals(1, SurveyAccess.issues(shared, organizationId = null).size)
    assertEquals(emptyList(), SurveyAccess.issues(shared, organizationId = "org"))
    assertEquals(emptyList(), SurveyAccess.issues(sharing, organizationId = null))
  }
}
