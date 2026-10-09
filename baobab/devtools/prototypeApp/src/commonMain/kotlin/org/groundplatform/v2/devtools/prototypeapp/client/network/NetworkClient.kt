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
package org.groundplatform.v2.devtools.prototypeapp.client.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Device connectivity client (`client/network/ *Client`) exposing network reachability state per
 * `docs/technical/client/architecture.md`.
 */
class NetworkClient(initialIsOnline: Boolean = true) {
  private val _isOnline = MutableStateFlow(initialIsOnline)

  /** Reactive stream of whether the device currently has network connectivity. */
  val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

  /** Synchronous snapshot of whether the device currently has network connectivity. */
  fun isOnline(): Boolean = _isOnline.value

  /** Updates network connectivity state (used by device connectivity monitors or workbench). */
  fun setOnline(online: Boolean) {
    _isOnline.value = online
  }

  /** Restores default online connectivity state. */
  fun reset() {
    _isOnline.value = true
  }
}
