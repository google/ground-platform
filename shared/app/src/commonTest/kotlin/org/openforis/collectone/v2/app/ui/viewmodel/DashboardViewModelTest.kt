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
package org.openforis.collectone.v2.app.ui.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore
import org.openforis.collectone.v2.app.data.repository.AuthRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.LibraryRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.LocalStoreTransactionRunner
import org.openforis.collectone.v2.app.data.repository.MutationRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.OrganizationRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.SurveyEditorRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.SurveyRepositoryImpl
import org.openforis.collectone.v2.app.data.seed.SampleOrganizationsData
import org.openforis.collectone.v2.app.domain.model.EntityDetailsPane
import org.openforis.collectone.v2.app.domain.model.ListFilterTab
import org.openforis.collectone.v2.app.domain.model.MutationSyncState
import org.openforis.collectone.v2.app.domain.model.UploadStatusFilter
import org.openforis.collectone.v2.app.domain.usecase.CreateSurveyUseCase
import org.openforis.collectone.v2.app.domain.usecase.SyncMutationsUseCase
import org.openforis.collectone.v2.app.ui.state.DashboardEvent
import org.openforis.collectone.v2.app.ui.state.DashboardUiState

/** See [OnboardingViewModelTest] for why fixtures are built outside `runNow`. */
class DashboardViewModelTest {
  private class Fixture {
    val store = seededStore()
    val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    val surveyRepository = SurveyRepositoryImpl(store)
    val mutationRepository = MutationRepositoryImpl(store)
    val surveyEditorRepository = SurveyEditorRepositoryImpl(store)
    val transactionRunner = LocalStoreTransactionRunner(store)
    val viewModel =
      DashboardViewModel(
        surveyRepository = surveyRepository,
        organizationRepository = OrganizationRepositoryImpl(store),
        authRepository = AuthRepositoryImpl(),
        mutationRepository = mutationRepository,
        createSurveyUseCase =
          CreateSurveyUseCase(surveyRepository, surveyEditorRepository, transactionRunner),
        syncMutationsUseCase =
          SyncMutationsUseCase(mutationRepository, surveyRepository, transactionRunner),
        scope = scope,
      )
    val events = mutableListOf<DashboardEvent>()

    init {
      scope.launch { viewModel.events.collect { events += it } }
    }

    val uiState
      get() = viewModel.uiState.value
  }

  @Test
  fun initialState_hasDefaultLayout_andSeededData() {
    val f = Fixture()
    assertTrue(f.uiState.isSidePanelExpanded)
    assertEquals(DashboardUiState.DEFAULT_SIDE_PANEL_WIDTH_DP, f.uiState.sidePanelWidthDp)
    assertTrue(f.uiState.isDetailsPanelExpanded)
    assertFalse(f.uiState.isDashboardTableExpanded)
    assertNull(f.uiState.dashboardTableDatasetId)
    assertNull(f.uiState.selectedLayerDatasetId)
    assertEquals(EntityDetailsPane.PROPERTIES, f.uiState.entityDetailsPane)
    assertEquals(ListFilterTab.ALL, f.uiState.listFilterTab)
    assertTrue(f.uiState.surveys.isNotEmpty())
    assertTrue(f.uiState.hasOpenableActiveSurvey)
    assertTrue(f.uiState.entities.isNotEmpty())
    assertTrue(f.uiState.entityDatasetLayers.isNotEmpty())
    assertEquals(SampleOrganizationsData.SIGNED_IN_EMAIL, f.uiState.signedInUserEmail)
    assertTrue(f.uiState.signedInUserOrganizations.isNotEmpty())
    assertEquals(9, f.uiState.allMutationsSorted.size)
    assertEquals(4, f.uiState.outboxMutationCount)
    assertEquals(5, f.uiState.uploadedMutationCount)
  }

  @Test
  fun sidePanelWidth_isClamped_ignoresNonFinite_andKeptWhileCollapsed() {
    val f = Fixture()
    f.viewModel.updateSidePanelWidth(420f)
    assertEquals(420f, f.uiState.sidePanelWidthDp)
    f.viewModel.updateSidePanelWidth(DashboardUiState.MIN_SIDE_PANEL_WIDTH_DP - 100f)
    assertEquals(DashboardUiState.MIN_SIDE_PANEL_WIDTH_DP, f.uiState.sidePanelWidthDp)
    f.viewModel.updateSidePanelWidth(DashboardUiState.MAX_SIDE_PANEL_WIDTH_DP + 100f)
    assertEquals(DashboardUiState.MAX_SIDE_PANEL_WIDTH_DP, f.uiState.sidePanelWidthDp)
    f.viewModel.updateSidePanelWidth(Float.NaN)
    f.viewModel.updateSidePanelWidth(Float.POSITIVE_INFINITY)
    assertEquals(DashboardUiState.MAX_SIDE_PANEL_WIDTH_DP, f.uiState.sidePanelWidthDp)

    f.viewModel.collapseSidePanel()
    assertFalse(f.uiState.isSidePanelExpanded)
    f.viewModel.toggleSidePanel()
    assertTrue(f.uiState.isSidePanelExpanded)
    assertEquals(DashboardUiState.MAX_SIDE_PANEL_WIDTH_DP, f.uiState.sidePanelWidthDp)
  }

  @Test
  fun dashboardTable_collapsesDetailsWhenExpanded_andRestoresThemForASelection() {
    val f = Fixture()
    f.viewModel.updateDashboardTableExpanded(true)
    assertTrue(f.uiState.isDashboardTableExpanded)
    assertFalse(f.uiState.isDetailsPanelExpanded)

    // Collapsing with nothing selected leaves the details panel alone.
    f.viewModel.updateDashboardTableExpanded(false)
    assertFalse(f.uiState.isDetailsPanelExpanded)

    // Collapsing with a selected record brings the details card back.
    f.viewModel.toggleDashboardTableExpanded()
    f.viewModel.toggleDashboardTableExpanded(hasSelection = true)
    assertFalse(f.uiState.isDashboardTableExpanded)
    assertTrue(f.uiState.isDetailsPanelExpanded)

    // Collapsing with a selected layer does too.
    val datasetId = f.uiState.entities.first().datasetId
    f.viewModel.updateDashboardTableExpanded(true)
    f.viewModel.selectLayer(datasetId)
    assertFalse(f.uiState.isDetailsPanelExpanded, "table expanded: card stays collapsed")
    f.viewModel.updateDashboardTableExpanded(false)
    assertTrue(f.uiState.isDetailsPanelExpanded)
  }

  @Test
  fun selectLayer_opensDetailsAndFollowsTable_showInTableExpandsIt() {
    val f = Fixture()
    val datasetId = f.uiState.entities.first().datasetId
    f.viewModel.collapseDetailsPanel()
    f.viewModel.selectEntityDetailsTab(EntityDetailsPane.SUBMISSIONS)

    f.viewModel.selectLayer(datasetId)
    assertEquals(datasetId, f.uiState.selectedLayerDatasetId)
    assertEquals(datasetId, f.uiState.dashboardTableDatasetId)
    assertEquals(EntityDetailsPane.PROPERTIES, f.uiState.entityDetailsPane)
    assertTrue(f.uiState.isDetailsPanelExpanded)

    f.viewModel.showSelectedLayerInTable()
    assertTrue(f.uiState.isDashboardTableExpanded)
    assertFalse(f.uiState.isDetailsPanelExpanded)

    f.viewModel.selectLayer(null)
    assertNull(f.uiState.selectedLayerDatasetId)
    assertEquals(datasetId, f.uiState.dashboardTableDatasetId)
  }

  @Test
  fun entityAndSubmissionSelections_clearLayer_andSwitchTableToTheirDataset() {
    val f = Fixture()
    val entity = f.uiState.entities.first()
    val other = f.uiState.entities.first { it.datasetId != entity.datasetId }
    f.viewModel.selectLayer(other.datasetId)
    f.viewModel.collapseDetailsPanel()
    f.viewModel.selectEntityDetailsTab(EntityDetailsPane.SUBMISSIONS)

    f.viewModel.onEntitySelected(entity.id)
    assertNull(f.uiState.selectedLayerDatasetId)
    assertEquals(entity.datasetId, f.uiState.dashboardTableDatasetId)
    assertEquals(EntityDetailsPane.PROPERTIES, f.uiState.entityDetailsPane)
    assertTrue(f.uiState.isDetailsPanelExpanded)

    f.viewModel.onSubmissionOpened(tableDatasetId = other.datasetId)
    assertEquals(EntityDetailsPane.SUBMISSIONS, f.uiState.entityDetailsPane)
    assertEquals(other.datasetId, f.uiState.dashboardTableDatasetId)

    // Clearing the selection keeps the table where it was.
    f.viewModel.onEntitySelected(null)
    assertEquals(other.datasetId, f.uiState.dashboardTableDatasetId)
    f.viewModel.showEntitySubmissions()
    assertEquals(EntityDetailsPane.SUBMISSIONS, f.uiState.entityDetailsPane)
    f.viewModel.showEntityProperties()
    assertEquals(EntityDetailsPane.PROPERTIES, f.uiState.entityDetailsPane)
  }

  @Test
  fun listDatasets_collapseIndividually_andShowExpandedWhileSearching() {
    val f = Fixture()
    assertFalse(f.uiState.isListDatasetCollapsed("plots"))
    f.viewModel.toggleListDatasetCollapsed("plots")
    f.viewModel.toggleListDatasetCollapsed("trees")
    assertEquals(setOf("plots", "trees"), f.uiState.collapsedListDatasetIds)
    f.viewModel.toggleListDatasetCollapsed("plots")
    assertFalse(f.uiState.isListDatasetCollapsed("plots"))
    assertTrue(f.uiState.isListDatasetCollapsed("trees"))

    f.viewModel.updateListSearchQuery("oak")
    assertEquals("oak", f.uiState.listSearchQuery)
    assertFalse(f.uiState.isListDatasetCollapsed("trees"))
    f.viewModel.clearListSearchQuery()
    assertTrue(f.uiState.isListDatasetCollapsed("trees"))

    f.viewModel.selectListFilterTab(ListFilterTab.ENTITIES)
    assertEquals(ListFilterTab.ENTITIES, f.uiState.listFilterTab)
  }

  @Test
  fun tabLabels_useTheSingleVisibleDatasetsPluralLabel() {
    val f = Fixture()
    assertEquals(ListFilterTab.ENTITIES.label, f.uiState.tabLabelFor(ListFilterTab.ENTITIES))
    assertEquals("Places", f.uiState.tabLabelFor(ListFilterTab.PLACES))
    val layers = f.uiState.entityDatasetLayers
    assertTrue(layers.size > 1)
    runNow { layers.drop(1).forEach { f.surveyRepository.toggleLayerVisibility(it.id) } }
    assertEquals(listOf(layers.first().id), f.uiState.visibleEntityDatasetLayers.map { it.id })
    assertEquals(layers.first().pluralDomainLabel, f.uiState.activeEntitiesTabLabel)
  }

  @Test
  fun uploads_filterByStatusChipAndEntity_andReportCounts() {
    val f = Fixture()
    f.viewModel.showUploads()
    assertEquals(DashboardEvent.UploadsOpened, f.events.last())
    assertNull(f.uiState.selectedUploadStatusFilter)
    assertEquals(9, f.uiState.filteredUploadMutations.size)
    assertEquals(1, f.uiState.uploadCountForFilter(UploadStatusFilter.PENDING))
    assertEquals(2, f.uiState.uploadCountForFilter(UploadStatusFilter.IN_PROGRESS))
    assertEquals(5, f.uiState.uploadCountForFilter(UploadStatusFilter.UPLOADED))
    assertEquals(1, f.uiState.uploadCountForFilter(UploadStatusFilter.FAILED))

    f.viewModel.toggleUploadStatusFilter(UploadStatusFilter.PENDING)
    assertEquals(listOf("mut-outbox-02"), f.uiState.filteredUploadMutations.map { it.id })
    f.viewModel.toggleUploadStatusFilter(UploadStatusFilter.PENDING)
    assertNull(f.uiState.selectedUploadStatusFilter)
    assertEquals(
      listOf("mut-outbox-04", "mut-outbox-03", "mut-outbox-02", "mut-outbox-01"),
      f.uiState.outboxMutations.map { it.id },
    )

    val entityId = f.uiState.mutations.first().entityId
    val expected = f.uiState.mutations.count { it.entityId == entityId }
    f.viewModel.selectUploadStatusFilter(UploadStatusFilter.UPLOADED)
    f.viewModel.openUploadsForEntity(entityId)
    assertEquals(DashboardEvent.UploadsOpened, f.events.last())
    assertNull(f.uiState.selectedUploadStatusFilter, "entity filter clears the status chip")
    assertEquals(entityId, f.uiState.uploadsEntityFilter?.id)
    assertEquals(expected, f.uiState.filteredUploadMutations.size)
    assertEquals(expected, f.uiState.uploadCountForEntity(entityId))
    assertTrue(f.uiState.pendingUploadEntityIds.contains("00000008-15d0-51f2-84eb-a72dfea5a747"))
    assertEquals(2, f.uiState.pendingUploadCountForEntity("00000008-15d0-51f2-84eb-a72dfea5a747"))

    f.viewModel.clearUploadsEntityFilter()
    assertEquals(9, f.uiState.filteredUploadMutations.size)
    f.viewModel.showUploads(UploadStatusFilter.FAILED)
    assertEquals(UploadStatusFilter.FAILED, f.uiState.selectedUploadStatusFilter)
    assertEquals("mut-outbox-01", f.uiState.filteredUploadMutations.single().id)
  }

  @Test
  fun syncMutations_moveOutboxToUploaded_andEmitNotices() {
    val f = Fixture()
    f.viewModel.syncMutationNow("mut-outbox-04")
    assertEquals(3, f.uiState.outboxMutationCount)
    assertEquals(6, f.uiState.uploadedMutationCount)
    assertEquals("mut-outbox-04", f.uiState.uploadedMutations.first().id)
    assertEquals(MutationSyncState.UPLOADED, f.uiState.uploadedMutations.first().state)
    val notice = assertIs<DashboardEvent.Notice>(f.events.last())
    assertTrue(notice.message.startsWith("Uploaded mutation "))

    f.viewModel.syncAllOutboxMutations()
    assertEquals(0, f.uiState.outboxMutationCount)
    assertEquals(9, f.uiState.uploadedMutationCount)
    assertTrue(f.uiState.pendingUploadEntityIds.isEmpty())
    assertTrue(assertIs<DashboardEvent.Notice>(f.events.last()).message.startsWith("Uploaded all "))

    // Nothing left to sync: no notice.
    val before = f.events.size
    f.viewModel.syncAllOutboxMutations()
    assertEquals(before, f.events.size)
  }

  @Test
  fun openSurveyOnWeb_switchesActiveSurvey_resetsTable_andIgnoresUnknownIds() {
    val f = Fixture()
    val target = f.uiState.surveys.first { it.id != f.uiState.activeSurveyId }
    val datasetId = f.uiState.entities.first().datasetId
    f.viewModel.selectLayer(datasetId)
    f.viewModel.updateDashboardTableExpanded(true)

    f.viewModel.openSurveyOnWeb(target.id)
    assertEquals(target.id, f.uiState.activeSurveyId)
    assertEquals(target.id, runNow { f.surveyRepository.getActiveSurveyId() })
    assertNull(f.uiState.selectedLayerDatasetId)
    assertNull(f.uiState.dashboardTableDatasetId)
    assertFalse(f.uiState.isDashboardTableExpanded)
    assertEquals(DashboardEvent.SurveyActivated(target.id), f.events.last())

    val before = f.events.size
    f.viewModel.openSurveyOnWeb("survey-missing")
    assertEquals(target.id, f.uiState.activeSurveyId)
    assertEquals(before, f.events.size)
  }

  @Test
  fun createSurvey_addsAnOwnedSurveyInTheOrganization_opensIt_andNotifies() {
    val f = Fixture()
    val before = f.uiState.surveys.size
    val kfs = assertNotNull(f.uiState.organization(SampleOrganizationsData.KENYA_FOREST_SERVICE))

    val id = f.viewModel.createSurvey("Mangrove Nursery Audit", kfs.id)
    assertEquals(before + 1, f.uiState.surveys.size)
    val created = f.uiState.surveys.last()
    assertEquals(id, created.id)
    assertEquals("Mangrove Nursery Audit", created.title)
    assertEquals(SampleOrganizationsData.SIGNED_IN_EMAIL, created.ownerEmail)
    assertEquals(kfs.id, created.organizationId)
    assertEquals(id, f.uiState.activeSurveyId)
    val draft = runNow { f.surveyEditorRepository.getDraft(id) }
    assertEquals(kfs.id, draft.details.organizationId)
    assertEquals(SampleOrganizationsData.SIGNED_IN_EMAIL, draft.sharing.ownerEmail)
    assertEquals(DashboardEvent.SurveyActivated(id), f.events[f.events.size - 2])
    assertEquals(
      DashboardEvent.Notice("Created survey \"Mangrove Nursery Audit\"."),
      f.events.last(),
    )

    // A second survey with the same title gets its own random ID.
    assertNotEquals(id, f.viewModel.createSurvey("Mangrove Nursery Audit"))
    val personal = f.viewModel.createSurvey("   ")
    assertEquals(
      CreateSurveyUseCase.DEFAULT_TITLE,
      f.uiState.surveys.first { it.id == personal }.title,
    )
    assertNull(f.uiState.surveys.first { it.id == personal }.organizationId)
  }

  @Test
  fun reset_restoresSessionDefaults() {
    val f = Fixture()
    f.viewModel.updateSidePanelWidth(480f)
    f.viewModel.collapseSidePanel()
    f.viewModel.updateDashboardTableExpanded(true)
    f.viewModel.selectLayer(f.uiState.entities.first().datasetId)
    f.viewModel.toggleUploadStatusFilter(UploadStatusFilter.FAILED)
    f.viewModel.toggleListDatasetCollapsed("plots")
    f.viewModel.updateListSearchQuery("oak")

    f.viewModel.reset()

    assertEquals(
      DashboardUiState(),
      f.uiState.copy(
        surveys = emptyList(),
        activeSurveyId = "",
        organizations = emptyList(),
        signedInUserEmail = "",
        surveyLibraries = emptyMap(),
        entities = emptyList(),
        mapLayers = emptyList(),
        mutations = emptyList(),
        allMutationsSorted = emptyList(),
        filteredUploadMutations = emptyList(),
        outboxMutations = emptyList(),
        uploadedMutations = emptyList(),
        pendingUploadEntityIds = emptySet(),
      ),
    )
  }

  @Test
  fun dashboardTable_autoCollapsesSidePanelWhenExpanded_andRestoresItOnClose() {
    val f = Fixture()
    assertTrue(f.uiState.isSidePanelExpanded)

    f.viewModel.updateDashboardTableExpanded(true)
    assertTrue(f.uiState.isDashboardTableExpanded)
    assertFalse(f.uiState.isSidePanelExpanded)

    f.viewModel.updateDashboardTableExpanded(false)
    assertFalse(f.uiState.isDashboardTableExpanded)
    assertTrue(f.uiState.isSidePanelExpanded)

    // When the side panel was already collapsed before opening the table, closing keeps it
    // collapsed.
    f.viewModel.collapseSidePanel()
    assertFalse(f.uiState.isSidePanelExpanded)
    f.viewModel.updateDashboardTableExpanded(true)
    assertFalse(f.uiState.isSidePanelExpanded)
    f.viewModel.updateDashboardTableExpanded(false)
    assertFalse(f.uiState.isSidePanelExpanded)

    // Manual side-panel toggles while the table is open are preserved when the table closes.
    f.viewModel.expandSidePanel()
    f.viewModel.updateDashboardTableExpanded(true)
    assertFalse(f.uiState.isSidePanelExpanded)
    f.viewModel.updateSidePanelExpanded(true)
    assertTrue(f.uiState.isSidePanelExpanded)
    f.viewModel.updateDashboardTableExpanded(false)
    assertTrue(f.uiState.isSidePanelExpanded)
  }

  @Test
  fun exportOptions_scopesProfilePlansOnlyToDatasetsWithMappedFields() {
    val f = Fixture()
    val viewModel =
      DashboardViewModel(
        surveyRepository = f.surveyRepository,
        organizationRepository = OrganizationRepositoryImpl(f.store),
        authRepository = AuthRepositoryImpl(),
        mutationRepository = f.mutationRepository,
        createSurveyUseCase =
          CreateSurveyUseCase(f.surveyRepository, f.surveyEditorRepository, f.transactionRunner),
        syncMutationsUseCase =
          SyncMutationsUseCase(f.mutationRepository, f.surveyRepository, f.transactionRunner),
        scope = f.scope,
        libraryRepository = LibraryRepositoryImpl(f.store),
        surveyEditorRepository = f.surveyEditorRepository,
      )
    val exportOptions = viewModel.uiState.value.exportOptions

    // Coffee parcels link properties to EUDR concepts, so they receive the EUDR GeoJSON profile.
    val parcels = assertNotNull(exportOptions["coffee_parcels"])
    assertTrue(parcels.hasGeometry)
    assertEquals(listOf("eudr_geojson"), parcels.profilePlans.map { it.profile.id })

    // Other map layers in the same survey have no fields mapped to EUDR concepts, so they only
    // offer plain GeoJSON and no extraneous EUDR GeoJSON profile.
    val shadePlots = assertNotNull(exportOptions["shade_monitoring_plots"])
    assertTrue(shadePlots.hasGeometry)
    assertTrue(shadePlots.profilePlans.isEmpty())
    assertNull(viewModel.exportWithProfile("shade_monitoring_plots", "eudr_geojson"))

    val washingStations = assertNotNull(exportOptions["washing_stations"])
    assertTrue(washingStations.hasGeometry)
    assertTrue(washingStations.profilePlans.isEmpty())
    assertNull(viewModel.exportWithProfile("washing_stations", "eudr_geojson"))
  }
}
