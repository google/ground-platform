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

import kotlin.test.Test
import kotlin.test.assertEquals
import openforis.collectone.v1.survey.FormAvailability as FormAvailabilityProto
import openforis.collectone.v1.survey.SurveyOutcome as SurveyOutcomeProto
import openforis.collectone.v1.survey.SurveyState
import org.openforis.collectone.v2.app.domain.model.EffortComparison
import org.openforis.collectone.v2.app.domain.model.FormAvailability
import org.openforis.collectone.v2.app.domain.model.SurveyLifecycleState
import org.openforis.collectone.v2.app.domain.model.SurveyOutcome
import org.openforis.collectone.v2.app.domain.model.SurveyOutcomeKind

/** [SurveyLifecycleMapper] round-trips every value and decodes unset fields to safe defaults. */
class SurveyLifecycleMapperTest {
  @Test
  fun everyLifecycleState_roundTrips() {
    for (state in SurveyLifecycleState.entries) {
      assertEquals(
        state,
        SurveyLifecycleMapper.surveyStateFromProto(SurveyLifecycleMapper.surveyStateToProto(state)),
      )
    }
  }

  @Test
  fun unknownOrDeletedState_readsAsPublished() {
    assertEquals(
      SurveyLifecycleState.PUBLISHED,
      SurveyLifecycleMapper.surveyStateFromProto(SurveyState.SURVEY_STATE_UNSPECIFIED),
    )
    assertEquals(
      SurveyLifecycleState.PUBLISHED,
      SurveyLifecycleMapper.surveyStateFromProto(SurveyState.DELETED),
    )
  }

  @Test
  fun everyFormAvailability_roundTrips_withBothWrittenAsUnset() {
    assertEquals(null, SurveyLifecycleMapper.formAvailabilityToProto(FormAvailability.BOTH))
    for (availability in FormAvailability.entries) {
      assertEquals(
        availability,
        SurveyLifecycleMapper.formAvailabilityFromProto(
          SurveyLifecycleMapper.formAvailabilityToProto(availability)
        ),
      )
    }
  }

  @Test
  fun formAvailabilityWithBothFlags_readsAsBoth() {
    assertEquals(
      FormAvailability.BOTH,
      SurveyLifecycleMapper.formAvailabilityFromProto(
        FormAvailabilityProto(web = true, mobile = true)
      ),
    )
  }

  @Test
  fun everyOutcomeAndEffort_roundTrips() {
    val efforts = EffortComparison.entries + listOf(null)
    for (effort in efforts) {
      val outcome =
        SurveyOutcome(
          outcomes = SurveyOutcomeKind.entries.toSet(),
          effortComparison = effort,
          answeredAt = "2026-02-03T04:05:06Z",
          answeredBy = "ana@example.org",
        )

      assertEquals(
        outcome,
        SurveyLifecycleMapper.surveyOutcomeFromProto(
          SurveyLifecycleMapper.surveyOutcomeToProto(outcome)
        ),
      )
    }
  }

  @Test
  fun unspecifiedOutcomesAndMissingAnswerTime_areDropped() {
    val decoded =
      SurveyLifecycleMapper.surveyOutcomeFromProto(
        SurveyOutcomeProto(outcomes = listOf(SurveyOutcomeProto.Outcome.OUTCOME_UNSPECIFIED))
      )

    assertEquals(emptySet(), decoded.outcomes)
    assertEquals("", decoded.answeredAt)
    assertEquals(null, decoded.effortComparison)
  }
}
