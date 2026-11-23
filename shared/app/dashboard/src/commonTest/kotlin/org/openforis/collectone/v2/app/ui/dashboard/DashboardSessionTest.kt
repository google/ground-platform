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
package org.openforis.collectone.v2.app.ui.dashboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.domain.model.UploadStatusFilter
import org.openforis.collectone.v2.app.ui.navigation.EntityDetailsPane

class DashboardSessionTest {
  @Test
  fun expandingTheTable_docksTheDetailsPanelAndSidePanel_andClosingItRestoresSidePanel() {
    val expanded =
      DashboardSession(isSidePanelExpanded = true, isDetailsPanelExpanded = true)
        .withTableExpanded(true)
    assertTrue(expanded.isDashboardTableExpanded)
    assertFalse(expanded.isDetailsPanelExpanded)
    assertFalse(expanded.isSidePanelExpanded)
    assertEquals(true, expanded.wasSidePanelExpandedBeforeTable)

    val closed = expanded.withTableExpanded(false)
    assertFalse(closed.isDashboardTableExpanded)
    assertFalse(closed.isDetailsPanelExpanded)
    assertTrue(closed.isSidePanelExpanded)
    assertNull(closed.wasSidePanelExpandedBeforeTable)
  }

  @Test
  fun expandingDetailsPanel_docksTheTableAndRestoresSidePanel() {
    val tableOpen = DashboardSession(isSidePanelExpanded = true).withTableExpanded(true)
    assertTrue(tableOpen.isDashboardTableExpanded)
    assertFalse(tableOpen.isDetailsPanelExpanded)
    assertFalse(tableOpen.isSidePanelExpanded)

    val detailsOpen = tableOpen.withDetailsPanelExpanded(true)
    assertTrue(detailsOpen.isDetailsPanelExpanded)
    assertFalse(detailsOpen.isDashboardTableExpanded)
    assertTrue(detailsOpen.isSidePanelExpanded)
    assertNull(detailsOpen.wasSidePanelExpandedBeforeTable)
  }

  @Test
  fun timeSeriesSheet_collapsesDetailsAndSidePanel_docksTable_andRestoresSidePanel() {
    val opened =
      DashboardSession(isSidePanelExpanded = true, isDetailsPanelExpanded = true)
        .withTimeSeriesOpen(true)
    assertTrue(opened.isTimeSeriesOpen)
    assertTrue(opened.isTimeSeriesExpanded)
    assertFalse(opened.isDetailsPanelExpanded)
    assertFalse(opened.isDashboardTableExpanded)
    assertFalse(opened.isSidePanelExpanded)
    assertEquals(true, opened.wasSidePanelExpandedBeforeTable)

    // Opening the data table closes the time series sheet, docks details, and keeps the remembered
    // side panel state so closing the table later restores it.
    val tableOpen = opened.withTableExpanded(true)
    assertFalse(tableOpen.isTimeSeriesOpen)
    assertFalse(tableOpen.isTimeSeriesExpanded)
    assertTrue(tableOpen.isDashboardTableExpanded)
    assertFalse(tableOpen.isDetailsPanelExpanded)
    assertFalse(tableOpen.isSidePanelExpanded)
    assertEquals(true, tableOpen.wasSidePanelExpandedBeforeTable)

    val closed = tableOpen.withTableExpanded(false)
    assertFalse(closed.isDashboardTableExpanded)
    assertTrue(closed.isSidePanelExpanded)
    assertNull(closed.wasSidePanelExpandedBeforeTable)

    // Closing the time series sheet directly restores the side panel.
    val closedTimeSeries = opened.withTimeSeriesOpen(false)
    assertFalse(closedTimeSeries.isTimeSeriesOpen)
    assertFalse(closedTimeSeries.isTimeSeriesExpanded)
    assertTrue(closedTimeSeries.isSidePanelExpanded)
    assertNull(closedTimeSeries.wasSidePanelExpandedBeforeTable)
  }

  @Test
  fun closingTheTable_keepsASidePanelThatWasAlreadyCollapsed() {
    val closed =
      DashboardSession(isSidePanelExpanded = false).withTableExpanded(true).withTableExpanded(false)
    assertFalse(closed.isSidePanelExpanded)
  }

  @Test
  fun changingTheSidePanelWhileTheTableIsOpen_winsOverTheRememberedState() {
    val session =
      DashboardSession(isSidePanelExpanded = false)
        .withTableExpanded(true)
        .withSidePanelExpanded(true)
    assertNull(session.wasSidePanelExpandedBeforeTable)

    val closed = session.withTableExpanded(false)
    assertTrue(closed.isSidePanelExpanded)
  }

  @Test
  fun settingTheTableToItsCurrentState_changesNothing() {
    val closed = DashboardSession()
    assertSame(closed, closed.withTableExpanded(false))
    val open = closed.withTableExpanded(true)
    assertSame(open, open.withTableExpanded(true))
  }

  @Test
  fun openingASurvey_closesBottomSheets_restoresTheSidePanel_andDropsSelections() {
    val fromTable =
      DashboardSession(isSidePanelExpanded = true)
        .withLayerSelected("plots")
        .withTableExpanded(true)
        .withSurveyOpened()

    assertFalse(fromTable.isDashboardTableExpanded)
    assertFalse(fromTable.isTimeSeriesOpen)
    assertTrue(fromTable.isSidePanelExpanded)
    assertTrue(fromTable.isDetailsPanelExpanded)
    assertNull(fromTable.wasSidePanelExpandedBeforeTable)
    assertNull(fromTable.selectedLayerDatasetId)
    assertNull(fromTable.dashboardTableDatasetId)

    val fromTimeSeries =
      DashboardSession(isSidePanelExpanded = true).withTimeSeriesOpen(true).withSurveyOpened()
    assertFalse(fromTimeSeries.isTimeSeriesOpen)
    assertTrue(fromTimeSeries.isSidePanelExpanded)
    assertNull(fromTimeSeries.wasSidePanelExpandedBeforeTable)
  }

  @Test
  fun openingASurvey_withTheTableClosed_keepsTheSidePanelAsItIs() {
    val opened = DashboardSession(isSidePanelExpanded = false).withSurveyOpened()
    assertFalse(opened.isSidePanelExpanded)
  }

  @Test
  fun sidePanelWidth_isClampedAndIgnoresNonFiniteValues() {
    val session = DashboardSession()
    assertEquals(
      DashboardUiState.MIN_SIDE_PANEL_WIDTH_DP,
      session.withSidePanelWidth(0f).sidePanelWidthDp,
    )
    assertEquals(
      DashboardUiState.MAX_SIDE_PANEL_WIDTH_DP,
      session.withSidePanelWidth(10_000f).sidePanelWidthDp,
    )
    assertSame(session, session.withSidePanelWidth(Float.NaN))
    assertSame(session, session.withSidePanelWidth(Float.NEGATIVE_INFINITY))
  }

  @Test
  fun selectingALayer_opensItsDetails_docksTableAndClosesTimeSeries() {
    val session =
      DashboardSession(
          isDetailsPanelExpanded = false,
          entityDetailsPane = EntityDetailsPane.SUBMISSIONS,
        )
        .withTableExpanded(true)

    val selected = session.withLayerSelected("plots")
    assertEquals("plots", selected.selectedLayerDatasetId)
    assertEquals("plots", selected.dashboardTableDatasetId)
    assertEquals(EntityDetailsPane.PROPERTIES, selected.entityDetailsPane)
    assertTrue(selected.isDetailsPanelExpanded)
    assertFalse(selected.isDashboardTableExpanded)

    val fromTimeSeries = DashboardSession().withTimeSeriesOpen(true).withLayerSelected("plots")
    assertFalse(fromTimeSeries.isTimeSeriesOpen)
    assertTrue(fromTimeSeries.isDetailsPanelExpanded)

    val cleared = selected.copy(isDetailsPanelExpanded = false).withLayerSelected(null)
    assertNull(cleared.selectedLayerDatasetId)
    assertEquals("plots", cleared.dashboardTableDatasetId)
    assertFalse(cleared.isDetailsPanelExpanded)
  }

  @Test
  fun clearingTheEntitySelection_closesTimeSeries_andLeavesDetailsPanelStateAlone() {
    val collapsed = DashboardSession(isDetailsPanelExpanded = false)
    assertFalse(collapsed.withEntitySelected(null, null).isDetailsPanelExpanded)
    assertTrue(collapsed.withEntitySelected("e1", null).isDetailsPanelExpanded)

    val withTimeSeries = DashboardSession(isSidePanelExpanded = true).withTimeSeriesOpen(true)
    val cleared = withTimeSeries.withEntitySelected(null, null)
    assertFalse(cleared.isTimeSeriesOpen)
    assertTrue(cleared.isSidePanelExpanded)
  }

  @Test
  fun entitySubmissionAndFormSelections_dockTable_andKeepTimeSeriesWhenSelectingEntityOrForm() {
    val session =
      DashboardSession(dashboardTableDatasetId = "trees", selectedLayerDatasetId = "x")
        .withTableExpanded(true)

    val entity = session.withEntitySelected("e1", datasetId = null)
    assertEquals("trees", entity.dashboardTableDatasetId)
    assertNull(entity.selectedLayerDatasetId)
    assertTrue(entity.isDetailsPanelExpanded)
    assertFalse(entity.isDashboardTableExpanded)

    val submission = session.withSubmissionOpened(tableDatasetId = null)
    assertEquals("trees", submission.dashboardTableDatasetId)
    assertEquals(EntityDetailsPane.SUBMISSIONS, submission.entityDetailsPane)
    assertNull(submission.selectedLayerDatasetId)
    assertTrue(submission.isDetailsPanelExpanded)
    assertFalse(submission.isDashboardTableExpanded)
    assertEquals("plots", session.withSubmissionOpened("plots").dashboardTableDatasetId)

    val form = session.withFormOpened()
    assertTrue(form.isFormOpen)
    assertTrue(form.isDetailsPanelExpanded)
    assertFalse(form.isDashboardTableExpanded)
    assertFalse(form.isSidePanelExpanded)
    assertTrue(form.isTimeSeriesOpen)
    assertFalse(form.isTimeSeriesExpanded)

    val expandedTs = form.withTimeSeriesExpanded(true)
    assertTrue(expandedTs.isTimeSeriesExpanded)
    assertTrue(expandedTs.isDetailsPanelExpanded)

    val afterForm = expandedTs.withFormClosed()
    assertFalse(afterForm.isFormOpen)
    assertFalse(afterForm.isTimeSeriesOpen)
    assertFalse(afterForm.isTimeSeriesExpanded)
    assertTrue(afterForm.isSidePanelExpanded)
  }

  @Test
  fun formLifecycle_collapsesSidePanel_andRestoresOnlyIfOpenBeforeCollection() {
    val startedFromOpen = DashboardSession(isSidePanelExpanded = true).withFormOpened()
    assertFalse(startedFromOpen.isSidePanelExpanded)
    assertEquals(true, startedFromOpen.wasSidePanelExpandedBeforeForm)
    assertTrue(startedFromOpen.isTimeSeriesOpen)
    assertFalse(startedFromOpen.isTimeSeriesExpanded)

    val finishedFromOpen = startedFromOpen.withFormClosed()
    assertTrue(finishedFromOpen.isSidePanelExpanded)
    assertNull(finishedFromOpen.wasSidePanelExpandedBeforeForm)
    assertFalse(finishedFromOpen.isTimeSeriesOpen)

    val startedFromClosed = DashboardSession(isSidePanelExpanded = false).withFormOpened()
    assertFalse(startedFromClosed.isSidePanelExpanded)
    assertEquals(false, startedFromClosed.wasSidePanelExpandedBeforeForm)

    val finishedFromClosed = startedFromClosed.withFormClosed()
    assertFalse(finishedFromClosed.isSidePanelExpanded)
    assertNull(finishedFromClosed.wasSidePanelExpandedBeforeForm)

    // Manually toggling the side panel during data collection overrides the remembered state.
    val manuallyExpanded = startedFromClosed.withSidePanelExpanded(true).withFormClosed()
    assertTrue(manuallyExpanded.isSidePanelExpanded)
  }

  @Test
  fun togglingAFilterOrADataset_twice_returnsToTheStart() {
    val session = DashboardSession()

    val filtered = session.withUploadStatusFilterToggled(UploadStatusFilter.entries.first())
    assertEquals(UploadStatusFilter.entries.first(), filtered.selectedUploadStatusFilter)
    assertNull(
      filtered
        .withUploadStatusFilterToggled(UploadStatusFilter.entries.first())
        .selectedUploadStatusFilter
    )

    val collapsed = session.withListDatasetCollapsedToggled("plots")
    assertEquals(setOf("plots"), collapsed.collapsedListDatasetIds)
    assertEquals(session, collapsed.withListDatasetCollapsedToggled("plots"))
  }
}
