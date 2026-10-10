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
import org.groundplatform.v2.core.impact.Fx.concept
import org.groundplatform.v2.core.impact.Fx.plot
import org.groundplatform.v2.core.impact.Fx.survey

class ImpactAggregatorTest {
  @Test
  fun emptyInput_onlyGlobalFeaturesRow() {
    val result = ImpactAggregator.run(ImpactInput(emptyList(), emptyList()))
    assertEquals(
      listOf(ImpactAggregateRow(ImpactScopeType.GLOBAL, "", ImpactMetric.FEATURES)),
      result.rows,
    )
    assertEquals(ImpactDiagnostics(), result.diagnostics)
    assertEquals(emptyMap(), result.attributions)
  }

  @Test
  fun everySurveyAndOrganizationGetsAFeaturesRow() {
    val result = Fx.run(listOf(survey("s1", "o1"), survey("s2", null)))
    assertEquals(
      listOf(
        ImpactAggregateRow(ImpactScopeType.SURVEY, "s1", ImpactMetric.FEATURES),
        ImpactAggregateRow(
          ImpactScopeType.SURVEY,
          "s1",
          ImpactMetric.ATTRIBUTION,
          attributionScore = 1,
          value = 1.0,
        ),
        ImpactAggregateRow(ImpactScopeType.SURVEY, "s2", ImpactMetric.FEATURES),
        ImpactAggregateRow(
          ImpactScopeType.SURVEY,
          "s2",
          ImpactMetric.ATTRIBUTION,
          attributionScore = 1,
          value = 1.0,
        ),
        ImpactAggregateRow(ImpactScopeType.ORGANIZATION, "o1", ImpactMetric.FEATURES),
        ImpactAggregateRow(
          ImpactScopeType.ORGANIZATION,
          "o1",
          ImpactMetric.ATTRIBUTION,
          attributionScore = 1,
          value = 1.0,
        ),
        ImpactAggregateRow(ImpactScopeType.GLOBAL, "", ImpactMetric.FEATURES),
      ),
      result.rows,
    )
  }

  @Test
  fun rowsOrderedByScopeThenMetric() {
    val code = concept("b.code", ImpactAggregation.COUNT_BY_CODE)
    val count = concept("a.count", ImpactAggregation.COUNT_DISTINCT_FEATURES)
    val producer = concept("core.producer_id", ImpactAggregation.NONE)
    val links = setOf(code.id, count.id, producer.id)
    val values = mapOf(code.id to "z y", count.id to "1", producer.id to "P")
    val result =
      Fx.run(
        listOf(
          survey(
            "s2",
            "o2",
            links = links,
            features =
              listOf(
                ImpactFeature(
                  "a",
                  "G",
                  areaHa = 1.0,
                  countryCode = "KE",
                  values = values.mapValues { ImpactValue(it.value) },
                )
              ),
            events =
              listOf(
                ImpactEventRecord(ImpactEventKind.DOI_MINTED),
                ImpactEventRecord(ImpactEventKind.EXPORT),
              ),
            isClosed = true,
          ),
          survey("s1", "o1", features = listOf(plot("b", "H"))),
        ),
        listOf(code, count, producer),
        config = ImpactConfig(countryMinFeatures = 1),
      )
    val s2 =
      result.rows(ImpactScopeType.SURVEY, "s2").map {
        listOf(it.metric.name, it.conceptId, it.code, it.eventKind?.name.orEmpty())
          .joinToString("/")
      }
    assertEquals(
      listOf(
        "FEATURES///",
        "CONCEPT/a.count//",
        "CONCEPT/b.code/y/",
        "CONCEPT/b.code/z/",
        "DISTINCT_VALUES/core.producer_id//",
        "EVENT///EXPORT",
        "EVENT///DOI_MINTED",
        "OUTCOME///",
        "ATTRIBUTION///",
      ),
      s2,
    )
    val scopes = result.rows.map { it.scopeType to it.scopeId }.distinct()
    assertEquals(
      listOf(
        ImpactScopeType.SURVEY to "s1",
        ImpactScopeType.SURVEY to "s2",
        ImpactScopeType.ORGANIZATION to "o1",
        ImpactScopeType.ORGANIZATION to "o2",
        ImpactScopeType.COUNTRY to "KE",
        ImpactScopeType.GLOBAL to "",
      ),
      scopes,
    )
  }
}
