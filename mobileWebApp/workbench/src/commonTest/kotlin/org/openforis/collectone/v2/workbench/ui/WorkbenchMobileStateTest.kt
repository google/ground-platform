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
package org.openforis.collectone.v2.workbench.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.data.datasource.remote.SeedUserDataService
import org.openforis.collectone.v2.app.di.AppDataHolder
import org.openforis.collectone.v2.app.domain.model.LocationLockState
import org.openforis.collectone.v2.app.domain.model.MutationSyncState
import org.openforis.collectone.v2.app.domain.model.NavigationTargetKind
import org.openforis.collectone.v2.app.domain.model.PlaceKind
import org.openforis.collectone.v2.app.domain.model.PlaceSource
import org.openforis.collectone.v2.app.domain.model.SurveyPlaceItem
import org.openforis.collectone.v2.app.domain.model.SyncStatus
import org.openforis.collectone.v2.app.domain.model.parsePlaceCoordinates
import org.openforis.collectone.v2.app.domain.model.typeNounOf
import org.openforis.collectone.v2.app.ui.listsearch.awaitPlacesSearchIdle
import org.openforis.collectone.v2.app.ui.map.MapFeatureNoun
import org.openforis.collectone.v2.app.ui.navigation.MainSurveyViewMode
import org.openforis.collectone.v2.app.ui.session.availableSubmissionGroupsOf
import org.openforis.collectone.v2.app.ui.session.surveyRecordsUiStatesOf
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.mobile.ui.MobileAppState
import org.openforis.collectone.v2.mobile.ui.navigation.MobileRoute

/** Mobile app behavior driven through the workbench ([WorkbenchState]). */
class WorkbenchMobileStateTest {

  @Test
  fun downloadSurvey_marksSurveyAsDownloaded() {
    val workbench =
      WorkbenchState(
        MobileAppState(initialRoute = MobileRoute.DownloadSurvey, dataHolder = sampleDataHolder())
      )
    val state = workbench.mobile
    val targetId = "00000035-caf4-571e-b907-f506f92666ab"
    assertFalse(
      state.viewModels.dashboard.uiState.value.surveys.first { it.id == targetId }.isDownloaded
    )

    state.viewModels.onboarding.downloadSurvey(targetId)
    assertTrue(
      state.viewModels.dashboard.uiState.value.surveys.first { it.id == targetId }.isDownloaded
    )

    // Toggle downloaded indicator off and back on
    workbench.toggleSurveyDownloaded(targetId)
    assertFalse(
      state.viewModels.dashboard.uiState.value.surveys.first { it.id == targetId }.isDownloaded
    )
  }

  @Test
  fun listViewSearch_filtersPlacesAndEntitiesAndRemovesFormSubmissionSearch() = runTest {
    val workbench =
      WorkbenchState(
        MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
      )
    val state = workbench.mobile
    state.setMainSurveyViewMode(MainSurveyViewMode.LIST)

    assertEquals(
      MapFeatureNoun.Generic,
      state.viewModels.surveyMap.uiState.value.activeEntitiesCountNoun,
    )
    assertEquals(
      "Coffee Parcel",
      state.viewModels.dataCollection.uiState.value.entities.typeNounOf(
        "00000007-640e-5ed7-9874-4d6b6717f21d"
      ),
    )
    assertEquals(
      "Shade Tree Monitoring Plot",
      state.viewModels.dataCollection.uiState.value.entities.typeNounOf(
        "0000000a-56d7-5c1d-9457-7457267120a3"
      ),
    )
    assertEquals(
      "Cooperative Washing Station",
      state.viewModels.dataCollection.uiState.value.entities.typeNounOf(
        "0000000b-7633-5116-9d6d-50581002d8cf"
      ),
    )

    // When filtered down to a single visible entity dataset layer, dynamic domain naming activates
    state.searchableListActions.toggleLayerVisibility("layer-shade-transects")
    state.searchableListActions.toggleLayerVisibility("layer-water-points")
    assertEquals(
      MapFeatureNoun.OfLayer("coffee parcel", "coffee parcels"),
      state.viewModels.surveyMap.uiState.value.activeEntitiesCountNoun,
    )
    state.searchableListActions.toggleLayerVisibility("layer-shade-transects")
    state.searchableListActions.toggleLayerVisibility("layer-water-points")
    assertEquals(
      MapFeatureNoun.Generic,
      state.viewModels.surveyMap.uiState.value.activeEntitiesCountNoun,
    )

    // Unfiltered counts: 0 places when search query is blank, 5 entities grouped across 3 datasets
    assertEquals(0, state.viewModels.listSearch.uiState.value.places.size)
    assertTrue(state.viewModels.listSearch.uiState.value.places.isEmpty())
    assertEquals(5, state.viewModels.listSearch.uiState.value.entities.size)
    assertEquals(3, state.viewModels.listSearch.uiState.value.entityGroups.size)

    // Search for a geographic place ("Karima Hill" / "Karima Hill Forest Reserve")
    state.searchableListActions.updateListSearchQuery("Karima Hill")
    assertEquals(1, state.viewModels.listSearch.uiState.value.places.size)
    assertEquals(
      "Karima Hill Forest Reserve",
      state.viewModels.listSearch.uiState.value.places.first().name,
    )
    assertTrue(state.viewModels.listSearch.uiState.value.entities.isEmpty())

    // Verify coordinate parsing helper handles both degree/cardinal and signed decimal strings
    val parsedCardinal = assertNotNull(parsePlaceCoordinates("0.5012° S, 36.9324° E"))
    assertEquals(-0.5012, parsedCardinal.latitude, 0.0001)
    assertEquals(36.9324, parsedCardinal.longitude, 0.0001)
    val parsedDecimal = assertNotNull(parsePlaceCoordinates("-0.5012, 36.9324"))
    assertEquals(-0.5012, parsedDecimal.latitude, 0.0001)
    assertEquals(36.9324, parsedDecimal.longitude, 0.0001)

    // Selecting a place centers/zooms the map on the place's bounds, switches locationLockState to
    // PANNED,
    // and collapses the bottom sheet. A country zooms out much farther than a village/forest.
    state.searchableListActions.selectPlace("place-kenya-country")
    val kenyaZoom = assertNotNull(state.viewModels.surveyMap.uiState.value.selectedPlace).targetZoom
    val kenyaZoomDelta = state.viewModels.surveyMap.uiState.value.mapZoomDelta
    assertTrue(kenyaZoom < 6.0f)

    state.searchableListActions.selectPlace("place-iriaini-market")
    val villageZoom =
      assertNotNull(state.viewModels.surveyMap.uiState.value.selectedPlace).targetZoom
    val villageZoomDelta = state.viewModels.surveyMap.uiState.value.mapZoomDelta
    assertTrue(villageZoom > 13.0f)
    assertTrue(villageZoom > kenyaZoom)
    assertTrue(villageZoomDelta > kenyaZoomDelta)

    // Clicking away on the map (selectEntity(null)) dismisses the selected place chip
    state.viewModels.surveyMap.selectEntity(null)
    assertEquals(null, state.viewModels.surveyMap.uiState.value.selectedPlace)

    state.searchableListActions.selectPlace("place-karima-forest")
    assertEquals("place-karima-forest", state.viewModels.surveyMap.uiState.value.selectedPlace?.id)
    assertEquals(MainSurveyViewMode.MAP, state.mainViewMode)
    assertEquals(
      LocationLockState.PANNED,
      state.viewModels.surveyMap.uiState.value.locationLockState,
    )
    assertFalse(state.viewModels.surveyMap.uiState.value.isCameraFollowingUser)
    assertFalse(state.viewModels.surveyMap.uiState.value.isEntityBottomSheetExpanded)

    // Straight-line wayfinding to a place works
    state.searchableListActions.toggleNavigationToPlace("place-karima-forest")
    val placeNav = assertNotNull(state.viewModels.surveyMap.uiState.value.activeNavigation)
    assertEquals(NavigationTargetKind.PLACE, placeNav.targetKind)
    assertEquals("place-karima-forest", placeNav.targetId)
    state.viewModels.surveyMap.stopNavigation()
    state.viewModels.surveyMap.clearSelectedPlace()

    // Search for decimal coordinates creates an ad-hoc coordinate place match
    state.searchableListActions.updateListSearchQuery("-0.4210, 36.9520")
    assertTrue(
      state.viewModels.listSearch.uiState.value.places.any { it.kind == PlaceKind.COORDINATES }
    )

    // Search for "Kamau" (matches entity label & owner property)
    state.searchableListActions.updateListSearchQuery("Kamau")
    assertEquals(1, state.viewModels.listSearch.uiState.value.entities.size)
    assertEquals(
      "Plot NYR-104 • Kamau Family Parcel",
      state.viewModels.listSearch.uiState.value.entities.first().label,
    )

    // Search for "Nyeri" (matches the survey's place gazetteer)
    state.searchableListActions.updateListSearchQuery("Nyeri")
    assertTrue(state.viewModels.listSearch.uiState.value.places.isNotEmpty())
    state.searchableListActions.clearListSearchQuery()

    // Live Places search results merge into filteredListPlaces. The query also starts a real
    // geocoder search in the background; wait for it so its (empty) results don't replace the
    // simulated ones below.
    state.searchableListActions.updateListSearchQuery("Aberdare National Park")
    state.viewModels.listSearch.awaitPlacesSearchIdle()
    state.viewModels.listSearch.onPlacesSearchResults(
      query = "Aberdare National Park",
      results =
        listOf(
          SurveyPlaceItem(
            id = "place.aberdare.901",
            name = "Aberdare National Park",
            categoryLabel = "National Park",
            regionSubtitle = "Nyeri County, Kenya",
            normalizedX = 0.05f,
            normalizedY = 0.05f,
            position = LatLng(-0.3833, 36.7),
          )
        ),
    )
    assertTrue(
      state.viewModels.listSearch.uiState.value.places.any {
        it.name == "Aberdare National Park" && it.source == PlaceSource.PLACES_API
      }
    )

    // Workbench offline simulation disables Mapbox Places search while keeping local map features
    // searchable
    workbench.updateAirplaneMode(true)
    assertTrue(workbench.viewModel.uiState.value.isAirplaneMode)
    assertFalse(state.viewModels.surveyMap.uiState.value.isOnline)
    assertFalse(state.viewModels.surveyMap.uiState.value.isPlacesSearchAvailable)
    assertTrue(state.viewModels.listSearch.uiState.value.places.isEmpty())

    // Searching local features ("Kamau") works while offline
    state.searchableListActions.updateListSearchQuery("Kamau")
    assertTrue(state.viewModels.listSearch.uiState.value.places.isEmpty())
    assertEquals(1, state.viewModels.listSearch.uiState.value.entities.size)
    assertEquals(
      "Plot NYR-104 • Kamau Family Parcel",
      state.viewModels.listSearch.uiState.value.entities.first().label,
    )

    // Turning Airplane mode back off in the workbench restores Places search availability (empty
    // when query blank, populated when query entered)
    workbench.toggleAirplaneMode()
    assertFalse(workbench.viewModel.uiState.value.isAirplaneMode)
    assertTrue(state.viewModels.surveyMap.uiState.value.isOnline)
    assertTrue(state.viewModels.surveyMap.uiState.value.isPlacesSearchAvailable)
    state.searchableListActions.clearListSearchQuery()
    assertEquals(0, state.viewModels.listSearch.uiState.value.places.size)
    state.searchableListActions.updateListSearchQuery("36.9")
    assertEquals(6, state.viewModels.listSearch.uiState.value.places.size)
  }

  @Test
  fun launchFormForEntity_opensFormWizardController_andCompletesSubmissionWithFormattedAnswers() {
    // The workbench loads its XForms preset into the mobile app.
    val state =
      WorkbenchState(
          MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
        )
        .mobile
    val initialShadeSubs =
      state.viewModels.dataCollection.uiState.value.entities
        .first { it.id == "0000000a-56d7-5c1d-9457-7457267120a3" }
        .submissions
        .size
    assertEquals(3, initialShadeSubs)
    assertFalse(state.viewModels.dataCollection.uiState.value.isDataCollectionFormOpen)
    assertEquals(null, state.viewModels.dataCollection.uiState.value.activeFormWizardController)

    // Launch form on 1:N entity `0000000a-56d7-5c1d-9457-7457267120a3`
    state.viewModels.dataCollection.launchFormForEntity(
      entityId = "0000000a-56d7-5c1d-9457-7457267120a3",
      formId = "form-shade-canopy-audit",
    )

    assertTrue(state.viewModels.dataCollection.uiState.value.isDataCollectionFormOpen)
    assertEquals(
      "0000000a-56d7-5c1d-9457-7457267120a3",
      state.viewModels.dataCollection.uiState.value.activeDataCollectionEntityId,
    )
    assertEquals(
      "form-shade-canopy-audit",
      state.viewModels.dataCollection.uiState.value.activeDataCollectionFormId,
    )
    val controller = state.viewModels.dataCollection.uiState.value.activeFormWizardController!!
    assertEquals("EUDR & Shade-Tree Field Survey", controller.formState.formDef.title)

    // Modify an answer via FormWizardController before finalizing
    controller.updateString("/data/dominant_shade_species", "Cordia africana & Grevillea")
    controller.updateInt("/data/surviving_saplings_count", 196)

    // Finalize and submit recordInstance
    state.viewModels.dataCollection.completeActiveFormSubmission(
      controller.formState.recordInstance
    )

    // Form runner closes and the entity's submissions list grows by 1 with the answered fields
    assertFalse(state.viewModels.dataCollection.uiState.value.isDataCollectionFormOpen)
    assertEquals(null, state.viewModels.dataCollection.uiState.value.activeFormWizardController)
    val updatedEntity =
      state.viewModels.dataCollection.uiState.value.entities.first {
        it.id == "0000000a-56d7-5c1d-9457-7457267120a3"
      }
    assertEquals(4, updatedEntity.submissions.size)
    val latestSubmission = updatedEntity.submissions.first()
    assertEquals("EUDR & Shade-Tree Field Survey", latestSubmission.formTitle)
    assertTrue(
      latestSubmission.fields.any {
        it.questionName == "dominant_shade_species" &&
          it.answerValue == "Cordia africana & Grevillea"
      }
    )
    assertTrue(
      latestSubmission.fields.any {
        it.questionName == "surviving_saplings_count" && it.answerValue == "196"
      }
    )
    assertTrue(state.notices.notice.value?.contains("Submitted") == true)
  }

  @Test
  fun syncStatusIndicators_showUploadingSyncedAndFailedOnEntitiesAndSubmissionsInLists() {
    val workbench =
      WorkbenchState(
        MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
      )
    val state = workbench.mobile
    state.setMainSurveyViewMode(MainSurveyViewMode.LIST)

    // 1. Default entities cover Uploading, Synced, and Failed states
    val nyr104 =
      state.viewModels.dataCollection.uiState.value.entities.first {
        it.id == "00000007-640e-5ed7-9874-4d6b6717f21d"
      }
    val shade201 =
      state.viewModels.dataCollection.uiState.value.entities.first {
        it.id == "0000000a-56d7-5c1d-9457-7457267120a3"
      }
    val nyr108 =
      state.viewModels.dataCollection.uiState.value.entities.first {
        it.id == "00000008-15d0-51f2-84eb-a72dfea5a747"
      }
    val nyr112 =
      state.viewModels.dataCollection.uiState.value.entities.first {
        it.id == "00000009-1699-51bb-b7a3-f90a60bedd4d"
      }
    val station01 =
      state.viewModels.dataCollection.uiState.value.entities.first {
        it.id == "0000000b-7633-5116-9d6d-50581002d8cf"
      }

    assertEquals(SyncStatus.SYNCED, nyr104.syncStatus)
    assertTrue(nyr104.isSynced)
    assertEquals(SyncStatus.UPLOADING, shade201.syncStatus)
    assertTrue(shade201.isUploading)
    assertEquals(SyncStatus.UPLOADING, nyr108.syncStatus)
    assertEquals(SyncStatus.FAILED, nyr112.syncStatus)
    assertTrue(nyr112.isFailed)
    assertEquals(SyncStatus.FAILED, station01.syncStatus)

    assertEquals(2, state.viewModels.dataCollection.uiState.value.entities.count { it.isUploading })
    assertEquals(1, state.viewModels.dataCollection.uiState.value.entities.count { it.isSynced })
    assertEquals(2, state.viewModels.dataCollection.uiState.value.entities.count { it.isFailed })

    // 2. Default submissions cover Uploading, Synced, and Failed states
    val subNyr104 =
      state.viewModels.dataCollection.uiState.value.allSubmissions.first {
        it.id == "00000022-c99b-5e7e-b810-c506ebf06f19"
      }
    val subShadeWave3 =
      state.viewModels.dataCollection.uiState.value.allSubmissions.first {
        it.id == "00000026-cca2-5594-b83f-02266f40048e"
      }
    val subShadeWave2 =
      state.viewModels.dataCollection.uiState.value.allSubmissions.first {
        it.id == "00000025-7bc5-5b86-839c-a76cee54cc81"
      }
    val subWshSep =
      state.viewModels.dataCollection.uiState.value.allSubmissions.first {
        it.id == "00000030-ca50-56e0-8458-9c5728954d52"
      }

    assertEquals(SyncStatus.SYNCED, subNyr104.syncStatus)
    assertTrue(subNyr104.isSynced)
    assertEquals(SyncStatus.UPLOADING, subShadeWave3.syncStatus)
    assertTrue(subShadeWave3.isUploading)
    assertEquals(SyncStatus.SYNCED, subShadeWave2.syncStatus)
    assertEquals(SyncStatus.FAILED, subWshSep.syncStatus)
    assertTrue(subWshSep.isFailed)

    assertEquals(
      3,
      state.viewModels.dataCollection.uiState.value.allSubmissions.count { it.isUploading },
    )
    assertEquals(
      5,
      state.viewModels.dataCollection.uiState.value.allSubmissions.count { it.isSynced },
    )
    assertEquals(
      1,
      state.viewModels.dataCollection.uiState.value.allSubmissions.count { it.isFailed },
    )

    // 3. Filtering lists by syncStatus label ("Uploading", "Synced", "Failed") filters entities
    state.searchableListActions.updateListSearchQuery("Uploading")
    assertEquals(2, state.viewModels.listSearch.uiState.value.entities.size)
    assertTrue(
      state.viewModels.listSearch.uiState.value.entities.all {
        it.syncStatus == SyncStatus.UPLOADING
      }
    )

    state.searchableListActions.updateListSearchQuery("Failed")
    assertEquals(2, state.viewModels.listSearch.uiState.value.entities.size)
    assertTrue(
      state.viewModels.listSearch.uiState.value.entities.all { it.syncStatus == SyncStatus.FAILED }
    )

    state.searchableListActions.updateListSearchQuery("Synced")
    assertEquals(1, state.viewModels.listSearch.uiState.value.entities.size)
    assertEquals(
      "00000007-640e-5ed7-9874-4d6b6717f21d",
      state.viewModels.listSearch.uiState.value.entities.first().id,
    )
    state.searchableListActions.clearListSearchQuery()

    // 4. Cycling / updating submission and entity sync statuses updates parent entity aggregate
    // state
    state.entityDetailsActions.cycleSubmissionSyncStatus("00000026-cca2-5594-b83f-02266f40048e")
    assertEquals(
      SyncStatus.SYNCED,
      state.viewModels.dataCollection.uiState.value.allSubmissions
        .first { it.id == "00000026-cca2-5594-b83f-02266f40048e" }
        .syncStatus,
    )
    assertEquals(
      SyncStatus.SYNCED,
      state.viewModels.dataCollection.uiState.value.entities
        .first { it.id == "0000000a-56d7-5c1d-9457-7457267120a3" }
        .syncStatus,
    )

    // 5. Syncing all Outbox mutations transitions remaining Uploading/Failed items to Synced
    state.viewModels.dashboard.syncAllOutboxMutations()
    assertTrue(
      state.viewModels.dataCollection.uiState.value.entities.all {
        it.syncStatus == SyncStatus.SYNCED
      }
    )
    assertTrue(
      state.viewModels.dataCollection.uiState.value.allSubmissions.all {
        it.syncStatus == SyncStatus.SYNCED
      }
    )
  }

  @Test
  fun availableSubmissions_offlineShowsOnlySubmissionsStoredOnDevice() {
    val workbench =
      WorkbenchState(
        MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
      )
    val state = workbench.mobile
    val entity =
      state.viewModels.dataCollection.uiState.value.entities.first { e ->
        e.submissions.any {
          state.viewModels.dashboard.uiState.value.isSubmissionStoredOnDevice(it)
        } &&
          e.submissions.any {
            !state.viewModels.dashboard.uiState.value.isSubmissionStoredOnDevice(it)
          }
      }

    assertEquals(
      entity.submissions.size,
      state.surveyRecords.availableSubmissionGroupsOf(entity).sumOf { it.submissions.size },
    )

    workbench.updateAirplaneMode(true)
    val offline = state.surveyRecords.availableSubmissionGroupsOf(entity).flatMap { it.submissions }
    assertTrue(offline.isNotEmpty())
    assertTrue(offline.size < entity.submissions.size)
    assertTrue(
      offline.all { state.viewModels.dashboard.uiState.value.isSubmissionStoredOnDevice(it) }
    )

    // Other collectors' submissions (e.g. Samuel Kariuki on 00000008-15d0-51f2-84eb-a72dfea5a747)
    // require connectivity
    val nyr108 =
      state.viewModels.dataCollection.uiState.value.entities.first {
        it.id == "00000008-15d0-51f2-84eb-a72dfea5a747"
      }
    assertTrue(state.surveyRecords.availableSubmissionGroupsOf(nyr108).isEmpty())
  }

  @Test
  fun offlineMode_preventsOnlineOnlyFlows_forDownloadsAndUploads() {
    val workbench =
      WorkbenchState(
        MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
      )
    val state = workbench.mobile
    workbench.updateAirplaneMode(true)
    assertFalse(state.viewModels.surveyMap.uiState.value.isOnline)

    // 1. Outbox uploads are prevented while offline
    val initialOutboxCount = state.viewModels.dashboard.uiState.value.outboxMutationCount
    state.viewModels.dashboard.syncMutationNow("mut-outbox-04")
    assertEquals(initialOutboxCount, state.viewModels.dashboard.uiState.value.outboxMutationCount)
    state.viewModels.dashboard.syncAllOutboxMutations()
    assertEquals(initialOutboxCount, state.viewModels.dashboard.uiState.value.outboxMutationCount)
    assertTrue(state.notices.notice.value?.contains("offline", ignoreCase = true) == true)

    // 2. Downloading an undownloaded survey is prevented while offline, whereas opening an already
    // downloaded survey succeeds
    val undownloadedSurvey =
      state.viewModels.dashboard.uiState.value.surveys.first { !it.isDownloaded }
    state.viewModels.onboarding.downloadSurvey(undownloadedSurvey.id)
    assertFalse(
      state.viewModels.dashboard.uiState.value.surveys
        .first { it.id == undownloadedSurvey.id }
        .isDownloaded
    )
  }

  @Test
  fun repeatGroupQuestions_savedInCompletedSubmissionAcrossInstances() {
    // The workbench loads its XForms preset into the mobile app.
    val state =
      WorkbenchState(
          MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
        )
        .mobile
    state.viewModels.dataCollection.launchFormForEntity(
      entityId = "0000000a-56d7-5c1d-9457-7457267120a3",
      formId = "form-shade-canopy-audit",
    )
    val controller =
      assertNotNull(state.viewModels.dataCollection.uiState.value.activeFormWizardController)

    // Edit Repeat #1 answers and add Repeat #2
    controller.updateString("/data/sampled_trees[1]/tree_tag", "TREE-101")
    controller.updateDouble("/data/sampled_trees[1]/stem_dbh_cm", 44.5)

    controller.addRepeatInstanceAndOpen("/data/sampled_trees")
    controller.updateString("/data/sampled_trees[2]/tree_tag", "TREE-102")
    controller.updateDouble("/data/sampled_trees[2]/stem_dbh_cm", 51.0)

    state.viewModels.dataCollection.completeActiveFormSubmission()

    val updatedPlot =
      state.viewModels.dataCollection.uiState.value.entities.first {
        it.id == "0000000a-56d7-5c1d-9457-7457267120a3"
      }
    val latestSubmission = updatedPlot.submissions.first()

    // Ensure no group/repeat container nodes were emitted as "(Unanswered)"
    assertFalse(latestSubmission.fields.any { it.answerValue == "(Unanswered)" })

    val tree1Tag =
      assertNotNull(
        latestSubmission.fields.firstOrNull { it.questionName == "sampled_trees[1]/tree_tag" }
      )
    assertEquals("TREE-101", tree1Tag.answerValue)
    assertTrue(tree1Tag.questionLabel.contains("#1"))

    val tree1Dbh =
      assertNotNull(
        latestSubmission.fields.firstOrNull { it.questionName == "sampled_trees[1]/stem_dbh_cm" }
      )
    assertEquals("44.5", tree1Dbh.answerValue)

    val tree2Tag =
      assertNotNull(
        latestSubmission.fields.firstOrNull { it.questionName == "sampled_trees[2]/tree_tag" }
      )
    assertEquals("TREE-102", tree2Tag.answerValue)
    assertTrue(tree2Tag.questionLabel.contains("#2"))

    val tree2Dbh =
      assertNotNull(
        latestSubmission.fields.firstOrNull { it.questionName == "sampled_trees[2]/stem_dbh_cm" }
      )
    assertEquals("51.0", tree2Dbh.answerValue)
  }

  @Test
  fun retryFailedUploads_cyclesTheFailedRecordsOnly() {
    val dataHolder = sampleDataHolder()
    val state =
      WorkbenchState(MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = dataHolder))
        .mobile
    val failedEntity = state.viewModels.dataCollection.uiState.value.entities.first()
    val syncedEntity =
      state.viewModels.dataCollection.uiState.value.entities.first { it.id != failedEntity.id }
    dataHolder.launch {
      dataHolder.surveyRepository.updateEntitySyncStatus(failedEntity.id, SyncStatus.FAILED)
      dataHolder.surveyRepository.updateEntitySyncStatus(syncedEntity.id, SyncStatus.SYNCED)
    }

    state.retryFailedUploads()

    assertEquals(
      SyncStatus.UPLOADING,
      state.viewModels.dataCollection.uiState.value.entities
        .first { it.id == failedEntity.id }
        .syncStatus,
    )
    assertEquals(
      SyncStatus.SYNCED,
      state.viewModels.dataCollection.uiState.value.entities
        .first { it.id == syncedEntity.id }
        .syncStatus,
    )
    assertTrue(
      state.viewModels.dashboard.uiState.value.mutations.none {
        it.state == MutationSyncState.FAILED
      }
    )
  }

  @Test
  fun retryFailedUploads_withSyncEngineEnabled_doesNotCycleSyncedRecordsIntoUploading() {
    val service = SeedUserDataService()
    val dataHolder =
      AppDataHolder(
        userDataServiceUrl = "https://collect-one.example.test",
        grpcClient = service.client(),
        syncDownloadedSurveysOnly = true,
        enableSyncEngine = true,
      )
    val state =
      WorkbenchState(MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = dataHolder))
        .mobile
    try {
      state.retryFailedUploads()
      state.entityDetailsActions.cycleEntitySyncStatus(
        state.viewModels.dataCollection.uiState.value.entities.first().id
      )

      assertTrue(
        state.viewModels.dataCollection.uiState.value.entities.all {
          it.syncStatus == SyncStatus.SYNCED
        }
      )
      assertEquals(0, state.viewModels.dashboard.uiState.value.outboxMutationCount)
    } finally {
      state.close()
    }
  }

  /** The app's record surfaces as the Main Survey builds them now. */
  private val MobileAppState.surveyRecords
    get() =
      surveyRecordsUiStatesOf(
        viewModels = viewModels,
        data = dataHolder.appData.value,
        notice = notices.notice.value,
        isAlwaysOnline = false,
      )
}
