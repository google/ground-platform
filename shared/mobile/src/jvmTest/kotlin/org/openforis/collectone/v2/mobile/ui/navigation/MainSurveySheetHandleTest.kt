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

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What tops the Main Survey's bottom sheet once it has settled: the drag handle while it peeks, and
 * a way back down instead of the handle once it covers the screen.
 */
@OptIn(ExperimentalTestApi::class)
class MainSurveySheetHandleTest {
  @Test
  fun list_showsTheHandleWhilePeekingAndACloseButtonUnderTheSearchBarOnceExpanded() =
    runMobileNavigationTest {
      val state = show()
      val screenHeight = it.onRoot().getUnclippedBoundsInRoot().bottom
      fun handleTop() =
        it
          .onNodeWithTag(SHEET_DRAG_HANDLE_TAG, useUnmergedTree = true)
          .getUnclippedBoundsInRoot()
          .top

      // Peeking: on the sheet's edge at the bottom of the screen, not pushed down inside it.
      val peekingTop = handleTop()
      assertTrue(peekingTop >= screenHeight - 104.dp, "peeking handle at $peekingTop")
      assertTrue(peekingTop < screenHeight - 60.dp, "peeking handle at $peekingTop")

      it.runOnIdle { state.viewModels.surveyMap.updateEntityBottomSheetExpanded(true) }
      it.waitForIdle()

      // Expanded: no handle; the Close button sits right under the floating search bar, which
      // reserves 66 dp.
      it.onAllNodesWithTag(SHEET_DRAG_HANDLE_TAG, useUnmergedTree = true).assertCountEquals(0)
      val close = it.onNodeWithContentDescription(COLLAPSE_LIST)
      val closeTop = close.getUnclippedBoundsInRoot().top
      assertTrue(closeTop >= 66.dp, "close button at $closeTop")
      assertTrue(closeTop < 66.dp + 40.dp, "close button at $closeTop")

      close.performClick()
      it.waitForIdle()

      assertFalse(state.viewModels.surveyMap.uiState.value.isEntityBottomSheetExpanded)
      assertTrue(handleTop() >= screenHeight - 104.dp, "handle after collapsing at ${handleTop()}")
    }

  @Test
  fun selectedFeature_hasNoHandleOnceExpanded_andItsBackArrowIsInItsHeader() =
    runMobileNavigationTest {
      val state = show()
      it.runOnIdle { state.viewModels.surveyMap.selectEntity(NYR_104) }
      it.waitForIdle()

      // Peeking: the handle, and Back next to the feature's name.
      it.onNodeWithTag(SHEET_DRAG_HANDLE_TAG, useUnmergedTree = true).assertExists()
      it.onNodeWithContentDescription(BACK_TO_FEATURES).assertExists()

      it.runOnIdle { state.viewModels.surveyMap.updateEntityBottomSheetExpanded(true) }
      it.waitForIdle()

      it.onAllNodesWithTag(SHEET_DRAG_HANDLE_TAG, useUnmergedTree = true).assertCountEquals(0)
      // No Close button either: Back returns to the list.
      it.onAllNodesWithContentDescription(COLLAPSE_LIST).assertCountEquals(0)
      val backTop = it.onNodeWithContentDescription(BACK_TO_FEATURES).getUnclippedBoundsInRoot().top
      assertTrue(backTop >= 66.dp, "back arrow at $backTop")

      it.onNodeWithContentDescription(BACK_TO_FEATURES).performClick()
      it.waitForIdle()

      assertTrue(state.viewModels.dataCollection.uiState.value.selectedEntityId == null)
    }

  private companion object {
    const val NYR_104 = "00000007-640e-5ed7-9874-4d6b6717f21d"
    const val COLLAPSE_LIST = "Collapse list sheet to map"
    const val BACK_TO_FEATURES = "Back to all map features"
  }
}
