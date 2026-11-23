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

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.seed.SampleSurveysData
import org.openforis.collectone.v2.app.domain.repository.SurveyMapThumbnailRepository

@OptIn(ExperimentalTestApi::class, ExperimentalEncodingApi::class)
class SurveyMapThumbnailUiTest {
  private val pngBytes =
    Base64.decode(
      "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
    )

  @Test
  fun surveyMapThumbnail_fetchesAndCachesStaticImageWhenTokenPresent() = runComposeUiTest {
    val kenya =
      SampleSurveysData.defaultSampleSurveys().first {
        it.id == "00000033-6b50-51cd-ac94-baea2ea06d65"
      }
    val fetchedUrls = mutableListOf<String>()
    val repo =
      object : SurveyMapThumbnailRepository {
        override val mapboxPublicToken: String = "pk.test_ui_token"

        override suspend fun fetchTile(url: String): ByteArray {
          fetchedUrls += url
          return pngBytes
        }
      }
    val cache = SurveyThumbnailTileCache(maxTiles = 8)

    setContent {
      MaterialTheme {
        CompositionLocalProvider(
          LocalSurveyMapThumbnailRepository provides repo,
          LocalSurveyThumbnailTileCache provides cache,
        ) {
          SurveyMapThumbnail(
            thumbnail = kenya.thumbnail,
            isDownloaded = true,
            modifier = Modifier.width(320.dp).height(140.dp).testTag("hero-thumb"),
          )
        }
      }
    }

    waitForIdle()
    onNodeWithTag("hero-thumb").assertWidthIsEqualTo(320.dp).assertHeightIsEqualTo(140.dp)
    assertTrue(fetchedUrls.isNotEmpty(), "Expected Mapbox Static Images fetch")
    val expectedUrl =
      kenya.thumbnail.staticImageUrl(
        accessToken = "pk.test_ui_token",
        widthPx = 640,
        heightPx = 288,
      )
    assertTrue(fetchedUrls.contains(expectedUrl), "Fetched URLs were: $fetchedUrls")
    assertNotNull(cache.get(expectedUrl))
  }

  @Test
  fun surveyMapThumbnail_matchesWideMobileHeroAspectRatio() = runComposeUiTest {
    val kenya =
      SampleSurveysData.defaultSampleSurveys().first {
        it.id == "00000033-6b50-51cd-ac94-baea2ea06d65"
      }
    val fetchedUrls = mutableListOf<String>()
    val repo =
      object : SurveyMapThumbnailRepository {
        override val mapboxPublicToken: String = "pk.test_mobile_token"

        override suspend fun fetchTile(url: String): ByteArray {
          fetchedUrls += url
          return pngBytes
        }
      }

    setContent {
      MaterialTheme {
        CompositionLocalProvider(
          LocalSurveyMapThumbnailRepository provides repo,
          LocalSurveyThumbnailTileCache provides SurveyThumbnailTileCache(maxTiles = 8),
        ) {
          SurveyMapThumbnail(
            thumbnail = kenya.thumbnail,
            isDownloaded = true,
            modifier = Modifier.width(382.dp).height(136.dp).testTag("mobile-hero-thumb"),
          )
        }
      }
    }

    waitForIdle()
    onNodeWithTag("mobile-hero-thumb").assertWidthIsEqualTo(382.dp).assertHeightIsEqualTo(136.dp)
    val expectedUrl =
      kenya.thumbnail.staticImageUrl(
        accessToken = "pk.test_mobile_token",
        widthPx = 640,
        heightPx = 224,
      )
    assertTrue(fetchedUrls.contains(expectedUrl), "Fetched URLs were: $fetchedUrls")
  }

  @Test
  fun surveyMapThumbnail_rendersOfflineVectorFallbackWhenNoTokenConfigured() = runComposeUiTest {
    val kenya =
      SampleSurveysData.defaultSampleSurveys().first {
        it.id == "00000033-6b50-51cd-ac94-baea2ea06d65"
      }

    setContent {
      MaterialTheme {
        SurveyMapThumbnail(
          thumbnail = kenya.thumbnail,
          isDownloaded = false,
          modifier = Modifier.width(320.dp).height(140.dp).testTag("offline-thumb"),
        )
      }
    }

    waitForIdle()
    onNodeWithTag("offline-thumb").assertWidthIsEqualTo(320.dp).assertHeightIsEqualTo(140.dp)
  }

  @Test
  fun surveyMapThumbnail_omitsGeoJsonOverlayWhenSurveyHasNoInAreaFeatures() = runComposeUiTest {
    val arusha =
      SampleSurveysData.defaultSampleSurveys().first {
        it.id == "00000036-1a1c-5c51-997f-28b67d813aa3"
      }
    val aoiOnlyThumbnail = arusha.thumbnail.copy(features = emptyList())
    val fetchedUrls = mutableListOf<String>()
    val repo =
      object : SurveyMapThumbnailRepository {
        override val mapboxPublicToken: String = "pk.test_aoi_only"

        override suspend fun fetchTile(url: String): ByteArray {
          fetchedUrls += url
          return pngBytes
        }
      }

    setContent {
      MaterialTheme {
        CompositionLocalProvider(
          LocalSurveyMapThumbnailRepository provides repo,
          LocalSurveyThumbnailTileCache provides SurveyThumbnailTileCache(maxTiles = 8),
        ) {
          SurveyMapThumbnail(
            thumbnail = aoiOnlyThumbnail,
            isDownloaded = false,
            modifier = Modifier.width(320.dp).height(140.dp).testTag("arusha-thumb"),
          )
        }
      }
    }

    waitForIdle()
    onNodeWithTag("arusha-thumb").assertWidthIsEqualTo(320.dp).assertHeightIsEqualTo(140.dp)
    assertTrue(fetchedUrls.isNotEmpty())
    assertFalse(
      fetchedUrls.single().contains("/geojson("),
      "AOI-only survey must not emit geojson(...) bounding box overlay: ${fetchedUrls.single()}",
    )
  }
}
