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
package org.openforis.collectone.v2.app.domain.model.editor

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sin
import org.openforis.collectone.v2.app.domain.model.Measurement
import org.openforis.collectone.v2.app.domain.model.MeasurementUnit
import org.openforis.collectone.v2.core.geo.GeoCoord
import org.openforis.collectone.v2.core.geo.io.GeoSimplifier
import org.openforis.collectone.v2.map.LatLng

/** Geometry helpers for multi-part survey areas (bounds, area, display simplification). */
object SurveyAreaGeometry {
  private const val EARTH_RADIUS_M = 6_378_137.0

  /** Below this signed double area (square degrees), parts count as having no area. */
  private const val MIN_TWICE_AREA_DEG2 = 1e-12

  /** Center of the bounding box of all [parts], or `null` if there are no vertices. */
  fun boundsCenter(parts: List<List<LatLng>>): LatLng? {
    val pts = parts.flatten()
    if (pts.isEmpty()) return null
    return LatLng(
      latitude = (pts.minOf { it.latitude } + pts.maxOf { it.latitude }) / 2,
      longitude = (pts.minOf { it.longitude } + pts.maxOf { it.longitude }) / 2,
    )
  }

  /**
   * Area-weighted centroid of all [parts] in planar degrees (accurate for survey-sized areas away
   * from the antimeridian), or `null` if there are no vertices. Each part counts by its area,
   * whichever way its ring winds. Without any area (fewer than 3 vertices per part, or collinear
   * ones) it's the average of all vertices.
   */
  fun centroid(parts: List<List<LatLng>>): LatLng? {
    val vertices = parts.flatten()
    if (vertices.isEmpty()) return null
    var twiceArea = 0.0
    var weightedLat = 0.0
    var weightedLng = 0.0
    for (ring in parts.filter { it.size >= 3 }) {
      var ringTwiceArea = 0.0
      var ringLat = 0.0
      var ringLng = 0.0
      for (i in ring.indices) {
        val a = ring[i]
        val b = ring[(i + 1) % ring.size]
        val cross = a.longitude * b.latitude - b.longitude * a.latitude
        ringTwiceArea += cross
        ringLng += (a.longitude + b.longitude) * cross
        ringLat += (a.latitude + b.latitude) * cross
      }
      val orientation = if (ringTwiceArea < 0) -1.0 else 1.0
      twiceArea += orientation * ringTwiceArea
      weightedLat += orientation * ringLat
      weightedLng += orientation * ringLng
    }
    if (twiceArea < MIN_TWICE_AREA_DEG2) {
      return LatLng(
        latitude = vertices.sumOf { it.latitude } / vertices.size,
        longitude = vertices.sumOf { it.longitude } / vertices.size,
      )
    }
    return LatLng(
      latitude = weightedLat / (3 * twiceArea),
      longitude = weightedLng / (3 * twiceArea),
    )
  }

  /** A Web Mercator zoom level that roughly fits all [parts] in a ~300 dp viewport. */
  fun zoomToFit(parts: List<List<LatLng>>): Double {
    val pts = parts.flatten()
    if (pts.size < 2) return 12.0
    val span =
      maxOf(
        pts.maxOf { it.longitude } - pts.minOf { it.longitude },
        pts.maxOf { it.latitude } - pts.minOf { it.latitude },
      )
    if (span <= 0.0) return 16.0
    return (ln(360.0 / span) / ln(2.0) - 0.5).coerceIn(1.0, 16.0)
  }

  /**
   * Approximate area of an open ring on the WGS 84 sphere, in square meters (the spherical-excess
   * approximation used by common web mapping tools; accurate to well under 1% for survey areas).
   */
  fun ringAreaSquareMeters(ring: List<LatLng>): Double {
    if (ring.size < 3) return 0.0
    var total = 0.0
    for (i in ring.indices) {
      val a = ring[i]
      val b = ring[(i + 1) % ring.size]
      total +=
        (rad(b.longitude) - rad(a.longitude)) * (2 + sin(rad(a.latitude)) + sin(rad(b.latitude)))
    }
    return abs(total * EARTH_RADIUS_M * EARTH_RADIUS_M / 2.0)
  }

  /** Total area of all [parts] in square meters. Overlapping parts are counted twice. */
  fun areaSquareMeters(parts: List<List<LatLng>>): Double = parts.sumOf(::ringAreaSquareMeters)

  /**
   * An area of [squareMeters] to show: hectares to 2 decimal places below 10 ha, whole hectares
   * below 100 km² (`850 ha`), otherwise whole square kilometers (`1,234 km²`).
   */
  fun displayArea(squareMeters: Double): Measurement {
    val ha = squareMeters / 10_000.0
    return when {
      ha < 10 -> Measurement(kotlin.math.round(ha * 100) / 100, MeasurementUnit.HECTARE, 2)
      ha < 10_000 -> Measurement(kotlin.math.round(ha), MeasurementUnit.HECTARE, decimals = 0)
      else -> Measurement(kotlin.math.round(ha / 100), MeasurementUnit.SQUARE_KILOMETER, 0)
    }
  }

  /**
   * [parts] simplified for drawing (thumbnails, previews): each part keeps at most
   * [maxVerticesPerPart] vertices. Sampling and storage always use the full geometry.
   */
  fun displayParts(parts: List<List<LatLng>>, maxVerticesPerPart: Int = 400): List<List<LatLng>> =
    parts.map { part ->
      if (part.size <= maxVerticesPerPart) part
      else
        GeoSimplifier.simplifyRingToMaxVertices(part.map { it.toGeoCoord() }, maxVerticesPerPart)
          .map { it.toLatLng() }
    }

  fun groupThousands(v: Long): String {
    val digits = abs(v).toString()
    val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
    return if (v < 0) "-$grouped" else grouped
  }

  private fun rad(deg: Double) = deg * PI / 180.0
}

fun LatLng.toGeoCoord() = GeoCoord(lat = latitude, lng = longitude)

/** This coordinate as a [LatLng], clamped into range (see [LatLng.coerced]). */
fun GeoCoord.toLatLng() = LatLng.coerced(latitude = lat, longitude = lng)

/** Formats [v] with exactly [decimals] fraction digits (no platform `String.format` in common). */
fun formatFixed(v: Double, decimals: Int): String {
  var factor = 1L
  repeat(decimals) { factor *= 10 }
  val scaled = kotlin.math.round(kotlin.math.abs(v) * factor).toLong()
  val whole = scaled / factor
  val frac = (scaled % factor).toString().padStart(decimals, '0')
  val sign = if (v < 0 && scaled != 0L) "-" else ""
  return if (decimals == 0) "$sign$whole" else "$sign$whole.$frac"
}
