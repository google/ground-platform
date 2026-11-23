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

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.MeasurementUnitSystem
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.LatLng

/** An entity report names its units in the language selected when it's generated. */
class RecordPdfUnitsLanguageTest {
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

  private fun imperialReportIn(languageTag: String): String {
    // On the JVM the app locale is the default locale, which the other tests expect in English.
    val previous = Locale.getDefault()
    Locale.setDefault(Locale.forLanguageTag(languageTag))
    return try {
      runBlocking {
        RecordPdfReports.entityReport(
          entity = plot,
          surveyTitle = "Survey",
          geometry = plot.geometry,
          unitSystem = MeasurementUnitSystem.IMPERIAL,
          generatedAt = Instant.parse("2026-09-21T14:13:00Z"),
        )
      }
        .bytes
        .decodeToString()
    } finally {
      Locale.setDefault(previous)
    }
  }

  @Test
  fun spanishReport_namesItsUnitsInSpanish() {
    val text = imperialReportIn("es")

    assertTrue(
      text.contains(pdfLiteral("Polígono • 3.51 acres, 1575 pies de perímetro")),
      "Geometry summary",
    )
    assertFalse(text.contains(pdfLiteral("1575 ft")), "No English unit left")
  }
}
