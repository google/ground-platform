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

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

/**
 * The dispatcher ViewModels run their work on unless a test passes another one.
 *
 * It's immediate, so a write to the local data store (which never suspends in memory) completes,
 * and its result reaches the ViewModel's `uiState`, before the action that started it returns.
 * Views rely on that to show the outcome of an action in the same frame.
 */
val ViewModelDispatcher: CoroutineDispatcher = Dispatchers.Unconfined

/**
 * The `viewModelScope` of a ViewModel that runs its work on [dispatcher]: a [SupervisorJob],
 * cancelled when the ViewModel is cleared. Pass the scope to the `ViewModel(viewModelScope)`
 * constructor.
 *
 * A ViewModel owned by another ViewModel (rather than by a `ViewModelStore`) passes its owner's job
 * as [parent], so it's cancelled with its owner.
 */
fun viewModelScopeOn(dispatcher: CoroutineDispatcher, parent: Job? = null): CoroutineScope =
  CoroutineScope(SupervisorJob(parent) + dispatcher)
