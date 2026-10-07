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
package org.groundplatform.v2.devtools.prototypeapp.domain.repository

import kotlinx.coroutines.flow.Flow
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
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyEditorDraft

/** Everything stored for one survey. */
data class SurveyContent(
  val forms: List<FormPreviewItem> = emptyList(),
  val mapLayers: List<MapLayerItem> = emptyList(),
  val entities: List<GeospatialEntityItem> = emptyList(),
  val standaloneSubmissions: List<SubmissionPreviewItem> = emptyList(),
  val submissionGeometries: List<SubmissionGeometryPolygon> = emptyList(),
  val config: SurveyConfig? = null,
  /** The Survey editor's draft of this survey, or `null` if it has never been edited. */
  val editorDraft: SurveyEditorDraft? = null,
)

/**
 * Domain repository contract for surveys and their content (forms, map layers, map features,
 * submissions, submission geometries) and offline basemap tile packages.
 *
 * Reads are observable [Flow]s; writes are `suspend` functions. Methods without a `surveyId`
 * parameter act on the active survey.
 */
interface SurveyRepository {
  fun observeSurveys(): Flow<List<SurveyPreviewItem>>

  fun observeActiveSurveyId(): Flow<String>

  fun observeSurveyContent(surveyId: String): Flow<SurveyContent>

  fun observeSurveyStats(): Flow<Map<String, SurveyStats>>

  fun observeSurveyConfigs(): Flow<Map<String, SurveyConfig>>

  fun observeOfflineTilePackages(): Flow<List<OfflineTilePackageItem>>

  suspend fun getSurveys(): List<SurveyPreviewItem>

  /** Inserts or replaces the given surveys (matched by ID). */
  suspend fun setSurveys(surveys: List<SurveyPreviewItem>)

  suspend fun getActiveSurveyId(): String

  suspend fun setActiveSurveyId(surveyId: String)

  suspend fun updateSurvey(
    surveyId: String,
    transform: (SurveyPreviewItem) -> SurveyPreviewItem,
  ): SurveyPreviewItem?

  suspend fun setSurveyDownloaded(surveyId: String, downloaded: Boolean): SurveyPreviewItem?

  suspend fun getSurveyConfig(surveyId: String): SurveyConfig?

  suspend fun setSurveyConfig(surveyId: String, config: SurveyConfig)

  // Active survey content -------------------------------------------------------------------------

  suspend fun getMapLayers(): List<MapLayerItem>

  suspend fun setMapLayers(layers: List<MapLayerItem>)

  suspend fun toggleLayerVisibility(layerId: String)

  suspend fun setAllLayersVisible(visible: Boolean)

  suspend fun getForms(): List<FormPreviewItem>

  suspend fun setForms(forms: List<FormPreviewItem>)

  suspend fun getEntities(): List<GeospatialEntityItem>

  /** Replaces all map features in the active survey (keeping the given order). */
  suspend fun setEntities(entities: List<GeospatialEntityItem>)

  /** Inserts or replaces map features in the active survey; new ones are appended. */
  suspend fun upsertEntities(entities: List<GeospatialEntityItem>)

  suspend fun getStandaloneSubmissions(): List<SubmissionPreviewItem>

  suspend fun setStandaloneSubmissions(submissions: List<SubmissionPreviewItem>)

  suspend fun getSubmissionGeometries(): List<SubmissionGeometryPolygon>

  suspend fun updateEntitySyncStatus(entityId: String, status: SyncStatus)

  suspend fun updateSubmissionSyncStatus(submissionId: String, status: SyncStatus)

  suspend fun retryFailedUploadsForEntity(entityId: String)

  /** Marks every submission and map feature in the active survey as synced. */
  suspend fun markAllSynced()

  // Offline tile packages -------------------------------------------------------------------------

  suspend fun getOfflineTilePackages(): List<OfflineTilePackageItem>

  suspend fun setOfflineTilePackages(packages: List<OfflineTilePackageItem>)

  suspend fun setOfflineTilePackageDownloaded(packageId: String, downloaded: Boolean)

  suspend fun downloadNewOfflineMapArea(
    regionName: String,
    zoomRangeLabel: String,
    sizeLabel: String,
  ): OfflineTilePackageItem
}
