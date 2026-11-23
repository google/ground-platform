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
package org.openforis.collectone.v2.web.ui.surveys

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.data.seed.SampleOrganizationsData
import org.openforis.collectone.v2.web.ui.WebAppState
import org.openforis.collectone.v2.web.ui.organization

class WebSurveysPageTest {
  private val state = WebAppState(dataHolder = sampleDataHolder())
  private val user = state.viewModels.onboarding.uiState.value.profile.email
  private val kfs =
    state.viewModels.dashboard.uiState.value.organization(
      SampleOrganizationsData.KENYA_FOREST_SERVICE
    )!!
  private val mekong =
    state.viewModels.dashboard.uiState.value.organization(
      SampleOrganizationsData.MEKONG_MANGROVE_ALLIANCE
    )!!

  @Test
  fun chips_areAllMineAndOnePerMembership() {
    val chips =
      SurveyListFilter.chipsFor(state.viewModels.dashboard.uiState.value.organizations, user)
    assertEquals(
      listOf(
        SurveyListFilter.All,
        SurveyListFilter.Mine,
        SurveyListFilter.InOrganization(kfs),
        SurveyListFilter.InOrganization(mekong),
      ),
      chips,
    )
    // The listed community the user hasn't joined isn't a chip.
    assertTrue(
      chips.none {
        (it as? SurveyListFilter.InOrganization)?.organization?.name == "Open Foris Community"
      }
    )
  }

  @Test
  fun accessLabel_prefersOwnershipThenOrganizationRole() {
    val surveys = state.viewModels.dashboard.uiState.value.surveys
    val organizations = state.viewModels.dashboard.uiState.value.organizations
    fun label(id: String) =
      WebSurveysList.accessLabel(surveys.first { it.id == id }, organizations, user)
    // Maya owns this personal survey.
    assertEquals(SurveyAccessLabel.OWNER, label("00000034-c088-5ab9-8b98-e6bccb3dca71"))
    // Owned by someone else in an organization she manages.
    assertEquals(
      SurveyAccessLabel.ORGANIZATION_MANAGER,
      label("00000033-6b50-51cd-ac94-baea2ea06d65"),
    )
    // Owned by someone else in an organization she's a member of.
    assertEquals(
      SurveyAccessLabel.ORGANIZATION_MEMBER,
      label("00000035-caf4-571e-b907-f506f92666ab"),
    )
    // Ownership wins over organization role.
    assertEquals(SurveyAccessLabel.OWNER, label("00000036-1a1c-5c51-997f-28b67d813aa3"))
    // Neither owner nor member: shared explicitly.
    val stranger =
      surveys
        .first { it.id == "00000033-6b50-51cd-ac94-baea2ea06d65" }
        .copy(organizationId = "org-unknown")
    assertEquals(
      SurveyAccessLabel.SHARED,
      WebSurveysList.accessLabel(stranger, organizations, user),
    )
  }

  @Test
  fun filter_byOwnershipOrganizationAndQuery() {
    val surveys = state.viewModels.dashboard.uiState.value.surveys
    val organizations = state.viewModels.dashboard.uiState.value.organizations
    val mine = WebSurveysList.filter(surveys, organizations, user, SurveyListFilter.Mine)
    assertTrue(mine.isNotEmpty())
    assertTrue(mine.all { it.ownerEmail == user })

    // Personal surveys (not belonging to any organization) appear under "My surveys" even when
    // the signed-in user is not the original owner (e.g., shared personal surveys or demo users).
    val mineForOtherUser =
      WebSurveysList.filter(
        surveys,
        organizations,
        "other.user@example.org",
        SurveyListFilter.Mine,
      )
    val personalSurveys =
      WebSurveysList.sections(surveys, organizations).first { it.organization == null }.surveys
    assertEquals(personalSurveys.map { it.id }, mineForOtherUser.map { it.id })

    val inKfs =
      WebSurveysList.filter(surveys, organizations, user, SurveyListFilter.InOrganization(kfs))
    assertEquals(3, inKfs.size)
    assertTrue(inKfs.all { it.organizationId == kfs.id })

    // Free text matches the organization name too.
    val byOrgName =
      WebSurveysList.filter(surveys, organizations, user, SurveyListFilter.All, "mekong")
    assertEquals(listOf("00000035-caf4-571e-b907-f506f92666ab"), byOrgName.map { it.id })
    assertTrue(
      WebSurveysList.filter(surveys, organizations, user, SurveyListFilter.All, "zzz").isEmpty()
    )
  }

  @Test
  fun sections_groupByOrganizationWithPersonalLast() {
    val sections =
      WebSurveysList.sections(
        state.viewModels.dashboard.uiState.value.surveys,
        state.viewModels.dashboard.uiState.value.organizations,
      )
    assertEquals(listOf(kfs, mekong, null), sections.map { it.organization })
    assertEquals(
      state.viewModels.dashboard.uiState.value.surveys.size,
      sections.sumOf { it.surveys.size },
    )
    assertNull(sections.last().organization)
    // Organizations without surveys get no section.
    assertTrue(
      sections.none { it.organization?.id == SampleOrganizationsData.OPEN_FORIS_COMMUNITY }
    )
  }

  @Test
  fun createSurvey_addsAnOwnedSurveyInTheChosenOrganizationAndOpensIt() {
    val testState = WebAppState(dataHolder = sampleDataHolder())
    val before = testState.viewModels.dashboard.uiState.value.surveys.size
    val id = testState.createSurvey(title = "Mangrove Nursery Audit", organizationId = kfs.id)
    assertEquals(before + 1, testState.viewModels.dashboard.uiState.value.surveys.size)
    val created = testState.viewModels.dashboard.uiState.value.surveys.last()
    assertEquals(id, created.id)
    assertEquals("Mangrove Nursery Audit", created.title)
    assertEquals(user, created.ownerEmail)
    assertEquals(kfs.id, created.organizationId)
    assertEquals(id, testState.viewModels.dashboard.uiState.value.activeSurveyId)
    assertEquals(kfs.id, testState.dataHolder.appData.value.editorDraft.details.organizationId)
    assertEquals(user, testState.dataHolder.appData.value.editorDraft.sharing.ownerEmail)
    // A second survey with the same title gets its own random ID; a blank title gets a default.
    assertNotEquals(id, testState.createSurvey("Mangrove Nursery Audit"))
    val personal = testState.createSurvey("   ")
    assertEquals(
      "Untitled survey",
      testState.viewModels.dashboard.uiState.value.surveys.first { it.id == personal }.title,
    )
    assertNull(
      testState.viewModels.dashboard.uiState.value.surveys
        .first { it.id == personal }
        .organizationId
    )
  }

  @Test
  fun openSurveyOnWeb_switchesActiveSurveyWithoutDownloadingIt() {
    val testState = WebAppState(dataHolder = sampleDataHolder())
    val target =
      testState.viewModels.dashboard.uiState.value.surveys.first {
        it.id != testState.viewModels.dashboard.uiState.value.activeSurveyId
      }
    val wasDownloaded = target.isDownloaded
    testState.openSurveyOnWeb(target.id)
    assertEquals(target.id, testState.viewModels.dashboard.uiState.value.activeSurveyId)
    assertEquals(
      wasDownloaded,
      testState.viewModels.dashboard.uiState.value.surveys
        .first { it.id == target.id }
        .isDownloaded,
    )
    assertTrue(testState.viewModels.dashboard.uiState.value.hasOpenableActiveSurvey)
    // Unknown IDs are ignored.
    testState.openSurveyOnWeb("survey-missing")
    assertEquals(target.id, testState.viewModels.dashboard.uiState.value.activeSurveyId)
  }
}
