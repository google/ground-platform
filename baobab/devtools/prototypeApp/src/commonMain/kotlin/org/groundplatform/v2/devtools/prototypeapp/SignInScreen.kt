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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.luminance
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
  val dataCollectionUiState = state.dataCollectionUiState
  if (
    dataCollectionUiState.isDataCollectionFormOpen &&
      dataCollectionUiState.activeFormWizardController != null
  ) {
    DataCollectionFormScreen(state)
    return
  }
  val onboarding = state.onboarding
  val onboardingUiState by onboarding.uiState.collectAsState()
  val settingsUiState by state.settings.uiState.collectAsState()
  when (state.currentScreen) {
    AppScreen.SIGN_IN ->
      SignInScreen(
        onSignIn = onboarding::signInWithGoogle,
        languageSelector = {
          SignInLanguageSelector(
            languageCode = settingsUiState.languageCode,
            onLanguageChange = state.settings::updateLanguage,
          )
        },
      )
    AppScreen.TERMS_OF_SERVICE ->
      TermsOfServiceScreen(
        uiState = onboardingUiState,
        actions = onboarding,
        debugTools = { PrototypeDebugToolsButton(state = state) },
      )
    AppScreen.DOWNLOAD_SURVEY ->
      DownloadSurveyScreen(
        uiState = onboardingUiState,
        actions = onboarding,
        notice = state.activeSurveyNotice,
        isDarkTheme = state.isDarkTheme,
        debugTools = { PrototypeDebugToolsButton(state = state) },
      )
    AppScreen.MAIN_SURVEY -> MainSurveyScreen(state)
  }
}

/**
 * Renders the Ground 2.0 Bichromatic "Windswept Savanna Cloud-Acacia" vector logo
 * (`shared/assets/logo.svg` • `viewBox="0 0 512 512"`).
 */
@Composable
fun CloudAcaciaLogo(modifier: Modifier = Modifier) {
  val isOnDarkSurface = LocalContentColor.current.luminance() > 0.5f
  val primaryForest = if (isOnDarkSurface) Color(0xFFA3D9A8) else Color(0xFF1B4D2E)
  val accentAmber = Color(0xFFE59A0C)

  Canvas(modifier = modifier) {
    val s = size.minDimension / 512f
    fun sx(x: Float) = x * s
    fun sy(y: Float) = y * s

    val mediumStroke = Stroke(width = sx(4f), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val accentStroke = Stroke(width = sx(3f), cap = StrokeCap.Round, join = StrokeJoin.Round)

    // Negative-space exclusion masks for the horizon sunrise dome
    val horizonMaskPath =
      Path().apply {
        moveTo(sx(0f), sy(368f))
        lineTo(sx(84f), sy(368f))
        cubicTo(sx(180f), sy(402f), sx(340f), sy(400f), sx(432f), sy(364f))
        lineTo(sx(512f), sy(364f))
        lineTo(sx(512f), sy(512f))
        lineTo(sx(0f), sy(512f))
        close()
      }

    val trunkMoatMaskPath =
      Path().apply {
        moveTo(sx(191f), sy(406f))
        cubicTo(sx(211f), sy(362f), sx(227f), sy(316f), sx(233f), sy(244f))
        lineTo(sx(291f), sy(244f))
        cubicTo(sx(285f), sy(316f), sx(281f), sy(362f), sx(301f), sy(406f))
        close()
      }

    // 1. Accent Color (#E59A0C): Off-Center Horizon Sunrise Dome (Split by Curved Moat)
    clipPath(horizonMaskPath, clipOp = ClipOp.Difference) {
      clipPath(trunkMoatMaskPath, clipOp = ClipOp.Difference) {
        drawCircle(
          color = accentAmber,
          radius = sx(99f),
          center = Offset(sx(268f), sy(386f)),
        )
      }
    }

    // 2. Accent Color (#E59A0C): Medium Tapered Crescent Community Underline
    val communityUnderlinePath =
      Path().apply {
        moveTo(sx(150f), sy(438f))
        cubicTo(sx(146f), sy(437f), sx(146f), sy(433f), sx(150f), sy(433f))
        cubicTo(sx(224f), sy(450f), sx(308f), sy(448f), sx(376f), sy(428f))
        cubicTo(sx(380f), sy(427f), sx(381f), sy(431f), sx(378f), sy(433f))
        cubicTo(sx(308f), sy(468f), sx(220f), sy(470f), sx(150f), sy(438f))
        close()
      }
    drawPath(path = communityUnderlinePath, color = accentAmber)
    drawPath(path = communityUnderlinePath, color = accentAmber, style = accentStroke)

    // 3. Primary Color (#1B4D2E): Asymmetric 2-Dome Windswept Cloud-Canopy Ribbon
    val cloudCanopyPath =
      Path().apply {
        moveTo(sx(118f), sy(264f))
        cubicTo(sx(74f), sy(256f), sx(48f), sy(218f), sx(56f), sy(172f))
        cubicTo(sx(64f), sy(128f), sx(106f), sy(98f), sx(156f), sy(106f))
        cubicTo(sx(188f), sy(56f), sx(250f), sy(34f), sx(314f), sy(48f))
        cubicTo(sx(380f), sy(62f), sx(436f), sy(112f), sx(450f), sy(176f))
        cubicTo(sx(458f), sy(214f), sx(436f), sy(248f), sx(398f), sy(254f))
        cubicTo(sx(393f), sy(255f), sx(391f), sy(249f), sx(396f), sy(247f))
        cubicTo(sx(424f), sy(236f), sx(436f), sy(208f), sx(428f), sy(176f))
        cubicTo(sx(416f), sy(124f), sx(368f), sy(82f), sx(308f), sy(70f))
        cubicTo(sx(252f), sy(58f), sx(196f), sy(78f), sx(170f), sy(124f))
        cubicTo(sx(167f), sy(129f), sx(160f), sy(131f), sx(154f), sy(129f))
        cubicTo(sx(114f), sy(118f), sx(82f), sy(140f), sx(76f), sy(176f))
        cubicTo(sx(70f), sy(212f), sx(92f), sy(244f), sx(122f), sy(256f))
        cubicTo(sx(127f), sy(258f), sx(124f), sy(265f), sx(118f), sy(264f))
        close()
      }
    drawPath(path = cloudCanopyPath, color = primaryForest)
    drawPath(path = cloudCanopyPath, color = primaryForest, style = mediumStroke)

    // 4. Primary Color (#1B4D2E): S-Curve Acacia Trunk & Asymmetric Branches
    val acaciaTrunkPath =
      Path().apply {
        moveTo(sx(214f), sy(402f))
        cubicTo(sx(232f), sy(362f), sx(248f), sy(316f), sx(252f), sy(268f))
        cubicTo(sx(228f), sy(248f), sx(192f), sy(224f), sx(158f), sy(188f))
        cubicTo(sx(154f), sy(184f), sx(159f), sy(179f), sx(164f), sy(183f))
        cubicTo(sx(198f), sy(212f), sx(230f), sy(232f), sx(254f), sy(246f))
        cubicTo(sx(262f), sy(204f), sx(286f), sy(164f), sx(326f), sy(128f))
        cubicTo(sx(330f), sy(124f), sx(336f), sy(129f), sx(332f), sy(134f))
        cubicTo(sx(298f), sy(170f), sx(278f), sy(206f), sx(270f), sy(242f))
        cubicTo(sx(296f), sy(226f), sx(326f), sy(208f), sx(358f), sy(184f))
        cubicTo(sx(362f), sy(181f), sx(367f), sy(186f), sx(363f), sy(190f))
        cubicTo(sx(332f), sy(218f), sx(300f), sy(240f), sx(268f), sy(258f))
        cubicTo(sx(264f), sy(310f), sx(266f), sy(360f), sx(280f), sy(402f))
        close()
      }
    drawPath(path = acaciaTrunkPath, color = primaryForest)
    drawPath(path = acaciaTrunkPath, color = primaryForest, style = mediumStroke)

    // 5. Primary Color (#1B4D2E): Asymmetric Tapered Crescent Earth Horizon
    val earthHorizonPath =
      Path().apply {
        moveTo(sx(78f), sy(390f))
        cubicTo(sx(74f), sy(388f), sx(76f), sy(383f), sx(80f), sy(384f))
        cubicTo(sx(176f), sy(410f), sx(338f), sy(406f), sx(432f), sy(376f))
        cubicTo(sx(436f), sy(375f), sx(438f), sy(380f), sx(434f), sy(382f))
        cubicTo(sx(340f), sy(430f), sx(174f), sy(434f), sx(78f), sy(390f))
        close()
      }
    drawPath(path = earthHorizonPath, color = primaryForest)
    drawPath(path = earthHorizonPath, color = primaryForest, style = mediumStroke)
  }
}

/**
 * 1. Sign In screen (Sign in with Google + Language selector matching Ground SettingsSelectItem).
 */
@Composable
fun SignInScreen(
  onSignIn: () -> Unit,
  /** Language selector shown above the sign-in button. */
  languageSelector: @Composable () -> Unit = {},
) {
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
        // Stylized naturalistic mission & field survey illustration
        GroundMissionGraphic(modifier = Modifier.fillMaxWidth().height(152.dp))

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
      languageSelector()

      OutlinedButton(
        onClick = onSignIn,
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

/**
 * Naturalistic vector landscape illustration shared by the mobile and web sign-in screens.
 *
 * Depicts a sunlit watershed valley with misty highland ridges, terraced agroforestry slopes, a
 * meandering river, organic savanna acacia and broadleaf trees, a winding field footpath, and
 * understated survey parcel markers nestled into the terrain.
 */
@Composable
fun GroundMissionGraphic(modifier: Modifier = Modifier) {
  Box(
    modifier =
      modifier
        .clip(MaterialTheme.shapes.medium)
        .border(
          width = 1.dp,
          color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
          shape = MaterialTheme.shapes.medium,
        )
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val w = size.width
      val h = size.height
      val s = h / 156f

      // 1. Warm golden-hour morning sky gradient
      drawRect(
        brush =
          Brush.verticalGradient(
            0.0f to Color(0xFFEAF3E2),
            0.42f to Color(0xFFF9ECC7),
            0.78f to Color(0xFFF6D38E),
            1.0f to Color(0xFFE8B86B),
          )
      )

      // 2. Soft morning sun & atmospheric glow low over the valley pass
      val sunCenter = Offset(w * 0.60f, h * 0.30f)
      val sunRadius = h * 0.19f
      drawCircle(
        color = Color(0xFFFDF2B8).copy(alpha = 0.45f),
        radius = sunRadius * 2.1f,
        center = sunCenter,
      )
      drawCircle(
        color = Color(0xFFFCE38A).copy(alpha = 0.65f),
        radius = sunRadius * 1.4f,
        center = sunCenter,
      )
      drawCircle(color = Color(0xFFFFF7CC), radius = sunRadius, center = sunCenter)

      // Soft organic clouds drifting across the upper sky
      drawSoftCloud(
        center = Offset(w * 0.22f, h * 0.17f),
        scale = s * 0.95f,
        color = Color(0xFFFFFDF5).copy(alpha = 0.78f),
      )
      drawSoftCloud(
        center = Offset(w * 0.80f, h * 0.15f),
        scale = s * 0.80f,
        color = Color(0xFFFFFDF5).copy(alpha = 0.72f),
      )

      // Distant soaring birds in the valley sky
      drawBirdSilhouette(center = Offset(w * 0.43f, h * 0.18f), scale = s * 0.9f)
      drawBirdSilhouette(center = Offset(w * 0.47f, h * 0.15f), scale = s * 0.68f)

      // 3. Distant misty mountain ranges (atmospheric perspective)
      val farRange =
        Path().apply {
          moveTo(0f, h * 0.46f)
          cubicTo(w * 0.12f, h * 0.32f, w * 0.24f, h * 0.29f, w * 0.38f, h * 0.39f)
          cubicTo(w * 0.48f, h * 0.45f, w * 0.58f, h * 0.43f, w * 0.70f, h * 0.33f)
          cubicTo(w * 0.82f, h * 0.24f, w * 0.92f, h * 0.29f, w, h * 0.36f)
          lineTo(w, h)
          lineTo(0f, h)
          close()
        }
      drawPath(path = farRange, color = Color(0xFF8AAFA0))

      val midMountainRidge =
        Path().apply {
          moveTo(0f, h * 0.50f)
          cubicTo(w * 0.16f, h * 0.39f, w * 0.32f, h * 0.43f, w * 0.50f, h * 0.47f)
          cubicTo(w * 0.66f, h * 0.50f, w * 0.82f, h * 0.36f, w, h * 0.43f)
          lineTo(w, h)
          lineTo(0f, h)
          close()
        }
      drawPath(path = midMountainRidge, color = Color(0xFF679380))

      // 4. Sunlit mid-ground agroforestry & savanna hills
      val leftHill =
        Path().apply {
          moveTo(0f, h * 0.53f)
          cubicTo(w * 0.22f, h * 0.45f, w * 0.44f, h * 0.54f, w * 0.64f, h * 0.68f)
          lineTo(w * 0.64f, h)
          lineTo(0f, h)
          close()
        }
      drawPath(
        path = leftHill,
        brush =
          Brush.verticalGradient(
            colors = listOf(Color(0xFF6B9E62), Color(0xFF477A4E)),
            startY = h * 0.45f,
            endY = h * 0.85f,
          ),
      )

      // Natural cultivated terrace bands on the left hillside
      val terrace1 =
        Path().apply {
          moveTo(0f, h * 0.60f)
          cubicTo(w * 0.18f, h * 0.53f, w * 0.34f, h * 0.59f, w * 0.50f, h * 0.67f)
        }
      val terrace2 =
        Path().apply {
          moveTo(0f, h * 0.67f)
          cubicTo(w * 0.16f, h * 0.60f, w * 0.32f, h * 0.66f, w * 0.46f, h * 0.74f)
        }
      drawPath(
        path = terrace1,
        color = Color(0xFF88B874).copy(alpha = 0.55f),
        style = Stroke(width = 2.2f * s, cap = StrokeCap.Round),
      )
      drawPath(
        path = terrace2,
        color = Color(0xFF88B874).copy(alpha = 0.45f),
        style = Stroke(width = 2.0f * s, cap = StrokeCap.Round),
      )

      // Right forested hillside catching warm morning light
      val rightHill =
        Path().apply {
          moveTo(w * 0.34f, h * 0.72f)
          cubicTo(w * 0.54f, h * 0.54f, w * 0.76f, h * 0.46f, w, h * 0.54f)
          lineTo(w, h)
          lineTo(w * 0.34f, h)
          close()
        }
      drawPath(
        path = rightHill,
        brush =
          Brush.verticalGradient(
            colors = listOf(Color(0xFF5A8F59), Color(0xFF356442)),
            startY = h * 0.46f,
            endY = h * 0.90f,
          ),
      )

      // 5. Meandering valley river with natural sandy banks and sunlit water ripples
      val riverPath =
        Path().apply {
          moveTo(w * 0.51f, h * 0.47f)
          cubicTo(w * 0.45f, h * 0.57f, w * 0.57f, h * 0.69f, w * 0.45f, h * 0.83f)
          cubicTo(w * 0.39f, h * 0.90f, w * 0.31f, h * 0.96f, w * 0.24f, h * 1.02f)
        }
      // Sandy riverbank
      drawPath(
        path = riverPath,
        color = Color(0xFFD7C293),
        style = Stroke(width = 14f * s, cap = StrokeCap.Round),
      )
      // Deep river water
      drawPath(
        path = riverPath,
        color = Color(0xFF3B8598),
        style = Stroke(width = 9.5f * s, cap = StrokeCap.Round),
      )
      // Sunlit water reflection shimmer
      drawPath(
        path = riverPath,
        color = Color(0xFFA8DFEC).copy(alpha = 0.70f),
        style = Stroke(width = 3.0f * s, cap = StrokeCap.Round),
      )

      // 6. Subtle, harmonious field survey parcel & footpath on the hillsides
      val parcelPts =
        listOf(
          Offset(w * 0.14f, h * 0.61f),
          Offset(w * 0.31f, h * 0.55f),
          Offset(w * 0.38f, h * 0.65f),
          Offset(w * 0.24f, h * 0.73f),
          Offset(w * 0.11f, h * 0.69f),
        )
      val parcelPath =
        Path().apply {
          parcelPts.forEachIndexed { idx, pt ->
            if (idx == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
          }
          close()
        }
      drawPath(path = parcelPath, color = Color(0xFFA7F3D0).copy(alpha = 0.22f))
      drawPath(
        path = parcelPath,
        color = Color(0xFFECFDF5).copy(alpha = 0.85f),
        style =
          Stroke(
            width = 1.8f * s,
            join = StrokeJoin.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f * s, 3.5f * s), 0f),
          ),
      )
      parcelPts.forEach { pt ->
        drawCircle(color = Color(0xFFECFDF5), radius = 2.6f * s, center = pt)
      }

      // Natural earthen footpath winding across the right slope
      val footpath =
        Path().apply {
          moveTo(w * 0.55f, h * 0.86f)
          cubicTo(w * 0.62f, h * 0.77f, w * 0.67f, h * 0.73f, w * 0.74f, h * 0.66f)
        }
      drawPath(
        path = footpath,
        color = Color(0xFFE6CE9A).copy(alpha = 0.75f),
        style =
          Stroke(
            width = 2.2f * s,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * s, 4f * s), 0f),
          ),
      )

      // 7. Foreground rolling meadow & shadowed botanical hills
      val foreLeft =
        Path().apply {
          moveTo(0f, h * 0.77f)
          cubicTo(w * 0.18f, h * 0.71f, w * 0.34f, h * 0.79f, w * 0.48f, h)
          lineTo(0f, h)
          close()
        }
      drawPath(path = foreLeft, color = Color(0xFF285538))

      val foreRight =
        Path().apply {
          moveTo(w * 0.42f, h)
          cubicTo(w * 0.60f, h * 0.77f, w * 0.82f, h * 0.72f, w, h * 0.79f)
          lineTo(w, h)
          close()
        }
      drawPath(path = foreRight, color = Color(0xFF1F462D))

      // 8. Naturalistic trees: Savanna Acacia on the left knoll + broadleaf groves
      drawNaturalAcaciaTree(
        base = Offset(w * 0.11f, h * 0.76f),
        scale = s * 1.12f,
      )
      drawBroadleafTree(
        base = Offset(w * 0.36f, h * 0.54f),
        scale = s * 0.76f,
        shadowColor = Color(0xFF275336),
        midColor = Color(0xFF3E7548),
        highlightColor = Color(0xFF74A968),
      )
      drawBroadleafTree(
        base = Offset(w * 0.62f, h * 0.59f),
        scale = s * 0.82f,
        shadowColor = Color(0xFF234B30),
        midColor = Color(0xFF386D44),
        highlightColor = Color(0xFF6B9F62),
      )
      drawBroadleafTree(
        base = Offset(w * 0.86f, h * 0.60f),
        scale = s * 1.05f,
        shadowColor = Color(0xFF1E422A),
        midColor = Color(0xFF31633D),
        highlightColor = Color(0xFF649A5C),
      )
      drawBroadleafTree(
        base = Offset(w * 0.92f, h * 0.64f),
        scale = s * 0.85f,
        shadowColor = Color(0xFF193823),
        midColor = Color(0xFF2B5736),
        highlightColor = Color(0xFF568B50),
      )

      // Foreground botanical grass & shrub tufts framing the lower corners
      drawGrassTuft(base = Offset(w * 0.06f, h * 0.92f), scale = s, color = Color(0xFF163522))
      drawGrassTuft(
        base = Offset(w * 0.16f, h * 0.95f),
        scale = s * 0.85f,
        color = Color(0xFF163522),
      )
      drawGrassTuft(
        base = Offset(w * 0.82f, h * 0.93f),
        scale = s * 0.9f,
        color = Color(0xFF132E1D),
      )
      drawGrassTuft(base = Offset(w * 0.93f, h * 0.90f), scale = s, color = Color(0xFF132E1D))

      // 9. Understated Ground map pins anchored naturally to the field plots
      val pinScale = s.coerceIn(0.82f, 1.15f)
      drawGroundSquirclePin(
        tip = Offset(w * 0.25f, h * 0.64f),
        fillColor = Color(0xFF2E7D32),
        scale = pinScale * 0.92f,
        glyph = PinGlyph.CHECK,
      )
      drawGroundSquirclePin(
        tip = Offset(w * 0.74f, h * 0.66f),
        fillColor = Color(0xFFD97706),
        scale = pinScale * 0.92f,
        glyph = PinGlyph.POINT,
      )
    }
  }
}

private enum class PinGlyph {
  CHECK,
  POINT,
}

/** Draws a soft, organic multi-lobe cloud with a flat base. */
private fun DrawScope.drawSoftCloud(center: Offset, scale: Float, color: Color) {
  val w = 48f * scale
  val h = 12f * scale
  drawRoundRect(
    color = color,
    topLeft = Offset(center.x - w / 2f, center.y - h / 2f),
    size = Size(w, h),
    cornerRadius = CornerRadius(h / 2f, h / 2f),
  )
  drawCircle(
    color = color,
    radius = 9f * scale,
    center = Offset(center.x - 9f * scale, center.y - 3f * scale),
  )
  drawCircle(
    color = color,
    radius = 11.5f * scale,
    center = Offset(center.x + 3f * scale, center.y - 5f * scale),
  )
  drawCircle(
    color = color,
    radius = 7.5f * scale,
    center = Offset(center.x + 14f * scale, center.y - 2f * scale),
  )
}

/** Draws a delicate distant bird silhouette in flight. */
private fun DrawScope.drawBirdSilhouette(center: Offset, scale: Float) {
  val bird =
    Path().apply {
      moveTo(center.x - 5.5f * scale, center.y + 1.2f * scale)
      cubicTo(
        center.x - 3f * scale,
        center.y - 2.2f * scale,
        center.x - 1f * scale,
        center.y - 1.5f * scale,
        center.x,
        center.y + 0.5f * scale,
      )
      cubicTo(
        center.x + 1f * scale,
        center.y - 1.5f * scale,
        center.x + 3f * scale,
        center.y - 2.2f * scale,
        center.x + 5.5f * scale,
        center.y + 1.2f * scale,
      )
    }
  drawPath(
    path = bird,
    color = Color(0xFF567D6E).copy(alpha = 0.75f),
    style = Stroke(width = 1.3f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round),
  )
}

/**
 * Draws a naturalistic savanna acacia tree with an organic tapered trunk, spreading limbs, and
 * sunlit layered foliage pads.
 */
private fun DrawScope.drawNaturalAcaciaTree(base: Offset, scale: Float) {
  val barkColor = Color(0xFF4A3222)
  // Tapered curved trunk & forked branches
  val trunk =
    Path().apply {
      moveTo(base.x - 3.5f * scale, base.y)
      cubicTo(
        base.x - 2.0f * scale,
        base.y - 8f * scale,
        base.x - 1.5f * scale,
        base.y - 14f * scale,
        base.x - 2.2f * scale,
        base.y - 20f * scale,
      )
      lineTo(base.x - 13f * scale, base.y - 28f * scale)
      lineTo(base.x - 10.5f * scale, base.y - 29f * scale)
      lineTo(base.x - 1.0f * scale, base.y - 22f * scale)
      lineTo(base.x + 1.5f * scale, base.y - 31f * scale)
      lineTo(base.x + 3.5f * scale, base.y - 31f * scale)
      lineTo(base.x + 1.5f * scale, base.y - 21f * scale)
      lineTo(base.x + 12f * scale, base.y - 27f * scale)
      lineTo(base.x + 14f * scale, base.y - 26f * scale)
      lineTo(base.x + 2.0f * scale, base.y - 18f * scale)
      cubicTo(
        base.x + 1.8f * scale,
        base.y - 12f * scale,
        base.x + 2.2f * scale,
        base.y - 6f * scale,
        base.x + 4.0f * scale,
        base.y,
      )
      close()
    }
  drawPath(path = trunk, color = barkColor)

  // Shadowed lower canopy pads
  drawCanopyPad(
    center = Offset(base.x - 11f * scale, base.y - 29f * scale),
    width = 24f * scale,
    height = 7.5f * scale,
    color = Color(0xFF1D462B),
  )
  drawCanopyPad(
    center = Offset(base.x + 11f * scale, base.y - 27f * scale),
    width = 24f * scale,
    height = 7.5f * scale,
    color = Color(0xFF1D462B),
  )
  drawCanopyPad(
    center = Offset(base.x + 1f * scale, base.y - 33f * scale),
    width = 32f * scale,
    height = 8.5f * scale,
    color = Color(0xFF265936),
  )

  // Sunlit upper canopy highlights
  drawCanopyPad(
    center = Offset(base.x - 10f * scale, base.y - 30.8f * scale),
    width = 20f * scale,
    height = 5.0f * scale,
    color = Color(0xFF437D4C),
  )
  drawCanopyPad(
    center = Offset(base.x + 12f * scale, base.y - 28.8f * scale),
    width = 20f * scale,
    height = 5.0f * scale,
    color = Color(0xFF4E8953),
  )
  drawCanopyPad(
    center = Offset(base.x + 2f * scale, base.y - 35.2f * scale),
    width = 26f * scale,
    height = 5.5f * scale,
    color = Color(0xFF68A262),
  )
}

private fun DrawScope.drawCanopyPad(center: Offset, width: Float, height: Float, color: Color) {
  drawRoundRect(
    color = color,
    topLeft = Offset(center.x - width / 2f, center.y - height / 2f),
    size = Size(width, height),
    cornerRadius = CornerRadius(height / 2f, height / 2f),
  )
}

/** Draws a lush broadleaf tree with a trunk and multi-tone sunlit foliage crown. */
private fun DrawScope.drawBroadleafTree(
  base: Offset,
  scale: Float,
  shadowColor: Color,
  midColor: Color,
  highlightColor: Color,
) {
  // Trunk
  drawRoundRect(
    color = Color(0xFF4A3323),
    topLeft = Offset(base.x - 1.8f * scale, base.y - 12f * scale),
    size = Size(3.6f * scale, 12.5f * scale),
    cornerRadius = CornerRadius(1.2f * scale, 1.2f * scale),
  )
  // Shadow foliage base
  drawCircle(
    color = shadowColor,
    radius = 8.5f * scale,
    center = Offset(base.x - 5.5f * scale, base.y - 14f * scale),
  )
  drawCircle(
    color = shadowColor,
    radius = 8.0f * scale,
    center = Offset(base.x + 5.5f * scale, base.y - 13.5f * scale),
  )
  // Mid-tone crown
  drawCircle(
    color = midColor,
    radius = 10f * scale,
    center = Offset(base.x, base.y - 18.5f * scale),
  )
  drawCircle(
    color = midColor,
    radius = 7.2f * scale,
    center = Offset(base.x - 4.5f * scale, base.y - 16f * scale),
  )
  // Sunlit crown highlight
  drawCircle(
    color = highlightColor,
    radius = 6.8f * scale,
    center = Offset(base.x + 2.2f * scale, base.y - 20.5f * scale),
  )
}

/** Draws an organic foreground grass/botanical tuft. */
private fun DrawScope.drawGrassTuft(base: Offset, scale: Float, color: Color) {
  val tuft =
    Path().apply {
      moveTo(base.x, base.y)
      quadraticTo(
        base.x - 4f * scale,
        base.y - 7f * scale,
        base.x - 9f * scale,
        base.y - 9f * scale,
      )
      quadraticTo(base.x - 2f * scale, base.y - 5f * scale, base.x, base.y)
      moveTo(base.x, base.y)
      quadraticTo(
        base.x - 1f * scale,
        base.y - 8f * scale,
        base.x - 2f * scale,
        base.y - 12f * scale,
      )
      quadraticTo(base.x + 1f * scale, base.y - 6f * scale, base.x, base.y)
      moveTo(base.x, base.y)
      quadraticTo(
        base.x + 4f * scale,
        base.y - 7f * scale,
        base.x + 8f * scale,
        base.y - 10f * scale,
      )
      quadraticTo(base.x + 2f * scale, base.y - 4f * scale, base.x, base.y)
    }
  drawPath(
    path = tuft,
    color = color,
    style = Stroke(width = 1.8f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round),
  )
}

/** Draws a stylized Ground squircle map pin anchored at [tip]. */
private fun DrawScope.drawGroundSquirclePin(
  tip: Offset,
  fillColor: Color,
  scale: Float,
  glyph: PinGlyph,
) {
  val bodyW = 20f * scale
  val bodyH = 20f * scale
  val corner = 7f * scale
  val pointerH = 5f * scale
  val topLeft = Offset(tip.x - bodyW / 2f, tip.y - bodyH - pointerH)
  val center = Offset(tip.x, topLeft.y + bodyH / 2f)

  // Drop shadow at tip
  drawOval(
    color = Color.Black.copy(alpha = 0.24f),
    topLeft = Offset(tip.x - 6f * scale, tip.y - 2f * scale),
    size = Size(12f * scale, 4f * scale),
  )

  // White pointer triangle
  val pointerPath =
    Path().apply {
      moveTo(tip.x - 4.5f * scale, topLeft.y + bodyH - 1f)
      lineTo(tip.x, tip.y)
      lineTo(tip.x + 4.5f * scale, topLeft.y + bodyH - 1f)
      close()
    }
  drawPath(path = pointerPath, color = Color.White)

  // White outer squircle casing
  drawRoundRect(
    color = Color.White,
    topLeft = topLeft,
    size = Size(bodyW, bodyH),
    cornerRadius = CornerRadius(corner, corner),
  )

  // Colored inner squircle
  val inset = 1.8f * scale
  drawRoundRect(
    color = fillColor,
    topLeft = Offset(topLeft.x + inset, topLeft.y + inset),
    size = Size(bodyW - inset * 2f, bodyH - inset * 2f),
    cornerRadius = CornerRadius(corner - inset * 0.6f, corner - inset * 0.6f),
  )

  when (glyph) {
    PinGlyph.CHECK -> {
      val checkPath =
        Path().apply {
          moveTo(center.x - 4.2f * scale, center.y + 0.2f * scale)
          lineTo(center.x - 1.2f * scale, center.y + 3.2f * scale)
          lineTo(center.x + 4.5f * scale, center.y - 2.8f * scale)
        }
      drawPath(
        path = checkPath,
        color = Color.White,
        style =
          Stroke(
            width = 2.0f * scale,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
          ),
      )
    }
    PinGlyph.POINT -> {
      drawCircle(color = Color.White, radius = 3.0f * scale, center = center)
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
  val settingsUiState by state.settings.uiState.collectAsState()
  SignInScreen(
    onSignIn = state.onboarding::signInWithGoogle,
    languageSelector = {
      SignInLanguageSelector(
        languageCode = settingsUiState.languageCode,
        onLanguageChange = state.settings::updateLanguage,
      )
    },
  )
}
