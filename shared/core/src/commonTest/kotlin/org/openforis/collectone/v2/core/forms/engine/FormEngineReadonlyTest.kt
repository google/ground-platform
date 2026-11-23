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
import kotlin.test.assertFalse
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
import org.openforis.collectone.v2.core.forms.model.ComponentState

class FormEngineReadonlyTest {

  @Test
  fun readonly_expressionFalse_leavesFieldEditable() {
    val session = FormSession(readonlyForm)

    assertFalse(session.state.findFieldState("dynamic_field")?.isReadOnly == true)
  }

  @Test
  fun readonly_onGroup_isInheritedByChildren() {
    val session = FormSession(readonlyForm)

    assertTrue(session.state.findFieldState("locked_group/child_input")?.isReadOnly == true)
    val groupState = session.state.rootComponents[2] as ComponentState.GroupState
    val childControl = groupState.children.first() as ComponentState.ControlState
    assertTrue(childControl.fieldState.isReadOnly)
  }

  @Test
  fun readonly_expressionBecomesTrue_locksField() {
    val session = FormSession(readonlyForm)

    session.updateString("lock_mode", "locked")

    assertTrue(session.state.findFieldState("dynamic_field")?.isReadOnly == true)
  }

  private val readonlyForm: FormDef =
    FormDef(
      form_id = "readonly_form",
      model =
        ModelDef(
          primary_instance =
            PrimaryInstance(
              record_schema =
                RecordSchema(
                  name = "data",
                  fields =
                    listOf(
                      FieldDefinition(name = "lock_mode", type = DataType.TYPE_STRING),
                      FieldDefinition(name = "dynamic_field", type = DataType.TYPE_STRING),
                      FieldDefinition(
                        name = "locked_group",
                        type = DataType.TYPE_MESSAGE,
                        fields =
                          listOf(
                            FieldDefinition(name = "child_input", type = DataType.TYPE_STRING)
                          ),
                      ),
                    ),
                ),
              default_values = buildRecordNode { string("lock_mode", "open") },
            ),
          bindings =
            listOf(
              FieldBinding(field_path = "lock_mode", type = DataType.TYPE_STRING),
              FieldBinding(
                field_path = "dynamic_field",
                type = DataType.TYPE_STRING,
                read_only_expression = "/data/lock_mode = 'locked'",
              ),
              FieldBinding(
                field_path = "locked_group",
                type = DataType.TYPE_MESSAGE,
                read_only = true,
              ),
              FieldBinding(field_path = "locked_group/child_input", type = DataType.TYPE_STRING),
            ),
        ),
      view =
        ViewDef(
          components =
            listOf(
              ViewComponent(
                control = ControlDef(field_ref = "lock_mode", type = ControlType.CONTROL_INPUT)
              ),
              ViewComponent(
                control = ControlDef(field_ref = "dynamic_field", type = ControlType.CONTROL_INPUT)
              ),
              ViewComponent(
                group =
                  GroupDef(
                    field_ref = "locked_group",
                    components =
                      listOf(
                        ViewComponent(
                          control =
                            ControlDef(
                              field_ref = "child_input",
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
