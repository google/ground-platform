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
package org.openforis.collectone.v2.app.data.datasource.local.persistence

import app.cash.sqldelight.async.coroutines.awaitAsList
import kotlin.time.Instant
import org.openforis.collectone.v2.app.data.datasource.local.db.LocalDatabase
import org.openforis.collectone.v2.app.data.datasource.local.offline.OfflineMapAreaStore
import org.openforis.collectone.v2.app.domain.model.DataSize
import org.openforis.collectone.v2.app.domain.model.OfflineAreaLayer
import org.openforis.collectone.v2.app.domain.model.OfflineAreaLayerStatus
import org.openforis.collectone.v2.app.domain.model.OfflineAreaName
import org.openforis.collectone.v2.app.domain.model.OfflineImageryType
import org.openforis.collectone.v2.app.domain.model.OfflineMapArea
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.map.LngLatBounds

/**
 * [OfflineMapAreaStore] in the local database's `offline_map_area` and `offline_map_area_layer`
 * tables. Rows with an imagery type or status this version doesn't know (written by a newer one)
 * are skipped.
 */
class SqlDelightOfflineMapAreaStore(private val connection: LocalDatabaseConnection) :
  OfflineMapAreaStore {
  override suspend fun load(): List<OfflineMapArea> = connection.withDatabase { db ->
    val layersByArea =
      db.offlineMapAreaQueries.selectLayers().awaitAsList().groupBy({ it.area_id }) { row ->
        val type = enumOrNull<OfflineImageryType>(row.imagery_type)
        val status = enumOrNull<OfflineAreaLayerStatus>(row.status)
        if (type == null || status == null) null
        else OfflineAreaLayer(type, status, size = row.size_bytes?.let(::DataSize))
      }
    db.offlineMapAreaQueries.selectAreas().awaitAsList().map { row ->
      val name =
        row.name?.let(OfflineAreaName::Named)
          ?: OfflineAreaName.NearCoordinates(
            LatLng.coerced(row.name_lat ?: 0.0, row.name_lng ?: 0.0)
          )
      OfflineMapArea(
        id = row.id,
        name = name,
        bounds = LngLatBounds(row.west, row.south, row.east, row.north),
        createdAt = Instant.fromEpochMilliseconds(row.created_at),
        layers = layersByArea[row.id].orEmpty().filterNotNull().sortedBy { it.type },
      )
    }
  }

  override suspend fun insertArea(area: OfflineMapArea) {
    connection.withDatabase { db ->
      db.transaction {
        val near = (area.name as? OfflineAreaName.NearCoordinates)?.position
        db.offlineMapAreaQueries.insertArea(
          id = area.id,
          name = (area.name as? OfflineAreaName.Named)?.text,
          name_lat = near?.latitude,
          name_lng = near?.longitude,
          west = area.bounds.west,
          south = area.bounds.south,
          east = area.bounds.east,
          north = area.bounds.north,
          created_at = area.createdAt.toEpochMilliseconds(),
        )
        area.layers.forEach { putLayer(db, area.id, it) }
      }
    }
  }

  override suspend fun putLayer(areaId: String, layer: OfflineAreaLayer) {
    connection.withDatabase { db -> putLayer(db, areaId, layer) }
  }

  override suspend fun deleteLayer(areaId: String, type: OfflineImageryType) {
    connection.withDatabase { db -> db.offlineMapAreaQueries.deleteLayer(areaId, type.name) }
  }

  override suspend fun deleteArea(areaId: String) {
    connection.withDatabase { db ->
      // Foreign keys aren't enforced (no PRAGMA foreign_keys), so the layers are deleted too.
      db.transaction {
        db.offlineMapAreaQueries.deleteLayersOfArea(areaId)
        db.offlineMapAreaQueries.deleteArea(areaId)
      }
    }
  }

  private suspend fun putLayer(
    db: LocalDatabase,
    areaId: String,
    layer: OfflineAreaLayer,
  ) {
    db.offlineMapAreaQueries.putLayer(areaId, layer.type.name, layer.status.name, layer.size?.bytes)
  }

  private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
    enumValues<T>().firstOrNull { it.name == name }
}
