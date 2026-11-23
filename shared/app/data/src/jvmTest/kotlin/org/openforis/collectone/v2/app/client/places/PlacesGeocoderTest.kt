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
package org.openforis.collectone.v2.app.client.places

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.openforis.collectone.v2.app.domain.model.SurveyMapAnchor
import org.openforis.collectone.v2.map.LatLng

/** [PlacesGeocoder] over a mocked Mapbox Geocoding v5 endpoint: failures yield no results. */
class PlacesGeocoderTest {
  private val near = SurveyMapAnchor(LatLng(-0.42, 36.95), 12.0, "")
  private val requested = mutableListOf<String>()

  private fun okEngine(body: String = TWO_PLACES) = MockEngine { request ->
    requested += request.url.toString()
    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
  }

  private fun geocoder(
    engine: MockEngine? = okEngine(),
    token: String? = "pk.test",
    timeoutMillis: Long = 5_000,
  ) =
    PlacesGeocoder(
      httpClient = engine?.let(::HttpClient),
      accessToken = { token },
      timeoutMillis = timeoutMillis,
    )

  private fun search(
    geocoder: PlacesGeocoder,
    query: String = "Othaya",
    anchor: SurveyMapAnchor = near,
  ) = runBlocking { geocoder.search(query, anchor, defaultSubtitle = "Kenya") }

  @Test
  fun search_queriesNearTheSurveyAndMapsTheResults() {
    val places = search(geocoder(), query = "  Othaya ")

    assertEquals(listOf("Othaya", "Nyeri"), places.map { it.name })
    val url = requested.single()
    assertEquals(
      "/geocoding/v5/mapbox.places/Othaya.json",
      url.substringAfter("api.mapbox.com").substringBefore("?"),
    )
    listOf("access_token=pk.test", "proximity=36.95%2C-0.42", "limit=8").forEach {
      assertTrue(it in url, "$it in $url")
    }
  }

  @Test
  fun search_withoutASurveyCenter_sendsNoProximity() {
    search(geocoder(), anchor = SurveyMapAnchor(LatLng(0.0, 0.0), 2.0, ""))

    assertTrue("proximity" !in requested.single())
  }

  @Test
  fun search_withABlankQuery_sendsNoRequest() {
    assertEquals(emptyList(), search(geocoder(), query = "   "))
    assertEquals(emptyList(), requested)
  }

  @Test
  fun search_withoutAPublicToken_sendsNoRequest() {
    assertEquals(emptyList(), search(geocoder(token = "sk.secret")))
    assertEquals(emptyList(), search(geocoder(token = null)))
    assertEquals(emptyList(), requested)
  }

  @Test
  fun search_withoutAnHttpClient_hasNoResults() {
    assertEquals(emptyList(), search(geocoder(engine = null)))
  }

  @Test
  fun search_httpErrorsAndFailures_haveNoResults() {
    val unauthorized = MockEngine { respond("{}", HttpStatusCode.Unauthorized) }
    val failing = MockEngine { error("connection reset") }

    assertEquals(emptyList(), search(geocoder(engine = unauthorized)))
    assertEquals(emptyList(), search(geocoder(engine = failing)))
  }

  @Test
  fun search_slowResponses_timeOutWithoutResults() {
    val slow = MockEngine {
      delay(1_000)
      respond(TWO_PLACES, HttpStatusCode.OK)
    }

    assertEquals(emptyList(), search(geocoder(engine = slow, timeoutMillis = 50)))
  }

  @Test
  fun reverseGeocodePlaceLabels_namesUpToTheLimitAroundTheCenter() {
    val labels = runBlocking {
      geocoder().reverseGeocodePlaceLabels(LatLng(-0.42, 36.95), limit = 1)
    }

    assertEquals(listOf("Othaya"), labels.map { it.name })
    val url = requested.single()
    assertEquals(
      "/geocoding/v5/mapbox.places/36.95,-0.42.json",
      url.substringAfter("api.mapbox.com").substringBefore("?"),
    )
    assertTrue("limit=4" in url, url)
  }

  @Test
  fun reverseGeocodePlaceLabels_failures_haveNoLabels() {
    val unauthorized = MockEngine { respond("{}", HttpStatusCode.Unauthorized) }

    assertEquals(
      emptyList(),
      runBlocking { geocoder(engine = unauthorized).reverseGeocodePlaceLabels(LatLng(1.0, 2.0)) },
    )
    assertEquals(
      emptyList(),
      runBlocking { geocoder(token = null).reverseGeocodePlaceLabels(LatLng(1.0, 2.0)) },
    )
  }

  private companion object {
    val TWO_PLACES =
      """
      {"features":[
        {"id":"place.1","text":"Othaya","place_name":"Othaya, Nyeri, Kenya",
         "place_type":["place"],"center":[36.94,-0.55]},
        {"id":"region.2","text":"Nyeri","place_name":"Nyeri, Kenya",
         "place_type":["region"],"center":[36.95,-0.42]}
      ]}
      """
        .trimIndent()
  }
}
