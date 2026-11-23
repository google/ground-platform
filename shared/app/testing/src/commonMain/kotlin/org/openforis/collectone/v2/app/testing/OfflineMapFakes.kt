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
package org.openforis.collectone.v2.app.testing

import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.openforis.collectone.v2.app.domain.error.DomainResult
import org.openforis.collectone.v2.app.domain.model.DataSize
import org.openforis.collectone.v2.app.domain.model.OfflineAreaLayer
import org.openforis.collectone.v2.app.domain.model.OfflineAreaLayerStatus
import org.openforis.collectone.v2.app.domain.model.OfflineAreaName
import org.openforis.collectone.v2.app.domain.model.OfflineImageryType
import org.openforis.collectone.v2.app.domain.model.OfflineMapArea
import org.openforis.collectone.v2.app.domain.model.OfflineRegionMode
import org.openforis.collectone.v2.app.domain.repository.DeviceStorageRepository
import org.openforis.collectone.v2.app.domain.repository.OfflineMapRepository
import org.openforis.collectone.v2.map.LngLatBounds

/**
 * An [OfflineMapRepository] with scripted estimates and names that records every request. Saves add
 * an area whose layers stay SAVING until a test moves them on with [setProgress] or [complete].
 *
 * @property estimateDelayMillis how long [estimateSizes] and [resolveName] take, in virtual time,
 *   so tests can move a camera while they run.
 */
class FakeOfflineMapRepository(
  override var regionMode: OfflineRegionMode = OfflineRegionMode.SIMULATED,
  var sizes: DomainResult<Map<OfflineImageryType, DataSize>> =
    DomainResult.Success(
      mapOf(
        OfflineImageryType.SATELLITE to DataSize.ofMegabytes(38),
        OfflineImageryType.TERRAIN to DataSize.ofMegabytes(10),
      )
    ),
  var name: OfflineAreaName = OfflineAreaName.Named("Othaya, Nyeri"),
  var estimateDelayMillis: Long = 0,
) : OfflineMapRepository {
  val areas = MutableStateFlow<List<OfflineMapArea>>(emptyList())
  val estimateRequests = mutableListOf<LngLatBounds>()
  val nameRequests = mutableListOf<LngLatBounds>()
  val cancelled = mutableListOf<Pair<String, OfflineImageryType?>>()
  val resumed = mutableListOf<Pair<String, OfflineImageryType?>>()
  val removedLayers = mutableListOf<Pair<String, OfflineImageryType>>()
  val removedAreas = mutableListOf<String>()

  override fun observeAreas(): Flow<List<OfflineMapArea>> = areas

  override suspend fun estimateSizes(
    bounds: LngLatBounds,
    types: Set<OfflineImageryType>,
  ): DomainResult<Map<OfflineImageryType, DataSize>> {
    estimateRequests += bounds
    if (estimateDelayMillis > 0) delay(estimateDelayMillis)
    return sizes
  }

  override suspend fun resolveName(bounds: LngLatBounds, zoom: Double): OfflineAreaName {
    nameRequests += bounds
    if (estimateDelayMillis > 0) delay(estimateDelayMillis)
    return name
  }

  override suspend fun save(
    bounds: LngLatBounds,
    name: OfflineAreaName,
    types: Set<OfflineImageryType>,
  ): DomainResult<String> {
    val id = "area-${areas.value.size + 1}"
    val estimated = (sizes as? DomainResult.Success)?.value.orEmpty()
    val layers =
      OfflineImageryType.entries
        .filter { it in types }
        .map { OfflineAreaLayer(it, OfflineAreaLayerStatus.SAVING, size = estimated[it]) }
    areas.update { listOf(OfflineMapArea(id, name, bounds, CREATED_AT, layers)) + it }
    return DomainResult.Success(id)
  }

  /** Sets the [progress] (0 to 1) of every saving layer of area [areaId]. */
  fun setProgress(areaId: String, progress: Float) =
    updateLayers(areaId) { layer ->
      if (layer.status != OfflineAreaLayerStatus.SAVING) layer
      else
        layer.copy(
          progress = progress,
          savedSize = (layer.size ?: DataSize.ZERO) * progress.toDouble(),
        )
    }

  /** Finishes every saving layer of area [areaId]. */
  fun complete(areaId: String) =
    updateLayers(areaId) { layer ->
      if (layer.status != OfflineAreaLayerStatus.SAVING) layer
      else
        layer.copy(
          status = OfflineAreaLayerStatus.READY,
          progress = 1f,
          savedSize = layer.size ?: DataSize.ZERO,
        )
    }

  /** Sets the [status] of layer [type] of area [areaId]. */
  fun setStatus(areaId: String, type: OfflineImageryType, status: OfflineAreaLayerStatus) =
    updateLayers(areaId) { if (it.type == type) it.copy(status = status) else it }

  override suspend fun cancel(areaId: String, type: OfflineImageryType?) {
    cancelled += areaId to type
    removeLayersWhere(areaId) {
      it.status == OfflineAreaLayerStatus.SAVING && (type == null || it.type == type)
    }
  }

  override suspend fun resume(areaId: String, type: OfflineImageryType?) {
    resumed += areaId to type
    updateLayers(areaId) {
      if ((type == null || it.type == type) && it.status != OfflineAreaLayerStatus.READY) {
        it.copy(status = OfflineAreaLayerStatus.SAVING, failure = null)
      } else {
        it
      }
    }
  }

  override suspend fun removeLayer(areaId: String, type: OfflineImageryType) {
    removedLayers += areaId to type
    removeLayersWhere(areaId) { it.type == type }
  }

  override suspend fun removeArea(areaId: String) {
    removedAreas += areaId
    areas.update { list -> list.filterNot { it.id == areaId } }
  }

  private fun updateLayers(areaId: String, transform: (OfflineAreaLayer) -> OfflineAreaLayer) =
    areas.update { list ->
      list.map { if (it.id == areaId) it.copy(layers = it.layers.map(transform)) else it }
    }

  private fun removeLayersWhere(areaId: String, predicate: (OfflineAreaLayer) -> Boolean) =
    areas.update { list ->
      list.mapNotNull { area ->
        if (area.id != areaId) area
        else area.copy(layers = area.layers.filterNot(predicate)).takeIf { it.layers.isNotEmpty() }
      }
    }

  companion object {
    /** When the fake's saved areas were created. */
    val CREATED_AT: Instant = Instant.fromEpochSeconds(1_770_000_000)
  }
}

/** A [DeviceStorageRepository] reporting fixed sizes (null when "unmeasurable"). */
class FakeDeviceStorageRepository(
  var free: DataSize? = DataSize.ofMegabytes(12_400),
  var total: DataSize? = DataSize.ofMegabytes(64_000),
) : DeviceStorageRepository {
  override suspend fun freeSpace(): DataSize? = free

  override suspend fun totalSpace(): DataSize? = total
}
