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
package org.openforis.collectone.v2.app.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps [key] of [savedState] equal to the [value] of the latest [state] for as long as this
 * ViewModel lives, so Android restores it after process death. The ViewModel reads it back when it
 * builds its initial state.
 *
 * Only save small UI state the user would otherwise lose (a search query, a selection); data comes
 * back from the local data store.
 */
fun <S, T> ViewModel.saveInto(
  savedState: SavedStateHandle,
  key: String,
  state: Flow<S>,
  value: (S) -> T,
) {
  viewModelScope.launch { state.map(value).distinctUntilChanged().collect { savedState[key] = it } }
}
