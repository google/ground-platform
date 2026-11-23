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
package org.openforis.collectone.v2.app.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.openforis.collectone.v2.app.client.auth.platformCachedAvatar
import org.openforis.collectone.v2.app.client.auth.platformFetchAvatar
import org.openforis.collectone.v2.app.client.places.createThumbnailHttpClient
import org.openforis.collectone.v2.app.domain.repository.ProfileImageLoader

/**
 * Loads profile photos from the platform's avatar cache (in the browser, the sign-in bridge's),
 * falling back to plain HTTP.
 */
class ProfileImageLoaderImpl : ProfileImageLoader {
  private val httpClient: HttpClient? by lazy { createThumbnailHttpClient() }

  override fun cachedBytes(url: String): ByteArray? = platformCachedAvatar(url)

  override suspend fun fetchBytes(url: String): ByteArray? =
    platformFetchAvatar(url) ?: fetchOverHttp(url)

  /** The photo at [url] over HTTP; `null` on any failure, so the avatar shows initials. */
  private suspend fun fetchOverHttp(url: String): ByteArray? {
    val client = httpClient ?: return null
    return try {
      withTimeoutOrNull(FETCH_TIMEOUT_MS) {
        val response = client.get(url)
        if (response.status.isSuccess()) response.body<ByteArray>() else null
      }
    } catch (e: CancellationException) {
      throw e
    } catch (_: Throwable) {
      null
    }
  }

  private companion object {
    const val FETCH_TIMEOUT_MS = 5_000L
  }
}
