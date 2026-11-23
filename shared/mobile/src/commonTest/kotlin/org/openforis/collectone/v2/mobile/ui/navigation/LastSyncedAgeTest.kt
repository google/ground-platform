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
package org.openforis.collectone.v2.mobile.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class LastSyncedAgeTest {
  private val syncedAt = Instant.fromEpochMilliseconds(1_900_000_000_000L)

  private fun ageAfter(millis: Long) = lastSyncedAge(syncedAt, now = syncedAt + millis.milliseconds)

  @Test
  fun underAMinute_isJustNow() {
    assertEquals(LastSyncedAge.JustNow, ageAfter(59_999L))
  }

  @Test
  fun clockBehindTheSyncTime_isJustNow() {
    assertEquals(LastSyncedAge.JustNow, ageAfter(-5 * 60_000L))
  }

  @Test
  fun underAnHour_countsWholeMinutes() {
    assertEquals(LastSyncedAge.Minutes(1), ageAfter(60_000L))
    assertEquals(LastSyncedAge.Minutes(59), ageAfter(59 * 60_000L + 59_999L))
  }

  @Test
  fun underADay_countsWholeHours() {
    assertEquals(LastSyncedAge.Hours(1), ageAfter(60 * 60_000L))
    assertEquals(LastSyncedAge.Hours(23), ageAfter(24 * 60 * 60_000L - 1))
  }

  @Test
  fun aDayOrMore_countsWholeDays() {
    assertEquals(LastSyncedAge.Days(1), ageAfter(24 * 60 * 60_000L))
    assertEquals(LastSyncedAge.Days(3), ageAfter(3 * 24 * 60 * 60_000L + 1))
  }
}
