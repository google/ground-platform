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

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.domain.model.editor.EditorChoice
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.domain.model.editor.EditorRelevance
import org.openforis.collectone.v2.app.domain.model.editor.EditorSection
import org.openforis.collectone.v2.app.domain.model.editor.FormFlowGraph
import org.openforis.collectone.v2.app.domain.model.editor.RelevanceOperator

class FlowLayoutTest {

  private fun choiceQuestion(key: String, sectionId: String = "") =
    EditorQuestion(
      key = key,
      name = key,
      type = EditorQuestionType.SELECT_ONE,
      label = key,
      sectionId = sectionId,
      choices = listOf(EditorChoice("yes", "Yes"), EditorChoice("no", "No")),
    )

  private fun textQuestion(
    key: String,
    sectionId: String = "",
    relevance: EditorRelevance? = null,
  ) =
    EditorQuestion(
      key = key,
      name = key,
      type = EditorQuestionType.TEXT,
      label = key,
      sectionId = sectionId,
      relevance = relevance,
    )

  @Test
  fun linearTrunk_placesSlotsInMonotonicColumnsOnRowZero() {
    val form = EditorForm("f", "Form", listOf(textQuestion("q1"), textQuestion("q2")))
    val layout =
      FlowLayout(form.questions.size, FormFlowGraph.edges(form), form, includeGhostSlot = true)

    assertEquals(0, layout.slotRow(0))
    assertEquals(0, layout.slotRow(1))
    assertEquals(0, layout.slotRow(2))
    assertEquals(0, layout.slotRow(layout.endSlot))
    assertEquals(0, layout.maxRow)
    assertTrue(layout.slotX(0) < layout.slotX(1))
    assertTrue(layout.slotX(1) < layout.slotX(2))
    assertTrue(layout.slotX(2) < layout.ghostX)
    assertTrue(layout.ghostX < layout.slotX(layout.endSlot))
  }

  @Test
  fun conditionalBranches_shareRowForSameConditionAndSeparateDistinctConditions() {
    val q1 = choiceQuestion("q1")
    val yesA =
      textQuestion("q2", relevance = EditorRelevance("q1", RelevanceOperator.EQUALS, "yes"))
    val yesB =
      textQuestion("q3", relevance = EditorRelevance("q1", RelevanceOperator.EQUALS, "yes"))
    val noA = textQuestion("q4", relevance = EditorRelevance("q1", RelevanceOperator.EQUALS, "no"))
    val trunkEnd = textQuestion("q5")
    val form = EditorForm("f", "Form", listOf(q1, yesA, yesB, noA, trunkEnd))
    val layout = FlowLayout(form.questions.size, FormFlowGraph.edges(form), form)

    // Same condition ("q1 == yes") shares row 1 in consecutive columns; "q1 == no" gets row 2.
    assertEquals(1, layout.slotRow(2))
    assertEquals(1, layout.slotRow(3))
    assertTrue(layout.slotCol(3) > layout.slotCol(2))
    assertEquals(2, layout.slotRow(4))
    // Following unconditional question returns to row 0 past the branched columns.
    assertEquals(0, layout.slotRow(5))
    assertTrue(layout.slotCol(5) > layout.slotCol(3))
    assertEquals(2, layout.maxRow)
  }

  @Test
  fun sectionGhostAndReadOnlyMode_controlSectionFramesAndEndColumn() {
    val s1 = EditorSection("s1", "Site")
    val sEmpty = EditorSection("s2", "Empty")
    val form =
      EditorForm(
        formId = "f",
        title = "Form",
        questions = listOf(textQuestion("q1", sectionId = "s1")),
        sections = listOf(s1, sEmpty),
      )
    val edges = FormFlowGraph.edges(form)

    val editable = FlowLayout(form.questions.size, edges, form, includeGhostSlot = true)
    assertTrue(editable.hasSectionGhost("s1"))
    assertTrue(editable.hasSectionGhost("s2"))
    assertEquals(editable.ghostCol + 1, editable.endCol)
    val editableS1Bounds = assertNotNull(editable.sectionFrameBounds("s1"))
    assertTrue(editableS1Bounds.width > CardWidth)
    assertNotNull(editable.sectionFrameBounds("s2"))

    val readOnly = FlowLayout(form.questions.size, edges, form, includeGhostSlot = false)
    assertFalse(readOnly.hasSectionGhost("s1"))
    assertFalse(readOnly.hasSectionGhost("s2"))
    assertEquals(readOnly.ghostCol, readOnly.endCol)
    val readOnlyS1Bounds = assertNotNull(readOnly.sectionFrameBounds("s1"))
    assertEquals(CardWidth + 32.dp, readOnlyS1Bounds.width)
    // Empty section in read-only mode has neither questions nor ghost card → no frame.
    assertNull(readOnly.sectionFrameBounds("s2"))
  }

  @Test
  fun hoveredSectionId_prefersFrameBoundsAndFallsBackToTargetQuestionSection() {
    val s1 = EditorSection("s1", "First")
    val s2 = EditorSection("s2", "Second")
    val form =
      EditorForm(
        formId = "f",
        title = "Form",
        questions =
          listOf(textQuestion("q1", sectionId = "s1"), textQuestion("q2", sectionId = "s2")),
        sections = listOf(s1, s2),
      )
    val layout = FlowLayout(form.questions.size, FormFlowGraph.edges(form), form)
    val s2Bounds = assertNotNull(layout.sectionFrameBounds("s2"))
    val centerInsideS2 = (s2Bounds.minX + s2Bounds.maxX) / 2

    // Even if targetIndex points at q1 (in s1), a card hovered inside s2's frame resolves to s2.
    assertEquals("s2", layout.hoveredSectionId(targetIndex = 0, draggedCenterX = centerInsideS2))
    // Outside every section frame, hoveredSectionId falls back to the question at targetIndex.
    assertEquals("s1", layout.hoveredSectionId(targetIndex = 0, draggedCenterX = 0.dp))
    assertNull(
      FlowLayout(0, emptyList(), form = null)
        .hoveredSectionId(targetIndex = 0, draggedCenterX = 0.dp)
    )
  }

  @Test
  fun emptySectionsWithInsertAtIndex_renderAtStartMiddleAndEndColumns() {
    val sStart = EditorSection("s_start", "Start Section", insertAtIndex = 0)
    val sMid = EditorSection("s_mid", "Middle Section", insertAtIndex = 1)
    val sEnd = EditorSection("s_end", "End Section", insertAtIndex = 2)
    val form =
      EditorForm(
        formId = "f",
        title = "Form",
        questions = listOf(textQuestion("q1"), textQuestion("q2")),
        sections = listOf(sStart, sMid, sEnd),
      )
    val layout =
      FlowLayout(form.questions.size, FormFlowGraph.edges(form), form, includeGhostSlot = true)

    assertTrue(layout.slotX(0) < layout.sectionGhostX("s_start"))
    assertTrue(layout.sectionGhostX("s_start") < layout.slotX(1))
    assertTrue(layout.slotX(1) < layout.sectionGhostX("s_mid"))
    assertTrue(layout.sectionGhostX("s_mid") < layout.slotX(2))
    assertTrue(layout.slotX(2) < layout.sectionGhostX("s_end"))
    assertTrue(layout.sectionGhostX("s_end") < layout.ghostX)
  }
}
