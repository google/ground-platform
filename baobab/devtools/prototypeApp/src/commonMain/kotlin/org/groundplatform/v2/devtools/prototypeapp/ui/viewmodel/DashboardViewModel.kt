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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.groundplatform.v2.devtools.prototypeapp.domain.model.AuthProfile
import org.groundplatform.v2.devtools.prototypeapp.domain.model.EntityDetailsPane
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ListFilterTab
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MutationLogItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationLibrary
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyMapAnchor
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.UploadStatusFilter
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.DatasetKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyEditorDraft
import org.groundplatform.v2.devtools.prototypeapp.domain.model.matchesUploadsFilters
import org.groundplatform.v2.devtools.prototypeapp.domain.model.newestFirst
import org.groundplatform.v2.devtools.prototypeapp.domain.model.outboxNewestFirst
import org.groundplatform.v2.devtools.prototypeapp.domain.model.uploadedNewestFirst
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.AuthRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.ImpactEventRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.LibraryRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.MutationRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.OrganizationRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyContent
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyEditorRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.CreateSurveyUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ExportFile
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ExportSurveyDataUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ResolveLibraryUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ResolvedLibrary
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.SyncMutationsUseCase
import org.groundplatform.v2.devtools.prototypeapp.ui.state.DashboardEvent
import org.groundplatform.v2.devtools.prototypeapp.ui.state.DashboardUiState
import org.groundplatform.v2.devtools.prototypeapp.ui.state.DatasetExportOptions

/**
 * User intents of the web dashboard, the web Surveys page, the searchable list's filters, and the
 * `Uploads` drawer sub-screen. Implemented by [DashboardViewModel].
 */
interface DashboardActions {
  // --- Exports ---

  /** Records a CSV download of dataset [datasetId] as an export event. */
  fun recordCsvExport(datasetId: String) {}

  /** Dataset [datasetId] as GeoJSON (recorded as an export event), or `null` if it has no map. */
  fun exportGeoJson(datasetId: String): ExportFile? = null

  /**
   * Dataset [datasetId] exported through export profile [profileId] (recorded as an export event
   * with the profile), or `null` if the profile isn't available for it.
   */
  fun exportWithProfile(datasetId: String, profileId: String): ExportFile? = null

  // --- Surveys (web) ---

  /**
   * Opens a survey in the web app (dashboard and Survey editor). Unlike the mobile flow, this
   * doesn't download the survey for offline use or move the mobile preview to the Main Survey UI.
   */
  fun openSurveyOnWeb(surveyId: String)

  /**
   * Creates a new, empty survey owned by the signed-in user, optionally in [organizationId], makes
   * it active, and returns its ID. The caller normally opens the Survey editor next.
   */
  fun createSurvey(
    title: String,
    organizationId: String? = null,
    purposeIds: List<String> = emptyList(),
    programIds: List<String> = emptyList(),
  ): String

  // --- Left-hand side panel ---

  fun toggleSidePanel()

  fun expandSidePanel()

  fun collapseSidePanel()

  fun updateSidePanelExpanded(expanded: Boolean)

  /**
   * Sets the left-hand panel width to [widthDp], clamped to
   * [DashboardUiState.MIN_SIDE_PANEL_WIDTH_DP]..[DashboardUiState.MAX_SIDE_PANEL_WIDTH_DP].
   * Non-finite values are ignored.
   */
  fun updateSidePanelWidth(widthDp: Float)

  // --- Right-hand details panel ---

  fun toggleDetailsPanel()

  fun expandDetailsPanel()

  fun collapseDetailsPanel()

  fun updateDetailsPanelExpanded(expanded: Boolean)

  // --- Bottom data table ---

  /**
   * Expands or collapses the bottom data table. Expanding collapses the details panel; collapsing
   * re-expands it when a layer is selected here or a map feature or submission is selected
   * elsewhere ([hasSelection]).
   */
  fun updateDashboardTableExpanded(expanded: Boolean, hasSelection: Boolean = false)

  fun toggleDashboardTableExpanded(hasSelection: Boolean = false)

  /** Activates the bottom data table tab for the dataset with [datasetId]. */
  fun selectDashboardTable(datasetId: String)

  /** "Show in table": expands the bottom data table on the selected layer. */
  fun showSelectedLayerInTable()

  /** "Show in table": expands the bottom data table on [datasetId] (the selected entity's). */
  fun showDatasetInTable(datasetId: String)

  // --- Layer selection & entity details pane ---

  /**
   * Selects the map layer or data table of the entity dataset with [datasetId], opening its details
   * card; `null` clears the layer selection. The shell clears the selected map feature, submission,
   * and place alongside.
   */
  fun selectLayer(datasetId: String?)

  /** Shows the selected entity's `1:N` submissions in its details surface. */
  fun showEntitySubmissions()

  /** Returns the selected entity's details surface to its properties. */
  fun showEntityProperties()

  /** Selects a tab of the entity details card (`Data` or `History`) without other side effects. */
  fun selectEntityDetailsTab(pane: EntityDetailsPane)

  // --- Searchable list ---

  fun updateListSearchQuery(query: String)

  fun clearListSearchQuery()

  fun selectListFilterTab(tab: ListFilterTab)

  /** Collapses or expands the map features of the entity dataset with [datasetId] in the list. */
  fun toggleListDatasetCollapsed(datasetId: String)

  // --- Uploads ---

  /** Opens the `Uploads` screen showing every entity's mutations, filtered to [filter] if set. */
  fun showUploads(filter: UploadStatusFilter? = null)

  /** Opens the `Uploads` screen filtered to the mutations of the entity with [entityId]. */
  fun openUploadsForEntity(entityId: String)

  fun selectUploadStatusFilter(filter: UploadStatusFilter?)

  /** Toggles [filter] (selecting it, or clearing it if already active). */
  fun toggleUploadStatusFilter(filter: UploadStatusFilter)

  fun clearUploadsEntityFilter()

  /** Uploads a single Outbox mutation now. */
  fun syncMutationNow(mutationId: String)

  /** Uploads every pending, in-progress, or failed mutation of the active survey. */
  fun syncAllOutboxMutations()
}

/**
 * ViewModel of the web dashboard (panel layout, data tables, layer selection, entity details pane),
 * the web Surveys page (survey list, opening and creating surveys), the searchable list's filters,
 * and the `Uploads` drawer sub-screen (mutation log filters and sync).
 *
 * Surveys, map features, and layers come from [SurveyRepository], organizations from
 * [OrganizationRepository], the signed-in user from [AuthRepository], and the mutation log from
 * [MutationRepository]. Survey creation runs through [CreateSurveyUseCase] and uploads through
 * [SyncMutationsUseCase]. Outcomes that reach beyond this slice (switching the active survey,
 * showing the `Uploads` sub-screen, notices) are published as [DashboardEvent]s for the app shell.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
  private val surveyRepository: SurveyRepository,
  organizationRepository: OrganizationRepository,
  authRepository: AuthRepository,
  mutationRepository: MutationRepository,
  private val createSurveyUseCase: CreateSurveyUseCase,
  private val syncMutationsUseCase: SyncMutationsUseCase,
  private val scope: CoroutineScope,
  /** Organization libraries, whose Purpose Packs the Create survey dialog offers. */
  libraryRepository: LibraryRepository? = null,
  private val resolveLibraryUseCase: ResolveLibraryUseCase = ResolveLibraryUseCase(),
  /** Activity records (e.g. receipts) waiting to upload, shown on the `Uploads` screen. */
  impactEventRepository: ImpactEventRepository? = null,
  /** The active survey's editor draft: dataset schemas and concept links for export profiles. */
  surveyEditorRepository: SurveyEditorRepository? = null,
  private val exportSurveyData: ExportSurveyDataUseCase = ExportSurveyDataUseCase(),
) : DashboardActions {
  /** Everything the dashboard reads from the local data store. */
  private data class Data(
    val surveys: List<SurveyPreviewItem> = emptyList(),
    val activeSurveyId: String = "",
    val content: SurveyContent = SurveyContent(),
    val organizations: List<Organization> = emptyList(),
    val profile: AuthProfile = AuthProfile("", "", ""),
    val mutations: List<MutationLogItem> = emptyList(),
    val libraries: Map<String, OrganizationLibrary> = emptyMap(),
    val pendingActivityRecordCount: Int = 0,
    val draft: SurveyEditorDraft? = null,
  )

  /** Session (non-persisted) state of the dashboard slice. */
  private data class Session(
    val isSidePanelExpanded: Boolean = true,
    val sidePanelWidthDp: Float = DashboardUiState.DEFAULT_SIDE_PANEL_WIDTH_DP,
    val isDetailsPanelExpanded: Boolean = true,
    val isDashboardTableExpanded: Boolean = false,
    val dashboardTableDatasetId: String? = null,
    val selectedLayerDatasetId: String? = null,
    val entityDetailsPane: EntityDetailsPane = EntityDetailsPane.PROPERTIES,
    val listSearchQuery: String = "",
    val listFilterTab: ListFilterTab = ListFilterTab.ALL,
    val collapsedListDatasetIds: Set<String> = emptySet(),
    val selectedUploadStatusFilter: UploadStatusFilter? = null,
    val uploadsEntityFilterId: String? = null,
  )

  private val data: StateFlow<Data> =
    combine(
        combine(
          surveyRepository.observeSurveys(),
          surveyRepository.observeActiveSurveyId().flatMapLatest { id ->
            surveyRepository.observeSurveyContent(id).map { id to it }
          },
          organizationRepository.observeOrganizations(),
          authRepository.observeSession(),
          mutationRepository.observeMutations(),
        ) { surveys, (activeId, content), organizations, auth, mutations ->
          Data(
            surveys = surveys,
            activeSurveyId = activeId,
            content = content,
            organizations = organizations,
            profile = auth.profile,
            mutations = mutations,
          )
        },
        libraryRepository?.observeLibraries() ?: flowOf(emptyMap()),
        impactEventRepository?.observeEvents()?.map { events -> events.count { !it.isUploaded } }
          ?: flowOf(0),
        surveyRepository.observeActiveSurveyId().flatMapLatest { id ->
          surveyEditorRepository?.observeDraft(id)?.map<SurveyEditorDraft, SurveyEditorDraft?> {
            it
          } ?: flowOf(null)
        },
      ) { data, libraries, pendingActivityRecords, draft ->
        data.copy(
          libraries = libraries,
          pendingActivityRecordCount = pendingActivityRecords,
          draft = draft,
        )
      }
      .stateIn(scope, SharingStarted.Eagerly, Data())

  private val session = MutableStateFlow(Session())

  private val _events =
    MutableSharedFlow<DashboardEvent>(
      extraBufferCapacity = 64,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

  /** Outcomes of dashboard actions that the app shell applies outside this slice. */
  val events: Flow<DashboardEvent> = _events.asSharedFlow()

  val uiState: StateFlow<DashboardUiState> =
    combine(data, session, ::buildUiState)
      .stateIn(scope, SharingStarted.Eagerly, DashboardUiState())

  private fun buildUiState(data: Data, session: Session): DashboardUiState {
    val mutations = data.mutations
    val memberOrganizationIds =
      data.organizations.filter { it.isMember(data.profile.email) }.map { it.id }
    return DashboardUiState(
      exportOptions = exportOptions(data),
      surveys = data.surveys,
      activeSurveyId = data.activeSurveyId,
      organizations = data.organizations,
      signedInUserEmail = data.profile.email,
      surveyLibraries =
        (listOf<String?>(null) + memberOrganizationIds).associate { id ->
          id.orEmpty() to resolveLibraryUseCase.forOrganization(data.libraries, id)
        },
      entities = data.content.entities,
      mapLayers = data.content.mapLayers,
      mutations = mutations,
      isSidePanelExpanded = session.isSidePanelExpanded,
      sidePanelWidthDp = session.sidePanelWidthDp,
      isDetailsPanelExpanded = session.isDetailsPanelExpanded,
      isDashboardTableExpanded = session.isDashboardTableExpanded,
      dashboardTableDatasetId = session.dashboardTableDatasetId,
      selectedLayerDatasetId = session.selectedLayerDatasetId,
      entityDetailsPane = session.entityDetailsPane,
      listSearchQuery = session.listSearchQuery,
      listFilterTab = session.listFilterTab,
      collapsedListDatasetIds = session.collapsedListDatasetIds,
      selectedUploadStatusFilter = session.selectedUploadStatusFilter,
      uploadsEntityFilterId = session.uploadsEntityFilterId,
      allMutationsSorted = mutations.newestFirst(),
      filteredUploadMutations =
        mutations.newestFirst().filter {
          it.matchesUploadsFilters(
            session.selectedUploadStatusFilter,
            session.uploadsEntityFilterId,
          )
        },
      outboxMutations = mutations.outboxNewestFirst(),
      uploadedMutations = mutations.uploadedNewestFirst(),
      pendingUploadEntityIds =
        mutations.filter { it.isOutbox }.mapTo(mutableSetOf()) { it.entityId },
      pendingActivityRecordCount = data.pendingActivityRecordCount,
    )
  }

  // --- Exports ---

  /** The active survey's resolved library. */
  private fun activeLibrary(data: Data): ResolvedLibrary {
    val organizationId = data.surveys.firstOrNull { it.id == data.activeSurveyId }?.organizationId
    return resolveLibraryUseCase.forOrganization(data.libraries, organizationId)
  }

  /** Export choices for each dataset of the active survey. */
  private fun exportOptions(data: Data): Map<String, DatasetExportOptions> {
    val draft = data.draft ?: return emptyMap()
    val library = activeLibrary(data)
    val profiles = exportSurveyData.enabledProfiles(library, draft.details.purposeIds)
    return draft.datasets.associate { dataset ->
      val isMap = dataset.kind == DatasetKind.MAP_LAYER
      dataset.id to
        DatasetExportOptions(
          datasetId = dataset.id,
          hasGeometry = isMap,
          profilePlans =
            if (!isMap) {
              emptyList()
            } else {
              profiles.map { profile ->
                exportSurveyData.plan(
                  profile = profile,
                  dataset = dataset,
                  forms = draft.forms,
                  library = library,
                  entities = data.content.entities,
                  language = draft.details.defaultLanguage,
                )
              }
            },
        )
    }
  }

  private fun datasetEntities(datasetId: String) =
    data.value.content.entities.filter { it.datasetId == datasetId }

  override fun recordCsvExport(datasetId: String) {
    val surveyId = data.value.activeSurveyId
    val entities = datasetEntities(datasetId)
    scope.launch { exportSurveyData.recordExport(surveyId, entities) }
  }

  override fun exportGeoJson(datasetId: String): ExportFile? {
    val current = data.value
    val dataset = current.draft?.datasets?.firstOrNull { it.id == datasetId } ?: return null
    if (dataset.kind != DatasetKind.MAP_LAYER) return null
    val entities = datasetEntities(datasetId)
    val file =
      exportSurveyData.geoJson(dataset, entities, SurveyMapAnchor.forSurvey(current.activeSurveyId))
    scope.launch { exportSurveyData.recordExport(current.activeSurveyId, entities) }
    return file
  }

  override fun exportWithProfile(datasetId: String, profileId: String): ExportFile? {
    val current = data.value
    val dataset = current.draft?.datasets?.firstOrNull { it.id == datasetId } ?: return null
    val plan =
      exportOptions(current)[datasetId]?.profilePlans?.firstOrNull { it.profile.id == profileId }
        ?: return null
    val entities = datasetEntities(datasetId)
    val file =
      exportSurveyData.profileGeoJson(
        plan,
        dataset,
        entities,
        SurveyMapAnchor.forSurvey(current.activeSurveyId),
      )
    scope.launch { exportSurveyData.recordExport(current.activeSurveyId, entities, profileId) }
    return file
  }

  /** Returns session state to its defaults (used by the prototype's Reset). */
  fun reset() {
    session.value = Session()
  }

  /**
   * Follows a map feature selection made on the map or in a list: clears the layer selection,
   * returns the details pane to the properties, switches the bottom table to the feature's dataset,
   * and (unless the table is expanded) shows the details card.
   */
  fun onEntitySelected(entityId: String?) {
    val datasetId = entityId?.let { id ->
      data.value.content.entities.firstOrNull { it.id == id }?.datasetId
    }
    session.update {
      it.copy(
        selectedLayerDatasetId = null,
        entityDetailsPane = EntityDetailsPane.PROPERTIES,
        dashboardTableDatasetId = datasetId ?: it.dashboardTableDatasetId,
        isDetailsPanelExpanded =
          if (entityId != null && !it.isDashboardTableExpanded) true else it.isDetailsPanelExpanded,
      )
    }
  }

  /**
   * Follows a submission being opened for inspection: clears the layer selection, shows the
   * submissions pane (closing the submission returns to the feature's submissions list), switches
   * the bottom table to [tableDatasetId] when given, and (unless the table is expanded) shows the
   * details card.
   */
  fun onSubmissionOpened(tableDatasetId: String? = null) {
    session.update {
      it.copy(
        selectedLayerDatasetId = null,
        entityDetailsPane = EntityDetailsPane.SUBMISSIONS,
        dashboardTableDatasetId = tableDatasetId ?: it.dashboardTableDatasetId,
        isDetailsPanelExpanded =
          if (!it.isDashboardTableExpanded) true else it.isDetailsPanelExpanded,
      )
    }
  }

  // --- Surveys (web) ---

  override fun openSurveyOnWeb(surveyId: String) {
    if (data.value.surveys.none { it.id == surveyId }) return
    scope.launch { surveyRepository.setActiveSurveyId(surveyId) }
    session.update {
      it.copy(
        selectedLayerDatasetId = null,
        dashboardTableDatasetId = null,
        isDashboardTableExpanded = false,
      )
    }
    _events.tryEmit(DashboardEvent.SurveyActivated(surveyId))
  }

  override fun createSurvey(
    title: String,
    organizationId: String?,
    purposeIds: List<String>,
    programIds: List<String>,
  ): String {
    val current = data.value
    val survey =
      createSurveyUseCase.newSurvey(
        title = title,
        existingSurveys = current.surveys,
        organization =
          organizationId?.let { id -> current.organizations.firstOrNull { it.id == id } },
        ownerEmail = current.profile.email,
      )
    scope.launch {
      createSurveyUseCase(
        survey,
        ownerName = current.profile.displayName,
        purposeIds = purposeIds,
        programIds = programIds,
      )
    }
    session.update { it.copy(selectedLayerDatasetId = null) }
    _events.tryEmit(DashboardEvent.SurveyActivated(survey.id))
    _events.tryEmit(DashboardEvent.Notice("Created survey \"${survey.title}\"."))
    return survey.id
  }

  /** Clears the layer selection when the active survey changes outside this slice. */
  fun clearLayerSelection() {
    session.update { it.copy(selectedLayerDatasetId = null) }
  }

  // --- Left-hand side panel ---

  override fun toggleSidePanel() {
    session.update { it.copy(isSidePanelExpanded = !it.isSidePanelExpanded) }
  }

  override fun expandSidePanel() = updateSidePanelExpanded(true)

  override fun collapseSidePanel() = updateSidePanelExpanded(false)

  override fun updateSidePanelExpanded(expanded: Boolean) {
    session.update { it.copy(isSidePanelExpanded = expanded) }
  }

  override fun updateSidePanelWidth(widthDp: Float) {
    if (!widthDp.isFinite()) return
    session.update {
      it.copy(
        sidePanelWidthDp =
          widthDp.coerceIn(
            DashboardUiState.MIN_SIDE_PANEL_WIDTH_DP,
            DashboardUiState.MAX_SIDE_PANEL_WIDTH_DP,
          )
      )
    }
  }

  // --- Right-hand details panel ---

  override fun toggleDetailsPanel() {
    session.update { it.copy(isDetailsPanelExpanded = !it.isDetailsPanelExpanded) }
  }

  override fun expandDetailsPanel() = updateDetailsPanelExpanded(true)

  override fun collapseDetailsPanel() = updateDetailsPanelExpanded(false)

  override fun updateDetailsPanelExpanded(expanded: Boolean) {
    session.update { it.copy(isDetailsPanelExpanded = expanded) }
  }

  // --- Bottom data table ---

  override fun updateDashboardTableExpanded(expanded: Boolean, hasSelection: Boolean) {
    session.update {
      val wasExpanded = it.isDashboardTableExpanded
      val detailsExpanded =
        when {
          expanded && !wasExpanded -> false
          !expanded && wasExpanded && (hasSelection || it.selectedLayerDatasetId != null) -> true
          else -> it.isDetailsPanelExpanded
        }
      it.copy(isDashboardTableExpanded = expanded, isDetailsPanelExpanded = detailsExpanded)
    }
  }

  override fun toggleDashboardTableExpanded(hasSelection: Boolean) =
    updateDashboardTableExpanded(!session.value.isDashboardTableExpanded, hasSelection)

  override fun selectDashboardTable(datasetId: String) {
    session.update { it.copy(dashboardTableDatasetId = datasetId) }
  }

  override fun showSelectedLayerInTable() {
    val datasetId = session.value.selectedLayerDatasetId ?: return
    showDatasetInTable(datasetId)
  }

  override fun showDatasetInTable(datasetId: String) {
    selectDashboardTable(datasetId)
    updateDashboardTableExpanded(expanded = true, hasSelection = true)
  }

  // --- Layer selection & entity details pane ---

  override fun selectLayer(datasetId: String?) {
    session.update {
      if (datasetId == null) {
        it.copy(selectedLayerDatasetId = null)
      } else {
        it.copy(
          selectedLayerDatasetId = datasetId,
          entityDetailsPane = EntityDetailsPane.PROPERTIES,
          dashboardTableDatasetId = datasetId,
          isDetailsPanelExpanded =
            if (!it.isDashboardTableExpanded) true else it.isDetailsPanelExpanded,
        )
      }
    }
  }

  override fun showEntitySubmissions() = selectEntityDetailsTab(EntityDetailsPane.SUBMISSIONS)

  override fun showEntityProperties() = selectEntityDetailsTab(EntityDetailsPane.PROPERTIES)

  override fun selectEntityDetailsTab(pane: EntityDetailsPane) {
    session.update { it.copy(entityDetailsPane = pane) }
  }

  // --- Searchable list ---

  override fun updateListSearchQuery(query: String) {
    session.update { it.copy(listSearchQuery = query) }
  }

  override fun clearListSearchQuery() = updateListSearchQuery("")

  override fun selectListFilterTab(tab: ListFilterTab) {
    session.update { it.copy(listFilterTab = tab) }
  }

  override fun toggleListDatasetCollapsed(datasetId: String) {
    session.update {
      it.copy(
        collapsedListDatasetIds =
          if (datasetId in it.collapsedListDatasetIds) it.collapsedListDatasetIds - datasetId
          else it.collapsedListDatasetIds + datasetId
      )
    }
  }

  // --- Uploads ---

  override fun showUploads(filter: UploadStatusFilter?) {
    session.update { it.copy(selectedUploadStatusFilter = filter, uploadsEntityFilterId = null) }
    _events.tryEmit(DashboardEvent.UploadsOpened)
  }

  override fun openUploadsForEntity(entityId: String) {
    session.update { it.copy(selectedUploadStatusFilter = null, uploadsEntityFilterId = entityId) }
    _events.tryEmit(DashboardEvent.UploadsOpened)
  }

  override fun selectUploadStatusFilter(filter: UploadStatusFilter?) {
    session.update { it.copy(selectedUploadStatusFilter = filter) }
  }

  override fun toggleUploadStatusFilter(filter: UploadStatusFilter) {
    session.update {
      it.copy(
        selectedUploadStatusFilter = if (it.selectedUploadStatusFilter == filter) null else filter
      )
    }
  }

  override fun clearUploadsEntityFilter() {
    session.update { it.copy(uploadsEntityFilterId = null) }
  }

  override fun syncMutationNow(mutationId: String) {
    scope.launch {
      val notice = syncMutationsUseCase.syncSingleMutation(mutationId) ?: return@launch
      _events.tryEmit(DashboardEvent.Notice(notice))
    }
  }

  override fun syncAllOutboxMutations() {
    val surveyId = data.value.activeSurveyId
    scope.launch {
      val notice = syncMutationsUseCase.syncAllOutboxMutations(surveyId) ?: return@launch
      _events.tryEmit(DashboardEvent.Notice(notice))
    }
  }
}
