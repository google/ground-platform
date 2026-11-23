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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.domain.model.editor.EditorChoice
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EditorIssueMessage
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.domain.model.editor.EditorRelevance
import org.openforis.collectone.v2.app.domain.model.editor.EditorSection
import org.openforis.collectone.v2.app.domain.model.editor.RelevanceOperator
import org.openforis.collectone.v2.app.domain.model.messageKey

/** Authoring conditional singleton and repeated sections through [FormEditorViewModel]. */
class FormEditorConditionalSectionsTest {
  private fun q(
    key: String,
    section: String = "",
    type: EditorQuestionType = EditorQuestionType.TEXT,
  ) =
    EditorQuestion(
      key = key,
      name = key,
      type = type,
      label = key,
      sectionId = section,
      choices =
        if (type.hasChoices) listOf(EditorChoice("yes", "Yes"), EditorChoice("no", "No"))
        else emptyList(),
    )

  private val vm =
    FormEditorViewModel.standalone(
      initialForm =
        EditorForm(
          formId = "f",
          title = "F",
          questions =
            listOf(
              q("visited", type = EditorQuestionType.SELECT_ONE),
              q("gate", type = EditorQuestionType.SELECT_ONE),
              q("t1", "trees"),
              q("t2", "trees"),
              q("x"),
            ),
          sections = listOf(EditorSection("trees", "Trees", repeatable = true)),
        )
    )

  private val form: EditorForm
    get() = vm.uiState.value.form

  @Test
  fun enableSectionRelevance_testsTheClosestEarlierQuestion() {
    vm.enableSectionRelevance("trees")
    assertEquals(
      EditorRelevance("gate", RelevanceOperator.EQUALS, "yes"),
      form.section("trees")!!.relevance,
    )
    // The questions inside don't get display logic of their own.
    assertTrue(form.questionsIn("trees").all { it.relevance == null })
    assertEquals(emptyList(), vm.uiState.value.issues)

    vm.disableSectionRelevance("trees")
    assertNull(form.section("trees")!!.relevance)
  }

  @Test
  fun enableSectionRelevance_withoutEarlierQuestions_doesNothing() {
    vm.moveSection("trees", -1)
    vm.moveSection("trees", -1)
    assertEquals("trees", form.questions.first().sectionId)
    vm.enableSectionRelevance("trees")
    assertNull(form.section("trees")!!.relevance)
  }

  @Test
  fun deletingTheSourceQuestion_makesTheSectionAlwaysShown() {
    vm.enableSectionRelevance("trees")
    vm.deleteQuestion("gate")
    assertNull(form.section("trees")!!.relevance)
    assertEquals(emptyList(), vm.uiState.value.issues)
  }

  @Test
  fun renamingTheSourceChoice_updatesTheSectionCondition() {
    vm.enableSectionRelevance("trees")
    vm.updateChoice("gate", 0, EditorChoice("si", "Sí"))
    assertEquals("si", form.section("trees")!!.relevance!!.value)
  }

  @Test
  fun movingTheSourceIntoTheSection_isReportedOnTheSection() {
    vm.enableSectionRelevance("trees")
    vm.setQuestionSection("gate", "trees")
    assertEquals(listOf("visited", "gate", "t1", "t2", "x"), form.questions.map { it.key })
    val issue = vm.uiState.value.issuesForSection("trees").single()
    assertEquals(EditorIssueMessage.SECTION_DISPLAY_LOGIC_SOURCE_INSIDE, issue.message.messageKey)
    // Section issues aren't Form-level issues.
    assertEquals(emptyList(), vm.uiState.value.formIssues)
  }

  @Test
  fun moveSection_movesTheWholeSectionAndIsUndoable() {
    vm.moveSection("trees", 1)
    assertEquals(listOf("visited", "gate", "x", "t1", "t2"), form.questions.map { it.key })
    vm.undo()
    assertEquals(listOf("visited", "gate", "t1", "t2", "x"), form.questions.map { it.key })
  }

  @Test
  fun displayLogicOutsideTheRepeat_cantUseItsQuestions() {
    assertEquals(listOf("visited", "gate"), form.eligibleRelevanceSources("x").map { it.key })
    assertEquals(
      listOf("visited", "gate", "t1"),
      form.eligibleRelevanceSources("t2").map { it.key },
    )
  }

  @Test
  fun keyForPath_mapsRepeatEntriesToTheirQuestion() {
    val ui = vm.uiState.value
    assertEquals("/data/trees/t1", ui.pathOf("t1"))
    assertEquals("t2", ui.keyForPath("/data/trees[3]/t2"))
    assertEquals("x", ui.keyForPath("/data/x"))
    assertNull(ui.keyForPath("/data/t1"))
  }

  @Test
  fun flowEdges_skipTheWholeConditionalSection() {
    vm.enableSectionRelevance("trees")
    val edges = vm.uiState.value.flowEdges
    // gate (slot 2) can go straight to x (slot 5), past the section.
    assertTrue(edges.any { it.from == 2 && it.to == 5 })
  }
}
