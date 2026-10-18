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
package org.groundplatform.v2.map

import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebMercatorProjectionTest {
  private val viewport = DpSize(800.dp, 600.dp)
  private val nyeri = LatLng(-0.4198, 36.9512)

  private fun assertNear(expected: Double, actual: Double, tolerance: Double = 1e-4) =
    assertTrue(abs(expected - actual) <= tolerance, "expected $expected but was $actual")

  @Test
  fun projectAndUnproject_roundTripAcrossZoomsAndBearings() {
    val points =
      listOf(
        LatLng(-0.4198, 36.9512),
        LatLng(-0.4150, 36.9580),
        LatLng(-0.4250, 36.9420),
        LatLng(37.7749, -122.4194),
      )
    val cameras =
      listOf(
        CameraPosition(nyeri, zoom = 5.0, bearing = 0.0),
        CameraPosition(nyeri, zoom = 13.0, bearing = 0.0),
        CameraPosition(nyeri, zoom = 16.5, bearing = 35.0),
        CameraPosition(nyeri, zoom = 18.0, bearing = -120.0),
      )

    for (camera in cameras) {
      for (point in points) {
        val screen = WebMercator.project(point, camera, viewport)
        val roundTrip = WebMercator.unproject(screen, camera, viewport)
        assertNear(point.latitude, roundTrip.latitude, tolerance = 1e-4)
        assertNear(point.longitude, roundTrip.longitude, tolerance = 1e-4)
      }
    }
  }

  @Test
  fun screenOffsets_roundTripThroughUnprojectAndProject() {
    val camera = CameraPosition(nyeri, zoom = 14.0, bearing = 15.0)
    val offsets =
      listOf(
        DpOffset(400.dp, 300.dp),
        DpOffset(120.dp, 85.dp),
        DpOffset(680.dp, 510.dp),
        DpOffset(250.5.dp, 412.25.dp),
      )

    for (offset in offsets) {
      val latLng = WebMercator.unproject(offset, camera, viewport)
      val projected = WebMercator.project(latLng, camera, viewport)
      assertNear(offset.x.value.toDouble(), projected.x.value.toDouble(), tolerance = 1e-2)
      assertNear(offset.y.value.toDouble(), projected.y.value.toDouble(), tolerance = 1e-2)
    }
  }

  @Test
  fun worldSize_matches512TileConventionAnd256TileZoomOffset() {
    assertEquals(512.0, WebMercator.TILE_SIZE)
    for (zoom in listOf(1.0, 8.0, 13.0, 17.0)) {
      val (x512AtZoomMinusOne, y512AtZoomMinusOne) = WebMercator.toWorld(nyeri, zoom - 1.0)
      val world256AtZoomX = (nyeri.longitude + 180.0) / 360.0 * (256.0 * 2.0.pow(zoom))
      assertNear(world256AtZoomX, x512AtZoomMinusOne, tolerance = 1e-6)

      val (x512AtZoom, y512AtZoom) = WebMercator.toWorld(nyeri, zoom)
      assertNear(x512AtZoomMinusOne * 2.0, x512AtZoom, tolerance = 1e-6)
      assertNear(y512AtZoomMinusOne * 2.0, y512AtZoom, tolerance = 1e-6)
    }
  }
}
