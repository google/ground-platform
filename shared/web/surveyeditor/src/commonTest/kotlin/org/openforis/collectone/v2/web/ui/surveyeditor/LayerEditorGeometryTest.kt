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
package org.openforis.collectone.v2.web.ui.surveyeditor

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.seed.SurveyEditorSamples
import org.openforis.collectone.v2.app.domain.model.Measurement
import org.openforis.collectone.v2.app.domain.model.MeasurementUnit
import org.openforis.collectone.v2.app.domain.model.editor.SampleDesignConfig
import org.openforis.collectone.v2.app.domain.model.editor.SamplePlotProperties
import org.openforis.collectone.v2.app.domain.model.editor.SubPlotMode
import org.openforis.collectone.v2.app.domain.model.editor.SurveyArea
import org.openforis.collectone.v2.app.domain.model.editor.formatFixed
import org.openforis.collectone.v2.app.domain.model.editor.toGeoCoord
import org.openforis.collectone.v2.app.domain.model.editor.toLatLng
import org.openforis.collectone.v2.core.sampling.PlotGeometry
import org.openforis.collectone.v2.map.Basemap
import org.openforis.collectone.v2.map.CameraPosition
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.map.MapContent
import org.openforis.collectone.v2.map.MapLayer
import org.openforis.collectone.v2.map.StyleValue

class LayerEditorGeometryTest {
  private val w = 800.0
  private val h = 500.0

  private fun assertClose(expected: Double, actual: Double, eps: Double = 1e-7) =
    assertTrue(abs(expected - actual) <= eps, "expected $expected but was $actual")

  /** A linear projection that fits [points] into the test viewport; enough for hit-testing. */
  private fun projectionFitting(points: List<LatLng>): (LatLng) -> ScreenPoint {
    val west = points.minOf { it.longitude }
    val north = points.maxOf { it.latitude }
    val span =
      maxOf(points.maxOf { it.longitude } - west, north - points.minOf { it.latitude }, 1e-9)
    val scale = (minOf(w, h) - 80) / span
    return { ScreenPoint(40 + (it.longitude - west) * scale, 40 + (north - it.latitude) * scale) }
  }

  @Test
  fun scaleBar_isNiceAndFits() {
    val bar = ScaleBar.forCamera(CameraPosition(LatLng(0.0, 0.0), 15.0), maxWidthPx = 100.0)
    val leading = bar.meters / 10.0.pow(floor(log10(bar.meters)))
    assertTrue(leading in listOf(1.0, 2.0, 5.0), "leading digit $leading")
    assertTrue(bar.widthPx <= 100.0 && bar.widthPx > 20.0)
    assertEquals(Measurement(5.0, MeasurementUnit.KILOMETER, 0), ScaleBar(5000.0, 80.0).length)
    assertEquals(Measurement(200.0, MeasurementUnit.METER, 0), ScaleBar(200.0, 80.0).length)
  }

  @Test
  fun metersPerPixel_matchesMapboxConvention() {
    // 512 dp world at zoom 0: the equator spans 40 075 km / 512 per dp.
    assertClose(
      40_075_016.686 / 512,
      ScaleBar.metersPerPixel(CameraPosition(LatLng(0.0, 0.0), 0.0)),
      1e-6,
    )
  }

  @Test
  fun hitTest_findsPolygonByContainmentAndPointByDistance() {
    val parcels = SurveyEditorSamples.coffeeParcels()
    val project = projectionFitting(parcels.rows.flatMap { it.geometry })
    val target = parcels.rows.first()
    val centroid =
      LatLng(
        target.geometry.map { it.latitude }.average(),
        target.geometry.map { it.longitude }.average(),
      )
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
    assertNull(emptyList<LatLng>().toMapGeometry(GeometryType.POLYGON))
    assertTrue(listOf(a).toMapGeometry(GeometryType.LINE_STRING) is Geometry.Point)
    assertTrue(listOf(a, b).toMapGeometry(GeometryType.POLYGON) is Geometry.LineString)
    assertTrue(listOf(a, b, c).toMapGeometry(GeometryType.POLYGON) is Geometry.Polygon)
    assertTrue(listOf(a, b, c).toMapGeometry(GeometryType.LINE_STRING) is Geometry.LineString)
  }

  @Test
  fun layerEditorContent_drawsSelectedFeatureLast() {
    val parcels = SurveyEditorSamples.coffeeParcels()
    val selected = parcels.rows.first().key
    val content = layerEditorContent(parcels, selected, Color.Red, Basemap.None())
    val features = content.sources.first { it.id == LAYER_EDITOR_SOURCE }.features
    assertEquals(parcels.rows.count { it.geometry.isNotEmpty() }, features.size)
    assertEquals(selected, features.last().id)
    assertEquals("true", features.last().properties[LAYER_EDITOR_SELECTED])
    assertTrue(features.dropLast(1).all { it.properties[LAYER_EDITOR_SELECTED] == "false" })

    val centroids = content.sources.first { it.id == LAYER_EDITOR_CENTROIDS_SOURCE }.features
    assertEquals(features.size, centroids.size)
    assertEquals(selected, centroids.last().id)
    assertTrue(centroids.all { it.geometry is Geometry.Point })
    val centroidLayer =
      assertIs<MapLayer.Circle>(content.layers.first { it.id == "layer-editor-centroid" })
    assertEquals(LAYER_EDITOR_CENTROIDS_SOURCE, centroidLayer.sourceId)
  }

  @Test
  fun layerEditorContent_omitsCentroidsForPointLayers() {
    val plots = SurveyEditorSamples.shadePlots()
    val content = layerEditorContent(plots, null, Color.Green, Basemap.None())
    val centroids = content.sources.first { it.id == LAYER_EDITOR_CENTROIDS_SOURCE }.features
    assertTrue(centroids.isEmpty())
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
    val parcels = state.ui.mapLayers.first { it.geometryType == GeometryType.POLYGON }
    val drawn = listOf(LatLng(-0.41, 36.95), LatLng(-0.41, 36.96), LatLng(-0.42, 36.955))
    val key = state.addRow(parcels.key, geometry = drawn)
    val row = state.ui.datasets.first { it.key == parcels.key }.rows.first { it.key == key }
    assertEquals(drawn, row.geometry)
  }

  @Test
  fun samplePointsOverlay_drawsHollowSquareBoxesSizedOnTheGround() {
    val samples = listOf(LatLng(-0.42, 36.95), LatLng(-0.421, 36.951))
    val content = withSamplePointsOverlay(MapContent(), samples, Color.Red, sizeM = 3.0)

    // Outlines only (no fill), so the imagery inside each box stays visible.
    assertTrue(content.layers.none { it is MapLayer.Fill || it is MapLayer.Circle })
    val outlines = content.layers.filterIsInstance<MapLayer.Line>()
    assertEquals(2, outlines.size)
    assertEquals(StyleValue.Constant(Color.Red), outlines.last().color)

    // Each sample is a 4-corner square matching PlotGeometry.subPlotBox (shared with KML export),
    // so it scales with the map instead of keeping a fixed size on screen.
    val features = content.sources.single().features
    assertEquals(samples.size, features.size)
    val ring = assertIs<Geometry.Polygon>(features.first().geometry).rings.single()
    val expected =
      PlotGeometry.subPlotBox(samples.first().toGeoCoord(), 3.0).dropLast(1).map { it.toLatLng() }
    assertEquals(4, ring.size)
    assertEquals(expected, ring)
  }

  @Test
  fun samplePointSize_followsPlotSizeAndStaysClearOfNeighbors() {
    val design = SampleDesignConfig(plotSizeM = 40.0, subPlot = SubPlotMode.CENTER)
    assertEquals(4.0, samplePointSizeM(design))
    // A tight grid keeps boxes at most half the spacing, so neighbors never touch.
    assertEquals(
      2.0,
      samplePointSizeM(design.copy(subPlot = SubPlotMode.GRID, subPlotSpacingM = 4.0)),
    )
    assertEquals(DEFAULT_SAMPLE_SIZE_M, samplePointSizeM(design.copy(plotSizeM = Double.NaN)))
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
