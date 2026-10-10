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
package org.groundplatform.v2.devtools.prototypeapp.ui.formeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyFormTemplates
import org.groundplatform.v2.devtools.prototypeapp.ui.state.FormEditorUiState
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.FormEditorActions

/** Whether the signed-in user may save this Form as a template anywhere. */
internal val FormEditorUiState.canSaveAsTemplate: Boolean
  get() = library.canAddToDictionary || library.canSaveToGlobalLibrary

/** Why **Save as template** is unavailable, or `null` when it's available. */
internal val FormEditorUiState.saveAsTemplateUnavailableReason: String?
  get() =
    when {
      canSaveAsTemplate -> null
      library.library.organizationId == null ->
        "Move this survey into an organization to save its forms as templates."
      else ->
        "Only Managers of ${library.organizationName ?: "this organization"} can save templates."
    }

/**
 * **Save as template**: saves a copy of the Form, with its concept links and choice codes, to the
 * survey organization's library, or (for `"All users"` Managers) the global library. After saving,
 * says where to find it.
 */
@Composable
internal fun SaveAsTemplateDialog(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  onDismiss: () -> Unit,
) {
  val library = uiState.library
  val organizationName = library.organizationName ?: "your organization"
  var title by remember { mutableStateOf(uiState.form.title) }
  var description by remember { mutableStateOf("") }
  var toGlobal by remember { mutableStateOf(!library.canAddToDictionary) }
  var error by remember { mutableStateOf<String?>(null) }
  var savedTo by remember { mutableStateOf<String?>(null) }

  savedTo?.let { destination ->
    AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("Template saved") },
      text = {
        Text(
          "\"${title.trim()}\" is in $destination. It's available in \"From template…\" when " +
            "adding a form, and listed in the organization's Templates tab."
        )
      },
      confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
    return
  }

  val questions = uiState.form.questions.size
  val linked = uiState.form.questions.count { it.conceptLink != null }
  val droppedLinks = if (toGlobal) SurveyFormTemplates.organizationLinkCount(uiState.form) else 0
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Save as template") },
    text = {
      Column(
        modifier = Modifier.widthIn(max = 480.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          "Save a copy of this form so it can be added to other surveys. Its standard field " +
            "links and choice codes are kept; where it saves data isn't.",
          style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
          value = title,
          onValueChange = {
            title = it
            error = null
          },
          label = { Text("Template name") },
          singleLine = true,
          isError = error != null,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text("Description (optional)") },
          minLines = 2,
          modifier = Modifier.fillMaxWidth(),
        )
        if (library.canAddToDictionary && library.canSaveToGlobalLibrary) {
          DestinationOption("$organizationName templates", selected = !toGlobal) {
            toGlobal = false
          }
          DestinationOption("Global library (all organizations)", selected = toGlobal) {
            toGlobal = true
          }
        }
        Text(
          "$questions ${if (questions == 1) "question" else "questions"}" +
            if (linked > 0) ", $linked linked to standard fields." else ".",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (droppedLinks > 0) {
          Text(
            "Global templates can only use global standard fields, so $droppedLinks " +
              "${if (droppedLinks == 1) "link" else "links"} to $organizationName fields will be " +
              "removed from the template.",
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
            savedTo = if (toGlobal) "the global library" else "$organizationName templates"
          } else {
            error = result
          }
        },
        enabled = title.isNotBlank(),
      ) {
        Text("Save template")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
private fun DestinationOption(label: String, selected: Boolean, onSelect: () -> Unit) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioButton(selected = selected, onClick = null)
    Text(label, style = MaterialTheme.typography.bodyMedium)
  }
}
