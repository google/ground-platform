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

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.domain.model.editor.toLatLng
import org.openforis.collectone.v2.app.domain.model.geometryType
import org.openforis.collectone.v2.app.domain.model.hasGeometry
import org.openforis.collectone.v2.app.domain.model.maxFramingZoom
import org.openforis.collectone.v2.app.ui.datacollection.SurveyMainMap
import org.openforis.collectone.v2.app.ui.session.TestAppSession
import org.openforis.collectone.v2.map.CameraPosition
import org.openforis.collectone.v2.map.LatLng

@OptIn(ExperimentalTestApi::class)
class SurveyMapCameraControllerTest {

  private fun assertNear(expected: Double, actual: Double, tolerance: Double = 1e-5) =
    assertTrue(abs(expected - actual) <= tolerance, "expected $expected but was $actual")

  @Test
  fun recenterMapOnUser_movesCameraBackToUserGpsAfterPanning() =
    runDesktopComposeUiTest(width = 400, height = 800) {
      val state = TestAppSession(sampleDataHolder())
      var controller: SurveyMapCameraController? = null

      setContent {
        val uiState by state.viewModels.surveyMap.uiState.collectAsState()
        val remembered =
          rememberSurveyMapCamera(uiState = uiState, actions = state.viewModels.surveyMap)
        SurveyMainMap(
          uiState = uiState,
          actions = state.viewModels.surveyMap,
          dataCollection = state.viewModels.dataCollection.uiState.value,
          formMap = state.viewModels.dataCollection,
          pendingIds = state.viewModels.dashboard.uiState.value.pendingUploadEntityIds,
          camera = remembered,
          modifier = Modifier.size(400.dp, 800.dp),
        )
        controller = remembered
      }
      waitForIdle()

      val mapCamera = assertNotNull(controller)
      val gpsCenter =
        state.viewModels.surveyMap.uiState.value.anchor.toLatLng(
          state.viewModels.surveyMap.uiState.value.userGpsNormalizedX.toDouble(),
          state.viewModels.surveyMap.uiState.value.userGpsNormalizedY.toDouble(),
        )
      assertNear(gpsCenter.latitude, mapCamera.camera.position.center.latitude)
      assertNear(gpsCenter.longitude, mapCamera.camera.position.center.longitude)

      // Simulate panning the map away from the user's GPS position via a gesture settling.
      val pannedPosition =
        CameraPosition(
          center = LatLng(gpsCenter.latitude + 0.004, gpsCenter.longitude - 0.003),
          zoom = mapCamera.camera.position.zoom,
        )
      mapCamera.camera.move(pannedPosition)
      state.viewModels.surveyMap.syncMapCamera(pannedPosition)
      waitForIdle()
      assertFalse(state.viewModels.surveyMap.uiState.value.isCameraFollowingUser)
      assertNear(pannedPosition.center.latitude, mapCamera.camera.position.center.latitude)
      assertNear(pannedPosition.center.longitude, mapCamera.camera.position.center.longitude)

      // Tap "Recenter" on mobile: camera must animate back to the user's GPS location.
      state.viewModels.surveyMap.recenterMapOnUser()
      mainClock.advanceTimeBy(1_000)
      waitForIdle()

      assertTrue(state.viewModels.surveyMap.uiState.value.isCameraFollowingUser)
      assertNear(gpsCenter.latitude, mapCamera.camera.position.center.latitude)
      assertNear(gpsCenter.longitude, mapCamera.camera.position.center.longitude)
    }

  @Test
  fun recenterMapOnUser_cancelsActiveEntityFitAndRecentersOnGps() =
    runDesktopComposeUiTest(width = 400, height = 800) {
      val state = TestAppSession(sampleDataHolder())
      var controller: SurveyMapCameraController? = null

      setContent {
        val uiState by state.viewModels.surveyMap.uiState.collectAsState()
        val remembered =
          rememberSurveyMapCamera(uiState = uiState, actions = state.viewModels.surveyMap)
        SurveyMainMap(
          uiState = uiState,
          actions = state.viewModels.surveyMap,
          dataCollection = state.viewModels.dataCollection.uiState.value,
          formMap = state.viewModels.dataCollection,
          pendingIds = state.viewModels.dashboard.uiState.value.pendingUploadEntityIds,
          camera = remembered,
          modifier = Modifier.size(400.dp, 800.dp),
        )
        controller = remembered
      }
      waitForIdle()

      val mapCamera = assertNotNull(controller)
      val entity =
        state.viewModels.surveyMap.uiState.value.visibleMapEntities.first { it.hasGeometry }
      val gpsCenter =
        state.viewModels.surveyMap.uiState.value.anchor.toLatLng(
          state.viewModels.surveyMap.uiState.value.userGpsNormalizedX.toDouble(),
          state.viewModels.surveyMap.uiState.value.userGpsNormalizedY.toDouble(),
        )

      mainClock.autoAdvance = false
      state.viewModels.surveyMap.recenterMapOnEntity(entity, targetScreenY = 0.28f)
      val bounds = checkNotNull(EntityGeometry.bounds(entity))
      mapCamera.run {
        it.fitBounds(
          bounds,
          framingInsets(it.viewportSize, bottom = 360.dp),
          maxZoom = entity.geometryType.maxFramingZoom.toDouble(),
        )
      }
      mainClock.advanceTimeByFrame()
      mainClock.advanceTimeBy(100)
      assertFalse(state.viewModels.surveyMap.uiState.value.isCameraFollowingUser)

      // Recenter while the entity framing move is still in flight.
      state.viewModels.surveyMap.recenterMapOnUser()
      mainClock.advanceTimeBy(1_000)
      mainClock.autoAdvance = true
      waitForIdle()

      assertTrue(state.viewModels.surveyMap.uiState.value.isCameraFollowingUser)
      assertNear(gpsCenter.latitude, mapCamera.camera.position.center.latitude)
      assertNear(gpsCenter.longitude, mapCamera.camera.position.center.longitude)
    }

  @Test
  fun updateUserGpsLocation_movesCameraWhileFollowingUser() =
    runDesktopComposeUiTest(width = 400, height = 800) {
      val state = TestAppSession(sampleDataHolder())
      var controller: SurveyMapCameraController? = null

      setContent {
        val uiState by state.viewModels.surveyMap.uiState.collectAsState()
        val remembered =
          rememberSurveyMapCamera(uiState = uiState, actions = state.viewModels.surveyMap)
        SurveyMainMap(
          uiState = uiState,
          actions = state.viewModels.surveyMap,
          dataCollection = state.viewModels.dataCollection.uiState.value,
          formMap = state.viewModels.dataCollection,
          pendingIds = state.viewModels.dashboard.uiState.value.pendingUploadEntityIds,
          camera = remembered,
          modifier = Modifier.size(400.dp, 800.dp),
        )
        controller = remembered
      }
      waitForIdle()

      val mapCamera = assertNotNull(controller)
      state.viewModels.surveyMap.updateUserGpsLocation(0.35f, 0.65f)
      mainClock.advanceTimeBy(1_000)
      waitForIdle()

      val expectedGps = state.viewModels.surveyMap.uiState.value.anchor.toLatLng(0.35, 0.65)
      assertNear(expectedGps.latitude, mapCamera.camera.position.center.latitude)
      assertNear(expectedGps.longitude, mapCamera.camera.position.center.longitude)
    }
}
