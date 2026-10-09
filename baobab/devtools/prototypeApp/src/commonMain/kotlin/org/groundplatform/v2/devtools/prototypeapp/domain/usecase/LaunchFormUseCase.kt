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

import groundplatform.v2.forms.DataType
import groundplatform.v2.forms.FormDef
import org.groundplatform.v2.core.forms.ui.FormWizardController
import org.groundplatform.v2.core.forms.ui.FormWizardStep
import org.groundplatform.v2.devtools.prototypeapp.domain.model.FormPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.GeospatialEntityItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyConfig

/**
 * Opens a Form for data collection: resolves the [FormDef] to run (the workbench's custom
 * definition or the Form's built-in fallback, with the `entityref` step added when the collector
 * still has to pick the target map feature) and pre-populates the Form's entity-reference questions
 * with the target feature's ID.
 *
 * The result is the [FormWizardController] that drives the shared form runner. The controller is UI
 * infrastructure from `shared/ui` (`org.groundplatform.v2.core.forms.ui`), the one non-domain value
 * the data collection state holds; this use case builds it so the ViewModel doesn't have to know
 * which questions reference entities.
 */
class LaunchFormUseCase(
  private val resolveFormDef: ResolveFormDefForLaunchUseCase = ResolveFormDefForLaunchUseCase()
) {
  /**
   * Builds the wizard for [form].
   *
   * With an [entity], the Form opens on that map feature: every entity-reference question
   * ([ENTITY_REFERENCE_PATHS]) the Form has is filled with its ID, and no `entityref` step is
   * added. Without one, a Form that [FormPreviewItem.requiresEntity] gets the `entityref` step over
   * [candidateEntities] (when [includeEntityRefStep]) so the collector picks the feature in the
   * Form; [defaultSelectedEntityId] pre-selects one of them (e.g. when re-launching an open Form
   * whose feature was already picked).
   */
  operator fun invoke(
    customFormDef: FormDef?,
    form: FormPreviewItem,
    entity: GeospatialEntityItem? = null,
    candidateEntities: List<GeospatialEntityItem> = emptyList(),
    includeEntityRefStep: Boolean = entity == null,
    defaultSelectedEntityId: String = entity?.id.orEmpty(),
    surveyConfig: SurveyConfig? = null,
  ): FormWizardController {
    val formDef =
      resolveFormDef(
        customFormDef = customFormDef,
        form = form,
        candidateEntities = candidateEntities,
        defaultSelectedEntityId = defaultSelectedEntityId,
        includeEntityRefStep = includeEntityRefStep && entity == null,
        surveyConfig = surveyConfig,
      )
    val controller = FormWizardController(formDef = formDef)
    if (entity != null) prepopulateEntityReference(controller, entity.id)
    return controller
  }

  /**
   * Fills every entity-reference question of [controller]'s Form ([ENTITY_REFERENCE_PATHS]) that
   * the Form actually has with [entityId]. Questions the Form doesn't have are left alone.
   */
  fun prepopulateEntityReference(controller: FormWizardController, entityId: String) {
    val fields = controller.formState.fieldStates
    ENTITY_REFERENCE_PATHS.filter { it in fields }.forEach { controller.updateString(it, entityId) }
  }

  companion object {
    /**
     * Canonical XForms `entityref` nodeset path (`/data/target_entity`) representing the geospatial
     * entity reference step (`select_one_from_file <dataset>.csv` / `appearance="map-select"`) when
     * a form requires a target geospatial entity.
     */
    const val ENTITY_REF_FIELD_PATH: String = "/data/target_entity"

    /**
     * Question paths the sample Forms use to reference their target map feature, filled in when a
     * Form opens on a feature or the collector picks one at the `entityref` step.
     */
    val ENTITY_REFERENCE_PATHS: List<String> =
      listOf(
        ENTITY_REF_FIELD_PATH,
        "/data/sample_plot_entity",
        "/data/past_individual_id",
        "/data/primary_respondent_id",
      )

    /**
     * Returns `true` if [step] represents an `entityref` question step (e.g. bound to
     * [ENTITY_REF_FIELD_PATH] `/data/target_entity`, `/data/entity_id`, or configured with
     * `appearance="map-select"` / `"entityref"`).
     */
    fun isEntityRefStep(step: FormWizardStep?): Boolean =
      controlsOf(step).any { ctrl ->
        ctrl.canonicalPath == ENTITY_REF_FIELD_PATH ||
          ctrl.canonicalPath.endsWith("/target_entity") ||
          ctrl.canonicalPath.endsWith("/entity_id") ||
          ctrl.canonicalPath.endsWith("/sample_plot_entity") ||
          ctrl.canonicalPath.endsWith("/past_individual_id") ||
          ctrl.canonicalPath.endsWith("/primary_respondent_id") ||
          ctrl.appearance.contains("map-select", ignoreCase = true) ||
          ctrl.appearance.contains("entityref", ignoreCase = true)
      }

    /**
     * Returns `true` if [step] contains a geometry (`geopoint`, `geotrace`, or `geoshape`) question
     * control.
     */
    fun isGeometryStep(step: FormWizardStep?): Boolean =
      controlsOf(step).any {
        val dt = it.fieldState.dataType
        dt == DataType.TYPE_GEOPOINT || dt == DataType.TYPE_GEOTRACE || dt == DataType.TYPE_GEOSHAPE
      }

    private fun controlsOf(step: FormWizardStep?) =
      when (step) {
        is FormWizardStep.QuestionStep -> listOf(step.control)
        is FormWizardStep.FieldListGroupStep -> step.controls
        else -> emptyList()
      }
  }
}
