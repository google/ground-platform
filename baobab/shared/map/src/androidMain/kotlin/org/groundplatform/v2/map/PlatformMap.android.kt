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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import com.caverock.androidsvg.SVG
import com.mapbox.bindgen.Value
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.LayerPosition
import com.mapbox.maps.MapInitOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.RenderedQueryGeometry
import com.mapbox.maps.RenderedQueryOptions
import com.mapbox.maps.ScreenBox
import com.mapbox.maps.ScreenCoordinate
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.scalebar.scalebar
import kotlin.coroutines.resume
import kotlin.math.ceil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

private const val TAG = "GroundMap"

/** Tap tolerance for features, in dp. */
private const val TAP_RADIUS_DP = 6f

/**
 * Mapbox Maps SDK for Android renderer.
 *
 * Like the web renderer, the native map is display-only: gestures run in Compose ([mapGestures]),
 * [MapCameraState] is the source of truth, and the map follows it with `setCamera`. Mapbox measures
 * in density-independent pixels with 512-pixel tiles, the same world as [WebMercator], so zoom
 * passes through unchanged and Compose markers line up.
 *
 * The host app must provide a Mapbox access token (`MapboxOptions.accessToken`, or a
 * `mapbox_access_token` string resource). Without one, this falls back to [PreviewMap].
 */
@Composable
internal actual fun PlatformMap(
  content: MapContent,
  cameraState: MapCameraState,
  onEvent: (MapEvent) -> Unit,
  gestures: MapGestureOptions,
  modifier: Modifier,
) {
  val hasToken = remember { runCatching { MapboxOptions.accessToken }.getOrNull()?.isNotEmpty() }
  if (hasToken != true) {
    PreviewMap(content, cameraState, onEvent, gestures, modifier)
    return
  }
  val context = LocalContext.current
  val density = LocalDensity.current.density
  val currentContent by rememberUpdatedState(content)
  val currentOnEvent by rememberUpdatedState(onEvent)
  val scope = rememberCoroutineScope()
  val renderer =
    remember(context) { MapboxAndroidRenderer(context, content.basemap, cameraState.position) }
  DisposableEffect(renderer) { onDispose { renderer.destroy() } }

  // Content: apply the diff from what the map shows to the latest content, one op at a time.
  LaunchedEffect(renderer) { snapshotFlow { currentContent }.collect { renderer.show(it) } }

  // Camera: the map follows the hoisted state.
  LaunchedEffect(renderer) { snapshotFlow { cameraState.position }.collect { renderer.jumpTo(it) } }

  ReportCameraIdle(cameraState, onEvent)

  Box(modifier) {
    AndroidView(factory = { renderer.mapView }, modifier = Modifier.matchParentSize())
    Box(
      Modifier.matchParentSize().mapGestures(cameraState, gestures) { tap ->
        val xPx = tap.x.value * density
        val yPx = tap.y.value * density
        if (renderer.clickMapboxButtonAt(xPx, yPx, TAP_RADIUS_DP * density)) return@mapGestures
        val at = cameraState.unproject(tap)
        scope.launch {
          val hit = renderer.featureAt(xPx, yPx, TAP_RADIUS_DP * density, currentContent, at)
          currentOnEvent(hit ?: MapEvent.BackgroundTapped(at))
        }
      }
    )
  }
}

/** Owns one [MapView] and keeps it in sync with [MapContent] and the camera. */
private class MapboxAndroidRenderer(
  private val context: Context,
  initialBasemap: Basemap,
  initialCamera: CameraPosition,
) {
  val mapView =
    MapView(
      context,
      MapInitOptions(
        context,
        cameraOptions = initialCamera.toCameraOptions(),
        // Composes and clips like any other view, unlike a SurfaceView.
        textureView = true,
        // loadBasemap() loads the style, so it can wait for the user-location layers.
        styleUri = null,
      ),
    )

  private val map = mapView.mapboxMap
  private var style: Style? = null
  private var styleReady = CompletableDeferred<Style>()
  private var styleBasemap: Basemap? = null
  private var shown: MapContent? = null
  private val layers = mutableMapOf<String, MapLayer>()
  private var destroyed = false

  init {
    // Compose handles gestures. Mapbox's logo and attribution button (with the telemetry opt-out
    // its terms require) stay; GroundMap also draws the basemap's attribution text.
    mapView.gestures.updateSettings {
      scrollEnabled = false
      rotateEnabled = false
      pitchEnabled = false
      pinchToZoomEnabled = false
      doubleTapToZoomInEnabled = false
      doubleTouchToZoomOutEnabled = false
      quickZoomEnabled = false
    }
    mapView.compass.enabled = false
    mapView.scalebar.enabled = false
    loadBasemap(initialBasemap)
  }

  private fun loadBasemap(basemap: Basemap) {
    styleBasemap = basemap
    style = null
    val ready = CompletableDeferred<Style>()
    styleReady = ready
    map.loadStyle(MapboxStyleSpec.style(basemap)) { loaded ->
      if (destroyed || styleReady !== ready) return@loadStyle
      addUserLocationLayers(loaded)
      style = loaded
      ready.complete(loaded)
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
      val style = styleReady.await()
      if (destroyed) return
      try {
        apply(style, op, content)
      } catch (e: Exception) {
        // A bad layer shouldn't take the app down; the rest of the content still renders.
        Log.w(TAG, "Failed to apply $op", e)
      }
    }
    shown = content
  }

  private fun apply(style: Style, op: MapOp, target: MapContent) {
    when (op) {
      is MapOp.SetBasemap -> error("handled in show()")
      is MapOp.AddIcon -> style.addImage(op.icon.id, rasterize(op.icon.svg)).check("add icon")
      is MapOp.RemoveIcon ->
        if (style.hasStyleImage(op.iconId)) style.removeStyleImage(op.iconId).check("remove icon")
      is MapOp.AddSource ->
        style
          .addStyleSource(
            op.source.id,
            MapboxStyleSpec.geoJsonSource(MapboxStyleSpec.geoJson(op.source.features)).toValue(),
          )
          .check("add source")
      is MapOp.SetSourceData -> setSourceData(style, op.source.id, op.source.features)
      is MapOp.RemoveSource ->
        if (style.styleSourceExists(op.sourceId)) {
          style.removeStyleSource(op.sourceId).check("remove source")
        }
      is MapOp.AddLayer -> {
        addLayer(style, op.layer, op.beforeLayerId)
        layers[op.layer.id] = op.layer
      }
      is MapOp.UpdateLayer -> {
        // Re-adding covers type changes and properties that were removed, which a property update
        // would leave behind. The layer goes back below its upper neighbour in the target order.
        val order = target.layers.map { it.id }
        val above = order.getOrNull(order.indexOf(op.layer.id) + 1)
        if (style.styleLayerExists(op.layer.id)) style.removeStyleLayer(op.layer.id)
        addLayer(style, op.layer, above)
        layers[op.layer.id] = op.layer
      }
      is MapOp.RemoveLayer -> {
        if (style.styleLayerExists(op.layerId)) style.removeStyleLayer(op.layerId)
        layers.remove(op.layerId)
      }
      is MapOp.SetUserLocation ->
        style
          .setStyleSourceProperty(
            MapboxStyleSpec.USER_LOCATION_SOURCE,
            "data",
            MapboxStyleSpec.userLocationGeoJson(op.location).toValue(),
          )
          .check("set user location")
    }
  }

  private fun setSourceData(style: Style, sourceId: String, features: List<MapFeature>) {
    style
      .setStyleSourceProperty(sourceId, "data", MapboxStyleSpec.geoJson(features).toValue())
      .check("set source data")
  }

  /** Adds [layer] below [beforeLayerId] if it exists, else below the user-location layers. */
  private fun addLayer(style: Style, layer: MapLayer, beforeLayerId: String?) {
    val below =
      beforeLayerId?.takeIf { style.styleLayerExists(it) }
        ?: MapboxStyleSpec.USER_LOCATION_HALO_LAYER.takeIf { style.styleLayerExists(it) }
    style
      .addStyleLayer(MapboxStyleSpec.layer(layer).toValue(), LayerPosition(null, below, null))
      .check("add layer ${layer.id}")
  }

  private fun addUserLocationLayers(style: Style) {
    style
      .addStyleSource(
        MapboxStyleSpec.USER_LOCATION_SOURCE,
        MapboxStyleSpec.userLocationSource().toValue(),
      )
      .check("add user-location source")
    MapboxStyleSpec.userLocationLayers().forEach {
      style.addStyleLayer(it.toValue(), null).check("add user-location layer")
    }
  }

  /**
   * Mapbox's attribution button sits under the Compose gesture layer, so taps on or within [slopPx]
   * of it are forwarded. Returns whether the button took the tap.
   */
  fun clickMapboxButtonAt(xPx: Float, yPx: Float, slopPx: Float): Boolean {
    val button =
      findView(mapView) { it.javaClass.name == ATTRIBUTION_VIEW_CLASS && it.isShown }
        ?: return false
    val at = IntArray(2).also(button::getLocationInWindow)
    val origin = IntArray(2).also(mapView::getLocationInWindow)
    val left = at[0] - origin[0]
    val top = at[1] - origin[1]
    val bounds = Rect(left, top, left + button.width, top + button.height)
    val slop = slopPx.toInt()
    bounds.inset(-slop, -slop)
    return bounds.contains(xPx.toInt(), yPx.toInt()) && button.performClick()
  }

  fun jumpTo(camera: CameraPosition) {
    if (destroyed) return
    map.setCamera(camera.toCameraOptions())
  }

  /**
   * The topmost content feature within [radiusPx] of ([xPx], [yPx]), in px from the corner,
   * reported as tapped at [at].
   */
  suspend fun featureAt(
    xPx: Float,
    yPx: Float,
    radiusPx: Float,
    content: MapContent,
    at: LatLng,
  ): MapEvent.FeatureTapped? {
    if (destroyed || style == null) return null
    val layerIds = content.layers.map { it.id }.filter { layers.containsKey(it) }
    if (layerIds.isEmpty()) return null
    val box =
      ScreenBox(
        ScreenCoordinate((xPx - radiusPx).toDouble(), (yPx - radiusPx).toDouble()),
        ScreenCoordinate((xPx + radiusPx).toDouble(), (yPx + radiusPx).toDouble()),
      )
    return suspendCancellableCoroutine { continuation ->
      val cancelable =
        map.queryRenderedFeatures(
          RenderedQueryGeometry(box),
          RenderedQueryOptions(layerIds, null),
        ) { result ->
          // Results are ordered topmost first.
          val hit =
            result.value?.firstNotNullOfOrNull { rendered ->
              val layerId = rendered.layers.firstOrNull() ?: return@firstNotNullOfOrNull null
              val featureId =
                rendered.queriedFeature.feature.getStringProperty(
                  MapboxStyleSpec.FEATURE_ID_PROPERTY
                ) ?: return@firstNotNullOfOrNull null
              MapEvent.FeatureTapped(layerId, featureId, at)
            }
          if (continuation.isActive) continuation.resume(hit)
        }
      continuation.invokeOnCancellation { cancelable.cancel() }
    }
  }

  /** Renders [svg] at its natural size in dp, at screen density. */
  private fun rasterize(svg: String): Bitmap {
    val image = SVG.getFromString(svg)
    val viewBox = image.documentViewBox
    val widthDp = image.documentWidth.takeIf { it > 0 } ?: viewBox?.width() ?: DEFAULT_ICON_DP
    val heightDp = image.documentHeight.takeIf { it > 0 } ?: viewBox?.height() ?: DEFAULT_ICON_DP
    val scale = context.resources.displayMetrics.density
    val bitmap =
      Bitmap.createBitmap(
        ceil(widthDp * scale).toInt().coerceAtLeast(1),
        ceil(heightDp * scale).toInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
      )
    image.documentWidth = widthDp
    image.documentHeight = heightDp
    Canvas(bitmap).apply {
      scale(scale, scale)
      image.renderToCanvas(this)
    }
    return bitmap
  }

  fun destroy() {
    if (destroyed) return
    destroyed = true
    styleReady.cancel()
    mapView.onDestroy()
  }

  private companion object {
    const val DEFAULT_ICON_DP = 24f

    // Matched by name: the class extends an AppCompat view that this module doesn't compile
    // against.
    const val ATTRIBUTION_VIEW_CLASS = "com.mapbox.maps.plugin.attribution.AttributionViewImpl"
  }
}

private fun CameraPosition.toCameraOptions(): CameraOptions =
  CameraOptions.Builder()
    .center(Point.fromLngLat(center.longitude, center.latitude))
    .zoom(zoom)
    .bearing(bearing)
    .pitch(pitch)
    .build()

/** The first view in [root]'s tree, [root] included, that matches [predicate]. */
private fun findView(root: View, predicate: (View) -> Boolean): View? {
  if (predicate(root)) return root
  if (root is ViewGroup) {
    for (i in 0 until root.childCount) findView(root.getChildAt(i), predicate)?.let {
      return it
    }
  }
  return null
}

/** The JSON as a Mapbox [Value]; style JSON from [MapboxStyleSpec] is always valid. */
private fun kotlinx.serialization.json.JsonElement.toValue(): Value {
  val parsed = Value.fromJson(toString())
  return parsed.value ?: error("Invalid style JSON: ${parsed.error}")
}

/** Logs the error of a Mapbox style call, if any. */
private fun com.mapbox.bindgen.Expected<String, *>.check(what: String) {
  error?.let { Log.w(TAG, "Failed to $what: $it") }
}
