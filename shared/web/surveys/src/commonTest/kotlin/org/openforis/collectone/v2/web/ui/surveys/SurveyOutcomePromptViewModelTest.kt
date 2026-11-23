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
package org.openforis.collectone.v2.web.ui.surveys

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.repository.OrganizationRepositoryImpl
import org.openforis.collectone.v2.app.data.seed.SampleSurveysData
import org.openforis.collectone.v2.app.domain.model.SurveyLifecycleState
import org.openforis.collectone.v2.app.domain.model.SurveyOutcomeKind
import org.openforis.collectone.v2.app.domain.model.editor.isoUtc
import org.openforis.collectone.v2.app.domain.model.editor.parseIsoUtc
import org.openforis.collectone.v2.web.domain.usecase.SurveyLifecycleFixture

/** The "Was this data used?" badge on the web surveys page and the card it opens. */
class SurveyOutcomePromptViewModelTest {
  private val closedId = SampleSurveysData.CLOSED_SURVEY_ID
  private val closedAt = checkNotNull(parseIsoUtc(SampleSurveysData.CLOSED_SURVEY_CLOSED_AT))

  private class Fixture(val lifecycle: SurveyLifecycleFixture = SurveyLifecycleFixture()) {
    /** A prompt ViewModel that reads the clock as [now]. */
    fun viewModel(now: Instant): SurveyOutcomePromptViewModel {
      lifecycle.clock.current = now
      return SurveyOutcomePromptViewModel(
        surveyRepository = lifecycle.surveyRepository,
        organizationRepository = OrganizationRepositoryImpl(lifecycle.store),
        authRepository = lifecycle.authRepository,
        surveyLifecycle = lifecycle.lifecycle,
        clock = lifecycle.clock,
      )
    }
  }

  @Test
  fun seededClosedSurvey_showsTheBadge_onlyOnce90DaysHavePassed() {
    val f = Fixture()
    assertFalse(closedId in f.viewModel(closedAt + 89.days).uiState.value.dueSurveyIds)
    assertTrue(closedId in f.viewModel(closedAt + 90.days).uiState.value.dueSurveyIds)
  }

  @Test
  fun seededClosedSurvey_showsTheBadgeToday() {
    // The sample data's current dates (October 2026) are well past 90 days after the seed's close.
    val f = Fixture()
    val today = checkNotNull(parseIsoUtc("2026-10-10T00:00:00Z"))
    val state = f.viewModel(today).uiState.value
    assertEquals(setOf(closedId), state.dueSurveyIds)
    assertNull(state.card)
  }

  @Test
  fun badge_opensTheCard_andAUseHidesItForGood() {
    val f = Fixture()
    val viewModel = f.viewModel(closedAt + 120.days)
    viewModel.openOutcomeCard(closedId)
    val card = assertNotNull(viewModel.uiState.value.card)
    assertEquals(closedId, card.surveyId)

    viewModel.toggleOutcome(SurveyOutcomeKind.TRAINED_OR_VALIDATED_MODEL)
    viewModel.saveOutcome()

    assertNull(viewModel.uiState.value.card)
    assertFalse(closedId in viewModel.uiState.value.dueSurveyIds)
    assertFalse(closedId in f.viewModel(closedAt + 2000.days).uiState.value.dueSurveyIds)
  }

  @Test
  fun notYet_restartsThe90DayWaitFromTheAnswer() {
    val f = Fixture()
    val answeredAt = closedAt + 120.days
    val viewModel = f.viewModel(answeredAt)
    viewModel.openOutcomeCard(closedId)
    viewModel.toggleOutcome(SurveyOutcomeKind.NOT_YET)
    viewModel.saveOutcome()

    assertEquals(
      isoUtc(answeredAt),
      f.lifecycle.survey(closedId).outcome?.answeredAt,
    )
    assertFalse(closedId in viewModel.uiState.value.dueSurveyIds)
    assertFalse(closedId in f.viewModel(answeredAt + 89.days).uiState.value.dueSurveyIds)
    assertTrue(closedId in f.viewModel(answeredAt + 90.days).uiState.value.dueSurveyIds)
  }

  @Test
  fun skip_keepsTheBadge() {
    val f = Fixture()
    val viewModel = f.viewModel(closedAt + 120.days)
    viewModel.openOutcomeCard(closedId)
    viewModel.toggleOutcome(SurveyOutcomeKind.LAND_TITLING)
    viewModel.skipOutcome()

    assertNull(viewModel.uiState.value.card)
    assertNull(f.lifecycle.survey(closedId).outcome)
    assertTrue(closedId in viewModel.uiState.value.dueSurveyIds)
  }

  @Test
  fun surveysClosedFromTheEditor_joinTheBadgeAfter90Days() {
    val f = Fixture()
    val surveyId = "00000036-1a1c-5c51-997f-28b67d813aa3"
    f.lifecycle.clock.current = closedAt
    runNow { f.lifecycle.lifecycle.setState(surveyId, SurveyLifecycleState.ARCHIVED) }

    assertFalse(surveyId in f.viewModel(closedAt + 30.days).uiState.value.dueSurveyIds)
    assertTrue(surveyId in f.viewModel(closedAt + 90.days).uiState.value.dueSurveyIds)
  }

  @Test
  fun badge_isOnlyForPeopleWhoManageTheSurvey() {
    val f = Fixture()
    runNow {
      f.lifecycle.surveyRepository.updateSurvey(closedId) {
        it.copy(ownerEmail = "someone.else@example.org", organizationId = null)
      }
    }
    assertTrue(f.viewModel(closedAt + 120.days).uiState.value.dueSurveyIds.isEmpty())
  }
}
