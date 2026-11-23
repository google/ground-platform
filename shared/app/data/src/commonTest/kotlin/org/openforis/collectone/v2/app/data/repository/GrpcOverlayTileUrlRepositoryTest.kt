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

import com.squareup.wire.durationOfSeconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import openforis.collectone.v1.survey.GeoDataServiceServer
import openforis.collectone.v1.survey.GetOverlayTileUrlRequest
import openforis.collectone.v1.survey.GetOverlayTileUrlResponse
import org.openforis.collectone.v2.app.client.grpc.CollectOneGrpcClient
import org.openforis.collectone.v2.app.client.grpc.GrpcWebException
import org.openforis.collectone.v2.app.client.grpc.GrpcWebTransport
import org.openforis.collectone.v2.app.data.datasource.remote.SeedUserDataService
import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.domain.error.DomainResult
import org.openforis.collectone.v2.app.domain.error.getOrNull
import org.openforis.collectone.v2.app.domain.repository.OverlayTileUrl

class GrpcOverlayTileUrlRepositoryTest {
  private val seed = SeedUserDataService()
  private val requests = mutableListOf<GetOverlayTileUrlRequest>()

  private fun repositoryAnswering(response: GetOverlayTileUrlResponse) =
    GrpcOverlayTileUrlRepository(
      CollectOneGrpcClient(
        surveyService = seed,
        organizationService = seed,
        libraryService = seed,
        dataService = seed,
        geoDataService =
          object : GeoDataServiceServer by seed {
            override suspend fun GetOverlayTileUrl(
              request: GetOverlayTileUrlRequest
            ): GetOverlayTileUrlResponse {
              requests += request
              return response
            }
          },
      )
    )

  @Test
  fun resolveAsksForTheSurveysImagerySource() = runTest {
    val repository = repositoryAnswering(GetOverlayTileUrlResponse(url_template = "t/{z}/{x}/{y}"))

    repository.resolve(surveyId = "survey-1", imagerySourceId = "source-1")

    assertEquals(
      listOf(GetOverlayTileUrlRequest(survey_id = "survey-1", imagery_source_id = "source-1")),
      requests,
    )
  }

  @Test
  fun resolveReturnsTheServersUrlAndTtl() = runTest {
    val repository =
      repositoryAnswering(
        GetOverlayTileUrlResponse(url_template = "t/{z}/{x}/{y}", ttl = durationOfSeconds(600L, 0L))
      )

    val tileUrl = repository.resolve(surveyId = "survey-1", imagerySourceId = "source-1")

    assertEquals(
      DomainResult.Success(OverlayTileUrl(urlTemplate = "t/{z}/{x}/{y}", ttlSeconds = 600L)),
      tileUrl,
    )
  }

  @Test
  fun resolveFallsBackToTheDefaultTtlWhenTheServerGivesNone() = runTest {
    val repository = repositoryAnswering(GetOverlayTileUrlResponse(url_template = "t/{z}/{x}/{y}"))

    val tileUrl = repository.resolve(surveyId = "survey-1", imagerySourceId = "source-1")

    assertEquals(GrpcOverlayTileUrlRepository.DEFAULT_TTL_SECONDS, tileUrl.getOrNull()?.ttlSeconds)
  }

  @Test
  fun resolveWithoutAccessToTheSurvey_failsWithPermissionDenied() = runTest {
    val repository =
      GrpcOverlayTileUrlRepository(
        CollectOneGrpcClient(
          surveyService = seed,
          organizationService = seed,
          libraryService = seed,
          dataService = seed,
          geoDataService =
            object : GeoDataServiceServer by seed {
              override suspend fun GetOverlayTileUrl(
                request: GetOverlayTileUrlRequest
              ): GetOverlayTileUrlResponse =
                throw GrpcWebException(GrpcWebTransport.PERMISSION_DENIED, "/Get", "no access")
            },
        )
      )

    val tileUrl = repository.resolve(surveyId = "survey-1", imagerySourceId = "source-1")

    assertEquals(DomainResult.Failure(DomainError.PermissionDenied), tileUrl)
  }
}
