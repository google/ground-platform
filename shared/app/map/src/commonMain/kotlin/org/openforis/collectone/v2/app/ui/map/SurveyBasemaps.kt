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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.openforis.collectone.v2.app.domain.model.ImagerySource
import org.openforis.collectone.v2.app.domain.model.ImagerySourceType
import org.openforis.collectone.v2.map.Basemap
import org.openforis.collectone.v2.map.LineEmphasis
import org.openforis.collectone.v2.map.RasterTileLayer
import org.openforis.collectone.v2.map.VectorFeatureFilter
import org.openforis.collectone.v2.map.VectorLineOverlay

/**
 * The survey map's basemaps: hosted Mapbox styles, which every platform renders with the Mapbox
 * access token its host app configures. [Satellite] carries its own place and boundary labels.
 */
object SurveyBasemaps {
  val Streets = Basemap.MapboxOutdoors.copy(backgroundColor = Color(0xFFE3EFE6))

  val Satellite = Basemap.MapboxSatelliteStreets

  /** The Mapbox satellite tileset, the one the Satellite basemap draws. */
  const val SATELLITE_TILESET = "mapbox://mapbox.satellite"

  /**
   * The composite vector tileset of the Terrain basemap (`outdoors-v12`); overlays use the exact
   * URL, so saved terrain tiles serve them offline.
   */
  const val TERRAIN_TILESET =
    "mapbox://mapbox.mapbox-streets-v8,mapbox.mapbox-terrain-v2,mapbox.mapbox-bathymetry-v2"

  /** The zoom levels saved areas cover (see `OfflineMapLimits`). */
  private const val SAVED_MIN_ZOOM = 6
  private const val SAVED_MAX_ZOOM = 16

  /** Contour lines over satellite imagery: a light color that reads on it. */
  private val ContourColor = Color(0xFFFFE6C2)

  /**
   * Adds what [base] can't show of the saved areas in [imagery]: over the Terrain basemap, the
   * satellite imagery inside each saved satellite area; over Satellite, the contour lines inside
   * each saved terrain area (index contours thicker). Each overlay is bounded to its area.
   */
  fun withSavedImagery(base: Basemap.MapboxStyle, imagery: SavedImageryOnMap): Basemap.MapboxStyle {
    val overlays =
      if (base == Satellite) {
        if (imagery.drawTerrain) imagery.terrain.map(::contourOverlay) else emptyList()
      } else {
        if (imagery.drawSatellite) imagery.satellite.map(::satelliteOverlay) else emptyList()
      }
    return if (overlays.isEmpty()) base else base.copy(overlays = base.overlays + overlays)
  }

  private fun satelliteOverlay(area: SavedImageryArea) =
    RasterTileLayer(
      tilesetUrl = SATELLITE_TILESET,
      tileSize = 256,
      minZoom = SAVED_MIN_ZOOM,
      maxZoom = SAVED_MAX_ZOOM,
      bounds = area.bounds,
    )

  private fun contourOverlay(area: SavedImageryArea) =
    VectorLineOverlay(
      tilesetUrl = TERRAIN_TILESET,
      sourceLayer = "contour",
      color = ContourColor,
      width = 0.8.dp,
      emphasis = LineEmphasis(VectorFeatureFilter.NumberIn("index", setOf(5, 10)), 1.6.dp),
      opacity = 0.7f,
      bounds = area.bounds,
      minZoom = SAVED_MIN_ZOOM,
    )

  /**
   * Appends any enabled organization [imagerySources] (`"All users"` and survey-specific
   * organization XYZ tile URLs, plus resolved Earth Engine tile URLs from [resolvedTileUrls]) on
   * top of [base] as raster tile overlays. Unresolved Earth Engine layers are safely skipped.
   */
  fun withImagerySources(
    base: Basemap.MapboxStyle,
    imagerySources: List<ImagerySource>,
    resolvedTileUrls: Map<String, String> = emptyMap(),
  ): Basemap.MapboxStyle {
    if (imagerySources.isEmpty()) return base
    val extraLayers = imagerySources.mapNotNull { source ->
      val template =
        when (source.type) {
          ImagerySourceType.EARTH_ENGINE ->
            (resolvedTileUrls[source.id] ?: source.urlTemplate).takeIf { it.isNotBlank() }
          ImagerySourceType.XYZ_TILES -> source.urlTemplate.takeIf { it.isNotBlank() }
        }
      template?.let { RasterTileLayer(urlTemplate = it, opacity = 0.88f) }
    }
    return base.copy(overlays = base.overlays + extraLayers)
  }
}
