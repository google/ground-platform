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
package org.openforis.collectone.v2.web.ui.formeditor

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.openforis.collectone.v2.app.domain.model.FormAvailability
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType

/**
 * The Form editor in template mode ([FormEditorViewModel.forTemplate]), as an organization's
 * Templates tab opens it: the survey's submission settings give way to the template's details.
 */
@OptIn(ExperimentalTestApi::class)
class TemplateFormEditorTest {
  private val scope = CoroutineScope(Dispatchers.Unconfined + Job())

  @AfterTest fun cancel() = scope.cancel()

  private val plotForm =
    EditorForm(
      formId = "form-plot_audit",
      title = "Plot audit",
      questions =
        listOf(
          EditorQuestion("q1", "plot", EditorQuestionType.POLYGON, "Plot boundary"),
          EditorQuestion("q2", "farmer_name", EditorQuestionType.TEXT, "Farmer name"),
        ),
    )

  private fun withTemplateEditor(
    form: EditorForm = plotForm,
    block: ComposeUiTest.(FormEditorViewModel) -> Unit,
  ) =
    runDesktopComposeUiTest(width = 1600, height = 1000) {
      val editor = FormEditorViewModel.forTemplate(form, FormLibraryContext(), scope)
      editor.selectForm()
      setContent {
        MaterialTheme {
          val uiState by editor.uiState.collectAsState()
          FormEditorPage(
            uiState = uiState,
            actions = editor,
            previewController = editor.previewController.collectAsState().value,
            isDarkTheme = false,
            templateDetails = { Text("Template details slot") },
          )
        }
      }
      block(editor)
    }

  @Test
  fun formProperties_showTheTemplatesDetails_insteadOfSubmissionSettings() =
    withTemplateEditor { editor ->
      assertEquals(true, editor.uiState.value.isTemplate)
      onNodeWithText("Template title").assertIsDisplayed()
      onNodeWithText("Template details slot").assertIsDisplayed()
      onNodeWithText("new map layer of polygons", substring = true).assertIsDisplayed()
      onAllNodesWithText("Submissions").assertCountEquals(0)
      onNodeWithText("Library template", substring = true).assertIsDisplayed()
    }

  @Test
  fun aFormWithoutGeometry_saysSurveysGetADataTable() =
    withTemplateEditor(plotForm.copy(questions = plotForm.questions.drop(1))) {
      onNodeWithText("new data table", substring = true).assertIsDisplayed()
    }

  @Test
  fun webAvailableTemplate_hidesOpenInGoogleEarthToggle() =
    withTemplateEditor(plotForm.copy(availability = FormAvailability.BOTH)) { editor ->
      editor.updateOpenInGoogleEarth(true)
      assertEquals(false, editor.uiState.value.form.openInGoogleEarth)
      onAllNodesWithText("Enable Open in Google Earth").assertCountEquals(0)
    }
}
