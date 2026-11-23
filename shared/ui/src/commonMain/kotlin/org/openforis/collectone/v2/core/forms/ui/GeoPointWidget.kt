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
package org.openforis.collectone.v2.core.forms.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import openforis.collectone.v1.forms.GeoPoint
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedIconButton
import org.openforis.collectone.v2.core.forms.ui.resources.Res
import org.openforis.collectone.v2.core.forms.ui.resources.forms_accuracy_required
import org.openforis.collectone.v2.core.forms.ui.resources.forms_add_point
import org.openforis.collectone.v2.core.forms.ui.resources.forms_capture_location
import org.openforis.collectone.v2.core.forms.ui.resources.forms_geo_capture_hint
import org.openforis.collectone.v2.core.forms.ui.resources.forms_geo_location_captured
import org.openforis.collectone.v2.core.forms.ui.resources.forms_geo_map_panned_hint
import org.openforis.collectone.v2.core.forms.ui.resources.forms_geo_saved_point
import org.openforis.collectone.v2.core.forms.ui.resources.forms_geo_status_captured
import org.openforis.collectone.v2.core.forms.ui.resources.forms_geo_waiting_for_location
import org.openforis.collectone.v2.core.forms.ui.resources.forms_gps_locked
import org.openforis.collectone.v2.core.forms.ui.resources.forms_map_centered_on_gps
import org.openforis.collectone.v2.core.forms.ui.resources.forms_map_panned_crosshair
import org.openforis.collectone.v2.core.forms.ui.resources.forms_recenter_on_gps

/**
 * State passed to an optional host-provided map viewport ([LocalGeoPointMapViewport]) inside
 * [GeoPointInputWidget] so host apps (such as the mobile app and the web console) can render a live
 * Mapbox GL JS basemap synchronized with the question's pan/zoom and GPS state.
 */
data class GeoPointMapViewportState(
  val path: String,
  val panAllowed: Boolean,
  val isPanned: Boolean,
  /** The live GPS fix, or `null` while the device has no location. */
  val liveGpsLatitude: Double?,
  val liveGpsLongitude: Double?,
  val targetLatitude: Double,
  val targetLongitude: Double,
  /** Horizontal accuracy shown on the map, or `null` when unknown. */
  val accuracyMeters: Double?,
  val capturedPoint: GeoPoint?,
  val panOffsetLat: Double,
  val panOffsetLon: Double,
  val zoomLevel: Float,
  val onPanDeltaPixels: (dxPx: Float, dyPx: Float, widthPx: Float, heightPx: Float) -> Unit,
  val onZoomDelta: (deltaZoom: Float) -> Unit,
  val onRecenterGps: () -> Unit,
)

/**
 * Optional composition local allowing a host application (such as the mobile app and the web
 * console) to render a live platform basemap inside [GeoPointInputWidget]'s map viewport. When
 * `null`, [GeoPointInputWidget] renders its built-in interactive satellite/topographic Compose
 * `Canvas` basemap.
 */
val LocalGeoPointMapViewport:
  ProvidableCompositionLocal<(@Composable (GeoPointMapViewportState) -> Unit)?> =
  compositionLocalOf {
    null
  }

/**
 * Returns the primary action trigger label for a `geopoint` question:
 * - `"Add point"` when the map has been panned away from the user's GPS position ([isPanned] is
 *   `true`)
 * - `"Capture location"` when centered on the user's GPS position ([isPanned] is `false`)
 */
fun geoPointPrimaryTriggerLabel(isPanned: Boolean): StringResource =
  if (isPanned) Res.string.forms_add_point else Res.string.forms_capture_location

/** Rounds a geographic coordinate to 6 decimal places (~0.11 m precision). */
internal fun roundGeoCoord6(value: Double): Double =
  (value * 1_000_000.0).roundToInt() / 1_000_000.0

/** Formats a decimal degree value to 6 decimal places. */
private fun formatDecimal6(value: Double): String {
  val rounded = roundGeoCoord6(abs(value))
  val whole = rounded.toInt()
  val frac = ((rounded - whole) * 1_000_000.0).roundToInt().toString().padStart(6, '0')
  return "$whole.$frac"
}

/**
 * Formats [latitude] and [longitude] into a cardinal degree string (e.g. `"1.292066° S, 36.821946°
 * E"`).
 */
fun formatGeoPointCoordinates(latitude: Double, longitude: Double): String {
  val latHemisphere = if (latitude < 0.0) "S" else "N"
  val lonHemisphere = if (longitude < 0.0) "W" else "E"
  return "${formatDecimal6(latitude)}° $latHemisphere, ${formatDecimal6(longitude)}° $lonHemisphere"
}

/** Formats horizontal GPS accuracy in meters (e.g. `"±3.2 m"`). */
fun formatGeoPointAccuracy(accuracyMeters: Double?): String {
  if (accuracyMeters == null) return "±? m"
  val tenths = (accuracyMeters * 10.0).roundToInt() / 10.0
  return "±$tenths m"
}

/** Tells the collector that location capture is waiting for a fix from [LocalGeoLocationSource]. */
@Composable
internal fun GeoLocationWaitingText() {
  Text(
    text = stringResource(Res.string.forms_geo_waiting_for_location),
    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.error),
  )
}

/**
 * Map-based input for a `geopoint` question answered with [point]. Captures the GPS fix, or the
 * crosshair when [panAllowed] and the collector panned the map; [editState] holds the map's pan,
 * zoom, and undo / redo history. Reports every new answer (`null` when cleared) to [onPointChange].
 */
@Composable
internal fun GeoPointInputWidget(
  path: String,
  point: GeoPoint?,
  panAllowed: Boolean,
  accuracyThreshold: Double?,
  editState: GeoEditState,
  onPointChange: (GeoPoint?) -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val vertices = listOfNotNull(point)
  fun applyVertices(newVertices: List<GeoPoint>) = onPointChange(newVertices.lastOrNull())

  val liveFix by LocalGeoLocationSource.current.currentFix.collectAsState()
  // Without a fix the map stays on the captured point, if any; nothing can be captured.
  val anchorLat = liveFix?.latitude ?: point?.latitude ?: 0.0
  val anchorLon = liveFix?.longitude ?: point?.longitude ?: 0.0
  val liveGpsAcc = liveFix?.accuracyMeters
  val panOffsetLat = editState.panOffsetLat
  val panOffsetLon = editState.panOffsetLon

  val isPanned = editState.isPanned(panAllowed)
  val targetLat = roundGeoCoord6(anchorLat + if (panAllowed) panOffsetLat else 0.0)
  val targetLon = roundGeoCoord6(anchorLon + if (panAllowed) panOffsetLon else 0.0)
  val capturePoint =
    if (isPanned) geoCapturePoint(liveFix, panOffsetLat, panOffsetLon) else geoCapturePoint(liveFix)

  val displayAcc = if (isPanned) liveGpsAcc else (point?.accuracy_meters ?: liveGpsAcc)
  val meetsAccuracy =
    accuracyThreshold == null || (displayAcc != null && displayAcc <= accuracyThreshold)
  val triggerLabel = stringResource(geoPointPrimaryTriggerLabel(isPanned))

  val viewportState =
    GeoPointMapViewportState(
      path = path,
      panAllowed = panAllowed,
      isPanned = isPanned,
      liveGpsLatitude = liveFix?.latitude,
      liveGpsLongitude = liveFix?.longitude,
      targetLatitude = targetLat,
      targetLongitude = targetLon,
      accuracyMeters = displayAcc,
      capturedPoint = point,
      panOffsetLat = panOffsetLat,
      panOffsetLon = panOffsetLon,
      zoomLevel = editState.zoomLevel,
      onPanDeltaPixels = { dxPx, dyPx, widthPx, heightPx ->
        editState.panByPixels(dxPx, dyPx, widthPx, heightPx, panAllowed)
      },
      onZoomDelta = editState::zoomBy,
      onRecenterGps = editState::recenter,
    )

  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    if (accuracyThreshold != null || point != null) {
      @OptIn(ExperimentalLayoutApi::class)
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        if (accuracyThreshold != null) {
          TonalBadge(
            text = stringResource(Res.string.forms_accuracy_required, accuracyThreshold.toString()),
            icon = Icons.Default.Info,
            tone = if (meetsAccuracy) BadgeTone.SECONDARY else BadgeTone.ERROR,
          )
        }
        if (point != null) {
          TonalBadge(
            text = stringResource(Res.string.forms_geo_status_captured),
            icon = Icons.Default.Check,
            tone = BadgeTone.SECONDARY,
          )
        }
      }
    }

    // Interactive Map Viewport for Add Point / Capture Location
    GeoPointInteractiveMapBox(viewportState = viewportState)

    if (liveFix == null) GeoLocationWaitingText()

    // Concise saved-point status or capture instruction (without duplicating map telemetry)
    if (isPanned && point != null) {
      Text(
        text =
          stringResource(
            Res.string.forms_geo_saved_point,
            point.latitude.toString(),
            point.longitude.toString(),
            formatGeoPointAccuracy(point.accuracy_meters),
            stringResource(Res.string.forms_add_point),
          ),
        style =
          MaterialTheme.typography.labelSmall.copy(
            color = colors.primary,
            fontWeight = FontWeight.Medium,
          ),
      )
    } else if (point != null) {
      Text(
        text =
          stringResource(
            Res.string.forms_geo_location_captured,
            point.latitude.toString(),
            point.longitude.toString(),
            formatGeoPointAccuracy(point.accuracy_meters),
          ),
        style =
          MaterialTheme.typography.labelSmall.copy(
            color = colors.primary,
            fontWeight = FontWeight.Medium,
          ),
      )
    } else {
      Text(
        text =
          if (isPanned) {
            stringResource(Res.string.forms_geo_map_panned_hint, triggerLabel)
          } else {
            stringResource(Res.string.forms_geo_capture_hint, triggerLabel)
          },
        style = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant),
      )
    }

    // Primary Action Row: [Undo] [Redo] [Capture location / Add point] [Recenter]
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      GeoUndoRedoButtons(
        canUndo = editState.canUndo(vertices),
        canRedo = editState.canRedo,
        onUndo = { editState.undo(vertices)?.let(::applyVertices) },
        onRedo = { editState.redo(vertices)?.let(::applyVertices) },
      )

      GeoCaptureButton(
        label = triggerLabel,
        isPanned = isPanned,
        enabled = capturePoint != null,
        onClick = {
          val captured = capturePoint ?: return@GeoCaptureButton
          editState.recordEdit(vertices)
          onPointChange(captured)
        },
        modifier = Modifier.weight(1f),
      )

      if (isPanned) {
        OutlinedIconButton(onClick = { viewportState.onRecenterGps() }) {
          Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = stringResource(Res.string.forms_recenter_on_gps),
            modifier = Modifier.size(20.dp),
          )
        }
      }
    }
  }
}

/**
 * Interactive map viewport rendered inside [GeoPointInputWidget] for `TYPE_GEOPOINT` questions.
 *
 * Delegates basemap rendering to [LocalGeoPointMapViewport] when provided by the host (e.g., live
 * Mapbox GL JS basemap in the mobile app and the web console), or renders a built-in interactive
 * satellite and topographic Compose `Canvas` basemap with drag-to-pan gestures, GPS blue dot +
 * accuracy halo, center target crosshair reticle, captured pin marker, zoom controls, and Recenter
 * pill.
 */
@Composable
private fun GeoPointInteractiveMapBox(viewportState: GeoPointMapViewportState) {
  val hostMapViewport = LocalGeoPointMapViewport.current

  GeoMapFrame(height = 216.dp) {
    if (hostMapViewport != null) {
      hostMapViewport(viewportState)
    } else {
      GeoMapBasemap(
        panOffsetLat = viewportState.panOffsetLat,
        panOffsetLon = viewportState.panOffsetLon,
        zoomLevel = viewportState.zoomLevel,
        panAllowed = viewportState.panAllowed,
        hasLiveFix = viewportState.liveGpsLatitude != null,
        accuracyMeters = viewportState.accuracyMeters,
        showStream = true,
        gestureKey = viewportState.path,
        onPanDeltaPixels = viewportState.onPanDeltaPixels,
      )
    }

    // Captured pin (relative to the current map center) under the crosshair reticle.
    Canvas(modifier = Modifier.fillMaxSize()) {
      val captured = viewportState.capturedPoint
      if (captured != null) {
        val saved =
          geoMapOffset(
            point = captured,
            centerLat = viewportState.targetLatitude,
            centerLon = viewportState.targetLongitude,
            zoomLevel = viewportState.zoomLevel,
          )
        if (saved.x in 0f..size.width && saved.y in 0f..size.height) {
          drawCircle(
            color = GeoMapColors.SavedPointHalo.copy(alpha = 0.28f),
            radius = 14.dp.toPx(),
            center = saved,
          )
          drawCircle(color = Color.White, radius = 7.dp.toPx(), center = saved)
          drawCircle(color = GeoMapColors.SavedPoint, radius = 5.dp.toPx(), center = saved)
        }
      }
      drawGeoReticle(isPanned = viewportState.isPanned)
    }

    GeoMapStatusChip(
      icon =
        when {
          !viewportState.panAllowed -> Icons.Default.Lock
          viewportState.isPanned -> Icons.Default.Place
          else -> Icons.Default.LocationOn
        },
      text =
        when {
          !viewportState.panAllowed -> stringResource(Res.string.forms_gps_locked)
          viewportState.isPanned -> stringResource(Res.string.forms_map_panned_crosshair)
          else -> stringResource(Res.string.forms_map_centered_on_gps)
        },
      highlighted = viewportState.isPanned,
    )
    GeoMapZoomButtons(onZoomDelta = viewportState.onZoomDelta)
    GeoMapAccuracyReadout(
      accuracyMeters = viewportState.accuracyMeters,
      zoomLevel = viewportState.zoomLevel,
    )
    if (viewportState.isPanned) GeoMapRecenterChip(onClick = viewportState.onRecenterGps)
  }
}
