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
package org.openforis.collectone.v2.app.client.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * File names, labels, and timestamps of record reports: pure text formatting that needs no string
 * resources, so it also runs as an Android unit test.
 */
class RecordPdfFormattingTest {
  @Test
  fun fileNamesAndLabels_areSafeAndReadable() {
    assertEquals(
      "coffee-parcel-eudr-gh-0419",
      RecordPdfReports.slug("Coffee Parcel / EUDR-GH-0419"),
    )
    assertEquals("record", RecordPdfReports.slug("中文"))
    assertEquals("S2-10c4a89e2f", RecordPdfReports.slug("S2:10c4a89e2f", lowercase = false))
    assertEquals("Site visit › Canopy", RecordPdfReports.humanizeGroupPath("site_visit/canopy"))
  }

  @Test
  fun formatUtcTimestamp_convertsInstants() {
    assertEquals("1970-01-01 00:00 UTC", formatUtcTimestamp(Instant.fromEpochMilliseconds(0)))
    assertEquals(
      "2000-02-29 23:59 UTC",
      formatUtcTimestamp(Instant.fromEpochMilliseconds(951_868_740_000)),
    )
    assertEquals(
      "2026-10-01 16:38 UTC",
      formatUtcTimestamp(Instant.fromEpochMilliseconds(1_790_872_680_000)),
    )
  }

  @Test
  fun formatUtcTimestamp_dropsSecondsAndMilliseconds() {
    assertEquals(
      "2026-10-01 16:38 UTC",
      formatUtcTimestamp(Instant.fromEpochMilliseconds(1_790_872_739_999)),
    )
  }

  @Test
  fun formatUtcTimestamp_beforeTheEpoch_roundsDownToTheMinute() {
    assertEquals("1969-12-31 23:59 UTC", formatUtcTimestamp(Instant.fromEpochMilliseconds(-1)))
  }
}
