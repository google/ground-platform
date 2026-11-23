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
package org.openforis.collectone.v2.web.ui.surveyeditor

import androidx.compose.runtime.Composable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.Measurement
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.scaleBarLength
import org.openforis.collectone.v2.map.CameraPosition
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.surveyeditor_basemap_none
import org.openforis.collectone.v2.web.resources.surveyeditor_basemap_satellite
import org.openforis.collectone.v2.web.resources.surveyeditor_basemap_terrain

/** A screen-space point in dp, relative to the map viewport's top-left corner. */
data class ScreenPoint(val x: Double, val y: Double)

/** Basemap imagery shown beneath the layer features in the Map layer editor. */
enum class EditorBasemap(val label: String) {
  SATELLITE("Satellite"),
  TERRAIN("Terrain"),
  NONE("None"),
}

/** Display name of this basemap, in the user's language. */
internal val EditorBasemap.displayLabel: String
  @Composable
  get() =
    stringResource(
      when (this) {
        EditorBasemap.SATELLITE -> Res.string.surveyeditor_basemap_satellite
        EditorBasemap.TERRAIN -> Res.string.surveyeditor_basemap_terrain
        EditorBasemap.NONE -> Res.string.surveyeditor_basemap_none
      }
    )

/** Where editor maps start before they fit their content. */
internal val DEFAULT_EDITOR_CAMERA = CameraPosition(LatLng(-0.4185, 36.9520), 14.0)

/** Arithmetic mean of these vertices, or `null` when empty. */
internal fun List<LatLng>.centroidOrNull(): LatLng? {
  if (isEmpty()) return null
  var latSum = 0.0
  var lngSum = 0.0
  for (p in this) {
    latSum += p.latitude
    lngSum += p.longitude
  }
  return LatLng(latSum / size, lngSum / size)
}

/** A "nice" scale bar length (1, 2 or 5 × 10ⁿ meters) that fits within [maxWidthPx]. */
data class ScaleBar(val meters: Double, val widthPx: Double) {
  /** [meters] as the bar is labeled, e.g. `200 m` or `5 km`. */
  val length: Measurement
    get() = scaleBarLength(meters)

  companion object {
    private const val EARTH_CIRCUMFERENCE_M = 40_075_016.686
    /** Web Mercator world size in dp at zoom 0, as used by Mapbox and the map module. */
    private const val WORLD_SIZE_DP = 512.0

    /** Ground distance covered by one dp at the center of [camera]. */
    fun metersPerPixel(camera: CameraPosition): Double =
      EARTH_CIRCUMFERENCE_M * cos(camera.center.latitude * PI / 180) /
        (WORLD_SIZE_DP * 2.0.pow(camera.zoom))

    fun forCamera(camera: CameraPosition, maxWidthPx: Double = 100.0): ScaleBar {
      val metersPerPixel = metersPerPixel(camera)
      val maxMeters = metersPerPixel * maxWidthPx
      val magnitude = 10.0.pow(floor(log10(maxMeters)))
      val nice =
        listOf(5.0, 2.0, 1.0).map { it * magnitude }.firstOrNull { it <= maxMeters } ?: magnitude
      return ScaleBar(nice, nice / metersPerPixel)
    }
  }
}

/** Squared distance from [p] to the segment `a–b`, in the same units as the inputs. */
internal fun distanceToSegmentSquared(p: ScreenPoint, a: ScreenPoint, b: ScreenPoint): Double {
  val dx = b.x - a.x
  val dy = b.y - a.y
  val len2 = dx * dx + dy * dy
  val t =
    if (len2 == 0.0) 0.0 else (((p.x - a.x) * dx + (p.y - a.y) * dy) / len2).coerceIn(0.0, 1.0)
  val cx = a.x + t * dx - p.x
  val cy = a.y + t * dy - p.y
  return cx * cx + cy * cy
}

/** Even-odd point-in-polygon test in screen space. */
internal fun polygonContains(polygon: List<ScreenPoint>, p: ScreenPoint): Boolean {
  var inside = false
  var j = polygon.lastIndex
  for (i in polygon.indices) {
    val a = polygon[i]
    val b = polygon[j]
    if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) {
      inside = !inside
    }
    j = i
  }
  return inside
}

/**
 * Finds the feature under [tap]: polygons by containment, lines and points by distance within
 * [tolerancePx]. Geometry is placed on screen with [project]. Later (top-most) rows win ties.
 */
internal fun hitTestFeature(
  dataset: EntityDataset,
  project: (LatLng) -> ScreenPoint,
  tap: ScreenPoint,
  tolerancePx: Double = 12.0,
): String? {
  val tol2 = tolerancePx * tolerancePx
  var best: String? = null
  var bestDistance = Double.MAX_VALUE
  for (row in dataset.rows) {
    if (row.geometry.isEmpty()) continue
    val pts = row.geometry.map(project)
    val d2 =
      when {
        dataset.geometryType == GeometryType.POLYGON &&
          pts.size >= 3 &&
          polygonContains(pts, tap) -> 0.0
        pts.size == 1 -> (pts[0].x - tap.x).let { it * it } + (pts[0].y - tap.y).let { it * it }
        else -> {
          val closed = dataset.geometryType == GeometryType.POLYGON
          val segments =
            pts.zipWithNext() + if (closed) listOf(pts.last() to pts.first()) else emptyList()
          val segmentDist2 = segments.minOf { (a, b) -> distanceToSegmentSquared(tap, a, b) }
          val center = row.geometry.centroidOrNull()?.let(project)
          val centerDist2 =
            if (center != null) {
              (center.x - tap.x).let { it * it } + (center.y - tap.y).let { it * it }
            } else {
              Double.MAX_VALUE
            }
          minOf(segmentDist2, centerDist2)
        }
      }
    if (d2 <= tol2 && d2 <= bestDistance) {
      best = row.key
      bestDistance = d2
    }
  }
  return best
}

/** Midpoints of each segment, used as "insert vertex" handles. */
internal fun midpoints(pts: List<ScreenPoint>, closed: Boolean): List<ScreenPoint> {
  if (pts.size < 2) return emptyList()
  val segments =
    pts.zipWithNext() +
      if (closed && pts.size >= 3) listOf(pts.last() to pts.first()) else emptyList()
  return segments.map { (a, b) -> ScreenPoint((a.x + b.x) / 2, (a.y + b.y) / 2) }
}
