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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Dp
import kotlin.test.Test
import kotlin.test.assertTrue

/** A selected map feature's details scroll to their end inside the expanded bottom sheet. */
@OptIn(ExperimentalTestApi::class)
class MainSurveySheetScrollTest {
  private companion object {
    /** A sample map feature with enough details to need scrolling. */
    const val NYR_104 = "00000007-640e-5ed7-9874-4d6b6717f21d"
  }

  @Test
  fun expandedFeatureSheet_scrollsToItsEndWithTheMouseWheel() = runMobileNavigationTest {
    val state = show()
    it.runOnIdle {
      state.viewModels.surveyMap.selectEntity(NYR_104)
      state.viewModels.surveyMap.updateEntityBottomSheetExpanded(true)
    }
    it.waitForIdle()
    val screenHeight = it.onRoot().getUnclippedBoundsInRoot().bottom
    fun linkBottom(): Dp =
      it.onNodeWithText("History (", substring = true).getUnclippedBoundsInRoot().bottom

    val before = linkBottom()
    assertTrue(before > screenHeight, "the link starts below the screen ($before of $screenHeight)")

    it.onRoot().performMouseInput {
      moveTo(Offset(width / 2f, height * 0.6f))
      repeat(40) { scroll(3f) }
    }
    it.waitForIdle()

    assertTrue(linkBottom() <= screenHeight, "wheel: link at ${linkBottom()} of $screenHeight")
  }

  @Test
  fun expandedFeatureSheet_scrollsToItsEndByTouch() = runMobileNavigationTest {
    val state = show()
    it.runOnIdle {
      state.viewModels.surveyMap.selectEntity(NYR_104)
      state.viewModels.surveyMap.updateEntityBottomSheetExpanded(true)
    }
    it.waitForIdle()
    val screenHeight = it.onRoot().getUnclippedBoundsInRoot().bottom
    fun linkBottom(): Dp =
      it.onNodeWithText("History (", substring = true).getUnclippedBoundsInRoot().bottom

    for (i in 1..6) {
      it.onRoot().performTouchInput {
        swipe(Offset(width / 2f, height * 0.8f), Offset(width / 2f, height * 0.4f), 300)
      }
      it.waitForIdle()
    }

    assertTrue(linkBottom() <= screenHeight, "touch: link at ${linkBottom()} of $screenHeight")
  }
}
