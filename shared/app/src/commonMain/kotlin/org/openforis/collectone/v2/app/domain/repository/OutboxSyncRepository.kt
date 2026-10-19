/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.app.domain.repository

/**
 * Domain repository contract for the sync engine's outbox of pending changes: uploading now, and
 * retrying or discarding changes the server rejected.
 */
interface OutboxSyncRepository {
  /** Sends pending changes and pulls remote changes now (no-op while offline). */
  suspend fun syncNow()

  /**
   * Moves rejected changes to a map feature (or, when [submissionId] is set, a submission) back
   * into the upload queue. Returns the number of changes requeued.
   */
  suspend fun retryRecord(surveyId: String, entityId: String, submissionId: String?): Int

  /** Moves every rejected change in [surveyId] back into the upload queue; returns the count. */
  suspend fun retryRejected(surveyId: String): Int

  /** Drops rejected changes to a record, restoring the server's version. Returns the count. */
  suspend fun discardRejected(surveyId: String, entityId: String, submissionId: String?): Int
}
