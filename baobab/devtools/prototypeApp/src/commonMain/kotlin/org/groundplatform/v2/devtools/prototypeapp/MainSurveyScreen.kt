/*
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License'); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Polyline
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import org.groundplatform.v2.core.forms.ui.GroundAlertDialogOverlay
import org.groundplatform.v2.core.forms.ui.GroundBadgeTone
import org.groundplatform.v2.core.forms.ui.GroundModalBottomSheetOverlay
import org.groundplatform.v2.core.forms.ui.GroundTonalBadge

/**
 * 5. Main Survey UI screen (`PrototypeScreen.MAIN_SURVEY`) providing:
 * - Top App Bar with Hamburger Menu, active survey title, and `Map` / `List` toggle.
 * - **Map View**: Displays Ground geospatial entities on the map, a `"Layers"` button to toggle
 *   layer visibility, and an interactive **Entity Bottom Sheet** when an entity is clicked:
 *   - For `1:1` (`SubmissionModel.SINGLE_1_TO_1`) entities with data: displays the submission data
 *     inline on the entity card.
 *   - For `1:N` (`SubmissionModel.MULTIPLE_1_TO_N`) entities: displays a chronological list of
 *     submissions (data collector, timestamp) that can be clicked to inspect full submission
 *     details.
 * - **List View**: Searchable list of Geospatial Entities and Submissions grouped by Form.
 * - **Hamburger Navigation Drawer**: Options for Surveys, Offline maps,
 *   Change settings, View Terms of Service, and Sign out.
 */
@Composable
fun GroundMainSurveyScreen(state: PrototypeAppState) {
  val isMapShowing =
    state.activeDrawerSubView == MainDrawerSubView.NONE &&
      state.mainViewMode == MainSurveyViewMode.MAP
  val surfaceColor = if (isMapShowing) Color.Transparent else MaterialTheme.colorScheme.surface
  val activeQrEntity = state.activeQrCodeEntity
  val activePdfSheet = state.activeSharedPdfSheet

  Box(modifier = Modifier.fillMaxSize().background(surfaceColor)) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Top App Bar with Hamburger button, Survey Title, and Map/List View Switcher
      MainSurveyTopAppBar(state)

      // Main Body: either a Drawer Sub-View (Switch Surveys / Offline Maps / Settings) or Map / List View
      Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        when (state.activeDrawerSubView) {
          MainDrawerSubView.SWITCH_SURVEYS -> SwitchDownloadedSurveysSubScreen(state)
          MainDrawerSubView.MANAGE_OFFLINE_MAPS -> ManageOfflineMapsSubScreen(state)
          MainDrawerSubView.SETTINGS -> SurveySettingsSubScreen(state)
          MainDrawerSubView.NONE -> {
            when (state.mainViewMode) {
              MainSurveyViewMode.MAP -> SurveyMapView(state)
              MainSurveyViewMode.LIST -> SurveyListView(state)
            }
          }
        }
      }
    }

    // Map Layers Modal Bottom Sheet (confined inside the mobile device frame)
    if (state.isLayersSheetOpen) {
      MapLayersControlSheet(state = state)
    }

    // Slide-over Hamburger Navigation Drawer Overlay
    if (state.isDrawerOpen) {
      MainSurveyNavigationDrawerOverlay(state)
    }

    // Scannable Entity GeoID QR Code Modal Overlay
    if (activeQrEntity != null) {
      EntityQrCodeModalDialog(
        state = state,
        entity = activeQrEntity,
      )
    }

    // Share PDF to Preferred App Modal Overlay (for both Entities and Submissions)
    if (activePdfSheet != null) {
      SharePdfToAppModalDialog(
        state = state,
        sheet = activePdfSheet,
      )
    }
  }
}

/** Top App Bar for the Main Survey UI with Hamburger Menu button and `Map` | `List` switcher. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainSurveyTopAppBar(state: PrototypeAppState) {
  val topBarContainer =
    if (state.isDarkTheme) {
      MaterialTheme.colorScheme.primaryContainer
    } else {
      MaterialTheme.colorScheme.primary
    }
  val activePillContainer =
    if (state.isDarkTheme) {
      MaterialTheme.colorScheme.primary
    } else {
      MaterialTheme.colorScheme.inversePrimary
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
            tint = Color(0xFFC8E6C9),
            modifier = Modifier.size(12.dp),
          )
          Text(
            text =
              "${state.activeSurvey.location} • ${state.visibleMapEntities.size} ${state.activeEntitiesCountNoun} on map",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFC8E6C9),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    },
    actions = {
      SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(end = 8.dp)) {
        MainSurveyViewMode.entries.forEachIndexed { index, mode ->
          val isSelected =
            state.activeDrawerSubView == MainDrawerSubView.NONE && state.mainViewMode == mode
          SegmentedButton(
            selected = isSelected,
            onClick = { state.setMainSurveyViewMode(mode) },
            shape =
              SegmentedButtonDefaults.itemShape(
                index = index,
                count = MainSurveyViewMode.entries.size,
              ),
            colors =
              SegmentedButtonDefaults.colors(
                activeContainerColor = activePillContainer,
                activeContentColor = Color(0xFF003825),
                activeBorderColor = activePillContainer,
                inactiveContainerColor = Color(0xFF11422E),
                inactiveContentColor = Color.White,
                inactiveBorderColor = Color(0xFF386B52),
              ),
            icon = {
              Icon(
                imageVector =
                  if (mode == MainSurveyViewMode.MAP) {
                    Icons.Default.Map
                  } else {
                    Icons.AutoMirrored.Filled.List
                  },
                contentDescription = null,
                modifier = Modifier.size(14.dp),
              )
            },
            label = {
              Text(
                text = if (mode == MainSurveyViewMode.MAP) "Map" else "List",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
              )
            },
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
 * - **Submission Geometries** (`FormGeometrySource { form_id, field_path }`, rendered with dotted
 *   polygon outlines corresponding to geometry questions/fields in the survey forms)
 * - A `"Layers"` button (`MapLayersControlSheet`) to toggle the offline basemap, entity layers,
 *   and submission geometry layers
 * - A bottom sheet displaying entity & submission details (`1:1` vs `1:N`)
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
    if (state.selectedEntityId != null) {
      if (state.isEntityBottomSheetExpanded && sheetState.currentValue != SheetValue.Expanded) {
        sheetState.expand()
      } else if (
        !state.isEntityBottomSheetExpanded &&
          sheetState.currentValue != SheetValue.PartiallyExpanded
      ) {
        sheetState.partialExpand()
      }
    }
  }

  val peekHeight = if (selectedEntity != null) 146.dp else 0.dp

  BottomSheetScaffold(
    scaffoldState = scaffoldState,
    sheetPeekHeight = peekHeight,
    sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    sheetShadowElevation = 8.dp,
    sheetSwipeEnabled = selectedEntity != null,
    sheetDragHandle =
      if (selectedEntity != null) {
        { BottomSheetDefaults.DragHandle() }
      } else {
        null
      },
    containerColor = Color.Transparent,
    sheetContent = {
      if (selectedEntity != null) {
        EntityBottomSheetCard(
          entity = selectedEntity,
          state = state,
          modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        )
      } else {
        Spacer(modifier = Modifier.height(1.dp))
      }
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

      // 1. Real Mapbox GL JS Basemap (mapboxgl.Map via window.GroundMapboxBridge) + GeoJSON Layers & Mapbox Markers
      MapboxBasemapView(
        state = state,
        animatedShiftX = animatedShiftX,
        animatedShiftY = animatedShiftY,
        modifier = Modifier.fillMaxSize(),
      )

      // 3. Top Map Overlay Bar: GNSS (GPS) Satellites & Accuracy Chip + "Layers" Button
      Column(
        modifier =
          Modifier.align(Alignment.TopCenter)
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          // GNSS (GPS) Satellites & Current Horizontal Accuracy Chip over the map
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
                contentDescription = "GNSS Satellites & Accuracy",
                tint = Color(0xFF8BD6B1),
                modifier = Modifier.size(14.dp),
              )
              Text(
                text = "GNSS: ${state.gnssStatusChipLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                fontWeight = FontWeight.Bold,
              )
            }
          }

          // "Layers" Button to control basemap (Map vs Satellite), offline tiles, entities, and submission geometries
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
          Surface(
            onClick = { state.updateLayersSheetOpen(!state.isLayersSheetOpen) },
            shape = MaterialTheme.shapes.large,
            color = layersBg,
            contentColor = layersContent,
            border = BorderStroke(1.dp, Color(0xFF8BD6B1)),
            shadowElevation = 3.dp,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Layers,
                contentDescription = null,
                tint = layersContent,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = "Layers",
                style = MaterialTheme.typography.labelSmall,
                color = layersContent,
                fontWeight = FontWeight.Bold,
              )
            }
          }
        }

        // Compact GPS Follow State Chip
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

        // Straight-Line Navigation HUD Banner (when navigating to an Entity or Submission)
        val activeNav = state.activeNavigation
        if (activeNav != null) {
          StraightLineNavigationHudBanner(
            navState = activeNav,
            state = state,
          )
        }
      }

      // 4B. Floating Mapbox Zoom In (+) / Zoom Out (−) Control Pill on Right Edge
      Surface(
        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.93f),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        border = BorderStroke(1.dp, Color(0xFF8BD6B1)),
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
      ) {
        Column(
          modifier = Modifier.padding(vertical = 2.dp, horizontal = 2.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          IconButton(
            onClick = {
              state.zoomInMap()
              zoomPlatformMapboxBasemap(0.75f)
            },
            modifier = Modifier.size(36.dp),
          ) {
            Icon(
              imageVector = Icons.Default.Add,
              contentDescription = "Zoom In Map",
              tint = Color.White,
              modifier = Modifier.size(18.dp),
            )
          }

          HorizontalDivider(
            modifier = Modifier.width(24.dp),
            color = Color(0xFF2D5944),
          )

          IconButton(
            onClick = {
              state.zoomOutMap()
              zoomPlatformMapboxBasemap(-0.75f)
            },
            modifier = Modifier.size(36.dp),
          ) {
            Text(
              text = "−",
              color = Color.White,
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
          }
        }
      }

      // 5. Bottom Overlay Stack: Google Maps-style Horizontal Scale Widget in bottom-left
      //    (plus optional "Recenter" ExtendedFloatingActionButton when map is panned)
      Column(
        modifier =
          Modifier.align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(bottom = peekHeight),
      ) {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
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

        if (selectedEntity == null) {
          // Helper hint chip at the bottom of the map when no location is selected
          Surface(
            modifier =
              Modifier.align(Alignment.CenterHorizontally)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.92f),
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            border = BorderStroke(1.dp, Color(0xFF8BD6B1)),
            shadowElevation = 2.dp,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Default.TouchApp,
                contentDescription = null,
                tint = Color(0xFF8BD6B1),
                modifier = Modifier.size(14.dp),
              )
              Text(
                text = "Drag map to pan • Tap any location or dotted polygon to inspect",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Medium,
              )
            }
          }
        }
      }
    }
  }
}

/**
 * Interactive Straight-Line Navigation HUD Banner displayed at the top of the Map View whenever
 * straight-line navigation to a Geospatial Entity (`NavigationTargetKind.ENTITY`) or a Form
 * Submission (`NavigationTargetKind.SUBMISSION`) is active.
 */
@Composable
private fun StraightLineNavigationHudBanner(
  navState: StraightLineNavigationState,
  state: PrototypeAppState,
) {
  val accentColor =
    if (navState.targetKind == NavigationTargetKind.ENTITY) {
      Color(0xFF00E5FF)
    } else {
      Color(0xFFFFB300)
    }
  val kindLabel =
    if (navState.targetKind == NavigationTargetKind.ENTITY) {
      "${state.entitySingularTypeLabel(navState.targetId).uppercase()} WAYFINDING"
    } else {
      "SUBMISSION WAYFINDING"
    }

  ElevatedCard(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    colors =
      CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.95f),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
      ),
    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
  ) {
    Column(
      modifier =
        Modifier.border(1.5.dp, accentColor, MaterialTheme.shapes.medium)
          .padding(horizontal = 12.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.weight(1f),
        ) {
          // Rotating Compass Arrow Badge
          Surface(
            modifier = Modifier.size(32.dp),
            shape = CircleShape,
            color = accentColor.copy(alpha = 0.16f),
            border = BorderStroke(1.5.dp, accentColor),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Default.Navigation,
                contentDescription = "Compass Bearing Arrow",
                tint = accentColor,
                modifier =
                  Modifier.size(17.dp)
                    .graphicsLayer(rotationZ = navState.vector.bearingDegrees.toFloat()),
              )
            }
          }

          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              GroundTonalBadge(
                text = kindLabel,
                tone =
                  if (navState.targetKind == NavigationTargetKind.ENTITY) {
                    GroundBadgeTone.PRIMARY
                  } else {
                    GroundBadgeTone.TERTIARY
                  },
              )
              Text(
                text =
                  if (navState.vector.isArrived) {
                    "ARRIVED AT TARGET (≤ 8 m)"
                  } else {
                    "~${navState.vector.estimatedWalkMinutes} min walk"
                  },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
              )
            }

            Text(
              text = navState.targetTitle,
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurface,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )

            Text(
              text = navState.targetSubtitle,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        // Distance + Bearing Readout Pill
        Column(
          horizontalAlignment = Alignment.End,
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          Text(
            text = navState.formattedDistance,
            style =
              MaterialTheme.typography.titleSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor,
              ),
          )
          Text(
            text = "Bearing ${navState.vector.formattedBearing}",
            style =
              MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
              ),
          )
        }
      }

      // Bottom Control Strip: "Walk Closer" simulation button + "Stop" navigation button
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Straight-line geodesic vector from your GPS location",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1f),
        )

        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (!navState.vector.isArrived) {
            AssistChip(
              onClick = { state.stepUserTowardNavigationTarget() },
              leadingIcon = {
                Icon(
                  imageVector = Icons.Default.Explore,
                  contentDescription = "Simulate walking closer to target",
                  modifier = Modifier.size(14.dp),
                )
              },
              label = {
                Text(
                  text = "Walk Closer",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                )
              },
            )
          }

          AssistChip(
            onClick = { state.stopNavigation() },
            leadingIcon = {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Stop Straight-Line Navigation",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(14.dp),
              )
            },
            label = {
              Text(
                text = "Stop",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
              )
            },
          )
        }
      }
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
 * 2. **Geospatial Entity Layers (`LayerDef.entity_dataset_id`)** — rendered with solid outlines
 * 3. **Submission Geometry Layers (`LayerDef.form_geometry`)** — rendered with dotted polygon
 *    outlines corresponding to geometry questions/fields in the survey forms
 */
@Composable
private fun MapLayersControlSheet(state: PrototypeAppState) {
  GroundModalBottomSheetOverlay(
    onDismissRequest = { state.updateLayersSheetOpen(false) }
  ) {
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
            text = "Map Layers & Basemap",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "Select Map vs Satellite basemap and toggle survey map layers",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(onClick = { state.updateLayersSheetOpen(false) }) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close Layers",
          )
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
              SegmentedButtonDefaults.itemShape(
                index = index,
                count = BasemapType.entries.size,
              ),
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

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      // Section 2: Data collection sites (geospatial entities)
      Text(
        text = "DATA COLLECTION SITES",
        style =
          MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 0.5.sp,
          ),
      )
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

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      // Section 3: Form submissions (geospatial fields from forms)
      Text(
        text = "FORM SUBMISSIONS",
        style =
          MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 0.5.sp,
          ),
      )
      state.groupedSubmissionLayersByForm.forEach { group ->
        val form = group.form
        val allGroupLayersVisible = group.layers.all { it.isVisible }
        OutlinedCard(
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.medium,
          colors =
            CardDefaults.outlinedCardColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            // Form Title Group Header
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(MaterialTheme.shapes.small)
                  .clickable { state.toggleFormSubmissionLayersVisibility(form.id) }
                  .padding(horizontal = 4.dp, vertical = 2.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
              ) {
                Icon(
                  imageVector = Icons.Default.Description,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.secondary,
                  modifier = Modifier.size(15.dp),
                )
                Text(
                  text = form.title,
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                )
              }
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                GroundTonalBadge(
                  text = "${group.submissions.size} submitted",
                  tone = GroundBadgeTone.SECONDARY,
                )
                Icon(
                  imageVector =
                    if (allGroupLayersVisible) {
                      Icons.Default.Visibility
                    } else {
                      Icons.Default.VisibilityOff
                    },
                  contentDescription =
                    if (allGroupLayersVisible) "Hide ${form.title}" else "Show ${form.title}",
                  tint =
                    if (allGroupLayersVisible) {
                      MaterialTheme.colorScheme.primary
                    } else {
                      MaterialTheme.colorScheme.onSurfaceVariant
                    },
                  modifier = Modifier.size(18.dp),
                )
              }
            }

            group.layers.forEach { layer ->
              val geomCount = state.submissionGeometries.count { it.layerId == layer.id }
              Surface(
                onClick = { state.toggleLayerVisibility(layer.id) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface,
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  // Dotted polygon swatch rendered with Canvas PathEffect.dashPathEffect
                  Canvas(modifier = Modifier.size(18.dp)) {
                    drawRect(
                      color = Color(layer.colorHex).copy(alpha = 0.22f),
                      size = size,
                    )
                    drawRect(
                      color = Color(layer.colorHex),
                      size = size,
                      style =
                        Stroke(
                          width = 2.2f,
                          pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.5f, 2.5f), 0f),
                        ),
                    )
                  }
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = layer.label,
                      style = MaterialTheme.typography.labelMedium,
                      fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                      text =
                        "${layer.geometryTypeLabel} • $geomCount ${if (geomCount == 1) "submission" else "submissions"}",
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
        }
      }

      Spacer(modifier = Modifier.height(12.dp))
    }
  }
}

/**
 * Bottom Sheet shown when a survey location is clicked on the map:
 * - Shows main location metadata (Label, Dataset, `GeoID`, Area/Perimeter, Properties).
 * - For `1:1` (`SubmissionModel.SINGLE_1_TO_1`) with data: shows the submission
 *   data directly inline in the card.
 * - For `1:N` (`SubmissionModel.MULTIPLE_1_TO_N`): shows a list of submissions
 *   (data collector, timestamp) which can be clicked to see the full submission details.
 */
@Composable
private fun EntityBottomSheetCard(
  entity: GeospatialEntityItem,
  state: PrototypeAppState,
  modifier: Modifier = Modifier,
) {
  val selectedSubmission = state.selectedSubmission
  val isDark = state.isDarkTheme
  val sheetBg = if (isDark) Color(0xFF1E2522) else Color.White
  val textColor = if (isDark) Color.White else Color(0xFF111827)

  // Format area/perimeter according to active unit system
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

  Column(
    modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    // Header: Reference Badge + Title + Close button
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
            Modifier.size(28.dp)
              .clip(CircleShape)
              .background(Color(entity.colorHex)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector =
              if (entity.geometryTypeLabel == "Polygon") {
                Icons.Default.Polyline
              } else {
                Icons.Default.LocationOn
              },
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(15.dp),
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = entity.label,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
          )
          Text(
            text = "${entity.datasetName} • ${entity.geometryTypeLabel} ($areaFormatted, $perimeterFormatted)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
          )
        }
      }

      // Close Bottom Sheet Button
      IconButton(
        onClick = { state.selectEntity(null) },
        modifier = Modifier.size(32.dp),
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "Close Location Sheet",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(18.dp),
        )
      }
    }

    // Metadata & Share Actions Row: GeoID + QR Code Link + Share PDF Link
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // GeoID Badge
      GroundTonalBadge(
        text = "GeoID: ${entity.geoId}",
        tone = GroundBadgeTone.PRIMARY,
        monospace = true,
      )

      // Live Distance & Compass Bearing Badge from User GPS
      val entityWayfindingBadge = state.formattedWayfindingBadgeForEntity(entity.id)
      if (entityWayfindingBadge.isNotEmpty()) {
        GroundTonalBadge(
          text = "➤ $entityWayfindingBadge",
          tone = GroundBadgeTone.TERTIARY,
          monospace = true,
        )
      }

      // Straight-Line Navigation Toggle Button for Geospatial Entity
      val isNavigatingEntity = state.isNavigatingToEntity(entity.id)
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

    // Organizer-defined Action Buttons for this dataset type (`form.targetDatasetId == entity.datasetId`)
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
      modifier =
        Modifier.weight(1f)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
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

      // --- SUBMISSION DISPLAY LOGIC ---
      if (entity.submissionModel == SubmissionModel.SINGLE_1_TO_1) {
        // Single-Submission -> Directly show the submission data inline on the card!
        val sub = entity.submissions.firstOrNull()
        if (sub != null) {
          OneToOneInlineSubmissionCard(
            submission = sub,
            state = state,
            isDark = isDark,
            textColor = textColor,
            onSharePdf = { state.shareSubmissionPdf(sub.id) },
          )
        } else {
          Text(
            text = "No baseline submission recorded yet for this ${entity.singularTypeLabel.lowercase()}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        // Multi-Submission Entity -> Either show Full Submission Details (if a row was clicked)
        // OR show the list of submissions grouped by form title!
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
        } else {
          OneToManySubmissionsListSection(
            entity = entity,
            state = state,
            isDark = isDark,
            textColor = textColor,
            onSelectSubmission = { state.selectSubmissionDetail(it.id) },
          )
        }
      }
    }
  }
}

/**
 * Renders inline submission data for a `SubmissionModel.SINGLE_1_TO_1` entity with data,
 * headed directly by the form's title (`submission.formTitle`).
 */
@Composable
private fun OneToOneInlineSubmissionCard(
  submission: SubmissionPreviewItem,
  state: PrototypeAppState,
  isDark: Boolean,
  textColor: Color,
  onSharePdf: () -> Unit,
) {
  OutlinedCard(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
    colors =
      CardDefaults.outlinedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
      ),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
          modifier = Modifier.weight(1f),
        ) {
          Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = submission.formTitle,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
          )
        }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
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
            text = submission.formVersion,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Icon(
          imageVector = Icons.Default.Person,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(13.dp),
        )
        Text(
          text = "Collector: ${submission.collectorName} •",
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
          imageVector = Icons.Default.Schedule,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(12.dp),
        )
        Text(
          text = submission.timestamp,
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      submission.fields.forEach { field ->
        val fieldWayfindingBadge =
          state.formattedWayfindingBadgeForSubmissionField(submission.id, field)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.Top,
        ) {
          Text(
            text = field.questionLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.48f),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Column(
            modifier = Modifier.weight(0.52f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Text(
              text = field.answerValue,
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.onSurface,
            )
            if (fieldWayfindingBadge.isNotEmpty()) {
              GroundTonalBadge(
                text = "➤ $fieldWayfindingBadge",
                tone = GroundBadgeTone.WARNING,
                monospace = true,
              )
            }
          }
        }
      }
    }
  }
}

/**
 * For `1:N` (`SubmissionModel.MULTIPLE_1_TO_N`) entities, groups submissions by form and uses the
 * title of the form as each group's header rather than saying the word "Form".
 */
@Composable
private fun OneToManySubmissionsListSection(
  entity: GeospatialEntityItem,
  state: PrototypeAppState,
  isDark: Boolean,
  textColor: Color,
  onSelectSubmission: (SubmissionPreviewItem) -> Unit,
) {
  val formGroups = state.groupedSubmissionsForEntity(entity)
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    formGroups.forEach { group ->
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
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp),
              modifier = Modifier.weight(1f),
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
                CardDefaults.outlinedCardColors(
                  containerColor = MaterialTheme.colorScheme.surface
                ),
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
                  Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Icon(
                      imageVector = Icons.Default.Person,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onSurface,
                      modifier = Modifier.size(13.dp),
                    )
                    Text(
                      text = sub.collectorName,
                      style =
                        MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                      color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (index == 0) {
                      GroundTonalBadge(
                        text = "LATEST",
                        tone = GroundBadgeTone.SECONDARY,
                      )
                    }
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

                IconButton(
                  onClick = { onSelectSubmission(sub) },
                  modifier = Modifier.size(32.dp),
                ) {
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
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
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
          Text(
            text = "${submission.targetTypeLabel}: ${submission.entityLabel}",
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
 * Searchable List View of the active survey (`MainSurveyViewMode.LIST`) displaying survey
 * locations (grouped by their domain dataset) and submissions (grouped by form).
 */
@Composable
private fun SurveyListView(state: PrototypeAppState) {
  val isDark = state.isDarkTheme
  val surfaceColor = MaterialTheme.colorScheme.surface
  val textColor = MaterialTheme.colorScheme.onSurface
  val selectedSubmission = state.selectedSubmission

  Column(modifier = Modifier.fillMaxSize().background(surfaceColor)) {
    // Search Bar + Filter Tabs Header
    Surface(
      modifier = Modifier.fillMaxWidth(),
      color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = state.listSearchQuery,
          onValueChange = { state.updateListSearchQuery(it) },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          placeholder = {
            Text(
              text = "Search ${state.activeEntitiesCountNoun} or submissions...",
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

        // Category Filter Tabs: All | Locations | Submissions (Grouped by Form)
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          ListFilterTab.entries.forEach { tab ->
            val isSelected = state.listFilterTab == tab
            val countLabel =
              when (tab) {
                ListFilterTab.ALL -> state.entities.size + state.allSubmissions.size
                ListFilterTab.ENTITIES -> state.entities.size
                ListFilterTab.SUBMISSIONS -> state.allSubmissions.size
              }
            FilterChip(
              selected = isSelected,
              onClick = { state.selectListFilterTab(tab) },
              label = {
                Text(
                  text = "${state.tabLabelFor(tab)} ($countLabel)",
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
      }
    }

    // If a submission is currently inspected in full detail, show it with a Back button
    if (selectedSubmission != null) {
      Column(
        modifier =
          Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
      ) {
        SubmissionFullDetailsCard(
          submission = selectedSubmission,
          state = state,
          isDark = isDark,
          textColor = textColor,
          backLabel = "Back to Searchable List",
          onBack = { state.selectSubmissionDetail(null) },
          onSharePdf = { state.shareSubmissionPdf(selectedSubmission.id) },
        )
      }
      return
    }

    // Scrollable Searchable List of survey locations (grouped by dataset) and submissions (grouped by Form)
    val matchedEntities = state.filteredListEntities
    val groupedSubmissions = state.groupedFilteredListSubmissions

    Column(
      modifier =
        Modifier.weight(1f)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 14.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // 1. SURVEY LOCATIONS SECTION (GROUPED BY DOMAIN DATASET)
      if (matchedEntities.isNotEmpty()) {
        val groupedByDataset = matchedEntities.groupBy { it.datasetName }
        groupedByDataset.forEach { (datasetName, datasetEntities) ->
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            Icon(
              imageVector = Icons.Default.LocationOn,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = "${datasetName.uppercase()} (${datasetEntities.size})",
              style =
                MaterialTheme.typography.labelSmall.copy(
                  fontWeight = FontWeight.Bold,
                  letterSpacing = 0.6.sp,
                ),
              color = MaterialTheme.colorScheme.primary,
            )
          }
          datasetEntities.forEach { entity ->
            val isNavigatingEntity = state.isNavigatingToEntity(entity.id)
            val entityWayfindingBadge = state.formattedWayfindingBadgeForEntity(entity.id)
            OutlinedCard(
              onClick = {
                state.selectEntity(entity.id)
                state.setMainSurveyViewMode(MainSurveyViewMode.MAP)
              },
              modifier = Modifier.fillMaxWidth(),
              shape = MaterialTheme.shapes.medium,
              colors =
                CardDefaults.outlinedCardColors(
                  containerColor = MaterialTheme.colorScheme.surface
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
                  Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                  ) {
                    Text(
                      text = entity.label,
                      style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                      color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                      Text(
                        text = "GeoID: ${entity.geoId}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                      )
                      if (entityWayfindingBadge.isNotEmpty()) {
                        GroundTonalBadge(
                          text = "➤ $entityWayfindingBadge",
                          tone = GroundBadgeTone.TERTIARY,
                          monospace = true,
                        )
                      }
                    }
                    Text(
                      text = "${entity.datasetName} • ${entity.submissions.size} submission(s)",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                  ) {
                    Text(
                      text = "View on Map",
                      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                      color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                      imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(12.dp),
                    )
                  }
                }

                // Quick Navigate, QR Code & Share PDF actions for location item in List View
                Row(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  FilterChip(
                    selected = isNavigatingEntity,
                    onClick = { state.startNavigationToEntity(entity.id) },
                    label = {
                      Text(
                        text = if (isNavigatingEntity) "Navigating" else "Navigate",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                      )
                    },
                    leadingIcon = {
                      Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = "Navigate to ${entity.singularTypeLabel}",
                        modifier = Modifier.size(12.dp),
                      )
                    },
                  )
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
                        modifier = Modifier.size(12.dp),
                      )
                    },
                  )
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
                        modifier = Modifier.size(12.dp),
                      )
                    },
                  )
                }
              }
            }
          }
        }
      }

      // 2. SUBMISSIONS SECTION (GROUPED BY FORM TITLE)
      if (groupedSubmissions.isNotEmpty()) {
        groupedSubmissions.forEach { group ->
          val form = group.form
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            Icon(
              imageVector = Icons.Default.Description,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = "${form.title.uppercase()} (${group.submissions.size})",
              style =
                MaterialTheme.typography.labelSmall.copy(
                  fontWeight = FontWeight.Bold,
                  letterSpacing = 0.6.sp,
                ),
              color = MaterialTheme.colorScheme.primary,
            )
          }
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
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              // Form Group Header Banner
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Default.Description,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(14.dp),
                    )
                    Text(
                      text = form.title,
                      style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                      color = MaterialTheme.colorScheme.onSurface,
                    )
                  }
                  Text(
                    text = "Action: \"${form.ctaLabel}\" • ${form.version}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                  )
                }

                GroundTonalBadge(
                  text = "${group.submissions.size} submitted",
                  tone = GroundBadgeTone.PRIMARY,
                )
              }

              // Submissions belonging to this Form group
              group.submissions.forEach { sub ->
                OutlinedCard(
                  onClick = { state.selectSubmissionDetail(sub.id) },
                  modifier = Modifier.fillMaxWidth(),
                  shape = MaterialTheme.shapes.small,
                  colors =
                    CardDefaults.outlinedCardColors(
                      containerColor = MaterialTheme.colorScheme.surface
                    ),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Column(
                      modifier = Modifier.weight(1f),
                      verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                      ) {
                        Text(
                          text = "${sub.targetTypeLabel}: ${sub.entityLabel}",
                          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                          color = MaterialTheme.colorScheme.onSurface,
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
                          text = "${sub.collectorName} •",
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                          imageVector = Icons.Default.Schedule,
                          contentDescription = null,
                          tint = MaterialTheme.colorScheme.onSurfaceVariant,
                          modifier = Modifier.size(11.dp),
                        )
                        Text(
                          text = sub.timestamp,
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                    }
                    IconButton(
                      onClick = { state.selectSubmissionDetail(sub.id) },
                      modifier = Modifier.size(32.dp),
                    ) {
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

      if (matchedEntities.isEmpty() && groupedSubmissions.isEmpty()) {
        Box(
          modifier = Modifier.fillMaxWidth().padding(32.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "No ${state.activeEntitiesCountNoun} or submissions match \"${state.listSearchQuery}\".",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
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
      Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.fillMaxWidth()) {
          // User Profile & Organization Header
          Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF144532),
            contentColor = Color.White,
          ) {
            Column(
              modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Box(
                  modifier =
                    Modifier.size(44.dp)
                      .clip(CircleShape)
                      .background(Color(0xFF8BD6B1)),
                  contentAlignment = Alignment.Center,
                ) {
                  Text(
                    text = "ML",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color(0xFF003825),
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

              Text(
                text = state.signedInUserName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
              )
              Text(
                text = state.signedInUserEmail,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFC8E6C9),
              )
              Text(
                text = state.signedInOrganization,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFF8BD6B1),
              )
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
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }

          Spacer(modifier = Modifier.height(8.dp))

          // Navigation Drawer Options using M3 NavigationDrawerItem
          DrawerMenuItem(
            icon = Icons.Default.SwapHoriz,
            title = "Surveys",
            subtitle = "${state.downloadedSurveyCount} downloaded on device",
            selected = state.activeDrawerSubView == MainDrawerSubView.SWITCH_SURVEYS,
            onClick = { state.drawerSwitchSurveys() },
          )
          DrawerMenuItem(
            icon = Icons.Default.Map,
            title = "Offline maps",
            subtitle = "Mapbox vector & satellite raster tile cache",
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
          text = "Open Foris Ground 2.0 • Offline-First Core",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(18.dp),
        )
      }
    }
  }
}

@Composable
private fun DrawerMenuItem(
  icon: ImageVector,
  title: String,
  subtitle: String,
  selected: Boolean,
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
    },
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
 * Separate screen accessible from the `"Surveys"` navigation drawer option showing ONLY the
 * surveys which have already been downloaded to the device, plus a primary action button
 * (`"Browse & download more surveys"`) which navigates to the full `Download surveys` screen.
 */
@Composable
private fun SwitchDownloadedSurveysSubScreen(state: PrototypeAppState) {
  val downloadedList = state.downloadedSurveys

  Column(
    modifier =
      Modifier.fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
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
      OutlinedButton(
        onClick = { state.closeDrawerSubView() },
        shape = MaterialTheme.shapes.small,
      ) {
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
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
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
                GroundTonalBadge(
                  text = "ACTIVE",
                  tone = GroundBadgeTone.PRIMARY,
                )
              }
            }
            Text(
              text = "${survey.location} • ${survey.entityCount} locations • ${survey.offlineSizeLabel}",
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
            Button(
              onClick = { state.openSurvey(survey.id) },
              shape = MaterialTheme.shapes.small,
            ) {
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
private fun EntityQrCodeModalDialog(
  state: PrototypeAppState,
  entity: GeospatialEntityItem,
) {
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
          Box(
            modifier = Modifier.padding(12.dp),
            contentAlignment = Alignment.Center,
          ) {
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
          text = "Scan with Ground or any EUDR compliance reader to verify ${entity.singularTypeLabel.lowercase()} geometry & GeoID.",
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
    dismissButton = {
      TextButton(onClick = { state.closeEntityQrCode() }) {
        Text("Close")
      }
    },
  )
}

/**
 * Modal bottom sheet allowing the user to share a generated offline PDF receipt for either a
 * Geospatial Entity or a Submission (`state.activeSharedPdfSheet`) to their preferred app.
 */
@Composable
private fun SharePdfToAppModalDialog(
  state: PrototypeAppState,
  sheet: SharedPdfSheetState,
) {
  GroundModalBottomSheetOverlay(
    onDismissRequest = { state.closeSharePdfSheet() }
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = 20.dp)
          .padding(bottom = 24.dp),
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
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close Share PDF Sheet",
          )
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
                  fontFamily = FontFamily.Monospace,
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
      ) {
        Text("Done")
      }
    }
  }
}

/**
 * Drawer Sub-Screen: `Offline maps` (consistent with `docs/design/00-index.md` "Offline
 * Storage Safeguards & Media Purging" — Mapbox vector & raster tiles and 500 MB storage guardrail).
 */
@Composable
private fun ManageOfflineMapsSubScreen(state: PrototypeAppState) {
  Column(
    modifier =
      Modifier.fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
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
      OutlinedButton(
        onClick = { state.closeDrawerSubView() },
        shape = MaterialTheme.shapes.small,
      ) {
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
        CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
    ) {
      Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
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
            "Pre-cached Mapbox vector & satellite raster tiles across zoom levels 10–19. Downloads automatically pause if device storage drops below 500 MB.",
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
          CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
          ),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
          ) {
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

