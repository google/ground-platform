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
    assertEquals(!initialLayerVisibility, testState.entityDatasetLayers.first { it.id == firstLayer.id }.isVisible)

    testState.updateLayersSheetOpen(false)
    assertFalse(testState.isLayersSheetOpen)
  }
}

