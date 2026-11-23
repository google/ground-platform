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

import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.domain.error.DomainResult
import org.openforis.collectone.v2.app.domain.model.OfflineAreaEstimate
import org.openforis.collectone.v2.app.domain.model.OfflineAreaVerdict
import org.openforis.collectone.v2.app.domain.model.OfflineImageryType
import org.openforis.collectone.v2.app.domain.model.OfflineMapLimits
import org.openforis.collectone.v2.app.domain.model.OfflineRegionMode
import org.openforis.collectone.v2.app.domain.model.spansAntimeridian
import org.openforis.collectone.v2.app.domain.repository.ConnectivityRepository
import org.openforis.collectone.v2.app.domain.repository.DeviceStorageRepository
import org.openforis.collectone.v2.app.domain.repository.OfflineMapRepository
import org.openforis.collectone.v2.map.LngLatBounds

/**
 * Estimates saving a framed area for offline use, applying the guards in order, each stopping the
 * ones after it:
 * 1. this build can't save areas → [OfflineAreaVerdict.Unavailable];
 * 2. the frame crosses ±180° → [OfflineAreaVerdict.CrossesAntimeridian];
 * 3. it's larger than [OfflineMapLimits.MAX_AREA_KM2] → [OfflineAreaVerdict.TooLarge] (checked
 *    before any request, so no estimate or name lookup runs for it);
 * 4. the device is offline → [OfflineAreaVerdict.Offline];
 * 5. the sizes of every imagery type are estimated (a network failure reads as offline);
 * 6. the selected types, with the margin and reserve of [OfflineMapLimits], must fit in the free
 *    space → [OfflineAreaVerdict.NotEnoughSpace] (skipped when free space can't be measured);
 * 7. otherwise [OfflineAreaVerdict.Ready].
 *
 * Every imagery type is estimated, whatever [types] selects, so the UI updates the total for
 * another selection with [OfflineAreaEstimate.withTypes] instead of estimating again.
 */
class EstimateOfflineAreaUseCase(
  private val offlineMapRepository: OfflineMapRepository,
  private val deviceStorageRepository: DeviceStorageRepository,
  private val connectivityRepository: ConnectivityRepository,
) {
  suspend operator fun invoke(
    bounds: LngLatBounds,
    types: Set<OfflineImageryType> = OfflineImageryType.entries.toSet(),
  ): OfflineAreaEstimate {
    val area = if (bounds.spansAntimeridian) 0.0 else bounds.areaSquareKilometers()
    fun stop(verdict: OfflineAreaVerdict) = OfflineAreaEstimate(area, types, verdict)
    return when {
      offlineMapRepository.regionMode == OfflineRegionMode.UNAVAILABLE ->
        stop(OfflineAreaVerdict.Unavailable)
      bounds.spansAntimeridian -> stop(OfflineAreaVerdict.CrossesAntimeridian)
      area > OfflineMapLimits.MAX_AREA_KM2 ->
        stop(OfflineAreaVerdict.TooLarge(OfflineMapLimits.MAX_AREA_KM2, area))
      !connectivityRepository.isOnline() -> stop(OfflineAreaVerdict.Offline)
      else ->
        when (
          val sizes = offlineMapRepository.estimateSizes(bounds, OfflineImageryType.entries.toSet())
        ) {
          is DomainResult.Failure ->
            stop(
              if (sizes.error == DomainError.Network) OfflineAreaVerdict.Offline
              else OfflineAreaVerdict.Failed(sizes.error)
            )
          is DomainResult.Success -> {
            val freeSpace = deviceStorageRepository.freeSpace()
            OfflineAreaEstimate(
                areaKm2 = area,
                types = types,
                verdict = OfflineAreaVerdict.Ready,
                sizes = sizes.value,
                freeSpace = freeSpace,
              )
              .withTypes(types)
          }
        }
    }
  }
}
