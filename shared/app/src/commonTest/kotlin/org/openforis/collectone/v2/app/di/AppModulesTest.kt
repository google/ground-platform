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
package org.openforis.collectone.v2.app.di

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.isActive
import org.koin.dsl.koinApplication
import org.openforis.collectone.v2.app.data.datasource.local.store.InMemoryLocalStore
import org.openforis.collectone.v2.app.data.datasource.remote.SeedUserDataService
import org.openforis.collectone.v2.app.testing.assertEveryDefinitionResolves

/** The shared Koin graph ([appModules]) can create every definition in each host configuration. */
class AppModulesTest {

  @Test
  fun withoutUserDataService_everyDefinitionResolves() {
    assertEveryDefinitionResolves(appModules(AppConfig()))
  }

  @Test
  fun webConsole_everyDefinitionResolves() {
    assertEveryDefinitionResolves(appModules(inMemoryServiceConfig()))
  }

  @Test
  fun mobileAppWithSyncEngine_everyDefinitionResolves() {
    assertEveryDefinitionResolves(
      appModules(inMemoryServiceConfig(syncDownloadedSurveysOnly = true, enableSyncEngine = true))
    )
  }

  @Test
  fun withoutUserDataService_hasNoSyncEngine() {
    val holder = createAppDataHolder()

    assertNull(holder.grpcClient)
    assertNull(holder.syncCoordinator)
    assertSame(holder.localStore, holder.editStore)
  }

  @Test
  fun withSyncEngine_recordsEditsAndSubscribesToDownloadedSurveys() {
    val holder =
      createAppDataHolder(
        inMemoryServiceConfig(syncDownloadedSurveysOnly = true, enableSyncEngine = true)
      )

    assertNotNull(holder.syncCoordinator)
    assertNotNull(holder.topicSubscriptionManager)
    assertNotNull(holder.outboxSyncRepository)
    assertNotSame(holder.localStore, holder.editStore)
  }

  @Test
  fun withoutLocalDatabase_keepsTheStoreInMemory() {
    // Graphs with the device's local database: AppModulesPersistenceTest (jvmTest).
    assertIs<InMemoryLocalStore>(createAppDataHolder(inMemoryServiceConfig()).localStore)
  }

  @Test
  fun appDataHolder_usesTheGivenLocalStore() {
    val store = InMemoryLocalStore()

    assertSame(store, AppDataHolder(localStore = store).localStore)
  }

  @Test
  fun stoppingTheGraph_cancelsTheAppScope() {
    val application = koinApplication {
      modules(appModules(inMemoryServiceConfig(enableSyncEngine = true)))
    }
    val holder = application.koin.get<AppDataHolder>()
    assertTrue(holder.scope.isActive)

    application.close()

    assertFalse(holder.scope.isActive)
  }

  private fun inMemoryServiceConfig(
    syncDownloadedSurveysOnly: Boolean = false,
    enableSyncEngine: Boolean = false,
  ): AppConfig {
    val client = SeedUserDataService().client()
    return AppConfig(
      syncDownloadedSurveysOnly = syncDownloadedSurveysOnly,
      userDataClient = { client },
      enableSyncEngine = enableSyncEngine,
    )
  }
}
