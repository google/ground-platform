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
package org.openforis.collectone.v2.core.geo.geoid

import kotlin.math.max
import openforis.collectone.v1.data.Geometry
import openforis.collectone.v1.data.GeometryType
import openforis.collectone.v1.data.Position
import openforis.collectone.v1.forms.GeoPoint
import org.openforis.collectone.v2.core.geo.GeometryProtos
import org.openforis.collectone.v2.core.geo.s2.ENTITY_S2_CELL_LEVEL
import org.openforis.collectone.v2.core.geo.s2.S2
import org.openforis.collectone.v2.core.geo.s2.S2FixedLevelCoverer
import org.openforis.collectone.v2.core.geo.s2.S2LatLngRect
import org.openforis.collectone.v2.core.geo.s2.s2CellToken as computeS2CellToken

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
   * Default level-20 cell overlap fraction at or above which the AgStack Asset Registry treats two
   * polygons sharing a [FIELD_LEVEL] hash as duplicates rather than promoting the new polygon to
   * [FIELD_FALLBACK_LEVEL].
   */
  const val DUPLICATE_OVERLAP_THRESHOLD = 0.95

  /** Required character length of a hexadecimal SHA-256 GeoID. */
  const val GEO_ID_HEX_LENGTH = 64

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

  /**
   * Candidate GeoIDs for a canonical [geometry], or `null` if [geometry] has no valid coordinates.
   *
   * - Points use [POINT_LEVEL] (level 30) for both `primary` and `fallback`.
   * - Polygons use the exterior ring's bounding box at [FIELD_LEVEL] (level 13) for `primary` and
   *   [FIELD_FALLBACK_LEVEL] (level 20) for `fallback`.
   * - LineStrings and Multi* geometries use their overall coordinate bounding box at [FIELD_LEVEL]
   *   and [FIELD_FALLBACK_LEVEL] (or [POINT_LEVEL] if degenerate to a single point).
   */
  fun forGeometry(geometry: Geometry): FieldGeoIdCandidates? {
    val vertices = extractBoundingVertices(geometry) ?: return null
    val distinctCoords = vertices.distinctBy { it.latitude to it.longitude }
    if (
      GeometryProtos.typeOf(geometry) == GeometryType.GEOMETRY_TYPE_POINT ||
        distinctCoords.size == 1
    ) {
      val pointGeoId = forPoint(distinctCoords.first())
      return FieldGeoIdCandidates(primary = pointGeoId, fallback = pointGeoId)
    }
    return forField(vertices)
  }

  /**
   * Computes the offline primary candidate GeoID for [geometry], or `""` when [geometry] has no
   * valid coordinates.
   */
  fun computeCandidate(geometry: Geometry): String = forGeometry(geometry)?.primary.orEmpty()

  /** Whether [geoId] is a well-formed 64-character lowercase hexadecimal SHA-256 string. */
  fun isValidFormat(geoId: String): Boolean =
    geoId.length == GEO_ID_HEX_LENGTH && geoId.all { it in '0'..'9' || it in 'a'..'f' }

  /**
   * Whether [geoId] is a valid AgStack GeoID matching either the [FieldGeoIdCandidates.primary] or
   * [FieldGeoIdCandidates.fallback] candidate of [geometry].
   */
  fun matchesGeometry(geoId: String, geometry: Geometry): Boolean {
    if (!isValidFormat(geoId)) return false
    val candidates = forGeometry(geometry) ?: return false
    return geoId == candidates.primary || geoId == candidates.fallback
  }

  /**
   * Level-20 S2 cell tokens covering the bounding box of [geometry], used by the AgStack registry
   * to test polygon overlap when two fields share a level-13 primary hash.
   */
  fun level20CellTokens(geometry: Geometry): Set<String> {
    val vertices = extractBoundingVertices(geometry) ?: return emptySet()
    return cellTokens(vertices, FIELD_FALLBACK_LEVEL).toSet()
  }

  /**
   * Fraction of level-20 S2 bounding-box cells shared between [a] and [b] (`|C_a ∩ C_b| /
   * max(|C_a|, |C_b|)`), in `0.0..1.0`.
   */
  fun level20OverlapFraction(a: Geometry, b: Geometry): Double {
    val tokensA = level20CellTokens(a)
    val tokensB = level20CellTokens(b)
    val denom = max(tokensA.size, tokensB.size)
    if (denom == 0) return 0.0
    val shared = tokensA.count { it in tokensB }
    return shared.toDouble() / denom.toDouble()
  }

  /**
   * Computes the `EntityRecord.s2_cell_token` at [level] (default [ENTITY_S2_CELL_LEVEL]) for the
   * representative point of [geometry], or `""` if [geometry] has no valid representative point.
   */
  fun s2CellToken(geometry: Geometry, level: Int = ENTITY_S2_CELL_LEVEL): String {
    val rep =
      GeometryProtos.representativePosition(geometry)?.takeIf(::isValidPosition) ?: return ""
    return computeS2CellToken(rep.latitude, rep.longitude, level)
  }

  private fun extractBoundingVertices(geometry: Geometry): List<GeoPoint>? {
    val positions: List<Position> =
      when (GeometryProtos.typeOf(geometry)) {
        GeometryType.GEOMETRY_TYPE_POINT -> listOfNotNull(geometry.point)
        GeometryType.GEOMETRY_TYPE_LINE_STRING -> geometry.line_string?.positions.orEmpty()
        GeometryType.GEOMETRY_TYPE_POLYGON ->
          geometry.polygon?.rings?.firstOrNull()?.positions.orEmpty()
        GeometryType.GEOMETRY_TYPE_MULTI_POINT -> geometry.multi_point?.positions.orEmpty()
        GeometryType.GEOMETRY_TYPE_MULTI_LINE_STRING ->
          geometry.multi_line_string?.lines?.flatMap { it.positions }.orEmpty()
        GeometryType.GEOMETRY_TYPE_MULTI_POLYGON ->
          geometry.multi_polygon
            ?.polygons
            ?.mapNotNull { it.rings.firstOrNull() }
            ?.flatMap { it.positions }
            .orEmpty()
        GeometryType.GEOMETRY_TYPE_UNSPECIFIED,
        null -> emptyList()
      }
    if (positions.isEmpty() || !positions.all(::isValidPosition)) return null
    return positions.map(GeometryProtos::toGeoPoint)
  }

  private fun isValidPosition(position: Position): Boolean =
    position.latitude in -90.0..90.0 && position.longitude in -180.0..180.0
}

/**
 * The two GeoIDs the AgStack registry may assign to a field boundary.
 *
 * @property primary SHA-256 of the level-13 tokens; assigned when no registered field shares it.
 * @property fallback SHA-256 of the level-20 tokens; assigned when [primary] is already taken and
 *   the field does not overlap an existing one beyond the registry's threshold.
 */
data class FieldGeoIdCandidates(val primary: String, val fallback: String)
