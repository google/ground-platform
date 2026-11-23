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
package org.openforis.collectone.v2.app.data.seed.backend

import org.openforis.collectone.v2.app.domain.model.LayerSourceType
import org.openforis.collectone.v2.app.domain.model.MapLayerItem
import org.openforis.collectone.v2.map.GeometryType

/** Hardcoded sample map layers and offline tile packages for the sample data. */
object BackendStaticMapLayersData {
  /** Returns the preloaded [MapLayerItem] list for [surveyId]. */
  fun mapLayersForSurvey(surveyId: String): List<MapLayerItem> =
    when (surveyId) {
      "00000036-1a1c-5c51-997f-28b67d813aa3" ->
        listOf(
          MapLayerItem(
            id = "layer-land-use-observations",
            label = "Land Use Observations",
            colorHex = 0xFF2E7D32,
            geometryType = GeometryType.POINT,
            isVisible = true,
            sourceType = LayerSourceType.ENTITY_DATASET,
            singularNoun = "land-use observation",
            pluralNoun = "land-use observations",
            datasetId = "land_use_observations",
            iconName = "landscape",
          )
        )
      "00000034-c088-5ab9-8b98-e6bccb3dca71" ->
        listOf(
          MapLayerItem(
            id = "layer-sample-plots",
            label = "Permanent Forest Sample Plots",
            colorHex = 0xFF1B5E20,
            geometryType = GeometryType.POLYGON,
            isVisible = true,
            sourceType = LayerSourceType.ENTITY_DATASET,
            singularNoun = "sample plot",
            pluralNoun = "sample plots",
            datasetId = "sample_plots",
            iconName = "forest",
          )
        )
      "00000031-ffc6-5c8e-934c-d14ec07533ad" ->
        listOf(
          MapLayerItem(
            id = "layer-commodity-plots",
            label = "Commodity Plots (EUDR)",
            colorHex = 0xFFD84315,
            geometryType = GeometryType.POLYGON,
            isVisible = true,
            sourceType = LayerSourceType.ENTITY_DATASET,
            singularNoun = "commodity plot",
            pluralNoun = "commodity plots",
            datasetId = "commodity_plots",
            iconName = "agriculture",
          )
        )
      "00000032-8d62-5717-8074-f4917e0cd76d" ->
        listOf(
          MapLayerItem(
            id = "layer-past-individuals",
            label = "Preloaded Past Household Individuals",
            colorHex = 0xFF6A1B9A,
            geometryType = GeometryType.POINT,
            isVisible = true,
            sourceType = LayerSourceType.ENTITY_DATASET,
            singularNoun = "past individual",
            pluralNoun = "past individuals",
            datasetId = "past_individuals",
            iconName = "person",
          )
        )
      "00000037-7e4a-5b2c-9d18-3f8a61c4e902" ->
        listOf(
          MapLayerItem(
            id = "layer-interpretation-plots",
            label = "Satellite Interpretation Sample Plots",
            colorHex = 0xFF00897B,
            geometryType = GeometryType.POLYGON,
            isVisible = true,
            sourceType = LayerSourceType.ENTITY_DATASET,
            singularNoun = "interpretation plot",
            pluralNoun = "interpretation plots",
            datasetId = "interpretation_plots",
            iconName = "forest",
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
        colorHex = 0xFF6D4C41,
        geometryType = GeometryType.POLYGON,
        isVisible = true,
        sourceType = LayerSourceType.ENTITY_DATASET,
        singularNoun = "coffee parcel",
        pluralNoun = "coffee parcels",
        datasetId = "coffee_parcels",
        iconName = "eco",
      ),
      MapLayerItem(
        id = "layer-shade-transects",
        label = "Shade Tree Monitoring Plots",
        colorHex = 0xFF2E7D32,
        geometryType = GeometryType.LINE_STRING,
        isVisible = true,
        sourceType = LayerSourceType.ENTITY_DATASET,
        singularNoun = "monitoring plot",
        pluralNoun = "monitoring plots",
        datasetId = "shade_monitoring_plots",
        iconName = "park",
      ),
      MapLayerItem(
        id = "layer-water-points",
        label = "Cooperative Washing Stations",
        colorHex = 0xFF0277BD,
        geometryType = GeometryType.POINT,
        isVisible = true,
        sourceType = LayerSourceType.ENTITY_DATASET,
        singularNoun = "washing station",
        pluralNoun = "washing stations",
        datasetId = "washing_stations",
        iconName = "water_drop",
      ),
    )
}
