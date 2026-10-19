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
package org.openforis.collectone.v2.core.forms.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.roundToInt
import org.openforis.collectone.v2.core.forms.model.ComponentState

private val RankItemSpacing = 8.dp
private val DefaultRankItemHeight = 56.dp

/**
 * Renders an ordered `<rank>` question (`ControlType.CONTROL_RANK`) where items can be reordered
 * either by dragging cards vertically or by activating the up/down buttons.
 */
@Composable
internal fun RankControlWidget(
  control: ComponentState.ControlState,
  controller: FormWizardController,
) {
  val colors = MaterialTheme.colorScheme
  val density = LocalDensity.current
  val path = control.canonicalPath
  val currentRankedValues: List<String> =
    (control.fieldState.value
      ?.list_value
      ?.values
      ?.mapNotNull { it.string_value }
      ?.takeIf { it.isNotEmpty() }
      ?: control.fieldState.value
        ?.scalar_value
        ?.string_value
        ?.trim()
        ?.split(Regex("\\s+"))
        ?.filter { it.isNotEmpty() }
        ?.takeIf { it.isNotEmpty() }) ?: control.options.map { it.value }

  val orderedOptions =
    currentRankedValues.mapNotNull { code -> control.options.find { it.value == code } } +
      control.options.filter { opt -> opt.value !in currentRankedValues }

  val drag = remember { RankReorderState() }
  val defaultHeightPx = with(density) { DefaultRankItemHeight.toPx() }
  val spacingPx = with(density) { RankItemSpacing.toPx() }
  var itemHeightPx by remember { mutableFloatStateOf(defaultHeightPx) }
  val pitchPx = (itemHeightPx + spacingPx).coerceAtLeast(1f)

  val moveOption: (fromIndex: Int, toIndex: Int) -> Unit = { fromIndex, toIndex ->
    val currentCodes = orderedOptions.map { it.value }
    val updatedCodes = currentCodes.movedRankItem(fromIndex, toIndex)
    if (updatedCodes != currentCodes) {
      controller.updateMultiSelect(path, updatedCodes)
    }
  }

  Column(verticalArrangement = Arrangement.spacedBy(RankItemSpacing)) {
    Text(
      text = "Order items from highest (#1) to lowest priority:",
      style = MaterialTheme.typography.labelMedium.copy(color = colors.onSurfaceVariant),
    )
    orderedOptions.forEachIndexed { index, option ->
      key(option.value) {
        val isDragged = drag.draggingKey == option.value
        val shift by
          animateFloatAsState(
            targetValue = drag.shiftFor(index) * pitchPx,
            animationSpec = if (drag.isDragging) spring() else snap(),
          )
        val displayIndex = drag.displayIndexFor(index, option.value, orderedOptions.size)

        OutlinedCard(
          modifier =
            Modifier.fillMaxWidth()
              .onSizeChanged { size ->
                if (size.height > 0) {
                  itemHeightPx = size.height.toFloat()
                }
              }
              .zIndex(if (isDragged) 1f else 0f)
              .graphicsLayer {
                translationY = if (isDragged) drag.offset else shift
                val lift = if (isDragged) 1.02f else 1f
                scaleX = lift
                scaleY = lift
                shadowElevation = if (isDragged) 8.dp.toPx() else 0f
              }
              .pointerHoverIcon(PointerIcon.Hand)
              .rankDragToReorder(
                state = drag,
                key = option.value,
                index = index,
                count = orderedOptions.size,
                pitchPx = pitchPx,
                onMove = moveOption,
              )
              .semantics {
                customActions = buildList {
                  if (index > 0) {
                    add(
                      CustomAccessibilityAction("Move up") {
                        moveOption(index, index - 1)
                        true
                      }
                    )
                  }
                  if (index < orderedOptions.lastIndex) {
                    add(
                      CustomAccessibilityAction("Move down") {
                        moveOption(index, index + 1)
                        true
                      }
                    )
                  }
                }
              },
          shape = MaterialTheme.shapes.medium,
          colors =
            CardDefaults.outlinedCardColors(
              containerColor =
                if (isDragged) colors.surfaceContainerHighest else colors.surfaceContainerLow
            ),
          border =
            BorderStroke(
              width = if (isDragged) 2.dp else 1.dp,
              color = if (isDragged) colors.primary else colors.outlineVariant,
            ),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector = RankDragHandleIcon,
              contentDescription = "Drag to reorder",
              tint = if (isDragged) colors.primary else colors.onSurfaceVariant,
              modifier = Modifier.size(20.dp),
            )
            GroundTonalBadge(text = "#${displayIndex + 1}", tone = GroundBadgeTone.PRIMARY)
            Text(
              text = option.label.text,
              style =
                MaterialTheme.typography.bodyMedium.copy(
                  fontWeight = FontWeight.Medium,
                  color = colors.onSurface,
                ),
              modifier = Modifier.weight(1f),
            )
            OutlinedButton(
              onClick = {
                if (index > 0) {
                  moveOption(index, index - 1)
                }
              },
              enabled = index > 0,
              contentPadding = PaddingValues(horizontal = 10.dp),
            ) {
              Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Move up",
                modifier = Modifier.size(18.dp),
              )
            }
            OutlinedButton(
              onClick = {
                if (index < orderedOptions.lastIndex) {
                  moveOption(index, index + 1)
                }
              },
              enabled = index < orderedOptions.lastIndex,
              contentPadding = PaddingValues(horizontal = 10.dp),
            ) {
              Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Move down",
                modifier = Modifier.size(18.dp),
              )
            }
          }
        }
      }
    }
  }
}

/**
 * Drag-and-drop reordering state for a vertical list of rank option cards.
 *
 * The underlying option list is only committed on drop. While dragging, the dragged card follows
 * the pointer ([offset]), its siblings shift by one [pitch] ([shiftFor]) to open a gap at
 * [targetIndex], and the displayed rank badges update live via [displayIndexFor].
 */
internal class RankReorderState {
  var draggingKey: String? by mutableStateOf(null)
    private set

  var fromIndex: Int by mutableIntStateOf(-1)
    private set

  /** Vertical pointer travel of the dragged item in pixels. */
  var offset: Float by mutableFloatStateOf(0f)
    private set

  private var itemCount = 0
  private var pitch = 1f

  val isDragging: Boolean
    get() = draggingKey != null

  /** Index the dragged item would land at if dropped now, or -1 when idle. */
  val targetIndex: Int
    get() = if (isDragging) rankDragTargetIndex(fromIndex, offset, pitch, itemCount) else -1

  fun start(key: String, index: Int, count: Int, pitch: Float) {
    draggingKey = key
    fromIndex = index
    itemCount = count
    this.pitch = pitch.coerceAtLeast(1f)
    offset = 0f
  }

  /** Moves the dragged item by [delta] pixels, keeping it within half a pitch of the list ends. */
  fun dragBy(delta: Float) {
    if (!isDragging) return
    val min = -fromIndex * pitch - pitch / 2f
    val max = (itemCount - 1 - fromIndex) * pitch + pitch / 2f
    offset = (offset + delta).coerceIn(min, max)
  }

  /** Visual shift, in pitches (-1, 0, +1), for the non-dragged item at [index]. */
  fun shiftFor(index: Int): Int =
    if (!isDragging || index == fromIndex) 0 else rankDragShiftFor(index, fromIndex, targetIndex)

  /**
   * Effective 0-based rank position shown on the badge of the item at [index] with [key] while a
   * drag is active (or [index] when idle).
   */
  fun displayIndexFor(index: Int, key: String, count: Int = itemCount): Int {
    if (!isDragging || count <= 0) return index
    val raw = if (draggingKey == key) targetIndex else index + shiftFor(index)
    return raw.coerceIn(0, count - 1)
  }

  /** Ends the drag, returning the dragged key and its drop index (or `null` if idle). */
  fun end(): Pair<String, Int>? {
    val key = draggingKey ?: return null
    val result = key to targetIndex
    cancel()
    return result
  }

  fun cancel() {
    draggingKey = null
    fromIndex = -1
    offset = 0f
  }
}

/** Index an item starting at [from] lands on after being dragged [delta] px in a [pitch] list. */
internal fun rankDragTargetIndex(from: Int, delta: Float, pitch: Float, count: Int): Int {
  if (count <= 0) return -1
  val safePitch = pitch.coerceAtLeast(1f)
  return (from + (delta / safePitch).roundToInt()).coerceIn(0, count - 1)
}

/**
 * Direction (-1, 0, +1) the item at [index] shifts while the item at [from] hovers over [target].
 */
internal fun rankDragShiftFor(index: Int, from: Int, target: Int): Int =
  when {
    from < target && index in (from + 1)..target -> -1
    from > target && index in target until from -> 1
    else -> 0
  }

/** Returns a copy of this list with the element at [from] moved to [to]. */
internal fun <T> List<T>.movedRankItem(from: Int, to: Int): List<T> {
  if (from !in indices || to !in indices || from == to) return this
  return toMutableList().apply { add(to, removeAt(from)) }
}

/**
 * Makes a rank option card draggable within a [RankReorderState] list.
 *
 * Uses `awaitFirstDown(requireUnconsumed = true)` so presses on the child "Move up" and "Move down"
 * buttons are handled by those buttons without starting a drag, while vertical drags anywhere else
 * on the card are consumed before an enclosing `verticalScroll` container can claim them.
 */
@Composable
internal fun Modifier.rankDragToReorder(
  state: RankReorderState,
  key: String,
  index: Int,
  count: Int,
  pitchPx: Float,
  onMove: (fromIndex: Int, toIndex: Int) -> Unit,
): Modifier {
  val currentOnMove by rememberUpdatedState(onMove)
  return pointerInput(key, index, count, pitchPx) {
    awaitEachGesture {
      val down = awaitFirstDown(requireUnconsumed = true)
      val slopPx =
        if (down.type == PointerType.Mouse) {
          2.dp.toPx()
        } else {
          viewConfiguration.touchSlop * 0.5f
        }
      val (start, initialDeltaY) =
        awaitVerticalRankDragPastSlop(down, slopPx) ?: return@awaitEachGesture
      state.start(key, index, count, pitchPx)
      try {
        state.dragBy(initialDeltaY)
        val released =
          verticalDrag(start.id) { change ->
            state.dragBy(change.positionChange().y)
            change.consume()
          }
        if (released) {
          state.end()?.let { (_, to) -> if (to >= 0 && to != index) currentOnMove(index, to) }
        } else {
          state.cancel()
        }
      } catch (e: CancellationException) {
        state.cancel()
        throw e
      }
    }
  }
}

private suspend fun AwaitPointerEventScope.awaitVerticalRankDragPastSlop(
  down: PointerInputChange,
  slopPx: Float,
): Pair<PointerInputChange, Float>? {
  var travel = Offset.Zero
  while (true) {
    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return null
    if (!change.pressed || change.isConsumed) return null
    val delta = change.positionChange()
    travel += delta
    if (abs(travel.y) >= abs(travel.x) && abs(delta.y) > 0f) {
      change.consume()
    }
    if (abs(travel.y) > slopPx) {
      change.consume()
      return change to travel.y
    }
  }
}

/** Material Design `DragIndicator` (6-dot grip) icon (`24x24` viewport). */
internal val RankDragHandleIcon: ImageVector by lazy {
  ImageVector.Builder(
      name = "Ground.DragIndicator",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
    )
    .path(fill = SolidColor(Color.Black)) {
      moveTo(11f, 18f)
      curveToRelative(0f, 1.1f, -0.9f, 2f, -2f, 2f)
      reflectiveCurveToRelative(-2f, -0.9f, -2f, -2f)
      reflectiveCurveToRelative(0.9f, -2f, 2f, -2f)
      reflectiveCurveTo(11f, 16.9f, 11f, 18f)
      close()
      moveTo(9f, 10f)
      curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
      reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
      reflectiveCurveToRelative(2f, -0.9f, 2f, -2f)
      reflectiveCurveTo(10.1f, 10f, 9f, 10f)
      close()
      moveTo(9f, 4f)
      curveTo(7.9f, 4f, 7f, 4.9f, 7f, 6f)
      reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
      reflectiveCurveToRelative(2f, -0.9f, 2f, -2f)
      reflectiveCurveTo(10.1f, 4f, 9f, 4f)
      close()
      moveTo(15f, 8f)
      curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
      reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
      reflectiveCurveToRelative(-2f, 0.9f, -2f, 2f)
      reflectiveCurveTo(13.9f, 8f, 15f, 8f)
      close()
      moveTo(15f, 10f)
      curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
      reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
      reflectiveCurveToRelative(2f, -0.9f, 2f, -2f)
      reflectiveCurveTo(16.1f, 10f, 15f, 10f)
      close()
      moveTo(15f, 16f)
      curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
      reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
      reflectiveCurveToRelative(2f, -0.9f, 2f, -2f)
      reflectiveCurveTo(16.1f, 16f, 15f, 16f)
      close()
    }
    .build()
}
