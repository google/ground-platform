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
package org.openforis.collectone.v2.app.data.sync

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import openforis.collectone.v1.data.DataServiceServer
import openforis.collectone.v1.data.ListEntitiesRequest
import org.openforis.collectone.v2.app.client.grpc.CollectOneGrpcClient
import org.openforis.collectone.v2.app.data.datasource.local.store.InMemoryLocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.datasource.remote.GrpcSyncDataSource
import org.openforis.collectone.v2.app.data.datasource.remote.SeedUserDataService
import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.domain.repository.ConnectivityRepository

/** Push tickle payloads, the scoped syncs they run, and when the last sync succeeded. */
class SyncTickleTest {

  // --- parseSyncTickle ---

  @Test
  fun parseSyncTickle_readsSurveyScopeAndRecordVersion() {
    val tickle =
      parseSyncTickle(mapOf("survey_id" to "s1", "scope" to "entities", "record_version" to "42"))

    assertEquals(SyncTickle("s1", SurveySyncScope.ENTITIES, minRecordVersion = 42L), tickle)
  }

  @Test
  fun parseSyncTickle_trimsValues() {
    val tickle =
      parseSyncTickle(mapOf("survey_id" to " s1 ", "scope" to " DEF ", "record_version" to " 7 "))

    assertEquals(SyncTickle("s1", SurveySyncScope.DEFINITION, minRecordVersion = 7L), tickle)
  }

  @Test
  fun parseSyncTickle_withoutRecordVersion_hasNoMinimumVersion() {
    val tickle = parseSyncTickle(mapOf("survey_id" to "s1", "scope" to "def"))

    assertNull(assertNotNull(tickle).minRecordVersion)
  }

  @Test
  fun parseSyncTickle_ignoresMalformedRecordVersion() {
    val tickle =
      parseSyncTickle(mapOf("survey_id" to "s1", "scope" to "def", "record_version" to "latest"))

    assertNull(assertNotNull(tickle).minRecordVersion)
  }

  @Test
  fun parseSyncTickle_withoutSurveyId_isNull() {
    assertNull(parseSyncTickle(mapOf("survey_id" to " ", "scope" to "def")))
    assertNull(parseSyncTickle(mapOf("scope" to "def")))
  }

  @Test
  fun parseSyncTickle_withUnknownScope_isNull() {
    assertNull(parseSyncTickle(mapOf("survey_id" to "s1", "scope" to "submissions")))
    assertNull(parseSyncTickle(mapOf("survey_id" to "s1")))
  }

  // --- syncTickleNow and SyncActivity.lastSyncedAt ---

  private val store = InMemoryLocalStore()
  private val seed = SeedUserDataService()
  private var isServerDown = false
  private val dataService =
    object : DataServiceServer by seed {
      override suspend fun ListEntities(request: ListEntitiesRequest) =
        if (isServerDown) error("Server unavailable") else seed.ListEntities(request)
    }
  private val client = CollectOneGrpcClient(seed, seed, seed, dataService)
  private val syncDataSource = GrpcSyncDataSource(client)
  private val connectivity = ConnectivityRepository(initialIsOnline = true)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
  private var nowEpochSeconds = 1_900_000_000L
  private val coordinator =
    SyncCoordinator(
      store = store,
      syncDataSource = syncDataSource,
      drainer = OutboxDrainer(store, client),
      connectivity = connectivity,
      scope = scope,
      clock =
        object : Clock {
          override fun now() = kotlin.time.Instant.fromEpochSeconds(nowEpochSeconds)
        },
    )

  @AfterTest
  fun tearDown() {
    scope.cancel()
  }

  private fun seededSurveyId(): String {
    runNow { syncDataSource.seedStoreFromGrpc(store, defaultActiveSurveyId = "", seedVersion = 1) }
    return runNow { store.transaction { surveys().first().id } }
  }

  @Test
  fun lastSyncedAt_isNullBeforeAnySync() {
    assertNull(coordinator.activity.value.lastSyncedAt)
  }

  @Test
  fun syncTickleNow_succeeds_andRecordsWhenItSynced() {
    val tickle = SyncTickle(seededSurveyId(), SurveySyncScope.ENTITIES)

    val succeeded = runNow { coordinator.syncTickleNow(tickle) }

    assertTrue(succeeded)
    assertEquals(Instant.fromEpochSeconds(nowEpochSeconds), coordinator.activity.value.lastSyncedAt)
  }

  @Test
  fun syncTickleNow_whenThePullFails_reportsFailure() {
    val tickle = SyncTickle(seededSurveyId(), SurveySyncScope.ENTITIES)
    isServerDown = true

    val succeeded = runNow { coordinator.syncTickleNow(tickle) }

    assertFalse(succeeded)
    val error = assertIs<DomainError.Unknown>(coordinator.activity.value.lastError)
    assertEquals("Server unavailable", error.cause?.message)
  }

  @Test
  fun failedSync_keepsTheTimeOfTheLastSuccessfulOne() {
    val tickle = SyncTickle(seededSurveyId(), SurveySyncScope.ENTITIES)
    runNow { coordinator.syncTickleNow(tickle) }
    val lastSuccess = coordinator.activity.value.lastSyncedAt
    nowEpochSeconds += 60
    isServerDown = true

    runNow { coordinator.syncTickleNow(tickle) }

    assertEquals(lastSuccess, coordinator.activity.value.lastSyncedAt)
  }
}
