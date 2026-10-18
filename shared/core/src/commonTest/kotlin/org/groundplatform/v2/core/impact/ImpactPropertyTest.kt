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

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.groundplatform.v2.core.geo.s2.s2CellToken
import org.groundplatform.v2.core.impact.ImpactScopeType.CELL
import org.groundplatform.v2.core.impact.ImpactScopeType.COUNTRY
import org.groundplatform.v2.core.impact.ImpactScopeType.GLOBAL
import org.groundplatform.v2.core.impact.ImpactScopeType.ORGANIZATION

/**
 * Property tests from docs/technical/backend/impact-aggregation.md ("Shared Logic and Testing"):
 * deduplication is idempotent, totals never decrease when suppression merges cells upward, and
 * opted-out organizations never appear in global outputs. Inputs are generated from fixed seeds so
 * failures reproduce.
 */
class ImpactPropertyTest {
  private val config =
    ImpactConfig(
      cellMinFeatures = 3,
      cellMinOrganizations = 2,
      fineCellLevel = 10,
      coarseCellLevel = 7,
      countryMinFeatures = 4,
    )

  private val globalConcepts =
    listOf(
      Fx.concept("g.count", ImpactAggregation.COUNT_DISTINCT_FEATURES),
      Fx.concept("g.code", ImpactAggregation.COUNT_BY_CODE),
      Fx.concept("g.share", ImpactAggregation.SHARE_BY_CODE, privacy = ImpactPrivacy.ORG_ONLY),
      Fx.concept("g.area", ImpactAggregation.SUM, unit = "har"),
      Fx.concept("g.mean", ImpactAggregation.MEAN, unit = "kg"),
      Fx.concept("g.sensitive", ImpactAggregation.SUM, privacy = ImpactPrivacy.SENSITIVE),
      Fx.concept(
        "core.producer_id",
        ImpactAggregation.NONE,
        privacy = ImpactPrivacy.AGGREGATE_PUBLIC,
      ),
    )

  /** A plot: one real-world feature that several surveys may map (same GeoID and geometry). */
  private class Plot(val geoId: String, val geometry: ImpactGeometry)

  private fun randomValue(random: Random, concept: ImpactConcept): String =
    when (concept.aggregation) {
      ImpactAggregation.COUNT_BY_CODE,
      ImpactAggregation.SHARE_BY_CODE ->
        List(random.nextInt(0, 3)) { "c${random.nextInt(4)}" }.joinToString(" ")
      ImpactAggregation.SUM,
      ImpactAggregation.MEAN ->
        if (random.nextInt(10) == 0) "n/a" else random.nextInt(0, 100).toString()
      else -> "v${random.nextInt(6)}"
    }

  private fun generate(seed: Int): ImpactInput {
    val random = Random(seed)
    val organizations =
      List(random.nextInt(1, 5)) {
        ImpactOrganization(
          id = "o$it",
          countryCode = listOf("KE", "UG", null)[random.nextInt(3)],
          excludeFromPlatformAggregates = random.nextInt(4) == 0,
        )
      }
    val organizationConcepts = organizations.map {
      Fx.concept(
        "org.${it.id}.trees",
        ImpactAggregation.SUM,
        owner = it.id,
        goals = if (random.nextBoolean()) listOf("ecosystem_restoration") else emptyList(),
      )
    }
    val plots =
      List(random.nextInt(5, 40)) {
        val lat = random.nextDouble(-0.2, 0.2)
        val lng = random.nextDouble(36.8, 37.2)
        val geometry =
          if (random.nextBoolean()) Fx.point(lat, lng)
          else Fx.square(lat, lng, random.nextDouble(0.0005, 0.005))
        Plot("geo-$it", geometry)
      }
    var tabular = 0
    val surveys =
      List(random.nextInt(1, 8)) { s ->
        val organizationId =
          if (random.nextInt(5) == 0) null else organizations[random.nextInt(organizations.size)].id
        val available =
          globalConcepts + organizationConcepts.filter { it.ownerOrganizationId == organizationId }
        val links =
          available.filter { random.nextInt(3) != 0 }.map { it.id }.toSet() +
            setOf("missing").filter { random.nextInt(4) == 0 }
        val features =
          List(random.nextInt(0, 16)) {
            val values =
              available
                .filter { random.nextBoolean() }
                .associate { it.id to ImpactValue(randomValue(random, it)) }
            if (random.nextInt(5) == 0) {
              // A tabular row or unlocated feature without a GeoID.
              tabular++
              ImpactFeature(
                entityId = "t$tabular",
                updatedAtMillis = random.nextLong(0, 4),
                geometry =
                  if (random.nextBoolean())
                    Fx.point(random.nextDouble(-0.2, 0.2), random.nextDouble(36.8, 37.2))
                  else null,
                areaHa = random.nextDouble(0.0, 3.0),
                countryCode = listOf("TZ", null)[random.nextInt(2)],
                values = values,
              )
            } else {
              val plot = plots[random.nextInt(plots.size)]
              ImpactFeature(
                entityId = "e${random.nextInt(20)}",
                geoId = plot.geoId,
                updatedAtMillis = random.nextLong(0, 4),
                geometry = plot.geometry,
                values = values,
              )
            }
          }
        Fx.survey("s$s", organizationId, links = links, features = features)
      }
    return ImpactInput(surveys, globalConcepts + organizationConcepts, organizations)
  }

  private fun ImpactRunResult.featureRows(vararg scopes: ImpactScopeType) = rows.filter {
    it.scopeType in scopes && it.metric == ImpactMetric.FEATURES
  }

  @Test
  fun dedup_isIdempotentAndDeterministic() {
    repeat(ITERATIONS) { seed ->
      val input = generate(seed)
      val result = ImpactAggregator.run(input, config)
      assertEquals(result, ImpactAggregator.run(input, config), "seed $seed: rerun")
      assertEquals(
        result,
        ImpactAggregator.run(input.copy(surveys = input.surveys.reversed()), config),
        "seed $seed: order",
      )

      // Copy every GeoID feature into another survey of the same organization.
      val copies =
        input.surveys.map { survey ->
          survey.copy(
            id = "${survey.id}-copy",
            features = survey.features.filter { !it.geoId.isNullOrBlank() },
            events = emptyList(),
          )
        }
      val duplicated = ImpactAggregator.run(input.copy(surveys = input.surveys + copies), config)
      assertEquals(
        result.featureRows(ORGANIZATION, GLOBAL, COUNTRY, CELL),
        duplicated.featureRows(ORGANIZATION, GLOBAL, COUNTRY, CELL),
        "seed $seed: duplicates",
      )
    }
  }

  @Test
  fun cellMerging_neverLosesFeatures() {
    repeat(ITERATIONS) { seed ->
      val input = generate(seed)
      val result = ImpactAggregator.run(input, config)
      val optedOut =
        input.organizations.filter { it.excludeFromPlatformAggregates }.map { it.id }.toSet()

      // Distinct located plots of eligible surveys, computed independently of the aggregator: all
      // duplicates of a GeoID share one geometry, and GeoID-less features are unique.
      val points = HashMap<String, ImpactLatLng>()
      for (survey in input.surveys.filter { it.organizationId !in optedOut }) {
        for (feature in survey.features) {
          val point = representativePoint(feature.geometry) ?: continue
          val key = feature.geoId ?: "${survey.id}/${feature.entityId}"
          points[key] = point
        }
      }
      val cells = result.rows.filter { it.scopeType == CELL }
      assertEquals(
        points.size.toLong(),
        cells.sumOf { it.featureCount } + result.diagnostics.suppressedCellFeatureCount,
        "seed $seed: conservation",
      )
      assertEquals(cells.count { it.suppressed }, result.diagnostics.suppressedCellCount)

      val fineCounts =
        points.values.groupingBy { s2CellToken(it.lat, it.lng, config.fineCellLevel) }.eachCount()
      val coarseOf =
        points.values.associate {
          s2CellToken(it.lat, it.lng, config.fineCellLevel) to
            s2CellToken(it.lat, it.lng, config.coarseCellLevel)
        }
      val published = cells.filter { !it.suppressed }
      for (row in published) assertTrue(
        row.featureCount >= config.cellMinFeatures,
        "seed $seed: $row",
      )
      val publishedFine = published.map { it.scopeId }.filter { it in fineCounts }.toSet()
      for (row in published.filter { it.scopeId !in fineCounts }) {
        val merged = fineCounts.filterKeys {
          it !in publishedFine && coarseOf.getValue(it) == row.scopeId
        }
        assertTrue(merged.isNotEmpty(), "seed $seed: $row merges nothing")
        assertEquals(merged.values.sum().toLong(), row.featureCount, "seed $seed: $row")
        for (count in merged.values) assertTrue(row.featureCount >= count)
      }
    }
  }

  @Test
  fun optedOutOrganizations_neverAppearInGlobalOutputs() {
    repeat(ITERATIONS) { seed ->
      val input = generate(seed)
      val optedOut =
        input.organizations.filter { it.excludeFromPlatformAggregates }.map { it.id }.toSet()
      val result = ImpactAggregator.run(input, config)
      val without =
        ImpactAggregator.run(
          input.copy(surveys = input.surveys.filter { it.organizationId !in optedOut }),
          config,
        )
      fun platform(r: ImpactRunResult) =
        r.rows.filter { it.scopeType in setOf(GLOBAL, COUNTRY, CELL) }
      assertEquals(platform(without), platform(result), "seed $seed")
      assertEquals(without.diagnostics.suppressedCellCount, result.diagnostics.suppressedCellCount)
      assertEquals(
        without.diagnostics.suppressedCellFeatureCount,
        result.diagnostics.suppressedCellFeatureCount,
      )
      assertEquals(
        without.diagnostics.suppressedCountryCount,
        result.diagnostics.suppressedCountryCount,
      )
      assertTrue(
        result.rows.none { it.organizationSuggested && it.organizationId in optedOut },
        "seed $seed",
      )
      assertTrue(
        platform(result).none {
          it.conceptId == "g.sensitive" ||
            it.conceptId.startsWith("org.") && !it.organizationSuggested
        }
      )
    }
  }

  private companion object {
    const val ITERATIONS = 200
  }
}
