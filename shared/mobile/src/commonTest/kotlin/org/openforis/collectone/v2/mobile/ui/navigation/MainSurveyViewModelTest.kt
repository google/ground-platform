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

import androidx.lifecycle.SavedStateHandle
import kotlin.test.Test
import kotlin.test.assertEquals
import org.openforis.collectone.v2.app.ui.navigation.MainSurveyViewMode

/** The Main Survey's map or list view survives Android process death through [SavedStateHandle]. */
class MainSurveyViewModelTest {

  @Test
  fun withoutSavedState_showsTheMap() {
    val mainSurvey = MainSurveyViewModel()

    assertEquals(MainSurveyViewMode.MAP, mainSurvey.viewMode)
  }

  @Test
  fun savedViewMode_isRestoredByTheNextViewModel() {
    val savedState = SavedStateHandle()
    MainSurveyViewModel(savedState).viewMode = MainSurveyViewMode.LIST

    val restored = MainSurveyViewModel(savedState)

    assertEquals(MainSurveyViewMode.LIST, restored.viewMode)
  }

  @Test
  fun unknownSavedViewMode_fallsBackToTheMap() {
    val savedState = SavedStateHandle(mapOf("mainViewMode" to "REMOVED_MODE"))

    val mainSurvey = MainSurveyViewModel(savedState)

    assertEquals(MainSurveyViewMode.MAP, mainSurvey.viewMode)
  }
}
