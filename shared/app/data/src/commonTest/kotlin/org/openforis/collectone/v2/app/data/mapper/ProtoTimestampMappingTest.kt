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

import com.squareup.wire.ofEpochSecond
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import openforis.collectone.v1.data.ImpactEvent as ImpactEventProto
import openforis.collectone.v1.data.ImpactEventType as ImpactEventTypeProto
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyOutcome as SurveyOutcomeProto
import openforis.collectone.v1.survey.SurveyState
import org.openforis.collectone.v2.app.domain.model.ImpactEvent
import org.openforis.collectone.v2.app.domain.model.ImpactEventType
import org.openforis.collectone.v2.app.domain.model.SurveyLifecycleState
import org.openforis.collectone.v2.app.domain.model.SurveyOutcome
import org.openforis.collectone.v2.app.domain.model.SurveyOutcomeKind
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem

/**
 * The domain models keep lifecycle times as ISO 8601 UTC text, while the protos store them as
 * `google.protobuf.Timestamp` fields. These tests pin the mapping between the two.
 */
class ProtoTimestampMappingTest {
  /** 2026-05-04T10:20:30Z. */
  private val closedAtSeconds = 1_777_890_030L
  private val closedAtIso = "2026-05-04T10:20:30Z"

  /** 2026-05-05T08:00:00Z. */
  private val answeredAtSeconds = 1_777_968_000L
  private val answeredAtIso = "2026-05-05T08:00:00Z"

  /** 2026-04-02T09:15:00Z. */
  private val occurredAtSeconds = 1_775_121_300L
  private val occurredAtIso = "2026-04-02T09:15:00Z"

  private fun survey(closedAt: String?, outcome: SurveyOutcome? = null) =
    SurveyPreviewItem(
      id = "survey-1",
      title = "Shade tree monitoring",
      description = "",
      location = "",
      isDownloaded = false,
      entityCount = 0,
      state = if (closedAt == null) SurveyLifecycleState.PUBLISHED else SurveyLifecycleState.CLOSED,
      closedAt = closedAt,
      outcome = outcome,
    )

  private fun surveyDefOf(survey: SurveyPreviewItem): SurveyDef =
    ProtoSurveyMapper.surveyToProto(
        survey = survey,
        forms = emptyList(),
        mapLayers = emptyList(),
        config = null,
        editorDraft = null,
      )
      .first

  private fun impactEvent() =
    ImpactEvent(
      id = "impact-1",
      type = ImpactEventType.EXPORT,
      surveyId = "survey-1",
      organizationId = null,
      occurredAt = occurredAtIso,
      actorUserId = "user-1",
      isUploaded = true,
    )

  @Test
  fun closedAt_isWrittenAsTheClosedAtTimestamp() {
    val surveyDef = surveyDefOf(survey(closedAt = closedAtIso))

    assertEquals(ofEpochSecond(closedAtSeconds, 0L), surveyDef.closed_at)
  }

  @Test
  fun closedAt_isReadFromTheClosedAtTimestamp() {
    val surveyDef =
      SurveyDef(
        survey_id = "survey-1",
        state = SurveyState.CLOSED,
        closed_at = ofEpochSecond(closedAtSeconds, 0L),
      )

    val survey = ProtoSurveyMapper.surveyPreviewFromProto(surveyDef, acl = null)

    assertEquals(closedAtIso, survey.closedAt)
  }

  @Test
  fun closedAt_isNullWithoutAClosedAtTimestamp() {
    val surveyDef = SurveyDef(survey_id = "survey-1", state = SurveyState.PUBLISHED)

    val survey = ProtoSurveyMapper.surveyPreviewFromProto(surveyDef, acl = null)

    assertNull(survey.closedAt)
  }

  @Test
  fun outcomeAnsweredAt_isWrittenAsTheAnsweredAtTimestamp() {
    val outcome =
      SurveyOutcome(
        outcomes = setOf(SurveyOutcomeKind.LAND_TITLING),
        answeredAt = answeredAtIso,
        answeredBy = "owner@example.org",
      )

    val surveyDef = surveyDefOf(survey(closedAt = closedAtIso, outcome = outcome))

    assertEquals(ofEpochSecond(answeredAtSeconds, 0L), surveyDef.outcome?.answered_at)
  }

  @Test
  fun outcomeAnsweredAt_isReadFromTheAnsweredAtTimestamp() {
    val surveyDef =
      SurveyDef(
        survey_id = "survey-1",
        state = SurveyState.CLOSED,
        outcome =
          SurveyOutcomeProto(
            outcomes = listOf(SurveyOutcomeProto.Outcome.LAND_TITLING),
            answered_at = ofEpochSecond(answeredAtSeconds, 0L),
            answered_by = "owner@example.org",
          ),
      )

    val survey = ProtoSurveyMapper.surveyPreviewFromProto(surveyDef, acl = null)

    assertEquals(answeredAtIso, survey.outcome?.answeredAt)
  }

  @Test
  fun impactEventOccurredAt_isWrittenAsTheOccurredAtTimestamp() {
    val proto = ProtoRecordMapper.impactEventToProto(impactEvent())

    assertEquals(ofEpochSecond(occurredAtSeconds, 0L), proto.occurred_at)
  }

  @Test
  fun impactEventOccurredAt_isReadFromTheOccurredAtTimestamp() {
    val proto =
      ImpactEventProto(
        event_id = "impact-1",
        type = ImpactEventTypeProto.EXPORT,
        survey_id = "survey-1",
        occurred_at = ofEpochSecond(occurredAtSeconds, 0L),
        actor_user_id = "user-1",
      )

    assertEquals(occurredAtIso, ProtoRecordMapper.impactEventFromProto(proto).occurredAt)
  }

  @Test
  fun dateOrIso_atUtcMidnight_isJustTheDate() {
    // 2026-05-04T00:00:00Z.
    assertEquals("2026-05-04", formatInstantDateOrIso(ofEpochSecond(1_777_852_800L, 0L)))
  }

  @Test
  fun dateOrIso_atAnyOtherTime_isTheDateTimeToTheSecond() {
    assertEquals(closedAtIso, formatInstantDateOrIso(ofEpochSecond(closedAtSeconds, 500_000_000L)))
  }

  @Test
  fun dateOrIso_withoutATimestamp_isEmpty() {
    assertEquals("", formatInstantDateOrIso(null))
  }
}
