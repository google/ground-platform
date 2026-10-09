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
package org.groundplatform.v2.devtools.prototypeapp.domain.usecase

import kotlin.math.abs
import kotlin.math.roundToInt
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ListFilterTab
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPlaceItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.parsePlaceCoordinates

/**
 * Domain use case combining local survey place gazetteer search, raw GPS coordinate query parsing,
 * and remote Mapbox Places API results while respecting network connectivity (`isOnline`) rules.
 */
class SearchPlacesUseCase {
  operator fun invoke(
    query: String,
    isOnline: Boolean = true,
    listFilterTab: ListFilterTab,
    localPlaces: List<SurveyPlaceItem>,
    remoteApiPlaces: List<SurveyPlaceItem>,
    surveyLocationLabel: String,
    surveyBaseLng: Double,
    surveyBaseLat: Double,
  ): List<SurveyPlaceItem> {
    if (!isOnline) {
      return emptyList()
    }
    if (listFilterTab != ListFilterTab.ALL && listFilterTab != ListFilterTab.PLACES) {
      return emptyList()
    }
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    val tokens = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
    val localMatched = localPlaces.filter { place ->
      val hay =
        "${place.name} ${place.categoryLabel} ${place.regionSubtitle} ${place.coordinatesLabel}"
      place.name.contains(q, ignoreCase = true) ||
        place.categoryLabel.contains(q, ignoreCase = true) ||
        place.regionSubtitle.contains(q, ignoreCase = true) ||
        place.coordinatesLabel.contains(q, ignoreCase = true) ||
        (tokens.size > 1 && tokens.all { hay.contains(it, ignoreCase = true) })
    }
    val combined = LinkedHashMap<String, SurveyPlaceItem>()
    for (apiItem in remoteApiPlaces) {
      combined[apiItem.id] = apiItem
    }
    for (item in localMatched) {
      if (combined.values.none { it.name.equals(item.name, ignoreCase = true) }) {
        combined[item.id] = item
      }
    }
    val matched = combined.values.toList()
    val coordPlace =
      parseCoordinateQueryToPlace(
        query = q,
        surveyLocationLabel = surveyLocationLabel,
        surveyBaseLng = surveyBaseLng,
        surveyBaseLat = surveyBaseLat,
      )
    return if (
      coordPlace != null && matched.none { it.coordinatesLabel == coordPlace.coordinatesLabel }
    ) {
      listOf(coordPlace) + matched
    } else {
      matched
    }
  }

  fun parseCoordinateQueryToPlace(
    query: String,
    surveyLocationLabel: String,
    surveyBaseLng: Double,
    surveyBaseLat: Double,
  ): SurveyPlaceItem? {
    val (lat, lng) = parsePlaceCoordinates(query) ?: return null
    val nx = (0.50f + ((lng - surveyBaseLng) / 0.014).toFloat()).coerceIn(0.05f, 0.95f)
    val ny = (0.50f + ((surveyBaseLat - lat) / 0.018).toFloat()).coerceIn(0.05f, 0.95f)
    val latDir = if (lat < 0) "S" else "N"
    val lngDir = if (lng < 0) "W" else "E"
    val formattedLat = (abs(lat) * 10000.0).roundToInt() / 10000.0
    val formattedLng = (abs(lng) * 10000.0).roundToInt() / 10000.0
    val coordLabel = "$formattedLat°$latDir, $formattedLng°$lngDir"
    return SurveyPlaceItem(
      id = "place-coord-query",
      name = "Coordinates ($coordLabel)",
      categoryLabel = "GPS Coordinates",
      regionSubtitle = "$surveyLocationLabel • Direct coordinate lookup",
      coordinatesLabel = coordLabel,
      normalizedX = nx,
      normalizedY = ny,
      zoomDelta = 1.15f,
      longitude = lng,
      latitude = lat,
    )
  }
}
