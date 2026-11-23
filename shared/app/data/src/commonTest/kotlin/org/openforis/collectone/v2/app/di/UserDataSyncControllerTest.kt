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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.openforis.collectone.v2.app.client.auth.AuthClient
import org.openforis.collectone.v2.app.client.auth.FakeAuthClient
import org.openforis.collectone.v2.app.client.auth.ScriptedAuthClient
import org.openforis.collectone.v2.app.client.auth.ScriptedAuthClient.Companion.MAYA
import org.openforis.collectone.v2.app.client.grpc.CollectOneGrpcClient
import org.openforis.collectone.v2.app.data.datasource.local.store.InMemoryLocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.datasource.remote.GrpcSyncDataSource
import org.openforis.collectone.v2.app.data.datasource.remote.SeedUserDataService
import org.openforis.collectone.v2.app.data.repository.SurveyRepositoryImpl
import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.domain.repository.SampleDataRepository

/** [UserDataSyncController] runs and retries the initial sync. */
class UserDataSyncControllerTest {
  /** Fails the first [failures] downloads, as if the service were unreachable. */
  private class FlakySampleData(private val failures: Int) : SampleDataRepository {
    var attempts = 0
      private set

    override suspend fun seedIfNeeded() {
      attempts++
      if (attempts <= failures) error("connection refused")
    }

    override suspend fun resetToSampleData() = Unit
  }

  private val store = InMemoryLocalStore()
  private val waits = mutableListOf<Long>()
  private val service = SeedUserDataService()
  private val syncDataSource =
    GrpcSyncDataSource(
      CollectOneGrpcClient(
        surveyService = service,
        organizationService = service,
        libraryService = service,
        dataService = service,
      )
    )

  private fun controller(
    sampleData: SampleDataRepository,
    syncDataSource: GrpcSyncDataSource? = this.syncDataSource,
    authClient: AuthClient = FakeAuthClient(),
  ) =
    UserDataSyncController(
      serviceUrl = SERVICE_URL,
      syncDataSource = syncDataSource,
      authClient = authClient,
      localStore = store,
      sampleDataRepository = sampleData,
      surveyRepository = SurveyRepositoryImpl(store),
      syncEngine = null,
      scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
      retryDelay = { waits += it },
    )

  @Test
  fun withoutAService_seedsTheSampleDataOnce() {
    val sampleData = FlakySampleData(failures = 0)

    val controller = controller(sampleData, syncDataSource = null).apply { start() }

    assertEquals(UserDataSyncState.NotConfigured, controller.state.value)
    assertEquals(1, sampleData.attempts)
  }

  @Test
  fun successfulSync_isReported() {
    val controller = controller(FlakySampleData(failures = 0)).apply { start() }

    assertEquals(UserDataSyncState.Synced, controller.state.value)
    assertTrue(controller.hasCompletedInitialSync.value)
  }

  @Test
  fun failedSync_isReportedWithTheServiceAndTheError() {
    val controller = controller(FlakySampleData(failures = Int.MAX_VALUE)).apply { start() }

    val failed = assertIs<UserDataSyncState.Failed>(controller.state.value)
    assertEquals(SERVICE_URL, failed.serviceUrl)
    val error = assertIs<DomainError.Unknown>(failed.error)
    assertEquals("connection refused", error.cause?.message)
  }

  @Test
  fun failedSync_stillCompletesTheInitialSync() {
    val controller = controller(FlakySampleData(failures = Int.MAX_VALUE)).apply { start() }

    assertTrue(controller.hasCompletedInitialSync.value)
  }

  @Test
  fun failedSync_retriesWithDoublingWaitsUpToTheCap() {
    controller(FlakySampleData(failures = Int.MAX_VALUE)).start()

    assertEquals(listOf(2_000L, 4_000L, 8_000L, 16_000L), waits)
  }

  @Test
  fun failedSync_givesUpAfterTheLastRetry() {
    val sampleData = FlakySampleData(failures = Int.MAX_VALUE)

    val controller = controller(sampleData).apply { start() }

    assertEquals(1 + UserDataSyncController.MAX_SEED_RETRY_ATTEMPTS, sampleData.attempts)
    assertIs<UserDataSyncState.Failed>(controller.state.value)
  }

  @Test
  fun failedSync_recoversOnAnAutomaticRetry() {
    val controller = controller(FlakySampleData(failures = 2)).apply { start() }

    assertEquals(UserDataSyncState.Synced, controller.state.value)
    assertEquals(listOf(2_000L, 4_000L), waits)
  }

  @Test
  fun manualRetry_afterTheAutomaticRetriesGaveUp_syncs() {
    val sampleData = FlakySampleData(failures = 1 + UserDataSyncController.MAX_SEED_RETRY_ATTEMPTS)
    val controller = controller(sampleData).apply { start() }

    controller.retry()

    assertEquals(UserDataSyncState.Synced, controller.state.value)
  }

  @Test
  fun sameAccountWithARefreshedProfile_keepsItsData() {
    // Restored offline, then confirmed by the auth service with a newer photo.
    val client = ScriptedAuthClient(serviceSession = MAYA, isServiceReachable = false)
    runNow { client.restoreSession(MAYA.copy(photoUrl = null)) }
    val sampleData = FlakySampleData(failures = 0)
    controller(sampleData, authClient = client).start()
    assertEquals(1, sampleData.attempts)

    client.reconnect()

    assertEquals(1, sampleData.attempts, "the store wasn't cleared and synced again")
  }

  @Test
  fun anotherAccount_syncsItsOwnData() {
    val client = ScriptedAuthClient(serviceSession = MAYA)
    val sampleData = FlakySampleData(failures = 0)
    controller(sampleData, authClient = client).start()

    client.reconnect(MAYA.copy(email = "ima.user@example.org"))

    assertEquals(2, sampleData.attempts)
  }

  companion object {
    private const val SERVICE_URL = "http://service.test"
  }
}
