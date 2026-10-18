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
package org.groundplatform.v2.web.ui.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.groundplatform.v2.app.domain.model.editor.ChoiceSource
import org.groundplatform.v2.app.domain.model.editor.DatasetKind
import org.groundplatform.v2.app.domain.model.editor.EditorChoice
import org.groundplatform.v2.app.domain.model.editor.EditorForm
import org.groundplatform.v2.app.domain.model.editor.EditorQuestion
import org.groundplatform.v2.app.domain.model.editor.EditorQuestionType
import org.groundplatform.v2.app.domain.model.editor.EditorRelevance
import org.groundplatform.v2.app.domain.model.editor.FlowEdge
import org.groundplatform.v2.app.domain.model.editor.FlowEdgeKind
import org.groundplatform.v2.app.domain.model.editor.FormFlowGraph
import org.groundplatform.v2.app.domain.model.editor.RelevanceOperator
import org.groundplatform.v2.app.domain.model.editor.estimatedMinutes
import org.groundplatform.v2.app.domain.model.editor.longestPathQuestionCount
import org.groundplatform.v2.web.ui.state.FormEditorUiState

class FormEditorViewModelTest {

  @Test
  fun emptyFormHasZeroLongestPathAndZeroEstimatedMinutes() {
    val emptyForm = EditorForm(formId = "empty", title = "Empty", questions = emptyList())
    assertEquals(0, FormFlowGraph.longestPathQuestionCount(emptyForm))
    assertEquals(0, FormFlowGraph.estimatedMinutes(emptyForm))

    val uiState = FormEditorUiState(form = emptyForm)
    assertEquals(0, uiState.longestPathQuestionCount)
    assertEquals(0, uiState.estimatedCompletionMinutes)
    assertFalse(uiState.isFatigueWarning)
  }

  @Test
  fun linearAndConditionalFormsComputeLongestPathAndMinutesAtTwoMinutesPerQuestion() {
    val q1 =
      EditorQuestion(
        key = "q1",
        name = "has_trees",
        type = EditorQuestionType.SELECT_ONE,
        label = "Are trees present?",
        choices = listOf(EditorChoice("yes", "Yes"), EditorChoice("no", "No")),
      )
    val q2 =
      EditorQuestion(
        key = "q2",
        name = "tree_count",
        type = EditorQuestionType.NUMBER,
        label = "How many trees?",
        relevance = EditorRelevance("q1", RelevanceOperator.EQUALS, "yes"),
      )
    val q3 =
      EditorQuestion(
        key = "q3",
        name = "notes",
        type = EditorQuestionType.TEXT,
        label = "Additional notes",
      )
    val form = EditorForm(formId = "f1", title = "Tree survey", questions = listOf(q1, q2, q3))

    assertEquals(3, FormFlowGraph.longestPathQuestionCount(form))
    assertEquals(6, FormFlowGraph.estimatedMinutes(form))

    // Custom DAG edges where slot 2 is bypassed (0 -> 1 -> 3 -> 4) visits only 2 questions.
    val bypassEdges =
      listOf(
        FlowEdge(from = 0, to = 1, kind = FlowEdgeKind.NEXT),
        FlowEdge(from = 1, to = 3, kind = FlowEdgeKind.SKIP),
        FlowEdge(from = 3, to = 4, kind = FlowEdgeKind.NEXT),
      )
    assertEquals(2, FormFlowGraph.longestPathQuestionCount(form, bypassEdges))
    assertEquals(4, FormFlowGraph.estimatedMinutes(form, bypassEdges))
  }

  @Test
  fun fatigueWarningTriggersOnlyWhenLongestPathExceedsTenQuestions() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    try {
      val initialQuestions =
        (1..10).map { i ->
          EditorQuestion(
            key = "q$i",
            name = "field_$i",
            type = EditorQuestionType.TEXT,
            label = "Question $i",
          )
        }
      val formFlow =
        MutableStateFlow(
          EditorForm(formId = "fatigue_test", title = "Survey", questions = initialQuestions)
        )
      val vm =
        FormEditorViewModel(
          formFlow = formFlow,
          datasetsFlow = MutableStateFlow(emptyList()),
          updateForm = { transform -> formFlow.value = transform(formFlow.value) },
          scope = scope,
        )

      // Exactly 10 questions -> 20 minutes -> no fatigue warning.
      assertEquals(10, vm.uiState.value.longestPathQuestionCount)
      assertEquals(20, vm.uiState.value.estimatedCompletionMinutes)
      assertFalse(vm.uiState.value.isFatigueWarning)

      // Adding an 11th question -> 22 minutes -> triggers fatigue warning (> 10 questions).
      vm.addQuestion(EditorQuestionType.TEXT)
      assertEquals(11, vm.uiState.value.longestPathQuestionCount)
      assertEquals(22, vm.uiState.value.estimatedCompletionMinutes)
      assertTrue(vm.uiState.value.isFatigueWarning)
    } finally {
      scope.cancel()
    }
  }

  @Test
  fun createDatasetForChoiceQuestion_updatesQuestionBindingAndDatasetKind() {
    val q1 =
      EditorQuestion(
        key = "q1",
        name = "tree_species",
        type = EditorQuestionType.SELECT_ONE,
        label = "Tree Species",
      )
    val formFlow =
      MutableStateFlow(
        EditorForm(formId = "species_form", title = "Species", questions = listOf(q1))
      )
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    try {
      val vm =
        FormEditorViewModel(
          formFlow = formFlow,
          datasetsFlow = MutableStateFlow(emptyList()),
          updateForm = { transform -> formFlow.value = transform(formFlow.value) },
          scope = scope,
        )

      vm.createDatasetForChoiceQuestion(0, DatasetKind.MAP_LAYER)
      val updatedQ = vm.uiState.value.form.questions[0]
      assertEquals(ChoiceSource.MAP_LAYER, updatedQ.choiceSource)
      assertEquals("new_dataset", updatedQ.choiceDatasetId)

      vm.createDatasetForChoiceQuestion("q1", DatasetKind.DATA_TABLE)
      val updatedQ2 = vm.uiState.value.form.questions[0]
      assertEquals(ChoiceSource.DATA_TABLE, updatedQ2.choiceSource)
      assertEquals("new_dataset", updatedQ2.choiceDatasetId)
    } finally {
      scope.cancel()
    }
  }

  @Test
  fun cascadingSelects_updatesParentQuestionKeyAndChoiceParentValue() {
    val q1 =
      EditorQuestion(
        key = "country",
        name = "country",
        type = EditorQuestionType.SELECT_ONE,
        label = "Country",
        choices = listOf(EditorChoice("us", "United States"), EditorChoice("ca", "Canada")),
      )
    val q2 =
      EditorQuestion(
        key = "region",
        name = "region",
        type = EditorQuestionType.SELECT_ONE,
        label = "Region",
        choices =
          listOf(
            EditorChoice("ca_state", "California"),
            EditorChoice("on_prov", "Ontario"),
          ),
      )
    val formFlow =
      MutableStateFlow(
        EditorForm(formId = "geo_form", title = "Geography", questions = listOf(q1, q2))
      )
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    try {
      val vm =
        FormEditorViewModel(
          formFlow = formFlow,
          datasetsFlow = MutableStateFlow(emptyList()),
          updateForm = { transform -> formFlow.value = transform(formFlow.value) },
          scope = scope,
        )

      // Set parent question key
      vm.updateQuestionParentKey(1, "country")
      assertEquals("country", vm.uiState.value.form.questions[1].parentQuestionKey)

      // Set choice parent value by index
      vm.updateChoiceParentValue(1, 0, "us")
      assertEquals("us", vm.uiState.value.form.questions[1].choices[0].parentValue)

      // Set choice parent value by key
      vm.updateChoiceParentValue("region", 1, "ca")
      assertEquals("ca", vm.uiState.value.form.questions[1].choices[1].parentValue)
    } finally {
      scope.cancel()
    }
  }

  @Test
  fun importQuestionChoices_replacesAndAppendsChoices() {
    val q1 =
      EditorQuestion(
        key = "q1",
        name = "items",
        type = EditorQuestionType.SELECT_ONE,
        label = "Items",
        choices = listOf(EditorChoice("item_1", "Item 1")),
      )
    val formFlow =
      MutableStateFlow(
        EditorForm(formId = "import_form", title = "Import", questions = listOf(q1))
      )
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    try {
      val vm =
        FormEditorViewModel(
          formFlow = formFlow,
          datasetsFlow = MutableStateFlow(emptyList()),
          updateForm = { transform -> formFlow.value = transform(formFlow.value) },
          scope = scope,
        )

      val importedChoices =
        listOf(
          EditorChoice("apple", "Apple", parentValue = "fruit"),
          EditorChoice("carrot", "Carrot", parentValue = "vegetable"),
        )

      // Append = false -> Replaces choices
      vm.importQuestionChoices(0, importedChoices, append = false)
      assertEquals(2, vm.uiState.value.form.questions[0].choices.size)
      assertEquals("apple", vm.uiState.value.form.questions[0].choices[0].value)
      assertEquals("fruit", vm.uiState.value.form.questions[0].choices[0].parentValue)
      assertEquals("carrot", vm.uiState.value.form.questions[0].choices[1].value)

      // Append = true -> Appends choices
      val additionalChoices = listOf(EditorChoice("banana", "Banana", parentValue = "fruit"))
      vm.importQuestionChoices("q1", additionalChoices, append = true)
      assertEquals(3, vm.uiState.value.form.questions[0].choices.size)
      assertEquals("banana", vm.uiState.value.form.questions[0].choices[2].value)
    } finally {
      scope.cancel()
    }
  }

  @Test
  fun sections_addUpdateDeleteAndAssignQuestions() {
    val q1 = EditorQuestion(key = "q1", name = "species", label = "Species", type = EditorQuestionType.TEXT)
    val q2 = EditorQuestion(key = "q2", name = "height", label = "Height", type = EditorQuestionType.NUMBER)
    val formFlow =
      MutableStateFlow(
        EditorForm(formId = "sec_test", title = "Sec Test", questions = listOf(q1, q2))
      )
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    try {
      val vm =
        FormEditorViewModel(
          formFlow = formFlow,
          datasetsFlow = MutableStateFlow(emptyList()),
          updateForm = { transform -> formFlow.value = transform(formFlow.value) },
          scope = scope,
        )

      // Add section
      val secId = vm.addSection("Trees", repeatable = true)
      assertEquals(1, vm.uiState.value.form.sections.size)
      val sec = vm.uiState.value.form.sections[0]
      assertEquals(secId, sec.id)
      assertEquals("Trees", sec.title)
      assertTrue(sec.repeatable)

      // Assign questions to section
      vm.setQuestionSection(0, secId)
      vm.setQuestionSection("q2", secId)
      assertEquals(secId, vm.uiState.value.form.questions[0].sectionId)
      assertEquals(secId, vm.uiState.value.form.questions[1].sectionId)

      // Update section
      vm.updateSection(secId) { it.copy(repeatCountExpression = "5") }
      assertEquals("5", vm.uiState.value.form.sections[0].repeatCountExpression)

      // Delete section -> unassigns questions
      vm.deleteSection(secId)
      assertEquals(0, vm.uiState.value.form.sections.size)
      assertEquals("", vm.uiState.value.form.questions[0].sectionId)
      assertEquals("", vm.uiState.value.form.questions[1].sectionId)
    } finally {
      scope.cancel()
    }
  }

  @Test
  fun flowLayout_positionsConditionalQuestionsOnBranchLanes() {
    val q1 =
      EditorQuestion(
        key = "q1",
        name = "has_trees",
        label = "Has trees?",
        type = EditorQuestionType.SELECT_ONE,
      )
    val q2 =
      EditorQuestion(
        key = "q2",
        name = "species",
        label = "Species",
        type = EditorQuestionType.TEXT,
        relevance = EditorRelevance("q1", RelevanceOperator.EQUALS, "yes"),
      )
    val q3 =
      EditorQuestion(
        key = "q3",
        name = "notes",
        label = "Notes",
        type = EditorQuestionType.TEXT,
      )
    val form = EditorForm(formId = "f", title = "F", questions = listOf(q1, q2, q3))
    val edges = FormFlowGraph.edges(form)

    val layout =
      org.groundplatform.v2.web.ui.formeditor.FlowLayout(form.questions.size, edges, form)
    // q1 is unconditional: slot 1, row 0
    assertEquals(0, layout.slotRow(1))
    // q2 is conditional on q1: slot 2, branches to row > 0
    assertTrue(layout.slotRow(2) > 0)
    // q3 is unconditional: slot 3, on main trunk row 0
    assertEquals(0, layout.slotRow(3))

    // Layout exposes slotPosition, slotX, slotY, totalWidth, totalHeight
    val pos = layout.slotPosition(1)
    assertEquals(layout.slotX(1), pos.x)
    assertEquals(layout.slotY(1), pos.y)
    assertTrue(layout.totalWidth > layout.slotX(layout.endSlot))
    assertTrue(layout.totalHeight > layout.slotY(2))
  }
}
