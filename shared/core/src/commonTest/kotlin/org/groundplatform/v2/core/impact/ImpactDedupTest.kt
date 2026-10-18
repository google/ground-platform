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
import org.groundplatform.v2.core.impact.Fx.feature
import org.groundplatform.v2.core.impact.Fx.plot
import org.groundplatform.v2.core.impact.Fx.ring
import org.groundplatform.v2.core.impact.Fx.run
import org.groundplatform.v2.core.impact.Fx.survey
import org.groundplatform.v2.core.impact.ImpactMetric.CONCEPT
import org.groundplatform.v2.core.impact.ImpactMetric.FEATURES
import org.groundplatform.v2.core.impact.ImpactScopeType.GLOBAL
import org.groundplatform.v2.core.impact.ImpactScopeType.ORGANIZATION
import org.groundplatform.v2.core.impact.ImpactScopeType.SURVEY

class ImpactDedupTest {
  private val code = concept("g.code", ImpactAggregation.COUNT_BY_CODE)
  private val count = concept("g.count", ImpactAggregation.COUNT_DISTINCT_FEATURES)

  @Test
  fun mostRecentlyUpdatedDuplicateWins() {
    val result =
      run(
        listOf(
          survey(
            "s1",
            "o1",
            links = setOf(code.id),
            features =
              listOf(
                plot("a", "G", areaHa = 1.0, updatedAt = 1, values = mapOf(code.id to "old")),
                plot("b", "G", areaHa = 2.0, updatedAt = 2, values = mapOf(code.id to "new")),
              ),
          )
        ),
        listOf(code),
      )
    assertFeatures(result.row(SURVEY, "s1", FEATURES), 1, 2.0)
    assertEquals(
      listOf("new"),
      result.rows(SURVEY, "s1").filter { it.metric == CONCEPT }.map { it.code },
    )
  }

  @Test
  fun updatedAtTie_smallestEntityIdWins() {
    val result =
      run(
        listOf(
          survey(
            "s1",
            "o1",
            features =
              listOf(
                plot("b", "G", areaHa = 2.0, updatedAt = 5),
                plot("a", "G", areaHa = 1.0, updatedAt = 5),
              ),
          )
        )
      )
    assertFeatures(result.row(SURVEY, "s1", FEATURES), 1, 1.0)
  }

  @Test
  fun fullTie_smallestSurveyIdWins() {
    val result =
      run(
        listOf(
          survey("s2", "o1", features = listOf(plot("a", "G", areaHa = 2.0, updatedAt = 5))),
          survey("s1", "o1", features = listOf(plot("a", "G", areaHa = 1.0, updatedAt = 5))),
        )
      )
    assertFeatures(result.row(ORGANIZATION, "o1", FEATURES), 1, 1.0)
  }

  @Test
  fun geoIdIsTrimmed_andBlankGeoIdsCountIndividually() {
    val result =
      run(
        listOf(
          survey(
            "s1",
            "o1",
            features =
              listOf(
                plot("a", " G "),
                plot("b", "G"),
                plot("c", null),
                plot("d", ""),
                plot("e", "   "),
              ),
          )
        )
      )
    assertFeatures(result.row(SURVEY, "s1", FEATURES), 4, 4.0)
  }

  @Test
  fun geoIdLessFeaturesInDifferentSurveysNeverMerge() {
    val result =
      run(
        listOf(
          survey("s1", "o1", features = listOf(plot("a"))),
          survey("s2", "o1", features = listOf(plot("a"))),
        )
      )
    assertFeatures(result.row(ORGANIZATION, "o1", FEATURES), 2, 2.0)
  }

  @Test
  fun crossSurveyDuplicatesCountOnceInOrganization() {
    val result =
      run(
        listOf(
          survey("s1", "o1", features = listOf(plot("a", "G"), plot("b", "H"))),
          survey("s2", "o1", features = listOf(plot("c", "G"))),
        )
      )
    assertFeatures(result.row(SURVEY, "s1", FEATURES), 2, 2.0)
    assertFeatures(result.row(SURVEY, "s2", FEATURES), 1, 1.0)
    assertFeatures(result.row(ORGANIZATION, "o1", FEATURES), 2, 2.0)
  }

  @Test
  fun crossOrganizationDuplicates_countOncePerOrganizationAndOnceGlobally() {
    val result =
      run(
        listOf(
          survey("s1", "o1", features = listOf(plot("a", "G"), plot("b", "H"))),
          survey("s2", "o2", features = listOf(plot("c", "G"))),
        )
      )
    assertFeatures(result.row(ORGANIZATION, "o1", FEATURES), 2, 2.0)
    assertFeatures(result.row(ORGANIZATION, "o2", FEATURES), 1, 1.0)
    assertFeatures(result.row(GLOBAL, "", FEATURES), 2, 2.0)
  }

  @Test
  fun winnerProvidesValues_withoutMergingFromLosers() {
    // The newer duplicate's survey doesn't link the concept, so the plot has no value globally.
    val result =
      run(
        listOf(
          survey(
            "s1",
            "o1",
            links = setOf(count.id),
            features = listOf(plot("a", "G", updatedAt = 1, values = mapOf(count.id to "x"))),
          ),
          survey(
            "s2",
            "o2",
            features = listOf(plot("b", "G", updatedAt = 2, values = mapOf(count.id to "y"))),
          ),
        ),
        listOf(count),
      )
    assertFeatures(result.row(GLOBAL, "", FEATURES), 1, 1.0)
    assertFeatures(result.row(GLOBAL, "", CONCEPT, count.id), 0, 0.0)
    assertFeatures(result.row(SURVEY, "s1", CONCEPT, count.id), 1, 1.0)
  }

  @Test
  fun area_polygonWithHole() {
    // Near the equator a 0.01° square is ~123.08 ha (1° × 1° is 1,230,846 ha), so a 0.02° square
    // with a 0.01° hole is ~369.25 ha.
    val polygon =
      ImpactGeometry.Polygon(listOf(ring(0.0, 0.0, 0.02), ring(0.005, 0.005, 0.01).reversed()))
    assertNear(369.25, geodesicAreaHa(polygon), 369.25 * 0.002)
    assertNear(492.34, geodesicAreaHa(Fx.square(0.0, 0.0, 0.02)), 492.34 * 0.002)
    val result =
      run(listOf(survey("s1", null, features = listOf(feature("a", geometry = polygon)))))
    assertNear(geodesicAreaHa(polygon), result.row(SURVEY, "s1", FEATURES).areaHa)
  }

  @Test
  fun area_openAndClosedRingsMatch() {
    val closed = ring(-0.42, 36.95, 0.001)
    assertNear(
      geodesicAreaHa(ImpactGeometry.Polygon(listOf(closed))),
      geodesicAreaHa(ImpactGeometry.Polygon(listOf(closed.dropLast(1)))),
      1e-9,
    )
  }

  @Test
  fun area_fallbacks() {
    val square = Fx.square(0.0, 0.0, 0.01)
    assertEquals(2.5, featureAreaHa(feature("a", geometry = Fx.point(0.0, 0.0), areaHa = 2.5)))
    assertEquals(0.0, featureAreaHa(feature("a", geometry = Fx.point(0.0, 0.0))))
    assertEquals(3.0, featureAreaHa(feature("a", areaHa = 3.0)))
    assertEquals(0.0, featureAreaHa(feature("a")))
    assertEquals(
      4.0,
      featureAreaHa(
        feature("a", geometry = ImpactGeometry.LineString(ring(0.0, 0.0, 0.01)), areaHa = 4.0)
      ),
    )
    // A polygon with area ignores the precomputed value.
    assertEquals(
      geodesicAreaHa(square),
      featureAreaHa(feature("a", geometry = square, areaHa = 99.0)),
    )
    // A degenerate polygon has no area.
    val degenerate =
      ImpactGeometry.Polygon(listOf(listOf(ImpactLatLng(0.0, 0.0), ImpactLatLng(0.0, 1.0))))
    assertEquals(5.0, featureAreaHa(feature("a", geometry = degenerate, areaHa = 5.0)))
    // Negative or non-finite precomputed areas are ignored.
    assertEquals(0.0, featureAreaHa(feature("a", areaHa = -1.0)))
    assertEquals(0.0, featureAreaHa(feature("a", areaHa = Double.NaN)))
    // Holes larger than the exterior never make the area negative.
    val inverted = ImpactGeometry.Polygon(listOf(ring(0.0, 0.0, 0.001), ring(0.0, 0.0, 0.01)))
    assertEquals(0.0, geodesicAreaHa(inverted))
  }

  @Test
  fun representativePoint_perGeometryType() {
    assertEquals(ImpactLatLng(1.0, 2.0), representativePoint(Fx.point(1.0, 2.0)))
    // The closing vertex is not double-counted.
    val center = representativePoint(Fx.square(0.0, 0.0, 0.02))!!
    assertNear(0.01, center.lat)
    assertNear(0.01, center.lng)
    val line = ImpactGeometry.LineString(listOf(ImpactLatLng(0.0, 0.0), ImpactLatLng(2.0, 4.0)))
    assertEquals(ImpactLatLng(1.0, 2.0), representativePoint(line))
    assertEquals(null, representativePoint(ImpactGeometry.LineString(emptyList())))
    assertEquals(null, representativePoint(null))
  }
}
