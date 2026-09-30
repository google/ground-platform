/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.devtools.prototypeapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Kind of tabular view shown in the web dashboard's data table panel. */
internal enum class DashboardDataTableKind {
  /** Records of a map layer (`EntityDatasetDef` with geometry / `EntityRecord`). */
  MAP_LAYER,

  /** Records of a data table (`EntityDatasetDef` without geometry). */
  DATA_TABLE,
}

/** One row of a [DashboardDataTable]; [id] is the entity record ID. */
internal data class DashboardDataTableRow(
  val id: String,
  val cells: List<String>,
  val isSelected: Boolean = false,
)

/**
 * A table rendered in the web dashboard's collapsible data table panel, listing every record of one
 * entity dataset. Submissions are never shown here: their data can be hierarchical, so they are
 * only shown in a document-style view.
 */
internal data class DashboardDataTable(
  val id: String,
  val datasetId: String,
  val title: String,
  val kind: DashboardDataTableKind,
  val columns: List<String>,
  val rows: List<DashboardDataTableRow>,
) {
  /** Index of the highlighted row, or `-1` when none is selected. */
  val selectedRowIndex: Int
    get() = rows.indexOfFirst { it.isSelected }
}

/**
 * Builds the web dashboard's data tables: one per entity dataset in [entities] (in order of first
 * appearance), with the [selectedEntityId] row highlighted. Presentation properties are summarized
 * by the `Status` column and left out; property values that reference another record are shown as
 * that record's label via [relatedLabel].
 */
internal fun buildDashboardDataTables(
  entities: List<GeospatialEntityItem>,
  selectedEntityId: String?,
  relatedLabel: (entity: GeospatialEntityItem, value: String) -> String? = { _, _ -> null },
): List<DashboardDataTable> =
  entities
    .groupBy { it.datasetId }
    .map { (datasetId, datasetEntities) ->
      val propertyKeys =
        datasetEntities
          .asSequence()
          .flatMap { it.properties.keys.asSequence() }
          .filter { it !in EntityPresentationPropertyKeys }
          .distinct()
          .toList()
      DashboardDataTable(
        id = "dataset:$datasetId",
        datasetId = datasetId,
        title = datasetEntities.first().datasetName,
        kind =
          if (datasetEntities.any { it.hasGeometry }) {
            DashboardDataTableKind.MAP_LAYER
          } else {
            DashboardDataTableKind.DATA_TABLE
          },
        columns = listOf("Label", "Status", "Submissions", "Sync", "GeoID") + propertyKeys,
        rows =
          datasetEntities.map { entity ->
            DashboardDataTableRow(
              id = entity.id,
              cells =
                listOf(
                  entity.label,
                  entity.mapStatusSummaryBadge,
                  entity.submissionCount.toString(),
                  entity.syncStatus.label,
                  entity.geoId,
                ) +
                  propertyKeys.map { key ->
                    val value = entity.properties[key].orEmpty()
                    relatedLabel(entity, value) ?: value
                  },
              isSelected = entity.id == selectedEntityId,
            )
          },
      )
    }

private val DashboardSidePanelWidth = 400.dp
private val DashboardFirstColumnWidth = 200.dp
private val DashboardColumnWidth = 160.dp
private val DashboardTableTabsHeight = 48.dp
private val DashboardDetailsCardWidth = 380.dp
private val DashboardOverlayMargin = 14.dp

/**
 * Main page of the Ground web dashboard (`#dashboard`).
 * - **Left**: A collapsible side panel with the searchable list of map features (one line per
 * record) and places ([BottomSheetSearchableListContent]).
 * - **Main area**: The live survey map ([MapboxBasemapView]). Selecting a map feature pans and
 * zooms to it and opens its details in a floating card in the upper-right corner (
 * [WebEntityDetailsCard]). A floating Map / Satellite toggle sits in the lower-left corner.
 * - **Bottom of the map**: A collapsible panel of data tables, one per entity dataset (
 * [DashboardDataTablesPanel]). It only expands on request: from its ▲ toggle or the card's "Show in
 * table" button.
 */
@Composable
internal fun WebDashboardPage(
  state: PrototypeAppState,
  onOpenSurveyEditor: () -> Unit = {},
  onSignOut: () -> Unit = { state.signOut() },
) {
  val activeQrEntity = state.activeQrCodeEntity
  val activePdfSheet = state.activeSharedPdfSheet
  val isSidePanelExpanded = state.isSidePanelExpanded

  Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
      WebTopToolbar(
        state = state,
        onOpenSurveyEditor = onOpenSurveyEditor,
        onSignOut = onSignOut,
      )
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
        AnimatedVisibility(
          visible = isSidePanelExpanded,
          enter = expandHorizontally(),
          exit = shrinkHorizontally(),
        ) {
          Row(modifier = Modifier.fillMaxHeight()) {
            DashboardSidePanel(
              state = state,
              modifier = Modifier.width(DashboardSidePanelWidth).fillMaxHeight(),
            )
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          }
        }
        DashboardMapArea(
          state = state,
          modifier = Modifier.weight(1f).fillMaxHeight(),
        )
      }
    }

    if (activeQrEntity != null) {
      EntityQrCodeModalDialog(state = state, entity = activeQrEntity)
    }
    if (activePdfSheet != null) {
      SharePdfToAppModalDialog(state = state, sheet = activePdfSheet)
    }
  }
}

/** Left-hand panel: the searchable list of map features and places. */
@Composable
private fun DashboardSidePanel(
  state: PrototypeAppState,
  modifier: Modifier = Modifier,
) {
  Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainerLow) {
    BottomSheetSearchableListContent(
      state = state,
      modifier = Modifier.fillMaxSize().padding(top = 4.dp),
      isSidePanel = true,
    )
  }
}

/**
 * Map area: live basemap, floating details card (upper right), cluster callout (upper left),
 * basemap toggle and scale bar (lower left), and the data table panel (bottom).
 */
@Composable
private fun DashboardMapArea(state: PrototypeAppState, modifier: Modifier = Modifier) {
  val selectedEntity = state.selectedEntity
  val selectedSubmission = state.selectedSubmission
  val isTableExpanded = state.isDashboardTableExpanded
  val hasTables = state.entities.isNotEmpty()
  var lastFramedSelectionEpoch by remember { mutableStateOf(-1L) }

  BoxWithConstraints(modifier = modifier) {
    val expandedTableHeight = (maxHeight * 0.42f).coerceAtLeast(160.dp)
    val tablePanelHeight =
      when {
        !hasTables -> 0.dp
        isTableExpanded -> expandedTableHeight + DashboardTableTabsHeight
        else -> DashboardTableTabsHeight
      }

    // Fit a newly selected entity into the part of the map not covered by the floating card or the
    // table. Expanding or collapsing the table re-centers without zooming. Records without geometry
    // open the card without moving the map.
    LaunchedEffect(selectedEntity?.id, state.entitySelectionEpoch, isTableExpanded) {
      val entity = selectedEntity ?: return@LaunchedEffect
      if (!entity.hasGeometry) return@LaunchedEffect
      val isNewSelection = state.entitySelectionEpoch != lastFramedSelectionEpoch
      lastFramedSelectionEpoch = state.entitySelectionEpoch
      val visibleViewportHeight = (maxHeight - tablePanelHeight).coerceAtLeast(0.dp)
      val targetScreenY =
        if (maxHeight > 0.dp) {
          ((visibleViewportHeight / 2) / maxHeight).coerceIn(0.10f, 0.50f)
        } else {
          0.50f
        }
      state.recenterMapOnEntity(entity, targetScreenY)

      val fittedZoomDelta =
        framePlatformMapboxOnEntity(
          bounds = state.resolveEntityLngLatBounds(entity),
          bottomPaddingCssPx = tablePanelHeight.value,
          rightPaddingCssPx = (DashboardDetailsCardWidth + DashboardOverlayMargin * 2).value,
          fitToBounds = isNewSelection,
          maxZoom = entity.geometryKind.maxFramingZoom,
        )
      state.syncMapZoomDelta(fittedZoomDelta.toFloat())
    }

    MapboxBasemapView(
      state = state,
      animatedShiftX = state.mapWorldToScreenShiftX,
      animatedShiftY = state.mapWorldToScreenShiftY,
      modifier = Modifier.fillMaxSize(),
      collapseSheetOnBackgroundTap = false,
      showNavigationOverlay = false,
    )

    // Basemap toggle in the top-left corner, with the cluster callout (if any) below it.
    Column(
      modifier = Modifier.align(Alignment.TopStart).padding(DashboardOverlayMargin),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      BasemapToggle(state = state)
      if (state.isMapClusteringActive && state.selectedCluster != null) {
        Box(modifier = Modifier.width(360.dp)) { MapClusterBalloonsOverlay(state = state) }
      }
    }

    // Floating details card, kept clear of the data table panel.
    val cardModifier =
      Modifier.align(Alignment.TopEnd)
        .padding(DashboardOverlayMargin)
        .width(DashboardDetailsCardWidth)
        .heightIn(
          max = (maxHeight - tablePanelHeight - DashboardOverlayMargin * 2).coerceAtLeast(160.dp)
        )
    when {
      selectedEntity != null ->
        WebEntityDetailsCard(entity = selectedEntity, state = state, modifier = cardModifier)
      selectedSubmission != null ->
        WebSubmissionDetailsCard(
          submission = selectedSubmission,
          state = state,
          modifier = cardModifier,
        )
    }

    // The scale bar sits on top of the data table panel so it stays visible when the panel opens.
    Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
      GoogleMapsScaleBarWidget(
        scaleSpec = state.mapScaleBarSpec,
        isSatellite = state.selectedBasemapType == BasemapType.SATELLITE,
        modifier = Modifier.padding(12.dp),
      )
      if (hasTables) {
        DashboardDataTablesPanel(state = state, expandedTableHeight = expandedTableHeight)
      }
    }
  }
}

/**
 * Floating Map / Satellite basemap toggle over the web dashboard's map. The segmented buttons draw
 * their own outline, so they get an opaque fill and an unclipped shadow instead of a wrapping
 * container (whose clip would cut off the outline's rounded ends).
 */
@Composable
private fun BasemapToggle(state: PrototypeAppState) {
  val types = BasemapType.entries
  SingleChoiceSegmentedButtonRow(
    modifier = Modifier.shadow(elevation = 3.dp, shape = CircleShape, clip = false)
  ) {
    types.forEachIndexed { index, type ->
      SegmentedButton(
        selected = state.selectedBasemapType == type,
        onClick = { state.selectBasemapType(type) },
        shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size),
        colors =
          SegmentedButtonDefaults.colors(
            inactiveContainerColor = MaterialTheme.colorScheme.surface
          ),
        label = { Text(type.label) },
      )
    }
  }
}

/**
 * Collapsible panel over the bottom of the map with one tab per entity dataset (
 * [DashboardDataTable]). It stays collapsed until the user expands it (▲ toggle, a tab, or the
 * details card's "Show in table" button), and follows the selected entity's dataset.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardDataTablesPanel(
  state: PrototypeAppState,
  expandedTableHeight: Dp,
  modifier: Modifier = Modifier,
) {
  val allEntities = state.entities
  val selectedEntityId = state.selectedEntityId
  val isExpanded = state.isDashboardTableExpanded
  val tables =
    remember(allEntities, selectedEntityId) {
      buildDashboardDataTables(
        entities = allEntities,
        selectedEntityId = selectedEntityId,
        relatedLabel = { entity, value ->
          state.relatedEntityForPropertyValue(entity, value)?.label
        },
      )
    }
  if (tables.isEmpty()) return

  val selectedIndex =
    tables.indexOfFirst { it.datasetId == state.dashboardTableDatasetId }.coerceAtLeast(0)
  val table = tables[selectedIndex]

  Surface(
    modifier = modifier.fillMaxWidth().animateContentSize(),
    shape =
      MaterialTheme.shapes.large.copy(
        bottomStart = CornerSize(0.dp),
        bottomEnd = CornerSize(0.dp),
      ),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shadowElevation = 8.dp,
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .height(DashboardTableTabsHeight)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = Icons.Default.TableRows,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        PrimaryScrollableTabRow(
          selectedTabIndex = selectedIndex,
          modifier = Modifier.weight(1f),
          containerColor = Color.Transparent,
          edgePadding = 0.dp,
          divider = {},
        ) {
          tables.forEachIndexed { index, tab ->
            Tab(
              selected = index == selectedIndex,
              onClick = {
                state.selectDashboardTable(tab.datasetId)
                state.updateDashboardTableExpanded(true)
              },
              text = {
                Text(
                  text = "${tab.title} (${tab.rows.size})",
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              },
            )
          }
        }
        IconButton(onClick = { state.toggleDashboardTableExpanded() }) {
          Icon(
            imageVector =
              if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
            contentDescription = if (isExpanded) "Collapse table" else "Expand table",
          )
        }
      }

      if (isExpanded) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        DashboardDataTableView(
          table = table,
          onRowClick = { row -> state.selectEntity(row.id) },
          modifier = Modifier.fillMaxWidth().height(expandedTableHeight),
        )
      }
    }
  }
}
/**
 * Spreadsheet-style view of a [DashboardDataTable] with a sticky header row. Rows are lazily
 * composed so map layers with thousands of features stay responsive, and the table scrolls
 * horizontally when its columns don't fit.
 */
@Composable
private fun DashboardDataTableView(
  table: DashboardDataTable,
  onRowClick: (DashboardDataTableRow) -> Unit,
  modifier: Modifier = Modifier,
) {
  val columnWidths =
    remember(table.columns) {
      table.columns.indices.map { if (it == 0) DashboardFirstColumnWidth else DashboardColumnWidth }
    }
  val listState = remember(table.id) { LazyListState() }
  val selectedRowIndex = table.selectedRowIndex

  // Keep the selected row in view, e.g. after picking a map feature on the map.
  LaunchedEffect(table.id, selectedRowIndex) {
    if (selectedRowIndex >= 0) {
      listState.animateScrollToItem(selectedRowIndex)
    }
  }

  BoxWithConstraints(modifier = modifier) {
    val tableWidth = maxOf(columnWidths.fold(0.dp) { acc, w -> acc + w }, maxWidth)
    Box(modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
      Column(modifier = Modifier.width(tableWidth).fillMaxHeight()) {
        Row(
          modifier =
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
          table.columns.forEachIndexed { index, column ->
            Text(
              text = column,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier =
                Modifier.width(columnWidths[index]).padding(horizontal = 12.dp, vertical = 8.dp),
            )
          }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (table.rows.isEmpty()) {
          Text(
            text = "No rows yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
          )
        }

        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f)) {
          itemsIndexed(table.rows, key = { _, row -> row.id }) { index, row ->
            val rowColor =
              when {
                row.isSelected -> MaterialTheme.colorScheme.secondaryContainer
                index % 2 == 1 -> MaterialTheme.colorScheme.surfaceContainer
                else -> Color.Transparent
              }
            val textColor =
              if (row.isSelected) {
                MaterialTheme.colorScheme.onSecondaryContainer
              } else {
                MaterialTheme.colorScheme.onSurface
              }
            Row(
              modifier = Modifier.fillMaxWidth().background(rowColor).clickable { onRowClick(row) }
            ) {
              row.cells.forEachIndexed { cellIndex, cell ->
                Text(
                  text = cell,
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = if (row.isSelected) FontWeight.SemiBold else FontWeight.Normal,
                  color = textColor,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier =
                    Modifier.width(columnWidths[cellIndex])
                      .padding(horizontal = 12.dp, vertical = 8.dp),
                )
              }
            }
          }
        }
      }
    }
  }
}
