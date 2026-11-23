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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.domain.model.DomainMessage
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.QuotaCheckResult
import org.openforis.collectone.v2.app.domain.model.editor.CollectEarthIdmParser
import org.openforis.collectone.v2.app.domain.model.editor.CollectEarthProjectImport
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EditorIssue
import org.openforis.collectone.v2.app.domain.model.editor.EditorIssueMessage
import org.openforis.collectone.v2.app.domain.model.editor.FormImport
import org.openforis.collectone.v2.app.domain.model.editor.FormPreviewTarget
import org.openforis.collectone.v2.app.domain.model.editor.PeerDataVisibility
import org.openforis.collectone.v2.app.domain.model.editor.PlotReviewOptions
import org.openforis.collectone.v2.app.domain.model.editor.PlotReviewWorkflow
import org.openforis.collectone.v2.app.domain.model.editor.SharingPolicy
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.app.domain.repository.AuthRepository
import org.openforis.collectone.v2.app.domain.repository.LibraryRepository
import org.openforis.collectone.v2.app.domain.repository.OrganizationRepository
import org.openforis.collectone.v2.app.domain.repository.OutboxSyncRepository
import org.openforis.collectone.v2.app.domain.repository.SurveyEditorRepository
import org.openforis.collectone.v2.app.domain.repository.SurveyRepository
import org.openforis.collectone.v2.app.domain.usecase.CreateSurveyUseCase
import org.openforis.collectone.v2.app.domain.usecase.ExportSurveyDataUseCase
import org.openforis.collectone.v2.app.domain.usecase.ResolveLibraryUseCase
import org.openforis.collectone.v2.app.ui.text.ErrorMessages
import org.openforis.collectone.v2.app.ui.text.toUiText
import org.openforis.collectone.v2.app.ui.viewmodel.ViewModelDispatcher
import org.openforis.collectone.v2.app.ui.viewmodel.viewModelScopeOn
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.web.domain.model.editor.CreateSurveyWizardStep
import org.openforis.collectone.v2.web.domain.model.editor.MapLayerImportPlan
import org.openforis.collectone.v2.web.domain.model.editor.SurveyCollectionMode
import org.openforis.collectone.v2.web.domain.model.editor.SurveyWizardRules
import org.openforis.collectone.v2.web.domain.model.editor.WizardDraftEdits
import org.openforis.collectone.v2.web.domain.model.editor.WizardMapFeatureSource
import org.openforis.collectone.v2.web.domain.model.editor.WizardStartMethod
import org.openforis.collectone.v2.web.domain.usecase.CheckWizardSurveyQuotaUseCase
import org.openforis.collectone.v2.web.domain.usecase.ConfigureWizardSurveyDraftUseCase
import org.openforis.collectone.v2.web.domain.usecase.EvaluateWizardImpactReadinessUseCase
import org.openforis.collectone.v2.web.domain.usecase.FindUnfinishedWizardSurveyUseCase
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.wizard_import_parse_failed
import org.openforis.collectone.v2.web.resources.wizard_save_failed
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorSection
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorViewModel

/**
 * Coordinates the 6-step Collect One Survey Creation Wizard (`CreateSurveyWizardPage`) and keeps
 * its working survey synchronized with [SurveyEditorViewModel] so the embedded Survey area,
 * Sampling design, Form editor, and Sharing components operate on the live draft.
 */
class CreateSurveyWizardViewModel(
  private val surveyRepository: SurveyRepository,
  private val surveyEditorRepository: SurveyEditorRepository,
  private val organizationRepository: OrganizationRepository,
  private val authRepository: AuthRepository,
  private val libraryRepository: LibraryRepository,
  private val createSurveyUseCase: CreateSurveyUseCase,
  private val surveyEditor: SurveyEditorViewModel,
  dispatcher: CoroutineDispatcher = ViewModelDispatcher,
  private val resolveLibraryUseCase: ResolveLibraryUseCase = ResolveLibraryUseCase(),
  private val exportSurveyDataUseCase: ExportSurveyDataUseCase = ExportSurveyDataUseCase(),
  private val evaluateImpactReadiness: EvaluateWizardImpactReadinessUseCase =
    EvaluateWizardImpactReadinessUseCase(exportSurveyDataUseCase),
  private val configureWizardSurveyDraft: ConfigureWizardSurveyDraftUseCase =
    ConfigureWizardSurveyDraftUseCase(),
  private val findUnfinishedWizardSurvey: FindUnfinishedWizardSurveyUseCase =
    FindUnfinishedWizardSurveyUseCase(surveyEditorRepository),
  private val checkWizardSurveyQuota: CheckWizardSurveyQuotaUseCase =
    CheckWizardSurveyQuotaUseCase(),
  /**
   * The sync engine's outbox, to wait until the server has accepted the new survey before leaving
   * the wizard; `null` when there's no sync engine (changes are then saved once stored locally).
   */
  private val outboxSyncRepository: OutboxSyncRepository? = null,
) : ViewModel(viewModelScopeOn(dispatcher)), CreateSurveyWizardActions {

  private val session = MutableStateFlow(CreateSurveyWizardSession())

  private val _events =
    MutableSharedFlow<CreateSurveyWizardEvent>(
      extraBufferCapacity = 16,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
  val events: Flow<CreateSurveyWizardEvent> = _events.asSharedFlow()

  val uiState: StateFlow<CreateSurveyWizardUiState> =
    combine(
        session,
        surveyEditor.uiState,
        surveyRepository.observeSurveys(),
        organizationRepository.observeOrganizations(),
        libraryRepository.observeLibraries(),
      ) { s, editorUi, surveys, organizations, libraries ->
        buildCreateSurveyWizardUiState(
          s,
          editorUi,
          surveys,
          organizations,
          libraries,
          resolveLibraryUseCase,
          evaluateImpactReadiness,
        )
      }
      .stateIn(viewModelScope, SharingStarted.Eagerly, CreateSurveyWizardUiState())

  override fun startWizard(defaultOrganizationId: String?) {
    viewModelScope.launch {
      val previousActiveId = surveyRepository.getActiveSurveyId()
      val existingSurveys = surveyRepository.getSurveys()
      val auth = authRepository.observeSession().first()
      val ownerEmail = auth.profile.email

      val currentSession = session.value
      val currentWorkingId = currentSession.workingSurveyId
      var discardedId = currentWorkingId
      if (currentWorkingId.isNotBlank() && existingSurveys.any { it.id == currentWorkingId }) {
        // Only what the organizer entered counts: a fresh session fills in the organization, its
        // survey area, and sharing by itself (possibly once organizations load, which leaves a
        // working copy behind), and that alone is no draft worth asking about.
        val editorDraft =
          surveyEditor.uiState.value.takeIf { it.surveyId == currentWorkingId }?.draft
        val hasInMemoryEdits =
          currentSession.title.isNotBlank() ||
            currentSession.step != CreateSurveyWizardStep.OVERVIEW ||
            currentSession.startMethod != WizardStartMethod.FROM_SCRATCH ||
            currentSession.importedXForms != null ||
            editorDraft?.let(WizardDraftEdits::hasOrganizerInput) == true ||
            surveyEditorRepository
              .getWorkingCopyDraft(currentWorkingId)
              ?.let(WizardDraftEdits::hasOrganizerInput) == true
        if (hasInMemoryEdits) {
          if (surveyRepository.getActiveSurveyId() != currentWorkingId) {
            surveyRepository.setActiveSurveyId(currentWorkingId)
          }
          surveyEditor.continueWorkingCopy()
          session.update {
            it.copy(
              isPromptingUnsavedDraft = true,
              pendingOrganizationId = defaultOrganizationId,
            )
          }
          return@launch
        } else {
          // Clean up the untouched blank survey before creating a fresh one.
          surveyEditor.discardChanges()
          surveyEditorRepository.discardWorkingCopy(currentWorkingId)
          surveyRepository.deleteSurvey(currentWorkingId)
        }
      } else {
        val unfinished = findUnfinishedWizardSurvey(existingSurveys, ownerEmail)
        if (unfinished != null) {
          val unfinishedId = unfinished.survey.id
          if (!WizardDraftEdits.hasOrganizerInput(unfinished.workingCopy)) {
            // Left before entering anything: start over rather than ask about an empty draft.
            surveyEditorRepository.discardWorkingCopy(unfinishedId)
            surveyRepository.deleteSurvey(unfinishedId)
            discardedId = unfinishedId
          } else {
            surveyRepository.setActiveSurveyId(unfinishedId)
            // Open the working copy right away (over the saved draft it started from) so the
            // wizard shows it, not the previously active survey, while the store catches up.
            surveyEditor.openSurveyDraft(
              unfinishedId,
              unfinished.workingCopy,
              opened = unfinished.saved,
            )
            session.value =
              resumedWizardSession(
                workingSurveyId = unfinishedId,
                workingCopy = unfinished.workingCopy,
                previousActiveSurveyId = previousActiveId.takeIf { it != unfinishedId }.orEmpty(),
                copySourceSurveyId = existingSurveys.firstOrNull { it.id != unfinishedId }?.id,
                pendingOrganizationId = defaultOrganizationId,
              )
            return@launch
          }
        }
      }

      createFreshWizardSession(
        defaultOrganizationId = defaultOrganizationId,
        previousActiveId = previousActiveId.takeIf { it != discardedId }.orEmpty(),
      )
    }
  }

  override fun continueUnsavedDraft() {
    surveyEditor.continueWorkingCopy()
    session.update { it.copy(isPromptingUnsavedDraft = false, pendingOrganizationId = null) }
  }

  override fun discardUnsavedDraft() {
    viewModelScope.launch {
      val currentSession = session.value
      val workingId = currentSession.workingSurveyId
      val previousId = currentSession.previousActiveSurveyId
      val pendingOrgId = currentSession.pendingOrganizationId
      surveyEditor.discardChanges()
      if (workingId.isNotBlank()) {
        surveyEditorRepository.discardWorkingCopy(workingId)
        surveyRepository.deleteSurvey(workingId)
      }
      createFreshWizardSession(
        defaultOrganizationId = pendingOrgId,
        previousActiveId = previousId,
      )
    }
  }

  /**
   * Creates a blank draft survey in [defaultOrganizationId] (or the user's first organization),
   * opens it immediately in [surveyEditor] so subsequent step edits apply without waiting on store
   * emissions, and records [previousActiveId] so canceling restores the prior active survey.
   */
  private suspend fun createFreshWizardSession(
    defaultOrganizationId: String?,
    previousActiveId: String,
  ) {
    val existingSurveys = surveyRepository.getSurveys()
    val organizations = organizationRepository.observeOrganizations().first()
    val auth = authRepository.observeSession().first()
    val ownerEmail = auth.profile.email
    val ownerName = auth.profile.displayName.ifBlank { ownerEmail }

    val org = defaultOrganization(defaultOrganizationId, organizations, ownerEmail)

    val newSurvey =
      createSurveyUseCase.newSurvey(
        title = "",
        existingSurveys = existingSurveys,
        organization = org,
        ownerEmail = ownerEmail,
      )
    val initialDraft =
      createSurveyUseCase(
        survey = newSurvey,
        ownerName = ownerName,
        purposeIds = emptyList(),
        programIds = emptyList(),
      ) { createdDraft ->
        WizardDraftEdits.initialDraft(createdDraft, org)
      }
    // Open the draft right away so later data emissions can't race with the wizard's first edits.
    surveyEditor.openSurveyDraft(newSurvey.id, initialDraft)
    session.value =
      CreateSurveyWizardSession(
        step = CreateSurveyWizardStep.OVERVIEW,
        workingSurveyId = newSurvey.id,
        previousActiveSurveyId = previousActiveId,
        copySourceSurveyId = existingSurveys.firstOrNull()?.id,
      )
    if (org == null) applyDefaultOrganizationWhenLoaded(newSurvey.id, defaultOrganizationId)
  }

  /**
   * The organization a new survey starts in: [defaultOrganizationId] (the one being browsed) if it
   * is a real organization, else the first one [ownerEmail] is a member of.
   */
  private fun defaultOrganization(
    defaultOrganizationId: String?,
    organizations: List<Organization>,
    ownerEmail: String,
  ): Organization? =
    defaultOrganizationId
      ?.takeIf { it.isNotBlank() && it != Organization.ALL_USERS_ID }
      ?.let { id -> organizations.firstOrNull { it.id == id && !it.isSynthetic } }
      ?: organizations.firstOrNull { !it.isSynthetic && it.isMember(ownerEmail) }

  private var defaultOrganizationJob: Job? = null

  /**
   * Right after sign-in or a page load, organizations (or the signed-in profile) may not have
   * loaded yet when the wizard starts. Once they do, selects the default organization for survey
   * [surveyId], with its survey area and sharing defaults, unless the organizer has already chosen
   * one or moved on to another survey.
   */
  private fun applyDefaultOrganizationWhenLoaded(surveyId: String, defaultOrganizationId: String?) {
    defaultOrganizationJob?.cancel()
    defaultOrganizationJob = viewModelScope.launch {
      val org =
        combine(
            organizationRepository.observeOrganizations(),
            authRepository.observeSession(),
          ) { organizations, auth ->
            defaultOrganization(defaultOrganizationId, organizations, auth.profile.email)
          }
          .filterNotNull()
          .first()
      val current = session.value
      if (
        current.workingSurveyId == surveyId &&
          !current.isOrganizationChosen &&
          surveyEditor.uiState.value.surveyId == surveyId &&
          surveyEditor.uiState.value.draft.details.organizationId == null
      ) {
        applyOrganization(org.id, org)
      }
    }
  }

  override fun selectStep(step: CreateSurveyWizardStep) {
    val current = session.value.step
    if (step == current) return
    if (step.ordinal > current.ordinal && !uiState.value.canProceedFromCurrentStep) {
      flagCurrentStepValidationErrors(current)
      return
    }
    transitionToStep(step)
  }

  override fun nextStep() {
    val current = session.value.step
    if (!uiState.value.canProceedFromCurrentStep) {
      flagCurrentStepValidationErrors(current)
      return
    }
    val nextIndex = current.ordinal + 1
    val next = CreateSurveyWizardStep.entries.getOrNull(nextIndex) ?: return
    transitionToStep(next)
  }

  /**
   * Reveals inline validation messages for [current] and increments
   * [CreateSurveyWizardSession.validationAttemptCount] so the step UI can re-trigger focus or shake
   * feedback on repeated blocked Next attempts.
   */
  private fun flagCurrentStepValidationErrors(current: CreateSurveyWizardStep) {
    when (current) {
      CreateSurveyWizardStep.OVERVIEW ->
        session.update {
          it.copy(
            showOverviewValidationErrors = true,
            validationAttemptCount = it.validationAttemptCount + 1,
          )
        }
      CreateSurveyWizardStep.MODE_AND_PURPOSE ->
        session.update {
          it.copy(
            showPurposeValidationErrors = true,
            validationAttemptCount = it.validationAttemptCount + 1,
          )
        }
      CreateSurveyWizardStep.SURVEY_AREA ->
        session.update {
          it.copy(
            showSurveyAreaValidationErrors = true,
            validationAttemptCount = it.validationAttemptCount + 1,
          )
        }
      CreateSurveyWizardStep.FORM_QUESTIONS -> {
        selectFirstFormVariantIssueQuestion()
        session.update {
          it.copy(
            showFormQuestionsValidationErrors = true,
            validationAttemptCount = it.validationAttemptCount + 1,
          )
        }
      }
      else -> {}
    }
  }

  /**
   * Selects the first Form and question with a Form variant compatibility issue (`Web` or `Google
   * Earth`) so the organizer immediately sees the offending question when Step 5 or survey saving
   * is blocked.
   */
  private fun selectFirstFormVariantIssueQuestion() {
    val editorUi = surveyEditor.uiState.value
    for (formEntry in editorUi.forms) {
      val issue = editorUi.formIssues(formEntry).firstOrNull { it.isVariantCompatibilityIssue() }
      if (issue != null) {
        surveyEditor.select(SurveyEditorSection.Form(formEntry.key))
        val formEditor = surveyEditor.formEditor(formEntry.key)
        formEditor.selectPreviewTarget(FormPreviewTarget.WEB)
        if (issue.questionKey != null) {
          formEditor.select(issue.questionKey)
        } else {
          formEditor.selectFormSettings()
        }
        return
      }
    }
  }

  private fun EditorIssue.isVariantCompatibilityIssue(): Boolean {
    val key = (message as? DomainMessage.Text)?.key
    return key == EditorIssueMessage.GOOGLE_EARTH_INCOMPATIBLE_QUESTION
  }

  override fun previousStep() {
    val current = session.value.step
    val prevIndex = current.ordinal - 1
    val prev = CreateSurveyWizardStep.entries.getOrNull(prevIndex) ?: return
    session.update { it.copy(step = prev) }
  }

  override fun skipStep() {
    if (!uiState.value.canSkipCurrentStep) return
    nextStep()
  }

  /**
   * Advances or jumps to [target], applying deferred side effects for any steps crossed: copying or
   * importing the Overview start source when leaving Step 1, seeding the primary map layer and
   * Forms when reaching Step 4 (`MAP_FEATURES`) or beyond, selecting the primary Form's preview
   * target on Step 5 (`FORM_QUESTIONS`), and applying IPLC/CARE sharing defaults on Step 6
   * (`SHARING`).
   */
  private fun transitionToStep(target: CreateSurveyWizardStep) {
    val current = session.value.step
    if (current == CreateSurveyWizardStep.OVERVIEW && target.ordinal > current.ordinal) {
      applyOverviewStartMethodIfNeeded()
    }
    if (target.ordinal >= CreateSurveyWizardStep.MAP_FEATURES.ordinal) {
      ensurePrimaryLayerAndForm()
    }
    if (target == CreateSurveyWizardStep.FORM_QUESTIONS) {
      val primaryForm = surveyEditor.uiState.value.draft.forms.firstOrNull()
      if (primaryForm != null) {
        val availability = primaryForm.form.availability
        val previewTarget =
          if (!availability.includesMobile && availability.includesWeb) {
            FormPreviewTarget.WEB
          } else {
            session.value.collectionMode.defaultPreviewTarget
          }
        surveyEditor.select(SurveyEditorSection.Form(primaryForm.key))
        surveyEditor.formEditor(primaryForm.key).selectPreviewTarget(previewTarget)
      }
    }
    if (target == CreateSurveyWizardStep.SHARING) {
      maybeApplyCareDefaultsOnSharingStep()
    }
    session.update { it.copy(step = target) }
  }

  /**
   * Materializes the organizer's Step 1 start method (`FROM_SCRATCH`, `COPY_EXISTING`, or
   * `IMPORT_XFORMS`) into the working draft once, tracking the applied source on [session] so
   * navigating back to Step 1 and forward again does not overwrite later edits unless the chosen
   * source survey or imported file actually changed.
   */
  private fun applyOverviewStartMethodIfNeeded() {
    val s = session.value
    when (s.startMethod) {
      WizardStartMethod.FROM_SCRATCH -> {
        session.update { it.copy(appliedStartMethod = WizardStartMethod.FROM_SCRATCH) }
      }
      WizardStartMethod.COPY_EXISTING -> {
        val sourceId = s.copySourceSurveyId ?: uiState.value.existingSurveys.firstOrNull()?.id
        if (
          sourceId != null &&
            (s.appliedStartMethod != WizardStartMethod.COPY_EXISTING ||
              s.appliedCopySourceId != sourceId)
        ) {
          viewModelScope.launch {
            val sourceDraft = surveyEditorRepository.getDraft(sourceId)
            surveyEditor.replaceWorkingDraft({ current ->
              configureWizardSurveyDraft.copyStructureFromSource(current, sourceDraft)
            })
            session.update {
              it.copy(
                appliedStartMethod = WizardStartMethod.COPY_EXISTING,
                appliedCopySourceId = sourceId,
                seededPurposeIds = sourceDraft.details.purposeIds.toSet(),
              )
            }
          }
        }
      }
      WizardStartMethod.IMPORT_XFORMS -> {
        val imported = s.importedXForms
        val selectedCep = s.selectedCepProject
        if (
          imported != null &&
            (s.appliedStartMethod != WizardStartMethod.IMPORT_XFORMS ||
              s.appliedImportedXFormsFileName != s.importedXFormsFileName)
        ) {
          // Clear previously auto-seeded forms before importing the selected XForms or CEP file.
          surveyEditor.replaceWorkingDraft({ current ->
            current.copy(forms = emptyList(), datasets = emptyList())
          })
          val formKey = surveyEditor.importForm(imported)
          selectedCep?.plotDataset?.let { plotDataset ->
            surveyEditor.replaceWorkingDraft({ current ->
              current.copy(datasets = listOf(plotDataset))
            })
          }
          surveyEditor.select(SurveyEditorSection.Form(formKey))
          session.update {
            it.copy(
              appliedStartMethod = WizardStartMethod.IMPORT_XFORMS,
              appliedImportedXFormsFileName = s.importedXFormsFileName,
            )
          }
        }
      }
    }
  }

  /**
   * Ensures the working draft has a primary `MAP_LAYER` and a primary `EditorForm` configured
   * according to the selected Purpose Packs and [WizardMapFeatureSource], plus one Form (with its
   * own linked dataset) for each further template of the selected Purpose Packs.
   *
   * Field validation of sample plots (see [setPlotFieldValidation]) carries over: it's set up again
   * on the primary Form when a purpose or starter template replaces it, and removed when the survey
   * no longer uses sample plots for visual interpretation.
   */
  private fun ensurePrimaryLayerAndForm() {
    val s = session.value
    val library = uiState.value.library
    val currentPurposeIds = surveyEditor.uiState.value.draft.details.purposeIds
    val effectiveMode = s.effectiveCollectionMode(currentPurposeIds)
    val hasPurposeSelectionChanged =
      currentPurposeIds.toSet() != s.seededPurposeIds ||
        s.starterTemplateId != s.seededStarterTemplateId
    var seededFormKeys = s.seededFormKeys

    val review = activePlotReview()
    val keepsReview = SurveyWizardRules.offersPlotFieldValidation(s.mapFeatureSource, effectiveMode)
    if (review != null && !keepsReview) {
      surveyEditor.replaceWorkingDraft({ removePlotReview(it, review.first, effectiveMode) })
    }

    surveyEditor.replaceWorkingDraft({ current ->
      val syncResult =
        configureWizardSurveyDraft.synchronizePrimaryLayerAndForms(
          current = current,
          library = library,
          startMethod = s.startMethod,
          effectiveMode = effectiveMode,
          mapFeatureSource = s.mapFeatureSource,
          isGeometryTypeOverridden = s.isGeometryTypeOverridden,
          isEudrPlotsUnderFourHaConfirmed = s.isEudrPlotsUnderFourHaConfirmed,
          hasPurposeSelectionChanged = hasPurposeSelectionChanged,
          previousSeededFormKeys = s.seededFormKeys,
          starterTemplateIds = starterTemplateIds(currentPurposeIds, s.starterTemplateId),
        )
      seededFormKeys = syncResult.seededFormKeys
      syncResult.draft
    })

    if (hasPurposeSelectionChanged) {
      session.update {
        it.copy(
          seededPurposeIds = currentPurposeIds.toSet(),
          seededStarterTemplateId = s.starterTemplateId,
          seededFormKeys = seededFormKeys,
        )
      }
    }
    if (review != null && keepsReview) applyPlotReview(review.second)
  }

  /** The primary layer's key and review options while field validation is on, else `null`. */
  private fun activePlotReview(): Pair<String, PlotReviewOptions>? {
    val draft = surveyEditor.uiState.value.draft
    val layer = draft.datasets.firstOrNull { it.kind == DatasetKind.MAP_LAYER } ?: return null
    val options = PlotReviewWorkflow.setup(draft, layer.key).options
    return if (options.fieldValidation) layer.key to options else null
  }

  /** Sets up the review for [options] with the primary Form as its desk check. */
  private fun applyPlotReview(options: PlotReviewOptions) {
    val draft = surveyEditor.uiState.value.draft
    val layer = draft.datasets.firstOrNull { it.kind == DatasetKind.MAP_LAYER } ?: return
    val primaryFormKey = draft.forms.firstOrNull()?.key ?: return
    surveyEditor.replaceWorkingDraft({ current ->
      PlotReviewWorkflow.apply(current, layer.key, options, deskFormKey = primaryFormKey)
    })
  }

  /** Removes plot review from [layerKey] and restores the adopted desk Form's availability. */
  private fun removePlotReview(
    draft: SurveyEditorDraft,
    layerKey: String,
    effectiveMode: SurveyCollectionMode,
  ): SurveyEditorDraft {
    val deskKey = PlotReviewWorkflow.setup(draft, layerKey).deskFormKey
    val removed = PlotReviewWorkflow.remove(draft, layerKey)
    return if (deskKey == null) {
      removed
    } else {
      removed.copy(
        forms =
          removed.forms.map { entry ->
            if (entry.key == deskKey) {
              entry.copy(form = entry.form.copy(availability = effectiveMode.formAvailability))
            } else {
              entry
            }
          }
      )
    }
  }

  override fun setPlotFieldValidation(enabled: Boolean) {
    val draft = surveyEditor.uiState.value.draft
    val layer = draft.datasets.firstOrNull { it.kind == DatasetKind.MAP_LAYER } ?: return
    if (enabled) {
      val options = PlotReviewWorkflow.setup(draft, layer.key).options
      applyPlotReview(options.copy(fieldValidation = true))
    } else {
      val effectiveMode = session.value.effectiveCollectionMode(draft.details.purposeIds)
      surveyEditor.replaceWorkingDraft({ removePlotReview(it, layer.key, effectiveMode) })
      // The primary Form goes back to the collection mode's availability and save-to.
      ensurePrimaryLayerAndForm()
    }
  }

  override fun updatePlotFieldValidation(options: PlotReviewOptions) {
    if (options.fieldValidation) applyPlotReview(options) else setPlotFieldValidation(false)
  }

  /**
   * Auto-applies CARE/FPIC data sovereignty defaults (`RESTRICTED` sharing, `OWN_ONLY` peer
   * visibility, and FPIC collector consent) the first time Step 6 (`SHARING`) is reached for an
   * IPLC organization or a survey linked to IPLC/CARE concepts.
   */
  private fun maybeApplyCareDefaultsOnSharingStep() {
    if (session.value.isCareSovereigntyApplied) return
    val draft = surveyEditor.uiState.value.draft
    val org = uiState.value.selectedOrganization
    val conceptIds = evaluateImpactReadiness.extractLinkedConceptIds(draft)
    val reason = SurveyWizardRules.careDefaultsReason(org?.organizationType, conceptIds) ?: return
    applyCareSovereigntyDefaults()
    session.update { it.copy(careDefaultsAutoReason = reason, isCareCardExpanded = true) }
  }

  override fun updateTitle(title: String) {
    session.update { it.copy(title = title, showOverviewValidationErrors = true) }
    surveyEditor.updateDetails { it.copy(title = title) }
  }

  override fun updateDescription(description: String) {
    surveyEditor.updateDetails { it.copy(description = description) }
  }

  override fun selectOrganization(organizationId: String?) {
    defaultOrganizationJob?.cancel()
    session.update { it.copy(isOrganizationChosen = true) }
    applyOrganization(organizationId)
  }

  /**
   * Sets the working survey's organization to [organizationId] ([known] when the editor's list may
   * not have it yet), moving its survey area along if it was the previous organization's default.
   */
  private fun applyOrganization(organizationId: String?, known: Organization? = null) {
    val previousOrgId = surveyEditor.uiState.value.draft.details.organizationId
    val previousOrg = previousOrgId?.let { id ->
      surveyEditor.uiState.value.organizations.firstOrNull { it.id == id }
    }
    surveyEditor.setOrganization(organizationId)
    val org =
      known?.takeIf { it.id == organizationId }
        ?: organizationId?.let { id ->
          surveyEditor.uiState.value.organizations.firstOrNull { it.id == id }
        }
    val currentArea = surveyEditor.uiState.value.draft.details.surveyArea
    if (currentArea == null || currentArea == previousOrg?.defaultSurveyArea()) {
      surveyEditor.setSurveyArea(org?.defaultSurveyArea())
    }
    if (org != null) {
      surveyEditor.updateSharing {
        WizardDraftEdits.organizationSharing(it, org.organizationType)
      }
    }
  }

  override fun selectStartMethod(method: WizardStartMethod) {
    session.update { it.copy(startMethod = method, importErrorMessage = null) }
  }

  override fun selectCopySourceSurvey(surveyId: String?) {
    session.update { it.copy(copySourceSurveyId = surveyId) }
  }

  override fun importXFormsXml(fileName: String, xmlText: String) {
    val availability = session.value.collectionMode.formAvailability
    val cepProjects =
      CollectEarthIdmParser.parseProjects(
        xml = xmlText,
        fallbackFileName = fileName,
        availability = availability,
      )
    if (cepProjects.isNotEmpty()) {
      val firstProject = cepProjects.first()
      session.update {
        it.copy(
          importedCepProjects = cepProjects,
          selectedCepProject = firstProject,
        )
      }
      applySelectedCepProject(firstProject)
      return
    }

    val fallbackTitle = fileName.substringBeforeLast('.').ifBlank { "Imported form" }
    val imported =
      FormImport.fromXml(
        xml = xmlText,
        fallbackTitle = fallbackTitle,
        availability = availability,
      )
    if (imported == null || imported.form.questions.isEmpty()) {
      session.update {
        it.copy(
          importedXFormsFileName = fileName,
          importedXForms = null,
          importedCepProjects = emptyList(),
          selectedCepProject = null,
          importErrorMessage = Res.string.wizard_import_parse_failed.toUiText(fileName),
        )
      }
    } else {
      val shouldUpdateTitle = surveyEditor.uiState.value.draft.details.title.isBlank()
      session.update {
        it.copy(
          title = if (shouldUpdateTitle) imported.form.title else it.title,
          importedXFormsFileName = fileName,
          importedXForms = imported,
          importedCepProjects = emptyList(),
          selectedCepProject = null,
          importErrorMessage = null,
        )
      }
      if (shouldUpdateTitle) {
        surveyEditor.updateDetails { it.copy(title = imported.form.title) }
      }
    }
  }

  override fun selectImportedCepProject(fileName: String) {
    val project =
      session.value.importedCepProjects.firstOrNull { it.fileName == fileName } ?: return
    applySelectedCepProject(project)
  }

  /**
   * Applies the chosen Collect Earth `.cep` project's title, description, languages, survey area,
   * and imported form to the wizard session and draft, switching to `VISUAL_CLASSIFICATION` +
   * `IMPORT_FILE` when the archive includes a sample plot CSV grid.
   */
  private fun applySelectedCepProject(project: CollectEarthProjectImport) {
    val hasPlotGrid = project.plotDataset != null
    session.update {
      it.copy(
        title = project.projectTitle,
        importedXFormsFileName = project.fileName,
        importedXForms = project.importedForm,
        selectedCepProject = project,
        importErrorMessage = null,
        collectionMode =
          if (hasPlotGrid) SurveyCollectionMode.VISUAL_CLASSIFICATION else it.collectionMode,
        mapFeatureSource =
          if (hasPlotGrid) WizardMapFeatureSource.IMPORT_FILE else it.mapFeatureSource,
        isMapFeatureSourceOverridden = hasPlotGrid || it.isMapFeatureSourceOverridden,
        isGeometryTypeOverridden = hasPlotGrid || it.isGeometryTypeOverridden,
        appliedImportedXFormsFileName = null,
      )
    }
    surveyEditor.updateDetails { current ->
      current.copy(
        title = project.projectTitle,
        description = project.description.ifBlank { current.description },
        defaultLanguage = project.languages.firstOrNull() ?: current.defaultLanguage,
        supportedLanguages = project.languages.ifEmpty { current.supportedLanguages },
        surveyArea = project.surveyArea ?: current.surveyArea,
      )
    }
  }

  override fun clearImportedXForms() {
    session.update {
      it.copy(
        importedXFormsFileName = null,
        importedXForms = null,
        importedCepProjects = emptyList(),
        selectedCepProject = null,
        importErrorMessage = null,
      )
    }
  }

  override fun selectCollectionMode(mode: SurveyCollectionMode) {
    session.update {
      it.copy(
        collectionMode = mode,
        mapFeatureSource = mode.defaultFeatureSource,
        isMapFeatureSourceOverridden = true,
      )
    }
  }

  override fun togglePurposePack(packId: String) {
    val details = surveyEditor.uiState.value.draft.details
    val currentIds = details.purposeIds
    val alreadySelected = packId in currentIds
    val newPurposeIds = if (alreadySelected) emptyList() else listOf(packId)
    val library = uiState.value.library
    // A new purpose starts with its default (first) starter template, which presets the programs.
    val starter =
      if (packId != SurveyWizardRules.OTHER_PURPOSE_ID && !alreadySelected) {
        library.purposePack(packId)?.defaultStarterTemplate
      } else {
        null
      }
    surveyEditor.updateDetails {
      it.copy(
        purposeIds = newPurposeIds,
        programIds = starter?.programIds.orEmpty(),
      )
    }
    session.update {
      it.copy(pendingStarterTemplateId = null, starterTemplateId = starter?.id.orEmpty())
    }
    session.update { current ->
      val nextSource =
        if (current.isMapFeatureSourceOverridden) {
          current.mapFeatureSource
        } else {
          SurveyWizardRules.defaultMapFeatureSource(newPurposeIds)
        }
      val nextMode =
        if (current.isMapFeatureSourceOverridden) {
          current.collectionMode
        } else {
          SurveyWizardRules.defaultCollectionMode(newPurposeIds, nextSource)
        }
      current.copy(
        mapFeatureSource = nextSource,
        collectionMode = nextMode,
        isGeometryTypeOverridden = false,
      )
    }
    if (
      !alreadySelected &&
        packId == SurveyWizardRules.EUDR_PURPOSE_ID &&
        !session.value.isEudrPlotsUnderFourHaConfirmed
    ) {
      updatePrimaryLayerGeometryType(GeometryType.POLYGON)
    }
    if (surveyEditor.uiState.value.draft.datasets.any { it.kind == DatasetKind.MAP_LAYER }) {
      ensurePrimaryLayerAndForm()
    }
  }

  override fun updateOtherPurpose(text: String) {
    surveyEditor.updateOtherPurpose(text)
  }

  override fun toggleProgram(programId: String) {
    surveyEditor.toggleProgram(programId)
  }

  override fun selectStarterTemplate(starterTemplateId: String) {
    if (starterTemplateId == session.value.starterTemplateId) return
    if (haveSeededFormsBeenEdited()) {
      session.update { it.copy(pendingStarterTemplateId = starterTemplateId) }
    } else {
      applyStarterTemplate(starterTemplateId, replaceForms = true)
    }
  }

  override fun resolveStarterTemplateChange(replaceForms: Boolean?) {
    val pending = session.value.pendingStarterTemplateId ?: return
    session.update { it.copy(pendingStarterTemplateId = null) }
    if (replaceForms != null) applyStarterTemplate(pending, replaceForms)
  }

  /**
   * Chooses starter template [starterTemplateId], presetting its programs, and either re-seeds the
   * Forms from its templates ([replaceForms]) or keeps the current ones.
   */
  private fun applyStarterTemplate(starterTemplateId: String, replaceForms: Boolean) {
    val details = surveyEditor.uiState.value.draft.details
    val pack = details.purposeIds.firstNotNullOfOrNull(uiState.value.library::purposePack) ?: return
    val starter = pack.starterTemplate(starterTemplateId)
    surveyEditor.updateDetails { d -> d.copy(programIds = starter.programIds) }
    session.update { it.copy(starterTemplateId = starterTemplateId) }
    if (!replaceForms) {
      // Treat the kept Forms as seeded for the new template, so they aren't replaced later.
      session.update { it.copy(seededStarterTemplateId = starterTemplateId) }
      return
    }
    if (
      SurveyWizardRules.requiresEudrPolygonGuardrail(
        details.purposeIds,
        starterTemplateIds(details.purposeIds, starterTemplateId),
      ) && !session.value.isEudrPlotsUnderFourHaConfirmed
    ) {
      updatePrimaryLayerGeometryType(GeometryType.POLYGON)
    }
    if (surveyEditor.uiState.value.draft.datasets.any { it.kind == DatasetKind.MAP_LAYER }) {
      ensurePrimaryLayerAndForm()
    }
  }

  /**
   * Whether any Form seeded from the purpose's templates was edited (its template origin is marked
   * modified) or removed since seeding.
   */
  private fun haveSeededFormsBeenEdited(): Boolean {
    val keys = session.value.seededFormKeys
    if (keys.isEmpty()) return false
    val seeded = surveyEditor.uiState.value.draft.forms.filter { it.key in keys }
    return seeded.size < keys.size || seeded.any { it.form.templateOrigin?.modified == true }
  }

  /** The chosen [starterTemplateId] keyed by the selected purpose in [purposeIds], if any. */
  private fun starterTemplateIds(
    purposeIds: List<String>,
    starterTemplateId: String,
  ): Map<String, String> =
    purposeIds
      .firstOrNull()
      ?.takeIf { starterTemplateId.isNotBlank() }
      ?.let {
        mapOf(it to starterTemplateId)
      }
      .orEmpty()

  override fun selectMapFeatureSource(source: WizardMapFeatureSource) {
    val purposeIds = surveyEditor.uiState.value.draft.details.purposeIds
    val nextMode = SurveyWizardRules.defaultCollectionMode(purposeIds, source)
    session.update {
      it.copy(
        mapFeatureSource = source,
        collectionMode = nextMode,
        isMapFeatureSourceOverridden = true,
      )
    }
    val draft = surveyEditor.uiState.value.draft
    val primaryLayer = draft.datasets.firstOrNull { it.kind == DatasetKind.MAP_LAYER }
    when (source) {
      WizardMapFeatureSource.SAMPLE_PLOTS -> {
        if (primaryLayer == null) {
          surveyEditor.addSamplePlotsLayer()
        } else if (!primaryLayer.isGenerated) {
          surveyEditor.updateDataset(primaryLayer.key, WizardDraftEdits::asSamplePlotsLayer)
        }
      }
      WizardMapFeatureSource.FIELD_FREEFORM -> {
        if (primaryLayer != null && primaryLayer.isGenerated) {
          val geometryType =
            SurveyWizardRules.defaultGeometryType(
              purposeIds = draft.details.purposeIds,
              mapFeatureSource = source,
              eudrPlotsUnderFourHaConfirmed = session.value.isEudrPlotsUnderFourHaConfirmed,
              starterTemplateIds =
                starterTemplateIds(draft.details.purposeIds, session.value.starterTemplateId),
            )
          surveyEditor.updateDataset(primaryLayer.key) { layer ->
            WizardDraftEdits.asFieldFreeformLayer(layer, draft.details.title, geometryType)
          }
        } else if (primaryLayer != null) {
          surveyEditor.updateDataset(primaryLayer.key) { it.copy(fieldCreationEnabled = true) }
        }
      }
      WizardMapFeatureSource.IMPORT_FILE -> {
        if (primaryLayer != null && primaryLayer.isGenerated) {
          surveyEditor.updateDataset(primaryLayer.key) { d ->
            d.copy(generator = null, rows = emptyList())
          }
        }
      }
    }
    if (surveyEditor.uiState.value.draft.datasets.any { it.kind == DatasetKind.MAP_LAYER }) {
      ensurePrimaryLayerAndForm()
    }
  }

  override fun updatePrimaryLayerDisplayName(name: String) {
    val primary =
      surveyEditor.uiState.value.draft.datasets.firstOrNull { it.kind == DatasetKind.MAP_LAYER }
        ?: return
    surveyEditor.updateDataset(primary.key) { it.copy(displayName = name) }
  }

  override fun updatePrimaryLayerGeometryType(kind: GeometryType) {
    session.update { it.copy(isGeometryTypeOverridden = true) }
    val draft = surveyEditor.uiState.value.draft
    val primary = draft.datasets.firstOrNull { it.kind == DatasetKind.MAP_LAYER } ?: return
    surveyEditor.updateDataset(primary.key) { it.copy(geometryType = kind) }
    val primaryForm = draft.forms.firstOrNull() ?: return
    surveyEditor.updateForm(primaryForm.key) { form ->
      WizardDraftEdits.withPrimaryGeometryType(form, kind)
    }
  }

  override fun updatePrimaryLayerFieldCreation(enabled: Boolean) {
    val primary =
      surveyEditor.uiState.value.draft.datasets.firstOrNull { it.kind == DatasetKind.MAP_LAYER }
        ?: return
    surveyEditor.updateDataset(primary.key) { it.copy(fieldCreationEnabled = enabled) }
  }

  override fun confirmEudrPlotsUnderFourHa(confirmed: Boolean) {
    session.update { it.copy(isEudrPlotsUnderFourHaConfirmed = confirmed) }
    if (
      !confirmed &&
        SurveyWizardRules.requiresEudrPolygonGuardrail(
          surveyEditor.uiState.value.draft.details.purposeIds,
          starterTemplateIds(
            surveyEditor.uiState.value.draft.details.purposeIds,
            session.value.starterTemplateId,
          ),
        )
    ) {
      updatePrimaryLayerGeometryType(GeometryType.POLYGON)
    }
  }

  override fun importPrimaryMapLayer(plan: MapLayerImportPlan) {
    val draft = surveyEditor.uiState.value.draft
    val existingEmpty =
      draft.datasets.firstOrNull { it.kind == DatasetKind.MAP_LAYER && it.rows.isEmpty() }
    val importedKey = surveyEditor.importMapLayer(plan)
    if (existingEmpty != null && existingEmpty.key != importedKey) {
      surveyEditor.deleteDataset(existingEmpty.key)
    }
    session.update { it.copy(mapFeatureSource = WizardMapFeatureSource.IMPORT_FILE) }
  }

  override fun updateCollectorConsentRequired(required: Boolean) {
    val defaultText =
      if (required && session.value.collectorConsentText.isBlank()) {
        SurveyWizardRules.DEFAULT_COLLECTOR_CONSENT_TEXT
      } else {
        session.value.collectorConsentText
      }
    session.update {
      it.copy(isCollectorConsentRequired = required, collectorConsentText = defaultText)
    }
    surveyEditor.updateSharing {
      it.copy(collectorConsentRequired = required, collectorConsentText = defaultText)
    }
  }

  override fun updateCollectorConsentText(text: String) {
    session.update { it.copy(collectorConsentText = text) }
    surveyEditor.updateSharing { it.copy(collectorConsentText = text) }
  }

  override fun applyCareSovereigntyDefaults() {
    val nextConsentText =
      session.value.collectorConsentText.ifBlank { SurveyWizardRules.DEFAULT_FPIC_CONSENT_TEXT }
    surveyEditor.updateSharing {
      it.copy(
        policy = SharingPolicy.RESTRICTED,
        peerDataVisibility = PeerDataVisibility.OWN_ONLY,
        collectorConsentRequired = true,
        collectorConsentText = nextConsentText,
      )
    }
    session.update {
      it.copy(
        isCareSovereigntyApplied = true,
        isCollectorConsentRequired = true,
        collectorConsentText = nextConsentText,
        careDefaultsAutoReason = null,
      )
    }
  }

  override fun setCareCardExpanded(expanded: Boolean) {
    session.update { it.copy(isCareCardExpanded = expanded) }
  }

  override fun openInSurveyEditor() {
    commitAndOpenEditor(openDashboard = false)
  }

  override fun finishWizard() {
    commitAndOpenEditor(openDashboard = true)
  }

  /**
   * Finalizes the working survey: materializes any pending Step 1 source or primary layer/Form,
   * enforces organization/global creation and design quotas, saves the draft (generating deferred
   * sample plots if needed), waits until the server has accepted every change to the survey (its
   * definition, map features, and sample plots), updates the survey preview item, and only then
   * navigates either to the Survey dashboard ([openDashboard] = `true`) or to the full Survey
   * editor (`false`).
   *
   * If saving fails, the wizard stays where it is and shows why ([CreateSurveyWizardUiState
   * .importErrorMessage]); the edits are kept locally, and finishing again resends them.
   */
  private fun commitAndOpenEditor(openDashboard: Boolean) {
    if (session.value.isFinishing) return
    session.update { it.copy(isFinishing = true, importErrorMessage = null) }
    viewModelScope.launch {
      try {
        commitAndOpenEditorNow(openDashboard)
      } finally {
        session.update { it.copy(isFinishing = false) }
      }
    }
  }

  private suspend fun commitAndOpenEditorNow(openDashboard: Boolean) {
    applyOverviewStartMethodIfNeeded()
    ensurePrimaryLayerAndForm()
    val draft = surveyEditor.uiState.value.draft
    val editorUi = surveyEditor.uiState.value
    val firstVariantIssue =
      editorUi.forms.firstNotNullOfOrNull { formEntry ->
        editorUi.formIssues(formEntry).firstOrNull { it.isVariantCompatibilityIssue() }
      }
    if (firstVariantIssue != null) {
      selectFirstFormVariantIssueQuestion()
      session.update {
        it.copy(
          showFormQuestionsValidationErrors = true,
          validationAttemptCount = it.validationAttemptCount + 1,
          importErrorMessage = firstVariantIssue.message.toUiText(),
        )
      }
      return
    }
    val quotaCheck =
      checkWizardSurveyQuota(
        draft = draft,
        organizations = editorUi.organizations,
        existingSurveys = surveyRepository.observeSurveys().first(),
        signedInUserEmail = editorUi.signedInUserEmail,
        workingSurveyId = session.value.workingSurveyId,
      )
    if (quotaCheck is QuotaCheckResult.Exceeded) {
      session.update { it.copy(importErrorMessage = quotaCheck.message.toUiText()) }
      return
    }

    val finalOrgId = draft.details.organizationId
    val finalOrg = finalOrgId?.let { id -> editorUi.organizations.firstOrNull { it.id == id } }
    val finalTitle = draft.details.title.trim().ifBlank { CreateSurveyUseCase.DEFAULT_TITLE }
    val finalDesc = draft.details.description.trim()

    surveyEditor.updateDetails { d -> d.copy(title = finalTitle) }
    val surveyId = session.value.workingSurveyId.ifBlank { surveyRepository.getActiveSurveyId() }
    val saveError =
      try {
        // Changes the server rejected on an earlier attempt are sent again with this one.
        outboxSyncRepository?.retryRejected(surveyId)
        val savedDraft = surveyEditor.saveWorkingDraftNow() ?: surveyEditor.uiState.value.draft
        surveyRepository.updateSurvey(surveyId) { preview ->
          WizardDraftEdits.finishedSurveyPreview(
            preview = preview,
            surveyId = surveyId,
            title = finalTitle,
            description = finalDesc,
            organizationId = finalOrgId,
            organization = finalOrg,
            savedDraft = savedDraft,
          )
        }
        outboxSyncRepository?.awaitSurveySaved(surveyId)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        DomainError.Unknown(e)
      }
    if (saveError != null) {
      session.update {
        it.copy(
          importErrorMessage =
            Res.string.wizard_save_failed.toUiText(ErrorMessages.reason(saveError))
        )
      }
      return
    }
    surveyRepository.setActiveSurveyId(surveyId)
    session.value = CreateSurveyWizardSession()
    _events.tryEmit(CreateSurveyWizardEvent.OpenSurveyEditor(surveyId))
    if (openDashboard) {
      surveyEditor.close()
    }
  }

  override fun createDatasetForChoiceQuestion(questionIndex: Int, kind: DatasetKind) {
    surveyEditor.createDatasetForChoiceQuestion(questionIndex, kind)
  }

  override fun cancelWizard() {
    viewModelScope.launch {
      val workingId = session.value.workingSurveyId
      val previousId = session.value.previousActiveSurveyId
      session.value = CreateSurveyWizardSession()
      surveyEditor.discardChanges()
      if (workingId.isNotBlank()) {
        surveyEditorRepository.discardWorkingCopy(workingId)
        surveyRepository.deleteSurvey(workingId)
      }
      if (workingId.isNotBlank() && previousId.isNotBlank() && workingId != previousId) {
        surveyRepository.setActiveSurveyId(previousId)
      }
      _events.tryEmit(CreateSurveyWizardEvent.Cancelled)
    }
  }
}
