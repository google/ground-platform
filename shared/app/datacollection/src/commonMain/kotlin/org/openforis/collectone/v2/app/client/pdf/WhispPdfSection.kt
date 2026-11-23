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
package org.openforis.collectone.v2.app.client.pdf

import kotlin.math.ceil
import kotlin.math.max
import org.openforis.collectone.v2.app.domain.model.WhispAnalysis
import org.openforis.collectone.v2.app.domain.model.WhispBeforeAfterComparison
import org.openforis.collectone.v2.app.domain.model.WhispIndicator
import org.openforis.collectone.v2.app.domain.model.WhispMetric
import org.openforis.collectone.v2.app.domain.model.WhispPlotContext
import org.openforis.collectone.v2.app.domain.model.WhispRiskLevel
import org.openforis.collectone.v2.app.domain.model.WhispRiskVerdict
import org.openforis.collectone.v2.app.domain.model.WhispYearlyCategory
import org.openforis.collectone.v2.app.domain.model.WhispYearlySeries

private val PdfRiskHigh = PdfColor.fromArgb(0xFFC62828)
private val PdfRiskMoreInfo = PdfColor.fromArgb(0xFFD97706)
private val PdfRiskLow = PdfColor.fromArgb(0xFF2E7D32)

private val PdfWhispChartPalette =
  listOf(
    PdfColor.fromArgb(0xFFD32F2F),
    PdfColor.fromArgb(0xFF7B1FA2),
    PdfColor.fromArgb(0xFFEF6C00),
    PdfColor.fromArgb(0xFF00897B),
    PdfColor.fromArgb(0xFF1976D2),
    PdfColor.fromArgb(0xFFE64A19),
    PdfColor.fromArgb(0xFF3949AB),
    PdfColor.fromArgb(0xFF546E7A),
  )

/** Renders the Open Foris Whisp risk analysis section (verdicts, charts, and metrics) in a PDF. */
internal fun PdfReportLayout.whispRiskAnalysisSection(
  analysis: WhispAnalysis,
  strings: PdfReportStrings,
) {
  sectionHeading(strings.riskAnalysis)
  paragraph(strings.riskIntro, size = 9f, color = PdfPalette.onSurfaceVariant)
  spacer(4f)

  if (analysis.riskVerdicts.isNotEmpty()) {
    whispRiskVerdictChips(analysis.riskVerdicts, strings)
  }

  whispContextSection(analysis.context)
  whispYearlySection(analysis, strings)
  whispActiveComparisonsSection(analysis, strings)
  whispIndicatorsSection(analysis.indicators, strings.riskIndicators)
  whispActiveMetricsSection(analysis.treeCoverMetrics, strings.riskTreecover, analysis)
  whispActiveMetricsSection(analysis.commodityMetrics, strings.riskCommodities, analysis)
}

private fun PdfReportLayout.whispContextSection(ctx: WhispPlotContext) {
  if (ctx.isEmpty) return
  ctx.country?.let { country ->
    val display =
      if (ctx.producerCountry != null && ctx.producerCountry != country) {
        "$country (${ctx.producerCountry})"
      } else {
        country
      }
    field("Country", display)
  }
  ctx.adminLevel1?.let { field("Admin region", it) }
  ctx.areaHa?.let { field("Plot area", "$it ha") }
  ctx.inWaterbody?.let { field("In waterbody", if (it) "Yes" else "No") }
}

private fun PdfReportLayout.whispYearlySection(analysis: WhispAnalysis, strings: PdfReportStrings) {
  if (analysis.yearlySeries.isEmpty()) return
  groupLabel(strings.riskYearlyChart)
  if (analysis.hasYearlyActivity) {
    whispYearlyVectorChart(analysis, strings)
  } else {
    paragraph(strings.riskYearlyNoActivity, size = 9f, color = PdfPalette.onSurfaceVariant)
  }
}

private fun PdfReportLayout.whispActiveComparisonsSection(
  analysis: WhispAnalysis,
  strings: PdfReportStrings,
) {
  val activeComparisons = analysis.beforeAfterComparisons.filter { it.hasNonZeroValues }
  if (activeComparisons.isEmpty()) return
  groupLabel(strings.riskBeforeAfter)
  whispBeforeAfterBars(activeComparisons, analysis)
}

private fun PdfReportLayout.whispIndicatorsSection(
  indicators: List<WhispIndicator>,
  heading: String,
) {
  if (indicators.isEmpty()) return
  groupLabel(heading)
  indicators.forEach { indicator ->
    field(indicator.label, indicator.rawValue.replaceFirstChar { it.uppercaseChar() })
  }
}

private fun PdfReportLayout.whispActiveMetricsSection(
  metrics: List<WhispMetric>,
  heading: String,
  analysis: WhispAnalysis,
) {
  val active = metrics.filter { (it.numericValue ?: 1.0) > 0.0 }
  if (active.isEmpty()) return
  groupLabel(heading)
  whispMetricBars(active, analysis)
}

private fun PdfReportLayout.whispRiskVerdictChips(
  verdicts: List<WhispRiskVerdict>,
  strings: PdfReportStrings,
) {
  val chipHeight = 18f
  val gap = 6f
  ensureSpace(chipHeight + 10f)
  var cursorX = left
  verdicts.forEach { verdict ->
    val levelText =
      when (verdict.level) {
        WhispRiskLevel.HIGH -> strings.riskHigh
        WhispRiskLevel.MORE_INFO_NEEDED -> strings.riskMoreInfo
        WhispRiskLevel.LOW -> strings.riskLow
        WhispRiskLevel.UNKNOWN -> verdict.rawValue.ifBlank { strings.riskUnknown }
      }
    val text = "${verdict.label}: $levelText"
    val width = PdfFont.BOLD.measure(text, 8.5f) + 14f
    if (cursorX > left && cursorX + width > right) {
      spacer(chipHeight + 6f)
      ensureSpace(chipHeight + 6f)
      cursorX = left
    }
    val fill =
      when (verdict.level) {
        WhispRiskLevel.HIGH -> PdfRiskHigh
        WhispRiskLevel.MORE_INFO_NEEDED -> PdfRiskMoreInfo
        WhispRiskLevel.LOW -> PdfRiskLow
        WhispRiskLevel.UNKNOWN -> PdfPalette.surfaceContainer
      }
    val fg = if (fill.isDark) PdfColor.WHITE else PdfPalette.onSurface
    page.roundedRect(cursorX, y, width, chipHeight, chipHeight / 2f, fill = fill)
    page.text(cursorX + 7f, y + 12f, text, PdfFont.BOLD, 8.5f, fg)
    cursorX += width + gap
  }
  spacer(chipHeight + 8f)
}

private class PdfChartFrame(
  val plotLeft: Float,
  val plotRight: Float,
  val plotTop: Float,
  val plotBottom: Float,
  val minYear: Int,
  val maxYear: Int,
  val yMax: Double,
) {
  private val yearSpan = max(1, maxYear - minYear)
  val plotW = (plotRight - plotLeft).coerceAtLeast(1f)
  val plotH = (plotBottom - plotTop).coerceAtLeast(1f)

  fun xForYear(year: Int): Float = plotLeft + ((year - minYear).toFloat() / yearSpan) * plotW

  fun yForVal(value: Double): Float =
    plotTop + plotH * (1f - (value / yMax).toFloat().coerceIn(0f, 1f))
}

/**
 * Draws a vector multi-series yearly disturbance/loss/fire chart with a 2020 EUDR cutoff marker and
 * color legend directly onto the PDF page.
 */
private fun PdfReportLayout.whispYearlyVectorChart(
  analysis: WhispAnalysis,
  strings: PdfReportStrings,
) {
  val seriesList = analysis.chartSeries
  if (seriesList.isEmpty()) return
  val allYears = seriesList.flatMap { s -> s.points.map { it.first } }.distinct().sorted()
  val yMax = pdfNiceAxisMax(seriesList.maxOfOrNull { it.maxValue } ?: 1.0, analysis.isPercentUnit)

  val chartBoxHeight = 148f
  val legendRows = ceil(seriesList.size / 3.0).toInt().coerceAtLeast(1)
  ensureSpace(chartBoxHeight + legendRows * 14f + 16f)

  val boxTop = y + 2f
  page.roundedRect(
    x = left,
    top = boxTop,
    w = contentWidth,
    h = chartBoxHeight,
    radius = 6f,
    fill = PdfPalette.surfaceContainer,
    stroke = PdfPalette.outlineVariant,
    lineWidth = 0.5f,
  )

  val frame =
    PdfChartFrame(
      plotLeft = left + 44f,
      plotRight = right - 12f,
      plotTop = boxTop + 14f,
      plotBottom = boxTop + chartBoxHeight - 24f,
      minYear = allYears.first(),
      maxYear = allYears.last(),
      yMax = yMax,
    )
  drawChartGridAndAxes(frame, analysis, strings)
  drawChartSeries(frame, seriesList)
  val legendY = drawChartLegend(seriesList, startY = boxTop + chartBoxHeight + 12f)
  spacer(legendY - y + 8f)
}

private fun PdfReportLayout.drawChartGridAndAxes(
  frame: PdfChartFrame,
  analysis: WhispAnalysis,
  strings: PdfReportStrings,
) {
  // Horizontal gridlines & Y-axis ticks
  listOf(1.0, 0.5, 0.0).forEach { frac ->
    val gy = frame.plotTop + frame.plotH * (1f - frac.toFloat())
    page.line(frame.plotLeft, gy, frame.plotRight, gy, PdfPalette.outlineVariant, 0.5f)
    val label = analysis.formatMetricValue(frame.yMax * frac)
    val tw = PdfFont.REGULAR.measure(label, 7.5f)
    page.text(
      x = frame.plotLeft - tw - 5f,
      baseline = gy + 2.5f,
      text = label,
      font = PdfFont.REGULAR,
      size = 7.5f,
      color = PdfPalette.onSurfaceVariant,
    )
  }

  // Vertical 2020 EUDR cutoff marker
  if (2020 in frame.minYear..frame.maxYear) {
    val cutoffX = frame.xForYear(2020)
    page.line(cutoffX, frame.plotTop, cutoffX, frame.plotBottom, PdfRiskMoreInfo, 0.9f)
    val cutLabel = strings.riskCutoff2020
    val cutW = PdfFont.BOLD.measure(cutLabel, 7.5f)
    val labelX = (cutoffX - cutW / 2f).coerceIn(frame.plotLeft, frame.plotRight - cutW)
    page.text(labelX, frame.plotBottom + 14f, cutLabel, PdfFont.BOLD, 7.5f, PdfRiskMoreInfo)
  }

  // X-axis min & max year labels
  page.text(
    frame.plotLeft,
    frame.plotBottom + 14f,
    frame.minYear.toString(),
    PdfFont.REGULAR,
    7.5f,
    PdfPalette.onSurfaceVariant,
  )
  val maxYearLabel = frame.maxYear.toString()
  val maxYearW = PdfFont.REGULAR.measure(maxYearLabel, 7.5f)
  page.text(
    frame.plotRight - maxYearW,
    frame.plotBottom + 14f,
    maxYearLabel,
    PdfFont.REGULAR,
    7.5f,
    PdfPalette.onSurfaceVariant,
  )
}

private fun PdfReportLayout.drawChartSeries(
  frame: PdfChartFrame,
  seriesList: List<WhispYearlySeries>,
) {
  seriesList.forEachIndexed { idx, series ->
    val color = pdfColorForSeries(series, idx)
    val pts = series.points.map { (yr, v) -> frame.xForYear(yr) to frame.yForVal(v) }
    if (pts.size >= 2) {
      page.polyline(points = pts, closed = false, stroke = color, lineWidth = 1.4f)
    }
    series.points.forEach { (yr, v) ->
      if (v > 0.0) {
        page.circle(frame.xForYear(yr), frame.yForVal(v), 2.2f, fill = color)
      }
    }
  }
}

private fun PdfReportLayout.drawChartLegend(
  seriesList: List<WhispYearlySeries>,
  startY: Float,
): Float {
  var legendY = startY
  var cursorX = left
  seriesList.forEachIndexed { idx, series ->
    val color = pdfColorForSeries(series, idx)
    val labelW = PdfFont.REGULAR.measure(series.label, 8f)
    val itemW = 10f + labelW + 14f
    if (cursorX > left && cursorX + itemW > right) {
      legendY += 13f
      cursorX = left
    }
    page.circle(cursorX + 3.5f, legendY - 2.5f, 3f, fill = color)
    page.text(cursorX + 9f, legendY, series.label, PdfFont.REGULAR, 8f, PdfPalette.onSurface)
    cursorX += itemW
  }
  return legendY
}

private fun PdfReportLayout.whispBeforeAfterBars(
  comparisons: List<WhispBeforeAfterComparison>,
  analysis: WhispAnalysis,
) {
  val maxVal =
    if (analysis.isPercentUnit) 100.0
    else
      comparisons.maxOfOrNull { max(it.before2020 ?: 0.0, it.after2020 ?: 0.0) }?.coerceAtLeast(1.0)
        ?: 1.0
  comparisons.forEach { item ->
    item.before2020?.let { before ->
      pdfProportionBar(
        label = "${item.label} (before 2020)",
        valueText = analysis.formatMetricValue(before),
        fraction = (before / maxVal).toFloat().coerceIn(0f, 1f),
        barColor = PdfPalette.outline,
      )
    }
    item.after2020?.let { after ->
      pdfProportionBar(
        label = "${item.label} (after 2020)",
        valueText = analysis.formatMetricValue(after),
        fraction = (after / maxVal).toFloat().coerceIn(0f, 1f),
        barColor = if (after > 0.0) PdfRiskHigh else PdfPalette.outlineVariant,
      )
    }
  }
}

private fun PdfReportLayout.whispMetricBars(metrics: List<WhispMetric>, analysis: WhispAnalysis) {
  val maxVal =
    if (analysis.isPercentUnit) 100.0
    else metrics.mapNotNull { it.numericValue }.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
  metrics.forEach { metric ->
    val num = metric.numericValue
    if (num != null) {
      pdfProportionBar(
        label = metric.label,
        valueText = analysis.formatMetricValue(num),
        fraction = (num / maxVal).toFloat().coerceIn(0f, 1f),
        barColor = PdfPalette.primary,
      )
    } else {
      field(metric.label, metric.rawValue)
    }
  }
}

private fun PdfReportLayout.pdfProportionBar(
  label: String,
  valueText: String,
  fraction: Float,
  barColor: PdfColor,
) {
  val rowHeight = 16f
  ensureSpace(rowHeight + 2f)
  val labelWidth = contentWidth * 0.42f
  val trackX = left + labelWidth + 10f
  val valueWidth = 58f
  val trackWidth = (right - trackX - valueWidth - 8f).coerceAtLeast(24f)
  val barTop = y + 5f
  val barHeight = 6f
  val trimmedLabel = wrapText(label, PdfFont.REGULAR, 8.5f, labelWidth).firstOrNull().orEmpty()
  page.text(left + 4f, y + 10f, trimmedLabel, PdfFont.REGULAR, 8.5f, PdfPalette.onSurfaceVariant)
  page.roundedRect(trackX, barTop, trackWidth, barHeight, 3f, PdfPalette.surfaceContainer)
  val filled = trackWidth * fraction.coerceIn(0f, 1f)
  if (filled > 0.5f) page.roundedRect(trackX, barTop, filled, barHeight, 3f, barColor)
  page.text(right - valueWidth, y + 10f, valueText, PdfFont.REGULAR, 8.5f, PdfPalette.onSurface)
  spacer(rowHeight)
}

private fun pdfColorForSeries(series: WhispYearlySeries, index: Int): PdfColor =
  when (series.category) {
    WhispYearlyCategory.FOREST_LOSS ->
      if (index % 2 == 0) PdfWhispChartPalette[0] else PdfWhispChartPalette[1]
    WhispYearlyCategory.DEGRADATION_AND_ALERTS -> PdfWhispChartPalette[2 + (index % 3)]
    WhispYearlyCategory.FIRE -> PdfWhispChartPalette[5]
    WhispYearlyCategory.OTHER -> PdfWhispChartPalette[index % PdfWhispChartPalette.size]
  }

private fun pdfNiceAxisMax(rawMax: Double, isPercent: Boolean): Double {
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
