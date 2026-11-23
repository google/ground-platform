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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import openforis.collectone.v1.forms.ControlDef
import openforis.collectone.v1.forms.ControlType
import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.FieldBinding
import openforis.collectone.v1.forms.FieldDefinition
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.ItemsetDef
import openforis.collectone.v1.forms.LanguageTranslation
import openforis.collectone.v1.forms.LocalizedString
import openforis.collectone.v1.forms.MediaRef
import openforis.collectone.v1.forms.ModelDef
import openforis.collectone.v1.forms.PrimaryInstance
import openforis.collectone.v1.forms.RangeConfig
import openforis.collectone.v1.forms.RecordSchema
import openforis.collectone.v1.forms.SecondaryInstance
import openforis.collectone.v1.forms.TranslationCatalog
import openforis.collectone.v1.forms.ViewComponent
import openforis.collectone.v1.forms.ViewDef
import org.openforis.collectone.v2.core.forms.model.ComponentState
import org.openforis.collectone.v2.core.forms.model.FinalizationResult
import org.openforis.collectone.v2.core.forms.model.ValidationErrorKind

class FormEngineValidationTest {

  @Test
  fun validation_emptyRequiredField_reportsRequiredMessage() {
    val session = FormSession(respondentValidationForm)

    // `age` is empty too, but an empty value does not violate its constraint.
    assertFalse(session.state.isValid)
    assertEquals(1, session.state.validationErrors.size)
    assertEquals(ValidationErrorKind.REQUIRED_MISSING, session.state.validationErrors.first().kind)
    assertEquals("Respondent name is mandatory", session.state.validationErrors.first().message)
  }

  @Test
  fun validation_constraintViolated_reportsConstraintMessage() {
    val session = FormSession(respondentValidationForm)

    session.updateString("name", "Amina")
    session.updateInt("age", 14)

    assertFalse(session.state.isValid)
    assertEquals(
      listOf(ValidationErrorKind.CONSTRAINT_VIOLATED to "Age must be at least 18"),
      session.state.validationErrors.map { it.kind to it.message },
    )
  }

  @Test
  fun validation_rangeValueOutOfBounds_reportsRangeError() {
    val session = FormSession(respondentValidationForm)

    session.updateString("name", "Amina")
    session.updateDouble("score", 15.0)

    assertFalse(session.state.isValid)
    assertEquals(
      listOf(ValidationErrorKind.RANGE_OUT_OF_BOUNDS),
      session.state.validationErrors.map { it.kind },
    )
  }

  @Test
  fun finalize_invalidForm_returnsValidationFailure() {
    val session = FormSession(respondentValidationForm)

    session.updateString("name", "Amina")
    session.updateInt("age", 14)

    assertIs<FinalizationResult.ValidationFailure>(session.finalize())
  }

  @Test
  fun finalize_validForm_succeedsWithEndTime() {
    val session = FormSession(respondentValidationForm)
    session.updateString("name", "Amina")
    session.updateInt("age", 28)
    session.updateDouble("score", 8.0)

    val success = session.finalize()

    assertIs<FinalizationResult.Success>(success)
    assertNotNull(success.recordInstance.metadata?.end_time)
  }

  private val respondentValidationForm: FormDef =
    FormDef(
      form_id = "validation_test",
      model =
        ModelDef(
          primary_instance =
            PrimaryInstance(
              record_schema =
                RecordSchema(
                  name = "data",
                  fields =
                    listOf(
                      FieldDefinition(name = "name", type = DataType.TYPE_STRING),
                      FieldDefinition(name = "age", type = DataType.TYPE_INT32),
                      FieldDefinition(name = "score", type = DataType.TYPE_DOUBLE),
                    ),
                )
            ),
          bindings =
            listOf(
              FieldBinding(
                field_path = "name",
                type = DataType.TYPE_STRING,
                required_expression = "true()",
                required_message = "Respondent name is mandatory",
              ),
              FieldBinding(
                field_path = "age",
                type = DataType.TYPE_INT32,
                constraint_expression = ". >= 18 and . <= 120",
                constraint_message = "Age must be at least 18",
              ),
              FieldBinding(field_path = "score", type = DataType.TYPE_DOUBLE),
            ),
        ),
      view =
        ViewDef(
          components =
            listOf(
              ViewComponent(
                control =
                  ControlDef(
                    field_ref = "score",
                    type = ControlType.CONTROL_RANGE,
                    range_config = RangeConfig(start = 0.0, end = 10.0, step = 1.0),
                  )
              )
            )
        ),
    )

  @Test
  fun requiredMessage_fromConventionItext_isReported() {
    val session = FormSession(itextMessagesForm)

    assertEquals("Age is mandatory", session.state.validationErrors.first().message)
  }

  @Test
  fun constraintMessage_fromConventionItext_isReported() {
    val session = FormSession(itextMessagesForm)

    session.updateInt("age", 12)

    assertEquals("Must be 18 or older", session.state.validationErrors.first().message)
  }

  @Test
  fun choiceLabel_fromItext_resolvesEveryItextForm() {
    val session = FormSession(itextMessagesForm)

    val speciesControl = session.state.rootComponents[1] as ComponentState.ControlState
    val oakLabel = speciesControl.options.first { it.value == "oak" }.label
    assertEquals("White Oak", oakLabel.text)
    assertEquals("Oak", oakLabel.shortText)
    assertEquals("Look for rounded lobes", oakLabel.guidanceText)
    assertEquals("jr://images/oak.png", oakLabel.media?.image_uri)
  }

  private val itextMessagesForm: FormDef =
    FormDef(
      form_id = "itext_msg_form",
      default_language = "English",
      model =
        ModelDef(
          primary_instance =
            PrimaryInstance(
              record_schema =
                RecordSchema(
                  name = "data",
                  fields =
                    listOf(
                      FieldDefinition(name = "age", type = DataType.TYPE_INT32),
                      FieldDefinition(name = "species", type = DataType.TYPE_SELECT_ONE),
                    ),
                )
            ),
          secondary_instances =
            listOf(
              SecondaryInstance(
                id = "trees",
                rows =
                  listOf(
                    org.openforis.collectone.v2.core.forms.model.secondaryInstanceRow(
                      "name" to "oak",
                      "itextId" to "oak_id",
                    ),
                    org.openforis.collectone.v2.core.forms.model.secondaryInstanceRow(
                      "name" to "pine",
                      "itextId" to "pine_id",
                    ),
                  ),
              )
            ),
          bindings =
            listOf(
              FieldBinding(
                field_path = "age",
                type = DataType.TYPE_INT32,
                required_expression = "true()",
                constraint_expression = ". >= 18",
              ),
              FieldBinding(field_path = "species", type = DataType.TYPE_SELECT_ONE),
            ),
          translations =
            TranslationCatalog(
              languages =
                listOf(
                  LanguageTranslation(
                    language = "English",
                    is_default = true,
                    strings =
                      mapOf(
                        "/data/age:jr:requiredMsg" to LocalizedString(value_ = "Age is mandatory"),
                        "/data/age:jr:constraintMsg" to
                          LocalizedString(value_ = "Must be 18 or older"),
                        "oak_id" to
                          LocalizedString(
                            value_ = "White Oak",
                            short_value = "Oak",
                            guidance_value = "Look for rounded lobes",
                            media = MediaRef(image_uri = "jr://images/oak.png"),
                          ),
                        "pine_id" to LocalizedString(value_ = "Eastern Pine"),
                      ),
                  )
                )
            ),
        ),
      view =
        ViewDef(
          components =
            listOf(
              ViewComponent(
                control = ControlDef(field_ref = "age", type = ControlType.CONTROL_INPUT)
              ),
              ViewComponent(
                control =
                  ControlDef(
                    field_ref = "species",
                    type = ControlType.CONTROL_SELECT_ONE,
                    itemset =
                      ItemsetDef(
                        instance_id = "trees",
                        value_ref = "name",
                        label_ref = "itextId",
                      ),
                  )
              ),
            )
        ),
    )
}
