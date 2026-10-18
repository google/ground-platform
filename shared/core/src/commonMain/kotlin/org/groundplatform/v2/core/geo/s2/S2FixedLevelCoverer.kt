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
package org.groundplatform.v2.core.geo.s2

import kotlin.math.min

/**
 * Computes the covering `s2sphere.RegionCoverer` produces for a [S2LatLngRect] when `min_level ==
 * max_level` (with the default `level_mod = 1` and `max_cells = 8`).
 *
 * With a fixed level, `max_cells` can never be honored, so the coverer's priority queue reduces to
 * a plain depth-first descent: starting from the same initial candidates, every cell whose padded
 * rectangle bound may intersect the region is subdivided until the target level, and every
 * target-level cell that may intersect is kept. The result is then normalized and denormalized back
 * to the target level, which amounts to sorting by cell ID and removing duplicates.
 */
internal object S2FixedLevelCoverer {

  /** Cells at [level] covering [region], in ascending cell ID order. */
  fun covering(region: S2LatLngRect, level: Int): List<S2CellId> {
    require(level in 0..S2.MAX_LEVEL) { "level must be in 0..${S2.MAX_LEVEL}: $level" }
    val result = mutableListOf<S2CellId>()

    fun visit(cell: S2Cell) {
      if (!region.mayIntersect(cell)) return
      if (cell.level >= level) {
        result.add(cell.id)
        return
      }
      for (child in cell.subdivide()) visit(child)
    }

    for (cell in initialCandidates(region, level)) visit(cell)
    return result.distinct().sorted()
  }

  /** Mirrors `RegionCoverer.__get_initial_candidates` for `max_cells >= 4` and `level_mod == 1`. */
  private fun initialCandidates(region: S2LatLngRect, level: Int): List<S2Cell> {
    val cap = region.capBound()
    val startLevel = min(minWidthMaxLevel(2 * cap.angle()), min(level, S2.MAX_LEVEL - 1))
    if (startLevel > 0) {
      return S2CellId.fromPoint(cap.axis).vertexNeighbors(startLevel).map(S2Cell::fromCellId)
    }
    return (0 until 6).map(S2Cell::face)
  }

  /**
   * The maximum level at which every cell is at least [value] radians wide
   * (`S2::kMinWidth.GetMaxLevel` for the quadratic projection).
   */
  private fun minWidthMaxLevel(value: Double): Int {
    if (value <= 0) return S2.MAX_LEVEL
    val exponent = S2.frexpExponent(MIN_WIDTH_DERIV / value)
    return (exponent - 1).coerceIn(0, S2.MAX_LEVEL)
  }

  private val MIN_WIDTH_DERIV = 2 * kotlin.math.sqrt(2.0) / 3
}
