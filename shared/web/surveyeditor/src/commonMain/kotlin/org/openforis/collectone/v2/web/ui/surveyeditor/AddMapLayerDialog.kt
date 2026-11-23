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
import androidx.compose.material.icons.outlined.GridOn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.ui.text.UiText
import org.openforis.collectone.v2.app.ui.text.pluralRes
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.web.domain.model.editor.MapLayerImportPlan
import org.openforis.collectone.v2.web.domain.model.editor.MapLayerImportPreview
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.surveyeditor_add_dataset_from_template
import org.openforis.collectone.v2.web.resources.surveyeditor_add_dataset_no_templates
import org.openforis.collectone.v2.web.resources.surveyeditor_add_layer_empty
import org.openforis.collectone.v2.web.resources.surveyeditor_add_layer_empty_body
import org.openforis.collectone.v2.web.resources.surveyeditor_add_layer_feature_count
import org.openforis.collectone.v2.web.resources.surveyeditor_add_layer_from_template_body
import org.openforis.collectone.v2.web.resources.surveyeditor_add_layer_generate_plots
import org.openforis.collectone.v2.web.resources.surveyeditor_add_layer_generate_plots_body
import org.openforis.collectone.v2.web.resources.surveyeditor_add_layer_generate_plots_need_area
import org.openforis.collectone.v2.web.resources.surveyeditor_add_layer_import_file
import org.openforis.collectone.v2.web.resources.surveyeditor_add_layer_import_file_body
import org.openforis.collectone.v2.web.resources.surveyeditor_add_layer_no_features
import org.openforis.collectone.v2.web.resources.surveyeditor_add_layer_skipped_features
import org.openforis.collectone.v2.web.resources.surveyeditor_add_layer_title
import org.openforis.collectone.v2.web.resources.surveyeditor_back
import org.openforis.collectone.v2.web.resources.surveyeditor_cancel
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_properties_count
import org.openforis.collectone.v2.web.resources.surveyeditor_import
import org.openforis.collectone.v2.web.resources.surveyeditor_import_file
import org.openforis.collectone.v2.web.ui.common.TextFilePickResult
import org.openforis.collectone.v2.web.ui.common.TextFilePicker
import org.openforis.collectone.v2.web.ui.common.openPlatformTextFilePicker
import org.openforis.collectone.v2.web.ui.formeditor.FormLibraryContext

/**
 * Asks how to add a Map layer: start empty, import a GeoJSON or KML file, or generate sample plots.
 * Imports are previewed (with any issues found in the file) before the layer is created.
 *
 * With [offerTemplates], it also offers **From template**: picking a template of the survey's
 * library whose Form captures a geometry adds that Form and opens its new Map layer.
 */
@Composable
fun AddMapLayerDialog(
  uiState: SurveyEditorUiState,
  actions: SurveyEditorActions,
  onDismiss: () -> Unit,
  pickTextFile: TextFilePicker = ::openPlatformTextFilePicker,
  offerTemplates: Boolean = false,
) {
  var preview by remember { mutableStateOf<MapLayerImportPreview?>(null) }
  var message by remember { mutableStateOf<UiText?>(null) }
  var choosingTemplate by remember { mutableStateOf(false) }

  if (choosingTemplate) {
    TemplatePickerDialog(
      library = uiState.library,
      kind = DatasetKind.MAP_LAYER,
      onPick = { templateId ->
        actions.addFormFromTemplate(templateId, openDataset = true)
        onDismiss()
      },
      onBack = { choosingTemplate = false },
    )
    return
  }

  val current = preview
  if (current != null) {
    ImportPreviewDialog(
      preview = current,
      onImport = { plan ->
        actions.importMapLayer(plan)
        onDismiss()
      },
      onBack = { preview = null },
    )
    return
  }

  AddMapLayerOptionsDialog(
    uiState = uiState,
    offerTemplates = offerTemplates,
    message = message,
    onAddEmpty = {
      actions.addDataset(DatasetKind.MAP_LAYER)
      onDismiss()
    },
    onImportFile = {
      message = null
      pickTextFile(GEOMETRY_FILE_ACCEPT) { result ->
        when (result) {
          is TextFilePickResult.Picked ->
            preview = actions.previewMapLayerImport(result.fileName, result.text)
          is TextFilePickResult.Failed -> message = result.message
          TextFilePickResult.Cancelled -> Unit
        }
      }
    },
    onChooseTemplate = { choosingTemplate = true },
    onGeneratePlots = {
      actions.addSamplePlotsLayer()
      onDismiss()
    },
    onDismiss = onDismiss,
  )
}

@Composable
private fun AddMapLayerOptionsDialog(
  uiState: SurveyEditorUiState,
  offerTemplates: Boolean,
  message: UiText?,
  onAddEmpty: () -> Unit,
  onImportFile: () -> Unit,
  onChooseTemplate: () -> Unit,
  onGeneratePlots: () -> Unit,
  onDismiss: () -> Unit,
) {
  val canGenerate = uiState.canGenerateSamplePlots
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(Res.string.surveyeditor_add_layer_title)) },
    text = {
      Column(
        modifier = Modifier.widthIn(max = 480.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        AddOption(
          icon = Icons.Outlined.Add,
          title = stringResource(Res.string.surveyeditor_add_layer_empty),
          body = stringResource(Res.string.surveyeditor_add_layer_empty_body),
          onClick = onAddEmpty,
        )
        AddOption(
          icon = Icons.Outlined.UploadFile,
          title = stringResource(Res.string.surveyeditor_add_layer_import_file),
          body = stringResource(Res.string.surveyeditor_add_layer_import_file_body),
          onClick = onImportFile,
        )
        if (offerTemplates) {
          FromTemplateOption(
            library = uiState.library,
            kind = DatasetKind.MAP_LAYER,
            body = stringResource(Res.string.surveyeditor_add_layer_from_template_body),
            onClick = onChooseTemplate,
          )
        }
        AddOption(
          icon = Icons.Outlined.GridOn,
          title = stringResource(Res.string.surveyeditor_add_layer_generate_plots),
          body =
            stringResource(
              if (canGenerate) Res.string.surveyeditor_add_layer_generate_plots_body
              else Res.string.surveyeditor_add_layer_generate_plots_need_area
            ),
          enabled = canGenerate,
          onClick = onGeneratePlots,
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
private fun ImportPreviewDialog(
  preview: MapLayerImportPreview,
  onImport: (MapLayerImportPlan) -> Unit,
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
            stringResource(Res.string.surveyeditor_add_layer_no_features),
            style = MaterialTheme.typography.bodyMedium,
          )
        } else {
          Text(plan.name, style = MaterialTheme.typography.titleSmall)
          val featuresText =
            stringResource(
              Res.string.surveyeditor_add_layer_feature_count,
              plan.rows.size,
              stringResource(plan.geometryType.pluralRes).lowercase(),
            )
          val propertiesText =
            pluralStringResource(
              Res.plurals.surveyeditor_dataset_properties_count,
              plan.properties.size,
              plan.properties.size,
            )
          Text(
            "$featuresText • $propertiesText",
            style = MaterialTheme.typography.bodyMedium,
          )
          if (plan.skippedFeatures > 0) {
            InfoLine(
              pluralStringResource(
                Res.plurals.surveyeditor_add_layer_skipped_features,
                plan.skippedFeatures,
                plan.skippedFeatures,
              )
            )
          }
        }
        IssueList(preview.result.issues.map { it.toIssueLine() })
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

/**
 * **From template** in the Add map layer / data table dialogs: adds the Form of a template whose
 * submissions feed a [kind] dataset, with that new dataset. Disabled when [library] has none.
 */
@Composable
internal fun FromTemplateOption(
  library: FormLibraryContext,
  kind: DatasetKind,
  body: String,
  onClick: () -> Unit,
) {
  val available = library.templatesFeeding(kind).isNotEmpty()
  AddOption(
    icon = Icons.Outlined.Description,
    title = stringResource(Res.string.surveyeditor_add_dataset_from_template),
    body =
      if (available) body else stringResource(Res.string.surveyeditor_add_dataset_no_templates),
    enabled = available,
    onClick = onClick,
  )
}

@Composable
internal fun AddOption(
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
