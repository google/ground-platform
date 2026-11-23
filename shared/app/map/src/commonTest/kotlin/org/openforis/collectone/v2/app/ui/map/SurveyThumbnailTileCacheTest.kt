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
package org.openforis.collectone.v2.app.ui.map

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest
import org.openforis.collectone.v2.app.domain.repository.SurveyMapThumbnailRepository

@OptIn(ExperimentalEncodingApi::class)
class SurveyThumbnailTileCacheTest {
  private val pngBytes =
    Base64.decode(
      "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
    )

  @Test
  fun fetchTile_reusesDecodedBitmapAndEvictsLeastRecentlyUsed() = runTest {
    val fetched = mutableListOf<String>()
    val repository =
      object : SurveyMapThumbnailRepository {
        override val mapboxPublicToken: String? = null

        override suspend fun fetchTile(url: String): ByteArray? {
          fetched += url
          return if (url.endsWith("bad.png")) "not-a-png".encodeToByteArray() else pngBytes
        }
      }

    val cache = SurveyThumbnailTileCache(maxTiles = 2)
    val first = assertNotNull(cache.fetchTile(repository, "https://tiles.test/1.png"))
    val second = assertNotNull(cache.fetchTile(repository, "https://tiles.test/1.png"))
    assertSame(first, second)
    assertEquals(listOf("https://tiles.test/1.png"), fetched)

    assertNotNull(cache.fetchTile(repository, "https://tiles.test/2.png"))
    // Touch 1.png so 2.png becomes the least recently used entry.
    assertSame(first, cache.get("https://tiles.test/1.png"))
    assertNotNull(cache.fetchTile(repository, "https://tiles.test/3.png"))

    assertNotNull(cache.get("https://tiles.test/1.png"))
    assertNull(cache.get("https://tiles.test/2.png"))
    assertNotNull(cache.get("https://tiles.test/3.png"))

    // Separate cache instances do not share entries, and unreadable bytes are not cached.
    val otherCache = SurveyThumbnailTileCache(maxTiles = 2)
    assertNull(otherCache.get("https://tiles.test/1.png"))
    assertNull(cache.fetchTile(repository, "https://tiles.test/bad.png"))
    assertNull(cache.get("https://tiles.test/bad.png"))
  }
}
