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

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LngLatBoundsTest {
  @Test
  fun area_oneDegreeSquareAtTheEquator() {
    assertEquals(12_364.0, LngLatBounds(0.0, 0.0, 1.0, 1.0).areaSquareKilometers(), 1.0)
  }

  @Test
  fun area_sevenKilometreSquare() {
    // 7 km along a meridian or the equator is 7 / (R × π / 180) degrees.
    val side = 7.0 / (LngLatBounds.EARTH_RADIUS_KM * PI / 180)
    assertEquals(
      49.0,
      LngLatBounds(36.9, -side / 2, 36.9 + side, side / 2).areaSquareKilometers(),
      0.1,
    )
  }

  @Test
  fun area_shrinksTowardThePoles() {
    val equator = LngLatBounds(10.0, 0.0, 11.0, 1.0).areaSquareKilometers()
    val sixty = LngLatBounds(10.0, 60.0, 11.0, 61.0).areaSquareKilometers()
    // cos 60.5° ≈ 0.492.
    assertEquals(0.492, sixty / equator, 0.002)
  }

  @Test
  fun area_doesNotDependOnHemisphereOrCornerOrder() {
    val north = LngLatBounds(10.0, 40.0, 11.0, 41.0).areaSquareKilometers()
    assertEquals(north, LngLatBounds(10.0, -41.0, 11.0, -40.0).areaSquareKilometers(), 1e-6)
    assertEquals(north, LngLatBounds(10.0, 41.0, 11.0, 40.0).areaSquareKilometers(), 1e-6)
  }

  @Test
  fun area_zeroSizeBoundsHaveNoArea() {
    assertEquals(0.0, LngLatBounds.of(listOf(LatLng(-0.4, 36.9))).areaSquareKilometers())
  }

  @Test
  fun crossesAntimeridian_onlyWhenWestLiesEastOfEast() {
    assertFalse(LngLatBounds(-10.0, 0.0, 10.0, 1.0).crossesAntimeridian)
    assertFalse(LngLatBounds(5.0, 0.0, 5.0, 1.0).crossesAntimeridian)
    assertTrue(LngLatBounds(179.5, 0.0, -179.5, 1.0).crossesAntimeridian)
  }
}
