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
package org.openforis.collectone.v2.app.ui.datacollection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Forest
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.max
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.WhispAnalysis
import org.openforis.collectone.v2.app.domain.model.WhispBeforeAfterComparison
import org.openforis.collectone.v2.app.domain.model.WhispIndicator
import org.openforis.collectone.v2.app.domain.model.WhispMetric
import org.openforis.collectone.v2.app.domain.model.WhispPlotContext
import org.openforis.collectone.v2.app.domain.model.WhispRiskLevel
import org.openforis.collectone.v2.app.domain.model.WhispRiskVerdict
import org.openforis.collectone.v2.app.domain.model.WhispYearlyCategory
import org.openforis.collectone.v2.app.domain.model.WhispYearlySeries
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_risk_after_2020
import org.openforis.collectone.v2.app.resources.app_risk_all_fields_toggle
import org.openforis.collectone.v2.app.resources.app_risk_analysis_whisp_intro
import org.openforis.collectone.v2.app.resources.app_risk_before_2020
import org.openforis.collectone.v2.app.resources.app_risk_before_after_heading
import org.openforis.collectone.v2.app.resources.app_risk_commodities_heading
import org.openforis.collectone.v2.app.resources.app_risk_context_area
import org.openforis.collectone.v2.app.resources.app_risk_context_country
import org.openforis.collectone.v2.app.resources.app_risk_context_heading
import org.openforis.collectone.v2.app.resources.app_risk_context_region
import org.openforis.collectone.v2.app.resources.app_risk_context_waterbody
import org.openforis.collectone.v2.app.resources.app_risk_context_waterbody_no
import org.openforis.collectone.v2.app.resources.app_risk_context_waterbody_yes
import org.openforis.collectone.v2.app.resources.app_risk_indicators_heading
import org.openforis.collectone.v2.app.resources.app_risk_level_high
import org.openforis.collectone.v2.app.resources.app_risk_level_low
import org.openforis.collectone.v2.app.resources.app_risk_level_more_info
import org.openforis.collectone.v2.app.resources.app_risk_level_unknown
import org.openforis.collectone.v2.app.resources.app_risk_no_nonzero_layers
import org.openforis.collectone.v2.app.resources.app_risk_other_metrics_heading
import org.openforis.collectone.v2.app.resources.app_risk_treecover_heading
import org.openforis.collectone.v2.app.resources.app_risk_verdicts_heading
import org.openforis.collectone.v2.app.resources.app_risk_yearly_chart_cutoff_label
import org.openforis.collectone.v2.app.resources.app_risk_yearly_chart_heading
import org.openforis.collectone.v2.app.resources.app_risk_yearly_chart_no_activity
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.clickable

private val RiskHighColor = Color(0xFFC62828)
private val RiskMoreInfoColor = Color(0xFFD97706)
private val RiskLowColor = Color(0xFF2E7D32)

/** Distinct, colorblind-conscious palette for overlaid Whisp yearly series. */
private val WhispChartPalette =
  listOf(
    Color(0xFFD32F2F), // Forest loss red
    Color(0xFF7B1FA2), // Deforestation purple
    Color(0xFFEF6C00), // Degradation / fire orange
    Color(0xFF00897B), // Radar alerts teal
    Color(0xFF1976D2), // Optical alerts blue
    Color(0xFFE64A19), // Fire vermilion
    Color(0xFF3949AB), // Indigo
    Color(0xFF546E7A), // Slate
  )

/** Localized label for a [WhispRiskLevel]. */
@Composable
fun WhispRiskLevel.displayLabel(): String =
  when (this) {
    WhispRiskLevel.HIGH -> stringResource(Res.string.app_risk_level_high)
    WhispRiskLevel.MORE_INFO_NEEDED -> stringResource(Res.string.app_risk_level_more_info)
    WhispRiskLevel.LOW -> stringResource(Res.string.app_risk_level_low)
    WhispRiskLevel.UNKNOWN -> stringResource(Res.string.app_risk_level_unknown)
  }

/** Semantic fill color for a [WhispRiskLevel] badge. */
@Composable
fun WhispRiskLevel.badgeColor(): Color =
  when (this) {
    WhispRiskLevel.HIGH -> RiskHighColor
    WhispRiskLevel.MORE_INFO_NEEDED -> RiskMoreInfoColor
    WhispRiskLevel.LOW -> RiskLowColor
    WhispRiskLevel.UNKNOWN -> MaterialTheme.colorScheme.surfaceContainerHighest
  }

/** Readable text color on top of [WhispRiskLevel.badgeColor]. */
@Composable
fun WhispRiskLevel.badgeContentColor(): Color =
  when (this) {
    WhispRiskLevel.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> Color.White
  }

/**
 * Compact color-coded badge showing a Whisp risk level (reused in the Risk analysis tab and the web
 * dashboard's rolled-up data table column).
 */
@Composable
fun WhispRiskBadge(
  level: WhispRiskLevel,
  label: String = level.displayLabel(),
  compact: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val textStyle =
    if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium
  Surface(
    modifier = modifier,
    shape = if (compact) MaterialTheme.shapes.extraSmall else MaterialTheme.shapes.small,
    color = level.badgeColor(),
    contentColor = level.badgeContentColor(),
  ) {
    Text(
      text = label,
      style = textStyle.copy(fontWeight = FontWeight.SemiBold),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier =
        Modifier.padding(
          horizontal = if (compact) 6.dp else 10.dp,
          vertical = if (compact) 2.dp else 4.dp,
        ),
    )
  }
}

/**
 * Dedicated Risk analysis tab body for an entity enriched by Open Foris Whisp: risk verdicts, plot
 * context, multi-series overlaid annual disturbance & loss chart, before/after 2020 comparison, key
 * indicators, tree cover & commodity breakdowns, and an expandable raw-field list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WhispRiskAnalysisPane(analysis: WhispAnalysis, modifier: Modifier = Modifier) {
  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    WhispAttributionBanner()

    // 1. Risk verdicts
    if (analysis.riskVerdicts.isNotEmpty()) {
      RiskSectionHeading(stringResource(Res.string.app_risk_verdicts_heading))
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        analysis.riskVerdicts.forEach { verdict -> WhispRiskVerdictCard(verdict) }
      }
    }

    // 2. Plot context
    if (!analysis.context.isEmpty) {
      RiskSectionHeading(stringResource(Res.string.app_risk_context_heading))
      WhispPlotContextGrid(analysis.context)
    }

    // 3. Yearly disturbance & loss overlaid chart
    if (analysis.yearlySeries.isNotEmpty()) {
      RiskSectionHeading(stringResource(Res.string.app_risk_yearly_chart_heading))
      WhispYearlyDisturbanceChart(analysis = analysis)
    }

    // 4. Before vs. After 2020 paired summaries
    val activeComparisons = analysis.beforeAfterComparisons.filter { it.hasNonZeroValues }
    if (activeComparisons.isNotEmpty()) {
      RiskSectionHeading(stringResource(Res.string.app_risk_before_after_heading))
      WhispBeforeAfterSection(comparisons = activeComparisons, analysis = analysis)
    }

    // 5. Decision-tree indicators (Ind_*)
    if (analysis.indicators.isNotEmpty()) {
      RiskSectionHeading(stringResource(Res.string.app_risk_indicators_heading))
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        analysis.indicators.forEach { indicator -> WhispIndicatorRow(indicator) }
      }
    }

    // 6. Baseline Tree Cover (2020)
    if (analysis.treeCoverMetrics.isNotEmpty()) {
      RiskSectionHeading(stringResource(Res.string.app_risk_treecover_heading))
      WhispMetricBarsSection(metrics = analysis.treeCoverMetrics, analysis = analysis)
    }

    // 7. Commodities & Land Use
    if (analysis.commodityMetrics.isNotEmpty()) {
      RiskSectionHeading(stringResource(Res.string.app_risk_commodities_heading))
      WhispMetricBarsSection(metrics = analysis.commodityMetrics, analysis = analysis)
    }

    // 8. Other metrics (if any non-zero or non-numeric)
    val visibleOther = analysis.otherMetrics.filter { (it.numericValue ?: 1.0) > 0.0 }
    if (visibleOther.isNotEmpty()) {
      RiskSectionHeading(stringResource(Res.string.app_risk_other_metrics_heading))
      WhispMetricBarsSection(metrics = visibleOther, analysis = analysis)
    }

    // 9. Expandable raw Whisp fields disclosure
    WhispRawFieldsDisclosure(analysis)
  }
}

@Composable
private fun WhispAttributionBanner() {
  Surface(
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = Icons.Outlined.Forest,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(18.dp),
      )
      Text(
        text = stringResource(Res.string.app_risk_analysis_whisp_intro),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun WhispRawFieldsDisclosure(analysis: WhispAnalysis) {
  var showAllFields by remember(analysis) { mutableStateOf(false) }

  HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clip(MaterialTheme.shapes.small)
        .clickable { showAllFields = !showAllFields }
        .padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = stringResource(Res.string.app_risk_all_fields_toggle, analysis.rawEntries.size),
      style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
      color = MaterialTheme.colorScheme.primary,
    )
    Icon(
      imageVector = if (showAllFields) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(18.dp),
    )
  }
  AnimatedVisibility(visible = showAllFields) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      analysis.rawEntries.forEach { (key, value) ->
        Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.Top,
        ) {
          Text(
            text = WhispAnalysis.humanizeWhispKey(key),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
          )
          Text(
            text = value.ifBlank { "—" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    }
  }
}

@Composable
private fun RiskSectionHeading(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(top = 2.dp),
  )
}

@Composable
private fun WhispRiskVerdictCard(verdict: WhispRiskVerdict) {
  Surface(
    shape = MaterialTheme.shapes.small,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    modifier =
      Modifier.border(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
        shape = MaterialTheme.shapes.small,
      ),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        text = verdict.label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      WhispRiskBadge(
        level = verdict.level,
        label =
          if (verdict.level == WhispRiskLevel.UNKNOWN && verdict.rawValue.isNotBlank()) {
            verdict.rawValue
          } else {
            verdict.level.displayLabel()
          },
        compact = true,
      )
    }
  }
}

@Composable
private fun WhispPlotContextGrid(context: WhispPlotContext) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    context.country?.let { country ->
      val display =
        if (context.producerCountry != null && context.producerCountry != country) {
          "$country (${context.producerCountry})"
        } else {
          country
        }
      RiskKeyValueRow(stringResource(Res.string.app_risk_context_country), display)
    }
    context.adminLevel1?.let {
      RiskKeyValueRow(stringResource(Res.string.app_risk_context_region), it)
    }
    context.areaHa?.let {
      RiskKeyValueRow(stringResource(Res.string.app_risk_context_area), "$it ha")
    }
    context.inWaterbody?.let { inWater ->
      Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(Res.string.app_risk_context_waterbody),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          if (inWater) {
            Icon(
              imageVector = Icons.Outlined.WarningAmber,
              contentDescription = null,
              tint = RiskMoreInfoColor,
              modifier = Modifier.size(16.dp),
            )
          }
          Text(
            text =
              stringResource(
                if (inWater) Res.string.app_risk_context_waterbody_yes
                else Res.string.app_risk_context_waterbody_no
              ),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (inWater) FontWeight.SemiBold else FontWeight.Normal,
            color = if (inWater) RiskMoreInfoColor else MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    }
  }
}

@Composable
private fun RiskKeyValueRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

/**
 * Multi-series overlaid annual disturbance, loss, and fire chart with a vertical 2020 EUDR cutoff
 * reference line and interactive legend chips to toggle individual categories.
 */
@Composable
private fun WhispYearlyDisturbanceChart(analysis: WhispAnalysis) {
  val allSeries = analysis.chartSeries
  if (allSeries.isEmpty()) return

  if (!analysis.hasYearlyActivity) {
    WhispNoYearlyActivityNotice()
    return
  }

  var hiddenKeys by remember(analysis) { mutableStateOf(emptySet<String>()) }
  val visibleSeries = allSeries.filter { it.key !in hiddenKeys }.ifEmpty { allSeries }
  val seriesColors =
    remember(allSeries) {
      allSeries.mapIndexed { idx, s -> s.key to colorForSeries(s, idx) }.toMap()
    }
  val allYears =
    remember(allSeries) {
      allSeries.flatMap { s -> s.points.map { it.first } }.distinct().sorted()
    }
  val minYear = allYears.first()
  val maxYear = allYears.last()
  val yMax = niceAxisMax(visibleSeries.maxOfOrNull { it.maxValue } ?: 1.0, analysis.isPercentUnit)
  val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
  val axisLabelColor = MaterialTheme.colorScheme.onSurfaceVariant

  Surface(
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(modifier = Modifier.fillMaxWidth().height(144.dp)) {
        WhispChartYAxis(analysis = analysis, yMax = yMax, axisLabelColor = axisLabelColor)
        WhispChartCanvas(
          visibleSeries = visibleSeries,
          seriesColors = seriesColors,
          minYear = minYear,
          maxYear = maxYear,
          yMax = yMax,
          gridColor = gridColor,
          cutoffColor = RiskMoreInfoColor,
          modifier = Modifier.weight(1f).fillMaxHeight(),
        )
      }
      WhispChartXAxis(
        minYear = minYear,
        maxYear = maxYear,
        axisLabelColor = axisLabelColor,
        cutoffColor = RiskMoreInfoColor,
      )
      WhispChartLegend(
        allSeries = allSeries,
        seriesColors = seriesColors,
        hiddenKeys = hiddenKeys,
        onToggleSeries = { key ->
          hiddenKeys = toggleHiddenSeriesKey(hiddenKeys, key, totalSeriesCount = allSeries.size)
        },
      )
    }
  }
}

private fun toggleHiddenSeriesKey(
  hiddenKeys: Set<String>,
  key: String,
  totalSeriesCount: Int,
): Set<String> =
  if (key !in hiddenKeys) {
    if (totalSeriesCount - hiddenKeys.size > 1) hiddenKeys + key else hiddenKeys
  } else {
    hiddenKeys - key
  }

@Composable
private fun WhispNoYearlyActivityNotice() {
  Surface(
    shape = MaterialTheme.shapes.small,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Text(
      text = stringResource(Res.string.app_risk_yearly_chart_no_activity),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.fillMaxWidth().padding(12.dp),
    )
  }
}

@Composable
private fun WhispChartYAxis(analysis: WhispAnalysis, yMax: Double, axisLabelColor: Color) {
  Column(
    modifier = Modifier.width(38.dp).fillMaxHeight().padding(end = 4.dp),
    verticalArrangement = Arrangement.SpaceBetween,
    horizontalAlignment = Alignment.End,
  ) {
    listOf(yMax, yMax / 2.0, 0.0).forEach { tick ->
      Text(
        text = analysis.formatMetricValue(tick),
        style = MaterialTheme.typography.labelSmall,
        color = axisLabelColor,
        maxLines = 1,
      )
    }
  }
}

@Composable
private fun WhispChartCanvas(
  visibleSeries: List<WhispYearlySeries>,
  seriesColors: Map<String, Color>,
  minYear: Int,
  maxYear: Int,
  yMax: Double,
  gridColor: Color,
  cutoffColor: Color,
  modifier: Modifier = Modifier,
) {
  val yearSpan = max(1, maxYear - minYear)
  Canvas(modifier = modifier) {
    val padTop = 6.dp.toPx()
    val padBottom = 6.dp.toPx()
    val padH = 4.dp.toPx()
    val plotW = (size.width - padH * 2f).coerceAtLeast(1f)
    val plotH = (size.height - padTop - padBottom).coerceAtLeast(1f)

    fun xForYear(year: Int): Float = padH + ((year - minYear).toFloat() / yearSpan) * plotW
    fun yForVal(value: Double): Float =
      padTop + plotH * (1f - (value / yMax).toFloat().coerceIn(0f, 1f))

    drawYearlyChartGrid(plotW, plotH, padH, padTop, gridColor)

    if (2020 in minYear..maxYear) {
      val cutoffX = xForYear(2020)
      drawLine(
        color = cutoffColor,
        start = Offset(cutoffX, padTop),
        end = Offset(cutoffX, padTop + plotH),
        strokeWidth = 1.25.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f),
      )
    }

    val baseY = padTop + plotH
    visibleSeries.forEach { series ->
      val color = seriesColors[series.key] ?: WhispChartPalette.first()
      drawYearlySeries(series, color, baseY, ::xForYear, ::yForVal)
    }
  }
}

private fun DrawScope.drawYearlyChartGrid(
  plotW: Float,
  plotH: Float,
  padH: Float,
  padTop: Float,
  gridColor: Color,
) {
  listOf(0f, 0.5f, 1f).forEach { frac ->
    val gy = padTop + plotH * frac
    drawLine(
      color = gridColor,
      start = Offset(padH, gy),
      end = Offset(padH + plotW, gy),
      strokeWidth = 1.dp.toPx(),
    )
  }
}

private fun DrawScope.drawYearlySeries(
  series: WhispYearlySeries,
  color: Color,
  baseY: Float,
  xForYear: (Int) -> Float,
  yForVal: (Double) -> Float,
) {
  if (series.points.isEmpty()) return
  val strokePath = Path()
  val fillPath = Path()
  series.points.forEachIndexed { idx, (year, value) ->
    val px = xForYear(year)
    val py = yForVal(value)
    if (idx == 0) {
      strokePath.moveTo(px, py)
      fillPath.moveTo(px, baseY)
      fillPath.lineTo(px, py)
    } else {
      strokePath.lineTo(px, py)
      fillPath.lineTo(px, py)
    }
    if (idx == series.points.lastIndex) {
      fillPath.lineTo(px, baseY)
      fillPath.close()
    }
  }
  drawPath(path = fillPath, color = color.copy(alpha = 0.12f))
  drawPath(
    path = strokePath,
    color = color,
    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
  )
  series.points.forEach { (year, value) ->
    if (value > 0.0) {
      drawCircle(
        color = color,
        radius = 3.dp.toPx(),
        center = Offset(xForYear(year), yForVal(value)),
      )
    }
  }
}

@Composable
private fun WhispChartXAxis(
  minYear: Int,
  maxYear: Int,
  axisLabelColor: Color,
  cutoffColor: Color,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(start = 38.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = minYear.toString(),
      style = MaterialTheme.typography.labelSmall,
      color = axisLabelColor,
    )
    if (2020 in (minYear + 1) until maxYear) {
      Text(
        text = stringResource(Res.string.app_risk_yearly_chart_cutoff_label),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = cutoffColor,
      )
    }
    Text(
      text = maxYear.toString(),
      style = MaterialTheme.typography.labelSmall,
      color = axisLabelColor,
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhispChartLegend(
  allSeries: List<WhispYearlySeries>,
  seriesColors: Map<String, Color>,
  hiddenKeys: Set<String>,
  onToggleSeries: (String) -> Unit,
) {
  FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    allSeries.forEach { series ->
      val color = seriesColors[series.key] ?: WhispChartPalette.first()
      WhispChartLegendChip(
        label = series.label,
        color = color,
        isEnabled = series.key !in hiddenKeys,
        onClick = { onToggleSeries(series.key) },
      )
    }
  }
}

@Composable
private fun WhispChartLegendChip(
  label: String,
  color: Color,
  isEnabled: Boolean,
  onClick: () -> Unit,
) {
  Surface(
    shape = MaterialTheme.shapes.extraSmall,
    color =
      if (isEnabled) color.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainer,
    modifier = Modifier.clip(MaterialTheme.shapes.extraSmall).clickable(onClick = onClick),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Box(
        modifier =
          Modifier.size(8.dp)
            .background(
              if (isEnabled) color else MaterialTheme.colorScheme.outline,
              CircleShape,
            )
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color =
          if (isEnabled) MaterialTheme.colorScheme.onSurface
          else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun WhispBeforeAfterSection(
  comparisons: List<WhispBeforeAfterComparison>,
  analysis: WhispAnalysis,
) {
  val maxVal =
    if (analysis.isPercentUnit) 100.0
    else
      comparisons.maxOfOrNull { max(it.before2020 ?: 0.0, it.after2020 ?: 0.0) }?.coerceAtLeast(1.0)
        ?: 1.0

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    comparisons.forEach { item ->
      Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
          text = item.label,
          style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
          color = MaterialTheme.colorScheme.onSurface,
        )
        item.before2020?.let { before ->
          ProportionBarRow(
            label = stringResource(Res.string.app_risk_before_2020),
            valueText = analysis.formatMetricValue(before),
            fraction = (before / maxVal).toFloat().coerceIn(0f, 1f),
            barColor = MaterialTheme.colorScheme.secondary,
          )
        }
        item.after2020?.let { after ->
          ProportionBarRow(
            label = stringResource(Res.string.app_risk_after_2020),
            valueText = analysis.formatMetricValue(after),
            fraction = (after / maxVal).toFloat().coerceIn(0f, 1f),
            barColor = if (after > 0.0) RiskHighColor else MaterialTheme.colorScheme.outlineVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun WhispIndicatorRow(indicator: WhispIndicator) {
  val chipBg =
    when (indicator.isFlagged) {
      true -> RiskMoreInfoColor.copy(alpha = 0.16f)
      false -> RiskLowColor.copy(alpha = 0.14f)
      null -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
  val chipFg =
    when (indicator.isFlagged) {
      true -> RiskMoreInfoColor
      false -> RiskLowColor
      null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = indicator.label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.weight(1f).padding(end = 8.dp),
    )
    Surface(shape = MaterialTheme.shapes.extraSmall, color = chipBg, contentColor = chipFg) {
      Text(
        text = indicator.rawValue.replaceFirstChar { it.uppercaseChar() },
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
      )
    }
  }
}

@Composable
private fun WhispMetricBarsSection(metrics: List<WhispMetric>, analysis: WhispAnalysis) {
  val active = metrics.filter { (it.numericValue ?: 1.0) > 0.0 }
  if (active.isEmpty()) {
    Text(
      text = stringResource(Res.string.app_risk_no_nonzero_layers),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    return
  }
  val maxVal =
    if (analysis.isPercentUnit) 100.0
    else active.mapNotNull { it.numericValue }.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0

  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    active.forEach { metric ->
      val num = metric.numericValue
      if (num != null) {
        ProportionBarRow(
          label = metric.label,
          valueText = analysis.formatMetricValue(num),
          fraction = (num / maxVal).toFloat().coerceIn(0f, 1f),
          barColor = MaterialTheme.colorScheme.primary,
        )
      } else {
        RiskKeyValueRow(metric.label, metric.rawValue)
      }
    }
  }
}

@Composable
private fun ProportionBarRow(
  label: String,
  valueText: String,
  fraction: Float,
  barColor: Color,
) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f).padding(end = 8.dp),
      )
      Text(
        text = valueText,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .height(6.dp)
          .clip(MaterialTheme.shapes.extraSmall)
          .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
      if (fraction > 0f) {
        Box(
          modifier =
            Modifier.fillMaxHeight()
              .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
              .clip(MaterialTheme.shapes.extraSmall)
              .background(barColor)
        )
      }
    }
  }
}

internal fun colorForSeries(series: WhispYearlySeries, index: Int): Color =
  when (series.category) {
    WhispYearlyCategory.FOREST_LOSS ->
      if (index % 2 == 0) WhispChartPalette[0] else WhispChartPalette[1]
    WhispYearlyCategory.DEGRADATION_AND_ALERTS -> WhispChartPalette[2 + (index % 3)]
    WhispYearlyCategory.FIRE -> WhispChartPalette[5]
    WhispYearlyCategory.OTHER -> WhispChartPalette[index % WhispChartPalette.size]
  }

internal fun niceAxisMax(rawMax: Double, isPercent: Boolean): Double {
  if (rawMax <= 0.0) return if (isPercent) 10.0 else 1.0
  val target =
    when {
      rawMax <= 1.0 -> 1.0
      rawMax <= 5.0 -> ceil(rawMax)
      rawMax <= 20.0 -> ceil(rawMax / 5.0) * 5.0
      rawMax <= 50.0 -> ceil(rawMax / 10.0) * 10.0
      else -> ceil(rawMax / 25.0) * 25.0
    }
  return if (isPercent) target.coerceAtMost(100.0) else target
}
