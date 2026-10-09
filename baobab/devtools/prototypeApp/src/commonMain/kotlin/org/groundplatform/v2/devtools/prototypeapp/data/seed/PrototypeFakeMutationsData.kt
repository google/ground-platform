/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.devtools.prototypeapp.data.seed

import org.groundplatform.v2.devtools.prototypeapp.domain.model.MutationLogItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MutationOperationKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MutationSyncState

/** Hardcoded sample mutation log records for the prototype app. */
internal object PrototypeFakeMutationsData {
  /**
   * Sample local mutations (`DataMutation` items) spanning `Pending`, `In progress`, `Uploaded`,
   * and `Failed`, ordered in reverse chronological order with user-friendly operation descriptions.
   */
  fun defaultMutations(): List<MutationLogItem> =
    listOf(
      // --- Pending, In progress, and Failed Mutations (reverse chronological order) ---
      MutationLogItem(
        id = "mut-outbox-04",
        surveyId = "survey-kenya-coffee",
        operationKind = MutationOperationKind.CREATE_SUBMISSION,
        title = "Seasonal Shade Tree & Canopy Audit",
        targetLabel = "Transect SHD-201 • Chinga North Agroforestry",
        entityId = "entity-shade-201",
        submissionId = "sub-shade-201-wave3",
        actorName = "Maya Lin",
        state = MutationSyncState.UPLOADING,
        stateDetail = "Uploading (74%)",
        operationTimestamp = "2026-09-19 09:40:18 UTC",
        startedTimestamp = "2026-09-19 09:40:22 UTC",
        completedTimestamp = null,
        payloadSummary = "5 responses",
      ),
      MutationLogItem(
        id = "mut-outbox-03",
        surveyId = "survey-kenya-coffee",
        operationKind = MutationOperationKind.UPDATE_SUBMISSION,
        title = "Smallholder Household Socio-Economic Survey",
        targetLabel = "Plot NYR-108 • Njeri Cooperative Block B",
        entityId = "entity-nyr-108",
        submissionId = null,
        actorName = "Maya Lin",
        state = MutationSyncState.RETRYING,
        stateDetail = "Retrying over weak signal",
        operationTimestamp = "2026-09-19 09:34:05 UTC",
        startedTimestamp = "2026-09-19 09:34:12 UTC",
        completedTimestamp = null,
        payloadSummary = "Updated household interview",
      ),
      MutationLogItem(
        id = "mut-outbox-02",
        surveyId = "survey-kenya-coffee",
        operationKind = MutationOperationKind.UPDATE_ENTITY,
        title = "Marked parcel as In progress",
        targetLabel = "Plot NYR-108 • Njeri Cooperative Block B",
        entityId = "entity-nyr-108",
        submissionId = null,
        actorName = "Maya Lin",
        state = MutationSyncState.QUEUED,
        stateDetail = "Waiting for network connection",
        operationTimestamp = "2026-09-19 09:21:40 UTC",
        startedTimestamp = "2026-09-19 09:21:44 UTC",
        completedTimestamp = null,
        payloadSummary = "Coffee Parcel status updated",
      ),
      MutationLogItem(
        id = "mut-outbox-01",
        surveyId = "survey-kenya-coffee",
        operationKind = MutationOperationKind.DELETE_SUBMISSION,
        title = "Duplicate Parcel Baseline Draft",
        targetLabel = "Plot NYR-112 • Kariuki Hillside Parcel",
        entityId = "entity-nyr-112",
        submissionId = null,
        actorName = "Maya Lin",
        state = MutationSyncState.FAILED,
        stateDetail = "Connection timed out — tap Retry",
        operationTimestamp = "2026-09-19 08:55:10 UTC",
        startedTimestamp = "2026-09-19 08:55:15 UTC",
        completedTimestamp = null,
        payloadSummary = "Deleted draft submission",
      ),
      // --- Uploaded Mutations (reverse chronological order) ---
      MutationLogItem(
        id = "mut-uploaded-05",
        surveyId = "survey-kenya-coffee",
        operationKind = MutationOperationKind.CREATE_SUBMISSION,
        title = "Washing Station Effluent & Water Check",
        targetLabel = "Station WSH-01 • Gura River Wet Mill",
        entityId = "entity-station-01",
        submissionId = "sub-wsh-01-sep",
        actorName = "Maya Lin",
        state = MutationSyncState.UPLOADED,
        stateDetail = "Uploaded",
        operationTimestamp = "2026-09-18 17:30:04 UTC",
        startedTimestamp = "2026-09-18 17:30:11 UTC",
        completedTimestamp = "2026-09-18 17:30:15 UTC",
        payloadSummary = "3 responses",
      ),
      MutationLogItem(
        id = "mut-uploaded-04",
        surveyId = "survey-kenya-coffee",
        operationKind = MutationOperationKind.UPDATE_ENTITY,
        title = "Marked parcel as Completed",
        targetLabel = "Plot NYR-104 • Kamau Family Parcel",
        entityId = "entity-nyr-104",
        submissionId = "sub-nyr-104-baseline",
        actorName = "Maya Lin",
        state = MutationSyncState.UPLOADED,
        stateDetail = "Uploaded",
        operationTimestamp = "2026-09-18 10:14:20 UTC",
        startedTimestamp = "2026-09-18 10:14:25 UTC",
        completedTimestamp = "2026-09-18 10:14:27 UTC",
        payloadSummary = "Coffee Parcel status updated",
      ),
      MutationLogItem(
        id = "mut-uploaded-03",
        surveyId = "survey-kenya-coffee",
        operationKind = MutationOperationKind.CREATE_SUBMISSION,
        title = "EUDR Parcel Baseline Registration",
        targetLabel = "Plot NYR-104 • Kamau Family Parcel",
        entityId = "entity-nyr-104",
        submissionId = "sub-nyr-104-baseline",
        actorName = "Maya Lin",
        state = MutationSyncState.UPLOADED,
        stateDetail = "Uploaded",
        operationTimestamp = "2026-09-18 10:14:02 UTC",
        startedTimestamp = "2026-09-18 10:14:15 UTC",
        completedTimestamp = "2026-09-18 10:14:24 UTC",
        payloadSummary = "4 responses",
      ),
      MutationLogItem(
        id = "mut-uploaded-02",
        surveyId = "survey-kenya-coffee",
        operationKind = MutationOperationKind.UPDATE_SUBMISSION,
        title = "Smallholder Household Socio-Economic Survey",
        targetLabel = "Plot NYR-108 • Njeri Cooperative Block B",
        entityId = "entity-nyr-108",
        submissionId = null,
        actorName = "Maya Lin",
        state = MutationSyncState.UPLOADED,
        stateDetail = "Uploaded",
        operationTimestamp = "2026-09-17 16:02:11 UTC",
        startedTimestamp = "2026-09-17 16:05:00 UTC",
        completedTimestamp = "2026-09-17 16:05:08 UTC",
        payloadSummary = "4 responses",
      ),
      MutationLogItem(
        id = "mut-uploaded-01",
        surveyId = "survey-kenya-coffee",
        operationKind = MutationOperationKind.CREATE_SUBMISSION,
        title = "Seasonal Shade Tree & Canopy Audit",
        targetLabel = "Transect SHD-201 • Chinga North Agroforestry",
        entityId = "entity-shade-201",
        submissionId = "sub-shade-201-wave3",
        actorName = "Maya Lin",
        state = MutationSyncState.UPLOADED,
        stateDetail = "Uploaded",
        operationTimestamp = "2026-06-14 14:20:09 UTC",
        startedTimestamp = "2026-06-14 14:22:30 UTC",
        completedTimestamp = "2026-06-14 14:22:36 UTC",
        payloadSummary = "3 responses",
      ),
    )
}
