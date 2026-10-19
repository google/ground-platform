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
package org.openforis.collectone.v2.app.ui.viewmodel

import io.ktor.client.HttpClient
import org.openforis.collectone.v2.app.client.places.PlacesGeocoder
import org.openforis.collectone.v2.app.client.places.createPlacesHttpClient
import org.openforis.collectone.v2.app.client.places.mapboxAccessToken
import org.openforis.collectone.v2.app.domain.model.SurveyThumbnailPlaceLabel
import org.openforis.collectone.v2.map.LatLng

/** Creates the HTTP client used to fetch survey map thumbnail tiles. */
internal fun createThumbnailHttpClient(): HttpClient? = createPlacesHttpClient()

/** Resolves the public Mapbox token (`pk.*`) for survey map thumbnails, if configured. */
internal fun resolveMapboxPublicToken(): String? =
  mapboxAccessToken()?.takeIf { it.startsWith("pk.") }

/** Reverse-geocodes place labels around [center] for survey map thumbnails. */
internal suspend fun reverseGeocodeThumbnailLabels(
  center: LatLng,
  limit: Int = 2,
): List<SurveyThumbnailPlaceLabel> =
  PlacesGeocoder().reverseGeocodePlaceLabels(center, limit = limit)
