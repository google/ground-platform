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
package org.openforis.collectone.v2.web.ui.surveyeditor

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.domain.model.SurveyLifecycleState
import org.openforis.collectone.v2.app.domain.model.editor.PlotReviewOptions
import org.openforis.collectone.v2.app.domain.model.editor.PlotReviewWorkflow

/** The Plot review section under a generated Map layer's sample design. */
@OptIn(ExperimentalTestApi::class)
class PlotReviewPanelTest {
  private val desk = "Check plots on imagery (web)"
  private val field = "Verify plots in the field (mobile)"
  private val fieldOptions =
    listOf(
      "Send plots below this confidence",
      "Random spot checks",
      "Show desk answers to field crews",
    )

  @Test
  fun openSurvey_turnsOnFieldValidation() =
    runDesktopComposeUiTest(width = 900, height = 1400) {
      val editor = surveyEditorViewModel()
      val key = editor.addSamplePlotsLayer()
      editor.updatePlotReview(key, PlotReviewOptions())
      setContent {
        MaterialTheme {
          val uiState by editor.uiState.collectAsState()
          PlotReviewPanel(uiState, editor, uiState.datasets.first { it.key == key })
        }
      }
      waitForIdle()

      onNodeWithText(desk).assertIsEnabled()
      onNodeWithText(field).assertIsEnabled().performClick()
      waitForIdle()

      assertTrue(PlotReviewWorkflow.setup(editor.ui.draft, key).options.fieldValidation)
      fieldOptions.forEach { onNodeWithText(it).assertIsEnabled() }
    }

  @Test
  fun closedSurvey_showsTheReviewButCantChangeIt() =
    runDesktopComposeUiTest(width = 900, height = 1400) {
      val editor = surveyEditorViewModel()
      val key = editor.addSamplePlotsLayer()
      editor.updatePlotReview(key, PlotReviewOptions())
      // A desk-only review of a closed survey: turning field validation on would apply at once.
      val closed = editor.ui.copy(lifecycleState = SurveyLifecycleState.CLOSED)
      setContent {
        MaterialTheme { PlotReviewPanel(closed, editor, closed.datasets.first { it.key == key }) }
      }
      waitForIdle()

      onNodeWithText(desk).assertIsNotEnabled()
      onNodeWithText(field).assertIsNotEnabled().performClick()
      waitForIdle()

      val setup = PlotReviewWorkflow.setup(editor.ui.draft, key)
      assertTrue(setup.isEnabled)
      assertFalse(setup.options.fieldValidation)
    }

  @Test
  fun closedSurvey_disablesTheFieldValidationOptions() =
    runDesktopComposeUiTest(width = 900, height = 1400) {
      val editor = surveyEditorViewModel()
      val key = editor.addSamplePlotsLayer()
      editor.updatePlotReview(key, PlotReviewOptions(fieldValidation = true))
      val closed = editor.ui.copy(lifecycleState = SurveyLifecycleState.CLOSED)
      setContent {
        MaterialTheme { PlotReviewPanel(closed, editor, closed.datasets.first { it.key == key }) }
      }
      waitForIdle()

      onNodeWithText(field).assertIsNotEnabled()
      fieldOptions.forEach { onNodeWithText(it).assertIsNotEnabled() }
    }
}
