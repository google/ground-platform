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
package org.openforis.collectone.v2.workbench.ui.viewmodel

import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.SubmissionPreviewItem
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.SyncStatus
import org.openforis.collectone.v2.app.domain.model.deriveEntitySyncStatus

// The workbench's upload and download simulators: how a simulated sync status or download changes
// the records and surveys in the local store. Pure functions, so `WorkbenchViewModel` only writes
// their results to the store and words the notices.

/**
 * These entities with entity [entityId] set to [status], or null when there is no such entity.
 * Marking an entity synced marks all its submissions synced too (an entity uploads with its
 * submissions); other statuses leave the submissions as they are.
 */
internal fun List<GeospatialEntityItem>.withEntitySyncStatus(
  entityId: String,
  status: SyncStatus,
): List<GeospatialEntityItem>? {
  if (none { it.id == entityId }) return null
  return map { item ->
    if (item.id != entityId) return@map item
    val submissions =
      if (status == SyncStatus.SYNCED) {
        item.submissions.map { it.copy(syncStatus = SyncStatus.SYNCED) }
      } else {
        item.submissions
      }
    item.copy(submissions = submissions, syncStatus = status)
  }
}

/** The records after [withSubmissionSyncStatus] set one submission's sync status. */
internal data class SubmissionSyncStatusChange(
  /** Every entity, the one holding the submission with its status derived again. */
  val entities: List<GeospatialEntityItem>,
  /**
   * The standalone submissions, or null when the submission isn't one of them (nothing to save).
   */
  val standaloneSubmissions: List<SubmissionPreviewItem>?,
  /** The changed submission's form title for the notice, or null when no submission matched. */
  val formTitle: String?,
)

/**
 * Sets submission [submissionId] to [status], wherever it is: under an entity in [entities] (whose
 * own status is then derived from its submissions by [deriveEntitySyncStatus], falling back to
 * synced when the submission was marked synced, else to the entity's previous status), or in
 * [standaloneSubmissions] (field logs with no entity).
 */
internal fun withSubmissionSyncStatus(
  entities: List<GeospatialEntityItem>,
  standaloneSubmissions: List<SubmissionPreviewItem>,
  submissionId: String,
  status: SyncStatus,
): SubmissionSyncStatusChange {
  var formTitle: String? = null
  val updatedEntities = entities.map { item ->
    if (item.submissions.none { it.id == submissionId }) return@map item
    val submissions =
      item.submissions.map { sub ->
        if (sub.id == submissionId) {
          formTitle = sub.formTitle
          sub.copy(syncStatus = status)
        } else {
          sub
        }
      }
    val fallback = if (status == SyncStatus.SYNCED) SyncStatus.SYNCED else item.syncStatus
    item.copy(
      submissions = submissions,
      syncStatus = deriveEntitySyncStatus(submissions, fallback = fallback),
    )
  }
  val updatedStandalone =
    if (standaloneSubmissions.any { it.id == submissionId }) {
      standaloneSubmissions.map { sub ->
        if (sub.id == submissionId) {
          formTitle = sub.formTitle
          sub.copy(syncStatus = status)
        } else {
          sub
        }
      }
    } else {
      null
    }
  return SubmissionSyncStatusChange(updatedEntities, updatedStandalone, formTitle)
}

/**
 * These surveys with survey [surveyId]'s downloaded flag flipped, and the survey as it is now (null
 * when there is no such survey), for the notice.
 */
internal fun List<SurveyPreviewItem>.withDownloadToggled(
  surveyId: String
): Pair<List<SurveyPreviewItem>, SurveyPreviewItem?> {
  var toggled: SurveyPreviewItem? = null
  val updated = map { item ->
    if (item.id == surveyId) item.copy(isDownloaded = !item.isDownloaded).also { toggled = it }
    else item
  }
  return updated to toggled
}

/**
 * These surveys with the active survey's ([activeSurveyId]) title and description replaced; a blank
 * value keeps the current one, so the survey never loses its name.
 */
internal fun List<SurveyPreviewItem>.withDetails(
  activeSurveyId: String,
  title: String,
  description: String,
): List<SurveyPreviewItem> = map { item ->
  if (item.id == activeSurveyId) {
    item.copy(
      title = title.ifBlank { item.title },
      description = description.ifBlank { item.description },
    )
  } else {
    item
  }
}
