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
package org.openforis.collectone.v2.mobile.services

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import org.openforis.collectone.v2.mobile.ui.MobileAppState

/**
 * Reports whether the device has a network connection. Each host implements it with its platform
 * API (Android's `ConnectivityManager`, the browser's `navigator.onLine`); the workbench simulates
 * connectivity with its airplane mode toggle instead.
 */
fun interface ConnectivityMonitor {
  /**
   * Calls [onChange] with whether the device is online, once now and on every change, on the main
   * thread. Returns a function that stops monitoring.
   */
  fun start(onChange: (isOnline: Boolean) -> Unit): () -> Unit
}

/** Feeds [monitor]'s connectivity reports to [state] while this composable is in the tree. */
@Composable
fun MonitorDeviceConnectivity(state: MobileAppState, monitor: ConnectivityMonitor) {
  DisposableEffect(state, monitor) {
    val stop = monitor.start(state::updateDeviceConnectivity)
    onDispose { stop() }
  }
}
