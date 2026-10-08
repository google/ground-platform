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
package org.groundplatform.v2.devtools.prototypeapp.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import org.groundplatform.v2.core.forms.ui.GeoPointMapViewportState
import org.groundplatform.v2.devtools.prototypeapp.PrototypeAppState
import org.groundplatform.v2.devtools.prototypeapp.domain.model.FormPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.PlaceFraming
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.toLatLng
import org.groundplatform.v2.devtools.prototypeapp.domain.model.geometryKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.hasGeometry
import org.groundplatform.v2.devtools.prototypeapp.domain.model.maxFramingZoom
import org.groundplatform.v2.devtools.prototypeapp.ui.state.DataCollectionUiState
import org.groundplatform.v2.devtools.prototypeapp.ui.state.SurveyMapUiState
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.DataCollectionActions
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.FormMapInteraction
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.SurveyMapActions
import org.groundplatform.v2.map.CameraPosition
import org.groundplatform.v2.map.GroundMap
import org.groundplatform.v2.map.LatLng
import org.groundplatform.v2.map.MapEvent
import org.groundplatform.v2.map.MapInsets

/** Keeps a focused place clear of the floating search bar (top) and bottom sheet peek (bottom). */
private val PlaceFocusPadding = MapInsets(left = 40.dp, top = 68.dp, right = 40.dp, bottom = 108.dp)

/**
 * The main survey map (mobile map view and web dashboard) built from the app shell; see the
 * overload taking the map viewport's and data collection's state.
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
    dataCollection = state.dataCollectionUiState,
    formMap = state.dataCollection,
    pendingIds = state.pendingUploadEntityIds,
    camera = camera,
    modifier = modifier,
    collapseSheetOnBackgroundTap = collapseSheetOnBackgroundTap,
    showNavigationOverlay = showNavigationOverlay,
  )
}

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
internal fun SurveyMainMap(
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

/** The map behind a form's GeoPoint question, built from the app shell. */
@Composable
internal fun GeoPointFormMap(
  state: PrototypeAppState,
  viewportState: GeoPointMapViewportState,
  modifier: Modifier = Modifier,
) {
  GeoPointFormMap(
    mapUiState = state.surveyMapUiState,
    uiState = state.dataCollectionUiState,
    pendingIds = state.pendingUploadEntityIds,
    viewportState = viewportState,
    modifier = modifier,
  )
}

/**
 * The map behind a form's GeoPoint question, following the question's pan and zoom. The camera
 * starts at the collector's GPS location from [mapUiState]; the open form's target feature from
 * [uiState] is highlighted.
 *
 * @param pendingIds entities with pending uploads, drawn in the pending style.
 */
@Composable
internal fun GeoPointFormMap(
  mapUiState: SurveyMapUiState,
  uiState: DataCollectionUiState,
  pendingIds: Set<String>,
  viewportState: GeoPointMapViewportState,
  modifier: Modifier = Modifier,
) {
  val viewport by rememberUpdatedState(viewportState)
  val camera =
    rememberSurveyMapCamera(
      desired = { geoPointCamera(mapUiState, viewport) },
      onSettled = { writeBackGeoPointCamera(mapUiState, viewport, it) },
    )
  SurveyGroundMap(
    map =
      SurveyMapContent.geoPointForm(
        uiState = mapUiState,
        dataCollection = uiState,
        pendingIds = pendingIds,
        isFollowingUser = !viewportState.isPanned,
      ),
    camera = camera,
    modifier = modifier,
  ) { _, _ ->
  }
}

/** The map in a form's `entityref` step, built from the app shell. */
@Composable
internal fun EntityRefFormMap(
  state: PrototypeAppState,
  form: FormPreviewItem,
  modifier: Modifier = Modifier,
) {
  EntityRefFormMap(
    mapUiState = state.surveyMapUiState,
    mapActions = state.surveyMap,
    uiState = state.dataCollectionUiState,
    actions = state.dataCollection,
    form = form,
    pendingIds = state.pendingUploadEntityIds,
    modifier = modifier,
  )
}

/**
 * The map in a form's `entityref` step, showing [form]'s candidate map features; tapping a
 * candidate picks it ([DataCollectionActions.selectEntityRefForActiveForm]). The open form's target
 * feature (preselected when the form opens, or picked on the map or from the list) is fitted into
 * the map, the same way the main map frames a selection. Clearing the selection leaves the camera
 * where it is.
 *
 * The camera follows the map viewport ([mapUiState], [mapActions]) like the main map.
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
  val camera =
    rememberSurveyMapCamera(
      desired = mapActions::desiredMapCamera,
      onSettled = mapActions::syncMapCamera,
    )
  val selected = uiState.activeDataCollectionEntity
  LaunchedEffect(selected?.id, uiState.entityRefFramingEpoch) {
    val entity = selected ?: return@LaunchedEffect
    if (!entity.hasGeometry) return@LaunchedEffect
    val bounds = EntityGeometry.bounds(entity, mapUiState.anchor)
    camera.run {
      it.fitBounds(
        bounds,
        framingInsets(it.viewportSize, bottom = 0.dp),
        maxZoom = entity.geometryKind.maxFramingZoom.toDouble(),
      )
    }
  }
  SurveyGroundMap(
    map =
      SurveyMapContent.entityRefForm(
        uiState = mapUiState,
        dataCollection = uiState,
        form = form,
        pendingIds = pendingIds,
      ),
    camera = camera,
    modifier = modifier,
  ) { tappedId, _ ->
    tappedId?.let(SurveyMapIds::entityIdOf)?.let(actions::selectEntityRefForActiveForm)
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
 * Reports a settled GeoPoint map camera to the question through its pan and zoom callbacks. The pan
 * callback takes pixel deltas; passing a 1×1 viewport makes them fractions of its span.
 */
private fun writeBackGeoPointCamera(
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
private fun gpsLatLng(mapUiState: SurveyMapUiState): LatLng =
  mapUiState.anchor.toLatLng(
    mapUiState.userGpsNormalizedX.toDouble(),
    mapUiState.userGpsNormalizedY.toDouble(),
  )
