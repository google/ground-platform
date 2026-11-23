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
package org.openforis.collectone.v2.app.domain.model.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DatePickerConversionsTest {

  @Test
  fun isoDateToUtcMillis_unixEpoch_isZero() {
    assertEquals(0L, isoDateToUtcMillis("1970-01-01"))
  }

  @Test
  fun isoDateToUtcMillis_leapDay_isUtcMidnight() {
    assertEquals(1_709_164_800_000L, isoDateToUtcMillis("2024-02-29"))
  }

  @Test
  fun isoDateToUtcMillis_beforeEpoch_isNegative() {
    assertEquals(-25_509L * 86_400_000L, isoDateToUtcMillis("1900-02-28"))
  }

  @Test
  fun isoDateToUtcMillis_yearZero_isSupported() {
    assertEquals(-719_528L * 86_400_000L, isoDateToUtcMillis("0000-01-01"))
  }

  @Test
  fun isoDateToUtcMillis_februaryTwentyNinthOfCommonYear_returnsNull() {
    assertNull(isoDateToUtcMillis("2023-02-29"))
  }

  @Test
  fun isoDateToUtcMillis_centuryNonLeapYear_rejectsFebruaryTwentyNinth() {
    assertNull(isoDateToUtcMillis("1900-02-29"))
  }

  @Test
  fun isoDateToUtcMillis_monthThirteen_returnsNull() {
    assertNull(isoDateToUtcMillis("2024-13-01"))
  }

  @Test
  fun isoDateToUtcMillis_dateTime_returnsNull() {
    assertNull(isoDateToUtcMillis("2024-02-29T00:00:00Z"))
  }

  @Test
  fun isoDateToUtcMillis_negativeYear_returnsNull() {
    assertNull(isoDateToUtcMillis("-0001-12-31"))
  }

  @Test
  fun utcMillisToIsoDate_lastMillisecondBeforeEpoch_isPreviousDay() {
    assertEquals("1969-12-31", utcMillisToIsoDate(-1L))
  }

  @Test
  fun utcMillisToIsoDate_lateInTheDay_staysOnTheSameDay() {
    assertEquals("2024-02-29", utcMillisToIsoDate(1_709_164_800_000L + 86_399_999L))
  }

  @Test
  fun utcMillisToIsoDate_smallYear_isZeroPadded() {
    assertEquals("0042-03-05", utcMillisToIsoDate(isoDateToUtcMillis("0042-03-05")!!))
  }

  @Test
  fun friendlyDate_validDate_usesShortMonthName() {
    assertEquals("Mar 5, 2026", friendlyDate("2026-03-05"))
  }

  @Test
  fun friendlyDate_invalidDate_returnsInputUnchanged() {
    assertEquals("2026-02-30", friendlyDate("2026-02-30"))
  }
}
