/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.app.data.repository

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationTarget
import org.openforis.collectone.v2.app.data.datasource.local.store.OutboxState
import org.openforis.collectone.v2.app.data.datasource.local.store.PendingMutation
import org.openforis.collectone.v2.app.data.sync.Rebaser
import org.openforis.collectone.v2.app.data.sync.SyncCoordinator
import org.openforis.collectone.v2.app.data.sync.rebaseRecords
import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.domain.repository.ConnectivityRepository
import org.openforis.collectone.v2.app.domain.repository.OutboxSyncRepository

/**
 * [OutboxSyncRepository] over the [LocalStore] outbox and the [SyncCoordinator]: what the user can
 * do about uploads the server rejected.
 *
 * Retrying requeues the rejected mutations with a clean slate (no attempts, error, or backoff) and
 * asks for a sync; discarding drops them. Either way the affected records are rebased in the same
 * transaction, so the local copy shows (or stops showing) those edits at once. Rebasing may also
 * drop requeued mutations that no longer change anything.
 *
 * Waiting for a survey to be saved ([awaitSurveySaved]) watches the outbox (and [connectivity],
 * when given) until the survey's mutations are gone or one of them fails.
 */
class OutboxSyncRepositoryImpl(
  private val store: LocalStore,
  private val coordinator: SyncCoordinator,
  private val connectivity: ConnectivityRepository? = null,
) : OutboxSyncRepository {
  override suspend fun syncNow() = coordinator.syncNow()

  override suspend fun awaitSurveySaved(surveyId: String): DomainError? {
    // Failures recorded before this call (by earlier attempts) don't count: only a send that fails
    // while waiting does, so a mutation that was backing off gets another chance first.
    val attemptsBefore =
      store
        .transaction { outbox() }
        .filter { it.isInSurvey(surveyId) }
        .associate { it.mutationId to it.attempts }
    coordinator.requestSync()
    val online = connectivity?.observeIsOnline() ?: flowOf(true)
    return combine(store.observeOutbox(), online) { outbox, isOnline ->
        val pending = outbox.filter { it.isInSurvey(surveyId) }
        val rejected = pending.firstOrNull { it.state == OutboxState.REJECTED }
        val failed = pending.firstOrNull {
          it.state == OutboxState.RETRYING &&
            it.lastError != null &&
            it.attempts > (attemptsBefore[it.mutationId] ?: 0)
        }
        when {
          pending.isEmpty() -> SaveOutcome.Saved
          rejected != null -> SaveOutcome.Failed(rejected.lastError ?: DomainError.Unknown())
          failed != null -> SaveOutcome.Failed(failed.lastError ?: DomainError.Unknown())
          !isOnline -> SaveOutcome.Failed(DomainError.Network)
          else -> null
        }
      }
      .mapNotNull { it }
      .first()
      .let { (it as? SaveOutcome.Failed)?.error }
  }

  /** How waiting for a survey's mutations ended. */
  private sealed interface SaveOutcome {
    data object Saved : SaveOutcome

    data class Failed(val error: DomainError) : SaveOutcome
  }

  override suspend fun retryRecord(surveyId: String, entityId: String, submissionId: String?): Int =
    requeue {
      it.isFor(surveyId, entityId, submissionId)
    }

  override suspend fun retryRejected(surveyId: String): Int = requeue { it.isInSurvey(surveyId) }

  override suspend fun discardRejected(
    surveyId: String,
    entityId: String,
    submissionId: String?,
  ): Int = store.transaction {
    val discarded =
      outbox().filter {
        it.state == OutboxState.REJECTED && it.isFor(surveyId, entityId, submissionId)
      }
    if (discarded.isNotEmpty()) {
      val ids = discarded.map { it.mutationId }.toSet()
      putOutbox(outbox().filterNot { it.mutationId in ids })
      rebaseRecords(discarded)
    }
    discarded.size
  }

  private suspend fun requeue(matches: (PendingMutation) -> Boolean): Int {
    val count = store.transaction {
      val requeued = outbox().filter { it.state == OutboxState.REJECTED && matches(it) }
      if (requeued.isNotEmpty()) {
        val ids = requeued.map { it.mutationId }.toSet()
        putOutbox(
          outbox().map {
            if (it.mutationId in ids) {
              it.copy(state = OutboxState.QUEUED, lastError = null, notBefore = null, attempts = 0)
            } else {
              it
            }
          }
        )
        rebaseRecords(requeued)
      }
      requeued.size
    }
    if (count > 0) coordinator.requestSync()
    return count
  }

  private fun PendingMutation.isInSurvey(surveyId: String): Boolean =
    when (val t = target) {
      is MutationTarget.Data -> t.surveyId == surveyId
      is MutationTarget.Survey -> t.surveyId == surveyId
      else -> false
    }

  private fun PendingMutation.isFor(
    surveyId: String,
    entityId: String,
    submissionId: String?,
  ): Boolean =
    target == MutationTarget.Data(surveyId) &&
      recordKey ==
        if (submissionId != null) Rebaser.submissionKey(submissionId)
        else Rebaser.entityKey(entityId)
}
