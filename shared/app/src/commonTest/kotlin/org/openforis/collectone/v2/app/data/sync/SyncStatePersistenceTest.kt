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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import openforis.collectone.v1.data.DataMutation
import openforis.collectone.v1.data.EntityMutation
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.survey.SurveyDef
import org.openforis.collectone.v2.app.client.storage.SyncStateFile
import org.openforis.collectone.v2.app.data.datasource.local.store.InMemoryLocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationPayload
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationTarget
import org.openforis.collectone.v2.app.data.datasource.local.store.SurveyBase
import org.openforis.collectone.v2.app.data.datasource.local.store.SyncTokens
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow

/** Sync state survives restarts: offline edits are rebuilt on top of the saved bases. */
class SyncStatePersistenceTest {
  private class MemoryFile : SyncStateFile {
    var bytes: ByteArray? = null

    override fun read() = bytes

    override fun write(bytes: ByteArray) {
      this.bytes = bytes
    }
  }

  private val file = MemoryFile()
  private val scope = CoroutineScope(Dispatchers.Unconfined)

  @Test
  fun savedStateIsRestored_andPendingEditsAreReappliedToTheLocalCopy() {
    val before = InMemoryLocalStore()
    val syncTokens = SyncTokens(surveys = "surveys-token", entitiesBySurvey = mapOf("s1" to "e"))
    runNow {
      before.transaction {
        putSurveyBase("s1", SurveyBase(saved = SurveyDef(survey_id = "s1", title = "Survey")))
        Rebaser(this)
          .acceptData(
            "s1",
            listOf(
              EntityRecord(entity_id = "e1", survey_id = "s1", label = "Server", revision = 2)
            ),
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
        updateSyncState { it.copy(syncTokens = syncTokens) }
      }
    }
    runNow { SyncStatePersistence(before, file, scope).saveNow() }

    val expectedOutbox = runNow { before.transaction { outbox() } }
    val expectedState = runNow { before.transaction { syncState() } }
    val after = InMemoryLocalStore()
    assertTrue(runNow { SyncStatePersistence(after, file, scope).restore() })

    runNow {
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
  fun nothingSaved_restoreReturnsFalse() {
    assertFalse(runNow { SyncStatePersistence(InMemoryLocalStore(), file, scope).restore() })
  }

  @Test
  fun unreadableState_isIgnored() {
    file.bytes = byteArrayOf(0x7f, 0x7f, 0x7f)

    assertFalse(runNow { SyncStatePersistence(InMemoryLocalStore(), file, scope).restore() })
  }
}
