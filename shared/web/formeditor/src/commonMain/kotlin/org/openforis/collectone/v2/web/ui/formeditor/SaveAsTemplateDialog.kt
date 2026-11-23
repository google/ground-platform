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
package org.openforis.collectone.v2.web.ui.formeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.editor.SurveyFormTemplates
import org.openforis.collectone.v2.app.ui.common.AdminOnlyBadge
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.RadioButton
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.core.forms.ui.controls.selectable
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.formeditor_cancel
import org.openforis.collectone.v2.web.resources.formeditor_done
import org.openforis.collectone.v2.web.resources.formeditor_save_as_template_intro
import org.openforis.collectone.v2.web.resources.formeditor_save_as_template_title
import org.openforis.collectone.v2.web.resources.formeditor_save_template
import org.openforis.collectone.v2.web.resources.formeditor_save_template_managers_only
import org.openforis.collectone.v2.web.resources.formeditor_save_template_managers_only_this_organization
import org.openforis.collectone.v2.web.resources.formeditor_save_template_needs_organization
import org.openforis.collectone.v2.web.resources.formeditor_saving_to_global_library
import org.openforis.collectone.v2.web.resources.formeditor_template_description_optional
import org.openforis.collectone.v2.web.resources.formeditor_template_destination_global
import org.openforis.collectone.v2.web.resources.formeditor_template_destination_organization
import org.openforis.collectone.v2.web.resources.formeditor_template_destination_your_organization
import org.openforis.collectone.v2.web.resources.formeditor_template_dropped_links
import org.openforis.collectone.v2.web.resources.formeditor_template_dropped_links_your_organization
import org.openforis.collectone.v2.web.resources.formeditor_template_name
import org.openforis.collectone.v2.web.resources.formeditor_template_question_summary
import org.openforis.collectone.v2.web.resources.formeditor_template_question_summary_linked
import org.openforis.collectone.v2.web.resources.formeditor_template_saved_global
import org.openforis.collectone.v2.web.resources.formeditor_template_saved_organization
import org.openforis.collectone.v2.web.resources.formeditor_template_saved_title
import org.openforis.collectone.v2.web.resources.formeditor_template_saved_your_organization

/** Whether the signed-in user may save this Form as a template anywhere. */
val FormEditorUiState.canSaveAsTemplate: Boolean
  get() = library.canAddToDictionary || library.canSaveToGlobalLibrary

/** Why **Save as template** is unavailable; shown with [text]. */
sealed interface SaveAsTemplateUnavailableReason {
  /** The survey isn't in an organization. */
  data object NoOrganization : SaveAsTemplateUnavailableReason

  /** Only Managers of the organization (named [organizationName], if known) can save templates. */
  data class NotManager(val organizationName: String?) : SaveAsTemplateUnavailableReason

  /** The message shown to the user. */
  @Composable
  fun text(): String =
    when (this) {
      NoOrganization -> stringResource(Res.string.formeditor_save_template_needs_organization)
      is NotManager ->
        organizationName?.let {
          stringResource(Res.string.formeditor_save_template_managers_only, it)
        } ?: stringResource(Res.string.formeditor_save_template_managers_only_this_organization)
    }
}

/** Why **Save as template** is unavailable, or `null` when it's available. */
val FormEditorUiState.saveAsTemplateUnavailableReason: SaveAsTemplateUnavailableReason?
  get() =
    when {
      canSaveAsTemplate -> null
      library.library.organizationId == null -> SaveAsTemplateUnavailableReason.NoOrganization
      else -> SaveAsTemplateUnavailableReason.NotManager(library.organizationName)
    }

/**
 * **Save as template**: saves a copy of the Form, with its concept links and choice codes, to the
 * survey organization's library, or (for `"All users"` Managers) the global library. After saving,
 * says where to find it.
 */
@Composable
internal fun SaveAsTemplateDialog(
  uiState: FormEditorUiState,
  actions: TemplateActions,
  onDismiss: () -> Unit,
) {
  val library = uiState.library
  val organizationName = library.organizationName
  var title by remember { mutableStateOf(uiState.form.title) }
  var description by remember { mutableStateOf("") }
  var toGlobal by remember { mutableStateOf(!library.canAddToDictionary) }
  var error by remember { mutableStateOf<String?>(null) }
  // Whether the template was saved to the global library; `null` until saved.
  var savedToGlobal by remember { mutableStateOf<Boolean?>(null) }

  savedToGlobal?.let { global ->
    val templateName = title.trim()
    val savedMessage =
      when {
        global -> stringResource(Res.string.formeditor_template_saved_global, templateName)
        organizationName != null ->
          stringResource(
            Res.string.formeditor_template_saved_organization,
            templateName,
            organizationName,
          )
        else -> stringResource(Res.string.formeditor_template_saved_your_organization, templateName)
      }
    AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(stringResource(Res.string.formeditor_template_saved_title)) },
      text = { Text(savedMessage) },
      confirmButton = {
        TextButton(onClick = onDismiss) { Text(stringResource(Res.string.formeditor_done)) }
      },
    )
    return
  }

  val questions = uiState.form.questions.size
  val linked = uiState.form.questions.count { it.conceptLink != null }
  val droppedLinks = if (toGlobal) SurveyFormTemplates.organizationLinkCount(uiState.form) else 0
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(Res.string.formeditor_save_as_template_title)) },
    text = {
      Column(
        modifier = Modifier.widthIn(max = 480.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          stringResource(Res.string.formeditor_save_as_template_intro),
          style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
          value = title,
          onValueChange = {
            title = it
            error = null
          },
          label = { Text(stringResource(Res.string.formeditor_template_name)) },
          singleLine = true,
          isError = error != null,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text(stringResource(Res.string.formeditor_template_description_optional)) },
          minLines = 2,
          modifier = Modifier.fillMaxWidth(),
        )
        if (library.canAddToDictionary && library.canSaveToGlobalLibrary) {
          DestinationOption(
            organizationName?.let {
              stringResource(Res.string.formeditor_template_destination_organization, it)
            } ?: stringResource(Res.string.formeditor_template_destination_your_organization),
            selected = !toGlobal,
          ) {
            toGlobal = false
          }
          DestinationOption(
            stringResource(Res.string.formeditor_template_destination_global),
            selected = toGlobal,
            trailing = { AdminOnlyBadge() },
          ) {
            toGlobal = true
          }
        } else if (library.canSaveToGlobalLibrary) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              stringResource(Res.string.formeditor_saving_to_global_library),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AdminOnlyBadge()
          }
        }
        Text(
          if (linked > 0) {
            pluralStringResource(
              Res.plurals.formeditor_template_question_summary_linked,
              questions,
              questions,
              linked,
            )
          } else {
            pluralStringResource(
              Res.plurals.formeditor_template_question_summary,
              questions,
              questions,
            )
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (droppedLinks > 0) {
          Text(
            if (organizationName != null) {
              pluralStringResource(
                Res.plurals.formeditor_template_dropped_links,
                droppedLinks,
                droppedLinks,
                organizationName,
              )
            } else {
              pluralStringResource(
                Res.plurals.formeditor_template_dropped_links_your_organization,
                droppedLinks,
                droppedLinks,
              )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        error?.let {
          Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val result = actions.saveAsTemplate(title, description, toGlobal)
          if (result == null) {
            savedToGlobal = toGlobal
          } else {
            error = result
          }
        },
        enabled = title.isNotBlank(),
      ) {
        Text(stringResource(Res.string.formeditor_save_template))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(Res.string.formeditor_cancel)) }
    },
  )
}

@Composable
private fun DestinationOption(
  label: String,
  selected: Boolean,
  trailing: (@Composable () -> Unit)? = null,
  onSelect: () -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioButton(selected = selected, onClick = null)
    Text(label, style = MaterialTheme.typography.bodyMedium)
    if (trailing != null) {
      Spacer(Modifier.width(8.dp))
      trailing()
    }
  }
}
