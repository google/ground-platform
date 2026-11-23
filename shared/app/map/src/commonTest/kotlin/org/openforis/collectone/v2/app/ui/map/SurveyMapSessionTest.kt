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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.domain.model.LocationLockState
import org.openforis.collectone.v2.app.domain.model.NavigationTargetKind
import org.openforis.collectone.v2.app.domain.model.SurveyMapAnchor
import org.openforis.collectone.v2.app.domain.model.SurveyPlaceItem
import org.openforis.collectone.v2.app.domain.repository.DeviceLocationSnapshot
import org.openforis.collectone.v2.map.CameraPosition
import org.openforis.collectone.v2.map.LatLng

class SurveyMapSessionTest {
  private val anchor = SurveyMapAnchor(LatLng(0.0, 0.0), zoom = 15.0, label = "")

  @Test
  fun pannedBy_stopsFollowing_andAccumulatesClampedOffsets() {
    val panned = SurveyMapSession().pannedBy(0.1f, -0.2f).pannedBy(0.1f, 0f)
    assertFalse(panned.isCameraFollowingUser)
    assertEquals(LocationLockState.PANNED, panned.locationLockState)
    assertEquals(0.2f, panned.mapPanOffsetX, 1e-6f)
    assertEquals(-0.2f, panned.mapPanOffsetY, 1e-6f)

    val far = SurveyMapSession().pannedBy(1e9f, -1e9f)
    assertEquals(SurveyMapSession.MAX_PAN, far.mapPanOffsetX)
    assertEquals(-SurveyMapSession.MAX_PAN, far.mapPanOffsetY)
  }

  @Test
  fun pannedTo_placesThePointAtTheTargetScreenHeight() {
    val session = SurveyMapSession(location = DeviceLocationSnapshot(0.5f, 0.5f))
    val centered = session.pannedTo(0.7f, 0.4f)
    assertEquals(-0.2f, centered.mapPanOffsetX, 1e-6f)
    assertEquals(0.1f, centered.mapPanOffsetY, 1e-6f)
    // Showing the point higher up (e.g. above a bottom sheet) shifts the offset by the difference.
    assertEquals(-0.1f, session.pannedTo(0.7f, 0.4f, targetScreenY = 0.3f).mapPanOffsetY, 1e-6f)
  }

  @Test
  fun recentered_followsTheUserAgain() {
    val recentered = SurveyMapSession().pannedBy(0.3f, 0.3f).recentered()
    assertTrue(recentered.isCameraFollowingUser)
    assertEquals(LocationLockState.LOCKED, recentered.locationLockState)
    assertEquals(0f, recentered.mapPanOffsetX)
    assertEquals(0f, recentered.mapPanOffsetY)
  }

  @Test
  fun zoomedTo_clampsToTheRange_andDropsTheClusterOnlyWhenLeavingClustering() {
    val clustered = SurveyMapSession(mapZoomDelta = -4f, selectedClusterId = "c")
    assertEquals(-8f, clustered.zoomedTo(-20f, SurveyMapSession.STEP_ZOOM_RANGE).mapZoomDelta)
    assertEquals("c", clustered.zoomedTo(-5f, SurveyMapSession.STEP_ZOOM_RANGE).selectedClusterId)

    val zoomedIn = clustered.zoomedTo(10f, SurveyMapSession.STEP_ZOOM_RANGE)
    assertEquals(3.7f, zoomedIn.mapZoomDelta)
    assertNull(zoomedIn.selectedClusterId)
  }

  @Test
  fun movedTo_movesTheCameraWithTheUserWhileFollowing() {
    val moved = SurveyMapSession().movedTo(0.4f, 0.6f, "here")
    assertEquals(0.4f, moved.userGpsNormalizedX)
    assertEquals(0.6f, moved.userGpsNormalizedY)
    assertEquals("here", moved.location.coordinatesLabel)
    assertEquals(0f, moved.mapPanOffsetX)
    assertEquals(0f, moved.mapPanOffsetY)
  }

  @Test
  fun movedTo_keepsAPannedViewportInPlace() {
    val panned =
      SurveyMapSession(location = DeviceLocationSnapshot(0.5f, 0.5f)).pannedBy(0.1f, 0.1f)
    val moved = panned.movedTo(0.6f, 0.45f, "there")
    // The screen center (gps - offset) stays at (0.4, 0.4).
    assertEquals(0.4f, moved.userGpsNormalizedX - moved.mapPanOffsetX, 1e-6f)
    assertEquals(0.4f, moved.userGpsNormalizedY - moved.mapPanOffsetY, 1e-6f)
    assertFalse(moved.isCameraFollowingUser)
  }

  @Test
  fun desiredCamera_isTheScreenCenter_atTheAnchorZoomPlusTheDelta() {
    val camera = SurveyMapSession(mapZoomDelta = -1.5f).desiredCamera(anchor)
    assertEquals(0.0, camera.center.latitude, 1e-12)
    assertEquals(0.0, camera.center.longitude, 1e-12)
    assertEquals(13.5, camera.zoom, 1e-9)
  }

  @Test
  fun desiredCamera_pastThePoleAndTheAntimeridian_clampsLatitude_andWrapsLongitude() {
    val nearAntimeridian = SurveyMapAnchor(LatLng(89.99, 179.995), zoom = 15.0, label = "")
    // A pan one survey width east and two heights north puts the center at (90.026°, 180.009°).
    val camera =
      SurveyMapSession(mapPanOffsetX = -1f, mapPanOffsetY = 2f).desiredCamera(nearAntimeridian)
    assertEquals(-179.991, camera.center.longitude, 1e-9)
    assertEquals(SurveyMapSession.MAX_MAP_LATITUDE, camera.center.latitude)

    // Extreme negative normalized coordinates wrap cleanly even past -540°.
    val multiTurnWest =
      SurveyMapSession(location = DeviceLocationSnapshot(normalizedX = -30_000f, normalizedY = 0f))
        .desiredCamera(anchor)
    assertTrue(multiTurnWest.center.longitude in -180.0..<180.0)
  }

  @Test
  fun syncedTo_ignoresRoundTripNoise_butTakesTheZoom() {
    val session = SurveyMapSession()
    val camera = session.desiredCamera(anchor).copy(zoom = 16.0)
    val synced = session.syncedTo(camera, anchor)
    assertTrue(synced.isCameraFollowingUser)
    assertEquals(0f, synced.mapPanOffsetX)
    assertEquals(1f, synced.mapZoomDelta)
  }

  @Test
  fun syncedTo_aMovedCamera_countsAsAPan() {
    val camera = CameraPosition(center = anchor.toLatLng(0.6, 0.5), zoom = 15.0)
    val synced = SurveyMapSession().syncedTo(camera, anchor)
    assertFalse(synced.isCameraFollowingUser)
    assertEquals(LocationLockState.PANNED, synced.locationLockState)
    assertEquals(-0.1f, synced.mapPanOffsetX, 1e-5f)
    assertEquals(0f, synced.mapZoomDelta)
  }

  @Test
  fun syncedTo_clampsTheZoom_andSkipsANaNZoom() {
    val session = SurveyMapSession(mapZoomDelta = 1f)
    val center = session.desiredCamera(anchor).center
    assertEquals(
      SurveyMapSession.SYNC_ZOOM_RANGE.start,
      session.syncedTo(CameraPosition(center, zoom = 0.0), anchor).mapZoomDelta,
    )
    assertSame(session, session.syncedTo(CameraPosition(center, zoom = Double.NaN), anchor))
  }

  @Test
  fun navigatingTo_recenters_closesSheets_andSelectsOnlyAPlaceTarget() {
    val start =
      SurveyMapSession(isLayersSheetOpen = true, isEntityBottomSheetExpanded = true)
        .pannedBy(0.2f, 0.2f)
    val toEntity = start.navigatingTo(NavigationTargetKind.ENTITY, "e1", selectedEntityId = "e1")
    assertTrue(toEntity.isCameraFollowingUser)
    assertFalse(toEntity.isLayersSheetOpen)
    assertFalse(toEntity.isEntityBottomSheetExpanded)
    assertEquals("e1", toEntity.selectedEntityId)
    assertNull(toEntity.selectedPlaceId)
    assertTrue(toEntity.isNavigatingTo(NavigationTargetKind.ENTITY, "e1"))
    assertFalse(toEntity.isNavigatingTo(NavigationTargetKind.SUBMISSION, "e1"))

    val place =
      SurveyPlaceItem(
        id = "p1",
        name = "Karima",
        categoryLabel = "Forest",
        regionSubtitle = "Nyeri",
        position = LatLng(0.0, 0.0),
        normalizedX = 0.3f,
        normalizedY = 0.3f,
      )
    val toPlace =
      toEntity.navigatingTo(
        NavigationTargetKind.PLACE,
        "p1",
        selectedEntityId = null,
        place = place,
      )
    assertNull(toPlace.selectedEntityId)
    assertEquals("p1", toPlace.selectedPlaceId)
    assertEquals(place, toPlace.lastSelectedPlace)
    assertEquals(place, toPlace.navigationTargetPlace)
  }
}
