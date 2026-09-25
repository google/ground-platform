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
package org.groundplatform.v2.core.forms.xpath.functions

import groundplatform.v2.forms.GeoPoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.groundplatform.v2.core.forms.xpath.model.XPathValue

/**
 * Pure Kotlin Multiplatform geospatial geometry utilities for XForms functions:
 * - `distance(geotrace | geoshape | nodeset | pointA, pointB)`
 * - `area(geoshape | nodeset)`
 * - `geofence(geopoint, geoshape)`
 * - `intersects(geoshape | geotrace)`
 */
internal object GeoUtils {

  // Standard WGS84 Equatorial Radius in meters used by JavaRosa / XForms
  const val EARTH_RADIUS_METERS = 6378137.0

  private fun toRadians(deg: Double): Double = deg * PI / 180.0

  /** Extracts an ordered list of [GeoPoint] vertices from any [XPathValue]. */
  fun extractPoints(value: XPathValue): List<GeoPoint> =
    when (value) {
      is XPathValue.GeoPointVal -> listOf(value.value)
      is XPathValue.GeoTraceVal -> value.value.points
      is XPathValue.GeoShapeVal -> value.value.points
      is XPathValue.NodeSet -> value.nodes.flatMap { extractPoints(it.extractValue()) }
      is XPathValue.ValueList -> value.values.flatMap { extractPoints(it) }
      else -> {
        val str = value.toXPathString().trim()
        if (str.isEmpty()) emptyList()
        else if (str.contains(';')) {
          str.split(';').mapNotNull { XPathValue.parseGeoPointString(it) }
        } else {
          listOfNotNull(XPathValue.parseGeoPointString(str))
        }
      }
    }

  /** Haversine great-circle distance between two geographic points in meters. */
  fun haversineDistanceMeters(p1: GeoPoint, p2: GeoPoint): Double {
    val lat1 = toRadians(p1.latitude)
    val lat2 = toRadians(p2.latitude)
    val dLat = lat2 - lat1
    val dLon = toRadians(p2.longitude - p1.longitude)
    val a =
      sin(dLat / 2.0) * sin(dLat / 2.0) + cos(lat1) * cos(lat2) * sin(dLon / 2.0) * sin(dLon / 2.0)
    val c = 2.0 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    return EARTH_RADIUS_METERS * c
  }

  /** Total geodesic length along an ordered sequence of [points] in meters. */
  fun totalDistanceMeters(points: List<GeoPoint>): Double {
    if (points.size < 2) return 0.0
    var sum = 0.0
    for (i in 0 until points.size - 1) {
      sum += haversineDistanceMeters(points[i], points[i + 1])
    }
    return sum
  }

  /**
   * Computes enclosed surface area in square meters (`m^2`) for a polygon defined by [points] using
   * the spherical polygon excess / Shoelace projection formula (matching JavaRosa / XForms).
   */
  fun calculateAreaSquareMeters(points: List<GeoPoint>): Double {
    if (points.size < 3) return 0.0
    // Ensure closed ring
    val ring =
      if (
        points.first().latitude == points.last().latitude &&
          points.first().longitude == points.last().longitude
      ) {
        points
      } else {
        points + points.first()
      }
    if (ring.size < 4) return 0.0

    var areaAccumulator = 0.0
    for (i in 0 until ring.size - 1) {
      val p1 = ring[i]
      val p2 = ring[i + 1]
      val lon1 = toRadians(p1.longitude)
      val lon2 = toRadians(p2.longitude)
      val lat1 = toRadians(p1.latitude)
      val lat2 = toRadians(p2.latitude)
      areaAccumulator += (lon2 - lon1) * (2.0 + sin(lat1) + sin(lat2))
    }
    return abs(areaAccumulator * EARTH_RADIUS_METERS * EARTH_RADIUS_METERS / 2.0)
  }

  /**
   * Evaluates point-in-polygon inclusion using the ray-casting algorithm (`true` if [pt] lies
   * inside or on the boundary of [polygon]).
   */
  fun isPointInPolygon(pt: GeoPoint, polygon: List<GeoPoint>): Boolean {
    if (polygon.size < 3) return false
    var inside = false
    val x = pt.longitude
    val y = pt.latitude
    var j = polygon.size - 1
    for (i in polygon.indices) {
      val xi = polygon[i].longitude
      val yi = polygon[i].latitude
      val xj = polygon[j].longitude
      val yj = polygon[j].latitude

      // Check exact vertex or edge coincidence
      if (x == xi && y == yi) return true

      val intersect = ((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi + 1e-15) + xi)
      if (intersect) {
        inside = !inside
      }
      j = i
    }
    return inside
  }

  /**
   * Returns `true` if any non-adjacent segments of the polyline/polygon defined by [points] cross
   * or self-intersect.
   */
  fun hasSelfIntersection(points: List<GeoPoint>): Boolean {
    val n = points.size
    if (n < 4) return false
    val isClosedRing =
      points.first().latitude == points.last().latitude &&
        points.first().longitude == points.last().longitude
    val segCount = n - 1
    for (i in 0 until segCount) {
      for (j in i + 2 until segCount) {
        // Adjacent first and last segments of a closed ring share the start/end vertex
        if (isClosedRing && i == 0 && j == segCount - 1) continue
        if (segmentsIntersect(points[i], points[i + 1], points[j], points[j + 1])) {
          return true
        }
      }
    }
    return false
  }

  private fun segmentsIntersect(a: GeoPoint, b: GeoPoint, c: GeoPoint, d: GeoPoint): Boolean {
    val d1 = direction(c, d, a)
    val d2 = direction(c, d, b)
    val d3 = direction(a, b, c)
    val d4 = direction(a, b, d)
    if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) {
      return true
    }
    if (abs(d1) < 1e-12 && onSegment(c, d, a)) return true
    if (abs(d2) < 1e-12 && onSegment(c, d, b)) return true
    if (abs(d3) < 1e-12 && onSegment(a, b, c)) return true
    if (abs(d4) < 1e-12 && onSegment(a, b, d)) return true
    return false
  }

  private fun direction(pi: GeoPoint, pj: GeoPoint, pk: GeoPoint): Double =
    (pk.longitude - pi.longitude) * (pj.latitude - pi.latitude) -
      (pj.longitude - pi.longitude) * (pk.latitude - pi.latitude)

  private fun onSegment(pi: GeoPoint, pj: GeoPoint, pk: GeoPoint): Boolean =
    pk.longitude >= minOf(pi.longitude, pj.longitude) &&
      pk.longitude <= maxOf(pi.longitude, pj.longitude) &&
      pk.latitude >= minOf(pi.latitude, pj.latitude) &&
      pk.latitude <= maxOf(pi.latitude, pj.latitude)
}
