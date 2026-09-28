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

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Kind of tabular view shown in the web dashboard's data table panel. */
internal enum class DashboardDataTableKind {
  /** All map features in the selected feature's map layer (`EntityDatasetDef` / `EntityRecord`). */
  MAP_LAYER,

  /** Submissions of one Form linked to the selected map feature (`SubmissionRecord`). */
  FORM_SUBMISSIONS,
}

/** One row of a [DashboardDataTable]; [id] is the map feature or submission ID. */
internal data class DashboardDataTableRow(
  val id: String,
  val cells: List<String>,
  val isSelected: Boolean = false,
)

/** A table rendered in the web dashboard's collapsible data table panel. */
internal data class DashboardDataTable(
  val id: String,
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
 * `simplestyle-spec` presentation keys. They drive map styling and are already summarized by the
 * `Status` column, so they are left out of the map layer table.
 */
private val simpleStylePropertyKeys =
  setOf(
    "marker-size",
    "marker-symbol",
    "marker-color",
    "stroke",
    "stroke-opacity",
    "stroke-width",
    "fill",
    "fill-opacity",
    "title",
    "description",
    "status",
  )

/**
 * Builds the data tables shown when [entity] is selected on the web dashboard:
 * - First, the map layer table listing every map feature in [datasetEntities], with [entity]'s row
 *   highlighted.
 * - Then one table per Form in [submissionGroups], listing that Form's submissions for [entity],
 *   with the [selectedSubmissionId] row highlighted.
 */
internal fun buildDashboardDataTables(
  entity: GeospatialEntityItem,
  datasetEntities: List<GeospatialEntityItem>,
  submissionGroups: List<FormSubmissionsGroup>,
  selectedSubmissionId: String?,
): List<DashboardDataTable> {
  val propertyKeys =
    datasetEntities
      .asSequence()
      .flatMap { it.properties.keys.asSequence() }
      .filter { it !in simpleStylePropertyKeys }
      .distinct()
      .toList()
  val layerTable =
    DashboardDataTable(
      id = "layer:${entity.datasetId}",
      title = entity.datasetName,
      kind = DashboardDataTableKind.MAP_LAYER,
      columns = listOf("Label", "Status", "Submissions", "Sync", "GeoID") + propertyKeys,
      rows =
        datasetEntities.map { feature ->
          DashboardDataTableRow(
            id = feature.id,
            cells =
              listOf(
                feature.label,
                feature.mapStatusSummaryBadge,
                feature.submissionCount.toString(),
                feature.syncStatus.label,
                feature.geoId,
              ) + propertyKeys.map { feature.properties[it].orEmpty() },
            isSelected = feature.id == entity.id,
          )
        },
    )

  val submissionTables =
    submissionGroups.map { group ->
      val questions = group.submissions.flatMap { it.fields }.distinctBy { it.questionName }
      DashboardDataTable(
        id = "form:${group.form.id}",
        title = group.formTitle,
        kind = DashboardDataTableKind.FORM_SUBMISSIONS,
        columns = listOf("Submitted", "Data collector", "Sync") + questions.map { it.questionLabel },
        rows =
          group.submissions.map { submission ->
            val answers = submission.fields.associateBy { it.questionName }
            DashboardDataTableRow(
              id = submission.id,
              cells =
                listOf(
                  submission.timestamp,
                  submission.collectorName,
                  submission.syncStatus.label,
                ) + questions.map { answers[it.questionName]?.answerValue.orEmpty() },
              isSelected = submission.id == selectedSubmissionId,
            )
          },
      )
    }

  return listOf(layerTable) + submissionTables
}

private val DashboardSidePanelWidth = 400.dp
private val DashboardFirstColumnWidth = 200.dp
private val DashboardColumnWidth = 160.dp

/**
 * Main page of the Ground web dashboard (`#dashboard`).
 * - **Left**: The mobile bottom sheet content ([SurveyPersistentBottomSheetContent]) as a fixed side
 *   panel: the searchable list of map features and places, or the selected map feature's details and
 *   `1:N` submissions.
 * - **Main area**: The live survey map ([MapboxBasemapView]).
 * - **Bottom of the map**: When a map feature is selected, a collapsible panel of data tables
 *   ([DashboardDataTablesPanel]) for its map layer and linked Form submissions.
 */
@Composable
internal fun WebDashboardPage(state: PrototypeAppState) {
  val activeQrEntity = state.activeQrCodeEntity
  val activePdfSheet = state.activeSharedPdfSheet

  Box(modifier = Modifier.fillMaxSize()) {
    Row(modifier = Modifier.fillMaxSize()) {
      DashboardSidePanel(
        state = state,
        modifier = Modifier.width(DashboardSidePanelWidth).fillMaxHeight(),
      )
      VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      DashboardMapArea(state = state, modifier = Modifier.weight(1f).fillMaxHeight())
    }

    if (activeQrEntity != null) {
      EntityQrCodeModalDialog(state = state, entity = activeQrEntity)
    }
    if (activePdfSheet != null) {
      SharePdfToAppModalDialog(state = state, sheet = activePdfSheet)
    }
  }
}

/** Left-hand panel: survey header plus the shared bottom sheet content in side-panel mode. */
@Composable
private fun DashboardSidePanel(state: PrototypeAppState, modifier: Modifier = Modifier) {
  Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainerLow) {
    Column(modifier = Modifier.fillMaxSize()) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Text(
          text = state.activeSurvey.title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = state.activeSurvey.location,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      SurveyPersistentBottomSheetContent(
        state = state,
        modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp),
        isSidePanel = true,
      )
    }
  }
}

/** Map area: live basemap, cluster callout, scale bar, and the data table panel. */
@Composable
private fun DashboardMapArea(state: PrototypeAppState, modifier: Modifier = Modifier) {
  val selectedEntity = state.selectedEntity

  BoxWithConstraints(modifier = modifier) {
    val expandedTableHeight = (maxHeight * 0.42f).coerceAtLeast(160.dp)

    MapboxBasemapView(
      state = state,
      animatedShiftX = state.mapWorldToScreenShiftX,
      animatedShiftY = state.mapWorldToScreenShiftY,
      modifier = Modifier.fillMaxSize(),
      collapseSheetOnBackgroundTap = false,
    )

    if (state.isMapClusteringActive && state.selectedCluster != null) {
      Box(modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).width(360.dp)) {
        MapClusterBalloonsOverlay(state = state)
      }
    }

    // The scale bar sits on top of the data table panel so it stays visible when the panel opens.
    Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
      Box(modifier = Modifier.padding(12.dp)) {
        GoogleMapsScaleBarWidget(
          scaleSpec = state.mapScaleBarSpec,
          isSatellite = state.selectedBasemapType == BasemapType.SATELLITE,
        )
      }
      if (selectedEntity != null) {
        DashboardDataTablesPanel(
          state = state,
          entity = selectedEntity,
          expandedTableHeight = expandedTableHeight,
        )
      }
    }
  }
}

/**
 * Collapsible panel over the bottom of the map with one tab per [DashboardDataTable]. Collapsed, it
 * shows only the tab strip; selecting a tab or the expand button reveals the table.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardDataTablesPanel(
  state: PrototypeAppState,
  entity: GeospatialEntityItem,
  expandedTableHeight: Dp,
  modifier: Modifier = Modifier,
) {
  var isExpanded by remember { mutableStateOf(false) }
  var selectedTableId by remember { mutableStateOf<String?>(null) }

  val allEntities = state.entities
  val forms = state.forms
  val selectedSubmission = state.selectedSubmission
  val datasetEntities =
    remember(allEntities, entity.datasetId) {
      allEntities.filter { it.datasetId == entity.datasetId }
    }
  val tables =
    remember(entity, datasetEntities, forms, selectedSubmission?.id) {
      buildDashboardDataTables(
        entity = entity,
        datasetEntities = datasetEntities,
        submissionGroups = state.groupedSubmissionsForEntity(entity),
        selectedSubmissionId = selectedSubmission?.id,
      )
    }

  // Opening a submission in the side panel switches to that Form's table.
  LaunchedEffect(selectedSubmission?.id) {
    if (selectedSubmission != null) {
      selectedTableId = "form:${selectedSubmission.formId}"
    }
  }

  val selectedIndex = tables.indexOfFirst { it.id == selectedTableId }.coerceAtLeast(0)
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
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.List,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Data tables",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.width(12.dp))
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
                selectedTableId = tab.id
                isExpanded = true
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
        IconButton(onClick = { isExpanded = !isExpanded }) {
          Icon(
            imageVector =
              if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
            contentDescription = if (isExpanded) "Collapse data tables" else "Expand data tables",
          )
        }
      }

      if (isExpanded) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        DashboardDataTableView(
          table = table,
          onRowClick = { row ->
            when (table.kind) {
              DashboardDataTableKind.MAP_LAYER -> state.selectEntity(row.id)
              DashboardDataTableKind.FORM_SUBMISSIONS -> state.selectSubmissionDetail(row.id)
            }
          },
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
              modifier =
                Modifier.fillMaxWidth().background(rowColor).clickable { onRowClick(row) }
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
