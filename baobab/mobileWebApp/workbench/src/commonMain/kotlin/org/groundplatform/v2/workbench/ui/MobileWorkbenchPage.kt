/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.workbench.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.groundplatform.v2.app.ui.common.DevicePreviewFrame
import org.groundplatform.v2.core.forms.ui.GroundTheme
import org.groundplatform.v2.mobile.GroundMobileApp
import org.groundplatform.v2.mobile.ui.MobileAppState
import org.groundplatform.v2.mobile.ui.onboarding.MobileScreenHost

/**
 * Root of the Wasm mobile build (`mobileWebApp`). With [showChrome], the mobile app runs inside the
 * workbench's simulated device ([MobileWorkbenchPage]); without it, the mobile app fills the
 * browser window, for testing in a phone's browser.
 */
@Composable
fun MobileWorkbenchApp(
  state: WorkbenchState = remember { WorkbenchState(MobileAppState()) },
  showChrome: Boolean = true,
) {
  if (!showChrome) {
    GroundMobileApp(state.mobile)
    return
  }
  val mobile = state.mobile
  GroundTheme(darkTheme = mobile.isDarkTheme) {
    Surface(
      modifier = Modifier.fillMaxSize(),
      // The Mapbox basemap renders behind the Compose canvas, so the root surface must stay
      // transparent while the device preview shows it.
      color =
        if (mobile.isMapShowing) Color.Transparent
        else MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
      MobileWorkbenchPage(workbench = state, isMapShowing = mobile.isMapShowing)
    }
  }
}

/**
 * Mobile and tablet device preview stage: the workbench's simulated device around the mobile app.
 */
@Composable
fun MobileWorkbenchPage(
  workbench: WorkbenchState,
  isMapShowing: Boolean,
) {
  val verticalScrollState = rememberScrollState()
  val horizontalScrollState = rememberScrollState()
  val mobile = workbench.mobile

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
        "Open Foris Collect One ${workbench.deviceFormFactor.label} UI • ${mobile.currentScreen.title}",
      isDarkTheme = mobile.isDarkTheme,
      isScreenTransparent = isMapShowing,
      formFactor = workbench.deviceFormFactor,
      orientation = workbench.deviceOrientation,
      onSelectFormFactor = workbench::selectDeviceFormFactor,
      onRotateDevice = workbench::rotateDevice,
      onToggleDarkTheme = mobile::toggleDarkTheme,
      isAirplaneMode = workbench.isAirplaneMode,
      onToggleAirplaneMode = workbench::toggleAirplaneMode,
    ) {
      MobileScreenHost(mobile)
    }
  }
}
