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
package org.openforis.collectone.v2.web.domain.model

import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import org.openforis.collectone.v2.app.domain.model.SurveyOutcomeKind
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.editor.parseIsoUtc

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

  /** Whether [survey] should show the "Was this data used?" prompt at [now]. */
  fun isDue(survey: SurveyPreviewItem, now: Instant): Boolean {
    if (!survey.isClosed) return false
    val outcome = survey.outcome
    if (outcome != null && !outcome.isNotYet) return false
    val since =
      listOfNotNull(survey.closedAt, outcome?.answeredAt).mapNotNull(::parseIsoUtc).maxOrNull()
        ?: return false
    return now - since >= WAIT_DAYS.days
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
