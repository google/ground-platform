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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import openforis.collectone.v1.forms.GeoPoint
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedIconButton
import org.openforis.collectone.v2.core.forms.ui.resources.Res
import org.openforis.collectone.v2.core.forms.ui.resources.forms_add_point
import org.openforis.collectone.v2.core.forms.ui.resources.forms_capture_vertex
import org.openforis.collectone.v2.core.forms.ui.resources.forms_clear_all_vertices
import org.openforis.collectone.v2.core.forms.ui.resources.forms_geometry_gps_stream_only
import org.openforis.collectone.v2.core.forms.ui.resources.forms_geometry_line_need_more
import org.openforis.collectone.v2.core.forms.ui.resources.forms_geometry_line_ready
import org.openforis.collectone.v2.core.forms.ui.resources.forms_geometry_polygon_need_more
import org.openforis.collectone.v2.core.forms.ui.resources.forms_geometry_polygon_ready
import org.openforis.collectone.v2.core.forms.ui.resources.forms_geometry_walking
import org.openforis.collectone.v2.core.forms.ui.resources.forms_map_centered_on_gps
import org.openforis.collectone.v2.core.forms.ui.resources.forms_map_panned_crosshair
import org.openforis.collectone.v2.core.forms.ui.resources.forms_start_walking
import org.openforis.collectone.v2.core.forms.ui.resources.forms_stop_walking
import org.openforis.collectone.v2.core.forms.ui.resources.forms_vertex_count

/**
 * Interactive map-based drawing widget for `TYPE_GEOTRACE` (linestring / transect) and
 * `TYPE_GEOSHAPE` (polygon / boundary) questions.
 *
 * Supports both:
 * - **Pannable mode** (`appearance="placement-map"`, `"map"`, `"maps"`, or `"walk-or-draw"`):
 *   collector can pan the map to place vertices with `"Add point"`.
 * - **Locked GPS mode** (default / GPS walk mode): map is locked to user GPS; collector records
 *   vertices along their walking path with `"Capture vertex"`.
 *
 * Provides:
 * - Interactive Compose Canvas map rendering live crosshair target reticle, GPS position dot,
 *   vertices, connecting polylines, and closed polygon fill.
 * - Primary action button: `"Add point"` (when panned) or `"Capture vertex"` (when on GPS).
 * - Action buttons: `"Undo vertex"`, `"Clear"`, and `"Recenter"`.
 * - Status HUD indicating vertex count, shape completion, and pan / lock mode.
 */
@Composable
internal fun GeoGeometryDrawingWidget(
  vertices: List<GeoPoint>,
  isClosedShape: Boolean,
  panAllowed: Boolean,
  editState: GeoEditState,
  onVerticesChange: (List<GeoPoint>) -> Unit,
) {
  val colors = MaterialTheme.colorScheme

  val firstExisting = vertices.firstOrNull()
  val liveFix by LocalGeoLocationSource.current.currentFix.collectAsState()
  // Without a fix the map stays on the first vertex, if any; nothing can be captured.
  val anchorLat = liveFix?.latitude ?: firstExisting?.latitude ?: 0.0
  val anchorLon = liveFix?.longitude ?: firstExisting?.longitude ?: 0.0
  val panOffsetLat = editState.panOffsetLat
  val panOffsetLon = editState.panOffsetLon
  val zoomLevel = editState.zoomLevel
  val isWalking = editState.isWalking

  val isPanned = editState.isPanned(panAllowed)
  val targetLat = roundGeoCoord6(anchorLat + if (panAllowed) panOffsetLat else 0.0)
  val targetLon = roundGeoCoord6(anchorLon + if (panAllowed) panOffsetLon else 0.0)
  val displayAcc = liveFix?.accuracyMeters
  val gpsVertex = geoCapturePoint(liveFix)
  val crosshairVertex =
    if (isPanned) geoCapturePoint(liveFix, panOffsetLat, panOffsetLon) else gpsVertex

  val minVerticesRequired = if (isClosedShape) 3 else 2
  val hasMinVertices = vertices.size >= minVerticesRequired

  fun appendVertex(point: GeoPoint, points: List<GeoPoint>) {
    editState.recordEdit(points)
    onVerticesChange(points + point)
  }

  // Walking records the live fix as a vertex when walking starts and every 5 s after that, skipping
  // samples while there is no fix. It stops when the question leaves the screen.
  val latestVertices by rememberUpdatedState(vertices)
  val latestGpsVertex by rememberUpdatedState(gpsVertex)
  LaunchedEffect(editState, isWalking) {
    if (!isWalking) return@LaunchedEffect
    if (latestVertices.isEmpty()) {
      latestGpsVertex?.let { appendVertex(it, latestVertices) }
    }
    while (isActive && editState.isWalking) {
      delay(5_000L)
      if (!editState.isWalking) break
      latestGpsVertex?.let { appendVertex(it, latestVertices) }
    }
  }
  DisposableEffect(editState) { onDispose { editState.stopWalking() } }

  val triggerLabel =
    stringResource(if (isPanned) Res.string.forms_add_point else Res.string.forms_capture_vertex)

  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    // Concise status badge row (vertex count & walking state)
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      TonalBadge(
        text =
          pluralStringResource(
            Res.plurals.forms_vertex_count,
            vertices.size,
            vertices.size,
          ),
        tone = if (hasMinVertices) BadgeTone.PRIMARY else BadgeTone.WARNING,
      )
      if (isWalking) {
        TonalBadge(
          text =
            pluralStringResource(
              Res.plurals.forms_geometry_walking,
              vertices.size,
              vertices.size,
            ),
          tone = BadgeTone.WARNING,
        )
      }
    }

    // Interactive Map Viewport with Vertices, Lines, Polygon Fill, Crosshair & GPS Dot
    GeoMapFrame(height = 230.dp) {
      GeoMapBasemap(
        panOffsetLat = panOffsetLat,
        panOffsetLon = panOffsetLon,
        zoomLevel = zoomLevel,
        panAllowed = panAllowed,
        hasLiveFix = liveFix != null,
        accuracyMeters = displayAcc,
        showStream = false,
        gestureKey = editState,
        onPanDeltaPixels = { dxPx, dyPx, widthPx, heightPx ->
          editState.panByPixels(dxPx, dyPx, widthPx, heightPx, panAllowed)
        },
      )
      Canvas(modifier = Modifier.fillMaxSize()) {
        drawGeometryDraft(
          vertices = vertices.map { geoMapOffset(it, targetLat, targetLon, zoomLevel) },
          isClosedShape = isClosedShape,
        )
        drawGeoReticle(isPanned = isPanned)
      }

      GeoMapStatusChip(
        icon =
          when {
            isWalking -> Icons.Default.LocationOn
            !panAllowed -> Icons.Default.Lock
            isPanned -> Icons.Default.Place
            else -> Icons.Default.LocationOn
          },
        text =
          when {
            isWalking ->
              pluralStringResource(
                Res.plurals.forms_geometry_walking,
                vertices.size,
                vertices.size,
              )
            !panAllowed -> stringResource(Res.string.forms_geometry_gps_stream_only)
            isPanned -> stringResource(Res.string.forms_map_panned_crosshair)
            else -> stringResource(Res.string.forms_map_centered_on_gps)
          },
        highlighted = isWalking || isPanned,
      )
      GeoMapZoomButtons(onZoomDelta = editState::zoomBy)
      GeoMapAccuracyReadout(accuracyMeters = displayAcc, zoomLevel = zoomLevel)
      if (isPanned) {
        GeoMapRecenterChip(onClick = editState::recenter)
      }
    }

    if (liveFix == null) GeoLocationWaitingText()

    // Concise vertex requirement status (without duplicating live GPS accuracy / coordinates)
    if (hasMinVertices) {
      Text(
        text =
          if (isClosedShape) {
            pluralStringResource(
              Res.plurals.forms_geometry_polygon_ready,
              vertices.size,
              vertices.size,
            )
          } else {
            pluralStringResource(
              Res.plurals.forms_geometry_line_ready,
              vertices.size,
              vertices.size,
            )
          },
        style =
          MaterialTheme.typography.labelSmall.copy(
            color = colors.primary,
            fontWeight = FontWeight.Medium,
          ),
      )
    } else {
      val needed = minVerticesRequired - vertices.size
      Text(
        text =
          pluralStringResource(
            if (isClosedShape) Res.plurals.forms_geometry_polygon_need_more
            else Res.plurals.forms_geometry_line_need_more,
            needed,
            needed,
            triggerLabel,
          ),
        style = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant),
      )
    }

    // Single-row primary controls: [Undo] [Redo] [Start/Stop walking] [Add point / Capture vertex]
    // [Clear]
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      GeoUndoRedoButtons(
        canUndo = editState.canUndo(vertices),
        canRedo = editState.canRedo,
        onUndo = { editState.undo(vertices)?.let(onVerticesChange) },
        onRedo = { editState.redo(vertices)?.let(onVerticesChange) },
      )

      if (!panAllowed) {
        Button(
          onClick = editState::toggleWalking,
          enabled = isWalking || gpsVertex != null,
          modifier = Modifier.weight(1f),
          colors =
            if (isWalking) {
              ButtonDefaults.buttonColors(
                containerColor = colors.error,
                contentColor = colors.onError,
              )
            } else {
              ButtonDefaults.buttonColors()
            },
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        ) {
          Icon(
            imageVector = if (isWalking) Icons.Default.Close else Icons.Default.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text =
              stringResource(
                if (isWalking) Res.string.forms_stop_walking else Res.string.forms_start_walking
              ),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
          )
        }
      }

      if (panAllowed) {
        GeoCaptureButton(
          label = triggerLabel,
          isPanned = isPanned,
          enabled = crosshairVertex != null,
          onClick = { crosshairVertex?.let { appendVertex(it, vertices) } },
          modifier = Modifier.weight(1f),
        )
      } else {
        OutlinedButton(
          onClick = { gpsVertex?.let { appendVertex(it, vertices) } },
          enabled = gpsVertex != null,
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        ) {
          Text(
            text = stringResource(Res.string.forms_add_point),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
          )
        }
      }

      if (vertices.isNotEmpty()) {
        OutlinedIconButton(
          onClick = {
            editState.stopWalking()
            editState.recordEdit(vertices)
            onVerticesChange(emptyList())
          }
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = stringResource(Res.string.forms_clear_all_vertices),
            modifier = Modifier.size(20.dp),
          )
        }
      }
    }
  }
}

/**
 * Draws a geotrace / geoshape draft from its [vertices] screen positions: the closed polygon fill
 * ([isClosedShape], 3+ vertices) or the line, a dashed line from the last vertex to the crosshair,
 * and the vertex markers (first and last highlighted).
 */
private fun DrawScope.drawGeometryDraft(vertices: List<Offset>, isClosedShape: Boolean) {
  if (isClosedShape && vertices.size >= 3) {
    val polyPath =
      Path().apply {
        moveTo(vertices[0].x, vertices[0].y)
        for (i in 1 until vertices.size) lineTo(vertices[i].x, vertices[i].y)
        close()
      }
    drawPath(path = polyPath, color = GeoMapColors.PolygonFill.copy(alpha = 0.22f))
    drawPath(
      path = polyPath,
      color = GeoMapColors.PolygonOutline,
      style = Stroke(width = 2.5.dp.toPx()),
    )
  } else if (vertices.size >= 2) {
    val linePath =
      Path().apply {
        moveTo(vertices[0].x, vertices[0].y)
        for (i in 1 until vertices.size) lineTo(vertices[i].x, vertices[i].y)
      }
    drawPath(path = linePath, color = GeoMapColors.TraceLine, style = Stroke(width = 2.8.dp.toPx()))
  }

  // Candidate line from the last vertex to the crosshair.
  vertices.lastOrNull()?.let { last ->
    drawLine(
      color = GeoMapColors.PannedAccent.copy(alpha = 0.75f),
      start = last,
      end = center,
      strokeWidth = 1.8.dp.toPx(),
      pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
    )
  }

  vertices.forEachIndexed { idx, off ->
    drawCircle(color = GeoMapColors.Shadow.copy(alpha = 0.6f), radius = 9.dp.toPx(), center = off)
    drawCircle(color = Color.White, radius = 7.dp.toPx(), center = off)
    drawCircle(
      color =
        when (idx) {
          0 -> GeoMapColors.FirstVertex
          vertices.lastIndex -> GeoMapColors.LastVertex
          else -> GeoMapColors.Vertex
        },
      radius = 5.dp.toPx(),
      center = off,
    )
  }
}
