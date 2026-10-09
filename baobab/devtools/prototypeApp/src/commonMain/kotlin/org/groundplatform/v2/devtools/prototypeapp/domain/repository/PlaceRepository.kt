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
package org.groundplatform.v2.devtools.prototypeapp.domain.repository

import kotlinx.coroutines.flow.Flow
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPlaceItem

/**
 * Domain repository contract for accessing local survey places and querying remote geocoding places
 * (`mapbox.places`).
 */
interface PlaceRepository {
  fun observeLocalPlaces(): Flow<List<SurveyPlaceItem>>

  suspend fun getLocalPlaces(): List<SurveyPlaceItem>

  suspend fun setLocalPlaces(places: List<SurveyPlaceItem>)

  fun searchRemotePlaces(
    surveyId: String,
    query: String,
    defaultRegionSubtitle: String,
    centerLongitude: Double,
    centerLatitude: Double,
    onResults: (List<SurveyPlaceItem>) -> Unit,
  )
}
