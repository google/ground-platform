/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.devtools.prototypeapp.ui.navigation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.SatelliteAlt
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.groundplatform.v2.core.forms.ui.GroundAlertDialogOverlay
import org.groundplatform.v2.core.forms.ui.GroundBadgeTone
import org.groundplatform.v2.core.forms.ui.GroundModalBottomSheetOverlay
import org.groundplatform.v2.core.forms.ui.GroundTonalBadge
import org.groundplatform.v2.devtools.prototypeapp.PrototypeAppState
import org.groundplatform.v2.devtools.prototypeapp.domain.model.BasemapType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImagerySource
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MainDrawerSubView
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapFeatureCluster
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapScaleBarSpec
import org.groundplatform.v2.devtools.prototypeapp.domain.model.NavigationTargetKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.StraightLineNavigationState
import org.groundplatform.v2.devtools.prototypeapp.domain.model.formatClusterSitesCountLabel
import org.groundplatform.v2.devtools.prototypeapp.domain.model.geometryKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.hasGeometry
import org.groundplatform.v2.devtools.prototypeapp.domain.model.maxFramingZoom
import org.groundplatform.v2.devtools.prototypeapp.ui.common.horizontalScrollWithMouseDrag
import org.groundplatform.v2.devtools.prototypeapp.ui.datacollection.SurveyPersistentBottomSheetContent
import org.groundplatform.v2.devtools.prototypeapp.ui.map.BasemapOptionOrder
import org.groundplatform.v2.devtools.prototypeapp.ui.map.BasemapOptionRow
import org.groundplatform.v2.devtools.prototypeapp.ui.map.BasemapPreviewCard
import org.groundplatform.v2.devtools.prototypeapp.ui.map.SurveyMainMap
import org.groundplatform.v2.devtools.prototypeapp.ui.map.framingInsets
import org.groundplatform.v2.devtools.prototypeapp.ui.map.rememberSurveyMapCamera
import org.groundplatform.v2.devtools.prototypeapp.ui.state.DataCollectionUiState
import org.groundplatform.v2.devtools.prototypeapp.ui.state.SurveyMapUiState
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.DataCollectionActions
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.SurveyMapActions
import org.groundplatform.v2.devtools.prototypeapp.ui.workbench.PrototypeDebugToolsButton

/**
 * 5. Main Survey UI screen (`PrototypeScreen.MAIN_SURVEY`) providing:
 * - Top App Bar with Hamburger Menu and active survey title.
 * - **Map View**: Displays Ground geospatial entities on the map, a basemap preview card that opens
 *   the basemap selector, and an interactive **Entity Bottom Sheet** when an entity is clicked:
 * ```
 *     - Displays `simplestyle-spec` marker symbols (`○`, `◐`, `✓`) and `marker-color` across entity
 *       points, lines, and polygons, and shows the unified chronological `1:N` list of submissions
 *       grouped by form title, timestamp) that can be clicked to inspect full submission details.
 * ```
 * - **Unified Persistent Bottom Sheet (`SurveyPersistentBottomSheetContent`)**:
 * ```
 *     - When no map feature is selected, peeks at the bottom of the map with a Search bar and category
 *       filter chips (`All`, `Places`, `Map features`) and expands into the full
 *       searchable list of grouped map features and places.
 *
 *     - When a map feature is selected (via map tap or list selection), transitions in-place to
 *       `EntityBottomSheetCard` showing its `simplestyle-spec` marker, baseline properties, form
 *       launchers, and `1:N` submission history.
 * ```
 * - **Hamburger Navigation Drawer**: Options for Surveys, Outbox, Uploaded, Offline maps, Change
 *   settings, View Terms of Service, and Sign out.
 */
@Composable
fun MainSurveyScreen(state: PrototypeAppState) {
  val isMapShowing = state.activeDrawerSubView == MainDrawerSubView.NONE
  val surfaceColor = if (isMapShowing) Color.Transparent else MaterialTheme.colorScheme.surface
  val dataCollectionUiState = state.dataCollectionUiState
  val activeQrEntity = dataCollectionUiState.activeQrCodeEntity
  val activePdfSheet = dataCollectionUiState.activeSharedPdfSheet

  Box(modifier = Modifier.fillMaxSize().background(surfaceColor)) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Top App Bar with Hamburger button, Survey Title, and quick sheet toggle
      MainSurveyTopAppBar(state)

      // Main Body: either a Drawer Sub-View (Switch Surveys / Uploads / Offline Maps / Settings)
      // or the unified Map + Persistent Bottom Sheet View
      Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        when (state.activeDrawerSubView) {
          MainDrawerSubView.SWITCH_SURVEYS -> SwitchDownloadedSurveysSubScreen(state)
          MainDrawerSubView.UPLOADS,
          MainDrawerSubView.OUTBOX,
          MainDrawerSubView.UPLOADED -> UploadsMutationsSubScreen(state)
          MainDrawerSubView.MANAGE_OFFLINE_MAPS -> ManageOfflineMapsSubScreen(state)
          MainDrawerSubView.SETTINGS -> SurveySettingsSubScreen(state)
          MainDrawerSubView.NONE -> SurveyMapView(state)
        }
      }
    }

    // Layers Modal Bottom Sheet (confined inside the mobile device frame)
    if (state.isLayersSheetOpen) {
      val mapUiState by state.surveyMap.uiState.collectAsState()
      LayersControlSheet(uiState = mapUiState, actions = state.surveyMap)
    }

    // Available Forms Modal Bottom Sheet (triggered by the bottom-centered FAB)
    if (dataCollectionUiState.isAvailableFormsSheetOpen) {
      AvailableFormsModalSheet(uiState = dataCollectionUiState, actions = state.dataCollection)
    }

    // Slide-over Hamburger Navigation Drawer Overlay
    if (state.isDrawerOpen) {
      MainSurveyNavigationDrawerOverlay(state)
    }

    // Scannable Entity GeoID QR Code Modal Overlay
    if (activeQrEntity != null) {
      EntityQrCodeModalDialog(actions = state.dataCollection, entity = activeQrEntity)
    }

    // Share PDF to Preferred App Modal Overlay (for both Entities and Submissions)
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

/** Top App Bar for the Main Survey UI with Hamburger Menu button and Survey Title/Location. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainSurveyTopAppBar(state: PrototypeAppState) {
  val topBarContainer =
    if (state.isDarkTheme) {
      MaterialTheme.colorScheme.primaryContainer
    } else {
      MaterialTheme.colorScheme.primary
    }
  val onTopBarContainer =
    if (state.isDarkTheme) {
      MaterialTheme.colorScheme.onPrimaryContainer
    } else {
      MaterialTheme.colorScheme.onPrimary
    }

  TopAppBar(
    navigationIcon = {
      IconButton(onClick = { state.updateDrawerOpen(true) }) {
        Icon(
          imageVector = Icons.Outlined.Menu,
          contentDescription = "Open Navigation Drawer",
          tint = onTopBarContainer,
        )
      }
    },
    title = {
      Column {
        Text(
          text = state.activeSurvey.title,
          style = MaterialTheme.typography.titleSmall,
          color = onTopBarContainer,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.LocationOn,
            contentDescription = null,
            tint = onTopBarContainer.copy(alpha = 0.85f),
            modifier = Modifier.size(12.dp),
          )
          Text(
            text = state.activeSurvey.location,
            style = MaterialTheme.typography.labelSmall,
            color = onTopBarContainer.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    },
    actions = { PrototypeDebugToolsButton(state = state, iconTint = onTopBarContainer) },
    colors =
      TopAppBarDefaults.topAppBarColors(
        containerColor = topBarContainer,
        titleContentColor = onTopBarContainer,
        navigationIconContentColor = onTopBarContainer,
        actionIconContentColor = onTopBarContainer,
      ),
  )
}

/**
 * Interactive Map View showing:
 * - Toggleable **Offline Basemap** (`Satellite + Contours` or `Vector Topographic`)
 * - Ground **Geospatial Entities** (`EntityType.GEOSPATIAL`, rendered with solid polygon outlines)
 * - A basemap preview card ([BasemapPreviewCard]) that opens the layers sheet
 *   ([LayersControlSheet]) to select the basemap
 * - A unified persistent bottom sheet (`SurveyPersistentBottomSheetContent`) that peeks with a
 *   search bar by default, expands into the searchable list of map layers, data tables, and places,
 *   and transitions in-place to `EntityBottomSheetCard` when a map feature is selected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SurveyMapView(state: PrototypeAppState) {
  val uiState by state.surveyMap.uiState.collectAsState()
  val actions: SurveyMapActions = state.surveyMap
  val selectedEntity = uiState.selectedEntity

  val sheetState =
    rememberStandardBottomSheetState(
      initialValue =
        if (uiState.isEntityBottomSheetExpanded) {
          SheetValue.Expanded
        } else {
          SheetValue.PartiallyExpanded
        },
      skipHiddenState = true,
    )
  val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

  LaunchedEffect(sheetState.currentValue) {
    val expanded = sheetState.currentValue == SheetValue.Expanded
    if (uiState.isEntityBottomSheetExpanded != expanded) {
      actions.updateEntityBottomSheetExpanded(expanded)
    }
  }

  LaunchedEffect(uiState.selectedEntityId, uiState.isEntityBottomSheetExpanded) {
    if (uiState.isEntityBottomSheetExpanded && sheetState.currentValue != SheetValue.Expanded) {
      sheetState.expand()
    } else if (
      !uiState.isEntityBottomSheetExpanded &&
        sheetState.currentValue != SheetValue.PartiallyExpanded
    ) {
      sheetState.partialExpand()
    }
  }

  val mapCamera =
    rememberSurveyMapCamera(desired = actions::desiredMapCamera, onSettled = actions::syncMapCamera)

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    // With a map feature selected, the sheet peeks at about half the screen: enough to show the
    // feature's details while the map above frames the feature itself.
    val peekHeight =
      if (selectedEntity != null || state.dataCollectionUiState.selectedSubmission != null) {
        (maxHeight * 0.45f).coerceAtLeast(152.dp)
      } else {
        122.dp
      }

    // Fit a newly selected map feature into the map area above the sheet. Mobile has no side
    // details panel, so only the sheet is compensated for (vertically); the feature stays centered
    // horizontally. Records without geometry replace the sheet's contents without moving the map.
    LaunchedEffect(selectedEntity?.id, uiState.entitySelectionEpoch) {
      val entity = selectedEntity ?: return@LaunchedEffect
      if (!entity.hasGeometry) return@LaunchedEffect
      val visibleHeight = (maxHeight - peekHeight).coerceAtLeast(0.dp)
      val targetScreenY =
        if (maxHeight > 0.dp) ((visibleHeight / 2) / maxHeight).coerceIn(0.10f, 0.50f) else 0.50f
      actions.recenterMapOnEntity(entity, targetScreenY)
      val bounds = state.resolveEntityLngLatBounds(entity)
      mapCamera.run {
        it.fitBounds(
          bounds,
          framingInsets(it.viewportSize, bottom = peekHeight),
          maxZoom = entity.geometryKind.maxFramingZoom.toDouble(),
        )
      }
    }

    BottomSheetScaffold(
      scaffoldState = scaffoldState,
      sheetPeekHeight = peekHeight,
      sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
      sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
      sheetTonalElevation = 0.dp,
      sheetShadowElevation = 8.dp,
      sheetSwipeEnabled = true,
      sheetDragHandle = { BottomSheetDefaults.DragHandle() },
      containerColor = Color.Transparent,
      sheetContent = {
        SurveyPersistentBottomSheetContent(
          state = state,
          modifier = Modifier.fillMaxWidth().fillMaxHeight(0.84f),
        )
      },
    ) {
      BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {
        // Survey map: native basemap and feature layers with Compose markers on top.
        SurveyMainMap(
          uiState = uiState,
          actions = actions,
          dataCollection = state.dataCollectionUiState,
          formMap = state.dataCollection,
          pendingIds = state.pendingUploadEntityIds,
          camera = mapCamera,
          modifier = Modifier.fillMaxSize(),
        )

        // 3. Top Map Overlay: Docked Navigation HUD Banner (flush with toolbar) + Floating Map
        // Chips
        Column(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
          // Straight-Line Navigation HUD Banner docked to the top of the screen like Google Maps
          val activeNav = uiState.activeNavigation
          if (activeNav != null) {
            StraightLineNavigationHudBanner(
              navState = activeNav,
              targetTypeLabel = uiState.entitySingularTypeLabel(activeNav.targetId),
              isDarkTheme = state.isDarkTheme,
              actions = actions,
            )
          }

          // Floating Map Chips: GPS Accuracy Chip + "Layers" Button
          Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              // Combined GPS Status / Auto-Center Chip over the map
              GnssStatusChip(
                isFollowingUser = uiState.isCameraFollowingUser,
                gnssStatusLabel = uiState.gnssStatusChipLabel,
              )

              // Basemap preview card that opens the layers sheet
              BasemapPreviewCard(
                selectedBasemapType = uiState.selectedBasemapType,
                onClick = { actions.updateLayersSheetOpen(!uiState.isLayersSheetOpen) },
              )
            }

            // Selected Cluster Balloon detail callout when a Mapbox cluster balloon is tapped
            if (uiState.isMapClusteringActive && uiState.selectedCluster != null) {
              MapClusterBalloonsOverlay(uiState = uiState, actions = actions)
            }
          }
        }

        // 5. Bottom Overlay Stack: Google Maps-style Horizontal Scale Widget in bottom-left,
        //    bottom-center data collection FAB, and optional "Recenter" button in bottom-right
        val isSheetExpanded =
          uiState.isEntityBottomSheetExpanded || sheetState.targetValue == SheetValue.Expanded

        // Scale bar in bottom-left
        Box(
          modifier =
            Modifier.align(Alignment.BottomStart)
              .padding(start = 14.dp, bottom = peekHeight + 10.dp)
        ) {
          GoogleMapsScaleBarWidget(
            scaleSpec = uiState.mapScaleBarSpec,
            isSatellite = uiState.selectedBasemapType == BasemapType.SATELLITE,
          )
        }

        // Optional "Recenter" button in bottom-right when panned away from user
        if (!isSheetExpanded && !uiState.isCameraFollowingUser) {
          ExtendedFloatingActionButton(
            onClick = { actions.recenterMapOnUser() },
            icon = {
              Icon(
                imageVector = Icons.Outlined.MyLocation,
                contentDescription = "Recenter map on GPS location",
                modifier = Modifier.size(18.dp),
              )
            },
            text = {
              Text(
                text = "Recenter",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
              )
            },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier =
              Modifier.align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = peekHeight + 10.dp)
                .height(40.dp),
          )
        }

        // Bottom-centered data collection entry point FAB on mobile (only visible when sheet is
        // collapsed)
        if (!isSheetExpanded) {
          Box(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = peekHeight + 10.dp),
            contentAlignment = Alignment.Center,
          ) {
            DataCollectionFormsFab(actions = state.dataCollection, isDarkTheme = state.isDarkTheme)
          }
        }
      }
    }
  }
}

/**
 * Combined GPS status / auto-center chip over the map: the GNSS fix ([gnssStatusLabel]) while the
 * camera follows the user, or "Panned" once the map was dragged away.
 */
@Composable
internal fun GnssStatusChip(isFollowingUser: Boolean, gnssStatusLabel: String) {
  Surface(
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.92f),
    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    border =
      BorderStroke(
        1.dp,
        if (isFollowingUser) {
          MaterialTheme.colorScheme.inversePrimary
        } else {
          MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.55f)
        },
      ),
    shadowElevation = 2.dp,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Icon(
        imageVector =
          if (isFollowingUser) Icons.Outlined.SatelliteAlt else Icons.Outlined.MyLocation,
        contentDescription = if (isFollowingUser) "GPS Auto-Center" else "Panned",
        tint =
          if (isFollowingUser) {
            MaterialTheme.colorScheme.inversePrimary
          } else {
            MaterialTheme.colorScheme.inverseOnSurface
          },
        modifier = Modifier.size(14.dp),
      )
      Text(
        text = if (isFollowingUser) "GPS: $gnssStatusLabel" else "Panned",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.inverseOnSurface,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

/**
 * Renders the expanded callout card for the currently selected [MapFeatureCluster] balloon when a
 * geographically pinned Mapbox cluster balloon is tapped on the map.
 */
@Composable
internal fun MapClusterBalloonsOverlay(uiState: SurveyMapUiState, actions: SurveyMapActions) {
  val selectedCluster = uiState.selectedCluster ?: return
  SelectedClusterBalloonDetailCard(
    sitesCountLabel = uiState.formatClusterSitesCountLabel(selectedCluster.siteCount),
    cluster = selectedCluster,
    onZoomIn = { actions.zoomIntoCluster(selectedCluster.id) },
    onDismiss = { actions.selectCluster(null) },
  )
}

/**
 * Expanded callout card for the currently selected [MapFeatureCluster] balloon showing the count of
 * map features and their workflow states.
 */
@Composable
internal fun SelectedClusterBalloonDetailCard(
  sitesCountLabel: String,
  cluster: MapFeatureCluster,
  onZoomIn: () -> Unit,
  onDismiss: () -> Unit,
) {
  Surface(
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.96f),
    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.inversePrimary),
    shadowElevation = 6.dp,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = sitesCountLabel,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.inversePrimary,
          fontWeight = FontWeight.Bold,
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          TextButton(
            onClick = onZoomIn,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            modifier = Modifier.height(26.dp),
          ) {
            Text(
              text = "Zoom in +",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.inversePrimary,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              softWrap = false,
            )
          }
          IconButton(onClick = onDismiss, modifier = Modifier.size(22.dp)) {
            Icon(
              imageVector = Icons.Outlined.Close,
              contentDescription = "Dismiss cluster balloon",
              tint = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.8f),
              modifier = Modifier.size(14.dp),
            )
          }
        }
      }

      if (cluster.siteSymbolGroups.isNotEmpty()) {
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScrollWithMouseDrag(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          cluster.siteSymbolGroups.forEach { group ->
            val groupColor = Color(group.colorHex)
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.10f),
              contentColor = MaterialTheme.colorScheme.inverseOnSurface,
              border = BorderStroke(1.dp, groupColor.copy(alpha = 0.85f)),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
              ) {
                if (group.isNoSymbolGroup) {
                  Box(
                    modifier =
                      Modifier.size(12.dp)
                        .clip(CircleShape)
                        .background(groupColor.copy(alpha = 0.28f))
                        .border(1.5.dp, groupColor, CircleShape)
                  )
                } else {
                  Box(
                    modifier = Modifier.size(14.dp).clip(CircleShape).background(groupColor),
                    contentAlignment = Alignment.Center,
                  ) {
                    Text(
                      text = group.markerSymbol,
                      style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                      color = contentColorOnArgb(group.colorHex),
                      fontWeight = FontWeight.ExtraBold,
                    )
                  }
                }
                Text(
                  text =
                    if (group.isNoSymbolGroup) {
                      "Unmarked map feature: ${group.count}"
                    } else {
                      "${group.statusLabel}: ${group.count}"
                    },
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.inverseOnSurface,
                  fontWeight = FontWeight.SemiBold,
                )
              }
            }
          }
        }
      }
    }
  }
}

private fun contentColorOnArgb(argb: Long): Color {
  val r = ((argb shr 16) and 0xFF) / 255f
  val g = ((argb shr 8) and 0xFF) / 255f
  val b = (argb and 0xFF) / 255f
  val luminance = 0.299f * r + 0.587f * g + 0.114f * b
  return if (luminance > 0.6f) Color(0xFF181D18) else Color.White
}

/**
 * Floating Action Button (`FloatingActionButton`) on the Main Survey screen that opens the
 * [AvailableFormsModalSheet] list of available forms
 * ([DataCollectionActions.openAvailableFormsSheet]) to start data collection without requiring a
 * geospatial entity to be pre-selected from the map. [isDarkTheme] picks the FAB's colors.
 */
@Composable
internal fun DataCollectionFormsFab(
  actions: DataCollectionActions,
  isDarkTheme: Boolean,
  modifier: Modifier = Modifier,
) {
  val formsBg =
    if (isDarkTheme) {
      MaterialTheme.colorScheme.primary
    } else {
      MaterialTheme.colorScheme.primaryContainer
    }
  val formsContent =
    if (isDarkTheme) {
      MaterialTheme.colorScheme.onPrimary
    } else {
      MaterialTheme.colorScheme.onPrimaryContainer
    }
  FloatingActionButton(
    onClick = { actions.openAvailableFormsSheet() },
    modifier = modifier,
    containerColor = formsBg,
    contentColor = formsContent,
  ) {
    Icon(imageVector = Icons.Outlined.Add, contentDescription = "Collect data")
  }
}

/**
 * Modal bottom sheet opened by the bottom-centered [DataCollectionFormsFab] listing all available
 * forms in the active survey ([DataCollectionUiState.mobileForms]). Selecting a form launches data
 * collection via [DataCollectionActions.launchFormFromFab], which presents the Map or List entity
 * selector at the step in the data collection process where an `entityref` is required.
 */
@Composable
internal fun AvailableFormsModalSheet(
  uiState: DataCollectionUiState,
  actions: DataCollectionActions,
) {
  GroundModalBottomSheetOverlay(onDismissRequest = { actions.closeAvailableFormsSheet() }) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Available Data Collection Forms",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "Select a form to start data collection.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(onClick = { actions.closeAvailableFormsSheet() }) {
          Icon(imageVector = Icons.Outlined.Close, contentDescription = "Close Available Forms")
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      uiState.mobileForms.forEach { form ->
        val eligibleCount = uiState.eligibleEntitiesForForm(form).size
        val canLaunch = !form.requiresEntity || eligibleCount > 0

        OutlinedCard(
          onClick = { if (canLaunch) actions.launchFormFromFab(form.id) },
          enabled = canLaunch,
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.medium,
          colors =
            CardDefaults.outlinedCardColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = form.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
              )
              Spacer(modifier = Modifier.width(8.dp))
              GroundTonalBadge(
                text = "${form.questionCount} questions",
                tone = GroundBadgeTone.PRIMARY,
              )
            }

            Text(
              text = form.description,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
              onClick = { actions.launchFormFromFab(form.id) },
              enabled = canLaunch,
              modifier = Modifier.align(Alignment.End),
            ) {
              Icon(
                imageVector = Icons.Outlined.Description,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = form.ctaLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
              )
            }
          }
        }
      }
    }
  }
}

/**
 * Interactive Straight-Line Navigation HUD Banner docked to the top of the Map View (like Google
 * Maps) using the toolbar background color whenever straight-line navigation to a Geospatial Entity
 * (`NavigationTargetKind.ENTITY`) or a Form Submission (`NavigationTargetKind.SUBMISSION`) is
 * active.
 */
@Composable
internal fun StraightLineNavigationHudBanner(
  navState: StraightLineNavigationState,
  targetTypeLabel: String,
  isDarkTheme: Boolean,
  actions: SurveyMapActions,
) {
  val colors = MaterialTheme.colorScheme
  val topBarContainer =
    if (isDarkTheme) {
      colors.primaryContainer
    } else {
      colors.primary
    }
  val onTopBarContainer =
    if (isDarkTheme) {
      colors.onPrimaryContainer
    } else {
      colors.onPrimary
    }
  val accentColor =
    when (navState.targetKind) {
      NavigationTargetKind.ENTITY -> colors.tertiaryContainer
      NavigationTargetKind.PLACE -> colors.secondaryContainer
      NavigationTargetKind.SUBMISSION -> colors.inversePrimary
    }
  val kindLabel =
    when (navState.targetKind) {
      NavigationTargetKind.ENTITY -> "${targetTypeLabel.uppercase()} WAYFINDING"
      NavigationTargetKind.PLACE -> "PLACE WAYFINDING"
      NavigationTargetKind.SUBMISSION -> "SUBMISSION WAYFINDING"
    }

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
    color = topBarContainer,
    contentColor = onTopBarContainer,
    shadowElevation = 6.dp,
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      // Subtle top divider separating the docked navigation banner from the top toolbar
      HorizontalDivider(color = onTopBarContainer.copy(alpha = 0.16f))

      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        // Primary Wayfinding Row: Rotating Compass Arrow + Target Details + Distance/Bearing/Walk
        // Readout
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          // Rotating Compass Arrow Badge
          Surface(
            modifier = Modifier.size(38.dp),
            shape = CircleShape,
            color = colors.secondaryContainer,
            contentColor = colors.onSecondaryContainer,
            border = BorderStroke(1.5.dp, colors.outlineVariant),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Outlined.Navigation,
                contentDescription = "Compass Bearing Arrow",
                tint = colors.onSecondaryContainer,
                modifier =
                  Modifier.size(19.dp)
                    .graphicsLayer(rotationZ = navState.vector.bearingDegrees.toFloat()),
              )
            }
          }

          // Target Type Pill, Title & Subtitle
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Surface(
              shape = CircleShape,
              color = colors.secondaryContainer,
              contentColor = colors.onSecondaryContainer,
            ) {
              Text(
                text = kindLabel,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSecondaryContainer,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
              )
            }

            Text(
              text = navState.targetTitle,
              style = MaterialTheme.typography.titleSmall,
              color = onTopBarContainer,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )

            Text(
              text = navState.targetSubtitle,
              style = MaterialTheme.typography.labelSmall,
              color = onTopBarContainer.copy(alpha = 0.85f),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }

          // Distance + Bearing + Walk Time Readout Column (never wraps vertically)
          Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Text(
              text = navState.formattedDistance,
              style =
                MaterialTheme.typography.titleMedium.copy(
                  fontWeight = FontWeight.ExtraBold,
                  color = onTopBarContainer,
                ),
              maxLines = 1,
              softWrap = false,
            )
            Text(
              text = "Bearing ${navState.vector.formattedBearing}",
              style =
                MaterialTheme.typography.labelSmall.copy(
                  color = onTopBarContainer,
                  fontWeight = FontWeight.Bold,
                ),
              maxLines = 1,
              softWrap = false,
            )
            Text(
              text =
                if (navState.vector.isArrived) {
                  "ARRIVED (≤ 8 m)"
                } else {
                  "~${navState.vector.estimatedWalkMinutes} min walk"
                },
              style = MaterialTheme.typography.labelSmall,
              color = onTopBarContainer.copy(alpha = 0.85f),
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              softWrap = false,
            )
          }
        }

        HorizontalDivider(color = onTopBarContainer.copy(alpha = 0.14f))

        // Bottom Control Strip: Geodesic Vector Status + High-Contrast "Walk Closer" & "Stop" Pills
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Straight-line geodesic vector from your GPS location",
            style = MaterialTheme.typography.labelSmall,
            color = onTopBarContainer.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )

          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            if (!navState.vector.isArrived) {
              Surface(
                onClick = { actions.stepUserTowardNavigationTarget() },
                shape = CircleShape,
                color = colors.secondaryContainer,
                contentColor = colors.onSecondaryContainer,
                border = BorderStroke(1.dp, colors.outlineVariant),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Explore,
                    contentDescription = "Simulate walking closer to target",
                    tint = colors.onSecondaryContainer,
                    modifier = Modifier.size(14.dp),
                  )
                  Text(
                    text = "Walk Closer",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSecondaryContainer,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                  )
                }
              }
            }

            Surface(
              onClick = { actions.stopNavigation() },
              shape = CircleShape,
              color = colors.errorContainer,
              contentColor = colors.onErrorContainer,
              border = BorderStroke(1.dp, colors.error.copy(alpha = 0.6f)),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(
                  imageVector = Icons.Outlined.Close,
                  contentDescription = "Stop Straight-Line Navigation",
                  tint = colors.onErrorContainer,
                  modifier = Modifier.size(14.dp),
                )
                Text(
                  text = "Stop",
                  style = MaterialTheme.typography.labelSmall,
                  color = colors.onErrorContainer,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  softWrap = false,
                )
              }
            }
          }
        }
      }

      // Bottom accent bar matching the active navigation target kind
      Box(
        modifier =
          Modifier.fillMaxWidth().height(2.5.dp).background(accentColor.copy(alpha = 0.85f))
      )
    }
  }
}

/**
 * Google Maps-style horizontal scale bar widget rendered in the bottom-left corner of the map.
 * Displays the current ground distance label (`100 m`, `200 m`, `1 km`, etc.) alongside a
 * dynamically sized horizontal scale bracket (`|_________|`) that adapts to Map vs Satellite tiles.
 */
@Composable
internal fun GoogleMapsScaleBarWidget(
  scaleSpec: MapScaleBarSpec,
  isSatellite: Boolean,
  modifier: Modifier = Modifier,
) {
  val animatedWidthDp by
    animateDpAsState(
      targetValue = scaleSpec.barWidthDp.dp,
      animationSpec = tween(durationMillis = 180),
      label = "googleMapsScaleBarWidth",
    )
  val bgColor =
    if (isSatellite) {
      MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.90f)
    } else {
      MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    }
  val primaryColor = MaterialTheme.colorScheme.onSurface
  val haloColor = MaterialTheme.colorScheme.surface

  Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.small,
    color = bgColor,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    shadowElevation = 3.dp,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
      Text(
        text = scaleSpec.label,
        style = MaterialTheme.typography.labelSmall,
        color = primaryColor,
        fontWeight = FontWeight.Bold,
      )

      Canvas(modifier = Modifier.width(animatedWidthDp).height(9.dp)) {
        val leftX = 1.dp.toPx()
        val rightX = (size.width - 1.dp.toPx()).coerceAtLeast(leftX + 4f)
        val topY = 1.dp.toPx()
        val bottomY = size.height - 1.5.dp.toPx()
        val haloStroke = 3.2.dp.toPx()
        val mainStroke = 1.6.dp.toPx()

        drawLine(
          color = haloColor,
          start = Offset(leftX, bottomY),
          end = Offset(rightX, bottomY),
          strokeWidth = haloStroke,
        )
        drawLine(
          color = haloColor,
          start = Offset(leftX, topY),
          end = Offset(leftX, bottomY),
          strokeWidth = haloStroke,
        )
        drawLine(
          color = haloColor,
          start = Offset(rightX, topY),
          end = Offset(rightX, bottomY),
          strokeWidth = haloStroke,
        )

        drawLine(
          color = primaryColor,
          start = Offset(leftX, bottomY),
          end = Offset(rightX, bottomY),
          strokeWidth = mainStroke,
        )
        drawLine(
          color = primaryColor,
          start = Offset(leftX, topY),
          end = Offset(leftX, bottomY),
          strokeWidth = mainStroke,
        )
        drawLine(
          color = primaryColor,
          start = Offset(rightX, topY),
          end = Offset(rightX, bottomY),
          strokeWidth = mainStroke,
        )
      }
    }
  }
}

/** Small uppercase primary-colored section heading in the basemap selector (`TYPE`, ...). */
@Composable
private fun BasemapSectionHeading(text: String, modifier: Modifier = Modifier) {
  Text(
    text = text,
    style =
      MaterialTheme.typography.labelSmall.copy(
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 0.5.sp,
      ),
    modifier = modifier,
  )
}

/**
 * Shared body of the basemap selector, used by the mobile layers sheet ([LayersControlSheet]) and
 * the web dashboard's dialog ([LayersControlDialog]). Map layer visibility is not set here: mobile
 * toggles it in the bottom sheet and the web dashboard in its left-hand panel.
 * 1. Basemap type options (`Satellite` and `Map`)
 * 2. `"All users"` and survey-specific organization imagery layers (XYZ tile URLs)
 * 3. Downloaded (offline) basemap overlay toggle, when [showOfflineBasemap] (mobile only)
 */
@Composable
internal fun LayersSelectorContent(
  uiState: SurveyMapUiState,
  actions: SurveyMapActions,
  modifier: Modifier = Modifier,
  showOfflineBasemap: Boolean = true,
) {
  val allUsersSources = uiState.allUsersImagerySources
  val surveyOrgName = uiState.activeSurveyOrganizationName
  val surveyOrgSources = uiState.activeSurveyOrganizationImagerySources

  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
      BasemapSectionHeading(text = "TYPE", modifier = Modifier.padding(bottom = 4.dp))
      BasemapOptionOrder.forEach { basemap ->
        BasemapOptionRow(
          type = basemap,
          isSelected = uiState.selectedBasemapType == basemap,
          onSelect = { actions.selectBasemapType(basemap) },
        )
      }
    }

    if (allUsersSources.isNotEmpty()) {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasemapSectionHeading(text = "IMAGERY · ALL USERS")
        allUsersSources.forEach { source ->
          ImagerySourceLayerCard(
            source = source,
            organizationName = "All users",
            isEnabled = uiState.isImagerySourceEnabled(source.id),
            isMobile = showOfflineBasemap,
            onToggle = { actions.toggleImagerySource(source.id) },
          )
        }
      }
    }

    if (surveyOrgName != null && surveyOrgSources.isNotEmpty()) {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasemapSectionHeading(text = "IMAGERY · ${surveyOrgName.uppercase()}")
        surveyOrgSources.forEach { source ->
          ImagerySourceLayerCard(
            source = source,
            organizationName = surveyOrgName,
            isEnabled = uiState.isImagerySourceEnabled(source.id),
            isMobile = showOfflineBasemap,
            onToggle = { actions.toggleImagerySource(source.id) },
          )
        }
      }
    }

    if (showOfflineBasemap) {
      BasemapSectionHeading(text = "DOWNLOADED BASEMAPS")
      // Offline Basemap Tile Package Overlay Toggle
      OutlinedCard(
        onClick = { actions.toggleOfflineBasemapVisibility() },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors =
          CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
          ),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Offline Basemap Overlay (Nyeri Sector Tiles)",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = uiState.offlineBasemapStyle.tileDescription,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = uiState.isOfflineBasemapVisible,
            onCheckedChange = { actions.toggleOfflineBasemapVisibility() },
          )
        }
      }
    }
  }
}

@Composable
private fun ImagerySourceLayerCard(
  source: ImagerySource,
  organizationName: String,
  isEnabled: Boolean,
  isMobile: Boolean,
  onToggle: () -> Unit,
) {
  val subtitle =
    if (isMobile) {
      val offlineStatus =
        if (source.allowOfflineDownload) "Offline download permitted"
        else "Online only · offline download disabled"
      "$organizationName · $offlineStatus"
    } else {
      "$organizationName · ${source.type.label}"
    }
  OutlinedCard(
    onClick = onToggle,
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    colors =
      CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = source.name,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Switch(checked = isEnabled, onCheckedChange = { onToggle() })
    }
  }
}

/**
 * Material 3 modal bottom sheet opened by the mobile map's [BasemapPreviewCard] to select the
 * **Basemap Type (`Map` vs `Satellite`)**, toggle organization imagery layers, and toggle the
 * **Offline Basemap (`Mapbox Offline Tiles`)**.
 */
@Composable
internal fun LayersControlSheet(uiState: SurveyMapUiState, actions: SurveyMapActions) {
  GroundModalBottomSheetOverlay(onDismissRequest = { actions.updateLayersSheetOpen(false) }) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(
            text = "Basemap",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "Select basemap type, organization imagery, and offline tile overlays",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(onClick = { actions.updateLayersSheetOpen(false) }) {
          Icon(imageVector = Icons.Outlined.Close, contentDescription = "Close layers")
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      LayersSelectorContent(uiState = uiState, actions = actions)

      Spacer(modifier = Modifier.height(12.dp))
    }
  }
}

/**
 * Modal dialog opened by the web dashboard's [BasemapPreviewCard] to select the basemap type (`Map`
 * vs `Satellite`) and toggle `"All users"` and survey-specific organization imagery layers. Offline
 * maps are a mobile-only feature, so their toggle is left out.
 */
@Composable
internal fun LayersControlDialog(uiState: SurveyMapUiState, actions: SurveyMapActions) {
  GroundAlertDialogOverlay(
    onDismissRequest = { actions.updateLayersSheetOpen(false) },
    title = {
      Text(
        text = "Basemap",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
      )
    },
    text = {
      Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        LayersSelectorContent(uiState = uiState, actions = actions, showOfflineBasemap = false)
      }
    },
    confirmButton = {
      TextButton(onClick = { actions.updateLayersSheetOpen(false) }) { Text("Done") }
    },
  )
}

/** Backward-compatible alias for [MainSurveyScreen]. */
@Composable
fun GroundMainSurveyScreen(state: PrototypeAppState) {
  MainSurveyScreen(state)
}
