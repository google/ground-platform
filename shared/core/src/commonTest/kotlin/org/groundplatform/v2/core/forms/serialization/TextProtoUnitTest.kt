/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.core.forms.serialization

import groundplatform.v2.forms.FieldValue
import groundplatform.v2.forms.RecordInstance
import groundplatform.v2.forms.RecordNode
import groundplatform.v2.forms.TypedValue
import kotlin.test.Test
import kotlin.test.assertEquals
import okio.ByteString.Companion.toByteString

class TextProtoUnitTest {

  @Test
  fun testFormDefXmlTextProtoRtoundTrip() {
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
    val textProto = TextProtoSerializer.serializeFormDef(formDef)
    val decodedFormDef = TextProtoSerializer.deserializeFormDef(textProto)

    assertEquals(formDef.form_id, decodedFormDef.form_id)
    assertEquals(formDef.title, decodedFormDef.title)
    assertEquals(formDef.version, decodedFormDef.version)
    assertEquals(formDef, decodedFormDef)
  }

  @Test
  fun testRecordInstanceXmlTextProtoRoundTrip() {
    val sampleXml =
      """
      <data id="tree_survey" version="2026091701">
        <meta>
          <instanceID>uuid:8f7e6d5c-4b3a-2109-8765-4321fedcba98</instanceID>
        </meta>
        <species>Quercus robur</species>
        <height_m>18.5</height_m>
      </data>
      """
        .trimIndent()

    val record = XFormsXmlSerializer.deserializeRecordInstance(sampleXml)
    val textProto = TextProtoSerializer.serializeRecordInstance(record)
    val decodedRecord = TextProtoSerializer.deserializeRecordInstance(textProto)

    assertEquals(record, decodedRecord)
  }

  @Test
  fun testBinaryValueSurvivesTextProtoRoundTrip() {
    // Regression: `binary_value` used to be written with `ByteString.utf8()`, which replaces every
    // invalid UTF-8 sequence with U+FFFD. Any real payload (a PNG header, encrypted bytes, a lone
    // 0xFF) was silently corrupted on the way out and unrecoverable on the way back in.
    val payload =
      byteArrayOf(
          0x89.toByte(),
          0x50,
          0x4E,
          0x47,
          0x0D,
          0x0A,
          0x1A,
          0x0A,
          0x00,
          0xFF.toByte(),
          0xFE.toByte(),
          0xC0.toByte(),
          0x22,
          0x5C,
        )
        .toByteString()

    val record =
      RecordInstance(
        form_id = "binary_form",
        data_ =
          RecordNode(
            fields = mapOf("photo" to FieldValue(scalar_value = TypedValue(binary_value = payload)))
          ),
      )

    val textProto = TextProtoSerializer.serializeRecordInstance(record)
    val decoded = TextProtoSerializer.deserializeRecordInstance(textProto)

    assertEquals(
      payload,
      decoded.data_?.fields?.get("photo")?.scalar_value?.binary_value,
      "binary_value must round-trip byte-for-byte",
    )
    assertEquals(record, decoded)
  }
}
