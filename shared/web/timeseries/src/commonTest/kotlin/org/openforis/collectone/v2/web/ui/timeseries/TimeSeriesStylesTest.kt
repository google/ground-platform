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
package org.openforis.collectone.v2.web.ui.timeseries

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimeSeriesStylesTest {
  @Test
  fun everyBand_hasItsOwnColorWithinItsSource() {
    for (source in TimeSeriesUiState.SOURCES) {
      val bands = TimeSeriesUiState.bandsOf(source)
      bands.forEach { band -> assertTrue(TimeSeriesStyles.isKnown(band), "$band has no style") }
      val styles = bands.map(TimeSeriesStyles::band)
      val sourceColor = TimeSeriesStyles.source(source).color
      for (tone in listOf(ThemedColor::light, ThemedColor::dark)) {
        val colors = styles.map { tone(it.color) }
        assertEquals(colors.size, colors.toSet().size, "$source bands share a color")
        assertTrue(tone(sourceColor) !in colors, "$source shares a color with one of its bands")
      }
    }
  }

  @Test
  fun eachMenuSection_isOneRunOfBands() {
    for (source in TimeSeriesUiState.SOURCES) {
      val groups = TimeSeriesUiState.bandsOf(source).map { TimeSeriesStyles.band(it).group }
      val runs = 1 + groups.zipWithNext().count { (previous, next) -> previous != next }
      assertEquals(groups.toSet().size, runs, "$source repeats a band menu section")
    }
  }

  @Test
  fun unknownBands_stillGetAStyle() {
    assertTrue(!TimeSeriesStyles.isKnown("NEW_INDEX"))
    assertEquals(null, TimeSeriesStyles.band("NEW_INDEX").name)
  }
}
