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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.PurposePack
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.Checkbox
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.core.forms.ui.controls.clickable
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.org_cancel
import org.openforis.collectone.v2.web.resources.org_field_description_english
import org.openforis.collectone.v2.web.resources.org_save
import org.openforis.collectone.v2.web.resources.org_template_associated_purposes
import org.openforis.collectone.v2.web.resources.org_template_discard
import org.openforis.collectone.v2.web.resources.org_template_discard_body
import org.openforis.collectone.v2.web.resources.org_template_discard_title
import org.openforis.collectone.v2.web.resources.org_template_edit_title
import org.openforis.collectone.v2.web.resources.org_template_keep_editing
import org.openforis.collectone.v2.web.resources.org_template_new_title
import org.openforis.collectone.v2.web.ui.formeditor.FormEditorPage
import org.openforis.collectone.v2.web.ui.text.OrganizationMessage

/**
 * Full-screen editor of a library template: the shared [FormEditorPage] (in template mode, see
 * [org.openforis.collectone.v2.web.ui.formeditor.FormEditorUiState.isTemplate]) with the template's
 * description and Purpose Packs in its Form properties, and **Save** / **Cancel** in a header.
 * Closing with unsaved edits asks first. [onSave] returns why nothing was saved (shown in the
 * header), or `null` once saving started, which closes the editor.
 */
@Composable
internal fun TemplateEditorDialog(
  session: TemplateEditorSession,
  availablePurposes: List<PurposePack>,
  onSave: () -> OrganizationMessage?,
  onDismiss: () -> Unit,
) {
  DisposableEffect(session) { onDispose { session.close() } }
  val formState by session.formEditor.uiState.collectAsState()
  val previewController by session.formEditor.previewController.collectAsState()
  val description by session.description.collectAsState()
  val purposePackIds by session.purposePackIds.collectAsState()
  var error by remember(session) { mutableStateOf<OrganizationMessage?>(null) }
  var confirmingDiscard by remember(session) { mutableStateOf(false) }
  val requestClose = {
    // Read through the session so edits made since the last recomposition count.
    if (session.isDirty) confirmingDiscard = true else onDismiss()
  }

  if (confirmingDiscard) {
    DiscardTemplateChangesDialog(
      onConfirm = {
        confirmingDiscard = false
        onDismiss()
      },
      onDismiss = { confirmingDiscard = false },
    )
  }

  Dialog(
    onDismissRequest = requestClose,
    properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
  ) {
    Surface(
      modifier = Modifier.fillMaxSize().padding(24.dp).testTag(TEMPLATE_EDITOR_TAG),
      shape = MaterialTheme.shapes.large,
      color = MaterialTheme.colorScheme.surface,
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        TemplateEditorHeader(
          isNew = session.templateId == null,
          canSave = formState.form.title.isNotBlank(),
          error = error,
          onRequestClose = requestClose,
          onSave = {
            error = onSave()
            if (error == null) onDismiss()
          },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        FormEditorPage(
          uiState = formState,
          actions = session.formEditor,
          previewController = previewController,
          isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < DARK_SURFACE_LUMINANCE,
          modifier = Modifier.weight(1f),
          templateDetails = {
            TemplateDetailsFields(
              description = description,
              onDescriptionChange = {
                error = null
                session.updateDescription(it)
              },
              availablePurposes = availablePurposes,
              selectedPurposeIds = purposePackIds,
              onPurposeChange = session::setPurposePack,
            )
          },
        )
      }
    }
  }
}

@Composable
private fun DiscardTemplateChangesDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(Res.string.org_template_discard_title)) },
    text = { Text(stringResource(Res.string.org_template_discard_body)) },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text(
          stringResource(Res.string.org_template_discard),
          color = MaterialTheme.colorScheme.error,
        )
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(Res.string.org_template_keep_editing)) }
    },
  )
}

@Composable
private fun TemplateEditorHeader(
  isNew: Boolean,
  canSave: Boolean,
  error: OrganizationMessage?,
  onRequestClose: () -> Unit,
  onSave: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    IconButton(onClick = onRequestClose) {
      Icon(Icons.Outlined.Close, contentDescription = stringResource(Res.string.org_cancel))
    }
    Text(
      stringResource(
        if (isNew) Res.string.org_template_new_title else Res.string.org_template_edit_title
      ),
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.weight(1f),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    error?.let {
      Text(
        it.asString(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.widthIn(max = 360.dp),
      )
    }
    TextButton(onClick = onRequestClose) { Text(stringResource(Res.string.org_cancel)) }
    Button(onClick = onSave, enabled = canSave) { Text(stringResource(Res.string.org_save)) }
  }
}

/** The template's English description and the Purpose Packs listing it. */
@Composable
private fun TemplateDetailsFields(
  description: String,
  onDescriptionChange: (String) -> Unit,
  availablePurposes: List<PurposePack>,
  selectedPurposeIds: Set<String>,
  onPurposeChange: (packId: String, listed: Boolean) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedTextField(
      value = description,
      onValueChange = onDescriptionChange,
      label = { Text(stringResource(Res.string.org_field_description_english)) },
      minLines = 2,
      modifier = Modifier.fillMaxWidth(),
    )
    if (availablePurposes.isNotEmpty()) {
      Text(
        stringResource(Res.string.org_template_associated_purposes),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
      )
      availablePurposes.forEach { purpose ->
        val isChecked = purpose.id in selectedPurposeIds
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier =
            Modifier.fillMaxWidth()
              .clickable { onPurposeChange(purpose.id, !isChecked) }
              .padding(vertical = 2.dp),
        ) {
          Checkbox(checked = isChecked, onCheckedChange = { onPurposeChange(purpose.id, it) })
          Spacer(Modifier.width(8.dp))
          Text(purpose.title.text, style = MaterialTheme.typography.bodyMedium)
        }
      }
    }
  }
}

/** Test tag of the template editor's surface. */
internal const val TEMPLATE_EDITOR_TAG = "template-editor"

/** Surfaces darker than this luminance mean the dark theme is on (for the Form preview). */
private const val DARK_SURFACE_LUMINANCE = 0.5f
