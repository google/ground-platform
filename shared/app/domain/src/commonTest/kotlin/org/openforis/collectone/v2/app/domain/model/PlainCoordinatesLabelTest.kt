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
package org.openforis.collectone.v2.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import org.openforis.collectone.v2.map.LatLng

class PlainCoordinatesLabelTest {
  @Test
  fun southernAndEasternHemispheres() {
    assertEquals("0.4182°S, 36.9491°E", plainCoordinatesLabel(LatLng(-0.4182, 36.9491)))
  }

  @Test
  fun northernAndWesternHemispheres() {
    assertEquals("9.8249°N, 62.2159°W", plainCoordinatesLabel(LatLng(9.8249, -62.2159)))
  }

  @Test
  fun roundsToFourDecimals() {
    assertEquals("0.4194°S, 36.9510°E", plainCoordinatesLabel(LatLng(-0.41936, 36.95104)))
  }

  @Test
  fun padsShortFractions() {
    assertEquals("3.0000°S, 36.7000°E", plainCoordinatesLabel(LatLng(-3.0, 36.7)))
  }

  @Test
  fun valuesThatRoundToZero_takeTheNorthernAndEasternHemispheres() {
    assertEquals("0.0000°N, 0.0000°E", plainCoordinatesLabel(LatLng(-0.00001, -0.00004)))
  }
}
