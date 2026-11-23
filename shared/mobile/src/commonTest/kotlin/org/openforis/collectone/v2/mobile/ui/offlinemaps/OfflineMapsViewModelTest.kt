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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore
import org.openforis.collectone.v2.app.data.repository.MutationRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.SurveyRepositoryImpl
import org.openforis.collectone.v2.app.domain.error.DomainResult
import org.openforis.collectone.v2.app.domain.model.DataSize
import org.openforis.collectone.v2.app.domain.model.OfflineAreaName
import org.openforis.collectone.v2.app.domain.model.OfflineImageryType
import org.openforis.collectone.v2.app.domain.model.OfflineRegionMode
import org.openforis.collectone.v2.app.domain.usecase.EstimateDeviceStorageUseCase
import org.openforis.collectone.v2.app.testing.FakeDeviceStorageRepository
import org.openforis.collectone.v2.app.testing.FakeOfflineMapRepository
import org.openforis.collectone.v2.map.LngLatBounds

/**
 * [OfflineMapsViewModel] lists the saved areas with the measured storage, asks before removing an
 * area or a layer, and cancels or resumes a layer's download.
 */
class OfflineMapsViewModelTest {
  private class Fixture(regionMode: OfflineRegionMode = OfflineRegionMode.SIMULATED) {
    val repository = FakeOfflineMapRepository(regionMode = regionMode)
    private val store = seededStore()
    val viewModel =
      OfflineMapsViewModel(
        offlineMapRepository = repository,
        mutationRepository = MutationRepositoryImpl(store),
        surveyRepository = SurveyRepositoryImpl(store),
        estimateDeviceStorage = EstimateDeviceStorageUseCase(FakeDeviceStorageRepository()),
      )

    val uiState
      get() = viewModel.uiState.value

    /** Saves an area with both layers and finishes it; returns its ID. */
    fun savedArea(): String = runNow {
      val id =
        (repository.save(
            LngLatBounds(west = 36.90, south = -0.45, east = 36.95, north = -0.40),
            OfflineAreaName.Named("Othaya, Nyeri"),
            OfflineImageryType.entries.toSet(),
          ) as DomainResult.Success)
          .value
      repository.complete(id)
      id
    }
  }

  @Test
  fun savedAreas_showWithTheirSizeInTheStorage() {
    val f = Fixture()
    assertTrue(f.uiState.areas.isEmpty())
    f.savedArea()
    assertEquals(1, f.uiState.areas.size)
    assertEquals(DataSize.ofMegabytes(48), assertNotNull(f.uiState.storage).savedImagery)
  }

  @Test
  fun removeArea_asksFirst_andDismissKeepsIt() {
    val f = Fixture()
    val id = f.savedArea()
    f.viewModel.promptRemoveArea(id)
    assertEquals(PendingOfflineRemoval.Area(id), f.uiState.pendingRemoval)
    assertEquals(id, f.uiState.pendingRemovalArea?.id)

    f.viewModel.dismissRemoval()
    assertNull(f.uiState.pendingRemoval)
    assertTrue(f.repository.removedAreas.isEmpty())

    f.viewModel.promptRemoveArea(id)
    f.viewModel.confirmRemoval()
    assertEquals(listOf(id), f.repository.removedAreas)
    assertTrue(f.uiState.areas.isEmpty())
    assertNull(f.uiState.pendingRemoval)
  }

  @Test
  fun removeLayer_asksFirst_andRemovesOnlyThatLayer() {
    val f = Fixture()
    val id = f.savedArea()
    f.viewModel.promptRemoveLayer(id, OfflineImageryType.SATELLITE)
    assertEquals(
      PendingOfflineRemoval.Layer(id, OfflineImageryType.SATELLITE),
      f.uiState.pendingRemoval,
    )
    f.viewModel.confirmRemoval()
    assertEquals(listOf(id to OfflineImageryType.SATELLITE), f.repository.removedLayers)
    assertEquals(
      listOf(OfflineImageryType.TERRAIN),
      f.uiState.areas.single().layers.map { it.type },
    )
  }

  @Test
  fun cancelAndResume_reachTheRepository() {
    val f = Fixture()
    val id = f.savedArea()
    f.viewModel.resumeLayer(id, OfflineImageryType.TERRAIN)
    f.viewModel.cancelLayer(id, OfflineImageryType.SATELLITE)
    assertEquals(
      listOf<Pair<String, OfflineImageryType?>>(id to OfflineImageryType.TERRAIN),
      f.repository.resumed,
    )
    assertEquals(
      listOf<Pair<String, OfflineImageryType?>>(id to OfflineImageryType.SATELLITE),
      f.repository.cancelled,
    )
  }

  @Test
  fun regionMode_showsSaveAnArea_andTheSimulatedNotice() {
    with(Fixture(OfflineRegionMode.SIMULATED).uiState) {
      assertTrue(canSaveAreas)
      assertTrue(isSimulated)
    }
    with(Fixture(OfflineRegionMode.NATIVE).uiState) {
      assertTrue(canSaveAreas)
      assertFalse(isSimulated)
    }
    with(Fixture(OfflineRegionMode.UNAVAILABLE).uiState) {
      assertFalse(canSaveAreas)
      assertFalse(isSimulated)
    }
  }
}
