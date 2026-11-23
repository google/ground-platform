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
package org.openforis.collectone.v2.core.forms.serialization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import openforis.collectone.v1.forms.ActionType
import openforis.collectone.v1.forms.ControlType
import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.EventType
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.PreloadType

class XFormsUnitTest {

  @Test
  fun testDeserializeReadsFormHeader() {
    val formDef = XFormsXmlSerializer.deserializeFormDef(bindingsFormXml)

    assertEquals("bindings_test", formDef.form_id)
    assertEquals("20250101", formDef.version)
    assertEquals("Bindings Test", formDef.title)
  }

  @Test
  fun testDeserializeReadsEveryBind() {
    assertEquals(5, bindingsFormBindings().size)
  }

  @Test
  fun testDeserializeReadsUidPreloadOnReadonlyBind() {
    val uidBinding = bindingsFormBindings().first { it.field_path == "meta/instanceID" }

    assertEquals(PreloadType.PRELOAD_UID, uidBinding.preload)
    assertTrue(uidBinding.read_only)
  }

  @Test
  fun testDeserializeReadsTimestampPreloadWithParam() {
    val startBinding = bindingsFormBindings().first { it.field_path == "start_time" }

    assertEquals(DataType.TYPE_DATETIME, startBinding.type)
    assertEquals(PreloadType.PRELOAD_TIMESTAMP, startBinding.preload)
    assertEquals("start", startBinding.preload_param)
  }

  @Test
  fun testDeserializeReadsValidationBindWithMessages() {
    val ageBinding = bindingsFormBindings().first { it.field_path == "age" }

    assertEquals(DataType.TYPE_INT32, ageBinding.type)
    assertEquals("true()", ageBinding.required_expression)
    assertEquals("Age is required", ageBinding.required_message)
    assertEquals(". >= 18", ageBinding.constraint_expression)
    assertEquals("Must be adult", ageBinding.constraint_message)
  }

  @Test
  fun testDeserializeReadsRelevantAndCalculateExpressions() {
    val calcBinding = bindingsFormBindings().first { it.field_path == "full_name" }

    assertEquals("/data/age > 20", calcBinding.relevant_expression)
    assertEquals("concat('User-', /data/age)", calcBinding.calculate_expression)
  }

  @Test
  fun testDeserializeReadsInstanceDefaultValues() {
    val formDef = XFormsXmlSerializer.deserializeFormDef(bindingsFormXml)

    val defaults = assertNotNull(formDef.model?.primary_instance?.default_values)
    assertEquals(25, defaults.fields["age"]?.scalar_value?.int32_value)
  }

  @Test
  fun testBindingsFormRoundTrips() {
    assertXmlRoundTrips(XFormsXmlSerializer.deserializeFormDef(bindingsFormXml))
  }

  private fun bindingsFormBindings() =
    XFormsXmlSerializer.deserializeFormDef(bindingsFormXml).model?.bindings.orEmpty()

  private val bindingsFormXml =
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

  @Test
  fun testDeserializeReadsEveryTranslation() {
    assertEquals(2, multiLanguageCatalog().languages.size)
  }

  @Test
  fun testDeserializeMarksDefaultTranslation() {
    assertTrue(multiLanguageCatalog().languages.first { it.language == "English" }.is_default)
  }

  @Test
  fun testDeserializeReadsItextTextForms() {
    val enStr = englishTreeLabel()

    assertEquals("Select tree species", enStr.value_)
    assertEquals("Species", enStr.short_value)
    assertEquals("Check the bark and leaves carefully", enStr.guidance_value)
  }

  @Test
  fun testDeserializeReadsItextMediaForms() {
    val enStr = englishTreeLabel()

    assertEquals("jr://images/tree_guide.png", enStr.media?.image_uri)
    assertEquals("jr://audio/tree_prompt.mp3", enStr.media?.audio_uri)
  }

  @Test
  fun testMultiLanguageFormRoundTrips() {
    assertXmlRoundTrips(XFormsXmlSerializer.deserializeFormDef(multiLanguageFormXml))
  }

  private fun multiLanguageCatalog() =
    assertNotNull(XFormsXmlSerializer.deserializeFormDef(multiLanguageFormXml).model?.translations)

  private fun englishTreeLabel() =
    assertNotNull(
      multiLanguageCatalog().languages.first { it.language == "English" }.strings["q_tree:label"]
    )

  private val multiLanguageFormXml =
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

  @Test
  fun testDeserializeReadsEntityDeclaration() {
    val entity = entityActionModel().entities.single()

    assertEquals("trees", entity.dataset)
    assertEquals("1", entity.create_condition)
    assertEquals("/data/tree_diameter > 0", entity.update_condition)
    assertEquals("concat('Tree ', /data/tree_diameter)", entity.label_expression)
    assertEquals("/data/base_ver", entity.sync_metadata?.base_version_expression)
  }

  @Test
  fun testDeserializeReadsEntitySaveToBind() {
    val diamBinding = entityActionModel().bindings.first { it.field_path == "tree_diameter" }

    assertEquals("diameter_cm", diamBinding.entity_saveto)
  }

  @Test
  fun testDeserializeReadsSubmissionConfig() {
    val submission = assertNotNull(entityActionModel().submission)

    assertEquals("https://central.example.org/v1/submission", submission.action_url)
    assertEquals("post", submission.method)
    assertEquals("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A", submission.base64_rsa_public_key)
    assertTrue(submission.auto_send)
  }

  @Test
  fun testDeserializeReadsEveryModelAction() {
    assertEquals(2, entityActionModel().actions.size)
  }

  @Test
  fun testDeserializeReadsFirstLoadSetValueAction() {
    val setVal = entityActionModel().actions.first { it.type == ActionType.ACTION_SET_VALUE }

    assertEquals(EventType.EVENT_INSTANCE_FIRST_LOAD, setVal.events.first())
    assertEquals("tree_diameter", setVal.target_field)
    assertEquals("10.5", setVal.value_expression)
  }

  @Test
  fun testDeserializeReadsValueChangedSetGeopointAction() {
    val setGeo = entityActionModel().actions.first { it.type == ActionType.ACTION_SET_GEOPOINT }

    assertEquals(EventType.EVENT_VALUE_CHANGED, setGeo.events.first())
    assertEquals("location", setGeo.target_field)
  }

  @Test
  fun testEntityActionFormRoundTrips() {
    assertXmlRoundTrips(XFormsXmlSerializer.deserializeFormDef(entityActionFormXml))
  }

  private fun entityActionModel() =
    assertNotNull(XFormsXmlSerializer.deserializeFormDef(entityActionFormXml).model)

  private val entityActionFormXml =
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

  @Test
  fun testDeserializeReadsInputControl() {
    assertEquals(ControlType.CONTROL_INPUT, outputFragmentsControl().type)
  }

  @Test
  fun testDeserializeReplacesLabelOutputsWithPlaceholders() {
    val label = assertNotNull(outputFragmentsControl().label)

    assertEquals("Hello {0}, your current score is {1} points!", label.text)
    assertEquals(2, label.outputs.size)
    assertEquals("{0}", label.outputs[0].placeholder_id)
    assertEquals("/data/name", label.outputs[0].value_expression)
    assertEquals("{1}", label.outputs[1].placeholder_id)
    assertEquals("/data/score", label.outputs[1].value_expression)
  }

  @Test
  fun testOutputFragmentsFormRoundTrips() {
    assertXmlRoundTrips(XFormsXmlSerializer.deserializeFormDef(outputFragmentsFormXml))
  }

  private fun outputFragmentsControl() =
    assertNotNull(
      XFormsXmlSerializer.deserializeFormDef(outputFragmentsFormXml)
        .view
        ?.components
        ?.firstOrNull()
        ?.control
    )

  private val outputFragmentsFormXml =
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

  @Test
  fun testDeserializeReadsSmsSettings() {
    val model = assertNotNull(sampleParityForm.model)

    assertEquals("par", model.primary_instance?.record_schema?.sms_prefix)
    assertEquals("#", model.primary_instance?.record_schema?.sms_delimiter)
    assertEquals("lck", model.bindings.first { it.field_path == "locked" }.sms_tag)
  }

  @Test
  fun testDeserializeReadsDynamicReadonlyExpression() {
    val stateBind =
      assertNotNull(sampleParityForm.model).bindings.first { it.field_path == "state" }

    assertFalse(stateBind.read_only)
    assertEquals("/data/locked = 'yes'", stateBind.read_only_expression)
  }

  @Test
  fun testDeserializeReadsCustomItemsetPath() {
    val countyControl =
      assertNotNull(
        sampleParityForm.view
          ?.components
          ?.mapNotNull { it.control }
          ?.first {
            it.field_ref == "county"
          }
      )

    assertEquals("counties", countyControl.itemset?.instance_id)
    assertEquals("counties/county", countyControl.itemset?.nodeset_path)
    assertEquals("state = /data/state", countyControl.itemset?.nodeset_filter)
  }

  @Test
  fun testDeserializeReadsRepeatWithCountAndBoundControls() {
    val repeat = sampleParityRepeat()

    assertEquals("rep", repeat.field_ref)
    assertEquals("current()/../count", repeat.count_expression)
    assertEquals("item_name", repeat.components.first().control?.field_ref)
  }

  @Test
  fun testDeserializeReadsRepeatInsertAction() {
    val repeat = sampleParityRepeat()

    assertEquals(1, repeat.actions.size)
    assertEquals("rep/seq", repeat.actions.first().target_field)
  }

  @Test
  fun testSampleParityFormRoundTripsThroughXml() {
    assertXmlRoundTrips(sampleParityForm)
  }

  @Test
  fun testSampleParityFormRoundTripsThroughProtoJson() {
    assertEquals(
      sampleParityForm,
      ProtoJsonSerializer.deserializeFormDef(
        ProtoJsonSerializer.serializeFormDef(sampleParityForm)
      ),
    )
  }

  @Test
  fun testSampleParityFormRoundTripsThroughTextProto() {
    assertEquals(
      sampleParityForm,
      TextProtoSerializer.deserializeFormDef(
        TextProtoSerializer.serializeFormDef(sampleParityForm)
      ),
    )
  }

  private val sampleParityForm
    get() = XFormsXmlSerializer.deserializeFormDef(sampleParityFormXml)

  private fun sampleParityRepeat() =
    assertNotNull(sampleParityForm.view?.components?.mapNotNull { it.repeat }?.first())

  private val sampleParityFormXml =
    """
    <?xml version="1.0" encoding="UTF-8"?>
    <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml" xmlns:jr="http://openrosa.org/javarosa" xmlns:odk="http://www.opendatakit.org/xforms">
      <h:head>
        <h:title>Sample Forms Parity</h:title>
        <model odk:xforms-version="1.0.0">
          <instance>
            <data id="sample_parity" version="1" odk:prefix="par" odk:delimiter="#">
              <locked/>
              <state/>
              <county/>
              <count>2</count>
              <rep jr:template="">
                <seq/>
                <item_name/>
              </rep>
            </data>
          </instance>
          <instance id="counties">
            <counties>
              <county>
                <state>WA</state>
                <name>king</name>
                <label>King County</label>
              </county>
            </counties>
          </instance>
          <bind id="b_locked" nodeset="/data/locked" type="string" odk:tag="lck"/>
          <bind id="b_state" nodeset="/data/state" type="string" readonly="/data/locked = 'yes'"/>
          <bind id="b_county" nodeset="/data/county" type="string"/>
          <bind id="b_count" nodeset="/data/count" type="int"/>
          <bind id="b_seq" nodeset="/data/rep/seq" type="int"/>
          <bind id="b_item_name" nodeset="/data/rep/item_name" type="string"/>
        </model>
      </h:head>
      <h:body>
        <input bind="b_locked">
          <label>Locked?</label>
        </input>
        <input bind="b_state">
          <label>State</label>
        </input>
        <select1 bind="b_county">
          <label>County</label>
          <itemset nodeset="instance('counties')/counties/county[state = /data/state]">
            <value ref="name"/>
            <label ref="label"/>
          </itemset>
        </select1>
        <repeat nodeset="/data/rep" jr:count="current()/../count">
          <setvalue event="odk-new-repeat" bind="b_seq" value="position(..)"/>
          <input ref="current()/item_name">
            <label>Item Name</label>
          </input>
        </repeat>
      </h:body>
    </h:html>
    """
      .trimIndent()

  private fun assertXmlRoundTrips(formDef: FormDef) {
    val serializedXml = XFormsXmlSerializer.serialize(formDef)

    assertEquals(formDef, XFormsXmlSerializer.deserializeFormDef(serializedXml))
  }
}
