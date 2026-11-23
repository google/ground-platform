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

import openforis.collectone.v1.forms.FormDef
import org.openforis.collectone.v2.app.domain.model.FormPreviewItem
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.SurveyConfig

/**
 * Opens a Form for data collection: resolves the [FormDef] to run (the workbench's custom
 * definition or the Form's built-in fallback, with the `entityref` step added when the collector
 * still has to pick the target map feature) and names the map feature the Form's entity-reference
 * questions start with.
 *
 * The result is a [FormLaunch]: the Form to run and the map feature its entity-reference questions
 * start with. The presentation layer builds the form runner from it.
 */
class LaunchFormUseCase(
  private val resolveFormDef: ResolveFormDefForLaunchUseCase = ResolveFormDefForLaunchUseCase()
) {
  /**
   * Prepares [form] for data collection.
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
  ): FormLaunch {
    val formDef =
      resolveFormDef(
        customFormDef = customFormDef,
        form = form,
        candidateEntities = candidateEntities,
        defaultSelectedEntityId = defaultSelectedEntityId,
        includeEntityRefStep = includeEntityRefStep && entity == null,
        surveyConfig = surveyConfig,
      )
    return FormLaunch(formDef = formDef, entityId = entity?.id)
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
  }
}

/**
 * A Form ready for data collection: the [formDef] to run and, when it opens on a map feature, the
 * [entityId] every entity-reference question ([LaunchFormUseCase.ENTITY_REFERENCE_PATHS]) starts
 * with.
 */
data class FormLaunch(val formDef: FormDef, val entityId: String? = null)
