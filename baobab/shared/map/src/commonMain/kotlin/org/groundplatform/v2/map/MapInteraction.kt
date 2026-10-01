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
package org.groundplatform.v2.map

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.DpOffset

/**
 * Lets the app take over one-finger drags instead of panning the map, e.g. to move a vertex while
 * editing geometry.
 */
fun interface MapDragHandler {
  /**
   * A one-finger drag started at [position], relative to the map's top-left corner. Called once the
   * pointer has moved past the touch slop, so taps never reach it. Returns the [MapDrag] that
   * receives the rest of the gesture, or `null` to pan the map as usual.
   */
  fun onDragStart(position: DpOffset): MapDrag?
}

/** A drag claimed through [MapDragHandler]. */
interface MapDrag {
  /** The pointer moved to [position], relative to the map's top-left corner. */
  fun onDrag(position: DpOffset)

  /** The pointer was lifted or the gesture was cancelled. */
  fun onDragEnd() {}
}

/** How a [GroundMap] responds to input; passed from [GroundMap] to the platform renderer. */
@Immutable
internal data class MapGestureOptions(
  /** When `false` the map ignores input, so it can sit in a scrolling list as a preview. */
  val enabled: Boolean = true,
  val dragHandler: MapDragHandler? = null,
)
