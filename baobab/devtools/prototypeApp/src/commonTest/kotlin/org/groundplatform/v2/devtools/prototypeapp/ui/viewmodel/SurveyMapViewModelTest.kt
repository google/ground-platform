/*
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.runNow
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.seededStore
import org.groundplatform.v2.devtools.prototypeapp.data.repository.LocationRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.OrganizationRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.PlaceRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SettingsRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.domain.model.BasemapType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LocationLockState
import org.groundplatform.v2.devtools.prototypeapp.domain.model.NavigationTargetKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OfflineBasemapStyle
import org.groundplatform.v2.devtools.prototypeapp.ui.state.SurveyMapEvent

/** See [OnboardingViewModelTest] for why fixtures are built outside `runNow`. */
class SurveyMapViewModelTest {
  private class Fixture {
    val store = seededStore()
    val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    val surveyRepository = SurveyRepositoryImpl(store)
    val locationRepository = LocationRepositoryImpl()
    val viewModel =
      SurveyMapViewModel(
        surveyRepository = surveyRepository,
        organizationRepository = OrganizationRepositoryImpl(store),
        settingsRepository = SettingsRepositoryImpl(store),
        locationRepository = locationRepository,
        placeRepository = PlaceRepositoryImpl(store),
        scope = scope,
      )
    val events = mutableListOf<SurveyMapEvent>()

    init {
      scope.launch { viewModel.events.collect { events += it } }
    }

    val uiState
      get() = viewModel.uiState.value
  }

  @Test
  fun initialState_followsUserAtDefaultZoom_withSeededMapContent() {
    val f = Fixture()
    assertTrue(f.uiState.isCameraFollowingUser)
    assertEquals(LocationLockState.LOCKED, f.uiState.locationLockState)
    assertEquals(0f, f.uiState.mapZoomDelta)
    assertFalse(f.uiState.isMapClusteringActive)
    assertTrue(f.uiState.mapLayers.isNotEmpty())
    assertTrue(f.uiState.visibleMapEntities.isNotEmpty())
    assertEquals(BasemapType.SATELLITE, f.uiState.selectedBasemapType)
    assertEquals(
      f.locationRepository.getLocationSnapshot().normalizedX,
      f.uiState.userGpsNormalizedX,
    )
  }

  @Test
  fun gpsUpdates_moveTheCameraWhileFollowing_andKeepTheViewportAfterPanning() {
    val f = Fixture()
    f.viewModel.updateUserGpsLocation(0.40f, 0.60f, "label")
    assertEquals(0.40f, f.uiState.userGpsNormalizedX)
    assertEquals(0.60f, f.uiState.userGpsNormalizedY)
    assertEquals("label", f.uiState.userGpsCoordinatesLabel)
    assertEquals(0f, f.uiState.mapPanOffsetX)
    assertEquals(0.40f, f.locationRepository.getLocationSnapshot().normalizedX)

    f.viewModel.panMap(0.10f, -0.05f)
    assertFalse(f.uiState.isCameraFollowingUser)
    assertEquals(LocationLockState.PANNED, f.uiState.locationLockState)
    assertEquals(0.10f, f.uiState.mapPanOffsetX)

    // While panned, moving the GPS keeps the viewport where it is (offset absorbs the move).
    f.viewModel.updateUserGpsLocation(0.50f, 0.60f)
    assertEquals(0.20f, f.uiState.mapPanOffsetX, 1e-5f)
    assertEquals("label", f.uiState.userGpsCoordinatesLabel)

    f.viewModel.recenterMapOnUser()
    assertTrue(f.uiState.isCameraFollowingUser)
    assertEquals(0f, f.uiState.mapPanOffsetX)
    assertEquals(0f, f.uiState.mapPanOffsetY)
  }

  @Test
  fun zoomSteps_areClampedAndActivateClustering() {
    val f = Fixture()
    repeat(4) { f.viewModel.zoomOutMap() }
    assertEquals(-3.0f, f.uiState.mapZoomDelta)
    assertTrue(f.uiState.isMapClusteringActive)
    assertTrue(f.uiState.mapFeatureClusters.isNotEmpty())
    assertEquals(
      f.uiState.visibleMapEntities.size,
      f.uiState.mapFeatureClusters.sumOf { it.totalCount },
    )

    repeat(10) { f.viewModel.zoomOutMap() }
    assertEquals(-5.0f, f.uiState.mapZoomDelta)
    repeat(20) { f.viewModel.zoomInMap() }
    assertEquals(3.7f, f.uiState.mapZoomDelta)
    assertFalse(f.uiState.isMapClusteringActive)

    f.viewModel.resetMapZoom()
    assertEquals(0f, f.uiState.mapZoomDelta)
  }

  @Test
  fun selectCluster_emitsEvent_andSelectingAgainZoomsIntoIt() {
    val f = Fixture()
    repeat(5) { f.viewModel.zoomOutMap() }
    val cluster = f.uiState.mapFeatureClusters.first()

    f.viewModel.selectCluster(cluster.id)
    assertEquals(cluster.id, f.uiState.selectedClusterId)
    assertEquals(SurveyMapEvent.ClusterSelected(cluster), f.events.last())

    val zoomBefore = f.uiState.mapZoomDelta
    f.viewModel.selectCluster(cluster.id)
    assertEquals(zoomBefore + 0.75f, f.uiState.mapZoomDelta)
    assertEquals(LocationLockState.PANNED, f.uiState.locationLockState)

    f.viewModel.selectCluster(null)
    assertNull(f.uiState.selectedClusterId)
  }

  @Test
  fun basemapAndOfflineStyle_stayCoupled() {
    val f = Fixture()
    f.viewModel.toggleBasemapType()
    assertEquals(BasemapType.NORMAL, f.uiState.selectedBasemapType)
    assertEquals(OfflineBasemapStyle.VECTOR_TOPO, f.uiState.offlineBasemapStyle)

    f.viewModel.toggleOfflineBasemapVisibility()
    assertFalse(f.uiState.isOfflineBasemapVisible)

    f.viewModel.updateOfflineBasemapStyle(OfflineBasemapStyle.SATELLITE_HYBRID)
    assertEquals(BasemapType.SATELLITE, f.uiState.selectedBasemapType)
    assertTrue(f.uiState.isOfflineBasemapVisible)
  }

  @Test
  fun imagerySources_toggleOnAndOff() {
    val f = Fixture()
    val source = f.uiState.availableImagerySources.firstOrNull() ?: return
    assertFalse(f.uiState.isImagerySourceEnabled(source.id))
    f.viewModel.toggleImagerySource(source.id)
    assertTrue(f.uiState.isImagerySourceEnabled(source.id))
    assertEquals(listOf(source), f.uiState.enabledImagerySources)
    f.viewModel.toggleImagerySource(source.id)
    assertFalse(f.uiState.isImagerySourceEnabled(source.id))
  }

  @Test
  fun selectEntity_emitsEvent_bumpsEpoch_andClearsSelectedPlace() {
    val f = Fixture()
    val place = assertNotNull(f.viewModel.findPlaceById("place-karima-forest"))
    f.viewModel.selectPlace(place)
    assertEquals("place-karima-forest", f.uiState.selectedPlace?.id)
    assertEquals(SurveyMapEvent.PlaceSelected(place.resolvedForSelection()), f.events.last())
    assertEquals(LocationLockState.PANNED, f.uiState.locationLockState)
    assertFalse(f.uiState.isCameraFollowingUser)

    f.viewModel.updateLayersSheetOpen(true)
    f.viewModel.selectEntity("entity-nyr-104")
    assertEquals("entity-nyr-104", f.uiState.selectedEntityId)
    assertEquals("entity-nyr-104", f.uiState.selectedEntity?.id)
    assertEquals(1L, f.uiState.entitySelectionEpoch)
    assertNull(f.uiState.selectedPlace)
    assertFalse(f.uiState.isLayersSheetOpen)
    assertEquals(SurveyMapEvent.EntitySelected("entity-nyr-104"), f.events.last())

    f.viewModel.toggleEntityBottomSheetExpanded()
    assertTrue(f.uiState.isEntityBottomSheetExpanded)

    f.viewModel.selectEntity(null)
    assertNull(f.uiState.selectedEntityId)
    assertFalse(f.uiState.isEntityBottomSheetExpanded)
    assertEquals(1L, f.uiState.entitySelectionEpoch)
    assertEquals(SurveyMapEvent.EntitySelected(null), f.events.last())
  }

  @Test
  fun hidingTheSelectedFeaturesLayer_clearsSelection_andPersistsVisibility() {
    val f = Fixture()
    f.viewModel.selectEntity("entity-nyr-104")
    f.viewModel.toggleLayerVisibility("layer-coffee-parcels")

    assertNull(f.uiState.selectedEntityId)
    assertEquals(SurveyMapEvent.SelectionCleared, f.events.last())
    assertFalse(f.uiState.visibleLayerIds.contains("layer-coffee-parcels"))
    assertTrue(f.uiState.visibleMapEntities.none { it.layerId == "layer-coffee-parcels" })
    val stored = runNow { f.surveyRepository.getMapLayers() }
    assertFalse(stored.first { it.id == "layer-coffee-parcels" }.isVisible)

    f.viewModel.toggleLayerVisibility("layer-coffee-parcels")
    assertTrue(f.uiState.visibleLayerIds.contains("layer-coffee-parcels"))
  }

  @Test
  fun navigationToEntity_startsStopsAndStepsTheUser() {
    val f = Fixture()
    f.viewModel.panMap(0.2f, 0.2f)
    f.viewModel.startNavigationToEntity("entity-nyr-104")

    val nav = assertNotNull(f.uiState.activeNavigation)
    assertEquals(NavigationTargetKind.ENTITY, nav.targetKind)
    assertEquals("entity-nyr-104", nav.targetId)
    assertTrue(f.uiState.isNavigatingToEntity("entity-nyr-104"))
    assertEquals("entity-nyr-104", f.uiState.selectedEntityId)
    assertTrue(f.uiState.isCameraFollowingUser)
    val started = assertIs<SurveyMapEvent.NavigationStarted>(f.events.last())
    assertNull(started.submissionId)
    assertTrue(started.notice.startsWith("Straight-line navigation to "))

    val distanceBefore = nav.vector.distanceMeters
    f.viewModel.stepUserTowardNavigationTarget()
    val after = assertNotNull(f.uiState.activeNavigation)
    assertTrue(after.vector.distanceMeters < distanceBefore)

    f.viewModel.toggleNavigationToEntity("entity-nyr-104")
    assertNull(f.uiState.activeNavigation)
    assertNull(f.uiState.navigationTargetKind)
    assertEquals(SurveyMapEvent.NavigationStopped, f.events.last())
  }

  @Test
  fun navigationToPlace_keepsTargetAfterPlaceDeselection() {
    val f = Fixture()
    val place = assertNotNull(f.viewModel.findPlaceById("place-karima-forest"))
    f.viewModel.startNavigationToPlace(place)
    assertTrue(f.uiState.isNavigatingToPlace("place-karima-forest"))
    assertEquals("place-karima-forest", f.uiState.selectedPlace?.id)

    f.viewModel.clearSelectedPlace()
    assertNull(f.uiState.selectedPlace)
    assertEquals(NavigationTargetKind.PLACE, assertNotNull(f.uiState.activeNavigation).targetKind)

    f.viewModel.stopNavigation()
    assertNull(f.uiState.activeNavigation)
  }

  @Test
  fun reset_restoresSessionDefaults_andDeviceLocation() {
    val f = Fixture()
    f.viewModel.updateUserGpsLocation(0.30f, 0.30f)
    f.viewModel.panMap(0.1f, 0.1f)
    f.viewModel.zoomOutMap()
    f.viewModel.toggleBasemapType()
    f.viewModel.selectEntity("entity-nyr-104")

    f.viewModel.reset()

    val defaults = LocationRepositoryImpl().getLocationSnapshot()
    assertEquals(defaults.normalizedX, f.uiState.userGpsNormalizedX)
    assertEquals(defaults.normalizedY, f.uiState.userGpsNormalizedY)
    assertTrue(f.uiState.isCameraFollowingUser)
    assertEquals(0f, f.uiState.mapZoomDelta)
    assertEquals(BasemapType.SATELLITE, f.uiState.selectedBasemapType)
    assertTrue(f.uiState.isOnline)
    assertNull(f.uiState.selectedEntityId)
  }
}
