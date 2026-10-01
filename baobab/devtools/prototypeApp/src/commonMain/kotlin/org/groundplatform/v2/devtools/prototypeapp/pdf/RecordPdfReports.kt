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
package org.groundplatform.v2.devtools.prototypeapp.pdf

import org.groundplatform.v2.devtools.prototypeapp.EntityGeometryKind
import org.groundplatform.v2.devtools.prototypeapp.displayProperties
import org.groundplatform.v2.devtools.prototypeapp.domain.model.GeospatialEntityItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MeasurementUnitSystem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionFieldEntry
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.entityGeometrySummary
import org.groundplatform.v2.devtools.prototypeapp.geometryKind
import org.groundplatform.v2.map.Geometry

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
   * @param relatedLabelFor label of the record a property value references (a foreign key), if any.
   */
  fun entityReport(
    entity: GeospatialEntityItem,
    surveyTitle: String,
    geometry: Geometry?,
    unitSystem: MeasurementUnitSystem,
    generatedAtEpochMillis: Long,
    relatedLabelFor: (String) -> String? = { null },
  ): GeneratedPdf {
    val generatedAt = formatUtcTimestamp(generatedAtEpochMillis)
    val layout =
      PdfReportLayout(
        title = "${entity.label} • ${entity.singularTypeLabel}",
        subject = "$surveyTitle • ${entity.datasetName}",
        runningHeader = "Ground • $surveyTitle",
        footerNote = "${entity.singularTypeLabel} report • Generated $generatedAt",
      )
    with(layout) {
      title(entity.label)
      subtitle("${entity.singularTypeLabel} • ${entity.datasetName}")
      spacer(4f)
      statusChip(
        markerSymbol = entity.markerSymbol,
        statusText = entity.workflowStatus,
        statusColor = PdfColor.fromCss(entity.markerColorCss, PdfColor.fromArgb(entity.colorHex)),
      )

      sectionHeading("Details")
      field("Layer", entity.datasetName)
      field("Geometry", entityGeometrySummary(entity, unitSystem))
      field("GeoID", entity.geoId, mono = true)
      if (entity.coordinatesLabel.isNotBlank()) field("Coordinates", entity.coordinatesLabel)
      field("Record ID", entity.id, mono = true)

      if (geometry != null && entity.geometryKind != EntityGeometryKind.NONE) {
        sectionHeading("Location")
        val color = PdfColor.fromCss(entity.strokeColorCss, PdfColor.fromArgb(entity.colorHex))
        geometryFigure(
          paths = geometry.paths(),
          closed = geometry is Geometry.Polygon,
          color = color,
        )
        val vertices = geometry.paths().flatten()
        if (vertices.size > 1) {
          spacer(4f)
          vertices.forEachIndexed { index, (lng, lat) ->
            field(
              "Vertex ${index + 1}",
              "${formatCoordinate(lat)}, ${formatCoordinate(lng)}",
              mono = true,
            )
          }
        }
      }

      val properties = entity.displayProperties
      if (properties.isNotEmpty()) {
        sectionHeading("Properties")
        properties.forEach { (key, value) ->
          val related = relatedLabelFor(value)
          field(key, if (related != null) "$related ($value)" else value)
        }
      }

      sectionHeading("Submissions")
      if (entity.submissions.isEmpty()) {
        paragraph("No submissions recorded yet.", size = 9.5f, color = PdfPalette.onSurfaceVariant)
      }
      entity.submissions.forEach { submission ->
        subheading(
          text = "${submission.formTitle} • ${submission.timestamp}",
          meta =
            "Collected by ${submission.collectorName} • " +
              "Form version ${submission.formVersion} • ${submission.id}",
        )
        responses(submission.fields)
        spacer(6f)
      }
    }
    return layout.finish(entityFileName(entity))
  }

  /** Report for a single submission: where and by whom it was collected, and every response. */
  fun submissionReport(
    submission: SubmissionPreviewItem,
    surveyTitle: String,
    entity: GeospatialEntityItem?,
    generatedAtEpochMillis: Long,
  ): GeneratedPdf {
    val generatedAt = formatUtcTimestamp(generatedAtEpochMillis)
    val layout =
      PdfReportLayout(
        title = "${submission.formTitle} • ${submission.timestamp}",
        subject = surveyTitle,
        runningHeader = "Ground • $surveyTitle",
        footerNote = "Submission ${submission.id} • Generated $generatedAt",
      )
    with(layout) {
      title(submission.formTitle)
      subtitle("Submission • ${submission.timestamp}")
      spacer(4f)

      sectionHeading("Details")
      val target =
        when {
          entity != null -> "${entity.label} (${entity.singularTypeLabel} • GeoID ${entity.geoId})"
          submission.hasAttachedEntity -> submission.entityLabel
          submission.coordinatesLabel.isNotBlank() ->
            "No attached map feature (${submission.coordinatesLabel})"
          else -> "No attached map feature"
        }
      field(submission.targetTypeLabel, target)
      field("Data collector", "${submission.collectorName} (${submission.collectorEmail})")
      field("Collected", submission.timestamp)
      field("Form version", submission.formVersion, mono = true)
      field("Submission ID", submission.id, mono = true)

      sectionHeading("Responses")
      if (submission.fields.isEmpty()) {
        paragraph("No responses recorded.", size = 9.5f, color = PdfPalette.onSurfaceVariant)
      }
      responses(submission.fields)
    }
    return layout.finish(submissionFileName(submission))
  }

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
    "${slug(entity.singularTypeLabel)}-" +
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

/** Vertex paths of a geometry as (longitude, latitude) pairs. */
private fun Geometry.paths(): List<List<Pair<Double, Double>>> =
  when (this) {
    is Geometry.Point -> listOf(listOf(position.longitude to position.latitude))
    is Geometry.LineString -> listOf(points.map { it.longitude to it.latitude })
    is Geometry.Polygon -> rings.map { ring -> ring.map { it.longitude to it.latitude } }
  }

/** Formats Unix epoch milliseconds as `YYYY-MM-DD HH:MM UTC` (proleptic Gregorian calendar). */
internal fun formatUtcTimestamp(epochMillis: Long): String {
  val totalMinutes = epochMillis.floorDiv(60_000L)
  val days = totalMinutes.floorDiv(1_440L)
  val minuteOfDay = totalMinutes.mod(1_440L).toInt()
  // Days-from-civil inverse (H. Hinnant, "chrono-Compatible Low-Level Date Algorithms").
  val z = days + 719_468
  val era = z.floorDiv(146_097L)
  val doe = z - era * 146_097
  val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
  val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
  val mp = (5 * doy + 2) / 153
  val day = doy - (153 * mp + 2) / 5 + 1
  val month = if (mp < 10) mp + 3 else mp - 9
  val year = yoe + era * 400 + if (month <= 2) 1 else 0
  fun two(n: Number) = n.toString().padStart(2, '0')
  return "$year-${two(month)}-${two(day)} ${two(minuteOfDay / 60)}:${two(minuteOfDay % 60)} UTC"
}
