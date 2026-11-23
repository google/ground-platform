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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.domain.model.PlaceSource
import org.openforis.collectone.v2.app.domain.model.SurveyMapAnchor
import org.openforis.collectone.v2.map.LatLng

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
    assertEquals(LatLng(-0.3833, 36.7), place.position)
    assertEquals(36.55, place.bounds?.west)
    assertEquals(-0.15, place.bounds?.north)
    assertEquals(PlaceSource.PLACES_API, place.source)
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
    assertEquals(36.9421, place.position.longitude)
    // Without a bbox, the extent is inferred from the category.
    val bounds = assertNotNull(place.bounds)
    assertTrue(bounds.west < place.position.longitude)
  }

  @Test
  fun map_returnsEmptyForMalformedBody() {
    assertTrue(PlacesResponseMapper.map("not json", "q", near, "Kenya").isEmpty())
    assertTrue(PlacesResponseMapper.map("{}", "q", near, "Kenya").isEmpty())
  }
}
