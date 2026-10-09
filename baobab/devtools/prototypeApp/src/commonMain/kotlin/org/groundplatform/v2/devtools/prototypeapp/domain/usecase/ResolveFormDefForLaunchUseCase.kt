/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.devtools.prototypeapp.domain.usecase

import groundplatform.v2.forms.ChoiceItem
import groundplatform.v2.forms.ControlDef
import groundplatform.v2.forms.ControlType
import groundplatform.v2.forms.DataType
import groundplatform.v2.forms.FieldBinding
import groundplatform.v2.forms.FieldDefinition
import groundplatform.v2.forms.FieldValue
import groundplatform.v2.forms.FormDef
import groundplatform.v2.forms.LabelDef
import groundplatform.v2.forms.ModelDef
import groundplatform.v2.forms.PreloadType
import groundplatform.v2.forms.PrimaryInstance
import groundplatform.v2.forms.RecordNode
import groundplatform.v2.forms.RecordSchema
import groundplatform.v2.forms.TypedValue
import groundplatform.v2.forms.ViewComponent
import groundplatform.v2.forms.ViewDef
import org.groundplatform.v2.core.forms.model.FormDefinition
import org.groundplatform.v2.core.forms.ui.WorkbenchExampleForm
import org.groundplatform.v2.devtools.prototypeapp.domain.model.FormPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.GeospatialEntityItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyConfig

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
    val ctaDefault = "Verified in field (${form.ctaLabel})"
    val observationDefault = "Field verification complete"
    val metricDefault = 94
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
                          ),
                        "organizer_action_cta" to
                          FieldValue(scalar_value = TypedValue(string_value = ctaDefault)),
                        "collector_observation" to
                          FieldValue(scalar_value = TypedValue(string_value = observationDefault)),
                        "canopy_or_parcel_metric" to
                          FieldValue(scalar_value = TypedValue(int32_value = metricDefault)),
                      )
                  ),
              ),
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
                FieldBinding(
                  field_path = "/data/collector_observation",
                  type = DataType.TYPE_STRING,
                ),
                FieldBinding(
                  field_path = "/data/canopy_or_parcel_metric",
                  type = DataType.TYPE_INT32,
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
                      field_ref = "/data/organizer_action_cta",
                      type = ControlType.CONTROL_INPUT,
                      label = LabelDef(text = "Completed Form Action (${form.ctaLabel})"),
                    )
                ),
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
