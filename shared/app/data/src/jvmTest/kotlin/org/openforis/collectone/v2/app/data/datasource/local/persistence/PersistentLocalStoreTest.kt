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

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import openforis.collectone.v1.data.DataMutation
import openforis.collectone.v1.data.EntityMutation
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.userdata.ClientSyncState
import openforis.collectone.v1.userdata.SyncTokensRecord
import org.openforis.collectone.v2.app.client.storage.JdbcSqlDriverFactory
import org.openforis.collectone.v2.app.data.datasource.local.store.InMemoryLocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationPayload
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationTarget
import org.openforis.collectone.v2.app.data.datasource.local.store.SurveyBase
import org.openforis.collectone.v2.app.data.datasource.local.store.SyncTokens
import org.openforis.collectone.v2.app.data.sync.OutboxRecorder
import org.openforis.collectone.v2.app.data.sync.Rebaser

/**
 * [PersistentLocalStore] writes the sync state through to the device, atomically with each edit.
 */
class PersistentLocalStoreTest {
  private val databaseFile = Files.createTempFile("collect-one-local-store", ".db").toFile()
  private val database = SqlDelightSyncStateStore(JdbcSqlDriverFactory(databaseFile))

  @AfterTest
  fun deleteDatabase() {
    runBlocking { database.close() }
    databaseFile.delete()
  }

  @Test
  fun closingAndReopeningTheDatabase_keepsTheOutboxBasesAndSyncState() {
    val store = writingThrough(database)
    runBlocking { store.transaction { recordOfflineEdit() } }
    val expected = runBlocking { store.transaction { ClientSyncStateCodec.capture(this) } }
    runBlocking { database.close() }

    val reopened = SqlDelightSyncStateStore(JdbcSqlDriverFactory(databaseFile))
    val stored = runBlocking { reopened.load().also { reopened.close() } }

    assertEquals(expected, stored)
    assertEquals(1, stored.outbox.size)
    assertEquals("Server", stored.entity_bases.single().label)
    assertEquals("surveys-token", stored.sync_tokens?.surveys)
  }

  @Test
  fun beforeWritingThrough_changesStayInMemory() {
    val stored = ClientSyncState(client_id = "stored", sync_tokens = SyncTokensRecord())
    runBlocking { database.replace(stored) }
    val store = PersistentLocalStore(InMemoryLocalStore(), database)

    runBlocking {
      store.clear()
      store.transaction { recordOfflineEdit() }
    }

    assertEquals(stored, runBlocking { database.load() })
  }

  @Test
  fun startWritingThrough_replacesTheStoredStateWithTheWorkingCopy() {
    runBlocking { database.replace(ClientSyncState(client_id = "stale")) }
    val store = PersistentLocalStore(InMemoryLocalStore(), database)
    runBlocking { store.transaction { recordOfflineEdit() } }

    runBlocking { store.startWritingThrough() }

    assertEquals(
      runBlocking { store.transaction { ClientSyncStateCodec.capture(this) } },
      runBlocking { database.load() },
    )
  }

  @Test
  fun failedDeviceWrite_failsTheTransaction_andLeavesTheWorkingCopyUnchanged() {
    val failing = FailingSyncStateStore()
    val store = PersistentLocalStore(InMemoryLocalStore(), failing)
    runBlocking { store.startWritingThrough() }
    failing.failApply = true

    assertFailsWith<IllegalStateException> {
      runBlocking { store.transaction { recordOfflineEdit() } }
    }

    runBlocking {
      store.transaction {
        assertTrue(outbox().isEmpty())
        assertNull(entityBase("s1", "e1"))
        assertEquals(SyncTokens(), syncState().syncTokens)
      }
    }
  }

  @Test
  fun editThatDoesNotTouchTheSyncState_writesNothingToTheDevice() {
    val recording = RecordingSyncStateStore()
    val store = PersistentLocalStore(InMemoryLocalStore(), recording)
    runBlocking { store.startWritingThrough() }

    runBlocking { store.transaction { putForms("s2", emptyList()) } }

    assertTrue(recording.changes.isEmpty())
  }

  @Test
  fun edit_writesOnlyTheRecordsItChanged_onceAtTheOuterCommit() {
    val recording = RecordingSyncStateStore()
    val store = PersistentLocalStore(InMemoryLocalStore(), recording)
    runBlocking { store.startWritingThrough() }
    val server = EntityRecord(entity_id = "e1", survey_id = "s1", label = "Server", revision = 2)

    runBlocking {
      store.transaction {
        putEntityBase("s1", "e1", server)
        store.transaction { putEntityBase("s1", "e2", null) }
      }
    }

    assertEquals(
      listOf(
        SyncStateChange(
          entityBases =
            mapOf(SurveyRecordKey("s1", "e1") to server, SurveyRecordKey("s1", "e2") to null)
        )
      ),
      recording.changes,
    )
  }

  @Test
  fun outboxChange_writesOnlyAddedAndRemovedMutations() {
    val recording = RecordingSyncStateStore()
    val store = PersistentLocalStore(InMemoryLocalStore(), recording)
    runBlocking {
      store.startWritingThrough()
      store.transaction { recordOfflineEdit() }
    }
    val first = runBlocking { store.transaction { outbox().single() } }
    recording.changes.clear()

    runBlocking { store.transaction { putOutbox(emptyList()) } }

    assertEquals(
      listOf(SyncStateChange(deletedMutationIds = setOf(first.mutationId))),
      recording.changes,
    )
  }

  @Test
  fun clearWhileWritingThrough_forgetsTheStoredState() {
    val store = writingThrough(database)
    runBlocking { store.transaction { recordOfflineEdit() } }

    runBlocking { store.clear() }

    assertEquals(ClientSyncState(), runBlocking { database.load() })
  }

  @Test
  fun deleteSurvey_forgetsItsStoredBases_butKeepsTheOutbox() {
    val store = writingThrough(database)
    runBlocking { store.transaction { recordOfflineEdit() } }

    runBlocking { store.transaction { deleteSurvey("s1") } }

    val stored = runBlocking { database.load() }
    assertTrue(stored.survey_bases.isEmpty())
    assertTrue(stored.entity_bases.isEmpty())
    assertEquals(1, stored.outbox.size)
  }

  private fun writingThrough(syncStateStore: SyncStateStore) =
    PersistentLocalStore(InMemoryLocalStore(), syncStateStore).also {
      runBlocking { it.startWritingThrough() }
    }

  /** A synced survey with a server entity, an offline edit of it, and sync tokens. */
  private fun org.openforis.collectone.v2.app.data.datasource.local.store.LocalStoreTransaction
    .recordOfflineEdit() {
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
    updateSyncState { it.copy(syncTokens = SyncTokens(surveys = "surveys-token")) }
  }

  private class RecordingSyncStateStore : SyncStateStore {
    val changes = mutableListOf<SyncStateChange>()

    override suspend fun load() = ClientSyncState()

    override suspend fun account() = ""

    override suspend fun replace(state: ClientSyncState, account: String) = Unit

    override suspend fun apply(change: SyncStateChange) {
      changes += change
    }
  }

  private class FailingSyncStateStore : SyncStateStore {
    var failApply = false

    override suspend fun load() = ClientSyncState()

    override suspend fun account() = ""

    override suspend fun replace(state: ClientSyncState, account: String) = Unit

    override suspend fun apply(change: SyncStateChange) {
      check(!failApply) { "Disk full" }
    }
  }
}
