/*
 * Copyright 2026 The Ground Authors.
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

/**
 * 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License'); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.groundplatform.v2.core.forms.engine

import groundplatform.v2.forms.ActionDef
import groundplatform.v2.forms.ActionType
import groundplatform.v2.forms.ControlDef
import groundplatform.v2.forms.ControlType
import groundplatform.v2.forms.DataType
import groundplatform.v2.forms.EntityDeclaration
import groundplatform.v2.forms.EntityPropertyMapping
import groundplatform.v2.forms.EntitySyncMetadata
import groundplatform.v2.forms.EventType
import groundplatform.v2.forms.FieldBinding
import groundplatform.v2.forms.FieldDefinition
import groundplatform.v2.forms.FormDef
import groundplatform.v2.forms.GroupDef
import groundplatform.v2.forms.IntentConfig
import groundplatform.v2.forms.ItemsetDef
import groundplatform.v2.forms.LabelDef
import groundplatform.v2.forms.LanguageTranslation
import groundplatform.v2.forms.LocalizedString
import groundplatform.v2.forms.MediaRef
import groundplatform.v2.forms.ModelDef
import groundplatform.v2.forms.OutputFragment
import groundplatform.v2.forms.PreloadType
import groundplatform.v2.forms.PrimaryInstance
import groundplatform.v2.forms.RangeConfig
import groundplatform.v2.forms.RecordSchema
import groundplatform.v2.forms.RepeatDef
import groundplatform.v2.forms.SecondaryInstance
import groundplatform.v2.forms.TranslationCatalog
import groundplatform.v2.forms.TypedValue
import groundplatform.v2.forms.ViewComponent
import groundplatform.v2.forms.ViewDef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.core.forms.buildRecordNode
import org.groundplatform.v2.core.forms.model.ComponentState
import org.groundplatform.v2.core.forms.model.FinalizationResult
import org.groundplatform.v2.core.forms.model.PlatformEffectRequest
import org.groundplatform.v2.core.forms.model.ValidationErrorKind

class FormEngineUnitTest {

  @Test
  fun initialize_evaluatesPreloadsDefaultsAndFirstLoadActions() {
    val formDef =
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

    val env =
      FormEnvironment(
        clockEpochMillis = { 1789689600000L },
        uuidGenerator = { "11111111-2222-4333-8444-555555555555" },
        deviceProperties = mapOf("deviceid" to "pixel-9-pro"),
        contextParams = mapOf("role" to "supervisor"),
      )

    val state = FormEngine.initialize(formDef, environment = env)

    assertEquals(
      "uuid:11111111-2222-4333-8444-555555555555",
      state.recordInstance.metadata?.instance_id,
    )
    assertEquals("pixel-9-pro", state.recordInstance.metadata?.device_id)
    assertEquals("Kenya", state.findFieldState("country")?.value?.scalar_value?.string_value)
    assertEquals(
      "pixel-9-pro",
      state.findFieldState("device_id")?.value?.scalar_value?.string_value,
    )
    assertEquals(
      "supervisor",
      state.findFieldState("session_role")?.value?.scalar_value?.string_value,
    )
    assertEquals(
      "init-Kenya",
      state.findFieldState("initialized_flag")?.value?.scalar_value?.string_value,
    )
    assertEquals(
      listOf(PlatformEffectRequest.SetGeopointRequest("/household/initial_gps")),
      state.pendingRequests,
    )
  }

  @Test
  fun topologicalCalculations_evaluatesOutOfOrderDependenciesAndOnce() {
    // Declare `total` BEFORE `tax` and `subtotal` to verify topological DAG sorting
    val formDef =
      FormDef(
        form_id = "invoice_form",
        model =
          ModelDef(
            primary_instance =
              PrimaryInstance(
                record_schema =
                  RecordSchema(
                    name = "data",
                    fields =
                      listOf(
                        FieldDefinition(name = "total", type = DataType.TYPE_DOUBLE),
                        FieldDefinition(name = "tax", type = DataType.TYPE_DOUBLE),
                        FieldDefinition(name = "subtotal", type = DataType.TYPE_DOUBLE),
                        FieldDefinition(name = "qty", type = DataType.TYPE_INT32),
                        FieldDefinition(name = "price", type = DataType.TYPE_DOUBLE),
                        FieldDefinition(name = "initial_code", type = DataType.TYPE_STRING),
                      ),
                  ),
                default_values =
                  buildRecordNode {
                    int32("qty", 4)
                    double("price", 25.0)
                  },
              ),
            bindings =
              listOf(
                FieldBinding(
                  field_path = "total",
                  type = DataType.TYPE_DOUBLE,
                  calculate_expression = "/data/subtotal + /data/tax",
                ),
                FieldBinding(
                  field_path = "tax",
                  type = DataType.TYPE_DOUBLE,
                  calculate_expression = "/data/subtotal * 0.10",
                ),
                FieldBinding(
                  field_path = "subtotal",
                  type = DataType.TYPE_DOUBLE,
                  calculate_expression = "/data/qty * /data/price",
                ),
                FieldBinding(
                  field_path = "initial_code",
                  type = DataType.TYPE_STRING,
                  calculate_expression = "once(concat('CODE-', /data/qty))",
                ),
              ),
          ),
      )

    val session = FormSession(formDef)
    assertEquals(100.0, session.state.findFieldState("subtotal")?.value?.scalar_value?.double_value)
    assertEquals(10.0, session.state.findFieldState("tax")?.value?.scalar_value?.double_value)
    assertEquals(110.0, session.state.findFieldState("total")?.value?.scalar_value?.double_value)
    assertEquals(
      "CODE-4",
      session.state.findFieldState("initial_code")?.value?.scalar_value?.string_value,
    )

    // Update qty to 10 -> subtotal (250), tax (25), total (275) update, but once() retains "CODE-4"
    session.updateInt("qty", 10)
    assertEquals(250.0, session.state.findFieldState("subtotal")?.value?.scalar_value?.double_value)
    assertEquals(25.0, session.state.findFieldState("tax")?.value?.scalar_value?.double_value)
    assertEquals(275.0, session.state.findFieldState("total")?.value?.scalar_value?.double_value)
    assertEquals(
      "CODE-4",
      session.state.findFieldState("initial_code")?.value?.scalar_value?.string_value,
    )
  }

  @Test
  fun relevancyMasking_prunesNonRelevantFromEffectiveRecordAndRestoresOnToggle() {
    val formDef =
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

    val session = FormSession(formDef)
    assertTrue(session.state.findFieldState("farm_details/acres")?.isRelevant == true)
    assertEquals(
      12.5,
      session.state.findFieldState("farm_details/acres")?.value?.scalar_value?.double_value,
    )

    // Toggle has_farm to "no" -> farm_details and its children become non-relevant and are pruned
    session.updateString("has_farm", "no")
    assertFalse(session.state.findFieldState("farm_details")?.isRelevant ?: true)
    assertFalse(session.state.findFieldState("farm_details/acres")?.isRelevant ?: true)
    assertNull(session.state.findFieldState("farm_details/acres")?.value)
    assertNull(session.state.recordInstance.data_?.fields?.get("farm_details"))

    // Toggle has_farm back to "yes" -> previously entered 12.5 acres is non-destructively restored!
    session.updateString("has_farm", "yes")
    assertTrue(session.state.findFieldState("farm_details/acres")?.isRelevant == true)
    assertEquals(
      12.5,
      session.state.findFieldState("farm_details/acres")?.value?.scalar_value?.double_value,
    )
  }

  @Test
  fun validationAndFinalization_checksRequiredConstraintAndRangeRules() {
    val formDef =
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

    val session = FormSession(formDef)
    // Initially `name` is empty -> REQUIRED_MISSING, while `age` is empty so constraint is NOT
    // violated
    assertFalse(session.state.isValid)
    assertEquals(1, session.state.validationErrors.size)
    assertEquals(ValidationErrorKind.REQUIRED_MISSING, session.state.validationErrors.first().kind)
    assertEquals("Respondent name is mandatory", session.state.validationErrors.first().message)

    // Set invalid age (14) and out-of-range score (15.0)
    session.updateString("name", "Amina")
    session.updateInt("age", 14)
    session.updateDouble("score", 15.0)

    assertFalse(session.state.isValid)
    assertEquals(2, session.state.validationErrors.size)
    assertTrue(
      session.state.validationErrors.any {
        it.kind == ValidationErrorKind.CONSTRAINT_VIOLATED &&
          it.message == "Age must be at least 18"
      }
    )
    assertTrue(
      session.state.validationErrors.any { it.kind == ValidationErrorKind.RANGE_OUT_OF_BOUNDS }
    )

    val failedFinalization = session.finalize()
    assertIs<FinalizationResult.ValidationFailure>(failedFinalization)

    // Fix age and score -> form becomes valid and finalizes cleanly with end_time populated
    session.updateInt("age", 28)
    session.updateDouble("score", 8.0)
    assertTrue(session.state.isValid)

    val success = session.finalize()
    assertIs<FinalizationResult.Success>(success)
    assertNotNull(success.recordInstance.metadata?.end_time)
  }

  @Test
  fun repeatsAndDynamicCount_reconcilesRepeatInstancesAndFiresNewRepeatActions() {
    val formDef =
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

    val session = FormSession(formDef)
    val repeatGroup =
      session.state.rootComponents.filterIsInstance<ComponentState.RepeatGroupState>().single()
    assertEquals(2, repeatGroup.instances.size)
    assertEquals("Member #1", repeatGroup.instances[0].label?.text)
    assertEquals("Member #2", repeatGroup.instances[1].label?.text)
    assertEquals(
      21,
      session.state.findFieldState("/data/person[1]/age")?.value?.scalar_value?.int32_value,
    )
    assertEquals(
      22,
      session.state.findFieldState("/data/person[2]/age")?.value?.scalar_value?.int32_value,
    )

    // Increase hh_size to 3 -> 3rd repeat instance automatically added and initialized via
    // EVENT_REPEAT_INSERT
    session.updateInt("hh_size", 3)
    val updatedRepeatGroup =
      session.state.rootComponents.filterIsInstance<ComponentState.RepeatGroupState>().single()
    assertEquals(3, updatedRepeatGroup.instances.size)
    assertEquals(
      23,
      session.state.findFieldState("/data/person[3]/age")?.value?.scalar_value?.int32_value,
    )
    assertEquals(
      3,
      session.state.findFieldState("/data/person[3]/label_idx")?.value?.scalar_value?.int32_value,
    )
  }

  @Test
  fun cascadingItemsetTranslationsAndEntities_resolvesDynamicOptionsAndEntityState() {
    val formDef =
      FormDef(
        form_id = "cascading_entity_form",
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
                        FieldDefinition(name = "country", type = DataType.TYPE_STRING),
                        FieldDefinition(name = "city", type = DataType.TYPE_SELECT_ONE),
                        FieldDefinition(name = "tree_id", type = DataType.TYPE_STRING),
                        FieldDefinition(name = "species", type = DataType.TYPE_STRING),
                      ),
                  ),
                default_values =
                  buildRecordNode {
                    string("country", "KE")
                    string("tree_id", "uuid:9999")
                    string("species", "Baobab")
                  },
              ),
            secondary_instances =
              listOf(
                SecondaryInstance(
                  id = "cities",
                  inline_data =
                    """
                    name,country,label
                    nairobi,KE,Nairobi
                    mombasa,KE,Mombasa
                    dar,TZ,Dar es Salaam
                    """
                      .trimIndent(),
                )
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
                          "city_q" to
                            LocalizedString(
                              value_ = "Select city in {country}",
                              short_value = "City",
                              guidance_value = "Ask the respondent for their primary municipality",
                              media = MediaRef(image_uri = "jr://images/city_en.png"),
                            )
                        ),
                    ),
                    LanguageTranslation(
                      language = "Swahili",
                      strings =
                        mapOf(
                          "city_q" to
                            LocalizedString(
                              value_ = "Chagua mji katika {country}",
                              short_value = "Mji",
                            )
                        ),
                    ),
                  )
              ),
            bindings =
              listOf(
                FieldBinding(field_path = "country", type = DataType.TYPE_STRING),
                FieldBinding(field_path = "city", type = DataType.TYPE_SELECT_ONE),
                FieldBinding(
                  field_path = "species",
                  type = DataType.TYPE_STRING,
                  entity_saveto = "species_name",
                ),
              ),
            entities =
              listOf(
                EntityDeclaration(
                  dataset = "trees",
                  entity_id_expression = "/data/tree_id",
                  label_expression = "concat(/data/species, ' (', /data/city, ')')",
                  create_condition = "true()",
                  sync_metadata =
                    EntitySyncMetadata(
                      base_version_expression = "1",
                      trunk_version_expression = "1",
                      branch_id_expression = "'branch-abc'",
                    ),
                  property_mappings =
                    listOf(
                      EntityPropertyMapping(
                        entity_property = "municipality",
                        source_field_path = "city",
                      )
                    ),
                )
              ),
          ),
        view =
          ViewDef(
            components =
              listOf(
                ViewComponent(
                  control =
                    ControlDef(
                      field_ref = "city",
                      type = ControlType.CONTROL_SELECT_ONE,
                      label =
                        LabelDef(
                          text_id = "city_q",
                          outputs =
                            listOf(
                              OutputFragment(
                                placeholder_id = "country",
                                value_expression = "/data/country",
                              )
                            ),
                        ),
                      itemset =
                        ItemsetDef(
                          instance_id = "cities",
                          nodeset_filter = "country = current()/../country",
                          value_ref = "name",
                          label_ref = "label",
                        ),
                    )
                )
              )
          ),
      )

    val session = FormSession(formDef)
    val cityControl =
      session.state.rootComponents.filterIsInstance<ComponentState.ControlState>().single()

    // Verify localized label with OutputFragment interpolation
    assertEquals("Select city in KE", cityControl.label?.text)
    assertEquals("City", cityControl.label?.shortText)
    assertEquals("jr://images/city_en.png", cityControl.label?.media?.image_uri)

    // Verify cascading itemset filtered by country = KE
    assertEquals(listOf("nairobi", "mombasa"), cityControl.options.map { it.value })

    // Switch language to Swahili -> label updates dynamically
    session.setLanguage("Swahili")
    val swahiliControl =
      session.state.rootComponents.filterIsInstance<ComponentState.ControlState>().single()
    assertEquals("Chagua mji katika KE", swahiliControl.label?.text)

    // Change country to TZ -> cascading options update to ["dar"]
    session.updateString("country", "TZ")
    val tzControl =
      session.state.rootComponents.filterIsInstance<ComponentState.ControlState>().single()
    assertEquals(listOf("dar"), tzControl.options.map { it.value })

    // Select city "dar" -> EntityState updates label and mapped properties
    session.updateString("city", "dar")
    val entityState = session.state.entityStates.single()
    assertEquals("trees", entityState.dataset)
    assertTrue(entityState.shouldCreate)
    assertEquals("uuid:9999", entityState.entityId)
    assertEquals("Baobab (dar)", entityState.label)
    assertEquals(1, entityState.baseVersion)
    assertEquals("branch-abc", entityState.branchId)
    assertEquals("Baobab", entityState.properties["species_name"]?.string_value)
    assertEquals("dar", entityState.properties["municipality"]?.string_value)
  }

  @Test
  fun valueChangedActionsIntentsAndManualRepeats_executesSideEffectsAndUpdatesRecord() {
    val formDef =
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
                        groundplatform.v2.forms.IntentConfig(
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

    val session = FormSession(formDef)
    assertNull(session.state.findFieldState("note_status")?.value)

    // Edit `note` -> triggers EVENT_VALUE_CHANGED actions (`note_status` = "edited" +
    // SetGeopointRequest)
    session.updateString("note", "Patient resting")
    assertEquals(
      "edited",
      session.state.findFieldState("note_status")?.value?.scalar_value?.string_value,
    )
    assertEquals(
      listOf(PlatformEffectRequest.SetGeopointRequest("/data/edit_gps")),
      session.state.pendingRequests,
    )

    // Verify resolved IntentConfig parameters
    val noteControl =
      session.state.rootComponents.filterIsInstance<ComponentState.ControlState>().first()
    assertEquals("Patient resting", noteControl.resolvedIntent?.parameters?.get("subject_note"))

    // Apply external intent response -> populates `pulse_rate` = 72
    val afterIntent =
      FormEngine.applyIntentResponse(
        state = session.state,
        contextCanonicalPath = "/data/note",
        responseMappings = noteControl.resolvedIntent!!.responseMappings,
        intentResults = mapOf("bpm" to TypedValue(int32_value = 72)),
      )
    assertEquals(72, afterIntent.findFieldState("pulse_rate")?.value?.scalar_value?.int32_value)

    // Add two manual repeat instances and remove the first
    session.addRepeatInstance("sample")
    session.updateString("/data/sample[1]/code", "S-100")
    session.addRepeatInstance("sample")
    session.updateString("/data/sample[2]/code", "S-200")

    val repeatState =
      session.state.rootComponents.filterIsInstance<ComponentState.RepeatGroupState>().single()
    assertTrue(repeatState.canAddInstance)
    assertTrue(repeatState.canRemoveInstance)
    assertEquals(2, repeatState.instances.size)

    session.removeRepeatInstance("sample", 1)
    val afterRemoveRepeat =
      session.state.rootComponents.filterIsInstance<ComponentState.RepeatGroupState>().single()
    assertEquals(1, afterRemoveRepeat.instances.size)
    assertEquals(
      "S-200",
      session.state.findFieldState("/data/sample[1]/code")?.value?.scalar_value?.string_value,
    )
  }
}
