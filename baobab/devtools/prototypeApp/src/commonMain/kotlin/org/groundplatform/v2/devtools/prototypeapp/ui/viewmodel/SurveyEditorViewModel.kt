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

import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
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
import org.groundplatform.v2.core.sampling.SampleEstimate
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryConcept
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LocalizedText
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationLibrary
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPlaceItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.ChoiceSource
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.CollaboratorRole
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.DatasetKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorForm
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorFormTemplates
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityDataset
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityIdSource
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityProperty
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityRow
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.FormConceptLinkSync
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.FormDatasetLinks
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.FormEditorValidator
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.FormIds
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.GeometryKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.ImportedForm
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.LatLng
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.LayerStyle
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.MapLayerImportPlan
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.PropertyType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SampleAreaSource
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SampleDesignConfig
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SampleGenerationOutcome
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SampleGenerationProgress
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SampleMethod
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SamplePlotProperties
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SamplingAreaResult
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SamplingAreas
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SaveToMode
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SaveToRules
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SharingPolicy
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SharingSettings
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyArea
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyDetails
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyEditorDerivation
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyEditorDraft
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyEditorForm
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyFormTemplates
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.isoUtc
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.slugify
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.toEditorDataset
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.withRenamedTargetDataset
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.withRenamedTargetProperty
import org.groundplatform.v2.devtools.prototypeapp.domain.model.geometryKind
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.AuthRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.LibraryRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.OrganizationRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.PlaceRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyEditorRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.GenerateSamplePlotsUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.InviteCollaboratorUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ManageLibraryUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ResolveLibraryUseCase
import org.groundplatform.v2.devtools.prototypeapp.ui.common.platformEpochMillis
import org.groundplatform.v2.devtools.prototypeapp.ui.formeditor.moved
import org.groundplatform.v2.devtools.prototypeapp.ui.state.FormLibraryContext
import org.groundplatform.v2.devtools.prototypeapp.ui.state.SurveyEditorEvent
import org.groundplatform.v2.devtools.prototypeapp.ui.state.SurveyEditorSection
import org.groundplatform.v2.devtools.prototypeapp.ui.state.SurveyEditorUiState

/**
 * Map feature edits made by drawing on an interactive map: the subset of [SurveyEditorActions] that
 * the Map layer editor's map needs, so other editors (e.g. the survey area Draw tab) can reuse the
 * map with a scratch feature store.
 */
interface MapFeatureEditor {
  /**
   * Appends an entity and returns its row key. Map layer features use [geometry] when given (e.g.
   * vertices drawn on the map), otherwise a small default shape at [at] or near existing features.
   */
  fun addRow(key: String, at: LatLng? = null, geometry: List<LatLng>? = null): String

  /**
   * Replaces a feature's vertices. Ignored for generated sample plots, whose geometry is locked.
   */
  fun updateGeometry(key: String, rowKey: String, geometry: List<LatLng>)
}

/** User intents of the Survey editor page. Implemented by [SurveyEditorViewModel]. */
interface SurveyEditorActions : MapFeatureEditor {
  // --- Navigation, layout & lifecycle ---

  fun select(section: SurveyEditorSection)

  /**
   * Sets the editor's left-hand navigation panel width to [widthDp], clamped to
   * [SurveyEditorUiState.MIN_SIDE_PANEL_WIDTH_DP]..[SurveyEditorUiState.MAX_SIDE_PANEL_WIDTH_DP].
   * Non-finite values are ignored.
   */
  fun updateSidePanelWidth(widthDp: Float)

  /** Publishes the draft to the local data store, then leaves the editor. */
  fun publish()

  /** Throws away unpublished edits, restoring the published survey. */
  fun discardChanges()

  /** Throws away unpublished edits and leaves the editor. */
  fun close()

  /** The Form editor of Form [formKey], created on first use and kept while the Form exists. */
  fun formEditor(formKey: String): FormEditorViewModel

  // --- Survey details ---

  fun updateDetails(transform: (SurveyDetails) -> SurveyDetails)

  /**
   * Adds a language to the supported languages list if not already present. If the default language
   * is currently empty, sets it to this language as well.
   */
  fun addSupportedLanguage(code: String)

  /**
   * Removes a language from supported languages. If the removed language was the default language,
   * resets it to the first remaining supported language or empty string.
   */
  fun removeSupportedLanguage(code: String)

  /** Sets the default language. Also ensures the language is included in supported languages. */
  fun setDefaultLanguage(code: String)

  /** Sets or clears the survey area and boundaries. */
  fun setSurveyArea(area: SurveyArea?)

  /**
   * Moves the survey into [organizationId], or makes it a personal survey when `null`. Clearing the
   * organization while general access is [SharingPolicy.ORGANIZATION] falls back to
   * [SharingPolicy.RESTRICTED] and sets [SurveyEditorUiState.organizationNotice], since nobody
   * could use that policy.
   */
  fun setOrganization(organizationId: String?)

  fun dismissOrganizationNotice()

  /**
   * Looks up places matching [query] near [center] for [surveyId]; [regionSubtitle] labels results
   * without a region of their own. Results are delivered to [onResults], which may be called after
   * this returns.
   */
  fun searchPlaces(
    surveyId: String,
    query: String,
    regionSubtitle: String,
    center: LatLng,
    onResults: (List<SurveyPlaceItem>) -> Unit,
  )

  // --- Sharing ---

  fun updateSharing(transform: (SharingSettings) -> SharingSettings)

  /**
   * Adds (or updates the role of) a collaborator. New people get a pending invite with a fresh
   * invite link token. Returns an error message, or `null`.
   */
  fun inviteCollaborator(email: String, role: CollaboratorRole): String?

  /** Invalidates the old invite link of a pending collaborator and issues a new one. */
  fun resetInviteLink(email: String)

  /**
   * Simulates the invitee opening their invite link, signing in, and accepting. Their account's
   * name and photo are cached on the ACL entry for display in the Sharing pane. Returns an error
   * message, or `null`.
   */
  fun acceptInvite(
    email: String,
    displayName: String,
    photoUrl: String?,
    cachedOn: String = "",
  ): String?

  fun setCollaboratorRole(email: String, role: CollaboratorRole)

  fun removeCollaborator(email: String)

  // --- Forms ---

  /** Adds a blank Form with a linked Map layer or Data table and opens it. */
  fun addForm()

  /**
   * Adds [imported] as a new Form, linking or creating its target dataset as appropriate, and opens
   * it. Returns the new Form's key.
   */
  fun importForm(imported: ImportedForm): String

  /**
   * Adds a copy of template [templateId] from the survey's resolved library (with a linked Map
   * layer or Data table, like [addForm]) and opens it. Later edits to the template don't change the
   * copy. Returns the new Form's key, or `null` if the template isn't available.
   */
  fun addFormFromTemplate(templateId: String): String?

  // --- Purposes ---

  /** Selects or clears Purpose Pack [packId] for the survey (`SurveyDef.purpose_ids`). */
  fun togglePurpose(packId: String)

  /** Selects or clears program [programId] for the survey (`SurveyDef.program_ids`). */
  fun toggleProgram(programId: String)

  /**
   * Creates a new Map layer or Data table backed by and linked to [formKey]. [kind] defaults to a
   * Map layer if the form has a geometry question, otherwise a Data table. When [open] is true, the
   * new dataset is selected.
   */
  fun createDatasetForForm(formKey: String, kind: DatasetKind? = null, open: Boolean = true)

  /**
   * Creates a new Form backed by and linked to [datasetKey]. When [open] is true, the new Form is
   * selected in the Survey editor.
   */
  fun createFormForDataset(datasetKey: String, open: Boolean = true)

  /**
   * Switches Form [formKey] between adding new features and updating existing ones.
   *
   * Switching to updates detaches the dataset the form was adding to: it's deleted if it has no
   * features yet, otherwise kept but unlinked. Switching back to adding features creates a new
   * linked dataset if none is linked.
   */
  fun setFormSaveToMode(formKey: String, mode: SaveToMode)

  fun deleteForm(key: String)

  /** Moves Form [key] to [toIndex] in the Forms list. */
  fun moveForm(key: String, toIndex: Int)

  // --- Map layers & Data tables ---

  fun addDataset(kind: DatasetKind)

  fun deleteDataset(key: String)

  /** Edits dataset [key]; renaming its ID is followed by Forms that update it. */
  fun updateDataset(key: String, transform: (EntityDataset) -> EntityDataset)

  /** Unlinks [datasetKey] from its linked form, making its schema directly editable. */
  fun unlinkDataset(datasetKey: String)

  /**
   * Moves Map layer or Data table [key] to [toIndex] among datasets of the same kind. Datasets of
   * the other kind keep their positions.
   */
  fun moveDataset(key: String, toIndex: Int)

  fun addProperty(key: String)

  /**
   * Updates property [index]; renaming it also renames the matching cell values in every row and
   * the property references of Forms that update the dataset.
   */
  fun updateProperty(key: String, index: Int, property: EntityProperty)

  fun removeProperty(key: String, index: Int)

  fun updateCell(key: String, rowKey: String, property: String, value: String)

  fun removeRow(key: String, rowKey: String)

  /** Creates a Map layer from [plan] and opens it. Returns its key. */
  fun importMapLayer(plan: MapLayerImportPlan): String

  // --- Sample plots ---

  /**
   * Adds an empty sample plots Map layer with a default design and opens it. The design uses the
   * survey area if there is one, otherwise the first polygon Map layer as strata. Returns its key.
   */
  fun addSamplePlotsLayer(): String

  /** Edits the sample design of generated layer [key]. */
  fun updateSampleDesign(key: String, transform: (SampleDesignConfig) -> SampleDesignConfig)

  /** Picks a new random seed for generated layer [key]. */
  fun rerollSeed(key: String)

  /** The area [config] draws plots from, or why it isn't available. */
  fun samplingArea(config: SampleDesignConfig): SamplingAreaResult

  /** Distinct stratum IDs of [config]'s strata layer, in order of first appearance. */
  fun strataIds(config: SampleDesignConfig): List<String>

  /**
   * Live estimate (area and approximate plot count) for generated layer [key], or `null` if the
   * area isn't available or the engine can't estimate.
   */
  fun estimateSample(key: String): SampleEstimate?

  /**
   * Whether [dataset]'s sample plots were generated from a different area or design than the
   * current one, so they should be regenerated.
   */
  fun isDesignStale(dataset: EntityDataset): Boolean

  /**
   * Generates (or regenerates) the sample plots of layer [datasetKey] from its design in the
   * background; progress shows in [SurveyEditorUiState.generation].
   */
  fun generateSamplePlots(datasetKey: String)

  /** Stops the running generation at the next chunk boundary, keeping the previous plots. */
  fun cancelGeneration()
}

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
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SurveyEditorViewModel(
  surveyRepository: SurveyRepository,
  private val surveyEditorRepository: SurveyEditorRepository,
  organizationRepository: OrganizationRepository,
  authRepository: AuthRepository,
  private val placeRepository: PlaceRepository,
  private val generateSamplePlots: GenerateSamplePlotsUseCase =
    GenerateSamplePlotsUseCase(now = { isoUtc(platformEpochMillis()) }),
  private val inviteCollaboratorUseCase: InviteCollaboratorUseCase = InviteCollaboratorUseCase(),
  private val scope: CoroutineScope,
  /** Organization libraries; the Form editor links questions to their concepts. */
  libraryRepository: LibraryRepository? = null,
  /** Adds concepts to an organization's dictionary from the Form editor (Managers only). */
  private val manageLibraryUseCase: ManageLibraryUseCase? =
    libraryRepository?.let(::ManageLibraryUseCase),
  private val resolveLibraryUseCase: ResolveLibraryUseCase = ResolveLibraryUseCase(),
) : SurveyEditorActions {
  /** Everything the editor reads from the local data store. */
  private data class Data(
    val surveyId: String = "",
    val liveDraft: SurveyEditorDraft = SurveyEditorDraft.blank(surveyId = ""),
    val organizations: List<Organization> = emptyList(),
    val signedInUserEmail: String = "",
    val localPlaces: List<SurveyPlaceItem> = emptyList(),
    val submissionCountByDatasetId: Map<String, Int> = emptyMap(),
    /** Every stored organization library, keyed by organization ID. */
    val libraries: Map<String, OrganizationLibrary> = emptyMap(),
  )

  /** Session (non-persisted) state of the editor. */
  private data class Session(
    /** Survey the session belongs to; a different active survey starts a fresh session. */
    val surveyId: String? = null,
    /** The draft the edits started from, or `null` while the editor shows the live draft. */
    val opened: SurveyEditorDraft? = null,
    /** The edited draft, or `null` while the editor shows the live draft. */
    val draft: SurveyEditorDraft? = null,
    val section: SurveyEditorSection = SurveyEditorSection.Details,
    val sidePanelWidthDp: Float = SurveyEditorUiState.DEFAULT_SIDE_PANEL_WIDTH_DP,
    val generation: SampleGenerationProgress? = null,
    val generationErrors: Map<String, String> = emptyMap(),
    val organizationNotice: String? = null,
    val isSaving: Boolean = false,
  )

  private val data: StateFlow<Data> =
    combine(
        surveyRepository.observeActiveSurveyId().flatMapLatest { id ->
          combine(
            surveyEditorRepository.observeDraft(id),
            surveyRepository.observeSurveyContent(id).map { content ->
              content.entities
                .groupBy { it.datasetId }
                .mapValues { (_, entities) -> entities.sumOf { it.submissions.size } }
            },
          ) { draft, counts ->
            Triple(id, draft, counts)
          }
        },
        organizationRepository.observeOrganizations(),
        authRepository.observeSession(),
        placeRepository.observeLocalPlaces(),
        libraryRepository?.observeLibraries() ?: flowOf(emptyMap()),
      ) { (surveyId, draft, counts), organizations, auth, places, libraries ->
        Data(
          surveyId = surveyId,
          liveDraft = draft,
          organizations = organizations,
          signedInUserEmail = auth.profile.email,
          localPlaces = places,
          submissionCountByDatasetId = counts,
          libraries = libraries,
        )
      }
      .stateIn(scope, SharingStarted.Eagerly, Data())

  private val session = MutableStateFlow(Session())

  private val _events =
    MutableSharedFlow<SurveyEditorEvent>(
      extraBufferCapacity = 64,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

  /** Outcomes of editor actions that the app shell applies outside this slice. */
  val events: Flow<SurveyEditorEvent> = _events.asSharedFlow()

  val uiState: StateFlow<SurveyEditorUiState> =
    combine(data, session, ::buildUiState)
      .stateIn(scope, SharingStarted.Eagerly, SurveyEditorUiState())

  /** The dictionary the Form editors link questions to: the survey's resolved library. */
  private val libraryContext: StateFlow<FormLibraryContext> =
    uiState
      .map { it.library }
      .distinctUntilChanged()
      .stateIn(scope, SharingStarted.Eagerly, uiState.value.library)

  private val formEditors = mutableMapOf<String, Pair<FormEditorViewModel, Job>>()

  /** Set by [cancelGeneration]; polled between generated chunks. */
  private var cancelRequested = false

  init {
    // A different active survey starts a fresh session (its own section, draft, and Form editors).
    scope.launch {
      data
        .map { it.surveyId }
        .distinctUntilChanged()
        .collect { surveyId ->
          if (session.value.surveyId != surveyId) {
            session.value = Session(surveyId = surveyId)
            clearFormEditors()
          }
        }
    }
  }

  /**
   * The survey's resolved library (its organization's, then the global one), who may add to it, and
   * the concepts of its purposes, which rank first in label suggestions.
   */
  private fun libraryContextFor(
    details: SurveyDetails,
    organizations: List<Organization>,
    email: String,
    libraries: Map<String, OrganizationLibrary>,
  ): FormLibraryContext {
    val organization = organizations.firstOrNull { it.id == details.organizationId }
    val library = resolveLibraryUseCase.forOrganization(libraries, organization?.id)
    val canManage = manageLibraryUseCase != null
    return FormLibraryContext(
      library = library,
      organizationName = organization?.name,
      canAddToDictionary = canManage && organization?.isManager(email) == true,
      canSaveToGlobalLibrary =
        canManage &&
          organizations.any { it.id == Organization.ALL_USERS_ID && it.isManager(email) },
      language = details.defaultLanguage.ifEmpty { LocalizedText.DEFAULT_LANGUAGE },
      purposeConceptIds = library.conceptIdsForPurposes(details.purposeIds),
    )
  }

  /**
   * Adds draft [concept] to its organization's dictionary; returns why it can't be added, or `null`
   * (the library updates when the save completes).
   */
  private fun addOrganizationConcept(concept: LibraryConcept): String? {
    val manage = manageLibraryUseCase ?: return "This survey has no organization dictionary."
    val organizationLibrary =
      data.value.libraries[concept.organizationId] ?: OrganizationLibrary(concept.organizationId)
    manage.conceptError(concept, organizationLibrary, isNew = true)?.let {
      return it
    }
    scope.launch { manage.saveConcept(concept) }
    return null
  }

  private fun buildUiState(data: Data, session: Session): SurveyEditorUiState {
    val edits = session.takeIf { it.surveyId == data.surveyId && it.draft != null }
    val draft = edits?.draft ?: data.liveDraft
    return SurveyEditorUiState(
      surveyId = data.surveyId,
      opened = edits?.opened ?: data.liveDraft,
      draft = draft,
      library =
        libraryContextFor(
          draft.details,
          data.organizations,
          data.signedInUserEmail,
          data.libraries,
        ),
      organizations = data.organizations,
      signedInUserEmail = data.signedInUserEmail,
      localPlaces = data.localPlaces,
      submissionCountByDatasetId = data.submissionCountByDatasetId,
      isDirty = edits != null,
      isSaving = session.isSaving,
      section = session.section,
      sidePanelWidthDp = session.sidePanelWidthDp,
      generation = session.generation,
      generationErrors = session.generationErrors,
      organizationNotice = session.organizationNotice,
    )
  }

  /** Returns session state to its defaults (used by the prototype's Reset). */
  fun reset() {
    session.value = Session(surveyId = data.value.surveyId)
    clearFormEditors()
    hashCache.clear()
  }

  private fun clearFormEditors() {
    formEditors.values.forEach { (_, job) -> job.cancel() }
    formEditors.clear()
  }

  /** The draft the editor opened and the draft under edit, for the active survey. */
  private val draft: SurveyEditorDraft
    get() = uiState.value.draft

  private val opened: SurveyEditorDraft
    get() = uiState.value.opened

  // --- Draft edits ---

  /** Mutable working copy of the draft for one edit, mirroring the draft's fields. */
  private class Edits(draft: SurveyEditorDraft, session: Session) {
    var details: SurveyDetails = draft.details
    var sharing: SharingSettings = draft.sharing
    var forms: List<SurveyEditorForm> = draft.forms
    var datasets: List<EntityDataset> = draft.datasets
    var section: SurveyEditorSection = session.section
    var organizationNotice: String? = session.organizationNotice
    var nextId = draft.nextKeyId

    fun newKey(prefix: String) = "$prefix${nextId++}"

    fun toDraft() =
      SurveyEditorDraft(
        details = details,
        sharing = sharing,
        forms = forms,
        datasets = datasets,
        nextKeyId = nextId,
      )

    val mapLayers: List<EntityDataset>
      get() = datasets.filter { it.kind == DatasetKind.MAP_LAYER }

    fun form(key: String): SurveyEditorForm? = forms.firstOrNull { it.key == key }

    fun dataset(key: String): EntityDataset? = datasets.firstOrNull { it.key == key }

    /** The survey's datasets as seen by the save-to logic of Form [formKey]. */
    fun catalog(formKey: String) = datasets.map { it.toEditorDataset(formKey, forms) }

    fun updateDataset(key: String, transform: (EntityDataset) -> EntityDataset) {
      val old = dataset(key) ?: return
      val updated = transform(old)
      datasets = datasets.map { if (it.key == key) updated else it }
      if (updated.id != old.id) {
        forms = forms.map { it.copy(form = it.form.withRenamedTargetDataset(old.id, updated.id)) }
      }
    }

    /** Synchronizes the schema of all datasets linked to Form [formKey] with its questions. */
    fun syncDatasetsLinkedToForm(formKey: String) {
      val form = form(formKey)?.form ?: return
      if (datasets.none { it.linkedFormKey == formKey }) return
      datasets = datasets.map { d ->
        if (d.linkedFormKey != formKey) d else FormDatasetLinks.syncedWithForm(d, form)
      }
    }

    /**
     * Creates a dataset linked to Form [formKey]; see [SurveyEditorActions.createDatasetForForm].
     */
    fun createDatasetForForm(formKey: String, kind: DatasetKind?, open: Boolean) {
      val form = form(formKey)?.form ?: return
      val resolvedKind = kind ?: FormDatasetLinks.datasetKindFor(form)
      // Unlink any dataset previously linked to this form (and drop it if it has no features yet).
      val previouslyLinked = datasets.filter { it.linkedFormKey == formKey }
      val emptyLinkedKeys = previouslyLinked.filter { it.rows.isEmpty() }.map { it.key }.toSet()
      val remainingDatasets =
        datasets
          .filterNot { it.key in emptyLinkedKeys }
          .map { if (it.linkedFormKey == formKey) it.copy(linkedFormKey = null) else it }
      val datasetKey = newKey("d")
      val title =
        FormDatasetLinks.uniqueTitle(
          form.title.ifBlank { "New ${resolvedKind.singular.lowercase()}" },
          remainingDatasets.map { it.displayName },
        )
      val properties = FormDatasetLinks.linkedProperties(form)
      val dataset =
        EntityDataset(
          key = datasetKey,
          kind = resolvedKind,
          id = FormDatasetLinks.uniqueId(slugify(title), remainingDatasets.map { it.id }),
          displayName = title,
          geometryKind = FormDatasetLinks.geometryKindFor(form),
          keyProperty = properties.first().name,
          labelProperty = properties.getOrNull(1)?.name ?: properties.first().name,
          linkedFormKey = formKey,
          properties = properties,
        )
      datasets = remainingDatasets + dataset
      if (open) section = SurveyEditorSection.Dataset(dataset.key)
    }
  }

  /**
   * Applies [block] to a working copy of the draft and stores the result as the edited draft,
   * freezing the opened snapshot on the first edit. A block that changes nothing leaves the session
   * as it is.
   */
  private fun edit(block: Edits.() -> Unit) {
    val current = session.value
    val before = draft
    val openedBefore = opened
    val edits = Edits(before, current).apply(block)
    val after = edits.toDraft()
    if (
      after == before &&
        edits.section == current.section &&
        edits.organizationNotice == current.organizationNotice
    ) {
      return
    }
    session.update {
      it.copy(
        surveyId = data.value.surveyId,
        opened = openedBefore,
        draft = after,
        section = edits.section,
        organizationNotice = edits.organizationNotice,
      )
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
    if (session.value.isSaving) return
    val surveyId = data.value.surveyId
    val toSave = draft
    val previous = opened
    session.update { it.copy(isSaving = true) }
    scope.launch {
      try {
        surveyEditorRepository.saveDraft(surveyId, toSave, previous = previous)
      } finally {
        session.update {
          it.copy(
            draft = null,
            opened = null,
            isSaving = false,
            generationErrors = emptyMap(),
            organizationNotice = null,
          )
        }
      }
      _events.tryEmit(SurveyEditorEvent.Published)
    }
  }

  override fun discardChanges() {
    val live = data.value.liveDraft
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
        generationErrors = emptyMap(),
        organizationNotice = null,
        section = if (stillExists) current else SurveyEditorSection.Details,
      )
    }
  }

  override fun close() {
    discardChanges()
    _events.tryEmit(SurveyEditorEvent.Closed)
  }

  override fun formEditor(formKey: String): FormEditorViewModel =
    formEditors
      .getOrPut(formKey) {
        val job = Job(scope.coroutineContext[Job])
        val formScope = CoroutineScope(scope.coroutineContext + job)
        val initial = uiState.value
        val initialForm =
          initial.form(formKey)?.form
            ?: EditorForm(formId = "", title = "", questions = emptyList())
        val viewModel =
          FormEditorViewModel(
            formFlow =
              uiState
                .map { it.form(formKey)?.form ?: initialForm }
                .stateIn(formScope, SharingStarted.Eagerly, initialForm),
            datasetsFlow =
              uiState
                .map { it.datasetCatalog(formKey) }
                .stateIn(formScope, SharingStarted.Eagerly, initial.datasetCatalog(formKey)),
            updateForm = { transform -> updateForm(formKey, transform) },
            scope = formScope,
            libraryFlow = libraryContext,
            addOrganizationConcept = ::addOrganizationConcept,
            saveTemplate = ::saveFormAsTemplate,
          )
        viewModel to job
      }
      .first

  /**
   * Replaces Form [formKey] with [transform] of it. Datasets linked to the Form follow its
   * questions.
   */
  internal fun updateForm(formKey: String, transform: (EditorForm) -> EditorForm) = edit {
    val entry = form(formKey) ?: return@edit
    val updated = transform(entry.form)
    if (updated == entry.form) return@edit
    forms = forms.map { if (it.key == formKey) it.copy(form = updated) else it }
    if (updated.questions != entry.form.questions) syncDatasetsLinkedToForm(formKey)
    // Properties that linked questions save to inherit their concepts.
    if (updated.questions != entry.form.questions || updated.saveTo != entry.form.saveTo) {
      datasets = SaveToRules.inheritConcepts(updated, formKey, datasets)
    }
  }

  /** Synchronizes the schema of all datasets linked to Form [formKey] with its questions. */
  fun syncDatasetsLinkedToForm(formKey: String) = edit { syncDatasetsLinkedToForm(formKey) }

  // --- Survey details ---

  override fun updateDetails(transform: (SurveyDetails) -> SurveyDetails) = edit {
    details = transform(details)
  }

  override fun addSupportedLanguage(code: String) {
    val normalized = code.trim().lowercase()
    if (normalized.isEmpty()) return
    updateDetails { current ->
      val currentList = current.supportedLanguages
      val newList = if (normalized in currentList) currentList else currentList + normalized
      val defaultLang = current.defaultLanguage.ifEmpty { normalized }
      current.copy(supportedLanguages = newList, defaultLanguage = defaultLang)
    }
  }

  override fun removeSupportedLanguage(code: String) {
    val normalized = code.trim().lowercase()
    updateDetails { current ->
      val newList = current.supportedLanguages.filterNot { it.lowercase() == normalized }
      val newDefault =
        if (current.defaultLanguage.lowercase() == normalized) {
          newList.firstOrNull() ?: ""
        } else {
          current.defaultLanguage
        }
      current.copy(supportedLanguages = newList, defaultLanguage = newDefault)
    }
  }

  override fun setDefaultLanguage(code: String) {
    val normalized = code.trim().lowercase()
    if (normalized.isEmpty()) return
    updateDetails { current ->
      val currentList = current.supportedLanguages
      val newList = if (normalized in currentList) currentList else currentList + normalized
      current.copy(defaultLanguage = normalized, supportedLanguages = newList)
    }
  }

  override fun setSurveyArea(area: SurveyArea?) = updateDetails { it.copy(surveyArea = area) }

  override fun setOrganization(organizationId: String?) = edit {
    val normalized = organizationId?.takeIf { it.isNotBlank() }
    organizationNotice = null
    details = details.copy(organizationId = normalized)
    if (normalized == null && sharing.policy == SharingPolicy.ORGANIZATION) {
      sharing = sharing.copy(policy = SharingPolicy.RESTRICTED)
      organizationNotice =
        "General access was changed to \"${SharingPolicy.RESTRICTED.label}\" because the survey " +
          "no longer belongs to an organization."
    }
  }

  override fun dismissOrganizationNotice() {
    session.update { it.copy(organizationNotice = null) }
  }

  override fun searchPlaces(
    surveyId: String,
    query: String,
    regionSubtitle: String,
    center: LatLng,
    onResults: (List<SurveyPlaceItem>) -> Unit,
  ) {
    placeRepository.searchRemotePlaces(
      surveyId = surveyId,
      query = query,
      defaultRegionSubtitle = regionSubtitle,
      centerLongitude = center.lng,
      centerLatitude = center.lat,
      onResults = onResults,
    )
  }

  // --- Sharing ---

  override fun updateSharing(transform: (SharingSettings) -> SharingSettings) = edit {
    sharing = transform(sharing)
  }

  override fun inviteCollaborator(email: String, role: CollaboratorRole): String? =
    when (val outcome = inviteCollaboratorUseCase.invite(draft.sharing, email, role)) {
      is InviteCollaboratorUseCase.Outcome.Rejected -> outcome.message
      is InviteCollaboratorUseCase.Outcome.Updated -> {
        updateSharing { outcome.sharing }
        null
      }
    }

  override fun resetInviteLink(email: String) = updateSharing {
    inviteCollaboratorUseCase.resetInviteLink(it, email)
  }

  override fun acceptInvite(
    email: String,
    displayName: String,
    photoUrl: String?,
    cachedOn: String,
  ): String? =
    when (
      val outcome =
        inviteCollaboratorUseCase.acceptInvite(
          draft.sharing,
          email,
          displayName,
          photoUrl,
          cachedOn,
        )
    ) {
      is InviteCollaboratorUseCase.Outcome.Rejected -> outcome.message
      is InviteCollaboratorUseCase.Outcome.Updated -> {
        updateSharing { outcome.sharing }
        null
      }
    }

  override fun setCollaboratorRole(email: String, role: CollaboratorRole) = updateSharing { s ->
    s.copy(
      collaborators = s.collaborators.map { if (it.email == email) it.copy(role = role) else it }
    )
  }

  override fun removeCollaborator(email: String) = updateSharing { s ->
    s.copy(collaborators = s.collaborators.filterNot { it.email == email })
  }

  // --- Forms ---

  override fun addForm() = edit {
    val title = FormDatasetLinks.uniqueTitle("New form", forms.map { it.form.title })
    val formId = FormIds.newFormId()
    val formKey = newKey("f")
    val datasetKey = newKey("d")
    val blankForm = EditorFormTemplates.blank(formId, title)

    // By default, submissions add to a linked Map layer (if the form captures a location) or Data
    // table (otherwise).
    val linkedDataset =
      EntityDataset(
        key = datasetKey,
        kind = FormDatasetLinks.datasetKindFor(blankForm),
        id = FormDatasetLinks.uniqueId(slugify(title), datasets.map { it.id }),
        displayName = title,
        geometryKind = GeometryKind.POINT,
        keyProperty = "id",
        labelProperty = "id",
        linkedFormKey = formKey,
        properties =
          listOf(FormDatasetLinks.idProperty) + FormDatasetLinks.formProperties(blankForm),
      )
    datasets = datasets + linkedDataset
    forms = forms + SurveyEditorForm(formKey, blankForm)
    section = SurveyEditorSection.Form(formKey)
  }

  override fun addFormFromTemplate(templateId: String): String? {
    val template = libraryContext.value.library.formTemplate(templateId) ?: return null
    var formKey: String? = null
    edit {
      val (updated, key) = SurveyFormTemplates.addTemplate(toDraft(), template)
      forms = updated.forms
      datasets = updated.datasets
      nextId = updated.nextKeyId
      section = SurveyEditorSection.Form(key)
      formKey = key
    }
    return formKey
  }

  override fun togglePurpose(packId: String) = updateDetails { d ->
    d.copy(
      purposeIds = if (packId in d.purposeIds) d.purposeIds - packId else d.purposeIds + packId
    )
  }

  override fun toggleProgram(programId: String) = updateDetails { d ->
    d.copy(
      programIds =
        if (programId in d.programIds) d.programIds - programId else d.programIds + programId
    )
  }

  /**
   * Saves a copy of [form] as a template titled [title] in the survey organization's library, or
   * the global library when [toGlobal]. Returns why it can't be saved, or `null` (the library
   * updates when the save completes).
   */
  private fun saveFormAsTemplate(
    form: EditorForm,
    title: String,
    description: String,
    toGlobal: Boolean,
  ): String? {
    val manage = manageLibraryUseCase ?: return "Templates can't be saved here."
    val context = libraryContext.value
    val organizationId =
      if (toGlobal) {
        if (!context.canSaveToGlobalLibrary) {
          return "Only Managers of All users can add to the global library."
        }
        Organization.ALL_USERS_ID
      } else {
        val id = context.library.organizationId
        if (!context.canAddToDictionary || id == null) {
          return "Only Managers of this survey's organization can save templates."
        }
        id
      }
    manage.titleError(title)?.let {
      return it
    }
    val library = data.value.libraries[organizationId] ?: OrganizationLibrary(organizationId)
    val template =
      SurveyFormTemplates.templateFrom(
        form = form,
        id = manage.newTemplateId(library, title),
        organizationId = organizationId,
        title = title,
        description = description,
      )
    scope.launch { manage.saveTemplate(template) }
    return null
  }

  override fun importForm(imported: ImportedForm): String {
    var formKey = ""
    edit {
      formKey = newKey("f")
      val title =
        FormDatasetLinks.uniqueTitle(
          imported.form.title.ifBlank { "Imported form" },
          forms.map { it.form.title },
        )
      val baseId =
        imported.form.formId.takeIf { it.isNotBlank() && it != "data" } ?: FormIds.newFormId()
      val formId = FormDatasetLinks.uniqueId(baseId, forms.map { it.form.formId })
      val datasetIds = datasets.map { it.id }.toSet()
      val questions =
        imported.form.questions.map { q ->
          if (
            q.choiceDatasetId != null && q.choiceDatasetId !in datasetIds && q.choices.isNotEmpty()
          ) {
            q.copy(
              choiceDatasetId = null,
              choiceSource = ChoiceSource.MANUAL,
              allowAddEntity = false,
            )
          } else {
            q
          }
        }
      // Imported `ground:concept` links stay linked only when the concept is in the dictionary.
      val library = libraryContext.value.library
      var form =
        FormConceptLinkSync.reconcileImported(
          imported.form.copy(formId = formId, title = title, questions = questions)
        ) {
          library.concept(it) != null
        }
      if (form.saveTo.mode == SaveToMode.UPDATE) {
        val requestedId = form.saveTo.targetDatasetId
        val targetIndex =
          datasets.indexOfFirst { it.id == requestedId }.takeIf { it >= 0 }
            ?: datasets.indexOfFirst { !it.isGenerated }.takeIf { it >= 0 }
            ?: datasets.indices.firstOrNull()
        if (targetIndex != null) {
          val targetDataset = datasets[targetIndex]
          val targetId = targetDataset.id
          if (targetId != requestedId) {
            val validProps = targetDataset.properties.map { it.name }.toSet()
            val validMatch =
              imported.idMatchColumn in setOf("name", "label") ||
                imported.idMatchColumn in validProps
            form =
              form.copy(
                saveTo =
                  form.saveTo.copy(
                    idSource =
                      if (validMatch) form.saveTo.idSource else EntityIdSource.SELECTED_FEATURE,
                    idQuestionKey = if (validMatch) form.saveTo.idQuestionKey else null,
                    mappings = form.saveTo.mappings.filter { it.property in validProps },
                  )
              )
          }
          form =
            form.copy(
              saveTo =
                SurveyEditorDerivation.updateSaveTo(form, imported, formKey, datasets, targetId)
            )
          if (targetId == requestedId) {
            datasets =
              datasets.toMutableList().apply {
                set(
                  targetIndex,
                  SurveyEditorDerivation.withMappedProperties(get(targetIndex), form),
                )
              }
          }
        }
      } else {
        val linkIndex =
          imported.createDatasetId?.let { id ->
            datasets
              .indexOfFirst { it.id == id && it.linkedFormKey == null && !it.isGenerated }
              .takeIf { it >= 0 }
          }
        if (linkIndex != null) {
          datasets =
            datasets.toMutableList().apply {
              val linked = get(linkIndex).copy(linkedFormKey = formKey)
              set(linkIndex, FormDatasetLinks.syncedWithForm(linked, form))
            }
        } else {
          val kind = FormDatasetLinks.datasetKindFor(form)
          val datasetTitle = FormDatasetLinks.uniqueTitle(title, datasets.map { it.displayName })
          val preferredId =
            imported.createDatasetId?.takeIf { FormEditorValidator.isValidName(it) }
              ?: slugify(datasetTitle)
          val properties = FormDatasetLinks.linkedProperties(form)
          val linkedDataset =
            EntityDataset(
              key = newKey("d"),
              kind = kind,
              id = FormDatasetLinks.uniqueId(preferredId, datasets.map { it.id }),
              displayName = datasetTitle,
              geometryKind = FormDatasetLinks.geometryKindFor(form),
              keyProperty = properties.first().name,
              labelProperty = properties.getOrNull(1)?.name ?: properties.first().name,
              linkedFormKey = formKey,
              properties = properties,
            )
          datasets = datasets + linkedDataset
        }
      }
      datasets = SaveToRules.inheritConcepts(form, formKey, datasets)
      forms = forms + SurveyEditorForm(formKey, form)
      section = SurveyEditorSection.Form(formKey)
    }
    // Offer links for unlinked questions that match standard fields.
    formEditor(formKey).suggestImportMatches()
    return formKey
  }

  override fun createDatasetForForm(formKey: String, kind: DatasetKind?, open: Boolean) = edit {
    createDatasetForForm(formKey, kind, open)
  }

  override fun createFormForDataset(datasetKey: String, open: Boolean) = edit {
    val dataset = dataset(datasetKey) ?: return@edit
    val formKey = newKey("f")
    val title =
      FormDatasetLinks.uniqueTitle("${dataset.displayName} form", forms.map { it.form.title })
    val form =
      EditorForm(
        formId = FormIds.newFormId(),
        title = title,
        questions = FormDatasetLinks.questionsFor(dataset),
      )
    // Update dataset to link to this form.
    updateDataset(datasetKey) { it.copy(linkedFormKey = formKey) }
    forms = forms + SurveyEditorForm(formKey, form)
    if (open) section = SurveyEditorSection.Form(formKey)
  }

  override fun setFormSaveToMode(formKey: String, mode: SaveToMode) {
    if (draft.forms.none { it.key == formKey }) return
    // Pick the update target while the form's own dataset is still linked, so it isn't chosen.
    formEditor(formKey).setSaveToMode(mode)
    edit {
      when (mode) {
        SaveToMode.UPDATE -> {
          val linked = datasets.filter { it.linkedFormKey == formKey }
          val emptyKeys = linked.filter { it.rows.isEmpty() }.map { it.key }.toSet()
          datasets =
            datasets
              .filterNot { it.key in emptyKeys }
              .map { if (it.linkedFormKey == formKey) it.copy(linkedFormKey = null) else it }
        }
        SaveToMode.CREATE ->
          if (datasets.none { it.linkedFormKey == formKey }) {
            createDatasetForForm(formKey, kind = null, open = false)
          }
      }
    }
  }

  override fun deleteForm(key: String) {
    edit {
      val index = forms.indexOfFirst { it.key == key }
      if (index < 0) return@edit
      // Unlink any datasets linked to this deleted form.
      datasets = datasets.map { if (it.linkedFormKey == key) it.copy(linkedFormKey = null) else it }
      forms = forms.filterNot { it.key == key }
      section =
        forms.getOrNull(index.coerceAtMost(forms.lastIndex))?.let {
          SurveyEditorSection.Form(it.key)
        } ?: SurveyEditorSection.Details
    }
    formEditors.remove(key)?.second?.cancel()
  }

  override fun moveForm(key: String, toIndex: Int) = edit {
    forms = forms.moved(forms.indexOfFirst { it.key == key }, toIndex)
  }

  // --- Map layers & Data tables ---

  override fun addDataset(kind: DatasetKind) = edit {
    val title =
      FormDatasetLinks.uniqueTitle(
        "New ${kind.singular.lowercase()}",
        datasets.map { it.displayName },
      )
    val dataset =
      EntityDataset(
        key = newKey("d"),
        kind = kind,
        id = FormDatasetLinks.uniqueId(slugify(title), datasets.map { it.id }),
        displayName = title,
        geometryKind = GeometryKind.POINT,
        keyProperty = "id",
        labelProperty = "name",
        properties =
          listOf(
            EntityProperty("id", "ID", PropertyType.TEXT, required = true),
            EntityProperty("name", "Name", PropertyType.TEXT),
          ),
      )
    datasets = datasets + dataset
    section = SurveyEditorSection.Dataset(dataset.key)
  }

  override fun deleteDataset(key: String) = edit {
    val dataset = dataset(key) ?: return@edit
    val siblings = datasets.filter { it.kind == dataset.kind }
    val index = siblings.indexOf(dataset)
    datasets = datasets.filterNot { it.key == key }
    val remaining = siblings.filterNot { it.key == key }
    section =
      remaining.getOrNull(index.coerceAtMost(remaining.lastIndex))?.let {
        SurveyEditorSection.Dataset(it.key)
      } ?: SurveyEditorSection.Details
  }

  override fun updateDataset(key: String, transform: (EntityDataset) -> EntityDataset) = edit {
    updateDataset(key, transform)
  }

  override fun unlinkDataset(datasetKey: String) =
    updateDataset(datasetKey) { it.copy(linkedFormKey = null) }

  override fun moveDataset(key: String, toIndex: Int) = edit {
    val kind = dataset(key)?.kind ?: return@edit
    val siblings = datasets.filter { it.kind == kind }
    val reordered = siblings.moved(siblings.indexOfFirst { it.key == key }, toIndex).iterator()
    datasets = datasets.map { if (it.kind == kind) reordered.next() else it }
  }

  override fun addProperty(key: String) =
    updateDataset(key) { d ->
      val name = FormDatasetLinks.uniqueId("property", d.properties.map { it.name })
      d.copy(properties = d.properties + EntityProperty(name, "New property"))
    }

  override fun updateProperty(key: String, index: Int, property: EntityProperty) = edit {
    val dataset = dataset(key) ?: return@edit
    val oldName = dataset.properties.getOrNull(index)?.name
    updateDataset(key) { d ->
      val old = d.properties.getOrNull(index) ?: return@updateDataset d
      val renamed = old.name != property.name
      d.copy(
        properties = d.properties.toMutableList().apply { set(index, property) },
        keyProperty = if (renamed && d.keyProperty == old.name) property.name else d.keyProperty,
        labelProperty =
          if (renamed && d.labelProperty == old.name) property.name else d.labelProperty,
        rows =
          if (!renamed) d.rows
          else
            d.rows.map { row ->
              val value = row.values[old.name]
              if (value == null) row
              else row.copy(values = row.values - old.name + (property.name to value))
            },
      )
    }
    if (oldName != null && oldName != property.name) {
      forms = forms.map { entry ->
        val saveTarget = SaveToRules.saveTarget(entry.form, catalog(entry.key))
        entry.copy(
          form =
            entry.form.withRenamedTargetProperty(dataset.id, oldName, property.name, saveTarget)
        )
      }
    }
  }

  override fun removeProperty(key: String, index: Int) =
    updateDataset(key) { d ->
      val removed = d.properties.getOrNull(index) ?: return@updateDataset d
      val remaining = d.properties.filterIndexed { i, _ -> i != index }
      d.copy(
        properties = remaining,
        keyProperty =
          if (d.keyProperty == removed.name) remaining.firstOrNull()?.name.orEmpty()
          else d.keyProperty,
        labelProperty =
          if (d.labelProperty == removed.name) remaining.firstOrNull()?.name.orEmpty()
          else d.labelProperty,
        rows = d.rows.map { it.copy(values = it.values - removed.name) },
      )
    }

  override fun addRow(key: String, at: LatLng?, geometry: List<LatLng>?): String {
    var rowKey = ""
    edit {
      rowKey = newKey("r")
      updateDataset(key) { d ->
        val keyValue =
          FormDatasetLinks.uniqueId(
            "${d.id.take(3).uppercase()}-${d.rows.size + 1}",
            d.rows.map { it.values[d.keyProperty].orEmpty() },
          )
        val shape =
          if (d.kind == DatasetKind.MAP_LAYER) geometry ?: FormDatasetLinks.defaultGeometry(d, at)
          else emptyList()
        d.copy(rows = d.rows + EntityRow(rowKey, mapOf(d.keyProperty to keyValue), shape))
      }
    }
    return rowKey
  }

  override fun updateCell(key: String, rowKey: String, property: String, value: String) =
    updateDataset(key) { d ->
      d.copy(
        rows =
          d.rows.map {
            if (it.key == rowKey) it.copy(values = it.values + (property to value)) else it
          }
      )
    }

  override fun updateGeometry(key: String, rowKey: String, geometry: List<LatLng>) =
    updateDataset(key) { d ->
      if (d.isGenerated) d
      else d.copy(rows = d.rows.map { if (it.key == rowKey) it.copy(geometry = geometry) else it })
    }

  override fun removeRow(key: String, rowKey: String) =
    updateDataset(key) { d -> d.copy(rows = d.rows.filterNot { it.key == rowKey }) }

  override fun importMapLayer(plan: MapLayerImportPlan): String {
    var datasetKey = ""
    edit {
      val title = FormDatasetLinks.uniqueTitle(plan.name, datasets.map { it.displayName })
      datasetKey = newKey("d")
      val dataset =
        EntityDataset(
          key = datasetKey,
          kind = DatasetKind.MAP_LAYER,
          id = FormDatasetLinks.uniqueId(slugify(title), datasets.map { it.id }),
          displayName = title,
          geometryKind = plan.geometryKind,
          keyProperty = plan.keyProperty,
          labelProperty = plan.labelProperty,
          properties = plan.properties,
          rows = plan.rows.map { (values, geometry) -> EntityRow(newKey("r"), values, geometry) },
        )
      datasets = datasets + dataset
      section = SurveyEditorSection.Dataset(datasetKey)
    }
    return datasetKey
  }

  // --- Sample plots ---

  override fun addSamplePlotsLayer(): String {
    var key = ""
    edit {
      val title = FormDatasetLinks.uniqueTitle("Sample plots", datasets.map { it.displayName })
      val strata = mapLayers.firstOrNull {
        it.geometryKind == GeometryKind.POLYGON && !it.isGenerated
      }
      val design =
        if (!details.surveyArea?.parts.isNullOrEmpty() || strata == null) {
          SampleDesignConfig()
        } else {
          SampleDesignConfig(
            method = SampleMethod.STRATIFIED_RANDOM,
            areaSource = SampleAreaSource.StrataLayer(strata.key, strata.labelProperty),
          )
        }
      key = newKey("d")
      val dataset =
        EntityDataset(
          key = key,
          kind = DatasetKind.MAP_LAYER,
          id = FormDatasetLinks.uniqueId(slugify(title), datasets.map { it.id }),
          displayName = title,
          description = "Sample plots generated from a statistical sample design.",
          geometryKind = design.geometryKind,
          keyProperty = SamplePlotProperties.PLOT_ID,
          labelProperty = SamplePlotProperties.PLOT_ID,
          properties = SamplePlotProperties.schema,
          style = LayerStyle(colorHex = "#F9A825", fillOpacity = 0.15),
          generator = design,
        )
      datasets = datasets + dataset
      section = SurveyEditorSection.Dataset(key)
    }
    return key
  }

  override fun updateSampleDesign(
    key: String,
    transform: (SampleDesignConfig) -> SampleDesignConfig,
  ) = updateDataset(key) { d -> d.generator?.let { d.copy(generator = transform(it)) } ?: d }

  override fun rerollSeed(key: String) =
    updateSampleDesign(key) { it.copy(seed = Random.nextLong(1, 1_000_000)) }

  override fun samplingArea(config: SampleDesignConfig): SamplingAreaResult =
    SamplingAreas.samplingArea(config, draft.details.surveyArea, draft.datasets)

  override fun strataIds(config: SampleDesignConfig): List<String> =
    SamplingAreas.strataIds(config, draft.datasets)

  override fun estimateSample(key: String): SampleEstimate? {
    val dataset = draft.datasets.firstOrNull { it.key == key } ?: return null
    return generateSamplePlots.estimate(dataset, draft.details.surveyArea, draft.datasets)
  }

  private class HashCacheEntry(
    val areaRef: Any?,
    val property: String?,
    val design: SampleDesignConfig,
    val hash: String?,
  )

  /** Per-dataset cache so the hash is only recomputed when its inputs change (by identity). */
  private val hashCache = mutableMapOf<String, HashCacheEntry>()

  /**
   * Fingerprint of generated layer [dataset]'s current inputs (area and design), or `null` if the
   * area isn't available. Cached, so calling it on every recomposition is cheap.
   */
  fun currentInputHash(dataset: EntityDataset): String? {
    val config = dataset.generator ?: return null
    val design = config.withoutProvenance()
    val current = draft
    val (areaRef, property) =
      when (val source = config.areaSource) {
        SampleAreaSource.SurveyArea -> current.details.surveyArea?.parts to null
        is SampleAreaSource.StrataLayer ->
          current.datasets.firstOrNull { it.key == source.datasetKey }?.rows to
            source.stratumProperty
      }
    hashCache[dataset.key]?.let { c ->
      if (c.areaRef === areaRef && c.property == property && c.design == design) return c.hash
    }
    val hash = generateSamplePlots.inputHash(dataset, current.details.surveyArea, current.datasets)
    hashCache[dataset.key] = HashCacheEntry(areaRef, property, design, hash)
    return hash
  }

  override fun isDesignStale(dataset: EntityDataset): Boolean {
    val lastRun = dataset.generator?.lastRun ?: return false
    return currentInputHash(dataset) != lastRun.inputHash
  }

  override fun generateSamplePlots(datasetKey: String) {
    scope.launch { generateSample(datasetKey) }
  }

  /**
   * Generates the sample plots of layer [datasetKey] from its design, replacing its features.
   *
   * Plots are produced in chunks; between chunks progress is published in
   * [SurveyEditorUiState.generation]. Features are only replaced once generation succeeds, so
   * cancelling ([cancelGeneration]) or a failure leaves the previous plots untouched. Failures are
   * also reported as a dataset issue until the next attempt.
   */
  suspend fun generateSample(datasetKey: String): SampleGenerationOutcome {
    if (session.value.generation != null) {
      return SampleGenerationOutcome.Failed("Sample plots are already being generated.")
    }
    val current = draft
    val dataset =
      current.datasets.firstOrNull { it.key == datasetKey }
        ?: return SampleGenerationOutcome.Failed("This map layer no longer exists.")
    val config =
      dataset.generator
        ?: return SampleGenerationOutcome.Failed(GenerateSamplePlotsUseCase.NO_DESIGN)
    uiState.value.regenerateBlockedReason(dataset)?.let {
      return SampleGenerationOutcome.Blocked(it)
    }
    (samplingArea(config) as? SamplingAreaResult.Unavailable)?.let {
      return fail(datasetKey, it.message)
    }
    session.update { it.copy(generationErrors = it.generationErrors - datasetKey) }
    cancelRequested = false
    val result =
      try {
        generateSamplePlots(
          dataset = dataset,
          surveyArea = current.details.surveyArea,
          datasets = current.datasets,
          onProgress = { plotsSoFar, expected ->
            session.update {
              it.copy(generation = SampleGenerationProgress(datasetKey, plotsSoFar, expected))
            }
          },
          isCancelled = { cancelRequested },
        )
      } finally {
        session.update { it.copy(generation = null) }
      }
    return when (result) {
      GenerateSamplePlotsUseCase.Result.Cancelled -> SampleGenerationOutcome.Cancelled
      is GenerateSamplePlotsUseCase.Result.Failed -> fail(datasetKey, result.message)
      is GenerateSamplePlotsUseCase.Result.Generated -> {
        val generated = result.dataset
        updateDataset(datasetKey) { d ->
          d.copy(
            rows = generated.rows,
            geometryKind = generated.geometryKind,
            generator = (d.generator ?: config).copy(lastRun = generated.generator?.lastRun),
          )
        }
        SampleGenerationOutcome.Generated(result.plotCount)
      }
    }
  }

  /** Same as [generateSample]; reads better at call sites that replace existing plots. */
  suspend fun regenerateSample(datasetKey: String): SampleGenerationOutcome =
    generateSample(datasetKey)

  override fun cancelGeneration() {
    if (session.value.generation != null) cancelRequested = true
  }

  private fun fail(datasetKey: String, message: String): SampleGenerationOutcome.Failed {
    session.update { it.copy(generationErrors = it.generationErrors + (datasetKey to message)) }
    return SampleGenerationOutcome.Failed(message)
  }

  /** Returns a copy of this list with the element at [from] moved to [to]. */
  private fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
  }
}
