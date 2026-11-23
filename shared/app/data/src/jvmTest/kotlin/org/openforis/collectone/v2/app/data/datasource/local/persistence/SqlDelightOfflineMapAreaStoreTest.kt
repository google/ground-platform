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

import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import openforis.collectone.v1.userdata.ClientSyncState
import openforis.collectone.v1.userdata.PendingMutationRecord
import org.openforis.collectone.v2.app.client.storage.JdbcSqlDriverFactory
import org.openforis.collectone.v2.app.domain.model.DataSize
import org.openforis.collectone.v2.app.domain.model.OfflineAreaLayer
import org.openforis.collectone.v2.app.domain.model.OfflineAreaLayerStatus
import org.openforis.collectone.v2.app.domain.model.OfflineAreaName
import org.openforis.collectone.v2.app.domain.model.OfflineImageryType
import org.openforis.collectone.v2.app.domain.model.OfflineMapArea
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.map.LngLatBounds

/** Saved offline map areas in a real SQLite database (JDBC), including databases from version 4. */
class SqlDelightOfflineMapAreaStoreTest {
  private val databaseFile = Files.createTempFile("collect-one-offline-areas", ".db").toFile()
  private val connection = LocalDatabaseConnection(JdbcSqlDriverFactory(databaseFile))
  private val store = SqlDelightOfflineMapAreaStore(connection)

  private val named =
    OfflineMapArea(
      id = "a1",
      name = OfflineAreaName.Named("Othaya, Nyeri"),
      bounds = LngLatBounds(36.90, -0.40, 36.95, -0.37),
      createdAt = Instant.parse("2026-11-08T10:00:00Z"),
      layers =
        listOf(
          OfflineAreaLayer(
            OfflineImageryType.SATELLITE,
            OfflineAreaLayerStatus.READY,
            size = DataSize(38_000_000),
          ),
          OfflineAreaLayer(OfflineImageryType.TERRAIN, OfflineAreaLayerStatus.SAVING),
        ),
    )

  private val nearCoordinates =
    OfflineMapArea(
      id = "a2",
      name = OfflineAreaName.NearCoordinates(LatLng(-0.385, 36.925)),
      bounds = LngLatBounds(36.0, -1.0, 36.05, -0.95),
      createdAt = Instant.parse("2026-11-09T10:00:00Z"),
      layers = listOf(OfflineAreaLayer(OfflineImageryType.TERRAIN, OfflineAreaLayerStatus.PAUSED)),
    )

  @AfterTest
  fun deleteDatabase() {
    connection.closeNow()
    databaseFile.delete()
  }

  @Test
  fun storedAreas_surviveReopeningTheDatabase_newestFirst() {
    runBlocking {
      store.insertArea(named)
      store.insertArea(nearCoordinates)
      connection.close()
    }

    assertEquals(
      listOf(nearCoordinates, named),
      runBlocking { SqlDelightOfflineMapAreaStore(connection).load() },
    )
  }

  @Test
  fun layers_areReplacedAndDeletedByType() {
    val ready =
      OfflineAreaLayer(
        OfflineImageryType.TERRAIN,
        OfflineAreaLayerStatus.READY,
        size = DataSize(9_000_000),
      )
    runBlocking {
      store.insertArea(named)
      store.putLayer("a1", ready)
    }
    assertEquals(named.layers.take(1) + ready, runBlocking { store.load() }.single().layers)

    runBlocking { store.deleteLayer("a1", OfflineImageryType.SATELLITE) }
    assertEquals(listOf(ready), runBlocking { store.load() }.single().layers)
  }

  @Test
  fun deletingAnArea_deletesItsLayers() {
    runBlocking {
      store.insertArea(named)
      store.deleteArea("a1")
      // An area with the same ID starts without the old layers.
      store.insertArea(named.copy(layers = emptyList()))
    }

    assertEquals(emptyList(), runBlocking { store.load() }.single().layers)
  }

  @Test
  fun liveStateIsNotStored() {
    runBlocking {
      store.insertArea(
        named.copy(
          layers =
            listOf(
              OfflineAreaLayer(
                OfflineImageryType.SATELLITE,
                OfflineAreaLayerStatus.SAVING,
                progress = 0.5f,
                savedSize = DataSize(10),
              )
            )
        )
      )
    }

    val layer = runBlocking { store.load() }.single().layers.single()
    assertEquals(0f, layer.progress)
    assertEquals(DataSize.ZERO, layer.savedSize)
  }

  @Test
  fun clearingTheSyncState_keepsSavedAreas() {
    runBlocking {
      store.insertArea(named)
      // What signing out does to the database.
      SqlDelightSyncStateStore(connection).replace(ClientSyncState(), account = "")
    }

    assertEquals(listOf(named), runBlocking { store.load() })
  }

  @Test
  fun versionFourDatabase_isMigratedWithItsOutboxKept() {
    // A database of schema version 4 (src/commonMain/sqldelight/databases/4.db) holding one unsent
    // edit.
    File(VERSION_FOUR_SCHEMA).copyTo(databaseFile, overwrite = true)
    val pending = PendingMutationRecord(mutation_id = "m1", client_seq = 1L)
    DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}").use { jdbc ->
      jdbc.createStatement().use { it.execute("PRAGMA user_version = 4") }
      jdbc.prepareStatement("INSERT INTO outbox_mutation VALUES (?, ?, ?)").use {
        it.setString(1, "m1")
        it.setLong(2, 1L)
        it.setBytes(3, PendingMutationRecord.ADAPTER.encode(pending))
        it.executeUpdate()
      }
    }

    assertTrue(runBlocking { store.load() }.isEmpty())
    runBlocking { store.insertArea(named) }

    assertEquals(listOf(named), runBlocking { store.load() })
    assertEquals(
      listOf(pending),
      runBlocking { SqlDelightSyncStateStore(connection).load() }.outbox,
    )
  }

  private companion object {
    const val VERSION_FOUR_SCHEMA = "src/commonMain/sqldelight/databases/4.db"
  }
}
