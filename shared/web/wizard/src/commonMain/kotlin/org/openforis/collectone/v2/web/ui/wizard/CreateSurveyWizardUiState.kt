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
package org.openforis.collectone.v2.web.ui.wizard

import org.jetbrains.compose.resources.StringResource
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.PurposePack
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.editor.CollectEarthProjectImport
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.ImportedForm
import org.openforis.collectone.v2.app.domain.model.editor.PlotReviewSetup
import org.openforis.collectone.v2.app.domain.model.editor.SurveyArea
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorForm
import org.openforis.collectone.v2.app.domain.usecase.ResolvedLibrary
import org.openforis.collectone.v2.app.ui.text.UiText
import org.openforis.collectone.v2.web.domain.model.editor.CareDefaultsReason
import org.openforis.collectone.v2.web.domain.model.editor.CreateSurveyWizardStep
import org.openforis.collectone.v2.web.domain.model.editor.SurveyCollectionMode
import org.openforis.collectone.v2.web.domain.model.editor.SurveyWizardRules
import org.openforis.collectone.v2.web.domain.model.editor.WizardMapFeatureSource
import org.openforis.collectone.v2.web.domain.model.editor.WizardStartMethod
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.wizard_validation_copy_source_required
import org.openforis.collectone.v2.web.resources.wizard_validation_form_variant_incompatible
import org.openforis.collectone.v2.web.resources.wizard_validation_import_file_required
import org.openforis.collectone.v2.web.resources.wizard_validation_other_purpose_required
import org.openforis.collectone.v2.web.resources.wizard_validation_purpose_required
import org.openforis.collectone.v2.web.resources.wizard_validation_survey_area_required
import org.openforis.collectone.v2.web.resources.wizard_validation_title_required

/** Live impact and export readiness summary displayed in Step 5 and Step 6 of the wizard. */
typealias WizardImpactReadiness =
  org.openforis.collectone.v2.web.domain.model.editor.WizardImpactReadiness

/**
 * Immutable UI state for the full-page 6-step Collect One Survey Creation Wizard
 * (`CreateSurveyWizardPage`).
 */
data class CreateSurveyWizardUiState(
  val currentStep: CreateSurveyWizardStep = CreateSurveyWizardStep.OVERVIEW,
  val workingSurveyId: String = "",
  val previousActiveSurveyId: String = "",
  val title: String = "",
  val showOverviewValidationErrors: Boolean = false,
  val description: String = "",
  val organizationId: String? = null,
  val startMethod: WizardStartMethod = WizardStartMethod.FROM_SCRATCH,
  val copySourceSurveyId: String? = null,
  val importedXFormsFileName: String? = null,
  val importedXForms: ImportedForm? = null,
  val importedCepProjects: List<CollectEarthProjectImport> = emptyList(),
  val selectedCepProject: CollectEarthProjectImport? = null,
  /** Why importing or finishing failed, shown on the Overview and Sharing steps. */
  val importErrorMessage: UiText? = null,
  /**
   * Whether finishing is saving the survey (its definition, map features, and sample plots) and
   * waiting for the server to accept it; the wizard stays open until it has.
   */
  val isFinishing: Boolean = false,
  val collectionMode: SurveyCollectionMode = SurveyCollectionMode.FIELD_SURVEY,
  val purposeIds: List<String> = emptyList(),
  val otherPurpose: String = "",
  val showPurposeValidationErrors: Boolean = false,
  val surveyArea: SurveyArea? = null,
  val showSurveyAreaValidationErrors: Boolean = false,
  /**
   * Whether any Form in the working draft has questions that are incompatible with its selected
   * Form variants (such as GPS-only geometry questions on a web Form, or geometry/media questions
   * when "Open in Google Earth" is enabled).
   */
  val hasFormVariantIncompatibility: Boolean = false,
  val showFormQuestionsValidationErrors: Boolean = false,
  val validationAttemptCount: Int = 0,
  val mapFeatureSource: WizardMapFeatureSource = WizardMapFeatureSource.FIELD_FREEFORM,
  /**
   * Whether [mapFeatureSource] was already decided before Step 4, by the selected Purpose Packs
   * (e.g. LULC training data calls for sample plots) or by the collection mode the organizer
   * picked, rather than being the fallback default. Step 4 then shows it collapsed.
   */
  val mapFeatureSourcePreselected: Boolean = false,
  val eudrPlotsUnderFourHaConfirmed: Boolean = false,
  val collectorConsentRequired: Boolean = false,
  val collectorConsentText: String = "",
  val existingSurveys: List<SurveyPreviewItem> = emptyList(),
  val memberOrganizations: List<Organization> = emptyList(),
  val selectedOrganization: Organization? = null,
  val library: ResolvedLibrary = ResolvedLibrary(organizationId = null),
  val sortedPurposePacks: List<PurposePack> = emptyList(),
  /** Programs the selected purpose offers, shown as chips (tracking and impact reporting). */
  val offeredProgramIds: List<String> = emptyList(),
  /** The selected purpose's pack, whose starter templates are offered; null for none or "Other". */
  val selectedPurposePack: PurposePack? = null,
  /** Chosen starter template of [selectedPurposePack] (blank for its default). */
  val starterTemplateId: String = "",
  /**
   * Starter template the user picked while the seeded Forms had been edited; non-null while asking
   * whether to replace them.
   */
  val pendingStarterTemplateId: String? = null,
  val primaryMapLayer: EntityDataset? = null,
  val primaryForm: SurveyEditorForm? = null,
  /**
   * Whether Step 4 offers field validation of the sample plots, with [primaryForm] as the desk
   * check (see `SurveyWizardRules.offersPlotFieldValidation`).
   */
  val offersPlotFieldValidation: Boolean = false,
  /** Review of [primaryMapLayer]'s plots (field validation and its options), or `null`. */
  val plotReview: PlotReviewSetup? = null,
  val impactReadiness: WizardImpactReadiness = WizardImpactReadiness(),
  /**
   * Why the wizard applied CARE defaults by itself, shown inline on Step 6 while they're still in
   * effect; `null` when the organizer applied them, or they're no longer in effect.
   */
  val careDefaultsAutoReason: CareDefaultsReason? = null,
  /** Whether Step 6's CARE card is expanded to show its full analysis. */
  val isCareCardExpanded: Boolean = false,
  /**
   * Whether the wizard is prompting the organizer to continue editing or discard a previous unsaved
   * survey draft.
   */
  val isPromptingUnsavedDraft: Boolean = false,
) {
  /**
   * Whether the Survey title field on Step 1 (`Overview`) should display a validation error. Only
   * becomes true after the user interacts with the title field or tries to advance to the next step
   * with a blank title.
   */
  val isTitleInvalid: Boolean
    get() = showOverviewValidationErrors && title.isBlank()

  /**
   * Whether Step 2 (`Mode & purpose`) has at least one purpose selected, and a non-blank
   * description when "Other" (`SurveyWizardRules.OTHER_PURPOSE_ID`) is selected.
   */
  val isPurposeValid: Boolean
    get() =
      purposeIds.isNotEmpty() &&
        (SurveyWizardRules.OTHER_PURPOSE_ID !in purposeIds || otherPurpose.isNotBlank())

  /** Whether Step 2 (`Mode & purpose`) should display a purpose validation error. */
  val isPurposeInvalid: Boolean
    get() = showPurposeValidationErrors && !isPurposeValid

  /** Whether Step 3 (`Survey area`) has a non-empty survey area boundary defined. */
  val isSurveyAreaValid: Boolean
    get() = surveyArea != null && surveyArea.parts.isNotEmpty()

  /** Whether Step 3 (`Survey area`) should display a validation error. */
  val isSurveyAreaInvalid: Boolean
    get() = showSurveyAreaValidationErrors && !isSurveyAreaValid

  /** Whether Step 5 (`Form questions`) should display a form variant compatibility error. */
  val isFormQuestionsInvalid: Boolean
    get() = showFormQuestionsValidationErrors && hasFormVariantIncompatibility

  /**
   * User-facing validation error message for [currentStep] when the user has attempted to advance
   * before completing required fields, or `null` when no active error banner should be shown.
   */
  val currentStepValidationMessage: StringResource?
    get() =
      when (currentStep) {
        CreateSurveyWizardStep.OVERVIEW ->
          if (!showOverviewValidationErrors) {
            null
          } else if (title.isBlank()) {
            Res.string.wizard_validation_title_required
          } else if (
            startMethod == WizardStartMethod.COPY_EXISTING && copySourceSurveyId.isNullOrBlank()
          ) {
            Res.string.wizard_validation_copy_source_required
          } else if (startMethod == WizardStartMethod.IMPORT_XFORMS && importedXForms == null) {
            Res.string.wizard_validation_import_file_required
          } else {
            null
          }
        CreateSurveyWizardStep.MODE_AND_PURPOSE ->
          if (!isPurposeInvalid) {
            null
          } else if (purposeIds.isEmpty()) {
            Res.string.wizard_validation_purpose_required
          } else {
            Res.string.wizard_validation_other_purpose_required
          }
        CreateSurveyWizardStep.SURVEY_AREA ->
          if (isSurveyAreaInvalid) {
            Res.string.wizard_validation_survey_area_required
          } else {
            null
          }
        CreateSurveyWizardStep.FORM_QUESTIONS ->
          if (isFormQuestionsInvalid) {
            Res.string.wizard_validation_form_variant_incompatible
          } else {
            null
          }
        else -> null
      }

  /** Whether the user can advance from [currentStep] via the **Next** button. */
  val canProceedFromCurrentStep: Boolean
    get() =
      when (currentStep) {
        CreateSurveyWizardStep.OVERVIEW -> {
          val hasTitle = title.isNotBlank()
          val methodReady =
            when (startMethod) {
              WizardStartMethod.FROM_SCRATCH -> true
              WizardStartMethod.COPY_EXISTING -> !copySourceSurveyId.isNullOrBlank()
              WizardStartMethod.IMPORT_XFORMS -> importedXForms != null
            }
          hasTitle && methodReady
        }
        CreateSurveyWizardStep.MODE_AND_PURPOSE -> isPurposeValid
        CreateSurveyWizardStep.SURVEY_AREA -> isSurveyAreaValid
        CreateSurveyWizardStep.MAP_FEATURES -> true
        CreateSurveyWizardStep.FORM_QUESTIONS -> !hasFormVariantIncompatibility
        CreateSurveyWizardStep.SHARING -> true
      }

  /** Whether Step 3 (`Survey area`) shows a **Skip for now** secondary action. */
  val canSkipCurrentStep: Boolean
    get() = currentStep == CreateSurveyWizardStep.SURVEY_AREA && !collectionMode.surveyAreaRequired
}

/** One-off navigation outcomes emitted by `CreateSurveyWizardViewModel`. */
sealed interface CreateSurveyWizardEvent {
  /** Survey creation completed or handed off to the full `SurveyEditorPage`. */
  data class OpenSurveyEditor(val surveyId: String) : CreateSurveyWizardEvent

  /** Wizard was cancelled; return to the previous page. */
  data object Cancelled : CreateSurveyWizardEvent
}
