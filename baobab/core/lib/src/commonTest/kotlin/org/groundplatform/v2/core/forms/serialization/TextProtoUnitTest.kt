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

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
