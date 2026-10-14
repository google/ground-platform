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
package org.groundplatform.v2.app.data.seed

import org.groundplatform.v2.app.data.datasource.local.store.LocalStore
import org.groundplatform.v2.app.data.datasource.local.store.LocalStoreTransaction
import org.groundplatform.v2.app.data.datasource.local.store.StoredPreferences
import org.groundplatform.v2.app.domain.model.MeasurementUnitSystem
import org.groundplatform.v2.app.domain.model.SurveyConfig
import org.groundplatform.v2.app.domain.model.UserSettings
import org.groundplatform.v2.app.domain.model.editor.SurveyEditorDerivation
import org.groundplatform.v2.app.domain.model.editor.SurveyEditorDraft

/**
 * Fills a [LocalStore] with the sample data.
 *
 * This is the only code allowed to read the hardcoded sample datasets (`Sample*Data`); the rest of
 * the app reads everything from the store. Data for every sample survey is written up front, so
 * switching surveys never reloads (and never discards edits).
 */
class SampleDataSeeder(private val store: LocalStore) {
  /** Seeds the store unless it already holds the current sample data version. */
  suspend fun seedIfNeeded() {
    store.transaction { if (preferences().seedVersion != SEED_VERSION) writeSampleData() }
  }

  /** Deletes everything in the store and writes the sample data again. */
  suspend fun reset() {
    store.transaction {
      store.clear()
      writeSampleData()
    }
  }

  companion object {
    /** Bump when the sample data changes shape, so existing stores are reseeded. */
    const val SEED_VERSION: Int = 16

    /** Survey opened by default on first launch. */
    const val DEFAULT_ACTIVE_SURVEY_ID: String = "survey-kenya-coffee"

    /** Writes all sample data inside an existing transaction. */
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
        putSubmissionGeometries(
          surveyId,
          SampleSubmissionsData.submissionGeometriesForSurvey(surveyId),
        )
        putSurveyConfig(
          surveyId,
          SurveyConfig(
            primaryForm = SampleSurveysData.exampleFormForSurveyId(surveyId).formDefinition,
            formsById =
              forms.associate { it.id to SampleSurveysData.builtInFallbackFormForForm(it) },
          ),
        )
      }
      // Survey editor drafts are derived from the survey data above; only the two surveys with
      // extras that cannot be derived (sharing people, survey area, Data tables) store one.
      putSurveyEditorDraft(
        DEFAULT_ACTIVE_SURVEY_ID,
        SampleSurveyEditorData.kenyaCoffeeDraft(deriveEditorDraft(DEFAULT_ACTIVE_SURVEY_ID)),
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
          activeSurveyId = DEFAULT_ACTIVE_SURVEY_ID,
          userSettings =
            UserSettings(
              language = "en",
              measurementUnits = MeasurementUnitSystem.METRIC,
              shouldUploadPhotosOnWifiOnly = false,
            ),
          uploadedMediaCacheSizeLabel = "38.4 MB",
          uploadedMediaFileCount = 27,
          seedVersion = SEED_VERSION,
        )
      )
    }

    /** Derives the Survey editor draft for [surveyId] from the data already written. */
    private fun LocalStoreTransaction.deriveEditorDraft(surveyId: String): SurveyEditorDraft {
      val forms = SampleSurveysData.formsForSurvey(surveyId)
      val formsById = forms.associate {
        it.id to SampleSurveysData.builtInFallbackFormForForm(it)
      }
      return SurveyEditorDerivation.derive(
        surveyId = surveyId,
        survey = SampleSurveysData.defaultSampleSurveys().firstOrNull { it.id == surveyId },
        forms = forms,
        formDefinition = { formsById[it.id] },
        mapLayers = SampleMapLayersData.mapLayersForSurvey(surveyId),
        entities = SampleEntitiesData.entitiesForSurvey(surveyId),
      )
    }
  }
}
