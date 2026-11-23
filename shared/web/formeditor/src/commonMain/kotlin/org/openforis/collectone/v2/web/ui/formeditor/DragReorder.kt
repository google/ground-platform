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

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import org.openforis.collectone.v2.app.ui.common.DragAxis
import org.openforis.collectone.v2.app.ui.common.MouseDragSlop
import org.openforis.collectone.v2.app.ui.common.awaitDragPastSlop
import org.openforis.collectone.v2.app.ui.common.dragSlopFor

/**
 * Drag-and-drop reordering state for a list of equally spaced items.
 *
 * The list itself is only reordered on drop. While dragging, the dragged item follows the pointer
 * ([offset]) and its siblings are shifted by one [pitch] ([shiftFor]) to open a gap at
 * [targetIndex].
 */
class DragReorderState {
  var draggingKey: String? by mutableStateOf(null)
    private set

  var fromIndex: Int by mutableIntStateOf(-1)
    private set

  /** Pointer travel of the dragged item along the list axis, in pixels. */
  var offset: Float by mutableFloatStateOf(0f)
    private set

  private var itemCount = 0
  private var pitch = 1f

  val isDragging: Boolean
    get() = draggingKey != null

  /** Index the dragged item would land at if dropped now, or -1 when idle. */
  val targetIndex: Int
    get() = if (isDragging) dragTargetIndex(fromIndex, offset, pitch, itemCount) else -1

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
    val min = -fromIndex * pitch - pitch / 2
    val max = (itemCount - 1 - fromIndex) * pitch + pitch / 2
    offset = (offset + delta).coerceIn(min, max)
  }

  /** Visual shift, in pitches, for the non-dragged item at [index]. */
  fun shiftFor(index: Int): Int =
    if (!isDragging || index == fromIndex) 0 else dragShiftFor(index, fromIndex, targetIndex)

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
fun dragTargetIndex(from: Int, delta: Float, pitch: Float, count: Int): Int {
  if (count <= 0) return -1
  return (from + (delta / pitch).roundToInt()).coerceIn(0, count - 1)
}

/**
 * Direction (-1, 0, +1) the item at [index] shifts while the item at [from] hovers over [target].
 */
fun dragShiftFor(index: Int, from: Int, target: Int): Int =
  when {
    from < target && index in (from + 1)..target -> -1
    from > target && index in target until from -> 1
    else -> 0
  }

/** Returns a copy of this list with the element at [from] moved to [to]. */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
  if (from !in indices || to !in indices || from == to) return this
  return toMutableList().apply { add(to, removeAt(from)) }
}

/**
 * Makes this item draggable within a [DragReorderState] list. [onMove] receives the item key and
 * its drop index. [autoScroll] is called after each drag step and returns how many pixels the
 * surrounding container scrolled, so the dragged item stays under the pointer.
 *
 * A drag only starts past [dragSlopFor] (see [MouseDragSlop]), so a click with a slightly moving
 * mouse still reaches the item's `clickable`.
 */
fun Modifier.dragToReorder(
  state: DragReorderState,
  key: String,
  index: Int,
  count: Int,
  pitchPx: Float,
  axis: DragAxis,
  onMove: (key: String, toIndex: Int) -> Unit,
  autoScroll: (() -> Float)? = null,
): Modifier =
  pointerInput(key, index, count, pitchPx) {
    fun along(offset: Offset) = if (axis == DragAxis.HORIZONTAL) offset.x else offset.y
    fun step(delta: Float) {
      state.dragBy(delta)
      autoScroll?.invoke()?.let { scrolled -> if (scrolled != 0f) state.dragBy(scrolled) }
    }
    awaitEachGesture {
      val down = awaitFirstDown(requireUnconsumed = false)
      val (start, travel) = awaitDragPastSlop(down, dragSlopFor(down)) ?: return@awaitEachGesture
      state.start(key, index, count, pitchPx)
      try {
        step(along(travel))
        val released =
          drag(start.id) { change ->
            step(along(change.positionChange()))
            change.consume()
          }
        if (released) {
          state.end()?.let { (k, to) -> if (to >= 0 && to != index) onMove(k, to) }
        } else {
          state.cancel()
        }
      } catch (e: CancellationException) {
        state.cancel()
        throw e
      }
    }
  }
