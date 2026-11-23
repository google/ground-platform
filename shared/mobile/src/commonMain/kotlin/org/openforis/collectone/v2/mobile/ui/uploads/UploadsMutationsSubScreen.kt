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
package org.openforis.collectone.v2.mobile.ui.uploads

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.MutationLogItem
import org.openforis.collectone.v2.app.domain.model.MutationSyncState
import org.openforis.collectone.v2.app.domain.model.UploadStatusFilter
import org.openforis.collectone.v2.app.ui.common.FlatFilterChip
import org.openforis.collectone.v2.app.ui.common.horizontalScrollWithMouseDrag
import org.openforis.collectone.v2.app.ui.dashboard.DashboardActions
import org.openforis.collectone.v2.app.ui.dashboard.DashboardUiState
import org.openforis.collectone.v2.app.ui.datacollection.EntityGeometryIcon
import org.openforis.collectone.v2.app.ui.text.ErrorMessages
import org.openforis.collectone.v2.app.ui.text.labelRes
import org.openforis.collectone.v2.core.forms.ui.BadgeTone
import org.openforis.collectone.v2.core.forms.ui.TonalBadge
import org.openforis.collectone.v2.core.forms.ui.controls.FilledTonalButton
import org.openforis.collectone.v2.core.forms.ui.controls.InputChip
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.mobile.resources.Res
import org.openforis.collectone.v2.mobile.resources.mobile_back
import org.openforis.collectone.v2.mobile.resources.mobile_retry
import org.openforis.collectone.v2.mobile.resources.mobile_upload
import org.openforis.collectone.v2.mobile.resources.mobile_upload_filter_chip
import org.openforis.collectone.v2.mobile.resources.mobile_upload_status_failed_label
import org.openforis.collectone.v2.mobile.resources.mobile_upload_status_in_progress
import org.openforis.collectone.v2.mobile.resources.mobile_upload_status_pending
import org.openforis.collectone.v2.mobile.resources.mobile_upload_status_uploaded
import org.openforis.collectone.v2.mobile.resources.mobile_uploads_empty
import org.openforis.collectone.v2.mobile.resources.mobile_uploads_empty_failed
import org.openforis.collectone.v2.mobile.resources.mobile_uploads_empty_for_feature
import org.openforis.collectone.v2.mobile.resources.mobile_uploads_empty_in_progress
import org.openforis.collectone.v2.mobile.resources.mobile_uploads_empty_pending
import org.openforis.collectone.v2.mobile.resources.mobile_uploads_empty_uploaded
import org.openforis.collectone.v2.mobile.resources.mobile_uploads_pending_activity_records
import org.openforis.collectone.v2.mobile.resources.mobile_uploads_show_all_features
import org.openforis.collectone.v2.mobile.resources.mobile_uploads_sync_all
import org.openforis.collectone.v2.mobile.resources.mobile_uploads_sync_all_count
import org.openforis.collectone.v2.mobile.resources.mobile_uploads_title

/**
 * Unified `Uploads` screen accessible from the Hamburger Navigation Drawer, merging pending,
 * in-progress, uploaded, and failed mutations into a single compact, user-friendly list with status
 * filter chips (`Pending`, `In progress`, `Uploaded`, `Failed`).
 *
 * Shows the status filter chips, the optional map feature filter chip, and the filtered mutation
 * cards from [uiState]; filter and sync intents go to [actions].
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
          text = stringResource(Res.string.mobile_uploads_title),
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
                  stringResource(
                    Res.string.mobile_uploads_sync_all_count,
                    uiState.outboxMutationCount,
                  )
                } else {
                  stringResource(Res.string.mobile_uploads_sync_all)
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
          Text(stringResource(Res.string.mobile_back), style = MaterialTheme.typography.labelSmall)
        }
      }
    }

    // Activity records (e.g. PDF receipts) saved offline upload with the next sync.
    val pendingActivityRecords = uiState.pendingActivityRecordCount
    if (pendingActivityRecords > 0) {
      Text(
        text =
          pluralStringResource(
            Res.plurals.mobile_uploads_pending_activity_records,
            pendingActivityRecords,
            pendingActivityRecords,
          ),
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
        FlatFilterChip(
          selected = isSelected,
          onClick = { actions.toggleUploadStatusFilter(filter) },
          label = {
            Text(
              text =
                stringResource(
                  Res.string.mobile_upload_filter_chip,
                  uploadStatusFilterLabel(filter),
                  count,
                ),
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
            contentDescription = stringResource(Res.string.mobile_uploads_show_all_features),
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
              activeFilter != null -> uploadsEmptyMessage(activeFilter)
              entityFilter != null -> stringResource(Res.string.mobile_uploads_empty_for_feature)
              else -> stringResource(Res.string.mobile_uploads_empty)
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
      UploadStatusFilter.UPLOADED -> BadgeTone.PRIMARY
      UploadStatusFilter.IN_PROGRESS -> BadgeTone.TERTIARY
      UploadStatusFilter.PENDING -> BadgeTone.NEUTRAL
      UploadStatusFilter.FAILED -> BadgeTone.WARNING
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
          text = "${stringResource(mutation.operationKind.labelRes)} • ${mutation.title}",
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
              text = uploadStatusFilterLabel(statusFilter),
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            )
          }
        } else {
          TonalBadge(text = uploadStatusFilterLabel(statusFilter), tone = badgeTone)
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
                text =
                  if (statusFilter == UploadStatusFilter.FAILED) {
                    stringResource(Res.string.mobile_retry)
                  } else {
                    stringResource(Res.string.mobile_upload)
                  },
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
      } else if (statusFilter == UploadStatusFilter.FAILED) {
        failureText(mutation)?.let { failure ->
          Text(
            text = failure,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

/** Display label of an upload status [filter] (chips and status badges in `Uploads`). */
@Composable
private fun uploadStatusFilterLabel(filter: UploadStatusFilter): String =
  when (filter) {
    UploadStatusFilter.PENDING -> stringResource(Res.string.mobile_upload_status_pending)
    UploadStatusFilter.IN_PROGRESS -> stringResource(Res.string.mobile_upload_status_in_progress)
    UploadStatusFilter.UPLOADED -> stringResource(Res.string.mobile_upload_status_uploaded)
    UploadStatusFilter.FAILED -> stringResource(Res.string.mobile_upload_status_failed_label)
  }

/** Empty-state message of `Uploads` when filtered by upload status [filter]. */
@Composable
private fun uploadsEmptyMessage(filter: UploadStatusFilter): String =
  when (filter) {
    UploadStatusFilter.PENDING -> stringResource(Res.string.mobile_uploads_empty_pending)
    UploadStatusFilter.IN_PROGRESS -> stringResource(Res.string.mobile_uploads_empty_in_progress)
    UploadStatusFilter.UPLOADED -> stringResource(Res.string.mobile_uploads_empty_uploaded)
    UploadStatusFilter.FAILED -> stringResource(Res.string.mobile_uploads_empty_failed)
  }

/**
 * Why [mutation] failed, in the UI language. Changes logged without an outbox error (sample data,
 * or entries from before failures were typed) fall back to their raw [MutationLogItem.stateDetail].
 */
@Composable
private fun failureText(mutation: MutationLogItem): String? =
  mutation.lastError?.let { ErrorMessages.reason(it).resolve() }
    ?: mutation.stateDetail.takeIf { it.isNotBlank() }
