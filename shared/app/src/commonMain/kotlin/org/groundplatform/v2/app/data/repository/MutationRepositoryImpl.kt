/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.groundplatform.v2.app.data.datasource.local.store.LocalStore
import org.groundplatform.v2.app.data.datasource.local.store.MutationTarget
import org.groundplatform.v2.app.data.datasource.local.store.OutboxState
import org.groundplatform.v2.app.data.datasource.local.store.PendingMutation
import org.groundplatform.v2.app.data.sync.Rebaser
import org.groundplatform.v2.app.domain.model.MutationLogItem
import org.groundplatform.v2.app.domain.model.MutationSyncState
import org.groundplatform.v2.app.domain.repository.MutationRepository

/**
 * [MutationRepository] backed by the [LocalStore].
 *
 * When [trackOutbox] is on (the sync engine is connected), the state of each logged change in the
 * `Uploads` view comes from the outbox mutations for its record: queued, in progress, retrying, or
 * failed while any are pending, and uploaded once the server has confirmed them all.
 */
class MutationRepositoryImpl(
  private val store: LocalStore,
  private val trackOutbox: Boolean = false,
) : MutationRepository {
  override fun observeMutations(): Flow<List<MutationLogItem>> =
    if (!trackOutbox) {
      store.observeMutations()
    } else {
      combine(store.observeMutations(), store.observeOutbox()) { log, outbox ->
        log.map { withOutboxState(it, outbox) }
      }
    }

  override suspend fun getMutations(): List<MutationLogItem> = store.transaction {
    if (trackOutbox) mutations().map { withOutboxState(it, outbox()) } else mutations()
  }

  override suspend fun setMutations(mutations: List<MutationLogItem>) {
    store.transaction { putMutations(mutations) }
  }

  override suspend fun prependMutations(newMutations: List<MutationLogItem>) {
    store.transaction { putMutations(newMutations + mutations()) }
  }

  override suspend fun updateMutation(
    mutationId: String,
    transform: (MutationLogItem) -> MutationLogItem,
  ) {
    store.transaction {
      putMutations(mutations().map { if (it.id == mutationId) transform(it) else it })
    }
  }

  private fun withOutboxState(
    item: MutationLogItem,
    outbox: List<PendingMutation>,
  ): MutationLogItem {
    val recordKey =
      item.submissionId?.let(Rebaser::submissionKey) ?: Rebaser.entityKey(item.entityId)
    val pending = outbox.filter {
      it.target == MutationTarget.Data(item.surveyId) && it.recordKey == recordKey
    }
    val state =
      when {
        pending.any { it.state == OutboxState.REJECTED } -> MutationSyncState.FAILED
        pending.any { it.state == OutboxState.IN_FLIGHT } -> MutationSyncState.UPLOADING
        pending.any { it.state == OutboxState.RETRYING } -> MutationSyncState.RETRYING
        pending.isNotEmpty() -> MutationSyncState.QUEUED
        item.isOutbox -> MutationSyncState.UPLOADED
        else -> item.state
      }
    if (state == item.state) return item
    val detail =
      when (state) {
        MutationSyncState.FAILED,
        MutationSyncState.RETRYING ->
          pending.firstNotNullOfOrNull { it.lastError } ?: item.stateDetail
        MutationSyncState.UPLOADED -> "Synced to Collect One Cloud"
        else -> item.stateDetail
      }
    return item.copy(state = state, stateDetail = detail)
  }
}
