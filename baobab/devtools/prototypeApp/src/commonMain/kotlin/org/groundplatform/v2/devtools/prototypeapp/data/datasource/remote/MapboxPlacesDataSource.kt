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
package org.groundplatform.v2.devtools.prototypeapp.data.datasource.remote

import org.groundplatform.v2.devtools.prototypeapp.client.places.MapboxPlacesClient
import org.groundplatform.v2.devtools.prototypeapp.data.mapper.PlaceJsonMapper
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPlaceItem

/**
 * Remote data source wrapping [MapboxPlacesClient] and mapping raw JSON responses into domain
 * [SurveyPlaceItem] instances via [PlaceJsonMapper].
 */
class MapboxPlacesDataSource(
  private val mapboxPlacesClient: MapboxPlacesClient = MapboxPlacesClient(),
  private val placeJsonMapper: PlaceJsonMapper = PlaceJsonMapper(),
) {
  fun searchPlaces(
    surveyId: String,
    query: String,
    isAirplaneMode: Boolean,
    defaultRegionSubtitle: String,
    centerLongitude: Double,
    centerLatitude: Double,
    onResults: (List<SurveyPlaceItem>) -> Unit,
  ) {
    mapboxPlacesClient.queryPlacesJson(
      surveyId = surveyId,
      query = query,
      isAirplaneMode = isAirplaneMode,
    ) { json ->
      onResults(
        parsePlacesResultsJson(
          json = json,
          defaultRegionSubtitle = defaultRegionSubtitle,
          surveyLng = centerLongitude,
          surveyLat = centerLatitude,
        )
      )
    }
  }

  fun parsePlacesResultsJson(
    json: String,
    defaultRegionSubtitle: String,
    surveyLng: Double,
    surveyLat: Double,
  ): List<SurveyPlaceItem> =
    placeJsonMapper.mapJsonToPlaces(
      json = json,
      defaultRegionSubtitle = defaultRegionSubtitle,
      surveyLng = surveyLng,
      surveyLat = surveyLat,
    )
}
