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
import org.openforis.collectone.v2.app.domain.model.LocationLockState
import org.openforis.collectone.v2.app.domain.model.NavigationTargetKind
import org.openforis.collectone.v2.app.domain.model.OfflineRegionMode
import org.openforis.collectone.v2.app.domain.model.position
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_map_navigation_started_submission
import org.openforis.collectone.v2.app.testing.FakeOfflineMapRepository
import org.openforis.collectone.v2.app.ui.text.UiText
import org.openforis.collectone.v2.map.CameraPosition
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.map.LngLatBounds

/**
 * [SurveyMapViewModel] on the sample data: how its intents change the UI state and which events
 * they emit. The pure parts are covered by [SurveyMapSessionTest] and
 * [SurveyMapUiStateBuilderTest].
 */
class SurveyMapViewModelIntentsTest {
  @Test
  fun desiredCamera_andSyncedCamera_roundTrip_withoutPanning() {
    val f = MapViewModelFixture()
    val desired = f.viewModel.desiredMapCamera()
    assertEquals(f.uiState.anchor.zoom, desired.zoom, 1e-9)

    f.viewModel.syncMapCamera(desired.copy(zoom = desired.zoom - 2))
    assertTrue(f.uiState.isCameraFollowingUser)
    assertEquals(-2f, f.uiState.mapZoomDelta)
  }

  @Test
  fun syncedCamera_elsewhere_pansTheMap() {
    val f = MapViewModelFixture()
    val anchor = f.uiState.anchor
    f.viewModel.syncMapCamera(CameraPosition(anchor.toLatLng(0.8, 0.2), anchor.zoom))
    assertFalse(f.uiState.isCameraFollowingUser)
    assertEquals(LocationLockState.PANNED, f.uiState.locationLockState)
    val center = f.viewModel.desiredMapCamera().center
    assertEquals(anchor.toLatLng(0.8, 0.2).longitude, center.longitude, 1e-6)
  }

  @Test
  fun syncedZoom_ignoresNaN_andClampsToTheMapsRange() {
    val f = MapViewModelFixture()
    f.viewModel.syncMapZoomDelta(Float.NaN)
    assertEquals(0f, f.uiState.mapZoomDelta)
    f.viewModel.syncMapZoomDelta(-50f)
    assertEquals(-13f, f.uiState.mapZoomDelta)
  }

  @Test
  fun panMap_withoutMovement_keepsFollowingTheUser() {
    val f = MapViewModelFixture()
    f.viewModel.panMap(0f, 0f)
    assertTrue(f.uiState.isCameraFollowingUser)
    f.viewModel.zoomMapBy(0f)
    assertEquals(0f, f.uiState.mapZoomDelta)
  }

  @Test
  fun recenterOnEntity_centersItsLabelPosition_atTheRequestedHeight() {
    val f = MapViewModelFixture()
    val entity = f.uiState.entities.first { it.id == SEEDED_ENTITY_ID }
    f.viewModel.recenterMapOnEntity(SEEDED_ENTITY_ID, targetScreenY = 0.30f)
    assertFalse(f.uiState.isCameraFollowingUser)

    val (lng, lat) = f.viewModel.resolveEntityLngLat(entity)
    val (nx, ny) = f.uiState.anchor.toNormalized(LatLng(lat, lng))
    assertEquals(nx.toFloat(), f.uiState.userGpsNormalizedX - f.uiState.mapPanOffsetX, 1e-5f)
    // The screen center sits 0.2 below the feature, so the feature shows at 30% of the height.
    assertEquals(
      ny.toFloat() + 0.20f,
      f.uiState.userGpsNormalizedY - f.uiState.mapPanOffsetY,
      1e-5f,
    )

    // Unknown features leave the camera alone.
    f.viewModel.recenterMapOnUser()
    f.viewModel.recenterMapOnEntity("unknown")
    assertTrue(f.uiState.isCameraFollowingUser)
  }

  @Test
  fun zoomIntoAnUnknownCluster_zoomsInOneStep() {
    val f = MapViewModelFixture()
    f.viewModel.zoomIntoCluster("no-such-cluster")
    assertEquals(0.75f, f.uiState.mapZoomDelta)
    assertNull(f.uiState.selectedClusterId)

    f.viewModel.selectCluster("no-such-cluster")
    assertNull(f.uiState.selectedClusterId)
  }

  @Test
  fun setSelectedEntity_bumpsTheEpochOnlyWhenAsked_andEmitsNothing() = runTest {
    val f = MapViewModelFixture()
    f.viewModel.events.test {
      f.viewModel.setSelectedEntity(SEEDED_ENTITY_ID)
      assertEquals(SEEDED_ENTITY_ID, f.uiState.selectedEntityId)
      assertEquals(0L, f.uiState.entitySelectionEpoch)
      f.viewModel.setSelectedEntity(SEEDED_ENTITY_ID, bumpSelectionEpoch = true)
      assertEquals(1L, f.uiState.entitySelectionEpoch)
      expectNoEvents()
    }
  }

  @Test
  fun selectPlace_pansToIt_andClampsItsZoom() = runTest {
    val f = MapViewModelFixture()
    val place = assertNotNull(f.viewModel.findPlaceById(SEEDED_PLACE_ID)).copy(targetZoom = 30f)
    f.viewModel.events.test {
      f.viewModel.updateLayersSheetOpen(true)
      f.viewModel.selectPlace(place)
      val selected = assertIs<SurveyMapEvent.PlaceSelected>(awaitItem()).place
      assertEquals(SEEDED_PLACE_ID, f.uiState.selectedPlace?.id)
      assertFalse(f.uiState.isLayersSheetOpen)
      assertTrue(f.uiState.mapZoomDelta <= 3.2f)
      assertEquals(selected.targetZoom - f.uiState.anchor.zoom.toFloat(), f.uiState.mapZoomDelta)
    }
  }

  @Test
  fun navigationToSubmission_selectsItsFeature_showsItsLayer_andToggles() = runTest {
    val f = MapViewModelFixture()
    f.viewModel.updateUserGpsLocation(0.50f, 0.50f)
    val entity = f.uiState.entities.first { it.submissions.isNotEmpty() }
    val submission = entity.submissions.first()
    runNow {
      f.surveyRepository.setMapLayers(
        f.surveyRepository.getMapLayers().map {
          if (it.id == entity.layerId) it.copy(isVisible = false) else it
        }
      )
    }
    assertFalse(entity.layerId in f.uiState.visibleLayerIds)

    f.viewModel.events.test {
      f.viewModel.toggleNavigationToSubmission(submission.id)
      val started = assertIs<SurveyMapEvent.NavigationStarted>(awaitItem())
      assertEquals(submission.id, started.submissionId)
      assertEquals(
        Res.string.app_map_navigation_started_submission,
        assertIs<UiText.Resource>(started.notice).resource,
      )
      assertTrue(f.uiState.isNavigatingToSubmission(submission.id))
      assertEquals(entity.id, f.uiState.selectedEntityId)
      assertTrue(entity.layerId in f.uiState.visibleLayerIds)
      assertEquals(NavigationTargetKind.SUBMISSION, f.uiState.activeNavigation?.targetKind)
      assertNotNull(f.viewModel.distanceAndBearingToSubmission(submission.id))

      f.viewModel.toggleNavigationToSubmission(submission.id)
      assertEquals(SurveyMapEvent.NavigationStopped, awaitItem())
      assertNull(f.uiState.navigationTargetKind)
    }
  }

  @Test
  fun navigationToUnknownTargets_doesNothing() = runTest {
    val f = MapViewModelFixture()
    f.viewModel.events.test {
      f.viewModel.startNavigationToEntity("unknown")
      f.viewModel.startNavigationToSubmission("unknown")
      f.viewModel.stepUserTowardNavigationTarget()
      expectNoEvents()
    }
    assertNull(f.uiState.navigationTargetKind)
  }

  @Test
  fun steppingTowardTheTarget_eventuallyArrivesOnIt() {
    val f = MapViewModelFixture()
    f.viewModel.updateUserGpsLocation(0.50f, 0.50f)
    f.viewModel.startNavigationToEntity(SEEDED_ENTITY_ID)
    repeat(30) { f.viewModel.stepUserTowardNavigationTarget(stepFraction = 1f) }
    val nav = assertNotNull(f.uiState.activeNavigation)
    assertTrue(nav.vector.distanceMeters <= 18)
  }

  @Test
  fun wayfindingBadges_measureFromTheUser() {
    val f = MapViewModelFixture()
    assertNull(f.viewModel.distanceAndBearingToEntity(SEEDED_ENTITY_ID))
    f.viewModel.updateUserGpsLocation(0.50f, 0.50f)
    val toEntity = assertNotNull(f.viewModel.distanceAndBearingToEntity(SEEDED_ENTITY_ID))
    assertNull(f.viewModel.distanceAndBearingToEntity("unknown"))
    assertNull(f.viewModel.distanceAndBearingToSubmission("unknown"))

    val place = assertNotNull(f.viewModel.findPlaceById(SEEDED_PLACE_ID))
    val (placeX, placeY) = f.uiState.anchor.positionOf(place.position)
    val toPlace = assertNotNull(f.viewModel.distanceAndBearingToPlace(place))
    assertEquals(
      f.viewModel.computeStraightLineVector(placeX, placeY).distanceMeters,
      toPlace.distanceMeters,
    )

    // Walking onto the feature brings the distance down.
    val entity = f.uiState.entities.first { it.id == SEEDED_ENTITY_ID }
    val (x, y) = f.uiState.anchor.positionOf(assertNotNull(entity.position))
    f.viewModel.updateUserGpsLocation(x, y)
    val after = assertNotNull(f.viewModel.distanceAndBearingToEntity(SEEDED_ENTITY_ID))
    assertTrue(after.distanceMeters < toEntity.distanceMeters)
  }

  @Test
  fun geometryFields_ofSubmissions_resolveToTheirGeometry() {
    val f = MapViewModelFixture()
    f.viewModel.updateUserGpsLocation(0.50f, 0.50f)
    val data = seededMapData()
    val geometry = data.submissionGeometries.first()
    val submission = data.allSubmissions.first { it.id == geometry.submissionId }
    val field =
      submission.fields.first {
        f.viewModel.isSubmissionFieldGeometry(submission.id, it)
      }
    assertNotNull(f.viewModel.distanceAndBearingToSubmissionField(submission.id, field))
    assertNotNull(f.viewModel.resolveSubmissionTargetGeometry(submission.id))
    submission.fields
      .filterNot { f.viewModel.isSubmissionFieldGeometry(submission.id, it) }
      .forEach { assertNull(f.viewModel.distanceAndBearingToSubmissionField(submission.id, it)) }
  }

  @Test
  fun framingBounds_stopsFollowing_andBumpsTheEpochEachTime() {
    val f = MapViewModelFixture(FakeOfflineMapRepository(regionMode = OfflineRegionMode.SIMULATED))
    val bounds = LngLatBounds(west = 36.90, south = -0.45, east = 36.95, north = -0.40)
    f.viewModel.frameBounds(bounds)
    f.viewModel.frameBounds(bounds)
    assertEquals(2L, f.uiState.framingEpoch)
    assertFalse(f.uiState.isCameraFollowingUser)
    assertTrue(f.uiState.canSaveMapAreas)

    f.viewModel.setOfflineAreaSelectionActive(false)
    assertFalse(f.uiState.isSelectingOfflineArea)
  }
}
