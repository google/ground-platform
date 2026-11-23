/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.app.data.sync

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import openforis.collectone.v1.data.DataMutation
import openforis.collectone.v1.data.EntityMutation
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.userdata.ClientSyncState
import org.openforis.collectone.v2.app.client.storage.FileSyncStateFile
import org.openforis.collectone.v2.app.client.storage.JdbcSqlDriverFactory
import org.openforis.collectone.v2.app.data.datasource.local.persistence.ClientSyncStateCodec
import org.openforis.collectone.v2.app.data.datasource.local.persistence.PersistentLocalStore
import org.openforis.collectone.v2.app.data.datasource.local.persistence.SqlDelightSyncStateStore
import org.openforis.collectone.v2.app.data.datasource.local.store.InMemoryLocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStoreTransaction
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationPayload
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationTarget
import org.openforis.collectone.v2.app.data.datasource.local.store.SurveyBase
import org.openforis.collectone.v2.app.data.datasource.local.store.SyncTokens
import org.openforis.collectone.v2.app.domain.model.SyncStatus

/**
 * Sync state survives restarts: offline edits are rebuilt on top of the stored bases, and state
 * saved by older versions in a sync state file moves into the local database.
 */
class SyncStatePersistenceTest {
  private val directory = Files.createTempDirectory("collect-one-sync-state").toFile()
  private val databaseFile = File(directory, "collect_one.db")
  private val legacyFile = File(directory, "sync_state.binpb")
  private val databases = mutableListOf<SqlDelightSyncStateStore>()

  @AfterTest
  fun deleteFiles() {
    runBlocking { databases.forEach { it.close() } }
    directory.deleteRecursively()
  }

  /** A fresh app launch: an empty working copy over the database file. */
  private fun launch(): PersistentLocalStore {
    val database = SqlDelightSyncStateStore(JdbcSqlDriverFactory(databaseFile))
    databases += database
    return PersistentLocalStore(InMemoryLocalStore(), database)
  }

  @Test
  fun storedStateIsRestored_andPendingEditsAreReappliedToTheLocalCopy() {
    val before = launch()
    runBlocking {
      SyncStatePersistence(before).start()
      before.transaction { recordOfflineEdit() }
    }
    val expectedOutbox = runBlocking { before.transaction { outbox() } }
    val expectedState = runBlocking { before.transaction { syncState() } }

    val after = launch()
    assertTrue(runBlocking { SyncStatePersistence(after).restore() })

    runBlocking {
      after.transaction {
        assertEquals(expectedOutbox, outbox())
        assertEquals(expectedState, syncState())
        assertEquals("Server", entityBase("s1", "e1")?.label)
        assertEquals("Offline", entity("s1", "e1")?.label)
        assertEquals(listOf("s1"), surveys().map { it.id })
        assertEquals("s1", preferences().activeSurveyId)
      }
    }
  }

  @Test
  fun editsAfterARestore_areStoredToo() {
    val first = launch()
    runBlocking {
      SyncStatePersistence(first).start()
      first.transaction { recordOfflineEdit() }
    }
    val second = launch()
    runBlocking {
      val persistence = SyncStatePersistence(second)
      persistence.restore()
      persistence.start()
      second.transaction { putEntityBase("s1", "e1", null) }
    }

    val third = launch()
    runBlocking { SyncStatePersistence(third).restore() }

    runBlocking { third.transaction { assertNull(entityBase("s1", "e1")) } }
  }

  @Test
  fun stateStoredForAnAccount_isRestoredForThatAccountOnly() {
    val first = launch()
    runBlocking {
      SyncStatePersistence(first).start(account = "a@example.org")
      first.transaction { recordOfflineEdit() }
    }

    assertFalse(runBlocking { SyncStatePersistence(launch()).restore(account = "b@example.org") })
    assertFalse(runBlocking { SyncStatePersistence(launch()).restore() })
    assertTrue(runBlocking { SyncStatePersistence(launch()).restore(account = "a@example.org") })
  }

  @Test
  fun startingForAnotherAccount_replacesTheStoredState() {
    val first = launch()
    runBlocking {
      SyncStatePersistence(first).start(account = "a@example.org")
      first.transaction { recordOfflineEdit() }
    }

    val second = launch()
    runBlocking {
      val persistence = SyncStatePersistence(second)
      persistence.restore(account = "b@example.org")
      persistence.start(account = "b@example.org")
    }

    assertFalse(runBlocking { SyncStatePersistence(launch()).restore(account = "a@example.org") })
    assertFalse(runBlocking { SyncStatePersistence(launch()).restore(account = "b@example.org") })
  }

  @Test
  fun nothingStored_restoreReturnsFalse() {
    assertFalse(runBlocking { SyncStatePersistence(launch()).restore() })
  }

  @Test
  fun legacyFile_isMovedIntoTheDatabase_andDeleted() {
    val saved = InMemoryLocalStore()
    runBlocking { saved.transaction { recordOfflineEdit() } }
    val state = runBlocking { saved.transaction { ClientSyncStateCodec.capture(this) } }
    legacyFile.writeBytes(ClientSyncState.ADAPTER.encode(state))

    val store = launch()
    assertTrue(runBlocking { SyncStatePersistence(store, FileSyncStateFile(legacyFile)).restore() })

    assertFalse(legacyFile.exists())
    runBlocking { store.transaction { assertEquals("Offline", entity("s1", "e1")?.label) } }
    // The next launch restores from the database alone.
    assertTrue(
      runBlocking { SyncStatePersistence(launch(), FileSyncStateFile(legacyFile)).restore() }
    )
  }

  @Test
  fun legacyFile_doesNotOverwriteADatabaseThatAlreadyHoldsState() {
    val first = launch()
    runBlocking {
      SyncStatePersistence(first).start()
      first.transaction { recordOfflineEdit() }
    }
    legacyFile.writeBytes(ClientSyncState.ADAPTER.encode(ClientSyncState(client_id = "stale")))

    val store = launch()
    assertTrue(runBlocking { SyncStatePersistence(store, FileSyncStateFile(legacyFile)).restore() })

    assertFalse(legacyFile.exists())
    runBlocking { store.transaction { assertEquals("Offline", entity("s1", "e1")?.label) } }
  }

  @Test
  fun unreadableLegacyFile_isIgnoredAndDeleted() {
    legacyFile.writeBytes(byteArrayOf(0x7f, 0x7f, 0x7f))

    val restored = runBlocking {
      SyncStatePersistence(launch(), FileSyncStateFile(legacyFile)).restore()
    }

    assertFalse(restored)
    assertFalse(legacyFile.exists())
  }

  @Test
  fun restore_prunesOrphanedOutboxEntriesAndNormalizesStaleBaseSyncStatus() {
    val before = launch()
    runBlocking {
      SyncStatePersistence(before).start()
      before.transaction {
        putSurveyBase("s1", SurveyBase(saved = SurveyDef(survey_id = "s1", title = "Survey")))
        putEntityBase(
          "s1",
          "e1",
          EntityRecord(
            entity_id = "e1",
            survey_id = "s1",
            label = "Server",
            revision = 1,
            sync_status_key = "UPLOADING",
          ),
        )
        OutboxRecorder()
          .record(
            this,
            MutationTarget.Data("deleted-survey"),
            Rebaser.entityKey("orphan-e1"),
            MutationPayload.Data(
              DataMutation(
                entity_mutation =
                  EntityMutation(
                    entity_id = "orphan-e1",
                    upsert = EntityRecord(label = "Orphan"),
                  )
              )
            ),
          )
      }
    }

    val after = launch()
    assertTrue(runBlocking { SyncStatePersistence(after).restore() })

    runBlocking {
      after.transaction {
        assertTrue(outbox().isEmpty())
        assertEquals("SYNCED", entityBase("s1", "e1")?.sync_status_key)
        assertEquals(SyncStatus.SYNCED, entity("s1", "e1")?.syncStatus)
      }
    }
  }

  /** A synced survey with a server entity, an offline edit of it, and sync tokens. */
  private fun LocalStoreTransaction.recordOfflineEdit() {
    putSurveyBase("s1", SurveyBase(saved = SurveyDef(survey_id = "s1", title = "Survey")))
    Rebaser(this)
      .acceptData(
        "s1",
        listOf(EntityRecord(entity_id = "e1", survey_id = "s1", label = "Server", revision = 2)),
        emptyList(),
      )
    OutboxRecorder()
      .record(
        this,
        MutationTarget.Data("s1"),
        Rebaser.entityKey("e1"),
        MutationPayload.Data(
          DataMutation(
            entity_mutation =
              EntityMutation(entity_id = "e1", upsert = EntityRecord(label = "Offline"))
          )
        ),
        baseRevision = 2,
      )
    updateSyncState {
      it.copy(
        syncTokens = SyncTokens(surveys = "surveys-token", entitiesBySurvey = mapOf("s1" to "e"))
      )
    }
  }
}
