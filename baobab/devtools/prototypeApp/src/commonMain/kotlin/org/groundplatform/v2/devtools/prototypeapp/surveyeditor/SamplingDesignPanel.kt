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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.groundplatform.v2.devtools.prototypeapp.formeditor.DropdownSelector

/** Above this many plots the prototype warns that generating and editing may be slow. */
private const val LARGE_DESIGN_PLOTS = 20_000

/** Custom allocations list at most this many strata; the rest keep their current counts. */
private const val MAX_CUSTOM_STRATA_SHOWN = 30

/**
 * Sample design section of a generated Map layer's settings: area, method and its parameters
 * (progressive disclosure), plot shape, sample points, ordering and seed, a live estimate, and
 * Generate / Regenerate with progress and Cancel.
 */
@Composable
internal fun SamplingDesignPanel(state: SurveyEditorState, dataset: EntityDataset) {
  val config = dataset.generator ?: return
  val key = dataset.key
  val scope = rememberCoroutineScope()
  fun update(transform: (SampleDesignConfig) -> SampleDesignConfig) =
    state.updateSampleDesign(key, transform)

  val progress = state.generation?.takeIf { it.datasetKey == key }
  val busy = state.generation != null
  val lastRun = config.lastRun
  val stale = state.isDesignStale(dataset)
  val blockedReason = state.regenerateBlockedReason(dataset)
  val error = state.generationError(key)
  val areaResult = state.samplingArea(config)
  var confirmRegenerate by remember { mutableStateOf(false) }

  fun generate() {
    scope.launch { state.generateSample(key) }
  }

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    if (stale && blockedReason == null) {
      Banner(
        icon = Icons.Outlined.Warning,
        title = "Design is out of date — regenerate",
        body = "The design or its area changed after these sample plots were generated.",
        container = MaterialTheme.colorScheme.tertiaryContainer,
        content = MaterialTheme.colorScheme.onTertiaryContainer,
      )
    }
    if (blockedReason != null) {
      Banner(
        icon = Icons.Outlined.Lock,
        title = "Sample plots are in use",
        body = blockedReason,
        container = MaterialTheme.colorScheme.surfaceContainerHigh,
        content = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (error != null) {
      Banner(
        icon = Icons.Outlined.Warning,
        title = "Couldn't generate sample plots",
        body = error,
        container = MaterialTheme.colorScheme.errorContainer,
        content = MaterialTheme.colorScheme.onErrorContainer,
      )
    }

    AreaSourceSelector(state, config, ::update)
    if (areaResult is SamplingAreaResult.Unavailable) {
      Text(
        areaResult.message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
      )
    }

    DropdownSelector(
      label = "Method",
      selectedText = config.method.label,
      options = SampleMethod.entries,
      optionText = { it.label },
      onSelect = { m -> update { it.copy(method = m) } },
    )
    Text(
      config.method.description,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    MethodParameters(state, config, ::update)

    DropdownSelector(
      label = "Plot shape",
      selectedText = config.plotShape.label,
      options = PlotShapeOption.entries,
      optionText = { it.label },
      onSelect = { s -> update { it.copy(plotShape = s) } },
    )
    if (config.plotShape != PlotShapeOption.POINT) {
      DecimalField(
        label =
          if (config.plotShape == PlotShapeOption.CIRCLE) "Plot diameter (m)" else "Plot side (m)",
        value = config.plotSizeM,
        min = 1.0,
        onValue = { v -> update { it.copy(plotSizeM = v) } },
      )
    }

    DropdownSelector(
      label = "Sample points in each plot",
      selectedText = config.subPlot.label,
      options = SubPlotMode.entries,
      optionText = { it.label },
      onSelect = { m -> update { it.copy(subPlot = m) } },
    )
    when (config.subPlot) {
      SubPlotMode.GRID -> {
        IntegerField(
          label = "Points per side",
          value = config.subPlotGridN.toLong(),
          min = 1,
          max = 10,
          supportingText = "${config.subPlotGridN * config.subPlotGridN} sample points per plot",
          onValue = { v -> update { it.copy(subPlotGridN = v.toInt()) } },
        )
        DecimalField(
          label = "Spacing between points (m)",
          value = config.subPlotSpacingM,
          min = 0.1,
          onValue = { v -> update { it.copy(subPlotSpacingM = v) } },
        )
      }
      SubPlotMode.RANDOM ->
        IntegerField(
          label = "Number of points",
          value = config.subPlotRandomCount.toLong(),
          min = 1,
          max = 100,
          onValue = { v -> update { it.copy(subPlotRandomCount = v.toInt()) } },
        )
      SubPlotMode.NONE,
      SubPlotMode.CENTER -> Unit
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(modifier = Modifier.weight(1f)) {
        Text("Shuffle visiting order", style = MaterialTheme.typography.bodyMedium)
        Text(
          "Number plots in random order so neighbors aren't visited one after another.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Switch(checked = config.shuffle, onCheckedChange = { v -> update { it.copy(shuffle = v) } })
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      IntegerField(
        label = "Random seed",
        value = config.seed,
        min = 0,
        supportingText = "The same seed always gives the same plots.",
        onValue = { v -> update { it.copy(seed = v) } },
        modifier = Modifier.weight(1f),
      )
      IconButton(onClick = { state.rerollSeed(key) }, enabled = !busy) {
        Icon(Icons.Outlined.Casino, contentDescription = "Pick a new random seed")
      }
    }

    EstimateLine(state, dataset, config, areaResult)

    if (progress != null) {
      val fraction = progress.fraction
      if (fraction != null) {
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
      } else {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          buildString {
            append("Generating… ${SurveyAreaGeometry.groupThousands(progress.plotsSoFar.toLong())}")
            if (progress.expectedPlots > 0) {
              append(
                " of about ${SurveyAreaGeometry.groupThousands(progress.expectedPlots.toLong())}"
              )
            }
            append(" plots")
          },
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = state::cancelGeneration) { Text("Cancel") }
      }
    } else {
      Button(
        onClick = {
          if (lastRun != null && dataset.rows.isNotEmpty()) confirmRegenerate = true else generate()
        },
        enabled = !busy && blockedReason == null && areaResult is SamplingAreaResult.Ready,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(if (lastRun == null) "Generate sample plots" else "Regenerate sample plots")
      }
    }
    if (lastRun != null) {
      Text(
        "${SurveyAreaGeometry.groupThousands(lastRun.featureCount.toLong())} plots generated " +
          "${lastRun.generatedAt.replace('T', ' ').removeSuffix("Z")} UTC " +
          "(engine version ${lastRun.engineVersion})",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }

  if (confirmRegenerate) {
    AlertDialog(
      onDismissRequest = { confirmRegenerate = false },
      title = { Text("Regenerate sample plots?") },
      text = {
        Text(
          "This replaces all ${SurveyAreaGeometry.groupThousands(dataset.rows.size.toLong())} " +
            "sample plots. Values in columns you added are kept for plots with the same plot ID."
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            confirmRegenerate = false
            generate()
          }
        ) {
          Text("Regenerate")
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmRegenerate = false }) { Text("Keep current plots") }
      },
    )
  }
}

/** Survey area or a polygon Map layer (plus the property that names each stratum). */
@Composable
private fun AreaSourceSelector(
  state: SurveyEditorState,
  config: SampleDesignConfig,
  update: ((SampleDesignConfig) -> SampleDesignConfig) -> Unit,
) {
  val strataLayers = state.strataLayers
  val options: List<SampleAreaSource> =
    listOf(SampleAreaSource.SurveyArea) +
      strataLayers.map { layer ->
        val current = config.areaSource as? SampleAreaSource.StrataLayer
        val property =
          current?.takeIf { it.datasetKey == layer.key }?.stratumProperty ?: layer.labelProperty
        SampleAreaSource.StrataLayer(layer.key, property)
      }
  fun labelOf(source: SampleAreaSource): String =
    when (source) {
      SampleAreaSource.SurveyArea -> "Survey area"
      is SampleAreaSource.StrataLayer ->
        strataLayers
          .firstOrNull { it.key == source.datasetKey }
          ?.let { "Strata from ${it.displayName}" } ?: "Deleted map layer"
    }
  DropdownSelector(
    label = "Area",
    selectedText = labelOf(config.areaSource),
    options = options,
    optionText = ::labelOf,
    onSelect = { source -> update { it.copy(areaSource = source) } },
  )
  val source = config.areaSource as? SampleAreaSource.StrataLayer ?: return
  val layer = strataLayers.firstOrNull { it.key == source.datasetKey } ?: return
  DropdownSelector(
    label = "Stratum property",
    selectedText = layer.property(source.stratumProperty)?.label ?: "Choose…",
    options = layer.properties,
    optionText = { "${it.label} (${it.name})" },
    onSelect = { p -> update { it.copy(areaSource = source.copy(stratumProperty = p.name)) } },
  )
  val strata = state.strataIds(config)
  Text(
    "${strata.size} ${if (strata.size == 1) "stratum" else "strata"}: " +
      strata.take(5).joinToString(", ") +
      if (strata.size > 5) ", …" else "",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

@Composable
private fun MethodParameters(
  state: SurveyEditorState,
  config: SampleDesignConfig,
  update: ((SampleDesignConfig) -> SampleDesignConfig) -> Unit,
) {
  @Composable
  fun minDistance() =
    DecimalField(
      label = "Minimum distance between plots (m)",
      value = config.minDistanceM,
      min = 0.0,
      onValue = { v -> update { it.copy(minDistanceM = v) } },
    )

  when (config.method) {
    SampleMethod.SYSTEMATIC_GRID ->
      DecimalField(
        label = "Grid spacing (m)",
        value = config.gridSpacingM,
        min = 1.0,
        supportingText = "Distance between neighboring plot centers.",
        onValue = { v -> update { it.copy(gridSpacingM = v) } },
      )
    SampleMethod.SIMPLE_RANDOM -> {
      IntegerField(
        label = "Number of plots",
        value = config.count.toLong(),
        min = 1,
        onValue = { v -> update { it.copy(count = v.toInt()) } },
      )
      minDistance()
    }
    SampleMethod.STRATIFIED_RANDOM -> {
      if (config.areaSource == SampleAreaSource.SurveyArea) {
        Text(
          "The survey area counts as one stratum. To sample strata separately, choose a polygon " +
            "map layer as the area.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      DropdownSelector(
        label = "Allocation",
        selectedText = config.allocation.label,
        options = AllocationMode.entries,
        optionText = { it.label },
        onSelect = { a -> update { it.copy(allocation = a) } },
      )
      if (config.allocation == AllocationMode.CUSTOM) {
        val strata = state.strataIds(config).ifEmpty { listOf("") }
        strata.take(MAX_CUSTOM_STRATA_SHOWN).forEach { id ->
          IntegerField(
            label = "Plots in ${id.ifBlank { "survey area" }}",
            value = (config.customCounts[id] ?: 0).toLong(),
            min = 0,
            onValue = { v ->
              update { it.copy(customCounts = it.customCounts + (id to v.toInt())) }
            },
          )
        }
        if (strata.size > MAX_CUSTOM_STRATA_SHOWN) {
          Text(
            "${strata.size - MAX_CUSTOM_STRATA_SHOWN} more strata get no plots unless set.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        IntegerField(
          label = "Total plots",
          value = config.count.toLong(),
          min = 1,
          onValue = { v -> update { it.copy(count = v.toInt()) } },
        )
      }
      minDistance()
    }
    SampleMethod.CLUSTER -> {
      IntegerField(
        label = "Number of clusters",
        value = config.clusterCount.toLong(),
        min = 1,
        onValue = { v -> update { it.copy(clusterCount = v.toInt()) } },
      )
      IntegerField(
        label = "Plots per side",
        value = config.plotsPerSide.toLong(),
        min = 1,
        max = 20,
        supportingText = "${config.plotsPerSide * config.plotsPerSide} plots in each cluster",
        onValue = { v -> update { it.copy(plotsPerSide = v.toInt()) } },
      )
      DecimalField(
        label = "Spacing between plots in a cluster (m)",
        value = config.clusterSpacingM,
        min = 1.0,
        onValue = { v -> update { it.copy(clusterSpacingM = v) } },
      )
      minDistance()
    }
  }
}

@Composable
private fun EstimateLine(
  state: SurveyEditorState,
  dataset: EntityDataset,
  config: SampleDesignConfig,
  areaResult: SamplingAreaResult,
) {
  if (areaResult !is SamplingAreaResult.Ready) return
  // Recompute only when the design or the area itself changes, not on every edit elsewhere.
  val areaRef: Any? =
    when (val source = config.areaSource) {
      SampleAreaSource.SurveyArea -> state.details.surveyArea
      is SampleAreaSource.StrataLayer ->
        state.datasets.firstOrNull { it.key == source.datasetKey }?.rows
    }
  val estimate = remember(config.withoutProvenance(), areaRef) { state.estimateSample(dataset.key) }
  val text =
    if (estimate == null) {
      "Estimate not available yet."
    } else {
      "Area ${SurveyAreaGeometry.formatArea(estimate.areaHa * 10_000)} • about " +
        "${SurveyAreaGeometry.groupThousands(estimate.estimatedPlotCount.toLong())} plots"
    }
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape = MaterialTheme.shapes.small,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text("Estimate", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
      Text(text, style = MaterialTheme.typography.bodySmall)
      if ((estimate?.estimatedPlotCount ?: 0) > LARGE_DESIGN_PLOTS) {
        Text(
          "Large designs can be slow to generate and edit in this prototype.",
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
  supportingText: String? = null,
) {
  var text by remember { mutableStateOf(formatNumber(value)) }
  LaunchedEffect(value) { if (text.toDoubleOrNull() != value) text = formatNumber(value) }
  val parsed = text.toDoubleOrNull()
  val valid = parsed != null && parsed >= min
  OutlinedTextField(
    value = text,
    onValueChange = { t ->
      text = t
      t.toDoubleOrNull()?.takeIf { it >= min }?.let(onValue)
    },
    label = { Text(label) },
    singleLine = true,
    isError = !valid,
    supportingText =
      when {
        !valid -> ({ Text("Enter a number of at least ${formatNumber(min)}.") })
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
  supportingText: String? = null,
) {
  var text by remember { mutableStateOf(value.toString()) }
  LaunchedEffect(value) { if (text.toLongOrNull() != value) text = value.toString() }
  val parsed = text.toLongOrNull()
  val valid = parsed != null && parsed in min..max
  OutlinedTextField(
    value = text,
    onValueChange = { t ->
      text = t
      t.toLongOrNull()?.takeIf { it in min..max }?.let(onValue)
    },
    label = { Text(label) },
    singleLine = true,
    isError = !valid,
    supportingText =
      when {
        !valid && max == Long.MAX_VALUE -> ({ Text("Enter a whole number of at least $min.") })
        !valid -> ({ Text("Enter a whole number from $min to $max.") })
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
