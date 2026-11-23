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
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import openforis.collectone.v1.forms.ActionDef
import openforis.collectone.v1.forms.ActionType
import openforis.collectone.v1.forms.ControlDef
import openforis.collectone.v1.forms.ControlType
import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.EventType
import openforis.collectone.v1.forms.FieldBinding
import openforis.collectone.v1.forms.FieldDefinition
import openforis.collectone.v1.forms.FieldValue
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.ModelDef
import openforis.collectone.v1.forms.PrimaryInstance
import openforis.collectone.v1.forms.RecordMetadata
import openforis.collectone.v1.forms.RecordNode
import openforis.collectone.v1.forms.RecordSchema
import openforis.collectone.v1.forms.RepeatDef
import openforis.collectone.v1.forms.TypedValue
import openforis.collectone.v1.forms.ViewComponent
import openforis.collectone.v1.forms.ViewDef
import org.openforis.collectone.v2.core.forms.model.FinalizationResult

class FormSessionCompiledFormTest {

  /**
   * A tree form whose `species` question fires a value-changed action. The action's XPath is only
   * parsed the first time it runs, which lets the tests observe which [CompiledForm] an edit used.
   */
  private val treeForm =
    FormDef(
      form_id = "tree",
      model =
        ModelDef(
          primary_instance =
            PrimaryInstance(
              record_schema =
                RecordSchema(
                  name = "data",
                  fields =
                    listOf(
                      FieldDefinition(name = "species", type = DataType.TYPE_STRING),
                      FieldDefinition(name = "species_note", type = DataType.TYPE_STRING),
                      FieldDefinition(name = "height_m", type = DataType.TYPE_DOUBLE),
                      FieldDefinition(name = "height_cm", type = DataType.TYPE_DOUBLE),
                      FieldDefinition(
                        name = "stem",
                        type = DataType.TYPE_MESSAGE,
                        fields = listOf(FieldDefinition(name = "dbh", type = DataType.TYPE_DOUBLE)),
                      ),
                    ),
                )
            ),
          bindings =
            listOf(
              FieldBinding(
                field_path = "height_cm",
                type = DataType.TYPE_DOUBLE,
                calculate_expression = "/data/height_m * 100",
              ),
              FieldBinding(
                field_path = "height_m",
                type = DataType.TYPE_DOUBLE,
                required_expression = "true()",
              ),
            ),
        ),
      view =
        ViewDef(
          components =
            listOf(
              ViewComponent(
                control =
                  ControlDef(
                    field_ref = "species",
                    type = ControlType.CONTROL_INPUT,
                    actions =
                      listOf(
                        ActionDef(
                          events = listOf(EventType.EVENT_VALUE_CHANGED),
                          type = ActionType.ACTION_SET_VALUE,
                          target_field = "species_note",
                          value_expression = "concat('Species: ', /data/species)",
                        )
                      ),
                  )
              ),
              ViewComponent(
                control = ControlDef(field_ref = "height_m", type = ControlType.CONTROL_INPUT)
              ),
              ViewComponent(
                repeat =
                  RepeatDef(
                    field_ref = "stem",
                    components =
                      listOf(
                        ViewComponent(
                          control =
                            ControlDef(field_ref = "stem/dbh", type = ControlType.CONTROL_INPUT)
                        )
                      ),
                  )
              ),
            )
        ),
    )

  private val fixedEnvironment =
    FormEnvironment(clockEpochMillis = { 1789689600000L }, uuidGenerator = { "fixed-uuid" })

  @Test
  fun session_reusesItsCompiledForm_withoutReparsingExpressions() {
    val compiled = FormEngine.compile(treeForm)
    val session = FormSession(compiled, environment = fixedEnvironment)
    val parsedAfterInitialize = compiled.parsedExpressionCount

    session.updateString("species", "Teak")

    // The value-changed action's expression was parsed into *this* instance by the edit.
    assertEquals(parsedAfterInitialize + 1, compiled.parsedExpressionCount)
    assertEquals(
      "Species: Teak",
      session.state.findFieldState("species_note")?.value?.scalar_value?.string_value,
    )

    val parsedAfterFirstEdit = compiled.parsedExpressionCount
    session.updateString("species", "Oak")
    session.updateDouble("height_m", 12.5)
    session.addRepeatInstance("stem")
    session.updateDouble("/data/stem[1]/dbh", 31.0)
    session.removeRepeatInstance("stem", 1)
    session.setLanguage("default")
    session.finalize()

    assertEquals(parsedAfterFirstEdit, compiled.parsedExpressionCount)
    assertSame(compiled, session.compiledForm)
    assertSame(treeForm, session.state.formDef)
  }

  @Test
  fun session_producesTheSameStatesAsTheStatelessEngine() {
    val session = FormSession(treeForm, environment = fixedEnvironment)
    var expected = FormEngine.initialize(treeForm, environment = fixedEnvironment)
    assertEquals(expected, session.state)

    session.updateString("species", "Teak")
    expected =
      FormEngine.updateFieldValue(expected, "species", stringValue("Teak"), fixedEnvironment)
    assertEquals(expected, session.state)

    session.updateDouble("height_m", 12.5)
    expected =
      FormEngine.updateFieldValue(
        expected,
        "height_m",
        FieldValue(scalar_value = TypedValue(double_value = 12.5)),
        fixedEnvironment,
      )
    assertEquals(expected, session.state)
    assertEquals(
      1250.0,
      session.state.findFieldState("height_cm")?.value?.scalar_value?.double_value,
    )

    session.addRepeatInstance("stem")
    expected = FormEngine.addRepeatInstance(expected, "stem", fixedEnvironment)
    assertEquals(expected, session.state)

    session.removeRepeatInstance("stem", 1)
    expected = FormEngine.removeRepeatInstance(expected, "stem", 1, fixedEnvironment)
    assertEquals(expected, session.state)

    val sessionResult = session.finalize()
    val engineResult = FormEngine.finalize(expected, fixedEnvironment)
    assertEquals(engineResult, sessionResult)
    assertEquals(FinalizationResult.Success::class, sessionResult::class)
  }

  @Test
  fun compiledOverload_rejectsAStateFromAnotherForm() {
    val state = FormEngine.initialize(treeForm, environment = fixedEnvironment)
    val otherCompiled = FormEngine.compile(treeForm.copy(form_id = "other"))

    assertFailsWith<IllegalArgumentException> {
      FormEngine.updateFieldValue(
        otherCompiled,
        state,
        "species",
        stringValue("Teak"),
        fixedEnvironment,
      )
    }
  }

  @Test
  fun setFieldValue_onDataField_keepsMissingMetadataMissing() {
    val update =
      RecordMutator.setFieldValue(
        rootNode = RecordNode(),
        metadata = null,
        rootAliases = setOf("data"),
        canonicalPath = "/data/species",
        newValue = stringValue("Teak"),
      )

    assertEquals("Teak", update.node.fields["species"]?.scalar_value?.string_value)
    assertNull(update.metadata)
  }

  @Test
  fun setFieldValue_onMetaField_createsMetadata() {
    val update =
      RecordMutator.setFieldValue(
        rootNode = RecordNode(),
        metadata = null,
        rootAliases = setOf("data"),
        canonicalPath = "/data/meta/instanceID",
        newValue = stringValue("uuid:abc"),
      )

    assertEquals(RecordMetadata(instance_id = "uuid:abc"), update.metadata)
  }

  private fun stringValue(value: String) =
    FieldValue(scalar_value = TypedValue(string_value = value))
}
