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
package org.groundplatform.v2.web.ui.state

import org.groundplatform.v2.app.domain.model.Organization
import org.groundplatform.v2.app.domain.model.PurposePack
import org.groundplatform.v2.app.domain.model.SurveyPreviewItem
import org.groundplatform.v2.app.domain.model.editor.CollectEarthProjectImport
import org.groundplatform.v2.app.domain.model.editor.EntityDataset
import org.groundplatform.v2.app.domain.model.editor.ImportedForm
import org.groundplatform.v2.app.domain.model.editor.SurveyArea
import org.groundplatform.v2.app.domain.model.editor.SurveyEditorForm
import org.groundplatform.v2.app.domain.usecase.ResolvedLibrary
import org.groundplatform.v2.web.domain.model.editor.CreateSurveyWizardStep
import org.groundplatform.v2.web.domain.model.editor.SurveyCollectionMode
import org.groundplatform.v2.web.domain.model.editor.SurveyWizardRules
import org.groundplatform.v2.web.domain.model.editor.WizardMapFeatureSource
import org.groundplatform.v2.web.domain.model.editor.WizardStartMethod

/** Live impact and export readiness summary displayed in Step 5 and Step 6 of the wizard. */
typealias WizardImpactReadiness =
  org.groundplatform.v2.web.domain.model.editor.WizardImpactReadiness

/**
 * Immutable UI state for the full-page 6-step Ground 2.0 Survey Creation Wizard
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
  val importErrorMessage: String? = null,
  val collectionMode: SurveyCollectionMode = SurveyCollectionMode.FIELD_SURVEY,
  val purposeIds: List<String> = emptyList(),
  val otherPurpose: String = "",
  val showPurposeValidationErrors: Boolean = false,
  val surveyArea: SurveyArea? = null,
  val showSurveyAreaValidationErrors: Boolean = false,
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
  val offeredProgramIds: List<String> = emptyList(),
  val primaryMapLayer: EntityDataset? = null,
  val primaryForm: SurveyEditorForm? = null,
  val impactReadiness: WizardImpactReadiness = WizardImpactReadiness(),
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

  /**
   * User-facing validation error message for [currentStep] when the user has attempted to advance
   * before completing required fields, or `null` when no active error banner should be shown.
   */
  val currentStepValidationMessage: String?
    get() =
      when (currentStep) {
        CreateSurveyWizardStep.OVERVIEW ->
          if (!showOverviewValidationErrors) {
            null
          } else if (title.isBlank()) {
            "Enter a survey title to continue."
          } else if (
            startMethod == WizardStartMethod.COPY_EXISTING && copySourceSurveyId.isNullOrBlank()
          ) {
            "Select a source survey to copy before continuing."
          } else if (startMethod == WizardStartMethod.IMPORT_XFORMS && importedXForms == null) {
            "Choose a valid XForms XML file to import before continuing."
          } else {
            null
          }
        CreateSurveyWizardStep.MODE_AND_PURPOSE ->
          if (!isPurposeInvalid) {
            null
          } else if (purposeIds.isEmpty()) {
            "Select at least one purpose (or choose \"Other\" and describe your goal) to continue."
          } else {
            "Please describe the purpose of this survey to continue."
          }
        CreateSurveyWizardStep.SURVEY_AREA ->
          if (isSurveyAreaInvalid) {
            "Select or draw a survey area to continue."
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
        CreateSurveyWizardStep.FORM_QUESTIONS -> true
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
