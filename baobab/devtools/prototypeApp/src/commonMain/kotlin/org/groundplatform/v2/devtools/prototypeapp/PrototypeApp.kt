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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.AirplanemodeInactive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.groundplatform.v2.core.forms.ui.GroundAlertDialogOverlay
import org.groundplatform.v2.core.forms.ui.GroundBadgeTone
import org.groundplatform.v2.core.forms.ui.GroundTheme
import org.groundplatform.v2.core.forms.ui.GroundTonalBadge
import org.groundplatform.v2.core.forms.ui.LocalGroundBrandFontFamily
import org.groundplatform.v2.core.forms.ui.resources.Res
import org.groundplatform.v2.core.forms.ui.resources.sign_in_with_google
import org.groundplatform.v2.devtools.prototypeapp.surveyeditor.SurveyEditorPage
import org.groundplatform.v2.devtools.prototypeapp.surveyeditor.SurveyEditorState
import org.jetbrains.compose.resources.stringResource

/** Top-level pages of the prototype web app, addressable via the URL hash (e.g. `#dashboard`). */
enum class PrototypeWorkbenchPage(val label: String, val hash: String) {
  MOBILE_PROTOTYPE("Mobile prototype", "prototype"),
  WEB_DASHBOARD("Web dashboard", "dashboard"),
  SURVEY_EDITOR("Survey editor", "survey-editor");

  /** Whether this page belongs to the unified web application (dashboard + survey editor). */
  val isWebApp: Boolean
    get() = this == WEB_DASHBOARD || this == SURVEY_EDITOR

  companion object {
    /** Top-level switcher tabs shown in the workbench top bar. */
    val topBarPages: List<PrototypeWorkbenchPage> = listOf(MOBILE_PROTOTYPE, WEB_DASHBOARD)

    /** Legacy hashes kept working after pages were renamed. */
    private val aliases = mapOf("form-editor" to SURVEY_EDITOR, "web" to WEB_DASHBOARD)

    fun fromHash(hash: String?): PrototypeWorkbenchPage {
      val h = hash?.removePrefix("#")
      return entries.firstOrNull { it.hash == h } ?: aliases[h] ?: MOBILE_PROTOTYPE
    }
  }
}

/**
 * Root Compose Multiplatform Web application for `baobab/devtools/prototypeApp`.
 *
 * Renders an interactive UX design workbench that embeds a live Mobile or Tablet device preview of
 * the Ground 2.0 Compose Multiplatform UI (`Sign In` -> `Terms of Service` -> `Download survey` ->
 * `Main Survey UI`) and a unified web application combining the [WebDashboardPage] (survey map and
 * data tables) with the [SurveyEditorPage] (for designing surveys, Forms, Map layers, and Data
 * tables).
 */
@Composable
fun PrototypeApp(
  state: PrototypeAppState = remember { PrototypeAppState() },
  initialPage: PrototypeWorkbenchPage = PrototypeWorkbenchPage.MOBILE_PROTOTYPE,
  onPageChanged: (PrototypeWorkbenchPage) -> Unit = {},
) {
  var page by remember { mutableStateOf(initialPage) }
  val surveyEditorState = remember {
    SurveyEditorState().apply {
      updateDetails {
        it.copy(
          title = state.activeSurvey.title,
          description = state.activeSurvey.description,
        )
      }
    }
  }
  LaunchedEffect(state.activeSurveyId) {
    surveyEditorState.updateDetails {
      it.copy(
        title = state.activeSurvey.title,
        description = state.activeSurvey.description,
      )
    }
  }
  LaunchedEffect(surveyEditorState.details.title, surveyEditorState.details.description) {
    if (surveyEditorState.details.title.isNotBlank()) {
      state.updateActiveSurveyDetails(
        title = surveyEditorState.details.title,
        description = surveyEditorState.details.description,
      )
    }
  }
  val isMobileMapShowing =
    page == PrototypeWorkbenchPage.MOBILE_PROTOTYPE &&
      state.currentScreen == PrototypeScreen.MAIN_SURVEY &&
      state.activeDrawerSubView == MainDrawerSubView.NONE &&
      (!state.isDataCollectionFormOpen || state.isCurrentFormStepGeoPoint)
  // The Mapbox basemap renders behind the Compose canvas, so the root surface must stay transparent
  // whenever a page shows it.
  val isMapShowing = isMobileMapShowing || page == PrototypeWorkbenchPage.WEB_DASHBOARD

  GroundTheme(darkTheme = state.isDarkTheme) {
    Surface(
      modifier = Modifier.fillMaxSize(),
      color =
        if (isMapShowing) {
          Color.Transparent
        } else {
          MaterialTheme.colorScheme.surfaceContainerLowest
        },
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        PrototypeWorkbenchTopBar(
          state = state,
          page = page,
          onSelectPage = {
            page = it
            onPageChanged(it)
          },
        )

        when (page) {
          PrototypeWorkbenchPage.SURVEY_EDITOR ->
            SurveyEditorPage(
              state = surveyEditorState,
              isDarkTheme = state.isDarkTheme,
              onBackToDashboard = {
                page = PrototypeWorkbenchPage.WEB_DASHBOARD
                onPageChanged(PrototypeWorkbenchPage.WEB_DASHBOARD)
              },
            )
          PrototypeWorkbenchPage.WEB_DASHBOARD ->
            WebDashboardPage(
              state = state,
              onOpenSurveyEditor = {
                page = PrototypeWorkbenchPage.SURVEY_EDITOR
                onPageChanged(PrototypeWorkbenchPage.SURVEY_EDITOR)
              },
              onSignOut = {
                state.signOut()
                page = PrototypeWorkbenchPage.MOBILE_PROTOTYPE
                onPageChanged(PrototypeWorkbenchPage.MOBILE_PROTOTYPE)
              },
            )
          PrototypeWorkbenchPage.MOBILE_PROTOTYPE -> MobilePrototypePage(state, isMobileMapShowing)
        }
      }
    }
  }
}

/** Device preview stage plus the UX co-design inspector panel. */
@Composable
private fun MobilePrototypePage(state: PrototypeAppState, isMapShowing: Boolean) {
  Row(
    modifier = Modifier.fillMaxSize().padding(16.dp),
    horizontalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    val previewStageWeight = if (state.effectiveFrameWidthDp >= 600) 1.55f else 1.15f

    // Left / Center stage: Embedded Mobile or Tablet Device Preview
    Box(
      modifier =
        Modifier.weight(previewStageWeight)
          .fillMaxHeight()
          .verticalScroll(rememberScrollState())
          .horizontalScroll(rememberScrollState()),
      contentAlignment = Alignment.TopCenter,
    ) {
      MobileDevicePreviewFrame(
        deviceTitle =
          "Ground 2.0 ${state.deviceFormFactor.label} UI • Step ${state.currentScreen.stepNumber}/4: ${state.currentScreen.title}",
        isDarkTheme = state.isDarkTheme,
        isScreenTransparent = isMapShowing,
        formFactor = state.deviceFormFactor,
        orientation = state.deviceOrientation,
        onSelectFormFactor = { state.selectDeviceFormFactor(it) },
        onRotateDevice = { state.rotateDevice() },
      ) {
        MobileScreenHost(state)
      }
    }

    // Right panel: UX Designer Flow & State Controls
    UxDesignerInspectorPanel(state = state, modifier = Modifier.weight(0.85f).fillMaxHeight())
  }
}

/** Top navigation bar for the Web UX Prototype Workbench. */
@Composable
private fun PrototypeWorkbenchTopBar(
  state: PrototypeAppState,
  page: PrototypeWorkbenchPage,
  onSelectPage: (PrototypeWorkbenchPage) -> Unit,
) {
  val assistColors =
    androidx.compose.material3.AssistChipDefaults.assistChipColors(
      containerColor = Color(0xFF1D5128),
      labelColor = Color.White,
      leadingIconContentColor = Color(0xFF9CD49F),
    )
  Surface(
    modifier = Modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.inverseSurface,
    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    tonalElevation = 2.dp,
    shadowElevation = 4.dp,
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 10.dp)
          .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Brand & Tool Title
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        CloudAcaciaLogo(modifier = Modifier.size(38.dp))
        Column {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            val brandFont = LocalGroundBrandFontFamily.current
            Text(
              text =
                buildAnnotatedString {
                  withStyle(SpanStyle(fontFamily = brandFont, fontWeight = FontWeight.ExtraBold)) {
                    append("Ground")
                  }
                  append(" 2.0 Mobile UI Prototype")
                },
              style = MaterialTheme.typography.titleMedium,
              color = Color.White,
              fontWeight = FontWeight.Bold,
            )
            Surface(shape = MaterialTheme.shapes.extraSmall, color = Color(0xFF36693E)) {
              Text(
                text = "devtools/prototypeApp",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFB7F1B9),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
              )
            }
          }
          Text(
            text = "Compose Multiplatform Mobile & Tablet UI Preview & UX Co-Design Workbench",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFC1C9BE),
          )
        }
      }

      Spacer(modifier = Modifier.width(16.dp))

      // Global Workbench Controls
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // Page switcher: Mobile prototype | Web dashboard (unified with Survey editor)
        PrototypeWorkbenchPage.topBarPages.forEach { entry ->
          val isSelected =
            if (entry == PrototypeWorkbenchPage.WEB_DASHBOARD) {
              page.isWebApp
            } else {
              page == entry
            }
          FilterChip(
            selected = isSelected,
            onClick = { onSelectPage(entry) },
            colors =
              androidx.compose.material3.FilterChipDefaults.filterChipColors(
                containerColor = Color(0xFF1D5128),
                labelColor = Color.White,
                selectedContainerColor = Color(0xFFB7F1B9),
                selectedLabelColor = Color(0xFF002106),
              ),
            border =
              androidx.compose.material3.FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = isSelected,
                borderColor = Color(0xFF424940),
                selectedBorderColor = Color(0xFFB7F1B9),
              ),
            label = {
              Text(
                text = entry.label,
                maxLines = 1,
                softWrap = false,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
              )
            },
          )
        }
        Spacer(modifier = Modifier.width(8.dp))

        // Theme toggle button
        AssistChip(
          onClick = { state.toggleDarkTheme() },
          colors = assistColors,
          border =
            androidx.compose.material3.AssistChipDefaults.assistChipBorder(
              enabled = true,
              borderColor = Color(0xFF424940),
            ),
          leadingIcon = {
            Icon(
              imageVector =
                if (state.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
              contentDescription = null,
              modifier = Modifier.size(15.dp),
            )
          },
          label = {
            Text(
              text = if (state.isDarkTheme) "Light UI" else "Dark UI",
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              softWrap = false,
              style = MaterialTheme.typography.labelMedium,
            )
          },
        )

        // Device offline (Offline simulator) toggle chip
        FilterChip(
          selected = state.isAirplaneMode,
          onClick = { state.toggleAirplaneMode() },
          colors =
            androidx.compose.material3.FilterChipDefaults.filterChipColors(
              containerColor = Color(0xFF1D5128),
              labelColor = Color.White,
              iconColor = Color.White,
              selectedContainerColor = Color(0xFFFFB74D),
              selectedLabelColor = Color(0xFF3E2723),
              selectedLeadingIconColor = Color(0xFF3E2723),
            ),
          border =
            androidx.compose.material3.FilterChipDefaults.filterChipBorder(
              enabled = true,
              selected = state.isAirplaneMode,
              borderColor = Color(0xFF424940),
              selectedBorderColor = Color(0xFFFFE082),
            ),
          leadingIcon = {
            Icon(
              imageVector =
                if (state.isAirplaneMode) {
                  Icons.Default.AirplanemodeActive
                } else {
                  Icons.Default.AirplanemodeInactive
                },
              contentDescription = "Toggle Device offline simulation",
              modifier = Modifier.size(15.dp),
            )
          },
          label = {
            Text(
              text =
                if (state.isAirplaneMode) {
                  "Device offline: ON"
                } else {
                  "Device offline"
                },
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              softWrap = false,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = if (state.isAirplaneMode) FontWeight.Bold else FontWeight.Medium,
            )
          },
        )

        // Reset Flow button
        AssistChip(
          onClick = { state.resetPrototypeFlow() },
          colors = assistColors,
          border =
            androidx.compose.material3.AssistChipDefaults.assistChipBorder(
              enabled = true,
              borderColor = Color(0xFF424940),
            ),
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = null,
              modifier = Modifier.size(15.dp),
            )
          },
          label = {
            Text(
              text = "Reset Flow",
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              softWrap = false,
              style = MaterialTheme.typography.labelMedium,
            )
          },
        )
      }
    }
  }
}

/**
 * Realistic Mobile or Tablet hardware device frame embedding the Compose Multiplatform UI preview.
 */
@Composable
fun MobileDevicePreviewFrame(
  deviceTitle: String,
  isDarkTheme: Boolean,
  isScreenTransparent: Boolean = false,
  formFactor: DeviceFormFactor = DeviceFormFactor.MOBILE,
  orientation: DeviceOrientation = formFactor.defaultOrientation,
  onSelectFormFactor: (DeviceFormFactor) -> Unit = {},
  onRotateDevice: () -> Unit = {},
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val frameWidthDp = formFactor.widthForOrientation(orientation)
  val frameHeightDp = formFactor.heightForOrientation(orientation)
  val dimensionsLabel = formFactor.dimensionsLabelForOrientation(orientation)
  val isRotated = orientation != formFactor.defaultOrientation

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    // Stage Header with Device Title + Inline Mobile / Tablet Form Factor Toggle + Rotate Device
    // Widget
    Row(
      modifier = Modifier.width(frameWidthDp.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
        Text(
          text = deviceTitle,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "Viewport: ${formFactor.label} • ${orientation.label} ($dimensionsLabel)",
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          style =
            MaterialTheme.typography.labelSmall.copy(
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
      }

      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // Segmented Form Factor Switcher right above the device frame
        SingleChoiceSegmentedButtonRow {
          DeviceFormFactor.entries.forEachIndexed { index, factor ->
            val isSelected = formFactor == factor
            val icon =
              if (factor == DeviceFormFactor.MOBILE) {
                Icons.Default.Smartphone
              } else {
                Icons.Default.Tablet
              }
            SegmentedButton(
              selected = isSelected,
              onClick = { onSelectFormFactor(factor) },
              shape =
                SegmentedButtonDefaults.itemShape(
                  index = index,
                  count = DeviceFormFactor.entries.size,
                ),
              icon = {
                Icon(
                  imageVector = icon,
                  contentDescription = factor.label,
                  modifier = Modifier.size(14.dp),
                )
              },
              label = {
                Text(
                  text = factor.label,
                  maxLines = 1,
                  softWrap = false,
                  style = MaterialTheme.typography.labelSmall,
                )
              },
            )
          }
        }

        // Rotate Device Widget right next to the Form Factor switcher
        FilterChip(
          selected = isRotated,
          onClick = { onRotateDevice() },
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.ScreenRotation,
              contentDescription = "Rotate device",
              modifier = Modifier.size(14.dp),
            )
          },
          label = {
            Text(
              text = "Rotate",
              maxLines = 1,
              softWrap = false,
              style = MaterialTheme.typography.labelSmall,
            )
          },
        )
      }
    }

    // Outer Device Hardware Bezel (dynamically sized for Mobile vs Tablet and Portrait vs
    // Landscape)
    val outerShape = RoundedCornerShape(formFactor.outerCornerRadiusDp.dp)
    val innerShape = RoundedCornerShape(formFactor.innerCornerRadiusDp.dp)
    Box(
      modifier =
        Modifier.width(frameWidthDp.dp)
          .height(frameHeightDp.dp)
          .clip(outerShape)
          .background(Color(0xFF111827))
          .border(2.dp, Color(0xFF374151), outerShape)
          .padding(if (formFactor == DeviceFormFactor.TABLET) 12.dp else 10.dp)
    ) {
      Column(
        modifier =
          Modifier.fillMaxSize()
            .clip(innerShape)
            .background(
              if (isScreenTransparent) {
                Color.Transparent
              } else {
                MaterialTheme.colorScheme.surface
              }
            )
      ) {
        // Device Status Bar
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainerHighest,
          contentColor = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text =
                if (
                  formFactor == DeviceFormFactor.TABLET ||
                    orientation == DeviceOrientation.LANDSCAPE
                ) {
                  "09:41 • Wed Sep 19"
                } else {
                  "09:41"
                },
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
            )
            // Camera punch-hole notch (Mobile) or slim landscape bezel sensor (Tablet)
            if (formFactor == DeviceFormFactor.MOBILE) {
              Box(
                modifier =
                  Modifier.width(68.dp)
                    .height(11.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF061B12))
              )
            } else {
              Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF061B12)))
            }
            Text(
              text = "5G • 100%",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }

        // Embedded Compose Multiplatform Screen Content
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }

        // Bottom Gesture Navigation Bar
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainer,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
            contentAlignment = Alignment.Center,
          ) {
            Box(
              modifier =
                Modifier.width(
                    if (
                      formFactor == DeviceFormFactor.TABLET ||
                        orientation == DeviceOrientation.LANDSCAPE
                    ) {
                      160.dp
                    } else {
                      116.dp
                    }
                  )
                  .height(4.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.outline)
            )
          }
        }
      }
    }
  }
}

/** Right-hand UX Designer Co-Design & Flow Inspector panel in the Web Prototype App. */
@Composable
private fun UxDesignerInspectorPanel(state: PrototypeAppState, modifier: Modifier = Modifier) {
  ElevatedCard(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
  ) {
    Column(
      modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
        text = "UX Co-Design & Flow Controls",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text =
          "Use this panel during UX review sessions to jump between onboarding and main survey screens, inspect single- vs multi-submission location bottom sheets, toggle layers, or test XForms FormDef XML data collection.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      // Live XForms FormDef XML Tester Section
      PrototypeXFormsWorkbenchPanel(state)

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      // 1. Interactive Screen Stepper
      Text(
        text = "1. ONBOARDING & SURVEY FLOW SCREENS",
        style =
          MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 0.6.sp,
          ),
      )

      PrototypeScreen.entries.forEach { screen ->
        val isActive = state.currentScreen == screen
        OutlinedCard(
          onClick = { state.navigateTo(screen) },
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.medium,
          colors =
            CardDefaults.outlinedCardColors(
              containerColor =
                if (isActive) {
                  MaterialTheme.colorScheme.primaryContainer
                } else {
                  MaterialTheme.colorScheme.surfaceContainerLow
                }
            ),
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
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Step ${screen.stepNumber}: ${screen.title}",
                style = MaterialTheme.typography.labelLarge,
                color =
                  if (isActive) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                  } else {
                    MaterialTheme.colorScheme.onSurface
                  },
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = screen.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color =
                  if (isActive) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                  } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                  },
              )
            }
            if (isActive) {
              GroundTonalBadge(text = "ACTIVE", tone = GroundBadgeTone.PRIMARY)
            }
          }
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      // 2. Main Survey UI Quick State Shortcuts (Map, List, Layers, ○ → ◐ → ✓ Marker States,
      // Drawer)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "2. MAIN SURVEY UI QUICK INSPECTOR (STEP 4)",
          style =
            MaterialTheme.typography.labelSmall.copy(
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
              letterSpacing = 0.6.sp,
            ),
        )
        FilledTonalButton(
          onClick = { state.addRandomSites(5_000) },
          modifier = Modifier.height(30.dp),
          contentPadding = ButtonDefaults.TextButtonContentPadding,
        ) {
          Icon(
            imageVector = Icons.Default.Layers,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "+5,000 Random Features (${state.entities.size})",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
          )
        }
      }
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        val mainShortcuts: List<Triple<ImageVector, String, () -> Unit>> =
          listOf(
            Triple(
              Icons.Default.Map,
              "○ Pending Parcel (NYR-112)",
              {
                state.navigateTo(PrototypeScreen.MAIN_SURVEY)
                state.setMainSurveyViewMode(MainSurveyViewMode.MAP)
                state.selectEntity("entity-nyr-112")
              },
            ),
            Triple(
              Icons.Default.Map,
              "◐ In Progress (NYR-108)",
              {
                state.navigateTo(PrototypeScreen.MAIN_SURVEY)
                state.setMainSurveyViewMode(MainSurveyViewMode.MAP)
                state.selectEntity("entity-nyr-108")
              },
            ),
            Triple(
              Icons.Default.CheckCircle,
              "✓ Completed (NYR-104)",
              {
                state.navigateTo(PrototypeScreen.MAIN_SURVEY)
                state.setMainSurveyViewMode(MainSurveyViewMode.MAP)
                state.selectEntity("entity-nyr-104")
              },
            ),
            Triple(
              Icons.Default.Timeline,
              "✓ Transect Line (SHD-201)",
              {
                state.navigateTo(PrototypeScreen.MAIN_SURVEY)
                state.setMainSurveyViewMode(MainSurveyViewMode.MAP)
                state.selectEntity("entity-shade-201")
              },
            ),
            Triple(
              Icons.Default.CheckCircle,
              "Standalone Submission (No Feature)",
              {
                state.navigateTo(PrototypeScreen.MAIN_SURVEY)
                state.setMainSurveyViewMode(MainSurveyViewMode.MAP)
                state.selectSubmissionDetail("sub-standalone-pest-01")
              },
            ),
            Triple(
              Icons.Default.Navigation,
              "Navigate to Feature",
              {
                state.navigateTo(PrototypeScreen.MAIN_SURVEY)
                state.startNavigationToEntity("entity-nyr-104")
              },
            ),
            Triple(
              Icons.Default.Layers,
              "Layers Sheet",
              {
                state.navigateTo(PrototypeScreen.MAIN_SURVEY)
                state.setMainSurveyViewMode(MainSurveyViewMode.MAP)
                state.updateLayersSheetOpen(!state.isLayersSheetOpen)
              },
            ),
            Triple(
              Icons.AutoMirrored.Filled.List,
              "Searchable List View",
              {
                state.navigateTo(PrototypeScreen.MAIN_SURVEY)
                state.setMainSurveyViewMode(MainSurveyViewMode.LIST)
              },
            ),
            Triple(
              Icons.Default.Menu,
              "Navigation Drawer",
              {
                state.navigateTo(PrototypeScreen.MAIN_SURVEY)
                state.updateDrawerOpen(true)
              },
            ),
            Triple(
              Icons.Default.CloudUpload,
              "Uploads (${state.mutations.size})",
              {
                state.navigateTo(PrototypeScreen.MAIN_SURVEY)
                state.drawerOpenUploads()
              },
            ),
            Triple(
              Icons.Default.Settings,
              "Settings Screen",
              {
                state.navigateTo(PrototypeScreen.MAIN_SURVEY)
                state.drawerOpenSettings()
              },
            ),
            Triple(
              Icons.Default.Map,
              "Offline Maps & Storage",
              {
                state.navigateTo(PrototypeScreen.MAIN_SURVEY)
                state.drawerManageOfflineMaps()
              },
            ),
            Triple(
              Icons.Default.MyLocation,
              if (state.isCameraFollowingUser) "Simulate Map Pan" else "Recenter GPS",
              {
                state.navigateTo(PrototypeScreen.MAIN_SURVEY)
                state.setMainSurveyViewMode(MainSurveyViewMode.MAP)
                if (state.isCameraFollowingUser) {
                  state.panMap(deltaNormalizedX = 0.16f, deltaNormalizedY = -0.12f)
                } else {
                  state.recenterMapOnUser()
                }
              },
            ),
            Triple(
              Icons.Default.LocationOn,
              "Simulate GPS Walk",
              {
                state.navigateTo(PrototypeScreen.MAIN_SURVEY)
                state.setMainSurveyViewMode(MainSurveyViewMode.MAP)
                val nextX =
                  if (state.userGpsNormalizedX > 0.62f) 0.36f else state.userGpsNormalizedX + 0.08f
                val nextY =
                  if (state.userGpsNormalizedY > 0.60f) 0.38f else state.userGpsNormalizedY + 0.06f
                state.updateUserGpsLocation(nextX, nextY)
              },
            ),
          )
        mainShortcuts.forEach { (icon, label, action) ->
          AssistChip(
            onClick = action,
            leadingIcon = {
              Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(14.dp))
            },
            label = {
              Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
              )
            },
          )
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      // 3. Quick Search Presets (Name or Location)
      Text(
        text = "3. DOWNLOAD SURVEY SEARCH PRESETS (NAME OR LOCATION)",
        style =
          MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 0.6.sp,
          ),
      )
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        val sampleQueries =
          listOf(
            "All" to "",
            "Brazil" to "Brazil",
            "Kenya" to "Kenya",
            "Vietnam" to "Vietnam",
            "Mangrove" to "Mangrove",
            "Watershed" to "Watershed",
          )
        sampleQueries.forEach { (label, query) ->
          val selected = state.searchQuery == query
          FilterChip(
            selected = selected,
            onClick = {
              state.navigateTo(PrototypeScreen.DOWNLOAD_SURVEY)
              state.updateSearchQuery(query)
            },
            leadingIcon =
              if (query.isNotEmpty()) {
                {
                  Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                  )
                }
              } else {
                null
              },
            label = {
              Text(
                text = if (query.isEmpty()) "Show All (${state.surveys.size})" else "\"$label\"",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
              )
            },
          )
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      // 4. Survey Download State Toggles
      Text(
        text = "4. DOWNLOADED INDICATOR STATE SIMULATOR",
        style =
          MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 0.6.sp,
          ),
      )
      Text(
        text = "Toggle which surveys are marked as already downloaded on the device:",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      state.surveys.forEach { survey ->
        OutlinedCard(
          onClick = {
            state.navigateTo(PrototypeScreen.DOWNLOAD_SURVEY)
            state.toggleSurveyDownloaded(survey.id)
          },
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.small,
          colors =
            CardDefaults.outlinedCardColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = survey.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = survey.location,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            GroundTonalBadge(
              text = if (survey.isDownloaded) "Downloaded" else "Not Downloaded",
              tone = if (survey.isDownloaded) GroundBadgeTone.PRIMARY else GroundBadgeTone.NEUTRAL,
            )
          }
        }
      }
    }
  }
}
