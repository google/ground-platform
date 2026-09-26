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

import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.PrototypeAppDataStore
import org.groundplatform.v2.devtools.prototypeapp.domain.model.FormPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.GeospatialEntityItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapLayerItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OfflineTilePackageItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionGeometryPolygon
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SyncStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.deriveEntitySyncStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyRepository

/**
 * Concrete [SurveyRepository] implementation backed by [PrototypeAppDataStore].
 */
class SurveyRepositoryImpl(private val dataStore: PrototypeAppDataStore) : SurveyRepository {
  override fun getSurveys(): List<SurveyPreviewItem> = dataStore.surveys

  override fun setSurveys(surveys: List<SurveyPreviewItem>) {
    dataStore.surveys = surveys
  }

  override fun getActiveSurveyId(): String = dataStore.activeSurveyId

  override fun setActiveSurveyId(surveyId: String) {
    dataStore.activeSurveyId = surveyId
  }

  override fun loadSurveyDatasets(surveyId: String) {
    dataStore.loadSurveyDatasets(surveyId)
  }

  override fun getMapLayers(): List<MapLayerItem> = dataStore.mapLayers

  override fun setMapLayers(layers: List<MapLayerItem>) {
    dataStore.mapLayers = layers
  }

  override fun getForms(): List<FormPreviewItem> = dataStore.forms

  override fun setForms(forms: List<FormPreviewItem>) {
    dataStore.forms = forms
  }

  override fun getEntities(): List<GeospatialEntityItem> = dataStore.entities

  override fun setEntities(entities: List<GeospatialEntityItem>) {
    dataStore.entities = entities
  }

  override fun getStandaloneSubmissions(): List<SubmissionPreviewItem> =
    dataStore.standaloneSubmissions

  override fun setStandaloneSubmissions(submissions: List<SubmissionPreviewItem>) {
    dataStore.standaloneSubmissions = submissions
  }

  override fun getSubmissionGeometries(): List<SubmissionGeometryPolygon> =
    dataStore.submissionGeometries

  override fun setSubmissionGeometries(geometries: List<SubmissionGeometryPolygon>) {
    dataStore.submissionGeometries = geometries
  }

  override fun getOfflineTilePackages(): List<OfflineTilePackageItem> =
    dataStore.offlineTilePackages

  override fun setOfflineTilePackages(packages: List<OfflineTilePackageItem>) {
    dataStore.offlineTilePackages = packages
  }

  override fun downloadSurvey(surveyId: String): SurveyPreviewItem? {
    var updatedItem: SurveyPreviewItem? = null
    dataStore.surveys =
      dataStore.surveys.map { item ->
        if (item.id == surveyId) {
          val next = item.copy(isDownloaded = true)
          updatedItem = next
          next
        } else {
          item
        }
      }
    return updatedItem
  }

  override fun toggleSurveyDownloaded(surveyId: String): SurveyPreviewItem? {
    var updatedItem: SurveyPreviewItem? = null
    dataStore.surveys =
      dataStore.surveys.map { item ->
        if (item.id == surveyId) {
          val next = item.copy(isDownloaded = !item.isDownloaded)
          updatedItem = next
          next
        } else {
          item
        }
      }
    return updatedItem
  }

  override fun toggleLayerVisibility(layerId: String) {
    dataStore.mapLayers =
      dataStore.mapLayers.map { layer ->
        if (layer.id == layerId) layer.copy(isVisible = !layer.isVisible) else layer
      }
  }

  override fun setAllLayersVisible(visible: Boolean) {
    dataStore.mapLayers = dataStore.mapLayers.map { it.copy(isVisible = visible) }
  }

  override fun toggleOfflineTilePackage(packageId: String) {
    dataStore.offlineTilePackages =
      dataStore.offlineTilePackages.map { pkg ->
        if (pkg.id == packageId) pkg.copy(isDownloaded = !pkg.isDownloaded) else pkg
      }
  }

  override fun deleteOfflineTilePackage(packageId: String): OfflineTilePackageItem? {
    val target = dataStore.offlineTilePackages.firstOrNull { it.id == packageId } ?: return null
    dataStore.offlineTilePackages =
      dataStore.offlineTilePackages.map { pkg ->
        if (pkg.id == packageId) pkg.copy(isDownloaded = false) else pkg
      }
    return target.copy(isDownloaded = false)
  }

  override fun downloadNewOfflineMapArea(
    regionName: String,
    zoomRangeLabel: String,
    sizeLabel: String,
  ): OfflineTilePackageItem {
    val newPkgId = "tile-custom-${dataStore.offlineTilePackages.size + 1}"
    val newPkg =
      OfflineTilePackageItem(
        id = newPkgId,
        regionName = regionName.ifBlank { "Custom Survey Viewport Area" },
        tileTypeLabel = "Satellite + Vector Hybrid",
        zoomRangeLabel = zoomRangeLabel,
        sizeLabel = sizeLabel,
        isDownloaded = true,
      )
    dataStore.offlineTilePackages = listOf(newPkg) + dataStore.offlineTilePackages
    return newPkg
  }

  override fun updateEntitySyncStatus(entityId: String, status: SyncStatus) {
    dataStore.entities =
      dataStore.entities.map { entity ->
        if (entity.id == entityId) {
          entity.copy(
            syncStatus = status,
            submissions = entity.submissions.map { it.copy(syncStatus = status) },
          )
        } else {
          entity
        }
      }
  }

  override fun updateSubmissionSyncStatus(submissionId: String, status: SyncStatus) {
    if (dataStore.standaloneSubmissions.any { it.id == submissionId }) {
      dataStore.standaloneSubmissions =
        dataStore.standaloneSubmissions.map { sub ->
          if (sub.id == submissionId) sub.copy(syncStatus = status) else sub
        }
      return
    }
    dataStore.entities =
      dataStore.entities.map { entity ->
        if (entity.submissions.any { it.id == submissionId }) {
          val updatedSubmissions =
            entity.submissions.map { sub ->
              if (sub.id == submissionId) sub.copy(syncStatus = status) else sub
            }
          entity.copy(
            submissions = updatedSubmissions,
            syncStatus = deriveEntitySyncStatus(updatedSubmissions, fallback = entity.syncStatus),
          )
        } else {
          entity
        }
      }
  }

  override fun retryFailedUploadsForEntity(entityId: String) {
    dataStore.entities =
      dataStore.entities.map { entity ->
        if (entity.id == entityId) {
          val updatedSubmissions =
            entity.submissions.map { sub ->
              if (sub.syncStatus == SyncStatus.FAILED) {
                sub.copy(syncStatus = SyncStatus.UPLOADING)
              } else {
                sub
              }
            }
          val nextEntityStatus =
            if (entity.syncStatus == SyncStatus.FAILED) {
              SyncStatus.UPLOADING
            } else {
              deriveEntitySyncStatus(updatedSubmissions, fallback = entity.syncStatus)
            }
          entity.copy(submissions = updatedSubmissions, syncStatus = nextEntityStatus)
        } else {
          entity
        }
      }
  }

  override fun resetToDefaults() {
    dataStore.resetToDefaults()
  }
}
