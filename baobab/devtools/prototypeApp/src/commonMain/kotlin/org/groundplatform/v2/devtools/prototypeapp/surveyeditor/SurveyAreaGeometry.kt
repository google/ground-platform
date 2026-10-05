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
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sin
import org.groundplatform.v2.core.geo.io.GeoSimplifier
import org.groundplatform.v2.core.sampling.GeoCoord

/** Geometry helpers for multi-part survey areas (bounds, area, display simplification). */
object SurveyAreaGeometry {
  private const val EARTH_RADIUS_M = 6_378_137.0

  /** Center of the bounding box of all [parts], or `null` if there are no vertices. */
  fun boundsCenter(parts: List<List<LatLng>>): LatLng? {
    val pts = parts.flatten()
    if (pts.isEmpty()) return null
    return LatLng(
      lat = (pts.minOf { it.lat } + pts.maxOf { it.lat }) / 2,
      lng = (pts.minOf { it.lng } + pts.maxOf { it.lng }) / 2,
    )
  }

  /** A Web Mercator zoom level that roughly fits all [parts] in a ~300 dp viewport. */
  fun zoomToFit(parts: List<List<LatLng>>): Double {
    val pts = parts.flatten()
    if (pts.size < 2) return 12.0
    val span =
      maxOf(
        pts.maxOf { it.lng } - pts.minOf { it.lng },
        pts.maxOf { it.lat } - pts.minOf { it.lat },
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
      total += (rad(b.lng) - rad(a.lng)) * (2 + sin(rad(a.lat)) + sin(rad(b.lat)))
    }
    return abs(total * EARTH_RADIUS_M * EARTH_RADIUS_M / 2.0)
  }

  /** Total area of all [parts] in square meters. Overlapping parts are counted twice. */
  fun areaSquareMeters(parts: List<List<LatLng>>): Double = parts.sumOf(::ringAreaSquareMeters)

  /** "850 ha" below 100 km², otherwise "1,234 km²". */
  fun formatArea(squareMeters: Double): String {
    val ha = squareMeters / 10_000.0
    return when {
      ha < 10 -> "${formatFixed(ha, 2)} ha"
      ha < 10_000 -> "${groupThousands(kotlin.math.round(ha).toLong())} ha"
      else -> "${groupThousands(kotlin.math.round(ha / 100).toLong())} km²"
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

  internal fun groupThousands(v: Long): String {
    val digits = abs(v).toString()
    val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
    return if (v < 0) "-$grouped" else grouped
  }

  private fun rad(deg: Double) = deg * PI / 180.0
}

internal fun LatLng.toGeoCoord() = GeoCoord(lat = lat, lng = lng)

internal fun GeoCoord.toLatLng() = LatLng(lat = lat, lng = lng)
