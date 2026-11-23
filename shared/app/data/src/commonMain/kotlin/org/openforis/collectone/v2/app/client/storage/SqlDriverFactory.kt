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

/** File name of the app's local SQLite database on platforms that keep it in a file. */
const val LOCAL_DATABASE_NAME = "collect_one.db"

/**
 * Opens the platform's SQLite connection for the app's local database
 * (`docs/technical/client/local-data-store.md`). Each platform has one implementation: Android, JVM
 * (desktop and tests), the browser (a web worker running SQLite), and iOS.
 */
fun interface SqlDriverFactory {
  /**
   * Opens a driver for the database described by [schema]. Drivers that track the schema version
   * themselves (Android, iOS) create or migrate the schema while opening; the others leave the
   * database as it is and the caller brings it up to date.
   */
  fun create(schema: SqlSchema<QueryResult.AsyncValue<Unit>>): SqlDriver
}
