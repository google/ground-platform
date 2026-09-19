/**
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License'); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.groundplatform.v2.core.forms.xpath.model

import com.google.type.Date
import com.google.type.TimeOfDay
import com.squareup.wire.Instant
import com.squareup.wire.ofEpochSecond
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Pure Kotlin Multiplatform civil calendar and ISO-8601 date/time utilities for XPath temporal
 * arithmetic and ODK date formatting (`format-date`, `format-date-time`, `decimal-date-time`,
 * `decimal-time`, `today`, `now`).
 */
internal object TemporalUtils {

  private val SHORT_MONTH_NAMES =
    arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

  // 1970-01-01 was a Thursday (day of week index 4 if Sun=0..Sat=6)
  private val SHORT_WEEKDAY_NAMES = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

  /**
   * Converts a proleptic Gregorian calendar date to epoch days since 1970-01-01 (Howard Hinnant
   * algorithm).
   */
  fun dateToEpochDays(year: Int, month: Int, day: Int): Long {
    val y = if (month <= 2) year.toLong() - 1L else year.toLong()
    val era = if (y >= 0) y / 400L else (y - 399L) / 400L
    val yoe = y - era * 400L
    val m = month.toLong()
    val doy = (153L * (if (m > 2) m - 3L else m + 9L) + 2L) / 5L + day.toLong() - 1L
    val doe = yoe * 365L + yoe / 4L - yoe / 100L + doy
    return era * 146097L + doe - 719468L
  }

  /** Converts epoch days since 1970-01-01 to a [Date] message. */
  fun epochDaysToDate(epochDays: Long): Date {
    val z = epochDays + 719468L
    val era = if (z >= 0) z / 146097L else (z - 146096L) / 146097L
    val doe = z - era * 146097L
    val yoe = (doe - doe / 1460L + doe / 36524L - doe / 146096L) / 365L
    val y = yoe + era * 400L
    val doy = doe - (365L * yoe + yoe / 4L - yoe / 100L)
    val mp = (5L * doy + 2L) / 153L
    val d = doy - (153L * mp + 2L) / 5L + 1L
    val m = if (mp < 10L) mp + 3L else mp - 9L
    val year = if (m <= 2L) y + 1L else y
    return Date(year = year.toInt(), month = m.toInt(), day = d.toInt())
  }

  /** Returns the day of week name (`Sun`..`Sat`) for epoch days since 1970-01-01. */
  fun dayOfWeekShortName(epochDays: Long): String {
    // 1970-01-01 (day 0) is Thursday (index 4)
    val idx = ((epochDays + 4L) % 7L + 7L).toInt() % 7
    return SHORT_WEEKDAY_NAMES[idx]
  }

  fun dateToEpochDays(date: Date): Double =
    dateToEpochDays(date.year, date.month, date.day).toDouble()

  fun timestampToEpochDays(instant: Instant): Double =
    (instant.getEpochSecond().toDouble() + instant.getNano().toDouble() / 1_000_000_000.0) / 86400.0

  fun timeOfDayToDecimal(time: TimeOfDay): Double =
    (time.hours * 3600.0 +
      time.minutes * 60.0 +
      time.seconds.toDouble() +
      time.nanos / 1_000_000_000.0) / 86400.0

  fun formatDate(date: Date): String =
    "${date.year.toString().padStart(4, '0')}-${date.month.toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}"

  fun formatTime(time: TimeOfDay): String {
    val base =
      "${time.hours.toString().padStart(2, '0')}:${time.minutes.toString().padStart(2, '0')}:${time.seconds.toString().padStart(2, '0')}"
    return if (time.nanos > 0) {
      val millis = (time.nanos / 1_000_000).toString().padStart(3, '0')
      "$base.$millis"
    } else {
      base
    }
  }

  fun formatTimestamp(instant: Instant): String {
    val epochSec = instant.getEpochSecond()
    val nano = instant.getNano()
    val days = floor(epochSec.toDouble() / 86400.0).toLong()
    val remSec = ((epochSec % 86400L) + 86400L) % 86400L
    val date = epochDaysToDate(days)
    val hours = (remSec / 3600L).toInt()
    val minutes = ((remSec % 3600L) / 60L).toInt()
    val seconds = (remSec % 60L).toInt()
    val timeStr =
      "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    val nanosStr =
      if (nano > 0) {
        "." + (nano / 1_000_000).toString().padStart(3, '0')
      } else {
        ""
      }
    return "${formatDate(date)}T$timeStr${nanosStr}Z"
  }

  /** Parses an ISO-8601 date (`YYYY-MM-DD`) or date-time string into a [Date] if valid, or null. */
  fun tryParseDate(str: String): Date? {
    val trimmed = str.trim()
    if (trimmed.length < 10) return null
    val datePart = trimmed.substring(0, 10)
    val parts = datePart.split('-')
    if (parts.size != 3) return null
    val y = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    val d = parts[2].toIntOrNull() ?: return null
    if (m !in 1..12 || d !in 1..31) return null
    return Date(year = y, month = m, day = d)
  }

  /**
   * Parses an ISO-8601 time (`HH:MM` or `HH:MM:SS` or `HH:MM:SS.sss`) into a [TimeOfDay] if valid.
   */
  fun tryParseTime(str: String): TimeOfDay? {
    val trimmed = str.trim()
    val timeOnly =
      if (trimmed.contains('T'))
        trimmed.substringAfter('T').substringBefore('Z').substringBefore('+')
      else trimmed.substringBefore('Z').substringBefore('+')
    val colonParts = timeOnly.split(':')
    if (colonParts.size < 2) return null
    val h = colonParts[0].toIntOrNull() ?: return null
    val m = colonParts[1].toIntOrNull() ?: return null
    var s = 0
    var nanos = 0
    if (colonParts.size >= 3) {
      val secParts = colonParts[2].split('.')
      s = secParts[0].toIntOrNull() ?: return null
      if (secParts.size > 1) {
        val frac = secParts[1].take(9).padEnd(9, '0')
        nanos = frac.toIntOrNull() ?: 0
      }
    }
    if (h !in 0..23 || m !in 0..59 || s !in 0..59) return null
    return TimeOfDay(hours = h, minutes = m, seconds = s, nanos = nanos)
  }

  /**
   * Parses an ISO-8601 timestamp (`YYYY-MM-DDTHH:MM:SS[.sss][Z|+/-HH:MM]`) or date into epoch days.
   */
  fun tryParseToEpochDays(str: String): Double? {
    val trimmed = str.trim()
    val date = tryParseDate(trimmed) ?: return null
    val baseDays = dateToEpochDays(date)
    if (!trimmed.contains('T')) {
      return baseDays
    }
    val afterT = trimmed.substringAfter('T')
    var offsetSeconds = 0L
    val timePart: String
    if (afterT.endsWith('Z') || afterT.endsWith('z')) {
      timePart = afterT.dropLast(1)
    } else if (afterT.contains('+')) {
      timePart = afterT.substringBefore('+')
      offsetSeconds = parseTzOffsetSeconds(afterT.substringAfter('+'))
    } else if (afterT.lastIndexOf('-') > 0) {
      val idx = afterT.lastIndexOf('-')
      timePart = afterT.substring(0, idx)
      offsetSeconds = -parseTzOffsetSeconds(afterT.substring(idx + 1))
    } else {
      timePart = afterT
    }
    val time = tryParseTime(timePart) ?: return baseDays
    val timeSec =
      time.hours * 3600.0 +
        time.minutes * 60.0 +
        time.seconds.toDouble() +
        time.nanos / 1_000_000_000.0
    val utcSec = timeSec - offsetSeconds.toDouble()
    return baseDays + utcSec / 86400.0
  }

  private fun parseTzOffsetSeconds(tz: String): Long {
    val parts = tz.split(':')
    val h = parts.getOrNull(0)?.toLongOrNull() ?: 0L
    val m = parts.getOrNull(1)?.toLongOrNull() ?: 0L
    return h * 3600L + m * 60L
  }

  /**
   * Formats a date or date-time using ODK XForms `format-date` / `format-date-time` pattern tokens:
   * - `%Y`: 4-digit year
   * - `%y`: 2-digit year
   * - `%m`: 0-padded month (01-12)
   * - `%n`: numeric month (1-12)
   * - `%b`: short month name (Jan-Dec)
   * - `%d`: 0-padded day (01-31)
   * - `%e`: numeric day (1-31)
   * - `%a`: short weekday name (Sun-Sat)
   * - `%H`: 0-padded 24-hour (00-23)
   * - `%h`: numeric 24-hour (0-23)
   * - `%M`: 0-padded minute (00-59)
   * - `%S`: 0-padded second (00-59)
   * - `%3`: 0-padded milliseconds (000-999)
   */
  fun formatPattern(epochDays: Double, pattern: String): String {
    if (epochDays.isNaN()) return ""
    val wholeDays = floor(epochDays).toLong()
    val fracDay = epochDays - wholeDays.toDouble()
    val totalMillisInDay = (fracDay * 86400000.0).roundToLong().coerceIn(0L, 86399999L)
    val totalSecInDay = totalMillisInDay / 1000L
    val millis = (totalMillisInDay % 1000L).toInt()
    val hours = (totalSecInDay / 3600L).toInt()
    val minutes = ((totalSecInDay % 3600L) / 60L).toInt()
    val seconds = (totalSecInDay % 60L).toInt()

    val date = epochDaysToDate(wholeDays)
    val sb = StringBuilder()
    var i = 0
    while (i < pattern.length) {
      val c = pattern[i]
      if (c == '%' && i + 1 < pattern.length) {
        when (val token = pattern[i + 1]) {
          'Y' -> sb.append(date.year.toString().padStart(4, '0'))
          'y' -> sb.append((date.year % 100).toString().padStart(2, '0'))
          'm' -> sb.append(date.month.toString().padStart(2, '0'))
          'n' -> sb.append(date.month.toString())
          'b' -> sb.append(SHORT_MONTH_NAMES.getOrElse(date.month - 1) { "" })
          'd' -> sb.append(date.day.toString().padStart(2, '0'))
          'e' -> sb.append(date.day.toString())
          'a' -> sb.append(dayOfWeekShortName(wholeDays))
          'H' -> sb.append(hours.toString().padStart(2, '0'))
          'h' -> sb.append(hours.toString())
          'M' -> sb.append(minutes.toString().padStart(2, '0'))
          'S' -> sb.append(seconds.toString().padStart(2, '0'))
          '3' -> sb.append(millis.toString().padStart(3, '0'))
          '%' -> sb.append('%')
          else -> {
            sb.append('%')
            sb.append(token)
          }
        }
        i += 2
      } else {
        sb.append(c)
        i++
      }
    }
    return sb.toString()
  }

  fun epochDaysToInstant(epochDays: Double): Instant {
    val totalSeconds = floor(epochDays * 86400.0).toLong()
    val nanos = ((epochDays * 86400.0 - totalSeconds.toDouble()) * 1_000_000_000.0).roundToLong()
    return ofEpochSecond(totalSeconds, nanos)
  }
}
