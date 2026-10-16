/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.app.domain.model.editor

/** Formats epoch milliseconds as an ISO 8601 UTC timestamp (`2026-10-05T16:34:41Z`). */
fun isoUtc(epochMillis: Long): String {
  val totalSeconds = epochMillis.floorDiv(1000L)
  val days = totalSeconds.floorDiv(86_400L)
  val secondsOfDay = totalSeconds - days * 86_400L
  // Civil-from-days (H. Hinnant, public domain algorithm).
  val z = days + 719_468
  val era = z.floorDiv(146_097L)
  val doe = z - era * 146_097
  val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146_096) / 365
  val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
  val mp = (5 * doy + 2) / 153
  val day = doy - (153 * mp + 2) / 5 + 1
  val month = if (mp < 10) mp + 3 else mp - 9
  val year = yoe + era * 400 + if (month <= 2) 1 else 0
  fun two(v: Long) = v.toString().padStart(2, '0')
  val h = secondsOfDay / 3600
  val m = (secondsOfDay % 3600) / 60
  val s = secondsOfDay % 60
  return "$year-${two(month)}-${two(day)}T${two(h)}:${two(m)}:${two(s)}Z"
}

/**
 * Parses an ISO 8601 UTC timestamp (`2026-10-05T16:34:41Z`, optionally with fractional seconds, or
 * a bare `2026-10-05` date) into epoch milliseconds, or `null` if it isn't one.
 */
fun epochMillisOfIsoUtc(iso: String): Long? {
  val match = ISO_UTC.matchEntire(iso.trim()) ?: return null
  val (y, mo, d) = match.destructured
  val year = y.toLong()
  val month = mo.toLong()
  val day = d.toLong()
  if (month !in 1..12 || day !in 1..31) return null
  val hour = match.groupValues[4].toLongOrNull() ?: 0
  val minute = match.groupValues[5].toLongOrNull() ?: 0
  val second = match.groupValues[6].toLongOrNull() ?: 0
  // Days-from-civil (H. Hinnant, public domain algorithm); the inverse of `isoUtc`.
  val yy = if (month <= 2) year - 1 else year
  val era = yy.floorDiv(400L)
  val yoe = yy - era * 400
  val mp = if (month > 2) month - 3 else month + 9
  val doy = (153 * mp + 2) / 5 + day - 1
  val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
  val days = era * 146_097 + doe - 719_468
  return ((days * 24 + hour) * 60 + minute) * 60_000 + second * 1000
}

private val ISO_UTC =
  Regex("""(\d{4})-(\d{2})-(\d{2})(?:T(\d{2}):(\d{2})(?::(\d{2})(?:\.\d+)?)?Z)?""")
