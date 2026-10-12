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
package org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel

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
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactGoals
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryPrograms
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationLibrary
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyLifecycleState
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyThumbnailGenerator
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.CreateSurveyWizardStep
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.DatasetKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorForm
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorFormTemplates
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestion
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestionType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorSaveTo
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityDataset
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityProperty
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.FormDatasetLinks
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.FormIds
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.FormImport
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.GeometryKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.ImportedForm
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.LayerStyle
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.MapLayerImportPlan
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.PeerDataVisibility
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.PropertyType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SampleDesignConfig
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SamplePlotProperties
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SaveToMode
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SaveToRules
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SharingPolicy
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyCollectionMode
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyEditorDerivation
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyEditorDraft
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyEditorForm
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyWizardRules
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.WizardMapFeatureSource
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.WizardStartMethod
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.slugify
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.toEditorDataset
import org.groundplatform.v2.devtools.prototypeapp.domain.model.geometryKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.hasGeometry
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.AuthRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.LibraryRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.OrganizationRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyEditorRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.CreateSurveyUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ExportSurveyDataUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ResolveLibraryUseCase
import org.groundplatform.v2.devtools.prototypeapp.ui.state.CreateSurveyWizardEvent
import org.groundplatform.v2.devtools.prototypeapp.ui.state.CreateSurveyWizardUiState
import org.groundplatform.v2.devtools.prototypeapp.ui.state.SurveyEditorSection
import org.groundplatform.v2.devtools.prototypeapp.ui.state.SurveyEditorUiState
import org.groundplatform.v2.devtools.prototypeapp.ui.state.WizardImpactReadiness

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

  fun clearImportedXForms()

  fun selectCollectionMode(mode: SurveyCollectionMode)

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
    val importErrorMessage: String? = null,
    val collectionMode: SurveyCollectionMode = SurveyCollectionMode.FIELD_SURVEY,
    val mapFeatureSource: WizardMapFeatureSource = WizardMapFeatureSource.FIELD_FREEFORM,
    val userOverrodeMapFeatureSource: Boolean = false,
    val userOverrodeGeometryKind: Boolean = false,
    val eudrPlotsUnderFourHaConfirmed: Boolean = false,
    val collectorConsentRequired: Boolean = false,
    val collectorConsentText: String = "",
    val seededPurposeIds: Set<String> = emptySet(),
    val appliedStartMethod: WizardStartMethod? = null,
    val appliedCopySourceId: String? = null,
    val appliedImportedXFormsFileName: String? = null,
    val careDefaultsAppliedForOrgOrConcepts: Boolean = false,
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
      computeImpactReadiness(
        draft = draft,
        library = library,
        selectedOrg = selectedOrg,
        language = details.defaultLanguage.ifBlank { "en" },
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
      importErrorMessage = s.importErrorMessage,
      collectionMode = s.collectionMode,
      purposeIds = details.purposeIds,
      otherPurpose = details.otherPurpose,
      showPurposeValidationErrors = s.showPurposeValidationErrors,
      surveyArea = details.surveyArea,
      showSurveyAreaValidationErrors = s.showSurveyAreaValidationErrors,
      validationAttemptCount = s.validationAttemptCount,
      mapFeatureSource = s.mapFeatureSource,
      eudrPlotsUnderFourHaConfirmed = s.eudrPlotsUnderFourHaConfirmed,
      collectorConsentRequired = s.collectorConsentRequired,
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

  private fun computeImpactReadiness(
    draft: SurveyEditorDraft,
    library: org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ResolvedLibrary,
    selectedOrg: Organization?,
    language: String,
  ): WizardImpactReadiness {
    val purposeIds = draft.details.purposeIds
    val selectedPacks = purposeIds.mapNotNull(library::purposePack)
    val packTitles = buildList {
      addAll(selectedPacks.map { it.title.get(language).ifBlank { it.id } })
      if (SurveyWizardRules.OTHER_PURPOSE_ID in purposeIds) {
        val otherText = draft.details.otherPurpose.trim()
        add(if (otherText.isNotEmpty()) "Other: $otherText" else "Other")
      }
    }
    val programLabels = draft.details.programIds.map(LibraryPrograms::label)

    val allQuestions = draft.forms.flatMap { it.form.questions }
    val linkedConceptIds = buildSet {
      allQuestions.forEach { q -> q.conceptLink?.conceptId?.let(::add) }
      draft.datasets.forEach { d ->
        d.properties.forEach { p -> p.conceptLink?.conceptId?.let(::add) }
      }
    }

    val goalIds = buildSet {
      selectedPacks.forEach { addAll(it.goals) }
      linkedConceptIds.forEach { id -> library.concept(id)?.goals?.let(::addAll) }
    }
    val goalTitles = goalIds.map(ImpactGoals::label)

    val primaryDataset = draft.datasets.firstOrNull { it.kind == DatasetKind.MAP_LAYER }
    val enabledProfiles = exportSurveyDataUseCase.enabledProfiles(library, purposeIds)
    val exportPlans =
      if (primaryDataset != null) {
        enabledProfiles.map { profile ->
          exportSurveyDataUseCase.plan(
            profile = profile,
            dataset = primaryDataset,
            forms = draft.forms,
            library = library,
            entities = emptyList(),
            language = language,
          )
        }
      } else {
        emptyList()
      }

    val careApplied =
      draft.sharing.policy == SharingPolicy.RESTRICTED &&
        draft.sharing.peerDataVisibility == PeerDataVisibility.OWN_ONLY &&
        SurveyWizardRules.usesIplcCareDefaults(selectedOrg?.organizationType, linkedConceptIds)

    return WizardImpactReadiness(
      selectedPackTitles = packTitles,
      selectedProgramLabels = programLabels,
      linkedConceptCount = allQuestions.count { it.conceptLink != null },
      totalQuestionCount = allQuestions.count { it.type != EditorQuestionType.NOTE },
      goalTitles = goalTitles,
      exportPlans = exportPlans,
      careSovereigntyApplied = careApplied,
      excludeFromPlatformAggregates = selectedOrg?.excludeFromPlatformAggregates == true,
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
              val copiedDatasets =
                sourceDraft.datasets.map { d ->
                  // Clone schema and sample design config, clearing collected entity rows.
                  d.copy(
                    rows = emptyList(),
                    generator = d.generator?.copy(lastRun = null),
                  )
                }
              val copiedForms =
                sourceDraft.forms.map { entry ->
                  entry.copy(form = entry.form.copy(formId = FormIds.newFormId()))
                }
              current.copy(
                details =
                  current.details.copy(
                    defaultLanguage = sourceDraft.details.defaultLanguage,
                    supportedLanguages = sourceDraft.details.supportedLanguages,
                    surveyArea = sourceDraft.details.surveyArea ?: current.details.surveyArea,
                    purposeIds =
                      (current.details.purposeIds + sourceDraft.details.purposeIds).distinct(),
                    otherPurpose =
                      sourceDraft.details.otherPurpose.ifBlank { current.details.otherPurpose },
                    programIds =
                      (current.details.programIds + sourceDraft.details.programIds).distinct(),
                  ),
                forms = copiedForms,
                datasets = copiedDatasets,
                nextKeyId = maxOf(current.nextKeyId, sourceDraft.nextKeyId + 10),
              )
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
        if (
          imported != null &&
            (s.appliedStartMethod != WizardStartMethod.IMPORT_XFORMS ||
              s.appliedImportedXFormsFileName != s.importedXFormsFileName)
        ) {
          // Clear previously auto-seeded forms before importing the selected XForms file.
          surveyEditor.replaceWorkingDraft({ current ->
            current.copy(forms = emptyList(), datasets = emptyList())
          })
          val formKey = surveyEditor.importForm(imported)
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
   * Ensures the working draft has a single primary `MAP_LAYER` and a single starting `EditorForm`
   * configured according to the selected Purpose Pack and [WizardMapFeatureSource].
   */
  private fun ensurePrimaryLayerAndForm() {
    val s = session.value
    val library = uiState.value.library
    val currentPurposeIds = surveyEditor.uiState.value.draft.details.purposeIds
    val effectiveMode =
      if (s.userOverrodeMapFeatureSource) {
        s.collectionMode
      } else {
        SurveyWizardRules.defaultCollectionMode(currentPurposeIds, s.mapFeatureSource)
      }
    val purposeSelectionChanged = currentPurposeIds.toSet() != s.seededPurposeIds

    surveyEditor.replaceWorkingDraft({ current ->
      var datasets = current.datasets
      var forms = current.forms
      var nextId = current.nextKeyId
      fun newKey(prefix: String) = "$prefix${nextId++}"

      val defaultGeomKind =
        SurveyWizardRules.defaultGeometryKind(
          purposeIds = current.details.purposeIds,
          mapFeatureSource = s.mapFeatureSource,
          eudrPlotsUnderFourHaConfirmed = s.eudrPlotsUnderFourHaConfirmed,
        )

      // 1. Seed or replace the single starting Form when Purpose selection changes in FROM_SCRATCH.
      if (purposeSelectionChanged && s.startMethod == WizardStartMethod.FROM_SCRATCH) {
        val nonOtherIds =
          current.details.purposeIds.filter { it != SurveyWizardRules.OTHER_PURPOSE_ID }
        val templates = library.templatesForPurposes(nonOtherIds)
        if (templates.isNotEmpty()) {
          val language = current.details.defaultLanguage.ifBlank { "en" }
          val primaryTemplate = templates.first()
          val baseTitle =
            primaryTemplate.title
              .get(language)
              .ifBlank { primaryTemplate.form.title }
              .ifBlank { "Site assessment" }
          val mergedQuestions = buildList {
            val seenNames = mutableSetOf<String>()
            templates.forEach { tmpl ->
              tmpl.form.questions.forEach { q ->
                if (seenNames.add(q.name)) {
                  add(
                    if (q.choiceDatasetId != null && datasets.none { it.id == q.choiceDatasetId }) {
                      q.copy(
                        choiceDatasetId = null,
                        choiceSource =
                          org.groundplatform.v2.devtools.prototypeapp.domain.model.editor
                            .ChoiceSource
                            .MANUAL,
                        allowAddEntity = false,
                      )
                    } else {
                      q
                    }
                  )
                }
              }
            }
          }
          val existingFormKey = forms.firstOrNull()?.key ?: newKey("f")
          val seededForm =
            primaryTemplate.form.copy(
              formId = forms.firstOrNull()?.form?.formId ?: FormIds.newFormId(),
              title = baseTitle,
              questions = mergedQuestions,
              saveTo = EditorSaveTo(),
            )
          forms = listOf(SurveyEditorForm(existingFormKey, seededForm))
          // Keep only the primary map layer when reseeding from scratch.
          datasets = datasets.filter { it.kind == DatasetKind.MAP_LAYER }.take(1)
        } else if (s.seededPurposeIds.isNotEmpty()) {
          forms = emptyList()
          datasets = datasets.filter { it.kind == DatasetKind.MAP_LAYER }.take(1)
        }
      }

      // 2. Ensure a primary Map layer exists and matches the selected mapFeatureSource.
      var primaryLayer = datasets.firstOrNull { it.kind == DatasetKind.MAP_LAYER }
      if (primaryLayer == null) {
        val layerTitle =
          when (s.mapFeatureSource) {
            WizardMapFeatureSource.SAMPLE_PLOTS -> "Sample plots"
            else -> current.details.title.ifBlank { "Survey sites" }
          }
        val layerKey = newKey("d")
        primaryLayer =
          if (s.mapFeatureSource == WizardMapFeatureSource.SAMPLE_PLOTS) {
            EntityDataset(
              key = layerKey,
              kind = DatasetKind.MAP_LAYER,
              id = FormDatasetLinks.uniqueId(slugify(layerTitle), datasets.map { it.id }),
              displayName = layerTitle,
              description = "Sample plots generated from a statistical sample design.",
              geometryKind = SampleDesignConfig().geometryKind,
              keyProperty = SamplePlotProperties.PLOT_ID,
              labelProperty = SamplePlotProperties.PLOT_ID,
              fieldCreationEnabled = effectiveMode.defaultFieldCreationEnabled,
              properties = SamplePlotProperties.schema,
              style = LayerStyle(colorHex = "#F9A825", fillOpacity = 0.15),
              generator = SampleDesignConfig(),
            )
          } else {
            EntityDataset(
              key = layerKey,
              kind = DatasetKind.MAP_LAYER,
              id = FormDatasetLinks.uniqueId(slugify(layerTitle), datasets.map { it.id }),
              displayName = layerTitle,
              geometryKind = defaultGeomKind,
              keyProperty = "id",
              labelProperty = "id",
              fieldCreationEnabled = effectiveMode.defaultFieldCreationEnabled,
              properties = listOf(EntityProperty("id", "ID", PropertyType.TEXT, required = true)),
            )
          }
        datasets = listOf(primaryLayer) + datasets.filter { it.kind == DatasetKind.MAP_LAYER }
      } else if (
        s.mapFeatureSource == WizardMapFeatureSource.SAMPLE_PLOTS && !primaryLayer.isGenerated
      ) {
        val design = SampleDesignConfig()
        primaryLayer =
          primaryLayer.copy(
            displayName =
              if (primaryLayer.displayName == "Survey sites" || primaryLayer.displayName.isBlank())
                "Sample plots"
              else primaryLayer.displayName,
            description = "Sample plots generated from a statistical sample design.",
            geometryKind = design.geometryKind,
            keyProperty = SamplePlotProperties.PLOT_ID,
            labelProperty = SamplePlotProperties.PLOT_ID,
            fieldCreationEnabled = false,
            linkedFormKey = null,
            properties = SamplePlotProperties.schema,
            rows = emptyList(),
            generator = design,
          )
        datasets = datasets.map { if (it.key == primaryLayer.key) primaryLayer else it }
      } else if (
        s.mapFeatureSource != WizardMapFeatureSource.SAMPLE_PLOTS && primaryLayer.isGenerated
      ) {
        primaryLayer =
          primaryLayer.copy(
            displayName = current.details.title.ifBlank { "Survey sites" },
            geometryKind = defaultGeomKind,
            keyProperty = "id",
            labelProperty = "id",
            fieldCreationEnabled = true,
            properties = listOf(EntityProperty("id", "ID", PropertyType.TEXT, required = true)),
            rows = emptyList(),
            generator = null,
          )
        datasets = datasets.map { if (it.key == primaryLayer.key) primaryLayer else it }
      } else if (!primaryLayer.isGenerated && !s.userOverrodeGeometryKind) {
        primaryLayer = primaryLayer.copy(geometryKind = defaultGeomKind)
        datasets = datasets.map { if (it.key == primaryLayer.key) primaryLayer else it }
      }

      // 3. Ensure a primary Form exists.
      if (forms.isEmpty()) {
        val formKey = newKey("f")
        val formId = FormIds.newFormId()
        val formTitle =
          when (effectiveMode) {
            SurveyCollectionMode.VISUAL_CLASSIFICATION -> "Plot interpretation"
            else -> current.details.title.ifBlank { "Site assessment" }
          }
        val baseForm =
          if (effectiveMode == SurveyCollectionMode.VISUAL_CLASSIFICATION) {
            EditorForm(
              formId = formId,
              title = formTitle,
              availability = effectiveMode.formAvailability,
              questions =
                listOf(
                  EditorQuestion(
                    key = "q1",
                    name = "land_cover",
                    type = EditorQuestionType.SELECT_ONE,
                    label = "What is the primary land cover?",
                    required = true,
                    choices =
                      listOf(
                        org.groundplatform.v2.devtools.prototypeapp.domain.model.editor
                          .EditorChoice(
                            "forest",
                            "Forest",
                            colorHex = "#3C8D40",
                          ),
                        org.groundplatform.v2.devtools.prototypeapp.domain.model.editor
                          .EditorChoice(
                            "non_forest",
                            "Non-forest",
                            colorHex = "#F37C22",
                          ),
                      ),
                  )
                ),
            )
          } else {
            EditorFormTemplates.blank(formId, formTitle)
              .copy(availability = effectiveMode.formAvailability)
          }
        forms = listOf(SurveyEditorForm(formKey, baseForm))
      }

      // 4. Synchronize form availability, geometry capture, and save_to wiring with activeLayer.
      val activeLayer = datasets.first { it.kind == DatasetKind.MAP_LAYER }
      val usesUpdateMode =
        s.mapFeatureSource == WizardMapFeatureSource.SAMPLE_PLOTS ||
          s.mapFeatureSource == WizardMapFeatureSource.IMPORT_FILE ||
          effectiveMode.defaultSaveToMode == SaveToMode.UPDATE

      forms = forms.mapIndexed { index, entry ->
        var updatedForm = entry.form.copy(availability = effectiveMode.formAvailability)
        if (
          index == 0 &&
            !usesUpdateMode &&
            s.startMethod == WizardStartMethod.FROM_SCRATCH &&
            !updatedForm.hasGeometry
        ) {
          val geomType =
            when (activeLayer.geometryKind) {
              GeometryKind.POINT -> EditorQuestionType.LOCATION
              GeometryKind.LINE -> EditorQuestionType.LINE
              GeometryKind.POLYGON -> EditorQuestionType.POLYGON
            }
          val geomLabel =
            when (activeLayer.geometryKind) {
              GeometryKind.POLYGON -> "Record plot boundary"
              GeometryKind.LINE -> "Record transect line"
              GeometryKind.POINT -> "Record site location"
            }
          val geomQuestion =
            EditorQuestion(
              key = newKey("q"),
              name = "location",
              type = geomType,
              label = geomLabel,
              required = true,
              capture = effectiveMode.defaultGeometryCapture,
            )
          updatedForm = updatedForm.copy(questions = listOf(geomQuestion) + updatedForm.questions)
        }
        if (updatedForm.availability.includesWeb) {
          updatedForm =
            updatedForm.copy(
              questions =
                updatedForm.questions.map { q ->
                  if (q.type.isGeometry) {
                    q.copy(capture = effectiveMode.defaultGeometryCapture)
                  } else {
                    q
                  }
                }
            )
        }
        if (index == 0) {
          if (usesUpdateMode) {
            val targetEditorDataset = activeLayer.toEditorDataset(entry.key, forms)
            val mappings =
              SaveToRules.autoMap(updatedForm, targetEditorDataset).filter { it.property != null }
            updatedForm =
              updatedForm.copy(
                saveTo =
                  EditorSaveTo(
                    mode = SaveToMode.UPDATE,
                    targetDatasetId = activeLayer.id,
                    mappings = mappings,
                  )
              )
          } else {
            updatedForm =
              updatedForm.copy(saveTo = updatedForm.saveTo.copy(mode = SaveToMode.CREATE))
          }
        }
        entry.copy(form = updatedForm)
      }

      val firstForm = forms.first()
      datasets = datasets.map { d ->
        if (d.key != activeLayer.key) d
        else if (firstForm.form.saveTo.mode == SaveToMode.CREATE && !d.isGenerated) {
          FormDatasetLinks.syncedWithForm(
              d.copy(
                linkedFormKey = firstForm.key,
                fieldCreationEnabled = effectiveMode.defaultFieldCreationEnabled,
              ),
              firstForm.form,
            )
            .copy(
              kind = DatasetKind.MAP_LAYER,
              geometryKind =
                if (firstForm.form.hasGeometry) FormDatasetLinks.geometryKindFor(firstForm.form)
                else activeLayer.geometryKind,
            )
        } else {
          val withMapped = SurveyEditorDerivation.withMappedProperties(d, firstForm.form)
          withMapped.copy(
            kind = DatasetKind.MAP_LAYER,
            linkedFormKey = null,
            fieldCreationEnabled = effectiveMode.defaultFieldCreationEnabled,
          )
        }
      }
      datasets = SaveToRules.inheritConcepts(firstForm.form, firstForm.key, datasets)

      current.copy(forms = forms, datasets = datasets, nextKeyId = nextId)
    })

    if (purposeSelectionChanged) {
      session.update { it.copy(seededPurposeIds = currentPurposeIds.toSet()) }
    }
  }

  private fun maybeApplyCareDefaultsOnSharingStep() {
    if (session.value.careDefaultsAppliedForOrgOrConcepts) return
    val draft = surveyEditor.uiState.value.draft
    val org = uiState.value.selectedOrganization
    val conceptIds = buildSet {
      draft.forms.forEach { f ->
        f.form.questions.forEach { q -> q.conceptLink?.conceptId?.let(::add) }
      }
      draft.datasets.forEach { d ->
        d.properties.forEach { p -> p.conceptLink?.conceptId?.let(::add) }
      }
    }
    if (SurveyWizardRules.usesIplcCareDefaults(org?.organizationType, conceptIds)) {
      surveyEditor.updateSharing {
        it.copy(
          policy = SharingPolicy.RESTRICTED,
          peerDataVisibility = PeerDataVisibility.OWN_ONLY,
        )
      }
      session.update { it.copy(careDefaultsAppliedForOrgOrConcepts = true) }
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
    val fallbackTitle = fileName.substringBeforeLast('.').ifBlank { "Imported form" }
    val imported =
      FormImport.fromXml(
        xml = xmlText,
        fallbackTitle = fallbackTitle,
        availability = session.value.collectionMode.formAvailability,
      )
    if (imported == null || imported.form.questions.isEmpty()) {
      session.update {
        it.copy(
          importedXFormsFileName = fileName,
          importedXForms = null,
          importErrorMessage = "Could not parse any XForms questions from \"$fileName\".",
        )
      }
    } else {
      session.update {
        it.copy(
          importedXFormsFileName = fileName,
          importedXForms = imported,
          importErrorMessage = null,
        )
      }
      if (surveyEditor.uiState.value.draft.details.title.isBlank()) {
        surveyEditor.updateDetails { it.copy(title = imported.form.title) }
      }
    }
  }

  override fun clearImportedXForms() {
    session.update {
      it.copy(
        importedXFormsFileName = null,
        importedXForms = null,
        importErrorMessage = null,
      )
    }
  }

  override fun selectCollectionMode(mode: SurveyCollectionMode) {
    session.update {
      it.copy(
        collectionMode = mode,
        mapFeatureSource = mode.defaultFeatureSource,
        userOverrodeMapFeatureSource = true,
      )
    }
  }

  override fun togglePurposePack(packId: String) {
    val currentIds = surveyEditor.uiState.value.draft.details.purposeIds
    val alreadySelected = currentIds == listOf(packId)
    val newPurposeIds = if (alreadySelected) emptyList() else listOf(packId)
    val selectedPack =
      if (!alreadySelected && packId != SurveyWizardRules.OTHER_PURPOSE_ID) {
        uiState.value.library.purposePack(packId)
      } else {
        null
      }
    val newProgramIds = selectedPack?.programIds.orEmpty()
    surveyEditor.updateDetails {
      it.copy(
        purposeIds = newPurposeIds,
        programIds = newProgramIds,
      )
    }
    session.update { current ->
      val nextSource =
        if (current.userOverrodeMapFeatureSource) {
          current.mapFeatureSource
        } else {
          SurveyWizardRules.defaultMapFeatureSource(newPurposeIds)
        }
      val nextMode =
        if (current.userOverrodeMapFeatureSource) {
          current.collectionMode
        } else {
          SurveyWizardRules.defaultCollectionMode(newPurposeIds, nextSource)
        }
      current.copy(
        mapFeatureSource = nextSource,
        collectionMode = nextMode,
        userOverrodeGeometryKind = false,
      )
    }
    if (
      !alreadySelected &&
        packId == "eudr_due_diligence" &&
        !session.value.eudrPlotsUnderFourHaConfirmed
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
        userOverrodeMapFeatureSource = true,
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
                  eudrPlotsUnderFourHaConfirmed = session.value.eudrPlotsUnderFourHaConfirmed,
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
    session.update { it.copy(userOverrodeGeometryKind = true) }
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
    session.update { it.copy(eudrPlotsUnderFourHaConfirmed = confirmed) }
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
        "I consent to share the field observations and geospatial data collected in this survey with the organizing institution under its data governance policy."
      } else {
        session.value.collectorConsentText
      }
    session.update {
      it.copy(collectorConsentRequired = required, collectorConsentText = defaultText)
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
    surveyEditor.updateSharing {
      it.copy(
        policy = SharingPolicy.RESTRICTED,
        peerDataVisibility = PeerDataVisibility.OWN_ONLY,
      )
    }
    session.update { it.copy(careDefaultsAppliedForOrgOrConcepts = true) }
  }

  override fun openInSurveyEditor() {
    commitAndOpenEditor()
  }

  override fun finishWizard() {
    commitAndOpenEditor()
  }

  private fun commitAndOpenEditor() {
    scope.launch {
      applyOverviewStartMethodIfNeeded()
      ensurePrimaryLayerAndForm()
      val draft = surveyEditor.uiState.value.draft
      val finalTitle = draft.details.title.trim().ifBlank { CreateSurveyUseCase.DEFAULT_TITLE }
      val finalDesc = draft.details.description.trim()
      val finalOrgId = draft.details.organizationId
      val finalOrg = finalOrgId?.let { id ->
        surveyEditor.uiState.value.organizations.firstOrNull { it.id == id }
      }
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
      _events.tryEmit(CreateSurveyWizardEvent.OpenSurveyEditor(surveyId))
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
