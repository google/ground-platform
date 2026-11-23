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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import openforis.collectone.v1.data.Geometry as GeometryProto
import openforis.collectone.v1.data.GeometryType as GeometryTypeProto
import openforis.collectone.v1.data.MultiPolygonCoordinates
import openforis.collectone.v1.data.PolygonCoordinates
import openforis.collectone.v1.data.PositionList
import org.openforis.collectone.v2.core.geo.GeometryProtos
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng

class GeometryMapperTest {
  private val a = LatLng(-0.4190, 36.9500)
  private val b = LatLng(-0.4190, 36.9520)
  private val c = LatLng(-0.4205, 36.9520)

  @Test
  fun point_roundTrips() {
    val point = Geometry.Point(LatLng(-0.4182, 36.9491))

    assertEquals(point, GeometryMapper.fromProto(GeometryMapper.toProto(point)))
  }

  @Test
  fun polygon_isStoredWithAClosedRing() {
    val proto = GeometryMapper.toProto(Geometry.Polygon(listOf(listOf(a, b, c))))

    assertEquals(4, proto.polygon?.rings?.single()?.positions?.size)
    assertEquals(GeometryTypeProto.GEOMETRY_TYPE_POLYGON, proto.type)
  }

  @Test
  fun polygon_roundTripsWithAnOpenRing() {
    val polygon = Geometry.Polygon(listOf(listOf(a, b, c)))

    assertEquals(polygon, GeometryMapper.fromProto(GeometryMapper.toProto(polygon)))
  }

  @Test
  fun lineString_roundTrips() {
    val line = Geometry.LineString(listOf(a, b))

    assertEquals(line, GeometryMapper.fromProto(GeometryMapper.toProto(line)))
  }

  @Test
  fun multiPolygon_keepsEveryPart() {
    val first =
      checkNotNull(GeometryMapper.toProto(Geometry.Polygon(listOf(listOf(a, b, c)))).polygon)
    val second = PolygonCoordinates(rings = listOf(PositionList(first.rings.single().positions)))
    val multi =
      GeometryProto(
        type = GeometryTypeProto.GEOMETRY_TYPE_MULTI_POLYGON,
        multi_polygon = MultiPolygonCoordinates(polygons = listOf(first, second)),
      )

    val polygon = Geometry.Polygon(listOf(listOf(a, b, c)))
    assertEquals(Geometry.MultiPolygon(listOf(polygon, polygon)), GeometryMapper.fromProto(multi))
  }

  @Test
  fun multiPartGeometries_roundTrip() {
    val geometries =
      listOf(
        Geometry.MultiPoint(listOf(Geometry.Point(a), Geometry.Point(b))),
        Geometry.MultiLineString(
          listOf(Geometry.LineString(listOf(a, b)), Geometry.LineString(listOf(b, c)))
        ),
        Geometry.MultiPolygon(listOf(Geometry.Polygon(listOf(listOf(a, b, c))))),
      )

    for (geometry in geometries) {
      assertEquals(geometry, GeometryMapper.fromProto(GeometryMapper.toProto(geometry)))
    }
  }

  @Test
  fun multiPolygonWithAnInvalidPart_isNull() {
    val valid =
      checkNotNull(GeometryMapper.toProto(Geometry.Polygon(listOf(listOf(a, b, c)))).polygon)
    val degenerate =
      PolygonCoordinates(rings = listOf(PositionList(valid.rings.single().positions.take(2))))
    val multi =
      GeometryProto(
        type = GeometryTypeProto.GEOMETRY_TYPE_MULTI_POLYGON,
        multi_polygon = MultiPolygonCoordinates(polygons = listOf(valid, degenerate)),
      )

    assertNull(GeometryMapper.fromProto(multi))
  }

  @Test
  fun geometryWhoseTypeDisagreesWithItsCoordinates_isNull() {
    val point = GeometryProtos.point(latitude = -0.4182, longitude = 36.9491)

    assertNull(GeometryMapper.fromProto(point.copy(type = GeometryTypeProto.GEOMETRY_TYPE_POLYGON)))
  }

  @Test
  fun outOfRangeCoordinates_areNull() {
    assertNull(GeometryMapper.fromProto(GeometryProtos.point(latitude = 91.0, longitude = 0.0)))
  }

  @Test
  fun types_roundTrip() {
    for (type in GeometryType.entries) {
      assertEquals(type, GeometryMapper.typeFromProto(GeometryMapper.typeToProto(type)))
    }
  }
}
