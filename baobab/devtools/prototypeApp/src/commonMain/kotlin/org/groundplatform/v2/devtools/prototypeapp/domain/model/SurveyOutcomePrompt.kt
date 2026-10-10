/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.devtools.prototypeapp.domain.model

/**
 * When to ask again "What happened with this data?" for a closed survey (see
 * `docs/product/impact-measurement.md`, "Light-Touch Questions").
 *
 * A closed or archived survey whose outcome wasn't answered, or was answered "Not yet", is due
 * [WAIT_DAYS] days after it was closed. Answering "Not yet" again restarts the wait from the
 * answer, so organizers are asked at most once per [WAIT_DAYS] days.
 */
object SurveyOutcomePrompt {
  /** Days to wait before asking again. */
  const val WAIT_DAYS = 90

  private const val MILLIS_PER_DAY = 86_400_000L

  /** Whether [survey] should show the "Was this data used?" prompt at [nowMillis] (epoch ms). */
  fun isDue(survey: SurveyPreviewItem, nowMillis: Long): Boolean {
    if (!survey.isClosed) return false
    val outcome = survey.outcome
    if (outcome != null && !outcome.isNotYet) return false
    val since =
      listOfNotNull(survey.closedAt, outcome?.answeredAt)
        .mapNotNull(::epochMillisOfIsoUtc)
        .maxOrNull() ?: return false
    return nowMillis - since >= WAIT_DAYS * MILLIS_PER_DAY
  }
}

/** Selection rules of the "What happened with this data?" chips. */
object SurveyOutcomeSelection {
  /**
   * [selection] with [kind] turned on or off. "Not yet" can't be combined with other outcomes:
   * choosing it clears the others, and choosing another outcome clears "Not yet".
   */
  fun toggle(selection: Set<SurveyOutcomeKind>, kind: SurveyOutcomeKind): Set<SurveyOutcomeKind> =
    when {
      kind in selection -> selection - kind
      kind == SurveyOutcomeKind.NOT_YET -> setOf(SurveyOutcomeKind.NOT_YET)
      else -> selection - SurveyOutcomeKind.NOT_YET + kind
    }
}

/**
 * Parses an ISO 8601 UTC timestamp (`2026-10-05T16:34:41Z`, optionally with fractional seconds, or
 * a bare `2026-10-05` date) into epoch milliseconds, or `null` if it isn't one.
 */
internal fun epochMillisOfIsoUtc(iso: String): Long? {
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
