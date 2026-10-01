/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.devtools.prototypeapp.client.places

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyMapAnchor

class PlacesResponseMapperTest {
  private val near = SurveyMapAnchor.forSurvey(SurveyMapAnchor.DEFAULT_SURVEY_ID)

  @Test
  fun map_readsMapboxGeocodingFeatures() {
    val body =
      """
      {"type":"FeatureCollection","features":[{
        "id":"place.123","text":"Aberdare National Park",
        "place_name":"Aberdare National Park, Nyeri County, Kenya",
        "place_type":["poi"],"center":[36.7,-0.3833],
        "bbox":[36.55,-0.6,36.85,-0.15]
      }]}
      """
        .trimIndent()

    val place = PlacesResponseMapper.map(body, "Aberdare", near, "Kenya").single()

    assertEquals("place.123", place.id)
    assertEquals("Aberdare National Park", place.name)
    assertEquals("Poi", place.categoryLabel)
    assertEquals("Aberdare National Park, Nyeri County, Kenya", place.regionSubtitle)
    assertEquals(36.7, place.longitude)
    assertEquals(-0.3833, place.latitude)
    assertEquals(36.55, place.bboxMinLng)
    assertEquals(-0.15, place.bboxMaxLat)
    assertEquals("Places API", place.sourceLabel)
  }

  @Test
  fun map_readsNominatimFeatures() {
    val body =
      """
      {"type":"FeatureCollection","features":[{
        "type":"Feature",
        "properties":{"name":"Othaya","display_name":"Othaya, Nyeri, Kenya","addresstype":"town"},
        "geometry":{"type":"Point","coordinates":[36.9421,-0.5512]}
      }]}
      """
        .trimIndent()

    val place = PlacesResponseMapper.map(body, "Othaya", near, "Kenya").single()

    assertEquals("Othaya", place.name)
    assertEquals("Town", place.categoryLabel)
    assertEquals("Othaya, Nyeri, Kenya", place.regionSubtitle)
    assertEquals(36.9421, place.longitude)
    // Without a bbox, the extent is inferred from the category.
    assertNotNull(place.bboxMinLng)
    assertTrue(place.bboxMinLng!! < place.longitude)
  }

  @Test
  fun map_returnsEmptyForMalformedBody() {
    assertTrue(PlacesResponseMapper.map("not json", "q", near, "Kenya").isEmpty())
    assertTrue(PlacesResponseMapper.map("{}", "q", near, "Kenya").isEmpty())
  }

  @Test
  fun formatCoordinates_usesHemispheresAndTrimsZeros() {
    assertEquals("0.3833°S, 36.7°E", PlacesResponseMapper.formatCoordinates(-0.3833, 36.7))
    assertEquals("9.8249°N, 106.3422°E", PlacesResponseMapper.formatCoordinates(9.8249, 106.3422))
    assertEquals("13.532°S, 71.9675°W", PlacesResponseMapper.formatCoordinates(-13.532, -71.9675))
    assertEquals("0°N, 0°E", PlacesResponseMapper.formatCoordinates(0.0, 0.0))
  }
}
