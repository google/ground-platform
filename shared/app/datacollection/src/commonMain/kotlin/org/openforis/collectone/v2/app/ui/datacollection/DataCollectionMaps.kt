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
package org.openforis.collectone.v2.app.ui.datacollection

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import org.openforis.collectone.v2.app.domain.model.FormPreviewItem
import org.openforis.collectone.v2.app.domain.model.editor.toLatLng
import org.openforis.collectone.v2.app.domain.model.hasGeometry
import org.openforis.collectone.v2.app.ui.map.BasemapToggleButton
import org.openforis.collectone.v2.app.ui.map.EntityGeometry
import org.openforis.collectone.v2.app.ui.map.SurveyAppMap
import org.openforis.collectone.v2.app.ui.map.SurveyMainMap
import org.openforis.collectone.v2.app.ui.map.SurveyMapActions
import org.openforis.collectone.v2.app.ui.map.SurveyMapCameraController
import org.openforis.collectone.v2.app.ui.map.SurveyMapContent
import org.openforis.collectone.v2.app.ui.map.SurveyMapIds
import org.openforis.collectone.v2.app.ui.map.SurveyMapUiState
import org.openforis.collectone.v2.app.ui.map.framingInsets
import org.openforis.collectone.v2.app.ui.map.rememberSurveyMapCamera
import org.openforis.collectone.v2.core.forms.ui.GeoPointMapViewportState
import org.openforis.collectone.v2.map.CameraPosition
import org.openforis.collectone.v2.map.LatLng

/**
 * The main survey map (mobile map view and web dashboard): the visible map features, clusters,
 * navigation, GPS location, a place picked from search, and (web dashboard) the open form's
 * geometry answers and the geometry being drawn, from [dataCollection].
 *
 * [camera] is hoisted so screens can frame selections with it. When [collapseSheetOnBackgroundTap]
 * is `true` (mobile bottom sheet host), tapping the empty map first collapses an expanded sheet
 * before clearing the selection. Hosts without a bottom sheet (the web dashboard) pass `false` so a
 * background tap clears the selection immediately.
 *
 * @param formMap the open form's hooks: map clicks become vertices while drawing, and tapping a
 *   geometry answer focuses its question.
 * @param pendingIds entities with pending uploads, drawn in the pending style.
 */
@Composable
fun SurveyMainMap(
  uiState: SurveyMapUiState,
  actions: SurveyMapActions,
  dataCollection: DataCollectionUiState,
  formMap: FormMapInteraction,
  pendingIds: Set<String>,
  camera: SurveyMapCameraController,
  modifier: Modifier = Modifier,
  collapseSheetOnBackgroundTap: Boolean = true,
  showNavigationOverlay: Boolean = true,
) {
  SurveyMainMap(
    uiState = uiState,
    actions = actions,
    map =
      SurveyMapContent.main(
        uiState = uiState,
        dataCollection = dataCollection,
        pendingIds = pendingIds,
        showNavigation = showNavigationOverlay,
      ),
    camera = camera,
    modifier = modifier,
    collapseSheetOnBackgroundTap = collapseSheetOnBackgroundTap,
    onDrawingTap =
      if (dataCollection.isWebMapDrawing) {
        { at -> formMap.addWebMapDrawingVertex(at) }
      } else {
        null
      },
    onFormGeometryTap =
      if (dataCollection.isDataCollectionFormOpen) {
        { path -> formMap.focusWebFormQuestion(path) }
      } else {
        null
      },
  )
}

/**
 * The map behind a form's GeoPoint question, following the question's pan and zoom. The camera
 * starts at the collector's GPS location from [mapUiState]; the open form's target feature from
 * [uiState] is highlighted. A small button in the bottom-right corner switches between the
 * Satellite and Terrain basemaps ([onToggleBasemap]).
 *
 * @param pendingIds entities with pending uploads, drawn in the pending style.
 */
@Composable
internal fun GeoPointFormMap(
  mapUiState: SurveyMapUiState,
  uiState: DataCollectionUiState,
  pendingIds: Set<String>,
  viewportState: GeoPointMapViewportState,
  onToggleBasemap: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val currentMapUiState by rememberUpdatedState(mapUiState)
  val viewport by rememberUpdatedState(viewportState)
  val camera =
    rememberSurveyMapCamera(
      desired = { geoPointCamera(currentMapUiState, viewport) },
      onSettled = { writeBackGeoPointCamera(currentMapUiState, viewport, it) },
      isFollowingUser = { !viewport.isPanned },
    )
  Box(modifier = modifier) {
    SurveyAppMap(
      map =
        SurveyMapContent.geoPointForm(
          uiState = mapUiState,
          dataCollection = uiState,
          pendingIds = pendingIds,
          isFollowingUser = !viewportState.isPanned,
        ),
      camera = camera,
      modifier = Modifier.fillMaxSize(),
    ) { _, _ ->
    }
    FormMapBasemapToggle(mapUiState = mapUiState, onToggle = onToggleBasemap)
  }
}

/** The basemap toggle in the bottom-right corner of a data collection form's map. */
@Composable
private fun BoxScope.FormMapBasemapToggle(mapUiState: SurveyMapUiState, onToggle: () -> Unit) {
  BasemapToggleButton(
    selectedBasemapType = mapUiState.selectedBasemapType,
    onToggle = onToggle,
    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
  )
}

/**
 * The map in a form's `entityref` step, showing [form]'s candidate map features; tapping a
 * candidate picks it ([DataCollectionActions.selectEntityRefForActiveForm]). The open form's target
 * feature (preselected when the form opens, or picked on the map or from the list) is fitted into
 * the map, the same way the main map frames a selection. Clearing the selection leaves the camera
 * where it is.
 *
 * The camera follows the map viewport ([mapUiState], [mapActions]) like the main map, and a small
 * button in the bottom-right corner switches between the Satellite and Terrain basemaps.
 *
 * @param pendingIds entities with pending uploads, drawn in the pending style.
 */
@Composable
internal fun EntityRefFormMap(
  mapUiState: SurveyMapUiState,
  mapActions: SurveyMapActions,
  uiState: DataCollectionUiState,
  actions: DataCollectionActions,
  form: FormPreviewItem,
  pendingIds: Set<String>,
  modifier: Modifier = Modifier,
) {
  val camera = rememberSurveyMapCamera(uiState = mapUiState, actions = mapActions)
  val selected = uiState.activeDataCollectionEntity
  LaunchedEffect(selected?.id, uiState.entityRefFramingEpoch) {
    val entity = selected ?: return@LaunchedEffect
    if (!entity.hasGeometry) return@LaunchedEffect
    val bounds = EntityGeometry.bounds(entity) ?: return@LaunchedEffect
    camera.run {
      it.fitBounds(
        bounds,
        framingInsets(it.viewportSize, bottom = 0.dp),
        maxZoom = mapUiState.maxFramingZoom(entity),
      )
    }
  }
  Box(modifier = modifier) {
    SurveyAppMap(
      map =
        SurveyMapContent.entityRefForm(
          uiState = mapUiState,
          dataCollection = uiState,
          form = form,
          pendingIds = pendingIds,
        ),
      camera = camera,
      modifier = Modifier.fillMaxSize(),
    ) { tappedId, _ ->
      tappedId?.let(SurveyMapIds::entityIdOf)?.let(actions::selectEntityRefForActiveForm)
    }
    FormMapBasemapToggle(mapUiState = mapUiState, onToggle = mapActions::toggleBasemapType)
  }
}

/**
 * Where the GeoPoint map looks: the user's GPS location shifted by the question's pan offset (in
 * degrees), at the question's zoom.
 */
internal fun geoPointCamera(
  mapUiState: SurveyMapUiState,
  viewport: GeoPointMapViewportState,
): CameraPosition {
  val gps = gpsLatLng(mapUiState)
  return CameraPosition(
    center = LatLng(gps.latitude + viewport.panOffsetLat, gps.longitude + viewport.panOffsetLon),
    zoom = viewport.zoomLevel.toDouble(),
  )
}

/**
 * Reports a settled GeoPoint map camera to the question through its pan and zoom callbacks.
 *
 * `GeoPointInputWidget` expresses pan deltas in pixels over a viewport whose degree span is
 * `0.0016 * (17.5 / zoomLevel.coerceIn(13f, 20f))`; passing a `1×1` viewport normalizes pixel
 * deltas into fractions of that span. Drift below `1e-7°` (~1 cm) or `0.01` zoom levels is ignored
 * to prevent camera settle feedback loops.
 */
internal fun writeBackGeoPointCamera(
  mapUiState: SurveyMapUiState,
  viewport: GeoPointMapViewportState,
  camera: CameraPosition,
) {
  val expected = geoPointCamera(mapUiState, viewport)
  val dLat = camera.center.latitude - expected.center.latitude
  val dLon = camera.center.longitude - expected.center.longitude
  if (viewport.panAllowed && (abs(dLat) > 1e-7 || abs(dLon) > 1e-7)) {
    // Must match GeoPointInputWidget's span for its current zoom.
    val spanDegrees = 0.0016 * (17.5 / viewport.zoomLevel.coerceIn(13f, 20f))
    viewport.onPanDeltaPixels(
      (-dLon / spanDegrees).toFloat(),
      (dLat / spanDegrees).toFloat(),
      1f,
      1f,
    )
  }
  val dZoom = camera.zoom - viewport.zoomLevel
  if (abs(dZoom) > 0.01) viewport.onZoomDelta(dZoom.toFloat())
}

/** The collector's GPS location on the map viewport's survey anchor. */
internal fun gpsLatLng(mapUiState: SurveyMapUiState): LatLng =
  mapUiState.anchor.toLatLng(
    mapUiState.userGpsNormalizedX.toDouble(),
    mapUiState.userGpsNormalizedY.toDouble(),
  )
