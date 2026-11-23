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
package org.openforis.collectone.v2.web.ui.organization

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.LibraryStatus
import org.openforis.collectone.v2.app.ui.text.labelRes
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.org_cancel
import org.openforis.collectone.v2.web.resources.org_delete_item
import org.openforis.collectone.v2.web.resources.org_edit_item
import org.openforis.collectone.v2.web.resources.org_field_description_english
import org.openforis.collectone.v2.web.resources.org_field_title_english
import org.openforis.collectone.v2.web.resources.org_hide
import org.openforis.collectone.v2.web.resources.org_save
import org.openforis.collectone.v2.web.resources.org_show
import org.openforis.collectone.v2.web.ui.text.OrganizationMessage

// The organization page's library tabs: Purposes, Dictionary, and Templates. On "All users" they
// edit the global library; elsewhere the organization's entries come first and global entries are
// read-only, with a Hide toggle for global templates and Purpose Packs.

@Composable
internal fun statusBadge(status: LibraryStatus): String? =
  stringResource(status.labelRes).takeIf { status != LibraryStatus.STABLE }

/** A template or Purpose Pack row: details, then Rename/Delete or Hide/Show actions. */
@Composable
internal fun EntryRowLayout(
  title: String,
  id: String? = null,
  description: String,
  badges: List<String>,
  summary: String,
  dimmed: Boolean,
  row: LibraryEntryRow<*>,
  entryLabel: String,
  onRename: () -> Unit,
  onDelete: () -> Unit,
  onSetHidden: (Boolean) -> Unit,
  extraContent: (@Composable () -> Unit)? = null,
) {
  LibraryEntryCard {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          EntryTitleRow(title = title, badges = badges, dimmed = dimmed)
          if (id != null) {
            MonospaceId(id)
          }
          if (description.isNotBlank()) {
            Text(description, style = MaterialTheme.typography.bodySmall)
          }
          if (summary.isNotBlank()) {
            Text(
              summary,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        if (row.canEdit) {
          IconButton(onClick = onRename) {
            Icon(
              Icons.Outlined.Edit,
              contentDescription = stringResource(Res.string.org_edit_item, entryLabel),
            )
          }
          IconButton(onClick = onDelete) {
            Icon(
              Icons.Outlined.Delete,
              contentDescription = stringResource(Res.string.org_delete_item, entryLabel),
            )
          }
        }
        if (row.canHide) {
          TextButton(onClick = { onSetHidden(!row.isHidden) }) {
            Icon(
              if (row.isHidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
              contentDescription = null,
            )
            Spacer(Modifier.width(6.dp))
            Text(
              if (row.isHidden) stringResource(Res.string.org_show)
              else stringResource(Res.string.org_hide)
            )
          }
        }
      }
      extraContent?.invoke()
    }
  }
}

@Composable
internal fun LibraryEntryCard(content: @Composable () -> Unit) {
  OutlinedCard(
    modifier = Modifier.fillMaxWidth(),
    colors =
      CardDefaults.outlinedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
      ),
  ) {
    Box(modifier = Modifier.fillMaxWidth().padding(14.dp)) { content() }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EntryTitleRow(title: String, badges: List<String>, dimmed: Boolean = false) {
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(
      title,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold,
      color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
      modifier = Modifier.align(Alignment.CenterVertically),
    )
    badges.forEach { badge ->
      Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.align(Alignment.CenterVertically),
      ) {
        Text(
          badge,
          style = MaterialTheme.typography.labelSmall,
          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
      }
    }
  }
}

@Composable
internal fun MonospaceId(id: String) {
  Text(
    id,
    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

/** Edits the English title and description of a template or Purpose Pack. */
@Composable
private fun RenameEntryDialog(
  title: String,
  initialTitle: String,
  initialDescription: String,
  onSave: (title: String, description: String) -> OrganizationMessage?,
  onDismiss: () -> Unit,
) {
  var entryTitle by remember { mutableStateOf(initialTitle) }
  var description by remember { mutableStateOf(initialDescription) }
  var error by remember { mutableStateOf<OrganizationMessage?>(null) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = entryTitle,
          onValueChange = {
            entryTitle = it
            error = null
          },
          label = { Text(stringResource(Res.string.org_field_title_english)) },
          singleLine = true,
          isError = error != null,
          supportingText = error?.let { { Text(it.asString()) } },
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text(stringResource(Res.string.org_field_description_english)) },
          minLines = 2,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { error = onSave(entryTitle, description) },
        enabled = entryTitle.isNotBlank(),
      ) {
        Text(stringResource(Res.string.org_save))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(Res.string.org_cancel)) }
    },
  )
}

@Composable
internal fun ConfirmDeleteDialog(
  title: String,
  body: String,
  confirmLabel: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(body) },
    confirmButton = {
      Button(
        onClick = onConfirm,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
      ) {
        Text(confirmLabel)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(Res.string.org_cancel)) }
    },
  )
}
