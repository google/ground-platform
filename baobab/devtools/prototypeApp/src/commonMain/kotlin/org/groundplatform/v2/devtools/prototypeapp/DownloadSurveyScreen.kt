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
 * 3. "Download survey" screen where users can see a list of all surveys shared with them, or search
 *    by name or location by typing in the search bar. Each survey item includes a title,
 *    description, map thumbnail, and an indicator for surveys that have already been downloaded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSurveyScreen(state: PrototypeAppState) {
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
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
      Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
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
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
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
      OutlinedButton(onClick = { state.dismissDownloadSurveySignOutPrompt() }) { Text("Cancel") }
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
    border = BorderStroke(width = if (survey.isDownloaded) 1.5.dp else 1.dp, color = borderColor),
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
        SurveyMapThumbnail(theme = survey.thumbnailTheme, isDownloaded = survey.isDownloaded)

        // Survey Metadata (Title, Location, Description)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
private fun SurveyMapThumbnail(theme: MapThumbnailTheme, isDownloaded: Boolean) {
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
          quadraticTo(size.width * 0.45f, size.height * 0.48f, size.width, size.height * 0.22f)
        }
      drawPath(path = waterPath, color = Color(theme.secondaryWaterHex), style = Stroke(width = 8f))

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

/** Backward-compatible alias for [DownloadSurveyScreen]. */
@Composable
fun GroundDownloadSurveyScreen(state: PrototypeAppState) {
  DownloadSurveyScreen(state)
}
