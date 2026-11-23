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
package org.openforis.collectone.v2.app.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive

/** Unit tests for [viewModelScopeOn] and [saveInto]. */
class ViewModelHelpersTest {

  @Test
  fun saveInto_mirrorsDistinctValuesUntilOwnerCancels() {
    val ownerJob = Job()
    val savedState = SavedStateHandle()
    val state = MutableStateFlow("initial")
    val vm = TestViewModel(ownerJob, savedState, state)

    assertEquals("initial", savedState["query"])

    state.value = "updated"
    assertEquals("updated", savedState["query"])

    ownerJob.cancel()
    state.value = "after-cancel"
    assertEquals("updated", savedState["query"])
    assertFalse(vm.isScopeActive)
  }

  private class TestViewModel(
    parent: Job,
    savedState: SavedStateHandle,
    state: MutableStateFlow<String>,
  ) : ViewModel(viewModelScopeOn(ViewModelDispatcher, parent)) {
    private val scope = viewModelScopeOn(ViewModelDispatcher, parent)
    val isScopeActive: Boolean
      get() = scope.isActive

    init {
      saveInto(savedState, "query", state) { it }
    }
  }
}
