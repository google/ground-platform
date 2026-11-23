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
package org.openforis.collectone.v2.core.geo.io

import kotlin.math.roundToInt
import openforis.collectone.v1.data.Geometry
import openforis.collectone.v1.data.GeometryType
import openforis.collectone.v1.data.Position
import org.openforis.collectone.v2.core.geo.GeoCoord
import org.openforis.collectone.v2.core.geo.GeometryProtos
import org.openforis.collectone.v2.core.sampling.DefaultSamplingEngine
import org.openforis.collectone.v2.core.sampling.PlotGeometry
import org.openforis.collectone.v2.core.sampling.PlotShape
import org.openforis.collectone.v2.core.sampling.Ring
import org.openforis.collectone.v2.core.sampling.SampleDesign
import org.openforis.collectone.v2.core.sampling.SampleEncoding
import org.openforis.collectone.v2.core.sampling.SubPlotLayout

/**
 * Resolves Collect-Earth-style ground-scaled hollow subplot frames for a plot placemark from its
 * `samples` geotrace property or the layer's [SampleDesign].
 */
internal object KmlSubPlots {
  const val SAMPLES_PROPERTY = "samples"
  const val SAMPLE_IDS_PROPERTY = "sample_ids"
  const val DEFAULT_SUBPLOT_SIZE_M = PlotGeometry.DEFAULT_SUBPLOT_SIZE_M
  const val DEFAULT_SUBPLOT_LOOK_AT_RANGE_M = 300
  private const val MIN_LOOK_AT_RANGE_M = 200
  private const val MAX_LOOK_AT_RANGE_M = 450

  data class SubPlotRing(
    val id: String,
    val sampleId: String,
    val center: GeoCoord,
    val positions: List<Position>,
  )

  data class ResolvedSubPlots(
    val sampleCoords: List<GeoCoord> = emptyList(),
    val sampleIds: List<String> = emptyList(),
    val rings: List<SubPlotRing> = emptyList(),
    val synthesizedProperties: Map<String, String> = emptyMap(),
  ) {
    val isNotEmpty: Boolean
      get() = rings.isNotEmpty()

    companion object {
      val EMPTY = ResolvedSubPlots()
    }
  }

  fun resolve(
    placemark: KmlBalloonWriter.PlacemarkSpec,
    design: SampleDesign?,
  ): ResolvedSubPlots {
    val disabledByDesign =
      design != null &&
        (design.subPlot == SubPlotLayout.None || design.plotShape == PlotShape.POINT)
    if (disabledByDesign && placemark.subPlots.isEmpty()) {
      return ResolvedSubPlots.EMPTY
    }

    val coords = resolveSampleCoords(placemark, design)
    if (coords.isEmpty()) return ResolvedSubPlots.EMPTY

    val rawSampleIds = placemark.properties[SAMPLE_IDS_PROPERTY].orEmpty()
    val decodedIds = SampleEncoding.decodeSampleIds(rawSampleIds)
    val sampleIds =
      if (decodedIds.size == coords.size) {
        decodedIds
      } else {
        SampleEncoding.defaultSampleIds(coords.size)
      }

    val sizeM = PlotGeometry.subPlotSizeM(design, coords)
    val baseId = placemark.id.ifBlank { placemark.geoId.ifBlank { "plot" } }
    val rings = coords.mapIndexed { index, center ->
      val sampleId = sampleIds[index]
      val ring = PlotGeometry.subPlotBox(center, sizeM)
      SubPlotRing(
        id = "$baseId-subplot-$sampleId",
        sampleId = sampleId,
        center = center,
        positions = ring.map { GeometryProtos.position(it.lat, it.lng) },
      )
    }

    val synthesized = buildMap {
      if (placemark.properties[SAMPLES_PROPERTY].isNullOrBlank()) {
        put(SAMPLES_PROPERTY, SampleEncoding.encodeSamples(coords))
      }
      if (placemark.properties[SAMPLE_IDS_PROPERTY].isNullOrBlank()) {
        put(SAMPLE_IDS_PROPERTY, SampleEncoding.encodeSampleIds(sampleIds))
      }
    }
    return ResolvedSubPlots(
      sampleCoords = coords,
      sampleIds = sampleIds,
      rings = rings,
      synthesizedProperties = synthesized,
    )
  }

  /**
   * Ground side length in meters of each hollow square subplot box (delegates to
   * [PlotGeometry.subPlotSizeM] so Google Earth and Collect One draw identical boxes).
   */
  fun subPlotSizeM(design: SampleDesign?): Double = PlotGeometry.subPlotSizeM(design)

  /** Camera `<LookAt><range>` framing both the plot boundary and its internal subplots. */
  fun lookAtRange(design: SampleDesign?): Int {
    val sizeM = design?.plotSizeM?.takeIf { it.isFinite() && it > 0.0 }
    return sizeM?.let { (it * 4.0).roundToInt().coerceIn(MIN_LOOK_AT_RANGE_M, MAX_LOOK_AT_RANGE_M) }
      ?: DEFAULT_SUBPLOT_LOOK_AT_RANGE_M
  }

  private fun resolveSampleCoords(
    placemark: KmlBalloonWriter.PlacemarkSpec,
    design: SampleDesign?,
  ): List<GeoCoord> {
    val preResolved =
      placemark.subPlots.ifEmpty {
        placemark.properties[SAMPLES_PROPERTY]
          ?.takeIf { it.isNotBlank() }
          ?.let(SampleEncoding::decodeSamples)
          .orEmpty()
      }
    return when {
      preResolved.isNotEmpty() -> preResolved
      design == null ||
        design.subPlot == SubPlotLayout.None ||
        design.plotShape == PlotShape.POINT -> emptyList()
      else -> synthesizeFromDesign(placemark, design)
    }
  }

  private fun synthesizeFromDesign(
    placemark: KmlBalloonWriter.PlacemarkSpec,
    design: SampleDesign,
  ): List<GeoCoord> {
    val centerPos =
      placemark.geometry?.let(GeometryProtos::representativePosition) ?: return emptyList()
    val center = GeoCoord(centerPos.latitude, centerPos.longitude)
    val boundary =
      outerRingOf(placemark.geometry).ifEmpty {
        PlotGeometry.boundary(center, design.plotShape, design.plotSizeM)
      }
    val plotId =
      placemark.properties["plot_id"]?.takeIf { it.isNotBlank() }
        ?: placemark.geoId.takeIf { it.isNotBlank() }
        ?: placemark.id
    val plotSeed = DefaultSamplingEngine.subPlotSeed(design.seed, plotId)
    return PlotGeometry.subPlotSamples(
      center = center,
      boundary = boundary,
      shape = design.plotShape,
      sizeM = design.plotSizeM,
      layout = design.subPlot,
      plotSeed = plotSeed,
    )
  }

  private fun outerRingOf(geometry: Geometry): Ring =
    when (GeometryProtos.typeOf(geometry)) {
      GeometryType.GEOMETRY_TYPE_POLYGON ->
        geometry.polygon?.rings?.firstOrNull()?.positions.orEmpty().map {
          GeoCoord(it.latitude, it.longitude)
        }
      GeometryType.GEOMETRY_TYPE_MULTI_POLYGON ->
        geometry.multi_polygon
          ?.polygons
          ?.firstOrNull()
          ?.rings
          ?.firstOrNull()
          ?.positions
          .orEmpty()
          .map { GeoCoord(it.latitude, it.longitude) }
      else -> emptyList()
    }
}
