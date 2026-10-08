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
package org.groundplatform.v2.devtools.prototypeapp.ui.dashboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.devtools.prototypeapp.PrototypeAppState
import org.groundplatform.v2.devtools.prototypeapp.PrototypeWorkbenchPage
import org.groundplatform.v2.devtools.prototypeapp.data.seed.PrototypeFakeOrganizationsData

class WebSurveysPageTest {
  private val state = PrototypeAppState()
  private val user = state.signedInUserEmail
  private val kfs = state.organization(PrototypeFakeOrganizationsData.KENYA_FOREST_SERVICE)!!
  private val mekong = state.organization(PrototypeFakeOrganizationsData.MEKONG_MANGROVE_ALLIANCE)!!

  @Test
  fun surveysPage_isTheWebLandingPage() {
    assertEquals(PrototypeWorkbenchPage.WEB_SURVEYS, PrototypeWorkbenchPage.fromHash("#surveys"))
    assertTrue(PrototypeWorkbenchPage.WEB_SURVEYS.isWebApp)
    assertFalse(PrototypeWorkbenchPage.WEB_SURVEYS.needsActiveSurvey)
    assertTrue(PrototypeWorkbenchPage.WEB_DASHBOARD.needsActiveSurvey)
    assertTrue(PrototypeWorkbenchPage.SURVEY_EDITOR.needsActiveSurvey)
    assertEquals(
      listOf(PrototypeWorkbenchPage.MOBILE_PROTOTYPE, PrototypeWorkbenchPage.WEB_SURVEYS),
      PrototypeWorkbenchPage.topBarPages,
    )
  }

  @Test
  fun chips_areAllMineAndOnePerMembership() {
    val chips = SurveyListFilter.chipsFor(state.organizations, user)
    assertEquals(
      listOf("All", "My surveys", kfs.name, mekong.name),
      chips.map { it.label },
    )
    // The listed community the user hasn't joined isn't a chip.
    assertTrue(chips.none { it.label == "Open Foris Community" })
  }

  @Test
  fun accessLabel_prefersOwnershipThenOrganizationRole() {
    val surveys = state.surveys
    val organizations = state.organizations
    fun label(id: String) =
      WebSurveysList.accessLabel(surveys.first { it.id == id }, organizations, user)
    // Maya owns this personal survey.
    assertEquals(SurveyAccessLabel.OWNER, label("survey-sample-plots-forest"))
    // Owned by someone else in an organization she manages.
    assertEquals(SurveyAccessLabel.ORGANIZATION_MANAGER, label("survey-kenya-coffee"))
    // Owned by someone else in an organization she's a member of.
    assertEquals(SurveyAccessLabel.ORGANIZATION_MEMBER, label("survey-serengeti-corridor"))
    // Ownership wins over organization role.
    assertEquals(SurveyAccessLabel.OWNER, label("survey-single-point-land-use"))
    // Neither owner nor member: shared explicitly.
    val stranger =
      surveys.first { it.id == "survey-kenya-coffee" }.copy(organizationId = "org-unknown")
    assertEquals(
      SurveyAccessLabel.SHARED,
      WebSurveysList.accessLabel(stranger, organizations, user),
    )
  }

  @Test
  fun filter_byOwnershipOrganizationAndQuery() {
    val surveys = state.surveys
    val organizations = state.organizations
    val mine = WebSurveysList.filter(surveys, organizations, user, SurveyListFilter.Mine)
    assertTrue(mine.isNotEmpty())
    assertTrue(mine.all { it.ownerEmail == user })

    val inKfs =
      WebSurveysList.filter(surveys, organizations, user, SurveyListFilter.InOrganization(kfs))
    assertEquals(3, inKfs.size)
    assertTrue(inKfs.all { it.organizationId == kfs.id })

    // Free text matches the organization name too.
    val byOrgName =
      WebSurveysList.filter(surveys, organizations, user, SurveyListFilter.All, "mekong")
    assertEquals(listOf("survey-serengeti-corridor"), byOrgName.map { it.id })
    assertTrue(
      WebSurveysList.filter(surveys, organizations, user, SurveyListFilter.All, "zzz").isEmpty()
    )
  }

  @Test
  fun sections_groupByOrganizationWithPersonalLast() {
    val sections = WebSurveysList.sections(state.surveys, state.organizations)
    assertEquals(listOf(kfs.name, mekong.name, "Personal surveys"), sections.map { it.title })
    assertEquals(state.surveys.size, sections.sumOf { it.surveys.size })
    assertNull(sections.last().organization)
    // Organizations without surveys get no section.
    assertTrue(
      sections.none { it.organization?.id == PrototypeFakeOrganizationsData.OPEN_FORIS_COMMUNITY }
    )
  }

  @Test
  fun openSurveyOnWeb_switchesActiveSurveyWithoutTouchingMobileFlow() {
    val testState = PrototypeAppState()
    val screenBefore = testState.currentScreen
    val target = testState.surveys.first { it.id != testState.activeSurveyId }
    val wasDownloaded = target.isDownloaded
    testState.openSurveyOnWeb(target.id)
    assertEquals(target.id, testState.activeSurveyId)
    assertEquals(screenBefore, testState.currentScreen)
    assertEquals(wasDownloaded, testState.surveys.first { it.id == target.id }.isDownloaded)
    assertTrue(testState.hasOpenableActiveSurvey)
    // Unknown IDs are ignored.
    testState.openSurveyOnWeb("survey-missing")
    assertEquals(target.id, testState.activeSurveyId)
  }

  @Test
  fun createSurvey_addsAnOwnedSurveyInTheChosenOrganizationAndOpensIt() {
    val testState = PrototypeAppState()
    val before = testState.surveys.size
    val id = testState.createSurvey(title = "Mangrove Nursery Audit", organizationId = kfs.id)
    assertEquals("survey-mangrove-nursery-audit", id)
    assertEquals(before + 1, testState.surveys.size)
    val created = testState.surveys.last()
    assertEquals(id, created.id)
    assertEquals("Mangrove Nursery Audit", created.title)
    assertEquals(user, created.ownerEmail)
    assertEquals(kfs.id, created.organizationId)
    assertEquals(id, testState.activeSurveyId)
    assertEquals(kfs.id, testState.activeSurveyEditorDraft.details.organizationId)
    assertEquals(user, testState.activeSurveyEditorDraft.sharing.ownerEmail)
    // A second survey with the same title gets a unique ID; a blank title gets a default.
    assertEquals(
      "survey-mangrove-nursery-audit-2",
      testState.createSurvey("Mangrove Nursery Audit"),
    )
    val personal = testState.createSurvey("   ")
    assertEquals("Untitled survey", testState.surveys.first { it.id == personal }.title)
    assertNull(testState.surveys.first { it.id == personal }.organizationId)
  }
}
