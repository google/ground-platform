/*
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.groundplatform.v2.app.data.seed

import org.groundplatform.v2.app.data.datasource.local.store.LocalStore
import org.groundplatform.v2.app.data.datasource.remote.GrpcSyncDataSource

/**
 * Fills a [LocalStore] with the initial state of the user data service via [GrpcSyncDataSource].
 *
 * When no service is configured ([syncDataSource] is null), the store is left as is: the app starts
 * empty, and tests seed stores directly from the fixtures in `app-testing`.
 */
class SampleDataSeeder(
  private val store: LocalStore,
  private val syncDataSource: GrpcSyncDataSource?,
) {
  /** Pulls the initial state unless the store already holds the current seed version. */
  suspend fun seedIfNeeded() {
    val syncDataSource = syncDataSource ?: return
    val currentVersion = store.transaction { preferences().seedVersion }
    if (currentVersion != SEED_VERSION) {
      syncDataSource.seedStoreFromGrpc(
        store = store,
        defaultActiveSurveyId = DEFAULT_ACTIVE_SURVEY_ID,
        seedVersion = SEED_VERSION,
      )
    }
  }

  /** Deletes everything in the store, then pulls the initial state again if a service is set. */
  suspend fun reset() {
    store.clear()
    val syncDataSource = syncDataSource ?: return
    syncDataSource.resetSyncTokens()
    syncDataSource.seedStoreFromGrpc(
      store = store,
      defaultActiveSurveyId = DEFAULT_ACTIVE_SURVEY_ID,
      seedVersion = SEED_VERSION,
    )
  }

  companion object {
    /** Bump when the seed data changes shape, so existing stores are reseeded. */
    const val SEED_VERSION: Int = 16

    /** Survey opened by default on first launch. */
    const val DEFAULT_ACTIVE_SURVEY_ID: String = "survey-kenya-coffee"
  }
}
