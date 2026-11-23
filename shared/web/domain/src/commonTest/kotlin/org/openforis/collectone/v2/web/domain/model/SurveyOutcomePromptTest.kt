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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import org.openforis.collectone.v2.app.domain.model.EffortComparison
import org.openforis.collectone.v2.app.domain.model.MapThumbnailTheme
import org.openforis.collectone.v2.app.domain.model.SurveyLifecycleState
import org.openforis.collectone.v2.app.domain.model.SurveyOutcome
import org.openforis.collectone.v2.app.domain.model.SurveyOutcomeKind
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.editor.isoUtc
import org.openforis.collectone.v2.app.domain.model.editor.parseIsoUtc

class SurveyOutcomePromptTest {
  private val closedAt = "2026-01-10T12:00:00Z"
  private val closedAtInstant = checkNotNull(parseIsoUtc(closedAt))

  private fun survey(
    state: SurveyLifecycleState = SurveyLifecycleState.CLOSED,
    closedAt: String? = this.closedAt,
    outcome: SurveyOutcome? = null,
  ) =
    SurveyPreviewItem(
      id = "s",
      title = "Survey",
      description = "",
      location = "",
      isDownloaded = false,
      thumbnailTheme = MapThumbnailTheme.SAVANNA,
      entityCount = 0,
      state = state,
      closedAt = closedAt,
      outcome = outcome,
    )

  private fun daysAfter(instant: Instant, days: Int) = instant + days.days

  private fun answer(vararg outcomes: SurveyOutcomeKind, at: String = closedAt) =
    SurveyOutcome(outcomes = outcomes.toSet(), answeredAt = at, answeredBy = "a@example.org")

  @Test
  fun isDue_closedWithoutAnswer_after90DaysOnly() {
    assertFalse(SurveyOutcomePrompt.isDue(survey(), daysAfter(closedAtInstant, 89)))
    assertTrue(SurveyOutcomePrompt.isDue(survey(), daysAfter(closedAtInstant, 90)))
    assertTrue(SurveyOutcomePrompt.isDue(survey(), daysAfter(closedAtInstant, 400)))
  }

  @Test
  fun isDue_archivedCountsLikeClosed_publishedNever() {
    val later = daysAfter(closedAtInstant, 120)
    assertTrue(SurveyOutcomePrompt.isDue(survey(state = SurveyLifecycleState.ARCHIVED), later))
    assertFalse(SurveyOutcomePrompt.isDue(survey(state = SurveyLifecycleState.PUBLISHED), later))
  }

  @Test
  fun isDue_answeredWithAUse_isNeverDue() {
    val used = survey(outcome = answer(SurveyOutcomeKind.SHARED_WITH_BUYERS))
    assertFalse(SurveyOutcomePrompt.isDue(used, daysAfter(closedAtInstant, 365)))
  }

  @Test
  fun isDue_notYet_restartsTheWaitFromTheAnswer() {
    val answeredAt = isoUtc(daysAfter(closedAtInstant, 100))
    val answeredInstant = checkNotNull(parseIsoUtc(answeredAt))
    val notYet = survey(outcome = answer(SurveyOutcomeKind.NOT_YET, at = answeredAt))
    assertFalse(SurveyOutcomePrompt.isDue(notYet, daysAfter(answeredInstant, 89)))
    assertTrue(SurveyOutcomePrompt.isDue(notYet, daysAfter(answeredInstant, 90)))
  }

  @Test
  fun isDue_answerWithoutOutcomes_countsAsNotYet() {
    val effortOnly =
      survey(
        outcome =
          SurveyOutcome(
            effortComparison = EffortComparison.LESS,
            answeredAt = closedAt,
            answeredBy = "a@example.org",
          )
      )
    assertTrue(effortOnly.outcome!!.isNotYet)
    assertTrue(SurveyOutcomePrompt.isDue(effortOnly, daysAfter(closedAtInstant, 90)))
  }

  @Test
  fun isDue_withoutAReadableClosedTime_isFalse() {
    val later = daysAfter(closedAtInstant, 365)
    assertFalse(SurveyOutcomePrompt.isDue(survey(closedAt = null), later))
    assertFalse(SurveyOutcomePrompt.isDue(survey(closedAt = "last spring"), later))
  }

  @Test
  fun toggle_notYetIsExclusive() {
    val shared = SurveyOutcomeSelection.toggle(emptySet(), SurveyOutcomeKind.SHARED_WITH_BUYERS)
    val both = SurveyOutcomeSelection.toggle(shared, SurveyOutcomeKind.LAND_TITLING)
    assertEquals(
      setOf(SurveyOutcomeKind.SHARED_WITH_BUYERS, SurveyOutcomeKind.LAND_TITLING),
      both,
    )

    val notYet = SurveyOutcomeSelection.toggle(both, SurveyOutcomeKind.NOT_YET)
    assertEquals(setOf(SurveyOutcomeKind.NOT_YET), notYet)

    val used = SurveyOutcomeSelection.toggle(notYet, SurveyOutcomeKind.REPORTED_FERM)
    assertEquals(setOf(SurveyOutcomeKind.REPORTED_FERM), used)

    assertEquals(emptySet(), SurveyOutcomeSelection.toggle(used, SurveyOutcomeKind.REPORTED_FERM))
  }
}
