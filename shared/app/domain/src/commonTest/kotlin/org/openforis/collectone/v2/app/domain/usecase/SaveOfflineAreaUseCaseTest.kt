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
package org.openforis.collectone.v2.app.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.domain.error.DomainResult
import org.openforis.collectone.v2.app.domain.model.OfflineAreaName
import org.openforis.collectone.v2.app.domain.model.OfflineImageryType
import org.openforis.collectone.v2.app.domain.model.OfflineRegionMode
import org.openforis.collectone.v2.app.domain.repository.ConnectivityRepository
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.map.LngLatBounds

class SaveOfflineAreaUseCaseTest {
  private val bounds = LngLatBounds(36.90, -0.40, 36.95, -0.37)
  private val both = OfflineImageryType.entries.toSet()
  private val repository = FakeOfflineMapRepository()
  private val connectivity = ConnectivityRepository()
  private val save = SaveOfflineAreaUseCase(repository, connectivity)

  @Test
  fun saves_withTheGivenName() = runTest {
    val result = save(bounds, OfflineAreaName.Named("Cedar Ridge"), both)

    assertEquals(DomainResult.Success("area-1"), result)
    assertEquals(
      listOf<OfflineAreaName>(OfflineAreaName.Named("Cedar Ridge")),
      repository.savedNames,
    )
  }

  @Test
  fun aTakenName_getsTheNextFreeNumber() = runTest {
    repeat(3) { save(bounds, OfflineAreaName.Named("Cedar Ridge"), both) }

    assertEquals(
      listOf("Cedar Ridge", "Cedar Ridge (2)", "Cedar Ridge (3)"),
      repository.savedNames.map { (it as OfflineAreaName.Named).text },
    )
  }

  @Test
  fun coordinateNames_areKeptAsTheyAre() = runTest {
    val near = OfflineAreaName.NearCoordinates(LatLng(-0.385, 36.925))
    repeat(2) { save(bounds, near, both) }

    assertEquals(listOf<OfflineAreaName>(near, near), repository.savedNames)
  }

  @Test
  fun offline_failsWithNetwork_withoutSaving() = runTest {
    connectivity.setOnline(false)

    assertEquals(
      DomainResult.Failure(DomainError.Network),
      save(bounds, OfflineAreaName.Named("Cedar Ridge"), both),
    )
    assertTrue(repository.savedNames.isEmpty())
  }

  @Test
  fun invalidRequests_failValidation() = runTest {
    val name = OfflineAreaName.Named("Cedar Ridge")
    val validation = DomainResult.Failure(DomainError.Validation)

    assertEquals(validation, save(bounds, name, emptySet()))
    assertEquals(validation, save(LngLatBounds(36.0, -1.0, 37.0, 0.0), name, both))
    assertEquals(validation, save(LngLatBounds(179.99, 0.0, -179.99, 0.01), name, both))
    assertEquals(
      validation,
      SaveOfflineAreaUseCase(FakeOfflineMapRepository(OfflineRegionMode.UNAVAILABLE), connectivity)(
        bounds,
        name,
        both,
      ),
    )
    assertTrue(repository.savedNames.isEmpty())
  }
}
