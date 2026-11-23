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

import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.client.storage.JdbcSqlDriverFactory
import org.openforis.collectone.v2.app.data.datasource.local.store.InMemoryLocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStoreContractTest
import org.openforis.collectone.v2.app.data.datasource.local.store.runDirect

/**
 * [PersistentLocalStore] over an in-memory SQLite database (JDBC) passes every [LocalStore]
 * contract test, and after each test the database holds the same sync state as the working copy.
 */
class PersistentLocalStoreContractTest : LocalStoreContractTest() {
  private val created = mutableListOf<Pair<PersistentLocalStore, SqlDelightSyncStateStore>>()

  override fun createStore(): LocalStore {
    val database = SqlDelightSyncStateStore(JdbcSqlDriverFactory())
    val store = PersistentLocalStore(InMemoryLocalStore(), database)
    runDirect { store.startWritingThrough() }
    created += store to database
    return store
  }

  @AfterTest
  fun databaseMatchesTheWorkingCopy() {
    for ((store, database) in created) {
      val stored = runDirect { database.load() }
      runDirect {
        store.transaction {
          assertEquals(outbox().map(ClientSyncStateCodec::toRecord), stored.outbox)
          val restored = InMemoryLocalStore()
          restored.transaction { ClientSyncStateCodec.restore(this, stored) }
          assertEquals(syncState(), restored.transaction { syncState() })
          val expected = ClientSyncStateCodec.capture(this)
          for (base in expected.entity_bases) assertTrue(base in stored.entity_bases, "$base")
          for (base in expected.submission_bases) assertTrue(base in stored.submission_bases)
          for (base in expected.survey_bases) assertTrue(base in stored.survey_bases, "$base")
          for (base in expected.organization_bases) assertTrue(base in stored.organization_bases)
          for (base in expected.library_bases) assertTrue(base in stored.library_bases)
          stored.entity_bases.forEach { assertEquals(it, entityBase(it.survey_id, it.entity_id)) }
          stored.submission_bases.forEach {
            assertEquals(it, submissionBase(it.survey_id, it.submission_id))
          }
          stored.survey_bases.forEach {
            assertEquals(
              it,
              surveyBase(it.survey_id)?.let { b -> ClientSyncStateCodec.toRecord(it.survey_id, b) },
            )
          }
          stored.organization_bases.forEach {
            assertEquals(it, organizationBase(it.organization_id))
          }
          stored.library_bases.forEach { assertEquals(it, libraryBase(it.organization_id)) }
        }
      }
      runDirect { database.close() }
    }
  }
}
