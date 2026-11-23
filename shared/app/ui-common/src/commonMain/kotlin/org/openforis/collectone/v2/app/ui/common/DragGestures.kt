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
package org.openforis.collectone.v2.app.ui.common

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Axis along which a draggable list or scroll gesture is laid out. */
enum class DragAxis {
  HORIZONTAL,
  VERTICAL,
}

/**
 * Mouse travel needed before a press turns into a drag.
 *
 * Compose's own drag detectors use a mouse slop of only 0.125 dp (`mouseSlop` in foundation's
 * `DragGestureDetector.kt`, applied as `touchSlop * mouseSlop / 18.dp` for `PointerType.Mouse`), so
 * the pixel or two a hand moves during an ordinary click starts a drag. The drag consumes the
 * pointer movement, which cancels the `clickable` underneath, and the click is lost: the user has
 * to click again holding the mouse perfectly still. Clickable items that can also be dragged use
 * this larger slop instead.
 */
val MouseDragSlop = 8.dp

/** Distance the pointer that went [down] must travel before a drag starts. */
fun PointerInputScope.dragSlopFor(down: PointerInputChange): Float =
  if (down.type == PointerType.Mouse) MouseDragSlop.toPx() else viewConfiguration.touchSlop

/**
 * Waits until the pointer that went [down] travels past [slopPx] (measured along [axis], or in any
 * direction when `null`), without consuming anything before then so clicks underneath still
 * register. Returns the change that crossed the slop and the travel so far, or `null` if the
 * pointer was released or another gesture consumed the movement first.
 */
suspend fun AwaitPointerEventScope.awaitDragPastSlop(
  down: PointerInputChange,
  slopPx: Float,
  axis: DragAxis? = null,
): Pair<PointerInputChange, Offset>? {
  var travel = Offset.Zero
  while (true) {
    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return null
    if (!change.pressed || change.isConsumed) return null
    travel += change.positionChange()
    val distance =
      when (axis) {
        DragAxis.HORIZONTAL -> abs(travel.x)
        DragAxis.VERTICAL -> abs(travel.y)
        null -> travel.getDistance()
      }
    if (distance > slopPx) {
      change.consume()
      return change to travel
    }
  }
}
