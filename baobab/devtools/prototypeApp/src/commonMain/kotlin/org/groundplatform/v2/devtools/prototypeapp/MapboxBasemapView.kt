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
package org.groundplatform.v2.devtools.prototypeapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity

/**
 * Synchronizes the DOM `#mapbox-basemap-container` viewport rectangle with the Compose
 * [SurveyMapView] bounds inside the mobile/tablet device frame.
 */
internal expect fun syncPlatformMapboxViewport(
  leftPx: Float,
  topPx: Float,
  widthPx: Float,
  heightPx: Float,
  borderRadiusPx: Float,
  visible: Boolean,
)

/**
 * Synchronizes the live `mapboxgl.Map` camera, basemap style (`SATELLITE` vs `NORMAL`), offline
 * tile visibility, and GeoJSON entity/submission layers + `mapboxgl.Marker`s via
 * `window.GroundMapboxBridge`.
 */
internal expect fun syncPlatformMapboxBasemap(
  surveyId: String,
  basemapType: String,
  isOfflineVisible: Boolean,
  panOffsetX: Float,
  panOffsetY: Float,
  userGpsX: Float,
  userGpsY: Float,
  featuresGeoJson: String,
)

/** Pans the live `mapboxgl.Map` instance by `(dxPx, dyPx)` CSS pixels during drag gestures. */
internal expect fun panPlatformMapboxBasemap(dxPx: Float, dyPx: Float)

/** Zooms the live `mapboxgl.Map` instance by `deltaZoom` during wheel/pinch gestures. */
internal expect fun zoomPlatformMapboxBasemap(deltaZoom: Float)

/**
 * Performs interactive hit-testing at `(xPx, yPx)` CSS pixels relative to the Mapbox container
 * against `mapboxgl.Marker` badges, `mapboxgl.NavigationControl` buttons, and WebGL GeoJSON layers
 * (`map.queryRenderedFeatures`).
 *
 * Returns:
 * - `"entity:<entityId>"` if an entity marker, polygon, or point was clicked
 * - `"submission:<geometryId>"` if a submission geometry badge or dotted polygon was clicked
 * - `"control:zoom"` if a Mapbox navigation control button was clicked
 * - `""` if the empty map background was clicked
 */
internal expect fun handlePlatformMapboxClick(xPx: Float, yPx: Float): String

/** Hides the `#mapbox-basemap-container` when leaving the Map view. */
internal expect fun hidePlatformMapboxBasemap()

/**
 * Queries the Mapbox Places API (`mapbox.places` / Mapbox Geocoding API) via
 * `window.GroundMapboxBridge.searchPlaces` for geographic places matching [query] near the active
 * [surveyId], invoking [onResultsJson] with a JSON array of matching place objects.
 *
 * When [isAirplaneMode] is `true` (Offline), no network request is issued and `"[]"` is returned.
 */
internal expect fun searchPlatformMapboxPlaces(
  surveyId: String,
  query: String,
  isAirplaneMode: Boolean,
  onResultsJson: (String) -> Unit,
)

/**
 * Flies the live `mapboxgl.Map` camera directly to `[lng, lat]` at [zoom] and renders a Mapbox
 * Place pin marker (`name`, `category`, `coordinatesLabel`) on the map.
 */
internal expect fun flyPlatformMapboxToPlace(
  lng: Double,
  lat: Double,
  zoom: Float,
  name: String,
  category: String,
  coordinatesLabel: String,
)

/** Clears any active Mapbox Place search pin marker from the live `mapboxgl.Map`. */
internal expect fun clearPlatformMapboxPlace()

/**
 * Renders the real Mapbox GL JS basemap (`mapboxgl.Map`) inside `SurveyMapView` and delegates all
 * polygon, point, submission geometry, offline sector, and GPS blue-dot rendering + hit-testing
 * directly to Mapbox GL GeoJSON layers and `mapboxgl.Marker` instances.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MapboxBasemapView(
  state: PrototypeAppState,
  animatedShiftX: Float,
  animatedShiftY: Float,
  modifier: Modifier = Modifier,
) {
  val density = LocalDensity.current.density
  var viewportLeftCssPx by remember { mutableStateOf(0f) }
  var viewportTopCssPx by remember { mutableStateOf(0f) }
  var viewportWidthCssPx by remember { mutableStateOf(0f) }
  var viewportHeightCssPx by remember { mutableStateOf(0f) }

  val activeSurveyId = state.activeSurveyId
  val selectedBasemapType = state.selectedBasemapType
  val isOfflineBasemapVisible = state.isOfflineBasemapVisible
  val panOffsetX = state.mapPanOffsetX
  val panOffsetY = state.mapPanOffsetY
  val userGpsX = state.userGpsNormalizedX
  val userGpsY = state.userGpsNormalizedY
  val isCameraFollowingUser = state.isCameraFollowingUser
  val mapZoomDelta = state.mapZoomDelta

  val allEntities = state.entities
  val mapLayers = state.mapLayers
  val visibleEntities =
    remember(allEntities, mapLayers) {
      val visibleIds = mapLayers.filter { it.isVisible }.map { it.id }.toSet()
      allEntities.filter { it.layerId in visibleIds }
    }
  val visibleSubGeometries = emptyList<SubmissionGeometryPolygon>()
  val selectedEntity = state.selectedEntity
  val selectedSubmission = state.selectedSubmission
  val activeNav = state.activeNavigation
  val isClusteringActive = state.isMapClusteringActive
  val clusterRadius = state.mapClusterRadiusNormalized
  val mapClusters =
    remember(visibleEntities, isClusteringActive, clusterRadius) {
      if (!isClusteringActive) {
        emptyList()
      } else {
        state.mapFeatureClusters
      }
    }
  val selectedClusterId = state.selectedClusterId

  val baseEntitiesJson =
    remember(visibleEntities) {
      serializeMapboxEntitiesJson(visibleEntities, selectedEntityId = null)
    }

  val entitiesJsonWithSelection =
    remember(baseEntitiesJson, selectedEntity?.id) {
      val selId = selectedEntity?.id
      if (selId == null) {
        baseEntitiesJson
      } else {
        baseEntitiesJson.replace(
          "\"id\":\"$selId\",\"selected\":false",
          "\"id\":\"$selId\",\"selected\":true",
        )
      }
    }

  val activeEntitiesCountNoun = state.activeEntitiesCountNoun
  val featuresPayloadJson =
    remember(
      entitiesJsonWithSelection,
      visibleSubGeometries,
      selectedEntity?.id,
      selectedSubmission?.id,
      activeNav,
      isCameraFollowingUser,
      mapZoomDelta,
      isClusteringActive,
      mapClusters,
      selectedClusterId,
      activeEntitiesCountNoun,
    ) {
      buildMapboxFeaturesPayloadJsonFromPrebuiltEntities(
        entitiesJson = entitiesJsonWithSelection,
        entityCount = visibleEntities.size,
        submissions = visibleSubGeometries,
        selectedEntityId = selectedEntity?.id,
        selectedSubmissionId = selectedSubmission?.id,
        activeNavigation = activeNav,
        isCameraFollowingUser = isCameraFollowingUser,
        zoomDelta = mapZoomDelta,
        isClusteringActive = isClusteringActive,
        clusters = mapClusters,
        selectedClusterId = selectedClusterId,
        activeEntitiesCountNoun = activeEntitiesCountNoun,
      )
    }

  val selectedPlace = state.selectedPlace
  androidx.compose.runtime.LaunchedEffect(
    selectedPlace?.id,
    selectedPlace?.longitude,
    selectedPlace?.latitude,
    selectedPlace?.targetZoom,
  ) {
    if (selectedPlace != null) {
      flyPlatformMapboxToPlace(
        lng = selectedPlace.longitude,
        lat = selectedPlace.latitude,
        zoom = selectedPlace.targetZoom.coerceIn(2.0f, 18.5f),
        name = selectedPlace.name,
        category = selectedPlace.categoryLabel,
        coordinatesLabel = selectedPlace.coordinatesLabel,
      )
    } else {
      clearPlatformMapboxPlace()
    }
  }

  androidx.compose.runtime.LaunchedEffect(
    activeSurveyId,
    selectedBasemapType,
    isOfflineBasemapVisible,
    panOffsetX,
    panOffsetY,
    userGpsX,
    userGpsY,
    featuresPayloadJson,
    viewportWidthCssPx,
    viewportHeightCssPx,
  ) {
    if (viewportWidthCssPx > 4f && viewportHeightCssPx > 4f) {
      syncPlatformMapboxBasemap(
        surveyId = activeSurveyId,
        basemapType = selectedBasemapType.name,
        isOfflineVisible = isOfflineBasemapVisible,
        panOffsetX = panOffsetX,
        panOffsetY = panOffsetY,
        userGpsX = userGpsX,
        userGpsY = userGpsY,
        featuresGeoJson = featuresPayloadJson,
      )
    }
  }

  SideEffect {
    val isVisible = viewportWidthCssPx > 4f && viewportHeightCssPx > 4f
    syncPlatformMapboxViewport(
      leftPx = viewportLeftCssPx,
      topPx = viewportTopCssPx,
      widthPx = viewportWidthCssPx,
      heightPx = viewportHeightCssPx,
      borderRadiusPx = 0f,
      visible = isVisible,
    )
    if (isVisible) {
      syncPlatformMapboxBasemap(
        surveyId = activeSurveyId,
        basemapType = selectedBasemapType.name,
        isOfflineVisible = isOfflineBasemapVisible,
        panOffsetX = panOffsetX,
        panOffsetY = panOffsetY,
        userGpsX = userGpsX,
        userGpsY = userGpsY,
        featuresGeoJson = featuresPayloadJson,
      )
    }
  }

  DisposableEffect(Unit) { onDispose { hidePlatformMapboxBasemap() } }

  Canvas(
    modifier =
      modifier
        .fillMaxSize()
        .onGloballyPositioned { coordinates ->
          val pos = coordinates.positionInWindow()
          val sz = coordinates.size
          viewportLeftCssPx = pos.x / density
          viewportTopCssPx = pos.y / density
          viewportWidthCssPx = sz.width / density
          viewportHeightCssPx = sz.height / density
        }
        .onPointerEvent(PointerEventType.Scroll) { event ->
          val scrollDeltaY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
          if (scrollDeltaY != 0f) {
            val step = (-scrollDeltaY * 0.35f).coerceIn(-1.2f, 1.2f)
            state.zoomMapBy(step)
            zoomPlatformMapboxBasemap(step)
          }
        }
        .pointerInput(viewportWidthCssPx, viewportHeightCssPx, density) {
          detectTapGestures { tapOffset ->
            val clickXCssPx = tapOffset.x / density
            val clickYCssPx = tapOffset.y / density
            val hitResult = handlePlatformMapboxClick(clickXCssPx, clickYCssPx)
            when {
              hitResult == "place:dismiss" -> {
                state.clearSelectedPlace()
              }
              hitResult.startsWith("cluster:") -> {
                state.clearSelectedPlace()
                val clusterId = hitResult.removePrefix("cluster:")
                val wasAlreadySelected = state.selectedClusterId == clusterId
                state.selectCluster(clusterId)
                if (wasAlreadySelected) {
                  zoomPlatformMapboxBasemap(0.75f)
                }
              }
              hitResult.startsWith("entity:") -> {
                state.clearSelectedPlace()
                val entityId = hitResult.removePrefix("entity:")
                state.selectEntity(entityId)
              }
              hitResult.startsWith("submission:") -> {
                state.clearSelectedPlace()
                val geomId = hitResult.removePrefix("submission:")
                state.selectSubmissionGeometry(geomId)
              }
              hitResult == "control:zoom-in" -> {
                state.zoomInMap()
              }
              hitResult == "control:zoom-out" -> {
                state.zoomOutMap()
              }
              hitResult == "control:compass" -> {
                state.resetMapZoom()
              }
              else -> {
                state.clearSelectedPlace()
                state.updateLayersSheetOpen(false)
                state.selectCluster(null)
                if (state.isEntityBottomSheetExpanded) {
                  state.updateEntityBottomSheetExpanded(false)
                } else {
                  state.selectEntity(null)
                }
              }
            }
          }
        }
        .pointerInput(viewportWidthCssPx, viewportHeightCssPx, density) {
          detectDragGestures { change, dragAmount ->
            change.consume()
            val widthPx = size.width.toFloat()
            val heightPx = size.height.toFloat()
            if (widthPx > 0f && heightPx > 0f) {
              state.panMap(
                deltaNormalizedX = dragAmount.x / widthPx,
                deltaNormalizedY = dragAmount.y / heightPx,
              )
              panPlatformMapboxBasemap(dxPx = dragAmount.x / density, dyPx = dragAmount.y / density)
            }
          }
        }
  ) {
    // Clear the Skia canvas pixels in the map viewport to rgba(0, 0, 0, 0) so the real
    // mapboxgl.Map WebGL canvas and mapboxgl.Marker elements inside #mapbox-basemap-container
    // render directly with no static 2D canvas shapes overlaid on top.
    drawRect(color = Color.Transparent, blendMode = BlendMode.Clear)
  }
}

internal fun buildMapboxFeaturesPayloadJson(state: PrototypeAppState): String =
  buildMapboxFeaturesPayloadJson(
    entities = state.visibleMapEntities,
    submissions = state.visibleSubmissionGeometries,
    selectedEntityId = state.selectedEntity?.id,
    selectedSubmissionId = state.selectedSubmission?.id,
    activeNavigation = state.activeNavigation,
    isCameraFollowingUser = state.isCameraFollowingUser,
    zoomDelta = state.mapZoomDelta,
    isClusteringActive = state.isMapClusteringActive,
    clusters = state.mapFeatureClusters,
    selectedClusterId = state.selectedClusterId,
    activeEntitiesCountNoun = state.activeEntitiesCountNoun,
  )

private fun serializeMapboxEntitiesJson(
  entities: List<GeospatialEntityItem>,
  selectedEntityId: String?,
): String =
  entities.joinToString(separator = ",") { ent ->
    val markerColor = escapeJsonString(ent.markerColorCss)
    val strokeColor = escapeJsonString(ent.strokeColorCss)
    val fillColor = escapeJsonString(ent.fillColorCss)
    val markerSymbol = escapeJsonString(ent.markerSymbol)
    val selected = ent.id == selectedEntityId
    val shortLabel = escapeJsonString(ent.label.substringBefore(" •"))
    val statusSummary = escapeJsonString(ent.mapStatusSummaryBadge)
    """{"id":"${ent.id}","selected":$selected,"label":"$shortLabel","badge":"$markerSymbol","markerSymbol":"$markerSymbol","markerColor":"$markerColor","strokeColor":"$strokeColor","fillColor":"$fillColor","statusSummary":"$statusSummary","submissionCount":${ent.submissionCount},"isCompleted":${ent.isCompleted},"isPending":${ent.isPending},"geometryType":"${ent.geometryTypeLabel}","nx":${ent.normalizedX},"ny":${ent.normalizedY},"color":"$markerColor"}"""
  }

private fun buildMapboxFeaturesPayloadJson(
  entities: List<GeospatialEntityItem>,
  submissions: List<SubmissionGeometryPolygon>,
  selectedEntityId: String?,
  selectedSubmissionId: String?,
  activeNavigation: StraightLineNavigationState?,
  isCameraFollowingUser: Boolean,
  zoomDelta: Float,
  isClusteringActive: Boolean = false,
  clusters: List<MapFeatureCluster> = emptyList(),
  selectedClusterId: String? = null,
  activeEntitiesCountNoun: String = "map features",
): String =
  buildMapboxFeaturesPayloadJsonFromPrebuiltEntities(
    entitiesJson = serializeMapboxEntitiesJson(entities, selectedEntityId),
    entityCount = entities.size,
    submissions = submissions,
    selectedEntityId = selectedEntityId,
    selectedSubmissionId = selectedSubmissionId,
    activeNavigation = activeNavigation,
    isCameraFollowingUser = isCameraFollowingUser,
    zoomDelta = zoomDelta,
    isClusteringActive = isClusteringActive,
    clusters = clusters,
    selectedClusterId = selectedClusterId,
    activeEntitiesCountNoun = activeEntitiesCountNoun,
  )

private fun buildMapboxFeaturesPayloadJsonFromPrebuiltEntities(
  entitiesJson: String,
  entityCount: Int,
  submissions: List<SubmissionGeometryPolygon>,
  selectedEntityId: String?,
  selectedSubmissionId: String?,
  activeNavigation: StraightLineNavigationState?,
  isCameraFollowingUser: Boolean,
  zoomDelta: Float,
  isClusteringActive: Boolean = false,
  clusters: List<MapFeatureCluster> = emptyList(),
  selectedClusterId: String? = null,
  activeEntitiesCountNoun: String = "map features",
): String {
  val effectiveEntitiesJson = if (isClusteringActive) "" else entitiesJson
  val submissionsJson = ""
  val clustersJson =
    clusters.joinToString(separator = ",") { cluster ->
      val selected = cluster.id == selectedClusterId
      val safeSummary = escapeJsonString(cluster.balloonSummaryLabel)
      val siteNoun =
        if (cluster.siteCount == 1) {
          activeEntitiesCountNoun.removeSuffix("s")
        } else {
          activeEntitiesCountNoun
        }
      val safeSiteCountLabel = escapeJsonString("${cluster.siteCount} $siteNoun")
      val siteGroupsJson =
        cluster.siteSymbolGroups.joinToString(separator = ",") { grp ->
          val safeSym = escapeJsonString(grp.markerSymbol)
          val safeColor = escapeJsonString(grp.colorCss)
          val safeStatus = escapeJsonString(grp.statusLabel)
          """{"symbol":"$safeSym","isNoSymbol":${grp.isNoSymbolGroup},"count":${grp.count},"color":"$safeColor","statusLabel":"$safeStatus"}"""
        }
      val groupsJson =
        cluster.symbolGroups.joinToString(separator = ",") { grp ->
          val safeSym = escapeJsonString(grp.markerSymbol)
          val safeColor = escapeJsonString(grp.colorCss)
          val safeStatus = escapeJsonString(grp.statusLabel)
          """{"symbol":"$safeSym","isNoSymbol":${grp.isNoSymbolGroup},"count":${grp.count},"color":"$safeColor","statusLabel":"$safeStatus"}"""
        }
      """{"id":"${cluster.id}","nx":${cluster.normalizedX},"ny":${cluster.normalizedY},"totalCount":${cluster.totalCount},"siteCount":${cluster.siteCount},"siteCountLabel":"$safeSiteCountLabel","submissionGeometryCount":${cluster.submissionGeometryCount},"selected":$selected,"summaryLabel":"$safeSummary","siteGroups":[$siteGroupsJson],"groups":[$groupsJson]}"""
    }
  val navJson =
    if (activeNavigation != null) {
      val vec = activeNavigation.vector
      val safeTitle = escapeJsonString(activeNavigation.targetTitle.substringBefore(" •"))
      val safeDist =
        escapeJsonString(
          "${vec.formattedDistance} • ${vec.bearingDegrees}° ${vec.cardinalDirection}"
        )
      val hex = colorHexToCssString(activeNavigation.colorHex)
      """{"active":true,"kind":"${activeNavigation.targetKind.badgeLabel}","targetId":"${activeNavigation.targetId}","title":"$safeTitle","distanceBadge":"$safeDist","fromX":${vec.fromNormalizedX},"fromY":${vec.fromNormalizedY},"toX":${vec.toNormalizedX},"toY":${vec.toNormalizedY},"bearing":${vec.bearingDegrees},"cardinal":"${vec.cardinalDirection}","arrived":${vec.hasArrived},"color":"$hex"}"""
    } else {
      """{"active":false}"""
    }
  val selectedEntityIdJson =
    if (selectedEntityId != null) "\"${escapeJsonString(selectedEntityId)}\"" else "null"
  val selectedClusterIdJson =
    if (selectedClusterId != null) "\"${escapeJsonString(selectedClusterId)}\"" else "null"
  return """{"isFollowingUser":$isCameraFollowingUser,"isLocationLocked":$isCameraFollowingUser,"zoomDelta":$zoomDelta,"entityCount":$entityCount,"selectedEntityId":$selectedEntityIdJson,"isClusteringActive":$isClusteringActive,"selectedClusterId":$selectedClusterIdJson,"clusters":[$clustersJson],"navigation":$navJson,"entities":[$effectiveEntitiesJson],"submissions":[$submissionsJson]}"""
}

private fun escapeJsonString(raw: String): String = raw.replace("\\", "\\\\").replace("\"", "\\\"")

private fun colorHexToCssString(colorHex: Long): String {
  val rgb = (colorHex and 0xFFFFFFL).toString(16).padStart(6, '0').uppercase()
  return "#$rgb"
}
