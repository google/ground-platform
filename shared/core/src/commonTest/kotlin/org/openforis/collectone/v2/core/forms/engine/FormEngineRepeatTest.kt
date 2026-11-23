/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.core.forms.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import openforis.collectone.v1.forms.ActionDef
import openforis.collectone.v1.forms.ActionType
import openforis.collectone.v1.forms.ControlDef
import openforis.collectone.v1.forms.ControlType
import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.EventType
import openforis.collectone.v1.forms.FieldBinding
import openforis.collectone.v1.forms.FieldDefinition
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.LabelDef
import openforis.collectone.v1.forms.ModelDef
import openforis.collectone.v1.forms.OutputFragment
import openforis.collectone.v1.forms.PrimaryInstance
import openforis.collectone.v1.forms.RecordSchema
import openforis.collectone.v1.forms.RepeatDef
import openforis.collectone.v1.forms.SecondaryInstance
import openforis.collectone.v1.forms.ViewComponent
import openforis.collectone.v1.forms.ViewDef
import org.openforis.collectone.v2.core.forms.buildRecordNode
import org.openforis.collectone.v2.core.forms.model.ComponentState
import org.openforis.collectone.v2.core.forms.model.FormState
import org.openforis.collectone.v2.core.forms.serialization.XFormsXmlSerializer

class FormEngineRepeatTest {

  @Test
  fun repeatCount_initialValue_createsThatManyInstances() {
    val session = FormSession(householdMembersForm)

    assertEquals(2, session.state.singleRepeatGroup().instances.size)
  }

  @Test
  fun repeatLabel_outputs_interpolateInstancePosition() {
    val session = FormSession(householdMembersForm)

    val instances = session.state.singleRepeatGroup().instances
    assertEquals(listOf("Member #1", "Member #2"), instances.map { it.label?.text })
  }

  @Test
  fun repeatInsertAction_initialInstances_setValuesFromPosition() {
    val session = FormSession(householdMembersForm)

    assertEquals(
      21,
      session.state.findFieldState("/data/person[1]/age")?.value?.scalar_value?.int32_value,
    )
    assertEquals(
      22,
      session.state.findFieldState("/data/person[2]/age")?.value?.scalar_value?.int32_value,
    )
  }

  @Test
  fun repeatCount_increase_addsInstance() {
    val session = FormSession(householdMembersForm)

    session.updateInt("hh_size", 3)

    assertEquals(3, session.state.singleRepeatGroup().instances.size)
  }

  @Test
  fun repeatInsertAction_addedInstance_setsValueFromPosition() {
    val session = FormSession(householdMembersForm)

    session.updateInt("hh_size", 3)

    assertEquals(
      23,
      session.state.findFieldState("/data/person[3]/age")?.value?.scalar_value?.int32_value,
    )
  }

  @Test
  fun calculate_inAddedRepeatInstance_usesItsPosition() {
    val session = FormSession(householdMembersForm)

    session.updateInt("hh_size", 3)

    assertEquals(
      3,
      session.state.findFieldState("/data/person[3]/label_idx")?.value?.scalar_value?.int32_value,
    )
  }

  private fun FormState.singleRepeatGroup(): ComponentState.RepeatGroupState =
    rootComponents.filterIsInstance<ComponentState.RepeatGroupState>().single()

  private val householdMembersForm: FormDef =
    FormDef(
      form_id = "repeat_test",
      model =
        ModelDef(
          primary_instance =
            PrimaryInstance(
              record_schema =
                RecordSchema(
                  name = "data",
                  fields =
                    listOf(
                      FieldDefinition(name = "base_age", type = DataType.TYPE_INT32),
                      FieldDefinition(name = "hh_size", type = DataType.TYPE_INT32),
                      FieldDefinition(
                        name = "person",
                        type = DataType.TYPE_MESSAGE,
                        is_repeated = true,
                        fields =
                          listOf(
                            FieldDefinition(name = "age", type = DataType.TYPE_INT32),
                            FieldDefinition(name = "label_idx", type = DataType.TYPE_INT32),
                          ),
                      ),
                    ),
                ),
              default_values =
                buildRecordNode {
                  int32("base_age", 20)
                  int32("hh_size", 2)
                },
            ),
          bindings =
            listOf(
              FieldBinding(field_path = "base_age", type = DataType.TYPE_INT32),
              FieldBinding(field_path = "hh_size", type = DataType.TYPE_INT32),
              FieldBinding(
                field_path = "person/label_idx",
                type = DataType.TYPE_INT32,
                calculate_expression = "position(..)",
              ),
            ),
        ),
      view =
        ViewDef(
          components =
            listOf(
              ViewComponent(
                repeat =
                  RepeatDef(
                    field_ref = "person",
                    count_expression = "/data/hh_size",
                    label =
                      LabelDef(
                        text = "Member #{0}",
                        outputs =
                          listOf(
                            OutputFragment(
                              placeholder_id = "0",
                              value_expression = "position(..)",
                            )
                          ),
                      ),
                    components =
                      listOf(
                        ViewComponent(
                          control =
                            ControlDef(
                              field_ref = "person/age",
                              type = ControlType.CONTROL_INPUT,
                              actions =
                                listOf(
                                  ActionDef(
                                    events = listOf(EventType.EVENT_REPEAT_INSERT),
                                    type = ActionType.ACTION_SET_VALUE,
                                    target_field = "person/age",
                                    value_expression = "/data/base_age + position(..)",
                                  )
                                ),
                            )
                        )
                      ),
                  )
              )
            )
        ),
    )

  @Test
  fun lastSavedInstance_singleRecord_isQueryable() {
    val session = FormSession(repeatActionsForm, environment = fixedLocationEnvironment())

    assertEquals(
      "CarriedOver",
      session.state.findFieldState("prev_item")?.value?.scalar_value?.string_value,
    )
  }

  @Test
  fun firstLoadSetGeopoint_withLocationProvider_setsValueImmediately() {
    val session = FormSession(repeatActionsForm, environment = fixedLocationEnvironment())

    val gpsVal = session.state.findFieldState("bg_gps")?.value?.scalar_value?.geopoint_value
    assertNotNull(gpsVal)
    assertEquals(37.422, gpsVal.latitude)
    assertEquals(-122.084, gpsVal.longitude)
  }

  @Test
  fun repeatCount_relativeToCurrent_createsThatManyInstances() {
    val session = FormSession(repeatActionsForm, environment = fixedLocationEnvironment())

    val repeatGroup = session.state.rootComponents.first() as ComponentState.RepeatGroupState
    assertEquals(2, repeatGroup.instances.size)
  }

  @Test
  fun repeatLevelInsertAction_runsForEachCreatedInstance() {
    val session = FormSession(repeatActionsForm, environment = fixedLocationEnvironment())

    assertEquals(
      1,
      session.state.findFieldState("/data/rep[1]/seq_num")?.value?.scalar_value?.int32_value,
    )
    assertEquals(
      2,
      session.state.findFieldState("/data/rep[2]/seq_num")?.value?.scalar_value?.int32_value,
    )
  }

  private fun fixedLocationEnvironment() =
    FormEnvironment(
      locationProvider = {
        openforis.collectone.v1.forms.GeoPoint(
          latitude = 37.422,
          longitude = -122.084,
          altitude_meters = 12.0,
          accuracy_meters = 4.5,
        )
      }
    )

  private val repeatActionsForm: FormDef =
    FormDef(
      form_id = "repeat_actions_form",
      model =
        ModelDef(
          primary_instance =
            PrimaryInstance(
              record_schema =
                RecordSchema(
                  name = "data",
                  fields =
                    listOf(
                      FieldDefinition(name = "count", type = DataType.TYPE_INT32),
                      FieldDefinition(name = "prev_item", type = DataType.TYPE_STRING),
                      FieldDefinition(name = "bg_gps", type = DataType.TYPE_GEOPOINT),
                      FieldDefinition(
                        name = "rep",
                        type = DataType.TYPE_MESSAGE,
                        is_repeated = true,
                        fields =
                          listOf(FieldDefinition(name = "seq_num", type = DataType.TYPE_INT32)),
                      ),
                    ),
                ),
              default_values = buildRecordNode { int32("count", 2) },
            ),
          secondary_instances =
            listOf(
              SecondaryInstance(
                id = "last-saved",
                uri = "jr://instance/last-saved",
                root_name = "data",
                rows =
                  listOf(
                    org.openforis.collectone.v2.core.forms.model.secondaryInstanceRow(
                      "item" to "CarriedOver"
                    )
                  ),
              )
            ),
          bindings =
            listOf(
              FieldBinding(field_path = "count", type = DataType.TYPE_INT32),
              FieldBinding(
                field_path = "prev_item",
                type = DataType.TYPE_STRING,
                calculate_expression = "instance('last-saved')/data/item",
              ),
              FieldBinding(field_path = "bg_gps", type = DataType.TYPE_GEOPOINT),
              FieldBinding(field_path = "rep/seq_num", type = DataType.TYPE_INT32),
            ),
          actions =
            listOf(
              ActionDef(
                events = listOf(EventType.EVENT_INSTANCE_FIRST_LOAD),
                type = ActionType.ACTION_SET_GEOPOINT,
                target_field = "bg_gps",
              )
            ),
        ),
      view =
        ViewDef(
          components =
            listOf(
              ViewComponent(
                repeat =
                  RepeatDef(
                    field_ref = "rep",
                    count_expression = "current()/../count",
                    actions =
                      listOf(
                        ActionDef(
                          events = listOf(EventType.EVENT_REPEAT_INSERT),
                          type = ActionType.ACTION_SET_VALUE,
                          target_field = "rep/seq_num",
                          value_expression = "position(..)",
                        )
                      ),
                    components =
                      listOf(
                        ViewComponent(
                          control =
                            ControlDef(field_ref = "seq_num", type = ControlType.CONTROL_INPUT)
                        )
                      ),
                  )
              )
            )
        ),
    )

  @Test
  fun repeat_blankInitialInstanceInXml_isKept() {
    val session = FormSession(treeRepeatXmlForm)

    val repeatGroup = session.state.rootComponents.first() as ComponentState.RepeatGroupState
    assertEquals(1, repeatGroup.instances.size)
  }

  @Test
  fun repeatControls_firstInstance_bindToIndexedPaths() {
    val session = FormSession(treeRepeatXmlForm)

    assertEquals(
      listOf("/data/trees[1]/species", "/data/trees[1]/dbh_cm"),
      session.state.repeatInstanceControls(0).map { it.canonicalPath },
    )
  }

  @Test
  fun repeatControls_inheritRequiredFromBind() {
    val session = FormSession(treeRepeatXmlForm)

    assertTrue(session.state.repeatInstanceControls(0)[0].fieldState.isRequired)
  }

  @Test
  fun addRepeatInstance_controlsBindToSecondInstancePaths() {
    val session = FormSession(treeRepeatXmlForm)

    session.addRepeatInstance("/data/trees")

    assertEquals(
      listOf("/data/trees[2]/species", "/data/trees[2]/dbh_cm"),
      session.state.repeatInstanceControls(1).map { it.canonicalPath },
    )
  }

  @Test
  fun repeatControls_firstInstanceAnswers_showInItsFieldStates() {
    val session = treeRepeatSessionWithAnswers("cordia" to 28.5, "grevillea" to 41.0)

    val firstInstance = session.state.repeatInstanceControls(0)
    assertEquals("cordia", firstInstance[0].fieldState.value?.scalar_value?.string_value)
    assertFalse(firstInstance[0].fieldState.isEmpty)
    assertEquals(28.5, firstInstance[1].fieldState.value?.scalar_value?.double_value)
    assertFalse(firstInstance[1].fieldState.isEmpty)
  }

  @Test
  fun repeatControls_secondInstanceAnswers_showInItsFieldStates() {
    val session = treeRepeatSessionWithAnswers("cordia" to 28.5, "grevillea" to 41.0)

    val secondInstance = session.state.repeatInstanceControls(1)
    assertEquals("grevillea", secondInstance[0].fieldState.value?.scalar_value?.string_value)
    assertFalse(secondInstance[0].fieldState.isEmpty)
    assertEquals(41.0, secondInstance[1].fieldState.value?.scalar_value?.double_value)
    assertFalse(secondInstance[1].fieldState.isEmpty)
  }

  /**
   * Answers `species` and `dbh_cm` through each repeat instance's controls, adding an instance
   * before every answer after the first (the form starts with one blank instance).
   */
  private fun treeRepeatSessionWithAnswers(vararg answers: Pair<String, Double>): FormSession {
    val session = FormSession(treeRepeatXmlForm)
    answers.forEachIndexed { index, (species, dbhCm) ->
      if (index > 0) session.addRepeatInstance("/data/trees")
      val controls = session.state.repeatInstanceControls(index)
      session.updateString(controls[0].canonicalPath, species)
      session.updateDouble(controls[1].canonicalPath, dbhCm)
    }
    return session
  }

  private fun FormState.repeatInstanceControls(index: Int): List<ComponentState.ControlState> =
    (rootComponents.first() as ComponentState.RepeatGroupState)
      .instances[index]
      .children
      .filterIsInstance<ComponentState.ControlState>()

  private val treeRepeatXmlForm: FormDef =
    XFormsXmlSerializer.deserializeFormDef(
      """
      <h:html xmlns="http://www.w3.org/2002/xforms"
              xmlns:h="http://www.w3.org/1999/xhtml"
              xmlns:jr="http://openrosa.org/javarosa">
        <h:head>
          <h:title>Repeat Group Saving Test</h:title>
          <model>
            <instance>
              <data id="repeat_save_test">
                <trees jr:template="">
                  <species/>
                  <dbh_cm/>
                </trees>
                <trees>
                  <species/>
                  <dbh_cm/>
                </trees>
              </data>
            </instance>
            <bind nodeset="/data/trees/species" type="select1" required="true()"/>
            <bind nodeset="/data/trees/dbh_cm" type="decimal"/>
          </model>
        </h:head>
        <h:body>
          <group ref="/data/trees">
            <label>Sampled Tree</label>
            <repeat nodeset="/data/trees">
              <select1 ref="/data/trees/species">
                <label>Species</label>
                <item><label>Cordia</label><value>cordia</value></item>
                <item><label>Grevillea</label><value>grevillea</value></item>
              </select1>
              <input ref="/data/trees/dbh_cm">
                <label>DBH (cm)</label>
              </input>
            </repeat>
          </group>
        </h:body>
      </h:html>
      """
        .trimIndent()
    )
}
