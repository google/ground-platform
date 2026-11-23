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

import com.google.type.TimeOfDay
import com.squareup.wire.Instant

private const val SECONDS_PER_DAY = 86_400L
private const val SECONDS_PER_HOUR = 3_600L
private const val SECONDS_PER_MINUTE = 60L
private const val NANOS_PER_MILLI = 1_000_000

/** `HH:MM:SS`, plus `.mmm` (truncated to milliseconds) when [time] has a fractional second. */
fun formatIsoTime(time: TimeOfDay): String {
  val base = "${pad2(time.hours)}:${pad2(time.minutes)}:${pad2(time.seconds)}"
  return if (time.nanos > 0) "$base.${millisOf(time.nanos)}" else base
}

/**
 * UTC `YYYY-MM-DDTHH:MM:SSZ`, plus `.mmm` (truncated to milliseconds) when [instant] has a
 * fractional second. Use it rather than `Instant.toString()`, which is ISO 8601 only on the JVM.
 */
fun formatIsoInstant(instant: Instant): String {
  val nanos = instant.getNano()
  val fraction = if (nanos > 0) ".${millisOf(nanos)}" else ""
  return "${formatIsoDateTimeWithoutZone(instant)}${fraction}Z"
}

/** UTC `YYYY-MM-DDTHH:MM:SS.mmmZ`, always writing milliseconds (`.000` for whole seconds). */
internal fun formatIsoInstantWithMillis(instant: Instant): String =
  "${formatIsoDateTimeWithoutZone(instant)}.${millisOf(instant.getNano())}Z"

/**
 * Seconds in an unsigned ISO-8601 UTC offset (`HH:MM`, `HH`); the caller applies the sign. Missing
 * or unparseable fields count as zero.
 */
internal fun parseIsoOffsetSeconds(offset: String): Long {
  val parts = offset.split(':')
  val hours = parts.getOrNull(0)?.toLongOrNull() ?: 0L
  val minutes = parts.getOrNull(1)?.toLongOrNull() ?: 0L
  return hours * SECONDS_PER_HOUR + minutes * SECONDS_PER_MINUTE
}

private fun formatIsoDateTimeWithoutZone(instant: Instant): String {
  val epochSecond = instant.getEpochSecond()
  val date = CivilDate.fromEpochDays(epochSecond.floorDiv(SECONDS_PER_DAY))
  val secondOfDay = epochSecond.mod(SECONDS_PER_DAY)
  val hours = (secondOfDay / SECONDS_PER_HOUR).toInt()
  val minutes = ((secondOfDay % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE).toInt()
  val seconds = (secondOfDay % SECONDS_PER_MINUTE).toInt()
  return "${date.toIsoString()}T${pad2(hours)}:${pad2(minutes)}:${pad2(seconds)}"
}

private fun millisOf(nanos: Int): String = (nanos / NANOS_PER_MILLI).toString().padStart(3, '0')

private fun pad2(value: Int): String = value.toString().padStart(2, '0')
