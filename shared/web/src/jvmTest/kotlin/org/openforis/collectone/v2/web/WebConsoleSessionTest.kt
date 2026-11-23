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

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.web.navigation.MemoryBrowserHistory
import org.openforis.collectone.v2.web.ui.ProvideWebSession
import org.openforis.collectone.v2.web.ui.WebAppState
import org.openforis.collectone.v2.web.ui.organization
import org.openforis.collectone.v2.web.ui.sessionViewModel
import org.openforis.collectone.v2.web.ui.surveyEditor
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorViewModel

/**
 * What the web console's session keeps across its pages: the survey map follows organization
 * changes, and the Survey Creation Wizard remembers its working survey between visits.
 */
@OptIn(ExperimentalTestApi::class)
class WebConsoleSessionTest {
  private fun signedInState(): WebAppState =
    WebAppState(dataHolder = sampleDataHolder()).apply {
      viewModels.onboarding.signInWithGoogle(ignoreOffline = true)
      viewModels.onboarding.acceptTermsOfService()
    }

  @Test
  fun removingAnOrganizationsImagerySource_turnsItOffOnTheSurveyMap() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val state = signedInState()
      val organization =
        state.organization.uiState.value.organizations.first {
          !it.isSynthetic && it.isManager(state.viewModels.onboarding.uiState.value.profile.email)
        }
      assertNull(
        state.organization.addImagerySource(
          organization.id,
          "Topo",
          "https://tiles.example.org/{z}/{x}/{y}.png",
          allowOfflineDownload = false,
        )
      )
      val source =
        state.organization.uiState.value.organizations
          .first { it.id == organization.id }
          .imagerySources
          .last()
      state.viewModels.surveyMap.toggleImagerySource(source.id)
      assertTrue(state.viewModels.surveyMap.uiState.value.isImagerySourceEnabled(source.id))
      setContent {
        WebConsoleApp(
          state = state,
          browserHistory = MemoryBrowserHistory("/organizations/${organization.id}"),
        )
      }
      waitForIdle()

      state.organization.removeImagerySource(organization.id, source.id)
      waitForIdle()

      assertFalse(state.viewModels.surveyMap.uiState.value.isImagerySourceEnabled(source.id))
    }

  @Test
  fun aPageAndATest_shareTheSessionsViewModels() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val state = signedInState()
      val fromTest = state.surveyEditor
      var fromPage: SurveyEditorViewModel? = null

      setContent { ProvideWebSession(state) { fromPage = sessionViewModel() } }
      waitForIdle()

      assertSame(fromTest, fromPage)
    }

  @Test
  fun leavingTheWizardWithoutEdits_andOpeningItAgain_keepsOneWorkingSurvey() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val state = signedInState()
      val surveyCount = state.viewModels.dashboard.uiState.value.surveys.size
      val history = MemoryBrowserHistory("/surveys/new", initialState = "/surveys\n/surveys/new")
      setContent { WebConsoleApp(state = state, browserHistory = history) }
      waitForIdle()
      assertEquals(surveyCount + 1, state.viewModels.dashboard.uiState.value.surveys.size)

      history.back()
      waitForIdle()
      history.forward()
      waitForIdle()

      assertEquals(surveyCount + 1, state.viewModels.dashboard.uiState.value.surveys.size)
    }
}
