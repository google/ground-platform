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

import kotlin.math.abs
import org.openforis.collectone.v2.app.domain.model.BasemapType
import org.openforis.collectone.v2.app.domain.model.ImagerySource
import org.openforis.collectone.v2.app.domain.model.LocationLockState
import org.openforis.collectone.v2.app.domain.model.NavigationTargetKind
import org.openforis.collectone.v2.app.domain.model.OfflineImageryType
import org.openforis.collectone.v2.app.domain.model.SurveyMapAnchor
import org.openforis.collectone.v2.app.domain.model.SurveyPlaceItem
import org.openforis.collectone.v2.app.domain.repository.DeviceLocationSnapshot
import org.openforis.collectone.v2.app.domain.usecase.ClusterMapFeaturesUseCase
import org.openforis.collectone.v2.map.CameraPosition
import org.openforis.collectone.v2.map.LatLng as MapLatLng
import org.openforis.collectone.v2.map.LngLatBounds

/**
 * Session state of the survey map viewport: what the user did to the map in this ViewModel's
 * lifetime (camera, imagery toggles, selection, navigation target). None of it is persisted; data
 * from the local data store is in [SurveyMapData].
 *
 * The camera is modelled relative to the user's GPS position, in the survey's normalized map space
 * (see [SurveyMapAnchor]): the screen center is at `userGps - mapPanOffset`, so while
 * [isCameraFollowingUser] the offsets are zero and GPS updates move the camera with the user. The
 * zoom is a delta from [SurveyMapAnchor.zoom].
 *
 * The transitions below are pure, so `MutableStateFlow.update` can retry them safely.
 */
internal data class SurveyMapSession(
  val selectedBasemapType: BasemapType = BasemapType.NORMAL,
  val enabledImagerySourceIds: Set<String> = emptySet(),
  val resolvedOverlayTileUrls: Map<String, String> = emptyMap(),
  val pinnedSceneOverlay: ImagerySource? = null,
  val isLayersSheetOpen: Boolean = false,
  val hiddenDownloadedImagery: Set<OfflineImageryType> = emptySet(),
  val isSelectingOfflineArea: Boolean = false,
  val framedBounds: LngLatBounds? = null,
  /** Bumped on every [framedBounds] request, so framing the same bounds twice frames twice. */
  val framingEpoch: Long = 0L,
  val location: DeviceLocationSnapshot = DeviceLocationSnapshot(),
  val isCameraFollowingUser: Boolean = true,
  val locationLockState: LocationLockState = LocationLockState.LOCKED,
  val mapPanOffsetX: Float = 0f,
  val mapPanOffsetY: Float = 0f,
  val mapZoomDelta: Float = 0f,
  val selectedEntityId: String? = null,
  /** Bumped when the map should frame the selected feature again, even if it didn't change. */
  val entitySelectionEpoch: Long = 0L,
  val isEntityBottomSheetExpanded: Boolean = false,
  val selectedClusterId: String? = null,
  val selectedPlaceId: String? = null,
  /** The place picked from search; search results may no longer contain it. */
  val lastSelectedPlace: SurveyPlaceItem? = null,
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
  fun pannedTo(nx: Float, ny: Float, targetScreenY: Float = 0.50f): SurveyMapSession =
    copy(
      isCameraFollowingUser = false,
      locationLockState = LocationLockState.PANNED,
      mapPanOffsetX = (userGpsNormalizedX - nx).coerceIn(-MAX_PAN, MAX_PAN),
      mapPanOffsetY =
        ((userGpsNormalizedY - ny) + (targetScreenY - 0.50f)).coerceIn(-MAX_PAN, MAX_PAN),
    )

  /** Camera dragged by normalized deltas, which stops following the user. */
  fun pannedBy(deltaX: Float, deltaY: Float): SurveyMapSession =
    copy(
      isCameraFollowingUser = false,
      locationLockState = LocationLockState.PANNED,
      mapPanOffsetX = (mapPanOffsetX + deltaX).coerceIn(-MAX_PAN, MAX_PAN),
      mapPanOffsetY = (mapPanOffsetY + deltaY).coerceIn(-MAX_PAN, MAX_PAN),
    )

  /** Camera following the user again, at the screen center. */
  fun recentered(): SurveyMapSession =
    copy(
      isCameraFollowingUser = true,
      locationLockState = LocationLockState.LOCKED,
      mapPanOffsetX = 0f,
      mapPanOffsetY = 0f,
    )

  /** Zoom delta set to [zoomDelta] (clamped to [range]); leaving clustering drops the cluster. */
  fun zoomedTo(zoomDelta: Float, range: ClosedFloatingPointRange<Float>): SurveyMapSession {
    val next = zoomDelta.coerceIn(range)
    val clustering = next <= ClusterMapFeaturesUseCase.CLUSTERING_ZOOM_DELTA
    return copy(
      mapZoomDelta = next,
      selectedClusterId = if (clustering) selectedClusterId else null,
    )
  }

  fun withoutPlace(): SurveyMapSession = copy(selectedPlaceId = null, lastSelectedPlace = null)

  /**
   * The user's GPS position moved to ([x], [y]). While following, the camera moves with the user
   * (the offsets stay zero); after a pan the offsets absorb the move, so the viewport stays put.
   */
  fun movedTo(x: Float, y: Float, coordinatesLabel: String): SurveyMapSession {
    val moved =
      copy(
        location =
          location.copy(normalizedX = x, normalizedY = y, coordinatesLabel = coordinatesLabel)
      )
    if (isCameraFollowingUser) return moved
    return moved.copy(
      mapPanOffsetX = (mapPanOffsetX + (x - userGpsNormalizedX)).coerceIn(-MAX_PAN, MAX_PAN),
      mapPanOffsetY = (mapPanOffsetY + (y - userGpsNormalizedY)).coerceIn(-MAX_PAN, MAX_PAN),
    )
  }

  /**
   * The camera this session asks the map for: the screen center in geographic coordinates (latitude
   * clamped to Web Mercator's limit, longitude wrapped into [-180, 180)) and the anchor's zoom plus
   * the session's delta.
   */
  fun desiredCamera(anchor: SurveyMapAnchor): CameraPosition {
    // SurveyMapAnchor.toLatLng's projection, computed on raw degrees: a long pan can put the screen
    // center past a pole or the antimeridian, where a LatLng can't be built before clamping.
    val nx = (userGpsNormalizedX - mapPanOffsetX).toDouble()
    val ny = (userGpsNormalizedY - mapPanOffsetY).toDouble()
    val latitude = anchor.center.latitude - (ny - 0.5) * SurveyMapAnchor.SPAN_LATITUDE
    val longitude = anchor.center.longitude + (nx - 0.5) * SurveyMapAnchor.SPAN_LONGITUDE
    return CameraPosition(
      center =
        MapLatLng(
          latitude.coerceIn(-MAX_MAP_LATITUDE, MAX_MAP_LATITUDE),
          (longitude + 180.0).mod(360.0) - 180.0,
        ),
      zoom = anchor.zoom + mapZoomDelta,
    )
  }

  /**
   * Session synced to where the map [camera] settled. A pan within [MAP_SYNC_TOLERANCE] of the
   * current offsets is float noise from the round trip through geographic coordinates and keeps the
   * camera following the user; a larger one counts as a pan. The zoom is always taken, clamped to
   * [SYNC_ZOOM_RANGE], unless the map reported NaN (no zoom yet).
   */
  fun syncedTo(camera: CameraPosition, anchor: SurveyMapAnchor): SurveyMapSession {
    val (nx, ny) = anchor.toNormalized(camera.center)
    val panX = (userGpsNormalizedX - nx).toFloat().coerceIn(-MAX_PAN, MAX_PAN)
    val panY = (userGpsNormalizedY - ny).toFloat().coerceIn(-MAX_PAN, MAX_PAN)
    val moved =
      abs(panX - mapPanOffsetX) > MAP_SYNC_TOLERANCE ||
        abs(panY - mapPanOffsetY) > MAP_SYNC_TOLERANCE
    val panned =
      if (moved) {
        copy(
          isCameraFollowingUser = false,
          locationLockState = LocationLockState.PANNED,
          mapPanOffsetX = panX,
          mapPanOffsetY = panY,
        )
      } else {
        this
      }
    val zoomDelta = (camera.zoom - anchor.zoom).toFloat()
    return if (zoomDelta.isNaN()) panned else panned.zoomedTo(zoomDelta, SYNC_ZOOM_RANGE)
  }

  /**
   * Navigation toward a target started: the camera recenters on the user, and the sheets close so
   * the navigation line is visible.
   */
  fun navigatingTo(
    kind: NavigationTargetKind,
    targetId: String,
    selectedEntityId: String?,
    place: SurveyPlaceItem? = null,
  ): SurveyMapSession {
    val next =
      recentered()
        .copy(
          navigationTargetKind = kind,
          navigationTargetId = targetId,
          navigationTargetPlace = place,
          selectedEntityId = selectedEntityId,
          isEntityBottomSheetExpanded = false,
          isLayersSheetOpen = false,
        )
    return if (place == null) next
    else next.copy(selectedPlaceId = place.id, lastSelectedPlace = place)
  }

  fun isNavigatingTo(kind: NavigationTargetKind, targetId: String): Boolean =
    navigationTargetKind == kind && navigationTargetId == targetId

  companion object {
    /** Web Mercator's latitude limit, for camera centers. */
    const val MAX_MAP_LATITUDE = 85.0

    /** Pan offset change (normalized) below which a settled camera counts as not moved. */
    const val MAP_SYNC_TOLERANCE = 1e-4f

    /** Largest normalized pan offset in either direction. */
    const val MAX_PAN = 10000f

    /** Zoom deltas the map camera itself may settle at. */
    val SYNC_ZOOM_RANGE = -13.0f..3.7f

    /** Zoom deltas reachable with the zoom buttons. */
    val STEP_ZOOM_RANGE = -8.0f..3.7f

    /** Zoom deltas a place picked from search may set. */
    val PLACE_ZOOM_RANGE = -13.0f..3.2f
  }
}
