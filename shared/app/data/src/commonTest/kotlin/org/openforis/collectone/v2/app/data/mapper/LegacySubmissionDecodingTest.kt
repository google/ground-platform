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
import openforis.collectone.v1.data.SubmissionGeometryPolygonDef
import openforis.collectone.v1.data.SubmissionRecord
import org.openforis.collectone.v2.app.domain.model.SurveyMapAnchor
import org.openforis.collectone.v2.core.geo.GeometryProtos
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.LatLng

/**
 * Submissions stored before they had WGS 84 geometry still decode: the mapper reads the reserved
 * position, label, and rectangle fields when `location` or `geometry` is absent.
 */
class LegacySubmissionDecodingTest {
  private val surveyId = "00000033-6b50-51cd-ac94-baea2ea06d65"
  private val anchor = SurveyMapAnchor.forSurvey(surveyId)

  private fun stringField(tag: Int, value: String): ByteString {
    val buffer = Buffer()
    ProtoAdapter.STRING.encodeWithTag(ProtoWriter(buffer), tag, value)
    return buffer.readByteString()
  }

  private fun floatField(tag: Int, value: Float): ByteString {
    val buffer = Buffer()
    ProtoAdapter.FLOAT.encodeWithTag(ProtoWriter(buffer), tag, value)
    return buffer.readByteString()
  }

  /** A submission as an older client stored it, with the reserved [fields] it wrote. */
  private fun legacySubmission(vararg fields: ByteString): SubmissionRecord {
    val stored =
      Buffer()
        .write(SubmissionRecord(submission_id = "sub-1", survey_id = surveyId).encodeByteString())
    fields.forEach { stored.write(it) }
    return SubmissionRecord.ADAPTER.decode(stored.readByteString())
  }

  private fun assertNear(expected: LatLng, actual: LatLng?) {
    assertEquals(expected.latitude, actual!!.latitude, absoluteTolerance = 1e-6)
    assertEquals(expected.longitude, actual.longitude, absoluteTolerance = 1e-6)
  }

  @Test
  fun normalizedPosition_decodesToItsPlaceInTheSurveyMap() {
    val record = legacySubmission(floatField(24, 0.52f), floatField(25, 0.46f))

    val location = ProtoRecordMapper.submissionFromProto(record).location

    assertNear(anchor.toLatLng(0.52f.toDouble(), 0.46f.toDouble()), location)
  }

  @Test
  fun normalizedPosition_winsOverTheLabel() {
    val record =
      legacySubmission(
        stringField(23, "-0.4198°, 36.9512° (±3.2m GPS)"),
        floatField(24, 0.25f),
        floatField(25, 0.75f),
      )

    val location = ProtoRecordMapper.submissionFromProto(record).location

    assertNear(anchor.toLatLng(0.25f.toDouble(), 0.75f.toDouble()), location)
  }

  @Test
  fun labelWithHemispheres_decodesToItsCoordinates() {
    val record = legacySubmission(stringField(23, "Center: 0.41935° S, 36.95100° E (±2.4m GPS)"))

    assertNear(LatLng(-0.41935, 36.951), ProtoRecordMapper.submissionFromProto(record).location)
  }

  @Test
  fun signedLabel_decodesToItsCoordinates() {
    val record = legacySubmission(stringField(23, "-0.4198°, 36.9512° (±3.2m GPS)"))

    assertNear(LatLng(-0.4198, 36.9512), ProtoRecordMapper.submissionFromProto(record).location)
  }

  @Test
  fun rowWithoutAPosition_hasNoLocation() {
    assertNull(ProtoRecordMapper.submissionFromProto(legacySubmission()).location)
  }

  @Test
  fun canonicalLocation_winsOverLegacyFields() {
    val record =
      legacySubmission(floatField(24, 0.25f), floatField(25, 0.75f))
        .copy(location = GeometryProtos.point(latitude = -0.4182, longitude = 36.9491))

    assertEquals(LatLng(-0.4182, 36.9491), ProtoRecordMapper.submissionFromProto(record).location)
  }

  @Test
  fun legacyRectangle_decodesToItsCornersInTheSurveyMap() {
    val stored =
      Buffer()
        .write(SubmissionGeometryPolygonDef(id = "poly-1").encodeByteString())
        .write(floatField(14, 0.30f))
        .write(floatField(15, 0.34f))
        .write(floatField(16, 0.14f))
        .write(floatField(17, 0.10f))
        .readByteString()
    val polygon = SubmissionGeometryPolygonDef.ADAPTER.decode(stored)

    val ring =
      (ProtoRecordMapper.geometryPolygonFromProto(polygon, surveyId)!!.geometry as Geometry.Polygon)
        .rings
        .single()

    val x = 0.30f.toDouble()
    val y = 0.34f.toDouble()
    val halfWidth = 0.14f / 2.0
    val halfHeight = 0.10f / 2.0
    val expected =
      listOf(
        anchor.toLatLng(x - halfWidth, y - halfHeight),
        anchor.toLatLng(x + halfWidth, y - halfHeight),
        anchor.toLatLng(x + halfWidth, y + halfHeight),
        anchor.toLatLng(x - halfWidth, y + halfHeight),
      )
    expected.zip(ring).forEach { (want, got) -> assertNear(want, got) }
  }

  @Test
  fun polygonWithoutGeometry_isDropped() {
    assertNull(
      ProtoRecordMapper.geometryPolygonFromProto(SubmissionGeometryPolygonDef(id = "p"), surveyId)
    )
  }

  @Test
  fun rewrittenSubmission_carriesOnlyTheCanonicalLocation() {
    val decoded =
      ProtoRecordMapper.submissionFromProto(
        legacySubmission(floatField(24, 0.52f), floatField(25, 0.46f))
      )

    val rewritten = ProtoRecordMapper.submissionToProto(surveyId, decoded)

    assertEquals(ByteString.EMPTY, rewritten.unknownFields)
    assertEquals(decoded.location, ProtoRecordMapper.submissionFromProto(rewritten).location)
  }
}
