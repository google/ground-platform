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
package org.openforis.collectone.v2.web.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals

class VirtualDataTableTest {
  @Test
  fun visibleVariableColumnRange_handlesEmptyNarrowAndWideVariableColumns() {
    assertEquals(
      IntRange.EMPTY,
      visibleVariableColumnRange(computeColumnOffsetsPx(FloatArray(0)), 0f, 800),
    )

    val widths = FloatArray(250) { if (it % 3 == 0) 200f else 120f }
    val offsets = computeColumnOffsetsPx(widths)

    // Scroll at origin: cols 0..3 cover [0, 640), so 600px hits col 3; + overscan (2) => 0..5.
    val initial = visibleVariableColumnRange(offsets, scrollPx = 0f, viewportWidthPx = 600)
    assertEquals(0, initial.first)
    assertEquals(5, initial.last)

    // Scroll deep into whisp columns (~20,000 px): binary search lands right on the window.
    val mid = visibleVariableColumnRange(offsets, scrollPx = 20_000f, viewportWidthPx = 800)
    assertEquals(true, mid.first > 100 && mid.last < 160)
    assertEquals(true, (mid.last - mid.first) <= 12)

    // Scroll past end clamps cleanly to final column.
    val end = visibleVariableColumnRange(offsets, scrollPx = 100_000f, viewportWidthPx = 800)
    assertEquals(247..249, end)
  }
}
