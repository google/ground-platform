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
package org.openforis.collectone.v2.web.ui.timeseries

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import openforis.collectone.v1.survey.SceneStretch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.editor.SurveyAreaGeometry
import org.openforis.collectone.v2.app.domain.model.editor.formatFixed
import org.openforis.collectone.v2.app.ui.common.EARTH_ENGINE_PANEL_BADGE_TAG
import org.openforis.collectone.v2.app.ui.common.FlatFilterChip
import org.openforis.collectone.v2.app.ui.common.PoweredByEarthEngineBadge
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.web_time_series_area
import org.openforis.collectone.v2.web.resources.web_time_series_band_picker
import org.openforis.collectone.v2.web.resources.web_time_series_chart_description
import org.openforis.collectone.v2.web.resources.web_time_series_chart_hint
import org.openforis.collectone.v2.web.resources.web_time_series_elevation
import org.openforis.collectone.v2.web.resources.web_time_series_elevation_range
import org.openforis.collectone.v2.web.resources.web_time_series_error_busy
import org.openforis.collectone.v2.web.resources.web_time_series_error_denied
import org.openforis.collectone.v2.web.resources.web_time_series_error_not_uploaded
import org.openforis.collectone.v2.web.resources.web_time_series_error_other
import org.openforis.collectone.v2.web.resources.web_time_series_error_timeout
import org.openforis.collectone.v2.web.resources.web_time_series_error_unavailable
import org.openforis.collectone.v2.web.resources.web_time_series_error_unsupported
import org.openforis.collectone.v2.web.resources.web_time_series_loading
import org.openforis.collectone.v2.web.resources.web_time_series_no_observations
import org.openforis.collectone.v2.web.resources.web_time_series_observations
import org.openforis.collectone.v2.web.resources.web_time_series_population
import org.openforis.collectone.v2.web.resources.web_time_series_population_density
import org.openforis.collectone.v2.web.resources.web_time_series_retry
import org.openforis.collectone.v2.web.resources.web_time_series_scene_clear
import org.openforis.collectone.v2.web.resources.web_time_series_scene_failed
import org.openforis.collectone.v2.web.resources.web_time_series_scene_loading
import org.openforis.collectone.v2.web.resources.web_time_series_select_feature
import org.openforis.collectone.v2.web.resources.web_time_series_selected
import org.openforis.collectone.v2.web.resources.web_time_series_source_picker
import org.openforis.collectone.v2.web.resources.web_time_series_stretch_nir_swir_red
import org.openforis.collectone.v2.web.resources.web_time_series_stretch_sar
import org.openforis.collectone.v2.web.resources.web_time_series_stretch_swir_nir_red
import org.openforis.collectone.v2.web.resources.web_time_series_stretch_true_color
import org.openforis.collectone.v2.web.resources.web_time_series_unavailable
import org.openforis.collectone.v2.web.resources.web_time_series_unsupported

/**
 * Content of the dashboard's "Time series & imagery" tab (Collect Earth Online's Geo-Dash): the
 * selected map feature's area, elevation, and population, and a chart of one band's plot means over
 * time from Landsat, Sentinel-2, or Sentinel-1. Selecting an observation shows its scene on the map
 * ([PinnedSceneBanner]).
 */
@Composable
fun TimeSeriesPanel(
  uiState: TimeSeriesUiState,
  actions: TimeSeriesActions,
  modifier: Modifier = Modifier,
) {
  val plot = uiState.plot
  Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
    when {
      !uiState.isAvailable ->
        PanelMessageWithAttribution(stringResource(Res.string.web_time_series_unavailable))
      plot == null ->
        PanelMessageWithAttribution(stringResource(Res.string.web_time_series_select_feature))
      !plot.isSupported ->
        PanelMessageWithAttribution(stringResource(Res.string.web_time_series_unsupported))
      else -> {
        PlotSummaryRow(plot = plot, statistics = uiState.statistics, uiState = uiState, actions)
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
          SeriesContent(uiState, actions, Modifier.fillMaxSize())
        }
        SelectionFooter(uiState)
      }
    }
  }
}

@Composable
private fun PlotSummaryRow(
  plot: TimeSeriesPlot,
  statistics: Loadable<PlotStatistics>,
  uiState: TimeSeriesUiState,
  actions: TimeSeriesActions,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
      text = plot.label,
      style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.widthIn(max = 220.dp),
    )
    val facts = buildList {
      if (plot.geometryType == GeometryType.POLYGON) {
        add(stringResource(Res.string.web_time_series_area, formatHectares(plot.areaHectares)))
      }
      val loaded = (statistics as? Loadable.Loaded)?.value
      if (loaded != null) {
        elevationText(loaded)?.let(::add)
        populationText(loaded)?.let(::add)
      }
    }
    Text(
      text = facts.joinToString("  ·  "),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
    if (statistics == Loadable.Loading) {
      CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
    }
    SeriesPickers(uiState, actions)
  }
}

/** The imagery source and band menus; the band's color is also its chart's. */
@Composable
private fun SeriesPickers(uiState: TimeSeriesUiState, actions: TimeSeriesActions) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    TimeSeriesPicker(
      title = stringResource(Res.string.web_time_series_source_picker),
      options =
        TimeSeriesUiState.SOURCES.map { source ->
          val style = TimeSeriesStyles.source(source)
          PickerOption(
            value = source,
            label = stringResource(style.name),
            detail = stringResource(style.detail),
            icon = style.icon,
            color = style.color.current(),
          )
        },
      selected = uiState.source,
      onSelect = actions::selectSource,
    )
    var previousGroup: BandGroup? = null
    TimeSeriesPicker(
      title = stringResource(Res.string.web_time_series_band_picker),
      options =
        uiState.bands.map { band ->
          val style = TimeSeriesStyles.band(band)
          val heading =
            if (style.group != previousGroup) stringResource(style.group.heading) else null
          previousGroup = style.group
          PickerOption(
            value = band,
            label = bandLabel(band),
            detail = style.detail?.let { stringResource(it) }.orEmpty(),
            icon = style.icon,
            color = style.color.current(),
            sectionHeading = heading,
          )
        },
      selected = uiState.band,
      onSelect = actions::selectBand,
    )
  }
}

@Composable
private fun elevationText(statistics: PlotStatistics): String? {
  val min = statistics.elevationMinMeters ?: return null
  val max = statistics.elevationMaxMeters ?: min
  val minText = formatFixed(min, 0)
  val maxText = formatFixed(max, 0)
  return if (minText == maxText) {
    stringResource(Res.string.web_time_series_elevation, minText)
  } else {
    stringResource(Res.string.web_time_series_elevation_range, minText, maxText)
  }
}

@Composable
private fun populationText(statistics: PlotStatistics): String? {
  val density = statistics.populationDensityPerKm2 ?: return null
  val densityText = SurveyAreaGeometry.groupThousands(kotlin.math.round(density).toLong())
  val population = statistics.population
  return if (population == null) {
    stringResource(
      Res.string.web_time_series_population_density,
      densityText,
      statistics.populationYear,
    )
  } else {
    stringResource(
      Res.string.web_time_series_population,
      SurveyAreaGeometry.groupThousands(kotlin.math.round(population).toLong()),
      densityText,
      statistics.populationYear,
    )
  }
}

@Composable
private fun SeriesContent(
  uiState: TimeSeriesUiState,
  actions: TimeSeriesActions,
  modifier: Modifier = Modifier,
) {
  when (val series = uiState.series) {
    Loadable.Idle,
    Loadable.Loading ->
      Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
      ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
          text = stringResource(Res.string.web_time_series_loading),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    is Loadable.Failed -> FailureMessage(series, actions, modifier)
    is Loadable.Loaded -> {
      val values = series.value.values(uiState.band)
      val count = series.value.validCount(uiState.band)
      val label = bandLabel(uiState.band)
      if (count == 0) {
        PanelMessage(stringResource(Res.string.web_time_series_no_observations, label), modifier)
      } else {
        TimeSeriesChart(
          timesMs = series.value.timesMs,
          values = values,
          selectedIndex = uiState.selectedIndex,
          onSelect = actions::selectObservation,
          onStep = actions::stepObservation,
          onClear = actions::clearPinnedScene,
          color = TimeSeriesStyles.band(uiState.band).color.current(),
          contentDescription =
            stringResource(Res.string.web_time_series_chart_description, label, count),
          modifier = modifier.padding(vertical = 4.dp),
        )
      }
    }
  }
}

@Composable
private fun SelectionFooter(uiState: TimeSeriesUiState) {
  val series = uiState.loadedSeries
  val selected = uiState.selectedObservation
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
      text =
        if (selected != null) {
          stringResource(
            Res.string.web_time_series_selected,
            selected.date,
            selected.platformLabel,
            bandLabel(uiState.band),
            formatFixed(selected.value, 3),
          )
        } else if (series != null) {
          stringResource(Res.string.web_time_series_chart_hint)
        } else {
          ""
        },
      style = MaterialTheme.typography.bodySmall,
      color =
        if (selected != null) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1.2f),
    )
    if (series != null) {
      Text(
        text =
          stringResource(
            Res.string.web_time_series_observations,
            series.validCount(uiState.band),
            series.windowLabel,
          ) + "  ·  " + series.attributions.joinToString(" · "),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.End,
        modifier = Modifier.weight(1f),
      )
    }
    PoweredByEarthEngineBadge(modifier = Modifier.testTag(EARTH_ENGINE_PANEL_BADGE_TAG))
  }
}

/**
 * Floating banner over the map while a historical scene is shown: its satellite and date, the band
 * combination, and a button to remove it. When the map is too narrow for the whole banner, the
 * scene and band combinations scroll sideways between the pin and the close button, which stay in
 * view.
 */
@Composable
fun PinnedSceneBanner(
  pinnedScene: PinnedScene,
  actions: TimeSeriesActions,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shadowElevation = 4.dp,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
  ) {
    Row(
      modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = Icons.Outlined.PushPin,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(18.dp),
      )
      Row(
        modifier = Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = "${pinnedScene.observation.platformLabel} · ${pinnedScene.observation.date}",
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        )
        when (val tiles = pinnedScene.tiles) {
          Loadable.Loading,
          Loadable.Idle -> {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            Text(
              text = stringResource(Res.string.web_time_series_scene_loading),
              style = MaterialTheme.typography.bodySmall,
            )
          }
          is Loadable.Failed ->
            Text(
              text = stringResource(Res.string.web_time_series_scene_failed),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
          is Loadable.Loaded -> Unit
        }
        if (pinnedScene.stretches.size > 1) {
          pinnedScene.stretches.forEach { stretch ->
            FlatFilterChip(
              selected = stretch == pinnedScene.stretch,
              onClick = { actions.selectStretch(stretch) },
              label = { Text(stringResource(stretchLabel(stretch))) },
            )
          }
        }
      }
      IconButton(onClick = actions::clearPinnedScene, modifier = Modifier.size(32.dp)) {
        Icon(
          imageVector = Icons.Outlined.Close,
          contentDescription = stringResource(Res.string.web_time_series_scene_clear),
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }
}

@Composable
private fun FailureMessage(
  failure: Loadable.Failed,
  actions: TimeSeriesActions,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    val message = stringResource(errorMessage(failure.reason))
    Text(
      text =
        if (failure.reason == TimeSeriesError.UNSUPPORTED_PLOT && failure.detail != null) {
          "$message ${failure.detail}"
        } else {
          message
        },
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    if (
      failure.reason != TimeSeriesError.DENIED && failure.reason != TimeSeriesError.UNSUPPORTED_PLOT
    ) {
      TextButton(onClick = actions::retry) {
        Text(stringResource(Res.string.web_time_series_retry))
      }
    }
  }
}

@Composable
private fun PanelMessageWithAttribution(text: String) {
  Column(modifier = Modifier.fillMaxSize()) {
    PanelMessage(text = text, modifier = Modifier.fillMaxWidth().weight(1f))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
      PoweredByEarthEngineBadge(modifier = Modifier.testTag(EARTH_ENGINE_PANEL_BADGE_TAG))
    }
  }
}

@Composable
private fun PanelMessage(text: String, modifier: Modifier = Modifier) {
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
  }
}

private fun formatHectares(hectares: Double): String =
  if (hectares < 10) formatFixed(hectares, 2)
  else SurveyAreaGeometry.groupThousands(kotlin.math.round(hectares).toLong())

private fun stretchLabel(stretch: SceneStretch): StringResource =
  when (stretch) {
    SceneStretch.SWIR_NIR_RED -> Res.string.web_time_series_stretch_swir_nir_red
    SceneStretch.NIR_SWIR_RED -> Res.string.web_time_series_stretch_nir_swir_red
    SceneStretch.SAR_VV_VH_RATIO -> Res.string.web_time_series_stretch_sar
    else -> Res.string.web_time_series_stretch_true_color
  }

private fun errorMessage(reason: TimeSeriesError): StringResource =
  when (reason) {
    TimeSeriesError.NOT_UPLOADED -> Res.string.web_time_series_error_not_uploaded
    TimeSeriesError.UNSUPPORTED_PLOT -> Res.string.web_time_series_error_unsupported
    TimeSeriesError.BUSY -> Res.string.web_time_series_error_busy
    TimeSeriesError.TIMEOUT -> Res.string.web_time_series_error_timeout
    TimeSeriesError.UNAVAILABLE -> Res.string.web_time_series_error_unavailable
    TimeSeriesError.DENIED -> Res.string.web_time_series_error_denied
    TimeSeriesError.OTHER -> Res.string.web_time_series_error_other
  }
