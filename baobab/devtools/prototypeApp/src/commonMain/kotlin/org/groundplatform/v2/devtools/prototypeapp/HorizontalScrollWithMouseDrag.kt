/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.devtools.prototypeapp

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange

/**
 * [horizontalScroll] that can also be dragged with a mouse.
 *
 * Compose scrollables only follow touch and stylus drags (mouse input scrolls with the wheel or
 * trackpad), so chip rows in the device preview, which stands in for a touch screen, can't be
 * dragged with a mouse. This adds a mouse-only horizontal drag on top of the regular scroll
 * behavior: touch drags, wheel, and Shift+wheel are still handled by [horizontalScroll], and taps
 * on children still click (a drag only starts past the touch slop, then consumes the gesture).
 */
internal fun Modifier.horizontalScrollWithMouseDrag(state: ScrollState): Modifier =
  mouseDragToScroll(state).horizontalScroll(state)

/**
 * Scrolls [state] horizontally while a mouse drags over this element. Apply it before (outside) the
 * matching [horizontalScroll] so drag positions are measured in the fixed viewport rather than the
 * scrolling content.
 */
internal fun Modifier.mouseDragToScroll(state: ScrollState): Modifier =
  pointerInput(state) {
    awaitEachGesture {
      val down = awaitFirstDown(requireUnconsumed = false)
      if (down.type != PointerType.Mouse) return@awaitEachGesture
      val drag =
        awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
          change.consume()
          state.dispatchRawDelta(-overSlop)
        } ?: return@awaitEachGesture
      horizontalDrag(drag.id) { change ->
        state.dispatchRawDelta(-change.positionChange().x)
        change.consume()
      }
    }
  }
