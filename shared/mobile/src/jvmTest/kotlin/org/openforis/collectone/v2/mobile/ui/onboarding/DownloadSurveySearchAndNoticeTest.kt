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
package org.openforis.collectone.v2.mobile.ui.onboarding

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.openforis.collectone.v2.mobile.ui.navigation.MobileRoute
import org.openforis.collectone.v2.mobile.ui.navigation.runMobileNavigationTest

/** The Download survey screen's search field and its transient notice. */
@OptIn(ExperimentalTestApi::class)
class DownloadSurveySearchAndNoticeTest {

  @Test
  fun searchField_isCompactAndItsClearButtonEmptiesTheQuery() = runMobileNavigationTest {
    val state = show(MobileRoute.DownloadSurvey)
    val searchField = it.onNodeWithContentDescription("Search surveys")
    it.onNodeWithContentDescription("Clear Search").assertDoesNotExist()

    searchField.performTextReplacement("Kenya")
    it.waitForIdle()
    assertEquals("Kenya", state.viewModels.onboarding.uiState.value.searchQuery)

    it.onNodeWithContentDescription("Clear Search").assertHeightIsEqualTo(40.dp).performClick()
    it.waitForIdle()
    assertEquals("", state.viewModels.onboarding.uiState.value.searchQuery)
    it.onNodeWithContentDescription("Clear Search").assertDoesNotExist()
  }

  @Test
  fun searchField_hidesWhenTheListScrollsDownAndReturnsWhenItScrollsBack() =
    runMobileNavigationTest {
      show(MobileRoute.DownloadSurvey)
      val searchField = it.onNodeWithContentDescription("Search surveys")
      searchField.assertIsDisplayed()

      it.onRoot().performTouchInput { swipeUp(startY = bottom * 0.8f, endY = bottom * 0.3f) }
      it.waitForIdle()
      searchField.assertIsNotDisplayed()

      it.onRoot().performTouchInput { swipeDown(startY = bottom * 0.5f, endY = bottom * 0.7f) }
      it.waitForIdle()
      searchField.assertIsDisplayed()
    }

  @Test
  fun searchField_staysInViewWhileThereIsAQuery() = runMobileNavigationTest {
    show(MobileRoute.DownloadSurvey)
    val searchField = it.onNodeWithContentDescription("Search surveys")
    // One letter keeps enough surveys listed to scroll.
    searchField.performTextReplacement("a")
    it.waitForIdle()

    it.onRoot().performTouchInput { swipeUp(startY = bottom * 0.8f, endY = bottom * 0.3f) }
    it.waitForIdle()

    searchField.assertIsDisplayed()
  }

  @Test
  fun notice_showsInASnackbarAndClearsOnceItHasGone() = runMobileNavigationTest {
    val state = show(MobileRoute.DownloadSurvey)
    it.mainClock.autoAdvance = false

    it.runOnIdle { state.notices.show("Downloaded \"Kenya\" for offline use.") }
    it.mainClock.advanceTimeBy(1_000)
    it.onNodeWithText("Downloaded \"Kenya\" for offline use.").assertIsDisplayed()

    it.mainClock.advanceTimeBy(15_000)
    it.onNodeWithText("Downloaded \"Kenya\" for offline use.").assertDoesNotExist()
    assertNull(state.notices.notice.value)
  }
}
