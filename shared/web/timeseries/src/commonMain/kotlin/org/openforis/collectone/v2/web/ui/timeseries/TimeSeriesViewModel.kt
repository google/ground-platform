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
package org.openforis.collectone.v2.web.ui.timeseries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import openforis.collectone.v1.survey.SceneStretch
import openforis.collectone.v1.survey.TimeSeriesSource
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.ImagerySource
import org.openforis.collectone.v2.app.domain.model.ImagerySourceType
import org.openforis.collectone.v2.app.domain.model.geometryType
import org.openforis.collectone.v2.app.domain.model.outline
import org.openforis.collectone.v2.app.ui.viewmodel.ViewModelDispatcher
import org.openforis.collectone.v2.app.ui.viewmodel.viewModelScopeOn
import org.openforis.collectone.v2.web.client.timeseries.PlotImageryRepository

/** User intents of the dashboard's "Time series & imagery" panel. */
interface TimeSeriesActions {
  /**
   * Follows the selected map feature [entity] of survey [surveyId]. Data loads only while the panel
   * is [visible]; selecting another feature clears the pinned scene.
   */
  fun follow(surveyId: String?, entity: GeospatialEntityItem?, visible: Boolean)

  /** Charts [source]'s imagery, keeping the band when the source has it. */
  fun selectSource(source: TimeSeriesSource)

  fun selectBand(band: String)

  /** Selects observation [index] and shows its scene on the map; `null` clears both. */
  fun selectObservation(index: Int?)

  /** Moves the selection [delta] observations (with a value) forward or back. */
  fun stepObservation(delta: Int)

  /** Redraws the pinned scene with [stretch]. */
  fun selectStretch(stretch: SceneStretch)

  /** Removes the pinned scene from the map and clears the selection. */
  fun clearPinnedScene()

  /** Reloads whatever failed. */
  fun retry()
}

/**
 * ViewModel of the dashboard's "Time series & imagery" panel (Collect Earth Online's Geo-Dash):
 * statistics and time series of the selected map feature, and the historical scene of a selected
 * observation, shown on the survey map through [showSceneOnMap].
 *
 * It only calls the server while the panel is visible ([follow]), and keeps results for the session
 * so switching sources or returning to a plot doesn't recompute them.
 */
class TimeSeriesViewModel(
  /** Reads the analyses from the server; `null` when the console has no backend. */
  private val repository: PlotImageryRepository?,
  dispatcher: CoroutineDispatcher = ViewModelDispatcher,
  /** Shows a scene's tiles on the survey map, or removes them with `null`. */
  private val showSceneOnMap: (ImagerySource?) -> Unit = {},
  /** Delay before a scene follows keyboard stepping, so held arrow keys don't flood the server. */
  private val stepSceneDelayMs: Long = 400L,
) : ViewModel(viewModelScopeOn(dispatcher)), TimeSeriesActions {
  private val state = MutableStateFlow(TimeSeriesUiState(isAvailable = repository != null))
  val uiState: StateFlow<TimeSeriesUiState> = state.asStateFlow()

  private val stretchBySource = mutableMapOf<TimeSeriesSource, SceneStretch>()
  private var statisticsJob: Job? = null
  private var seriesJob: Job? = null
  private var sceneJob: Job? = null
  private var isVisible = false

  override fun follow(surveyId: String?, entity: GeospatialEntityItem?, visible: Boolean) {
    val plot =
      if (surveyId.isNullOrBlank() || entity == null) {
        null
      } else {
        TimeSeriesPlot(
          surveyId = surveyId,
          entityId = entity.id,
          label = entity.label,
          geometryType = entity.geometryType,
          areaHectares = entity.areaHectares,
          vertices = entity.geometry?.outline.orEmpty(),
        )
      }
    isVisible = visible
    val current = state.value.plot
    if (plot?.let(PlotImageryRepository::plotKey) != current?.let(PlotImageryRepository::plotKey)) {
      statisticsJob?.cancel()
      seriesJob?.cancel()
      if (state.value.pinnedScene != null) clearPinnedScene()
      state.update {
        it.copy(
          plot = plot,
          statistics = Loadable.Idle,
          series = Loadable.Idle,
          selectedIndex = null,
          pinnedScene = null,
        )
      }
    } else if (plot != null && plot != current) {
      state.update { it.copy(plot = plot) }
    }
    if (visible) load()
  }

  override fun selectSource(source: TimeSeriesSource) {
    if (source == state.value.source) return
    clearPinnedScene()
    state.update {
      val bands = TimeSeriesUiState.bandsOf(source)
      it.copy(
        source = source,
        band = if (it.band in bands) it.band else bands.first(),
        series = Loadable.Idle,
      )
    }
    if (isVisible) load()
  }

  override fun selectBand(band: String) {
    if (band !in state.value.bands) return
    state.update { it.copy(band = band) }
  }

  override fun selectObservation(index: Int?) {
    select(index, sceneDelayMs = 0L)
  }

  /**
   * Steps [delta] valid (non-`NaN`, i.e. non-cloud-masked) observations in the active band,
   * clamping at the series ends and debouncing scene tile loading by [stepSceneDelayMs] so held
   * arrow keys do not flood `GeoDataService` with intermediate `GetSceneTileUrl` calls.
   */
  override fun stepObservation(delta: Int) {
    val current = state.value
    val series = current.loadedSeries ?: return
    val values = series.values(current.band)
    val valid = values.indices.filter { !values[it].isNaN() }
    if (valid.isEmpty()) return
    val position = current.selectedIndex?.let { selected -> valid.indexOfFirst { it >= selected } }
    val next =
      when {
        position == null || position < 0 -> if (delta >= 0) valid.first() else valid.last()
        else -> valid[(position + delta).coerceIn(0, valid.lastIndex)]
      }
    select(next, sceneDelayMs = stepSceneDelayMs)
  }

  override fun selectStretch(stretch: SceneStretch) {
    val pinned = state.value.pinnedScene ?: return
    if (stretch == pinned.stretch || stretch !in pinned.stretches) return
    stretchBySource[state.value.source] = stretch
    pinScene(pinned.observation, delayMs = 0L)
  }

  override fun clearPinnedScene() {
    sceneJob?.cancel()
    state.update { it.copy(pinnedScene = null, selectedIndex = null) }
    showSceneOnMap(null)
  }

  override fun retry() {
    state.update {
      it.copy(
        statistics = if (it.statistics is Loadable.Failed) Loadable.Idle else it.statistics,
        series = if (it.series is Loadable.Failed) Loadable.Idle else it.series,
      )
    }
    state.value.pinnedScene
      ?.takeIf { it.tiles is Loadable.Failed }
      ?.let { pinScene(it.observation, 0L) }
    load()
  }

  private fun select(index: Int?, sceneDelayMs: Long) {
    val current = state.value
    val observation = index?.let { i ->
      current.loadedSeries?.let { series -> current.observation(series, i) }
    }
    if (observation == null) {
      clearPinnedScene()
      return
    }
    state.update { it.copy(selectedIndex = observation.index) }
    pinScene(observation, sceneDelayMs)
  }

  /**
   * Pins [observation]'s scene: shows it as loading at once, then (after [delayMs], so held arrow
   * keys only load the scene they stop on) asks for its tiles and puts them on the map. A newer pin
   * cancels this one, and a response for a scene that is no longer pinned is ignored.
   */
  private fun pinScene(observation: TimeSeriesObservation, delayMs: Long) {
    val repository = repository ?: return
    val plot = state.value.plot ?: return
    val source = state.value.source
    val stretches = TimeSeriesUiState.stretchesOf(source)
    val stretch = stretchBySource[source]?.takeIf { it in stretches } ?: stretches.first()
    sceneJob?.cancel()
    state.update {
      it.copy(
        pinnedScene =
          PinnedScene(
            observation = observation,
            stretch = stretch,
            stretches = stretches,
            tiles = Loadable.Loading,
          )
      )
    }
    sceneJob = viewModelScope.launch {
      if (delayMs > 0) delay(delayMs)
      when (
        val tiles = loadCatching {
          repository.sceneTiles(plot.surveyId, observation.sceneId, stretch)
        }
      ) {
        is Loadable.Loaded -> {
          updatePinned(observation) {
            it.copy(
              stretch = tiles.value.stretch,
              tiles = Loadable.Loaded(tiles.value.urlTemplate),
              attribution = tiles.value.attribution,
            )
          }
          showSceneOnMap(
            ImagerySource(
              id = PINNED_SCENE_SOURCE_ID,
              name = "${observation.platformLabel} ${observation.date}",
              urlTemplate = tiles.value.urlTemplate,
              type = ImagerySourceType.XYZ_TILES,
            )
          )
        }
        is Loadable.Failed -> {
          updatePinned(observation) { it.copy(tiles = tiles) }
          showSceneOnMap(null)
        }
        else -> Unit
      }
    }
  }

  /** Applies [transform] to the pinned scene if it is still [observation]'s. */
  private fun updatePinned(
    observation: TimeSeriesObservation,
    transform: (PinnedScene) -> PinnedScene,
  ) = state.update { current ->
    val pinned = current.pinnedScene?.takeIf { it.observation == observation }
    if (pinned == null) current else current.copy(pinnedScene = transform(pinned))
  }

  /**
   * Starts loading whatever of the followed plot's statistics and series is still [Loadable.Idle]
   * (cached results apply at once). A result is dropped if the user moved to another plot (or
   * source) before it arrived.
   */
  private fun load() {
    val current = state.value
    val plot = current.plot?.takeIf { it.isSupported } ?: return
    val repository = repository ?: return
    val key = PlotImageryRepository.plotKey(plot)
    fun isStillFollowed(state: TimeSeriesUiState) =
      state.plot?.let(PlotImageryRepository::plotKey) == key

    if (current.statistics == Loadable.Idle) {
      val cached = repository.cachedStatistics(plot)
      if (cached != null) {
        state.update { it.copy(statistics = Loadable.Loaded(cached)) }
      } else {
        state.update { it.copy(statistics = Loadable.Loading) }
        statisticsJob = viewModelScope.launch {
          val result = loadCatching { repository.statistics(plot) }
          state.update { if (isStillFollowed(it)) it.copy(statistics = result) else it }
        }
      }
    }

    if (current.series == Loadable.Idle) {
      val source = current.source
      val cached = repository.cachedTimeSeries(plot, source)
      if (cached != null) {
        state.update { it.copy(series = Loadable.Loaded(cached)) }
      } else {
        state.update { it.copy(series = Loadable.Loading) }
        seriesJob = viewModelScope.launch {
          val result = loadCatching { repository.timeSeries(plot, source) }
          state.update {
            if (isStillFollowed(it) && it.source == source) it.copy(series = result) else it
          }
        }
      }
    }
  }

  /** [block]'s result as [Loadable.Loaded], or the reason it failed; cancellation propagates. */
  private suspend fun <T> loadCatching(block: suspend () -> T): Loadable<T> =
    try {
      Loadable.Loaded(block())
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      PlotImageryRepository.failure(e)
    }

  companion object {
    /** ID of the synthetic imagery source that shows the pinned scene on the map. */
    const val PINNED_SCENE_SOURCE_ID = "time-series-pinned-scene"
  }
}
