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

import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.openforis.collectone.v2.app.domain.error.DomainResult
import org.openforis.collectone.v2.app.domain.model.DataSize
import org.openforis.collectone.v2.app.domain.model.OfflineAreaName
import org.openforis.collectone.v2.app.domain.model.OfflineImageryType
import org.openforis.collectone.v2.app.domain.model.OfflineMapArea
import org.openforis.collectone.v2.app.domain.model.OfflineRegionMode
import org.openforis.collectone.v2.app.domain.repository.DeviceStorageRepository
import org.openforis.collectone.v2.app.domain.repository.OfflineMapRepository
import org.openforis.collectone.v2.map.LngLatBounds

/** An [OfflineMapRepository] with scripted estimates that records saves. */
internal class FakeOfflineMapRepository(
  override val regionMode: OfflineRegionMode = OfflineRegionMode.SIMULATED,
  var sizes: DomainResult<Map<OfflineImageryType, DataSize>> =
    DomainResult.Success(
      mapOf(
        OfflineImageryType.SATELLITE to DataSize(30_000_000),
        OfflineImageryType.TERRAIN to DataSize(10_000_000),
      )
    ),
) : OfflineMapRepository {
  val areas = MutableStateFlow<List<OfflineMapArea>>(emptyList())
  val estimateRequests = mutableListOf<Set<OfflineImageryType>>()
  val savedNames = mutableListOf<OfflineAreaName>()

  override fun observeAreas(): Flow<List<OfflineMapArea>> = areas

  override suspend fun estimateSizes(
    bounds: LngLatBounds,
    types: Set<OfflineImageryType>,
  ): DomainResult<Map<OfflineImageryType, DataSize>> {
    estimateRequests += types
    return sizes
  }

  override suspend fun resolveName(bounds: LngLatBounds, zoom: Double): OfflineAreaName =
    OfflineAreaName.NearCoordinates(bounds.center)

  override suspend fun save(
    bounds: LngLatBounds,
    name: OfflineAreaName,
    types: Set<OfflineImageryType>,
  ): DomainResult<String> {
    savedNames += name
    val id = "area-${savedNames.size}"
    areas.value += OfflineMapArea(id, name, bounds, Instant.fromEpochSeconds(0), emptyList())
    return DomainResult.Success(id)
  }

  override suspend fun cancel(areaId: String, type: OfflineImageryType?) = Unit

  override suspend fun resume(areaId: String, type: OfflineImageryType?) = Unit

  override suspend fun removeLayer(areaId: String, type: OfflineImageryType) = Unit

  override suspend fun removeArea(areaId: String) = Unit
}

/** A [DeviceStorageRepository] reporting fixed sizes. */
internal class FakeDeviceStorageRepository(
  var free: DataSize? = DataSize.ofMegabytes(10_000),
  var total: DataSize? = DataSize.ofMegabytes(64_000),
) : DeviceStorageRepository {
  override suspend fun freeSpace(): DataSize? = free

  override suspend fun totalSpace(): DataSize? = total
}
