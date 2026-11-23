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

import org.openforis.collectone.v2.app.domain.error.DomainError

/** Progress of [AppDataHolder]'s initial sync from the user data service. */
sealed interface UserDataSyncState {
  /** No service is configured; the store is used as is (tests and fixtures). */
  data object NotConfigured : UserDataSyncState

  /** The initial state is being pulled from the service. */
  data object Syncing : UserDataSyncState

  /** The store holds the service's data. */
  data object Synced : UserDataSyncState

  /**
   * Pulling from the service at [serviceUrl] failed because of [error]; the user can retry. The raw
   * detail is only logged.
   */
  data class Failed(val serviceUrl: String?, val error: DomainError) : UserDataSyncState
}

/**
 * Latest unacknowledged backend mutation or background sync error surfaced to the UI after initial
 * load, so rejected edits or transient server errors are never swallowed silently.
 */
data class BackendOperationError(
  /** Stable identifier used to dismiss this specific error occurrence. */
  val key: String,
  /**
   * Why it failed, shown as a localized message (`ErrorMessages.reason`). The server's or the
   * exception's raw message is for logs only, never shown to the user.
   */
  val error: DomainError,
  /** True when the operation is still queued for retry and can be retried immediately. */
  val canRetry: Boolean,
)
