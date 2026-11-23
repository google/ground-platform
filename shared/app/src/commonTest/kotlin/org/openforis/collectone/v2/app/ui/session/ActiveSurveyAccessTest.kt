/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.app.ui.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.di.AppData
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.OrganizationMember
import org.openforis.collectone.v2.app.domain.model.OrganizationRole
import org.openforis.collectone.v2.app.domain.model.SurveyLifecycleState
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.editor.SharingSettings
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft

class ActiveSurveyAccessTest {
  @Test
  fun activeSurvey_withNoSurveys_isABlankPlaceholder() {
    // A new store holds no surveys until the first sync or download.
    val access = ActiveSurveyAccess(AppData(), email = "", isSignedIn = false)

    val active = access.activeSurvey

    assertEquals("", active.id)
    assertEquals("", active.title)
  }

  @Test
  fun activeSurvey_whenTheActiveIdIsUnknown_isTheFirstSurvey() {
    val data = AppData(surveys = listOf(survey("a"), survey("b")), activeSurveyId = "gone")

    assertEquals("a", ActiveSurveyAccess(data, OWNER, isSignedIn = true).activeSurvey.id)
  }

  @Test
  fun organization_isTheActiveSurveysOrganization() {
    val data = data(survey("s", organizationId = FOREST_ORG.id), organizations = listOf(FOREST_ORG))

    assertEquals(FOREST_ORG, ActiveSurveyAccess(data, OWNER, isSignedIn = true).organization)
    assertNull(ActiveSurveyAccess(data(survey("s")), OWNER, isSignedIn = true).organization)
  }

  @Test
  fun platformAdmin_isAManagerOfTheSyntheticOrganization_onlyWhileSignedIn() {
    val allUsers =
      Organization(
        id = Organization.ALL_USERS_ID,
        name = "All users",
        isSynthetic = true,
        members = listOf(OrganizationMember(ADMIN, OrganizationRole.MANAGER)),
      )
    val data = data(survey("s"), organizations = listOf(allUsers, FOREST_ORG))

    assertTrue(ActiveSurveyAccess(data, ADMIN, isSignedIn = true).isPlatformAdmin)
    assertFalse(ActiveSurveyAccess(data, ADMIN, isSignedIn = false).isPlatformAdmin)
    // Managing a regular organization doesn't make anyone a platform administrator.
    assertFalse(ActiveSurveyAccess(data, FOREST_MANAGER, isSignedIn = true).isPlatformAdmin)
  }

  @Test
  fun affiliation_joinsTheUsersOrganizations() {
    val river =
      Organization(id = "river", name = "River Trust", members = listOf(OrganizationMember(OWNER)))
    val forest = FOREST_ORG.copy(members = FOREST_ORG.members + OrganizationMember(OWNER))
    val data = data(survey("s"), organizations = listOf(forest, river))

    assertEquals(
      "Forest Society • River Trust",
      ActiveSurveyAccess(data, OWNER, isSignedIn = true).affiliation,
    )
    assertEquals("", ActiveSurveyAccess(data, "stranger@example.org", true).affiliation)
  }

  @Test
  fun canCollectData_neverOnceTheSurveyIsClosed() {
    val open = data(survey("s"))
    val closed = data(survey("s", state = SurveyLifecycleState.CLOSED))

    assertTrue(ActiveSurveyAccess(open, OWNER, isSignedIn = true).canCollectData)
    assertFalse(ActiveSurveyAccess(closed, OWNER, isSignedIn = true).canCollectData)
  }

  @Test
  fun canManageSurvey_requiresSigningIn() {
    val data = data(survey("s"))

    assertTrue(ActiveSurveyAccess(data, OWNER, isSignedIn = true).canManageSurvey(false))
    assertFalse(ActiveSurveyAccess(data, OWNER, isSignedIn = false).canManageSurvey(false))
    assertFalse(
      ActiveSurveyAccess(data, "stranger@example.org", isSignedIn = true).canManageSurvey(false)
    )
  }

  @Test
  fun submissionViewer_isAnOrganizerForWhoeverMayManageTheSurvey() {
    val data = data(survey("s", organizationId = FOREST_ORG.id), organizations = listOf(FOREST_ORG))

    assertTrue(ActiveSurveyAccess(data, FOREST_MANAGER, true).submissionViewer.isOrganizer)
    assertFalse(ActiveSurveyAccess(data, "stranger@example.org", true).submissionViewer.isOrganizer)
  }

  private fun survey(
    id: String,
    organizationId: String? = null,
    state: SurveyLifecycleState = SurveyLifecycleState.PUBLISHED,
  ) =
    SurveyPreviewItem(
      id = id,
      title = id,
      description = "",
      location = "",
      isDownloaded = true,
      entityCount = 0,
      ownerEmail = OWNER,
      organizationId = organizationId,
      state = state,
    )

  /** [survey] as the active survey, owned by [OWNER] in the Survey editor's sharing settings. */
  private fun data(survey: SurveyPreviewItem, organizations: List<Organization> = emptyList()) =
    AppData(
      surveys = listOf(survey),
      organizations = organizations,
      activeSurveyId = survey.id,
      editorDraft =
        SurveyEditorDraft.blank(surveyId = survey.id)
          .copy(sharing = SharingSettings(ownerEmail = OWNER)),
    )

  private companion object {
    const val OWNER = "owner@example.org"
    const val ADMIN = "admin@example.org"
    const val FOREST_MANAGER = "manager@forest.example.org"
    val FOREST_ORG =
      Organization(
        id = "forest",
        name = "Forest Society",
        members = listOf(OrganizationMember(FOREST_MANAGER, OrganizationRole.MANAGER)),
      )
  }
}
