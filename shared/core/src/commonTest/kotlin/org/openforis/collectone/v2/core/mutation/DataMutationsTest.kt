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
package org.openforis.collectone.v2.core.mutation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import okio.ByteString.Companion.encodeUtf8
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.Geometry
import openforis.collectone.v1.data.SubmissionRecord
import openforis.collectone.v1.forms.FieldValue
import openforis.collectone.v1.forms.GeoPoint
import openforis.collectone.v1.forms.RecordInstance
import openforis.collectone.v1.forms.RecordNode
import openforis.collectone.v1.forms.RecordNodeList
import openforis.collectone.v1.forms.TypedValue

class DataMutationsTest {

  private fun text(value: String) = FieldValue(scalar_value = TypedValue(string_value = value))

  private fun entry(id: String, vararg fields: Pair<String, FieldValue>) =
    RecordNode(id = id, fields = fields.toMap())

  private fun repeat(vararg entries: RecordNode) =
    FieldValue(repeat_value = RecordNodeList(nodes = entries.toList()))

  private fun submission(vararg fields: Pair<String, FieldValue>) =
    SubmissionRecord(record = RecordInstance(data_ = RecordNode(fields = fields.toMap())))

  private fun SubmissionRecord.trees(): List<RecordNode> =
    record!!.data_!!.fields.getValue("trees").repeat_value!!.nodes

  @Test
  fun entityGeometryUpsert_replacesEveryOtherGeometryForm() {
    val legacy = EntityRecord(entity_id = "e", point = GeoPoint(latitude = 1.0, longitude = 2.0))

    val merged = DataMutations.applyEntity(legacy, upsert = EntityRecord(geometry = Geometry()))

    assertEquals(Geometry(), merged.geometry)
    assertNull(merged.point)
  }

  @Test
  fun entityGeometryDelete_clearsCanonicalAndLegacyForms() {
    val record = EntityRecord(entity_id = "e", geometry = Geometry(), label = "Plot 1")

    val deleted =
      DataMutations.applyEntity(
        record,
        delete = EntityRecord(point = GeoPoint(latitude = 0.0, longitude = 0.0)),
      )

    assertNull(deleted.geometry)
    assertEquals("Plot 1", deleted.label)
  }

  @Test
  fun repeatEntries_withIds_mergeByIdKeepingUntouchedEntries() {
    val base = submission("trees" to repeat(entry("t1", "species" to text("oak")), entry("t2")))
    val upsert = submission("trees" to repeat(entry("t2", "species" to text("pine"))))

    val merged = DataMutations.applySubmission(base, upsert = upsert)

    assertEquals(listOf("t1", "t2"), merged.trees().map { it.id })
    assertEquals(text("pine"), merged.trees()[1].fields["species"])
    assertEquals(text("oak"), merged.trees()[0].fields["species"])
  }

  @Test
  fun repeatEntries_withoutIds_areReplacedWhole() {
    val base = submission("trees" to repeat(entry("", "species" to text("oak")), entry("")))
    val upsert = submission("trees" to repeat(entry("", "species" to text("pine"))))

    val merged = DataMutations.applySubmission(base, upsert = upsert)

    assertEquals(listOf(text("pine")), merged.trees().map { it.fields["species"] })
  }

  @Test
  fun answerDelete_removesLeavesAndRepeatEntriesById() {
    val base =
      submission(
        "name" to text("Ana"),
        "trees" to repeat(entry("t1"), entry("t2", "species" to text("oak"), "dbh" to text("9"))),
      )
    val delete =
      submission(
        "name" to FieldValue(),
        "trees" to repeat(entry("t1"), entry("t2", "dbh" to FieldValue())),
      )

    val result = DataMutations.applySubmission(base, delete = delete)

    assertNull(result.record!!.data_!!.fields["name"])
    // t1 had only its key in the mask, so it is removed; t2 only loses the answer named in it.
    assertEquals(listOf("t2"), result.trees().map { it.id })
    assertEquals(setOf("species"), result.trees().single().fields.keys)
  }

  @Test
  fun recordAndRawPayload_replaceEachOther() {
    val withRecord = submission("name" to text("Ana"))

    val raw =
      DataMutations.applySubmission(
        withRecord,
        upsert = SubmissionRecord(raw_payload = "<data/>".encodeUtf8()),
      )
    val back = DataMutations.applySubmission(raw, upsert = submission("name" to text("Bo")))

    assertNull(raw.record)
    assertNull(back.raw_payload)
    assertEquals(text("Bo"), back.record!!.data_!!.fields["name"])
  }

  @Test
  fun serverUpdatedAt_isNeverTakenFromAnUpsert() {
    val upsert = SubmissionRecord(server_updated_at = com.squareup.wire.ofEpochSecond(100, 0))

    assertNull(DataMutations.applySubmission(SubmissionRecord(), upsert = upsert).server_updated_at)
  }
}
