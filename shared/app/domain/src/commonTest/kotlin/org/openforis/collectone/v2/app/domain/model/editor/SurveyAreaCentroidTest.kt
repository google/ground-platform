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
package org.openforis.collectone.v2.app.domain.model.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.openforis.collectone.v2.map.LatLng

class SurveyAreaCentroidTest {
  @Test
  fun rectangle_centroidIsItsMiddle() {
    val area = SurveyArea(name = "Plot", parts = listOf(rectangle(-0.44, 36.92, -0.40, 36.98)))

    assertLatLng(LatLng(-0.42, 36.95), area.centroid)
  }

  @Test
  fun lShapedPart_centroidIsWeightedByArea() {
    // A 2 x 2 square with its top-right 1 x 1 quarter cut out.
    val lShape =
      listOf(
        LatLng(0.0, 0.0),
        LatLng(0.0, 2.0),
        LatLng(1.0, 2.0),
        LatLng(1.0, 1.0),
        LatLng(2.0, 1.0),
        LatLng(2.0, 0.0),
      )

    assertLatLng(LatLng(5.0 / 6, 5.0 / 6), SurveyAreaGeometry.centroid(listOf(lShape)))
  }

  @Test
  fun parts_countByArea_whicheverWayTheyWind() {
    val big = rectangle(0.0, 0.0, 3.0, 3.0) // area 9, centroid (1.5, 1.5)
    val small = rectangle(0.0, 10.0, 1.0, 11.0).reversed() // area 1, centroid (0.5, 10.5)

    assertLatLng(
      LatLng((9 * 1.5 + 0.5) / 10, (9 * 1.5 + 10.5) / 10),
      SurveyAreaGeometry.centroid(listOf(big, small)),
    )
  }

  @Test
  fun partsWithoutArea_averageTheirVertices() {
    val line = listOf(LatLng(1.0, 1.0), LatLng(3.0, 5.0))

    assertLatLng(LatLng(2.0, 3.0), SurveyAreaGeometry.centroid(listOf(line)))
  }

  @Test
  fun noVertices_hasNoCentroid() {
    assertNull(SurveyAreaGeometry.centroid(emptyList()))
    assertNull(SurveyArea(name = "Empty", parts = emptyList()).centroid)
  }

  private fun rectangle(south: Double, west: Double, north: Double, east: Double) =
    listOf(LatLng(north, west), LatLng(north, east), LatLng(south, east), LatLng(south, west))

  private fun assertLatLng(expected: LatLng, actual: LatLng?) {
    requireNotNull(actual)
    assertEquals(expected.latitude, actual.latitude, absoluteTolerance = 1e-9)
    assertEquals(expected.longitude, actual.longitude, absoluteTolerance = 1e-9)
  }
}
