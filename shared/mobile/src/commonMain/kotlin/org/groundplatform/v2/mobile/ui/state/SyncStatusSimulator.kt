/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.mobile.ui.state

/**
 * Workbench controls that simulate upload progress from the sync status badges of the record views
 * (map feature details and submission cards).
 */
interface SyncStatusSimulator {
  /** Cycles the sync status of map feature [entityId] (`Uploading` → `Synced` → `Failed`). */
  fun cycleEntitySyncStatus(entityId: String)

  /** Cycles the sync status of submission [submissionId] (`Uploading` → `Synced` → `Failed`). */
  fun cycleSubmissionSyncStatus(submissionId: String)
}
