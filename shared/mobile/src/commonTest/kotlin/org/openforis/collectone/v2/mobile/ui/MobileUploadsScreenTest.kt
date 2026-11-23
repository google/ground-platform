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
package org.openforis.collectone.v2.mobile.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.domain.model.MutationOperationKind
import org.openforis.collectone.v2.app.domain.model.MutationSyncState
import org.openforis.collectone.v2.app.domain.model.UploadStatusFilter
import org.openforis.collectone.v2.mobile.ui.navigation.MobileRoute

/** The Uploads screen: listing, filtering, and syncing the mutations waiting in the outbox. */
class MobileUploadsScreenTest {
  @Test
  fun drawerOpenUploads_closesTheDrawerOnTheUploadsSubView() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
    state.updateDrawerOpen(true)

    state.drawerOpenUploads()

    assertFalse(state.isDrawerOpen)
    assertEquals(MobileRoute.Uploads, state.currentRoute)
  }

  @Test
  fun drawerOpenUploads_listsEveryMutationUnfiltered() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())

    state.drawerOpenUploads()

    assertNull(state.viewModels.dashboard.uiState.value.selectedUploadStatusFilter)
    assertEquals(9, state.viewModels.dashboard.uiState.value.filteredUploadMutations.size)
  }

  @Test
  fun drawerOpenUploads_withAStatusFilter_selectsThatFilter() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())

    state.viewModels.dashboard.showUploads(UploadStatusFilter.UPLOADED)

    assertEquals(
      UploadStatusFilter.UPLOADED,
      state.viewModels.dashboard.uiState.value.selectedUploadStatusFilter,
    )
  }

  @Test
  fun outboxMutations_listNewestFirst() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())

    val outbox = state.viewModels.dashboard.uiState.value.outboxMutations

    assertEquals(
      listOf("mut-outbox-04", "mut-outbox-03", "mut-outbox-02", "mut-outbox-01"),
      outbox.map { it.id },
    )
    assertEquals(outbox.sortedByDescending { it.operationTimestamp }, outbox)
  }

  @Test
  fun outboxMutations_haveAStartedButNoCompletedTimestamp() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())

    assertEquals(4, state.viewModels.dashboard.uiState.value.outboxMutationCount)
    state.viewModels.dashboard.uiState.value.outboxMutations.forEach { item ->
      assertTrue(item.isOutbox)
      assertFalse(item.isUploaded)
      assertTrue(item.operationTimestamp.isNotBlank())
      assertTrue(item.startedTimestamp?.isNotBlank() == true)
      assertNull(item.completedTimestamp)
    }
  }

  @Test
  fun uploadedMutations_listNewestFirst() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())

    val uploaded = state.viewModels.dashboard.uiState.value.uploadedMutations

    assertEquals(
      listOf(
        "mut-uploaded-05",
        "mut-uploaded-04",
        "mut-uploaded-03",
        "mut-uploaded-02",
        "mut-uploaded-01",
      ),
      uploaded.map { it.id },
    )
    assertEquals(uploaded.sortedByDescending { it.operationTimestamp }, uploaded)
  }

  @Test
  fun uploadedMutations_haveStartedAndCompletedTimestamps() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())

    assertEquals(5, state.viewModels.dashboard.uiState.value.uploadedMutationCount)
    state.viewModels.dashboard.uiState.value.uploadedMutations.forEach { item ->
      assertTrue(item.isUploaded)
      assertFalse(item.isOutbox)
      assertEquals(MutationSyncState.UPLOADED, item.state)
      assertTrue(item.operationTimestamp.isNotBlank())
      assertTrue(item.startedTimestamp?.isNotBlank() == true)
      assertTrue(item.completedTimestamp?.isNotBlank() == true)
    }
  }

  @Test
  fun syncMutationNow_movesTheMutationToTheTopOfUploaded() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())

    state.viewModels.dashboard.syncMutationNow("mut-outbox-04")

    assertEquals(3, state.viewModels.dashboard.uiState.value.outboxMutationCount)
    assertEquals(6, state.viewModels.dashboard.uiState.value.uploadedMutationCount)
    val synced = state.viewModels.dashboard.uiState.value.uploadedMutations.first()
    assertEquals("mut-outbox-04", synced.id)
    assertEquals(MutationSyncState.UPLOADED, synced.state)
    assertNotNull(synced.completedTimestamp)
  }

  @Test
  fun syncAllOutboxMutations_movesTheWholeOutboxToUploaded() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())

    state.viewModels.dashboard.syncAllOutboxMutations()

    assertEquals(0, state.viewModels.dashboard.uiState.value.outboxMutationCount)
    assertEquals(9, state.viewModels.dashboard.uiState.value.uploadedMutationCount)
  }

  @Test
  fun uploadStatusFilter_offersPendingInProgressUploadedAndFailedChips() {
    // Their English labels are checked in shared/app's DomainStringsParityTest.
    assertEquals(
      listOf(
        UploadStatusFilter.PENDING,
        UploadStatusFilter.IN_PROGRESS,
        UploadStatusFilter.UPLOADED,
        UploadStatusFilter.FAILED,
      ),
      UploadStatusFilter.entries,
    )
  }

  @Test
  fun uploadCountForFilter_countsTheMutationsInEachStatus() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())

    assertEquals(
      1,
      state.viewModels.dashboard.uiState.value.uploadCountForFilter(UploadStatusFilter.PENDING),
    )
    assertEquals(
      2,
      state.viewModels.dashboard.uiState.value.uploadCountForFilter(UploadStatusFilter.IN_PROGRESS),
    )
    assertEquals(
      5,
      state.viewModels.dashboard.uiState.value.uploadCountForFilter(UploadStatusFilter.UPLOADED),
    )
    assertEquals(
      1,
      state.viewModels.dashboard.uiState.value.uploadCountForFilter(UploadStatusFilter.FAILED),
    )
  }

  @Test
  fun toggleUploadStatusFilter_pending_listsOnlyThePendingMutation() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())

    state.viewModels.dashboard.toggleUploadStatusFilter(UploadStatusFilter.PENDING)

    assertEquals(
      UploadStatusFilter.PENDING,
      state.viewModels.dashboard.uiState.value.selectedUploadStatusFilter,
    )
    assertEquals(
      listOf("mut-outbox-02"),
      state.viewModels.dashboard.uiState.value.filteredUploadMutations.map { it.id },
    )
  }

  @Test
  fun toggleUploadStatusFilter_failed_listsOnlyTheFailedMutation() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())

    state.viewModels.dashboard.toggleUploadStatusFilter(UploadStatusFilter.FAILED)

    assertEquals(1, state.viewModels.dashboard.uiState.value.filteredUploadMutations.size)
    assertEquals(
      MutationOperationKind.DELETE_SUBMISSION,
      state.viewModels.dashboard.uiState.value.filteredUploadMutations.first().operationKind,
    )
  }

  @Test
  fun toggleUploadStatusFilter_anotherFilter_replacesTheActiveOne() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
    state.viewModels.dashboard.toggleUploadStatusFilter(UploadStatusFilter.PENDING)

    state.viewModels.dashboard.toggleUploadStatusFilter(UploadStatusFilter.IN_PROGRESS)

    assertEquals(
      UploadStatusFilter.IN_PROGRESS,
      state.viewModels.dashboard.uiState.value.selectedUploadStatusFilter,
    )
    assertEquals(2, state.viewModels.dashboard.uiState.value.filteredUploadMutations.size)
  }

  @Test
  fun toggleUploadStatusFilter_theActiveFilter_clearsIt() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
    state.viewModels.dashboard.toggleUploadStatusFilter(UploadStatusFilter.UPLOADED)

    state.viewModels.dashboard.toggleUploadStatusFilter(UploadStatusFilter.UPLOADED)

    assertNull(state.viewModels.dashboard.uiState.value.selectedUploadStatusFilter)
    assertEquals(9, state.viewModels.dashboard.uiState.value.filteredUploadMutations.size)
  }

  @Test
  fun openUploadsForEntity_listsOnlyThatEntitysMutations() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
    val entityId = state.viewModels.dashboard.uiState.value.mutations.first().entityId
    val expected =
      state.viewModels.dashboard.uiState.value.mutations.count { it.entityId == entityId }

    state.viewModels.dashboard.openUploadsForEntity(entityId)

    assertEquals(MobileRoute.Uploads, state.currentRoute)
    assertEquals(entityId, state.viewModels.dashboard.uiState.value.uploadsEntityFilter?.id)
    assertEquals(expected, state.viewModels.dashboard.uiState.value.filteredUploadMutations.size)
    assertEquals(expected, state.viewModels.dashboard.uiState.value.uploadCountForEntity(entityId))
    assertTrue(
      state.viewModels.dashboard.uiState.value.filteredUploadMutations.all {
        it.entityId == entityId
      }
    )
  }

  @Test
  fun clearUploadsEntityFilter_listsEveryMutationAgain() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
    state.viewModels.dashboard.openUploadsForEntity(
      state.viewModels.dashboard.uiState.value.mutations.first().entityId
    )

    state.viewModels.dashboard.clearUploadsEntityFilter()

    assertEquals(
      state.viewModels.dashboard.uiState.value.mutations.size,
      state.viewModels.dashboard.uiState.value.filteredUploadMutations.size,
    )
  }

  @Test
  fun closeDrawerSubView_clearsTheEntityFilter() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
    state.viewModels.dashboard.openUploadsForEntity(
      state.viewModels.dashboard.uiState.value.mutations.first().entityId
    )

    state.closeDrawerSubView()

    assertNull(state.viewModels.dashboard.uiState.value.uploadsEntityFilterId)
  }

  @Test
  fun drawerOpenUploads_clearsTheEntityFilter() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
    state.viewModels.dashboard.openUploadsForEntity(
      state.viewModels.dashboard.uiState.value.mutations.first().entityId
    )

    state.drawerOpenUploads()

    assertNull(state.viewModels.dashboard.uiState.value.uploadsEntityFilterId)
  }
}
