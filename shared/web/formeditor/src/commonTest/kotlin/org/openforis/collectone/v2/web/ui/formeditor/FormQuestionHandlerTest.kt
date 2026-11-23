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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.domain.model.FormAvailability
import org.openforis.collectone.v2.app.domain.model.editor.EditorChoice
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.domain.model.editor.EditorRelevance
import org.openforis.collectone.v2.app.domain.model.editor.FormPreviewTarget
import org.openforis.collectone.v2.app.domain.model.editor.GeometryCapture
import org.openforis.collectone.v2.app.domain.model.editor.RelevanceOperator

class FormQuestionHandlerTest {

  @Test
  fun duplicateQuestionBranch_copiesTransitiveBranchAndRewiresInternalLinksWithUndoRedo() {
    val root =
      EditorQuestion(
        key = "q1",
        name = "country",
        type = EditorQuestionType.SELECT_ONE,
        label = "Country",
        choices = listOf(EditorChoice("ke", "Kenya")),
      )
    val child =
      EditorQuestion(
        key = "q2",
        name = "region",
        type = EditorQuestionType.SELECT_ONE,
        label = "Region",
        parentQuestionKey = "q1",
        relevance = EditorRelevance("q1", RelevanceOperator.EQUALS, "ke"),
        choices = listOf(EditorChoice("rift", "Rift Valley", parentValue = "ke")),
      )
    val grandchild =
      EditorQuestion(
        key = "q3",
        name = "notes",
        type = EditorQuestionType.TEXT,
        label = "Notes",
        relevance = EditorRelevance("q2", RelevanceOperator.IS_ANSWERED),
      )
    val unrelated =
      EditorQuestion(
        key = "q4",
        name = "observer",
        type = EditorQuestionType.TEXT,
        label = "Observer",
      )
    val vm =
      FormEditorViewModel.standalone(
        EditorForm("f", "Survey", listOf(root, child, grandchild, unrelated))
      )

    vm.duplicateQuestionBranch("q1")

    val questions = vm.uiState.value.form.questions
    assertEquals(7, questions.size)
    // Copies are inserted immediately after the branch's last member (q3), before q4.
    assertEquals("q4", questions.last().key)
    val rootCopy = questions[3]
    val childCopy = questions[4]
    val grandchildCopy = questions[5]
    assertEquals(rootCopy.key, vm.uiState.value.selectedKey)
    assertEquals("country_2", rootCopy.name)
    assertEquals(rootCopy.key, childCopy.parentQuestionKey)
    assertEquals(rootCopy.key, childCopy.relevance?.sourceQuestionKey)
    assertEquals(childCopy.key, grandchildCopy.relevance?.sourceQuestionKey)

    vm.undo()
    assertEquals(listOf("q1", "q2", "q3", "q4"), vm.uiState.value.form.questions.map { it.key })

    vm.redo()
    assertEquals(7, vm.uiState.value.form.questions.size)
  }

  @Test
  fun sectionLifecycle_addAssignDuplicateAndDeleteUpdatesQuestionsAndSelection() {
    val vm =
      FormEditorViewModel.standalone(
        EditorForm(
          formId = "f",
          title = "Form",
          questions =
            listOf(
              EditorQuestion("q1", "tree", EditorQuestionType.TEXT, "Tree"),
              EditorQuestion("q2", "height", EditorQuestionType.NUMBER, "Height"),
            ),
        )
      )

    val secId = vm.addSection(title = "Plot measurements", repeatable = true)
    vm.setQuestionSection("q1", secId)
    vm.setQuestionSection(questionIndex = 1, sectionId = secId)
    assertTrue(vm.uiState.value.form.questions.all { it.sectionId == secId })

    vm.duplicateSection(secId)
    val stateAfterDup = vm.uiState.value
    assertEquals(2, stateAfterDup.form.sections.size)
    val copiedSection = stateAfterDup.form.sections.last()
    assertEquals(copiedSection.id, stateAfterDup.selectedSectionId)
    assertEquals("Plot measurements (copy)", copiedSection.title)
    assertEquals(2, stateAfterDup.form.questions.count { it.sectionId == copiedSection.id })

    // Deleting a section keeps its questions in the Form while clearing their sectionId.
    vm.deleteSection(secId)
    val stateAfterDelete = vm.uiState.value
    assertEquals(listOf(copiedSection.id), stateAfterDelete.form.sections.map { it.id })
    assertEquals("", stateAfterDelete.form.find("q1")?.sectionId)
    assertEquals("", stateAfterDelete.form.find("q2")?.sectionId)
  }

  @Test
  fun relevanceValidationAndWebCompatibilityHelpers_updateQuestionsCleanly() {
    val q1 =
      EditorQuestion(
        key = "q1",
        name = "has_trees",
        type = EditorQuestionType.SELECT_ONE,
        label = "Has trees?",
        choices = listOf(EditorChoice("yes", "Yes"), EditorChoice("no", "No")),
      )
    val q2 =
      EditorQuestion(
        key = "q2",
        name = "boundary",
        type = EditorQuestionType.POLYGON,
        label = "Walk boundary",
        capture = GeometryCapture.GPS_ONLY,
      )
    val vm =
      FormEditorViewModel.standalone(
        EditorForm(
          formId = "f",
          title = "Form",
          questions = listOf(q1, q2),
          availability = FormAvailability.MOBILE,
        )
      )

    // Enable default relevance on q2 → links to preceding choice question q1 with its first choice.
    vm.enableRelevance("q2")
    val rel = assertNotNull(vm.uiState.value.form.find("q2")?.relevance)
    assertEquals("q1", rel.sourceQuestionKey)
    assertEquals("yes", rel.value)

    vm.disableRelevance("q2")
    assertNull(vm.uiState.value.form.find("q2")?.relevance)

    // Validation normalizes an all-default EditorValidation back to null.
    vm.updateValidation("q1") { it.copy(min = "3") }
    assertEquals("3", vm.uiState.value.form.find("q1")?.validation?.min)
    vm.updateValidation("q1") { it.copy(min = "") }
    assertNull(vm.uiState.value.form.find("q1")?.validation)

    // Web preview compatibility helper enables web availability cleanly even with GPS-only
    // geometry.
    vm.selectPreviewTarget(FormPreviewTarget.WEB)
    assertFalse(vm.uiState.value.form.availability.includesWeb)
    vm.enableOnPreviewTarget()
    assertTrue(vm.uiState.value.form.availability.includesWeb)
    assertTrue(vm.uiState.value.issues.isEmpty())

    vm.clearUndoRedo()
    assertFalse(vm.uiState.value.canUndo)
    assertFalse(vm.uiState.value.canRedo)
  }

  @Test
  fun addQuestionAndAddSection_respectStartMiddleAndEndPositions() {
    val vm =
      FormEditorViewModel.standalone(
        EditorForm(
          formId = "f",
          title = "Form",
          questions =
            listOf(
              EditorQuestion("q1", "first", EditorQuestionType.TEXT, "First"),
              EditorQuestion("q2", "second", EditorQuestionType.TEXT, "Second"),
            ),
        )
      )
    // Deselect questions (as when clicking canvas backdrop) and verify explicit atIndex still wins.
    vm.selectForm()
    assertNull(vm.uiState.value.selectedKey)

    vm.addQuestion(EditorQuestionType.DATE, atIndex = 0)
    val startKey = vm.uiState.value.form.questions[0].key
    assertEquals(listOf(startKey, "q1", "q2"), vm.uiState.value.form.questions.map { it.key })

    vm.selectForm()
    vm.addQuestion(EditorQuestionType.NUMBER, atIndex = 2)
    val midKey = vm.uiState.value.form.questions[2].key
    assertEquals(
      listOf(startKey, "q1", midKey, "q2"),
      vm.uiState.value.form.questions.map { it.key },
    )

    val secId = vm.addSection(title = "Inserted Section", repeatable = false, atIndex = 1)
    assertEquals(1, vm.uiState.value.form.section(secId)?.insertAtIndex)

    vm.addQuestion(EditorQuestionType.PHOTO, atIndex = 1, sectionId = secId)
    val secQKey = vm.uiState.value.form.questions[1].key
    assertEquals(
      listOf(startKey, secQKey, "q1", midKey, "q2"),
      vm.uiState.value.form.questions.map { it.key },
    )
    assertEquals(secId, vm.uiState.value.form.questions[1].sectionId)
    assertNull(vm.uiState.value.form.section(secId)?.insertAtIndex)
  }
}
