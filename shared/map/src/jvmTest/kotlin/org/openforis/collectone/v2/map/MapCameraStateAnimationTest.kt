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
package org.openforis.collectone.v2.map

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class MapCameraStateAnimationTest {

  /** Frame clock whose frames are exactly [frameMs] apart, so animations are deterministic. */
  private class FakeFrameClock(private val frameMs: Long = 100) : MonotonicFrameClock {
    var frames = 0
      private set

    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R =
      onFrame(frames++ * frameMs * 1_000_000)
  }

  private val start = CameraPosition(LatLng(0.0, 0.0), zoom = 2.0)

  @Test
  fun animateTo_withoutAFrameClock_jumpsToTheTarget() = runBlocking {
    val camera = MapCameraState(start)
    val target = CameraPosition(LatLng(10.0, 10.0), zoom = 5.0)

    camera.animateTo(target)

    assertEquals(target, camera.position)
    assertFalse(camera.isMovingByGesture)
  }

  @Test
  fun animateTo_easesThroughFramesAndEndsExactlyAtTheTarget() = runBlocking {
    val camera = MapCameraState(start)
    val target = CameraPosition(LatLng(10.0, 20.0), zoom = 6.0)
    val clock = FakeFrameClock()

    withContext(clock) { camera.animateTo(target, durationMs = 400) }

    assertEquals(target, camera.position)
    // The start frame, then one frame every 100 ms up to the 400 ms mark.
    assertEquals(5, clock.frames)
  }

  @Test
  fun animate_takesTheShortWayRoundForBearingAndTheAntimeridian() = runBlocking {
    val from = CameraPosition(LatLng(0.0, 170.0), zoom = 3.0, bearing = 350.0)
    val to = CameraPosition(LatLng(0.0, -170.0), zoom = 3.0, bearing = 10.0)
    val frames = mutableListOf<CameraPosition>()

    withContext(FakeFrameClock()) { WebMercator.animate(from, to, 400) { frames += it } }

    val middle = frames[frames.size / 2]
    // Bearing passes through north (360°) rather than sweeping back through 180°.
    assertTrue(middle.bearing > 350.0, "bearing ${middle.bearing}")
    // Longitude crosses ±180° rather than sweeping back across the prime meridian.
    assertTrue(kotlin.math.abs(middle.center.longitude) > 170.0, "lng ${middle.center.longitude}")
    assertEquals(to, frames.last())
  }

  @Test
  fun animateByGesture_marksTheMoveAsGestureDriven() = runBlocking {
    val camera = MapCameraState(start)

    camera.animateByGesture(start.copy(zoom = 3.0), durationMs = 0)

    assertTrue(camera.isMovingByGesture)
    camera.move(start)
    assertFalse(camera.isMovingByGesture)
  }

  @Test
  fun project_andUnproject_useMercatorAroundTheViewportCenter() {
    val camera = MapCameraState(start)
    camera.viewportSize = DpSize(200.dp, 100.dp)
    val center = DpOffset(100.dp, 50.dp)

    assertEquals(center, camera.project(start.center))
    assertEquals(start.center, camera.unproject(center))
  }

  @Test
  fun fitBounds_returnsAndMovesToTheFittedCamera() = runBlocking {
    val camera = MapCameraState(start)
    camera.viewportSize = DpSize(512.dp, 512.dp)

    val target = camera.fitBounds(LngLatBounds(-10.0, -10.0, 10.0, 10.0), durationMs = 0)

    assertEquals(target, camera.position)
    assertEquals(0.0, target.bearing)
  }

  @Test
  fun centerOn_keepsZoomAndBearing() = runBlocking {
    val camera = MapCameraState(start.copy(bearing = 45.0))
    val point = LatLng(5.0, 5.0)

    val target = camera.centerOn(point, durationMs = 0)

    assertEquals(start.zoom, target.zoom)
    assertEquals(45.0, target.bearing)
    assertEquals(target, camera.position)
  }
}
