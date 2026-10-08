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
package org.groundplatform.v2.devtools.prototypeapp.ui.map

import androidx.compose.ui.graphics.Color
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImagerySource
import org.groundplatform.v2.map.Basemap
import org.groundplatform.v2.map.RasterTileLayer

/**
 * The prototype's basemaps: Esri's public raster tiles, which the web build can show without a
 * Mapbox access token (Mapbox GL JS v1 only needs one for `mapbox://` resources). Fine for a local
 * prototype; a production app should use a basemap its terms allow, such as
 * [Basemap.MapboxOutdoors].
 */
internal object SurveyBasemaps {
  private const val ESRI = "https://server.arcgisonline.com/ArcGIS/rest/services"

  val Streets =
    Basemap.RasterTiles(
      listOf(RasterTileLayer("$ESRI/World_Topo_Map/MapServer/tile/{z}/{y}/{x}", opacity = 0.95f)),
      attribution = "Powered by Esri · Esri, HERE, Garmin, © OpenStreetMap contributors",
      backgroundColor = Color(0xFFE3EFE6),
    )

  val Satellite =
    Basemap.RasterTiles(
      listOf(
        RasterTileLayer(
          "$ESRI/World_Imagery/MapServer/tile/{z}/{y}/{x}",
          contrast = 0.08f,
          saturation = 0.05f,
        ),
        RasterTileLayer(
          "$ESRI/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}",
          opacity = 0.85f,
        ),
      ),
      attribution = "Powered by Esri · Esri, Maxar, Earthstar Geographics",
      backgroundColor = Color(0xFF142E21),
    )

  /**
   * Appends any enabled organization [imagerySources] (`"All users"` and survey-specific
   * organization XYZ tile URLs) on top of [base] so they render as raster tile layers.
   */
  fun withImagerySources(
    base: Basemap.RasterTiles,
    imagerySources: List<ImagerySource>,
  ): Basemap.RasterTiles {
    if (imagerySources.isEmpty()) return base
    val extraLayers = imagerySources.map { source ->
      RasterTileLayer(urlTemplate = source.urlTemplate, opacity = 0.88f)
    }
    return base.copy(layers = base.layers + extraLayers)
  }
}
