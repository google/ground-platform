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
package org.openforis.collectone.v2.workbench.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.openforis.collectone.v2.app.ui.common.DemoAttributionFooterBar
import org.openforis.collectone.v2.app.ui.common.DevicePreviewFrame
import org.openforis.collectone.v2.app.ui.navigation.AppScreen
import org.openforis.collectone.v2.core.forms.ui.AppTheme
import org.openforis.collectone.v2.core.forms.ui.LocalGeoLocationSource
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.mobile.MobileApp
import org.openforis.collectone.v2.mobile.ui.navigation.appScreen
import org.openforis.collectone.v2.mobile.ui.onboarding.MobileScreenHost

private val CHROME_PARAM_REGEX =
  Regex("""(?:^|[?&])chrome=([^&]*)(?:&|$)""", RegexOption.IGNORE_CASE)

/**
 * Returns `true` when the browser reports a mobile or tablet environment: a mobile user-agent
 * token, iPadOS desktop-class Safari (`Macintosh` with multi-touch), or a coarse primary pointer
 * without hover capability.
 */
fun isMobileBrowser(
  userAgent: String,
  maxTouchPoints: Int = 0,
  isCoarsePointerWithoutHover: Boolean = false,
): Boolean =
  org.openforis.collectone.v2.app.domain.model.isMobileBrowser(
    userAgent = userAgent,
    maxTouchPoints = maxTouchPoints,
    isCoarsePointerWithoutHover = isCoarsePointerWithoutHover,
  )

/**
 * Resolves whether the workbench device frame and controls should be shown around the mobile app.
 *
 * - `?chrome=0` or `?chrome=false` explicitly hides the workbench chrome.
 * - `?chrome=1` or `?chrome=true` explicitly shows the workbench chrome.
 * - Otherwise, the workbench chrome is hidden automatically on mobile browsers and shown on desktop
 *   browsers.
 */
fun shouldShowWorkbenchChrome(
  search: String,
  userAgent: String,
  maxTouchPoints: Int = 0,
  isCoarsePointerWithoutHover: Boolean = false,
): Boolean {
  val chromeParam = CHROME_PARAM_REGEX.find(search)?.groupValues?.get(1)?.lowercase()
  return when (chromeParam) {
    "0",
    "false" -> false
    "1",
    "true" -> true
    else -> !isMobileBrowser(userAgent, maxTouchPoints, isCoarsePointerWithoutHover)
  }
}

/**
 * Root of the Wasm mobile build (`mobileWebApp`). With [showChrome], the mobile app runs inside the
 * workbench's simulated device ([MobileWorkbenchPage]); without it (on a mobile browser or with
 * `?chrome=0`), the mobile app fills the browser window like a native app. Inside the simulated
 * device, location questions get the workbench's [SimulatedGeoLocationSource]; without the chrome,
 * they get the host's `LocalGeoLocationSource` (the device's real location).
 *
 * Without the chrome, [navController] drives the mobile app's navigation, so the host can bind it
 * to the browser history; the simulated device keeps its own, unbound from the browser.
 */
@Composable
fun MobileWorkbenchApp(
  state: WorkbenchState,
  showChrome: Boolean = true,
  hostOrigin: String = "",
  navController: NavHostController = rememberNavController(),
) {
  if (!showChrome) {
    MobileApp(state.mobile, navController = navController)
    return
  }
  val geoLocationSource = remember { SimulatedGeoLocationSource() }
  LaunchedEffect(geoLocationSource) { geoLocationSource.walk() }
  CompositionLocalProvider(LocalGeoLocationSource provides geoLocationSource) {
    MobileWorkbenchContent(state = state, hostOrigin = hostOrigin)
  }
}

@Composable
private fun MobileWorkbenchContent(state: WorkbenchState, hostOrigin: String) {
  val mobile = state.mobile
  val settingsUiState by mobile.viewModels.settings.uiState.collectAsState()
  val dataCollectionUiState by mobile.viewModels.dataCollection.uiState.collectAsState()

  val isMapShowing = mobile.isMapShowing(dataCollectionUiState)
  AppTheme(darkTheme = settingsUiState.isDarkTheme) {
    Surface(
      modifier = Modifier.fillMaxSize(),
      // The Mapbox basemap renders behind the Compose canvas, so the root surface must stay
      // transparent while the device preview shows it.
      color =
        if (isMapShowing) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
          MobileWorkbenchPage(
            workbench = state,
            isMapShowing = isMapShowing,
            hostOrigin = hostOrigin,
          )
        }
        DemoAttributionFooterBar()
      }
    }
  }
}

/** URL of the hosted Android APK served alongside the mobile web workbench, or empty if unset. */
internal fun apkDownloadUrl(hostOrigin: String): String =
  hostOrigin.trimEnd('/').takeIf { it.isNotEmpty() }?.let { "$it/collect-one.apk" } ?: ""

/**
 * URL of the hosted iOS development install page served alongside the mobile web workbench, or
 * empty if unset.
 */
internal fun iosDownloadUrl(hostOrigin: String): String =
  hostOrigin.trimEnd('/').takeIf { it.isNotEmpty() }?.let { "$it/collect-one-ios.html" } ?: ""

/**
 * Mobile and tablet device preview stage: the workbench's simulated device around the mobile app.
 */
@Composable
fun MobileWorkbenchPage(
  workbench: WorkbenchState,
  hostOrigin: String = "",
  isMapShowing: Boolean,
) {
  val verticalScrollState = rememberScrollState()
  val horizontalScrollState = rememberScrollState()
  val mobile = workbench.mobile
  val uiState by workbench.viewModel.uiState.collectAsState()
  val settings = mobile.viewModels.settings
  val settingsUiState by settings.uiState.collectAsState()
  val surveyMap = mobile.viewModels.surveyMap
  val mapUiState by surveyMap.uiState.collectAsState()
  val dataCollectionUiState by mobile.viewModels.dataCollection.uiState.collectAsState()
  // LocalClipboard has no commonMain ClipEntry factory yet.
  @Suppress("DEPRECATION") val clipboardManager = LocalClipboardManager.current
  val apkUrl = apkDownloadUrl(hostOrigin)
  val iosUrl = iosDownloadUrl(hostOrigin)

  Box(
    modifier =
      Modifier.fillMaxSize()
        .padding(horizontal = 16.dp, vertical = 8.dp)
        .verticalScroll(verticalScrollState)
        .horizontalScroll(horizontalScrollState),
    contentAlignment = Alignment.TopCenter,
  ) {
    DevicePreviewFrame(
      deviceTitle =
        "Collect One ${uiState.deviceFormFactor.label} UI • ${mobile.currentRoute.appScreen.deviceFrameTitle}",
      isDarkTheme = settingsUiState.isDarkTheme,
      isScreenTransparent = isMapShowing,
      isTopInsetTransparent = mobile.isMainMapScreenShowing(dataCollectionUiState),
      formFactor = uiState.deviceFormFactor,
      orientation = uiState.deviceOrientation,
      onSelectFormFactor = workbench::selectDeviceFormFactor,
      onRotateDevice = workbench::rotateDevice,
      onToggleDarkTheme = settings::toggleDarkTheme,
      isAirplaneMode = uiState.isAirplaneMode,
      onToggleAirplaneMode = workbench::toggleAirplaneMode,
      onWalkCloser =
        mapUiState.activeNavigation
          ?.takeIf { !it.vector.isArrived }
          ?.let { { surveyMap.stepUserTowardNavigationTarget() } },
      onCopyApkLink =
        if (apkUrl.isNotEmpty()) {
          { clipboardManager.setText(AnnotatedString(apkUrl)) }
        } else null,
      onCopyIosLink =
        if (iosUrl.isNotEmpty()) {
          { clipboardManager.setText(AnnotatedString(iosUrl)) }
        } else null,
    ) {
      MobileScreenHost(mobile)
    }
  }
}

/** Name of this screen in the device frame's title bar (the workbench's English developer UI). */
private val AppScreen.deviceFrameTitle: String
  get() =
    when (this) {
      AppScreen.SIGN_IN -> "Sign In"
      AppScreen.TERMS_OF_SERVICE -> "Terms of Service"
      AppScreen.DOWNLOAD_SURVEY -> "Download Survey"
      AppScreen.MAIN_SURVEY -> "Main Survey (Map & List)"
    }
