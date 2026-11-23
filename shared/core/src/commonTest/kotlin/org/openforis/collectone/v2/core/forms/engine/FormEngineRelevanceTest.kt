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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import openforis.collectone.v1.forms.ControlDef
import openforis.collectone.v1.forms.ControlType
import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.FieldBinding
import openforis.collectone.v1.forms.FieldDefinition
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.GroupDef
import openforis.collectone.v1.forms.ModelDef
import openforis.collectone.v1.forms.PrimaryInstance
import openforis.collectone.v1.forms.RecordSchema
import openforis.collectone.v1.forms.ViewComponent
import openforis.collectone.v1.forms.ViewDef
import org.openforis.collectone.v2.core.forms.buildRecordNode

class FormEngineRelevanceTest {

  @Test
  fun relevance_true_keepsGroupValues() {
    val session = FormSession(farmRelevanceForm)

    assertTrue(session.state.findFieldState("farm_details/acres")?.isRelevant == true)
    assertEquals(
      12.5,
      session.state.findFieldState("farm_details/acres")?.value?.scalar_value?.double_value,
    )
  }

  @Test
  fun relevance_turnsFalse_marksGroupSubtreeNonRelevant() {
    val session = FormSession(farmRelevanceForm)

    session.updateString("has_farm", "no")

    assertFalse(session.state.findFieldState("farm_details")?.isRelevant ?: true)
    assertFalse(session.state.findFieldState("farm_details/acres")?.isRelevant ?: true)
  }

  @Test
  fun relevance_turnsFalse_prunesValuesFromRecord() {
    val session = FormSession(farmRelevanceForm)

    session.updateString("has_farm", "no")

    assertNull(session.state.findFieldState("farm_details/acres")?.value)
    assertNull(session.state.recordInstance.data_?.fields?.get("farm_details"))
  }

  @Test
  fun relevance_turnsTrueAgain_restoresPreviousValues() {
    val session = FormSession(farmRelevanceForm)

    session.updateString("has_farm", "no")
    session.updateString("has_farm", "yes")

    assertTrue(session.state.findFieldState("farm_details/acres")?.isRelevant == true)
    assertEquals(
      12.5,
      session.state.findFieldState("farm_details/acres")?.value?.scalar_value?.double_value,
    )
  }

  private val farmRelevanceForm: FormDef =
    FormDef(
      form_id = "relevancy_test",
      model =
        ModelDef(
          primary_instance =
            PrimaryInstance(
              record_schema =
                RecordSchema(
                  name = "data",
                  fields =
                    listOf(
                      FieldDefinition(name = "has_farm", type = DataType.TYPE_STRING),
                      FieldDefinition(
                        name = "farm_details",
                        type = DataType.TYPE_MESSAGE,
                        fields =
                          listOf(
                            FieldDefinition(name = "acres", type = DataType.TYPE_DOUBLE),
                            FieldDefinition(name = "crop", type = DataType.TYPE_STRING),
                          ),
                      ),
                    ),
                ),
              default_values =
                buildRecordNode {
                  string("has_farm", "yes")
                  group("farm_details") {
                    double("acres", 12.5)
                    string("crop", "coffee")
                  }
                },
            ),
          bindings =
            listOf(
              FieldBinding(
                field_path = "farm_details",
                type = DataType.TYPE_MESSAGE,
                relevant_expression = "/data/has_farm = 'yes'",
              ),
              FieldBinding(field_path = "farm_details/acres", type = DataType.TYPE_DOUBLE),
              FieldBinding(field_path = "farm_details/crop", type = DataType.TYPE_STRING),
            ),
        ),
      view =
        ViewDef(
          components =
            listOf(
              ViewComponent(
                control = ControlDef(field_ref = "has_farm", type = ControlType.CONTROL_INPUT)
              ),
              ViewComponent(
                group =
                  GroupDef(
                    field_ref = "farm_details",
                    components =
                      listOf(
                        ViewComponent(
                          control =
                            ControlDef(
                              field_ref = "farm_details/acres",
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
