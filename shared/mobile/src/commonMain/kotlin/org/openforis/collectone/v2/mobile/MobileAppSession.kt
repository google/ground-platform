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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import org.koin.compose.KoinContext
import org.koin.compose.viewmodel.koinViewModel
import org.openforis.collectone.v2.app.di.AppDataHolder
import org.openforis.collectone.v2.app.ui.common.LocalProfileImageCache
import org.openforis.collectone.v2.app.ui.common.LocalProfileImageLoader
import org.openforis.collectone.v2.app.ui.common.ProfileImageCache
import org.openforis.collectone.v2.app.ui.koinAppViewModels
import org.openforis.collectone.v2.app.ui.map.LocalSurveyMapThumbnailRepository
import org.openforis.collectone.v2.app.ui.map.LocalSurveyThumbnailTileCache
import org.openforis.collectone.v2.app.ui.map.SurveyThumbnailTileCache
import org.openforis.collectone.v2.mobile.ui.MobileAppState
import org.openforis.collectone.v2.mobile.ui.navigation.MainSurveyViewModel
import org.openforis.collectone.v2.mobile.ui.navigation.MobileRoute
import org.openforis.collectone.v2.mobile.ui.offlinemaps.OfflineAreaSelectionViewModel

/**
 * Builds the [MobileAppState] of a mobile host over [dataHolder]'s Koin graph and passes it to
 * [content]. Its ViewModels come from `koinViewModel()`, so they outlive the session across
 * configuration changes; the session itself stops routing their events when it leaves composition.
 *
 * Without an [initialRoute], it starts where the data on the device leaves off ([launchRoute]):
 * sign-in or the terms until the user is past onboarding, then the survey they had open, the
 * surveys on the device, or Download survey. It shows nothing until the session is restored and the
 * signed-in user's stored data is in the local store (from the device's local database, which on
 * the web opens asynchronously), but never waits for the network: the sync continues in the
 * background and the screens update as data arrives. A route shown before the launch (on a reload,
 * or after Android process death) comes back only if it doesn't need another survey
 * ([MobileLaunch.mayRestore]).
 */
@Composable
fun MobileAppSession(
  dataHolder: AppDataHolder,
  initialRoute: MobileRoute? = null,
  content: @Composable (MobileAppState) -> Unit,
) {
  // Provides this session's isolated Koin instance rather than a global startKoin() graph.
  @Suppress("DEPRECATION")
  KoinContext(dataHolder.koin) {
    // Fixed once known: later sign-ins and sign-outs navigate within the session.
    val launch =
      if (initialRoute != null) {
        remember(initialRoute) { MobileLaunch.at(initialRoute) }
      } else {
        // One launch per graph: a host that starts another graph (another user data service)
        // launches over it again.
        val launchViewModel =
          koinViewModel<MobileLaunchViewModel>(key = "MobileLaunch@${dataHolder.hashCode()}")
        val restoredLaunch by launchViewModel.launch.collectAsState()
        restoredLaunch
      }
    if (launch != null) MobileAppSessionContent(dataHolder, launch, content)
  }
}

@Composable
private fun MobileAppSessionContent(
  dataHolder: AppDataHolder,
  launch: MobileLaunch,
  content: @Composable (MobileAppState) -> Unit,
) {
  val viewModels = koinAppViewModels()
  val mainSurvey = koinViewModel<MainSurveyViewModel>()
  val offlineAreaSelection = koinViewModel<OfflineAreaSelectionViewModel>()
  val state =
    remember(dataHolder, viewModels, mainSurvey, offlineAreaSelection) {
      MobileAppState(
        initialRoute = launch.startRoute,
        dataHolder = dataHolder,
        viewModels = viewModels,
        mainSurvey = mainSurvey,
        offlineAreaSelection = offlineAreaSelection,
        mayRestoreRoute = launch::mayRestore,
      )
    }
  val thumbnailTileCache = remember(dataHolder) { SurveyThumbnailTileCache() }
  val profileImageCache = remember(dataHolder) { ProfileImageCache() }
  DisposableEffect(state) { onDispose { state.close() } }
  CompositionLocalProvider(
    LocalSurveyMapThumbnailRepository provides dataHolder.koin.get(),
    LocalSurveyThumbnailTileCache provides thumbnailTileCache,
    LocalProfileImageLoader provides dataHolder.koin.get(),
    LocalProfileImageCache provides profileImageCache,
  ) {
    content(state)
  }
}
