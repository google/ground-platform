/*
 * Copyright 2026 The Collect One Authors.
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
package org.openforis.collectone.v2.mobile.ui.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.time.Instant
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.ui.common.UserAvatarButton
import org.openforis.collectone.v2.app.ui.datacollection.DataCollectionActions
import org.openforis.collectone.v2.app.ui.datacollection.SearchableListActions
import org.openforis.collectone.v2.core.forms.ui.controls.DropdownMenuItem
import org.openforis.collectone.v2.core.forms.ui.controls.FilledTonalButton
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.mobile.resources.Res
import org.openforis.collectone.v2.mobile.resources.mobile_clear_search_content_description
import org.openforis.collectone.v2.mobile.resources.mobile_open_navigation_drawer
import org.openforis.collectone.v2.mobile.resources.mobile_retry_failed_uploads
import org.openforis.collectone.v2.mobile.resources.mobile_search_placeholder
import org.openforis.collectone.v2.mobile.resources.mobile_upload_status_all_saved
import org.openforis.collectone.v2.mobile.resources.mobile_upload_status_failed
import org.openforis.collectone.v2.mobile.resources.mobile_upload_status_last_synced_days_ago
import org.openforis.collectone.v2.mobile.resources.mobile_upload_status_last_synced_hours_ago
import org.openforis.collectone.v2.mobile.resources.mobile_upload_status_last_synced_just_now
import org.openforis.collectone.v2.mobile.resources.mobile_upload_status_last_synced_minutes_ago
import org.openforis.collectone.v2.mobile.resources.mobile_upload_status_summary
import org.openforis.collectone.v2.mobile.resources.mobile_upload_status_syncing_now
import org.openforis.collectone.v2.mobile.resources.mobile_upload_status_uploading
import org.openforis.collectone.v2.mobile.resources.mobile_view_uploads
import org.openforis.collectone.v2.mobile.ui.LocalClock
import org.openforis.collectone.v2.mobile.ui.uploads.UploadStatusCounts

/**
 * Floating search bar at the top of the mobile map screen containing the Hamburger navigation
 * drawer button on the left, the live search input in the center, and the user avatar account menu
 * on the right.
 */
@Composable
internal fun MobileMapSearchBar(
  uiState: MainSurveyUiState,
  hasSelectedRecord: Boolean,
  isSheetExpanded: Boolean,
  actions: MainSurveyActions,
  listActions: SearchableListActions,
  dataCollectionActions: DataCollectionActions,
  modifier: Modifier = Modifier,
) {
  val query = uiState.listSearchQuery
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.extraLarge,
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor = MaterialTheme.colorScheme.onSurface,
    shadowElevation = 4.dp,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().height(48.dp).padding(start = 4.dp, end = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(
        onClick = { actions.updateDrawerOpen(true) },
        modifier = Modifier.size(40.dp),
      ) {
        Icon(
          imageVector = Icons.Outlined.Menu,
          contentDescription = stringResource(Res.string.mobile_open_navigation_drawer),
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(22.dp),
        )
      }

      Box(
        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart,
      ) {
        if (query.isEmpty()) {
          Text(
            text = stringResource(Res.string.mobile_search_placeholder),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        @Suppress("DEPRECATION")
        BasicTextField(
          value = query,
          onValueChange = {
            listActions.updateListSearchQuery(it)
            if (it.isNotEmpty()) {
              if (hasSelectedRecord) {
                dataCollectionActions.returnToBottomSheetList()
              }
              if (!isSheetExpanded) {
                listActions.updateEntityBottomSheetExpanded(true)
              }
            }
          },
          singleLine = true,
          textStyle =
            MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
          cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
          modifier = Modifier.fillMaxWidth(),
        )
      }

      if (query.isNotEmpty()) {
        IconButton(
          onClick = { listActions.clearListSearchQuery() },
          modifier = Modifier.size(36.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = stringResource(Res.string.mobile_clear_search_content_description),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
          )
        }
      }

      MobileUploadStatusButton(
        counts = uiState.uploadStatusCounts,
        lastSyncedAt = uiState.lastSyncedAt,
        onRetryFailedUploads = actions::retryFailedUploads,
        onOpenUploads = actions::drawerOpenUploads,
        modifier = Modifier.padding(end = 4.dp),
      )

      UserAvatarButton(
        userName = uiState.userProfile.name,
        userInitials = uiState.userProfile.initials,
        onClick = { actions.updateUserProfileOpen(true) },
        userPhotoUrl = uiState.userProfile.photoUrl,
        avatarSize = 32.dp,
        modifier = Modifier.padding(start = 2.dp),
      )
    }
  }
}

/**
 * Single subtle upload status icon button (Google apps style: Drive/Docs/Photos). Muted cloud-done
 * when fully synced, primary cloud-upload when uploading, error cloud-off when failed. Clicking
 * opens a popup showing upload statistics ([counts], [lastSyncedAt]) and a retry button for failed
 * uploads.
 */
@Composable
internal fun MobileUploadStatusButton(
  counts: UploadStatusCounts,
  lastSyncedAt: Instant?,
  onRetryFailedUploads: () -> Unit,
  onOpenUploads: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val uploadingCount = counts.uploading
  val failedCount = counts.failed
  val syncedCount = counts.synced
  val clock = LocalClock.current
  var showDetails by remember { mutableStateOf(false) }

  val (icon, tint, desc) =
    when {
      failedCount > 0 ->
        Triple(
          Icons.Outlined.CloudOff,
          MaterialTheme.colorScheme.error,
          pluralStringResource(Res.plurals.mobile_upload_status_failed, failedCount, failedCount),
        )
      uploadingCount > 0 ->
        Triple(
          Icons.Outlined.CloudUpload,
          MaterialTheme.colorScheme.primary,
          pluralStringResource(
            Res.plurals.mobile_upload_status_uploading,
            uploadingCount,
            uploadingCount,
          ),
        )
      else ->
        Triple(
          Icons.Outlined.CloudDone,
          MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
          stringResource(Res.string.mobile_upload_status_all_saved),
        )
    }

  Box(modifier = modifier) {
    IconButton(
      onClick = { showDetails = true },
      modifier = Modifier.size(36.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Icon(
          imageVector = icon,
          contentDescription = desc,
          tint = tint,
          modifier = Modifier.size(18.dp),
        )
        if (failedCount > 0 || uploadingCount > 0) {
          val count = if (failedCount > 0) failedCount else uploadingCount
          Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = tint,
          )
        }
      }
    }

    if (showDetails) {
      DropdownMenu(
        expanded = showDetails,
        onDismissRequest = { showDetails = false },
      ) {
        DropdownMenuItem(
          text = {
            Column(modifier = Modifier.padding(4.dp)) {
              Text(
                text = desc,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
              )
              Spacer(Modifier.height(4.dp))
              Text(
                text =
                  stringResource(
                    Res.string.mobile_upload_status_summary,
                    syncedCount,
                    uploadingCount,
                    failedCount,
                  ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(Modifier.height(2.dp))
              val syncTimeText =
                if (uploadingCount > 0) {
                  stringResource(Res.string.mobile_upload_status_syncing_now)
                } else {
                  lastSyncedAt?.let {
                    lastSyncedText(lastSyncedAge(it, clock.now()))
                  }
                }
              if (syncTimeText != null) {
                Text(
                  text = syncTimeText,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              if (failedCount > 0) {
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                  onClick = {
                    showDetails = false
                    onRetryFailedUploads()
                  },
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Text(
                    stringResource(Res.string.mobile_retry_failed_uploads),
                    style = MaterialTheme.typography.labelSmall,
                  )
                }
              }
              Spacer(Modifier.height(8.dp))
              OutlinedButton(
                onClick = {
                  showDetails = false
                  onOpenUploads()
                },
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  stringResource(Res.string.mobile_view_uploads),
                  style = MaterialTheme.typography.labelSmall,
                )
              }
            }
          },
          onClick = {},
        )
      }
    }
  }
}

/** "Last synced …" text for a sync that finished [age] ago. */
@Composable
private fun lastSyncedText(age: LastSyncedAge): String =
  when (age) {
    LastSyncedAge.JustNow -> stringResource(Res.string.mobile_upload_status_last_synced_just_now)
    is LastSyncedAge.Minutes ->
      pluralStringResource(
        Res.plurals.mobile_upload_status_last_synced_minutes_ago,
        age.count,
        age.count,
      )
    is LastSyncedAge.Hours ->
      pluralStringResource(
        Res.plurals.mobile_upload_status_last_synced_hours_ago,
        age.count,
        age.count,
      )
    is LastSyncedAge.Days ->
      pluralStringResource(
        Res.plurals.mobile_upload_status_last_synced_days_ago,
        age.count,
        age.count,
      )
  }
