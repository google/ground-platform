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

import com.squareup.wire.ofEpochSecond
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
import org.openforis.collectone.v2.app.client.auth.FakeAuthClient
import org.openforis.collectone.v2.app.client.grpc.CollectOneGrpcClient
import org.openforis.collectone.v2.app.client.grpc.GrpcWebException
import org.openforis.collectone.v2.app.data.datasource.local.store.InMemoryLocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationPayload
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationTarget
import org.openforis.collectone.v2.app.data.datasource.local.store.OutboxState
import org.openforis.collectone.v2.app.data.datasource.local.store.PendingMutation
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.datasource.remote.GrpcSyncDataSource
import org.openforis.collectone.v2.app.data.datasource.remote.SeedUserDataService
import org.openforis.collectone.v2.app.data.repository.SurveyRepositoryImpl
import org.openforis.collectone.v2.app.data.sync.OutboxDrainer
import org.openforis.collectone.v2.app.data.sync.OutboxRecorder
import org.openforis.collectone.v2.app.data.sync.Rebaser
import org.openforis.collectone.v2.app.data.sync.SyncActivity
import org.openforis.collectone.v2.app.data.sync.SyncCoordinator
import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.domain.model.SaveStatus
import org.openforis.collectone.v2.app.domain.repository.ConnectivityRepository
import org.openforis.collectone.v2.app.domain.repository.SampleDataRepository

/**
 * [UserDataSyncController.saveStatus], behind the web console header's "Saving…" indicator: it
 * reports saving only while edits are sent, never during polls and pulls that send nothing (the
 * stuck "Saving" spinner), and failed while edits wait to retry. The syncs run on an unconfined
 * dispatcher against the in-memory seed service, with sends and pulls held open where a test needs
 * to look inside them.
 */
class UserDataSaveStatusTest {
  private class TestClock(var current: Instant) : Clock {
    override fun now(): Instant = current
  }

  private val store = InMemoryLocalStore()
  private val seed = SeedUserDataService()
  private val clock = TestClock(Clock.System.now())

  /** Holds `MutateData` open until completed, when set. */
  private var sendGate: CompletableDeferred<Unit>? = null

  /**
   * What `MutateData` fails with, when set; cleared by [failNextSendOnlyWith] after one failure.
   */
  private var sendFailure: Exception? = null
  private var failOnce = false
  private var sends = 0

  /** Holds the pull (`ListOrganizations`) open until completed, when set. */
  private var pullGate: CompletableDeferred<Unit>? = null
  private var pullStarted = false

  private val dataService =
    object : DataServiceServer by seed {
      override suspend fun MutateData(request: MutateDataRequest): MutateDataResponse {
        sends++
        sendGate?.await()
        sendFailure?.let {
          if (failOnce) sendFailure = null
          throw it
        }
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

  private val organizationService =
    object : OrganizationServiceServer by seed {
      override suspend fun ListOrganizations(
        request: ListOrganizationsRequest
      ): ListOrganizationsResponse {
        pullStarted = true
        pullGate?.await()
        return seed.ListOrganizations(request)
      }
    }

  private val client = CollectOneGrpcClient(seed, organizationService, seed, dataService)
  private val connectivity = ConnectivityRepository(initialIsOnline = true)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
  private val coordinator =
    SyncCoordinator(
      store = store,
      syncDataSource = GrpcSyncDataSource(client),
      drainer = OutboxDrainer(store, client, clock = clock),
      connectivity = connectivity,
      scope = scope,
      clock = clock,
    )
  private val controller =
    UserDataSyncController(
      serviceUrl = "http://service.test",
      syncDataSource = GrpcSyncDataSource(client),
      authClient = FakeAuthClient(),
      localStore = store,
      sampleDataRepository =
        object : SampleDataRepository {
          override suspend fun seedIfNeeded() = Unit

          override suspend fun resetToSampleData() = Unit
        },
      surveyRepository = SurveyRepositoryImpl(store),
      syncEngine =
        UserDataSyncController.SyncEngine(
          coordinator = coordinator,
          topicSubscriptionManager = null,
          statePersistence = { null },
        ),
      scope = scope,
    )

  /** Every status [UserDataSyncController.saveStatus] reported, starting with the current one. */
  private val seen = mutableListOf<SaveStatus>()

  init {
    scope.launch { controller.saveStatus.collect { seen += it } }
  }

  @AfterTest fun tearDown() = scope.cancel()

  private val status: SaveStatus
    get() = controller.saveStatus.value

  /** Records an edit of map feature [entityId] in survey `s1`, waiting [debounceMillis] to send. */
  private fun recordEdit(entityId: String, debounceMillis: Long = 0L) = runNow {
    store.transaction {
      OutboxRecorder(clock = clock)
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
          debounceMillis = debounceMillis,
        )
    }
  }

  private fun outbox(): List<PendingMutation> = runNow { store.transaction { outbox() } }

  /** Runs a full sync (as a poll, a focus change, or the tab returning does) to the end. */
  private fun sync() = runNow { coordinator.syncNow() }

  /** Starts a full sync in the background; it runs until it waits on a gate. */
  private fun startSync() = scope.launch { coordinator.syncNow() }

  @Test
  fun beforeAnySync_isIdle() {
    assertEquals(SaveStatus.IDLE, status)
  }

  @Test
  fun syncThatSendsNothing_isNeverSaving() {
    sync()

    assertTrue(pullStarted)
    assertEquals(listOf(SaveStatus.IDLE), seen)
  }

  @Test
  fun sendingAnEdit_isSavingUntilItIsSent_thenIdle() {
    recordEdit("e1")
    sendGate = CompletableDeferred()

    val sync = startSync()
    assertEquals(SaveStatus.SAVING, status)

    sendGate!!.complete(Unit)

    assertTrue(sync.isCompleted)
    assertEquals(SaveStatus.IDLE, status)
    assertEquals(emptyList(), outbox())
    assertEquals(listOf(SaveStatus.IDLE, SaveStatus.SAVING, SaveStatus.IDLE), seen)
  }

  @Test
  fun pullAfterTheEditIsSent_isNotSaving() {
    recordEdit("e1")
    pullGate = CompletableDeferred()

    val sync = startSync()

    // The edit is sent and the sync is still pulling.
    assertEquals(1, sends)
    assertTrue(pullStarted)
    assertTrue(coordinator.activity.value.isSyncing)
    assertEquals(SaveStatus.IDLE, status)

    pullGate!!.complete(Unit)
    assertTrue(sync.isCompleted)
  }

  @Test
  fun pollWhileAnEditIsDebounced_isNotSaving() {
    recordEdit("e1", debounceMillis = 60_000L)
    pullGate = CompletableDeferred()

    startSync()

    // The poll is pulling while the edit waits for its debounce: nothing is being saved.
    assertTrue(pullStarted)
    assertEquals(0, sends)
    assertEquals(OutboxState.QUEUED, outbox().single().state)
    assertEquals(SaveStatus.IDLE, status)

    pullGate!!.complete(Unit)
    assertFalse(SaveStatus.SAVING in seen)
  }

  @Test
  fun failedSend_isFailedUntilTheRetrySucceeds() {
    recordEdit("e1")
    sendFailure = GrpcWebException(14, "/MutateData", "Unavailable")
    failOnce = true
    sendGate = CompletableDeferred()
    startSync()
    assertEquals(SaveStatus.SAVING, status)

    sendGate!!.complete(Unit)

    assertEquals(OutboxState.RETRYING, outbox().single().state)
    assertEquals(SaveStatus.FAILED, status)

    // The backoff expires and the retry goes through.
    clock.current += 120.seconds
    sendGate = CompletableDeferred()
    startSync()
    assertEquals(SaveStatus.SAVING, status)

    sendGate!!.complete(Unit)

    assertEquals(emptyList(), outbox())
    assertEquals(SaveStatus.IDLE, status)
    assertEquals(
      listOf(
        SaveStatus.IDLE,
        SaveStatus.SAVING,
        SaveStatus.FAILED,
        SaveStatus.SAVING,
        SaveStatus.IDLE,
      ),
      seen,
    )
  }

  @Test
  fun pollWhileEditsWaitBehindABackingOffSend_staysFailedWithoutSaving() {
    recordEdit("e1")
    sendFailure = GrpcWebException(14, "/MutateData", "Unavailable")
    sync()
    // A later edit of the same survey queues behind the send that is backing off.
    recordEdit("e2")
    assertEquals(
      listOf(OutboxState.RETRYING, OutboxState.QUEUED),
      outbox().sortedBy { it.clientSeq }.map { it.state },
    )
    val sendsBefore = sends
    seen.clear()
    pullGate = CompletableDeferred()

    // Polls before the backoff expires send nothing however long they pull.
    startSync()
    assertTrue(pullStarted)
    assertEquals(SaveStatus.FAILED, status)
    pullGate!!.complete(Unit)
    sync()

    assertEquals(sendsBefore, sends)
    assertEquals(SaveStatus.FAILED, status)
    assertEquals(emptyList(), seen, "the status never left FAILED")
  }

  @Test
  fun rejectedSend_isNotFailed() {
    recordEdit("e1")
    sendFailure = GrpcWebException(3, "/MutateData", "Invalid")
    sendGate = CompletableDeferred()
    startSync()
    assertEquals(SaveStatus.SAVING, status)

    sendGate!!.complete(Unit)

    assertEquals(OutboxState.REJECTED, outbox().single().state)
    // Rejections are reported by BackendErrorMonitor, not as a save waiting to retry.
    assertEquals(SaveStatus.IDLE, status)
    assertEquals(listOf(SaveStatus.IDLE, SaveStatus.SAVING, SaveStatus.IDLE), seen)
  }

  @Test
  fun staleBase_staysSavingThroughThePullAndTheResend() {
    recordEdit("e1")
    sendFailure = GrpcWebException(10, "/MutateData", "Aborted")
    failOnce = true
    pullGate = CompletableDeferred()

    val sync = startSync()

    // The send found a stale base: the pull it needs is part of the save.
    assertTrue(pullStarted)
    assertEquals(SaveStatus.SAVING, status)

    pullGate!!.complete(Unit)

    assertTrue(sync.isCompleted)
    assertEquals(2, sends)
    assertEquals(emptyList(), outbox())
    assertEquals(listOf(SaveStatus.IDLE, SaveStatus.SAVING, SaveStatus.IDLE), seen)
  }

  @Test
  fun syncCancelledMidSend_isNoLongerSaving() {
    recordEdit("e1")
    sendGate = CompletableDeferred()
    val sync = startSync()
    assertEquals(SaveStatus.SAVING, status)

    sync.cancel()

    assertFalse(coordinator.activity.value.isSyncing)
    assertEquals(SaveStatus.IDLE, status)
  }

  @Test
  fun saveStatusOf_mapsSendingAndRetryingMutations() {
    fun pending(state: OutboxState, lastError: DomainError? = null) =
      PendingMutation(
        mutationId = "m-$state",
        clientSeq = 1L,
        target = MutationTarget.Data("s1"),
        recordKey = Rebaser.entityKey("e1"),
        payload = MutationPayload.Data(DataMutation()),
        state = state,
        lastError = lastError,
      )
    val idle = SyncActivity()
    val syncing = SyncActivity(isSyncing = true)
    val sending = SyncActivity(isSyncing = true, isSending = true)
    val retrying = pending(OutboxState.RETRYING, DomainError.Unknown())

    assertEquals(SaveStatus.IDLE, saveStatusOf(idle, emptyList()))
    assertEquals(SaveStatus.IDLE, saveStatusOf(syncing, listOf(pending(OutboxState.QUEUED))))
    assertEquals(SaveStatus.IDLE, saveStatusOf(syncing, listOf(pending(OutboxState.IN_FLIGHT))))
    assertEquals(SaveStatus.IDLE, saveStatusOf(idle, listOf(pending(OutboxState.REJECTED))))
    assertEquals(SaveStatus.SAVING, saveStatusOf(sending, listOf(retrying)))
    assertEquals(SaveStatus.FAILED, saveStatusOf(syncing, listOf(retrying)))
    assertEquals(SaveStatus.FAILED, saveStatusOf(idle, listOf(retrying)))
  }
}
