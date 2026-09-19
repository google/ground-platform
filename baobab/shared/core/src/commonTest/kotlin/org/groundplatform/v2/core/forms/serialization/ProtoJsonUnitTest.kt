/**
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License'); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.groundplatform.v2.core.forms.serialization

import groundplatform.v2.forms.GeoPoint
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.groundplatform.v2.core.forms.buildRecordInstance

class ProtoJsonUnitTest {

  @Test
  fun testFormDefXmlJsonRoundTrip() {
    val sampleXml =
      """
      <h:html xmlns="http://www.w3.org/2002/xforms"
              xmlns:h="http://www.w3.org/1999/xhtml"
              xmlns:jr="http://openrosa.org/javarosa">
        <h:head>
          <h:title>Tree Survey</h:title>
          <model>
            <instance>
              <data id="tree_survey" version="2026091701">
                <species/>
                <height_m/>
              </data>
            </instance>
            <bind nodeset="/data/species" type="string" required="true()"/>
            <bind nodeset="/data/height_m" type="decimal"/>
          </model>
        </h:head>
        <h:body>
          <input ref="/data/species">
            <label>Tree Species</label>
          </input>
          <input ref="/data/height_m">
            <label>Height (meters)</label>
          </input>
        </h:body>
      </h:html>
      """
        .trimIndent()

    val formDef = XFormsXmlSerializer.deserializeFormDef(sampleXml)
    val json = ProtoJsonSerializer.serializeFormDef(formDef)

    assertContains(json, "\"formId\": \"tree_survey\"")
    assertContains(json, "\"title\": \"Tree Survey\"")
    assertContains(json, "\"fieldPath\": \"species\"")

    val decodedFormDef = ProtoJsonSerializer.deserializeFormDef(json)
    assertEquals(formDef, decodedFormDef)

    // Also test snake_case JSON round-trip
    val snakeJson = ProtoJsonSerializer.serializeFormDef(formDef, preserveProtoFieldNames = true)
    assertContains(snakeJson, "\"form_id\": \"tree_survey\"")
    assertEquals(formDef, ProtoJsonSerializer.deserializeFormDef(snakeJson))
  }

  @Test
  fun testRecordInstanceRichTypesJsonRoundTrip() {
    val record =
      buildRecordInstance(
        formId = "baobab_survey",
        version = "2026091701",
        instanceId = "uuid:8f7e6d5c-4b3a-2109-8765-4321fedcba98",
        deviceId = "android-pixel-9",
      ) {
        string("species", "Adansonia digitata")
        int32("tree_count", 12)
        int64("sensor_id", 9876543210123L)
        double("height_m", 24.8)
        bool("is_protected", true)
        date("survey_date", 2026, 9, 18)
        time("survey_time", 14, 30, 15)
        timestamp("captured_at", 1789733415L, 123000000L)
        geopoint("location", -18.7669, 46.8691, 120.5, 3.2)
        geoshape(
          "canopy_boundary",
          GeoPoint(latitude = 0.0, longitude = 0.0),
          GeoPoint(latitude = 0.0, longitude = 1.0),
          GeoPoint(latitude = 1.0, longitude = 1.0),
          GeoPoint(latitude = 0.0, longitude = 0.0),
        )
        multiSelect("tags", "ancient", "flowering")
        group("soil") { string("ph_category", "neutral") }
        repeat("branches") {
          item { double("length_m", 5.2) }
          item { double("length_m", 6.1) }
        }
      }

    val json = ProtoJsonSerializer.serializeRecordInstance(record)
    assertContains(json, "\"formId\": \"baobab_survey\"")
    assertContains(json, "\"stringValue\": \"Adansonia digitata\"")
    assertContains(json, "\"int64Value\": \"9876543210123\"")
    assertContains(json, "\"timestampValue\": \"2026-09-18T12:10:15.123Z\"")

    val decoded = ProtoJsonSerializer.deserializeRecordInstance(json)
    assertEquals(record, decoded)
  }

  @Test
  fun testInvalidJsonThrowsIllegalArgumentException() {
    assertFailsWith<IllegalArgumentException> {
      ProtoJsonSerializer.deserializeFormDef("{not valid json")
    }
    assertFailsWith<IllegalArgumentException> {
      ProtoJsonSerializer.deserializeRecordInstance("[]")
    }
  }
}
