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

import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import org.groundplatform.v2.map.LatLng
import org.groundplatform.v2.map.LngLatBounds
import org.groundplatform.v2.map.MapInsets

/** How the map frames a selected [SurveyPlaceItem]. */
data class PlaceFocus(
  val center: LatLng,
  val bounds: LngLatBounds,
  /** Nominal zoom for the place, used when the camera can't fit [bounds]. */
  val zoom: Double,
  /** Closest zoom the camera may reach while fitting [bounds]. */
  val maxZoom: Double,
  val padding: MapInsets,
)

/**
 * Decides the extent and zoom for places from search results: a geocoder bounding box when there is
 * a usable one, otherwise an extent inferred from the place's category (a country spans degrees, a
 * village a few hundred metres).
 */
object PlaceFraming {
  const val MIN_ZOOM = 2.2
  const val MAX_ZOOM = 16.8

  /** Keeps the place clear of the floating search bar (top) and bottom sheet peek (bottom). */
  val FOCUS_PADDING = MapInsets(left = 40.dp, top = 68.dp, right = 40.dp, bottom = 108.dp)

  /** Bounding boxes smaller than this in both dimensions are treated as points. */
  private const val MIN_BBOX_SPAN_DEGREES = 0.0002

  /** Explicit zooms are only kept for extents at most this wide. */
  private const val EXPLICIT_ZOOM_MAX_SPAN_DEGREES = 0.0004

  /** Zoom offset that leaves a margin around the extent. */
  private const val ZOOM_MARGIN = 0.65

  /** Inferred extents are slightly shorter than wide, matching typical landscape viewports. */
  private const val LATITUDE_ASPECT = 0.85

  /** Half the width, in degrees, of the area a place of [category] typically covers. */
  fun halfSpanDegrees(category: String, explicitZoom: Double? = null): Double {
    val cat = category.lowercase()
    fun has(vararg words: String) = words.any { it in cat }
    return when {
      has("country") -> 4.2
      has("state", "province") || ("region" in cat && "regional hub" !in cat) -> 1.1
      has("county", "district", "national park") -> 0.28
      has("city", "regional hub", "municipality") -> 0.065
      has("town", "sub-county") -> 0.032
      has("forest", "reserve", "dam", "reservoir", "hydrology") -> 0.018
      has("village", "locality", "hamlet", "sub-location", "market") -> 0.009
      has("neighborhood", "suburb", "river", "crossing", "junction") -> 0.0045
      explicitZoom != null && explicitZoom in 2.0..18.5 ->
        maxOf(0.0015, 180.0 / 2.0.pow(explicitZoom + ZOOM_MARGIN))
      else -> 0.0025
    }
  }

  /** [bbox] when it has a usable extent, otherwise an extent inferred around [center]. */
  fun bounds(
    center: LatLng,
    category: String,
    bbox: LngLatBounds? = null,
    explicitZoom: Double? = null,
  ): LngLatBounds {
    if (bbox != null && !bbox.isPointLike()) return bbox
    val half = halfSpanDegrees(category, explicitZoom)
    return LngLatBounds(
      west = center.longitude - half,
      south = center.latitude - half * LATITUDE_ASPECT,
      east = center.longitude + half,
      north = center.latitude + half * LATITUDE_ASPECT,
    )
  }

  /**
   * Zoom that shows [bounds] with a margin. An [explicitZoom] wins only for point-sized extents,
   * where the extent says nothing about scale.
   */
  fun zoom(bounds: LngLatBounds, explicitZoom: Double? = null): Double {
    val maxSpan = maxOf(abs(bounds.east - bounds.west), abs(bounds.north - bounds.south))
    val zoom =
      if (explicitZoom == null || explicitZoom <= 0 || maxSpan > EXPLICIT_ZOOM_MAX_SPAN_DEGREES) {
        val computed =
          ln(360.0 / maxOf(EXPLICIT_ZOOM_MAX_SPAN_DEGREES, maxSpan)) / ln(2.0) - ZOOM_MARGIN
        computed.coerceIn(MIN_ZOOM, MAX_ZOOM)
      } else {
        explicitZoom
      }
    return zoom.roundTo(2)
  }

  /** How the map should frame [place]. */
  fun focus(place: SurveyPlaceItem): PlaceFocus {
    val center = LatLng(place.latitude, place.longitude)
    val explicitZoom = place.targetZoom.toDouble()
    val bounds = bounds(center, place.categoryLabel, place.bbox(), explicitZoom)
    val zoom = zoom(bounds, explicitZoom)
    return PlaceFocus(
      center = center,
      bounds = bounds,
      zoom = zoom,
      maxZoom = (zoom + 0.6).coerceIn(2.5, MAX_ZOOM),
      padding = FOCUS_PADDING,
    )
  }

  private fun LngLatBounds.isPointLike() =
    abs(east - west) < MIN_BBOX_SPAN_DEGREES && abs(north - south) < MIN_BBOX_SPAN_DEGREES
}

/** The place's geocoder bounding box, normalized so west ≤ east and south ≤ north. */
fun SurveyPlaceItem.bbox(): LngLatBounds? {
  val w = bboxMinLng ?: return null
  val s = bboxMinLat ?: return null
  val e = bboxMaxLng ?: return null
  val n = bboxMaxLat ?: return null
  return LngLatBounds(minOf(w, e), minOf(s, n), maxOf(w, e), maxOf(s, n))
}
