/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.web.ui.timeseries

import kotlin.test.Test
import kotlin.test.assertEquals

class TimeSeriesYearAxisTest {

  @Test
  fun yearOf_isTheUtcCalendarYear() {
    assertEquals(2026, yearOf(1_767_225_600_000L)) // 2026-01-01T00:00:00Z
    assertEquals(2025, yearOf(1_767_225_599_999L))
    assertEquals(1969, yearOf(-1L))
  }

  @Test
  fun yearStartMs_isNewYearUtc() {
    assertEquals(0L, yearStartMs(1970))
    assertEquals(1_577_836_800_000L, yearStartMs(2020)) // 2020-01-01T00:00:00Z
    assertEquals(1_767_225_600_000L, yearStartMs(2026)) // 2026-01-01T00:00:00Z
  }
}
