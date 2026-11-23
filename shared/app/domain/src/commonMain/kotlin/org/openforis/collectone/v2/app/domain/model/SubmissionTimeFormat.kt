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
package org.openforis.collectone.v2.app.domain.model

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime

private val SUBMISSION_TIME = LocalDateTime.Format {
  date(LocalDate.Formats.ISO)
  char(' ')
  hour()
  char(':')
  minute()
}

private val MUTATION_TIME = LocalDateTime.Format {
  date(LocalDate.Formats.ISO)
  char(' ')
  hour()
  char(':')
  minute()
  char(':')
  second()
}

/**
 * Display text of a submission time, e.g. `"2026-09-24 08:15 UTC"`: the date and time to the minute
 * in UTC, numeric and locale-neutral like coordinates.
 */
fun formatSubmissionTime(instant: Instant): String =
  SUBMISSION_TIME.format(instant.toLocalDateTime(TimeZone.UTC)) + " UTC"

/**
 * Display and sort text of a mutation log time, e.g. `"2026-09-24 08:15:42 UTC"`: the UTC date and
 * time to the second so lexicographical order matches chronological order.
 */
fun formatMutationTime(instant: Instant): String =
  MUTATION_TIME.format(instant.toLocalDateTime(TimeZone.UTC)) + " UTC"

/**
 * Display text of when this submission was last updated ([SubmissionPreviewItem.updatedAt]), or
 * `""` for submissions without a recorded time.
 */
val SubmissionPreviewItem.updatedAtLabel: String
  get() = updatedAt?.let(::formatSubmissionTime).orEmpty()
