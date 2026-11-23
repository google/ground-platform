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
package org.openforis.collectone.v2.web.ui.surveyeditor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.SampleDesignConfig
import org.openforis.collectone.v2.app.domain.model.editor.SamplePlotProperties
import org.openforis.collectone.v2.app.domain.model.editor.toGeoCoord
import org.openforis.collectone.v2.app.domain.model.editor.toLatLng
import org.openforis.collectone.v2.app.domain.model.formatHexColorCss
import org.openforis.collectone.v2.app.ui.map.AppPin
import org.openforis.collectone.v2.core.sampling.PlotGeometry
import org.openforis.collectone.v2.map.Basemap
import org.openforis.collectone.v2.map.FeatureFilter
import org.openforis.collectone.v2.map.GeoJsonSource
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.map.MapContent
import org.openforis.collectone.v2.map.MapFeature
import org.openforis.collectone.v2.map.MapIcon
import org.openforis.collectone.v2.map.MapLayer
import org.openforis.collectone.v2.map.MarkerAnchor
import org.openforis.collectone.v2.map.StyleValue

// The map content (sources, style layers, and overlays) the Map layer editor's map draws. Pure
// functions of the layer and its selection, kept apart from the composable so they can be tested.

internal const val LAYER_EDITOR_SOURCE = "layer-editor-features"
/** Feature property marking the selected row (`"true"` or `"false"`). */
internal const val LAYER_EDITOR_SELECTED = "selected"

internal const val LAYER_EDITOR_CENTROIDS_SOURCE = "layer-editor-centroids"

/**
 * What [InteractiveLayerMapCard] draws with the map's style layers: every row of [dataset] as a
 * feature styled like the layer, with the [selectedRow] haloed and on top.
 *
 * Non-point features also emit a centroid point in [LAYER_EDITOR_CENTROIDS_SOURCE] rendered with a
 * [MapLayer.Circle] so small polygons (such as 30 m × 30 m sample plots spaced across a region or
 * country) remain visible at low and medium zoom levels where Mapbox's tile simplification drops
 * sub-pixel polygon rings.
 */
internal fun layerEditorContent(
  dataset: EntityDataset,
  selectedRow: String?,
  layerColor: Color,
  basemap: Basemap,
): MapContent {
  val sortedRows = dataset.rows.sortedBy { it.key == selectedRow }
  val features = sortedRows.mapNotNull { row ->
    val geometry = row.geometry.toMapGeometry(dataset.geometryType) ?: return@mapNotNull null
    MapFeature(
      id = row.key,
      geometry = geometry,
      properties = mapOf(LAYER_EDITOR_SELECTED to (row.key == selectedRow).toString()),
    )
  }
  val centroidFeatures =
    if (dataset.geometryType == GeometryType.POINT) {
      emptyList()
    } else {
      sortedRows.mapNotNull { row ->
        val center = row.geometry.centroidOrNull() ?: return@mapNotNull null
        MapFeature(
          id = row.key,
          geometry = Geometry.Point(center),
          properties = mapOf(LAYER_EDITOR_SELECTED to (row.key == selectedRow).toString()),
        )
      }
    }
  val pinColor = formatHexColorCss(layerColor.toArgb().toLong())
  val iconName = dataset.style.iconName
  val pin =
    MapIcon(
      AppPin.iconId(pinColor, iconName, isPending = false),
      AppPin.svg(pinColor, iconName, isPending = false),
    )
  return MapContent(
    basemap = basemap,
    sources =
      listOf(
        GeoJsonSource(LAYER_EDITOR_SOURCE, features),
        GeoJsonSource(LAYER_EDITOR_CENTROIDS_SOURCE, centroidFeatures),
      ),
    layers = layerEditorStyleLayers(dataset, layerColor, pin),
    icons = listOf(pin),
  )
}

/**
 * The style layers of [layerEditorContent]: fills, a halo under the selected feature, casings and
 * lines, the centroid dots, and [pin] for point features.
 */
private fun layerEditorStyleLayers(
  dataset: EntityDataset,
  layerColor: Color,
  pin: MapIcon,
): List<MapLayer> {
  val stroke = dataset.style.strokeWidth.toFloat().dp
  val src = LAYER_EDITOR_SOURCE
  val points = FeatureFilter.GeometryTypeIs(GeometryType.POINT)
  val notPoints = FeatureFilter.Not(points)
  val isSelected = FeatureFilter.Equals(LAYER_EDITOR_SELECTED, "true")
  val casing = Color.Black.copy(alpha = 0.35f)
  val halo = Color.White.copy(alpha = 0.9f)
  return listOf(
    MapLayer.Fill(
      id = "layer-editor-fill",
      sourceId = src,
      color = StyleValue.Constant(layerColor),
      opacity = StyleValue.Constant(dataset.style.fillOpacity.toFloat()),
    ),
    MapLayer.Line(
      id = "layer-editor-halo",
      sourceId = src,
      filter = FeatureFilter.All(listOf(notPoints, isSelected)),
      color = StyleValue.Constant(halo),
      width = StyleValue.Constant(stroke + 6.dp),
    ),
    MapLayer.Line(
      id = "layer-editor-casing",
      sourceId = src,
      filter = notPoints,
      color = StyleValue.Constant(casing),
      width = StyleValue.Constant(stroke + 2.dp),
    ),
    MapLayer.Line(
      id = "layer-editor-line",
      sourceId = src,
      filter = notPoints,
      color = StyleValue.Constant(layerColor),
      width = StyleValue.Constant(stroke),
    ),
    MapLayer.Circle(
      id = "layer-editor-centroid",
      sourceId = LAYER_EDITOR_CENTROIDS_SOURCE,
      color = StyleValue.Constant(layerColor),
      radius = StyleValue.Match(LAYER_EDITOR_SELECTED, mapOf("true" to 6.dp), 4.dp),
      strokeColor = StyleValue.Constant(Color.White),
      strokeWidth = 1.5.dp,
    ),
    // Points are drawn as the same app pin the survey map uses: layer color and icon.
    MapLayer.Symbol(
      id = "layer-editor-point",
      sourceId = src,
      filter = points,
      iconId = StyleValue.Constant(pin.id),
      iconSize = StyleValue.Match(LAYER_EDITOR_SELECTED, mapOf("true" to 1.85f), 1.5f),
      iconAnchor = MarkerAnchor.BOTTOM,
      // Moves the pin down so its tip, not the bottom of its shadow, sits on the location.
      iconOffset = DpOffset(0.dp, (AppPin.HEIGHT - AppPin.TIP_Y).toFloat().dp),
    ),
  )
}

internal const val LAYER_EDITOR_BACKDROP_SOURCE = "layer-editor-backdrop"

/**
 * Draws [polygons] (such as the survey area boundary) underneath the layer's own features so the
 * organizer has spatial context while previewing or editing map features.
 */
internal fun withBackdropPolygons(
  content: MapContent,
  polygons: List<List<LatLng>>,
  outlineColor: Color,
): MapContent {
  val validRings = polygons.filter { it.size >= 3 }
  if (validRings.isEmpty()) return content
  val features = validRings.mapIndexed { idx, ring ->
    MapFeature(
      id = "backdrop-${idx + 1}",
      geometry = Geometry.Polygon(listOf(ring.map { it })),
    )
  }
  val backdropLayers =
    listOf(
      MapLayer.Fill(
        id = "layer-editor-backdrop-fill",
        sourceId = LAYER_EDITOR_BACKDROP_SOURCE,
        color = StyleValue.Constant(outlineColor),
        opacity = StyleValue.Constant(0.08f),
      ),
      MapLayer.Line(
        id = "layer-editor-backdrop-casing",
        sourceId = LAYER_EDITOR_BACKDROP_SOURCE,
        color = StyleValue.Constant(Color.White.copy(alpha = 0.7f)),
        width = StyleValue.Constant(3.dp),
      ),
      MapLayer.Line(
        id = "layer-editor-backdrop-line",
        sourceId = LAYER_EDITOR_BACKDROP_SOURCE,
        color = StyleValue.Constant(outlineColor),
        width = StyleValue.Constant(1.75.dp),
      ),
    )
  return content.copy(
    sources = listOf(GeoJsonSource(LAYER_EDITOR_BACKDROP_SOURCE, features)) + content.sources,
    layers = backdropLayers + content.layers,
  )
}

internal const val LAYER_EDITOR_SAMPLES_SOURCE = "layer-editor-samples"

/** Maximum number of subplot sample point rings drawn at once on the preview map. */
internal const val MAX_VISIBLE_PLOT_SAMPLES = 2_500

/**
 * The sample points of plot [selectedRow] in [dataset], or none when no plot (or a plot that no
 * longer exists) is selected.
 */
internal fun selectedPlotSamples(dataset: EntityDataset, selectedRow: String?): List<LatLng> {
  if (selectedRow == null) return emptyList()
  val row = dataset.rows.firstOrNull { it.key == selectedRow } ?: return emptyList()
  return SamplePlotProperties.parseGeotrace(row.values[SamplePlotProperties.SAMPLES].orEmpty())
}

/**
 * Sample points to draw on the preview map for [dataset]: includes [selectedRow]'s decoded sample
 * points first when a plot is selected, and includes decoded sample points across [dataset]'s rows
 * (capped at [maxSamples]) even when [selectedRow] is `null`.
 */
internal fun visiblePlotSamples(
  dataset: EntityDataset,
  selectedRow: String? = null,
  maxSamples: Int = MAX_VISIBLE_PLOT_SAMPLES,
): List<LatLng> {
  if (dataset.rows.isEmpty() || maxSamples <= 0) return emptyList()
  // Lazy, so rows past the cap are never decoded.
  val others =
    dataset.rows
      .asSequence()
      .filter { it.key != selectedRow }
      .mapNotNull { row -> row.values[SamplePlotProperties.SAMPLES]?.takeIf { it.isNotBlank() } }
      .flatMap { SamplePlotProperties.parseGeotrace(it) }
  return (selectedPlotSamples(dataset, selectedRow).asSequence() + others).take(maxSamples).toList()
}

/**
 * [content] plus [samples] (the selected plot's sample points) drawn above everything else. Returns
 * [content] unchanged when there are none.
 *
 * Sample points are hollow square boxes with a fixed side on the ground, [sizeM] meters, so they
 * shrink when zooming out and grow when zooming in, like the plots around them (matching the survey
 * map and Google Earth KML). Each box is a line in [layerColor] over a slightly wider white halo,
 * keeping it legible on both dark and bright imagery while interpreters can still see the imagery
 * inside it.
 */
internal fun withSamplePointsOverlay(
  content: MapContent,
  samples: List<LatLng>,
  layerColor: Color,
  sizeM: Double = DEFAULT_SAMPLE_SIZE_M,
): MapContent {
  if (samples.isEmpty()) return content
  val features = samples.mapIndexed { i, p ->
    MapFeature(
      id = "sample-${i + 1}",
      geometry = Geometry.Polygon(listOf(groundSquare(p, sizeM))),
    )
  }
  return content.copy(
    sources = content.sources + GeoJsonSource(LAYER_EDITOR_SAMPLES_SOURCE, features),
    layers =
      content.layers +
        listOf(
          MapLayer.Line(
            id = "layer-editor-samples-halo",
            sourceId = LAYER_EDITOR_SAMPLES_SOURCE,
            color = StyleValue.Constant(Color.White),
            width = StyleValue.Constant(3.dp),
          ),
          MapLayer.Line(
            id = "layer-editor-samples",
            sourceId = LAYER_EDITOR_SAMPLES_SOURCE,
            color = StyleValue.Constant(layerColor),
            width = StyleValue.Constant(1.5.dp),
          ),
        ),
  )
}

/** Ground side length in meters of sample boxes when a layer has no sample design. */
internal const val DEFAULT_SAMPLE_SIZE_M = PlotGeometry.DEFAULT_SUBPLOT_SIZE_M

/** Ground side length in meters of the sample boxes of [config]'s plots (shared with KML). */
internal fun samplePointSizeM(config: SampleDesignConfig): Double =
  PlotGeometry.subPlotSizeM(config.toSampleDesign())

/** Open 4-corner ring of a ground-scaled square [sizeM] meters wide centered on [center]. */
internal fun groundSquare(center: LatLng, sizeM: Double): List<LatLng> {
  val closed = PlotGeometry.subPlotBox(center.toGeoCoord(), sizeM)
  val open = if (closed.size > 1 && closed.first() == closed.last()) closed.dropLast(1) else closed
  return open.map { it.toLatLng() }
}

/**
 * These vertices as a map geometry of [kind], or the simplest geometry they can form while still
 * being drawn (e.g. a two-vertex polygon renders as a line).
 */
internal fun List<LatLng>.toMapGeometry(kind: GeometryType): Geometry? {
  val pts = map { it }
  return when {
    pts.isEmpty() -> null
    pts.size == 1 || kind == GeometryType.POINT -> Geometry.Point(pts.first())
    kind == GeometryType.POLYGON && pts.size >= 3 -> Geometry.Polygon(listOf(pts))
    else -> Geometry.LineString(pts)
  }
}
