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
package org.groundplatform.v2.web.ui.wizard

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.groundplatform.v2.app.data.seed.SampleOrganizationsData
import org.groundplatform.v2.web.ui.WebAppState
import org.groundplatform.v2.web.ui.WebPage

@OptIn(ExperimentalTestApi::class)
class CreateSurveyWizardPageTest {

  @Test
  fun clickingCloseButtonPromptsConfirmationBeforeDiscardingSurvey() =
    runDesktopComposeUiTest(width = 1280, height = 900) {
      val state = WebAppState()
      state.openCreateSurveyWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
      val workingId = state.createSurveyWizard.uiState.value.workingSurveyId

      setContent { MaterialTheme { CreateSurveyWizardPage(state = state) } }
      waitForIdle()

      // Clicking X ("Cancel survey creation") opens the discard confirmation dialog without
      // immediately discarding the survey.
      onNodeWithContentDescription("Cancel survey creation").performClick()
      waitForIdle()

      onNodeWithText("Discard survey?").assertIsDisplayed()
      onNodeWithText("If you leave now, this survey and any changes you've made will be lost.")
        .assertIsDisplayed()
      assertEquals(WebPage.CREATE_SURVEY, state.activePage)
      assertTrue(state.surveys.any { it.id == workingId })

      // Clicking "Keep editing" dismisses the dialog and keeps the survey in the wizard.
      onNodeWithText("Keep editing").performClick()
      waitForIdle()

      assertEquals(WebPage.CREATE_SURVEY, state.activePage)
      assertTrue(state.surveys.any { it.id == workingId })

      // Clicking X again and confirming "Discard" discards the survey and leaves the wizard.
      onNodeWithContentDescription("Cancel survey creation").performClick()
      waitForIdle()
      onNodeWithText("Discard").performClick()
      waitForIdle()

      assertEquals(WebPage.WEB_SURVEYS, state.activePage)
      assertFalse(state.surveys.any { it.id == workingId })
    }
}
