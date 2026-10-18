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
package org.groundplatform.v2.core.forms.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import groundplatform.v2.forms.DataType
import groundplatform.v2.forms.GeoPoint
import org.groundplatform.v2.core.forms.model.ComponentState
import org.groundplatform.v2.core.forms.model.FieldState

/**
 * Geometry a compact-layout question can ask the collector to draw on the host's map, with the
 * number of vertices needed for a complete answer.
 */
enum class MapDrawingKind(val label: String, val minVertices: Int) {
  POINT("Point", 1),
  LINE("Line", 2),
  POLYGON("Polygon", 3),
}

/** Returns the [MapDrawingKind] for [dataType], or `null` when it isn't a geometry type. */
fun mapDrawingKindOf(dataType: DataType): MapDrawingKind? =
  when (dataType) {
    DataType.TYPE_GEOPOINT -> MapDrawingKind.POINT
    DataType.TYPE_GEOTRACE -> MapDrawingKind.LINE
    DataType.TYPE_GEOSHAPE -> MapDrawingKind.POLYGON
    else -> null
  }

/**
 * Host hooks for "draw on the map" geometry input (see [CompactGeometryInput.MapDrawing]).
 *
 * The compact layout tells the host which question is being drawn; the host turns its map clicks
 * into vertices with [addMapDrawingVertex] and renders the in-progress geometry. The web dashboard
 * implements this with its main map.
 */
interface CompactMapDrawingHost {
  /** Canonical path of the question whose geometry is being drawn on the map, or `null`. */
  val activeDrawingPath: String?

  /** Starts routing map clicks to the geometry question at [path]. */
  fun startDrawing(path: String, kind: MapDrawingKind)

  /** Stops routing map clicks to the active question, keeping whatever was drawn. */
  fun stopDrawing()

  /** True when the host can centre a question's geometry in its map ([frameGeometry]). */
  val canFrameGeometry: Boolean
    get() = false

  /**
   * Frames the geometry currently stored for the question at [path] in the host's map ("Zoom to
   * fit"). Only offered when [canFrameGeometry] is `true`; the default does nothing.
   */
  fun frameGeometry(path: String, kind: MapDrawingKind) {}
}

/**
 * How geometry questions (`geopoint`, `geotrace`, `geoshape`) take input in the compact layout.
 *
 * - [Device]: the mobile widgets, which capture the device's GPS fix and offer a pannable mini-map
 *   when the question's appearance allows it.
 * - [MapDrawing]: a request to draw on the host's map ([MapDrawingRequestWidget]). This is the only
 *   input the web dashboard offers, since a browser has no field GPS trace to follow: "GPS only"
 *   questions are presented as map drawing too. A `null` [MapDrawing.host] renders the request
 *   without any map wiring, for read-only previews.
 */
sealed interface CompactGeometryInput {
  data object Device : CompactGeometryInput

  data class MapDrawing(val host: CompactMapDrawingHost? = null) : CompactGeometryInput
}

/** The vertices currently stored for a geometry question of [kind]. */
fun mapDrawingVertices(fieldState: FieldState, kind: MapDrawingKind): List<GeoPoint> {
  val scalar = fieldState.value?.scalar_value ?: return emptyList()
  return when (kind) {
    MapDrawingKind.POINT -> listOfNotNull(scalar.geopoint_value)
    MapDrawingKind.LINE -> scalar.geotrace_value?.points.orEmpty()
    MapDrawingKind.POLYGON -> scalar.geoshape_value?.points.orEmpty()
  }
}

/** Writes [points] as the answer of the geometry question at [path]; empty clears it. */
fun FormWizardController.setMapDrawingVertices(
  path: String,
  kind: MapDrawingKind,
  points: List<GeoPoint>,
) {
  when (kind) {
    MapDrawingKind.POINT -> {
      val point = points.lastOrNull()
      if (point == null) clearField(path)
      else {
        updateGeoPoint(
          path = path,
          latitude = point.latitude,
          longitude = point.longitude,
          altitudeMeters = point.altitude_meters,
          accuracyMeters = point.accuracy_meters,
        )
      }
    }
    MapDrawingKind.LINE -> updateGeoTrace(path, points)
    MapDrawingKind.POLYGON -> updateGeoShape(path, points)
  }
}

/**
 * Adds a map click at [latitude] / [longitude] to the geometry question at [path]: a point is
 * replaced, a line or polygon gets a new vertex. Returns the vertices after the change, so hosts
 * can finish drawing once a [MapDrawingKind.POINT] is placed.
 */
fun FormWizardController.addMapDrawingVertex(
  path: String,
  kind: MapDrawingKind,
  latitude: Double,
  longitude: Double,
): List<GeoPoint> {
  val current = formState.fieldStates[path]?.let { mapDrawingVertices(it, kind) }.orEmpty()
  val vertex = GeoPoint(latitude = latitude, longitude = longitude, accuracy_meters = 0.0)
  val updated = if (kind == MapDrawingKind.POINT) listOf(vertex) else current + vertex
  setMapDrawingVertices(path, kind, updated)
  return updated
}

/** Removes the last vertex of the geometry question at [path]. */
fun FormWizardController.undoMapDrawingVertex(path: String, kind: MapDrawingKind) {
  val current = formState.fieldStates[path]?.let { mapDrawingVertices(it, kind) }.orEmpty()
  setMapDrawingVertices(path, kind, current.dropLast(1))
}

/** Collector-facing instruction for drawing a geometry of [kind] on the map. */
fun mapDrawingInstruction(kind: MapDrawingKind): String =
  when (kind) {
    MapDrawingKind.POINT -> "Click the map to place this point."
    MapDrawingKind.LINE -> "Click the map to add the line's vertices, in order (at least 2)."
    MapDrawingKind.POLYGON -> "Click the map to add the polygon's corners, in order (at least 3)."
  }

/** One-line summary of what has been drawn so far, e.g. `"3 vertices"` or `"No point yet"`. */
fun mapDrawingSummary(kind: MapDrawingKind, vertexCount: Int): String =
  when {
    kind == MapDrawingKind.POINT && vertexCount == 0 -> "No point yet"
    kind == MapDrawingKind.POINT -> "Point placed"
    vertexCount == 1 -> "1 vertex"
    else -> "$vertexCount vertices"
  }

/**
 * The compact layout's input for a geometry question when the host provides map drawing
 * ([CompactGeometryInput.MapDrawing]): an instruction to click the map, the drawing status, and
 * **Draw on map** / **Done** / **Undo** / **Clear** actions. Vertices themselves arrive through the
 * host ([CompactMapDrawingHost]), which calls [addMapDrawingVertex] for each map click. Hosts that
 * can frame geometry ([CompactMapDrawingHost.canFrameGeometry]) also get a **Zoom to fit** button
 * once something is drawn.
 *
 * With a `null` [host] or [readOnly], the actions are disabled: the card documents the interaction
 * for the Form designer's web preview without a live map.
 */
@Composable
fun MapDrawingRequestWidget(
  control: ComponentState.ControlState,
  controller: FormWizardController,
  host: CompactMapDrawingHost?,
  readOnly: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val path = control.canonicalPath
  val fieldState = control.fieldState
  val kind = mapDrawingKindOf(fieldState.dataType) ?: return
  val vertices = mapDrawingVertices(fieldState, kind)
  val isDrawing = host?.activeDrawingPath == path
  val isComplete = vertices.size >= kind.minVertices
  val interactive = host != null && !readOnly
  val colors = MaterialTheme.colorScheme

  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Surface(
      shape = MaterialTheme.shapes.small,
      color = if (isDrawing) colors.primaryContainer else colors.secondaryContainer,
      contentColor = if (isDrawing) colors.onPrimaryContainer else colors.onSecondaryContainer,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          imageVector = Icons.Default.Place,
          contentDescription = null,
          modifier = Modifier.size(20.dp),
        )
        Text(
          text =
            if (isDrawing) mapDrawingInstruction(kind)
            else "Drawn on the map: ${kind.label.lowercase()}. Use Draw on map to start.",
          style = MaterialTheme.typography.bodySmall,
        )
      }
    }

    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      GroundTonalBadge(
        text = mapDrawingSummary(kind, vertices.size),
        icon = if (isComplete) Icons.Default.Check else null,
        tone =
          when {
            isComplete -> GroundBadgeTone.PRIMARY
            vertices.isNotEmpty() -> GroundBadgeTone.WARNING
            else -> GroundBadgeTone.NEUTRAL
          },
      )
      if (isDrawing) {
        GroundTonalBadge(
          text = "Drawing on map",
          icon = Icons.Outlined.Edit,
          tone = GroundBadgeTone.TERTIARY,
        )
      }
      if (kind == MapDrawingKind.POINT && vertices.isNotEmpty()) {
        val point = vertices.first()
        GroundTonalBadge(
          text = formatGeoPointCoordinates(point.latitude, point.longitude),
          tone = GroundBadgeTone.NEUTRAL,
          monospace = true,
        )
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (isDrawing) {
        Button(onClick = { host?.stopDrawing() }, enabled = interactive && isComplete) {
          Text("Done")
        }
        OutlinedButton(
          onClick = { controller.undoMapDrawingVertex(path, kind) },
          enabled = interactive && vertices.isNotEmpty(),
        ) {
          Text("Undo")
        }
      } else {
        FilledTonalButton(onClick = { host?.startDrawing(path, kind) }, enabled = interactive) {
          Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Text(
            text = if (vertices.isEmpty()) "Draw on map" else "Redraw",
            modifier = Modifier.padding(start = 6.dp),
          )
        }
      }
      if (vertices.isNotEmpty()) {
        TextButton(
          onClick = { controller.setMapDrawingVertices(path, kind, emptyList()) },
          enabled = interactive,
        ) {
          Text("Clear")
        }
      }
      // Zoom to fit: centre whatever has been drawn in the host's map, for any geometry kind (a
      // point or line can be off-screen just as easily as a polygon).
      if (host != null && !readOnly && host.canFrameGeometry && vertices.isNotEmpty()) {
        Spacer(modifier = Modifier.weight(1f))
        @OptIn(ExperimentalMaterial3Api::class)
        TooltipBox(
          positionProvider =
            TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
          tooltip = { PlainTooltip { Text("Zoom to fit") } },
          state = rememberTooltipState(),
        ) {
          IconButton(onClick = { host.frameGeometry(path, kind) }) {
            Icon(imageVector = FitScreenIcon, contentDescription = "Zoom to fit")
          }
        }
      }
    }
  }
}

/**
 * Material Symbols `center_focus_strong` (Apache 2.0): a filled disc inside four corner brackets,
 * used for the **Zoom to fit** action. Built by hand because `shared/ui` ships only the core
 * Material icon set.
 */
val FitScreenIcon: ImageVector by lazy {
  ImageVector.Builder(
      name = "FitScreen",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
    )
    .apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(12f, 8f)
        curveToRelative(-2.21f, 0f, -4f, 1.79f, -4f, 4f)
        reflectiveCurveToRelative(1.79f, 4f, 4f, 4f)
        reflectiveCurveToRelative(4f, -1.79f, 4f, -4f)
        reflectiveCurveToRelative(-1.79f, -4f, -4f, -4f)
        close()
        moveTo(5f, 15f)
        horizontalLineTo(3f)
        verticalLineToRelative(4f)
        curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
        horizontalLineToRelative(4f)
        verticalLineToRelative(-2f)
        horizontalLineTo(5f)
        verticalLineToRelative(-4f)
        close()
        moveTo(5f, 5f)
        horizontalLineToRelative(4f)
        verticalLineTo(3f)
        horizontalLineTo(5f)
        curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
        verticalLineToRelative(4f)
        horizontalLineToRelative(2f)
        verticalLineTo(5f)
        close()
        moveTo(19f, 3f)
        horizontalLineToRelative(-4f)
        verticalLineToRelative(2f)
        horizontalLineToRelative(4f)
        verticalLineToRelative(4f)
        horizontalLineToRelative(2f)
        verticalLineTo(5f)
        curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
        close()
        moveTo(19f, 19f)
        horizontalLineToRelative(-4f)
        verticalLineToRelative(2f)
        horizontalLineToRelative(4f)
        curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
        verticalLineToRelative(-4f)
        horizontalLineToRelative(-2f)
        verticalLineToRelative(4f)
        close()
      }
    }
    .build()
}
