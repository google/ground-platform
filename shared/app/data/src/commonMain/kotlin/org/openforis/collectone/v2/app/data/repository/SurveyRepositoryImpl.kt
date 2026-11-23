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
package org.openforis.collectone.v2.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.openforis.collectone.v2.app.data.datasource.local.session.ActiveSurveyStore
import org.openforis.collectone.v2.app.data.datasource.local.session.InMemoryActiveSurveyStore
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStoreTransaction
import org.openforis.collectone.v2.app.domain.model.FormPreviewItem
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.MapLayerItem
import org.openforis.collectone.v2.app.domain.model.SubmissionGeometryPolygon
import org.openforis.collectone.v2.app.domain.model.SubmissionPreviewItem
import org.openforis.collectone.v2.app.domain.model.SurveyConfig
import org.openforis.collectone.v2.app.domain.model.SurveyLifecycleState
import org.openforis.collectone.v2.app.domain.model.SurveyOutcome
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.SurveyStats
import org.openforis.collectone.v2.app.domain.model.SurveyThumbnailGenerator
import org.openforis.collectone.v2.app.domain.model.SyncStatus
import org.openforis.collectone.v2.app.domain.model.deriveEntitySyncStatus
import org.openforis.collectone.v2.app.domain.repository.SurveyContent
import org.openforis.collectone.v2.app.domain.repository.SurveyRepository

/**
 * [SurveyRepository] backed by the [LocalStore], which remembers the survey last made active in an
 * [ActiveSurveyStore] (in the device's local database on mobile) so a relaunch can reopen it.
 *
 * Two notions of "current survey" coexist. The active survey (`StoredPreferences.activeSurveyId`)
 * is stored with the data (seeding and restoring sync state set it), and every per-survey method
 * without a survey ID (layers, forms, map features, submissions, sync statuses) acts on it, reading
 * it in the same transaction as the write so a concurrent switch can't split them. The open survey
 * ([observeOpenSurveyId]) is the one the user actually opened this session, null until then.
 */
class SurveyRepositoryImpl(
  private val store: LocalStore,
  private val activeSurveyStore: ActiveSurveyStore = InMemoryActiveSurveyStore(),
) : SurveyRepository {
  /** In memory only: a relaunch reopens a survey through [restoreActiveSurvey]. */
  private val openSurveyId = MutableStateFlow<String?>(null)

  override fun observeSurveys(): Flow<List<SurveyPreviewItem>> = store.observeSurveys()

  override fun observeActiveSurveyId(): Flow<String> =
    store.observePreferences().map { it.activeSurveyId }.distinctUntilChanged()

  override fun observeSurveyContent(surveyId: String): Flow<SurveyContent> =
    combine(
      store.observeForms(surveyId),
      store.observeMapLayers(surveyId),
      store.observeEntities(surveyId),
      store.observeStandaloneSubmissions(surveyId),
      combine(
        store.observeSubmissionGeometries(surveyId),
        store.observeSurveyConfig(surveyId),
        ::Pair,
      ),
    ) { forms, layers, entities, standalone, (geometries, config) ->
      SurveyContent(
        forms = forms,
        mapLayers = layers,
        entities = entities,
        standaloneSubmissions = standalone,
        submissionGeometries = geometries,
        config = config,
      )
    }

  override fun observeSurveyStats(): Flow<Map<String, SurveyStats>> = store.observeSurveyStats()

  override fun observeSurveyConfigs(): Flow<Map<String, SurveyConfig>> =
    store.observeSurveyConfigs()

  override suspend fun getSurveys(): List<SurveyPreviewItem> = store.transaction { surveys() }

  override suspend fun setSurveys(surveys: List<SurveyPreviewItem>) {
    store.transaction { surveys.forEach { upsertSurvey(it) } }
  }

  override suspend fun getActiveSurveyId(): String = store.transaction {
    preferences().activeSurveyId
  }

  override suspend fun setActiveSurveyId(surveyId: String) {
    store.transaction { updatePreferences { it.copy(activeSurveyId = surveyId) } }
    if (surveyId.isBlank()) activeSurveyStore.clear() else activeSurveyStore.save(surveyId)
    openSurveyId.value = surveyId.ifBlank { null }
  }

  override fun observeOpenSurveyId(): StateFlow<String?> = openSurveyId.asStateFlow()

  override suspend fun restoreActiveSurvey(): String? {
    val surveyId = activeSurveyStore.load() ?: return null
    val restored = store.transaction {
      if (surveys().none { it.id == surveyId }) return@transaction null
      updatePreferences { it.copy(activeSurveyId = surveyId) }
      surveyId
    }
    if (restored != null) openSurveyId.value = restored
    return restored
  }

  override suspend fun forgetActiveSurvey() {
    activeSurveyStore.clear()
    openSurveyId.value = null
  }

  /** Forgets the open survey when it is [surveyId], which left the device. */
  private suspend fun forgetActiveSurveyIfItIs(surveyId: String) {
    if (activeSurveyStore.load() == surveyId) activeSurveyStore.clear()
    if (openSurveyId.value == surveyId) openSurveyId.value = null
  }

  override suspend fun updateSurvey(
    surveyId: String,
    transform: (SurveyPreviewItem) -> SurveyPreviewItem,
  ): SurveyPreviewItem? = store.transaction { updateSurvey(surveyId, transform) }

  override suspend fun deleteSurvey(surveyId: String) {
    store.transaction { deleteSurvey(surveyId) }
    forgetActiveSurveyIfItIs(surveyId)
  }

  /**
   * Adds the survey to (or removes it from) the device's downloaded set and updates its list entry.
   * The device-local flag is never sent to the server; with the sync engine, the next sync fetches
   * the survey's definition and data, or drops them once its pending uploads have drained. Removing
   * the download forgets the survey as the one to reopen on relaunch.
   */
  override suspend fun setSurveyDownloaded(
    surveyId: String,
    downloaded: Boolean,
  ): SurveyPreviewItem? {
    val survey = store.transaction {
      updateSyncState {
        it.copy(
          downloadedSurveyIds =
            if (downloaded) it.downloadedSurveyIds + surveyId else it.downloadedSurveyIds - surveyId
        )
      }
      updateSurvey(surveyId) { it.copy(isDownloaded = downloaded) }
    }
    if (!downloaded) forgetActiveSurveyIfItIs(surveyId)
    return survey
  }

  override suspend fun setSurveyState(
    surveyId: String,
    state: SurveyLifecycleState,
    closedAt: String?,
  ): SurveyPreviewItem? = updateSurvey(surveyId) { it.copy(state = state, closedAt = closedAt) }

  override suspend fun setSurveyOutcome(
    surveyId: String,
    outcome: SurveyOutcome?,
  ): SurveyPreviewItem? = updateSurvey(surveyId) { it.copy(outcome = outcome) }

  override suspend fun getSurveyConfig(surveyId: String): SurveyConfig? = store.transaction {
    surveyConfig(surveyId)
  }

  override suspend fun setSurveyConfig(surveyId: String, config: SurveyConfig) {
    store.transaction { putSurveyConfig(surveyId, config) }
  }

  override suspend fun getMapLayers(): List<MapLayerItem> = active { mapLayers(it) }

  override suspend fun setMapLayers(layers: List<MapLayerItem>) = active {
    putMapLayers(it, layers)
    refreshSurveyThumbnail(it)
  }

  override suspend fun toggleLayerVisibility(layerId: String) = active { id ->
    putMapLayers(
      id,
      mapLayers(id).map { if (it.id == layerId) it.copy(isVisible = !it.isVisible) else it },
    )
    refreshSurveyThumbnail(id)
  }

  override suspend fun setAllLayersVisible(visible: Boolean) = active { id ->
    putMapLayers(id, mapLayers(id).map { it.copy(isVisible = visible) })
    refreshSurveyThumbnail(id)
  }

  override suspend fun getForms(): List<FormPreviewItem> = active { forms(it) }

  override suspend fun setForms(forms: List<FormPreviewItem>) = active { putForms(it, forms) }

  override suspend fun getEntities(): List<GeospatialEntityItem> = active { entities(it) }

  override suspend fun setEntities(entities: List<GeospatialEntityItem>) = active {
    putEntities(it, entities)
    refreshSurveyThumbnail(it)
  }

  override suspend fun upsertEntities(entities: List<GeospatialEntityItem>) = active {
    upsertEntities(it, entities)
    refreshSurveyThumbnail(it)
  }

  override suspend fun getStandaloneSubmissions(): List<SubmissionPreviewItem> = active {
    standaloneSubmissions(it)
  }

  override suspend fun setStandaloneSubmissions(submissions: List<SubmissionPreviewItem>) = active {
    putStandaloneSubmissions(it, submissions)
  }

  override suspend fun getSubmissionGeometries(): List<SubmissionGeometryPolygon> = active {
    submissionGeometries(it)
  }

  override suspend fun updateEntitySyncStatus(entityId: String, status: SyncStatus) = active { id ->
    val entity = entity(id, entityId) ?: return@active
    upsertEntity(
      id,
      entity.copy(
        syncStatus = status,
        submissions = entity.submissions.map { it.copy(syncStatus = status) },
      ),
    )
  }

  /**
   * Marks a standalone submission, or a map feature's submission and then the feature, whose status
   * is re-derived from its submissions (a failed one wins over an uploading one).
   */
  override suspend fun updateSubmissionSyncStatus(submissionId: String, status: SyncStatus) =
    active { id ->
      val standalone = standaloneSubmissions(id)
      if (standalone.any { it.id == submissionId }) {
        putStandaloneSubmissions(
          id,
          standalone.map { if (it.id == submissionId) it.copy(syncStatus = status) else it },
        )
        return@active
      }
      val entity = entities(id).firstOrNull { e -> e.submissions.any { it.id == submissionId } }
      if (entity != null) {
        val updated =
          entity.submissions.map { if (it.id == submissionId) it.copy(syncStatus = status) else it }
        upsertEntity(
          id,
          entity.copy(
            submissions = updated,
            syncStatus = deriveEntitySyncStatus(updated, fallback = entity.syncStatus),
          ),
        )
      }
    }

  /**
   * Shows a map feature's failed uploads as uploading again. It doesn't touch the outbox: with the
   * sync engine, [OutboxSyncRepositoryImpl.retryRecord] requeues the mutations themselves.
   */
  override suspend fun retryFailedUploadsForEntity(entityId: String) = active { id ->
    val entity = entity(id, entityId) ?: return@active
    val updated =
      entity.submissions.map {
        if (it.syncStatus == SyncStatus.FAILED) it.copy(syncStatus = SyncStatus.UPLOADING) else it
      }
    val nextStatus =
      if (entity.syncStatus == SyncStatus.FAILED) {
        SyncStatus.UPLOADING
      } else {
        deriveEntitySyncStatus(updated, fallback = entity.syncStatus)
      }
    upsertEntity(id, entity.copy(submissions = updated, syncStatus = nextStatus))
  }

  override suspend fun markAllSynced() = active { id ->
    upsertEntities(
      id,
      entities(id).map { entity ->
        entity.copy(
          submissions = entity.submissions.map { it.copy(syncStatus = SyncStatus.SYNCED) },
          syncStatus = SyncStatus.SYNCED,
        )
      },
    )
    putStandaloneSubmissions(
      id,
      standaloneSubmissions(id).map { it.copy(syncStatus = SyncStatus.SYNCED) },
    )
  }

  /** Runs [block] in a transaction with the active survey's ID. */
  private suspend fun <R> active(block: LocalStoreTransaction.(surveyId: String) -> R): R =
    store.transaction {
      block(preferences().activeSurveyId)
    }

  private fun LocalStoreTransaction.refreshSurveyThumbnail(surveyId: String) {
    val currentEntities = entities(surveyId)
    val currentLayers = mapLayers(surveyId)
    val geoCount = currentEntities.count { it.geometry != null }
    updateSurvey(surveyId) { existing ->
      existing.copy(
        entityCount =
          if (existing.entityCount == 0 || existing.isDownloaded) geoCount
          else existing.entityCount,
        thumbnail =
          SurveyThumbnailGenerator.fromRuntime(
            surveyId = surveyId,
            surveyArea = existing.surveyArea,
            mapLayers = currentLayers,
            entities = currentEntities,
            tileUrlTemplate = existing.thumbnail.tileUrlTemplate,
          ),
      )
    }
  }
}
