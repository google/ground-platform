/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.devtools.prototypeapp.ui.dashboard

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertFalse
import org.groundplatform.v2.devtools.prototypeapp.PrototypeAppState

/**
 * Collapsing the web dashboard's data table panel must keep the table rows composed while the panel
 * animates down so the header and rows slide down together rather than the rows disappearing
 * immediately.
 */
@OptIn(ExperimentalTestApi::class)
class DashboardDataTablesPanelAnimationTest {

  @Test
  fun collapsingTable_keepsRowsComposedDuringCollapseAnimation() =
    runDesktopComposeUiTest(width = 1200, height = 600) {
      val state = PrototypeAppState()
      state.updateDashboardTableExpanded(true)
      val firstRowLabel = state.entities.first().label

      setContent {
        MaterialTheme {
          DashboardDataTablesPanel(state = state, expandedTableHeight = 240.dp)
        }
      }
      waitForIdle()
      onNodeWithText(firstRowLabel).assertExists()

      mainClock.autoAdvance = false
      onNodeWithContentDescription("Collapse table").performClick()
      assertFalse(state.isDashboardTableExpanded)

      // Advance partway through the collapse animation: rows must still be composed as the panel
      // slides down.
      mainClock.advanceTimeByFrame()
      mainClock.advanceTimeBy(64)
      onNodeWithText(firstRowLabel).assertExists()

      // Once the collapse animation finishes, the table body uncomposes.
      mainClock.advanceTimeBy(1_000)
      mainClock.autoAdvance = true
      waitForIdle()
      onNodeWithText(firstRowLabel).assertDoesNotExist()
    }
}
