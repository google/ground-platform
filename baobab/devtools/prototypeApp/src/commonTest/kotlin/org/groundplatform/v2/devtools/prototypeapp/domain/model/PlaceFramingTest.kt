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
package org.groundplatform.v2.devtools.prototypeapp.domain.model

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.groundplatform.v2.map.LatLng
import org.groundplatform.v2.map.LngLatBounds

class PlaceFramingTest {
  private val nyeri = LatLng(-0.4198, 36.9512)

  @Test
  fun zoom_isWiderForLargerCategories() {
    val country = PlaceFraming.zoom(PlaceFraming.bounds(nyeri, "Country"))
    val county = PlaceFraming.zoom(PlaceFraming.bounds(nyeri, "County"))
    val village = PlaceFraming.zoom(PlaceFraming.bounds(nyeri, "Village"))

    assertTrue(country < 6.0, "country zoom $country")
    assertTrue(country < county && county < village)
    assertTrue(village > 13.0, "village zoom $village")
  }

  @Test
  fun zoom_isClampedToSupportedRange() {
    val world = LngLatBounds(west = -180.0, south = -85.0, east = 180.0, north = 85.0)
    assertEquals(PlaceFraming.MIN_ZOOM, PlaceFraming.zoom(world))
  }

  @Test
  fun bounds_usesGeocoderBboxWhenItHasAnExtent() {
    val bbox = LngLatBounds(west = 36.5, south = -0.6, east = 37.1, north = -0.2)
    assertEquals(bbox, PlaceFraming.bounds(nyeri, "Village", bbox))
  }

  @Test
  fun bounds_infersExtentForPointLikeBbox() {
    val pointBbox = LngLatBounds(36.9512, -0.4198, 36.9513, -0.4197)

    val bounds = PlaceFraming.bounds(nyeri, "Town", pointBbox)

    val half = PlaceFraming.halfSpanDegrees("Town")
    assertEquals(nyeri.longitude - half, bounds.west, 1e-9)
    assertEquals(nyeri.longitude + half, bounds.east, 1e-9)
    // Inferred extents are slightly shorter than wide.
    assertTrue(abs(bounds.north - bounds.south) < abs(bounds.east - bounds.west))
  }

  @Test
  fun zoom_keepsExplicitZoomOnlyForPointSizedExtents() {
    val point = LngLatBounds(36.9512, -0.4198, 36.9512, -0.4198)
    val area = LngLatBounds(36.5, -0.6, 37.1, -0.2)

    assertEquals(17.5, PlaceFraming.zoom(point, explicitZoom = 17.5))
    assertTrue(PlaceFraming.zoom(area, explicitZoom = 17.5) < 17.5)
  }

  @Test
  fun focus_capsMaxZoomSlightlyAboveNominalZoom() {
    val place =
      SurveyPlaceItem(
        id = "p",
        name = "Othaya",
        categoryLabel = "Town",
        regionSubtitle = "Nyeri County, Kenya",
        coordinatesLabel = "0.5512° S, 36.9421° E",
        normalizedX = 0.5f,
        normalizedY = 0.5f,
        longitude = 36.9421,
        latitude = -0.5512,
      )

    val focus = PlaceFraming.focus(place)

    assertEquals(LatLng(-0.5512, 36.9421), focus.center)
    assertEquals((focus.zoom + 0.6).coerceIn(2.5, PlaceFraming.MAX_ZOOM), focus.maxZoom, 1e-9)
    assertEquals(PlaceFraming.FOCUS_PADDING, focus.padding)
  }
}
