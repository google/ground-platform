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

  private fun buildTables(selectedEntityId: String? = entity.id) =
    buildDashboardDataTables(
      entities = state.entities,
      selectedEntityId = selectedEntityId,
      relatedLabel = { e, value -> state.relatedEntityForPropertyValue(e, value)?.label },
    )

  private fun layerTable() = buildTables().first { it.datasetId == entity.datasetId }

  @Test
  fun fromHash_resolvesDashboardPage() {
    assertEquals(
      PrototypeWorkbenchPage.WEB_DASHBOARD,
      PrototypeWorkbenchPage.fromHash("#dashboard"),
    )
    assertEquals(
      PrototypeWorkbenchPage.WEB_DASHBOARD,
      PrototypeWorkbenchPage.fromHash("#web"),
    )
  }

  @Test
  fun webAppPages_combineDashboardAndSurveyEditorUnderWebTopBarTab() {
    assertEquals(
      listOf(PrototypeWorkbenchPage.MOBILE_PROTOTYPE, PrototypeWorkbenchPage.WEB_DASHBOARD),
      PrototypeWorkbenchPage.topBarPages,
    )
    assertFalse(PrototypeWorkbenchPage.MOBILE_PROTOTYPE.isWebApp)
    assertTrue(PrototypeWorkbenchPage.WEB_DASHBOARD.isWebApp)
    assertTrue(PrototypeWorkbenchPage.SURVEY_EDITOR.isWebApp)
  }

  @Test
  fun updateActiveSurveyDetails_updatesActiveSurveyTitleAndDescription() {
    val testState = PrototypeAppState()
    testState.updateActiveSurveyDetails(
      title = "Updated Survey Title",
      description = "Updated survey description from the survey editor.",
    )

    assertEquals("Updated Survey Title", testState.activeSurvey.title)
    assertEquals(
      "Updated survey description from the survey editor.",
      testState.activeSurvey.description,
    )
  }

  @Test
  fun mapLayerTable_listsDatasetFeaturesAndHighlightsSelection() {
    val layerTable = layerTable()

    assertEquals(DashboardDataTableKind.MAP_LAYER, layerTable.kind)
    assertEquals(entity.datasetName, layerTable.title)
    assertEquals(datasetEntities.map { it.id }, layerTable.rows.map { it.id })
    assertEquals(datasetEntities.indexOf(entity), layerTable.selectedRowIndex)
    assertTrue(layerTable.rows.all { it.cells.size == layerTable.columns.size })
  }

  @Test
  fun mapLayerTable_omitsSimpleStyleProperties() {
    val columns = layerTable().columns

    assertFalse("marker-color" in columns)
    assertFalse("marker-symbol" in columns)
    assertFalse("stroke" in columns)
    assertFalse("fill" in columns)
  }

  @Test
  fun dataTables_oneTablePerDatasetAndNoSubmissionTables() {
    val tables = buildTables()

    assertEquals(state.entities.map { it.datasetId }.distinct(), tables.map { it.datasetId })
    assertTrue(tables.all { it.kind == DashboardDataTableKind.MAP_LAYER })
    assertEquals(state.entities.size, tables.sumOf { it.rows.size })
    tables.forEach { table -> assertTrue(table.rows.all { it.cells.size == table.columns.size }) }
  }

  @Test
  fun dataTables_highlightOnlyTheSelectedEntity() {
    assertEquals(1, buildTables().count { it.selectedRowIndex >= 0 })
    assertTrue(buildTables(selectedEntityId = null).all { it.selectedRowIndex == -1 })
  }

  @Test
  fun dataTables_recordsWithoutGeometryAreDataTables() {
    val record = entity.copy(id = "record-1", datasetId = "farmers", geometryTypeLabel = "")

    val table = buildDashboardDataTables(listOf(record), selectedEntityId = null).single()

    assertEquals(DashboardDataTableKind.DATA_TABLE, table.kind)
  }

  @Test
  fun dataTables_showRelatedRecordLabelsInsteadOfIds() {
    val parcel = state.entities.first { it.properties["Washing Station"] != null }
    val station = state.entities.first { it.id == parcel.properties["Washing Station"] }
    val table = buildTables().first { it.datasetId == parcel.datasetId }
    val column = table.columns.indexOf("Washing Station")

    assertEquals(station.label, table.rows.first { it.id == parcel.id }.cells[column])
  }

  @Test
  fun dashboardTable_staysCollapsedOnSelectionAndExpandsOnShowInTable() {
    val testState = PrototypeAppState()
    assertFalse(testState.isDashboardTableExpanded)

    testState.selectEntity(entity.id)
    assertFalse(testState.isDashboardTableExpanded)
    assertEquals(entity.datasetId, testState.dashboardTableDatasetId)

    testState.showSelectedEntityInTable()
    assertTrue(testState.isDashboardTableExpanded)
    assertEquals(entity.datasetId, testState.dashboardTableDatasetId)

    // Once open, the table stays open and follows the selection to other datasets.
    val other = testState.entities.first { it.datasetId != entity.datasetId }
    testState.selectEntity(other.id)
    assertTrue(testState.isDashboardTableExpanded)
    assertEquals(other.datasetId, testState.dashboardTableDatasetId)

    testState.toggleDashboardTableExpanded()
    assertFalse(testState.isDashboardTableExpanded)
  }

  @Test
  fun recenterMapOnEntity_adjustsTargetScreenYForReducedViewport() {
    val testState = PrototypeAppState()
    testState.selectEntity(entity.id)
    assertEquals(entity.id, testState.selectedEntityId)

    val expandedTableHeightDp = 336f
    val tabsHeightDp = 48f
    val totalHeightDp = 800f
    val tablePanelHeight = expandedTableHeightDp + tabsHeightDp
    val visibleViewportHeight = totalHeightDp - tablePanelHeight
    val targetScreenY = (visibleViewportHeight / 2) / totalHeightDp

    testState.recenterMapOnEntity(entity, targetScreenY)
    assertEquals(0.50f, entity.normalizedX + testState.mapWorldToScreenShiftX, 0.0001f)
    assertEquals(targetScreenY, entity.normalizedY + testState.mapWorldToScreenShiftY, 0.0001f)
  }

  @Test
  fun sidePanel_startsExpandedByDefault() {
    val testState = PrototypeAppState()
    assertTrue(testState.isSidePanelExpanded)
    assertTrue(testState.isDashboardSidePanelExpanded)
  }

  @Test
  fun toggleSidePanel_togglesBetweenExpandedAndCollapsed() {
    val testState = PrototypeAppState()
    assertTrue(testState.isSidePanelExpanded)

    testState.toggleSidePanel()
    assertFalse(testState.isSidePanelExpanded)
    assertFalse(testState.isDashboardSidePanelExpanded)

    testState.toggleDashboardSidePanel()
    assertTrue(testState.isSidePanelExpanded)
    assertTrue(testState.isDashboardSidePanelExpanded)
  }

  @Test
  fun collapseAndExpandSidePanel_updatesStateExplicitly() {
    val testState = PrototypeAppState()

    testState.collapseSidePanel()
    assertFalse(testState.isSidePanelExpanded)

    testState.expandSidePanel()
    assertTrue(testState.isSidePanelExpanded)

    testState.collapseDashboardSidePanel()
    assertFalse(testState.isDashboardSidePanelExpanded)

    testState.expandDashboardSidePanel()
    assertTrue(testState.isDashboardSidePanelExpanded)

    testState.updateSidePanelExpanded(false)
    assertFalse(testState.isSidePanelExpanded)

    testState.updateSidePanelExpanded(true)
    assertTrue(testState.isSidePanelExpanded)

    testState.updateDashboardSidePanelExpanded(false)
    assertFalse(testState.isDashboardSidePanelExpanded)

    testState.updateDashboardSidePanelExpanded(true)
    assertTrue(testState.isDashboardSidePanelExpanded)
  }

  @Test
  fun resetPrototypeFlow_resetsSidePanelToExpanded() {
    val testState = PrototypeAppState()
    testState.collapseSidePanel()
    assertFalse(testState.isSidePanelExpanded)

    testState.resetPrototypeFlow()
    assertTrue(testState.isSidePanelExpanded)
  }

  @Test
  fun signedInUserInitials_derivesTwoLetterInitialsCorrectly() {
    val testState = PrototypeAppState()
    assertEquals("ML", testState.signedInUserInitials)
  }

  @Test
  fun signOut_resetsAuthenticationAndNavigatesToSignIn() {
    val testState = PrototypeAppState()
    testState.signInWithGoogle()
    testState.acceptTermsOfService()
    testState.openSurvey(testState.surveys.first().id)
    assertTrue(testState.isSignedIn)
    assertEquals(PrototypeScreen.MAIN_SURVEY, testState.currentScreen)

    testState.signOut()
    assertFalse(testState.isSignedIn)
    assertEquals(PrototypeScreen.SIGN_IN, testState.currentScreen)
    assertFalse(testState.isDrawerOpen)
    assertEquals(MainDrawerSubView.NONE, testState.activeDrawerSubView)
  }

  @Test
  fun layersSheet_togglesStateAndControlsBasemapAndLayerVisibility() {
    val testState = PrototypeAppState()
    assertFalse(testState.isLayersSheetOpen)

    testState.updateLayersSheetOpen(true)
    assertTrue(testState.isLayersSheetOpen)

    testState.selectBasemapType(BasemapType.SATELLITE)
    assertEquals(BasemapType.SATELLITE, testState.selectedBasemapType)

    val initialOfflineVisibility = testState.isOfflineBasemapVisible
    testState.toggleOfflineBasemapVisibility()
    assertEquals(!initialOfflineVisibility, testState.isOfflineBasemapVisible)

    val firstLayer = testState.entityDatasetLayers.first()
    val initialLayerVisibility = firstLayer.isVisible
    testState.toggleLayerVisibility(firstLayer.id)
    assertEquals(
      !initialLayerVisibility,
      testState.entityDatasetLayers.first { it.id == firstLayer.id }.isVisible
    )

    testState.updateLayersSheetOpen(false)
    assertFalse(testState.isLayersSheetOpen)
  }

  @Test
  fun detailsPanel_startsExpandedByDefault() {
    val testState = PrototypeAppState()
    assertTrue(testState.isDetailsPanelExpanded)
    assertTrue(testState.isDashboardDetailsPanelExpanded)
    assertTrue(testState.isRightPanelExpanded)
  }

  @Test
  fun toggleDetailsPanel_togglesBetweenExpandedAndCollapsed() {
    val testState = PrototypeAppState()
    assertTrue(testState.isDetailsPanelExpanded)

    testState.toggleDetailsPanel()
    assertFalse(testState.isDetailsPanelExpanded)
    assertFalse(testState.isDashboardDetailsPanelExpanded)
    assertFalse(testState.isRightPanelExpanded)

    testState.toggleDashboardDetailsPanel()
    assertTrue(testState.isDetailsPanelExpanded)

    testState.collapseDetailsPanel()
    assertFalse(testState.isDetailsPanelExpanded)

    testState.expandDetailsPanel()
    assertTrue(testState.isDetailsPanelExpanded)

    testState.updateDetailsPanelExpanded(false)
    assertFalse(testState.isDetailsPanelExpanded)

    testState.updateDetailsPanelExpanded(true)
    assertTrue(testState.isDetailsPanelExpanded)

    testState.toggleRightPanel()
    assertFalse(testState.isRightPanelExpanded)

    testState.expandRightPanel()
    assertTrue(testState.isRightPanelExpanded)

    testState.collapseRightPanel()
    assertFalse(testState.isRightPanelExpanded)

    testState.updateRightPanelExpanded(true)
    assertTrue(testState.isRightPanelExpanded)
  }

  @Test
  fun openingTable_collapsesDetailsPanel() {
    val testState = PrototypeAppState()
    testState.selectEntity(entity.id)
    assertTrue(testState.isDetailsPanelExpanded)
    assertFalse(testState.isDashboardTableExpanded)

    testState.showSelectedEntityInTable()
    assertTrue(testState.isDashboardTableExpanded)
    assertFalse(testState.isDetailsPanelExpanded)

    // Collapsing the data table restores the details panel for the selected entity
    testState.toggleDashboardTableExpanded()
    assertFalse(testState.isDashboardTableExpanded)
    assertTrue(testState.isDetailsPanelExpanded)

    // Expanding via updateDashboardTableExpanded also collapses details panel
    testState.updateDashboardTableExpanded(true)
    assertTrue(testState.isDashboardTableExpanded)
    assertFalse(testState.isDetailsPanelExpanded)
  }

  @Test
  fun selectingEntityWhileTableIsOpen_keepsDetailsPanelCollapsed() {
    val testState = PrototypeAppState()
    testState.updateDashboardTableExpanded(true)
    assertTrue(testState.isDashboardTableExpanded)
    assertFalse(testState.isDetailsPanelExpanded)

    testState.selectEntity(entity.id)
    assertEquals(entity.id, testState.selectedEntityId)
    assertFalse(testState.isDetailsPanelExpanded)

    // Closing the table restores the panel
    testState.updateDashboardTableExpanded(false)
    assertTrue(testState.isDetailsPanelExpanded)
  }

  @Test
  fun resetPrototypeFlow_resetsDetailsPanelToExpanded() {
    val testState = PrototypeAppState()
    testState.collapseDetailsPanel()
    assertFalse(testState.isDetailsPanelExpanded)

    testState.resetPrototypeFlow()
    assertTrue(testState.isDetailsPanelExpanded)
  }

  @Test
  fun buildDashboardTableCsv_quotesFieldsPerRfc4180() {
    val table =
      DashboardDataTable(
        id = "dataset:test",
        datasetId = "test",
        title = "Test",
        kind = DashboardDataTableKind.DATA_TABLE,
        columns = listOf("Label", "Notes"),
        rows =
          listOf(
            DashboardDataTableRow(id = "1", cells = listOf("Plain", "a,b")),
            DashboardDataTableRow(id = "2", cells = listOf("Say \"hi\"", "line1\nline2")),
            DashboardDataTableRow(id = "3", cells = listOf("", "cr\rhere")),
          ),
      )

    assertEquals(
      "Label,Notes\r\n" +
        "Plain,\"a,b\"\r\n" +
        "\"Say \"\"hi\"\"\",\"line1\nline2\"\r\n" +
        ",\"cr\rhere\"\r\n",
      buildDashboardTableCsv(table),
    )
  }

  @Test
  fun buildDashboardTableCsv_includesHeaderAndEveryRow() {
    val table = layerTable()
    val lines = buildDashboardTableCsv(table).split("\r\n").filter { it.isNotEmpty() }

    assertTrue(lines.first().startsWith("Label,Submissions,Sync,GeoID"))
    assertFalse("Status" in table.columns)
    assertTrue(lines.size >= table.rows.size + 1)
  }

  @Test
  fun dashboardTableCsvFileName_sanitizesTitle() {
    val table = layerTable().copy(title = "Plots: A/B <2026>?")

    assertEquals("Plots_ A_B _2026__.csv", dashboardTableCsvFileName(table))
    assertEquals("table.csv", dashboardTableCsvFileName(table.copy(title = "  ")))
  }

  @Test
  fun buildDashboardLayerSummary_countsFeaturesPerStatus() {
    val summary = buildDashboardLayerSummary(state.entities, entity.datasetId)!!

    assertEquals(entity.datasetName, summary.title)
    assertEquals(datasetEntities.size, summary.featureCount)
    assertEquals(datasetEntities.size, summary.statusCounts.sumOf { it.count })
    assertEquals(
      datasetEntities.map { it.mapStatusSummaryBadge }.toSet(),
      summary.statusCounts.map { it.label }.toSet(),
    )
    assertEquals(
      summary.statusCounts.map { it.count }.sortedDescending(),
      summary.statusCounts.map { it.count },
    )
    assertEquals(null, buildDashboardLayerSummary(state.entities, "missing-dataset"))
  }

  @Test
  fun selectLayer_isMutuallyExclusiveWithEntitySelection() {
    val testState = PrototypeAppState()
    testState.selectEntity(entity.id)

    testState.selectLayer(entity.datasetId)
    assertEquals(entity.datasetId, testState.selectedLayerDatasetId)
    assertEquals(null, testState.selectedEntityId)
    assertEquals(entity.datasetId, testState.dashboardTableDatasetId)
    assertTrue(testState.isDetailsPanelExpanded)

    testState.selectEntity(entity.id)
    assertEquals(null, testState.selectedLayerDatasetId)
    assertEquals(entity.id, testState.selectedEntityId)
  }

  @Test
  fun showSelectedLayerInTable_expandsTableOnLayerDataset() {
    val testState = PrototypeAppState()
    testState.selectLayer(entity.datasetId)

    testState.showSelectedLayerInTable()
    assertTrue(testState.isDashboardTableExpanded)
    assertEquals(entity.datasetId, testState.dashboardTableDatasetId)
    assertFalse(testState.isDetailsPanelExpanded)

    testState.updateDashboardTableExpanded(false)
    assertTrue(testState.isDetailsPanelExpanded)
  }
}
