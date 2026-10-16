/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.web

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window
import org.groundplatform.v2.app.di.AppDataHolder
import org.groundplatform.v2.app.ui.common.RequireUserDataService
import org.groundplatform.v2.web.ui.WebAppState
import org.groundplatform.v2.web.ui.WebPage

/** Mounts the web console ([WebConsoleApp]) and keeps the page in sync with the URL hash. */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
  val container = document.getElementById("ComposeTarget") ?: document.body!!
  ComposeViewport(container) {
    // Without a configured user data service, shows setup instructions instead of the console.
    RequireUserDataService { serviceUrl ->
      WebConsoleApp(
        state =
          remember { WebAppState(dataHolder = AppDataHolder(userDataServiceUrl = serviceUrl)) },
        initialPage = WebPage.fromHash(window.location.hash),
        initialOrganizationId = WebPage.organizationIdFromHash(window.location.hash),
        onHashChanged = { window.location.hash = it },
      )
    }
  }
}
