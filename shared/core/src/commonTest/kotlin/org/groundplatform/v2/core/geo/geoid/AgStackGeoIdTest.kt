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
package org.groundplatform.v2.core.geo.geoid

import groundplatform.v2.forms.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** A golden covering produced by the AgStack reference code at one S2 level. */
internal data class GoldenCase(
  val name: String,
  val vertices: List<GeoPoint>,
  val level: Int,
  val tokenCount: Int,
  val firstToken: String,
  val lastToken: String,
  /** Full token list when short enough to inline, otherwise `null`. */
  val tokens: List<String>?,
  val geoId: String,
)

/** A random field with the AgStack reference level-13 and level-20 GeoIDs. */
internal data class RandomFieldCase(
  val vertices: List<GeoPoint>,
  val level13GeoId: String,
  val level20TokenCount: Int,
  val level20GeoId: String,
)

class AgStackGeoIdTest {

  private fun point(latitude: Double, longitude: Double) =
    GeoPoint(latitude = latitude, longitude = longitude)

  @Test
  fun forField_matchesGeoIdIssuedByProductionRegistry() {
    // Request and response from the AgStack Asset Registry README (`/register-field-boundary`).
    val ring =
      listOf(
        point(30.311450431756946, 76.88855767250062),
        point(30.310732833916543, 76.88841819763184),
        point(30.31070505582999, 76.88945889472961),
        point(30.311399505631794, 76.8894535303116),
        point(30.311450431756946, 76.88855767250062),
      )

    assertEquals(
      "0d8b4afb3b3332f75cf5b1889b0564a9e7a80f4bad239a9a593e1665210c3079",
      AgStackGeoId.forField(ring).primary,
    )
  }

  @Test
  fun forPoint_matchesGeoIdsIssuedByProductionRegistry() {
    // Responses from the AgStack Asset Registry README (`/register-point` and bulk points).
    assertEquals(
      "8c5c1d6ae4c50c574a05b44c0af6797c2001f518a35116c4642bd48edf769024",
      AgStackGeoId.forPoint(point(30.90706, 74.78209)),
    )
    assertEquals(
      "a28da121245cee30b3c901b00d62ba61855c82d0abca5dfb219ca16ac00aef2b",
      AgStackGeoId.forPoint(point(29.92052, 75.77439)),
    )
  }

  @Test
  fun cellTokens_matchReferenceImplementation_forGoldenCases() {
    for (case in GOLDEN_CASES) {
      val tokens = AgStackGeoId.cellTokens(case.vertices, case.level)

      assertEquals(case.tokenCount, tokens.size, "token count for ${case.name}")
      assertEquals(case.firstToken, tokens.first(), "first token for ${case.name}")
      assertEquals(case.lastToken, tokens.last(), "last token for ${case.name}")
      case.tokens?.let { assertEquals(it, tokens, "tokens for ${case.name}") }
      assertEquals(case.geoId, AgStackGeoId.hashTokens(tokens), "GeoID for ${case.name}")
    }
  }

  @Test
  fun forField_matchesReferenceImplementation_forRandomFields() {
    for ((index, case) in RANDOM_FIELD_CASES.withIndex()) {
      val candidates = AgStackGeoId.forField(case.vertices)

      assertEquals(case.level13GeoId, candidates.primary, "level-13 GeoID for random field $index")
      assertEquals(
        case.level20TokenCount,
        AgStackGeoId.cellTokens(case.vertices, AgStackGeoId.FIELD_FALLBACK_LEVEL).size,
        "level-20 token count for random field $index",
      )
      assertEquals(case.level20GeoId, candidates.fallback, "level-20 GeoID for random field $index")
    }
  }

  @Test
  fun cellTokens_ignoresVertexOrderAndRingClosure() {
    val ring = listOf(point(1.0, 2.0), point(1.001, 2.0), point(1.001, 2.001), point(1.0, 2.001))

    val expected = AgStackGeoId.cellTokens(ring, AgStackGeoId.FIELD_FALLBACK_LEVEL)

    assertEquals(
      expected,
      AgStackGeoId.cellTokens(ring.reversed() + ring.last(), AgStackGeoId.FIELD_FALLBACK_LEVEL),
    )
  }

  @Test
  fun cellTokens_areSortedAndAtRequestedLevel() {
    val ring = listOf(point(-14.797, -39.274), point(-14.795, -39.272))

    val tokens = AgStackGeoId.cellTokens(ring, 18)

    assertTrue(tokens.isNotEmpty())
    // A level-L token encodes 3 + 2L + 1 bits, rounded up to whole hex digits (before trailing
    // zeros are stripped, so it can only be shorter).
    assertTrue(tokens.all { it.length <= (3 + 2 * 18 + 1 + 3) / 4 })
    assertEquals(tokens.sortedBy { it.padEnd(16, '0').toULong(16) }, tokens)
  }

  @Test
  fun hashTokens_ofNoTokens_isSha256OfEmptyInput() {
    assertEquals(
      "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
      AgStackGeoId.hashTokens(emptyList()),
    )
  }

  @Test
  fun cellTokens_rejectsInvalidInput() {
    assertFailsWith<IllegalArgumentException> { AgStackGeoId.cellTokens(emptyList(), 13) }
    assertFailsWith<IllegalArgumentException> {
      AgStackGeoId.cellTokens(listOf(point(90.5, 0.0)), 13)
    }
    assertFailsWith<IllegalArgumentException> {
      AgStackGeoId.cellTokens(listOf(point(0.0, -180.1)), 13)
    }
    assertFailsWith<IllegalArgumentException> {
      AgStackGeoId.cellTokens(listOf(point(Double.NaN, 0.0)), 13)
    }
    assertFailsWith<IllegalArgumentException> {
      AgStackGeoId.cellTokens(listOf(point(0.0, 0.0)), 31)
    }
  }
}
