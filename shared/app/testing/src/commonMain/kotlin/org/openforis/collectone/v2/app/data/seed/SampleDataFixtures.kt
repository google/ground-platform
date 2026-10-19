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
package org.openforis.collectone.v2.app.data.seed

import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStoreTransaction
import org.openforis.collectone.v2.app.data.datasource.local.store.StoredPreferences
import org.openforis.collectone.v2.app.domain.model.MeasurementUnitSystem
import org.openforis.collectone.v2.app.domain.model.SurveyConfig
import org.openforis.collectone.v2.app.domain.model.UserSettings
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDerivation
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft

/**
 * Writes all sample data fixtures inside an existing transaction, matching what [SampleDataSeeder]
 * pulls from a user data service loaded with [UserDataSeedGenerator]'s seed.
 */
fun LocalStoreTransaction.writeSampleData() {
  SampleOrganizationsData.defaultOrganizations().forEach { upsertOrganization(it) }
  // The global library bootstraps the "All users" organization's library.
  putLibrary(GlobalLibrarySeedData.library())
  SampleLibraryData.defaultOrganizationLibraries().forEach { putLibrary(it) }
  val surveys = SampleSurveysData.defaultSampleSurveys()
  surveys.forEach { upsertSurvey(it) }
  for (survey in surveys) {
    val surveyId = survey.id
    val forms = SampleSurveysData.formsForSurvey(surveyId)
    putForms(surveyId, forms)
    putMapLayers(surveyId, SampleMapLayersData.mapLayersForSurvey(surveyId))
    putEntities(surveyId, SampleEntitiesData.entitiesForSurvey(surveyId))
    putStandaloneSubmissions(
      surveyId,
      SampleSubmissionsData.standaloneSubmissionsForSurvey(surveyId),
    )
    putSubmissionGeometries(surveyId, SampleSubmissionsData.submissionGeometriesForSurvey(surveyId))
    putSurveyConfig(
      surveyId,
      SurveyConfig(
        primaryForm = SampleSurveysData.exampleFormForSurveyId(surveyId).formDefinition,
        formsById = forms.associate { it.id to SampleSurveysData.builtInFallbackFormForForm(it) },
      ),
    )
  }
  // Survey editor drafts are derived from the survey data above; only the two surveys with extras
  // that cannot be derived (sharing people, survey area, Data tables) store one.
  putSurveyEditorDraft(
    SampleDataSeeder.DEFAULT_ACTIVE_SURVEY_ID,
    SampleSurveyEditorData.kenyaCoffeeDraft(
      deriveEditorDraft(SampleDataSeeder.DEFAULT_ACTIVE_SURVEY_ID)
    ),
  )
  putSurveyEditorDraft(
    SampleSurveyEditorData.ORGANIZATION_SHARED_SURVEY_ID,
    SampleSurveyEditorData.organizationSharedDraft(
      deriveEditorDraft(SampleSurveyEditorData.ORGANIZATION_SHARED_SURVEY_ID)
    ),
  )
  putMutations(SampleMutationsData.defaultMutations())
  putPlaces(SamplePlacesData.defaultSurveyPlaces())
  putOfflineTilePackages(SampleMapLayersData.defaultOfflineTilePackages())
  putPreferences(
    StoredPreferences(
      activeSurveyId = SampleDataSeeder.DEFAULT_ACTIVE_SURVEY_ID,
      userSettings =
        UserSettings(
          language = "en",
          measurementUnits = MeasurementUnitSystem.METRIC,
          shouldUploadPhotosOnWifiOnly = false,
        ),
      uploadedMediaCacheSizeLabel = "38.4 MB",
      uploadedMediaFileCount = 27,
      seedVersion = SampleDataSeeder.SEED_VERSION,
    )
  )
}

/** Derives the Survey editor draft for [surveyId] from the sample data. */
private fun deriveEditorDraft(surveyId: String): SurveyEditorDraft {
  val forms = SampleSurveysData.formsForSurvey(surveyId)
  val formsById = forms.associate { it.id to SampleSurveysData.builtInFallbackFormForForm(it) }
  return SurveyEditorDerivation.derive(
    surveyId = surveyId,
    survey = SampleSurveysData.defaultSampleSurveys().firstOrNull { it.id == surveyId },
    forms = forms,
    formDefinition = { formsById[it.id] },
    mapLayers = SampleMapLayersData.mapLayersForSurvey(surveyId),
    entities = SampleEntitiesData.entitiesForSurvey(surveyId),
  )
}
