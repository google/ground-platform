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
package org.openforis.collectone.v2.server.userdata

import com.google.cloud.ByteArray
import com.google.cloud.spanner.Struct
import com.google.cloud.spanner.Type
import com.google.cloud.spanner.Value
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyState

class SpannerValuesTest {
  @Test
  fun render_decodesProtoAndEnumColumns_withTheGeneratedWireClasses() {
    val survey = SurveyDef(survey_id = "s-1", title = "Coffee plots")
    val row =
      Struct.newBuilder()
        .set("Record")
        .to(
          Value.protoMessage(
            ByteArray.copyFrom(SurveyDef.ADAPTER.encode(survey)),
            "openforis.collectone.v1.survey.SurveyDef",
          )
        )
        .set("State")
        .to(
          Value.protoEnum(
            SurveyState.PUBLISHED.value.toLong(),
            "openforis.collectone.v1.survey.SurveyState",
          )
        )
        .build()

    @Suppress("UNCHECKED_CAST") val record = SpannerValues.render(row, 0) as Map<String, Any?>
    assertEquals("proto", record["kind"])
    assertEquals("openforis.collectone.v1.survey.SurveyDef", record["type"])
    val json = record["json"] as String
    assertTrue("Coffee plots" in json && "s-1" in json, json)
    assertEquals("PUBLISHED", SpannerValues.render(row, 1))
    assertEquals(
      "openforis.collectone.v1.survey.SurveyDef",
      SpannerValues.typeName(row.getColumnType(0)),
    )
  }

  @Test
  fun render_returnsBase64ForUndecodableProtos_andLargeIntegersAsStrings() {
    val row =
      Struct.newBuilder()
        .set("Record")
        .to(Value.protoMessage(ByteArray.copyFrom(byteArrayOf(1, 2, 3)), "unknown.Message"))
        .set("Big")
        .to(Long.MAX_VALUE)
        .set("Small")
        .to(42L)
        .set("Missing")
        .to(Value.string(null))
        .set("Json")
        .to(Value.json(LONG_JSON))
        .set("Labels")
        .to(Value.stringArray(listOf("a", null)))
        .build()

    @Suppress("UNCHECKED_CAST") val record = SpannerValues.render(row, 0) as Map<String, Any?>
    assertEquals(true, record["decodeError"])
    assertEquals("AQID", record["base64"])
    assertEquals(Long.MAX_VALUE.toString(), SpannerValues.render(row, 1))
    assertEquals(42L, SpannerValues.render(row, 2))
    assertEquals(null, SpannerValues.render(row, 3))
    assertEquals(LONG_JSON, SpannerValues.render(row, 4), "JSON isn't abbreviated")
    assertEquals(listOf("a", "NULL"), SpannerValues.render(row, 5))
    assertEquals("ARRAY<INT64>", SpannerValues.typeName(Type.array(Type.int64())))
  }

  @Test
  fun categoryOf_groupsSpannerClientSpans() {
    assertEquals("commit", SpannerOperationRecorder.categoryOf("CloudSpannerOperation.Commit"))
    assertEquals(
      "query",
      SpannerOperationRecorder.categoryOf("CloudSpannerOperation.ExecuteStreamingQuery"),
    )
    assertEquals(
      "transaction",
      SpannerOperationRecorder.categoryOf("CloudSpanner.ReadWriteTransaction"),
    )
    assertEquals(
      "session",
      SpannerOperationRecorder.categoryOf("CloudSpannerOperation.BatchCreateSessions"),
    )
    assertEquals("dml", SpannerOperationRecorder.categoryOf("CloudSpannerOperation.ExecuteUpdate"))
  }

  @Test
  fun settingsFromEnv_requireAnEmulatorOrInstance() {
    assertEquals(null, SpannerSettings.fromEnv(emptyMap()))
    val emulator = SpannerSettings.fromEnv(mapOf("SPANNER_EMULATOR_HOST" to "localhost:9010"))!!
    assertEquals("localhost:9010", emulator.emulatorHost)
    assertEquals(
      "projects/collect-one-dev/instances/dev/databases/user-data",
      emulator.database.name,
    )
    val cloud =
      SpannerSettings.fromEnv(mapOf("SPANNER_INSTANCE_ID" to "prod", "SPANNER_PROJECT_ID" to "p"))!!
    assertEquals(null, cloud.emulatorHost)
    assertEquals("projects/p/instances/prod/databases/user-data", cloud.database.name)
  }

  companion object {
    private val LONG_JSON =
      "{\"properties\":{" + (1..40).joinToString(",") { "\"k$it\":$it" } + "}}"
  }
}
