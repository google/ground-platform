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

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.openforis.collectone.v2.app.domain.model.editor.EditorChoice
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType

class ChoiceFileImportTest {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
  private val csv = "value,label\npine,Pine\nmaple,Maple"

  @AfterTest fun tearDown() = scope.cancel()

  private fun editorWithChoices(vararg choices: EditorChoice): FormEditorViewModel {
    val question =
      EditorQuestion(
        key = "q1",
        name = "species",
        label = "Species",
        type = EditorQuestionType.SELECT_ONE,
        choices = choices.toList(),
      )
    val formFlow =
      MutableStateFlow(EditorForm(formId = "f1", title = "F1", questions = listOf(question)))
    return FormEditorViewModel(
      formFlow = formFlow,
      datasetsFlow = MutableStateFlow(emptyList()),
      updateForm = { transform -> formFlow.value = transform(formFlow.value) },
    )
  }

  private fun FormEditorViewModel.choiceValues() =
    uiState.value.form.questions.single().choices.map { it.value }

  @Test
  fun placeholderChoices_areReplacedRightAway() {
    val editor =
      editorWithChoices(EditorChoice("option_1", "Option 1"), EditorChoice("option_2", "Option 2"))

    val outcome = editor.importChoicesFromFile("q1", csv, "trees.csv")

    assertEquals(ChoiceFileImport.Replaced, outcome)
    assertEquals(listOf("pine", "maple"), editor.choiceValues())
  }

  @Test
  fun noChoices_areReplacedRightAway() {
    val editor = editorWithChoices()

    val outcome = editor.importChoicesFromFile("q1", csv, "trees.csv")

    assertEquals(ChoiceFileImport.Replaced, outcome)
    assertEquals(listOf("pine", "maple"), editor.choiceValues())
  }

  @Test
  fun realChoices_needConfirmationBeforeChanging() {
    val editor = editorWithChoices(EditorChoice("oak", "Oak"))

    val outcome = editor.importChoicesFromFile("q1", csv, "trees.csv")

    assertEquals(
      listOf("pine", "maple"),
      (outcome as ChoiceFileImport.NeedsConfirmation).choices.map { it.value },
    )
    assertEquals(listOf("oak"), editor.choiceValues())
  }

  @Test
  fun emptyFile_importsNothing() {
    val editor = editorWithChoices()

    assertEquals(
      ChoiceFileImport.NothingToImport,
      editor.importChoicesFromFile("q1", "", "empty.csv"),
    )
  }
}
