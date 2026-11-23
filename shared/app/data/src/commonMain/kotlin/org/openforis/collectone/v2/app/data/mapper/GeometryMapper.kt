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
package org.openforis.collectone.v2.app.data.mapper

import openforis.collectone.v1.data.BoundingBox
import openforis.collectone.v1.data.Geometry as GeometryProto
import openforis.collectone.v1.data.GeometryType as GeometryTypeProto
import openforis.collectone.v1.data.Position
import openforis.collectone.v1.data.PositionList
import org.openforis.collectone.v2.core.geo.GeometryProtos
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.map.LngLatBounds

/**
 * Maps the canonical proto geometry (`data.Geometry`, GeoJSON in WGS 84) to the app's [Geometry]
 * and back.
 */
internal object GeometryMapper {
  fun toProto(geometry: Geometry): GeometryProto =
    when (geometry) {
      is Geometry.Point ->
        GeometryProtos.point(geometry.position.latitude, geometry.position.longitude)
      is Geometry.LineString -> GeometryProtos.lineString(geometry.points.map(::position))
      is Geometry.Polygon -> GeometryProtos.polygon(geometry.rings.map { it.map(::position) })
      is Geometry.MultiPoint ->
        GeometryProtos.multiPoint(geometry.parts.map { position(it.position) })
      is Geometry.MultiLineString ->
        GeometryProtos.multiLineString(geometry.parts.map { it.points.map(::position) })
      is Geometry.MultiPolygon ->
        GeometryProtos.multiPolygon(
          geometry.parts.map { it.rings.map { ring -> ring.map(::position) } }
        )
    }

  /**
   * [proto] as the app's [Geometry], with every part of a multi-part geometry, or `null` when it is
   * empty or invalid.
   */
  fun fromProto(proto: GeometryProto): Geometry? =
    when (GeometryProtos.typeOf(proto)) {
      GeometryTypeProto.GEOMETRY_TYPE_POINT -> proto.point?.let(::point)
      GeometryTypeProto.GEOMETRY_TYPE_LINE_STRING -> lineString(proto.line_string)
      GeometryTypeProto.GEOMETRY_TYPE_POLYGON -> polygon(proto.polygon?.rings.orEmpty())
      GeometryTypeProto.GEOMETRY_TYPE_MULTI_POINT ->
        multi(proto.multi_point?.positions, ::point, Geometry::MultiPoint)
      GeometryTypeProto.GEOMETRY_TYPE_MULTI_LINE_STRING ->
        multi(proto.multi_line_string?.lines, ::lineString, Geometry::MultiLineString)
      GeometryTypeProto.GEOMETRY_TYPE_MULTI_POLYGON ->
        multi(proto.multi_polygon?.polygons, { polygon(it.rings) }, Geometry::MultiPolygon)
      GeometryTypeProto.GEOMETRY_TYPE_UNSPECIFIED,
      null -> null
    }

  /** A multi-part geometry of [parts], or `null` when there are none or one is invalid. */
  private fun <P, T : Geometry.Single> multi(
    parts: List<P>?,
    part: (P) -> T?,
    build: (List<T>) -> Geometry,
  ): Geometry? {
    val decoded = parts.orEmpty().map { part(it) ?: return null }
    return decoded.takeIf { it.isNotEmpty() }?.let(build)
  }

  /** The proto name of an app geometry [type]. */
  fun typeToProto(type: GeometryType?): GeometryTypeProto =
    when (type) {
      GeometryType.POINT -> GeometryTypeProto.GEOMETRY_TYPE_POINT
      GeometryType.LINE_STRING -> GeometryTypeProto.GEOMETRY_TYPE_LINE_STRING
      GeometryType.POLYGON -> GeometryTypeProto.GEOMETRY_TYPE_POLYGON
      null -> GeometryTypeProto.GEOMETRY_TYPE_UNSPECIFIED
    }

  /** The app geometry type of a proto [type]; multi-part types map to their part's type. */
  fun typeFromProto(type: GeometryTypeProto): GeometryType? =
    when (type) {
      GeometryTypeProto.GEOMETRY_TYPE_POINT,
      GeometryTypeProto.GEOMETRY_TYPE_MULTI_POINT -> GeometryType.POINT
      GeometryTypeProto.GEOMETRY_TYPE_LINE_STRING,
      GeometryTypeProto.GEOMETRY_TYPE_MULTI_LINE_STRING -> GeometryType.LINE_STRING
      GeometryTypeProto.GEOMETRY_TYPE_POLYGON,
      GeometryTypeProto.GEOMETRY_TYPE_MULTI_POLYGON -> GeometryType.POLYGON
      GeometryTypeProto.GEOMETRY_TYPE_UNSPECIFIED -> null
    }

  /** [bounds] as the canonical bounding box: its southwest and northeast corners. */
  fun boundsToProto(bounds: LngLatBounds): BoundingBox =
    BoundingBox(
      southwest = GeometryProtos.position(bounds.south, bounds.west),
      northeast = GeometryProtos.position(bounds.north, bounds.east),
    )

  /** The bounds of [proto], or `null` unless both corners are set. */
  fun boundsFromProto(proto: BoundingBox): LngLatBounds? {
    val southwest = proto.southwest ?: return null
    val northeast = proto.northeast ?: return null
    return normalizedBounds(
      southwest.longitude,
      southwest.latitude,
      northeast.longitude,
      northeast.latitude,
    )
  }

  /**
   * Bounds with the edges [west], [south], [east], and [north], swapped where needed so west ≤ east
   * and south ≤ north: stored data predates validation.
   */
  fun normalizedBounds(west: Double, south: Double, east: Double, north: Double): LngLatBounds =
    LngLatBounds(minOf(west, east), minOf(south, north), maxOf(west, east), maxOf(south, north))

  private fun point(position: Position): Geometry.Point? = latLng(position)?.let(Geometry::Point)

  private fun lineString(line: PositionList?): Geometry.LineString? {
    val points = line?.positions.orEmpty().map { latLng(it) ?: return null }
    return points.takeIf { it.size >= 2 }?.let(Geometry::LineString)
  }

  /** Rings are stored closed; the app lists them open. */
  private fun polygon(rings: List<PositionList>): Geometry.Polygon? {
    val open = rings.map { ring ->
      val points = ring.positions.map { latLng(it) ?: return null }
      if (points.size > 1 && points.first() == points.last()) points.dropLast(1) else points
    }
    return open
      .takeIf { it.isNotEmpty() && it.all { ring -> ring.size >= 3 } }
      ?.let(Geometry::Polygon)
  }

  private fun position(point: LatLng): Position =
    GeometryProtos.position(point.latitude, point.longitude)

  private fun latLng(position: Position): LatLng? =
    position
      .takeIf { it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }
      ?.let {
        LatLng(latitude = it.latitude, longitude = it.longitude)
      }
}
