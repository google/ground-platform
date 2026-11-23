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
package org.openforis.collectone.v2.server.service

import io.grpc.Status
import io.grpc.StatusException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import openforis.collectone.v1.data.ListEntitiesRequest
import openforis.collectone.v1.survey.GetSurveyRequest
import openforis.collectone.v1.survey.ListOrganizationsRequest
import openforis.collectone.v1.survey.MapConfig
import openforis.collectone.v1.survey.MutateSurveyRequest
import openforis.collectone.v1.survey.SurveyArea
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyMutation
import openforis.collectone.v1.userdata.SurveySeed
import openforis.collectone.v1.userdata.UserDataSeed
import org.openforis.collectone.v2.core.geo.GeometryProtos
import org.openforis.collectone.v2.server.service.ServiceTestSeed.OTHER_SURVEY
import org.openforis.collectone.v2.server.service.ServiceTestSeed.SURVEY
import org.openforis.collectone.v2.server.service.ServiceTestSeed.SURVEY_TITLE

/**
 * Behavior of [UserDataBackend] itself on [ServiceTestSeed]: resetting to the seed, refusing sync
 * tokens from another data epoch, and backfilling the seed's survey areas. The RPCs it delegates
 * are covered by the per-service tests ([SurveyServiceImplTest], [OrganizationServiceImplTest],
 * [LibraryServiceImplTest], [DataServiceImplTest]).
 */
open class UserDataBackendTest {
  /** A backend loaded with [seed]; overridden to test other stores. */
  protected open fun newBackend(seed: UserDataSeed): UserDataBackend = inMemoryBackend(seed)

  private val backend by lazy { newBackend(ServiceTestSeed.seed) }

  @Test
  fun reset_restoresTheSeed() = runTest {
    backend.MutateSurvey(
      MutateSurveyRequest(
        survey_id = SURVEY,
        mutations = listOf(SurveyMutation(upsert = SurveyDef(title = "Changed"))),
      )
    )

    backend.reset()

    assertEquals(
      SURVEY_TITLE,
      backend.GetSurvey(GetSurveyRequest(survey_id = SURVEY)).survey?.title,
    )
  }

  @Test
  fun syncTokenFromAnotherBackend_failsWithFailedPrecondition() = runTest {
    val token = backend.ListEntities(ListEntitiesRequest(survey_id = SURVEY)).next_sync_token
    val restarted = newBackend(ServiceTestSeed.seed)

    val error =
      assertFailsWith<StatusException> {
        restarted.ListEntities(ListEntitiesRequest(survey_id = SURVEY, sync_token = token))
      }

    assertEquals(Status.Code.FAILED_PRECONDITION, error.status.code)
  }

  @Test
  fun syncTokenFromBeforeReset_failsWithFailedPrecondition() = runTest {
    val token = backend.ListOrganizations(ListOrganizationsRequest()).next_sync_token
    backend.reset()

    val error =
      assertFailsWith<StatusException> {
        backend.ListOrganizations(ListOrganizationsRequest(sync_token = token))
      }

    assertEquals(Status.Code.FAILED_PRECONDITION, error.status.code)
  }

  @Test
  fun backfillSeedSurveyAreas_setsTheSeedAreaOnStoredSurveysWithoutOne() = runTest {
    val versionBefore = savedSurvey(OTHER_SURVEY).record_version

    val backfilled = backend.backfillSeedSurveyAreas(seedWithAreas(OTHER_SURVEY to SEED_AREA))

    assertEquals(listOf(OTHER_SURVEY), backfilled)
    val saved = savedSurvey(OTHER_SURVEY)
    assertEquals(SEED_AREA, saved.map_config?.survey_area)
    assertTrue(saved.record_version > versionBefore, "The record version increases")
  }

  @Test
  fun backfillSeedSurveyAreas_alsoSetsTheAreaOnTheWorkingCopy() = runTest {
    backend.backfillSeedSurveyAreas(seedWithAreas(SURVEY to SEED_AREA))

    val workingCopy =
      backend
        .GetSurvey(
          GetSurveyRequest(survey_id = SURVEY, working_copy = GetSurveyRequest.WorkingCopy())
        )
        .survey
    assertEquals(SEED_AREA, workingCopy?.map_config?.survey_area)
  }

  @Test
  fun backfillSeedSurveyAreas_runAgain_changesNothing() = runTest {
    val seed = seedWithAreas(OTHER_SURVEY to SEED_AREA)
    backend.backfillSeedSurveyAreas(seed)
    val afterFirstRun = savedSurvey(OTHER_SURVEY)

    val backfilled = backend.backfillSeedSurveyAreas(seed)

    assertEquals(emptyList(), backfilled)
    assertEquals(afterFirstRun, savedSurvey(OTHER_SURVEY))
  }

  @Test
  fun backfillSeedSurveyAreas_keepsAnAreaAlreadySet() = runTest {
    backend.MutateSurvey(
      MutateSurveyRequest(
        survey_id = OTHER_SURVEY,
        mutations =
          listOf(
            SurveyMutation(upsert = SurveyDef(map_config = MapConfig(survey_area = CHOSEN_AREA)))
          ),
      )
    )

    val backfilled = backend.backfillSeedSurveyAreas(seedWithAreas(OTHER_SURVEY to SEED_AREA))

    assertEquals(emptyList(), backfilled)
    assertEquals(CHOSEN_AREA, savedSurvey(OTHER_SURVEY).map_config?.survey_area)
  }

  @Test
  fun backfillSeedSurveyAreas_ignoresSeedSurveysThatAreNotStored() = runTest {
    val backfilled =
      backend.backfillSeedSurveyAreas(seedWithAreas(testId("survey-missing") to SEED_AREA))

    assertEquals(emptyList(), backfilled)
  }

  private suspend fun savedSurvey(surveyId: String): SurveyDef =
    requireNotNull(backend.GetSurvey(GetSurveyRequest(survey_id = surveyId)).survey)

  private fun seedWithAreas(vararg areas: Pair<String, SurveyArea>) =
    UserDataSeed(
      surveys =
        areas.map { (surveyId, area) ->
          SurveySeed(
            survey = SurveyDef(survey_id = surveyId, map_config = MapConfig(survey_area = area))
          )
        }
    )

  private companion object {
    val SEED_AREA = squareArea("Seed area", south = -0.44, west = 36.92)
    val CHOSEN_AREA = squareArea("Chosen area", south = 1.0, west = 30.0)

    fun squareArea(name: String, south: Double, west: Double) =
      SurveyArea(
        name = name,
        boundary =
          GeometryProtos.multiPolygon(
            listOf(
              listOf(
                listOf(
                  GeometryProtos.position(latitude = south, longitude = west),
                  GeometryProtos.position(latitude = south + 0.01, longitude = west),
                  GeometryProtos.position(latitude = south + 0.01, longitude = west + 0.01),
                  GeometryProtos.position(latitude = south, longitude = west + 0.01),
                )
              )
            )
          ),
      )
  }
}
