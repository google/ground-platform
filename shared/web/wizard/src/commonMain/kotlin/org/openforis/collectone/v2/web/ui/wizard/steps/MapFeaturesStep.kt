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
package org.openforis.collectone.v2.web.ui.wizard.steps

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddLocationAlt
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.EntityRow
import org.openforis.collectone.v2.app.domain.model.editor.PlotReviewOptions
import org.openforis.collectone.v2.app.domain.model.editor.SurveyArea
import org.openforis.collectone.v2.app.ui.text.UiText
import org.openforis.collectone.v2.app.ui.text.labelRes
import org.openforis.collectone.v2.app.ui.text.pluralRes
import org.openforis.collectone.v2.core.forms.ui.BadgeTone
import org.openforis.collectone.v2.core.forms.ui.TonalBadge
import org.openforis.collectone.v2.core.forms.ui.controls.Checkbox
import org.openforis.collectone.v2.core.forms.ui.controls.ElevatedCard
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.Switch
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.web.domain.model.editor.MapLayerImportPreview
import org.openforis.collectone.v2.web.domain.model.editor.SurveyWizardRules
import org.openforis.collectone.v2.web.domain.model.editor.WizardMapFeatureSource
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.surveyeditor_plot_review
import org.openforis.collectone.v2.web.resources.wizard_allow_field_creation
import org.openforis.collectone.v2.web.resources.wizard_allow_field_creation_supporting
import org.openforis.collectone.v2.web.resources.wizard_also_allow_field_creation
import org.openforis.collectone.v2.web.resources.wizard_also_allow_field_creation_supporting
import org.openforis.collectone.v2.web.resources.wizard_change
import org.openforis.collectone.v2.web.resources.wizard_eudr_guardrail_body
import org.openforis.collectone.v2.web.resources.wizard_eudr_guardrail_title
import org.openforis.collectone.v2.web.resources.wizard_eudr_plots_under_four_ha
import org.openforis.collectone.v2.web.resources.wizard_geometry_type
import org.openforis.collectone.v2.web.resources.wizard_import_features_choose_file
import org.openforis.collectone.v2.web.resources.wizard_import_features_loaded
import org.openforis.collectone.v2.web.resources.wizard_import_features_supporting
import org.openforis.collectone.v2.web.resources.wizard_import_features_title
import org.openforis.collectone.v2.web.resources.wizard_map_features_source_preselected
import org.openforis.collectone.v2.web.resources.wizard_map_features_source_title
import org.openforis.collectone.v2.web.resources.wizard_map_features_subtitle
import org.openforis.collectone.v2.web.resources.wizard_map_layer_name
import org.openforis.collectone.v2.web.resources.wizard_other_datasets_note
import org.openforis.collectone.v2.web.resources.wizard_primary_layer_settings
import org.openforis.collectone.v2.web.resources.wizard_sample_design_supporting
import org.openforis.collectone.v2.web.resources.wizard_sample_design_title
import org.openforis.collectone.v2.web.resources.wizard_step_map_features
import org.openforis.collectone.v2.web.ui.common.DropdownSelector
import org.openforis.collectone.v2.web.ui.common.MapFeatureSourceCardGraphic
import org.openforis.collectone.v2.web.ui.common.TextFilePickResult
import org.openforis.collectone.v2.web.ui.common.TextFilePicker
import org.openforis.collectone.v2.web.ui.surveyeditor.GEOMETRY_FILE_ACCEPT
import org.openforis.collectone.v2.web.ui.surveyeditor.InteractiveLayerMapCard
import org.openforis.collectone.v2.web.ui.surveyeditor.IssueList
import org.openforis.collectone.v2.web.ui.surveyeditor.PlotFieldValidationPanel
import org.openforis.collectone.v2.web.ui.surveyeditor.SamplingDesignPanel
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorActions
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorUiState
import org.openforis.collectone.v2.web.ui.surveyeditor.toIssueLine
import org.openforis.collectone.v2.web.ui.text.descriptionRes
import org.openforis.collectone.v2.web.ui.text.labelRes
import org.openforis.collectone.v2.web.ui.wizard.CreateSurveyWizardActions
import org.openforis.collectone.v2.web.ui.wizard.CreateSurveyWizardUiState

// ---------------------------------------------------------------------------------------------
// Step 4: Map features
// ---------------------------------------------------------------------------------------------

@Composable
internal fun MapFeaturesStepContent(
  uiState: CreateSurveyWizardUiState,
  actions: CreateSurveyWizardActions,
  editorUiState: SurveyEditorUiState,
  editorActions: SurveyEditorActions,
  pickTextFile: TextFilePicker,
  sourceCollapsed: Boolean,
  onSourceCollapsedChange: (Boolean) -> Unit,
  sourcePreselected: Boolean,
  mapPreviewHeight: Dp,
) {
  val primaryLayer = uiState.primaryMapLayer
  val requiresEudrGuardrail =
    SurveyWizardRules.requiresEudrPolygonGuardrail(
      editorUiState.details.purposeIds,
      editorUiState.details.purposeIds
        .firstOrNull()
        ?.takeIf { uiState.starterTemplateId.isNotBlank() }
        ?.let { mapOf(it to uiState.starterTemplateId) }
        .orEmpty(),
    )
  var importPreview by remember { mutableStateOf<MapLayerImportPreview?>(null) }
  var importError by remember { mutableStateOf<UiText?>(null) }
  var selectedPreviewRow by remember { mutableStateOf<String?>(null) }

  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
      text = stringResource(Res.string.wizard_step_map_features),
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
    )
    Text(
      text = stringResource(Res.string.wizard_map_features_subtitle),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }

  ElevatedCard(
    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier.animateContentSize(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      WizardSectionTitle(stringResource(Res.string.wizard_map_features_source_title))
      fun iconOf(source: WizardMapFeatureSource) =
        when (source) {
          WizardMapFeatureSource.FIELD_FREEFORM -> Icons.Outlined.AddLocationAlt
          WizardMapFeatureSource.SAMPLE_PLOTS -> Icons.Outlined.GridOn
          WizardMapFeatureSource.IMPORT_FILE -> Icons.Outlined.UploadFile
        }
      if (sourceCollapsed) {
        // Once chosen (or preselected), the choice collapses to a one-line summary so the
        // configuration for it is right below; "Change" brings the cards back.
        val source = uiState.mapFeatureSource
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Icon(
            iconOf(source),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              stringResource(source.labelRes),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
            Text(
              if (sourcePreselected) {
                stringResource(
                  Res.string.wizard_map_features_source_preselected,
                  stringResource(source.descriptionRes),
                )
              } else {
                stringResource(source.descriptionRes)
              },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          OutlinedButton(onClick = { onSourceCollapsedChange(false) }) {
            Text(stringResource(Res.string.wizard_change))
          }
        }
      } else {
        Row(
          modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          WizardMapFeatureSource.entries.forEach { source ->
            SelectableChoiceCard(
              title = stringResource(source.labelRes),
              description = stringResource(source.descriptionRes),
              icon = iconOf(source),
              tags = source.tags,
              selected = uiState.mapFeatureSource == source,
              onClick = {
                actions.selectMapFeatureSource(source)
                onSourceCollapsedChange(true)
              },
              modifier = Modifier.weight(1f),
              graphic = { MapFeatureSourceCardGraphic(source = source) },
            )
          }
        }
      }
    }
  }

  if (requiresEudrGuardrail && uiState.mapFeatureSource != WizardMapFeatureSource.SAMPLE_PLOTS) {
    Surface(
      color = MaterialTheme.colorScheme.tertiaryContainer,
      contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
      shape = MaterialTheme.shapes.medium,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(Icons.Outlined.Verified, contentDescription = null, modifier = Modifier.size(20.dp))
          Text(
            text = stringResource(Res.string.wizard_eudr_guardrail_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
          )
        }
        Text(
          text = stringResource(Res.string.wizard_eudr_guardrail_body),
          style = MaterialTheme.typography.bodySmall,
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Checkbox(
            checked = uiState.eudrPlotsUnderFourHaConfirmed,
            onCheckedChange = actions::confirmEudrPlotsUnderFourHa,
          )
          Text(
            text = stringResource(Res.string.wizard_eudr_plots_under_four_ha),
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    }
  }

  if (primaryLayer != null) {
    val surveyArea = editorUiState.details.surveyArea
    val mapDataset =
      remember(primaryLayer, uiState.mapFeatureSource, surveyArea) {
        if (
          uiState.mapFeatureSource == WizardMapFeatureSource.FIELD_FREEFORM &&
            primaryLayer.rows.isEmpty()
        ) {
          seedPreviewFeaturesForFreeformLayer(primaryLayer, surveyArea)
        } else {
          primaryLayer
        }
      }
    // The map and its settings share a fixed height, and the settings scroll on their own (as in
    // the
    // survey editor), so the map stays in view while scrolling through long settings such as a
    // sample design.
    Row(
      modifier = Modifier.fillMaxWidth().height(mapPreviewHeight),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalAlignment = Alignment.Top,
    ) {
      Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
        key(uiState.mapFeatureSource, primaryLayer.geometryType) {
          InteractiveLayerMapCard(
            editor = editorActions,
            dataset = mapDataset,
            selectedRow = selectedPreviewRow,
            onSelectRow = { selectedPreviewRow = it },
            modifier = Modifier.fillMaxSize(),
            geometryEditable =
              !primaryLayer.isGenerated &&
                uiState.mapFeatureSource != WizardMapFeatureSource.FIELD_FREEFORM,
            showAddButtonInHeader = false,
            backdropPolygons = surveyArea?.parts.orEmpty(),
          )
        }
      }

      ElevatedCard(
        modifier = Modifier.width(400.dp).fillMaxHeight(),
        colors =
          CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(
          modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          WizardSectionTitle(stringResource(Res.string.wizard_primary_layer_settings))
          OutlinedTextField(
            value = primaryLayer.displayName,
            onValueChange = actions::updatePrimaryLayerDisplayName,
            label = { Text(stringResource(Res.string.wizard_map_layer_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )

          when (uiState.mapFeatureSource) {
            WizardMapFeatureSource.FIELD_FREEFORM -> {
              val allowedGeometries =
                if (requiresEudrGuardrail && !uiState.eudrPlotsUnderFourHaConfirmed) {
                  listOf(GeometryType.POLYGON)
                } else {
                  GeometryType.entries
                }
              DropdownSelector(
                label = stringResource(Res.string.wizard_geometry_type),
                selectedText = stringResource(primaryLayer.geometryType.pluralRes),
                options = allowedGeometries,
                optionText = { stringResource(it.pluralRes) },
                onSelect = actions::updatePrimaryLayerGeometryType,
              )
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = stringResource(Res.string.wizard_allow_field_creation),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                  )
                  Text(
                    text = stringResource(Res.string.wizard_allow_field_creation_supporting),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Switch(
                  checked = primaryLayer.fieldCreationEnabled,
                  onCheckedChange = actions::updatePrimaryLayerFieldCreation,
                )
              }
            }

            WizardMapFeatureSource.SAMPLE_PLOTS -> {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                WizardSectionTitle(stringResource(Res.string.wizard_sample_design_title))
                TonalBadge(
                  text = "Collect Earth Online",
                  tone = BadgeTone.PRIMARY,
                )
              }
              Text(
                text = stringResource(Res.string.wizard_sample_design_supporting),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              SamplingDesignPanel(
                uiState = editorUiState,
                actions = editorActions,
                dataset = primaryLayer,
                pickTextFile = pickTextFile,
              )
              val deskForm = uiState.primaryForm
              if (uiState.offersPlotFieldValidation && deskForm != null) {
                WizardSectionTitle(stringResource(Res.string.surveyeditor_plot_review))
                PlotFieldValidationPanel(
                  dataset = primaryLayer,
                  options = uiState.plotReview?.options ?: PlotReviewOptions(),
                  deskFormTitle = deskForm.form.title,
                  onFieldValidationChange = actions::setPlotFieldValidation,
                  onOptionsChange = actions::updatePlotFieldValidation,
                )
              }
            }

            WizardMapFeatureSource.IMPORT_FILE -> {
              WizardSectionTitle(stringResource(Res.string.wizard_import_features_title))
              Text(
                text = stringResource(Res.string.wizard_import_features_supporting),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                OutlinedButton(
                  onClick = {
                    importError = null
                    pickTextFile(GEOMETRY_FILE_ACCEPT) { result ->
                      when (result) {
                        is TextFilePickResult.Picked -> {
                          val preview =
                            editorActions.previewMapLayerImport(result.fileName, result.text)
                          importPreview = preview
                          preview.plan?.let(actions::importPrimaryMapLayer)
                        }
                        is TextFilePickResult.Failed -> importError = result.message
                        TextFilePickResult.Cancelled -> Unit
                      }
                    }
                  }
                ) {
                  Icon(
                    Icons.Outlined.UploadFile,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(Modifier.width(6.dp))
                  Text(stringResource(Res.string.wizard_import_features_choose_file))
                }
                if (primaryLayer.rows.isNotEmpty()) {
                  TonalBadge(
                    text =
                      pluralStringResource(
                        Res.plurals.wizard_import_features_loaded,
                        primaryLayer.rows.size,
                        primaryLayer.rows.size,
                        stringResource(primaryLayer.geometryType.pluralRes).lowercase(),
                      ),
                    tone = BadgeTone.PRIMARY,
                  )
                }
              }
              importError?.let { msg ->
                Text(
                  text = msg.resolve(),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.error,
                )
              }
              importPreview?.let { preview ->
                IssueList(preview.result.issues.map { it.toIssueLine() })
              }
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = stringResource(Res.string.wizard_also_allow_field_creation),
                    style = MaterialTheme.typography.bodyMedium,
                  )
                  Text(
                    text = stringResource(Res.string.wizard_also_allow_field_creation_supporting),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Switch(
                  checked = primaryLayer.fieldCreationEnabled,
                  onCheckedChange = actions::updatePrimaryLayerFieldCreation,
                )
              }
            }
          }
        }
      }
    }
  }

  val otherDatasetCount = editorUiState.datasets.count { it.key != primaryLayer?.key }
  if (otherDatasetCount > 0) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        Icons.Outlined.Info,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp),
      )
      Text(
        text =
          pluralStringResource(
            Res.plurals.wizard_other_datasets_note,
            otherDatasetCount,
            otherDatasetCount,
          ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

private fun seedPreviewFeaturesForFreeformLayer(
  dataset: EntityDataset,
  surveyArea: SurveyArea?,
): EntityDataset {
  val areaPoints = surveyArea?.parts?.flatten().orEmpty()
  val centerLat: Double
  val centerLng: Double
  val latSpan: Double
  val lngSpan: Double
  if (areaPoints.isNotEmpty()) {
    val minLat = areaPoints.minOf { it.latitude }
    val maxLat = areaPoints.maxOf { it.latitude }
    val minLng = areaPoints.minOf { it.longitude }
    val maxLng = areaPoints.maxOf { it.longitude }
    centerLat = (minLat + maxLat) / 2.0
    centerLng = (minLng + maxLng) / 2.0
    latSpan = ((maxLat - minLat).coerceAtLeast(0.008)) * 0.22
    lngSpan = ((maxLng - minLng).coerceAtLeast(0.008)) * 0.22
  } else {
    centerLat = -0.4167
    centerLng = 36.95
    latSpan = 0.012
    lngSpan = 0.012
  }
  val offsets = listOf(-0.6 to -0.45, 0.08 to 0.45, 0.55 to -0.2)
  val previewRows = offsets.mapIndexed { idx, (dLatFactor, dLngFactor) ->
    val lat = centerLat + latSpan * dLatFactor
    val lng = centerLng + lngSpan * dLngFactor
    val rLat = latSpan * 0.28
    val rLng = lngSpan * 0.28
    val geometry =
      when (dataset.geometryType) {
        GeometryType.POINT -> listOf(LatLng.coerced(lat, lng))
        GeometryType.LINE_STRING ->
          listOf(
            LatLng.coerced(lat - rLat * 0.7, lng - rLng * 0.8),
            LatLng.coerced(lat + rLat * 0.2, lng),
            LatLng.coerced(lat - rLat * 0.3, lng + rLng * 0.85),
          )
        GeometryType.POLYGON ->
          listOf(
            LatLng.coerced(lat - rLat, lng - rLng),
            LatLng.coerced(lat + rLat, lng - rLng * 0.85),
            LatLng.coerced(lat + rLat * 0.9, lng + rLng),
            LatLng.coerced(lat - rLat * 0.85, lng + rLng * 0.9),
          )
      }
    EntityRow(
      key = "${dataset.key}-example-${idx + 1}",
      values =
        mapOf(
          dataset.keyProperty to "example_${idx + 1}",
          dataset.labelProperty to "Example ${idx + 1}",
        ),
      geometry = geometry,
    )
  }
  return dataset.copy(
    key = "${dataset.key}-preview-${dataset.geometryType.name}",
    rows = previewRows,
  )
}
