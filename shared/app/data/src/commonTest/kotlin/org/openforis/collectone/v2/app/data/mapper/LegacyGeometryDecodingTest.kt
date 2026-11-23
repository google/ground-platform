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

import com.squareup.wire.ProtoAdapter
import com.squareup.wire.ProtoWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import okio.Buffer
import okio.ByteString
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.forms.GeoPoint
import openforis.collectone.v1.survey.LayerDef
import org.openforis.collectone.v2.app.domain.model.SurveyMapAnchor
import org.openforis.collectone.v2.app.domain.model.geometryType
import org.openforis.collectone.v2.core.geo.GeometryProtos
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng

/**
 * Entities and layers stored before the canonical geometry still decode: the mapper reads their
 * legacy fields when `geometry` is absent, and writes only `geometry`.
 */
class LegacyGeometryDecodingTest {
  private val surveyId = "00000033-6b50-51cd-ac94-baea2ea06d65"

  private fun entityOf(record: EntityRecord) =
    ProtoRecordMapper.entityFromProto(record, submissions = emptyList(), isGenerated = false)

  /** A string field as an older client wrote it, before its number was reserved. */
  private fun storedField(tag: Int, value: String): ByteString {
    val buffer = Buffer()
    ProtoAdapter.STRING.encodeWithTag(ProtoWriter(buffer), tag, value)
    return buffer.readByteString()
  }

  /** A float field as an older client wrote it, before its number was reserved. */
  private fun storedField(tag: Int, value: Float): ByteString {
    val buffer = Buffer()
    ProtoAdapter.FLOAT.encodeWithTag(ProtoWriter(buffer), tag, value)
    return buffer.readByteString()
  }

  /** An entity as stored before `geometry`: decoded from its bytes, with the labels it had. */
  private fun legacyRecord(label: String): EntityRecord {
    val stored =
      Buffer()
        .write(EntityRecord(entity_id = "plot-1", survey_id = surveyId).encodeByteString())
        .write(storedField(tag = 25, value = 0.28f))
        .write(storedField(tag = 26, value = 0.32f))
        .write(storedField(tag = 21, value = label))
        .write(storedField(tag = 24, value = "0.4182°S, 36.9481°E"))
        .readByteString()
    return EntityRecord.ADAPTER.decode(stored)
  }

  @Test
  fun labeledPolygon_decodesToTheShapeTheAppDrew() {
    val entity = entityOf(legacyRecord(label = "Polygon"))

    assertEquals(GeometryType.POLYGON, entity.geometryType)
  }

  @Test
  fun labeledPolygon_keepsTheVerticesTheAppDrewAroundItsNormalizedPosition() {
    val anchor = SurveyMapAnchor.forSurvey(surveyId)
    fun at(dx: Double, dy: Double) =
      anchor.toLatLng(0.28f.toDouble() + dx * 0.22, 0.32f.toDouble() + dy * 0.11)

    val ring =
      (entityOf(legacyRecord(label = "Polygon")).geometry as Geometry.Polygon).rings.single()

    val expected = listOf(at(-0.48, -0.42), at(0.45, -0.5), at(0.52, 0.38), at(-0.4, 0.48))
    expected.zip(ring).forEach { (want, got) ->
      assertEquals(want.latitude, got.latitude, absoluteTolerance = 1e-12)
      assertEquals(want.longitude, got.longitude, absoluteTolerance = 1e-12)
    }
  }

  @Test
  fun labeledLineString_decodesToALine() {
    assertEquals(
      GeometryType.LINE_STRING,
      entityOf(legacyRecord(label = "LineString")).geometryType,
    )
  }

  @Test
  fun rowWithoutALabel_hasNoGeometry() {
    assertNull(entityOf(legacyRecord(label = "")).geometry)
  }

  @Test
  fun legacyPoint_decodesToItsCoordinates() {
    val record =
      EntityRecord(entity_id = "plot-1", point = GeoPoint(latitude = -0.4182, longitude = 36.9491))

    assertEquals(Geometry.Point(LatLng(-0.4182, 36.9491)), entityOf(record).geometry)
  }

  @Test
  fun canonicalGeometry_winsOverLegacyFields() {
    val record =
      legacyRecord(label = "Polygon")
        .copy(geometry = GeometryProtos.point(latitude = -0.4182, longitude = 36.9491))

    assertEquals(Geometry.Point(LatLng(-0.4182, 36.9491)), entityOf(record).geometry)
  }

  @Test
  fun rewrittenRow_carriesOnlyTheCanonicalGeometry() {
    val rewritten =
      ProtoRecordMapper.entityToProto(surveyId, entityOf(legacyRecord(label = "Polygon")))

    assertEquals(GeometryProtos.of(rewritten), rewritten.geometry)
    assertEquals(ByteString.EMPTY, rewritten.unknownFields)
  }

  @Test
  fun labeledLayer_decodesItsGeometryType() {
    val layer =
      LayerDef.ADAPTER.decode(
        Buffer()
          .write(LayerDef(id = "l1").encodeByteString())
          .write(storedField(tag = 8, value = "Point"))
          .readByteString()
      )

    assertEquals(GeometryType.POINT, LegacyEntityGeometry.typeOf(layer))
  }
}
