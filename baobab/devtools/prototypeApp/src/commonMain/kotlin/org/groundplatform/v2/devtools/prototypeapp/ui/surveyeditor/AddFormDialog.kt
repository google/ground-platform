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
package org.groundplatform.v2.devtools.prototypeapp.ui.surveyeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.groundplatform.v2.core.forms.ui.GroundBadgeTone
import org.groundplatform.v2.core.forms.ui.GroundTonalBadge
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.FormDatasetLinks
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.FormImport
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.ImportedForm
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SaveToMode
import org.groundplatform.v2.devtools.prototypeapp.ui.common.TextFilePickResult
import org.groundplatform.v2.devtools.prototypeapp.ui.common.TextFilePicker
import org.groundplatform.v2.devtools.prototypeapp.ui.common.openPlatformTextFilePicker
import org.groundplatform.v2.devtools.prototypeapp.ui.state.FormLibraryContext
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.SurveyEditorActions

/** File types offered by the XForms XML file picker. */
internal const val XFORMS_FILE_ACCEPT = ".xml,application/xml,text/xml"

/** Body of the Add form dialog's template option. */
internal const val FORM_TEMPLATES_BODY =
  "Start from a reusable form, with standard fields, from your organization or the global library."

/** A file read for import as a Form, with the parsed [ImportedForm] (`null` if unreadable). */
data class FormImportPreview(val fileName: String, val imported: ImportedForm?) {
  companion object {
    fun of(fileName: String, text: String): FormImportPreview {
      val fallbackTitle = fileName.substringBeforeLast('.').ifBlank { "Imported form" }
      val imported =
        FormImport.fromXml(xml = text, fallbackTitle = fallbackTitle)?.takeIf {
          it.form.questions.isNotEmpty()
        }
      return FormImportPreview(fileName, imported)
    }
  }
}

/**
 * Asks how to add a Form: start from an empty form, copy a template from the survey's resolved
 * [library], or import from an XForms XML file. Imports are previewed (with any notes from the
 * importer) before the Form is created.
 */
@Composable
internal fun AddFormDialog(
  actions: SurveyEditorActions,
  onDismiss: () -> Unit,
  library: FormLibraryContext = FormLibraryContext(),
  pickTextFile: TextFilePicker = ::openPlatformTextFilePicker,
) {
  var preview by remember { mutableStateOf<FormImportPreview?>(null) }
  var message by remember { mutableStateOf<String?>(null) }
  var choosingTemplate by remember { mutableStateOf(false) }

  if (choosingTemplate) {
    TemplatePickerDialog(
      library = library,
      onPick = { templateId ->
        actions.addFormFromTemplate(templateId)
        onDismiss()
      },
      onBack = { choosingTemplate = false },
    )
    return
  }

  val current = preview
  if (current != null) {
    FormImportPreviewDialog(
      preview = current,
      onImport = { imported ->
        actions.importForm(imported)
        onDismiss()
      },
      onBack = { preview = null },
    )
    return
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Add form") },
    text = {
      Column(
        modifier = Modifier.widthIn(max = 480.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        AddOption(
          icon = Icons.Outlined.Add,
          title = "Empty form",
          body = "Start with a blank form and add questions from scratch.",
          onClick = {
            actions.addForm()
            onDismiss()
          },
        )
        val templateCount = library.library.pickableFormTemplates.size
        AddOption(
          icon = Icons.Outlined.Description,
          title = "From template…",
          body =
            if (templateCount == 0) "No templates are available for this survey yet."
            else FORM_TEMPLATES_BODY,
          enabled = templateCount > 0,
          onClick = { choosingTemplate = true },
        )
        AddOption(
          icon = Icons.Outlined.UploadFile,
          title = "Import from XML",
          body = "Create a form from an ODK XForms XML file.",
          onClick = {
            message = null
            pickTextFile(XFORMS_FILE_ACCEPT) { result ->
              when (result) {
                is TextFilePickResult.Picked ->
                  preview = FormImportPreview.of(result.fileName, result.text)
                is TextFilePickResult.Failed -> message = result.message
                TextFilePickResult.Cancelled -> Unit
              }
            }
          },
        )
        message?.let { InfoLine(it) }
      }
    },
    confirmButton = {},
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
private fun FormImportPreviewDialog(
  preview: FormImportPreview,
  onImport: (ImportedForm) -> Unit,
  onBack: () -> Unit,
) {
  val imported = preview.imported
  AlertDialog(
    onDismissRequest = onBack,
    title = { Text("Import ${preview.fileName}") },
    text = {
      Column(
        modifier =
          Modifier.widthIn(max = 520.dp)
            .heightIn(max = 480.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (imported == null) {
          Text(
            "No questions could be read from this file.",
            style = MaterialTheme.typography.bodyMedium,
          )
        } else {
          val form = imported.form
          val count = form.questions.size
          Text(form.title, style = MaterialTheme.typography.titleSmall)
          Text(
            "$count ${if (count == 1) "question" else "questions"} • ${saveToSummary(imported)}",
            style = MaterialTheme.typography.bodyMedium,
          )
          imported.notes.forEach { note -> InfoLine(note) }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { imported?.let(onImport) }, enabled = imported != null) {
        Text("Import")
      }
    },
    dismissButton = { TextButton(onClick = onBack) { Text("Back") } },
  )
}

private fun saveToSummary(imported: ImportedForm): String {
  val form = imported.form
  return if (form.saveTo.mode == SaveToMode.UPDATE) {
    val target = form.saveTo.targetDatasetId
    if (target != null) "Updates $target" else "Updates existing features"
  } else {
    val kind = FormDatasetLinks.datasetKindFor(form).singular.lowercase()
    val target = imported.createDatasetId
    if (target != null) "Adds to $target" else "Adds to a new $kind"
  }
}

/**
 * Lists the templates of the survey's resolved library (the organization's first, then global ones
 * it hasn't hidden), each with its source and how many of its questions link to standard fields.
 */
@Composable
private fun TemplatePickerDialog(
  library: FormLibraryContext,
  onPick: (templateId: String) -> Unit,
  onBack: () -> Unit,
) {
  val templates = library.library.pickableFormTemplates
  AlertDialog(
    onDismissRequest = onBack,
    title = { Text("Add form from template") },
    text = {
      Column(
        modifier =
          Modifier.widthIn(max = 560.dp)
            .heightIn(max = 520.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          "The form is copied into this survey. Later changes to the template don't affect it.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        templates.forEach { template ->
          val questions = template.form.questions.size
          val linked = template.linkedQuestionCount
          OutlinedCard(onClick = { onPick(template.id) }, modifier = Modifier.fillMaxWidth()) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Text(
                  template.title.get(library.language),
                  style = MaterialTheme.typography.titleSmall,
                  modifier = Modifier.weight(1f, fill = false),
                )
                GroundTonalBadge(
                  text =
                    if (library.library.isOrganizationEntry(template.id)) {
                      library.organizationName ?: "Organization"
                    } else {
                      "Global"
                    },
                  tone =
                    if (library.library.isOrganizationEntry(template.id)) GroundBadgeTone.TERTIARY
                    else GroundBadgeTone.NEUTRAL,
                )
              }
              template.description
                .get(library.language)
                .takeIf { it.isNotBlank() }
                ?.let {
                  Text(it, style = MaterialTheme.typography.bodySmall)
                }
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                if (linked > 0) {
                  Icon(
                    Icons.Outlined.Link,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                  )
                }
                Text(
                  "$questions ${if (questions == 1) "question" else "questions"}" +
                    if (linked > 0) " · $linked linked to standard fields" else "",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = { TextButton(onClick = onBack) { Text("Back") } },
  )
}
