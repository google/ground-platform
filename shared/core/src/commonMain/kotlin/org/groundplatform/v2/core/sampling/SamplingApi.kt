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

/**
 * Public API of the statistical sample design engine.
 *
 * The engine is pure, deterministic Kotlin: the same [SampleDesign] and [SamplingArea] produce the
 * same plots on every platform (JVM, JS, WasmJS, iOS). Coordinates are WGS 84 decimal degrees.
 *
 * Generated plots are meant to be stored as ordinary map features (`EntityRecord`s): the plot
 * boundary becomes the feature geometry (`GeoShape`, or `GeoPoint` for [PlotShape.POINT]) and the
 * sub-plot sample points become the plot's `samples` property.
 */

/** Version of the generation algorithms. Bump whenever output for a given seed would change. */
const val SAMPLING_ENGINE_VERSION: Int = 1

/** A WGS 84 coordinate in decimal degrees. */
data class GeoCoord(val lat: Double, val lng: Double)

/**
 * A closed polygon ring. The first and last coordinates may or may not repeat; both are accepted.
 */
typealias Ring = List<GeoCoord>

/**
 * A named part of the sampling area. A stratum with several disjoint parts is expressed as several
 * [Stratum] entries sharing the same [id] (mirroring several map features sharing a `stratum`
 * value).
 */
data class Stratum(val id: String, val ring: Ring)

/**
 * The area plots are drawn from: either the survey area (all parts form one implicit stratum) or a
 * set of strata from a polygon map layer. Holes are not supported.
 */
data class SamplingArea(val strata: List<Stratum>) {
  companion object {
    /** The survey area's parts as a single unnamed stratum. */
    fun fromSurveyArea(parts: List<Ring>): SamplingArea =
      SamplingArea(parts.map { Stratum(id = "", ring = it) })
  }
}

/** How plots are distributed across strata in a stratified design. */
sealed interface Allocation {
  /** The same number of plots in every stratum. */
  data object Equal : Allocation

  /** Plot counts proportional to each stratum's area. */
  data object Proportional : Allocation

  /** Explicit plot counts per stratum ID; strata not listed get zero plots. */
  data class Custom(val countsByStratum: Map<String, Int>) : Allocation
}

/** Method used to place plots. */
sealed interface PlotLayout {
  /** Regular grid with [spacingM] meters between plot centers and a seeded random origin offset. */
  data class SystematicGrid(val spacingM: Double) : PlotLayout

  /** [count] plots placed uniformly at random, at least [minDistanceM] meters apart. */
  data class SimpleRandom(val count: Int, val minDistanceM: Double = 0.0) : PlotLayout

  /**
   * [totalCount] plots split across strata according to [allocation] (ignored for
   * [Allocation.Custom]), then placed at random within each stratum.
   */
  data class StratifiedRandom(
    val totalCount: Int,
    val allocation: Allocation = Allocation.Proportional,
    val minDistanceM: Double = 0.0,
  ) : PlotLayout

  /**
   * [clusterCount] primary units placed at random; each holds a [plotsPerSide] × [plotsPerSide]
   * square pattern of plots spaced [plotSpacingM] meters apart. Plots outside the area are dropped.
   */
  data class Cluster(
    val clusterCount: Int,
    val plotsPerSide: Int,
    val plotSpacingM: Double,
    val minDistanceM: Double = 0.0,
  ) : PlotLayout
}

/** Geometry of each plot. */
enum class PlotShape {
  /** Axis-aligned square of side `plotSizeM`, stored as a 5-vertex closed ring. */
  SQUARE,
  /** Circle of diameter `plotSizeM`, stored as a 24-vertex closed ring. */
  CIRCLE,
  /** Center point only; no boundary. */
  POINT,
}

/** Placement of sample points inside each plot. */
sealed interface SubPlotLayout {
  data object None : SubPlotLayout

  /** A single sample at the plot center. */
  data object Center : SubPlotLayout

  /** An [n] × [n] grid centered in the plot, [spacingM] meters apart. */
  data class Grid(val n: Int, val spacingM: Double) : SubPlotLayout

  /** [count] points at random inside the plot boundary, seeded per plot. */
  data class Random(val count: Int) : SubPlotLayout
}

/** Complete, reproducible description of a sample design. */
data class SampleDesign(
  val layout: PlotLayout,
  val plotShape: PlotShape = PlotShape.SQUARE,
  val plotSizeM: Double = 100.0,
  val subPlot: SubPlotLayout = SubPlotLayout.None,
  /** Shuffle [GeneratedPlot.sampleOrder] so interpreters don't visit neighbors in sequence. */
  val shuffle: Boolean = true,
  val seed: Long = 1L,
)

/** One generated plot, ready to be written as a map feature. */
data class GeneratedPlot(
  /** Stable ID, unique within the design (e.g. `"P000123"`). */
  val plotId: String,
  val center: GeoCoord,
  /** Closed ring (first == last) for SQUARE/CIRCLE; empty for POINT. */
  val boundary: Ring,
  /** Stratum ID, or empty for unstratified designs. */
  val stratum: String,
  /** 1-based visiting order (shuffled when [SampleDesign.shuffle] is true). */
  val sampleOrder: Int,
  /** Area represented by this plot, in hectares (stratum area ÷ plots in stratum). */
  val inclusionWeightHa: Double,
  /** Cluster ID for [PlotLayout.Cluster] designs; empty otherwise. */
  val clusterId: String,
  /** Sub-plot sample points, in order; `sample_id` of each is its 1-based index. */
  val samples: List<GeoCoord>,
)

/** Pre-generation summary used for live feedback in the UI. */
data class SampleEstimate(
  /** Total area of the sampling area in hectares. */
  val areaHa: Double,
  /** Approximate number of plots the design would produce. */
  val estimatedPlotCount: Int,
)

/** Outcome of [SamplingEngine.generate]. */
sealed interface SamplingResult {
  data class Success(val plots: List<GeneratedPlot>) : SamplingResult

  /** Generation could not satisfy the design (e.g. cannot fit N plots at D m apart). */
  data class Failure(val message: String) : SamplingResult

  data object Cancelled : SamplingResult
}

/** Entry point of the sampling engine. Implementations must be deterministic. */
interface SamplingEngine {
  /** Fast estimate (no plot placement) for live UI feedback. */
  fun estimate(area: SamplingArea, design: SampleDesign): SampleEstimate

  /**
   * Generates all plots. [onProgress] receives the fraction done (0.0–1.0); generation stops and
   * returns [SamplingResult.Cancelled] as soon as [isCancelled] returns true.
   */
  fun generate(
    area: SamplingArea,
    design: SampleDesign,
    onProgress: (Double) -> Unit = {},
    isCancelled: () -> Boolean = { false },
  ): SamplingResult

  /**
   * Generates plots in chunks of up to [chunkSize], so single-threaded clients (WasmJS) can yield
   * between chunks. Concatenating all chunks equals [SamplingResult.Success.plots] from [generate].
   * Plot placement runs when the first chunk is requested; later chunks only materialize plots.
   * Iteration throws [SamplingException] where [generate] would return [SamplingResult.Failure].
   */
  fun generateChunks(
    area: SamplingArea,
    design: SampleDesign,
    chunkSize: Int = 2000,
  ): Sequence<List<GeneratedPlot>>

  companion object {
    /** The default engine implementation. */
    val Default: SamplingEngine
      get() = DefaultSamplingEngine
  }
}
