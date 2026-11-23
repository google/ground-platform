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
package org.openforis.collectone.v2.app.domain.model

import kotlin.math.abs
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.map.LngLatBounds

/**
 * Parses coordinates typed by a person (such as `"0.5012° S, 36.9324° E"`, `"0.4160°S, 36.9465°E"`,
 * or `"-0.5012, 36.9324"`) into a position, or `null` when [text] is not a valid coordinate pair.
 */
fun parsePlaceCoordinates(text: String): LatLng? {
  val cleaned = text.trim()
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
  return LatLng(lat, lng)
}

/**
 * Target zoom (`[PlaceFraming.MIN_ZOOM, PlaceFraming.MAX_ZOOM]`) for a place: from its geocoder
 * [bounds] when usable, otherwise from the extent [PlaceFraming] infers for its [categoryLabel].
 * Places of unknown category fall back to the default survey zoom plus [fallbackZoomDelta].
 */
fun inferTargetZoomForPlace(
  categoryLabel: String,
  bounds: LngLatBounds? = null,
  fallbackZoomDelta: Float = 0.75f,
): Float {
  // Zoom depends only on the extent's size, so any center works here.
  val extent =
    PlaceFraming.bounds(
      center = LatLng(0.0, 0.0),
      category = categoryLabel,
      bbox = bounds,
      explicitZoom = DEFAULT_SURVEY_ZOOM + fallbackZoomDelta,
    )
  return PlaceFraming.zoom(extent).toFloat()
}

private const val DEFAULT_SURVEY_ZOOM = 15.3

/** What a [SurveyPlaceItem] stands for, which decides how the UI names it. */
enum class PlaceKind {
  /** A place from the gazetteer or the geocoder, shown with its own name, category, and region. */
  NAMED,

  /**
   * Coordinates typed into the place search. The UI names it after its position, in the user's
   * language; its [SurveyPlaceItem.name] is the locale-neutral `plainCoordinatesLabel`, for search,
   * and its [SurveyPlaceItem.regionSubtitle] is the survey's location.
   */
  COORDINATES,
}

/** Where a [SurveyPlaceItem] comes from. The UI names it in the user's language. */
enum class PlaceSource {
  /** The place search: the Mapbox Places API, or the backend place catalog that mirrors it. */
  PLACES_API
}

/**
 * Represents a geographic place, landmark, town, road junction, or hydrology feature returned by
 * the Mapbox Places API (`mapbox.places`) and searchable via `"Search places or map features..."`.
 * Its [position] is in WGS 84 decimal degrees; the UI formats it for display. [bounds] is the
 * geocoder's bounding box (west ≤ east, south ≤ north), or `null` when it has none. [source] is
 * where it comes from, or `null` when that is unknown. [kind] tells a named place from typed
 * coordinates.
 */
data class SurveyPlaceItem(
  val id: String,
  val name: String,
  val categoryLabel: String,
  val regionSubtitle: String,
  val position: LatLng,
  val normalizedX: Float,
  val normalizedY: Float,
  val zoomDelta: Float = 0.75f,
  val bounds: LngLatBounds? = null,
  val targetZoom: Float =
    inferTargetZoomForPlace(
      categoryLabel = categoryLabel,
      bounds = bounds,
      fallbackZoomDelta = zoomDelta,
    ),
  val mapboxPlaceId: String = id,
  val source: PlaceSource? = PlaceSource.PLACES_API,
  val kind: PlaceKind = PlaceKind.NAMED,
) {
  /** This place with [targetZoom] clamped to `[2.0, 18.5]`, for centering the map on it. */
  fun resolvedForSelection(): SurveyPlaceItem =
    copy(targetZoom = targetZoom.coerceIn(MIN_SELECTION_ZOOM, MAX_SELECTION_ZOOM))

  /**
   * Whether [query] appears in the place's name, category, region, or coordinates (as
   * [plainCoordinatesLabel] writes them), ignoring case.
   */
  fun matches(query: String): Boolean =
    name.contains(query, ignoreCase = true) ||
      categoryLabel.contains(query, ignoreCase = true) ||
      regionSubtitle.contains(query, ignoreCase = true) ||
      plainCoordinatesLabel(position).contains(query, ignoreCase = true)

  private companion object {
    const val MIN_SELECTION_ZOOM = 2.0f
    const val MAX_SELECTION_ZOOM = 18.5f
  }
}
