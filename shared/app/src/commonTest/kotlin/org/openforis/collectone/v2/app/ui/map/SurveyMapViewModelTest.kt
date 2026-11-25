/*
 * Copyright 2026 The Collect One Authors.
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

package org.openforis.collectone.v2.app.ui.map

import app.cash.turbine.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore
import org.openforis.collectone.v2.app.data.repository.LocationRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.OrganizationRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.PlaceRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.SettingsRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.SurveyRepositoryImpl
import org.openforis.collectone.v2.app.domain.error.DomainResult
import org.openforis.collectone.v2.app.domain.model.BasemapType
import org.openforis.collectone.v2.app.domain.model.DataSize
import org.openforis.collectone.v2.app.domain.model.ImagerySource
import org.openforis.collectone.v2.app.domain.model.LocationLockState
import org.openforis.collectone.v2.app.domain.model.NavigationTargetKind
import org.openforis.collectone.v2.app.domain.model.OfflineAreaName
import org.openforis.collectone.v2.app.domain.model.OfflineImageryType
import org.openforis.collectone.v2.app.domain.model.OfflineRegionMode
import org.openforis.collectone.v2.app.domain.repository.ConnectivityRepository
import org.openforis.collectone.v2.app.domain.usecase.ClusterMapFeaturesUseCase
import org.openforis.collectone.v2.app.domain.usecase.ComputeWayfindingNavigationUseCase
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_map_navigation_started
import org.openforis.collectone.v2.app.testing.FakeOfflineMapRepository
import org.openforis.collectone.v2.app.ui.text.UiText
import org.openforis.collectone.v2.map.LngLatBounds

/**
 * Fixtures are built outside `runNow` on purpose: the view model's `Dispatchers.Unconfined` scope
 * only runs nested coroutines after the current one yields, so a fixture built inside an Unconfined
 * block would not see its own state updates until the block ends.
 */
class SurveyMapViewModelTest {
  private class Fixture(regionMode: OfflineRegionMode = OfflineRegionMode.SIMULATED) {
    val offlineMaps = FakeOfflineMapRepository(regionMode = regionMode)
    val store = seededStore()
    val surveyRepository = SurveyRepositoryImpl(store)
    val locationRepository = LocationRepositoryImpl()
    val viewModel =
      SurveyMapViewModel(
        surveyRepository = surveyRepository,
        organizationRepository = OrganizationRepositoryImpl(store),
        settingsRepository = SettingsRepositoryImpl(store),
        locationRepository = locationRepository,
        placeRepository = PlaceRepositoryImpl(store),
        connectivityRepository = ConnectivityRepository(),
        clusterMapFeatures = ClusterMapFeaturesUseCase(),
        computeWayfindingNavigation = ComputeWayfindingNavigationUseCase(),
        offlineMapRepository = offlineMaps,
      )

    val uiState
      get() = viewModel.uiState.value
  }

  private val areaBounds = LngLatBounds(west = 36.90, south = -0.45, east = 36.95, north = -0.40)

  @Test
  fun savedAreas_reachTheMapOnceReady_byImageryType() {
    val f = Fixture()
    val id = runNow {
      (f.offlineMaps.save(
          areaBounds,
          OfflineAreaName.Named("Othaya"),
          OfflineImageryType.entries.toSet(),
        ) as DomainResult.Success)
        .value
    }
    // Saving layers aren't drawn yet.
    assertTrue(f.uiState.savedSatelliteAreas.isEmpty())

    f.offlineMaps.complete(id)
    assertEquals(
      listOf(SavedImageryArea(id, areaBounds, DataSize.ofMegabytes(38))),
      f.uiState.savedSatelliteAreas,
    )
    assertEquals(listOf(id), f.uiState.savedTerrainAreas.map { it.id })
    assertEquals(DataSize.ofMegabytes(10), f.uiState.savedSize(OfflineImageryType.TERRAIN))
    assertTrue(f.uiState.canSaveMapAreas)
  }

  @Test
  fun downloadedImagerySwitches_startOn_andToggleIndependently() {
    val f = Fixture()
    assertTrue(f.uiState.isDownloadedSatelliteVisible)
    assertTrue(f.uiState.isDownloadedTerrainVisible)

    f.viewModel.toggleDownloadedImagery(OfflineImageryType.SATELLITE)
    assertFalse(f.uiState.isDownloadedSatelliteVisible)
    assertTrue(f.uiState.isDownloadedTerrainVisible)

    f.viewModel.toggleDownloadedImagery(OfflineImageryType.SATELLITE)
    assertTrue(f.uiState.isDownloadedSatelliteVisible)
  }

  @Test
  fun aDeviceThatCantSaveAreas_hidesSaveAnArea() {
    assertFalse(Fixture(OfflineRegionMode.UNAVAILABLE).uiState.canSaveMapAreas)
  }

  @Test
  fun selectionMode_closesTheLayersSheet_andFramingIsAppliedOnce() {
    val f = Fixture()
    f.viewModel.updateLayersSheetOpen(true)
    f.viewModel.setOfflineAreaSelectionActive(true)
    assertTrue(f.uiState.isSelectingOfflineArea)
    assertFalse(f.uiState.isLayersSheetOpen)

    f.viewModel.frameBounds(areaBounds)
    assertEquals(areaBounds, f.uiState.framedBounds)
    val epoch = f.uiState.framingEpoch
    f.viewModel.clearFramedBounds()
    assertEquals(null, f.uiState.framedBounds)
    assertEquals(epoch, f.uiState.framingEpoch)
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
    assertEquals(BasemapType.NORMAL, f.uiState.selectedBasemapType)
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
    val clustersAt300 = f.uiState.mapFeatureClusters
    assertTrue(clustersAt300.size > 1)
    assertEquals(
      f.uiState.visibleMapEntities.size,
      clustersAt300.sumOf { it.totalCount },
    )

    f.viewModel.zoomOutMap()
    assertEquals(-3.75f, f.uiState.mapZoomDelta)
    val clustersAt375 = f.uiState.mapFeatureClusters
    assertTrue(clustersAt375.size < clustersAt300.size)

    f.viewModel.zoomOutMap()
    assertEquals(-4.5f, f.uiState.mapZoomDelta)
    val clustersAt450 = f.uiState.mapFeatureClusters
    assertEquals(1, clustersAt450.size)
    assertEquals(
      f.uiState.visibleMapEntities.size,
      clustersAt450.sumOf { it.totalCount },
    )

    repeat(9) { f.viewModel.zoomOutMap() }
    assertEquals(-8.0f, f.uiState.mapZoomDelta)
    repeat(20) { f.viewModel.zoomInMap() }
    assertEquals(3.7f, f.uiState.mapZoomDelta)
    assertFalse(f.uiState.isMapClusteringActive)

    f.viewModel.resetMapZoom()
    assertEquals(0f, f.uiState.mapZoomDelta)
  }

  @Test
  fun selectCluster_emitsEvent_andSingleClickZoomsIntoAndCentersCluster() = runTest {
    val f = Fixture()
    f.viewModel.events.test {
      repeat(5) { f.viewModel.zoomOutMap() }
      val initialZoom = f.uiState.mapZoomDelta
      assertEquals(-3.75f, initialZoom)
      val cluster = f.uiState.mapFeatureClusters.first()

      f.viewModel.selectCluster(cluster.id)
      assertEquals(SurveyMapEvent.ClusterSelected(cluster), expectMostRecentItem())
      assertTrue(f.uiState.mapZoomDelta >= initialZoom + 1.25f)
      assertEquals(LocationLockState.PANNED, f.uiState.locationLockState)
      assertFalse(f.uiState.isCameraFollowingUser)

      f.viewModel.selectCluster(null)
      assertNull(f.uiState.selectedClusterId)
    }
  }

  @Test
  fun toggleBasemapType_switchesBetweenSatelliteAndTerrain() {
    val f = Fixture()
    f.viewModel.toggleBasemapType()
    assertEquals(BasemapType.SATELLITE, f.uiState.selectedBasemapType)
    f.viewModel.toggleBasemapType()
    assertEquals(BasemapType.NORMAL, f.uiState.selectedBasemapType)
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
  fun pinnedScene_drawsAboveEnabledImagery_untilRemoved() {
    val f = Fixture()
    val source = f.uiState.availableImagerySources.firstOrNull()
    source?.let { f.viewModel.toggleImagerySource(it.id) }
    val scene =
      ImagerySource(
        id = "scene",
        name = "Landsat 8 2023-08-14",
        urlTemplate = "https://example.org/scene/{z}/{x}/{y}",
      )

    f.viewModel.showPinnedScene(scene)
    assertEquals(scene, f.uiState.enabledImagerySources.last())
    assertEquals(listOfNotNull(source, scene), f.uiState.enabledImagerySources)

    f.viewModel.showPinnedScene(null)
    assertEquals(listOfNotNull(source), f.uiState.enabledImagerySources)
  }

  @Test
  fun selectEntity_emitsEvent_bumpsEpoch_andClearsSelectedPlace() = runTest {
    val f = Fixture()
    f.viewModel.events.test {
      val place = assertNotNull(f.viewModel.findPlaceById("place-karima-forest"))
      f.viewModel.selectPlace(place)
      assertEquals("place-karima-forest", f.uiState.selectedPlace?.id)
      assertEquals(
        SurveyMapEvent.PlaceSelected(place.resolvedForSelection()),
        expectMostRecentItem(),
      )
      assertEquals(LocationLockState.PANNED, f.uiState.locationLockState)
      assertFalse(f.uiState.isCameraFollowingUser)

      f.viewModel.updateLayersSheetOpen(true)
      f.viewModel.selectEntity("00000007-640e-5ed7-9874-4d6b6717f21d")
      assertEquals("00000007-640e-5ed7-9874-4d6b6717f21d", f.uiState.selectedEntityId)
      assertEquals("00000007-640e-5ed7-9874-4d6b6717f21d", f.uiState.selectedEntity?.id)
      assertEquals(1L, f.uiState.entitySelectionEpoch)
      assertNull(f.uiState.selectedPlace)
      assertFalse(f.uiState.isLayersSheetOpen)
      assertEquals(
        SurveyMapEvent.EntitySelected("00000007-640e-5ed7-9874-4d6b6717f21d"),
        expectMostRecentItem(),
      )

      f.viewModel.toggleEntityBottomSheetExpanded()
      assertTrue(f.uiState.isEntityBottomSheetExpanded)

      f.viewModel.selectEntity(null)
      assertNull(f.uiState.selectedEntityId)
      assertFalse(f.uiState.isEntityBottomSheetExpanded)
      assertEquals(1L, f.uiState.entitySelectionEpoch)
      assertEquals(SurveyMapEvent.EntitySelected(null), expectMostRecentItem())
    }
  }

  @Test
  fun hidingTheSelectedFeaturesLayer_clearsSelection_andPersistsVisibility() = runTest {
    val f = Fixture()
    f.viewModel.events.test {
      f.viewModel.selectEntity("00000007-640e-5ed7-9874-4d6b6717f21d")
      f.viewModel.toggleLayerVisibility("layer-coffee-parcels")

      assertNull(f.uiState.selectedEntityId)
      assertEquals(SurveyMapEvent.SelectionCleared, expectMostRecentItem())
      assertFalse(f.uiState.visibleLayerIds.contains("layer-coffee-parcels"))
      assertTrue(f.uiState.visibleMapEntities.none { it.layerId == "layer-coffee-parcels" })
      val stored = runNow { f.surveyRepository.getMapLayers() }
      assertFalse(stored.first { it.id == "layer-coffee-parcels" }.isVisible)

      f.viewModel.toggleLayerVisibility("layer-coffee-parcels")
      assertTrue(f.uiState.visibleLayerIds.contains("layer-coffee-parcels"))
    }
  }

  @Test
  fun navigationToEntity_startsStopsAndStepsTheUser() = runTest {
    val f = Fixture()
    f.viewModel.updateUserGpsLocation(0.50f, 0.50f)
    f.viewModel.events.test {
      f.viewModel.panMap(0.2f, 0.2f)
      f.viewModel.startNavigationToEntity("00000007-640e-5ed7-9874-4d6b6717f21d")

      val nav = assertNotNull(f.uiState.activeNavigation)
      assertEquals(NavigationTargetKind.ENTITY, nav.targetKind)
      assertEquals("00000007-640e-5ed7-9874-4d6b6717f21d", nav.targetId)
      assertTrue(f.uiState.isNavigatingToEntity("00000007-640e-5ed7-9874-4d6b6717f21d"))
      assertEquals("00000007-640e-5ed7-9874-4d6b6717f21d", f.uiState.selectedEntityId)
      assertTrue(f.uiState.isCameraFollowingUser)
      val started = assertIs<SurveyMapEvent.NavigationStarted>(expectMostRecentItem())
      assertNull(started.submissionId)
      assertEquals(
        Res.string.app_map_navigation_started,
        assertIs<UiText.Resource>(started.notice).resource,
      )

      val distanceBefore = nav.vector.distanceMeters
      f.viewModel.stepUserTowardNavigationTarget()
      val after = assertNotNull(f.uiState.activeNavigation)
      assertTrue(after.vector.distanceMeters < distanceBefore)

      f.viewModel.toggleNavigationToEntity("00000007-640e-5ed7-9874-4d6b6717f21d")
      assertNull(f.uiState.activeNavigation)
      assertNull(f.uiState.navigationTargetKind)
      assertEquals(SurveyMapEvent.NavigationStopped, expectMostRecentItem())
    }
  }

  @Test
  fun navigationToPlace_keepsTargetAfterPlaceDeselection() {
    val f = Fixture()
    f.viewModel.updateUserGpsLocation(0.50f, 0.50f)
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
    f.viewModel.selectEntity("00000007-640e-5ed7-9874-4d6b6717f21d")

    f.viewModel.reset()

    val defaults = LocationRepositoryImpl().getLocationSnapshot()
    assertEquals(defaults.normalizedX, f.uiState.userGpsNormalizedX)
    assertEquals(defaults.normalizedY, f.uiState.userGpsNormalizedY)
    assertTrue(f.uiState.isCameraFollowingUser)
    assertEquals(0f, f.uiState.mapZoomDelta)
    assertEquals(BasemapType.NORMAL, f.uiState.selectedBasemapType)
    assertTrue(f.uiState.isOnline)
    assertNull(f.uiState.selectedEntityId)
  }
}
