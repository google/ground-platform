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
package org.groundplatform.v2.devtools.prototypeapp.surveyeditor

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tan

/** A screen-space point in CSS pixels (density-independent), relative to the map viewport. */
data class ScreenPoint(val x: Double, val y: Double)

/** Basemap imagery shown beneath the layer features in the Map layer editor. */
enum class EditorBasemap(val label: String) {
  SATELLITE("Satellite"),
  TERRAIN("Terrain"),
  NONE("None"),
}

/**
 * Web Mercator camera for the interactive Map layer editor.
 *
 * Uses the same conventions as Mapbox GL JS (a 512 CSS px world at zoom 0), so features drawn by
 * Compose using [project] line up exactly with a Mapbox basemap rendered with the same center and
 * zoom.
 */
data class MapCamera(val center: LatLng, val zoom: Double) {

  /** Width/height of the whole world in CSS pixels at the current zoom. */
  val worldSize: Double
    get() = TILE_SIZE * 2.0.pow(zoom)

  /** Projects [p] to viewport coordinates for a viewport of `width × height` CSS pixels. */
  fun project(p: LatLng, width: Double, height: Double): ScreenPoint {
    val ws = worldSize
    return ScreenPoint(
      (mercatorX(p.lng) - mercatorX(center.lng)) * ws + width / 2,
      (mercatorY(p.lat) - mercatorY(center.lat)) * ws + height / 2,
    )
  }

  /** Inverse of [project]. */
  fun unproject(p: ScreenPoint, width: Double, height: Double): LatLng {
    val ws = worldSize
    val mx = mercatorX(center.lng) + (p.x - width / 2) / ws
    val my = mercatorY(center.lat) + (p.y - height / 2) / ws
    return LatLng(latFromMercatorY(my), lngFromMercatorX(mx))
  }

  /** Moves the content by `(dx, dy)` CSS pixels, as when dragging the map. */
  fun panBy(dx: Double, dy: Double, width: Double, height: Double): MapCamera =
    copy(
      center = clampLatLng(unproject(ScreenPoint(width / 2 - dx, height / 2 - dy), width, height))
    )

  /** Zooms by [delta] levels keeping the geographic point under [anchor] fixed on screen. */
  fun zoomAround(delta: Double, anchor: ScreenPoint, width: Double, height: Double): MapCamera {
    val newZoom = (zoom + delta).coerceIn(MIN_ZOOM, MAX_ZOOM)
    if (newZoom == zoom) return this
    val anchored = unproject(anchor, width, height)
    val zoomed = copy(zoom = newZoom)
    val drift = zoomed.project(anchored, width, height)
    return zoomed.panBy(anchor.x - drift.x, anchor.y - drift.y, width, height)
  }

  /** Ground distance covered by one CSS pixel at the camera center. */
  val metersPerPixel: Double
    get() = EARTH_CIRCUMFERENCE_M * cos(center.lat * PI / 180) / worldSize

  companion object {
    const val TILE_SIZE = 512.0
    const val MIN_ZOOM = 1.0
    const val MAX_ZOOM = 20.0
    const val MAX_LAT = 85.05112878
    private const val EARTH_CIRCUMFERENCE_M = 40_075_016.686

    val DEFAULT = MapCamera(LatLng(-0.4185, 36.9520), 14.0)

    /**
     * Returns a camera that shows all [points] inside a `width × height` viewport with [padding]
     * CSS pixels on each side. Single points (or empty input) use [singlePointZoom].
     */
    fun fit(
      points: List<LatLng>,
      width: Double,
      height: Double,
      padding: Double = 48.0,
      maxZoom: Double = 18.0,
      singlePointZoom: Double = 16.0,
    ): MapCamera {
      if (points.isEmpty()) return DEFAULT
      val minX = points.minOf { mercatorX(it.lng) }
      val maxX = points.maxOf { mercatorX(it.lng) }
      val minY = points.minOf { mercatorY(it.lat) }
      val maxY = points.maxOf { mercatorY(it.lat) }
      val center = LatLng(latFromMercatorY((minY + maxY) / 2), lngFromMercatorX((minX + maxX) / 2))
      val spanX = maxX - minX
      val spanY = maxY - minY
      if (spanX < 1e-12 && spanY < 1e-12) return MapCamera(center, min(singlePointZoom, maxZoom))
      val availW = max(width - 2 * padding, 1.0)
      val availH = max(height - 2 * padding, 1.0)
      val scale = min(availW / (spanX * TILE_SIZE), availH / (spanY * TILE_SIZE))
      return MapCamera(center, log2(scale).coerceIn(MIN_ZOOM, maxZoom))
    }

    fun mercatorX(lng: Double) = (lng + 180.0) / 360.0

    fun mercatorY(lat: Double): Double {
      val phi = lat.coerceIn(-MAX_LAT, MAX_LAT) * PI / 180
      return (1 - ln(tan(PI / 4 + phi / 2)) / PI) / 2
    }

    fun lngFromMercatorX(x: Double) = x * 360.0 - 180.0

    fun latFromMercatorY(y: Double) = (2 * atan(exp((1 - 2 * y) * PI)) - PI / 2) * 180 / PI

    private fun clampLatLng(p: LatLng) =
      LatLng(p.lat.coerceIn(-MAX_LAT, MAX_LAT), ((p.lng + 540.0) % 360.0) - 180.0)
  }
}

/** A "nice" scale bar length (1, 2 or 5 × 10ⁿ meters) that fits within [maxWidthPx]. */
data class ScaleBar(val meters: Double, val widthPx: Double) {
  val label: String
    get() = if (meters >= 1000) "${formatNumber(meters / 1000)} km" else "${formatNumber(meters)} m"

  companion object {
    fun forCamera(camera: MapCamera, maxWidthPx: Double = 100.0): ScaleBar {
      val maxMeters = camera.metersPerPixel * maxWidthPx
      val magnitude = 10.0.pow(floor(log10(maxMeters)))
      val nice =
        listOf(5.0, 2.0, 1.0).map { it * magnitude }.firstOrNull { it <= maxMeters } ?: magnitude
      return ScaleBar(nice, nice / camera.metersPerPixel)
    }

    private fun formatNumber(v: Double): String =
      if (v == floor(v)) v.toLong().toString() else ((v * 10).toLong() / 10.0).toString()
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
 * Finds the feature under [tap] (screen CSS px): polygons by containment, lines and points by
 * distance within [tolerancePx]. Later (top-most) rows win ties.
 */
internal fun hitTestFeature(
  dataset: EntityDataset,
  camera: MapCamera,
  width: Double,
  height: Double,
  tap: ScreenPoint,
  tolerancePx: Double = 12.0,
): String? {
  val tol2 = tolerancePx * tolerancePx
  var best: String? = null
  var bestDistance = Double.MAX_VALUE
  for (row in dataset.rows) {
    if (row.geometry.isEmpty()) continue
    val pts = row.geometry.map { camera.project(it, width, height) }
    val d2 =
      when {
        dataset.geometryKind == GeometryKind.POLYGON &&
          pts.size >= 3 &&
          polygonContains(pts, tap) -> 0.0
        pts.size == 1 -> (pts[0].x - tap.x).let { it * it } + (pts[0].y - tap.y).let { it * it }
        else -> {
          val closed = dataset.geometryKind == GeometryKind.POLYGON
          val segments =
            pts.zipWithNext() + if (closed) listOf(pts.last() to pts.first()) else emptyList()
          segments.minOf { (a, b) -> distanceToSegmentSquared(tap, a, b) }
        }
      }
    if (d2 <= tol2 && d2 <= bestDistance) {
      best = row.key
      bestDistance = d2
    }
  }
  return best
}
