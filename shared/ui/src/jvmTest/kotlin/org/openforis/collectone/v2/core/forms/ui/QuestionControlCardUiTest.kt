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
package org.openforis.collectone.v2.core.forms.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

/**
 * When [QuestionControlCard] shows a question's validation errors: on blur or when the runner asks
 * for them (Next / Submit), never while the collector is still typing.
 */
@OptIn(ExperimentalTestApi::class)
class QuestionControlCardUiTest {

  private fun heightAndNotesForm() =
    formController(
      title = "Tree Survey",
      model =
        """
        <instance><data id="trees" version="1"><height_m/><notes/></data></instance>
        <bind nodeset="/data/height_m" type="decimal" constraint=". &gt; 0"
              jr:constraintMsg="Height must be positive"/>
        <bind nodeset="/data/notes" type="string"/>
        """,
      body =
        """
        <input ref="/data/height_m"><label>Height (meters)</label></input>
        <input ref="/data/notes"><label>Notes</label></input>
        """,
    )

  @Test
  fun invalidAnswer_isHiddenWhileTheFieldHasFocus() = runComposeUiTest {
    val controller = heightAndNotesForm()
    setContent {
      AppTheme {
        QuestionControlCard(controller.currentQuestion(), controller, showValidationErrors = false)
      }
    }

    onAllNodes(hasSetTextAction())[0].performTextInput("-5")

    onNodeWithText("Height must be positive").assertDoesNotExist()
  }

  @Test
  fun invalidAnswer_isShownOnceFocusLeavesTheQuestion() = runComposeUiTest {
    val controller = heightAndNotesForm()
    setContent {
      AppTheme {
        Column {
          controller.steps.filterIsInstance<FormWizardStep.QuestionStep>().forEach { step ->
            QuestionControlCard(step.control, controller, showValidationErrors = false)
          }
        }
      }
    }

    onAllNodes(hasSetTextAction())[0].performTextInput("-5")
    onAllNodes(hasSetTextAction())[1].performClick()

    onNodeWithText("Height must be positive").assertExists()
  }

  @Test
  fun invalidAnswer_isShownWithoutInteractionWhenTheRunnerAsks() = runComposeUiTest {
    val controller = heightAndNotesForm()
    controller.updateDouble("/data/height_m", -5.0)
    setContent {
      AppTheme {
        QuestionControlCard(controller.currentQuestion(), controller, showValidationErrors = true)
      }
    }

    onNodeWithText("Height must be positive").assertExists()
  }

  @Test
  fun untouchedInvalidAnswer_isHiddenUntilTheRunnerAsks() = runComposeUiTest {
    val controller = heightAndNotesForm()
    controller.updateDouble("/data/height_m", -5.0)
    setContent {
      AppTheme {
        QuestionControlCard(controller.currentQuestion(), controller, showValidationErrors = false)
      }
    }

    onNodeWithText("Height must be positive").assertDoesNotExist()
  }

  @Test
  fun mobileVariant_rendersGpsControlNormally_whileWebVariantRendersFallbackWarning() =
    runComposeUiTest {
      val controller =
        formController(
          title = "Tree GPS",
          model =
            """
            <instance><data id="trees" version="1"><tree_gps/></data></instance>
            <bind nodeset="/data/tree_gps" type="geopoint" required="true()"/>
            """,
          body =
            """
            <input ref="/data/tree_gps">
              <label>Tree GPS location</label>
              <hint>Walk to the base of the trunk</hint>
            </input>
            """,
        )

      // Default variant is MOBILE: renders normally without fallback warning.
      setContent {
        AppTheme {
          QuestionControlCard(
            controller.currentQuestion(),
            controller,
            showValidationErrors = false,
          )
        }
      }
      onNodeWithText("Tree GPS location *").assertExists()
      onNodeWithText("Walk to the base of the trunk").assertExists()
      onNodeWithTag("variant_incompatible_warning_/data/tree_gps").assertDoesNotExist()

      // Under WEB variant, GPS-only geometry is compatible (no compatibility warning).
      setContent {
        AppTheme {
          CompositionLocalProvider(LocalFormVariant provides FormVariant.WEB) {
            QuestionControlCard(
              controller.currentQuestion(),
              controller,
              showValidationErrors = false,
            )
          }
        }
      }
      onNodeWithText("Tree GPS location *").assertExists()
      onNodeWithText("Walk to the base of the trunk").assertExists()
      onNodeWithTag("variant_incompatible_warning_/data/tree_gps").assertDoesNotExist()

      // Under GOOGLE_EARTH variant, renders title, hint, and fallback warning without the input
      // widget.
      setContent {
        AppTheme {
          CompositionLocalProvider(LocalFormVariant provides FormVariant.GOOGLE_EARTH) {
            QuestionControlCard(
              controller.currentQuestion(),
              controller,
              showValidationErrors = false,
            )
          }
        }
      }
      onNodeWithText("Tree GPS location").assertExists()
      onNodeWithText("Walk to the base of the trunk").assertExists()
      onNodeWithTag("variant_incompatible_warning_/data/tree_gps").assertExists()
      onNodeWithText(
          "Location questions aren't available on Google Earth. This question will be skipped. Contact the survey organizer to resolve this."
        )
        .assertExists()
    }
}
