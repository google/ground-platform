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
package org.openforis.collectone.v2.app.client.offline

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.domain.model.OfflineImageryType
import org.openforis.collectone.v2.app.domain.model.OfflineMapLimits
import org.openforis.collectone.v2.app.domain.model.OfflineRegionMode
import org.openforis.collectone.v2.map.LngLatBounds

/**
 * One offline tile region to estimate, download, or remove: one imagery layer of a saved map area.
 *
 * @property regionId `<areaId>-satellite` or `<areaId>-terrain` ([regionIdOf]).
 * @property bounds the area to save, at most [OfflineMapLimits.MAX_AREA_KM2] (the domain checks).
 * @property imageryType which basemap style to save.
 */
data class OfflineRegionRequest(
  val regionId: String,
  val bounds: LngLatBounds,
  val imageryType: OfflineImageryType,
  val minZoom: Int = OfflineMapLimits.MIN_ZOOM,
  val maxZoom: Int = OfflineMapLimits.MAX_ZOOM,
) {
  companion object {
    /** The region ID of imagery [type] of saved area [areaId]. */
    fun regionIdOf(areaId: String, type: OfflineImageryType): String =
      "$areaId-${type.name.lowercase()}"
  }
}

/**
 * The estimated size of a region: [storageBytes] on the device, [transferBytes] over the network,
 * and the share either may be off by ([errorMargin], e.g. 0.2).
 */
data class OfflineRegionEstimate(
  val storageBytes: Long,
  val transferBytes: Long,
  val errorMargin: Double,
)

/**
 * A region download's progress: [completed] of [required] resources (tile packs) and the
 * [completedBytes] downloaded so far.
 */
data class OfflineRegionProgress(
  val completed: Long,
  val required: Long,
  val completedBytes: Long,
) {
  /** The share done, from 0 to 1. */
  val fraction: Float
    get() = if (required <= 0) 0f else (completed.toFloat() / required).coerceIn(0f, 1f)
}

/**
 * Saves map tile regions on the device for offline use: the data layer's port to the map SDK, which
 * `:shared:app:data` can't call itself. Each host plugs in its implementation through
 * `platformModule(offlineRegionClient = …)`:
 * - [SimulatedOfflineRegionClient] in the mobile app in the browser (its map has no offline
 *   storage) and tests;
 * - [UnavailableOfflineRegionClient] on Android and iOS until their native clients land;
 * - a native client (Mapbox TileStore) later, with [OfflineRegionMode.NATIVE].
 *
 * Failures are thrown as [OfflineRegionException]s carrying a [DomainError].
 */
interface OfflineRegionClient {
  /** How this client saves regions; [OfflineRegionMode.SIMULATED] drives the browser notice. */
  val mode: OfflineRegionMode

  /** The estimated size of [request]'s region. */
  suspend fun estimate(request: OfflineRegionRequest): OfflineRegionEstimate

  /**
   * Downloads [request]'s region, emitting progress until it completes. Cancelling the collector
   * stops the download (its partial data stays until [remove]); failures are thrown.
   */
  fun download(request: OfflineRegionRequest): Flow<OfflineRegionProgress>

  /** The IDs of the regions saved on the device, for reconciling the saved areas (NATIVE only). */
  suspend fun regionIds(): Set<String>

  /** Removes region [regionId] and its data; nothing happens when it doesn't exist. */
  suspend fun remove(regionId: String)
}

/** A failure of an [OfflineRegionClient] operation, for the reason in [error]. */
class OfflineRegionException(val error: DomainError, message: String) : Exception(message)

/**
 * The [OfflineRegionClient] of builds that can't save map areas yet
 * ([OfflineRegionMode.UNAVAILABLE]): the UI hides its entry points, and every download or estimate
 * fails.
 */
class UnavailableOfflineRegionClient : OfflineRegionClient {
  override val mode: OfflineRegionMode = OfflineRegionMode.UNAVAILABLE

  override suspend fun estimate(request: OfflineRegionRequest): OfflineRegionEstimate =
    throw unavailable()

  override fun download(request: OfflineRegionRequest): Flow<OfflineRegionProgress> = flow {
    throw unavailable()
  }

  override suspend fun regionIds(): Set<String> = emptySet()

  override suspend fun remove(regionId: String) = Unit

  private fun unavailable() =
    OfflineRegionException(DomainError.Unknown(), "Saving map areas isn't available in this build")
}

/**
 * This platform's [OfflineRegionClient]: simulated in the browser, unavailable elsewhere until the
 * native clients land. [isOnline] tells a simulated client whether the device is online.
 */
expect fun defaultOfflineRegionClient(isOnline: () -> Boolean): OfflineRegionClient
