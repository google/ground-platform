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
import org.openforis.collectone.v2.mobile.ui.MobileAppState
import org.openforis.collectone.v2.workbench.ui.MobileWorkbenchApp
import org.openforis.collectone.v2.workbench.ui.WorkbenchState
import org.openforis.collectone.v2.workbench.ui.shouldShowWorkbenchChrome

/**
 * Mounts the mobile app. On desktop browsers (or with `?chrome=1` in the URL) it runs inside the
 * workbench's simulated device; on mobile browsers (or with `?chrome=0` in the URL) the workbench
 * chrome is hidden and the mobile app fills the window. Waits for the auth client to restore the
 * signed-in session first.
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
  val showChrome =
    shouldShowWorkbenchChrome(
      search = window.location.search,
      userAgent = window.navigator.userAgent,
      maxTouchPoints = window.navigator.maxTouchPoints,
      isCoarsePointerWithoutHover =
        window.matchMedia("(pointer: coarse) and (hover: none)").matches,
    )
  ComposeViewport(container) {
    // Without a configured user data service, shows setup instructions instead of the app.
    RequireUserDataService { serviceUrl ->
      MobileWorkbenchApp(
        state =
          remember {
            WorkbenchState(
              MobileAppState(
                dataHolder =
                  AppDataHolder(
                    userDataServiceUrl = serviceUrl,
                    authClient = authClient,
                    syncDownloadedSurveysOnly = true,
                  )
              )
            )
          },
        showChrome = showChrome,
      )
    }
  }
}
