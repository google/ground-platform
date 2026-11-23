/*
 * Copyright 2026 The Collect One Authors.
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
package org.openforis.collectone.v2.app.di

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.openforis.collectone.v2.app.client.auth.AuthClient
import org.openforis.collectone.v2.app.client.auth.FakeAuthClient
import org.openforis.collectone.v2.app.client.auth.ScriptedAuthClient
import org.openforis.collectone.v2.app.data.datasource.local.store.InMemoryLocalStore
import org.openforis.collectone.v2.app.data.datasource.remote.GrpcSyncDataSource
import org.openforis.collectone.v2.app.data.datasource.remote.SeedUserDataService
import org.openforis.collectone.v2.app.data.repository.SurveyRepositoryImpl
import org.openforis.collectone.v2.app.data.sync.OutboxDrainer
import org.openforis.collectone.v2.app.data.sync.SyncCoordinator
import org.openforis.collectone.v2.app.domain.model.SaveStatus
import org.openforis.collectone.v2.app.domain.repository.ConnectivityRepository
import org.openforis.collectone.v2.app.domain.repository.SampleDataRepository

/**
 * [UserDataSyncController] with the sync engine: it starts the engine after the initial sync and
 * relays which views are on screen to it. Runs on an unconfined dispatcher against the in-memory
 * seed service, so each sync completes before the call that triggered it returns.
 */
class UserDataSyncEngineTest {
  private class CountingSampleData : SampleDataRepository {
    var seeds = 0
      private set

    override suspend fun seedIfNeeded() {
      seeds++
    }

    override suspend fun resetToSampleData() = Unit
  }

  private val store = InMemoryLocalStore()
  private val client = SeedUserDataService().client()
  private val syncDataSource = GrpcSyncDataSource(client)
  private val connectivity = ConnectivityRepository(initialIsOnline = false)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
  private val coordinator =
    SyncCoordinator(
      store = store,
      syncDataSource = syncDataSource,
      drainer = OutboxDrainer(store, client),
      connectivity = connectivity,
      scope = scope,
    )
  private val sampleData = CountingSampleData()

  @AfterTest fun tearDown() = scope.cancel()

  private fun controller(authClient: AuthClient = FakeAuthClient()) =
    UserDataSyncController(
      serviceUrl = "http://service.test",
      syncDataSource = syncDataSource,
      authClient = authClient,
      localStore = store,
      sampleDataRepository = sampleData,
      surveyRepository = SurveyRepositoryImpl(store),
      syncEngine =
        UserDataSyncController.SyncEngine(
          coordinator = coordinator,
          topicSubscriptionManager = null,
          statePersistence = { null },
        ),
      scope = scope,
    )

  @Test
  fun beforeAnySync_nothingIsSavingAndNothingWasSynced() {
    val controller = controller()

    assertEquals(SaveStatus.IDLE, controller.saveStatus.value)
    assertNull(controller.lastSyncedAt.value)
  }

  @Test
  fun online_theInitialSyncStartsTheEngine_whichSyncs() {
    connectivity.setOnline(true)

    val controller = controller().apply { start() }

    assertEquals(UserDataSyncState.Synced, controller.state.value)
    assertEquals(1, sampleData.seeds)
    assertNotNull(controller.lastSyncedAt.value)
    assertEquals(SaveStatus.IDLE, controller.saveStatus.value)
  }

  @Test
  fun offline_theEngineWaitsWithoutSyncing() {
    val controller = controller().apply { start() }

    assertTrue(controller.hasCompletedInitialSync.value)
    assertNull(controller.lastSyncedAt.value)
  }

  @Test
  fun comingOnline_afterStarting_syncs() {
    val controller = controller().apply { start() }

    connectivity.setOnline(true)

    assertNotNull(controller.lastSyncedAt.value)
  }

  @Test
  fun viewsOnScreen_areRelayedToTheEnginesPolling() {
    val controller = controller().apply { start() }

    controller.setSurveyListOpen(true)
    assertEquals(SyncCoordinator.COLLABORATIVE_POLL_MS, coordinator.pollIntervalMillis())
    controller.setSurveyListOpen(false)
    assertEquals(SyncCoordinator.IDLE_POLL_MS, coordinator.pollIntervalMillis())

    controller.setCollaborativeView(true)
    assertEquals(SyncCoordinator.COLLABORATIVE_POLL_MS, coordinator.pollIntervalMillis())
  }

  @Test
  fun retryWhileSignedOut_reportsSyncedWithoutDownloading() {
    val controller = controller(authClient = ScriptedAuthClient(serviceSession = null))
    controller.start()

    controller.retry()

    assertEquals(UserDataSyncState.Synced, controller.state.value)
    assertTrue(controller.hasLoadedLocalData.value)
    assertEquals(0, sampleData.seeds)
  }
}
