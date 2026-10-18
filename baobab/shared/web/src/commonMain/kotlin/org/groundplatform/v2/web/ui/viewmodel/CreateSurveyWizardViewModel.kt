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
package org.groundplatform.v2.web.ui.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.groundplatform.v2.app.domain.model.LibraryPrograms
import org.groundplatform.v2.app.domain.model.Organization
import org.groundplatform.v2.app.domain.model.OrganizationLibrary
import org.groundplatform.v2.app.domain.model.QuotaAndCostEnforcer
import org.groundplatform.v2.app.domain.model.QuotaCheckResult
import org.groundplatform.v2.app.domain.model.SurveyLifecycleState
import org.groundplatform.v2.app.domain.model.SurveyPreviewItem
import org.groundplatform.v2.app.domain.model.SurveyThumbnailGenerator
import org.groundplatform.v2.app.domain.model.editor.CollectEarthIdmParser
import org.groundplatform.v2.app.domain.model.editor.CollectEarthProjectImport
import org.groundplatform.v2.app.domain.model.editor.DatasetKind
import org.groundplatform.v2.app.domain.model.editor.EditorQuestionType
import org.groundplatform.v2.app.domain.model.editor.EntityProperty
import org.groundplatform.v2.app.domain.model.editor.FormImport
import org.groundplatform.v2.app.domain.model.editor.GeometryKind
import org.groundplatform.v2.app.domain.model.editor.ImportedForm
import org.groundplatform.v2.app.domain.model.editor.PeerDataVisibility
import org.groundplatform.v2.app.domain.model.editor.PropertyType
import org.groundplatform.v2.app.domain.model.editor.SampleDesignConfig
import org.groundplatform.v2.app.domain.model.editor.SamplePlotProperties
import org.groundplatform.v2.app.domain.model.editor.SharingPolicy
import org.groundplatform.v2.app.domain.repository.AuthRepository
import org.groundplatform.v2.app.domain.repository.LibraryRepository
import org.groundplatform.v2.app.domain.repository.OrganizationRepository
import org.groundplatform.v2.app.domain.repository.SurveyEditorRepository
import org.groundplatform.v2.app.domain.repository.SurveyRepository
import org.groundplatform.v2.app.domain.usecase.CreateSurveyUseCase
import org.groundplatform.v2.app.domain.usecase.ExportSurveyDataUseCase
import org.groundplatform.v2.app.domain.usecase.ResolveLibraryUseCase
import org.groundplatform.v2.web.domain.model.editor.CreateSurveyWizardStep
import org.groundplatform.v2.web.domain.model.editor.MapLayerImportPlan
import org.groundplatform.v2.web.domain.model.editor.SurveyCollectionMode
import org.groundplatform.v2.web.domain.model.editor.SurveyWizardRules
import org.groundplatform.v2.web.domain.model.editor.WizardMapFeatureSource
import org.groundplatform.v2.web.domain.model.editor.WizardStartMethod
import org.groundplatform.v2.web.domain.usecase.ConfigureWizardSurveyDraftUseCase
import org.groundplatform.v2.web.domain.usecase.EvaluateWizardImpactReadinessUseCase
import org.groundplatform.v2.web.ui.state.CreateSurveyWizardEvent
import org.groundplatform.v2.web.ui.state.CreateSurveyWizardUiState
import org.groundplatform.v2.web.ui.state.SurveyEditorSection
import org.groundplatform.v2.web.ui.state.SurveyEditorUiState

/** User intents for the 6-step Ground 2.0 Survey Creation Wizard. */
interface CreateSurveyWizardActions {
  fun startWizard(defaultOrganizationId: String? = null)

  fun selectStep(step: CreateSurveyWizardStep)

  fun nextStep()

  fun previousStep()

  fun skipStep()

  fun updateTitle(title: String)

  fun updateDescription(description: String)

  fun selectOrganization(organizationId: String?)

  fun selectStartMethod(method: WizardStartMethod)

  fun selectCopySourceSurvey(surveyId: String?)

  fun importXFormsXml(fileName: String, xmlText: String)

  fun selectImportedCepProject(fileName: String)

  fun clearImportedXForms()

  fun selectCollectionMode(mode: SurveyCollectionMode)

  /**
   * Selects or clears Purpose Pack [packId] (or "Other"); several purposes may be selected at once.
   * Selecting a pack also selects the programs it offers.
   */
  fun togglePurposePack(packId: String)

  fun updateOtherPurpose(text: String)

  fun toggleProgram(programId: String)

  fun selectMapFeatureSource(source: WizardMapFeatureSource)

  fun updatePrimaryLayerDisplayName(name: String)

  fun updatePrimaryLayerGeometryKind(kind: GeometryKind)

  fun updatePrimaryLayerFieldCreation(enabled: Boolean)

  fun confirmEudrPlotsUnderFourHa(confirmed: Boolean)

  fun importPrimaryMapLayer(plan: MapLayerImportPlan)

  fun updateCollectorConsentRequired(required: Boolean)

  fun updateCollectorConsentText(text: String)

  fun applyCareSovereigntyDefaults()

  fun openInSurveyEditor()

  fun finishWizard()

  fun cancelWizard()
}

/**
 * Coordinates the 6-step Ground 2.0 Survey Creation Wizard (`CreateSurveyWizardPage`) and keeps its
 * working survey synchronized with [SurveyEditorViewModel] so the embedded Survey area, Sampling
 * design, Form editor, and Sharing components operate on the live draft.
 */
class CreateSurveyWizardViewModel(
  private val surveyRepository: SurveyRepository,
  private val surveyEditorRepository: SurveyEditorRepository,
  private val organizationRepository: OrganizationRepository,
  private val authRepository: AuthRepository,
  private val libraryRepository: LibraryRepository,
  private val createSurveyUseCase: CreateSurveyUseCase,
  private val surveyEditor: SurveyEditorViewModel,
  private val scope: CoroutineScope,
  private val resolveLibraryUseCase: ResolveLibraryUseCase = ResolveLibraryUseCase(),
  private val exportSurveyDataUseCase: ExportSurveyDataUseCase = ExportSurveyDataUseCase(),
  private val evaluateImpactReadiness: EvaluateWizardImpactReadinessUseCase =
    EvaluateWizardImpactReadinessUseCase(exportSurveyDataUseCase),
  private val configureWizardSurveyDraft: ConfigureWizardSurveyDraftUseCase =
    ConfigureWizardSurveyDraftUseCase(),
) : CreateSurveyWizardActions {

  private data class Session(
    val step: CreateSurveyWizardStep = CreateSurveyWizardStep.OVERVIEW,
    val workingSurveyId: String = "",
    val previousActiveSurveyId: String = "",
    val title: String = "",
    val showOverviewValidationErrors: Boolean = false,
    val showPurposeValidationErrors: Boolean = false,
    val showSurveyAreaValidationErrors: Boolean = false,
    val validationAttemptCount: Int = 0,
    val startMethod: WizardStartMethod = WizardStartMethod.FROM_SCRATCH,
    val copySourceSurveyId: String? = null,
    val importedXFormsFileName: String? = null,
    val importedXForms: ImportedForm? = null,
    val importedCepProjects: List<CollectEarthProjectImport> = emptyList(),
    val selectedCepProject: CollectEarthProjectImport? = null,
    val importErrorMessage: String? = null,
    val collectionMode: SurveyCollectionMode = SurveyCollectionMode.FIELD_SURVEY,
    val mapFeatureSource: WizardMapFeatureSource = WizardMapFeatureSource.FIELD_FREEFORM,
    val isMapFeatureSourceOverridden: Boolean = false,
    val isGeometryKindOverridden: Boolean = false,
    val isEudrPlotsUnderFourHaConfirmed: Boolean = false,
    val isCollectorConsentRequired: Boolean = false,
    val collectorConsentText: String = "",
    val seededPurposeIds: Set<String> = emptySet(),
    /** Keys of the Forms seeded from [seededPurposeIds]' templates (the primary Form first). */
    val seededFormKeys: Set<String> = emptySet(),
    val appliedStartMethod: WizardStartMethod? = null,
    val appliedCopySourceId: String? = null,
    val appliedImportedXFormsFileName: String? = null,
    val isCareSovereigntyApplied: Boolean = false,
  )

  private val session = MutableStateFlow(Session())

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
        buildUiState(s, editorUi, surveys, organizations, libraries)
      }
      .stateIn(scope, SharingStarted.Eagerly, CreateSurveyWizardUiState())

  private fun buildUiState(
    s: Session,
    editorUi: SurveyEditorUiState,
    surveys: List<SurveyPreviewItem>,
    organizations: List<Organization>,
    libraries: Map<String, OrganizationLibrary>,
  ): CreateSurveyWizardUiState {
    val draft = editorUi.draft
    val details = draft.details
    val email = editorUi.signedInUserEmail
    val memberOrgs = organizations.filter { !it.isSynthetic && it.isMember(email) }
    val selectedOrg =
      details.organizationId?.let { id -> organizations.firstOrNull { it.id == id } }
    val library = resolveLibraryUseCase.forOrganization(libraries, selectedOrg?.id)
    val sortedPacks = SurveyWizardRules.sortPurposePacks(library, s.collectionMode)
    val selectedPacks = sortedPacks.filter { it.id in details.purposeIds }
    val offeredPrograms = LibraryPrograms.offeredFor(selectedPacks, details.programIds)
    val primaryLayer = draft.datasets.firstOrNull { it.kind == DatasetKind.MAP_LAYER }
    val primaryForm = draft.forms.firstOrNull()
    val existingSurveys =
      surveys.filter { it.id != s.workingSurveyId }.sortedBy { it.title.lowercase() }

    val readiness =
      evaluateImpactReadiness(
        draft = draft,
        library = library,
        selectedOrganization = selectedOrg,
        language = details.defaultLanguage.ifBlank { "en" },
        isCareSovereigntyApplied = s.isCareSovereigntyApplied,
        isCollectorConsentRequired = s.isCollectorConsentRequired,
      )

    return CreateSurveyWizardUiState(
      currentStep = s.step,
      workingSurveyId = s.workingSurveyId,
      previousActiveSurveyId = s.previousActiveSurveyId,
      title = s.title,
      showOverviewValidationErrors = s.showOverviewValidationErrors,
      description = details.description,
      organizationId = details.organizationId,
      startMethod = s.startMethod,
      copySourceSurveyId = s.copySourceSurveyId ?: existingSurveys.firstOrNull()?.id,
      importedXFormsFileName = s.importedXFormsFileName,
      importedXForms = s.importedXForms,
      importedCepProjects = s.importedCepProjects,
      selectedCepProject = s.selectedCepProject,
      importErrorMessage = s.importErrorMessage,
      collectionMode = s.collectionMode,
      purposeIds = details.purposeIds,
      otherPurpose = details.otherPurpose,
      showPurposeValidationErrors = s.showPurposeValidationErrors,
      surveyArea = details.surveyArea,
      showSurveyAreaValidationErrors = s.showSurveyAreaValidationErrors,
      validationAttemptCount = s.validationAttemptCount,
      mapFeatureSource = s.mapFeatureSource,
      mapFeatureSourcePreselected =
        s.isMapFeatureSourceOverridden ||
          SurveyWizardRules.purposeMapFeatureSource(details.purposeIds) != null,
      eudrPlotsUnderFourHaConfirmed = s.isEudrPlotsUnderFourHaConfirmed,
      collectorConsentRequired = s.isCollectorConsentRequired,
      collectorConsentText = s.collectorConsentText,
      existingSurveys = existingSurveys,
      memberOrganizations = memberOrgs,
      selectedOrganization = selectedOrg,
      library = library,
      sortedPurposePacks = sortedPacks,
      offeredProgramIds = offeredPrograms,
      primaryMapLayer = primaryLayer,
      primaryForm = primaryForm,
      impactReadiness = readiness,
    )
  }

  override fun startWizard(defaultOrganizationId: String?) {
    scope.launch {
      val previousActiveId = surveyRepository.getActiveSurveyId()
      val existingSurveys = surveyRepository.getSurveys()
      val organizations = organizationRepository.observeOrganizations().first()
      val auth = authRepository.observeSession().first()
      val ownerEmail = auth.profile.email
      val ownerName = auth.profile.displayName.ifBlank { ownerEmail }

      val org =
        defaultOrganizationId
          ?.takeIf { it.isNotBlank() && it != Organization.ALL_USERS_ID }
          ?.let { id -> organizations.firstOrNull { it.id == id && !it.isSynthetic } }
          ?: organizations.firstOrNull { !it.isSynthetic && it.isMember(ownerEmail) }

      val newSurvey =
        createSurveyUseCase.newSurvey(
          title = "",
          existingSurveys = existingSurveys,
          organization = org,
          ownerEmail = ownerEmail,
        )
      createSurveyUseCase(
        survey = newSurvey,
        ownerName = ownerName,
        purposeIds = emptyList(),
        programIds = emptyList(),
      )
      // Start with a blank title in the stored draft so Step 1 prompts cleanly for a survey title.
      val createdDraft = surveyEditorRepository.getDraft(newSurvey.id)
      val initialSharing =
        if (org != null) {
          val usesCare = SurveyWizardRules.usesIplcCareDefaults(org.organizationType, emptySet())
          createdDraft.sharing.copy(
            policy = if (usesCare) SharingPolicy.RESTRICTED else SharingPolicy.ORGANIZATION,
            peerDataVisibility =
              if (usesCare) PeerDataVisibility.OWN_ONLY else PeerDataVisibility.ALL,
          )
        } else {
          createdDraft.sharing
        }
      val initialDraft =
        createdDraft.copy(
          details =
            createdDraft.details.copy(
              title = "",
              description = "",
              organizationId = org?.id,
              defaultLanguage = "en",
              supportedLanguages = listOf("en"),
              surveyArea = null,
            ),
          sharing = initialSharing,
        )
      surveyEditorRepository.saveDraft(newSurvey.id, initialDraft)
      session.value =
        Session(
          step = CreateSurveyWizardStep.OVERVIEW,
          workingSurveyId = newSurvey.id,
          previousActiveSurveyId = previousActiveId,
          copySourceSurveyId = existingSurveys.firstOrNull()?.id,
        )
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
      else -> {}
    }
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

  private fun transitionToStep(target: CreateSurveyWizardStep) {
    val current = session.value.step
    if (current == CreateSurveyWizardStep.OVERVIEW && target.ordinal > current.ordinal) {
      applyOverviewStartMethodIfNeeded()
    }
    if (target.ordinal >= CreateSurveyWizardStep.MAP_FEATURES.ordinal) {
      ensurePrimaryLayerAndForm()
    }
    if (target == CreateSurveyWizardStep.FORM_QUESTIONS) {
      val primaryFormKey = surveyEditor.uiState.value.draft.forms.firstOrNull()?.key
      if (primaryFormKey != null) {
        surveyEditor.select(SurveyEditorSection.Form(primaryFormKey))
        surveyEditor
          .formEditor(primaryFormKey)
          .selectPreviewTarget(session.value.collectionMode.defaultPreviewTarget)
      }
    }
    if (target == CreateSurveyWizardStep.SHARING) {
      maybeApplyCareDefaultsOnSharingStep()
    }
    session.update { it.copy(step = target) }
  }

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
          scope.launch {
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
   */
  private fun ensurePrimaryLayerAndForm() {
    val s = session.value
    val library = uiState.value.library
    val currentPurposeIds = surveyEditor.uiState.value.draft.details.purposeIds
    val effectiveMode =
      if (s.isMapFeatureSourceOverridden) {
        s.collectionMode
      } else {
        SurveyWizardRules.defaultCollectionMode(currentPurposeIds, s.mapFeatureSource)
      }
    val hasPurposeSelectionChanged = currentPurposeIds.toSet() != s.seededPurposeIds
    var seededFormKeys = s.seededFormKeys

    surveyEditor.replaceWorkingDraft({ current ->
      val syncResult =
        configureWizardSurveyDraft.synchronizePrimaryLayerAndForms(
          current = current,
          library = library,
          startMethod = s.startMethod,
          effectiveMode = effectiveMode,
          mapFeatureSource = s.mapFeatureSource,
          isGeometryKindOverridden = s.isGeometryKindOverridden,
          isEudrPlotsUnderFourHaConfirmed = s.isEudrPlotsUnderFourHaConfirmed,
          hasPurposeSelectionChanged = hasPurposeSelectionChanged,
          previousSeededFormKeys = s.seededFormKeys,
        )
      seededFormKeys = syncResult.seededFormKeys
      syncResult.draft
    })

    if (hasPurposeSelectionChanged) {
      session.update {
        it.copy(seededPurposeIds = currentPurposeIds.toSet(), seededFormKeys = seededFormKeys)
      }
    }
  }

  private fun maybeApplyCareDefaultsOnSharingStep() {
    if (session.value.isCareSovereigntyApplied) return
    val draft = surveyEditor.uiState.value.draft
    val org = uiState.value.selectedOrganization
    val conceptIds = evaluateImpactReadiness.extractLinkedConceptIds(draft)
    if (SurveyWizardRules.usesIplcCareDefaults(org?.organizationType, conceptIds)) {
      applyCareSovereigntyDefaults()
    }
  }

  override fun updateTitle(title: String) {
    session.update { it.copy(title = title, showOverviewValidationErrors = true) }
    surveyEditor.updateDetails { it.copy(title = title) }
  }

  override fun updateDescription(description: String) {
    surveyEditor.updateDetails { it.copy(description = description) }
  }

  override fun selectOrganization(organizationId: String?) {
    surveyEditor.setOrganization(organizationId)
    val org = organizationId?.let { id ->
      surveyEditor.uiState.value.organizations.firstOrNull { it.id == id }
    }
    if (org != null) {
      val isIplc = SurveyWizardRules.usesIplcCareDefaults(org.organizationType, emptySet())
      surveyEditor.updateSharing {
        it.copy(
          policy = if (isIplc) SharingPolicy.RESTRICTED else SharingPolicy.ORGANIZATION,
          peerDataVisibility = if (isIplc) PeerDataVisibility.OWN_ONLY else PeerDataVisibility.ALL,
        )
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
          importErrorMessage =
            "Could not parse any XForms or Collect Earth questions from \"$fileName\".",
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
        isGeometryKindOverridden = hasPlotGrid || it.isGeometryKindOverridden,
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
    val toggledPackProgramIds =
      if (packId != SurveyWizardRules.OTHER_PURPOSE_ID && !alreadySelected) {
        library.purposePack(packId)?.programIds.orEmpty()
      } else {
        emptyList()
      }
    val previousPackProgramIds =
      currentIds
        .flatMap { id ->
          if (id != SurveyWizardRules.OTHER_PURPOSE_ID) {
            library.purposePack(id)?.programIds.orEmpty()
          } else {
            emptyList()
          }
        }
        .toSet()
    val newProgramIds =
      (details.programIds.filterNot { it in previousPackProgramIds } + toggledPackProgramIds)
        .distinct()
    surveyEditor.updateDetails {
      it.copy(
        purposeIds = newPurposeIds,
        programIds = newProgramIds,
      )
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
        isGeometryKindOverridden = false,
      )
    }
    if (
      !alreadySelected &&
        packId == "eudr_due_diligence" &&
        !session.value.isEudrPlotsUnderFourHaConfirmed
    ) {
      updatePrimaryLayerGeometryKind(GeometryKind.POLYGON)
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
          val design = SampleDesignConfig()
          surveyEditor.updateDataset(primaryLayer.key) { d ->
            d.copy(
              displayName =
                if (d.displayName == "Survey sites" || d.displayName == d.id) "Sample plots"
                else d.displayName,
              geometryKind = design.geometryKind,
              keyProperty = SamplePlotProperties.PLOT_ID,
              labelProperty = SamplePlotProperties.PLOT_ID,
              fieldCreationEnabled = false,
              linkedFormKey = null,
              properties = SamplePlotProperties.schema,
              rows = emptyList(),
              generator = design,
            )
          }
        }
      }
      WizardMapFeatureSource.FIELD_FREEFORM -> {
        if (primaryLayer != null && primaryLayer.isGenerated) {
          surveyEditor.updateDataset(primaryLayer.key) { d ->
            d.copy(
              displayName = draft.details.title.ifBlank { "Survey sites" },
              geometryKind =
                SurveyWizardRules.defaultGeometryKind(
                  purposeIds = draft.details.purposeIds,
                  mapFeatureSource = source,
                  eudrPlotsUnderFourHaConfirmed = session.value.isEudrPlotsUnderFourHaConfirmed,
                ),
              keyProperty = "id",
              labelProperty = "id",
              fieldCreationEnabled = true,
              properties = listOf(EntityProperty("id", "ID", PropertyType.TEXT, required = true)),
              rows = emptyList(),
              generator = null,
            )
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

  override fun updatePrimaryLayerGeometryKind(kind: GeometryKind) {
    session.update { it.copy(isGeometryKindOverridden = true) }
    val draft = surveyEditor.uiState.value.draft
    val primary = draft.datasets.firstOrNull { it.kind == DatasetKind.MAP_LAYER } ?: return
    surveyEditor.updateDataset(primary.key) { it.copy(geometryKind = kind) }
    val primaryForm = draft.forms.firstOrNull() ?: return
    val geomQuestionType =
      when (kind) {
        GeometryKind.POINT -> EditorQuestionType.LOCATION
        GeometryKind.LINE -> EditorQuestionType.LINE
        GeometryKind.POLYGON -> EditorQuestionType.POLYGON
      }
    surveyEditor.updateForm(primaryForm.key) { form ->
      val firstGeomIdx = form.questions.indexOfFirst { it.type.isGeometry }
      if (firstGeomIdx >= 0) {
        form.copy(
          questions =
            form.questions.mapIndexed { idx, q ->
              if (idx == firstGeomIdx) {
                q.copy(
                  type = geomQuestionType,
                  label =
                    if (kind == GeometryKind.POLYGON) "Record plot boundary"
                    else "Record site location",
                )
              } else {
                q
              }
            }
        )
      } else {
        form
      }
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
          surveyEditor.uiState.value.draft.details.purposeIds
        )
    ) {
      updatePrimaryLayerGeometryKind(GeometryKind.POLYGON)
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
      )
    }
  }

  override fun openInSurveyEditor() {
    commitAndOpenEditor(openDashboard = false)
  }

  override fun finishWizard() {
    commitAndOpenEditor(openDashboard = true)
  }

  private fun commitAndOpenEditor(openDashboard: Boolean) {
    scope.launch {
      applyOverviewStartMethodIfNeeded()
      ensurePrimaryLayerAndForm()
      val draft = surveyEditor.uiState.value.draft
      val finalOrgId = draft.details.organizationId
      val finalOrg = finalOrgId?.let { id ->
        surveyEditor.uiState.value.organizations.firstOrNull { it.id == id }
      }
      val globalLimits =
        surveyEditor.uiState.value.organizations.firstOrNull { it.isSynthetic }?.quotaLimits
      val existingSurveys = surveyRepository.observeSurveys().first()
      val createCheck =
        QuotaAndCostEnforcer.checkCanCreateSurvey(
          ownerEmail = surveyEditor.uiState.value.signedInUserEmail,
          organizationId = finalOrgId,
          existingSurveys = existingSurveys,
          globalLimits = globalLimits,
          organization = finalOrg,
          excludeSurveyId = session.value.workingSurveyId,
        )
      if (createCheck is QuotaCheckResult.Exceeded) {
        session.update { it.copy(importErrorMessage = createCheck.reason) }
        return@launch
      }

      val designCheck =
        QuotaAndCostEnforcer.checkSurveyDesign(
          draft = draft,
          organization = finalOrg,
          globalLimits = globalLimits,
        )
      if (designCheck is QuotaCheckResult.Exceeded) {
        session.update { it.copy(importErrorMessage = designCheck.reason) }
        return@launch
      }

      val finalTitle = draft.details.title.trim().ifBlank { CreateSurveyUseCase.DEFAULT_TITLE }
      val finalDesc = draft.details.description.trim()
      val finalLocation =
        draft.details.surveyArea?.name?.ifBlank { null } ?: finalOrg?.name ?: "No survey area yet"

      surveyEditor.updateDetails { d -> d.copy(title = finalTitle) }
      val savedDraft = surveyEditor.saveWorkingDraftNow() ?: surveyEditor.uiState.value.draft
      val surveyId = session.value.workingSurveyId.ifBlank { surveyRepository.getActiveSurveyId() }
      surveyRepository.updateSurvey(surveyId) { preview ->
        preview.copy(
          title = finalTitle,
          description = finalDesc,
          organizationId = finalOrgId,
          location = finalLocation,
          state = SurveyLifecycleState.DRAFT,
          sharingPolicy = savedDraft.sharing.policy,
          peerDataVisibility = savedDraft.sharing.peerDataVisibility,
          collectorConsentRequired = savedDraft.sharing.collectorConsentRequired,
          collectorConsentText = savedDraft.sharing.collectorConsentText,
          entityCount =
            savedDraft.datasets.filter { it.kind == DatasetKind.MAP_LAYER }.sumOf { it.rows.size },
          thumbnail = SurveyThumbnailGenerator.fromDraft(surveyId, savedDraft),
        )
      }
      surveyRepository.setActiveSurveyId(surveyId)
      _events.tryEmit(CreateSurveyWizardEvent.OpenSurveyEditor(surveyId))
      if (openDashboard) {
        surveyEditor.close()
      }
    }
  }

  override fun cancelWizard() {
    scope.launch {
      val workingId = session.value.workingSurveyId
      val previousId = session.value.previousActiveSurveyId
      surveyEditor.discardChanges()
      if (workingId.isNotBlank()) {
        surveyRepository.deleteSurvey(workingId)
      }
      if (workingId.isNotBlank() && previousId.isNotBlank() && workingId != previousId) {
        surveyRepository.setActiveSurveyId(previousId)
      }
      _events.tryEmit(CreateSurveyWizardEvent.Cancelled)
    }
  }
}
