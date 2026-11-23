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
package org.openforis.collectone.v2.app.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore
import org.openforis.collectone.v2.app.data.repository.LocalStoreTransactionRunner
import org.openforis.collectone.v2.app.data.repository.MutationRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.SurveyRepositoryImpl
import org.openforis.collectone.v2.app.domain.repository.ConnectivityRepository
import org.openforis.collectone.v2.app.domain.repository.OutboxSyncRepository

/** Uploading the outbox through the sync engine (the path real devices take). */
class SyncMutationsUseCaseOutboxTest {
  /** Records the calls the use case makes to the sync engine's outbox. */
  private class RecordingOutbox : OutboxSyncRepository {
    val calls = mutableListOf<String>()

    override suspend fun syncNow() {
      calls += "syncNow"
    }

    override suspend fun retryRecord(surveyId: String, entityId: String, submissionId: String?) =
      0.also { calls += "retryRecord($entityId)" }

    override suspend fun retryRejected(surveyId: String) =
      1.also { calls += "retryRejected($surveyId)" }

    override suspend fun discardRejected(
      surveyId: String,
      entityId: String,
      submissionId: String?,
    ) = 0
  }

  private val store = seededStore()
  private val mutationRepository = MutationRepositoryImpl(store)
  private val outbox = RecordingOutbox()
  private val connectivity = ConnectivityRepository(initialIsOnline = true)
  private val useCase =
    SyncMutationsUseCase(
      mutationRepository = mutationRepository,
      surveyRepository = SurveyRepositoryImpl(store),
      transactionRunner = LocalStoreTransactionRunner(store),
      connectivityRepository = connectivity,
      outboxSyncRepository = outbox,
    )

  private fun surveyWithPendingChanges(): String = runNow {
    mutationRepository.getMutations().first { it.isOutbox }.surveyId
  }

  @Test
  fun syncAllOutboxMutations_requeuesRejectedChangesBeforeSyncing() {
    val surveyId = surveyWithPendingChanges()

    runNow { useCase.syncAllOutboxMutations(surveyId) }

    assertEquals(listOf("retryRejected($surveyId)", "syncNow"), outbox.calls)
  }

  @Test
  fun syncAllOutboxMutations_offline_leavesTheOutboxAlone() {
    val surveyId = surveyWithPendingChanges()
    connectivity.setOnline(false)

    val notice = runNow { useCase.syncAllOutboxMutations(surveyId) }

    assertEquals(SyncMutationsUseCase.OFFLINE_UPLOAD_NOTICE, notice)
    assertTrue(outbox.calls.isEmpty())
  }
}
