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

import com.squareup.wire.ProtoWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import okio.Buffer
import openforis.collectone.v1.data.GeometryType as GeometryTypeProto
import openforis.collectone.v1.forms.GeoPoint
import openforis.collectone.v1.forms.GeoShape
import openforis.collectone.v1.survey.DataTableRow
import openforis.collectone.v1.survey.SurveyArea
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng

/**
 * Map layer rows and survey areas store canonical geometry and positions; rows and areas stored
 * with the legacy `forms.GeoPoint` / `forms.GeoShape` fields still decode.
 */
class SurveyGeometryMapperTest {
  private val a = LatLng(-0.4180, 36.9480)
  private val b = LatLng(-0.4180, 36.9500)
  private val c = LatLng(-0.4200, 36.9500)

  @Test
  fun polygonRow_roundTripsItsOpenRing() {
    val geometry = SurveyGeometryMapper.rowGeometryToProto(GeometryType.POLYGON, listOf(a, b, c))

    assertEquals(GeometryTypeProto.GEOMETRY_TYPE_POLYGON, geometry?.type)
    assertEquals(
      listOf(a, b, c),
      SurveyGeometryMapper.rowVertices(DataTableRow(row_key = "r1", geometry = geometry)),
    )
  }

  @Test
  fun pointRowWithSeveralVertices_keepsThemAll() {
    val geometry = SurveyGeometryMapper.rowGeometryToProto(GeometryType.POINT, listOf(a, b))

    assertEquals(GeometryTypeProto.GEOMETRY_TYPE_MULTI_POINT, geometry?.type)
    assertEquals(listOf(a, b), SurveyGeometryMapper.rowVertices(DataTableRow(geometry = geometry)))
  }

  @Test
  fun rowWithoutVertices_hasNoGeometry() {
    assertNull(SurveyGeometryMapper.rowGeometryToProto(GeometryType.LINE_STRING, emptyList()))
  }

  @Test
  fun legacyRow_decodesItsStoredVertices() {
    val buffer = Buffer().write(DataTableRow(row_key = "r1").encodeByteString())
    val writer = ProtoWriter(buffer)
    listOf(a, b).forEach {
      GeoPoint.ADAPTER.encodeWithTag(
        writer,
        3,
        GeoPoint(latitude = it.latitude, longitude = it.longitude),
      )
    }
    val row = DataTableRow.ADAPTER.decode(buffer.readByteString())

    assertEquals(listOf(a, b), SurveyGeometryMapper.rowVertices(row))
  }

  @Test
  fun area_roundTripsEveryPart() {
    val parts = listOf(listOf(a, b, c), listOf(c, b, a))
    val area = SurveyArea(boundary = SurveyGeometryMapper.areaBoundaryToProto(parts))

    assertEquals(GeometryTypeProto.GEOMETRY_TYPE_MULTI_POLYGON, area.boundary?.type)
    assertEquals(parts, SurveyGeometryMapper.areaParts(area))
  }

  @Test
  fun legacyArea_decodesItsStoredRings() {
    val ring = listOf(a, b, c)
    val buffer = Buffer().write(SurveyArea(name = "Area").encodeByteString())
    GeoShape.ADAPTER.encodeWithTag(
      ProtoWriter(buffer),
      2,
      GeoShape(points = ring.map { GeoPoint(latitude = it.latitude, longitude = it.longitude) }),
    )
    val area = SurveyArea.ADAPTER.decode(buffer.readByteString())

    assertEquals(listOf(ring), SurveyGeometryMapper.areaParts(area))
  }

  @Test
  fun areaCenter_roundTrips() {
    val area = SurveyArea(center = SurveyGeometryMapper.areaCenterToProto(a))

    assertEquals(a, SurveyGeometryMapper.areaCenter(area))
  }

  @Test
  fun legacyArea_decodesItsStoredCenter() {
    val buffer = Buffer().write(SurveyArea(name = "Area").encodeByteString())
    GeoPoint.ADAPTER.encodeWithTag(
      ProtoWriter(buffer),
      SurveyGeometryMapper.AREA_LEGACY_CENTER,
      GeoPoint(latitude = a.latitude, longitude = a.longitude),
    )
    val area = SurveyArea.ADAPTER.decode(buffer.readByteString())

    assertEquals(a, SurveyGeometryMapper.areaCenter(area))
  }

  @Test
  fun areaWithoutCenter_hasNone() {
    assertNull(SurveyGeometryMapper.areaCenter(SurveyArea(name = "Area")))
  }
}
