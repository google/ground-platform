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

import kotlinx.coroutines.flow.first
import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.domain.error.DomainResult
import org.openforis.collectone.v2.app.domain.model.OfflineAreaName
import org.openforis.collectone.v2.app.domain.model.OfflineImageryType
import org.openforis.collectone.v2.app.domain.model.OfflineMapLimits
import org.openforis.collectone.v2.app.domain.model.OfflineRegionMode
import org.openforis.collectone.v2.app.domain.model.spansAntimeridian
import org.openforis.collectone.v2.app.domain.repository.ConnectivityRepository
import org.openforis.collectone.v2.app.domain.repository.OfflineMapRepository
import org.openforis.collectone.v2.map.LngLatBounds

/**
 * Saves a framed area for offline use and starts its downloads, returning the new area's ID.
 *
 * Fails with [DomainError.Network] while the device is offline, and with [DomainError.Validation]
 * when nothing is selected, this build can't save areas, or the frame breaks a guard of
 * [EstimateOfflineAreaUseCase] (the UI disables saving then, so this only guards a stale frame). A
 * place name already used by a saved area gets a number: `Othaya, Nyeri (2)`, `(3)`, ….
 */
class SaveOfflineAreaUseCase(
  private val offlineMapRepository: OfflineMapRepository,
  private val connectivityRepository: ConnectivityRepository,
) {
  suspend operator fun invoke(
    bounds: LngLatBounds,
    name: OfflineAreaName,
    types: Set<OfflineImageryType>,
  ): DomainResult<String> {
    val invalid =
      types.isEmpty() ||
        offlineMapRepository.regionMode == OfflineRegionMode.UNAVAILABLE ||
        bounds.spansAntimeridian ||
        bounds.areaSquareKilometers() > OfflineMapLimits.MAX_AREA_KM2
    return when {
      invalid -> DomainResult.Failure(DomainError.Validation)
      !connectivityRepository.isOnline() -> DomainResult.Failure(DomainError.Network)
      else -> offlineMapRepository.save(bounds, uniqueName(name), types)
    }
  }

  private suspend fun uniqueName(name: OfflineAreaName): OfflineAreaName {
    if (name !is OfflineAreaName.Named) return name
    val taken =
      offlineMapRepository
        .observeAreas()
        .first()
        .mapNotNull { (it.name as? OfflineAreaName.Named)?.text }
        .toSet()
    if (name.text !in taken) return name
    val number = generateSequence(2) { it + 1 }.first { "${name.text} ($it)" !in taken }
    return OfflineAreaName.Named("${name.text} ($number)")
  }
}
