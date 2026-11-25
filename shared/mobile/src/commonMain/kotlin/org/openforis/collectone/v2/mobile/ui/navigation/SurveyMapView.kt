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
package org.openforis.collectone.v2.mobile.ui.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.SatelliteAlt
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.BasemapType
import org.openforis.collectone.v2.app.domain.model.hasGeometry
import org.openforis.collectone.v2.app.ui.common.EARTH_ENGINE_MAP_BADGE_TAG
import org.openforis.collectone.v2.app.ui.common.LocalMobileTopInset
import org.openforis.collectone.v2.app.ui.common.PoweredByEarthEngineBadge
import org.openforis.collectone.v2.app.ui.datacollection.DataCollectionActions
import org.openforis.collectone.v2.app.ui.datacollection.DataCollectionUiState
import org.openforis.collectone.v2.app.ui.datacollection.EntityDetailsActions
import org.openforis.collectone.v2.app.ui.datacollection.SearchableListActions
import org.openforis.collectone.v2.app.ui.datacollection.SurveyMainMap
import org.openforis.collectone.v2.app.ui.datacollection.SurveyPersistentBottomSheetContent
import org.openforis.collectone.v2.app.ui.map.BasemapPreviewCard
import org.openforis.collectone.v2.app.ui.map.GoogleMapsScaleBarWidget
import org.openforis.collectone.v2.app.ui.map.MapClusterBalloonsOverlay
import org.openforis.collectone.v2.app.ui.map.SurveyMapActions
import org.openforis.collectone.v2.app.ui.map.SurveyMapUiState
import org.openforis.collectone.v2.app.ui.map.framingInsets
import org.openforis.collectone.v2.app.ui.map.rememberSurveyMapCamera
import org.openforis.collectone.v2.app.ui.text.gnssAccuracyText
import org.openforis.collectone.v2.core.forms.ui.LocalGeoLocationSource
import org.openforis.collectone.v2.core.forms.ui.controls.ExtendedFloatingActionButton
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.mobile.resources.Res
import org.openforis.collectone.v2.mobile.resources.mobile_back
import org.openforis.collectone.v2.mobile.resources.mobile_gps_auto_center
import org.openforis.collectone.v2.mobile.resources.mobile_gps_panned
import org.openforis.collectone.v2.mobile.resources.mobile_gps_status
import org.openforis.collectone.v2.mobile.resources.mobile_recenter
import org.openforis.collectone.v2.mobile.resources.mobile_recenter_content_description
import org.openforis.collectone.v2.mobile.ui.offlinemaps.OfflineAreaSelectionActions
import org.openforis.collectone.v2.mobile.ui.offlinemaps.OfflineAreaSelectionOverlay

/**
 * Interactive Map View showing:
 * - Floating top search bar ([MobileMapSearchBar]) with Hamburger menu and user profile avatar
 * - The outlines of the map areas saved for offline use, and their imagery over the other basemap
 * - **Geospatial Entities** (`EntityType.GEOSPATIAL`, rendered with solid polygon outlines)
 * - A basemap preview card ([BasemapPreviewCard]) that opens the layers sheet
 *   ([LayersControlSheet]) to select the basemap
 * - A unified persistent bottom sheet (`SurveyPersistentBottomSheetContent`) that peeks with the
 *   active survey title and area name by default, expands into the searchable list of map layers,
 *   data tables, and places, and transitions in-place to `EntityBottomSheetCard` when a map feature
 *   is selected.
 *
 * In the area selection mode ([MainSurveyUiState.offlineAreaSelection]), the map stays mounted with
 * its camera, the sheet, search bar, chips, and buttons hide, and [OfflineAreaSelectionOverlay]
 * frames the area to save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SurveyMapView(
  uiState: MainSurveyUiState,
  mapUiState: SurveyMapUiState,
  dataCollectionUiState: DataCollectionUiState,
  actions: MainSurveyActions,
  mapActions: SurveyMapActions,
  dataCollectionActions: DataCollectionActions,
  listActions: SearchableListActions,
  detailsActions: EntityDetailsActions,
  offlineAreaActions: OfflineAreaSelectionActions,
) {
  val isSelectingArea = uiState.offlineAreaSelection.isActive
  val selectedEntity = mapUiState.selectedEntity
  val selectedSubmission = dataCollectionUiState.selectedSubmission

  val sheetState =
    rememberStandardBottomSheetState(
      initialValue =
        if (mapUiState.isEntityBottomSheetExpanded) {
          SheetValue.Expanded
        } else {
          SheetValue.PartiallyExpanded
        },
      skipHiddenState = true,
    )
  val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

  LaunchedEffect(sheetState.currentValue) {
    val expanded = sheetState.currentValue == SheetValue.Expanded
    if (mapUiState.isEntityBottomSheetExpanded != expanded) {
      mapActions.updateEntityBottomSheetExpanded(expanded)
    }
  }

  LaunchedEffect(mapUiState.selectedEntityId, mapUiState.isEntityBottomSheetExpanded) {
    if (mapUiState.isEntityBottomSheetExpanded && sheetState.currentValue != SheetValue.Expanded) {
      sheetState.expand()
    } else if (
      !mapUiState.isEntityBottomSheetExpanded &&
        sheetState.currentValue != SheetValue.PartiallyExpanded
    ) {
      sheetState.partialExpand()
    }
  }

  val mapCamera = rememberSurveyMapCamera(uiState = mapUiState, actions = mapActions)
  val geoLocationSource = LocalGeoLocationSource.current
  val liveFix by geoLocationSource.currentFix.collectAsState()
  LaunchedEffect(liveFix, geoLocationSource) {
    val fix = liveFix ?: return@LaunchedEffect
    if (!geoLocationSource.isSimulated) {
      mapActions.updateDeviceLocation(fix.latitude, fix.longitude, fix.accuracyMeters)
    }
  }
  val topInset = LocalMobileTopInset.current
  val topSearchBarReservedHeight = 66.dp + topInset

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val hasSelectedRecord = selectedEntity != null || selectedSubmission != null
    val peekHeight = mainSurveySheetPeekHeight(maxHeight, isSelectingArea, hasSelectedRecord)
    val isSheetExpanded =
      mapUiState.isEntityBottomSheetExpanded || sheetState.targetValue == SheetValue.Expanded
    val handleTopPadding = if (isSheetExpanded) topSearchBarReservedHeight else 0.dp

    // Fit a newly selected map feature into the map area above the sheet. Records without geometry
    // replace the sheet's contents without moving the map.
    LaunchedEffect(selectedEntity?.id, mapUiState.entitySelectionEpoch) {
      val entity = selectedEntity ?: return@LaunchedEffect
      if (!entity.hasGeometry) return@LaunchedEffect
      mapActions.recenterMapOnEntity(entity, selectedFeatureScreenY(maxHeight, peekHeight))
      val bounds = uiState.selectedEntityBounds ?: return@LaunchedEffect
      mapCamera.run {
        it.fitBounds(
          bounds,
          framingInsets(it.viewportSize, bottom = peekHeight),
          maxZoom = mapUiState.maxFramingZoom(entity),
        )
      }
    }

    BottomSheetScaffold(
      scaffoldState = scaffoldState,
      sheetPeekHeight = peekHeight,
      sheetShape =
        if (isSheetExpanded) {
          RoundedCornerShape(0.dp)
        } else {
          RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        },
      sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
      sheetTonalElevation = 0.dp,
      sheetShadowElevation = 8.dp,
      sheetSwipeEnabled = !isSelectingArea,
      sheetDragHandle = {
        if (isSelectingArea) return@BottomSheetScaffold
        if (hasSelectedRecord) {
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .padding(top = handleTopPadding)
                .padding(horizontal = 8.dp, vertical = 2.dp)
          ) {
            IconButton(
              onClick = {
                val back =
                  selectedRecordBack(
                    hasSelectedEntity = selectedEntity != null,
                    hasSelectedSubmission = selectedSubmission != null,
                    entityDetailsPane = uiState.entityDetailsPane,
                  )
                when (back) {
                  SelectedRecordBack.CLOSE_SUBMISSION ->
                    dataCollectionActions.selectSubmissionDetail(null)
                  SelectedRecordBack.SHOW_PROPERTIES -> dataCollectionActions.showEntityProperties()
                  SelectedRecordBack.RETURN_TO_LIST ->
                    dataCollectionActions.returnToBottomSheetList()
                }
              },
              modifier = Modifier.align(Alignment.CenterStart).size(36.dp),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(Res.string.mobile_back),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
              )
            }
            BottomSheetDefaults.DragHandle(modifier = Modifier.align(Alignment.Center))
          }
        } else {
          Box(modifier = Modifier.padding(top = handleTopPadding)) {
            BottomSheetDefaults.DragHandle()
          }
        }
      },
      containerColor = Color.Transparent,
      sheetContent = {
        if (!isSelectingArea) {
          SurveyPersistentBottomSheetContent(
            uiState = uiState.bottomSheet,
            listActions = listActions,
            detailsActions = detailsActions,
            onReturnToList = dataCollectionActions::returnToBottomSheetList,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
          )
        }
      },
    ) {
      BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {
        // Survey map: native basemap and feature layers with Compose markers on top.
        SurveyMainMap(
          uiState = mapUiState,
          actions = mapActions,
          dataCollection = dataCollectionUiState,
          formMap = dataCollectionActions,
          pendingIds = uiState.pendingUploadEntityIds,
          camera = mapCamera,
          modifier = Modifier.fillMaxSize(),
        )
        // The area selection mode hides the map's controls; its overlay is drawn over everything.
        if (isSelectingArea) return@BoxWithConstraints

        // Top Map Overlay below the floating search bar: Navigation HUD Banner + Floating Map Chips
        Column(
          modifier =
            Modifier.align(Alignment.TopCenter)
              .fillMaxWidth()
              .padding(top = topSearchBarReservedHeight)
        ) {
          val activeNav = mapUiState.activeNavigation
          if (activeNav != null) {
            StraightLineNavigationHudBanner(
              navState = activeNav,
              targetTypeLabel = mapUiState.entityTypeText(activeNav.targetId).resolve(),
              isDarkTheme = uiState.isDarkTheme,
              actions = mapActions,
            )
          }

          // Floating Map Chips: GPS Accuracy Chip + "Layers" Button
          Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.Top,
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Combined GPS Status / Auto-Center Chip over the map
                GnssStatusChip(
                  isFollowingUser = mapUiState.isCameraFollowingUser,
                  gnssStatusLabel = gnssAccuracyText(mapUiState.gnssAccuracy).resolve(),
                )
                if (mapUiState.isEarthEngineLayerShown) {
                  PoweredByEarthEngineBadge(
                    isMapOverlay = true,
                    modifier = Modifier.testTag(EARTH_ENGINE_MAP_BADGE_TAG),
                  )
                }
              }

              // Basemap preview card that opens the layers sheet
              BasemapPreviewCard(
                selectedBasemapType = mapUiState.selectedBasemapType,
                onClick = { mapActions.updateLayersSheetOpen(!mapUiState.isLayersSheetOpen) },
              )
            }

            // Selected Cluster Balloon detail callout when a Mapbox cluster balloon is tapped
            if (mapUiState.isMapClusteringActive && mapUiState.selectedCluster != null) {
              MapClusterBalloonsOverlay(uiState = mapUiState, actions = mapActions)
            }
          }
        }

        // Bottom overlay stack: Google Maps-style Horizontal Scale Widget in bottom-left,
        //    bottom-center data collection FAB, and optional "Recenter" button in bottom-right
        // Scale bar in bottom-left
        Box(
          modifier =
            Modifier.align(Alignment.BottomStart)
              .padding(start = 14.dp, bottom = peekHeight + 10.dp)
        ) {
          GoogleMapsScaleBarWidget(
            scaleSpec = mapUiState.mapScaleBarSpec,
            isSatellite = mapUiState.selectedBasemapType == BasemapType.SATELLITE,
          )
        }

        // Optional "Recenter" button in bottom-right when panned away from user
        if (!isSheetExpanded && !mapUiState.isCameraFollowingUser) {
          ExtendedFloatingActionButton(
            onClick = { mapActions.recenterMapOnUser() },
            icon = {
              Icon(
                imageVector = Icons.Outlined.MyLocation,
                contentDescription = stringResource(Res.string.mobile_recenter_content_description),
                modifier = Modifier.size(18.dp),
              )
            },
            text = {
              Text(
                text = stringResource(Res.string.mobile_recenter),
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
        // collapsed). Closed and archived surveys, and surveys where the user is only a viewer,
        // don't collect data.
        if (!isSheetExpanded && uiState.canCollectData) {
          Box(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = peekHeight + 10.dp),
            contentAlignment = Alignment.Center,
          ) {
            DataCollectionFormsFab(
              actions = dataCollectionActions,
              isDarkTheme = uiState.isDarkTheme,
            )
          }
        }
      }
    }

    if (isSelectingArea) {
      OfflineAreaSelectionOverlay(
        uiState = uiState.offlineAreaSelection,
        actions = offlineAreaActions,
        camera = mapCamera.camera,
        onClose = actions::closeOfflineAreaSelection,
        topInset = topInset,
      )
      return@BoxWithConstraints
    }

    // Floating Search Bar pinned to the top of the mobile screen above the map and bottom sheet
    MobileMapSearchBar(
      uiState = uiState,
      hasSelectedRecord =
        dataCollectionUiState.selectedEntity != null || selectedSubmission != null,
      isSheetExpanded = mapUiState.isEntityBottomSheetExpanded,
      actions = actions,
      listActions = listActions,
      dataCollectionActions = dataCollectionActions,
      modifier =
        Modifier.align(Alignment.TopCenter)
          .padding(start = 12.dp, top = topInset + 10.dp, end = 12.dp),
    )
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
        contentDescription =
          if (isFollowingUser) {
            stringResource(Res.string.mobile_gps_auto_center)
          } else {
            stringResource(Res.string.mobile_gps_panned)
          },
        tint =
          if (isFollowingUser) {
            MaterialTheme.colorScheme.inversePrimary
          } else {
            MaterialTheme.colorScheme.inverseOnSurface
          },
        modifier = Modifier.size(14.dp),
      )
      Text(
        text =
          if (isFollowingUser) {
            stringResource(Res.string.mobile_gps_status, gnssStatusLabel)
          } else {
            stringResource(Res.string.mobile_gps_panned)
          },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.inverseOnSurface,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}
