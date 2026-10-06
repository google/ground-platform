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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * Renders the Ground 2.0 5-Tone Flat "Cloud Acacia" vector logo (`shared/assets/logo.svg` •
 * `viewBox="0 0 512 512"`).
 */
@Composable
fun CloudAcaciaLogo(modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val s = size.minDimension / 512f
    fun sx(x: Float) = x * s
    fun sy(y: Float) = y * s

    val containerForest = Color(0xFF0F3828)
    val dropletMint = Color(0xFF52D69A)
    val sunGold = Color(0xFFFDE047)
    val deepPine = Color(0xFF071F16)
    val geopointOrange = Color(0xFFF97316)

    // 1. Solid Deep Forest Pebble App Container (<rect x="24" y="24" width="464" height="464"
    // rx="128" fill="#0F3828" />)
    drawRoundRect(
      color = containerForest,
      topLeft = Offset(sx(24f), sy(24f)),
      size = Size(sx(464f), sy(464f)),
      cornerRadius = CornerRadius(sx(128f), sy(128f)),
    )

    // 2. Solid Crisp Mint-Emerald Droplet Sky
    val pinPath =
      Path().apply {
        moveTo(sx(256f), sy(64f))
        cubicTo(sx(160f), sy(64f), sx(94f), sy(134f), sx(94f), sy(224f))
        cubicTo(sx(94f), sy(318f), sx(204f), sy(392f), sx(243f), sy(422f))
        cubicTo(sx(250f), sy(428f), sx(262f), sy(428f), sx(269f), sy(422f))
        cubicTo(sx(308f), sy(392f), sx(418f), sy(318f), sx(418f), sy(224f))
        cubicTo(sx(418f), sy(134f), sx(352f), sy(64f), sx(256f), sy(64f))
        close()
      }

    drawPath(path = pinPath, color = dropletMint)

    // 3. Clipped Savanna Landscape Inside the Droplet (<g clip-path="url(#gm-pin-clip)">)
    clipPath(pinPath) {
      // Solid Radiant Gold Sunrise Disc (<circle cx="256" cy="244" r="104" fill="#FDE047" />)
      drawCircle(color = sunGold, radius = sx(104f), center = Offset(sx(256f), sy(244f)))

      // Solid Ultra-Deep Pine Cloud Acacia Canopy (3 Golden-Ratio Lobes)
      val upperCloudPath =
        Path().apply {
          moveTo(sx(164f), sy(182f))
          cubicTo(sx(162f), sy(158f), sx(190f), sy(146f), sx(218f), sy(154f))
          cubicTo(sx(234f), sy(134f), sx(278f), sy(132f), sx(298f), sy(152f))
          cubicTo(sx(332f), sy(142f), sx(374f), sy(156f), sx(376f), sy(182f))
          cubicTo(sx(378f), sy(200f), sx(348f), sy(204f), sx(308f), sy(198f))
          cubicTo(sx(274f), sy(192f), sx(238f), sy(192f), sx(204f), sy(196f))
          cubicTo(sx(178f), sy(198f), sx(165f), sy(194f), sx(164f), sy(182f))
          close()
        }
      drawPath(path = upperCloudPath, color = deepPine)

      // Lower-Left Cloud Bough (22px Clear Sky Channel for Small-Size Legibility)
      val sideCloudPath =
        Path().apply {
          moveTo(sx(118f), sy(230f))
          cubicTo(sx(116f), sy(210f), sx(142f), sy(200f), sx(170f), sy(208f))
          cubicTo(sx(190f), sy(202f), sx(216f), sy(210f), sx(218f), sy(226f))
          cubicTo(sx(220f), sy(238f), sx(196f), sy(242f), sx(166f), sy(240f))
          cubicTo(sx(138f), sy(242f), sx(119f), sy(238f), sx(118f), sy(230f))
          close()
        }
      drawPath(path = sideCloudPath, color = deepPine)

      // Calligraphic Trunk with Grounded Root Flare & Open Branch Window
      val trunkPath =
        Path().apply {
          moveTo(sx(200f), sy(354f))
          cubicTo(sx(228f), sy(324f), sx(241f), sy(288f), sx(243f), sy(252f))
          cubicTo(sx(243f), sy(238f), sx(215f), sy(232f), sx(180f), sy(230f))
          lineTo(sx(202f), sy(218f))
          cubicTo(sx(226f), sy(220f), sx(243f), sy(228f), sx(249f), sy(242f))
          lineTo(sx(249f), sy(186f))
          lineTo(sx(267f), sy(186f))
          cubicTo(sx(267f), sy(210f), sx(268f), sy(232f), sx(269f), sy(252f))
          cubicTo(sx(271f), sy(288f), sx(284f), sy(324f), sx(312f), sy(354f))
          close()
        }
      drawPath(path = trunkPath, color = deepPine)

      // Low-Horizon Rolling Earth Mound
      val earthBasePath =
        Path().apply {
          moveTo(sx(94f), sy(344f))
          cubicTo(sx(190f), sy(312f), sx(322f), sy(312f), sx(418f), sy(344f))
          lineTo(sx(256f), sy(440f))
          close()
        }
      drawPath(path = earthBasePath, color = deepPine)
    }

    // 4. Solid Persimmon-Orange Geopoint Circle at Zenith (<circle cx="256" cy="114" r="24"
    // fill="#F97316" />)
    drawCircle(color = geopointOrange, radius = sx(24f), center = Offset(sx(256f), sy(114f)))

    // 5. Bold Rounded Concentric Horizon Arc (<path d="M142 440 C200 412, 312 412, 370 440" ... />)
    val horizonWavePath =
      Path().apply {
        moveTo(sx(142f), sy(440f))
        cubicTo(sx(200f), sy(412f), sx(312f), sy(412f), sx(370f), sy(440f))
      }
    drawPath(
      path = horizonWavePath,
      color = dropletMint,
      style = Stroke(width = sx(16f), cap = StrokeCap.Round),
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
                imageVector = Icons.Outlined.LocationOn,
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
          GoogleLogoIcon(size = 20.dp)
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
