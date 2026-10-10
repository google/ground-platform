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
import org.groundplatform.v2.core.geo.s2.s2CellToken
import org.groundplatform.v2.core.impact.Fx.concept
import org.groundplatform.v2.core.impact.Fx.feature
import org.groundplatform.v2.core.impact.Fx.square

/**
 * Golden fixture mirroring the prototype's seeded surveys (docs/technical/backend/
 * impact-aggregation.md, "Shared Logic and Testing"): a Kenyan coffee cooperative's EUDR survey
 * with three polygon parcels, one of which a buyer's audit survey maps again later; an organization
 * concept with the suggested goal `ecosystem_restoration`; and an organization that opted out of
 * platform-wide aggregates.
 */
class ImpactGoldenTest {
  private val commodity = concept("eudr.commodity", ImpactAggregation.COUNT_BY_CODE)
  private val areaHa = concept("core.area_ha", ImpactAggregation.SUM, unit = "har")
  private val producerId =
    concept("core.producer_id", ImpactAggregation.NONE, privacy = ImpactPrivacy.SENSITIVE)
  private val shadeTrees =
    concept(
      "org.kenya-coffee.shade_tree_count",
      ImpactAggregation.SUM,
      owner = "kenya-coffee",
      privacy = ImpactPrivacy.ORG_ONLY,
      goals = listOf("ecosystem_restoration"),
    )
  private val eudr =
    ImpactPurpose("eudr", isGlobal = true, exportProfileIds = setOf("eudr-geojson"))

  private fun parcel(
    id: String,
    geoId: String,
    lat: Double,
    lng: Double,
    producer: String,
    trees: String,
    updatedAt: Long = 100,
  ) =
    feature(
      id,
      geoId,
      updatedAt,
      geometry = square(lat, lng, 0.001),
      values =
        mapOf(
          commodity.id to "coffee",
          producerId.id to producer,
          shadeTrees.id to trees,
          areaHa.id to "1.2",
        ),
    )

  private val input =
    ImpactInput(
      surveys =
        listOf(
          Fx.survey(
            "kenya-eudr",
            "kenya-coffee",
            purposes = listOf(eudr),
            links = setOf(commodity.id, areaHa.id, producerId.id, shadeTrees.id),
            features =
              listOf(
                parcel("p1", "geo-1", -0.4200, 36.9500, "KE-001", "12"),
                parcel("p2", "geo-2", -0.4210, 36.9520, "KE-002", "8"),
                parcel("p3", "geo-3", -0.4230, 36.9540, "ke-001", "20"),
              ),
            events =
              listOf(
                ImpactEventRecord(ImpactEventKind.EXPORT, 3, 3.69, "eudr-geojson"),
                ImpactEventRecord(ImpactEventKind.SURVEY_CLOSED, 3, 3.69),
              ),
            isClosed = true,
            outcome = setOf(ImpactOutcomeKind.SUBMITTED_EUDR_DDS),
          ),
          // The buyer re-maps parcel geo-2 later and records cocoa intercropping.
          Fx.survey(
            "buyer-audit",
            "coffee-buyer",
            links = setOf(commodity.id),
            features =
              listOf(
                feature(
                  "b1",
                  "geo-2",
                  200,
                  geometry = square(-0.4210, 36.9520, 0.001),
                  values = mapOf(commodity.id to "coffee cocoa"),
                )
              ),
            events = listOf(ImpactEventRecord(ImpactEventKind.PARTNER_PUSH, 1, 1.23)),
          ),
          Fx.survey(
            "private-monitoring",
            "private-estate",
            purposes = listOf(eudr),
            links = setOf(commodity.id),
            features =
              listOf(
                feature(
                  "x1",
                  "geo-9",
                  geometry = Fx.point(-0.43, 36.96),
                  areaHa = 5.0,
                  values = mapOf(commodity.id to "tea"),
                ),
                feature(
                  "x2",
                  "geo-1",
                  300,
                  geometry = square(-0.4200, 36.9500, 0.001),
                  values = mapOf(commodity.id to "tea"),
                ),
              ),
            isClosed = true,
          ),
        ),
      concepts = listOf(commodity, areaHa, producerId, shadeTrees),
      organizations =
        listOf(
          ImpactOrganization("kenya-coffee", countryCode = "KE"),
          ImpactOrganization("coffee-buyer", countryCode = "GB"),
          ImpactOrganization(
            "private-estate",
            countryCode = "KE",
            excludeFromPlatformAggregates = true,
          ),
        ),
    )

  private val config =
    ImpactConfig(cellMinFeatures = 3, cellMinOrganizations = 2, countryMinFeatures = 2)

  @Test
  fun goldenRows() {
    val result = ImpactAggregator.run(input, config)
    assertEquals(EXPECTED.trim(), render(result.rows))
    assertEquals(
      ImpactDiagnostics(suppressedCountryCount = 1),
      result.diagnostics,
    )
    // The three platform-eligible parcels fall in different fine (level 10) cells, each below the
    // threshold, and merge into one published coarse (level 7) cell.
    val fineCells =
      listOf(-0.4195 to 36.9505, -0.4205 to 36.9525, -0.4225 to 36.9545).map { (lat, lng) ->
        s2CellToken(lat, lng, config.fineCellLevel)
      }
    assertEquals(3, fineCells.toSet().size)
    assertEquals("18284", s2CellToken(-0.4215, 36.9525, config.coarseCellLevel))
    assertEquals(
      mapOf(
        "buyer-audit" to AttributionBasis.PARTNER_USE,
        "kenya-eudr" to AttributionBasis.OFFICIAL_SUBMISSION,
        "private-monitoring" to AttributionBasis.DATA_COLLECTED,
      ),
      result.attributions.mapValues { it.value.basis },
    )
  }

  private companion object {
    const val EXPECTED =
      """
SURVEY 'buyer-audit' FEATURES n=1 ha=1.23 v=0.0000
SURVEY 'buyer-audit' CONCEPT concept=eudr.commodity code=cocoa n=1 ha=1.23 v=0.0000
SURVEY 'buyer-audit' CONCEPT concept=eudr.commodity code=coffee n=1 ha=1.23 v=0.0000
SURVEY 'buyer-audit' EVENT event=PARTNER_PUSH n=1 ha=1.23 v=1.0000
SURVEY 'buyer-audit' ATTRIBUTION score=4 n=0 ha=0.00 v=1.0000
SURVEY 'kenya-eudr' FEATURES n=3 ha=3.69 v=0.0000
SURVEY 'kenya-eudr' CONCEPT concept=core.area_ha n=3 ha=3.69 v=3.6000
SURVEY 'kenya-eudr' CONCEPT concept=eudr.commodity code=coffee n=3 ha=3.69 v=0.0000
SURVEY 'kenya-eudr' CONCEPT concept=org.kenya-coffee.shade_tree_count n=3 ha=3.69 v=40.0000
SURVEY 'kenya-eudr' DISTINCT_VALUES concept=core.producer_id n=3 ha=3.69 v=2.0000
SURVEY 'kenya-eudr' EVENT event=EXPORT n=3 ha=3.69 v=1.0000
SURVEY 'kenya-eudr' EVENT event=SURVEY_CLOSED n=3 ha=3.69 v=1.0000
SURVEY 'kenya-eudr' OUTCOME outcome=SUBMITTED_EUDR_DDS n=0 ha=0.00 v=1.0000
SURVEY 'kenya-eudr' ATTRIBUTION score=5 n=0 ha=0.00 v=1.0000
SURVEY 'private-monitoring' FEATURES n=2 ha=6.23 v=0.0000
SURVEY 'private-monitoring' CONCEPT concept=eudr.commodity code=tea n=2 ha=6.23 v=0.0000
SURVEY 'private-monitoring' OUTCOME outcome=UNANSWERED n=0 ha=0.00 v=1.0000
SURVEY 'private-monitoring' ATTRIBUTION score=2 n=0 ha=0.00 v=1.0000
ORGANIZATION 'coffee-buyer' FEATURES n=1 ha=1.23 v=0.0000
ORGANIZATION 'coffee-buyer' CONCEPT concept=eudr.commodity code=cocoa n=1 ha=1.23 v=0.0000
ORGANIZATION 'coffee-buyer' CONCEPT concept=eudr.commodity code=coffee n=1 ha=1.23 v=0.0000
ORGANIZATION 'coffee-buyer' EVENT event=PARTNER_PUSH n=1 ha=1.23 v=1.0000
ORGANIZATION 'coffee-buyer' ATTRIBUTION score=4 n=0 ha=0.00 v=1.0000
ORGANIZATION 'kenya-coffee' FEATURES n=3 ha=3.69 v=0.0000
ORGANIZATION 'kenya-coffee' CONCEPT concept=core.area_ha n=3 ha=3.69 v=3.6000
ORGANIZATION 'kenya-coffee' CONCEPT concept=eudr.commodity code=coffee n=3 ha=3.69 v=0.0000
ORGANIZATION 'kenya-coffee' CONCEPT concept=org.kenya-coffee.shade_tree_count n=3 ha=3.69 v=40.0000
ORGANIZATION 'kenya-coffee' DISTINCT_VALUES concept=core.producer_id n=3 ha=3.69 v=2.0000
ORGANIZATION 'kenya-coffee' EVENT event=EXPORT n=3 ha=3.69 v=1.0000
ORGANIZATION 'kenya-coffee' EVENT event=SURVEY_CLOSED n=3 ha=3.69 v=1.0000
ORGANIZATION 'kenya-coffee' OUTCOME outcome=SUBMITTED_EUDR_DDS n=0 ha=0.00 v=1.0000
ORGANIZATION 'kenya-coffee' ATTRIBUTION score=5 n=0 ha=0.00 v=1.0000
ORGANIZATION 'private-estate' FEATURES n=2 ha=6.23 v=0.0000
ORGANIZATION 'private-estate' CONCEPT concept=eudr.commodity code=tea n=2 ha=6.23 v=0.0000
ORGANIZATION 'private-estate' OUTCOME outcome=UNANSWERED n=0 ha=0.00 v=1.0000
ORGANIZATION 'private-estate' ATTRIBUTION score=2 n=0 ha=0.00 v=1.0000
COUNTRY 'GB' FEATURES n=0 ha=0.00 v=0.0000 SUPPRESSED
COUNTRY 'KE' FEATURES n=2 ha=2.46 v=0.0000
COUNTRY 'KE' CONCEPT concept=core.area_ha n=2 ha=2.46 v=2.4000
COUNTRY 'KE' CONCEPT concept=eudr.commodity code=coffee n=2 ha=2.46 v=0.0000
GLOBAL '' FEATURES n=3 ha=3.69 v=0.0000
GLOBAL '' CONCEPT concept=core.area_ha n=2 ha=2.46 v=2.4000
GLOBAL '' CONCEPT concept=eudr.commodity code=cocoa n=1 ha=1.23 v=0.0000
GLOBAL '' CONCEPT concept=eudr.commodity code=coffee n=3 ha=3.69 v=0.0000
GLOBAL '' CONCEPT concept=org.kenya-coffee.shade_tree_count suggested org=kenya-coffee goal=ecosystem_restoration n=3 ha=3.69 v=40.0000
GLOBAL '' EVENT event=EXPORT n=3 ha=3.69 v=1.0000
GLOBAL '' EVENT event=PARTNER_PUSH n=1 ha=1.23 v=1.0000
GLOBAL '' EVENT event=SURVEY_CLOSED n=3 ha=3.69 v=1.0000
GLOBAL '' OUTCOME outcome=SUBMITTED_EUDR_DDS n=0 ha=0.00 v=1.0000
GLOBAL '' ATTRIBUTION score=5 n=0 ha=0.00 v=1.0000
CELL '18284' FEATURES n=3 ha=3.69 v=0.0000
"""
  }
}
