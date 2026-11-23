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
package org.openforis.collectone.v2.mobileweb

import org.openforis.collectone.v2.mobile.services.ConnectivityMonitor

/**
 * [ConnectivityMonitor] that follows the browser's `navigator.onLine` and `online`/`offline`
 * events.
 */
internal object BrowserConnectivityMonitor : ConnectivityMonitor {
  override fun start(onChange: (isOnline: Boolean) -> Unit): () -> Unit {
    onChange(isNavigatorOnline())
    val handler = addConnectivityListener(onChange)
    return { removeConnectivityListener(handler) }
  }
}

@JsFun("() => navigator.onLine") private external fun isNavigatorOnline(): Boolean

@JsFun(
  """(callback) => {
    const handler = () => callback(navigator.onLine);
    window.addEventListener('online', handler);
    window.addEventListener('offline', handler);
    return handler;
  }"""
)
private external fun addConnectivityListener(callback: (Boolean) -> Unit): JsAny

@JsFun(
  """(handler) => {
    window.removeEventListener('online', handler);
    window.removeEventListener('offline', handler);
  }"""
)
private external fun removeConnectivityListener(handler: JsAny)
