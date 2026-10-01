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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MapContentDiffTest {
  private val point = MapFeature("p1", Geometry.Point(LatLng(1.0, 2.0)))
  private val sourceA = GeoJsonSource("a", listOf(point))
  private val sourceB = GeoJsonSource("b", emptyList())

  private fun circle(id: String, source: String = "a") =
    MapLayer.Circle(id, source, color = StyleValue.Constant(Color.Red))

  private val base =
    MapContent(sources = listOf(sourceA, sourceB), layers = listOf(circle("l1"), circle("l2")))

  @Test
  fun initialBuild_addsEverythingAfterBasemap() {
    val ops = diffMapContent(null, base)
    assertEquals(
      listOf(
        MapOp.SetBasemap(Basemap.MapboxOutdoors),
        MapOp.AddSource(sourceA),
        MapOp.AddSource(sourceB),
        MapOp.AddLayer(circle("l1"), null),
        MapOp.AddLayer(circle("l2"), null),
      ),
      ops,
    )
  }

  @Test
  fun unchangedContent_producesNoOps() {
    assertEquals(emptyList(), diffMapContent(base, base.copy()))
  }

  @Test
  fun basemapChange_rebuildsEverything() {
    val ops = diffMapContent(base, base.copy(basemap = Basemap.MapboxSatelliteStreets))
    assertEquals(diffMapContent(null, base.copy(basemap = Basemap.MapboxSatelliteStreets)), ops)
  }

  @Test
  fun changedFeatures_setSourceDataOnly() {
    val moved =
      sourceA.copy(features = listOf(point.copy(properties = mapOf("selected" to "true"))))
    val ops = diffMapContent(base, base.copy(sources = listOf(moved, sourceB)))
    assertEquals(listOf(MapOp.SetSourceData(moved)), ops)
  }

  @Test
  fun changedStyle_updatesLayerInPlace() {
    val blue = circle("l2").copy(color = StyleValue.Constant(Color.Blue))
    val ops = diffMapContent(base, base.copy(layers = listOf(circle("l1"), blue)))
    assertEquals(listOf(MapOp.UpdateLayer(blue)), ops)
  }

  @Test
  fun insertedLayers_areAddedBelowTheirUpperNeighbour() {
    val new =
      base.copy(
        layers = listOf(circle("l0"), circle("l1"), circle("mid"), circle("l2"), circle("top"))
      )
    val ops = diffMapContent(base, new)
    assertEquals(
      listOf(
        MapOp.AddLayer(circle("top"), null),
        MapOp.AddLayer(circle("mid"), "l2"),
        MapOp.AddLayer(circle("l0"), "l1"),
      ),
      ops,
    )
  }

  @Test
  fun reorderedLayers_removeAndReAddAll() {
    val new = base.copy(layers = listOf(circle("l2"), circle("l1")))
    val ops = diffMapContent(base, new)
    assertEquals(
      listOf(
        MapOp.RemoveLayer("l2"),
        MapOp.RemoveLayer("l1"),
        MapOp.AddLayer(circle("l2"), null),
        MapOp.AddLayer(circle("l1"), null),
      ),
      ops,
    )
  }

  @Test
  fun removedSource_isRemovedAfterItsLayers() {
    val old = base.copy(layers = listOf(circle("l1"), circle("onB", source = "b")))
    val new = base.copy(sources = listOf(sourceA), layers = listOf(circle("l1")))
    val ops = diffMapContent(old, new)
    assertEquals(listOf(MapOp.RemoveLayer("onB"), MapOp.RemoveSource("b")), ops)
  }

  @Test
  fun layerMovedToAnotherSource_isReAdded() {
    val new = base.copy(layers = listOf(circle("l1"), circle("l2", source = "b")))
    val ops = diffMapContent(base, new)
    assertEquals(
      listOf(MapOp.RemoveLayer("l2"), MapOp.AddLayer(circle("l2", source = "b"), null)),
      ops,
    )
  }

  @Test
  fun changedIcon_isReplacedBeforeLayersUseIt() {
    val old = base.copy(icons = listOf(MapIcon("pin", "<svg/>")))
    val newIcon = MapIcon("pin", "<svg id='new'/>")
    val ops = diffMapContent(old, old.copy(icons = listOf(newIcon)))
    assertEquals(listOf(MapOp.RemoveIcon("pin"), MapOp.AddIcon(newIcon)), ops)
  }

  @Test
  fun userLocation_changesAreReported() {
    val here = LatLng(10.0, 20.0)
    assertEquals(
      listOf(MapOp.SetUserLocation(here)),
      diffMapContent(base, base.copy(userLocation = here)),
    )
  }

  @Test
  fun markers_areNotRendererOps() {
    val ops = diffMapContent(base, base.copy(markers = listOf(MapMarker("m", LatLng(0.0, 0.0)))))
    assertTrue(ops.isEmpty())
  }

  @Test
  fun content_rejectsLayersWithUnknownSources() {
    assertFailsWith<IllegalArgumentException> { MapContent(layers = listOf(circle("l", "nope"))) }
  }
}
