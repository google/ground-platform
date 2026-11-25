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

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.ui.datacollection.SearchableListActions
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

  /** Hosts the search bar over [state], recording the sheet expansions it asks for. */
  private fun DesktopComposeUiTest.showSearchBar(
    state: MobileAppState,
    isSheetExpanded: MutableState<Boolean>,
    requestedExpansions: MutableList<Boolean>,
  ) {
    val listActions =
      object : SearchableListActions by state.searchableListActions {
        override fun updateEntityBottomSheetExpanded(expanded: Boolean) {
          requestedExpansions += expanded
          isSheetExpanded.value = expanded
        }
      }
    setContent {
      AppTheme {
        MobileMapSearchBar(
          uiState = state.collectMainSurveyUiState(),
          hasSelectedRecord = false,
          isSheetExpanded = isSheetExpanded.value,
          actions = state,
          listActions = listActions,
          dataCollectionActions = state.viewModels.dataCollection,
        )
      }
    }
    waitForIdle()
  }

  @Test
  fun clearingTheQuery_collapsesTheSheetThatTypingExpanded() =
    runDesktopComposeUiTest(width = 404, height = 840) {
      val state =
        MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
      val requestedExpansions = mutableListOf<Boolean>()
      showSearchBar(state, mutableStateOf(false), requestedExpansions)

      onNode(hasSetTextAction()).performTextInput("plot")
      waitForIdle()
      assertEquals(listOf(true), requestedExpansions)

      onNodeWithContentDescription("Clear Search").performClick()
      waitForIdle()
      assertEquals(listOf(true, false), requestedExpansions)
    }

  @Test
  fun deletingTheQuery_collapsesTheSheetThatTypingExpanded() =
    runDesktopComposeUiTest(width = 404, height = 840) {
      val state =
        MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
      val requestedExpansions = mutableListOf<Boolean>()
      showSearchBar(state, mutableStateOf(false), requestedExpansions)

      onNode(hasSetTextAction()).performTextInput("p")
      waitForIdle()
      onNode(hasSetTextAction()).performTextClearance()
      waitForIdle()

      assertEquals(listOf(true, false), requestedExpansions)
    }

  @Test
  fun clearingTheQuery_leavesASheetTheUserExpandedThemselves() =
    runDesktopComposeUiTest(width = 404, height = 840) {
      val state =
        MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
      val requestedExpansions = mutableListOf<Boolean>()
      showSearchBar(state, mutableStateOf(true), requestedExpansions)

      onNode(hasSetTextAction()).performTextInput("plot")
      waitForIdle()
      onNodeWithContentDescription("Clear Search").performClick()
      waitForIdle()

      assertEquals(emptyList(), requestedExpansions)
    }

  @Test
  fun clearingTheQuery_leavesASheetTheUserCollapsedWhileSearching() =
    runDesktopComposeUiTest(width = 404, height = 840) {
      val state =
        MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
      val requestedExpansions = mutableListOf<Boolean>()
      val isSheetExpanded = mutableStateOf(false)
      showSearchBar(state, isSheetExpanded, requestedExpansions)

      onNode(hasSetTextAction()).performTextInput("plot")
      waitForIdle()
      // The user drags the sheet back down.
      runOnIdle { isSheetExpanded.value = false }
      waitForIdle()
      onNodeWithContentDescription("Clear Search").performClick()
      waitForIdle()

      assertEquals(listOf(true), requestedExpansions)
    }
}
