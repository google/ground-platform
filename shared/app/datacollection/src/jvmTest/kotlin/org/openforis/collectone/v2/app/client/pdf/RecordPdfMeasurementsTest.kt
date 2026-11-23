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
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.LatLng

/**
 * The lengths and areas printed in an entity report's geometry summary.
 *
 * JVM-only: [PdfReportStrings.load] reads Compose string resources, which on Android need
 * `android.content.res.Resources`, a stub that throws in Android unit tests.
 */
class RecordPdfMeasurementsTest {
  private val plot =
    GeospatialEntityItem(
      id = "plot-1",
      label = "Plot 1",
      datasetId = "plots",
      datasetName = "Plots",
      layerId = "layer-plots",
      geoId = "",
      geometry =
        Geometry.Polygon(
          listOf(
            listOf(
              LatLng(-0.4, 36.9),
              LatLng(-0.4, 36.91),
              LatLng(-0.41, 36.91),
              LatLng(-0.4, 36.9),
            )
          )
        ),
      areaHectares = 1.42,
      perimeterMeters = 480,
      colorHex = 0xFF2E7D32L,
      properties = emptyMap(),
      submissions = emptyList(),
    )

  private fun reportText(unitSystem: MeasurementUnitSystem, strings: PdfReportStrings): String =
    RecordPdfReports.entityReport(
        entity = plot,
        surveyTitle = "Survey",
        geometry = plot.geometry,
        unitSystem = unitSystem,
        generatedAt = Instant.parse("2026-09-21T14:13:00Z"),
        strings = strings,
      )
      .bytes
      .decodeToString()

  @Test
  fun metricReport_printsHectaresAndMeters() = runTest {
    val text = reportText(MeasurementUnitSystem.METRIC, PdfReportStrings.load())

    assertTrue(text.contains(pdfLiteral("Polygon • 1.42 ha, 480 m perimeter")), "Geometry summary")
  }

  @Test
  fun imperialReport_printsAcresAndFeet() = runTest {
    val text = reportText(MeasurementUnitSystem.IMPERIAL, PdfReportStrings.load())

    assertTrue(
      text.contains(pdfLiteral("Polygon • 3.51 acres, 1575 ft perimeter")),
      "Geometry summary",
    )
  }
}
