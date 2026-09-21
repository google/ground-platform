/*
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
package org.groundplatform.v2.devtools.formdebugger

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FormDebuggerStateTest {

  @Test
  fun testInitialSampleDataIsLoadedAndEvaluated() {
    val state = FormDebuggerState(loadInitialSample = true)
    assertEquals(ProtoRepresentation.TEXTPROTO, state.protoRepresentation)
    assertTrue(state.formXml.isNotBlank())
    assertTrue(state.formTextProto.isNotBlank())
    assertNull(state.formXmlError)
    assertNull(state.formTextProtoError)

    assertTrue(state.recordXml.isNotBlank())
    assertTrue(state.recordTextProto.isNotBlank())
    assertNull(state.recordXmlError)
    assertNull(state.recordTextProtoError)

    assertContains(
      state.xpathOutput,
      "String Output: Adansonia digitata (height: 24.8m, health: healthy)",
    )
  }

  @Test
  fun testFormDefBidiSyncXmlToTextProtoAndBack() {
    val state = FormDebuggerState(loadInitialSample = false)

    val xml =
      """
      <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml">
        <h:head>
          <h:title>Test Form</h:title>
          <model>
            <instance>
              <data id="my_form" version="1">
                <name/>
              </data>
            </instance>
            <bind nodeset="/data/name" type="string"/>
          </model>
        </h:head>
        <h:body>
          <input ref="/data/name">
            <label>Your Name</label>
          </input>
        </h:body>
      </h:html>
      """
        .trimIndent()

    // 1. Paste XML -> should populate TextProto
    state.onFormXmlChanged(xml)
    assertNull(state.formXmlError)
    assertNull(state.formTextProtoError)
    assertContains(state.formTextProto, "form_id: \"my_form\"")
    assertContains(state.formTextProto, "title: \"Test Form\"")

    // 2. Edit TextProto -> should update XML
    val updatedTextProto =
      state.formTextProto.replace("title: \"Test Form\"", "title: \"Updated Survey Title\"")
    state.onFormTextProtoChanged(updatedTextProto)
    assertNull(state.formTextProtoError)
    assertNull(state.formXmlError)
    assertContains(state.formXml, "<h:title>Updated Survey Title</h:title>")
  }

  @Test
  fun testRecordInstanceBidiSyncXmlToTextProtoAndBack() {
    val state = FormDebuggerState(loadInitialSample = true)

    // 1. Modify Record XML -> should update Record TextProto & XPath output
    val newRecordXml =
      """
      <data id="baobab_survey" version="2026091701">
        <meta>
          <instanceID>uuid:1234</instanceID>
        </meta>
        <species>Adansonia grandidieri</species>
        <height_m>30.0</height_m>
        <circumference_m>18.0</circumference_m>
        <health_status>healthy</health_status>
      </data>
      """
        .trimIndent()

    state.onRecordXmlChanged(newRecordXml)
    assertNull(state.recordXmlError)
    assertNull(state.recordTextProtoError)
    assertContains(state.recordTextProto, "string_value: \"Adansonia grandidieri\"")
    assertContains(state.recordTextProto, "double_value: 30.0")
    assertContains(state.xpathOutput, "Adansonia grandidieri")

    // 2. Modify Record TextProto -> should update Record XML & XPath output
    val updatedTextProto =
      state.recordTextProto.replace(
        "string_value: \"Adansonia grandidieri\"",
        "string_value: \"Adansonia rubrostipa\"",
      )
    state.onRecordTextProtoChanged(updatedTextProto)
    assertNull(state.recordTextProtoError)
    assertNull(state.recordXmlError)
    assertContains(state.recordXml, "<species>Adansonia rubrostipa</species>")
    assertContains(state.xpathOutput, "Adansonia rubrostipa")
  }

  @Test
  fun testSwitchingBetweenTextProtoAndJsonAndBidiSyncInJsonMode() {
    val state = FormDebuggerState(loadInitialSample = true)

    // Switch representation from TEXTPROTO to JSON
    state.onProtoRepresentationChanged(ProtoRepresentation.JSON)
    assertEquals(ProtoRepresentation.JSON, state.protoRepresentation)
    assertNull(state.formTextProtoError)
    assertNull(state.recordTextProtoError)

    // Verify right-hand panes now contain JSON
    assertContains(state.formTextProto, "\"formId\": \"baobab_survey\"")
    assertContains(state.formTextProto, "\"title\": \"Baobab Tree Survey\"")
    assertContains(state.recordTextProto, "\"formId\": \"baobab_survey\"")
    assertContains(state.recordTextProto, "\"stringValue\": \"Adansonia digitata\"")
    assertContains(state.recordTextProto, "\"doubleValue\": 24.8")

    // Edit FormDef JSON -> should update FormDef XML
    val updatedFormJson =
      state.formTextProto.replace(
        "\"title\": \"Baobab Tree Survey\"",
        "\"title\": \"Madagascar Baobab Survey\"",
      )
    state.onFormTextProtoChanged(updatedFormJson)
    assertNull(state.formTextProtoError)
    assertContains(state.formXml, "<h:title>Madagascar Baobab Survey</h:title>")

    // Edit RecordInstance JSON -> should update RecordInstance XML and XPath evaluation
    val updatedRecordJson =
      state.recordTextProto
        .replace(
          "\"stringValue\": \"Adansonia digitata\"",
          "\"stringValue\": \"Adansonia suarezensis\"",
        )
        .replace("\"doubleValue\": 24.8", "\"doubleValue\": 31.5")
    state.onRecordTextProtoChanged(updatedRecordJson)
    assertNull(state.recordTextProtoError)
    assertContains(state.recordXml, "<species>Adansonia suarezensis</species>")
    assertContains(state.recordXml, "<height_m>31.5</height_m>")
    assertContains(
      state.xpathOutput,
      "String Output: Adansonia suarezensis (height: 31.5m, health: healthy)",
    )

    // Invalid JSON should populate error state
    state.onRecordTextProtoChanged("{ malformed json ")
    assertNotNull(state.recordTextProtoError)

    // Switching back to TEXTPROTO should re-format valid current models back into textproto
    state.onProtoRepresentationChanged(ProtoRepresentation.TEXTPROTO)
    assertEquals(ProtoRepresentation.TEXTPROTO, state.protoRepresentation)
    assertNull(state.recordTextProtoError)
    assertContains(state.formTextProto, "title: \"Madagascar Baobab Survey\"")
    assertContains(state.recordTextProto, "string_value: \"Adansonia suarezensis\"")
  }

  @Test
  fun testRealTimeXPathEvaluationOnExpressionChange() {
    val state = FormDebuggerState(loadInitialSample = true)

    state.onXPathChanged("/data/height_m * 2")
    assertContains(state.xpathOutput, "String Output: 49.6")
    assertContains(state.xpathOutput, "Result Type:   Number")

    state.onXPathChanged("invalid_xpath(((")
    assertContains(state.xpathOutput, "XPath Error:")
  }

  @Test
  fun testRunFormAndMobileRunnerSyncsRecordAndXPath() {
    val state = FormDebuggerState(loadInitialSample = true)
    state.runForm()

    assertTrue(state.isFormRunnerOpen)
    val controller = assertNotNull(state.wizardController)
    assertEquals(5, controller.totalSteps) // 4 questions + 1 summary step

    // Update /data/species and /data/height_m via the mobile FormWizardController
    controller.updateString("/data/species", "Adansonia perrieri")
    controller.updateDouble("/data/height_m", 29.4)

    // Verify Section 2 (RecordInstance XML & TextProto) and Section 3 (XPath) updated live!
    assertContains(state.recordXml, "<species>Adansonia perrieri</species>")
    assertContains(state.recordXml, "<height_m>29.4</height_m>")
    assertContains(state.recordTextProto, "string_value: \"Adansonia perrieri\"")
    assertContains(
      state.xpathOutput,
      "String Output: Adansonia perrieri (height: 29.4m, health: healthy)",
    )

    state.closeFormRunner()
    assertEquals(false, state.isFormRunnerOpen)
  }
}
