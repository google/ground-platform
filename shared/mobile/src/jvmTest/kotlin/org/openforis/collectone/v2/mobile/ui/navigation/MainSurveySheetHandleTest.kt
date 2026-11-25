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
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

/** Where the Main Survey's bottom sheet shows its drag handle once the sheet has settled. */
@OptIn(ExperimentalTestApi::class)
class MainSurveySheetHandleTest {
  @Test
  fun handle_restsOnThePeekingSheetAndUnderTheSearchBarOnceExpanded() = runMobileNavigationTest {
    val state = show()
    val screenHeight = it.onRoot().getUnclippedBoundsInRoot().bottom
    fun handleTop() =
      it.onNodeWithTag(SHEET_DRAG_HANDLE_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot().top

    // Peeking: on the sheet's edge at the bottom of the screen, not pushed down inside it.
    val peekingTop = handleTop()
    assertTrue(peekingTop >= screenHeight - 104.dp, "peeking handle at $peekingTop")
    assertTrue(peekingTop < screenHeight - 60.dp, "peeking handle at $peekingTop")

    it.runOnIdle { state.viewModels.surveyMap.updateEntityBottomSheetExpanded(true) }
    it.waitForIdle()

    // Expanded: right under the floating search bar, which reserves 66 dp.
    val expandedTop = handleTop()
    assertTrue(expandedTop >= 66.dp, "expanded handle at $expandedTop")
    assertTrue(expandedTop < 66.dp + 40.dp, "expanded handle at $expandedTop")

    it.runOnIdle { state.viewModels.surveyMap.updateEntityBottomSheetExpanded(false) }
    it.waitForIdle()

    assertTrue(handleTop() >= screenHeight - 104.dp, "handle after collapsing at ${handleTop()}")
  }
}
