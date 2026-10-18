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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapCameraStateTest {
  private val viewport = DpSize(400.dp, 800.dp)
  private val nairobi = LatLng(-1.2921, 36.8219)

  private fun assertNear(expected: Double, actual: Double, tolerance: Double = 1e-6) =
    assertTrue(abs(expected - actual) <= tolerance, "expected $expected but was $actual")

  @Test
  fun center_projectsToViewportCenter() {
    val p = WebMercator.project(nairobi, CameraPosition(nairobi, 12.0), viewport)
    assertNear(200.0, p.x.value.toDouble(), 1e-3)
    assertNear(400.0, p.y.value.toDouble(), 1e-3)
  }

  @Test
  fun zoomZero_worldIs512dpWide() {
    val (x0, _) = WebMercator.toWorld(LatLng(0.0, -180.0), 0.0)
    val (x1, y1) = WebMercator.toWorld(LatLng(0.0, 180.0), 0.0)
    assertNear(0.0, x0)
    assertNear(512.0, x1)
    assertNear(256.0, y1)
  }

  @Test
  fun unproject_invertsProject_withBearing() {
    val camera = CameraPosition(nairobi, 14.5, bearing = 37.0)
    val target = LatLng(-1.30, 36.83)
    val screen = WebMercator.project(target, camera, viewport)
    val back = WebMercator.unproject(screen, camera, viewport)
    assertNear(target.latitude, back.latitude)
    assertNear(target.longitude, back.longitude)
  }

  @Test
  fun bearing90_putsEastAtTheTop() {
    val camera = CameraPosition(LatLng(0.0, 0.0), 10.0, bearing = 90.0)
    val east = WebMercator.project(LatLng(0.0, 0.01), camera, viewport)
    assertNear(200.0, east.x.value.toDouble(), 1e-3)
    assertTrue(east.y.value < 400f)
  }

  @Test
  fun cameraForBounds_fitsBoundsInsidePaddedViewport() {
    val bounds = LngLatBounds(36.80, -1.31, 36.85, -1.27)
    val padding = MapInsets(bottom = 300.dp)
    val camera = WebMercator.cameraForBounds(bounds, viewport, padding, maxZoom = 20.0)
    val nw = WebMercator.project(LatLng(bounds.north, bounds.west), camera, viewport)
    val se = WebMercator.project(LatLng(bounds.south, bounds.east), camera, viewport)
    val eps = 0.5f
    assertTrue(nw.x.value >= -eps && se.x.value <= 400f + eps, "x: ${nw.x} .. ${se.x}")
    assertTrue(nw.y.value >= -eps && se.y.value <= 500f + eps, "y: ${nw.y} .. ${se.y}")
    // One dimension fills the padded area exactly.
    val filledX = abs((se.x.value - nw.x.value) - 400f) < 1f
    val filledY = abs((se.y.value - nw.y.value) - 500f) < 1f
    assertTrue(filledX || filledY)
  }

  @Test
  fun cameraForBounds_pointUsesMaxZoom() {
    val camera =
      WebMercator.cameraForBounds(LngLatBounds.of(listOf(nairobi)), viewport, MapInsets.Zero, 17.0)
    assertEquals(17.0, camera.zoom)
    assertNear(nairobi.latitude, camera.center.latitude)
  }

  @Test
  fun cameraState_withoutRenderer_movesImmediately() {
    val state = MapCameraState(CameraPosition(nairobi, 10.0))
    val target = CameraPosition(LatLng(0.0, 0.0), 3.0)
    state.move(target)
    assertEquals(target, state.position)
    assertEquals(false, state.isMovingByGesture)
  }

  @Test
  fun hitTest_prefersTopmostLayer_andFallsBackToBackground() {
    val square =
      Geometry.Polygon(
        listOf(listOf(LatLng(0.0, 0.0), LatLng(0.0, 1.0), LatLng(1.0, 1.0), LatLng(1.0, 0.0)))
      )
    val content =
      MapContent(
        sources =
          listOf(
            GeoJsonSource(
              "s",
              listOf(
                MapFeature("area", square),
                MapFeature("pin", Geometry.Point(LatLng(0.5, 0.5))),
              ),
            )
          ),
        layers =
          listOf(
            MapLayer.Fill("fill", "s", color = StyleValue.Constant(Color.Green)),
            MapLayer.Circle("pins", "s", color = StyleValue.Constant(Color.Red)),
          ),
      )
    val camera = CameraPosition(LatLng(0.5, 0.5), 8.0)
    val project = { p: LatLng -> WebMercator.project(p, camera, viewport) }
    val here = LatLng(0.5, 0.5)

    val onPin = project(LatLng(0.5, 0.5))
    assertEquals(
      MapEvent.FeatureTapped("pins", "pin", here),
      hitTest(content, onPin, project, here),
    )

    val insideSquare = DpOffset(onPin.x + 40.dp, onPin.y)
    assertEquals(
      MapEvent.FeatureTapped("fill", "area", here),
      hitTest(content, insideSquare, project, here),
    )

    assertNull(hitTest(content, DpOffset(0.dp, 0.dp), project, here))
  }

  @Test
  fun styleValue_matchFallsBackToDefault() {
    val color = StyleValue.Match("status", mapOf("done" to Color.Green), default = Color.Gray)
    assertEquals(Color.Green, color.evaluate(mapOf("status" to "done")))
    assertEquals(Color.Gray, color.evaluate(mapOf("status" to "pending")))
    assertEquals(Color.Gray, color.evaluate(emptyMap()))
  }

  @Test
  fun cameraCentering_putsPointInMiddleOfPaddedArea_withBearing() {
    val camera = CameraPosition(nairobi, 15.0, bearing = 30.0)
    val target = LatLng(-1.30, 36.83)
    val padding = MapInsets(left = 40.dp, top = 20.dp, right = 0.dp, bottom = 300.dp)
    val centered = WebMercator.cameraCentering(target, camera, padding)
    assertEquals(camera.zoom, centered.zoom)
    assertEquals(camera.bearing, centered.bearing)
    val p = WebMercator.project(target, centered, viewport)
    assertNear((400.0 + 40.0) / 2, p.x.value.toDouble(), 1e-2)
    assertNear((800.0 + 20.0 - 300.0) / 2, p.y.value.toDouble(), 1e-2)
  }
}
