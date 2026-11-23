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
package org.openforis.collectone.v2.map

import androidx.compose.runtime.Immutable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** A WGS 84 coordinate in degrees. */
@Immutable
data class LatLng(val latitude: Double, val longitude: Double) {
  init {
    require(latitude in -90.0..90.0) { "latitude out of range: $latitude" }
    require(longitude in -180.0..180.0) { "longitude out of range: $longitude" }
  }

  companion object {
    /**
     * The position at [latitude] and [longitude] clamped into range, reading NaN as 0: for values
     * that may lie outside it, like stored data that predates validation or a position offset from
     * another one.
     */
    fun coerced(latitude: Double, longitude: Double): LatLng =
      LatLng(
        latitude.takeUnless { it.isNaN() }?.coerceIn(-90.0, 90.0) ?: 0.0,
        longitude.takeUnless { it.isNaN() }?.coerceIn(-180.0, 180.0) ?: 0.0,
      )
  }
}

/**
 * Geographic bounds in degrees. A single point has zero-size bounds.
 *
 * The bounds don't wrap around the antimeridian: they span longitudes from [west] up to [east], so
 * [center], [contains], [areaSquareKilometers], and [of] all assume `west <= east`. Bounds with
 * `west > east` report [crossesAntimeridian], and callers that can't handle them should reject
 * them.
 */
@Immutable
data class LngLatBounds(val west: Double, val south: Double, val east: Double, val north: Double) {
  val center: LatLng
    get() = LatLng((south + north) / 2, (west + east) / 2)

  /**
   * Whether these bounds were meant to cross the ±180° meridian, i.e. [west] lies east of [east].
   * Nothing in this class handles such bounds.
   */
  val crossesAntimeridian: Boolean
    get() = west > east

  operator fun contains(point: LatLng): Boolean =
    point.latitude in south..north && point.longitude in west..east

  /**
   * The area these bounds cover on the Earth's surface, in km².
   *
   * Uses a sphere with the WGS 84 mean radius R = [EARTH_RADIUS_KM]: the area between two meridians
   * and two parallels is R² × Δλ × |sin φN − sin φS|, with Δλ in radians. That is within 1% of the
   * ellipsoidal area, enough for size limits and display. Assumes `west <= east` (see
   * [crossesAntimeridian]).
   */
  fun areaSquareKilometers(): Double {
    val deltaLongitude = (east - west) * PI / 180
    val sinNorth = sin(north * PI / 180)
    val sinSouth = sin(south * PI / 180)
    return EARTH_RADIUS_KM * EARTH_RADIUS_KM * deltaLongitude * abs(sinNorth - sinSouth)
  }

  companion object {
    /** The mean radius of the WGS 84 ellipsoid (IUGG R1), in km. */
    const val EARTH_RADIUS_KM = 6371.0088

    /** Smallest bounds containing all [points]. Does not handle antimeridian crossing. */
    fun of(points: Iterable<LatLng>): LngLatBounds {
      val it = points.iterator()
      require(it.hasNext()) { "points must not be empty" }
      val first = it.next()
      var west = first.longitude
      var east = first.longitude
      var south = first.latitude
      var north = first.latitude
      for (p in it) {
        west = minOf(west, p.longitude)
        east = maxOf(east, p.longitude)
        south = minOf(south, p.latitude)
        north = maxOf(north, p.latitude)
      }
      return LngLatBounds(west, south, east, north)
    }
  }
}

/**
 * Kinds of GeoJSON geometry the map draws (RFC 7946 section 3.1). A multi-part geometry has the
 * kind of its parts: a MultiPolygon is a [POLYGON].
 */
enum class GeometryType(
  /**
   * The fewest vertices a geometry of this type has: one for a point, two for a line, and three for
   * a polygon ring (listed without repeating the first vertex).
   */
  val minVertices: Int
) {
  POINT(1),
  LINE_STRING(2),
  POLYGON(3),
}

/** A subset of GeoJSON geometries (RFC 7946 section 3.1). */
@Immutable
sealed interface Geometry {
  val type: GeometryType

  /** All vertices, used for bounds and hit-testing; for polygons, their exterior rings. */
  val coordinates: List<LatLng>

  /**
   * The point whose coordinates stand for this geometry
   * (`docs/technical/model/data/05-geometry.md`): a Point itself, the first vertex of a line, the
   * average of a polygon's exterior ring, or that of the first part of a multi-part geometry.
   */
  val representativePoint: LatLng

  /** The single geometries this is made of: itself, or each part of a multi-part geometry. */
  val parts: List<Geometry>

  fun bounds(): LngLatBounds = LngLatBounds.of(coordinates)

  /** A geometry of one part: a [Point], [LineString], or [Polygon]. */
  sealed interface Single : Geometry {
    override val parts: List<Geometry>
      get() = listOf(this)
  }

  /** A geometry of one or more [parts] of the same kind (RFC 7946 sections 3.1.3, 3.1.5, 3.1.7). */
  sealed interface Multi<T : Single> : Geometry {
    override val parts: List<T>

    override val coordinates: List<LatLng>
      get() = parts.flatMap { it.coordinates }

    override val representativePoint: LatLng
      get() = parts.first().representativePoint
  }

  data class Point(val position: LatLng) : Single {
    override val type = GeometryType.POINT
    override val coordinates: List<LatLng>
      get() = listOf(position)

    override val representativePoint: LatLng
      get() = position
  }

  data class LineString(val points: List<LatLng>) : Single {
    init {
      require(points.size >= 2) { "a LineString needs at least 2 points" }
    }

    override val type = GeometryType.LINE_STRING
    override val coordinates: List<LatLng>
      get() = points

    override val representativePoint: LatLng
      get() = points.first()
  }

  /**
   * A polygon with an exterior ring followed by optional holes. Rings are listed without repeating
   * the first vertex; renderers close them.
   */
  data class Polygon(val rings: List<List<LatLng>>) : Single {
    init {
      require(rings.isNotEmpty() && rings.all { it.size >= 3 }) {
        "a Polygon needs an exterior ring and every ring needs at least 3 vertices"
      }
    }

    override val type = GeometryType.POLYGON
    override val coordinates: List<LatLng>
      get() = rings.first()

    override val representativePoint: LatLng
      get() {
        val ring = rings.first()
        return LatLng(
          ring.sumOf { it.latitude } / ring.size,
          ring.sumOf { it.longitude } / ring.size,
        )
      }
  }

  data class MultiPoint(override val parts: List<Point>) : Multi<Point> {
    init {
      require(parts.isNotEmpty()) { "a MultiPoint needs at least one point" }
    }

    override val type = GeometryType.POINT
  }

  data class MultiLineString(override val parts: List<LineString>) : Multi<LineString> {
    init {
      require(parts.isNotEmpty()) { "a MultiLineString needs at least one line" }
    }

    override val type = GeometryType.LINE_STRING
  }

  data class MultiPolygon(override val parts: List<Polygon>) : Multi<Polygon> {
    init {
      require(parts.isNotEmpty()) { "a MultiPolygon needs at least one polygon" }
    }

    override val type = GeometryType.POLYGON
  }
}
