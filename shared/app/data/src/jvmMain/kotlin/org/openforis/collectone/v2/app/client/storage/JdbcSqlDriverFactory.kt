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
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

/**
 * Opens the local database over JDBC: in memory by default (tests), or in [file] on desktop builds.
 */
class JdbcSqlDriverFactory private constructor(private val url: String) : SqlDriverFactory {
  constructor() : this(JdbcSqliteDriver.IN_MEMORY)

  constructor(file: File) : this("jdbc:sqlite:${file.absolutePath}")

  override fun create(schema: SqlSchema<QueryResult.AsyncValue<Unit>>): SqlDriver =
    JdbcSqliteDriver(url)
}
