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

import org.openforis.collectone.v2.app.domain.model.LayerSourceType
import org.openforis.collectone.v2.app.domain.model.MapFeatureCluster
import org.openforis.collectone.v2.app.domain.model.OfflineImageryType
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.StraightLineNavigationState
import org.openforis.collectone.v2.app.domain.model.SurveyPlaceItem
import org.openforis.collectone.v2.app.domain.model.toClusterFeatures
import org.openforis.collectone.v2.app.domain.usecase.ClusterMapFeaturesUseCase
import org.openforis.collectone.v2.app.domain.usecase.ComputeWayfindingNavigationUseCase

/**
 * Derives [SurveyMapUiState] from the store snapshot ([SurveyMapData]) and the map's session state
 * ([SurveyMapSession]).
 *
 * Clustering is the expensive part, so [clustersFor] memoizes the last result on its inputs. The
 * memo is plain fields without synchronization: it is only touched from the ViewModel's
 * single-threaded dispatcher (the `uiState` combine and the actions that run on the main thread).
 *
 * @param canSaveMapAreas whether this device can save map areas; fixed for the ViewModel's life.
 */
internal class SurveyMapUiStateBuilder(
  private val clusterMapFeatures: ClusterMapFeaturesUseCase,
  private val computeWayfindingNavigation: ComputeWayfindingNavigationUseCase,
  private val canSaveMapAreas: Boolean,
) {
  private var cachedClustersKey: String = ""
  private var cachedClustersResult: List<MapFeatureCluster> = emptyList()

  fun build(data: SurveyMapData, session: SurveyMapSession): SurveyMapUiState {
    val organizations = data.organizations
    val allUsersOrganization =
      organizations.firstOrNull { it.id == Organization.ALL_USERS_ID }
        ?: organizations.firstOrNull { it.isSynthetic }
    // The survey's own organization; a survey shared with all users has none to show.
    val surveyOrganization =
      data.activeSurvey
        ?.organizationId
        ?.let { id -> organizations.firstOrNull { it.id == id } }
        ?.takeIf { !it.isAllUsers }
    val mapLayers = data.mapLayers
    val visibleLayerIds = data.visibleLayerIds
    val entities = data.entities
    return SurveyMapUiState(
      activeSurveyId = data.activeSurveyId,
      anchor = data.anchor,
      surveyLocationLabel = data.activeSurvey?.location.orEmpty(),
      activeSurveyOrganizationName = surveyOrganization?.name,
      mapLayers = mapLayers,
      entities = entities,
      visibleMapEntities = entities.filter { it.layerId in visibleLayerIds },
      mapFeatureClusters = clustersFor(data, session),
      activeEntitiesCountNoun = countNoun(data),
      selectedBasemapType = session.selectedBasemapType,
      allUsersImagerySources = allUsersOrganization?.imagerySources.orEmpty(),
      activeSurveyOrganizationImagerySources = surveyOrganization?.imagerySources.orEmpty(),
      enabledImagerySourceIds = session.enabledImagerySourceIds,
      resolvedOverlayTileUrls = session.resolvedOverlayTileUrls,
      pinnedSceneOverlay = session.pinnedSceneOverlay,
      isLayersSheetOpen = session.isLayersSheetOpen,
      canSaveMapAreas = canSaveMapAreas,
      isDownloadedSatelliteVisible =
        OfflineImageryType.SATELLITE !in session.hiddenDownloadedImagery,
      isDownloadedTerrainVisible = OfflineImageryType.TERRAIN !in session.hiddenDownloadedImagery,
      savedSatelliteAreas = data.savedImagery.satellite,
      savedTerrainAreas = data.savedImagery.terrain,
      isSelectingOfflineArea = session.isSelectingOfflineArea,
      framedBounds = session.framedBounds,
      framingEpoch = session.framingEpoch,
      userGpsNormalizedX = session.userGpsNormalizedX,
      userGpsNormalizedY = session.userGpsNormalizedY,
      userLatLng =
        session.location.let { loc ->
          val lat = loc.latitude
          val lon = loc.longitude
          if (lat != null && lon != null) {
            org.openforis.collectone.v2.map.LatLng(lat, lon)
          } else {
            null
          }
        },
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
      isOnline = data.isOnline,
      navigationTargetKind = session.navigationTargetKind,
      navigationTargetId = session.navigationTargetId,
      activeNavigation = activeNavigation(data, session),
    )
  }

  /**
   * The layer's own nouns (`"coffee parcels"`) when exactly one visible entity dataset layer has
   * map features and names them; otherwise the generic map feature nouns.
   */
  private fun countNoun(data: SurveyMapData): MapFeatureNoun {
    val entities = data.entities
    if (entities.isEmpty()) return MapFeatureNoun.Generic
    return data.mapLayers
      .filter { layer ->
        layer.isVisible &&
          layer.sourceType == LayerSourceType.ENTITY_DATASET &&
          entities.any { it.layerId == layer.id }
      }
      .singleOrNull()
      ?.takeIf { it.singularNoun.isNotBlank() && it.pluralNoun.isNotBlank() }
      ?.let { MapFeatureNoun.OfLayer(it.singularNoun, it.pluralNoun) } ?: MapFeatureNoun.Generic
  }

  /**
   * Clusters of the visible map features at the session's zoom (none while zoomed in past
   * clustering), memoized on the features, the visible layers, the anchor, and the zoom.
   */
  fun clustersFor(data: SurveyMapData, session: SurveyMapSession): List<MapFeatureCluster> {
    if (!clusterMapFeatures.isClusteringActive(session.mapZoomDelta)) return emptyList()
    val key =
      "${data.entities.hashCode()}:${data.visibleLayerIds.hashCode()}:${data.anchor.hashCode()}:" +
        "${session.mapZoomDelta}"
    if (key == cachedClustersKey) return cachedClustersResult
    val visibleLayerIds = data.visibleLayerIds
    val visibleFeatures =
      data.entities.filter { it.layerId in visibleLayerIds }.toClusterFeatures(data.anchor)
    val computed =
      clusterMapFeatures(
        features = visibleFeatures,
        radiusNormalized =
          clusterMapFeatures.clusterRadiusNormalized(session.mapZoomDelta, visibleFeatures.size),
      )
    cachedClustersKey = key
    cachedClustersResult = computed
    return computed
  }

  /** The navigation line to the session's target, or `null` when not navigating or unresolved. */
  fun activeNavigation(
    data: SurveyMapData,
    session: SurveyMapSession,
  ): StraightLineNavigationState? =
    computeWayfindingNavigation.resolveActiveNavigationState(
      navigationTargetKind = session.navigationTargetKind,
      navigationTargetId = session.navigationTargetId,
      fromNormalizedX = session.userGpsNormalizedX,
      fromNormalizedY = session.userGpsNormalizedY,
      unitSystem = data.unitSystem,
      entities = data.entities,
      allSubmissions = data.allSubmissions,
      submissionGeometries = data.submissionGeometries,
      findPlaceById = { findPlaceById(it, data, session) },
      anchor = data.anchor,
    )
}

/**
 * Resolves a place by [placeId]: the selected place first, then the navigation target (both are
 * snapshots, so they survive the search results changing), then the survey's local places.
 */
internal fun findPlaceById(
  placeId: String,
  data: SurveyMapData,
  session: SurveyMapSession,
): SurveyPlaceItem? =
  session.lastSelectedPlace?.takeIf { it.id == placeId }
    ?: session.navigationTargetPlace?.takeIf { it.id == placeId }
    ?: data.localPlaces.firstOrNull { it.id == placeId }
