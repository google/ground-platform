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

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.openforis.collectone.v2.app.client.storage.SqlDriverFactory
import org.openforis.collectone.v2.app.data.datasource.local.db.LocalDatabase

/**
 * The app's one connection to the local database, shared by the stores that keep their data in it.
 * The database is opened, and its schema created or migrated, on first use. Calls run one at a
 * time.
 */
class LocalDatabaseConnection(private val driverFactory: SqlDriverFactory) {
  private val lock = Mutex()
  private var driver: SqlDriver? = null
  private var database: LocalDatabase? = null

  /** Runs [block] with the database, opening it first if needed. */
  suspend fun <R> withDatabase(block: suspend (LocalDatabase) -> R): R = lock.withLock {
    val db =
      database
        ?: driverFactory.openLocalDatabase().let { (openedDriver, openedDatabase) ->
          driver = openedDriver
          database = openedDatabase
          openedDatabase
        }
    block(db)
  }

  /** Closes the database once calls in progress finish; the next call opens it again. */
  suspend fun close() {
    lock.withLock { closeNow() }
  }

  /**
   * Closes the database right away, without waiting for calls in progress, for when the app's
   * dependency graph stops (its coroutines are cancelled first). The next call opens it again.
   */
  fun closeNow() {
    driver?.close()
    driver = null
    database = null
  }
}
