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
package org.openforis.collectone.v2.mobile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.openforis.collectone.v2.app.ui.common.LocalMobileTopInset
import org.openforis.collectone.v2.app.ui.common.ProvideAppLocale
import org.openforis.collectone.v2.core.forms.ui.AppTheme
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.mobile.ui.MobileAppState
import org.openforis.collectone.v2.mobile.ui.onboarding.MobileScreenHost

/**
 * Root of the Collect One mobile app, shared by every mobile host (`androidApp`, `iosApp`, and the
 * Wasm mobile build): `Sign In` → `Terms of Service` → `Download survey` → `Main Survey UI`.
 *
 * Hosts build [state] with [MobileAppSession], whose Koin graph the screens get their ViewModels
 * from. The screens are destinations of one navigation graph driven by [navController], which also
 * handles the system Back gesture; hosts pass their own controller to bind it to the browser
 * history. The root surface turns transparent while the basemap shows, because on web the Mapbox
 * basemap renders behind the Compose canvas. On the main map screen, the top safe-drawing inset is
 * transparent and passed down via [LocalMobileTopInset] so the map extends edge-to-edge behind the
 * status bar while floating overlays stay below the inset.
 *
 * Hover feedback (the pointing-hand cursor and the hover highlight around controls) is turned off
 * for the whole mobile app; it belongs to the web console, where a mouse hovers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileApp(
  state: MobileAppState,
  navController: NavHostController = rememberNavController(),
) {
  val settingsUiState by state.viewModels.settings.uiState.collectAsState()
  val dataCollectionUiState by state.viewModels.dataCollection.uiState.collectAsState()
  ProvideAppLocale(settingsUiState.languageCode) {
    // The mobile app is touch-first, including when it runs in a browser for testing: no hand
    // cursor and no hover highlight, which would stick to the last tapped control.
    AppTheme(
      darkTheme = settingsUiState.isDarkTheme,
      interactivePointerIcon = PointerIcon.Default,
    ) {
      CompositionLocalProvider(LocalRippleConfiguration provides TouchRippleConfiguration) {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color =
            if (state.isMapShowing(dataCollectionUiState)) Color.Transparent
            else MaterialTheme.colorScheme.surface,
        ) {
          val isTopInsetTransparent = state.isMainMapScreenShowing(dataCollectionUiState)
          val safeDrawingInsets = WindowInsets.safeDrawing
          val topInsetPadding =
            if (isTopInsetTransparent) {
              safeDrawingInsets.asPaddingValues().calculateTopPadding()
            } else {
              0.dp
            }
          val appliedInsets =
            if (isTopInsetTransparent) {
              safeDrawingInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
            } else {
              safeDrawingInsets
            }
          CompositionLocalProvider(LocalMobileTopInset provides topInsetPadding) {
            Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(appliedInsets)) {
              MobileScreenHost(state, navController)
            }
          }
        }
      }
    }
  }
}

/**
 * Material 3's ripple with its default pressed, focused, and dragged highlights and no hover
 * highlight.
 */
@OptIn(ExperimentalMaterial3Api::class)
internal val TouchRippleConfiguration =
  RippleConfiguration(
    rippleAlpha =
      RippleAlpha(
        draggedAlpha = 0.16f,
        focusedAlpha = 0.10f,
        hoveredAlpha = 0f,
        pressedAlpha = 0.10f,
      )
  )
