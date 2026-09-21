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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.groundplatform.v2.core.forms.ui.GroundTheme

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
      color = if (isMapShowing) Color.Transparent else Color(0xFFF0F4F1),
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        PrototypeWorkbenchTopBar(state)

        Row(
          modifier = Modifier.fillMaxSize().padding(16.dp),
          horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
          val previewStageWeight =
            if (state.deviceFormFactor == DeviceFormFactor.TABLET) 1.55f else 1.15f

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
              onSelectFormFactor = { state.selectDeviceFormFactor(it) },
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
  Surface(
    modifier = Modifier.fillMaxWidth(),
    color = Color(0xFF133A29),
    shadowElevation = 4.dp,
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 12.dp)
          .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Brand & Tool Title
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Box(
          modifier =
            Modifier.size(36.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(Color(0xFF2E7D32)),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "G",
            style =
              MaterialTheme.typography.titleMedium.copy(
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
              ),
          )
        }
        Column {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = "Ground 2.0 Mobile UI Prototype",
              style =
                MaterialTheme.typography.titleMedium.copy(
                  color = Color.White,
                  fontWeight = FontWeight.Bold,
                ),
            )
            Box(
              modifier =
                Modifier.clip(RoundedCornerShape(6.dp))
                  .background(Color(0xFF1E6F50))
                  .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
              Text(
                text = "devtools/prototypeApp",
                style =
                  MaterialTheme.typography.labelSmall.copy(
                    color = Color(0xFFC8E6C9),
                    fontFamily = FontFamily.Monospace,
                  ),
              )
            }
          }
          Text(
            text = "Compose Multiplatform Mobile & Tablet UI Preview & UX Co-Design Workbench",
            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFA7C4B5)),
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
          Box(
            modifier =
              Modifier.clip(RoundedCornerShape(20.dp))
                .background(if (isSelected) Color(0xFF8BD6B1) else Color(0xFF1F4E39))
                .border(
                  width = 1.dp,
                  color = if (isSelected) Color(0xFF8BD6B1) else Color(0xFF386B52),
                  shape = RoundedCornerShape(20.dp),
                )
                .clickable { state.navigateTo(screen) }
                .padding(horizontal = 12.dp, vertical = 6.dp)
          ) {
            Text(
              text = "${screen.stepNumber}. ${screen.title}",
              style =
                MaterialTheme.typography.labelMedium.copy(
                  color = if (isSelected) Color(0xFF003825) else Color.White,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                ),
            )
          }
        }

        // Segmented Form Factor Toggle: Mobile | Tablet
        Row(
          modifier =
            Modifier.clip(RoundedCornerShape(10.dp))
              .background(Color(0xFF194230))
              .border(1.dp, Color(0xFF386B52), RoundedCornerShape(10.dp))
              .padding(2.dp),
          horizontalArrangement = Arrangement.spacedBy(2.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          DeviceFormFactor.entries.forEach { factor ->
            val selected = state.deviceFormFactor == factor
            val icon =
              if (factor == DeviceFormFactor.MOBILE) {
                Icons.Default.Smartphone
              } else {
                Icons.Default.Tablet
              }
            Row(
              modifier =
                Modifier.clip(RoundedCornerShape(8.dp))
                  .background(if (selected) Color(0xFF8BD6B1) else Color.Transparent)
                  .clickable { state.selectDeviceFormFactor(factor) }
                  .padding(horizontal = 10.dp, vertical = 5.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = icon,
                contentDescription = factor.label,
                tint = if (selected) Color(0xFF003825) else Color.White,
                modifier = Modifier.size(14.dp),
              )
              Text(
                text = factor.label,
                maxLines = 1,
                softWrap = false,
                style =
                  MaterialTheme.typography.labelMedium.copy(
                    color = if (selected) Color(0xFF003825) else Color.White,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                  ),
              )
            }
          }
        }

        // Theme toggle button
        Row(
          modifier =
            Modifier.clip(RoundedCornerShape(8.dp))
              .background(Color(0xFF245840))
              .clickable { state.toggleDarkTheme() }
              .padding(horizontal = 10.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          Icon(
            imageVector = if (state.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = if (state.isDarkTheme) "Light UI" else "Dark UI",
            maxLines = 1,
            softWrap = false,
            style =
              MaterialTheme.typography.labelMedium.copy(
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
              ),
          )
        }

        // Reset Flow button
        Row(
          modifier =
            Modifier.clip(RoundedCornerShape(8.dp))
              .background(Color(0xFF374151))
              .clickable { state.resetPrototypeFlow() }
              .padding(horizontal = 10.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = "Reset Flow",
            maxLines = 1,
            softWrap = false,
            style =
              MaterialTheme.typography.labelMedium.copy(
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
              ),
          )
        }
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
  onSelectFormFactor: (DeviceFormFactor) -> Unit = {},
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    // Stage Header with Device Title + Inline Mobile / Tablet Form Factor Toggle
    Row(
      modifier = Modifier.width(formFactor.frameWidthDp.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
        Text(
          text = deviceTitle,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          style =
            MaterialTheme.typography.labelMedium.copy(
              fontWeight = FontWeight.Bold,
              color = Color(0xFF144532),
            ),
        )
        Text(
          text = "Viewport: ${formFactor.label} (${formFactor.dimensionsLabel})",
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          style =
            MaterialTheme.typography.labelSmall.copy(
              color = Color(0xFF4B5563),
              fontFamily = FontFamily.Monospace,
            ),
        )
      }

      // Segmented Form Factor Pill Switcher right above the device frame
      Row(
        modifier =
          Modifier.clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFE2ECE6))
            .border(1.dp, Color(0xFFB7D1C3), RoundedCornerShape(10.dp))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        DeviceFormFactor.entries.forEach { factor ->
          val isSelected = formFactor == factor
          val icon =
            if (factor == DeviceFormFactor.MOBILE) {
              Icons.Default.Smartphone
            } else {
              Icons.Default.Tablet
            }
          Row(
            modifier =
              Modifier.clip(RoundedCornerShape(8.dp))
                .background(if (isSelected) Color(0xFF1E6F50) else Color.Transparent)
                .clickable { onSelectFormFactor(factor) }
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = icon,
              contentDescription = factor.label,
              tint = if (isSelected) Color.White else Color(0xFF1F2937),
              modifier = Modifier.size(13.dp),
            )
            Text(
              text = factor.label,
              maxLines = 1,
              softWrap = false,
              style =
                MaterialTheme.typography.labelSmall.copy(
                  color = if (isSelected) Color.White else Color(0xFF1F2937),
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                ),
            )
          }
        }
      }
    }

    // Outer Device Hardware Bezel (dynamically sized for Mobile vs Tablet)
    val outerShape = RoundedCornerShape(formFactor.outerCornerRadiusDp.dp)
    val innerShape = RoundedCornerShape(formFactor.innerCornerRadiusDp.dp)
    Box(
      modifier =
        Modifier.width(formFactor.frameWidthDp.dp)
          .height(formFactor.frameHeightDp.dp)
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
              when {
                isScreenTransparent -> Color.Transparent
                isDarkTheme -> Color(0xFF191C1A)
                else -> Color(0xFFFBFDF9)
              }
            )
      ) {
        // Device Status Bar
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .background(Color(0xFF0E3A28))
              .padding(horizontal = 20.dp, vertical = 7.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text =
              if (formFactor == DeviceFormFactor.TABLET) {
                "09:41 • Wed Sep 19"
              } else {
                "09:41"
              },
            style =
              MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White,
              ),
          )
          // Camera punch-hole notch (Mobile) or slim landscape bezel sensor (Tablet)
          if (formFactor == DeviceFormFactor.MOBILE) {
            Box(
              modifier =
                Modifier.width(68.dp).height(11.dp).clip(CircleShape).background(Color(0xFF061B12))
            )
          } else {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Box(
                modifier =
                  Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF061B12))
              )
              Text(
                text = "FIELD TABLET • HIGH-PRECISION GNSS",
                style =
                  MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    color = Color(0xFF8BD6B1),
                    fontWeight = FontWeight.Bold,
                  ),
              )
            }
          }
          Text(
            text = "5G • 100%",
            style =
              MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
              ),
          )
        }

        // Embedded Compose Multiplatform Screen Content
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }

        // Bottom Gesture Navigation Bar
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .background(if (isDarkTheme) Color(0xFF111412) else Color.White)
              .padding(vertical = 7.dp),
          contentAlignment = Alignment.Center,
        ) {
          Box(
            modifier =
              Modifier.width(if (formFactor == DeviceFormFactor.TABLET) 160.dp else 116.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(Color(0xFF9CA3AF))
          )
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

/** 1. Placeholder Splash / Loading screen for Ground. */
@Composable
fun GroundSplashScreen(state: PrototypeAppState) {
  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(Color(0xFF144532))
        .padding(horizontal = 28.dp, vertical = 32.dp)
  ) {
    Column(
      modifier = Modifier.align(Alignment.Center),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // Ground Emblem Badge
      Box(
        modifier =
          Modifier.size(96.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF1E6F50))
            .border(2.dp, Color(0xFF8BD6B1), RoundedCornerShape(28.dp)),
        contentAlignment = Alignment.Center,
      ) {
        Canvas(modifier = Modifier.size(56.dp)) {
          // Stylized terrain horizon lines + location pin
          drawCircle(
            color = Color(0xFF8BD6B1),
            radius = size.minDimension * 0.28f,
            center = Offset(size.width * 0.5f, size.height * 0.38f),
            style = Stroke(width = 5f),
          )
          drawCircle(
            color = Color.White,
            radius = size.minDimension * 0.10f,
            center = Offset(size.width * 0.5f, size.height * 0.38f),
          )
          val hillPath =
            Path().apply {
              moveTo(size.width * 0.12f, size.height * 0.82f)
              quadraticTo(
                size.width * 0.36f,
                size.height * 0.56f,
                size.width * 0.62f,
                size.height * 0.78f,
              )
              quadraticTo(
                size.width * 0.78f,
                size.height * 0.64f,
                size.width * 0.90f,
                size.height * 0.82f,
              )
            }
          drawPath(path = hillPath, color = Color(0xFFA5D6A7), style = Stroke(width = 4.5f))
        }
      }

      Text(
        text = "Ground",
        style =
          MaterialTheme.typography.headlineLarge.copy(
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp,
          ),
      )

      Text(
        text = "Community-centered geospatial data collection",
        style =
          MaterialTheme.typography.bodyMedium.copy(
            color = Color(0xFFC8E6C9),
            textAlign = TextAlign.Center,
          ),
      )

      Spacer(modifier = Modifier.height(20.dp))

      // Loading progress bar & status label
      Column(
        modifier = Modifier.fillMaxWidth(0.82f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        LinearProgressIndicator(
          modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
          color = Color(0xFF8BD6B1),
          trackColor = Color(0xFF235C44),
        )
        Text(
          text = "Initializing offline map engine & workspace...",
          style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFA7C4B5)),
          textAlign = TextAlign.Center,
        )
      }
    }

    // Bottom interactive prototype trigger to advance from Splash -> Sign In
    Column(
      modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Button(
        onClick = { state.completeSplashLoading() },
        modifier = Modifier.fillMaxWidth(),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = Color(0xFF8BD6B1),
            contentColor = Color(0xFF003825),
          ),
        shape = RoundedCornerShape(14.dp),
      ) {
        Text(
          text = "Continue to Sign In",
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowForward,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
        )
      }
      Text(
        text = "Placeholder Splash / Loading Screen • Tap to advance",
        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF88B39E)),
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
      Box(
        modifier =
          Modifier.size(60.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF1E6F50)),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = "G",
          style =
            MaterialTheme.typography.headlineMedium.copy(
              color = Color.White,
              fontWeight = FontWeight.ExtraBold,
            ),
        )
      }
      Text(
        text = "Welcome to Ground",
        style =
          MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.Bold,
            color = onSurfaceColor,
          ),
      )
      Text(
        text =
          "Collect field observations, map boundaries, and synchronize survey records offline.",
        style =
          MaterialTheme.typography.bodyMedium.copy(
            color = onSurfaceColor.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
          ),
      )
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Center Field Survey Illustration Card
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(20.dp),
      colors =
        CardDefaults.cardColors(
          containerColor =
            if (state.isDarkTheme) Color(0xFF22302A) else Color(0xFFEAF3EE)
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
              .clip(RoundedCornerShape(14.dp))
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

          Row(
            modifier =
              Modifier.align(Alignment.BottomStart)
                .padding(10.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xCC0D3B10))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = Icons.Default.LocationOn,
              contentDescription = null,
              tint = Color(0xFF8BD6B1),
              modifier = Modifier.size(13.dp),
            )
            Text(
              text = "Offline Satellite & Vector Layers Ready",
              style = MaterialTheme.typography.labelSmall.copy(color = Color.White),
            )
          }
        }

        Text(
          text = "Sign in to access surveys shared with your account.",
          style =
            MaterialTheme.typography.bodySmall.copy(
              color = onSurfaceColor.copy(alpha = 0.8f),
              textAlign = TextAlign.Center,
            ),
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

      Button(
        onClick = { state.signInWithGoogle() },
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(26.dp),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color(0xFF1F1F1F),
          ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          // Google 'G' Badge
          Box(
            modifier =
              Modifier.size(26.dp)
                .clip(CircleShape)
                .background(Color(0xFFF1F3F4))
                .border(1.dp, Color(0xFFDADCE0), CircleShape),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "G",
              style =
                MaterialTheme.typography.titleSmall.copy(
                  color = Color(0xFF4285F4),
                  fontWeight = FontWeight.ExtraBold,
                ),
            )
          }
          Text(
            text = strings.signInWithGoogle,
            style =
              MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1F1F1F),
              ),
          )
        }
      }

      Text(
        text = "Authentication is currently limited to Google Accounts.",
        style =
          MaterialTheme.typography.labelSmall.copy(
            color = onSurfaceColor.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
          ),
      )
    }
  }
}

/** 3. Terms of Service screen. */
@Composable
fun GroundTermsOfServiceScreen(state: PrototypeAppState) {
  val surfaceColor = MaterialTheme.colorScheme.surface
  val onSurfaceColor = MaterialTheme.colorScheme.onSurface

  Column(modifier = Modifier.fillMaxSize().background(surfaceColor)) {
    // Top App Bar
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .background(Color(0xFF1E6F50))
          .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier =
          Modifier.clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF144D37))
            .clickable { state.declineTermsOfService() }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(14.dp),
        )
        Text(
          text = "Back",
          style =
            MaterialTheme.typography.labelMedium.copy(
              color = Color.White,
              fontWeight = FontWeight.SemiBold,
            ),
        )
      }
      Column {
        Text(
          text = "Terms of Service",
          style =
            MaterialTheme.typography.titleMedium.copy(
              color = Color.White,
              fontWeight = FontWeight.Bold,
            ),
        )
        Text(
          text = "Signed in as ${state.signedInUserEmail}",
          style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFC8E6C9)),
        )
      }
    }

    // Scrollable Terms of Service Body
    Column(
      modifier =
        Modifier.weight(1f)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 18.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Text(
        text = "Please review and accept the Ground Terms of Service before downloading surveys.",
        style =
          MaterialTheme.typography.bodyMedium.copy(
            fontWeight = FontWeight.SemiBold,
            color = onSurfaceColor,
          ),
      )

      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors =
          CardDefaults.cardColors(
            containerColor =
              if (state.isDarkTheme) Color(0xFF232B27) else Color(0xFFF3F7F4)
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
            onSurfaceColor = onSurfaceColor,
          )
          HorizontalDivider(color = onSurfaceColor.copy(alpha = 0.1f))
          TermsSectionItem(
            number = "2",
            title = "Offline Storage & Synchronization",
            body =
              "Downloaded survey definitions and map tiles are cached locally on your device for offline field work and automatically synchronized when connectivity is restored.",
            onSurfaceColor = onSurfaceColor,
          )
          HorizontalDivider(color = onSurfaceColor.copy(alpha = 0.1f))
          TermsSectionItem(
            number = "3",
            title = "Location & Sensor Permissions",
            body =
              "Ground uses device location services when capturing points, polygons, or transects during active data collection tasks.",
            onSurfaceColor = onSurfaceColor,
          )
          HorizontalDivider(color = onSurfaceColor.copy(alpha = 0.1f))
          TermsSectionItem(
            number = "4",
            title = "Privacy & Responsible Use",
            body =
              "Do not record sensitive personal data unless explicitly authorized and consented to under your organization's survey governance protocol.",
            onSurfaceColor = onSurfaceColor,
          )
        }
      }

      // Checkbox row
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { state.setTermsChecked(!state.termsCheckboxChecked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Checkbox(
          checked = state.termsCheckboxChecked,
          onCheckedChange = { state.setTermsChecked(it) },
          colors =
            CheckboxDefaults.colors(
              checkedColor = Color(0xFF1E6F50),
              checkmarkColor = Color.White,
            ),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = "I have read and agree to the Ground Terms of Service.",
          style = MaterialTheme.typography.bodySmall.copy(color = onSurfaceColor),
        )
      }
    }

    // Bottom Sticky Action Bar
    Surface(
      modifier = Modifier.fillMaxWidth(),
      color = if (state.isDarkTheme) Color(0xFF1E2421) else Color.White,
      shadowElevation = 6.dp,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedButton(
          onClick = { state.declineTermsOfService() },
          modifier = Modifier.weight(0.42f),
          shape = RoundedCornerShape(12.dp),
        ) {
          Text("Decline")
        }
        Button(
          onClick = { state.acceptTermsOfService() },
          enabled = state.termsCheckboxChecked,
          modifier = Modifier.weight(0.58f),
          shape = RoundedCornerShape(12.dp),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = Color(0xFF1E6F50),
              contentColor = Color.White,
            ),
        ) {
          Text(
            text = "Agree & Continue",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
          )
        }
      }
    }
  }
}

@Composable
private fun TermsSectionItem(
  number: String,
  title: String,
  body: String,
  onSurfaceColor: Color,
) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
      text = "$number. $title",
      style =
        MaterialTheme.typography.labelLarge.copy(
          fontWeight = FontWeight.Bold,
          color = Color(0xFF1E6F50),
        ),
    )
    Text(
      text = body,
      style =
        MaterialTheme.typography.bodySmall.copy(
          color = onSurfaceColor.copy(alpha = 0.82f),
          lineHeight = 18.sp,
        ),
    )
  }
}

/**
 * 4. "Download survey" screen where users can see a list of all surveys shared with them, or search
 * by name or location by typing in the search bar. Each survey item includes a title, description,
 * map thumbnail, and an indicator for surveys that have already been downloaded.
 */
@Composable
fun GroundDownloadSurveyScreen(state: PrototypeAppState) {
  val surfaceColor = MaterialTheme.colorScheme.surface
  val onSurfaceColor = MaterialTheme.colorScheme.onSurface
  val filtered = state.filteredSurveys

  Column(modifier = Modifier.fillMaxSize().background(surfaceColor)) {
    // Top Header + Search Bar Container
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .background(Color(0xFF1E6F50))
          .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(
            text = "Download survey",
            style =
              MaterialTheme.typography.titleMedium.copy(
                color = Color.White,
                fontWeight = FontWeight.Bold,
              ),
          )
          Text(
            text = "${state.surveys.size} shared with you • ${state.downloadedSurveyCount} downloaded",
            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFC8E6C9)),
          )
        }

        // User Avatar Pill
        Box(
          modifier =
            Modifier.clip(CircleShape)
              .background(Color(0xFF124531))
              .border(1.dp, Color(0xFF8BD6B1), CircleShape)
              .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
          Text(
            text = "ML",
            style =
              MaterialTheme.typography.labelSmall.copy(
                color = Color.White,
                fontWeight = FontWeight.Bold,
              ),
          )
        }
      }

      // Search Bar for filtering by survey name or location
      OutlinedTextField(
        value = state.searchQuery,
        onValueChange = { state.updateSearchQuery(it) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = {
          Text(
            text = "Search by name or location...",
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
          if (state.searchQuery.isNotEmpty()) {
            Box(
              modifier =
                Modifier.clip(CircleShape)
                  .clickable { state.clearSearchQuery() }
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
        shape = RoundedCornerShape(24.dp),
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedTextColor = Color(0xFF111827),
            unfocusedTextColor = Color(0xFF111827),
            focusedBorderColor = Color(0xFF8BD6B1),
            unfocusedBorderColor = Color.Transparent,
          ),
      )
    }

    // Optional feedback toast banner when downloading/toggling a survey
    state.activeSurveyNotice?.let { notice ->
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .background(Color(0xFFE8F5E9))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = Icons.Default.CheckCircle,
          contentDescription = null,
          tint = Color(0xFF1B5E20),
          modifier = Modifier.size(14.dp),
        )
        Text(
          text = notice,
          style =
            MaterialTheme.typography.labelSmall.copy(
              color = Color(0xFF1B5E20),
              fontWeight = FontWeight.SemiBold,
            ),
          modifier = Modifier.weight(1f),
        )
      }
    }

    // Section Summary Header
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 10.dp),
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
            color = onSurfaceColor.copy(alpha = 0.65f),
            letterSpacing = 0.6.sp,
          ),
      )
      if (state.searchQuery.isNotBlank()) {
        Text(
          text = "Clear search",
          style =
            MaterialTheme.typography.labelSmall.copy(
              fontWeight = FontWeight.SemiBold,
              color = Color(0xFF1E6F50),
            ),
          modifier = Modifier.clickable { state.clearSearchQuery() },
        )
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
            tint = Color(0xFF1E6F50),
            modifier = Modifier.size(36.dp),
          )
          Text(
            text = "No surveys match \"${state.searchQuery}\"",
            style =
              MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                color = onSurfaceColor,
              ),
            textAlign = TextAlign.Center,
          )
          Text(
            text = "Try searching by another survey title, keyword, or location (e.g. Brazil, Kenya, Vietnam).",
            style =
              MaterialTheme.typography.bodySmall.copy(
                color = onSurfaceColor.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
              ),
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
    when {
      isDarkTheme && survey.isDownloaded -> Color(0xFF1F2E26)
      isDarkTheme -> Color(0xFF232725)
      survey.isDownloaded -> Color(0xFFF4FAF6)
      else -> Color.White
    }
  val borderColor =
    when {
      survey.isDownloaded -> Color(0xFF2E7D32)
      isDarkTheme -> Color(0xFF374151)
      else -> Color(0xFFDDE5E0)
    }
  val textColor = if (isDarkTheme) Color.White else Color(0xFF111827)

  Card(
    modifier =
      Modifier.fillMaxWidth()
        .border(
          width = if (survey.isDownloaded) 1.5.dp else 1.dp,
          color = borderColor,
          shape = RoundedCornerShape(16.dp),
        )
        .clickable { onOpenSurveyClick() },
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = cardBg),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
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
        // Map Thumbnail Placeholder (82x82 dp)
        SurveyMapThumbnail(
          theme = survey.thumbnailTheme,
          isDownloaded = survey.isDownloaded,
        )

        // Survey Metadata (Title, Location, Description)
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          // Title + Downloaded Corner Indicator
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
          ) {
            Text(
              text = survey.title,
              style =
                MaterialTheme.typography.titleSmall.copy(
                  fontWeight = FontWeight.Bold,
                  color = textColor,
                ),
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
          }

          // Location tag
          val locationColor = if (isDarkTheme) Color(0xFF8BD6B1) else Color(0xFF1E6F50)
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
          ) {
            Icon(
              imageVector = Icons.Default.LocationOn,
              contentDescription = null,
              tint = locationColor,
              modifier = Modifier.size(12.dp),
            )
            Text(
              text = survey.location,
              style =
                MaterialTheme.typography.labelSmall.copy(
                  fontWeight = FontWeight.SemiBold,
                  color = locationColor,
                ),
            )
            Text(
              text = "• ${survey.coordinatesLabel}",
              style =
                MaterialTheme.typography.labelSmall.copy(
                  color = textColor.copy(alpha = 0.55f),
                ),
            )
          }

          // Description
          Text(
            text = survey.description,
            style =
              MaterialTheme.typography.bodySmall.copy(
                color = textColor.copy(alpha = 0.78f),
                lineHeight = 16.sp,
              ),
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
          style =
            MaterialTheme.typography.labelSmall.copy(
              color = textColor.copy(alpha = 0.6f),
            ),
        )

        if (survey.isDownloaded) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            // Downloaded Indicator Pill
            Row(
              modifier =
                Modifier.clip(RoundedCornerShape(20.dp))
                  .background(Color(0xFFE8F5E9))
                  .border(1.dp, Color(0xFF2E7D32), RoundedCornerShape(20.dp))
                  .clickable { onToggleDownloadClick() }
                  .padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color(0xFF1B5E20),
                modifier = Modifier.size(12.dp),
              )
              Text(
                text = "Downloaded",
                style =
                  MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B5E20),
                  ),
              )
            }
            // Open Survey Button
            Row(
              modifier =
                Modifier.clip(RoundedCornerShape(20.dp))
                  .background(Color(0xFF1E6F50))
                  .clickable { onOpenSurveyClick() }
                  .padding(horizontal = 10.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
              Text(
                text = "Open",
                style =
                  MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                  ),
              )
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp),
              )
            }
          }
        } else {
          // Download Button
          Row(
            modifier =
              Modifier.clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1E6F50))
                .clickable { onDownloadClick() }
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = Icons.Default.Download,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(13.dp),
            )
            Text(
              text = "Download",
              style =
                MaterialTheme.typography.labelSmall.copy(
                  fontWeight = FontWeight.Bold,
                  color = Color.White,
                ),
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
        .clip(RoundedCornerShape(12.dp))
        .background(Color(theme.primaryTerrainHex))
        .border(1.dp, Color.Black.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
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
    Box(
      modifier =
        Modifier.align(Alignment.BottomStart)
          .padding(4.dp)
          .clip(RoundedCornerShape(4.dp))
          .background(Color.Black.copy(alpha = 0.55f))
          .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
      Text(
        text = theme.badgeLabel,
        style =
          MaterialTheme.typography.labelSmall.copy(
            fontSize = 8.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold,
          ),
      )
    }

    // Top-right mini offline check icon on map thumbnail when downloaded
    if (isDownloaded) {
      Box(
        modifier =
          Modifier.align(Alignment.TopEnd)
            .padding(4.dp)
            .size(18.dp)
            .clip(CircleShape)
            .background(Color(0xFF2E7D32))
            .border(1.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Default.Check,
          contentDescription = "Downloaded",
          tint = Color.White,
          modifier = Modifier.size(11.dp),
        )
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
  Card(
    modifier = modifier,
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
        style =
          MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            color = Color(0xFF133A29),
          ),
      )
      Text(
        text =
          "Use this panel during UX review sessions to jump between onboarding and main survey screens, inspect 1:1 vs 1:N entity bottom sheets, toggle layers, or test XForms FormDef XML data collection.",
        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF4B5563)),
      )

      // Live XForms FormDef XML Tester Section
      XFormsFormDefChromeSection(state)

      HorizontalDivider(color = Color(0xFFE5E7EB))

      // 1. Interactive Screen Stepper
      Text(
        text = "1. ONBOARDING & SURVEY FLOW SCREENS",
        style =
          MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E6F50),
            letterSpacing = 0.6.sp,
          ),
      )

      PrototypeScreen.entries.forEach { screen ->
        val isActive = state.currentScreen == screen
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(if (isActive) Color(0xFFE8F5E9) else Color(0xFFF9FAFB))
              .border(
                width = 1.dp,
                color = if (isActive) Color(0xFF2E7D32) else Color(0xFFE5E7EB),
                shape = RoundedCornerShape(12.dp),
              )
              .clickable { state.navigateTo(screen) }
              .padding(12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Step ${screen.stepNumber}: ${screen.title}",
              style =
                MaterialTheme.typography.labelLarge.copy(
                  fontWeight = FontWeight.Bold,
                  color = if (isActive) Color(0xFF1B5E20) else Color(0xFF1F2937),
                ),
            )
            Text(
              text = screen.subtitle,
              style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF6B7280)),
            )
          }
          if (isActive) {
            Box(
              modifier =
                Modifier.clip(RoundedCornerShape(12.dp))
                  .background(Color(0xFF1E6F50))
                  .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
              Text(
                text = "ACTIVE",
                style =
                  MaterialTheme.typography.labelSmall.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                  ),
              )
            }
          }
        }
      }

      HorizontalDivider(color = Color(0xFFE5E7EB))

      // 2. Main Survey UI Quick State Shortcuts (Map, List, Layers, 1:1 vs 1:N, Drawer)
      Text(
        text = "2. MAIN SURVEY UI QUICK INSPECTOR (STEP 5)",
        style =
          MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E6F50),
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
              "Map + 1:1 Entity",
              {
                state.navigateTo(PrototypeScreen.MAIN_SURVEY)
                state.setMainSurveyViewMode(MainSurveyViewMode.MAP)
                state.selectEntity("entity-nyr-104")
              },
            ),
            Triple(
              Icons.Default.Timeline,
              "Map + 1:N Entity",
              {
                state.navigateTo(PrototypeScreen.MAIN_SURVEY)
                state.setMainSurveyViewMode(MainSurveyViewMode.MAP)
                state.selectEntity("entity-shade-201")
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
                val nextX = if (state.userGpsNormalizedX > 0.62f) 0.36f else state.userGpsNormalizedX + 0.08f
                val nextY = if (state.userGpsNormalizedY > 0.60f) 0.38f else state.userGpsNormalizedY + 0.06f
                state.updateUserGpsLocation(nextX, nextY)
              },
            ),
          )
        mainShortcuts.forEach { (icon, label, action) ->
          Row(
            modifier =
              Modifier.clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFE8F5E9))
                .border(1.dp, Color(0xFF2E7D32), RoundedCornerShape(16.dp))
                .clickable { action() }
                .padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            Icon(
              imageVector = icon,
              contentDescription = null,
              tint = Color(0xFF1B5E20),
              modifier = Modifier.size(13.dp),
            )
            Text(
              text = label,
              style =
                MaterialTheme.typography.labelSmall.copy(
                  color = Color(0xFF1B5E20),
                  fontWeight = FontWeight.Bold,
                ),
            )
          }
        }
      }

      HorizontalDivider(color = Color(0xFFE5E7EB))

      // 3. Quick Search Presets (Name or Location)
      Text(
        text = "3. DOWNLOAD SURVEY SEARCH PRESETS (NAME OR LOCATION)",
        style =
          MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E6F50),
            letterSpacing = 0.6.sp,
          ),
      )
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        val sampleQueries = listOf("All" to "", "Brazil" to "Brazil", "Kenya" to "Kenya", "Vietnam" to "Vietnam", "Mangrove" to "Mangrove", "Watershed" to "Watershed")
        sampleQueries.forEach { (label, query) ->
          val selected = state.searchQuery == query
          val contentColor = if (selected) Color.White else Color(0xFF374151)
          Row(
            modifier =
              Modifier.clip(RoundedCornerShape(16.dp))
                .background(if (selected) Color(0xFF1E6F50) else Color(0xFFF3F4F6))
                .clickable {
                  state.navigateTo(PrototypeScreen.DOWNLOAD_SURVEY)
                  state.updateSearchQuery(query)
                }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            if (query.isNotEmpty()) {
              Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(12.dp),
              )
            }
            Text(
              text = if (query.isEmpty()) "Show All (${state.surveys.size})" else "\"$label\"",
              style =
                MaterialTheme.typography.labelSmall.copy(
                  color = contentColor,
                  fontWeight = FontWeight.SemiBold,
                ),
            )
          }
        }
      }

      HorizontalDivider(color = Color(0xFFE5E7EB))

      // 4. Survey Download State Toggles
      Text(
        text = "4. DOWNLOADED INDICATOR STATE SIMULATOR",
        style =
          MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E6F50),
            letterSpacing = 0.6.sp,
          ),
      )
      Text(
        text = "Toggle which surveys are marked as already downloaded on the device:",
        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF6B7280)),
      )

      state.surveys.forEach { survey ->
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(Color(0xFFF9FAFB))
              .clickable {
                state.navigateTo(PrototypeScreen.DOWNLOAD_SURVEY)
                state.toggleSurveyDownloaded(survey.id)
              }
              .padding(horizontal = 10.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = survey.title,
              style =
                MaterialTheme.typography.labelMedium.copy(
                  fontWeight = FontWeight.SemiBold,
                  color = Color(0xFF111827),
                ),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = survey.location,
              style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6B7280)),
            )
          }
          Row(
            modifier =
              Modifier.clip(RoundedCornerShape(12.dp))
                .background(if (survey.isDownloaded) Color(0xFFE8F5E9) else Color(0xFFE5E7EB))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            if (survey.isDownloaded) {
              Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color(0xFF1B5E20),
                modifier = Modifier.size(12.dp),
              )
            }
            Text(
              text = if (survey.isDownloaded) "Downloaded" else "Not Downloaded",
              style =
                MaterialTheme.typography.labelSmall.copy(
                  fontWeight = FontWeight.Bold,
                  color = if (survey.isDownloaded) Color(0xFF1B5E20) else Color(0xFF4B5563),
                ),
            )
          }
        }
      }
    }
  }
}
