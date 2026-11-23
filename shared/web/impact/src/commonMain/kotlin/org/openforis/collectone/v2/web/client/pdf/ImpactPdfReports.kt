/*
 * Copyright 2026 The Collect One Authors.
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
package org.openforis.collectone.v2.web.client.pdf

import kotlin.time.Instant
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.openforis.collectone.v2.app.client.pdf.GeneratedPdf
import org.openforis.collectone.v2.app.client.pdf.PdfFont
import org.openforis.collectone.v2.app.client.pdf.PdfPalette
import org.openforis.collectone.v2.app.client.pdf.PdfReportLayout
import org.openforis.collectone.v2.app.client.pdf.PdfReportStrings
import org.openforis.collectone.v2.app.client.pdf.formatUtcTimestamp
import org.openforis.collectone.v2.app.ui.text.labelRes
import org.openforis.collectone.v2.web.domain.model.ImpactFormat
import org.openforis.collectone.v2.web.domain.model.ImpactIndicator
import org.openforis.collectone.v2.web.domain.model.ImpactSummary
import org.openforis.collectone.v2.web.domain.model.ImpactSummaryScope
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.wizard_impact_closed_not_answered
import org.openforis.collectone.v2.web.resources.wizard_impact_global_title
import org.openforis.collectone.v2.web.resources.wizard_impact_how_data_was_used
import org.openforis.collectone.v2.web.resources.wizard_impact_level_of_five
import org.openforis.collectone.v2.web.resources.wizard_impact_map_features
import org.openforis.collectone.v2.web.resources.wizard_impact_not_used_yet
import org.openforis.collectone.v2.web.resources.wizard_impact_own_indicators
import org.openforis.collectone.v2.web.resources.wizard_impact_pdf_area_mapped
import org.openforis.collectone.v2.web.resources.wizard_impact_pdf_at_a_glance
import org.openforis.collectone.v2.web.resources.wizard_impact_pdf_by_country
import org.openforis.collectone.v2.web.resources.wizard_impact_pdf_contribution
import org.openforis.collectone.v2.web.resources.wizard_impact_pdf_counted_once
import org.openforis.collectone.v2.web.resources.wizard_impact_pdf_counted_once_note
import org.openforis.collectone.v2.web.resources.wizard_impact_pdf_counted_once_note_published
import org.openforis.collectone.v2.web.resources.wizard_impact_pdf_excluded_organizations
import org.openforis.collectone.v2.web.resources.wizard_impact_pdf_fewer_than_10_features
import org.openforis.collectone.v2.web.resources.wizard_impact_pdf_footer
import org.openforis.collectone.v2.web.resources.wizard_impact_pdf_kept_private
import org.openforis.collectone.v2.web.resources.wizard_impact_pdf_kept_private_areas
import org.openforis.collectone.v2.web.resources.wizard_impact_pdf_map_areas
import org.openforis.collectone.v2.web.resources.wizard_impact_pdf_map_areas_with_numbers
import org.openforis.collectone.v2.web.resources.wizard_impact_pdf_mapped_again
import org.openforis.collectone.v2.web.resources.wizard_impact_pdf_not_counted
import org.openforis.collectone.v2.web.resources.wizard_impact_pdf_published_numbers
import org.openforis.collectone.v2.web.resources.wizard_impact_pdf_running_header
import org.openforis.collectone.v2.web.resources.wizard_impact_pdf_subject_global
import org.openforis.collectone.v2.web.resources.wizard_impact_pdf_subject_organization
import org.openforis.collectone.v2.web.resources.wizard_impact_pdf_subject_survey
import org.openforis.collectone.v2.web.resources.wizard_impact_pdf_suggested_note
import org.openforis.collectone.v2.web.resources.wizard_impact_pdf_title
import org.openforis.collectone.v2.web.resources.wizard_impact_pdf_unique_map_features
import org.openforis.collectone.v2.web.resources.wizard_impact_producers_registered
import org.openforis.collectone.v2.web.resources.wizard_impact_suggested_indicators
import org.openforis.collectone.v2.web.resources.wizard_impact_surveys
import org.openforis.collectone.v2.web.resources.wizard_impact_your_indicators
import org.openforis.collectone.v2.web.ui.text.activityLabelRes
import org.openforis.collectone.v2.web.ui.text.explanationRes
import org.openforis.collectone.v2.web.ui.text.impactMeasurementText
import org.openforis.collectone.v2.web.ui.text.labelRes
import org.openforis.collectone.v2.web.ui.text.load

/**
 * Printable impact summaries of a survey, an organization, or the whole platform, built on the
 * client with [PdfReportLayout] from the same [ImpactSummary] the Impact views show, so the PDF and
 * the screen always agree.
 */
object ImpactPdfReports {
  /**
   * Impact summary for [summary]: totals, indicators by goal (with bars for breakdowns), the
   * organization's own and organization-suggested indicators, how the data was used, and what
   * couldn't be counted. Suspends to load its text in the current locale.
   *
   * @param filterLabel the organization filter in words, if any (e.g. `"Country: Kenya"`).
   */
  suspend fun summary(
    summary: ImpactSummary,
    generatedAt: Instant,
    filterLabel: String? = null,
  ): GeneratedPdf {
    val generatedAtText = formatUtcTimestamp(generatedAt)
    val displayTitle =
      if (summary.scope == ImpactSummaryScope.GLOBAL)
        getString(Res.string.wizard_impact_global_title)
      else summary.title
    val subject =
      getString(
        when (summary.scope) {
          ImpactSummaryScope.SURVEY -> Res.string.wizard_impact_pdf_subject_survey
          ImpactSummaryScope.ORGANIZATION -> Res.string.wizard_impact_pdf_subject_organization
          ImpactSummaryScope.GLOBAL -> Res.string.wizard_impact_pdf_subject_global
        }
      )
    val layout =
      PdfReportLayout(
        title = getString(Res.string.wizard_impact_pdf_title, displayTitle),
        subject = subject,
        runningHeader = getString(Res.string.wizard_impact_pdf_running_header, displayTitle),
        footerNote = getString(Res.string.wizard_impact_pdf_footer, generatedAtText),
        strings = PdfReportStrings.load(),
      )
    suspend fun mapFeatures(count: Long) =
      getPluralString(
        Res.plurals.wizard_impact_map_features,
        count.toQuantity(),
        ImpactFormat.count(count),
      )
    suspend fun surveyCount(count: Long) =
      getPluralString(
        Res.plurals.wizard_impact_surveys,
        count.toQuantity(),
        ImpactFormat.count(count),
      )
    with(layout) {
      title(displayTitle)
      subtitle(
        listOfNotNull(
            subject,
            getString(Res.string.wizard_impact_pdf_published_numbers).takeIf {
              summary.isPublished
            },
            filterLabel,
          )
          .joinToString(" • ")
      )
      paragraph(
        getString(
          if (summary.isPublished) Res.string.wizard_impact_pdf_counted_once_note_published
          else Res.string.wizard_impact_pdf_counted_once_note
        ),
        size = 9f,
        color = PdfPalette.onSurfaceVariant,
      )

      sectionHeading(getString(Res.string.wizard_impact_pdf_at_a_glance))
      val features = summary.features
      field(
        getString(Res.string.wizard_impact_pdf_unique_map_features),
        ImpactFormat.count(features.uniqueCount),
      )
      field(
        getString(Res.string.wizard_impact_pdf_area_mapped),
        impactMeasurementText(ImpactFormat.hectares(features.areaHa)).resolveString(),
      )
      if (features.duplicateCount > 0) {
        field(
          getString(Res.string.wizard_impact_pdf_counted_once),
          getPluralString(
            Res.plurals.wizard_impact_pdf_mapped_again,
            features.duplicateCount.toQuantity(),
            ImpactFormat.count(features.duplicateCount),
          ),
        )
      }
      summary.producerCount?.let {
        field(getString(Res.string.wizard_impact_producers_registered), ImpactFormat.count(it))
      }

      summary.goals.forEach { goal ->
        sectionHeading(goal.title.load())
        goal.indicators.forEach { indicator(it) }
      }
      if (summary.ownIndicators.isNotEmpty()) {
        sectionHeading(
          getString(
            if (summary.isPublished) Res.string.wizard_impact_own_indicators
            else Res.string.wizard_impact_your_indicators
          )
        )
        summary.ownIndicators.forEach { indicator(it) }
      }
      if (summary.suggestedIndicators.isNotEmpty()) {
        sectionHeading(getString(Res.string.wizard_impact_suggested_indicators))
        paragraph(
          getString(Res.string.wizard_impact_pdf_suggested_note),
          size = 9f,
          color = PdfPalette.onSurfaceVariant,
        )
        summary.suggestedIndicators.forEach { group ->
          subheading(group.goalTitle.load(), meta = group.organizationName)
          group.indicators.forEach { indicator(it) }
        }
      }

      sectionHeading(getString(Res.string.wizard_impact_how_data_was_used))
      summary.activity.forEach { item ->
        val coverage = if (item.featureCount > 0) " • ${mapFeatures(item.featureCount)}" else ""
        field(getString(item.type.activityLabelRes), ImpactFormat.count(item.count) + coverage)
      }
      val outcomes = summary.outcomes
      outcomes.uses.forEach { (kind, count) -> field(getString(kind.labelRes), surveyCount(count)) }
      if (outcomes.notYetCount > 0) {
        field(getString(Res.string.wizard_impact_not_used_yet), surveyCount(outcomes.notYetCount))
      }
      if (outcomes.unansweredCount > 0) {
        field(
          getString(Res.string.wizard_impact_closed_not_answered),
          surveyCount(outcomes.unansweredCount),
        )
      }
      summary.attribution.level?.let { level ->
        field(
          getString(Res.string.wizard_impact_pdf_contribution),
          "${getString(Res.string.wizard_impact_level_of_five, level.score.toString())} • " +
            getString(level.labelRes),
        )
        paragraph(getString(level.explanationRes), size = 9f, color = PdfPalette.onSurfaceVariant)
      }
      if (summary.attribution.distribution.isNotEmpty()) {
        val total = summary.attribution.distribution.values.sum()
        summary.attribution.distribution.entries
          .sortedByDescending { it.key.score }
          .forEach { (level, count) ->
            bar(
              "${level.score} • ${getString(level.labelRes)}",
              surveyCount(count),
              if (total > 0) count.toFloat() / total else 0f,
            )
          }
      }

      if (summary.countries.isNotEmpty()) {
        sectionHeading(getString(Res.string.wizard_impact_pdf_by_country))
        summary.countries.forEach { country ->
          field(
            country.countryName,
            if (country.isSuppressed) {
              getString(Res.string.wizard_impact_pdf_fewer_than_10_features)
            } else {
              val area = impactMeasurementText(ImpactFormat.hectares(country.areaHa))
              "${mapFeatures(country.featureCount)} • ${area.resolveString()}"
            },
          )
        }
      }
      summary.cells?.let { cells ->
        sectionHeading(getString(Res.string.wizard_impact_pdf_map_areas))
        field(
          getString(Res.string.wizard_impact_pdf_map_areas_with_numbers),
          ImpactFormat.count(cells.publishedCellCount.toLong()),
        )
        field(
          getString(Res.string.wizard_impact_pdf_kept_private),
          getPluralString(
            Res.plurals.wizard_impact_pdf_kept_private_areas,
            cells.suppressedCellCount,
            ImpactFormat.count(cells.suppressedCellCount.toLong()),
          ),
        )
      }
      if (summary.scope == ImpactSummaryScope.GLOBAL && summary.excludedOrganizationCount > 0) {
        paragraph(
          getPluralString(
            Res.plurals.wizard_impact_pdf_excluded_organizations,
            summary.excludedOrganizationCount,
            ImpactFormat.count(summary.excludedOrganizationCount.toLong()),
          ),
          size = 9f,
          color = PdfPalette.onSurfaceVariant,
        )
      }

      if (summary.diagnostics.isNotEmpty()) {
        sectionHeading(getString(Res.string.wizard_impact_pdf_not_counted))
        summary.diagnostics.forEach {
          paragraph("• ${it.message.load()}", size = 9.5f, color = PdfPalette.onSurfaceVariant)
        }
      }
    }
    return layout.finish(fileName(summary))
  }

  private fun Long.toQuantity(): Int = coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

  private suspend fun PdfReportLayout.indicator(indicator: ImpactIndicator) {
    field(
      indicator.label,
      listOf(indicator.valueText.load(), indicator.detail.load())
        .filter { it.isNotBlank() }
        .joinToString(" • "),
    )
    indicator.breakdown.forEach { bar(it.label, it.valueText.load(), it.fraction) }
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
        .ifEmpty { "collect-one" }
    return "impact-summary-$slug.pdf"
  }
}
