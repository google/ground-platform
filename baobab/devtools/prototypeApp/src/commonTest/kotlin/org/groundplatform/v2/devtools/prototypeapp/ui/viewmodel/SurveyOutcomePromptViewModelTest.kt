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
package org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.runNow
import org.groundplatform.v2.devtools.prototypeapp.data.repository.OrganizationRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.seed.PrototypeFakeSurveysData
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyLifecycleState
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyOutcomeKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.isoUtc
import org.groundplatform.v2.devtools.prototypeapp.domain.model.epochMillisOfIsoUtc
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.SurveyLifecycleFixture

/** The "Was this data used?" badge on the web surveys page and the card it opens. */
class SurveyOutcomePromptViewModelTest {
  private val closedId = PrototypeFakeSurveysData.CLOSED_SURVEY_ID
  private val closedAtMillis =
    checkNotNull(epochMillisOfIsoUtc(PrototypeFakeSurveysData.CLOSED_SURVEY_CLOSED_AT))

  private fun days(n: Int) = n * 86_400_000L

  private class Fixture(val lifecycle: SurveyLifecycleFixture = SurveyLifecycleFixture()) {
    /** A prompt ViewModel that reads the clock as [nowMillis]. */
    fun viewModel(nowMillis: Long): SurveyOutcomePromptViewModel {
      lifecycle.nowMillis = nowMillis
      return SurveyOutcomePromptViewModel(
        surveyRepository = lifecycle.surveyRepository,
        organizationRepository = OrganizationRepositoryImpl(lifecycle.store),
        authRepository = lifecycle.authRepository,
        surveyLifecycle = lifecycle.lifecycle,
        scope = testScope(),
        now = { lifecycle.nowMillis },
      )
    }
  }

  @Test
  fun seededClosedSurvey_showsTheBadge_onlyOnce90DaysHavePassed() {
    val f = Fixture()
    assertFalse(closedId in f.viewModel(closedAtMillis + days(89)).uiState.value.dueSurveyIds)
    assertTrue(closedId in f.viewModel(closedAtMillis + days(90)).uiState.value.dueSurveyIds)
  }

  @Test
  fun seededClosedSurvey_showsTheBadgeToday() {
    // The prototype's current dates (October 2026) are well past 90 days after the seed's close.
    val f = Fixture()
    val today = checkNotNull(epochMillisOfIsoUtc("2026-10-10T00:00:00Z"))
    val state = f.viewModel(today).uiState.value
    assertEquals(setOf(closedId), state.dueSurveyIds)
    assertNull(state.card)
  }

  @Test
  fun badge_opensTheCard_andAUseHidesItForGood() {
    val f = Fixture()
    val viewModel = f.viewModel(closedAtMillis + days(120))
    viewModel.openOutcomeCard(closedId)
    val card = assertNotNull(viewModel.uiState.value.card)
    assertEquals(closedId, card.surveyId)

    viewModel.toggleOutcome(SurveyOutcomeKind.TRAINED_OR_VALIDATED_MODEL)
    viewModel.saveOutcome()

    assertNull(viewModel.uiState.value.card)
    assertFalse(closedId in viewModel.uiState.value.dueSurveyIds)
    assertFalse(closedId in f.viewModel(closedAtMillis + days(2000)).uiState.value.dueSurveyIds)
  }

  @Test
  fun notYet_restartsThe90DayWaitFromTheAnswer() {
    val f = Fixture()
    val answeredAt = closedAtMillis + days(120)
    val viewModel = f.viewModel(answeredAt)
    viewModel.openOutcomeCard(closedId)
    viewModel.toggleOutcome(SurveyOutcomeKind.NOT_YET)
    viewModel.saveOutcome()

    assertEquals(isoUtc(answeredAt), f.lifecycle.survey(closedId).outcome?.answeredAt)
    assertFalse(closedId in viewModel.uiState.value.dueSurveyIds)
    assertFalse(closedId in f.viewModel(answeredAt + days(89)).uiState.value.dueSurveyIds)
    assertTrue(closedId in f.viewModel(answeredAt + days(90)).uiState.value.dueSurveyIds)
  }

  @Test
  fun skip_keepsTheBadge() {
    val f = Fixture()
    val viewModel = f.viewModel(closedAtMillis + days(120))
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
    val surveyId = "survey-single-point-land-use"
    f.lifecycle.nowMillis = closedAtMillis
    runNow { f.lifecycle.lifecycle.setState(surveyId, SurveyLifecycleState.ARCHIVED) }

    assertFalse(surveyId in f.viewModel(closedAtMillis + days(30)).uiState.value.dueSurveyIds)
    assertTrue(surveyId in f.viewModel(closedAtMillis + days(90)).uiState.value.dueSurveyIds)
  }

  @Test
  fun badge_isOnlyForPeopleWhoManageTheSurvey() {
    val f = Fixture()
    runNow {
      f.lifecycle.surveyRepository.updateSurvey(closedId) {
        it.copy(ownerEmail = "someone.else@example.org", organizationId = null)
      }
    }
    assertTrue(f.viewModel(closedAtMillis + days(120)).uiState.value.dueSurveyIds.isEmpty())
  }
}
