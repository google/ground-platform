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

/**
 * A proleptic Gregorian calendar date with no time zone, converted to and from days since
 * 1970-01-01 with Howard Hinnant's `days_from_civil` and `civil_from_days` algorithms.
 */
data class CivilDate(val year: Int, val month: Int, val day: Int) {

  /**
   * Days since 1970-01-01. Out-of-range months and days are not rejected: they roll over
   * arithmetically (month 13 is January of the next year, January 32 is February 1), which the
   * lenient XML parsers rely on.
   */
  fun toEpochDays(): Long {
    val y = if (month <= 2) year.toLong() - 1L else year.toLong()
    val era = y.floorDiv(YEARS_PER_ERA)
    val yearOfEra = y - era * YEARS_PER_ERA
    val m = month.toLong()
    val dayOfYear = (153L * (if (m > 2L) m - 3L else m + 9L) + 2L) / 5L + day.toLong() - 1L
    val dayOfEra = yearOfEra * 365L + yearOfEra / 4L - yearOfEra / 100L + dayOfYear
    return era * DAYS_PER_ERA + dayOfEra - EPOCH_SHIFT_DAYS
  }

  /** `YYYY-MM-DD`, zero-padding each field. A negative year's sign is padded like a digit. */
  fun toIsoString(): String =
    "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-" +
      day.toString().padStart(2, '0')

  companion object {
    /** The calendar date [epochDays] days after 1970-01-01. */
    fun fromEpochDays(epochDays: Long): CivilDate {
      val z = epochDays + EPOCH_SHIFT_DAYS
      val era = z.floorDiv(DAYS_PER_ERA)
      val dayOfEra = z - era * DAYS_PER_ERA
      val yearOfEra = (dayOfEra - dayOfEra / 1460L + dayOfEra / 36524L - dayOfEra / 146096L) / 365L
      val dayOfYear = dayOfEra - (365L * yearOfEra + yearOfEra / 4L - yearOfEra / 100L)
      val mp = (5L * dayOfYear + 2L) / 153L
      val day = dayOfYear - (153L * mp + 2L) / 5L + 1L
      val month = if (mp < 10L) mp + 3L else mp - 9L
      val year = yearOfEra + era * YEARS_PER_ERA + (if (month <= 2L) 1L else 0L)
      return CivilDate(year = year.toInt(), month = month.toInt(), day = day.toInt())
    }

    /** Years in one 400-year Gregorian cycle. */
    private const val YEARS_PER_ERA = 400L

    /** Days in one 400-year Gregorian cycle. */
    private const val DAYS_PER_ERA = 146097L

    /** Days from 0000-03-01, where the algorithm's eras start, to 1970-01-01. */
    private const val EPOCH_SHIFT_DAYS = 719468L
  }
}

internal fun Date.toCivilDate(): CivilDate = CivilDate(year = year, month = month, day = day)

internal fun CivilDate.toProtoDate(): Date = Date(year = year, month = month, day = day)
