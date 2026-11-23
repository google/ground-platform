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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.MeasurementUnitSystem

class WhispPdfReportTest {

  @Test
  fun entityReport_rendersStructuredWhispSectionAndYearlyChartInsteadOfRawWhispKeys() = runTest {
    val entity =
      GeospatialEntityItem(
        id = "p1",
        datasetId = "plots",
        datasetName = "Cocoa Plots",
        layerId = "plots",
        label = "Parcel A",
        geoId = "S2-abc",
        geometry = null,
        areaHectares = 4.2,
        perimeterMeters = 800,
        colorHex = 0xFF2E7D32,
        submissions = emptyList(),
        properties =
          linkedMapOf(
            "farmer_name" to "Ada",
            "whisp_Country" to "BRA",
            "whisp_Unit" to "percent",
            "whisp_risk_pcrop" to "low",
            "whisp_risk_acrop" to "high",
            "whisp_GFC_loss_year_2019" to "0",
            "whisp_GFC_loss_year_2020" to "2.0",
            "whisp_GFC_loss_year_2021" to "14.5",
            "whisp_MODIS_fire_2019" to "0",
            "whisp_MODIS_fire_2020" to "0",
            "whisp_MODIS_fire_2021" to "6.0",
            "whisp_Ind_01_treecover" to "yes",
            "whisp_EUFO_2020" to "75.0",
            "whisp_Cocoa_FDaP" to "18.0",
          ),
      )

    val pdf =
      RecordPdfReports.entityReport(
        entity = entity,
        surveyTitle = "EUDR Pilot",
        geometry = null,
        unitSystem = MeasurementUnitSystem.METRIC,
        generatedAt = Instant.fromEpochSeconds(1_760_000_000L),
      )
    val content = pdf.bytes.decodeToString()

    assertTrue(content.contains(pdfLiteral("Risk analysis (Open Foris Whisp)")))
    assertTrue(content.contains(pdfLiteral("Perennial crops: Low risk")))
    assertTrue(content.contains(pdfLiteral("Annual crops: High risk")))
    assertTrue(content.contains(pdfLiteral("Annual disturbance & loss")))
    assertTrue(content.contains(pdfLiteral("GFC tree cover loss")))
    assertTrue(content.contains(pdfLiteral("MODIS fire")))
    assertTrue(content.contains(pdfLiteral("2020 cutoff")))
    assertTrue(content.contains(pdfLiteral("farmer_name")))
    // Raw whisp_ keys should not be dumped directly into the PDF.
    assertFalse(content.contains("whisp_GFC_loss_year_2021"))
  }
}
