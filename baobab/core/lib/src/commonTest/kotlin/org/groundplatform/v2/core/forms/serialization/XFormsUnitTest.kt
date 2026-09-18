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

import groundplatform.v2.forms.ActionType
import groundplatform.v2.forms.ControlType
import groundplatform.v2.forms.DataType
import groundplatform.v2.forms.EventType
import groundplatform.v2.forms.PreloadType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class XFormsUnitTest {

  @Test
  fun testDeserializeAndSerializeBindingsAndPreloads() {
    val xml =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml" xmlns:jr="http://openrosa.org/javarosa" xmlns:orx="http://openrosa.org/xforms">
        <h:head>
          <h:title>Bindings Test</h:title>
          <model>
            <instance>
              <data id="bindings_test" version="20250101">
                <meta>
                  <instanceID/>
                </meta>
                <start_time/>
                <device_id/>
                <age>25</age>
                <full_name/>
              </data>
            </instance>
            <bind nodeset="/data/meta/instanceID" type="string" readonly="true()" jr:preload="uid"/>
            <bind nodeset="/data/start_time" type="dateTime" jr:preload="timestamp" jr:preloadParams="start"/>
            <bind nodeset="/data/device_id" type="string" jr:preload="property" jr:preloadParams="deviceid"/>
            <bind nodeset="/data/age" type="int" required="true()" jr:requiredMsg="Age is required" constraint=". &gt;= 18" jr:constraintMsg="Must be adult"/>
            <bind nodeset="/data/full_name" type="string" relevant="/data/age &gt; 20" calculate="concat('User-', /data/age)"/>
          </model>
        </h:head>
        <h:body>
          <input ref="/data/age">
            <label>Enter Age</label>
          </input>
        </h:body>
      </h:html>
      """
        .trimIndent()

    val formDef = XFormsXmlSerializer.deserializeFormDef(xml)
    assertEquals("bindings_test", formDef.form_id)
    assertEquals("20250101", formDef.version)
    assertEquals("Bindings Test", formDef.title)

    val bindings = formDef.model?.bindings.orEmpty()
    assertEquals(5, bindings.size)

    val uidBinding = bindings.first { it.field_path == "meta/instanceID" }
    assertEquals(PreloadType.PRELOAD_UID, uidBinding.preload)
    assertTrue(uidBinding.read_only)

    val startBinding = bindings.first { it.field_path == "start_time" }
    assertEquals(DataType.TYPE_DATETIME, startBinding.type)
    assertEquals(PreloadType.PRELOAD_TIMESTAMP, startBinding.preload)
    assertEquals("start", startBinding.preload_param)

    val ageBinding = bindings.first { it.field_path == "age" }
    assertEquals(DataType.TYPE_INT32, ageBinding.type)
    assertEquals("true()", ageBinding.required_expression)
    assertEquals("Age is required", ageBinding.required_message)
    assertEquals(". >= 18", ageBinding.constraint_expression)
    assertEquals("Must be adult", ageBinding.constraint_message)

    val calcBinding = bindings.first { it.field_path == "full_name" }
    assertEquals("/data/age > 20", calcBinding.relevant_expression)
    assertEquals("concat('User-', /data/age)", calcBinding.calculate_expression)

    // Default value for /data/age should be 25
    val defaults = formDef.model?.primary_instance?.default_values
    assertNotNull(defaults)
    assertEquals(25, defaults.fields["age"]?.scalar_value?.int32_value)

    // Round-trip back to XML and re-deserialize
    val serializedXml = XFormsXmlSerializer.serialize(formDef)
    val deserialized = XFormsXmlSerializer.deserializeFormDef(serializedXml)
    assertEquals(formDef, deserialized)
  }

  @Test
  fun testTranslationsAndMediaCatalog() {
    val xml =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml" xmlns:jr="http://openrosa.org/javarosa">
        <h:head>
          <h:title>Multi-language Survey</h:title>
          <model>
            <itext>
              <translation lang="English" default="true()">
                <text id="q_tree:label">
                  <value>Select tree species</value>
                  <value form="short">Species</value>
                  <value form="guidance">Check the bark and leaves carefully</value>
                  <value form="image">jr://images/tree_guide.png</value>
                  <value form="audio">jr://audio/tree_prompt.mp3</value>
                </text>
              </translation>
              <translation lang="Spanish">
                <text id="q_tree:label">
                  <value>Seleccione la especie de árbol</value>
                  <value form="short">Especie</value>
                </text>
              </translation>
            </itext>
            <instance>
              <data id="lang_survey">
                <tree/>
              </data>
            </instance>
            <bind nodeset="/data/tree" type="select1"/>
          </model>
        </h:head>
        <h:body>
          <select1 ref="/data/tree">
            <label ref="jr:itext('q_tree:label')"/>
            <item>
              <label>Oak</label>
              <value>oak</value>
            </item>
            <item>
              <label>Pine</label>
              <value>pine</value>
            </item>
          </select1>
        </h:body>
      </h:html>
      """
        .trimIndent()

    val formDef = XFormsXmlSerializer.deserializeFormDef(xml)
    val catalog = formDef.model?.translations
    assertNotNull(catalog)
    assertEquals(2, catalog.languages.size)

    val en = catalog.languages.first { it.language == "English" }
    assertTrue(en.is_default)
    val enStr = en.strings["q_tree:label"]
    assertNotNull(enStr)
    assertEquals("Select tree species", enStr.value_)
    assertEquals("Species", enStr.short_value)
    assertEquals("Check the bark and leaves carefully", enStr.guidance_value)
    assertEquals("jr://images/tree_guide.png", enStr.media?.image_uri)
    assertEquals("jr://audio/tree_prompt.mp3", enStr.media?.audio_uri)

    val serializedXml = XFormsXmlSerializer.serialize(formDef)
    val deserialized = XFormsXmlSerializer.deserializeFormDef(serializedXml)
    assertEquals(formDef, deserialized)
  }

  @Test
  fun testEntitiesSubmissionAndLifecycleActions() {
    val xml =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml" xmlns:jr="http://openrosa.org/javarosa" xmlns:odk="http://www.opendatakit.org/xforms" xmlns:entities="http://www.opendatakit.org/xforms/entities">
        <h:head>
          <h:title>Entity &amp; Action Form</h:title>
          <model odk:xforms-version="1.0.0" entities:entities-version="2024.1.0">
            <instance>
              <data id="entity_form" version="1">
                <tree_diameter/>
                <location/>
                <meta>
                  <instanceID/>
                  <entity dataset="trees" id="/data/meta/entity/@id" create="1" update="/data/tree_diameter &gt; 0" baseVersion="/data/base_ver" trunkVersion="/data/trunk_ver" branchId="/data/branch_id">
                    <label>concat('Tree ', /data/tree_diameter)</label>
                  </entity>
                </meta>
              </data>
            </instance>
            <bind nodeset="/data/tree_diameter" type="decimal" entities:saveto="diameter_cm"/>
            <bind nodeset="/data/location" type="geopoint"/>
            <submission action="https://central.example.org/v1/submission" method="post" base64RsaPublicKey="MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A" auto-send="true" auto-delete="false"/>
            <setvalue event="odk-instance-first-load" ref="/data/tree_diameter" value="10.5"/>
            <odk:setgeopoint event="xforms-value-changed" ref="/data/location"/>
          </model>
        </h:head>
        <h:body>
          <input ref="/data/tree_diameter">
            <label>Diameter (cm)</label>
          </input>
        </h:body>
      </h:html>
      """
        .trimIndent()

    val formDef = XFormsXmlSerializer.deserializeFormDef(xml)
    val model = formDef.model
    assertNotNull(model)

    // Check entity declaration
    assertEquals(1, model.entities.size)
    val entity = model.entities.first()
    assertEquals("trees", entity.dataset)
    assertEquals("1", entity.create_condition)
    assertEquals("/data/tree_diameter > 0", entity.update_condition)
    assertEquals("concat('Tree ', /data/tree_diameter)", entity.label_expression)
    assertEquals("/data/base_ver", entity.sync_metadata?.base_version_expression)

    // Check entities:saveto binding
    val diamBinding = model.bindings.first { it.field_path == "tree_diameter" }
    assertEquals("diameter_cm", diamBinding.entity_saveto)

    // Check submission config
    val submission = model.submission
    assertNotNull(submission)
    assertEquals("https://central.example.org/v1/submission", submission.action_url)
    assertEquals("post", submission.method)
    assertEquals("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A", submission.base64_rsa_public_key)
    assertTrue(submission.auto_send)

    // Check actions
    assertEquals(2, model.actions.size)
    val setVal = model.actions.first { it.type == ActionType.ACTION_SET_VALUE }
    assertEquals(EventType.EVENT_INSTANCE_FIRST_LOAD, setVal.events.first())
    assertEquals("tree_diameter", setVal.target_field)
    assertEquals("10.5", setVal.value_expression)

    val setGeo = model.actions.first { it.type == ActionType.ACTION_SET_GEOPOINT }
    assertEquals(EventType.EVENT_VALUE_CHANGED, setGeo.events.first())
    assertEquals("location", setGeo.target_field)

    val serializedXml = XFormsXmlSerializer.serialize(formDef)
    val deserialized = XFormsXmlSerializer.deserializeFormDef(serializedXml)
    assertEquals(formDef, deserialized)
  }

  @Test
  fun testDynamicOutputFragmentsInLabel() {
    val xml =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml">
        <h:head>
          <h:title>Output Fragments</h:title>
          <model>
            <instance>
              <data id="output_form">
                <name/>
                <score/>
              </data>
            </instance>
            <bind nodeset="/data/name" type="string"/>
            <bind nodeset="/data/score" type="int"/>
          </model>
        </h:head>
        <h:body>
          <input ref="/data/score">
            <label>Hello <output value="/data/name"/>, your current score is <output value="/data/score"/> points!</label>
          </input>
        </h:body>
      </h:html>
      """
        .trimIndent()

    val formDef = XFormsXmlSerializer.deserializeFormDef(xml)
    val control = formDef.view?.components?.firstOrNull()?.control
    assertNotNull(control)
    assertEquals(ControlType.CONTROL_INPUT, control.type)

    val label = control.label
    assertNotNull(label)
    assertEquals("Hello {0}, your current score is {1} points!", label.text)
    assertEquals(2, label.outputs.size)
    assertEquals("{0}", label.outputs[0].placeholder_id)
    assertEquals("/data/name", label.outputs[0].value_expression)
    assertEquals("{1}", label.outputs[1].placeholder_id)
    assertEquals("/data/score", label.outputs[1].value_expression)

    val serializedXml = XFormsXmlSerializer.serialize(formDef)
    val deserialized = XFormsXmlSerializer.deserializeFormDef(serializedXml)
    assertEquals(formDef, deserialized)
  }
}
