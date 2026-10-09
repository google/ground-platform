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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.FormDatasetLinks
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.FormImport
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.ImportedForm
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SaveToMode
import org.groundplatform.v2.devtools.prototypeapp.ui.common.TextFilePickResult
import org.groundplatform.v2.devtools.prototypeapp.ui.common.TextFilePicker
import org.groundplatform.v2.devtools.prototypeapp.ui.common.openPlatformTextFilePicker
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.SurveyEditorActions

/** File types offered by the XForms XML file picker. */
internal const val XFORMS_FILE_ACCEPT = ".xml,application/xml,text/xml"

/** Shown in the Add form dialog for the disabled template option. */
internal const val FORM_TEMPLATES_COMING_SOON = "Start from a reusable form template. Coming soon."

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
 * Asks how to add a Form: start from an empty form, use a template (disabled, TBD), or import from
 * an XForms XML file. Imports are previewed (with any notes from the importer) before the Form is
 * created.
 */
@Composable
internal fun AddFormDialog(
  actions: SurveyEditorActions,
  onDismiss: () -> Unit,
  pickTextFile: TextFilePicker = ::openPlatformTextFilePicker,
) {
  var preview by remember { mutableStateOf<FormImportPreview?>(null) }
  var message by remember { mutableStateOf<String?>(null) }

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
        AddOption(
          icon = Icons.Outlined.Description,
          title = "Use a template",
          body = FORM_TEMPLATES_COMING_SOON,
          enabled = false,
          onClick = {},
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
