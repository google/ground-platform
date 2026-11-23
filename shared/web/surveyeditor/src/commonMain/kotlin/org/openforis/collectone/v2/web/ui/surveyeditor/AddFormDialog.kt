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
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.FormTemplate
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.FormDatasetLinks
import org.openforis.collectone.v2.app.domain.model.editor.FormImport
import org.openforis.collectone.v2.app.domain.model.editor.ImportedForm
import org.openforis.collectone.v2.app.domain.model.editor.SaveToMode
import org.openforis.collectone.v2.app.ui.text.UiText
import org.openforis.collectone.v2.app.ui.text.pluralRes
import org.openforis.collectone.v2.core.forms.ui.BadgeTone
import org.openforis.collectone.v2.core.forms.ui.TonalBadge
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.surveyeditor_add_form_adds_to_dataset
import org.openforis.collectone.v2.web.resources.surveyeditor_add_form_adds_to_new_data_table
import org.openforis.collectone.v2.web.resources.surveyeditor_add_form_adds_to_new_map_layer
import org.openforis.collectone.v2.web.resources.surveyeditor_add_form_empty
import org.openforis.collectone.v2.web.resources.surveyeditor_add_form_empty_body
import org.openforis.collectone.v2.web.resources.surveyeditor_add_form_for_purpose
import org.openforis.collectone.v2.web.resources.surveyeditor_add_form_for_purpose_body
import org.openforis.collectone.v2.web.resources.surveyeditor_add_form_from_template
import org.openforis.collectone.v2.web.resources.surveyeditor_add_form_import_file
import org.openforis.collectone.v2.web.resources.surveyeditor_add_form_import_file_body
import org.openforis.collectone.v2.web.resources.surveyeditor_add_form_no_purposes
import org.openforis.collectone.v2.web.resources.surveyeditor_add_form_no_questions
import org.openforis.collectone.v2.web.resources.surveyeditor_add_form_no_templates
import org.openforis.collectone.v2.web.resources.surveyeditor_add_form_purpose_add
import org.openforis.collectone.v2.web.resources.surveyeditor_add_form_purpose_note
import org.openforis.collectone.v2.web.resources.surveyeditor_add_form_purpose_title
import org.openforis.collectone.v2.web.resources.surveyeditor_add_form_questions_count
import org.openforis.collectone.v2.web.resources.surveyeditor_add_form_template_global
import org.openforis.collectone.v2.web.resources.surveyeditor_add_form_template_linked
import org.openforis.collectone.v2.web.resources.surveyeditor_add_form_template_note
import org.openforis.collectone.v2.web.resources.surveyeditor_add_form_template_organization
import org.openforis.collectone.v2.web.resources.surveyeditor_add_form_template_title
import org.openforis.collectone.v2.web.resources.surveyeditor_add_form_templates_body
import org.openforis.collectone.v2.web.resources.surveyeditor_add_form_title
import org.openforis.collectone.v2.web.resources.surveyeditor_add_form_updates_dataset
import org.openforis.collectone.v2.web.resources.surveyeditor_add_form_updates_existing
import org.openforis.collectone.v2.web.resources.surveyeditor_back
import org.openforis.collectone.v2.web.resources.surveyeditor_cancel
import org.openforis.collectone.v2.web.resources.surveyeditor_import
import org.openforis.collectone.v2.web.resources.surveyeditor_import_file
import org.openforis.collectone.v2.web.resources.wizard_starter_template_title
import org.openforis.collectone.v2.web.ui.common.ProgramPickerSection
import org.openforis.collectone.v2.web.ui.common.PurposePackGrid
import org.openforis.collectone.v2.web.ui.common.StarterTemplatePicker
import org.openforis.collectone.v2.web.ui.common.TextFilePickResult
import org.openforis.collectone.v2.web.ui.common.TextFilePicker
import org.openforis.collectone.v2.web.ui.common.openPlatformTextFilePicker
import org.openforis.collectone.v2.web.ui.formeditor.FormLibraryContext

/** File types offered by the XForms XML / Collect Earth file picker. */
internal const val XFORMS_FILE_ACCEPT = ".xml,.xhtml,.cep,.zip,.ced"

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
 * [library], add the Forms of a purpose's starting template (which also adds the purpose and its
 * programs to the survey), or import from an XForms XML file. Imports are previewed (with any notes
 * from the importer) before the Form is created.
 */
@Composable
fun AddFormDialog(
  actions: FormCatalogActions,
  onDismiss: () -> Unit,
  library: FormLibraryContext = FormLibraryContext(),
  pickTextFile: TextFilePicker = ::openPlatformTextFilePicker,
) {
  var preview by remember { mutableStateOf<FormImportPreview?>(null) }
  var message by remember { mutableStateOf<UiText?>(null) }
  var choosingTemplate by remember { mutableStateOf(false) }
  var choosingPurpose by remember { mutableStateOf(false) }

  if (choosingPurpose) {
    PurposeFormsDialog(
      library = library,
      onAdd = { packId, starterTemplateId, programIds ->
        actions.addFormsForPurpose(packId, starterTemplateId, programIds)
        onDismiss()
      },
      onBack = { choosingPurpose = false },
    )
    return
  }

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
    title = { Text(stringResource(Res.string.surveyeditor_add_form_title)) },
    text = {
      Column(
        modifier = Modifier.widthIn(max = 480.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        AddOption(
          icon = Icons.Outlined.Add,
          title = stringResource(Res.string.surveyeditor_add_form_empty),
          body = stringResource(Res.string.surveyeditor_add_form_empty_body),
          onClick = {
            actions.addForm()
            onDismiss()
          },
        )
        val templateCount = library.library.pickableFormTemplates.size
        AddOption(
          icon = Icons.Outlined.Description,
          title = stringResource(Res.string.surveyeditor_add_form_from_template),
          body =
            stringResource(
              if (templateCount == 0) Res.string.surveyeditor_add_form_no_templates
              else Res.string.surveyeditor_add_form_templates_body
            ),
          enabled = templateCount > 0,
          onClick = { choosingTemplate = true },
        )
        val purposeCount = library.library.pickablePurposePacks.size
        AddOption(
          icon = Icons.Outlined.Flag,
          title = stringResource(Res.string.surveyeditor_add_form_for_purpose),
          body =
            stringResource(
              if (purposeCount == 0) Res.string.surveyeditor_add_form_no_purposes
              else Res.string.surveyeditor_add_form_for_purpose_body
            ),
          enabled = purposeCount > 0,
          onClick = { choosingPurpose = true },
        )
        AddOption(
          icon = Icons.Outlined.UploadFile,
          title = stringResource(Res.string.surveyeditor_add_form_import_file),
          body = stringResource(Res.string.surveyeditor_add_form_import_file_body),
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
        message?.let { InfoLine(it.resolve()) }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(Res.string.surveyeditor_cancel)) }
    },
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
    title = { Text(stringResource(Res.string.surveyeditor_import_file, preview.fileName)) },
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
            stringResource(Res.string.surveyeditor_add_form_no_questions),
            style = MaterialTheme.typography.bodyMedium,
          )
        } else {
          val form = imported.form
          val count = form.questions.size
          Text(form.title, style = MaterialTheme.typography.titleSmall)
          Text(
            pluralStringResource(Res.plurals.surveyeditor_add_form_questions_count, count, count) +
              " • " +
              saveToSummary(imported),
            style = MaterialTheme.typography.bodyMedium,
          )
          imported.notes.forEach { note -> InfoLine(note) }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { imported?.let(onImport) }, enabled = imported != null) {
        Text(stringResource(Res.string.surveyeditor_import))
      }
    },
    dismissButton = {
      TextButton(onClick = onBack) { Text(stringResource(Res.string.surveyeditor_back)) }
    },
  )
}

/**
 * The templates of [FormLibraryContext.library] a survey can pick, or with [kind] only those whose
 * Form feeds a [kind] dataset (a Map layer when it captures a geometry, else a Data table).
 */
internal fun FormLibraryContext.templatesFeeding(kind: DatasetKind?): List<FormTemplate> =
  library.pickableFormTemplates.filter {
    kind == null || FormDatasetLinks.datasetKindFor(it.form) == kind
  }

/** Where submissions of a Form copied from [template] go: "Adds to a new map layer · Polygons". */
@Composable
private fun templateDatasetSummary(template: FormTemplate): String =
  when (FormDatasetLinks.datasetKindFor(template.form)) {
    DatasetKind.MAP_LAYER ->
      stringResource(Res.string.surveyeditor_add_form_adds_to_new_map_layer) +
        " · " +
        stringResource(FormDatasetLinks.geometryTypeFor(template.form).pluralRes)
    DatasetKind.DATA_TABLE ->
      stringResource(Res.string.surveyeditor_add_form_adds_to_new_data_table)
  }

@Composable
private fun saveToSummary(imported: ImportedForm): String {
  val form = imported.form
  return if (form.saveTo.mode == SaveToMode.UPDATE) {
    val target = form.saveTo.targetDatasetId
    if (target != null) stringResource(Res.string.surveyeditor_add_form_updates_dataset, target)
    else stringResource(Res.string.surveyeditor_add_form_updates_existing)
  } else {
    val target = imported.createDatasetId
    if (target != null) {
      stringResource(Res.string.surveyeditor_add_form_adds_to_dataset, target)
    } else {
      stringResource(
        when (FormDatasetLinks.datasetKindFor(form)) {
          DatasetKind.MAP_LAYER -> Res.string.surveyeditor_add_form_adds_to_new_map_layer
          DatasetKind.DATA_TABLE -> Res.string.surveyeditor_add_form_adds_to_new_data_table
        }
      )
    }
  }
}

/**
 * Lists the templates of the survey's resolved library (the organization's first, then global ones
 * it hasn't hidden), each with its source, how many of its questions link to standard fields, and
 * the dataset its submissions add to. With [kind], only templates whose Form feeds that kind of
 * dataset are listed (see [templatesFeeding]).
 */
@Composable
internal fun TemplatePickerDialog(
  library: FormLibraryContext,
  onPick: (templateId: String) -> Unit,
  onBack: () -> Unit,
  kind: DatasetKind? = null,
) {
  val templates = library.templatesFeeding(kind)
  val organizationLabel =
    library.organizationName
      ?: stringResource(Res.string.surveyeditor_add_form_template_organization)
  val globalLabel = stringResource(Res.string.surveyeditor_add_form_template_global)
  AlertDialog(
    onDismissRequest = onBack,
    title = { Text(stringResource(Res.string.surveyeditor_add_form_template_title)) },
    text = {
      Column(
        modifier =
          Modifier.widthIn(max = 560.dp)
            .heightIn(max = 520.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          stringResource(Res.string.surveyeditor_add_form_template_note),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        templates.forEach { template ->
          TemplatePickerCard(
            template = template,
            library = library,
            organizationLabel = organizationLabel,
            globalLabel = globalLabel,
            onClick = { onPick(template.id) },
          )
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = onBack) { Text(stringResource(Res.string.surveyeditor_back)) }
    },
  )
}

@Composable
private fun TemplatePickerCard(
  template: FormTemplate,
  library: FormLibraryContext,
  organizationLabel: String,
  globalLabel: String,
  onClick: () -> Unit,
) {
  val questions = template.form.questions.size
  val linked = template.linkedQuestionCount
  val isOrg = library.library.isOrganizationEntry(template.id)
  val questionsText =
    pluralStringResource(Res.plurals.surveyeditor_add_form_questions_count, questions, questions)
  val countLabel =
    if (linked > 0) {
      pluralStringResource(
        Res.plurals.surveyeditor_add_form_template_linked,
        linked,
        questionsText,
        linked,
      )
    } else {
      questionsText
    }
  OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          template.title.get(library.language),
          style = MaterialTheme.typography.titleSmall,
          modifier = Modifier.weight(1f, fill = false),
        )
        TonalBadge(
          text = if (isOrg) organizationLabel else globalLabel,
          tone = if (isOrg) BadgeTone.TERTIARY else BadgeTone.NEUTRAL,
        )
      }
      template.description
        .get(library.language)
        .takeIf { it.isNotBlank() }
        ?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        if (linked > 0) {
          Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(14.dp))
        }
        Text(
          "$countLabel • ${templateDatasetSummary(template)}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

/**
 * Picks a purpose, one of its starting templates, and the programs to tag (preset by the template),
 * then adds the template's Forms. Unlike the survey creation wizard, this can add a purpose to a
 * survey that already has others.
 */
@Composable
private fun PurposeFormsDialog(
  library: FormLibraryContext,
  onAdd: (packId: String, starterTemplateId: String, programIds: List<String>) -> Unit,
  onBack: () -> Unit,
) {
  val packs =
    remember(library.library, library.language) {
      library.library.pickablePurposePacks.sortedBy { it.title.get(library.language).lowercase() }
    }
  var packId by remember { mutableStateOf<String?>(null) }
  var starterTemplateId by remember { mutableStateOf("") }
  var programIds by remember { mutableStateOf(emptyList<String>()) }
  val pack = packs.firstOrNull { it.id == packId }
  val starter = pack?.starterTemplate(starterTemplateId)
  val canAdd =
    starter != null && starter.formTemplateIds.any { library.library.formTemplate(it) != null }

  AlertDialog(
    onDismissRequest = onBack,
    properties = DialogProperties(usePlatformDefaultWidth = false),
    modifier = Modifier.widthIn(max = 820.dp).padding(24.dp),
    title = { Text(stringResource(Res.string.surveyeditor_add_form_purpose_title)) },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          stringResource(Res.string.surveyeditor_add_form_purpose_note),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PurposePackGrid(
          packs = packs,
          library = library.library,
          organizationName = library.organizationName,
          language = library.language,
          selectedIds = listOfNotNull(packId),
          onToggle = { id ->
            if (id == packId) {
              packId = null
              programIds = emptyList()
            } else {
              val chosen = packs.first { it.id == id }
              packId = id
              starterTemplateId = chosen.defaultStarterTemplate.id
              programIds = chosen.defaultStarterTemplate.programIds
            }
          },
          showOtherCard = false,
          showValidationBanner = false,
        )
        if (pack != null) {
          Text(
            stringResource(Res.string.wizard_starter_template_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
          )
          StarterTemplatePicker(
            pack = pack,
            library = library.library,
            language = library.language,
            selectedId = starterTemplateId,
            onSelect = { id ->
              starterTemplateId = id
              programIds = pack.starterTemplate(id).programIds
            },
          )
          ProgramPickerSection(
            programIds = pack.programIds,
            selectedIds = programIds,
            onToggle = { id ->
              programIds = if (id in programIds) programIds - id else programIds + id
            },
          )
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = { pack?.let { onAdd(it.id, starterTemplateId, programIds) } },
        enabled = canAdd,
      ) {
        Text(stringResource(Res.string.surveyeditor_add_form_purpose_add))
      }
    },
    dismissButton = {
      TextButton(onClick = onBack) { Text(stringResource(Res.string.surveyeditor_back)) }
    },
  )
}
