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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import org.koin.compose.KoinContext
import org.openforis.collectone.v2.app.di.AppDataHolder
import org.openforis.collectone.v2.app.ui.common.LocalProfileImageCache
import org.openforis.collectone.v2.app.ui.common.LocalProfileImageLoader
import org.openforis.collectone.v2.app.ui.common.ProfileImageCache
import org.openforis.collectone.v2.app.ui.koinAppViewModels
import org.openforis.collectone.v2.app.ui.map.LocalSurveyMapThumbnailRepository
import org.openforis.collectone.v2.app.ui.map.LocalSurveyThumbnailTileCache
import org.openforis.collectone.v2.app.ui.map.SurveyThumbnailTileCache
import org.openforis.collectone.v2.web.ui.WebAppState

/**
 * Builds the [WebAppState] of the web console over [dataHolder]'s Koin graph (made of `webModules`)
 * and passes it to [content]. Its ViewModels come from `koinViewModel()`, and the ViewModels that
 * the console's pages share live in the current `ViewModelStoreOwner` (the browser window). The
 * session stops routing their events when it leaves composition.
 */
@Composable
fun WebAppSession(dataHolder: AppDataHolder, content: @Composable (WebAppState) -> Unit) {
  // Provides this session's isolated Koin instance rather than a global startKoin() graph.
  @Suppress("DEPRECATION")
  KoinContext(dataHolder.koin) {
    val viewModels = koinAppViewModels(isAlwaysOnline = true)
    val window =
      checkNotNull(LocalViewModelStoreOwner.current) {
        "The web console needs a ViewModelStoreOwner (the browser window)"
      }
    val state =
      remember(dataHolder, viewModels, window) {
        WebAppState(dataHolder = dataHolder, viewModels = viewModels, sessionViewModels = window)
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
}
