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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.tan
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Where the map is looking. [bearing] is degrees clockwise from north; [pitch] is degrees from
 * straight down.
 */
@Immutable
data class CameraPosition(
  val center: LatLng,
  val zoom: Double,
  val bearing: Double = 0.0,
  val pitch: Double = 0.0,
)

/**
 * Hoisted, observable camera for a [GroundMap], in the style of maps-compose `CameraPositionState`.
 *
 * Both sides write to it: user gestures (reported by the platform renderer) and the app (through
 * [move], [animateTo], and [fitBounds]). Read [position] to observe the camera; use [project] and
 * [unproject] to place Compose overlays such as editing handles.
 */
@Stable
class MapCameraState(initial: CameraPosition) {
  /** The current camera. Updated on every frame of a gesture or animation. */
  var position: CameraPosition by mutableStateOf(initial)
    private set

  /** Size of the map viewport; zero until the map is laid out. */
  var viewportSize: DpSize by mutableStateOf(DpSize.Zero)
    internal set

  /** Whether the most recent change to [position] came from a user gesture. */
  var isMovingByGesture: Boolean by mutableStateOf(false)
    private set

  /** Bound by the platform renderer while it is on screen; `null` falls back to Mercator math. */
  internal var renderer: MapRenderer? = null

  /** The running [animateTo] without a renderer; cancelled by gestures and newer moves. */
  private var animation: Job? = null

  /** Jumps to [target] without animation. */
  fun move(target: CameraPosition) {
    stopAnimation()
    renderer?.moveTo(target)
    updatePosition(target, byGesture = false)
  }

  /**
   * Animates to [target], suspending until the animation ends or is interrupted by a gesture or
   * another camera move.
   */
  suspend fun animateTo(target: CameraPosition, durationMs: Int = DEFAULT_ANIMATION_MS) {
    val r = renderer
    if (r != null) {
      r.animateTo(target, durationMs)
      return
    }
    stopAnimation()
    if (durationMs <= 0 || coroutineContext[MonotonicFrameClock] == null) {
      updatePosition(target, byGesture = false)
      return
    }
    coroutineScope {
      val job = launch {
        WebMercator.animate(position, target, durationMs) { updatePosition(it, false) }
      }
      animation = job
      job.join()
    }
  }

  /** Stops a running [animateTo], leaving the camera where it is. */
  internal fun stopAnimation() {
    animation?.cancel()
    animation = null
  }

  /**
   * Fits [bounds] into the viewport minus [padding], zooming in no further than [maxZoom], and
   * resets the bearing to north. Returns the resulting camera.
   */
  suspend fun fitBounds(
    bounds: LngLatBounds,
    padding: MapInsets = MapInsets.Zero,
    maxZoom: Double = DEFAULT_MAX_FIT_ZOOM,
    durationMs: Int = DEFAULT_ANIMATION_MS,
  ): CameraPosition {
    val target = WebMercator.cameraForBounds(bounds, viewportSize, padding, maxZoom)
    animateTo(target, durationMs)
    return target
  }

  /**
   * Moves [point] to the middle of the viewport minus [padding], keeping the zoom and bearing.
   * Returns the resulting camera.
   */
  suspend fun centerOn(
    point: LatLng,
    padding: MapInsets = MapInsets.Zero,
    durationMs: Int = DEFAULT_ANIMATION_MS,
  ): CameraPosition {
    val target = WebMercator.cameraCentering(point, position, padding)
    animateTo(target, durationMs)
    return target
  }

  /** Screen position of [point], relative to the map's top-left corner. */
  fun project(point: LatLng): DpOffset =
    renderer?.project(point) ?: WebMercator.project(point, position, viewportSize)

  /** Geographic position under [offset], relative to the map's top-left corner. */
  fun unproject(offset: DpOffset): LatLng =
    renderer?.unproject(offset) ?: WebMercator.unproject(offset, position, viewportSize)

  /** Called by renderers whenever the camera changes, including on every gesture frame. */
  internal fun updatePosition(target: CameraPosition, byGesture: Boolean) {
    isMovingByGesture = byGesture
    position = target
  }

  companion object {
    const val DEFAULT_ANIMATION_MS = 600
    const val DEFAULT_MAX_FIT_ZOOM = 17.0
  }
}

/** Remembers a [MapCameraState] starting at [initial]. */
@Composable
fun rememberMapCameraState(initial: CameraPosition): MapCameraState =
  rememberSaveable(saver = cameraStateSaver()) { MapCameraState(initial) }

private fun cameraStateSaver() =
  androidx.compose.runtime.saveable.Saver<MapCameraState, List<Double>>(
    save = {
      val p = it.position
      listOf(p.center.latitude, p.center.longitude, p.zoom, p.bearing, p.pitch)
    },
    restore = { MapCameraState(CameraPosition(LatLng(it[0], it[1]), it[2], it[3], it[4])) },
  )

/**
 * Camera operations a platform renderer implements. Renderers that don't override [project] and
 * [unproject] get the pitch-free [WebMercator] math, which matches Mapbox at zero pitch.
 */
internal interface MapRenderer {
  fun moveTo(target: CameraPosition)

  suspend fun animateTo(target: CameraPosition, durationMs: Int)

  fun project(point: LatLng): DpOffset?

  fun unproject(offset: DpOffset): LatLng?
}

/** Spherical Web Mercator (EPSG:3857) with Mapbox's 512 dp world size at zoom 0. Ignores pitch. */
internal object WebMercator {
  const val TILE_SIZE = 512.0
  const val MAX_LATITUDE = 85.05112878

  private fun worldSize(zoom: Double) = TILE_SIZE * 2.0.pow(zoom)

  /** World coordinates in dp at [zoom]; origin at the north-west corner. */
  fun toWorld(point: LatLng, zoom: Double): Pair<Double, Double> {
    val size = worldSize(zoom)
    val lat = point.latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE) * PI / 180
    val x = (point.longitude + 180) / 360 * size
    val y = (1 - ln(tan(PI / 4 + lat / 2)) / PI) / 2 * size
    return x to y
  }

  fun fromWorld(x: Double, y: Double, zoom: Double): LatLng {
    val size = worldSize(zoom)
    val lng = x / size * 360 - 180
    val n = PI * (1 - 2 * y / size)
    val lat = atan(sinh(n)) * 180 / PI
    return LatLng(lat.coerceIn(-MAX_LATITUDE, MAX_LATITUDE), ((lng + 540) % 360) - 180)
  }

  fun project(point: LatLng, camera: CameraPosition, viewport: DpSize): DpOffset {
    val (px, py) = toWorld(point, camera.zoom)
    val (cx, cy) = toWorld(camera.center, camera.zoom)
    val a = -camera.bearing * PI / 180
    val dx = px - cx
    val dy = py - cy
    val rx = dx * cos(a) - dy * sin(a)
    val ry = dx * sin(a) + dy * cos(a)
    return DpOffset(
      (viewport.width.value / 2 + rx).toFloat().dp,
      (viewport.height.value / 2 + ry).toFloat().dp,
    )
  }

  fun unproject(offset: DpOffset, camera: CameraPosition, viewport: DpSize): LatLng {
    val sx = offset.x.value - viewport.width.value / 2.0
    val sy = offset.y.value - viewport.height.value / 2.0
    val a = camera.bearing * PI / 180
    val dx = sx * cos(a) - sy * sin(a)
    val dy = sx * sin(a) + sy * cos(a)
    val (cx, cy) = toWorld(camera.center, camera.zoom)
    return fromWorld(cx + dx, cy + dy, camera.zoom)
  }

  fun cameraForBounds(
    bounds: LngLatBounds,
    viewport: DpSize,
    padding: MapInsets,
    maxZoom: Double,
  ): CameraPosition {
    val (x0, y0) = toWorld(LatLng(bounds.north, bounds.west), 0.0)
    val (x1, y1) = toWorld(LatLng(bounds.south, bounds.east), 0.0)
    val availW = (viewport.width - padding.left - padding.right).value.toDouble().coerceAtLeast(1.0)
    val availH =
      (viewport.height - padding.top - padding.bottom).value.toDouble().coerceAtLeast(1.0)
    val spanX = x1 - x0
    val spanY = y1 - y0
    val zoom =
      if (spanX <= 0 && spanY <= 0) {
        maxZoom
      } else {
        val scale =
          minOf(
            if (spanX > 0) availW / spanX else Double.MAX_VALUE,
            if (spanY > 0) availH / spanY else Double.MAX_VALUE,
          )
        log2(scale).coerceIn(0.0, maxZoom)
      }
    // Shift the center so the bounds sit in the middle of the padded area, not the whole viewport.
    val (bx, by) = toWorld(bounds.center, zoom)
    val shiftX = (padding.left.value - padding.right.value) / 2.0
    val shiftY = (padding.top.value - padding.bottom.value) / 2.0
    return CameraPosition(center = fromWorld(bx - shiftX, by - shiftY, zoom), zoom = zoom)
  }

  /** [camera] moved so [point] sits in the middle of the viewport minus [padding]. */
  fun cameraCentering(point: LatLng, camera: CameraPosition, padding: MapInsets): CameraPosition {
    val (px, py) = toWorld(point, camera.zoom)
    // The padded area's middle is this far from the viewport's middle, on screen.
    val sx = (padding.left.value - padding.right.value) / 2.0
    val sy = (padding.top.value - padding.bottom.value) / 2.0
    val a = camera.bearing * PI / 180
    val wx = sx * cos(a) - sy * sin(a)
    val wy = sx * sin(a) + sy * cos(a)
    return camera.copy(center = fromWorld(px - wx, py - wy, camera.zoom))
  }

  /**
   * Eases from [from] to [to] over [durationMs], calling [onFrame] once per frame. Center moves
   * linearly in world space at a fixed zoom so the path is straight on screen; bearing takes the
   * short way round.
   */
  suspend fun animate(
    from: CameraPosition,
    to: CameraPosition,
    durationMs: Int,
    onFrame: (CameraPosition) -> Unit,
  ) {
    val (fx, fy) = toWorld(from.center, 0.0)
    val (tx0, ty) = toWorld(to.center, 0.0)
    // Cross the antimeridian the short way.
    val world = TILE_SIZE
    val tx = tx0 + if (tx0 - fx > world / 2) -world else if (fx - tx0 > world / 2) world else 0.0
    val bearingDelta = ((to.bearing - from.bearing + 540) % 360) - 180
    val start = withFrameNanos { it }
    while (true) {
      val elapsedMs = (withFrameNanos { it } - start) / 1_000_000.0
      val t = (elapsedMs / durationMs).coerceIn(0.0, 1.0)
      val e = easeInOut(t)
      onFrame(
        CameraPosition(
          center = fromWorld(fx + (tx - fx) * e, fy + (ty - fy) * e, 0.0),
          zoom = from.zoom + (to.zoom - from.zoom) * e,
          bearing = from.bearing + bearingDelta * e,
          pitch = from.pitch + (to.pitch - from.pitch) * e,
        )
      )
      if (t >= 1.0) break
    }
    onFrame(to)
  }

  private fun easeInOut(t: Double) = if (t < 0.5) 2 * t * t else 1 - (-2 * t + 2).pow(2) / 2
}
