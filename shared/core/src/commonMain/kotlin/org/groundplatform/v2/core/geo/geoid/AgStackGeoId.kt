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
import org.groundplatform.v2.core.geo.s2.S2
import org.groundplatform.v2.core.geo.s2.S2FixedLevelCoverer
import org.groundplatform.v2.core.geo.s2.S2LatLngRect

/**
 * Offline computation of [AgStack Asset Registry](https://github.com/agstack/asset-registry)
 * GeoIDs.
 *
 * A GeoID is the lowercase hex SHA-256 of the S2 cell tokens covering a geometry at one S2 level,
 * concatenated without separators in ascending cell ID order. The registry computes the covering
 * from the geometry's latitude/longitude **bounding box** (the polygon's exterior ring only; holes
 * are ignored), using every cell at that level whose padded bounds touch the box.
 *
 * Which level becomes the registered GeoID depends on the registry's state, not just the geometry:
 * - **Fields (polygons)**: the level-13 hash ([FIELD_LEVEL]) if no registered field shares it.
 *   Otherwise the registry compares level-20 cell overlap with the existing fields; at or above the
 *   overlap threshold (95% by default) the field is rejected as a duplicate, otherwise it is
 *   registered under the level-20 hash ([FIELD_FALLBACK_LEVEL]).
 * - **Points**: the level-30 hash ([POINT_LEVEL]).
 *
 * Values computed here are therefore *candidate* GeoIDs. They are exactly what the registry would
 * assign at each level, but only the registry can say which candidate a field actually receives.
 */
object AgStackGeoId {
  /** S2 level whose hash is a field's GeoID when no registered field shares it. */
  const val FIELD_LEVEL = 13

  /** S2 level whose hash is a field's GeoID when its [FIELD_LEVEL] hash is already taken. */
  const val FIELD_FALLBACK_LEVEL = 20

  /** S2 level whose hash is a point's GeoID. */
  const val POINT_LEVEL = 30

  /**
   * S2 cell tokens covering the bounding box of [vertices] at [level], in the registry's order.
   *
   * These are the values the registry stores as `s2_index__L<level>_list` and uses for overlap
   * checks. For a polygon pass its exterior ring; for a point pass the single point.
   */
  fun cellTokens(vertices: List<GeoPoint>, level: Int): List<String> {
    require(vertices.isNotEmpty()) { "vertices must not be empty" }
    require(level in 0..S2.MAX_LEVEL) { "level must be in 0..${S2.MAX_LEVEL}: $level" }
    for (vertex in vertices) {
      require(vertex.latitude in -90.0..90.0) { "latitude out of range: ${vertex.latitude}" }
      require(vertex.longitude in -180.0..180.0) { "longitude out of range: ${vertex.longitude}" }
    }
    val region =
      S2LatLngRect.fromPointPair(
        S2.degreesToRadians(vertices.minOf { it.latitude }),
        S2.degreesToRadians(vertices.minOf { it.longitude }),
        S2.degreesToRadians(vertices.maxOf { it.latitude }),
        S2.degreesToRadians(vertices.maxOf { it.longitude }),
      )
    return S2FixedLevelCoverer.covering(region, level).map { it.toToken() }
  }

  /** GeoID for an ordered list of cell tokens (`Utils.generate_geo_id`). */
  fun hashTokens(tokens: List<String>): String {
    val sha = Sha256()
    for (token in tokens) sha.update(token.encodeToByteArray())
    return sha.hexDigest()
  }

  /** Candidate GeoID of the bounding box of [vertices] at [level]. */
  fun geoId(vertices: List<GeoPoint>, level: Int): String = hashTokens(cellTokens(vertices, level))

  /**
   * Candidate GeoIDs for a field boundary given its exterior ring. The ring may be open or closed;
   * only its bounding box matters.
   */
  fun forField(exteriorRing: List<GeoPoint>): FieldGeoIdCandidates =
    FieldGeoIdCandidates(
      primary = geoId(exteriorRing, FIELD_LEVEL),
      fallback = geoId(exteriorRing, FIELD_FALLBACK_LEVEL),
    )

  /** GeoID the registry assigns to a newly registered [point]. */
  fun forPoint(point: GeoPoint): String = geoId(listOf(point), POINT_LEVEL)
}

/**
 * The two GeoIDs the AgStack registry may assign to a field boundary.
 *
 * @property primary SHA-256 of the level-13 tokens; assigned when no registered field shares it.
 * @property fallback SHA-256 of the level-20 tokens; assigned when [primary] is already taken and
 *   the field does not overlap an existing one beyond the registry's threshold.
 */
data class FieldGeoIdCandidates(val primary: String, val fallback: String)
