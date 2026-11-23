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
package org.openforis.collectone.v2.core.forms.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.DatePicker
import androidx.compose.runtime.getValue
import openforis.collectone.v1.forms.DataType
import org.openforis.collectone.v2.core.forms.ui.resources.Res
import org.openforis.collectone.v2.core.forms.ui.resources.forms_date_invalid
import org.openforis.collectone.v2.core.forms.ui.resources.forms_integer_invalid
import org.openforis.collectone.v2.core.forms.ui.resources.forms_integer_out_of_range

/** Range of values representable by a proto `int32` field. */
private val INT32_RANGE = Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()

/** Outcome of interpreting the raw text a user typed into an integer control. */
internal sealed interface IntegerInput {
  /** The control is empty, so the underlying field should be cleared. */
  data object Empty : IntegerInput

  /** The text is not a usable integer; [message] explains why. */
  data class Invalid(val message: FormUiText) : IntegerInput

  /** The text parsed to [value], which is in range for the target data type. */
  data class Valid(val value: Long) : IntegerInput
}

/**
 * Parses [raw] as an integer appropriate for [dataType].
 *
 * Values outside the `int32` range are reported as [IntegerInput.Invalid] rather than being
 * narrowed with `toInt()`, which would silently wrap (for example `3000000000` would be stored as
 * `-1294967296`). Only `TYPE_INT64` fields accept the full `Long` range.
 */
internal fun parseIntegerInput(raw: String, dataType: DataType): IntegerInput {
  if (raw.isBlank()) return IntegerInput.Empty
  val parsed =
    raw.trim().toLongOrNull()
      ?: return IntegerInput.Invalid(FormUiText.Resource(Res.string.forms_integer_invalid))
  if (dataType != DataType.TYPE_INT64 && parsed !in INT32_RANGE) {
    return IntegerInput.Invalid(
      FormUiText.Resource(Res.string.forms_integer_out_of_range, Int.MIN_VALUE, Int.MAX_VALUE)
    )
  }
  return IntegerInput.Valid(parsed)
}

private const val MILLIS_PER_DAY = 86_400_000L

/** Outcome of interpreting the raw text a user typed into a date control. */
internal sealed interface DateInput {
  /** The control is empty, so the underlying field should be cleared. */
  data object Empty : DateInput

  /** The text is not a valid calendar date in `YYYY-MM-DD` format; [message] explains why. */
  data class Invalid(val message: FormUiText) : DateInput

  /** The text parsed to a valid proleptic Gregorian calendar date. */
  data class Valid(val year: Int, val month: Int, val day: Int) : DateInput
}

/** Formats [year], [month], and [day] as an ISO-8601 `YYYY-MM-DD` string. */
internal fun formatIsoDate(year: Int, month: Int, day: Int): String =
  "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

/**
 * Returns the number of days in [month] (`1..12`) of [year] in the proleptic Gregorian calendar.
 */
internal fun daysInMonth(year: Int, month: Int): Int =
  when (month) {
    1,
    3,
    5,
    7,
    8,
    10,
    12 -> 31
    4,
    6,
    9,
    11 -> 30
    2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
    else -> 0
  }

/**
 * Parses [raw] as an ISO-8601 calendar date (`YYYY-MM-DD`).
 *
 * Rejects malformed strings, out-of-range months, and non-existent calendar days (such as
 * `2026-02-30`).
 */
internal fun parseDateInput(raw: String): DateInput {
  if (raw.isBlank()) return DateInput.Empty
  val trimmed = raw.trim()
  val parts = trimmed.split('-')
  if (parts.size != 3 || parts[0].length != 4 || parts[1].length != 2 || parts[2].length != 2) {
    return DateInput.Invalid(FormUiText.Resource(Res.string.forms_date_invalid))
  }
  val year = parts[0].toIntOrNull()
  val month = parts[1].toIntOrNull()
  val day = parts[2].toIntOrNull()
  if (
    year == null ||
      month == null ||
      day == null ||
      year !in 1..9999 ||
      month !in 1..12 ||
      day !in 1..daysInMonth(year, month)
  ) {
    return DateInput.Invalid(FormUiText.Resource(Res.string.forms_date_invalid))
  }
  return DateInput.Valid(year, month, day)
}

/**
 * Converts a proleptic Gregorian date ([year], [month], [day]) to UTC midnight epoch milliseconds
 * for Material 3 [DatePicker].
 */
internal fun civilDateToUtcMillis(year: Int, month: Int, day: Int): Long {
  val y = (if (month <= 2) year - 1 else year).toLong()
  val era = if (y >= 0) y / 400 else (y - 399) / 400
  val yoe = y - era * 400
  val mp = (month + if (month > 2) -3 else 9).toLong()
  val doy = (153 * mp + 2) / 5 + day - 1
  val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
  val epochDays = era * 146097 + doe - 719468
  return epochDays * MILLIS_PER_DAY
}

/**
 * Converts UTC epoch milliseconds from Material 3 [DatePicker] to a proleptic Gregorian
 * [DateInput.Valid] calendar date.
 */
internal fun utcMillisToCivilDate(utcMillis: Long): DateInput.Valid {
  val epochDays = utcMillis.floorDiv(MILLIS_PER_DAY)
  val z = epochDays + 719468L
  val era = (if (z >= 0) z else z - 146096L) / 146097L
  val doe = z - era * 146097L
  val yoe = (doe - doe / 1460L + doe / 36524L - doe / 146096L) / 365L
  val y = yoe + era * 400L
  val doy = doe - (365L * yoe + yoe / 4L - yoe / 100L)
  val mp = (5L * doy + 2L) / 153L
  val d = (doy - (153L * mp + 2L) / 5L + 1L).toInt()
  val m = (mp + (if (mp < 10L) 3L else -9L)).toInt()
  val civilYear = (y + (if (m <= 2) 1L else 0L)).toInt()
  return DateInput.Valid(civilYear, m, d)
}

internal const val MIN_PICKER_YEAR = 1900
internal const val MAX_PICKER_YEAR = 2100

private val MONTH_NAMES =
  listOf(
    "January",
    "February",
    "March",
    "April",
    "May",
    "June",
    "July",
    "August",
    "September",
    "October",
    "November",
    "December",
  )

private val SHORT_MONTH_NAMES =
  listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

private val DAY_OF_WEEK_NAMES =
  listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

internal val WEEKDAY_HEADERS = listOf("S", "M", "T", "W", "T", "F", "S")

/**
 * Returns the Sunday-based day of the week (`0` = Sunday .. `6` = Saturday) for the proleptic
 * Gregorian date ([year], [month], [day]).
 */
internal fun sundayDayOfWeek(year: Int, month: Int, day: Int): Int {
  val epochDays = civilDateToUtcMillis(year, month, day).floorDiv(MILLIS_PER_DAY)
  return (epochDays + 4L).mod(7L).toInt()
}

/**
 * Builds the Sunday-first calendar grid rows (`4..6` rows of `7` cells) for [month] (`1..12`) of
 * [year], with `null` in leading and trailing cells outside the month.
 */
internal fun monthWeeks(year: Int, month: Int): List<List<Int?>> {
  val leadingEmpty = sundayDayOfWeek(year, month, 1)
  val totalDays = daysInMonth(year, month)
  val totalCells = ((leadingEmpty + totalDays + 6) / 7) * 7
  return (0 until totalCells)
    .map { index ->
      val day = index - leadingEmpty + 1
      if (day in 1..totalDays) day else null
    }
    .chunked(7)
}

/**
 * Shifts ([year], [month]) by [deltaMonths] and clamps the resulting year to
 * [MIN_PICKER_YEAR]..[MAX_PICKER_YEAR].
 */
internal fun shiftYearMonth(year: Int, month: Int, deltaMonths: Int): Pair<Int, Int> {
  val totalMonths =
    (year * 12 + (month - 1) + deltaMonths).coerceIn(
      MIN_PICKER_YEAR * 12,
      MAX_PICKER_YEAR * 12 + 11,
    )
  return (totalMonths / 12) to (totalMonths % 12 + 1)
}

internal fun formatMonthYear(year: Int, month: Int): String =
  "${MONTH_NAMES[(month - 1).coerceIn(0, 11)]} $year"

internal fun formatShortMonthDayYear(year: Int, month: Int, day: Int): String =
  "${SHORT_MONTH_NAMES[(month - 1).coerceIn(0, 11)]} $day, $year"

internal fun formatFullDateDescription(year: Int, month: Int, day: Int): String {
  val dow = DAY_OF_WEEK_NAMES[sundayDayOfWeek(year, month, day)]
  val monthName = MONTH_NAMES[(month - 1).coerceIn(0, 11)]
  return "$dow, $monthName $day, $year"
}
