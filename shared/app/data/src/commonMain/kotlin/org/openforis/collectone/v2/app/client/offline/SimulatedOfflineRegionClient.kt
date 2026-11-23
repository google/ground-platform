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

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.domain.model.OfflineImageryType
import org.openforis.collectone.v2.app.domain.model.OfflineRegionMode
import org.openforis.collectone.v2.map.LngLatBounds

/**
 * An [OfflineRegionClient] that only pretends to download ([OfflineRegionMode.SIMULATED]), for the
 * mobile app in the browser, whose map (Mapbox GL JS v1) has no offline storage, and for tests. It
 * keeps no tile data, only which regions finished (in memory); the saved areas themselves are
 * stored by the repository.
 * - **Estimates** count the 512 px tiles covering the bounds at each zoom level (web mercator) and
 *   multiply by a typical size per tile of the imagery type ([bytesPerTile]).
 * - **Downloads** advance in [STEPS] paced steps over 6 to 10 seconds (longer for larger regions)
 *   and fail with [DomainError.Network] when [isOnline] turns false.
 */
class SimulatedOfflineRegionClient(private val isOnline: () -> Boolean = { true }) :
  OfflineRegionClient {
  override val mode: OfflineRegionMode = OfflineRegionMode.SIMULATED

  private val completedRegionIds = MutableStateFlow<Set<String>>(emptySet())

  override suspend fun estimate(request: OfflineRegionRequest): OfflineRegionEstimate {
    val bytes = tileCount(request) * bytesPerTile(request.imageryType)
    return OfflineRegionEstimate(bytes, bytes, ERROR_MARGIN)
  }

  override fun download(request: OfflineRegionRequest): Flow<OfflineRegionProgress> = flow {
    val required = tileCount(request)
    val totalBytes = required * bytesPerTile(request.imageryType)
    val stepDelay = duration(totalBytes) / STEPS
    emit(OfflineRegionProgress(0, required, 0))
    for (step in 1..STEPS) {
      delay(stepDelay)
      if (!isOnline()) throw OfflineRegionException(DomainError.Network, "The device is offline")
      emit(OfflineRegionProgress(required * step / STEPS, required, totalBytes * step / STEPS))
    }
    completedRegionIds.update { it + request.regionId }
  }

  override suspend fun regionIds(): Set<String> = completedRegionIds.value

  override suspend fun remove(regionId: String) {
    completedRegionIds.update { it - regionId }
  }

  companion object {
    /** The progress steps of a download. */
    const val STEPS: Int = 20

    /** The error margin estimates report: the size per tile is rough. */
    const val ERROR_MARGIN: Double = 0.2

    /** The shortest a simulated download takes. */
    val MIN_DURATION: Duration = 6.seconds

    /** The longest a simulated download takes. */
    val MAX_DURATION: Duration = 10.seconds

    /** The extra time a simulated download takes per MB, up to [MAX_DURATION]. */
    private val DURATION_PER_MEGABYTE: Duration = 100.milliseconds

    private const val BYTES_PER_MEGABYTE = 1_000_000L

    /**
     * A typical size of one 512 px tile of imagery [type], with the street data it shares: a rough
     * calibration from published Mapbox tile sizes, until native downloads measure real ones.
     */
    fun bytesPerTile(type: OfflineImageryType): Long =
      when (type) {
        OfflineImageryType.SATELLITE -> 120_000
        OfflineImageryType.TERRAIN -> 40_000
      }

    /** How long a simulated download of [bytes] takes. */
    fun duration(bytes: Long): Duration =
      (MIN_DURATION + DURATION_PER_MEGABYTE * (bytes / BYTES_PER_MEGABYTE).toInt()).coerceAtMost(
        MAX_DURATION
      )

    /** The number of 512 px tiles covering [request]'s bounds at each of its zoom levels. */
    fun tileCount(request: OfflineRegionRequest): Long =
      (request.minZoom..request.maxZoom).sumOf { zoom -> tileCount(request.bounds, zoom) }

    /** The number of 512 px web mercator tiles covering [bounds] at [zoom]. */
    fun tileCount(bounds: LngLatBounds, zoom: Int): Long {
      val tilesPerSide = 1L shl zoom
      val columns = tileX(bounds.east, tilesPerSide) - tileX(bounds.west, tilesPerSide) + 1
      val rows = tileY(bounds.south, tilesPerSide) - tileY(bounds.north, tilesPerSide) + 1
      return columns * rows
    }

    private fun tileX(longitude: Double, tilesPerSide: Long): Long =
      floor((longitude + 180) / 360 * tilesPerSide).toLong().coerceIn(0, tilesPerSide - 1)

    private fun tileY(latitude: Double, tilesPerSide: Long): Long {
      val radians = latitude.coerceIn(-MAX_MERCATOR_LATITUDE, MAX_MERCATOR_LATITUDE) * PI / 180
      val mercator = ln(tan(PI / 4 + radians / 2))
      return floor((1 - mercator / PI) / 2 * tilesPerSide).toLong().coerceIn(0, tilesPerSide - 1)
    }

    /** The latitude limit of web mercator tiles. */
    private const val MAX_MERCATOR_LATITUDE = 85.0511287798
  }
}
