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
package org.openforis.collectone.v2.app.ui.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.openforis.collectone.v2.app.domain.model.PlaceKind
import org.openforis.collectone.v2.app.domain.model.SurveyPlaceItem
import org.openforis.collectone.v2.map.LatLng

/** The English names, categories, and regions of named places and of typed coordinates. */
class PlaceTextTest {
  private fun english(text: UiText): String = runBlocking { text.resolveString() }

  private val reserve =
    SurveyPlaceItem(
      id = "place-karima-forest",
      name = "Karima Hill Forest Reserve",
      categoryLabel = "Forest Reserve",
      regionSubtitle = "Nyeri County, Kenya",
      position = LatLng(-0.4183, 36.9491),
      normalizedX = 0.5f,
      normalizedY = 0.5f,
    )

  private val typedCoordinates =
    SurveyPlaceItem(
      id = "place-coord-query",
      name = "0.4210°S, 36.9505°E",
      categoryLabel = "",
      regionSubtitle = "Nyeri County, Kenya",
      position = LatLng(-0.421, 36.9505),
      normalizedX = 0.5f,
      normalizedY = 0.5f,
      kind = PlaceKind.COORDINATES,
    )

  @Test
  fun namedPlace_showsItsOwnNameCategoryAndRegion() {
    assertEquals("Karima Hill Forest Reserve", english(reserve.nameText()))
    assertEquals("Forest Reserve", english(reserve.categoryText()))
    assertEquals("Nyeri County, Kenya", english(reserve.regionText()))
  }

  @Test
  fun typedCoordinates_areNamedAfterTheirPosition() {
    assertEquals("Coordinates (0.4210°S, 36.9505°E)", english(typedCoordinates.nameText()))
    assertEquals("GPS Coordinates", english(typedCoordinates.categoryText()))
    assertEquals(
      "Nyeri County, Kenya • Direct coordinate lookup",
      english(typedCoordinates.regionText()),
    )
  }
}
