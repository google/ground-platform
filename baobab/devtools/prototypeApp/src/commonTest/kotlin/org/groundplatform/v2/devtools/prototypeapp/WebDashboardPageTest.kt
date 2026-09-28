/*
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.groundplatform.v2.devtools.prototypeapp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebDashboardPageTest {

  private val state = PrototypeAppState()
  private val entity = state.entities.first { it.submissions.isNotEmpty() }
  private val datasetEntities = state.entities.filter { it.datasetId == entity.datasetId }

  private fun buildTables(selectedSubmissionId: String? = null) =
    buildDashboardDataTables(
      entity = entity,
      datasetEntities = datasetEntities,
      submissionGroups = state.groupedSubmissionsForEntity(entity),
      selectedSubmissionId = selectedSubmissionId,
    )

  @Test
  fun fromHash_resolvesDashboardPage() {
    assertEquals(
      PrototypeWorkbenchPage.WEB_DASHBOARD,
      PrototypeWorkbenchPage.fromHash("#dashboard"),
    )
  }

  @Test
  fun mapLayerTable_listsDatasetFeaturesAndHighlightsSelection() {
    val layerTable = buildTables().first()

    assertEquals(DashboardDataTableKind.MAP_LAYER, layerTable.kind)
    assertEquals(entity.datasetName, layerTable.title)
    assertEquals(datasetEntities.map { it.id }, layerTable.rows.map { it.id })
    assertEquals(datasetEntities.indexOf(entity), layerTable.selectedRowIndex)
    assertTrue(layerTable.rows.all { it.cells.size == layerTable.columns.size })
  }

  @Test
  fun mapLayerTable_omitsSimpleStyleProperties() {
    val columns = buildTables().first().columns

    assertFalse("marker-color" in columns)
    assertFalse("marker-symbol" in columns)
    assertFalse("stroke" in columns)
    assertFalse("fill" in columns)
  }

  @Test
  fun submissionTables_oneTablePerFormWithAlignedCells() {
    val submissionTables = buildTables().drop(1)
    val groups = state.groupedSubmissionsForEntity(entity)

    assertEquals(groups.size, submissionTables.size)
    submissionTables.zip(groups).forEach { (table, group) ->
      assertEquals(DashboardDataTableKind.FORM_SUBMISSIONS, table.kind)
      assertEquals(group.submissions.map { it.id }, table.rows.map { it.id })
      assertTrue(table.rows.all { it.cells.size == table.columns.size })
      assertEquals(-1, table.selectedRowIndex)
    }
  }

  @Test
  fun submissionTables_highlightSelectedSubmission() {
    val submission = entity.submissions.first()
    val table = buildTables(selectedSubmissionId = submission.id).first {
      it.id == "form:${submission.formId}"
    }

    assertEquals(submission.id, table.rows[table.selectedRowIndex].id)
  }
}
