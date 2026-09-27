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
package org.groundplatform.v2.devtools.prototypeapp.data.datasource.local

import groundplatform.v2.forms.FormDef
import org.groundplatform.v2.core.forms.ui.WorkbenchExampleForm
import org.groundplatform.v2.devtools.prototypeapp.domain.model.FormPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.GeospatialEntityItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapLayerItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MeasurementUnitSystem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MutationLogItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OfflineTilePackageItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionGeometryPolygon
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPlaceItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.UserSettings

/**
 * In-memory local data store (`PrototypeAppDataStore`) coordinating hardcoded sample datasets
 * (`PrototypeFakeSurveysData`, `PrototypeFakeEntitiesData`, `PrototypeFakeSubmissionsData`,
 * `PrototypeFakeMapLayersData`, `PrototypeFakePlacesData`, `PrototypeFakeMutationsData`) for
 * `devtools/prototypeApp`.
 */
class PrototypeAppDataStore(
  initialSurveys: List<SurveyPreviewItem> = defaultSampleSurveys(),
  initialActiveSurveyId: String = "survey-kenya-coffee",
  initialMapLayers: List<MapLayerItem> = mapLayersForSurvey(initialActiveSurveyId),
  initialForms: List<FormPreviewItem> = formsForSurvey(initialActiveSurveyId),
  initialEntities: List<GeospatialEntityItem> = entitiesForSurvey(initialActiveSurveyId),
  initialStandaloneSubmissions: List<SubmissionPreviewItem> =
    standaloneSubmissionsForSurvey(initialActiveSurveyId),
  initialSubmissionGeometries: List<SubmissionGeometryPolygon> =
    submissionGeometriesForSurvey(initialActiveSurveyId),
  initialOfflineTilePackages: List<OfflineTilePackageItem> = defaultOfflineTilePackages(),
  initialMutations: List<MutationLogItem> = defaultMutations(),
  initialPlaces: List<SurveyPlaceItem> = defaultSurveyPlaces(),
  initialUserSettings: UserSettings = defaultUserSettings(),
) {
  var surveys: List<SurveyPreviewItem> = initialSurveys
  var activeSurveyId: String = initialActiveSurveyId
  var mapLayers: List<MapLayerItem> = initialMapLayers
  var forms: List<FormPreviewItem> = initialForms
  var entities: List<GeospatialEntityItem> = initialEntities
  var standaloneSubmissions: List<SubmissionPreviewItem> = initialStandaloneSubmissions
  var submissionGeometries: List<SubmissionGeometryPolygon> = initialSubmissionGeometries
  var offlineTilePackages: List<OfflineTilePackageItem> = initialOfflineTilePackages
  var mutations: List<MutationLogItem> = initialMutations
  var places: List<SurveyPlaceItem> = initialPlaces
  var userSettings: UserSettings = initialUserSettings
  var uploadedMediaCacheSizeLabel: String = DEFAULT_UPLOADED_MEDIA_CACHE_SIZE_LABEL
  var uploadedMediaFileCount: Int = DEFAULT_UPLOADED_MEDIA_FILE_COUNT

  /** Loads all datasets for [surveyId] into the active data store state. */
  fun loadSurveyDatasets(surveyId: String) {
    activeSurveyId = surveyId
    forms = formsForSurvey(surveyId)
    entities = entitiesForSurvey(surveyId)
    standaloneSubmissions = standaloneSubmissionsForSurvey(surveyId)
    submissionGeometries = submissionGeometriesForSurvey(surveyId)
    mapLayers = mapLayersForSurvey(surveyId)
  }

  /** Resets the data store back to its initial hardcoded defaults. */
  fun resetToDefaults() {
    activeSurveyId = "survey-kenya-coffee"
    surveys = defaultSampleSurveys()
    loadSurveyDatasets(activeSurveyId)
    offlineTilePackages = defaultOfflineTilePackages()
    mutations = defaultMutations()
    places = defaultSurveyPlaces()
    userSettings = defaultUserSettings()
    uploadedMediaCacheSizeLabel = DEFAULT_UPLOADED_MEDIA_CACHE_SIZE_LABEL
    uploadedMediaFileCount = DEFAULT_UPLOADED_MEDIA_FILE_COUNT
  }

  companion object {
    const val DEFAULT_UPLOADED_MEDIA_CACHE_SIZE_LABEL: String = "38.4 MB"
    const val DEFAULT_UPLOADED_MEDIA_FILE_COUNT: Int = 27
    const val ENTITY_REF_FIELD_PATH: String = "/data/target_entity"

    val DEFAULT_PROTOTYPE_XFORMS_XML: String
      get() = PrototypeFakeSurveysData.DEFAULT_PROTOTYPE_XFORMS_XML

    const val BAOBAB_BIOMETRICS_SAMPLE_XFORMS_XML: String =
      PrototypeFakeSurveysData.BAOBAB_BIOMETRICS_SAMPLE_XFORMS_XML

    fun defaultUserSettings(): UserSettings =
      UserSettings(
        language = "en",
        measurementUnits = MeasurementUnitSystem.METRIC,
        shouldUploadPhotosOnWifiOnly = true,
      )

    fun builtInFallbackXFormsXmlForForm(form: FormPreviewItem): String =
      PrototypeFakeSurveysData.builtInFallbackXFormsXmlForForm(form)

    fun defaultPlaces(): List<SurveyPlaceItem> = PrototypeFakePlacesData.defaultPlaces()

    fun defaultSurveyPlaces(): List<SurveyPlaceItem> = PrototypeFakePlacesData.defaultSurveyPlaces()

    fun defaultSampleSurveys(): List<SurveyPreviewItem> =
      PrototypeFakeSurveysData.defaultSampleSurveys()

    fun cachedExampleFormDef(example: WorkbenchExampleForm): FormDef? =
      PrototypeFakeSurveysData.cachedExampleFormDef(example)

    fun surveyIdForExampleForm(example: WorkbenchExampleForm): String =
      PrototypeFakeSurveysData.surveyIdForExampleForm(example)

    fun exampleFormForSurveyId(surveyId: String): WorkbenchExampleForm =
      PrototypeFakeSurveysData.exampleFormForSurveyId(surveyId)

    fun preloadedEntityCountForSurvey(surveyId: String): Int =
      PrototypeFakeSurveysData.preloadedEntityCountForSurvey(surveyId)

    fun preloadedSubmissionCountForSurvey(surveyId: String): Int =
      PrototypeFakeSurveysData.preloadedSubmissionCountForSurvey(surveyId)

    fun formsForSurvey(surveyId: String): List<FormPreviewItem> =
      PrototypeFakeSurveysData.formsForSurvey(surveyId)

    fun entitiesForSurvey(surveyId: String): List<GeospatialEntityItem> =
      PrototypeFakeEntitiesData.entitiesForSurvey(surveyId)

    fun standaloneSubmissionsForSurvey(surveyId: String): List<SubmissionPreviewItem> =
      PrototypeFakeSubmissionsData.standaloneSubmissionsForSurvey(surveyId)

    fun submissionGeometriesForSurvey(surveyId: String): List<SubmissionGeometryPolygon> =
      PrototypeFakeSubmissionsData.submissionGeometriesForSurvey(surveyId)

    fun mapLayersForSurvey(surveyId: String): List<MapLayerItem> =
      PrototypeFakeMapLayersData.mapLayersForSurvey(surveyId)

    fun defaultMapLayers(): List<MapLayerItem> = PrototypeFakeMapLayersData.defaultMapLayers()

    fun defaultSubmissionGeometries(): List<SubmissionGeometryPolygon> =
      PrototypeFakeSubmissionsData.defaultSubmissionGeometries()

    fun defaultForms(): List<FormPreviewItem> = PrototypeFakeSurveysData.defaultForms()

    fun defaultStandaloneSubmissions(): List<SubmissionPreviewItem> =
      PrototypeFakeSubmissionsData.defaultStandaloneSubmissions()

    fun defaultGeospatialEntities(): List<GeospatialEntityItem> =
      PrototypeFakeEntitiesData.defaultGeospatialEntities()

    fun defaultOfflineTilePackages(): List<OfflineTilePackageItem> =
      PrototypeFakeMapLayersData.defaultOfflineTilePackages()

    fun defaultMutations(): List<MutationLogItem> = PrototypeFakeMutationsData.defaultMutations()
  }
}
