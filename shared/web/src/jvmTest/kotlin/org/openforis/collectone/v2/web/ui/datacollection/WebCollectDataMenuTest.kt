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
package org.openforis.collectone.v2.web.ui.datacollection

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.openforis.collectone.v2.app.data.datasource.local.store.runDirect
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.domain.model.FormAvailability
import org.openforis.collectone.v2.app.domain.model.FormPreviewItem
import org.openforis.collectone.v2.app.domain.model.editor.FormPreviewTarget
import org.openforis.collectone.v2.app.ui.datacollection.DataCollectionActions
import org.openforis.collectone.v2.app.ui.datacollection.DataCollectionUiState
import org.openforis.collectone.v2.app.ui.navigation.MainSurveyViewMode
import org.openforis.collectone.v2.core.forms.ui.AppTheme
import org.openforis.collectone.v2.web.WebConsoleApp
import org.openforis.collectone.v2.web.navigation.MemoryBrowserHistory
import org.openforis.collectone.v2.web.ui.WebAppState
import org.openforis.collectone.v2.web.ui.canManageActiveSurvey
import org.openforis.collectone.v2.web.ui.surveyEditor

/**
 * Verifies the dashboard's **Collect data** toolbar menu when forms are available on web and when
 * all forms in a survey are configured as mobile-only.
 */
@OptIn(ExperimentalTestApi::class)
class WebCollectDataMenuTest {

  private fun signedInDashboardState(): WebAppState =
    WebAppState(dataHolder = sampleDataHolder()).apply {
      viewModels.onboarding.signInWithGoogle(ignoreOffline = true)
      viewModels.onboarding.acceptTermsOfService()
      viewModels.dashboard.uiState.value.surveys.firstOrNull { survey ->
        openSurveyOnWeb(survey.id)
        canManageActiveSurvey
      }
    }

  private fun ComposeUiTest.showDashboard(state: WebAppState): MemoryBrowserHistory {
    val surveyId = state.viewModels.dashboard.uiState.value.activeSurveyId
    val history = MemoryBrowserHistory(initialPath = "/surveys/$surveyId/dashboard")
    setContent { WebConsoleApp(state = state, browserHistory = history) }
    waitForIdle()
    return history
  }

  @Test
  fun collectDataMenu_whenFormsAreMobileOnly_offersManageSurveyShortcutToWebFormPreview() {
    val state = signedInDashboardState()
    val surveyId = state.viewModels.dashboard.uiState.value.activeSurveyId
    val firstFormKey = runDirect {
      val draft = state.dataHolder.surveyEditorRepository.getDraft(surveyId)
      val mobileOnlyForms =
        draft.forms.map { entry ->
          entry.copy(form = entry.form.copy(availability = FormAvailability.MOBILE))
        }
      state.dataHolder.surveyEditorRepository.saveDraft(
        surveyId,
        draft.copy(forms = mobileOnlyForms),
      )
      mobileOnlyForms.first().key
    }

    runDesktopComposeUiTest(width = 1440, height = 900) {
      val history = showDashboard(state)
      onNodeWithText("Collect data").performClick()
      waitForIdle()

      onNodeWithText("No forms in this survey are available on web.").assertExists()
      // Click the "Manage survey" item inside the open Collect data dropdown menu.
      onAllNodesWithText("Manage survey").onLast().performClick()
      waitForIdle()

      assertEquals("/surveys/$surveyId/editor/forms/$firstFormKey", history.path)
      assertEquals(
        FormPreviewTarget.WEB,
        state.surveyEditor.formEditor(firstFormKey).uiState.value.previewTarget,
      )
      onNodeWithText("Not available on web").assertExists()
    }
  }

  @Test
  fun collectDataMenu_listsOnlyWebAvailableFormsAndLaunchesOnClick() =
    runDesktopComposeUiTest(width = 640, height = 480) {
      val state = WebAppState(dataHolder = sampleDataHolder())
      val webForm =
        FormPreviewItem(
          id = "form-whisp-test",
          title = "Whisp test",
          description = "",
          version = "1",
          targetDatasetId = "whisp_test",
          targetDatasetName = "Whisp test",
          questionCount = 2,
          ctaLabel = "Whisp test",
          requiresEntity = false,
          availability = FormAvailability.BOTH,
        )
      val mobileOnlyForm =
        FormPreviewItem(
          id = "form-mobile-only",
          title = "Field GPS Check",
          description = "",
          version = "1",
          targetDatasetId = "gps_sites",
          targetDatasetName = "GPS sites",
          questionCount = 3,
          ctaLabel = "Field GPS Check",
          requiresEntity = false,
          availability = FormAvailability.MOBILE,
        )
      var launchedFormId: String? = null
      val actions =
        object : DataCollectionActions by state.viewModels.dataCollection {
          override fun launchFormFromFab(formId: String, pickerMode: MainSurveyViewMode) {
            launchedFormId = formId
          }
        }

      setContent {
        AppTheme {
          WebCollectDataMenuButton(
            uiState = DataCollectionUiState(forms = listOf(webForm, mobileOnlyForm)),
            actions = actions,
          )
        }
      }
      waitForIdle()

      onNodeWithText("Collect data").performClick()
      waitForIdle()

      onNodeWithText("Field GPS Check", substring = true).assertDoesNotExist()
      onNodeWithText("Whisp test", substring = true).performClick()
      waitForIdle()

      assertEquals("form-whisp-test", launchedFormId)
    }
}
