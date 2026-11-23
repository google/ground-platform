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
package org.openforis.collectone.v2.web.ui.dashboard

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.core.forms.ui.AppTheme
import org.openforis.collectone.v2.web.WebDashboardDestination
import org.openforis.collectone.v2.web.navigation.RecordingPageNavigation
import org.openforis.collectone.v2.web.ui.ProvideWebSession
import org.openforis.collectone.v2.web.ui.WebAppState

/**
 * Verifies that **Open in Google Earth** in the web dashboard's left side panel and data-table
 * **Download** menu is only shown when the survey / map layer has at least one Google Earth-enabled
 * form, while KML downloads remain available for viewing geometries.
 */
@OptIn(ExperimentalTestApi::class)
class DashboardGoogleEarthGatingTest {

  @Test
  fun openInGoogleEarth_shownForSurveyWithEarthForms_andHiddenWhenNoneEnabled() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val state = WebAppState(dataHolder = sampleDataHolder())

      setContent {
        AppTheme {
          ProvideWebSession(state) {
            WebDashboardDestination(
              web = state,
              navigation = RecordingPageNavigation(),
              onSignOut = {},
            )
          }
        }
      }
      waitForIdle()

      // Default Kenya Coffee survey has Google Earth-enabled forms -> side panel shows the button.
      assertTrue(state.viewModels.dashboard.uiState.value.hasGoogleEarthForms)
      onNodeWithText("Open in Google Earth").assertExists()

      // Expanding the data table auto-collapses the side panel; its Download menu also offers it.
      state.viewModels.dashboard.showDatasetInTable("coffee_parcels")
      waitForIdle()
      onNodeWithText("Download").performClick()
      waitForIdle()
      assertTrue(onAllNodesWithText("Open in Google Earth").fetchSemanticsNodes().isNotEmpty())
      onNodeWithText("Download KML (Google Earth)").assertExists()

      // Dismiss dropdown and switch to Simple Point & Land Use Survey (no Earth forms).
      onNodeWithText("Download GeoJSON").performClick()
      waitForIdle()
      state.viewModels.dashboard.openSurveyOnWeb("00000036-1a1c-5c51-997f-28b67d813aa3")
      waitForIdle()

      // Left side panel is expanded again, and "Open in Google Earth" is hidden.
      assertTrue(state.viewModels.dashboard.uiState.value.isSidePanelExpanded)
      assertFalse(state.viewModels.dashboard.uiState.value.hasGoogleEarthForms)
      onNodeWithText("Open in Google Earth").assertDoesNotExist()

      // Data table Download menu hides "Open in Google Earth" while keeping KML / GeoJSON exports.
      state.viewModels.dashboard.showDatasetInTable("land_use_observations")
      waitForIdle()
      onNodeWithText("Download").performClick()
      waitForIdle()
      onNodeWithText("Open in Google Earth").assertDoesNotExist()
      onNodeWithText("Download GeoJSON").assertExists()
      onNodeWithText("Download KML (Google Earth)").assertExists()
      onNodeWithText("Download survey KML (all layers)").assertExists()
    }
}
