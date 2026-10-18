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
package org.groundplatform.v2.workbench.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.groundplatform.v2.app.data.datasource.local.store.sampleDataHolder
import org.groundplatform.v2.app.domain.model.AppScreen
import org.groundplatform.v2.app.domain.model.ListFilterTab
import org.groundplatform.v2.app.domain.model.LocationLockState
import org.groundplatform.v2.app.domain.model.MainSurveyViewMode
import org.groundplatform.v2.app.domain.model.NavigationTargetKind
import org.groundplatform.v2.app.domain.model.SurveyPlaceItem
import org.groundplatform.v2.app.domain.model.SyncStatus
import org.groundplatform.v2.app.domain.model.parsePlaceCoordinates
import org.groundplatform.v2.mobile.ui.MobileAppState

/** Mobile app behavior driven through the workbench ([WorkbenchState]). */
class WorkbenchMobileStateTest {

  @Test
  fun downloadSurvey_marksSurveyAsDownloaded() {
    val workbench =
      WorkbenchState(
        MobileAppState(initialScreen = AppScreen.DOWNLOAD_SURVEY, dataHolder = sampleDataHolder())
      )
    val state = workbench.mobile
    val targetId = "00000035-caf4-571e-b907-f506f92666ab"
    assertFalse(state.surveys.first { it.id == targetId }.isDownloaded)

    state.downloadSurvey(targetId)
    assertTrue(state.surveys.first { it.id == targetId }.isDownloaded)

    // Toggle downloaded indicator off and back on
    workbench.toggleSurveyDownloaded(targetId)
    assertFalse(state.surveys.first { it.id == targetId }.isDownloaded)
  }

  @Test
  fun listViewSearch_filtersPlacesAndEntitiesAndRemovesFormSubmissionSearch() {
    val workbench =
      WorkbenchState(
        MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())
      )
    val state = workbench.mobile
    state.setMainSurveyViewMode(MainSurveyViewMode.LIST)

    // ListFilterTab contains ALL, PLACES ("Places"), ENTITIES ("Map features")
    assertEquals(
      listOf(ListFilterTab.ALL, ListFilterTab.PLACES, ListFilterTab.ENTITIES),
      ListFilterTab.entries,
    )
    assertEquals("Places", ListFilterTab.PLACES.label)
    assertEquals("Map features", ListFilterTab.ENTITIES.label)
    assertEquals("Map features", state.activeEntitiesTabLabel)
    assertEquals("map features", state.activeEntitiesCountNoun)
    assertEquals(
      "Coffee Parcel",
      state.entitySingularTypeLabel("00000007-640e-5ed7-9874-4d6b6717f21d"),
    )
    assertEquals(
      "Shade Tree Monitoring Plot",
      state.entitySingularTypeLabel("0000000a-56d7-5c1d-9457-7457267120a3"),
    )
    assertEquals(
      "Cooperative Washing Station",
      state.entitySingularTypeLabel("0000000b-7633-5116-9d6d-50581002d8cf"),
    )

    // When filtered down to a single visible entity dataset layer, dynamic domain naming activates
    state.toggleLayerVisibility("layer-shade-transects")
    state.toggleLayerVisibility("layer-water-points")
    assertEquals("Coffee Parcels", state.activeEntitiesTabLabel)
    assertEquals("coffee parcels", state.activeEntitiesCountNoun)
    state.toggleLayerVisibility("layer-shade-transects")
    state.toggleLayerVisibility("layer-water-points")
    assertEquals("Map features", state.activeEntitiesTabLabel)

    // Unfiltered counts: 0 places when search query is blank, 5 entities grouped across 3 datasets;
    // form submission search is removed since there is no unique key to identify them with
    assertEquals(0, state.filteredListPlaces.size)
    assertTrue(state.filteredListPlaces.isEmpty())
    assertEquals(5, state.filteredListEntities.size)
    assertEquals(3, state.groupedFilteredListEntities.size)
    assertTrue(state.filteredListSubmissions.isEmpty())
    assertTrue(state.groupedFilteredListSubmissions.isEmpty())

    // Search for a geographic place ("Karima Hill" / "Karima Hill Forest Reserve")
    state.updateListSearchQuery("Karima Hill")
    assertEquals(1, state.filteredListPlaces.size)
    assertEquals("Karima Hill Forest Reserve", state.filteredListPlaces.first().name)
    assertTrue(state.filteredListEntities.isEmpty())

    // Verify coordinate parsing helper handles both degree/cardinal and signed decimal strings
    val parsedCardinal = assertNotNull(parsePlaceCoordinates("0.5012° S, 36.9324° E"))
    assertEquals(-0.5012, parsedCardinal.first, 0.0001)
    assertEquals(36.9324, parsedCardinal.second, 0.0001)
    val parsedDecimal = assertNotNull(parsePlaceCoordinates("-0.5012, 36.9324"))
    assertEquals(-0.5012, parsedDecimal.first, 0.0001)
    assertEquals(36.9324, parsedDecimal.second, 0.0001)

    // Selecting a place centers/zooms the map on the place's bounds, switches locationLockState to
    // PANNED,
    // and collapses the bottom sheet. A country zooms out much farther than a village/forest.
    state.selectPlace("place-kenya-country")
    val kenyaZoom = assertNotNull(state.selectedPlace).targetZoom
    val kenyaZoomDelta = state.mapZoomDelta
    assertTrue(kenyaZoom < 6.0f)

    state.selectPlace("place-iriaini-market")
    val villageZoom = assertNotNull(state.selectedPlace).targetZoom
    val villageZoomDelta = state.mapZoomDelta
    assertTrue(villageZoom > 13.0f)
    assertTrue(villageZoom > kenyaZoom)
    assertTrue(villageZoomDelta > kenyaZoomDelta)

    // Clicking away on the map (selectEntity(null)) dismisses the selected place chip
    state.selectEntity(null)
    assertEquals(null, state.selectedPlace)

    state.selectPlace("place-karima-forest")
    assertEquals("place-karima-forest", state.selectedPlace?.id)
    assertEquals(MainSurveyViewMode.MAP, state.mainViewMode)
    assertEquals(LocationLockState.PANNED, state.locationLockState)
    assertFalse(state.isCameraFollowingUser)
    assertFalse(state.isEntityBottomSheetExpanded)

    // Straight-line wayfinding to a place works
    state.startNavigationToPlace("place-karima-forest")
    val placeNav = assertNotNull(state.activeNavigation)
    assertEquals(NavigationTargetKind.PLACE, placeNav.targetKind)
    assertEquals("place-karima-forest", placeNav.targetId)
    state.stopNavigation()
    state.clearSelectedPlace()

    // Search for decimal coordinates creates an ad-hoc coordinate place match
    state.updateListSearchQuery("-0.4210, 36.9520")
    assertTrue(state.filteredListPlaces.any { it.categoryLabel == "GPS Coordinates" })

    // Search for "Kamau" (matches entity label & owner property)
    state.updateListSearchQuery("Kamau")
    assertEquals(1, state.filteredListEntities.size)
    assertEquals("Plot NYR-104 • Kamau Family Parcel", state.filteredListEntities.first().label)

    // Filtering by ListFilterTab.PLACES hides entities; places remain empty when query is blank
    state.clearListSearchQuery()
    state.selectListFilterTab(ListFilterTab.PLACES)
    assertEquals(0, state.filteredListPlaces.size)
    assertTrue(state.filteredListEntities.isEmpty())
    state.updateListSearchQuery("Nyeri")
    assertTrue(state.filteredListPlaces.isNotEmpty())
    assertTrue(state.filteredListEntities.isEmpty())
    state.clearListSearchQuery()

    state.selectListFilterTab(ListFilterTab.ENTITIES)
    assertTrue(state.filteredListPlaces.isEmpty())
    assertEquals(5, state.filteredListEntities.size)
    state.selectListFilterTab(ListFilterTab.ALL)

    // Live Places search results merge into filteredListPlaces
    state.updateListSearchQuery("Aberdare National Park")
    state.onPlacesSearchResults(
      query = "Aberdare National Park",
      results =
        listOf(
          SurveyPlaceItem(
            id = "place.aberdare.901",
            name = "Aberdare National Park",
            categoryLabel = "National Park",
            regionSubtitle = "Nyeri County, Kenya",
            coordinatesLabel = "0.3833° S, 36.7000° E",
            normalizedX = 0.05f,
            normalizedY = 0.05f,
            longitude = 36.7,
            latitude = -0.3833,
          )
        ),
    )
    assertTrue(
      state.filteredListPlaces.any {
        it.name == "Aberdare National Park" && it.sourceLabel == "Places API"
      }
    )

    // Workbench offline simulation disables Mapbox Places search while keeping local map features
    // searchable
    state.selectListFilterTab(ListFilterTab.PLACES)
    assertEquals(ListFilterTab.PLACES, state.listFilterTab)
    workbench.updateAirplaneMode(true)
    assertTrue(workbench.uiState.isAirplaneMode)
    assertFalse(state.isOnline)
    assertFalse(state.isPlacesSearchAvailable)
    assertEquals(ListFilterTab.ALL, state.listFilterTab)
    assertTrue(state.filteredListPlaces.isEmpty())
    state.selectListFilterTab(ListFilterTab.PLACES)
    assertEquals(ListFilterTab.ALL, state.listFilterTab)

    // Searching local features ("Kamau") works while offline
    state.updateListSearchQuery("Kamau")
    assertTrue(state.filteredListPlaces.isEmpty())
    assertEquals(1, state.filteredListEntities.size)
    assertEquals("Plot NYR-104 • Kamau Family Parcel", state.filteredListEntities.first().label)

    // Turning Airplane mode back off in the workbench restores Places search availability (empty
    // when query blank, populated when query entered)
    workbench.toggleAirplaneMode()
    assertFalse(workbench.uiState.isAirplaneMode)
    assertTrue(state.isOnline)
    assertTrue(state.isPlacesSearchAvailable)
    state.clearListSearchQuery()
    assertEquals(0, state.filteredListPlaces.size)
    state.updateListSearchQuery("36.9")
    assertEquals(6, state.filteredListPlaces.size)
  }

  @Test
  fun launchFormForEntity_opensFormWizardController_andCompletesSubmissionWithFormattedAnswers() {
    // The workbench loads its XForms preset into the mobile app.
    val state =
      WorkbenchState(
          MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())
        )
        .mobile
    val initialShadeSubs =
      state.entities.first { it.id == "0000000a-56d7-5c1d-9457-7457267120a3" }.submissions.size
    assertEquals(3, initialShadeSubs)
    assertFalse(state.isDataCollectionFormOpen)
    assertEquals(null, state.activeFormWizardController)

    // Launch form on 1:N entity `0000000a-56d7-5c1d-9457-7457267120a3`
    state.launchFormForEntity(
      entityId = "0000000a-56d7-5c1d-9457-7457267120a3",
      formId = "form-shade-canopy-audit",
    )

    assertTrue(state.isDataCollectionFormOpen)
    assertEquals("0000000a-56d7-5c1d-9457-7457267120a3", state.activeDataCollectionEntityId)
    assertEquals("form-shade-canopy-audit", state.activeDataCollectionFormId)
    val controller = state.activeFormWizardController!!
    assertEquals("EUDR & Shade-Tree Field Survey", controller.formState.formDef.title)

    // Modify an answer via FormWizardController before finalizing
    controller.updateString("/data/dominant_shade_species", "Cordia africana & Grevillea")
    controller.updateInt("/data/surviving_saplings_count", 196)

    // Finalize and submit recordInstance
    state.completeActiveFormSubmission(controller.formState.recordInstance)

    // Form runner closes and the entity's submissions list grows by 1 with the answered fields
    assertFalse(state.isDataCollectionFormOpen)
    assertEquals(null, state.activeFormWizardController)
    val updatedEntity = state.entities.first { it.id == "0000000a-56d7-5c1d-9457-7457267120a3" }
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
    assertTrue(state.activeSurveyNotice?.contains("Submitted") == true)
  }

  @Test
  fun syncStatusIndicators_showUploadingSyncedAndFailedOnEntitiesAndSubmissionsInLists() {
    val workbench =
      WorkbenchState(
        MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())
      )
    val state = workbench.mobile
    state.setMainSurveyViewMode(MainSurveyViewMode.LIST)

    // 1. Default entities cover Uploading, Synced, and Failed states
    val nyr104 = state.entities.first { it.id == "00000007-640e-5ed7-9874-4d6b6717f21d" }
    val shade201 = state.entities.first { it.id == "0000000a-56d7-5c1d-9457-7457267120a3" }
    val nyr108 = state.entities.first { it.id == "00000008-15d0-51f2-84eb-a72dfea5a747" }
    val nyr112 = state.entities.first { it.id == "00000009-1699-51bb-b7a3-f90a60bedd4d" }
    val station01 = state.entities.first { it.id == "0000000b-7633-5116-9d6d-50581002d8cf" }

    assertEquals(SyncStatus.SYNCED, nyr104.syncStatus)
    assertTrue(nyr104.isSynced)
    assertEquals(SyncStatus.UPLOADING, shade201.syncStatus)
    assertTrue(shade201.isUploading)
    assertEquals(SyncStatus.UPLOADING, nyr108.syncStatus)
    assertEquals(SyncStatus.FAILED, nyr112.syncStatus)
    assertTrue(nyr112.isFailed)
    assertEquals(SyncStatus.FAILED, station01.syncStatus)

    assertEquals(2, state.uploadingEntityCount)
    assertEquals(1, state.syncedEntityCount)
    assertEquals(2, state.failedEntityCount)

    // 2. Default submissions cover Uploading, Synced, and Failed states
    val subNyr104 = state.allSubmissions.first { it.id == "00000022-c99b-5e7e-b810-c506ebf06f19" }
    val subShadeWave3 =
      state.allSubmissions.first { it.id == "00000026-cca2-5594-b83f-02266f40048e" }
    val subShadeWave2 =
      state.allSubmissions.first { it.id == "00000025-7bc5-5b86-839c-a76cee54cc81" }
    val subWshSep = state.allSubmissions.first { it.id == "00000030-ca50-56e0-8458-9c5728954d52" }

    assertEquals(SyncStatus.SYNCED, subNyr104.syncStatus)
    assertTrue(subNyr104.isSynced)
    assertEquals(SyncStatus.UPLOADING, subShadeWave3.syncStatus)
    assertTrue(subShadeWave3.isUploading)
    assertEquals(SyncStatus.SYNCED, subShadeWave2.syncStatus)
    assertEquals(SyncStatus.FAILED, subWshSep.syncStatus)
    assertTrue(subWshSep.isFailed)

    assertEquals(3, state.uploadingSubmissionCount)
    assertEquals(5, state.syncedSubmissionCount)
    assertEquals(1, state.failedSubmissionCount)

    // 3. Filtering lists by syncStatus label ("Uploading", "Synced", "Failed") filters entities
    // (form submission search is removed)
    state.updateListSearchQuery("Uploading")
    assertEquals(2, state.filteredListEntities.size)
    assertTrue(state.filteredListEntities.all { it.syncStatus == SyncStatus.UPLOADING })
    assertTrue(state.filteredListSubmissions.isEmpty())

    state.updateListSearchQuery("Failed")
    assertEquals(2, state.filteredListEntities.size)
    assertTrue(state.filteredListEntities.all { it.syncStatus == SyncStatus.FAILED })
    assertTrue(state.filteredListSubmissions.isEmpty())

    state.updateListSearchQuery("Synced")
    assertEquals(1, state.filteredListEntities.size)
    assertEquals("00000007-640e-5ed7-9874-4d6b6717f21d", state.filteredListEntities.first().id)
    assertTrue(state.filteredListSubmissions.isEmpty())
    state.clearListSearchQuery()

    // 4. Cycling / updating submission and entity sync statuses updates parent entity aggregate
    // state
    state.cycleSubmissionSyncStatus("00000026-cca2-5594-b83f-02266f40048e")
    assertEquals(
      SyncStatus.SYNCED,
      state.allSubmissions.first { it.id == "00000026-cca2-5594-b83f-02266f40048e" }.syncStatus,
    )
    assertEquals(
      SyncStatus.SYNCED,
      state.entities.first { it.id == "0000000a-56d7-5c1d-9457-7457267120a3" }.syncStatus,
    )

    // 5. Syncing all Outbox mutations transitions remaining Uploading/Failed items to Synced
    state.syncAllOutboxMutations()
    assertTrue(state.entities.all { it.syncStatus == SyncStatus.SYNCED })
    assertTrue(state.allSubmissions.all { it.syncStatus == SyncStatus.SYNCED })
  }

  @Test
  fun availableSubmissions_offlineShowsOnlySubmissionsStoredOnDevice() {
    val workbench =
      WorkbenchState(
        MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())
      )
    val state = workbench.mobile
    val entity =
      state.entities.first { e ->
        e.submissions.any { state.isSubmissionStoredOnDevice(it) } &&
          e.submissions.any { !state.isSubmissionStoredOnDevice(it) }
      }

    assertEquals(
      entity.submissions.size,
      state.availableGroupedSubmissionsForEntity(entity).sumOf { it.submissions.size },
    )

    workbench.updateAirplaneMode(true)
    val offline = state.availableGroupedSubmissionsForEntity(entity).flatMap { it.submissions }
    assertTrue(offline.isNotEmpty())
    assertTrue(offline.size < entity.submissions.size)
    assertTrue(offline.all { state.isSubmissionStoredOnDevice(it) })

    // Other collectors' submissions (e.g. Samuel Kariuki on 00000008-15d0-51f2-84eb-a72dfea5a747)
    // require connectivity
    val nyr108 = state.entities.first { it.id == "00000008-15d0-51f2-84eb-a72dfea5a747" }
    assertTrue(state.availableGroupedSubmissionsForEntity(nyr108).isEmpty())
  }

  @Test
  fun offlineMode_preventsOnlineOnlyFlows_forDownloadsAndUploads() {
    val workbench =
      WorkbenchState(
        MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())
      )
    val state = workbench.mobile
    workbench.updateAirplaneMode(true)
    assertFalse(state.isOnline)

    // 1. Outbox uploads are prevented while offline
    val initialOutboxCount = state.outboxMutationCount
    state.syncMutationNow("mut-outbox-04")
    assertEquals(initialOutboxCount, state.outboxMutationCount)
    state.syncAllOutboxMutations()
    assertEquals(initialOutboxCount, state.outboxMutationCount)
    assertTrue(state.activeSurveyNotice?.contains("offline", ignoreCase = true) == true)

    // 2. Downloading an undownloaded survey is prevented while offline, whereas opening an already
    // downloaded survey succeeds
    val undownloadedSurvey = state.surveys.first { !it.isDownloaded }
    state.downloadSurvey(undownloadedSurvey.id)
    assertFalse(state.surveys.first { it.id == undownloadedSurvey.id }.isDownloaded)

    // 3. Downloading an undownloaded offline tile package is prevented while offline
    val undownloadedTilePkg = state.offlineTilePackages.first { !it.isDownloaded }
    state.toggleOfflineTilePackage(undownloadedTilePkg.id)
    assertFalse(state.offlineTilePackages.first { it.id == undownloadedTilePkg.id }.isDownloaded)
  }

  @Test
  fun repeatGroupQuestions_savedInCompletedSubmissionAcrossInstances() {
    // The workbench loads its XForms preset into the mobile app.
    val state =
      WorkbenchState(
          MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())
        )
        .mobile
    state.launchFormForEntity(
      entityId = "0000000a-56d7-5c1d-9457-7457267120a3",
      formId = "form-shade-canopy-audit",
    )
    val controller = assertNotNull(state.activeFormWizardController)

    // Edit Repeat #1 answers and add Repeat #2
    controller.updateString("/data/sampled_trees[1]/tree_tag", "TREE-101")
    controller.updateDouble("/data/sampled_trees[1]/stem_dbh_cm", 44.5)

    controller.addRepeatInstanceAndOpen("/data/sampled_trees")
    controller.updateString("/data/sampled_trees[2]/tree_tag", "TREE-102")
    controller.updateDouble("/data/sampled_trees[2]/stem_dbh_cm", 51.0)

    state.completeActiveFormSubmission()

    val updatedPlot = state.entities.first { it.id == "0000000a-56d7-5c1d-9457-7457267120a3" }
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
}
