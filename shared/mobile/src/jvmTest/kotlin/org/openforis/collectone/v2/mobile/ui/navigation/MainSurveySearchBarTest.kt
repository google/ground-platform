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
package org.openforis.collectone.v2.mobile.ui.navigation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.core.forms.ui.AppTheme
import org.openforis.collectone.v2.mobile.ui.MobileAppState
import org.openforis.collectone.v2.mobile.ui.collectMainSurveyUiState
import org.openforis.collectone.v2.mobile.ui.uploads.UploadStatusCounts

@OptIn(ExperimentalTestApi::class)
class MainSurveySearchBarTest {
  @Test
  fun uploadStatusPopup_hasLinkToUploadsScreen() =
    runDesktopComposeUiTest(width = 404, height = 840) {
      val state =
        MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
      var openedUploadsCount = 0
      val actions =
        object : MainSurveyActions by state {
          override fun drawerOpenUploads() {
            openedUploadsCount++
          }
        }

      setContent {
        AppTheme {
          val uiState = state.collectMainSurveyUiState()
          MobileMapSearchBar(
            uiState = uiState.copy(uploadStatusCounts = UploadStatusCounts()),
            hasSelectedRecord = false,
            isSheetExpanded = false,
            actions = actions,
            listActions = state.searchableListActions,
            dataCollectionActions = state.viewModels.dataCollection,
          )
        }
      }
      waitForIdle()

      onNodeWithContentDescription("All changes saved").performClick()
      waitForIdle()

      onNodeWithText("View uploads").assertExists().performClick()
      waitForIdle()

      assertEquals(1, openedUploadsCount)
    }
}
