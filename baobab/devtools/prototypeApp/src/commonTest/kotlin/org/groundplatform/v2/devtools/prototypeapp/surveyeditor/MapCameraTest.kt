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
package org.groundplatform.v2.devtools.prototypeapp.surveyeditor

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapCameraTest {
  private val w = 800.0
  private val h = 500.0

  private fun assertClose(expected: Double, actual: Double, eps: Double = 1e-7) =
    assertTrue(abs(expected - actual) <= eps, "expected $expected but was $actual")

  @Test
  fun center_projectsToViewportCenter() {
    val cam = MapCamera(LatLng(-0.418, 36.95), 15.0)
    val s = cam.project(cam.center, w, h)
    assertClose(w / 2, s.x)
    assertClose(h / 2, s.y)
  }

  @Test
  fun projectAndUnproject_roundTrip() {
    val cam = MapCamera(LatLng(-0.418, 36.95), 16.3)
    val p = LatLng(-0.4201, 36.9533)
    val back = cam.unproject(cam.project(p, w, h), w, h)
    assertClose(p.lat, back.lat)
    assertClose(p.lng, back.lng)
  }

  @Test
  fun worldSize_matchesMapboxConvention() {
    // Mapbox GL JS: 512 px world at zoom 0, doubling per level.
    assertClose(512.0, MapCamera(LatLng(0.0, 0.0), 0.0).worldSize)
    assertClose(512.0 * 1024, MapCamera(LatLng(0.0, 0.0), 10.0).worldSize)
  }

  @Test
  fun panBy_movesContentWithTheDrag() {
    val cam = MapCamera(LatLng(-0.418, 36.95), 15.0)
    val p = LatLng(-0.419, 36.951)
    val before = cam.project(p, w, h)
    val after = cam.panBy(30.0, -20.0, w, h).project(p, w, h)
    assertClose(before.x + 30, after.x, 1e-6)
    assertClose(before.y - 20, after.y, 1e-6)
  }

  @Test
  fun zoomAround_keepsAnchorFixed() {
    val cam = MapCamera(LatLng(-0.418, 36.95), 14.0)
    val anchor = ScreenPoint(120.0, 400.0)
    val geo = cam.unproject(anchor, w, h)
    val zoomed = cam.zoomAround(1.5, anchor, w, h)
    assertClose(15.5, zoomed.zoom)
    val s = zoomed.project(geo, w, h)
    assertClose(anchor.x, s.x, 1e-6)
    assertClose(anchor.y, s.y, 1e-6)
  }

  @Test
  fun zoom_isClamped() {
    val cam = MapCamera(LatLng(0.0, 0.0), MapCamera.MAX_ZOOM)
    assertEquals(cam, cam.zoomAround(2.0, ScreenPoint(10.0, 10.0), w, h))
  }

  @Test
  fun fit_containsAllPointsWithinPadding() {
    val pts = SurveyEditorSamples.coffeeParcels().rows.flatMap { it.geometry }
    val cam = MapCamera.fit(pts, w, h, padding = 40.0)
    pts.forEach {
      val s = cam.project(it, w, h)
      assertTrue(s.x in 39.9..(w - 39.9) && s.y in 39.9..(h - 39.9), "$it → $s")
    }
  }

  @Test
  fun fit_singlePointUsesDefaultZoom() {
    val cam = MapCamera.fit(listOf(LatLng(1.0, 2.0)), w, h)
    assertEquals(16.0, cam.zoom)
    assertClose(1.0, cam.center.lat)
  }

  @Test
  fun scaleBar_isNiceAndFits() {
    val bar = ScaleBar.forCamera(MapCamera(LatLng(0.0, 0.0), 15.0), maxWidthPx = 100.0)
    val leading = bar.meters / 10.0.pow(floor(log10(bar.meters)))
    assertTrue(leading in listOf(1.0, 2.0, 5.0), "leading digit $leading")
    assertTrue(bar.widthPx <= 100.0 && bar.widthPx > 20.0)
    assertEquals("5 km", ScaleBar(5000.0, 80.0).label)
    assertEquals("200 m", ScaleBar(200.0, 80.0).label)
  }

  @Test
  fun hitTest_findsPolygonByContainmentAndPointByDistance() {
    val parcels = SurveyEditorSamples.coffeeParcels()
    val cam = MapCamera.fit(parcels.rows.flatMap { it.geometry }, w, h)
    val target = parcels.rows.first()
    val centroid =
      LatLng(target.geometry.map { it.lat }.average(), target.geometry.map { it.lng }.average())
    assertEquals(target.key, hitTestFeature(parcels, cam, w, h, cam.project(centroid, w, h)))
    assertNull(hitTestFeature(parcels, cam, w, h, ScreenPoint(-500.0, -500.0)))

    val plots = SurveyEditorSamples.shadePlots()
    val plotCam = MapCamera.fit(plots.rows.flatMap { it.geometry }, w, h)
    val plot = plots.rows.first()
    val s = plotCam.project(plot.geometry.first(), w, h)
    assertEquals(plot.key, hitTestFeature(plots, plotCam, w, h, ScreenPoint(s.x + 5, s.y - 5)))
  }

  @Test
  fun midpoints_closePolygonsOnly() {
    val square =
      listOf(
        ScreenPoint(0.0, 0.0),
        ScreenPoint(10.0, 0.0),
        ScreenPoint(10.0, 10.0),
        ScreenPoint(0.0, 10.0),
      )
    assertEquals(4, midpoints(square, closed = true).size)
    assertEquals(3, midpoints(square, closed = false).size)
    assertEquals(ScreenPoint(5.0, 0.0), midpoints(square, closed = false).first())
  }

  @Test
  fun formatFixed_padsAndRounds() {
    assertEquals("-0.41820", formatFixed(-0.4182, 5))
    assertEquals("15.3", formatFixed(15.26, 1))
    assertEquals("0.0", formatFixed(-0.01, 1))
  }

  @Test
  fun addRow_usesDrawnGeometry() {
    val state = SurveyEditorState()
    val parcels = state.mapLayers.first { it.geometryKind == GeometryKind.POLYGON }
    val drawn = listOf(LatLng(-0.41, 36.95), LatLng(-0.41, 36.96), LatLng(-0.42, 36.955))
    val key = state.addRow(parcels.key, geometry = drawn)
    val row = state.datasets.first { it.key == parcels.key }.rows.first { it.key == key }
    assertEquals(drawn, row.geometry)
  }
}
