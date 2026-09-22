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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.groundplatform.v2.core.forms.ui.GroundAlertDialogOverlay
import org.groundplatform.v2.core.forms.ui.GroundBadgeTone
import org.groundplatform.v2.core.forms.ui.GroundTheme
import org.groundplatform.v2.core.forms.ui.GroundTonalBadge

/**
 * Root Compose Multiplatform Web application for `baobab/devtools/prototypeApp`.
 *
 * Renders an interactive UX design workbench that embeds a live Mobile or Tablet device preview of
 * the Ground 2.0 Compose Multiplatform UI (`Splash` -> `Sign In` -> `Terms of Service` ->
 * `Download survey` -> `Main Survey UI`).
 */
@Composable
fun PrototypeApp(state: PrototypeAppState = remember { PrototypeAppState() }) {
  val isMapShowing =
    state.currentScreen == PrototypeScreen.MAIN_SURVEY &&
      state.mainViewMode == MainSurveyViewMode.MAP &&
      !state.isDataCollectionFormOpen

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
        PrototypeWorkbenchTopBar(state)

        Row(
          modifier = Modifier.fillMaxSize().padding(16.dp),
          horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
          val previewStageWeight =
            if (state.effectiveFrameWidthDp >= 600) 1.55f else 1.15f

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
                "Ground 2.0 ${state.deviceFormFactor.label} UI • Step ${state.currentScreen.stepNumber}/5: ${state.currentScreen.title}",
              isDarkTheme = state.isDarkTheme,
              isScreenTransparent = isMapShowing,
              formFactor = state.deviceFormFactor,
              orientation = state.deviceOrientation,
              onSelectFormFactor = { state.selectDeviceFormFactor(it) },
              onRotateDevice = { state.rotateDevice() },
            ) {
              GroundMobilePrototypeScreenHost(state)
            }
          }

          // Right panel: UX Designer Flow & State Controls
          UxDesignerInspectorPanel(
            state = state,
            modifier = Modifier.weight(0.85f).fillMaxHeight(),
          )
        }
      }
    }
  }
}

/** Top navigation bar for the Web UX Prototype Workbench. */
@Composable
private fun PrototypeWorkbenchTopBar(state: PrototypeAppState) {
  val chipColors =
    androidx.compose.material3.FilterChipDefaults.filterChipColors(
      containerColor = Color(0xFF1F4E39),
      labelColor = Color.White,
      iconColor = Color.White,
      selectedContainerColor = Color(0xFF8BD6B1),
      selectedLabelColor = Color(0xFF003825),
      selectedLeadingIconColor = Color(0xFF003825),
    )
  val assistColors =
    androidx.compose.material3.AssistChipDefaults.assistChipColors(
      containerColor = Color(0xFF1F4E39),
      labelColor = Color.White,
      leadingIconContentColor = Color(0xFF8BD6B1),
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
        GroundCloudAcaciaLogo(modifier = Modifier.size(38.dp))
        Column {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = "Ground 2.0 Mobile UI Prototype",
              style = MaterialTheme.typography.titleMedium,
              color = Color.White,
              fontWeight = FontWeight.Bold,
            )
            Surface(
              shape = MaterialTheme.shapes.extraSmall,
              color = Color(0xFF1E6F50),
            ) {
              Text(
                text = "devtools/prototypeApp",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFC8E6C9),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
              )
            }
          }
          Text(
            text = "Compose Multiplatform Mobile & Tablet UI Preview & UX Co-Design Workbench",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFA7C4B5),
          )
        }
      }

      Spacer(modifier = Modifier.width(16.dp))

      // Screen Quick-Jump Pills + Device Form Factor Toggle + Controls
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        PrototypeScreen.entries.forEach { screen ->
          val isSelected = state.currentScreen == screen
          FilterChip(
            selected = isSelected,
            onClick = { state.navigateTo(screen) },
            colors = chipColors,
            border =
              androidx.compose.material3.FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = isSelected,
                borderColor = Color(0xFF386B52),
                selectedBorderColor = Color(0xFF8BD6B1),
              ),
            label = {
              Text(
                text = "${screen.stepNumber}. ${screen.title}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
              )
            },
          )
        }

        // Segmented Form Factor Toggle: Mobile | Tablet
        SingleChoiceSegmentedButtonRow {
          DeviceFormFactor.entries.forEachIndexed { index, factor ->
            val selected = state.deviceFormFactor == factor
            val icon =
              if (factor == DeviceFormFactor.MOBILE) {
                Icons.Default.Smartphone
              } else {
                Icons.Default.Tablet
              }
            SegmentedButton(
              selected = selected,
              onClick = { state.selectDeviceFormFactor(factor) },
              shape =
                SegmentedButtonDefaults.itemShape(
                  index = index,
                  count = DeviceFormFactor.entries.size,
                ),
              colors =
                SegmentedButtonDefaults.colors(
                  activeContainerColor = Color(0xFF8BD6B1),
                  activeContentColor = Color(0xFF003825),
                  activeBorderColor = Color(0xFF8BD6B1),
                  inactiveContainerColor = Color(0xFF194230),
                  inactiveContentColor = Color.White,
                  inactiveBorderColor = Color(0xFF386B52),
                ),
              icon = {
                Icon(
                  imageVector = icon,
                  contentDescription = factor.label,
                  modifier = Modifier.size(15.dp),
                )
              },
              label = {
                Text(
                  text = factor.label,
                  maxLines = 1,
                  softWrap = false,
                  style = MaterialTheme.typography.labelMedium,
                )
              },
            )
          }
        }

        // Rotate Device widget button in top bar
        FilterChip(
          selected = state.isDeviceRotated,
          onClick = { state.rotateDevice() },
          colors = chipColors,
          border =
            androidx.compose.material3.FilterChipDefaults.filterChipBorder(
              enabled = true,
              selected = state.isDeviceRotated,
              borderColor = Color(0xFF386B52),
              selectedBorderColor = Color(0xFF8BD6B1),
            ),
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.ScreenRotation,
              contentDescription = "Rotate device",
              modifier = Modifier.size(15.dp),
            )
          },
          label = {
            Text(
              text = "Rotate (${state.deviceOrientation.label})",
              maxLines = 1,
              softWrap = false,
              style = MaterialTheme.typography.labelMedium,
            )
          },
        )

        // Theme toggle button
        AssistChip(
          onClick = { state.toggleDarkTheme() },
          colors = assistColors,
          border =
            androidx.compose.material3.AssistChipDefaults.assistChipBorder(
              enabled = true,
              borderColor = Color(0xFF386B52),
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
              softWrap = false,
              style = MaterialTheme.typography.labelMedium,
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
              borderColor = Color(0xFF386B52),
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
    // Stage Header with Device Title + Inline Mobile / Tablet Form Factor Toggle + Rotate Device Widget
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
              fontFamily = FontFamily.Monospace,
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

    // Outer Device Hardware Bezel (dynamically sized for Mobile vs Tablet and Portrait vs Landscape)
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
                  Modifier.width(68.dp).height(11.dp).clip(CircleShape).background(Color(0xFF061B12))
              )
            } else {
              Box(
                modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF061B12))
              )
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

/**
 * Host composable that switches between the 5 Ground Mobile UI prototype screens:
 * 1. Splash / Loading Screen
 * 2. Sign In Screen (Sign in with Google only)
 * 3. Terms of Service Screen
 * 4. Download Survey Screen (with search by name or location, map thumbnail, and downloaded indicator)
 * 5. Main Survey UI (Map & List Views, Layers toggle, 1:1 / 1:N Entity Bottom Sheet, and Navigation Drawer)
 */
@Composable
fun GroundMobilePrototypeScreenHost(state: PrototypeAppState) {
  if (state.isDataCollectionFormOpen && state.activeFormWizardController != null) {
    PrototypeDataCollectionFormScreen(state)
    return
  }
  when (state.currentScreen) {
    PrototypeScreen.SPLASH -> GroundSplashScreen(state)
    PrototypeScreen.SIGN_IN -> GroundSignInScreen(state)
    PrototypeScreen.TERMS_OF_SERVICE -> GroundTermsOfServiceScreen(state)
    PrototypeScreen.DOWNLOAD_SURVEY -> GroundDownloadSurveyScreen(state)
    PrototypeScreen.MAIN_SURVEY -> GroundMainSurveyScreen(state)
  }
}

/**
 * Renders the Ground 2.0 "Cloud Acacia" vector logo (`shared/assets/logo.svg` • `viewBox="0 0 512 512"`).
 */
@Composable
fun GroundCloudAcaciaLogo(modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val s = size.minDimension / 512f
    fun sx(x: Float) = x * s
    fun sy(y: Float) = y * s

    // 1. Rounded Pebble App Container (<rect x="24" y="24" width="464" height="464" rx="128" fill="url(#ac3-bg)" />)
    drawRoundRect(
      brush =
        Brush.linearGradient(
          colors = listOf(Color(0xFF1A5C43), Color(0xFF0A241A)),
          start = Offset(sx(64f), sy(32f)),
          end = Offset(sx(448f), sy(480f)),
        ),
      topLeft = Offset(sx(24f), sy(24f)),
      size = Size(sx(464f), sy(464f)),
      cornerRadius = CornerRadius(sx(128f), sy(128f)),
    )

    // 2. Outer Map Pin / Canopy Droplet Path
    val pinPath =
      Path().apply {
        moveTo(sx(256f), sy(76f))
        cubicTo(sx(166f), sy(76f), sx(104f), sy(142f), sx(104f), sy(228f))
        cubicTo(sx(104f), sy(318f), sx(208f), sy(392f), sx(244f), sy(422f))
        cubicTo(sx(251f), sy(428f), sx(261f), sy(428f), sx(268f), sy(422f))
        cubicTo(sx(304f), sy(392f), sx(408f), sy(318f), sx(408f), sy(228f))
        cubicTo(sx(408f), sy(142f), sx(346f), sy(76f), sx(256f), sy(76f))
        close()
      }

    drawPath(
      path = pinPath,
      brush =
        Brush.linearGradient(
          0.0f to Color(0xFFA7E3C5),
          0.48f to Color(0xFF34A853),
          1.0f to Color(0xFF144532),
          start = Offset(sx(136f), sy(76f)),
          end = Offset(sx(376f), sy(416f)),
        ),
    )

    // 3. Clipped Interior Scene (<g clip-path="url(#ac3-clip)">)
    clipPath(pinPath) {
      // Warm Golden Sunrise Disc (<circle cx="256" cy="240" r="94" fill="url(#ac3-sun)" />)
      drawCircle(
        brush =
          Brush.linearGradient(
            0.0f to Color(0xFFFFF9E6),
            0.60f to Color(0xFFFDE047),
            1.0f to Color(0xFFF59E0B),
            start = Offset(sx(256f), sy(148f)),
            end = Offset(sx(256f), sy(328f)),
          ),
        radius = sx(94f),
        center = Offset(sx(256f), sy(240f)),
      )

      val darkForest = Color(0xFF0B291E)

      // Main Upper Cloud Canopy
      val upperCloudPath =
        Path().apply {
          moveTo(sx(174f), sy(192f))
          cubicTo(sx(172f), sy(170f), sx(198f), sy(156f), sx(224f), sy(164f))
          cubicTo(sx(238f), sy(148f), sx(274f), sy(148f), sx(288f), sy(164f))
          cubicTo(sx(314f), sy(156f), sx(346f), sy(168f), sx(348f), sy(188f))
          cubicTo(sx(350f), sy(202f), sx(326f), sy(208f), sx(294f), sy(204f))
          cubicTo(sx(268f), sy(202f), sx(244f), sy(202f), sx(218f), sy(204f))
          cubicTo(sx(190f), sy(206f), sx(175f), sy(202f), sx(174f), sy(192f))
          close()
        }
      drawPath(path = upperCloudPath, color = darkForest)

      // Lower Side Cloud Bough
      val sideCloudPath =
        Path().apply {
          moveTo(sx(132f), sy(218f))
          cubicTo(sx(130f), sy(200f), sx(152f), sy(190f), sx(176f), sy(196f))
          cubicTo(sx(194f), sy(190f), sx(218f), sy(198f), sx(220f), sy(212f))
          cubicTo(sx(222f), sy(222f), sx(200f), sy(226f), sx(174f), sy(224f))
          cubicTo(sx(150f), sy(226f), sx(133f), sy(224f), sx(132f), sy(218f))
          close()
        }
      drawPath(path = sideCloudPath, color = darkForest)

      // Trunk & Branching Forks
      val trunkPath =
        Path().apply {
          moveTo(sx(224f), sy(350f))
          cubicTo(sx(238f), sy(320f), sx(245f), sy(284f), sx(245f), sy(248f))
          cubicTo(sx(245f), sy(232f), sx(220f), sy(222f), sx(188f), sy(218f))
          lineTo(sx(208f), sy(212f))
          cubicTo(sx(230f), sy(216f), sx(244f), sy(224f), sx(250f), sy(234f))
          lineTo(sx(252f), sy(194f))
          lineTo(sx(264f), sy(194f))
          lineTo(sx(268f), sy(248f))
          cubicTo(sx(268f), sy(284f), sx(275f), sy(320f), sx(290f), sy(350f))
          close()
        }
      drawPath(path = trunkPath, color = darkForest)

      // Rolling Earth Base
      val earthBasePath =
        Path().apply {
          moveTo(sx(104f), sy(342f))
          cubicTo(sx(192f), sy(314f), sx(320f), sy(314f), sx(408f), sy(342f))
          lineTo(sx(256f), sy(436f))
          close()
        }
      drawPath(path = earthBasePath, color = darkForest)
    }

    // 4. Signature Small Orange Circle Motif (<circle cx="256" cy="122" r="20" fill="url(#ac3-orange)" />)
    drawCircle(
      brush =
        Brush.linearGradient(
          colors = listOf(Color(0xFFFB923C), Color(0xFFEA580C)),
          start = Offset(sx(236f), sy(106f)),
          end = Offset(sx(276f), sy(146f)),
        ),
      radius = sx(20f),
      center = Offset(sx(256f), sy(122f)),
    )

    // 5. Minimal Horizon Wave (<path d="M144 432 C200 406, 312 406, 368 432" ... />)
    val horizonWavePath =
      Path().apply {
        moveTo(sx(144f), sy(432f))
        cubicTo(sx(200f), sy(406f), sx(312f), sy(406f), sx(368f), sy(432f))
      }
    drawPath(
      path = horizonWavePath,
      color = Color(0xFF8BD6B1).copy(alpha = 0.75f),
      style = Stroke(width = sx(14f), cap = StrokeCap.Round),
    )
  }
}

/** 1. Splash / Loading screen for Ground 2.0 featuring the Cloud Acacia SVG logo. */
@Composable
fun GroundSplashScreen(state: PrototypeAppState) {
  val isCompactHeight = state.effectiveFrameHeightDp < 500

  Column(
    modifier =
      Modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.primaryContainer)
        .verticalScroll(rememberScrollState())
        .padding(
          horizontal = 28.dp,
          vertical = if (isCompactHeight) 16.dp else 32.dp,
        ),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.SpaceBetween,
  ) {
    Spacer(modifier = Modifier.height(if (isCompactHeight) 4.dp else 20.dp))

    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 8.dp else 16.dp),
    ) {
      // Ground 2.0 Cloud Acacia Emblem Badge (from shared/assets/logo.svg)
      GroundCloudAcaciaLogo(modifier = Modifier.size(if (isCompactHeight) 78.dp else 124.dp))

      Text(
        text = "Ground",
        style =
          MaterialTheme.typography.headlineLarge.copy(
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp,
          ),
      )

      Text(
        text = "Community-centered geospatial data collection",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(if (isCompactHeight) 6.dp else 20.dp))

      // Loading progress bar & status label
      Column(
        modifier = Modifier.fillMaxWidth(0.82f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        LinearProgressIndicator(
          modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
          color = MaterialTheme.colorScheme.primary,
          trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        Text(
          text = "Initializing offline map engine & workspace...",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
          textAlign = TextAlign.Center,
        )
      }
    }

    Spacer(modifier = Modifier.height(if (isCompactHeight) 12.dp else 24.dp))

    // Bottom interactive prototype trigger to advance from Splash -> Sign In
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Button(
        onClick = { state.completeSplashLoading() },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
      ) {
        Text(
          text = "Continue to Sign In",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowForward,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
        )
      }
      Text(
        text = "Ground 2.0 Splash / Loading Screen • Tap to advance",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
      )
    }
  }
}

/** 2. Sign In screen (Sign in with Google + Language selector matching Ground SettingsSelectItem). */
@Composable
fun GroundSignInScreen(state: PrototypeAppState) {
  val surfaceColor = MaterialTheme.colorScheme.surface
  val onSurfaceColor = MaterialTheme.colorScheme.onSurface
  val strings = groundLocalizedStringsFor(state.selectedLanguageCode)

  Column(
    modifier =
      Modifier.fillMaxSize()
        .background(surfaceColor)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 22.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.SpaceBetween,
  ) {
    // Top Brand Header
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Spacer(modifier = Modifier.height(4.dp))
      GroundCloudAcaciaLogo(modifier = Modifier.size(68.dp))
      Text(
        text = "Welcome to Ground",
        style = MaterialTheme.typography.headlineSmall,
        color = onSurfaceColor,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text =
          "Collect field observations, map boundaries, and synchronize survey records offline.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Center Field Survey Illustration Card
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = MaterialTheme.shapes.large,
      colors =
        CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        // Stylized map preview graphic
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .height(132.dp)
              .clip(MaterialTheme.shapes.medium)
              .background(Color(0xFF1B5E20))
        ) {
          Canvas(modifier = Modifier.fillMaxSize()) {
            // Grid lines
            val stepX = size.width / 5f
            val stepY = size.height / 4f
            for (i in 1..4) {
              drawLine(
                color = Color.White.copy(alpha = 0.12f),
                start = Offset(stepX * i, 0f),
                end = Offset(stepX * i, size.height),
                strokeWidth = 1.2f,
              )
            }
            for (j in 1..3) {
              drawLine(
                color = Color.White.copy(alpha = 0.12f),
                start = Offset(0f, stepY * j),
                end = Offset(size.width, stepY * j),
                strokeWidth = 1.2f,
              )
            }
            // River curve
            val river =
              Path().apply {
                moveTo(0f, size.height * 0.75f)
                cubicTo(
                  size.width * 0.35f,
                  size.height * 0.55f,
                  size.width * 0.60f,
                  size.height * 0.85f,
                  size.width,
                  size.height * 0.35f,
                )
              }
            drawPath(path = river, color = Color(0xFF29B6F6), style = Stroke(width = 10f))

            // Survey polygon highlight
            val poly =
              Path().apply {
                moveTo(size.width * 0.25f, size.height * 0.25f)
                lineTo(size.width * 0.62f, size.height * 0.20f)
                lineTo(size.width * 0.70f, size.height * 0.58f)
                lineTo(size.width * 0.30f, size.height * 0.62f)
                close()
              }
            drawPath(path = poly, color = Color(0xFFA5D6A7).copy(alpha = 0.35f))
            drawPath(path = poly, color = Color(0xFFA5D6A7), style = Stroke(width = 3f))
          }

          Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp),
              )
              Text(
                text = "Offline Satellite & Vector Layers Ready",
                style = MaterialTheme.typography.labelSmall,
              )
            }
          }
        }

        Text(
          text = "Sign in to access surveys shared with your account.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Language Selector matching Ground SettingsSelectItem + Bottom Sign-In Actions
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      SignInLanguageSelector(state = state)

      OutlinedButton(
        onClick = { state.signInWithGoogle() },
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors =
          ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
          ),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          // Google 'G' Badge
          Surface(
            modifier = Modifier.size(26.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Text(
                text = "G",
                style = MaterialTheme.typography.titleSmall,
                color = Color(0xFF4285F4),
                fontWeight = FontWeight.ExtraBold,
              )
            }
          }
          Text(
            text = strings.signInWithGoogle,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }

      Text(
        text = "Authentication is currently limited to Google Accounts.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }
  }
}

/** 3. Terms of Service screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroundTermsOfServiceScreen(state: PrototypeAppState) {
  val onSurfaceColor = MaterialTheme.colorScheme.onSurface

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = "Terms of Service",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Signed in as ${state.signedInUserEmail}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = { state.declineTermsOfService() }) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
            )
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
          ),
      )
    },
    bottomBar = {
      Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          OutlinedButton(
            onClick = { state.declineTermsOfService() },
            modifier = Modifier.weight(0.42f),
            shape = MaterialTheme.shapes.medium,
          ) {
            Text("Decline")
          }
          Button(
            onClick = { state.acceptTermsOfService() },
            enabled = state.termsCheckboxChecked,
            modifier = Modifier.weight(0.58f),
            shape = MaterialTheme.shapes.medium,
          ) {
            Text(
              text = "Agree & Continue",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }
    },
    containerColor = MaterialTheme.colorScheme.surface,
  ) { innerPadding ->
    // Scrollable Terms of Service Body
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(innerPadding)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 18.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Text(
        text = "Please review and accept the Ground Terms of Service before downloading surveys.",
        style = MaterialTheme.typography.bodyMedium,
        color = onSurfaceColor,
        fontWeight = FontWeight.SemiBold,
      )

      OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors =
          CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
          ),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          TermsSectionItem(
            number = "1",
            title = "Survey Data Collection & Sharing",
            body =
              "Field observations, GPS coordinates, geometries, and photos collected in Ground are shared with the survey organizers who granted you access to each survey.",
          )
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          TermsSectionItem(
            number = "2",
            title = "Offline Storage & Synchronization",
            body =
              "Downloaded survey definitions and map tiles are cached locally on your device for offline field work and automatically synchronized when connectivity is restored.",
          )
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          TermsSectionItem(
            number = "3",
            title = "Location & Sensor Permissions",
            body =
              "Ground uses device location services when capturing points, polygons, or transects during active data collection tasks.",
          )
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          TermsSectionItem(
            number = "4",
            title = "Privacy & Responsible Use",
            body =
              "Do not record sensitive personal data unless explicitly authorized and consented to under your organization's survey governance protocol.",
          )
        }
      }

      // Checkbox row
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { state.setTermsChecked(!state.termsCheckboxChecked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Checkbox(
          checked = state.termsCheckboxChecked,
          onCheckedChange = { state.setTermsChecked(it) },
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = "I have read and agree to the Ground Terms of Service.",
          style = MaterialTheme.typography.bodySmall,
          color = onSurfaceColor,
        )
      }
    }
  }
}

@Composable
private fun TermsSectionItem(
  number: String,
  title: String,
  body: String,
) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
      text = "$number. $title",
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.primary,
      fontWeight = FontWeight.Bold,
    )
    Text(
      text = body,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      lineHeight = 18.sp,
    )
  }
}

/**
 * 4. "Download survey" screen where users can see a list of all surveys shared with them, or search
 * by name or location by typing in the search bar. Each survey item includes a title, description,
 * map thumbnail, and an indicator for surveys that have already been downloaded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroundDownloadSurveyScreen(state: PrototypeAppState) {
  val onSurfaceColor = MaterialTheme.colorScheme.onSurface
  val filtered = state.filteredSurveys

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = "Download survey",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text =
                "${state.surveys.size} shared with you • ${state.downloadedSurveyCount} downloaded",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = { state.navigateBackFromDownloadSurvey() }) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
            )
          }
        },
        actions = {
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.padding(end = 12.dp),
          ) {
            Text(
              text = "ML",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            )
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
          ),
      )
    },
    containerColor = MaterialTheme.colorScheme.surface,
  ) { innerPadding ->
    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      // Search Bar for filtering by survey name or location
      Box(
        modifier =
          Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
      ) {
        OutlinedTextField(
          value = state.searchQuery,
          onValueChange = { state.updateSearchQuery(it) },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          placeholder = {
            Text(
              text = "Search by name or location...",
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
            if (state.searchQuery.isNotEmpty()) {
              IconButton(onClick = { state.clearSearchQuery() }) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = "Clear Search",
                  modifier = Modifier.size(18.dp),
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
      }

      // Optional feedback toast banner when downloading/toggling a survey
      state.activeSurveyNotice?.let { notice ->
        Surface(
          color = MaterialTheme.colorScheme.secondaryContainer,
          contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Default.CheckCircle,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(15.dp),
            )
            Text(
              text = notice,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.weight(1f),
            )
          }
        }
      }

      // Section Summary Header
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text =
            if (state.searchQuery.isBlank()) {
              "SURVEYS SHARED WITH YOU (${filtered.size})"
            } else {
              "MATCHING SURVEYS (${filtered.size} OF ${state.surveys.size})"
            },
          style =
            MaterialTheme.typography.labelSmall.copy(
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              letterSpacing = 0.6.sp,
            ),
        )
        if (state.searchQuery.isNotBlank()) {
          TextButton(onClick = { state.clearSearchQuery() }) {
            Text(
              text = "Clear search",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      }

      // Survey Items List
      if (filtered.isEmpty()) {
        Box(
          modifier = Modifier.fillMaxSize().padding(32.dp),
          contentAlignment = Alignment.Center,
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Default.Map,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(36.dp),
            )
            Text(
              text = "No surveys match \"${state.searchQuery}\"",
              style = MaterialTheme.typography.titleSmall,
              color = onSurfaceColor,
              fontWeight = FontWeight.Bold,
              textAlign = TextAlign.Center,
            )
            Text(
              text =
                "Try searching by another survey title, keyword, or location (e.g. Brazil, Kenya, Vietnam).",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
            )
          }
        }
      } else {
        Column(
          modifier =
            Modifier.weight(1f)
              .fillMaxWidth()
              .verticalScroll(rememberScrollState())
              .padding(horizontal = 14.dp, vertical = 4.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          filtered.forEach { survey ->
            SurveyListItemCard(
              survey = survey,
              isDarkTheme = state.isDarkTheme,
              onDownloadClick = { state.downloadSurvey(survey.id) },
              onToggleDownloadClick = { state.toggleSurveyDownloaded(survey.id) },
              onOpenSurveyClick = { state.openSurvey(survey.id) },
            )
          }
          Spacer(modifier = Modifier.height(12.dp))
        }
      }
    }

    if (state.isDownloadSurveySignOutPromptOpen) {
      DownloadSurveySignOutPromptDialog(state)
    }
  }
}

/**
 * Confirmation prompt dialog shown when the user taps Back on the Download surveys screen after
 * accepting the Terms of Service, confirming before signing the user out.
 */
@Composable
private fun DownloadSurveySignOutPromptDialog(state: PrototypeAppState) {
  GroundAlertDialogOverlay(
    onDismissRequest = { state.dismissDownloadSurveySignOutPrompt() },
    icon = {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.Logout,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
      )
    },
    title = {
      Text(
        text = "Sign out?",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
    },
    text = {
      Text(
        text =
          "Going back will sign out ${state.signedInUserEmail} and return to the Sign In screen.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    },
    confirmButton = {
      Button(
        onClick = { state.confirmDownloadSurveySignOut() },
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
      ) {
        Text("Sign out", fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      OutlinedButton(onClick = { state.dismissDownloadSurveySignOutPrompt() }) {
        Text("Cancel")
      }
    },
  )
}

/**
 * Individual survey card showing:
 * - Map thumbnail placeholder (`SurveyMapThumbnail`)
 * - Survey title
 * - Survey location & coordinates
 * - Survey description
 * - Indicator badge on surveys that have already been downloaded (`Downloaded`) or a Download CTA
 * - Action to open the survey in the Main Survey UI (`Open`)
 */
@Composable
private fun SurveyListItemCard(
  survey: SurveyPreviewItem,
  isDarkTheme: Boolean,
  onDownloadClick: () -> Unit,
  onToggleDownloadClick: () -> Unit,
  onOpenSurveyClick: () -> Unit,
) {
  val cardBg =
    if (survey.isDownloaded) {
      MaterialTheme.colorScheme.surfaceContainerLow
    } else {
      MaterialTheme.colorScheme.surface
    }
  val borderColor =
    if (survey.isDownloaded) {
      MaterialTheme.colorScheme.primary
    } else {
      MaterialTheme.colorScheme.outlineVariant
    }

  OutlinedCard(
    onClick = onOpenSurveyClick,
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.outlinedCardColors(containerColor = cardBg),
    border =
      BorderStroke(
        width = if (survey.isDownloaded) 1.5.dp else 1.dp,
        color = borderColor,
      ),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
      ) {
        // Map Thumbnail Placeholder (80x80 dp)
        SurveyMapThumbnail(
          theme = survey.thumbnailTheme,
          isDownloaded = survey.isDownloaded,
        )

        // Survey Metadata (Title, Location, Description)
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            text = survey.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
          ) {
            Icon(
              imageVector = Icons.Default.LocationOn,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(12.dp),
            )
            Text(
              text = survey.location,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = "• ${survey.coordinatesLabel}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          Text(
            text = survey.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      // Bottom Footer: Downloaded status indicator + Open Survey CTA or Download action
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "${survey.entityCount} locations • ${survey.offlineSizeLabel}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (survey.isDownloaded) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            AssistChip(
              onClick = onToggleDownloadClick,
              leadingIcon = {
                Icon(
                  imageVector = Icons.Default.Check,
                  contentDescription = null,
                  modifier = Modifier.size(14.dp),
                )
              },
              label = {
                Text(
                  text = "Downloaded",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                )
              },
              modifier = Modifier.height(30.dp),
            )

            FilledTonalButton(
              onClick = onOpenSurveyClick,
              modifier = Modifier.height(30.dp),
              contentPadding = ButtonDefaults.TextButtonContentPadding,
            ) {
              Text(
                text = "Open",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
              )
              Spacer(modifier = Modifier.width(3.dp))
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
              )
            }
          }
        } else {
          Button(
            onClick = onDownloadClick,
            modifier = Modifier.height(32.dp),
            contentPadding = ButtonDefaults.TextButtonContentPadding,
          ) {
            Icon(
              imageVector = Icons.Default.Download,
              contentDescription = null,
              modifier = Modifier.size(13.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "Download",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }
    }
  }
}

/** Stylized map thumbnail placeholder rendered with Compose Canvas for each survey card. */
@Composable
private fun SurveyMapThumbnail(
  theme: MapThumbnailTheme,
  isDownloaded: Boolean,
) {
  Box(
    modifier =
      Modifier.size(80.dp)
        .clip(MaterialTheme.shapes.medium)
        .background(Color(theme.primaryTerrainHex))
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      // Subtle map grid lines
      val gridStep = size.width / 3f
      for (i in 1..2) {
        drawLine(
          color = Color.White.copy(alpha = 0.14f),
          start = Offset(gridStep * i, 0f),
          end = Offset(gridStep * i, size.height),
          strokeWidth = 1f,
        )
        drawLine(
          color = Color.White.copy(alpha = 0.14f),
          start = Offset(0f, gridStep * i),
          end = Offset(size.width, gridStep * i),
          strokeWidth = 1f,
        )
      }

      // Waterway / contour band
      val waterPath =
        Path().apply {
          moveTo(0f, size.height * 0.78f)
          quadraticTo(
            size.width * 0.45f,
            size.height * 0.48f,
            size.width,
            size.height * 0.22f,
          )
        }
      drawPath(
        path = waterPath,
        color = Color(theme.secondaryWaterHex),
        style = Stroke(width = 8f),
      )

      // Survey ROI Polygon
      drawRect(
        color = Color(theme.accentPolygonHex).copy(alpha = 0.35f),
        topLeft = Offset(size.width * 0.18f, size.height * 0.18f),
        size = Size(size.width * 0.56f, size.height * 0.48f),
      )
      drawRect(
        color = Color(theme.accentPolygonHex),
        topLeft = Offset(size.width * 0.18f, size.height * 0.18f),
        size = Size(size.width * 0.56f, size.height * 0.48f),
        style = Stroke(width = 2f),
      )

      // Center map pin dot
      drawCircle(
        color = Color.White,
        radius = 5f,
        center = Offset(size.width * 0.46f, size.height * 0.42f),
      )
      drawCircle(
        color = Color(0xFFD32F2F),
        radius = 3.2f,
        center = Offset(size.width * 0.46f, size.height * 0.42f),
      )
    }

    // Bottom Map Layer Badge
    Surface(
      color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f),
      contentColor = Color.White,
      shape = MaterialTheme.shapes.extraSmall,
      modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
    ) {
      Text(
        text = theme.badgeLabel,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
      )
    }

    // Top-right mini offline check icon on map thumbnail when downloaded
    if (isDownloaded) {
      Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        border = BorderStroke(1.dp, Color.White),
        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Downloaded",
            modifier = Modifier.size(11.dp),
          )
        }
      }
    }
  }
}

/** Right-hand UX Designer Co-Design & Flow Inspector panel in the Web Prototype App. */
@Composable
private fun UxDesignerInspectorPanel(
  state: PrototypeAppState,
  modifier: Modifier = Modifier,
) {
  ElevatedCard(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    colors =
      CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.surface
      ),
    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
  ) {
    Column(
      modifier =
        Modifier.fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(20.dp),
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
      XFormsFormDefChromeSection(state)

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
              GroundTonalBadge(
                text = "ACTIVE",
                tone = GroundBadgeTone.PRIMARY,
              )
            }
          }
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      // 2. Main Survey UI Quick State Shortcuts (Map, List, Layers, 1:1 vs 1:N, Drawer)
      Text(
        text = "2. MAIN SURVEY UI QUICK INSPECTOR (STEP 5)",
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
        val mainShortcuts: List<Triple<ImageVector, String, () -> Unit>> =
          listOf(
            Triple(
              Icons.Default.Map,
              "Map + Coffee Parcel (1:1)",
              {
                state.navigateTo(PrototypeScreen.MAIN_SURVEY)
                state.setMainSurveyViewMode(MainSurveyViewMode.MAP)
                state.selectEntity("entity-nyr-104")
              },
            ),
            Triple(
              Icons.Default.Timeline,
              "Map + Shade Plot (1:N)",
              {
                state.navigateTo(PrototypeScreen.MAIN_SURVEY)
                state.setMainSurveyViewMode(MainSurveyViewMode.MAP)
                state.selectEntity("entity-shade-201")
              },
            ),
            Triple(
              Icons.Default.Navigation,
              "Navigate to Site",
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
              Icons.Default.Settings,
              "Settings Screen",
              {
                state.navigateTo(PrototypeScreen.MAIN_SURVEY)
                state.drawerOpenSettings()
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
              Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
              )
            },
            label = {
              Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
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
