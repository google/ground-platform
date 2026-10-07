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

import kotlin.math.abs
import kotlin.math.hypot
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.groundplatform.v2.devtools.prototypeapp.domain.model.BasemapType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.GeospatialEntityItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LayerSourceType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LocationLockState
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapFeatureCluster
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapLayerItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MeasurementUnitSystem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.NavigationTargetKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OfflineBasemapStyle
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.StraightLineVector
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionFieldEntry
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionGeometryPolygon
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyMapAnchor
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPlaceItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.LatLng
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.withEditorLayerStyles
import org.groundplatform.v2.devtools.prototypeapp.domain.model.toClusterFeatures
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.DeviceLocationSnapshot
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.LocationRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.OrganizationRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.PlaceRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SettingsRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyContent
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ClusterMapFeaturesUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ComputeWayfindingNavigationUseCase
import org.groundplatform.v2.devtools.prototypeapp.ui.state.SurveyMapEvent
import org.groundplatform.v2.devtools.prototypeapp.ui.state.SurveyMapUiState
import org.groundplatform.v2.map.CameraPosition
import org.groundplatform.v2.map.LatLng as MapLatLng

/** User intents of the survey map viewport. Implemented by [SurveyMapViewModel]. */
interface SurveyMapActions {
  // --- Camera ---

  /** Pans the map by normalized deltas; dragging disengages GPS camera following. */
  fun panMap(deltaNormalizedX: Float, deltaNormalizedY: Float)

  /** Recenters the camera on the user's GPS location and re-enables following. */
  fun recenterMapOnUser()

  /**
   * Recenters the camera on [entity], placing it at normalized screen height [targetScreenY] (to
   * account for a bottom sheet or table covering part of the viewport).
   */
  fun recenterMapOnEntity(entity: GeospatialEntityItem, targetScreenY: Float = 0.50f)

  fun recenterMapOnEntity(entityId: String, targetScreenY: Float = 0.50f)

  /** The camera the map should show for the current GPS position, pan offset, and zoom. */
  fun desiredMapCamera(): CameraPosition

  /** Records where the map camera settled after a gesture or an explicit camera move. */
  fun syncMapCamera(camera: CameraPosition)

  /** Records a zoom the map camera applied itself (e.g. fitting a selected feature). */
  fun syncMapZoomDelta(zoomDelta: Float)

  fun zoomMapBy(deltaZoom: Float)

  fun zoomInMap()

  fun zoomOutMap()

  fun resetMapZoom()

  // --- Basemap, imagery & layers ---

  fun updateLayersSheetOpen(open: Boolean)

  fun selectBasemapType(type: BasemapType)

  fun toggleBasemapType()

  fun toggleOfflineBasemapVisibility()

  fun updateOfflineBasemapStyle(style: OfflineBasemapStyle)

  fun toggleImagerySource(sourceId: String)

  /** Toggles a survey map layer; hiding the selected feature's layer clears the selection. */
  fun toggleLayerVisibility(layerId: String)

  // --- GPS ---

  /** Moves the simulated GPS location; the camera follows it unless the map was panned. */
  fun updateUserGpsLocation(
    newNormalizedX: Float,
    newNormalizedY: Float,
    coordinatesLabel: String? = null,
  )

  // --- Selection ---

  /** Selects a map feature on the map (collapsed bottom sheet), or clears it when `null`. */
  fun selectEntity(entityId: String?)

  fun updateEntityBottomSheetExpanded(expanded: Boolean)

  fun toggleEntityBottomSheetExpanded()

  /** Selects a cluster balloon (clears when `null`); tapping it again zooms into it. */
  fun selectCluster(clusterId: String?)

  fun zoomIntoCluster(clusterId: String)

  /** Centers the map on [place] (from Place search) and shows its marker. */
  fun selectPlace(place: SurveyPlaceItem)

  fun clearSelectedPlace()

  /** Simulates device Airplane mode, which disables online Places search. */
  fun updateAirplaneMode(enabled: Boolean)

  // --- Straight-line wayfinding ---

  fun startNavigationToEntity(entityId: String)

  fun startNavigationToSubmission(submissionId: String)

  fun startNavigationToPlace(place: SurveyPlaceItem)

  fun toggleNavigationToEntity(entityId: String)

  fun toggleNavigationToSubmission(submissionId: String)

  fun stopNavigation()

  /** Advances the simulated GPS location along the navigation line by [stepFraction]. */
  fun stepUserTowardNavigationTarget(stepFraction: Float = 0.40f)
}

/**
 * ViewModel of the survey map viewport: camera and GPS following, basemap and organization imagery,
 * zoomed-out clustering, straight-line wayfinding, place search, and the selected map feature,
 * cluster, or place.
 *
 * Survey content and organizations come from [SurveyRepository] and [OrganizationRepository]; the
 * device location is written through [LocationRepository]; places are searched through
 * [PlaceRepository]. Clustering and the scale bar are modelled by [ClusterMapFeaturesUseCase], and
 * wayfinding by [ComputeWayfindingNavigationUseCase]. Outcomes that reach beyond the map (list
 * selection, drawer, notices) are published as [SurveyMapEvent]s for the app shell.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SurveyMapViewModel(
  private val surveyRepository: SurveyRepository,
  organizationRepository: OrganizationRepository,
  settingsRepository: SettingsRepository,
  private val locationRepository: LocationRepository,
  private val placeRepository: PlaceRepository,
  private val scope: CoroutineScope,
  private val clusterMapFeatures: ClusterMapFeaturesUseCase = ClusterMapFeaturesUseCase(),
  private val computeWayfindingNavigation: ComputeWayfindingNavigationUseCase =
    ComputeWayfindingNavigationUseCase(),
) : SurveyMapActions {
  /** Everything the map reads from the local data store. */
  private data class Data(
    val surveys: List<SurveyPreviewItem> = emptyList(),
    val activeSurveyId: String = "",
    val content: SurveyContent = SurveyContent(),
    val organizations: List<Organization> = emptyList(),
    val unitSystem: MeasurementUnitSystem = MeasurementUnitSystem.METRIC,
    val localPlaces: List<SurveyPlaceItem> = emptyList(),
  ) {
    val anchor: SurveyMapAnchor
      get() = SurveyMapAnchor.forSurvey(activeSurveyId)

    val activeSurvey: SurveyPreviewItem?
      get() = surveys.firstOrNull { it.id == activeSurveyId } ?: surveys.firstOrNull()

    val entities: List<GeospatialEntityItem>
      get() = content.entities

    val mapLayers: List<MapLayerItem>
      get() = content.mapLayers.withEditorLayerStyles(content.editorDraft?.datasets.orEmpty())

    val visibleLayerIds: Set<String>
      get() = content.mapLayers.filter { it.isVisible }.map { it.id }.toSet()

    val allSubmissions: List<SubmissionPreviewItem>
      get() = entities.flatMap { it.submissions } + content.standaloneSubmissions

    val submissionGeometries: List<SubmissionGeometryPolygon>
      get() = content.submissionGeometries
  }

  /** Session (non-persisted) state of the map viewport. */
  private data class Session(
    val selectedBasemapType: BasemapType = BasemapType.SATELLITE,
    val isOfflineBasemapVisible: Boolean = true,
    val offlineBasemapStyle: OfflineBasemapStyle = OfflineBasemapStyle.SATELLITE_HYBRID,
    val enabledImagerySourceIds: Set<String> = emptySet(),
    val isLayersSheetOpen: Boolean = false,
    val location: DeviceLocationSnapshot = DeviceLocationSnapshot(),
    val isCameraFollowingUser: Boolean = true,
    val locationLockState: LocationLockState = LocationLockState.LOCKED,
    val mapPanOffsetX: Float = 0f,
    val mapPanOffsetY: Float = 0f,
    val mapZoomDelta: Float = 0f,
    val selectedEntityId: String? = null,
    val entitySelectionEpoch: Long = 0L,
    val isEntityBottomSheetExpanded: Boolean = false,
    val selectedClusterId: String? = null,
    val selectedPlaceId: String? = null,
    val lastSelectedPlace: SurveyPlaceItem? = null,
    val isAirplaneMode: Boolean = false,
    val navigationTargetKind: NavigationTargetKind? = null,
    val navigationTargetId: String? = null,
    /** Snapshot of the place being navigated to, so it resolves after search results change. */
    val navigationTargetPlace: SurveyPlaceItem? = null,
  ) {
    val userGpsNormalizedX: Float
      get() = location.normalizedX

    val userGpsNormalizedY: Float
      get() = location.normalizedY

    /** Camera panned away from the user, placing world point ([nx], [ny]) at the screen center. */
    fun pannedTo(nx: Float, ny: Float, targetScreenY: Float = 0.50f): Session =
      copy(
        isCameraFollowingUser = false,
        locationLockState = LocationLockState.PANNED,
        mapPanOffsetX = (userGpsNormalizedX - nx).coerceIn(-MAX_PAN, MAX_PAN),
        mapPanOffsetY =
          ((userGpsNormalizedY - ny) + (targetScreenY - 0.50f)).coerceIn(-MAX_PAN, MAX_PAN),
      )

    /** Camera following the user again, at the screen center. */
    fun recentered(): Session =
      copy(
        isCameraFollowingUser = true,
        locationLockState = LocationLockState.LOCKED,
        mapPanOffsetX = 0f,
        mapPanOffsetY = 0f,
      )

    /** Zoom delta set to [zoomDelta] (clamped to [range]); leaving clustering drops the cluster. */
    fun zoomedTo(zoomDelta: Float, range: ClosedFloatingPointRange<Float>): Session {
      val next = zoomDelta.coerceIn(range)
      val clustering = next <= ClusterMapFeaturesUseCase.CLUSTERING_ZOOM_DELTA
      return copy(
        mapZoomDelta = next,
        selectedClusterId = if (clustering) selectedClusterId else null,
      )
    }

    fun withoutPlace(): Session = copy(selectedPlaceId = null, lastSelectedPlace = null)
  }

  private val data: StateFlow<Data> =
    combine(
        surveyRepository.observeSurveys(),
        surveyRepository.observeActiveSurveyId().flatMapLatest { id ->
          surveyRepository.observeSurveyContent(id).map { id to it }
        },
        organizationRepository.observeOrganizations(),
        settingsRepository.observeUserSettings(),
        placeRepository.observeLocalPlaces(),
      ) { surveys, (activeId, content), organizations, settings, places ->
        Data(
          surveys = surveys,
          activeSurveyId = activeId,
          content = content,
          organizations = organizations,
          unitSystem = settings.measurementUnits,
          localPlaces = places,
        )
      }
      .stateIn(scope, SharingStarted.Eagerly, Data())

  private val session =
    MutableStateFlow(Session(location = locationRepository.getLocationSnapshot()))

  private val _events =
    MutableSharedFlow<SurveyMapEvent>(
      extraBufferCapacity = 64,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

  /** Outcomes of map actions that the app shell applies outside the map slice. */
  val events: Flow<SurveyMapEvent> = _events.asSharedFlow()

  val uiState: StateFlow<SurveyMapUiState> =
    combine(data, session, ::buildUiState)
      .stateIn(scope, SharingStarted.Eagerly, SurveyMapUiState())

  private var cachedClustersKey: String = ""
  private var cachedClustersResult: List<MapFeatureCluster> = emptyList()

  private fun buildUiState(data: Data, session: Session): SurveyMapUiState {
    val organizations = data.organizations
    val allUsersOrganization =
      organizations.firstOrNull { it.id == Organization.ALL_USERS_ID }
        ?: organizations.firstOrNull { it.isSynthetic }
    val surveyOrganization =
      data.activeSurvey
        ?.organizationId
        ?.let { id -> organizations.firstOrNull { it.id == id } }
        ?.takeIf { !it.isSynthetic && it.id != Organization.ALL_USERS_ID }
    val mapLayers = data.mapLayers
    val visibleLayerIds = data.visibleLayerIds
    val entities = data.entities
    val visibleEntities = entities.filter { it.layerId in visibleLayerIds }
    val visibleDatasetLayers =
      if (entities.isEmpty()) {
        emptyList()
      } else {
        mapLayers.filter { layer ->
          layer.isVisible &&
            layer.sourceType == LayerSourceType.ENTITY_DATASET &&
            entities.any { it.layerId == layer.id }
        }
      }
    return SurveyMapUiState(
      activeSurveyId = data.activeSurveyId,
      anchor = data.anchor,
      surveyLocationLabel = data.activeSurvey?.location.orEmpty(),
      activeSurveyOrganizationName = surveyOrganization?.name,
      mapLayers = mapLayers,
      entities = entities,
      visibleMapEntities = visibleEntities,
      mapFeatureClusters = clustersFor(data, session),
      activeEntitiesCountNoun =
        visibleDatasetLayers.singleOrNull()?.pluralDomainLabel?.lowercase() ?: "map features",
      selectedBasemapType = session.selectedBasemapType,
      isOfflineBasemapVisible = session.isOfflineBasemapVisible,
      offlineBasemapStyle = session.offlineBasemapStyle,
      allUsersImagerySources = allUsersOrganization?.imagerySources.orEmpty(),
      activeSurveyOrganizationImagerySources = surveyOrganization?.imagerySources.orEmpty(),
      enabledImagerySourceIds = session.enabledImagerySourceIds,
      isLayersSheetOpen = session.isLayersSheetOpen,
      userGpsNormalizedX = session.userGpsNormalizedX,
      userGpsNormalizedY = session.userGpsNormalizedY,
      userGpsCoordinatesLabel = session.location.coordinatesLabel,
      gnssSatelliteCount = session.location.gnssSatelliteCount,
      gnssAccuracyMeters = session.location.gnssAccuracyMeters,
      unitSystem = data.unitSystem,
      isCameraFollowingUser = session.isCameraFollowingUser,
      locationLockState = session.locationLockState,
      mapPanOffsetX = session.mapPanOffsetX,
      mapPanOffsetY = session.mapPanOffsetY,
      mapZoomDelta = session.mapZoomDelta,
      mapScaleBarSpec =
        clusterMapFeatures.computeScaleBarSpec(data.activeSurveyId, session.mapZoomDelta),
      selectedEntityId = session.selectedEntityId,
      entitySelectionEpoch = session.entitySelectionEpoch,
      isEntityBottomSheetExpanded = session.isEntityBottomSheetExpanded,
      selectedClusterId = session.selectedClusterId,
      selectedPlace = session.selectedPlaceId?.let { findPlaceById(it, data, session) },
      isAirplaneMode = session.isAirplaneMode,
      navigationTargetKind = session.navigationTargetKind,
      navigationTargetId = session.navigationTargetId,
      activeNavigation = activeNavigation(data, session),
    )
  }

  /** Clusters of the visible map features at the session's zoom, memoized on their inputs. */
  private fun clustersFor(data: Data, session: Session): List<MapFeatureCluster> {
    if (!clusterMapFeatures.isClusteringActive(session.mapZoomDelta)) return emptyList()
    val key =
      "${data.entities.hashCode()}:${data.visibleLayerIds.hashCode()}:${session.mapZoomDelta}"
    if (key == cachedClustersKey) return cachedClustersResult
    val visibleLayerIds = data.visibleLayerIds
    val computed =
      clusterMapFeatures(
        features = data.entities.filter { it.layerId in visibleLayerIds }.toClusterFeatures(),
        radiusNormalized = clusterMapFeatures.clusterRadiusNormalized(session.mapZoomDelta),
      )
    cachedClustersKey = key
    cachedClustersResult = computed
    return computed
  }

  private fun activeNavigation(data: Data, session: Session) =
    computeWayfindingNavigation.resolveActiveNavigationState(
      navigationTargetKind = session.navigationTargetKind,
      navigationTargetId = session.navigationTargetId,
      fromNormalizedX = session.userGpsNormalizedX,
      fromNormalizedY = session.userGpsNormalizedY,
      unitSystem = data.unitSystem,
      userGpsCoordinatesLabel = session.location.coordinatesLabel,
      entities = data.entities,
      allSubmissions = data.allSubmissions,
      submissionGeometries = data.submissionGeometries,
      findPlaceById = { findPlaceById(it, data, session) },
    )

  private fun findPlaceById(placeId: String, data: Data, session: Session): SurveyPlaceItem? =
    session.lastSelectedPlace?.takeIf { it.id == placeId }
      ?: session.navigationTargetPlace?.takeIf { it.id == placeId }
      ?: data.localPlaces.firstOrNull { it.id == placeId }

  /**
   * Resolves a [SurveyPlaceItem] by [placeId] among the selected or navigated-to place and the
   * survey's local places.
   */
  fun findPlaceById(placeId: String): SurveyPlaceItem? =
    findPlaceById(placeId, data.value, session.value)

  /** Returns session state to its defaults and the device location to its default reading. */
  fun reset() {
    session.value = Session(location = locationRepository.resetToDefaults())
  }

  // --- Camera ---

  override fun panMap(deltaNormalizedX: Float, deltaNormalizedY: Float) {
    if (deltaNormalizedX == 0f && deltaNormalizedY == 0f) return
    session.update {
      it.copy(
        isCameraFollowingUser = false,
        locationLockState = LocationLockState.PANNED,
        mapPanOffsetX = (it.mapPanOffsetX + deltaNormalizedX).coerceIn(-MAX_PAN, MAX_PAN),
        mapPanOffsetY = (it.mapPanOffsetY + deltaNormalizedY).coerceIn(-MAX_PAN, MAX_PAN),
      )
    }
  }

  override fun recenterMapOnUser() {
    session.update { it.recentered() }
  }

  override fun recenterMapOnEntity(entity: GeospatialEntityItem, targetScreenY: Float) {
    session.update { it.pannedTo(entity.normalizedX, entity.normalizedY, targetScreenY) }
  }

  override fun recenterMapOnEntity(entityId: String, targetScreenY: Float) {
    val entity = data.value.entities.firstOrNull { it.id == entityId } ?: return
    recenterMapOnEntity(entity, targetScreenY)
  }

  /** Resolves the geographic coordinates `(lng, lat)` of [entity] in the active survey. */
  fun resolveEntityLngLat(entity: GeospatialEntityItem): Pair<Double, Double> {
    val position =
      data.value.anchor.toLatLng(entity.normalizedX.toDouble(), entity.normalizedY.toDouble())
    return position.longitude to position.latitude
  }

  override fun desiredMapCamera(): CameraPosition {
    val anchor = data.value.anchor
    val s = session.value
    val center =
      anchor.toLatLng(
        (s.userGpsNormalizedX - s.mapPanOffsetX).toDouble(),
        (s.userGpsNormalizedY - s.mapPanOffsetY).toDouble(),
      )
    return CameraPosition(
      center =
        MapLatLng(
          center.latitude.coerceIn(-MAX_MAP_LATITUDE, MAX_MAP_LATITUDE),
          ((center.longitude + 540) % 360) - 180,
        ),
      zoom = anchor.zoom + s.mapZoomDelta,
    )
  }

  override fun syncMapCamera(camera: CameraPosition) {
    val anchor = data.value.anchor
    val (nx, ny) = anchor.toNormalized(camera.center)
    session.update { s ->
      val panX = (s.userGpsNormalizedX - nx).toFloat().coerceIn(-MAX_PAN, MAX_PAN)
      val panY = (s.userGpsNormalizedY - ny).toFloat().coerceIn(-MAX_PAN, MAX_PAN)
      val moved =
        abs(panX - s.mapPanOffsetX) > MAP_SYNC_TOLERANCE ||
          abs(panY - s.mapPanOffsetY) > MAP_SYNC_TOLERANCE
      val panned =
        if (moved) {
          s.copy(
            isCameraFollowingUser = false,
            locationLockState = LocationLockState.PANNED,
            mapPanOffsetX = panX,
            mapPanOffsetY = panY,
          )
        } else {
          s
        }
      val zoomDelta = (camera.zoom - anchor.zoom).toFloat()
      if (zoomDelta.isNaN()) panned else panned.zoomedTo(zoomDelta, SYNC_ZOOM_RANGE)
    }
  }

  override fun syncMapZoomDelta(zoomDelta: Float) {
    if (zoomDelta.isNaN()) return
    session.update { it.zoomedTo(zoomDelta, SYNC_ZOOM_RANGE) }
  }

  override fun zoomMapBy(deltaZoom: Float) {
    if (deltaZoom == 0f) return
    session.update { it.zoomedTo(it.mapZoomDelta + deltaZoom, STEP_ZOOM_RANGE) }
  }

  override fun zoomInMap() = zoomMapBy(0.75f)

  override fun zoomOutMap() = zoomMapBy(-0.75f)

  override fun resetMapZoom() {
    session.update { it.copy(mapZoomDelta = 0f, selectedClusterId = null) }
  }

  // --- Basemap, imagery & layers ---

  override fun updateLayersSheetOpen(open: Boolean) {
    session.update { it.copy(isLayersSheetOpen = open) }
  }

  override fun selectBasemapType(type: BasemapType) {
    session.update {
      it.copy(
        selectedBasemapType = type,
        offlineBasemapStyle =
          if (type == BasemapType.SATELLITE) OfflineBasemapStyle.SATELLITE_HYBRID
          else OfflineBasemapStyle.VECTOR_TOPO,
      )
    }
  }

  override fun toggleBasemapType() {
    selectBasemapType(
      if (session.value.selectedBasemapType == BasemapType.SATELLITE) BasemapType.NORMAL
      else BasemapType.SATELLITE
    )
  }

  override fun toggleOfflineBasemapVisibility() {
    session.update { it.copy(isOfflineBasemapVisible = !it.isOfflineBasemapVisible) }
  }

  override fun updateOfflineBasemapStyle(style: OfflineBasemapStyle) {
    session.update {
      it.copy(
        offlineBasemapStyle = style,
        selectedBasemapType =
          if (style == OfflineBasemapStyle.SATELLITE_HYBRID) BasemapType.SATELLITE
          else BasemapType.NORMAL,
        isOfflineBasemapVisible = true,
      )
    }
  }

  override fun toggleImagerySource(sourceId: String) {
    session.update {
      val ids = it.enabledImagerySourceIds
      it.copy(enabledImagerySourceIds = if (sourceId in ids) ids - sourceId else ids + sourceId)
    }
  }

  override fun toggleLayerVisibility(layerId: String) {
    val layers = data.value.mapLayers
    val layer = layers.firstOrNull { it.id == layerId } ?: return
    val nextVisible = !layer.isVisible
    val selectedEntity =
      session.value.selectedEntityId?.let { id ->
        data.value.entities.firstOrNull { it.id == id && it.layerId in data.value.visibleLayerIds }
      }
    if (!nextVisible && selectedEntity?.layerId == layerId) {
      session.update { it.copy(selectedEntityId = null, isEntityBottomSheetExpanded = false) }
      _events.tryEmit(SurveyMapEvent.SelectionCleared)
    }
    storeMapLayers(layers.map { if (it.id == layerId) it.copy(isVisible = nextVisible) else it })
  }

  /** Writes the active survey's [layers] to the local data store. */
  private fun storeMapLayers(layers: List<MapLayerItem>) {
    scope.launch { surveyRepository.setMapLayers(layers) }
  }

  // --- GPS ---

  override fun updateUserGpsLocation(
    newNormalizedX: Float,
    newNormalizedY: Float,
    coordinatesLabel: String?,
  ) {
    val clampedX = newNormalizedX.coerceIn(0.10f, 0.90f)
    val clampedY = newNormalizedY.coerceIn(0.10f, 0.90f)
    val label = coordinatesLabel ?: session.value.location.coordinatesLabel
    locationRepository.updateGpsLocation(clampedX, clampedY, label)
    session.update { s ->
      val dx = clampedX - s.userGpsNormalizedX
      val dy = clampedY - s.userGpsNormalizedY
      val moved =
        s.copy(
          location =
            s.location.copy(
              normalizedX = clampedX,
              normalizedY = clampedY,
              coordinatesLabel = label,
            )
        )
      if (s.isCameraFollowingUser) {
        moved
      } else {
        moved.copy(
          mapPanOffsetX = (s.mapPanOffsetX + dx).coerceIn(-MAX_PAN, MAX_PAN),
          mapPanOffsetY = (s.mapPanOffsetY + dy).coerceIn(-MAX_PAN, MAX_PAN),
        )
      }
    }
  }

  // --- Selection ---

  override fun selectEntity(entityId: String?) {
    session.update {
      it
        .withoutPlace()
        .copy(
          selectedEntityId = entityId,
          isEntityBottomSheetExpanded = false,
          isLayersSheetOpen = if (entityId != null) false else it.isLayersSheetOpen,
          entitySelectionEpoch =
            if (entityId != null) it.entitySelectionEpoch + 1 else it.entitySelectionEpoch,
        )
    }
    _events.tryEmit(SurveyMapEvent.EntitySelected(entityId))
  }

  /**
   * Shows [entityId] as the selected map feature without the side effects of [selectEntity], for
   * selections made from a list, a submission, or a form. Bumping the selection epoch makes the map
   * frame the feature again.
   */
  fun setSelectedEntity(entityId: String?, bumpSelectionEpoch: Boolean = false) {
    session.update {
      it.copy(
        selectedEntityId = entityId,
        entitySelectionEpoch =
          if (bumpSelectionEpoch) it.entitySelectionEpoch + 1 else it.entitySelectionEpoch,
      )
    }
  }

  override fun updateEntityBottomSheetExpanded(expanded: Boolean) {
    session.update { it.copy(isEntityBottomSheetExpanded = expanded) }
  }

  override fun toggleEntityBottomSheetExpanded() {
    session.update { it.copy(isEntityBottomSheetExpanded = !it.isEntityBottomSheetExpanded) }
  }

  override fun selectCluster(clusterId: String?) {
    if (clusterId == null) {
      session.update { it.copy(selectedClusterId = null) }
      return
    }
    if (session.value.selectedClusterId == clusterId) {
      zoomIntoCluster(clusterId)
      return
    }
    val target = clustersFor(data.value, session.value).firstOrNull { it.id == clusterId }
    session.update {
      it.copy(
        selectedClusterId = clusterId,
        selectedEntityId = if (target != null) null else it.selectedEntityId,
      )
    }
    if (target != null) _events.tryEmit(SurveyMapEvent.ClusterSelected(target))
  }

  override fun zoomIntoCluster(clusterId: String) {
    val current = data.value
    val before = session.value
    val target = clustersFor(current, before).firstOrNull { it.id == clusterId }
    var next = before
    if (target != null) next = next.pannedTo(target.normalizedX, target.normalizedY)
    next = next.zoomedTo(next.mapZoomDelta + 0.75f, STEP_ZOOM_RANGE)
    val refreshed =
      clustersFor(current, next).firstOrNull {
        target != null &&
          hypot(it.normalizedX - target.normalizedX, it.normalizedY - target.normalizedY) < 0.08f
      }
    session.value = next.copy(selectedClusterId = refreshed?.id)
  }

  override fun selectPlace(place: SurveyPlaceItem) {
    val resolved = place.resolvedForSelection()
    val anchor = data.value.anchor
    val (placeNx, placeNy) = anchor.toNormalized(MapLatLng(resolved.latitude, resolved.longitude))
    session.update {
      it.copy(
        lastSelectedPlace = resolved,
        selectedPlaceId = resolved.id,
        selectedEntityId = null,
        isCameraFollowingUser = false,
        locationLockState = LocationLockState.PANNED,
        mapPanOffsetX = (it.userGpsNormalizedX - placeNx).toFloat(),
        mapPanOffsetY = (it.userGpsNormalizedY - placeNy).toFloat(),
        mapZoomDelta = (resolved.targetZoom - anchor.zoom.toFloat()).coerceIn(-13.0f, 3.2f),
        isEntityBottomSheetExpanded = false,
        isLayersSheetOpen = false,
      )
    }
    _events.tryEmit(SurveyMapEvent.PlaceSelected(resolved))
  }

  override fun clearSelectedPlace() {
    session.update { it.withoutPlace() }
  }

  override fun updateAirplaneMode(enabled: Boolean) {
    session.update { it.copy(isAirplaneMode = enabled) }
  }

  /**
   * Looks up places matching [query] near [center] for [surveyId] through the place repository
   * (returning nothing while in Airplane mode), delivering them to [onResults].
   */
  fun searchPlaces(
    surveyId: String,
    query: String,
    regionSubtitle: String,
    center: LatLng,
    onResults: (List<SurveyPlaceItem>) -> Unit,
  ) {
    placeRepository.searchRemotePlaces(
      surveyId = surveyId,
      query = query,
      isAirplaneMode = session.value.isAirplaneMode,
      defaultRegionSubtitle = regionSubtitle,
      centerLongitude = center.lng,
      centerLatitude = center.lat,
      onResults = onResults,
    )
  }

  // --- Straight-line wayfinding ---

  /** The straight-line vector from the user's GPS position to a normalized map point. */
  fun computeStraightLineVector(targetNormalizedX: Float, targetNormalizedY: Float) =
    computeWayfindingNavigation(
      fromNormalizedX = session.value.userGpsNormalizedX,
      fromNormalizedY = session.value.userGpsNormalizedY,
      targetNormalizedX = targetNormalizedX,
      targetNormalizedY = targetNormalizedY,
      unitSystem = data.value.unitSystem,
    )

  /** Normalized target coordinates and optional geometry polygon of [submissionId]. */
  fun resolveSubmissionTargetGeometry(
    submissionId: String
  ): Triple<Float, Float, SubmissionGeometryPolygon?>? =
    computeWayfindingNavigation.resolveSubmissionTargetGeometry(
      submissionId = submissionId,
      allSubmissions = data.value.allSubmissions,
      submissionGeometries = data.value.submissionGeometries,
      entities = data.value.entities,
    )

  fun distanceAndBearingToEntity(entityId: String): StraightLineVector? {
    val entity = data.value.entities.firstOrNull { it.id == entityId } ?: return null
    return computeStraightLineVector(entity.normalizedX, entity.normalizedY)
  }

  fun distanceAndBearingToSubmission(submissionId: String): StraightLineVector? {
    val (tx, ty, _) = resolveSubmissionTargetGeometry(submissionId) ?: return null
    return computeStraightLineVector(tx, ty)
  }

  fun distanceAndBearingToPlace(place: SurveyPlaceItem): StraightLineVector =
    computeStraightLineVector(place.normalizedX, place.normalizedY)

  /** Formatted distance & bearing badge for [entityId] (e.g. `"495 m • 319° NW"`), or `""`. */
  fun formattedWayfindingBadgeForEntity(entityId: String): String =
    distanceAndBearingToEntity(entityId)?.formattedBadge.orEmpty()

  fun formattedWayfindingBadgeForSubmission(submissionId: String): String =
    distanceAndBearingToSubmission(submissionId)?.formattedBadge.orEmpty()

  fun formattedWayfindingBadgeForPlace(place: SurveyPlaceItem): String =
    distanceAndBearingToPlace(place).formattedBadge

  /** True if [field] in [submissionId] is a geometry question. */
  fun isSubmissionFieldGeometry(submissionId: String, field: SubmissionFieldEntry): Boolean =
    computeWayfindingNavigation.isSubmissionFieldGeometry(
      submissionId = submissionId,
      field = field,
      submissionGeometries = data.value.submissionGeometries,
    )

  /** Formatted distance & bearing badge for a geometry [field] of [submissionId], or `""`. */
  fun formattedWayfindingBadgeForSubmissionField(
    submissionId: String,
    field: SubmissionFieldEntry,
  ): String {
    if (!isSubmissionFieldGeometry(submissionId, field)) return ""
    val geom =
      data.value.submissionGeometries.firstOrNull {
        it.submissionId == submissionId &&
          (it.fieldPath == field.questionName || it.questionLabel == field.questionLabel)
      }
    val vector =
      if (geom != null) {
        computeStraightLineVector(geom.normalizedX, geom.normalizedY)
      } else {
        distanceAndBearingToSubmission(submissionId) ?: return ""
      }
    return vector.formattedBadge
  }

  override fun startNavigationToEntity(entityId: String) {
    val current = data.value
    val entity = current.entities.firstOrNull { it.id == entityId } ?: return
    storeMapLayers(
      current.mapLayers.map { layer ->
        if (layer.id == entity.layerId) layer.copy(isVisible = true) else layer
      }
    )
    session.update {
      it
        .recentered()
        .copy(
          navigationTargetKind = NavigationTargetKind.ENTITY,
          navigationTargetId = entity.id,
          navigationTargetPlace = null,
          selectedEntityId = entity.id,
          isEntityBottomSheetExpanded = false,
          isLayersSheetOpen = false,
        )
    }
    val badge = formattedWayfindingBadgeForEntity(entity.id)
    _events.tryEmit(
      SurveyMapEvent.NavigationStarted(
        submissionId = null,
        notice = "Straight-line navigation to ${entity.label} ($badge)",
      )
    )
  }

  override fun startNavigationToSubmission(submissionId: String) {
    val current = data.value
    val sub = current.allSubmissions.firstOrNull { it.id == submissionId } ?: return
    val parentEntity = current.entities.firstOrNull { it.id == sub.entityId }
    storeMapLayers(
      current.mapLayers.map { layer ->
        if (parentEntity != null && layer.id == parentEntity.layerId) {
          layer.copy(isVisible = true)
        } else {
          layer
        }
      }
    )
    session.update {
      it
        .recentered()
        .copy(
          navigationTargetKind = NavigationTargetKind.SUBMISSION,
          navigationTargetId = sub.id,
          navigationTargetPlace = null,
          selectedEntityId = parentEntity?.id,
          isEntityBottomSheetExpanded = false,
          isLayersSheetOpen = false,
        )
    }
    val badge = formattedWayfindingBadgeForSubmission(sub.id)
    val contextLabel =
      parentEntity?.label?.substringBefore(" •")
        ?: sub.coordinatesLabel.ifBlank { "Standalone Field Log" }
    _events.tryEmit(
      SurveyMapEvent.NavigationStarted(
        submissionId = sub.id,
        notice = "Straight-line navigation to ${sub.formTitle} • $contextLabel ($badge)",
      )
    )
  }

  override fun startNavigationToPlace(place: SurveyPlaceItem) {
    session.update {
      it
        .recentered()
        .copy(
          navigationTargetKind = NavigationTargetKind.PLACE,
          navigationTargetId = place.id,
          navigationTargetPlace = place,
          selectedPlaceId = place.id,
          lastSelectedPlace = place,
          selectedEntityId = null,
          isEntityBottomSheetExpanded = false,
          isLayersSheetOpen = false,
        )
    }
    val badge = formattedWayfindingBadgeForPlace(place)
    _events.tryEmit(
      SurveyMapEvent.NavigationStarted(
        submissionId = null,
        notice = "Straight-line navigation to ${place.name} ($badge)",
      )
    )
  }

  override fun toggleNavigationToEntity(entityId: String) {
    if (isNavigatingTo(NavigationTargetKind.ENTITY, entityId)) stopNavigation()
    else startNavigationToEntity(entityId)
  }

  override fun toggleNavigationToSubmission(submissionId: String) {
    if (isNavigatingTo(NavigationTargetKind.SUBMISSION, submissionId)) stopNavigation()
    else startNavigationToSubmission(submissionId)
  }

  private fun isNavigatingTo(kind: NavigationTargetKind, targetId: String): Boolean =
    session.value.navigationTargetKind == kind && session.value.navigationTargetId == targetId

  override fun stopNavigation() {
    session.update {
      it.copy(navigationTargetKind = null, navigationTargetId = null, navigationTargetPlace = null)
    }
    _events.tryEmit(SurveyMapEvent.NavigationStopped)
  }

  override fun stepUserTowardNavigationTarget(stepFraction: Float) {
    val nav = activeNavigation(data.value, session.value) ?: return
    val s = session.value
    val targetX = nav.vector.toNormalizedX
    val targetY = nav.vector.toNormalizedY
    val fraction = stepFraction.coerceIn(0.10f, 1.0f)
    val arrived = nav.vector.distanceMeters <= 18
    val nextX =
      if (arrived) targetX else s.userGpsNormalizedX + (targetX - s.userGpsNormalizedX) * fraction
    val nextY =
      if (arrived) targetY else s.userGpsNormalizedY + (targetY - s.userGpsNormalizedY) * fraction
    updateUserGpsLocation(nextX, nextY)
  }

  private companion object {
    /** Web Mercator's latitude limit, for camera centers. */
    const val MAX_MAP_LATITUDE = 85.0

    /** Pan offset change (normalized) below which a settled camera counts as not moved. */
    const val MAP_SYNC_TOLERANCE = 1e-4f

    /** Largest normalized pan offset in either direction. */
    const val MAX_PAN = 10000f

    /** Zoom deltas the map camera itself may settle at. */
    val SYNC_ZOOM_RANGE = -13.0f..3.7f

    /** Zoom deltas reachable with the zoom buttons. */
    val STEP_ZOOM_RANGE = -5.0f..3.7f
  }
}
