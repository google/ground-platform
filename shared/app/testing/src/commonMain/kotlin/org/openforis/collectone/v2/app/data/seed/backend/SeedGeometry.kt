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
package org.openforis.collectone.v2.app.data.seed.backend

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sqrt
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.LatLng

/**
 * Geometries of the sample map features, placed at the coordinates each feature was authored with:
 * points at that position, parcels as squares of their recorded area centered on it, and transects
 * as straight lines of their recorded length starting from it. So the coordinates the app derives
 * from a feature's geometry match the sample data.
 */
internal object SeedGeometry {
  private const val METERS_PER_DEGREE_LATITUDE = 111_320.0
  private const val SQUARE_METERS_PER_HECTARE = 10_000.0
  private const val MIN_AREA_HECTARES = 0.01

  fun point(latitude: Double, longitude: Double): Geometry =
    Geometry.Point(LatLng(latitude, longitude))

  /** A square of [areaHectares] whose vertex average is [latitude], [longitude]. */
  fun square(latitude: Double, longitude: Double, areaHectares: Double): Geometry {
    val halfSide = sqrt(max(areaHectares, MIN_AREA_HECTARES) * SQUARE_METERS_PER_HECTARE) / 2
    val dLat = halfSide / METERS_PER_DEGREE_LATITUDE
    val dLng = halfSide / metersPerDegreeLongitude(latitude)
    return Geometry.Polygon(
      listOf(
        listOf(
          LatLng(latitude - dLat, longitude - dLng),
          LatLng(latitude - dLat, longitude + dLng),
          LatLng(latitude + dLat, longitude + dLng),
          LatLng(latitude + dLat, longitude - dLng),
        )
      )
    )
  }

  /** A transect of [lengthMeters] heading east from its first vertex at [latitude], [longitude]. */
  fun transect(latitude: Double, longitude: Double, lengthMeters: Int): Geometry =
    Geometry.LineString(
      listOf(
        LatLng(latitude, longitude),
        LatLng(latitude, longitude + lengthMeters / metersPerDegreeLongitude(latitude)),
      )
    )

  private fun metersPerDegreeLongitude(latitude: Double): Double =
    METERS_PER_DEGREE_LATITUDE * cos(latitude * PI / 180)
}

/**
 * Moves sample data authored around [from] to the same place relative to [to], keeping ground
 * distances: north-south offsets stay in degrees, east-west offsets scale with the latitude.
 */
internal class SeedRelocation(private val from: LatLng, private val to: LatLng) {
  private val longitudeScale = cos(from.latitude * PI / 180) / cos(to.latitude * PI / 180)

  fun position(position: LatLng): LatLng =
    LatLng(
      to.latitude + (position.latitude - from.latitude),
      to.longitude + (position.longitude - from.longitude) * longitudeScale,
    )

  fun geometry(geometry: Geometry): Geometry =
    when (geometry) {
      is Geometry.Point -> point(geometry)
      is Geometry.LineString -> lineString(geometry)
      is Geometry.Polygon -> polygon(geometry)
      is Geometry.MultiPoint -> Geometry.MultiPoint(geometry.parts.map(::point))
      is Geometry.MultiLineString -> Geometry.MultiLineString(geometry.parts.map(::lineString))
      is Geometry.MultiPolygon -> Geometry.MultiPolygon(geometry.parts.map(::polygon))
    }

  private fun point(point: Geometry.Point) = Geometry.Point(position(point.position))

  private fun lineString(line: Geometry.LineString) =
    Geometry.LineString(line.points.map(::position))

  private fun polygon(polygon: Geometry.Polygon) =
    Geometry.Polygon(polygon.rings.map { ring -> ring.map(::position) })
}
