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
import kotlin.test.assertTrue
import openforis.collectone.v1.forms.ControlDef
import openforis.collectone.v1.forms.ControlType
import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.EntityDeclaration
import openforis.collectone.v1.forms.EntityPropertyMapping
import openforis.collectone.v1.forms.EntitySyncMetadata
import openforis.collectone.v1.forms.FieldBinding
import openforis.collectone.v1.forms.FieldDefinition
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.ItemsetDef
import openforis.collectone.v1.forms.LabelDef
import openforis.collectone.v1.forms.LanguageTranslation
import openforis.collectone.v1.forms.LocalizedString
import openforis.collectone.v1.forms.MediaRef
import openforis.collectone.v1.forms.ModelDef
import openforis.collectone.v1.forms.OutputFragment
import openforis.collectone.v1.forms.PrimaryInstance
import openforis.collectone.v1.forms.RecordSchema
import openforis.collectone.v1.forms.SecondaryInstance
import openforis.collectone.v1.forms.TranslationCatalog
import openforis.collectone.v1.forms.ViewComponent
import openforis.collectone.v1.forms.ViewDef
import org.openforis.collectone.v2.core.forms.buildRecordNode
import org.openforis.collectone.v2.core.forms.model.ComponentState
import org.openforis.collectone.v2.core.forms.model.FormState

class FormEngineCascadingEntityFormTest {

  @Test
  fun label_withTextId_interpolatesOutputs() {
    val session = FormSession(cityTreeEntityForm)

    assertEquals("Select city in KE", session.state.singleControl().label?.text)
  }

  @Test
  fun label_withTextId_resolvesItextVariants() {
    val session = FormSession(cityTreeEntityForm)

    val label = session.state.singleControl().label
    assertEquals("City", label?.shortText)
    assertEquals("jr://images/city_en.png", label?.media?.image_uri)
  }

  @Test
  fun itemset_withFilter_listsMatchingRows() {
    val session = FormSession(cityTreeEntityForm)

    assertEquals(
      listOf("nairobi", "mombasa"),
      session.state.singleControl().options.map { it.value },
    )
  }

  @Test
  fun setLanguage_relabelsControls() {
    val session = FormSession(cityTreeEntityForm)

    session.setLanguage("Swahili")

    assertEquals("Chagua mji katika KE", session.state.singleControl().label?.text)
  }

  @Test
  fun itemset_filterInputChange_updatesOptions() {
    val session = FormSession(cityTreeEntityForm)

    session.updateString("country", "TZ")

    assertEquals(listOf("dar"), session.state.singleControl().options.map { it.value })
  }

  @Test
  fun entity_createCondition_marksEntityForCreationInDataset() {
    val session = FormSession(cityTreeEntityForm)
    session.updateString("country", "TZ")

    session.updateString("city", "dar")

    val entityState = session.state.entityStates.single()
    assertEquals("trees", entityState.dataset)
    assertTrue(entityState.shouldCreate)
  }

  @Test
  fun entity_idExpression_resolvesEntityId() {
    val session = FormSession(cityTreeEntityForm)
    session.updateString("country", "TZ")

    session.updateString("city", "dar")

    assertEquals("uuid:9999", session.state.entityStates.single().entityId)
  }

  @Test
  fun entity_labelExpression_resolvesEntityLabel() {
    val session = FormSession(cityTreeEntityForm)
    session.updateString("country", "TZ")

    session.updateString("city", "dar")

    assertEquals("Baobab (dar)", session.state.entityStates.single().label)
  }

  @Test
  fun entity_syncMetadata_resolvesBaseVersionAndBranch() {
    val session = FormSession(cityTreeEntityForm)
    session.updateString("country", "TZ")

    session.updateString("city", "dar")

    val entityState = session.state.entityStates.single()
    assertEquals(1, entityState.baseVersion)
    assertEquals("branch-abc", entityState.branchId)
  }

  @Test
  fun entity_properties_includeSaveToBindings() {
    val session = FormSession(cityTreeEntityForm)
    session.updateString("country", "TZ")

    session.updateString("city", "dar")

    val properties = session.state.entityStates.single().properties
    assertEquals("Baobab", properties["species_name"]?.string_value)
  }

  @Test
  fun entity_properties_includePropertyMappings() {
    val session = FormSession(cityTreeEntityForm)
    session.updateString("country", "TZ")

    session.updateString("city", "dar")

    val properties = session.state.entityStates.single().properties
    assertEquals("dar", properties["municipality"]?.string_value)
  }

  private fun FormState.singleControl(): ComponentState.ControlState =
    rootComponents.filterIsInstance<ComponentState.ControlState>().single()

  private val cityTreeEntityForm: FormDef =
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
                rows =
                  listOf(
                    org.openforis.collectone.v2.core.forms.model.secondaryInstanceRow(
                      "name" to "nairobi",
                      "country" to "KE",
                      "label" to "Nairobi",
                    ),
                    org.openforis.collectone.v2.core.forms.model.secondaryInstanceRow(
                      "name" to "mombasa",
                      "country" to "KE",
                      "label" to "Mombasa",
                    ),
                    org.openforis.collectone.v2.core.forms.model.secondaryInstanceRow(
                      "name" to "dar",
                      "country" to "TZ",
                      "label" to "Dar es Salaam",
                    ),
                  ),
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
}
