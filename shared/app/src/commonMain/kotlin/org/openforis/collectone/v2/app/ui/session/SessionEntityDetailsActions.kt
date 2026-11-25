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
package org.openforis.collectone.v2.app.ui.session

import org.openforis.collectone.v2.app.ui.AppViewModels
import org.openforis.collectone.v2.app.ui.datacollection.EntityDetailsActions

/**
 * Intents of the map feature details surfaces, applied to the session's [viewModels]. Cycling sync
 * statuses only does something where the client simulates uploads ([onCycleEntitySyncStatus] and
 * [onCycleSubmissionSyncStatus], as in the workbench).
 */
class SessionEntityDetailsActions(
  private val viewModels: AppViewModels,
  private val onCycleEntitySyncStatus: (entityId: String) -> Unit = {},
  private val onCycleSubmissionSyncStatus: (submissionId: String) -> Unit = {},
) : EntityDetailsActions {
  private val dataCollection = viewModels.dataCollection

  override fun selectEntity(entityId: String?) = viewModels.surveyMap.selectEntity(entityId)

  override fun showDatasetInTable(datasetId: String) =
    viewModels.dashboard.showDatasetInTable(datasetId)

  override fun toggleNavigationToEntity(entityId: String) =
    viewModels.surveyMap.toggleNavigationToEntity(entityId)

  override fun openUploadsForEntity(entityId: String) =
    viewModels.dashboard.openUploadsForEntity(entityId)

  override fun showEntitySubmissions() = viewModels.dashboard.showEntitySubmissions()

  override fun showEntityProperties() = dataCollection.showEntityProperties()

  override fun selectSubmissionDetail(submissionId: String?) =
    dataCollection.selectSubmissionDetail(submissionId)

  override fun launchFormForEntity(entityId: String, formId: String) =
    dataCollection.launchFormForEntity(entityId, formId)

  override fun cycleEntitySyncStatus(entityId: String) = onCycleEntitySyncStatus(entityId)

  override fun cycleSubmissionSyncStatus(submissionId: String) =
    onCycleSubmissionSyncStatus(submissionId)

  override fun openEntityQrCode(entityId: String) = dataCollection.openEntityQrCode(entityId)

  override fun shareEntityPdf(entityId: String) = dataCollection.shareEntityPdf(entityId)

  override fun shareSubmissionPdf(submissionId: String) =
    dataCollection.shareSubmissionPdf(submissionId)
}
