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

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import org.groundplatform.v2.core.forms.ui.GroundAlertDialogOverlay
import org.groundplatform.v2.core.forms.ui.GroundBadgeTone
import org.groundplatform.v2.core.forms.ui.GroundModalBottomSheetOverlay
import org.groundplatform.v2.core.forms.ui.GroundTonalBadge
import org.groundplatform.v2.core.forms.ui.LocalGroundBrandFontFamily


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
          // User Profile & Organization Header
          Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1D5128),
            contentColor = Color.White,
          ) {
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
                    color = Color.White,
                  )
                }
                IconButton(
                  onClick = { state.updateDrawerOpen(false) },
                  modifier = Modifier.size(32.dp),
                ) {
                  Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Drawer",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                  )
                }
              }

              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Box(
                  modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF9CD49F)),
                  contentAlignment = Alignment.Center,
                ) {
                  Text(
                    text = "ML",
                    style =
                      MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color(0xFF003914),
                  )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                  Text(
                    text = state.signedInUserName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                  )
                  Text(
                    text = state.signedInUserEmail,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFB7F1B9),
                  )
                  Text(
                    text = state.signedInOrganization,
                    style =
                      MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFF9CD49F),
                  )
                }
              }
            }
          }

          // Active Survey Summary Banner
          Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
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
              icon = Icons.Default.SwapHoriz,
              title = "Surveys",
              subtitle = "${state.downloadedSurveyCount} downloaded on device",
              selected = state.activeDrawerSubView == MainDrawerSubView.SWITCH_SURVEYS,
              onClick = { state.drawerSwitchSurveys() },
            )
            DrawerMenuItem(
              icon = Icons.Default.CloudUpload,
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
              icon = Icons.Default.Map,
              title = "Offline maps",
              subtitle = "Vector & satellite raster tile cache",
              selected = state.activeDrawerSubView == MainDrawerSubView.MANAGE_OFFLINE_MAPS,
              onClick = { state.drawerManageOfflineMaps() },
            )
            DrawerMenuItem(
              icon = Icons.Default.Settings,
              title = "Settings",
              subtitle = "Units (${state.unitSystem.areaUnit}), language & media cache",
              selected = state.activeDrawerSubView == MainDrawerSubView.SETTINGS,
              onClick = { state.drawerOpenSettings() },
            )
            DrawerMenuItem(
              icon = Icons.Default.Description,
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
              icon = Icons.AutoMirrored.Filled.Logout,
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
  val filteredMutations = state.filteredUploadMutations
  val activeFilter = state.selectedUploadStatusFilter

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
          imageVector = Icons.Default.CloudUpload,
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
        if (state.outboxMutationCount > 0) {
          FilledTonalButton(
            onClick = { state.syncAllOutboxMutations() },
            shape = MaterialTheme.shapes.small,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          ) {
            Icon(
              imageVector = Icons.Default.CloudUpload,
              contentDescription = null,
              modifier = Modifier.size(13.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "Sync all (${state.outboxMutationCount})",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            )
          }
        }

        OutlinedButton(
          onClick = { state.closeDrawerSubView() },
          shape = MaterialTheme.shapes.small,
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text("Back", style = MaterialTheme.typography.labelSmall)
        }
      }
    }

    // Status Filter Chips Row: Pending | In progress | Uploaded | Failed
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      UploadStatusFilter.entries.forEach { filter ->
        val isSelected = activeFilter == filter
        val count = state.uploadCountForFilter(filter)
        FilterChip(
          selected = isSelected,
          onClick = { state.toggleUploadStatusFilter(filter) },
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
                  imageVector = Icons.Default.Check,
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

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    // Compact List of User-Friendly Upload Items
    if (filteredMutations.isEmpty()) {
      Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text =
            if (activeFilter != null) {
              "No ${activeFilter.label.lowercase()} uploads."
            } else {
              "No uploads yet."
            },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
      }
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        filteredMutations.forEach { mutation ->
          UploadMutationRowCard(mutation = mutation, state = state)
        }
      }
    }
  }
}

/**
 * Compact, user-friendly row card for a [MutationLogItem] in `Uploads`, showing:
 * - Action label (`Form submitted`, `Form modified`, `Form deleted`, `Map feature modified`, etc.) and
 * form/feature title
 * - Target entity label and concise timestamp
 * - Status badge (`Pending`, `In progress`, `Uploaded`, `Failed`) and inline retry/upload action
 */
@Composable
internal fun UploadMutationRowCard(mutation: MutationLogItem, state: PrototypeAppState) {
  val statusFilter = mutation.uploadStatusFilter
  val badgeTone =
    when (statusFilter) {
      UploadStatusFilter.UPLOADED -> GroundBadgeTone.PRIMARY
      UploadStatusFilter.IN_PROGRESS -> GroundBadgeTone.TERTIARY
      UploadStatusFilter.PENDING -> GroundBadgeTone.NEUTRAL
      UploadStatusFilter.FAILED -> GroundBadgeTone.WARNING
    }

  OutlinedCard(
    onClick = {
      if (mutation.submissionId != null) {
        state.selectSubmissionDetail(mutation.submissionId)
      } else if (mutation.entityId.isNotBlank()) {
        state.selectEntity(mutation.entityId)
      }
      state.closeDrawerSubView()
    },
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
            onClick = { state.syncMutationNow(mutation.id) },
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
                    Icons.Default.Refresh
                  } else {
                    Icons.Default.CloudUpload
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
          imageVector = Icons.Default.SwapHoriz,
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
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
              } else {
                MaterialTheme.colorScheme.surface
              }
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
                color = MaterialTheme.colorScheme.onSurface,
              )
              if (isActive) {
                GroundTonalBadge(text = "ACTIVE", tone = GroundBadgeTone.PRIMARY)
              }
            }
            Text(
              text =
                "${survey.location} • ${survey.entityCount} locations • ${survey.offlineSizeLabel}",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = survey.description,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
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
 * Modal dialog displaying a scannable QR Code for a survey location (`Icons.Default.QrCode`),
 * allowing offline field verification and rapid lookup of the location's `GeoID`.
 */
@Composable
internal fun EntityQrCodeModalDialog(state: PrototypeAppState, entity: GeospatialEntityItem) {
  GroundAlertDialogOverlay(
    onDismissRequest = { state.closeEntityQrCode() },
    icon = {
      Icon(
        imageVector = Icons.Default.QrCode,
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
              imageVector = Icons.Default.QrCode,
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
        GroundTonalBadge(
          text = "GeoID: ${entity.geoId}",
          tone = GroundBadgeTone.PRIMARY,
          monospace = true,
        )
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
          state.closeEntityQrCode()
          state.shareEntityPdf(entity.id)
        }
      ) {
        Icon(
          imageVector = Icons.Default.Share,
          contentDescription = null,
          modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text("Share PDF")
      }
    },
    dismissButton = { TextButton(onClick = { state.closeEntityQrCode() }) { Text("Close") } },
  )
}

/**
 * Modal bottom sheet allowing the user to share a generated offline PDF receipt for either a
 * Geospatial Entity or a Submission (`state.activeSharedPdfSheet`) to their preferred app.
 */
@Composable
internal fun SharePdfToAppModalDialog(state: PrototypeAppState, sheet: SharedPdfSheetState) {
  GroundModalBottomSheetOverlay(onDismissRequest = { state.closeSharePdfSheet() }) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
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
          Icon(
            imageVector = Icons.Default.Share,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
          )
          Text(
            text = "Share PDF to Preferred App",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
        IconButton(onClick = { state.closeSharePdfSheet() }) {
          Icon(imageVector = Icons.Default.Close, contentDescription = "Close Share PDF Sheet")
        }
      }

      // PDF attachment preview card
      OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        colors =
          CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
          ),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            imageVector = Icons.Default.PictureAsPdf,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(26.dp),
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = sheet.pdfFileName,
              style =
                MaterialTheme.typography.labelMedium.copy(
                  fontWeight = FontWeight.Bold,
                ),
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = "${sheet.title} • ${sheet.subtitle}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      Text(
        text = "Select preferred application to send offline PDF receipt:",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        listOf("WhatsApp", "Gmail", "Google Drive", "Bluetooth").forEachIndexed { index, appName ->
          val isPreferred = index == 0
          FilterChip(
            selected = isPreferred,
            onClick = { state.closeSharePdfSheet() },
            label = {
              Text(
                text = appName,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
              )
            },
          )
        }
      }

      Button(
        onClick = { state.closeSharePdfSheet() },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
      ) { Text("Done") }
    }
  }
}

/**
 * Drawer Sub-Screen: `Offline maps` (consistent with `docs/design/00-index.md` "Offline Storage
 * Safeguards & Media Purging" — Mapbox vector & raster tiles and 500 MB storage guardrail).
 */
@Composable
internal fun ManageOfflineMapsSubScreen(state: PrototypeAppState) {
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
          imageVector = Icons.Default.Map,
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
      OutlinedButton(onClick = { state.closeDrawerSubView() }, shape = MaterialTheme.shapes.small) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = null,
          modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text("Back to Map")
      }
    }

    // Clear, user-friendly device storage breakdown chart
    DeviceStorageBreakdownCard(storage = state.deviceStorageInfo)

    state.offlineTilePackages.forEach { pkg ->
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
              onClick = { state.promptRemoveOfflineTilePackage(pkg.id) },
              shape = MaterialTheme.shapes.large,
            ) {
              Icon(
                imageVector = Icons.Default.Check,
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
              onClick = { state.toggleOfflineTilePackage(pkg.id) },
              shape = MaterialTheme.shapes.large,
            ) {
              Icon(
                imageVector = Icons.Default.Download,
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

    if (state.pendingRemovalTilePackageId != null) {
      RemoveOfflineTilePackageConfirmationDialog(state)
    }
  }
}

/**
 * Confirmation prompt dialog shown before removing an offline map tile package from the device.
 */
@Composable
private fun RemoveOfflineTilePackageConfirmationDialog(state: PrototypeAppState) {
  val packageId = state.pendingRemovalTilePackageId ?: return
  val pkg = state.offlineTilePackages.firstOrNull { it.id == packageId } ?: return

  GroundAlertDialogOverlay(
    onDismissRequest = { state.dismissRemoveOfflineTilePackage() },
    icon = {
      Icon(
        imageVector = Icons.Default.CloudOff,
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
        onClick = { state.confirmRemoveOfflineTilePackage() },
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
      OutlinedButton(onClick = { state.dismissRemoveOfflineTilePackage() }) { Text("Cancel") }
    },
  )
}

/**
 * Drawer Sub-Screen: `Settings` — delegates to [GroundSettingsScreen], ported from
 * `org.groundplatform.android.ui.settings.SettingsScreen` in `github.com/google/ground-android`.
 */
@Composable
internal fun SurveySettingsSubScreen(state: PrototypeAppState) {
  SettingsScreen(state = state)
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
fun DeviceStorageBreakdownCard(
  storage: DeviceStorageInfo,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    colors =
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
      ),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
            imageVector = Icons.Default.CheckCircle,
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

      // Storage breakdown legend items (Downloaded imagery, Submitted forms, photos, etc., Free, and Other)
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
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

/**
 * Stacked horizontal proportional bar chart illustrating device storage distribution.
 */
@Composable
fun DeviceStorageBreakdownChart(
  storage: DeviceStorageInfo,
  modifier: Modifier = Modifier,
) {
  val imageryColor = StorageChartColors.downloadedImageryColor
  val dataColor = StorageChartColors.dataColor
  val otherColor = StorageChartColors.otherColor
  val freeColor = StorageChartColors.freeColor

  val imageryFrac = storage.imageryFraction
  val dataFrac = storage.dataFraction
  val otherFrac = storage.otherUsedFraction
  val freeFrac = storage.freeFraction

  Box(
    modifier = modifier.clip(RoundedCornerShape(8.dp)).background(freeColor),
  ) {
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
    Box(
      modifier = Modifier.size(10.dp).clip(CircleShape).background(color),
    )
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
