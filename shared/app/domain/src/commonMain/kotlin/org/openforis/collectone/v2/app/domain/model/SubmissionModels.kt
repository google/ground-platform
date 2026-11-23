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

import kotlin.time.Instant
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.LatLng

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
  val areaHectares: Double,
  val vertexCount: Int,
  val colorHex: Long,
  /** The recorded geometry in WGS 84 (`SubmissionGeometryPolygonDef.geometry`). */
  val geometry: Geometry,
)

/** Question-and-answer pair inside a recorded `SubmissionRecord`. */
data class SubmissionFieldEntry(
  val questionName: String,
  val questionLabel: String,
  val answerValue: String,
)

/**
 * Synchronization state of a Geospatial Entity (`EntityRecord`) or Form Submission
 * (`SubmissionRecord`) between local offline storage and the Collect One cloud server.
 */
enum class SyncStatus {
  UPLOADING,
  SYNCED,
  FAILED;

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
  /**
   * When the collector last saved this submission, in their device's clock
   * (`SubmissionRecord.client_updated_at`), or `null` for submissions saved before it was recorded.
   */
  val clientUpdatedAt: Instant? = null,
  /**
   * When the server accepted the last change (`SubmissionRecord.server_updated_at`), or `null`
   * while the submission hasn't been uploaded.
   */
  val serverUpdatedAt: Instant? = null,
  val fields: List<SubmissionFieldEntry>,
  /**
   * The survey author's singular noun for the type of the attached feature
   * (`SubmissionRecord.target_type_label`, e.g. `"Coffee Parcel"`), or blank for a submission
   * without one or when the author gave none; the UI names those in the user's language.
   */
  val targetTypeNoun: String = "",
  val syncStatus: SyncStatus = SyncStatus.SYNCED,
  /**
   * Where the collector was when recording (`SubmissionRecord.location`), or `null` when unknown.
   * Shown as coordinates for submissions without a map feature.
   */
  val location: LatLng? = null,
) {
  /** True when this submission is linked to a persistent Geospatial Entity (`entityId != ""`). */
  val hasAttachedEntity: Boolean
    get() = entityId.isNotBlank()

  /**
   * When this submission was last updated, for display and sorting: the collector's save time
   * ([clientUpdatedAt]) because that is when the work happened, even if the upload came later, else
   * the server's ([serverUpdatedAt]), or `null` when neither is known.
   */
  val updatedAt: Instant?
    get() = clientUpdatedAt ?: serverUpdatedAt

  val isUploading: Boolean
    get() = syncStatus == SyncStatus.UPLOADING

  val isSynced: Boolean
    get() = syncStatus == SyncStatus.SYNCED

  val isFailed: Boolean
    get() = syncStatus == SyncStatus.FAILED
}
