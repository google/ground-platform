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

import org.openforis.collectone.v2.app.domain.model.UploadStatusFilter
import org.openforis.collectone.v2.app.ui.navigation.EntityDetailsPane

/**
 * The dashboard's screen state that isn't stored anywhere: the web dashboard's panel layout and
 * selections, the searchable list's collapsed datasets, and the `Uploads` filters. It lasts as long
 * as the [DashboardViewModel] and is lost with it (the workbench's Reset returns it to these
 * defaults).
 *
 * The transitions are pure so the rules that tie the panels together can be tested on their own:
 * - The bottom data table and the right-hand panel (details card or open form) are mutually
 *   exclusive: expanding the bottom data table docks the right-hand panel, and expanding the
 *   right-hand panel (or selecting a feature, layer, submission, or form) docks the bottom data
 *   table.
 * - Opening the dedicated time series bottom sheet from the right-hand place card auto-collapses
 *   the right-hand panel and the left-hand side panel, while remaining mutually exclusive with the
 *   bottom data table.
 * - Entering data collection mode auto-collapses the left-hand side panel (remembering its prior
 *   state in [wasSidePanelExpandedBeforeForm]) and replaces the bottom table with a collapsed
 *   Analyze imagery bottom sheet that can be expanded alongside the open form. Completing or
 *   closing the form restores the normal table and restores the left-hand side panel.
 * - Expanding a bottom sheet collapses the left-hand side panel and remembers how it was
 *   ([wasSidePanelExpandedBeforeTable]), so closing it (or opening another survey) restores the
 *   side panel, while any explicit side panel change forgets the remembered state.
 */
internal data class DashboardSession(
  val isSidePanelExpanded: Boolean = true,
  /**
   * How the side panel was before an expanded bottom sheet (data table or time series) collapsed
   * it, or `null` when no bottom sheet is holding it collapsed (both are closed, or the user
   * changed the side panel since).
   */
  val wasSidePanelExpandedBeforeTable: Boolean? = null,
  /** Whether a data collection form is currently open in the right-hand panel. */
  val isFormOpen: Boolean = false,
  /**
   * How the side panel was before entering data collection mode collapsed it, or `null` when no
   * form is holding it collapsed (or the user changed the side panel since).
   */
  val wasSidePanelExpandedBeforeForm: Boolean? = null,
  val sidePanelWidthDp: Float = DashboardUiState.DEFAULT_SIDE_PANEL_WIDTH_DP,
  val isDetailsPanelExpanded: Boolean = true,
  val isDashboardTableExpanded: Boolean = false,
  val dashboardTableDatasetId: String? = null,
  val isTimeSeriesOpen: Boolean = false,
  val isTimeSeriesExpanded: Boolean = false,
  val selectedLayerDatasetId: String? = null,
  val entityDetailsPane: EntityDetailsPane = EntityDetailsPane.PROPERTIES,
  val collapsedListDatasetIds: Set<String> = emptySet(),
  val selectedUploadStatusFilter: UploadStatusFilter? = null,
  val uploadsEntityFilterId: String? = null,
) {
  private val isAnyBottomSheetOpen: Boolean
    get() = isDashboardTableExpanded || (isTimeSeriesOpen && isTimeSeriesExpanded)

  /**
   * The user expanded or collapsed the side panel; bottom sheets and forms no longer decide how it
   * returns.
   */
  fun withSidePanelExpanded(expanded: Boolean) =
    copy(
      isSidePanelExpanded = expanded,
      wasSidePanelExpandedBeforeTable = null,
      wasSidePanelExpandedBeforeForm = null,
    )

  /** Non-finite widths (from a drag that produced no size) are ignored. */
  fun withSidePanelWidth(widthDp: Float): DashboardSession {
    if (!widthDp.isFinite()) return this
    return copy(
      sidePanelWidthDp =
        widthDp.coerceIn(
          DashboardUiState.MIN_SIDE_PANEL_WIDTH_DP,
          DashboardUiState.MAX_SIDE_PANEL_WIDTH_DP,
        )
    )
  }

  /**
   * Expands or docks the right-hand panel (details card or open form). Expanding docks the bottom
   * data table (and restores the side panel it collapsed), so only one of the two is visible at a
   * time. The dedicated time series bottom sheet stays open alongside the right-hand panel.
   */
  fun withDetailsPanelExpanded(expanded: Boolean): DashboardSession =
    if (expanded) {
      withTableExpanded(false).copy(isDetailsPanelExpanded = true)
    } else {
      copy(isDetailsPanelExpanded = false)
    }

  /**
   * Expanding the table (from closed) docks the right-hand panel, closes the dedicated time series
   * sheet (if open), and collapses the side panel (remembering its state when no bottom sheet was
   * open); closing it restores the side panel. Setting the state the table is already in changes
   * nothing.
   */
  fun withTableExpanded(expanded: Boolean): DashboardSession {
    val wasExpanded = isDashboardTableExpanded
    return when {
      expanded && !wasExpanded ->
        copy(
          isSidePanelExpanded = if (isAnyBottomSheetOpen) isSidePanelExpanded else false,
          wasSidePanelExpandedBeforeTable =
            if (isAnyBottomSheetOpen) wasSidePanelExpandedBeforeTable else isSidePanelExpanded,
          isDetailsPanelExpanded = false,
          isDashboardTableExpanded = true,
          isTimeSeriesOpen = false,
          isTimeSeriesExpanded = false,
        )
      !expanded && wasExpanded ->
        copy(
          isSidePanelExpanded = wasSidePanelExpandedBeforeTable ?: isSidePanelExpanded,
          wasSidePanelExpandedBeforeTable = null,
          isDashboardTableExpanded = false,
        )
      else -> this
    }
  }

  /**
   * Opens or closes the dedicated time series bottom sheet. Opening closes the bottom data table
   * (if open), collapses the right-hand panel, and collapses the side panel (remembering its state
   * when no bottom sheet was open). Closing restores the side panel (unless a form is holding it
   * collapsed).
   */
  fun withTimeSeriesOpen(open: Boolean): DashboardSession {
    val wasOpen = isTimeSeriesOpen
    return when {
      open && !wasOpen ->
        copy(
          isSidePanelExpanded = if (isAnyBottomSheetOpen) isSidePanelExpanded else false,
          wasSidePanelExpandedBeforeTable =
            if (isAnyBottomSheetOpen) wasSidePanelExpandedBeforeTable else isSidePanelExpanded,
          isDetailsPanelExpanded = false,
          isDashboardTableExpanded = false,
          isTimeSeriesOpen = true,
          isTimeSeriesExpanded = true,
        )
      open && wasOpen && !isTimeSeriesExpanded ->
        copy(
          isDetailsPanelExpanded = if (isFormOpen) isDetailsPanelExpanded else false,
          isTimeSeriesExpanded = true,
        )
      !open && wasOpen ->
        copy(
          isSidePanelExpanded =
            if (isFormOpen) isSidePanelExpanded
            else (wasSidePanelExpandedBeforeTable ?: isSidePanelExpanded),
          wasSidePanelExpandedBeforeTable = null,
          isTimeSeriesOpen = false,
          isTimeSeriesExpanded = false,
        )
      else -> this
    }
  }

  /**
   * Expands or collapses the Analyze imagery bottom sheet while it is open (such as during data
   * collection mode), without closing the right-hand form panel or changing side panel memory.
   */
  fun withTimeSeriesExpanded(expanded: Boolean): DashboardSession {
    if (!isTimeSeriesOpen || isTimeSeriesExpanded == expanded) return this
    return copy(isTimeSeriesExpanded = expanded)
  }

  /**
   * Another survey opened on the web: its tables and layers are different, so any open form or
   * bottom sheet closes (restoring the side panel it collapsed) and table and layer selections are
   * dropped.
   */
  fun withSurveyOpened() =
    withFormClosed()
      .withTableExpanded(false)
      .withTimeSeriesOpen(false)
      .copy(
        selectedLayerDatasetId = null,
        dashboardTableDatasetId = null,
        isDetailsPanelExpanded = true,
      )

  /**
   * A layer selection (or `null` to clear it). Selecting a layer opens its details on the
   * properties pane, docks the bottom data table, closes any open time series sheet, and follows
   * the layer in the bottom table; clearing it leaves the table and panels as they are.
   */
  fun withLayerSelected(datasetId: String?) =
    if (datasetId == null) {
      copy(selectedLayerDatasetId = null)
    } else {
      withTableExpanded(false)
        .withTimeSeriesOpen(false)
        .copy(
          selectedLayerDatasetId = datasetId,
          entityDetailsPane = EntityDetailsPane.PROPERTIES,
          dashboardTableDatasetId = datasetId,
          isDetailsPanelExpanded = true,
        )
    }

  /**
   * A map feature selected elsewhere (map, list, or table), in dataset [datasetId] when known. A
   * selection replaces the layer selection, expands the right-hand details panel, and docks the
   * bottom data table (while keeping an open time series sheet so it follows the new feature).
   * Clearing it (`entityId == null`) closes any open time series sheet and leaves the table and
   * details panel as they were.
   */
  fun withEntitySelected(entityId: String?, datasetId: String?) =
    if (entityId != null) {
      withTableExpanded(false)
        .copy(
          selectedLayerDatasetId = null,
          entityDetailsPane = EntityDetailsPane.PROPERTIES,
          dashboardTableDatasetId = datasetId ?: dashboardTableDatasetId,
          isDetailsPanelExpanded = true,
        )
    } else {
      (if (isFormOpen) this else withTimeSeriesOpen(false)).copy(
        selectedLayerDatasetId = null,
        entityDetailsPane = EntityDetailsPane.PROPERTIES,
      )
    }

  /**
   * A submission opened for inspection: expands the right-hand panel on its feature's submissions
   * pane and docks the bottom data table.
   */
  fun withSubmissionOpened(tableDatasetId: String?) =
    withTableExpanded(false)
      .copy(
        selectedLayerDatasetId = null,
        entityDetailsPane = EntityDetailsPane.SUBMISSIONS,
        dashboardTableDatasetId = tableDatasetId ?: dashboardTableDatasetId,
        isDetailsPanelExpanded = true,
      )

  /**
   * A data collection form opened in the right-hand panel: expands the right-hand panel,
   * auto-collapses the left-hand panel (remembering its prior state), and replaces the bottom data
   * table with a collapsed Analyze imagery bottom sheet.
   */
  fun withFormOpened(): DashboardSession {
    val priorSidePanel =
      if (isFormOpen) {
        wasSidePanelExpandedBeforeForm
      } else {
        wasSidePanelExpandedBeforeTable ?: isSidePanelExpanded
      }
    return copy(
      isFormOpen = true,
      isSidePanelExpanded = false,
      wasSidePanelExpandedBeforeForm = priorSidePanel,
      wasSidePanelExpandedBeforeTable = null,
      isDetailsPanelExpanded = true,
      isDashboardTableExpanded = false,
      isTimeSeriesOpen = true,
      isTimeSeriesExpanded = false,
    )
  }

  /**
   * The active data collection form finished or closed: restores the normal bottom data table and
   * restores the left-hand panel if it was open before starting data collection.
   */
  fun withFormClosed(): DashboardSession {
    if (!isFormOpen) return this
    return copy(
      isFormOpen = false,
      isSidePanelExpanded = wasSidePanelExpandedBeforeForm ?: isSidePanelExpanded,
      wasSidePanelExpandedBeforeForm = null,
      wasSidePanelExpandedBeforeTable = null,
      isTimeSeriesOpen = false,
      isTimeSeriesExpanded = false,
    )
  }

  fun withListDatasetCollapsedToggled(datasetId: String) =
    copy(
      collapsedListDatasetIds =
        if (datasetId in collapsedListDatasetIds) collapsedListDatasetIds - datasetId
        else collapsedListDatasetIds + datasetId
    )

  /** Selecting the active status filter again clears it, as tapping a selected chip does. */
  fun withUploadStatusFilterToggled(filter: UploadStatusFilter) =
    copy(selectedUploadStatusFilter = if (selectedUploadStatusFilter == filter) null else filter)
}
