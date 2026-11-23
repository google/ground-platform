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
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openforis.collectone.v2.app.domain.error.DomainResult
import org.openforis.collectone.v2.app.domain.model.OfflineAreaLayerStatus
import org.openforis.collectone.v2.app.domain.model.OfflineAreaName
import org.openforis.collectone.v2.app.domain.model.OfflineImageryType
import org.openforis.collectone.v2.app.domain.model.OfflineMapArea
import org.openforis.collectone.v2.app.domain.model.OfflineMapLimits
import org.openforis.collectone.v2.app.domain.model.OfflineRegionMode
import org.openforis.collectone.v2.app.domain.repository.OfflineMapRepository
import org.openforis.collectone.v2.app.domain.usecase.EstimateOfflineAreaUseCase
import org.openforis.collectone.v2.app.domain.usecase.SaveOfflineAreaUseCase
import org.openforis.collectone.v2.app.ui.viewmodel.ViewModelDispatcher
import org.openforis.collectone.v2.app.ui.viewmodel.viewModelScopeOn
import org.openforis.collectone.v2.map.LngLatBounds

/** User intents of the area selection mode. Implemented by [OfflineAreaSelectionViewModel]. */
interface OfflineAreaSelectionActions {
  /** The map is moving: the frame's estimate and name are stale, and work on them stops. */
  fun onFrameMoving()

  /** The map settled with the frame on [bounds] at [zoom]. */
  fun onFrameSettled(bounds: LngLatBounds, zoom: Double)

  /** Selects or unselects imagery [type]; the last selected type stays selected. */
  fun toggleType(type: OfflineImageryType)

  /** Saves the framed area for offline use. */
  fun save()

  /** Estimates the frame again (after it failed), or resumes a save that failed. */
  fun tryAgain()

  /** Cancels the save in progress, removing what it saved, and returns to framing. */
  fun cancelSave()
}

/**
 * ViewModel of the mobile app's area selection mode: the user frames an area on the main map, sees
 * its name and estimated size, chooses its imagery, and saves it for offline use.
 *
 * When the frame settles, the estimate ([EstimateOfflineAreaUseCase]) and the place name
 * ([OfflineMapRepository.resolveName]) run in parallel after [debounceMillis], and a frame that
 * moves again cancels them. A frame larger than [OfflineMapLimits.MAX_AREA_KM2] says so at once,
 * without a request. [SaveOfflineAreaUseCase] only starts the downloads, which the repository runs
 * in the app's scope; this ViewModel then follows the area's progress. So [stop] (Hide) stops
 * following but leaves the download running (Offline maps shows it), and only [cancelSave] cancels
 * it.
 */
class OfflineAreaSelectionViewModel(
  private val offlineMapRepository: OfflineMapRepository,
  private val estimateOfflineArea: EstimateOfflineAreaUseCase,
  private val saveOfflineArea: SaveOfflineAreaUseCase,
  dispatcher: CoroutineDispatcher = ViewModelDispatcher,
  /** How long the frame stays still before it's estimated; screenshot tests pass 0. */
  private val debounceMillis: Long = DEBOUNCE_MILLIS,
) : ViewModel(viewModelScopeOn(dispatcher)), OfflineAreaSelectionActions {
  private val _uiState =
    MutableStateFlow(OfflineAreaSelectionUiState(regionMode = offlineMapRepository.regionMode))

  val uiState: StateFlow<OfflineAreaSelectionUiState> = _uiState.asStateFlow()

  private var frameJob: Job? = null
  private var saveJob: Job? = null

  /** Whether this device can save map areas, so the app shows the ways in. */
  val canSaveAreas: Boolean
    get() = offlineMapRepository.regionMode != OfflineRegionMode.UNAVAILABLE

  /** Enters the selection mode with nothing framed yet. */
  fun start() {
    if (!canSaveAreas) return
    stopWork()
    _uiState.value =
      OfflineAreaSelectionUiState(isActive = true, regionMode = offlineMapRepository.regionMode)
  }

  /** Leaves the selection mode; a save in progress continues in the app's scope. */
  fun stop() {
    stopWork()
    _uiState.update { it.copy(isActive = false) }
  }

  private fun stopWork() {
    frameJob?.cancel()
    saveJob?.cancel()
  }

  override fun onFrameMoving() {
    if (_uiState.value.phase != OfflineAreaSelectionPhase.FRAMING) return
    frameJob?.cancel()
  }

  override fun onFrameSettled(bounds: LngLatBounds, zoom: Double) {
    val state = _uiState.value
    if (!state.isActive || state.phase != OfflineAreaSelectionPhase.FRAMING) return
    if (bounds == state.bounds && state.estimate != null) return
    frameJob?.cancel()
    _uiState.update { it.copy(bounds = bounds, zoom = zoom, estimate = null, name = null) }
    frameJob = viewModelScope.launch {
      if (bounds.areaSquareKilometers() > OfflineMapLimits.MAX_AREA_KM2) {
        // The use case stops at the area check without a request; show it at once.
        val estimate = estimateOfflineArea(bounds, _uiState.value.selectedTypes)
        _uiState.update { it.copy(estimate = estimate) }
        return@launch
      }
      if (debounceMillis > 0) delay(debounceMillis)
      estimate(bounds, zoom)
    }
  }

  private suspend fun estimate(bounds: LngLatBounds, zoom: Double) {
    coroutineScope {
      val name = async { offlineMapRepository.resolveName(bounds, zoom) }
      val estimate = estimateOfflineArea(bounds, _uiState.value.selectedTypes)
      _uiState.update { it.copy(estimate = estimate.withTypes(it.selectedTypes)) }
      val resolved = name.await()
      _uiState.update { it.copy(name = resolved) }
    }
  }

  override fun toggleType(type: OfflineImageryType) {
    _uiState.update { state ->
      val types =
        if (type in state.selectedTypes) state.selectedTypes - type else state.selectedTypes + type
      if (types.isEmpty()) state
      else state.copy(selectedTypes = types, estimate = state.estimate?.withTypes(types))
    }
  }

  override fun save() {
    val state = _uiState.value
    val bounds = state.bounds ?: return
    if (!state.canSave) return
    val name = state.name ?: OfflineAreaName.NearCoordinates(bounds.center)
    frameJob?.cancel()
    _uiState.update { it.copy(phase = OfflineAreaSelectionPhase.SAVING, savingArea = null) }
    viewModelScope.launch {
      when (val saved = saveOfflineArea(bounds, name, state.selectedTypes)) {
        is DomainResult.Success -> follow(saved.value)
        is DomainResult.Failure ->
          _uiState.update {
            it.copy(phase = OfflineAreaSelectionPhase.FAILED, saveFailure = saved.error)
          }
      }
    }
  }

  /** Follows the progress of area [areaId] until it's saved, fails, or is cancelled. */
  private fun follow(areaId: String) {
    _uiState.update { it.copy(savingAreaId = areaId, saveFailure = null) }
    saveJob?.cancel()
    saveJob = viewModelScope.launch {
      offlineMapRepository.observeAreas().collect { areas ->
        val area = areas.firstOrNull { it.id == areaId }
        _uiState.update { it.followed(area) }
      }
    }
  }

  override fun tryAgain() {
    val state = _uiState.value
    when (state.phase) {
      OfflineAreaSelectionPhase.FAILED -> {
        val areaId = state.savingAreaId
        if (areaId == null) {
          _uiState.update { it.copy(phase = OfflineAreaSelectionPhase.FRAMING) }
          save()
        } else {
          _uiState.update { it.copy(phase = OfflineAreaSelectionPhase.SAVING, saveFailure = null) }
          viewModelScope.launch { offlineMapRepository.resume(areaId) }
          follow(areaId)
        }
      }
      OfflineAreaSelectionPhase.FRAMING -> {
        val bounds = state.bounds ?: return
        _uiState.update { it.copy(estimate = null) }
        onFrameSettledAgain(bounds, state.zoom)
      }
      else -> Unit
    }
  }

  private fun onFrameSettledAgain(bounds: LngLatBounds, zoom: Double) {
    frameJob?.cancel()
    frameJob = viewModelScope.launch { estimate(bounds, zoom) }
  }

  override fun cancelSave() {
    val areaId = _uiState.value.savingAreaId
    saveJob?.cancel()
    _uiState.update {
      it.copy(
        phase = OfflineAreaSelectionPhase.FRAMING,
        savingAreaId = null,
        savingArea = null,
        saveFailure = null,
      )
    }
    if (areaId != null) viewModelScope.launch { offlineMapRepository.cancel(areaId) }
  }

  companion object {
    /** How long the frame stays still before its estimate and name are looked up. */
    const val DEBOUNCE_MILLIS = 400L
  }
}

/** This state following the saved [area] (null once it was removed, e.g. cancelled elsewhere). */
private fun OfflineAreaSelectionUiState.followed(
  area: OfflineMapArea?
): OfflineAreaSelectionUiState {
  if (area == null) {
    return copy(phase = OfflineAreaSelectionPhase.FRAMING, savingAreaId = null, savingArea = null)
  }
  val stopped =
    area.layers.firstOrNull {
      it.status == OfflineAreaLayerStatus.FAILED || it.status == OfflineAreaLayerStatus.PAUSED
    }
  val phase =
    when {
      area.layers.all { it.status == OfflineAreaLayerStatus.READY } ->
        OfflineAreaSelectionPhase.SAVED
      stopped != null && !area.isSaving -> OfflineAreaSelectionPhase.FAILED
      else -> OfflineAreaSelectionPhase.SAVING
    }
  return copy(
    phase = phase,
    savingArea = area,
    saveFailure = if (phase == OfflineAreaSelectionPhase.FAILED) stopped?.failure else null,
  )
}
