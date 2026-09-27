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
import org.jetbrains.compose.resources.stringResource

/**
 * Host composable that switches between the 4 Ground Mobile UI prototype screens:
 * 1. Sign In Screen (Sign in with Google only)
 * 2. Terms of Service Screen
 * 3. Download Survey Screen (with search by name or location, map thumbnail, and downloaded
 *    indicator)
 * 4. Main Survey UI (Map & List Views, Layers toggle, simplestyle-spec Marker Progression ○ → ◐ →
 *    ✓, and Navigation Drawer)
 */
@Composable
fun MobileScreenHost(state: PrototypeAppState) {
  if (state.isDataCollectionFormOpen && state.activeFormWizardController != null) {
    DataCollectionFormScreen(state)
    return
  }
  when (state.currentScreen) {
    AppScreen.SIGN_IN -> SignInScreen(state)
    AppScreen.TERMS_OF_SERVICE -> TermsOfServiceScreen(state)
    AppScreen.DOWNLOAD_SURVEY -> DownloadSurveyScreen(state)
    AppScreen.MAIN_SURVEY -> MainSurveyScreen(state)
  }
}

/**
 * Renders the Ground 2.0 "Cloud Acacia" vector logo (`shared/assets/logo.svg` • `viewBox="0 0 512
 * 512"`).
 */
@Composable
fun CloudAcaciaLogo(modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val s = size.minDimension / 512f
    fun sx(x: Float) = x * s
    fun sy(y: Float) = y * s

    // 1. Rounded Pebble App Container (<rect x="24" y="24" width="464" height="464" rx="128"
    // fill="url(#ac3-bg)" />)
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

    // 4. Signature Small Orange Circle Motif (<circle cx="256" cy="122" r="20"
    // fill="url(#ac3-orange)" />)
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

/**
 * 1. Sign In screen (Sign in with Google + Language selector matching Ground SettingsSelectItem).
 */
@Composable
fun SignInScreen(state: PrototypeAppState) {
  val surfaceColor = MaterialTheme.colorScheme.surface
  val onSurfaceColor = MaterialTheme.colorScheme.onSurface
  val brandFont = LocalGroundBrandFontFamily.current

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
      CloudAcaciaLogo(modifier = Modifier.size(68.dp))
      Text(
        text = "Ground",
        style =
          MaterialTheme.typography.headlineMedium.copy(
            fontFamily = brandFont,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.6.sp,
          ),
      )
      Text(
        text =
          buildAnnotatedString {
            append("Welcome to ")
            withStyle(SpanStyle(fontFamily = brandFont, fontWeight = FontWeight.Bold)) {
              append("Ground")
            }
          },
        style = MaterialTheme.typography.titleLarge,
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
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
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
            text = stringResource(Res.string.sign_in_with_google),
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

/** Backward-compatible alias for [MobileScreenHost]. */
@Composable
fun GroundMobilePrototypeScreenHost(state: PrototypeAppState) {
  MobileScreenHost(state)
}

/** Backward-compatible alias for [CloudAcaciaLogo]. */
@Composable
fun GroundCloudAcaciaLogo(modifier: Modifier = Modifier) {
  CloudAcaciaLogo(modifier)
}

/** Backward-compatible alias for [SignInScreen]. */
@Composable
fun GroundSignInScreen(state: PrototypeAppState) {
  SignInScreen(state)
}
