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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.openforis.collectone.v2.app.domain.model.editor.ChoiceSource
import org.openforis.collectone.v2.app.domain.model.editor.EditorChoice
import org.openforis.collectone.v2.app.domain.model.editor.EditorDataset
import org.openforis.collectone.v2.app.domain.model.editor.EditorDatasetProperty
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EditorPropertyKind
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.domain.model.editor.EditorRelevance
import org.openforis.collectone.v2.app.domain.model.editor.RelevanceOperator
import org.openforis.collectone.v2.web.ui.surveyeditor.formEditorViewModel
import org.openforis.collectone.v2.web.ui.surveyeditor.ui

/** Editing the choices of a select question in the Form editor. */
class FormEditorChoicesTest {

  private val treesPresent =
    EditorQuestion(
      key = "q1",
      name = "trees_present",
      type = EditorQuestionType.SELECT_ONE,
      label = "Are trees present?",
      choices = listOf(EditorChoice("yes", "Yes"), EditorChoice("no", "No")),
    )

  private val treeCount =
    EditorQuestion(
      key = "q2",
      name = "tree_count",
      type = EditorQuestionType.NUMBER,
      label = "How many trees?",
      relevance = EditorRelevance("q1", RelevanceOperator.EQUALS, "yes"),
    )

  private val treeForm =
    EditorForm(formId = "trees", title = "Trees", questions = listOf(treesPresent, treeCount))

  private val species =
    EditorDataset(
      id = "species",
      displayName = "Species",
      isMapLayer = false,
      keyProperty = "code",
      labelProperty = "name",
      properties =
        listOf(
          EditorDatasetProperty("code", "Code", EditorPropertyKind.TEXT),
          EditorDatasetProperty("name", "Name", EditorPropertyKind.TEXT),
        ),
    )

  private fun FormEditorViewModel.question(key: String) = ui.form.questions.single { it.key == key }

  @Test
  fun addChoice_appendsTheNextUnusedOption() {
    val viewModel = formEditorViewModel(treeForm)

    viewModel.addChoice("q1")

    assertEquals(
      EditorChoice("option_3", "Option 3"),
      viewModel.question("q1").choices.last(),
    )
  }

  @Test
  fun removeChoice_dropsOnlyThatChoice() {
    val viewModel = formEditorViewModel(treeForm)

    viewModel.removeChoice("q1", 0)

    assertEquals(listOf("no"), viewModel.question("q1").choices.map { it.value })
  }

  @Test
  fun updateChoice_renamingAValue_keepsDependentConditionsPointingAtIt() {
    val viewModel = formEditorViewModel(treeForm)

    viewModel.updateChoice("q1", 0, EditorChoice("y", "Yes"))

    assertEquals("y", viewModel.question("q1").choices.first().value)
    assertEquals("y", viewModel.question("q2").relevance?.value)
  }

  @Test
  fun setChoiceDataset_switchesTheQuestionToThatDataTable() {
    val viewModel = formEditorViewModel(treeForm, listOf(species))

    viewModel.setChoiceDataset("q1", "species")

    val question = viewModel.question("q1")
    assertEquals(ChoiceSource.DATA_TABLE, question.choiceSource)
    assertEquals("species", question.choiceDatasetId)
  }

  @Test
  fun setChoiceDataset_toNone_goesBackToManualChoices_withoutAddingFeatures() {
    val viewModel = formEditorViewModel(treeForm, listOf(species))
    viewModel.setChoiceDataset("q1", "species")

    viewModel.setChoiceDataset("q1", null)

    val question = viewModel.question("q1")
    assertEquals(ChoiceSource.MANUAL, question.choiceSource)
    assertFalse(question.allowAddEntity)
  }
}
