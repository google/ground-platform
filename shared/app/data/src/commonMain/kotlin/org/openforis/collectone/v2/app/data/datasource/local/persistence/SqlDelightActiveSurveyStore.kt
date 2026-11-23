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

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import org.openforis.collectone.v2.app.data.datasource.local.session.ActiveSurveyStore

/** [ActiveSurveyStore] in the local database's `active_survey` table (one row). */
class SqlDelightActiveSurveyStore(private val connection: LocalDatabaseConnection) :
  ActiveSurveyStore {
  override suspend fun load(): String? = connection.withDatabase { db ->
    db.activeSurveyQueries.select().awaitAsOneOrNull()
  }

  override suspend fun save(surveyId: String) {
    connection.withDatabase { db -> db.activeSurveyQueries.put(surveyId) }
  }

  override suspend fun clear() {
    connection.withDatabase { db -> db.activeSurveyQueries.delete() }
  }
}
