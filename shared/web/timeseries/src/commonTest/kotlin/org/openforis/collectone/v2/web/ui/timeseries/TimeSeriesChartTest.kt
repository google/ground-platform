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

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimeSeriesChartTest {
  @Test
  fun chartPoints_filtersMaskedValues_andPadsFlatSeries() {
    val points =
      ChartPoints.of(
        timesMs = listOf(1_000L, 2_000L, 3_000L),
        values = listOf(Double.NaN, 0.42, 0.42),
      )

    assertFalse(points.isEmpty)
    assertEquals(listOf(1, 2), points.indices)
    assertEquals(2_000L, points.minTime)
    assertEquals(3_000L, points.maxTime)
    assertTrue(points.minValue < 0.42 && points.maxValue > 0.42)

    val empty = ChartPoints.of(timesMs = listOf(1_000L), values = listOf(Double.NaN))
    assertTrue(empty.isEmpty)
  }

  @Test
  fun chartLayout_mapsOffsetsAndFindsNearestValidObservation() {
    val points =
      ChartPoints.of(
        timesMs = listOf(1_000L, 2_000L, 3_000L),
        values = listOf(0.2, Double.NaN, 0.8),
      )
    val layout = ChartLayout(Size(400f, 200f), points, Density(1f))

    assertNull(layout.offsetOf(1))
    val first = assertNotNull(layout.offsetOf(0))
    val last = assertNotNull(layout.offsetOf(2))
    assertTrue(first.x < last.x)
    assertTrue(first.y > last.y)

    assertEquals(0, layout.nearest(first.x + 2f))
    assertEquals(2, layout.nearest(last.x - 2f))

    val tooNarrow = ChartLayout(Size(20f, 200f), points, Density(1f))
    assertNull(tooNarrow.nearest(10f))
  }

  @Test
  fun niceTicks_andUtcYearHelpers_coverRangesAndBoundaries() {
    val ticks = niceTicks(0.05, 0.92, count = 5)
    assertTrue(ticks.isNotEmpty())
    assertTrue(ticks.all { it in 0.0..1.0 })

    assertEquals(listOf(0.5), niceTicks(0.5, 0.5, count = 5))
    assertEquals(listOf(0.2), niceTicks(0.2, 0.8, count = 0))

    val start2024 = yearStartMs(2024)
    assertEquals(2024, yearOf(start2024))
    assertEquals(2023, yearOf(start2024 - 1L))
  }
}
