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
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.MapLayerItem
import org.openforis.collectone.v2.app.domain.model.SurveyThumbnailGenerator
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng

class SurveyMapThumbnailRepositoryImplTest {
  @Test
  fun fetchTile_returnsImageBytesAndEncodesBoundingBoxPathBrackets() = runBlocking {
    val requestedPaths = mutableListOf<String>()
    val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
    val engine = MockEngine { request ->
      requestedPaths += request.url.encodedPath
      when {
        request.url.encodedPath.endsWith("/404") ->
          respond("missing", status = HttpStatusCode.NotFound)
        request.url.encodedPath.endsWith("/empty") ->
          respond(ByteArray(0), status = HttpStatusCode.OK)
        else ->
          respond(
            content = jpegBytes,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Image.JPEG.toString()),
          )
      }
    }

    val repo =
      SurveyMapThumbnailRepositoryImpl(
        httpClientProvider = { HttpClient(engine) },
        tokenProvider = { "pk.test_public_token" },
      )
    assertEquals("pk.test_public_token", repo.mapboxPublicToken)

    val bytes =
      repo.fetchTile(
        "https://api.mapbox.com/styles/v1/mapbox/satellite-streets-v12/static/[36.65,-3.405,36.71,-3.355]/640x280@2x?padding=24&access_token=pk.test"
      )
    assertContentEquals(jpegBytes, bytes)
    assertEquals(
      "/styles/v1/mapbox/satellite-streets-v12/static/%5B36.65,-3.405,36.71,-3.355%5D/640x280@2x",
      requestedPaths.single(),
    )

    assertNull(repo.fetchTile("https://api.mapbox.com/404"))
    assertNull(repo.fetchTile("https://api.mapbox.com/empty"))
  }

  @Test
  fun fetchTile_returnsNullWhenClientUnavailableOrRequestThrows() = runBlocking {
    val noClientRepo =
      SurveyMapThumbnailRepositoryImpl(httpClientProvider = { null }, tokenProvider = { null })
    assertNull(noClientRepo.mapboxPublicToken)
    assertNull(noClientRepo.fetchTile("https://api.mapbox.com/test.jpg"))

    val throwingEngine = MockEngine { error("offline") }
    val offlineRepo =
      SurveyMapThumbnailRepositoryImpl(
        httpClientProvider = { HttpClient(throwingEngine) },
        tokenProvider = { null },
      )
    assertNull(offlineRepo.fetchTile("https://api.mapbox.com/test.jpg"))
  }

  @Test
  fun fetchTile_cachesSuccessfulSnapshotsAndInvalidatesWhenMapFeaturesChange() = runBlocking {
    var requestCount = 0
    var failNext = false
    val engine = MockEngine { request ->
      requestCount++
      if (failNext) {
        respond("error", status = HttpStatusCode.ServiceUnavailable)
      } else {
        respond(
          content = request.url.encodedPath.encodeToByteArray(),
          status = HttpStatusCode.OK,
          headers = headersOf(HttpHeaders.ContentType, ContentType.Image.PNG.toString()),
        )
      }
    }

    val repo =
      SurveyMapThumbnailRepositoryImpl(
        httpClientProvider = { HttpClient(engine) },
        tokenProvider = { "pk.test" },
        maxCacheEntries = 2,
      )

    val layer =
      MapLayerItem(
        id = "layer-plots",
        label = "Plots",
        colorHex = 0xFF00897BL,
        geometryType = GeometryType.POINT,
        isVisible = true,
      )
    val entity1 =
      GeospatialEntityItem(
        id = "p1",
        label = "Plot 1",
        datasetId = "plots",
        datasetName = "Plots",
        layerId = "layer-plots",
        geoId = "P-1",
        geometry = Geometry.Point(LatLng(-3.385, 36.682)),
        areaHectares = 0.0,
        perimeterMeters = 0,
        colorHex = 0xFF00897BL,
        properties = emptyMap(),
        submissions = emptyList(),
      )
    val entity2 =
      entity1.copy(
        id = "p2",
        label = "Plot 2",
        geoId = "P-2",
        geometry = Geometry.Point(LatLng(-3.392, 36.691)),
      )

    val thumb1 =
      SurveyThumbnailGenerator.fromRuntime(
        surveyId = "s1",
        surveyArea = null,
        mapLayers = listOf(layer),
        entities = listOf(entity1),
      )
    val url1 = thumb1.staticImageUrl("pk.test")

    // First fetch hits the network; second fetch is served from the LRU byte cache.
    val firstBytes = repo.fetchTile(url1)
    val cachedBytes = repo.fetchTile(url1)
    assertContentEquals(firstBytes, cachedBytes)
    assertEquals(1, requestCount)
    assertEquals(1, repo.cachedTileCount())

    // Adding a map feature produces a new staticImageUrl, invalidating the previous cache key.
    val thumb2 =
      SurveyThumbnailGenerator.fromRuntime(
        surveyId = "s1",
        surveyArea = null,
        mapLayers = listOf(layer),
        entities = listOf(entity1, entity2),
      )
    val url2 = thumb2.staticImageUrl("pk.test")
    assertNotEquals(url1, url2)

    val updatedBytes = repo.fetchTile(url2)
    assertEquals(2, requestCount)
    assertEquals(2, repo.cachedTileCount())
    assertNotEquals(firstBytes?.decodeToString(), updatedBytes?.decodeToString())

    // Failed responses are not cached, so a subsequent retry succeeds.
    failNext = true
    assertNull(repo.fetchTile("https://api.mapbox.com/transient.png"))
    assertEquals(3, requestCount)
    failNext = false
    val recovered = repo.fetchTile("https://api.mapbox.com/transient.png")
    assertContentEquals("/transient.png".encodeToByteArray(), recovered)
    assertEquals(4, requestCount)
    assertEquals(2, repo.cachedTileCount())
  }
}
