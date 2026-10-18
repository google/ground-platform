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
package org.groundplatform.v2.core.geo.s2

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** A closed interval on the real line; empty when `lo > hi` (`R1Interval`). */
internal data class R1Interval(val lo: Double, val hi: Double) {
  fun isEmpty(): Boolean = lo > hi

  fun center(): Double = 0.5 * (lo + hi)

  fun bound(i: Int): Double = if (i == 0) lo else hi

  fun intersects(other: R1Interval): Boolean =
    if (lo <= other.lo) other.lo <= hi && other.lo <= other.hi else lo <= other.hi && lo <= hi

  fun expanded(radius: Double): R1Interval =
    if (isEmpty()) this else R1Interval(lo - radius, hi + radius)

  fun intersection(other: R1Interval): R1Interval = R1Interval(max(lo, other.lo), min(hi, other.hi))

  companion object {
    fun fromPointPair(a: Double, b: Double): R1Interval =
      if (a <= b) R1Interval(a, b) else R1Interval(b, a)
  }
}

/**
 * A closed interval on the unit circle, in radians within `[-π, π]` (`S1Interval`). When `lo > hi`
 * the interval is "inverted" and wraps through ±π.
 */
internal class S1Interval private constructor(val lo: Double, val hi: Double) {

  fun isFull(): Boolean = (hi - lo) == 2 * PI

  fun isInverted(): Boolean = lo > hi

  fun isEmpty(): Boolean = lo - hi == 2 * PI

  fun bound(i: Int): Double = if (i == 0) lo else hi

  fun center(): Double {
    val center = 0.5 * (lo + hi)
    if (!isInverted()) return center
    return if (center <= 0) center + PI else center - PI
  }

  fun length(): Double {
    var length = hi - lo
    if (length >= 0) return length
    length += 2 * PI
    return if (length > 0) length else -1.0
  }

  fun intersects(other: S1Interval): Boolean {
    if (isEmpty() || other.isEmpty()) return false
    return if (isInverted()) {
      other.isInverted() || other.lo <= hi || other.hi >= lo
    } else if (other.isInverted()) {
      other.lo <= hi || other.hi >= lo
    } else {
      other.lo <= hi && other.hi >= lo
    }
  }

  fun expanded(radius: Double): S1Interval {
    if (isEmpty()) return this
    if (length() + 2 * radius >= 2 * PI - 1e-15) return full()
    var newLo = S2.remainderNear(lo - radius, 2 * PI)
    val newHi = S2.remainderNear(hi + radius, 2 * PI)
    if (newLo <= -PI) newLo = PI
    return of(newLo, newHi)
  }

  override fun equals(other: Any?): Boolean =
    other is S1Interval && lo == other.lo && hi == other.hi

  override fun hashCode(): Int = 31 * lo.hashCode() + hi.hashCode()

  override fun toString(): String = "S1Interval($lo, $hi)"

  companion object {
    fun full(): S1Interval = S1Interval(-PI, PI)

    /** Normalizing constructor: maps a `-π` endpoint to `π` unless the interval is full. */
    fun of(lo: Double, hi: Double): S1Interval {
      var clampedLo = lo
      var clampedHi = hi
      if (lo == -PI && hi != PI) clampedLo = PI
      if (hi == -PI && lo != PI) clampedHi = PI
      return S1Interval(clampedLo, clampedHi)
    }

    /** The shorter of the two intervals with endpoints [a] and [b]. */
    fun fromPointPair(a: Double, b: Double): S1Interval {
      val pa = if (a == -PI) PI else a
      val pb = if (b == -PI) PI else b
      return if (positiveDistance(pa, pb) <= PI) S1Interval(pa, pb) else S1Interval(pb, pa)
    }

    private fun positiveDistance(a: Double, b: Double): Double {
      val d = b - a
      if (d >= 0) return d
      return (b + PI) - (a - PI)
    }
  }
}

/** A spherical cap: the part of the unit sphere cut off by a plane (`S2Cap`). */
internal class S2Cap(val axis: S2Point, height: Double) {
  var height: Double = height
    private set

  fun isEmpty(): Boolean = height < 0

  fun angle(): Double = if (isEmpty()) -1.0 else 2 * asin(sqrt(0.5 * height))

  fun addPoint(p: S2Point) {
    if (isEmpty()) {
      error("addPoint on an empty cap is not supported")
    }
    val dist2 = (axis - p).norm2()
    height = max(height, ROUND_UP * 0.5 * dist2)
  }

  companion object {
    private const val ROUND_UP = 1.0 + 1.0 / 4503599627370496.0 // 1 + 2^-52

    fun fromAxisAngle(axis: S2Point, radians: Double): S2Cap = S2Cap(axis, heightForAngle(radians))

    private fun heightForAngle(radians: Double): Double {
      if (radians >= PI) return 2.0
      val d = sin(0.5 * radians)
      return 2 * d * d
    }
  }
}

/** A rectangle in latitude/longitude space, in radians (`S2LatLngRect`). */
internal class S2LatLngRect(val lat: R1Interval, val lng: S1Interval) {

  fun intersects(other: S2LatLngRect): Boolean =
    lat.intersects(other.lat) && lng.intersects(other.lng)

  /** Conservative test using the cell's padded bounding rectangle (`MayIntersect`). */
  fun mayIntersect(cell: S2Cell): Boolean = intersects(cell.rectBound())

  private fun vertexPoint(k: Int): S2Point =
    S2.latLngToPoint(lat.bound(k shr 1), lng.bound((k shr 1) xor (k and 1)))

  /** A cap that contains this rectangle (`S2LatLngRect::GetCapBound`). */
  fun capBound(): S2Cap {
    val poleZ: Double
    val poleAngle: Double
    if (lat.lo + lat.hi < 0) {
      poleZ = -1.0
      poleAngle = PI / 2.0 + lat.hi
    } else {
      poleZ = 1.0
      poleAngle = PI / 2.0 - lat.lo
    }
    val poleCap = S2Cap.fromAxisAngle(S2Point(0.0, 0.0, poleZ), poleAngle)

    val lngSpan = lng.hi - lng.lo
    if (S2.remainderNear(lngSpan, 2 * PI) >= 0 && lngSpan < 2 * PI) {
      val midCap = S2Cap.fromAxisAngle(S2.latLngToPoint(lat.center(), lng.center()), 0.0)
      for (k in 0 until 4) midCap.addPoint(vertexPoint(k))
      if (midCap.height < poleCap.height) return midCap
    }
    return poleCap
  }

  companion object {
    fun full(): S2LatLngRect = S2LatLngRect(FULL_LAT, S1Interval.full())

    val FULL_LAT: R1Interval = R1Interval(-PI / 2.0, PI / 2.0)

    /** The rectangle spanned by two corners given in radians (`S2LatLngRect::FromPointPair`). */
    fun fromPointPair(
      latA: Double,
      lngA: Double,
      latB: Double,
      lngB: Double,
    ): S2LatLngRect =
      S2LatLngRect(R1Interval.fromPointPair(latA, latB), S1Interval.fromPointPair(lngA, lngB))
  }
}

/** An S2 cell with its (u, v) bounds precomputed (`S2Cell`). */
internal class S2Cell
private constructor(
  val id: S2CellId,
  val face: Int,
  val level: Int,
  private val orientation: Int,
  /** `uv[0]` = (u lo, u hi), `uv[1]` = (v lo, v hi). */
  private val uv: Array<DoubleArray>,
) {

  /** The four children of this cell, in Hilbert curve order (`S2Cell::Subdivide`). */
  fun subdivide(): List<S2Cell> {
    val (uMid, vMid) = id.centerUv()
    return id.children().mapIndexed { pos, childId ->
      val ij = S2.POS_TO_IJ[orientation][pos]
      val i = ij shr 1
      val j = ij and 1
      val childUv = arrayOf(DoubleArray(2), DoubleArray(2))
      childUv[0][i] = uv[0][i]
      childUv[0][1 - i] = uMid
      childUv[1][j] = uv[1][j]
      childUv[1][1 - j] = vMid
      S2Cell(childId, face, level + 1, orientation xor S2.POS_TO_ORIENTATION[pos], childUv)
    }
  }

  private fun vertexLatitude(i: Int, j: Int): Double =
    S2.latitude(S2.faceUvToXyz(face, uv[0][i], uv[1][j]))

  private fun vertexLongitude(i: Int, j: Int): Double =
    S2.longitude(S2.faceUvToXyz(face, uv[0][i], uv[1][j]))

  /**
   * A latitude/longitude rectangle bounding this cell, padded by 2⁻⁵¹ radians to absorb rounding
   * error (`S2Cell::GetRectBound` as implemented by `s2sphere`).
   */
  fun rectBound(): S2LatLngRect {
    if (level > 0) {
      val u = uv[0][0] + uv[0][1]
      val v = uv[1][0] + uv[1][1]
      val i = if (S2.uAxisZ(face) == 0.0) (if (u < 0) 1 else 0) else (if (u > 0) 1 else 0)
      val j = if (S2.vAxisZ(face) == 0.0) (if (v < 0) 1 else 0) else (if (v > 0) 1 else 0)

      val lat =
        R1Interval.fromPointPair(vertexLatitude(i, j), vertexLatitude(1 - i, 1 - j))
          .expanded(MAX_ERROR)
          .intersection(S2LatLngRect.FULL_LAT)
      if (lat.lo == -PI / 2.0 || lat.hi == PI / 2.0) {
        return S2LatLngRect(lat, S1Interval.full())
      }
      val lng = S1Interval.fromPointPair(vertexLongitude(i, 1 - j), vertexLongitude(1 - i, j))
      return S2LatLngRect(lat, lng.expanded(MAX_ERROR))
    }

    val poleMinLat = asin(sqrt(1.0 / 3.0))
    return when (face) {
      0 -> S2LatLngRect(R1Interval(-PI / 4.0, PI / 4.0), S1Interval.of(-PI / 4.0, PI / 4.0))
      1 -> S2LatLngRect(R1Interval(-PI / 4.0, PI / 4.0), S1Interval.of(PI / 4.0, 3.0 * PI / 4.0))
      2 -> S2LatLngRect(R1Interval(poleMinLat, PI / 2.0), S1Interval.of(-PI, PI))
      3 ->
        S2LatLngRect(
          R1Interval(-PI / 4.0, PI / 4.0),
          S1Interval.of(3.0 * PI / 4.0, -3.0 * PI / 4.0),
        )
      4 -> S2LatLngRect(R1Interval(-PI / 4.0, PI / 4.0), S1Interval.of(-3.0 * PI / 4.0, -PI / 4.0))
      else -> S2LatLngRect(R1Interval(-PI / 2.0, -poleMinLat), S1Interval.of(-PI, PI))
    }
  }

  companion object {
    private const val MAX_ERROR = 1.0 / 2251799813685248.0 // 2^-51

    fun fromCellId(id: S2CellId): S2Cell {
      val (face, i, j, orientation) = id.toFaceIjOrientation()
      val level = id.level()
      val cellSize = S2CellId.sizeIj(level)
      val uv = arrayOf(DoubleArray(2), DoubleArray(2))
      for ((axis, ij) in intArrayOf(i, j).withIndex()) {
        val ijLo = ij and -cellSize
        val ijHi = ijLo + cellSize
        uv[axis][0] = S2.stToUv((1.0 / S2.MAX_SIZE) * ijLo)
        uv[axis][1] = S2.stToUv((1.0 / S2.MAX_SIZE) * ijHi)
      }
      return S2Cell(id, face, level, orientation, uv)
    }

    fun face(face: Int): S2Cell = fromCellId(S2CellId.fromFacePosLevel(face, 0uL, 0))
  }
}
