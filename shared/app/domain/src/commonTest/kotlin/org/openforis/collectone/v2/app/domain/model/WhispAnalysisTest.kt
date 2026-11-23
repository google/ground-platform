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
package org.openforis.collectone.v2.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WhispAnalysisTest {

  @Test
  fun parse_returnsNullWhenNoWhispPropertiesExist() {
    assertNull(WhispAnalysis.parse(mapOf("crop" to "coffee", "status" to "verified")))
  }

  @Test
  fun parse_classifiesRiskVerdictsContextYearlySeriesBeforeAfterIndicatorsAndThemes() {
    val props =
      linkedMapOf(
        "crop" to "cocoa",
        "whisp_Country" to "BRA",
        "whisp_ProducerCountry" to "BR",
        "whisp_Admin_Level_1" to "Para",
        "whisp_Area" to "4.25",
        "whisp_Unit" to "percent",
        "whisp_In_waterbody" to "false",
        "whisp_risk_pcrop" to "low",
        "whisp_risk_acrop" to "more_info_needed",
        "whisp_risk_timber" to "high",
        "whisp_Ind_01_treecover" to "yes",
        "whisp_Ind_04_disturbance_after_2020" to "yes",
        "whisp_GFC_loss_year_2019" to "0",
        "whisp_GFC_loss_year_2020" to "1.5",
        "whisp_GFC_loss_year_2021" to "12.4",
        "whisp_MODIS_fire_2019" to "0",
        "whisp_MODIS_fire_2020" to "0",
        "whisp_MODIS_fire_2021" to "5.0",
        "whisp_GFC_loss_before_2020" to "1.5",
        "whisp_GFC_loss_after_2020" to "12.4",
        "whisp_EUFO_2020" to "82.0",
        "whisp_GFC_TC_2020" to "78.5",
        "whisp_Cocoa_FDaP" to "14.2",
        "whisp_Custom_layer" to "3.0",
      )

    val analysis = assertNotNull(WhispAnalysis.parse(props))
    assertEquals(WhispRiskLevel.HIGH, analysis.overallRisk)
    assertEquals(3, analysis.riskVerdicts.size)
    assertEquals("Perennial crops", analysis.riskVerdicts[0].label)
    assertEquals(WhispRiskLevel.LOW, analysis.riskVerdicts[0].level)
    assertEquals("Annual crops", analysis.riskVerdicts[1].label)
    assertEquals(WhispRiskLevel.MORE_INFO_NEEDED, analysis.riskVerdicts[1].level)
    assertEquals("Timber", analysis.riskVerdicts[2].label)
    assertEquals(WhispRiskLevel.HIGH, analysis.riskVerdicts[2].level)

    assertEquals("%", analysis.unit)
    assertTrue(analysis.isPercentUnit)
    assertEquals("BRA", analysis.context.country)
    assertEquals("Para", analysis.context.adminLevel1)
    assertEquals("4.25", analysis.context.areaHa)
    assertEquals(false, analysis.context.inWaterbody)

    assertEquals(2, analysis.yearlySeries.size)
    val gfc = analysis.yearlySeries.first { it.key == "GFC_loss_year" }
    assertEquals("GFC tree cover loss", gfc.label)
    assertEquals(WhispYearlyCategory.FOREST_LOSS, gfc.category)
    assertEquals(listOf(2019 to 0.0, 2020 to 1.5, 2021 to 12.4), gfc.points)
    val fire = analysis.yearlySeries.first { it.key == "MODIS_fire" }
    assertEquals(WhispYearlyCategory.FIRE, fire.category)
    assertTrue(analysis.hasYearlyActivity)

    assertEquals(1, analysis.beforeAfterComparisons.size)
    assertEquals(1.5, analysis.beforeAfterComparisons[0].before2020)
    assertEquals(12.4, analysis.beforeAfterComparisons[0].after2020)

    assertEquals(2, analysis.indicators.size)
    assertEquals("Tree cover (2020)", analysis.indicators[0].label)
    assertEquals(true, analysis.indicators[0].isFlagged)

    assertEquals(listOf("EUFO_2020", "GFC_TC_2020"), analysis.treeCoverMetrics.map { it.key })
    assertEquals(listOf("Cocoa_FDaP"), analysis.commodityMetrics.map { it.key })
    assertEquals(listOf("Custom_layer"), analysis.otherMetrics.map { it.key })
    assertEquals(23, analysis.rawEntries.size)
    assertEquals("12.4%", analysis.formatMetricValue(12.4))
  }

  @Test
  fun singleYearSnapshotKeysAreNotTreatedAsYearlySeries() {
    val analysis =
      assertNotNull(
        WhispAnalysis.parse(
          mapOf("whisp_EUFO_2020" to "50", "whisp_Soy_Song_2020" to "10", "whisp_Unit" to "percent")
        )
      )
    assertTrue(analysis.yearlySeries.isEmpty())
    assertFalse(analysis.hasYearlyActivity)
    assertEquals(1, analysis.treeCoverMetrics.size)
    assertEquals(1, analysis.commodityMetrics.size)
  }
}
