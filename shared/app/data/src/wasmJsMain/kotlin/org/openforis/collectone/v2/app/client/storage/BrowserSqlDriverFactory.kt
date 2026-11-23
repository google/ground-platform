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
package org.openforis.collectone.v2.app.client.storage

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import org.w3c.dom.Worker

/**
 * Runs the local database in a web worker (`shared/app/data/web/sqlite-worker/`) with the official
 * SQLite WebAssembly build. The worker keeps the database in the Origin Private File System when
 * the browser allows it, and otherwise in IndexedDB; see
 * `docs/technical/client/local-data-store.md`.
 */
class BrowserSqlDriverFactory : SqlDriverFactory {
  override fun create(schema: SqlSchema<QueryResult.AsyncValue<Unit>>): SqlDriver =
    WebWorkerDriver(createLocalDatabaseWorker())
}

// The bundler resolves the worker script from the `collect-one-sqlite-worker` npm package.
private fun createLocalDatabaseWorker(): Worker =
  js(
    """new Worker(new URL("collect-one-sqlite-worker/sqlite.worker.js", import.meta.url), { type: "module" })"""
  )
