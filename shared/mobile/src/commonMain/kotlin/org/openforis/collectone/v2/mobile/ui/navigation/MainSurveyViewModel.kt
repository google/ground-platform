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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import org.openforis.collectone.v2.app.ui.navigation.MainSurveyViewMode

/**
 * State of the Main Survey that isn't a route but should survive Android process death: whether it
 * shows the map or the list ([viewMode]), kept in [savedState]. Where the user is (the Main Survey,
 * a drawer page, or the form) is the navigation back stack's job; `MobileAppState` decides when the
 * view mode changes.
 */
class MainSurveyViewModel(private val savedState: SavedStateHandle = SavedStateHandle()) :
  ViewModel() {

  private var viewModeState by
    mutableStateOf(savedState.restoreViewMode() ?: MainSurveyViewMode.MAP)

  /** Whether the Main Survey shows the map or the list. */
  var viewMode: MainSurveyViewMode
    get() = viewModeState
    set(value) {
      viewModeState = value
      savedState[VIEW_MODE_KEY] = value.name
    }

  private companion object {
    const val VIEW_MODE_KEY = "mainViewMode"

    /** The saved view mode, or null when none (or an unknown one) is saved. */
    fun SavedStateHandle.restoreViewMode(): MainSurveyViewMode? =
      get<String>(VIEW_MODE_KEY)?.let { name ->
        MainSurveyViewMode.entries.firstOrNull { it.name == name }
      }
  }
}
