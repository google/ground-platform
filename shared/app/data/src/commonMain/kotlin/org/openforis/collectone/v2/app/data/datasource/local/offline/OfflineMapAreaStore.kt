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
package org.openforis.collectone.v2.app.data.datasource.local.offline

import org.openforis.collectone.v2.app.domain.model.OfflineAreaLayer
import org.openforis.collectone.v2.app.domain.model.OfflineImageryType
import org.openforis.collectone.v2.app.domain.model.OfflineMapArea

/**
 * Keeps the map areas saved on this device for offline use: in the local database's
 * `offline_map_area` tables when the host has one, else in memory. Only each layer's type, status,
 * and size are kept; download progress and failures are live state the repository holds.
 *
 * Saved areas belong to the device, so nothing clears this store on sign-out.
 */
interface OfflineMapAreaStore {
  /** The stored areas, newest first. */
  suspend fun load(): List<OfflineMapArea>

  /** Stores the new [area] with its layers. */
  suspend fun insertArea(area: OfflineMapArea)

  /** Stores [layer] of area [areaId], replacing the stored layer of its type. */
  suspend fun putLayer(areaId: String, layer: OfflineAreaLayer)

  /** Forgets layer [type] of area [areaId]. */
  suspend fun deleteLayer(areaId: String, type: OfflineImageryType)

  /** Forgets area [areaId] and its layers. */
  suspend fun deleteArea(areaId: String)
}

/** [OfflineMapAreaStore] in memory, for hosts without a local database (and tests). */
class InMemoryOfflineMapAreaStore(areas: List<OfflineMapArea> = emptyList()) : OfflineMapAreaStore {
  private var areas: List<OfflineMapArea> = areas.map { it.storedCopy() }

  override suspend fun load(): List<OfflineMapArea> = areas.sortedByDescending { it.createdAt }

  override suspend fun insertArea(area: OfflineMapArea) {
    areas = areas.filterNot { it.id == area.id } + area.storedCopy()
  }

  override suspend fun putLayer(areaId: String, layer: OfflineAreaLayer) {
    update(areaId) { layers -> layers.filterNot { it.type == layer.type } + layer.stored() }
  }

  override suspend fun deleteLayer(areaId: String, type: OfflineImageryType) {
    update(areaId) { layers -> layers.filterNot { it.type == type } }
  }

  override suspend fun deleteArea(areaId: String) {
    areas = areas.filterNot { it.id == areaId }
  }

  private fun update(areaId: String, change: (List<OfflineAreaLayer>) -> List<OfflineAreaLayer>) {
    areas = areas.map { area ->
      if (area.id == areaId) area.copy(layers = change(area.layers).sortedBy { it.type }) else area
    }
  }
}

/** This area as a store keeps it: layers in type order, without live state. */
internal fun OfflineMapArea.storedCopy(): OfflineMapArea =
  copy(layers = layers.map { it.stored() }.sortedBy { it.type })

/** This layer as a store keeps it: its type, status, and size. */
internal fun OfflineAreaLayer.stored(): OfflineAreaLayer =
  OfflineAreaLayer(type = type, status = status, size = size)
