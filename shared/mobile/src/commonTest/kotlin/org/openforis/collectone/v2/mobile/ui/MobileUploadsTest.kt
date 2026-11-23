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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.data.datasource.remote.SeedUserDataService
import org.openforis.collectone.v2.app.di.AppDataHolder
import org.openforis.collectone.v2.app.domain.model.SyncStatus
import org.openforis.collectone.v2.app.domain.model.UploadStatusFilter
import org.openforis.collectone.v2.app.ui.session.ActiveSurveyAccess
import org.openforis.collectone.v2.app.ui.session.surveyRecordsUiStatesOf
import org.openforis.collectone.v2.mobile.ui.navigation.MainSurveyUiState
import org.openforis.collectone.v2.mobile.ui.navigation.MobileRoute
import org.openforis.collectone.v2.mobile.ui.state.MobileWorkbenchHooks
import org.openforis.collectone.v2.mobile.ui.uploads.UploadStatusCounts

/** The upload status menu: retrying failed uploads and the time of the last sync. */
class MobileUploadsTest {
  /** Records what the app asked of the workbench. */
  private class RecordingWorkbenchHooks : MobileWorkbenchHooks {
    val calls = mutableListOf<String>()

    override fun onSurveyActivated(surveyId: String) {
      calls += "activated $surveyId"
    }

    override fun cycleEntitySyncStatus(entityId: String) {
      calls += "cycle entity $entityId"
    }

    override fun cycleSubmissionSyncStatus(submissionId: String) {
      calls += "cycle submission $submissionId"
    }

    override fun retryFailedUploads() {
      calls += "retry"
    }
  }

  private val dataHolder = sampleDataHolder()
  private val state = MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = dataHolder)

  @Test
  fun retryFailedUploads_outsideTheWorkbench_uploadsThePendingChanges() {
    assertTrue(state.viewModels.dashboard.uiState.value.outboxMutationCount > 0)

    state.retryFailedUploads()

    assertEquals(0, state.viewModels.dashboard.uiState.value.outboxMutationCount)
  }

  @Test
  fun retryFailedUploads_inTheWorkbench_letsTheWorkbenchSimulateTheRetry() {
    val hooks = RecordingWorkbenchHooks()
    state.workbenchHooks = hooks

    state.retryFailedUploads()

    assertEquals(listOf("retry"), hooks.calls)
  }

  @Test
  fun retryFailedUploads_inTheWorkbench_leavesTheOutboxAlone() {
    state.workbenchHooks = RecordingWorkbenchHooks()
    val outboxCount = state.viewModels.dashboard.uiState.value.outboxMutationCount

    state.retryFailedUploads()

    assertEquals(outboxCount, state.viewModels.dashboard.uiState.value.outboxMutationCount)
  }

  @Test
  fun cycleEntitySyncStatus_inTheWorkbench_isForwardedToTheWorkbench() {
    val hooks = RecordingWorkbenchHooks()
    state.workbenchHooks = hooks

    state.entityDetailsActions.cycleEntitySyncStatus("entity-1")

    assertEquals(listOf("cycle entity entity-1"), hooks.calls)
  }

  @Test
  fun lastSyncedAt_withoutTheSyncEngine_isUnknown() {
    assertNull(state.dataHolder.lastSyncedAt.value)
  }

  @Test
  fun searchBarUploadStatusCounts_matchDrawerBadgeAndUploadsQueueAcrossTransitionsAndSurveys() {
    // Initial active survey (Kenya Coffee): 1 pending + 2 in progress = 3 uploading, 5 synced, 1
    // failed.
    val initial = state.currentMainSurveyUiState()
    val initialDashboard = state.viewModels.dashboard.uiState.value
    assertEquals(
      UploadStatusCounts(uploading = 3, synced = 5, failed = 1),
      initial.uploadStatusCounts,
    )
    assertEquals(
      initial.uploadStatusCounts.uploading + initial.uploadStatusCounts.failed,
      initial.drawer.pendingUploadCount,
    )
    assertEquals(initialDashboard.outboxMutationCount, initial.drawer.pendingUploadCount)
    assertEquals(
      initialDashboard.uploadCountForFilter(UploadStatusFilter.PENDING) +
        initialDashboard.uploadCountForFilter(UploadStatusFilter.IN_PROGRESS),
      initial.uploadStatusCounts.uploading,
    )
    assertEquals(
      initialDashboard.uploadCountForFilter(UploadStatusFilter.UPLOADED),
      initial.uploadStatusCounts.synced,
    )
    assertEquals(
      initialDashboard.uploadCountForFilter(UploadStatusFilter.FAILED),
      initial.uploadStatusCounts.failed,
    )

    // Syncing the single failed mutation clears the failed count while keeping 3 uploading.
    state.viewModels.dashboard.syncMutationNow("mut-outbox-01")
    val afterRetryOne = state.currentMainSurveyUiState()
    assertEquals(
      UploadStatusCounts(uploading = 3, synced = 6, failed = 0),
      afterRetryOne.uploadStatusCounts,
    )
    assertEquals(3, afterRetryOne.drawer.pendingUploadCount)

    // Syncing all remaining outbox mutations transitions everything to synced (0 uploading, 0
    // failed).
    state.viewModels.dashboard.syncAllOutboxMutations()
    val afterSyncAll = state.currentMainSurveyUiState()
    assertEquals(
      UploadStatusCounts(uploading = 0, synced = 9, failed = 0),
      afterSyncAll.uploadStatusCounts,
    )
    assertEquals(0, afterSyncAll.drawer.pendingUploadCount)

    // Switching to another survey with no upload queue entries resets badge, popover, and list to
    // 0.
    val otherSurvey =
      state.viewModels.dashboard.uiState.value.surveys.first {
        it.id != initialDashboard.activeSurveyId && it.isDownloaded
      }
    state.viewModels.onboarding.openSurvey(otherSurvey.id)
    val onOtherSurvey = state.currentMainSurveyUiState()
    assertEquals(UploadStatusCounts(), onOtherSurvey.uploadStatusCounts)
    assertEquals(0, onOtherSurvey.drawer.pendingUploadCount)
    assertTrue(state.viewModels.dashboard.uiState.value.filteredUploadMutations.isEmpty())
  }

  @Test
  fun staleEntityOrSubmissionSyncStatuses_doNotShowUploadingBadgeWhenUploadQueueIsEmpty() {
    dataHolder.launch {
      dataHolder.mutationRepository.setMutations(emptyList())
      val staleEntities =
        state.viewModels.dataCollection.uiState.value.entities.map {
          it.copy(
            syncStatus = SyncStatus.UPLOADING,
            submissions = it.submissions.map { sub -> sub.copy(syncStatus = SyncStatus.UPLOADING) },
          )
        }
      dataHolder.surveyRepository.setEntities(staleEntities)
    }

    val ui = state.currentMainSurveyUiState()
    assertEquals(UploadStatusCounts(), ui.uploadStatusCounts)
    assertEquals(0, ui.drawer.pendingUploadCount)
    assertTrue(state.viewModels.dashboard.uiState.value.filteredUploadMutations.isEmpty())
  }

  @Test
  fun withSyncEngineConnected_seededSurveysHaveZeroPendingUploadsAndAllRecordsSynced() {
    val service = SeedUserDataService()
    val syncedHolder =
      AppDataHolder(
        userDataServiceUrl = "https://collect-one.example.test",
        grpcClient = service.client(),
        syncDownloadedSurveysOnly = true,
        enableSyncEngine = true,
      )
    val syncedState =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = syncedHolder)
    try {
      val ui = syncedState.currentMainSurveyUiState()
      assertEquals(0, ui.uploadStatusCounts.uploading)
      assertEquals(0, ui.uploadStatusCounts.failed)
      assertEquals(0, ui.drawer.pendingUploadCount)
      assertEquals(0, syncedState.viewModels.dashboard.uiState.value.outboxMutationCount)
      assertEquals(
        syncedState.viewModels.dashboard.uiState.value.uploadedMutationCount,
        ui.uploadStatusCounts.synced,
      )
      assertTrue(
        syncedState.viewModels.dataCollection.uiState.value.entities.all {
          it.syncStatus == SyncStatus.SYNCED
        }
      )
      assertTrue(
        syncedState.viewModels.dataCollection.uiState.value.allSubmissions.all {
          it.syncStatus == SyncStatus.SYNCED
        }
      )
    } finally {
      syncedState.close()
    }
  }

  private fun MobileAppState.currentMainSurveyUiState(): MainSurveyUiState {
    val appData = dataHolder.appData.value
    val records =
      surveyRecordsUiStatesOf(
        viewModels = viewModels,
        data = appData,
        notice = notices.notice.value,
        isAlwaysOnline = false,
      )
    val onboarding = viewModels.onboarding.uiState.value
    return mainSurveyUiState(
      records = records,
      settings = viewModels.settings.uiState.value,
      onboarding = onboarding,
      access = ActiveSurveyAccess.of(appData, onboarding),
      lastSyncedAt = dataHolder.lastSyncedAt.value,
      badges = viewModels.surveyMap,
    )
  }
}
