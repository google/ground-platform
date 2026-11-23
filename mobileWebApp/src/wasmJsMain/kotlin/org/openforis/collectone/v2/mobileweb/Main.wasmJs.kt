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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import androidx.navigation.ExperimentalBrowserHistoryApi
import androidx.navigation.NavHostController
import androidx.navigation.bindToBrowserNavigation
import androidx.navigation.compose.rememberNavController
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin
import org.openforis.collectone.v2.app.client.auth.AuthClient
import org.openforis.collectone.v2.app.client.auth.defaultAuthClient
import org.openforis.collectone.v2.app.client.offline.SimulatedOfflineRegionClient
import org.openforis.collectone.v2.app.client.storage.BrowserSqlDriverFactory
import org.openforis.collectone.v2.app.di.AppDataHolder
import org.openforis.collectone.v2.app.di.platformModule
import org.openforis.collectone.v2.app.logging.configureAppLogging
import org.openforis.collectone.v2.app.ui.session.RequireUserDataService
import org.openforis.collectone.v2.core.forms.ui.BrowserGeoLocationSource
import org.openforis.collectone.v2.core.forms.ui.LocalGeoLocationSource
import org.openforis.collectone.v2.mobile.MobileApp
import org.openforis.collectone.v2.mobile.MobileAppSession
import org.openforis.collectone.v2.mobile.di.mobileModules
import org.openforis.collectone.v2.mobile.resumesAtHistoryRoute
import org.openforis.collectone.v2.mobile.services.MonitorDeviceConnectivity
import org.openforis.collectone.v2.mobile.ui.MobileAppState
import org.openforis.collectone.v2.mobile.ui.navigation.mobileRouteOfUrlFragment
import org.openforis.collectone.v2.workbench.ui.MobileWorkbenchApp
import org.openforis.collectone.v2.workbench.ui.WorkbenchState
import org.openforis.collectone.v2.workbench.ui.shouldShowWorkbenchChrome

/**
 * Mounts the mobile app. On desktop browsers (or with `?chrome=1` in the URL) it runs inside the
 * workbench's simulated device; on mobile browsers (or with `?chrome=0` in the URL) the workbench
 * chrome is hidden and the mobile app fills the window. Waits for the auth client to restore the
 * signed-in session first.
 *
 * Logs at debug level: the mobile app in the browser is a development and testing host.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
  configureAppLogging(isDevelopmentBuild = true)
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
      val dataHolder =
        remember(serviceUrl) {
          val platform =
            platformModule(
              authClient = { authClient },
              // Offline edits and the last synced data survive reloads, as on a device.
              localDatabase = ::BrowserSqlDriverFactory,
              // Mapbox GL JS v1 has no offline storage: saving map areas is simulated (and the app
              // says so), following the browser's or the workbench's connectivity.
              offlineRegionClient = ::SimulatedOfflineRegionClient,
            )
          startKoin { modules(mobileModules(serviceUrl, platform)) }.koin.get<AppDataHolder>()
        }
      // Starts where the data on the device leaves off, offline too (the account signed in on this
      // device, its surveys, and the survey it had open are kept in the local database).
      MobileAppSession(dataHolder) { mobile ->
        // Decided by the session restored at launch, which the session waits for before starting.
        val restoreRoute =
          remember(mobile) {
            val restoredSession = dataHolder.authRepository.observeRestoredSession().value
            restoredSession != null && resumesAtHistoryRoute(restoredSession)
          }
        // Without the workbench chrome (and its simulated airplane mode and GPS), the app runs on a
        // real device, so it follows the browser's connectivity and location without workbench
        // simulation hooks.
        if (showChrome) {
          val workbench = remember(mobile) { WorkbenchState(mobile) }
          MobileWorkbenchApp(
            state = workbench,
            showChrome = true,
            hostOrigin = window.location.origin,
          )
        } else {
          MonitorDeviceConnectivity(mobile, BrowserConnectivityMonitor)
          val navController = rememberNavController()
          BindToBrowserHistory(mobile, navController, restoreRoute)
          val scope = rememberCoroutineScope()
          val geoLocationSource = remember { BrowserGeoLocationSource(scope) }
          CompositionLocalProvider(LocalGeoLocationSource provides geoLocationSource) {
            MobileApp(mobile, navController = navController)
          }
        }
      }
    }
  }
}

/**
 * Mirrors the mobile app's back stack in the browser history while it fills the window: the
 * browser's Back and Forward buttons move through its routes, and the URL fragment names the
 * current one (`#survey`, `#settings`). A reload restores that route when [restoreRoute] (the user
 * is still signed in, with the terms accepted) and the route may be shown again (the Main Survey's
 * routes only with the survey the user had open reopened, see `MobileNavigator.mayRestore`);
 * otherwise the app starts at its launch route: the survey the user had open, the surveys on the
 * device, or Download survey. Inside the workbench's simulated device the history stays unbound, as
 * on a real device.
 */
@OptIn(ExperimentalBrowserHistoryApi::class)
@Composable
private fun BindToBrowserHistory(
  mobile: MobileAppState,
  navController: NavHostController,
  restoreRoute: Boolean,
) {
  LaunchedEffect(navController) {
    // Binding navigates to the URL's route, which needs the graph the NavHost sets.
    navController.currentBackStack.first { it.isNotEmpty() }
    val historyRoute =
      navController.mobileRouteOfUrlFragment(window.location.hash.removePrefix("#"))
    if (!restoreRoute || historyRoute == null || !mobile.navigator.mayRestore(historyRoute)) {
      window.history.replaceState(null, "", window.location.pathname + window.location.search)
    }
    navController.bindToBrowserNavigation()
  }
  LaunchedEffect(mobile) {
    snapshotFlow { mobile.currentRoute }.collect { mobile.returnToSignInIfSignedOut() }
  }
}
