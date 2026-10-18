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
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Minimal pure-Kotlin port of the parts of the S2 geometry library needed to reproduce the AgStack
 * Asset Registry's GeoID computation bit-for-bit.
 *
 * AgStack computes its S2 coverings with the Python `s2sphere` package (v0.2.x), a port of the C++
 * S2 library. To produce identical cell tokens on every platform this file mirrors `s2sphere`'s
 * floating-point operations in the same order, including its approximate (padded) cell bounds,
 * rather than the newer, more precise algorithms in the current C++/Java/Go S2 releases. Function
 * names reference the corresponding `s2sphere` / C++ S2 symbol so the two can be compared directly.
 */

/** A point in 3D Euclidean space. Points on the unit sphere are "normalized". (`S2Point`) */
internal data class S2Point(val x: Double, val y: Double, val z: Double) {
  operator fun get(axis: Int): Double =
    when (axis) {
      0 -> x
      1 -> y
      else -> z
    }

  operator fun minus(other: S2Point): S2Point = S2Point(x - other.x, y - other.y, z - other.z)

  fun norm2(): Double = x * x + y * y + z * z

  fun norm(): Double = sqrt(norm2())

  fun normalize(): S2Point {
    var n = norm()
    if (n != 0.0) n = 1.0 / n
    return S2Point(x * n, y * n, z * n)
  }

  /** Index (0, 1, 2) of the component with the largest absolute value; ties favor the later one. */
  fun largestAbsComponent(): Int {
    val ax = abs(x)
    val ay = abs(y)
    val az = abs(z)
    return if (ax > ay) {
      if (ax > az) 0 else 2
    } else {
      if (ay > az) 1 else 2
    }
  }
}

/** Face index plus (u, v) coordinates on that cube face. */
internal data class FaceUv(val face: Int, val u: Double, val v: Double)

/** Face index plus leaf-cell (i, j) coordinates and Hilbert curve orientation. */
internal data class FaceIjOrientation(val face: Int, val i: Int, val j: Int, val orientation: Int)

/** Cube-face projections and constants shared by the S2 classes (`S2::` free functions). */
internal object S2 {
  const val MAX_LEVEL = 30
  const val MAX_SIZE = 1 shl MAX_LEVEL
  const val POS_BITS = 2 * MAX_LEVEL + 1
  const val SWAP_MASK = 0x01
  const val INVERT_MASK = 0x02
  private const val LOOKUP_BITS = 4

  /** Matches CPython's `math.radians`, which multiplies by the constant `pi / 180`. */
  private const val DEG_TO_RAD = PI / 180.0

  val POS_TO_IJ: Array<IntArray> =
    arrayOf(
      intArrayOf(0, 1, 3, 2),
      intArrayOf(0, 2, 3, 1),
      intArrayOf(3, 2, 0, 1),
      intArrayOf(3, 1, 0, 2),
    )

  val POS_TO_ORIENTATION: IntArray = intArrayOf(SWAP_MASK, 0, 0, INVERT_MASK or SWAP_MASK)

  /** Hilbert curve lookup tables mapping 4-bit (i, j) chunks to positions and back. */
  val LOOKUP_POS: IntArray = IntArray(1 shl (2 * LOOKUP_BITS + 2))
  val LOOKUP_IJ: IntArray = IntArray(1 shl (2 * LOOKUP_BITS + 2))

  init {
    initLookupCell(0, 0, 0, 0, 0, 0)
    initLookupCell(0, 0, 0, SWAP_MASK, 0, SWAP_MASK)
    initLookupCell(0, 0, 0, INVERT_MASK, 0, INVERT_MASK)
    initLookupCell(0, 0, 0, SWAP_MASK or INVERT_MASK, 0, SWAP_MASK or INVERT_MASK)
  }

  private fun initLookupCell(
    level: Int,
    i: Int,
    j: Int,
    origOrientation: Int,
    pos: Int,
    orientation: Int,
  ) {
    if (level == LOOKUP_BITS) {
      val ij = (i shl LOOKUP_BITS) + j
      LOOKUP_POS[(ij shl 2) + origOrientation] = (pos shl 2) + orientation
      LOOKUP_IJ[(pos shl 2) + origOrientation] = (ij shl 2) + orientation
      return
    }
    val r = POS_TO_IJ[orientation]
    for (index in 0 until 4) {
      initLookupCell(
        level + 1,
        (i shl 1) + (r[index] shr 1),
        (j shl 1) + (r[index] and 1),
        origOrientation,
        (pos shl 2) + index,
        orientation xor POS_TO_ORIENTATION[index],
      )
    }
  }

  fun degreesToRadians(degrees: Double): Double = degrees * DEG_TO_RAD

  /** Quadratic projection from cell-space (s or t) to cube-face (u or v) coordinates. */
  fun stToUv(s: Double): Double =
    if (s >= 0.5) (1.0 / 3.0) * (4 * s * s - 1) else (1.0 / 3.0) * (1 - 4 * (1 - s) * (1 - s))

  /** Inverse of [stToUv]. */
  fun uvToSt(u: Double): Double = if (u >= 0) 0.5 * sqrt(1 + 3 * u) else 1 - 0.5 * sqrt(1 - 3 * u)

  /** Leaf-cell coordinate containing the cell-space coordinate [s], clamped to the face. */
  fun stToIj(s: Double): Int {
    val scaled = kotlin.math.floor(MAX_SIZE.toDouble() * s)
    return when {
      scaled < 0.0 -> 0
      scaled > (MAX_SIZE - 1).toDouble() -> MAX_SIZE - 1
      else -> scaled.toInt()
    }
  }

  fun faceUvToXyz(face: Int, u: Double, v: Double): S2Point =
    when (face) {
      0 -> S2Point(1.0, u, v)
      1 -> S2Point(-u, 1.0, v)
      2 -> S2Point(-u, -v, 1.0)
      3 -> S2Point(-1.0, -v, -u)
      4 -> S2Point(v, -1.0, -u)
      else -> S2Point(v, u, -1.0)
    }

  private fun validFaceXyzToUv(face: Int, p: S2Point): FaceUv =
    when (face) {
      0 -> FaceUv(face, p.y / p.x, p.z / p.x)
      1 -> FaceUv(face, -p.x / p.y, p.z / p.y)
      2 -> FaceUv(face, -p.x / p.z, -p.y / p.z)
      3 -> FaceUv(face, p.z / p.x, p.y / p.x)
      4 -> FaceUv(face, p.z / p.y, -p.x / p.y)
      else -> FaceUv(face, -p.y / p.z, -p.x / p.z)
    }

  fun xyzToFaceUv(p: S2Point): FaceUv {
    var face = p.largestAbsComponent()
    if (p[face] < 0) face += 3
    return validFaceXyzToUv(face, p)
  }

  /** The z component of the face's u-axis (`S2::GetUAxis(face)[2]`). */
  fun uAxisZ(face: Int): Double = if (face == 3 || face == 4) -1.0 else 0.0

  /** The z component of the face's v-axis (`S2::GetVAxis(face)[2]`). */
  fun vAxisZ(face: Int): Double = if (face == 0 || face == 1) 1.0 else 0.0

  /** Latitude in radians of a (not necessarily unit-length) point (`S2LatLng::Latitude`). */
  fun latitude(p: S2Point): Double = atan2(p.z, sqrt(p.x * p.x + p.y * p.y))

  /** Longitude in radians of a (not necessarily unit-length) point (`S2LatLng::Longitude`). */
  fun longitude(p: S2Point): Double = atan2(p.y, p.x)

  /** Unit-sphere point for a latitude/longitude in radians (`S2LatLng::ToPoint`). */
  fun latLngToPoint(latRadians: Double, lngRadians: Double): S2Point {
    val cosPhi = cos(latRadians)
    return S2Point(cos(lngRadians) * cosPhi, sin(lngRadians) * cosPhi, sin(latRadians))
  }

  /**
   * IEEE 754 remainder of [x] / [y] (quotient rounded to nearest, ties to even), as computed by
   * `s2sphere`'s `drem` via `Decimal.remainder_near`.
   *
   * Only the range used by the S2 code here is supported: `y > 0` and `|x| <= 2.5 * y`. Within that
   * range every subtraction below is exact (Sterbenz lemma), so the result matches the exact
   * decimal computation.
   */
  fun remainderNear(x: Double, y: Double): Double {
    require(y > 0 && abs(x) <= 2.5 * y) { "remainderNear: unsupported operands $x, $y" }
    val sign = if (x < 0) -1.0 else 1.0
    val ax = abs(x)
    val half = 0.5 * y
    if (ax <= half) return x // quotient 0 (a tie at exactly y/2 rounds to the even quotient 0)
    val r1 = ax - y // quotient 1
    if (r1 < half) return sign * r1
    if (r1 == half) return sign * -half // tie between quotients 1 and 2 → even quotient 2
    return sign * (ax - 2 * y) // quotient 2 (a tie with 3 rounds to the even quotient 2)
  }

  /**
   * Binary exponent `e` such that `v = m * 2^e` with `0.5 <= |m| < 1`, matching Python's
   * `math.frexp(v)[1]` (which returns 0 for zero, infinities, and NaN).
   */
  fun frexpExponent(v: Double): Int {
    if (v == 0.0 || v.isNaN() || v.isInfinite()) return 0
    val biased = ((v.toRawBits() ushr 52) and 0x7ff).toInt()
    if (biased == 0) {
      // Subnormal: scale into the normal range first.
      val scaled = v * 18014398509481984.0 // 2^54
      return ((scaled.toRawBits() ushr 52) and 0x7ff).toInt() - 1022 - 54
    }
    return biased - 1022
  }
}
