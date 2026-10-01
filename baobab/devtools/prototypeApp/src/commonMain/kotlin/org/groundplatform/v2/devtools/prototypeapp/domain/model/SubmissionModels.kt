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
 * Represents a polygon geometry recorded as an answer to a geometry question/field
 * (`FormGeometrySource { form_id, field_path }`) inside a form submission (`SubmissionRecord`),
 * rendered on the map with a dotted polygon outline.
 */
data class SubmissionGeometryPolygon(
  val id: String,
  val submissionId: String,
  val entityId: String,
  val layerId: String,
  val formId: String,
  val formTitle: String,
  val fieldPath: String,
  val questionLabel: String,
  val shortMapBadge: String,
  val collectorName: String,
  val timestamp: String,
  val areaHectares: Double,
  val vertexCount: Int,
  val normalizedX: Float,
  val normalizedY: Float,
  val widthFraction: Float,
  val heightFraction: Float,
  val colorHex: Long,
)

/** Question-and-answer pair inside a recorded `SubmissionRecord`. */
data class SubmissionFieldEntry(
  val questionName: String,
  val questionLabel: String,
  val answerValue: String,
)

/**
 * Synchronization state of a Geospatial Entity (`EntityRecord`) or Form Submission
 * (`SubmissionRecord`) between local offline storage and the Ground cloud server.
 */
enum class SyncStatus(val label: String, val description: String) {
  UPLOADING(label = "Uploading", description = "Uploading local changes to server"),
  SYNCED(label = "Synced", description = "Synchronized with cloud server"),
  FAILED(label = "Failed", description = "Upload failed — tap to retry");

  /**
   * Returns the next [SyncStatus] in the cycle (`UPLOADING` -> `SYNCED` -> `FAILED` ->
   * `UPLOADING`).
   */
  fun next(): SyncStatus =
    when (this) {
      UPLOADING -> SYNCED
      SYNCED -> FAILED
      FAILED -> UPLOADING
    }
}

/**
 * Derives the aggregate [SyncStatus] of a [GeospatialEntityItem] from its recorded [submissions],
 * prioritizing [SyncStatus.FAILED], then [SyncStatus.UPLOADING], and falling back to [fallback].
 */
fun deriveEntitySyncStatus(
  submissions: List<SubmissionPreviewItem>,
  fallback: SyncStatus = SyncStatus.SYNCED,
): SyncStatus =
  when {
    submissions.any { it.syncStatus == SyncStatus.FAILED } -> SyncStatus.FAILED
    submissions.any { it.syncStatus == SyncStatus.UPLOADING } -> SyncStatus.UPLOADING
    else -> fallback
  }

/**
 * Represents a completed form submission (`SubmissionRecord`), either linked to a Geospatial Entity
 * (`entityId.isNotBlank()`) or recorded as a standalone submission without an attached entity
 * (`entityId.isEmpty()`).
 */
data class SubmissionPreviewItem(
  val id: String,
  val entityId: String = "",
  val entityLabel: String = "",
  val formId: String,
  val formTitle: String,
  val formVersion: String,
  val collectorName: String,
  val collectorEmail: String,
  val timestamp: String,
  val fields: List<SubmissionFieldEntry>,
  val targetTypeLabel: String = "Location",
  val syncStatus: SyncStatus = SyncStatus.SYNCED,
  val coordinatesLabel: String = "",
  val normalizedX: Float? = null,
  val normalizedY: Float? = null,
) {
  /** True when this submission is linked to a persistent Geospatial Entity (`entityId != ""`). */
  val hasAttachedEntity: Boolean
    get() = entityId.isNotBlank()

  val isUploading: Boolean
    get() = syncStatus == SyncStatus.UPLOADING

  val isSynced: Boolean
    get() = syncStatus == SyncStatus.SYNCED

  val isFailed: Boolean
    get() = syncStatus == SyncStatus.FAILED
}

/**
 * Active PDF export & app-share sheet state for either a Geospatial Entity or a Form Submission.
 */
data class SharedPdfSheetState(
  val targetId: String,
  val title: String,
  val subtitle: String,
  val pdfFileName: String,
  val targetKindLabel: String,
  /** Number of pages in the generated PDF. */
  val pageCount: Int = 0,
  /** Generated PDF size for display, e.g. `"14 KB"`. */
  val fileSizeLabel: String = "",
)
