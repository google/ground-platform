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
package org.groundplatform.v2.devtools.prototypeapp.ui.onboarding

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.groundplatform.v2.core.forms.ui.GroundAlertDialogOverlay
import org.groundplatform.v2.devtools.prototypeapp.PrototypeAppState
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapThumbnailTheme
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.ui.state.OnboardingUiState
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.OnboardingActions
import org.groundplatform.v2.devtools.prototypeapp.ui.workbench.PrototypeDebugToolsButton

/**
 * 3. "Download survey" screen where users can see a list of all surveys shared with them, or search
 *    by name or location by typing in the search bar. Each survey item includes a title,
 *    description, map thumbnail, and an indicator for surveys that have already been downloaded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSurveyScreen(
  uiState: OnboardingUiState,
  actions: OnboardingActions,
  notice: String?,
  isDarkTheme: Boolean,
  /** Prototype-only tools shown in the top bar (none in production). */
  debugTools: @Composable () -> Unit = {},
) {
  val onSurfaceColor = MaterialTheme.colorScheme.onSurface
  val filtered = uiState.filteredSurveys

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
                "${uiState.surveys.size} shared with you • ${uiState.downloadedSurveyCount} downloaded",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = { actions.navigateBackFromDownloadSurvey() }) {
            Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          debugTools()
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
          value = uiState.searchQuery,
          onValueChange = { actions.updateSearchQuery(it) },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          placeholder = {
            Text(
              text = "Search by name, location, or organization...",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          },
          leadingIcon = {
            Icon(
              imageVector = Icons.Outlined.Search,
              contentDescription = "Search",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp),
            )
          },
          trailingIcon = {
            if (uiState.searchQuery.isNotEmpty()) {
              IconButton(onClick = { actions.clearSearchQuery() }) {
                Icon(
                  imageVector = Icons.Outlined.Close,
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
      notice?.let { notice ->
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
              imageVector = Icons.Outlined.CheckCircle,
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
            if (uiState.searchQuery.isBlank()) {
              "SURVEYS SHARED WITH YOU (${filtered.size})"
            } else {
              "MATCHING SURVEYS (${filtered.size} OF ${uiState.surveys.size})"
            },
          style =
            MaterialTheme.typography.labelSmall.copy(
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              letterSpacing = 0.6.sp,
            ),
        )
        if (uiState.searchQuery.isNotBlank()) {
          TextButton(onClick = { actions.clearSearchQuery() }) {
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
              imageVector = Icons.Outlined.Map,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(36.dp),
            )
            Text(
              text = "No surveys match \"${uiState.searchQuery}\"",
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
              organizationName = uiState.organizationNames[survey.organizationId],
              isDarkTheme = isDarkTheme,
              onDownloadClick = { actions.downloadSurvey(survey.id) },
              onToggleDownloadClick = { actions.promptRemoveDownloadedSurvey(survey.id) },
              onOpenSurveyClick = { actions.openSurvey(survey.id) },
            )
          }
          Spacer(modifier = Modifier.height(12.dp))
        }
      }
    }

    if (uiState.isSignOutPromptOpen) {
      DownloadSurveySignOutPromptDialog(uiState, actions)
    }

    if (uiState.pendingRemovalSurveyId != null) {
      RemoveDownloadedSurveyConfirmationDialog(uiState, actions)
    }
  }
}

/** Confirmation prompt dialog shown before removing a downloaded survey from the device. */
@Composable
private fun RemoveDownloadedSurveyConfirmationDialog(
  uiState: OnboardingUiState,
  actions: OnboardingActions,
) {
  val surveyId = uiState.pendingRemovalSurveyId ?: return
  val survey = uiState.surveys.firstOrNull { it.id == surveyId } ?: return

  GroundAlertDialogOverlay(
    onDismissRequest = { actions.dismissRemoveDownloadedSurvey() },
    icon = {
      Icon(
        imageVector = Icons.Outlined.CloudOff,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
      )
    },
    title = {
      Text(
        text = "Remove offline survey?",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
    },
    text = {
      Text(
        text =
          "Removing \"${survey.title}\" will delete the offline copy (${survey.offlineSizeLabel}) from this device. You will need an internet connection to download and use it offline again.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    },
    confirmButton = {
      Button(
        onClick = { actions.confirmRemoveDownloadedSurvey() },
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
      OutlinedButton(onClick = { actions.dismissRemoveDownloadedSurvey() }) { Text("Cancel") }
    },
  )
}

/**
 * Confirmation prompt dialog shown when the user taps Back on the Download surveys screen after
 * accepting the Terms of Service, confirming before signing the user out.
 */
@Composable
private fun DownloadSurveySignOutPromptDialog(
  uiState: OnboardingUiState,
  actions: OnboardingActions,
) {
  GroundAlertDialogOverlay(
    onDismissRequest = { actions.dismissSignOutPrompt() },
    icon = {
      Icon(
        imageVector = Icons.AutoMirrored.Outlined.Logout,
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
          "Going back will sign out ${uiState.profile.email} and return to the Sign In screen.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    },
    confirmButton = {
      Button(
        onClick = { actions.confirmSignOut() },
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
      OutlinedButton(onClick = { actions.dismissSignOutPrompt() }) { Text("Cancel") }
    },
  )
}

/**
 * Individual survey card showing:
 * - Map thumbnail placeholder (`SurveyMapThumbnail`)
 * - Survey title
 * - Survey location & coordinates
 * - Organization the survey belongs to, if any
 * - Survey description
 * - Indicator badge on surveys that have already been downloaded (`Downloaded`) or a Download CTA
 * - Action to open the survey in the Main Survey UI (`Open`)
 */
@Composable
private fun SurveyListItemCard(
  survey: SurveyPreviewItem,
  organizationName: String?,
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
              imageVector = Icons.Outlined.LocationOn,
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

          if (organizationName != null) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.Groups,
                contentDescription = "Organization",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
              )
              Text(
                text = organizationName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
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
                  imageVector = Icons.Outlined.Check,
                  contentDescription = null,
                  modifier = Modifier.size(14.dp),
                )
              },
              label = {
                Text(
                  text = "Downloaded",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  softWrap = false,
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
              )
              Spacer(modifier = Modifier.width(3.dp))
              Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
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
              imageVector = Icons.Outlined.Download,
              contentDescription = null,
              modifier = Modifier.size(13.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "Download",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              softWrap = false,
            )
          }
        }
      }
    }
  }
}

/**
 * Stylized map thumbnail placeholder rendered with Compose Canvas for each survey card, on mobile
 * and on the web surveys page.
 */
@Composable
internal fun SurveyMapThumbnail(
  theme: MapThumbnailTheme,
  isDownloaded: Boolean,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier =
      modifier
        .size(80.dp)
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
            imageVector = Icons.Outlined.Check,
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
  val uiState by state.onboarding.uiState.collectAsState()
  DownloadSurveyScreen(
    uiState = uiState,
    actions = state.onboarding,
    notice = state.activeSurveyNotice,
    isDarkTheme = state.isDarkTheme,
    debugTools = { PrototypeDebugToolsButton(state = state) },
  )
}
