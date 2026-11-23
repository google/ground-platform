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
package org.openforis.collectone.v2.app.ui.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.openforis.collectone.v2.map.LatLng

class CoordinatesTextTest {
  private fun english(latitude: Double, longitude: Double): String = runBlocking {
    coordinatesText(LatLng(latitude, longitude)).resolveString()
  }

  @Test
  fun southAndEast() {
    assertEquals("0.4160°S, 36.9470°E", english(-0.416, 36.947))
  }

  @Test
  fun northAndWest() {
    assertEquals("6.6900°N, 1.6200°W", english(6.69, -1.62))
  }

  @Test
  fun valuesRoundToFourDecimals() {
    assertEquals("0.4183°S, 36.9491°E", english(-0.418251, 36.94914))
  }

  @Test
  fun valuesThatRoundToZero_areNorthAndEast() {
    assertEquals("0.0000°N, 0.0000°E", english(-0.00004, -0.00004))
  }
}
