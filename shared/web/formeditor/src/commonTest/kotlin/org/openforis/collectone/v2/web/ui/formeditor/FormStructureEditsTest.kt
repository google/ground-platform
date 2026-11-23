/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.web.ui.formeditor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.domain.model.editor.EditorRelevance
import org.openforis.collectone.v2.app.domain.model.editor.EditorSection
import org.openforis.collectone.v2.app.domain.model.editor.RelevanceOperator

class FormStructureEditsTest {
  private fun q(
    key: String,
    section: String = "",
    name: String = key,
    dependsOn: String? = null,
    parent: String = "",
  ) =
    EditorQuestion(
      key = key,
      name = name,
      type = EditorQuestionType.TEXT,
      label = key,
      sectionId = section,
      relevance = dependsOn?.let { EditorRelevance(it, RelevanceOperator.IS_ANSWERED) },
      parentQuestionKey = parent,
    )

  private fun form(vararg questions: EditorQuestion, sections: List<EditorSection> = emptyList()) =
    EditorForm("f", "F", questions.toList(), sections = sections)

  private fun EditorForm.keys() = questions.map { it.key }

  @Test
  fun keyAllocator_skipsTakenKeysAndNeverGoesBack() {
    val keys = QuestionKeyAllocator(nextId = 1)
    assertEquals("q2", keys.allocate { it == "q1" })
    // q1 is free now, but the counter has moved past it.
    assertEquals("q3", keys.allocate { false })
  }

  @Test
  fun uniqueName_keepsAFreeStemAndNumbersFromTwo() {
    assertEquals("plot", uniqueName("plot", setOf("other")))
    assertEquals("plot_2", uniqueName("plot", setOf("plot")))
    assertEquals("plot_4", uniqueName("plot", setOf("plot", "plot_2", "plot_3")))
  }

  @Test
  fun nameStem_dropsOneNumberedSuffix() {
    assertEquals("plot", nameStem("plot_2"))
    assertEquals("plot_2", nameStem("plot_2_3"))
    assertEquals("plot2", nameStem("plot2"))
  }

  @Test
  fun newSectionId_countsPastExistingIds() {
    assertEquals("section_1", form().newSectionId())
    val sections = listOf(EditorSection("section_2", "A"))
    // One section → tries section_2, which is taken.
    assertEquals("section_3", form(sections = sections).newSectionId())
  }

  @Test
  fun sectionIdForInsertAt_followsTheDisplacedOrPreviousQuestion() {
    val f = form(q("a", "s1"), q("b", "s2"))
    assertEquals("s2", f.sectionIdForInsertAt(1))
    assertEquals("s2", f.sectionIdForInsertAt(2))
    assertEquals("", f.sectionIdForInsertAt(5))
    assertEquals("", f.sectionIdForInsertAt(null))
  }

  @Test
  fun branchKeys_collectsLaterDependentsTransitively() {
    val f =
      form(
        q("a"),
        q("b", dependsOn = "a"),
        q("c"),
        q("d", dependsOn = "b"),
        q("e", parent = "a"),
        q("f", dependsOn = "c"),
      )
    assertEquals(listOf("a", "b", "d", "e"), f.branchKeys("a").toList())
    assertEquals(emptySet(), f.branchKeys("missing"))
  }

  @Test
  fun copiesOf_rewiresReferencesInsideTheCopiedSetOnly() {
    val f = form(q("q1", name = "site"), q("q2", name = "trees_2", dependsOn = "q1"), q("q3"))
    val outside = q("q4", name = "x", dependsOn = "q3", parent = "q3")
    val copies =
      f.copy(questions = f.questions + outside)
        .copiesOf(listOf(f.questions[0], f.questions[1], outside), QuestionKeyAllocator(1))
    assertEquals(listOf("q5", "q6", "q7"), copies.map { it.key })
    assertEquals(listOf("site_2", "trees", "x_2"), copies.map { it.name })
    assertEquals("q5", copies[1].relevance?.sourceQuestionKey)
    assertEquals("q3", copies[2].relevance?.sourceQuestionKey)
    assertEquals("q3", copies[2].parentQuestionKey)
  }

  @Test
  fun copiesOf_movesCopiesIntoTheGivenSection() {
    val f = form(q("a", "s1"), q("b", "s1"))
    val copies = f.copiesOf(f.questions, QuestionKeyAllocator(1), sectionId = "s2")
    assertTrue(copies.all { it.sectionId == "s2" })
  }

  @Test
  fun movingQuestion_joinsTheSectionOfTheNeighborItPassed() {
    val f = form(q("a", "s1"), q("b", "s1"), q("c", "s2"), q("d", "s2"))
    val down = f.movingQuestion(index = 1, delta = 1)
    assertEquals(listOf("a", "c", "b", "d"), down.keys())
    assertEquals("s2", down.questions[2].sectionId)
    val up = f.movingQuestion(index = 2, delta = -1)
    assertEquals(listOf("a", "c", "b", "d"), up.keys())
    assertEquals("s1", up.questions[1].sectionId)
  }

  @Test
  fun movingQuestionTo_clampsIntoTheTargetSectionRun() {
    val f = form(q("a", "s1"), q("b", "s1"), q("c"), q("d", "s2"), q("e", "s2"))
    // Asked for index 0 of a list whose s2 run is [2, 3]: lands at the start of the run.
    val moved = f.movingQuestionTo(fromIndex = 2, targetIndex = 0, targetSectionId = "s2")
    assertEquals(listOf("a", "b", "c", "d", "e"), moved.keys())
    assertEquals("s2", moved.questions[2].sectionId)
  }

  @Test
  fun movingQuestionTo_withoutSectionTakesThePreviousQuestionsSection() {
    val f = form(q("a", "s1"), q("b", "s1"), q("c"))
    val moved = f.movingQuestionTo(fromIndex = 2, targetIndex = 1, targetSectionId = null)
    assertEquals(listOf("a", "c", "b"), moved.keys())
    assertEquals("s1", moved.questions[1].sectionId)
  }

  @Test
  fun movingQuestionTo_dropOutsideSectionsSnapsToTheNearerEdgeOfASectionRun() {
    val f = form(q("x"), q("a", "s1"), q("b", "s1"), q("c", "s1"), q("d", "s1"))
    // Without x the s1 run is [0, 3]; index 2 is as near its start as its end, so the start wins.
    val nearStart = f.movingQuestionTo(fromIndex = 0, targetIndex = 2, targetSectionId = "")
    assertEquals(listOf("x", "a", "b", "c", "d"), nearStart.keys())
    val nearEnd = f.movingQuestionTo(fromIndex = 0, targetIndex = 3, targetSectionId = "")
    assertEquals(listOf("a", "b", "c", "d", "x"), nearEnd.keys())
    assertEquals("", nearEnd.questions[4].sectionId)
  }

  @Test
  fun retypingQuestion_resetsIncompatibleDependentOperators() {
    val number = q("a").copy(type = EditorQuestionType.NUMBER)
    val greater = q("b").copy(relevance = EditorRelevance("a", RelevanceOperator.GREATER_THAN, "3"))
    val equals = q("c").copy(relevance = EditorRelevance("a", RelevanceOperator.EQUALS, "3"))
    val retyped =
      form(number, greater, equals).retypingQuestion("a", EditorQuestionType.TEXT) {
        error("text has no choices")
      }
    assertEquals(EditorQuestionType.TEXT, retyped.questions[0].type)
    assertEquals(EditorRelevance("a", RelevanceOperator.EQUALS, ""), retyped.questions[1].relevance)
    // Still valid for text, so kept as it was.
    assertEquals(equals.relevance, retyped.questions[2].relevance)
  }

  @Test
  fun retypingQuestion_toNumberGetsDefaultDecimalPlacesAndToChoicesGetsDefaultChoices() {
    val numeric = form(q("a")).retypingQuestion("a", EditorQuestionType.NUMBER) { emptyList() }
    assertEquals(EditorQuestion.DEFAULT_DECIMAL_PLACES, numeric.questions[0].decimalPlaces)
    val choices =
      form(q("a")).retypingQuestion("a", EditorQuestionType.SELECT_ONE, ::defaultChoices)
    assertEquals(defaultChoices(), choices.questions[0].choices)
    val text = numeric.retypingQuestion("a", EditorQuestionType.TEXT) { emptyList() }
    assertEquals(0, text.questions[0].decimalPlaces)
  }

  @Test
  fun questionsWithout_dropsDisplayLogicOnRemovedQuestions() {
    val remaining = form(q("a"), q("b", dependsOn = "a"), q("c")).questionsWithout(setOf("a"))
    assertEquals(listOf("b", "c"), remaining.map { it.key })
    assertNull(remaining[0].relevance)
  }

  @Test
  fun withTemplateUpdate_renamesClashingAdditionsAndAppendsThemUnsectioned() {
    val f =
      form(q("q1", "s1", name = "site"), q("q2", name = "old", dependsOn = "q1"))
        .copy(
          templateOrigin =
            org.openforis.collectone.v2.app.domain.model.editor.TemplateOrigin("plot_check", 1)
        )
    val added = listOf(q("q1", "s9", name = "site_3"), q("", name = ""), q("fresh", name = "fresh"))
    val updated =
      f.withTemplateUpdate(
        addedQuestions = added,
        removedQuestionKeys = setOf("q2"),
        keys = QuestionKeyAllocator(3),
        templateId = "plot_check",
        templateVersion = 2,
      )
    assertEquals(listOf("q1", "q3", "q4", "fresh"), updated.keys())
    assertEquals(listOf("site", "site_2", "field", "fresh"), updated.questions.map { it.name })
    assertEquals(listOf("s1", "", "", ""), updated.questions.map { it.sectionId })
    assertEquals(2, updated.templateOrigin?.templateVersion)
  }

  @Test
  fun insertingSectionAndQuestion_preservesMiddleAndStartPositionsAndShiftsEmptyAnchors() {
    val base = form(q("q1"), q("q2"), q("q3"))
    val withMiddleSec =
      base.insertingSection(EditorSection("s_mid", "Middle"), atIndex = 1, selectedKey = "q1")
    assertEquals(1, withMiddleSec.sections.single().insertAtIndex)

    // Inserting an unsectioned question at index 0 shifts s_mid's anchor from 1 to 2.
    val withStartQ = withMiddleSec.insertingQuestion(q("q0"), insertAt = 0)
    assertEquals(listOf("q0", "q1", "q2", "q3"), withStartQ.keys())
    assertEquals(2, withStartQ.section("s_mid")?.insertAtIndex)

    // Adding the first question inside s_mid places it at index 2 (before q2) and clears anchor.
    val populated =
      withStartQ.insertingQuestion(
        q("q_in_sec", section = "s_mid"),
        insertAt = withStartQ.sectionAnchorIndex(withStartQ.sections.single()),
      )
    assertEquals(listOf("q0", "q1", "q_in_sec", "q2", "q3"), populated.keys())
    assertNull(populated.section("s_mid")?.insertAtIndex)

    // Removing the last question of s_mid keeps s_mid anchored at index 2 rather than jumping last.
    val emptied = populated.removingQuestion("q_in_sec")
    assertEquals(listOf("q0", "q1", "q2", "q3"), emptied.keys())
    assertEquals(2, emptied.section("s_mid")?.insertAtIndex)
  }

  @Test
  fun insertingAfterEmptySection_keepsEmptySectionBeforeNewItemsAndDuplicatesInPlace() {
    val base =
      form(q("q1"), q("q2"))
        .insertingSection(EditorSection("s1", "First"), atIndex = 1)
        .insertingSection(EditorSection("s2", "Second"), atIndex = 1, afterSectionId = "s1")
    assertEquals(listOf("s1", "s2"), base.sections.map { it.id })
    assertEquals(listOf(1, 1), base.sections.map { it.insertAtIndex })

    // Inserting unsectioned question right after s1 keeps s1 at 1 and shifts s2 to 2.
    val withBetween = base.insertingQuestion(q("q_mid"), insertAt = 1, afterSectionId = "s1")
    assertEquals(listOf("q1", "q_mid", "q2"), withBetween.keys())
    assertEquals(1, withBetween.section("s1")?.insertAtIndex)
    assertEquals(2, withBetween.section("s2")?.insertAtIndex)

    // Duplicating empty s1 places the copy immediately after s1 at anchor 1.
    val (duplicated, copyId) =
      requireNotNull(withBetween.duplicatingSection("s1", QuestionKeyAllocator(10)))
    assertEquals(listOf("s1", copyId, "s2"), duplicated.sections.map { it.id })
    assertEquals(1, duplicated.section(copyId)?.insertAtIndex)
  }
}
