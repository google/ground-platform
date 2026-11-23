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
package org.openforis.collectone.v2.web.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.web_resize_side_panel

/**
 * Width of the separator between a resizable side panel and the adjacent content area. The whole
 * separator is the drag target for resizing the panel.
 */
val SidePanelSeparatorWidth = 8.dp

private const val HOVER_TINT_ALPHA = 0.16f
private const val DRAG_TINT_ALPHA = 0.24f

/**
 * Draggable separator on the inner border of a resizable side panel, [SidePanelSeparatorWidth]
 * wide. At rest it fills the drag area with [containerColor] (matching the adjacent side panel's
 * surface) without a divider line. When [enabled], the whole separator is the drag target: it shows
 * a resize cursor and tints the entire drag area while hovered or dragged, and dragging reports the
 * new width through [onWidthChange], which clamps it. The unclamped drag position is tracked so the
 * border only moves back once the pointer returns past the clamp limit.
 *
 * When [panelOnRight] is `true`, the separator sits on the left border of a right-hand panel, so
 * dragging left widens the panel and dragging right narrows it.
 */
@Composable
fun SidePanelSeparator(
  widthDp: Float,
  onWidthChange: (Float) -> Unit,
  enabled: Boolean = true,
  panelOnRight: Boolean = false,
  containerColor: Color = MaterialTheme.colorScheme.surface,
  modifier: Modifier = Modifier,
) {
  val density = LocalDensity.current
  val interactionSource = remember { MutableInteractionSource() }
  val isHovered by interactionSource.collectIsHoveredAsState()
  val isDragged by interactionSource.collectIsDraggedAsState()
  val isActive = enabled && (isHovered || isDragged)
  val currentWidthDp by rememberUpdatedState(widthDp)
  var unclampedWidthDp by remember { mutableStateOf(widthDp) }
  val direction = if (panelOnRight) -1f else 1f
  val resizeSidePanelLabel = stringResource(Res.string.web_resize_side_panel)
  val draggableState = rememberDraggableState { deltaPx ->
    unclampedWidthDp += direction * with(density) { deltaPx.toDp() }.value
    onWidthChange(unclampedWidthDp)
  }
  val targetColor =
    when {
      enabled && isDragged ->
        MaterialTheme.colorScheme.primary
          .copy(alpha = DRAG_TINT_ALPHA)
          .compositeOver(containerColor)
      enabled && isHovered ->
        MaterialTheme.colorScheme.primary
          .copy(alpha = HOVER_TINT_ALPHA)
          .compositeOver(containerColor)
      else -> containerColor
    }
  val backgroundColor by
    animateColorAsState(targetValue = targetColor, label = "sidePanelSeparatorBackground")

  DisposableEffect(isActive) {
    if (isActive) showPlatformHorizontalResizeCursor(true)
    onDispose { if (isActive) showPlatformHorizontalResizeCursor(false) }
  }

  Box(
    modifier =
      modifier
        .background(backgroundColor)
        .then(
          if (enabled) {
            Modifier.pointerHoverIcon(HorizontalResizePointerIcon)
              .hoverable(interactionSource)
              .draggable(
                state = draggableState,
                orientation = Orientation.Horizontal,
                interactionSource = interactionSource,
                onDragStarted = { unclampedWidthDp = currentWidthDp },
              )
              .semantics { contentDescription = resizeSidePanelLabel }
          } else {
            Modifier
          }
        )
  )
}
