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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HitTestTest {
  /** A flat test projection: 10 dp per degree, north up. */
  private val project = { p: LatLng -> DpOffset((p.longitude * 10).dp, (-p.latitude * 10).dp) }
  private val here = LatLng(0.0, 0.0)

  private fun tap(x: Float, y: Float) = DpOffset(x.dp, y.dp)

  private fun content(layer: MapLayer, vararg features: MapFeature) =
    MapContent(sources = listOf(GeoJsonSource("s", features.toList())), layers = listOf(layer))

  private val line =
    MapFeature("road", Geometry.LineString(listOf(LatLng(0.0, 0.0), LatLng(0.0, 10.0))))

  @Test
  fun line_isHitWithinHalfItsWidthPlusSlop() {
    // 2 dp wide: 1 dp each side plus the 6 dp slop.
    val layer = MapLayer.Line("roads", "s", color = StyleValue.Constant(Color.Black))
    val content = content(layer, line)

    assertEquals("road", hitTest(content, tap(50f, 6.9f), project, here)?.featureId)
    assertNull(hitTest(content, tap(50f, 7.5f), project, here))
  }

  @Test
  fun lineWidth_isEvaluatedPerFeature() {
    val layer =
      MapLayer.Line(
        "roads",
        "s",
        color = StyleValue.Constant(Color.Black),
        width = StyleValue.Match("kind", mapOf("highway" to 20.dp), default = 2.dp),
      )
    val wide = line.copy(properties = mapOf("kind" to "highway"))

    assertEquals("road", hitTest(content(layer, wide), tap(50f, 15f), project, here)?.featureId)
    assertNull(hitTest(content(layer, line), tap(50f, 15f), project, here))
  }

  @Test
  fun lineLayers_strokeClosedPolygonRings() {
    val square =
      MapFeature(
        "plot",
        Geometry.Polygon(
          listOf(listOf(LatLng(0.0, 0.0), LatLng(0.0, 10.0), LatLng(10.0, 10.0), LatLng(10.0, 0.0)))
        ),
      )
    val layer = MapLayer.Line("outline", "s", color = StyleValue.Constant(Color.Black))

    // On the closing edge from the last vertex back to the first (the western side).
    assertEquals("plot", hitTest(content(layer, square), tap(0f, -50f), project, here)?.featureId)
    // The middle of the polygon is not on its outline.
    assertNull(hitTest(content(layer, square), tap(50f, -50f), project, here))
  }

  @Test
  fun circle_isHitWithinItsRadiusPlusSlop() {
    val layer =
      MapLayer.Circle(
        "pins",
        "s",
        color = StyleValue.Constant(Color.Red),
        radius = StyleValue.Constant(4.dp),
      )
    val pin = MapFeature("pin", Geometry.Point(LatLng(0.0, 0.0)))

    assertEquals("pin", hitTest(content(layer, pin), tap(10f, 0f), project, here)?.featureId)
    assertNull(hitTest(content(layer, pin), tap(10.5f, 0f), project, here))
  }

  @Test
  fun symbol_usesAFixedHitRadius() {
    val layer = MapLayer.Symbol("icons", "s")
    val pin = MapFeature("pin", Geometry.Point(LatLng(0.0, 0.0)))

    assertEquals("pin", hitTest(content(layer, pin), tap(0f, 16f), project, here)?.featureId)
    assertNull(hitTest(content(layer, pin), tap(0f, 16.5f), project, here))
  }

  @Test
  fun filteredOutFeatures_areSkipped() {
    val layer =
      MapLayer.Line(
        "roads",
        "s",
        filter = FeatureFilter.Equals("kind", "highway"),
        color = StyleValue.Constant(Color.Black),
      )

    assertNull(hitTest(content(layer, line), tap(50f, 0f), project, here))
  }

  @Test
  fun laterFeaturesInASource_drawOnTopAndWin() {
    val layer = MapLayer.Line("roads", "s", color = StyleValue.Constant(Color.Black))
    val above = line.copy(id = "above")

    assertEquals(
      "above",
      hitTest(content(layer, line, above), tap(50f, 0f), project, here)?.featureId,
    )
  }

  @Test
  fun distanceToSegment_clampsToEndpointsAndHandlesDegenerateSegments() {
    val a = tap(0f, 0f)
    val b = tap(10f, 0f)

    assertEquals(5f, distanceToSegment(tap(5f, 5f), a, b))
    assertEquals(5f, distanceToSegment(tap(13f, 4f), a, b))
    assertEquals(5f, distanceToSegment(tap(3f, 4f), a, a))
  }

  @Test
  fun polygonContains_usesEvenOddRule() {
    val ring = listOf(tap(0f, 0f), tap(10f, 0f), tap(10f, 10f), tap(0f, 10f))

    assertTrue(polygonContains(ring, tap(5f, 5f)))
    assertFalse(polygonContains(ring, tap(15f, 5f)))
  }

  @Test
  fun points_andLineRings_ofMultiPartGeometry() {
    val a = LatLng(0.0, 0.0)
    val b = LatLng(1.0, 1.0)
    val c = LatLng(2.0, 0.0)
    val multi = Geometry.MultiPoint(listOf(Geometry.Point(a), Geometry.Point(b)))
    val triangle = Geometry.Polygon(listOf(listOf(a, b, c)))

    assertEquals(listOf(a, b), points(multi))
    assertEquals(emptyList(), points(triangle))
    assertEquals(listOf(listOf(a, b, c, a)), lineRings(triangle))
    assertEquals(emptyList(), lineRings(multi))
  }
}
