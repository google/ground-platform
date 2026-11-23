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

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime

/**
 * Conversions between ISO `YYYY-MM-DD` dates and the UTC-midnight milliseconds used by the M3
 * `DatePicker`. Dates are calendar dates in UTC, so no local time zone is involved and there is no
 * off-by-one around midnight.
 */
private val ISO_DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

/**
 * "Mar 5, 2026": English month abbreviation, unpadded day, year. Only for text exported in English
 * (the default XForms constraint message); the UI formats dates in the user's language.
 */
private val FRIENDLY_DATE = LocalDate.Format {
  monthName(MonthNames.ENGLISH_ABBREVIATED)
  char(' ')
  day(padding = Padding.NONE)
  chars(", ")
  year(padding = Padding.NONE)
}

/** UTC midnight of [iso] in milliseconds, or `null` if [iso] isn't a real `YYYY-MM-DD` date. */
fun isoDateToUtcMillis(iso: String): Long? =
  isoDateOrNull(iso)?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds()

/** ISO `YYYY-MM-DD` of the UTC calendar day containing [utcMillis]. */
fun utcMillisToIsoDate(utcMillis: Long): String =
  Instant.fromEpochMilliseconds(utcMillis).toLocalDateTime(TimeZone.UTC).date.toString()

/**
 * English form of an ISO date, e.g. "Mar 5, 2026"; returns [iso] unchanged if invalid. For exported
 * English text only; the UI uses the localized date text of `ui/text`.
 */
fun friendlyDate(iso: String): String = isoDateOrNull(iso)?.let(FRIENDLY_DATE::format) ?: iso

/** [iso] as a [LocalDate] if it is a real date written `YYYY-MM-DD` (four-digit year). */
fun isoDateOrNull(iso: String): LocalDate? {
  if (!ISO_DATE.matches(iso)) return null
  return try {
    LocalDate.parse(iso)
  } catch (_: IllegalArgumentException) {
    null
  }
}
