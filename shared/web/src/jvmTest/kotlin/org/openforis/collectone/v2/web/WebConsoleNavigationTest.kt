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
package org.openforis.collectone.v2.web

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.data.seed.SampleOrganizationsData
import org.openforis.collectone.v2.web.navigation.MemoryBrowserHistory
import org.openforis.collectone.v2.web.ui.WebAppState
import org.openforis.collectone.v2.web.ui.createSurveyWizard
import org.openforis.collectone.v2.web.ui.openOrganizationId
import org.openforis.collectone.v2.web.ui.organization
import org.openforis.collectone.v2.web.ui.surveyEditor
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorSection

/**
 * The web console's URLs: each page has one, links open their page, and the browser's Back and
 * Forward buttons, reloading, and invitation links work.
 */
@OptIn(ExperimentalTestApi::class)
class WebConsoleNavigationTest {
  private val unavailable =
    "That page isn't available. It may have been deleted, or you may not have access to it."

  private fun signedInState(): WebAppState =
    WebAppState(dataHolder = sampleDataHolder()).apply {
      viewModels.onboarding.signInWithGoogle(ignoreOffline = true)
      viewModels.onboarding.acceptTermsOfService()
    }

  private fun ComposeUiTest.showConsole(state: WebAppState, history: MemoryBrowserHistory) {
    setContent { WebConsoleApp(state = state, browserHistory = history) }
    waitForIdle()
  }

  @Test
  fun theRootUrl_opensTheSurveysList() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val history = MemoryBrowserHistory()

      showConsole(signedInState(), history)

      assertEquals("/surveys", history.path)
      assertEquals(listOf("/surveys"), history.paths)
    }

  @Test
  fun anOrganizationLink_opensTheOrganization() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val state = signedInState()
      val organizationId = SampleOrganizationsData.KENYA_FOREST_SERVICE
      val history = MemoryBrowserHistory("/organizations/$organizationId")

      showConsole(state, history)

      assertEquals("/organizations/$organizationId", history.path)
      assertEquals(organizationId, state.openOrganizationId)
    }

  @Test
  fun aSurveyDashboardLink_opensThatSurvey() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val state = signedInState()
      val survey =
        state.viewModels.dashboard.uiState.value.surveys.first {
          it.id != state.viewModels.dashboard.uiState.value.activeSurveyId
        }
      val history = MemoryBrowserHistory("/surveys/${survey.id}/dashboard")

      showConsole(state, history)

      assertEquals(survey.id, state.viewModels.dashboard.uiState.value.activeSurveyId)
      assertEquals("/surveys/${survey.id}/dashboard", history.path)
    }

  @Test
  fun openingAPage_addsAHistoryEntry_andBackAndForwardMoveThroughThem() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val history = MemoryBrowserHistory("/surveys")
      showConsole(signedInState(), history)

      onNodeWithText("Live metrics").performClick()
      waitForIdle()
      assertEquals(listOf("/surveys", "/impact"), history.paths)
      onNodeWithText("Live Metrics").assertIsDisplayed()
      onNodeWithText("Impact").assertDoesNotExist()

      history.back()
      waitForIdle()
      assertEquals("/surveys", history.path)
      onNodeWithText("Create survey").assertIsDisplayed()

      history.forward()
      waitForIdle()
      assertEquals("/impact", history.path)
      onNodeWithText("Live Metrics").assertIsDisplayed()
      onNodeWithContentDescription("Back to surveys").assertIsDisplayed()
    }

  @Test
  fun anUpArrow_returnsToAnEarlierPageOfTheBackStack() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val history = MemoryBrowserHistory("/surveys")
      showConsole(signedInState(), history)
      onNodeWithText("Live metrics").performClick()
      waitForIdle()

      onNodeWithContentDescription("Back to surveys").performClick()
      waitForIdle()

      assertEquals("/surveys", history.path)
      assertEquals("/surveys", history.state)
    }

  @Test
  fun reloading_restoresTheBackStackStoredWithTheHistoryEntry() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val history = MemoryBrowserHistory("/impact", initialState = "/surveys\n/impact")

      showConsole(signedInState(), history)

      assertEquals("/impact", history.path)
      assertEquals("/surveys\n/impact", history.state)
      assertEquals(listOf("/impact"), history.paths)
    }

  @Test
  fun anUnknownLink_opensTheSurveysListWithANotice() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val history = MemoryBrowserHistory("/no-such-page")

      showConsole(signedInState(), history)

      assertEquals("/surveys", history.path)
      onNodeWithText(unavailable).assertIsDisplayed()
    }

  @Test
  fun aLinkToAMissingSurvey_opensTheSurveysListWithANotice() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val history = MemoryBrowserHistory("/surveys/no-such-survey/dashboard")

      showConsole(signedInState(), history)

      assertEquals("/surveys", history.path)
      onNodeWithText(unavailable).assertIsDisplayed()
    }

  @Test
  fun theAdminPanelLink_ofANonAdministrator_opensTheSurveysListWithANotice() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val state = signedInState()
      val history = MemoryBrowserHistory("/admin")
      if (state.organization.uiState.value.isPlatformAdmin) return@runDesktopComposeUiTest

      showConsole(state, history)

      assertEquals("/surveys", history.path)
      onNodeWithText(unavailable).assertIsDisplayed()
    }

  @Test
  fun aHashOfAnEarlierConsoleVersion_opensItsPage() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val history = MemoryBrowserHistory("/", initialHash = "#organizations")

      showConsole(signedInState(), history)

      assertEquals("/organizations", history.path)
    }

  @Test
  fun signedOut_aLinkShowsTheSignInPage_andKeepsItsUrl() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val state = WebAppState(dataHolder = sampleDataHolder())
      val history = MemoryBrowserHistory("/organizations")

      showConsole(state, history)

      onNodeWithText("Sign in with Google").assertIsDisplayed()
      assertEquals("/organizations", history.path)
      state.viewModels.onboarding.signInWithGoogle(ignoreOffline = true)
      state.viewModels.onboarding.acceptTermsOfService()
      waitForIdle()
      assertEquals("/organizations", history.path)
      onNodeWithText("Sign in with Google").assertDoesNotExist()
    }

  @Test
  fun aSurveyInvitation_keepsItsUrlUntilSignedIn_thenOpensTheSurvey() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val state = WebAppState(dataHolder = sampleDataHolder())
      val invited =
        state.viewModels.dashboard.uiState.value.surveys.last {
          it.id != state.viewModels.dashboard.uiState.value.activeSurveyId
        }
      val history = MemoryBrowserHistory("/", initialHash = "#join/${invited.id}/token")

      showConsole(state, history)
      assertEquals("#join/${invited.id}/token", history.hash)
      assertNull(history.state)

      state.viewModels.onboarding.signInWithGoogle(ignoreOffline = true)
      state.viewModels.onboarding.acceptTermsOfService()
      waitForIdle()

      assertEquals(invited.id, state.viewModels.dashboard.uiState.value.activeSurveyId)
      assertEquals("/surveys/${invited.id}/dashboard", history.path)
      assertTrue(history.hash.isEmpty())
    }

  @Test
  fun advancingThroughCreateSurveyWizardSteps_updatesUrlInPlace_withoutPromptingForUnsavedDraft() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val state = signedInState()
      val history = MemoryBrowserHistory("/surveys")
      showConsole(state, history)

      onNodeWithText("Create survey").performClick()
      waitForIdle()
      assertEquals(listOf("/surveys", "/surveys/new"), history.paths)
      assertFalse(state.createSurveyWizard.uiState.value.isPromptingUnsavedDraft)

      onNode(hasSetTextAction() and hasText("Survey title *")).performTextInput("Forest Inventory")
      waitForIdle()

      onNodeWithText("Next").performClick()
      waitForIdle()

      assertEquals(listOf("/surveys", "/surveys/new/mode-and-purpose"), history.paths)
      assertFalse(state.createSurveyWizard.uiState.value.isPromptingUnsavedDraft)
      onAllNodesWithText("Continue editing unsaved draft?").assertCountEquals(0)

      state.createSurveyWizard.togglePurposePack("lulc_training_data")
      waitForIdle()

      onNodeWithText("Next").performClick()
      waitForIdle()

      assertEquals(listOf("/surveys", "/surveys/new/survey-area"), history.paths)
      assertFalse(state.createSurveyWizard.uiState.value.isPromptingUnsavedDraft)
      onAllNodesWithText("Continue editing unsaved draft?").assertCountEquals(0)

      // Stepping back in the wizard updates the URL in place without prompting.
      onNodeWithText("Back").performClick()
      waitForIdle()
      assertEquals(listOf("/surveys", "/surveys/new/mode-and-purpose"), history.paths)
      assertFalse(state.createSurveyWizard.uiState.value.isPromptingUnsavedDraft)

      // Leaving the wizard to Surveys and reopening it prompts once; continuing and clicking Next
      // does not prompt again.
      history.back()
      waitForIdle()
      assertEquals("/surveys", history.path)

      onNodeWithText("Create survey").performClick()
      waitForIdle()
      onNodeWithText("Continue editing unsaved draft?").assertIsDisplayed()
      onNodeWithText("Continue editing").performClick()
      waitForIdle()

      onNodeWithText("Next").performClick()
      waitForIdle()
      assertEquals(listOf("/surveys", "/surveys/new/survey-area"), history.paths)
      assertFalse(state.createSurveyWizard.uiState.value.isPromptingUnsavedDraft)
      onAllNodesWithText("Continue editing unsaved draft?").assertCountEquals(0)
    }

  @Test
  fun switchingSurveyEditorSectionsWithUnpublishedEdits_updatesUrlInPlace_withoutPromptingDraft() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val state = signedInState()
      val surveyId = state.viewModels.dashboard.uiState.value.activeSurveyId
      val history = MemoryBrowserHistory("/surveys/$surveyId/editor")
      showConsole(state, history)

      state.surveyEditor.updateDetails { it.copy(title = "Edited survey title") }
      waitForIdle()
      assertFalse(state.surveyEditor.uiState.value.isPromptingWorkingCopy)

      state.surveyEditor.select(SurveyEditorSection.Sharing)
      waitForIdle()

      assertEquals(listOf("/surveys/$surveyId/editor/sharing"), history.paths)
      assertFalse(state.surveyEditor.uiState.value.isPromptingWorkingCopy)
      onAllNodesWithText("Continue editing unsaved draft?").assertCountEquals(0)
    }
}
