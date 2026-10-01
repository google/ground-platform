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

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlin.coroutines.resume
import kotlin.math.ceil
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM
import org.jetbrains.skia.svg.SVGLengthUnit
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIScreen

/** Tap tolerance for features, in points. */
private const val TAP_RADIUS_PT = 6.0

/**
 * Mapbox Maps SDK for iOS renderer, through the Swift [MapboxIosView] registered in [GroundMapIos].
 *
 * Works like the web and Android renderers: the native map is display-only below a Compose gesture
 * layer ([mapGestures]), [MapCameraState] is the source of truth, and the map follows it. Compose
 * measures in points on iOS and Mapbox uses 512-point tiles, the same world as [WebMercator], so
 * zoom passes through unchanged and Compose markers line up.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal actual fun PlatformMap(
  content: MapContent,
  cameraState: MapCameraState,
  onEvent: (MapEvent) -> Unit,
  gestures: MapGestureOptions,
  modifier: Modifier,
) {
  val native = remember { GroundMapIos.viewFactory?.create() }
  if (native == null) {
    PreviewMap(content, cameraState, onEvent, gestures, modifier)
    return
  }
  val currentContent by rememberUpdatedState(content)
  val currentOnEvent by rememberUpdatedState(onEvent)
  val scope = rememberCoroutineScope()
  val renderer = remember(native) { MapboxIosRenderer(native, content.basemap) }
  DisposableEffect(renderer) { onDispose { renderer.destroy() } }

  // Content: apply the diff from what the map shows to the latest content, one op at a time.
  LaunchedEffect(renderer) { snapshotFlow { currentContent }.collect { renderer.show(it) } }

  // Camera: the map follows the hoisted state.
  LaunchedEffect(renderer) { snapshotFlow { cameraState.position }.collect { renderer.jumpTo(it) } }

  ReportCameraIdle(cameraState, onEvent)

  Box(modifier) {
    UIKitView(
      factory = { native.view },
      modifier = Modifier.matchParentSize(),
      // Touches go to Compose; the map is display-only.
      properties = UIKitInteropProperties(interactionMode = null),
    )
    Box(
      Modifier.matchParentSize().mapGestures(cameraState, gestures) { tap ->
        val x = tap.x.value.toDouble()
        val y = tap.y.value.toDouble()
        if (renderer.tapOrnament(x, y)) return@mapGestures
        val at = cameraState.unproject(tap)
        scope.launch {
          val hit = renderer.featureAt(x, y, currentContent, at)
          currentOnEvent(hit ?: MapEvent.BackgroundTapped(at))
        }
      }
    )
  }
}

/** Keeps one [MapboxIosView] in sync with [MapContent] and the camera. */
private class MapboxIosRenderer(private val native: MapboxIosView, initialBasemap: Basemap) {
  private var styleReady = CompletableDeferred<Unit>()
  private var styleBasemap: Basemap? = null
  private var shown: MapContent? = null
  private val layers = mutableMapOf<String, MapLayer>()
  private var destroyed = false

  init {
    loadBasemap(initialBasemap)
  }

  private fun loadBasemap(basemap: Basemap) {
    styleBasemap = basemap
    val ready = CompletableDeferred<Unit>()
    styleReady = ready
    native.loadStyle(MapboxStyleSpec.style(basemap)) {
      if (!destroyed && styleReady === ready) {
        addUserLocationLayers()
        ready.complete(Unit)
      }
    }
  }

  suspend fun show(content: MapContent) {
    for (op in diffMapContent(shown, content)) {
      if (destroyed) return
      if (op is MapOp.SetBasemap) {
        layers.clear()
        if (op.basemap != styleBasemap) loadBasemap(op.basemap)
        continue
      }
      styleReady.await()
      if (destroyed) return
      apply(op, content)
    }
    shown = content
  }

  private fun apply(op: MapOp, target: MapContent) {
    when (op) {
      is MapOp.SetBasemap -> error("handled in show()")
      is MapOp.AddIcon -> {
        val scale = UIScreen.mainScreen.scale
        rasterizeSvg(op.icon.svg, scale)?.let { native.addImage(op.icon.id, it, scale) }
      }
      is MapOp.RemoveIcon -> native.removeImage(op.iconId)
      is MapOp.AddSource ->
        native.addSource(
          op.source.id,
          MapboxStyleSpec.geoJsonSource(MapboxStyleSpec.geoJson(op.source.features)).toString(),
        )
      is MapOp.SetSourceData ->
        native.setSourceData(op.source.id, MapboxStyleSpec.geoJson(op.source.features).toString())
      is MapOp.RemoveSource -> native.removeSource(op.sourceId)
      is MapOp.AddLayer -> {
        addLayer(op.layer, op.beforeLayerId)
        layers[op.layer.id] = op.layer
      }
      is MapOp.UpdateLayer -> {
        // Re-adding covers type changes and properties that were removed, which a property update
        // would leave behind. The layer goes back below its upper neighbour in the target order.
        val order = target.layers.map { it.id }
        val above = order.getOrNull(order.indexOf(op.layer.id) + 1)
        if (native.hasLayer(op.layer.id)) native.removeLayer(op.layer.id)
        addLayer(op.layer, above)
        layers[op.layer.id] = op.layer
      }
      is MapOp.RemoveLayer -> {
        if (native.hasLayer(op.layerId)) native.removeLayer(op.layerId)
        layers.remove(op.layerId)
      }
      is MapOp.SetUserLocation ->
        native.setSourceData(
          MapboxStyleSpec.USER_LOCATION_SOURCE,
          MapboxStyleSpec.userLocationGeoJson(op.location).toString(),
        )
    }
  }

  /** Adds [layer] below [beforeLayerId] if it exists, else below the user-location layers. */
  private fun addLayer(layer: MapLayer, beforeLayerId: String?) {
    val below =
      beforeLayerId?.takeIf(native::hasLayer)
        ?: MapboxStyleSpec.USER_LOCATION_HALO_LAYER.takeIf(native::hasLayer)
    native.addLayer(MapboxStyleSpec.layer(layer).toString(), below)
  }

  private fun addUserLocationLayers() {
    native.addSource(
      MapboxStyleSpec.USER_LOCATION_SOURCE,
      MapboxStyleSpec.userLocationSource().toString(),
    )
    MapboxStyleSpec.userLocationLayers().forEach { native.addLayer(it.toString(), null) }
  }

  fun jumpTo(camera: CameraPosition) {
    if (destroyed) return
    native.setCamera(
      camera.center.latitude,
      camera.center.longitude,
      camera.zoom,
      camera.bearing,
      camera.pitch,
    )
  }

  fun tapOrnament(x: Double, y: Double): Boolean = !destroyed && native.tapOrnament(x, y)

  /** The topmost content feature near ([x], [y]) in points, reported as tapped at [at]. */
  suspend fun featureAt(
    x: Double,
    y: Double,
    content: MapContent,
    at: LatLng,
  ): MapEvent.FeatureTapped? {
    if (destroyed || !styleReady.isCompleted) return null
    val layerIds = content.layers.map { it.id }.filter { layers.containsKey(it) }
    if (layerIds.isEmpty()) return null
    return suspendCancellableCoroutine { continuation ->
      native.queryFeature(x, y, TAP_RADIUS_PT, layerIds, MapboxStyleSpec.FEATURE_ID_PROPERTY) {
        layerId,
        featureId ->
        val hit =
          if (layerId != null && featureId != null) {
            MapEvent.FeatureTapped(layerId, featureId, at)
          } else {
            null
          }
        if (continuation.isActive) continuation.resume(hit)
      }
    }
  }

  fun destroy() {
    if (destroyed) return
    destroyed = true
    styleReady.cancel()
    native.destroy()
  }
}

private const val DEFAULT_ICON_PT = 24f

/**
 * Renders [svg] at its natural size in points, at [scale] pixels per point, as PNG. Uses Skia,
 * which Compose already ships on iOS, since UIKit has no SVG renderer.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun rasterizeSvg(svg: String, scale: Double): NSData? {
  val dom = SVGDOM(Data.makeFromBytes(svg.encodeToByteArray()))
  val root = dom.root ?: return null
  val viewBox = root.viewBox
  val width =
    root.width.takeIf { it.unit != SVGLengthUnit.PERCENTAGE && it.value > 0 }?.value
      ?: viewBox?.width
      ?: DEFAULT_ICON_PT
  val height =
    root.height.takeIf { it.unit != SVGLengthUnit.PERCENTAGE && it.value > 0 }?.value
      ?: viewBox?.height
      ?: DEFAULT_ICON_PT
  dom.setContainerSize(width, height)
  val surface =
    Surface.makeRasterN32Premul(
      ceil(width * scale).toInt().coerceAtLeast(1),
      ceil(height * scale).toInt().coerceAtLeast(1),
    )
  surface.canvas.scale(scale.toFloat(), scale.toFloat())
  dom.render(surface.canvas)
  val png = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)?.bytes ?: return null
  return png.usePinned { NSData.create(bytes = it.addressOf(0), length = png.size.toULong()) }
}
