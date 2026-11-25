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

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.FlowEdge
import org.openforis.collectone.v2.app.domain.model.editor.FormFlowGraph

/** Canvas bounding box of a section's visual grouping frame in Dp. */
data class SectionFrameBounds(
  val minX: Dp,
  val minY: Dp,
  val maxX: Dp,
  val maxY: Dp,
) {
  val width: Dp
    get() = maxX - minX

  val height: Dp
    get() = maxY - minY
}

internal val CardWidth = 184.dp

internal val CardHeight = 312.dp

internal val StartButtonWidth = 148.dp

internal val StartButtonHeight = 48.dp

internal val TerminalWidth = 104.dp

internal val TerminalHeight = 56.dp

internal val SlotGap = 72.dp

private val CanvasPadding = 32.dp

internal val ArcInset = 22.dp

internal fun arcRise(span: Int): Dp = 36.dp + 24.dp * (span - 2).coerceIn(0, 6)

// ---------------------------------------------------------------------------------------------
// Flow canvas
// ---------------------------------------------------------------------------------------------

/**
 * Precomputed 2D Dp geometry for every flow slot (Start, questions, in-section and end Ghost cards,
 * and End) on the Form editor's horizontal DAG canvas.
 *
 * Layout invariants:
 * - Slot `0` (Start) sits at `(col = 0, row = 0)`. Unconditional trunk questions advance on `row =
 *   0`.
 * - Conditional questions (`question.relevance ?: section.relevance != null`) branch onto rows `>=
 *   1` keyed by `(sourceQuestionKey, operator, value)`: sibling questions testing the exact same
 *   condition share a horizontal branch row in consecutive columns, whereas distinct conditions
 *   stack on separate rows.
 * - `trunkCol` advances past every branch column and section ghost slot so subsequent unconditional
 *   questions return to `row = 0` to the right of earlier branches without horizontal overlap.
 * - [cardTop] reserves vertical headroom above `row = 0` for the tallest forward skip-arc (`span >
 *   1`).
 */
class FlowLayout(
  val questionCount: Int,
  val edges: List<FlowEdge>,
  val form: EditorForm? = null,
  val includeGhostSlot: Boolean = true,
) {
  val slotCount = questionCount + 2
  val endSlot = questionCount + 1
  private val maxRise: Dp = edges.filter { it.span > 1 }.maxOfOrNull { arcRise(it.span) } ?: 0.dp
  val cardTop: Dp = CanvasPadding + maxRise + 8.dp
  val midY: Dp = cardTop + CardHeight / 2

  fun width(slot: Int): Dp =
    when (slot) {
      0 -> StartButtonWidth
      endSlot -> TerminalWidth
      else -> CardWidth
    }

  fun height(slot: Int): Dp =
    when (slot) {
      0 -> StartButtonHeight
      endSlot -> TerminalHeight
      else -> CardHeight
    }

  private val slotCol = mutableMapOf<Int, Int>()
  private val slotRow = mutableMapOf<Int, Int>()
  private val sectionGhostCol = mutableMapOf<String, Int>()
  private val sectionGhostRow = mutableMapOf<String, Int>()

  val ghostCol: Int
  val endCol: Int
  val maxRow: Int

  init {
    slotCol[0] = 0
    slotRow[0] = 0

    var trunkCol = 0
    val branchRows = mutableMapOf<String, Int>()
    var nextBranchRow = 1
    val lastColInRow = mutableMapOf<Int, Int>()
    val populatedSectionIds =
      form?.questions?.mapNotNull { q -> q.sectionId.takeIf { it.isNotBlank() } }?.toSet().orEmpty()
    val emptySectionsByIndex =
      if (includeGhostSlot && form != null) {
        form.sections
          .filter { it.id !in populatedSectionIds }
          .groupBy { (it.insertAtIndex ?: questionCount).coerceIn(0, questionCount) }
      } else {
        emptyMap()
      }

    fun placeEmptySectionsAt(index: Int) {
      emptySectionsByIndex[index]?.forEach { sec ->
        trunkCol++
        sectionGhostCol[sec.id] = trunkCol
        sectionGhostRow[sec.id] = 0
        lastColInRow[0] = trunkCol
      }
    }

    for (qIdx in 0 until questionCount) {
      placeEmptySectionsAt(qIdx)
      val slot = qIdx + 1
      val question = form?.questions?.getOrNull(qIdx)
      val sectionId = question?.sectionId.orEmpty()
      val section = question?.let { form.section(it.sectionId) }
      val relevance = question?.relevance ?: section?.relevance

      if (relevance == null || form == null) {
        // Main trunk: row 0, advance column
        trunkCol++
        slotCol[slot] = trunkCol
        slotRow[slot] = 0
        lastColInRow[0] = trunkCol
      } else {
        // Conditional branch!
        val sourceKey = relevance.sourceQuestionKey
        val sourceIndex = form.indexOf(sourceKey)
        val parentSlot = if (sourceIndex >= 0) FormFlowGraph.questionSlot(sourceIndex) else 0
        val parentCol = slotCol[parentSlot] ?: 0

        val branchKey = "${sourceKey}_${relevance.operator}_${relevance.value}"
        val branchRow = branchRows.getOrPut(branchKey) { nextBranchRow++ }
        val prevColOnRow = lastColInRow[branchRow] ?: parentCol
        val col = maxOf(parentCol + 1, prevColOnRow + 1)
        slotCol[slot] = col
        slotRow[slot] = branchRow
        lastColInRow[branchRow] = col
        trunkCol = maxOf(trunkCol, col)
      }

      if (includeGhostSlot && sectionId.isNotBlank()) {
        val nextQ = form?.questions?.getOrNull(qIdx + 1)
        if (nextQ == null || nextQ.sectionId != sectionId) {
          val currentRow = slotRow[slot] ?: 0
          if (currentRow == 0) {
            trunkCol++
            sectionGhostCol[sectionId] = trunkCol
            sectionGhostRow[sectionId] = 0
            lastColInRow[0] = trunkCol
          } else {
            val prevCol = lastColInRow[currentRow] ?: trunkCol
            val gCol = prevCol + 1
            sectionGhostCol[sectionId] = gCol
            sectionGhostRow[sectionId] = currentRow
            lastColInRow[currentRow] = gCol
            trunkCol = maxOf(trunkCol, gCol)
          }
        }
      }
    }

    placeEmptySectionsAt(questionCount)

    val maxQuestionsCol =
      maxOf(
        slotCol.values.maxOrNull() ?: trunkCol,
        sectionGhostCol.values.maxOrNull() ?: trunkCol,
      )
    ghostCol = maxOf(trunkCol + 1, maxQuestionsCol + 1)
    endCol = if (includeGhostSlot) ghostCol + 1 else ghostCol

    slotCol[endSlot] = endCol
    slotRow[endSlot] = 0

    maxRow =
      maxOf(
        slotRow.values.maxOrNull() ?: 0,
        sectionGhostRow.values.maxOrNull() ?: 0,
      )
  }

  fun hasSectionGhost(sectionId: String): Boolean = sectionGhostCol.containsKey(sectionId)

  fun sectionGhostX(sectionId: String): Dp = colX(sectionGhostCol[sectionId] ?: 0)

  fun sectionGhostY(sectionId: String): Dp = rowY(sectionGhostRow[sectionId] ?: 0)

  fun slotCol(slot: Int): Int = slotCol[slot] ?: 0

  fun slotRow(slot: Int): Int = slotRow[slot] ?: 0

  private fun colX(col: Int): Dp =
    if (col == 0) {
      CanvasPadding
    } else {
      CanvasPadding + StartButtonWidth + SlotGap + (CardWidth + SlotGap) * (col - 1)
    }

  val BranchGap: Dp = 48.dp

  private fun rowY(row: Int): Dp = cardTop + (CardHeight + BranchGap) * row

  fun slotX(slot: Int): Dp = colX(slotCol(slot))

  fun slotY(slot: Int): Dp = rowY(slotRow(slot)) + (CardHeight - height(slot)) / 2

  fun x(slot: Int): Dp = slotX(slot)

  fun top(slot: Int): Dp = slotY(slot)

  fun slotPosition(slot: Int): DpOffset = DpOffset(slotX(slot), slotY(slot))

  val ghostX: Dp = colX(ghostCol)
  val ghostY: Dp = rowY(0)

  val totalWidth: Dp = colX(endCol) + TerminalWidth + CanvasPadding
  val totalHeight: Dp = rowY(maxRow) + CardHeight + CanvasPadding + 32.dp

  /**
   * Bounding box of [sectionId]'s visual grouping frame around its questions and (when editable)
   * its in-section ghost card, or `null` when the section has neither questions nor a ghost slot.
   */
  fun sectionFrameBounds(sectionId: String): SectionFrameBounds? {
    val currentForm = form ?: return null
    val sectionQuestions = currentForm.questions.filter { it.sectionId == sectionId }
    val slots = sectionQuestions.map { FormFlowGraph.questionSlot(currentForm.indexOf(it.key)) }
    val hasGhost = hasSectionGhost(sectionId)
    if (sectionQuestions.isEmpty() && !hasGhost) return null
    val minX =
      minOf(
        slots.minOfOrNull { slotX(it) } ?: (if (hasGhost) sectionGhostX(sectionId) else totalWidth),
        if (hasGhost) sectionGhostX(sectionId) else totalWidth,
      ) - 16.dp
    val minY =
      minOf(
        slots.minOfOrNull { slotY(it) }
          ?: (if (hasGhost) sectionGhostY(sectionId) else totalHeight),
        if (hasGhost) sectionGhostY(sectionId) else totalHeight,
      ) - 40.dp
    val maxX =
      maxOf(
        slots.maxOfOrNull { slotX(it) + width(it) } ?: 0.dp,
        if (hasGhost) sectionGhostX(sectionId) + CardWidth else 0.dp,
      ) + 16.dp
    val maxY =
      maxOf(
        slots.maxOfOrNull { slotY(it) + height(it) } ?: 0.dp,
        if (hasGhost) sectionGhostY(sectionId) + CardHeight else 0.dp,
      ) + 16.dp
    return SectionFrameBounds(minX = minX, minY = minY, maxX = maxX, maxY = maxY)
  }

  /**
   * Section that a dragged question card would land in at [targetIndex] with horizontal center
   * [draggedCenterX]: prefers the section frame spanning [draggedCenterX], falling back to the
   * section of the question currently at [targetIndex].
   */
  fun hoveredSectionId(targetIndex: Int, draggedCenterX: Dp): String? {
    val currentForm = form ?: return null
    val targetQ = currentForm.questions.getOrNull(targetIndex)
    val sectionAtTarget = targetQ?.sectionId?.takeIf { it.isNotEmpty() }
    val sectionByFrame =
      currentForm.sections
        .firstOrNull { section ->
          val bounds = sectionFrameBounds(section.id) ?: return@firstOrNull false
          draggedCenterX in bounds.minX..bounds.maxX
        }
        ?.id
    return sectionByFrame ?: sectionAtTarget
  }
}
