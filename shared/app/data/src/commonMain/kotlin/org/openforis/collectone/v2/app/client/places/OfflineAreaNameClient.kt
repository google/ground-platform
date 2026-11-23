/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.app.client.places

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.map.LngLatBounds

private val log = Logger.withTag("OfflineAreaNameClient")

/** Names the place a map area saved for offline use frames. */
fun interface OfflineAreaNameClient {
  /**
   * The name of the place [bounds] frames, as seen at map [zoom] (which picks how specific it is),
   * e.g. `Othaya, Nyeri`; null when none is found.
   */
  suspend fun name(bounds: LngLatBounds, zoom: Double): String?
}

/**
 * Names areas with the Mapbox Geocoding v6 reverse lookup of the frame's center, with
 * `permanent=true` because the name is stored with the saved area. Without a network client or a
 * public token (`pk.…`), and on any failure or after [timeoutMillis], the name is null.
 */
class MapboxOfflineAreaNameClient(
  httpClient: () -> HttpClient? = ::createPlacesHttpClient,
  private val accessToken: () -> String? = ::mapboxAccessToken,
  private val timeoutMillis: Long = PlacesGeocoder.REQUEST_TIMEOUT_MS,
) : OfflineAreaNameClient {
  /** Created on the first lookup, not with the app's dependency graph. */
  private val httpClient: HttpClient? by lazy(httpClient)

  override suspend fun name(bounds: LngLatBounds, zoom: Double): String? {
    val client = httpClient ?: return null
    val token = accessToken()?.takeIf { it.startsWith("pk.") } ?: return null
    // The URL holds the token, so it is never logged.
    val url = reverseUrl(bounds.center, zoom, token)
    return try {
      withTimeoutOrNull(timeoutMillis) {
        val response = client.get(url)
        if (response.status.isSuccess()) {
          OfflineAreaNameResponseMapper.map(response.bodyAsText())
        } else {
          log.w { "Area name lookup failed with HTTP ${response.status.value}" }
          null
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
      log.w { "Area name lookup failed: ${e::class.simpleName}" }
      null
    }
  }

  companion object {
    /**
     * The reverse lookup URL for [center] at [zoom]: one result, of the place types that suit the
     * zoom (neighborhoods when zoomed in, districts and regions when zoomed out).
     */
    internal fun reverseUrl(center: LatLng, zoom: Double, token: String): String =
      URLBuilder("https://api.mapbox.com")
        .apply {
          appendPathSegments("search", "geocode", "v6", "reverse")
          parameters.append("longitude", center.longitude.toString())
          parameters.append("latitude", center.latitude.toString())
          parameters.append("permanent", "true")
          parameters.append("limit", "1")
          parameters.append("types", placeTypes(zoom))
          parameters.append("access_token", token)
        }
        .buildString()

    /** The Geocoding v6 feature types that name an area seen at [zoom]. */
    internal fun placeTypes(zoom: Double): String =
      when {
        zoom >= NEIGHBORHOOD_ZOOM -> "neighborhood,locality"
        zoom >= LOCALITY_ZOOM -> "locality,place"
        else -> "district,region"
      }

    private const val NEIGHBORHOOD_ZOOM = 14.0
    private const val LOCALITY_ZOOM = 11.0
  }
}

/**
 * Reads the area name from a Mapbox Geocoding v6 response: the first feature's `properties.name`,
 * followed by the enclosing place or region from `properties.context` (`Othaya, Nyeri`).
 */
object OfflineAreaNameResponseMapper {
  private val json = Json { ignoreUnknownKeys = true }

  /** The context levels that follow a feature type in the name, the first one present wins. */
  private val enclosingLevels =
    mapOf(
      "neighborhood" to listOf("place", "district", "region"),
      "locality" to listOf("place", "district", "region"),
      "place" to listOf("region", "country"),
      "district" to listOf("region", "country"),
      "region" to listOf("country"),
    )

  /** The name in [body], or null when it has no named feature or isn't a v6 response. */
  fun map(body: String): String? {
    val properties =
      runCatching {
        json.parseToJsonElement(body).jsonObject["features"]?.jsonArray?.firstOrNull()?.let {
          it.jsonObject["properties"] as? JsonObject
        }
      }
        .getOrNull() ?: return null
    val name = properties.string("name")?.takeIf { it.isNotBlank() } ?: return null
    val context = properties["context"] as? JsonObject
    val levels = enclosingLevels[properties.string("feature_type")] ?: listOf("region", "country")
    val enclosing = levels.firstNotNullOfOrNull { level ->
      (context?.get(level) as? JsonObject)?.string("name")?.takeIf {
        it.isNotBlank() && it != name
      }
    }
    return if (enclosing == null) name else "$name, $enclosing"
  }

  private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.content
}

/**
 * Names areas with invented place names, for simulated saves (the mobile app in the browser) and
 * tests, which make no geocoding requests. The same center (to about 0.05°, 5 km) always gets the
 * same name.
 */
class PlaceholderOfflineAreaNameClient : OfflineAreaNameClient {
  override suspend fun name(bounds: LngLatBounds, zoom: Double): String = nameAt(bounds.center)

  companion object {
    /** Fictional place names: they must not read as, or be mistaken for, real places. */
    val NAMES: List<String> =
      listOf(
        "Cedar Ridge",
        "Riverbend Flats",
        "Stonebrook Hills",
        "Willow Crossing",
        "Fern Valley",
        "Juniper Springs",
        "Granite Pass",
        "Lakeside Meadows",
        "Amber Plains",
        "Copper Creek",
        "Misty Highlands",
        "Pine Terrace",
        "Silver Marsh",
        "Hazel Grove",
        "Sandstone Bluff",
        "Maple Hollow",
      )

    private const val GRID_DEGREES = 0.05
    private const val ROW_MULTIPLIER = 31

    /** The placeholder name of the area centered at [center]. */
    fun nameAt(center: LatLng): String {
      val row = (center.latitude / GRID_DEGREES).roundToLong()
      val column = (center.longitude / GRID_DEGREES).roundToLong()
      return NAMES[(abs(row * ROW_MULTIPLIER + column) % NAMES.size).toInt()]
    }
  }
}
