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
package org.openforis.collectone.v2.app.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.openforis.collectone.v2.app.client.places.createThumbnailHttpClient
import org.openforis.collectone.v2.app.client.places.resolveMapboxPublicToken
import org.openforis.collectone.v2.app.domain.repository.SurveyMapThumbnailRepository

/**
 * Loads and LRU-caches Mapbox Static Images snapshots over HTTP for survey card thumbnails.
 *
 * Cache keys are the normalized Mapbox Static Images request URLs, which encode the survey's framed
 * bounds/camera and GeoJSON map feature overlays. Any change to a survey's map features or area
 * produces a new URL and naturally invalidates the previous thumbnail while keeping prior snapshots
 * bounded by [maxCacheEntries].
 */
class SurveyMapThumbnailRepositoryImpl(
  httpClientProvider: () -> HttpClient? = ::createThumbnailHttpClient,
  tokenProvider: () -> String? = ::resolveMapboxPublicToken,
  private val maxCacheEntries: Int = DEFAULT_MAX_CACHED_TILES,
) : SurveyMapThumbnailRepository {
  private val httpClient: HttpClient? by lazy(httpClientProvider)
  private val cacheMutex = Mutex()
  private val byteCache = LinkedHashMap<String, ByteArray>()
  private val inFlight = HashMap<String, CompletableDeferred<ByteArray?>>()

  override val mapboxPublicToken: String? by lazy(tokenProvider)

  override suspend fun fetchTile(url: String): ByteArray? {
    val client = httpClient ?: return null
    val requestUrl = encodeUrlPathBrackets(url)
    val (deferred, isLeader) =
      cacheMutex.withLock {
        byteCache.remove(requestUrl)?.let { hit ->
          byteCache[requestUrl] = hit
          return hit
        }
        val existing = inFlight[requestUrl]
        if (existing != null) {
          existing to false
        } else {
          val created = CompletableDeferred<ByteArray?>()
          inFlight[requestUrl] = created
          created to true
        }
      }
    if (!isLeader) {
      return deferred.await()
    }
    return try {
      val fetched = fetchFromNetwork(client, requestUrl)
      cacheMutex.withLock {
        inFlight.remove(requestUrl)
        if (fetched != null) {
          storeLocked(requestUrl, fetched)
        }
      }
      deferred.complete(fetched)
      fetched
    } catch (e: CancellationException) {
      cacheMutex.withLock { inFlight.remove(requestUrl) }
      deferred.complete(null)
      throw e
    } catch (_: Throwable) {
      cacheMutex.withLock { inFlight.remove(requestUrl) }
      deferred.complete(null)
      null
    }
  }

  suspend fun clearCache() {
    cacheMutex.withLock { byteCache.clear() }
  }

  suspend fun cachedTileCount(): Int = cacheMutex.withLock { byteCache.size }

  private suspend fun fetchFromNetwork(client: HttpClient, requestUrl: String): ByteArray? =
    withTimeoutOrNull(IMAGE_FETCH_TIMEOUT_MS) {
      val response = client.get(requestUrl)
      if (!response.status.isSuccess()) return@withTimeoutOrNull null
      response.body<ByteArray>().takeIf { it.isNotEmpty() }
    }

  private fun storeLocked(requestUrl: String, bytes: ByteArray) {
    if (maxCacheEntries <= 0) return
    while (byteCache.size >= maxCacheEntries) {
      val oldestKey = byteCache.keys.firstOrNull() ?: break
      byteCache.remove(oldestKey)
    }
    byteCache[requestUrl] = bytes
  }

  private companion object {
    const val IMAGE_FETCH_TIMEOUT_MS = 8_000L
    const val DEFAULT_MAX_CACHED_TILES = 256

    /**
     * Percent-encodes raw `[` / `]` in the URL path before `?` so bounding-box fallback URLs
     * (`.../static/[w,s,e,n]/...`) parse cleanly across all Ktor platform engines (`NSURL`, `URI`).
     */
    fun encodeUrlPathBrackets(url: String): String {
      val queryIndex = url.indexOf('?')
      val path = if (queryIndex >= 0) url.substring(0, queryIndex) else url
      if ('[' !in path && ']' !in path) return url
      val encodedPath = path.replace("[", "%5B").replace("]", "%5D")
      return if (queryIndex >= 0) encodedPath + url.substring(queryIndex) else encodedPath
    }
  }
}
