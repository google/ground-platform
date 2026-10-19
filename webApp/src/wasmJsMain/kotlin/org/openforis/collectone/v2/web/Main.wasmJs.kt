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
package org.openforis.collectone.v2.web

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.openforis.collectone.v2.app.client.auth.AuthClient
import org.openforis.collectone.v2.app.client.auth.defaultAuthClient
import org.openforis.collectone.v2.app.di.AppDataHolder
import org.openforis.collectone.v2.app.ui.common.RequireUserDataService
import org.openforis.collectone.v2.web.ui.WebAppState
import org.openforis.collectone.v2.web.ui.WebPage

/**
 * Mounts the web console ([WebConsoleApp]) and keeps the page in sync with the URL hash. Waits for
 * the auth client to restore the signed-in session first, so signed-in users don't see the sign-in
 * page flash.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
  val container = document.getElementById("ComposeTarget") ?: document.body!!
  val authClient = defaultAuthClient()
  MainScope().launch {
    authClient.awaitInitialState()
    mount(container, authClient)
  }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun mount(container: org.w3c.dom.Element, authClient: AuthClient) {
  ComposeViewport(container) {
    // Without a configured user data service, shows setup instructions instead of the console.
    RequireUserDataService { serviceUrl ->
      WebConsoleApp(
        state =
          remember {
            WebAppState(
              dataHolder = AppDataHolder(userDataServiceUrl = serviceUrl, authClient = authClient)
            )
          },
        initialPage = WebPage.fromHash(window.location.hash),
        initialOrganizationId = WebPage.organizationIdFromHash(window.location.hash),
        onHashChanged = { window.location.hash = it },
      )
    }
  }
}
