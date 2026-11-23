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
package org.openforis.collectone.v2.server.geo

import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import okio.Buffer
import openforis.collectone.v1.data.Geometry
import openforis.collectone.v1.data.GeometryType
import openforis.collectone.v1.data.PolygonCoordinates
import openforis.collectone.v1.data.Position
import org.openforis.collectone.v2.core.geo.GeometryProtos
import org.slf4j.LoggerFactory

/**
 * Fetches Open Foris Whisp geospatial risk and contextual indicators for an entity [geometry].
 *
 * Returns the raw unprefixed property map from the Whisp response feature, or an empty map when
 * Whisp is not configured, the geometry cannot be represented as WKT, or the upstream call fails.
 */
fun interface WhispClient {
  fun fetchProperties(geometry: Geometry): Map<String, String>
}

/** Default no-op [WhispClient] when `WHISP_API_KEY` is not configured. */
object NoopWhispClient : WhispClient {
  override fun fetchProperties(geometry: Geometry): Map<String, String> = emptyMap()
}

/**
 * Calls the Open Foris Whisp WKT endpoint (`POST https://whisp.openforis.org/api/submit/wkt`)
 * authenticated with the `X-API-KEY` header, matching Ground 1.x's Whisp property generator.
 */
class HttpWhispClient(
  private val apiKey: String,
  private val endpoint: URI = DEFAULT_ENDPOINT,
  private val httpClient: HttpClient =
    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
  private val requestTimeout: Duration = Duration.ofSeconds(30),
) : WhispClient {

  override fun fetchProperties(geometry: Geometry): Map<String, String> {
    if (apiKey.isBlank()) return emptyMap()
    val wkt = geometryToWkt(geometry) ?: return emptyMap()
    val payload = buildWhispJsonPayload(wkt)
    val request =
      HttpRequest.newBuilder(endpoint)
        .timeout(requestTimeout)
        .header("accept", "application/json")
        .header("content-type", "application/json; charset=UTF-8")
        .header("X-API-KEY", apiKey)
        .POST(HttpRequest.BodyPublishers.ofString(payload))
        .build()
    val response =
      runCatching { httpClient.send(request, HttpResponse.BodyHandlers.ofString()) }
        .onFailure { e -> logger.warn("Failed to call Whisp API at {}", endpoint, e) }
        .getOrNull() ?: return emptyMap()
    if (response.statusCode() !in 200..299) {
      logger.warn(
        "Whisp HTTP {} for {}: {}",
        response.statusCode(),
        endpoint,
        response.body().take(MAX_ERROR_LOG_CHARS),
      )
      return emptyMap()
    }
    return parseWhispResponseProperties(response.body())
  }

  companion object {
    val DEFAULT_ENDPOINT: URI = URI.create("https://whisp.openforis.org/api/submit/wkt")
    private const val MAX_ERROR_LOG_CHARS = 400
    private val logger = LoggerFactory.getLogger(HttpWhispClient::class.java)

    internal fun buildWhispJsonPayload(wkt: String): String {
      val buffer = Buffer()
      JsonWriter.of(buffer).use { writer ->
        writer.beginObject()
        writer.name("wkt").value(wkt)
        writer.endObject()
      }
      return buffer.readUtf8()
    }

    /**
     * Converts a canonical [Geometry] to OGC Well-Known Text (`lon lat` coordinate order), or
     * returns `null` when [geometry] is empty or has invalid coordinates.
     */
    internal fun geometryToWkt(geometry: Geometry): String? =
      when (GeometryProtos.typeOf(geometry)) {
        GeometryType.GEOMETRY_TYPE_POINT -> pointToWkt(geometry.point)
        GeometryType.GEOMETRY_TYPE_LINE_STRING ->
          lineToWkt(geometry.line_string?.positions.orEmpty())
        GeometryType.GEOMETRY_TYPE_POLYGON -> polygonToWkt(geometry.polygon)
        GeometryType.GEOMETRY_TYPE_MULTI_POINT ->
          multiPointToWkt(geometry.multi_point?.positions.orEmpty())
        GeometryType.GEOMETRY_TYPE_MULTI_LINE_STRING ->
          multiLineToWkt(geometry.multi_line_string?.lines.orEmpty().map { it.positions })
        GeometryType.GEOMETRY_TYPE_MULTI_POLYGON ->
          multiPolygonToWkt(geometry.multi_polygon?.polygons.orEmpty())
        GeometryType.GEOMETRY_TYPE_UNSPECIFIED,
        null -> null
      }

    private fun pointToWkt(point: Position?): String? {
      val coord = point?.let(::formatPosition) ?: return null
      return "POINT ($coord)"
    }

    private fun lineToWkt(positions: List<Position>): String? {
      val coords = formatPositions(positions) ?: return null
      return if (positions.size >= 2) "LINESTRING ($coords)" else null
    }

    private fun polygonToWkt(polygon: PolygonCoordinates?): String? {
      val rings = formatPolygonRings(polygon) ?: return null
      return "POLYGON $rings"
    }

    private fun multiPointToWkt(positions: List<Position>): String? {
      if (positions.isEmpty()) return null
      val parts = positions.map { formatPosition(it) ?: return null }
      return "MULTIPOINT (${parts.joinToString(", ") { "($it)" }})"
    }

    private fun multiLineToWkt(lines: List<List<Position>>): String? {
      val validLines = lines.filter { it.size >= 2 }
      if (validLines.isEmpty()) return null
      val parts = validLines.map { formatPositions(it) ?: return null }
      return "MULTILINESTRING (${parts.joinToString(", ") { "($it)" }})"
    }

    private fun multiPolygonToWkt(polygons: List<PolygonCoordinates>): String? {
      if (polygons.isEmpty()) return null
      val parts = polygons.mapNotNull(::formatPolygonRings)
      return if (parts.isNotEmpty()) "MULTIPOLYGON (${parts.joinToString(", ")})" else null
    }

    private fun formatPolygonRings(polygon: PolygonCoordinates?): String? {
      val closedRings =
        polygon?.rings.orEmpty().map { closeRing(it.positions) }.filter { it.size >= 4 }
      if (closedRings.isEmpty()) return null
      val formattedRings = closedRings.map { formatPositions(it) ?: return null }
      return "(${formattedRings.joinToString(", ") { "($it)" }})"
    }

    private fun closeRing(positions: List<Position>): List<Position> {
      if (positions.size < 3) return positions
      val first = positions.first()
      val last = positions.last()
      return if (first.longitude == last.longitude && first.latitude == last.latitude) {
        positions
      } else {
        positions + first
      }
    }

    private fun formatPositions(positions: List<Position>): String? {
      if (positions.isEmpty()) return null
      val formatted = positions.map { formatPosition(it) ?: return null }
      return formatted.joinToString(", ")
    }

    private fun formatPosition(pos: Position): String? {
      val lon = pos.longitude
      val lat = pos.latitude
      val valid = lon.isFinite() && lat.isFinite() && lon in -180.0..180.0 && lat in -90.0..90.0
      return if (valid) "${formatDecimal(lon)} ${formatDecimal(lat)}" else null
    }

    private fun formatDecimal(value: Double): String {
      val normalized = if (value == -0.0) 0.0 else value
      return BigDecimal.valueOf(normalized).stripTrailingZeros().toPlainString()
    }

    /**
     * Extracts the feature properties from a Whisp JSON response: `{"code": "analysis_completed",
     * "data": {"features": [{"properties": {...}}]}}`.
     */
    internal fun parseWhispResponseProperties(body: String): Map<String, String> = runCatching {
      val buffer = Buffer().writeUtf8(body)
      JsonReader.of(buffer).use(::readRootResponse)
    }
      .onFailure { e -> logger.warn("Failed to parse Whisp JSON response", e) }
      .getOrDefault(emptyMap())

    private fun readRootResponse(reader: JsonReader): Map<String, String> {
      if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) return emptyMap()
      var code: String? = null
      var properties: Map<String, String> = emptyMap()
      reader.beginObject()
      while (reader.hasNext()) {
        when (reader.nextName()) {
          "code" -> code = readNullableString(reader)
          "data" -> properties = readDataObject(reader)
          "features" -> {
            val topLevel = readFeaturesArray(reader)
            if (properties.isEmpty()) properties = topLevel
          }
          else -> reader.skipValue()
        }
      }
      reader.endObject()
      if (code != null && code != "analysis_completed") {
        logger.warn("Whisp returned non-completed code: {}", code)
        return emptyMap()
      }
      return properties
    }

    private fun readDataObject(reader: JsonReader): Map<String, String> {
      if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
        reader.skipValue()
        return emptyMap()
      }
      var properties: Map<String, String> = emptyMap()
      reader.beginObject()
      while (reader.hasNext()) {
        when (reader.nextName()) {
          "features" -> properties = readFeaturesArray(reader)
          else -> reader.skipValue()
        }
      }
      reader.endObject()
      return properties
    }

    private fun readFeaturesArray(reader: JsonReader): Map<String, String> {
      if (reader.peek() != JsonReader.Token.BEGIN_ARRAY) {
        reader.skipValue()
        return emptyMap()
      }
      var firstProperties: Map<String, String> = emptyMap()
      var foundFirst = false
      reader.beginArray()
      while (reader.hasNext()) {
        if (!foundFirst) {
          firstProperties = readFeatureObject(reader)
          foundFirst = true
        } else {
          reader.skipValue()
        }
      }
      reader.endArray()
      return firstProperties
    }

    private fun readFeatureObject(reader: JsonReader): Map<String, String> {
      if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
        reader.skipValue()
        return emptyMap()
      }
      var properties: Map<String, String> = emptyMap()
      reader.beginObject()
      while (reader.hasNext()) {
        when (reader.nextName()) {
          "properties" -> properties = readPropertiesMap(reader)
          else -> reader.skipValue()
        }
      }
      reader.endObject()
      return properties
    }

    private fun readPropertiesMap(reader: JsonReader): Map<String, String> {
      if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
        reader.skipValue()
        return emptyMap()
      }
      val result = linkedMapOf<String, String>()
      reader.beginObject()
      while (reader.hasNext()) {
        val key = reader.nextName()
        val value = readPropertyValueAsString(reader)
        if (key.isNotBlank() && value != null) {
          result[key] = value
        }
      }
      reader.endObject()
      return result
    }

    private fun readPropertyValueAsString(reader: JsonReader): String? =
      when (reader.peek()) {
        JsonReader.Token.STRING,
        JsonReader.Token.NUMBER -> reader.nextString()
        JsonReader.Token.BOOLEAN -> reader.nextBoolean().toString()
        JsonReader.Token.NULL -> {
          reader.nextNull<Any>()
          null
        }
        JsonReader.Token.BEGIN_ARRAY,
        JsonReader.Token.BEGIN_OBJECT -> {
          val jsonValue = reader.readJsonValue()
          val out = Buffer()
          JsonWriter.of(out).use { it.jsonValue(jsonValue) }
          out.readUtf8()
        }
        else -> {
          reader.skipValue()
          null
        }
      }

    private fun readNullableString(reader: JsonReader): String? =
      if (reader.peek() == JsonReader.Token.NULL) {
        reader.nextNull<Any>()
        null
      } else {
        reader.nextString()
      }
  }
}
