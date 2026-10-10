/*
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.groundplatform.v2.devtools.prototypeapp.client.pdf

import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactFormat
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactIndicator
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactSummary
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactSummaryScope

/**
 * Printable impact summaries of a survey, an organization, or the whole platform, built on the
 * client with [PdfReportLayout] from the same [ImpactSummary] the Impact views show, so the PDF and
 * the screen always agree.
 */
internal object ImpactPdfReports {
  /**
   * Impact summary for [summary]: totals, indicators by goal (with bars for breakdowns), the
   * organization's own and organization-suggested indicators, how the data was used, and what
   * couldn't be counted.
   *
   * @param filterLabel the organization filter in words, if any (e.g. `"Country: Kenya"`).
   */
  fun summary(
    summary: ImpactSummary,
    generatedAtEpochMillis: Long,
    filterLabel: String? = null,
  ): GeneratedPdf {
    val generatedAt = formatUtcTimestamp(generatedAtEpochMillis)
    val scopeLabel =
      when (summary.scope) {
        ImpactSummaryScope.SURVEY -> "Survey"
        ImpactSummaryScope.ORGANIZATION -> "Organization"
        ImpactSummaryScope.GLOBAL -> "All organizations taking part in platform-wide numbers"
      }
    val layout =
      PdfReportLayout(
        title = "Impact summary • ${summary.title}",
        subject = "$scopeLabel impact summary",
        runningHeader = "Ground • Impact summary • ${summary.title}",
        footerNote = "Impact summary • Generated $generatedAt",
      )
    with(layout) {
      title(summary.title)
      subtitle(listOfNotNull("$scopeLabel impact summary", filterLabel).joinToString(" • "))
      paragraph(
        "Plots mapped more than once (same GeoID) are counted once.",
        size = 9f,
        color = PdfPalette.onSurfaceVariant,
      )

      sectionHeading("At a glance")
      val features = summary.features
      field("Unique map features", ImpactFormat.count(features.uniqueCount))
      field("Area mapped", ImpactFormat.hectares(features.areaHa))
      if (features.duplicateCount > 0) {
        field(
          "Counted once",
          "${ImpactFormat.count(features.duplicateCount, "map feature")} mapped again elsewhere",
        )
      }
      summary.producerCount?.let { field("Producers registered", ImpactFormat.count(it)) }

      summary.goals.forEach { goal ->
        sectionHeading(goal.title)
        goal.indicators.forEach { indicator(it) }
      }
      if (summary.ownIndicators.isNotEmpty()) {
        sectionHeading("Your indicators")
        summary.ownIndicators.forEach { indicator(it) }
      }
      if (summary.suggestedIndicators.isNotEmpty()) {
        sectionHeading("Organization-suggested indicators")
        paragraph(
          "Organizations' own fields they suggest count toward a goal. Not added to the totals " +
            "above.",
          size = 9f,
          color = PdfPalette.onSurfaceVariant,
        )
        summary.suggestedIndicators.forEach { group ->
          subheading(group.goalTitle, meta = group.organizationName)
          group.indicators.forEach { indicator(it) }
        }
      }

      sectionHeading("How the data was used")
      summary.activity.forEach { item ->
        val coverage =
          if (item.featureCount > 0) {
            " • ${ImpactFormat.count(item.featureCount, "map feature")}"
          } else {
            ""
          }
        field(item.label, ImpactFormat.count(item.count) + coverage)
      }
      val outcomes = summary.outcomes
      outcomes.uses.forEach { (kind, count) ->
        field(kind.label, ImpactFormat.count(count, "survey"))
      }
      if (outcomes.notYetCount > 0) {
        field("Not used yet", ImpactFormat.count(outcomes.notYetCount, "survey"))
      }
      if (outcomes.unansweredCount > 0) {
        field("Closed, not answered", ImpactFormat.count(outcomes.unansweredCount, "survey"))
      }
      summary.attribution.level?.let { level ->
        field("Contribution", "${level.score} of 5 • ${level.label}")
        paragraph(level.explanation, size = 9f, color = PdfPalette.onSurfaceVariant)
      }
      if (summary.attribution.distribution.isNotEmpty()) {
        val total = summary.attribution.distribution.values.sum()
        summary.attribution.distribution.entries
          .sortedByDescending { it.key.score }
          .forEach { (level, count) ->
            bar(
              "${level.score} • ${level.label}",
              ImpactFormat.count(count, "survey"),
              if (total > 0) count.toFloat() / total else 0f,
            )
          }
      }

      if (summary.countries.isNotEmpty()) {
        sectionHeading("By country")
        summary.countries.forEach { country ->
          field(
            country.countryName,
            if (country.isSuppressed) {
              "Fewer than 10 map features"
            } else {
              "${ImpactFormat.count(country.featureCount, "map feature")} • " +
                ImpactFormat.hectares(country.areaHa)
            },
          )
        }
      }
      summary.cells?.let { cells ->
        sectionHeading("Map areas")
        field("Map areas with numbers", ImpactFormat.count(cells.publishedCellCount.toLong()))
        field(
          "Kept private",
          "${ImpactFormat.count(cells.suppressedCellCount.toLong(), "map area")} with fewer than " +
            "10 map features or 2 organizations",
        )
      }
      if (summary.scope == ImpactSummaryScope.GLOBAL && summary.excludedOrganizationCount > 0) {
        paragraph(
          "${ImpactFormat.count(summary.excludedOrganizationCount.toLong(), "organization")} " +
            "keep their data out of platform-wide numbers.",
          size = 9f,
          color = PdfPalette.onSurfaceVariant,
        )
      }

      if (summary.diagnostics.isNotEmpty()) {
        sectionHeading("Not counted")
        summary.diagnostics.forEach {
          paragraph("• ${it.message}", size = 9.5f, color = PdfPalette.onSurfaceVariant)
        }
      }
    }
    return layout.finish(fileName(summary))
  }

  private fun PdfReportLayout.indicator(indicator: ImpactIndicator) {
    field(
      indicator.label,
      listOf(indicator.valueText, indicator.detail).filter { it.isNotBlank() }.joinToString(" • "),
    )
    indicator.breakdown.forEach { bar(it.label, it.valueText, it.fraction) }
  }

  /** A labeled horizontal bar showing [fraction] (0–1) of the content width. */
  private fun PdfReportLayout.bar(label: String, valueText: String, fraction: Float) {
    val rowHeight = 16f
    ensureSpace(rowHeight + 2f)
    val labelWidth = contentWidth * 0.34f
    val trackX = left + labelWidth + 12f
    val valueWidth = 110f
    val trackWidth = right - trackX - valueWidth - 8f
    val barTop = y + 5f
    val barHeight = 6f
    page.text(left + 8f, y + 10f, label, PdfFont.REGULAR, 8.5f, PdfPalette.onSurfaceVariant)
    page.roundedRect(trackX, barTop, trackWidth, barHeight, 3f, PdfPalette.surfaceContainer)
    val filled = trackWidth * fraction.coerceIn(0f, 1f)
    if (filled > 0.5f) page.roundedRect(trackX, barTop, filled, barHeight, 3f, PdfPalette.primary)
    page.text(right - valueWidth, y + 10f, valueText, PdfFont.REGULAR, 8.5f, PdfPalette.onSurface)
    spacer(rowHeight)
  }

  /** File name of [summary]'s PDF, e.g. `impact-summary-kenya-forest-service.pdf`. */
  fun fileName(summary: ImpactSummary): String {
    val slug =
      summary.title
        .lowercase()
        .map { if (it.isLetterOrDigit()) it else '-' }
        .joinToString("")
        .split('-')
        .filter { it.isNotEmpty() }
        .joinToString("-")
        .take(60)
        .ifEmpty { "ground" }
    return "impact-summary-$slug.pdf"
  }
}
