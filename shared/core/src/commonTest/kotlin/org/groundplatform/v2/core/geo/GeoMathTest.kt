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

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeoMathTest {
  /**
   * A lat/lng quadrangle densified along its parallels, so great-circle edges follow the parallels
   * closely. Returned counter-clockwise without a closing vertex.
   */
  private fun quad(
    south: Double,
    west: Double,
    north: Double,
    east: Double,
    n: Int = 2000,
  ): Pair<DoubleArray, DoubleArray> {
    val lats = ArrayList<Double>()
    val lngs = ArrayList<Double>()
    for (i in 0..n) {
      lats.add(south)
      lngs.add(west + (east - west) * i / n)
    }
    for (i in 0..n) {
      lats.add(north)
      lngs.add(east - (east - west) * i / n)
    }
    return lats.toDoubleArray() to lngs.toDoubleArray()
  }

  private fun assertRelative(expected: Double, actual: Double, tolerance: Double) {
    assertTrue(
      abs(actual - expected) / expected <= tolerance,
      "expected $expected ± ${tolerance * 100}% but was $actual",
    )
  }

  @Test
  fun ringAreaHa_matchesEllipsoidalQuadrangles() {
    // Reference values: numerical integration of M(φ)·N(φ)·cos φ over each WGS 84 quadrangle.
    val (a1, b1) = quad(0.0, 0.0, 1.0, 1.0)
    assertRelative(1_230_846.39, Wgs84.ringAreaHa(a1, b1), 0.001)
    val (a2, b2) = quad(40.0, 0.0, 50.0, 10.0)
    assertRelative(87_509_769.07, Wgs84.ringAreaHa(a2, b2), 0.005)
    val (a3, b3) = quad(-35.0, 20.0, -20.0, 35.0)
    assertRelative(245_664_001.59, Wgs84.ringAreaHa(a3, b3), 0.005)
  }

  @Test
  fun ringAreaHa_ignoresOrientationAndClosingVertex() {
    val lats = doubleArrayOf(0.0, 0.0, 0.01, 0.01, 0.0)
    val lngs = doubleArrayOf(0.0, 0.01, 0.01, 0.0, 0.0)
    val ccw = Wgs84.ringAreaHa(lats, lngs)
    val cw = Wgs84.ringAreaHa(lats.reversedArray(), lngs.reversedArray())
    assertEquals(ccw, cw, 1e-9)
    assertRelative(123.08, ccw, 0.001)
  }

  @Test
  fun lambertAzimuthalEqualArea_roundTrips() {
    val proj = LambertAzimuthalEqualArea(-1.0, 37.0)
    val xy = DoubleArray(2)
    val ll = DoubleArray(2)
    for ((lat, lng) in listOf(-1.0 to 37.0, -1.5 to 37.8, 2.0 to 35.0, -10.0 to 45.0)) {
      proj.forward(lat, lng, xy)
      proj.inverse(xy[0], xy[1], ll)
      assertEquals(lat, ll[0], 1e-6)
      assertEquals(lng, ll[1], 1e-9)
    }
    // Near the center, projected distances match local ground distances on both axes.
    proj.forward(-1.0, 37.01, xy)
    assertEquals(Wgs84.localDistanceM(-1.0, 37.0, -1.0, 37.01), xy[0], 0.05)
    proj.forward(-0.99, 37.0, xy)
    assertEquals(Wgs84.localDistanceM(-1.0, 37.0, -0.99, 37.0), xy[1], 0.05)
    val north = LambertAzimuthalEqualArea(60.0, 10.0)
    north.forward(60.0, 10.02, xy)
    assertEquals(Wgs84.localDistanceM(60.0, 10.0, 60.0, 10.02), xy[0], 0.05)
    north.forward(60.01, 10.0, xy)
    assertEquals(Wgs84.localDistanceM(60.0, 10.0, 60.01, 10.0), xy[1], 0.05)
  }

  @Test
  fun indexedRing_containsWithConcaveShape() {
    // A "U" shape: the notch between the arms is outside.
    val lats = doubleArrayOf(0.0, 0.0, 3.0, 3.0, 1.0, 1.0, 3.0, 3.0)
    val lngs = doubleArrayOf(0.0, 3.0, 3.0, 2.0, 2.0, 1.0, 1.0, 0.0)
    val ring = IndexedRing(lats, lngs)
    assertTrue(ring.contains(0.5, 1.5))
    assertTrue(ring.contains(2.5, 0.5))
    assertTrue(ring.contains(2.5, 2.5))
    assertFalse(ring.contains(2.0, 1.5))
    assertFalse(ring.contains(-0.1, 1.5))
    assertFalse(ring.contains(1.5, 3.5))
  }

  @Test
  fun indexedRing_largeRingMatchesBruteForce() {
    // A star-shaped ring with 20k vertices.
    val n = 20_000
    val lats = DoubleArray(n)
    val lngs = DoubleArray(n)
    for (i in 0 until n) {
      val t = 2 * kotlin.math.PI * i / n
      val r = 1.0 + 0.3 * kotlin.math.sin(37 * t)
      lats[i] = r * kotlin.math.sin(t)
      lngs[i] = r * kotlin.math.cos(t)
    }
    val ring = IndexedRing(lats, lngs)
    fun brute(lat: Double, lng: Double): Boolean {
      var inside = false
      var j = n - 1
      for (i in 0 until n) {
        if (
          (lats[i] > lat) != (lats[j] > lat) &&
            lng < lngs[i] + (lat - lats[i]) * (lngs[j] - lngs[i]) / (lats[j] - lats[i])
        ) {
          inside = !inside
        }
        j = i
      }
      return inside
    }
    for (k in 0 until 500) {
      val lat = -1.4 + 2.8 * ((k * 7919) % 500) / 500.0
      val lng = -1.4 + 2.8 * ((k * 104729) % 499) / 499.0
      assertEquals(brute(lat, lng), ring.contains(lat, lng), "at $lat,$lng")
    }
  }

  @Test
  fun ringSetIndex_returnsFirstContainingRing() {
    val a = IndexedRing(doubleArrayOf(0.0, 0.0, 1.0, 1.0), doubleArrayOf(0.0, 1.0, 1.0, 0.0))
    val b = IndexedRing(doubleArrayOf(0.5, 0.5, 2.0, 2.0), doubleArrayOf(0.5, 2.0, 2.0, 0.5))
    val index = RingSetIndex(listOf(a, b))
    assertEquals(0, index.find(0.25, 0.25))
    assertEquals(0, index.find(0.75, 0.75))
    assertEquals(1, index.find(1.5, 1.5))
    assertEquals(-1, index.find(1.5, 0.25))
  }

  @Test
  fun pointSpatialHash_detectsNeighborsWithinDistance() {
    val hash = PointSpatialHash(BoundingBox(59.0, 10.0, 61.0, 12.0), 100.0)
    hash.insert(60.0, 11.0)
    val frame = LocalMetricFrame(60.0, 11.0)
    assertTrue(hash.hasNeighborWithin(frame.lat(60.0), frame.lng(60.0)))
    assertTrue(hash.hasNeighborWithin(60.0, frame.lng(99.0)))
    assertFalse(hash.hasNeighborWithin(60.0, frame.lng(101.0)))
    assertFalse(hash.hasNeighborWithin(frame.lat(101.0), 11.0))
  }

  @Test
  fun quantizeDegrees_roundsTo1e7() {
    assertEquals(12.3456789, quantizeDegrees(12.345678912))
    assertEquals(-0.0000001, quantizeDegrees(-0.00000006))
  }
}
