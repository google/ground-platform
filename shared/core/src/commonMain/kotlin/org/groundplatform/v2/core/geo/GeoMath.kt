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

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * WGS 84 ellipsoid helpers: authalic (equal-area) latitude, geodesic polygon area and local meter ↔
 * degree conversions.
 *
 * Formulas follow Snyder, "Map Projections: A Working Manual" (USGS Professional Paper 1395, 1987),
 * equations 3-11, 3-12 and 3-18. Polygon edges are treated as great-circle arcs on the authalic
 * sphere, which maps ellipsoidal area exactly; for densely sampled rings the result agrees with
 * ellipsoidal geodesic-polygon tools to well under 0.5%.
 */
object Wgs84 {
  /** Semi-major axis in meters. */
  const val A: Double = 6_378_137.0
  /** Flattening. */
  const val F: Double = 1.0 / 298.257223563
  /** First eccentricity squared. */
  const val E2: Double = F * (2 - F)

  private val E: Double = sqrt(E2)

  /** Snyder eq. 3-12 evaluated at the pole. */
  private val QP: Double = q(1.0)

  /** Radius of the sphere with the same surface area as the ellipsoid (≈ 6,371,007 m). */
  val AUTHALIC_RADIUS: Double = A * sqrt(QP / 2)

  private const val DEG = PI / 180

  // Snyder eq. 3-18 series coefficients (authalic → geodetic latitude).
  private val C2: Double = E2 / 3 + 31 * E2 * E2 / 180 + 517 * E2 * E2 * E2 / 5040
  private val C4: Double = 23 * E2 * E2 / 360 + 251 * E2 * E2 * E2 / 3780
  private val C6: Double = 761 * E2 * E2 * E2 / 45360

  /** Snyder eq. 3-12, as a function of sin(φ). */
  private fun q(sinPhi: Double): Double {
    val es = E * sinPhi
    return (1 - E2) * (sinPhi / (1 - es * es) - (1 / (2 * E)) * ln((1 - es) / (1 + es)))
  }

  /** Authalic latitude β (radians) for geodetic latitude φ (radians), Snyder eq. 3-11. */
  fun authalicLatitude(phiRad: Double): Double {
    val ratio = (q(sin(phiRad)) / QP).coerceIn(-1.0, 1.0)
    return asin(ratio)
  }

  /** Geodetic latitude φ (radians) for authalic latitude β (radians), Snyder eq. 3-18. */
  fun geodeticLatitude(betaRad: Double): Double =
    betaRad + C2 * sin(2 * betaRad) + C4 * sin(4 * betaRad) + C6 * sin(6 * betaRad)

  /** Meridional radius of curvature M(φ) in meters. */
  fun meridionalRadius(latDeg: Double): Double {
    val s = sin(latDeg * DEG)
    val w = 1 - E2 * s * s
    return A * (1 - E2) / (w * sqrt(w))
  }

  /** Prime-vertical radius of curvature N(φ) in meters. */
  fun primeVerticalRadius(latDeg: Double): Double {
    val s = sin(latDeg * DEG)
    return A / sqrt(1 - E2 * s * s)
  }

  /**
   * Area of a simple ring of WGS 84 coordinates, in square meters. [lats] and [lngs] hold the
   * vertices in degrees; a repeated closing vertex is allowed. Orientation does not matter.
   *
   * Uses the spherical-excess form `E = 2·atan2(tan(Δλ/2)·(tan(β₁/2) + tan(β₂/2)), 1 + tan(β₁/2)
   * ·tan(β₂/2))` per edge on the authalic sphere. Rings crossing the antimeridian are not
   * supported.
   */
  fun ringAreaM2(lats: DoubleArray, lngs: DoubleArray, count: Int = lats.size): Double {
    if (count < 3) return 0.0
    var sum = 0.0
    var prevT = tan(authalicLatitude(lats[count - 1] * DEG) / 2)
    var prevLng = lngs[count - 1] * DEG
    for (i in 0 until count) {
      val t = tan(authalicLatitude(lats[i] * DEG) / 2)
      val lng = lngs[i] * DEG
      var dLng = lng - prevLng
      if (dLng > PI) dLng -= 2 * PI else if (dLng < -PI) dLng += 2 * PI
      sum += 2 * atan2(tan(dLng / 2) * (prevT + t), 1 + prevT * t)
      prevT = t
      prevLng = lng
    }
    return abs(sum) * AUTHALIC_RADIUS * AUTHALIC_RADIUS
  }

  /** [ringAreaM2] in hectares. */
  fun ringAreaHa(lats: DoubleArray, lngs: DoubleArray, count: Int = lats.size): Double =
    ringAreaM2(lats, lngs, count) / 10_000.0

  /**
   * Approximate distance in meters between two nearby points, using the local radii of curvature at
   * their mean latitude. Accurate to ~0.1% for separations up to tens of kilometers, which is what
   * minimum-distance checks need.
   */
  fun localDistanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val midLat = (lat1 + lat2) / 2
    val dy = (lat2 - lat1) * DEG * meridionalRadius(midLat)
    val dx = (lng2 - lng1) * DEG * primeVerticalRadius(midLat) * cos(midLat * DEG)
    return sqrt(dx * dx + dy * dy)
  }
}

/** Rounds [deg] to the 1e-7° grid (≈ 1.1 cm) used for cross-platform reproducibility. */
fun quantizeDegrees(deg: Double): Double = round(deg * 1e7) / 1e7

/** Axis-aligned bounding box in degrees. Empty when [minLat] > [maxLat]. */
data class BoundingBox(
  val minLat: Double,
  val minLng: Double,
  val maxLat: Double,
  val maxLng: Double,
) {
  val isEmpty: Boolean
    get() = minLat > maxLat || minLng > maxLng

  fun contains(lat: Double, lng: Double): Boolean =
    lat >= minLat && lat <= maxLat && lng >= minLng && lng <= maxLng

  fun intersects(other: BoundingBox): Boolean =
    !isEmpty &&
      !other.isEmpty &&
      other.minLat <= maxLat &&
      other.maxLat >= minLat &&
      other.minLng <= maxLng &&
      other.maxLng >= minLng

  fun union(other: BoundingBox): BoundingBox =
    when {
      isEmpty -> other
      other.isEmpty -> this
      else ->
        BoundingBox(
          minOf(minLat, other.minLat),
          minOf(minLng, other.minLng),
          maxOf(maxLat, other.maxLat),
          maxOf(maxLng, other.maxLng),
        )
    }

  companion object {
    val EMPTY = BoundingBox(1.0, 1.0, -1.0, -1.0)

    fun of(lats: DoubleArray, lngs: DoubleArray, count: Int = lats.size): BoundingBox {
      if (count == 0) return EMPTY
      var minLat = lats[0]
      var maxLat = lats[0]
      var minLng = lngs[0]
      var maxLng = lngs[0]
      for (i in 1 until count) {
        if (lats[i] < minLat) minLat = lats[i]
        if (lats[i] > maxLat) maxLat = lats[i]
        if (lngs[i] < minLng) minLng = lngs[i]
        if (lngs[i] > maxLng) maxLng = lngs[i]
      }
      return BoundingBox(minLat, minLng, maxLat, maxLng)
    }
  }
}

/**
 * Converts metric offsets around a center point into degrees using the local radii of curvature.
 * Used to build plot boundaries and sub-plot patterns, which are small enough (≤ a few km) for the
 * tangent-plane approximation to be exact at centimeter level.
 */
class LocalMetricFrame(val centerLat: Double, val centerLng: Double) {
  private val degPerMeterLat: Double = 1 / (Wgs84.meridionalRadius(centerLat) * PI / 180)
  private val degPerMeterLng: Double =
    1 / (Wgs84.primeVerticalRadius(centerLat) * cos(centerLat * PI / 180) * PI / 180)

  fun lat(northM: Double): Double = centerLat + northM * degPerMeterLat

  fun lng(eastM: Double): Double = centerLng + eastM * degPerMeterLng
}

/**
 * Oblique Lambert azimuthal equal-area projection of the WGS 84 ellipsoid (Snyder eqs. 24-13 to
 * 24-20 forward, 24-25 to 24-29 inverse, with 3-11, 3-12 and 3-18 for the authalic latitude),
 * centered on ([centerLat], [centerLng]).
 *
 * The projection is equal-area with respect to the ellipsoid, so a uniform density in projected
 * meters is a uniform density on the ground; this is what grid and random sampling designs need.
 * Snyder's `D` factor makes the scale true in every direction at the center; distances away from
 * the center are only approximately preserved (≈ 0.1% at 200 km, ≈ 1% at 600 km).
 */
class LambertAzimuthalEqualArea(val centerLat: Double, val centerLng: Double) {
  private val r = Wgs84.AUTHALIC_RADIUS
  private val lng0 = centerLng * DEG
  private val beta0 = Wgs84.authalicLatitude(centerLat * DEG)
  private val sinB0 = sin(beta0)
  private val cosB0 = cos(beta0)
  private val d: Double =
    if (cosB0 < 1e-12) 1.0
    else {
      val sinPhi0 = sin(centerLat * DEG)
      val m0 = cos(centerLat * DEG) / sqrt(1 - Wgs84.E2 * sinPhi0 * sinPhi0)
      Wgs84.A * m0 / (r * cosB0)
    }

  /** Projects ([lat], [lng]) in degrees to (x, y) meters, written into [out] (size ≥ 2). */
  fun forward(lat: Double, lng: Double, out: DoubleArray) {
    val beta = Wgs84.authalicLatitude(lat * DEG)
    val sinB = sin(beta)
    val cosB = cos(beta)
    val dl = lng * DEG - lng0
    val cosDl = cos(dl)
    val denom = 1 + sinB0 * sinB + cosB0 * cosB * cosDl
    val b = if (denom <= 1e-15) 0.0 else r * sqrt(2 / denom)
    out[0] = b * d * cosB * sin(dl)
    out[1] = (b / d) * (cosB0 * sinB - sinB0 * cosB * cosDl)
  }

  /** Inverse-projects (x, y) meters to (lat, lng) degrees, written into [out] (size ≥ 2). */
  fun inverse(x: Double, y: Double, out: DoubleArray) {
    // Undo the D scaling, then invert the spherical projection on the authalic sphere.
    val xs = x / d
    val ys = y * d
    val rho = sqrt(xs * xs + ys * ys)
    if (rho < 1e-9) {
      out[0] = centerLat
      out[1] = centerLng
      return
    }
    val c = 2 * asin((rho / (2 * r)).coerceAtMost(1.0))
    val sinC = sin(c)
    val cosC = cos(c)
    val beta = asin((cosC * sinB0 + ys * sinC * cosB0 / rho).coerceIn(-1.0, 1.0))
    val lng = lng0 + atan2(xs * sinC, rho * cosB0 * cosC - ys * sinB0 * sinC)
    out[0] = Wgs84.geodeticLatitude(beta) / DEG
    var lngDeg = lng / DEG
    if (lngDeg > 180) lngDeg -= 360 else if (lngDeg < -180) lngDeg += 360
    out[1] = lngDeg
  }

  private companion object {
    const val DEG = PI / 180
  }
}
