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
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.ui.navigation.EntityDetailsPane

/** A map feature's history (its submissions) as a full screen over the Main Survey. */
@OptIn(ExperimentalTestApi::class)
class FeatureHistoryScreenTest {
  @Test
  fun history_coversTheScreenAndListsTheFeaturesSubmissions() = runMobileNavigationTest {
    val state = show()
    it.runOnIdle {
      state.viewModels.surveyMap.selectEntity(NYR_104)
      state.viewModels.dashboard.showEntitySubmissions()
    }
    it.waitForIdle()

    // Its title bar is at the top of the screen, where the search bar is otherwise.
    val titleTop = it.onNodeWithText("History").getUnclippedBoundsInRoot().top
    assertTrue(titleTop < 66.dp, "history title at $titleTop")
    it.onNodeWithContentDescription("Back to details").assertExists()
    assertTrue(
      it.onAllNodesWithContentDescription("Submission details").fetchSemanticsNodes().isNotEmpty(),
      "the sample feature lists its submissions",
    )
  }

  @Test
  fun history_opensASubmissionAndGoesBackOneLevelAtATime() = runMobileNavigationTest {
    val state = show()
    it.runOnIdle {
      state.viewModels.surveyMap.selectEntity(NYR_104)
      state.viewModels.dashboard.showEntitySubmissions()
    }
    it.waitForIdle()

    it.onAllNodesWithContentDescription("Submission details").onFirst().performClick()
    it.waitForIdle()

    assertTrue(state.viewModels.dataCollection.uiState.value.selectedSubmissionId != null)
    it.onNodeWithText("History").assertExists()

    it.onNodeWithContentDescription("Back to submissions").performClick()
    it.waitForIdle()

    assertNull(state.viewModels.dataCollection.uiState.value.selectedSubmissionId)
    assertEquals(
      EntityDetailsPane.SUBMISSIONS,
      state.viewModels.dashboard.uiState.value.entityDetailsPane,
    )

    it.onNodeWithContentDescription("Back to details").performClick()
    it.waitForIdle()

    assertEquals(
      EntityDetailsPane.PROPERTIES,
      state.viewModels.dashboard.uiState.value.entityDetailsPane,
    )
    assertEquals(NYR_104, state.viewModels.dataCollection.uiState.value.selectedEntityId)
    it.onAllNodesWithContentDescription("Back to details").assertCountEquals(0)
  }

  private companion object {
    const val NYR_104 = "00000007-640e-5ed7-9874-4d6b6717f21d"
  }
}
