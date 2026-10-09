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
package org.groundplatform.v2.devtools.prototypeapp.domain.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Domain repository interface (`domain/repository/ *Repository`) exposing network connectivity
 * status to domain use cases and feature ViewModels without leaking workbench simulation flags into
 * application code.
 */
interface ConnectivityRepository {
  /** Reactive stream of whether the device currently has an active network connection. */
  fun observeIsOnline(): StateFlow<Boolean>

  /** Synchronous snapshot of whether the device currently has an active network connection. */
  fun isOnline(): Boolean

  /** Updates the device's online connectivity state. */
  fun setOnline(online: Boolean)

  /** Resets connectivity state to online. */
  fun reset()

  companion object {
    /** Default in-memory [ConnectivityRepository] for standalone use cases and ViewModels. */
    operator fun invoke(initialIsOnline: Boolean = true): ConnectivityRepository =
      object : ConnectivityRepository {
        private val state = MutableStateFlow(initialIsOnline)
        private val readOnly = state.asStateFlow()

        override fun observeIsOnline(): StateFlow<Boolean> = readOnly

        override fun isOnline(): Boolean = state.value

        override fun setOnline(online: Boolean) {
          state.value = online
        }

        override fun reset() {
          state.value = true
        }
      }
  }
}
