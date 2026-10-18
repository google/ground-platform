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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.core.impact.Fx.concept
import org.groundplatform.v2.core.impact.Fx.plot
import org.groundplatform.v2.core.impact.Fx.run
import org.groundplatform.v2.core.impact.Fx.survey
import org.groundplatform.v2.core.impact.ImpactMetric.CONCEPT
import org.groundplatform.v2.core.impact.ImpactMetric.DISTINCT_VALUES
import org.groundplatform.v2.core.impact.ImpactScopeType.GLOBAL
import org.groundplatform.v2.core.impact.ImpactScopeType.ORGANIZATION
import org.groundplatform.v2.core.impact.ImpactScopeType.SURVEY

class ImpactConceptAggregationTest {
  /** Runs one survey of organization `o1` linking [concept], with one point plot per value. */
  private fun runValues(
    concept: ImpactConcept,
    values: List<ImpactValue?>,
    config: ImpactConfig = ImpactConfig(),
  ): ImpactRunResult =
    ImpactAggregator.run(
      ImpactInput(
        surveys =
          listOf(
            survey(
              "s1",
              "o1",
              links = setOf(concept.id),
              features =
                values.mapIndexed { i, value ->
                  ImpactFeature(
                    entityId = "e$i",
                    areaHa = (i + 1).toDouble(),
                    values = if (value == null) emptyMap() else mapOf(concept.id to value),
                  )
                },
            )
          ),
        concepts = listOf(concept),
      ),
      config,
    )

  private fun conceptRows(
    result: ImpactRunResult,
    scope: ImpactScopeType = SURVEY,
    id: String = "s1",
  ) = result.rows(scope, id).filter { it.metric == CONCEPT }

  @Test
  fun countDistinctFeatures_countsNonBlankValues() {
    val concept = concept("g.count", ImpactAggregation.COUNT_DISTINCT_FEATURES)
    val result =
      runValues(concept, listOf(ImpactValue("x"), ImpactValue("  "), null, ImpactValue("y")))
    // Areas are 1, 2, 3, 4 ha by position; plots 1 and 4 have values.
    assertFeatures(result.row(SURVEY, "s1", CONCEPT, concept.id), 2, 5.0)
  }

  @Test
  fun countByCode_oneRowPerCode_multiSelectCountsEachCodeOncePerFeature() {
    val concept = concept("g.code", ImpactAggregation.COUNT_BY_CODE)
    val result =
      runValues(
        concept,
        listOf(
          ImpactValue("coffee cocoa"),
          ImpactValue("coffee"),
          ImpactValue(" coffee  coffee "),
          ImpactValue(""),
          null,
        ),
      )
    val rows = conceptRows(result)
    assertEquals(listOf("cocoa", "coffee"), rows.map { it.code })
    assertFeatures(rows[0], 1, 1.0)
    assertFeatures(rows[1], 3, 6.0)
    assertEquals(0.0, rows[1].value)
  }

  @Test
  fun shareByCode_dividesByFeaturesWithAnyValue() {
    val concept = concept("g.share", ImpactAggregation.SHARE_BY_CODE)
    val result =
      runValues(
        concept,
        listOf(ImpactValue("a b"), ImpactValue("a"), ImpactValue("c"), ImpactValue(" "), null),
      )
    val rows = conceptRows(result).associateBy { it.code }
    assertEquals(setOf("a", "b", "c"), rows.keys)
    assertNear(2.0 / 3, rows.getValue("a").value)
    assertNear(1.0 / 3, rows.getValue("b").value)
    assertNear(1.0 / 3, rows.getValue("c").value)
    assertFeatures(rows.getValue("a"), 2, 3.0)
  }

  @Test
  fun sum_normalizesMassUnits() {
    val concept = concept("g.mass", ImpactAggregation.SUM, unit = "kg")
    val result =
      runValues(
        concept,
        listOf(
          ImpactValue("2"),
          ImpactValue("1.5", "t"),
          ImpactValue("500", "g"),
          ImpactValue("3", ""),
        ),
      )
    val row = result.row(SURVEY, "s1", CONCEPT, concept.id)
    assertNear(2 + 1_500 + 0.5 + 3, row.value)
    assertFeatures(row, 4, 10.0)
    assertTrue(result.diagnostics.unnormalizableValues.isEmpty())
  }

  @Test
  fun sum_normalizesAreaUnits() {
    val concept = concept("core.area_ha", ImpactAggregation.SUM, unit = "har")
    val result =
      runValues(
        concept,
        listOf(ImpactValue("5000", "m2"), ImpactValue("1"), ImpactValue("0.01", "km2")),
      )
    assertNear(0.5 + 1 + 1, result.row(SURVEY, "s1", CONCEPT, concept.id).value)
  }

  @Test
  fun mean_skipsNonNumbersWithDiagnostic() {
    val concept = concept("g.mean", ImpactAggregation.MEAN, unit = "m")
    val result =
      runValues(
        concept,
        listOf(ImpactValue("2"), ImpactValue("abc"), ImpactValue("400", "cm"), ImpactValue("")),
      )
    val row = result.row(SURVEY, "s1", CONCEPT, concept.id)
    assertNear(3.0, row.value)
    assertFeatures(row, 2, 4.0)
    assertEquals(
      listOf(
        UnnormalizableValue("s1", concept.id, "e1", "abc", null, UnnormalizableReason.NOT_A_NUMBER)
      ),
      result.diagnostics.unnormalizableValues,
    )
  }

  @Test
  fun unsupportedUnits_areSkippedWithDiagnostic() {
    val concept = concept("g.area", ImpactAggregation.SUM, unit = "har")
    val result =
      runValues(concept, listOf(ImpactValue("3", "kg"), ImpactValue("1", "acre"), ImpactValue("2")))
    assertNear(2.0, result.row(SURVEY, "s1", CONCEPT, concept.id).value)
    assertEquals(
      listOf(
        UnnormalizableValue(
          "s1",
          concept.id,
          "e0",
          "3",
          "kg",
          UnnormalizableReason.UNSUPPORTED_UNIT,
        ),
        UnnormalizableValue(
          "s1",
          concept.id,
          "e1",
          "1",
          "acre",
          UnnormalizableReason.UNSUPPORTED_UNIT,
        ),
      ),
      result.diagnostics.unnormalizableValues,
    )
  }

  @Test
  fun diagnostics_reportedOncePerFeatureAcrossScopes() {
    val concept = concept("g.sum", ImpactAggregation.SUM)
    val result = runValues(concept, listOf(ImpactValue("n/a")))
    // The value appears in survey, organization, and global scope but is reported once.
    assertEquals(1, result.diagnostics.unnormalizableValues.size)
    assertEquals(1, result.diagnostics.forSurvey("s1").unnormalizableValues.size)
    assertEquals(0, result.diagnostics.forSurvey("other").unnormalizableValues.size)
  }

  @Test
  fun sumAndMean_withoutValues_emitZeroRows() {
    val sum = concept("g.sum", ImpactAggregation.SUM)
    val mean = concept("g.mean", ImpactAggregation.MEAN)
    for (concept in listOf(sum, mean)) {
      val row =
        runValues(concept, listOf(null, ImpactValue(""))).row(SURVEY, "s1", CONCEPT, concept.id)
      assertFeatures(row, 0, 0.0)
      assertEquals(0.0, row.value)
    }
  }

  @Test
  fun none_emitsNoRows() {
    val concept = concept("core.producer_name_x", ImpactAggregation.NONE)
    assertTrue(conceptRows(runValues(concept, listOf(ImpactValue("Wanjiru")))).isEmpty())
  }

  @Test
  fun parseImpactNumber_acceptsDecimalsOnly() {
    assertEquals(1000.0, parseImpactNumber("1e3"))
    assertEquals(7.0, parseImpactNumber(" 7 "))
    assertEquals(-0.5, parseImpactNumber("-.5"))
    assertEquals(2.0, parseImpactNumber("+2."))
    for (text in
      listOf("0x10", "1f", "1d", "NaN", "Infinity", "INF", "1,5", "", "1e999", "1 2", "e3")) {
      assertNull(parseImpactNumber(text), text)
    }
  }

  @Test
  fun skippedLinks_unknownDeprecatedAndOtherOrganization() {
    val global = concept("g.count", ImpactAggregation.COUNT_DISTINCT_FEATURES)
    val deprecated = concept("g.old", ImpactAggregation.COUNT_DISTINCT_FEATURES, deprecated = true)
    val own = concept("org.o1.x", ImpactAggregation.COUNT_DISTINCT_FEATURES, owner = "o1")
    val other = concept("org.o2.x", ImpactAggregation.COUNT_DISTINCT_FEATURES, owner = "o2")
    val links = setOf(global.id, deprecated.id, own.id, other.id, "missing")
    val values = links.associateWith { "v" }
    val result =
      run(
        listOf(
          survey("s1", "o1", links = links, features = listOf(plot("a", values = values))),
          survey(
            "s2",
            null,
            links = setOf(own.id, global.id),
            features = listOf(plot("a", values = values)),
          ),
        ),
        listOf(global, deprecated, own, other),
      )
    assertEquals(
      listOf(
        SkippedLink("s1", deprecated.id, SkippedLinkReason.DEPRECATED),
        SkippedLink("s1", "missing", SkippedLinkReason.UNKNOWN),
        SkippedLink("s1", other.id, SkippedLinkReason.NOT_IN_LIBRARY),
        SkippedLink("s2", own.id, SkippedLinkReason.NOT_IN_LIBRARY),
      ),
      result.diagnostics.skippedLinks,
    )
    assertEquals(
      listOf(global.id, own.id),
      conceptRows(result).map { it.conceptId },
    )
    assertEquals(listOf(global.id), conceptRows(result, SURVEY, "s2").map { it.conceptId })
    assertEquals(1, result.diagnostics.forSurvey("s2").skippedLinks.size)
  }

  @Test
  fun valuesForUnlinkedConcepts_areIgnored() {
    val linked = concept("g.a", ImpactAggregation.COUNT_DISTINCT_FEATURES)
    val unlinked = concept("g.b", ImpactAggregation.COUNT_DISTINCT_FEATURES)
    val result =
      run(
        listOf(
          survey(
            "s1",
            "o1",
            links = setOf(linked.id),
            features = listOf(plot("a", values = mapOf(linked.id to "x", unlinked.id to "y"))),
          )
        ),
        listOf(linked, unlinked),
      )
    assertEquals(listOf(linked.id), conceptRows(result).map { it.conceptId })
    assertFalse(result.hasRow(GLOBAL, "", CONCEPT, unlinked.id))
  }

  @Test
  fun distinctValues_firstConfiguredConceptTheSurveyLinks() {
    val producerId =
      concept("core.producer_id", ImpactAggregation.NONE, privacy = ImpactPrivacy.SENSITIVE)
    val producerName =
      concept("core.producer_name", ImpactAggregation.NONE, privacy = ImpactPrivacy.SENSITIVE)
    val result =
      run(
        listOf(
          survey(
            "s1",
            "o1",
            links = setOf(producerId.id, producerName.id),
            features =
              listOf(
                plot("a", "G1", values = mapOf(producerId.id to "P1", producerName.id to "A")),
                plot("b", "G2", values = mapOf(producerId.id to "p1")),
                plot("c", "G3", values = mapOf(producerId.id to " P2 ")),
                plot("d", "G4", values = mapOf(producerId.id to " ")),
                plot("e", "G1", updatedAt = -1, values = mapOf(producerId.id to "P9")),
              ),
          ),
          survey(
            "s2",
            "o1",
            links = setOf(producerName.id),
            features = listOf(plot("f", values = mapOf(producerName.id to "B"))),
          ),
        ),
        listOf(producerId, producerName),
      )
    val s1 = result.row(SURVEY, "s1", DISTINCT_VALUES, producerId.id)
    assertEquals(2.0, s1.value)
    assertFeatures(s1, 3, 3.0)
    assertFalse(result.hasRow(SURVEY, "s1", DISTINCT_VALUES, producerName.id))
    assertEquals(1.0, result.row(SURVEY, "s2", DISTINCT_VALUES, producerName.id).value)
    // Organization scope uses the first configured concept any of its surveys links.
    assertEquals(2.0, result.row(ORGANIZATION, "o1", DISTINCT_VALUES, producerId.id).value)
    // SENSITIVE concepts never reach global scope.
    assertTrue(result.rows(GLOBAL).none { it.metric == DISTINCT_VALUES })
  }

  @Test
  fun distinctValues_nonSensitiveGlobalConcept_reachesGlobal() {
    val producerId =
      concept("core.producer_id", ImpactAggregation.NONE, privacy = ImpactPrivacy.ORG_ONLY)
    val result =
      run(
        listOf(
          survey(
            "s1",
            "o1",
            links = setOf(producerId.id),
            features = listOf(plot("a", "G", values = mapOf(producerId.id to "P1"))),
          ),
          survey(
            "s2",
            "o2",
            links = setOf(producerId.id),
            features = listOf(plot("b", "H", values = mapOf(producerId.id to "P2"))),
          ),
        ),
        listOf(producerId),
      )
    assertEquals(2.0, result.row(GLOBAL, "", DISTINCT_VALUES, producerId.id).value)
  }

  @Test
  fun distinctValues_customConfig() {
    val tree = concept("g.tree_id", ImpactAggregation.COUNT_DISTINCT_FEATURES)
    val result =
      runValues(
        tree,
        listOf(ImpactValue("T1"), ImpactValue("T1"), ImpactValue("T2")),
        ImpactConfig(distinctValueConceptIds = listOf("missing", tree.id)),
      )
    // Applies regardless of the aggregation rule, alongside the concept's own row.
    assertEquals(2.0, result.row(SURVEY, "s1", DISTINCT_VALUES, tree.id).value)
    assertFeatures(result.row(SURVEY, "s1", CONCEPT, tree.id), 3, 6.0)
  }
}
