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
package org.groundplatform.v2.app.di

/** Progress of [AppDataHolder]'s initial sync from the user data service. */
sealed interface UserDataSyncState {
  /** No service is configured; the store is used as is (tests and fixtures). */
  data object NotConfigured : UserDataSyncState

  /** The initial state is being pulled from the service. */
  data object Syncing : UserDataSyncState

  /** The store holds the service's data. */
  data object Synced : UserDataSyncState

  /** Pulling from the service at [serviceUrl] failed with [message]; the user can retry. */
  data class Failed(val serviceUrl: String?, val message: String) : UserDataSyncState
}
