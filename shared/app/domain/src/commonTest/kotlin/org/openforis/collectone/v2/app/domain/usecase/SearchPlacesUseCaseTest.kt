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
package org.openforis.collectone.v2.app.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore
import org.openforis.collectone.v2.app.data.repository.PlaceRepositoryImpl
import org.openforis.collectone.v2.app.domain.model.PlaceKind
import org.openforis.collectone.v2.app.domain.model.SurveyPlaceItem
import org.openforis.collectone.v2.map.LatLng

class SearchPlacesUseCaseTest {
  private val useCase = SearchPlacesUseCase()

  @Test
  fun invoke_parsesCoordinateQueryIntoAdHocPlace() = runNow {
    val placeRepo = PlaceRepositoryImpl(seededStore())
    val coordMatches =
      useCase(
        query = "-0.4210, 36.9505",
        isOnline = true,
        localPlaces = placeRepo.getLocalPlaces(),
        remoteApiPlaces = emptyList(),
        surveyLocationLabel = "Nyeri County, Kenya",
        surveyBaseLng = 36.9512,
        surveyBaseLat = -0.4198,
      )
    assertTrue(coordMatches.isNotEmpty())
    assertEquals("place-coord-query", coordMatches.first().id)
  }

  @Test
  fun invoke_returnsNoPlacesWhileOffline() = runNow {
    val placeRepo = PlaceRepositoryImpl(seededStore())
    val matches =
      useCase(
        query = "-0.4210, 36.9505",
        isOnline = false,
        localPlaces = placeRepo.getLocalPlaces(),
        remoteApiPlaces = emptyList(),
        surveyLocationLabel = "Nyeri County, Kenya",
        surveyBaseLng = 36.9512,
        surveyBaseLat = -0.4198,
      )
    assertTrue(matches.isEmpty())
  }

  @Test
  fun invoke_findsLocalPlacesByTheirCoordinates() = runNow {
    val placeRepo = PlaceRepositoryImpl(seededStore())

    val matches =
      useCase(
        query = "36.9465",
        localPlaces = placeRepo.getLocalPlaces(),
        remoteApiPlaces = emptyList(),
        surveyLocationLabel = "Nyeri County, Kenya",
        surveyBaseLng = 36.9512,
        surveyBaseLat = -0.4198,
      )

    assertEquals(listOf("place-karima-forest"), matches.map { it.id })
  }

  @Test
  fun coordinateQueryPlace_isTypedCoordinatesAtTheParsedPosition() {
    val place =
      useCase.parseCoordinateQueryToPlace(
        query = "-0.421, 36.9505",
        surveyLocationLabel = "Nyeri County, Kenya",
        surveyBaseLng = 36.9512,
        surveyBaseLat = -0.4198,
      )

    assertEquals(PlaceKind.COORDINATES, place?.kind)
    assertEquals(LatLng(-0.421, 36.9505), place?.position)
    // Locale-neutral, for search: the UI names the place in the user's language.
    assertEquals("0.4210°S, 36.9505°E", place?.name)
    assertEquals("Nyeri County, Kenya", place?.regionSubtitle)
  }

  @Test
  fun coordinateQuery_isNotListedTwice_whenAPlaceShowsTheSameCoordinates() {
    val samePosition =
      SurveyPlaceItem(
        id = "place-at-query",
        name = "Junction",
        categoryLabel = "Road",
        regionSubtitle = "Nyeri County, Kenya",
        position = LatLng(-0.42101, 36.95049),
        normalizedX = 0.5f,
        normalizedY = 0.5f,
      )

    val matches =
      useCase(
        query = "-0.4210, 36.9505",
        localPlaces = emptyList(),
        remoteApiPlaces = listOf(samePosition),
        surveyLocationLabel = "Nyeri County, Kenya",
        surveyBaseLng = 36.9512,
        surveyBaseLat = -0.4198,
      )

    assertEquals(listOf("place-at-query"), matches.map { it.id })
  }
}
