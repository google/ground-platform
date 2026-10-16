/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.app.data.repository

import org.groundplatform.v2.app.data.datasource.local.store.LocalStore
import org.groundplatform.v2.app.data.datasource.local.store.MutationTarget
import org.groundplatform.v2.app.data.datasource.local.store.OutboxState
import org.groundplatform.v2.app.data.datasource.local.store.PendingMutation
import org.groundplatform.v2.app.data.sync.Rebaser
import org.groundplatform.v2.app.data.sync.SyncCoordinator
import org.groundplatform.v2.app.data.sync.rebaseRecords
import org.groundplatform.v2.app.domain.repository.OutboxSyncRepository

/** [OutboxSyncRepository] over the [LocalStore] outbox and the [SyncCoordinator]. */
class OutboxSyncRepositoryImpl(
  private val store: LocalStore,
  private val coordinator: SyncCoordinator,
) : OutboxSyncRepository {
  override suspend fun syncNow() = coordinator.syncNow()

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
