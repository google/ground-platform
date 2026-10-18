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
package org.groundplatform.v2.core.geo.io

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import org.groundplatform.v2.core.sampling.GeoCoord
import org.groundplatform.v2.core.sampling.Ring

/**
 * Checks and normalizes polygon rings read from files.
 *
 * Problems that make a ring unusable (bad coordinates, fewer than three distinct vertices) are
 * errors and the ring is dropped. Problems that can be worked around are warnings: unclosed rings
 * are closed, and self-intersections and possible antimeridian crossings are reported but **not**
 * fixed, since any automatic fix could change the area being surveyed.
 */
object RingValidator {

  /** Result of [normalize]: the open ring (or `null` if unusable) plus any issues found. */
  data class Normalized(val ring: Ring?, val issues: List<GeoIssue>)

  /**
   * Validates [raw] (as read from the file, closed or not) and returns it as an open ring with
   * consecutive duplicate vertices removed.
   *
   * @param featureIndex 0-based feature index used in messages, or `null` for a lone polygon.
   */
  fun normalize(raw: List<GeoCoord>, featureIndex: Int? = null): Normalized {
    val name = featureName(featureIndex)
    if (raw.any { !isValidCoord(it) }) {
      return Normalized(
        null,
        listOf(
          GeoIssue(
            GeoIssueSeverity.ERROR,
            "$name has coordinates outside the valid range and was skipped. Use WGS 84 decimal " +
              "degrees (latitude −90 to 90, longitude −180 to 180).",
            featureIndex,
          )
        ),
      )
    }
    val issues = mutableListOf<GeoIssue>()
    val closed = raw.size >= 2 && raw.first() == raw.last()
    val open = if (closed) raw.dropLast(1) else raw
    val deduped = ArrayList<GeoCoord>(open.size)
    for (c in open) if (deduped.lastOrNull() != c) deduped += c
    while (deduped.size > 1 && deduped.first() == deduped.last()) deduped.removeAt(
      deduped.lastIndex
    )
    if (deduped.toSet().size < 3) {
      return Normalized(
        null,
        listOf(
          GeoIssue(
            GeoIssueSeverity.ERROR,
            "$name has fewer than 3 distinct corners and was skipped.",
            featureIndex,
          )
        ),
      )
    }
    if (!closed) {
      issues +=
        GeoIssue(
          GeoIssueSeverity.WARNING,
          "$name's outline wasn't closed, so it was closed automatically.",
          featureIndex,
        )
    }
    if (findSelfIntersection(deduped) != null) {
      issues +=
        GeoIssue(
          GeoIssueSeverity.WARNING,
          "$name's outline crosses itself. Areas and sample plots may be wrong; fix the boundary " +
            "in a GIS tool and upload it again.",
          featureIndex,
        )
    }
    val lngSpan = deduped.maxOf { it.lng } - deduped.minOf { it.lng }
    if (lngSpan > 180.0) {
      issues +=
        GeoIssue(
          GeoIssueSeverity.WARNING,
          "$name may cross the 180° meridian, which isn't supported yet. Split it into parts on " +
            "either side of the meridian.",
          featureIndex,
        )
    }
    return Normalized(deduped, issues)
  }

  fun isValidCoord(c: GeoCoord): Boolean =
    !c.lat.isNaN() && !c.lng.isNaN() && c.lat in -90.0..90.0 && c.lng in -180.0..180.0

  /** "Feature 3" (1-based) for messages, or "The polygon" when there's no index. */
  internal fun featureName(featureIndex: Int?): String =
    if (featureIndex == null) "The polygon" else "Feature ${featureIndex + 1}"

  /**
   * Whether the open [ring] is wound counter-clockwise (positive signed area in lng/lat space), as
   * GeoJSON (RFC 7946 section 3.1.6) recommends for exterior rings.
   */
  fun isCounterClockwise(ring: Ring): Boolean = signedArea(ring) > 0

  private fun signedArea(ring: Ring): Double {
    var sum = 0.0
    for (i in ring.indices) {
      val a = ring[i]
      val b = ring[(i + 1) % ring.size]
      sum += a.lng * b.lat - b.lng * a.lat
    }
    return sum / 2
  }

  /**
   * Returns the indices of two non-adjacent edges of the open [ring] that intersect (edge `i` joins
   * vertex `i` to vertex `i + 1`, wrapping around), or `null` if the outline is simple.
   *
   * Edges are bucketed into a uniform grid so large rings (tens of thousands of vertices) are
   * checked in roughly linear time.
   */
  fun findSelfIntersection(ring: Ring): Pair<Int, Int>? {
    val n = ring.size
    if (n < 4) return null
    if (n <= 64) {
      for (i in 0 until n) for (j in i + 2 until n) {
        if (adjacent(i, j, n)) continue
        if (edgesIntersect(ring, i, j)) return i to j
      }
      return null
    }
    var minX = Double.MAX_VALUE
    var minY = Double.MAX_VALUE
    var maxX = -Double.MAX_VALUE
    var maxY = -Double.MAX_VALUE
    for (c in ring) {
      minX = min(minX, c.lng)
      maxX = max(maxX, c.lng)
      minY = min(minY, c.lat)
      maxY = max(maxY, c.lat)
    }
    val side = ceil(sqrt(n.toDouble())).toInt().coerceIn(1, 1024)
    val cellW = ((maxX - minX) / side).takeIf { it > 0 } ?: 1.0
    val cellH = ((maxY - minY) / side).takeIf { it > 0 } ?: 1.0
    val cells = HashMap<Int, MutableList<Int>>()
    fun cellX(x: Double) = ((x - minX) / cellW).toInt().coerceIn(0, side - 1)
    fun cellY(y: Double) = ((y - minY) / cellH).toInt().coerceIn(0, side - 1)
    for (i in 0 until n) {
      val a = ring[i]
      val b = ring[(i + 1) % n]
      val x0 = cellX(min(a.lng, b.lng))
      val x1 = cellX(max(a.lng, b.lng))
      val y0 = cellY(min(a.lat, b.lat))
      val y1 = cellY(max(a.lat, b.lat))
      for (cx in x0..x1) for (cy in y0..y1) {
        cells.getOrPut(cy * side + cx) { mutableListOf() } += i
      }
    }
    for (edges in cells.values) {
      for (p in edges.indices) for (q in p + 1 until edges.size) {
        val i = edges[p]
        val j = edges[q]
        if (adjacent(i, j, n)) continue
        if (edgesIntersect(ring, i, j)) return min(i, j) to max(i, j)
      }
    }
    return null
  }

  private fun adjacent(i: Int, j: Int, n: Int): Boolean {
    val d = kotlin.math.abs(i - j)
    return d <= 1 || d == n - 1
  }

  private fun edgesIntersect(ring: Ring, i: Int, j: Int): Boolean {
    val n = ring.size
    return segmentsIntersect(ring[i], ring[(i + 1) % n], ring[j], ring[(j + 1) % n])
  }

  /** Whether segments `p1–p2` and `q1–q2` share at least one point (touching counts). */
  internal fun segmentsIntersect(p1: GeoCoord, p2: GeoCoord, q1: GeoCoord, q2: GeoCoord): Boolean {
    val d1 = orientation(q1, q2, p1)
    val d2 = orientation(q1, q2, p2)
    val d3 = orientation(p1, p2, q1)
    val d4 = orientation(p1, p2, q2)
    if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) {
      return true
    }
    return (d1 == 0.0 && onSegment(q1, q2, p1)) ||
      (d2 == 0.0 && onSegment(q1, q2, p2)) ||
      (d3 == 0.0 && onSegment(p1, p2, q1)) ||
      (d4 == 0.0 && onSegment(p1, p2, q2))
  }

  private fun orientation(a: GeoCoord, b: GeoCoord, c: GeoCoord): Double =
    (b.lng - a.lng) * (c.lat - a.lat) - (b.lat - a.lat) * (c.lng - a.lng)

  private fun onSegment(a: GeoCoord, b: GeoCoord, p: GeoCoord): Boolean =
    p.lng in min(a.lng, b.lng)..max(a.lng, b.lng) && p.lat in min(a.lat, b.lat)..max(a.lat, b.lat)
}
