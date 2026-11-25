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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.editor.AllocationMode
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.PlotShapeOption
import org.openforis.collectone.v2.app.domain.model.editor.SampleAreaSource
import org.openforis.collectone.v2.app.domain.model.editor.SampleDesignConfig
import org.openforis.collectone.v2.app.domain.model.editor.SampleMethod
import org.openforis.collectone.v2.app.domain.model.editor.SubPlotMode
import org.openforis.collectone.v2.app.domain.model.editor.SurveyArea
import org.openforis.collectone.v2.app.domain.model.editor.SurveyAreaGeometry
import org.openforis.collectone.v2.app.ui.text.UiText
import org.openforis.collectone.v2.app.ui.text.descriptionRes
import org.openforis.collectone.v2.app.ui.text.labelRes
import org.openforis.collectone.v2.app.ui.text.toUiText
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.Switch
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.web.domain.model.editor.MapLayerImportPlan
import org.openforis.collectone.v2.web.domain.model.editor.MapLayerImportPreview
import org.openforis.collectone.v2.web.domain.model.editor.SampleDesignInputs
import org.openforis.collectone.v2.web.domain.model.editor.SamplingAreaResult
import org.openforis.collectone.v2.web.domain.model.editor.SamplingAreas
import org.openforis.collectone.v2.web.domain.model.editor.SuggestedGridSpacing
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.surveyeditor_cancel
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_allocation
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_area
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_area_deleted_layer
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_area_strata_from
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_area_survey_area
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_choose
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_cluster_spacing
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_enter_number_at_least
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_enter_whole_number_at_least
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_enter_whole_number_range
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_estimate
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_estimate_title
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_estimate_unavailable
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_generate_failed_title
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_generating
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_generating_of_about
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_grid_spacing
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_grid_spacing_auto_description
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_grid_spacing_description
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_in_use_title
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_large_design_warning
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_match_area_size
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_method
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_method_description
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_min_distance
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_more_strata
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_number_of_clusters
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_number_of_plots
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_number_of_points
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_plot_diameter
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_plot_shape
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_plot_side
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_plots_in_stratum
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_plots_in_survey_area
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_plots_per_cluster
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_plots_per_side
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_point_spacing
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_points_per_plot
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_points_per_side
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_previewing_plots
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_random_seed
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_random_seed_description
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_reroll_seed
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_sample_points
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_sample_points_require_shape
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_show_preview
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_shuffle
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_shuffle_description
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_stale_body
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_stale_title
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_strata_file_no_polygons
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_strata_file_not_polygons
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_strata_list
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_stratum_property
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_survey_area_one_stratum
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_total_plots
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_update_preview
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_upload_strata_file
import org.openforis.collectone.v2.web.ui.common.DropdownSelector
import org.openforis.collectone.v2.web.ui.common.TextFilePickResult
import org.openforis.collectone.v2.web.ui.common.TextFilePicker
import org.openforis.collectone.v2.web.ui.common.openPlatformTextFilePicker
import org.openforis.collectone.v2.web.ui.text.geoIssueText
import org.openforis.collectone.v2.web.ui.text.resolve
import org.openforis.collectone.v2.web.ui.text.toEditorText

/** Above this many plots the editor warns that generating and editing may be slow. */
private const val LARGE_DESIGN_PLOTS = 20_000

/** Custom allocations list at most this many strata; the rest keep their current counts. */
private const val MAX_CUSTOM_STRATA_SHOWN = 30

/**
 * Sample design section of a generated Map layer's settings: area, method and its parameters
 * (progressive disclosure), plot shape, sample points, ordering and seed, a live estimate, and Show
 * / Update preview with progress and Cancel.
 *
 * Shared by the survey editor and the create-survey wizard. Previews only change the working draft;
 * out-of-date designs are regenerated and committed when the draft is saved or published.
 */
@Composable
fun SamplingDesignPanel(
  uiState: SurveyEditorUiState,
  actions: SurveyEditorActions,
  dataset: EntityDataset,
  pickTextFile: TextFilePicker = ::openPlatformTextFilePicker,
) {
  val config = dataset.generator ?: return
  val key = dataset.key
  fun update(transform: (SampleDesignConfig) -> SampleDesignConfig) =
    actions.updateSampleDesign(key, transform)

  val progress = uiState.generation?.takeIf { it.datasetKey == key }
  val busy = uiState.generation != null
  val lastRun = config.lastRun
  val hasGeneratedPlots = lastRun != null && dataset.rows.isNotEmpty()
  val allDatasets =
    remember(uiState.datasets, dataset) {
      if (uiState.datasets.any { it.key == key }) uiState.datasets else uiState.datasets + dataset
    }
  val areaResult =
    remember(config.areaSource, uiState.details.surveyArea, allDatasets) {
      SamplingAreas.samplingArea(config, uiState.details.surveyArea, allDatasets)
    }
  val currentHash =
    remember(config.withoutProvenance(), areaResult) {
      (areaResult as? SamplingAreaResult.Ready)?.let { SampleDesignInputs.hash(it.area, config) }
    }
  val stale = lastRun != null && currentHash != lastRun.inputHash
  val blockedReason = uiState.regenerateBlockedReason(dataset)?.resolve()
  val error = uiState.generationError(key)?.resolve()

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    if (stale && blockedReason == null) {
      Banner(
        icon = Icons.Outlined.Warning,
        title = stringResource(Res.string.surveyeditor_sampling_stale_title),
        body = stringResource(Res.string.surveyeditor_sampling_stale_body),
        container = MaterialTheme.colorScheme.tertiaryContainer,
        content = MaterialTheme.colorScheme.onTertiaryContainer,
      )
    }
    if (blockedReason != null) {
      Banner(
        icon = Icons.Outlined.Lock,
        title = stringResource(Res.string.surveyeditor_sampling_in_use_title),
        body = blockedReason,
        container = MaterialTheme.colorScheme.surfaceContainerHigh,
        content = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (error != null) {
      Banner(
        icon = Icons.Outlined.Warning,
        title = stringResource(Res.string.surveyeditor_sampling_generate_failed_title),
        body = error,
        container = MaterialTheme.colorScheme.errorContainer,
        content = MaterialTheme.colorScheme.onErrorContainer,
      )
    }

    AreaSourceSelector(uiState, actions, config, allDatasets, ::update, pickTextFile)
    if (areaResult is SamplingAreaResult.Unavailable) {
      Text(
        areaResult.message.toEditorText().resolve(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
      )
    }

    ThumbnailSelector(
      label = stringResource(Res.string.surveyeditor_sampling_method),
      options = SampleMethod.entries,
      selected = config.method,
      optionText = { it.cardLabel },
      onSelect = { m -> update { it.copy(method = m) } },
      thumbnail = { m, colors -> drawSampleMethodThumbnail(m, colors) },
      enabled = !uiState.isReadOnly,
    )
    Text(
      stringResource(
        Res.string.surveyeditor_sampling_method_description,
        stringResource(config.method.labelRes),
        stringResource(config.method.descriptionRes),
      ),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    MethodParameters(areaResult, allDatasets, config, ::update, enabled = !uiState.isReadOnly)

    ThumbnailSelector(
      label = stringResource(Res.string.surveyeditor_sampling_plot_shape),
      options = PlotShapeOption.entries,
      selected = config.plotShape,
      optionText = { it.cardLabel },
      onSelect = { s ->
        update {
          it.copy(
            plotShape = s,
            subPlot = if (s == PlotShapeOption.POINT) SubPlotMode.NONE else it.subPlot,
          )
        }
      },
      thumbnail = { s, colors -> drawPlotShapeThumbnail(s, colors) },
      enabled = !uiState.isReadOnly,
    )
    val isPointPlot = config.plotShape == PlotShapeOption.POINT
    if (!isPointPlot) {
      DecimalField(
        label =
          stringResource(
            if (config.plotShape == PlotShapeOption.CIRCLE) {
              Res.string.surveyeditor_sampling_plot_diameter
            } else {
              Res.string.surveyeditor_sampling_plot_side
            }
          ),
        value = config.plotSizeM,
        min = 1.0,
        enabled = !uiState.isReadOnly,
        onValue = { v -> update { it.copy(plotSizeM = v) } },
      )
    }

    ThumbnailSelector(
      label = stringResource(Res.string.surveyeditor_sampling_sample_points),
      options = SubPlotMode.entries,
      selected = if (isPointPlot) SubPlotMode.NONE else config.subPlot,
      optionText = { it.cardLabel },
      onSelect = { m -> update { it.copy(subPlot = m) } },
      thumbnail = { m, colors -> drawSubPlotThumbnail(m, config.plotShape, colors) },
      enabled = !isPointPlot && !uiState.isReadOnly,
      supportingText =
        if (isPointPlot) {
          stringResource(Res.string.surveyeditor_sampling_sample_points_require_shape)
        } else {
          null
        },
    )
    if (!isPointPlot) {
      when (config.subPlot) {
        SubPlotMode.GRID -> {
          IntegerField(
            label = stringResource(Res.string.surveyeditor_sampling_points_per_side),
            value = config.subPlotGridN.toLong(),
            min = 1,
            max = 10,
            enabled = !uiState.isReadOnly,
            supportingText =
              pluralStringResource(
                Res.plurals.surveyeditor_sampling_points_per_plot,
                config.subPlotGridN * config.subPlotGridN,
                config.subPlotGridN * config.subPlotGridN,
              ),
            onValue = { v -> update { it.copy(subPlotGridN = v.toInt()) } },
          )
          DecimalField(
            label = stringResource(Res.string.surveyeditor_sampling_point_spacing),
            value = config.subPlotSpacingM,
            min = 0.1,
            enabled = !uiState.isReadOnly,
            onValue = { v -> update { it.copy(subPlotSpacingM = v) } },
          )
        }
        SubPlotMode.RANDOM ->
          IntegerField(
            label = stringResource(Res.string.surveyeditor_sampling_number_of_points),
            value = config.subPlotRandomCount.toLong(),
            min = 1,
            max = 100,
            enabled = !uiState.isReadOnly,
            onValue = { v -> update { it.copy(subPlotRandomCount = v.toInt()) } },
          )
        SubPlotMode.NONE,
        SubPlotMode.CENTER -> Unit
      }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          stringResource(Res.string.surveyeditor_sampling_shuffle),
          style = MaterialTheme.typography.bodyMedium,
        )
        Text(
          stringResource(Res.string.surveyeditor_sampling_shuffle_description),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Switch(
        checked = config.shuffle,
        enabled = !uiState.isReadOnly,
        onCheckedChange = { v -> update { it.copy(shuffle = v) } },
      )
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      IntegerField(
        label = stringResource(Res.string.surveyeditor_sampling_random_seed),
        value = config.seed,
        min = 0,
        enabled = !uiState.isReadOnly,
        supportingText = stringResource(Res.string.surveyeditor_sampling_random_seed_description),
        onValue = { v -> update { it.copy(seed = v) } },
        modifier = Modifier.weight(1f),
      )
      if (!uiState.isReadOnly) {
        IconButton(onClick = { actions.rerollSeed(key) }, enabled = !busy) {
          Icon(
            Icons.Outlined.Casino,
            contentDescription = stringResource(Res.string.surveyeditor_sampling_reroll_seed),
          )
        }
      }
    }

    EstimateLine(uiState, actions, dataset, config, areaResult)

    if (progress != null) {
      val fraction = progress.fraction
      if (fraction != null) {
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
      } else {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        val plotsSoFar = SurveyAreaGeometry.groupThousands(progress.plotsSoFar.toLong())
        Text(
          if (progress.expectedPlots > 0) {
            pluralStringResource(
              Res.plurals.surveyeditor_sampling_generating_of_about,
              progress.expectedPlots,
              plotsSoFar,
              SurveyAreaGeometry.groupThousands(progress.expectedPlots.toLong()),
            )
          } else {
            pluralStringResource(
              Res.plurals.surveyeditor_sampling_generating,
              progress.plotsSoFar,
              plotsSoFar,
            )
          },
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = actions::cancelGeneration) {
          Text(stringResource(Res.string.surveyeditor_cancel))
        }
      }
    } else if (!uiState.isReadOnly) {
      Button(
        onClick = { actions.generateSamplePlots(key) },
        enabled = !busy && blockedReason == null && areaResult is SamplingAreaResult.Ready,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          stringResource(
            if (hasGeneratedPlots) Res.string.surveyeditor_sampling_update_preview
            else Res.string.surveyeditor_sampling_show_preview
          )
        )
      }
    }
    if (lastRun != null && hasGeneratedPlots) {
      Text(
        pluralStringResource(
          Res.plurals.surveyeditor_sampling_previewing_plots,
          lastRun.featureCount,
          SurveyAreaGeometry.groupThousands(lastRun.featureCount.toLong()),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** Survey area or a polygon Map layer (plus the property that names each stratum). */
@Composable
private fun AreaSourceSelector(
  uiState: SurveyEditorUiState,
  actions: DatasetActions,
  config: SampleDesignConfig,
  allDatasets: List<EntityDataset>,
  update: ((SampleDesignConfig) -> SampleDesignConfig) -> Unit,
  pickTextFile: TextFilePicker,
) {
  val strataLayers = uiState.strataLayers
  var uploadError by remember { mutableStateOf<UiText?>(null) }
  val options: List<SampleAreaSource> =
    listOf(SampleAreaSource.SurveyArea) +
      strataLayers.map { layer ->
        val current = config.areaSource as? SampleAreaSource.StrataLayer
        val property =
          current?.takeIf { it.datasetKey == layer.key }?.stratumProperty ?: layer.labelProperty
        SampleAreaSource.StrataLayer(layer.key, property)
      }
  val surveyAreaLabel = stringResource(Res.string.surveyeditor_sampling_area_survey_area)
  val deletedLayerLabel = stringResource(Res.string.surveyeditor_sampling_area_deleted_layer)
  val strataFromLabels = strataLayers.associate {
    it.key to stringResource(Res.string.surveyeditor_sampling_area_strata_from, it.displayName)
  }
  fun labelOf(source: SampleAreaSource): String =
    when (source) {
      SampleAreaSource.SurveyArea -> surveyAreaLabel
      is SampleAreaSource.StrataLayer -> strataFromLabels[source.datasetKey] ?: deletedLayerLabel
    }
  if (strataLayers.isNotEmpty() || config.areaSource is SampleAreaSource.StrataLayer) {
    DropdownSelector(
      label = stringResource(Res.string.surveyeditor_sampling_area),
      selectedText = labelOf(config.areaSource),
      options = options,
      optionText = { labelOf(it) },
      enabled = !uiState.isReadOnly,
      onSelect = { source -> update { it.copy(areaSource = source) } },
    )
  }
  if (
    !uiState.isReadOnly &&
      (config.method == SampleMethod.STRATIFIED_RANDOM ||
        strataLayers.isNotEmpty() ||
        config.areaSource is SampleAreaSource.StrataLayer)
  ) {
    OutlinedButton(
      onClick = {
        uploadError = null
        pickTextFile(GEOMETRY_FILE_ACCEPT) { result ->
          when (result) {
            is TextFilePickResult.Picked ->
              when (
                val check =
                  checkStrataFile(actions.previewMapLayerImport(result.fileName, result.text))
              ) {
                is StrataFileCheck.Unusable -> uploadError = check.reason
                is StrataFileCheck.Usable -> {
                  val plan = check.plan
                  val newKey = actions.importMapLayer(plan, selectAfterImport = false)
                  update {
                    it.copy(areaSource = SampleAreaSource.StrataLayer(newKey, plan.labelProperty))
                  }
                }
              }
            is TextFilePickResult.Failed -> uploadError = result.message
            TextFilePickResult.Cancelled -> Unit
          }
        }
      },
      modifier = Modifier.fillMaxWidth(),
    ) {
      Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(6.dp))
      Text(stringResource(Res.string.surveyeditor_sampling_upload_strata_file))
    }
    uploadError?.let { msg ->
      Text(
        text = msg.resolve(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
      )
    }
  }
  val source = config.areaSource as? SampleAreaSource.StrataLayer ?: return
  val layer = strataLayers.firstOrNull { it.key == source.datasetKey } ?: return
  DropdownSelector(
    label = stringResource(Res.string.surveyeditor_sampling_stratum_property),
    selectedText =
      layer.property(source.stratumProperty)?.label
        ?: stringResource(Res.string.surveyeditor_sampling_choose),
    options = layer.properties,
    optionText = { it.label },
    enabled = !uiState.isReadOnly,
    onSelect = { p -> update { it.copy(areaSource = source.copy(stratumProperty = p.name)) } },
  )
  val strata = SamplingAreas.strataIds(config, allDatasets)
  Text(
    pluralStringResource(
      Res.plurals.surveyeditor_sampling_strata_list,
      strata.size,
      strata.size,
      strata.take(5).joinToString(", ") + if (strata.size > 5) ", …" else "",
    ),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

@Composable
private fun MethodParameters(
  areaResult: SamplingAreaResult,
  allDatasets: List<EntityDataset>,
  config: SampleDesignConfig,
  update: ((SampleDesignConfig) -> SampleDesignConfig) -> Unit,
  enabled: Boolean = true,
) {
  @Composable
  fun minDistance() =
    DecimalField(
      label = stringResource(Res.string.surveyeditor_sampling_min_distance),
      value = config.minDistanceM,
      min = 0.0,
      enabled = enabled,
      onValue = { v -> update { it.copy(minDistanceM = v) } },
    )

  when (config.method) {
    SampleMethod.SYSTEMATIC_GRID -> {
      val suggested =
        (areaResult as? SamplingAreaResult.Ready)?.let {
          SuggestedGridSpacing.forArea(it.area)
        }
      DecimalField(
        label = stringResource(Res.string.surveyeditor_sampling_grid_spacing),
        value = config.gridSpacingM,
        min = 1.0,
        enabled = enabled,
        supportingText =
          if (config.gridSpacingAuto && suggested != null) {
            stringResource(
              Res.string.surveyeditor_sampling_grid_spacing_auto_description,
              SuggestedGridSpacing.TARGET_PLOTS,
            )
          } else {
            stringResource(Res.string.surveyeditor_sampling_grid_spacing_description)
          },
        onValue = { v -> update { it.copy(gridSpacingM = v) } },
      )
      if (
        enabled && !config.gridSpacingAuto && suggested != null && suggested != config.gridSpacingM
      ) {
        TextButton(onClick = { update { it.copy(gridSpacingAuto = true) } }) {
          Text(
            stringResource(
              Res.string.surveyeditor_sampling_match_area_size,
              formatNumber(suggested),
            )
          )
        }
      }
    }
    SampleMethod.SIMPLE_RANDOM -> {
      IntegerField(
        label = stringResource(Res.string.surveyeditor_sampling_number_of_plots),
        value = config.count.toLong(),
        min = 1,
        enabled = enabled,
        onValue = { v -> update { it.copy(count = v.toInt()) } },
      )
      minDistance()
    }
    SampleMethod.STRATIFIED_RANDOM -> {
      if (config.areaSource == SampleAreaSource.SurveyArea) {
        Text(
          stringResource(Res.string.surveyeditor_sampling_survey_area_one_stratum),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      DropdownSelector(
        label = stringResource(Res.string.surveyeditor_sampling_allocation),
        selectedText = stringResource(config.allocation.labelRes),
        options = AllocationMode.entries,
        optionText = { stringResource(it.labelRes) },
        enabled = enabled,
        onSelect = { a -> update { it.copy(allocation = a) } },
      )
      if (config.allocation == AllocationMode.CUSTOM) {
        val strata = SamplingAreas.strataIds(config, allDatasets).ifEmpty { listOf("") }
        strata.take(MAX_CUSTOM_STRATA_SHOWN).forEach { id ->
          IntegerField(
            label =
              if (id.isBlank()) {
                stringResource(Res.string.surveyeditor_sampling_plots_in_survey_area)
              } else {
                stringResource(Res.string.surveyeditor_sampling_plots_in_stratum, id)
              },
            value = (config.customCounts[id] ?: 0).toLong(),
            min = 0,
            enabled = enabled,
            onValue = { v ->
              update { it.copy(customCounts = it.customCounts + (id to v.toInt())) }
            },
          )
        }
        if (strata.size > MAX_CUSTOM_STRATA_SHOWN) {
          Text(
            pluralStringResource(
              Res.plurals.surveyeditor_sampling_more_strata,
              strata.size - MAX_CUSTOM_STRATA_SHOWN,
              strata.size - MAX_CUSTOM_STRATA_SHOWN,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        IntegerField(
          label = stringResource(Res.string.surveyeditor_sampling_total_plots),
          value = config.count.toLong(),
          min = 1,
          enabled = enabled,
          onValue = { v -> update { it.copy(count = v.toInt()) } },
        )
      }
      minDistance()
    }
    SampleMethod.CLUSTER -> {
      IntegerField(
        label = stringResource(Res.string.surveyeditor_sampling_number_of_clusters),
        value = config.clusterCount.toLong(),
        min = 1,
        enabled = enabled,
        onValue = { v -> update { it.copy(clusterCount = v.toInt()) } },
      )
      IntegerField(
        label = stringResource(Res.string.surveyeditor_sampling_plots_per_side),
        value = config.plotsPerSide.toLong(),
        min = 1,
        max = 20,
        enabled = enabled,
        supportingText =
          pluralStringResource(
            Res.plurals.surveyeditor_sampling_plots_per_cluster,
            config.plotsPerSide * config.plotsPerSide,
            config.plotsPerSide * config.plotsPerSide,
          ),
        onValue = { v -> update { it.copy(plotsPerSide = v.toInt()) } },
      )
      DecimalField(
        label = stringResource(Res.string.surveyeditor_sampling_cluster_spacing),
        value = config.clusterSpacingM,
        min = 1.0,
        enabled = enabled,
        onValue = { v -> update { it.copy(clusterSpacingM = v) } },
      )
      minDistance()
    }
  }
}

@Composable
private fun EstimateLine(
  uiState: SurveyEditorUiState,
  actions: SamplePlotActions,
  dataset: EntityDataset,
  config: SampleDesignConfig,
  areaResult: SamplingAreaResult,
) {
  if (areaResult !is SamplingAreaResult.Ready) return
  // Recompute only when the design or the area itself changes, not on every edit elsewhere.
  val areaRef: Any? =
    when (val source = config.areaSource) {
      SampleAreaSource.SurveyArea -> uiState.details.surveyArea
      is SampleAreaSource.StrataLayer ->
        uiState.datasets.firstOrNull { it.key == source.datasetKey }?.rows
    }
  val estimate =
    remember(config.withoutProvenance(), areaRef) { actions.estimateSample(dataset.key) }
  val text =
    if (estimate == null) {
      stringResource(Res.string.surveyeditor_sampling_estimate_unavailable)
    } else {
      pluralStringResource(
        Res.plurals.surveyeditor_sampling_estimate,
        estimate.estimatedPlotCount,
        surveyAreaText(estimate.areaHa * 10_000).resolve(),
        SurveyAreaGeometry.groupThousands(estimate.estimatedPlotCount.toLong()),
      )
    }
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape = MaterialTheme.shapes.small,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
        stringResource(Res.string.surveyeditor_sampling_estimate_title),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
      )
      Text(text, style = MaterialTheme.typography.bodySmall)
      if ((estimate?.estimatedPlotCount ?: 0) > LARGE_DESIGN_PLOTS) {
        Text(
          stringResource(Res.string.surveyeditor_sampling_large_design_warning),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}

@Composable
private fun Banner(
  icon: ImageVector,
  title: String,
  body: String,
  container: Color,
  content: Color,
) {
  Surface(
    color = container,
    contentColor = content,
    shape = MaterialTheme.shapes.small,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
      Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(8.dp))
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(body, style = MaterialTheme.typography.bodySmall)
      }
    }
  }
}

/** A text field for a decimal number ≥ [min]; invalid text is shown as an error and not applied. */
@Composable
internal fun DecimalField(
  label: String,
  value: Double,
  onValue: (Double) -> Unit,
  modifier: Modifier = Modifier,
  min: Double = 0.0,
  enabled: Boolean = true,
  supportingText: String? = null,
) {
  var text by remember { mutableStateOf(formatNumber(value)) }
  LaunchedEffect(value) { if (text.toDoubleOrNull() != value) text = formatNumber(value) }
  val parsed = text.toDoubleOrNull()
  val valid = parsed != null && parsed >= min
  val invalidText =
    stringResource(Res.string.surveyeditor_sampling_enter_number_at_least, formatNumber(min))
  OutlinedTextField(
    value = text,
    onValueChange = { t ->
      text = t
      t.toDoubleOrNull()?.takeIf { it >= min }?.let(onValue)
    },
    enabled = enabled,
    label = { Text(label) },
    singleLine = true,
    isError = enabled && !valid,
    supportingText =
      when {
        enabled && !valid -> ({ Text(invalidText) })
        supportingText != null -> ({ Text(supportingText) })
        else -> null
      },
    modifier = modifier.fillMaxWidth(),
  )
}

/** A text field for a whole number in [min]..[max]; invalid text isn't applied. */
@Composable
internal fun IntegerField(
  label: String,
  value: Long,
  onValue: (Long) -> Unit,
  modifier: Modifier = Modifier,
  min: Long = 0,
  max: Long = Long.MAX_VALUE,
  enabled: Boolean = true,
  supportingText: String? = null,
) {
  var text by remember { mutableStateOf(value.toString()) }
  LaunchedEffect(value) { if (text.toLongOrNull() != value) text = value.toString() }
  val parsed = text.toLongOrNull()
  val valid = parsed != null && parsed in min..max
  val invalidText =
    if (max == Long.MAX_VALUE) {
      stringResource(Res.string.surveyeditor_sampling_enter_whole_number_at_least, min.toString())
    } else {
      stringResource(
        Res.string.surveyeditor_sampling_enter_whole_number_range,
        min.toString(),
        max.toString(),
      )
    }
  OutlinedTextField(
    value = text,
    onValueChange = { t ->
      text = t
      t.toLongOrNull()?.takeIf { it in min..max }?.let(onValue)
    },
    enabled = enabled,
    label = { Text(label) },
    singleLine = true,
    isError = enabled && !valid,
    supportingText =
      when {
        enabled && !valid -> ({ Text(invalidText) })
        supportingText != null -> ({ Text(supportingText) })
        else -> null
      },
    modifier = modifier.fillMaxWidth(),
  )
}

private fun formatNumber(v: Double): String =
  if (v == kotlin.math.floor(v) && kotlin.math.abs(v) < 1e15) v.toLong().toString()
  else v.toString()

/** Small info line used by the Add Map layer dialog and elsewhere. */
@Composable
internal fun InfoLine(text: String) {
  Row(verticalAlignment = Alignment.Top) {
    Icon(
      Icons.Outlined.Info,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(16.dp),
    )
    Spacer(Modifier.width(6.dp))
    Text(
      text,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/** What a picked strata file allows: importing its polygons, or nothing, and why. */
internal sealed interface StrataFileCheck {
  data class Usable(val plan: MapLayerImportPlan) : StrataFileCheck

  data class Unusable(val reason: UiText) : StrataFileCheck
}

/** Whether [preview] holds polygons to import as strata, or why it doesn't. */
internal fun checkStrataFile(preview: MapLayerImportPreview): StrataFileCheck {
  val plan = preview.plan
  return when {
    plan == null ->
      StrataFileCheck.Unusable(
        preview.result.issues.firstOrNull()?.let(::geoIssueText)
          ?: Res.string.surveyeditor_sampling_strata_file_no_polygons.toUiText(preview.fileName)
      )
    plan.geometryType != GeometryType.POLYGON ->
      StrataFileCheck.Unusable(Res.string.surveyeditor_sampling_strata_file_not_polygons.toUiText())
    else -> StrataFileCheck.Usable(plan)
  }
}
