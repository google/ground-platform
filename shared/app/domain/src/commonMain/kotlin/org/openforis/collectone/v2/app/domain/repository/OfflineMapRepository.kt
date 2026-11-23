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
package org.openforis.collectone.v2.app.domain.repository

import kotlinx.coroutines.flow.Flow
import org.openforis.collectone.v2.app.domain.error.DomainResult
import org.openforis.collectone.v2.app.domain.model.DataSize
import org.openforis.collectone.v2.app.domain.model.OfflineAreaName
import org.openforis.collectone.v2.app.domain.model.OfflineImageryType
import org.openforis.collectone.v2.app.domain.model.OfflineMapArea
import org.openforis.collectone.v2.app.domain.model.OfflineRegionMode
import org.openforis.collectone.v2.map.LngLatBounds

/**
 * The map areas saved on this device for offline use, and their downloads.
 *
 * Saved areas belong to the device: they survive relaunches (on hosts with a local database) and
 * signing out. Downloads run in the app's scope, so they continue when the screen that started them
 * closes, but stop with the app; layers left saving are PAUSED on the next launch.
 */
interface OfflineMapRepository {
  /** How this device saves map areas: natively, simulated, or not at all. */
  val regionMode: OfflineRegionMode

  /** The saved areas, newest first, with the live progress of the layers saving now. */
  fun observeAreas(): Flow<List<OfflineMapArea>>

  /** The estimated size of each imagery type of [types] for [bounds]. */
  suspend fun estimateSizes(
    bounds: LngLatBounds,
    types: Set<OfflineImageryType>,
  ): DomainResult<Map<OfflineImageryType, DataSize>>

  /**
   * The name of the place [bounds] frames, as seen at [zoom] (which picks how specific the place
   * is), or its center when no name is found.
   */
  suspend fun resolveName(bounds: LngLatBounds, zoom: Double): OfflineAreaName

  /**
   * Saves [bounds] as a new area named [name], with a layer for each of [types], and starts their
   * downloads. Returns the new area's ID.
   */
  suspend fun save(
    bounds: LngLatBounds,
    name: OfflineAreaName,
    types: Set<OfflineImageryType>,
  ): DomainResult<String>

  /**
   * Cancels the download of [type] (every layer saving, when null) of area [areaId], removing what
   * it saved; the area is removed when it has no layers left.
   */
  suspend fun cancel(areaId: String, type: OfflineImageryType? = null)

  /** Resumes the PAUSED or FAILED layer [type] (all of them, when null) of area [areaId]. */
  suspend fun resume(areaId: String, type: OfflineImageryType? = null)

  /** Removes layer [type] of area [areaId]; removing its last layer removes the area. */
  suspend fun removeLayer(areaId: String, type: OfflineImageryType)

  /** Removes area [areaId] and all its layers. */
  suspend fun removeArea(areaId: String)
}
