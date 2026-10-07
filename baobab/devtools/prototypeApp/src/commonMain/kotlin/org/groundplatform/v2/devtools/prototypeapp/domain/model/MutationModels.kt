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
package org.groundplatform.v2.devtools.prototypeapp.domain.model

/**
 * Filter chips displayed on the unified `Uploads` screen (`Pending`, `In progress`, `Uploaded`, and
 * `Failed`).
 */
enum class UploadStatusFilter(val label: String) {
  PENDING("Pending"),
  IN_PROGRESS("In progress"),
  UPLOADED("Uploaded"),
  FAILED("Failed"),
}

/**
 * Synchronization lifecycle state of a client mutation (`EntityMutation` or `SubmissionMutation`)
 * displayed in the unified `Uploads` navigation drawer view.
 */
enum class MutationSyncState(
  val label: String,
  val isOutbox: Boolean,
  val statusFilter: UploadStatusFilter,
) {
  UPLOADING(label = "In progress", isOutbox = true, statusFilter = UploadStatusFilter.IN_PROGRESS),
  QUEUED(label = "Pending", isOutbox = true, statusFilter = UploadStatusFilter.PENDING),
  RETRYING(label = "In progress", isOutbox = true, statusFilter = UploadStatusFilter.IN_PROGRESS),
  FAILED(label = "Failed", isOutbox = true, statusFilter = UploadStatusFilter.FAILED),
  UPLOADED(label = "Uploaded", isOutbox = false, statusFilter = UploadStatusFilter.UPLOADED),
}

/**
 * User-friendly category of mutation operation (`EntityMutation` vs `SubmissionMutation`) in the
 * unified `Uploads` view (e.g., `Form submitted`, `Form modified`, `Form deleted`).
 */
enum class MutationOperationKind(val label: String, val protoOperationLabel: String) {
  CREATE_SUBMISSION(label = "Form submitted", protoOperationLabel = "SubmissionMutation.update"),
  UPDATE_SUBMISSION(label = "Form modified", protoOperationLabel = "SubmissionMutation.update"),
  DELETE_SUBMISSION(label = "Form deleted", protoOperationLabel = "SubmissionMutation.delete"),
  CREATE_ENTITY(label = "Map feature created", protoOperationLabel = "EntityMutation.update"),
  UPDATE_ENTITY(label = "Map feature modified", protoOperationLabel = "EntityMutation.update"),
  DELETE_ENTITY(label = "Map feature deleted", protoOperationLabel = "EntityMutation.delete"),
  UPLOAD_MEDIA(label = "Photo attached", protoOperationLabel = "SubmissionMutation.update (media)"),
}

/**
 * Represents a local data mutation (`DataMutation` in `MutateDataRequest`) tracked in the unified
 * `Uploads` view.
 */
data class MutationLogItem(
  val id: String,
  val surveyId: String,
  val operationKind: MutationOperationKind,
  val title: String,
  val targetLabel: String,
  val entityId: String,
  val submissionId: String? = null,
  val actorName: String,
  val state: MutationSyncState,
  val stateDetail: String,
  val operationTimestamp: String,
  val startedTimestamp: String? = null,
  val completedTimestamp: String? = null,
  val payloadSummary: String = "",
) {
  /** Filter chip category (`Pending`, `In progress`, `Uploaded`, or `Failed`) for this item. */
  val uploadStatusFilter: UploadStatusFilter
    get() = state.statusFilter

  /** True when this mutation is not yet uploaded (`state.isOutbox == true`). */
  val isOutbox: Boolean
    get() = state.isOutbox

  /** True when this mutation has completed uploading (`state == MutationSyncState.UPLOADED`). */
  val isUploaded: Boolean
    get() = state == MutationSyncState.UPLOADED

  /** Concise `YYYY-MM-DD HH:MM` timestamp for compact list display. */
  val compactTimestamp: String
    get() =
      operationTimestamp.removeSuffix(" UTC").let { trimmed ->
        if (trimmed.length >= 16) trimmed.substring(0, 16) else trimmed
      }

  /** Formatted "time started or completed" summary string for display in mutation summaries. */
  val startedOrCompletedSummary: String
    get() =
      if (isUploaded) {
        val completed = completedTimestamp ?: operationTimestamp
        if (!startedTimestamp.isNullOrBlank()) {
          "Started: $startedTimestamp • Completed: $completed"
        } else {
          "Completed: $completed"
        }
      } else {
        if (!startedTimestamp.isNullOrBlank()) {
          "Started: $startedTimestamp"
        } else {
          "Started: Pending network connection"
        }
      }
}

/**
 * All mutations in strict reverse chronological order ([MutationLogItem.operationTimestamp]
 * descending, then the completed or started time, then ID), as listed on the `Uploads` screen.
 */
fun List<MutationLogItem>.newestFirst(): List<MutationLogItem> =
  sortedWith(
    compareByDescending<MutationLogItem> { it.operationTimestamp }
      .thenByDescending { it.completedTimestamp ?: it.startedTimestamp ?: "" }
      .thenByDescending { it.id }
  )

/**
 * Pending, in-progress, or failed mutations ([MutationLogItem.isOutbox]) in strict reverse
 * chronological order ([MutationLogItem.operationTimestamp] descending).
 */
fun List<MutationLogItem>.outboxNewestFirst(): List<MutationLogItem> = filter {
  it.isOutbox
}
  .sortedWith(
    compareByDescending<MutationLogItem> { it.operationTimestamp }
      .thenByDescending { it.startedTimestamp ?: "" }
      .thenByDescending { it.id }
  )

/**
 * Completed mutations ([MutationLogItem.isUploaded]) in strict reverse chronological order (
 * [MutationLogItem.operationTimestamp] descending).
 */
fun List<MutationLogItem>.uploadedNewestFirst(): List<MutationLogItem> = filter {
  it.isUploaded
}
  .sortedWith(
    compareByDescending<MutationLogItem> { it.operationTimestamp }
      .thenByDescending { it.completedTimestamp ?: "" }
      .thenByDescending { it.id }
  )

/**
 * Whether this mutation passes the `Uploads` screen filters: its status chip is [statusFilter] (or
 * [statusFilter] is `null`) and, when [entityId] is set, it targets that map feature.
 */
fun MutationLogItem.matchesUploadsFilters(
  statusFilter: UploadStatusFilter?,
  entityId: String?,
): Boolean =
  (statusFilter == null || uploadStatusFilter == statusFilter) &&
    (entityId == null || this.entityId == entityId)
