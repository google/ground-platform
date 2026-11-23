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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.openforis.collectone.v2.app.domain.model.PlaceFraming
import org.openforis.collectone.v2.map.AppMap
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.map.MapEvent
import org.openforis.collectone.v2.map.MapInsets

/** Keeps a focused place clear of the floating search bar (top) and bottom sheet peek (bottom). */
private val PlaceFocusPadding = MapInsets(left = 40.dp, top = 68.dp, right = 40.dp, bottom = 108.dp)

/** Keeps framed bounds (e.g. a saved area) clear of the search bar and bottom sheet peek. */
private val FramedBoundsPadding =
  MapInsets(left = 32.dp, top = 96.dp, right = 32.dp, bottom = 136.dp)

/**
 * Stateless main survey map: shows [map] and routes taps to the map viewport's [actions].
 *
 * @param onDrawingTap when non-null, a form's geometry is being drawn (web dashboard) and every
 *   click on the map, feature or not, is a vertex at the tapped position; the selection stays as it
 *   is. Markers report no position and are ignored.
 * @param onFormGeometryTap when non-null, tapping a geometry answer of the open form (web
 *   dashboard) reports its question path instead of changing the selection.
 */
@Composable
fun SurveyMainMap(
  uiState: SurveyMapUiState,
  actions: SurveyMapActions,
  map: SurveyMap,
  camera: SurveyMapCameraController,
  modifier: Modifier = Modifier,
  collapseSheetOnBackgroundTap: Boolean = true,
  onDrawingTap: ((LatLng) -> Unit)? = null,
  onFormGeometryTap: ((path: String) -> Unit)? = null,
) {
  val place = uiState.selectedPlace
  LaunchedEffect(place?.id, place?.position, place?.targetZoom) {
    if (place == null) return@LaunchedEffect
    val focus = PlaceFraming.focus(place)
    camera.run { it.fitBounds(focus.bounds, PlaceFocusPadding, focus.maxZoom) }
  }
  val framed = uiState.framedBounds
  LaunchedEffect(uiState.framingEpoch) {
    if (framed == null) return@LaunchedEffect
    actions.clearFramedBounds()
    camera.run { it.fitBounds(framed, FramedBoundsPadding) }
  }

  SurveyAppMap(map = map, camera = camera, modifier = modifier) { tappedId, at ->
    if (onDrawingTap != null) {
      if (at != null) onDrawingTap(at)
      return@SurveyAppMap
    }
    val formPath = tappedId?.let(SurveyMapIds::formGeometryPathOf)
    if (formPath != null && onFormGeometryTap != null) {
      onFormGeometryTap(formPath)
      return@SurveyAppMap
    }
    val entityId = tappedId?.let(SurveyMapIds::entityIdOf)
    val clusterId = tappedId?.let(SurveyMapIds::clusterIdOf)
    when {
      tappedId == SurveyMapIds.USER || tappedId == SurveyMapIds.NAVIGATION -> {}
      tappedId != null && SurveyMapIds.isPlace(tappedId) -> actions.clearSelectedPlace()
      clusterId != null -> {
        actions.clearSelectedPlace()
        actions.selectCluster(clusterId)
      }
      entityId != null -> {
        actions.clearSelectedPlace()
        actions.selectEntity(entityId)
      }
      else -> {
        actions.clearSelectedPlace()
        actions.updateLayersSheetOpen(false)
        actions.selectCluster(null)
        if (collapseSheetOnBackgroundTap && uiState.isEntityBottomSheetExpanded) {
          actions.updateEntityBottomSheetExpanded(false)
        } else {
          actions.selectEntity(null)
        }
      }
    }
  }
}

/**
 * A [AppMap] showing [map] with the survey marker UI. [onTap] gets the tapped feature or marker id,
 * or `null` for the empty map, plus where the map was tapped (`null` for markers, which don't
 * report a position).
 */
@Composable
fun SurveyAppMap(
  map: SurveyMap,
  camera: SurveyMapCameraController,
  modifier: Modifier,
  onTap: (id: String?, at: LatLng?) -> Unit,
) {
  AppMap(
    content = map.content,
    cameraState = camera.camera,
    onEvent = { event ->
      when (event) {
        is MapEvent.CameraIdle -> camera.onCameraIdle(event)
        is MapEvent.FeatureTapped -> onTap(event.featureId, event.at)
        is MapEvent.MarkerTapped -> onTap(event.markerId, null)
        is MapEvent.BackgroundTapped -> onTap(null, event.at)
      }
    },
    modifier = modifier,
    markerContent = { marker -> map.markers[marker.id]?.let { SurveyMarkerView(it) } },
  )
}
