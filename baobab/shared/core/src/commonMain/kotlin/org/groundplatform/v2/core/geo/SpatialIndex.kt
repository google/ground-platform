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
package org.groundplatform.v2.core.geo

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * A polygon ring prepared for fast point-in-polygon queries.
 *
 * Containment uses the even–odd crossing rule in the lat/lng plane, i.e. edges are straight lines
 * in degrees as in GeoJSON (RFC 7946 section 3.1.1). Edges are bucketed into uniform latitude bands
 * so that a query only visits edges whose latitude span overlaps the query point; this keeps
 * queries near O(√n) for country-scale rings with tens of thousands of vertices. Rings crossing the
 * antimeridian are not supported.
 */
class IndexedRing(lats: DoubleArray, lngs: DoubleArray, count: Int = lats.size) {
  /** Vertex latitudes, without a repeated closing vertex. */
  val lats: DoubleArray
  /** Vertex longitudes, without a repeated closing vertex. */
  val lngs: DoubleArray
  val size: Int
  val bounds: BoundingBox

  private val bandCount: Int
  private val bandHeight: Double
  private val bandStart: IntArray
  private val bandEdges: IntArray

  init {
    var n = count
    if (n > 1 && lats[0] == lats[n - 1] && lngs[0] == lngs[n - 1]) n--
    this.lats = lats.copyOf(n)
    this.lngs = lngs.copyOf(n)
    size = n
    bounds = BoundingBox.of(this.lats, this.lngs, n)
    bandCount = if (n < 3) 1 else min(MAX_BANDS, max(1, n / 4))
    val height = if (bounds.isEmpty) 0.0 else bounds.maxLat - bounds.minLat
    bandHeight = if (height > 0) height / bandCount else 1.0
    // Two passes (count, then fill) to build a compact CSR layout of band → edge indices.
    val counts = IntArray(bandCount + 1)
    forEachEdgeBand { _, band -> counts[band + 1]++ }
    for (b in 0 until bandCount) counts[b + 1] += counts[b]
    bandStart = counts.copyOf()
    bandEdges = IntArray(counts[bandCount])
    val cursor = counts.copyOf()
    forEachEdgeBand { edge, band -> bandEdges[cursor[band]++] = edge }
  }

  private inline fun forEachEdgeBand(action: (edge: Int, band: Int) -> Unit) {
    if (size < 3) return
    for (i in 0 until size) {
      val j = if (i + 1 == size) 0 else i + 1
      val lo = bandOf(min(lats[i], lats[j]))
      val hi = bandOf(max(lats[i], lats[j]))
      for (b in lo..hi) action(i, b)
    }
  }

  private fun bandOf(lat: Double): Int =
    floor((lat - bounds.minLat) / bandHeight).toInt().coerceIn(0, bandCount - 1)

  /** Whether ([lat], [lng]) lies inside the ring (even–odd rule). */
  fun contains(lat: Double, lng: Double): Boolean {
    if (size < 3 || !bounds.contains(lat, lng)) return false
    val band = bandOf(lat)
    var inside = false
    for (k in bandStart[band] until bandStart[band + 1]) {
      val i = bandEdges[k]
      val j = if (i + 1 == size) 0 else i + 1
      val yi = lats[i]
      val yj = lats[j]
      if ((yi > lat) != (yj > lat)) {
        val xCross = lngs[i] + (lat - yi) * (lngs[j] - lngs[i]) / (yj - yi)
        if (lng < xCross) inside = !inside
      }
    }
    return inside
  }

  /** Geodesic area of the ring in hectares (see [Wgs84.ringAreaHa]). */
  fun areaHa(): Double = Wgs84.ringAreaHa(lats, lngs, size)

  private companion object {
    const val MAX_BANDS = 4096
  }
}

/**
 * Uniform-grid index over a set of [IndexedRing]s, answering "which ring contains this point" in
 * roughly constant time. Each grid cell lists the rings whose bounding box overlaps it, in ring
 * order, so the first containing ring wins when rings overlap.
 */
class RingSetIndex(val rings: List<IndexedRing>) {
  val bounds: BoundingBox = rings.fold(BoundingBox.EMPTY) { acc, r -> acc.union(r.bounds) }
  private val side: Int = min(256, max(1, (kotlin.math.sqrt(rings.size.toDouble()) * 2).toInt()))
  private val cellH: Double = cellSize(bounds.maxLat - bounds.minLat)
  private val cellW: Double = cellSize(bounds.maxLng - bounds.minLng)
  private val cells: Array<IntArray>

  init {
    val lists = Array(side * side) { ArrayList<Int>() }
    rings.forEachIndexed { index, ring ->
      if (ring.bounds.isEmpty || bounds.isEmpty) return@forEachIndexed
      val r0 = row(ring.bounds.minLat)
      val r1 = row(ring.bounds.maxLat)
      val c0 = col(ring.bounds.minLng)
      val c1 = col(ring.bounds.maxLng)
      for (r in r0..r1) for (c in c0..c1) lists[r * side + c].add(index)
    }
    cells = Array(lists.size) { lists[it].toIntArray() }
  }

  private fun cellSize(extent: Double): Double = if (extent > 0) extent / side else 1.0

  private fun row(lat: Double): Int =
    floor((lat - bounds.minLat) / cellH).toInt().coerceIn(0, side - 1)

  private fun col(lng: Double): Int =
    floor((lng - bounds.minLng) / cellW).toInt().coerceIn(0, side - 1)

  /** Index of the first ring containing the point, or -1. */
  fun find(lat: Double, lng: Double): Int {
    if (bounds.isEmpty || !bounds.contains(lat, lng)) return -1
    for (index in cells[row(lat) * side + col(lng)]) {
      if (rings[index].contains(lat, lng)) return index
    }
    return -1
  }
}

/**
 * Uniform-grid spatial hash over points in degrees, for "is any accepted point closer than D
 * meters" checks. Cell sizes are chosen so that every point within [minDistanceM] of a query lies
 * in the 3 × 3 cells around it, anywhere inside [bounds].
 */
class PointSpatialHash(private val bounds: BoundingBox, private val minDistanceM: Double) {
  private val cellLat: Double
  private val cellLng: Double
  private val cells = HashMap<Long, MutableList<Int>>()
  private val lats = ArrayList<Double>()
  private val lngs = ArrayList<Double>()

  init {
    // Use the smallest local radii in the box so cells are never narrower than minDistanceM.
    val maxAbsLat =
      max(kotlin.math.abs(bounds.minLat), kotlin.math.abs(bounds.maxLat)).coerceAtMost(89.9)
    val mPerDegLat = Wgs84.meridionalRadius(0.0) * kotlin.math.PI / 180
    val mPerDegLng =
      Wgs84.primeVerticalRadius(maxAbsLat) *
        kotlin.math.cos(maxAbsLat * kotlin.math.PI / 180) *
        kotlin.math.PI / 180
    val d = max(minDistanceM, 1e-3)
    cellLat = d / mPerDegLat
    cellLng = d / mPerDegLng
  }

  private fun key(r: Long, c: Long): Long = (r shl 32) xor (c and 0xFFFFFFFFL)

  private fun rowOf(lat: Double): Long = floor((lat - bounds.minLat) / cellLat).toLong()

  private fun colOf(lng: Double): Long = floor((lng - bounds.minLng) / cellLng).toLong()

  /** Whether some inserted point lies strictly closer than [minDistanceM] to ([lat], [lng]). */
  fun hasNeighborWithin(lat: Double, lng: Double): Boolean {
    if (minDistanceM <= 0) return false
    val r = rowOf(lat)
    val c = colOf(lng)
    for (dr in -1L..1L) for (dc in -1L..1L) {
      val list = cells[key(r + dr, c + dc)] ?: continue
      for (i in list) {
        if (Wgs84.localDistanceM(lat, lng, lats[i], lngs[i]) < minDistanceM) return true
      }
    }
    return false
  }

  fun insert(lat: Double, lng: Double) {
    if (minDistanceM <= 0) return
    val index = lats.size
    lats.add(lat)
    lngs.add(lng)
    cells.getOrPut(key(rowOf(lat), colOf(lng))) { ArrayList(2) }.add(index)
  }
}
