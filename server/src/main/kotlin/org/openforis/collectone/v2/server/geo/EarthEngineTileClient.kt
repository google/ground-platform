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

import com.squareup.moshi.Types
import google.earthengine.v1main.Expression
import io.grpc.Status
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import okio.ByteString
import org.openforis.collectone.v2.server.web.protoJsonMoshi

/** Result of resolving an Earth Engine [Expression] into an XYZ tile URL template. */
data class ResolvedTileUrl(val urlTemplate: String, val ttlSeconds: Long)

/**
 * Resolves Earth Engine [Expression] computation graphs into short-lived XYZ tile URL templates,
 * caching results in memory for up to [DEFAULT_TTL_SECONDS].
 */
interface EarthEngineTileClient {
  /** Resolves [expression] into an XYZ tile URL template and the remaining TTL. */
  fun resolve(expression: Expression): ResolvedTileUrl

  companion object {
    /** Default tile URL time-to-live: 2 hours (7200 seconds). */
    const val DEFAULT_TTL_SECONDS: Long = 7200L

    /**
     * Creates an in-memory caching [EarthEngineTileClient] with an injectable [fetcher] and [clock]
     * for deterministic testing.
     */
    fun create(
      ttlSeconds: Long = DEFAULT_TTL_SECONDS,
      clock: () -> Instant = { Instant.now() },
      fetcher: (Expression) -> String,
    ): EarthEngineTileClient =
      CachingEarthEngineTileClient(ttlSeconds = ttlSeconds, clock = clock, fetcher = fetcher)

    /**
     * Creates a fake [EarthEngineTileClient] that generates deterministic tile URLs without network
     * calls.
     */
    fun fake(
      urlTemplatePrefix: String =
        "https://earthengine.googleapis.com/v1/projects/example-project/maps/fake-map",
      ttlSeconds: Long = DEFAULT_TTL_SECONDS,
      clock: () -> Instant = { Instant.now() },
    ): EarthEngineTileClient {
      var callCount = 0
      return create(ttlSeconds = ttlSeconds, clock = clock) { expr ->
        val hash = expr.encodeByteString().sha256().hex().take(8)
        callCount++
        "$urlTemplatePrefix-$hash-$callCount/tiles/{z}/{x}/{y}"
      }
    }

    /**
     * Initializes an [EarthEngineTileClient] from environment variables, system properties, or the
     * server build config.
     */
    fun fromEnvironment(
      clock: () -> Instant = { Instant.now() },
      httpClient: HttpClient = HttpClient.newHttpClient(),
    ): EarthEngineTileClient {
      val auth =
        EarthEngineAuth.fromEnvironment()
          ?: return object : EarthEngineTileClient {
            override fun resolve(expression: Expression): ResolvedTileUrl {
              throw Status.UNAVAILABLE.withDescription(EarthEngineAuth.NOT_CONFIGURED)
                .asRuntimeException()
            }
          }
      val fetcher = GoogleEarthEngineMapFetcher(auth = auth, httpClient = httpClient)
      return create(ttlSeconds = DEFAULT_TTL_SECONDS, clock = clock, fetcher = fetcher::fetchMap)
    }
  }
}

private data class CachedEntry(val urlTemplate: String, val expiresAt: Instant)

/** In-memory cache implementation of [EarthEngineTileClient]. */
private class CachingEarthEngineTileClient(
  private val ttlSeconds: Long,
  private val clock: () -> Instant,
  private val fetcher: (Expression) -> String,
) : EarthEngineTileClient {
  private val cache = ConcurrentHashMap<ByteString, CachedEntry>()

  override fun resolve(expression: Expression): ResolvedTileUrl {
    val key = expression.encodeByteString()
    val now = clock()
    val cached = cache[key]

    if (cached != null && cached.expiresAt.isAfter(now)) {
      val remaining = Duration.between(now, cached.expiresAt).seconds.coerceAtLeast(1L)
      return ResolvedTileUrl(urlTemplate = cached.urlTemplate, ttlSeconds = remaining)
    }

    val urlTemplate = fetcher(expression)
    val expiresAt = now.plusSeconds(ttlSeconds)
    cache[key] = CachedEntry(urlTemplate = urlTemplate, expiresAt = expiresAt)
    return ResolvedTileUrl(urlTemplate = urlTemplate, ttlSeconds = ttlSeconds)
  }
}

/** Calls the Google Earth Engine `maps.create` REST API to evaluate an [Expression]. */
internal class GoogleEarthEngineMapFetcher(
  private val auth: EarthEngineAuth,
  private val httpClient: HttpClient,
) {
  private val expressionAdapter = protoJsonMoshi.adapter(Expression::class.java)
  private val mapResponseAdapter =
    protoJsonMoshi.adapter<Map<String, Any>>(
      Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

  fun fetchMap(expression: Expression): String {
    val token = auth.accessToken()
    val projectId = auth.projectId

    val expressionJson = expressionAdapter.toJson(expression)
    val requestBody = """{"expression":$expressionJson,"fileFormat":"PNG"}"""

    val uri = URI.create("https://earthengine.googleapis.com/v1/projects/$projectId/maps")
    val request =
      HttpRequest.newBuilder()
        .uri(uri)
        .header("Authorization", "Bearer $token")
        .header("Content-Type", "application/json; charset=utf-8")
        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
        .build()

    val response =
      try {
        httpClient.send(request, HttpResponse.BodyHandlers.ofString())
      } catch (e: Exception) {
        throw Status.UNAVAILABLE.withDescription(
            "Failed to communicate with Earth Engine API: ${e.message}"
          )
          .withCause(e)
          .asRuntimeException()
      }

    if (response.statusCode() !in 200..299) {
      throw earthEngineHttpError(response.statusCode(), response.body())
    }

    val parsed =
      mapResponseAdapter.fromJson(response.body())
        ?: throw Status.INTERNAL.withDescription("Empty response from Earth Engine API")
          .asRuntimeException()

    val mapName =
      parsed["name"] as? String
        ?: throw Status.INTERNAL.withDescription(
            "Missing 'name' field in Earth Engine response: ${response.body()}"
          )
          .asRuntimeException()

    return "https://earthengine.googleapis.com/v1/$mapName/tiles/{z}/{x}/{y}"
  }
}
