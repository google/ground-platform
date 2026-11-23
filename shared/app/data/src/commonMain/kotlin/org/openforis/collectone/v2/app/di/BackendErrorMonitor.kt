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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openforis.collectone.v2.app.client.auth.AuthClient
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationTarget
import org.openforis.collectone.v2.app.data.datasource.local.store.OutboxState
import org.openforis.collectone.v2.app.data.datasource.local.store.PendingMutation
import org.openforis.collectone.v2.app.data.sync.SyncActivity
import org.openforis.collectone.v2.app.data.sync.SyncCoordinator
import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.domain.repository.ConnectivityRepository

/**
 * Surfaces the latest unacknowledged backend error ([error]) so rejected edits and transient sync
 * failures are never swallowed silently, and lets the user [dismiss] it or [retry] right away.
 *
 * Rejected mutations take precedence; transient outbox and pull errors are shown while online when
 * the initial sync banner ([initialSyncState]) is not already showing a failure. Dismissals are
 * forgotten when the signed-in user changes.
 */
class BackendErrorMonitor(
  private val localStore: LocalStore,
  private val syncCoordinator: SyncCoordinator?,
  connectivityRepository: ConnectivityRepository,
  initialSyncState: StateFlow<UserDataSyncState>,
  authClient: AuthClient,
  private val scope: CoroutineScope,
) {
  private val dismissedKeys = MutableStateFlow<Set<String>>(emptySet())

  /** Latest unacknowledged backend mutation rejection or online sync error, if any. */
  val error: StateFlow<BackendOperationError?> =
    combine(
        localStore.observeOutbox(),
        syncCoordinator?.activity ?: MutableStateFlow(SyncActivity()),
        connectivityRepository.observeIsOnline(),
        initialSyncState,
        dismissedKeys,
      ) { outbox, activity, isOnline, initialSync, dismissed ->
        if (!isOnline || initialSync is UserDataSyncState.Failed) {
          return@combine null
        }
        val rejected = outbox.lastOrNull {
          it.state == OutboxState.REJECTED && "rejected:${it.mutationId}" !in dismissed
        }
        if (rejected != null) {
          return@combine BackendOperationError(
            key = "rejected:${rejected.mutationId}",
            error = rejected.lastError ?: DomainError.Unknown(),
            canRetry = false,
          )
        }
        val retrying = outbox.lastOrNull {
          it.state == OutboxState.RETRYING && it.lastError != null && retryingKey(it) !in dismissed
        }
        if (retrying != null) {
          return@combine BackendOperationError(
            key = retryingKey(retrying),
            error = retrying.lastError ?: DomainError.Unknown(),
            canRetry = true,
          )
        }
        val syncError = activity.lastError
        if (syncError != null && "sync:${syncError.kind}" !in dismissed) {
          return@combine BackendOperationError(
            key = "sync:${syncError.kind}",
            error = syncError,
            canRetry = true,
          )
        }
        null
      }
      .stateIn(scope, SharingStarted.Eagerly, null)

  init {
    if (authClient.usesRealAccounts) {
      authClient.currentUser.onEach { dismissedKeys.value = emptySet() }.launchIn(scope)
    }
  }

  /** Dismisses the current [error] and clears non-data rejected outbox items. */
  fun dismiss() {
    val current = error.value ?: return
    dismissedKeys.update { it + current.key }
    if (current.key.startsWith("rejected:")) {
      val mutationId = current.key.removePrefix("rejected:")
      scope.launch {
        localStore.transaction {
          val remaining =
            outbox().filterNot {
              it.mutationId == mutationId &&
                it.state == OutboxState.REJECTED &&
                it.target !is MutationTarget.Data
            }
          if (remaining.size != outbox().size) {
            putOutbox(remaining)
          }
        }
      }
    }
  }

  /** Immediately retries any retrying outbox mutations and triggers a background sync. */
  fun retry() {
    dismissedKeys.update { keys ->
      keys.filterNot { it.startsWith("retrying:") || it.startsWith("sync:") }.toSet()
    }
    syncCoordinator?.retryNow(fullSync = true)
  }
}

/**
 * Dismissal key of a retrying [mutation]'s error: a new failure message shows it again, as does a
 * new reason when there's no message.
 */
private fun retryingKey(mutation: PendingMutation): String =
  "retrying:${mutation.mutationId}:${mutation.lastErrorDetail ?: mutation.lastError?.kind}"

/** Name of this error's reason, for dismissal keys (the same for every [DomainError.Unknown]). */
private val DomainError.kind: String
  get() = this::class.simpleName.orEmpty()
