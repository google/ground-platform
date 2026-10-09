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
package org.groundplatform.v2.devtools.prototypeapp.ui.surveyeditor

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import org.groundplatform.v2.devtools.prototypeapp.ui.common.TextFilePickResult
import org.groundplatform.v2.devtools.prototypeapp.ui.state.SurveyEditorSection
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.SurveyEditorViewModel
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.surveyEditorViewModel
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.ui

/** Every item in the survey editor's left-hand navigation must select on a single click. */
@OptIn(ExperimentalTestApi::class)
class SurveyEditorClickTest {

  private fun withNavigation(block: ComposeUiTest.(SurveyEditorViewModel) -> Unit) =
    runDesktopComposeUiTest(width = 600, height = 1000) {
      val state = surveyEditorViewModel()
      setContent {
        MaterialTheme {
          val uiState by state.uiState.collectAsState()
          SurveyNavigation(
            uiState = uiState,
            actions = state,
            modifier = Modifier.width(280.dp).fillMaxHeight(),
          )
        }
      }
      block(state)
    }

  @Test
  fun clickWithSlightMouseMovementSelectsForm() = withNavigation { state ->
    val entry = state.ui.forms.first()
    // A real click rarely keeps the mouse perfectly still between press and release.
    onAllNodesWithText(entry.form.title, useUnmergedTree = true).onFirst().performMouseInput {
      moveTo(center)
      press()
      moveBy(Offset(2f, 1f))
      release()
    }
    waitForIdle()
    assertEquals(SurveyEditorSection.Form(entry.key), state.ui.section)
  }

  @Test
  fun clickWithSlightMouseMovementSelectsDataset() = withNavigation { state ->
    val dataset = (state.ui.mapLayers + state.ui.dataTables).first()
    onAllNodesWithText(dataset.displayName, useUnmergedTree = true).onFirst().performMouseInput {
      moveTo(center)
      press()
      moveBy(Offset(1f, 3f))
      release()
    }
    waitForIdle()
    assertEquals(SurveyEditorSection.Dataset(dataset.key), state.ui.section)
  }

  @Test
  fun dragPastSlopStillReorders() = withNavigation { state ->
    val first = state.ui.forms.first()
    if (state.ui.forms.size < 2) return@withNavigation
    onAllNodesWithText(first.form.title, useUnmergedTree = true).onFirst().performMouseInput {
      moveTo(center)
      press()
      repeat(10) { moveBy(Offset(0f, 12f)) }
      release()
    }
    waitForIdle()
    assertEquals(SurveyEditorSection.Details, state.ui.section)
    assertEquals(first.key, state.ui.forms[1].key)
  }

  @Test
  fun addFormButtonOpensDialogAndEmptyFormCreatesForm() = withNavigation { state ->
    val initialCount = state.ui.forms.size
    onNodeWithContentDescription("Add form").performClick()
    waitForIdle()

    onNodeWithText("Empty form").assertIsDisplayed()
    onNodeWithText("Use a template").assertIsDisplayed()
    onNodeWithText("Import from XML").assertIsDisplayed()

    onNodeWithText("Empty form").performClick()
    waitForIdle()

    assertEquals(initialCount + 1, state.ui.forms.size)
    assertEquals("New form", state.ui.selectedForm?.form?.title)
  }

  @Test
  fun addFormDialogImportsFromXmlAfterPreview() =
    runDesktopComposeUiTest(width = 800, height = 800) {
      val state = surveyEditorViewModel()
      val initialCount = state.ui.forms.size
      val xml =
        """
        <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml">
          <h:head>
            <h:title>Imported tree survey</h:title>
            <model>
              <instance><data id="imported_tree_survey"><species/></data></instance>
              <bind nodeset="/data/species" type="string"/>
            </model>
          </h:head>
          <h:body>
            <input ref="/data/species"><label>Tree species</label></input>
          </h:body>
        </h:html>
        """
          .trimIndent()
      setContent {
        MaterialTheme {
          AddFormDialog(
            actions = state,
            onDismiss = {},
            pickTextFile = { _, onResult ->
              onResult(TextFilePickResult.Picked("tree_survey.xml", xml))
            },
          )
        }
      }

      onNodeWithText(FORM_TEMPLATES_COMING_SOON).assertIsDisplayed()
      onNodeWithText("Import from XML").performClick()
      waitForIdle()

      onNodeWithText("Import tree_survey.xml").assertIsDisplayed()
      onNodeWithText("Imported tree survey").assertIsDisplayed()
      onNodeWithText("Import").performClick()
      waitForIdle()

      assertEquals(initialCount + 1, state.ui.forms.size)
      assertEquals("Imported tree survey", state.ui.selectedForm?.form?.title)
    }
}
