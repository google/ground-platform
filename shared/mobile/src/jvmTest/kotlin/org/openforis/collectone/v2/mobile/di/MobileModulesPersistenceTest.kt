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
package org.openforis.collectone.v2.mobile.di

import kotlin.test.Test
import kotlin.test.assertIs
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.openforis.collectone.v2.app.client.storage.JdbcSqlDriverFactory
import org.openforis.collectone.v2.app.data.datasource.local.persistence.PersistentLocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStore
import org.openforis.collectone.v2.app.data.datasource.remote.SeedUserDataService
import org.openforis.collectone.v2.app.di.AppConfig
import org.openforis.collectone.v2.app.di.appModules
import org.openforis.collectone.v2.app.di.platformModule
import org.openforis.collectone.v2.app.testing.assertEveryDefinitionResolves

/**
 * The mobile app's Koin graph with the device's local database, as the Android app and the mobile
 * app in the browser configure it (here SQLite over JDBC, in memory).
 */
class MobileModulesPersistenceTest {

  @Test
  fun withLocalDatabase_everyDefinitionResolves() {
    assertEveryDefinitionResolves(persistentMobileModules())
  }

  @Test
  fun withLocalDatabase_storeWritesThroughToTheDatabase() {
    val koin = koinApplication { modules(persistentMobileModules()) }.koin

    assertIs<PersistentLocalStore>(koin.get<LocalStore>())
  }

  /** The graph of mobileModules with a local database and an in-memory service. */
  private fun persistentMobileModules(): List<Module> {
    val client = SeedUserDataService().client()
    return appModules(
      AppConfig(
        syncDownloadedSurveysOnly = true,
        userDataClient = { client },
        enableSyncEngine = true,
      ),
      platformModule(localDatabase = { JdbcSqlDriverFactory() }),
    ) + mobileViewModelModule
  }
}
