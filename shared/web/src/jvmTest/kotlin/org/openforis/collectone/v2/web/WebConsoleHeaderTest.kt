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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.web.navigation.MemoryBrowserHistory
import org.openforis.collectone.v2.web.ui.WebAppState
import org.openforis.collectone.v2.web.ui.canManageActiveSurvey
import org.openforis.collectone.v2.web.ui.common.WEB_COMPACT_LANGUAGE_SELECTOR_TAG

/**
 * The header every web console page shares (logo, language selector) and the survey toolbar on the
 * dashboard (survey switcher, "Manage survey"), driven through the console's root.
 */
@OptIn(ExperimentalTestApi::class)
class WebConsoleHeaderTest {

  /** Shows the console at [path], returning its browser history. */
  private fun ComposeUiTest.showConsole(state: WebAppState, path: String): MemoryBrowserHistory {
    val history = MemoryBrowserHistory(initialPath = path)
    setContent { WebConsoleApp(state = state, browserHistory = history) }
    waitForIdle()
    return history
  }

  /** A web app state with the sample user signed in. */
  private fun signedInState(): WebAppState =
    WebAppState(dataHolder = sampleDataHolder()).apply {
      viewModels.onboarding.signInWithGoogle(ignoreOffline = true)
      viewModels.onboarding.acceptTermsOfService()
    }

  /** A signed-in web app state with the first survey the user can manage open on the dashboard. */
  private fun dashboardState(): WebAppState =
    signedInState().apply {
      // Opens surveys in turn until one the user can manage is active.
      viewModels.dashboard.uiState.value.surveys.firstOrNull { survey ->
        openSurveyOnWeb(survey.id)
        canManageActiveSurvey
      }
    }

  @Test
  fun logoClick_opensTheSurveysPage() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val history = showConsole(signedInState(), "/organizations")
      assertEquals("/organizations", history.path)

      onNodeWithText("Collect One").performClick()
      waitForIdle()

      assertEquals("/surveys", history.path)
    }

  @Test
  fun languageSelector_changesTheAppLanguage() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val state = signedInState()
      showConsole(state, "/surveys")

      onNodeWithTag(WEB_COMPACT_LANGUAGE_SELECTOR_TAG).performClick()
      onNodeWithText("Español").performClick()
      waitForIdle()

      assertEquals("es", state.viewModels.settings.uiState.value.languageCode)
    }

  @Test
  fun surveySwitcher_opensAnotherRecentSurvey() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val state = dashboardState()
      val otherSurvey =
        state.viewModels.dashboard.uiState.value.surveys.first {
          it.id != state.viewModels.dashboard.uiState.value.activeSurveyId
        }
      val history =
        showConsole(
          state,
          "/surveys/${state.viewModels.dashboard.uiState.value.activeSurveyId}/dashboard",
        )

      onNodeWithContentDescription("Switch survey").performClick()
      onNodeWithText(otherSurvey.title).performClick()
      waitForIdle()

      assertEquals(otherSurvey.id, state.viewModels.dashboard.uiState.value.activeSurveyId)
      assertEquals("/surveys/${otherSurvey.id}/dashboard", history.path)
    }

  @Test
  fun surveySwitcher_allSurveysItem_opensTheSurveysPage() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val state = dashboardState()
      val history =
        showConsole(
          state,
          "/surveys/${state.viewModels.dashboard.uiState.value.activeSurveyId}/dashboard",
        )

      onNodeWithContentDescription("Switch survey").performClick()
      onNodeWithText("All surveys…").performClick()
      waitForIdle()

      assertEquals("/surveys", history.path)
    }

  @Test
  fun manageSurvey_opensTheSurveyEditorForSurveyOrganizers() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val state = dashboardState()
      assertTrue(state.canManageActiveSurvey, "the sample user organizes a survey")
      val history =
        showConsole(
          state,
          "/surveys/${state.viewModels.dashboard.uiState.value.activeSurveyId}/dashboard",
        )

      onNodeWithText("Manage survey").performClick()
      waitForIdle()

      assertEquals(
        "/surveys/${state.viewModels.dashboard.uiState.value.activeSurveyId}/editor",
        history.path,
      )
    }
}
