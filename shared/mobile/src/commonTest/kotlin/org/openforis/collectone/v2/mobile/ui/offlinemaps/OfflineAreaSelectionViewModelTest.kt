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
package org.openforis.collectone.v2.mobile.ui.offlinemaps

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.domain.model.DataSize
import org.openforis.collectone.v2.app.domain.model.OfflineAreaLayerStatus
import org.openforis.collectone.v2.app.domain.model.OfflineAreaName
import org.openforis.collectone.v2.app.domain.model.OfflineAreaVerdict
import org.openforis.collectone.v2.app.domain.model.OfflineImageryType
import org.openforis.collectone.v2.app.domain.model.OfflineRegionMode
import org.openforis.collectone.v2.app.domain.repository.ConnectivityRepository
import org.openforis.collectone.v2.app.domain.usecase.EstimateOfflineAreaUseCase
import org.openforis.collectone.v2.app.domain.usecase.SaveOfflineAreaUseCase
import org.openforis.collectone.v2.app.testing.FakeDeviceStorageRepository
import org.openforis.collectone.v2.app.testing.FakeOfflineMapRepository
import org.openforis.collectone.v2.app.ui.text.toUiText
import org.openforis.collectone.v2.map.LngLatBounds
import org.openforis.collectone.v2.mobile.resources.Res
import org.openforis.collectone.v2.mobile.resources.mobile_offline_area_estimate
import org.openforis.collectone.v2.mobile.resources.mobile_offline_area_estimating
import org.openforis.collectone.v2.mobile.resources.mobile_offline_area_offline
import org.openforis.collectone.v2.mobile.resources.mobile_offline_area_tile_limit

/**
 * [OfflineAreaSelectionViewModel] estimates and names the framed area once the map has been still
 * for the debounce delay, cancels that work when the map moves again, updates the total for another
 * selection of imagery without a request, and follows a save until it's done.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineAreaSelectionViewModelTest {
  /** About 5.6 × 5.6 km at the equator: 31 km², under the 50 km² limit. */
  private val small = LngLatBounds(west = 36.90, south = -0.45, east = 36.95, north = -0.40)

  /** Another small frame, a little east. */
  private val other = LngLatBounds(west = 36.92, south = -0.45, east = 36.97, north = -0.40)

  /** About 22 × 22 km: far over the limit. */
  private val large = LngLatBounds(west = 36.8, south = -0.5, east = 37.0, north = -0.3)

  private val delay = OfflineAreaSelectionViewModel.DEBOUNCE_MILLIS

  private class Fixture(
    scope: TestScope,
    regionMode: OfflineRegionMode = OfflineRegionMode.SIMULATED,
  ) {
    val repository = FakeOfflineMapRepository(regionMode = regionMode)
    val storage = FakeDeviceStorageRepository()
    val connectivity = ConnectivityRepository()
    val viewModel =
      OfflineAreaSelectionViewModel(
        offlineMapRepository = repository,
        estimateOfflineArea = EstimateOfflineAreaUseCase(repository, storage, connectivity),
        saveOfflineArea = SaveOfflineAreaUseCase(repository, connectivity),
        dispatcher = StandardTestDispatcher(scope.testScheduler),
      )

    val uiState
      get() = viewModel.uiState.value
  }

  private fun TestScope.started(
    regionMode: OfflineRegionMode = OfflineRegionMode.SIMULATED
  ): Fixture = Fixture(this, regionMode).also { it.viewModel.start() }

  /** Frames [bounds] and lets the estimate run. */
  private fun TestScope.framed(f: Fixture, bounds: LngLatBounds = small) {
    f.viewModel.onFrameSettled(bounds, 14.0)
    advanceTimeBy(delay + 1)
    runCurrent()
  }

  @Test
  fun estimateAndName_waitUntilTheFrameIsStill() = runTest {
    val f = started()
    f.viewModel.onFrameSettled(small, 14.0)
    runCurrent()
    assertEquals(small, f.uiState.bounds)
    assertNull(f.uiState.estimate)
    assertEquals(Res.string.mobile_offline_area_estimating.toUiText(), f.uiState.framingMessage)

    advanceTimeBy(delay - 1)
    runCurrent()
    assertTrue(f.repository.estimateRequests.isEmpty())

    advanceTimeBy(2)
    runCurrent()
    assertEquals(listOf(small), f.repository.estimateRequests)
    assertEquals(listOf(small), f.repository.nameRequests)
    assertEquals(OfflineAreaVerdict.Ready, f.uiState.estimate?.verdict)
    assertEquals(OfflineAreaName.Named("Othaya, Nyeri"), f.uiState.name)
    assertTrue(f.uiState.canSave)
    assertEquals(
      Res.string.mobile_offline_area_estimate.toUiText(
        DataSize.ofMegabytes(48).toUiText(),
        DataSize.ofMegabytes(12_400).toUiText(),
      ),
      f.uiState.framingMessage,
    )
  }

  @Test
  fun movingTheMap_restartsTheWait() = runTest {
    val f = started()
    f.viewModel.onFrameSettled(small, 14.0)
    advanceTimeBy(delay - 100)
    f.viewModel.onFrameMoving()
    f.viewModel.onFrameSettled(other, 14.0)
    advanceTimeBy(delay - 100)
    runCurrent()
    assertTrue(f.repository.estimateRequests.isEmpty())

    advanceTimeBy(101)
    runCurrent()
    assertEquals(listOf(other), f.repository.estimateRequests)
  }

  @Test
  fun movingTheMap_cancelsWorkInFlight() = runTest {
    val f = started()
    f.repository.estimateDelayMillis = 1_000
    f.viewModel.onFrameSettled(small, 14.0)
    advanceTimeBy(delay + 1)
    runCurrent()
    assertEquals(listOf(small), f.repository.estimateRequests)

    f.viewModel.onFrameMoving()
    advanceTimeBy(2_000)
    runCurrent()
    assertNull(f.uiState.estimate)
    assertNull(f.uiState.name)
  }

  @Test
  fun aFrameTooLarge_saysSoAtOnce_withoutARequest() = runTest {
    val f = started()
    f.viewModel.onFrameSettled(large, 11.0)
    runCurrent()
    assertIs<OfflineAreaVerdict.TooLarge>(f.uiState.estimate?.verdict)
    assertTrue(f.uiState.isTooLarge)
    assertFalse(f.uiState.canSave)
    assertTrue(f.repository.estimateRequests.isEmpty())
    assertTrue(f.repository.nameRequests.isEmpty())
  }

  @Test
  fun chips_updateTheTotalWithoutARequest_andKeepOneSelected() = runTest {
    val f = started()
    framed(f)
    assertEquals(DataSize.ofMegabytes(48), f.uiState.estimate?.total())

    f.viewModel.toggleType(OfflineImageryType.SATELLITE)
    assertEquals(setOf(OfflineImageryType.TERRAIN), f.uiState.selectedTypes)
    assertEquals(DataSize.ofMegabytes(10), f.uiState.estimate?.total())

    f.viewModel.toggleType(OfflineImageryType.TERRAIN)
    assertEquals(setOf(OfflineImageryType.TERRAIN), f.uiState.selectedTypes)
    assertEquals(1, f.repository.estimateRequests.size)
  }

  @Test
  fun offline_saysSo_andTryAgainEstimatesAgain() = runTest {
    val f = started()
    f.connectivity.setOnline(false)
    framed(f)
    assertEquals(OfflineAreaVerdict.Offline, f.uiState.estimate?.verdict)
    assertEquals(Res.string.mobile_offline_area_offline.toUiText(), f.uiState.framingMessage)
    assertTrue(f.uiState.canTryAgain)

    f.connectivity.setOnline(true)
    f.viewModel.tryAgain()
    runCurrent()
    assertEquals(OfflineAreaVerdict.Ready, f.uiState.estimate?.verdict)
  }

  @Test
  fun save_followsTheProgressUntilSaved() = runTest {
    val f = started()
    framed(f)
    f.viewModel.save()
    runCurrent()
    assertEquals(OfflineAreaSelectionPhase.SAVING, f.uiState.phase)
    val areaId = checkNotNull(f.uiState.savingAreaId)
    assertEquals(OfflineAreaName.Named("Othaya, Nyeri"), f.repository.areas.value.single().name)

    f.repository.setProgress(areaId, 0.25f)
    runCurrent()
    assertEquals(25, f.uiState.progressPercent)
    assertEquals(DataSize.ofMegabytes(48), f.uiState.savingTotal)

    f.repository.complete(areaId)
    runCurrent()
    assertEquals(OfflineAreaSelectionPhase.SAVED, f.uiState.phase)
  }

  @Test
  fun savingOffline_failsWithoutAnArea_andTryAgainSavesAnew() = runTest {
    val f = started()
    framed(f)
    f.connectivity.setOnline(false)

    f.viewModel.save()
    runCurrent()
    assertEquals(OfflineAreaSelectionPhase.FAILED, f.uiState.phase)
    assertEquals(DomainError.Network, f.uiState.saveFailure)
    assertNull(f.uiState.savingAreaId)
    assertTrue(f.repository.areas.value.isEmpty())

    // Nothing was saved, so there is nothing to resume: Try again saves the frame again.
    f.connectivity.setOnline(true)
    f.viewModel.tryAgain()
    runCurrent()
    assertEquals(OfflineAreaSelectionPhase.SAVING, f.uiState.phase)
    assertNull(f.uiState.saveFailure)
    assertEquals(f.uiState.savingAreaId, f.repository.areas.value.single().id)
  }

  @Test
  fun anAreaRemovedElsewhereWhileSaving_returnsToFraming() = runTest {
    val f = started()
    framed(f)
    f.viewModel.save()
    runCurrent()
    val areaId = checkNotNull(f.uiState.savingAreaId)

    // For example, removed from Offline maps on another screen.
    f.repository.cancel(areaId)
    runCurrent()

    assertEquals(OfflineAreaSelectionPhase.FRAMING, f.uiState.phase)
    assertNull(f.uiState.savingAreaId)
    assertNull(f.uiState.savingArea)
  }

  @Test
  fun tryAgain_whileSaving_doesNothing() = runTest {
    val f = started()
    framed(f)
    f.viewModel.save()
    runCurrent()
    val saving = f.uiState

    f.viewModel.tryAgain()
    runCurrent()

    assertEquals(saving, f.uiState)
    assertEquals(1, f.repository.areas.value.size)
  }

  @Test
  fun hide_leavesTheModeButKeepsSaving() = runTest {
    val f = started()
    framed(f)
    f.viewModel.save()
    runCurrent()

    f.viewModel.stop()
    runCurrent()
    assertFalse(f.uiState.isActive)
    assertTrue(f.repository.cancelled.isEmpty())
    assertTrue(f.repository.areas.value.single().isSaving)
  }

  @Test
  fun cancelSave_removesThePartialArea_andReturnsToFraming() = runTest {
    val f = started()
    framed(f)
    f.viewModel.save()
    runCurrent()
    val areaId = checkNotNull(f.uiState.savingAreaId)

    f.viewModel.cancelSave()
    runCurrent()
    assertEquals(listOf<Pair<String, OfflineImageryType?>>(areaId to null), f.repository.cancelled)
    assertEquals(OfflineAreaSelectionPhase.FRAMING, f.uiState.phase)
    assertTrue(f.repository.areas.value.isEmpty())
  }

  @Test
  fun aFailedLayer_failsTheSave_andTryAgainResumesIt() = runTest {
    val f = started()
    framed(f)
    f.viewModel.save()
    runCurrent()
    val areaId = checkNotNull(f.uiState.savingAreaId)

    f.repository.setStatus(areaId, OfflineImageryType.SATELLITE, OfflineAreaLayerStatus.FAILED)
    f.repository.setStatus(areaId, OfflineImageryType.TERRAIN, OfflineAreaLayerStatus.READY)
    runCurrent()
    assertEquals(OfflineAreaSelectionPhase.FAILED, f.uiState.phase)

    f.viewModel.tryAgain()
    runCurrent()
    assertEquals(listOf<Pair<String, OfflineImageryType?>>(areaId to null), f.repository.resumed)
    assertEquals(OfflineAreaSelectionPhase.SAVING, f.uiState.phase)
  }

  @Test
  fun saveFailures_nameTheirCause() {
    assertEquals(
      Res.string.mobile_offline_area_offline.toUiText(),
      OfflineAreaSelectionUiState(saveFailure = DomainError.Network).failureMessage,
    )
    assertEquals(
      Res.string.mobile_offline_area_tile_limit.toUiText(),
      OfflineAreaSelectionUiState(saveFailure = DomainError.QuotaExceeded).failureMessage,
    )
  }

  @Test
  fun aDeviceThatCantSaveAreas_neverStartsTheMode() = runTest {
    val f = started(OfflineRegionMode.UNAVAILABLE)
    assertFalse(f.viewModel.canSaveAreas)
    assertFalse(f.uiState.isActive)
  }

  @Test
  fun theBrowserBuild_isSimulated() = runTest {
    val f = started(OfflineRegionMode.SIMULATED)
    assertTrue(f.uiState.isSimulated)
    val native = started(OfflineRegionMode.NATIVE)
    assertFalse(native.uiState.isSimulated)
  }
}
