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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
import openforis.collectone.v1.forms.IntentConfig
import openforis.collectone.v1.forms.ModelDef
import openforis.collectone.v1.forms.PrimaryInstance
import openforis.collectone.v1.forms.RecordSchema
import openforis.collectone.v1.forms.RepeatDef
import openforis.collectone.v1.forms.TypedValue
import openforis.collectone.v1.forms.ViewComponent
import openforis.collectone.v1.forms.ViewDef
import org.openforis.collectone.v2.core.forms.model.ComponentState
import org.openforis.collectone.v2.core.forms.model.FormState
import org.openforis.collectone.v2.core.forms.model.PlatformEffectRequest

class FormEngineActionsTest {

  private fun FormState.singleRepeatGroup(): ComponentState.RepeatGroupState =
    rootComponents.filterIsInstance<ComponentState.RepeatGroupState>().single()

  @Test
  fun valueChangedAction_beforeAnyEdit_hasNotRun() {
    val session = FormSession(notesActionsForm)

    assertNull(session.state.findFieldState("note_status")?.value)
  }

  @Test
  fun valueChangedAction_onEdit_setsTargetValue() {
    val session = FormSession(notesActionsForm)

    session.updateString("note", "Patient resting")

    assertEquals(
      "edited",
      session.state.findFieldState("note_status")?.value?.scalar_value?.string_value,
    )
  }

  @Test
  fun valueChangedAction_onEdit_queuesSetGeopointRequest() {
    val session = FormSession(notesActionsForm)

    session.updateString("note", "Patient resting")

    assertEquals(
      listOf(PlatformEffectRequest.SetGeopointRequest("/data/edit_gps")),
      session.state.pendingRequests,
    )
  }

  @Test
  fun intent_parameters_resolveFromFieldValues() {
    val session = FormSession(notesActionsForm)

    session.updateString("note", "Patient resting")

    val noteControl =
      session.state.rootComponents.filterIsInstance<ComponentState.ControlState>().first()
    assertEquals("Patient resting", noteControl.resolvedIntent?.parameters?.get("subject_note"))
  }

  @Test
  fun applyIntentResponse_mapsResultsIntoFields() {
    val session = FormSession(notesActionsForm)
    val noteControl =
      session.state.rootComponents.filterIsInstance<ComponentState.ControlState>().first()
    val intent = assertNotNull(noteControl.resolvedIntent)

    val afterIntent =
      FormEngine.applyIntentResponse(
        state = session.state,
        contextCanonicalPath = "/data/note",
        responseMappings = intent.responseMappings,
        intentResults = mapOf("bpm" to TypedValue(int32_value = 72)),
      )

    assertEquals(72, afterIntent.findFieldState("pulse_rate")?.value?.scalar_value?.int32_value)
  }

  @Test
  fun addRepeatInstance_manualRepeat_addsInstances() {
    val session = FormSession(notesActionsForm)

    session.addRepeatInstance("sample")
    session.addRepeatInstance("sample")

    assertEquals(2, session.state.singleRepeatGroup().instances.size)
  }

  @Test
  fun addRepeatInstance_manualRepeat_allowsAddingMore() {
    val session = FormSession(notesActionsForm)

    session.addRepeatInstance("sample")
    session.addRepeatInstance("sample")

    assertTrue(session.state.singleRepeatGroup().canAddInstance)
  }

  @Test
  fun addRepeatInstance_manualRepeat_allowsRemoving() {
    val session = FormSession(notesActionsForm)

    session.addRepeatInstance("sample")
    session.addRepeatInstance("sample")

    assertTrue(session.state.singleRepeatGroup().canRemoveInstance)
  }

  @Test
  fun removeRepeatInstance_manualRepeat_dropsOneInstance() {
    val session = notesSessionWithSampleCodes("S-100", "S-200")

    session.removeRepeatInstance("sample", 1)

    assertEquals(1, session.state.singleRepeatGroup().instances.size)
  }

  @Test
  fun removeRepeatInstance_first_shiftsLaterInstancesDown() {
    val session = notesSessionWithSampleCodes("S-100", "S-200")

    session.removeRepeatInstance("sample", 1)

    assertEquals(
      "S-200",
      session.state.findFieldState("/data/sample[1]/code")?.value?.scalar_value?.string_value,
    )
  }

  /** Adds one manual `sample` repeat instance per code, answering its `code` question. */
  private fun notesSessionWithSampleCodes(vararg codes: String): FormSession {
    val session = FormSession(notesActionsForm)
    codes.forEachIndexed { index, code ->
      session.addRepeatInstance("sample")
      session.updateString("/data/sample[${index + 1}]/code", code)
    }
    return session
  }

  private val notesActionsForm: FormDef =
    FormDef(
      form_id = "actions_and_intents_form",
      model =
        ModelDef(
          primary_instance =
            PrimaryInstance(
              record_schema =
                RecordSchema(
                  name = "data",
                  fields =
                    listOf(
                      FieldDefinition(name = "note", type = DataType.TYPE_STRING),
                      FieldDefinition(name = "note_status", type = DataType.TYPE_STRING),
                      FieldDefinition(name = "edit_gps", type = DataType.TYPE_GEOPOINT),
                      FieldDefinition(name = "pulse_rate", type = DataType.TYPE_INT32),
                      FieldDefinition(
                        name = "sample",
                        type = DataType.TYPE_MESSAGE,
                        is_repeated = true,
                        fields =
                          listOf(FieldDefinition(name = "code", type = DataType.TYPE_STRING)),
                      ),
                    ),
                )
            ),
          bindings =
            listOf(
              FieldBinding(field_path = "note", type = DataType.TYPE_STRING),
              FieldBinding(field_path = "note_status", type = DataType.TYPE_STRING),
              FieldBinding(field_path = "pulse_rate", type = DataType.TYPE_INT32),
            ),
        ),
      view =
        ViewDef(
          components =
            listOf(
              ViewComponent(
                control =
                  ControlDef(
                    field_ref = "note",
                    type = ControlType.CONTROL_INPUT,
                    intent =
                      openforis.collectone.v1.forms.IntentConfig(
                        intent_uri = "org.opendatakit.sensors.OXIMETER",
                        parameters = mapOf("subject_note" to "/data/note"),
                        response_mappings = mapOf("bpm" to "pulse_rate"),
                      ),
                    actions =
                      listOf(
                        ActionDef(
                          events = listOf(EventType.EVENT_VALUE_CHANGED),
                          type = ActionType.ACTION_SET_VALUE,
                          target_field = "note_status",
                          literal_value = TypedValue(string_value = "edited"),
                        ),
                        ActionDef(
                          events = listOf(EventType.EVENT_VALUE_CHANGED),
                          type = ActionType.ACTION_SET_GEOPOINT,
                          target_field = "edit_gps",
                        ),
                      ),
                  )
              ),
              ViewComponent(
                repeat =
                  RepeatDef(
                    field_ref = "sample",
                    components =
                      listOf(
                        ViewComponent(
                          control =
                            ControlDef(
                              field_ref = "sample/code",
                              type = ControlType.CONTROL_INPUT,
                            )
                        )
                      ),
                  )
              ),
            )
        ),
    )
}
