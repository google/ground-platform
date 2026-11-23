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
package org.openforis.collectone.v2.app.data.repository

import com.squareup.wire.ofEpochSecond
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import openforis.collectone.v1.data.DataMutation
import openforis.collectone.v1.data.EntityMutation
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.SubmissionMutation
import openforis.collectone.v1.data.SubmissionRecord
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyMutation
import org.openforis.collectone.v2.app.data.datasource.local.store.InMemoryLocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationPayload
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationTarget
import org.openforis.collectone.v2.app.data.datasource.local.store.OutboxState
import org.openforis.collectone.v2.app.data.datasource.local.store.PendingMutation
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.datasource.remote.GrpcSyncDataSource
import org.openforis.collectone.v2.app.data.datasource.remote.SeedUserDataService
import org.openforis.collectone.v2.app.data.sync.OutboxDrainer
import org.openforis.collectone.v2.app.data.sync.Rebaser
import org.openforis.collectone.v2.app.data.sync.SyncCoordinator
import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.domain.repository.ConnectivityRepository

/**
 * [OutboxSyncRepositoryImpl] requeues or discards only the rejected mutations the user picked, and
 * leaves the rest of the outbox alone. The coordinator stays offline, so nothing is uploaded.
 */
class OutboxSyncRepositoryImplTest {
  private val store = InMemoryLocalStore()
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
  private val client = SeedUserDataService().client()
  private val coordinator =
    SyncCoordinator(
      store = store,
      syncDataSource = GrpcSyncDataSource(client),
      drainer = OutboxDrainer(store, client),
      connectivity = ConnectivityRepository(initialIsOnline = false),
      scope = scope,
    )
  private val repository = OutboxSyncRepositoryImpl(store, coordinator)

  @AfterTest fun tearDown() = scope.cancel()

  private fun entityEdit(id: String, surveyId: String, entityId: String, state: OutboxState) =
    PendingMutation(
      mutationId = id,
      clientSeq = 1L,
      target = MutationTarget.Data(surveyId),
      recordKey = Rebaser.entityKey(entityId),
      payload =
        MutationPayload.Data(
          DataMutation(
            entity_mutation =
              EntityMutation(entity_id = entityId, upsert = EntityRecord(label = id))
          )
        ),
      state = state,
      attempts = 3,
      lastError = DomainError.PermissionDenied,
      notBefore = ofEpochSecond(100L, 0L),
    )

  private fun submissionEdit(id: String, surveyId: String, submissionId: String) =
    PendingMutation(
      mutationId = id,
      clientSeq = 2L,
      target = MutationTarget.Data(surveyId),
      recordKey = Rebaser.submissionKey(submissionId),
      payload =
        MutationPayload.Data(
          DataMutation(
            submission_mutation =
              SubmissionMutation(submission_id = submissionId, upsert = SubmissionRecord())
          )
        ),
      state = OutboxState.REJECTED,
    )

  private fun surveyEdit(id: String, surveyId: String) =
    PendingMutation(
      mutationId = id,
      clientSeq = 3L,
      target = MutationTarget.Survey(surveyId, workingCopy = true),
      recordKey = surveyId,
      payload = MutationPayload.Survey(SurveyMutation(upsert = SurveyDef(title = id))),
      state = OutboxState.REJECTED,
    )

  private fun givenOutbox(vararg mutations: PendingMutation) = runNow {
    store.transaction { putOutbox(mutations.toList()) }
  }

  private fun outbox() = runNow { store.transaction { outbox() } }.associateBy { it.mutationId }

  @Test
  fun retryRecord_requeuesTheRecordsRejectedMutationAndClearsItsFailure() {
    givenOutbox(
      entityEdit("m1", "s1", "e1", OutboxState.REJECTED),
      entityEdit("m2", "s1", "e2", OutboxState.REJECTED),
    )

    val count = runNow { repository.retryRecord("s1", "e1", submissionId = null) }

    assertEquals(1, count)
    val requeued = outbox().getValue("m1")
    assertEquals(OutboxState.QUEUED, requeued.state)
    assertEquals(0, requeued.attempts)
    assertEquals(null, requeued.lastError)
    assertEquals(null, requeued.notBefore)
    assertEquals(OutboxState.REJECTED, outbox().getValue("m2").state)
  }

  @Test
  fun retryRecord_withASubmissionId_matchesTheSubmissionNotTheFeature() {
    givenOutbox(
      entityEdit("m1", "s1", "e1", OutboxState.REJECTED),
      submissionEdit("m2", "s1", "sub1"),
    )

    val count = runNow { repository.retryRecord("s1", "e1", submissionId = "sub1") }

    assertEquals(1, count)
    assertEquals(OutboxState.REJECTED, outbox().getValue("m1").state)
    assertEquals(OutboxState.QUEUED, outbox().getValue("m2").state)
  }

  @Test
  fun retryRecord_ignoresMutationsThatWerentRejected() {
    givenOutbox(entityEdit("m1", "s1", "e1", OutboxState.RETRYING))

    assertEquals(0, runNow { repository.retryRecord("s1", "e1", submissionId = null) })
    assertEquals(OutboxState.RETRYING, outbox().getValue("m1").state)
  }

  @Test
  fun retryRejected_requeuesTheSurveysDataAndDefinitionButNotOtherSurveys() {
    givenOutbox(
      entityEdit("m1", "s1", "e1", OutboxState.REJECTED),
      surveyEdit("m2", "s1"),
      entityEdit("m3", "s2", "e3", OutboxState.REJECTED),
    )

    val count = runNow { repository.retryRejected("s1") }

    assertEquals(2, count)
    val states = outbox().mapValues { it.value.state }
    assertEquals(OutboxState.QUEUED, states["m1"])
    assertEquals(OutboxState.REJECTED, states["m3"])
    // Requeued, then dropped by the rebase as a no-op: the store holds no base for survey s1.
    assertEquals(null, states["m2"])
  }

  @Test
  fun discardRejected_dropsOnlyTheRecordsRejectedMutations() {
    givenOutbox(
      entityEdit("m1", "s1", "e1", OutboxState.REJECTED),
      entityEdit("m2", "s1", "e1", OutboxState.QUEUED),
      entityEdit("m3", "s1", "e2", OutboxState.REJECTED),
    )

    val count = runNow { repository.discardRejected("s1", "e1", submissionId = null) }

    assertEquals(1, count)
    assertEquals(setOf("m2", "m3"), outbox().keys)
  }

  @Test
  fun discardRejected_withNothingRejected_changesNothing() {
    givenOutbox(entityEdit("m1", "s1", "e1", OutboxState.QUEUED))

    assertEquals(0, runNow { repository.discardRejected("s1", "e1", submissionId = null) })
    assertEquals(setOf("m1"), outbox().keys)
  }

  private val onlineRepository by lazy {
    OutboxSyncRepositoryImpl(store, coordinator, ConnectivityRepository(initialIsOnline = true))
  }

  @Test
  fun awaitSurveySaved_withNothingPendingForTheSurvey_succeeds() {
    givenOutbox(entityEdit("m1", "s2", "e1", OutboxState.QUEUED))

    assertEquals(null, runNow { onlineRepository.awaitSurveySaved("s1") })
  }

  @Test
  fun awaitSurveySaved_withARejectedChange_failsWithItsError() {
    givenOutbox(
      entityEdit("m1", "s1", "e1", OutboxState.REJECTED).copy(lastError = DomainError.Validation),
      entityEdit("m2", "s1", "e2", OutboxState.QUEUED),
    )

    assertEquals(DomainError.Validation, runNow { onlineRepository.awaitSurveySaved("s1") })
  }

  @Test
  fun awaitSurveySaved_whileOfflineWithPendingChanges_failsWithANetworkError() {
    val offline =
      OutboxSyncRepositoryImpl(store, coordinator, ConnectivityRepository(initialIsOnline = false))
    givenOutbox(entityEdit("m1", "s1", "e1", OutboxState.QUEUED))

    assertEquals(DomainError.Network, runNow { offline.awaitSurveySaved("s1") })
  }
}
