/*
 * IGNORE_COPYRIGHT: Ground is a Google-developed open-source project (The Ground Authors)
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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

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
 * - **Hamburger Navigation Drawer**: Options to Switch surveys, Manage offline maps,
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
@Composable
private fun MainSurveyTopAppBar(state: PrototypeAppState) {
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .background(Color(0xFF1E6F50))
        .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Hamburger Drawer Toggle Button
      Box(
        modifier =
          Modifier.size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF134B35))
            .clickable { state.updateDrawerOpen(true) },
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Default.Menu,
          contentDescription = "Open Navigation Drawer",
          tint = Color.White,
          modifier = Modifier.size(20.dp),
        )
      }

      Spacer(modifier = Modifier.width(10.dp))

      // Active Survey Title & Subtitle
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = state.activeSurvey.title,
          style =
            MaterialTheme.typography.labelLarge.copy(
              color = Color.White,
              fontWeight = FontWeight.Bold,
            ),
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
            text = "${state.activeSurvey.location} • ${state.visibleMapEntities.size} entities on map",
            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFC8E6C9)),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      Spacer(modifier = Modifier.width(8.dp))

      // Map | List Segmented Toggle
      Row(
        modifier =
          Modifier.clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF11422E))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        MainSurveyViewMode.entries.forEach { mode ->
          val isSelected =
            state.activeDrawerSubView == MainDrawerSubView.NONE && state.mainViewMode == mode
          val contentColor = if (isSelected) Color(0xFF003825) else Color.White
          Row(
            modifier =
              Modifier.clip(RoundedCornerShape(16.dp))
                .background(if (isSelected) Color(0xFF8BD6B1) else Color.Transparent)
                .clickable { state.setMainSurveyViewMode(mode) }
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector =
                if (mode == MainSurveyViewMode.MAP) {
                  Icons.Default.Map
                } else {
                  Icons.AutoMirrored.Filled.List
                },
              contentDescription = null,
              tint = contentColor,
              modifier = Modifier.size(13.dp),
            )
            Text(
              text = if (mode == MainSurveyViewMode.MAP) "Map" else "List",
              style =
                MaterialTheme.typography.labelSmall.copy(
                  color = contentColor,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                ),
            )
          }
        }
      }
    }
  }
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
@Composable
private fun SurveyMapView(state: PrototypeAppState) {
  val visibleEntities = state.visibleMapEntities
  val visibleSubGeometries = state.visibleSubmissionGeometries
  val selectedEntity = state.selectedEntity
  val selectedSubmission = state.selectedSubmission

  BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {
    val mapWidthPx = constraints.maxWidth.toFloat()
    val mapHeightPx = constraints.maxHeight.toFloat()

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
        Row(
          modifier =
            Modifier.clip(RoundedCornerShape(16.dp))
              .background(Color(0xE60E271C))
              .border(1.dp, Color(0xFF4CAF50), RoundedCornerShape(16.dp))
              .padding(horizontal = 11.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          Icon(
            imageVector = Icons.Default.SatelliteAlt,
            contentDescription = "GNSS Satellites & Accuracy",
            tint = Color(0xFF8BD6B1),
            modifier = Modifier.size(13.dp),
          )
          Text(
            text = "GNSS: ${state.gnssStatusChipLabel}",
            style =
              MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold,
              ),
          )
        }

        // "Layers" Button to control basemap (Map vs Satellite), offline tiles, entities, and submission geometries
        val totalSourcesCount = state.mapLayers.size + 1 // +1 for Offline Basemap
        val activeSourcesCount =
          state.visibleLayerIds.size + (if (state.isOfflineBasemapVisible) 1 else 0)
        val layersTint = if (state.isLayersSheetOpen) Color(0xFF003825) else Color.White
        Row(
          modifier =
            Modifier.clip(RoundedCornerShape(16.dp))
              .background(if (state.isLayersSheetOpen) Color(0xFF8BD6B1) else Color(0xEE133A29))
              .border(
                width = 1.dp,
                color = if (state.isLayersSheetOpen) Color.White else Color(0xFF8BD6B1),
                shape = RoundedCornerShape(16.dp),
              )
              .clickable { state.updateLayersSheetOpen(!state.isLayersSheetOpen) }
              .padding(horizontal = 12.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Layers,
            contentDescription = null,
            tint = layersTint,
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = "Layers ($activeSourcesCount/$totalSourcesCount)",
            style =
              MaterialTheme.typography.labelSmall.copy(
                color = layersTint,
                fontWeight = FontWeight.Bold,
              ),
          )
        }
      }

      // Compact Legend Strip: Solid Entity Outlines vs Dotted Form Submission Geometry Outlines + GPS Follow State
      Row(
        modifier =
          Modifier.clip(RoundedCornerShape(12.dp))
            .background(Color(0xCC0D2319))
            .border(1.dp, Color(0xFF2D5944), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "▢ Solid: Entity (${visibleEntities.size})",
          style =
            MaterialTheme.typography.labelSmall.copy(
              fontSize = 9.5.sp,
              color = Color(0xFFC8E6C9),
              fontWeight = FontWeight.SemiBold,
            ),
        )
        Text(
          text = "┈ Dotted: Submission (${visibleSubGeometries.size})",
          style =
            MaterialTheme.typography.labelSmall.copy(
              fontSize = 9.5.sp,
              color = Color(0xFF90CAF9),
              fontWeight = FontWeight.SemiBold,
            ),
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          Icon(
            imageVector = Icons.Default.MyLocation,
            contentDescription = null,
            tint = if (state.isCameraFollowingUser) Color(0xFF8BD6B1) else Color(0xFFFFCC80),
            modifier = Modifier.size(10.dp),
          )
          Text(
            text = if (state.isCameraFollowingUser) "GPS Auto-Center" else "Panned",
            style =
              MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.5.sp,
                color = if (state.isCameraFollowingUser) Color(0xFF8BD6B1) else Color(0xFFFFCC80),
                fontWeight = FontWeight.Bold,
              ),
          )
        }
      }
    }

    // 4. Floating "Layers" Popover Sheet (when Layers button is clicked)
    if (state.isLayersSheetOpen) {
      MapLayersControlSheet(
        state = state,
        modifier =
          Modifier.align(Alignment.TopEnd)
            .padding(top = 74.dp, end = 10.dp, start = 14.dp)
            .heightIn(max = 460.dp),
      )
    }

    // 5. Bottom Overlay Stack: Google Maps-style "Recenter" Pill Button (when map is dragged/panned)
    //    stacked cleanly above the Entity Bottom Sheet or Helper Hint Chip
    Column(
      modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
    ) {
      if (!state.isCameraFollowingUser) {
        Surface(
          modifier =
            Modifier.padding(start = 14.dp, bottom = 10.dp)
              .clip(RoundedCornerShape(24.dp))
              .clickable { state.recenterMapOnUser() },
          shape = RoundedCornerShape(24.dp),
          color = Color.White,
          shadowElevation = 6.dp,
        ) {
          Row(
            modifier =
              Modifier.border(1.5.dp, Color(0xFF1A73E8), RoundedCornerShape(24.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Default.MyLocation,
              contentDescription = "Recenter map on GPS location",
              tint = Color(0xFF1A73E8),
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = "Recenter",
              style =
                MaterialTheme.typography.labelLarge.copy(
                  color = Color(0xFF1A73E8),
                  fontWeight = FontWeight.Bold,
                ),
            )
          }
        }
      }

      if (selectedEntity != null) {
        EntityBottomSheetCard(
          entity = selectedEntity,
          state = state,
          modifier = Modifier.fillMaxWidth(),
        )
      } else {
        // Helper hint chip at the bottom of the map when no entity is selected
        Row(
          modifier =
            Modifier.align(Alignment.CenterHorizontally)
              .padding(14.dp)
              .clip(RoundedCornerShape(20.dp))
              .background(Color(0xE6133A29))
              .border(1.dp, Color(0xFF8BD6B1), RoundedCornerShape(20.dp))
              .padding(horizontal = 14.dp, vertical = 8.dp),
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
            text = "Drag map to pan • Tap any entity or dotted polygon to inspect",
            style =
              MaterialTheme.typography.labelSmall.copy(
                color = Color.White,
                fontWeight = FontWeight.Medium,
              ),
          )
        }
      }
    }
  }
}

/**
 * Popover dialog opened by the `"Layers"` button to select/toggle:
 * 1. **Basemap Type (`Map` vs `Satellite`)** & **Offline Basemap (`Mapbox Offline Tiles`)**
 * 2. **Geospatial Entity Layers (`LayerDef.entity_dataset_id`)** — rendered with solid outlines
 * 3. **Submission Geometry Layers (`LayerDef.form_geometry`)** — rendered with dotted polygon
 *    outlines corresponding to geometry questions/fields in the survey forms
 */
@Composable
private fun MapLayersControlSheet(
  state: PrototypeAppState,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
  ) {
    Column(
      modifier =
        Modifier.verticalScroll(rememberScrollState())
          .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(
            text = "Map Layers & Basemap (LayerDef)",
            style =
              MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827),
              ),
          )
          Text(
            text = "Select Map vs Satellite basemap and toggle entity & submission layers",
            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6B7280)),
          )
        }
        Box(
          modifier =
            Modifier.clip(CircleShape)
              .clickable { state.updateLayersSheetOpen(false) }
              .padding(4.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close Layers",
            tint = Color(0xFF374151),
            modifier = Modifier.size(16.dp),
          )
        }
      }

      HorizontalDivider(color = Color(0xFFE5E7EB))

      // SECTION 1: Basemap Type (Map vs Satellite) + Offline Basemap Toggle
      Text(
        text = "1. BASEMAP TYPE (MAP VS SATELLITE) & OFFLINE TILES",
        style =
          MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E6F50),
            letterSpacing = 0.5.sp,
          ),
      )
      // Map vs Satellite side-by-side cards (in Layers dialog only)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        BasemapType.entries.forEach { basemap ->
          val isSelected = state.selectedBasemapType == basemap
          val cardTitleColor = if (isSelected) Color(0xFF1B5E20) else Color(0xFF111827)
          Column(
            modifier =
              Modifier.weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isSelected) Color(0xFFE8F5E9) else Color(0xFFF9FAFB))
                .border(
                  width = if (isSelected) 1.5.dp else 1.dp,
                  color = if (isSelected) Color(0xFF1E6F50) else Color(0xFFE5E7EB),
                  shape = RoundedCornerShape(10.dp),
                )
                .clickable { state.selectBasemapType(basemap) }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
              Icon(
                imageVector =
                  if (basemap == BasemapType.NORMAL) {
                    Icons.Default.Map
                  } else {
                    Icons.Default.SatelliteAlt
                  },
                contentDescription = null,
                tint = cardTitleColor,
                modifier = Modifier.size(14.dp),
              )
              Text(
                text = basemap.label,
                style =
                  MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = cardTitleColor,
                  ),
              )
            }
            Text(
              text = basemap.description,
              style =
                MaterialTheme.typography.labelSmall.copy(
                  fontSize = 9.5.sp,
                  color = Color(0xFF6B7280),
                ),
            )
          }
        }
      }

      // Offline Basemap Tile Package Overlay Toggle
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (state.isOfflineBasemapVisible) Color(0xFFF0F7F3) else Color(0xFFF9FAFB))
            .border(
              width = 1.dp,
              color = if (state.isOfflineBasemapVisible) Color(0xFF2E7D32) else Color(0xFFE5E7EB),
              shape = RoundedCornerShape(10.dp),
            )
            .clickable { state.toggleOfflineBasemapVisibility() }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Checkbox(
          checked = state.isOfflineBasemapVisible,
          onCheckedChange = { state.toggleOfflineBasemapVisibility() },
          colors =
            CheckboxDefaults.colors(
              checkedColor = Color(0xFF1E6F50),
              checkmarkColor = Color.White,
            ),
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Offline Basemap Overlay (Nyeri Sector Tiles)",
            style =
              MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF111827),
              ),
          )
          Text(
            text = state.offlineBasemapStyle.tileDescription,
            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6B7280)),
          )
        }
      }

      HorizontalDivider(color = Color(0xFFE5E7EB))

      // SECTION 2: Geospatial Entity Dataset Layers (Solid Outlines)
      Text(
        text = "2. GEOSPATIAL ENTITY DATASETS (SOLID OUTLINES)",
        style =
          MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E6F50),
            letterSpacing = 0.5.sp,
          ),
      )
      state.entityDatasetLayers.forEach { layer ->
        val layerEntityCount = state.entities.count { it.layerId == layer.id }
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(if (layer.isVisible) Color(0xFFF3F8F5) else Color(0xFFF9FAFB))
              .clickable { state.toggleLayerVisibility(layer.id) }
              .padding(horizontal = 8.dp, vertical = 5.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Checkbox(
            checked = layer.isVisible,
            onCheckedChange = { state.toggleLayerVisibility(layer.id) },
            colors =
              CheckboxDefaults.colors(
                checkedColor = Color(layer.colorHex),
                checkmarkColor = Color.White,
              ),
          )
          // Solid layer color swatch
          Box(
            modifier =
              Modifier.size(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(layer.colorHex).copy(alpha = 0.25f))
                .border(2.dp, Color(layer.colorHex), RoundedCornerShape(4.dp))
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = layer.label,
              style =
                MaterialTheme.typography.labelMedium.copy(
                  fontWeight = FontWeight.SemiBold,
                  color = Color(0xFF111827),
                ),
            )
            Text(
              text = "${layer.geometryTypeLabel} (Solid) • $layerEntityCount entities",
              style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6B7280)),
            )
          }
        }
      }

      HorizontalDivider(color = Color(0xFFE5E7EB))

      // SECTION 3: Submission Geometry Layers (Form Geometry Questions — Dotted Polygon Outlines)
      Text(
        text = "3. FORM SUBMISSION GEOMETRIES (DOTTED POLYGONS)",
        style =
          MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1565C0),
            letterSpacing = 0.5.sp,
          ),
      )
      state.formGeometryLayers.forEach { layer ->
        val geomCount = state.submissionGeometries.count { it.layerId == layer.id }
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(if (layer.isVisible) Color(0xFFF0F7FF) else Color(0xFFF9FAFB))
              .clickable { state.toggleLayerVisibility(layer.id) }
              .padding(horizontal = 8.dp, vertical = 5.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Checkbox(
            checked = layer.isVisible,
            onCheckedChange = { state.toggleLayerVisibility(layer.id) },
            colors =
              CheckboxDefaults.colors(
                checkedColor = Color(layer.colorHex),
                checkmarkColor = Color.White,
              ),
          )
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
              style =
                MaterialTheme.typography.labelMedium.copy(
                  fontWeight = FontWeight.SemiBold,
                  color = Color(0xFF111827),
                ),
            )
            Text(
              text = "${layer.formId} :: ${layer.fieldPath} ($geomCount polygons)",
              style =
                MaterialTheme.typography.labelSmall.copy(
                  fontSize = 9.5.sp,
                  fontFamily = FontFamily.Monospace,
                  color = Color(0xFF4B5563),
                ),
            )
          }
        }
      }
    }
  }
}

/**
 * Bottom Sheet shown when a Geospatial Entity is clicked on the map:
 * - Shows main entity metadata (Label, Dataset, `GeoID`, Area/Perimeter, Properties).
 * - For `1:1` (`SubmissionModel.SINGLE_1_TO_1`) entity:submissions with data: shows the submission
 *   data directly inline in the card.
 * - For `1:N` (`SubmissionModel.MULTIPLE_1_TO_N`) entity:submissions: shows a list of submissions
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

  val isExpanded = state.isEntityBottomSheetExpanded

  Surface(
    modifier = modifier.heightIn(max = if (isExpanded) 415.dp else 72.dp),
    shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
    color = sheetBg,
    shadowElevation = 12.dp,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // Bottom Sheet Drag Handle (clickable to toggle collapsed / expanded state)
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .clickable { state.toggleEntityBottomSheetExpanded() }
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center,
      ) {
        Box(
          modifier =
            Modifier.width(42.dp)
              .height(4.dp)
              .clip(CircleShape)
              .background(Color(0xFFD1D5DB))
        )
      }

      // Entity Header: Reference Badge + Title + Expand/Collapse button + Close button
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          modifier =
            Modifier.weight(1f)
              .clickable { state.toggleEntityBottomSheetExpanded() },
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier =
              Modifier.size(26.dp)
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
              modifier = Modifier.size(14.dp),
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = entity.label,
              style =
                MaterialTheme.typography.titleSmall.copy(
                  fontWeight = FontWeight.Bold,
                  color = textColor,
                ),
              maxLines = 1,
            )
            Text(
              text = "${entity.datasetName} • ${entity.geometryTypeLabel} ($areaFormatted, $perimeterFormatted)",
              style =
                MaterialTheme.typography.labelSmall.copy(
                  color = textColor.copy(alpha = 0.68f),
                ),
              maxLines = 1,
            )
          }
        }

        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          // Expand / Collapse Bottom Sheet Button
          Box(
            modifier =
              Modifier.clip(CircleShape)
                .background(if (isDark) Color(0xFF2E3833) else Color(0xFFF3F4F6))
                .clickable { state.toggleEntityBottomSheetExpanded() }
                .padding(6.dp)
          ) {
            Icon(
              imageVector =
                if (isExpanded) {
                  Icons.Default.KeyboardArrowDown
                } else {
                  Icons.Default.KeyboardArrowUp
                },
              contentDescription = if (isExpanded) "Collapse Entity Sheet" else "Expand Entity Sheet",
              tint = textColor,
              modifier = Modifier.size(16.dp),
            )
          }

          // Close Bottom Sheet Button
          Box(
            modifier =
              Modifier.clip(CircleShape)
                .background(if (isDark) Color(0xFF2E3833) else Color(0xFFF3F4F6))
                .clickable { state.selectEntity(null) }
                .padding(6.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Close Entity Sheet",
              tint = textColor,
              modifier = Modifier.size(15.dp),
            )
          }
        }
      }

      if (isExpanded) {
        // Metadata & Share Actions Row: GeoID + 1:1/1:N Badge + QR Code Link + Share PDF Link
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // GeoID Badge (per `00-index.md` "On-Screen GeoID Confirmation & Entity Card Display")
        Box(
          modifier =
            Modifier.clip(RoundedCornerShape(8.dp))
              .background(Color(0xFFE8F5E9))
              .border(1.dp, Color(0xFF2E7D32), RoundedCornerShape(8.dp))
              .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
          Text(
            text = "GeoID: ${entity.geoId}",
            style =
              MaterialTheme.typography.labelSmall.copy(
                color = Color(0xFF1B5E20),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
              ),
          )
        }

        // 1:1 vs 1:N Submission Model Badge
        val isOneToOne = entity.submissionModel == SubmissionModel.SINGLE_1_TO_1
        Box(
          modifier =
            Modifier.clip(RoundedCornerShape(8.dp))
              .background(if (isOneToOne) Color(0xFFEFF6FF) else Color(0xFFFFF7ED))
              .border(
                width = 1.dp,
                color = if (isOneToOne) Color(0xFF1D4ED8) else Color(0xFFC2410C),
                shape = RoundedCornerShape(8.dp),
              )
              .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
          Text(
            text = entity.submissionModel.badgeLabel,
            style =
              MaterialTheme.typography.labelSmall.copy(
                color = if (isOneToOne) Color(0xFF1E40AF) else Color(0xFF9A3412),
                fontWeight = FontWeight.Bold,
              ),
          )
        }

        // QR Code Link for Geospatial Entity
        Row(
          modifier =
            Modifier.clip(RoundedCornerShape(8.dp))
              .background(if (isDark) Color(0xFF26332D) else Color(0xFFEFF6F2))
              .border(1.dp, Color(0xFF1E6F50), RoundedCornerShape(8.dp))
              .clickable { state.openEntityQrCode(entity.id) }
              .padding(horizontal = 8.dp, vertical = 3.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            imageVector = Icons.Default.QrCode,
            contentDescription = "Entity QR Code",
            tint = if (isDark) Color(0xFF8BD6B1) else Color(0xFF1E6F50),
            modifier = Modifier.size(12.dp),
          )
          Text(
            text = "QR Code",
            style =
              MaterialTheme.typography.labelSmall.copy(
                color = if (isDark) Color(0xFF8BD6B1) else Color(0xFF1E6F50),
                fontWeight = FontWeight.Bold,
              ),
          )
        }

        // Share PDF Link for Geospatial Entity
        Row(
          modifier =
            Modifier.clip(RoundedCornerShape(8.dp))
              .background(if (isDark) Color(0xFF26332D) else Color(0xFFEFF6F2))
              .border(1.dp, Color(0xFF1E6F50), RoundedCornerShape(8.dp))
              .clickable { state.shareEntityPdf(entity.id) }
              .padding(horizontal = 8.dp, vertical = 3.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Share,
            contentDescription = "Share Entity PDF",
            tint = if (isDark) Color(0xFF8BD6B1) else Color(0xFF1E6F50),
            modifier = Modifier.size(12.dp),
          )
          Text(
            text = "Share PDF",
            style =
              MaterialTheme.typography.labelSmall.copy(
                color = if (isDark) Color(0xFF8BD6B1) else Color(0xFF1E6F50),
                fontWeight = FontWeight.Bold,
              ),
          )
        }
      }

      // Organizer-defined Form Action Buttons for this Entity Type (`form.targetDatasetId == entity.datasetId`)
      val entityForms = state.formsForEntity(entity)
      if (entityForms.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "DATA COLLECTION FORMS FOR THIS ENTITY",
            style =
              MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color(0xFF8BD6B1) else Color(0xFF1E6F50),
                letterSpacing = 0.5.sp,
              ),
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
                shape = RoundedCornerShape(10.dp),
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1E6F50),
                    contentColor = Color.White,
                    disabledContainerColor =
                      if (isDark) Color(0xFF2A332E) else Color(0xFFE5E7EB),
                    disabledContentColor =
                      if (isDark) Color(0xFF6B7280) else Color(0xFF9CA3AF),
                  ),
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
                      "${form.ctaLabel} (1:1 Completed)"
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
              style =
                MaterialTheme.typography.labelSmall.copy(
                  color = if (isDark) Color(0xFF8BD6B1) else Color(0xFF1E6F50),
                  fontWeight = FontWeight.SemiBold,
                ),
            )
          }
        }
      }

      HorizontalDivider(color = textColor.copy(alpha = 0.1f))

      // Scrollable Body inside Bottom Sheet: Entity Properties + (1:1 Inline Data OR 1:N List / Detail)
      Column(
        modifier =
          Modifier.weight(1f, fill = false)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        // Baseline Entity Attributes (`EntityRecord.properties`)
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          entity.properties.forEach { (key, value) ->
            Column(
              modifier =
                Modifier.clip(RoundedCornerShape(8.dp))
                  .background(if (isDark) Color(0xFF28312D) else Color(0xFFF5F8F6))
                  .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
              Text(
                text = key,
                style =
                  MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    color = textColor.copy(alpha = 0.6f),
                  ),
              )
              Text(
                text = value,
                style =
                  MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                  ),
              )
            }
          }
        }

        // --- SUBMISSION DISPLAY LOGIC ---
        if (entity.submissionModel == SubmissionModel.SINGLE_1_TO_1) {
          // 1:1 Entity:Submission -> Directly show the submission data inline on the card!
          val sub = entity.submissions.firstOrNull()
          if (sub != null) {
            OneToOneInlineSubmissionCard(
              submission = sub,
              isDark = isDark,
              textColor = textColor,
              onSharePdf = { state.shareSubmissionPdf(sub.id) },
            )
          } else {
            Text(
              text = "No baseline submission recorded yet for this 1:1 entity.",
              style = MaterialTheme.typography.bodySmall.copy(color = textColor.copy(alpha = 0.7f)),
            )
          }
        } else {
          // 1:N Entity:Submissions -> Either show Full Submission Details (if a row was clicked)
          // OR show the chronological list of submissions (data collector, timestamp)!
          if (selectedSubmission != null && selectedSubmission.entityId == entity.id) {
            SubmissionFullDetailsCard(
              submission = selectedSubmission,
              isDark = isDark,
              textColor = textColor,
              backLabel = "Back to all ${entity.submissions.size} submissions",
              onBack = { state.selectSubmissionDetail(null) },
              onSharePdf = { state.shareSubmissionPdf(selectedSubmission.id) },
            )
          } else {
            OneToManySubmissionsListSection(
              entity = entity,
              isDark = isDark,
              textColor = textColor,
              onSelectSubmission = { state.selectSubmissionDetail(it.id) },
              onShareSubmissionPdf = { state.shareSubmissionPdf(it.id) },
            )
          }
        }
      }
      }
    }
  }
}

/**
 * Renders inline submission data for a `1:1` (`SubmissionModel.SINGLE_1_TO_1`) entity with data.
 */
@Composable
private fun OneToOneInlineSubmissionCard(
  submission: SubmissionPreviewItem,
  isDark: Boolean,
  textColor: Color,
  onSharePdf: () -> Unit,
) {
  Card(
    modifier =
      Modifier.fillMaxWidth()
        .border(1.dp, Color(0xFF2E7D32), RoundedCornerShape(12.dp)),
    shape = RoundedCornerShape(12.dp),
    colors =
      CardDefaults.cardColors(
        containerColor = if (isDark) Color(0xFF192820) else Color(0xFFF2F9F4)
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
        val headerTint = if (isDark) Color(0xFF8BD6B1) else Color(0xFF1B5E20)
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
          modifier = Modifier.weight(1f),
        ) {
          Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = headerTint,
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = "1:1 Submission (${submission.formTitle})",
            style =
              MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = headerTint,
              ),
          )
        }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Row(
            modifier =
              Modifier.clip(RoundedCornerShape(6.dp))
                .background(if (isDark) Color(0xFF26332D) else Color(0xFFE8F5E9))
                .border(1.dp, Color(0xFF1E6F50), RoundedCornerShape(6.dp))
                .clickable { onSharePdf() }
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
          ) {
            Icon(
              imageVector = Icons.Default.Share,
              contentDescription = "Share Submission PDF",
              tint = headerTint,
              modifier = Modifier.size(11.dp),
            )
            Text(
              text = "Share PDF",
              style =
                MaterialTheme.typography.labelSmall.copy(
                  color = headerTint,
                  fontWeight = FontWeight.Bold,
                ),
            )
          }
          Text(
            text = submission.formVersion,
            style =
              MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                color = textColor.copy(alpha = 0.6f),
              ),
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
          tint = textColor.copy(alpha = 0.78f),
          modifier = Modifier.size(13.dp),
        )
        Text(
          text = "Collector: ${submission.collectorName} •",
          style =
            MaterialTheme.typography.labelSmall.copy(
              color = textColor.copy(alpha = 0.78f),
              fontWeight = FontWeight.Medium,
            ),
        )
        Icon(
          imageVector = Icons.Default.Schedule,
          contentDescription = null,
          tint = textColor.copy(alpha = 0.78f),
          modifier = Modifier.size(12.dp),
        )
        Text(
          text = submission.timestamp,
          style =
            MaterialTheme.typography.labelSmall.copy(
              color = textColor.copy(alpha = 0.78f),
              fontWeight = FontWeight.Medium,
            ),
        )
      }

      HorizontalDivider(color = textColor.copy(alpha = 0.1f))

      submission.fields.forEach { field ->
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.Top,
        ) {
          Text(
            text = field.questionLabel,
            style =
              MaterialTheme.typography.labelSmall.copy(
                color = textColor.copy(alpha = 0.7f),
              ),
            modifier = Modifier.weight(0.48f),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = field.answerValue,
            style =
              MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = textColor,
              ),
            modifier = Modifier.weight(0.52f),
          )
        }
      }
    }
  }
}

/**
 * For `1:N` (`SubmissionModel.MULTIPLE_1_TO_N`) entities, shows a chronological list of
 * submissions (data collector, timestamp) which can be clicked to inspect full submission details.
 */
@Composable
private fun OneToManySubmissionsListSection(
  entity: GeospatialEntityItem,
  isDark: Boolean,
  textColor: Color,
  onSelectSubmission: (SubmissionPreviewItem) -> Unit,
  onShareSubmissionPdf: (SubmissionPreviewItem) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "SUBMISSIONS HISTORY (${entity.submissions.size} RECORDED • TAP TO VIEW)",
        style =
          MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            color = if (isDark) Color(0xFF8BD6B1) else Color(0xFF1E6F50),
            letterSpacing = 0.4.sp,
          ),
      )
    }

    entity.submissions.forEachIndexed { index, sub ->
      Card(
        modifier =
          Modifier.fillMaxWidth()
            .border(
              width = 1.dp,
              color = if (isDark) Color(0xFF374151) else Color(0xFFD1E2D9),
              shape = RoundedCornerShape(10.dp),
            )
            .clickable { onSelectSubmission(sub) },
        shape = RoundedCornerShape(10.dp),
        colors =
          CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF252E2A) else Color(0xFFF9FBFA)
          ),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(5.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(13.dp),
              )
              Text(
                text = sub.collectorName,
                style =
                  MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                  ),
              )
              if (index == 0) {
                Box(
                  modifier =
                    Modifier.clip(RoundedCornerShape(6.dp))
                      .background(Color(0xFFE8F5E9))
                      .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                  Text(
                    text = "LATEST",
                    style =
                      MaterialTheme.typography.labelSmall.copy(
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B5E20),
                      ),
                  )
                }
              }
            }
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = textColor.copy(alpha = 0.68f),
                modifier = Modifier.size(11.dp),
              )
              Text(
                text = "${sub.timestamp} • ${sub.formTitle} (${sub.formVersion})",
                style =
                  MaterialTheme.typography.labelSmall.copy(
                    color = textColor.copy(alpha = 0.68f),
                  ),
              )
            }
          }

          val detailsColor = if (isDark) Color(0xFF8BD6B1) else Color(0xFF1E6F50)
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Row(
              modifier =
                Modifier.clip(RoundedCornerShape(6.dp))
                  .background(if (isDark) Color(0xFF1E2824) else Color(0xFFEFF6F2))
                  .border(1.dp, detailsColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                  .clickable { onShareSubmissionPdf(sub) }
                  .padding(horizontal = 6.dp, vertical = 2.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "Share Submission PDF",
                tint = detailsColor,
                modifier = Modifier.size(11.dp),
              )
              Text(
                text = "Share",
                style =
                  MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = detailsColor,
                  ),
              )
            }
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
              Text(
                text = "Details",
                style =
                  MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = detailsColor,
                  ),
              )
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = detailsColor,
                modifier = Modifier.size(12.dp),
              )
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
  isDark: Boolean,
  textColor: Color,
  backLabel: String,
  onBack: () -> Unit,
  onSharePdf: () -> Unit,
) {
  Card(
    modifier =
      Modifier.fillMaxWidth()
        .border(1.5.dp, Color(0xFF1E6F50), RoundedCornerShape(12.dp)),
    shape = RoundedCornerShape(12.dp),
    colors =
      CardDefaults.cardColors(
        containerColor = if (isDark) Color(0xFF1C2722) else Color(0xFFF4FAF6)
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
        Row(
          modifier =
            Modifier.clip(RoundedCornerShape(8.dp))
              .background(Color(0xFF1E6F50))
              .clickable { onBack() }
              .padding(horizontal = 8.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(13.dp),
          )
          Text(
            text = backLabel,
            style =
              MaterialTheme.typography.labelSmall.copy(
                color = Color.White,
                fontWeight = FontWeight.Bold,
              ),
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Row(
            modifier =
              Modifier.clip(RoundedCornerShape(8.dp))
                .background(if (isDark) Color(0xFF26332D) else Color(0xFFE8F5E9))
                .border(1.dp, Color(0xFF1E6F50), RoundedCornerShape(8.dp))
                .clickable { onSharePdf() }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = Icons.Default.Share,
              contentDescription = "Share Submission PDF",
              tint = if (isDark) Color(0xFF8BD6B1) else Color(0xFF1E6F50),
              modifier = Modifier.size(12.dp),
            )
            Text(
              text = "Share PDF",
              style =
                MaterialTheme.typography.labelSmall.copy(
                  color = if (isDark) Color(0xFF8BD6B1) else Color(0xFF1E6F50),
                  fontWeight = FontWeight.Bold,
                ),
            )
          }

          Text(
            text = "Schema: ${submission.formVersion}",
            style =
              MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                color = textColor.copy(alpha = 0.65f),
              ),
          )
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = submission.formTitle,
          style =
            MaterialTheme.typography.titleSmall.copy(
              fontWeight = FontWeight.Bold,
              color = textColor,
            ),
        )
        Text(
          text = "Entity: ${submission.entityLabel}",
          style =
            MaterialTheme.typography.labelSmall.copy(
              fontWeight = FontWeight.SemiBold,
              color = if (isDark) Color(0xFF8BD6B1) else Color(0xFF1E6F50),
            ),
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = textColor.copy(alpha = 0.8f),
            modifier = Modifier.size(12.dp),
          )
          Text(
            text = "Data Collector: ${submission.collectorName} (${submission.collectorEmail})",
            style = MaterialTheme.typography.labelSmall.copy(color = textColor.copy(alpha = 0.8f)),
          )
        }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Schedule,
            contentDescription = null,
            tint = textColor.copy(alpha = 0.8f),
            modifier = Modifier.size(12.dp),
          )
          Text(
            text = "Collected: ${submission.timestamp}",
            style = MaterialTheme.typography.labelSmall.copy(color = textColor.copy(alpha = 0.8f)),
          )
        }
      }

      HorizontalDivider(color = textColor.copy(alpha = 0.12f))

      submission.fields.forEach { field ->
        Column(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(if (isDark) Color(0xFF25302B) else Color.White)
              .padding(horizontal = 10.dp, vertical = 6.dp),
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          Text(
            text = field.questionLabel,
            style =
              MaterialTheme.typography.labelSmall.copy(
                color = textColor.copy(alpha = 0.65f),
              ),
          )
          Text(
            text = field.answerValue,
            style =
              MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = textColor,
              ),
          )
        }
      }
    }
  }
}

/**
 * Searchable List View of the active survey (`MainSurveyViewMode.LIST`) displaying Geospatial
 * Entities and Submissions grouped by Form.
 */
@Composable
private fun SurveyListView(state: PrototypeAppState) {
  val isDark = state.isDarkTheme
  val surfaceColor = MaterialTheme.colorScheme.surface
  val textColor = MaterialTheme.colorScheme.onSurface
  val selectedSubmission = state.selectedSubmission

  Column(modifier = Modifier.fillMaxSize().background(surfaceColor)) {
    // Search Bar + Filter Tabs Header
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .background(if (isDark) Color(0xFF222A26) else Color(0xFFEBF3EE))
          .padding(horizontal = 14.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedTextField(
        value = state.listSearchQuery,
        onValueChange = { state.updateListSearchQuery(it) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = {
          Text(
            text = "Search entities, forms, or submissions...",
            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF6B7280)),
          )
        },
        leadingIcon = {
          Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = Color(0xFF6B7280),
            modifier = Modifier.size(18.dp),
          )
        },
        trailingIcon = {
          if (state.listSearchQuery.isNotEmpty()) {
            Box(
              modifier =
                Modifier.clip(CircleShape)
                  .clickable { state.clearListSearchQuery() }
                  .padding(6.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Clear Search",
                tint = Color(0xFF374151),
                modifier = Modifier.size(16.dp),
              )
            }
          }
        },
        shape = RoundedCornerShape(22.dp),
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedTextColor = Color(0xFF111827),
            unfocusedTextColor = Color(0xFF111827),
            focusedBorderColor = Color(0xFF1E6F50),
            unfocusedBorderColor = Color.Transparent,
          ),
      )

      // Category Filter Tabs: All | Entities | Submissions (Grouped by Form)
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
          Box(
            modifier =
              Modifier.clip(RoundedCornerShape(16.dp))
                .background(if (isSelected) Color(0xFF1E6F50) else Color.White)
                .border(
                  width = 1.dp,
                  color = if (isSelected) Color(0xFF1E6F50) else Color(0xFFD1D5DB),
                  shape = RoundedCornerShape(16.dp),
                )
                .clickable { state.selectListFilterTab(tab) }
                .padding(horizontal = 10.dp, vertical = 5.dp)
          ) {
            Text(
              text = "${tab.label} ($countLabel)",
              style =
                MaterialTheme.typography.labelSmall.copy(
                  color = if (isSelected) Color.White else Color(0xFF374151),
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                ),
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
          isDark = isDark,
          textColor = textColor,
          backLabel = "Back to Searchable List",
          onBack = { state.selectSubmissionDetail(null) },
          onSharePdf = { state.shareSubmissionPdf(selectedSubmission.id) },
        )
      }
      return
    }

    // Scrollable Searchable List of Geospatial Entities and Submissions (grouped by Form)
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
      // 1. GEOSPATIAL ENTITIES SECTION
      if (matchedEntities.isNotEmpty()) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = Color(0xFF1E6F50),
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = "GEOSPATIAL ENTITIES (${matchedEntities.size})",
            style =
              MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E6F50),
                letterSpacing = 0.6.sp,
              ),
          )
        }
        matchedEntities.forEach { entity ->
          Card(
            modifier =
              Modifier.fillMaxWidth()
                .border(1.dp, Color(0xFFDDE5E0), RoundedCornerShape(12.dp))
                .clickable {
                  state.selectEntity(entity.id)
                  state.setMainSurveyViewMode(MainSurveyViewMode.MAP)
                },
            shape = RoundedCornerShape(12.dp),
            colors =
              CardDefaults.cardColors(
                containerColor = if (isDark) Color(0xFF232926) else Color.White
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
                    style =
                      MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                      ),
                  )
                  Text(
                    text = "GeoID: ${entity.geoId} • ${entity.submissionModel.badgeLabel}",
                    style =
                      MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF1E6F50),
                        fontWeight = FontWeight.SemiBold,
                      ),
                  )
                  Text(
                    text = "${entity.datasetName} • ${entity.submissions.size} submission(s)",
                    style =
                      MaterialTheme.typography.labelSmall.copy(
                        color = textColor.copy(alpha = 0.68f),
                      ),
                  )
                }
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                  Text(
                    text = "View on Map",
                    style =
                      MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E6F50),
                      ),
                  )
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color(0xFF1E6F50),
                    modifier = Modifier.size(12.dp),
                  )
                }
              }

              // Quick QR Code & Share PDF actions for Geospatial Entity in List View
              Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Row(
                  modifier =
                    Modifier.clip(RoundedCornerShape(6.dp))
                      .background(if (isDark) Color(0xFF26332D) else Color(0xFFEFF6F2))
                      .border(1.dp, Color(0xFF1E6F50), RoundedCornerShape(6.dp))
                      .clickable { state.openEntityQrCode(entity.id) }
                      .padding(horizontal = 7.dp, vertical = 2.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Icon(
                    imageVector = Icons.Default.QrCode,
                    contentDescription = "Entity QR Code",
                    tint = Color(0xFF1E6F50),
                    modifier = Modifier.size(11.dp),
                  )
                  Text(
                    text = "QR Code",
                    style =
                      MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF1E6F50),
                        fontWeight = FontWeight.Bold,
                      ),
                  )
                }
                Row(
                  modifier =
                    Modifier.clip(RoundedCornerShape(6.dp))
                      .background(if (isDark) Color(0xFF26332D) else Color(0xFFEFF6F2))
                      .border(1.dp, Color(0xFF1E6F50), RoundedCornerShape(6.dp))
                      .clickable { state.shareEntityPdf(entity.id) }
                      .padding(horizontal = 7.dp, vertical = 2.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share Entity PDF",
                    tint = Color(0xFF1E6F50),
                    modifier = Modifier.size(11.dp),
                  )
                  Text(
                    text = "Share PDF",
                    style =
                      MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF1E6F50),
                        fontWeight = FontWeight.Bold,
                      ),
                  )
                }
              }
            }
          }
        }
      }

      // 2. SUBMISSIONS GROUPED BY FORM SECTION
      if (groupedSubmissions.isNotEmpty()) {
        val totalMatchedSubmissions = groupedSubmissions.sumOf { it.submissions.size }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Description,
            contentDescription = null,
            tint = Color(0xFF1E6F50),
            modifier = Modifier.size(14.dp),
          )
          Text(
            text =
              "SUBMISSIONS GROUPED BY FORM ($totalMatchedSubmissions IN ${groupedSubmissions.size} FORMS)",
            style =
              MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E6F50),
                letterSpacing = 0.6.sp,
              ),
          )
        }

        groupedSubmissions.forEach { group ->
          val form = group.form
          Column(
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (isDark) Color(0xFF1E2622) else Color(0xFFF3F8F5))
                .border(1.dp, Color(0xFFC8E0D4), RoundedCornerShape(14.dp))
                .padding(10.dp),
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
                    tint = Color(0xFF1E6F50),
                    modifier = Modifier.size(14.dp),
                  )
                  Text(
                    text = form.title,
                    style =
                      MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                      ),
                  )
                }
                Text(
                  text =
                    "Action: \"${form.ctaLabel}\" • ${form.submissionModel.badgeLabel} • ${form.version}",
                  style =
                    MaterialTheme.typography.labelSmall.copy(
                      color = Color(0xFF1E6F50),
                      fontWeight = FontWeight.SemiBold,
                    ),
                )
              }

              Box(
                modifier =
                  Modifier.clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E6F50))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
              ) {
                Text(
                  text = "${group.submissions.size} submitted",
                  style =
                    MaterialTheme.typography.labelSmall.copy(
                      color = Color.White,
                      fontWeight = FontWeight.Bold,
                    ),
                )
              }
            }

            // Submissions belonging to this Form group
            group.submissions.forEach { sub ->
              Card(
                modifier =
                  Modifier.fillMaxWidth()
                    .border(1.dp, Color(0xFFDDE5E0), RoundedCornerShape(10.dp))
                    .clickable { state.selectSubmissionDetail(sub.id) },
                shape = RoundedCornerShape(10.dp),
                colors =
                  CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF252E2A) else Color.White
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
                    Text(
                      text = "Entity: ${sub.entityLabel}",
                      style =
                        MaterialTheme.typography.labelLarge.copy(
                          fontWeight = FontWeight.Bold,
                          color = textColor,
                        ),
                    )
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                      Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = textColor.copy(alpha = 0.72f),
                        modifier = Modifier.size(12.dp),
                      )
                      Text(
                        text = "${sub.collectorName} •",
                        style =
                          MaterialTheme.typography.labelSmall.copy(
                            color = textColor.copy(alpha = 0.72f),
                          ),
                      )
                      Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = textColor.copy(alpha = 0.72f),
                        modifier = Modifier.size(11.dp),
                      )
                      Text(
                        text = sub.timestamp,
                        style =
                          MaterialTheme.typography.labelSmall.copy(
                            color = textColor.copy(alpha = 0.72f),
                          ),
                      )
                    }
                  }
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Row(
                      modifier =
                        Modifier.clip(RoundedCornerShape(6.dp))
                          .background(if (isDark) Color(0xFF26332D) else Color(0xFFEFF6F2))
                          .border(1.dp, Color(0xFF1E6F50), RoundedCornerShape(6.dp))
                          .clickable { state.shareSubmissionPdf(sub.id) }
                          .padding(horizontal = 6.dp, vertical = 2.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                      Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share Submission PDF",
                        tint = Color(0xFF1E6F50),
                        modifier = Modifier.size(11.dp),
                      )
                      Text(
                        text = "Share PDF",
                        style =
                          MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF1E6F50),
                            fontWeight = FontWeight.Bold,
                          ),
                      )
                    }
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                      Text(
                        text = "Inspect",
                        style =
                          MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E6F50),
                          ),
                      )
                      Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color(0xFF1E6F50),
                        modifier = Modifier.size(12.dp),
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
            text = "No entities or submissions match \"${state.listSearchQuery}\".",
            style = MaterialTheme.typography.bodySmall.copy(color = textColor.copy(alpha = 0.7f)),
          )
        }
      }
    }
  }
}

/**
 * Hamburger Navigation Drawer overlay providing options to:
 * 1. Switch surveys (downloaded surveys screen with button to browse & download more surveys)
 * 2. Manage offline maps
 * 3. Change settings
 * 4. View Terms of Service
 * 5. Sign out
 */
@Composable
private fun MainSurveyNavigationDrawerOverlay(state: PrototypeAppState) {
  val isDark = state.isDarkTheme
  val drawerBg = if (isDark) Color(0xFF1A211E) else Color.White
  val textColor = if (isDark) Color.White else Color(0xFF111827)

  Box(modifier = Modifier.fillMaxSize()) {
    // Scrim backdrop
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(Color.Black.copy(alpha = 0.45f))
          .clickable { state.updateDrawerOpen(false) }
    )

    // Slide-over Drawer Panel
    Surface(
      modifier = Modifier.fillMaxHeight().width(300.dp),
      color = drawerBg,
      shadowElevation = 16.dp,
    ) {
      Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.fillMaxWidth()) {
          // User Profile & Organization Header
          Column(
            modifier =
              Modifier.fillMaxWidth()
                .background(Color(0xFF144532))
                .padding(horizontal = 18.dp, vertical = 20.dp),
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
                  style =
                    MaterialTheme.typography.titleMedium.copy(
                      color = Color(0xFF003825),
                      fontWeight = FontWeight.ExtraBold,
                    ),
                )
              }
              Box(
                modifier =
                  Modifier.clip(CircleShape)
                    .background(Color(0xFF1E6F50))
                    .clickable { state.updateDrawerOpen(false) }
                    .padding(6.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = "Close Drawer",
                  tint = Color.White,
                  modifier = Modifier.size(16.dp),
                )
              }
            }

            Text(
              text = state.signedInUserName,
              style =
                MaterialTheme.typography.titleMedium.copy(
                  color = Color.White,
                  fontWeight = FontWeight.Bold,
                ),
            )
            Text(
              text = state.signedInUserEmail,
              style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFC8E6C9)),
            )
            Text(
              text = state.signedInOrganization,
              style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF8BD6B1)),
            )
          }

          // Active Survey Summary Banner
          Column(
            modifier =
              Modifier.fillMaxWidth()
                .background(if (isDark) Color(0xFF222C27) else Color(0xFFEFF6F2))
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Text(
              text = "ACTIVE SURVEY",
              style =
                MaterialTheme.typography.labelSmall.copy(
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF1E6F50),
                ),
            )
            Text(
              text = state.activeSurvey.title,
              style =
                MaterialTheme.typography.labelMedium.copy(
                  fontWeight = FontWeight.Bold,
                  color = textColor,
                ),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }

          Spacer(modifier = Modifier.height(8.dp))

          // Navigation Drawer Options
          DrawerMenuItem(
            icon = Icons.Default.SwapHoriz,
            title = "Switch surveys",
            subtitle = "${state.downloadedSurveyCount} downloaded on device",
            textColor = textColor,
            onClick = { state.drawerSwitchSurveys() },
          )
          DrawerMenuItem(
            icon = Icons.Default.Map,
            title = "Manage offline maps",
            subtitle = "Mapbox vector & satellite raster tile cache",
            textColor = textColor,
            onClick = { state.drawerManageOfflineMaps() },
          )
          DrawerMenuItem(
            icon = Icons.Default.Settings,
            title = "Settings",
            subtitle = "Units (${state.unitSystem.areaUnit}), language & media cache",
            textColor = textColor,
            onClick = { state.drawerOpenSettings() },
          )
          DrawerMenuItem(
            icon = Icons.Default.Description,
            title = "Terms of Service",
            subtitle = "Platform data governance & privacy terms",
            textColor = textColor,
            onClick = { state.drawerViewTermsOfService() },
          )

          HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = textColor.copy(alpha = 0.1f),
          )

          DrawerMenuItem(
            icon = Icons.AutoMirrored.Filled.Logout,
            title = "Sign out",
            subtitle = "Disconnect ${state.signedInUserEmail}",
            textColor = Color(0xFFD32F2F),
            onClick = { state.drawerSignOut() },
          )
        }

        // Footer version note
        Text(
          text = "Open Foris Ground 2.0 • Offline-First Core",
          style =
            MaterialTheme.typography.labelSmall.copy(
              color = textColor.copy(alpha = 0.5f),
            ),
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
  textColor: Color,
  onClick: () -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clickable { onClick() }
        .padding(horizontal = 18.dp, vertical = 11.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = title,
      tint = textColor.copy(alpha = 0.85f),
      modifier = Modifier.size(20.dp),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style =
          MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.SemiBold,
            color = textColor,
          ),
      )
      Text(
        text = subtitle,
        style =
          MaterialTheme.typography.labelSmall.copy(
            color = textColor.copy(alpha = 0.65f),
          ),
      )
    }
  }
}

/**
 * Separate screen accessible from the `"Switch surveys"` navigation drawer option showing ONLY the
 * surveys which have already been downloaded to the device, plus a primary action button
 * (`"Browse & download more surveys"`) which navigates to the full `Download surveys` screen.
 */
@Composable
private fun SwitchDownloadedSurveysSubScreen(state: PrototypeAppState) {
  val textColor = MaterialTheme.colorScheme.onSurface
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
          tint = Color(0xFF1E6F50),
          modifier = Modifier.size(22.dp),
        )
        Column {
          Text(
            text = "Downloaded Surveys",
            style =
              MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = textColor,
              ),
          )
          Text(
            text = "${downloadedList.size} offline-ready survey(s) on this device",
            style =
              MaterialTheme.typography.labelSmall.copy(
                color = textColor.copy(alpha = 0.68f),
              ),
          )
        }
      }
      OutlinedButton(
        onClick = { state.closeDrawerSubView() },
        shape = RoundedCornerShape(10.dp),
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
      Card(
        modifier =
          Modifier.fillMaxWidth()
            .border(
              width = if (isActive) 2.dp else 1.dp,
              color = if (isActive) Color(0xFF1E6F50) else Color(0xFFDDE5E0),
              shape = RoundedCornerShape(14.dp),
            )
            .clickable { state.openSurvey(survey.id) },
        shape = RoundedCornerShape(14.dp),
        colors =
          CardDefaults.cardColors(
            containerColor = if (isActive) Color(0xFFEFF6F2) else Color.White
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
                style =
                  MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827),
                  ),
              )
              if (isActive) {
                Box(
                  modifier =
                    Modifier.clip(RoundedCornerShape(6.dp))
                      .background(Color(0xFF1E6F50))
                      .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                  Text(
                    text = "ACTIVE",
                    style =
                      MaterialTheme.typography.labelSmall.copy(
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                      ),
                  )
                }
              }
            }
            Text(
              text = "${survey.location} • ${survey.entityCount} entities • ${survey.offlineSizeLabel}",
              style =
                MaterialTheme.typography.labelSmall.copy(
                  color = Color(0xFF1E6F50),
                  fontWeight = FontWeight.SemiBold,
                ),
            )
            Text(
              text = survey.description,
              style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF4B5563)),
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Spacer(modifier = Modifier.width(8.dp))
          Button(
            onClick = { state.openSurvey(survey.id) },
            colors =
              ButtonDefaults.buttonColors(
                containerColor = if (isActive) Color(0xFF144532) else Color(0xFF1E6F50),
                contentColor = Color.White,
              ),
            shape = RoundedCornerShape(10.dp),
          ) {
            Text(
              text = if (isActive) "Open" else "Switch",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            )
          }
        }
      }
    }

    // Primary Action Button to navigate to the full Download Surveys screen
    Button(
      onClick = { state.openDownloadMoreSurveysScreen() },
      modifier = Modifier.fillMaxWidth().height(48.dp),
      shape = RoundedCornerShape(12.dp),
      colors =
        ButtonDefaults.buttonColors(
          containerColor = Color(0xFF1E6F50),
          contentColor = Color.White,
        ),
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
 * Modal dialog displaying a scannable QR Code for a Geospatial Entity (`Icons.Default.QrCode`),
 * allowing offline field verification and rapid lookup of the entity's `GeoID`.
 */
@Composable
private fun EntityQrCodeModalDialog(
  state: PrototypeAppState,
  entity: GeospatialEntityItem,
) {
  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(Color.Black.copy(alpha = 0.5f))
        .clickable { state.closeEntityQrCode() },
    contentAlignment = Alignment.Center,
  ) {
    Card(
      modifier =
        Modifier.width(310.dp)
          .clickable(enabled = false) {},
      shape = RoundedCornerShape(18.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White),
      elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
    ) {
      Column(
        modifier = Modifier.padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
              imageVector = Icons.Default.QrCode,
              contentDescription = null,
              tint = Color(0xFF1E6F50),
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = "Geospatial Entity QR Code",
              style =
                MaterialTheme.typography.titleSmall.copy(
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF111827),
                ),
            )
          }
          Box(
            modifier =
              Modifier.clip(CircleShape)
                .background(Color(0xFFF3F4F6))
                .clickable { state.closeEntityQrCode() }
                .padding(5.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Close QR Modal",
              tint = Color(0xFF374151),
              modifier = Modifier.size(14.dp),
            )
          }
        }

        // Simulated high-contrast QR matrix for the Entity GeoID
        Box(
          modifier =
            Modifier.size(148.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(Color.White)
              .border(2.dp, Color(0xFF144532), RoundedCornerShape(12.dp))
              .padding(12.dp),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Default.QrCode,
            contentDescription = "Entity QR Matrix",
            tint = Color(0xFF111827),
            modifier = Modifier.size(116.dp),
          )
        }

        Text(
          text = entity.label,
          style =
            MaterialTheme.typography.titleSmall.copy(
              fontWeight = FontWeight.Bold,
              color = Color(0xFF111827),
            ),
        )
        Box(
          modifier =
            Modifier.clip(RoundedCornerShape(8.dp))
              .background(Color(0xFFE8F5E9))
              .border(1.dp, Color(0xFF2E7D32), RoundedCornerShape(8.dp))
              .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
          Text(
            text = "GeoID: ${entity.geoId}",
            style =
              MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B5E20),
              ),
          )
        }
        Text(
          text = "Scan with Ground or any EUDR compliance reader to verify entity geometry & GeoID.",
          style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6B7280)),
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = { state.closeEntityQrCode() },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp),
          ) {
            Text("Close")
          }
          Button(
            onClick = {
              state.closeEntityQrCode()
              state.shareEntityPdf(entity.id)
            },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1E6F50),
                contentColor = Color.White,
              ),
          ) {
            Icon(
              imageVector = Icons.Default.Share,
              contentDescription = null,
              modifier = Modifier.size(13.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Share PDF")
          }
        }
      }
    }
  }
}

/**
 * Modal dialog allowing the user to share a generated offline PDF receipt for either a Geospatial
 * Entity or a Submission (`state.activeSharedPdfSheet`) to their preferred messaging/email app.
 */
@Composable
private fun SharePdfToAppModalDialog(
  state: PrototypeAppState,
  sheet: SharedPdfSheetState,
) {
  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(Color.Black.copy(alpha = 0.5f))
        .clickable { state.closeSharePdfSheet() },
    contentAlignment = Alignment.Center,
  ) {
    Card(
      modifier =
        Modifier.width(330.dp)
          .clickable(enabled = false) {},
      shape = RoundedCornerShape(18.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White),
      elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
    ) {
      Column(
        modifier = Modifier.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
              imageVector = Icons.Default.Share,
              contentDescription = null,
              tint = Color(0xFF1E6F50),
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = "Share PDF to Preferred App",
              style =
                MaterialTheme.typography.titleSmall.copy(
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF111827),
                ),
            )
          }
          Box(
            modifier =
              Modifier.clip(CircleShape)
                .background(Color(0xFFF3F4F6))
                .clickable { state.closeSharePdfSheet() }
                .padding(5.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Close Share PDF Modal",
              tint = Color(0xFF374151),
              modifier = Modifier.size(14.dp),
            )
          }
        }

        // PDF attachment preview card
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(Color(0xFFEFF6F2))
              .border(1.dp, Color(0xFF1E6F50), RoundedCornerShape(10.dp))
              .padding(10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            imageVector = Icons.Default.PictureAsPdf,
            contentDescription = null,
            tint = Color(0xFFD32F2F),
            modifier = Modifier.size(26.dp),
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = sheet.pdfFileName,
              style =
                MaterialTheme.typography.labelMedium.copy(
                  fontFamily = FontFamily.Monospace,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF111827),
                ),
            )
            Text(
              text = "${sheet.title} • ${sheet.subtitle}",
              style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF374151)),
            )
          }
        }

        Text(
          text = "Select preferred application to send offline PDF receipt:",
          style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF4B5563)),
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          listOf("WhatsApp", "Gmail", "Google Drive", "Bluetooth").forEachIndexed { index, appName ->
            val isPreferred = index == 0
            Box(
              modifier =
                Modifier.weight(1f)
                  .clip(RoundedCornerShape(8.dp))
                  .background(if (isPreferred) Color(0xFF1E6F50) else Color(0xFFF3F4F6))
                  .clickable { state.closeSharePdfSheet() }
                  .padding(vertical = 8.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = appName,
                style =
                  MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPreferred) Color.White else Color(0xFF374151),
                  ),
              )
            }
          }
        }

        Button(
          onClick = { state.closeSharePdfSheet() },
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(10.dp),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = Color(0xFF1E6F50),
              contentColor = Color.White,
            ),
        ) {
          Text("Done")
        }
      }
    }
  }
}

/**
 * Drawer Sub-Screen: `Manage offline maps` (consistent with `docs/design/00-index.md` "Offline
 * Storage Safeguards & Media Purging" — Mapbox vector & raster tiles and 500 MB storage guardrail).
 */
@Composable
private fun ManageOfflineMapsSubScreen(state: PrototypeAppState) {
  val textColor = MaterialTheme.colorScheme.onSurface

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
          tint = Color(0xFF1E6F50),
          modifier = Modifier.size(20.dp),
        )
        Text(
          text = "Manage Offline Maps",
          style =
            MaterialTheme.typography.titleMedium.copy(
              fontWeight = FontWeight.Bold,
              color = textColor,
            ),
        )
      }
      OutlinedButton(
        onClick = { state.closeDrawerSubView() },
        shape = RoundedCornerShape(10.dp),
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
      shape = RoundedCornerShape(12.dp),
      colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
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
            tint = Color(0xFF1B5E20),
            modifier = Modifier.size(15.dp),
          )
          Text(
            text = "Device Storage Safeguard Active (4.2 GB Available)",
            style =
              MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B5E20),
              ),
          )
        }
        Text(
          text =
            "Pre-cached Mapbox vector & satellite raster tiles across zoom levels 10–19. Downloads automatically pause if device storage drops below 500 MB.",
          style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF2E7D32)),
        )
      }
    }

    state.offlineTilePackages.forEach { pkg ->
      Card(
        modifier =
          Modifier.fillMaxWidth()
            .border(1.dp, Color(0xFFDDE5E0), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
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
              style =
                MaterialTheme.typography.labelLarge.copy(
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF111827),
                ),
            )
            Text(
              text = "${pkg.tileTypeLabel} • ${pkg.zoomRangeLabel} • ${pkg.sizeLabel}",
              style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6B7280)),
            )
          }
          Button(
            onClick = { state.toggleOfflineTilePackage(pkg.id) },
            colors =
              ButtonDefaults.buttonColors(
                containerColor = if (pkg.isDownloaded) Color(0xFFE8F5E9) else Color(0xFF1E6F50),
                contentColor = if (pkg.isDownloaded) Color(0xFF1B5E20) else Color.White,
              ),
            shape = RoundedCornerShape(16.dp),
          ) {
            Icon(
              imageVector =
                if (pkg.isDownloaded) {
                  Icons.Default.Check
                } else {
                  Icons.Default.Download
                },
              contentDescription = null,
              modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = if (pkg.isDownloaded) "Cached" else "Download",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            )
          }
        }
      }
    }
  }
}

/**
 * Drawer Sub-Screen: `Settings` (consistent with `docs/design/00-index.md` "Measurement Unit
 * Preferences", "On-the-Fly Language Switching", and "Uploaded Media Cache Eviction").
 */
@Composable
private fun SurveySettingsSubScreen(state: PrototypeAppState) {
  val textColor = MaterialTheme.colorScheme.onSurface

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
          imageVector = Icons.Default.Settings,
          contentDescription = null,
          tint = Color(0xFF1E6F50),
          modifier = Modifier.size(20.dp),
        )
        Text(
          text = "Field App Settings",
          style =
            MaterialTheme.typography.titleMedium.copy(
              fontWeight = FontWeight.Bold,
              color = textColor,
            ),
        )
      }
      OutlinedButton(
        onClick = { state.closeDrawerSubView() },
        shape = RoundedCornerShape(10.dp),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = null,
          modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text("Back to Survey")
      }
    }

    // 1. Measurement Units (Metric vs Imperial)
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
      Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = "Measurement Unit System",
          style =
            MaterialTheme.typography.labelLarge.copy(
              fontWeight = FontWeight.Bold,
              color = Color(0xFF111827),
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          MeasurementUnitSystem.entries.forEach { sys ->
            val selected = state.unitSystem == sys
            Button(
              onClick = { state.updateUnitSystem(sys) },
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = if (selected) Color(0xFF1E6F50) else Color(0xFFF3F4F6),
                  contentColor = if (selected) Color.White else Color(0xFF374151),
                ),
              shape = RoundedCornerShape(10.dp),
            ) {
              Text(sys.label, style = MaterialTheme.typography.labelSmall)
            }
          }
        }
      }
    }

    // 2. On-the-Fly Survey Language Switching
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
      Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = "Survey & UI Language",
          style =
            MaterialTheme.typography.labelLarge.copy(
              fontWeight = FontWeight.Bold,
              color = Color(0xFF111827),
            ),
        )
        Row(
          modifier = Modifier.horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          listOf("en (English)", "sw (Kiswahili)", "vi (Tiếng Việt)", "es (Español)").forEach {
            locale ->
            val selected = state.selectedLanguageLocale == locale
            Button(
              onClick = { state.updateLanguageLocale(locale) },
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = if (selected) Color(0xFF1E6F50) else Color(0xFFF3F4F6),
                  contentColor = if (selected) Color.White else Color(0xFF374151),
                ),
              shape = RoundedCornerShape(10.dp),
            ) {
              Text(locale, style = MaterialTheme.typography.labelSmall)
            }
          }
        }
      }
    }

    // 3. Uploaded Media Cache Eviction
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Uploaded Media Cache Eviction",
            style =
              MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827),
              ),
          )
          Text(
            text =
              if (state.mediaCacheCleared) {
                "Evicted 128 MB of already-synced field photos (thumbnails preserved)."
              } else {
                "Purge locally cached photos already synchronized to cloud storage."
              },
            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6B7280)),
          )
        }
        Button(
          onClick = { state.evictUploadedMediaCache() },
          enabled = !state.mediaCacheCleared,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = Color(0xFF1E6F50),
              contentColor = Color.White,
            ),
          shape = RoundedCornerShape(10.dp),
        ) {
          if (state.mediaCacheCleared) {
            Icon(
              imageVector = Icons.Default.Check,
              contentDescription = null,
              modifier = Modifier.size(13.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
          }
          Text(
            text = if (state.mediaCacheCleared) "Purged" else "Purge Cache",
            style = MaterialTheme.typography.labelSmall,
          )
        }
      }
    }
  }
}
