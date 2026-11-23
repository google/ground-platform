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
package org.openforis.collectone.v2.mobile.ui.uploads

import kotlin.test.Test
import kotlin.test.assertEquals
import org.openforis.collectone.v2.app.domain.model.MutationLogItem
import org.openforis.collectone.v2.app.domain.model.MutationOperationKind
import org.openforis.collectone.v2.app.domain.model.MutationSyncState
import org.openforis.collectone.v2.app.domain.model.SyncStatus

class UploadStatusCountsTest {

  @Test
  fun noRecords_countsNothing() {
    assertEquals(UploadStatusCounts(), UploadStatusCounts.of(emptyList()))
    assertEquals(UploadStatusCounts(), UploadStatusCounts.fromMutations(emptyList()))
  }

  @Test
  fun mixedRecords_areCountedByUploadState() {
    val counts =
      UploadStatusCounts.of(
        listOf(
          SyncStatus.UPLOADING,
          SyncStatus.SYNCED,
          SyncStatus.FAILED,
          SyncStatus.SYNCED,
          SyncStatus.UPLOADING,
          SyncStatus.SYNCED,
        )
      )

    assertEquals(UploadStatusCounts(uploading = 2, synced = 3, failed = 1), counts)
  }

  @Test
  fun fromMutations_countsQueuedUploadingAndRetryingAsUploading_uploadedAsSynced_andFailedAsFailed() {
    fun mutation(id: String, state: MutationSyncState) =
      MutationLogItem(
        id = id,
        surveyId = "survey-1",
        operationKind = MutationOperationKind.CREATE_SUBMISSION,
        title = "Form",
        targetLabel = "Plot 1",
        entityId = "entity-1",
        actorName = "Collector",
        state = state,
        stateDetail = "",
        operationTimestamp = "2026-10-01 10:00:00 UTC",
      )

    val counts =
      UploadStatusCounts.fromMutations(
        listOf(
          mutation("m1", MutationSyncState.QUEUED),
          mutation("m2", MutationSyncState.UPLOADING),
          mutation("m3", MutationSyncState.RETRYING),
          mutation("m4", MutationSyncState.UPLOADED),
          mutation("m5", MutationSyncState.UPLOADED),
          mutation("m6", MutationSyncState.FAILED),
        )
      )

    assertEquals(UploadStatusCounts(uploading = 3, synced = 2, failed = 1), counts)
  }
}
