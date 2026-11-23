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
@file:OptIn(ExperimentalWasmJsInterop::class)

package org.openforis.collectone.v2.web.navigation

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@JsFun("() => window.location.pathname") private external fun jsLocationPath(): String

@JsFun("() => window.location.hash") private external fun jsLocationHash(): String

@JsFun("() => (typeof window.history.state === 'string' ? window.history.state : null)")
private external fun jsHistoryState(): String?

@JsFun("(path, state) => window.history.pushState(state, '', path)")
private external fun jsPushState(path: String, state: String)

@JsFun("(path, state) => window.history.replaceState(state, '', path)")
private external fun jsReplaceState(path: String, state: String)

@JsFun(
  "(callback) => {" +
    " const listener = (event) => callback(typeof event.state === 'string' ? event.state : null);" +
    " window.addEventListener('popstate', listener);" +
    " return listener;" +
    " }"
)
private external fun jsAddPopStateListener(callback: (String?) -> Unit): JsAny

@JsFun("(listener) => window.removeEventListener('popstate', listener)")
private external fun jsRemovePopStateListener(listener: JsAny)

/** The browser window's session history (`window.location` and `window.history`). */
class WindowBrowserHistory : BrowserHistory {
  override val path: String
    get() = jsLocationPath()

  override val hash: String
    get() = jsLocationHash()

  override val state: String?
    get() = jsHistoryState()

  override fun push(path: String, state: String) = jsPushState(path, state)

  override fun replace(path: String, state: String) = jsReplaceState(path, state)

  override val pops: Flow<String?> = callbackFlow {
    val listener = jsAddPopStateListener { state -> trySend(state) }
    awaitClose { jsRemovePopStateListener(listener) }
  }
}
