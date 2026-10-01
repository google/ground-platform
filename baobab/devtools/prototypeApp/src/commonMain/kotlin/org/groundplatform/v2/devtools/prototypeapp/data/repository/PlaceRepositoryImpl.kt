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
package org.groundplatform.v2.devtools.prototypeapp.data.repository

import kotlinx.coroutines.flow.Flow
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.LocalStore
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.remote.MapboxPlacesDataSource
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPlaceItem
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.PlaceRepository

/**
 * Concrete [PlaceRepository] implementation coordinating local places in the [LocalStore] and
 * remote geocoding queries via [MapboxPlacesDataSource].
 */
class PlaceRepositoryImpl(
  private val store: LocalStore,
  private val remoteDataSource: MapboxPlacesDataSource = MapboxPlacesDataSource(),
) : PlaceRepository {
  override fun observeLocalPlaces(): Flow<List<SurveyPlaceItem>> = store.observePlaces()

  override suspend fun getLocalPlaces(): List<SurveyPlaceItem> = store.transaction { places() }

  override suspend fun setLocalPlaces(places: List<SurveyPlaceItem>) {
    store.transaction { putPlaces(places) }
  }

  override fun searchRemotePlaces(
    surveyId: String,
    query: String,
    isAirplaneMode: Boolean,
    defaultRegionSubtitle: String,
    centerLongitude: Double,
    centerLatitude: Double,
    onResults: (List<SurveyPlaceItem>) -> Unit,
  ) {
    remoteDataSource.searchPlaces(
      surveyId = surveyId,
      query = query,
      isAirplaneMode = isAirplaneMode,
      defaultRegionSubtitle = defaultRegionSubtitle,
      centerLongitude = centerLongitude,
      centerLatitude = centerLatitude,
      onResults = onResults,
    )
  }
}
