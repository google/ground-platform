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

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.MeasurementUnitSystem
import org.openforis.collectone.v2.app.domain.model.SubmissionFieldEntry
import org.openforis.collectone.v2.app.domain.model.SubmissionPreviewItem
import org.openforis.collectone.v2.app.domain.model.geometryType
import org.openforis.collectone.v2.app.domain.model.regularDisplayProperties
import org.openforis.collectone.v2.app.domain.model.updatedAtLabel
import org.openforis.collectone.v2.app.domain.model.whispAnalysis
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.GeometryType

/**
 * Builds the printable reports for map features (`EntityRecord`) and submissions
 * (`SubmissionRecord`), entirely on the client and offline, with [PdfReportLayout].
 */
internal object RecordPdfReports {

  /**
   * Report for a map feature or data table record: its workflow status, details, location figure
   * and vertices, properties, and every submission recorded for it. Device-local state (upload
   * status) and summary counts are left out.
   *
   * Suspends to load the report's text ([PdfReportStrings.load]) in the language selected now, so a
   * report asked for after the user switches language is written in the new one.
   *
   * @param relatedLabelFor label of the record a property value references (a foreign key), if any.
   */
  suspend fun entityReport(
    entity: GeospatialEntityItem,
    surveyTitle: String,
    geometry: Geometry?,
    unitSystem: MeasurementUnitSystem,
    generatedAt: Instant,
    relatedLabelFor: (String) -> String? = { null },
  ): GeneratedPdf =
    entityReport(
      entity,
      surveyTitle,
      geometry,
      unitSystem,
      generatedAt,
      relatedLabelFor,
      PdfReportStrings.load(),
    )

  /** [entityReport] with its text from [strings]. */
  internal fun entityReport(
    entity: GeospatialEntityItem,
    surveyTitle: String,
    geometry: Geometry?,
    unitSystem: MeasurementUnitSystem,
    generatedAt: Instant,
    relatedLabelFor: (String) -> String? = { null },
    strings: PdfReportStrings,
  ): GeneratedPdf {
    val generatedAtText = formatUtcTimestamp(generatedAt)
    val layout =
      PdfReportLayout(
        title = "${entity.label} • ${entity.typeNoun.ifBlank { strings.location }}",
        subject = "$surveyTitle • ${entity.datasetName}",
        runningHeader = formatTemplate(strings.runningHeader, surveyTitle),
        footerNote =
          formatTemplate(
            strings.entityFooter,
            entity.typeNoun.ifBlank { strings.location },
            generatedAtText,
          ),
        strings = strings,
      )
    with(layout) {
      title(entity.label)
      subtitle("${entity.typeNoun.ifBlank { strings.location }} • ${entity.datasetName}")
      spacer(4f)
      if (entity.hasWorkflowStatus) {
        statusChip(
          markerSymbol = entity.markerSymbol,
          statusText = entity.workflowStatus,
          statusColor = PdfColor.fromCss(entity.markerColorCss, PdfColor.fromArgb(entity.colorHex)),
        )
      }

      sectionHeading(strings.details)
      field(strings.layer, entity.datasetName)
      field(strings.geometry, geometrySummary(entity, unitSystem, strings))
      field(strings.geoId, entity.geoId, mono = true)
      entityCoordinates(entity, strings)?.let { field(strings.coordinates, it) }

      if (geometry != null && entity.geometryType != null) {
        sectionHeading(strings.location)
        val color = PdfColor.fromCss(entity.strokeColorCss, PdfColor.fromArgb(entity.colorHex))
        geometryFigure(
          paths = geometry.paths(),
          closed = geometry.type == GeometryType.POLYGON,
          color = color,
        )
        val vertices = geometry.paths().flatten()
        if (vertices.size > 1) {
          spacer(4f)
          vertices.forEachIndexed { index, (lng, lat) ->
            field(
              formatTemplate(strings.vertex, index + 1),
              "${formatCoordinate(lat)}, ${formatCoordinate(lng)}",
              mono = true,
            )
          }
        }
      }

      val properties = entity.regularDisplayProperties
      if (properties.isNotEmpty()) {
        sectionHeading(strings.properties)
        properties.forEach { (key, value) ->
          val related = relatedLabelFor(value)
          field(key, related ?: value)
        }
      }

      entity.whispAnalysis?.let { whisp -> whispRiskAnalysisSection(whisp, strings) }

      sectionHeading(strings.submissions)
      if (entity.submissions.isEmpty()) {
        paragraph(strings.noSubmissions, size = 9.5f, color = PdfPalette.onSurfaceVariant)
      }
      entity.submissions.forEach { submission ->
        subheading(
          text = "${submission.formTitle} • ${submission.updatedAtLabel}",
          meta =
            formatTemplate(strings.collectedBy, submission.collectorName, submission.formVersion),
        )
        responses(submission.fields)
        spacer(6f)
      }
    }
    return layout.finish(entityFileName(entity))
  }

  /**
   * Report for a single submission: where and by whom it was collected, and every response.
   * Suspends to load the report's text in the language selected now, like [entityReport].
   */
  suspend fun submissionReport(
    submission: SubmissionPreviewItem,
    surveyTitle: String,
    entity: GeospatialEntityItem?,
    generatedAt: Instant,
  ): GeneratedPdf =
    submissionReport(submission, surveyTitle, entity, generatedAt, PdfReportStrings.load())

  /** [submissionReport] with its text from [strings]. */
  internal fun submissionReport(
    submission: SubmissionPreviewItem,
    surveyTitle: String,
    entity: GeospatialEntityItem?,
    generatedAt: Instant,
    strings: PdfReportStrings,
  ): GeneratedPdf {
    val generatedAtText = formatUtcTimestamp(generatedAt)
    val layout =
      PdfReportLayout(
        title = "${submission.formTitle} • ${submission.updatedAtLabel}",
        subject = surveyTitle,
        runningHeader = formatTemplate(strings.runningHeader, surveyTitle),
        footerNote = formatTemplate(strings.submissionFooter, generatedAtText),
        strings = strings,
      )
    with(layout) {
      title(submission.formTitle)
      subtitle(formatTemplate(strings.submissionSubtitle, submission.updatedAtLabel))
      spacer(4f)

      sectionHeading(strings.details)
      val target =
        when {
          entity != null ->
            formatTemplate(
              strings.entityTarget,
              entity.label,
              entity.typeNoun.ifBlank { strings.location },
              entity.geoId,
            )
          submission.hasAttachedEntity -> submission.entityLabel
          else ->
            submission.location?.let {
              formatTemplate(strings.noAttachedFeatureAt, strings.coordinatesText(it))
            } ?: strings.noAttachedFeature
        }
      field(submission.targetTypeNoun.ifBlank { strings.location }, target)
      field(strings.dataCollector, "${submission.collectorName} (${submission.collectorEmail})")
      field(strings.collected, submission.updatedAtLabel)
      field(strings.formVersion, submission.formVersion, mono = true)

      sectionHeading(strings.responses)
      if (submission.fields.isEmpty()) {
        paragraph(strings.noResponses, size = 9.5f, color = PdfPalette.onSurfaceVariant)
      }
      responses(submission.fields)
    }
    return layout.finish(submissionFileName(submission))
  }

  /** The entity geometry summary (as in `entityGeometrySummaryText`) with [strings]' templates. */
  private fun geometrySummary(
    entity: GeospatialEntityItem,
    unitSystem: MeasurementUnitSystem,
    strings: PdfReportStrings,
  ): String {
    val length = strings.measurementText(unitSystem.length(entity.perimeterMeters))
    return when (entity.geometryType) {
      GeometryType.POLYGON ->
        formatTemplate(
          strings.geometryPolygon,
          strings.measurementText(unitSystem.area(entity.areaHectares)),
          length,
        )
      GeometryType.LINE_STRING -> formatTemplate(strings.geometryLine, length)
      GeometryType.POINT ->
        formatTemplate(strings.geometryPoint, entityCoordinates(entity, strings).orEmpty())
      null -> strings.geometryNone
    }
  }

  /** [entity]'s coordinates in [strings]' language, or `null` for a record without geometry. */
  private fun entityCoordinates(entity: GeospatialEntityItem, strings: PdfReportStrings): String? =
    entity.geometry?.representativePoint?.let(strings::coordinatesText)

  /** Lays out answers, with a label whenever the enclosing XForms group changes. */
  private fun PdfReportLayout.responses(fields: List<SubmissionFieldEntry>) {
    var currentGroup = ""
    fields.forEach { field ->
      val group = field.questionName.substringBeforeLast('/', missingDelimiterValue = "")
      if (group != currentGroup) {
        currentGroup = group
        if (group.isNotEmpty()) groupLabel(humanizeGroupPath(group))
      }
      field(field.questionLabel.ifBlank { field.questionName }, field.answerValue)
    }
  }

  /** `"site_visit/canopy"` → `"Site visit › Canopy"`, using WinAnsi-safe separators. */
  internal fun humanizeGroupPath(path: String): String =
    path
      .split('/')
      .filter { it.isNotBlank() }
      .joinToString(" › ") { segment ->
        segment.replace('_', ' ').replace('-', ' ').trim().replaceFirstChar { it.uppercaseChar() }
      }

  /** e.g. `coffee-parcel-S2-10c4a89e2f.pdf`; the GeoID keeps its case so it stays verbatim. */
  fun entityFileName(entity: GeospatialEntityItem): String =
    "${slug(entity.typeNoun.ifBlank { entity.datasetName })}-" +
      "${slug(entity.geoId.ifBlank { entity.id }, lowercase = false)}.pdf"

  /** e.g. `farm-inspection-sub-123.pdf`. */
  fun submissionFileName(submission: SubmissionPreviewItem): String =
    "${slug(submission.formTitle)}-${slug(submission.id)}.pdf"

  /** ASCII-only, hyphen-separated file name segment, lowercased unless [lowercase] is false. */
  internal fun slug(text: String, lowercase: Boolean = true): String =
    (if (lowercase) text.lowercase() else text)
      .map { if (it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9') it else '-' }
      .joinToString("")
      .split('-')
      .filter { it.isNotEmpty() }
      .joinToString("-")
      .take(60)
      .ifEmpty { "record" }
}

/** Vertex paths of every part of a geometry as (longitude, latitude) pairs. */
private fun Geometry.paths(): List<List<Pair<Double, Double>>> = parts.flatMap { part ->
  when (part) {
    is Geometry.Point -> listOf(listOf(part.position.longitude to part.position.latitude))
    is Geometry.LineString -> listOf(part.points.map { it.longitude to it.latitude })
    is Geometry.Polygon -> part.rings.map { ring -> ring.map { it.longitude to it.latitude } }
    else -> emptyList()
  }
}

/** Formats [instant] as `YYYY-MM-DD HH:MM UTC`, dropping seconds. */
fun formatUtcTimestamp(instant: Instant): String =
  UTC_MINUTES.format(instant.toLocalDateTime(TimeZone.UTC))

private val UTC_MINUTES = LocalDateTime.Format {
  date(LocalDate.Formats.ISO)
  char(' ')
  hour()
  char(':')
  minute()
  chars(" UTC")
}
