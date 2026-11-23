/*
 * Copyright 2026 The Collect One Authors.
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
package org.openforis.collectone.v2.web.ui.dashboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.domain.model.BasemapType
import org.openforis.collectone.v2.app.domain.model.relatedEntityForPropertyValue
import org.openforis.collectone.v2.app.ui.session.searchableListUiState
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.web.ui.WebAppState
import org.openforis.collectone.v2.web.ui.surveyRecords

class WebDashboardPageTest {

  private val state = WebAppState(dataHolder = sampleDataHolder())
  private val entity =
    state.viewModels.dataCollection.uiState.value.entities.first { it.submissions.isNotEmpty() }
  private val datasetEntities =
    state.viewModels.dataCollection.uiState.value.entities.filter {
      it.datasetId == entity.datasetId
    }

  private fun buildTables(selectedEntityId: String? = entity.id) =
    buildDashboardDataTables(
      entities = state.viewModels.dataCollection.uiState.value.entities,
      selectedEntityId = selectedEntityId,
      relatedLabel = { e, value ->
        state.viewModels.dashboard.uiState.value.relatedEntityForPropertyValue(e, value)?.label
      },
    )

  private fun layerTable() = buildTables().first { it.datasetId == entity.datasetId }

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

    assertEquals(
      state.viewModels.dataCollection.uiState.value.entities.map { it.datasetId }.distinct(),
      tables.map { it.datasetId },
    )
    assertTrue(tables.all { it.kind == DashboardDataTableKind.MAP_LAYER })
    assertEquals(
      state.viewModels.dataCollection.uiState.value.entities.size,
      tables.sumOf { it.rows.size },
    )
    tables.forEach { table -> assertTrue(table.rows.all { it.cells.size == table.columns.size }) }
  }

  @Test
  fun dataTables_highlightOnlyTheSelectedEntity() {
    assertEquals(1, buildTables().count { it.selectedRowIndex >= 0 })
    assertTrue(buildTables(selectedEntityId = null).all { it.selectedRowIndex == -1 })
  }

  @Test
  fun dataTables_recordsWithoutGeometryAreDataTables() {
    val record = entity.copy(id = "record-1", datasetId = "farmers", geometry = null)

    val table = buildDashboardDataTables(listOf(record), selectedEntityId = null).single()

    assertEquals(DashboardDataTableKind.DATA_TABLE, table.kind)
  }

  @Test
  fun dataTables_showRelatedRecordLabelsInsteadOfIds() {
    val parcel =
      state.viewModels.dataCollection.uiState.value.entities.first {
        it.properties["Washing Station"] != null
      }
    val station =
      state.viewModels.dataCollection.uiState.value.entities.first {
        it.id == parcel.properties["Washing Station"]
      }
    val table = buildTables().first { it.datasetId == parcel.datasetId }
    val column = table.columns.indexOf("Washing Station")

    assertEquals(station.label, table.rows.first { it.id == parcel.id }.cells[column])
  }

  @Test
  fun dashboardTable_staysCollapsedOnSelectionAndExpandsOnShowInTable() {
    val testState = WebAppState(dataHolder = sampleDataHolder())
    assertFalse(testState.viewModels.dashboard.uiState.value.isDashboardTableExpanded)

    testState.viewModels.surveyMap.selectEntity(entity.id)
    assertFalse(testState.viewModels.dashboard.uiState.value.isDashboardTableExpanded)
    assertEquals(
      entity.datasetId,
      testState.viewModels.dashboard.uiState.value.dashboardTableDatasetId,
    )

    testState.viewModels.dashboard.showDatasetInTable(
      checkNotNull(testState.viewModels.dataCollection.uiState.value.selectedEntity).datasetId
    )
    assertTrue(testState.viewModels.dashboard.uiState.value.isDashboardTableExpanded)
    assertEquals(
      entity.datasetId,
      testState.viewModels.dashboard.uiState.value.dashboardTableDatasetId,
    )

    // Selecting another entity opens its details card, docks the table, and switches the table's
    // active dataset to match.
    val other =
      testState.viewModels.dataCollection.uiState.value.entities.first {
        it.datasetId != entity.datasetId
      }
    testState.viewModels.surveyMap.selectEntity(other.id)
    assertFalse(testState.viewModels.dashboard.uiState.value.isDashboardTableExpanded)
    assertTrue(testState.viewModels.dashboard.uiState.value.isDetailsPanelExpanded)
    assertEquals(
      other.datasetId,
      testState.viewModels.dashboard.uiState.value.dashboardTableDatasetId,
    )

    testState.viewModels.dashboard.toggleDashboardTableExpanded()
    assertTrue(testState.viewModels.dashboard.uiState.value.isDashboardTableExpanded)
    assertFalse(testState.viewModels.dashboard.uiState.value.isDetailsPanelExpanded)
    testState.viewModels.dashboard.toggleDashboardTableExpanded()
    assertFalse(testState.viewModels.dashboard.uiState.value.isDashboardTableExpanded)
  }

  @Test
  fun recenterMapOnEntity_adjustsTargetScreenYForReducedViewport() {
    val testState = WebAppState(dataHolder = sampleDataHolder())
    testState.viewModels.surveyMap.selectEntity(entity.id)
    assertEquals(entity.id, testState.viewModels.dataCollection.uiState.value.selectedEntityId)

    val expandedTableHeightDp = 336f
    val tabsHeightDp = 48f
    val totalHeightDp = 800f
    val tablePanelHeight = expandedTableHeightDp + tabsHeightDp
    val visibleViewportHeight = totalHeightDp - tablePanelHeight
    val targetScreenY = (visibleViewportHeight / 2) / totalHeightDp

    testState.viewModels.surveyMap.recenterMapOnEntity(entity, targetScreenY)
    val (lng, lat) = testState.viewModels.surveyMap.resolveEntityLngLat(entity)
    val (entityX, entityY) =
      testState.viewModels.surveyMap.uiState.value.anchor.positionOf(LatLng(lat, lng))
    assertEquals(
      0.50f,
      entityX + testState.viewModels.surveyMap.uiState.value.mapWorldToScreenShiftX,
      0.0001f,
    )
    assertEquals(
      targetScreenY,
      entityY + testState.viewModels.surveyMap.uiState.value.mapWorldToScreenShiftY,
      0.0001f,
    )
  }

  @Test
  fun sidePanel_startsExpandedByDefault() {
    val testState = WebAppState(dataHolder = sampleDataHolder())
    assertTrue(testState.viewModels.dashboard.uiState.value.isSidePanelExpanded)
  }

  @Test
  fun toggleSidePanel_togglesBetweenExpandedAndCollapsed() {
    val testState = WebAppState(dataHolder = sampleDataHolder())
    assertTrue(testState.viewModels.dashboard.uiState.value.isSidePanelExpanded)

    testState.viewModels.dashboard.toggleSidePanel()
    assertFalse(testState.viewModels.dashboard.uiState.value.isSidePanelExpanded)

    testState.viewModels.dashboard.toggleSidePanel()
    assertTrue(testState.viewModels.dashboard.uiState.value.isSidePanelExpanded)
  }

  @Test
  fun collapseAndExpandSidePanel_updatesStateExplicitly() {
    val testState = WebAppState(dataHolder = sampleDataHolder())

    testState.viewModels.dashboard.collapseSidePanel()
    assertFalse(testState.viewModels.dashboard.uiState.value.isSidePanelExpanded)

    testState.viewModels.dashboard.expandSidePanel()
    assertTrue(testState.viewModels.dashboard.uiState.value.isSidePanelExpanded)

    testState.viewModels.dashboard.updateSidePanelExpanded(false)
    assertFalse(testState.viewModels.dashboard.uiState.value.isSidePanelExpanded)

    testState.viewModels.dashboard.updateSidePanelExpanded(true)
    assertTrue(testState.viewModels.dashboard.uiState.value.isSidePanelExpanded)
  }

  @Test
  fun reset_resetsSidePanelToExpanded() {
    val testState = WebAppState(dataHolder = sampleDataHolder())
    testState.viewModels.dashboard.collapseSidePanel()
    assertFalse(testState.viewModels.dashboard.uiState.value.isSidePanelExpanded)

    testState.reset()
    assertTrue(testState.viewModels.dashboard.uiState.value.isSidePanelExpanded)
  }

  @Test
  fun signedInUserInitials_derivesTwoLetterInitialsCorrectly() {
    val testState = WebAppState(dataHolder = sampleDataHolder())
    assertEquals("ML", testState.viewModels.onboarding.uiState.value.profile.initials)
  }

  @Test
  fun signOut_resetsAuthentication() {
    val testState = WebAppState(dataHolder = sampleDataHolder())
    testState.viewModels.onboarding.signInWithGoogle(ignoreOffline = true)
    testState.viewModels.onboarding.acceptTermsOfService()
    assertTrue(testState.viewModels.onboarding.uiState.value.isSignedIn)

    testState.signOut()
    assertFalse(testState.viewModels.onboarding.uiState.value.isSignedIn)
  }

  @Test
  fun languageSelector_updatesSettingsLanguageAndLocale() {
    val testState = WebAppState(dataHolder = sampleDataHolder())
    assertEquals("en", testState.viewModels.settings.uiState.value.languageCode)
    testState.viewModels.settings.updateLanguage("es")
    assertEquals("es", testState.viewModels.settings.uiState.value.languageCode)
    assertEquals("Español", testState.viewModels.settings.uiState.value.selectedLanguageDisplayName)
  }

  @Test
  fun layersSheet_togglesStateAndControlsBasemapAndLayerVisibility() {
    val testState = WebAppState(dataHolder = sampleDataHolder())
    assertFalse(testState.viewModels.surveyMap.uiState.value.isLayersSheetOpen)

    testState.viewModels.surveyMap.updateLayersSheetOpen(true)
    assertTrue(testState.viewModels.surveyMap.uiState.value.isLayersSheetOpen)

    testState.viewModels.surveyMap.selectBasemapType(BasemapType.SATELLITE)
    assertEquals(
      BasemapType.SATELLITE,
      testState.viewModels.surveyMap.uiState.value.selectedBasemapType,
    )

    val firstLayer = testState.viewModels.dashboard.uiState.value.entityDatasetLayers.first()
    val initialLayerVisibility = firstLayer.isVisible
    testState.searchableListActions.toggleLayerVisibility(firstLayer.id)
    assertEquals(
      !initialLayerVisibility,
      testState.viewModels.dashboard.uiState.value.entityDatasetLayers
        .first { it.id == firstLayer.id }
        .isVisible,
    )

    testState.viewModels.surveyMap.updateLayersSheetOpen(false)
    assertFalse(testState.viewModels.surveyMap.uiState.value.isLayersSheetOpen)
  }

  @Test
  fun detailsPanel_startsExpandedByDefault() {
    val testState = WebAppState(dataHolder = sampleDataHolder())
    assertTrue(testState.viewModels.dashboard.uiState.value.isDetailsPanelExpanded)
  }

  @Test
  fun toggleDetailsPanel_togglesBetweenExpandedAndCollapsed() {
    val testState = WebAppState(dataHolder = sampleDataHolder())
    assertTrue(testState.viewModels.dashboard.uiState.value.isDetailsPanelExpanded)

    testState.viewModels.dashboard.toggleDetailsPanel()
    assertFalse(testState.viewModels.dashboard.uiState.value.isDetailsPanelExpanded)

    testState.viewModels.dashboard.toggleDetailsPanel()
    assertTrue(testState.viewModels.dashboard.uiState.value.isDetailsPanelExpanded)

    testState.viewModels.dashboard.collapseDetailsPanel()
    assertFalse(testState.viewModels.dashboard.uiState.value.isDetailsPanelExpanded)

    testState.viewModels.dashboard.expandDetailsPanel()
    assertTrue(testState.viewModels.dashboard.uiState.value.isDetailsPanelExpanded)

    testState.viewModels.dashboard.updateDetailsPanelExpanded(false)
    assertFalse(testState.viewModels.dashboard.uiState.value.isDetailsPanelExpanded)

    testState.viewModels.dashboard.updateDetailsPanelExpanded(true)
    assertTrue(testState.viewModels.dashboard.uiState.value.isDetailsPanelExpanded)
  }

  @Test
  fun openingTable_docksDetailsPanel_andExpandingDetailsDocksTable() {
    val testState = WebAppState(dataHolder = sampleDataHolder())
    testState.viewModels.surveyMap.selectEntity(entity.id)
    assertTrue(testState.viewModels.dashboard.uiState.value.isDetailsPanelExpanded)
    assertFalse(testState.viewModels.dashboard.uiState.value.isDashboardTableExpanded)

    // Opening the bottom data table docks the right-hand details panel.
    testState.viewModels.dashboard.showDatasetInTable(
      checkNotNull(testState.viewModels.dataCollection.uiState.value.selectedEntity).datasetId
    )
    assertTrue(testState.viewModels.dashboard.uiState.value.isDashboardTableExpanded)
    assertFalse(testState.viewModels.dashboard.uiState.value.isDetailsPanelExpanded)

    // Expanding the details panel docks the bottom data table.
    testState.viewModels.dashboard.expandDetailsPanel()
    assertTrue(testState.viewModels.dashboard.uiState.value.isDetailsPanelExpanded)
    assertFalse(testState.viewModels.dashboard.uiState.value.isDashboardTableExpanded)

    // Closing the table leaves the details panel docked.
    testState.viewModels.dashboard.updateDashboardTableExpanded(true)
    assertFalse(testState.viewModels.dashboard.uiState.value.isDetailsPanelExpanded)
    testState.viewModels.dashboard.updateDashboardTableExpanded(false)
    assertFalse(testState.viewModels.dashboard.uiState.value.isDetailsPanelExpanded)
  }

  @Test
  fun selectingEntityWhileTableIsOpen_showsItsDetailsAndDocksTable() {
    val testState = WebAppState(dataHolder = sampleDataHolder())
    testState.viewModels.dashboard.updateDashboardTableExpanded(true)
    assertTrue(testState.viewModels.dashboard.uiState.value.isDashboardTableExpanded)
    assertFalse(testState.viewModels.dashboard.uiState.value.isDetailsPanelExpanded)

    testState.viewModels.surveyMap.selectEntity(entity.id)
    assertEquals(entity.id, testState.viewModels.dataCollection.uiState.value.selectedEntityId)
    assertTrue(testState.viewModels.dashboard.uiState.value.isDetailsPanelExpanded)
    assertFalse(testState.viewModels.dashboard.uiState.value.isDashboardTableExpanded)
  }

  @Test
  fun reset_resetsDetailsPanelToExpanded() {
    val testState = WebAppState(dataHolder = sampleDataHolder())
    testState.viewModels.dashboard.collapseDetailsPanel()
    assertFalse(testState.viewModels.dashboard.uiState.value.isDetailsPanelExpanded)

    testState.reset()
    assertTrue(testState.viewModels.dashboard.uiState.value.isDetailsPanelExpanded)
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

    assertTrue(lines.first().startsWith("Status,Label,Submissions,GeoID"))
    assertFalse("Sync" in table.columns)
    assertTrue(lines.size >= table.rows.size + 1)
  }

  @Test
  fun buildDashboardDataTables_statusIsFirstColumnAndHoldsWorkflowStatusAndRowEntity() {
    val table = layerTable()
    val statusIndex = table.columns.indexOf(DASHBOARD_STATUS_COLUMN)

    assertEquals(0, statusIndex)
    assertEquals(1, table.columns.indexOf(DASHBOARD_LABEL_COLUMN))
    table.rows.forEach { row ->
      val entity = assertNotNull(row.entity)
      assertEquals(row.id, entity.id)
      assertEquals(entity.workflowStatus, row.cells[statusIndex])
      assertEquals(entity.label, row.cells[1])
    }
  }

  @Test
  fun dashboardTableCsvFileName_sanitizesTitle() {
    val table = layerTable().copy(title = "Plots: A/B <2026>?")

    assertEquals("Plots_ A_B _2026__.csv", dashboardTableCsvFileName(table))
    assertEquals("table.csv", dashboardTableCsvFileName(table.copy(title = "  ")))
  }

  @Test
  fun buildDashboardLayerSummary_countsFeaturesPerStatus() {
    val summary =
      buildDashboardLayerSummary(
        state.viewModels.dataCollection.uiState.value.entities,
        entity.datasetId,
      )!!

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
    assertEquals(
      null,
      buildDashboardLayerSummary(
        state.viewModels.dataCollection.uiState.value.entities,
        "missing-dataset",
      ),
    )
  }

  @Test
  fun selectLayer_isMutuallyExclusiveWithEntitySelection() {
    val testState = WebAppState(dataHolder = sampleDataHolder())
    testState.viewModels.surveyMap.selectEntity(entity.id)

    testState.searchableListActions.selectLayer(entity.datasetId)
    assertEquals(
      entity.datasetId,
      testState.viewModels.dashboard.uiState.value.selectedLayerDatasetId,
    )
    assertEquals(null, testState.viewModels.dataCollection.uiState.value.selectedEntityId)
    assertEquals(
      entity.datasetId,
      testState.viewModels.dashboard.uiState.value.dashboardTableDatasetId,
    )
    assertTrue(testState.viewModels.dashboard.uiState.value.isDetailsPanelExpanded)

    testState.viewModels.surveyMap.selectEntity(entity.id)
    assertEquals(null, testState.viewModels.dashboard.uiState.value.selectedLayerDatasetId)
    assertEquals(entity.id, testState.viewModels.dataCollection.uiState.value.selectedEntityId)
  }

  @Test
  fun showSelectedLayerInTable_expandsTableOnLayerDatasetAndDocksDetails() {
    val testState = WebAppState(dataHolder = sampleDataHolder())
    testState.searchableListActions.selectLayer(entity.datasetId)

    // Opening the table docks the layer's details card; expanding details docks the table.
    testState.viewModels.dashboard.showSelectedLayerInTable()
    assertTrue(testState.viewModels.dashboard.uiState.value.isDashboardTableExpanded)
    assertEquals(
      entity.datasetId,
      testState.viewModels.dashboard.uiState.value.dashboardTableDatasetId,
    )
    assertFalse(testState.viewModels.dashboard.uiState.value.isDetailsPanelExpanded)

    testState.viewModels.dashboard.expandDetailsPanel()
    assertTrue(testState.viewModels.dashboard.uiState.value.isDetailsPanelExpanded)
    assertFalse(testState.viewModels.dashboard.uiState.value.isDashboardTableExpanded)
  }

  @Test
  fun dataTables_titlesNeverIncludeInternalDatasetIdsAcrossAllSurveys() {
    val testState = WebAppState(dataHolder = sampleDataHolder())
    for (survey in testState.viewModels.dashboard.uiState.value.surveys) {
      testState.viewModels.onboarding.openSurvey(survey.id)
      val tables =
        buildDashboardDataTables(
          entities = testState.viewModels.dataCollection.uiState.value.entities,
          selectedEntityId = null,
          relatedLabel = { e, value ->
            testState.viewModels.dashboard.uiState.value
              .relatedEntityForPropertyValue(e, value)
              ?.label
          },
        )
      tables.forEach { table ->
        assertFalse(
          "(${table.datasetId})" in table.title,
          "Data table title '${table.title}' for survey '${survey.id}' should not include internal dataset ID '${table.datasetId}'",
        )
      }
      testState.viewModels.dataCollection.uiState.value.entities.forEach { surveyEntity ->
        assertFalse(
          "(${surveyEntity.datasetId})" in surveyEntity.datasetName,
          "Entity datasetName '${surveyEntity.datasetName}' should not include internal datasetId '${surveyEntity.datasetId}'",
        )
      }
      testState.viewModels.dataCollection.uiState.value.forms.forEach { form ->
        if (form.targetDatasetId.isNotEmpty()) {
          assertFalse(
            "(${form.targetDatasetId})" in form.targetDatasetName,
            "Form targetDatasetName '${form.targetDatasetName}' should not include internal targetDatasetId '${form.targetDatasetId}'",
          )
        }
      }
    }
  }

  @Test
  fun webFormsForEntity_onlyReturnsFormsWithEntitySelectQuestion() {
    val testState = WebAppState(dataHolder = sampleDataHolder())
    val ent = testState.viewModels.dataCollection.uiState.value.entities.first()
    val webForms =
      testState.viewModels.dataCollection.uiState.value.formsForEntity(ent, onWeb = true)
    assertTrue(
      webForms.isNotEmpty(),
      "Expected at least one seeded form targeting ${ent.datasetId}",
    )
    assertTrue(
      webForms.all { it.hasEntitySelectQuestion },
      "All web forms for selected entity must have hasEntitySelectQuestion == true",
    )
  }

  @Test
  fun buildDashboardTableCsv_andDashboardTableCsvFileName_handleQuotingEscapingAndSanitization() {
    val table =
      DashboardDataTable(
        id = "dataset:ds-1",
        datasetId = "ds-1",
        title = "Coffee / Cocoa: Plots?",
        kind = DashboardDataTableKind.MAP_LAYER,
        columns = listOf("ID", "Notes, \"Quotes\"", "Missing"),
        rows =
          listOf(
            DashboardDataTableRow(
              id = "r1",
              cells = listOf("P-01", "Line 1\n\"Line 2\", ok", ""),
              isSelected = true,
              entity = null,
            )
          ),
      )

    assertEquals(0, table.selectedRowIndex)
    val csv = buildDashboardTableCsv(table)
    assertEquals(
      "ID,\"Notes, \"\"Quotes\"\"\",Missing\r\nP-01,\"Line 1\n\"\"Line 2\"\", ok\",\r\n",
      csv,
    )
    assertEquals(
      "Coffee _ Cocoa_ Plots_.csv",
      dashboardTableCsvFileName(table),
    )
    assertEquals("table.csv", dashboardTableCsvFileName(table.copy(title = "   ...   ")))
  }

  @Test
  fun statusFilter_filtersTargetDatasetAndExpandsOnlyFilteredDatasetWhenCollapsed() {
    val testState = WebAppState(dataHolder = sampleDataHolder())
    testState.searchableListActions.toggleListDatasetCollapsed("coffee_parcels")
    testState.searchableListActions.toggleListDatasetCollapsed("shade_monitoring_plots")
    assertEquals(
      setOf("coffee_parcels", "shade_monitoring_plots"),
      testState.surveyRecords
        .searchableListUiState(testState.viewModels.surveyMap)
        .collapsedDatasetIds,
    )

    testState.searchableListActions.toggleListStatusFilter("coffee_parcels", "In progress")
    val filteredList = testState.surveyRecords.searchableListUiState(testState.viewModels.surveyMap)
    assertTrue(filteredList.usesEntityStatus)
    assertEquals("In progress", filteredList.activeStatusFilters["coffee_parcels"])
    // coffee_parcels is expanded while its status filter is active, whereas shade_monitoring_plots
    // keeps its collapsed state.
    assertEquals(setOf("shade_monitoring_plots"), filteredList.collapsedDatasetIds)
    val parcelsGroup = filteredList.entityGroups.first { it.datasetId == "coffee_parcels" }
    assertEquals(1, parcelsGroup.entities.size)
    assertEquals("In progress", parcelsGroup.entities.single().workflowStatus)

    // Switching to Simple Point & Land Use Survey hides the status filter options cleanly.
    testState.viewModels.onboarding.openSurvey("00000036-1a1c-5c51-997f-28b67d813aa3")
    val noStatusList = testState.surveyRecords.searchableListUiState(testState.viewModels.surveyMap)
    assertFalse(noStatusList.usesEntityStatus)
    assertTrue(noStatusList.activeStatusFilters.isEmpty())
    assertTrue(noStatusList.entityGroups.all { it.statusFilterOptions.isEmpty() })
    assertEquals(3, noStatusList.entityGroups.flatMap { it.entities }.size)
  }
}
