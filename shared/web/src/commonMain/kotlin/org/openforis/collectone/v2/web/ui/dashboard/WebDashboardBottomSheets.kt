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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.TableRows
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.EntityPresentationPropertyKeys
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.relatedEntityForPropertyValue
import org.openforis.collectone.v2.app.ui.common.appendGeoId
import org.openforis.collectone.v2.app.ui.common.geoIdInlineContent
import org.openforis.collectone.v2.app.ui.common.geoIdPendingSyncDescription
import org.openforis.collectone.v2.app.ui.dashboard.DashboardActions
import org.openforis.collectone.v2.app.ui.dashboard.DashboardUiState
import org.openforis.collectone.v2.app.ui.datacollection.EntityStatusChip
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.Tab
import org.openforis.collectone.v2.core.forms.ui.controls.clickable
import org.openforis.collectone.v2.web.domain.model.ColumnPrefixGroup
import org.openforis.collectone.v2.web.domain.model.GroupedColumnsLayout
import org.openforis.collectone.v2.web.domain.model.GroupedTableColumn
import org.openforis.collectone.v2.web.domain.model.PagedEntityTableCache
import org.openforis.collectone.v2.web.domain.model.TableCellLoadState
import org.openforis.collectone.v2.web.domain.model.groupColumnsByCommonPrefix
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.web_collapse_table
import org.openforis.collectone.v2.web.resources.web_column_geo_id
import org.openforis.collectone.v2.web.resources.web_column_label
import org.openforis.collectone.v2.web.resources.web_column_status
import org.openforis.collectone.v2.web.resources.web_column_submissions
import org.openforis.collectone.v2.web.resources.web_expand_table
import org.openforis.collectone.v2.web.resources.web_no_rows_yet
import org.openforis.collectone.v2.web.resources.web_table_tab_title
import org.openforis.collectone.v2.web.resources.web_time_series_close
import org.openforis.collectone.v2.web.resources.web_time_series_collapse
import org.openforis.collectone.v2.web.resources.web_time_series_expand
import org.openforis.collectone.v2.web.resources.web_time_series_open
import org.openforis.collectone.v2.web.resources.web_time_series_tab
import org.openforis.collectone.v2.web.ui.common.CollapsedColumnGroupBodyCell
import org.openforis.collectone.v2.web.ui.common.CollapsedColumnGroupHeaderCell
import org.openforis.collectone.v2.web.ui.common.CollapsedColumnGroupWidth
import org.openforis.collectone.v2.web.ui.common.ColumnPrefixGroupChipsRow
import org.openforis.collectone.v2.web.ui.common.ExpandedGroupAccentStrip
import org.openforis.collectone.v2.web.ui.common.ExpandedGroupCollapseButton
import org.openforis.collectone.v2.web.ui.common.HorizontalResizePointerIcon
import org.openforis.collectone.v2.web.ui.common.VirtualDataTable
import org.openforis.collectone.v2.web.ui.common.VirtualTableLoadingCell
import org.openforis.collectone.v2.web.ui.common.showPlatformHorizontalResizeCursor
import org.openforis.collectone.v2.web.ui.timeseries.TimeSeriesActions
import org.openforis.collectone.v2.web.ui.timeseries.TimeSeriesPanel
import org.openforis.collectone.v2.web.ui.timeseries.TimeSeriesUiState

/**
 * Display labels of the fixed leading columns ([DASHBOARD_STATUS_COLUMN], [DASHBOARD_LABEL_COLUMN],
 * `Submissions`, [DASHBOARD_GEO_ID_COLUMN]); the column names themselves stay in English because
 * they also serve as CSV headers.
 */
private val DashboardFixedColumnLabels: List<StringResource> =
  listOf(
    Res.string.web_column_status,
    Res.string.web_column_label,
    Res.string.web_column_submissions,
    Res.string.web_column_geo_id,
  )

/** Width of the [DASHBOARD_STATUS_COLUMN], which holds a compact status chip. */
private val DashboardStatusColumnWidth = 140.dp

/** Test tag of the dashboard's bottom data table panel ([DashboardDataTablesPanel]). */
internal const val DASHBOARD_DATA_TABLES_PANEL_TAG = "dashboard-data-tables-panel"

/** Test tag of the dashboard's dedicated bottom time series sheet ([DashboardTimeSeriesSheet]). */
internal const val DASHBOARD_TIME_SERIES_SHEET_TAG = "dashboard-time-series-sheet"

/**
 * Dedicated bottom sheet for the selected map feature's "Time series & imagery" analysis, shown
 * when triggered from the right-hand place card or (initially collapsed) during data collection.
 */
@Composable
internal fun DashboardTimeSeriesSheet(
  timeSeries: TimeSeriesUiState,
  actions: TimeSeriesActions,
  isExpanded: Boolean,
  onToggleExpanded: () -> Unit,
  onClose: (() -> Unit)?,
  expandedHeight: Dp,
  modifier: Modifier = Modifier,
) {
  val expandedFraction by
    animateFloatAsState(
      targetValue = if (isExpanded) 1f else 0f,
      label = "timeSeriesExpandedFraction",
    )
  val sheetBodyHeight = expandedHeight * expandedFraction
  val isBodyShown = expandedFraction > 0f

  Surface(
    modifier = modifier.fillMaxWidth().testTag(DASHBOARD_TIME_SERIES_SHEET_TAG),
    shape =
      MaterialTheme.shapes.large.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp)),
    color = MaterialTheme.colorScheme.surfaceContainerLowest,
    shadowElevation = 6.dp,
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      DashboardTimeSeriesHeader(
        isExpanded = isExpanded,
        onToggleExpanded = onToggleExpanded,
        onClose = onClose,
      )
      if (isBodyShown) {
        Box(modifier = Modifier.fillMaxWidth().height(sheetBodyHeight).clipToBounds()) {
          Column(
            modifier =
              Modifier.wrapContentHeight(Alignment.Top, unbounded = true)
                .fillMaxWidth()
                .height(expandedHeight)
          ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
              TimeSeriesPanel(
                uiState = timeSeries,
                actions = actions,
                modifier = Modifier.fillMaxSize(),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DashboardTimeSeriesHeader(
  isExpanded: Boolean,
  onToggleExpanded: () -> Unit,
  onClose: (() -> Unit)?,
) {
  val headerModifier =
    if (onClose == null) {
      Modifier.fillMaxWidth()
        .height(DashboardTableTabsHeight)
        .clickable { onToggleExpanded() }
        .padding(start = 16.dp, end = 8.dp)
    } else {
      Modifier.fillMaxWidth().height(DashboardTableTabsHeight).padding(start = 16.dp, end = 8.dp)
    }
  Row(modifier = headerModifier, verticalAlignment = Alignment.CenterVertically) {
    Icon(
      imageVector = Icons.Outlined.Timeline,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(18.dp),
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text(
      text =
        stringResource(
          if (isExpanded) Res.string.web_time_series_tab else Res.string.web_time_series_open
        ),
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
    if (onClose != null) {
      IconButton(onClick = onClose) {
        Icon(
          imageVector = Icons.Outlined.Close,
          contentDescription = stringResource(Res.string.web_time_series_close),
        )
      }
    } else {
      IconButton(onClick = onToggleExpanded) {
        Icon(
          imageVector =
            if (isExpanded) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowUp,
          contentDescription =
            stringResource(
              if (isExpanded) Res.string.web_time_series_collapse
              else Res.string.web_time_series_expand
            ),
        )
      }
    }
  }
}

/**
 * Collapsible panel over the bottom of the map with one tab per entity dataset (
 * [DashboardDataTable]) in [uiState], with the row of [selectedEntityId] highlighted. It stays
 * collapsed until the user expands it (▲ toggle, a tab, or the details' "Show in table" button),
 * and follows the selected entity's dataset. Tab, expand, and collapse intents go to [actions];
 * clicking a row reports it through [onRowClick].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardDataTablesPanel(
  uiState: DashboardUiState,
  actions: DashboardActions,
  selectedEntityId: String?,
  onRowClick: (DashboardDataTableRow) -> Unit,
  expandedTableHeight: Dp,
  isLoading: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val tabs = remember(uiState.entities) { buildDashboardDatasetTabs(uiState.entities) }
  if (tabs.isEmpty()) return

  val isExpanded = uiState.isDashboardTableExpanded
  val selectedIndex =
    tabs.indexOfFirst { it.datasetId == uiState.dashboardTableDatasetId }.coerceAtLeast(0)
  val activeTab = tabs[selectedIndex]
  val expandedFraction by
    animateFloatAsState(
      targetValue = if (isExpanded) 1f else 0f,
      label = "tableExpandedFraction",
    )
  val isTableShown = expandedFraction > 0f
  val tableCache =
    if (isTableShown) {
      rememberDashboardTableCache(
        uiState.activeSurveyId,
        activeTab.datasetId,
        actions.tableRepository(),
      )
    } else {
      null
    }
  val remoteSchema by (tableCache?.schema?.collectAsState() ?: remember { mutableStateOf(null) })
  val propertyColumns =
    remember(activeTab.localPropertyKeys, remoteSchema) {
      mergeDashboardPropertyColumns(activeTab.localPropertyKeys, remoteSchema?.propertyKeys)
    }
  val groupState = rememberDashboardColumnGroupState(activeTab.id, propertyColumns)

  Surface(
    modifier = modifier.fillMaxWidth().testTag(DASHBOARD_DATA_TABLES_PANEL_TAG),
    shape =
      MaterialTheme.shapes.large.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp)),
    color = MaterialTheme.colorScheme.surfaceContainerLowest,
    shadowElevation = 6.dp,
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      DashboardDataTablesHeader(
        tabs = tabs,
        selectedIndex = selectedIndex,
        activeTab = activeTab,
        uiState = uiState,
        selectedEntityId = selectedEntityId,
        isExpanded = isExpanded,
        columnGroups = groupState.groupedProperties.groups,
        expandedGroupIds = groupState.expandedGroupIds,
        onToggleGroup = { group -> groupState.toggle(group.id, true) },
        exportOptions = uiState.exportOptions[activeTab.datasetId],
        actions = actions,
      )
      if (isTableShown) {
        DashboardDataTablesBody(
          tableHeight = expandedTableHeight * expandedFraction,
          expandedTableHeight = expandedTableHeight,
          activeTab = activeTab,
          uiState = uiState,
          tableCache = tableCache,
          groupedProperties = groupState.groupedProperties,
          scrollToGroupId = groupState.scrollToGroupId,
          onClearScrollToGroup = groupState.clearScrollTarget,
          onToggleGroup = { groupId -> groupState.toggle(groupId, false) },
          selectedEntityId = selectedEntityId,
          onRowClick = onRowClick,
          isLoading = isLoading,
        )
      }
    }
  }
}

private class DashboardColumnGroupState(
  val groupedProperties: GroupedColumnsLayout<String>,
  val expandedGroupIds: Set<String>,
  val scrollToGroupId: String?,
  val toggle: (groupId: String, scrollOnExpand: Boolean) -> Unit,
  val clearScrollTarget: () -> Unit,
)

@Composable
private fun rememberDashboardColumnGroupState(
  tabId: String,
  propertyColumns: List<String>,
): DashboardColumnGroupState {
  var expandedGroupIds by remember(tabId) { mutableStateOf(emptySet<String>()) }
  var scrollToGroupId by remember(tabId) { mutableStateOf<String?>(null) }
  val groupedProperties =
    remember(propertyColumns, expandedGroupIds) {
      groupColumnsByCommonPrefix(
        columns = propertyColumns,
        keyOf = { it },
        expandedGroupIds = expandedGroupIds,
      )
    }
  return DashboardColumnGroupState(
    groupedProperties = groupedProperties,
    expandedGroupIds = expandedGroupIds,
    scrollToGroupId = scrollToGroupId,
    toggle = { groupId, scrollOnExpand ->
      if (groupId in expandedGroupIds) {
        expandedGroupIds = expandedGroupIds - groupId
        scrollToGroupId = null
      } else {
        expandedGroupIds = expandedGroupIds + groupId
        if (scrollOnExpand) scrollToGroupId = groupId
      }
    },
    clearScrollTarget = { scrollToGroupId = null },
  )
}

private fun mergeDashboardPropertyColumns(
  localPropertyKeys: List<String>,
  remotePropertyKeys: List<String>?,
): List<String> {
  val seen = LinkedHashSet<String>(localPropertyKeys)
  remotePropertyKeys.orEmpty().forEach { key ->
    if (key !in EntityPresentationPropertyKeys) seen += key
  }
  return seen.toList()
}

@Composable
private fun DashboardDataTablesBody(
  tableHeight: Dp,
  expandedTableHeight: Dp,
  activeTab: DashboardDatasetTab,
  uiState: DashboardUiState,
  tableCache: PagedEntityTableCache?,
  groupedProperties: GroupedColumnsLayout<String>,
  scrollToGroupId: String?,
  onClearScrollToGroup: () -> Unit,
  onToggleGroup: (String) -> Unit,
  selectedEntityId: String?,
  onRowClick: (DashboardDataTableRow) -> Unit,
  isLoading: Boolean,
) {
  Box(modifier = Modifier.fillMaxWidth().height(tableHeight).clipToBounds()) {
    Column(
      modifier =
        Modifier.wrapContentHeight(Alignment.Top, unbounded = true)
          .fillMaxWidth()
          .height(expandedTableHeight)
    ) {
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      DashboardDataTableView(
        tab = activeTab,
        uiState = uiState,
        tableCache = tableCache,
        groupedProperties = groupedProperties,
        scrollToGroupId = scrollToGroupId,
        onClearScrollToGroup = onClearScrollToGroup,
        onToggleGroup = onToggleGroup,
        selectedEntityId = selectedEntityId,
        onRowClick = onRowClick,
        isLoading = isLoading,
        modifier = Modifier.fillMaxWidth().weight(1f),
      )
    }
  }
}

@Composable
private fun DashboardDataTablesHeader(
  tabs: List<DashboardDatasetTab>,
  selectedIndex: Int,
  activeTab: DashboardDatasetTab,
  uiState: DashboardUiState,
  selectedEntityId: String?,
  isExpanded: Boolean,
  columnGroups: List<ColumnPrefixGroup<String>>,
  expandedGroupIds: Set<String>,
  onToggleGroup: (ColumnPrefixGroup<String>) -> Unit,
  exportOptions: org.openforis.collectone.v2.app.ui.dashboard.DatasetExportOptions?,
  actions: DashboardActions,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth().height(DashboardTableTabsHeight).padding(start = 16.dp, end = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = Icons.Outlined.TableRows,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(18.dp),
    )
    Spacer(modifier = Modifier.width(8.dp))
    DashboardDatasetTabBar(
      tabs = tabs,
      selectedIndex = selectedIndex,
      actions = actions,
      modifier = Modifier.weight(1f),
    )
    if (isExpanded && columnGroups.isNotEmpty()) {
      ColumnPrefixGroupChipsRow(
        groups = columnGroups,
        expandedGroupIds = expandedGroupIds,
        onToggle = onToggleGroup,
        modifier = Modifier.widthIn(max = 360.dp).padding(horizontal = 8.dp),
      )
    }
    DashboardExportButton(
      datasetId = activeTab.datasetId,
      buildLocalCsvTable = {
        buildSingleDashboardCsvTable(activeTab.entities, selectedEntityId, uiState)
      },
      buildCsvTableFromEntities = { fullEntities ->
        buildSingleDashboardCsvTable(fullEntities, selectedEntityId, uiState)
      },
      options = exportOptions,
      actions = actions,
    )
    IconButton(onClick = { actions.toggleDashboardTableExpanded() }) {
      Icon(
        imageVector =
          if (isExpanded) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowUp,
        contentDescription =
          stringResource(
            if (isExpanded) Res.string.web_collapse_table else Res.string.web_expand_table
          ),
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardDatasetTabBar(
  tabs: List<DashboardDatasetTab>,
  selectedIndex: Int,
  actions: DashboardActions,
  modifier: Modifier = Modifier,
) {
  PrimaryScrollableTabRow(
    selectedTabIndex = selectedIndex,
    modifier = modifier,
    containerColor = Color.Transparent,
    edgePadding = 0.dp,
    divider = {},
  ) {
    tabs.forEachIndexed { index, tab ->
      Tab(
        selected = index == selectedIndex,
        onClick = {
          actions.selectDashboardTable(tab.datasetId)
          actions.updateDashboardTableExpanded(true)
        },
        text = {
          Text(
            text = stringResource(Res.string.web_table_tab_title, tab.title, tab.entities.size),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
      )
    }
  }
}

private fun buildSingleDashboardCsvTable(
  entities: List<GeospatialEntityItem>,
  selectedEntityId: String?,
  uiState: DashboardUiState,
): DashboardDataTable =
  buildDashboardDataTables(
      entities = entities,
      selectedEntityId = selectedEntityId,
      relatedLabel = { entity, value ->
        uiState.relatedEntityForPropertyValue(entity, value)?.label
      },
    )
    .first()

private const val DASHBOARD_FIXED_COLUMN_COUNT = 4
private const val DASHBOARD_WIDTH_SAMPLE_ROWS = 20
private val DashboardCellHeight = 36.dp

/**
 * 2D-virtualized spreadsheet view of a [DashboardDatasetTab] powered by [VirtualDataTable] and
 * [PagedEntityTableCache]. Only visible rows and visible columns are composed, and remote property
 * tiles (including all `whisp_*` enrichment fields) lazy-load on scroll with bounded LRU eviction.
 */
@Composable
private fun rememberDashboardTableCache(
  surveyId: String,
  datasetId: String,
  tableRepo: org.openforis.collectone.v2.app.domain.repository.EntityTableRepository?,
): PagedEntityTableCache? {
  val lazyRepo = tableRepo?.takeIf { it.isLazyLoadingEnabled }
  val hasIds = surveyId.isNotBlank() && datasetId.isNotBlank()
  val tableCache =
    remember(surveyId, datasetId, lazyRepo, hasIds) {
      if (lazyRepo != null && hasIds) PagedEntityTableCache(surveyId, datasetId, lazyRepo) else null
    }
  DisposableEffect(tableCache) { onDispose { tableCache?.clear() } }
  LaunchedEffect(tableCache) { tableCache?.ensureSchemaLoaded(this) }
  return tableCache
}

private fun estimateDashboardSlotWidth(
  colIndex: Int,
  slot: GroupedTableColumn<String>?,
  entities: List<GeospatialEntityItem>,
  totalSlots: Int,
): Dp {
  if (colIndex == 0) return DashboardStatusColumnWidth
  if (slot is GroupedTableColumn.CollapsedGroup) return CollapsedColumnGroupWidth
  val sample = entities.take(DASHBOARD_WIDTH_SAMPLE_ROWS)
  val columnName =
    when (colIndex) {
      1 -> DASHBOARD_LABEL_COLUMN
      2 -> "Submissions"
      3 -> DASHBOARD_GEO_ID_COLUMN
      else -> (slot as? GroupedTableColumn.Single)?.item.orEmpty()
    }
  val maxSampleChars =
    sample.maxOfOrNull { entity ->
      when (colIndex) {
        1 -> entity.label.length
        2 -> entity.submissionCount.toString().length
        3 -> entity.geoId.length
        else -> entity.properties[columnName]?.length ?: 0
      }
    } ?: 0
  val maxChars = maxOf(columnName.length, maxSampleChars)
  val extra = if (slot is GroupedTableColumn.Single && slot.isGroupStart) 24f else 0f
  val minWidth = if (colIndex == 1) 160.dp else 110.dp
  val maxWidthCap = if (colIndex == totalSlots - 1) 420.dp else 320.dp
  return (maxChars * 7.2f + 32f + extra).dp.coerceIn(minWidth, maxWidthCap)
}

@Composable
private fun DashboardDataTableView(
  tab: DashboardDatasetTab,
  uiState: DashboardUiState,
  tableCache: PagedEntityTableCache?,
  groupedProperties: GroupedColumnsLayout<String>,
  scrollToGroupId: String?,
  onClearScrollToGroup: () -> Unit,
  onToggleGroup: (String) -> Unit,
  selectedEntityId: String?,
  onRowClick: (DashboardDataTableRow) -> Unit,
  isLoading: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()
  val cacheRevision by (tableCache?.revision?.collectAsState() ?: remember { mutableStateOf(0) })
  val propSlots = groupedProperties.displaySlots
  val entityIds = remember(tab.entities) { tab.entities.map { it.id } }
  // Collapsed placeholder slots map to "" so PagedEntityTableCache skips fetching collapsed fields.
  val slotPropertyKeys = remember(propSlots) { dashboardSlotPropertyKeys(propSlots) }
  val (columnWidths, onResizeColumn) = rememberDashboardColumnWidths(tab, propSlots)
  val listState = remember(tab.id) { LazyListState() }
  val horizontalScroll = rememberScrollState()
  val selectedRowIndex =
    remember(tab.entities, selectedEntityId) {
      if (selectedEntityId == null) -1 else tab.entities.indexOfFirst { it.id == selectedEntityId }
    }
  ScrollToExpandedDashboardGroupEffect(
    scrollToGroupId = scrollToGroupId,
    groupedProperties = groupedProperties,
    columnWidths = columnWidths,
    horizontalScroll = horizontalScroll,
    onClearScrollToGroup = onClearScrollToGroup,
  )

  VirtualDataTable(
    rowCount = tab.entities.size,
    rowKey = { tab.entities[it].id },
    columnWidths = columnWidths,
    listState = listState,
    horizontalScroll = horizontalScroll,
    headerBackground = MaterialTheme.colorScheme.surfaceContainerLowest,
    showHeaderDivider = true,
    scrollToRowIndex = selectedRowIndex,
    onVisibleWindowChanged =
      tableCache?.let { cache ->
        { rowRange, colRange ->
          dashboardPropertyColumnRange(colRange, slotPropertyKeys.size)?.let { propRange ->
            cache.onViewportChanged(
              scope = scope,
              rowRange = rowRange,
              colRange = propRange,
              rowEntityIds = entityIds,
              columnKeys = slotPropertyKeys,
            )
          }
        }
      },
    modifier = modifier,
    emptyContent = { DashboardTableEmptyContent(isLoading) },
    headerCell = { index, width ->
      DashboardTableHeaderSlot(
        index = index,
        propSlot = propSlots.getOrNull(index - DASHBOARD_FIXED_COLUMN_COUNT),
        width = width,
        onToggleGroup = onToggleGroup,
        onResize = { newWidth -> onResizeColumn(index, newWidth) },
      )
    },
    rowModifier = { rowIndex ->
      dashboardRowModifier(tab.entities[rowIndex], rowIndex, selectedEntityId, onRowClick)
    },
    cell = { rowIndex, colIndex, width ->
      DashboardTableBodySlot(
        entity = tab.entities[rowIndex],
        rowIndex = rowIndex,
        colIndex = colIndex,
        propSlot = propSlots.getOrNull(colIndex - DASHBOARD_FIXED_COLUMN_COUNT),
        width = width,
        selectedEntityId = selectedEntityId,
        uiState = uiState,
        tableCache = tableCache,
        cacheRevision = cacheRevision,
        onToggleGroup = onToggleGroup,
      )
    },
  )
}

@Composable
private fun rememberDashboardColumnWidths(
  tab: DashboardDatasetTab,
  propSlots: List<GroupedTableColumn<String>>,
): Pair<List<Dp>, (Int, Dp) -> Unit> {
  val totalColumns = DASHBOARD_FIXED_COLUMN_COUNT + propSlots.size
  val defaultColumnWidths =
    remember(tab.id, propSlots, tab.entities) {
      List(totalColumns) { idx ->
        val slot = propSlots.getOrNull(idx - DASHBOARD_FIXED_COLUMN_COUNT)
        estimateDashboardSlotWidth(idx, slot, tab.entities, totalColumns)
      }
    }
  val customColumnWidths =
    remember(tab.id) { androidx.compose.runtime.mutableStateMapOf<String, Dp>() }
  val slotKeys =
    remember(propSlots) {
      listOf("fixed:0", "fixed:1", "fixed:2", "fixed:3") + propSlots.map { it.slotKey }
    }
  val columnWidths =
    defaultColumnWidths.indices.map { idx ->
      customColumnWidths[slotKeys[idx]] ?: defaultColumnWidths[idx]
    }
  return columnWidths to { index, newWidth -> customColumnWidths[slotKeys[index]] = newWidth }
}

private fun dashboardSlotPropertyKeys(propSlots: List<GroupedTableColumn<String>>): List<String> =
  propSlots.map { slot ->
    when (slot) {
      is GroupedTableColumn.Single -> slot.item
      is GroupedTableColumn.CollapsedGroup -> ""
    }
  }

private fun dashboardPropertyColumnRange(colRange: IntRange, slotCount: Int): IntRange? {
  if (slotCount == 0) return null
  val propStart = (colRange.first - DASHBOARD_FIXED_COLUMN_COUNT).coerceAtLeast(0)
  val propEnd = colRange.last - DASHBOARD_FIXED_COLUMN_COUNT
  if (propEnd < propStart) return null
  return propStart..propEnd.coerceAtMost(slotCount - 1)
}

@Composable
private fun ScrollToExpandedDashboardGroupEffect(
  scrollToGroupId: String?,
  groupedProperties: GroupedColumnsLayout<String>,
  columnWidths: List<Dp>,
  horizontalScroll: androidx.compose.foundation.ScrollState,
  onClearScrollToGroup: () -> Unit,
) {
  val density = LocalDensity.current
  LaunchedEffect(scrollToGroupId, groupedProperties.displaySlots) {
    val targetId = scrollToGroupId ?: return@LaunchedEffect
    val propIdx = groupedProperties.firstExpandedSlotIndex(targetId)
    if (propIdx >= 0) {
      val colIdx = DASHBOARD_FIXED_COLUMN_COUNT + propIdx
      val leadingDp = columnWidths.take(colIdx).fold(0.dp) { acc, w -> acc + w }
      val targetPx = with(density) { (leadingDp - 24.dp).coerceAtLeast(0.dp).toPx() }.toInt()
      withFrameNanos {}
      if (horizontalScroll.maxValue < targetPx) {
        withFrameNanos {}
      }
      horizontalScroll.animateScrollTo(targetPx.coerceIn(0, horizontalScroll.maxValue))
      onClearScrollToGroup()
    }
  }
}

@Composable
private fun DashboardTableHeaderSlot(
  index: Int,
  propSlot: GroupedTableColumn<String>?,
  width: Dp,
  onToggleGroup: (String) -> Unit,
  onResize: (Dp) -> Unit,
) {
  val density = LocalDensity.current
  when (propSlot) {
    is GroupedTableColumn.CollapsedGroup ->
      CollapsedColumnGroupHeaderCell(
        groupId = propSlot.group.id,
        wildcardLabel = propSlot.group.wildcardLabel,
        count = propSlot.group.size,
        width = width,
        height = DashboardCellHeight,
        onExpand = { onToggleGroup(propSlot.group.id) },
      )
    is GroupedTableColumn.Single ->
      DashboardResizableHeaderCell(
        columnIndex = index,
        columnName = propSlot.item,
        width = width,
        density = density,
        groupSlot = propSlot,
        onCollapseGroup = onToggleGroup,
        onResize = onResize,
      )
    null ->
      DashboardResizableHeaderCell(
        columnIndex = index,
        columnName = "",
        width = width,
        density = density,
        onResize = onResize,
      )
  }
}

@Composable
private fun DashboardTableBodySlot(
  entity: GeospatialEntityItem,
  rowIndex: Int,
  colIndex: Int,
  propSlot: GroupedTableColumn<String>?,
  width: Dp,
  selectedEntityId: String?,
  uiState: DashboardUiState,
  tableCache: PagedEntityTableCache?,
  cacheRevision: Int,
  onToggleGroup: (String) -> Unit,
) {
  if (propSlot is GroupedTableColumn.CollapsedGroup) {
    CollapsedColumnGroupBodyCell(
      width = width,
      height = DashboardCellHeight,
      showBorder = false,
      onExpand = { onToggleGroup(propSlot.group.id) },
    )
    return
  }
  val isSelected = entity.id == selectedEntityId
  val textColor =
    if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
    else MaterialTheme.colorScheme.onSurface
  val loadState =
    if (propSlot is GroupedTableColumn.Single) {
      resolveDashboardPropertyLoadState(
        entity = entity,
        rowIndex = rowIndex,
        propIndex = colIndex - DASHBOARD_FIXED_COLUMN_COUNT,
        columnName = propSlot.item,
        tableCache = tableCache,
        cacheRevision = cacheRevision,
      )
    } else {
      null
    }
  DashboardTableBodyCell(
    entity = entity,
    colIndex = colIndex,
    width = width,
    isSelected = isSelected,
    textColor = textColor,
    uiState = uiState,
    loadState = loadState,
  )
}

@Composable
private fun DashboardTableEmptyContent(isLoading: Boolean) {
  if (isLoading) {
    Box(
      modifier = Modifier.fillMaxWidth().padding(24.dp),
      contentAlignment = Alignment.Center,
    ) {
      CircularProgressIndicator()
    }
  } else {
    Text(
      text = stringResource(Res.string.web_no_rows_yet),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(16.dp),
    )
  }
}

@Composable
private fun dashboardRowModifier(
  entity: GeospatialEntityItem,
  rowIndex: Int,
  selectedEntityId: String?,
  onRowClick: (DashboardDataTableRow) -> Unit,
): Modifier {
  val isSelected = entity.id == selectedEntityId
  val rowColor =
    when {
      isSelected -> MaterialTheme.colorScheme.secondaryContainer
      rowIndex % 2 == 1 -> MaterialTheme.colorScheme.surfaceContainer
      else -> Color.Transparent
    }
  return Modifier.fillMaxWidth().background(rowColor).clickable {
    onRowClick(
      DashboardDataTableRow(
        id = entity.id,
        cells = emptyList(),
        isSelected = isSelected,
        entity = entity,
      )
    )
  }
}

@Composable
private fun DashboardResizableHeaderCell(
  columnIndex: Int,
  columnName: String,
  width: Dp,
  density: androidx.compose.ui.unit.Density,
  groupSlot: GroupedTableColumn.Single<String>? = null,
  onCollapseGroup: ((String) -> Unit)? = null,
  onResize: (Dp) -> Unit,
) {
  val currentWidthState by rememberUpdatedState(width)
  val resizeInteraction = remember { MutableInteractionSource() }
  val isResizing by resizeInteraction.collectIsDraggedAsState()
  val isHoveringResize by resizeInteraction.collectIsHoveredAsState()
  showPlatformHorizontalResizeCursor(isResizing)
  val groupId = groupSlot?.groupId
  Box(modifier = Modifier.width(width), contentAlignment = Alignment.CenterStart) {
    if (groupId != null) {
      ExpandedGroupAccentStrip(modifier = Modifier.align(Alignment.TopStart))
    }
    Row(
      modifier =
        Modifier.fillMaxWidth().padding(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      if (groupId != null && groupSlot.isGroupStart && onCollapseGroup != null) {
        ExpandedGroupCollapseButton(
          groupId = groupId,
          wildcardLabel = groupSlot.groupLabel,
          count = groupSlot.groupSize,
          onCollapse = { onCollapseGroup(groupId) },
        )
      }
      Text(
        text =
          DashboardFixedColumnLabels.getOrNull(columnIndex)?.let { stringResource(it) }
            ?: columnName,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Box(
      modifier =
        Modifier.align(Alignment.CenterEnd)
          .width(8.dp)
          .height(28.dp)
          .hoverable(resizeInteraction)
          .pointerHoverIcon(HorizontalResizePointerIcon)
          .draggable(
            orientation = Orientation.Horizontal,
            interactionSource = resizeInteraction,
            state =
              rememberDraggableState { deltaPx ->
                val deltaDp = with(density) { deltaPx.toDp() }
                onResize((currentWidthState + deltaDp).coerceIn(72.dp, 640.dp))
              },
          ),
      contentAlignment = Alignment.Center,
    ) {
      Box(
        modifier =
          Modifier.width(if (isResizing || isHoveringResize) 2.dp else 1.dp)
            .height(18.dp)
            .background(
              if (isResizing || isHoveringResize) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )
      )
    }
  }
}

private fun resolveDashboardPropertyLoadState(
  entity: GeospatialEntityItem,
  rowIndex: Int,
  propIndex: Int,
  columnName: String,
  tableCache: PagedEntityTableCache?,
  @Suppress("UNUSED_PARAMETER") cacheRevision: Int = 0,
): TableCellLoadState {
  val localValue = entity.properties[columnName]
  return if (tableCache == null) {
    TableCellLoadState.Loaded(localValue.orEmpty())
  } else {
    tableCache.cellState(
      entityId = entity.id,
      rowIndex = rowIndex,
      colIndex = propIndex,
      columnKey = columnName,
      hasLocalValue = localValue != null,
      localValue = localValue.orEmpty(),
    )
  }
}

@Composable
private fun DashboardTableBodyCell(
  entity: GeospatialEntityItem,
  colIndex: Int,
  width: Dp,
  isSelected: Boolean,
  textColor: Color,
  uiState: DashboardUiState,
  loadState: TableCellLoadState?,
) {
  when (colIndex) {
    0 ->
      Box(modifier = Modifier.width(width).padding(horizontal = 12.dp, vertical = 6.dp)) {
        EntityStatusChip(entity = entity, compact = true)
      }
    1 -> DashboardTextCell(AnnotatedString(entity.label), width, isSelected, textColor)
    2 ->
      DashboardTextCell(
        AnnotatedString(entity.submissionCount.toString()),
        width,
        isSelected,
        textColor,
      )
    3 ->
      DashboardTextCell(
        text = buildAnnotatedString { appendGeoId(entity, geoIdPendingSyncDescription()) },
        width = width,
        isSelected = isSelected,
        textColor = textColor,
      )
    else ->
      when (val state = loadState ?: TableCellLoadState.Loaded("")) {
        TableCellLoadState.Loading ->
          VirtualTableLoadingCell(width = width, height = DashboardCellHeight)
        is TableCellLoadState.Loaded -> {
          val resolved =
            uiState.relatedEntityForPropertyValue(entity, state.value)?.label ?: state.value
          DashboardTextCell(AnnotatedString(resolved), width, isSelected, textColor)
        }
      }
  }
}

@Composable
private fun DashboardTextCell(
  text: AnnotatedString,
  width: Dp,
  isSelected: Boolean,
  textColor: Color,
) {
  Text(
    text = text,
    inlineContent = geoIdInlineContent(tint = textColor),
    style = MaterialTheme.typography.bodySmall,
    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
    color = textColor,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    modifier = Modifier.width(width).padding(horizontal = 12.dp, vertical = 8.dp),
  )
}
