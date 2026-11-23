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
package org.openforis.collectone.v2.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import org.openforis.collectone.v2.mobile.services.ConnectivityMonitor

/**
 * [ConnectivityMonitor] backed by [ConnectivityManager]'s default network: online while it has a
 * validated internet connection.
 */
class AndroidConnectivityMonitor(context: Context) : ConnectivityMonitor {
  private val connectivityManager =
    context.applicationContext.getSystemService(ConnectivityManager::class.java)

  override fun start(onChange: (isOnline: Boolean) -> Unit): () -> Unit {
    // Network callbacks arrive on a background thread; report on the main thread.
    val main = Handler(Looper.getMainLooper())
    val report = { isOnline: Boolean -> main.post { onChange(isOnline) } }
    val callback =
      object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
          report(capabilities.isOnline())
        }

        override fun onLost(network: Network) {
          report(false)
        }
      }
    onChange(isOnlineNow())
    connectivityManager.registerDefaultNetworkCallback(callback)
    return {
      connectivityManager.unregisterNetworkCallback(callback)
      main.removeCallbacksAndMessages(null)
    }
  }

  private fun isOnlineNow(): Boolean =
    connectivityManager.activeNetwork
      ?.let(connectivityManager::getNetworkCapabilities)
      ?.isOnline() == true

  private fun NetworkCapabilities.isOnline(): Boolean =
    hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
      hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
