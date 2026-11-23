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
package org.openforis.collectone.v2.app.ui.datacollection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.FormSubmissionsGroup
import org.openforis.collectone.v2.app.domain.model.SubmissionPreviewItem
import org.openforis.collectone.v2.app.domain.model.SubmissionViewer
import org.openforis.collectone.v2.app.domain.model.SyncStatus
import org.openforis.collectone.v2.app.domain.model.updatedAtLabel
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_back_to_searchable_list
import org.openforis.collectone.v2.app.resources.app_collected_at
import org.openforis.collectone.v2.app.resources.app_data_collector
import org.openforis.collectone.v2.app.resources.app_form_action_subtitle
import org.openforis.collectone.v2.app.resources.app_latest
import org.openforis.collectone.v2.app.resources.app_more_submission_actions
import org.openforis.collectone.v2.app.resources.app_schema_version
import org.openforis.collectone.v2.app.resources.app_share_pdf
import org.openforis.collectone.v2.app.resources.app_share_submission_pdf
import org.openforis.collectone.v2.app.resources.app_submission_details
import org.openforis.collectone.v2.app.resources.app_sync_all_changes_saved
import org.openforis.collectone.v2.app.resources.app_sync_synced_description
import org.openforis.collectone.v2.app.resources.app_sync_upload_failed_retry
import org.openforis.collectone.v2.app.resources.app_sync_uploading_changes
import org.openforis.collectone.v2.app.resources.app_sync_uploading_description
import org.openforis.collectone.v2.app.resources.app_target_no_feature
import org.openforis.collectone.v2.app.resources.app_target_no_feature_with_location
import org.openforis.collectone.v2.app.resources.app_target_with_entity
import org.openforis.collectone.v2.app.ui.common.collectorAttributionText
import org.openforis.collectone.v2.app.ui.common.collectorNameText
import org.openforis.collectone.v2.app.ui.text.coordinatesText
import org.openforis.collectone.v2.app.ui.text.targetTypeText
import org.openforis.collectone.v2.app.ui.text.wayfindingBadge
import org.openforis.collectone.v2.core.forms.ui.BadgeTone
import org.openforis.collectone.v2.core.forms.ui.ChoiceColorPillsRow
import org.openforis.collectone.v2.core.forms.ui.ChoiceImageRegistry
import org.openforis.collectone.v2.core.forms.ui.TonalBadge
import org.openforis.collectone.v2.core.forms.ui.controls.AssistChip
import org.openforis.collectone.v2.core.forms.ui.controls.DropdownMenuItem
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.clickable

/**
 * Persistent bottom sheet content for the mobile map screen (`SurveyMapView`):
 * - [SurveyBottomSheetUiState.EntityDetails]: renders [EntityBottomSheetCard] with an `"All map
 *   features"` back pill, `simplestyle-spec` marker, baseline attributes, form launchers, and `1:N`
 *   submissions.
 * - [SurveyBottomSheetUiState.SubmissionDetails]: renders [SubmissionFullDetailsCard] for a
 *   standalone [SubmissionPreviewItem] with a back button calling [onReturnToList].
 * - [SurveyBottomSheetUiState.SearchableList]: renders [BottomSheetSearchableListContent], peeking
 *   at the bottom of the map with the survey title and area name (on mobile) or a search bar (in
 *   the web side panel), and expanding into the full grouped list.
 *
 * The sheet shows what [uiState] holds; the list and the details send their intents to
 * [listActions] and [detailsActions], and a standalone submission's back button calls
 * [onReturnToList]. The web dashboard shows the list in its left-hand panel
 * ([BottomSheetSearchableListContent]) and details in a floating card instead.
 */
@Composable
fun SurveyPersistentBottomSheetContent(
  uiState: SurveyBottomSheetUiState,
  listActions: SearchableListActions,
  detailsActions: EntityDetailsActions,
  onReturnToList: () -> Unit,
  modifier: Modifier = Modifier,
) {
  when (uiState) {
    is SurveyBottomSheetUiState.EntityDetails ->
      EntityBottomSheetCard(
        uiState = uiState.details,
        actions = detailsActions,
        modifier = modifier,
      )
    is SurveyBottomSheetUiState.SubmissionDetails -> {
      val submissionId = uiState.details.submission.id
      Column(
        modifier =
          modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 6.dp)
      ) {
        SubmissionFullDetailsCard(
          uiState = uiState.details,
          backLabel = stringResource(Res.string.app_back_to_searchable_list),
          onBack = onReturnToList,
          onSharePdf = { detailsActions.shareSubmissionPdf(submissionId) },
          onCycleSyncStatus = { detailsActions.cycleSubmissionSyncStatus(submissionId) },
        )
      }
    }
    is SurveyBottomSheetUiState.SearchableList ->
      BottomSheetSearchableListContent(
        uiState = uiState.list,
        actions = listActions,
        modifier = modifier,
      )
  }
}

/**
 * Shared component that renders chronological submissions grouped by form (`FormSubmissionGroup`),
 * used in both [EntityBottomSheetCard] (for a single entity's `1:N` submissions) and
 * [BottomSheetSearchableListContent] (for survey-wide submissions).
 */
@Composable
internal fun FormGroupedSubmissionsSection(
  groups: List<FormSubmissionsGroup>,
  viewer: SubmissionViewer,
  showTargetEntityLabel: Boolean,
  showFormActionSubtitle: Boolean,
  onSelectSubmission: (SubmissionPreviewItem) -> Unit,
  showSyncStatus: Boolean = true,
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    groups.forEach { group ->
      val form = group.form
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        // Form Title Group Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp),
              )
              Text(
                text = form.title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
            if (showFormActionSubtitle) {
              Text(
                text =
                  stringResource(Res.string.app_form_action_subtitle, form.ctaLabel, form.version),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }

        group.submissions.forEachIndexed { index, sub ->
          OutlinedCard(
            onClick = { onSelectSubmission(sub) },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            colors =
              CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
              ) {
                if (showTargetEntityLabel) {
                  val location = sub.location
                  val targetDisplayLabel =
                    if (sub.hasAttachedEntity) {
                      stringResource(
                        Res.string.app_target_with_entity,
                        sub.targetTypeText.resolve(),
                        sub.entityLabel,
                      )
                    } else if (location != null) {
                      stringResource(
                        Res.string.app_target_no_feature_with_location,
                        sub.targetTypeText.resolve(),
                        coordinatesText(location).resolve(),
                      )
                    } else {
                      stringResource(Res.string.app_target_no_feature, sub.targetTypeText.resolve())
                    }
                  Text(
                    text = targetDisplayLabel,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                }
                Row(
                  horizontalArrangement = Arrangement.spacedBy(5.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint =
                      if (showTargetEntityLabel) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                      } else {
                        MaterialTheme.colorScheme.onSurface
                      },
                    modifier = Modifier.size(13.dp),
                  )
                  Text(
                    text = collectorNameText(viewer, sub).resolve(),
                    style =
                      if (showTargetEntityLabel) {
                        MaterialTheme.typography.labelSmall
                      } else {
                        MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                      },
                    color =
                      if (showTargetEntityLabel) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                      } else {
                        MaterialTheme.colorScheme.onSurface
                      },
                  )
                  if (index == 0 && !showTargetEntityLabel) {
                    TonalBadge(
                      text = stringResource(Res.string.app_latest),
                      tone = BadgeTone.SECONDARY,
                    )
                  }
                }
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(11.dp),
                  )
                  Text(
                    text = "${sub.updatedAtLabel} • ${sub.formVersion}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }

              IconButton(onClick = { onSelectSubmission(sub) }, modifier = Modifier.size(32.dp)) {
                Icon(
                  imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                  contentDescription = stringResource(Res.string.app_submission_details),
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(20.dp),
                )
              }
            }
          }
        }
      }
    }
  }
}

/**
 * Full Submission Details Card shown when a submission in a 1:N list or List view is clicked.
 * - [onSharePdf]: shares the submission's PDF on mobile; downloads it from the web side panel's
 *   overflow menu.
 * - [onCycleSyncStatus]: tapping the mobile sync status badge (simulated uploads in the workbench).
 * - [isSidePanel]: the web layout, with an overflow menu instead of the mobile sync status and
 *   Share PDF chips, and distance and bearing badges on geometry answers.
 */
@Composable
fun SubmissionFullDetailsCard(
  uiState: SubmissionDetailsUiState,
  backLabel: String,
  onBack: () -> Unit,
  onSharePdf: () -> Unit,
  onCycleSyncStatus: () -> Unit = {},
  isSidePanel: Boolean = false,
  showBackButton: Boolean = true,
  showHeader: Boolean = true,
  showTitle: Boolean = true,
) {
  val submission = uiState.submission
  // The card uses the same container color as the answer blocks so the submission reads as one
  // document-like surface, set apart from the surrounding panel by a subtle outline.
  OutlinedCard(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      // Header: back arrow navigation, form title, and (side panel) overflow menu. Hosts that
      // already show the form title and actions (e.g. a tab or their own header) hide it.
      if (showHeader) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          if (showBackButton) {
            IconButton(onClick = { onBack() }, modifier = Modifier.size(28.dp)) {
              Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = backLabel,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
              )
            }
          }
          if (showTitle) {
            Text(
              text = submission.formTitle,
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.weight(1f),
            )
          } else {
            Spacer(modifier = Modifier.weight(1f))
          }
          if (isSidePanel) {
            DetailsOverflowMenu(
              actions = listOf(downloadPdfMenuAction(onSharePdf)),
              contentDescription = stringResource(Res.string.app_more_submission_actions),
            )
          }
        }
      }

      // Actions & Sync Status Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (!isSidePanel) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            SyncStatusIndicatorBadge(
              syncStatus = submission.syncStatus,
              onClick = onCycleSyncStatus,
            )

            AssistChip(
              onClick = { onSharePdf() },
              label = {
                Text(
                  text = stringResource(Res.string.app_share_pdf),
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  softWrap = false,
                )
              },
              leadingIcon = {
                Icon(
                  imageVector = Icons.Outlined.Share,
                  contentDescription = stringResource(Res.string.app_share_submission_pdf),
                  modifier = Modifier.size(12.dp),
                )
              },
            )
          }
        }

        Text(
          text = stringResource(Res.string.app_schema_version, submission.formVersion),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          softWrap = false,
        )
      }

      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val location = submission.location
        val detailTargetLabel =
          if (submission.hasAttachedEntity) {
            stringResource(
              Res.string.app_target_with_entity,
              submission.targetTypeText.resolve(),
              submission.entityLabel,
            )
          } else if (location != null) {
            stringResource(
              Res.string.app_target_no_feature_with_location,
              submission.targetTypeText.resolve(),
              coordinatesText(location).resolve(),
            )
          } else {
            stringResource(Res.string.app_target_no_feature, submission.targetTypeText.resolve())
          }
        Text(
          text = detailTargetLabel,
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.primary,
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(12.dp),
          )
          Text(
            text =
              stringResource(
                Res.string.app_data_collector,
                collectorAttributionText(uiState.viewer, submission).resolve(),
              ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(12.dp),
          )
          Text(
            text = stringResource(Res.string.app_collected_at, submission.updatedAtLabel),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      submission.fields.forEach { field ->
        val fieldWayfindingBadge = wayfindingBadge(uiState.fieldWayfinding[field])
        val coloredAnswers =
          remember(field.questionName, field.answerValue, uiState.formDefs) {
            ChoiceImageRegistry.resolveChoiceAnswerColors(
              questionName = field.questionName,
              answerValue = field.answerValue,
              formDefs = uiState.formDefs,
            )
          }
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.surface,
          contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
          Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = field.questionLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
              )
              if (isSidePanel && fieldWayfindingBadge.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                TonalBadge(
                  text = "➤ $fieldWayfindingBadge",
                  tone = BadgeTone.WARNING,
                  monospace = true,
                )
              }
            }
            if (coloredAnswers.isNotEmpty()) {
              ChoiceColorPillsRow(items = coloredAnswers)
            } else {
              Text(
                text = field.answerValue,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }
      }
    }
  }
}

/**
 * Material Design 3 status icon indicating whether a map feature (`GeospatialEntityItem`) or form
 * submission (`SubmissionPreviewItem`) is [SyncStatus.UPLOADING], [SyncStatus.SYNCED], or
 * [SyncStatus.FAILED]. Modeled after Google apps (Drive/Docs/Photos): small subtle icon with tap
 * popup.
 */
@Composable
internal fun SyncStatusIndicatorBadge(
  syncStatus: SyncStatus,
  modifier: Modifier = Modifier,
  countSuffix: String? = null,
  isHighlighted: Boolean = false,
  onClick: (() -> Unit)? = null,
) {
  var showPopup by remember { mutableStateOf(false) }

  val (icon, tint, desc) =
    when (syncStatus) {
      SyncStatus.UPLOADING ->
        Triple(
          Icons.Outlined.CloudUpload,
          MaterialTheme.colorScheme.primary,
          stringResource(Res.string.app_sync_uploading_changes),
        )
      SyncStatus.SYNCED ->
        Triple(
          Icons.Outlined.CloudDone,
          MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
          stringResource(Res.string.app_sync_all_changes_saved),
        )
      SyncStatus.FAILED ->
        Triple(
          Icons.Outlined.CloudOff,
          MaterialTheme.colorScheme.error,
          stringResource(Res.string.app_sync_upload_failed_retry),
        )
    }

  Box(modifier = modifier) {
    Surface(
      shape = CircleShape,
      color =
        if (isHighlighted) {
          when (syncStatus) {
            SyncStatus.UPLOADING -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            SyncStatus.SYNCED -> MaterialTheme.colorScheme.surfaceContainerHigh
            SyncStatus.FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
          }
        } else {
          Color.Transparent
        },
      contentColor = tint,
      modifier =
        Modifier.clip(CircleShape).clickable {
          if (onClick != null) {
            onClick()
          } else {
            showPopup = true
          }
        },
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = desc,
          tint = tint,
          modifier = Modifier.size(16.dp),
        )
        if (countSuffix != null) {
          Text(
            text = countSuffix,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = tint,
          )
        }
      }
    }

    if (showPopup) {
      DropdownMenu(
        expanded = showPopup,
        onDismissRequest = { showPopup = false },
      ) {
        DropdownMenuItem(
          text = {
            Column(modifier = Modifier.padding(4.dp)) {
              Text(
                text = desc,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = syncStatusDescription(syncStatus),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          },
          onClick = {
            showPopup = false
            onClick?.invoke()
          },
        )
      }
    }
  }
}

/** Localized longer description of [syncStatus], shown in the sync status popup. */
@Composable
private fun syncStatusDescription(syncStatus: SyncStatus): String =
  stringResource(
    when (syncStatus) {
      SyncStatus.UPLOADING -> Res.string.app_sync_uploading_description
      SyncStatus.SYNCED -> Res.string.app_sync_synced_description
      SyncStatus.FAILED -> Res.string.app_sync_upload_failed_retry
    }
  )
