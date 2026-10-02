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
package org.groundplatform.v2.devtools.prototypeapp.client.places

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.round
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.groundplatform.v2.devtools.prototypeapp.domain.model.PlaceFraming
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyMapAnchor
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPlaceItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.roundTo
import org.groundplatform.v2.map.LatLng
import org.groundplatform.v2.map.LngLatBounds

/** HTTP client for place search, or `null` on platforms where the prototype has no network. */
internal expect fun createPlacesHttpClient(): HttpClient?

/** A Mapbox public access token (`pk.…`) configured for this session, if any. */
internal expect fun mapboxAccessToken(): String?

/**
 * Searches places near a survey with the Mapbox Geocoding API when a public token is configured,
 * otherwise with OpenStreetMap Nominatim. Failures yield no results rather than errors.
 */
class PlacesGeocoder(
  private val httpClient: HttpClient? = createPlacesHttpClient(),
  private val accessToken: () -> String? = ::mapboxAccessToken,
) {
  suspend fun search(
    query: String,
    near: SurveyMapAnchor,
    defaultSubtitle: String,
  ): List<SurveyPlaceItem> {
    val client = httpClient ?: return emptyList()
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    val token = accessToken()?.takeIf { it.startsWith("pk.") }
    val url = if (token != null) mapboxUrl(q, token, near.center) else nominatimUrl(q)
    return try {
      val response = client.get(url)
      if (!response.status.isSuccess()) return emptyList()
      PlacesResponseMapper.map(response.bodyAsText(), q, near, defaultSubtitle)
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      emptyList()
    }
  }

  private fun mapboxUrl(query: String, token: String, proximity: LatLng): String =
    URLBuilder("https://api.mapbox.com")
      .apply {
        appendPathSegments("geocoding", "v5", "mapbox.places", "$query.json")
        parameters.append("access_token", token)
        parameters.append("proximity", "${proximity.longitude},${proximity.latitude}")
        parameters.append("limit", "8")
        parameters.append(
          "types",
          "country,region,postcode,district,place,locality,neighborhood,address,poi",
        )
      }
      .buildString()

  private fun nominatimUrl(query: String): String =
    URLBuilder("https://nominatim.openstreetmap.org/search")
      .apply {
        parameters.append("format", "geojson")
        parameters.append("limit", "6")
        parameters.append("q", query)
      }
      .buildString()
}

/**
 * Maps a Mapbox Geocoding v5 or Nominatim GeoJSON response to [SurveyPlaceItem]s. Both are
 * FeatureCollections; field names differ, so each value is read from whichever is present.
 */
object PlacesResponseMapper {
  private const val MAX_RESULTS = 8
  private val json = Json { ignoreUnknownKeys = true }

  fun map(
    body: String,
    query: String,
    near: SurveyMapAnchor,
    defaultSubtitle: String,
  ): List<SurveyPlaceItem> {
    val features =
      runCatching { json.parseToJsonElement(body).jsonObject["features"]?.jsonArray }.getOrNull()
        ?: return emptyList()
    return features.take(MAX_RESULTS).mapIndexedNotNull { index, element ->
      (element as? JsonObject)?.let { toPlace(it, index, query, near, defaultSubtitle) }
    }
  }

  private fun toPlace(
    feature: JsonObject,
    index: Int,
    query: String,
    near: SurveyMapAnchor,
    defaultSubtitle: String,
  ): SurveyPlaceItem {
    val props = feature["properties"] as? JsonObject
    val center =
      (feature["center"] ?: (feature["geometry"] as? JsonObject)?.get("coordinates")).doubles()
    val lng = center.getOrNull(0)?.takeIf { it != 0.0 } ?: near.center.longitude
    val lat = center.getOrNull(1)?.takeIf { it != 0.0 } ?: near.center.latitude
    val rawType =
      (feature["place_type"] as? JsonArray)?.firstOrNull()?.string()
        ?: props.string("addresstype")
        ?: props.string("type")
        ?: props.string("category")
        ?: "Place"
    val category = rawType.replaceFirstChar { it.uppercaseChar() }.replace('_', ' ')
    val placeName = feature.string("place_name")
    val name =
      feature.string("text") ?: props.string("name") ?: placeName?.substringBefore(',') ?: query
    val subtitle = placeName ?: props.string("display_name") ?: defaultSubtitle
    val position = LatLng(lat, lng)
    val bbox =
      feature["bbox"]
        .doubles()
        .takeIf { it.size >= 4 }
        ?.let { (w, s, e, n) -> LngLatBounds(minOf(w, e), minOf(s, n), maxOf(w, e), maxOf(s, n)) }
    val bounds = PlaceFraming.bounds(position, category, bbox)
    val zoom = PlaceFraming.zoom(bounds).toFloat()
    val (nx, ny) = near.toNormalized(position)
    val id = feature.string("id") ?: "places.$index.${abs(round(lng * 1000).toLong())}"
    return SurveyPlaceItem(
      id = id,
      name = name,
      categoryLabel = category,
      regionSubtitle = subtitle,
      coordinatesLabel = formatCoordinates(lat, lng),
      normalizedX = nx.coerceIn(0.05, 0.95).roundTo(4).toFloat(),
      normalizedY = ny.coerceIn(0.05, 0.95).roundTo(4).toFloat(),
      zoomDelta = (zoom - near.zoom.toFloat()).coerceIn(-13.0f, 3.2f),
      longitude = lng.roundTo(6),
      latitude = lat.roundTo(6),
      bboxMinLng = bounds.west.roundTo(6),
      bboxMinLat = bounds.south.roundTo(6),
      bboxMaxLng = bounds.east.roundTo(6),
      bboxMaxLat = bounds.north.roundTo(6),
      targetZoom = zoom,
      mapboxPlaceId = id,
      sourceLabel = "Places API",
    )
  }

  /** `"0.3833°S, 36.7°E"`: absolute degrees to at most 4 decimals, then the hemisphere. */
  internal fun formatCoordinates(lat: Double, lng: Double): String =
    "${formatDegrees(abs(lat))}°${if (lat < 0) 'S' else 'N'}, " +
      "${formatDegrees(abs(lng))}°${if (lng < 0) 'W' else 'E'}"

  private fun formatDegrees(value: Double): String {
    val scaled = round(value * 10_000).toLong()
    val whole = scaled / 10_000
    val fraction = (scaled % 10_000).toString().padStart(4, '0').trimEnd('0')
    return if (fraction.isEmpty()) "$whole" else "$whole.$fraction"
  }

  private fun JsonElement?.doubles(): List<Double> =
    (this as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull } ?: emptyList()

  private fun JsonElement?.string(): String? =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotEmpty() }

  private fun JsonObject?.string(key: String): String? = this?.get(key).string()
}
