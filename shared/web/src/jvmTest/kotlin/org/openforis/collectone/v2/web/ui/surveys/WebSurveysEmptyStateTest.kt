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

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.web.ui.WebAppState

@OptIn(ExperimentalTestApi::class)
class WebSurveysEmptyStateTest {

  @Test
  fun emptySurveysList_showsIllustrationAndCreateSurveyButton() =
    runDesktopComposeUiTest(width = 1200, height = 800) {
      val state = WebAppState(dataHolder = sampleDataHolder())
      val emptyUiState = state.viewModels.dashboard.uiState.value.copy(surveys = emptyList())
      var createWizardOpened = false

      setContent {
        MaterialTheme {
          WebSurveysPage(
            uiState = emptyUiState,
            onOpenSurvey = {},
            onCreateSurvey = {},
            header = {},
            onOpenCreateSurveyWizard = { createWizardOpened = true },
            isLoading = false,
          )
        }
      }
      waitForIdle()

      onNodeWithText("No surveys yet. Create one to get started.").assertIsDisplayed()
      onNodeWithText("Create survey").assertIsDisplayed().performClick()
      waitForIdle()

      assertTrue(createWizardOpened)
    }
}
