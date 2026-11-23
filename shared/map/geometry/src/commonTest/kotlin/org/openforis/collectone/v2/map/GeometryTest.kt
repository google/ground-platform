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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeometryTest {
  private val a = LatLng(0.0, 0.0)
  private val b = LatLng(0.0, 2.0)
  private val c = LatLng(2.0, 2.0)
  private val d = LatLng(2.0, 0.0)
  private val farSquare =
    Geometry.Polygon(
      listOf(listOf(LatLng(10.0, 10.0), LatLng(10.0, 11.0), LatLng(11.0, 11.0), LatLng(11.0, 10.0)))
    )

  @Test
  fun coercedPositionInRange_isKept() {
    assertEquals(LatLng(-0.416, 36.947), LatLng.coerced(-0.416, 36.947))
  }

  @Test
  fun coercedPositionOutOfRange_isClampedInsteadOfThrowing() {
    assertEquals(LatLng(90.0, -180.0), LatLng.coerced(91.5, -200.0))
  }

  @Test
  fun coercedMissingPosition_becomesZero() {
    assertEquals(LatLng(0.0, 0.0), LatLng.coerced(Double.NaN, Double.NaN))
  }

  @Test
  fun minVertices_matchTheSmallestGeometryOfEachType() {
    assertEquals(1, GeometryType.POINT.minVertices)
    assertEquals(2, GeometryType.LINE_STRING.minVertices)
    assertEquals(3, GeometryType.POLYGON.minVertices)
  }

  @Test
  fun singleGeometry_isItsOnlyPart() {
    val point = Geometry.Point(a)

    assertEquals(listOf<Geometry>(point), point.parts)
  }

  @Test
  fun multiPolygon_hasThePolygonTypeAndEveryPartsExteriorVertices() {
    val near = Geometry.Polygon(listOf(listOf(a, b, c, d)))
    val multi = Geometry.MultiPolygon(listOf(near, farSquare))

    assertEquals(GeometryType.POLYGON, multi.type)
    assertEquals(near.coordinates + farSquare.coordinates, multi.coordinates)
    assertEquals(LngLatBounds(west = 0.0, south = 0.0, east = 11.0, north = 11.0), multi.bounds())
  }

  @Test
  fun multiPartGeometry_isRepresentedByItsFirstPart() {
    val multi =
      Geometry.MultiPolygon(listOf(Geometry.Polygon(listOf(listOf(a, b, c, d))), farSquare))

    assertEquals(LatLng(1.0, 1.0), multi.representativePoint)
  }

  @Test
  fun multiPointAndMultiLineString_haveTheTypesOfTheirParts() {
    val points = Geometry.MultiPoint(listOf(Geometry.Point(a), Geometry.Point(c)))
    val lines = Geometry.MultiLineString(listOf(Geometry.LineString(listOf(a, b))))

    assertEquals(GeometryType.POINT, points.type)
    assertEquals(listOf(a, c), points.coordinates)
    assertEquals(GeometryType.LINE_STRING, lines.type)
    assertEquals(a, lines.representativePoint)
  }

  @Test
  fun latLng_rejectsOutOfRangeCoordinates() {
    assertFailsWith<IllegalArgumentException> { LatLng(90.5, 0.0) }
    assertFailsWith<IllegalArgumentException> { LatLng(0.0, -180.5) }
  }

  @Test
  fun polygon_isRepresentedByItsExteriorRingsAverageIgnoringHoles() {
    val hole = listOf(LatLng(0.5, 0.5), LatLng(0.5, 0.6), LatLng(0.6, 0.6))
    val polygon = Geometry.Polygon(listOf(listOf(a, b, c, d), hole))

    assertEquals(LatLng(1.0, 1.0), polygon.representativePoint)
    assertEquals(listOf(a, b, c, d), polygon.coordinates)
  }

  @Test
  fun lineString_isRepresentedByItsFirstVertex() {
    val line = Geometry.LineString(listOf(c, a))

    assertEquals(c, line.representativePoint)
    assertEquals(LngLatBounds(west = 0.0, south = 0.0, east = 2.0, north = 2.0), line.bounds())
  }

  @Test
  fun geometries_rejectTooFewVerticesOrParts() {
    assertFailsWith<IllegalArgumentException> { Geometry.LineString(listOf(a)) }
    assertFailsWith<IllegalArgumentException> { Geometry.Polygon(emptyList()) }
    assertFailsWith<IllegalArgumentException> { Geometry.Polygon(listOf(listOf(a, b))) }
    assertFailsWith<IllegalArgumentException> {
      Geometry.Polygon(listOf(listOf(a, b, c), listOf(a, b)))
    }
    assertFailsWith<IllegalArgumentException> { Geometry.MultiPoint(emptyList()) }
    assertFailsWith<IllegalArgumentException> { Geometry.MultiLineString(emptyList()) }
    assertFailsWith<IllegalArgumentException> { Geometry.MultiPolygon(emptyList()) }
  }

  @Test
  fun bounds_centerAndContainmentIncludeTheEdges() {
    val bounds = LngLatBounds.of(listOf(a, c))

    assertEquals(LatLng(1.0, 1.0), bounds.center)
    assertTrue(b in bounds)
    assertFalse(LatLng(2.1, 1.0) in bounds)
  }

  @Test
  fun boundsOfAPoint_haveZeroSizeAndOfNothingFail() {
    assertEquals(LngLatBounds(2.0, 2.0, 2.0, 2.0), Geometry.Point(c).bounds())
    assertFailsWith<IllegalArgumentException> { LngLatBounds.of(emptyList()) }
  }
}
