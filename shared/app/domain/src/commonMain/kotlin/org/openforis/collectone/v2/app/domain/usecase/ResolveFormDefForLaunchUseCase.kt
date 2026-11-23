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
import openforis.collectone.v1.forms.LabelDef
import openforis.collectone.v1.forms.ModelDef
import openforis.collectone.v1.forms.PreloadType
import openforis.collectone.v1.forms.PrimaryInstance
import openforis.collectone.v1.forms.RecordNode
import openforis.collectone.v1.forms.RecordSchema
import openforis.collectone.v1.forms.TypedValue
import openforis.collectone.v1.forms.ViewComponent
import openforis.collectone.v1.forms.ViewDef
import org.openforis.collectone.v2.app.domain.model.FormPreviewItem
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.SurveyConfig
import org.openforis.collectone.v2.app.domain.repository.FallbackFormProvider
import org.openforis.collectone.v2.core.forms.model.FormDefinition

/**
 * Domain use case resolving the [FormDef] to execute for a given [FormPreviewItem] and injecting
 * the `entityref` (`/data/target_entity`) map/list selection step when launched without a
 * pre-selected entity.
 *
 * A form's definition comes from the local data store; when the store has none, from
 * [fallbackForms] (sample data and tests only), and otherwise an empty form with no questions.
 */
class ResolveFormDefForLaunchUseCase(
  private val fallbackForms: FallbackFormProvider = FallbackFormProvider.NONE
) {
  operator fun invoke(
    customFormDef: FormDef?,
    form: FormPreviewItem,
    candidateEntities: List<GeospatialEntityItem> = emptyList(),
    defaultSelectedEntityId: String = "",
    includeEntityRefStep: Boolean = false,
    surveyConfig: SurveyConfig? = null,
  ): FormDef {
    val base = customFormDef ?: formDefinitionFor(form, surveyConfig).proto
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

  /**
   * [form]'s definition: stored in [surveyConfig], else supplied by [fallbackForms], else an empty
   * form with no questions.
   */
  fun formDefinitionFor(form: FormPreviewItem, surveyConfig: SurveyConfig?): FormDefinition =
    surveyConfig?.formsById?.get(form.id)
      ?: fallbackForms.fallbackFormFor(form)
      ?: emptyFormDefinition(form)

  /** A form titled like [form] with no questions, only the `meta/instanceID` every record has. */
  private fun emptyFormDefinition(form: FormPreviewItem): FormDefinition =
    FormDefinition(
      FormDef(
        form_id = form.id.replace('-', '_'),
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
                              FieldDefinition(name = "instanceID", type = DataType.TYPE_STRING)
                            ),
                        )
                      ),
                  )
              ),
            bindings =
              listOf(
                FieldBinding(
                  field_path = "/data/meta/instanceID",
                  type = DataType.TYPE_STRING,
                  preload = PreloadType.PRELOAD_UID,
                )
              ),
          ),
        view = ViewDef(),
      )
    )

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
            label = LabelDef(text = selectTargetLabel(form)),
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

  /**
   * Label of the synthesized feature-selection question, e.g. `Select Coffee Parcel (Smallholder
   * Coffee Parcels)`, or `Select Smallholder Coffee Parcels` when the survey names no feature type.
   * It is stored in the compiled form like the form's other question labels, so it stays English.
   */
  private fun selectTargetLabel(form: FormPreviewItem): String =
    if (form.targetTypeNoun.isBlank()) "Select ${form.targetDatasetName}"
    else "Select ${form.targetTypeNoun} (${form.targetDatasetName})"
}
