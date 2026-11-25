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
package org.openforis.collectone.v2.mobile.ui.onboarding

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

/**
 * How much of the Download survey screen's search field scrolling the survey list has hidden.
 * Scrolling the list down hides the field with it, and scrolling back up (even a little, anywhere
 * in the list) returns it.
 */
@Stable
internal class CollapsingSearchBarState {
  private var heightPx = 0f

  /** How far the field is moved up out of view, from 0 (fully shown) to minus its height. */
  var offsetPx by mutableFloatStateOf(0f)
    private set

  /** Sets the height of the field, which limits how far it can be hidden. */
  fun updateHeight(heightPx: Float) {
    this.heightPx = heightPx
    offsetPx = offsetPx.coerceIn(-heightPx, 0f)
  }

  /**
   * Follows a drag of the list: [consumed] is how far the list scrolled and [available] what it
   * left over (positive values reveal earlier items). The field hides only as far as the list
   * really scrolled, but returns on any drag back, so it can't stay hidden over a list that has
   * nothing left to scroll.
   */
  fun onListScrolled(consumed: Float, available: Float) {
    val dragged = consumed + available
    val delta = if (dragged > 0f) dragged else consumed
    offsetPx = (offsetPx + delta).coerceIn(-heightPx, 0f)
  }

  /** Shows the whole field. */
  fun expand() {
    offsetPx = 0f
  }

  /** Connects the field to the scrolling of the list below it, without consuming any of it. */
  val nestedScrollConnection =
    object : NestedScrollConnection {
      override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
      ): Offset {
        onListScrolled(consumed = consumed.y, available = available.y)
        return Offset.Zero
      }
    }
}

/**
 * Shrinks this element from the top by what [state] has hidden of it, clipping the rest. While
 * [isPinned], it stays fully shown, and returns to where the list's scrolling left it afterwards.
 */
internal fun Modifier.collapsedBy(state: CollapsingSearchBarState, isPinned: Boolean): Modifier =
  clipToBounds().layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    state.updateHeight(placeable.height.toFloat())
    val hidden = if (isPinned) 0 else -state.offsetPx.roundToInt()
    val visibleHeight = (placeable.height - hidden).coerceIn(0, placeable.height)
    layout(placeable.width, visibleHeight) { placeable.place(0, visibleHeight - placeable.height) }
  }
