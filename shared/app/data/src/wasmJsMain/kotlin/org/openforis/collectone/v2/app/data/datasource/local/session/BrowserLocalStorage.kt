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
package org.openforis.collectone.v2.app.data.datasource.local.session

import kotlinx.browser.localStorage

/**
 * The browser's `localStorage`. Reads and writes that the browser refuses (storage disabled or
 * full) are ignored, so the app keeps working with nothing stored.
 */
object BrowserLocalStorage : KeyValueStorage {
  override fun get(key: String): String? = runCatching { localStorage.getItem(key) }.getOrNull()

  override fun set(key: String, value: String) {
    runCatching { localStorage.setItem(key, value) }
  }

  override fun remove(key: String) {
    runCatching { localStorage.removeItem(key) }
  }
}

/**
 * The web console's [SignedInSessionStore]: keeps the Terms of Service acceptance of the account
 * the auth client restored across page reloads, until signing out, in the browser's `localStorage`.
 * It never signs anyone in by itself ([SignedInSessionStore.restoresAccount] is false).
 */
fun webConsoleSignedInSessionStore(): SignedInSessionStore =
  KeyValueSignedInSessionStore(
    storage = BrowserLocalStorage,
    restoresAccount = false,
    key = "collect-one.web-console.signed-in-session",
  )
