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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandContent
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.BasemapType
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.SubmissionPreviewItem
import org.openforis.collectone.v2.app.domain.model.hasGeometry
import org.openforis.collectone.v2.app.ui.common.EARTH_ENGINE_MAP_BADGE_TAG
import org.openforis.collectone.v2.app.ui.common.PoweredByEarthEngineBadge
import org.openforis.collectone.v2.app.ui.dashboard.DashboardUiState
import org.openforis.collectone.v2.app.ui.datacollection.BottomSheetSearchableListContent
import org.openforis.collectone.v2.app.ui.datacollection.CollectorConsentModalDialog
import org.openforis.collectone.v2.app.ui.datacollection.EntityGeometryIcon
import org.openforis.collectone.v2.app.ui.datacollection.EntityQrCodeModalDialog
import org.openforis.collectone.v2.app.ui.datacollection.PdfExportMessageSnackbar
import org.openforis.collectone.v2.app.ui.datacollection.SharePdfToAppModalDialog
import org.openforis.collectone.v2.app.ui.datacollection.SurveyMainMap
import org.openforis.collectone.v2.app.ui.map.BasemapPreviewCard
import org.openforis.collectone.v2.app.ui.map.EntityGeometry
import org.openforis.collectone.v2.app.ui.map.GoogleMapsScaleBarWidget
import org.openforis.collectone.v2.app.ui.map.LayersControlDialog
import org.openforis.collectone.v2.app.ui.map.MapClusterBalloonsOverlay
import org.openforis.collectone.v2.app.ui.map.framingInsets
import org.openforis.collectone.v2.app.ui.map.rememberSurveyMapCamera
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.core.forms.ui.controls.clickable
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.web_close_details
import org.openforis.collectone.v2.web.resources.web_close_form
import org.openforis.collectone.v2.web.resources.web_collapse_side_panel
import org.openforis.collectone.v2.web.resources.web_dismiss
import org.openforis.collectone.v2.web.resources.web_expand_details
import org.openforis.collectone.v2.web.resources.web_expand_form
import org.openforis.collectone.v2.web.resources.web_expand_side_panel
import org.openforis.collectone.v2.web.resources.web_open_in_google_earth
import org.openforis.collectone.v2.web.ui.common.SidePanelSeparator
import org.openforis.collectone.v2.web.ui.common.SidePanelSeparatorWidth
import org.openforis.collectone.v2.web.ui.common.WebFormPanelWidth
import org.openforis.collectone.v2.web.ui.datacollection.WebDataCollectionCard
import org.openforis.collectone.v2.web.ui.datacollection.WebEntityDetailsCard
import org.openforis.collectone.v2.web.ui.datacollection.webDataCollectionFormTitle
import org.openforis.collectone.v2.web.ui.map.WebMapDrawingHint
import org.openforis.collectone.v2.web.ui.timeseries.PinnedSceneBanner
import org.openforis.collectone.v2.web.ui.timeseries.TimeSeriesUiState

/** Size of the collapse / expand tab on the side panel's right border. */
private val DashboardSidePanelTabWidth = 20.dp
private val DashboardSidePanelTabHeight = 48.dp
internal val DashboardTableTabsHeight = 48.dp
private val DashboardDetailsCardWidth = 320.dp
internal val DashboardOverlayMargin = 14.dp
private val DashboardBasemapPreviewSize = 64.dp

/**
 * Gap on either side of the bottom panel, to the map area's edges or the floating card: wide enough
 * to clear the left-hand panel's collapse tab, so the two don't overlap.
 */
internal val DashboardBottomPanelGutter = DashboardSidePanelTabWidth + 4.dp

/** Test tag of the dashboard's floating details or data collection card. */
internal const val DASHBOARD_RIGHT_CARD_TAG = "dashboard-right-card"

/**
 * Main page of the Collect One web dashboard (`#dashboard`).
 * - **Left**: A collapsible side panel with the searchable list of map features (one line per
 *   record) and places ([BottomSheetSearchableListContent]). Drag its right border to resize it
 *   ([SidePanelSeparator], width kept in [DashboardUiState.sidePanelWidthDp]); the tab centered on
 *   that border collapses it, and the same tab at the map's left edge expands it again
 *   ([DashboardSidePanelToggleTab]).
 * - **Main area**: The live survey map ([SurveyMainMap]). Selecting a map feature pans and zooms to
 *   it and opens its details in a floating card in the upper-right corner (
 *   [WebEntityDetailsCard]). While a form is being filled in, that corner holds the data collection
 *   panel instead ([WebDataCollectionCard]). A basemap preview card in the upper-left corner (
 *   [BasemapPreviewCard]) opens the basemap selector in a modal dialog ([LayersControlDialog]).
 * - **Bottom of the map**: A collapsible panel of data tables, one per entity dataset (
 *   [DashboardDataTablesPanel]). Opening the data table docks the right-hand panel into a compact
 *   pill, and expanding the right-hand panel docks the data table, so only one is open at a time.
 *   Triggering Time series from a map feature's details card opens a dedicated "Time series &
 *   imagery" bottom sheet ([DashboardTimeSeriesSheet]) alongside the right-hand panel.
 *
 * Dashboard page driven by [uiState] and [actions]. The [toolbar], the side panel's [featureList],
 * and the floating [entityDetailsCard] and [submissionDetailsCard] are slots, because those parts
 * still read the app state themselves.
 */
@Composable
internal fun WebDashboardPage(
  uiState: WebDashboardUiState,
  actions: WebDashboardActions,
  toolbar: @Composable () -> Unit,
  featureList: @Composable (Modifier) -> Unit,
  entityDetailsCard: DashboardRecordCard<GeospatialEntityItem>,
  submissionDetailsCard: DashboardRecordCard<SubmissionPreviewItem>,
  onOpenInGoogleEarth: (() -> Unit)? = null,
) {
  val dashboard = uiState.dashboard
  val isSidePanelExpanded = dashboard.isSidePanelExpanded
  // One animated fraction scales the user-chosen width, so collapsing, expanding, and dragging all
  // drive the same layout and the panel's contents keep their full width while sliding.
  val expandedFraction by
    animateFloatAsState(
      targetValue = if (isSidePanelExpanded) 1f else 0f,
      label = "sidePanelExpandedFraction",
    )
  val fullPanelWidth = dashboard.sidePanelWidthDp.dp
  val panelWidth = fullPanelWidth * expandedFraction
  val isPanelShown = expandedFraction > 0f
  val borderEnd = if (isPanelShown) panelWidth + SidePanelSeparatorWidth else 0.dp

  Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
      toolbar()
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
        Row(modifier = Modifier.fillMaxSize()) {
          if (isPanelShown) {
            Box(modifier = Modifier.width(panelWidth).fillMaxHeight().clipToBounds()) {
              DashboardSidePanel(
                modifier =
                  Modifier.wrapContentWidth(Alignment.End, unbounded = true)
                    .width(fullPanelWidth)
                    .fillMaxHeight(),
                onOpenInGoogleEarth = onOpenInGoogleEarth,
                content = featureList,
              )
            }
            SidePanelSeparator(
              widthDp = dashboard.sidePanelWidthDp,
              onWidthChange = actions.dashboard::updateSidePanelWidth,
              enabled = isSidePanelExpanded,
              modifier = Modifier.width(SidePanelSeparatorWidth).fillMaxHeight(),
            )
          }
          DashboardMapArea(
            uiState = uiState,
            actions = actions,
            entityDetailsCard = entityDetailsCard,
            submissionDetailsCard = submissionDetailsCard,
            modifier = Modifier.weight(1f).fillMaxHeight(),
          )
        }
        DashboardSidePanelToggleTab(
          isExpanded = isSidePanelExpanded,
          onToggle = { actions.dashboard.toggleSidePanel() },
          modifier = Modifier.align(Alignment.CenterStart).offset(x = borderEnd),
        )
      }
    }

    DashboardDialogs(uiState = uiState, actions = actions)
  }
}

/**
 * The dashboard's modal dialogs (layers, GeoID QR code, PDF sharing, collector consent) and its PDF
 * export and impact download snackbars, over the whole page.
 */
@Composable
private fun BoxScope.DashboardDialogs(uiState: WebDashboardUiState, actions: WebDashboardActions) {
  val dataCollection = uiState.dataCollection
  val activeQrEntity = dataCollection.activeQrCodeEntity
  val activePdfSheet = dataCollection.activeSharedPdfSheet
  if (uiState.map.isLayersSheetOpen) {
    LayersControlDialog(uiState = uiState.map, actions = actions.map)
  }
  if (activeQrEntity != null) {
    EntityQrCodeModalDialog(
      actions = actions.dataCollection,
      entity = activeQrEntity,
      isWeb = true,
    )
  }
  if (activePdfSheet != null) {
    SharePdfToAppModalDialog(
      uiState = dataCollection,
      actions = actions.dataCollection,
      sheet = activePdfSheet,
    )
  }
  if (dataCollection.pendingCollectorConsent != null) {
    CollectorConsentModalDialog(uiState = dataCollection, actions = actions.dataCollection)
  }
  PdfExportMessageSnackbar(
    uiState = dataCollection,
    actions = actions.dataCollection,
    modifier = Modifier.align(Alignment.BottomCenter),
  )
  ImpactMessageSnackbar(
    message = uiState.impactMessage,
    onDismiss = actions.dismissImpactMessage,
    modifier = Modifier.align(Alignment.BottomCenter),
  )
}

/**
 * Floating details card of one selected record ([T]) in the dashboard's upper-right corner: it
 * receives the record, the click handler of its collapse button, and its modifier.
 */
internal typealias DashboardRecordCard<T> =
  @Composable (record: T, onCollapse: () -> Unit, modifier: Modifier) -> Unit

/**
 * Snackbar confirming an impact summary download, shared by the Impact views: shows [message], if
 * any, until [onDismiss] is clicked or a few seconds have passed.
 */
@Composable
internal fun ImpactMessageSnackbar(
  message: String?,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (message == null) return
  LaunchedEffect(message) {
    kotlinx.coroutines.delay(4_000)
    onDismiss()
  }
  Snackbar(
    modifier = modifier.padding(16.dp),
    action = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(Res.string.web_dismiss))
      }
    },
  ) {
    Text(message)
  }
}

/**
 * Left-hand panel holding [content] (the searchable list of map features and places) and, when
 * [onOpenInGoogleEarth] is given, an "Open in Google Earth" action pinned to the bottom.
 */
@Composable
private fun DashboardSidePanel(
  modifier: Modifier = Modifier,
  onOpenInGoogleEarth: (() -> Unit)? = null,
  content: @Composable (Modifier) -> Unit,
) {
  Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
    Column(modifier = Modifier.fillMaxSize()) {
      content(Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp))
      if (onOpenInGoogleEarth != null) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
          OutlinedButton(onClick = onOpenInGoogleEarth, modifier = Modifier.fillMaxWidth()) {
            Icon(
              imageVector = Icons.Outlined.Public,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
              text = stringResource(Res.string.web_open_in_google_earth),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
          }
        }
      }
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
  val label =
    stringResource(
      if (isExpanded) Res.string.web_collapse_side_panel else Res.string.web_expand_side_panel
    )
  val shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
  Surface(
    modifier = modifier.size(DashboardSidePanelTabWidth, DashboardSidePanelTabHeight),
    shape = shape,
    color = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    shadowElevation = 1.dp,
  ) {
    Box(
      modifier =
        Modifier.fillMaxSize().clip(shape).clickable(onClickLabel = label, role = Role.Button) {
          onToggle()
        },
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
 * Map area: live basemap, floating details or form card (upper right), cluster callout (upper
 * left), basemap toggle and scale bar (lower left), the bottom data table panel, and the dedicated
 * bottom time series sheet (shown once triggered from the right-hand place card).
 *
 * Opening the bottom data table docks the right-hand panel into a compact top-right pill, and
 * expanding the right-hand panel docks the bottom data table, so only one is open at a time. The
 * dedicated time series sheet stays in the part of the map left of the floating card (along with
 * the docked data table bar, pinned scene banner, and other overlays) and follows the card's edge
 * as it opens and closes.
 */
@Composable
private fun DashboardMapArea(
  uiState: WebDashboardUiState,
  actions: WebDashboardActions,
  entityDetailsCard: DashboardRecordCard<GeospatialEntityItem>,
  submissionDetailsCard: DashboardRecordCard<SubmissionPreviewItem>,
  modifier: Modifier = Modifier,
) {
  val dashboard = uiState.dashboard
  val dataCollectionUiState = uiState.dataCollection
  val mapUiState = uiState.map
  val selectedEntity = dataCollectionUiState.selectedEntity
  val selectedSubmission = dataCollectionUiState.selectedSubmission
  val isFormOpen = dataCollectionUiState.isDataCollectionFormOpen
  val isTableExpanded = dashboard.isDashboardTableExpanded
  val isTimeSeriesOpen = dashboard.isTimeSeriesOpen
  val isTimeSeriesExpanded = dashboard.isTimeSeriesExpanded
  val hasTables = dashboard.entities.isNotEmpty()
  var lastFramedSelectionEpoch by remember { mutableStateOf(-1L) }
  val mapCamera = rememberSurveyMapCamera(uiState = mapUiState, actions = actions.map)
  val timeSeriesState = uiState.timeSeries
  // The time series sheet follows the selected map feature, loading only while it's expanded.
  LaunchedEffect(dashboard.activeSurveyId, selectedEntity, isTimeSeriesOpen, isTimeSeriesExpanded) {
    actions.timeSeries.follow(
      dashboard.activeSurveyId,
      selectedEntity,
      isTimeSeriesOpen && isTimeSeriesExpanded,
    )
  }

  // Details for the floating card: the selected map feature, submission, or layer.
  val allEntities = dashboard.entities
  val selectedLayerDatasetId = dashboard.selectedLayerDatasetId
  val layerSummary =
    remember(allEntities, selectedLayerDatasetId) {
      selectedLayerDatasetId?.let { buildDashboardLayerSummary(allEntities, it) }
    }
  val summaryLayer = layerSummary?.let { summary ->
    val layerId = allEntities.firstOrNull { it.datasetId == summary.datasetId }?.layerId
    dashboard.mapLayers.firstOrNull { it.id == layerId }
  }
  val hasDetails = selectedEntity != null || selectedSubmission != null || layerSummary != null
  val hasRightPanel = isFormOpen || hasDetails
  val isDetailsExpanded = dashboard.isDetailsPanelExpanded
  val isRightPanelExpanded = isDetailsExpanded && hasRightPanel
  // Width of the floating card: the open form's, else the details', or none when docked/empty.
  val rightCardWidth =
    when {
      !isRightPanelExpanded -> 0.dp
      isFormOpen -> WebFormPanelWidth
      else -> DashboardDetailsCardWidth
    }
  // The rest of the map area ends at the card's left edge. The inset springs like the card's expand
  // and shrink animations, so the bottom sheet and map overlays move with the card's edge.
  val rightCardInset by
    animateDpAsState(
      targetValue = if (rightCardWidth > 0.dp) rightCardWidth + DashboardOverlayMargin else 0.dp,
      animationSpec =
        spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = Dp.VisibilityThreshold),
      label = "rightCardInset",
    )

  BoxWithConstraints(modifier = modifier) {
    val expandedTableHeight = (maxHeight * 0.42f).coerceAtLeast(160.dp)
    val bottomPanelHeight =
      when {
        isTimeSeriesOpen && isTimeSeriesExpanded -> expandedTableHeight + DashboardTableTabsHeight
        isTimeSeriesOpen -> DashboardTableTabsHeight
        !hasTables -> 0.dp
        isTableExpanded -> expandedTableHeight + DashboardTableTabsHeight
        else -> DashboardTableTabsHeight
      }

    // Fit a newly selected entity into the part of the map not covered by the floating card or the
    // bottom sheet. Expanding or collapsing a panel re-centers without zooming. Records without
    // geometry open the card without moving the map.
    LaunchedEffect(
      selectedEntity?.id,
      mapUiState.entitySelectionEpoch,
      isTableExpanded,
      isTimeSeriesOpen,
      isTimeSeriesExpanded,
      isDetailsExpanded,
      isFormOpen,
    ) {
      val entity = selectedEntity ?: return@LaunchedEffect
      if (!entity.hasGeometry) return@LaunchedEffect
      val isNewSelection = mapUiState.entitySelectionEpoch != lastFramedSelectionEpoch
      lastFramedSelectionEpoch = mapUiState.entitySelectionEpoch
      val visibleViewportHeight = (maxHeight - bottomPanelHeight).coerceAtLeast(0.dp)
      val targetScreenY =
        if (maxHeight > 0.dp) {
          ((visibleViewportHeight / 2) / maxHeight).coerceIn(0.10f, 0.50f)
        } else {
          0.50f
        }
      actions.map.recenterMapOnEntity(entity, targetScreenY)

      val rightPanel =
        when {
          !isRightPanelExpanded -> DashboardOverlayMargin
          isFormOpen -> WebFormPanelWidth + DashboardOverlayMargin * 2
          else -> DashboardDetailsCardWidth + DashboardOverlayMargin * 2
        }
      val bounds = EntityGeometry.bounds(entity) ?: return@LaunchedEffect
      mapCamera.run {
        val insets = framingInsets(it.viewportSize, bottom = bottomPanelHeight, right = rightPanel)
        if (isNewSelection) {
          it.fitBounds(bounds, insets, maxZoom = mapUiState.maxFramingZoom(entity))
        } else {
          it.centerOn(bounds.center, insets)
        }
      }
    }

    // Zoom to fit from a geometry question card: centre the geometry in the part of the map not
    // covered by the form panel or the bottom sheet, the way a selected feature is framed.
    val framingRequest = dataCollectionUiState.webMapFramingRequest
    LaunchedEffect(framingRequest) {
      val request = framingRequest ?: return@LaunchedEffect
      val rightPanel =
        if (isFormOpen && isDetailsExpanded) WebFormPanelWidth + DashboardOverlayMargin * 2
        else DashboardOverlayMargin
      mapCamera.run {
        val insets = framingInsets(it.viewportSize, bottom = bottomPanelHeight, right = rightPanel)
        it.fitBounds(request.bounds, insets, maxZoom = request.maxZoom)
      }
    }

    SurveyMainMap(
      uiState = mapUiState,
      actions = actions.map,
      dataCollection = dataCollectionUiState,
      formMap = actions.dataCollection,
      pendingIds = dashboard.pendingUploadEntityIds,
      camera = mapCamera,
      modifier = Modifier.fillMaxSize(),
      collapseSheetOnBackgroundTap = false,
      showNavigationOverlay = false,
    )

    // The part of the map left of the floating card.
    Box(modifier = Modifier.fillMaxSize().padding(end = rightCardInset)) {
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

      // The historical scene of the observation selected in the time series sheet, centered over
      // the map left of the card and kept clear of the basemap preview card on either side.
      timeSeriesState.pinnedScene?.let { pinnedScene ->
        PinnedSceneBanner(
          pinnedScene = pinnedScene,
          actions = actions.timeSeries,
          modifier =
            Modifier.align(Alignment.TopCenter)
              .padding(
                horizontal = DashboardOverlayMargin * 2 + DashboardBasemapPreviewSize,
                vertical = DashboardOverlayMargin,
              ),
        )
      }

      // Basemap preview card in the top-left corner, with the cluster callout (if any) below it.
      Column(
        modifier = Modifier.align(Alignment.TopStart).padding(DashboardOverlayMargin),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        BasemapPreviewCard(
          selectedBasemapType = mapUiState.selectedBasemapType,
          onClick = { actions.map.updateLayersSheetOpen(true) },
          size = DashboardBasemapPreviewSize,
        )
        if (mapUiState.isMapClusteringActive && mapUiState.selectedCluster != null) {
          Box(modifier = Modifier.width(360.dp)) {
            MapClusterBalloonsOverlay(uiState = mapUiState, actions = actions.map)
          }
        }
      }

      // Compact pill standing in for the docked right-hand panel in the top-right corner.
      AnimatedVisibility(
        visible = !isDetailsExpanded && hasRightPanel,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopEnd),
      ) {
        if (isFormOpen) {
          CollapsedFormPill(
            title = webDataCollectionFormTitle(dataCollectionUiState),
            onExpand = { actions.dashboard.expandDetailsPanel() },
            onClose = actions.requestCloseForm,
            modifier = Modifier.padding(DashboardOverlayMargin),
          )
        } else if (selectedEntity == null && selectedSubmission == null && layerSummary != null) {
          CollapsedLayerDetailsPill(
            title = layerSummary.title,
            layer = summaryLayer,
            onExpand = { actions.dashboard.expandDetailsPanel() },
            onClose = { actions.dashboard.selectLayer(null) },
            modifier = Modifier.padding(DashboardOverlayMargin),
          )
        } else {
          CollapsedDetailsPill(
            entity = selectedEntity,
            submission = selectedSubmission,
            onExpand = { actions.dashboard.expandDetailsPanel() },
            onClose = {
              if (selectedSubmission != null && selectedEntity == null) {
                actions.dataCollection.selectSubmissionDetail(null)
              } else {
                actions.map.selectEntity(null)
              }
            },
            modifier = Modifier.padding(DashboardOverlayMargin),
          )
        }
      }

      // The scale bar (and Earth Engine attribution when an EE layer is shown) sit on top of the
      // bottom sheet (data tables or dedicated time series sheet) so they stay visible when open.
      Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .padding(
                start = 12.dp,
                end = 12.dp,
                top = 8.dp,
                bottom = if (hasTables || isTimeSeriesOpen) 8.dp else 28.dp,
              ),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.Bottom,
        ) {
          GoogleMapsScaleBarWidget(
            scaleSpec = mapUiState.mapScaleBarSpec,
            isSatellite = mapUiState.selectedBasemapType == BasemapType.SATELLITE,
          )
          if (mapUiState.isEarthEngineLayerShown) {
            PoweredByEarthEngineBadge(
              isMapOverlay = true,
              modifier = Modifier.testTag(EARTH_ENGINE_MAP_BADGE_TAG),
            )
          }
        }
        DashboardBottomSheetSlot(
          hasTables = hasTables,
          isFormOpen = isFormOpen,
          isTimeSeriesOpen = isTimeSeriesOpen,
          isTimeSeriesExpanded = isTimeSeriesExpanded,
          dashboard = dashboard,
          actions = actions,
          selectedEntityId = selectedEntity?.id,
          expandedTableHeight = expandedTableHeight,
          isLoading = uiState.isLoading,
          timeSeries = timeSeriesState,
        )
      }
    }

    // Floating card in the upper-right corner. The docked data table bar and the dedicated time
    // series sheet stop short of it, so it can run the map's full height.
    val cardMaxHeight = (maxHeight - DashboardOverlayMargin * 2).coerceAtLeast(160.dp)
    val cardModifier =
      Modifier.padding(DashboardOverlayMargin)
        .width(DashboardDetailsCardWidth)
        .heightIn(max = cardMaxHeight)
        .testTag(DASHBOARD_RIGHT_CARD_TAG)
    // An open form takes over the right-hand panel from the details card until it is submitted or
    // closed; opening the bottom data table docks it into a compact pill without losing answers.
    AnimatedVisibility(
      visible = isFormOpen && isDetailsExpanded,
      enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
      exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut(),
      modifier = Modifier.align(Alignment.TopEnd),
    ) {
      WebDataCollectionCard(
        uiState = dataCollectionUiState,
        actions = actions.dataCollection,
        userGpsCoordinatesLabel = mapUiState.userGpsCoordinatesLabel,
        onCollapse = { actions.dashboard.collapseDetailsPanel() },
        onRequestClose = actions.requestCloseForm,
        modifier =
          Modifier.padding(DashboardOverlayMargin)
            .width(WebFormPanelWidth)
            .heightIn(max = cardMaxHeight)
            .testTag(DASHBOARD_RIGHT_CARD_TAG),
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
          entityDetailsCard(
            selectedEntity,
            { actions.dashboard.collapseDetailsPanel() },
            cardModifier,
          )
        selectedSubmission != null ->
          submissionDetailsCard(
            selectedSubmission,
            { actions.dashboard.collapseDetailsPanel() },
            cardModifier,
          )
        layerSummary != null ->
          WebLayerDetailsCard(
            summary = layerSummary,
            layer = summaryLayer,
            onClose = { actions.dashboard.selectLayer(null) },
            onShowInTable = actions.dashboard::showSelectedLayerInTable,
            onCollapse = { actions.dashboard.collapseDetailsPanel() },
            modifier = cardModifier,
          )
      }
    }
  }
}

/** Compact floating pill shown in the top-right corner when an open form card is docked. */
@Composable
private fun CollapsedFormPill(
  title: String,
  onExpand: () -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surfaceContainerLowest,
    shadowElevation = 4.dp,
    tonalElevation = 2.dp,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Row(
      modifier =
        Modifier.clip(CircleShape)
          .clickable { onExpand() }
          .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = Icons.Outlined.ExpandContent,
        contentDescription = stringResource(Res.string.web_expand_form),
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(20.dp),
      )
      Icon(
        imageVector = Icons.Outlined.Description,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(18.dp),
      )
      Text(
        text = title,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = 160.dp),
      )
      IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
        Icon(
          imageVector = Icons.Outlined.Close,
          contentDescription = stringResource(Res.string.web_close_form),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(16.dp),
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
    color = MaterialTheme.colorScheme.surfaceContainerLowest,
    shadowElevation = 4.dp,
    tonalElevation = 2.dp,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Row(
      modifier =
        Modifier.clip(CircleShape)
          .clickable { onExpand() }
          .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = Icons.Outlined.ExpandContent,
        contentDescription = stringResource(Res.string.web_expand_details),
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
          contentDescription = stringResource(Res.string.web_close_details),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(16.dp),
        )
      }
    }
  }
}

/**
 * Hosts the dashboard's bottom sheet slot: either the collapsible data tables panel or, once
 * triggered from the right-hand place card, the dedicated time series bottom sheet.
 */
@Composable
private fun DashboardBottomSheetSlot(
  hasTables: Boolean,
  isFormOpen: Boolean,
  isTimeSeriesOpen: Boolean,
  isTimeSeriesExpanded: Boolean,
  dashboard: DashboardUiState,
  actions: WebDashboardActions,
  selectedEntityId: String?,
  expandedTableHeight: Dp,
  isLoading: Boolean,
  timeSeries: TimeSeriesUiState,
) {
  Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
    AnimatedVisibility(
      visible = hasTables && !isTimeSeriesOpen,
      enter = fadeIn(),
      exit = fadeOut(),
    ) {
      // The same gutter on both sides, whether or not the card is open.
      DashboardDataTablesPanel(
        uiState = dashboard,
        actions = actions.dashboard,
        selectedEntityId = selectedEntityId,
        onRowClick = { row -> actions.map.selectEntity(row.id) },
        expandedTableHeight = expandedTableHeight,
        isLoading = isLoading,
        modifier = Modifier.padding(horizontal = DashboardBottomPanelGutter),
      )
    }
    AnimatedVisibility(
      visible = isTimeSeriesOpen,
      enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
      exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
    ) {
      DashboardTimeSeriesSheet(
        timeSeries = timeSeries,
        actions = actions.timeSeries,
        isExpanded = isTimeSeriesExpanded,
        onToggleExpanded = { actions.dashboard.toggleTimeSeriesExpanded() },
        onClose = if (isFormOpen) null else ({ actions.dashboard.closeTimeSeries() }),
        expandedHeight = expandedTableHeight,
        modifier = Modifier.padding(horizontal = DashboardBottomPanelGutter),
      )
    }
  }
}
