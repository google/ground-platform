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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.ui.common.FlatFilterChip
import org.openforis.collectone.v2.app.ui.text.UiText
import org.openforis.collectone.v2.app.ui.text.labelRes
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.web.domain.model.editor.DataTableImportPlan
import org.openforis.collectone.v2.web.domain.model.editor.DataTableImportPreview
import org.openforis.collectone.v2.web.domain.model.editor.DateOrder
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.surveyeditor_add_table_column_with_type
import org.openforis.collectone.v2.web.resources.surveyeditor_add_table_columns_count
import org.openforis.collectone.v2.web.resources.surveyeditor_add_table_date_order_option
import org.openforis.collectone.v2.web.resources.surveyeditor_add_table_empty
import org.openforis.collectone.v2.web.resources.surveyeditor_add_table_empty_body
import org.openforis.collectone.v2.web.resources.surveyeditor_add_table_from_template_body
import org.openforis.collectone.v2.web.resources.surveyeditor_add_table_import_csv
import org.openforis.collectone.v2.web.resources.surveyeditor_add_table_import_csv_body
import org.openforis.collectone.v2.web.resources.surveyeditor_add_table_no_rows
import org.openforis.collectone.v2.web.resources.surveyeditor_add_table_title
import org.openforis.collectone.v2.web.resources.surveyeditor_back
import org.openforis.collectone.v2.web.resources.surveyeditor_cancel
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_rows_count
import org.openforis.collectone.v2.web.resources.surveyeditor_import
import org.openforis.collectone.v2.web.resources.surveyeditor_import_file
import org.openforis.collectone.v2.web.ui.common.TextFilePickResult
import org.openforis.collectone.v2.web.ui.common.TextFilePicker
import org.openforis.collectone.v2.web.ui.common.openPlatformTextFilePicker
import org.openforis.collectone.v2.web.ui.formeditor.FormLibraryContext
import org.openforis.collectone.v2.web.ui.text.labelRes
import org.openforis.collectone.v2.web.ui.text.resolve
import org.openforis.collectone.v2.web.ui.text.toEditorText

/** File types offered by the Add data table dialog's file picker. */
internal const val CSV_FILE_ACCEPT = ".csv,.tsv,text/csv,text/tab-separated-values"

/**
 * Asks how to add a Data table: start empty or import a CSV file. Imports are previewed (with any
 * notes about the file) before the table is created. Numeric dates that could be day or month first
 * are read in the order usual for [region] (the user's locale), which the preview lets them change.
 *
 * With a [library], it also offers **From template**: picking one of its templates whose Form adds
 * table rows calls [onAddFromTemplate], which adds that Form and its new Data table.
 */
@Composable
fun AddDataTableDialog(
  actions: DatasetActions,
  onDismiss: () -> Unit,
  pickTextFile: TextFilePicker = ::openPlatformTextFilePicker,
  region: String = Locale.current.region,
  library: FormLibraryContext? = null,
  onAddFromTemplate: (templateId: String) -> Unit = {},
) {
  var preview by remember { mutableStateOf<DataTableImportPreview?>(null) }
  var message by remember { mutableStateOf<UiText?>(null) }
  var choosingTemplate by remember { mutableStateOf(false) }

  if (choosingTemplate && library != null) {
    TemplatePickerDialog(
      library = library,
      kind = DatasetKind.DATA_TABLE,
      onPick = { templateId ->
        onAddFromTemplate(templateId)
        onDismiss()
      },
      onBack = { choosingTemplate = false },
    )
    return
  }

  val current = preview
  if (current != null) {
    DataTableImportPreviewDialog(
      preview = current,
      onImport = { plan ->
        actions.importDataTable(plan)
        onDismiss()
      },
      onDateOrderChange = {
        preview = actions.previewDataTableImport(current.fileName, current.text, it)
      },
      onBack = { preview = null },
    )
    return
  }

  AddDataTableOptionsDialog(
    library = library,
    message = message,
    onAddEmpty = {
      actions.addDataset(DatasetKind.DATA_TABLE)
      onDismiss()
    },
    onImportCsv = {
      message = null
      pickTextFile(CSV_FILE_ACCEPT) { result ->
        when (result) {
          is TextFilePickResult.Picked ->
            preview =
              actions.previewDataTableImport(
                result.fileName,
                result.text,
                DateOrder.forRegion(region),
              )
          is TextFilePickResult.Failed -> message = result.message
          TextFilePickResult.Cancelled -> Unit
        }
      }
    },
    onChooseTemplate = { choosingTemplate = true },
    onDismiss = onDismiss,
  )
}

@Composable
private fun AddDataTableOptionsDialog(
  library: FormLibraryContext?,
  message: UiText?,
  onAddEmpty: () -> Unit,
  onImportCsv: () -> Unit,
  onChooseTemplate: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(Res.string.surveyeditor_add_table_title)) },
    text = {
      Column(
        modifier = Modifier.widthIn(max = 480.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        AddOption(
          icon = Icons.Outlined.Add,
          title = stringResource(Res.string.surveyeditor_add_table_empty),
          body = stringResource(Res.string.surveyeditor_add_table_empty_body),
          onClick = onAddEmpty,
        )
        AddOption(
          icon = Icons.Outlined.UploadFile,
          title = stringResource(Res.string.surveyeditor_add_table_import_csv),
          body = stringResource(Res.string.surveyeditor_add_table_import_csv_body),
          onClick = onImportCsv,
        )
        if (library != null) {
          FromTemplateOption(
            library = library,
            kind = DatasetKind.DATA_TABLE,
            body = stringResource(Res.string.surveyeditor_add_table_from_template_body),
            onClick = onChooseTemplate,
          )
        }
        message?.let { InfoLine(it.resolve()) }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(Res.string.surveyeditor_cancel)) }
    },
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DataTableImportPreviewDialog(
  preview: DataTableImportPreview,
  onImport: (DataTableImportPlan) -> Unit,
  onDateOrderChange: (DateOrder) -> Unit,
  onBack: () -> Unit,
) {
  val plan = preview.plan
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
        if (plan == null) {
          Text(
            stringResource(Res.string.surveyeditor_add_table_no_rows),
            style = MaterialTheme.typography.bodyMedium,
          )
        } else {
          val rows = plan.rows.size
          val columns = plan.properties.size
          Text(plan.name, style = MaterialTheme.typography.titleSmall)
          val rowsText =
            pluralStringResource(Res.plurals.surveyeditor_dataset_rows_count, rows, rows)
          val columnsText =
            pluralStringResource(Res.plurals.surveyeditor_add_table_columns_count, columns, columns)
          Text(
            "$rowsText • $columnsText",
            style = MaterialTheme.typography.bodyMedium,
          )
          Text(
            plan.properties
              .map {
                stringResource(
                  Res.string.surveyeditor_add_table_column_with_type,
                  it.label,
                  stringResource(it.type.labelRes),
                )
              }
              .joinToString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        preview.notes.forEach { InfoLine(it.toEditorText().resolve()) }
        if (plan != null && plan.ambiguousDateColumns.isNotEmpty()) {
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateOrder.entries.forEach { order ->
              val selected = plan.dateOrder == order
              FlatFilterChip(
                selected = selected,
                onClick = { if (!selected) onDateOrderChange(order) },
                label = {
                  Text(
                    stringResource(
                      Res.string.surveyeditor_add_table_date_order_option,
                      stringResource(order.labelRes),
                      order.example,
                    )
                  )
                },
              )
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { plan?.let(onImport) }, enabled = plan != null) {
        Text(stringResource(Res.string.surveyeditor_import))
      }
    },
    dismissButton = {
      TextButton(onClick = onBack) { Text(stringResource(Res.string.surveyeditor_back)) }
    },
  )
}
