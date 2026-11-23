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
package org.openforis.collectone.v2.web.ui.surveyeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.openforis.collectone.v2.app.domain.model.DomainMessage
import org.openforis.collectone.v2.app.domain.model.EffortComparison
import org.openforis.collectone.v2.app.domain.model.ImpactEvent
import org.openforis.collectone.v2.app.domain.model.QuotaAndCostEnforcer
import org.openforis.collectone.v2.app.domain.model.QuotaCheckResult
import org.openforis.collectone.v2.app.domain.model.QuotaLimitsConfig
import org.openforis.collectone.v2.app.domain.model.SurveyLifecycleState
import org.openforis.collectone.v2.app.domain.model.SurveyOutcomeKind
import org.openforis.collectone.v2.app.domain.model.SurveyPlaceItem
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.editor.CollaboratorRole
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EditorIssueMessage
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.EntityProperty
import org.openforis.collectone.v2.app.domain.model.editor.FormPreviewTarget
import org.openforis.collectone.v2.app.domain.model.editor.ImportedForm
import org.openforis.collectone.v2.app.domain.model.editor.PlotReviewOptions
import org.openforis.collectone.v2.app.domain.model.editor.SampleDesignConfig
import org.openforis.collectone.v2.app.domain.model.editor.SaveToMode
import org.openforis.collectone.v2.app.domain.model.editor.SharingSettings
import org.openforis.collectone.v2.app.domain.model.editor.SurveyArea
import org.openforis.collectone.v2.app.domain.model.editor.SurveyDetails
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.app.domain.model.editor.withModificationFrom
import org.openforis.collectone.v2.app.domain.repository.AuthRepository
import org.openforis.collectone.v2.app.domain.repository.EntityTableRepository
import org.openforis.collectone.v2.app.domain.repository.ImpactEventRepository
import org.openforis.collectone.v2.app.domain.repository.LibraryRepository
import org.openforis.collectone.v2.app.domain.repository.OrganizationRepository
import org.openforis.collectone.v2.app.domain.repository.PlaceRepository
import org.openforis.collectone.v2.app.domain.repository.SurveyEditorRepository
import org.openforis.collectone.v2.app.domain.repository.SurveyRepository
import org.openforis.collectone.v2.app.domain.usecase.ResolveLibraryUseCase
import org.openforis.collectone.v2.app.ui.text.toUiText
import org.openforis.collectone.v2.app.ui.viewmodel.ViewModelDispatcher
import org.openforis.collectone.v2.app.ui.viewmodel.viewModelScopeOn
import org.openforis.collectone.v2.core.sampling.SampleEstimate
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.web.domain.model.editor.DataTableImportPlan
import org.openforis.collectone.v2.web.domain.model.editor.DataTableImportPreview
import org.openforis.collectone.v2.web.domain.model.editor.DateOrder
import org.openforis.collectone.v2.web.domain.model.editor.MapLayerImportPlan
import org.openforis.collectone.v2.web.domain.model.editor.MapLayerImportPreview
import org.openforis.collectone.v2.web.domain.model.editor.SampleGenerationProgress
import org.openforis.collectone.v2.web.domain.model.editor.SamplingAreaResult
import org.openforis.collectone.v2.web.domain.model.editor.SuggestedGridSpacing
import org.openforis.collectone.v2.web.domain.usecase.GenerateSamplePlotsUseCase
import org.openforis.collectone.v2.web.domain.usecase.InviteCollaboratorUseCase
import org.openforis.collectone.v2.web.domain.usecase.ManageLibraryUseCase
import org.openforis.collectone.v2.web.domain.usecase.SurveyLifecycleUseCase
import org.openforis.collectone.v2.web.ui.formeditor.FormEditorViewModel
import org.openforis.collectone.v2.web.ui.formeditor.FormLibraryContext
import org.openforis.collectone.v2.web.ui.surveyoutcome.SurveyOutcomeCardHolder
import org.openforis.collectone.v2.web.ui.surveyoutcome.SurveyOutcomeCardState
import org.openforis.collectone.v2.web.ui.text.EditorDefaultNames
import org.openforis.collectone.v2.web.ui.text.EditorText

/**
 * ViewModel of the Survey editor page: survey details, sharing, Forms, Map layers, and Data tables
 * of the active survey, plus the currently selected section.
 *
 * The survey's draft comes from [SurveyEditorRepository.observeDraft]. Until the first edit the
 * editor shows that live draft; the first edit freezes a copy as the opened snapshot and the draft
 * under edit, which are compared for unpublished changes. [publish] saves the draft through
 * [SurveyEditorRepository.saveDraft] (giving it the opened snapshot so map features collected
 * meanwhile are kept) and [discardChanges] drops it, returning to the live draft. Changing the
 * active survey starts a fresh session.
 *
 * Form edits go through per-Form [FormEditorViewModel]s ([formEditor]), which write into this
 * draft; a Form's linked datasets follow its questions. Sample plots are generated with
 * [GenerateSamplePlotsUseCase], sharing invites with [InviteCollaboratorUseCase]. Leaving the
 * editor is published as [SurveyEditorEvent]s for the app shell.
 *
 * This ViewModel owns the editing session, navigation, the lifecycle, and the Form editors. The
 * other intents are implemented by handlers that change the draft through [SurveyDraftSession]:
 * [SurveyDetailsHandler], [SurveySharingHandler], [SurveyPurposeHandler],
 * [SurveyFormCatalogHandler], [SurveyFormDatasetLinkHandler], [SurveyDatasetHandler],
 * [SurveySamplePlotHandler], [SurveyPlotReviewHandler], and [SurveyLibraryHandler]; this ViewModel
 * forwards to them. The open Form editors are kept by [SurveyFormEditors].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SurveyEditorViewModel(
  private val surveyRepository: SurveyRepository,
  private val surveyEditorRepository: SurveyEditorRepository,
  organizationRepository: OrganizationRepository,
  authRepository: AuthRepository,
  private val placeRepository: PlaceRepository,
  private val generateSamplePlots: GenerateSamplePlotsUseCase = GenerateSamplePlotsUseCase(),
  private val inviteCollaboratorUseCase: InviteCollaboratorUseCase = InviteCollaboratorUseCase(),
  dispatcher: CoroutineDispatcher = ViewModelDispatcher,
  /** Organization libraries; the Form editor links questions to their concepts. */
  libraryRepository: LibraryRepository? = null,
  /** Adds concepts to an organization's dictionary from the Form editor (Managers only). */
  manageLibraryUseCase: ManageLibraryUseCase? = libraryRepository?.let(::ManageLibraryUseCase),
  resolveLibraryUseCase: ResolveLibraryUseCase = ResolveLibraryUseCase(),
  /** Closes, archives, and reopens the survey; `null` turns those actions off. */
  private val surveyLifecycle: SurveyLifecycleUseCase? = null,
  /** "Data was used" events, for the survey's Activity list. */
  impactEventRepository: ImpactEventRepository? = null,
  /** Lazy-loads remote entity table tiles on scroll when lightweight web sync is active. */
  private val entityTableRepository: EntityTableRepository? = null,
) : ViewModel(viewModelScopeOn(dispatcher)), SurveyEditorActions {
  private val data: StateFlow<SurveyEditorData> =
    combine(
        surveyRepository.observeActiveSurveyId().flatMapLatest { id ->
          combine(
            surveyEditorRepository.observeDraft(id),
            surveyEditorRepository.observeWorkingCopyDraft(id),
            surveyRepository.observeSurveyContent(id).map { content ->
              content.entities
                .groupBy { it.datasetId }
                .mapValues { (_, entities) -> entities.sumOf { it.submissions.size } }
            },
            surveyRepository.observeSurveys().map { surveys ->
              surveys.firstOrNull { it.id == id }
            },
            impactEventRepository?.observeEvents()?.map { events ->
              events.filter { it.surveyId == id }.sortedByDescending { it.occurredAt }
            } ?: flowOf(emptyList()),
          ) { draft, workingCopy, counts, survey, activity ->
            ActiveSurvey(id, draft, workingCopy, counts, survey, activity)
          }
        },
        organizationRepository.observeOrganizations(),
        authRepository.observeSession(),
        placeRepository.observeLocalPlaces(),
        libraryRepository?.observeLibraries() ?: flowOf(emptyMap()),
      ) { active, organizations, auth, places, libraries ->
        SurveyEditorData(
          surveyId = active.surveyId,
          liveDraft = active.draft,
          workingCopyDraft = active.workingCopyDraft,
          organizations = organizations,
          signedInUserEmail = auth.profile.email,
          localPlaces = places,
          submissionCountByDatasetId = active.submissionCounts,
          libraries = libraries,
          survey = active.survey,
          activity = active.activity,
        )
      }
      .stateIn(viewModelScope, SharingStarted.Eagerly, SurveyEditorData())

  /** What the editor reads about the active survey. */
  private data class ActiveSurvey(
    val surveyId: String,
    val draft: SurveyEditorDraft,
    val workingCopyDraft: SurveyEditorDraft?,
    val submissionCounts: Map<String, Int>,
    val survey: SurveyPreviewItem?,
    val activity: List<ImpactEvent>,
  )

  private val session = MutableStateFlow(SurveyEditorSession())

  /** The "What happened with this data?" card, offered after closing the survey. */
  private val outcomeCard = SurveyOutcomeCardHolder(surveyLifecycle, viewModelScope)

  private val _events =
    MutableSharedFlow<SurveyEditorEvent>(
      extraBufferCapacity = 64,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

  /** Outcomes of editor actions that the app shell applies outside this slice. */
  val events: Flow<SurveyEditorEvent> = _events.asSharedFlow()

  /** The library and dictionary writes the Form editors request; see [SurveyLibraryHandler]. */
  private val libraryHandler =
    SurveyLibraryHandler(
      manageLibraryUseCase,
      resolveLibraryUseCase,
      viewModelScope,
      libraries = { data.value.libraries },
      libraryContext = { libraryContext.value },
    )

  /** The editor's screen state; see [buildSurveyEditorUiState]. */
  val uiState: StateFlow<SurveyEditorUiState> =
    combine(data, session, outcomeCard.card, ::buildUiState)
      .stateIn(viewModelScope, SharingStarted.Eagerly, SurveyEditorUiState())

  /** The dictionary the Form editors link questions to: the survey's resolved library. */
  private val libraryContext: StateFlow<FormLibraryContext> =
    uiState
      .map { it.library }
      .distinctUntilChanged()
      .stateIn(viewModelScope, SharingStarted.Eagerly, uiState.value.library)

  /** The open Form editors, one per Form; see [formEditor]. */
  private val formEditors = SurveyFormEditors(viewModelScope, ::createFormEditor)

  /** Starting names of new items; see [useDefaultNames]. */
  private var defaultNames = EditorDefaultNames.ENGLISH

  /**
   * Names new questions, datasets, forms, and properties with [names] from now on: the view passes
   * them in the language it shows, since they're saved as the user's data.
   */
  fun useDefaultNames(names: EditorDefaultNames) {
    defaultNames = names
  }

  /** The editing session as the action handlers below see it. */
  private val draftSession =
    object : SurveyDraftSession {
      override val uiState: SurveyEditorUiState
        get() = this@SurveyEditorViewModel.uiState.value

      override val draft: SurveyEditorDraft
        get() = this@SurveyEditorViewModel.draft

      override val section: SurveyEditorSection
        get() = session.value.section

      override fun edit(block: SurveyDraftEdits.() -> Unit) = this@SurveyEditorViewModel.edit(block)

      override fun editForForm(formKey: String, block: SurveyDraftEdits.() -> Unit) =
        this@SurveyEditorViewModel.editForForm(formKey, block)
    }

  private val sharingHandler = SurveySharingHandler(draftSession, inviteCollaboratorUseCase)

  private val detailsHandler =
    SurveyDetailsHandler(
      draftSession,
      placeRepository,
      dismissNotice = { session.update { it.copy(organizationNotice = null) } },
    )

  private val purposeHandler = SurveyPurposeHandler(draftSession)

  private val datasetHandler = SurveyDatasetHandler(draftSession)

  private val samplePlotHandler =
    SurveySamplePlotHandler(
      draftSession,
      generateSamplePlots,
      viewModelScope,
      status =
        object : SampleGenerationStatus {
          override val progress: SampleGenerationProgress?
            get() = session.value.generation

          override fun setProgress(progress: SampleGenerationProgress?) = session.update {
            it.copy(generation = progress)
          }

          override fun setError(datasetKey: String, message: EditorText?) = session.update {
            it.copy(
              generationErrors =
                if (message == null) it.generationErrors - datasetKey
                else it.generationErrors + (datasetKey to message)
            )
          }
        },
      organizations = { data.value.organizations },
    )

  private val plotReviewHandler =
    SurveyPlotReviewHandler(draftSession, onFormsRemoved = { it.forEach(formEditors::close) })

  private val formCatalogHandler =
    SurveyFormCatalogHandler(
      draftSession,
      libraryContext,
      // Offer links for unlinked questions that match standard fields.
      onFormImported = { formKey -> formEditor(formKey).suggestImportMatches() },
      onFormDeleted = formEditors::close,
    )

  private val formDatasetLinkHandler = SurveyFormDatasetLinkHandler(draftSession)

  init {
    // A different active survey starts a fresh session (its own section, draft, and Form editors).
    // When the survey has a stored working copy, restore it and prompt the organizer whether to
    // continue editing or discard it.
    viewModelScope.launch {
      data
        .map { Triple(it.surveyId, it.liveDraft, it.workingCopyDraft) }
        .distinctUntilChanged()
        .collect { (surveyId, liveDraft, workingCopyDraft) ->
          val current = session.value
          if (current.surveyId != surveyId) {
            val hasStoredWorkingCopy =
              workingCopyDraft != null &&
                workingCopyDraft.copy(nextKeyId = 0) != liveDraft.copy(nextKeyId = 0)
            session.value =
              if (hasStoredWorkingCopy) {
                SurveyEditorSession(
                  surveyId = surveyId,
                  opened = liveDraft,
                  draft = workingCopyDraft,
                  isPromptingWorkingCopy = true,
                )
              } else {
                SurveyEditorSession(surveyId = surveyId)
              }
            outcomeCard.dismiss()
            formEditors.clear()
          }
        }
    }
    // Autosave in-progress edits to the survey's working copy; the repository queues them per
    // field and sends them once the organizer pauses.
    viewModelScope.launch {
      session
        .map { Triple(it.surveyId, it.draft, it.isSaving) }
        .distinctUntilChanged()
        .collect { (surveyId, draft, isSaving) ->
          if (surveyId == null || draft == null) return@collect
          // Skipped while saving (the save clears the working copy, and a late autosave of the
          // draft being saved would bring it back as "unsaved changes") and while prompting to
          // continue a restored working copy (nothing has been edited in this session yet).
          if (!isSaving && !session.value.isPromptingWorkingCopy && !uiState.value.isReadOnly) {
            surveyEditorRepository.autosaveDraft(surveyId, draft)
          }
        }
    }
  }

  private fun buildUiState(
    data: SurveyEditorData,
    session: SurveyEditorSession,
    outcomeCard: SurveyOutcomeCardState?,
  ): SurveyEditorUiState =
    buildSurveyEditorUiState(
      data,
      session,
      outcomeCard,
      hasLiveGeocoder = placeRepository.hasLiveGeocoder,
      libraryContextFor = libraryHandler::contextFor,
    )

  /**
   * Drops the editing session of the active survey without discarding its stored working copy:
   * unsaved edits, the section and panel width, the outcome card, the Form editors, and the cached
   * sample design fingerprints. Called when the app's session is reset.
   */
  fun reset() {
    session.value = SurveyEditorSession(surveyId = data.value.surveyId)
    outcomeCard.dismiss()
    formEditors.clear()
    samplePlotHandler.resetInputHashes()
  }

  /**
   * Opens [surveyId] with [initialDraft] immediately in the editor session so callers (such as the
   * Survey Creation Wizard) do not race with asynchronous [data] emissions. [opened] is the draft
   * the edits started from: the saved draft when [initialDraft] is a restored working copy.
   */
  fun openSurveyDraft(
    surveyId: String,
    initialDraft: SurveyEditorDraft,
    opened: SurveyEditorDraft = initialDraft,
  ) {
    val withSuggestedSpacing = SuggestedGridSpacing.applyTo(initialDraft, initialDraft)
    session.value =
      SurveyEditorSession(
        surveyId = surveyId,
        opened = if (opened === initialDraft) withSuggestedSpacing else opened,
        draft = withSuggestedSpacing,
      )
    outcomeCard.dismiss()
    formEditors.clear()
    samplePlotHandler.resetInputHashes()
  }

  /**
   * The survey being edited: the session's, or the store's active survey before the session has
   * one.
   */
  private val activeSessionSurveyId: String
    get() = session.value.surveyId?.takeIf { it.isNotBlank() } ?: data.value.surveyId

  /** The draft under edit, or the live draft until the first edit. */
  private val draft: SurveyEditorDraft
    get() =
      session.value
        .takeIf {
          it.draft != null && (it.surveyId == data.value.surveyId || it.surveyId != null)
        }
        ?.draft ?: data.value.liveDraft

  /** The draft the edits started from, or the live draft until the first edit. */
  private val opened: SurveyEditorDraft
    get() =
      session.value
        .takeIf {
          it.opened != null && (it.surveyId == data.value.surveyId || it.surveyId != null)
        }
        ?.opened ?: data.value.liveDraft

  // --- Draft edits ---

  /**
   * Applies [block] to a working copy of the draft and stores the result as the edited draft,
   * freezing the opened snapshot on the first edit. A block that changes nothing leaves the session
   * as it is. Ignored while the survey is closed or archived.
   */
  private fun edit(block: SurveyDraftEdits.() -> Unit) {
    if (uiState.value.isReadOnly) return
    val current = session.value
    val before = draft
    val openedBefore = opened
    val edits =
      SurveyDraftEdits(before, current.section, current.organizationNotice, defaultNames)
        .apply(block)
    val edited = edits.toDraft()
    val after = if (edited == before) edited else SuggestedGridSpacing.applyTo(before, edited)
    if (
      after == before &&
        edits.section == current.section &&
        edits.organizationNotice == current.organizationNotice
    ) {
      return
    }
    session.update {
      it.copy(
        surveyId = activeSessionSurveyId,
        opened = openedBefore,
        draft = after,
        isPromptingWorkingCopy = false,
        section = edits.section,
        organizationNotice = edits.organizationNotice,
      )
    }
  }

  /**
   * Runs [edit] and records the resulting survey draft mutation on [formKey]'s
   * [FormEditorViewModel] so that Form-scoped actions handled at the Survey editor level can be
   * undone and redone.
   */
  private fun editForForm(formKey: String, block: SurveyDraftEdits.() -> Unit) {
    val before = draft
    edit(block)
    val after = draft
    if (after != before && after.forms.any { it.key == formKey }) {
      formEditor(formKey).recordExternalSurveyMutation(before, after)
    }
  }

  /**
   * Stores [after], a whole survey draft restored by a Form editor's undo or redo, as the edited
   * draft. Unlike [edit] it applies no derivations (such as suggested grid spacing): the draft was
   * already derived when it was first recorded.
   */
  private fun applyFormUndoRedoDraft(after: SurveyEditorDraft) {
    if (uiState.value.isReadOnly) return
    val openedBefore = opened
    session.update {
      it.copy(
        surveyId = activeSessionSurveyId,
        opened = openedBefore,
        draft = after,
        isPromptingWorkingCopy = false,
      )
    }
  }

  /** Replaces the working draft in the current session, freezing [opened] on the first edit. */
  fun replaceWorkingDraft(
    transform: (SurveyEditorDraft) -> SurveyEditorDraft,
    section: SurveyEditorSection? = null,
  ) {
    if (uiState.value.isReadOnly) return
    val before = draft
    val openedBefore = opened
    val after = SuggestedGridSpacing.applyTo(before, transform(before))
    session.update {
      it.copy(
        surveyId = activeSessionSurveyId,
        opened = openedBefore,
        draft = after,
        isPromptingWorkingCopy = false,
        section = section ?: it.section,
      )
    }
  }

  /** Saves the current working draft to the repository without emitting a leave-editor event. */
  suspend fun saveWorkingDraftNow(): SurveyEditorDraft? {
    val surveyId = activeSessionSurveyId
    if (surveyId.isBlank()) return null
    session.update { it.copy(isSaving = true) }
    return saveSession(surveyId, previous = opened, snapshot = draft)
  }

  /**
   * Saves [surveyId]'s edits and ends its editing session. The save keeps going if the organizer
   * leaves the editor or opens another survey meanwhile: it then saves [snapshot] (the draft when
   * saving started) and leaves the other survey's session alone. Autosave and discarding are paused
   * while [SurveyEditorSession.isSaving], so neither can resurrect or drop the edits being saved.
   */
  private suspend fun saveSession(
    surveyId: String,
    previous: SurveyEditorDraft,
    snapshot: SurveyEditorDraft,
  ): SurveyEditorDraft {
    fun isSameSession() = session.value.surveyId == surveyId && data.value.surveyId == surveyId
    try {
      if (isSameSession()) samplePlotHandler.generatePendingSamplePlots()
      val toSave = if (isSameSession() && session.value.draft != null) draft else snapshot
      surveyEditorRepository.saveDraft(surveyId, toSave, previous = previous)
      return toSave
    } finally {
      session.update {
        if (it.surveyId != surveyId) {
          it.copy(isSaving = false)
        } else {
          it.copy(
            draft = null,
            opened = null,
            isPromptingWorkingCopy = false,
            isSaving = false,
            generationErrors = emptyMap(),
            organizationNotice = null,
          )
        }
      }
      if (isSameSession()) formEditors.clearUndoRedo()
    }
  }

  // --- Navigation, layout & lifecycle ---

  override fun select(section: SurveyEditorSection) {
    session.update { it.copy(section = section) }
  }

  override fun updateSidePanelWidth(widthDp: Float) {
    if (!widthDp.isFinite()) return
    session.update {
      it.copy(
        sidePanelWidthDp =
          widthDp.coerceIn(
            SurveyEditorUiState.MIN_SIDE_PANEL_WIDTH_DP,
            SurveyEditorUiState.MAX_SIDE_PANEL_WIDTH_DP,
          )
      )
    }
  }

  override fun publish() {
    val current = uiState.value
    if (session.value.isSaving || current.isReadOnly) return
    if (current.issueCount > 0) {
      navigateToFirstIssue(current)
      return
    }
    if (current.canPublish) {
      publishValidatedDraft(draft)
    }
  }

  private fun publishValidatedDraft(currentDraft: SurveyEditorDraft) {
    val org =
      currentDraft.details.organizationId?.let { id ->
        data.value.organizations.firstOrNull { it.id == id }
      }
    val globalLimits = data.value.organizations.firstOrNull { it.isSynthetic }?.quotaLimits
    val check =
      QuotaAndCostEnforcer.checkSurveyDesign(
        draft = currentDraft,
        organization = org,
        globalLimits = globalLimits,
      )
    if (check is QuotaCheckResult.Exceeded) {
      session.update {
        it.copy(
          organizationNotice = EditorText.Localized(check.message.toUiText()),
          isSaving = false,
        )
      }
      return
    }

    val surveyId = activeSessionSurveyId
    val previous = opened
    session.update { it.copy(isSaving = true) }
    viewModelScope.launch {
      saveSession(surveyId, previous = previous, snapshot = currentDraft)
      _events.tryEmit(SurveyEditorEvent.Published)
    }
  }

  override fun discardChanges() {
    if (session.value.isSaving) return
    val discardedSurveyId = clearSessionForDiscard()
    if (discardedSurveyId != null) {
      viewModelScope.launch { surveyEditorRepository.discardWorkingCopy(discardedSurveyId) }
    }
  }

  /**
   * Resets uncommitted session state synchronously and returns the survey ID whose working copy
   * should be discarded, or `null` if no stored working copy needs discarding.
   */
  private fun clearSessionForDiscard(): String? {
    val discardedSurveyId =
      session.value.surveyId.takeIf {
        session.value.draft != null || data.value.workingCopyDraft != null
      } ?: data.value.surveyId.takeIf { it.isNotBlank() && data.value.workingCopyDraft != null }
    val live = data.value.liveDraft
    formEditors.clearUndoRedo()
    session.update { s ->
      val current = s.section
      val stillExists =
        when (current) {
          is SurveyEditorSection.Form -> live.forms.any { it.key == current.key }
          is SurveyEditorSection.Dataset -> live.datasets.any { it.key == current.key }
          else -> true
        }
      s.copy(
        draft = null,
        opened = null,
        isPromptingWorkingCopy = false,
        generationErrors = emptyMap(),
        organizationNotice = null,
        section = if (stillExists) current else SurveyEditorSection.Details,
      )
    }
    return discardedSurveyId
  }

  override fun close() {
    discardChanges()
    _events.tryEmit(SurveyEditorEvent.Closed)
  }

  override fun deleteSurvey() {
    val current = uiState.value
    val surveyId = current.surveyId
    if (session.value.isSaving || !current.canManageSurvey || surveyId.isBlank()) return
    // Uncommitted edits of a survey being deleted can't be kept.
    val discardedSurveyId = clearSessionForDiscard()
    outcomeCard.dismiss()
    viewModelScope.launch {
      if (discardedSurveyId != null) surveyEditorRepository.discardWorkingCopy(discardedSurveyId)
      // Removes it locally and queues the move to Trash for the server.
      surveyRepository.deleteSurvey(surveyId)
      if (surveyRepository.getActiveSurveyId() == surveyId) surveyRepository.setActiveSurveyId("")
      _events.tryEmit(SurveyEditorEvent.Deleted)
    }
  }

  override fun onEditorOpened() {
    if (uiState.value.isReadOnly || session.value.isSaving) return
    if (session.value.draft != null) {
      if (uiState.value.hasUnpublishedChanges) {
        session.update { it.copy(isPromptingWorkingCopy = true) }
      }
      return
    }
    val surveyId = data.value.surveyId
    // Read the store directly: the observed data can lag behind a save that just happened.
    viewModelScope.launch {
      val workingCopy = surveyEditorRepository.getWorkingCopyDraft(surveyId) ?: return@launch
      val saved = surveyEditorRepository.getDraft(surveyId)
      val current = session.value
      if (
        current.isSaving ||
          current.draft != null ||
          data.value.surveyId != surveyId ||
          workingCopy.copy(nextKeyId = 0) == saved.copy(nextKeyId = 0)
      ) {
        return@launch
      }
      session.update {
        it.copy(
          surveyId = surveyId,
          opened = saved,
          draft = workingCopy,
          isPromptingWorkingCopy = true,
        )
      }
    }
  }

  override fun continueWorkingCopy() {
    session.update { it.copy(isPromptingWorkingCopy = false) }
  }

  override fun setLifecycleState(state: SurveyLifecycleState) {
    val current = uiState.value
    val lifecycle = surveyLifecycle ?: return
    if (!canChangeLifecycleState(current, state)) return
    if (state == SurveyLifecycleState.PUBLISHED && current.issueCount > 0) {
      navigateToFirstIssue(current)
      return
    }
    val surveyId = current.surveyId
    // A closed or archived survey is read-only, so uncommitted edits can't be kept.
    val discardedSurveyId =
      if (state == SurveyLifecycleState.CLOSED || state == SurveyLifecycleState.ARCHIVED) {
        clearSessionForDiscard()
      } else {
        outcomeCard.dismiss()
        null
      }
    val shouldSave = shouldSaveBeforePublishing(state, current)
    viewModelScope.launch {
      applyLifecycleTransition(lifecycle, surveyId, state, discardedSurveyId, shouldSave)
    }
  }

  private fun canChangeLifecycleState(
    current: SurveyEditorUiState,
    state: SurveyLifecycleState,
  ): Boolean = !session.value.isSaving && current.canManageSurvey && current.lifecycleState != state

  private fun shouldSaveBeforePublishing(
    state: SurveyLifecycleState,
    current: SurveyEditorUiState,
  ): Boolean =
    state == SurveyLifecycleState.PUBLISHED &&
      (current.canPublish || draft.datasets.any(samplePlotHandler::needsSampleGeneration))

  private suspend fun applyLifecycleTransition(
    lifecycle: SurveyLifecycleUseCase,
    surveyId: String,
    state: SurveyLifecycleState,
    discardedSurveyId: String?,
    shouldSave: Boolean,
  ) {
    if (discardedSurveyId != null) {
      surveyEditorRepository.discardWorkingCopy(discardedSurveyId)
    }
    if (shouldSave) {
      saveWorkingDraftNow()
    }
    val stoppedCollecting = lifecycle.setState(surveyId, state)
    if (stoppedCollecting) {
      surveyRepository.getSurveys().firstOrNull { it.id == surveyId }?.let(outcomeCard::open)
    }
  }

  /**
   * Navigates the editor to the first Form or Dataset with a blocking validation issue and selects
   * the offending question when applicable, so the user immediately sees why saving/publishing was
   * blocked.
   */
  private fun navigateToFirstIssue(current: SurveyEditorUiState) {
    val firstFormWithIssues = current.forms.firstOrNull { current.formIssues(it).isNotEmpty() }
    if (firstFormWithIssues != null) {
      val formKey = firstFormWithIssues.key
      select(SurveyEditorSection.Form(formKey))
      val firstIssue = current.formIssues(firstFormWithIssues).first()
      val formEditor = formEditors[formKey]
      val messageKey = (firstIssue.message as? DomainMessage.Text)?.key
      if (messageKey == EditorIssueMessage.GOOGLE_EARTH_INCOMPATIBLE_QUESTION) {
        formEditor.selectPreviewTarget(FormPreviewTarget.WEB)
      }
      when {
        firstIssue.questionKey != null -> formEditor.select(firstIssue.questionKey)
        firstIssue.sectionId != null -> formEditor.selectSection(firstIssue.sectionId)
        else -> formEditor.selectFormSettings()
      }
      return
    }
    val firstDatasetWithIssues = current.datasets.firstOrNull { current.datasetIssueCount(it) > 0 }
    if (firstDatasetWithIssues != null) {
      select(SurveyEditorSection.Dataset(firstDatasetWithIssues.key))
    } else if (current.sharingIssues.isNotEmpty()) {
      select(SurveyEditorSection.Sharing)
    } else if (current.detailsIssues.isNotEmpty()) {
      select(SurveyEditorSection.Details)
    }
  }

  override fun toggleOutcome(kind: SurveyOutcomeKind) = outcomeCard.toggleOutcome(kind)

  override fun setEffortComparison(comparison: EffortComparison?) =
    outcomeCard.setEffortComparison(comparison)

  override fun saveOutcome() = outcomeCard.saveOutcome()

  override fun skipOutcome() = outcomeCard.skipOutcome()

  override fun formEditor(formKey: String): FormEditorViewModel = formEditors[formKey]

  /**
   * A new editor of Form [formKey] running in [formScope], bound to this session: it reads the Form
   * and its dataset catalog from [uiState] and writes through [edit], so its edits share the
   * survey's draft, read-only state, and autosave.
   */
  private fun createFormEditor(formKey: String, formScope: CoroutineScope): FormEditorViewModel {
    val initial = uiState.value
    val initialForm =
      initial.form(formKey)?.form ?: EditorForm(formId = "", title = "", questions = emptyList())
    return FormEditorViewModel(
      formFlow =
        uiState
          .map { it.form(formKey)?.form ?: initialForm }
          .stateIn(formScope, SharingStarted.Eagerly, initialForm),
      datasetsFlow =
        uiState
          .map { it.datasetCatalog(formKey) }
          .stateIn(formScope, SharingStarted.Eagerly, initial.datasetCatalog(formKey)),
      // User edits to a Form copied from a template mark it modified.
      updateForm = { transform ->
        edit { updateForm(formKey) { form -> transform(form).withModificationFrom(form) } }
      },
      coroutineScope = formScope,
      libraryFlow = libraryContext,
      addOrganizationConcept = libraryHandler::addOrganizationConcept,
      saveTemplate = libraryHandler::saveFormAsTemplate,
      formKey = formKey,
      currentSurveyDraft = { draft },
      applySurveyDraft = ::applyFormUndoRedoDraft,
      isSurveyReadOnly = { uiState.value.isReadOnly },
      isReadOnlyFlow =
        uiState
          .map { it.isReadOnly }
          .stateIn(formScope, SharingStarted.Eagerly, initial.isReadOnly),
      createDatasetForChoiceQuestionCallback = { qIdx, kind ->
        createDatasetForChoiceQuestion(formKey, qIdx, kind)
      },
      defaultNames = { defaultNames },
    )
  }

  /**
   * Replaces Form [formKey] with [transform] of it. Datasets linked to the Form follow its
   * questions.
   */
  fun updateForm(formKey: String, transform: (EditorForm) -> EditorForm) =
    editForForm(formKey) { updateForm(formKey, transform) }

  /** Synchronizes the schema of all datasets linked to Form [formKey] with its questions. */
  fun syncDatasetsLinkedToForm(formKey: String) =
    editForForm(formKey) { syncDatasetsLinkedToForm(formKey) }

  // --- Survey details ---

  override fun updateDetails(transform: (SurveyDetails) -> SurveyDetails) =
    detailsHandler.updateDetails(transform)

  override fun updateQuotaLimits(transform: (QuotaLimitsConfig) -> QuotaLimitsConfig) =
    detailsHandler.updateQuotaLimits(transform)

  override fun addSupportedLanguage(code: String) = detailsHandler.addSupportedLanguage(code)

  override fun removeSupportedLanguage(code: String) = detailsHandler.removeSupportedLanguage(code)

  override fun setDefaultLanguage(code: String) = detailsHandler.setDefaultLanguage(code)

  override fun setSurveyArea(area: SurveyArea?) = detailsHandler.setSurveyArea(area)

  override fun setOrganization(organizationId: String?) =
    detailsHandler.setOrganization(organizationId)

  override fun dismissOrganizationNotice() = detailsHandler.dismissOrganizationNotice()

  override fun searchPlaces(
    surveyId: String,
    query: String,
    regionSubtitle: String,
    center: LatLng,
    onResults: (List<SurveyPlaceItem>) -> Unit,
  ) = detailsHandler.searchPlaces(surveyId, query, regionSubtitle, center, onResults)

  // --- Sharing ---

  override fun updateSharing(transform: (SharingSettings) -> SharingSettings) =
    sharingHandler.updateSharing(transform)

  override fun inviteCollaborator(email: String, role: CollaboratorRole): StringResource? =
    sharingHandler.inviteCollaborator(email, role)

  override fun resetInviteLink(email: String) = sharingHandler.resetInviteLink(email)

  override fun acceptInvite(
    email: String,
    displayName: String,
    photoUrl: String?,
    cachedOn: String,
  ): StringResource? = sharingHandler.acceptInvite(email, displayName, photoUrl, cachedOn)

  override fun setCollaboratorRole(email: String, role: CollaboratorRole) =
    sharingHandler.setCollaboratorRole(email, role)

  override fun removeCollaborator(email: String) = sharingHandler.removeCollaborator(email)

  // --- Purposes ---

  override fun togglePurpose(packId: String) = purposeHandler.togglePurpose(packId)

  override fun updateOtherPurpose(text: String) = purposeHandler.updateOtherPurpose(text)

  override fun toggleProgram(programId: String) = purposeHandler.toggleProgram(programId)

  // --- Forms ---

  override fun createDatasetForForm(formKey: String, kind: DatasetKind?, open: Boolean) =
    formDatasetLinkHandler.createDatasetForForm(formKey, kind, open)

  override fun createDatasetForChoiceQuestion(
    formKey: String,
    questionIndex: Int,
    kind: DatasetKind,
  ) = formDatasetLinkHandler.createDatasetForChoiceQuestion(formKey, questionIndex, kind)

  override fun createDatasetForChoiceQuestion(questionIndex: Int, kind: DatasetKind) =
    formDatasetLinkHandler.createDatasetForChoiceQuestion(questionIndex, kind)

  override fun createFormForDataset(datasetKey: String, open: Boolean) =
    formDatasetLinkHandler.createFormForDataset(datasetKey, open)

  override fun setFormSaveToMode(formKey: String, mode: SaveToMode) =
    formDatasetLinkHandler.setFormSaveToMode(formKey, mode)

  override fun unlinkDataset(datasetKey: String) = formDatasetLinkHandler.unlinkDataset(datasetKey)

  override fun addForm() = formCatalogHandler.addForm()

  override fun addFormFromTemplate(templateId: String, openDataset: Boolean): String? =
    formCatalogHandler.addFormFromTemplate(templateId, openDataset)

  override fun addFormsForPurpose(
    packId: String,
    starterTemplateId: String,
    programIds: List<String>,
  ): String? = formCatalogHandler.addFormsForPurpose(packId, starterTemplateId, programIds)

  override fun importForm(imported: ImportedForm): String = formCatalogHandler.importForm(imported)

  override fun deleteForm(key: String) = formCatalogHandler.deleteForm(key)

  override fun moveForm(key: String, toIndex: Int) = formCatalogHandler.moveForm(key, toIndex)

  // --- Map layers & Data tables ---

  override fun addDataset(kind: DatasetKind) = datasetHandler.addDataset(kind)

  override fun deleteDataset(key: String) = datasetHandler.deleteDataset(key)

  override fun updateDataset(key: String, transform: (EntityDataset) -> EntityDataset) =
    datasetHandler.updateDataset(key, transform)

  override fun moveDataset(key: String, toIndex: Int) = datasetHandler.moveDataset(key, toIndex)

  override fun addProperty(key: String) = datasetHandler.addProperty(key)

  override fun updateProperty(key: String, index: Int, property: EntityProperty) =
    datasetHandler.updateProperty(key, index, property)

  override fun removeProperty(key: String, index: Int) = datasetHandler.removeProperty(key, index)

  override fun addRow(key: String, at: LatLng?, geometry: List<LatLng>?): String =
    datasetHandler.addRow(key, at, geometry)

  override fun updateCell(key: String, rowKey: String, property: String, value: String) =
    datasetHandler.updateCell(key, rowKey, property, value)

  override fun updateGeometry(key: String, rowKey: String, geometry: List<LatLng>) =
    datasetHandler.updateGeometry(key, rowKey, geometry)

  override fun removeRow(key: String, rowKey: String) = datasetHandler.removeRow(key, rowKey)

  override fun importMapLayer(plan: MapLayerImportPlan, selectAfterImport: Boolean): String =
    datasetHandler.importMapLayer(plan, selectAfterImport)

  override fun importDataTable(plan: DataTableImportPlan): String =
    datasetHandler.importDataTable(plan)

  override fun previewDataTableImport(
    fileName: String,
    text: String,
    dateOrder: DateOrder,
  ): DataTableImportPreview = datasetHandler.previewDataTableImport(fileName, text, dateOrder)

  override fun previewMapLayerImport(fileName: String, text: String): MapLayerImportPreview =
    datasetHandler.previewMapLayerImport(fileName, text)

  override fun tableRepository(): EntityTableRepository? = entityTableRepository?.takeIf {
    it.isLazyLoadingEnabled
  }

  // --- Sample plots ---

  override fun addSamplePlotsLayer(): String = samplePlotHandler.addSamplePlotsLayer()

  override fun updateSampleDesign(
    key: String,
    transform: (SampleDesignConfig) -> SampleDesignConfig,
  ) = samplePlotHandler.updateSampleDesign(key, transform)

  override fun rerollSeed(key: String) = samplePlotHandler.rerollSeed(key)

  override fun samplingArea(config: SampleDesignConfig): SamplingAreaResult =
    samplePlotHandler.samplingArea(config)

  override fun strataIds(config: SampleDesignConfig): List<String> =
    samplePlotHandler.strataIds(config)

  override fun estimateSample(key: String): SampleEstimate? = samplePlotHandler.estimateSample(key)

  /**
   * Fingerprint of generated layer [dataset]'s current inputs (area and design), or `null` if the
   * area isn't available. Cached, so calling it on every recomposition is cheap.
   */
  fun currentInputHash(dataset: EntityDataset): String? =
    samplePlotHandler.currentInputHash(dataset)

  override fun isDesignStale(dataset: EntityDataset): Boolean =
    samplePlotHandler.isDesignStale(dataset)

  override fun generateSamplePlots(datasetKey: String) =
    samplePlotHandler.generateSamplePlots(datasetKey)

  /**
   * Generates the sample plots of layer [datasetKey] from its design, replacing its features.
   *
   * Plots are produced in chunks; between chunks progress is published in
   * [SurveyEditorUiState.generation]. Features are only replaced once generation succeeds, so
   * cancelling ([cancelGeneration]) or a failure leaves the previous plots untouched. Failures are
   * also reported as a dataset issue until the next attempt.
   */
  suspend fun generateSample(datasetKey: String): SampleGenerationOutcome =
    samplePlotHandler.generateSample(datasetKey)

  /** Same as [generateSample]; reads better at call sites that replace existing plots. */
  suspend fun regenerateSample(datasetKey: String): SampleGenerationOutcome =
    samplePlotHandler.regenerateSample(datasetKey)

  override fun cancelGeneration() = samplePlotHandler.cancelGeneration()

  // --- Plot review ---

  override fun updatePlotReview(datasetKey: String, options: PlotReviewOptions) =
    plotReviewHandler.updatePlotReview(datasetKey, options)

  override fun removePlotReview(datasetKey: String) = plotReviewHandler.removePlotReview(datasetKey)
}
