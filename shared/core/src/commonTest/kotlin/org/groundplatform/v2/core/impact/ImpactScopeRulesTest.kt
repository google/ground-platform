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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.groundplatform.v2.core.geo.s2.s2CellToken
import org.groundplatform.v2.core.impact.Fx.concept
import org.groundplatform.v2.core.impact.Fx.feature
import org.groundplatform.v2.core.impact.Fx.plot
import org.groundplatform.v2.core.impact.Fx.run
import org.groundplatform.v2.core.impact.Fx.survey
import org.groundplatform.v2.core.impact.ImpactMetric.CONCEPT
import org.groundplatform.v2.core.impact.ImpactMetric.FEATURES
import org.groundplatform.v2.core.impact.ImpactScopeType.CELL
import org.groundplatform.v2.core.impact.ImpactScopeType.COUNTRY
import org.groundplatform.v2.core.impact.ImpactScopeType.GLOBAL
import org.groundplatform.v2.core.impact.ImpactScopeType.ORGANIZATION
import org.groundplatform.v2.core.impact.ImpactScopeType.SURVEY

class ImpactScopeRulesTest {
  private val public = concept("g.public", ImpactAggregation.COUNT_DISTINCT_FEATURES)
  private val orgOnly =
    concept(
      "g.org_only",
      ImpactAggregation.COUNT_DISTINCT_FEATURES,
      privacy = ImpactPrivacy.ORG_ONLY,
    )
  private val sensitive =
    concept(
      "g.sensitive",
      ImpactAggregation.COUNT_DISTINCT_FEATURES,
      privacy = ImpactPrivacy.SENSITIVE,
    )
  private val noGoal = concept("org.o1.no_goal", ImpactAggregation.SUM, owner = "o1")
  private val withGoals =
    concept(
      "org.o1.trees",
      ImpactAggregation.SUM,
      owner = "o1",
      goals = listOf("ecosystem_restoration", "biodiversity"),
    )
  private val sensitiveWithGoal =
    concept(
      "org.o1.patrols",
      ImpactAggregation.SUM,
      owner = "o1",
      privacy = ImpactPrivacy.SENSITIVE,
      goals = listOf("protection"),
    )
  private val optedOutWithGoal =
    concept(
      "org.o3.trees",
      ImpactAggregation.SUM,
      owner = "o3",
      goals = listOf("ecosystem_restoration"),
    )
  private val concepts =
    listOf(public, orgOnly, sensitive, noGoal, withGoals, sensitiveWithGoal, optedOutWithGoal)

  private val organizations =
    listOf(
      ImpactOrganization("o1", countryCode = "KE"),
      ImpactOrganization("o2", countryCode = "ke"),
      ImpactOrganization("o3", countryCode = "KE", excludeFromPlatformAggregates = true),
    )

  private fun values(vararg concepts: ImpactConcept, value: String = "1") = concepts.associate {
    it.id to value
  }

  private fun input(): List<ImpactSurvey> {
    val o1Links =
      setOf(public.id, orgOnly.id, sensitive.id, noGoal.id, withGoals.id, sensitiveWithGoal.id)
    val o1Values =
      values(public, orgOnly, sensitive, noGoal, withGoals, sensitiveWithGoal, value = "5")
    return listOf(
      survey(
        "s1",
        "o1",
        links = o1Links,
        features = listOf(plot("a", "G1", values = o1Values), plot("b", "G2", values = o1Values)),
      ),
      // Same organization, a duplicate of G1 and a new plot: deduplicated within o1.
      survey(
        "s2",
        "o1",
        links = o1Links,
        features = listOf(plot("c", "G1", values = o1Values), plot("d", "G3", values = o1Values)),
      ),
      survey(
        "s3",
        "o2",
        links = setOf(public.id),
        features =
          listOf(
            plot("e", "G1", values = values(public)),
            plot("f", "G4", values = values(public)),
          ),
      ),
      survey(
        "s4",
        "o3",
        links = setOf(public.id, optedOutWithGoal.id),
        features =
          listOf(
            plot("g", "G5", values = values(public, optedOutWithGoal)),
            plot("h", "G6", values = values(public, optedOutWithGoal)),
          ),
      ),
      survey(
        "s5",
        null,
        links = setOf(public.id),
        features = listOf(plot("i", values = values(public))),
      ),
    )
  }

  private fun result(config: ImpactConfig = ImpactConfig(countryMinFeatures = 1)) =
    run(input(), concepts, organizations, config)

  @Test
  fun surveyAndOrganizationScopes_includeEveryResolvedConcept() {
    val result = result()
    for (concept in listOf(public, orgOnly, sensitive, noGoal, withGoals, sensitiveWithGoal)) {
      assertTrue(result.hasRow(SURVEY, "s1", CONCEPT, concept.id), concept.id)
      assertTrue(result.hasRow(ORGANIZATION, "o1", CONCEPT, concept.id), concept.id)
    }
    assertFeatures(result.row(ORGANIZATION, "o1", FEATURES), 3, 3.0)
    assertNear(15.0, result.row(ORGANIZATION, "o1", CONCEPT, withGoals.id).value)
  }

  @Test
  fun optedOutOrganization_keepsItsOwnScopes() {
    val result = result()
    assertFeatures(result.row(SURVEY, "s4", FEATURES), 2, 2.0)
    assertFeatures(result.row(ORGANIZATION, "o3", FEATURES), 2, 2.0)
    assertTrue(result.hasRow(ORGANIZATION, "o3", CONCEPT, optedOutWithGoal.id))
  }

  @Test
  fun personalSurveys_haveNoOrganizationScope_butCountGlobally() {
    val result = result()
    assertTrue(result.rows.none { it.scopeType == ORGANIZATION && it.scopeId.isEmpty() })
    assertEquals(
      setOf("o1", "o2", "o3"),
      result.rows.filter { it.scopeType == ORGANIZATION }.map { it.scopeId }.toSet(),
    )
    // G1, G2, G3 (o1), G4 (o2; G1 duplicate counted once), and the personal plot; o3 excluded.
    assertFeatures(result.row(GLOBAL, "", FEATURES), 5, 5.0)
  }

  @Test
  fun globalScope_mainRowsOnlyForNonSensitiveGlobalConcepts() {
    val result = result()
    val main =
      result
        .rows(GLOBAL)
        .filter { it.metric == CONCEPT && !it.organizationSuggested }
        .map { it.conceptId }
    assertEquals(listOf(orgOnly.id, public.id), main)
    // g.public over G1..G4 and the personal plot (G1's winner by entity ID is `a`, which has it).
    assertFeatures(result.row(GLOBAL, "", CONCEPT, public.id), 5, 5.0)
  }

  @Test
  fun globalScope_organizationSuggestedRowsPerGoal() {
    val result = result()
    val suggested = result.rows(GLOBAL).filter { it.organizationSuggested }
    assertEquals(
      listOf(withGoals.id to "ecosystem_restoration", withGoals.id to "biodiversity"),
      suggested.map { it.conceptId to it.goal },
    )
    for (row in suggested) {
      assertEquals("o1", row.organizationId)
      // Over o1's own deduplicated plots G1, G2, G3.
      assertNear(15.0, row.value)
      assertFeatures(row, 3, 3.0)
    }
    // Never in main totals.
    assertFalse(
      result.rows(GLOBAL).any { it.conceptId == withGoals.id && !it.organizationSuggested }
    )
    // Organization concepts without goals, SENSITIVE ones, and opted-out organizations' are
    // excluded.
    for (excluded in listOf(noGoal, sensitiveWithGoal, optedOutWithGoal)) {
      assertFalse(result.rows(GLOBAL).any { it.conceptId == excluded.id }, excluded.id)
    }
  }

  @Test
  fun globalScope_suggestedRowsFollowMainRows() {
    val metrics =
      result().rows(GLOBAL).map { if (it.organizationSuggested) "SUGGESTED" else it.metric.name }
    val lastMain = metrics.lastIndexOf("CONCEPT")
    val firstSuggested = metrics.indexOf("SUGGESTED")
    assertTrue(firstSuggested > lastMain, metrics.toString())
  }

  @Test
  fun countryScope_platformEligibleFeaturesByCountry() {
    val result = result()
    // o1 and o2 are in Kenya (o2's code normalized to upper case); o3 is opted out; the personal
    // plot has no country.
    assertEquals(
      listOf("KE"),
      result.rows.filter { it.scopeType == COUNTRY }.map { it.scopeId }.distinct(),
    )
    assertFeatures(result.row(COUNTRY, "KE", FEATURES), 4, 4.0)
    assertTrue(result.hasRow(COUNTRY, "KE", CONCEPT, public.id))
    assertFalse(result.hasRow(COUNTRY, "KE", CONCEPT, sensitive.id))
    assertFalse(result.hasRow(COUNTRY, "KE", CONCEPT, withGoals.id))
    assertTrue(result.rows(COUNTRY, "KE").none { it.organizationSuggested })
    assertTrue(
      result.rows(COUNTRY, "KE").all {
        it.metric == FEATURES || it.metric == CONCEPT || it.metric == ImpactMetric.DISTINCT_VALUES
      }
    )
  }

  @Test
  fun countryScope_featureCountryOverridesOrganization_andSmallCountriesAreSuppressed() {
    val result =
      run(
        listOf(
          survey(
            "s1",
            "o1",
            links = setOf(public.id),
            features =
              listOf(
                plot("a", "G1", values = values(public)),
                plot("b", "G2", values = values(public)),
                feature("c", "G3", areaHa = 7.0, country = " tz ", values = values(public)),
              ),
          ),
          survey("s2", "o9", features = listOf(plot("d", "G4"))),
        ),
        concepts,
        organizations,
        ImpactConfig(countryMinFeatures = 2),
      )
    assertFeatures(result.row(COUNTRY, "KE", FEATURES), 2, 2.0)
    assertEquals(
      listOf(ImpactAggregateRow(COUNTRY, "TZ", FEATURES, suppressed = true)),
      result.rows(COUNTRY, "TZ"),
    )
    assertEquals(1, result.diagnostics.suppressedCountryCount)
    // Unknown organization o9 has no country: its plot counts globally but in no country.
    assertFeatures(result.row(GLOBAL, "", FEATURES), 4, 10.0)
  }

  @Test
  fun unknownOrganization_isNotOptedOut() {
    val result =
      run(
        listOf(survey("s1", "missing-org", features = listOf(plot("a")))),
        concepts,
        organizations,
      )
    assertFeatures(result.row(GLOBAL, "", FEATURES), 1, 1.0)
    assertFeatures(result.row(ORGANIZATION, "missing-org", FEATURES), 1, 1.0)
  }

  // Grid cells.

  private val cellConfig =
    ImpactConfig(
      cellMinFeatures = 2,
      cellMinOrganizations = 2,
      fineCellLevel = 10,
      coarseCellLevel = 7,
    )
  private val base = ImpactLatLng(-0.42, 36.95)

  private fun at(id: String, lat: Double, lng: Double, geoId: String = id) =
    feature(id, geoId, geometry = Fx.point(lat, lng), areaHa = 1.0)

  /** A longitude east of [base] in a different fine cell but the same coarse cell. */
  private fun neighborLng(): Double {
    val fine = s2CellToken(base.lat, base.lng, 10)
    val coarse = s2CellToken(base.lat, base.lng, 7)
    for (step in 1..200) {
      val lng = base.lng + step * 0.005
      if (s2CellToken(base.lat, lng, 10) != fine && s2CellToken(base.lat, lng, 7) == coarse)
        return lng
    }
    error("no neighbor")
  }

  @Test
  fun cells_publishFineCellMeetingBothThresholds() {
    val result =
      run(
        listOf(
          survey("s1", "o1", features = listOf(at("a", base.lat, base.lng))),
          survey("s2", "o2", features = listOf(at("b", base.lat + 0.0001, base.lng + 0.0001))),
        ),
        config = cellConfig,
      )
    val token = s2CellToken(base.lat, base.lng, 10)
    assertEquals(
      listOf(ImpactAggregateRow(CELL, token, FEATURES, featureCount = 2, areaHa = 2.0)),
      result.rows.filter { it.scopeType == CELL },
    )
    assertEquals(0, result.diagnostics.suppressedCellCount)
  }

  @Test
  fun cells_unpublishedFineCellsMergeIntoCoarseParent() {
    val lng = neighborLng()
    val result =
      run(
        listOf(
          survey("s1", "o1", features = listOf(at("a", base.lat, base.lng))),
          survey("s2", "o2", features = listOf(at("b", base.lat, lng))),
        ),
        config = cellConfig,
      )
    val coarse = s2CellToken(base.lat, base.lng, 7)
    assertNotEquals(s2CellToken(base.lat, base.lng, 10), s2CellToken(base.lat, lng, 10))
    assertEquals(
      listOf(ImpactAggregateRow(CELL, coarse, FEATURES, featureCount = 2, areaHa = 2.0)),
      result.rows.filter { it.scopeType == CELL },
    )
  }

  @Test
  fun cells_suppressedWhenCoarseCellStillBelowThreshold() {
    val lng = neighborLng()
    val result =
      run(
        listOf(
          // Same fine cell but one organization: not published, and the coarse cell has one
          // organization too.
          survey(
            "s1",
            "o1",
            features =
              listOf(
                at("a", base.lat, base.lng),
                at("b", base.lat + 0.0001, base.lng),
                at("c", base.lat, lng),
              ),
          ),
          // Far away: a lone feature.
          survey("s2", "o2", features = listOf(at("d", 10.0, 10.0))),
        ),
        config = cellConfig,
      )
    val cells = result.rows.filter { it.scopeType == CELL }
    assertEquals(
      listOf(s2CellToken(base.lat, base.lng, 7), s2CellToken(10.0, 10.0, 7)).sorted(),
      cells.map { it.scopeId },
    )
    assertTrue(cells.all { it.suppressed && it.featureCount == 0L && it.areaHa == 0.0 })
    assertEquals(2, result.diagnostics.suppressedCellCount)
    assertEquals(4L, result.diagnostics.suppressedCellFeatureCount)
  }

  @Test
  fun cells_excludeOptedOutOrganizationsAndFeaturesWithoutGeometry() {
    val result =
      run(
        listOf(
          survey(
            "s1",
            "o1",
            features = listOf(at("a", base.lat, base.lng), plot("no-geometry", "G9")),
          ),
          survey("s3", "o3", features = listOf(at("b", base.lat, base.lng + 0.0001))),
        ),
        organizations = organizations,
        config = cellConfig,
      )
    // Only `a` is cell-eligible: suppressed, and the opted-out organization doesn't count toward
    // the organization threshold.
    assertEquals(1L, result.diagnostics.suppressedCellFeatureCount)
    assertEquals(1, result.rows.count { it.scopeType == CELL })
  }

  @Test
  fun cells_personalSurveysShareOneOrganizationKey() {
    val result =
      run(
        listOf(
          survey("s1", null, features = listOf(at("a", base.lat, base.lng))),
          survey("s2", null, features = listOf(at("b", base.lat + 0.0001, base.lng))),
        ),
        config = cellConfig,
      )
    assertTrue(result.rows.filter { it.scopeType == CELL }.all { it.suppressed })
  }
}
