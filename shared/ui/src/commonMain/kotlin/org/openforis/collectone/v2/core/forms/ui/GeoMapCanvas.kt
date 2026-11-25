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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import openforis.collectone.v1.forms.GeoPoint
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedIconButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.clickable
import org.openforis.collectone.v2.core.forms.ui.resources.Res
import org.openforis.collectone.v2.core.forms.ui.resources.forms_gps_accuracy_zoom
import org.openforis.collectone.v2.core.forms.ui.resources.forms_map_panned_recenter
import org.openforis.collectone.v2.core.forms.ui.resources.forms_recenter_on_gps
import org.openforis.collectone.v2.core.forms.ui.resources.forms_redo
import org.openforis.collectone.v2.core.forms.ui.resources.forms_undo
import org.openforis.collectone.v2.core.forms.ui.resources.forms_zoom_in
import org.openforis.collectone.v2.core.forms.ui.resources.forms_zoom_out

/*
 * Building blocks of the built-in geo question maps shared by [GeoPointInputWidget] and
 * [GeoGeometryDrawingWidget]: the rounded map frame, the drawn basemap, the crosshair reticle, the
 * floating status chip and zoom buttons, and the undo / redo / capture buttons under the map.
 */

/** Zoom level a geo map opens at. */
internal const val DEFAULT_GEO_MAP_ZOOM: Float = 17.5f

/**
 * Degrees of latitude / longitude spanned by a geo map's width or height at [zoomLevel]: 0.0016° at
 * [DEFAULT_GEO_MAP_ZOOM], shrinking as the map zooms in.
 */
internal fun geoMapSpanDegrees(zoomLevel: Float): Float =
  0.0016f * (DEFAULT_GEO_MAP_ZOOM / zoomLevel.coerceIn(13f, 20f))

private val MapShape = RoundedCornerShape(12.dp)
private val ChipShape = RoundedCornerShape(8.dp)
private val ZoomButtonShape = RoundedCornerShape(6.dp)
private const val OVERLAY_ALPHA = 0.88f

/** The rounded, bordered, [height]-tall frame that stacks a geo map's layers. */
@Composable
internal fun GeoMapFrame(height: Dp, content: @Composable BoxScope.() -> Unit) {
  Box(
    modifier =
      Modifier.fillMaxWidth()
        .height(height)
        .clip(MapShape)
        .border(1.dp, GeoMapColors.Border, MapShape),
    content = content,
  )
}

/**
 * The drawn stand-in basemap: field parcels, a survey grid, an optional dashed stream
 * ([showStream]) and, with [hasLiveFix], the collector's GPS dot. Everything but the crosshair
 * moves with the pan offset. When [panAllowed], dragging reports pixel deltas and the map size to
 * [onPanDeltaPixels].
 */
@Composable
internal fun GeoMapBasemap(
  panOffsetLat: Double,
  panOffsetLon: Double,
  zoomLevel: Float,
  panAllowed: Boolean,
  hasLiveFix: Boolean,
  accuracyMeters: Double?,
  showStream: Boolean,
  gestureKey: Any?,
  onPanDeltaPixels: (dxPx: Float, dyPx: Float, widthPx: Float, heightPx: Float) -> Unit,
) {
  Canvas(
    modifier =
      Modifier.fillMaxSize().background(GeoMapColors.Background).pointerInput(
        gestureKey,
        panAllowed,
        zoomLevel,
      ) {
        if (panAllowed) {
          detectDragGestures { change, dragAmount ->
            change.consume()
            onPanDeltaPixels(
              dragAmount.x,
              dragAmount.y,
              size.width.toFloat(),
              size.height.toFloat(),
            )
          }
        }
      }
  ) {
    val spanDeg = geoMapSpanDegrees(zoomLevel)
    val shiftX = (-panOffsetLon.toFloat() / spanDeg) * size.width
    val shiftY = (panOffsetLat.toFloat() / spanDeg) * size.height
    drawFieldParcels(shiftX, shiftY)
    drawSurveyGrid(shiftX, shiftY)
    if (showStream) drawStream(shiftX, shiftY)
    if (hasLiveFix) {
      drawLiveGpsDot(
        center = Offset(x = size.width / 2f + shiftX, y = size.height / 2f + shiftY),
        accuracyMeters = accuracyMeters,
      )
    }
  }
}

private fun DrawScope.drawFieldParcels(shiftX: Float, shiftY: Float) {
  drawRect(
    color = GeoMapColors.FieldParcelLight,
    topLeft = Offset(size.width * 0.08f + shiftX, size.height * 0.10f + shiftY),
    size = Size(size.width * 0.36f, size.height * 0.34f),
  )
  drawRect(
    color = GeoMapColors.FieldParcelLighter,
    topLeft = Offset(size.width * 0.52f + shiftX, size.height * 0.16f + shiftY),
    size = Size(size.width * 0.38f, size.height * 0.42f),
  )
  drawRect(
    color = GeoMapColors.FieldParcelDark,
    topLeft = Offset(size.width * 0.18f + shiftX, size.height * 0.56f + shiftY),
    size = Size(size.width * 0.44f, size.height * 0.32f),
  )
}

private fun DrawScope.drawSurveyGrid(shiftX: Float, shiftY: Float) {
  val gridStepX = size.width / 6f
  val gridStepY = size.height / 4f
  val offsetModX = ((shiftX % gridStepX) + gridStepX) % gridStepX
  val offsetModY = ((shiftY % gridStepY) + gridStepY) % gridStepY
  for (i in -1..6) {
    val x = i * gridStepX + offsetModX
    drawLine(
      color = Color.White.copy(alpha = 0.08f),
      start = Offset(x, 0f),
      end = Offset(x, size.height),
      strokeWidth = 1f,
    )
  }
  for (j in -1..4) {
    val y = j * gridStepY + offsetModY
    drawLine(
      color = Color.White.copy(alpha = 0.08f),
      start = Offset(0f, y),
      end = Offset(size.width, y),
      strokeWidth = 1f,
    )
  }
}

private fun DrawScope.drawStream(shiftX: Float, shiftY: Float) {
  val streamPath =
    Path().apply {
      moveTo(0f + shiftX * 0.5f, size.height * 0.78f + shiftY)
      cubicTo(
        size.width * 0.35f + shiftX,
        size.height * 0.62f + shiftY,
        size.width * 0.65f + shiftX,
        size.height * 0.40f + shiftY,
        size.width * 1.05f + shiftX * 0.5f,
        size.height * 0.18f + shiftY,
      )
    }
  drawPath(
    path = streamPath,
    color = GeoMapColors.Stream.copy(alpha = 0.28f),
    style =
      Stroke(width = 2.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))),
  )
}

/** Draws the collector's live GPS position at [center] with a halo sized by [accuracyMeters]. */
internal fun DrawScope.drawLiveGpsDot(center: Offset, accuracyMeters: Double?) {
  val haloRadiusDp = ((accuracyMeters ?: 0.0).toFloat() * 3.2f).coerceIn(14f, 56f).dp
  drawCircle(
    color = GeoMapColors.GpsAccuracyHalo.copy(alpha = 0.22f),
    radius = haloRadiusDp.toPx(),
    center = center,
  )
  drawCircle(
    color = GeoMapColors.GpsAccuracyRing.copy(alpha = 0.55f),
    radius = haloRadiusDp.toPx(),
    center = center,
    style = Stroke(width = 1.2.dp.toPx()),
  )
  drawCircle(color = Color.White, radius = 7.dp.toPx(), center = center)
  drawCircle(color = GeoMapColors.GpsDot, radius = 5.dp.toPx(), center = center)
}

/**
 * Screen position of [point] on a map centered on ([centerLat], [centerLon]) at [zoomLevel], where
 * the map's width and height each span [geoMapSpanDegrees].
 */
internal fun DrawScope.geoMapOffset(
  point: GeoPoint,
  centerLat: Double,
  centerLon: Double,
  zoomLevel: Float,
): Offset {
  val spanDeg = geoMapSpanDegrees(zoomLevel)
  val dLon = (point.longitude - centerLon).toFloat()
  val dLat = (point.latitude - centerLat).toFloat()
  return Offset(
    x = center.x + (dLon / spanDeg) * size.width,
    y = center.y - (dLat / spanDeg) * size.height,
  )
}

/** Draws the crosshair reticle at the map center, in the panned accent when [isPanned]. */
internal fun DrawScope.drawGeoReticle(isPanned: Boolean) {
  val reticleColor = if (isPanned) GeoMapColors.PannedAccent else GeoMapColors.Accent
  val ringRadius = 18.dp.toPx()
  val tickInner = 7.dp.toPx()
  val tickOuter = 25.dp.toPx()

  drawCircle(
    color = GeoMapColors.Shadow.copy(alpha = 0.65f),
    radius = ringRadius,
    center = center,
    style = Stroke(width = 3.5.dp.toPx()),
  )
  drawCircle(
    color = reticleColor,
    radius = ringRadius,
    center = center,
    style = Stroke(width = 2.dp.toPx()),
  )
  // Crosshair ticks: W, E, N, S.
  drawLine(
    color = reticleColor,
    start = Offset(center.x - tickOuter, center.y),
    end = Offset(center.x - tickInner, center.y),
    strokeWidth = 2.dp.toPx(),
  )
  drawLine(
    color = reticleColor,
    start = Offset(center.x + tickInner, center.y),
    end = Offset(center.x + tickOuter, center.y),
    strokeWidth = 2.dp.toPx(),
  )
  drawLine(
    color = reticleColor,
    start = Offset(center.x, center.y - tickOuter),
    end = Offset(center.x, center.y - tickInner),
    strokeWidth = 2.dp.toPx(),
  )
  drawLine(
    color = reticleColor,
    start = Offset(center.x, center.y + tickInner),
    end = Offset(center.x, center.y + tickOuter),
    strokeWidth = 2.dp.toPx(),
  )
  drawCircle(color = GeoMapColors.Shadow, radius = 4.5.dp.toPx(), center = center)
  drawCircle(
    color = if (isPanned) GeoMapColors.PannedCenterDot else GeoMapColors.CenterDot,
    radius = 3.dp.toPx(),
    center = center,
  )
}

/**
 * The map's single status chip (top-left), combining what used to be separate mode, GPS accuracy,
 * and recenter chips:
 * - while the map is panned away from GPS, it says so and tapping it centers the map again
 *   ([onRecenter]);
 * - while a line or polygon records the collector's walk, it shows [walkingText];
 * - otherwise it shows the GPS accuracy and the map's zoom level, with a lock icon described by
 *   [lockedDescription] while panning is not allowed.
 */
@Composable
internal fun BoxScope.GeoMapStatusChip(
  isPanned: Boolean,
  accuracyMeters: Double?,
  zoomLevel: Float,
  onRecenter: () -> Unit,
  lockedDescription: String? = null,
  walkingText: String? = null,
) {
  val icon: ImageVector
  val text: String
  when {
    isPanned -> {
      icon = Icons.Default.Refresh
      text = stringResource(Res.string.forms_map_panned_recenter)
    }
    walkingText != null -> {
      icon = Icons.Default.LocationOn
      text = walkingText
    }
    else -> {
      icon = if (lockedDescription != null) Icons.Default.Lock else Icons.Default.LocationOn
      text =
        stringResource(
          Res.string.forms_gps_accuracy_zoom,
          formatGeoPointAccuracy(accuracyMeters),
          ((zoomLevel * 10f).roundToInt() / 10f).toString(),
        )
    }
  }
  val highlighted = isPanned || walkingText != null
  val recenterLabel = stringResource(Res.string.forms_recenter_on_gps)
  Surface(
    modifier =
      Modifier.align(Alignment.TopStart)
        .padding(8.dp)
        .clip(ChipShape)
        .then(
          if (isPanned) {
            Modifier.clickable(
              onClickLabel = recenterLabel,
              role = Role.Button,
              onClick = onRecenter,
            )
          } else {
            Modifier
          }
        ),
    shape = ChipShape,
    color = GeoMapColors.OverlaySurface.copy(alpha = OVERLAY_ALPHA),
    border = BorderStroke(1.dp, GeoMapColors.Border),
  ) {
    val textColor = if (highlighted) GeoMapColors.ActiveStatusText else GeoMapColors.StatusText
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = lockedDescription.takeIf { !highlighted },
        tint = textColor,
        modifier = Modifier.size(13.dp),
      )
      Text(
        text = text,
        style =
          MaterialTheme.typography.labelSmall.copy(
            color = textColor,
            fontWeight = FontWeight.SemiBold,
          ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/** Top-right zoom in / out buttons, each reporting a zoom step to [onZoomDelta]. */
@Composable
internal fun BoxScope.GeoMapZoomButtons(onZoomDelta: (Float) -> Unit) {
  Column(
    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    GeoMapZoomButton(
      icon = Icons.Default.Add,
      description = stringResource(Res.string.forms_zoom_in),
      onClick = { onZoomDelta(ZOOM_STEP) },
    )
    GeoMapZoomButton(
      icon = RemoveIcon,
      description = stringResource(Res.string.forms_zoom_out),
      onClick = { onZoomDelta(-ZOOM_STEP) },
    )
  }
}

private const val ZOOM_STEP = 0.75f

@Composable
private fun GeoMapZoomButton(icon: ImageVector, description: String, onClick: () -> Unit) {
  Surface(
    modifier = Modifier.size(28.dp).clip(ZoomButtonShape).clickable(onClick = onClick),
    shape = ZoomButtonShape,
    color = GeoMapColors.OverlaySurface.copy(alpha = OVERLAY_ALPHA),
    border = BorderStroke(1.dp, GeoMapColors.Border),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        imageVector = icon,
        contentDescription = description,
        tint = Color.White,
        modifier = Modifier.size(16.dp),
      )
    }
  }
}

/** The Undo and Redo icon buttons that lead a geo question's action row. */
@Composable
internal fun GeoUndoRedoButtons(
  canUndo: Boolean,
  canRedo: Boolean,
  onUndo: () -> Unit,
  onRedo: () -> Unit,
) {
  OutlinedIconButton(onClick = onUndo, enabled = canUndo) {
    Icon(
      imageVector = UndoIcon,
      contentDescription = stringResource(Res.string.forms_undo),
      modifier = Modifier.size(20.dp),
    )
  }
  OutlinedIconButton(onClick = onRedo, enabled = canRedo) {
    Icon(
      imageVector = RedoIcon,
      contentDescription = stringResource(Res.string.forms_redo),
      modifier = Modifier.size(20.dp),
    )
  }
}

/**
 * The primary button that records the crosshair ([isPanned]) or GPS position, labeled [label]. Pass
 * a weighted [modifier] so it fills the rest of the action row.
 */
@Composable
internal fun GeoCaptureButton(
  label: String,
  isPanned: Boolean,
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Button(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier,
    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
  ) {
    Icon(
      imageVector = if (isPanned) Icons.Default.Place else Icons.Default.LocationOn,
      contentDescription = null,
      modifier = Modifier.size(18.dp),
    )
    Spacer(modifier = Modifier.width(6.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      softWrap = false,
    )
  }
}
