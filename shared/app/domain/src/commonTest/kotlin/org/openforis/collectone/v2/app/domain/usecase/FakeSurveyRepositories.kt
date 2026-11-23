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
package org.openforis.collectone.v2.app.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.openforis.collectone.v2.app.domain.model.FormPreviewItem
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.MapLayerItem
import org.openforis.collectone.v2.app.domain.model.MutationLogItem
import org.openforis.collectone.v2.app.domain.model.SubmissionGeometryPolygon
import org.openforis.collectone.v2.app.domain.model.SubmissionPreviewItem
import org.openforis.collectone.v2.app.domain.model.SurveyConfig
import org.openforis.collectone.v2.app.domain.model.SurveyLifecycleState
import org.openforis.collectone.v2.app.domain.model.SurveyOutcome
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.SurveyStats
import org.openforis.collectone.v2.app.domain.model.SyncStatus
import org.openforis.collectone.v2.app.domain.repository.MutationRepository
import org.openforis.collectone.v2.app.domain.repository.SurveyContent
import org.openforis.collectone.v2.app.domain.repository.SurveyRepository
import org.openforis.collectone.v2.app.domain.repository.TransactionRunner

/**
 * In-memory [SurveyRepository] holding the active survey's content in plain fields, for use cases
 * that read and write it. Calls outside that content fail, so a test notices when the use case
 * under test starts depending on more of the repository.
 */
internal class FakeSurveyRepository(
  var activeSurveyId: String = "survey-1",
  var forms: List<FormPreviewItem> = emptyList(),
  var mapLayers: List<MapLayerItem> = emptyList(),
  var entities: List<GeospatialEntityItem> = emptyList(),
  var standaloneSubmissions: List<SubmissionPreviewItem> = emptyList(),
  var surveys: List<SurveyPreviewItem> = emptyList(),
  val configs: MutableMap<String, SurveyConfig> = mutableMapOf(),
) : SurveyRepository {
  private fun unused(): Nothing = error("Not used by the use cases under test")

  override fun observeSurveys(): Flow<List<SurveyPreviewItem>> = unused()

  override fun observeActiveSurveyId(): Flow<String> = unused()

  override fun observeSurveyContent(surveyId: String): Flow<SurveyContent> = unused()

  override fun observeSurveyStats(): Flow<Map<String, SurveyStats>> = unused()

  override fun observeSurveyConfigs(): Flow<Map<String, SurveyConfig>> = unused()

  override suspend fun getSurveys(): List<SurveyPreviewItem> = surveys

  override suspend fun setSurveys(surveys: List<SurveyPreviewItem>) {
    this.surveys = surveys
  }

  override suspend fun getActiveSurveyId(): String = activeSurveyId

  override suspend fun setActiveSurveyId(surveyId: String) {
    activeSurveyId = surveyId
  }

  override fun observeOpenSurveyId(): StateFlow<String?> = MutableStateFlow(activeSurveyId)

  override suspend fun restoreActiveSurvey(): String? = unused()

  override suspend fun forgetActiveSurvey() = unused()

  override suspend fun updateSurvey(
    surveyId: String,
    transform: (SurveyPreviewItem) -> SurveyPreviewItem,
  ): SurveyPreviewItem? {
    val existing = surveys.firstOrNull { it.id == surveyId } ?: return null
    val updated = transform(existing)
    surveys = surveys.map { if (it.id == surveyId) updated else it }
    return updated
  }

  override suspend fun deleteSurvey(surveyId: String) = unused()

  override suspend fun setSurveyDownloaded(
    surveyId: String,
    downloaded: Boolean,
  ): SurveyPreviewItem? = unused()

  override suspend fun setSurveyState(
    surveyId: String,
    state: SurveyLifecycleState,
    closedAt: String?,
  ): SurveyPreviewItem? = unused()

  override suspend fun setSurveyOutcome(
    surveyId: String,
    outcome: SurveyOutcome?,
  ): SurveyPreviewItem? = unused()

  override suspend fun getSurveyConfig(surveyId: String): SurveyConfig? = configs[surveyId]

  override suspend fun setSurveyConfig(surveyId: String, config: SurveyConfig) {
    configs[surveyId] = config
  }

  override suspend fun getMapLayers(): List<MapLayerItem> = mapLayers

  override suspend fun setMapLayers(layers: List<MapLayerItem>) {
    mapLayers = layers
  }

  override suspend fun toggleLayerVisibility(layerId: String) = unused()

  override suspend fun setAllLayersVisible(visible: Boolean) = unused()

  override suspend fun getForms(): List<FormPreviewItem> = forms

  override suspend fun setForms(forms: List<FormPreviewItem>) {
    this.forms = forms
  }

  override suspend fun getEntities(): List<GeospatialEntityItem> = entities

  override suspend fun setEntities(entities: List<GeospatialEntityItem>) {
    this.entities = entities
  }

  override suspend fun upsertEntities(entities: List<GeospatialEntityItem>) {
    val byId = entities.associateBy { it.id }
    val existingIds = this.entities.map { it.id }.toSet()
    this.entities =
      this.entities.map { byId[it.id] ?: it } + entities.filter { it.id !in existingIds }
  }

  override suspend fun getStandaloneSubmissions(): List<SubmissionPreviewItem> =
    standaloneSubmissions

  override suspend fun setStandaloneSubmissions(submissions: List<SubmissionPreviewItem>) {
    standaloneSubmissions = submissions
  }

  override suspend fun getSubmissionGeometries(): List<SubmissionGeometryPolygon> = emptyList()

  override suspend fun updateEntitySyncStatus(entityId: String, status: SyncStatus) = unused()

  override suspend fun updateSubmissionSyncStatus(submissionId: String, status: SyncStatus) =
    unused()

  override suspend fun retryFailedUploadsForEntity(entityId: String) = unused()

  override suspend fun markAllSynced() = unused()
}

/** In-memory [MutationRepository]: the log is a plain list, newest first. */
internal class FakeMutationRepository(var mutations: List<MutationLogItem> = emptyList()) :
  MutationRepository {
  override fun observeMutations(): Flow<List<MutationLogItem>> = MutableStateFlow(mutations)

  override suspend fun getMutations(): List<MutationLogItem> = mutations

  override suspend fun setMutations(mutations: List<MutationLogItem>) {
    this.mutations = mutations
  }

  override suspend fun prependMutations(newMutations: List<MutationLogItem>) {
    mutations = newMutations + mutations
  }

  override suspend fun updateMutation(
    mutationId: String,
    transform: (MutationLogItem) -> MutationLogItem,
  ) {
    mutations = mutations.map { if (it.id == mutationId) transform(it) else it }
  }
}

/** [TransactionRunner] that runs the block directly and counts how often it was asked to. */
internal class CountingTransactionRunner : TransactionRunner {
  var transactions = 0
    private set

  override suspend fun <R> invoke(block: suspend () -> R): R {
    transactions++
    return block()
  }
}
