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

import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPlaceItem

/** Hardcoded sample places gazetteer for the prototype app. */
internal object PrototypeFakePlacesData {
  /** Alias for [defaultSurveyPlaces]. */
  fun defaultPlaces(): List<SurveyPlaceItem> = defaultSurveyPlaces()

  /**
   * Default geographic places, towns, landmarks, road junctions, and hydrology features from the
   * Mapbox Places API (`mapbox.places`) searchable via `"Search places or map features..."`.
   */
  fun defaultSurveyPlaces(): List<SurveyPlaceItem> =
    listOf(
      SurveyPlaceItem(
        id = "place-othaya-town",
        name = "Othaya Town Center",
        categoryLabel = "Town",
        regionSubtitle = "Othaya Sub-County, Nyeri • Cooperative Market Hub",
        coordinatesLabel = "0.4192°S, 36.9498°E",
        normalizedX = 0.40f,
        normalizedY = 0.47f,
        zoomDelta = -2.5f,
        longitude = 36.9498,
        latitude = -0.4192,
        bboxMinLng = 36.9220,
        bboxMinLat = -0.4420,
        bboxMaxLng = 36.9780,
        bboxMaxLat = -0.3960,
        mapboxPlaceId = "place.othaya.101",
      ),
      SurveyPlaceItem(
        id = "place-chinga-dam",
        name = "Chinga Dam & Reservoir",
        categoryLabel = "Hydrology / Dam",
        regionSubtitle = "Chinga Ward, Nyeri County • Upper Gura Catchment",
        coordinatesLabel = "0.4258°S, 36.9574°E",
        normalizedX = 0.78f,
        normalizedY = 0.74f,
        zoomDelta = -1.7f,
        longitude = 36.9574,
        latitude = -0.4258,
        bboxMinLng = 36.9430,
        bboxMinLat = -0.4380,
        bboxMaxLng = 36.9720,
        bboxMaxLat = -0.4130,
        mapboxPlaceId = "poi.chinga.102",
      ),
      SurveyPlaceItem(
        id = "place-gura-river-bridge",
        name = "Gura River Crossing",
        categoryLabel = "River Crossing",
        regionSubtitle = "Gura Valley Riparian Corridor • 1,785 m",
        coordinatesLabel = "0.4215°S, 36.9535°E",
        normalizedX = 0.62f,
        normalizedY = 0.56f,
        zoomDelta = 0.2f,
        longitude = 36.9535,
        latitude = -0.4215,
        bboxMinLng = 36.9495,
        bboxMinLat = -0.4255,
        bboxMaxLng = 36.9575,
        bboxMaxLat = -0.4175,
        mapboxPlaceId = "poi.gura.103",
      ),
      SurveyPlaceItem(
        id = "place-karima-forest",
        name = "Karima Hill Forest Reserve",
        categoryLabel = "Forest Reserve",
        regionSubtitle = "Othaya Highlands • Sacred Indigenous Canopy",
        coordinatesLabel = "0.4160°S, 36.9465°E",
        normalizedX = 0.20f,
        normalizedY = 0.22f,
        zoomDelta = -1.7f,
        longitude = 36.9465,
        latitude = -0.4160,
        bboxMinLng = 36.9320,
        bboxMinLat = -0.4280,
        bboxMaxLng = 36.9610,
        bboxMaxLat = -0.4040,
        mapboxPlaceId = "poi.karima.104",
      ),
      SurveyPlaceItem(
        id = "place-nyeri-town",
        name = "Nyeri Town",
        categoryLabel = "Regional Hub",
        regionSubtitle = "Nyeri County Headquarters • Central Highlands",
        coordinatesLabel = "0.4148°S, 36.9510°E",
        normalizedX = 0.49f,
        normalizedY = 0.16f,
        zoomDelta = -3.5f,
        longitude = 36.9510,
        latitude = -0.4148,
        bboxMinLng = 36.8900,
        bboxMinLat = -0.4650,
        bboxMaxLng = 37.0120,
        bboxMaxLat = -0.3650,
        mapboxPlaceId = "place.nyeri.105",
      ),
      SurveyPlaceItem(
        id = "place-iriaini-market",
        name = "Iriaini Coffee & Tea Market",
        categoryLabel = "Village / Market Center",
        regionSubtitle = "Iriaini Ward, Othaya • Smallholder Buying Center",
        coordinatesLabel = "0.4238°S, 36.9478°E",
        normalizedX = 0.26f,
        normalizedY = 0.68f,
        zoomDelta = -0.8f,
        longitude = 36.9478,
        latitude = -0.4238,
        bboxMinLng = 36.9410,
        bboxMinLat = -0.4305,
        bboxMaxLng = 36.9545,
        bboxMaxLat = -0.4170,
        mapboxPlaceId = "poi.iriaini.106",
      ),
      SurveyPlaceItem(
        id = "place-kenya-country",
        name = "Kenya",
        categoryLabel = "Country",
        regionSubtitle = "East Africa • Republic of Kenya",
        coordinatesLabel = "0.0236°N, 37.9062°E",
        normalizedX = 0.50f,
        normalizedY = 0.50f,
        zoomDelta = -10.1f,
        longitude = 37.9062,
        latitude = 0.0236,
        bboxMinLng = 33.9098,
        bboxMinLat = -4.6780,
        bboxMaxLng = 41.8995,
        bboxMaxLat = 5.5060,
        mapboxPlaceId = "country.kenya.01",
      ),
    )
}
