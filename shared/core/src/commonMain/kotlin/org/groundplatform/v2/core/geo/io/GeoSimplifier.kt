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

import kotlin.math.PI
import kotlin.math.cos
import org.groundplatform.v2.core.sampling.GeoCoord
import org.groundplatform.v2.core.sampling.Ring

/**
 * Douglas–Peucker line simplification, for **display only** (thumbnails, previews). Sampling and
 * storage always use the full-precision geometry.
 *
 * Distances are measured in degrees with longitude scaled by the cosine of the mean latitude, so
 * the tolerance behaves roughly the same in both directions away from the poles.
 */
object GeoSimplifier {

  /**
   * Simplifies an open polyline, always keeping its first and last points. [toleranceDeg] is the
   * maximum distance (in degrees of latitude) a removed point may be from the simplified line.
   */
  fun simplifyLine(points: List<GeoCoord>, toleranceDeg: Double): List<GeoCoord> {
    if (points.size <= 2 || toleranceDeg <= 0.0) return points
    val lngScale = cos(points.map { it.lat }.average() * PI / 180.0)
    val keep = BooleanArray(points.size)
    keep[0] = true
    keep[points.lastIndex] = true
    // Iterative rather than recursive so very long boundaries can't overflow the stack.
    val stack = ArrayDeque<Pair<Int, Int>>()
    stack.addLast(0 to points.lastIndex)
    val tol2 = toleranceDeg * toleranceDeg
    while (stack.isNotEmpty()) {
      val (start, end) = stack.removeLast()
      var maxD2 = -1.0
      var index = -1
      for (i in start + 1 until end) {
        val d2 = distanceToSegmentSquared(points[i], points[start], points[end], lngScale)
        if (d2 > maxD2) {
          maxD2 = d2
          index = i
        }
      }
      if (index >= 0 && maxD2 > tol2) {
        keep[index] = true
        stack.addLast(start to index)
        stack.addLast(index to end)
      }
    }
    return points.filterIndexed { i, _ -> keep[i] }
  }

  /**
   * Simplifies an open ring (no repeated closing vertex). The result always has at least three
   * vertices, so it still draws as a polygon.
   */
  fun simplifyRing(ring: Ring, toleranceDeg: Double): Ring {
    if (ring.size <= 4 || toleranceDeg <= 0.0) return ring
    // Split the ring at the vertex farthest from the first one and simplify both halves, so the
    // two anchors are well separated.
    val first = ring.first()
    val far =
      ring.indices.maxByOrNull { i ->
        val dLat = ring[i].lat - first.lat
        val dLng = ring[i].lng - first.lng
        dLat * dLat + dLng * dLng
      } ?: 0
    if (far == 0) return ring
    val a = simplifyLine(ring.subList(0, far + 1), toleranceDeg)
    val b = simplifyLine(ring.subList(far, ring.size) + first, toleranceDeg)
    val result = a + b.drop(1).dropLast(1)
    return if (result.size >= 3) result else ring
  }

  /**
   * Simplifies [ring] until it has at most [maxVertices] vertices (but at least three), picking the
   * smallest tolerance that does so. Rings that are already small enough are returned as is.
   */
  fun simplifyRingToMaxVertices(ring: Ring, maxVertices: Int): Ring {
    if (ring.size <= maxVertices.coerceAtLeast(3)) return ring
    val latSpan = ring.maxOf { it.lat } - ring.minOf { it.lat }
    val lngSpan = ring.maxOf { it.lng } - ring.minOf { it.lng }
    var lo = 0.0
    var hi = maxOf(latSpan, lngSpan, 1e-9)
    var best = simplifyRing(ring, hi)
    repeat(30) {
      val mid = (lo + hi) / 2
      val candidate = simplifyRing(ring, mid)
      if (candidate.size <= maxVertices) {
        best = candidate
        hi = mid
      } else {
        lo = mid
      }
    }
    return best
  }

  private fun distanceToSegmentSquared(
    p: GeoCoord,
    a: GeoCoord,
    b: GeoCoord,
    lngScale: Double,
  ): Double {
    val ax = a.lng * lngScale
    val bx = b.lng * lngScale
    val px = p.lng * lngScale
    val dx = bx - ax
    val dy = b.lat - a.lat
    val len2 = dx * dx + dy * dy
    val t =
      if (len2 == 0.0) 0.0 else (((px - ax) * dx + (p.lat - a.lat) * dy) / len2).coerceIn(0.0, 1.0)
    val cx = ax + t * dx - px
    val cy = a.lat + t * dy - p.lat
    return cx * cx + cy * cy
  }
}
