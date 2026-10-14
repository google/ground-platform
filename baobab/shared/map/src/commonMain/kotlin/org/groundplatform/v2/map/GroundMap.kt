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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A map that draws [content], follows [cameraState], and reports [MapEvent]s.
 *
 * The basemap and style layers are drawn by the platform's native map SDK. [MapContent.markers] are
 * drawn in Compose on top, using [markerContent], so marker UI is shared across platforms; markers
 * that [MapMarker.collides] hide while they overlap one drawn above them.
 *
 * @param gesturesEnabled whether the user can pan, zoom, and tap the map; turn it off for static
 *   previews, e.g. inside a scrolling list.
 * @param doubleTapZoomEnabled whether a double tap or double click zooms in. Turn it off where
 *   quick repeated taps mean something else, e.g. placing vertices while drawing; single taps are
 *   then reported without waiting for a possible second tap.
 * @param dragHandler takes over one-finger drags it claims, e.g. to move a vertex while editing.
 */
@Composable
fun GroundMap(
  content: MapContent,
  cameraState: MapCameraState,
  onEvent: (MapEvent) -> Unit,
  modifier: Modifier = Modifier,
  gesturesEnabled: Boolean = true,
  doubleTapZoomEnabled: Boolean = true,
  dragHandler: MapDragHandler? = null,
  markerContent: @Composable (MapMarker) -> Unit = {},
) {
  val density = LocalDensity.current
  // Renderers capture gesture callbacks once, so route the handler through state.
  val currentDragHandler by rememberUpdatedState(dragHandler)
  val hasDragHandler = dragHandler != null
  val gestures =
    remember(gesturesEnabled, doubleTapZoomEnabled, hasDragHandler) {
      MapGestureOptions(
        enabled = gesturesEnabled,
        doubleTapZoom = doubleTapZoomEnabled,
        dragHandler =
          if (hasDragHandler) MapDragHandler { currentDragHandler?.onDragStart(it) } else null,
      )
    }
  Box(
    modifier.clipToBounds().onSizeChanged {
      with(density) { cameraState.viewportSize = DpSize(it.width.toDp(), it.height.toDp()) }
    }
  ) {
    PlatformMap(content, cameraState, onEvent, gestures, Modifier.matchParentSize())
    MarkerLayer(
      content.markers,
      cameraState,
      onEvent,
      gesturesEnabled,
      markerContent,
      Modifier.matchParentSize(),
    )
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

/**
 * Places each marker's content at its projected position, showing only those [visibleMarkers]
 * picks; re-lays out, not recomposes, on pan and zoom.
 */
@Composable
private fun MarkerLayer(
  markers: List<MapMarker>,
  cameraState: MapCameraState,
  onEvent: (MapEvent) -> Unit,
  tappable: Boolean,
  markerContent: @Composable (MapMarker) -> Unit,
  modifier: Modifier,
) {
  val currentOnEvent by rememberUpdatedState(onEvent)
  Layout(
    content = {
      markers.forEach { marker ->
        key(marker.id) {
          Box(
            if (tappable) {
              Modifier.markerTap(marker.id) { currentOnEvent(MapEvent.MarkerTapped(it)) }
            } else {
              Modifier
            }
          ) {
            markerContent(marker)
          }
        }
      }
    },
    // Markers share pointer input only with their own siblings, so the layer must share too for
    // gestures over a marker to reach the PlatformMap beneath it.
    modifier = modifier.sharePointerInput(),
  ) { measurables, constraints ->
    val loose = constraints.copy(minWidth = 0, minHeight = 0)
    val placeables = measurables.map { it.measure(loose) }
    // Reading the position subscribes this layout to camera changes, also when a native renderer
    // answers project() without touching snapshot state.
    cameraState.position
    val bounds = markers.mapIndexed { i, marker ->
      val placeable = placeables[i]
      val at = cameraState.project(marker.position)
      val x = (at.x + marker.offset.x).roundToPx() - placeable.width / 2
      val y =
        (at.y + marker.offset.y).roundToPx() -
          when (marker.anchor) {
            MarkerAnchor.CENTER -> placeable.height / 2
            MarkerAnchor.TOP -> 0
            MarkerAnchor.BOTTOM -> placeable.height
          }
      IntRect(IntOffset(x, y), IntSize(placeable.width, placeable.height))
    }
    val viewport = IntSize(constraints.maxWidth, constraints.maxHeight)
    val visible = visibleMarkers(markers, bounds, viewport, MARKER_COLLISION_GAP.roundToPx())
    layout(viewport.width, viewport.height) {
      placeables.forEachIndexed { i, placeable ->
        if (visible[i]) placeable.place(bounds[i].topLeft)
      }
    }
  }
}

/** Minimum space kept between colliding markers that are both shown. */
private val MARKER_COLLISION_GAP = 4.dp

/**
 * Which [markers], laid out at [bounds] (px, relative to the map's top left), to show on a map of
 * size [viewport].
 *
 * Markers outside the viewport are hidden. Colliding markers ([MapMarker.collides]) are then placed
 * top down (last first), the way map SDKs place labels: each shows only if it stays [gap] px clear
 * of every colliding marker already placed, so crowded labels disappear when zoomed out and
 * reappear as zooming in separates them. Markers just off screen take part too, so panning doesn't
 * make labels pop in and out.
 */
internal fun visibleMarkers(
  markers: List<MapMarker>,
  bounds: List<IntRect>,
  viewport: IntSize,
  gap: Int,
): BooleanArray {
  val visible =
    BooleanArray(markers.size) { i ->
      val box = bounds[i]
      box.right > 0 && box.left < viewport.width && box.bottom > 0 && box.top < viewport.height
    }
  val nearby = IntRect(-viewport.width, -viewport.height, 2 * viewport.width, 2 * viewport.height)
  val placed = mutableListOf<IntRect>()
  for (i in markers.indices.reversed()) {
    if (!markers[i].collides || !bounds[i].overlaps(nearby)) continue
    val padded = bounds[i].inflate(gap)
    if (placed.any { it.overlaps(padded) }) visible[i] = false else placed += bounds[i]
  }
  return visible
}

/**
 * Reports taps on [markerId] to [onTap] while sharing pointer input with siblings, so pan, pinch,
 * and wheel-zoom gestures started over a marker still reach the sibling [PlatformMap] beneath it.
 */
private fun Modifier.markerTap(markerId: String, onTap: (String) -> Unit): Modifier =
  this.sharePointerInput().pointerInput(markerId) {
    awaitEachGesture {
      // Consume down on Initial so PlatformMap's tap detector (which runs before us on Main)
      // ignores it, while PlatformMap's pan/pinch loop (requireUnconsumed = false) still runs.
      awaitFirstDown(pass = PointerEventPass.Initial).consume()
      awaitPointerEvent(PointerEventPass.Final)
      waitForUpOrCancellation()?.let {
        it.consume()
        onTap(markerId)
      }
    }
  }

/** Lets hits on a marker pass through to the sibling [PlatformMap] beneath it. */
private fun Modifier.sharePointerInput(): Modifier = this then SharePointerInputElement

private data object SharePointerInputElement : ModifierNodeElement<SharePointerInputNode>() {
  override fun create() = SharePointerInputNode()

  override fun update(node: SharePointerInputNode) {}
}

private class SharePointerInputNode : Modifier.Node(), PointerInputModifierNode {
  override fun sharePointerInputWithSiblings(): Boolean = true

  override fun onPointerEvent(
    pointerEvent: PointerEvent,
    pass: PointerEventPass,
    bounds: IntSize,
  ) {}

  override fun onCancelPointerInput() {}
}
