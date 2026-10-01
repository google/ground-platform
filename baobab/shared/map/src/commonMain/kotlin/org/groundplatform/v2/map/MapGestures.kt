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

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import kotlin.math.abs
import kotlin.math.log2
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop

/** How long the camera must be still before [MapEvent.CameraIdle] is reported. */
private const val IDLE_DELAY_MS = 250L

internal const val MIN_ZOOM = 0.0
internal const val MAX_ZOOM = 22.0

/** Zoom levels per wheel notch. */
private const val WHEEL_ZOOM_STEP = 0.35

/**
 * Pan, pinch, and wheel zoom that drive [cameraState] directly, plus taps reported to [onTap].
 *
 * Used by renderers whose native map is display-only (web, Android) or absent (preview), so
 * gestures behave the same everywhere and [MapCameraState] stays the single source of truth for the
 * camera. One-finger drags go to [MapGestureOptions.dragHandler] first. [onTap] and the drag
 * handler are captured once per [cameraState], so they should read changing values through state.
 */
internal fun Modifier.mapGestures(
  cameraState: MapCameraState,
  options: MapGestureOptions = MapGestureOptions(),
  onTap: (DpOffset) -> Unit,
): Modifier {
  if (!options.enabled) return this
  val dragHandler = options.dragHandler
  return pointerInput(cameraState, dragHandler) {
      // A detectTransformGestures loop that can hand a one-finger drag to the app instead.
      awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        var claimed: MapDrag? = null
        var pastSlop = false
        var totalPan = Offset.Zero
        var totalZoom = 1f
        while (true) {
          val event = awaitPointerEvent()
          if (event.changes.any { it.isConsumed }) break
          val drag = claimed
          if (drag != null) {
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null || !change.pressed) break
            drag.onDrag(change.position.toDpOffset(this))
            change.consume()
            continue
          }
          val zoomChange = event.calculateZoom()
          val panChange = event.calculatePan()
          if (!pastSlop) {
            totalZoom *= zoomChange
            totalPan += panChange
            val zoomMotion = abs(1 - totalZoom) * event.calculateCentroidSize(useCurrent = false)
            if (zoomMotion > slop || totalPan.getDistance() > slop) {
              pastSlop = true
              if (dragHandler != null && event.changes.count { it.pressed } == 1) {
                val started = dragHandler.onDragStart(down.position.toDpOffset(this))
                if (started != null) {
                  claimed = started
                  event.changes
                    .firstOrNull { it.id == down.id }
                    ?.let {
                      started.onDrag(it.position.toDpOffset(this))
                      it.consume()
                    }
                  continue
                }
              }
            }
          }
          if (pastSlop) {
            val centroid = event.calculateCentroid(useCurrent = false)
            if (centroid != Offset.Unspecified && (zoomChange != 1f || panChange != Offset.Zero)) {
              transformCamera(cameraState, centroid.toDpOffset(this), panChange, zoomChange)
            }
            event.changes.forEach { if (it.positionChanged()) it.consume() }
          }
          if (event.changes.none { it.pressed }) break
        }
        claimed?.onDragEnd()
      }
    }
    .pointerInput(cameraState) {
      // Mouse wheel / trackpad scroll zoom for desktop and web, around the cursor.
      awaitPointerEventScope {
        while (true) {
          val event = awaitPointerEvent()
          if (event.type != PointerEventType.Scroll) continue
          val change = event.changes.firstOrNull() ?: continue
          val dy = change.scrollDelta.y
          if (dy == 0f) continue
          cameraState.stopAnimation()
          val c = cameraState.position
          val zoomed = c.copy(zoom = (c.zoom - dy * WHEEL_ZOOM_STEP).coerceIn(MIN_ZOOM, MAX_ZOOM))
          cameraState.updatePosition(
            zoomAround(cameraState, zoomed, change.position.toDpOffset(this)),
            byGesture = true,
          )
          event.changes.forEach { it.consume() }
        }
      }
    }
    .pointerInput(cameraState) { detectTapGestures { onTap(it.toDpOffset(this)) } }
}

/** Applies one step of a pan/pinch gesture: zoom by [zoom] around [centroid], then pan by [pan]. */
private fun Density.transformCamera(
  cameraState: MapCameraState,
  centroid: DpOffset,
  pan: Offset,
  zoom: Float,
) {
  cameraState.stopAnimation()
  val c = cameraState.position
  val zoomed = c.copy(zoom = (c.zoom + log2(zoom.toDouble())).coerceIn(MIN_ZOOM, MAX_ZOOM))
  val panned = zoomAround(cameraState, zoomed, centroid)
  val viewportCenter =
    DpOffset(cameraState.viewportSize.width / 2, cameraState.viewportSize.height / 2)
  val newCenter =
    WebMercator.unproject(viewportCenter - pan.toDpOffset(this), panned, cameraState.viewportSize)
  cameraState.updatePosition(panned.copy(center = newCenter), byGesture = true)
}

/**
 * Reports [MapEvent.CameraIdle] once [cameraState] has been still for [IDLE_DELAY_MS], for
 * renderers without a native idle event.
 */
@Composable
internal fun ReportCameraIdle(cameraState: MapCameraState, onEvent: (MapEvent) -> Unit) {
  val currentOnEvent by rememberUpdatedState(onEvent)
  LaunchedEffect(cameraState) {
    snapshotFlow { cameraState.position }
      .drop(1)
      .collectLatest {
        delay(IDLE_DELAY_MS)
        currentOnEvent(MapEvent.CameraIdle(it, byGesture = cameraState.isMovingByGesture))
      }
  }
}

/** [zoomed] shifted so the point under [at] before zooming stays under it. */
private fun zoomAround(
  cameraState: MapCameraState,
  zoomed: CameraPosition,
  at: DpOffset,
): CameraPosition {
  val viewport = cameraState.viewportSize
  val anchor = WebMercator.unproject(at, cameraState.position, viewport)
  val anchorAfterZoom = WebMercator.project(anchor, zoomed, viewport)
  val viewportCenter = DpOffset(viewport.width / 2, viewport.height / 2)
  val center = WebMercator.unproject(viewportCenter + (anchorAfterZoom - at), zoomed, viewport)
  return zoomed.copy(center = center)
}

internal fun Offset.toDpOffset(density: Density) = with(density) { DpOffset(x.toDp(), y.toDp()) }
