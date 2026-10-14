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
package org.groundplatform.v2.web.ui.surveyeditor

import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.app.data.seed.SurveyEditorSamples
import org.groundplatform.v2.app.domain.model.editor.GeometryKind
import org.groundplatform.v2.app.domain.model.editor.LatLng
import org.groundplatform.v2.app.domain.model.editor.SampleDesignConfig
import org.groundplatform.v2.app.domain.model.editor.SamplePlotProperties
import org.groundplatform.v2.app.domain.model.editor.SubPlotMode
import org.groundplatform.v2.app.domain.model.editor.SurveyArea
import org.groundplatform.v2.app.domain.model.editor.formatFixed
import org.groundplatform.v2.app.domain.model.geometryKind
import org.groundplatform.v2.map.Basemap
import org.groundplatform.v2.map.CameraPosition
import org.groundplatform.v2.map.Geometry
import org.groundplatform.v2.map.LatLng as MapLatLng
import org.groundplatform.v2.map.MapContent
import org.groundplatform.v2.map.MapLayer
import org.groundplatform.v2.map.StyleValue
import org.groundplatform.v2.web.ui.viewmodel.surveyEditorViewModel
import org.groundplatform.v2.web.ui.viewmodel.ui

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
  fun surveyAreaThumbnailContent_hasPartsVerticesAndCenter() {
    val area =
      SurveyArea(
        name = "Plot",
        parts =
          listOf(
            listOf(LatLng(0.0, 0.0), LatLng(0.0, 1.0), LatLng(1.0, 1.0)),
            listOf(LatLng(2.0, 2.0), LatLng(2.0, 3.0), LatLng(3.0, 3.0)),
          ),
      )
    val ids = surveyAreaThumbnailContent(area, Color.Blue).sources.single().features.map { it.id }
    assertEquals(
      listOf("part-0", "part-1") + (0 until 6).map { "vertex-$it" } + "center",
      ids,
    )
  }

  @Test
  fun surveyAreaThumbnailContent_skipsVertexDotsForDetailedAreas() {
    val ring =
      (0 until 300).map { i ->
        val a = 2 * kotlin.math.PI * i / 300
        LatLng(kotlin.math.sin(a), kotlin.math.cos(a))
      }
    val ids =
      surveyAreaThumbnailContent(SurveyArea("Circle", listOf(ring)), Color.Blue)
        .sources
        .single()
        .features
        .map { it.id }
    assertEquals(listOf("part-0", "center"), ids)
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
    val state = surveyEditorViewModel()
    val parcels = state.ui.mapLayers.first { it.geometryKind == GeometryKind.POLYGON }
    val drawn = listOf(LatLng(-0.41, 36.95), LatLng(-0.41, 36.96), LatLng(-0.42, 36.955))
    val key = state.addRow(parcels.key, geometry = drawn)
    val row = state.ui.datasets.first { it.key == parcels.key }.rows.first { it.key == key }
    assertEquals(drawn, row.geometry)
  }

  @Test
  fun samplePointsOverlay_drawsHollowRingsSizedOnTheGround() {
    val samples = listOf(LatLng(-0.42, 36.95), LatLng(-0.421, 36.951))
    val content = withSamplePointsOverlay(MapContent(), samples, Color.Red, radiusM = 3.0)

    // Outlines only (no fill), so the imagery inside each ring stays visible.
    assertTrue(content.layers.none { it is MapLayer.Fill || it is MapLayer.Circle })
    val rings = content.layers.filterIsInstance<MapLayer.Line>()
    assertEquals(2, rings.size)
    assertEquals(StyleValue.Constant(Color.Red), rings.last().color)

    // Each sample is a polygon whose vertices are 3 m from the sample on the ground, so it scales
    // with the map instead of keeping a fixed size on screen.
    val features = content.sources.single().features
    assertEquals(samples.size, features.size)
    val ring = assertIs<Geometry.Polygon>(features.first().geometry).rings.single()
    val center = samples.first()
    ring.forEach { v ->
      val dy = (v.latitude - center.lat) * 111_320.0
      val dx = (v.longitude - center.lng) * 111_320.0 * cos(center.lat * PI / 180)
      assertClose(3.0, sqrt(dx * dx + dy * dy), eps = 1e-6)
    }
  }

  @Test
  fun samplePointRadius_followsPlotSizeAndStaysClearOfNeighbors() {
    val design = SampleDesignConfig(plotSizeM = 40.0, subPlot = SubPlotMode.CENTER)
    assertEquals(3.0, samplePointRadiusM(design))
    // A tight grid keeps rings at most a quarter of the spacing, so neighbors never touch.
    assertEquals(
      1.0,
      samplePointRadiusM(design.copy(subPlot = SubPlotMode.GRID, subPlotSpacingM = 4.0)),
    )
    assertEquals(DEFAULT_SAMPLE_RADIUS_M, samplePointRadiusM(design.copy(plotSizeM = Double.NaN)))
  }

  @Test
  fun samplePointsOverlay_noSamples_returnsContentUnchanged() {
    val content = MapContent()
    assertEquals(content, withSamplePointsOverlay(content, emptyList(), Color.Red))
  }

  @Test
  fun selectedPlotSamples_onlyReturnsTheSelectedPlotsSamples() {
    val base = SurveyEditorSamples.shadePlots()
    val plots =
      base.copy(
        rows =
          base.rows.mapIndexed { i, row ->
            row.copy(
              values = row.values + (SamplePlotProperties.SAMPLES to "$i.0 1.0 0 0;$i.0 2.0 0 0")
            )
          }
      )
    val second = plots.rows[1].key

    assertEquals(listOf(LatLng(1.0, 1.0), LatLng(1.0, 2.0)), selectedPlotSamples(plots, second))
    assertTrue(selectedPlotSamples(plots, null).isEmpty(), "no plot selected")
    assertTrue(selectedPlotSamples(plots, "missing").isEmpty(), "selected plot no longer exists")
  }
}
