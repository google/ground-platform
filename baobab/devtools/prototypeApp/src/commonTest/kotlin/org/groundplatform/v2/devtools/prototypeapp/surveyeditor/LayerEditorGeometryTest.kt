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

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.devtools.prototypeapp.data.seed.SurveyEditorSamples
import org.groundplatform.v2.map.Basemap
import org.groundplatform.v2.map.CameraPosition
import org.groundplatform.v2.map.Geometry
import org.groundplatform.v2.map.LatLng as MapLatLng

class LayerEditorGeometryTest {
  private val w = 800.0
  private val h = 500.0

  private fun assertClose(expected: Double, actual: Double, eps: Double = 1e-7) =
    assertTrue(abs(expected - actual) <= eps, "expected $expected but was $actual")

  /** A linear projection that fits [points] into the test viewport; enough for hit-testing. */
  private fun projectionFitting(points: List<LatLng>): (LatLng) -> ScreenPoint {
    val west = points.minOf { it.lng }
    val north = points.maxOf { it.lat }
    val span = maxOf(points.maxOf { it.lng } - west, north - points.minOf { it.lat }, 1e-9)
    val scale = (minOf(w, h) - 80) / span
    return { ScreenPoint(40 + (it.lng - west) * scale, 40 + (north - it.lat) * scale) }
  }

  @Test
  fun toMapLatLng_wrapsLongitude() {
    assertEquals(MapLatLng(10.0, -170.0), LatLng(10.0, 190.0).toMapLatLng())
    assertEquals(LatLng(-0.4, 36.9), MapLatLng(-0.4, 36.9).toEditorLatLng())
  }

  @Test
  fun scaleBar_isNiceAndFits() {
    val bar = ScaleBar.forCamera(CameraPosition(MapLatLng(0.0, 0.0), 15.0), maxWidthPx = 100.0)
    val leading = bar.meters / 10.0.pow(floor(log10(bar.meters)))
    assertTrue(leading in listOf(1.0, 2.0, 5.0), "leading digit $leading")
    assertTrue(bar.widthPx <= 100.0 && bar.widthPx > 20.0)
    assertEquals("5 km", ScaleBar(5000.0, 80.0).label)
    assertEquals("200 m", ScaleBar(200.0, 80.0).label)
  }

  @Test
  fun metersPerPixel_matchesMapboxConvention() {
    // 512 dp world at zoom 0: the equator spans 40 075 km / 512 per dp.
    assertClose(
      40_075_016.686 / 512,
      ScaleBar.metersPerPixel(CameraPosition(MapLatLng(0.0, 0.0), 0.0)),
      1e-6,
    )
  }

  @Test
  fun hitTest_findsPolygonByContainmentAndPointByDistance() {
    val parcels = SurveyEditorSamples.coffeeParcels()
    val project = projectionFitting(parcels.rows.flatMap { it.geometry })
    val target = parcels.rows.first()
    val centroid =
      LatLng(target.geometry.map { it.lat }.average(), target.geometry.map { it.lng }.average())
    assertEquals(target.key, hitTestFeature(parcels, project, project(centroid)))
    assertNull(hitTestFeature(parcels, project, ScreenPoint(-500.0, -500.0)))

    val plots = SurveyEditorSamples.shadePlots()
    val plotProject = projectionFitting(plots.rows.flatMap { it.geometry })
    val plot = plots.rows.first()
    val s = plotProject(plot.geometry.first())
    assertEquals(plot.key, hitTestFeature(plots, plotProject, ScreenPoint(s.x + 5, s.y - 5)))
  }

  @Test
  fun toMapGeometry_degradesToWhatCanBeDrawn() {
    val a = LatLng(0.0, 0.0)
    val b = LatLng(0.0, 1.0)
    val c = LatLng(1.0, 1.0)
    assertNull(emptyList<LatLng>().toMapGeometry(GeometryKind.POLYGON))
    assertTrue(listOf(a).toMapGeometry(GeometryKind.LINE) is Geometry.Point)
    assertTrue(listOf(a, b).toMapGeometry(GeometryKind.POLYGON) is Geometry.LineString)
    assertTrue(listOf(a, b, c).toMapGeometry(GeometryKind.POLYGON) is Geometry.Polygon)
    assertTrue(listOf(a, b, c).toMapGeometry(GeometryKind.LINE) is Geometry.LineString)
  }

  @Test
  fun layerEditorContent_drawsSelectedFeatureLast() {
    val parcels = SurveyEditorSamples.coffeeParcels()
    val selected = parcels.rows.first().key
    val content = layerEditorContent(parcels, selected, Color.Red, Basemap.None())
    val features = content.sources.single().features
    assertEquals(parcels.rows.count { it.geometry.isNotEmpty() }, features.size)
    assertEquals(selected, features.last().id)
    assertEquals("true", features.last().properties[LAYER_EDITOR_SELECTED])
    assertTrue(features.dropLast(1).all { it.properties[LAYER_EDITOR_SELECTED] == "false" })
  }

  @Test
  fun surveyAreaThumbnailContent_hasBoundaryVerticesAndCenter() {
    val area =
      SurveyArea(
        name = "Plot",
        boundaries = listOf(LatLng(0.0, 0.0), LatLng(0.0, 1.0), LatLng(1.0, 1.0)),
      )
    val ids = surveyAreaThumbnailContent(area, Color.Blue).sources.single().features.map { it.id }
    assertEquals(listOf("boundary", "vertex-0", "vertex-1", "vertex-2", "center"), ids)
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
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    val parcels = state.mapLayers.first { it.geometryKind == GeometryKind.POLYGON }
    val drawn = listOf(LatLng(-0.41, 36.95), LatLng(-0.41, 36.96), LatLng(-0.42, 36.955))
    val key = state.addRow(parcels.key, geometry = drawn)
    val row = state.datasets.first { it.key == parcels.key }.rows.first { it.key == key }
    assertEquals(drawn, row.geometry)
  }
}
