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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.SurveyLifecycleState
import org.openforis.collectone.v2.app.ui.text.labelRes
import org.openforis.collectone.v2.core.forms.ui.BadgeTone
import org.openforis.collectone.v2.core.forms.ui.TonalBadge
import org.openforis.collectone.v2.core.forms.ui.controls.DropdownMenuItem
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.core.forms.ui.controls.clickable
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.surveyeditor_cancel
import org.openforis.collectone.v2.web.resources.surveyeditor_lifecycle_archive
import org.openforis.collectone.v2.web.resources.surveyeditor_lifecycle_archive_message
import org.openforis.collectone.v2.web.resources.surveyeditor_lifecycle_archive_message_discard
import org.openforis.collectone.v2.web.resources.surveyeditor_lifecycle_archive_survey
import org.openforis.collectone.v2.web.resources.surveyeditor_lifecycle_archive_survey_description
import org.openforis.collectone.v2.web.resources.surveyeditor_lifecycle_archive_title
import org.openforis.collectone.v2.web.resources.surveyeditor_lifecycle_archived_description
import org.openforis.collectone.v2.web.resources.surveyeditor_lifecycle_close_message
import org.openforis.collectone.v2.web.resources.surveyeditor_lifecycle_close_message_discard
import org.openforis.collectone.v2.web.resources.surveyeditor_lifecycle_close_survey
import org.openforis.collectone.v2.web.resources.surveyeditor_lifecycle_close_survey_description
import org.openforis.collectone.v2.web.resources.surveyeditor_lifecycle_close_title
import org.openforis.collectone.v2.web.resources.surveyeditor_lifecycle_closed_description
import org.openforis.collectone.v2.web.resources.surveyeditor_lifecycle_draft_description
import org.openforis.collectone.v2.web.resources.surveyeditor_lifecycle_publish_survey
import org.openforis.collectone.v2.web.resources.surveyeditor_lifecycle_publish_survey_description
import org.openforis.collectone.v2.web.resources.surveyeditor_lifecycle_published_description
import org.openforis.collectone.v2.web.resources.surveyeditor_lifecycle_reopen_from_archived_description
import org.openforis.collectone.v2.web.resources.surveyeditor_lifecycle_reopen_from_closed_description
import org.openforis.collectone.v2.web.resources.surveyeditor_lifecycle_reopen_survey
import org.openforis.collectone.v2.web.resources.surveyeditor_lifecycle_status_badge_description
import org.openforis.collectone.v2.web.resources.surveyeditor_lifecycle_status_label
import org.openforis.collectone.v2.web.resources.surveyeditor_lifecycle_switch_to_draft
import org.openforis.collectone.v2.web.resources.surveyeditor_lifecycle_switch_to_draft_from_archived_description
import org.openforis.collectone.v2.web.resources.surveyeditor_lifecycle_switch_to_draft_from_closed_description
import org.openforis.collectone.v2.web.resources.surveyeditor_lifecycle_switch_to_draft_from_published_description

/** Semantic badge tone for each [SurveyLifecycleState]. */
internal fun SurveyLifecycleState.badgeTone(): BadgeTone =
  when (this) {
    SurveyLifecycleState.DRAFT -> BadgeTone.WARNING
    SurveyLifecycleState.PUBLISHED -> BadgeTone.PRIMARY
    SurveyLifecycleState.CLOSED -> BadgeTone.SECONDARY
    SurveyLifecycleState.ARCHIVED -> BadgeTone.NEUTRAL
  }

/** Plain-language summary of what a [SurveyLifecycleState] means for organizers and collectors. */
@Composable
internal fun SurveyLifecycleState.statusDescription(): String =
  when (this) {
    SurveyLifecycleState.DRAFT ->
      stringResource(Res.string.surveyeditor_lifecycle_draft_description)
    SurveyLifecycleState.PUBLISHED ->
      stringResource(Res.string.surveyeditor_lifecycle_published_description)
    SurveyLifecycleState.CLOSED ->
      stringResource(Res.string.surveyeditor_lifecycle_closed_description)
    SurveyLifecycleState.ARCHIVED ->
      stringResource(Res.string.surveyeditor_lifecycle_archived_description)
  }

/** One lifecycle transition offered in [SurveyStatusBadgeMenu] and `Survey details`. */
internal data class SurveyLifecycleActionOption(
  val targetState: SurveyLifecycleState,
  val label: StringResource,
  val description: StringResource,
  val requiresConfirmation: Boolean = false,
)

/** Available lifecycle state transitions from [current]. */
internal fun lifecycleActionOptions(
  current: SurveyLifecycleState
): List<SurveyLifecycleActionOption> =
  when (current) {
    SurveyLifecycleState.DRAFT ->
      listOf(
        SurveyLifecycleActionOption(
          targetState = SurveyLifecycleState.PUBLISHED,
          label = Res.string.surveyeditor_lifecycle_publish_survey,
          description = Res.string.surveyeditor_lifecycle_publish_survey_description,
        ),
        SurveyLifecycleActionOption(
          targetState = SurveyLifecycleState.ARCHIVED,
          label = Res.string.surveyeditor_lifecycle_archive_survey,
          description = Res.string.surveyeditor_lifecycle_archive_survey_description,
          requiresConfirmation = true,
        ),
      )
    SurveyLifecycleState.PUBLISHED ->
      listOf(
        SurveyLifecycleActionOption(
          targetState = SurveyLifecycleState.DRAFT,
          label = Res.string.surveyeditor_lifecycle_switch_to_draft,
          description =
            Res.string.surveyeditor_lifecycle_switch_to_draft_from_published_description,
        ),
        SurveyLifecycleActionOption(
          targetState = SurveyLifecycleState.CLOSED,
          label = Res.string.surveyeditor_lifecycle_close_survey,
          description = Res.string.surveyeditor_lifecycle_close_survey_description,
          requiresConfirmation = true,
        ),
        SurveyLifecycleActionOption(
          targetState = SurveyLifecycleState.ARCHIVED,
          label = Res.string.surveyeditor_lifecycle_archive_survey,
          description = Res.string.surveyeditor_lifecycle_archive_survey_description,
          requiresConfirmation = true,
        ),
      )
    SurveyLifecycleState.CLOSED ->
      listOf(
        SurveyLifecycleActionOption(
          targetState = SurveyLifecycleState.PUBLISHED,
          label = Res.string.surveyeditor_lifecycle_reopen_survey,
          description = Res.string.surveyeditor_lifecycle_reopen_from_closed_description,
        ),
        SurveyLifecycleActionOption(
          targetState = SurveyLifecycleState.DRAFT,
          label = Res.string.surveyeditor_lifecycle_switch_to_draft,
          description = Res.string.surveyeditor_lifecycle_switch_to_draft_from_closed_description,
        ),
        SurveyLifecycleActionOption(
          targetState = SurveyLifecycleState.ARCHIVED,
          label = Res.string.surveyeditor_lifecycle_archive_survey,
          description = Res.string.surveyeditor_lifecycle_archive_survey_description,
          requiresConfirmation = true,
        ),
      )
    SurveyLifecycleState.ARCHIVED ->
      listOf(
        SurveyLifecycleActionOption(
          targetState = SurveyLifecycleState.PUBLISHED,
          label = Res.string.surveyeditor_lifecycle_reopen_survey,
          description = Res.string.surveyeditor_lifecycle_reopen_from_archived_description,
        ),
        SurveyLifecycleActionOption(
          targetState = SurveyLifecycleState.DRAFT,
          label = Res.string.surveyeditor_lifecycle_switch_to_draft,
          description = Res.string.surveyeditor_lifecycle_switch_to_draft_from_archived_description,
        ),
      )
  }

/**
 * Read-only tonal status pill (`Draft`, `Published`, `Closed`, `Archived`), for places that show a
 * survey's status without managing it, such as the dashboard's top banner.
 */
@Composable
fun SurveyStatusBadge(lifecycleState: SurveyLifecycleState, modifier: Modifier = Modifier) {
  TonalBadge(
    text = stringResource(lifecycleState.labelRes),
    tone = lifecycleState.badgeTone(),
    modifier = modifier,
  )
}

/**
 * Tonal status pill shown next to the survey title in the Survey editor (`Draft`, `Published`,
 * `Closed`, `Archived`). When [canManageSurvey] is `true`, clicking it opens a dropdown menu with
 * options to publish or change the survey's lifecycle state; otherwise it is a [SurveyStatusBadge].
 */
@Composable
fun SurveyStatusBadgeMenu(
  lifecycleState: SurveyLifecycleState,
  canManageSurvey: Boolean,
  hasUnpublishedChanges: Boolean = false,
  onSelectState: (SurveyLifecycleState) -> Unit,
  modifier: Modifier = Modifier,
) {
  val options = remember(lifecycleState) { lifecycleActionOptions(lifecycleState) }
  var isOpen by remember { mutableStateOf(false) }
  var confirmingState by remember { mutableStateOf<SurveyLifecycleState?>(null) }
  val badgeDescription =
    stringResource(
      Res.string.surveyeditor_lifecycle_status_badge_description,
      stringResource(lifecycleState.labelRes),
    )

  if (!canManageSurvey || options.isEmpty()) {
    SurveyStatusBadge(lifecycleState = lifecycleState, modifier = modifier)
    return
  }

  val colors = MaterialTheme.colorScheme
  val (containerColor, contentColor) =
    when (lifecycleState.badgeTone()) {
      BadgeTone.PRIMARY -> colors.primaryContainer to colors.onPrimaryContainer
      BadgeTone.SECONDARY -> colors.secondaryContainer to colors.onSecondaryContainer
      BadgeTone.TERTIARY,
      BadgeTone.WARNING -> colors.tertiaryContainer to colors.onTertiaryContainer
      BadgeTone.ERROR -> colors.errorContainer to colors.onErrorContainer
      BadgeTone.NEUTRAL -> colors.surfaceContainerHigh to colors.onSurfaceVariant
    }

  Box(modifier = modifier) {
    Surface(
      shape = CircleShape,
      color = containerColor,
      contentColor = contentColor,
      modifier =
        Modifier.clip(CircleShape)
          .clickable { isOpen = !isOpen }
          .semantics {
            contentDescription = badgeDescription
          },
    ) {
      Row(
        modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Text(
          text = stringResource(lifecycleState.labelRes),
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
          maxLines = 1,
          softWrap = false,
        )
        Icon(
          imageVector = Icons.Outlined.ArrowDropDown,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
        )
      }
    }

    DropdownMenu(
      expanded = isOpen,
      onDismissRequest = { isOpen = false },
      modifier = Modifier.width(300.dp),
    ) {
      Text(
        text =
          stringResource(
            Res.string.surveyeditor_lifecycle_status_label,
            stringResource(lifecycleState.labelRes),
          ),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
      )
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      options.forEach { option ->
        DropdownMenuItem(
          text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(
                text = stringResource(option.label),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
              )
              Text(
                text = stringResource(option.description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          },
          onClick = {
            isOpen = false
            if (option.requiresConfirmation) {
              confirmingState = option.targetState
            } else {
              onSelectState(option.targetState)
            }
          },
        )
      }
    }
  }

  confirmingState?.let { state ->
    ConfirmLifecycleDialog(
      state = state,
      hasUnpublishedChanges = hasUnpublishedChanges,
      onConfirm = {
        confirmingState = null
        onSelectState(state)
      },
      onDismiss = { confirmingState = null },
    )
  }
}

/** Asks before closing or archiving the survey. */
@Composable
private fun ConfirmLifecycleDialog(
  state: SurveyLifecycleState,
  hasUnpublishedChanges: Boolean,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  val isArchive = state == SurveyLifecycleState.ARCHIVED
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        stringResource(
          if (isArchive) Res.string.surveyeditor_lifecycle_archive_title
          else Res.string.surveyeditor_lifecycle_close_title
        )
      )
    },
    text = {
      Text(
        stringResource(
          when {
            isArchive && hasUnpublishedChanges ->
              Res.string.surveyeditor_lifecycle_archive_message_discard
            isArchive -> Res.string.surveyeditor_lifecycle_archive_message
            hasUnpublishedChanges -> Res.string.surveyeditor_lifecycle_close_message_discard
            else -> Res.string.surveyeditor_lifecycle_close_message
          }
        )
      )
    },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text(
          stringResource(
            if (isArchive) Res.string.surveyeditor_lifecycle_archive
            else Res.string.surveyeditor_lifecycle_close_survey
          )
        )
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(Res.string.surveyeditor_cancel)) }
    },
  )
}
