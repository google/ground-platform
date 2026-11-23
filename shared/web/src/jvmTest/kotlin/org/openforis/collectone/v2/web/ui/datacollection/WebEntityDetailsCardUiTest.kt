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
package org.openforis.collectone.v2.web.ui.datacollection

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.domain.model.geometryType
import org.openforis.collectone.v2.core.forms.ui.AppTheme
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.web.WebDashboardDestination
import org.openforis.collectone.v2.web.navigation.RecordingPageNavigation
import org.openforis.collectone.v2.web.ui.ProvideWebSession
import org.openforis.collectone.v2.web.ui.WebAppState
import org.openforis.collectone.v2.web.ui.dashboard.DASHBOARD_RIGHT_CARD_TAG

/**
 * Verifies the web dashboard entity details card footer actions: **Analyze imagery** sits beside
 * **Show in table** for points and polygons (instead of an icon button in the card header) and is
 * omitted for line features.
 */
@OptIn(ExperimentalTestApi::class)
class WebEntityDetailsCardUiTest {

  private fun ComposeUiTest.showDashboard(state: WebAppState) {
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
  }

  @Test
  fun polygonFeature_showsAnalyzeImageryRightOfShowInTable_andOmitsHeaderButton() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val state = WebAppState(dataHolder = sampleDataHolder())
      val polygon =
        state.viewModels.dataCollection.uiState.value.entities.first {
          it.geometryType == GeometryType.POLYGON
        }
      state.viewModels.surveyMap.selectEntity(polygon.id)
      showDashboard(state)

      onNodeWithContentDescription("Time series").assertDoesNotExist()
      onNodeWithContentDescription("Analyze imagery").assertDoesNotExist()

      val showInTable = onNodeWithText("Show in table").performScrollTo()
      val analyzeImagery = onNodeWithText("Analyze imagery").performScrollTo()
      waitForIdle()

      val cardBounds = onNodeWithTag(DASHBOARD_RIGHT_CARD_TAG).getUnclippedBoundsInRoot()
      val showBounds = showInTable.getUnclippedBoundsInRoot()
      val analyzeBounds = analyzeImagery.getUnclippedBoundsInRoot()
      assertEquals(
        showBounds.top,
        analyzeBounds.top,
        "Analyze imagery shares a row with Show in table",
      )
      assertTrue(
        analyzeBounds.left > showBounds.right,
        "Analyze imagery sits to the right of Show in table ($showBounds vs $analyzeBounds)",
      )
      assertTrue(
        analyzeBounds.right <= cardBounds.right,
        "Analyze imagery fits inside the right card ($analyzeBounds vs $cardBounds)",
      )

      analyzeImagery.performClick()
      waitForIdle()

      assertTrue(state.viewModels.dashboard.uiState.value.isTimeSeriesOpen)
      assertFalse(state.viewModels.dashboard.uiState.value.isDashboardTableExpanded)
    }

  @Test
  fun pointFeature_showsAnalyzeImageryRightOfShowInTable() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val state = WebAppState(dataHolder = sampleDataHolder())
      val point =
        state.viewModels.dataCollection.uiState.value.entities.first {
          it.geometryType == GeometryType.POINT
        }
      state.viewModels.surveyMap.selectEntity(point.id)
      showDashboard(state)

      val showBounds = onNodeWithText("Show in table").performScrollTo().getUnclippedBoundsInRoot()
      val analyzeBounds =
        onNodeWithText("Analyze imagery").performScrollTo().getUnclippedBoundsInRoot()
      assertEquals(
        showBounds.top,
        analyzeBounds.top,
        "Analyze imagery shares a row with Show in table",
      )
      assertTrue(analyzeBounds.left > showBounds.right)

      onNodeWithText("Analyze imagery").performClick()
      waitForIdle()

      assertTrue(state.viewModels.dashboard.uiState.value.isTimeSeriesOpen)
      assertFalse(state.viewModels.dashboard.uiState.value.isDashboardTableExpanded)
    }

  @Test
  fun lineFeature_hidesAnalyzeImageryButton() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val state = WebAppState(dataHolder = sampleDataHolder())
      val line =
        state.viewModels.dataCollection.uiState.value.entities.first {
          it.geometryType == GeometryType.LINE_STRING
        }
      state.viewModels.surveyMap.selectEntity(line.id)
      showDashboard(state)

      onNodeWithText("Show in table").performScrollTo().assertExists()
      onNodeWithText("Analyze imagery").assertDoesNotExist()
    }
}
