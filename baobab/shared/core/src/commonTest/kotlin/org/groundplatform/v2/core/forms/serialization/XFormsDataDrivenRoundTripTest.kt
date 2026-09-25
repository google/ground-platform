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

import groundplatform.v2.forms.FormDef
import groundplatform.v2.forms.RecordInstance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Data-driven round-trip tests verifying that simple and complex XForms XML documents can be
 * deserialized into ProtoForms models, serialized back into canonical XML, and re-deserialized
 * without any loss of semantic structure or metadata.
 */
class XFormsDataDrivenRoundTripTest {

  private data class FormRoundTripCase(
    val name: String,
    val inputXml: String,
    val verify: (FormDef) -> Unit,
  )

  private data class RecordRoundTripCase(
    val name: String,
    val formXml: String?,
    val submissionXml: String,
    val verify: (RecordInstance) -> Unit,
  )

  @Test
  fun testAllFormDefRoundTripCases() {
    for (testCase in FORM_ROUND_TRIP_CASES) {
      // Step 1: Deserialize original input XML into FormDef_1
      val formDef1 = XFormsXmlSerializer.deserializeFormDef(testCase.inputXml)
      testCase.verify(formDef1)

      // Step 2: Serialize FormDef_1 into canonical XML_1
      val xml1 = XFormsXmlSerializer.serialize(formDef1, prettyPrint = true)
      assertTrue(xml1.isNotBlank(), "Serialized XML should not be blank for case: ${testCase.name}")

      // Step 3: Deserialize XML_1 back into FormDef_2
      val formDef2 = XFormsXmlSerializer.deserializeFormDef(xml1)

      // Step 4: Verify exact protobuf structural and value equality
      assertEquals(
        expected = formDef1,
        actual = formDef2,
        message = "FormDef round-trip mismatch in test case: ${testCase.name}",
      )

      // Step 5: Serialize FormDef_2 into XML_2 and assert idempotence (XML_1 == XML_2)
      val xml2 = XFormsXmlSerializer.serialize(formDef2, prettyPrint = true)
      assertEquals(
        expected = xml1,
        actual = xml2,
        message = "Canonical XML serialization idempotence mismatch in test case: ${testCase.name}",
      )
    }
  }

  @Test
  fun testAllRecordInstanceRoundTripCases() {
    for (testCase in RECORD_ROUND_TRIP_CASES) {
      val formDef = testCase.formXml?.let { XFormsXmlSerializer.deserializeFormDef(it) }

      // Step 1: Deserialize submission XML into RecordInstance_1
      val record1 =
        XFormsXmlSerializer.deserializeRecordInstance(
          xml = testCase.submissionXml,
          formDef = formDef,
        )
      testCase.verify(record1)

      // Step 2: Serialize RecordInstance_1 into canonical XML_1
      val rootName =
        formDef?.model?.primary_instance?.record_schema?.name?.ifEmpty { "data" } ?: "data"
      val xml1 =
        XFormsXmlSerializer.serialize(
          record = record1,
          rootElementName = rootName,
          prettyPrint = true,
        )

      // Step 3: Deserialize XML_1 back into RecordInstance_2
      val record2 = XFormsXmlSerializer.deserializeRecordInstance(xml = xml1, formDef = formDef)

      // Step 4: Verify exact RecordInstance equality
      assertEquals(
        expected = record1,
        actual = record2,
        message = "RecordInstance round-trip mismatch in test case: ${testCase.name}",
      )

      // Step 5: Assert canonical XML idempotence (XML_1 == XML_2)
      val xml2 =
        XFormsXmlSerializer.serialize(
          record = record2,
          rootElementName = rootName,
          prettyPrint = true,
        )
      assertEquals(
        expected = xml1,
        actual = xml2,
        message = "RecordInstance canonical XML idempotence mismatch in test case: ${testCase.name}",
      )
    }
  }

  companion object {
    private val FORM_ROUND_TRIP_CASES =
      listOf(
        FormRoundTripCase(
          name = "1. Simple Household Registration Form",
          inputXml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml" xmlns:jr="http://openrosa.org/javarosa">
              <h:head>
                <h:title>Household Registration</h:title>
                <model>
                  <instance>
                    <data id="hh_registration" version="1.0">
                      <meta>
                        <instanceID/>
                      </meta>
                      <respondent_name/>
                      <household_members>4</household_members>
                      <registration_date/>
                    </data>
                  </instance>
                  <bind nodeset="/data/meta/instanceID" type="string" readonly="true()" jr:preload="uid"/>
                  <bind nodeset="/data/respondent_name" type="string" required="true()" jr:requiredMsg="Respondent name is mandatory"/>
                  <bind nodeset="/data/household_members" type="int" constraint=". &gt;= 1 and . &lt;= 30" jr:constraintMsg="Members must be between 1 and 30"/>
                  <bind nodeset="/data/registration_date" type="date" jr:preload="date" jr:preloadParams="today"/>
                </model>
              </h:head>
              <h:body>
                <input ref="/data/respondent_name">
                  <label>Respondent Full Name</label>
                  <hint>Enter first and last name</hint>
                </input>
                <input ref="/data/household_members">
                  <label>Number of Household Members</label>
                </input>
                <input ref="/data/registration_date">
                  <label>Date of Registration</label>
                </input>
              </h:body>
            </h:html>
            """
              .trimIndent(),
          verify = { form ->
            assertEquals("hh_registration", form.form_id)
            assertEquals("1.0", form.version)
            assertEquals("Household Registration", form.title)
            assertEquals(4, form.model?.bindings?.size)
            assertEquals(3, form.view?.components?.size)
            assertEquals(
              4,
              form.model
                ?.primary_instance
                ?.default_values
                ?.fields
                ?.get("household_members")
                ?.scalar_value
                ?.int32_value,
            )
          },
        ),
        FormRoundTripCase(
          name = "2. Multi-Lingual Health & Nutrition Survey Form",
          inputXml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml" xmlns:jr="http://openrosa.org/javarosa">
              <h:head>
                <h:title>Child Health Survey</h:title>
                <model>
                  <itext>
                    <translation lang="English" default="true()">
                      <text id="child_name:label">
                        <value>Child's Given Name</value>
                        <value form="short">Name</value>
                      </text>
                      <text id="symptoms:label">
                        <value>Observed Symptoms</value>
                        <value form="guidance">Select all symptoms reported in the past 7 days</value>
                        <value form="image">jr://images/symptom_chart.png</value>
                      </text>
                    </translation>
                    <translation lang="French">
                      <text id="child_name:label">
                        <value>Prénom de l'enfant</value>
                        <value form="short">Prénom</value>
                      </text>
                      <text id="symptoms:label">
                        <value>Symptômes observés</value>
                      </text>
                    </translation>
                  </itext>
                  <instance>
                    <data id="child_health_v2" version="2.1">
                      <meta>
                        <instanceID/>
                      </meta>
                      <child_name/>
                      <symptoms/>
                      <summary_note/>
                    </data>
                  </instance>
                  <bind nodeset="/data/meta/instanceID" type="string" readonly="true()" jr:preload="uid"/>
                  <bind nodeset="/data/child_name" type="string" required="true()"/>
                  <bind nodeset="/data/symptoms" type="select"/>
                  <bind nodeset="/data/summary_note" type="string" readonly="true()"/>
                </model>
              </h:head>
              <h:body>
                <input ref="/data/child_name">
                  <label ref="jr:itext('child_name:label')"/>
                </input>
                <select ref="/data/symptoms">
                  <label ref="jr:itext('symptoms:label')"/>
                  <item>
                    <label>Fever</label>
                    <value>fever</value>
                  </item>
                  <item>
                    <label>Cough</label>
                    <value>cough</value>
                  </item>
                  <item>
                    <label>Fatigue</label>
                    <value>fatigue</value>
                  </item>
                </select>
                <input ref="/data/summary_note">
                  <label>Health summary for <output value="/data/child_name"/> recorded.</label>
                </input>
              </h:body>
            </h:html>
            """
              .trimIndent(),
          verify = { form ->
            assertEquals("child_health_v2", form.form_id)
            assertEquals(2, form.model?.translations?.languages?.size)
            val summaryControl = form.view?.components?.get(2)?.control
            assertNotNull(summaryControl)
            assertEquals(1, summaryControl.label?.outputs?.size)
            assertEquals(
              "/data/child_name",
              summaryControl.label?.outputs?.get(0)?.value_expression,
            )
          },
        ),
        FormRoundTripCase(
          name = "3. Cascading Choice & Dynamic Itemset Form with Randomization & Rank",
          inputXml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml" xmlns:jr="http://openrosa.org/javarosa" xmlns:odk="http://www.opendatakit.org/xforms">
              <h:head>
                <h:title>Agricultural Crop Survey</h:title>
                <model>
                  <instance>
                    <data id="crop_survey" version="3.0">
                      <country/>
                      <district/>
                      <crop_priority/>
                    </data>
                  </instance>
                  <instance id="districts" src="jr://file-csv/districts.csv"/>
                  <instance id="crops" src="jr://file-csv/crops.csv"/>
                  <bind nodeset="/data/country" type="select1" required="true()"/>
                  <bind nodeset="/data/district" type="select1" relevant="/data/country != ''"/>
                  <bind nodeset="/data/crop_priority" type="rank"/>
                </model>
              </h:head>
              <h:body>
                <select1 ref="/data/country">
                  <label>Select Country</label>
                  <item>
                    <label>Kenya</label>
                    <value>KE</value>
                  </item>
                  <item>
                    <label>Tanzania</label>
                    <value>TZ</value>
                  </item>
                </select1>
                <select1 ref="/data/district">
                  <label>Select District</label>
                  <itemset nodeset="randomize(instance('districts')/root/item[country=/data/country], 98765)">
                    <value ref="name"/>
                    <label ref="label"/>
                  </itemset>
                </select1>
                <odk:rank ref="/data/crop_priority">
                  <label>Rank Crops by Yield Importance</label>
                  <itemset nodeset="instance('crops')/root/item">
                    <value ref="crop_code"/>
                    <label ref="crop_title"/>
                  </itemset>
                </odk:rank>
              </h:body>
            </h:html>
            """
              .trimIndent(),
          verify = { form ->
            assertEquals(2, form.model?.secondary_instances?.size)
            val districtControl = form.view?.components?.get(1)?.control
            val itemset = districtControl?.itemset
            assertNotNull(itemset)
            assertTrue(itemset.randomize)
            assertEquals("98765", itemset.random_seed_expression)
            assertEquals("districts", itemset.instance_id)
          },
        ),
        FormRoundTripCase(
          name = "4. Field Plot Monitoring Form with Repeat Groups, GeoConfig, Range & Upload",
          inputXml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml" xmlns:jr="http://openrosa.org/javarosa" xmlns:odk="http://www.opendatakit.org/xforms">
              <h:head>
                <h:title>Forest Biodiversity Plot Monitoring</h:title>
                <model>
                  <instance>
                    <data id="bio_monitoring" version="4.2">
                      <meta>
                        <instanceID/>
                      </meta>
                      <plot_center/>
                      <canopy_cover/>
                      <plot_photo/>
                      <plot_count>2</plot_count>
                      <subplot_group>
                        <subplot_code/>
                        <tree_repeat jr:template="">
                          <species_name/>
                          <height_m/>
                        </tree_repeat>
                      </subplot_group>
                    </data>
                  </instance>
                  <bind nodeset="/data/meta/instanceID" type="string" readonly="true()" jr:preload="uid"/>
                  <bind nodeset="/data/plot_center" type="geopoint" required="true()"/>
                  <bind nodeset="/data/canopy_cover" type="decimal"/>
                  <bind nodeset="/data/plot_photo" type="binary"/>
                  <bind nodeset="/data/plot_count" type="int"/>
                  <bind nodeset="/data/subplot_group/subplot_code" type="string"/>
                  <bind nodeset="/data/subplot_group/tree_repeat/species_name" type="string"/>
                  <bind nodeset="/data/subplot_group/tree_repeat/height_m" type="decimal"/>
                </model>
              </h:head>
              <h:body>
                <input ref="/data/plot_center" accuracyThreshold="5.0" unacceptableAccuracyThreshold="15.0">
                  <label>Plot Center GPS</label>
                </input>
                <range ref="/data/canopy_cover" start="0" end="100" step="5" odk:tick-interval="10">
                  <label>Canopy Cover Percentage</label>
                </range>
                <upload ref="/data/plot_photo" mediatype="image/*" odk:max-pixels="2048">
                  <label>Hemispherical Canopy Photo</label>
                </upload>
                <group ref="/data/subplot_group" appearance="field-list" intent="org.example.BARCODE_SCAN(code=/data/subplot_group/subplot_code)">
                  <label>Subplot Details</label>
                  <input ref="/data/subplot_group/subplot_code">
                    <label>Subplot Barcode</label>
                  </input>
                  <group ref="/data/subplot_group/tree_repeat">
                    <label>Tree Measurements</label>
                    <repeat nodeset="/data/subplot_group/tree_repeat" jr:count="/data/plot_count" jr:noAddRemove="true()">
                      <input ref="/data/subplot_group/tree_repeat/species_name">
                        <label>Species Scientific Name</label>
                      </input>
                      <input ref="/data/subplot_group/tree_repeat/height_m">
                        <label>Tree Height (m)</label>
                      </input>
                    </repeat>
                  </group>
                </group>
              </h:body>
            </h:html>
            """
              .trimIndent(),
          verify = { form ->
            val geoCtrl = form.view?.components?.get(0)?.control
            assertEquals(5.0, geoCtrl?.geo_config?.accuracy_threshold_meters)
            assertEquals(15.0, geoCtrl?.geo_config?.warning_threshold_meters)

            val rangeCtrl = form.view?.components?.get(1)?.control
            assertEquals(0.0, rangeCtrl?.range_config?.start)
            assertEquals(100.0, rangeCtrl?.range_config?.end)
            assertEquals(5.0, rangeCtrl?.range_config?.step)
            assertEquals(10.0, rangeCtrl?.range_config?.tick_interval)

            val groupComp = form.view?.components?.get(3)?.group
            assertNotNull(groupComp)
            assertEquals("field-list", groupComp.appearance)
            val intent = groupComp.intent
            assertNotNull(intent)
            assertEquals("org.example.BARCODE_SCAN", intent.intent_uri)

            val nestedRepeat = groupComp.components.get(1).repeat
            assertNotNull(nestedRepeat)
            assertEquals("/data/plot_count", nestedRepeat.count_expression)
            assertTrue(nestedRepeat.no_add_remove)
          },
        ),
        FormRoundTripCase(
          name = "5. Encrypted Longitudinal Entity Tracking Form with Actions",
          inputXml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml" xmlns:jr="http://openrosa.org/javarosa" xmlns:odk="http://www.opendatakit.org/xforms" xmlns:entities="http://www.opendatakit.org/xforms/entities">
              <h:head>
                <h:title>Longitudinal Tree Registry</h:title>
                <model odk:xforms-version="1.0.0" entities:entities-version="2024.1.0">
                  <instance>
                    <data id="tree_registry" version="20250501">
                      <tree_tag/>
                      <dbh_cm/>
                      <gps_point/>
                      <meta>
                        <instanceID/>
                        <entity dataset="monitored_trees" id="/data/meta/entity/@id" create="1" update="/data/dbh_cm &gt; 10" baseVersion="/data/base_v" trunkVersion="/data/trunk_v" branchId="/data/branch_id">
                          <label>concat('Tag #', /data/tree_tag)</label>
                        </entity>
                      </meta>
                    </data>
                  </instance>
                  <bind nodeset="/data/meta/instanceID" type="string" readonly="true()" jr:preload="uid"/>
                  <bind nodeset="/data/tree_tag" type="string" required="true()" entities:saveto="tag_number"/>
                  <bind nodeset="/data/dbh_cm" type="decimal" entities:saveto="latest_dbh"/>
                  <bind nodeset="/data/gps_point" type="geopoint" entities:saveto="geometry"/>
                  <submission action="https://odk.groundplatform.org/v1/projects/1/submission" method="post" base64RsaPublicKey="MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA123456" auto-send="true" auto-delete="true"/>
                  <setvalue event="odk-instance-first-load" ref="/data/dbh_cm" value="15.0"/>
                  <odk:setgeopoint event="xforms-value-changed" ref="/data/gps_point"/>
                </model>
              </h:head>
              <h:body>
                <input ref="/data/tree_tag">
                  <label>Tree Tag Code</label>
                </input>
                <input ref="/data/dbh_cm">
                  <label>Diameter at Breast Height (cm)</label>
                </input>
                <input ref="/data/gps_point">
                  <label>Tree GPS Location</label>
                </input>
              </h:body>
            </h:html>
            """
              .trimIndent(),
          verify = { form ->
            assertEquals(1, form.model?.entities?.size)
            assertEquals("monitored_trees", form.model?.entities?.first()?.dataset)
            assertEquals(
              "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA123456",
              form.model?.submission?.base64_rsa_public_key,
            )
            assertEquals(2, form.model?.actions?.size)
          },
        ),
        FormRoundTripCase(
          name = "6. Compact SMS Survey Form",
          inputXml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml" xmlns:jr="http://openrosa.org/javarosa">
              <h:head>
                <h:title>Compact SMS Disease Report</h:title>
                <model>
                  <instance>
                    <data id="sms_report" version="1.0" jr:prefix="dr" jr:delimiter="+">
                      <clinic_code/>
                      <cases_count/>
                    </data>
                  </instance>
                  <bind nodeset="/data/clinic_code" type="string" required="true()" jr:tag="cc"/>
                  <bind nodeset="/data/cases_count" type="int" required="true()" jr:tag="cnt"/>
                </model>
              </h:head>
              <h:body>
                <input ref="/data/clinic_code">
                  <label>Clinic Identifier</label>
                </input>
                <input ref="/data/cases_count">
                  <label>Confirmed Cases Count</label>
                </input>
              </h:body>
            </h:html>
            """
              .trimIndent(),
          verify = { form ->
            val schema = form.model?.primary_instance?.record_schema
            assertEquals("dr", schema?.sms_prefix)
            assertEquals("+", schema?.sms_delimiter)
            assertEquals(
              "cc",
              form.model?.bindings?.first { it.field_path == "clinic_code" }?.sms_tag,
            )
            assertEquals(
              "cnt",
              form.model?.bindings?.first { it.field_path == "cases_count" }?.sms_tag,
            )
          },
        ),
      )

    private val RECORD_ROUND_TRIP_CASES =
      listOf(
        RecordRoundTripCase(
          name = "Record 1: Simple Household Registration Submission",
          formXml = FORM_ROUND_TRIP_CASES[0].inputXml,
          submissionXml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <data id="hh_registration" version="1.0">
              <meta>
                <instanceID>uuid:9a8b7c6d-5e4f-3a2b-1c0d-9e8f7a6b5c4d</instanceID>
                <timeStart>2025-03-10T09:00:00.000Z</timeStart>
                <timeEnd>2025-03-10T09:12:45.000Z</timeEnd>
                <deviceID>pixel-8-pro-01</deviceID>
              </meta>
              <respondent_name>Carlos Mendoza</respondent_name>
              <household_members>6</household_members>
              <registration_date>2025-03-10</registration_date>
            </data>
            """
              .trimIndent(),
          verify = { record ->
            assertEquals("hh_registration", record.form_id)
            assertEquals("uuid:9a8b7c6d-5e4f-3a2b-1c0d-9e8f7a6b5c4d", record.metadata?.instance_id)
            assertEquals(
              "Carlos Mendoza",
              record.data_?.fields?.get("respondent_name")?.scalar_value?.string_value,
            )
            assertEquals(
              6,
              record.data_?.fields?.get("household_members")?.scalar_value?.int32_value,
            )
          },
        ),
        RecordRoundTripCase(
          name = "Record 2: Multi-Select & Nested Group Submission",
          formXml = FORM_ROUND_TRIP_CASES[1].inputXml,
          submissionXml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <data id="child_health_v2" version="2.1">
              <meta>
                <instanceID>uuid:health-sub-002</instanceID>
              </meta>
              <child_name>Amina Diop</child_name>
              <symptoms>fever cough fatigue</symptoms>
              <summary_note>Health summary for Amina Diop recorded.</summary_note>
            </data>
            """
              .trimIndent(),
          verify = { record ->
            val symptoms = record.data_?.fields?.get("symptoms")?.list_value?.values
            assertNotNull(symptoms)
            assertEquals(3, symptoms.size)
            assertEquals("fever", symptoms[0].string_value)
            assertEquals("cough", symptoms[1].string_value)
            assertEquals("fatigue", symptoms[2].string_value)
          },
        ),
        RecordRoundTripCase(
          name =
            "Record 3: Complex Biodiversity Plot Submission with Nested Repeat Groups & Geospatial",
          formXml = FORM_ROUND_TRIP_CASES[3].inputXml,
          submissionXml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <data id="bio_monitoring" version="4.2">
              <meta>
                <instanceID>uuid:bio-plot-777</instanceID>
                <timeStart>2025-04-01T11:20:00.000Z</timeStart>
                <timeEnd>2025-04-01T12:05:30.000Z</timeEnd>
                <deviceID>trimble-nomad-05</deviceID>
                <audit>audit.csv</audit>
              </meta>
              <plot_center>-3.4653 -62.2159 85.5 2.4</plot_center>
              <canopy_cover>75.5</canopy_cover>
              <plot_photo>canopy_001.jpg</plot_photo>
              <plot_count>2</plot_count>
              <subplot_group>
                <subplot_code>SUB-NW-01</subplot_code>
                <tree_repeat>
                  <species_name>Swietenia macrophylla</species_name>
                  <height_m>34.5</height_m>
                </tree_repeat>
                <tree_repeat>
                  <species_name>Ceiba pentandra</species_name>
                  <height_m>48.0</height_m>
                </tree_repeat>
              </subplot_group>
            </data>
            """
              .trimIndent(),
          verify = { record ->
            val rootData = record.data_
            assertNotNull(rootData)
            val plotCenter = rootData.fields["plot_center"]?.scalar_value?.geopoint_value
            assertNotNull(plotCenter)
            assertEquals(-3.4653, plotCenter.latitude)
            assertEquals(-62.2159, plotCenter.longitude)

            val subplot = rootData.fields["subplot_group"]?.node_value
            assertNotNull(subplot)
            assertEquals("SUB-NW-01", subplot.fields["subplot_code"]?.scalar_value?.string_value)

            val trees = subplot.fields["tree_repeat"]?.repeat_value?.nodes
            assertNotNull(trees)
            assertEquals(2, trees.size)
            assertEquals(
              "Swietenia macrophylla",
              trees[0].fields["species_name"]?.scalar_value?.string_value,
            )
            assertEquals(34.5, trees[0].fields["height_m"]?.scalar_value?.double_value)
            assertEquals(
              "Ceiba pentandra",
              trees[1].fields["species_name"]?.scalar_value?.string_value,
            )
            assertEquals(48.0, trees[1].fields["height_m"]?.scalar_value?.double_value)
          },
        ),
        RecordRoundTripCase(
          name = "Record 4: Schema-less Geospatial Trace & Shape Submission",
          formXml = null,
          submissionXml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <data id="geo_survey" version="1.0">
              <meta>
                <instanceID>uuid:geo-trace-shape-009</instanceID>
              </meta>
              <surveyor_name>Dr. Jane Goodall</surveyor_name>
              <river_transect>-3.1 35.1 1200 3.5; -3.2 35.2 1205 4.0</river_transect>
              <park_boundary>-3.1 35.1 1200 3.0; -3.1 35.5 1210 3.0; -3.5 35.5 1220 3.0; -3.1 35.1 1200 3.0</park_boundary>
            </data>
            """
              .trimIndent(),
          verify = { record ->
            val rootData = record.data_
            assertNotNull(rootData)
            val trace = rootData.fields["river_transect"]?.scalar_value?.geotrace_value
            assertNotNull(trace)
            assertEquals(2, trace.points.size)

            val shape = rootData.fields["park_boundary"]?.scalar_value?.geoshape_value
            assertNotNull(shape)
            assertEquals(4, shape.points.size)
          },
        ),
      )
  }
}
