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

import org.openforis.collectone.v2.app.domain.model.MutationLogItem
import org.openforis.collectone.v2.app.domain.model.MutationSyncState
import org.openforis.collectone.v2.app.domain.model.SyncStatus

/**
 * How many upload queue items of the active survey are [uploading] (pending or in progress),
 * [synced] (uploaded), and [failed], as the search bar upload status button and popover summarize
 * them.
 */
data class UploadStatusCounts(val uploading: Int = 0, val synced: Int = 0, val failed: Int = 0) {
  companion object {
    /** Counts the upload queue items in [mutations] by their live upload state. */
    fun fromMutations(mutations: Iterable<MutationLogItem>): UploadStatusCounts {
      var uploading = 0
      var synced = 0
      var failed = 0
      for (item in mutations) {
        when (item.state) {
          MutationSyncState.UPLOADING,
          MutationSyncState.QUEUED,
          MutationSyncState.RETRYING -> uploading++
          MutationSyncState.UPLOADED -> synced++
          MutationSyncState.FAILED -> failed++
        }
      }
      return UploadStatusCounts(uploading = uploading, synced = synced, failed = failed)
    }

    /** Counts the records whose upload states are [syncStatuses]. */
    fun of(syncStatuses: Iterable<SyncStatus>): UploadStatusCounts {
      val counts = syncStatuses.groupingBy { it }.eachCount()
      return UploadStatusCounts(
        uploading = counts[SyncStatus.UPLOADING] ?: 0,
        synced = counts[SyncStatus.SYNCED] ?: 0,
        failed = counts[SyncStatus.FAILED] ?: 0,
      )
    }
  }
}
