/*
 * Copyright 2026 Google LLC
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

package org.groundplatform.v2.devtools.prototypeapp.domain.usecase

import org.groundplatform.v2.devtools.prototypeapp.domain.model.DeviceStorageInfo
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OfflineTilePackageItem

/**
 * Estimates the device storage breakdown shown in Settings and Offline maps: a fixed baseline
 * imagery cache plus downloaded tile packages, and a data footprint that grows with the number of
 * mutations and entities on the device.
 *
 * The prototype has no real file system, so sizes are modelled rather than measured.
 */
class EstimateDeviceStorageUseCase {
  operator fun invoke(
    offlineTilePackages: List<OfflineTilePackageItem>,
    mutationCount: Int,
    entityCount: Int,
  ): DeviceStorageInfo {
    val downloadedTilesBytes =
      offlineTilePackages.filter { it.isDownloaded }.sumOf { tilePackageBytes(it) }
    return DeviceStorageInfo(
      totalBytes = TOTAL_DEVICE_BYTES,
      downloadedImageryBytes = BASE_IMAGERY_BYTES + downloadedTilesBytes,
      dataBytes =
        BASE_DATA_BYTES + mutationCount * BYTES_PER_MUTATION + entityCount * BYTES_PER_ENTITY,
      otherUsedBytes = OTHER_USED_BYTES,
    )
  }

  private fun tilePackageBytes(tilePackage: OfflineTilePackageItem): Long =
    when (tilePackage.id) {
      "pkg-nyeri-satellite" -> 82_700_000L
      "pkg-nyeri-topo" -> 14_200_000L
      "pkg-kenya-regional" -> 168_000_000L
      else -> DEFAULT_TILE_PACKAGE_BYTES
    }

  private companion object {
    const val TOTAL_DEVICE_BYTES = 64L * 1024L * 1024L * 1024L
    const val BASE_IMAGERY_BYTES = 1_850_000_000L
    const val BASE_DATA_BYTES = 420_000_000L
    const val OTHER_USED_BYTES = 18_200_000_000L
    const val BYTES_PER_MUTATION = 15_000L
    const val BYTES_PER_ENTITY = 8_000L
    const val DEFAULT_TILE_PACKAGE_BYTES = 50_000_000L
  }
}
