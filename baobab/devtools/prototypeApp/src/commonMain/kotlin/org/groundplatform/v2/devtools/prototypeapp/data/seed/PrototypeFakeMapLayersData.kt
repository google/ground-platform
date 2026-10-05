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
package org.groundplatform.v2.devtools.prototypeapp.data.seed

import org.groundplatform.v2.devtools.prototypeapp.domain.model.LayerSourceType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapLayerItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OfflineTilePackageItem

/** Hardcoded sample map layers and offline tile packages for the prototype app. */
internal object PrototypeFakeMapLayersData {
  /** Returns the preloaded [MapLayerItem] list for [surveyId]. */
  fun mapLayersForSurvey(surveyId: String): List<MapLayerItem> =
    when (surveyId) {
      "survey-single-point-land-use" ->
        listOf(
          MapLayerItem(
            id = "layer-land-use-observations",
            label = "Land Use Observations",
            sourceDescription = "Dataset: land_use_observations (Point)",
            colorHex = 0xFF2E7D32,
            geometryTypeLabel = "Point",
            isVisible = true,
            sourceType = LayerSourceType.ENTITY_DATASET,
            singularItemLabel = "land-use observation",
            pluralItemLabel = "land-use observations",
            datasetId = "land_use_observations",
            iconName = "landscape",
          )
        )
      "survey-sample-plots-forest" ->
        listOf(
          MapLayerItem(
            id = "layer-sample-plots",
            label = "Permanent Forest Sample Plots",
            sourceDescription = "Dataset: sample_plots (Polygon, SP-01 to SP-05)",
            colorHex = 0xFF1B5E20,
            geometryTypeLabel = "Polygon",
            isVisible = true,
            sourceType = LayerSourceType.ENTITY_DATASET,
            singularItemLabel = "sample plot",
            pluralItemLabel = "sample plots",
            datasetId = "sample_plots",
            iconName = "forest",
          )
        )
      "survey-commodity-perimeter-center" ->
        listOf(
          MapLayerItem(
            id = "layer-commodity-plots",
            label = "Commodity Plots (EUDR)",
            sourceDescription = "Dataset: commodity_plots (Polygon)",
            colorHex = 0xFFD84315,
            geometryTypeLabel = "Polygon",
            isVisible = true,
            sourceType = LayerSourceType.ENTITY_DATASET,
            singularItemLabel = "commodity plot",
            pluralItemLabel = "commodity plots",
            datasetId = "commodity_plots",
            iconName = "agriculture",
          )
        )
      "survey-household-past-individuals" ->
        listOf(
          MapLayerItem(
            id = "layer-past-individuals",
            label = "Preloaded Past Household Individuals",
            sourceDescription = "Dataset: past_individuals (Point, IND-101 to IND-106)",
            colorHex = 0xFF6A1B9A,
            geometryTypeLabel = "Point",
            isVisible = true,
            sourceType = LayerSourceType.ENTITY_DATASET,
            singularItemLabel = "past individual",
            pluralItemLabel = "past individuals",
            datasetId = "past_individuals",
            iconName = "person",
          )
        )
      else -> defaultMapLayers()
    }

  /**
   * Default map layer definitions (`LayerDef` inside `SurveyDef.map_config.layers`) backed by
   * geospatial entity datasets (`LayerDef.entity_dataset_id`).
   */
  fun defaultMapLayers(): List<MapLayerItem> =
    listOf(
      // Survey Dataset Layers (solid outlines)
      MapLayerItem(
        id = "layer-coffee-parcels",
        label = "Smallholder Coffee Parcels",
        sourceDescription = "Dataset: coffee_parcels (Polygon)",
        colorHex = 0xFF6D4C41,
        geometryTypeLabel = "Polygon",
        isVisible = true,
        sourceType = LayerSourceType.ENTITY_DATASET,
        singularItemLabel = "coffee parcel",
        pluralItemLabel = "coffee parcels",
        datasetId = "coffee_parcels",
        iconName = "eco",
      ),
      MapLayerItem(
        id = "layer-shade-transects",
        label = "Shade Tree Monitoring Plots",
        sourceDescription = "Dataset: shade_monitoring_plots (LineString)",
        colorHex = 0xFF2E7D32,
        geometryTypeLabel = "LineString",
        isVisible = true,
        sourceType = LayerSourceType.ENTITY_DATASET,
        singularItemLabel = "monitoring plot",
        pluralItemLabel = "monitoring plots",
        datasetId = "shade_monitoring_plots",
        iconName = "park",
      ),
      MapLayerItem(
        id = "layer-water-points",
        label = "Cooperative Washing Stations",
        sourceDescription = "Dataset: washing_stations (Point)",
        colorHex = 0xFF0277BD,
        geometryTypeLabel = "Point",
        isVisible = true,
        sourceType = LayerSourceType.ENTITY_DATASET,
        singularItemLabel = "washing station",
        pluralItemLabel = "washing stations",
        datasetId = "washing_stations",
        iconName = "water_drop",
      ),
    )

  /** Sample Mapbox vector & satellite raster tile packages for `Offline maps`. */
  fun defaultOfflineTilePackages(): List<OfflineTilePackageItem> =
    listOf(
      OfflineTilePackageItem(
        id = "tiles-nyeri-vector",
        regionName = "Nyeri & Mt. Kenya West Vector Basemap",
        tileTypeLabel = "Vector Tiles (Contours & Roads)",
        zoomRangeLabel = "Zoom 10–18",
        sizeLabel = "14.2 MB",
        isDownloaded = true,
      ),
      OfflineTilePackageItem(
        id = "tiles-nyeri-satellite",
        regionName = "Othaya & Chinga High-Res Satellite Imagery",
        tileTypeLabel = "Satellite Raster Tiles",
        zoomRangeLabel = "Zoom 12–19",
        sizeLabel = "68.5 MB",
        isDownloaded = true,
      ),
      OfflineTilePackageItem(
        id = "tiles-kirinyaga-east",
        regionName = "Kirinyaga Neighboring Cooperative Sector",
        tileTypeLabel = "Hybrid Vector + Raster Tiles",
        zoomRangeLabel = "Zoom 11–18",
        sizeLabel = "42.0 MB",
        isDownloaded = false,
      ),
    )
}
