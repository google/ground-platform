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

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.MeasurementUnitSystem
import org.openforis.collectone.v2.app.domain.model.SubmissionPreviewItem
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.LatLng

/**
 * The coordinates printed in reports: an entity report's details field and geometry summary, and a
 * standalone submission report's target.
 *
 * JVM-only: [PdfReportStrings.load] reads Compose string resources, which on Android need
 * `android.content.res.Resources`, a stub that throws in Android unit tests.
 */
class RecordPdfCoordinatesTest {
  private val generatedAt = Instant.parse("2026-09-21T14:13:00Z")

  private val station =
    GeospatialEntityItem(
      id = "station-1",
      label = "Weather station",
      datasetId = "stations",
      datasetName = "Stations",
      layerId = "layer-stations",
      geoId = "",
      geometry = Geometry.Point(LatLng(6.69, -1.62)),
      areaHectares = 0.0,
      perimeterMeters = 0,
      colorHex = 0xFF2E7D32L,
      properties = emptyMap(),
      submissions = emptyList(),
    )

  private fun reportText(strings: PdfReportStrings): String =
    RecordPdfReports.entityReport(
        entity = station,
        surveyTitle = "Survey",
        geometry = station.geometry,
        unitSystem = MeasurementUnitSystem.METRIC,
        generatedAt = generatedAt,
        strings = strings,
      )
      .bytes
      .decodeToString()

  private val standaloneObservation =
    SubmissionPreviewItem(
      id = "submission-1",
      formId = "form-1",
      formTitle = "Observation",
      formVersion = "1",
      collectorName = "Ada",
      collectorEmail = "ada@example.com",
      fields = emptyList(),
      location = LatLng(6.69, -1.62),
    )

  private fun submissionReportText(strings: PdfReportStrings): String =
    RecordPdfReports.submissionReport(
        submission = standaloneObservation,
        surveyTitle = "Survey",
        entity = null,
        generatedAt = generatedAt,
        strings = strings,
      )
      .bytes
      .decodeToString()

  @Test
  fun englishReport_writesCoordinatesWithEnglishHemispheres() = runTest {
    val text = reportText(PdfReportStrings.load())

    assertTrue(text.contains(pdfLiteral("6.6900°N, 1.6200°W")), "Coordinates field")
    assertTrue(text.contains(pdfLiteral("Point • 6.6900°N, 1.6200°W")), "Geometry summary")
  }

  @Test
  fun localizedReport_writesCoordinatesWithItsHemispheres() = runTest {
    val spanish =
      PdfReportStrings.load()
        .copy(
          geometryPoint = "Punto • %1\$s",
          longitudeWest = "%1\$s°O",
        )

    val text = reportText(spanish)

    assertTrue(text.contains(pdfLiteral("6.6900°N, 1.6200°O")), "Coordinates field")
    assertTrue(text.contains(pdfLiteral("Punto • 6.6900°N, 1.6200°O")), "Geometry summary")
  }

  @Test
  fun standaloneSubmissionReport_writesItsLocationWithTheReportsHemispheres() = runTest {
    val spanish =
      PdfReportStrings.load()
        .copy(
          noAttachedFeatureAt = "Sin entidad del mapa (%1\$s)",
          longitudeWest = "%1\$s°O",
        )

    val text = submissionReportText(spanish)

    assertTrue(text.contains(pdfLiteral("Sin entidad del mapa (6.6900°N, 1.6200°O)")), "Target")
  }
}
