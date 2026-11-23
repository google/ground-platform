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
package org.openforis.collectone.v2.core.forms.temporal

import com.google.type.Date
import com.google.type.TimeOfDay
import com.squareup.wire.ofEpochSecond
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.openforis.collectone.v2.core.forms.serialization.FormDefXmlDeserializer
import org.openforis.collectone.v2.core.forms.serialization.FormDefXmlSerializer
import org.openforis.collectone.v2.core.forms.serialization.RecordInstanceXmlSerializer
import org.openforis.collectone.v2.core.forms.xpath.model.TemporalUtils

/**
 * Pins the civil-calendar and ISO-8601 behavior of the XPath temporal functions and the XML
 * serializers, including their quirks on invalid input, so that sharing one implementation cannot
 * change what users see.
 */
class IsoDateTimeBehaviorTest {

  @Test
  fun epochDays_unixEpoch_isZero() {
    assertEquals(0L, TemporalUtils.dateToEpochDays(1970, 1, 1))
  }

  @Test
  fun epochDays_dayBeforeUnixEpoch_isMinusOne() {
    assertEquals(-1L, TemporalUtils.dateToEpochDays(1969, 12, 31))
  }

  @Test
  fun epochDays_leapDay_countsFebruary29() {
    assertEquals(19782L, TemporalUtils.dateToEpochDays(2024, 2, 29))
  }

  @Test
  fun epochDays_centuryNonLeapYear_skipsFebruary29() {
    assertEquals(-25509L, TemporalUtils.dateToEpochDays(1900, 2, 28))
    assertEquals(-25508L, TemporalUtils.dateToEpochDays(1900, 3, 1))
  }

  @Test
  fun epochDays_fourHundredYearLeapYear_countsFebruary29() {
    assertEquals(11017L, TemporalUtils.dateToEpochDays(2000, 3, 1))
  }

  @Test
  fun epochDays_yearZero_isProlepticLeapYear() {
    assertEquals(-719528L, TemporalUtils.dateToEpochDays(0, 1, 1))
    assertEquals(-719468L, TemporalUtils.dateToEpochDays(0, 3, 1))
  }

  @Test
  fun epochDays_negativeYear_usesProlepticGregorianCalendar() {
    assertEquals(-719529L, TemporalUtils.dateToEpochDays(-1, 12, 31))
  }

  @Test
  fun epochDays_lastDayOfYear9999_isSupported() {
    assertEquals(2932896L, TemporalUtils.dateToEpochDays(9999, 12, 31))
  }

  @Test
  fun epochDays_outOfRangeMonthAndDay_overflowArithmetically() {
    // Callers that skip validation rely on the formula rolling over rather than failing.
    assertEquals(
      TemporalUtils.dateToEpochDays(2025, 1, 1),
      TemporalUtils.dateToEpochDays(2024, 13, 1),
    )
    assertEquals(
      TemporalUtils.dateToEpochDays(2024, 2, 1),
      TemporalUtils.dateToEpochDays(2024, 1, 32),
    )
    assertEquals(-719560L, TemporalUtils.dateToEpochDays(0, 0, 0))
  }

  @Test
  fun civilDate_fromEpochDays_invertsEpochDaysAcrossEras() {
    var days = -1_000_000L
    while (days <= 3_000_000L) {
      val date = TemporalUtils.epochDaysToDate(days)
      assertEquals(days, TemporalUtils.dateToEpochDays(date.year, date.month, date.day))
      days += 997L
    }
  }

  @Test
  fun civilDate_fromNegativeEpochDays_returnsNegativeYear() {
    assertEquals(Date(year = -1, month = 12, day = 31), TemporalUtils.epochDaysToDate(-719529L))
  }

  @Test
  fun civilDate_fromLeapDayEpochDays_returnsFebruary29() {
    assertEquals(Date(year = 2024, month = 2, day = 29), TemporalUtils.epochDaysToDate(19782L))
  }

  @Test
  fun formatDate_padsYearMonthAndDay() {
    assertEquals("0042-03-05", TemporalUtils.formatDate(Date(year = 42, month = 3, day = 5)))
  }

  @Test
  fun formatDate_negativeYear_keepsLegacyPadding() {
    // Quirk: the sign is padded like a digit.
    assertEquals("00-1-12-31", TemporalUtils.formatDate(Date(year = -1, month = 12, day = 31)))
  }

  @Test
  fun formatTime_withoutNanos_omitsFraction() {
    assertEquals(
      "07:05:09",
      TemporalUtils.formatTime(TimeOfDay(hours = 7, minutes = 5, seconds = 9)),
    )
  }

  @Test
  fun formatTime_withNanos_truncatesToMillis() {
    assertEquals(
      "07:05:09.012",
      TemporalUtils.formatTime(TimeOfDay(hours = 7, minutes = 5, seconds = 9, nanos = 12_999_999)),
    )
  }

  @Test
  fun formatTimestamp_beforeEpoch_borrowsFromPreviousDay() {
    assertEquals("1969-12-31T23:59:59Z", TemporalUtils.formatTimestamp(ofEpochSecond(-1L, 0L)))
  }

  @Test
  fun formatTimestamp_withSubMillisecondNanos_writesZeroMillis() {
    assertEquals("1970-01-01T00:00:00.000Z", TemporalUtils.formatTimestamp(ofEpochSecond(0L, 500L)))
  }

  @Test
  fun formatTimestamp_withMillis_writesMillis() {
    assertEquals(
      "2024-02-29T12:00:00.005Z",
      TemporalUtils.formatTimestamp(ofEpochSecond(1709208000L, 5_000_000L)),
    )
  }

  @Test
  fun formDefSerializer_formatsInstantLikeXPathFormatter() {
    for (instant in
      listOf(
        ofEpochSecond(-1L, 0L),
        ofEpochSecond(0L, 500L),
        ofEpochSecond(1709208000L, 5_000_000L),
        ofEpochSecond(-62_167_219_200L, 0L),
      )) {
      assertEquals(
        TemporalUtils.formatTimestamp(instant),
        FormDefXmlSerializer.formatInstantIso8601(instant),
      )
    }
  }

  @Test
  fun recordSerializer_formatsInstantWithAlwaysPresentMillis() {
    assertEquals(
      "1969-12-31T23:59:59.000Z",
      RecordInstanceXmlSerializer.formatIsoInstant(ofEpochSecond(-1L, 0L)),
    )
    assertEquals(
      "2024-02-29T12:00:00.005Z",
      RecordInstanceXmlSerializer.formatIsoInstant(ofEpochSecond(1709208000L, 5_000_000L)),
    )
  }

  @Test
  fun tryParseDate_monthOutOfRange_returnsNull() {
    assertNull(TemporalUtils.tryParseDate("2024-13-01"))
  }

  @Test
  fun tryParseDate_dayBeyondMonthLength_isAccepted() {
    assertEquals(Date(year = 2023, month = 2, day = 30), TemporalUtils.tryParseDate("2023-02-30"))
  }

  @Test
  fun tryParseToEpochDays_positiveOffset_convertsToUtc() {
    assertEquals(
      19782.0 + 36_000.0 / 86_400.0,
      TemporalUtils.tryParseToEpochDays("2024-02-29T12:00:00+02:00")!!,
      1e-9,
    )
  }

  @Test
  fun tryParseToEpochDays_negativeOffset_convertsToUtc() {
    assertEquals(
      19782.0 + 55_800.0 / 86_400.0,
      TemporalUtils.tryParseToEpochDays("2024-02-29T12:00:00-03:30")!!,
      1e-9,
    )
  }

  @Test
  fun tryParseToEpochDays_invalidText_returnsNull() {
    assertNull(TemporalUtils.tryParseToEpochDays("not a date"))
  }

  @Test
  fun formDefLiteral_positiveOffset_convertsToUtc() {
    assertEquals(
      ofEpochSecond(1709200800L, 0L),
      FormDefXmlDeserializer.parseTimestampLiteral("2024-02-29T12:00:00+02:00"),
    )
  }

  @Test
  fun formDefLiteral_negativeOffset_convertsToUtc() {
    assertEquals(
      ofEpochSecond(1709220600L, 0L),
      FormDefXmlDeserializer.parseTimestampLiteral("2024-02-29T12:00:00-03:30"),
    )
  }

  @Test
  fun formDefLiteral_offset_matchesRecordValueParsing() {
    val text = "2024-02-29T23:30:00.250+05:45"

    assertEquals(
      RecordInstanceXmlSerializer.parseIsoInstant(text),
      FormDefXmlDeserializer.parseTimestampLiteral(text),
    )
  }

  @Test
  fun formDefLiteral_timestampWithFraction_keepsNanos() {
    assertEquals(
      ofEpochSecond(1709208000L, 120_000_000L),
      FormDefXmlDeserializer.parseTimestampLiteral("2024-02-29T12:00:00.12Z"),
    )
  }

  @Test
  fun formDefLiteral_dateOnlyTimestamp_returnsNull() {
    assertNull(FormDefXmlDeserializer.parseTimestampLiteral("2024-02-29"))
  }

  @Test
  fun formDefLiteral_negativeYearTimestamp_returnsNull() {
    assertNull(FormDefXmlDeserializer.parseTimestampLiteral("-0001-12-31T00:00:00Z"))
  }

  @Test
  fun recordInstant_positiveOffset_convertsToUtc() {
    assertEquals(
      ofEpochSecond(1709200800L, 0L),
      RecordInstanceXmlSerializer.parseIsoInstant("2024-02-29T12:00:00+02:00"),
    )
  }

  @Test
  fun recordInstant_negativeOffset_convertsToUtc() {
    assertEquals(
      ofEpochSecond(1709220600L, 0L),
      RecordInstanceXmlSerializer.parseIsoInstant("2024-02-29T12:00:00-03:30"),
    )
  }

  @Test
  fun recordInstant_spaceSeparator_isAccepted() {
    assertEquals(
      ofEpochSecond(1709208000L, 0L),
      RecordInstanceXmlSerializer.parseIsoInstant("2024-02-29 12:00:00Z"),
    )
  }

  @Test
  fun recordInstant_dateOnly_isMidnightUtc() {
    assertEquals(
      ofEpochSecond(19782L * 86_400L, 0L),
      RecordInstanceXmlSerializer.parseIsoInstant("2024-02-29"),
    )
  }

  @Test
  fun recordInstant_invalidText_fallsBackToYearZeroSentinel() {
    // Quirk: unparseable fields default to 0, so garbage becomes 0000-00-00T00:00:00Z.
    assertEquals(
      ofEpochSecond(-719560L * 86_400L, 0L),
      RecordInstanceXmlSerializer.parseIsoInstant("garbage"),
    )
  }
}
