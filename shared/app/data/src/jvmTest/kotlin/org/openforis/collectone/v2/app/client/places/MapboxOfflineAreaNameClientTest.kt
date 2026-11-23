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
package org.openforis.collectone.v2.app.client.places

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.openforis.collectone.v2.map.LngLatBounds

/** [MapboxOfflineAreaNameClient] over a mocked Mapbox Geocoding v6 endpoint. */
class MapboxOfflineAreaNameClientTest {
  private val bounds = LngLatBounds(36.90, -0.40, 36.95, -0.36)

  private fun client(engine: MockEngine, timeoutMillis: Long = 5_000) =
    MapboxOfflineAreaNameClient(
      httpClient = { HttpClient(engine) },
      accessToken = { "pk.test" },
      timeoutMillis = timeoutMillis,
    )

  @Test
  fun namesTheAreaFromTheReverseLookupOfItsCenter() {
    val requested = mutableListOf<String>()
    val engine = MockEngine { request ->
      requested += request.url.toString()
      respond(
        """{"features":[{"properties":{"name":"Othaya","feature_type":"place",""" +
          """"context":{"region":{"name":"Nyeri"}}}}]}""",
        HttpStatusCode.OK,
        headersOf(HttpHeaders.ContentType, "application/json"),
      )
    }

    assertEquals("Othaya, Nyeri", runBlocking { client(engine).name(bounds, 12.0) })
    val url = requested.single()
    assertEquals(
      "/search/geocode/v6/reverse",
      url.substringAfter("api.mapbox.com").substringBefore("?"),
    )
    listOf("longitude=36.92", "latitude=-0.38", "permanent=true", "limit=1").forEach {
      assert(it in url) { "$it in $url" }
    }
  }

  @Test
  fun httpErrors_haveNoName() {
    val engine = MockEngine { respond("{}", HttpStatusCode.Unauthorized) }

    assertNull(runBlocking { client(engine).name(bounds, 12.0) })
  }

  @Test
  fun slowResponses_timeOutWithoutAName() {
    val engine = MockEngine {
      delay(1_000)
      respond("""{"features":[{"properties":{"name":"Late"}}]}""", HttpStatusCode.OK)
    }

    assertNull(runBlocking { client(engine, timeoutMillis = 50).name(bounds, 12.0) })
  }

  @Test
  fun networkFailures_haveNoName() {
    val engine = MockEngine { error("connection reset") }

    assertNull(runBlocking { client(engine).name(bounds, 12.0) })
  }
}
