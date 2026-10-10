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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.LocalStore
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.runNow
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.seededStore
import org.groundplatform.v2.devtools.prototypeapp.data.repository.AuthRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.ConnectivityRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.ImpactEventRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyEditorRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.seed.PrototypeFakeOrganizationsData
import org.groundplatform.v2.devtools.prototypeapp.domain.model.EffortComparison
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactCoverage
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactEventType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyLifecycleState
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyOutcomeKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.isoUtc

/** A [SurveyLifecycleUseCase] over [store] with a settable clock, recording impact events. */
internal class SurveyLifecycleFixture(val store: LocalStore = seededStore()) {
  var nowMillis: Long = 1_791_331_200_000L // 2026-10-07T00:00:00Z
  val surveyRepository = SurveyRepositoryImpl(store)
  val impactEventRepository = ImpactEventRepositoryImpl(store)
  val authRepository = AuthRepositoryImpl()
  val lifecycle =
    SurveyLifecycleUseCase(
      surveyRepository = surveyRepository,
      authRepository = authRepository,
      recordImpactEvent =
        RecordImpactEventUseCase(
          impactEventRepository = impactEventRepository,
          surveyRepository = surveyRepository,
          surveyEditorRepository = SurveyEditorRepositoryImpl(store),
          authRepository = authRepository,
          connectivityRepository = ConnectivityRepositoryImpl(),
          now = { isoUtc(nowMillis) },
        ),
      now = { nowMillis },
    )

  fun survey(id: String) = runNow { surveyRepository.getSurveys().first { it.id == id } }

  /** Survey closed events recorded for survey [id] (the seed may hold other events). */
  fun closedEvents(id: String) = runNow {
    impactEventRepository.getEvents().filter {
      it.surveyId == id && it.type == ImpactEventType.SURVEY_CLOSED
    }
  }

  fun entities(id: String) = runNow { surveyRepository.observeSurveyContent(id).first().entities }
}

class SurveyLifecycleUseCaseTest {
  private val surveyId = "survey-single-point-land-use"

  @Test
  fun close_setsClosedTime_andRecordsSurveyClosedWithCoverage() {
    val f = SurveyLifecycleFixture()
    val stopped = runNow { f.lifecycle.setState(surveyId, SurveyLifecycleState.CLOSED) }

    assertTrue(stopped)
    val survey = f.survey(surveyId)
    assertEquals(SurveyLifecycleState.CLOSED, survey.state)
    assertEquals(isoUtc(f.nowMillis), survey.closedAt)
    val event = f.closedEvents(surveyId).single()
    assertEquals(ImpactEventType.SURVEY_CLOSED, event.type)
    assertEquals(surveyId, event.surveyId)
    val coverage = ImpactCoverage.of(f.entities(surveyId))
    assertTrue(coverage.featureCount > 0)
    assertEquals(coverage.featureCount, event.featureCount)
    assertEquals(coverage.areaHa, event.areaHa)
    assertEquals(PrototypeFakeOrganizationsData.SIGNED_IN_EMAIL, event.actorUserId)
  }

  @Test
  fun archiveAfterClose_keepsClosedTime_andRecordsNothingNew() {
    val f = SurveyLifecycleFixture()
    runNow { f.lifecycle.setState(surveyId, SurveyLifecycleState.CLOSED) }
    val closedAt = f.survey(surveyId).closedAt
    f.nowMillis += 86_400_000L

    val stopped = runNow { f.lifecycle.setState(surveyId, SurveyLifecycleState.ARCHIVED) }

    assertFalse(stopped)
    assertEquals(SurveyLifecycleState.ARCHIVED, f.survey(surveyId).state)
    assertEquals(closedAt, f.survey(surveyId).closedAt)
    assertEquals(1, f.closedEvents(surveyId).size)
  }

  @Test
  fun archivePublished_stopsCollecting() {
    val f = SurveyLifecycleFixture()
    assertTrue(runNow { f.lifecycle.setState(surveyId, SurveyLifecycleState.ARCHIVED) })
    assertEquals(isoUtc(f.nowMillis), f.survey(surveyId).closedAt)
    assertEquals(ImpactEventType.SURVEY_CLOSED, f.closedEvents(surveyId).single().type)
  }

  @Test
  fun reopen_clearsClosedTime_keepsOutcome_andSameStateIsANoOp() {
    val f = SurveyLifecycleFixture()
    runNow { f.lifecycle.setState(surveyId, SurveyLifecycleState.CLOSED) }
    runNow { f.lifecycle.saveOutcome(surveyId, setOf(SurveyOutcomeKind.LAND_TITLING), null) }
    assertFalse(runNow { f.lifecycle.setState(surveyId, SurveyLifecycleState.CLOSED) })

    assertFalse(runNow { f.lifecycle.setState(surveyId, SurveyLifecycleState.PUBLISHED) })

    val survey = f.survey(surveyId)
    assertEquals(SurveyLifecycleState.PUBLISHED, survey.state)
    assertNull(survey.closedAt)
    assertEquals(setOf(SurveyOutcomeKind.LAND_TITLING), survey.outcome?.outcomes)
    assertEquals(1, f.closedEvents(surveyId).size)
  }

  @Test
  fun saveOutcome_storesTheAnswer_withTimeAndUser_droppingNotYetFromAMix() {
    val f = SurveyLifecycleFixture()
    val saved = runNow {
      f.lifecycle.saveOutcome(
        surveyId,
        setOf(SurveyOutcomeKind.NOT_YET, SurveyOutcomeKind.SUBMITTED_EUDR_DDS),
        EffortComparison.LESS,
      )
    }

    assertEquals(setOf(SurveyOutcomeKind.SUBMITTED_EUDR_DDS), saved.outcomes)
    assertEquals(EffortComparison.LESS, saved.effortComparison)
    assertEquals(isoUtc(f.nowMillis), saved.answeredAt)
    assertEquals(PrototypeFakeOrganizationsData.SIGNED_IN_EMAIL, saved.answeredBy)
    assertEquals(saved, f.survey(surveyId).outcome)

    val notYet = runNow {
      f.lifecycle.saveOutcome(surveyId, setOf(SurveyOutcomeKind.NOT_YET), null)
    }
    assertTrue(notYet.isNotYet)
    assertEquals(setOf(SurveyOutcomeKind.NOT_YET), f.survey(surveyId).outcome?.outcomes)
  }

  @Test
  fun unknownSurvey_isIgnored() {
    val f = SurveyLifecycleFixture()
    assertFalse(runNow { f.lifecycle.setState("missing", SurveyLifecycleState.CLOSED) })
    assertTrue(f.closedEvents("missing").isEmpty())
  }
}
