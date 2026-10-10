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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.isoUtc

class SurveyOutcomePromptTest {
  private val closedAt = "2026-01-10T12:00:00Z"
  private val closedAtMillis = checkNotNull(epochMillisOfIsoUtc(closedAt))

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
      coordinatesLabel = "",
      offlineSizeLabel = "",
      isDownloaded = false,
      thumbnailTheme = MapThumbnailTheme.SAVANNA,
      entityCount = 0,
      state = state,
      closedAt = closedAt,
      outcome = outcome,
    )

  private fun daysAfter(millis: Long, days: Int) = millis + days * 86_400_000L

  private fun answer(vararg outcomes: SurveyOutcomeKind, at: String = closedAt) =
    SurveyOutcome(outcomes = outcomes.toSet(), answeredAt = at, answeredBy = "a@example.org")

  @Test
  fun isDue_closedWithoutAnswer_after90DaysOnly() {
    assertFalse(SurveyOutcomePrompt.isDue(survey(), daysAfter(closedAtMillis, 89)))
    assertTrue(SurveyOutcomePrompt.isDue(survey(), daysAfter(closedAtMillis, 90)))
    assertTrue(SurveyOutcomePrompt.isDue(survey(), daysAfter(closedAtMillis, 400)))
  }

  @Test
  fun isDue_archivedCountsLikeClosed_publishedNever() {
    val later = daysAfter(closedAtMillis, 120)
    assertTrue(SurveyOutcomePrompt.isDue(survey(state = SurveyLifecycleState.ARCHIVED), later))
    assertFalse(SurveyOutcomePrompt.isDue(survey(state = SurveyLifecycleState.PUBLISHED), later))
  }

  @Test
  fun isDue_answeredWithAUse_isNeverDue() {
    val used = survey(outcome = answer(SurveyOutcomeKind.SHARED_WITH_BUYERS))
    assertFalse(SurveyOutcomePrompt.isDue(used, daysAfter(closedAtMillis, 365)))
  }

  @Test
  fun isDue_notYet_restartsTheWaitFromTheAnswer() {
    val answeredAt = isoUtc(daysAfter(closedAtMillis, 100))
    val answeredMillis = checkNotNull(epochMillisOfIsoUtc(answeredAt))
    val notYet = survey(outcome = answer(SurveyOutcomeKind.NOT_YET, at = answeredAt))
    assertFalse(SurveyOutcomePrompt.isDue(notYet, daysAfter(answeredMillis, 89)))
    assertTrue(SurveyOutcomePrompt.isDue(notYet, daysAfter(answeredMillis, 90)))
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
    assertTrue(SurveyOutcomePrompt.isDue(effortOnly, daysAfter(closedAtMillis, 90)))
  }

  @Test
  fun isDue_withoutAReadableClosedTime_isFalse() {
    val later = daysAfter(closedAtMillis, 365)
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

  @Test
  fun epochMillisOfIsoUtc_isTheInverseOfIsoUtc() {
    listOf(0L, 951_782_400_000L, 1_791_331_200_000L, 1_802_000_123_000L, -86_400_000L).forEach {
      assertEquals(it, epochMillisOfIsoUtc(isoUtc(it)))
    }
    assertEquals(epochMillisOfIsoUtc("2026-10-05T00:00:00Z"), epochMillisOfIsoUtc("2026-10-05"))
    assertEquals(
      epochMillisOfIsoUtc("2026-10-05T16:34:41Z"),
      epochMillisOfIsoUtc("2026-10-05T16:34:41.250Z"),
    )
    assertNull(epochMillisOfIsoUtc("2026-13-01T00:00:00Z"))
    assertNull(epochMillisOfIsoUtc("not a date"))
  }
}
