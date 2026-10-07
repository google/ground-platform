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
package org.groundplatform.v2.devtools.prototypeapp.domain.model.editor

import org.groundplatform.v2.core.sampling.Allocation
import org.groundplatform.v2.core.sampling.PlotLayout
import org.groundplatform.v2.core.sampling.PlotShape
import org.groundplatform.v2.core.sampling.SAMPLING_ENGINE_VERSION
import org.groundplatform.v2.core.sampling.SampleDesign
import org.groundplatform.v2.core.sampling.SampleEncoding
import org.groundplatform.v2.core.sampling.SubPlotLayout

/** Sampling method (mirrors `SamplingDesign.method`). */
enum class SampleMethod(val label: String, val description: String) {
  SYSTEMATIC_GRID("Systematic grid", "Plots on a regular grid with a random starting point."),
  SIMPLE_RANDOM("Simple random", "A set number of plots placed at random."),
  STRATIFIED_RANDOM("Stratified random", "Plots placed at random within each stratum."),
  CLUSTER("Cluster", "Groups of plots placed at random."),
}

/** How stratified plots are split across strata (mirrors `SamplingDesign.allocation`). */
enum class AllocationMode(val label: String) {
  EQUAL("Equal"),
  PROPORTIONAL("Proportional to area"),
  CUSTOM("Custom"),
}

/** Plot geometry (mirrors `SamplingDesign.plot_shape`). */
enum class PlotShapeOption(val label: String) {
  SQUARE("Square"),
  CIRCLE("Circle"),
  POINT("Point (center only)"),
}

/** Placement of sample points inside each plot (mirrors `SubPlotDesign.mode`). */
enum class SubPlotMode(val label: String) {
  NONE("None"),
  CENTER("One at the center"),
  GRID("Grid"),
  RANDOM("Random"),
}

/** Where sample plots are drawn from. */
sealed interface SampleAreaSource {
  /** All parts of the survey area, as a single unnamed stratum. */
  data object SurveyArea : SampleAreaSource

  /**
   * The polygon features of Map layer [datasetKey], grouped into strata by the value of
   * [stratumProperty] (each feature is its own stratum when blank).
   */
  data class StrataLayer(val datasetKey: String, val stratumProperty: String) : SampleAreaSource
}

/**
 * Prototype mirror of `EntityDatasetDef.generator` (`SamplingDesign`): the engine's [SampleDesign]
 * plus the area it's drawn from and provenance of the last generation.
 *
 * Parameters for every method are kept (flat), so switching methods back and forth doesn't lose
 * values the organizer typed.
 */
data class SampleDesignConfig(
  val method: SampleMethod = SampleMethod.SYSTEMATIC_GRID,
  val areaSource: SampleAreaSource = SampleAreaSource.SurveyArea,
  val gridSpacingM: Double = 500.0,
  val count: Int = 50,
  val minDistanceM: Double = 0.0,
  val allocation: AllocationMode = AllocationMode.PROPORTIONAL,
  /** Plots per stratum ID, for [AllocationMode.CUSTOM]. */
  val customCounts: Map<String, Int> = emptyMap(),
  val clusterCount: Int = 10,
  val plotsPerSide: Int = 2,
  val clusterSpacingM: Double = 100.0,
  val plotShape: PlotShapeOption = PlotShapeOption.SQUARE,
  val plotSizeM: Double = 30.0,
  val subPlot: SubPlotMode = SubPlotMode.NONE,
  val subPlotGridN: Int = 3,
  val subPlotSpacingM: Double = 10.0,
  val subPlotRandomCount: Int = 5,
  val shuffle: Boolean = true,
  val seed: Long = 1L,
  /** Provenance of the last generation; `null` until plots have been generated. */
  val lastRun: GenerationRecord? = null,
) {
  /** The engine design for these parameters. */
  fun toSampleDesign(): SampleDesign =
    SampleDesign(
      layout =
        when (method) {
          SampleMethod.SYSTEMATIC_GRID -> PlotLayout.SystematicGrid(gridSpacingM)
          SampleMethod.SIMPLE_RANDOM -> PlotLayout.SimpleRandom(count, minDistanceM)
          SampleMethod.STRATIFIED_RANDOM ->
            PlotLayout.StratifiedRandom(
              totalCount = count,
              allocation =
                when (allocation) {
                  AllocationMode.EQUAL -> Allocation.Equal
                  AllocationMode.PROPORTIONAL -> Allocation.Proportional
                  AllocationMode.CUSTOM -> Allocation.Custom(customCounts)
                },
              minDistanceM = minDistanceM,
            )
          SampleMethod.CLUSTER ->
            PlotLayout.Cluster(clusterCount, plotsPerSide, clusterSpacingM, minDistanceM)
        },
      plotShape =
        when (plotShape) {
          PlotShapeOption.SQUARE -> PlotShape.SQUARE
          PlotShapeOption.CIRCLE -> PlotShape.CIRCLE
          PlotShapeOption.POINT -> PlotShape.POINT
        },
      plotSizeM = plotSizeM,
      subPlot =
        when (subPlot) {
          SubPlotMode.NONE -> SubPlotLayout.None
          SubPlotMode.CENTER -> SubPlotLayout.Center
          SubPlotMode.GRID -> SubPlotLayout.Grid(subPlotGridN, subPlotSpacingM)
          SubPlotMode.RANDOM -> SubPlotLayout.Random(subPlotRandomCount)
        },
      shuffle = shuffle,
      seed = seed,
    )

  /** These parameters without provenance, for comparing designs. */
  fun withoutProvenance(): SampleDesignConfig = copy(lastRun = null)

  /** Geometry type of the features this design produces. */
  val geometryKind: GeometryKind
    get() = if (plotShape == PlotShapeOption.POINT) GeometryKind.POINT else GeometryKind.POLYGON
}

/**
 * Provenance of a generation run (`generated_at`, `input_hash`, `feature_count`, `engine_version`).
 */
data class GenerationRecord(
  val generatedAt: String,
  /** [SampleDesignInputs.hash] of the area and design used. */
  val inputHash: String,
  val featureCount: Int,
  val engineVersion: Int = SAMPLING_ENGINE_VERSION,
)

/** Reserved properties written on every generated plot. */
object SamplePlotProperties {
  const val PLOT_ID = "plot_id"
  const val STRATUM = "stratum"
  const val SAMPLE_ORDER = "sample_order"
  const val INCLUSION_WEIGHT = "inclusion_weight"
  const val CLUSTER_ID = "cluster_id"
  /** Sample point locations in XForms geotrace format (`lat lng 0 0;lat lng 0 0`). */
  const val SAMPLES = "samples"
  /** Space-separated sample IDs (`1 2 3 …`), matching [SAMPLES] by position. */
  const val SAMPLE_IDS = "sample_ids"

  val all: Set<String> =
    setOf(PLOT_ID, STRATUM, SAMPLE_ORDER, INCLUSION_WEIGHT, CLUSTER_ID, SAMPLES, SAMPLE_IDS)

  /** Schema for a new sample plots layer. */
  val schema: List<EntityProperty> =
    listOf(
      EntityProperty(PLOT_ID, "Plot ID", PropertyType.TEXT, required = true),
      EntityProperty(STRATUM, "Stratum", PropertyType.TEXT),
      EntityProperty(SAMPLE_ORDER, "Sample order", PropertyType.INTEGER),
      EntityProperty(INCLUSION_WEIGHT, "Inclusion weight (ha)", PropertyType.DECIMAL),
      EntityProperty(CLUSTER_ID, "Cluster ID", PropertyType.TEXT),
      EntityProperty(SAMPLES, "Sample points", PropertyType.TEXT),
      EntityProperty(SAMPLE_IDS, "Sample IDs", PropertyType.TEXT),
    )

  /** Formats points as the `samples` geotrace string (see [SampleEncoding]). */
  fun formatGeotrace(points: List<LatLng>): String =
    SampleEncoding.encodeSamples(points.map { it.toGeoCoord() })

  /** Parses a `samples` geotrace string into points; empty if it's malformed. */
  fun parseGeotrace(text: String): List<LatLng> =
    SampleEncoding.decodeSamples(text).orEmpty().map { it.toLatLng() }

  /** Number of points in a geotrace without parsing coordinates. */
  fun geotraceSize(text: String): Int = if (text.isBlank()) 0 else text.count { it == ';' } + 1
}
