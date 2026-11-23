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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.openforis.collectone.v2.app.domain.model.DeviceStorageInfo
import org.openforis.collectone.v2.app.domain.model.OfflineImageryType
import org.openforis.collectone.v2.app.domain.model.OfflineMapArea
import org.openforis.collectone.v2.app.domain.model.OfflineRegionMode
import org.openforis.collectone.v2.app.domain.repository.MutationRepository
import org.openforis.collectone.v2.app.domain.repository.OfflineMapRepository
import org.openforis.collectone.v2.app.domain.repository.SurveyRepository
import org.openforis.collectone.v2.app.domain.usecase.EstimateDeviceStorageUseCase
import org.openforis.collectone.v2.app.ui.viewmodel.ViewModelDispatcher
import org.openforis.collectone.v2.app.ui.viewmodel.viewModelScopeOn

/** A removal of a saved area, or of one of its layers, waiting for the user to confirm it. */
sealed interface PendingOfflineRemoval {
  val areaId: String

  data class Area(override val areaId: String) : PendingOfflineRemoval

  data class Layer(override val areaId: String, val type: OfflineImageryType) :
    PendingOfflineRemoval
}

/**
 * Screen state of Offline maps: the saved areas, newest first, with their layers' progress, the
 * measured device storage, and how this device saves areas.
 *
 * @property storage the device storage breakdown, or null when it can't be measured.
 * @property pendingRemoval a removal waiting for confirmation.
 */
data class OfflineMapsUiState(
  val areas: List<OfflineMapArea> = emptyList(),
  val storage: DeviceStorageInfo? = null,
  val regionMode: OfflineRegionMode = OfflineRegionMode.UNAVAILABLE,
  val pendingRemoval: PendingOfflineRemoval? = null,
) {
  /** Whether this device can save areas, which shows Save an area. */
  val canSaveAreas: Boolean
    get() = regionMode != OfflineRegionMode.UNAVAILABLE

  /** Whether downloads are only simulated here, which the screen and each area say. */
  val isSimulated: Boolean
    get() = regionMode == OfflineRegionMode.SIMULATED

  /** The area [pendingRemoval] is about, if any. */
  val pendingRemovalArea: OfflineMapArea?
    get() = pendingRemoval?.let { removal -> areas.firstOrNull { it.id == removal.areaId } }
}

/** User intents of Offline maps that stay on the screen. Implemented by [OfflineMapsViewModel]. */
interface OfflineMapsActions {
  /** Asks before removing area [areaId]. */
  fun promptRemoveArea(areaId: String)

  /** Asks before removing layer [type] of area [areaId]. */
  fun promptRemoveLayer(areaId: String, type: OfflineImageryType)

  /** Removes what the pending prompt is about. */
  fun confirmRemoval()

  fun dismissRemoval()

  /** Cancels saving layer [type] of area [areaId], removing what it saved. */
  fun cancelLayer(areaId: String, type: OfflineImageryType)

  /** Resumes saving the paused or failed layer [type] of area [areaId]. */
  fun resumeLayer(areaId: String, type: OfflineImageryType)
}

/**
 * ViewModel of Offline maps: the map areas saved for offline use ([OfflineMapRepository]), their
 * layers' downloads, and the device storage breakdown ([EstimateDeviceStorageUseCase]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineMapsViewModel(
  private val offlineMapRepository: OfflineMapRepository,
  mutationRepository: MutationRepository,
  surveyRepository: SurveyRepository,
  private val estimateDeviceStorage: EstimateDeviceStorageUseCase,
  dispatcher: CoroutineDispatcher = ViewModelDispatcher,
) : ViewModel(viewModelScopeOn(dispatcher)), OfflineMapsActions {
  private val pendingRemoval = MutableStateFlow<PendingOfflineRemoval?>(null)

  private val entityCount: Flow<Int> =
    surveyRepository.observeActiveSurveyId().flatMapLatest { id ->
      surveyRepository.observeSurveyContent(id).map { it.entities.size }
    }

  private val areas = offlineMapRepository.observeAreas()

  /** The storage breakdown, measured again when saved areas or their sizes change, or the data. */
  private val storage: Flow<DeviceStorageInfo?> =
    combine(
        areas.distinctUntilChangedBy { list -> list.map { it.id to it.totalSize } },
        mutationRepository.observeMutations().map { it.size },
        entityCount,
        ::Triple,
      )
      .mapLatest { (areas, mutationCount, entities) ->
        estimateDeviceStorage(areas, mutationCount, entities)
      }

  val uiState: StateFlow<OfflineMapsUiState> =
    combine(areas, storage, pendingRemoval) { areas, storage, removal ->
        OfflineMapsUiState(
          areas = areas,
          storage = storage,
          regionMode = offlineMapRepository.regionMode,
          pendingRemoval = removal?.takeIf { pending -> areas.any { it.id == pending.areaId } },
        )
      }
      .stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        OfflineMapsUiState(regionMode = offlineMapRepository.regionMode),
      )

  override fun promptRemoveArea(areaId: String) {
    pendingRemoval.value = PendingOfflineRemoval.Area(areaId)
  }

  override fun promptRemoveLayer(areaId: String, type: OfflineImageryType) {
    pendingRemoval.value = PendingOfflineRemoval.Layer(areaId, type)
  }

  override fun confirmRemoval() {
    val removal = pendingRemoval.value ?: return
    pendingRemoval.value = null
    viewModelScope.launch {
      when (removal) {
        is PendingOfflineRemoval.Area -> offlineMapRepository.removeArea(removal.areaId)
        is PendingOfflineRemoval.Layer ->
          offlineMapRepository.removeLayer(removal.areaId, removal.type)
      }
    }
  }

  override fun dismissRemoval() {
    pendingRemoval.value = null
  }

  override fun cancelLayer(areaId: String, type: OfflineImageryType) {
    viewModelScope.launch { offlineMapRepository.cancel(areaId, type) }
  }

  override fun resumeLayer(areaId: String, type: OfflineImageryType) {
    viewModelScope.launch { offlineMapRepository.resume(areaId, type) }
  }
}
