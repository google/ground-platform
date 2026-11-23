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
package org.openforis.collectone.v2.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.openforis.collectone.v2.app.domain.repository.ProfileImageLoader

/**
 * Where profile pictures load from. The app session provides the data layer's loader; without one,
 * avatars show the user's initials.
 */
val LocalProfileImageLoader =
  staticCompositionLocalOf<ProfileImageLoader> { ProfileImageLoader.None }

/**
 * Decoded profile pictures ([ImageBitmap]) keyed by photo URL, over the bytes a
 * [ProfileImageLoader] loads, so a user's avatar repeated across a screen (lists, cards, menus) is
 * decoded once.
 *
 * Holds at most [maxImages] bitmaps and evicts the least recently used one first. It's only used
 * from composition and the effects it launches, which run on the UI thread, so it isn't
 * synchronized.
 *
 * One cache serves the whole composition tree through [LocalProfileImageCache]. Its default
 * instance lives as long as the process; a host that wants the decoded images dropped with its
 * session (or that switches [ProfileImageLoader]s, whose images must not mix) provides its own.
 */
class ProfileImageCache(private val maxImages: Int = DEFAULT_MAX_IMAGES) {
  // Insertion order is recency order: hits move to the end, evictions take the first key.
  private val bitmaps = LinkedHashMap<String, ImageBitmap>()

  /** The image at [url] if it's decoded already or [loader] has its bytes without waiting. */
  fun get(url: String, loader: ProfileImageLoader): ImageBitmap? {
    bitmaps.remove(url)?.let { hit ->
      bitmaps[url] = hit
      return hit
    }
    if (isDataImageUri(url)) {
      val decoded = decodeDataImageUri(url) ?: return null
      return decodeAndStore(url, decoded)
    }
    val cachedBytes = loader.cachedBytes(url) ?: return null
    return decodeAndStore(url, cachedBytes)
  }

  /**
   * The image at [url], loading it with [loader] if needed; `null` when it can't be loaded or
   * decoded (avatars then show initials). Failures aren't remembered, so a later call retries.
   */
  suspend fun fetch(url: String, loader: ProfileImageLoader): ImageBitmap? {
    get(url, loader)?.let {
      return it
    }
    return try {
      val bytes = loader.fetchBytes(url) ?: return null
      decodeAndStore(url, bytes)
    } catch (e: CancellationException) {
      throw e
    } catch (_: Throwable) {
      null
    }
  }

  private fun decodeAndStore(url: String, bytes: ByteArray): ImageBitmap? {
    if (bytes.isEmpty()) return null
    return try {
      val bitmap = bytes.decodeToImageBitmap()
      if (bitmaps.size >= maxImages) {
        bitmaps.keys.firstOrNull()?.let(bitmaps::remove)
      }
      bitmaps[url] = bitmap
      bitmap
    } catch (_: Throwable) {
      null
    }
  }

  private companion object {
    const val DEFAULT_MAX_IMAGES = 64
  }
}

internal fun isDataImageUri(url: String): Boolean = url.startsWith("data:image/", ignoreCase = true)

@OptIn(ExperimentalEncodingApi::class)
internal fun decodeDataImageUri(uri: String): ByteArray? {
  if (!isDataImageUri(uri)) return null
  val marker = ";base64,"
  val index = uri.indexOf(marker, ignoreCase = true)
  if (index < 0) return null
  val payload = uri.substring(index + marker.length).trim()
  if (payload.isEmpty()) return null
  return try {
    Base64.decode(payload)
  } catch (_: Throwable) {
    null
  }
}

/** The [ProfileImageCache] avatars decode into; see there for its lifetime. */
val LocalProfileImageCache = staticCompositionLocalOf { ProfileImageCache() }

/**
 * Remembers and loads the user's profile image or organization logo with [LocalProfileImageLoader]
 * when [photoUrl] is an HTTP/HTTPS URL or a `data:image/...;base64,...` URI, returning `null` while
 * loading or when unavailable so callers can render initials.
 */
@Composable
fun rememberProfileImageBitmap(photoUrl: String?): ImageBitmap? {
  val loader = LocalProfileImageLoader.current
  val cache = LocalProfileImageCache.current
  val validUrl =
    photoUrl?.trim()?.takeIf {
      it.startsWith("https://") || it.startsWith("http://") || isDataImageUri(it)
    }
  var bitmap by
    remember(validUrl, loader, cache) { mutableStateOf(validUrl?.let { cache.get(it, loader) }) }
  LaunchedEffect(validUrl, loader, cache) {
    if (validUrl != null && bitmap == null && !isDataImageUri(validUrl)) {
      bitmap = cache.fetch(validUrl, loader)
    }
  }
  return bitmap
}
