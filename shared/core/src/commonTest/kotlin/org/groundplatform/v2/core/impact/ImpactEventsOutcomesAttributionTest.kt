/*
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.groundplatform.v2.core.impact

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.groundplatform.v2.core.impact.AttributionBasis.DATA_COLLECTED
import org.groundplatform.v2.core.impact.AttributionBasis.EXPLORATORY
import org.groundplatform.v2.core.impact.AttributionBasis.OFFICIAL_SUBMISSION
import org.groundplatform.v2.core.impact.AttributionBasis.PARTNER_USE
import org.groundplatform.v2.core.impact.AttributionBasis.PURPOSE_EXPORT
import org.groundplatform.v2.core.impact.Fx.plot
import org.groundplatform.v2.core.impact.Fx.run
import org.groundplatform.v2.core.impact.Fx.survey
import org.groundplatform.v2.core.impact.ImpactEventKind.EXPORT
import org.groundplatform.v2.core.impact.ImpactEventKind.PARTNER_PUSH
import org.groundplatform.v2.core.impact.ImpactEventKind.SURVEY_CLOSED
import org.groundplatform.v2.core.impact.ImpactMetric.ATTRIBUTION
import org.groundplatform.v2.core.impact.ImpactMetric.EVENT
import org.groundplatform.v2.core.impact.ImpactMetric.OUTCOME
import org.groundplatform.v2.core.impact.ImpactOutcomeKind.LAND_TITLING
import org.groundplatform.v2.core.impact.ImpactOutcomeKind.NOT_YET
import org.groundplatform.v2.core.impact.ImpactOutcomeKind.PROTECTED_AREA_MANAGEMENT
import org.groundplatform.v2.core.impact.ImpactOutcomeKind.REPORTED_FERM
import org.groundplatform.v2.core.impact.ImpactOutcomeKind.SHARED_WITH_BUYERS
import org.groundplatform.v2.core.impact.ImpactOutcomeKind.SUBMITTED_EUDR_DDS
import org.groundplatform.v2.core.impact.ImpactOutcomeKind.TRAINED_OR_VALIDATED_MODEL
import org.groundplatform.v2.core.impact.ImpactScopeType.COUNTRY
import org.groundplatform.v2.core.impact.ImpactScopeType.GLOBAL
import org.groundplatform.v2.core.impact.ImpactScopeType.ORGANIZATION
import org.groundplatform.v2.core.impact.ImpactScopeType.SURVEY

class ImpactEventsOutcomesAttributionTest {
  private val optedOut =
    listOf(ImpactOrganization("o3", countryCode = "KE", excludeFromPlatformAggregates = true))

  private fun events(result: ImpactRunResult, scope: ImpactScopeType, id: String) =
    result
      .rows(scope, id)
      .filter { it.metric == EVENT }
      .map { Triple(it.eventKind, it.value, it.featureCount) to it.areaHa }

  @Test
  fun events_countedPerKindWithRecordedCoverage() {
    val result =
      run(
        listOf(
          survey(
            "s1",
            "o1",
            events =
              listOf(
                ImpactEventRecord(PARTNER_PUSH, 4, 1.5),
                ImpactEventRecord(EXPORT, 3, 1.0),
                ImpactEventRecord(EXPORT, 5, 2.0, "eudr-geojson"),
              ),
          ),
          survey("s2", "o1", events = listOf(ImpactEventRecord(EXPORT, 1, 0.25))),
          survey("s3", "o3", events = listOf(ImpactEventRecord(SURVEY_CLOSED, 9, 9.0))),
        ),
        organizations = optedOut,
      )
    assertEquals(
      listOf(Triple(EXPORT, 2.0, 8L) to 3.0, Triple(PARTNER_PUSH, 1.0, 4L) to 1.5),
      events(result, SURVEY, "s1"),
    )
    assertEquals(
      listOf(Triple(EXPORT, 3.0, 9L) to 3.25, Triple(PARTNER_PUSH, 1.0, 4L) to 1.5),
      events(result, ORGANIZATION, "o1"),
    )
    assertEquals(listOf(Triple(SURVEY_CLOSED, 1.0, 9L) to 9.0), events(result, ORGANIZATION, "o3"))
    // The opted-out organization's events don't reach global scope.
    assertEquals(
      listOf(Triple(EXPORT, 3.0, 9L) to 3.25, Triple(PARTNER_PUSH, 1.0, 4L) to 1.5),
      events(result, GLOBAL, ""),
    )
    assertTrue(result.rows.none { it.scopeType == COUNTRY })
  }

  private fun outcomes(result: ImpactRunResult, scope: ImpactScopeType, id: String) =
    result.rows(scope, id).filter { it.metric == OUTCOME }.map { it.outcomeKind to it.value }

  @Test
  fun outcomes_notYetIsSeparateFromUnanswered() {
    val surveys =
      listOf(
        survey("closed-unanswered", "o1", isClosed = true),
        survey("closed-empty", "o1", isClosed = true, outcome = emptySet()),
        survey("closed-not-yet", "o1", isClosed = true, outcome = setOf(NOT_YET)),
        survey("closed-mixed", "o1", isClosed = true, outcome = setOf(NOT_YET, LAND_TITLING)),
        survey(
          "closed-two",
          "o1",
          isClosed = true,
          outcome = setOf(SHARED_WITH_BUYERS, SUBMITTED_EUDR_DDS),
        ),
        survey("open-unanswered", "o1"),
        survey("open-answered", "o1", outcome = setOf(REPORTED_FERM)),
        survey("opted-out", "o3", isClosed = true),
      )
    val result = run(surveys, organizations = optedOut)
    assertEquals(listOf(OUTCOME_UNANSWERED to 1.0), outcomes(result, SURVEY, "closed-unanswered"))
    assertEquals(listOf("NOT_YET" to 1.0), outcomes(result, SURVEY, "closed-empty"))
    assertEquals(listOf("NOT_YET" to 1.0), outcomes(result, SURVEY, "closed-not-yet"))
    assertEquals(listOf("LAND_TITLING" to 1.0), outcomes(result, SURVEY, "closed-mixed"))
    assertEquals(
      listOf("SUBMITTED_EUDR_DDS" to 1.0, "SHARED_WITH_BUYERS" to 1.0),
      outcomes(result, SURVEY, "closed-two"),
    )
    assertEquals(emptyList(), outcomes(result, SURVEY, "open-unanswered"))
    assertEquals(listOf("REPORTED_FERM" to 1.0), outcomes(result, SURVEY, "open-answered"))
    val organization =
      listOf(
        "SUBMITTED_EUDR_DDS" to 1.0,
        "REPORTED_FERM" to 1.0,
        "LAND_TITLING" to 1.0,
        "SHARED_WITH_BUYERS" to 1.0,
        "NOT_YET" to 2.0,
        OUTCOME_UNANSWERED to 1.0,
      )
    assertEquals(organization, outcomes(result, ORGANIZATION, "o1"))
    assertEquals(organization, outcomes(result, GLOBAL, ""))
  }

  private val eudr =
    ImpactPurpose("eudr", isGlobal = true, exportProfileIds = setOf("eudr-geojson"))
  private val local = ImpactPurpose("local", isGlobal = false)

  private fun basis(survey: ImpactSurvey, config: ImpactConfig = ImpactConfig()) =
    ImpactAggregator.run(ImpactInput(listOf(survey), emptyList()), config)
      .attributions
      .getValue(survey.id)
      .basis

  private fun plots(n: Int) = List(n) { plot("e$it") }

  @Test
  fun attribution_everyBasisAndPrecedence() {
    val cases =
      listOf(
        survey("x", "o", outcome = setOf(SUBMITTED_EUDR_DDS)) to OFFICIAL_SUBMISSION,
        survey("x", "o", outcome = setOf(REPORTED_FERM)) to OFFICIAL_SUBMISSION,
        survey("x", "o", outcome = setOf(PROTECTED_AREA_MANAGEMENT)) to OFFICIAL_SUBMISSION,
        // Official outranks partner use.
        survey(
          "x",
          "o",
          outcome = setOf(SUBMITTED_EUDR_DDS, SHARED_WITH_BUYERS),
          events = listOf(ImpactEventRecord(PARTNER_PUSH)),
        ) to OFFICIAL_SUBMISSION,
        survey("x", "o", events = listOf(ImpactEventRecord(PARTNER_PUSH))) to PARTNER_USE,
        survey("x", "o", outcome = setOf(SHARED_WITH_BUYERS)) to PARTNER_USE,
        survey("x", "o", outcome = setOf(LAND_TITLING)) to PARTNER_USE,
        survey("x", "o", outcome = setOf(TRAINED_OR_VALIDATED_MODEL)) to PARTNER_USE,
        // Partner use outranks a purpose export.
        survey(
          "x",
          "o",
          purposes = listOf(eudr),
          events =
            listOf(
              ImpactEventRecord(EXPORT, exportProfileId = "eudr-geojson"),
              ImpactEventRecord(PARTNER_PUSH),
            ),
        ) to PARTNER_USE,
        survey(
          "x",
          "o",
          purposes = listOf(eudr),
          events = listOf(ImpactEventRecord(EXPORT, exportProfileId = "eudr-geojson")),
        ) to PURPOSE_EXPORT,
        // Exports with another profile, or none, don't count as purpose exports.
        survey(
          "x",
          "o",
          purposes = listOf(eudr),
          features = plots(1),
          events = listOf(ImpactEventRecord(EXPORT, exportProfileId = "other")),
        ) to DATA_COLLECTED,
        survey(
          "x",
          "o",
          purposes = listOf(eudr),
          features = plots(1),
          events = listOf(ImpactEventRecord(EXPORT)),
        ) to DATA_COLLECTED,
        // Purpose export without purposes is impossible: no profile is enabled.
        survey(
          "x",
          "o",
          features = plots(4),
          events = listOf(ImpactEventRecord(EXPORT, exportProfileId = "eudr-geojson")),
        ) to EXPLORATORY,
        survey("x", "o", features = plots(4)) to EXPLORATORY,
        survey("x", "o", features = plots(5)) to DATA_COLLECTED,
        survey("x", "o", purposes = listOf(local), features = plots(1)) to DATA_COLLECTED,
        survey("x", "o", purposes = listOf(local), submissionCount = 3) to DATA_COLLECTED,
        // No data yet.
        survey("x", "o", purposes = listOf(eudr)) to EXPLORATORY,
        // No purposes and few features is exploratory even with submissions.
        survey("x", "o", submissionCount = 10) to EXPLORATORY,
        survey(
          "x",
          null,
          outcome = setOf(NOT_YET),
          isClosed = true,
          features = plots(1),
          purposes = listOf(local),
        ) to DATA_COLLECTED,
      )
    for ((i, case) in cases.withIndex()) {
      assertEquals(case.second, basis(case.first), "case $i: ${case.first}")
    }
    assertEquals(
      DATA_COLLECTED,
      basis(survey("x", "o", features = plots(2)), ImpactConfig(exploratoryMaxFeatures = 2)),
    )
    assertEquals(5, OFFICIAL_SUBMISSION.score)
    assertEquals(1, EXPLORATORY.score)
  }

  private fun scores(result: ImpactRunResult, scope: ImpactScopeType, id: String) =
    result
      .rows(scope, id)
      .filter { it.metric == ATTRIBUTION }
      .map { it.attributionScore to it.value }

  @Test
  fun attributionRows_surveyOrganizationAndGlobalDistribution() {
    val result =
      run(
        listOf(
          survey("a", "o1", purposes = listOf(eudr), outcome = setOf(SUBMITTED_EUDR_DDS)),
          survey("b", "o1", purposes = listOf(local), features = plots(1)),
          survey("c", "o1", purposes = listOf(eudr, local), features = plots(1)),
          survey("d", "o2", features = plots(1)),
          survey("e", "o3", purposes = listOf(eudr), outcome = setOf(SUBMITTED_EUDR_DDS)),
          survey(
            "f",
            null,
            purposes = listOf(eudr),
            events = listOf(ImpactEventRecord(PARTNER_PUSH)),
          ),
        ),
        organizations = optedOut,
      )
    assertEquals(listOf(5 to 1.0), scores(result, SURVEY, "a"))
    assertEquals(listOf(2 to 1.0), scores(result, SURVEY, "b"))
    assertEquals(listOf(2 to 2.0, 5 to 1.0), scores(result, ORGANIZATION, "o1"))
    assertEquals(listOf(1 to 1.0), scores(result, ORGANIZATION, "o2"))
    assertEquals(listOf(5 to 1.0), scores(result, ORGANIZATION, "o3"))
    // Global: only surveys with a global purpose (a, c, f) of eligible organizations (not e).
    assertEquals(listOf(2 to 1.0, 4 to 1.0, 5 to 1.0), scores(result, GLOBAL, ""))
    assertEquals(listOf("a", "b", "c", "d", "e", "f"), result.attributions.keys.toList())
    assertEquals(PARTNER_USE, result.attributions.getValue("f").basis)
  }
}
