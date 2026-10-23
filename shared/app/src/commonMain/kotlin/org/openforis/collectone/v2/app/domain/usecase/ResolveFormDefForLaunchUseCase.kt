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
package org.openforis.collectone.v2.app.domain.usecase

import openforis.collectone.v1.forms.ChoiceItem
import openforis.collectone.v1.forms.ControlDef
import openforis.collectone.v1.forms.ControlType
import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.FieldBinding
import openforis.collectone.v1.forms.FieldDefinition
import openforis.collectone.v1.forms.FieldValue
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.ItemsetDef
import openforis.collectone.v1.forms.LabelDef
import openforis.collectone.v1.forms.LanguageTranslation
import openforis.collectone.v1.forms.LocalizedString
import openforis.collectone.v1.forms.MediaRef
import openforis.collectone.v1.forms.ModelDef
import openforis.collectone.v1.forms.PreloadType
import openforis.collectone.v1.forms.PrimaryInstance
import openforis.collectone.v1.forms.RecordNode
import openforis.collectone.v1.forms.RecordSchema
import openforis.collectone.v1.forms.SecondaryInstance
import openforis.collectone.v1.forms.TranslationCatalog
import openforis.collectone.v1.forms.TypedValue
import openforis.collectone.v1.forms.ViewComponent
import openforis.collectone.v1.forms.ViewDef
import org.openforis.collectone.v2.app.domain.model.FormPreviewItem
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.SurveyConfig
import org.openforis.collectone.v2.core.forms.model.FormDefinition
import org.openforis.collectone.v2.core.forms.model.secondaryInstanceRow
import org.openforis.collectone.v2.core.forms.ui.WorkbenchExampleForm

/**
 * Domain use case resolving the [FormDef] to execute for a given [FormPreviewItem] and injecting
 * the `entityref` (`/data/target_entity`) map/list selection step when launched without a
 * pre-selected entity.
 */
class ResolveFormDefForLaunchUseCase {
  operator fun invoke(
    customFormDef: FormDef?,
    form: FormPreviewItem,
    candidateEntities: List<GeospatialEntityItem> = emptyList(),
    defaultSelectedEntityId: String = "",
    includeEntityRefStep: Boolean = false,
    surveyConfig: SurveyConfig? = null,
  ): FormDef {
    val storedForm = surveyConfig?.formsById?.get(form.id)
    val base = customFormDef ?: (storedForm ?: builtInFallbackFormForForm(form)).proto
    return if (includeEntityRefStep && form.requiresEntity) {
      ensureEntityRefStepInFormDef(
        baseFormDef = base,
        form = form,
        candidateEntities = candidateEntities,
        defaultSelectedEntityId = defaultSelectedEntityId,
      )
    } else {
      base
    }
  }

  private data class ChoiceOptionSpec(
    val value: String,
    val label: String,
    val colorHex: String,
    val imageUri: String,
  )

  private data class ChoiceQuestionSpec(
    val name: String,
    val label: String,
    val hint: String,
    val isMultiSelect: Boolean,
    val defaultValue: String = "",
    val options: List<ChoiceOptionSpec>,
  )

  private fun choiceQuestionsForForm(formId: String): List<ChoiceQuestionSpec> =
    when (formId) {
      "form-eudr-baseline" ->
        listOf(
          ChoiceQuestionSpec(
            name = "commodity_type",
            label = "Primary Regulated Commodity Crop",
            hint = "Select the EUDR Annex I commodity produced on this parcel.",
            isMultiSelect = false,
            defaultValue = "",
            options =
              listOf(
                ChoiceOptionSpec(
                  value = "coffee",
                  label = "Coffee (Coffea arabica / canephora)",
                  colorHex = "",
                  imageUri = "jr://images/commodity_type-coffee.png",
                ),
                ChoiceOptionSpec(
                  value = "cocoa",
                  label = "Cocoa (Theobroma cacao)",
                  colorHex = "",
                  imageUri = "jr://images/commodity_type-cocoa.png",
                ),
                ChoiceOptionSpec(
                  value = "timber",
                  label = "Timber / Agroforestry Wood",
                  colorHex = "",
                  imageUri = "jr://images/commodity_type-timber.png",
                ),
              ),
          ),
          ChoiceQuestionSpec(
            name = "certification_schemes",
            label = "Active Sustainability Certifications",
            hint = "Select all sustainability standards verified for this parcel.",
            isMultiSelect = true,
            defaultValue = "",
            options =
              listOf(
                ChoiceOptionSpec(
                  value = "rainforest_alliance",
                  label = "Rainforest Alliance / UTZ",
                  colorHex = "#2E7D32",
                  imageUri = "",
                ),
                ChoiceOptionSpec(
                  value = "organic_eu",
                  label = "EU / USDA Certified Organic",
                  colorHex = "#689F38",
                  imageUri = "",
                ),
                ChoiceOptionSpec(
                  value = "fairtrade",
                  label = "Fairtrade International",
                  colorHex = "#0288D1",
                  imageUri = "",
                ),
                ChoiceOptionSpec(
                  value = "carbon_verified",
                  label = "Plan Vivo / Gold Standard Carbon",
                  colorHex = "#00897B",
                  imageUri = "",
                ),
              ),
          ),
        )
      "form-household-interview" ->
        listOf(
          ChoiceQuestionSpec(
            name = "clean_cooking_energy",
            label = "Primary Household Cooking Fuel / Stove Type",
            hint = "Select the main stove technology used by the household.",
            isMultiSelect = false,
            defaultValue = "",
            options =
              listOf(
                ChoiceOptionSpec(
                  value = "improved_cookstove",
                  label = "Improved Fuelwood Rocket / Clay Cookstove",
                  colorHex = "",
                  imageUri = "jr://images/clean_cooking_energy-improved_cookstove.png",
                ),
                ChoiceOptionSpec(
                  value = "lpg_biogas",
                  label = "LPG / Biogas / Electric Clean Stove",
                  colorHex = "",
                  imageUri = "jr://images/clean_cooking_energy-lpg_biogas.png",
                ),
                ChoiceOptionSpec(
                  value = "three_stone_fire",
                  label = "Traditional Three-Stone Firewood Hearth",
                  colorHex = "",
                  imageUri = "jr://images/clean_cooking_energy-three_stone_fire.png",
                ),
              ),
          ),
          ChoiceQuestionSpec(
            name = "primary_livelihood_sources",
            label = "Household Livelihood & Income Sources",
            hint = "Select all income sources contributing to the household.",
            isMultiSelect = true,
            defaultValue = "",
            options =
              listOf(
                ChoiceOptionSpec(
                  value = "agroforestry_cocoa",
                  label = "Shade Coffee / Cocoa Agroforestry",
                  colorHex = "#388E3C",
                  imageUri = "",
                ),
                ChoiceOptionSpec(
                  value = "annual_food_crops",
                  label = "Food Crops (Maize, Beans, Cassava)",
                  colorHex = "#FBC02D",
                  imageUri = "",
                ),
                ChoiceOptionSpec(
                  value = "beekeeping",
                  label = "Apiculture / Forest Honey & Non-Timber Products",
                  colorHex = "#FFA000",
                  imageUri = "",
                ),
                ChoiceOptionSpec(
                  value = "tree_nursery_pes",
                  label = "Tree Seedling Nursery / Restoration Payments (PES)",
                  colorHex = "#00897B",
                  imageUri = "",
                ),
              ),
          ),
        )
      "form-shade-canopy-audit" ->
        listOf(
          ChoiceQuestionSpec(
            name = "canopy_stratum",
            label = "Dominant Shade Canopy Stratum",
            hint = "Select the vertical canopy stratum of the audited shade stand.",
            isMultiSelect = false,
            defaultValue = "",
            options =
              listOf(
                ChoiceOptionSpec(
                  value = "emergent",
                  label = "Emergent Native Overstory (> 18m)",
                  colorHex = "",
                  imageUri = "jr://images/canopy_stratum-emergent.png",
                ),
                ChoiceOptionSpec(
                  value = "mid_canopy",
                  label = "Mid-Canopy Shade Stratum (8-18m)",
                  colorHex = "",
                  imageUri = "jr://images/canopy_stratum-mid_canopy.png",
                ),
                ChoiceOptionSpec(
                  value = "understory",
                  label = "Understory Coffee & Shrub Stratum (< 8m)",
                  colorHex = "",
                  imageUri = "jr://images/canopy_stratum-understory.png",
                ),
              ),
          ),
          ChoiceQuestionSpec(
            name = "soil_moisture_class",
            label = "Topsoil Moisture Regime",
            hint = "Assess soil moisture beneath the leaf-litter layer.",
            isMultiSelect = false,
            defaultValue = "",
            options =
              listOf(
                ChoiceOptionSpec(
                  value = "dry",
                  label = "Dry / Cracked Surface",
                  colorHex = "#D84315",
                  imageUri = "",
                ),
                ChoiceOptionSpec(
                  value = "moist_well_drained",
                  label = "Moist & Well-Drained Loam",
                  colorHex = "#2E7D32",
                  imageUri = "",
                ),
                ChoiceOptionSpec(
                  value = "saturated",
                  label = "Waterlogged / Hydric",
                  colorHex = "#0277BD",
                  imageUri = "",
                ),
              ),
          ),
        )
      "form-deforestation-alert" ->
        listOf(
          ChoiceQuestionSpec(
            name = "alert_cause",
            label = "Verified Canopy Disturbance Cause",
            hint = "Select the primary ground-truthed driver of the GLAD alert.",
            isMultiSelect = false,
            defaultValue = "",
            options =
              listOf(
                ChoiceOptionSpec(
                  value = "windthrow",
                  label = "Natural Windthrow / Storm Branch Fall",
                  colorHex = "#0288D1",
                  imageUri = "",
                ),
                ChoiceOptionSpec(
                  value = "pruning",
                  label = "Scheduled Shade Tree Pollarding / Pruning",
                  colorHex = "#689F38",
                  imageUri = "",
                ),
                ChoiceOptionSpec(
                  value = "clearing",
                  label = "Unauthorized Canopy Clearing / Encroachment",
                  colorHex = "#C62828",
                  imageUri = "",
                ),
              ),
          ),
          ChoiceQuestionSpec(
            name = "disturbance_indicators",
            label = "Observed Forest Disturbance Signs",
            hint = "Select all disturbance indicators observed within the alert footprint.",
            isMultiSelect = true,
            defaultValue = "",
            options =
              listOf(
                ChoiceOptionSpec(
                  value = "none",
                  label = "None — Undisturbed Stand",
                  colorHex = "",
                  imageUri = "jr://images/disturbance_indicators-none.png",
                ),
                ChoiceOptionSpec(
                  value = "selective_logging",
                  label = "Selective Pole / Timber Cutting",
                  colorHex = "",
                  imageUri = "jr://images/disturbance_indicators-selective_logging.png",
                ),
                ChoiceOptionSpec(
                  value = "fire_charcoal",
                  label = "Charcoal Kiln / Fire Scars",
                  colorHex = "",
                  imageUri = "jr://images/disturbance_indicators-fire_charcoal.png",
                ),
              ),
          ),
        )
      "form-water-quality" ->
        listOf(
          ChoiceQuestionSpec(
            name = "effluent_treatment",
            label = "Active Effluent Treatment Stage",
            hint = "Select the primary wastewater treatment system operating today.",
            isMultiSelect = false,
            defaultValue = "",
            options =
              listOf(
                ChoiceOptionSpec(
                  value = "vetiver_wetland",
                  label = "Constructed Vetiver Grass Wetland",
                  colorHex = "",
                  imageUri = "jr://images/effluent_treatment-vetiver_wetland.png",
                ),
                ChoiceOptionSpec(
                  value = "settling_pond",
                  label = "Anaerobic Settling & Neutralization Lagoons",
                  colorHex = "",
                  imageUri = "jr://images/effluent_treatment-settling_pond.png",
                ),
                ChoiceOptionSpec(
                  value = "untreated",
                  label = "Direct Discharge / Overflow Observed",
                  colorHex = "",
                  imageUri = "jr://images/effluent_treatment-untreated.png",
                ),
              ),
          )
        )
      else ->
        listOf(
          ChoiceQuestionSpec(
            name = "pest_or_disease",
            label = "Observed Pest or Disease Category",
            hint = "Select the primary biological observation at this point.",
            isMultiSelect = false,
            defaultValue = "",
            options =
              listOf(
                ChoiceOptionSpec(
                  value = "berry_borer",
                  label = "Coffee Berry Borer (Hypothenemus hampei)",
                  colorHex = "",
                  imageUri = "jr://images/pest_or_disease-berry_borer.png",
                ),
                ChoiceOptionSpec(
                  value = "leaf_rust",
                  label = "Coffee Leaf Rust (Hemileia vastatrix)",
                  colorHex = "",
                  imageUri = "jr://images/pest_or_disease-leaf_rust.png",
                ),
                ChoiceOptionSpec(
                  value = "healthy",
                  label = "Beneficial Pollinators / Healthy Foliage",
                  colorHex = "",
                  imageUri = "jr://images/pest_or_disease-healthy.png",
                ),
              ),
          ),
          ChoiceQuestionSpec(
            name = "land_use",
            label = "Surrounding Land Cover Type",
            hint = "Select the land cover class immediately surrounding the sighting.",
            isMultiSelect = false,
            defaultValue = "",
            options =
              listOf(
                ChoiceOptionSpec(
                  value = "agroforestry_shade",
                  label = "Agroforestry / Shade-Grown Tree Crop",
                  colorHex = "#689F38",
                  imageUri = "",
                ),
                ChoiceOptionSpec(
                  value = "primary_forest",
                  label = "Primary / Intact Natural Forest",
                  colorHex = "#1B5E20",
                  imageUri = "",
                ),
                ChoiceOptionSpec(
                  value = "wetland_riparian",
                  label = "Wetland / Riparian Buffer",
                  colorHex = "#0288D1",
                  imageUri = "",
                ),
              ),
          ),
        )
    }

  fun builtInFallbackFormForForm(form: FormPreviewItem): FormDefinition {
    when (form.id) {
      "form-single-point-land-use" ->
        return WorkbenchExampleForm.SINGLE_POINT_LAND_USE.formDefinition
      "form-sample-plots-forest" ->
        return WorkbenchExampleForm.SAMPLE_PLOTS_FOREST_ASSESSMENT.formDefinition
      "form-commodity-perimeter-center" ->
        return WorkbenchExampleForm.COMMODITY_PERIMETER_AND_CENTER.formDefinition
      "form-household-past-individuals" ->
        return WorkbenchExampleForm.HOUSEHOLD_SURVEY_PAST_INDIVIDUALS.formDefinition
      "form-coffee-parcel" -> return WorkbenchExampleForm.ALL_FIELD_TYPES.formDefinition
    }
    val safeFormId = form.id.replace('-', '_')
    val choiceQuestions = choiceQuestionsForForm(form.id)
    val choiceFields = choiceQuestions.map { q ->
      FieldDefinition(
        name = q.name,
        type = if (q.isMultiSelect) DataType.TYPE_SELECT_MULTIPLE else DataType.TYPE_SELECT_ONE,
      )
    }
    val choiceBindings = choiceQuestions.map { q ->
      FieldBinding(
        field_path = "/data/${q.name}",
        type = if (q.isMultiSelect) DataType.TYPE_SELECT_MULTIPLE else DataType.TYPE_SELECT_ONE,
        required_expression = "true()",
      )
    }
    val secondaryInstances = choiceQuestions.map { q ->
      SecondaryInstance(
        id = q.name,
        rows =
          q.options.mapIndexed { index, opt ->
            val cols = mutableMapOf("itextId" to "${q.name}-$index", "name" to opt.value)
            if (opt.colorHex.isNotBlank()) {
              cols["color"] = opt.colorHex
            }
            secondaryInstanceRow(cols)
          },
      )
    }
    val itextStrings = buildMap {
      choiceQuestions.forEach { q ->
        q.options.forEachIndexed { index, opt ->
          put(
            "${q.name}-$index",
            LocalizedString(
              value_ = opt.label,
              media = if (opt.imageUri.isNotBlank()) MediaRef(image_uri = opt.imageUri) else null,
            ),
          )
        }
      }
    }
    val choiceComponents = choiceQuestions.map { q ->
      ViewComponent(
        control =
          ControlDef(
            field_ref = "/data/${q.name}",
            type =
              if (q.isMultiSelect) ControlType.CONTROL_SELECT_MULTIPLE
              else ControlType.CONTROL_SELECT_ONE,
            label = LabelDef(text = q.label),
            hint = LabelDef(text = q.hint),
            itemset =
              ItemsetDef(
                instance_id = q.name,
                nodeset_path = "/root/item",
                value_ref = "name",
                label_ref = "jr:itext(itextId)",
              ),
          )
      )
    }
    return FormDefinition(
      FormDef(
        form_id = safeFormId,
        title = form.title,
        version = form.version,
        model =
          ModelDef(
            primary_instance =
              PrimaryInstance(
                record_schema =
                  RecordSchema(
                    name = "data",
                    title = form.title,
                    fields =
                      listOf(
                        FieldDefinition(
                          name = "meta",
                          type = DataType.TYPE_MESSAGE,
                          fields =
                            listOf(
                              FieldDefinition(
                                name = "instanceID",
                                type = DataType.TYPE_STRING,
                              )
                            ),
                        ),
                        FieldDefinition(
                          name = "organizer_action_cta",
                          type = DataType.TYPE_STRING,
                        ),
                      ) +
                        choiceFields +
                        listOf(
                          FieldDefinition(
                            name = "collector_observation",
                            type = DataType.TYPE_STRING,
                          ),
                          FieldDefinition(
                            name = "canopy_or_parcel_metric",
                            type = DataType.TYPE_INT32,
                          ),
                        ),
                  ),
                default_values =
                  RecordNode(
                    fields =
                      mapOf(
                        "meta" to
                          FieldValue(
                            node_value =
                              RecordNode(
                                fields =
                                  mapOf(
                                    "instanceID" to
                                      FieldValue(scalar_value = TypedValue(string_value = ""))
                                  )
                              )
                          )
                      )
                  ),
              ),
            secondary_instances = secondaryInstances,
            bindings =
              listOf(
                FieldBinding(
                  field_path = "/data/meta/instanceID",
                  type = DataType.TYPE_STRING,
                  preload = PreloadType.PRELOAD_UID,
                ),
                FieldBinding(
                  field_path = "/data/organizer_action_cta",
                  type = DataType.TYPE_STRING,
                  required_expression = "true()",
                ),
              ) +
                choiceBindings +
                listOf(
                  FieldBinding(
                    field_path = "/data/collector_observation",
                    type = DataType.TYPE_STRING,
                  ),
                  FieldBinding(
                    field_path = "/data/canopy_or_parcel_metric",
                    type = DataType.TYPE_INT32,
                  ),
                ),
            translations =
              TranslationCatalog(
                languages =
                  listOf(
                    LanguageTranslation(
                      language = "default",
                      is_default = true,
                      strings = itextStrings,
                    )
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
                      field_ref = "/data/organizer_action_cta",
                      type = ControlType.CONTROL_INPUT,
                      label = LabelDef(text = "Completed Form Action (${form.ctaLabel})"),
                    )
                )
              ) +
                choiceComponents +
                listOf(
                  ViewComponent(
                    control =
                      ControlDef(
                        field_ref = "/data/collector_observation",
                        type = ControlType.CONTROL_INPUT,
                        label = LabelDef(text = "Field Observation Notes"),
                      )
                  ),
                  ViewComponent(
                    control =
                      ControlDef(
                        field_ref = "/data/canopy_or_parcel_metric",
                        type = ControlType.CONTROL_INPUT,
                        label = LabelDef(text = "Measured Field Metric / Score (%)"),
                      )
                  ),
                )
          ),
      )
    )
  }

  fun ensureEntityRefStepInFormDef(
    baseFormDef: FormDef,
    form: FormPreviewItem,
    candidateEntities: List<GeospatialEntityItem> = emptyList(),
    defaultSelectedEntityId: String = "",
  ): FormDef {
    if (!form.requiresEntity) return baseFormDef
    val entityRefPaths =
      setOf(
        "/data/target_entity",
        "target_entity",
        "/data/sample_plot_entity",
        "sample_plot_entity",
        "/data/past_individual_id",
        "past_individual_id",
        "/data/primary_respondent_id",
        "primary_respondent_id",
      )
    val hasExistingEntityStep =
      baseFormDef.model?.bindings.orEmpty().any { it.field_path in entityRefPaths } ||
        hasEntityStepInComponents(baseFormDef.view?.components.orEmpty(), entityRefPaths)
    if (hasExistingEntityStep) return baseFormDef

    val choices =
      if (candidateEntities.isNotEmpty()) {
        candidateEntities.map { ent ->
          ChoiceItem(
            value_ = ent.id,
            label = LabelDef(text = "${ent.label} (${ent.geoId})"),
          )
        }
      } else {
        listOf(
          ChoiceItem(
            value_ = "default_feature",
            label = LabelDef(text = "Default Feature"),
          )
        )
      }

    val selectComponent =
      ViewComponent(
        control =
          ControlDef(
            field_ref = "/data/target_entity",
            type = ControlType.CONTROL_SELECT_ONE,
            appearance = "map-select",
            label =
              LabelDef(text = "Select ${form.targetSingularTypeLabel} (${form.targetDatasetName})"),
            choices = choices,
          )
      )

    val existingModel = baseFormDef.model ?: ModelDef()
    val existingPrimary = existingModel.primary_instance ?: PrimaryInstance()
    val existingSchema = existingPrimary.record_schema ?: RecordSchema(name = "data")
    val targetFieldSchema =
      FieldDefinition(
        name = "target_entity",
        type = DataType.TYPE_STRING,
      )
    val updatedSchemaFields =
      if (existingSchema.fields.any { it.name == "target_entity" }) {
        existingSchema.fields
      } else {
        val metaIdx = existingSchema.fields.indexOfFirst { it.name == "meta" }
        if (metaIdx >= 0) {
          existingSchema.fields.take(metaIdx + 1) +
            listOf(targetFieldSchema) +
            existingSchema.fields.drop(metaIdx + 1)
        } else {
          listOf(targetFieldSchema) + existingSchema.fields
        }
      }
    val existingDefaults = existingPrimary.default_values ?: RecordNode()
    val updatedDefaults =
      existingDefaults.copy(
        fields =
          existingDefaults.fields +
            ("target_entity" to
              FieldValue(scalar_value = TypedValue(string_value = defaultSelectedEntityId)))
      )
    val updatedPrimary =
      existingPrimary.copy(
        record_schema = existingSchema.copy(fields = updatedSchemaFields),
        default_values = updatedDefaults,
      )
    val updatedBindings =
      existingModel.bindings +
        FieldBinding(
          field_path = "/data/target_entity",
          type = DataType.TYPE_STRING,
          required_expression = "true()",
        )
    val updatedModel =
      existingModel.copy(primary_instance = updatedPrimary, bindings = updatedBindings)
    val existingView = baseFormDef.view ?: ViewDef()
    val updatedView =
      existingView.copy(components = listOf(selectComponent) + existingView.components)
    return baseFormDef.copy(model = updatedModel, view = updatedView)
  }

  private fun hasEntityStepInComponents(
    components: List<ViewComponent>,
    entityRefPaths: Set<String>,
  ): Boolean = components.any { comp ->
    val ctrl = comp.control
    val grp = comp.group
    val rep = comp.repeat
    when {
      ctrl != null ->
        ctrl.field_ref in entityRefPaths ||
          ctrl.appearance.split(Regex("\\s+")).contains("map-select")
      grp != null -> hasEntityStepInComponents(grp.components, entityRefPaths)
      rep != null -> hasEntityStepInComponents(rep.components, entityRefPaths)
      else -> false
    }
  }
}
