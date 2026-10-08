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
package org.groundplatform.v2.devtools.prototypeapp.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.runNow
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.seededStore
import org.groundplatform.v2.devtools.prototypeapp.data.repository.PlaceRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ListFilterTab

class SearchPlacesUseCaseTest {
  private val useCase = SearchPlacesUseCase()

  @Test
  fun invoke_parsesCoordinateQueryIntoAdHocPlace() = runNow {
    val placeRepo = PlaceRepositoryImpl(seededStore())
    val coordMatches =
      useCase(
        query = "-0.4210, 36.9505",
        isAirplaneMode = false,
        listFilterTab = ListFilterTab.ALL,
        localPlaces = placeRepo.getLocalPlaces(),
        remoteApiPlaces = emptyList(),
        surveyLocationLabel = "Nyeri County, Kenya",
        surveyBaseLng = 36.9512,
        surveyBaseLat = -0.4198,
      )
    assertTrue(coordMatches.isNotEmpty())
    assertEquals("place-coord-query", coordMatches.first().id)
  }
}
