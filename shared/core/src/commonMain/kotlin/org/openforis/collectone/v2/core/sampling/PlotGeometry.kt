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
package org.openforis.collectone.v2.core.sampling

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.openforis.collectone.v2.core.geo.GeoCoord
import org.openforis.collectone.v2.core.geo.IndexedRing
import org.openforis.collectone.v2.core.geo.LocalMetricFrame
import org.openforis.collectone.v2.core.geo.Wgs84
import org.openforis.collectone.v2.core.geo.quantizeDegrees

/**
 * Plot boundaries and sub-plot sample layouts, built in a local metric frame around each plot
 * center. Rings are counter-clockwise (RFC 7946 section 3.1.6) and closed (first == last).
 */
object PlotGeometry {
  /** Default ground side length in meters of a hollow square sub-plot box (Collect Earth 2 m). */
  const val DEFAULT_SUBPLOT_SIZE_M = 2.0

  private const val MIN_SUBPLOT_SIZE_M = 0.5
  private const val MAX_SUBPLOT_SIZE_M = 4.0
  private const val MAX_PAIRWISE_SAMPLES = 100
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

  /**
   * Ground side length in meters of each hollow square sub-plot box: 10% of the plot size, capped
   * at half the grid spacing so neighboring boxes never touch, and clamped to 0.5–4.0 m (defaulting
   * to Collect Earth's 2 m × 2 m box when [design] is absent or non-finite). When [design] is
   * absent and [samples] has at least two points, also caps at half the smallest pairwise spacing.
   */
  fun subPlotSizeM(design: SampleDesign?, samples: List<GeoCoord> = emptyList()): Double {
    if (design != null) {
      val fromPlot = design.plotSizeM * 0.10
      val spaced =
        when (val sub = design.subPlot) {
          is SubPlotLayout.Grid -> minOf(fromPlot, sub.spacingM * 0.5)
          else -> fromPlot
        }
      return spaced
        .takeIf { it.isFinite() && it > 0.0 }
        ?.coerceIn(MIN_SUBPLOT_SIZE_M, MAX_SUBPLOT_SIZE_M) ?: DEFAULT_SUBPLOT_SIZE_M
    }
    val minSpacingM = minSampleSpacingM(samples)
    val spaced =
      if (minSpacingM != null && minSpacingM > 0.0) {
        minOf(DEFAULT_SUBPLOT_SIZE_M, minSpacingM * 0.5)
      } else {
        DEFAULT_SUBPLOT_SIZE_M
      }
    return spaced.coerceIn(MIN_SUBPLOT_SIZE_M, MAX_SUBPLOT_SIZE_M)
  }

  /**
   * Closed 5-vertex ground-scaled square frame ([PlotShape.SQUARE]) of side [sizeM] meters centered
   * on [center]. Used consistently across Collect One maps and Google Earth KML export.
   */
  fun subPlotBox(center: GeoCoord, sizeM: Double): Ring = boundary(center, PlotShape.SQUARE, sizeM)

  private fun minSampleSpacingM(samples: List<GeoCoord>): Double? {
    if (samples.size < 2) return null
    val capped = samples.take(MAX_PAIRWISE_SAMPLES)
    var best = Double.POSITIVE_INFINITY
    for (i in capped.indices) {
      val a = capped[i]
      for (j in i + 1 until capped.size) {
        val b = capped[j]
        val d = Wgs84.localDistanceM(a.lat, a.lng, b.lat, b.lng)
        if (d > 0.0 && d < best) best = d
      }
    }
    return best.takeIf { it.isFinite() }
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
