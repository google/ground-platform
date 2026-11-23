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

import co.touchlab.kermit.Severity
import com.squareup.wire.ofEpochSecond
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import openforis.collectone.v1.data.DataMutation
import openforis.collectone.v1.data.DataMutationResult
import openforis.collectone.v1.data.DataServiceServer
import openforis.collectone.v1.data.EntityMutation
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.MutateDataRequest
import openforis.collectone.v1.data.MutateDataResponse
import openforis.collectone.v1.survey.ListOrganizationsRequest
import openforis.collectone.v1.survey.ListOrganizationsResponse
import openforis.collectone.v1.survey.OrganizationServiceServer
import org.openforis.collectone.v2.app.client.grpc.CollectOneGrpcClient
import org.openforis.collectone.v2.app.client.grpc.GrpcWebException
import org.openforis.collectone.v2.app.data.datasource.local.store.InMemoryLocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationPayload
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationTarget
import org.openforis.collectone.v2.app.data.datasource.remote.GrpcSyncDataSource
import org.openforis.collectone.v2.app.data.datasource.remote.SeedUserDataService
import org.openforis.collectone.v2.app.domain.repository.ConnectivityRepository
import org.openforis.collectone.v2.app.logging.recordAppLogs
import org.openforis.collectone.v2.app.testing.FakeClock

/**
 * When the sync engine sends the outbox after connectivity changes and failures, in virtual time:
 * the server can be made unreachable, which fails both sends and pulls with `UNAVAILABLE`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncCoordinatorRetryTest {
  private val store = InMemoryLocalStore()
  private val seed = SeedUserDataService()
  private val connectivity = ConnectivityRepository(initialIsOnline = false)

  /** Whether calls reach the server; when false they fail as a network outage would. */
  private var isServerReachable = true

  /** Entity IDs of every `MutateData` call the server answered, in order. */
  private val sentEntityIds = mutableListOf<String>()

  /** Whether there is an account to sync for (see [SyncCoordinator]'s `canSync`). */
  private var isSignedIn = true

  /** Number of pulls that reached the server (or failed trying). */
  private var pullAttempts = 0

  private fun unavailable(path: String) = GrpcWebException(UNAVAILABLE, path, "unreachable")

  private val dataService =
    object : DataServiceServer by seed {
      override suspend fun MutateData(request: MutateDataRequest): MutateDataResponse {
        if (!isServerReachable) throw unavailable("/MutateData")
        sentEntityIds += request.mutations.map { it.entity_mutation!!.entity_id }
        return MutateDataResponse(
          results =
            request.mutations.map {
              DataMutationResult(
                entity =
                  EntityRecord(
                    entity_id = it.entity_mutation!!.entity_id,
                    survey_id = request.survey_id,
                    revision = 1,
                    commit_timestamp = ofEpochSecond(1_900_000_000L, 0L),
                  )
              )
            }
        )
      }
    }

  /** Every pull starts with the organizations scope, so this counts (and fails) pulls. */
  private val organizationService =
    object : OrganizationServiceServer by seed {
      override suspend fun ListOrganizations(
        request: ListOrganizationsRequest
      ): ListOrganizationsResponse {
        pullAttempts++
        if (!isServerReachable) throw unavailable("/ListOrganizations")
        return seed.ListOrganizations(request)
      }
    }

  private val client = CollectOneGrpcClient(seed, organizationService, seed, dataService)

  /** Backoff without jitter, so retry times are exact: 2, 4, 8, 16, 32, then 60 seconds. */
  private val backoff = RetryBackoff(jitterRatio = 0.0)

  /** The coordinator under test, on [testScope]'s virtual clock. */
  private fun TestScope.coordinator(): SyncCoordinator {
    val clock =
      object : Clock {
        override fun now() =
          kotlin.time.Instant.fromEpochMilliseconds(START_MILLIS + testScheduler.currentTime)
      }
    return SyncCoordinator(
      store = store,
      syncDataSource = GrpcSyncDataSource(client),
      drainer = OutboxDrainer(store, client, clock, backoff = backoff),
      connectivity = connectivity,
      scope = backgroundScope,
      clock = clock,
      retryBackoff = backoff,
      canSync = { isSignedIn },
    )
  }

  private suspend fun recordEdit(entityId: String) {
    store.transaction {
      OutboxRecorder()
        .record(
          this,
          MutationTarget.Data("s1"),
          Rebaser.entityKey(entityId),
          MutationPayload.Data(
            DataMutation(
              entity_mutation =
                EntityMutation(entity_id = entityId, upsert = EntityRecord(label = entityId))
            )
          ),
        )
    }
  }

  private suspend fun outboxSize() = store.transaction { outbox() }.size

  @Test
  fun editRecordedOffline_isSentShortlyAfterComingOnline() = runTest {
    val coordinator = coordinator()
    coordinator.start()
    recordEdit("e1")
    runCurrent()
    assertEquals(emptyList(), sentEntityIds)

    connectivity.setOnline(true)
    runCurrent()
    advanceTimeBy(SyncCoordinator.ENQUEUE_DELAY_MS + 1)

    assertEquals(listOf("e1"), sentEntityIds)
    assertEquals(0, outboxSize())
  }

  @Test
  fun requestedSyncWhileSignedOut_callsNoServer() = runTest {
    connectivity.setOnline(true)
    val coordinator = coordinator()
    coordinator.start()
    runCurrent()
    val pullsBefore = pullAttempts
    isSignedIn = false

    coordinator.requestSync()
    recordEdit("e1")
    advanceTimeBy(SyncCoordinator.ENQUEUE_DELAY_MS + 1)

    assertEquals(pullsBefore, pullAttempts)
    assertEquals(emptyList(), sentEntityIds)
    assertEquals(null, coordinator.activity.value.lastError)
  }

  @Test
  fun editRecordedOnline_isSentAfterTheEnqueueDelay() = runTest {
    connectivity.setOnline(true)
    val coordinator = coordinator()
    coordinator.start()
    runCurrent()

    recordEdit("e1")
    advanceTimeBy(SyncCoordinator.ENQUEUE_DELAY_MS - 1)
    assertEquals(emptyList(), sentEntityIds)
    advanceTimeBy(2)

    assertEquals(listOf("e1"), sentEntityIds)
  }

  @Test
  fun failedSync_isLoggedAsAWarning() = runTest {
    connectivity.setOnline(true)
    isServerReachable = false
    val coordinator = coordinator()

    val logged = recordAppLogs {
      coordinator.start()
      runCurrent()
    }

    val warning = logged.first { it.tag == "SyncCoordinator" }
    assertEquals(Severity.Warn, warning.severity)
    assertTrue(warning.message.startsWith("Sync failed: "), warning.message)
    assertTrue("gRPC status 14 for /ListOrganizations" in warning.message, warning.message)
  }

  @Test
  fun failedSend_isRetriedWhenItsBackoffExpires() = runTest {
    connectivity.setOnline(true)
    isServerReachable = false
    val coordinator = coordinator()
    coordinator.start()
    runCurrent()
    recordEdit("e1")
    advanceTimeBy(SyncCoordinator.ENQUEUE_DELAY_MS + 1)
    assertEquals(1, store.transaction { outbox() }.single().attempts)

    isServerReachable = true
    advanceTimeBy(backoff.delayMillis(1) - 10)
    assertEquals(emptyList(), sentEntityIds)
    advanceTimeBy(SyncCoordinator.ENQUEUE_DELAY_MS + 20)

    assertEquals(listOf("e1"), sentEntityIds)
    assertEquals(0, outboxSize())
  }

  @Test
  fun failedSync_isRetriedWithDoublingWaitsWhileOnline() = runTest {
    connectivity.setOnline(true)
    isServerReachable = false
    coordinator().start()
    runCurrent()
    assertEquals(1, pullAttempts)

    advanceTimeBy(1_999)
    assertEquals(1, pullAttempts, "no retry before 2 seconds")
    advanceTimeBy(2)
    assertEquals(2, pullAttempts, "first retry after 2 seconds")
    advanceTimeBy(3_998)
    assertEquals(2, pullAttempts, "no retry before 4 more seconds")
    advanceTimeBy(2)
    assertEquals(3, pullAttempts, "second retry after 4 more seconds")
  }

  @Test
  fun failingSyncs_retryAtLeastOnceAMinute() = runTest {
    connectivity.setOnline(true)
    isServerReachable = false
    coordinator().start()
    runCurrent()
    advanceTimeBy(5 * 60_000L)
    val attemptsAfterFiveMinutes = pullAttempts

    advanceTimeBy(60_001)

    assertEquals(attemptsAfterFiveMinutes + 1, pullAttempts)
  }

  @Test
  fun successfulRetry_resetsTheBackoff() = runTest {
    connectivity.setOnline(true)
    isServerReachable = false
    val coordinator = coordinator()
    coordinator.start()
    runCurrent()
    advanceTimeBy(2_001 + 4_001) // Two retries; the next would wait 8 seconds.
    isServerReachable = true
    advanceTimeBy(8_001)
    assertEquals(4, pullAttempts, "the third retry succeeds")

    // With the server unreachable again, the next failure retries after 2 seconds, not 16.
    isServerReachable = false
    coordinator.requestSync()
    runCurrent()
    assertEquals(5, pullAttempts)
    advanceTimeBy(2_001)

    assertEquals(6, pullAttempts)
  }

  @Test
  fun failedSyncs_areNotRetriedWhileOffline() = runTest {
    connectivity.setOnline(true)
    isServerReachable = false
    coordinator().start()
    runCurrent()

    connectivity.setOnline(false)
    advanceTimeBy(10 * 60_000L)

    assertEquals(1, pullAttempts)
  }

  /**
   * Lets [coordinator]'s send of an edit fail for five minutes, so the edit is backing off for the
   * full minute when the server becomes reachable again.
   */
  private suspend fun TestScope.failSendsForFiveMinutes(coordinator: SyncCoordinator) {
    connectivity.setOnline(true)
    isServerReachable = false
    coordinator.start()
    runCurrent()
    recordEdit("e1")
    advanceTimeBy(5 * 60_000L)
    val pending = store.transaction { outbox() }.single()
    assertTrue(pending.attempts >= 5, "attempts: ${pending.attempts}")
    isServerReachable = true
  }

  @Test
  fun reconnecting_sendsABackedOffEditRightAway() = runTest {
    val coordinator = coordinator()
    failSendsForFiveMinutes(coordinator)

    connectivity.setOnline(false)
    runCurrent()
    connectivity.setOnline(true)
    runCurrent()

    assertEquals(listOf("e1"), sentEntityIds)
    assertEquals(0, outboxSize())
  }

  @Test
  fun returningToTheForeground_sendsABackedOffEditRightAway() = runTest {
    val coordinator = coordinator()
    failSendsForFiveMinutes(coordinator)

    coordinator.setForeground(true)
    runCurrent()

    assertEquals(listOf("e1"), sentEntityIds)
    assertEquals(0, outboxSize())
  }

  @Test
  fun retryNow_skipsTheSyncBackoff() = runTest {
    connectivity.setOnline(true)
    isServerReachable = false
    val coordinator = coordinator()
    coordinator.start()
    runCurrent()
    advanceTimeBy(5 * 60_000L) // Retries now wait a minute.
    val attempts = pullAttempts

    coordinator.retryNow(fullSync = true)
    runCurrent()
    assertEquals(attempts + 1, pullAttempts)
    advanceTimeBy(2_001)

    assertEquals(attempts + 2, pullAttempts, "the backoff starts over at 2 seconds")
  }

  @Test
  fun debouncedEdit_stillWaitsForItsQuietPeriodAfterReconnecting() = runTest {
    connectivity.setOnline(true)
    val coordinator = coordinator()
    coordinator.start()
    runCurrent()
    store.transaction {
      OutboxRecorder(clock = FakeClock(kotlin.time.Instant.fromEpochSeconds(START_MILLIS / 1000)))
        .record(
          this,
          MutationTarget.Data("s1"),
          Rebaser.entityKey("e1"),
          MutationPayload.Data(
            DataMutation(
              entity_mutation = EntityMutation(entity_id = "e1", upsert = EntityRecord(label = "A"))
            )
          ),
          debounceMillis = 10_000L,
        )
    }

    connectivity.setOnline(false)
    connectivity.setOnline(true)
    runCurrent()
    assertEquals(emptyList(), sentEntityIds)
    advanceTimeBy(10_001)

    assertEquals(listOf("e1"), sentEntityIds)
  }

  private companion object {
    const val START_MILLIS = 1_800_000_000_000L
    const val UNAVAILABLE = 14
  }
}
