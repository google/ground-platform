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
package org.openforis.collectone.v2.web.ui.dashboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem

class DashboardDataTablesWhispTest {

  private fun entityWithWhisp(id: String, riskPcrop: String): GeospatialEntityItem =
    GeospatialEntityItem(
      id = id,
      datasetId = "plots",
      datasetName = "Plots",
      layerId = "plots",
      label = "Plot $id",
      geoId = "GEO-$id",
      geometry = null,
      areaHectares = 1.0,
      perimeterMeters = 400,
      colorHex = 0xFF2E7D32,
      submissions = emptyList(),
      properties =
        linkedMapOf(
          "crop" to "cocoa",
          "whisp_Country" to "BRA",
          "whisp_risk_pcrop" to riskPcrop,
          "whisp_GFC_loss_year_2020" to "0",
          "whisp_GFC_loss_year_2021" to "4.2",
        ),
    )

  @Test
  fun buildDashboardDataTables_rollsUpWhispColumnsByDefault_andKeepsAllColumnsInCsv() {
    val entities = listOf(entityWithWhisp("1", "low"), entityWithWhisp("2", "high"))

    val rolledUp = buildDashboardDataTables(entities = entities, selectedEntityId = null).single()
    assertTrue(rolledUp.hasWhispColumns)
    assertEquals(4, rolledUp.whispColumnCount)
    assertFalse(rolledUp.isWhispExpanded)
    assertEquals(
      listOf("Status", "Label", "Submissions", "GeoID", "Risk analysis", "crop"),
      rolledUp.columns,
    )
    assertEquals("Low risk", rolledUp.rows[0].cells[4])
    assertEquals("High risk", rolledUp.rows[1].cells[4])

    // CSV export must always contain every individual whisp_* column even when rolled up.
    val csv = buildDashboardTableCsv(rolledUp)
    assertTrue(csv.contains("whisp_Country"))
    assertTrue(csv.contains("whisp_risk_pcrop"))
    assertTrue(csv.contains("whisp_GFC_loss_year_2021"))
    assertTrue(csv.contains("4.2"))
  }

  @Test
  fun buildDashboardDataTables_expandsWhispColumnsWhenRequested() {
    val entities = listOf(entityWithWhisp("1", "more_info_needed"))

    val expanded =
      buildDashboardDataTables(
          entities = entities,
          selectedEntityId = null,
          expandWhispColumns = true,
        )
        .single()
    assertTrue(expanded.isWhispExpanded)
    assertFalse("Risk analysis" in expanded.columns)
    assertTrue("whisp_Country" in expanded.columns)
    assertTrue("whisp_risk_pcrop" in expanded.columns)
    assertTrue("whisp_GFC_loss_year_2021" in expanded.columns)
  }
}
