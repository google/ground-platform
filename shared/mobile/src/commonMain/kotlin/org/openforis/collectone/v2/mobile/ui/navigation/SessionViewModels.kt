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
package org.openforis.collectone.v2.mobile.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import org.koin.compose.viewmodel.koinViewModel

/**
 * The owner of the session's ViewModels: the host's (the Android activity, or the browser window),
 * where `MobileAppSession` gets them. [MobileNavHost] provides it to its destinations, whose own
 * `LocalViewModelStoreOwner` is their back stack entry.
 */
internal val LocalSessionViewModelStoreOwner =
  staticCompositionLocalOf<ViewModelStoreOwner?> { null }

/**
 * A ViewModel a destination shares with the rest of the app, from the Koin graph: the instance the
 * session coordinates (its cross-feature events, such as navigation), scoped to the host like the
 * one navigation graph, rather than a new one per back stack entry.
 */
@Composable
internal inline fun <reified T : ViewModel> koinSessionViewModel(): T =
  koinViewModel(
    viewModelStoreOwner =
      checkNotNull(LocalSessionViewModelStoreOwner.current) {
        "Session ViewModels are only available inside MobileNavHost"
      }
  )
