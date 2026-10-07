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
package org.groundplatform.v2.devtools.prototypeapp.surveyeditor

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
import androidx.compose.material.icons.outlined.GridOn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.groundplatform.v2.core.geo.io.GeoFileReader
import org.groundplatform.v2.core.geo.io.GeoReadResult
import org.groundplatform.v2.devtools.prototypeapp.TextFilePickResult
import org.groundplatform.v2.devtools.prototypeapp.TextFilePicker
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.DatasetKind
import org.groundplatform.v2.devtools.prototypeapp.openPlatformTextFilePicker

/** A file read for import as a Map layer, with its plan (`null` if nothing can be imported). */
data class MapLayerImportPreview(
  val fileName: String,
  val result: GeoReadResult,
  val plan: MapLayerImportPlan?,
) {
  companion object {
    fun of(fileName: String, text: String): MapLayerImportPreview {
      val result = GeoFileReader.read(fileName, text)
      return MapLayerImportPreview(fileName, result, MapLayerImporter.plan(fileName, result))
    }
  }
}

/** Shown in the Add map layer dialog when sample plots can't be generated yet. */
internal const val SAMPLE_PLOTS_NEED_AREA = "Set a survey area or add a polygon map layer first."

/**
 * Asks how to add a Map layer: start empty, import a GeoJSON or KML file, or generate sample plots.
 * Imports are previewed (with any issues found in the file) before the layer is created.
 */
@Composable
internal fun AddMapLayerDialog(
  state: SurveyEditorState,
  onDismiss: () -> Unit,
  pickTextFile: TextFilePicker = ::openPlatformTextFilePicker,
) {
  var preview by remember { mutableStateOf<MapLayerImportPreview?>(null) }
  var message by remember { mutableStateOf<String?>(null) }

  val current = preview
  if (current != null) {
    ImportPreviewDialog(
      preview = current,
      onImport = { plan ->
        state.importMapLayer(plan)
        onDismiss()
      },
      onBack = { preview = null },
    )
    return
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Add map layer") },
    text = {
      Column(
        modifier = Modifier.widthIn(max = 480.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        AddOption(
          icon = Icons.Outlined.Add,
          title = "Empty map layer",
          body = "Add features by drawing them on the map or entering them in the table.",
          onClick = {
            state.addDataset(DatasetKind.MAP_LAYER)
            onDismiss()
          },
        )
        AddOption(
          icon = Icons.Outlined.UploadFile,
          title = "Import file",
          body = "Create a map layer from the points, lines or polygons in a GeoJSON or KML file.",
          onClick = {
            message = null
            pickTextFile(GEOMETRY_FILE_ACCEPT) { result ->
              when (result) {
                is TextFilePickResult.Picked ->
                  preview = MapLayerImportPreview.of(result.fileName, result.text)
                is TextFilePickResult.Failed -> message = result.message
                TextFilePickResult.Cancelled -> Unit
              }
            }
          },
        )
        val canGenerate = state.canGenerateSamplePlots
        AddOption(
          icon = Icons.Outlined.GridOn,
          title = "Generate sample plots",
          body =
            if (canGenerate) "Design a sample and generate plots inside the survey area or strata."
            else SAMPLE_PLOTS_NEED_AREA,
          enabled = canGenerate,
          onClick = {
            state.addSamplePlotsLayer()
            onDismiss()
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
private fun ImportPreviewDialog(
  preview: MapLayerImportPreview,
  onImport: (MapLayerImportPlan) -> Unit,
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
          Text(
            "No features could be read from this file.",
            style = MaterialTheme.typography.bodyMedium,
          )
        } else {
          Text(plan.name, style = MaterialTheme.typography.titleSmall)
          Text(
            "${plan.rows.size} ${plan.geometryKind.label.lowercase()} • " +
              "${plan.properties.size} properties",
            style = MaterialTheme.typography.bodyMedium,
          )
          if (plan.skippedFeatures > 0) {
            InfoLine(
              "${plan.skippedFeatures} features with other geometry types will be skipped. " +
                "A map layer has one geometry type."
            )
          }
        }
        IssueList(preview.result.issues)
      }
    },
    confirmButton = {
      TextButton(onClick = { plan?.let(onImport) }, enabled = plan != null) { Text("Import") }
    },
    dismissButton = { TextButton(onClick = onBack) { Text("Back") } },
  )
}

@Composable
private fun AddOption(
  icon: ImageVector,
  title: String,
  body: String,
  onClick: () -> Unit,
  enabled: Boolean = true,
) {
  OutlinedCard(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.padding(16.dp),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        icon,
        contentDescription = null,
        tint =
          if (enabled) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(24.dp),
      )
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
          body,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
