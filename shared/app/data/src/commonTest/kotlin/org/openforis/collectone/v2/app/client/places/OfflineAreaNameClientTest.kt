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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.map.LngLatBounds

class OfflineAreaNameClientTest {
  private fun response(name: String, featureType: String, context: String = "{}") =
    """
    {"type":"FeatureCollection","features":[{"type":"Feature",
      "properties":{"name":"$name","feature_type":"$featureType","context":$context}}]}
    """
      .trimIndent()

  @Test
  fun aPlace_isNamedWithItsRegion() {
    assertEquals(
      "Othaya, Nyeri",
      OfflineAreaNameResponseMapper.map(
        response(
          "Othaya",
          "place",
          """{"region":{"name":"Nyeri"},"country":{"name":"Kenya"}}""",
        )
      ),
    )
  }

  @Test
  fun aNeighborhood_isNamedWithItsPlace() {
    assertEquals(
      "Kilimani, Nairobi",
      OfflineAreaNameResponseMapper.map(
        response(
          "Kilimani",
          "neighborhood",
          """{"place":{"name":"Nairobi"},"region":{"name":"Nairobi County"}}""",
        )
      ),
    )
  }

  @Test
  fun aRegion_isNamedWithItsCountry_andRepeatedNamesAreSkipped() {
    assertEquals(
      "Nyeri, Kenya",
      OfflineAreaNameResponseMapper.map(
        response("Nyeri", "region", """{"country":{"name":"Kenya"}}""")
      ),
    )
    assertEquals(
      "Nairobi, Kenya",
      OfflineAreaNameResponseMapper.map(
        response(
          "Nairobi",
          "place",
          """{"region":{"name":"Nairobi"},"country":{"name":"Kenya"}}""",
        )
      ),
    )
  }

  @Test
  fun aNameWithoutContext_standsAlone() {
    assertEquals("Othaya", OfflineAreaNameResponseMapper.map(response("Othaya", "place")))
  }

  @Test
  fun emptyOrInvalidResponses_haveNoName() {
    assertNull(OfflineAreaNameResponseMapper.map("""{"type":"FeatureCollection","features":[]}"""))
    assertNull(OfflineAreaNameResponseMapper.map("not json"))
    assertNull(OfflineAreaNameResponseMapper.map(response("", "place")))
  }

  @Test
  fun placeTypes_getMoreGeneralAsTheMapZoomsOut() {
    assertEquals("neighborhood,locality", MapboxOfflineAreaNameClient.placeTypes(15.0))
    assertEquals("neighborhood,locality", MapboxOfflineAreaNameClient.placeTypes(14.0))
    assertEquals("locality,place", MapboxOfflineAreaNameClient.placeTypes(12.0))
    assertEquals("district,region", MapboxOfflineAreaNameClient.placeTypes(10.9))
  }

  @Test
  fun reverseUrl_asksForOnePermanentResultAtTheCenter() {
    val url = MapboxOfflineAreaNameClient.reverseUrl(LatLng(-0.4, 36.9), 13.0, "pk.test")

    assertTrue(url.startsWith("https://api.mapbox.com/search/geocode/v6/reverse?"), url)
    listOf(
        "longitude=36.9",
        "latitude=-0.4",
        "permanent=true",
        "limit=1",
        "types=locality%2Cplace",
        "access_token=pk.test",
      )
      .forEach { assertTrue(it in url, "$it in $url") }
  }

  @Test
  fun mapbox_withoutAClientOrAPublicToken_findsNoName() = runTest {
    val bounds = LngLatBounds(36.9, -0.4, 36.95, -0.37)

    assertNull(MapboxOfflineAreaNameClient(httpClient = { null }).name(bounds, 13.0))
    assertNull(
      MapboxOfflineAreaNameClient(httpClient = { null }, accessToken = { "sk.secret" })
        .name(bounds, 13.0)
    )
  }

  @Test
  fun placeholderNames_areStableNearbyAndVaryFarther() = runTest {
    val client = PlaceholderOfflineAreaNameClient()
    val here = client.name(LngLatBounds(36.90, -0.40, 36.95, -0.37), 13.0)
    // The same center to 0.05°, framed differently.
    val again = client.name(LngLatBounds(36.89, -0.41, 36.96, -0.36), 15.0)

    assertEquals(here, again)
    assertTrue(here in PlaceholderOfflineAreaNameClient.NAMES)
    val names =
      (0 until 40).map { PlaceholderOfflineAreaNameClient.nameAt(LatLng(0.0, it * 0.05)) }.toSet()
    assertEquals(PlaceholderOfflineAreaNameClient.NAMES.toSet(), names)
    assertNotEquals(
      PlaceholderOfflineAreaNameClient.nameAt(LatLng(0.0, 0.0)),
      PlaceholderOfflineAreaNameClient.nameAt(LatLng(0.0, 0.05)),
    )
  }
}
