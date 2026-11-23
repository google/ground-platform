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

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import org.openforis.collectone.v2.app.client.storage.SqlDriverFactory
import org.openforis.collectone.v2.app.data.datasource.local.db.LocalDatabase

/**
 * Opens the local database and brings its schema up to date.
 *
 * The schema version is SQLite's `user_version`. A new database gets the current schema; an older
 * one runs the SQLDelight migrations (the `.sqm` files next to the schema) from its version to the
 * current one. Drivers that track the version themselves (Android, iOS) have already done this
 * while opening, so the version matches and nothing runs.
 */
internal suspend fun SqlDriverFactory.openLocalDatabase(): Pair<SqlDriver, LocalDatabase> {
  val schema = LocalDatabase.Schema
  val driver = create(schema)
  val database = LocalDatabase(driver)
  val version = driver.userVersion()
  check(version <= schema.version) {
    "The local database has schema version $version, newer than this app's ${schema.version}"
  }
  if (version < schema.version) {
    database.transaction {
      if (version == 0L) {
        schema.create(driver).await()
      } else {
        schema.migrate(driver, version, schema.version).await()
      }
      driver.execute(null, "PRAGMA user_version = ${schema.version}", 0).await()
    }
  }
  return driver to database
}

private suspend fun SqlDriver.userVersion(): Long =
  executeQuery(
      identifier = null,
      sql = "PRAGMA user_version",
      mapper = { cursor ->
        QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null)
      },
      parameters = 0,
    )
    .await() ?: 0L
