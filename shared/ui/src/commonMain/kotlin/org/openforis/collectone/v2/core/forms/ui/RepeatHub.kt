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
package org.openforis.collectone.v2.core.forms.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.ElevatedCard
import org.openforis.collectone.v2.core.forms.ui.controls.FilledTonalButton
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.core.forms.ui.resources.Res
import org.openforis.collectone.v2.core.forms.ui.resources.forms_cancel
import org.openforis.collectone.v2.core.forms.ui.resources.forms_delete
import org.openforis.collectone.v2.core.forms.ui.resources.forms_repeat_add_another
import org.openforis.collectone.v2.core.forms.ui.resources.forms_repeat_add_entries_hint
import org.openforis.collectone.v2.core.forms.ui.resources.forms_repeat_add_entry
import org.openforis.collectone.v2.core.forms.ui.resources.forms_repeat_delete_entry
import org.openforis.collectone.v2.core.forms.ui.resources.forms_repeat_delete_message
import org.openforis.collectone.v2.core.forms.ui.resources.forms_repeat_delete_title
import org.openforis.collectone.v2.core.forms.ui.resources.forms_repeat_no_entries
import org.openforis.collectone.v2.core.forms.ui.resources.forms_repeat_required_count
import org.openforis.collectone.v2.core.forms.ui.resources.forms_repeat_section_entries
import org.openforis.collectone.v2.core.forms.ui.resources.forms_repeat_status_complete
import org.openforis.collectone.v2.core.forms.ui.resources.forms_repeat_status_in_progress
import org.openforis.collectone.v2.core.forms.ui.resources.forms_repeat_status_not_started
import org.openforis.collectone.v2.core.forms.ui.resources.forms_repeat_tap_to_fill
import org.openforis.collectone.v2.core.forms.ui.resources.forms_status_needs_attention

/**
 * The mobile runner's list of a repeat group's entries ([FormWizardStep.RepeatHubStep]): a header
 * with the section label and entry count, one row per entry (status icon, title, answer preview,
 * Delete), an empty state, and an Add button that reads "Add another …" once entries exist.
 *
 * Tapping an entry calls [onOpenEntry] with the entry's instance path. Deleting an entry that holds
 * answers asks for confirmation first; an empty entry is deleted straight away. The web runner
 * frames entries inline instead ([CompactRepeatSection]).
 */
@Composable
internal fun RepeatHub(
  step: FormWizardStep.RepeatHubStep,
  onOpenEntry: (instancePath: String) -> Unit,
  onRemoveEntry: (repeatIndex: Int) -> Unit,
  onAddEntry: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val group = step.repeatGroup
  val label = repeatSectionLabel(group.label?.text, group.canonicalPath)
  val entries = step.summaries

  ElevatedCard(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.elevatedCardColors(containerColor = colors.surfaceContainerLowest),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      RepeatSectionHeader(
        label = label,
        entryCount = entries.size,
        targetCount = group.targetCount,
        compact = false,
      )

      if (entries.isEmpty()) {
        RepeatEmptyState(label = label, compact = false)
      } else {
        entries.forEach { summary ->
          RepeatHubEntry(
            summary = summary,
            canRemove = group.canRemoveInstance,
            onOpen = { onOpenEntry(summary.instancePath) },
            onRemove = { onRemoveEntry(summary.repeatIndex) },
          )
        }
      }

      if (group.canAddInstance) {
        RepeatAddButton(
          label = label,
          hasEntries = entries.isNotEmpty(),
          compact = false,
          onClick = onAddEntry,
        )
      }
    }
  }
}

/** One tappable entry row of the mobile [RepeatHub]. */
@Composable
private fun RepeatHubEntry(
  summary: RepeatInstanceSummary,
  canRemove: Boolean,
  onOpen: () -> Unit,
  onRemove: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val preview =
    summary.previewParts.takeIf { it.isNotEmpty() }?.map { it.resolve() }?.joinToString(" • ")
      ?: stringResource(Res.string.forms_repeat_tap_to_fill)
  val isInvalid = summary.status == RepeatEntryStatus.NEEDS_ATTENTION

  OutlinedCard(
    onClick = onOpen,
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.outlinedCardColors(containerColor = colors.surfaceContainerLow),
    border = BorderStroke(1.dp, if (isInvalid) colors.error else colors.outlineVariant),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      RepeatEntryStatusIcon(status = summary.status, size = 24.dp)
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = summary.title,
          style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
          color = colors.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = preview,
          style = MaterialTheme.typography.bodySmall,
          color = colors.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (canRemove) {
          RepeatEntryDeleteButton(
            entryTitle = summary.title,
            answeredCount = summary.answeredCount,
            onDelete = onRemove,
          )
        }
        Icon(
          imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = null,
          tint = colors.onSurfaceVariant,
          modifier = Modifier.padding(end = 8.dp).size(20.dp),
        )
      }
    }
  }
}

/**
 * The web runner's frame around a repeat group ([CompactFormBlock.RepeatSection]): a header with
 * the section label and entry count, then each entry as a tinted panel headed by its status, title
 * and Delete action and holding the entry's own question cards ([entryContent]), an empty state,
 * and an Add button. With [readOnly] (the Form designer's preview), entries can't be added or
 * deleted.
 */
@Composable
internal fun CompactRepeatSection(
  section: CompactFormBlock.RepeatSection,
  onAddEntry: () -> Unit,
  onRemoveEntry: (repeatIndex: Int) -> Unit,
  readOnly: Boolean,
  entryContent: @Composable (CompactRepeatEntry) -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val group = section.hub?.repeatGroup
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    color = colors.surface,
    contentColor = colors.onSurface,
    border = BorderStroke(1.dp, colors.outlineVariant),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      RepeatSectionHeader(
        label = section.label,
        entryCount = section.entries.size,
        targetCount = section.targetCount,
        compact = true,
      )
      if (section.entries.isEmpty()) {
        RepeatEmptyState(label = section.label, compact = true)
      }
      section.entries.forEach { entry ->
        CompactRepeatEntryPanel(
          entry = entry,
          canRemove = group?.canRemoveInstance == true && !readOnly,
          onRemove = { onRemoveEntry(entry.repeatIndex) },
          content = { entryContent(entry) },
        )
      }
      if (group?.canAddInstance == true) {
        RepeatAddButton(
          label = section.label,
          hasEntries = section.entries.isNotEmpty(),
          compact = true,
          enabled = !readOnly,
          onClick = onAddEntry,
        )
      }
    }
  }
}

@Composable
private fun CompactRepeatEntryPanel(
  entry: CompactRepeatEntry,
  canRemove: Boolean,
  onRemove: () -> Unit,
  content: @Composable () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val status = entry.status
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.small,
    color = colors.surfaceContainerLow,
    border =
      if (status == RepeatEntryStatus.NEEDS_ATTENTION) BorderStroke(1.dp, colors.error) else null,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(start = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        RepeatEntryStatusIcon(status = status, size = 18.dp)
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = entry.title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
          )
          Text(
            text = repeatEntryStatusText(status),
            style = MaterialTheme.typography.labelSmall,
            color =
              if (status == RepeatEntryStatus.NEEDS_ATTENTION) colors.error
              else colors.onSurfaceVariant,
          )
        }
        if (canRemove) {
          RepeatEntryDeleteButton(
            entryTitle = entry.title,
            answeredCount = entry.summary?.answeredCount ?: 0,
            onDelete = onRemove,
            buttonSize = 32.dp,
            iconSize = 18.dp,
          )
        }
      }
      content()
    }
  }
}

/** Section label, entry count (with the required count when the form fixes it). */
@Composable
private fun RepeatSectionHeader(
  label: String,
  entryCount: Int,
  targetCount: Int?,
  compact: Boolean,
) {
  val colors = MaterialTheme.colorScheme
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = label,
        style =
          (if (compact) MaterialTheme.typography.titleSmall
            else MaterialTheme.typography.titleMedium)
            .copy(fontWeight = FontWeight.Bold),
        color = colors.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.semantics { heading() },
      )
      Text(
        text =
          pluralStringResource(Res.plurals.forms_repeat_section_entries, entryCount, entryCount),
        style = MaterialTheme.typography.labelMedium,
        color = colors.onSurfaceVariant,
      )
    }
    if (targetCount != null) {
      TonalBadge(
        text = stringResource(Res.string.forms_repeat_required_count, targetCount),
        tone = BadgeTone.TERTIARY,
      )
    }
  }
}

@Composable
private fun RepeatEmptyState(label: String, compact: Boolean) {
  val colors = MaterialTheme.colorScheme
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = if (compact) MaterialTheme.shapes.small else MaterialTheme.shapes.medium,
    color = colors.surfaceContainerLow,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(if (compact) 12.dp else 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = stringResource(Res.string.forms_repeat_no_entries),
        style =
          (if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium)
            .copy(fontWeight = FontWeight.SemiBold),
        color = colors.onSurface,
        textAlign = TextAlign.Center,
      )
      Text(
        text = stringResource(Res.string.forms_repeat_add_entries_hint, label),
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }
  }
}

/**
 * The Add button: a filled "Add … entry" while the section is empty (the obvious next step), a
 * tonal "Add another … entry" once it has entries (so it doesn't outshine Next / Submit).
 */
@Composable
private fun RepeatAddButton(
  label: String,
  hasEntries: Boolean,
  compact: Boolean,
  onClick: () -> Unit,
  enabled: Boolean = true,
) {
  val text =
    stringResource(
      if (hasEntries) Res.string.forms_repeat_add_another else Res.string.forms_repeat_add_entry,
      label,
    )
  val content: @Composable () -> Unit = {
    Icon(
      imageVector = Icons.Default.Add,
      contentDescription = null,
      modifier = Modifier.size(if (compact) 16.dp else 18.dp),
    )
    Spacer(modifier = Modifier.width(6.dp))
    Text(
      text = text,
      style =
        if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      softWrap = false,
    )
  }
  if (hasEntries) {
    FilledTonalButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
      content()
    }
  } else {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { content() }
  }
}

/**
 * Deletes a repeat entry: an icon button that, when the entry holds answers, first asks for
 * confirmation (naming the entry and how many answers go with it).
 */
@Composable
internal fun RepeatEntryDeleteButton(
  entryTitle: String,
  answeredCount: Int,
  onDelete: () -> Unit,
  buttonSize: Dp = 40.dp,
  iconSize: Dp = 20.dp,
) {
  val colors = MaterialTheme.colorScheme
  var confirming by remember { mutableStateOf(false) }
  IconButton(
    onClick = { if (answeredCount > 0) confirming = true else onDelete() },
    modifier = Modifier.size(buttonSize),
  ) {
    Icon(
      imageVector = Icons.Outlined.Delete,
      contentDescription = stringResource(Res.string.forms_repeat_delete_entry, entryTitle),
      tint = colors.error,
      modifier = Modifier.size(iconSize),
    )
  }
  if (confirming) {
    AlertDialog(
      onDismissRequest = { confirming = false },
      title = { Text(stringResource(Res.string.forms_repeat_delete_title, entryTitle)) },
      text = {
        Text(
          pluralStringResource(
            Res.plurals.forms_repeat_delete_message,
            answeredCount,
            answeredCount,
          )
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            confirming = false
            onDelete()
          },
          colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
        ) {
          Text(stringResource(Res.string.forms_delete))
        }
      },
      dismissButton = {
        TextButton(onClick = { confirming = false }) {
          Text(stringResource(Res.string.forms_cancel))
        }
      },
    )
  }
}

/**
 * Status icon of a repeat entry: a check when complete, a warning when an answer is invalid, a
 * half-filled ring while in progress, and an empty ring before the first answer.
 */
@Composable
internal fun RepeatEntryStatusIcon(status: RepeatEntryStatus, size: Dp) {
  val colors = MaterialTheme.colorScheme
  val description = repeatEntryStatusText(status)
  when (status) {
    RepeatEntryStatus.COMPLETE ->
      Icon(
        imageVector = Icons.Filled.CheckCircle,
        contentDescription = description,
        tint = colors.primary,
        modifier = Modifier.size(size),
      )
    RepeatEntryStatus.NEEDS_ATTENTION ->
      Icon(
        imageVector = Icons.Filled.Warning,
        contentDescription = description,
        tint = colors.error,
        modifier = Modifier.size(size),
      )
    RepeatEntryStatus.IN_PROGRESS,
    RepeatEntryStatus.NOT_STARTED -> {
      val ring = colors.outline
      val fill = colors.primary
      val halfFilled = status == RepeatEntryStatus.IN_PROGRESS
      Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(
          modifier = Modifier.size(size * 0.84f).semantics { contentDescription = description }
        ) {
          val stroke = 2.dp.toPx()
          val radius = (this.size.minDimension - stroke) / 2f
          drawCircle(color = ring, radius = radius, style = Stroke(width = stroke))
          if (halfFilled) {
            val inset = stroke * 1.5f
            drawArc(
              color = fill,
              startAngle = 90f,
              sweepAngle = 180f,
              useCenter = true,
              topLeft = Offset(inset, inset),
              size = Size(this.size.width - 2 * inset, this.size.height - 2 * inset),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun repeatEntryStatusText(status: RepeatEntryStatus): String =
  stringResource(
    when (status) {
      RepeatEntryStatus.NOT_STARTED -> Res.string.forms_repeat_status_not_started
      RepeatEntryStatus.IN_PROGRESS -> Res.string.forms_repeat_status_in_progress
      RepeatEntryStatus.COMPLETE -> Res.string.forms_repeat_status_complete
      RepeatEntryStatus.NEEDS_ATTENTION -> Res.string.forms_status_needs_attention
    }
  )

/** A repeat group's display label: its label, else its data name. */
internal fun repeatSectionLabel(label: String?, canonicalPath: String): String =
  label?.takeIf { it.isNotBlank() }
    ?: canonicalPath.substringAfterLast('/').substringBefore('[').ifBlank { canonicalPath }

/**
 * The repeat hub summary of the entry [context] belongs to, e.g. to know how many answers deleting
 * it would discard; `null` when the repeat has no hub (its entry count is fixed by the form).
 */
internal fun FormWizardController.repeatEntrySummary(
  context: RepeatStepContext
): RepeatInstanceSummary? =
  steps
    .firstOrNull {
      it is FormWizardStep.RepeatHubStep && it.repeatGroup.canonicalPath == context.repeatGroupPath
    }
    ?.let { it as FormWizardStep.RepeatHubStep }
    ?.summaries
    ?.firstOrNull { it.instancePath == context.instancePath }
