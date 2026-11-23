/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.app.ui.listsearch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openforis.collectone.v2.app.domain.model.EntityDatasetFeaturesGroup
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.SurveyMapAnchor
import org.openforis.collectone.v2.app.domain.model.SurveyPlaceItem
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.configuredDatasetStatusOptions
import org.openforis.collectone.v2.app.domain.model.datasetStatusFilterOptions
import org.openforis.collectone.v2.app.domain.model.entityDatasetLayersIn
import org.openforis.collectone.v2.app.domain.model.hasGeometry
import org.openforis.collectone.v2.app.domain.model.usesWorkflowStatus
import org.openforis.collectone.v2.app.domain.repository.ConnectivityRepository
import org.openforis.collectone.v2.app.domain.repository.PlaceRepository
import org.openforis.collectone.v2.app.domain.repository.SurveyContent
import org.openforis.collectone.v2.app.domain.repository.SurveyRepository
import org.openforis.collectone.v2.app.domain.usecase.SearchPlacesUseCase
import org.openforis.collectone.v2.app.ui.viewmodel.ViewModelDispatcher
import org.openforis.collectone.v2.app.ui.viewmodel.saveInto
import org.openforis.collectone.v2.app.ui.viewmodel.viewModelScopeOn

/** Intents of the searchable list's search field and per-dataset status filters. */
interface ListSearchActions {
  /** Searches the map features and places for [query]; places are looked up live while online. */
  fun updateQuery(query: String)

  /** Clears the query, listing every entity matching any active dataset status filters again. */
  fun clearQuery()

  /**
   * Filters entities in [datasetId] by [status], or shows all statuses in [datasetId] when `null`
   * or blank.
   */
  fun updateStatusFilter(datasetId: String, status: String?)

  /**
   * Toggles filtering entities in [datasetId] by [status] (selecting it, or clearing it if already
   * active on [datasetId]).
   */
  fun toggleStatusFilter(datasetId: String, status: String)

  /**
   * Clears the active status filter on [datasetId], or clears status filters across every dataset
   * when [datasetId] is `null`.
   */
  fun clearStatusFilter(datasetId: String? = null)
}

/**
 * ViewModel of the searchable list's search (the mobile Main Survey's list and the web dashboard's
 * left-hand panel): the query and status filter, kept across Android process death, the active
 * survey's map features they match, and the places [query] matches in the survey's gazetteer and
 * the live geocoder. Going offline drops the geocoder's results; coming back online searches the
 * current query again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListSearchViewModel(
  surveyRepository: SurveyRepository,
  private val placeRepository: PlaceRepository,
  private val connectivityRepository: ConnectivityRepository,
  private val searchPlaces: SearchPlacesUseCase,
  /**
   * Whether the client ignores the device's connectivity and is always online (the web console):
   * then places are searched even while the device reports being offline.
   */
  private val isAlwaysOnline: Boolean = false,
  dispatcher: CoroutineDispatcher = ViewModelDispatcher,
  /** Keeps the query and status filter across Android process death. */
  savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel(viewModelScopeOn(dispatcher)), ListSearchActions {
  /** Everything the search reads from the local data store. */
  private data class Data(
    val surveys: List<SurveyPreviewItem> = emptyList(),
    val activeSurveyId: String = "",
    val content: SurveyContent = SurveyContent(),
    val localPlaces: List<SurveyPlaceItem> = emptyList(),
    val isOnline: Boolean = true,
  ) {
    /** The active survey, or the first one when none is active. */
    val activeSurvey: SurveyPreviewItem?
      get() = surveys.firstOrNull { it.id == activeSurveyId } ?: surveys.firstOrNull()

    /** The longitude and latitude the active survey's map centers on. */
    val center: Pair<Double, Double>
      get() = SurveyMapAnchor.forSurvey(activeSurveyId).center.let { it.longitude to it.latitude }
  }

  /** Session (non-persisted, except the query and per-dataset status filters) state. */
  private data class Session(
    val query: String = "",
    val selectedStatuses: Map<String, String> = emptyMap(),
    val isSearchingPlaces: Boolean = false,
    val geocoderPlaces: List<SurveyPlaceItem> = emptyList(),
  )

  private val data: StateFlow<Data> =
    combine(
        surveyRepository.observeSurveys(),
        surveyRepository.observeActiveSurveyId().flatMapLatest { id ->
          surveyRepository.observeSurveyContent(id).map { id to it }
        },
        placeRepository.observeLocalPlaces(),
        connectivityRepository.observeIsOnline(),
      ) { surveys, (activeId, content), places, isOnline ->
        Data(
          surveys = surveys,
          activeSurveyId = activeId,
          content = content,
          localPlaces = places,
          isOnline = isAlwaysOnline || isOnline,
        )
      }
      .stateIn(viewModelScope, SharingStarted.Eagerly, Data())

  private val session =
    MutableStateFlow(
      Session(
        query = savedState[QUERY_KEY] ?: "",
        selectedStatuses = decodeStatusFilters(savedState.get<String?>(STATUS_FILTERS_KEY)),
      )
    )

  // Built on every change rather than with `combine`, because the geocoder delivers its results on
  // another thread: each change publishes a state built from the latest data and session, so the
  // caller reads its own change right away.
  private val _uiState = MutableStateFlow(buildUiState(data.value, session.value))

  val uiState: StateFlow<ListSearchUiState> = _uiState.asStateFlow()

  init {
    saveInto(savedState, QUERY_KEY, session) { it.query }
    saveInto(savedState, STATUS_FILTERS_KEY, session) { encodeStatusFilters(it.selectedStatuses) }
    var previousSurveyId = data.value.activeSurveyId
    viewModelScope.launch {
      data.collect { latest ->
        if (
          previousSurveyId.isNotEmpty() &&
            latest.activeSurveyId.isNotEmpty() &&
            latest.activeSurveyId != previousSurveyId
        ) {
          session.update { it.copy(selectedStatuses = emptyMap()) }
        }
        previousSurveyId = latest.activeSurveyId
        publish()
      }
    }
    if (!isAlwaysOnline) {
      viewModelScope.launch {
        // Skip the initial connectivity snapshot; only transitions after launch clear or retry
        // remote place lookups.
        connectivityRepository.observeIsOnline().drop(1).collect(::onConnectivityChanged)
      }
    }
  }

  private fun buildUiState(data: Data, session: Session): ListSearchUiState {
    val content = data.content
    val datasetLayers = content.entities.entityDatasetLayersIn(content.mapLayers)
    val effectiveStatuses = LinkedHashMap<String, String>()
    val matchedIds = HashSet<String>()
    val entityGroups = mutableListOf<EntityDatasetFeaturesGroup>()

    content.entities
      .groupBy { it.datasetId.ifBlank { it.datasetName } }
      .forEach { (datasetKey, allDatasetEntities) ->
        val first = allDatasetEntities.first()
        val datasetId = first.datasetId.ifBlank { datasetKey }
        val configured =
          configuredDatasetStatusOptions(
            datasetId = datasetId,
            forms = content.forms,
            config = content.config,
          )
        val rawSelected = session.selectedStatuses[datasetId]
        val statusOptions =
          if (
            rawSelected != null &&
              (configured.isNotEmpty() || allDatasetEntities.usesWorkflowStatus)
          ) {
            allDatasetEntities.datasetStatusFilterOptions(
              selectedStatus = rawSelected,
              configuredOptions = configured,
            )
          } else {
            allDatasetEntities.datasetStatusFilterOptions(
              selectedStatus = null,
              configuredOptions = configured,
            )
          }
        val effectiveSelected = rawSelected?.takeIf { statusOptions.isNotEmpty() }
        if (effectiveSelected != null) {
          effectiveStatuses[datasetId] = effectiveSelected
        }
        val matched = matchingEntities(allDatasetEntities, session.query, effectiveSelected)
        matched.forEach { matchedIds += it.id }
        if (matched.isNotEmpty() || effectiveSelected != null) {
          val layer = datasetLayers.firstOrNull { it.id == first.layerId }
          entityGroups +=
            EntityDatasetFeaturesGroup(
              layer = layer,
              datasetName = first.datasetName,
              entities = matched,
              datasetId = datasetId,
              isMapLayer = layer != null || allDatasetEntities.any { it.hasGeometry },
              statusFilterOptions = statusOptions,
              selectedStatusFilter = effectiveSelected,
            )
        }
      }

    val entities = content.entities.filter { it.id in matchedIds }
    return ListSearchUiState(
      query = session.query,
      selectedStatuses = effectiveStatuses,
      isOnline = data.isOnline,
      isSearchingPlaces = session.isSearchingPlaces,
      geocoderPlaces = session.geocoderPlaces,
      entities = entities,
      entityGroups = entityGroups,
      places = matchingPlaces(data, session),
    )
  }

  private fun matchingEntities(
    entities: List<GeospatialEntityItem>,
    query: String,
    selectedStatus: String?,
  ): List<GeospatialEntityItem> {
    val statusFiltered =
      if (selectedStatus.isNullOrBlank()) {
        entities
      } else {
        entities.filter { it.matchesStatusFilter(selectedStatus) }
      }
    val q = query.trim()
    if (q.isEmpty()) return statusFiltered
    return statusFiltered.filter { entity ->
      entity.label.contains(q, ignoreCase = true) ||
        entity.geoId.contains(q, ignoreCase = true) ||
        entity.datasetName.contains(q, ignoreCase = true) ||
        // Matches the English status name (e.g. "synced"), as before the labels were localized.
        entity.syncStatus.name.contains(q, ignoreCase = true) ||
        entity.properties.values.any { it.contains(q, ignoreCase = true) }
    }
  }

  private fun matchingPlaces(data: Data, session: Session): List<SurveyPlaceItem> {
    val (longitude, latitude) = data.center
    return searchPlaces(
      query = session.query,
      isOnline = data.isOnline,
      // A live geocoder already searches the survey's region; the gazetteer stands in for it.
      localPlaces = if (placeRepository.hasLiveGeocoder) emptyList() else data.localPlaces,
      remoteApiPlaces = session.geocoderPlaces,
      surveyBaseLng = longitude,
      surveyBaseLat = latitude,
      surveyLocationLabel = data.activeSurvey?.location.orEmpty(),
    )
  }

  override fun updateQuery(query: String) {
    updateSession { it.copy(query = query) }
    searchGeocoder(query.trim())
  }

  override fun clearQuery() {
    updateSession { it.copy(query = "") }
    clearGeocoderResults()
  }

  override fun updateStatusFilter(datasetId: String, status: String?) {
    val key = datasetId.trim()
    if (key.isEmpty()) return
    val resolved = resolveStatusKey(key, status)
    updateSession { current ->
      val next = LinkedHashMap(current.selectedStatuses)
      if (resolved == null) next.remove(key) else next[key] = resolved
      current.copy(selectedStatuses = next)
    }
  }

  override fun toggleStatusFilter(datasetId: String, status: String) {
    val key = datasetId.trim()
    if (key.isEmpty()) return
    val resolved = resolveStatusKey(key, status) ?: return
    updateSession { current ->
      val next = LinkedHashMap(current.selectedStatuses)
      if (next[key]?.equals(resolved, ignoreCase = true) == true) {
        next.remove(key)
      } else {
        next[key] = resolved
      }
      current.copy(selectedStatuses = next)
    }
  }

  override fun clearStatusFilter(datasetId: String?) {
    updateSession { current ->
      val next =
        if (datasetId == null) {
          emptyMap()
        } else {
          current.selectedStatuses - datasetId.trim()
        }
      current.copy(selectedStatuses = next)
    }
  }

  /** Clears any active status filters when switching surveys. */
  fun onSurveyActivated() {
    clearStatusFilter()
  }

  private fun resolveStatusKey(datasetId: String, status: String?): String? {
    val trimmed = status?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    val content = data.value.content
    val datasetEntities =
      content.entities.filter { it.datasetId.ifBlank { it.datasetName } == datasetId }
    val configured =
      configuredDatasetStatusOptions(
        datasetId = datasetId,
        forms = content.forms,
        config = content.config,
      )
    val matchedOption =
      datasetEntities
        .datasetStatusFilterOptions(selectedStatus = null, configuredOptions = configured)
        .firstOrNull { option ->
          option.status.equals(trimmed, ignoreCase = true) ||
            option.badgeText.equals(trimmed, ignoreCase = true)
        }
    return matchedOption?.status ?: trimmed
  }

  /** Looks [query] up with the live geocoder while online, or drops the previous results. */
  private fun searchGeocoder(query: String) {
    val data = data.value
    if (!data.isOnline || query.isEmpty()) {
      clearGeocoderResults()
      return
    }
    updateSession { it.copy(isSearchingPlaces = true) }
    val (longitude, latitude) = data.center
    placeRepository.searchRemotePlaces(
      surveyId = data.activeSurveyId,
      query = query,
      defaultRegionSubtitle = data.activeSurvey?.location.orEmpty(),
      centerLongitude = longitude,
      centerLatitude = latitude,
    ) { results ->
      onPlacesSearchResults(query, results)
    }
  }

  /**
   * Shows the geocoder's [results] for [query], unless the query changed since or the device went
   * offline. May be invoked from a background callback thread, so state updates are atomic.
   */
  fun onPlacesSearchResults(query: String, results: List<SurveyPlaceItem>) {
    updateSession {
      val isCurrent = data.value.isOnline && it.query.trim() == query.trim()
      it.copy(
        isSearchingPlaces = false,
        geocoderPlaces = if (isCurrent) results else it.geocoderPlaces,
      )
    }
  }

  private fun clearGeocoderResults() {
    updateSession { it.copy(isSearchingPlaces = false, geocoderPlaces = emptyList()) }
  }

  private fun updateSession(transform: (Session) -> Session) {
    session.update(transform)
    publish()
  }

  /** Publishes the state of the latest data and session, retrying if another thread published. */
  private fun publish() {
    _uiState.update { buildUiState(data.value, session.value) }
  }

  /** Going offline drops the geocoder's results; coming back online searches again. */
  private fun onConnectivityChanged(isOnline: Boolean) {
    if (isOnline) searchGeocoder(session.value.query.trim()) else clearGeocoderResults()
  }

  /**
   * Resets session search state (used by the workbench's Reset); [SavedStateHandle] mirrors the
   * cleared query and status filter automatically.
   */
  fun reset() {
    updateSession { Session() }
  }

  private companion object {
    const val QUERY_KEY = "listSearchQuery"
    const val STATUS_FILTERS_KEY = "listSearchStatusFilters"

    fun encodeStatusFilters(filters: Map<String, String>): String? =
      filters.entries
        .filter { it.key.isNotBlank() && it.value.isNotBlank() }
        .joinToString("\n") { (datasetId, status) -> "$datasetId\t$status" }
        .takeIf { it.isNotEmpty() }

    fun decodeStatusFilters(encoded: String?): Map<String, String> {
      if (encoded.isNullOrBlank()) return emptyMap()
      val result = LinkedHashMap<String, String>()
      encoded.lineSequence().forEach { line ->
        val sep = line.indexOf('\t')
        if (sep > 0 && sep < line.lastIndex) {
          val datasetId = line.substring(0, sep).trim()
          val status = line.substring(sep + 1).trim()
          if (datasetId.isNotEmpty() && status.isNotEmpty()) {
            result[datasetId] = status
          }
        }
      }
      return result
    }
  }
}
