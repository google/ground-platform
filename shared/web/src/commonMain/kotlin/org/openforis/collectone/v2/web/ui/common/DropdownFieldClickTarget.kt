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
package org.openforis.collectone.v2.web.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import org.openforis.collectone.v2.app.ui.common.dragSlopFor

/**
 * Click target laid over a read-only exposed dropdown field (an `OutlinedTextField` with `readOnly
 * = true` inside an `ExposedDropdownMenuBox`) that opens or closes the menu on every click.
 *
 * Without it the field handles clicks itself, and a read-only text field still starts a text
 * selection drag as soon as the mouse moves past Compose's 0.125 dp mouse slop. The pixel or two a
 * hand moves during an ordinary click is enough, the drag consumes the movement, the menu anchor
 * treats the click as cancelled, and the organizer has to click again holding the mouse perfectly
 * still. This target takes the whole gesture, so the text field never sees it, and only treats it
 * as a drag (ignoring it) past [org.openforis.collectone.v2.app.ui.common.MouseDragSlop].
 *
 * Place it in a [Box] with the field, after the field so it's on top.
 */
@Composable
internal fun BoxScope.DropdownFieldClickTarget(
  expanded: Boolean,
  onExpandedChange: (Boolean) -> Unit,
  enabled: Boolean = true,
) {
  val currentExpanded by rememberUpdatedState(expanded)
  val currentOnExpandedChange by rememberUpdatedState(onExpandedChange)
  Box(
    Modifier.matchParentSize()
      .pointerHoverIcon(PointerIcon.Default)
      .semantics {
        role = Role.DropdownList
        if (enabled) {
          onClick {
            currentOnExpandedChange(!currentExpanded)
            true
          }
        }
      }
      .pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false)
          down.consume()
          // The state when the button went down: if the press dismissed an open menu first, the
          // release must not reopen it.
          val wasExpanded = currentExpanded
          val slop = dragSlopFor(down)
          var travel = Offset.Zero
          while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            travel += change.positionChange()
            change.consume()
            if (!change.pressed) {
              if (travel.getDistance() <= slop) currentOnExpandedChange(!wasExpanded)
              break
            }
          }
        }
      }
  )
}
