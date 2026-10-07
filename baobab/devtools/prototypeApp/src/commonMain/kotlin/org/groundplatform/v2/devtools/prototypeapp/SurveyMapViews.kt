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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import org.groundplatform.v2.core.forms.ui.GeoPointMapViewportState
import org.groundplatform.v2.devtools.prototypeapp.domain.model.PlaceFraming
import org.groundplatform.v2.devtools.prototypeapp.map.SurveyMap
import org.groundplatform.v2.devtools.prototypeapp.map.SurveyMapCameraController
import org.groundplatform.v2.devtools.prototypeapp.map.SurveyMapContent
import org.groundplatform.v2.devtools.prototypeapp.map.SurveyMapIds
import org.groundplatform.v2.devtools.prototypeapp.map.SurveyMarkerView
import org.groundplatform.v2.devtools.prototypeapp.map.framingInsets
import org.groundplatform.v2.devtools.prototypeapp.map.rememberSurveyMapCamera
import org.groundplatform.v2.devtools.prototypeapp.ui.state.SurveyMapUiState
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.SurveyMapActions
import org.groundplatform.v2.map.CameraPosition
import org.groundplatform.v2.map.GroundMap
import org.groundplatform.v2.map.LatLng
import org.groundplatform.v2.map.MapEvent
import org.groundplatform.v2.map.MapInsets

/** Keeps a focused place clear of the floating search bar (top) and bottom sheet peek (bottom). */
private val PlaceFocusPadding = MapInsets(left = 40.dp, top = 68.dp, right = 40.dp, bottom = 108.dp)

/**
 * The main survey map (mobile map view and web dashboard): the visible map features, clusters,
 * navigation, GPS location, and a place picked from search.
 *
 * [camera] is hoisted so screens can frame selections with it. When [collapseSheetOnBackgroundTap]
 * is `true` (mobile bottom sheet host), tapping the empty map first collapses an expanded sheet
 * before clearing the selection. Hosts without a bottom sheet (the web dashboard) pass `false` so a
 * background tap clears the selection immediately.
 */
@Composable
internal fun SurveyMainMap(
  state: PrototypeAppState,
  camera: SurveyMapCameraController,
  modifier: Modifier = Modifier,
  collapseSheetOnBackgroundTap: Boolean = true,
  showNavigationOverlay: Boolean = true,
) {
  val uiState by state.surveyMap.uiState.collectAsState()
  SurveyMainMap(
    uiState = uiState,
    actions = state.surveyMap,
    map = SurveyMapContent.main(state, showNavigationOverlay),
    camera = camera,
    modifier = modifier,
    collapseSheetOnBackgroundTap = collapseSheetOnBackgroundTap,
    onDrawingTap =
      if (state.webMapDrawing.isDrawing) {
        { at -> state.addWebMapDrawingVertex(at) }
      } else {
        null
      },
    onFormGeometryTap =
      if (state.isDataCollectionFormOpen) {
        { path -> state.focusWebFormQuestion(path) }
      } else {
        null
      },
  )
}

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
internal fun SurveyMainMap(
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
  LaunchedEffect(place?.id, place?.latitude, place?.longitude, place?.targetZoom) {
    if (place == null) return@LaunchedEffect
    val focus = PlaceFraming.focus(place)
    camera.run { it.fitBounds(focus.bounds, PlaceFocusPadding, focus.maxZoom) }
  }

  SurveyGroundMap(map = map, camera = camera, modifier = modifier) { tappedId, at ->
    if (onDrawingTap != null) {
      if (at != null) onDrawingTap(at)
      return@SurveyGroundMap
    }
    val formPath = tappedId?.let(SurveyMapIds::formGeometryPathOf)
    if (formPath != null && onFormGeometryTap != null) {
      onFormGeometryTap(formPath)
      return@SurveyGroundMap
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

/** The map behind a form's GeoPoint question, following the question's pan and zoom. */
@Composable
internal fun GeoPointFormMap(
  state: PrototypeAppState,
  viewportState: GeoPointMapViewportState,
  modifier: Modifier = Modifier,
) {
  val viewport by rememberUpdatedState(viewportState)
  val camera =
    rememberSurveyMapCamera(
      desired = { geoPointCamera(state, viewport) },
      onSettled = { writeBackGeoPointCamera(state, viewport, it) },
    )
  SurveyGroundMap(
    map = SurveyMapContent.geoPointForm(state, isFollowingUser = !viewportState.isPanned),
    camera = camera,
    modifier = modifier,
  ) { _, _ ->
  }
}

/**
 * The map in a form's entity-reference step; tapping a candidate selects it. The selected map
 * feature (preselected when the form opens, or picked on the map or from the list) is fitted into
 * the map, the same way the main map frames a selection. Clearing the selection leaves the camera
 * where it is.
 */
@Composable
internal fun EntityRefFormMap(
  state: PrototypeAppState,
  form: FormPreviewItem,
  modifier: Modifier = Modifier,
) {
  val camera =
    rememberSurveyMapCamera(desired = state::desiredMapCamera, onSettled = state::syncMapCamera)
  val selected = state.activeDataCollectionEntity
  LaunchedEffect(selected?.id, state.entityRefFramingEpoch) {
    val entity = selected ?: return@LaunchedEffect
    if (!entity.hasGeometry) return@LaunchedEffect
    val bounds = state.resolveEntityLngLatBounds(entity)
    camera.run {
      it.fitBounds(
        bounds,
        framingInsets(it.viewportSize, bottom = 0.dp),
        maxZoom = entity.geometryKind.maxFramingZoom.toDouble(),
      )
    }
  }
  SurveyGroundMap(
    map = SurveyMapContent.entityRefForm(state, form),
    camera = camera,
    modifier = modifier,
  ) { tappedId, _ ->
    tappedId?.let(SurveyMapIds::entityIdOf)?.let(state::selectEntityRefForActiveForm)
  }
}

/**
 * A [GroundMap] showing [map] with the survey marker UI. [onTap] gets the tapped feature or marker
 * id, or `null` for the empty map, plus where the map was tapped (`null` for markers, which don't
 * report a position).
 */
@Composable
private fun SurveyGroundMap(
  map: SurveyMap,
  camera: SurveyMapCameraController,
  modifier: Modifier,
  onTap: (id: String?, at: LatLng?) -> Unit,
) {
  GroundMap(
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

/**
 * Where the GeoPoint map looks: the user's GPS location shifted by the question's pan offset (in
 * degrees), at the question's zoom.
 */
private fun geoPointCamera(
  state: PrototypeAppState,
  viewport: GeoPointMapViewportState,
): CameraPosition {
  val gps = gpsLatLng(state)
  return CameraPosition(
    center = LatLng(gps.latitude + viewport.panOffsetLat, gps.longitude + viewport.panOffsetLon),
    zoom = viewport.zoomLevel.toDouble(),
  )
}

/**
 * Reports a settled GeoPoint map camera to the question through its pan and zoom callbacks. The pan
 * callback takes pixel deltas; passing a 1×1 viewport makes them fractions of its span.
 */
private fun writeBackGeoPointCamera(
  state: PrototypeAppState,
  viewport: GeoPointMapViewportState,
  camera: CameraPosition,
) {
  val expected = geoPointCamera(state, viewport)
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

private fun gpsLatLng(state: PrototypeAppState): LatLng =
  state.mapAnchor.toLatLng(state.userGpsNormalizedX.toDouble(), state.userGpsNormalizedY.toDouble())
