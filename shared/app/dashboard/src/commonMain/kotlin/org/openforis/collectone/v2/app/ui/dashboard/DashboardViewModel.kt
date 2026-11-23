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
package org.openforis.collectone.v2.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
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
import org.openforis.collectone.v2.app.domain.model.AuthProfile
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.MutationLogItem
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.OrganizationLibrary
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.UploadStatusFilter
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.app.domain.model.matchesUploadsFilters
import org.openforis.collectone.v2.app.domain.model.newestFirst
import org.openforis.collectone.v2.app.domain.model.outboxNewestFirst
import org.openforis.collectone.v2.app.domain.model.uploadedNewestFirst
import org.openforis.collectone.v2.app.domain.repository.AuthRepository
import org.openforis.collectone.v2.app.domain.repository.EntityTableRepository
import org.openforis.collectone.v2.app.domain.repository.ImpactEventRepository
import org.openforis.collectone.v2.app.domain.repository.LibraryRepository
import org.openforis.collectone.v2.app.domain.repository.MutationRepository
import org.openforis.collectone.v2.app.domain.repository.OrganizationRepository
import org.openforis.collectone.v2.app.domain.repository.SurveyContent
import org.openforis.collectone.v2.app.domain.repository.SurveyEditorRepository
import org.openforis.collectone.v2.app.domain.repository.SurveyRepository
import org.openforis.collectone.v2.app.domain.usecase.CreateSurveyUseCase
import org.openforis.collectone.v2.app.domain.usecase.ExportFile
import org.openforis.collectone.v2.app.domain.usecase.ExportSurveyDataUseCase
import org.openforis.collectone.v2.app.domain.usecase.ResolveLibraryUseCase
import org.openforis.collectone.v2.app.domain.usecase.SyncMutationsUseCase
import org.openforis.collectone.v2.app.ui.navigation.EntityDetailsPane
import org.openforis.collectone.v2.app.ui.viewmodel.ViewModelDispatcher
import org.openforis.collectone.v2.app.ui.viewmodel.viewModelScopeOn

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
   * Dataset [datasetId] as a KML 2.2 file with embedded web forms hosted at [balloonAppUrl]
   * (recorded as an export event), or `null` if it has no map.
   */
  fun exportKml(
    datasetId: String,
    balloonAppUrl: String = "",
    serviceUrl: String = "",
  ): ExportFile? = null

  /**
   * All map layers of the active survey as a single KML 2.2 file with one `<Folder>` per map layer
   * (recorded as an export event), or `null` if the survey has no map layers.
   */
  fun exportSurveyKml(balloonAppUrl: String = "", serviceUrl: String = ""): ExportFile? = null

  /**
   * Google Earth Web deep-link URL (`https://earth.google.com/web/c/<OpenKmlDocument>`) that opens
   * the active survey's live KML endpoint in Google Earth (recorded as an export event), or `null`
   * when no survey or backend [serviceUrl] is configured.
   */
  fun googleEarthOpenUrl(
    balloonAppUrl: String = "",
    serviceUrl: String = "",
  ): String? = null

  /**
   * Dataset [datasetId] exported through export profile [profileId] (recorded as an export event
   * with the profile), or `null` if the profile isn't available for it.
   */
  fun exportWithProfile(datasetId: String, profileId: String): ExportFile? = null

  /**
   * Repository for lazy-loading server-backed table tiles and entity details on demand, or `null`
   * when all entity properties are already local.
   */
  fun tableRepository(): EntityTableRepository? = null

  /** Fetches full unmasked entities of [datasetId] for CSV export when lazy loading is active. */
  suspend fun loadEntitiesForExport(datasetId: String): List<GeospatialEntityItem> = emptyList()

  /** Async GeoJSON export that hydrates full entity properties when lazy loading is active. */
  suspend fun exportGeoJsonAsync(datasetId: String): ExportFile? = exportGeoJson(datasetId)

  /** Async KML export that hydrates full entity properties when lazy loading is active. */
  suspend fun exportKmlAsync(
    datasetId: String,
    balloonAppUrl: String = "",
    serviceUrl: String = "",
  ): ExportFile? = exportKml(datasetId, balloonAppUrl, serviceUrl)

  /**
   * Async multi-layer KML export that hydrates full entity properties when lazy loading is active.
   */
  suspend fun exportSurveyKmlAsync(
    balloonAppUrl: String = "",
    serviceUrl: String = "",
  ): ExportFile? = exportSurveyKml(balloonAppUrl, serviceUrl)

  /** Async profile export that hydrates full entity properties when lazy loading is active. */
  suspend fun exportWithProfileAsync(datasetId: String, profileId: String): ExportFile? =
    exportWithProfile(datasetId, profileId)

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

  // --- Bottom data table & time series sheet ---

  /**
   * Expands or collapses the bottom data table. Expanding docks the right-hand panel, closes the
   * dedicated time series sheet (if open), and collapses the left-hand side panel; collapsing
   * restores the left-hand side panel to its prior state.
   */
  fun updateDashboardTableExpanded(expanded: Boolean)

  fun toggleDashboardTableExpanded()

  /** Activates the bottom data table tab for the dataset with [datasetId]. */
  fun selectDashboardTable(datasetId: String)

  /**
   * Opens the dedicated "Time series & imagery" bottom sheet (from a map feature's details card).
   */
  fun showTimeSeries() {}

  /** Closes the dedicated "Time series & imagery" bottom sheet. */
  fun closeTimeSeries() {}

  /** Expands or collapses the "Time series & imagery" bottom sheet while it is open. */
  fun updateTimeSeriesExpanded(expanded: Boolean) {}

  /** Toggles whether the open "Time series & imagery" bottom sheet is expanded or collapsed. */
  fun toggleTimeSeriesExpanded() {}

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
 * [MutationRepository]. Survey creation runs through [CreateSurveyUseCase], uploads through
 * [SyncMutationsUseCase], and dataset exports through [DashboardExports]. The layout rules live in
 * [DashboardSession]'s pure transitions; this class only applies them. Outcomes that reach beyond
 * this slice (switching the active survey, showing the `Uploads` sub-screen, notices) are published
 * as [DashboardEvent]s for the app shell.
 *
 * Actions that return a value ([createSurvey], the exports) answer from the latest store snapshot
 * synchronously and write in the background, so the caller can navigate to the new survey (or save
 * the file) before the store has caught up.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
  private val surveyRepository: SurveyRepository,
  organizationRepository: OrganizationRepository,
  authRepository: AuthRepository,
  mutationRepository: MutationRepository,
  private val createSurveyUseCase: CreateSurveyUseCase,
  private val syncMutationsUseCase: SyncMutationsUseCase,
  dispatcher: CoroutineDispatcher = ViewModelDispatcher,
  /** Organization libraries, whose Purpose Packs the Create survey dialog offers. */
  libraryRepository: LibraryRepository? = null,
  private val resolveLibraryUseCase: ResolveLibraryUseCase,
  /** Activity records (e.g. receipts) waiting to upload, shown on the `Uploads` screen. */
  impactEventRepository: ImpactEventRepository? = null,
  /** The active survey's editor draft: dataset schemas and concept links for export profiles. */
  surveyEditorRepository: SurveyEditorRepository? = null,
  exportSurveyData: ExportSurveyDataUseCase,
  /** Lazy-loads remote entity table tiles, details, and full export rows on the web console. */
  private val entityTableRepository: EntityTableRepository? = null,
) : ViewModel(viewModelScopeOn(dispatcher)), DashboardActions {
  private val exports = DashboardExports(exportSurveyData, resolveLibraryUseCase)

  private val data: StateFlow<DashboardData> =
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
          DashboardData(
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
      .stateIn(viewModelScope, SharingStarted.Eagerly, DashboardData())

  private val session = MutableStateFlow(DashboardSession())

  private val _events =
    MutableSharedFlow<DashboardEvent>(
      extraBufferCapacity = 64,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

  /** Outcomes of dashboard actions that the app shell applies outside this slice. */
  val events: Flow<DashboardEvent> = _events.asSharedFlow()

  val uiState: StateFlow<DashboardUiState> =
    combine(data, session, ::buildUiState)
      .stateIn(viewModelScope, SharingStarted.Eagerly, DashboardUiState())

  private fun buildUiState(data: DashboardData, session: DashboardSession): DashboardUiState {
    val mutations =
      if (data.activeSurveyId.isBlank()) {
        emptyList()
      } else {
        data.mutations.filter { it.surveyId == data.activeSurveyId }
      }
    val memberOrganizationIds =
      data.organizations.filter { it.isMember(data.profile.email) }.map { it.id }
    return DashboardUiState(
      exportOptions = exports.options(data),
      hasGoogleEarthForms = exports.hasGoogleEarthForms(data),
      surveys = data.surveys,
      activeSurveyId = data.activeSurveyId,
      organizations = data.organizations,
      signedInUserEmail = data.profile.email,
      // A personal survey (key "") and one in each organization the user may create surveys in.
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
      isTimeSeriesOpen = session.isTimeSeriesOpen,
      isTimeSeriesExpanded = session.isTimeSeriesExpanded,
      selectedLayerDatasetId = session.selectedLayerDatasetId,
      entityDetailsPane = session.entityDetailsPane,
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

  override fun recordCsvExport(datasetId: String) {
    val current = data.value
    recordExport(current, datasetId, profileId = null)
  }

  override fun exportGeoJson(datasetId: String): ExportFile? {
    val current = data.value
    val file = exports.geoJson(current, datasetId) ?: return null
    recordExport(current, datasetId, profileId = null)
    return file
  }

  override fun exportKml(
    datasetId: String,
    balloonAppUrl: String,
    serviceUrl: String,
  ): ExportFile? {
    val current = data.value
    val file = exports.kml(current, datasetId, balloonAppUrl, serviceUrl) ?: return null
    recordExport(current, datasetId, profileId = null)
    return file
  }

  override fun exportSurveyKml(balloonAppUrl: String, serviceUrl: String): ExportFile? {
    val current = data.value
    val file = exports.surveyKml(current, balloonAppUrl, serviceUrl) ?: return null
    viewModelScope.launch { exports.record(current.activeSurveyId, current.content.entities, null) }
    return file
  }

  override fun googleEarthOpenUrl(
    balloonAppUrl: String,
    serviceUrl: String,
  ): String? {
    val current = data.value
    val url = exports.googleEarthOpenUrl(current, balloonAppUrl, serviceUrl) ?: return null
    viewModelScope.launch { exports.record(current.activeSurveyId, current.content.entities, null) }
    return url
  }

  override fun exportWithProfile(datasetId: String, profileId: String): ExportFile? {
    val current = data.value
    val file = exports.withProfile(current, datasetId, profileId) ?: return null
    recordExport(current, datasetId, profileId)
    return file
  }

  override fun tableRepository(): EntityTableRepository? = entityTableRepository?.takeIf {
    it.isLazyLoadingEnabled
  }

  override suspend fun loadEntitiesForExport(datasetId: String): List<GeospatialEntityItem> {
    val current = data.value
    val repo = tableRepository() ?: return current.datasetEntities(datasetId)
    if (current.activeSurveyId.isBlank()) return current.datasetEntities(datasetId)
    return repo.fetchAllEntitiesForExport(current.activeSurveyId, datasetId)
  }

  override suspend fun exportGeoJsonAsync(datasetId: String): ExportFile? {
    val hydrated = hydratedForExport(datasetId)
    val file = exports.geoJson(hydrated, datasetId) ?: return null
    recordExport(hydrated, datasetId, profileId = null)
    return file
  }

  override suspend fun exportKmlAsync(
    datasetId: String,
    balloonAppUrl: String,
    serviceUrl: String,
  ): ExportFile? {
    val hydrated = hydratedForExport(datasetId)
    val file = exports.kml(hydrated, datasetId, balloonAppUrl, serviceUrl) ?: return null
    recordExport(hydrated, datasetId, profileId = null)
    return file
  }

  override suspend fun exportSurveyKmlAsync(
    balloonAppUrl: String,
    serviceUrl: String,
  ): ExportFile? {
    val hydrated = hydratedForExport(datasetId = "")
    val file = exports.surveyKml(hydrated, balloonAppUrl, serviceUrl) ?: return null
    viewModelScope.launch {
      exports.record(hydrated.activeSurveyId, hydrated.content.entities, null)
    }
    return file
  }

  override suspend fun exportWithProfileAsync(datasetId: String, profileId: String): ExportFile? {
    val hydrated = hydratedForExport(datasetId)
    val file = exports.withProfile(hydrated, datasetId, profileId) ?: return null
    recordExport(hydrated, datasetId, profileId)
    return file
  }

  private suspend fun hydratedForExport(datasetId: String): DashboardData {
    val current = data.value
    val repo = tableRepository() ?: return current
    if (current.activeSurveyId.isBlank()) return current
    val fetched = repo.fetchAllEntitiesForExport(current.activeSurveyId, datasetId)
    val merged =
      if (datasetId.isBlank()) {
        fetched
      } else {
        current.content.entities.filterNot { it.datasetId == datasetId } + fetched
      }
    return current.copy(content = current.content.copy(entities = merged))
  }

  /** Records the export in the background, with the snapshot the file was built from. */
  private fun recordExport(current: DashboardData, datasetId: String, profileId: String?) {
    val entities = current.datasetEntities(datasetId)
    viewModelScope.launch { exports.record(current.activeSurveyId, entities, profileId) }
  }

  /** Returns session state to its defaults (used by the workbench's Reset). */
  fun reset() {
    session.value = DashboardSession()
  }

  /**
   * Follows a map feature selection made on the map or in a list: clears the layer selection,
   * returns the details pane to the properties, switches the bottom table to the feature's dataset,
   * and shows the details panel.
   */
  fun onEntitySelected(entityId: String?) {
    val datasetId = entityId?.let { id ->
      data.value.content.entities.firstOrNull { it.id == id }?.datasetId
    }
    session.update { it.withEntitySelected(entityId, datasetId) }
  }

  /**
   * Follows a submission being opened for inspection: clears the layer selection, shows the
   * submissions pane (closing the submission returns to the feature's submissions list), switches
   * the bottom table to [tableDatasetId] when given, expands the right-hand panel, and docks the
   * bottom data table.
   */
  fun onSubmissionOpened(tableDatasetId: String? = null) {
    session.update { it.withSubmissionOpened(tableDatasetId) }
  }

  /**
   * Follows a data collection form opening in the right-hand panel: expands it, auto-collapses the
   * left-hand panel, and replaces the bottom data table with a collapsed Analyze imagery sheet.
   */
  fun onFormOpened() {
    session.update { it.withFormOpened() }
  }

  /**
   * Follows the active data collection form closing or completing: restores the normal bottom data
   * table and restores the left-hand panel if it was open before starting data collection.
   */
  fun onFormClosed() {
    session.update { it.withFormClosed() }
  }

  // --- Surveys (web) ---

  /** Ignores surveys that aren't in the store (a stale link or a survey removed meanwhile). */
  override fun openSurveyOnWeb(surveyId: String) {
    if (data.value.surveys.none { it.id == surveyId }) return
    viewModelScope.launch { surveyRepository.setActiveSurveyId(surveyId) }
    session.update { it.withSurveyOpened() }
    _events.tryEmit(DashboardEvent.SurveyActivated(surveyId))
  }

  override fun createSurvey(
    title: String,
    organizationId: String?,
    purposeIds: List<String>,
    programIds: List<String>,
  ): String {
    val current = data.value
    // The survey (and its ID) is built now so the caller can open it; storing it runs after.
    val survey =
      createSurveyUseCase.newSurvey(
        title = title,
        existingSurveys = current.surveys,
        organization =
          organizationId?.let { id -> current.organizations.firstOrNull { it.id == id } },
        ownerEmail = current.profile.email,
      )
    viewModelScope.launch {
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
    session.update { it.withLayerSelected(null) }
  }

  // --- Left-hand side panel ---

  override fun toggleSidePanel() {
    session.update { it.withSidePanelExpanded(!it.isSidePanelExpanded) }
  }

  override fun expandSidePanel() = updateSidePanelExpanded(true)

  override fun collapseSidePanel() = updateSidePanelExpanded(false)

  override fun updateSidePanelExpanded(expanded: Boolean) {
    session.update { it.withSidePanelExpanded(expanded) }
  }

  override fun updateSidePanelWidth(widthDp: Float) {
    session.update { it.withSidePanelWidth(widthDp) }
  }

  // --- Right-hand details panel ---

  override fun toggleDetailsPanel() {
    session.update { it.withDetailsPanelExpanded(!it.isDetailsPanelExpanded) }
  }

  override fun expandDetailsPanel() = updateDetailsPanelExpanded(true)

  override fun collapseDetailsPanel() = updateDetailsPanelExpanded(false)

  override fun updateDetailsPanelExpanded(expanded: Boolean) {
    session.update { it.withDetailsPanelExpanded(expanded) }
  }

  // --- Bottom data table & time series sheet ---

  override fun updateDashboardTableExpanded(expanded: Boolean) {
    session.update { it.withTableExpanded(expanded) }
  }

  override fun toggleDashboardTableExpanded() =
    updateDashboardTableExpanded(!session.value.isDashboardTableExpanded)

  override fun selectDashboardTable(datasetId: String) {
    session.update { it.copy(dashboardTableDatasetId = datasetId) }
  }

  override fun showTimeSeries() {
    session.update { it.withTimeSeriesOpen(true) }
  }

  override fun closeTimeSeries() {
    session.update { it.withTimeSeriesOpen(false) }
  }

  override fun updateTimeSeriesExpanded(expanded: Boolean) {
    session.update { it.withTimeSeriesExpanded(expanded) }
  }

  override fun toggleTimeSeriesExpanded() {
    session.update { it.withTimeSeriesExpanded(!it.isTimeSeriesExpanded) }
  }

  override fun showSelectedLayerInTable() {
    val datasetId = session.value.selectedLayerDatasetId ?: return
    showDatasetInTable(datasetId)
  }

  override fun showDatasetInTable(datasetId: String) {
    selectDashboardTable(datasetId)
    updateDashboardTableExpanded(expanded = true)
  }

  // --- Layer selection & entity details pane ---

  override fun selectLayer(datasetId: String?) {
    session.update { it.withLayerSelected(datasetId) }
  }

  override fun showEntitySubmissions() = selectEntityDetailsTab(EntityDetailsPane.SUBMISSIONS)

  override fun showEntityProperties() = selectEntityDetailsTab(EntityDetailsPane.PROPERTIES)

  override fun selectEntityDetailsTab(pane: EntityDetailsPane) {
    session.update { it.copy(entityDetailsPane = pane) }
  }

  // --- Searchable list ---

  override fun toggleListDatasetCollapsed(datasetId: String) {
    session.update { it.withListDatasetCollapsedToggled(datasetId) }
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
    session.update { it.withUploadStatusFilterToggled(filter) }
  }

  override fun clearUploadsEntityFilter() {
    session.update { it.copy(uploadsEntityFilterId = null) }
  }

  /** The use case answers with a notice only when there is something to tell the user. */
  override fun syncMutationNow(mutationId: String) {
    viewModelScope.launch {
      val notice = syncMutationsUseCase.syncSingleMutation(mutationId) ?: return@launch
      _events.tryEmit(DashboardEvent.Notice(notice))
    }
  }

  override fun syncAllOutboxMutations() {
    val surveyId = data.value.activeSurveyId
    viewModelScope.launch {
      val notice = syncMutationsUseCase.syncAllOutboxMutations(surveyId) ?: return@launch
      _events.tryEmit(DashboardEvent.Notice(notice))
    }
  }
}

/** Everything the dashboard reads from the local data store, as one snapshot. */
internal data class DashboardData(
  val surveys: List<SurveyPreviewItem> = emptyList(),
  val activeSurveyId: String = "",
  val content: SurveyContent = SurveyContent(),
  val organizations: List<Organization> = emptyList(),
  val profile: AuthProfile = AuthProfile("", "", ""),
  val mutations: List<MutationLogItem> = emptyList(),
  val libraries: Map<String, OrganizationLibrary> = emptyMap(),
  val pendingActivityRecordCount: Int = 0,
  /** The active survey's editor draft; exports need it, so without one none are offered. */
  val draft: SurveyEditorDraft? = null,
) {
  fun datasetEntities(datasetId: String): List<GeospatialEntityItem> =
    content.entities.filter { it.datasetId == datasetId }
}
