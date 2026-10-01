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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.groundplatform.v2.core.forms.ui.GroundBadgeTone
import org.groundplatform.v2.core.forms.ui.GroundModalBottomSheetOverlay
import org.groundplatform.v2.core.forms.ui.GroundTonalBadge
import org.groundplatform.v2.devtools.prototypeapp.map.framingInsets
import org.groundplatform.v2.devtools.prototypeapp.map.rememberSurveyMapCamera

/**
 * 5. Main Survey UI screen (`PrototypeScreen.MAIN_SURVEY`) providing:
 * - Top App Bar with Hamburger Menu and active survey title.
 * - **Map View**: Displays Ground geospatial entities on the map, a `"Layers"` button to toggle
 *   layer visibility, and an interactive **Entity Bottom Sheet** when an entity is clicked:
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
internal fun MainSurveyTopAppBar(state: PrototypeAppState) {
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
          imageVector = Icons.Outlined.Menu,
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
            imageVector = Icons.Outlined.LocationOn,
            contentDescription = null,
            tint = Color(0xFFB7F1B9),
            modifier = Modifier.size(12.dp),
          )
          Text(
            text = state.activeSurvey.location,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFB7F1B9),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    },
    actions = { PrototypeDebugToolsButton(state = state, iconTint = Color.White) },
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
 *   search bar by default, expands into the searchable list of map layers, data tables, and places,
 *   and transitions in-place to `EntityBottomSheetCard` when a map feature is selected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SurveyMapView(state: PrototypeAppState) {
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
    } else if (
      !state.isEntityBottomSheetExpanded && sheetState.currentValue != SheetValue.PartiallyExpanded
    ) {
      sheetState.partialExpand()
    }
  }

  val mapCamera =
    rememberSurveyMapCamera(desired = state::desiredMapCamera, onSettled = state::syncMapCamera)

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    // With a map feature selected, the sheet peeks at about half the screen: enough to show the
    // feature's details while the map above frames the feature itself.
    val peekHeight =
      if (selectedEntity != null || state.selectedSubmission != null) {
        (maxHeight * 0.45f).coerceAtLeast(152.dp)
      } else {
        122.dp
      }

    // Fit a newly selected map feature into the map area above the sheet. Mobile has no side
    // details panel, so only the sheet is compensated for (vertically); the feature stays centered
    // horizontally. Records without geometry replace the sheet's contents without moving the map.
    LaunchedEffect(selectedEntity?.id, state.entitySelectionEpoch) {
      val entity = selectedEntity ?: return@LaunchedEffect
      if (!entity.hasGeometry) return@LaunchedEffect
      val visibleHeight = (maxHeight - peekHeight).coerceAtLeast(0.dp)
      val targetScreenY =
        if (maxHeight > 0.dp) ((visibleHeight / 2) / maxHeight).coerceIn(0.10f, 0.50f) else 0.50f
      state.recenterMapOnEntity(entity, targetScreenY)
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
        SurveyMainMap(state = state, camera = mapCamera, modifier = Modifier.fillMaxSize())

        // 3. Top Map Overlay: Docked Navigation HUD Banner (flush with toolbar) + Floating Map
        // Chips
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
              // Combined GPS Status / Auto-Center Chip over the map
              Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.92f),
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                border =
                  BorderStroke(
                    1.dp,
                    if (state.isCameraFollowingUser) Color(0xFF4CAF50) else Color(0xFFFFCC80),
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
                      if (state.isCameraFollowingUser) {
                        Icons.Outlined.SatelliteAlt
                      } else {
                        Icons.Outlined.MyLocation
                      },
                    contentDescription =
                      if (state.isCameraFollowingUser) "GPS Auto-Center" else "Panned",
                    tint =
                      if (state.isCameraFollowingUser) Color(0xFF8BD6B1) else Color(0xFFFFCC80),
                    modifier = Modifier.size(14.dp),
                  )
                  Text(
                    text =
                      if (state.isCameraFollowingUser) {
                        "GPS: ${state.gnssStatusChipLabel}"
                      } else {
                        "Panned"
                      },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    fontWeight = FontWeight.Bold,
                  )
                }
              }

              // Layers FAB to control basemaps and map layers
              LayersFloatingActionButton(state = state)
            }

            // Selected Cluster Balloon detail callout when a Mapbox cluster balloon is tapped
            if (state.isMapClusteringActive && state.selectedCluster != null) {
              MapClusterBalloonsOverlay(state = state)
            }
          }
        }

        // 5. Bottom Overlay Stack: Google Maps-style Horizontal Scale Widget in bottom-left,
        //    bottom-center data collection FAB, and optional "Recenter" button in bottom-right
        val isSheetExpanded =
          state.isEntityBottomSheetExpanded || sheetState.targetValue == SheetValue.Expanded

        // Scale bar in bottom-left
        Box(
          modifier =
            Modifier.align(Alignment.BottomStart)
              .padding(start = 14.dp, bottom = peekHeight + 10.dp)
        ) {
          GoogleMapsScaleBarWidget(
            scaleSpec = state.mapScaleBarSpec,
            isSatellite = state.selectedBasemapType == BasemapType.SATELLITE,
          )
        }

        // Optional "Recenter" button in bottom-right when panned away from user
        if (!isSheetExpanded && !state.isCameraFollowingUser) {
          ExtendedFloatingActionButton(
            onClick = { state.recenterMapOnUser() },
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
            DataCollectionFormsFab(state = state)
          }
        }
      }
    }
  }
}

/**
 * Renders the expanded callout card for the currently selected [MapFeatureCluster] balloon when a
 * geographically pinned Mapbox cluster balloon is tapped on the map.
 */
@Composable
internal fun MapClusterBalloonsOverlay(state: PrototypeAppState) {
  val selectedCluster = state.selectedCluster ?: return
  SelectedClusterBalloonDetailCard(
    sitesCountLabel = state.formatClusterSitesCountLabel(selectedCluster.siteCount),
    cluster = selectedCluster,
    onZoomIn = { state.zoomIntoCluster(selectedCluster.id) },
    onDismiss = { state.selectCluster(null) },
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
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              softWrap = false,
            )
          }
          IconButton(onClick = onDismiss, modifier = Modifier.size(22.dp)) {
            Icon(
              imageVector = Icons.Outlined.Close,
              contentDescription = "Dismiss cluster balloon",
              tint = Color.White.copy(alpha = 0.8f),
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
 * Floating Action Button (`FloatingActionButton`) on the Main Survey screen that opens the
 * [AvailableFormsModalSheet] list of available forms to start data collection without requiring a
 * geospatial entity to be pre-selected from the map.
 */
@Composable
internal fun DataCollectionFormsFab(state: PrototypeAppState, modifier: Modifier = Modifier) {
  val formsBg =
    if (state.isDarkTheme) {
      MaterialTheme.colorScheme.primary
    } else {
      MaterialTheme.colorScheme.primaryContainer
    }
  val formsContent =
    if (state.isDarkTheme) {
      MaterialTheme.colorScheme.onPrimary
    } else {
      MaterialTheme.colorScheme.onPrimaryContainer
    }
  FloatingActionButton(
    onClick = { state.openAvailableFormsSheet() },
    modifier = modifier,
    containerColor = formsBg,
    contentColor = formsContent,
  ) {
    Icon(imageVector = Icons.Outlined.Add, contentDescription = "Collect data")
  }
}

/**
 * Modal bottom sheet opened by the bottom-centered [DataCollectionFormsFab] listing all available
 * forms in the active survey. Selecting a form launches data collection via
 * [PrototypeAppState.launchFormFromFab], which presents the Map or List entity selector at the step
 * in the data collection process where an `entityref` is required.
 */
@Composable
internal fun AvailableFormsModalSheet(state: PrototypeAppState) {
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
            text = "Select a form to start data collection.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(onClick = { state.closeAvailableFormsSheet() }) {
          Icon(imageVector = Icons.Outlined.Close, contentDescription = "Close Available Forms")
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      state.forms.forEach { form ->
        val eligibleCount = state.eligibleEntitiesForForm(form).size
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
              onClick = { state.launchFormFromFab(form.id) },
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
                imageVector = Icons.Outlined.Navigation,
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
                    imageVector = Icons.Outlined.Explore,
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
                  imageVector = Icons.Outlined.Close,
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

/** Floating Action Button that toggles the map layers and basemap selector. */
@Composable
internal fun LayersFloatingActionButton(state: PrototypeAppState, modifier: Modifier = Modifier) {
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
    modifier = modifier,
  ) {
    Icon(imageVector = Icons.Outlined.Layers, contentDescription = "Layers")
  }
}

/**
 * Shared body of the layers & basemap selector:
 * 1. Basemap switcher (`Map` vs `Satellite`)
 * 2. Offline basemap tile package overlay toggle
 * 3. Map layers toggles for survey geospatial entities
 */
@Composable
internal fun LayersSelectorContent(state: PrototypeAppState, modifier: Modifier = Modifier) {
  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                  Icons.Outlined.Map
                } else {
                  Icons.Outlined.SatelliteAlt
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
        CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
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

      // Section 2: Map layers (geospatial entities)
      Text(
        text = "MAP LAYERS",
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
                color = layerNameColor(layer.isVisible),
              )
              Text(
                text = "${layer.geometryTypeLabel} • ${layer.formatCountLabel(layerEntityCount)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            LayerVisibilityToggle(layer = layer, state = state)
          }
        }
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
internal fun LayersControlSheet(state: PrototypeAppState) {
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
          Icon(imageVector = Icons.Outlined.Close, contentDescription = "Close layers")
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      LayersSelectorContent(state = state)

      Spacer(modifier = Modifier.height(12.dp))
    }
  }
}

/** Backward-compatible alias for [MainSurveyScreen]. */
@Composable
fun GroundMainSurveyScreen(state: PrototypeAppState) {
  MainSurveyScreen(state)
}
