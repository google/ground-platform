/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openforis.collectone.v2.app.domain.usecase

import org.openforis.collectone.v2.app.domain.model.DataSize
import org.openforis.collectone.v2.app.domain.model.DeviceStorageInfo
import org.openforis.collectone.v2.app.domain.model.OfflineMapArea
import org.openforis.collectone.v2.app.domain.model.sum
import org.openforis.collectone.v2.app.domain.repository.DeviceStorageRepository

/**
 * The device storage breakdown shown in Settings and Offline maps: the total and free space
 * measured by [DeviceStorageRepository], the size of the saved map areas, and the app's field data,
 * approximated from the number of pending changes and map features on the device.
 *
 * The saved imagery and the data are capped by the used space (total minus free), so the breakdown
 * adds up and the free space always shows as measured.
 */
class EstimateDeviceStorageUseCase(private val deviceStorageRepository: DeviceStorageRepository) {
  /**
   * The breakdown with the saved [areas], or null when this platform can't measure its total or
   * free space.
   */
  suspend operator fun invoke(
    areas: List<OfflineMapArea>,
    mutationCount: Int,
    entityCount: Int,
  ): DeviceStorageInfo? {
    val total = deviceStorageRepository.totalSpace() ?: return null
    val free = deviceStorageRepository.freeSpace() ?: return null
    val used = DataSize((total.bytes - free.bytes).coerceAtLeast(0))
    val imagery = minOf(areas.map { it.totalSize }.sum(), used)
    val data =
      minOf(
        DataSize(mutationCount * BYTES_PER_MUTATION + entityCount * BYTES_PER_ENTITY),
        used - imagery,
      )
    return DeviceStorageInfo(
      totalSpace = total,
      freeSpace = free,
      savedImagery = imagery,
      data = data,
    )
  }

  private companion object {
    /** About what a pending change takes in the local database. */
    const val BYTES_PER_MUTATION = 15_000L

    /** About what a map feature, with its geometry and properties, takes. */
    const val BYTES_PER_ENTITY = 8_000L
  }
}
