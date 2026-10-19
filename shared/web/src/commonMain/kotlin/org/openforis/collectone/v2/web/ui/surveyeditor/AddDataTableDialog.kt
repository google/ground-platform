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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.ui.common.GroundFilterChip
import org.openforis.collectone.v2.web.domain.model.editor.DataTableImportPlan
import org.openforis.collectone.v2.web.domain.model.editor.DataTableImporter
import org.openforis.collectone.v2.web.domain.model.editor.DateOrder
import org.openforis.collectone.v2.web.ui.common.TextFilePickResult
import org.openforis.collectone.v2.web.ui.common.TextFilePicker
import org.openforis.collectone.v2.web.ui.common.openPlatformTextFilePicker
import org.openforis.collectone.v2.web.ui.viewmodel.SurveyEditorActions

/** File types offered by the Add data table dialog's file picker. */
internal const val CSV_FILE_ACCEPT = ".csv,.tsv,text/csv,text/tab-separated-values"

/** A file read for import as a Data table, with its plan (`null` if nothing can be imported). */
data class DataTableImportPreview(
  val fileName: String,
  val text: String,
  val plan: DataTableImportPlan?,
  val notes: List<String>,
) {
  /** Re-reads the file with ambiguous numeric dates in [order]. */
  fun withDateOrder(order: DateOrder): DataTableImportPreview = of(fileName, text, order)

  companion object {
    fun of(
      fileName: String,
      text: String,
      dateOrder: DateOrder = DateOrder.DAY_FIRST,
    ): DataTableImportPreview {
      val result = DataTableImporter.plan(fileName, text, dateOrder)
      return DataTableImportPreview(fileName, text, result.plan, result.notes)
    }
  }
}

/**
 * Asks how to add a Data table: start empty or import a CSV file. Imports are previewed (with any
 * notes about the file) before the table is created. Numeric dates that could be day or month first
 * are read in the order usual for [region] (the user's locale), which the preview lets them change.
 */
@Composable
internal fun AddDataTableDialog(
  actions: SurveyEditorActions,
  onDismiss: () -> Unit,
  pickTextFile: TextFilePicker = ::openPlatformTextFilePicker,
  region: String = Locale.current.region,
) {
  var preview by remember { mutableStateOf<DataTableImportPreview?>(null) }
  var message by remember { mutableStateOf<String?>(null) }

  val current = preview
  if (current != null) {
    DataTableImportPreviewDialog(
      preview = current,
      onImport = { plan ->
        actions.importDataTable(plan)
        onDismiss()
      },
      onDateOrderChange = { preview = current.withDateOrder(it) },
      onBack = { preview = null },
    )
    return
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Add data table") },
    text = {
      Column(
        modifier = Modifier.widthIn(max = 480.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        AddOption(
          icon = Icons.Outlined.Add,
          title = "Empty data table",
          body = "Add columns and enter rows in the table.",
          onClick = {
            actions.addDataset(DatasetKind.DATA_TABLE)
            onDismiss()
          },
        )
        AddOption(
          icon = Icons.Outlined.UploadFile,
          title = "Import from CSV",
          body = "Create a data table from the columns and rows in a CSV file.",
          onClick = {
            message = null
            pickTextFile(CSV_FILE_ACCEPT) { result ->
              when (result) {
                is TextFilePickResult.Picked ->
                  preview =
                    DataTableImportPreview.of(
                      result.fileName,
                      result.text,
                      DateOrder.forRegion(region),
                    )
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
    title = { Text("Import ${preview.fileName}") },
    text = {
      Column(
        modifier =
          Modifier.widthIn(max = 520.dp)
            .heightIn(max = 480.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (plan == null) {
          Text("No rows could be read from this file.", style = MaterialTheme.typography.bodyMedium)
        } else {
          val rows = plan.rows.size
          val columns = plan.properties.size
          Text(plan.name, style = MaterialTheme.typography.titleSmall)
          Text(
            "$rows ${if (rows == 1) "row" else "rows"} • " +
              "$columns ${if (columns == 1) "column" else "columns"}",
            style = MaterialTheme.typography.bodyMedium,
          )
          Text(
            plan.properties.joinToString { "${it.label} (${it.type.label})" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        preview.notes.forEach { InfoLine(it) }
        if (plan != null && plan.ambiguousDateColumns.isNotEmpty()) {
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateOrder.entries.forEach { order ->
              val selected = plan.dateOrder == order
              GroundFilterChip(
                selected = selected,
                onClick = { if (!selected) onDateOrderChange(order) },
                label = { Text("${order.label} (${order.example})") },
              )
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { plan?.let(onImport) }, enabled = plan != null) { Text("Import") }
    },
    dismissButton = { TextButton(onClick = onBack) { Text("Back") } },
  )
}
