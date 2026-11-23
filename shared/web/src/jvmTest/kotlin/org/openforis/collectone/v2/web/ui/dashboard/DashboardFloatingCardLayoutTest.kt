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
package org.openforis.collectone.v2.web.ui.dashboard

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.DpRect
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.domain.model.ImagerySourceType
import org.openforis.collectone.v2.app.domain.model.geometryType
import org.openforis.collectone.v2.app.ui.common.EARTH_ENGINE_MAP_BADGE_TAG
import org.openforis.collectone.v2.app.ui.common.EARTH_ENGINE_PANEL_BADGE_TAG
import org.openforis.collectone.v2.core.forms.ui.AppTheme
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.web.WebDashboardDestination
import org.openforis.collectone.v2.web.navigation.RecordingPageNavigation
import org.openforis.collectone.v2.web.ui.ProvideWebSession
import org.openforis.collectone.v2.web.ui.WebAppState
import org.openforis.collectone.v2.web.ui.common.WebFormPanelWidth
import org.openforis.collectone.v2.web.ui.timeSeries

/**
 * Verifies the web dashboard's panel docking and dedicated time series bottom sheet:
 * - Opening the bottom data table docks the right-hand panel (details card or open form) into a
 *   compact top-right pill so the table gets full width; expanding the right-hand panel docks the
 *   bottom data table, keeping only one open at a time.
 * - The bottom data table has no "Time series" tab; triggering Time series from the right-hand
 *   place card opens a dedicated bottom sheet that stays clear of the floating card / open form.
 *   With `DASHBOARD_LAYOUT_PNG` set to a `.png` path, it also writes a screenshot of each layout
 *   next to it.
 */
@OptIn(ExperimentalTestApi::class)
class DashboardFloatingCardLayoutTest {
  private val screenshotPath: String? = System.getenv("DASHBOARD_LAYOUT_PNG")

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

  private fun ComposeUiTest.bounds(tag: String): DpRect =
    onNodeWithTag(tag).getUnclippedBoundsInRoot()

  /**
   * Asserts the right-hand card floats in the upper-right corner beside a bottom panel (the docked
   * table bar or the dedicated time series bottom sheet), which keeps equal gutters and clears the
   * left-hand panel's collapse tab.
   */
  private fun ComposeUiTest.assertCardBesideBottomPanel(
    bottomPanelTag: String,
    expectCardBesideBottomHeight: Boolean,
  ) {
    val root = onRoot().getUnclippedBoundsInRoot()
    val card = bounds(DASHBOARD_RIGHT_CARD_TAG)
    val bottomPanel = bounds(bottomPanelTag)
    val collapseTab = onNodeWithContentDescription("Expand side panel").getUnclippedBoundsInRoot()
    assertEquals(root.right - DashboardOverlayMargin, card.right, "floats a margin from the edge")
    assertTrue(card.bottom <= root.bottom - DashboardOverlayMargin, "keeps its bottom margin")
    if (expectCardBesideBottomHeight) {
      assertTrue(card.bottom > bottomPanel.top, "runs alongside the bottom sheet")
    }
    assertEquals(
      card.left - DashboardBottomPanelGutter,
      bottomPanel.right,
      "the bottom panel stops short of the right-hand card",
    )
    assertEquals(
      root.left + DashboardBottomPanelGutter,
      bottomPanel.left,
      "same gutter on the left",
    )
    assertTrue(collapseTab.right < bottomPanel.left, "the collapse tab stays clear of the panel")
  }

  private fun ComposeUiTest.screenshot(name: String) {
    val path = screenshotPath ?: return
    val bitmap = onRoot().captureToImage().asSkiaBitmap()
    val png = Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.PNG)
    File(path.removeSuffix(".png") + "_$name.png").writeBytes(png!!.bytes)
  }

  @Test
  fun openingTable_docksDetailsCard_andExpandingDetailsDocksTable() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val state = WebAppState(dataHolder = sampleDataHolder())
      val entity = state.viewModels.dataCollection.uiState.value.entities.first()
      state.viewModels.surveyMap.selectEntity(entity.id)
      state.viewModels.dashboard.showDatasetInTable(
        checkNotNull(state.viewModels.dataCollection.uiState.value.selectedEntity).datasetId
      )
      showDashboard(state)

      // Opening the bottom data table docks the right-hand details card into a compact pill, gives
      // the table the full map width, and never shows a "Time series" tab inside the table.
      assertTrue(state.viewModels.dashboard.uiState.value.isDashboardTableExpanded)
      assertFalse(state.viewModels.dashboard.uiState.value.isDetailsPanelExpanded)
      onNodeWithTag(DASHBOARD_RIGHT_CARD_TAG).assertDoesNotExist()
      onNodeWithTag(DASHBOARD_TIME_SERIES_SHEET_TAG).assertDoesNotExist()
      onNodeWithText("Time series", substring = true).assertDoesNotExist()
      onNodeWithContentDescription("Expand details").assertExists()
      onNodeWithTag(EARTH_ENGINE_MAP_BADGE_TAG).assertDoesNotExist()

      val root = onRoot().getUnclippedBoundsInRoot()
      val expandedTable = bounds(DASHBOARD_DATA_TABLES_PANEL_TAG)
      assertEquals(root.left + DashboardBottomPanelGutter, expandedTable.left)
      assertEquals(root.right - DashboardBottomPanelGutter, expandedTable.right)
      screenshot("table_with_docked_details")

      // Enabling an Earth Engine preset layer overlays the Earth Engine badge above the table.
      val eeSource =
        state.viewModels.surveyMap.uiState.value.availableImagerySources.first {
          it.type == ImagerySourceType.EARTH_ENGINE
        }
      state.viewModels.surveyMap.toggleImagerySource(eeSource.id)
      waitForIdle()
      val mapBadge = bounds(EARTH_ENGINE_MAP_BADGE_TAG)
      assertTrue(mapBadge.bottom <= expandedTable.top, "sits above the data table panel")

      // Expanding the docked details pill opens the right-hand card and docks the bottom table.
      onNodeWithContentDescription("Expand details").performClick()
      waitForIdle()
      assertTrue(state.viewModels.dashboard.uiState.value.isDetailsPanelExpanded)
      assertFalse(state.viewModels.dashboard.uiState.value.isDashboardTableExpanded)
      onNodeWithTag(DASHBOARD_RIGHT_CARD_TAG).assertExists()
      onNodeWithContentDescription("Expand table").assertExists()
      val card = bounds(DASHBOARD_RIGHT_CARD_TAG)
      val dockedTable = bounds(DASHBOARD_DATA_TABLES_PANEL_TAG)
      val badgeWithCard = bounds(EARTH_ENGINE_MAP_BADGE_TAG)
      assertEquals(card.left - DashboardBottomPanelGutter, dockedTable.right)
      assertTrue(badgeWithCard.right <= card.left, "stays clear of the floating details card")
      screenshot("details_with_docked_table")

      state.viewModels.surveyMap.toggleImagerySource(eeSource.id)
      waitForIdle()
      onNodeWithTag(EARTH_ENGINE_MAP_BADGE_TAG).assertDoesNotExist()
    }

  @Test
  fun dataCollectionMode_collapsesSidePanel_replacesTableWithCollapsedAnalyzeImagery_andRestoresOnExit() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val state = WebAppState(dataHolder = sampleDataHolder())
      assertTrue(state.viewModels.dashboard.uiState.value.isSidePanelExpanded)
      state.viewModels.dataCollection.launchFormForEntity(
        entityId = "0000000a-56d7-5c1d-9457-7457267120a3",
        formId = "form-shade-canopy-audit",
      )
      showDashboard(state)

      // Entering data collection mode auto-collapses the left-hand side panel and replaces the
      // normal data table with a collapsed Analyze imagery bottom sheet.
      assertFalse(state.viewModels.dashboard.uiState.value.isSidePanelExpanded)
      assertTrue(state.viewModels.dashboard.uiState.value.isTimeSeriesOpen)
      assertFalse(state.viewModels.dashboard.uiState.value.isTimeSeriesExpanded)
      onNodeWithContentDescription("Close form").assertExists()
      onNodeWithTag(DASHBOARD_DATA_TABLES_PANEL_TAG).assertDoesNotExist()
      onNodeWithTag(DASHBOARD_TIME_SERIES_SHEET_TAG).assertExists()
      onNodeWithContentDescription("Expand imagery analysis").assertExists()
      val openCard = bounds(DASHBOARD_RIGHT_CARD_TAG)
      assertEquals(WebFormPanelWidth, openCard.right - openCard.left)

      // Expanding the Analyze imagery bottom sheet keeps the open form panel beside it.
      onNodeWithContentDescription("Expand imagery analysis").performClick()
      waitForIdle()
      assertTrue(state.viewModels.dashboard.uiState.value.isTimeSeriesExpanded)
      assertTrue(state.viewModels.dashboard.uiState.value.isDetailsPanelExpanded)
      onNodeWithTag(DASHBOARD_RIGHT_CARD_TAG).assertExists()
      onNodeWithContentDescription("Collapse imagery analysis").assertExists()

      // Collapsing the form card docks it into a compact pill without discarding answers.
      onNodeWithContentDescription("Collapse form").performClick()
      waitForIdle()
      assertFalse(state.viewModels.dashboard.uiState.value.isDetailsPanelExpanded)
      assertTrue(state.viewModels.dataCollection.uiState.value.isDataCollectionFormOpen)
      onNodeWithTag(DASHBOARD_RIGHT_CARD_TAG).assertDoesNotExist()
      onNodeWithContentDescription("Expand form").assertExists()

      // Clicking the docked form pill re-expands the form.
      onNodeWithContentDescription("Expand form").performClick()
      waitForIdle()
      assertTrue(state.viewModels.dashboard.uiState.value.isDetailsPanelExpanded)
      onNodeWithTag(DASHBOARD_RIGHT_CARD_TAG).assertExists()

      // Attempting to close the form warns and asks for confirmation; "Keep editing" keeps it open.
      onNodeWithContentDescription("Close form").performClick()
      waitForIdle()
      onNodeWithText("Discard form responses?").assertExists()
      onNodeWithText("Keep editing").performClick()
      waitForIdle()
      onNodeWithText("Discard form responses?").assertDoesNotExist()
      assertTrue(state.viewModels.dataCollection.uiState.value.isDataCollectionFormOpen)

      // Confirming "Discard" closes the form, restores the normal table, and re-expands the left
      // side panel (since it was open before starting data collection).
      onNodeWithContentDescription("Close form").performClick()
      waitForIdle()
      onNodeWithText("Discard").performClick()
      waitForIdle()
      assertFalse(state.viewModels.dataCollection.uiState.value.isDataCollectionFormOpen)
      assertFalse(state.viewModels.dashboard.uiState.value.isTimeSeriesOpen)
      assertTrue(state.viewModels.dashboard.uiState.value.isSidePanelExpanded)
      onNodeWithTag(DASHBOARD_TIME_SERIES_SHEET_TAG).assertDoesNotExist()
      onNodeWithTag(DASHBOARD_DATA_TABLES_PANEL_TAG).assertExists()
    }

  @Test
  fun dataCollectionMode_warnsWhenNavigatingAwayViaToolbarOrFeatureSelection() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val state = WebAppState(dataHolder = sampleDataHolder())
      val entities = state.viewModels.dataCollection.uiState.value.entities
      val parcel = entities.first { it.id == "0000000a-56d7-5c1d-9457-7457267120a3" }
      val otherEntity = entities.first { it.id != parcel.id }
      val navigation = RecordingPageNavigation()
      state.viewModels.dataCollection.launchFormForEntity(
        entityId = parcel.id,
        formId = "form-shade-canopy-audit",
      )
      setContent {
        AppTheme {
          ProvideWebSession(state) {
            WebDashboardDestination(web = state, navigation = navigation, onSignOut = {})
          }
        }
      }
      waitForIdle()

      // Expanding the left side panel and selecting another feature prompts before leaving.
      state.viewModels.dashboard.expandSidePanel()
      waitForIdle()
      onNodeWithText(otherEntity.label).performClick()
      waitForIdle()
      onNodeWithText("Discard form responses?").assertExists()
      onNodeWithText("Keep editing").performClick()
      waitForIdle()
      assertTrue(state.viewModels.dataCollection.uiState.value.isDataCollectionFormOpen)
      assertEquals(parcel.id, state.viewModels.dataCollection.uiState.value.selectedEntityId)

      // Confirming discards the form and selects the other feature.
      onNodeWithText(otherEntity.label).performClick()
      waitForIdle()
      onNodeWithText("Discard").performClick()
      waitForIdle()
      assertFalse(state.viewModels.dataCollection.uiState.value.isDataCollectionFormOpen)
      assertEquals(otherEntity.id, state.viewModels.dataCollection.uiState.value.selectedEntityId)
    }

  @Test
  fun dedicatedTimeSeriesSheet_collapsesRightCardOnOpen_andRunsBesideOpenFormWhenExpanded() =
    // A short window, where the left-hand panel's collapse tab reaches down beside the sheet.
    runDesktopComposeUiTest(width = 1366, height = 700) {
      val state = WebAppState(dataHolder = sampleDataHolder())
      val parcel =
        state.viewModels.dataCollection.uiState.value.entities.first {
          it.geometryType == GeometryType.POLYGON
        }
      state.viewModels.surveyMap.selectEntity(parcel.id)
      showDashboard(state)

      // Before triggering from the right-hand place card, no time series sheet or tab is shown.
      onNodeWithTag(DASHBOARD_TIME_SERIES_SHEET_TAG).assertDoesNotExist()
      onNodeWithTag(DASHBOARD_DATA_TABLES_PANEL_TAG).assertExists()

      // Triggering Analyze imagery from the right-hand place card opens its dedicated bottom sheet
      // and auto-collapses the right-hand details panel.
      onNodeWithText("Analyze imagery").performScrollTo().performClick()
      waitForIdle()
      assertTrue(state.viewModels.dashboard.uiState.value.isTimeSeriesOpen)
      assertTrue(state.viewModels.dashboard.uiState.value.isTimeSeriesExpanded)
      assertFalse(state.viewModels.dashboard.uiState.value.isDetailsPanelExpanded)
      onNodeWithTag(DASHBOARD_TIME_SERIES_SHEET_TAG).assertExists()
      onNodeWithTag(DASHBOARD_DATA_TABLES_PANEL_TAG).assertDoesNotExist()
      onNodeWithTag(DASHBOARD_RIGHT_CARD_TAG).assertDoesNotExist()
      onNodeWithContentDescription("Expand details").assertExists()

      // Closing the dedicated time series sheet brings back the docked data table bar.
      onNodeWithContentDescription("Close time series").performClick()
      waitForIdle()
      assertFalse(state.viewModels.dashboard.uiState.value.isTimeSeriesOpen)
      onNodeWithTag(DASHBOARD_TIME_SERIES_SHEET_TAG).assertDoesNotExist()
      onNodeWithTag(DASHBOARD_DATA_TABLES_PANEL_TAG).assertExists()

      // Launch a form on the parcel: the Analyze imagery sheet replaces the table (collapsed),
      // and expanding it lets the collector pin a historical scene alongside the open form.
      state.viewModels.dataCollection.launchFormForEntity(
        parcel.id,
        state.viewModels.dataCollection.uiState.value
          .formsForEntity(parcel, onWeb = true)
          .first()
          .id,
      )
      waitForIdle()
      onNodeWithContentDescription("Expand imagery analysis").performClick()
      waitForIdle()
      waitUntil(timeoutMillis = 10_000) { state.timeSeries.uiState.value.loadedSeries != null }
      state.timeSeries.selectObservation(0)
      waitForIdle()

      onNodeWithContentDescription("Close form").assertExists()
      onNodeWithTag(EARTH_ENGINE_PANEL_BADGE_TAG).assertExists()
      onNodeWithTag(EARTH_ENGINE_MAP_BADGE_TAG).assertExists()
      assertCardBesideBottomPanel(
        bottomPanelTag = DASHBOARD_TIME_SERIES_SHEET_TAG,
        expectCardBesideBottomHeight = true,
      )
      val card = bounds(DASHBOARD_RIGHT_CARD_TAG)
      val mapBadge = bounds(EARTH_ENGINE_MAP_BADGE_TAG)
      val removeScene =
        onNodeWithContentDescription("Remove scene from map").getUnclippedBoundsInRoot()
      assertTrue(removeScene.right <= card.left, "the pinned scene banner stays clear of the card")
      assertTrue(mapBadge.right <= card.left, "the map Earth Engine badge stays clear of the card")
      screenshot("time_series_form")
    }
}
