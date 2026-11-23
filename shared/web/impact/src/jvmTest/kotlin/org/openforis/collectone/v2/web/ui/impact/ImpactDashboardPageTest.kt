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
package org.openforis.collectone.v2.web.ui.impact

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.seed.SampleOrganizationsData
import org.openforis.collectone.v2.web.ui.impact.ImpactViewModelTest.Companion.KENYA
import org.openforis.collectone.v2.web.ui.impact.ImpactViewModelTest.Companion.KFS
import org.openforis.collectone.v2.web.ui.impact.ImpactViewModelTest.Fixture

@OptIn(ExperimentalTestApi::class)
class ImpactDashboardPageTest {

  @Test
  fun navigation_switchesBetweenOverviewPlatformAndOrganization_andTogglesPublishedView() =
    runDesktopComposeUiTest(width = 1440, height = 960) {
      val f = Fixture()
      var openedOrganizationId: String? = null
      setContent {
        MaterialTheme {
          val uiState by f.viewModel.uiState.collectAsState()
          ImpactDashboardPage(
            uiState = uiState,
            actions = f.viewModel,
            sidePanelWidthDp = 260f,
            onSidePanelWidthChange = { _ -> Unit },
            header = { onChoose -> TextButton(onClick = onChoose) { Text("Header Choose") } },
            onOpenOrganization = { openedOrganizationId = it },
          )
        }
      }
      waitForIdle()

      // Overview shows the platform card and followed organizations.
      assertEquals(ImpactDashboardFocus.Overview, f.state.dashboard.focus)
      onAllNodesWithText("All of Collect One").onFirst().performClick()
      waitForIdle()
      assertEquals(ImpactDashboardFocus.Platform, f.state.dashboard.focus)
      onNodeWithText("By country and map area").assertExists()

      // Selecting a managed organization shows its full numbers and lets members preview published.
      onAllNodesWithText("Kenya Forest Service").onFirst().performClick()
      waitForIdle()
      assertEquals(ImpactDashboardFocus.OrganizationFocus(KFS), f.state.dashboard.focus)
      assertFalse(f.state.dashboard.isPreviewingPublished)

      onNode(isToggleable()).performClick()
      waitForIdle()
      assertTrue(f.state.dashboard.isPreviewingPublished)

      onNodeWithText("Organization page").performClick()
      waitForIdle()
      assertEquals(KFS, openedOrganizationId)

      onNodeWithText("Unfollow").performClick()
      waitForIdle()
      assertFalse(KFS in f.state.dashboard.selectedIds)
      assertEquals(ImpactDashboardFocus.Overview, f.state.dashboard.focus)
    }

  @Test
  fun chooseOrganizationsDialog_searchesAndSavesSelection() =
    runDesktopComposeUiTest(width = 1440, height = 960) {
      val f = Fixture()
      setContent {
        MaterialTheme {
          val uiState by f.viewModel.uiState.collectAsState()
          ImpactDashboardPage(
            uiState = uiState,
            actions = f.viewModel,
            sidePanelWidthDp = 260f,
            onSidePanelWidthChange = { _ -> Unit },
            header = { onChoose -> TextButton(onClick = onChoose) { Text("Header Choose") } },
          )
        }
      }
      waitForIdle()

      onNodeWithText("Header Choose").performClick()
      waitForIdle()

      onNode(hasSetTextAction()).performTextInput("zz-no-match")
      waitForIdle()
      onNodeWithText("No organizations match", substring = true).assertExists()

      onNode(hasSetTextAction()).performTextReplacement("Open Foris")
      waitForIdle()
      onNodeWithText("Open Foris Community").performClick()
      waitForIdle()

      onNodeWithText("Save").performClick()
      waitForIdle()
      assertTrue(SampleOrganizationsData.OPEN_FORIS_COMMUNITY in f.state.dashboard.selectedIds)
    }

  @Test
  fun surveyImpactPane_rendersLoadedBelowThresholdLoadingAndEmptyStates() =
    runDesktopComposeUiTest(width = 1280, height = 900) {
      val f = Fixture()
      f.viewModel.setOrganizationCountryFilter(KFS, "VN")
      val belowThreshold = assertNotNull(f.state.dashboard.organization(KFS)).published.summary
      val kenyaSummary = assertNotNull(f.state.surveys[KENYA])
      var mode by mutableStateOf(0)

      setContent {
        MaterialTheme {
          when (mode) {
            0 ->
              SurveyImpactPane(
                summary = kenyaSummary,
                isLoading = false,
                onDownload = { mode = 1 },
              )
            1 ->
              SurveyImpactPane(
                summary = belowThreshold,
                isLoading = false,
                onDownload = { Unit },
              )
            2 -> SurveyImpactPane(summary = null, isLoading = true, onDownload = { Unit })
            else -> SurveyImpactPane(summary = null, isLoading = false, onDownload = { Unit })
          }
        }
      }
      waitForIdle()
      onNodeWithText("Unique hectares mapped").assertExists()
      onNodeWithText("How the data was used").assertExists()

      onNodeWithText("Download PDF summary").performClick()
      waitForIdle()
      onNodeWithText("Not enough data to publish yet").assertExists()

      mainClock.autoAdvance = false
      runOnIdle { mode = 2 }
      mainClock.advanceTimeByFrame()
      onNodeWithText("Adding up", substring = true).assertExists()

      mainClock.autoAdvance = true
      runOnIdle { mode = 3 }
      waitForIdle()
      onNodeWithText("No numbers", substring = true).assertExists()
    }
}
