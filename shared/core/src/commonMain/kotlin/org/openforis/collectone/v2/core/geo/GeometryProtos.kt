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
package org.openforis.collectone.v2.core.geo

import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.Geometry
import openforis.collectone.v1.data.GeometryType
import openforis.collectone.v1.data.MultiLineStringCoordinates
import openforis.collectone.v1.data.MultiPolygonCoordinates
import openforis.collectone.v1.data.PolygonCoordinates
import openforis.collectone.v1.data.Position
import openforis.collectone.v1.data.PositionList
import openforis.collectone.v1.forms.GeoPoint
import openforis.collectone.v1.forms.GeoShape
import openforis.collectone.v1.forms.GeoTrace

/**
 * Builds, reads, and converts the canonical vector geometry, `data.Geometry`: a GeoJSON geometry in
 * WGS 84 longitude and latitude (`docs/technical/model/data/05-geometry.md`).
 */
object GeometryProtos {
  /** A Point at [latitude], [longitude]. */
  fun point(latitude: Double, longitude: Double): Geometry =
    Geometry(type = GeometryType.GEOMETRY_TYPE_POINT, point = position(latitude, longitude))

  /** A LineString through [vertices]. */
  fun lineString(vertices: List<Position>): Geometry =
    Geometry(
      type = GeometryType.GEOMETRY_TYPE_LINE_STRING,
      line_string = PositionList(positions = vertices),
    )

  /**
   * A Polygon with the exterior ring first, then holes. Rings may be open or closed; they are
   * stored closed (RFC 7946 section 3.1.6).
   */
  fun polygon(rings: List<List<Position>>): Geometry =
    Geometry(
      type = GeometryType.GEOMETRY_TYPE_POLYGON,
      polygon = PolygonCoordinates(rings = rings.map { PositionList(positions = closed(it)) }),
    )

  /** A MultiPoint of [positions]. */
  fun multiPoint(positions: List<Position>): Geometry =
    Geometry(
      type = GeometryType.GEOMETRY_TYPE_MULTI_POINT,
      multi_point = PositionList(positions = positions),
    )

  /** A MultiLineString of [lines], each a list of vertices. */
  fun multiLineString(lines: List<List<Position>>): Geometry =
    Geometry(
      type = GeometryType.GEOMETRY_TYPE_MULTI_LINE_STRING,
      multi_line_string =
        MultiLineStringCoordinates(lines = lines.map { PositionList(positions = it) }),
    )

  /** A MultiPolygon of [polygons], each a list of rings stored closed like [polygon]'s. */
  fun multiPolygon(polygons: List<List<List<Position>>>): Geometry =
    Geometry(
      type = GeometryType.GEOMETRY_TYPE_MULTI_POLYGON,
      multi_polygon =
        MultiPolygonCoordinates(
          polygons =
            polygons.map { rings ->
              PolygonCoordinates(rings = rings.map { PositionList(positions = closed(it)) })
            }
        ),
    )

  fun position(latitude: Double, longitude: Double): Position =
    Position(longitude = longitude, latitude = latitude)

  /**
   * The type of [geometry]: the `coordinates` case that is set, or `null` when none is set or
   * `type` names another one.
   */
  fun typeOf(geometry: Geometry): GeometryType? {
    val byCoordinates =
      when {
        geometry.point != null -> GeometryType.GEOMETRY_TYPE_POINT
        geometry.line_string != null -> GeometryType.GEOMETRY_TYPE_LINE_STRING
        geometry.polygon != null -> GeometryType.GEOMETRY_TYPE_POLYGON
        geometry.multi_point != null -> GeometryType.GEOMETRY_TYPE_MULTI_POINT
        geometry.multi_line_string != null -> GeometryType.GEOMETRY_TYPE_MULTI_LINE_STRING
        geometry.multi_polygon != null -> GeometryType.GEOMETRY_TYPE_MULTI_POLYGON
        else -> return null
      }
    return byCoordinates.takeIf {
      geometry.type == it || geometry.type == GeometryType.GEOMETRY_TYPE_UNSPECIFIED
    }
  }

  /**
   * The geometry of [record]: its canonical `geometry`, else the `point`, `trace`, or `shape` that
   * rows written before it carry, or `null` for a record without geometry.
   */
  fun of(record: EntityRecord): Geometry? =
    record.geometry?.takeIf { typeOf(it) != null }
      ?: fromLegacy(record.point, record.trace, record.shape)

  /** Whether [record] sets a geometry, in the canonical or a legacy form. */
  fun hasGeometry(record: EntityRecord): Boolean =
    listOfNotNull(record.geometry, record.point, record.trace, record.shape).isNotEmpty()

  /**
   * The canonical form of a legacy `forms.GeoPoint`, `GeoTrace`, or `GeoShape`, keeping the stored
   * vertices as they are (a `GeoShape` ring is already closed).
   */
  fun fromLegacy(point: GeoPoint?, trace: GeoTrace?, shape: GeoShape?): Geometry? =
    when {
      point != null -> Geometry(type = GeometryType.GEOMETRY_TYPE_POINT, point = point.toPosition())
      trace != null -> lineString(trace.points.map { it.toPosition() })
      shape != null ->
        Geometry(
          type = GeometryType.GEOMETRY_TYPE_POLYGON,
          polygon =
            PolygonCoordinates(
              rings = listOf(PositionList(positions = shape.points.map { it.toPosition() }))
            ),
        )
      else -> null
    }

  /**
   * The point that stands for [geometry] in labels and spatial indexes: a Point itself, the first
   * vertex of a line, or the average of a polygon's exterior ring without its closing vertex. Multi
   * geometries use their first part. `null` when [geometry] has no positions.
   */
  fun representativePosition(geometry: Geometry): Position? =
    when (typeOf(geometry)) {
      GeometryType.GEOMETRY_TYPE_POINT -> geometry.point
      GeometryType.GEOMETRY_TYPE_LINE_STRING -> geometry.line_string?.positions?.firstOrNull()
      GeometryType.GEOMETRY_TYPE_POLYGON -> ringAverage(geometry.polygon?.rings?.firstOrNull())
      GeometryType.GEOMETRY_TYPE_MULTI_POINT -> geometry.multi_point?.positions?.firstOrNull()
      GeometryType.GEOMETRY_TYPE_MULTI_LINE_STRING ->
        geometry.multi_line_string?.lines?.firstOrNull()?.positions?.firstOrNull()
      GeometryType.GEOMETRY_TYPE_MULTI_POLYGON ->
        ringAverage(geometry.multi_polygon?.polygons?.firstOrNull()?.rings?.firstOrNull())
      GeometryType.GEOMETRY_TYPE_UNSPECIFIED,
      null -> null
    }

  /**
   * Positions in the polygon rings of [geometry], closing vertices included; 0 for non-polygons.
   */
  fun polygonVertexCount(geometry: Geometry): Int =
    when (typeOf(geometry)) {
      GeometryType.GEOMETRY_TYPE_POLYGON -> geometry.polygon.vertexCount()
      GeometryType.GEOMETRY_TYPE_MULTI_POLYGON ->
        geometry.multi_polygon?.polygons.orEmpty().sumOf { it.vertexCount() }
      else -> 0
    }

  /**
   * Distinct vertices of [geometry]: every position, without the closing vertex of each polygon
   * ring. 0 for an empty or invalid geometry.
   */
  fun vertexCount(geometry: Geometry): Int =
    when (typeOf(geometry)) {
      GeometryType.GEOMETRY_TYPE_POINT -> 1
      GeometryType.GEOMETRY_TYPE_LINE_STRING -> geometry.line_string?.positions.orEmpty().size
      GeometryType.GEOMETRY_TYPE_POLYGON -> geometry.polygon.openVertexCount()
      GeometryType.GEOMETRY_TYPE_MULTI_POINT -> geometry.multi_point?.positions.orEmpty().size
      GeometryType.GEOMETRY_TYPE_MULTI_LINE_STRING ->
        geometry.multi_line_string?.lines.orEmpty().sumOf { it.positions.size }
      GeometryType.GEOMETRY_TYPE_MULTI_POLYGON ->
        geometry.multi_polygon?.polygons.orEmpty().sumOf { it.openVertexCount() }
      GeometryType.GEOMETRY_TYPE_UNSPECIFIED,
      null -> 0
    }

  /** [position] as a `forms.GeoPoint`, for code that still works on XForms geo values. */
  fun toGeoPoint(position: Position): GeoPoint =
    GeoPoint(
      latitude = position.latitude,
      longitude = position.longitude,
      altitude_meters = position.altitude_meters,
    )

  private fun PolygonCoordinates?.openVertexCount(): Int =
    this?.rings.orEmpty().sumOf { open(it.positions).size }

  private fun PolygonCoordinates?.vertexCount(): Int =
    this?.rings.orEmpty().sumOf { it.positions.size }

  private fun GeoPoint.toPosition(): Position =
    Position(longitude = longitude, latitude = latitude, altitude_meters = altitude_meters)

  private fun closed(ring: List<Position>): List<Position> =
    if (ring.size < 2 || ring.first().sameCoordinates(ring.last())) ring else ring + ring.first()

  private fun Position.sameCoordinates(other: Position): Boolean =
    latitude == other.latitude && longitude == other.longitude

  /** [ring] without its closing vertex. */
  fun open(ring: List<Position>): List<Position> =
    if (ring.size > 1 && ring.first().sameCoordinates(ring.last())) ring.dropLast(1) else ring

  private fun ringAverage(ring: PositionList?): Position? {
    val open = open(ring?.positions.orEmpty())
    if (open.isEmpty()) return null
    return position(open.sumOf { it.latitude } / open.size, open.sumOf { it.longitude } / open.size)
  }
}
