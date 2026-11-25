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
package org.openforis.collectone.v2.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openforis.collectone.v2.app.domain.model.BasemapType
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.ImagerySource
import org.openforis.collectone.v2.app.domain.model.LocationLockState
import org.openforis.collectone.v2.app.domain.model.MapLayerItem
import org.openforis.collectone.v2.app.domain.model.NavigationTargetKind
import org.openforis.collectone.v2.app.domain.model.OfflineImageryType
import org.openforis.collectone.v2.app.domain.model.OfflineRegionMode
import org.openforis.collectone.v2.app.domain.model.StraightLineVector
import org.openforis.collectone.v2.app.domain.model.SubmissionFieldEntry
import org.openforis.collectone.v2.app.domain.model.SubmissionGeometryPolygon
import org.openforis.collectone.v2.app.domain.model.SurveyPlaceItem
import org.openforis.collectone.v2.app.domain.repository.ConnectivityRepository
import org.openforis.collectone.v2.app.domain.repository.LocationRepository
import org.openforis.collectone.v2.app.domain.repository.OfflineMapRepository
import org.openforis.collectone.v2.app.domain.repository.OrganizationRepository
import org.openforis.collectone.v2.app.domain.repository.OverlayTileUrlRepository
import org.openforis.collectone.v2.app.domain.repository.PlaceRepository
import org.openforis.collectone.v2.app.domain.repository.SettingsRepository
import org.openforis.collectone.v2.app.domain.repository.SurveyRepository
import org.openforis.collectone.v2.app.domain.usecase.ClusterMapFeaturesUseCase
import org.openforis.collectone.v2.app.domain.usecase.ComputeWayfindingNavigationUseCase
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_map_navigation_standalone_field_log
import org.openforis.collectone.v2.app.resources.app_map_navigation_started
import org.openforis.collectone.v2.app.resources.app_map_navigation_started_submission
import org.openforis.collectone.v2.app.ui.text.UiText
import org.openforis.collectone.v2.app.ui.text.coordinatesText
import org.openforis.collectone.v2.app.ui.text.nameText
import org.openforis.collectone.v2.app.ui.text.toUiText
import org.openforis.collectone.v2.app.ui.text.wayfindingBadgeText
import org.openforis.collectone.v2.app.ui.viewmodel.ViewModelDispatcher
import org.openforis.collectone.v2.app.ui.viewmodel.viewModelScopeOn
import org.openforis.collectone.v2.map.CameraPosition
import org.openforis.collectone.v2.map.LatLng as MapLatLng
import org.openforis.collectone.v2.map.LngLatBounds

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

  fun toggleImagerySource(sourceId: String)

  /** Shows or hides the saved layers of imagery [type] (mobile layers sheet). */
  fun toggleDownloadedImagery(type: OfflineImageryType)

  /** Records that the map framed [SurveyMapUiState.framedBounds], so it isn't framed again. */
  fun clearFramedBounds()

  /** Toggles a survey map layer; hiding the selected feature's layer clears the selection. */
  fun toggleLayerVisibility(layerId: String)

  // --- GPS ---

  /** Moves the simulated GPS location; the camera follows it unless the map was panned. */
  fun updateUserGpsLocation(
    newNormalizedX: Float,
    newNormalizedY: Float,
    coordinatesLabel: String? = null,
  )

  /** Updates the user's real device GPS location; the camera follows it unless panned. */
  fun updateDeviceLocation(latitude: Double, longitude: Double, accuracyMeters: Double?) {}

  // --- Selection ---

  /** Selects a map feature on the map (collapsed bottom sheet), or clears it when `null`. */
  fun selectEntity(entityId: String?)

  fun updateEntityBottomSheetExpanded(expanded: Boolean)

  fun toggleEntityBottomSheetExpanded()

  /**
   * Selects cluster balloon [clusterId] and zooms into it right away (see [zoomIntoCluster]),
   * emitting [SurveyMapEvent.ClusterSelected]; `null` or a cluster that no longer exists clears the
   * selection.
   */
  fun selectCluster(clusterId: String?)

  /**
   * Centers [clusterId] and zooms to where it splits. The cluster stays selected only if it still
   * exists with the same features at the new zoom; an unknown cluster just zooms in one step.
   */
  fun zoomIntoCluster(clusterId: String)

  /** Centers the map on [place] (from Place search) and shows its marker. */
  fun selectPlace(place: SurveyPlaceItem)

  fun clearSelectedPlace()

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
 * wayfinding by [ComputeWayfindingNavigationUseCase]. Map areas saved for offline use come from
 * [OfflineMapRepository] (READY layers only, so download progress never redraws the map). Outcomes
 * that reach beyond the map (list selection, drawer, notices) are published as [SurveyMapEvent]s
 * for the app shell.
 *
 * The state is split by concern: [SurveyMapData] (the store snapshot, see [observeSurveyMapData]),
 * [SurveyMapSession] (session state and its pure transitions), [SurveyMapUiStateBuilder] (deriving
 * [uiState]), [SurveyMapWayfinding] (distances and bearings), and [OverlayTileUrlRefresher] (Earth
 * Engine tile URLs). This class maps user intents onto them and emits [events].
 *
 * Actions read the latest snapshots synchronously, so they must run on the main thread like the
 * rest of the UI; the default [dispatcher] is immediate, so a session change reaches [uiState]
 * before the action returns.
 */
class SurveyMapViewModel(
  private val surveyRepository: SurveyRepository,
  organizationRepository: OrganizationRepository,
  settingsRepository: SettingsRepository,
  private val locationRepository: LocationRepository,
  placeRepository: PlaceRepository,
  dispatcher: CoroutineDispatcher = ViewModelDispatcher,
  private val connectivityRepository: ConnectivityRepository,
  private val clusterMapFeatures: ClusterMapFeaturesUseCase,
  computeWayfindingNavigation: ComputeWayfindingNavigationUseCase,
  overlayTileUrls: OverlayTileUrlRepository? = null,
  offlineMapRepository: OfflineMapRepository? = null,
) : ViewModel(viewModelScopeOn(dispatcher)), SurveyMapActions, WayfindingBadges {
  private val uiStateBuilder =
    SurveyMapUiStateBuilder(
      clusterMapFeatures = clusterMapFeatures,
      computeWayfindingNavigation = computeWayfindingNavigation,
      canSaveMapAreas =
        offlineMapRepository != null &&
          offlineMapRepository.regionMode != OfflineRegionMode.UNAVAILABLE,
    )

  private val wayfinding = SurveyMapWayfinding(computeWayfindingNavigation)

  private val data: StateFlow<SurveyMapData> =
    observeSurveyMapData(
        surveyRepository = surveyRepository,
        organizationRepository = organizationRepository,
        settingsRepository = settingsRepository,
        placeRepository = placeRepository,
        connectivityRepository = connectivityRepository,
        offlineMapRepository = offlineMapRepository,
      )
      .stateIn(viewModelScope, SharingStarted.Eagerly, SurveyMapData())

  private val session =
    MutableStateFlow(SurveyMapSession(location = locationRepository.getLocationSnapshot()))

  // Events are fire-and-forget notices for the shell; when nobody collects (e.g. between sessions)
  // the oldest are dropped rather than suspending an action.
  private val _events =
    MutableSharedFlow<SurveyMapEvent>(
      extraBufferCapacity = 64,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

  /** Outcomes of map actions that the app shell applies outside the map slice. */
  val events: Flow<SurveyMapEvent> = _events.asSharedFlow()

  val uiState: StateFlow<SurveyMapUiState> =
    combine(data, session, uiStateBuilder::build)
      .stateIn(viewModelScope, SharingStarted.Eagerly, SurveyMapUiState())

  init {
    if (overlayTileUrls != null) {
      OverlayTileUrlRefresher(viewModelScope, overlayTileUrls, session).start(data)
    }
  }

  /**
   * Resolves a [SurveyPlaceItem] by [placeId] among the selected or navigated-to place and the
   * survey's local places.
   */
  fun findPlaceById(placeId: String): SurveyPlaceItem? =
    findPlaceById(placeId, data.value, session.value)

  /** Returns session state to its defaults and the device location to its default reading. */
  fun reset() {
    connectivityRepository.reset()
    session.value = SurveyMapSession(location = locationRepository.resetToDefaults())
  }

  // --- Camera ---

  override fun panMap(deltaNormalizedX: Float, deltaNormalizedY: Float) {
    if (deltaNormalizedX == 0f && deltaNormalizedY == 0f) return
    session.update { it.pannedBy(deltaNormalizedX, deltaNormalizedY) }
  }

  override fun recenterMapOnUser() {
    session.update { it.recentered() }
  }

  override fun recenterMapOnEntity(entity: GeospatialEntityItem, targetScreenY: Float) {
    val (lng, lat) = resolveEntityLngLat(entity)
    val (nx, ny) = data.value.anchor.toNormalized(MapLatLng(lat, lng))
    session.update { it.pannedTo(nx.toFloat(), ny.toFloat(), targetScreenY) }
  }

  override fun recenterMapOnEntity(entityId: String, targetScreenY: Float) {
    val entity = data.value.entities.firstOrNull { it.id == entityId } ?: return
    recenterMapOnEntity(entity, targetScreenY)
  }

  /**
   * The geographic coordinates `(lng, lat)` of [entity]: where its name is drawn
   * ([EntityGeometry.labelPosition]), or its survey's center for a record without geometry.
   */
  fun resolveEntityLngLat(entity: GeospatialEntityItem): Pair<Double, Double> {
    val position = entity.geometry?.let(EntityGeometry::labelPosition) ?: data.value.anchor.center
    return position.longitude to position.latitude
  }

  override fun desiredMapCamera(): CameraPosition = session.value.desiredCamera(data.value.anchor)

  override fun syncMapCamera(camera: CameraPosition) {
    val anchor = data.value.anchor
    session.update { it.syncedTo(camera, anchor) }
  }

  override fun syncMapZoomDelta(zoomDelta: Float) {
    if (zoomDelta.isNaN()) return
    session.update { it.zoomedTo(zoomDelta, SurveyMapSession.SYNC_ZOOM_RANGE) }
  }

  override fun zoomMapBy(deltaZoom: Float) {
    if (deltaZoom == 0f) return
    session.update { it.zoomedTo(it.mapZoomDelta + deltaZoom, SurveyMapSession.STEP_ZOOM_RANGE) }
  }

  override fun zoomInMap() = zoomMapBy(ZOOM_STEP)

  override fun zoomOutMap() = zoomMapBy(-ZOOM_STEP)

  override fun resetMapZoom() {
    session.update { it.copy(mapZoomDelta = 0f, selectedClusterId = null) }
  }

  // --- Basemap, imagery & layers ---

  override fun updateLayersSheetOpen(open: Boolean) {
    session.update { it.copy(isLayersSheetOpen = open) }
  }

  override fun selectBasemapType(type: BasemapType) {
    session.update { it.copy(selectedBasemapType = type) }
  }

  override fun toggleBasemapType() {
    selectBasemapType(
      if (session.value.selectedBasemapType == BasemapType.SATELLITE) BasemapType.NORMAL
      else BasemapType.SATELLITE
    )
  }

  override fun toggleDownloadedImagery(type: OfflineImageryType) {
    session.update {
      val hidden = it.hiddenDownloadedImagery
      it.copy(hiddenDownloadedImagery = if (type in hidden) hidden - type else hidden + type)
    }
  }

  /**
   * Turns the area selection mode on or off: while on, the map outlines every saved area and the
   * layers sheet closes.
   */
  fun setOfflineAreaSelectionActive(active: Boolean) {
    session.update {
      it.copy(
        isSelectingOfflineArea = active,
        isLayersSheetOpen = if (active) false else it.isLayersSheetOpen,
      )
    }
  }

  /** Asks the map to frame [bounds] (e.g. a saved area shown from Offline maps). */
  fun frameBounds(bounds: LngLatBounds) {
    session.update {
      it.copy(
        framedBounds = bounds,
        framingEpoch = it.framingEpoch + 1,
        isCameraFollowingUser = false,
        locationLockState = LocationLockState.PANNED,
      )
    }
  }

  override fun clearFramedBounds() {
    session.update { it.copy(framedBounds = null) }
  }

  /**
   * Shows [overlay] (an XYZ tile source, e.g. a historical scene from the web dashboard's time
   * series) above every other imagery layer, replacing the previous one; `null` removes it.
   */
  fun showPinnedScene(overlay: ImagerySource?) {
    session.update { it.copy(pinnedSceneOverlay = overlay) }
  }

  override fun toggleImagerySource(sourceId: String) {
    session.update {
      val ids = it.enabledImagerySourceIds
      it.copy(enabledImagerySourceIds = if (sourceId in ids) ids - sourceId else ids + sourceId)
    }
  }

  override fun toggleLayerVisibility(layerId: String) {
    val current = data.value
    val layers = current.mapLayers
    val layer = layers.firstOrNull { it.id == layerId } ?: return
    val nextVisible = !layer.isVisible
    val selectedEntity =
      session.value.selectedEntityId?.let { id ->
        current.entities.firstOrNull { it.id == id && it.layerId in current.visibleLayerIds }
      }
    if (!nextVisible && selectedEntity?.layerId == layerId) {
      session.update { it.copy(selectedEntityId = null, isEntityBottomSheetExpanded = false) }
      _events.tryEmit(SurveyMapEvent.SelectionCleared)
    }
    storeMapLayers(layers.map { if (it.id == layerId) it.copy(isVisible = nextVisible) else it })
  }

  /**
   * Writes the active survey's [layers] to the local data store; [uiState] picks the change up from
   * the store, so layer visibility has a single source of truth.
   */
  private fun storeMapLayers(layers: List<MapLayerItem>) {
    viewModelScope.launch { surveyRepository.setMapLayers(layers) }
  }

  /** Makes the layer of map feature [entity] visible, so a navigation target is on the map. */
  private fun showLayerOf(entity: GeospatialEntityItem?) {
    storeMapLayers(
      data.value.mapLayers.map { layer ->
        if (entity != null && layer.id == entity.layerId) layer.copy(isVisible = true) else layer
      }
    )
  }

  // --- GPS ---

  override fun updateUserGpsLocation(
    newNormalizedX: Float,
    newNormalizedY: Float,
    coordinatesLabel: String?,
  ) {
    // The simulated location stays inside the survey's viewport, away from its edges.
    val clampedX = newNormalizedX.coerceIn(GPS_MIN, GPS_MAX)
    val clampedY = newNormalizedY.coerceIn(GPS_MIN, GPS_MAX)
    val label = coordinatesLabel ?: session.value.location.coordinatesLabel
    locationRepository.updateGpsLocation(clampedX, clampedY, label)
    session.update { it.movedTo(clampedX, clampedY, label) }
  }

  override fun updateDeviceLocation(latitude: Double, longitude: Double, accuracyMeters: Double?) {
    val (nx, ny) = data.value.anchor.toNormalized(MapLatLng(latitude, longitude))
    val nxF = nx.toFloat()
    val nyF = ny.toFloat()
    val label = formatDeviceCoordinatesLabel(latitude, longitude, accuracyMeters)
    locationRepository.updateDeviceLocation(nxF, nyF, latitude, longitude, accuracyMeters, label)
    session.update { it.movedTo(nxF, nyF, label, accuracyMeters, latitude, longitude) }
  }

  private fun formatDeviceCoordinatesLabel(
    latitude: Double,
    longitude: Double,
    accuracyMeters: Double?,
  ): String {
    val latRounded = kotlin.math.round(latitude * 10_000.0) / 10_000.0
    val lonRounded = kotlin.math.round(longitude * 10_000.0) / 10_000.0
    val accSuffix =
      accuracyMeters
        ?.takeIf { it.isFinite() && it >= 0.0 }
        ?.let { " (±${kotlin.math.round(it * 10.0) / 10.0}m GPS)" }
        .orEmpty()
    return "$latRounded°, $lonRounded°$accSuffix"
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
    val target =
      uiStateBuilder.clustersFor(data.value, session.value).firstOrNull { it.id == clusterId }
    if (target != null) {
      _events.tryEmit(SurveyMapEvent.ClusterSelected(target))
      zoomIntoCluster(clusterId)
    } else {
      session.update { it.copy(selectedClusterId = null) }
    }
  }

  override fun zoomIntoCluster(clusterId: String) {
    val current = data.value
    val before = session.value
    val clusters = uiStateBuilder.clustersFor(current, before)
    val target = clusters.firstOrNull { it.id == clusterId }
    if (target == null) {
      session.value =
        before
          .zoomedTo(before.mapZoomDelta + ZOOM_STEP, SurveyMapSession.STEP_ZOOM_RANGE)
          .copy(selectedClusterId = null)
      return
    }
    val visibleCount = clusters.sumOf { it.totalCount }
    val targetZoom =
      clusterMapFeatures.expansionZoomDelta(
        cluster = target,
        currentZoomDelta = before.mapZoomDelta,
        featureCount = visibleCount,
      )
    val next =
      before
        .pannedTo(target.normalizedX, target.normalizedY)
        .zoomedTo(targetZoom, SurveyMapSession.STEP_ZOOM_RANGE)
    val refreshed =
      uiStateBuilder.clustersFor(current, next).firstOrNull {
        it.id == target.id && it.totalCount == target.totalCount
      }
    session.value = next.copy(selectedClusterId = refreshed?.id, selectedEntityId = null)
  }

  override fun selectPlace(place: SurveyPlaceItem) {
    val resolved = place.resolvedForSelection()
    val anchor = data.value.anchor
    val (placeNx, placeNy) = anchor.toNormalized(resolved.position)
    session.update {
      it.copy(
        lastSelectedPlace = resolved,
        selectedPlaceId = resolved.id,
        selectedEntityId = null,
        isCameraFollowingUser = false,
        locationLockState = LocationLockState.PANNED,
        mapPanOffsetX = (it.userGpsNormalizedX - placeNx).toFloat(),
        mapPanOffsetY = (it.userGpsNormalizedY - placeNy).toFloat(),
        mapZoomDelta =
          (resolved.targetZoom - anchor.zoom.toFloat()).coerceIn(SurveyMapSession.PLACE_ZOOM_RANGE),
        isEntityBottomSheetExpanded = false,
        isLayersSheetOpen = false,
      )
    }
    _events.tryEmit(SurveyMapEvent.PlaceSelected(resolved))
  }

  override fun clearSelectedPlace() {
    session.update { it.withoutPlace() }
  }

  // --- Straight-line wayfinding ---

  /** The straight-line vector from the user's GPS position to a normalized map point. */
  fun computeStraightLineVector(targetNormalizedX: Float, targetNormalizedY: Float) =
    wayfinding.vectorTo(data.value, session.value, targetNormalizedX, targetNormalizedY)

  /** Normalized target coordinates and optional geometry polygon of [submissionId]. */
  fun resolveSubmissionTargetGeometry(
    submissionId: String
  ): Triple<Float, Float, SubmissionGeometryPolygon?>? =
    wayfinding.submissionTargetGeometry(data.value, submissionId)

  override fun distanceAndBearingToEntity(entityId: String): StraightLineVector? =
    wayfinding.toEntity(data.value, session.value, entityId)

  fun distanceAndBearingToSubmission(submissionId: String): StraightLineVector? =
    wayfinding.toSubmission(data.value, session.value, submissionId)

  override fun distanceAndBearingToPlace(place: SurveyPlaceItem): StraightLineVector =
    wayfinding.toPlace(data.value, session.value, place)

  /** True if [field] in [submissionId] is a geometry question. */
  fun isSubmissionFieldGeometry(submissionId: String, field: SubmissionFieldEntry): Boolean =
    wayfinding.isGeometryField(data.value, submissionId, field)

  override fun distanceAndBearingToSubmissionField(
    submissionId: String,
    field: SubmissionFieldEntry,
  ): StraightLineVector? =
    wayfinding.toSubmissionField(data.value, session.value, submissionId, field)

  override fun startNavigationToEntity(entityId: String) {
    val entity = data.value.entities.firstOrNull { it.id == entityId } ?: return
    showLayerOf(entity)
    session.update {
      it.navigatingTo(NavigationTargetKind.ENTITY, entity.id, selectedEntityId = entity.id)
    }
    val badge = wayfindingNoticeBadge(distanceAndBearingToEntity(entity.id))
    _events.tryEmit(
      SurveyMapEvent.NavigationStarted(
        submissionId = null,
        notice = Res.string.app_map_navigation_started.toUiText(entity.label, badge),
      )
    )
  }

  /**
   * Navigates to [submissionId] and selects its map feature (a standalone submission selects none).
   * The notice names the feature, or the submission's coordinates, or calls it a standalone field
   * log.
   */
  override fun startNavigationToSubmission(submissionId: String) {
    val current = data.value
    val sub = current.allSubmissions.firstOrNull { it.id == submissionId } ?: return
    val parentEntity = current.entities.firstOrNull { it.id == sub.entityId }
    showLayerOf(parentEntity)
    session.update {
      it.navigatingTo(NavigationTargetKind.SUBMISSION, sub.id, selectedEntityId = parentEntity?.id)
    }
    val badge = wayfindingNoticeBadge(distanceAndBearingToSubmission(sub.id))
    // Feature labels may carry a " • detail" suffix that doesn't belong in the notice.
    val context: UiText =
      parentEntity?.label?.substringBefore(" •")?.let(UiText::Plain)
        ?: sub.location?.let(::coordinatesText)
        ?: Res.string.app_map_navigation_standalone_field_log.toUiText()
    _events.tryEmit(
      SurveyMapEvent.NavigationStarted(
        submissionId = sub.id,
        notice =
          Res.string.app_map_navigation_started_submission.toUiText(sub.formTitle, context, badge),
      )
    )
  }

  override fun startNavigationToPlace(place: SurveyPlaceItem) {
    session.update {
      it.navigatingTo(NavigationTargetKind.PLACE, place.id, selectedEntityId = null, place = place)
    }
    val badge = wayfindingNoticeBadge(distanceAndBearingToPlace(place))
    _events.tryEmit(
      SurveyMapEvent.NavigationStarted(
        submissionId = null,
        notice = Res.string.app_map_navigation_started.toUiText(place.nameText(), badge),
      )
    )
  }

  override fun toggleNavigationToEntity(entityId: String) {
    if (session.value.isNavigatingTo(NavigationTargetKind.ENTITY, entityId)) stopNavigation()
    else startNavigationToEntity(entityId)
  }

  override fun toggleNavigationToSubmission(submissionId: String) {
    if (session.value.isNavigatingTo(NavigationTargetKind.SUBMISSION, submissionId)) {
      stopNavigation()
    } else {
      startNavigationToSubmission(submissionId)
    }
  }

  override fun stopNavigation() {
    session.update {
      it.copy(navigationTargetKind = null, navigationTargetId = null, navigationTargetPlace = null)
    }
    _events.tryEmit(SurveyMapEvent.NavigationStopped)
  }

  /**
   * Moves the simulated GPS location [stepFraction] (clamped to 10–100%) of the way to the
   * navigation target, snapping onto it once within [ARRIVAL_METERS]. Does nothing when not
   * navigating.
   */
  override fun stepUserTowardNavigationTarget(stepFraction: Float) {
    val s = session.value
    val nav = uiStateBuilder.activeNavigation(data.value, s) ?: return
    val targetX = nav.vector.toNormalizedX
    val targetY = nav.vector.toNormalizedY
    val fraction = stepFraction.coerceIn(0.10f, 1.0f)
    val arrived = nav.vector.distanceMeters <= ARRIVAL_METERS
    val nextX =
      if (arrived) targetX else s.userGpsNormalizedX + (targetX - s.userGpsNormalizedX) * fraction
    val nextY =
      if (arrived) targetY else s.userGpsNormalizedY + (targetY - s.userGpsNormalizedY) * fraction
    updateUserGpsLocation(nextX, nextY)
  }

  private companion object {
    /** Zoom change of one zoom button press (and of tapping an unknown cluster). */
    const val ZOOM_STEP = 0.75f

    /** Bounds of the simulated GPS location in normalized map space. */
    const val GPS_MIN = 0.10f
    const val GPS_MAX = 0.90f

    /** Distance from the target under which a navigation step lands on it. */
    const val ARRIVAL_METERS = 18
  }
}

/** The distance and bearing a navigation notice shows, or nothing without a [vector]. */
private fun wayfindingNoticeBadge(vector: StraightLineVector?): UiText =
  vector?.let(::wayfindingBadgeText) ?: UiText.Plain("")
