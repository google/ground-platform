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
package org.openforis.collectone.v2.mobile

import org.openforis.collectone.v2.mobile.services.ConnectivityMonitor

/**
 * iOS [ConnectivityMonitor] that always reports the device as online.
 *
 * The iOS build is opt-in and isn't compiled or tested in CI yet, so it doesn't bind the Network
 * framework's `NWPathMonitor` here. Reporting online keeps the app's previous behavior: the sync
 * engine still survives a lost connection, because failed requests are retried with backoff and the
 * outbox keeps every unsent change. Replace this with an `NWPathMonitor`-backed monitor once the
 * iOS targets build in CI.
 */
internal object AlwaysOnlineConnectivityMonitor : ConnectivityMonitor {
  override fun start(onChange: (isOnline: Boolean) -> Unit): () -> Unit {
    onChange(true)
    return {}
  }
}
