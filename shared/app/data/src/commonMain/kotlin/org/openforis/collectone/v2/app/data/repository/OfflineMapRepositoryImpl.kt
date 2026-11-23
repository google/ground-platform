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
package org.openforis.collectone.v2.app.data.repository

import co.touchlab.kermit.Logger
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.openforis.collectone.v2.app.client.offline.OfflineRegionClient
import org.openforis.collectone.v2.app.client.offline.OfflineRegionRequest
import org.openforis.collectone.v2.app.client.offline.OfflineRegionRequest.Companion.regionIdOf
import org.openforis.collectone.v2.app.client.places.OfflineAreaNameClient
import org.openforis.collectone.v2.app.client.places.PlaceholderOfflineAreaNameClient
import org.openforis.collectone.v2.app.data.datasource.local.offline.OfflineMapAreaStore
import org.openforis.collectone.v2.app.data.datasource.local.offline.storedCopy
import org.openforis.collectone.v2.app.data.error.domainResultOf
import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.domain.error.DomainResult
import org.openforis.collectone.v2.app.domain.model.DataSize
import org.openforis.collectone.v2.app.domain.model.OfflineAreaLayer
import org.openforis.collectone.v2.app.domain.model.OfflineAreaLayerStatus
import org.openforis.collectone.v2.app.domain.model.OfflineAreaName
import org.openforis.collectone.v2.app.domain.model.OfflineImageryType
import org.openforis.collectone.v2.app.domain.model.OfflineMapArea
import org.openforis.collectone.v2.app.domain.model.OfflineRegionMode
import org.openforis.collectone.v2.app.domain.repository.OfflineMapRepository
import org.openforis.collectone.v2.map.LngLatBounds

private val log = Logger.withTag("OfflineMapRepository")

/**
 * [OfflineMapRepository] over the saved areas in an [OfflineMapAreaStore] and the tile regions of
 * an [OfflineRegionClient] (one region per layer, `<areaId>-satellite` or `<areaId>-terrain`).
 * - **Downloads** run in [scope] (the app scope), one job per layer, side by side, so they outlive
 *   the screen that started them. Their progress is kept in memory and merged into [observeAreas];
 *   status changes (SAVING, READY, PAUSED, FAILED) are stored. A lost connection pauses a layer;
 *   other errors fail it.
 * - **On first use** the stored areas are loaded: layers left SAVING (the app stopped mid-download)
 *   become PAUSED, and in [OfflineRegionMode.NATIVE] the areas are reconciled with the client's
 *   regions (READY layers whose region is gone become PAUSED; regions of no layer are removed).
 * - **Names** come from [nameClient], or from [placeholderNameClient] when downloads are simulated,
 *   falling back to the frame's center.
 */
class OfflineMapRepositoryImpl(
  private val store: OfflineMapAreaStore,
  private val regionClient: OfflineRegionClient,
  private val nameClient: OfflineAreaNameClient,
  private val clock: Clock,
  private val scope: CoroutineScope,
  private val placeholderNameClient: OfflineAreaNameClient = PlaceholderOfflineAreaNameClient(),
  private val newAreaId: () -> String = ::randomAreaId,
) : OfflineMapRepository {
  override val regionMode: OfflineRegionMode
    get() = regionClient.mode

  /** Live state of a layer that isn't stored: download progress and why it stopped. */
  private data class LiveLayer(
    val progress: Float = 0f,
    val savedSize: DataSize = DataSize.ZERO,
    val failure: DomainError? = null,
  )

  /** Guards [areas], [jobs], and the store's writes. Not reentrant: take it once per operation. */
  private val mutex = Mutex()

  /** The stored areas, newest first; null until loaded. */
  private val areas = MutableStateFlow<List<OfflineMapArea>?>(null)

  /** The live state of layers by region ID. */
  private val live = MutableStateFlow<Map<String, LiveLayer>>(emptyMap())

  /** The running download of each region ID (a flow, so jobs read it safely without [mutex]). */
  private val jobs = MutableStateFlow<Map<String, Job>>(emptyMap())

  override fun observeAreas(): Flow<List<OfflineMapArea>> =
    combine(areas.filterNotNull(), live) { stored, live ->
        stored.map { area -> area.copy(layers = area.layers.map { it.withLive(area.id, live) }) }
      }
      .onStart { ensureLoaded() }

  override suspend fun estimateSizes(
    bounds: LngLatBounds,
    types: Set<OfflineImageryType>,
  ): DomainResult<Map<OfflineImageryType, DataSize>> = domainResultOf {
    coroutineScope {
      types
        .map { type -> async { type to estimate(bounds, type, regionId = "estimate-${type.id}") } }
        .awaitAll()
        .toMap()
    }
  }

  override suspend fun resolveName(bounds: LngLatBounds, zoom: Double): OfflineAreaName {
    val client =
      if (regionMode == OfflineRegionMode.SIMULATED) placeholderNameClient else nameClient
    return client.name(bounds, zoom)?.let(OfflineAreaName::Named)
      ?: OfflineAreaName.NearCoordinates(bounds.center)
  }

  override suspend fun save(
    bounds: LngLatBounds,
    name: OfflineAreaName,
    types: Set<OfflineImageryType>,
  ): DomainResult<String> = domainResultOf {
    ensureLoaded()
    val id = newAreaId()
    // While saving, a layer's size is its estimate (null when it can't be estimated).
    val layers =
      types.sorted().map { type ->
        val estimate =
          when (val result = domainResultOf { estimate(bounds, type, regionIdOf(id, type)) }) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> null
          }
        OfflineAreaLayer(type, OfflineAreaLayerStatus.SAVING, size = estimate)
      }
    val area = OfflineMapArea(id, name, bounds, clock.now(), layers)
    mutex.withLock {
      store.insertArea(area)
      areas.update { listOf(area.storedCopy()) + it.orEmpty() }
      layers.forEach { startDownload(area, it.type) }
    }
    log.i { "Saving area $id (${types.joinToString { it.id }})" }
    id
  }

  override suspend fun cancel(areaId: String, type: OfflineImageryType?) {
    ensureLoaded()
    mutex.withLock {
      val area = storedArea(areaId) ?: return
      area.layers
        .filter {
          if (type == null) it.status == OfflineAreaLayerStatus.SAVING else it.type == type
        }
        .forEach { removeLayerLocked(areaId, it.type) }
    }
  }

  override suspend fun resume(areaId: String, type: OfflineImageryType?) {
    ensureLoaded()
    mutex.withLock {
      val area = storedArea(areaId) ?: return
      area.layers
        .filter { it.status.isStopped && (type == null || it.type == type) }
        .forEach { layer ->
          writeLayerLocked(areaId, layer.copy(status = OfflineAreaLayerStatus.SAVING))
          live.update { it - regionIdOf(areaId, layer.type) }
          startDownload(area, layer.type)
        }
    }
  }

  override suspend fun removeLayer(areaId: String, type: OfflineImageryType) {
    ensureLoaded()
    mutex.withLock { removeLayerLocked(areaId, type) }
  }

  override suspend fun removeArea(areaId: String) {
    ensureLoaded()
    mutex.withLock {
      val area = storedArea(areaId) ?: return
      area.layers.forEach { stopAndRemoveRegion(regionIdOf(areaId, it.type)) }
      store.deleteArea(areaId)
      areas.update { stored -> stored?.filterNot { it.id == areaId } }
    }
  }

  private suspend fun estimate(
    bounds: LngLatBounds,
    type: OfflineImageryType,
    regionId: String,
  ): DataSize =
    DataSize(regionClient.estimate(OfflineRegionRequest(regionId, bounds, type)).storageBytes)

  /** Loads the stored areas once, pausing interrupted layers and reconciling NATIVE regions. */
  private suspend fun ensureLoaded() {
    if (areas.value != null) return
    mutex.withLock {
      if (areas.value != null) return
      val stored = store.load()
      val regionIds =
        if (regionMode == OfflineRegionMode.NATIVE) {
          when (val result = domainResultOf { regionClient.regionIds() }) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> null.also { log.w { "Couldn't list offline regions" } }
          }
        } else null
      areas.value = stored.map { area ->
        area.copy(layers = area.layers.map { reconcile(area.id, it, regionIds) })
      }
      regionIds?.let { removeOrphanRegions(stored, it) }
    }
  }

  private suspend fun reconcile(
    areaId: String,
    layer: OfflineAreaLayer,
    regionIds: Set<String>?,
  ): OfflineAreaLayer {
    val interrupted = layer.status == OfflineAreaLayerStatus.SAVING
    val regionGone =
      regionIds != null &&
        layer.status == OfflineAreaLayerStatus.READY &&
        regionIdOf(areaId, layer.type) !in regionIds
    if (!interrupted && !regionGone) return layer
    return layer.copy(status = OfflineAreaLayerStatus.PAUSED).also { store.putLayer(areaId, it) }
  }

  private suspend fun removeOrphanRegions(stored: List<OfflineMapArea>, regionIds: Set<String>) {
    val known = stored.flatMap { area -> area.layers.map { regionIdOf(area.id, it.type) } }.toSet()
    (regionIds - known).forEach { regionId ->
      log.i { "Removing offline region $regionId of no saved area" }
      removeRegion(regionId)
    }
  }

  /** Starts (or restarts) the download of layer [type] of [area]. Call with [mutex] held. */
  private fun startDownload(area: OfflineMapArea, type: OfflineImageryType) {
    val regionId = regionIdOf(area.id, type)
    stopJob(regionId)
    val request = OfflineRegionRequest(regionId, area.bounds, type)
    val job =
      scope.launch(start = CoroutineStart.LAZY) {
        val thisJob = currentCoroutineContext().job
        var savedBytes = 0L
        val outcome = domainResultOf {
          regionClient.download(request).collect { progress ->
            savedBytes = progress.completedBytes
            if (jobs.value[regionId] === thisJob) {
              live.update {
                it + (regionId to LiveLayer(progress.fraction, DataSize(progress.completedBytes)))
              }
            }
          }
        }
        mutex.withLock {
          // Cancelled, removed, or restarted meanwhile: the newer state wins.
          if (jobs.value[regionId] !== thisJob) return@withLock
          jobs.update { it - regionId }
          finishLocked(area.id, type, outcome, DataSize(savedBytes))
        }
      }
    jobs.update { it + (regionId to job) }
    job.start()
  }

  /** Stores how layer [type] of area [areaId]'s download ended. Call with [mutex] held. */
  private suspend fun finishLocked(
    areaId: String,
    type: OfflineImageryType,
    outcome: DomainResult<Unit>,
    savedSize: DataSize,
  ) {
    val layer = storedArea(areaId)?.layer(type) ?: return
    val regionId = regionIdOf(areaId, type)
    when (outcome) {
      is DomainResult.Success -> {
        val size = savedSize.takeIf { it > DataSize.ZERO } ?: layer.size
        writeLayerLocked(areaId, layer.copy(status = OfflineAreaLayerStatus.READY, size = size))
        live.update { it - regionId }
        log.i { "Saved offline region $regionId" }
      }
      is DomainResult.Failure -> {
        val status =
          if (outcome.error == DomainError.Network) OfflineAreaLayerStatus.PAUSED
          else OfflineAreaLayerStatus.FAILED
        writeLayerLocked(areaId, layer.copy(status = status))
        live.update {
          it + (regionId to (it[regionId] ?: LiveLayer()).copy(failure = outcome.error))
        }
        log.w { "Offline region $regionId stopped: ${status.name}" }
      }
    }
  }

  /** Removes layer [type] of area [areaId], and the area once empty. Call with [mutex] held. */
  private suspend fun removeLayerLocked(areaId: String, type: OfflineImageryType) {
    val area = storedArea(areaId) ?: return
    if (area.layer(type) == null) return
    stopAndRemoveRegion(regionIdOf(areaId, type))
    val remaining = area.layers.filterNot { it.type == type }
    if (remaining.isEmpty()) {
      store.deleteArea(areaId)
      areas.update { stored -> stored?.filterNot { it.id == areaId } }
    } else {
      store.deleteLayer(areaId, type)
      replaceArea(area.copy(layers = remaining))
    }
  }

  /** Stops region [regionId]'s download and removes its data. Call with [mutex] held. */
  private suspend fun stopAndRemoveRegion(regionId: String) {
    stopJob(regionId)
    live.update { it - regionId }
    removeRegion(regionId)
  }

  private fun stopJob(regionId: String) {
    val job = jobs.value[regionId] ?: return
    jobs.update { it - regionId }
    job.cancel()
  }

  private suspend fun removeRegion(regionId: String) {
    if (domainResultOf { regionClient.remove(regionId) } is DomainResult.Failure) {
      log.w { "Couldn't remove offline region $regionId" }
    }
  }

  /** Stores [layer] of area [areaId] and shows it. Call with [mutex] held. */
  private suspend fun writeLayerLocked(areaId: String, layer: OfflineAreaLayer) {
    val area = storedArea(areaId) ?: return
    store.putLayer(areaId, layer)
    replaceArea(
      area.copy(
        layers = (area.layers.filterNot { it.type == layer.type } + layer).sortedBy { it.type }
      )
    )
  }

  private fun replaceArea(area: OfflineMapArea) {
    val stored = area.storedCopy()
    areas.update { list -> list?.map { if (it.id == area.id) stored else it } }
  }

  private fun storedArea(areaId: String): OfflineMapArea? =
    areas.value?.firstOrNull { it.id == areaId }

  private fun OfflineAreaLayer.withLive(areaId: String, live: Map<String, LiveLayer>) =
    live[regionIdOf(areaId, type)]?.let { state ->
      copy(
        progress = if (status == OfflineAreaLayerStatus.READY) 1f else state.progress,
        savedSize = state.savedSize,
        failure = state.failure,
      )
    } ?: this

  private val OfflineAreaLayerStatus.isStopped: Boolean
    get() = this == OfflineAreaLayerStatus.PAUSED || this == OfflineAreaLayerStatus.FAILED

  private val OfflineImageryType.id: String
    get() = name.lowercase()
}

@OptIn(ExperimentalUuidApi::class) private fun randomAreaId(): String = Uuid.random().toString()
