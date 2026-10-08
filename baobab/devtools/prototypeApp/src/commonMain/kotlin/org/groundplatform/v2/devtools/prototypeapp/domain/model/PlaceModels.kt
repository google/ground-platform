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
package org.groundplatform.v2.devtools.prototypeapp.domain.model

import kotlin.math.abs
import org.groundplatform.v2.map.LatLng
import org.groundplatform.v2.map.LngLatBounds

/**
 * Parses a place coordinate string (such as `"0.5012° S, 36.9324° E"`, `"0.4160°S, 36.9465°E"`, or
 * `"-0.5012, 36.9324"`) into a `(latitude, longitude)` pair of decimal degrees.
 */
fun parsePlaceCoordinates(coordinatesLabel: String): Pair<Double, Double>? {
  val cleaned = coordinatesLabel.trim()
  if (cleaned.isEmpty()) return null
  val parts = cleaned.split(Regex("""\s*[,;/]\s*|\s{2,}""")).filter { it.isNotBlank() }
  if (parts.size < 2) return null

  fun parseComponent(raw: String): Pair<Double, Char?>? {
    val upper = raw.trim().uppercase()
    val dirMatch = Regex("""([NSEW])\b""").find(upper)
    val dir = dirMatch?.groupValues?.get(1)?.firstOrNull()
    val numMatch = Regex("""[+-]?\d+(?:\.\d+)?""").find(upper) ?: return null
    val rawValue = numMatch.value.toDoubleOrNull() ?: return null
    val signedValue =
      when (dir) {
        'S',
        'W' -> -abs(rawValue)
        'N',
        'E' -> abs(rawValue)
        else -> rawValue
      }
    return signedValue to dir
  }

  val first = parseComponent(parts[0]) ?: return null
  val second = parseComponent(parts[1]) ?: return null
  val (lat, lng) =
    if (
      first.second == 'E' || first.second == 'W' || second.second == 'N' || second.second == 'S'
    ) {
      second.first to first.first
    } else {
      first.first to second.first
    }
  if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
  return lat to lng
}

/**
 * Target zoom (`[PlaceFraming.MIN_ZOOM, PlaceFraming.MAX_ZOOM]`) for a place: from its geocoder
 * bounding box when usable, otherwise from the extent [PlaceFraming] infers for its
 * [categoryLabel]. Places of unknown category fall back to the default survey zoom plus
 * [fallbackZoomDelta].
 */
fun inferTargetZoomForPlace(
  categoryLabel: String,
  bboxMinLng: Double? = null,
  bboxMinLat: Double? = null,
  bboxMaxLng: Double? = null,
  bboxMaxLat: Double? = null,
  fallbackZoomDelta: Float = 0.75f,
): Float {
  val bbox =
    if (bboxMinLng != null && bboxMinLat != null && bboxMaxLng != null && bboxMaxLat != null) {
      LngLatBounds(
        minOf(bboxMinLng, bboxMaxLng),
        minOf(bboxMinLat, bboxMaxLat),
        maxOf(bboxMinLng, bboxMaxLng),
        maxOf(bboxMinLat, bboxMaxLat),
      )
    } else {
      null
    }
  // Zoom depends only on the extent's size, so any center works here.
  val bounds =
    PlaceFraming.bounds(
      center = LatLng(0.0, 0.0),
      category = categoryLabel,
      bbox = bbox,
      explicitZoom = DEFAULT_SURVEY_ZOOM + fallbackZoomDelta,
    )
  return PlaceFraming.zoom(bounds).toFloat()
}

private const val DEFAULT_SURVEY_ZOOM = 15.3

/**
 * Represents a geographic place, landmark, town, road junction, or hydrology feature returned by
 * the Mapbox Places API (`mapbox.places`) and searchable via `"Search places or map features..."`.
 */
data class SurveyPlaceItem(
  val id: String,
  val name: String,
  val categoryLabel: String,
  val regionSubtitle: String,
  val coordinatesLabel: String,
  val normalizedX: Float,
  val normalizedY: Float,
  val zoomDelta: Float = 0.75f,
  val longitude: Double =
    parsePlaceCoordinates(coordinatesLabel)?.second ?: DEFAULT_FALLBACK_LONGITUDE,
  val latitude: Double =
    parsePlaceCoordinates(coordinatesLabel)?.first ?: DEFAULT_FALLBACK_LATITUDE,
  val bboxMinLng: Double? = null,
  val bboxMinLat: Double? = null,
  val bboxMaxLng: Double? = null,
  val bboxMaxLat: Double? = null,
  val targetZoom: Float =
    inferTargetZoomForPlace(
      categoryLabel = categoryLabel,
      bboxMinLng = bboxMinLng,
      bboxMinLat = bboxMinLat,
      bboxMaxLng = bboxMaxLng,
      bboxMaxLat = bboxMaxLat,
      fallbackZoomDelta = zoomDelta,
    ),
  val mapboxPlaceId: String = id,
  val sourceLabel: String = "Places API",
) {
  /**
   * This place with coordinates and zoom fit for centering the map on it: when [longitude] and
   * [latitude] are the default fallback (no usable geocoder coordinates), they are re-parsed from
   * [coordinatesLabel]; [targetZoom] is clamped to `[2.0, 18.5]`.
   */
  fun resolvedForSelection(): SurveyPlaceItem {
    val parsedCoords = parsePlaceCoordinates(coordinatesLabel)
    val hasDefaultFallbackCoords =
      abs(longitude - DEFAULT_FALLBACK_LONGITUDE) < 1e-6 &&
        abs(latitude - DEFAULT_FALLBACK_LATITUDE) < 1e-6
    val lat = if (hasDefaultFallbackCoords && parsedCoords != null) parsedCoords.first else latitude
    val lng =
      if (hasDefaultFallbackCoords && parsedCoords != null) parsedCoords.second else longitude
    return copy(longitude = lng, latitude = lat, targetZoom = targetZoom.coerceIn(2.0f, 18.5f))
  }

  companion object {
    /** Longitude a place gets when its [coordinatesLabel] cannot be parsed. */
    const val DEFAULT_FALLBACK_LONGITUDE = 36.9512

    /** Latitude a place gets when its [coordinatesLabel] cannot be parsed. */
    const val DEFAULT_FALLBACK_LATITUDE = -0.4198
  }
}
