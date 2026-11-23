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
package org.openforis.collectone.v2.app.domain.model

import org.openforis.collectone.v2.app.domain.error.DomainError

/**
 * Filter chips displayed on the unified `Uploads` screen (`Pending`, `In progress`, `Uploaded`, and
 * `Failed`).
 */
enum class UploadStatusFilter {
  PENDING,
  IN_PROGRESS,
  UPLOADED,
  FAILED,
}

/**
 * Whether the user's local edits are being saved to the user data service, for save status
 * indicators such as the web console header's "Saving…".
 */
enum class SaveStatus {
  /** Nothing is being sent and nothing is waiting to retry after a failed send. */
  IDLE,

  /**
   * Edits are being sent. Background polls and pulls that send nothing are not saving, even while
   * edits wait their turn (debounced, offline, or queued behind a send that is backing off).
   */
  SAVING,

  /** Edits failed to send and are waiting to be retried. */
  FAILED,
}

/**
 * Synchronization lifecycle state of a client mutation (`EntityMutation` or `SubmissionMutation`)
 * displayed in the unified `Uploads` navigation drawer view.
 */
enum class MutationSyncState(
  val isOutbox: Boolean,
  val statusFilter: UploadStatusFilter,
) {
  UPLOADING(isOutbox = true, statusFilter = UploadStatusFilter.IN_PROGRESS),
  QUEUED(isOutbox = true, statusFilter = UploadStatusFilter.PENDING),
  RETRYING(isOutbox = true, statusFilter = UploadStatusFilter.IN_PROGRESS),
  FAILED(isOutbox = true, statusFilter = UploadStatusFilter.FAILED),
  UPLOADED(isOutbox = false, statusFilter = UploadStatusFilter.UPLOADED),
}

/**
 * User-friendly category of mutation operation (`EntityMutation` vs `SubmissionMutation`) in the
 * unified `Uploads` view (e.g., `Form submitted`, `Form modified`, `Form deleted`).
 */
enum class MutationOperationKind(
  val protoOperationLabel: String,
  val isEntityOperation: Boolean = false,
) {
  CREATE_SUBMISSION(protoOperationLabel = "SubmissionMutation.update"),
  UPDATE_SUBMISSION(protoOperationLabel = "SubmissionMutation.update"),
  DELETE_SUBMISSION(protoOperationLabel = "SubmissionMutation.delete"),
  CREATE_ENTITY(protoOperationLabel = "EntityMutation.update", isEntityOperation = true),
  UPDATE_ENTITY(protoOperationLabel = "EntityMutation.update", isEntityOperation = true),
  DELETE_ENTITY(protoOperationLabel = "EntityMutation.delete", isEntityOperation = true),
  UPLOAD_MEDIA(protoOperationLabel = "SubmissionMutation.update (media)"),
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
  /**
   * Raw, untranslated detail of [state] (such as the server's message for a failed upload), kept
   * for diagnostics. It is never shown to users: the UI describes a failure by [lastError].
   */
  val stateDetail: String,
  val operationTimestamp: String,
  val startedTimestamp: String? = null,
  val completedTimestamp: String? = null,
  val payloadSummary: String = "",
  /**
   * Why the last upload attempt failed, while the change is [MutationSyncState.FAILED] or
   * [MutationSyncState.RETRYING]; `null` otherwise. Comes from the outbox, so it isn't stored with
   * the log entry.
   */
  val lastError: DomainError? = null,
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
