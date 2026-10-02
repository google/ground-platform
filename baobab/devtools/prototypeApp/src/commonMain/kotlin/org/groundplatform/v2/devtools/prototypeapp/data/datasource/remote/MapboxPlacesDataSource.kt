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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.groundplatform.v2.devtools.prototypeapp.client.places.PlacesGeocoder
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyMapAnchor
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPlaceItem
import org.groundplatform.v2.map.LatLng

/**
 * Remote data source for place search. Runs [PlacesGeocoder] queries in the background and delivers
 * results through callbacks; a new search cancels the previous one.
 */
class MapboxPlacesDataSource(
  private val geocoder: PlacesGeocoder = PlacesGeocoder(),
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
  private var inFlight: Job? = null

  fun searchPlaces(
    surveyId: String,
    query: String,
    isAirplaneMode: Boolean,
    defaultRegionSubtitle: String,
    centerLongitude: Double,
    centerLatitude: Double,
    onResults: (List<SurveyPlaceItem>) -> Unit,
  ) {
    inFlight?.cancel()
    if (isAirplaneMode || query.isBlank()) {
      onResults(emptyList())
      return
    }
    val base = SurveyMapAnchor.forSurvey(surveyId)
    val near = base.copy(center = LatLng(centerLatitude, centerLongitude))
    inFlight = scope.launch { onResults(geocoder.search(query, near, defaultRegionSubtitle)) }
  }
}
