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
package org.groundplatform.v2.devtools.prototypeapp

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import org.groundplatform.v2.core.forms.ui.GroundAlertDialogOverlay
import org.groundplatform.v2.core.forms.ui.GroundBadgeTone
import org.groundplatform.v2.core.forms.ui.GroundModalBottomSheetOverlay
import org.groundplatform.v2.core.forms.ui.GroundTonalBadge
import org.groundplatform.v2.core.forms.ui.LocalGroundBrandFontFamily

/**
 * 5. Main Survey UI screen (`PrototypeScreen.MAIN_SURVEY`) providing:
 * - Top App Bar with Hamburger Menu and active survey title.
 * - **Map View**: Displays Ground geospatial entities on the map, a `"Layers"` button to toggle
 * layer visibility, and an interactive **Entity Bottom Sheet** when an entity is clicked:
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
 *     - When a map feature is selected (via map tap or list selection), transitions in-place to
 *       `EntityBottomSheetCard` showing its `simplestyle-spec` marker, baseline properties, form
 *       launchers, and `1:N` submission history.
 * ```
 * - **Hamburger Navigation Drawer**: Options for Surveys, Outbox, Uploaded, Offline maps, Change
 * settings, View Terms of Service, and Sign out.
 */
@Composable
fun GroundMainSurveyScreen(state: PrototypeAppState) {
  val isMapShowing = state.activeDrawerSubView == MainDrawerSubView.NONE
  val surfaceColor = if (isMapShowing) Color.Transparent else MaterialTheme.colorScheme.surface
  val activeQrEntity = state.activeQrCodeEntity
  val activePdfSheet = state.activeSharedPdfSheet

  Box(modifier = Modifier.fillMaxSize().background(surfaceColor)) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Top App Bar with Hamburger button, Survey Title, and quick sheet toggle
      MainSurveyTopAppBar(state)

      // Main Body: either a Drawer Sub-View (Switch Surveys / Uploads / Offline Maps / Settings)
      // or the unified Map + Persistent Bottom Sheet View
      Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        when (state.activeDrawerSubView) {
          MainDrawerSubView.SWITCH_SURVEYS -> SwitchDownloadedSurveysSubScreen(state)
          MainDrawerSubView.UPLOADS, MainDrawerSubView.OUTBOX, MainDrawerSubView.UPLOADED ->
            UploadsMutationsSubScreen(state)
          MainDrawerSubView.MANAGE_OFFLINE_MAPS -> ManageOfflineMapsSubScreen(state)
          MainDrawerSubView.SETTINGS -> SurveySettingsSubScreen(state)
          MainDrawerSubView.NONE -> SurveyMapView(state)
        }
      }
    }

    // Layers Modal Bottom Sheet (confined inside the mobile device frame)
    if (state.isLayersSheetOpen) {
      LayersControlSheet(state = state)
    }

    // Available Forms Modal Bottom Sheet (triggered by the bottom-centered FAB)
    if (state.isAvailableFormsSheetOpen) {
      AvailableFormsModalSheet(state = state)
    }

    // Slide-over Hamburger Navigation Drawer Overlay
    if (state.isDrawerOpen) {
      MainSurveyNavigationDrawerOverlay(state)
    }

    // Scannable Entity GeoID QR Code Modal Overlay
    if (activeQrEntity != null) {
      EntityQrCodeModalDialog(state = state, entity = activeQrEntity)
    }

    // Share PDF to Preferred App Modal Overlay (for both Entities and Submissions)
    if (activePdfSheet != null) {
      SharePdfToAppModalDialog(state = state, sheet = activePdfSheet)
    }
  }
}

/** Top App Bar for the Main Survey UI with Hamburger Menu button and Survey Title/Location. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainSurveyTopAppBar(state: PrototypeAppState) {
  val topBarContainer =
    if (state.isDarkTheme) {
      MaterialTheme.colorScheme.primaryContainer
    } else {
      MaterialTheme.colorScheme.primary
    }

  TopAppBar(
    navigationIcon = {
      IconButton(onClick = { state.updateDrawerOpen(true) }) {
        Icon(
          imageVector = Icons.Default.Menu,
          contentDescription = "Open Navigation Drawer",
          tint = Color.White,
        )
      }
    },
    title = {
      Column {
        Text(
          text = state.activeSurvey.title,
          style = MaterialTheme.typography.titleSmall,
          color = Color.White,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = Color(0xFFB7F1B9),
            modifier = Modifier.size(12.dp),
          )
          Text(
            text =
              "${state.activeSurvey.location} • ${state.visibleMapEntities.size} ${state.activeEntitiesCountNoun} on map",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFB7F1B9),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    },
    colors =
      TopAppBarDefaults.topAppBarColors(
        containerColor = topBarContainer,
        titleContentColor = Color.White,
        navigationIconContentColor = Color.White,
        actionIconContentColor = Color.White,
      ),
  )
}

/**
 * Interactive Map View showing:
 * - Toggleable **Offline Basemap** (`Satellite + Contours` or `Vector Topographic`)
 * - Ground **Geospatial Entities** (`EntityType.GEOSPATIAL`, rendered with solid polygon outlines)
 * - A `"Layers"` button (`LayersControlSheet`) to toggle basemaps and map layers
 * - A unified persistent bottom sheet (`SurveyPersistentBottomSheetContent`) that peeks with a
 * search bar by default, expands into the searchable list of map layers, data tables, and places,
 * and transitions in-place to `EntityBottomSheetCard` when a map feature is selected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SurveyMapView(state: PrototypeAppState) {
  val selectedEntity = state.selectedEntity

  val sheetState =
    rememberStandardBottomSheetState(
      initialValue =
        if (state.isEntityBottomSheetExpanded) {
          SheetValue.Expanded
        } else {
          SheetValue.PartiallyExpanded
        },
      skipHiddenState = true,
    )
  val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

  LaunchedEffect(sheetState.currentValue) {
    val expanded = sheetState.currentValue == SheetValue.Expanded
    if (state.isEntityBottomSheetExpanded != expanded) {
      state.updateEntityBottomSheetExpanded(expanded)
    }
  }

  LaunchedEffect(state.selectedEntityId, state.isEntityBottomSheetExpanded) {
    if (state.isEntityBottomSheetExpanded && sheetState.currentValue != SheetValue.Expanded) {
      sheetState.expand()
    } else if (!state.isEntityBottomSheetExpanded &&
        sheetState.currentValue != SheetValue.PartiallyExpanded
    ) {
      sheetState.partialExpand()
    }
  }

  val peekHeight =
    if (selectedEntity != null || state.selectedSubmission != null) {
      152.dp
    } else {
      122.dp
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
      // Smoothly animate back to center when "Recenter" is tapped, or follow drag immediately
      val shiftAnimSpec =
        if (state.isCameraFollowingUser) {
          tween<Float>(durationMillis = 280)
        } else {
          snap()
        }
      val animatedShiftX by
        animateFloatAsState(
          targetValue = state.mapWorldToScreenShiftX,
          animationSpec = shiftAnimSpec,
          label = "mapShiftX",
        )
      val animatedShiftY by
        animateFloatAsState(
          targetValue = state.mapWorldToScreenShiftY,
          animationSpec = shiftAnimSpec,
          label = "mapShiftY",
        )

      // 1. Real Mapbox GL JS Basemap (mapboxgl.Map via window.GroundMapboxBridge) + GeoJSON Layers
      // & Mapbox Markers
      MapboxBasemapView(
        state = state,
        animatedShiftX = animatedShiftX,
        animatedShiftY = animatedShiftY,
        modifier = Modifier.fillMaxSize(),
      )

      // 3. Top Map Overlay: Docked Navigation HUD Banner (flush with toolbar) + Floating Map Chips
      Column(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
        // Straight-Line Navigation HUD Banner docked to the top of the screen like Google Maps
        val activeNav = state.activeNavigation
        if (activeNav != null) {
          StraightLineNavigationHudBanner(navState = activeNav, state = state)
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
            // GPS Current Horizontal Accuracy Chip over the map
            Surface(
              shape = MaterialTheme.shapes.large,
              color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.92f),
              contentColor = MaterialTheme.colorScheme.inverseOnSurface,
              border = BorderStroke(1.dp, Color(0xFF4CAF50)),
              shadowElevation = 2.dp,
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.SatelliteAlt,
                  contentDescription = "GPS Accuracy",
                  tint = Color(0xFF8BD6B1),
                  modifier = Modifier.size(14.dp),
                )
                Text(
                  text = "GPS Accuracy: ${state.gnssStatusChipLabel}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.inverseOnSurface,
                  fontWeight = FontWeight.Bold,
                )
              }
            }

            // Layers FAB to control basemaps and map layers
            val layersBg =
              if (state.isLayersSheetOpen) {
                Color(0xFF8BD6B1)
              } else {
                MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.93f)
              }
            val layersContent =
              if (state.isLayersSheetOpen) {
                Color(0xFF003825)
              } else {
                MaterialTheme.colorScheme.inverseOnSurface
              }
            FloatingActionButton(
              onClick = { state.updateLayersSheetOpen(!state.isLayersSheetOpen) },
              containerColor = layersBg,
              contentColor = layersContent,
            ) {
              Icon(
                imageVector = Icons.Default.Layers,
                contentDescription = "Layers",
              )
            }
          }

          // Compact GPS Follow State Chip
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Surface(
              shape = MaterialTheme.shapes.medium,
              color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f),
              border = BorderStroke(1.dp, Color(0xFF2D5944)),
            ) {
              val followColor =
                if (state.isCameraFollowingUser) Color(0xFF8BD6B1) else Color(0xFFFFCC80)
              Text(
                text = if (state.isCameraFollowingUser) "GPS Auto-Center" else "Panned",
                style = MaterialTheme.typography.labelSmall,
                color = followColor,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              )
            }
          }

          // Selected Cluster Balloon detail callout when a Mapbox cluster balloon is tapped
          if (state.isMapClusteringActive && state.selectedCluster != null) {
            MapClusterBalloonsOverlay(state = state)
          }
        }
      }

      // 5. Bottom Overlay Stack: Google Maps-style Horizontal Scale Widget in bottom-left
      //    (plus optional "Recenter" ExtendedFloatingActionButton when map is panned)
      Column(
        modifier =
          Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = peekHeight)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          GoogleMapsScaleBarWidget(
            scaleSpec = state.mapScaleBarSpec,
            isSatellite = state.selectedBasemapType == BasemapType.SATELLITE,
          )

          if (!state.isCameraFollowingUser) {
            ExtendedFloatingActionButton(
              onClick = { state.recenterMapOnUser() },
              icon = {
                Icon(
                  imageVector = Icons.Default.MyLocation,
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
              modifier = Modifier.height(40.dp),
            )
          }
        }

        // Single bottom-centered Floating Action Button to trigger data collection from a form
        DataCollectionFormsFab(
          state = state,
          modifier =
            Modifier.align(Alignment.CenterHorizontally)
              .padding(horizontal = 14.dp, vertical = 6.dp),
        )
      }
    }
  }
}

/**
 * Renders the expanded callout card for the currently selected [MapFeatureCluster] balloon when a
 * geographically pinned Mapbox cluster balloon is tapped on the map.
 */
@Composable
private fun MapClusterBalloonsOverlay(state: PrototypeAppState) {
  val selectedCluster = state.selectedCluster ?: return
  SelectedClusterBalloonDetailCard(
    sitesCountLabel = state.formatClusterSitesCountLabel(selectedCluster.siteCount),
    cluster = selectedCluster,
    onZoomIn = {
      state.zoomIntoCluster(selectedCluster.id)
      zoomPlatformMapboxBasemap(0.75f)
    },
    onDismiss = { state.selectCluster(null) },
  )
}

/**
 * Expanded callout card for the currently selected [MapFeatureCluster] balloon showing the count of
 * map features and their workflow states.
 */
@Composable
private fun SelectedClusterBalloonDetailCard(
  sitesCountLabel: String,
  cluster: MapFeatureCluster,
  onZoomIn: () -> Unit,
  onDismiss: () -> Unit,
) {
  Surface(
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.96f),
    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    border = BorderStroke(1.5.dp, Color(0xFF8BD6B1)),
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
          color = Color(0xFF8BD6B1),
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
              color = Color(0xFF8BD6B1),
              fontWeight = FontWeight.Bold,
            )
          }
          IconButton(onClick = onDismiss, modifier = Modifier.size(22.dp)) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Dismiss cluster balloon",
              tint = Color.White.copy(alpha = 0.8f),
              modifier = Modifier.size(14.dp),
            )
          }
        }
      }

      if (cluster.siteSymbolGroups.isNotEmpty()) {
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          cluster.siteSymbolGroups.forEach { group ->
            val groupColor = Color(group.colorHex)
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = Color.White.copy(alpha = 0.10f),
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
                      color = Color.White,
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
                  color = Color.White,
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

/**
 * Single bottom-centered Floating Action Button (`ExtendedFloatingActionButton`) on the Main Survey
 * screen (`Map` and `List` views) that opens the [AvailableFormsModalSheet] list of available forms
 * to start data collection without requiring a geospatial entity to be pre-selected from the map.
 */
@Composable
private fun DataCollectionFormsFab(state: PrototypeAppState, modifier: Modifier = Modifier) {
  ExtendedFloatingActionButton(
    onClick = { state.openAvailableFormsSheet() },
    modifier = modifier.height(46.dp),
    shape = CircleShape,
    containerColor = MaterialTheme.colorScheme.primary,
    contentColor = MaterialTheme.colorScheme.onPrimary,
    icon = {
      Icon(
        imageVector = Icons.Default.Add,
        contentDescription = "Collect data from a form",
        modifier = Modifier.size(20.dp),
      )
    },
    text = {
      Text(
        text = "Collect data",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
      )
    },
  )
}

/**
 * Modal bottom sheet opened by the bottom-centered [DataCollectionFormsFab] listing all available
 * forms in the active survey. Selecting a form launches data collection via
 * [PrototypeAppState.launchFormFromFab], which presents the Map or List entity selector at the step
 * in the data collection process where an `entityref` is required.
 */
@Composable
private fun AvailableFormsModalSheet(state: PrototypeAppState) {
  GroundModalBottomSheetOverlay(onDismissRequest = { state.closeAvailableFormsSheet() }) {
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
            text =
              "Select a form to start data collection. You will be prompted to select the target location on the Map or List at the step where it is required.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(onClick = { state.closeAvailableFormsSheet() }) {
          Icon(imageVector = Icons.Default.Close, contentDescription = "Close Available Forms")
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      state.forms.forEach { form ->
        val eligibleCount = state.eligibleEntitiesForForm(form).size
        val totalDatasetCount = state.allDatasetEntitiesForForm(form).size
        val canLaunch = !form.requiresEntity || eligibleCount > 0

        OutlinedCard(
          onClick = { if (canLaunch) state.launchFormFromFab(form.id) },
          enabled = canLaunch,
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.medium,
          colors =
            CardDefaults.outlinedCardColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.Description,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = form.title,
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface,
                )
              }
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

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                if (form.requiresEntity) {
                  Text(
                    text = "Target: ${form.targetDatasetName}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                  )
                  Text(
                    text =
                      "$eligibleCount of $totalDatasetCount ${form.targetSingularTypeLabel.lowercase()}(s) available • Select via Map or List in step 1",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                } else {
                  Text(
                    text = "Target: Standalone field log (No map feature required)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                  )
                  Text(
                    text =
                      "Records directly at your current GNSS position without an attached map feature",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }

              Spacer(modifier = Modifier.width(8.dp))

              Button(onClick = { state.launchFormFromFab(form.id) }, enabled = canLaunch) {
                Text(
                  text = form.ctaLabel,
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                )
              }
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
private fun StraightLineNavigationHudBanner(
  navState: StraightLineNavigationState,
  state: PrototypeAppState,
) {
  val topBarContainer =
    if (state.isDarkTheme) {
      MaterialTheme.colorScheme.primaryContainer
    } else {
      MaterialTheme.colorScheme.primary
    }
  val accentColor =
    when (navState.targetKind) {
      NavigationTargetKind.ENTITY -> Color(0xFF80DEEA)
      NavigationTargetKind.PLACE -> Color(0xFFA7FFEB)
      NavigationTargetKind.SUBMISSION -> Color(0xFFFFD54F)
    }
  val kindLabel =
    when (navState.targetKind) {
      NavigationTargetKind.ENTITY ->
        "${state.entitySingularTypeLabel(navState.targetId).uppercase()} WAYFINDING"
      NavigationTargetKind.PLACE -> "PLACE WAYFINDING"
      NavigationTargetKind.SUBMISSION -> "SUBMISSION WAYFINDING"
    }

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
    color = topBarContainer,
    contentColor = Color.White,
    shadowElevation = 6.dp,
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      // Subtle top divider separating the docked navigation banner from the top toolbar
      HorizontalDivider(color = Color.White.copy(alpha = 0.16f))

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
            color = Color(0xFF11422E),
            border = BorderStroke(1.5.dp, accentColor),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Default.Navigation,
                contentDescription = "Compass Bearing Arrow",
                tint = accentColor,
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
              color = Color(0xFF11422E),
              border = BorderStroke(1.dp, accentColor.copy(alpha = 0.65f)),
            ) {
              Text(
                text = kindLabel,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
              )
            }

            Text(
              text = navState.targetTitle,
              style = MaterialTheme.typography.titleSmall,
              color = Color.White,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )

            Text(
              text = navState.targetSubtitle,
              style = MaterialTheme.typography.labelSmall,
              color = Color(0xFFC8E6C9),
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
                  color = Color.White,
                ),
              maxLines = 1,
              softWrap = false,
            )
            Text(
              text = "Bearing ${navState.vector.formattedBearing}",
              style =
                MaterialTheme.typography.labelSmall.copy(
                  color = accentColor,
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
              color = Color(0xFFC8E6C9),
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              softWrap = false,
            )
          }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.14f))

        // Bottom Control Strip: Geodesic Vector Status + High-Contrast "Walk Closer" & "Stop" Pills
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Straight-line geodesic vector from your GPS location",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFC8E6C9),
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
                onClick = { state.stepUserTowardNavigationTarget() },
                shape = CircleShape,
                color = Color(0xFF11422E),
                contentColor = Color.White,
                border = BorderStroke(1.dp, Color(0xFF8BD6B1)),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                  Icon(
                    imageVector = Icons.Default.Explore,
                    contentDescription = "Simulate walking closer to target",
                    tint = Color(0xFF8BD6B1),
                    modifier = Modifier.size(14.dp),
                  )
                  Text(
                    text = "Walk Closer",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                  )
                }
              }
            }

            Surface(
              onClick = { state.stopNavigation() },
              shape = CircleShape,
              color = Color(0xFFB3261E),
              contentColor = Color.White,
              border = BorderStroke(1.dp, Color(0xFFFFCDD2).copy(alpha = 0.75f)),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = "Stop Straight-Line Navigation",
                  tint = Color.White,
                  modifier = Modifier.size(14.dp),
                )
                Text(
                  text = "Stop",
                  style = MaterialTheme.typography.labelSmall,
                  color = Color.White,
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
private fun GoogleMapsScaleBarWidget(
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

/**
 * Material 3 [ModalBottomSheet] opened by the `"Layers"` button to select/toggle:
 * 1. **Basemap Type (`Map` vs `Satellite`)** & **Offline Basemap (`Mapbox Offline Tiles`)**
 * 2. **Map Features (`LayerDef.entity_dataset_id`)** — geospatial entity layers rendered on the map
 */
@Composable
private fun LayersControlSheet(state: PrototypeAppState) {
  GroundModalBottomSheetOverlay(onDismissRequest = { state.updateLayersSheetOpen(false) }) {
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
            text = "Layers & Basemap",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text =
              if (state.hasGeospatialEntities) {
                "Select Map vs Satellite basemap and toggle survey map layers"
              } else {
                "Select Map vs Satellite basemap and offline tile overlays"
              },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(onClick = { state.updateLayersSheetOpen(false) }) {
          Icon(imageVector = Icons.Default.Close, contentDescription = "Close layers")
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      // Basemap section (Map vs Satellite + Offline Basemap Toggle)
      Text(
        text = "BASEMAP",
        style =
          MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 0.5.sp,
          ),
      )

      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        BasemapType.entries.forEachIndexed { index, basemap ->
          val isSelected = state.selectedBasemapType == basemap
          SegmentedButton(
            selected = isSelected,
            onClick = { state.selectBasemapType(basemap) },
            shape =
              SegmentedButtonDefaults.itemShape(index = index, count = BasemapType.entries.size),
            icon = {
              Icon(
                imageVector =
                  if (basemap == BasemapType.NORMAL) {
                    Icons.Default.Map
                  } else {
                    Icons.Default.SatelliteAlt
                  },
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
            },
            label = {
              Text(
                text = basemap.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
              )
            },
          )
        }
      }

      // Offline Basemap Tile Package Overlay Toggle
      OutlinedCard(
        onClick = { state.toggleOfflineBasemapVisibility() },
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
              text = state.offlineBasemapStyle.tileDescription,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = state.isOfflineBasemapVisible,
            onCheckedChange = { state.toggleOfflineBasemapVisibility() },
          )
        }
      }

      if (state.hasGeospatialEntities) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        state.entityDatasetLayers.forEach { layer ->
          val layerEntityCount = state.entities.count { it.layerId == layer.id }
          OutlinedCard(
            onClick = { state.toggleLayerVisibility(layer.id) },
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
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              // Solid layer color swatch
              Box(
                modifier =
                  Modifier.size(16.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(Color(layer.colorHex).copy(alpha = 0.25f))
                    .border(2.dp, Color(layer.colorHex), MaterialTheme.shapes.extraSmall)
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = layer.label,
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.SemiBold,
                )
                Text(
                  text = "${layer.geometryTypeLabel} • ${layer.formatCountLabel(layerEntityCount)}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Switch(
                checked = layer.isVisible,
                onCheckedChange = { state.toggleLayerVisibility(layer.id) },
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))
    }
  }
}

/**
 * Unified Persistent Bottom Sheet content for [SurveyMapView] (Option A):
 * 1. When a [GeospatialEntityItem] is selected (via map tap or list selection), renders
 * ```
 *    [EntityBottomSheetCard] with a `"All map features"` back pill, `simplestyle-spec` marker, baseline
 *    attributes, form launchers, and `1:N` submissions.
 * ```
 * 2. When a standalone [SubmissionPreviewItem] is selected from the searchable list, renders
 * ```
 *    [SubmissionFullDetailsCard] with a back button returning to the searchable list.
 * ```
 * 3. When no specific item is selected, renders [SurveyListView] — peeking at the bottom of the map
 * ```
 *    with a Search bar and category filter chips (`All`, `Places`, `Map features`) and expanding
 *    into the full grouped list.
 * ```
 */
@Composable
private fun SurveyPersistentBottomSheetContent(
  state: PrototypeAppState,
  modifier: Modifier = Modifier,
) {
  val selectedEntity = state.selectedEntity
  val selectedSubmission = state.selectedSubmission
  val isDark = state.isDarkTheme
  val textColor = if (isDark) Color.White else Color(0xFF111827)

  when {
    selectedEntity != null -> {
      EntityBottomSheetCard(entity = selectedEntity, state = state, modifier = modifier)
    }
    selectedSubmission != null -> {
      Column(
        modifier =
          modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 6.dp)
      ) {
        SubmissionFullDetailsCard(
          submission = selectedSubmission,
          state = state,
          isDark = isDark,
          textColor = textColor,
          backLabel = "Back to Searchable List",
          onBack = { state.returnToBottomSheetList() },
          onSharePdf = { state.shareSubmissionPdf(selectedSubmission.id) },
        )
      }
    }
    else -> {
      BottomSheetSearchableListContent(state = state, modifier = modifier)
    }
  }
}

/**
 * Shared Entity Summary Header used in both [EntityBottomSheetCard] and compact list cards in
 * [BottomSheetSearchableListContent].
 */
@Composable
private fun EntitySummaryHeader(
  entity: GeospatialEntityItem,
  state: PrototypeAppState,
  compact: Boolean = false,
  trailingContent: @Composable RowScope.() -> Unit = {},
) {
  val areaFormatted =
    if (state.unitSystem == MeasurementUnitSystem.METRIC) {
      "${entity.areaHectares} ha"
    } else {
      val acres = ((entity.areaHectares * 2.47105) * 100.0).roundToInt() / 100.0
      "$acres acres"
    }
  val perimeterFormatted =
    if (state.unitSystem == MeasurementUnitSystem.METRIC) {
      "${entity.perimeterMeters} m"
    } else {
      val feet = (entity.perimeterMeters * 3.28084).roundToInt()
      "$feet ft"
    }
  val badgeSize = if (compact) 22.dp else 28.dp

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      modifier = Modifier.weight(1f),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.size(badgeSize).clip(CircleShape).background(Color(entity.markerColorHex)),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = entity.markerSymbol,
          style =
            if (compact) {
              MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold)
            } else {
              MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold)
            },
          color = Color.White,
        )
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = entity.label,
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text =
            "${entity.datasetName} • ${entity.geometryTypeLabel} ($areaFormatted, $perimeterFormatted)",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      content = trailingContent,
    )
  }
}

/**
 * Shared row of `simplestyle-spec` workflow status, `1:N` submission count, `GeoID`, sync status,
 * live GNSS wayfinding badge, and quick action chips (`Navigate`, `QR Code`, `Share PDF`).
 */
@Composable
private fun EntityMetadataAndActionsRow(
  entity: GeospatialEntityItem,
  state: PrototypeAppState,
  showShareAndQrActions: Boolean,
) {
  val entityWayfindingBadge = state.formattedWayfindingBadgeForEntity(entity.id)
  val isNavigatingEntity = state.isNavigatingToEntity(entity.id)

  Row(
    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Data-Driven Marker Symbol & Status Badge (○ Pending -> ◐ In progress -> ✓ Completed)
    GroundTonalBadge(
      text = entity.mapStatusSummaryBadge,
      tone =
        when (entity.markerSymbol) {
          "✓" -> GroundBadgeTone.PRIMARY
          "◐" -> GroundBadgeTone.TERTIARY
          else -> GroundBadgeTone.WARNING
        },
    )

    // 1:N Submission Count Badge
    GroundTonalBadge(
      text =
        "${entity.submissionCount} ${if (entity.submissionCount == 1) "submission" else "submissions"}",
      tone = GroundBadgeTone.SECONDARY,
    )

    // GeoID Badge
    GroundTonalBadge(
      text = "GeoID: ${entity.geoId}",
      tone = GroundBadgeTone.SECONDARY,
      monospace = true,
    )

    // Sync Status Indicator Badge
    SyncStatusIndicatorBadge(
      syncStatus = entity.syncStatus,
      onClick = { state.cycleEntitySyncStatus(entity.id) },
    )

    // Live Distance & Compass Bearing Badge from User GPS
    if (entityWayfindingBadge.isNotEmpty()) {
      GroundTonalBadge(
        text = "➤ $entityWayfindingBadge",
        tone = GroundBadgeTone.TERTIARY,
        monospace = true,
      )
    }

    // Straight-Line Navigation Toggle Button for Geospatial Entity
    FilterChip(
      selected = isNavigatingEntity,
      onClick = { state.toggleNavigationToEntity(entity.id) },
      label = {
        Text(
          text = if (isNavigatingEntity) "Stop Nav" else "Navigate",
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        )
      },
      leadingIcon = {
        Icon(
          imageVector = Icons.Default.Navigation,
          contentDescription = "Straight-line navigate to entity",
          modifier = Modifier.size(13.dp),
        )
      },
    )

    if (showShareAndQrActions) {
      // QR Code Link
      AssistChip(
        onClick = { state.openEntityQrCode(entity.id) },
        label = {
          Text(
            text = "QR Code",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
          )
        },
        leadingIcon = {
          Icon(
            imageVector = Icons.Default.QrCode,
            contentDescription = "${entity.singularTypeLabel} QR Code",
            modifier = Modifier.size(13.dp),
          )
        },
      )

      // Share PDF Link
      AssistChip(
        onClick = { state.shareEntityPdf(entity.id) },
        label = {
          Text(
            text = "Share PDF",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
          )
        },
        leadingIcon = {
          Icon(
            imageVector = Icons.Default.Share,
            contentDescription = "Share ${entity.singularTypeLabel} PDF",
            modifier = Modifier.size(13.dp),
          )
        },
      )
    }
  }
}

/**
 * Bottom Sheet detail card shown when a survey location is selected (from the map or the bottom
 * sheet list):
 * - Shows main location metadata via [EntitySummaryHeader] and [EntityMetadataAndActionsRow].
 * - Shows a `"All map features"` back pill to return directly to the searchable list in the bottom sheet.
 * - Shows available form collection buttons and the unified `1:N` list of submissions grouped by
 * form title via [FormGroupedSubmissionsSection].
 */
@Composable
private fun EntityBottomSheetCard(
  entity: GeospatialEntityItem,
  state: PrototypeAppState,
  modifier: Modifier = Modifier,
) {
  val selectedSubmission = state.selectedSubmission
  val isDark = state.isDarkTheme
  val textColor = if (isDark) Color.White else Color(0xFF111827)

  Column(
    modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    // Shared Header + Back to All Map Features pill + Expand/Collapse + Close
    EntitySummaryHeader(
      entity = entity,
      state = state,
      compact = false,
      trailingContent = {
        Surface(
          onClick = { state.returnToBottomSheetList() },
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.secondaryContainer,
          contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back to all map features list",
              modifier = Modifier.size(12.dp),
            )
            Text(
              text = "All map features",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            )
          }
        }

        IconButton(
          onClick = { state.toggleEntityBottomSheetExpanded() },
          modifier = Modifier.size(28.dp),
        ) {
          Icon(
            imageVector =
              if (state.isEntityBottomSheetExpanded) {
                Icons.Default.KeyboardArrowDown
              } else {
                Icons.Default.KeyboardArrowUp
              },
            contentDescription = "Toggle Sheet Expansion",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
          )
        }

        IconButton(onClick = { state.selectEntity(null) }, modifier = Modifier.size(28.dp)) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close Location Sheet",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
          )
        }
      },
    )

    // Shared Metadata Badges & Share/Navigate Actions Row
    EntityMetadataAndActionsRow(entity = entity, state = state, showShareAndQrActions = true)

    // Organizer-defined Action Buttons for this dataset type (`form.targetDatasetId ==
    // entity.datasetId`)
    val entityForms = state.formsForEntity(entity)
    if (entityForms.isNotEmpty()) {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = "DATA COLLECTION FOR THIS ${entity.singularTypeLabel.uppercase()}",
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.primary,
        )
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          entityForms.forEach { form ->
            val isEnabled = state.isFormButtonEnabled(entity, form)
            Button(
              onClick = { state.launchFormForEntity(entity.id, form.id) },
              enabled = isEnabled,
              shape = MaterialTheme.shapes.small,
            ) {
              Icon(
                imageVector =
                  if (isEnabled) Icons.Default.Description else Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
              )
              Spacer(modifier = Modifier.width(5.dp))
              Text(
                text =
                  if (isEnabled) {
                    form.ctaLabel
                  } else {
                    "${form.ctaLabel} (Completed)"
                  },
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              )
            }
          }
        }
        val triggeredBanner = state.activeSurveyNotice
        if (triggeredBanner != null) {
          Text(
            text = triggeredBanner,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    // Scrollable Body inside Bottom Sheet: Baseline Properties + Submission Data / History
    Column(
      modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // Baseline Attributes (`EntityRecord.properties`)
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        entity.properties.forEach { (key, value) ->
          Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainer,
          ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
              Text(
                text = key,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = value,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }
      }

      // --- UNIFIED 1:N SUBMISSION DISPLAY LOGIC ---
      if (selectedSubmission != null && selectedSubmission.entityId == entity.id) {
        SubmissionFullDetailsCard(
          submission = selectedSubmission,
          state = state,
          isDark = isDark,
          textColor = textColor,
          backLabel = "Back to all ${entity.submissions.size} submissions",
          onBack = { state.selectSubmissionDetail(null) },
          onSharePdf = { state.shareSubmissionPdf(selectedSubmission.id) },
        )
      } else if (entity.submissions.isNotEmpty()) {
        FormGroupedSubmissionsSection(
          groups = state.groupedSubmissionsForEntity(entity),
          state = state,
          showTargetEntityLabel = false,
          showFormActionSubtitle = false,
          onSelectSubmission = { state.selectSubmissionDetail(it.id) },
        )
      } else {
        Text(
          text =
            "No submissions recorded yet for this ${entity.singularTypeLabel.lowercase()} (Marker: ${entity.markerSymbol} ${entity.workflowStatus}). Launch a form above to advance its marker via save_to (○ → ◐ → ✓).",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

/**
 * Shared component that renders chronological submissions grouped by form (`FormSubmissionGroup`),
 * used in both [EntityBottomSheetCard] (for a single entity's `1:N` submissions) and
 * [BottomSheetSearchableListContent] (for survey-wide submissions).
 */
@Composable
private fun FormGroupedSubmissionsSection(
  groups: List<FormSubmissionsGroup>,
  state: PrototypeAppState,
  showTargetEntityLabel: Boolean,
  showFormActionSubtitle: Boolean,
  onSelectSubmission: (SubmissionPreviewItem) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    groups.forEach { group ->
      val form = group.form
      OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors =
          CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
          ),
      ) {
        Column(
          modifier = Modifier.padding(10.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          // Form Title Group Header
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.Description,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(13.dp),
                )
                Text(
                  text = form.title,
                  style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onSurface,
                )
              }
              if (showFormActionSubtitle) {
                Text(
                  text = "Action: \"${form.ctaLabel}\" • ${form.version}",
                  style =
                    MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }
            GroundTonalBadge(
              text = "${group.submissions.size} submitted",
              tone = GroundBadgeTone.PRIMARY,
            )
          }

          group.submissions.forEachIndexed { index, sub ->
            OutlinedCard(
              onClick = { onSelectSubmission(sub) },
              modifier = Modifier.fillMaxWidth(),
              shape = MaterialTheme.shapes.small,
              colors =
                CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(
                  modifier = Modifier.weight(1f),
                  verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                  if (showTargetEntityLabel) {
                    val targetDisplayLabel =
                      if (sub.hasAttachedEntity) {
                        "${sub.targetTypeLabel}: ${sub.entityLabel}"
                      } else if (sub.coordinatesLabel.isNotBlank()) {
                        "${sub.targetTypeLabel} • No attached map feature (${sub.coordinatesLabel.substringBefore(" (")})"
                      } else {
                        "${sub.targetTypeLabel} • No attached map feature"
                      }
                    Text(
                      text = targetDisplayLabel,
                      style =
                        MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                      color = MaterialTheme.colorScheme.onSurface,
                    )
                  }
                  Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Icon(
                      imageVector = Icons.Default.Person,
                      contentDescription = null,
                      tint =
                        if (showTargetEntityLabel) {
                          MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                          MaterialTheme.colorScheme.onSurface
                        },
                      modifier = Modifier.size(13.dp),
                    )
                    Text(
                      text = sub.collectorName,
                      style =
                        if (showTargetEntityLabel) {
                          MaterialTheme.typography.labelSmall
                        } else {
                          MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        },
                      color =
                        if (showTargetEntityLabel) {
                          MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                          MaterialTheme.colorScheme.onSurface
                        },
                    )
                    if (index == 0 && !showTargetEntityLabel) {
                      GroundTonalBadge(text = "LATEST", tone = GroundBadgeTone.SECONDARY)
                    }
                    SyncStatusIndicatorBadge(
                      syncStatus = sub.syncStatus,
                      onClick = { state.cycleSubmissionSyncStatus(sub.id) },
                    )
                  }
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Default.Schedule,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.size(11.dp),
                    )
                    Text(
                      text = "${sub.timestamp} • ${sub.formVersion}",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }

                IconButton(onClick = { onSelectSubmission(sub) }, modifier = Modifier.size(32.dp)) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Submission details",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
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

/**
 * Searchable List content embedded inside the unified persistent bottom sheet (
 * [SurveyPersistentBottomSheetContent]) when no specific entity or submission is selected:
 * - Peeks at the bottom of the map with the Search bar (`"Search..."`)
 *   and an Expand/Collapse sheet button.
 * - Expands to display **Map layers** (grouped by spatial layer with un-nested entity records,
 *   reusing [EntitySummaryHeader] and [EntityMetadataAndActionsRow]), **Data tables** (tabular datasets),
 *   and **Places** (geographic places, landmarks, and coordinates in the survey region).
 */
@Composable
private fun BottomSheetSearchableListContent(
  state: PrototypeAppState,
  modifier: Modifier = Modifier,
) {
  val allPlaces = state.places
  val apiPlaces = state.mapboxPlacesApiResults
  val isAirplaneMode = state.isAirplaneMode
  val allEntities = state.entities
  val listTab = state.listFilterTab
  val listQuery = state.listSearchQuery
  val mapLayers = state.mapLayers

  val matchedPlaces =
    remember(allPlaces, apiPlaces, listTab, listQuery, isAirplaneMode) { state.filteredListPlaces }
  val matchedEntities = remember(allEntities, listTab, listQuery) { state.filteredListEntities }
  val groupedEntities = remember(matchedEntities, mapLayers) { state.groupedFilteredListEntities }

  Column(modifier = modifier) {
    // Sticky Peek Header: Search Bar + Expand/Collapse Toggle
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = state.listSearchQuery,
          onValueChange = {
            state.updateListSearchQuery(it)
            if (it.isNotEmpty() && !state.isEntityBottomSheetExpanded) {
              state.updateEntityBottomSheetExpanded(true)
            }
          },
          modifier = Modifier.weight(1f),
          singleLine = true,
          placeholder = {
            Text(
              text = "Search...",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          },
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.Search,
              contentDescription = "Search",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp),
            )
          },
          trailingIcon = {
            if (state.listSearchQuery.isNotEmpty()) {
              IconButton(
                onClick = { state.clearListSearchQuery() },
                modifier = Modifier.size(32.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = "Clear Search",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(16.dp),
                )
              }
            }
          },
          shape = MaterialTheme.shapes.extraLarge,
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
              unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
              focusedBorderColor = MaterialTheme.colorScheme.primary,
              unfocusedBorderColor = Color.Transparent,
            ),
        )

        IconButton(
          onClick = {
            if (state.isEntityBottomSheetExpanded) {
              state.setMainSurveyViewMode(MainSurveyViewMode.MAP)
            } else {
              state.setMainSurveyViewMode(MainSurveyViewMode.LIST)
            }
          },
          modifier = Modifier.size(40.dp),
        ) {
          Icon(
            imageVector =
              if (state.isEntityBottomSheetExpanded) {
                Icons.Default.KeyboardArrowDown
              } else {
                Icons.Default.KeyboardArrowUp
              },
            contentDescription =
              if (state.isEntityBottomSheetExpanded) {
                "Collapse list sheet to map"
              } else {
                "Expand searchable list sheet"
              },
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
          )
        }
      }



      // Offline / Airplane mode banner in the bottom sheet explaining that search is only in local
      // map features and that Places search is not available offline.
      if (isAirplaneMode) {
        Surface(
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              modifier = Modifier.weight(1f),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = "Airplane mode active",
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(16.dp),
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Offline (Airplane mode) • Searching local ${state.activeEntitiesCountNoun} only",
                  style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                  text = "Search is only in local ${state.activeEntitiesCountNoun}. Places search is not available offline.",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f),
                )
              }
            }

            Spacer(modifier = Modifier.width(6.dp))

            AssistChip(
              onClick = { state.updateAirplaneMode(false) },
              label = {
                Text(
                  text = "Turn Off",
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                )
              },
            )
          }
        }
      }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    // Scrollable Searchable List of Map features (grouped by dataset) and Places
    Column(
      modifier =
        Modifier.weight(1f)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 14.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // 1. MAP LAYERS SECTION
      if (groupedEntities.isNotEmpty()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Layers,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(15.dp),
          )
          Text(
            text = "MAP LAYERS",
            style =
              MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.6.sp,
              ),
          )
        }

        groupedEntities.forEach { group ->
          val layer = group.layer
          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            // Layer Group Header Banner
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  if (layer != null) {
                    Box(
                      modifier =
                        Modifier.size(14.dp)
                          .clip(MaterialTheme.shapes.extraSmall)
                          .background(Color(layer.colorHex).copy(alpha = 0.25f))
                          .border(2.dp, Color(layer.colorHex), MaterialTheme.shapes.extraSmall)
                    )
                  } else {
                    Icon(
                      imageVector = Icons.Default.LocationOn,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(14.dp),
                    )
                  }
                  Text(
                    text = layer?.label ?: group.datasetName,
                    style =
                      MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                }
                Text(
                  text =
                    if (layer != null) {
                      "${layer.geometryTypeLabel} • ${group.datasetName}"
                    } else {
                      group.datasetName
                    },
                  style =
                    MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                  color = MaterialTheme.colorScheme.primary,
                )
              }

              GroundTonalBadge(
                text = "${group.entities.size}",
                tone = GroundBadgeTone.PRIMARY,
              )
            }

            // Map features belonging to this dataset group elevated to direct list items (no outer card nesting)
            val maxRenderedFeatures = 40
            val displayedFeatures =
              if (group.entities.size > maxRenderedFeatures) {
                group.entities.take(maxRenderedFeatures)
              } else {
                group.entities
              }
            displayedFeatures.forEach { entity ->
              OutlinedCard(
                onClick = { state.selectEntityFromList(entity.id) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                colors =
                  CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                  ),
              ) {
                Column(
                  modifier = Modifier.fillMaxWidth().padding(10.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  EntitySummaryHeader(
                    entity = entity,
                    state = state,
                    compact = true,
                    trailingContent = {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                      ) {
                        Text(
                          text = "Inspect",
                          style =
                            MaterialTheme.typography.labelSmall.copy(
                              fontWeight = FontWeight.Bold
                            ),
                          color = MaterialTheme.colorScheme.primary,
                        )
                        Icon(
                          imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                          contentDescription = null,
                          tint = MaterialTheme.colorScheme.primary,
                          modifier = Modifier.size(12.dp),
                        )
                      }
                    },
                  )

                  EntityMetadataAndActionsRow(
                    entity = entity,
                    state = state,
                    showShareAndQrActions = false,
                  )
                }
              }
            }
            if (group.entities.size > maxRenderedFeatures) {
              Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text =
                    "Showing first $maxRenderedFeatures of ${group.entities.size} ${layer?.pluralNoun ?: "features"} in ${group.datasetName}. Use the search bar above to filter all ${group.entities.size} ${layer?.pluralNoun ?: "features"}.",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                  fontWeight = FontWeight.Medium,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                )
              }
            }
          }
        }
      }

      // 2. DATA TABLES SECTION — only displayed when tabular datasets exist and have matching entries
      // (Currently no non-spatial tabular datasets configured in the active survey)

      // 3. PLACES SECTION — only available when online (!isAirplaneMode) and entries match
      if (!isAirplaneMode && matchedPlaces.isNotEmpty()) {
        if (groupedEntities.isNotEmpty()) {
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Explore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(15.dp),
          )
          Text(
            text = "PLACES",
            style =
              MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.6.sp,
              ),
          )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          matchedPlaces.forEach { place ->
            val isSelectedPlace = state.selectedPlaceId == place.id
            val isNavigatingPlace = state.isNavigatingToPlace(place.id)
            val placeWayfindingBadge = state.formattedWayfindingBadgeForPlace(place.id)

            OutlinedCard(
              onClick = { state.selectPlace(place.id) },
              modifier = Modifier.fillMaxWidth(),
              shape = MaterialTheme.shapes.small,
              border =
                if (isSelectedPlace) {
                  BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                } else {
                  CardDefaults.outlinedCardBorder()
                },
              colors =
                CardDefaults.outlinedCardColors(
                  containerColor =
                    if (isSelectedPlace) {
                      MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    } else {
                      MaterialTheme.colorScheme.surface
                    }
                ),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Surface(
                      modifier = Modifier.size(24.dp),
                      shape = CircleShape,
                      color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Icon(
                          imageVector = Icons.Default.Explore,
                          contentDescription = null,
                          tint = MaterialTheme.colorScheme.onTertiaryContainer,
                          modifier = Modifier.size(14.dp),
                        )
                      }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = place.name,
                        style =
                          MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                      Text(
                        text = "${place.categoryLabel} • ${place.regionSubtitle}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                  }

                  FilledTonalIconButton(
                    onClick = { state.selectPlace(place.id) },
                    modifier = Modifier.size(30.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Default.Explore,
                      contentDescription = "Fly to place on map",
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(16.dp),
                    )
                  }
                }

                Row(
                  modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  GroundTonalBadge(text = place.categoryLabel, tone = GroundBadgeTone.SECONDARY)
                  GroundTonalBadge(
                    text = place.coordinatesLabel,
                    tone = GroundBadgeTone.SECONDARY,
                    monospace = true,
                  )
                  if (placeWayfindingBadge.isNotEmpty()) {
                    GroundTonalBadge(
                      text = "➤ $placeWayfindingBadge",
                      tone = GroundBadgeTone.TERTIARY,
                      monospace = true,
                    )
                  }
                  FilterChip(
                    selected = isNavigatingPlace,
                    onClick = { state.toggleNavigationToPlace(place.id) },
                    label = {
                      Text(
                        text = if (isNavigatingPlace) "Stop Nav" else "Navigate",
                        style =
                          MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                      )
                    },
                    leadingIcon = {
                      Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = "Straight-line navigate to place",
                        modifier = Modifier.size(13.dp),
                      )
                    },
                  )
                }
              }
            }
          }
        }
      }

      if (matchedPlaces.isEmpty() && matchedEntities.isEmpty()) {
        Box(
          modifier = Modifier.fillMaxWidth().padding(32.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text =
              when {
                isAirplaneMode ->
                  "No local map layers match \"${state.listSearchQuery}\". Places search is not available offline."
                else ->
                  "No map layers or places match \"${state.listSearchQuery}\"."
              },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    // Bottom-centered Floating Action Button inside the expanded sheet as well
    if (state.isEntityBottomSheetExpanded) {
      Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
      ) { DataCollectionFormsFab(state = state) }
    }
  }
}

/** Full Submission Details Card shown when a submission in a 1:N list or List view is clicked. */
@Composable
private fun SubmissionFullDetailsCard(
  submission: SubmissionPreviewItem,
  state: PrototypeAppState,
  isDark: Boolean,
  textColor: Color,
  backLabel: String,
  onBack: () -> Unit,
  onSharePdf: () -> Unit,
) {
  OutlinedCard(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
    colors =
      CardDefaults.outlinedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
      ),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        FilledTonalButton(
          onClick = { onBack() },
          shape = MaterialTheme.shapes.small,
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = backLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          SyncStatusIndicatorBadge(
            syncStatus = submission.syncStatus,
            onClick = { state.cycleSubmissionSyncStatus(submission.id) },
          )

          AssistChip(
            onClick = { onSharePdf() },
            label = {
              Text(
                text = "Share PDF",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              )
            },
            leadingIcon = {
              Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "Share Submission PDF",
                modifier = Modifier.size(12.dp),
              )
            },
          )

          Text(
            text = "Schema: ${submission.formVersion}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = submission.formTitle,
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          val detailTargetLabel =
            if (submission.hasAttachedEntity) {
              "${submission.targetTypeLabel}: ${submission.entityLabel}"
            } else if (submission.coordinatesLabel.isNotBlank()) {
              "${submission.targetTypeLabel} • No attached map feature (${submission.coordinatesLabel})"
            } else {
              "${submission.targetTypeLabel} • No attached map feature"
            }
          Text(
            text = detailTargetLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
          )
        }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(12.dp),
          )
          Text(
            text = "Data Collector: ${submission.collectorName} (${submission.collectorEmail})",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(12.dp),
          )
          Text(
            text = "Collected: ${submission.timestamp}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      submission.fields.forEach { field ->
        val fieldWayfindingBadge =
          state.formattedWayfindingBadgeForSubmissionField(submission.id, field)
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.surface,
        ) {
          Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = field.questionLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
              )
              if (fieldWayfindingBadge.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                GroundTonalBadge(
                  text = "➤ $fieldWayfindingBadge",
                  tone = GroundBadgeTone.WARNING,
                  monospace = true,
                )
              }
            }
            Text(
              text = field.answerValue,
              style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }
    }
  }
}

/**
 * Hamburger Navigation Drawer overlay providing options to:
 * 1. Surveys (downloaded surveys screen with button to browse & download more surveys)
 * 2. Offline maps
 * 3. Change settings
 * 4. View Terms of Service
 * 5. Sign out
 */
@Composable
private fun MainSurveyNavigationDrawerOverlay(state: PrototypeAppState) {
  val brandFont = LocalGroundBrandFontFamily.current
  Box(modifier = Modifier.fillMaxSize()) {
    // Scrim backdrop
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
          .clickable { state.updateDrawerOpen(false) }
    )

    // M3 ModalDrawerSheet Panel
    ModalDrawerSheet(
      modifier = Modifier.fillMaxHeight().width(308.dp),
      drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
      Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.fillMaxWidth()) {
          // User Profile & Organization Header
          Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1D5128),
            contentColor = Color.White,
          ) {
            Column(
              modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  GroundCloudAcaciaLogo(modifier = Modifier.size(30.dp))
                  Text(
                    text = "Ground",
                    style =
                      MaterialTheme.typography.titleLarge.copy(
                        fontFamily = brandFont,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp,
                      ),
                    color = Color.White,
                  )
                }
                IconButton(
                  onClick = { state.updateDrawerOpen(false) },
                  modifier = Modifier.size(32.dp),
                ) {
                  Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Drawer",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                  )
                }
              }

              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Box(
                  modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF9CD49F)),
                  contentAlignment = Alignment.Center,
                ) {
                  Text(
                    text = "ML",
                    style =
                      MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color(0xFF003914),
                  )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                  Text(
                    text = state.signedInUserName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                  )
                  Text(
                    text = state.signedInUserEmail,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFB7F1B9),
                  )
                  Text(
                    text = state.signedInOrganization,
                    style =
                      MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFF9CD49F),
                  )
                }
              }
            }
          }

          // Active Survey Summary Banner
          Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
          ) {
            Column(
              modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
              verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
              Text(
                text = "ACTIVE SURVEY",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = state.activeSurvey.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }

          Spacer(modifier = Modifier.height(8.dp))

          // Navigation Drawer Options using M3 NavigationDrawerItem
          Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            DrawerMenuItem(
              icon = Icons.Default.SwapHoriz,
              title = "Surveys",
              subtitle = "${state.downloadedSurveyCount} downloaded on device",
              selected = state.activeDrawerSubView == MainDrawerSubView.SWITCH_SURVEYS,
              onClick = { state.drawerSwitchSurveys() },
            )
            DrawerMenuItem(
              icon = Icons.Default.CloudUpload,
              title = "Uploads",
              subtitle = null,
              badgeText =
                if (state.outboxMutationCount > 0) "${state.outboxMutationCount}" else null,
              badgeTone = GroundBadgeTone.WARNING,
              selected =
                state.activeDrawerSubView == MainDrawerSubView.UPLOADS ||
                  state.activeDrawerSubView == MainDrawerSubView.OUTBOX ||
                  state.activeDrawerSubView == MainDrawerSubView.UPLOADED,
              onClick = { state.drawerOpenUploads() },
            )
            DrawerMenuItem(
              icon = Icons.Default.Map,
              title = "Offline maps",
              subtitle = "Vector & satellite raster tile cache",
              selected = state.activeDrawerSubView == MainDrawerSubView.MANAGE_OFFLINE_MAPS,
              onClick = { state.drawerManageOfflineMaps() },
            )
            DrawerMenuItem(
              icon = Icons.Default.Settings,
              title = "Settings",
              subtitle = "Units (${state.unitSystem.areaUnit}), language & media cache",
              selected = state.activeDrawerSubView == MainDrawerSubView.SETTINGS,
              onClick = { state.drawerOpenSettings() },
            )
            DrawerMenuItem(
              icon = Icons.Default.Description,
              title = "Terms of Service",
              subtitle = "Platform data governance & privacy terms",
              selected = false,
              onClick = { state.drawerViewTermsOfService() },
            )

            HorizontalDivider(
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
              color = MaterialTheme.colorScheme.outlineVariant,
            )

            DrawerMenuItem(
              icon = Icons.AutoMirrored.Filled.Logout,
              title = "Sign out",
              subtitle = "Disconnect ${state.signedInUserEmail}",
              selected = false,
              isDestructive = true,
              onClick = { state.drawerSignOut() },
            )
          }

          // Footer version note
          Text(
            text =
              buildAnnotatedString {
                append("Open Foris ")
                withStyle(SpanStyle(fontFamily = brandFont, fontWeight = FontWeight.Bold)) {
                  append("Ground")
                }
                append(" 2.0 • Offline-First Core")
              },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(18.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun DrawerMenuItem(
  icon: ImageVector,
  title: String,
  subtitle: String? = null,
  selected: Boolean,
  badgeText: String? = null,
  badgeTone: GroundBadgeTone = GroundBadgeTone.PRIMARY,
  isDestructive: Boolean = false,
  onClick: () -> Unit,
) {
  val itemColor =
    if (isDestructive) {
      MaterialTheme.colorScheme.error
    } else {
      MaterialTheme.colorScheme.onSurface
    }
  NavigationDrawerItem(
    label = {
      Column {
        Text(
          text = title,
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
          color = itemColor,
        )
        if (!subtitle.isNullOrBlank()) {
          Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color =
              if (isDestructive) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant
              },
          )
        }
      }
    },
    badge =
      badgeText?.let { countText -> { GroundTonalBadge(text = countText, tone = badgeTone) } },
    selected = selected,
    onClick = onClick,
    icon = {
      Icon(
        imageVector = icon,
        contentDescription = title,
        tint = itemColor,
        modifier = Modifier.size(20.dp),
      )
    },
    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
  )
}

/**
 * Unified `Uploads` screen accessible from the Hamburger Navigation Drawer, merging pending,
 * in-progress, uploaded, and failed mutations into a single compact, user-friendly list with status
 * filter chips (`Pending`, `In progress`, `Uploaded`, `Failed`).
 */
@Composable
private fun UploadsMutationsSubScreen(state: PrototypeAppState) {
  val filteredMutations = state.filteredUploadMutations
  val activeFilter = state.selectedUploadStatusFilter

  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    // Compact Top Header: Title + Sync All (if pending/failed) + Back to Map
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Icons.Default.CloudUpload,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp),
        )
        Text(
          text = "Uploads",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface,
        )
      }

      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (state.outboxMutationCount > 0) {
          FilledTonalButton(
            onClick = { state.syncAllOutboxMutations() },
            shape = MaterialTheme.shapes.small,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          ) {
            Icon(
              imageVector = Icons.Default.CloudUpload,
              contentDescription = null,
              modifier = Modifier.size(13.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "Sync all (${state.outboxMutationCount})",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            )
          }
        }

        OutlinedButton(
          onClick = { state.closeDrawerSubView() },
          shape = MaterialTheme.shapes.small,
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text("Back", style = MaterialTheme.typography.labelSmall)
        }
      }
    }

    // Status Filter Chips Row: Pending | In progress | Uploaded | Failed
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      UploadStatusFilter.entries.forEach { filter ->
        val isSelected = activeFilter == filter
        val count = state.uploadCountForFilter(filter)
        FilterChip(
          selected = isSelected,
          onClick = { state.toggleUploadStatusFilter(filter) },
          label = {
            Text(
              text = "${filter.label} ($count)",
              style =
                MaterialTheme.typography.labelSmall.copy(
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
            )
          },
          leadingIcon =
            if (isSelected) {
              {
                Icon(
                  imageVector = Icons.Default.Check,
                  contentDescription = null,
                  modifier = Modifier.size(14.dp),
                )
              }
            } else {
              null
            },
        )
      }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    // Compact List of User-Friendly Upload Items
    if (filteredMutations.isEmpty()) {
      Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text =
            if (activeFilter != null) {
              "No ${activeFilter.label.lowercase()} uploads."
            } else {
              "No uploads yet."
            },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
      }
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        filteredMutations.forEach { mutation ->
          UploadMutationRowCard(mutation = mutation, state = state)
        }
      }
    }
  }
}

/**
 * Compact, user-friendly row card for a [MutationLogItem] in `Uploads`, showing:
 * - Action label (`Form submitted`, `Form modified`, `Form deleted`, `Map feature modified`, etc.) and
 * form/feature title
 * - Target entity label and concise timestamp
 * - Status badge (`Pending`, `In progress`, `Uploaded`, `Failed`) and inline retry/upload action
 */
@Composable
private fun UploadMutationRowCard(mutation: MutationLogItem, state: PrototypeAppState) {
  val statusFilter = mutation.uploadStatusFilter
  val badgeTone =
    when (statusFilter) {
      UploadStatusFilter.UPLOADED -> GroundBadgeTone.PRIMARY
      UploadStatusFilter.IN_PROGRESS -> GroundBadgeTone.TERTIARY
      UploadStatusFilter.PENDING -> GroundBadgeTone.NEUTRAL
      UploadStatusFilter.FAILED -> GroundBadgeTone.WARNING
    }

  OutlinedCard(
    onClick = {
      if (mutation.submissionId != null) {
        state.selectSubmissionDetail(mutation.submissionId)
      } else if (mutation.entityId.isNotBlank()) {
        state.selectEntity(mutation.entityId)
      }
      state.closeDrawerSubView()
    },
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.small,
    border =
      BorderStroke(
        width = 1.dp,
        color =
          when (statusFilter) {
            UploadStatusFilter.FAILED -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
            UploadStatusFilter.IN_PROGRESS -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.outlineVariant
          },
      ),
    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
      verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      // Line 1: User-friendly action + title on left, compact status pill on right
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "${mutation.operationKind.label} • ${mutation.title}",
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f).padding(end = 6.dp),
        )

        if (statusFilter == UploadStatusFilter.FAILED) {
          Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
          ) {
            Text(
              text = statusFilter.label,
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            )
          }
        } else {
          GroundTonalBadge(text = statusFilter.label, tone = badgeTone)
        }
      }

      // Line 2: Target entity label + timestamp on left, compact Retry/Upload action on right
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "${mutation.targetLabel} • ${mutation.compactTimestamp}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f).padding(end = 6.dp),
        )

        if (mutation.isOutbox) {
          Surface(
            onClick = { state.syncMutationNow(mutation.id) },
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
              Icon(
                imageVector =
                  if (statusFilter == UploadStatusFilter.FAILED) {
                    Icons.Default.Refresh
                  } else {
                    Icons.Default.CloudUpload
                  },
                contentDescription = null,
                modifier = Modifier.size(11.dp),
              )
              Text(
                text = if (statusFilter == UploadStatusFilter.FAILED) "Retry" else "Upload",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              )
            }
          }
        }
      }

      if (mutation.state == MutationSyncState.UPLOADING) {
        LinearProgressIndicator(
          progress = { 0.74f },
          modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
          color = MaterialTheme.colorScheme.primary,
          trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
      } else if (statusFilter == UploadStatusFilter.FAILED && mutation.stateDetail.isNotBlank()) {
        Text(
          text = mutation.stateDetail,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.error,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

/**
 * Separate screen accessible from the `"Surveys"` navigation drawer option showing ONLY the surveys
 * which have already been downloaded to the device, plus a primary action button (`"Browse &
 * download more surveys"`) which navigates to the full `Download surveys` screen.
 */
@Composable
private fun SwitchDownloadedSurveysSubScreen(state: PrototypeAppState) {
  val downloadedList = state.downloadedSurveys

  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          imageVector = Icons.Default.SwapHoriz,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(22.dp),
        )
        Column {
          Text(
            text = "Downloaded Surveys",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = "${downloadedList.size} offline-ready survey(s) on this device",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      OutlinedButton(onClick = { state.closeDrawerSubView() }, shape = MaterialTheme.shapes.small) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = null,
          modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text("Back")
      }
    }

    // List of Downloaded Surveys only
    downloadedList.forEach { survey ->
      val isActive = survey.id == state.activeSurveyId
      OutlinedCard(
        onClick = { state.openSurvey(survey.id) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border =
          BorderStroke(
            width = if (isActive) 1.5.dp else 1.dp,
            color =
              if (isActive) {
                MaterialTheme.colorScheme.primary
              } else {
                MaterialTheme.colorScheme.outlineVariant
              },
          ),
        colors =
          CardDefaults.outlinedCardColors(
            containerColor =
              if (isActive) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
              } else {
                MaterialTheme.colorScheme.surface
              }
          ),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(14.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text = survey.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
              )
              if (isActive) {
                GroundTonalBadge(text = "ACTIVE", tone = GroundBadgeTone.PRIMARY)
              }
            }
            Text(
              text =
                "${survey.location} • ${survey.entityCount} locations • ${survey.offlineSizeLabel}",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = survey.description,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Spacer(modifier = Modifier.width(8.dp))
          if (isActive) {
            FilledTonalButton(
              onClick = { state.openSurvey(survey.id) },
              shape = MaterialTheme.shapes.small,
            ) {
              Text(
                text = "Open",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              )
            }
          } else {
            Button(onClick = { state.openSurvey(survey.id) }, shape = MaterialTheme.shapes.small) {
              Text(
                text = "Switch",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              )
            }
          }
        }
      }
    }

    // Primary Action Button to navigate to the full Download Surveys screen
    Button(
      onClick = { state.openDownloadMoreSurveysScreen() },
      modifier = Modifier.fillMaxWidth().height(48.dp),
      shape = MaterialTheme.shapes.medium,
    ) {
      Icon(
        imageVector = Icons.Default.Download,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = "Browse & download more surveys",
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
      )
    }
  }
}

/**
 * Modal dialog displaying a scannable QR Code for a survey location (`Icons.Default.QrCode`),
 * allowing offline field verification and rapid lookup of the location's `GeoID`.
 */
@Composable
private fun EntityQrCodeModalDialog(state: PrototypeAppState, entity: GeospatialEntityItem) {
  GroundAlertDialogOverlay(
    onDismissRequest = { state.closeEntityQrCode() },
    icon = {
      Icon(
        imageVector = Icons.Default.QrCode,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
    },
    title = {
      Text(
        text = "${entity.singularTypeLabel} QR Code",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
      )
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        // High-contrast QR matrix surface for scanning
        Surface(
          modifier = Modifier.size(148.dp),
          shape = MaterialTheme.shapes.medium,
          color = Color.White,
          border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        ) {
          Box(modifier = Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Default.QrCode,
              contentDescription = "${entity.label} QR Matrix",
              tint = Color(0xFF111827),
              modifier = Modifier.size(116.dp),
            )
          }
        }

        Text(
          text = entity.label,
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface,
        )
        GroundTonalBadge(
          text = "GeoID: ${entity.geoId}",
          tone = GroundBadgeTone.PRIMARY,
          monospace = true,
        )
        Text(
          text =
            "Scan with Ground or any EUDR compliance reader to verify ${entity.singularTypeLabel.lowercase()} geometry & GeoID.",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          state.closeEntityQrCode()
          state.shareEntityPdf(entity.id)
        }
      ) {
        Icon(
          imageVector = Icons.Default.Share,
          contentDescription = null,
          modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text("Share PDF")
      }
    },
    dismissButton = { TextButton(onClick = { state.closeEntityQrCode() }) { Text("Close") } },
  )
}

/**
 * Modal bottom sheet allowing the user to share a generated offline PDF receipt for either a
 * Geospatial Entity or a Submission (`state.activeSharedPdfSheet`) to their preferred app.
 */
@Composable
private fun SharePdfToAppModalDialog(state: PrototypeAppState, sheet: SharedPdfSheetState) {
  GroundModalBottomSheetOverlay(onDismissRequest = { state.closeSharePdfSheet() }) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Share,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
          )
          Text(
            text = "Share PDF to Preferred App",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
        IconButton(onClick = { state.closeSharePdfSheet() }) {
          Icon(imageVector = Icons.Default.Close, contentDescription = "Close Share PDF Sheet")
        }
      }

      // PDF attachment preview card
      OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        colors =
          CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
          ),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            imageVector = Icons.Default.PictureAsPdf,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(26.dp),
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = sheet.pdfFileName,
              style =
                MaterialTheme.typography.labelMedium.copy(
                  fontWeight = FontWeight.Bold,
                ),
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = "${sheet.title} • ${sheet.subtitle}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      Text(
        text = "Select preferred application to send offline PDF receipt:",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        listOf("WhatsApp", "Gmail", "Google Drive", "Bluetooth").forEachIndexed { index, appName ->
          val isPreferred = index == 0
          FilterChip(
            selected = isPreferred,
            onClick = { state.closeSharePdfSheet() },
            label = {
              Text(
                text = appName,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              )
            },
          )
        }
      }

      Button(
        onClick = { state.closeSharePdfSheet() },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
      ) { Text("Done") }
    }
  }
}

/**
 * Drawer Sub-Screen: `Offline maps` (consistent with `docs/design/00-index.md` "Offline Storage
 * Safeguards & Media Purging" — Mapbox vector & raster tiles and 500 MB storage guardrail).
 */
@Composable
private fun ManageOfflineMapsSubScreen(state: PrototypeAppState) {
  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          imageVector = Icons.Default.Map,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp),
        )
        Text(
          text = "Manage Offline Maps",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
      OutlinedButton(onClick = { state.closeDrawerSubView() }, shape = MaterialTheme.shapes.small) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = null,
          modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text("Back to Map")
      }
    }

    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = MaterialTheme.shapes.medium,
      colors =
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
      Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(15.dp),
          )
          Text(
            text = "Device Storage Safeguard Active (4.2 GB Available)",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
          )
        }
        Text(
          text =
            "Pre-cached vector & satellite raster tiles across zoom levels 10–19. Downloads automatically pause if device storage drops below 500 MB.",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
        )
      }
    }

    state.offlineTilePackages.forEach { pkg ->
      OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors =
          CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
              text = pkg.regionName,
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = "${pkg.tileTypeLabel} • ${pkg.zoomRangeLabel} • ${pkg.sizeLabel}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (pkg.isDownloaded) {
            FilledTonalButton(
              onClick = { state.toggleOfflineTilePackage(pkg.id) },
              shape = MaterialTheme.shapes.large,
            ) {
              Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "Cached",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              )
            }
          } else {
            Button(
              onClick = { state.toggleOfflineTilePackage(pkg.id) },
              shape = MaterialTheme.shapes.large,
            ) {
              Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "Download",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              )
            }
          }
        }
      }
    }
  }
}

/**
 * Drawer Sub-Screen: `Settings` — delegates to [GroundSettingsScreen], ported from
 * `org.groundplatform.android.ui.settings.SettingsScreen` in `github.com/google/ground-android`.
 */
@Composable
private fun SurveySettingsSubScreen(state: PrototypeAppState) {
  GroundSettingsScreen(state = state)
}

/**
 * Material Design 3 status badge indicating whether a map feature (`GeospatialEntityItem`) or form
 * submission (`SubmissionPreviewItem`) is [SyncStatus.UPLOADING], [SyncStatus.SYNCED], or
 * [SyncStatus.FAILED].
 */
@Composable
internal fun SyncStatusIndicatorBadge(
  syncStatus: SyncStatus,
  modifier: Modifier = Modifier,
  countSuffix: String? = null,
  isHighlighted: Boolean = false,
  onClick: (() -> Unit)? = null,
) {
  val (icon, containerColor, contentColor, borderColor) =
    when (syncStatus) {
      SyncStatus.UPLOADING ->
        SyncStatusVisualSpec(
          icon = Icons.Default.CloudUpload,
          containerColor = MaterialTheme.colorScheme.tertiaryContainer,
          contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
          borderColor =
            MaterialTheme.colorScheme.tertiary.copy(alpha = if (isHighlighted) 0.9f else 0.35f),
        )
      SyncStatus.SYNCED ->
        SyncStatusVisualSpec(
          icon = Icons.Default.CloudDone,
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
          borderColor =
            MaterialTheme.colorScheme.primary.copy(alpha = if (isHighlighted) 0.9f else 0.35f),
        )
      SyncStatus.FAILED ->
        SyncStatusVisualSpec(
          icon = Icons.Default.CloudOff,
          containerColor = MaterialTheme.colorScheme.errorContainer,
          contentColor = MaterialTheme.colorScheme.onErrorContainer,
          borderColor =
            MaterialTheme.colorScheme.error.copy(alpha = if (isHighlighted) 0.95f else 0.50f),
        )
    }

  val clickModifier =
    if (onClick != null) {
      Modifier.clickable(onClick = onClick)
    } else {
      Modifier
    }

  Surface(
    modifier = modifier.then(clickModifier),
    shape = RoundedCornerShape(6.dp),
    color = containerColor,
    contentColor = contentColor,
    border = BorderStroke(if (isHighlighted) 1.5.dp else 1.dp, borderColor),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = syncStatus.description,
        tint = contentColor,
        modifier = Modifier.size(12.dp),
      )
      Text(
        text =
          if (countSuffix != null) {
            "${syncStatus.label} $countSuffix"
          } else {
            syncStatus.label
          },
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = contentColor,
      )
    }
  }
}

private data class SyncStatusVisualSpec(
  val icon: ImageVector,
  val containerColor: Color,
  val contentColor: Color,
  val borderColor: Color,
)
