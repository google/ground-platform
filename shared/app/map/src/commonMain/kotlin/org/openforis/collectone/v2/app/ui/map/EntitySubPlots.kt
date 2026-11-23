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
package org.openforis.collectone.v2.app.ui.map

import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.MapLayerItem
import org.openforis.collectone.v2.app.domain.model.editor.SamplePlotProperties
import org.openforis.collectone.v2.app.domain.model.editor.toGeoCoord
import org.openforis.collectone.v2.app.domain.model.editor.toLatLng
import org.openforis.collectone.v2.app.domain.model.geometryType
import org.openforis.collectone.v2.app.domain.model.maxFramingZoom
import org.openforis.collectone.v2.core.sampling.DefaultSamplingEngine
import org.openforis.collectone.v2.core.sampling.PlotGeometry
import org.openforis.collectone.v2.core.sampling.PlotShape
import org.openforis.collectone.v2.core.sampling.Ring
import org.openforis.collectone.v2.core.sampling.SampleDesign
import org.openforis.collectone.v2.core.sampling.SampleEncoding
import org.openforis.collectone.v2.core.sampling.SubPlotLayout
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng

/**
 * Resolves and constructs ground-scaled hollow square sub-plot sample boxes for a selected map
 * feature on the survey map, sharing [PlotGeometry.subPlotSizeM] and [PlotGeometry.subPlotBox] with
 * Google Earth KML export.
 */
internal object EntitySubPlots {
  /** Default ground side length in meters when a layer has no sample design. */
  const val DEFAULT_SIZE_M = PlotGeometry.DEFAULT_SUBPLOT_SIZE_M

  /** Maximum camera zoom when framing a selected polygon plot that has sub-plots. */
  const val SUBPLOT_MAX_FRAMING_ZOOM = 19.0

  /**
   * Sub-plot sample centers for [entity] on [layer], or empty when the feature has no sub-plots (or
   * its layer's sampling design disables them). Prefers `entity.properties["samples"]` and falls
   * back to synthesizing deterministically from [MapLayerItem.sampleDesign].
   */
  fun resolve(entity: GeospatialEntityItem, layer: MapLayerItem?): List<LatLng> {
    val design = designFor(entity, layer)
    if (design != null && isDisabledByDesign(design)) return emptyList()

    val explicit =
      entity.properties[SamplePlotProperties.SAMPLES]
        ?.takeIf { it.isNotBlank() }
        ?.let(SampleEncoding::decodeSamples)
        .orEmpty()
    if (explicit.isNotEmpty()) return explicit.map { it.toLatLng() }

    return if (design != null) synthesizeFromDesign(entity, design) else emptyList()
  }

  /** True when [entity] on [layer] has at least one sub-plot sample point to draw. */
  fun hasSubPlots(entity: GeospatialEntityItem, layer: MapLayerItem?): Boolean =
    resolve(entity, layer).isNotEmpty()

  /**
   * Maximum camera zoom when framing [entity] on [layer]: zooms closer ([SUBPLOT_MAX_FRAMING_ZOOM])
   * for polygon plots with sub-plots so small plots (e.g. 30 m × 30 m) show their sample boxes
   * clearly, keeping the default [GeometryType.maxFramingZoom] otherwise.
   */
  fun maxFramingZoom(entity: GeospatialEntityItem, layer: MapLayerItem?): Double =
    if (entity.geometryType == GeometryType.POLYGON && hasSubPlots(entity, layer)) {
      SUBPLOT_MAX_FRAMING_ZOOM
    } else {
      entity.geometryType.maxFramingZoom.toDouble()
    }

  /** Ground side length in meters of each hollow square sub-plot box (shared with KML export). */
  fun sizeM(design: SampleDesign?, samples: List<LatLng>): Double =
    PlotGeometry.subPlotSizeM(design, samples.map { it.toGeoCoord() })

  /** Open 4-corner ring of a ground-scaled square [sizeM] meters wide centered on [center]. */
  fun groundSquare(center: LatLng, sizeM: Double): List<LatLng> {
    val closed = PlotGeometry.subPlotBox(center.toGeoCoord(), sizeM)
    val open =
      if (closed.size > 1 && closed.first() == closed.last()) closed.dropLast(1) else closed
    return open.map { it.toLatLng() }
  }

  private fun designFor(entity: GeospatialEntityItem, layer: MapLayerItem?): SampleDesign? =
    layer
      ?.takeIf {
        it.id == entity.layerId ||
          (it.datasetId != null &&
            entity.datasetId.isNotBlank() &&
            it.datasetId == entity.datasetId)
      }
      ?.sampleDesign

  private fun isDisabledByDesign(design: SampleDesign): Boolean =
    design.subPlot == SubPlotLayout.None || design.plotShape == PlotShape.POINT

  private fun synthesizeFromDesign(
    entity: GeospatialEntityItem,
    design: SampleDesign,
  ): List<LatLng> {
    val geometry = entity.geometry ?: return emptyList()
    val center = geometry.representativePoint.toGeoCoord()
    val boundary =
      closedOuterRingOf(geometry).ifEmpty {
        PlotGeometry.boundary(center, design.plotShape, design.plotSizeM)
      }
    val plotId =
      entity.properties[SamplePlotProperties.PLOT_ID]?.takeIf { it.isNotBlank() }
        ?: entity.geoId.takeIf { it.isNotBlank() }
        ?: entity.id
    val plotSeed = DefaultSamplingEngine.subPlotSeed(design.seed, plotId)
    return PlotGeometry.subPlotSamples(
        center = center,
        boundary = boundary,
        shape = design.plotShape,
        sizeM = design.plotSizeM,
        layout = design.subPlot,
        plotSeed = plotSeed,
      )
      .map { it.toLatLng() }
  }

  private fun closedOuterRingOf(geometry: Geometry): Ring {
    val polygon = geometry.parts.firstOrNull() as? Geometry.Polygon ?: return emptyList()
    val open = polygon.rings.firstOrNull().orEmpty().map { it.toGeoCoord() }
    if (open.size < 3) return emptyList()
    return if (open.first() == open.last()) open else open + open.first()
  }
}
