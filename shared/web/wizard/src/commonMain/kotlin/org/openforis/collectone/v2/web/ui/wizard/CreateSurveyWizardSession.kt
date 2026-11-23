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

import org.openforis.collectone.v2.app.domain.model.LibraryPrograms
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.OrganizationLibrary
import org.openforis.collectone.v2.app.domain.model.PurposePack
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.editor.CollectEarthProjectImport
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.ImportedForm
import org.openforis.collectone.v2.app.domain.model.editor.PlotReviewWorkflow
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.app.domain.usecase.CreateSurveyUseCase
import org.openforis.collectone.v2.app.domain.usecase.ResolveLibraryUseCase
import org.openforis.collectone.v2.app.domain.usecase.ResolvedLibrary
import org.openforis.collectone.v2.app.ui.text.UiText
import org.openforis.collectone.v2.web.domain.model.editor.CareDefaultsReason
import org.openforis.collectone.v2.web.domain.model.editor.CreateSurveyWizardStep
import org.openforis.collectone.v2.web.domain.model.editor.SurveyCollectionMode
import org.openforis.collectone.v2.web.domain.model.editor.SurveyWizardRules
import org.openforis.collectone.v2.web.domain.model.editor.WizardMapFeatureSource
import org.openforis.collectone.v2.web.domain.model.editor.WizardStartMethod
import org.openforis.collectone.v2.web.domain.usecase.EvaluateWizardImpactReadinessUseCase
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorUiState

/**
 * What the Survey Creation Wizard keeps beside the working draft: the current step, which
 * validation errors to show, the chosen start method and imports, the collection mode and map
 * feature source (with whether the organizer overrode the purposes' defaults), and bookkeeping of
 * what has already been applied to the draft so stepping back and forth doesn't re-apply it.
 *
 * The draft itself (title aside, which is mirrored here so Step 1 can validate it while typing)
 * lives in the Survey editor; this holds only what the draft can't record.
 */
internal data class CreateSurveyWizardSession(
  val step: CreateSurveyWizardStep = CreateSurveyWizardStep.OVERVIEW,
  val workingSurveyId: String = "",
  val previousActiveSurveyId: String = "",
  val title: String = "",
  val showOverviewValidationErrors: Boolean = false,
  val showPurposeValidationErrors: Boolean = false,
  val showSurveyAreaValidationErrors: Boolean = false,
  val showFormQuestionsValidationErrors: Boolean = false,
  val validationAttemptCount: Int = 0,
  val startMethod: WizardStartMethod = WizardStartMethod.FROM_SCRATCH,
  val copySourceSurveyId: String? = null,
  val importedXFormsFileName: String? = null,
  val importedXForms: ImportedForm? = null,
  val importedCepProjects: List<CollectEarthProjectImport> = emptyList(),
  val selectedCepProject: CollectEarthProjectImport? = null,
  val importErrorMessage: UiText? = null,
  val collectionMode: SurveyCollectionMode = SurveyCollectionMode.FIELD_SURVEY,
  val mapFeatureSource: WizardMapFeatureSource = WizardMapFeatureSource.FIELD_FREEFORM,
  val isMapFeatureSourceOverridden: Boolean = false,
  val isGeometryTypeOverridden: Boolean = false,
  val isEudrPlotsUnderFourHaConfirmed: Boolean = false,
  val isCollectorConsentRequired: Boolean = false,
  val collectorConsentText: String = "",
  val seededPurposeIds: Set<String> = emptySet(),
  /**
   * Starter template chosen for the selected purpose. Only used to seed Forms; the survey records
   * just its purposes, programs, and each Form's template origin.
   */
  val starterTemplateId: String = "",
  /** Starter template the seeded Forms came from. */
  val seededStarterTemplateId: String = "",
  /** Keys of the Forms seeded from [seededPurposeIds]' templates (the primary Form first). */
  val seededFormKeys: Set<String> = emptySet(),
  /** Starter template awaiting the user's answer on whether to replace edited Forms. */
  val pendingStarterTemplateId: String? = null,
  /**
   * The start method (and its copy source or imported file) last applied to the draft on leaving
   * Step 1. Leaving it again re-applies only when the choice changed, so going back to Step 1 and
   * forward doesn't wipe edits made in later steps.
   */
  val appliedStartMethod: WizardStartMethod? = null,
  val appliedCopySourceId: String? = null,
  val appliedImportedXFormsFileName: String? = null,
  /** Whether CARE data sovereignty defaults were applied, so they're applied at most once. */
  val isCareSovereigntyApplied: Boolean = false,
  /**
   * Why the wizard applied CARE defaults by itself on reaching Step 6, or `null` when it didn't (or
   * the organizer applied them with the button).
   */
  val careDefaultsAutoReason: CareDefaultsReason? = null,
  /**
   * Whether Step 6's CARE card shows its full analysis. Collapsed unless CARE was applied
   * automatically (so the organizer sees what changed on their behalf); then it's their choice.
   */
  val isCareCardExpanded: Boolean = false,
  /** Whether to ask if the previous unfinished survey should be continued or discarded. */
  val isPromptingUnsavedDraft: Boolean = false,
  /** Organization to start a fresh survey in if the organizer discards the unfinished one. */
  val pendingOrganizationId: String? = null,
  /** Whether the organizer picked the organization (or none) themselves in Step 1. */
  val isOrganizationChosen: Boolean = false,
  /** Whether finishing is saving the survey and waiting for the server to accept it. */
  val isFinishing: Boolean = false,
) {
  /**
   * The collection mode the purposes and map feature source call for, unless the user chose one
   * (picking a mode or a map feature source by hand overrides the purposes' defaults).
   */
  fun effectiveCollectionMode(purposeIds: List<String>): SurveyCollectionMode =
    if (isMapFeatureSourceOverridden) {
      collectionMode
    } else {
      SurveyWizardRules.defaultCollectionMode(purposeIds, mapFeatureSource)
    }
}

/**
 * The session of a wizard resumed on [workingCopy], the unsaved draft of a survey left unfinished
 * in an earlier session (for example before a page reload), back on Step 1.
 *
 * The draft can't record the session's choices, so they are read back from what they left in it: a
 * generated primary Map layer means sample plots (picked by hand if the purposes don't call for
 * them), a primary Form available on another device than the purposes' default mode means a
 * collection mode picked by hand, a primary layer geometry other than the default means it was
 * overridden, and Forms from templates were seeded from the purposes. Without this, moving on from
 * Step 1 would re-apply the purposes' defaults over the organizer's choices (wiping a sample
 * design, resetting the geometry type, or seeding duplicate Forms).
 */
internal fun resumedWizardSession(
  workingSurveyId: String,
  workingCopy: SurveyEditorDraft,
  previousActiveSurveyId: String,
  copySourceSurveyId: String?,
  pendingOrganizationId: String?,
): CreateSurveyWizardSession {
  val purposeIds = workingCopy.details.purposeIds
  val primaryLayer = workingCopy.datasets.firstOrNull { it.kind == DatasetKind.MAP_LAYER }
  val primaryForm = workingCopy.forms.firstOrNull()?.form
  val purposeSource = SurveyWizardRules.defaultMapFeatureSource(purposeIds)
  val mapFeatureSource =
    when {
      primaryLayer?.isGenerated == true -> WizardMapFeatureSource.SAMPLE_PLOTS
      primaryLayer != null && purposeSource == WizardMapFeatureSource.SAMPLE_PLOTS ->
        WizardMapFeatureSource.FIELD_FREEFORM
      else -> purposeSource
    }
  val defaultMode = SurveyWizardRules.defaultCollectionMode(purposeIds, mapFeatureSource)
  val collectionMode =
    primaryForm
      ?.takeIf { it.availability != defaultMode.formAvailability }
      ?.let { form ->
        SurveyCollectionMode.entries.firstOrNull { it.formAvailability == form.availability }
      } ?: defaultMode
  val starterTemplateId =
    primaryForm?.templateOrigin?.templateId?.takeIf { purposeIds.isNotEmpty() }.orEmpty()
  val isGeometryTypeOverridden =
    primaryLayer != null &&
      !primaryLayer.isGenerated &&
      primaryLayer.geometryType !=
        SurveyWizardRules.defaultGeometryType(
          purposeIds = purposeIds,
          mapFeatureSource = mapFeatureSource,
          starterTemplateIds =
            purposeIds
              .firstOrNull()
              ?.takeIf { starterTemplateId.isNotBlank() }
              ?.let { mapOf(it to starterTemplateId) }
              .orEmpty(),
        )
  return CreateSurveyWizardSession(
    step = CreateSurveyWizardStep.OVERVIEW,
    workingSurveyId = workingSurveyId,
    previousActiveSurveyId = previousActiveSurveyId,
    title = workingCopy.details.title.takeIf { it != CreateSurveyUseCase.DEFAULT_TITLE }.orEmpty(),
    copySourceSurveyId = copySourceSurveyId,
    collectionMode = collectionMode,
    mapFeatureSource = mapFeatureSource,
    isMapFeatureSourceOverridden =
      mapFeatureSource != purposeSource || collectionMode != defaultMode,
    isGeometryTypeOverridden = isGeometryTypeOverridden,
    isCollectorConsentRequired = workingCopy.sharing.collectorConsentRequired,
    collectorConsentText = workingCopy.sharing.collectorConsentText,
    seededPurposeIds = purposeIds.toSet(),
    starterTemplateId = starterTemplateId,
    seededStarterTemplateId = starterTemplateId,
    seededFormKeys =
      workingCopy.forms.filter { it.form.templateOrigin != null }.map { it.key }.toSet(),
    // The draft already holds whatever the start method put in it.
    appliedStartMethod = WizardStartMethod.FROM_SCRATCH.takeIf { workingCopy.forms.isNotEmpty() },
    isOrganizationChosen = true,
    isPromptingUnsavedDraft = true,
    pendingOrganizationId = pendingOrganizationId,
  )
}

/**
 * The wizard's UI state from its [session][s], the Survey editor's state, and the stored surveys,
 * organizations, and libraries. A pure function of its inputs, so the ViewModel recomputes it on
 * every emission and tests can call it directly.
 *
 * The organizer chooses among the organizations they're a member of (never the synthetic "All
 * users" one), copies from surveys other than the one being created, and sees the purposes of the
 * selected organization's library (merged with the global one).
 */
internal fun buildCreateSurveyWizardUiState(
  s: CreateSurveyWizardSession,
  editorUi: SurveyEditorUiState,
  surveys: List<SurveyPreviewItem>,
  organizations: List<Organization>,
  libraries: Map<String, OrganizationLibrary>,
  resolveLibraryUseCase: ResolveLibraryUseCase,
  evaluateImpactReadiness: EvaluateWizardImpactReadinessUseCase,
): CreateSurveyWizardUiState {
  val draft = editorUi.draft
  val details = draft.details
  val resolved =
    resolveWizardCatalogState(
      s = s,
      editorUi = editorUi,
      surveys = surveys,
      organizations = organizations,
      libraries = libraries,
      resolveLibraryUseCase = resolveLibraryUseCase,
      evaluateImpactReadiness = evaluateImpactReadiness,
    )
  val primaryLayer = draft.datasets.firstOrNull { it.kind == DatasetKind.MAP_LAYER }
  val primaryForm = draft.forms.firstOrNull()
  return CreateSurveyWizardUiState(
    currentStep = s.step,
    workingSurveyId = s.workingSurveyId,
    previousActiveSurveyId = s.previousActiveSurveyId,
    title = s.title,
    showOverviewValidationErrors = s.showOverviewValidationErrors,
    description = details.description,
    organizationId = details.organizationId,
    startMethod = s.startMethod,
    copySourceSurveyId = s.copySourceSurveyId ?: resolved.existingSurveys.firstOrNull()?.id,
    importedXFormsFileName = s.importedXFormsFileName,
    importedXForms = s.importedXForms,
    importedCepProjects = s.importedCepProjects,
    selectedCepProject = s.selectedCepProject,
    importErrorMessage = s.importErrorMessage,
    isFinishing = s.isFinishing,
    collectionMode = s.collectionMode,
    purposeIds = details.purposeIds,
    otherPurpose = details.otherPurpose,
    showPurposeValidationErrors = s.showPurposeValidationErrors,
    surveyArea = details.surveyArea,
    showSurveyAreaValidationErrors = s.showSurveyAreaValidationErrors,
    hasFormVariantIncompatibility =
      draft.forms.any { it.form.googleEarthIncompatibleQuestions().isNotEmpty() },
    showFormQuestionsValidationErrors = s.showFormQuestionsValidationErrors,
    validationAttemptCount = s.validationAttemptCount,
    mapFeatureSource = s.mapFeatureSource,
    mapFeatureSourcePreselected =
      s.isMapFeatureSourceOverridden ||
        SurveyWizardRules.purposeMapFeatureSource(details.purposeIds) != null,
    eudrPlotsUnderFourHaConfirmed = s.isEudrPlotsUnderFourHaConfirmed,
    collectorConsentRequired =
      draft.sharing.collectorConsentRequired || s.isCollectorConsentRequired,
    collectorConsentText = draft.sharing.collectorConsentText.ifBlank { s.collectorConsentText },
    existingSurveys = resolved.existingSurveys,
    memberOrganizations = resolved.memberOrgs,
    selectedOrganization = resolved.selectedOrg,
    library = resolved.library,
    sortedPurposePacks = resolved.sortedPacks,
    offeredProgramIds = resolved.offeredPrograms,
    selectedPurposePack = resolved.selectedPacks.singleOrNull(),
    starterTemplateId = s.starterTemplateId,
    pendingStarterTemplateId = s.pendingStarterTemplateId,
    primaryMapLayer = primaryLayer,
    primaryForm = primaryForm,
    offersPlotFieldValidation =
      primaryLayer?.isGenerated == true &&
        primaryForm != null &&
        SurveyWizardRules.offersPlotFieldValidation(
          s.mapFeatureSource,
          s.effectiveCollectionMode(details.purposeIds),
        ),
    plotReview = primaryLayer?.let { PlotReviewWorkflow.setup(draft, it.key) },
    impactReadiness = resolved.readiness,
    careDefaultsAutoReason =
      s.careDefaultsAutoReason.takeIf { resolved.readiness.careSovereigntyApplied },
    isCareCardExpanded = s.isCareCardExpanded,
    isPromptingUnsavedDraft = s.isPromptingUnsavedDraft,
  )
}

private class ResolvedWizardCatalogState(
  val memberOrgs: List<Organization>,
  val selectedOrg: Organization?,
  val library: ResolvedLibrary,
  val sortedPacks: List<PurposePack>,
  val selectedPacks: List<PurposePack>,
  val offeredPrograms: List<String>,
  val existingSurveys: List<SurveyPreviewItem>,
  val readiness: WizardImpactReadiness,
)

private fun resolveWizardCatalogState(
  s: CreateSurveyWizardSession,
  editorUi: SurveyEditorUiState,
  surveys: List<SurveyPreviewItem>,
  organizations: List<Organization>,
  libraries: Map<String, OrganizationLibrary>,
  resolveLibraryUseCase: ResolveLibraryUseCase,
  evaluateImpactReadiness: EvaluateWizardImpactReadinessUseCase,
): ResolvedWizardCatalogState {
  val draft = editorUi.draft
  val details = draft.details
  val email = editorUi.signedInUserEmail
  val memberOrgs = organizations.filter { !it.isSynthetic && it.isMember(email) }
  val selectedOrg = details.organizationId?.let { id -> organizations.firstOrNull { it.id == id } }
  val library = resolveLibraryUseCase.forOrganization(libraries, selectedOrg?.id)
  val sortedPacks = SurveyWizardRules.sortPurposePacks(library, s.collectionMode)
  val selectedPacks = sortedPacks.filter { it.id in details.purposeIds }
  return ResolvedWizardCatalogState(
    memberOrgs = memberOrgs,
    selectedOrg = selectedOrg,
    library = library,
    sortedPacks = sortedPacks,
    selectedPacks = selectedPacks,
    offeredPrograms = LibraryPrograms.offeredFor(selectedPacks, details.programIds),
    existingSurveys =
      surveys.filter { it.id != s.workingSurveyId }.sortedBy { it.title.lowercase() },
    readiness =
      evaluateImpactReadiness(
        draft = draft,
        library = library,
        selectedOrganization = selectedOrg,
        language = details.defaultLanguage.ifBlank { "en" },
        isCareSovereigntyApplied = s.isCareSovereigntyApplied,
        isCollectorConsentRequired = s.isCollectorConsentRequired,
      ),
  )
}
