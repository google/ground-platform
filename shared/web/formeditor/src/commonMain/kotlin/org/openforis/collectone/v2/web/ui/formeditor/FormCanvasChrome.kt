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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.editor.EditorIssue
import org.openforis.collectone.v2.app.domain.model.editor.FormPreviewTarget
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.formeditor_duration_estimate
import org.openforis.collectone.v2.web.resources.formeditor_duration_fatigue_warning
import org.openforis.collectone.v2.web.resources.formeditor_duration_minutes
import org.openforis.collectone.v2.web.resources.formeditor_duration_minutes_long
import org.openforis.collectone.v2.web.resources.formeditor_flow_summary_mobile
import org.openforis.collectone.v2.web.resources.formeditor_flow_summary_web
import org.openforis.collectone.v2.web.resources.formeditor_issue_count
import org.openforis.collectone.v2.web.resources.formeditor_legend_always_next
import org.openforis.collectone.v2.web.resources.formeditor_legend_if_condition_true
import org.openforis.collectone.v2.web.resources.formeditor_potential_path_count
import org.openforis.collectone.v2.web.resources.formeditor_screen_count
import org.openforis.collectone.v2.web.ui.common.GrabPointerIcon
import org.openforis.collectone.v2.web.ui.common.GrabbingPointerIcon
import org.openforis.collectone.v2.web.ui.common.showPlatformGrabCursor
import org.openforis.collectone.v2.web.ui.common.showPlatformGrabbingCursor

/**
 * Floating bottom-left status and legend pill overlaid on the Form editor canvas (Mobile and Web).
 * Surfaces screen/question & path count, estimated duration & fatigue warning badge with tooltip,
 * validation issue count, and the flow edge legend.
 */
@Composable
internal fun CanvasStatusOverlay(
  uiState: FormEditorUiState,
  actions: FormSelectionActions,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(24.dp),
    color = colors.surfaceContainerHigh.copy(alpha = 0.94f),
    contentColor = colors.onSurface,
    tonalElevation = 3.dp,
    shadowElevation = 4.dp,
    border = BorderStroke(1.dp, colors.outlineVariant),
  ) {
    Row(
      modifier =
        Modifier.horizontalScroll(rememberScrollState())
          .padding(horizontal = 14.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = flowSummaryText(uiState),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        softWrap = false,
      )
      DurationFatigueBadge(uiState = uiState)
      if (uiState.issues.isNotEmpty()) {
        IssuesCountPill(issues = uiState.issues, actions = actions)
      }
      FlowLegend()
    }
  }
}

@Composable
private fun flowSummaryText(uiState: FormEditorUiState): String {
  val questionCount = uiState.form.questions.size
  return when (uiState.previewTarget) {
    FormPreviewTarget.MOBILE ->
      stringResource(
        Res.string.formeditor_flow_summary_mobile,
        pluralStringResource(Res.plurals.formeditor_screen_count, questionCount, questionCount),
        pluralStringResource(
          Res.plurals.formeditor_potential_path_count,
          uiState.pathCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
          uiState.pathCount,
        ),
      )
    FormPreviewTarget.WEB ->
      pluralStringResource(Res.plurals.formeditor_flow_summary_web, questionCount, questionCount)
  }
}

@Composable
private fun IssuesCountPill(issues: List<EditorIssue>, actions: FormSelectionActions) {
  val colors = MaterialTheme.colorScheme
  Surface(
    onClick = {
      val first = issues.first()
      when {
        first.questionKey != null -> actions.select(first.questionKey)
        first.sectionId != null -> actions.selectSection(first.sectionId)
        else -> actions.selectForm()
      }
    },
    shape = RoundedCornerShape(50),
    color = colors.errorContainer,
    contentColor = colors.onErrorContainer,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(Icons.Outlined.Warning, contentDescription = null, modifier = Modifier.size(14.dp))
      Text(
        text = pluralStringResource(Res.plurals.formeditor_issue_count, issues.size, issues.size),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        softWrap = false,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DurationFatigueBadge(uiState: FormEditorUiState) {
  val colors = MaterialTheme.colorScheme
  val minutes = uiState.estimatedCompletionMinutes
  val longestCount = uiState.longestPathQuestionCount
  val isFatigue = uiState.isFatigueWarning
  val estimateText =
    pluralStringResource(Res.plurals.formeditor_duration_estimate, longestCount, longestCount)
  val fatigueText = stringResource(Res.string.formeditor_duration_fatigue_warning)
  val tooltipText = buildString {
    append(estimateText)
    if (isFatigue) {
      append(" ")
      append(fatigueText)
    }
  }
  TooltipBox(
    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
    tooltip = { PlainTooltip { Text(tooltipText) } },
    state = rememberTooltipState(),
  ) {
    Surface(
      shape = RoundedCornerShape(50),
      color = if (isFatigue) colors.tertiaryContainer else colors.surfaceContainerHighest,
      contentColor = if (isFatigue) colors.onTertiaryContainer else colors.onSurfaceVariant,
      modifier = Modifier.semantics { contentDescription = tooltipText },
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = if (isFatigue) Icons.Outlined.Warning else Icons.Outlined.Schedule,
          contentDescription = null,
          modifier = Modifier.size(14.dp),
        )
        Text(
          text =
            if (isFatigue) stringResource(Res.string.formeditor_duration_minutes_long, minutes)
            else stringResource(Res.string.formeditor_duration_minutes, minutes),
          style = MaterialTheme.typography.labelSmall,
          fontWeight = if (isFatigue) FontWeight.SemiBold else FontWeight.Medium,
          softWrap = false,
        )
      }
    }
  }
}

/** Custom horizontal scrollbar thumb beneath the flow canvas that supports grab-dragging. */
@Composable
internal fun FlowHorizontalScrollBar(scrollState: ScrollState, modifier: Modifier = Modifier) {
  val maxScroll = scrollState.maxValue
  if (maxScroll <= 0) return

  val colors = MaterialTheme.colorScheme

  Box(
    modifier =
      modifier
        .height(10.dp)
        .clip(CircleShape)
        .background(colors.surfaceContainerHighest.copy(alpha = 0.5f))
  ) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val trackWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
      val totalContentPx = trackWidthPx + maxScroll.toFloat()
      val thumbWidthFraction = (trackWidthPx / totalContentPx).coerceIn(0.1f, 1f)
      val thumbWidthPx = trackWidthPx * thumbWidthFraction
      val scrollFraction = (scrollState.value.toFloat() / maxScroll).coerceIn(0f, 1f)
      val thumbOffsetPx = scrollFraction * (trackWidthPx - thumbWidthPx)
      val thumbOffsetDp = with(LocalDensity.current) { thumbOffsetPx.toDp() }
      val thumbWidthDp = with(LocalDensity.current) { thumbWidthPx.toDp() }

      var isThumbHovered by remember { mutableStateOf(false) }
      var isThumbDragging by remember { mutableStateOf(false) }
      DisposableEffect(isThumbHovered) {
        if (isThumbHovered) showPlatformGrabCursor(true)
        onDispose { if (isThumbHovered) showPlatformGrabCursor(false) }
      }
      DisposableEffect(isThumbDragging) {
        if (isThumbDragging) showPlatformGrabbingCursor(true)
        onDispose { if (isThumbDragging) showPlatformGrabbingCursor(false) }
      }

      Box(
        modifier =
          Modifier.offset(x = thumbOffsetDp)
            .width(thumbWidthDp)
            .height(10.dp)
            .clip(CircleShape)
            .background(colors.outline.copy(alpha = 0.65f))
            .pointerHoverIcon(if (isThumbDragging) GrabbingPointerIcon else GrabPointerIcon)
            .pointerInput(Unit) {
              awaitPointerEventScope {
                while (true) {
                  val event = awaitPointerEvent()
                  when (event.type) {
                    PointerEventType.Enter -> isThumbHovered = true
                    PointerEventType.Exit -> isThumbHovered = false
                  }
                }
              }
            }
            .pointerInput(maxScroll, trackWidthPx, thumbWidthPx) {
              detectHorizontalDragGestures(
                onDragStart = { isThumbDragging = true },
                onDragEnd = { isThumbDragging = false },
                onDragCancel = { isThumbDragging = false },
              ) { change, dragAmount ->
                change.consume()
                val scrollableTrack = trackWidthPx - thumbWidthPx
                if (scrollableTrack > 0f) {
                  val deltaScroll = (dragAmount / scrollableTrack) * maxScroll
                  scrollState.dispatchRawDelta(deltaScroll)
                }
              }
            }
      )
    }
  }
}

@Composable
private fun FlowLegend() {
  val colors = MaterialTheme.colorScheme
  Row(
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    LegendItem(
      stringResource(Res.string.formeditor_legend_always_next),
      colors.outline,
      dashed = false,
    )
    LegendItem(
      stringResource(Res.string.formeditor_legend_if_condition_true),
      colors.primary,
      dashed = true,
    )
  }
}

@Composable
private fun LegendItem(text: String, color: Color, dashed: Boolean) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Canvas(modifier = Modifier.width(28.dp).height(10.dp)) {
      drawLine(
        color = color,
        start = Offset(0f, size.height / 2),
        end = Offset(size.width, size.height / 2),
        strokeWidth = 2.dp.toPx(),
        pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null,
      )
    }
    Spacer(Modifier.width(6.dp))
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      softWrap = false,
    )
  }
}
