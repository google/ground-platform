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
package org.groundplatform.v2.devtools.prototypeapp.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandContent
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.TableRows
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.groundplatform.v2.devtools.prototypeapp.PrototypeAppState
import org.groundplatform.v2.devtools.prototypeapp.domain.model.BasemapType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.EntityPresentationPropertyKeys
import org.groundplatform.v2.devtools.prototypeapp.domain.model.GeospatialEntityItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.geometryKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.hasGeometry
import org.groundplatform.v2.devtools.prototypeapp.domain.model.maxFramingZoom
import org.groundplatform.v2.devtools.prototypeapp.domain.model.relatedEntityForPropertyValue
import org.groundplatform.v2.devtools.prototypeapp.ui.common.HorizontalResizePointerIcon
import org.groundplatform.v2.devtools.prototypeapp.ui.common.appendGeoId
import org.groundplatform.v2.devtools.prototypeapp.ui.common.downloadTextFile
import org.groundplatform.v2.devtools.prototypeapp.ui.common.geoIdInlineContent
import org.groundplatform.v2.devtools.prototypeapp.ui.common.showPlatformHorizontalResizeCursor
import org.groundplatform.v2.devtools.prototypeapp.ui.datacollection.BottomSheetSearchableListContent
import org.groundplatform.v2.devtools.prototypeapp.ui.datacollection.EntityGeometryIcon
import org.groundplatform.v2.devtools.prototypeapp.ui.datacollection.EntityStatusChip
import org.groundplatform.v2.devtools.prototypeapp.ui.datacollection.WebDataCollectionCard
import org.groundplatform.v2.devtools.prototypeapp.ui.datacollection.WebEntityDetailsCard
import org.groundplatform.v2.devtools.prototypeapp.ui.datacollection.WebFormPanelWidth
import org.groundplatform.v2.devtools.prototypeapp.ui.datacollection.WebSubmissionDetailsCard
import org.groundplatform.v2.devtools.prototypeapp.ui.map.BasemapPreviewCard
import org.groundplatform.v2.devtools.prototypeapp.ui.map.SurveyMainMap
import org.groundplatform.v2.devtools.prototypeapp.ui.map.WebMapDrawingHint
import org.groundplatform.v2.devtools.prototypeapp.ui.map.framingInsets
import org.groundplatform.v2.devtools.prototypeapp.ui.map.rememberSurveyMapCamera
import org.groundplatform.v2.devtools.prototypeapp.ui.navigation.EntityQrCodeModalDialog
import org.groundplatform.v2.devtools.prototypeapp.ui.navigation.GoogleMapsScaleBarWidget
import org.groundplatform.v2.devtools.prototypeapp.ui.navigation.LayersControlDialog
import org.groundplatform.v2.devtools.prototypeapp.ui.navigation.MapClusterBalloonsOverlay
import org.groundplatform.v2.devtools.prototypeapp.ui.navigation.PdfExportMessageSnackbar
import org.groundplatform.v2.devtools.prototypeapp.ui.navigation.SharePdfToAppModalDialog
import org.groundplatform.v2.devtools.prototypeapp.ui.state.DashboardUiState
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.DashboardActions
import org.groundplatform.v2.devtools.prototypeapp.ui.workbench.WebTopToolbar

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
  /** The record this row lists; used to render its [DashboardStatusColumn] cell as a chip. */
  val entity: GeospatialEntityItem? = null,
)

/** First column: the record's workflow status, rendered as a compact status chip. */
internal const val DashboardStatusColumn = "Status"

/** Column holding the record's label; it is wider than the other columns. */
internal const val DashboardLabelColumn = "Label"

/** Name of the column holding each record's GeoID, shown with a pending-sync icon until synced. */
internal const val DashboardGeoIdColumn = "GeoID"

/** Width of the [DashboardStatusColumn], which holds a compact status chip. */
private val DashboardStatusColumnWidth = 140.dp

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
 * appearance), with the [selectedEntityId] row highlighted. Columns are [DashboardStatusColumn],
 * [DashboardLabelColumn], `Submissions`, [DashboardGeoIdColumn], then the dataset's properties.
 * Presentation properties (marker and stroke styling) are left out; property values that reference
 * another record are shown as that record's label via [relatedLabel].
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
        columns =
          listOf(DashboardStatusColumn, DashboardLabelColumn, "Submissions", DashboardGeoIdColumn) +
            propertyKeys,
        rows =
          datasetEntities.map { entity ->
            DashboardDataTableRow(
              id = entity.id,
              cells =
                listOf(
                  entity.workflowStatus,
                  entity.label,
                  entity.submissionCount.toString(),
                  entity.geoId,
                ) +
                  propertyKeys.map { key ->
                    val value = entity.properties[key].orEmpty()
                    relatedLabel(entity, value) ?: value
                  },
              isSelected = entity.id == selectedEntityId,
              entity = entity,
            )
          },
      )
    }

/**
 * Serializes [table] as CSV per RFC 4180: a header row of column names followed by one line per
 * row, with CRLF line breaks. Fields containing a comma, double quote, CR, or LF are enclosed in
 * double quotes, and embedded double quotes are escaped by doubling them.
 */
internal fun buildDashboardTableCsv(table: DashboardDataTable): String {
  val builder = StringBuilder()
  (listOf(table.columns) + table.rows.map { it.cells }).forEach { fields ->
    fields.joinTo(builder, separator = ",") { escapeCsvField(it) }
    builder.append("\r\n")
  }
  return builder.toString()
}

/** Quotes [field] for CSV when required by RFC 4180 (section 2, rules 6 and 7). */
private fun escapeCsvField(field: String): String =
  if (field.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) {
    "\"" + field.replace("\"", "\"\"") + "\""
  } else {
    field
  }

/**
 * File name for downloading [table] as CSV: its title with characters that are invalid in file
 * names replaced by `_`, plus the `.csv` extension.
 */
internal fun dashboardTableCsvFileName(table: DashboardDataTable): String {
  val baseName =
    table.title.replace(Regex("""[\\/:*?"<>|\x00-\x1F]"""), "_").trim().trim('.').ifEmpty {
      "table"
    }
  return "$baseName.csv"
}

/**
 * Width of the separator between a resizable left-hand side panel and the main content area. The
 * whole separator is the drag target for resizing the panel, and holds the drag handle.
 */
internal val SidePanelSeparatorWidth = 8.dp
/** Size of the collapse / expand tab on the side panel's right border. */
private val DashboardSidePanelTabWidth = 20.dp
private val DashboardSidePanelTabHeight = 48.dp
private val DashboardFirstColumnWidth = 200.dp
private val DashboardColumnWidth = 160.dp
private val DashboardTableTabsHeight = 48.dp
private val DashboardDetailsCardWidth = 320.dp
private val DashboardOverlayMargin = 14.dp

/**
 * Main page of the Ground web dashboard (`#dashboard`).
 * - **Left**: A collapsible side panel with the searchable list of map features (one line per
 *   record) and places ([BottomSheetSearchableListContent]). Drag its right border to resize it
 *   ([SidePanelSeparator], width kept in [PrototypeAppState.sidePanelWidthDp]); the tab centered on
 *   that border collapses it, and the same tab at the map's left edge expands it again
 *   ([DashboardSidePanelToggleTab]).
 * - **Main area**: The live survey map ([SurveyMainMap]). Selecting a map feature pans and zooms to
 *   it and opens its details in a floating card in the upper-right corner (
 *   [WebEntityDetailsCard]). While a form is being filled in, that corner holds the data collection
 *   panel instead ([WebDataCollectionCard]). A basemap preview card in the upper-left corner (
 *   [BasemapPreviewCard]) opens the basemap selector in a modal dialog ([LayersControlDialog]).
 * - **Bottom of the map**: A collapsible panel of data tables, one per entity dataset (
 *   [DashboardDataTablesPanel]). It only expands on request: from its ▲ toggle or the card's "Show
 *   in table" button.
 */
@Composable
internal fun WebDashboardPage(
  state: PrototypeAppState,
  onOpenSurveyEditor: () -> Unit = {},
  onSignOut: () -> Unit = { state.signOut() },
) {
  val uiState by state.dashboard.uiState.collectAsState()
  WebDashboardPage(
    uiState = uiState,
    actions = state.dashboard,
    state = state,
    onOpenSurveyEditor = onOpenSurveyEditor,
    onSignOut = onSignOut,
  )
}

/**
 * Dashboard page driven by [uiState] and [actions] for its layout (side panel, details panel, data
 * tables, layer selection). [state] still backs the toolbar, the live map, the floating cards, and
 * the modal dialogs, which have not moved to feature view models yet.
 */
@Composable
internal fun WebDashboardPage(
  uiState: DashboardUiState,
  actions: DashboardActions,
  state: PrototypeAppState,
  onOpenSurveyEditor: () -> Unit = {},
  onSignOut: () -> Unit = { state.signOut() },
) {
  val activeQrEntity = state.dataCollectionUiState.activeQrCodeEntity
  val activePdfSheet = state.dataCollectionUiState.activeSharedPdfSheet
  val isSidePanelExpanded = uiState.isSidePanelExpanded
  // One animated fraction scales the user-chosen width, so collapsing, expanding, and dragging all
  // drive the same layout and the panel's contents keep their full width while sliding.
  val expandedFraction by
    animateFloatAsState(
      targetValue = if (isSidePanelExpanded) 1f else 0f,
      label = "sidePanelExpandedFraction",
    )
  val fullPanelWidth = uiState.sidePanelWidthDp.dp
  val panelWidth = fullPanelWidth * expandedFraction
  val isPanelShown = expandedFraction > 0f
  val borderEnd = if (isPanelShown) panelWidth + SidePanelSeparatorWidth else 0.dp

  Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
      WebTopToolbar(state = state, onOpenSurveyEditor = onOpenSurveyEditor, onSignOut = onSignOut)
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
        Row(modifier = Modifier.fillMaxSize()) {
          if (isPanelShown) {
            Box(modifier = Modifier.width(panelWidth).fillMaxHeight().clipToBounds()) {
              DashboardSidePanel(
                state = state,
                modifier =
                  Modifier.wrapContentWidth(Alignment.End, unbounded = true)
                    .width(fullPanelWidth)
                    .fillMaxHeight(),
              )
            }
            SidePanelSeparator(
              widthDp = uiState.sidePanelWidthDp,
              onWidthChange = actions::updateSidePanelWidth,
              enabled = isSidePanelExpanded,
              modifier = Modifier.width(SidePanelSeparatorWidth).fillMaxHeight(),
            )
          }
          DashboardMapArea(
            uiState = uiState,
            actions = actions,
            state = state,
            modifier = Modifier.weight(1f).fillMaxHeight(),
          )
        }
        DashboardSidePanelToggleTab(
          isExpanded = isSidePanelExpanded,
          onToggle = { actions.toggleSidePanel() },
          modifier = Modifier.align(Alignment.CenterStart).offset(x = borderEnd),
        )
      }
    }

    if (state.isLayersSheetOpen) {
      val mapUiState by state.surveyMap.uiState.collectAsState()
      LayersControlDialog(uiState = mapUiState, actions = state.surveyMap)
    }
    if (activeQrEntity != null) {
      EntityQrCodeModalDialog(actions = state.dataCollection, entity = activeQrEntity, isWeb = true)
    }
    if (activePdfSheet != null) {
      SharePdfToAppModalDialog(
        uiState = state.dataCollectionUiState,
        actions = state.dataCollection,
        sheet = activePdfSheet,
      )
    }
    PdfExportMessageSnackbar(
      uiState = state.dataCollectionUiState,
      actions = state.dataCollection,
      modifier = Modifier.align(Alignment.BottomCenter),
    )
  }
}

/** Left-hand panel: the searchable list of map features and places. */
@Composable
private fun DashboardSidePanel(state: PrototypeAppState, modifier: Modifier = Modifier) {
  Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainerLow) {
    BottomSheetSearchableListContent(
      state = state,
      modifier = Modifier.fillMaxSize().padding(top = 4.dp),
      isSidePanel = true,
    )
  }
}

/**
 * Separator on the inner border of a resizable side panel, [SidePanelSeparatorWidth] wide, with an
 * M3 [VerticalDragHandle] centered vertically. When [enabled], the whole separator is the drag
 * target: it shows a resize cursor and a darker fill while hovered or dragged, and dragging reports
 * the new width through [onWidthChange], which clamps it. The unclamped drag position is tracked so
 * the border only moves back once the pointer returns past the clamp limit.
 *
 * When [panelOnRight] is `true`, the separator sits on the left border of a right-hand panel, so
 * dragging left widens the panel and dragging right narrows it.
 */
@Composable
internal fun SidePanelSeparator(
  widthDp: Float,
  onWidthChange: (Float) -> Unit,
  enabled: Boolean = true,
  panelOnRight: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val density = LocalDensity.current
  val interactionSource = remember { MutableInteractionSource() }
  val isHovered by interactionSource.collectIsHoveredAsState()
  val isDragged by interactionSource.collectIsDraggedAsState()
  val isActive = enabled && (isHovered || isDragged)
  val currentWidthDp by rememberUpdatedState(widthDp)
  var unclampedWidthDp by remember { mutableStateOf(widthDp) }
  val direction = if (panelOnRight) -1f else 1f
  val draggableState = rememberDraggableState { deltaPx ->
    unclampedWidthDp += direction * with(density) { deltaPx.toDp() }.value
    onWidthChange(unclampedWidthDp)
  }

  DisposableEffect(isActive) {
    if (isActive) showPlatformHorizontalResizeCursor(true)
    onDispose { if (isActive) showPlatformHorizontalResizeCursor(false) }
  }

  Box(
    modifier =
      modifier
        .background(
          if (isActive) {
            MaterialTheme.colorScheme.surfaceContainerHighest
          } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
          }
        )
        .then(
          if (enabled) {
            Modifier.pointerHoverIcon(HorizontalResizePointerIcon)
              .hoverable(interactionSource)
              .draggable(
                state = draggableState,
                orientation = Orientation.Horizontal,
                interactionSource = interactionSource,
                onDragStarted = { unclampedWidthDp = currentWidthDp },
              )
              .semantics { contentDescription = "Resize side panel" }
          } else {
            Modifier
          }
        ),
    contentAlignment = Alignment.Center,
  ) {
    // The separator is the drag target, so the handle doesn't need its own 48 dp touch target
    // (which would spill over the list and the map). It shares the interaction source to show its
    // dragged state while resizing.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
      VerticalDragHandle(interactionSource = interactionSource)
    }
  }
}

/**
 * Small tab just right of the side panel's border, vertically centered: a left chevron collapses
 * the panel; once collapsed, the tab rests at the map's left edge with a right chevron to expand
 * it.
 */
@Composable
private fun DashboardSidePanelToggleTab(
  isExpanded: Boolean,
  onToggle: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val label = if (isExpanded) "Collapse side panel" else "Expand side panel"
  val shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
  Surface(
    modifier = modifier.size(DashboardSidePanelTabWidth, DashboardSidePanelTabHeight),
    shape = shape,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    border = BorderStroke(DividerDefaults.Thickness, MaterialTheme.colorScheme.outlineVariant),
    shadowElevation = 1.dp,
  ) {
    Box(
      modifier =
        Modifier.fillMaxSize().clickable(onClickLabel = label, role = Role.Button) { onToggle() },
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector =
          if (isExpanded) {
            Icons.AutoMirrored.Outlined.KeyboardArrowLeft
          } else {
            Icons.AutoMirrored.Outlined.KeyboardArrowRight
          },
        contentDescription = label,
        modifier = Modifier.size(18.dp),
      )
    }
  }
}

/**
 * Map area: live basemap, floating details card (upper right), cluster callout (upper left),
 * basemap toggle and scale bar (lower left), and the data table panel (bottom).
 */
@Composable
private fun DashboardMapArea(
  uiState: DashboardUiState,
  actions: DashboardActions,
  state: PrototypeAppState,
  modifier: Modifier = Modifier,
) {
  val dataCollectionUiState = state.dataCollectionUiState
  val selectedEntity = dataCollectionUiState.selectedEntity
  val selectedSubmission = dataCollectionUiState.selectedSubmission
  val hasSelection = selectedEntity != null || selectedSubmission != null
  val isFormOpen = dataCollectionUiState.isDataCollectionFormOpen
  val isTableExpanded = uiState.isDashboardTableExpanded
  val hasTables = uiState.entities.isNotEmpty()
  var lastFramedSelectionEpoch by remember { mutableStateOf(-1L) }
  val mapCamera =
    rememberSurveyMapCamera(uiState = state.surveyMapUiState, actions = state.surveyMap)

  BoxWithConstraints(modifier = modifier) {
    val expandedTableHeight = (maxHeight * 0.42f).coerceAtLeast(160.dp)
    val tablePanelHeight =
      when {
        !hasTables -> 0.dp
        isTableExpanded -> expandedTableHeight + DashboardTableTabsHeight
        else -> DashboardTableTabsHeight
      }

    val isDetailsExpanded = uiState.isDetailsPanelExpanded

    // Fit a newly selected entity into the part of the map not covered by the floating card or the
    // table. Expanding or collapsing the table re-centers without zooming. Records without geometry
    // open the card without moving the map.
    LaunchedEffect(
      selectedEntity?.id,
      state.entitySelectionEpoch,
      isTableExpanded,
      isDetailsExpanded,
      isFormOpen,
    ) {
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

      val rightPanel =
        when {
          isFormOpen -> WebFormPanelWidth + DashboardOverlayMargin * 2
          isDetailsExpanded -> DashboardDetailsCardWidth + DashboardOverlayMargin * 2
          else -> DashboardOverlayMargin
        }
      val bounds = state.resolveEntityLngLatBounds(entity)
      mapCamera.run {
        val insets = framingInsets(it.viewportSize, bottom = tablePanelHeight, right = rightPanel)
        if (isNewSelection) {
          it.fitBounds(bounds, insets, maxZoom = entity.geometryKind.maxFramingZoom.toDouble())
        } else {
          it.centerOn(bounds.center, insets)
        }
      }
    }

    // Zoom to fit from a geometry question card: centre the geometry in the part of the map not
    // covered by the form panel or the table, the way a selected feature is framed.
    val framingRequest = dataCollectionUiState.webMapFramingRequest
    LaunchedEffect(framingRequest) {
      val request = framingRequest ?: return@LaunchedEffect
      val rightPanel =
        if (isFormOpen) WebFormPanelWidth + DashboardOverlayMargin * 2 else DashboardOverlayMargin
      mapCamera.run {
        val insets = framingInsets(it.viewportSize, bottom = tablePanelHeight, right = rightPanel)
        it.fitBounds(request.bounds, insets, maxZoom = request.maxZoom)
      }
    }

    SurveyMainMap(
      state = state,
      camera = mapCamera,
      modifier = Modifier.fillMaxSize(),
      collapseSheetOnBackgroundTap = false,
      showNavigationOverlay = false,
    )

    // Floating instructions while a form's geometry question is being drawn on the map.
    val webMapDrawing = dataCollectionUiState.webMapDrawing
    val draft = dataCollectionUiState.webMapDraftGeometry
    if (draft != null && webMapDrawing != null) {
      WebMapDrawingHint(
        draft = draft,
        onUndo = { webMapDrawing.undoVertex() },
        onDone = { webMapDrawing.stopDrawing() },
        onCancel = { webMapDrawing.cancelDrawing() },
        modifier = Modifier.align(Alignment.TopCenter).padding(DashboardOverlayMargin),
      )
    }

    // Basemap preview card in the top-left corner, with the cluster callout (if any) below it.
    Column(
      modifier = Modifier.align(Alignment.TopStart).padding(DashboardOverlayMargin),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      BasemapPreviewCard(
        selectedBasemapType = state.selectedBasemapType,
        onClick = { state.updateLayersSheetOpen(true) },
        size = 64.dp,
      )
      if (state.isMapClusteringActive && state.selectedCluster != null) {
        Box(modifier = Modifier.width(360.dp)) {
          MapClusterBalloonsOverlay(uiState = state.surveyMapUiState, actions = state.surveyMap)
        }
      }
    }

    // Floating details card or compact collapsed pill, kept clear of the data table panel.
    val allEntities = uiState.entities
    val selectedLayerDatasetId = uiState.selectedLayerDatasetId
    val layerSummary =
      remember(allEntities, selectedLayerDatasetId) {
        selectedLayerDatasetId?.let { buildDashboardLayerSummary(allEntities, it) }
      }
    val summaryLayer = layerSummary?.let { summary ->
      val layerId = allEntities.firstOrNull { it.datasetId == summary.datasetId }?.layerId
      uiState.mapLayers.firstOrNull { it.id == layerId }
    }
    val hasDetails = selectedEntity != null || selectedSubmission != null || layerSummary != null
    val cardMaxHeight =
      (maxHeight - tablePanelHeight - DashboardOverlayMargin * 2).coerceAtLeast(160.dp)
    val cardModifier =
      Modifier.padding(DashboardOverlayMargin)
        .width(DashboardDetailsCardWidth)
        .heightIn(max = cardMaxHeight)
    // An open form takes over the right-hand panel from the details card until it is submitted or
    // closed; map clicks keep working underneath (they also pick the form's target feature).
    AnimatedVisibility(
      visible = isFormOpen,
      enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
      exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut(),
      modifier = Modifier.align(Alignment.TopEnd),
    ) {
      WebDataCollectionCard(
        uiState = dataCollectionUiState,
        actions = state.dataCollection,
        userGpsCoordinatesLabel = state.userGpsCoordinatesLabel,
        modifier =
          Modifier.padding(DashboardOverlayMargin)
            .width(WebFormPanelWidth)
            .heightIn(max = cardMaxHeight),
      )
    }
    AnimatedVisibility(
      visible = !isFormOpen && isDetailsExpanded && hasDetails,
      enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
      exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut(),
      modifier = Modifier.align(Alignment.TopEnd),
    ) {
      when {
        selectedEntity != null ->
          WebEntityDetailsCard(
            entity = selectedEntity,
            state = state,
            onCollapse = { actions.collapseDetailsPanel() },
            modifier = cardModifier,
          )
        selectedSubmission != null ->
          WebSubmissionDetailsCard(
            submission = selectedSubmission,
            state = state,
            onCollapse = { actions.collapseDetailsPanel() },
            modifier = cardModifier,
          )
        layerSummary != null ->
          WebLayerDetailsCard(
            summary = layerSummary,
            layer = summaryLayer,
            state = state,
            onCollapse = { actions.collapseDetailsPanel() },
            modifier = cardModifier,
          )
      }
    }

    AnimatedVisibility(
      visible = !isFormOpen && !isDetailsExpanded && hasDetails,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.align(Alignment.TopEnd),
    ) {
      if (selectedEntity == null && selectedSubmission == null && layerSummary != null) {
        CollapsedLayerDetailsPill(
          title = layerSummary.title,
          layer = summaryLayer,
          onExpand = { actions.expandDetailsPanel() },
          onClose = { actions.selectLayer(null) },
          modifier = Modifier.padding(DashboardOverlayMargin),
        )
      } else {
        CollapsedDetailsPill(
          entity = selectedEntity,
          submission = selectedSubmission,
          onExpand = { actions.expandDetailsPanel() },
          onClose = {
            if (selectedSubmission != null && selectedEntity == null) {
              state.dataCollection.selectSubmissionDetail(null)
            } else {
              state.selectEntity(null)
            }
          },
          modifier = Modifier.padding(DashboardOverlayMargin),
        )
      }
    }

    // The scale bar sits on top of the data table panel so it stays visible when the panel opens.
    Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
      GoogleMapsScaleBarWidget(
        scaleSpec = state.mapScaleBarSpec,
        isSatellite = state.selectedBasemapType == BasemapType.SATELLITE,
        modifier = Modifier.padding(12.dp),
      )
      if (hasTables) {
        DashboardDataTablesPanel(
          uiState = uiState,
          actions = actions,
          selectedEntityId = selectedEntity?.id,
          hasSelection = hasSelection,
          onRowClick = { row -> state.selectEntity(row.id) },
          expandedTableHeight = expandedTableHeight,
        )
      }
    }
  }
}

/** Compact floating pill shown in the top-right corner when the details panel is collapsed. */
@Composable
private fun CollapsedDetailsPill(
  entity: GeospatialEntityItem?,
  submission: SubmissionPreviewItem?,
  onExpand: () -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shadowElevation = 4.dp,
    tonalElevation = 2.dp,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Row(
      modifier =
        Modifier.clickable { onExpand() }
          .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = Icons.Outlined.ExpandContent,
        contentDescription = "Expand details",
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(20.dp),
      )
      if (entity != null) {
        EntityGeometryIcon(entity = entity, size = 18.dp)
        Text(
          text = entity.label,
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.widthIn(max = 160.dp),
        )
      } else if (submission != null) {
        Icon(
          imageVector = Icons.Outlined.Description,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(18.dp),
        )
        Text(
          text = submission.formTitle,
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.widthIn(max = 160.dp),
        )
      }
      IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
        Icon(
          imageVector = Icons.Outlined.Close,
          contentDescription = "Close details",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(16.dp),
        )
      }
    }
  }
}

/**
 * Collapsible panel over the bottom of the map with one tab per entity dataset (
 * [DashboardDataTable]). It stays collapsed until the user expands it (▲ toggle, a tab, or the
 * details card's "Show in table" button), and follows the selected entity's dataset.
 */
@Composable
internal fun DashboardDataTablesPanel(
  state: PrototypeAppState,
  expandedTableHeight: Dp,
  modifier: Modifier = Modifier,
) {
  val uiState by state.dashboard.uiState.collectAsState()
  val dataCollectionUiState = state.dataCollectionUiState
  DashboardDataTablesPanel(
    uiState = uiState,
    actions = state.dashboard,
    selectedEntityId = dataCollectionUiState.selectedEntityId,
    hasSelection = dataCollectionUiState.hasSelectedRecord,
    onRowClick = { row -> state.selectEntity(row.id) },
    expandedTableHeight = expandedTableHeight,
    modifier = modifier,
  )
}

/**
 * Stateless data table panel: one table per entity dataset in [uiState], with the row of
 * [selectedEntityId] highlighted. Tab, expand, and collapse intents go to [actions]; clicking a row
 * reports it through [onRowClick]. [hasSelection] tells whether a map feature or submission is
 * selected, so collapsing the panel can bring its details card back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardDataTablesPanel(
  uiState: DashboardUiState,
  actions: DashboardActions,
  selectedEntityId: String?,
  hasSelection: Boolean,
  onRowClick: (DashboardDataTableRow) -> Unit,
  expandedTableHeight: Dp,
  modifier: Modifier = Modifier,
) {
  val allEntities = uiState.entities
  val isExpanded = uiState.isDashboardTableExpanded
  val tables =
    remember(allEntities, selectedEntityId) {
      buildDashboardDataTables(
        entities = allEntities,
        selectedEntityId = selectedEntityId,
        relatedLabel = { entity, value ->
          uiState.relatedEntityForPropertyValue(entity, value)?.label
        },
      )
    }
  if (tables.isEmpty()) return

  val selectedIndex =
    tables.indexOfFirst { it.datasetId == uiState.dashboardTableDatasetId }.coerceAtLeast(0)
  val table = tables[selectedIndex]
  val expandedFraction by
    animateFloatAsState(
      targetValue = if (isExpanded) 1f else 0f,
      label = "tableExpandedFraction",
    )
  val tableHeight = expandedTableHeight * expandedFraction
  val isTableShown = expandedFraction > 0f

  Surface(
    modifier = modifier.fillMaxWidth(),
    shape =
      MaterialTheme.shapes.large.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp)),
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
          imageVector = Icons.Outlined.TableRows,
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
                actions.selectDashboardTable(tab.datasetId)
                actions.updateDashboardTableExpanded(true, hasSelection)
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
        TooltipBox(
          positionProvider =
            TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
          tooltip = { PlainTooltip { Text("Download CSV") } },
          state = rememberTooltipState(),
        ) {
          IconButton(
            onClick = {
              downloadTextFile(
                fileName = dashboardTableCsvFileName(table),
                mimeType = "text/csv;charset=utf-8",
                content = buildDashboardTableCsv(table),
              )
            }
          ) {
            Icon(imageVector = Icons.Outlined.Download, contentDescription = "Download CSV")
          }
        }
        IconButton(onClick = { actions.toggleDashboardTableExpanded(hasSelection) }) {
          Icon(
            imageVector =
              if (isExpanded) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowUp,
            contentDescription = if (isExpanded) "Collapse table" else "Expand table",
          )
        }
      }

      if (isTableShown) {
        Box(modifier = Modifier.fillMaxWidth().height(tableHeight).clipToBounds()) {
          Column(
            modifier =
              Modifier.wrapContentHeight(Alignment.Top, unbounded = true)
                .fillMaxWidth()
                .height(expandedTableHeight)
          ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DashboardDataTableView(
              table = table,
              onRowClick = onRowClick,
              modifier = Modifier.fillMaxWidth().weight(1f),
            )
          }
        }
      }
    }
  }
}

/**
 * Spreadsheet-style view of a [DashboardDataTable] with a sticky header row. Rows are lazily
 * composed so map layers with thousands of features stay responsive, and the table scrolls
 * horizontally when its columns don't fit. The [DashboardStatusColumn] cells render a compact
 * status chip, and the [DashboardLabelColumn] is wider than the other columns.
 */
@Composable
private fun DashboardDataTableView(
  table: DashboardDataTable,
  onRowClick: (DashboardDataTableRow) -> Unit,
  modifier: Modifier = Modifier,
) {
  val columnWidths =
    remember(table.columns) {
      table.columns.map { column ->
        when (column) {
          DashboardStatusColumn -> DashboardStatusColumnWidth
          DashboardLabelColumn -> DashboardFirstColumnWidth
          else -> DashboardColumnWidth
        }
      }
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
              color = MaterialTheme.colorScheme.onSurface,
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
            val statusColumnIndex = table.columns.indexOf(DashboardStatusColumn)
            val geoIdColumnIndex = table.columns.indexOf(DashboardGeoIdColumn)
            Row(
              modifier = Modifier.fillMaxWidth().background(rowColor).clickable { onRowClick(row) },
              verticalAlignment = Alignment.CenterVertically,
            ) {
              row.cells.forEachIndexed { cellIndex, cell ->
                val entity = row.entity
                if (cellIndex == statusColumnIndex && entity != null) {
                  Box(
                    modifier =
                      Modifier.width(columnWidths[cellIndex])
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                  ) {
                    EntityStatusChip(entity = entity, compact = true)
                  }
                } else {
                  Text(
                    text =
                      if (cellIndex == geoIdColumnIndex && entity != null) {
                        buildAnnotatedString { appendGeoId(entity) }
                      } else {
                        AnnotatedString(cell)
                      },
                    inlineContent = geoIdInlineContent(tint = textColor),
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
}
