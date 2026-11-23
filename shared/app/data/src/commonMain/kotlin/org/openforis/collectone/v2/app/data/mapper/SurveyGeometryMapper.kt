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

import openforis.collectone.v1.data.Geometry
import openforis.collectone.v1.data.GeometryType as GeometryTypeProto
import openforis.collectone.v1.data.Position
import openforis.collectone.v1.forms.GeoPoint
import openforis.collectone.v1.forms.GeoShape
import openforis.collectone.v1.survey.DataTableRow
import openforis.collectone.v1.survey.SurveyArea as SurveyAreaProto
import org.openforis.collectone.v2.core.geo.GeometryProtos
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng

/**
 * Maps the Survey editor's vertex lists (Map layer rows and survey area parts) to the canonical
 * geometry of `DataTableRow.geometry` and `SurveyArea.boundary`, and back. Rows and areas stored
 * before those fields (`repeated forms.GeoPoint` field 3 of `DataTableRow`, `repeated
 * forms.GeoShape` field 2 and the `forms.GeoPoint` center 4 of `SurveyArea`, all reserved now)
 * still decode (`docs/technical/model/data/05-geometry.md`, "Migration").
 */
internal object SurveyGeometryMapper {
  /** Reserved field number of the legacy vertices of `DataTableRow`. */
  const val ROW_LEGACY_VERTICES = 3

  /** Reserved field number of `SurveyArea.parts`. */
  const val AREA_LEGACY_PARTS = 2

  /** Reserved field number of the legacy `forms.GeoPoint` center of `SurveyArea`. */
  const val AREA_LEGACY_CENTER = 4

  /**
   * The geometry of a Map layer row of [type] with [vertices], or `null` for a row without any. A
   * Point row with more than one vertex is a MultiPoint, so no vertex is lost.
   */
  fun rowGeometryToProto(type: GeometryType, vertices: List<LatLng>): Geometry? {
    if (vertices.isEmpty()) return null
    val positions = vertices.map(::position)
    return when (type) {
      GeometryType.POINT ->
        if (positions.size == 1) GeometryProtos.point(vertices[0].latitude, vertices[0].longitude)
        else GeometryProtos.multiPoint(positions)
      GeometryType.LINE_STRING -> GeometryProtos.lineString(positions)
      GeometryType.POLYGON -> GeometryProtos.polygon(listOf(positions))
    }
  }

  /** The vertices of [row]: those of its geometry, else the ones a legacy row stored. */
  fun rowVertices(row: DataTableRow): List<LatLng> =
    row.geometry?.let(::vertices)
      ?: ReservedFields.values(row.unknownFields, ROW_LEGACY_VERTICES, GeoPoint.ADAPTER).map {
        LatLng.coerced(it.latitude, it.longitude)
      }

  /** The boundary of an area made of [parts], each an open ring; `null` when there are none. */
  fun areaBoundaryToProto(parts: List<List<LatLng>>): Geometry? =
    parts
      .takeIf { it.isNotEmpty() }
      ?.let { GeometryProtos.multiPolygon(it.map { ring -> listOf(ring.map(::position)) }) }

  /**
   * The parts of [area], each an open ring: the exterior rings of its boundary, else the rings a
   * legacy area stored.
   */
  fun areaParts(area: SurveyAreaProto): List<List<LatLng>> {
    val boundary = area.boundary
    if (
      boundary != null &&
        GeometryProtos.typeOf(boundary) == GeometryTypeProto.GEOMETRY_TYPE_MULTI_POLYGON
    ) {
      return boundary.multi_polygon?.polygons.orEmpty().map { polygon ->
        GeometryProtos.open(polygon.rings.firstOrNull()?.positions.orEmpty()).map(::latLng)
      }
    }
    return ReservedFields.values(area.unknownFields, AREA_LEGACY_PARTS, GeoShape.ADAPTER).map {
      shape ->
      shape.points.map { LatLng.coerced(it.latitude, it.longitude) }
    }
  }

  /** The center of an area as stored in `SurveyArea.center`. */
  fun areaCenterToProto(center: LatLng): Position = position(center)

  /**
   * The center of [area]: its `center`, else the `forms.GeoPoint` a legacy area stored, else
   * `null`.
   */
  fun areaCenter(area: SurveyAreaProto): LatLng? =
    area.center?.let(::latLng)
      ?: ReservedFields.values(area.unknownFields, AREA_LEGACY_CENTER, GeoPoint.ADAPTER)
        .lastOrNull()
        ?.let { LatLng.coerced(it.latitude, it.longitude) }

  /** Every vertex of [geometry], without the closing vertex of polygon rings. */
  private fun vertices(geometry: Geometry): List<LatLng> =
    when (GeometryProtos.typeOf(geometry)) {
      GeometryTypeProto.GEOMETRY_TYPE_POINT -> listOfNotNull(geometry.point)
      GeometryTypeProto.GEOMETRY_TYPE_LINE_STRING -> geometry.line_string?.positions.orEmpty()
      GeometryTypeProto.GEOMETRY_TYPE_POLYGON ->
        GeometryProtos.open(geometry.polygon?.rings?.firstOrNull()?.positions.orEmpty())
      GeometryTypeProto.GEOMETRY_TYPE_MULTI_POINT -> geometry.multi_point?.positions.orEmpty()
      GeometryTypeProto.GEOMETRY_TYPE_MULTI_LINE_STRING ->
        geometry.multi_line_string?.lines.orEmpty().flatMap { it.positions }
      GeometryTypeProto.GEOMETRY_TYPE_MULTI_POLYGON ->
        geometry.multi_polygon?.polygons.orEmpty().flatMap {
          GeometryProtos.open(it.rings.firstOrNull()?.positions.orEmpty())
        }
      GeometryTypeProto.GEOMETRY_TYPE_UNSPECIFIED,
      null -> emptyList()
    }.map(::latLng)

  private fun position(point: LatLng): Position =
    GeometryProtos.position(point.latitude, point.longitude)

  private fun latLng(position: Position): LatLng =
    LatLng.coerced(position.latitude, position.longitude)
}
