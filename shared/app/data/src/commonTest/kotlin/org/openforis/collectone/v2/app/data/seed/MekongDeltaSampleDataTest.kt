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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.seed.backend.BackendStaticEntitiesData
import org.openforis.collectone.v2.app.data.seed.backend.BackendStaticSubmissionsData
import org.openforis.collectone.v2.app.data.seed.backend.BackendStaticSurveyEditorData
import org.openforis.collectone.v2.app.data.seed.backend.BackendStaticSurveysData
import org.openforis.collectone.v2.map.LngLatBounds

/** The Mekong Delta survey's sample data lies inside its survey area in Cần Thơ. */
class MekongDeltaSampleDataTest {
  private val surveyId = BackendStaticSurveyEditorData.ORGANIZATION_SHARED_SURVEY_ID
  private val area =
    LngLatBounds.of(BackendStaticSurveyEditorData.mekongDeltaSurveyArea().parts.flatten())

  @Test
  fun mapFeatures_lieInsideTheSurveyArea() {
    val entities = BackendStaticEntitiesData.entitiesForSurvey(surveyId)

    assertTrue(entities.isNotEmpty())
    for (entity in entities) {
      val outside = entity.geometry?.coordinates.orEmpty().filterNot { it in area }
      assertEquals(emptyList(), outside, entity.label)
    }
  }

  @Test
  fun submissions_areRecordedInsideTheSurveyArea() {
    val submissions =
      BackendStaticEntitiesData.entitiesForSurvey(surveyId).flatMap { it.submissions } +
        BackendStaticSubmissionsData.standaloneSubmissionsForSurvey(surveyId)
    val geometries = BackendStaticSubmissionsData.submissionGeometriesForSurvey(surveyId)

    assertTrue(submissions.any { it.location != null })
    for (submission in submissions) {
      submission.location?.let { assertTrue(it in area, submission.id) }
    }
    for (geometry in geometries) {
      assertTrue(geometry.geometry.coordinates.all { it in area }, geometry.id)
    }
  }

  @Test
  fun recordedCoordinateAnswers_nameTheRelocatedPosition() {
    val answers =
      BackendStaticSubmissionsData.standaloneSubmissionsForSurvey(surveyId)
        .flatMap { it.fields }
        .filter { it.questionName == "gps_observation_point" }

    assertTrue(answers.isNotEmpty())
    assertTrue(answers.all { it.answerValue.startsWith("9.") }, answers.toString())
  }

  @Test
  fun theDefaultSurvey_keepsItsSampleDataInKenya() {
    val kenya = BackendStaticEntitiesData.defaultGeospatialEntities()

    assertTrue(kenya.all { it.geometry?.coordinates.orEmpty().none { point -> point in area } })
  }

  @Test
  fun allSampleSurveys_haveMapFeaturesInsideTheirSurveyAreasAndPopulatedThumbnails() {
    for (survey in BackendStaticSurveysData.defaultSampleSurveys()) {
      val surveyArea = checkNotNull(survey.surveyArea) { "Missing surveyArea on ${survey.id}" }
      val bounds = LngLatBounds.of(surveyArea.parts.flatten())
      val entities = BackendStaticEntitiesData.entitiesForSurvey(survey.id)
      assertTrue(entities.isNotEmpty(), "Expected entities for ${survey.id}")
      for (entity in entities) {
        val outside = entity.geometry?.coordinates.orEmpty().filterNot { it in bounds }
        assertEquals(emptyList(), outside, "${survey.id} -> ${entity.label}")
      }
      assertEquals(
        entities.size,
        survey.thumbnail.features.size,
        "Expected all ${entities.size} features in thumbnail for ${survey.id} (${survey.title})",
      )
    }
  }
}
