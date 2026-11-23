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

package org.openforis.collectone.v2.web

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin
import org.openforis.collectone.v2.app.client.auth.AuthClient
import org.openforis.collectone.v2.app.client.auth.defaultAuthClient
import org.openforis.collectone.v2.app.client.auth.platformDevSignIn
import org.openforis.collectone.v2.app.data.datasource.local.session.webConsoleSignedInSessionStore
import org.openforis.collectone.v2.app.di.AppConfig
import org.openforis.collectone.v2.app.di.AppDataHolder
import org.openforis.collectone.v2.app.di.platformModule
import org.openforis.collectone.v2.app.domain.model.resolveMobileRedirectUrl
import org.openforis.collectone.v2.app.domain.model.resolveWebEnvironment
import org.openforis.collectone.v2.app.logging.configureAppLogging
import org.openforis.collectone.v2.app.ui.session.RequireUserDataService
import org.openforis.collectone.v2.web.di.webModules
import org.openforis.collectone.v2.web.navigation.WindowBrowserHistory

@JsFun(
  """() => {
    if (typeof window === 'undefined') return '';
    const env = window.COLLECT_ONE_ENV;
    return typeof env === 'string' ? env : '';
  }"""
)
private external fun jsConfiguredAppEnv(): String

@JsFun(
  """() => {
    if (typeof window === 'undefined') return '';
    const url = window.COLLECT_ONE_MOBILE_WEB_URL;
    return typeof url === 'string' ? url : '';
  }"""
)
private external fun jsConfiguredMobileWebUrl(): String

/**
 * Mounts the web console ([WebConsoleApp]) on the browser window's URL and history. Waits for the
 * auth client to restore the signed-in session first, so signed-in users don't see the sign-in page
 * flash.
 *
 * In non-production builds with a mobile web URL configured (or the local dev default), mobile
 * browsers are redirected directly to the mobile web app unless `?desktop=1` is present.
 *
 * Logs at debug level in development builds, which offer development sign-in (`DEV_SIGN_IN`), and
 * from info level in the builds served to real users.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
  val environment =
    resolveWebEnvironment(
      configuredEnv = jsConfiguredAppEnv(),
      hostname = window.location.hostname,
      port = window.location.port,
      isDevSignIn = platformDevSignIn(),
    )
  val redirectUrl =
    resolveMobileRedirectUrl(
      environment = environment,
      configuredMobileWebUrl = jsConfiguredMobileWebUrl(),
      currentOrigin = window.location.origin,
      currentSearch = window.location.search,
      currentHash = window.location.hash,
      userAgent = window.navigator.userAgent,
      maxTouchPoints = window.navigator.maxTouchPoints,
      isCoarsePointerWithoutHover =
        window.matchMedia("(pointer: coarse) and (hover: none)").matches,
      browserProtocol = window.location.protocol,
      browserHostname = window.location.hostname,
      browserPort = window.location.port,
    )
  if (redirectUrl != null) {
    window.location.replace(redirectUrl)
    return
  }

  configureAppLogging(isDevelopmentBuild = platformDevSignIn())
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
      val dataHolder =
        remember(serviceUrl) {
          val config = AppConfig(userDataServiceUrl = serviceUrl, lazyLoadEntityProperties = true)
          val platform =
            platformModule(
              authClient = { authClient },
              // Keeps the Terms of Service acceptance across page reloads, until signing out.
              signedInSessionStore = ::webConsoleSignedInSessionStore,
            )
          startKoin { modules(webModules(config, platform)) }.koin.get<AppDataHolder>()
        }
      WebAppSession(dataHolder) { state ->
        WebConsoleApp(state = state, browserHistory = remember { WindowBrowserHistory() })
      }
    }
  }
}
