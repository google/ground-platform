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
package org.openforis.collectone.v2.core.forms.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import openforis.collectone.v1.forms.ChoiceItem
import openforis.collectone.v1.forms.ControlDef
import openforis.collectone.v1.forms.ControlType
import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.FieldBinding
import openforis.collectone.v1.forms.FieldDefinition
import openforis.collectone.v1.forms.FieldValue
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.GroupDef
import openforis.collectone.v1.forms.LabelDef
import openforis.collectone.v1.forms.ModelDef
import openforis.collectone.v1.forms.PrimaryInstance
import openforis.collectone.v1.forms.RecordSchema
import openforis.collectone.v1.forms.RepeatDef
import openforis.collectone.v1.forms.TypedValue
import openforis.collectone.v1.forms.ViewComponent
import openforis.collectone.v1.forms.ViewDef
import org.openforis.collectone.v2.core.forms.buildRecordNode
import org.openforis.collectone.v2.core.forms.model.ComponentState
import org.openforis.collectone.v2.core.forms.model.FormState

/**
 * Characterizes how the engine turns a [ViewDef] into the [ComponentState] tree the form runners
 * render: synthetic paths, inherited relevance, choice resolution and repeat add/remove rules.
 */
class ViewMaterializationTest {

  @Test
  fun controlWithoutFieldRef_getsSyntheticPathFromItsPosition() {
    val state = FormEngine.initialize(viewForm)

    assertEquals("/data/_control_0", state.control(0).canonicalPath)
  }

  @Test
  fun unboundTrigger_isReadOnly() {
    val state = FormEngine.initialize(viewForm)

    assertTrue(state.control(0).fieldState.isReadOnly)
  }

  @Test
  fun control_resolvesHint() {
    val state = FormEngine.initialize(viewForm)

    assertEquals("Pick one", state.control(1).hint?.text)
  }

  @Test
  fun staticChoice_withoutParentValue_readsParentFromProperties() {
    val state = FormEngine.initialize(viewForm)

    val option = state.control(1).options.single { it.value == "north" }
    assertEquals("region_a", option.parentValue)
  }

  @Test
  fun staticChoice_withoutLabel_usesItsValueAsLabel() {
    val state = FormEngine.initialize(viewForm)

    val option = state.control(1).options.single { it.value == "south" }
    assertEquals("south", option.label.text)
  }

  @Test
  fun nonRelevantControl_listsNoOptions() {
    val state = FormEngine.initialize(viewForm).hideZone()

    assertFalse(state.control(1).isRelevant)
    assertTrue(state.control(1).options.isEmpty())
  }

  @Test
  fun groupWithoutFieldRef_usesItsParentPath() {
    val state = FormEngine.initialize(viewForm)

    assertEquals("/data", state.group(2).canonicalPath)
  }

  @Test
  fun groupWithoutFieldRef_resolvesChildPathsAgainstItsParent() {
    val state = FormEngine.initialize(viewForm)

    val child = state.group(2).children.single() as ComponentState.ControlState
    assertEquals("/data/notes", child.canonicalPath)
  }

  @Test
  fun nonRelevantGroup_marksChildControlsNonRelevant() {
    val state = FormEngine.initialize(viewForm).hideZone()

    val group = state.group(3)
    assertFalse(group.isRelevant)
    assertFalse((group.children.single() as ComponentState.ControlState).isRelevant)
  }

  @Test
  fun countControlledRepeat_reportsTargetCount() {
    val state = FormEngine.initialize(viewForm)

    assertEquals(2, state.repeat(4).targetCount)
  }

  @Test
  fun countControlledRepeat_cannotAddOrRemoveInstances() {
    val state = FormEngine.initialize(viewForm)

    assertFalse(state.repeat(4).canAddInstance)
    assertFalse(state.repeat(4).canRemoveInstance)
  }

  @Test
  fun noAddRemoveRepeat_cannotAddOrRemoveInstances() {
    val state = FormEngine.addRepeatInstance(FormEngine.initialize(viewForm), "/data/fixed")

    assertFalse(state.repeat(5).canAddInstance)
    assertFalse(state.repeat(5).canRemoveInstance)
  }

  @Test
  fun userRepeat_withoutInstances_canAddButNotRemove() {
    val state = FormEngine.initialize(viewForm)

    assertNull(state.repeat(6).targetCount)
    assertTrue(state.repeat(6).canAddInstance)
    assertFalse(state.repeat(6).canRemoveInstance)
  }

  @Test
  fun repeatInstance_childControlsBindToIndexedPaths() {
    val state = FormEngine.initialize(viewForm)

    val secondInstance = state.repeat(4).instances[1]
    val child = secondInstance.children.single() as ComponentState.ControlState
    assertEquals("/data/plot[2]/tree_count", child.canonicalPath)
  }

  private fun FormState.hideZone(): FormState =
    FormEngine.updateFieldValue(
      this,
      "/data/show_zone",
      FieldValue(scalar_value = TypedValue(string_value = "no")),
    )

  private fun FormState.control(index: Int) = rootComponents[index] as ComponentState.ControlState

  private fun FormState.group(index: Int) = rootComponents[index] as ComponentState.GroupState

  private fun FormState.repeat(index: Int) =
    rootComponents[index] as ComponentState.RepeatGroupState

  private val viewForm: FormDef =
    FormDef(
      form_id = "view_materialization",
      model =
        ModelDef(
          primary_instance =
            PrimaryInstance(
              record_schema =
                RecordSchema(
                  name = "data",
                  fields =
                    listOf(
                      FieldDefinition(name = "show_zone", type = DataType.TYPE_STRING),
                      FieldDefinition(name = "zone", type = DataType.TYPE_STRING),
                      FieldDefinition(name = "notes", type = DataType.TYPE_STRING),
                      FieldDefinition(
                        name = "zone_details",
                        type = DataType.TYPE_MESSAGE,
                        fields = listOf(FieldDefinition(name = "area", type = DataType.TYPE_INT32)),
                      ),
                      FieldDefinition(name = "plot_count", type = DataType.TYPE_INT32),
                      FieldDefinition(
                        name = "plot",
                        type = DataType.TYPE_MESSAGE,
                        is_repeated = true,
                        fields =
                          listOf(FieldDefinition(name = "tree_count", type = DataType.TYPE_INT32)),
                      ),
                      FieldDefinition(
                        name = "fixed",
                        type = DataType.TYPE_MESSAGE,
                        is_repeated = true,
                        fields =
                          listOf(FieldDefinition(name = "code", type = DataType.TYPE_STRING)),
                      ),
                      FieldDefinition(
                        name = "visit",
                        type = DataType.TYPE_MESSAGE,
                        is_repeated = true,
                        fields =
                          listOf(FieldDefinition(name = "date", type = DataType.TYPE_STRING)),
                      ),
                    ),
                ),
              default_values =
                buildRecordNode {
                  string("show_zone", "yes")
                  int32("plot_count", 2)
                },
            ),
          bindings =
            listOf(
              FieldBinding(
                field_path = "zone",
                type = DataType.TYPE_STRING,
                relevant_expression = "/data/show_zone = 'yes'",
              ),
              FieldBinding(
                field_path = "zone_details",
                type = DataType.TYPE_MESSAGE,
                relevant_expression = "/data/show_zone = 'yes'",
              ),
            ),
        ),
      view =
        ViewDef(
          components =
            listOf(
              ViewComponent(
                control =
                  ControlDef(type = ControlType.CONTROL_TRIGGER, label = LabelDef(text = "Go"))
              ),
              ViewComponent(
                control =
                  ControlDef(
                    field_ref = "zone",
                    type = ControlType.CONTROL_SELECT_ONE,
                    hint = LabelDef(text = "Pick one"),
                    choices =
                      listOf(
                        ChoiceItem(
                          value_ = "north",
                          label = LabelDef(text = "North"),
                          properties = mapOf("parent" to "region_a"),
                        ),
                        ChoiceItem(value_ = "south"),
                      ),
                  )
              ),
              ViewComponent(
                group =
                  GroupDef(
                    components =
                      listOf(
                        ViewComponent(
                          control =
                            ControlDef(field_ref = "notes", type = ControlType.CONTROL_INPUT)
                        )
                      )
                  )
              ),
              ViewComponent(
                group =
                  GroupDef(
                    field_ref = "zone_details",
                    components =
                      listOf(
                        ViewComponent(
                          control =
                            ControlDef(
                              field_ref = "zone_details/area",
                              type = ControlType.CONTROL_INPUT,
                            )
                        )
                      ),
                  )
              ),
              ViewComponent(
                repeat =
                  RepeatDef(
                    field_ref = "plot",
                    count_expression = "/data/plot_count",
                    components =
                      listOf(
                        ViewComponent(
                          control =
                            ControlDef(
                              field_ref = "plot/tree_count",
                              type = ControlType.CONTROL_INPUT,
                            )
                        )
                      ),
                  )
              ),
              ViewComponent(repeat = RepeatDef(field_ref = "fixed", no_add_remove = true)),
              ViewComponent(repeat = RepeatDef(field_ref = "visit")),
            )
        ),
    )
}
