/*
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License'); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
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

  val visibleEntities = state.visibleMapEntities
  val visibleSubGeometries = state.visibleSubmissionGeometries
  val selectedEntity = state.selectedEntity
  val selectedSubmission = state.selectedSubmission
  val activeNav = state.activeNavigation

  val featuresPayloadJson =
    remember(
      visibleEntities,
      visibleSubGeometries,
      selectedEntity?.id,
      selectedSubmission?.id,
      activeNav,
      isCameraFollowingUser,
      mapZoomDelta,
      selectedBasemapType,
      isOfflineBasemapVisible,
    ) {
      buildMapboxFeaturesPayloadJson(
        entities = visibleEntities,
        submissions = visibleSubGeometries,
        selectedEntityId = selectedEntity?.id,
        selectedSubmissionId = selectedSubmission?.id,
        activeNavigation = activeNav,
        isCameraFollowingUser = isCameraFollowingUser,
        zoomDelta = mapZoomDelta,
      )
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

  DisposableEffect(Unit) {
    onDispose {
      hidePlatformMapboxBasemap()
    }
  }

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
              hitResult.startsWith("entity:") -> {
                val entityId = hitResult.removePrefix("entity:")
                state.selectEntity(entityId)
              }
              hitResult.startsWith("submission:") -> {
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
                state.updateLayersSheetOpen(false)
                state.updateEntityBottomSheetExpanded(false)
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
              panPlatformMapboxBasemap(
                dxPx = dragAmount.x / density,
                dyPx = dragAmount.y / density,
              )
            }
          }
        }
  ) {
    // Clear the Skia canvas pixels in the map viewport to rgba(0, 0, 0, 0) so the real
    // mapboxgl.Map WebGL canvas and mapboxgl.Marker elements inside #mapbox-basemap-container
    // render directly with no static 2D canvas shapes overlaid on top.
    drawRect(
      color = Color.Transparent,
      blendMode = BlendMode.Clear,
    )
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
  )

private fun buildMapboxFeaturesPayloadJson(
  entities: List<GeospatialEntityItem>,
  submissions: List<SubmissionGeometryPolygon>,
  selectedEntityId: String?,
  selectedSubmissionId: String?,
  activeNavigation: StraightLineNavigationState?,
  isCameraFollowingUser: Boolean,
  zoomDelta: Float,
): String {
  val entitiesJson =
    entities.joinToString(separator = ",") { ent ->
      val hex = colorHexToCssString(ent.colorHex)
      val selected = ent.id == selectedEntityId
      val shortLabel = escapeJsonString(ent.label.substringBefore(" •"))
      val modelBadge = ""
      """{"id":"${ent.id}","label":"$shortLabel","badge":"$modelBadge","geometryType":"${ent.geometryTypeLabel}","nx":${ent.normalizedX},"ny":${ent.normalizedY},"color":"$hex","selected":$selected}"""
    }
  val submissionsJson =
    submissions.joinToString(separator = ",") { sub ->
      val hex = colorHexToCssString(sub.colorHex)
      val selected = sub.submissionId == selectedSubmissionId
      val shortBadge = escapeJsonString(sub.shortMapBadge)
      """{"id":"${sub.id}","submissionId":"${sub.submissionId}","label":"$shortBadge","nx":${sub.normalizedX},"ny":${sub.normalizedY},"wf":${sub.widthFraction},"hf":${sub.heightFraction},"color":"$hex","selected":$selected}"""
    }
  val navJson =
    if (activeNavigation != null) {
      val vec = activeNavigation.vector
      val safeTitle = escapeJsonString(activeNavigation.targetTitle.substringBefore(" •"))
      val safeDist =
        escapeJsonString("${vec.formattedDistance} • ${vec.bearingDegrees}° ${vec.cardinalDirection}")
      val hex = colorHexToCssString(activeNavigation.colorHex)
      """{"active":true,"kind":"${activeNavigation.targetKind.badgeLabel}","targetId":"${activeNavigation.targetId}","title":"$safeTitle","distanceBadge":"$safeDist","fromX":${vec.fromNormalizedX},"fromY":${vec.fromNormalizedY},"toX":${vec.toNormalizedX},"toY":${vec.toNormalizedY},"bearing":${vec.bearingDegrees},"cardinal":"${vec.cardinalDirection}","arrived":${vec.hasArrived},"color":"$hex"}"""
    } else {
      """{"active":false}"""
    }
  return """{"isFollowingUser":$isCameraFollowingUser,"zoomDelta":$zoomDelta,"navigation":$navJson,"entities":[$entitiesJson],"submissions":[$submissionsJson]}"""
}

private fun escapeJsonString(raw: String): String =
  raw.replace("\\", "\\\\").replace("\"", "\\\"")

private fun colorHexToCssString(colorHex: Long): String {
  val rgb = (colorHex and 0xFFFFFFL).toString(16).padStart(6, '0').uppercase()
  return "#$rgb"
}
