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
import openforis.collectone.v1.forms.ActionDef
import openforis.collectone.v1.forms.ActionType
import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.EventType
import openforis.collectone.v1.forms.FieldBinding
import openforis.collectone.v1.forms.FieldDefinition
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.ModelDef
import openforis.collectone.v1.forms.PreloadType
import openforis.collectone.v1.forms.PrimaryInstance
import openforis.collectone.v1.forms.RecordSchema
import org.openforis.collectone.v2.core.forms.buildRecordNode
import org.openforis.collectone.v2.core.forms.model.PlatformEffectRequest

class FormEngineInitializeTest {

  @Test
  fun initialize_withUuidGenerator_setsInstanceId() {
    val state = FormEngine.initialize(householdPreloadForm, environment = householdEnvironment())

    assertEquals(
      "uuid:11111111-2222-4333-8444-555555555555",
      state.recordInstance.metadata?.instance_id,
    )
  }

  @Test
  fun initialize_withDeviceProperties_setsMetadataDeviceId() {
    val state = FormEngine.initialize(householdPreloadForm, environment = householdEnvironment())

    assertEquals("pixel-9-pro", state.recordInstance.metadata?.device_id)
  }

  @Test
  fun initialize_appliesDefaultValues() {
    val state = FormEngine.initialize(householdPreloadForm, environment = householdEnvironment())

    assertEquals("Kenya", state.findFieldState("country")?.value?.scalar_value?.string_value)
  }

  @Test
  fun initialize_appliesPropertyPreload() {
    val state = FormEngine.initialize(householdPreloadForm, environment = householdEnvironment())

    assertEquals(
      "pixel-9-pro",
      state.findFieldState("device_id")?.value?.scalar_value?.string_value,
    )
  }

  @Test
  fun initialize_appliesContextPreload() {
    val state = FormEngine.initialize(householdPreloadForm, environment = householdEnvironment())

    assertEquals(
      "supervisor",
      state.findFieldState("session_role")?.value?.scalar_value?.string_value,
    )
  }

  @Test
  fun initialize_runsFirstLoadSetValueAction() {
    val state = FormEngine.initialize(householdPreloadForm, environment = householdEnvironment())

    assertEquals(
      "init-Kenya",
      state.findFieldState("initialized_flag")?.value?.scalar_value?.string_value,
    )
  }

  @Test
  fun initialize_queuesFirstLoadSetGeopointRequest() {
    val state = FormEngine.initialize(householdPreloadForm, environment = householdEnvironment())

    assertEquals(
      listOf(PlatformEffectRequest.SetGeopointRequest("/household/initial_gps")),
      state.pendingRequests,
    )
  }

  private val householdPreloadForm: FormDef =
    FormDef(
      form_id = "household_survey",
      version = "2026091801",
      model =
        ModelDef(
          primary_instance =
            PrimaryInstance(
              record_schema =
                RecordSchema(
                  name = "household",
                  fields =
                    listOf(
                      FieldDefinition(name = "country", type = DataType.TYPE_STRING),
                      FieldDefinition(name = "device_id", type = DataType.TYPE_STRING),
                      FieldDefinition(name = "session_role", type = DataType.TYPE_STRING),
                      FieldDefinition(name = "initialized_flag", type = DataType.TYPE_STRING),
                      FieldDefinition(name = "initial_gps", type = DataType.TYPE_GEOPOINT),
                    ),
                ),
              default_values = buildRecordNode { string("country", "Kenya") },
            ),
          bindings =
            listOf(
              FieldBinding(
                field_path = "device_id",
                type = DataType.TYPE_STRING,
                preload = PreloadType.PRELOAD_PROPERTY,
                preload_param = "deviceid",
              ),
              FieldBinding(
                field_path = "session_role",
                type = DataType.TYPE_STRING,
                preload = PreloadType.PRELOAD_CONTEXT,
                preload_param = "role",
              ),
            ),
          actions =
            listOf(
              ActionDef(
                events = listOf(EventType.EVENT_INSTANCE_FIRST_LOAD),
                type = ActionType.ACTION_SET_VALUE,
                target_field = "initialized_flag",
                value_expression = "concat('init-', /household/country)",
              ),
              ActionDef(
                events = listOf(EventType.EVENT_INSTANCE_FIRST_LOAD),
                type = ActionType.ACTION_SET_GEOPOINT,
                target_field = "initial_gps",
              ),
            ),
        ),
    )

  private fun householdEnvironment() =
    FormEnvironment(
      clockEpochMillis = { 1789689600000L },
      uuidGenerator = { "11111111-2222-4333-8444-555555555555" },
      deviceProperties = mapOf("deviceid" to "pixel-9-pro"),
      contextParams = mapOf("role" to "supervisor"),
    )
}
