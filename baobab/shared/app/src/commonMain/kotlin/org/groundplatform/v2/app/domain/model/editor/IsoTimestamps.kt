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
