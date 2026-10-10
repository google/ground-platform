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
package org.groundplatform.v2.devtools.prototypeapp.ui.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.groundplatform.v2.core.forms.ui.GroundAlertDialogOverlay
import org.groundplatform.v2.core.forms.ui.GroundBadgeTone
import org.groundplatform.v2.core.forms.ui.GroundModalBottomSheetOverlay
import org.groundplatform.v2.core.forms.ui.GroundTonalBadge
import org.groundplatform.v2.core.forms.ui.LocalGroundBrandFontFamily
import org.groundplatform.v2.devtools.prototypeapp.PrototypeAppState
import org.groundplatform.v2.devtools.prototypeapp.domain.model.DeviceStorageInfo
import org.groundplatform.v2.devtools.prototypeapp.domain.model.GeospatialEntityItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MainDrawerSubView
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MutationLogItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MutationSyncState
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SharedPdfSheetState
import org.groundplatform.v2.devtools.prototypeapp.domain.model.UploadStatusFilter
import org.groundplatform.v2.devtools.prototypeapp.ui.common.GeoIdPendingSyncIcon
import org.groundplatform.v2.devtools.prototypeapp.ui.common.GroundFilterChip
import org.groundplatform.v2.devtools.prototypeapp.ui.common.horizontalScrollWithMouseDrag
import org.groundplatform.v2.devtools.prototypeapp.ui.datacollection.EntityGeometryIcon
import org.groundplatform.v2.devtools.prototypeapp.ui.onboarding.CloudAcaciaLogo
import org.groundplatform.v2.devtools.prototypeapp.ui.settings.GroundSettingsScreen
import org.groundplatform.v2.devtools.prototypeapp.ui.settings.SettingsScreen
import org.groundplatform.v2.devtools.prototypeapp.ui.state.DashboardUiState
import org.groundplatform.v2.devtools.prototypeapp.ui.state.DataCollectionUiState
import org.groundplatform.v2.devtools.prototypeapp.ui.state.SettingsUiState
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.DashboardActions
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.DataCollectionActions
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.SettingsActions

/**
 * Hamburger Navigation Drawer overlay providing options to:
 * 1. Surveys (downloaded surveys screen with button to browse & download more surveys)
 * 2. Offline maps
 * 3. Change settings
 * 4. View Terms of Service
 * 5. Sign out
 */
@Composable
internal fun MainSurveyNavigationDrawerOverlay(state: PrototypeAppState) {
  val brandFont = LocalGroundBrandFontFamily.current
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
      Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.fillMaxWidth()) {
          // User Profile & Organization Header with naturalistic Ground landscape background
          Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
          ) {
            Box(modifier = Modifier.fillMaxWidth()) {
              DrawerHeaderBackground(
                isDarkTheme = state.isDarkTheme,
                scrimColor = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.matchParentSize(),
              )

              Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
                    CloudAcaciaLogo(modifier = Modifier.size(30.dp))
                    Text(
                      text = "Ground",
                      style =
                        MaterialTheme.typography.titleLarge.copy(
                          fontFamily = brandFont,
                          fontWeight = FontWeight.ExtraBold,
                          letterSpacing = 0.5.sp,
                        ),
                      color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                  }
                  IconButton(
                    onClick = { state.updateDrawerOpen(false) },
                    modifier =
                      Modifier.size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                  ) {
                    Icon(
                      imageVector = Icons.Outlined.Close,
                      contentDescription = "Close Drawer",
                      tint = MaterialTheme.colorScheme.onPrimaryContainer,
                      modifier = Modifier.size(18.dp),
                    )
                  }
                }

                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Box(
                    modifier =
                      Modifier.size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                  ) {
                    Text(
                      text = "ML",
                      style =
                        MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                      color = MaterialTheme.colorScheme.onPrimary,
                    )
                  }
                  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                      text = state.signedInUserName,
                      style =
                        MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                      color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                      text = state.signedInUserEmail,
                      style =
                        MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                      color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                      text = state.signedInOrganization,
                      style =
                        MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                      color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                  }
                }
              }
            }
          }

          // Active Survey Summary Banner
          Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
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
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }

          Spacer(modifier = Modifier.height(8.dp))

          // Navigation Drawer Options using M3 NavigationDrawerItem
          Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            DrawerMenuItem(
              icon = Icons.Outlined.SwapHoriz,
              title = "Surveys",
              subtitle = "${state.downloadedSurveyCount} downloaded on device",
              selected = state.activeDrawerSubView == MainDrawerSubView.SWITCH_SURVEYS,
              onClick = { state.drawerSwitchSurveys() },
            )
            DrawerMenuItem(
              icon = Icons.Outlined.CloudUpload,
              title = "Uploads",
              subtitle = null,
              badgeText =
                if (state.outboxMutationCount > 0) "${state.outboxMutationCount}" else null,
              badgeTone = GroundBadgeTone.WARNING,
              selected =
                state.activeDrawerSubView == MainDrawerSubView.UPLOADS ||
                  state.activeDrawerSubView == MainDrawerSubView.OUTBOX ||
                  state.activeDrawerSubView == MainDrawerSubView.UPLOADED,
              onClick = { state.drawerOpenUploads() },
            )
            DrawerMenuItem(
              icon = Icons.Outlined.Map,
              title = "Offline maps",
              subtitle = "Vector & satellite raster tile cache",
              selected = state.activeDrawerSubView == MainDrawerSubView.MANAGE_OFFLINE_MAPS,
              onClick = { state.drawerManageOfflineMaps() },
            )
            DrawerMenuItem(
              icon = Icons.Outlined.Settings,
              title = "Settings",
              subtitle = "Units (${state.unitSystem.areaUnit}), language & media cache",
              selected = state.activeDrawerSubView == MainDrawerSubView.SETTINGS,
              onClick = { state.drawerOpenSettings() },
            )
            DrawerMenuItem(
              icon = Icons.Outlined.Description,
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
              icon = Icons.AutoMirrored.Outlined.Logout,
              title = "Sign out",
              subtitle = "Disconnect ${state.signedInUserEmail}",
              selected = false,
              isDestructive = true,
              onClick = { state.drawerSignOut() },
            )
          }

          // Footer version note
          Text(
            text =
              buildAnnotatedString {
                append("Open Foris ")
                withStyle(SpanStyle(fontFamily = brandFont, fontWeight = FontWeight.Bold)) {
                  append("Ground")
                }
                append(" 2.0 • Offline-First Core")
              },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(18.dp),
          )
        }
      }
    }
  }
}

/**
 * Naturalistic panoramic Ground landscape background image for the mobile navigation drawer header.
 *
 * Depicts a sunlit watershed valley with misty highland mountain ridges, terraced agroforestry
 * slopes, a meandering valley river, drifting clouds, distant birds, and a signature savanna acacia
 * and broadleaf forest grove framed toward the right horizon, overlaid with a soft directional
 * tonal veil so user profile and organization text on the left maintains high-contrast legibility
 * in both Light and Dark themes.
 */
@Composable
internal fun DrawerHeaderBackground(
  isDarkTheme: Boolean,
  scrimColor: Color,
  modifier: Modifier = Modifier,
) {
  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val s = (h / 136f).coerceAtLeast(0.6f)

    // 1. High-key pastel morning sky gradient (light mode) or deep emerald twilight (dark mode)
    drawRect(
      brush =
        Brush.verticalGradient(
          colors =
            if (isDarkTheme) {
              listOf(
                Color(0xFF0A2011),
                Color(0xFF102E19),
                Color(0xFF163B20),
              )
            } else {
              listOf(
                Color(0xFFEDFAEE),
                Color(0xFFE2F6E3),
                Color(0xFFEDF6DA),
              )
            }
        )
    )

    // 2. Warm morning sun & soft concentric glow over the right valley pass
    val sunCenter = Offset(w * 0.73f, h * 0.28f)
    val sunRadius = h * 0.17f
    drawCircle(
      color =
        if (isDarkTheme) {
          Color(0xFFFDE68A).copy(alpha = 0.10f)
        } else {
          Color(0xFFFEF08A).copy(alpha = 0.45f)
        },
      radius = sunRadius * 2.15f,
      center = sunCenter,
    )
    drawCircle(
      color =
        if (isDarkTheme) {
          Color(0xFFFCD34D).copy(alpha = 0.16f)
        } else {
          Color(0xFFFDE047).copy(alpha = 0.58f)
        },
      radius = sunRadius * 1.42f,
      center = sunCenter,
    )
    drawCircle(
      color =
        if (isDarkTheme) {
          Color(0xFFFEF3C7).copy(alpha = 0.28f)
        } else {
          Color(0xFFFFFBEB).copy(alpha = 0.92f)
        },
      radius = sunRadius,
      center = sunCenter,
    )

    // Soft clouds & distant soaring birds in the upper valley sky
    val cloudColor =
      if (isDarkTheme) {
        Color(0xFFD8F3DC).copy(alpha = 0.10f)
      } else {
        Color(0xFFFFFFFF).copy(alpha = 0.78f)
      }
    drawHeaderCloud(center = Offset(w * 0.50f, h * 0.18f), scale = s * 0.85f, color = cloudColor)
    drawHeaderCloud(center = Offset(w * 0.88f, h * 0.16f), scale = s * 0.72f, color = cloudColor)

    val birdColor =
      if (isDarkTheme) {
        Color(0xFF74B87E).copy(alpha = 0.35f)
      } else {
        Color(0xFF6BA877).copy(alpha = 0.55f)
      }
    drawHeaderBird(center = Offset(w * 0.59f, h * 0.24f), scale = s * 0.82f, color = birdColor)
    drawHeaderBird(center = Offset(w * 0.63f, h * 0.19f), scale = s * 0.64f, color = birdColor)

    // 3. Distant misty mountain ridges in soft pastel mint/sage
    val farRange =
      Path().apply {
        moveTo(0f, h * 0.56f)
        cubicTo(w * 0.16f, h * 0.44f, w * 0.30f, h * 0.42f, w * 0.44f, h * 0.49f)
        cubicTo(w * 0.56f, h * 0.54f, w * 0.68f, h * 0.34f, w * 0.82f, h * 0.36f)
        cubicTo(w * 0.90f, h * 0.37f, w * 0.96f, h * 0.41f, w, h * 0.44f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
      }
    drawPath(
      path = farRange,
      color = if (isDarkTheme) Color(0xFF14381F) else Color(0xFFC5ECC8),
    )

    val midRidge =
      Path().apply {
        moveTo(0f, h * 0.64f)
        cubicTo(w * 0.18f, h * 0.52f, w * 0.36f, h * 0.56f, w * 0.54f, h * 0.59f)
        cubicTo(w * 0.70f, h * 0.61f, w * 0.84f, h * 0.44f, w, h * 0.50f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
      }
    drawPath(
      path = midRidge,
      color = if (isDarkTheme) Color(0xFF11301A) else Color(0xFFB3E4B8),
    )

    // 4. Mid-ground terraced agroforestry & savanna hills in soft pastel sage
    val leftHill =
      Path().apply {
        moveTo(0f, h * 0.68f)
        cubicTo(w * 0.22f, h * 0.58f, w * 0.44f, h * 0.66f, w * 0.64f, h * 0.80f)
        lineTo(w * 0.64f, h)
        lineTo(0f, h)
        close()
      }
    drawPath(
      path = leftHill,
      brush =
        Brush.verticalGradient(
          colors =
            if (isDarkTheme) {
              listOf(Color(0xFF153A20), Color(0xFF0E2916))
            } else {
              listOf(Color(0xFFA9DFB0), Color(0xFF98D6A0))
            },
          startY = h * 0.58f,
          endY = h,
        ),
    )

    // Subtle cultivated terrace contour lines on the left slope
    val terraceColor =
      if (isDarkTheme) {
        Color(0xFF2D633B).copy(alpha = 0.32f)
      } else {
        Color(0xFFD8F6DA).copy(alpha = 0.72f)
      }
    val terrace1 =
      Path().apply {
        moveTo(0f, h * 0.75f)
        cubicTo(w * 0.18f, h * 0.66f, w * 0.34f, h * 0.72f, w * 0.50f, h * 0.80f)
      }
    val terrace2 =
      Path().apply {
        moveTo(0f, h * 0.83f)
        cubicTo(w * 0.16f, h * 0.74f, w * 0.32f, h * 0.80f, w * 0.46f, h * 0.88f)
      }
    drawPath(
      path = terrace1,
      color = terraceColor,
      style = Stroke(width = 1.8f * s, cap = StrokeCap.Round),
    )
    drawPath(
      path = terrace2,
      color = terraceColor.copy(alpha = terraceColor.alpha * 0.8f),
      style = Stroke(width = 1.6f * s, cap = StrokeCap.Round),
    )

    // Right hillside in pastel meadow tones
    val rightHill =
      Path().apply {
        moveTo(w * 0.36f, h * 0.84f)
        cubicTo(w * 0.56f, h * 0.64f, w * 0.78f, h * 0.54f, w, h * 0.62f)
        lineTo(w, h)
        lineTo(w * 0.36f, h)
        close()
      }
    drawPath(
      path = rightHill,
      brush =
        Brush.verticalGradient(
          colors =
            if (isDarkTheme) {
              listOf(Color(0xFF13351D), Color(0xFF0C2313))
            } else {
              listOf(Color(0xFFA0DBA8), Color(0xFF8FD098))
            },
          startY = h * 0.54f,
          endY = h,
        ),
    )

    // 5. Meandering valley river in soft pastel aqua & sand
    val riverPath =
      Path().apply {
        moveTo(w * 0.58f, h * 0.59f)
        cubicTo(w * 0.52f, h * 0.69f, w * 0.62f, h * 0.80f, w * 0.52f, h * 0.91f)
        cubicTo(w * 0.48f, h * 0.95f, w * 0.43f, h * 0.99f, w * 0.38f, h * 1.03f)
      }
    drawPath(
      path = riverPath,
      color =
        if (isDarkTheme) {
          Color(0xFF3E3B2C).copy(alpha = 0.55f)
        } else {
          Color(0xFFE6DEC0).copy(alpha = 0.85f)
        },
      style = Stroke(width = 10.5f * s, cap = StrokeCap.Round),
    )
    drawPath(
      path = riverPath,
      color = if (isDarkTheme) Color(0xFF1D4E59) else Color(0xFF97D3DF),
      style = Stroke(width = 7.0f * s, cap = StrokeCap.Round),
    )
    drawPath(
      path = riverPath,
      color =
        if (isDarkTheme) {
          Color(0xFF56A8BC).copy(alpha = 0.35f)
        } else {
          Color(0xFFD9F4FA).copy(alpha = 0.85f)
        },
      style = Stroke(width = 2.2f * s, cap = StrokeCap.Round),
    )

    // 6. Foreground botanical hills in soft sage (avoiding dark greens behind text)
    val foreLeft =
      Path().apply {
        moveTo(0f, h * 0.86f)
        cubicTo(w * 0.20f, h * 0.80f, w * 0.36f, h * 0.88f, w * 0.52f, h)
        lineTo(0f, h)
        close()
      }
    drawPath(
      path = foreLeft,
      color = if (isDarkTheme) Color(0xFF0B2112) else Color(0xFF88CC93),
    )

    val foreRight =
      Path().apply {
        moveTo(w * 0.46f, h)
        cubicTo(w * 0.64f, h * 0.83f, w * 0.82f, h * 0.76f, w, h * 0.82f)
        lineTo(w, h)
        close()
      }
    drawPath(
      path = foreRight,
      color = if (isDarkTheme) Color(0xFF091B0F) else Color(0xFF7FC68B),
    )

    // 7. Signature Savanna Acacia & Broadleaf trees rendered in soft pastel sage tones
    drawHeaderBroadleafTree(
      base = Offset(w * 0.67f, h * 0.69f),
      scale = s * 0.76f,
      trunkColor = if (isDarkTheme) Color(0xFF223324) else Color(0xFF7CB886),
      shadowColor = if (isDarkTheme) Color(0xFF0E2916) else Color(0xFF78BD85),
      midColor = if (isDarkTheme) Color(0xFF153820) else Color(0xFF88CA94),
      highlightColor = if (isDarkTheme) Color(0xFF1F4D2D) else Color(0xFFA2DCAC),
    )
    drawHeaderAcaciaTree(
      base = Offset(w * 0.83f, h * 0.82f),
      scale = s * 1.05f,
      isDarkTheme = isDarkTheme,
    )
    drawHeaderBroadleafTree(
      base = Offset(w * 0.94f, h * 0.76f),
      scale = s * 0.82f,
      trunkColor = if (isDarkTheme) Color(0xFF1E2E20) else Color(0xFF76B380),
      shadowColor = if (isDarkTheme) Color(0xFF0C2413) else Color(0xFF73B880),
      midColor = if (isDarkTheme) Color(0xFF12321B) else Color(0xFF83C68F),
      highlightColor = if (isDarkTheme) Color(0xFF1B4628) else Color(0xFF9CD8A6),
    )

    val tuftColor = if (isDarkTheme) Color(0xFF14361E) else Color(0xFF6AB278)
    drawHeaderGrassTuft(base = Offset(w * 0.74f, h * 0.95f), scale = s * 0.85f, color = tuftColor)
    drawHeaderGrassTuft(base = Offset(w * 0.90f, h * 0.93f), scale = s * 0.92f, color = tuftColor)

    // 8. Stronger two-stage legibility scrim:
    //    (a) Horizontal veil keeping the left & center text zone high-contrast
    val lightVeilColor = Color(0xFFE6FAEA)
    val baseVeil = if (isDarkTheme) scrimColor else lightVeilColor
    drawRect(
      brush =
        Brush.horizontalGradient(
          0.0f to baseVeil.copy(alpha = if (isDarkTheme) 0.86f else 0.84f),
          0.55f to baseVeil.copy(alpha = if (isDarkTheme) 0.72f else 0.68f),
          0.82f to baseVeil.copy(alpha = if (isDarkTheme) 0.48f else 0.42f),
          1.0f to baseVeil.copy(alpha = if (isDarkTheme) 0.28f else 0.22f),
        )
    )
    //    (b) Vertical bottom-up veil behind the user profile rows (name, email, organizations)
    drawRect(
      brush =
        Brush.verticalGradient(
          0.0f to Color.Transparent,
          0.36f to baseVeil.copy(alpha = if (isDarkTheme) 0.18f else 0.15f),
          0.72f to baseVeil.copy(alpha = if (isDarkTheme) 0.52f else 0.48f),
          1.0f to baseVeil.copy(alpha = if (isDarkTheme) 0.62f else 0.56f),
        )
    )
  }
}

private fun DrawScope.drawHeaderCloud(center: Offset, scale: Float, color: Color) {
  val w = 44f * scale
  val h = 11f * scale
  drawRoundRect(
    color = color,
    topLeft = Offset(center.x - w / 2f, center.y - h / 2f),
    size = Size(w, h),
    cornerRadius = CornerRadius(h / 2f, h / 2f),
  )
  drawCircle(
    color = color,
    radius = 8.2f * scale,
    center = Offset(center.x - 8.5f * scale, center.y - 2.8f * scale),
  )
  drawCircle(
    color = color,
    radius = 10.5f * scale,
    center = Offset(center.x + 2.8f * scale, center.y - 4.6f * scale),
  )
  drawCircle(
    color = color,
    radius = 7.0f * scale,
    center = Offset(center.x + 13f * scale, center.y - 1.8f * scale),
  )
}

private fun DrawScope.drawHeaderBird(center: Offset, scale: Float, color: Color) {
  val bird =
    Path().apply {
      moveTo(center.x - 5.2f * scale, center.y + 1.1f * scale)
      cubicTo(
        center.x - 2.8f * scale,
        center.y - 2.1f * scale,
        center.x - 0.9f * scale,
        center.y - 1.4f * scale,
        center.x,
        center.y + 0.5f * scale,
      )
      cubicTo(
        center.x + 0.9f * scale,
        center.y - 1.4f * scale,
        center.x + 2.8f * scale,
        center.y - 2.1f * scale,
        center.x + 5.2f * scale,
        center.y + 1.1f * scale,
      )
    }
  drawPath(
    path = bird,
    color = color,
    style = Stroke(width = 1.25f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round),
  )
}

private fun DrawScope.drawHeaderAcaciaTree(base: Offset, scale: Float, isDarkTheme: Boolean) {
  val barkColor = if (isDarkTheme) Color(0xFF1F3323) else Color(0xFF78B583)
  val trunk =
    Path().apply {
      moveTo(base.x - 3.2f * scale, base.y)
      cubicTo(
        base.x - 1.8f * scale,
        base.y - 7.5f * scale,
        base.x - 1.4f * scale,
        base.y - 13f * scale,
        base.x - 2.0f * scale,
        base.y - 19f * scale,
      )
      lineTo(base.x - 12f * scale, base.y - 26.5f * scale)
      lineTo(base.x - 9.8f * scale, base.y - 27.5f * scale)
      lineTo(base.x - 0.9f * scale, base.y - 20.8f * scale)
      lineTo(base.x + 1.4f * scale, base.y - 29.5f * scale)
      lineTo(base.x + 3.2f * scale, base.y - 29.5f * scale)
      lineTo(base.x + 1.4f * scale, base.y - 20f * scale)
      lineTo(base.x + 11.2f * scale, base.y - 25.5f * scale)
      lineTo(base.x + 13f * scale, base.y - 24.5f * scale)
      lineTo(base.x + 1.8f * scale, base.y - 17f * scale)
      cubicTo(
        base.x + 1.6f * scale,
        base.y - 11f * scale,
        base.x + 2.0f * scale,
        base.y - 5.5f * scale,
        base.x + 3.6f * scale,
        base.y,
      )
      close()
    }
  drawPath(path = trunk, color = barkColor)

  val shadowCanopy = if (isDarkTheme) Color(0xFF0E2A17) else Color(0xFF6FB67C)
  val midCanopy = if (isDarkTheme) Color(0xFF14381F) else Color(0xFF7DC289)
  val lightCanopyLeft = if (isDarkTheme) Color(0xFF1C4A2A) else Color(0xFF8FD09B)
  val lightCanopyRight = if (isDarkTheme) Color(0xFF215230) else Color(0xFF97D5A2)
  val topCanopyHighlight = if (isDarkTheme) Color(0xFF29613A) else Color(0xFFAAE2B4)

  drawHeaderCanopyPad(
    center = Offset(base.x - 10.5f * scale, base.y - 27.5f * scale),
    width = 22f * scale,
    height = 7.0f * scale,
    color = shadowCanopy,
  )
  drawHeaderCanopyPad(
    center = Offset(base.x + 10.5f * scale, base.y - 25.5f * scale),
    width = 22f * scale,
    height = 7.0f * scale,
    color = shadowCanopy,
  )
  drawHeaderCanopyPad(
    center = Offset(base.x + 1f * scale, base.y - 31.2f * scale),
    width = 30f * scale,
    height = 8.0f * scale,
    color = midCanopy,
  )
  drawHeaderCanopyPad(
    center = Offset(base.x - 9.5f * scale, base.y - 29.2f * scale),
    width = 18.5f * scale,
    height = 4.6f * scale,
    color = lightCanopyLeft,
  )
  drawHeaderCanopyPad(
    center = Offset(base.x + 11.2f * scale, base.y - 27.2f * scale),
    width = 18.5f * scale,
    height = 4.6f * scale,
    color = lightCanopyRight,
  )
  drawHeaderCanopyPad(
    center = Offset(base.x + 1.8f * scale, base.y - 33.2f * scale),
    width = 24f * scale,
    height = 5.2f * scale,
    color = topCanopyHighlight,
  )
}

private fun DrawScope.drawHeaderCanopyPad(
  center: Offset,
  width: Float,
  height: Float,
  color: Color,
) {
  drawRoundRect(
    color = color,
    topLeft = Offset(center.x - width / 2f, center.y - height / 2f),
    size = Size(width, height),
    cornerRadius = CornerRadius(height / 2f, height / 2f),
  )
}

private fun DrawScope.drawHeaderBroadleafTree(
  base: Offset,
  scale: Float,
  trunkColor: Color,
  shadowColor: Color,
  midColor: Color,
  highlightColor: Color,
) {
  drawRoundRect(
    color = trunkColor,
    topLeft = Offset(base.x - 1.6f * scale, base.y - 11f * scale),
    size = Size(3.2f * scale, 11.5f * scale),
    cornerRadius = CornerRadius(1.1f * scale, 1.1f * scale),
  )
  drawCircle(
    color = shadowColor,
    radius = 8.0f * scale,
    center = Offset(base.x - 5.0f * scale, base.y - 13f * scale),
  )
  drawCircle(
    color = shadowColor,
    radius = 7.5f * scale,
    center = Offset(base.x + 5.0f * scale, base.y - 12.5f * scale),
  )
  drawCircle(
    color = midColor,
    radius = 9.4f * scale,
    center = Offset(base.x, base.y - 17.2f * scale),
  )
  drawCircle(
    color = midColor,
    radius = 6.8f * scale,
    center = Offset(base.x - 4.2f * scale, base.y - 14.8f * scale),
  )
  drawCircle(
    color = highlightColor,
    radius = 6.2f * scale,
    center = Offset(base.x + 2.0f * scale, base.y - 19.0f * scale),
  )
}

private fun DrawScope.drawHeaderGrassTuft(base: Offset, scale: Float, color: Color) {
  val tuft =
    Path().apply {
      moveTo(base.x, base.y)
      quadraticTo(
        base.x - 3.6f * scale,
        base.y - 6.2f * scale,
        base.x - 8.2f * scale,
        base.y - 8.2f * scale,
      )
      quadraticTo(base.x - 1.8f * scale, base.y - 4.5f * scale, base.x, base.y)
      moveTo(base.x, base.y)
      quadraticTo(
        base.x - 0.9f * scale,
        base.y - 7.2f * scale,
        base.x - 1.8f * scale,
        base.y - 10.8f * scale,
      )
      quadraticTo(base.x + 0.9f * scale, base.y - 5.4f * scale, base.x, base.y)
      moveTo(base.x, base.y)
      quadraticTo(
        base.x + 3.6f * scale,
        base.y - 6.2f * scale,
        base.x + 7.2f * scale,
        base.y - 9.0f * scale,
      )
      quadraticTo(base.x + 1.8f * scale, base.y - 3.6f * scale, base.x, base.y)
    }
  drawPath(
    path = tuft,
    color = color,
    style = Stroke(width = 1.6f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round),
  )
}

@Composable
internal fun DrawerMenuItem(
  icon: ImageVector,
  title: String,
  subtitle: String? = null,
  selected: Boolean,
  badgeText: String? = null,
  badgeTone: GroundBadgeTone = GroundBadgeTone.PRIMARY,
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
        if (!subtitle.isNullOrBlank()) {
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
      }
    },
    badge =
      badgeText?.let { countText -> { GroundTonalBadge(text = countText, tone = badgeTone) } },
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
 * Unified `Uploads` screen accessible from the Hamburger Navigation Drawer, merging pending,
 * in-progress, uploaded, and failed mutations into a single compact, user-friendly list with status
 * filter chips (`Pending`, `In progress`, `Uploaded`, `Failed`).
 */
@Composable
internal fun UploadsMutationsSubScreen(state: PrototypeAppState) {
  val uiState by state.dashboard.uiState.collectAsState()
  UploadsMutationsSubScreen(
    uiState = uiState,
    actions = state.dashboard,
    onBack = { state.closeDrawerSubView() },
    onOpenSubmission = { submissionId ->
      state.dataCollection.selectSubmissionDetail(submissionId)
      state.closeDrawerSubView()
    },
    onOpenEntity = { entityId ->
      state.selectEntity(entityId)
      state.closeDrawerSubView()
    },
  )
}

/**
 * Stateless `Uploads` screen: the status filter chips, the optional map feature filter chip, and
 * the filtered mutation cards from [uiState]; filter and sync intents go to [actions].
 *
 * @param onBack closes the sub-screen.
 * @param onOpenSubmission opens the submission a card stands for (and leaves the screen).
 * @param onOpenEntity opens the map feature a card without a submission stands for.
 */
@Composable
internal fun UploadsMutationsSubScreen(
  uiState: DashboardUiState,
  actions: DashboardActions,
  onBack: () -> Unit,
  onOpenSubmission: (submissionId: String) -> Unit,
  onOpenEntity: (entityId: String) -> Unit,
) {
  val filteredMutations = uiState.filteredUploadMutations
  val activeFilter = uiState.selectedUploadStatusFilter

  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    // Compact Top Header: Title + Sync All (if pending/failed) + Back to Map
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Icons.Outlined.CloudUpload,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp),
        )
        Text(
          text = "Uploads",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface,
        )
      }

      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (uiState.outboxMutationCount > 0 || uiState.pendingActivityRecordCount > 0) {
          FilledTonalButton(
            onClick = { actions.syncAllOutboxMutations() },
            shape = MaterialTheme.shapes.small,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.CloudUpload,
              contentDescription = null,
              modifier = Modifier.size(13.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text =
                if (uiState.outboxMutationCount > 0) {
                  "Sync all (${uiState.outboxMutationCount})"
                } else {
                  "Sync all"
                },
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            )
          }
        }

        OutlinedButton(
          onClick = onBack,
          shape = MaterialTheme.shapes.small,
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text("Back", style = MaterialTheme.typography.labelSmall)
        }
      }
    }

    // Activity records (e.g. PDF receipts) saved offline upload with the next sync.
    val pendingActivityRecords = uiState.pendingActivityRecordCount
    if (pendingActivityRecords > 0) {
      Text(
        text =
          if (pendingActivityRecords == 1) {
            "1 activity record waiting to sync"
          } else {
            "$pendingActivityRecords activity records waiting to sync"
          },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    // Status Filter Chips Row: Pending | In progress | Uploaded | Failed
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScrollWithMouseDrag(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      UploadStatusFilter.entries.forEach { filter ->
        val isSelected = activeFilter == filter
        val count = uiState.uploadCountForFilter(filter)
        GroundFilterChip(
          selected = isSelected,
          onClick = { actions.toggleUploadStatusFilter(filter) },
          label = {
            Text(
              text = "${filter.label} ($count)",
              style =
                MaterialTheme.typography.labelSmall.copy(
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              softWrap = false,
            )
          },
          leadingIcon =
            if (isSelected) {
              {
                Icon(
                  imageVector = Icons.Outlined.Check,
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

    // Entity filter (opened from a map feature's details): removable chip naming the feature.
    val entityFilter = uiState.uploadsEntityFilter
    if (entityFilter != null) {
      InputChip(
        selected = true,
        onClick = { actions.clearUploadsEntityFilter() },
        label = { Text(text = entityFilter.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = {
          EntityGeometryIcon(entity = entityFilter, size = InputChipDefaults.IconSize)
        },
        trailingIcon = {
          Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = "Show uploads for all map features",
            modifier = Modifier.size(InputChipDefaults.IconSize),
          )
        },
      )
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    // Compact List of User-Friendly Upload Items
    if (filteredMutations.isEmpty()) {
      Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text =
            when {
              activeFilter != null -> "No ${activeFilter.label.lowercase()} uploads."
              entityFilter != null -> "No uploads for this map feature."
              else -> "No uploads yet."
            },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
      }
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        filteredMutations.forEach { mutation ->
          UploadMutationRowCard(
            mutation = mutation,
            onOpen = {
              val submissionId = mutation.submissionId
              if (submissionId != null) {
                onOpenSubmission(submissionId)
              } else if (mutation.entityId.isNotBlank()) {
                onOpenEntity(mutation.entityId)
              } else {
                onBack()
              }
            },
            onSyncNow = { actions.syncMutationNow(mutation.id) },
          )
        }
      }
    }
  }
}

/**
 * Compact, user-friendly row card for a [MutationLogItem] in `Uploads`, showing:
 * - Action label (`Form submitted`, `Form modified`, `Form deleted`, `Map feature modified`, etc.)
 *   and form/feature title
 * - Target entity label and concise timestamp
 * - Status badge (`Pending`, `In progress`, `Uploaded`, `Failed`) and inline retry/upload action
 */
@Composable
internal fun UploadMutationRowCard(
  mutation: MutationLogItem,
  onOpen: () -> Unit,
  onSyncNow: () -> Unit,
) {
  val statusFilter = mutation.uploadStatusFilter
  val badgeTone =
    when (statusFilter) {
      UploadStatusFilter.UPLOADED -> GroundBadgeTone.PRIMARY
      UploadStatusFilter.IN_PROGRESS -> GroundBadgeTone.TERTIARY
      UploadStatusFilter.PENDING -> GroundBadgeTone.NEUTRAL
      UploadStatusFilter.FAILED -> GroundBadgeTone.WARNING
    }

  OutlinedCard(
    onClick = onOpen,
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.small,
    border =
      BorderStroke(
        width = 1.dp,
        color =
          when (statusFilter) {
            UploadStatusFilter.FAILED -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
            UploadStatusFilter.IN_PROGRESS -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.outlineVariant
          },
      ),
    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
      verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      // Line 1: User-friendly action + title on left, compact status pill on right
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "${mutation.operationKind.label} • ${mutation.title}",
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f).padding(end = 6.dp),
        )

        if (statusFilter == UploadStatusFilter.FAILED) {
          Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
          ) {
            Text(
              text = statusFilter.label,
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            )
          }
        } else {
          GroundTonalBadge(text = statusFilter.label, tone = badgeTone)
        }
      }

      // Line 2: Target entity label + timestamp on left, compact Retry/Upload action on right
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "${mutation.targetLabel} • ${mutation.compactTimestamp}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f).padding(end = 6.dp),
        )

        if (mutation.isOutbox) {
          Surface(
            onClick = onSyncNow,
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
              Icon(
                imageVector =
                  if (statusFilter == UploadStatusFilter.FAILED) {
                    Icons.Outlined.Refresh
                  } else {
                    Icons.Outlined.CloudUpload
                  },
                contentDescription = null,
                modifier = Modifier.size(11.dp),
              )
              Text(
                text = if (statusFilter == UploadStatusFilter.FAILED) "Retry" else "Upload",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              )
            }
          }
        }
      }

      if (mutation.state == MutationSyncState.UPLOADING) {
        LinearProgressIndicator(
          progress = { 0.74f },
          modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
          color = MaterialTheme.colorScheme.primary,
          trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
      } else if (statusFilter == UploadStatusFilter.FAILED && mutation.stateDetail.isNotBlank()) {
        Text(
          text = mutation.stateDetail,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.error,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

/**
 * Separate screen accessible from the `"Surveys"` navigation drawer option showing ONLY the surveys
 * which have already been downloaded to the device, plus a primary action button (`"Browse &
 * download more surveys"`) which navigates to the full `Download surveys` screen.
 */
@Composable
internal fun SwitchDownloadedSurveysSubScreen(state: PrototypeAppState) {
  val downloadedList = state.downloadedSurveys

  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
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
          imageVector = Icons.Outlined.SwapHoriz,
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
      OutlinedButton(onClick = { state.closeDrawerSubView() }, shape = MaterialTheme.shapes.small) {
        Icon(
          imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
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
                MaterialTheme.colorScheme.secondaryContainer
              } else {
                MaterialTheme.colorScheme.surface
              },
            contentColor =
              if (isActive) {
                MaterialTheme.colorScheme.onSecondaryContainer
              } else {
                MaterialTheme.colorScheme.onSurface
              },
          ),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(14.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text = survey.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color =
                  if (isActive) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                  } else {
                    MaterialTheme.colorScheme.onSurface
                  },
              )
              if (isActive) {
                GroundTonalBadge(text = "ACTIVE", tone = GroundBadgeTone.PRIMARY)
              }
            }
            Text(
              text =
                "${survey.location} • ${survey.entityCount} locations • ${survey.offlineSizeLabel}",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
              color =
                if (isActive) {
                  MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                  MaterialTheme.colorScheme.primary
                },
            )
            Text(
              text = survey.description,
              style = MaterialTheme.typography.bodySmall,
              color =
                if (isActive) {
                  MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                },
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
            Button(onClick = { state.openSurvey(survey.id) }, shape = MaterialTheme.shapes.small) {
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
        imageVector = Icons.Outlined.Download,
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
 * Modal dialog displaying a scannable QR Code for a survey location (`Icons.Outlined.QrCode`),
 * allowing offline field verification and rapid lookup of the location's `GeoID`. Its PDF action is
 * `Share PDF` on mobile and a direct `Download PDF` on the web dashboard ([isWeb]). Closing the
 * dialog and the PDF action go to [actions].
 */
@Composable
internal fun EntityQrCodeModalDialog(
  actions: DataCollectionActions,
  entity: GeospatialEntityItem,
  isWeb: Boolean = false,
) {
  GroundAlertDialogOverlay(
    onDismissRequest = { actions.closeEntityQrCode() },
    icon = {
      Icon(
        imageVector = Icons.Outlined.QrCode,
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
          Box(modifier = Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Outlined.QrCode,
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
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          GroundTonalBadge(
            text = "GeoID: ${entity.geoId}",
            tone = GroundBadgeTone.PRIMARY,
            monospace = true,
          )
          if (entity.isGeoIdPendingSync) {
            GeoIdPendingSyncIcon(tint = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
        Text(
          text =
            "Scan with Ground or any EUDR compliance reader to verify ${entity.singularTypeLabel.lowercase()} geometry & GeoID.",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          actions.closeEntityQrCode()
          if (isWeb) actions.downloadEntityPdf(entity.id) else actions.shareEntityPdf(entity.id)
        }
      ) {
        Icon(
          imageVector = if (isWeb) Icons.Outlined.Download else Icons.Outlined.Share,
          contentDescription = null,
          modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(if (isWeb) "Download PDF" else "Share PDF")
      }
    },
    dismissButton = { TextButton(onClick = { actions.closeEntityQrCode() }) { Text("Close") } },
  )
}

/**
 * Modal bottom sheet for a PDF report of a map feature or a submission
 * ([DataCollectionUiState.activeSharedPdfSheet]), generated on the device so it works offline.
 * **Share** opens the system share sheet (WhatsApp, Gmail, Drive, Bluetooth, ...) where the
 * platform supports sharing files ([DataCollectionUiState.canSharePdfFiles]); **Download** saves
 * the file; tapping the file card previews it. All actions go to [actions].
 */
@Composable
internal fun SharePdfToAppModalDialog(
  uiState: DataCollectionUiState,
  actions: DataCollectionActions,
  sheet: SharedPdfSheetState,
) {
  val canShare = uiState.canSharePdfFiles
  GroundModalBottomSheetOverlay(onDismissRequest = { actions.closeSharePdfSheet() }) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = if (canShare) "Share PDF" else "Download PDF",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface,
        )
        IconButton(onClick = { actions.closeSharePdfSheet() }) {
          Icon(imageVector = Icons.Outlined.Close, contentDescription = "Close")
        }
      }

      // The generated file. Tap to preview it.
      OutlinedCard(
        onClick = { actions.previewActivePdf() },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.PictureAsPdf,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(32.dp),
          )
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              text = sheet.pdfFileName,
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = sheet.subtitle,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
            if (sheet.pageCount > 0) {
              Text(
                text =
                  "${sheet.pageCount} ${if (sheet.pageCount == 1) "page" else "pages"} • " +
                    sheet.fileSizeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          Icon(
            imageVector = Icons.Outlined.Visibility,
            contentDescription = "Preview PDF",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
          )
        }
      }

      Text(
        text =
          if (canShare) {
            "Created on this device, so you can share it even when you're offline."
          } else {
            "Created on this device. To send it, download it, then attach it in any app."
          },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      if (canShare) {
        Button(
          onClick = { actions.shareActivePdf() },
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.medium,
        ) {
          Icon(
            imageVector = Icons.Outlined.Share,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Share")
        }
        OutlinedButton(
          onClick = { actions.saveActivePdf() },
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.medium,
        ) {
          Icon(
            imageVector = Icons.Outlined.Download,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Download")
        }
      } else {
        Button(
          onClick = { actions.saveActivePdf() },
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.medium,
        ) {
          Icon(
            imageVector = Icons.Outlined.Download,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Download")
        }
      }
    }
  }
}

/**
 * Confirmation or error after a PDF action ([DataCollectionUiState.pdfExportMessage]), shown as a
 * snackbar that dismisses itself after a few seconds (through [actions]).
 */
@Composable
internal fun PdfExportMessageSnackbar(
  uiState: DataCollectionUiState,
  actions: DataCollectionActions,
  modifier: Modifier = Modifier,
) {
  val message = uiState.pdfExportMessage ?: return
  LaunchedEffect(message) {
    delay(4_000)
    actions.dismissPdfExportMessage()
  }
  Snackbar(
    modifier = modifier.padding(16.dp),
    action = { TextButton(onClick = { actions.dismissPdfExportMessage() }) { Text("OK") } },
  ) {
    Text(text = message, maxLines = 2, overflow = TextOverflow.Ellipsis)
  }
}

/**
 * Drawer Sub-Screen: `Offline maps` (consistent with `docs/design/00-index.md` "Offline Storage
 * Safeguards & Media Purging" — Mapbox vector & raster tiles and 500 MB storage guardrail).
 */
@Composable
internal fun ManageOfflineMapsSubScreen(state: PrototypeAppState) {
  val uiState by state.settings.uiState.collectAsState()
  ManageOfflineMapsSubScreen(
    uiState = uiState,
    actions = state.settings,
    onBack = state::closeDrawerSubView,
  )
}

/** Stateless Offline maps sub-screen rendering [uiState] and forwarding intents to [actions]. */
@Composable
internal fun ManageOfflineMapsSubScreen(
  uiState: SettingsUiState,
  actions: SettingsActions,
  onBack: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
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
          imageVector = Icons.Outlined.Map,
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
      OutlinedButton(onClick = onBack, shape = MaterialTheme.shapes.small) {
        Icon(
          imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
          contentDescription = null,
          modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text("Back to Map")
      }
    }

    // Clear, user-friendly device storage breakdown chart
    DeviceStorageBreakdownCard(storage = uiState.storage)

    uiState.offlineTilePackages.forEach { pkg ->
      OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors =
          CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
              onClick = { actions.promptRemoveOfflineTilePackage(pkg.id) },
              shape = MaterialTheme.shapes.large,
            ) {
              Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "Downloaded",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              )
            }
          } else {
            Button(
              onClick = { actions.toggleOfflineTilePackage(pkg.id) },
              shape = MaterialTheme.shapes.large,
            ) {
              Icon(
                imageVector = Icons.Outlined.Download,
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

    if (uiState.pendingRemovalTilePackageId != null) {
      RemoveOfflineTilePackageConfirmationDialog(uiState, actions)
    }
  }
}

/** Confirmation prompt dialog shown before removing an offline map tile package from the device. */
@Composable
private fun RemoveOfflineTilePackageConfirmationDialog(
  uiState: SettingsUiState,
  actions: SettingsActions,
) {
  val packageId = uiState.pendingRemovalTilePackageId ?: return
  val pkg = uiState.offlineTilePackages.firstOrNull { it.id == packageId } ?: return

  GroundAlertDialogOverlay(
    onDismissRequest = { actions.dismissRemoveOfflineTilePackage() },
    icon = {
      Icon(
        imageVector = Icons.Outlined.CloudOff,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
      )
    },
    title = {
      Text(
        text = "Remove offline map?",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
    },
    text = {
      Text(
        text =
          "Removing \"${pkg.regionName}\" (${pkg.tileTypeLabel}) will free ${pkg.sizeLabel} on this device. You will need an internet connection to download and view these map tiles offline again.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    },
    confirmButton = {
      Button(
        onClick = { actions.confirmRemoveOfflineTilePackage() },
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
      ) {
        Text("Remove", fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      OutlinedButton(onClick = { actions.dismissRemoveOfflineTilePackage() }) { Text("Cancel") }
    },
  )
}

/**
 * Drawer Sub-Screen: `Settings` — delegates to [GroundSettingsScreen], ported from
 * `org.groundplatform.android.ui.settings.SettingsScreen` in `github.com/google/ground-android`.
 */
@Composable
internal fun SurveySettingsSubScreen(state: PrototypeAppState) {
  val uiState by state.settings.uiState.collectAsState()
  SettingsScreen(uiState = uiState, actions = state.settings, onBack = state::closeDrawerSubView)
}

/**
 * Clear, user-friendly device storage card displaying:
 * - Total device storage capacity
 * - Free / available device storage
 * - Storage occupied by downloaded imagery (raster and vector basemap tiles)
 * - Space taken up by data (surveys, forms, entities, submissions, mutations)
 * - Visual stacked horizontal proportional chart bar with color-coded legend
 */
@Composable
fun DeviceStorageBreakdownCard(storage: DeviceStorageInfo, modifier: Modifier = Modifier) {
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    colors =
      CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      // Header row: Icon, Title & Free / Total headline
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
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
          )
          Text(
            text = "Device Storage",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
        Text(
          text = "${storage.freeStorageLabel} free of ${storage.totalStorageLabel}",
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.primary,
        )
      }

      // Proportional stacked horizontal bar chart
      DeviceStorageBreakdownChart(
        storage = storage,
        modifier = Modifier.fillMaxWidth().height(16.dp),
      )

      // Storage breakdown legend items (Downloaded imagery, Submitted forms, photos, etc., Free,
      // and Other)
      Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StorageLegendRowItem(
          color = StorageChartColors.downloadedImageryColor,
          label = "Downloaded imagery",
          value = storage.downloadedImageryStorageLabel,
        )
        StorageLegendRowItem(
          color = StorageChartColors.dataColor,
          label = "Submitted forms, photos, etc.",
          value = storage.dataStorageLabel,
        )
        StorageLegendRowItem(
          color = StorageChartColors.otherColor,
          label = "System & other apps",
          value = storage.otherUsedStorageLabel,
        )
        StorageLegendRowItem(
          color = StorageChartColors.freeColor,
          label = "Free storage",
          value = storage.freeStorageLabel,
        )
      }

      // Safeguard threshold reassurance notice
      Text(
        text =
          "Offline downloads pause automatically if device storage drops below 500 MB to protect system stability.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** Stacked horizontal proportional bar chart illustrating device storage distribution. */
@Composable
fun DeviceStorageBreakdownChart(storage: DeviceStorageInfo, modifier: Modifier = Modifier) {
  val imageryColor = StorageChartColors.downloadedImageryColor
  val dataColor = StorageChartColors.dataColor
  val otherColor = StorageChartColors.otherColor
  val freeColor = StorageChartColors.freeColor

  val imageryFrac = storage.imageryFraction
  val dataFrac = storage.dataFraction
  val otherFrac = storage.otherUsedFraction
  val freeFrac = storage.freeFraction

  Box(modifier = modifier.clip(RoundedCornerShape(8.dp)).background(freeColor)) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val canvasWidth = size.width
      val canvasHeight = size.height

      var currentX = 0f

      // 1. Downloaded Imagery slice
      val imageryWidth = canvasWidth * imageryFrac
      if (imageryWidth > 0f) {
        drawRect(
          color = imageryColor,
          topLeft = Offset(currentX, 0f),
          size = androidx.compose.ui.geometry.Size(imageryWidth, canvasHeight),
        )
        currentX += imageryWidth
      }

      // 2. Survey & form Data slice
      val dataWidth = canvasWidth * dataFrac
      if (dataWidth > 0f) {
        drawRect(
          color = dataColor,
          topLeft = Offset(currentX, 0f),
          size = androidx.compose.ui.geometry.Size(dataWidth, canvasHeight),
        )
        currentX += dataWidth
      }

      // 3. System & Other apps slice
      val otherWidth = canvasWidth * otherFrac
      if (otherWidth > 0f) {
        drawRect(
          color = otherColor,
          topLeft = Offset(currentX, 0f),
          size = androidx.compose.ui.geometry.Size(otherWidth, canvasHeight),
        )
        currentX += otherWidth
      }

      // 4. Remaining width is Free storage
      val freeWidth = (canvasWidth - currentX).coerceAtLeast(0f)
      if (freeWidth > 0f) {
        drawRect(
          color = freeColor,
          topLeft = Offset(currentX, 0f),
          size = androidx.compose.ui.geometry.Size(freeWidth, canvasHeight),
        )
      }
    }
  }
}

/** Single color-coded legend entry for [DeviceStorageBreakdownCard]. */
@Composable
private fun StorageLegendRowItem(
  color: Color,
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
    Row(
      modifier = Modifier.weight(1f),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = value,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        softWrap = false,
      )
    }
  }
}

/** Semantic, high-contrast Material 3 harmonious colors for the device storage chart segments. */
internal object StorageChartColors {
  /** Downloaded satellite & vector map imagery: deep teal/blue (`#0288D1`). */
  val downloadedImageryColor: Color = Color(0xFF0288D1)

  /** Survey definitions, master data & submissions: forest green (`#2E7D32`). */
  val dataColor: Color = Color(0xFF2E7D32)

  /** OS system files and other applications: slate gray (`#9E9E9E`). */
  val otherColor: Color = Color(0xFF9E9E9E)

  /** Free / available device storage: light neutral tint (`#E0E0E0`). */
  val freeColor: Color = Color(0xFFE0E0E0)
}
