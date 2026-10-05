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
package org.groundplatform.v2.core.sampling

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.groundplatform.v2.core.geo.IndexedRing
import org.groundplatform.v2.core.geo.LocalMetricFrame
import org.groundplatform.v2.core.geo.quantizeDegrees

/**
 * Plot boundaries and sub-plot sample layouts, built in a local metric frame around each plot
 * center. Rings are counter-clockwise (RFC 7946 section 3.1.6) and closed (first == last).
 */
object PlotGeometry {
  private const val CIRCLE_VERTICES = DefaultSamplingEngine.CIRCLE_VERTICES
  private const val MAX_RANDOM_ATTEMPTS_PER_SAMPLE = 1000

  // Unit-circle vertices, starting due east and turning counter-clockwise.
  private val CIRCLE_COS = DoubleArray(CIRCLE_VERTICES) { cos(2 * PI * it / CIRCLE_VERTICES) }
  private val CIRCLE_SIN = DoubleArray(CIRCLE_VERTICES) { sin(2 * PI * it / CIRCLE_VERTICES) }

  /**
   * Boundary ring for a plot of [shape] and [sizeM] (square side or circle diameter) centered on
   * [center]: 5 coordinates for [PlotShape.SQUARE], 25 for [PlotShape.CIRCLE] (24 vertices plus the
   * closing one), and none for [PlotShape.POINT].
   */
  fun boundary(center: GeoCoord, shape: PlotShape, sizeM: Double): Ring {
    if (shape == PlotShape.POINT) return emptyList()
    val frame = LocalMetricFrame(center.lat, center.lng)
    val h = sizeM / 2
    val ring = ArrayList<GeoCoord>(if (shape == PlotShape.SQUARE) 5 else CIRCLE_VERTICES + 1)
    when (shape) {
      PlotShape.SQUARE -> {
        val south = quantizeDegrees(frame.lat(-h))
        val north = quantizeDegrees(frame.lat(h))
        val west = quantizeDegrees(frame.lng(-h))
        val east = quantizeDegrees(frame.lng(h))
        ring.add(GeoCoord(south, west))
        ring.add(GeoCoord(south, east))
        ring.add(GeoCoord(north, east))
        ring.add(GeoCoord(north, west))
      }
      PlotShape.CIRCLE ->
        for (k in 0 until CIRCLE_VERTICES) {
          ring.add(
            GeoCoord(
              quantizeDegrees(frame.lat(h * CIRCLE_SIN[k])),
              quantizeDegrees(frame.lng(h * CIRCLE_COS[k])),
            )
          )
        }
      PlotShape.POINT -> Unit
    }
    ring.add(ring[0])
    return ring
  }

  /**
   * Sub-plot sample points for one plot, in `sample_id` order (1-based index). Grid samples are in
   * reading order (north to south, west to east); random samples are drawn uniformly inside
   * [boundary] (or inside a [sizeM] square for point plots) from a stream seeded with [plotSeed].
   */
  fun subPlotSamples(
    center: GeoCoord,
    boundary: Ring,
    shape: PlotShape,
    sizeM: Double,
    layout: SubPlotLayout,
    plotSeed: Long,
  ): List<GeoCoord> =
    when (layout) {
      SubPlotLayout.None -> emptyList()
      SubPlotLayout.Center -> listOf(center)
      is SubPlotLayout.Grid -> {
        val frame = LocalMetricFrame(center.lat, center.lng)
        val half = (layout.n - 1) / 2.0
        val out = ArrayList<GeoCoord>(layout.n * layout.n)
        for (r in 0 until layout.n) {
          val lat = quantizeDegrees(frame.lat((half - r) * layout.spacingM))
          for (c in 0 until layout.n) {
            out.add(GeoCoord(lat, quantizeDegrees(frame.lng((c - half) * layout.spacingM))))
          }
        }
        out
      }
      is SubPlotLayout.Random ->
        randomSamples(center, boundary, shape, sizeM, layout.count, plotSeed)
    }

  private fun randomSamples(
    center: GeoCoord,
    boundary: Ring,
    shape: PlotShape,
    sizeM: Double,
    count: Int,
    plotSeed: Long,
  ): List<GeoCoord> {
    if (count <= 0) return emptyList()
    val frame = LocalMetricFrame(center.lat, center.lng)
    val h = (if (sizeM > 0) sizeM else 0.0) / 2
    val rng = SplitMix64(plotSeed)
    val ring =
      if (shape == PlotShape.POINT || boundary.size < 4) null
      else
        IndexedRing(
          DoubleArray(boundary.size) { boundary[it].lat },
          DoubleArray(boundary.size) { boundary[it].lng },
        )
    val out = ArrayList<GeoCoord>(count)
    var attempts = 0
    val maxAttempts = count * MAX_RANDOM_ATTEMPTS_PER_SAMPLE
    while (out.size < count) {
      if (attempts++ >= maxAttempts) {
        // Degenerate (e.g. sub-centimeter) plots: fall back to the center, which is inside.
        out.add(center)
        continue
      }
      val east = (2 * rng.nextDouble() - 1) * h
      val north = (2 * rng.nextDouble() - 1) * h
      val lat = quantizeDegrees(frame.lat(north))
      val lng = quantizeDegrees(frame.lng(east))
      if (ring != null && !ring.contains(lat, lng)) continue
      out.add(GeoCoord(lat, lng))
    }
    return out
  }
}
