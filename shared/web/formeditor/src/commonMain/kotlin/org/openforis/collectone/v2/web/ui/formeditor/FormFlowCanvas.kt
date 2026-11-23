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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.domain.model.editor.FlowEdge
import org.openforis.collectone.v2.app.domain.model.editor.FlowEdgeKind
import org.openforis.collectone.v2.app.domain.model.editor.FormFlowGraph
import org.openforis.collectone.v2.app.domain.model.editor.SaveToRules
import org.openforis.collectone.v2.app.ui.common.DragAxis
import org.openforis.collectone.v2.app.ui.common.awaitDragPastSlop
import org.openforis.collectone.v2.app.ui.common.dragSlopFor
import org.openforis.collectone.v2.app.ui.text.labelRes
import org.openforis.collectone.v2.core.forms.ui.BadgeTone
import org.openforis.collectone.v2.core.forms.ui.LocalInteractivePointerIcon
import org.openforis.collectone.v2.core.forms.ui.TonalBadge
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.backdropClickable
import org.openforis.collectone.v2.core.forms.ui.controls.clickable
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.formeditor_add_a_question
import org.openforis.collectone.v2.web.resources.formeditor_add_question_at_end_of_flow
import org.openforis.collectone.v2.web.resources.formeditor_add_question_here
import org.openforis.collectone.v2.web.resources.formeditor_add_question_inside_named_section
import org.openforis.collectone.v2.web.resources.formeditor_add_question_inside_section
import org.openforis.collectone.v2.web.resources.formeditor_add_to_named_section
import org.openforis.collectone.v2.web.resources.formeditor_add_to_section
import org.openforis.collectone.v2.web.resources.formeditor_append_question_in_section
import org.openforis.collectone.v2.web.resources.formeditor_condition_pill
import org.openforis.collectone.v2.web.resources.formeditor_conditional
import org.openforis.collectone.v2.web.resources.formeditor_ghost_card_subtitle
import org.openforis.collectone.v2.web.resources.formeditor_repeat_count_expression
import org.openforis.collectone.v2.web.resources.formeditor_repeated_section_badge
import org.openforis.collectone.v2.web.resources.formeditor_repeated_section_badge_untitled
import org.openforis.collectone.v2.web.resources.formeditor_review_and_submit
import org.openforis.collectone.v2.web.resources.formeditor_section_badge
import org.openforis.collectone.v2.web.resources.formeditor_section_badge_untitled
import org.openforis.collectone.v2.web.resources.formeditor_start_node_description
import org.openforis.collectone.v2.web.resources.formeditor_untitled_form
import org.openforis.collectone.v2.web.ui.common.GrabPointerIcon
import org.openforis.collectone.v2.web.ui.common.GrabbingPointerIcon
import org.openforis.collectone.v2.web.ui.common.showPlatformGrabCursor
import org.openforis.collectone.v2.web.ui.common.showPlatformGrabbingCursor

@Composable
internal fun FlowCanvasPanel(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  modifier: Modifier = Modifier,
) {
  val edges = uiState.flowEdges
  val horizontalScroll = rememberScrollState()
  Column(modifier = modifier.fillMaxSize()) {
    PlatformDisabledBanner(uiState, actions)
    UnavailablePreviewArea(uiState, Modifier.weight(1f).fillMaxWidth()) {
      FlowCanvas(uiState, actions, edges, horizontalScroll, Modifier.fillMaxSize())
    }
    FlowHorizontalScrollBar(
      scrollState = horizontalScroll,
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
  }
}

@Composable
private fun FlowCanvas(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  edges: List<FlowEdge>,
  horizontalScroll: androidx.compose.foundation.ScrollState,
  modifier: Modifier,
) {
  val form = uiState.form
  val layout =
    remember(form.questions, form.sections, edges, uiState.isReadOnly) {
      FlowLayout(form.questions.size, edges, form, includeGhostSlot = !uiState.isReadOnly)
    }
  val density = LocalDensity.current
  val selectedSlot =
    if (uiState.selectedIndex >= 0) FormFlowGraph.questionSlot(uiState.selectedIndex) else -1

  // Keep the selected screen in view (e.g. after adding or reordering). This jumps rather than
  // animating: while an animated scroll is in progress the scroll container consumes the next
  // pointer down to stop it, which swallowed the user's next click on a screen card.
  LaunchedEffect(selectedSlot, layout.slotCount) {
    if (selectedSlot >= 0) {
      val left = with(density) { (layout.x(selectedSlot) - SlotGap).roundToPx() }
      val right = with(density) { (layout.x(selectedSlot) + CardWidth + SlotGap).roundToPx() }
      val viewportEnd = horizontalScroll.value + horizontalScroll.viewportSize
      when {
        left < horizontalScroll.value -> horizontalScroll.scrollTo(left.coerceAtLeast(0))
        right > viewportEnd && horizontalScroll.viewportSize > 0 ->
          horizontalScroll.scrollTo(right - horizontalScroll.viewportSize)
      }
    }
  }

  val colors = MaterialTheme.colorScheme
  val drag = remember { DragReorderState() }
  var isPanning by remember { mutableStateOf(false) }
  var isCanvasHovered by remember { mutableStateOf(false) }
  val isGrabbing = isPanning || drag.isDragging
  val showGrab = isCanvasHovered && uiState.isEnabledOnPreviewTarget
  DisposableEffect(showGrab) {
    if (showGrab) showPlatformGrabCursor(true)
    onDispose { if (showGrab) showPlatformGrabCursor(false) }
  }
  DisposableEffect(isGrabbing) {
    if (isGrabbing) showPlatformGrabbingCursor(true)
    onDispose { if (isGrabbing) showPlatformGrabbingCursor(false) }
  }
  val pitchPx = with(density) { (CardWidth + SlotGap).toPx() }
  val cardWidthPx = with(density) { CardWidth.toPx() }
  val edgeZonePx = with(density) { 56.dp.toPx() }
  val edgeAlpha by animateFloatAsState(if (drag.isDragging) 0.15f else 1f)
  val verticalScroll = rememberScrollState()

  val draggingSlot =
    remember(drag.draggingKey, form) {
      val idx = form.indexOf(drag.draggingKey ?: "")
      if (idx >= 0) FormFlowGraph.questionSlot(idx) else -1
    }
  val draggingSlotLeftPx =
    if (draggingSlot >= 0) with(density) { layout.x(draggingSlot).toPx() } else 0f
  val draggedCenterXDp =
    with(density) { (draggingSlotLeftPx + drag.offset + cardWidthPx / 2).toDp() }

  val hoveredSectionId: String? =
    if (drag.isDragging) layout.hoveredSectionId(drag.targetIndex, draggedCenterXDp) else null
  androidx.compose.runtime.CompositionLocalProvider(
    LocalDisabledQuestionTypes provides uiState.disabledQuestionTypes
  ) {
    Box(
      modifier =
        modifier
          .pointerHoverIcon(
            icon = if (isGrabbing) GrabbingPointerIcon else GrabPointerIcon,
            overrideDescendants = isGrabbing,
          )
          .pointerInput(Unit) {
            awaitPointerEventScope {
              while (true) {
                val event = awaitPointerEvent()
                when (event.type) {
                  PointerEventType.Enter -> isCanvasHovered = true
                  PointerEventType.Exit -> isCanvasHovered = false
                }
              }
            }
          }
          .horizontalScroll(horizontalScroll)
          .verticalScroll(verticalScroll)
          .pointerInput(drag.isDragging) {
            // Pans the canvas. Like screen reordering, a pan only starts past [MouseDragSlop]:
            // Compose's default mouse slop (0.125 dp) turned slightly-moving clicks on screen cards
            // into pans that cancelled the click.
            if (!drag.isDragging) {
              awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val (start, travel) =
                  awaitDragPastSlop(down, dragSlopFor(down), DragAxis.HORIZONTAL)
                    ?: return@awaitEachGesture
                isPanning = true
                try {
                  horizontalScroll.dispatchRawDelta(-travel.x)
                  verticalScroll.dispatchRawDelta(-travel.y)
                  horizontalDrag(start.id) { change ->
                    val delta = change.positionChange()
                    horizontalScroll.dispatchRawDelta(-delta.x)
                    verticalScroll.dispatchRawDelta(-delta.y)
                    change.consume()
                  }
                } finally {
                  isPanning = false
                }
              }
            }
          }
    ) {
      // Clicking empty canvas selects the Form itself, showing Form properties.
      Box(
        modifier =
          Modifier.width(layout.totalWidth)
            .height(layout.totalHeight)
            .backdropClickable(
              indication = null,
              interactionSource = remember { MutableInteractionSource() },
              onClick = actions::selectForm,
            )
      ) {
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = edgeAlpha }) {
          edges.forEach { edge ->
            val highlighted =
              selectedSlot >= 0 && (edge.from == selectedSlot || edge.to == selectedSlot)
            val baseColor =
              when (edge.kind) {
                FlowEdgeKind.NEXT -> colors.outline
                FlowEdgeKind.CONDITIONAL -> colors.primary
                FlowEdgeKind.SKIP -> colors.tertiary
              }
            val color =
              if (selectedSlot >= 0 && !highlighted) baseColor.copy(alpha = 0.45f) else baseColor
            drawFlowEdge(edge, layout, color, strokeWidthDp = if (highlighted) 3.dp else 2.dp)
          }
        }

        // Section visual grouping frames
        form.sections.forEach { section ->
          val bounds = layout.sectionFrameBounds(section.id)
          val isSectionHovered = hoveredSectionId == section.id
          val isSectionSelected = uiState.selectedSectionId == section.id
          val hasSectionIssues = uiState.issuesForSection(section.id).isNotEmpty()

          if (bounds != null) {
            Surface(
              onClick = { actions.selectSection(section.id) },
              modifier =
                Modifier.offset(bounds.minX, bounds.minY).width(bounds.width).height(bounds.height),
              shape = RoundedCornerShape(20.dp),
              color =
                if (isSectionHovered) colors.primaryContainer.copy(alpha = 0.28f)
                else if (isSectionSelected) colors.primaryContainer.copy(alpha = 0.12f)
                else colors.surfaceContainerLow.copy(alpha = 0.45f),
              border =
                BorderStroke(
                  if (isSectionHovered || isSectionSelected) 2.5.dp else 1.5.dp,
                  if (isSectionHovered) colors.primary
                  else if (isSectionSelected) colors.primary
                  else if (hasSectionIssues) colors.error
                  else if (section.repeatable) colors.primary.copy(alpha = 0.6f)
                  else colors.outlineVariant.copy(alpha = 0.8f),
                ),
            ) {
              Box(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  modifier = Modifier.align(Alignment.TopStart),
                ) {
                  TonalBadge(
                    text =
                      when {
                        section.repeatable && section.title.isBlank() ->
                          stringResource(Res.string.formeditor_repeated_section_badge_untitled)
                        section.repeatable ->
                          stringResource(
                            Res.string.formeditor_repeated_section_badge,
                            section.title,
                          )
                        section.title.isBlank() ->
                          stringResource(Res.string.formeditor_section_badge_untitled)
                        else -> stringResource(Res.string.formeditor_section_badge, section.title)
                      },
                    tone = if (section.repeatable) BadgeTone.PRIMARY else BadgeTone.SECONDARY,
                    modifier =
                      Modifier.clip(CircleShape).clickable { actions.selectSection(section.id) },
                  )
                  if (section.relevance != null) {
                    TonalBadge(
                      text = stringResource(Res.string.formeditor_conditional),
                      tone = BadgeTone.TERTIARY,
                    )
                  }
                  if (section.repeatable && section.repeatCountExpression.isNotBlank()) {
                    Text(
                      text =
                        stringResource(
                          Res.string.formeditor_repeat_count_expression,
                          section.repeatCountExpression,
                        ),
                      style = MaterialTheme.typography.labelSmall,
                      color = colors.onSurfaceVariant,
                    )
                  }
                }
              }
            }

            if (layout.hasSectionGhost(section.id)) {
              val lastSectionIdx = form.questions.indexOfLast { it.sectionId == section.id }
              val sectionInsertIndex =
                if (lastSectionIdx >= 0) lastSectionIdx + 1 else form.sectionAnchorIndex(section)
              GhostQuestionCard(
                atIndex = sectionInsertIndex,
                onAdd = { type, idx -> actions.addQuestion(type, idx, sectionId = section.id) },
                title =
                  if (section.title.isBlank()) stringResource(Res.string.formeditor_add_to_section)
                  else stringResource(Res.string.formeditor_add_to_named_section, section.title),
                subtitle = stringResource(Res.string.formeditor_append_question_in_section),
                contentDescription =
                  if (section.title.isBlank()) {
                    stringResource(Res.string.formeditor_add_question_inside_section)
                  } else {
                    stringResource(
                      Res.string.formeditor_add_question_inside_named_section,
                      section.title,
                    )
                  },
                modifier =
                  Modifier.offset(
                    layout.sectionGhostX(section.id),
                    layout.sectionGhostY(section.id),
                  ),
              )
              if (!uiState.isReadOnly) {
                AddQuestionAffordance(
                  centerX = layout.sectionGhostX(section.id) + CardWidth + SlotGap / 2,
                  centerY = layout.sectionGhostY(section.id) + CardHeight / 2,
                  atIndex = sectionInsertIndex,
                  onAdd = { type, idx ->
                    actions.addQuestion(
                      type,
                      idx,
                      sectionId = "",
                      afterSectionId = section.id,
                    )
                  },
                  onAddSection = { idx ->
                    val secId =
                      actions.addSection(
                        "Section",
                        repeatable = false,
                        atIndex = idx,
                        afterSectionId = section.id,
                      )
                    actions.selectSection(secId)
                  },
                  onAddRepeatedSection = { idx ->
                    val secId =
                      actions.addSection(
                        "Section",
                        repeatable = true,
                        atIndex = idx,
                        afterSectionId = section.id,
                      )
                    actions.selectSection(secId)
                  },
                  alwaysVisible = false,
                )
              }
            }
          }
        }

        // Condition pills on conditional flow edges
        edges
          .filter { it.kind == FlowEdgeKind.CONDITIONAL }
          .forEach { edge ->
            val targetQ = form.questions.getOrNull(edge.to - 1)
            val section = targetQ?.let { form.section(it.sectionId) }
            val relevance = targetQ?.relevance ?: section?.relevance
            if (relevance != null) {
              val pillText =
                if (relevance.value.isNotBlank()) {
                  stringResource(
                    Res.string.formeditor_condition_pill,
                    stringResource(relevance.operator.labelRes),
                    relevance.value,
                  )
                } else stringResource(relevance.operator.labelRes)
              val pillX =
                (layout.slotX(edge.from) + layout.width(edge.from) + layout.slotX(edge.to)) / 2 -
                  32.dp
              val pillY =
                (layout.slotY(edge.from) + layout.slotY(edge.to)) / 2 + (CardHeight / 2) - 12.dp
              Surface(
                modifier = Modifier.offset(pillX, pillY),
                shape = RoundedCornerShape(12.dp),
                color = colors.surfaceContainerHighest,
                border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.7f)),
                shadowElevation = 2.dp,
              ) {
                Text(
                  text = pillText,
                  style =
                    MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                  color = colors.primary,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
              }
            }
          }

        LaunchButtonNode(
          text =
            form.effectiveCtaLabel.ifBlank { stringResource(Res.string.formeditor_untitled_form) },
          modifier = Modifier.offset(layout.x(0), layout.top(0)),
          isSelected = uiState.isFormSelected,
          onClick = actions::selectForm,
        )

        val handleAddSection: (Int?) -> Unit = { idx ->
          val secId = actions.addSection("Section", repeatable = false, atIndex = idx)
          actions.selectSection(secId)
        }
        val handleAddRepeatedSection: (Int?) -> Unit = { idx ->
          val secId = actions.addSection("Section", repeatable = true, atIndex = idx)
          actions.selectSection(secId)
        }

        // Hoverable "+" between Start and Q1 (or Q0)
        if (!uiState.isReadOnly) {
          AddQuestionAffordance(
            centerX = layout.x(0) + layout.width(0) + SlotGap / 2,
            centerY = layout.midY,
            atIndex = 0,
            onAdd = { type, idx -> actions.addQuestion(type, idx, sectionId = "") },
            onAddSection = handleAddSection,
            onAddRepeatedSection = handleAddRepeatedSection,
            alwaysVisible = false,
          )
        }

        form.questions.forEachIndexed { index, question ->
          val slot = FormFlowGraph.questionSlot(index)
          key(question.key) {
            val isDragged = drag.draggingKey == question.key
            val shift by
              animateFloatAsState(
                targetValue = drag.shiftFor(index) * pitchPx,
                animationSpec = if (drag.isDragging) spring() else snap(),
              )
            val slotLeftPx = with(density) { layout.x(slot).toPx() }
            val baseCardModifier =
              Modifier.offset(layout.x(slot), layout.top(slot))
                .zIndex(if (isDragged) 1f else 0f)
                .graphicsLayer {
                  translationX = if (isDragged) drag.offset else shift
                  val lift = if (isDragged) 1.04f else 1f
                  scaleX = lift
                  scaleY = lift
                  shadowElevation = if (isDragged) 12.dp.toPx() else 0f
                }
            ScreenPreviewCard(
              index = index,
              question = question,
              form = form,
              datasets = uiState.datasets,
              isSelected = question.key == uiState.selectedKey,
              hasIssues = uiState.issuesFor(question.key).isNotEmpty(),
              isLinked = question.conceptLink != null,
              isDragged = isDragged,
              showDragHandle = !uiState.isReadOnly,
              activeLanguage = uiState.activeLanguage,
              defaultLanguage = uiState.defaultLanguage,
              onClick = { actions.select(question.key) },
              modifier =
                if (uiState.isReadOnly) {
                  baseCardModifier
                } else {
                  baseCardModifier.dragToReorder(
                    state = drag,
                    key = question.key,
                    index = index,
                    count = form.questions.size,
                    pitchPx = pitchPx,
                    axis = DragAxis.HORIZONTAL,
                    onMove = { key, to ->
                      actions.moveQuestionTo(key, to, targetSectionId = hoveredSectionId ?: "")
                      actions.select(key)
                    },
                    autoScroll = {
                      val left = slotLeftPx + drag.offset - horizontalScroll.value
                      val viewport = horizontalScroll.viewportSize.toFloat()
                      val step =
                        when {
                          left < edgeZonePx -> -24f
                          left + cardWidthPx > viewport - edgeZonePx -> 24f
                          else -> 0f
                        }
                      if (step == 0f) 0f else horizontalScroll.dispatchRawDelta(step)
                    },
                  )
                },
            )

            // Hover-only insertion affordance between question screens
            if (!uiState.isReadOnly) {
              AddQuestionAffordance(
                centerX = layout.x(slot) + layout.width(slot) + SlotGap / 2,
                centerY = layout.top(slot) + layout.height(slot) / 2,
                atIndex = index + 1,
                onAdd = { type, idx ->
                  actions.addQuestion(type, idx, sectionId = question.sectionId)
                },
                onAddSection = handleAddSection,
                onAddRepeatedSection = handleAddRepeatedSection,
                alwaysVisible = false,
              )
            }
          }
        }
        if (!uiState.isReadOnly) {
          val lastEndSectionId = form.sections.lastOrNull()?.id
          GhostQuestionCard(
            atIndex = form.questions.size,
            onAdd = { type, idx ->
              actions.addQuestion(type, idx, sectionId = "", afterSectionId = lastEndSectionId)
            },
            onAddSection = { idx ->
              val secId =
                actions.addSection(
                  "Section",
                  repeatable = false,
                  atIndex = idx,
                  afterSectionId = lastEndSectionId,
                )
              actions.selectSection(secId)
            },
            onAddRepeatedSection = { idx ->
              val secId =
                actions.addSection(
                  "Section",
                  repeatable = true,
                  atIndex = idx,
                  afterSectionId = lastEndSectionId,
                )
              actions.selectSection(secId)
            },
            modifier = Modifier.offset(layout.ghostX, layout.cardTop),
          )
        }

        // Drop indicator between question cards while dragging
        if (
          drag.isDragging &&
            drag.targetIndex in 0..form.questions.size &&
            form.questions.isNotEmpty()
        ) {
          val dropSlot =
            if (drag.targetIndex < form.questions.size) {
              FormFlowGraph.questionSlot(drag.targetIndex)
            } else {
              FormFlowGraph.questionSlot(form.questions.size - 1)
            }
          val dropIndicatorX =
            if (drag.targetIndex < form.questions.size) {
              layout.x(dropSlot) - SlotGap / 2
            } else {
              layout.x(dropSlot) + CardWidth + SlotGap / 2
            }
          Surface(
            modifier =
              Modifier.offset(dropIndicatorX - 2.dp, layout.cardTop)
                .width(4.dp)
                .height(CardHeight)
                .zIndex(10f),
            color = colors.primary,
            shape = RoundedCornerShape(2.dp),
            shadowElevation = 4.dp,
          ) {}
        }

        TerminalNode(
          text = stringResource(Res.string.formeditor_review_and_submit),
          isSelected = uiState.isFormSelected,
          onClick = actions::selectForm,
          modifier = Modifier.offset(layout.x(layout.endSlot), layout.top(layout.endSlot)),
        )
        Text(
          text = SaveToRules.outcome(form, uiState.saveTarget),
          style = MaterialTheme.typography.labelSmall,
          color = colors.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier =
            Modifier.offset(
                layout.x(layout.endSlot) - SlotGap / 2,
                layout.top(layout.endSlot) + TerminalHeight + 8.dp,
              )
              .width(TerminalWidth + SlotGap),
        )
      }
    }
  }
}

@Composable
private fun AddQuestionAffordance(
  centerX: Dp,
  centerY: Dp,
  atIndex: Int,
  onAdd: (EditorQuestionType, Int?) -> Unit,
  alwaysVisible: Boolean = false,
  onAddSection: ((Int?) -> Unit)? = null,
  onAddRepeatedSection: ((Int?) -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isHovered by interactionSource.collectIsHoveredAsState()
  var menuExpanded by remember { mutableStateOf(false) }
  val colors = MaterialTheme.colorScheme

  val buttonSize = 32.dp
  val slotWidth = SlotGap
  val slotHeight = 64.dp

  Box(
    modifier =
      modifier
        .offset(x = centerX - slotWidth / 2, y = centerY - slotHeight / 2)
        .size(slotWidth, slotHeight)
        .hoverable(interactionSource),
    contentAlignment = Alignment.Center,
  ) {
    // The button is always composed and hit-testable, only transparent until hovered. Touch input
    // has no hover, so composing it on hover alone meant the first tap merely revealed it.
    val visible = alwaysVisible || isHovered || menuExpanded
    CompositionLocalProvider(
      LocalInteractivePointerIcon provides
        if (visible) LocalInteractivePointerIcon.current else GrabPointerIcon
    ) {
      Surface(
        onClick = { menuExpanded = true },
        shape = CircleShape,
        color = if (alwaysVisible && !isHovered) colors.secondaryContainer else colors.secondary,
        contentColor =
          if (alwaysVisible && !isHovered) colors.onSecondaryContainer else colors.onSecondary,
        shadowElevation = if (isHovered || menuExpanded) 4.dp else if (visible) 1.dp else 0.dp,
        modifier = Modifier.size(buttonSize).graphicsLayer { alpha = if (visible) 1f else 0f },
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            Icons.Outlined.Add,
            contentDescription = stringResource(Res.string.formeditor_add_question_here),
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }
    AddQuestionMenu(
      expanded = menuExpanded,
      onDismiss = { menuExpanded = false },
      onAdd = onAdd,
      atIndex = atIndex,
      onAddSection = onAddSection,
      onAddRepeatedSection = onAddRepeatedSection,
    )
  }
}

private fun DrawScope.drawFlowEdge(
  edge: FlowEdge,
  layout: FlowLayout,
  color: Color,
  strokeWidthDp: Dp,
) {
  val strokeWidth = strokeWidthDp.toPx()
  val dash =
    if (edge.kind == FlowEdgeKind.NEXT) null else PathEffect.dashPathEffect(floatArrayOf(12f, 8f))

  val fromRow = layout.slotRow(edge.from)
  val toRow = layout.slotRow(edge.to)

  if (fromRow != toRow) {
    // Cross-row branching connector: smooth cubic bezier S-curve
    val startX = (layout.slotX(edge.from) + layout.width(edge.from)).toPx()
    val startY = (layout.slotY(edge.from) + layout.height(edge.from) / 2).toPx()
    val endX = (layout.slotX(edge.to) - 2.dp).toPx()
    val endY = (layout.slotY(edge.to) + layout.height(edge.to) / 2).toPx()
    val dx = (endX - startX).coerceAtLeast(40f)
    val c1 = Offset(startX + dx * 0.5f, startY)
    val c2 = Offset(endX - dx * 0.5f, endY)
    val path =
      Path().apply {
        moveTo(startX, startY)
        cubicTo(c1.x, c1.y, c2.x, c2.y, endX, endY)
      }
    drawPath(path, color, style = Stroke(width = strokeWidth, pathEffect = dash))
    drawArrowHead(tip = Offset(endX, endY), from = Offset(endX - 10f, endY), color = color)
  } else if (edge.span == 1) {
    val startY = (layout.slotY(edge.from) + layout.height(edge.from) / 2).toPx()
    val endY = (layout.slotY(edge.to) + layout.height(edge.to) / 2).toPx()
    val start = Offset((layout.slotX(edge.from) + layout.width(edge.from)).toPx(), startY)
    val end = Offset((layout.slotX(edge.to) - 2.dp).toPx(), endY)
    if (layout.includeGhostSlot && edge.to == layout.endSlot) {
      val ghostLeft = Offset((layout.ghostX - 2.dp).toPx(), (layout.ghostY + CardHeight / 2).toPx())
      val ghostRight =
        Offset((layout.ghostX + CardWidth + 2.dp).toPx(), (layout.ghostY + CardHeight / 2).toPx())
      drawLine(color, start, ghostLeft, strokeWidth = strokeWidth, pathEffect = dash)
      drawLine(color, ghostRight, end, strokeWidth = strokeWidth, pathEffect = dash)
      drawArrowHead(tip = end, from = ghostRight, color = color)
    } else {
      drawLine(color, start, end, strokeWidth = strokeWidth, pathEffect = dash)
      drawArrowHead(tip = end, from = start, color = color)
    }
  } else {
    val sx = (layout.slotX(edge.from) + layout.width(edge.from) - ArcInset).toPx()
    val sy = layout.slotY(edge.from).toPx()
    val ex = (layout.slotX(edge.to) + ArcInset).toPx()
    val ey = (layout.slotY(edge.to) - 2.dp).toPx()
    val apexY = (layout.slotY(edge.from) - arcRise(edge.span)).toPx()
    val path =
      Path().apply {
        moveTo(sx, sy)
        cubicTo(sx, apexY, ex, apexY, ex, ey)
      }
    drawPath(path, color, style = Stroke(width = strokeWidth, pathEffect = dash))
    drawArrowHead(tip = Offset(ex, ey), from = Offset(ex, apexY), color = color)
  }
}

private fun DrawScope.drawArrowHead(tip: Offset, from: Offset, color: Color) {
  val angle = atan2(tip.y - from.y, tip.x - from.x)
  val length = 10.dp.toPx()
  val spread = 0.45f
  val path =
    Path().apply {
      moveTo(tip.x, tip.y)
      lineTo(tip.x - length * cos(angle - spread), tip.y - length * sin(angle - spread))
      lineTo(tip.x - length * cos(angle + spread), tip.y - length * sin(angle + spread))
      close()
    }
  drawPath(path, color)
}

/**
 * Start node of the flow: renders the data-collection launch button shown in the app using the
 * user-specified button label (or form title when blank). Selecting it shows Form properties.
 */
@Composable
private fun LaunchButtonNode(
  text: String,
  modifier: Modifier = Modifier,
  isSelected: Boolean = false,
  onClick: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val startDescription = stringResource(Res.string.formeditor_start_node_description, text)
  val sized =
    modifier.width(StartButtonWidth).height(StartButtonHeight).semantics {
      contentDescription = startDescription
    }
  val border = if (isSelected) BorderStroke(2.dp, colors.onPrimaryContainer) else null
  Surface(
    onClick = onClick,
    modifier = sized,
    shape = MaterialTheme.shapes.small,
    color = colors.primary,
    contentColor = colors.onPrimary,
    border = border,
  ) {
    Row(
      modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = Icons.Outlined.Description,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/** End node of the flow; selecting it shows Form properties. */
@Composable
private fun TerminalNode(
  text: String,
  modifier: Modifier = Modifier,
  isSelected: Boolean = false,
  onClick: (() -> Unit)? = null,
) {
  val colors = MaterialTheme.colorScheme
  val content: @Composable () -> Unit = {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
      Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
      )
    }
  }
  val sized = modifier.width(TerminalWidth).height(TerminalHeight)
  val shape = RoundedCornerShape(50)
  val border = if (isSelected) BorderStroke(2.dp, colors.secondary) else null
  if (onClick != null) {
    Surface(
      onClick = onClick,
      modifier = sized,
      shape = shape,
      color = colors.secondaryContainer,
      contentColor = colors.onSecondaryContainer,
      border = border,
      content = content,
    )
  } else {
    Surface(
      modifier = sized,
      shape = shape,
      color = colors.secondaryContainer,
      contentColor = colors.onSecondaryContainer,
      border = border,
      content = content,
    )
  }
}

/**
 * Dashed "ghost" screen card at the end of the flow (before Review & submit) or inside a section
 * container that lets authors append a new question screen directly on the canvas.
 */
@Composable
internal fun GhostQuestionCard(
  atIndex: Int,
  onAdd: (EditorQuestionType, Int?) -> Unit,
  modifier: Modifier = Modifier,
  title: String = stringResource(Res.string.formeditor_add_a_question),
  subtitle: String = stringResource(Res.string.formeditor_ghost_card_subtitle),
  contentDescription: String = stringResource(Res.string.formeditor_add_question_at_end_of_flow),
  onAddSection: ((Int?) -> Unit)? = null,
  onAddRepeatedSection: ((Int?) -> Unit)? = null,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isHovered by interactionSource.collectIsHoveredAsState()
  var menuExpanded by remember { mutableStateOf(false) }
  val colors = MaterialTheme.colorScheme
  val highlighted = isHovered || menuExpanded
  val borderColor = if (highlighted) colors.primary else colors.outlineVariant
  val containerColor =
    if (highlighted) colors.secondaryContainer.copy(alpha = 0.25f)
    else colors.surfaceContainerLow.copy(alpha = 0.7f)

  Box(
    modifier =
      modifier
        .width(CardWidth)
        .height(CardHeight)
        .clip(MaterialTheme.shapes.medium)
        .background(containerColor)
        .hoverable(interactionSource)
        .clickable(
          interactionSource = interactionSource,
          indication = null,
          onClick = { menuExpanded = true },
        )
        .semantics { this.contentDescription = contentDescription },
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val strokePx = (if (highlighted) 2.dp else 1.5.dp).toPx()
      val cornerPx = 12.dp.toPx()
      drawRoundRect(
        color = borderColor,
        topLeft = Offset(strokePx / 2f, strokePx / 2f),
        size =
          androidx.compose.ui.geometry.Size(
            size.width - strokePx,
            size.height - strokePx,
          ),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerPx, cornerPx),
        style =
          Stroke(
            width = strokePx,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
          ),
      )
    }
    GhostQuestionCardContent(highlighted = highlighted, title = title, subtitle = subtitle)
    AddQuestionMenu(
      expanded = menuExpanded,
      onDismiss = { menuExpanded = false },
      onAdd = onAdd,
      atIndex = atIndex,
      onAddSection = onAddSection,
      onAddRepeatedSection = onAddRepeatedSection,
    )
  }
}

@Composable
private fun GhostQuestionCardContent(highlighted: Boolean, title: String, subtitle: String) {
  val colors = MaterialTheme.colorScheme
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(10.dp),
    modifier = Modifier.padding(16.dp),
  ) {
    Surface(
      shape = CircleShape,
      color = if (highlighted) colors.primary else colors.secondaryContainer,
      contentColor = if (highlighted) colors.onPrimary else colors.onSecondaryContainer,
      modifier = Modifier.size(44.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(22.dp))
      }
    }
    Text(
      text = title,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.SemiBold,
      color = if (highlighted) colors.primary else colors.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    Text(
      text = subtitle,
      style = MaterialTheme.typography.bodySmall,
      color = colors.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
  }
}
