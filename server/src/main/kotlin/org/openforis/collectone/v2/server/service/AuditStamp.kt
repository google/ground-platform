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
package org.openforis.collectone.v2.server.service

import com.squareup.wire.Instant
import openforis.collectone.v1.data.AuditInfo
import openforis.collectone.v1.survey.SurveyDef

/**
 * Stamps the server-owned `AuditInfo` of the records one write creates or changes: who from the
 * authenticated caller, when from the store's clock ([now]). Clients can't set it: whatever a
 * mutation carries is overwritten.
 */
internal class AuditStamp(
  /** The user the write is attributed to, or empty when unknown (the development fallback). */
  private val userId: String,
  private val now: Instant,
) {
  /** Audit info for a record this write creates on behalf of [createdBy]. */
  fun created(createdBy: String = userId): AuditInfo =
    AuditInfo(
      created_by = createdBy,
      create_time = now,
      last_modified_by = createdBy,
      last_modify_time = now,
    )

  /**
   * Audit info for a record this write changes: [stored]'s creation details, and this write as the
   * last modification. A record stored without audit info keeps its creation details unknown.
   */
  fun modified(stored: AuditInfo?, modifiedBy: String = userId): AuditInfo =
    AuditInfo(
      created_by = stored?.created_by.orEmpty(),
      create_time = stored?.create_time,
      last_modified_by = modifiedBy,
      last_modify_time = now,
    )
}

/**
 * [this] without the fields the server owns (`audit_info`, `saved_revision_number`), so a mutation
 * can't set them and comparisons ignore them.
 */
internal fun SurveyDef.withoutServerOwnedFields(): SurveyDef =
  copy(audit_info = null, saved_revision_number = 0L)
