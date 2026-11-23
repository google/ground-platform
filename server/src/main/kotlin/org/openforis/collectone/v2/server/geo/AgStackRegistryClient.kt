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
import org.openforis.collectone.v2.core.geo.geoid.AgStackGeoId
import org.slf4j.LoggerFactory

/**
 * Outcome of registering a plot boundary or point with the
 * [AgStack Asset Registry](https://github.com/agstack/asset-registry).
 *
 * @property geoId Authoritative 64-character lowercase hexadecimal SHA-256 GeoID assigned or
 *   matched by the registry.
 * @property isDuplicate True when the registry matched an existing boundary at or above the overlap
 *   threshold (`95%` level-20 cell overlap) instead of registering a new boundary.
 * @property matchedGeoIds GeoIDs of existing boundaries reported in `"matched fields"`, if any.
 */
data class AgStackRegistrationResult(
  val geoId: String,
  val isDuplicate: Boolean = false,
  val matchedGeoIds: List<String> = emptyList(),
)

/**
 * Registers an entity [geometry] with the AgStack Asset Registry so external systems (such as Open
 * Foris Whisp and EUDR verification tools) can resolve the entity's GeoID to its boundary, and
 * returns the authoritative GeoID (level-13 primary, level-20 fallback on collision, or matched
 * existing GeoID on ≥95% overlap).
 */
fun interface AgStackRegistryClient {
  fun registerGeometry(geometry: Geometry, entityId: String): AgStackRegistrationResult?
}

/** Default no-op [AgStackRegistryClient] used when `AGSTACK_REGISTRY_URL` is not configured. */
object NoopAgStackRegistryClient : AgStackRegistryClient {
  override fun registerGeometry(geometry: Geometry, entityId: String): AgStackRegistrationResult? =
    null
}

/**
 * HTTP client for the AgStack Asset Registry (`POST /register-field-boundary` for polygons and
 * `POST /register-point` for points).
 */
class HttpAgStackRegistryClient(
  private val baseUrl: URI,
  private val apiKey: String = "",
  private val authToken: String = "",
  private val overlapThresholdPercent: Int = DEFAULT_OVERLAP_THRESHOLD_PERCENT,
  private val httpClient: HttpClient =
    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
  private val requestTimeout: Duration = Duration.ofSeconds(30),
) : AgStackRegistryClient {

  override fun registerGeometry(geometry: Geometry, entityId: String): AgStackRegistrationResult? {
    val isPoint = isPointGeometry(geometry)
    val wkt =
      if (isPoint) {
        pointToWkt(GeometryProtos.representativePosition(geometry))
      } else {
        fieldBoundaryToWkt(geometry)
      } ?: return null
    val endpoint =
      resolveEndpoint(if (isPoint) REGISTER_POINT_PATH else REGISTER_FIELD_BOUNDARY_PATH)
    val payload =
      if (isPoint) {
        buildPointPayload(wkt)
      } else {
        buildFieldBoundaryPayload(wkt, overlapThresholdPercent)
      }
    val requestBuilder =
      HttpRequest.newBuilder(endpoint)
        .timeout(requestTimeout)
        .header("accept", "application/json")
        .header("content-type", "application/json; charset=UTF-8")
        .POST(HttpRequest.BodyPublishers.ofString(payload))
    if (apiKey.isNotBlank()) {
      requestBuilder.header("X-API-KEY", apiKey)
    }
    if (authToken.isNotBlank()) {
      val bearer =
        if (authToken.startsWith("Bearer ", ignoreCase = true)) authToken else "Bearer $authToken"
      requestBuilder.header("Authorization", bearer)
    }
    val response =
      runCatching { httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString()) }
        .onFailure { e ->
          logger.warn(
            "Failed to call AgStack Asset Registry at {} for entity {}",
            endpoint,
            entityId,
            e,
          )
        }
        .getOrNull() ?: return null
    val parsed = parseRegistrationResponse(response.body(), response.statusCode())
    if (parsed == null && response.statusCode() !in 200..299) {
      logger.warn(
        "AgStack Asset Registry HTTP {} for {} (entity {}): {}",
        response.statusCode(),
        endpoint,
        entityId,
        response.body().take(MAX_ERROR_LOG_CHARS),
      )
    }
    return parsed
  }

  private fun resolveEndpoint(path: String): URI {
    val base = baseUrl.toString().trimEnd('/')
    if (base.endsWith(REGISTER_FIELD_BOUNDARY_PATH) || base.endsWith(REGISTER_POINT_PATH)) {
      return baseUrl
    }
    return URI.create("$base$path")
  }

  companion object {
    const val REGISTER_FIELD_BOUNDARY_PATH = "/register-field-boundary"
    const val REGISTER_POINT_PATH = "/register-point"
    const val DEFAULT_OVERLAP_THRESHOLD_PERCENT = 95
    private const val MAX_ERROR_LOG_CHARS = 400
    private val logger = LoggerFactory.getLogger(HttpAgStackRegistryClient::class.java)

    internal fun isPointGeometry(geometry: Geometry): Boolean {
      if (GeometryProtos.typeOf(geometry) == GeometryType.GEOMETRY_TYPE_POINT) return true
      val vertices = extractPositions(geometry)
      return vertices.isNotEmpty() && vertices.distinctBy { it.latitude to it.longitude }.size == 1
    }

    internal fun buildPointPayload(wkt: String): String {
      val buffer = Buffer()
      JsonWriter.of(buffer).use { writer ->
        writer.beginObject()
        writer.name("wkt").value(wkt)
        writer.endObject()
      }
      return buffer.readUtf8()
    }

    internal fun buildFieldBoundaryPayload(
      wkt: String,
      thresholdPercent: Int = DEFAULT_OVERLAP_THRESHOLD_PERCENT,
    ): String {
      val buffer = Buffer()
      JsonWriter.of(buffer).use { writer ->
        writer.beginObject()
        writer.name("wkt").value(wkt)
        writer.name("s2_index").value("L13,L20")
        writer.name("threshold").value(thresholdPercent)
        writer.endObject()
      }
      return buffer.readUtf8()
    }

    internal fun pointToWkt(point: Position?): String? {
      val coord = point?.let(::formatPosition) ?: return null
      return "POINT ($coord)"
    }

    /**
     * Converts [geometry] to a closed `POLYGON ((...))` WKT suitable for
     * `/register-field-boundary`: polygons use their exterior ring (and holes if present); lines or
     * multi-geometries use the closed bounding-box ring of their coordinates.
     */
    internal fun fieldBoundaryToWkt(geometry: Geometry): String? =
      when (GeometryProtos.typeOf(geometry)) {
        GeometryType.GEOMETRY_TYPE_POLYGON -> polygonToWkt(geometry.polygon)
        GeometryType.GEOMETRY_TYPE_LINE_STRING,
        GeometryType.GEOMETRY_TYPE_MULTI_POINT,
        GeometryType.GEOMETRY_TYPE_MULTI_LINE_STRING,
        GeometryType.GEOMETRY_TYPE_MULTI_POLYGON ->
          boundingBoxPolygonWkt(extractPositions(geometry))
        GeometryType.GEOMETRY_TYPE_POINT,
        GeometryType.GEOMETRY_TYPE_UNSPECIFIED,
        null -> null
      }

    private fun polygonToWkt(polygon: PolygonCoordinates?): String? {
      val closedRings =
        polygon?.rings.orEmpty().map { closeRing(it.positions) }.filter { it.size >= 4 }
      if (closedRings.isEmpty()) return null
      val formattedRings = closedRings.map { formatPositions(it) ?: return null }
      return "POLYGON (${formattedRings.joinToString(", ") { "($it)" }})"
    }

    private fun boundingBoxPolygonWkt(positions: List<Position>): String? {
      if (positions.isEmpty() || !positions.all(::isValidPosition)) return null
      val minLat = positions.minOf { it.latitude }
      val maxLat = positions.maxOf { it.latitude }
      val minLon = positions.minOf { it.longitude }
      val maxLon = positions.maxOf { it.longitude }
      val ring =
        listOf(
          Position(longitude = minLon, latitude = minLat),
          Position(longitude = maxLon, latitude = minLat),
          Position(longitude = maxLon, latitude = maxLat),
          Position(longitude = minLon, latitude = maxLat),
          Position(longitude = minLon, latitude = minLat),
        )
      val formatted = formatPositions(ring) ?: return null
      return "POLYGON (($formatted))"
    }

    private fun extractPositions(geometry: Geometry): List<Position> =
      when (GeometryProtos.typeOf(geometry)) {
        GeometryType.GEOMETRY_TYPE_POINT -> listOfNotNull(geometry.point)
        GeometryType.GEOMETRY_TYPE_LINE_STRING -> geometry.line_string?.positions.orEmpty()
        GeometryType.GEOMETRY_TYPE_POLYGON ->
          geometry.polygon?.rings?.firstOrNull()?.positions.orEmpty()
        GeometryType.GEOMETRY_TYPE_MULTI_POINT -> geometry.multi_point?.positions.orEmpty()
        GeometryType.GEOMETRY_TYPE_MULTI_LINE_STRING ->
          geometry.multi_line_string?.lines?.flatMap { it.positions }.orEmpty()
        GeometryType.GEOMETRY_TYPE_MULTI_POLYGON ->
          geometry.multi_polygon
            ?.polygons
            ?.mapNotNull { it.rings.firstOrNull() }
            ?.flatMap { it.positions }
            .orEmpty()
        GeometryType.GEOMETRY_TYPE_UNSPECIFIED,
        null -> emptyList()
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

    private fun isValidPosition(pos: Position): Boolean =
      pos.longitude.isFinite() &&
        pos.latitude.isFinite() &&
        pos.longitude in -180.0..180.0 &&
        pos.latitude in -90.0..90.0

    private fun formatPosition(pos: Position): String? =
      if (isValidPosition(pos)) {
        "${formatDecimal(pos.longitude)} ${formatDecimal(pos.latitude)}"
      } else {
        null
      }

    private fun formatDecimal(value: Double): String {
      val normalized = if (value == -0.0) 0.0 else value
      return BigDecimal.valueOf(normalized).stripTrailingZeros().toPlainString()
    }

    /**
     * Parses an AgStack Asset Registry JSON response (`{"Geo Id": "...", "matched fields": [...]}`
     * or `{"geo_id": "..."}`). Returns `null` if no valid 64-character hex GeoID is present.
     */
    internal fun parseRegistrationResponse(
      body: String,
      statusCode: Int = 200,
    ): AgStackRegistrationResult? = runCatching {
      val buffer = Buffer().writeUtf8(body)
      JsonReader.of(buffer).use { reader -> readResponseObject(reader, statusCode) }
    }
      .onFailure { e -> logger.warn("Failed to parse AgStack Asset Registry JSON response", e) }
      .getOrNull()

    private fun readResponseObject(
      reader: JsonReader,
      statusCode: Int,
    ): AgStackRegistrationResult? {
      if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) return null
      var geoId: String? = null
      val matched = mutableListOf<String>()
      reader.beginObject()
      while (reader.hasNext()) {
        when (reader.nextName().lowercase()) {
          "geo id",
          "geo_id",
          "geoid" -> geoId = readNullableString(reader)?.trim()?.lowercase()
          "matched fields",
          "matched_fields" -> matched.addAll(readStringList(reader))
          else -> reader.skipValue()
        }
      }
      reader.endObject()
      val validMatched = matched.map { it.trim().lowercase() }.filter(AgStackGeoId::isValidFormat)
      val resolvedGeoId =
        geoId?.takeIf(AgStackGeoId::isValidFormat) ?: validMatched.firstOrNull() ?: return null
      val isDuplicate = validMatched.isNotEmpty() || statusCode == 409
      return AgStackRegistrationResult(
        geoId = resolvedGeoId,
        isDuplicate = isDuplicate,
        matchedGeoIds = validMatched,
      )
    }

    private fun readStringList(reader: JsonReader): List<String> =
      when (reader.peek()) {
        JsonReader.Token.BEGIN_ARRAY -> {
          val list = mutableListOf<String>()
          reader.beginArray()
          while (reader.hasNext()) {
            readNullableString(reader)?.let(list::add)
          }
          reader.endArray()
          list
        }
        JsonReader.Token.STRING -> listOfNotNull(readNullableString(reader))
        else -> {
          reader.skipValue()
          emptyList()
        }
      }

    private fun readNullableString(reader: JsonReader): String? =
      when (reader.peek()) {
        JsonReader.Token.STRING,
        JsonReader.Token.NUMBER -> reader.nextString()
        JsonReader.Token.NULL -> {
          reader.nextNull<Any>()
          null
        }
        else -> {
          reader.skipValue()
          null
        }
      }
  }
}
