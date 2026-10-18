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

import groundplatform.v2.survey.SamplingDesign
import groundplatform.v2.survey.SubPlotDesign

/**
 * Conversions between the engine's [SampleDesign] and the persisted `SamplingDesign` proto
 * (`EntityDatasetDef.sampling`). Provenance fields (`generated_at`, `input_hash`, `feature_count`)
 * are set by the caller.
 */
object SamplingDesignProtos {
  /**
   * Builds the proto for [design]. Plots are drawn from the survey area unless [strataLayer] is
   * given. [SAMPLING_ENGINE_VERSION] is recorded as `engine_version`.
   */
  fun toProto(
    design: SampleDesign,
    strataLayer: SamplingDesign.StrataLayerRef? = null,
  ): SamplingDesign {
    val base =
      SamplingDesign(
        survey_area = if (strataLayer == null) true else null,
        strata_layer = strataLayer,
        plot_shape =
          when (design.plotShape) {
            PlotShape.SQUARE -> SamplingDesign.PlotShape.SQUARE
            PlotShape.CIRCLE -> SamplingDesign.PlotShape.CIRCLE
            PlotShape.POINT -> SamplingDesign.PlotShape.POINT
          },
        plot_size_m = design.plotSizeM,
        shuffle = design.shuffle,
        seed = design.seed,
        engine_version = SAMPLING_ENGINE_VERSION,
        sub_plot = subPlotToProto(design.subPlot),
      )
    return when (val layout = design.layout) {
      is PlotLayout.SystematicGrid ->
        base.copy(method = SamplingDesign.Method.SYSTEMATIC_GRID, spacing_m = layout.spacingM)
      is PlotLayout.SimpleRandom ->
        base.copy(
          method = SamplingDesign.Method.SIMPLE_RANDOM,
          count = layout.count,
          min_distance_m = layout.minDistanceM,
        )
      is PlotLayout.StratifiedRandom ->
        when (val allocation = layout.allocation) {
          Allocation.Equal -> base.copy(allocation = SamplingDesign.Allocation.EQUAL)
          Allocation.Proportional -> base.copy(allocation = SamplingDesign.Allocation.PROPORTIONAL)
          is Allocation.Custom ->
            base.copy(
              allocation = SamplingDesign.Allocation.CUSTOM,
              custom_counts = allocation.countsByStratum,
            )
        }.copy(
          method = SamplingDesign.Method.STRATIFIED_RANDOM,
          count = layout.totalCount,
          min_distance_m = layout.minDistanceM,
        )
      is PlotLayout.Cluster ->
        base.copy(
          method = SamplingDesign.Method.CLUSTER,
          count = layout.clusterCount,
          cluster_plots_per_side = layout.plotsPerSide,
          cluster_plot_spacing_m = layout.plotSpacingM,
          min_distance_m = layout.minDistanceM,
        )
    }
  }

  /**
   * Rebuilds the engine design from [proto], or returns null if the method is unspecified. A
   * `USER_DRAWN` sub-plot mode maps to [SubPlotLayout.None], since the engine generates no samples
   * for it.
   */
  fun fromProto(proto: SamplingDesign): SampleDesign? {
    val layout =
      when (proto.method) {
        SamplingDesign.Method.SYSTEMATIC_GRID -> PlotLayout.SystematicGrid(proto.spacing_m)
        SamplingDesign.Method.SIMPLE_RANDOM ->
          PlotLayout.SimpleRandom(proto.count, proto.min_distance_m)
        SamplingDesign.Method.STRATIFIED_RANDOM ->
          PlotLayout.StratifiedRandom(
            totalCount = proto.count,
            allocation =
              when (proto.allocation) {
                SamplingDesign.Allocation.EQUAL -> Allocation.Equal
                SamplingDesign.Allocation.CUSTOM -> Allocation.Custom(proto.custom_counts)
                SamplingDesign.Allocation.PROPORTIONAL,
                SamplingDesign.Allocation.ALLOCATION_UNSPECIFIED -> Allocation.Proportional
              },
            minDistanceM = proto.min_distance_m,
          )
        SamplingDesign.Method.CLUSTER ->
          PlotLayout.Cluster(
            clusterCount = proto.count,
            plotsPerSide = proto.cluster_plots_per_side,
            plotSpacingM = proto.cluster_plot_spacing_m,
            minDistanceM = proto.min_distance_m,
          )
        SamplingDesign.Method.METHOD_UNSPECIFIED -> return null
      }
    return SampleDesign(
      layout = layout,
      plotShape =
        when (proto.plot_shape) {
          SamplingDesign.PlotShape.CIRCLE -> PlotShape.CIRCLE
          SamplingDesign.PlotShape.POINT -> PlotShape.POINT
          SamplingDesign.PlotShape.SQUARE,
          SamplingDesign.PlotShape.PLOT_SHAPE_UNSPECIFIED -> PlotShape.SQUARE
        },
      plotSizeM = proto.plot_size_m,
      subPlot = subPlotFromProto(proto.sub_plot),
      shuffle = proto.shuffle,
      seed = proto.seed,
    )
  }

  private fun subPlotToProto(layout: SubPlotLayout): SubPlotDesign =
    when (layout) {
      SubPlotLayout.None -> SubPlotDesign(mode = SubPlotDesign.Mode.NONE)
      SubPlotLayout.Center -> SubPlotDesign(mode = SubPlotDesign.Mode.CENTER)
      is SubPlotLayout.Grid ->
        SubPlotDesign(
          mode = SubPlotDesign.Mode.GRID,
          grid_n = layout.n,
          spacing_m = layout.spacingM,
        )
      is SubPlotLayout.Random ->
        SubPlotDesign(mode = SubPlotDesign.Mode.RANDOM, count = layout.count)
    }

  private fun subPlotFromProto(proto: SubPlotDesign?): SubPlotLayout =
    when (proto?.mode) {
      SubPlotDesign.Mode.CENTER -> SubPlotLayout.Center
      SubPlotDesign.Mode.GRID -> SubPlotLayout.Grid(proto.grid_n, proto.spacing_m)
      SubPlotDesign.Mode.RANDOM -> SubPlotLayout.Random(proto.count)
      SubPlotDesign.Mode.NONE,
      SubPlotDesign.Mode.USER_DRAWN,
      SubPlotDesign.Mode.MODE_UNSPECIFIED,
      null -> SubPlotLayout.None
    }
}
