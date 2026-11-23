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
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.domain.error.DomainResult
import org.openforis.collectone.v2.app.domain.model.DataSize
import org.openforis.collectone.v2.app.domain.model.OfflineAreaVerdict
import org.openforis.collectone.v2.app.domain.model.OfflineImageryType
import org.openforis.collectone.v2.app.domain.model.OfflineMapLimits
import org.openforis.collectone.v2.app.domain.model.OfflineRegionMode
import org.openforis.collectone.v2.app.domain.repository.ConnectivityRepository
import org.openforis.collectone.v2.map.LngLatBounds

class EstimateOfflineAreaUseCaseTest {
  private val satellite = OfflineImageryType.SATELLITE
  private val terrain = OfflineImageryType.TERRAIN

  /** About 49.7 km² at the equator. */
  private val justUnderLimit = LngLatBounds(0.0, -0.03, 0.067, 0.03)

  /** About 50.3 km² at the equator. */
  private val justOverLimit = LngLatBounds(0.0, -0.03, 0.0678, 0.03)

  private val repository = FakeOfflineMapRepository()
  private val storage = FakeDeviceStorageRepository()
  private val connectivity = ConnectivityRepository()

  private fun useCase(offlineMapRepository: FakeOfflineMapRepository = repository) =
    EstimateOfflineAreaUseCase(offlineMapRepository, storage, connectivity)

  @Test
  fun anAreaThatFits_isReady_withEverySizeEstimated() = runTest {
    val estimate = useCase()(justUnderLimit, setOf(satellite))

    assertEquals(OfflineAreaVerdict.Ready, estimate.verdict)
    assertEquals(49.7, estimate.areaKm2, 0.1)
    assertEquals(DataSize(30_000_000), estimate.total())
    assertEquals(DataSize(40_000_000), estimate.total(setOf(satellite, terrain)))
    assertEquals(listOf(setOf(satellite, terrain)), repository.estimateRequests)
    assertEquals(storage.free, estimate.freeSpace)
  }

  @Test
  fun anAreaJustOverTheLimit_isTooLarge_withoutAnyRequest() = runTest {
    val estimate = useCase()(justOverLimit)

    val verdict = assertIs<OfflineAreaVerdict.TooLarge>(estimate.verdict)
    assertEquals(OfflineMapLimits.MAX_AREA_KM2, verdict.maxAreaKm2)
    assertEquals(50.3, verdict.areaKm2, 0.1)
    assertTrue(repository.estimateRequests.isEmpty())
  }

  @Test
  fun unavailableMode_comesFirst() = runTest {
    connectivity.setOnline(false)
    val unavailable = FakeOfflineMapRepository(OfflineRegionMode.UNAVAILABLE)

    assertEquals(
      OfflineAreaVerdict.Unavailable,
      useCase(unavailable)(LngLatBounds(179.0, 0.0, -179.0, 1.0)).verdict,
    )
  }

  @Test
  fun aFrameAcrossTheAntimeridian_comesBeforeTheAreaCheck() = runTest {
    assertEquals(
      OfflineAreaVerdict.CrossesAntimeridian,
      useCase()(LngLatBounds(179.0, 0.0, -179.0, 1.0)).verdict,
    )
  }

  @Test
  fun tooLarge_comesBeforeOffline() = runTest {
    connectivity.setOnline(false)

    assertIs<OfflineAreaVerdict.TooLarge>(useCase()(justOverLimit).verdict)
  }

  @Test
  fun offline_skipsTheEstimate() = runTest {
    connectivity.setOnline(false)

    assertEquals(OfflineAreaVerdict.Offline, useCase()(justUnderLimit).verdict)
    assertTrue(repository.estimateRequests.isEmpty())
  }

  @Test
  fun aNetworkFailure_readsAsOffline_andOtherFailuresAreKept() = runTest {
    repository.sizes = DomainResult.Failure(DomainError.Network)
    assertEquals(OfflineAreaVerdict.Offline, useCase()(justUnderLimit).verdict)

    repository.sizes = DomainResult.Failure(DomainError.QuotaExceeded)
    assertEquals(
      OfflineAreaVerdict.Failed(DomainError.QuotaExceeded),
      useCase()(justUnderLimit).verdict,
    )
  }

  @Test
  fun notEnoughSpace_reportsTheShortfallForTheSelectedTypes() = runTest {
    // Both types need 40 MB × 1.2 + 500 MB = 548 MB; satellite alone 536 MB.
    storage.free = DataSize.ofMegabytes(540)

    val both = useCase()(justUnderLimit)
    assertEquals(OfflineAreaVerdict.NotEnoughSpace(DataSize.ofMegabytes(8)), both.verdict)
    assertEquals(OfflineAreaVerdict.Ready, both.withTypes(setOf(satellite)).verdict)
  }

  @Test
  fun unknownFreeSpace_skipsTheSpaceCheck() = runTest {
    storage.free = null

    assertEquals(OfflineAreaVerdict.Ready, useCase()(justUnderLimit).verdict)
  }
}
