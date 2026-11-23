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
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.char
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** [instant] as an ISO 8601 UTC timestamp to the second (`2026-10-05T16:34:41Z`). */
fun isoUtc(instant: Instant): String = ISO_UTC_SECONDS.format(instant.toLocalDateTime(TimeZone.UTC))

/**
 * Parses an ISO 8601 UTC timestamp (`2026-10-05T16:34:41Z`, optionally without seconds or with
 * fractional seconds, which are dropped, or a bare `2026-10-05` date at UTC midnight), or returns
 * `null` if it isn't a real UTC date and time.
 */
fun parseIsoUtc(iso: String): Instant? {
  val match = ISO_UTC.matchEntire(iso.trim()) ?: return null
  // Groups: year, month, day, then the optional hour, minute and second.
  val fields = match.groupValues.drop(1).map { it.toIntOrNull() ?: 0 }
  return try {
    LocalDateTime(fields[0], fields[1], fields[2], fields[3], fields[4], fields[5])
      .toInstant(TimeZone.UTC)
  } catch (_: IllegalArgumentException) {
    null
  }
}

private val ISO_UTC_SECONDS = LocalDateTime.Format {
  date(LocalDate.Formats.ISO)
  char('T')
  hour()
  char(':')
  minute()
  char(':')
  second()
  char('Z')
}

private val ISO_UTC =
  Regex("""(\d{4})-(\d{2})-(\d{2})(?:T(\d{2}):(\d{2})(?::(\d{2})(?:\.\d+)?)?Z)?""")
