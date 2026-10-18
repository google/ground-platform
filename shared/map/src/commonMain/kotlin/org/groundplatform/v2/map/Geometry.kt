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
package org.groundplatform.v2.map

import androidx.compose.runtime.Immutable

/** A WGS 84 coordinate in degrees. */
@Immutable
data class LatLng(val latitude: Double, val longitude: Double) {
  init {
    require(latitude in -90.0..90.0) { "latitude out of range: $latitude" }
    require(longitude in -180.0..180.0) { "longitude out of range: $longitude" }
  }
}

/** Geographic bounds in degrees. A single point has zero-size bounds. */
@Immutable
data class LngLatBounds(val west: Double, val south: Double, val east: Double, val north: Double) {
  val center: LatLng
    get() = LatLng((south + north) / 2, (west + east) / 2)

  operator fun contains(point: LatLng): Boolean =
    point.latitude in south..north && point.longitude in west..east

  companion object {
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

/** GeoJSON geometry types supported by the map (RFC 7946 section 3.1). */
enum class GeometryType {
  POINT,
  LINE_STRING,
  POLYGON,
}

/** A subset of GeoJSON geometries (RFC 7946 section 3.1). */
@Immutable
sealed interface Geometry {
  val type: GeometryType

  /** All vertices, used for bounds and hit-testing. */
  val coordinates: List<LatLng>

  fun bounds(): LngLatBounds = LngLatBounds.of(coordinates)

  data class Point(val position: LatLng) : Geometry {
    override val type = GeometryType.POINT
    override val coordinates: List<LatLng>
      get() = listOf(position)
  }

  data class LineString(val points: List<LatLng>) : Geometry {
    init {
      require(points.size >= 2) { "a LineString needs at least 2 points" }
    }

    override val type = GeometryType.LINE_STRING
    override val coordinates: List<LatLng>
      get() = points
  }

  /**
   * A polygon with an exterior ring followed by optional holes. Rings are listed without repeating
   * the first vertex; renderers close them.
   */
  data class Polygon(val rings: List<List<LatLng>>) : Geometry {
    init {
      require(rings.isNotEmpty() && rings.all { it.size >= 3 }) {
        "a Polygon needs an exterior ring and every ring needs at least 3 vertices"
      }
    }

    override val type = GeometryType.POLYGON
    override val coordinates: List<LatLng>
      get() = rings.first()
  }
}
