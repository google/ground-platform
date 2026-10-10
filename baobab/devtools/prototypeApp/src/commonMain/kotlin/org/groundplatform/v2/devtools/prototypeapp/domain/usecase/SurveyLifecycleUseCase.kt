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
package org.groundplatform.v2.devtools.prototypeapp.domain.usecase

import kotlinx.coroutines.flow.first
import org.groundplatform.v2.devtools.prototypeapp.domain.model.EffortComparison
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactCoverage
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactEventType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyLifecycleState
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyOutcome
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyOutcomeKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.isoUtc
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.AuthRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyRepository

/**
 * Closes, archives, and reopens surveys, and stores the organizer's answer to "What happened with
 * this data?" (see `docs/product/impact-measurement.md`, "Light-Touch Questions").
 *
 * Leaving [SurveyLifecycleState.PUBLISHED] records a [ImpactEventType.SURVEY_CLOSED] event covering
 * the survey's map features. Closing never waits for the outcome question; callers ask it
 * afterwards and it can always be skipped.
 */
class SurveyLifecycleUseCase(
  private val surveyRepository: SurveyRepository,
  private val authRepository: AuthRepository,
  /** Records the survey closed event; `null` skips it (e.g. in tests that don't need it). */
  private val recordImpactEvent: RecordImpactEventUseCase?,
  /** Current time in epoch milliseconds. */
  private val now: () -> Long,
) {
  /**
   * Moves survey [surveyId] to [state]. Returns `true` if it just stopped collecting data
   * (published → closed or archived), so the caller should offer the outcome question.
   *
   * Closing sets the survey's closed time; archiving a closed survey keeps it; reopening clears it.
   * The previous outcome answer is kept either way.
   */
  suspend fun setState(surveyId: String, state: SurveyLifecycleState): Boolean {
    val survey = surveyRepository.getSurveys().firstOrNull { it.id == surveyId } ?: return false
    if (survey.state == state) return false
    val stopsCollecting = survey.state == SurveyLifecycleState.PUBLISHED
    val closedAt =
      when {
        state == SurveyLifecycleState.PUBLISHED -> null
        stopsCollecting -> isoUtc(now())
        else -> survey.closedAt ?: isoUtc(now())
      }
    surveyRepository.setSurveyState(surveyId, state, closedAt)
    if (stopsCollecting) {
      val entities = surveyRepository.observeSurveyContent(surveyId).first().entities
      recordImpactEvent?.invoke(
        type = ImpactEventType.SURVEY_CLOSED,
        surveyId = surveyId,
        coverage = ImpactCoverage.of(entities),
      )
    }
    return stopsCollecting
  }

  /**
   * Stores [outcomes] and the optional [effortComparison] as survey [surveyId]'s outcome, answered
   * now by the signed-in user. "Not yet" is dropped when combined with other outcomes.
   */
  suspend fun saveOutcome(
    surveyId: String,
    outcomes: Set<SurveyOutcomeKind>,
    effortComparison: EffortComparison?,
  ): SurveyOutcome {
    val used = outcomes - SurveyOutcomeKind.NOT_YET
    val outcome =
      SurveyOutcome(
        outcomes = used.ifEmpty { outcomes },
        effortComparison = effortComparison,
        answeredAt = isoUtc(now()),
        // The prototype identifies users by email; Ground stores the account's user ID.
        answeredBy = authRepository.getSession().profile.email,
      )
    surveyRepository.setSurveyOutcome(surveyId, outcome)
    return outcome
  }
}
