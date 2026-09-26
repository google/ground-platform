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

import org.groundplatform.v2.devtools.prototypeapp.domain.model.FormPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.GeospatialEntityItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapLayerItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OfflineTilePackageItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionGeometryPolygon
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SyncStatus

/**
 * Domain repository contract for managing surveys, active survey geospatial entities, forms,
 * standalone submissions, submission geometries, map layers, and offline basemap tile packages.
 */
interface SurveyRepository {
  fun getSurveys(): List<SurveyPreviewItem>

  fun setSurveys(surveys: List<SurveyPreviewItem>)

  fun getActiveSurveyId(): String

  fun setActiveSurveyId(surveyId: String)

  fun loadSurveyDatasets(surveyId: String)

  fun getMapLayers(): List<MapLayerItem>

  fun setMapLayers(layers: List<MapLayerItem>)

  fun getForms(): List<FormPreviewItem>

  fun setForms(forms: List<FormPreviewItem>)

  fun getEntities(): List<GeospatialEntityItem>

  fun setEntities(entities: List<GeospatialEntityItem>)

  fun getStandaloneSubmissions(): List<SubmissionPreviewItem>

  fun setStandaloneSubmissions(submissions: List<SubmissionPreviewItem>)

  fun getSubmissionGeometries(): List<SubmissionGeometryPolygon>

  fun setSubmissionGeometries(geometries: List<SubmissionGeometryPolygon>)

  fun getOfflineTilePackages(): List<OfflineTilePackageItem>

  fun setOfflineTilePackages(packages: List<OfflineTilePackageItem>)

  fun downloadSurvey(surveyId: String): SurveyPreviewItem?

  fun toggleSurveyDownloaded(surveyId: String): SurveyPreviewItem?

  fun toggleLayerVisibility(layerId: String)

  fun setAllLayersVisible(visible: Boolean)

  fun toggleOfflineTilePackage(packageId: String)

  fun deleteOfflineTilePackage(packageId: String): OfflineTilePackageItem?

  fun downloadNewOfflineMapArea(
    regionName: String,
    zoomRangeLabel: String,
    sizeLabel: String,
  ): OfflineTilePackageItem

  fun updateEntitySyncStatus(entityId: String, status: SyncStatus)

  fun updateSubmissionSyncStatus(submissionId: String, status: SyncStatus)

  fun retryFailedUploadsForEntity(entityId: String)

  fun resetToDefaults()
}
