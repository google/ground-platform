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

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.log2
import kotlinx.coroutines.await

/** Tap tolerance for features, in CSS px. */
private const val TAP_RADIUS_PX = 6.0

/**
 * Compose passes `WheelEvent.deltaY` through in CSS px, and browsers report about 100 px per mouse
 * wheel notch.
 */
internal actual const val SCROLL_DELTA_PER_WHEEL_NOTCH = 100f

/**
 * Mapbox GL JS renderer.
 *
 * The browser can't host a DOM element inside the Compose canvas, so the map lives in a `div`
 * underneath it, kept aligned with this composable's layout every frame, and the canvas is cleared
 * to transparent over it. The map is display-only: gestures are handled in Compose by [mapGestures]
 * and [MapCameraState] is the source of truth, which the map follows with `jumpTo`. Mapbox and
 * [WebMercator] share the 512 px world size, so markers and projections line up.
 *
 * Falls back to [PreviewMap] when the page hasn't loaded Mapbox GL JS.
 */
@Composable
internal actual fun PlatformMap(
  content: MapContent,
  cameraState: MapCameraState,
  onEvent: (MapEvent) -> Unit,
  gestures: MapGestureOptions,
  modifier: Modifier,
) {
  val available = remember { isMapboxGlAvailable() }
  if (!available) {
    PreviewMap(content, cameraState, onEvent, gestures, modifier)
    return
  }
  val currentContent by rememberUpdatedState(content)
  val currentOnEvent by rememberUpdatedState(onEvent)
  val density = LocalDensity.current.density
  val renderer = remember { MapboxWebRenderer(content.basemap, cameraState.position) }
  DisposableEffect(renderer) { onDispose { renderer.destroy() } }

  // Content: apply the diff from what the map shows to the latest content, one op at a time.
  LaunchedEffect(renderer) { snapshotFlow { currentContent }.collect { renderer.show(it) } }

  // Layout: follow this composable's window position, including scrolling ancestors. The frame loop
  // catches moves that don't re-dispatch positioning; onGloballyPositioned below aligns the map in
  // the same frame as the layout change (the loop runs at frame start, before layout, so on its own
  // it would trail a resizing layout by one frame and expose the page behind the canvas hole).
  LaunchedEffect(renderer, density) {
    while (true) {
      withFrameNanos {}
      renderer.syncBounds(density)
    }
  }

  // Camera: the map follows the hoisted state.
  LaunchedEffect(renderer, density) {
    snapshotFlow { cameraState.position }.collect { renderer.jumpTo(it, density) }
  }

  ReportCameraIdle(cameraState, onEvent)

  Canvas(
    modifier
      .onGloballyPositioned {
        renderer.coordinates = it
        renderer.syncBounds(density)
      }
      .mapGestures(cameraState, gestures) { tap ->
        val at = cameraState.unproject(tap)
        val hit =
          renderer.featureAt(tap.x.value * density, tap.y.value * density, currentContent, at)
        currentOnEvent(hit ?: MapEvent.BackgroundTapped(at))
      }
  ) {
    // Synchronize bounds and camera during the Compose draw phase so Mapbox repaints in the
    // exact same animation frame as any Compose Canvas overlays above it.
    renderer.syncBounds(density)
    renderer.jumpTo(cameraState.position, density)
    // Punch a hole in the Compose canvas so the map beneath shows through.
    drawRect(Color.Transparent, blendMode = BlendMode.Clear)
  }
}

/** Owns one Mapbox GL JS map and keeps it in sync with [MapContent], layout, and camera. */
private class MapboxWebRenderer(initialBasemap: Basemap, initialCamera: CameraPosition) {
  private val host =
    createMapboxHost(
      MapboxStyleSpec.style(initialBasemap),
      initialCamera.center.longitude,
      initialCamera.center.latitude,
      initialCamera.zoom,
      initialCamera.bearing,
      initialCamera.pitch,
    )
  private var styleBasemap = initialBasemap
  /** Whether the current style has the user-location source and layers yet. */
  private var styleDecorated = false
  private var shown: MapContent? = null
  private val layers = mutableMapOf<String, MapLayer>()
  private var lastBounds: List<Double>? = null
  private var destroyed = false
  /** The last camera and Compose density jumped to, to re-apply when [cssPerPx] changes. */
  private var lastCamera: CameraPosition? = initialCamera
  private var lastDensity = 1f
  /** The CSS px per Compose px the camera was last applied with. */
  private var appliedCssPerPx = 0.0

  /**
   * CSS px per Compose px: measured on Compose's canvas (see [cssPxPerComposePx]), falling back to
   * `1 / density` (or `1 / devicePixelRatio`) when it can't be measured yet.
   */
  private fun cssPerPx(density: Float = lastDensity): Double =
    cssPxPerComposePx().takeIf { it.isFinite() && it > 0 }
      ?: (1.0 / density.toDouble()).takeIf { it.isFinite() && it > 0 }
      ?: (1.0 / devicePixelRatio())

  var coordinates: LayoutCoordinates? = null

  suspend fun show(content: MapContent) {
    for (op in diffMapContent(shown, content)) {
      if (destroyed) return
      if (op !is MapOp.SetBasemap) {
        if (!mapboxIsStyleReady(host)) {
          mapboxStyleReady(host).await<JsAny?>()
        }
        if (destroyed) return
        if (!styleDecorated) {
          styleDecorated = true
          addUserLocationLayers()
        }
      }
      try {
        apply(op)
      } catch (e: Throwable) {
        // A bad layer shouldn't take the app down; the rest of the content still renders.
        println("GroundMap: failed to apply $op: ${e.message}")
      }
    }
    shown = content
  }

  private fun addUserLocationLayers() {
    try {
      mapboxAddSource(
        host,
        MapboxStyleSpec.USER_LOCATION_SOURCE,
        MapboxStyleSpec.userLocationSource().toString(),
      )
      MapboxStyleSpec.userLocationLayers().forEach { mapboxAddLayer(host, it.toString(), null, "") }
    } catch (e: Throwable) {
      println("GroundMap: failed to add the user-location layers: ${e.message}")
    }
  }

  private suspend fun apply(op: MapOp) {
    when (op) {
      is MapOp.SetBasemap -> {
        layers.clear()
        if (op.basemap != styleBasemap) {
          styleBasemap = op.basemap
          styleDecorated = false
          mapboxSetStyle(host, MapboxStyleSpec.style(op.basemap))
        }
      }
      is MapOp.AddIcon -> mapboxAddIcon(host, op.icon.id, op.icon.svg).await<JsAny?>()
      is MapOp.RemoveIcon -> mapboxRemoveIcon(host, op.iconId)
      is MapOp.AddSource ->
        mapboxAddSource(
          host,
          op.source.id,
          MapboxStyleSpec.geoJsonSource(MapboxStyleSpec.geoJson(op.source.features)).toString(),
        )
      is MapOp.SetSourceData ->
        mapboxSetSourceData(
          host,
          op.source.id,
          MapboxStyleSpec.geoJson(op.source.features).toString(),
        )
      is MapOp.RemoveSource -> mapboxRemoveSource(host, op.sourceId)
      is MapOp.AddLayer -> {
        mapboxAddLayer(
          host,
          MapboxStyleSpec.layer(op.layer).toString(),
          op.beforeLayerId,
          MapboxStyleSpec.USER_LOCATION_HALO_LAYER,
        )
        layers[op.layer.id] = op.layer
      }
      is MapOp.UpdateLayer -> {
        val old = layers[op.layer.id]
        if (old != null && old::class == op.layer::class) {
          mapboxUpdateLayer(
            host,
            MapboxStyleSpec.layer(old).toString(),
            MapboxStyleSpec.layer(op.layer).toString(),
          )
        } else {
          // A layer can't change type in place; re-add it where it was.
          val order = shown?.layers?.map { it.id }.orEmpty()
          val above = order.getOrNull(order.indexOf(op.layer.id) + 1)
          mapboxRemoveLayer(host, op.layer.id)
          mapboxAddLayer(
            host,
            MapboxStyleSpec.layer(op.layer).toString(),
            above,
            MapboxStyleSpec.USER_LOCATION_HALO_LAYER,
          )
        }
        layers[op.layer.id] = op.layer
      }
      is MapOp.RemoveLayer -> {
        mapboxRemoveLayer(host, op.layerId)
        layers.remove(op.layerId)
      }
      is MapOp.SetUserLocation ->
        mapboxSetSourceData(
          host,
          MapboxStyleSpec.USER_LOCATION_SOURCE,
          MapboxStyleSpec.userLocationGeoJson(op.location).toString(),
        )
    }
  }

  /** Aligns the map's elements with [coordinates], clipped to what ancestors leave visible. */
  fun syncBounds(density: Float = lastDensity) {
    if (destroyed) return
    lastDensity = density
    val px = cssPerPx(density)
    val coords = coordinates
    val bounds =
      if (coords == null || !coords.isAttached) {
        HIDDEN
      } else {
        val position = coords.positionInWindow()
        val clip: Rect = coords.boundsInWindow()
        val visible = if (clip.width > 0f && clip.height > 0f) 1.0 else 0.0
        listOf(
          visible,
          clip.left * px,
          clip.top * px,
          clip.width * px,
          clip.height * px,
          (position.x - clip.left) * px,
          (position.y - clip.top) * px,
          coords.size.width * px,
          coords.size.height * px,
        )
      }
    if (bounds != lastBounds) {
      lastBounds = bounds
      mapboxSetBounds(
        host,
        bounds[0] > 0,
        bounds[1],
        bounds[2],
        bounds[3],
        bounds[4],
        bounds[5],
        bounds[6],
        bounds[7],
        bounds[8],
      )
    }
    // The scale changes without the camera moving when the browser zoom or screen changes, which
    // would leave the map drawn at a different scale than the Compose overlays above it.
    lastCamera?.let { camera -> if (px != appliedCssPerPx) jumpTo(camera, density) }
  }

  /**
   * Jumps the map to [camera]. When a dp (Compose's [density] Compose px) isn't one CSS px, the
   * zoom is offset to keep the map and the Compose overlays at the same scale.
   */
  fun jumpTo(camera: CameraPosition, density: Float) {
    if (destroyed) return
    val cssPerPx = cssPerPx(density)
    if (camera == lastCamera && density == lastDensity && cssPerPx == appliedCssPerPx) return
    lastCamera = camera
    lastDensity = density
    appliedCssPerPx = cssPerPx
    val cssPxPerDp = density * cssPerPx
    mapboxJumpTo(
      host,
      camera.center.longitude,
      camera.center.latitude,
      (camera.zoom + log2(cssPxPerDp)).coerceAtLeast(0.0),
      camera.bearing,
      camera.pitch,
    )
  }

  /**
   * The topmost content feature at ([xPx], [yPx]) in physical px from the map's corner, reported as
   * tapped at [at].
   */
  fun featureAt(xPx: Float, yPx: Float, content: MapContent, at: LatLng): MapEvent.FeatureTapped? {
    if (destroyed) return null
    val scale = cssPerPx(lastDensity)
    val hit =
      mapboxQueryFeature(
        host,
        xPx * scale,
        yPx * scale,
        TAP_RADIUS_PX,
        content.layers.joinToString("\n") { it.id },
        MapboxStyleSpec.FEATURE_ID_PROPERTY,
      )
    if (hit.isEmpty()) return null
    val (layerId, featureId) = hit.split('\n', limit = 2)
    return MapEvent.FeatureTapped(layerId, featureId, at)
  }

  fun destroy() {
    if (destroyed) return
    destroyed = true
    mapboxDestroy(host)
  }

  private companion object {
    val HIDDEN = listOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
  }
}
