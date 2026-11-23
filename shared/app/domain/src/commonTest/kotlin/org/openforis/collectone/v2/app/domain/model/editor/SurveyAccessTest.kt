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
package org.openforis.collectone.v2.app.domain.model.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.domain.model.InvitationStatus
import org.openforis.collectone.v2.app.domain.model.MembershipStatus
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.OrganizationMember
import org.openforis.collectone.v2.app.domain.model.OrganizationRole
import org.openforis.collectone.v2.app.domain.model.messageKey

class SurveyAccessTest {
  private val owner = "owner@example.org"
  private val uuid = "123e4567-e89b-12d3-a456-426614174000"

  private val org =
    Organization(
      id = "org-1",
      name = "Forest Watch",
      members =
        listOf(
          OrganizationMember(email = "manager@example.org", role = OrganizationRole.MANAGER),
          OrganizationMember(email = "member@example.org"),
          OrganizationMember(email = "invited@example.org", status = MembershipStatus.INVITED),
        ),
    )

  private fun sharing(
    policy: SharingPolicy = SharingPolicy.RESTRICTED,
    defaultRole: CollaboratorRole = CollaboratorRole.DATA_COLLECTOR,
    vararg collaborators: Collaborator,
  ) =
    SharingSettings(
      ownerEmail = owner,
      policy = policy,
      collaborators = collaborators.toList(),
      defaultRole = defaultRole,
    )

  @Test
  fun surveyUrl_usesDraftForUnsavedSurveys() {
    assertEquals("https://collect-one.example.org/surveys/s-1", SurveyAccess.surveyUrl("s-1"))
    assertEquals("https://collect-one.example.org/surveys/draft", SurveyAccess.surveyUrl(""))
  }

  @Test
  fun surveyIdFromLink_acceptsLinksFromAnyHostAndBareIds() {
    assertEquals(uuid, SurveyAccess.surveyIdFromLink(SurveyAccess.surveyUrl(uuid)))
    assertEquals(uuid, SurveyAccess.surveyIdFromLink(" https://other.host/surveys/$uuid/?x=1#top "))
    assertEquals(uuid, SurveyAccess.surveyIdFromLink(uuid))
  }

  @Test
  fun surveyIdFromLink_rejectsOtherText() {
    assertNull(SurveyAccess.surveyIdFromLink("https://other.host/surveys/not-a-uuid"))
    assertNull(SurveyAccess.surveyIdFromLink("https://other.host/forms/$uuid"))
    assertNull(SurveyAccess.surveyIdFromLink("hello"))
  }

  @Test
  fun ownerIsAlwaysAnOrganizer() {
    assertEquals(CollaboratorRole.SURVEY_ORGANIZER, SurveyAccess.roleOf(owner, sharing(), null))
    assertTrue(SurveyAccess.canManage(owner, sharing(), null))
  }

  @Test
  fun restrictedSurvey_strangersHaveNoAccess() {
    val stranger = "stranger@example.org"

    assertNull(SurveyAccess.roleOf(stranger, sharing(), null))
    assertFalse(SurveyAccess.canView(stranger, sharing(), null))
    assertFalse(SurveyAccess.canCollectData(stranger, sharing(), null))
    assertFalse(SurveyAccess.canManage(stranger, sharing(), null))
  }

  @Test
  fun acceptedCollaboratorsGetTheirRole() {
    val settings =
      sharing(
        collaborators =
          arrayOf(
            Collaborator("viewer@example.org", CollaboratorRole.VIEWER, InvitationStatus.ACCEPTED),
            Collaborator(
              "collector@example.org",
              CollaboratorRole.DATA_COLLECTOR,
              InvitationStatus.ACCEPTED,
            ),
          )
      )

    assertEquals(CollaboratorRole.VIEWER, SurveyAccess.roleOf("viewer@example.org", settings, null))
    assertTrue(SurveyAccess.canView("viewer@example.org", settings, null))
    assertFalse(SurveyAccess.canCollectData("viewer@example.org", settings, null))
    assertTrue(SurveyAccess.canCollectData("collector@example.org", settings, null))
    assertFalse(SurveyAccess.canManage("collector@example.org", settings, null))
  }

  @Test
  fun organizationManagersInheritOrganizerAccess() {
    assertEquals(
      CollaboratorRole.SURVEY_ORGANIZER,
      SurveyAccess.roleOf("manager@example.org", sharing(), org),
    )
    // Plain members need the organization sharing policy.
    assertNull(SurveyAccess.roleOf("member@example.org", sharing(), org))
  }

  @Test
  fun organizationPolicy_activeMembersGetTheDefaultRole() {
    val settings = sharing(SharingPolicy.ORGANIZATION, CollaboratorRole.VIEWER)

    assertEquals(CollaboratorRole.VIEWER, SurveyAccess.roleOf("member@example.org", settings, org))
    assertNull(SurveyAccess.roleOf("invited@example.org", settings, org))
    assertNull(SurveyAccess.roleOf("stranger@example.org", settings, org))
  }

  @Test
  fun linkSharing_anyoneGetsTheDefaultRole() {
    val settings = sharing(SharingPolicy.ANYONE_WITH_LINK, CollaboratorRole.DATA_COLLECTOR)

    assertTrue(SurveyAccess.canCollectData("stranger@example.org", settings, null))
    assertFalse(SurveyAccess.canManage("stranger@example.org", settings, null))
  }

  @Test
  fun issues_organizationPolicyNeedsAnOrganization() {
    val settings = sharing(SharingPolicy.ORGANIZATION)

    assertEquals(
      listOf<Any>(EditorIssueMessage.SHARING_ORGANIZATION_MISSING),
      SurveyAccess.issues(settings, organizationId = null).map { it.messageKey },
    )
    assertEquals(emptyList(), SurveyAccess.issues(settings, organizationId = "org-1"))
    assertEquals(emptyList(), SurveyAccess.issues(sharing(), organizationId = null))
  }
}
