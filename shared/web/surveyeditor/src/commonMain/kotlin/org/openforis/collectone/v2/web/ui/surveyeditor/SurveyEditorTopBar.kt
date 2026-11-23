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
package org.openforis.collectone.v2.web.ui.surveyeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.SurveyLifecycleState
import org.openforis.collectone.v2.app.ui.text.labelRes
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.surveyeditor_close
import org.openforis.collectone.v2.web.resources.surveyeditor_header_all_changes_published
import org.openforis.collectone.v2.web.resources.surveyeditor_header_discard
import org.openforis.collectone.v2.web.resources.surveyeditor_header_discard_unpublished_message
import org.openforis.collectone.v2.web.resources.surveyeditor_header_discard_unpublished_title
import org.openforis.collectone.v2.web.resources.surveyeditor_header_discard_unsaved_message
import org.openforis.collectone.v2.web.resources.surveyeditor_header_discard_unsaved_title
import org.openforis.collectone.v2.web.resources.surveyeditor_header_draft_saved
import org.openforis.collectone.v2.web.resources.surveyeditor_header_fix_issues_to_publish
import org.openforis.collectone.v2.web.resources.surveyeditor_header_fix_issues_to_save
import org.openforis.collectone.v2.web.resources.surveyeditor_header_keep_editing
import org.openforis.collectone.v2.web.resources.surveyeditor_header_survey_editor
import org.openforis.collectone.v2.web.resources.surveyeditor_header_unpublished_changes
import org.openforis.collectone.v2.web.resources.surveyeditor_header_unsaved_changes
import org.openforis.collectone.v2.web.resources.surveyeditor_lifecycle_reopen
import org.openforis.collectone.v2.web.resources.surveyeditor_untitled_survey
import org.openforis.collectone.v2.web.resources.surveyeditor_working_copy_continue
import org.openforis.collectone.v2.web.resources.surveyeditor_working_copy_discard
import org.openforis.collectone.v2.web.resources.surveyeditor_working_copy_prompt_draft_message
import org.openforis.collectone.v2.web.resources.surveyeditor_working_copy_prompt_published_message
import org.openforis.collectone.v2.web.resources.surveyeditor_working_copy_prompt_title
import org.openforis.collectone.v2.web.ui.common.WebHeaderContext
import org.openforis.collectone.v2.web.ui.common.WebHeaderSupportingText

/**
 * Close button, survey title, and clickable lifecycle status badge on the left; Discard and Save
 * draft / Publish changes on the right. Close and Discard both run [SurveyEditorActions.close],
 * asking for confirmation first if there are uncommitted changes.
 */
@Composable
internal fun SurveyEditorTopBar(
  uiState: SurveyEditorUiState,
  actions: SurveyLifecycleActions,
  header:
    @Composable
    (
      navigationIcon: @Composable () -> Unit,
      context: @Composable () -> Unit,
      headerActions: @Composable () -> Unit,
    ) -> Unit,
) {
  val hasChanges = uiState.hasUnpublishedChanges
  val issueCount = uiState.issueCount
  var isConfirmingDiscard by remember { mutableStateOf(false) }

  header(
    {},
    {
      WebHeaderContext(
        title =
          uiState.details.title.ifBlank { stringResource(Res.string.surveyeditor_untitled_survey) },
        titleTrailing = {
          SurveyStatusBadgeMenu(
            lifecycleState = uiState.lifecycleState,
            canManageSurvey = uiState.canManageSurvey,
            hasUnpublishedChanges = hasChanges,
            onSelectState = actions::setLifecycleState,
          )
        },
      ) {
        WebHeaderSupportingText(
          stringResource(Res.string.surveyeditor_header_survey_editor),
          color = MaterialTheme.colorScheme.primary,
        )
        WebHeaderSupportingText("·")
        uiState.organization(uiState.details.organizationId)?.let { organization ->
          WebHeaderSupportingText(organization.name)
          WebHeaderSupportingText("·")
        }
        when {
          uiState.isReadOnly ->
            WebHeaderSupportingText(stringResource(uiState.lifecycleState.labelRes))
          issueCount > 0 ->
            WebHeaderSupportingText(
              pluralStringResource(
                if (uiState.isDraft) Res.plurals.surveyeditor_header_fix_issues_to_save
                else Res.plurals.surveyeditor_header_fix_issues_to_publish,
                issueCount,
                issueCount,
              ),
              color = MaterialTheme.colorScheme.error,
            )
          hasChanges ->
            WebHeaderSupportingText(
              if (uiState.isDraft) stringResource(Res.string.surveyeditor_header_unsaved_changes)
              else stringResource(Res.string.surveyeditor_header_unpublished_changes)
            )
          else ->
            WebHeaderSupportingText(
              if (uiState.isDraft) stringResource(Res.string.surveyeditor_header_draft_saved)
              else stringResource(Res.string.surveyeditor_header_all_changes_published)
            )
        }
      }
    },
    {
      if (uiState.isReadOnly && uiState.canManageSurvey) {
        OutlinedButton(onClick = { actions.setLifecycleState(SurveyLifecycleState.PUBLISHED) }) {
          Text(stringResource(Res.string.surveyeditor_lifecycle_reopen))
        }
      }
      if (!hasChanges) {
        OutlinedButton(onClick = actions::close) {
          Text(stringResource(Res.string.surveyeditor_close))
        }
      } else {
        TextButton(onClick = { isConfirmingDiscard = true }, enabled = !uiState.isSaving) {
          Text(stringResource(Res.string.surveyeditor_header_discard))
        }
        val canSaveOrPublish =
          if (uiState.isDraft) !uiState.isReadOnly && issueCount == 0 && !uiState.isSaving
          else uiState.canPublish && !uiState.isSaving
        Button(onClick = actions::publish, enabled = canSaveOrPublish) {
          if (uiState.isSaving) {
            CircularProgressIndicator(
              modifier = Modifier.size(16.dp),
              strokeWidth = 2.dp,
              color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.width(8.dp))
          }
          Text(stringResource(uiState.saveActionLabel))
        }
      }
    },
  )

  if (isConfirmingDiscard) {
    AlertDialog(
      onDismissRequest = { isConfirmingDiscard = false },
      title = {
        Text(
          if (uiState.isDraft) stringResource(Res.string.surveyeditor_header_discard_unsaved_title)
          else stringResource(Res.string.surveyeditor_header_discard_unpublished_title)
        )
      },
      text = {
        Text(
          if (uiState.isDraft) {
            stringResource(Res.string.surveyeditor_header_discard_unsaved_message)
          } else {
            stringResource(Res.string.surveyeditor_header_discard_unpublished_message)
          }
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            isConfirmingDiscard = false
            actions.close()
          }
        ) {
          Text(stringResource(Res.string.surveyeditor_header_discard))
        }
      },
      dismissButton = {
        TextButton(onClick = { isConfirmingDiscard = false }) {
          Text(stringResource(Res.string.surveyeditor_header_keep_editing))
        }
      },
    )
  }

  if (uiState.isPromptingWorkingCopy) {
    AlertDialog(
      onDismissRequest = actions::continueWorkingCopy,
      title = { Text(stringResource(Res.string.surveyeditor_working_copy_prompt_title)) },
      text = {
        Text(
          if (uiState.isDraft) {
            stringResource(Res.string.surveyeditor_working_copy_prompt_draft_message)
          } else {
            stringResource(Res.string.surveyeditor_working_copy_prompt_published_message)
          }
        )
      },
      confirmButton = {
        TextButton(onClick = actions::continueWorkingCopy) {
          Text(stringResource(Res.string.surveyeditor_working_copy_continue))
        }
      },
      dismissButton = {
        TextButton(onClick = actions::discardChanges) {
          Text(stringResource(Res.string.surveyeditor_working_copy_discard))
        }
      },
    )
  }
}

/** Banner shown while the survey is closed or archived, with Reopen for people who manage it. */
@Composable
internal fun ReadOnlyBanner(
  message: String,
  uiState: SurveyEditorUiState,
  actions: SurveyLifecycleActions,
) {
  Surface(
    color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
      Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
      if (uiState.canManageSurvey) {
        TextButton(onClick = { actions.setLifecycleState(SurveyLifecycleState.PUBLISHED) }) {
          Text(stringResource(Res.string.surveyeditor_lifecycle_reopen))
        }
      }
    }
  }
}
