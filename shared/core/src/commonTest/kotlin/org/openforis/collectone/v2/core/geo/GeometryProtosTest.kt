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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.Geometry
import openforis.collectone.v1.data.GeometryType
import openforis.collectone.v1.data.MultiPolygonCoordinates
import openforis.collectone.v1.data.PolygonCoordinates
import openforis.collectone.v1.data.Position
import openforis.collectone.v1.data.PositionList
import openforis.collectone.v1.forms.GeoPoint
import openforis.collectone.v1.forms.GeoShape
import openforis.collectone.v1.forms.GeoTrace

class GeometryProtosTest {
  private val a = GeometryProtos.position(latitude = -0.4180, longitude = 36.9480)
  private val b = GeometryProtos.position(latitude = -0.4180, longitude = 36.9500)
  private val c = GeometryProtos.position(latitude = -0.4200, longitude = 36.9500)

  @Test
  fun point_storesLongitudeAndLatitudeWithItsType() {
    val point = GeometryProtos.point(latitude = -0.4182, longitude = 36.9491)

    assertEquals(GeometryType.GEOMETRY_TYPE_POINT, point.type)
    assertEquals(Position(longitude = 36.9491, latitude = -0.4182), point.point)
  }

  @Test
  fun polygon_closesAnOpenRing() {
    val polygon = GeometryProtos.polygon(listOf(listOf(a, b, c)))

    assertEquals(listOf(a, b, c, a), polygon.polygon?.rings?.single()?.positions)
  }

  @Test
  fun polygon_keepsAClosedRing() {
    val polygon = GeometryProtos.polygon(listOf(listOf(a, b, c, a)))

    assertEquals(listOf(a, b, c, a), polygon.polygon?.rings?.single()?.positions)
  }

  @Test
  fun typeOf_isTheCoordinatesCase() {
    assertEquals(
      GeometryType.GEOMETRY_TYPE_LINE_STRING,
      GeometryProtos.typeOf(GeometryProtos.lineString(listOf(a, b))),
    )
  }

  @Test
  fun typeOf_whenTypeAndCoordinatesDisagree_isNull() {
    val mislabeled = Geometry(type = GeometryType.GEOMETRY_TYPE_POLYGON, point = a)

    assertNull(GeometryProtos.typeOf(mislabeled))
  }

  @Test
  fun typeOf_withoutCoordinates_isNull() {
    assertNull(GeometryProtos.typeOf(Geometry(type = GeometryType.GEOMETRY_TYPE_POINT)))
  }

  @Test
  fun of_prefersTheCanonicalGeometry() {
    val canonical = GeometryProtos.point(latitude = -0.4182, longitude = 36.9491)
    val record =
      EntityRecord(geometry = canonical, point = GeoPoint(latitude = 1.0, longitude = 2.0))

    assertEquals(canonical, GeometryProtos.of(record))
  }

  @Test
  fun of_rowWithALegacyPoint_readsIt() {
    val record = EntityRecord(point = GeoPoint(latitude = -0.4182, longitude = 36.9491))

    assertEquals(
      GeometryProtos.point(latitude = -0.4182, longitude = 36.9491),
      GeometryProtos.of(record),
    )
  }

  @Test
  fun of_rowWithALegacyTrace_readsItAsALineString() {
    val trace =
      GeoTrace(
        points =
          listOf(
            GeoPoint(latitude = -0.4180, longitude = 36.9480),
            GeoPoint(latitude = -0.4180, longitude = 36.9500),
          )
      )

    assertEquals(
      GeometryProtos.lineString(listOf(a, b)),
      GeometryProtos.of(EntityRecord(trace = trace)),
    )
  }

  @Test
  fun of_rowWithALegacyShape_readsItAsAPolygon() {
    val shape =
      GeoShape(
        points =
          listOf(
            GeoPoint(latitude = -0.4180, longitude = 36.9480),
            GeoPoint(latitude = -0.4180, longitude = 36.9500),
            GeoPoint(latitude = -0.4200, longitude = 36.9500),
            GeoPoint(latitude = -0.4180, longitude = 36.9480),
          )
      )

    assertEquals(
      GeometryProtos.polygon(listOf(listOf(a, b, c))),
      GeometryProtos.of(EntityRecord(shape = shape)),
    )
  }

  @Test
  fun of_recordWithoutGeometry_isNull() {
    assertNull(GeometryProtos.of(EntityRecord(entity_id = "row-1")))
  }

  @Test
  fun representativePosition_ofAPolygon_isTheAverageOfItsOpenRing() {
    val polygon = GeometryProtos.polygon(listOf(listOf(a, b, c)))

    val center = GeometryProtos.representativePosition(polygon)

    assertEquals(-0.418666, center!!.latitude, 1e-6)
    assertEquals(36.949333, center.longitude, 1e-6)
  }

  @Test
  fun representativePosition_ofALine_isItsFirstVertex() {
    assertEquals(a, GeometryProtos.representativePosition(GeometryProtos.lineString(listOf(a, b))))
  }

  @Test
  fun polygonVertexCount_countsEveryRingOfEveryPolygon() {
    val square = PolygonCoordinates(rings = listOf(PositionList(listOf(a, b, c, a))))
    val multi =
      Geometry(
        type = GeometryType.GEOMETRY_TYPE_MULTI_POLYGON,
        multi_polygon = MultiPolygonCoordinates(polygons = listOf(square, square)),
      )

    assertEquals(8, GeometryProtos.polygonVertexCount(multi))
  }

  @Test
  fun vertexCount_leavesOutEachRingsClosingVertex() {
    val multi =
      GeometryProtos.multiPolygon(listOf(listOf(listOf(a, b, c)), listOf(listOf(a, b, c))))

    assertEquals(6, GeometryProtos.vertexCount(multi))
  }

  @Test
  fun vertexCount_countsEveryVertexOfALine() {
    assertEquals(3, GeometryProtos.vertexCount(GeometryProtos.lineString(listOf(a, b, c))))
  }
}
