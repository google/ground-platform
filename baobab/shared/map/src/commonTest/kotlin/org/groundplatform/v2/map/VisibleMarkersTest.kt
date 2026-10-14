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

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals

class VisibleMarkersTest {
  private val viewport = IntSize(400, 300)

  private fun marker(id: String, collides: Boolean = true) =
    MapMarker(id, LatLng(0.0, 0.0), collides = collides)

  private fun visible(markers: List<MapMarker>, bounds: List<IntRect>, gap: Int = 0) =
    visibleMarkers(markers, bounds, viewport, gap).toList()

  @Test
  fun overlappingCollidingMarkers_topmostWins() {
    val markers = listOf(marker("a"), marker("b"), marker("c"))
    val bounds =
      listOf(IntRect(10, 10, 60, 30), IntRect(40, 20, 90, 40), IntRect(200, 200, 250, 220))
    assertEquals(listOf(false, true, true), visible(markers, bounds))
  }

  @Test
  fun separatedCollidingMarkers_allShow() {
    val markers = listOf(marker("a"), marker("b"))
    val bounds = listOf(IntRect(10, 10, 60, 30), IntRect(70, 10, 120, 30))
    assertEquals(listOf(true, true), visible(markers, bounds))
  }

  @Test
  fun markersCloserThanGap_collide() {
    val markers = listOf(marker("a"), marker("b"))
    val bounds = listOf(IntRect(10, 10, 60, 30), IntRect(62, 10, 112, 30))
    assertEquals(listOf(false, true), visible(markers, bounds, gap = 4))
  }

  @Test
  fun hiddenMarker_doesNotHideOthers() {
    // "c" hides "b", so "b" doesn't hide "a".
    val markers = listOf(marker("a"), marker("b"), marker("c"))
    val bounds = listOf(IntRect(0, 10, 50, 30), IntRect(40, 10, 90, 30), IntRect(80, 10, 130, 30))
    assertEquals(listOf(true, false, true), visible(markers, bounds))
  }

  @Test
  fun nonCollidingMarkers_alwaysShowAndNeverHideOthers() {
    val markers = listOf(marker("label"), marker("chip", collides = false))
    val bounds = listOf(IntRect(10, 10, 60, 30), IntRect(10, 10, 60, 30))
    assertEquals(listOf(true, true), visible(markers, bounds))
  }

  @Test
  fun markerJustOffScreen_isHiddenButStillHidesOverlappingLabel() {
    val markers = listOf(marker("a"), marker("b"))
    val bounds = listOf(IntRect(-40, 10, 10, 30), IntRect(-90, 10, -30, 30))
    assertEquals(listOf(false, false), visible(markers, bounds))
  }
}
