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

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.openforis.collectone.v2.app.domain.repository.ProfileImageLoader

@OptIn(ExperimentalTestApi::class)
class ProfileImageCacheTest {
  /** A fresh cache per test, so no test sees another's decoded images. */
  private val cache = ProfileImageCache()

  @Test
  fun photoUrl_loadsThroughTheProvidedLoader() = runDesktopComposeUiTest {
    val loader = FakeProfileImageLoader(fetched = mapOf(PHOTO_URL to pngBytes(width = 3)))
    var bitmap: ImageBitmap? = null

    setContent {
      CompositionLocalProvider(
        LocalProfileImageLoader provides loader,
        LocalProfileImageCache provides cache,
      ) {
        bitmap = rememberProfileImageBitmap(PHOTO_URL)
      }
    }
    waitForIdle()

    assertEquals(3, assertNotNull(bitmap).width)
    assertEquals(listOf(PHOTO_URL), loader.fetchedUrls)
  }

  @Test
  fun bytesTheLoaderKeeps_showWithoutFetching() = runDesktopComposeUiTest {
    val loader = FakeProfileImageLoader(cached = mapOf(PHOTO_URL to pngBytes(width = 2)))
    var bitmap: ImageBitmap? = null

    setContent {
      CompositionLocalProvider(
        LocalProfileImageLoader provides loader,
        LocalProfileImageCache provides cache,
      ) {
        bitmap = rememberProfileImageBitmap(PHOTO_URL)
      }
    }
    waitForIdle()

    assertEquals(2, assertNotNull(bitmap).width)
    assertTrue(loader.fetchedUrls.isEmpty())
  }

  @Test
  fun withoutAProvidedLoader_showsNoImage() = runDesktopComposeUiTest {
    var bitmap: ImageBitmap? = placeholderImage()

    setContent {
      CompositionLocalProvider(LocalProfileImageCache provides cache) {
        bitmap = rememberProfileImageBitmap(PHOTO_URL)
      }
    }
    waitForIdle()

    assertNull(bitmap)
  }

  @Test
  fun unloadableOrUnreadablePhoto_showsNoImage() = runDesktopComposeUiTest {
    val loader = FakeProfileImageLoader(fetched = mapOf(PHOTO_URL to "not an image".toByteArray()))
    var unreadable: ImageBitmap? = placeholderImage()
    var missing: ImageBitmap? = placeholderImage()

    setContent {
      CompositionLocalProvider(
        LocalProfileImageLoader provides loader,
        LocalProfileImageCache provides cache,
      ) {
        unreadable = rememberProfileImageBitmap(PHOTO_URL)
        missing = rememberProfileImageBitmap("https://example.org/missing.png")
      }
    }
    waitForIdle()

    assertNull(unreadable)
    assertNull(missing)
  }

  @Test
  fun nonHttpPhotoUrl_isNotLoaded() = runDesktopComposeUiTest {
    val loader = FakeProfileImageLoader(fetched = mapOf("file:///maya.png" to pngBytes(width = 1)))
    var bitmap: ImageBitmap? = placeholderImage()

    setContent {
      CompositionLocalProvider(
        LocalProfileImageLoader provides loader,
        LocalProfileImageCache provides cache,
      ) {
        bitmap = rememberProfileImageBitmap(" file:///maya.png ")
      }
    }
    waitForIdle()

    assertNull(bitmap)
    assertTrue(loader.fetchedUrls.isEmpty())
  }

  @Test
  fun decodedImages_areReusedWithoutAskingTheLoaderAgain() = runBlocking {
    val loader = FakeProfileImageLoader(fetched = mapOf(PHOTO_URL to pngBytes(width = 4)))

    val first = cache.fetch(PHOTO_URL, loader)
    val second = cache.fetch(PHOTO_URL, loader)

    assertSame(first, second)
    assertEquals(listOf(PHOTO_URL), loader.fetchedUrls)
  }

  @Test
  fun aFullCache_evictsTheLeastRecentlyUsedImage() = runBlocking {
    val small = ProfileImageCache(maxImages = 2)
    val urls = (1..3).map { "https://example.org/$it.png" }
    val loader = FakeProfileImageLoader(fetched = urls.associateWith { pngBytes(width = 1) })
    small.fetch(urls[0], loader)
    small.fetch(urls[1], loader)

    small.get(urls[0], loader) // Uses the first image again, so the second is now the oldest.
    small.fetch(urls[2], loader)

    assertNotNull(small.get(urls[0], loader))
    assertNull(small.get(urls[1], loader))
    assertNotNull(small.get(urls[2], loader))
  }

  @Test
  fun failedFetches_areRetriedLater() = runBlocking {
    val loader = FailingOnceProfileImageLoader(pngBytes(width = 1))

    assertNull(cache.fetch(PHOTO_URL, loader))
    assertNotNull(cache.fetch(PHOTO_URL, loader))
  }

  @Test
  fun dataImageUri_decodesDirectlyWithoutNetworkLoader() = runDesktopComposeUiTest {
    val dataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(pngBytes(width = 5))
    val loader = FakeProfileImageLoader()
    var bitmap: ImageBitmap? = null

    setContent {
      CompositionLocalProvider(
        LocalProfileImageLoader provides loader,
        LocalProfileImageCache provides cache,
      ) {
        bitmap = rememberProfileImageBitmap(dataUri)
      }
    }
    waitForIdle()

    assertEquals(5, assertNotNull(bitmap).width)
    assertTrue(loader.fetchedUrls.isEmpty())
  }

  @Test
  fun invalidDataImageUri_returnsNull() = runDesktopComposeUiTest {
    var badBase64: ImageBitmap? = placeholderImage()
    var nonImageData: ImageBitmap? = placeholderImage()

    setContent {
      CompositionLocalProvider(LocalProfileImageCache provides cache) {
        badBase64 = rememberProfileImageBitmap("data:image/png;base64,!!!not-base64!!!")
        nonImageData = rememberProfileImageBitmap("data:text/plain;base64,SGVsbG8=")
      }
    }
    waitForIdle()

    assertNull(badBase64)
    assertNull(nonImageData)
  }

  /** Throws on its first fetch (as when offline), then returns [bytes]. */
  private class FailingOnceProfileImageLoader(private val bytes: ByteArray) : ProfileImageLoader {
    private var failed = false

    override fun cachedBytes(url: String): ByteArray? = null

    override suspend fun fetchBytes(url: String): ByteArray? {
      if (!failed) {
        failed = true
        error("offline")
      }
      return bytes
    }
  }

  /** A [ProfileImageLoader] over fixed bytes, recording the URLs it fetched. */
  private class FakeProfileImageLoader(
    private val cached: Map<String, ByteArray> = emptyMap(),
    private val fetched: Map<String, ByteArray> = emptyMap(),
  ) : ProfileImageLoader {
    val fetchedUrls = mutableListOf<String>()

    override fun cachedBytes(url: String): ByteArray? = cached[url]

    override suspend fun fetchBytes(url: String): ByteArray? {
      fetchedUrls += url
      return fetched[url]
    }
  }

  private companion object {
    const val PHOTO_URL = "https://example.org/maya.png"

    fun pngBytes(width: Int): ByteArray =
      ByteArrayOutputStream().use { out ->
        ImageIO.write(BufferedImage(width, 1, BufferedImage.TYPE_INT_ARGB), "png", out)
        out.toByteArray()
      }

    /** A placeholder that each test expects to be replaced by `null`. */
    fun placeholderImage(): ImageBitmap = ImageBitmap(1, 1)
  }
}
