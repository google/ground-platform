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
import kotlin.time.Instant

class IsoTimestampsTest {

  @Test
  fun isoUtc_unixEpoch() {
    assertEquals("1970-01-01T00:00:00Z", isoUtc(Instant.fromEpochMilliseconds(0L)))
  }

  @Test
  fun isoUtc_leapDay() {
    assertEquals("2000-02-29T23:59:59Z", isoUtc(Instant.fromEpochMilliseconds(951_868_799_000L)))
  }

  @Test
  fun isoUtc_dropsMilliseconds() {
    assertEquals("2026-10-05T16:34:41Z", isoUtc(Instant.fromEpochMilliseconds(1_791_218_081_999L)))
  }

  @Test
  fun isoUtc_beforeTheEpoch_roundsDownToTheSecond() {
    assertEquals("1969-12-31T23:59:59Z", isoUtc(Instant.fromEpochMilliseconds(-1L)))
  }

  @Test
  fun parseIsoUtc_dateTime() {
    assertEquals(
      Instant.fromEpochMilliseconds(1_791_218_081_000L),
      parseIsoUtc("2026-10-05T16:34:41Z"),
    )
  }

  @Test
  fun parseIsoUtc_bareDate_isUtcMidnight() {
    assertEquals(Instant.fromEpochMilliseconds(1_791_158_400_000L), parseIsoUtc("2026-10-05"))
  }

  @Test
  fun parseIsoUtc_withoutSeconds() {
    assertEquals(
      Instant.fromEpochMilliseconds(1_791_218_040_000L),
      parseIsoUtc("2026-10-05T16:34Z"),
    )
  }

  @Test
  fun parseIsoUtc_ignoresFractionalSeconds() {
    assertEquals(
      Instant.fromEpochMilliseconds(1_791_218_081_000L),
      parseIsoUtc("2026-10-05T16:34:41.987Z"),
    )
  }

  @Test
  fun parseIsoUtc_trimsWhitespace() {
    assertEquals(Instant.fromEpochMilliseconds(0L), parseIsoUtc(" 1970-01-01T00:00:00Z "))
  }

  @Test
  fun parseIsoUtc_isTheInverseOfIsoUtc() {
    listOf(0L, 951_782_400_000L, 1_791_331_200_000L, 1_802_000_123_000L, -86_400_000L)
      .map(Instant::fromEpochMilliseconds)
      .forEach {
        assertEquals(it, parseIsoUtc(isoUtc(it)))
      }
  }

  @Test
  fun parseIsoUtc_notUtc_isNull() {
    assertNull(parseIsoUtc("2026-10-05T16:34:41+02:00"))
    assertNull(parseIsoUtc("2026-10-05T16:34:41"))
  }

  @Test
  fun parseIsoUtc_notADate_isNull() {
    assertNull(parseIsoUtc("not a date"))
    assertNull(parseIsoUtc("2026-13-01T00:00:00Z"))
    assertNull(parseIsoUtc("26-10-05"))
  }

  @Test
  fun parseIsoUtc_dayPastTheEndOfTheMonth_isNull() {
    assertNull(parseIsoUtc("2026-02-29T00:00:00Z"))
    assertNull(parseIsoUtc("2026-04-31"))
    assertEquals(Instant.fromEpochMilliseconds(1_709_164_800_000L), parseIsoUtc("2024-02-29"))
  }

  @Test
  fun parseIsoUtc_timeOutOfRange_isNull() {
    assertNull(parseIsoUtc("2026-10-05T24:00:00Z"))
    assertNull(parseIsoUtc("2026-10-05T16:60:00Z"))
    assertNull(parseIsoUtc("2026-10-05T16:34:60Z"))
  }
}
