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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.EntityRow
import org.openforis.collectone.v2.app.domain.model.editor.SamplePlotProperties
import org.openforis.collectone.v2.app.domain.model.editor.toGeoCoord
import org.openforis.collectone.v2.app.domain.model.editor.toLatLng
import org.openforis.collectone.v2.core.sampling.PlotGeometry
import org.openforis.collectone.v2.map.Basemap
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.map.MapContent

/**
 * The overlays of [LayerEditorMapContent.kt]'s builders that [LayerEditorGeometryTest] doesn't
 * cover: the backdrop polygons, which sample points are drawn, and the sample point rings.
 */
class LayerEditorMapContentTest {
  private val ring = listOf(LatLng(0.0, 0.0), LatLng(0.0, 1.0), LatLng(1.0, 1.0))

  private fun plots(vararg samples: Pair<String, List<LatLng>?>) =
    EntityDataset(
      key = "plots",
      kind = DatasetKind.MAP_LAYER,
      id = "plot",
      displayName = "Plots",
      geometryType = GeometryType.POLYGON,
      keyProperty = "id",
      labelProperty = "id",
      properties = emptyList(),
      rows =
        samples.map { (key, points) ->
          EntityRow(
            key,
            points
              ?.let {
                mapOf(SamplePlotProperties.SAMPLES to SamplePlotProperties.formatGeotrace(it))
              }
              .orEmpty(),
            ring,
          )
        },
    )

  @Test
  fun withBackdropPolygons_drawsUsableRingsUnderTheLayer() {
    val content = layerEditorContent(plots("a" to null), null, Color.Red, Basemap.None())
    val sliver = listOf(LatLng(0.0, 0.0), LatLng(1.0, 1.0))

    val withBackdrop = withBackdropPolygons(content, listOf(ring, sliver), Color.Blue)

    val backdrop = withBackdrop.sources.first()
    assertEquals(LAYER_EDITOR_BACKDROP_SOURCE, backdrop.id)
    assertEquals(listOf("backdrop-1"), backdrop.features.map { it.id }, "two-vertex rings dropped")
    assertEquals(content.sources, withBackdrop.sources.drop(1))
    assertTrue(withBackdrop.layers.take(3).all { it.sourceId == LAYER_EDITOR_BACKDROP_SOURCE })
    assertEquals(content.layers, withBackdrop.layers.drop(3))
  }

  @Test
  fun withBackdropPolygons_withoutUsableRings_returnsTheContentUnchanged() {
    val content = MapContent()

    assertSame(content, withBackdropPolygons(content, emptyList(), Color.Blue))
    assertSame(content, withBackdropPolygons(content, listOf(ring.take(2)), Color.Blue))
  }

  @Test
  fun visiblePlotSamples_putsTheSelectedPlotFirst_andSkipsPlotsWithoutSamples() {
    val a = listOf(LatLng(1.0, 1.0), LatLng(1.0, 2.0))
    val b = listOf(LatLng(2.0, 1.0))
    val dataset = plots("a" to a, "none" to null, "b" to b)

    assertEquals(a + b, visiblePlotSamples(dataset))
    assertEquals(b + a, visiblePlotSamples(dataset, selectedRow = "b"))
    assertEquals(a + b, visiblePlotSamples(dataset, selectedRow = "missing"))
  }

  @Test
  fun visiblePlotSamples_isCapped() {
    val a = listOf(LatLng(1.0, 1.0), LatLng(1.0, 2.0))
    val b = listOf(LatLng(2.0, 1.0), LatLng(2.0, 2.0))
    val dataset = plots("a" to a, "b" to b)

    assertEquals(listOf(b[0], b[1], a[0]), visiblePlotSamples(dataset, "b", maxSamples = 3))
    assertEquals(listOf(a[0]), visiblePlotSamples(dataset, maxSamples = 1))
    assertTrue(visiblePlotSamples(dataset, maxSamples = 0).isEmpty())
    assertTrue(visiblePlotSamples(plots()).isEmpty())
  }

  @Test
  fun groundSquare_matchesPlotGeometrySubPlotBox() {
    val center = LatLng(60.0, 10.0)
    val box = groundSquare(center, sizeM = 10.0)
    val expected =
      PlotGeometry.subPlotBox(center.toGeoCoord(), 10.0).dropLast(1).map { it.toLatLng() }

    assertEquals(4, box.size)
    assertEquals(expected, box)
  }

  @Test
  fun withSamplePointsOverlay_drawsOneSquareBoxPerSampleOnTop() {
    val content = layerEditorContent(plots("a" to null), null, Color.Red, Basemap.None())
    val samples = listOf(LatLng(1.0, 1.0), LatLng(1.0, 2.0))

    val overlaid = withSamplePointsOverlay(content, samples, Color.Red, sizeM = 3.0)

    val source = overlaid.sources.last()
    assertEquals(LAYER_EDITOR_SAMPLES_SOURCE, source.id)
    assertEquals(listOf("sample-1", "sample-2"), source.features.map { it.id })
    assertTrue(source.features.all { it.geometry is Geometry.Polygon })
    assertTrue(overlaid.layers.takeLast(2).all { it.sourceId == LAYER_EDITOR_SAMPLES_SOURCE })
  }
}
