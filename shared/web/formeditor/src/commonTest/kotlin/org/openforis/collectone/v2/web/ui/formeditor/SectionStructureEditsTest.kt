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
import kotlin.test.assertSame
import org.openforis.collectone.v2.app.domain.model.editor.EditorChoice
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.domain.model.editor.EditorRelevance
import org.openforis.collectone.v2.app.domain.model.editor.EditorSection
import org.openforis.collectone.v2.app.domain.model.editor.RelevanceOperator

/** Structural edits that keep sections, and their display logic, consistent. */
class SectionStructureEditsTest {
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

  private val gate = q("gate", type = EditorQuestionType.SELECT_ONE)

  /** gate, [a1, a2] in section a (shown if gate = yes), x, [b1] in section b. */
  private fun form() =
    EditorForm(
      formId = "f",
      title = "F",
      questions = listOf(gate, q("a1", "a"), q("a2", "a"), q("x"), q("b1", "b")),
      sections =
        listOf(
          EditorSection(
            "a",
            "A",
            relevance = EditorRelevance("gate", RelevanceOperator.EQUALS, "yes"),
          ),
          EditorSection("b", "B", repeatable = true),
        ),
    )

  private fun EditorForm.keys() = questions.map { it.key }

  private fun EditorForm.sectionOfKey(key: String) = find(key)!!.sectionId

  @Test
  fun withoutQuestions_clearsSectionDisplayLogicThatTestedThem() {
    val edited = form().withoutQuestions(setOf("gate"))
    assertEquals(listOf("a1", "a2", "x", "b1"), edited.keys())
    assertNull(edited.section("a")!!.relevance)
  }

  @Test
  fun retypingQuestion_adaptsSectionDisplayLogicOperator() {
    val edited = form().retypingQuestion("gate", EditorQuestionType.PHOTO) { emptyList() }
    assertEquals(
      EditorRelevance("gate", RelevanceOperator.IS_ANSWERED, ""),
      edited.section("a")!!.relevance,
    )
  }

  @Test
  fun withChoiceValueRenamed_updatesSectionDisplayLogic() {
    val edited = form().withChoiceValueRenamed("gate", "yes", "si")
    assertEquals("si", edited.section("a")!!.relevance!!.value)
    // Other values are untouched.
    assertEquals(
      "yes",
      form().withChoiceValueRenamed("gate", "no", "nah").section("a")!!.relevance!!.value,
    )
  }

  @Test
  fun movingSection_movesAllItsQuestionsPastOneBlock() {
    assertEquals(listOf("gate", "x", "a1", "a2", "b1"), form().movingSection("a", 1).keys())
    assertEquals(listOf("a1", "a2", "gate", "x", "b1"), form().movingSection("a", -1).keys())
    // Moving past another section skips all of it.
    val bUp = form().movingSection("b", -1).movingSection("b", -1)
    assertEquals(listOf("gate", "b1", "a1", "a2", "x"), bUp.keys())
    assertEquals("a", bUp.sectionOfKey("a2"))
  }

  @Test
  fun movingSection_atTheEdgeOrEmpty_isUnchanged() {
    val form = form()
    assertSame(form, form.movingSection("b", 1))
    val withEmpty = form.copy(sections = form.sections + EditorSection("c", "C"))
    assertSame(withEmpty, withEmpty.movingSection("c", -1))
    assertSame(form, form.movingSection("missing", 1))
  }

  @Test
  fun assigningSection_placesTheQuestionNextToTheSectionsQuestions() {
    val joinedA = form().assigningSection("x", "a")
    assertEquals(listOf("gate", "a1", "a2", "x", "b1"), joinedA.keys())
    assertEquals("a", joinedA.sectionOfKey("x"))
    // Joining a section from before it lands at its start.
    val gateIntoB = form().assigningSection("gate", "b")
    assertEquals(listOf("a1", "a2", "x", "gate", "b1"), gateIntoB.keys())
    assertEquals("b", gateIntoB.sectionOfKey("gate"))
  }

  @Test
  fun assigningSection_leavingASection_stepsOutOfIt() {
    val edited = form().assigningSection("a1", "")
    assertEquals("", edited.sectionOfKey("a1"))
    // a1 was at the section's edge, so the section stays contiguous.
    assertEquals(listOf("gate", "a1", "a2", "x", "b1"), edited.keys())
  }

  @Test
  fun assigningSection_emptySection_keepsThePosition() {
    val form = form().let { it.copy(sections = it.sections + EditorSection("c", "C")) }
    val edited = form.assigningSection("x", "c")
    assertEquals(form.keys(), edited.keys())
    assertEquals("c", edited.sectionOfKey("x"))
  }

  @Test
  fun assigningSection_unknownOrSameSection_isUnchanged() {
    val form = form()
    assertSame(form, form.assigningSection("x", "missing"))
    assertSame(form, form.assigningSection("a1", "a"))
  }

  @Test
  fun withTemplateUpdate_removedQuestionClearsSectionDisplayLogic() {
    val edited =
      form().withTemplateUpdate(emptyList(), setOf("gate"), QuestionKeyAllocator(nextId = 10))
    assertNull(edited.section("a")!!.relevance)
  }
}
