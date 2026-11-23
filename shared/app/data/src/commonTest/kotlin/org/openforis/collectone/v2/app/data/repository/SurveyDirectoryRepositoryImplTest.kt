/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.app.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import openforis.collectone.v1.survey.BatchGetSurveyOverviewsRequest
import openforis.collectone.v1.survey.BatchGetSurveyOverviewsResponse
import openforis.collectone.v1.survey.SearchPublicSurveysRequest
import openforis.collectone.v1.survey.SearchPublicSurveysResponse
import openforis.collectone.v1.survey.SurveyOverview
import openforis.collectone.v1.survey.SurveyOverviewResult
import openforis.collectone.v1.survey.SurveyServiceServer
import org.openforis.collectone.v2.app.client.grpc.CollectOneGrpcClient
import org.openforis.collectone.v2.app.client.grpc.GrpcWebException
import org.openforis.collectone.v2.app.client.grpc.GrpcWebTransport
import org.openforis.collectone.v2.app.data.datasource.local.store.InMemoryLocalStore
import org.openforis.collectone.v2.app.data.datasource.remote.SeedUserDataService
import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.domain.error.DomainResult
import org.openforis.collectone.v2.app.domain.error.getOrNull

class SurveyDirectoryRepositoryImplTest {
  private val seed = SeedUserDataService()
  private val overview = SurveyOverview(survey_id = "survey-1", title = "Public mangroves")

  private fun repositoryOver(surveyService: SurveyServiceServer) =
    SurveyDirectoryRepositoryImpl(
      store = InMemoryLocalStore(),
      grpcClient =
        CollectOneGrpcClient(
          surveyService = surveyService,
          organizationService = seed,
          libraryService = seed,
          dataService = seed,
        ),
    )

  /** A survey service whose directory lookups answer with [overview]. */
  private fun answering() =
    repositoryOver(
      object : SurveyServiceServer by seed {
        override suspend fun SearchPublicSurveys(request: SearchPublicSurveysRequest) =
          SearchPublicSurveysResponse(overviews = listOf(overview))

        override suspend fun BatchGetSurveyOverviews(request: BatchGetSurveyOverviewsRequest) =
          BatchGetSurveyOverviewsResponse(
            results = listOf(SurveyOverviewResult(overview = overview))
          )
      }
    )

  /** A survey service whose directory lookups fail with gRPC [status]. */
  private fun failingWith(status: Int) =
    repositoryOver(
      object : SurveyServiceServer by seed {
        override suspend fun SearchPublicSurveys(
          request: SearchPublicSurveysRequest
        ): SearchPublicSurveysResponse = throw GrpcWebException(status, "/Search", "detail")

        override suspend fun BatchGetSurveyOverviews(
          request: BatchGetSurveyOverviewsRequest
        ): BatchGetSurveyOverviewsResponse = throw GrpcWebException(status, "/BatchGet", "detail")
      }
    )

  @Test
  fun searchPublicSurveys_returnsTheMatches() = runTest {
    val result = answering().searchPublicSurveys("mangroves")

    assertEquals(listOf("survey-1"), result.getOrNull()?.map { it.id })
  }

  @Test
  fun searchPublicSurveys_offline_failsWithANetworkError() = runTest {
    val result = failingWith(GrpcWebTransport.UNAVAILABLE).searchPublicSurveys("mangroves")

    assertEquals(DomainResult.Failure(DomainError.Network), result)
  }

  @Test
  fun lookUpSurvey_returnsTheSurvey() = runTest {
    val result = answering().lookUpSurvey("survey-1")

    assertEquals("Public mangroves", result.getOrNull()?.title)
  }

  @Test
  fun lookUpSurvey_withoutAccess_failsWithPermissionDenied() = runTest {
    val result = failingWith(GrpcWebTransport.PERMISSION_DENIED).lookUpSurvey("survey-1")

    assertEquals(DomainResult.Failure(DomainError.PermissionDenied), result)
  }
}
