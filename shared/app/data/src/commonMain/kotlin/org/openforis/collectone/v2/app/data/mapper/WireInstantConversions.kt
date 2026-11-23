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
package org.openforis.collectone.v2.app.data.mapper

import com.squareup.wire.Instant
import com.squareup.wire.ofEpochSecond
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.openforis.collectone.v2.app.domain.model.editor.isoUtc
import org.openforis.collectone.v2.app.domain.model.editor.parseIsoUtc

/**
 * Wire [Instant] for an ISO 8601 UTC date (`yyyy-MM-dd`) or date-time; null if blank or invalid.
 */
internal fun parseIsoToInstant(iso: String): Instant? = parseIsoUtc(iso)?.toWireInstant()

/** ISO 8601 UTC text for [instant]: just the date at midnight, otherwise the date-time. */
internal fun formatInstantDateOrIso(instant: Instant?): String {
  if (instant == null) return ""
  val utc = instant.toKotlinInstant()
  val dateTime = utc.toLocalDateTime(TimeZone.UTC)
  val isMidnight = dateTime.hour == 0 && dateTime.minute == 0 && dateTime.second == 0
  return if (isMidnight) dateTime.date.toString() else isoUtc(utc)
}

/** This Wire (protobuf `Timestamp`) instant as a [kotlin.time.Instant]. */
internal fun Instant.toKotlinInstant(): kotlin.time.Instant =
  kotlin.time.Instant.fromEpochSeconds(getEpochSecond(), getNano())

/** This instant as a Wire (protobuf `Timestamp`) [Instant]. */
internal fun kotlin.time.Instant.toWireInstant(): Instant =
  ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong())
