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
package org.openforis.collectone.v2.app.ui.datacollection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.domain.model.EntityDatasetFeaturesGroup
import org.openforis.collectone.v2.app.domain.model.EntityStatusFilterOption
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.LayerSourceType
import org.openforis.collectone.v2.app.domain.model.MapLayerItem
import org.openforis.collectone.v2.app.ui.navigation.EntityDetailsPane
import org.openforis.collectone.v2.app.ui.session.SurveyRecordsUiStates
import org.openforis.collectone.v2.app.ui.session.TestAppSession
import org.openforis.collectone.v2.app.ui.session.collectSurveyRecordsUiStates
import org.openforis.collectone.v2.app.ui.session.entityDetailsUiState
import org.openforis.collectone.v2.app.ui.session.searchableListUiState
import org.openforis.collectone.v2.app.ui.session.surveyBottomSheetUiState
import org.openforis.collectone.v2.core.forms.ui.AppTheme

/**
 * Characterizes the survey's persistent bottom sheet (mobile) and the web dashboard's side panel
 * list over the sample data: what they show and which session state their clicks change.
 */
@OptIn(ExperimentalTestApi::class)
class SurveyBottomSheetContentUiTest {

  private val session = TestAppSession(sampleDataHolder())

  /** A map feature whose title no other feature shares, so its list row is unambiguous. */
  private val uniquelyTitledEntity: GeospatialEntityItem
    get() =
      session.viewModels.dataCollection.uiState.value.entities.first { entity ->
        session.viewModels.dataCollection.uiState.value.entities.count {
          it.displayTitle == entity.displayTitle
        } == 1
      }

  private val entityWithSubmissions: GeospatialEntityItem
    get() =
      session.viewModels.dataCollection.uiState.value.entities.first { it.submissions.isNotEmpty() }

  private fun ComposeUiTest.showSheet(isSidePanel: Boolean = false) {
    setContent {
      AppTheme {
        if (isSidePanel) {
          BottomSheetSearchableListContent(
            uiState = collectRecords().searchableListUiState(session.viewModels.surveyMap),
            actions = session.searchableListActions,
            modifier = Modifier.fillMaxSize(),
            isSidePanel = true,
          )
        } else {
          SurveyPersistentBottomSheetContent(
            uiState = collectRecords().surveyBottomSheetUiState(session.viewModels.surveyMap),
            listActions = session.searchableListActions,
            detailsActions = session.entityDetailsActions,
            onReturnToList = session.viewModels.dataCollection::returnToBottomSheetList,
            modifier = Modifier.fillMaxSize(),
          )
        }
      }
    }
  }

  @Test
  fun mobileList_showsTheSurveyTitleAndMapLayers() = runComposeUiTest {
    showSheet()

    onNodeWithText(session.activeSurveyAccess.activeSurvey.title).assertExists()
    onNodeWithText("MAP LAYERS").assertExists()
  }

  @Test
  fun mobileList_tappingAMapFeatureSelectsIt() = runComposeUiTest {
    val entity = uniquelyTitledEntity
    showSheet()

    onNodeWithText(entity.displayTitle).performClick()
    waitForIdle()

    assertEquals(entity.id, session.viewModels.dataCollection.uiState.value.selectedEntityId)
  }

  @Test
  fun sidePanel_showsASearchFieldInsteadOfTheSurveyTitle() = runComposeUiTest {
    showSheet(isSidePanel = true)

    onNodeWithText("Search...").assertExists()
    onNodeWithText(session.activeSurveyAccess.activeSurvey.title).assertDoesNotExist()
  }

  @Test
  fun sidePanel_queryWithoutMatches_showsTheEmptyMessage() = runComposeUiTest {
    showSheet(isSidePanel = true)

    onNode(hasSetTextAction()).performTextInput("zzqx-no-such-feature")
    waitForIdle()

    assertEquals("zzqx-no-such-feature", session.viewModels.listSearch.uiState.value.query)
    onNodeWithText("No map layers or places match \"zzqx-no-such-feature\".").assertExists()
  }

  @Test
  fun sidePanel_emptySurveyWithBlankQuery_showsNoMapLayersYetInsteadOfMatchedEmptyQuotes() =
    runComposeUiTest {
      runBlocking {
        session.dataHolder.surveyRepository.setEntities(emptyList())
        session.dataHolder.surveyRepository.setMapLayers(emptyList())
      }
      showSheet(isSidePanel = true)
      waitForIdle()

      assertFalse(
        session.surveyRecords.searchableListUiState(session.viewModels.surveyMap).hasMapLayers
      )
      onNodeWithText("No map layers yet.").assertExists()
      onNodeWithText("No map layers or places match \"\".").assertDoesNotExist()

      // Whitespace-only input still has no active filter and must not claim it matched '"   "'.
      onNode(hasSetTextAction()).performTextInput("   ")
      waitForIdle()
      onNodeWithText("No map layers yet.").assertExists()
      onNodeWithText("No map layers or places match \"   \".").assertDoesNotExist()
    }

  @Test
  fun sidePanel_surveyWithEmptyMapLayerAndBlankQuery_showsNoMapFeaturesYet() = runComposeUiTest {
    runBlocking {
      session.dataHolder.surveyRepository.setEntities(emptyList())
      session.dataHolder.surveyRepository.setMapLayers(
        listOf(
          MapLayerItem(
            id = "layer-parcels",
            label = "Whisp Parcels",
            colorHex = 0xFF2E7D32,
            geometryType = null,
            isVisible = true,
            sourceType = LayerSourceType.ENTITY_DATASET,
            datasetId = "dataset-parcels",
          )
        )
      )
    }
    showSheet(isSidePanel = true)
    waitForIdle()

    assertTrue(
      session.surveyRecords.searchableListUiState(session.viewModels.surveyMap).hasMapLayers
    )
    onNodeWithText("No map features yet.").assertExists()
    onNodeWithText("No map layers or places match \"\".").assertDoesNotExist()
  }

  @Test
  fun mobileList_offlineNonBlankQueryWithoutMatches_showsLocalLayersMatchedMessage() =
    runComposeUiTest {
      setContent {
        AppTheme {
          BottomSheetSearchableListContent(
            uiState =
              SearchableListUiState(
                surveyTitle = "Whisp Test",
                query = "  zzqx-offline  ",
                isOnline = false,
                hasMapLayers = true,
                entityGroups = emptyList(),
                places = emptyList(),
              ),
            actions = session.searchableListActions,
            modifier = Modifier.fillMaxSize(),
            isSidePanel = false,
          )
        }
      }

      onNodeWithText(
          "No local map layers match \"zzqx-offline\". Places search is not available offline."
        )
        .assertExists()
    }

  @Test
  fun mobileList_offlineBlankQueryWithoutLayers_showsNoMapLayersYet() = runComposeUiTest {
    setContent {
      AppTheme {
        BottomSheetSearchableListContent(
          uiState =
            SearchableListUiState(
              surveyTitle = "Whisp Test",
              query = "",
              isOnline = false,
              hasMapLayers = false,
              entityGroups = emptyList(),
              places = emptyList(),
            ),
          actions = session.searchableListActions,
          modifier = Modifier.fillMaxSize(),
          isSidePanel = false,
        )
      }
    }

    onNodeWithText("No map layers yet.").assertExists()
    onNodeWithText("No local map layers match \"\". Places search is not available offline.")
      .assertDoesNotExist()
  }

  @Test
  fun sidePanel_coordinateQuery_listsTheCoordinatesAsAPlace() = runComposeUiTest {
    val surveyLocation = session.activeSurveyAccess.activeSurvey.location
    showSheet(isSidePanel = true)

    onNode(hasSetTextAction()).performTextInput("-0.421, 36.9505")
    waitForIdle()

    onNodeWithText("Coordinates (0.4210°S, 36.9505°E)").assertExists()
    onNodeWithText("GPS Coordinates • $surveyLocation • Direct coordinate lookup").assertExists()
    onNodeWithText("GPS Coordinates").assertExists()
  }

  @Test
  fun mobileList_placeCard_showsCoordinatesOnTheirOwnLine_andASingleNavigateButton() =
    runComposeUiTest {
      val surveyLocation = session.activeSurveyAccess.activeSurvey.location
      session.searchableListActions.updateListSearchQuery("-0.421, 36.9505")
      showSheet()
      waitForIdle()

      onNodeWithText("Coordinates (0.4210°S, 36.9505°E)").assertExists()
      onNodeWithText("GPS Coordinates • $surveyLocation • Direct coordinate lookup").assertExists()
      // The coordinates are a line of their own; the category and coordinates chips and the
      // fly-to button are gone.
      onNodeWithText("0.4210°S, 36.9505°E").assertExists()
      onNodeWithText("GPS Coordinates").assertDoesNotExist()
      onNodeWithContentDescription("Fly to place").assertDoesNotExist()
      onAllNodesWithText("Navigate").assertCountEquals(1)

      onNodeWithText("Navigate").performScrollTo().performClick()
      waitForIdle()

      assertNotNull(session.viewModels.surveyMap.uiState.value.activeNavigation)
      onNodeWithText("End").assertExists()
    }

  @Test
  fun selectedEntity_navigateButtonStartsAndEndsNavigation() = runComposeUiTest {
    val entity = entityWithSubmissions
    session.viewModels.surveyMap.selectEntity(entity.id)
    showSheet()
    onAllNodesWithText("Navigate").assertCountEquals(1)

    onNodeWithText("Navigate").performClick()
    waitForIdle()
    assertNotNull(session.viewModels.surveyMap.uiState.value.activeNavigation)

    onNodeWithText("End").performClick()
    waitForIdle()
    assertNull(session.viewModels.surveyMap.uiState.value.activeNavigation)
    onNodeWithText("Navigate").assertExists()
  }

  @Test
  fun sidePanel_clickingADatasetHeaderSelectsItsLayer() = runComposeUiTest {
    val entity = uniquelyTitledEntity
    val datasetLabel =
      session.viewModels.dashboard.uiState.value.mapLayers
        .firstOrNull { it.id == entity.layerId }
        ?.label ?: entity.datasetName
    showSheet(isSidePanel = true)

    onAllNodesWithText(datasetLabel).onFirst().performClick()
    waitForIdle()

    assertEquals(
      entity.datasetId,
      session.viewModels.dashboard.uiState.value.selectedLayerDatasetId,
    )
  }

  @Test
  fun sidePanel_datasetChevronHidesAndShowsItsMapFeatures() = runComposeUiTest {
    val entity = uniquelyTitledEntity
    val datasetLabel =
      session.viewModels.dashboard.uiState.value.mapLayers
        .firstOrNull { it.id == entity.layerId }
        ?.label ?: entity.datasetName
    showSheet(isSidePanel = true)

    onNodeWithContentDescription("Collapse $datasetLabel").performClick()
    waitForIdle()

    onNodeWithText(entity.displayTitle).assertDoesNotExist()
    onNodeWithContentDescription("Expand $datasetLabel").performClick()
    waitForIdle()
    onNodeWithText(entity.displayTitle).assertExists()
  }

  @Test
  fun selectedEntity_showsItsPropertiesAndSubmissionsLink() = runComposeUiTest {
    val entity = entityWithSubmissions
    session.viewModels.surveyMap.selectEntity(entity.id)
    showSheet()

    onNodeWithText(entity.displayTitle).assertExists()
    onNodeWithText("Details").assertExists()
    onNodeWithText("Submissions (${entity.submissionCount})").assertExists()
  }

  @Test
  fun selectedEntity_submissionsLinkOpensTheSubmissionsList() = runComposeUiTest {
    val entity = entityWithSubmissions
    session.viewModels.surveyMap.selectEntity(entity.id)
    showSheet()

    onNodeWithText("Submissions (${entity.submissionCount})").performScrollTo().performClick()
    waitForIdle()

    // The feature's submissions, not the Uploads screen.
    val dashboard = session.viewModels.dashboard.uiState.value
    assertEquals(EntityDetailsPane.SUBMISSIONS, dashboard.entityDetailsPane)
    assertEquals(null, dashboard.uploadsEntityFilterId)
    onNodeWithContentDescription("Back to details").assertExists()
    onAllNodesWithContentDescription("Submission details")
      .assertCountEquals(entity.submissions.size)
  }

  @Test
  fun selectedEntity_submissionsPaneOpensASubmission() = runComposeUiTest {
    val entity = entityWithSubmissions
    session.viewModels.surveyMap.selectEntity(entity.id)
    session.viewModels.dashboard.showEntitySubmissions()
    showSheet()

    onNodeWithContentDescription("Back to details").assertExists()
    onAllNodesWithContentDescription("Submission details").onFirst().performClick()
    waitForIdle()

    assertTrue(
      session.viewModels.dataCollection.uiState.value.selectedSubmissionId in
        entity.submissions.map { it.id }
    )
    onNodeWithContentDescription("Back to submissions").assertExists()
  }

  @Test
  fun standaloneSubmission_backReturnsToTheList() = runComposeUiTest {
    val submission = session.viewModels.dataCollection.uiState.value.standaloneSubmissions.first()
    session.viewModels.dataCollection.selectSubmissionDetail(submission.id)
    showSheet()

    onNodeWithText("Schema: ${submission.formVersion}").assertExists()
    onNodeWithContentDescription("Back to Searchable List").performClick()
    waitForIdle()

    assertEquals(null, session.viewModels.dataCollection.uiState.value.selectedSubmissionId)
    onNodeWithText("MAP LAYERS").assertExists()
  }

  @Test
  fun webPropertiesPane_showInTableExpandsTheDashboardTable() = runComposeUiTest {
    val entity = entityWithSubmissions
    session.viewModels.surveyMap.selectEntity(entity.id)
    setContent {
      AppTheme {
        Column(Modifier.verticalScroll(rememberScrollState())) {
          EntityPropertiesPane(
            uiState =
              collectRecords()
                .entityDetailsUiState(entity, onWeb = true, session.viewModels.surveyMap),
            actions = session.entityDetailsActions,
            isWeb = true,
          )
        }
      }
    }

    onNodeWithText("Show in table").performScrollTo().performClick()
    waitForIdle()

    assertTrue(session.viewModels.dashboard.uiState.value.isDashboardTableExpanded)
    assertEquals(
      entity.datasetId,
      session.viewModels.dashboard.uiState.value.dashboardTableDatasetId,
    )
  }

  @Test
  fun mobileList_statusFilterChips_filterTargetDatasetAndHideWhenStatusNotUsed() =
    runComposeUiTest {
      showSheet(isSidePanel = false)

      onNodeWithText("○ Pending").assertExists()
      onNodeWithText("◐ In progress").assertExists()
      onNodeWithText("✓ Completed").assertExists()
      onNodeWithText("Plot NYR-104 • Kamau Family Parcel").assertExists()
      onNodeWithText("Plot NYR-112 • Kariuki Hillside Parcel").assertExists()
      onNodeWithText("Transect SHD-201 • Chinga North Agroforestry").assertExists()

      onNodeWithText("○ Pending").performClick()
      waitForIdle()

      assertEquals(
        "Pending",
        session.viewModels.listSearch.uiState.value.selectedStatusForDataset("coffee_parcels"),
      )
      onNodeWithText("Plot NYR-112 • Kariuki Hillside Parcel").assertExists()
      onNodeWithText("Plot NYR-104 • Kamau Family Parcel").assertDoesNotExist()
      // Other layers stay unaffected because status filtering is scoped to coffee_parcels.
      onNodeWithText("Transect SHD-201 • Chinga North Agroforestry").assertExists()

      // Clicking the active status filter chip again clears the filter.
      onNodeWithText("○ Pending").performClick()
      waitForIdle()

      assertEquals(
        null,
        session.viewModels.listSearch.uiState.value.selectedStatusForDataset("coffee_parcels"),
      )
      onNodeWithText("Plot NYR-104 • Kamau Family Parcel").assertExists()

      // Switching to a survey where status is not used hides the status filter chips cleanly.
      session.viewModels.onboarding.openSurvey("00000036-1a1c-5c51-997f-28b67d813aa3")
      waitForIdle()

      onNodeWithText("○ Pending").assertDoesNotExist()
      onNodeWithText("◐ In progress").assertDoesNotExist()
      onNodeWithText("✓ Completed").assertDoesNotExist()
      onNodeWithText("Primary Forest").assertExists()
    }

  @Test
  fun sidePanel_statusFilterChips_filterTargetDatasetAndHideWhenStatusNotUsed() = runComposeUiTest {
    showSheet(isSidePanel = true)

    onNodeWithText("○ Pending").assertExists()
    onNodeWithText("◐ In progress").assertExists()
    onNodeWithText("✓ Completed").assertExists()

    onNodeWithText("✓ Completed").performClick()
    waitForIdle()

    assertEquals(
      "Completed",
      session.viewModels.listSearch.uiState.value.selectedStatusForDataset("coffee_parcels"),
    )
    onNodeWithText("Plot NYR-104 • Kamau Family Parcel").assertExists()
    onNodeWithText("Plot NYR-112 • Kariuki Hillside Parcel").assertDoesNotExist()
    onNodeWithText("Station WSH-01 • Gura River Wet Mill").assertExists()

    session.viewModels.onboarding.openSurvey("00000036-1a1c-5c51-997f-28b67d813aa3")
    waitForIdle()

    onNodeWithText("○ Pending").assertDoesNotExist()
    onNodeWithText("◐ In progress").assertDoesNotExist()
    onNodeWithText("✓ Completed").assertDoesNotExist()
    onNodeWithText("Primary Forest").assertExists()
  }

  @Test
  fun dataTableDataset_showsStatusFilterChipsBelowDataTableHeader() = runComposeUiTest {
    val baseEntity = session.viewModels.dataCollection.uiState.value.entities.first()
    val pendingFarmer =
      baseEntity.copy(
        id = "farmer-1",
        label = "Farmer F-001 • Wanjiku",
        datasetId = "farmers",
        datasetName = "Farmers",
        layerId = "",
        geometry = null,
        properties = mapOf("status" to "Pending", "marker-symbol" to "○"),
      )
    val verifiedFarmer =
      baseEntity.copy(
        id = "farmer-2",
        label = "Farmer F-002 • Muthoni",
        datasetId = "farmers",
        datasetName = "Farmers",
        layerId = "",
        geometry = null,
        properties = mapOf("status" to "Verified", "marker-symbol" to "✓"),
      )
    var toggledPair: Pair<String, String>? = null
    setContent {
      AppTheme {
        BottomSheetSearchableListContent(
          uiState =
            SearchableListUiState(
              surveyTitle = "Cooperative Roster",
              entityGroups =
                listOf(
                  EntityDatasetFeaturesGroup(
                    layer = null,
                    datasetName = "Farmers",
                    entities = listOf(pendingFarmer, verifiedFarmer),
                    datasetId = "farmers",
                    isMapLayer = false,
                    statusFilterOptions =
                      listOf(
                        EntityStatusFilterOption(status = "Pending", symbol = "○", count = 1),
                        EntityStatusFilterOption(status = "Verified", symbol = "✓", count = 1),
                      ),
                    selectedStatusFilter = null,
                  )
                ),
            ),
          actions =
            object : SearchableListActions by session.searchableListActions {
              override fun toggleListStatusFilter(datasetId: String, status: String) {
                toggledPair = datasetId to status
              }
            },
          modifier = Modifier.fillMaxSize(),
          isSidePanel = true,
        )
      }
    }

    onNodeWithText("DATA TABLES").assertExists()
    onNodeWithText("Farmers").assertExists()
    onNodeWithText("○ Pending").assertExists()
    onNodeWithText("✓ Verified").performClick()
    waitForIdle()

    assertEquals("farmers" to "Verified", toggledPair)
  }

  /** The session's record surfaces, following its ViewModels as the clients' destinations do. */
  @Composable
  private fun collectRecords(): SurveyRecordsUiStates =
    collectSurveyRecordsUiStates(
      viewModels = session.viewModels,
      data = session.dataHolder.appData,
      notices = session.notices,
      isAlwaysOnline = false,
    )
}
