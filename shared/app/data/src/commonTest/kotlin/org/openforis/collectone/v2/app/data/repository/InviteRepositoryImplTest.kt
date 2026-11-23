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
import openforis.collectone.v1.survey.AcceptSurveyInviteRequest
import openforis.collectone.v1.survey.AcceptSurveyInviteResponse
import openforis.collectone.v1.survey.JoinOrganizationRequest
import openforis.collectone.v1.survey.JoinOrganizationResponse
import openforis.collectone.v1.survey.OrganizationServiceServer
import openforis.collectone.v1.survey.SurveyServiceServer
import org.openforis.collectone.v2.app.client.grpc.CollectOneGrpcClient
import org.openforis.collectone.v2.app.client.grpc.GrpcWebException
import org.openforis.collectone.v2.app.client.grpc.GrpcWebTransport
import org.openforis.collectone.v2.app.data.datasource.local.store.InMemoryLocalStore
import org.openforis.collectone.v2.app.data.datasource.remote.SeedUserDataService
import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.domain.error.DomainResult
import org.openforis.collectone.v2.app.domain.repository.InviteRedemption
import org.openforis.collectone.v2.app.domain.repository.Invitee

class InviteRepositoryImplTest {
  private val seed = SeedUserDataService()
  private val invitee =
    Invitee(
      userId = "uid-ima",
      email = "ima@example.org",
      displayName = "Ima User",
      photoUrl = null,
    )

  private fun repositoryOver(
    surveyService: SurveyServiceServer = seed,
    organizationService: OrganizationServiceServer = seed,
  ) =
    InviteRepositoryImpl(
      localStore = InMemoryLocalStore(),
      grpcClient =
        CollectOneGrpcClient(
          surveyService = surveyService,
          organizationService = organizationService,
          libraryService = seed,
          dataService = seed,
        ),
      syncCoordinator = null,
    )

  private val acceptingSurveyService =
    object : SurveyServiceServer by seed {
      override suspend fun AcceptSurveyInvite(request: AcceptSurveyInviteRequest) =
        AcceptSurveyInviteResponse()
    }

  private fun surveyServiceFailingWith(status: Int) =
    object : SurveyServiceServer by seed {
      override suspend fun AcceptSurveyInvite(
        request: AcceptSurveyInviteRequest
      ): AcceptSurveyInviteResponse = throw GrpcWebException(status, "/Accept", "detail")
    }

  private val joiningOrganizationService =
    object : OrganizationServiceServer by seed {
      override suspend fun JoinOrganization(request: JoinOrganizationRequest) =
        JoinOrganizationResponse()
    }

  private fun organizationServiceFailingWith(status: Int) =
    object : OrganizationServiceServer by seed {
      override suspend fun JoinOrganization(
        request: JoinOrganizationRequest
      ): JoinOrganizationResponse = throw GrpcWebException(status, "/Join", "detail")
    }

  @Test
  fun acceptSurveyInvite_acceptedByTheService() = runTest {
    val repository = repositoryOver(surveyService = acceptingSurveyService)

    val result = repository.acceptSurveyInvite("survey-1", "token", invitee)

    assertEquals(DomainResult.Success(InviteRedemption.ACCEPTED_BY_SERVICE), result)
  }

  @Test
  fun acceptSurveyInvite_withAnExpiredToken_failsWithPermissionDenied() = runTest {
    val repository =
      repositoryOver(surveyService = surveyServiceFailingWith(GrpcWebTransport.PERMISSION_DENIED))

    val result = repository.acceptSurveyInvite("survey-1", "token", invitee)

    assertEquals(DomainResult.Failure(DomainError.PermissionDenied), result)
  }

  @Test
  fun acceptSurveyInvite_beyondTheMembershipQuota_failsWithQuotaExceeded() = runTest {
    val repository =
      repositoryOver(surveyService = surveyServiceFailingWith(GrpcWebTransport.RESOURCE_EXHAUSTED))

    val result = repository.acceptSurveyInvite("survey-1", "token", invitee)

    assertEquals(DomainResult.Failure(DomainError.QuotaExceeded), result)
  }

  @Test
  fun joinOrganization_acceptedByTheService() = runTest {
    val repository = repositoryOver(organizationService = joiningOrganizationService)

    val result = repository.joinOrganization("org-1", "token", invitee)

    assertEquals(DomainResult.Success(InviteRedemption.ACCEPTED_BY_SERVICE), result)
  }

  @Test
  fun joinOrganization_offline_failsWithANetworkError() = runTest {
    val repository =
      repositoryOver(
        organizationService = organizationServiceFailingWith(GrpcWebTransport.UNAVAILABLE)
      )

    val result = repository.joinOrganization("org-1", "token", invitee)

    assertEquals(DomainResult.Failure(DomainError.Network), result)
  }

  @Test
  fun withoutAUserDataService_invitesAreLeftToTheCaller() = runTest {
    val repository =
      InviteRepositoryImpl(
        localStore = InMemoryLocalStore(),
        grpcClient = null,
        syncCoordinator = null,
      )

    assertEquals(
      DomainResult.Success(InviteRedemption.NO_SERVICE),
      repository.acceptSurveyInvite("survey-1", "token", invitee),
    )
    assertEquals(
      DomainResult.Success(InviteRedemption.NO_SERVICE),
      repository.joinOrganization("org-1", "token", invitee),
    )
  }
}
