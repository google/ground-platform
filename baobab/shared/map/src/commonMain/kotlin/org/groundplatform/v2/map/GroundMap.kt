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

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A map that draws [content], follows [cameraState], and reports [MapEvent]s.
 *
 * The basemap and style layers are drawn by the platform's native map SDK. [MapContent.markers] are
 * drawn in Compose on top, using [markerContent], so marker UI is shared across platforms.
 *
 * @param gesturesEnabled whether the user can pan, zoom, and tap the map; turn it off for static
 *   previews, e.g. inside a scrolling list.
 * @param dragHandler takes over one-finger drags it claims, e.g. to move a vertex while editing.
 */
@Composable
fun GroundMap(
  content: MapContent,
  cameraState: MapCameraState,
  onEvent: (MapEvent) -> Unit,
  modifier: Modifier = Modifier,
  gesturesEnabled: Boolean = true,
  dragHandler: MapDragHandler? = null,
  markerContent: @Composable (MapMarker) -> Unit = {},
) {
  val density = LocalDensity.current
  // Renderers capture gesture callbacks once, so route the handler through state.
  val currentDragHandler by rememberUpdatedState(dragHandler)
  val hasDragHandler = dragHandler != null
  val gestures =
    remember(gesturesEnabled, hasDragHandler) {
      MapGestureOptions(
        enabled = gesturesEnabled,
        dragHandler =
          if (hasDragHandler) MapDragHandler { currentDragHandler?.onDragStart(it) } else null,
      )
    }
  Box(
    modifier.onSizeChanged {
      with(density) { cameraState.viewportSize = DpSize(it.width.toDp(), it.height.toDp()) }
    }
  ) {
    PlatformMap(content, cameraState, onEvent, gestures, Modifier.matchParentSize())
    content.markers.forEach { marker ->
      key(marker.id) {
        MarkerSlot(marker, cameraState, onEvent, gesturesEnabled) { markerContent(marker) }
      }
    }
    val attribution = content.basemap.attribution
    if (attribution.isNotEmpty()) {
      BasicText(
        attribution,
        Modifier.align(Alignment.BottomEnd)
          .background(Color.White.copy(alpha = 0.7f))
          .padding(horizontal = 4.dp, vertical = 1.dp),
        style = TextStyle(color = Color(0xFF333333), fontSize = 10.sp),
      )
    }
  }
}

/**
 * The platform's native map. Implementations apply [diffMapContent] ops to the SDK, bind
 * [MapCameraState.renderer] while on screen, and report gestures through
 * [MapCameraState.updatePosition] and [onEvent], honoring [gestures].
 */
@Composable
internal expect fun PlatformMap(
  content: MapContent,
  cameraState: MapCameraState,
  onEvent: (MapEvent) -> Unit,
  gestures: MapGestureOptions,
  modifier: Modifier,
)

/** Places [content] at the marker's projected position; re-lays out, not recomposes, on pan. */
@Composable
private fun MarkerSlot(
  marker: MapMarker,
  cameraState: MapCameraState,
  onEvent: (MapEvent) -> Unit,
  tappable: Boolean,
  content: @Composable () -> Unit,
) {
  val currentOnEvent by rememberUpdatedState(onEvent)
  Box(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        // Reading the position subscribes this layout to camera changes, also when a native
        // renderer answers project() without touching snapshot state.
        cameraState.position
        val at = cameraState.project(marker.position)
        val x = (at.x + marker.offset.x).roundToPx() - placeable.width / 2
        val y =
          (at.y + marker.offset.y).roundToPx() -
            when (marker.anchor) {
              MarkerAnchor.CENTER -> placeable.height / 2
              MarkerAnchor.TOP -> 0
              MarkerAnchor.BOTTOM -> placeable.height
            }
        layout(placeable.width, placeable.height) { placeable.place(x, y) }
      }
      .then(
        if (tappable) {
          Modifier.pointerInput(marker.id) {
            detectTapGestures { currentOnEvent(MapEvent.MarkerTapped(marker.id)) }
          }
        } else {
          Modifier
        }
      )
  ) {
    content()
  }
}
