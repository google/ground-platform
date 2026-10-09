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
package org.groundplatform.v2.devtools.prototypeapp.ui.state

import org.groundplatform.v2.devtools.prototypeapp.domain.model.BasemapType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.GeospatialEntityItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImagerySource
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LocationLockState
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapFeatureCluster
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapLayerItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapScaleBarSpec
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MeasurementUnitSystem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.NavigationTargetKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OfflineBasemapStyle
import org.groundplatform.v2.devtools.prototypeapp.domain.model.StraightLineNavigationState
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyMapAnchor
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPlaceItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.formatClusterSitesCountLabel
import org.groundplatform.v2.devtools.prototypeapp.domain.model.singularTypeLabelOf
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ClusterMapFeaturesUseCase

/**
 * Screen state of the survey map viewport (mobile Map view and web dashboard map), observed as an
 * immutable snapshot: the camera and GPS location, basemap and imagery layers, zoomed-out clusters,
 * straight-line wayfinding, and the selected map feature, cluster, or place.
 *
 * Data fields come from the local data store through the repositories; the rest is session state
 * owned by `SurveyMapViewModel`.
 */
data class SurveyMapUiState(
  // --- Active survey data ---
  val activeSurveyId: String = "",
  /** Where the active survey sits on the map, for the map content builders. */
  val anchor: SurveyMapAnchor = SurveyMapAnchor.forSurvey(""),
  /** The active survey's location label (e.g. `"Nyeri County, Kenya"`), for place search. */
  val surveyLocationLabel: String = "",
  /** Name of the active survey's organization (never the synthetic `"All users"` one). */
  val activeSurveyOrganizationName: String? = null,
  /** The survey's map layers, styled with the published Survey editor draft (if any). */
  val mapLayers: List<MapLayerItem> = emptyList(),
  /** All map features in the active survey. */
  val entities: List<GeospatialEntityItem> = emptyList(),
  /** Map features currently visible on the map according to layer visibility. */
  val visibleMapEntities: List<GeospatialEntityItem> = emptyList(),
  /** Spatial clusters of [visibleMapEntities] when [isMapClusteringActive]; empty otherwise. */
  val mapFeatureClusters: List<MapFeatureCluster> = emptyList(),
  /**
   * Lowercase plural count noun for map counters and cluster labels (`"coffee parcels"` when a
   * single entity dataset layer is visible, otherwise `"map features"`).
   */
  val activeEntitiesCountNoun: String = "map features",
  // --- Basemap & imagery ---
  val selectedBasemapType: BasemapType = BasemapType.SATELLITE,
  val isOfflineBasemapVisible: Boolean = true,
  val offlineBasemapStyle: OfflineBasemapStyle = OfflineBasemapStyle.SATELLITE_HYBRID,
  /** Imagery sources configured on the synthetic `"All users"` organization. */
  val allUsersImagerySources: List<ImagerySource> = emptyList(),
  /** Imagery sources configured on the active survey's (non-synthetic) organization. */
  val activeSurveyOrganizationImagerySources: List<ImagerySource> = emptyList(),
  /** IDs of organization imagery sources currently toggled ON in the basemap layers dialog. */
  val enabledImagerySourceIds: Set<String> = emptySet(),
  /** Whether the `Layers` (basemap) sheet or dialog is open. */
  val isLayersSheetOpen: Boolean = false,
  // --- GPS location & camera ---
  /** Normalized world coordinates `[0, 1]` of the collector's current GPS location. */
  val userGpsNormalizedX: Float = 0.50f,
  val userGpsNormalizedY: Float = 0.50f,
  /** Formatted GPS coordinates & accuracy badge for the user's current field position. */
  val userGpsCoordinatesLabel: String = "-0.4198°, 36.9512° (±3.2m GPS)",
  val gnssSatelliteCount: Int = 18,
  /** Current horizontal GNSS accuracy in meters. */
  val gnssAccuracyMeters: Double = 2.1,
  val unitSystem: MeasurementUnitSystem = MeasurementUnitSystem.METRIC,
  /**
   * Whether the map camera keeps the user's GPS location centered (`true` by default). Becomes
   * `false` when the user pans the map or selects a place, revealing the `"Recenter"` button.
   */
  val isCameraFollowingUser: Boolean = true,
  val locationLockState: LocationLockState = LocationLockState.LOCKED,
  /** Normalized viewport offsets applied when the user drags/pans the map. */
  val mapPanOffsetX: Float = 0f,
  val mapPanOffsetY: Float = 0f,
  /** Zoom delta relative to the active survey's default zoom level. */
  val mapZoomDelta: Float = 0f,
  /** Google Maps-style horizontal scale bar for the current zoom. */
  val mapScaleBarSpec: MapScaleBarSpec = MapScaleBarSpec("100 m", 100, 72f),
  // --- Selection ---
  val selectedEntityId: String? = null,
  /** Monotonically increasing counter incremented every time a map feature is selected. */
  val entitySelectionEpoch: Long = 0L,
  /** Whether the Entity Bottom Sheet is expanded or collapsed into its peek bar. */
  val isEntityBottomSheetExpanded: Boolean = false,
  /** ID of the selected zoomed-out cluster balloon, if any. */
  val selectedClusterId: String? = null,
  /** The place picked from Place search that the map is centered on, if any. */
  val selectedPlace: SurveyPlaceItem? = null,
  /** Whether the device currently has an active network connection. */
  val isOnline: Boolean = true,
  // --- Straight-line wayfinding ---
  val navigationTargetKind: NavigationTargetKind? = null,
  val navigationTargetId: String? = null,
  /** The active straight-line navigation session resolved against the current data, if any. */
  val activeNavigation: StraightLineNavigationState? = null,
) {
  /** True when the map is zoomed out past the clustering threshold (about z12.95). */
  val isMapClusteringActive: Boolean
    get() = mapZoomDelta <= ClusterMapFeaturesUseCase.CLUSTERING_ZOOM_DELTA

  /** The selected [MapFeatureCluster], if any and if clustering is active. */
  val selectedCluster: MapFeatureCluster?
    get() =
      if (!isMapClusteringActive) null
      else selectedClusterId?.let { id -> mapFeatureClusters.firstOrNull { it.id == id } }

  /** IDs of the map layers toggled visible in the `Layers` sheet. */
  val visibleLayerIds: Set<String>
    get() = mapLayers.filter { it.isVisible }.map { it.id }.toSet()

  /** The selected map feature, if it is on a visible layer. */
  val selectedEntity: GeospatialEntityItem?
    get() = selectedEntityId?.let { id -> visibleMapEntities.firstOrNull { it.id == id } }

  /** All imagery sources toggleable for the active survey: `"All users"` sources first. */
  val availableImagerySources: List<ImagerySource>
    get() = allUsersImagerySources + activeSurveyOrganizationImagerySources

  /** Currently enabled imagery sources, in layer order. */
  val enabledImagerySources: List<ImagerySource>
    get() = availableImagerySources.filter { it.id in enabledImagerySourceIds }

  fun isImagerySourceEnabled(sourceId: String): Boolean = sourceId in enabledImagerySourceIds

  /** Whether online Places search is currently available (`isOnline`). */
  val isPlacesSearchAvailable: Boolean
    get() = isOnline

  /** Formatted horizontal GNSS accuracy (`±2.1 m` or `±6.8 ft`). */
  val gnssAccuracyFormatted: String
    get() = unitSystem.formatGnssAccuracy(gnssAccuracyMeters)

  /** GPS accuracy badge shown on the chip over the map. */
  val gnssStatusChipLabel: String
    get() = gnssAccuracyFormatted

  /** Formatted current zoom level badge (e.g. `"15.3z"`). */
  val effectiveMapZoomLabel: String
    get() {
      val rawZoom = (15.3f + mapZoomDelta).coerceIn(10.0f, 19.0f)
      val tenths = (rawZoom * 10f + 0.5f).toInt()
      return "${tenths / 10}.${tenths % 10}z"
    }

  /** Total horizontal world-to-viewport shift keeping the user centered while following. */
  val mapWorldToScreenShiftX: Float
    get() = (0.50f - userGpsNormalizedX) + mapPanOffsetX

  val mapWorldToScreenShiftY: Float
    get() = (0.50f - userGpsNormalizedY) + mapPanOffsetY

  /** Normalized screen position of the user's GPS blue dot (`0.50f` when centered). */
  val userScreenNormalizedX: Float
    get() = 0.50f + mapPanOffsetX

  val userScreenNormalizedY: Float
    get() = 0.50f + mapPanOffsetY

  /**
   * Formats a cluster's feature count with [activeEntitiesCountNoun], e.g. `"5 coffee parcels"`.
   */
  fun formatClusterSitesCountLabel(siteCount: Int): String =
    formatClusterSitesCountLabel(siteCount, activeEntitiesCountNoun)

  /** Singular domain noun of the map feature with [entityId] (e.g. `"Coffee Parcel"`). */
  fun entitySingularTypeLabel(entityId: String?): String = entities.singularTypeLabelOf(entityId)

  fun isNavigatingToEntity(entityId: String): Boolean =
    navigationTargetKind == NavigationTargetKind.ENTITY && navigationTargetId == entityId

  fun isNavigatingToSubmission(submissionId: String): Boolean =
    navigationTargetKind == NavigationTargetKind.SUBMISSION && navigationTargetId == submissionId

  fun isNavigatingToPlace(placeId: String): Boolean =
    navigationTargetKind == NavigationTargetKind.PLACE && navigationTargetId == placeId
}

/**
 * One-off outcomes of map actions that reach beyond the map slice; the app shell applies them to
 * the rest of the UI (list selection, drawer, screen, notices).
 */
sealed interface SurveyMapEvent {
  /** A map feature was selected on the map (or the selection cleared when [entityId] is `null`). */
  data class EntitySelected(val entityId: String?) : SurveyMapEvent

  /** The map was centered on a place picked from search. */
  data class PlaceSelected(val place: SurveyPlaceItem) : SurveyMapEvent

  /** A zoomed-out cluster balloon was selected. */
  data class ClusterSelected(val cluster: MapFeatureCluster) : SurveyMapEvent

  /** The selected map feature's layer was hidden, clearing the selection. */
  data object SelectionCleared : SurveyMapEvent

  /**
   * Straight-line navigation started: the shell switches to the Map view of the Main Survey screen,
   * selects [submissionId] (if navigating to a submission), and shows [notice].
   */
  data class NavigationStarted(val submissionId: String?, val notice: String) : SurveyMapEvent

  /** Straight-line navigation stopped; the shell clears its notice. */
  data object NavigationStopped : SurveyMapEvent

  data class Notice(val message: String) : SurveyMapEvent
}
