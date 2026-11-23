/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.web.ui.text

import org.jetbrains.compose.resources.StringResource
import org.openforis.collectone.v2.web.domain.model.editor.CreateSurveyWizardStep
import org.openforis.collectone.v2.web.domain.model.editor.SurveyCollectionMode
import org.openforis.collectone.v2.web.domain.model.editor.WizardMapFeatureSource
import org.openforis.collectone.v2.web.domain.model.editor.WizardStartMethod
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.wizard_mode_both
import org.openforis.collectone.v2.web.resources.wizard_mode_both_summary
import org.openforis.collectone.v2.web.resources.wizard_mode_field_survey
import org.openforis.collectone.v2.web.resources.wizard_mode_field_survey_summary
import org.openforis.collectone.v2.web.resources.wizard_mode_visual_classification
import org.openforis.collectone.v2.web.resources.wizard_mode_visual_classification_summary
import org.openforis.collectone.v2.web.resources.wizard_source_field_freeform
import org.openforis.collectone.v2.web.resources.wizard_source_field_freeform_description
import org.openforis.collectone.v2.web.resources.wizard_source_import_file
import org.openforis.collectone.v2.web.resources.wizard_source_import_file_description
import org.openforis.collectone.v2.web.resources.wizard_source_sample_plots
import org.openforis.collectone.v2.web.resources.wizard_source_sample_plots_description
import org.openforis.collectone.v2.web.resources.wizard_start_copy_existing
import org.openforis.collectone.v2.web.resources.wizard_start_copy_existing_description
import org.openforis.collectone.v2.web.resources.wizard_start_from_scratch
import org.openforis.collectone.v2.web.resources.wizard_start_from_scratch_description
import org.openforis.collectone.v2.web.resources.wizard_start_import
import org.openforis.collectone.v2.web.resources.wizard_start_import_description
import org.openforis.collectone.v2.web.resources.wizard_step_forms
import org.openforis.collectone.v2.web.resources.wizard_step_forms_subtitle
import org.openforis.collectone.v2.web.resources.wizard_step_map_features
import org.openforis.collectone.v2.web.resources.wizard_step_map_features_subtitle
import org.openforis.collectone.v2.web.resources.wizard_step_overview
import org.openforis.collectone.v2.web.resources.wizard_step_overview_subtitle
import org.openforis.collectone.v2.web.resources.wizard_step_purpose
import org.openforis.collectone.v2.web.resources.wizard_step_purpose_subtitle
import org.openforis.collectone.v2.web.resources.wizard_step_sharing
import org.openforis.collectone.v2.web.resources.wizard_step_sharing_subtitle
import org.openforis.collectone.v2.web.resources.wizard_step_survey_area
import org.openforis.collectone.v2.web.resources.wizard_step_survey_area_subtitle

/** The step's name in the wizard's stepper. */
val CreateSurveyWizardStep.labelRes: StringResource
  get() =
    when (this) {
      CreateSurveyWizardStep.OVERVIEW -> Res.string.wizard_step_overview
      CreateSurveyWizardStep.MODE_AND_PURPOSE -> Res.string.wizard_step_purpose
      CreateSurveyWizardStep.SURVEY_AREA -> Res.string.wizard_step_survey_area
      CreateSurveyWizardStep.MAP_FEATURES -> Res.string.wizard_step_map_features
      CreateSurveyWizardStep.FORM_QUESTIONS -> Res.string.wizard_step_forms
      CreateSurveyWizardStep.SHARING -> Res.string.wizard_step_sharing
    }

/** What the step covers, shown under its name. */
val CreateSurveyWizardStep.subtitleRes: StringResource
  get() =
    when (this) {
      CreateSurveyWizardStep.OVERVIEW -> Res.string.wizard_step_overview_subtitle
      CreateSurveyWizardStep.MODE_AND_PURPOSE -> Res.string.wizard_step_purpose_subtitle
      CreateSurveyWizardStep.SURVEY_AREA -> Res.string.wizard_step_survey_area_subtitle
      CreateSurveyWizardStep.MAP_FEATURES -> Res.string.wizard_step_map_features_subtitle
      CreateSurveyWizardStep.FORM_QUESTIONS -> Res.string.wizard_step_forms_subtitle
      CreateSurveyWizardStep.SHARING -> Res.string.wizard_step_sharing_subtitle
    }

val WizardStartMethod.labelRes: StringResource
  get() =
    when (this) {
      WizardStartMethod.FROM_SCRATCH -> Res.string.wizard_start_from_scratch
      WizardStartMethod.COPY_EXISTING -> Res.string.wizard_start_copy_existing
      WizardStartMethod.IMPORT_XFORMS -> Res.string.wizard_start_import
    }

val WizardStartMethod.descriptionRes: StringResource
  get() =
    when (this) {
      WizardStartMethod.FROM_SCRATCH -> Res.string.wizard_start_from_scratch_description
      WizardStartMethod.COPY_EXISTING -> Res.string.wizard_start_copy_existing_description
      WizardStartMethod.IMPORT_XFORMS -> Res.string.wizard_start_import_description
    }

val SurveyCollectionMode.labelRes: StringResource
  get() =
    when (this) {
      SurveyCollectionMode.FIELD_SURVEY -> Res.string.wizard_mode_field_survey
      SurveyCollectionMode.VISUAL_CLASSIFICATION -> Res.string.wizard_mode_visual_classification
      SurveyCollectionMode.BOTH -> Res.string.wizard_mode_both
    }

val SurveyCollectionMode.summaryRes: StringResource
  get() =
    when (this) {
      SurveyCollectionMode.FIELD_SURVEY -> Res.string.wizard_mode_field_survey_summary
      SurveyCollectionMode.VISUAL_CLASSIFICATION ->
        Res.string.wizard_mode_visual_classification_summary
      SurveyCollectionMode.BOTH -> Res.string.wizard_mode_both_summary
    }

val WizardMapFeatureSource.labelRes: StringResource
  get() =
    when (this) {
      WizardMapFeatureSource.FIELD_FREEFORM -> Res.string.wizard_source_field_freeform
      WizardMapFeatureSource.SAMPLE_PLOTS -> Res.string.wizard_source_sample_plots
      WizardMapFeatureSource.IMPORT_FILE -> Res.string.wizard_source_import_file
    }

val WizardMapFeatureSource.descriptionRes: StringResource
  get() =
    when (this) {
      WizardMapFeatureSource.FIELD_FREEFORM -> Res.string.wizard_source_field_freeform_description
      WizardMapFeatureSource.SAMPLE_PLOTS -> Res.string.wizard_source_sample_plots_description
      WizardMapFeatureSource.IMPORT_FILE -> Res.string.wizard_source_import_file_description
    }
