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
package org.openforis.collectone.v2.app.domain.repository

import kotlinx.coroutines.flow.StateFlow
import org.openforis.collectone.v2.core.version.MobileClientVersionPolicy
import org.openforis.collectone.v2.core.version.MobileUpdateStatus

/**
 * Tracks the server's mobile client version policy and exposes whether this client should suggest
 * or require an update, plus admin operations to read and update the policy.
 */
interface ClientVersionRepository {
  /** Last known mobile client version policy from the server. */
  val policy: StateFlow<MobileClientVersionPolicy>

  /** Current update status for this client version against [policy]. */
  val updateStatus: StateFlow<MobileUpdateStatus>

  /** Whether the user has dismissed the current suggested update for this session. */
  val isSuggestionDismissed: StateFlow<Boolean>

  /** Fetches the latest policy from the server. */
  suspend fun refreshPolicy(): Result<MobileClientVersionPolicy>

  /** Updates the server's mobile client version policy (platform administrators only). */
  suspend fun updatePolicy(policy: MobileClientVersionPolicy): Result<MobileClientVersionPolicy>

  /** Dismisses the currently suggested update for this session (no effect for required updates). */
  fun dismissSuggestion()
}
