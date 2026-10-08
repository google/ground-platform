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
package org.groundplatform.v2.devtools.prototypeapp.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.LocalStore
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.LocalStoreTransaction
import org.groundplatform.v2.devtools.prototypeapp.domain.model.FormPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.GeospatialEntityItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapLayerItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OfflineTilePackageItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionGeometryPolygon
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyConfig
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyStats
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SyncStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.deriveEntitySyncStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyContent
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyRepository

/** [SurveyRepository] backed by the [LocalStore]. */
class SurveyRepositoryImpl(private val store: LocalStore) : SurveyRepository {
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

  override fun observeOfflineTilePackages(): Flow<List<OfflineTilePackageItem>> =
    store.observeOfflineTilePackages()

  override suspend fun getSurveys(): List<SurveyPreviewItem> = store.transaction { surveys() }

  override suspend fun setSurveys(surveys: List<SurveyPreviewItem>) {
    store.transaction { surveys.forEach { upsertSurvey(it) } }
  }

  override suspend fun getActiveSurveyId(): String = store.transaction {
    preferences().activeSurveyId
  }

  override suspend fun setActiveSurveyId(surveyId: String) {
    store.transaction { updatePreferences { it.copy(activeSurveyId = surveyId) } }
  }

  override suspend fun updateSurvey(
    surveyId: String,
    transform: (SurveyPreviewItem) -> SurveyPreviewItem,
  ): SurveyPreviewItem? = store.transaction { updateSurvey(surveyId, transform) }

  override suspend fun setSurveyDownloaded(
    surveyId: String,
    downloaded: Boolean,
  ): SurveyPreviewItem? = updateSurvey(surveyId) { it.copy(isDownloaded = downloaded) }

  override suspend fun getSurveyConfig(surveyId: String): SurveyConfig? = store.transaction {
    surveyConfig(surveyId)
  }

  override suspend fun setSurveyConfig(surveyId: String, config: SurveyConfig) {
    store.transaction { putSurveyConfig(surveyId, config) }
  }

  override suspend fun getMapLayers(): List<MapLayerItem> = active { mapLayers(it) }

  override suspend fun setMapLayers(layers: List<MapLayerItem>) = active {
    putMapLayers(it, layers)
  }

  override suspend fun toggleLayerVisibility(layerId: String) = active { id ->
    putMapLayers(
      id,
      mapLayers(id).map { if (it.id == layerId) it.copy(isVisible = !it.isVisible) else it },
    )
  }

  override suspend fun setAllLayersVisible(visible: Boolean) = active { id ->
    putMapLayers(id, mapLayers(id).map { it.copy(isVisible = visible) })
  }

  override suspend fun getForms(): List<FormPreviewItem> = active { forms(it) }

  override suspend fun setForms(forms: List<FormPreviewItem>) = active { putForms(it, forms) }

  override suspend fun getEntities(): List<GeospatialEntityItem> = active { entities(it) }

  override suspend fun setEntities(entities: List<GeospatialEntityItem>) = active {
    putEntities(it, entities)
  }

  override suspend fun upsertEntities(entities: List<GeospatialEntityItem>) = active {
    upsertEntities(it, entities)
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

  override suspend fun getOfflineTilePackages(): List<OfflineTilePackageItem> = store.transaction {
    offlineTilePackages()
  }

  override suspend fun setOfflineTilePackages(packages: List<OfflineTilePackageItem>) {
    store.transaction { putOfflineTilePackages(packages) }
  }

  override suspend fun setOfflineTilePackageDownloaded(packageId: String, downloaded: Boolean) {
    store.transaction {
      putOfflineTilePackages(
        offlineTilePackages().map {
          if (it.id == packageId) it.copy(isDownloaded = downloaded) else it
        }
      )
    }
  }

  override suspend fun downloadNewOfflineMapArea(
    regionName: String,
    zoomRangeLabel: String,
    sizeLabel: String,
  ): OfflineTilePackageItem = store.transaction {
    val packages = offlineTilePackages()
    val newPackage =
      OfflineTilePackageItem(
        id = "tile-custom-${packages.size + 1}",
        regionName = regionName.ifBlank { "Custom Survey Viewport Area" },
        tileTypeLabel = "Satellite + Vector Hybrid",
        zoomRangeLabel = zoomRangeLabel,
        sizeLabel = sizeLabel,
        isDownloaded = true,
      )
    putOfflineTilePackages(listOf(newPackage) + packages)
    newPackage
  }

  /** Runs [block] in a transaction with the active survey's ID. */
  private suspend fun <R> active(block: LocalStoreTransaction.(surveyId: String) -> R): R =
    store.transaction {
      block(preferences().activeSurveyId)
    }
}
