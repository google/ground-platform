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
package org.openforis.collectone.v2.core.geo.io

import openforis.collectone.v1.data.Geometry
import openforis.collectone.v1.data.GeometryType
import openforis.collectone.v1.data.Position
import org.openforis.collectone.v2.core.geo.GeometryProtos
import org.openforis.collectone.v2.core.geo.io.KmlBalloonWriter.escapeXml
import org.openforis.collectone.v2.core.geo.io.KmlBalloonWriter.formatDecimal

/** Serializes plot and subplot geometries (`Point`, `LineString`, `Polygon`, `MultiGeometry`). */
internal object KmlGeometryWriter {
  fun appendPlacemarkGeometry(
    out: StringBuilder,
    indent: String,
    geometry: Geometry?,
    subPlots: KmlSubPlots.ResolvedSubPlots,
  ) {
    if (!subPlots.isNotEmpty) {
      geometry?.let { out.appendGeometry(indent = indent, geometry = it) }
      return
    }
    out.appendLine("$indent<MultiGeometry>")
    geometry?.let { out.appendGeometryPrimitives(indent = "$indent  ", geometry = it) }
    subPlots.rings.forEach { ring ->
      out.appendLineString(
        indent = "$indent  ",
        positions = ring.positions,
        id = ring.id,
      )
    }
    out.appendLine("$indent</MultiGeometry>")
  }

  private fun StringBuilder.appendGeometry(indent: String, geometry: Geometry) {
    when (GeometryProtos.typeOf(geometry)) {
      GeometryType.GEOMETRY_TYPE_POINT -> geometry.point?.let { appendPoint(indent, it) }
      GeometryType.GEOMETRY_TYPE_LINE_STRING ->
        appendLineString(indent, geometry.line_string?.positions.orEmpty())
      GeometryType.GEOMETRY_TYPE_POLYGON ->
        appendPolygon(indent, geometry.polygon?.rings.orEmpty().map { it.positions })
      GeometryType.GEOMETRY_TYPE_MULTI_POINT ->
        appendMultiPoints(indent, geometry.multi_point?.positions.orEmpty())
      GeometryType.GEOMETRY_TYPE_MULTI_LINE_STRING ->
        appendMultiLines(indent, geometry.multi_line_string?.lines.orEmpty().map { it.positions })
      GeometryType.GEOMETRY_TYPE_MULTI_POLYGON ->
        appendMultiPolygons(
          indent,
          geometry.multi_polygon?.polygons.orEmpty().map { poly ->
            poly.rings.map { it.positions }
          },
        )
      GeometryType.GEOMETRY_TYPE_UNSPECIFIED,
      null -> Unit
    }
  }

  private fun StringBuilder.appendGeometryPrimitives(indent: String, geometry: Geometry) {
    when (GeometryProtos.typeOf(geometry)) {
      GeometryType.GEOMETRY_TYPE_MULTI_POINT ->
        geometry.multi_point?.positions.orEmpty().forEach { appendPoint(indent, it) }
      GeometryType.GEOMETRY_TYPE_MULTI_LINE_STRING ->
        geometry.multi_line_string?.lines.orEmpty().forEach {
          appendLineString(indent, it.positions)
        }
      GeometryType.GEOMETRY_TYPE_MULTI_POLYGON ->
        geometry.multi_polygon?.polygons.orEmpty().forEach { poly ->
          appendPolygon(indent, poly.rings.map { it.positions })
        }
      else -> appendGeometry(indent, geometry)
    }
  }

  private fun StringBuilder.appendPoint(indent: String, position: Position) {
    appendLine("$indent<Point>")
    appendLine("$indent  <coordinates>${kmlCoord(position)}</coordinates>")
    appendLine("$indent</Point>")
  }

  private fun StringBuilder.appendLineString(
    indent: String,
    positions: List<Position>,
    id: String? = null,
  ) {
    if (positions.isEmpty()) return
    val openTag =
      if (id.isNullOrBlank()) "<LineString>" else """<LineString id="${escapeXml(id)}">"""
    appendLine("$indent$openTag")
    appendLine("$indent  <tessellate>1</tessellate>")
    appendLine(
      "$indent  <coordinates>${positions.joinToString(" ", transform = ::kmlCoord)}</coordinates>"
    )
    appendLine("$indent</LineString>")
  }

  private fun StringBuilder.appendMultiPoints(indent: String, positions: List<Position>) {
    if (positions.isEmpty()) return
    appendLine("$indent<MultiGeometry>")
    positions.forEach { appendPoint("$indent  ", it) }
    appendLine("$indent</MultiGeometry>")
  }

  private fun StringBuilder.appendMultiLines(indent: String, lines: List<List<Position>>) {
    if (lines.isEmpty()) return
    appendLine("$indent<MultiGeometry>")
    lines.forEach { appendLineString("$indent  ", it) }
    appendLine("$indent</MultiGeometry>")
  }

  private fun StringBuilder.appendMultiPolygons(
    indent: String,
    polygons: List<List<List<Position>>>,
  ) {
    if (polygons.isEmpty()) return
    appendLine("$indent<MultiGeometry>")
    polygons.forEach { appendPolygon("$indent  ", it) }
    appendLine("$indent</MultiGeometry>")
  }

  private fun StringBuilder.appendPolygon(indent: String, rings: List<List<Position>>) {
    val outerRaw = rings.firstOrNull().orEmpty()
    if (outerRaw.isEmpty()) return
    appendLine("$indent<Polygon>")
    appendLine("$indent  <tessellate>1</tessellate>")
    val outer = closedRing(outerRaw)
    appendLine("$indent  <outerBoundaryIs>")
    appendLine("$indent    <LinearRing>")
    appendLine(
      "$indent      <coordinates>${outer.joinToString(" ", transform = ::kmlCoord)}</coordinates>"
    )
    appendLine("$indent    </LinearRing>")
    appendLine("$indent  </outerBoundaryIs>")
    rings.drop(1).forEach { hole ->
      if (hole.isNotEmpty()) {
        val closedHole = closedRing(hole)
        appendLine("$indent  <innerBoundaryIs>")
        appendLine("$indent    <LinearRing>")
        appendLine(
          "$indent      <coordinates>${closedHole.joinToString(" ", transform = ::kmlCoord)}</coordinates>"
        )
        appendLine("$indent    </LinearRing>")
        appendLine("$indent  </innerBoundaryIs>")
      }
    }
    appendLine("$indent</Polygon>")
  }

  private fun closedRing(ring: List<Position>): List<Position> =
    if (
      ring.size >= 2 &&
        (ring.first().latitude != ring.last().latitude ||
          ring.first().longitude != ring.last().longitude)
    ) {
      ring + ring.first()
    } else {
      ring
    }

  private fun kmlCoord(point: Position): String =
    "${formatDecimal(point.longitude, 6)},${formatDecimal(point.latitude, 6)},0"
}
