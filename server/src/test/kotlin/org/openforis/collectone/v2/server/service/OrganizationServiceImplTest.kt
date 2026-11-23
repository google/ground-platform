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
import openforis.collectone.v1.survey.ListOrganizationsRequest
import openforis.collectone.v1.survey.MutateOrganizationRequest
import openforis.collectone.v1.survey.MutateSurveyRequest
import openforis.collectone.v1.survey.Organization
import openforis.collectone.v1.survey.OrganizationMutation
import openforis.collectone.v1.survey.SearchOrganizationsRequest
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyMutation
import openforis.collectone.v1.userdata.UserDataSeed
import org.openforis.collectone.v2.server.service.ServiceTestSeed.ORGANIZATION
import org.openforis.collectone.v2.server.service.ServiceTestSeed.OTHER_ORGANIZATION
import org.openforis.collectone.v2.server.service.ServiceTestSeed.SURVEY
import org.openforis.collectone.v2.server.service.ServiceTestSeed.UNLISTED_ORGANIZATION

/**
 * Behavior of the organization RPCs ([OrganizationServiceImpl]) on [ServiceTestSeed]: full syncs,
 * deltas with opaque sync tokens, and the organization directory.
 */
open class OrganizationServiceImplTest {
  /** A backend loaded with [seed]; overridden to test other stores. */
  protected open fun newBackend(seed: UserDataSeed): UserDataBackend = inMemoryBackend(seed)

  private val backend by lazy { newBackend(ServiceTestSeed.seed) }

  private suspend fun describeOrganization(description: String) =
    backend.MutateOrganization(
      MutateOrganizationRequest(
        organization_id = ORGANIZATION,
        mutations = listOf(OrganizationMutation(upsert = Organization(description = description))),
      )
    )

  @Test
  fun listOrganizations_fullSync_returnsUndeletedOrganizationsInCommitOrder() = runTest {
    val response = backend.ListOrganizations(ListOrganizationsRequest())

    assertEquals(
      listOf(ORGANIZATION, OTHER_ORGANIZATION, UNLISTED_ORGANIZATION),
      response.organizations.map { it.organization_id },
    )
  }

  @Test
  fun listOrganizations_fullSync_returnsASyncTokenAndNoPageToken() = runTest {
    val response = backend.ListOrganizations(ListOrganizationsRequest())

    assertTrue(response.next_sync_token.isNotEmpty())
    assertEquals("", response.next_page_token)
  }

  @Test
  fun mutateOrganization_commitsAfterTheCurrentWatermark() = runTest {
    val watermark = backend.currentWatermark()

    val response = describeOrganization("Updated description")

    assertTrue(response.commit_timestamp!! > watermark)
  }

  @Test
  fun listOrganizations_delta_returnsOrganizationsChangedSinceTheToken() = runTest {
    val token = backend.ListOrganizations(ListOrganizationsRequest()).next_sync_token
    describeOrganization("Updated description")

    val delta = backend.ListOrganizations(ListOrganizationsRequest(sync_token = token))

    assertEquals(listOf("Updated description"), delta.organizations.map { it.description })
  }

  @Test
  fun listOrganizations_delta_ignoresChangesToOtherRecordTypes() = runTest {
    val token = backend.ListOrganizations(ListOrganizationsRequest()).next_sync_token
    backend.MutateSurvey(
      MutateSurveyRequest(
        survey_id = SURVEY,
        mutations = listOf(SurveyMutation(upsert = SurveyDef(title = "Retitled"))),
      )
    )

    val delta = backend.ListOrganizations(ListOrganizationsRequest(sync_token = token))

    assertEquals(emptyList(), delta.organizations)
  }

  @Test
  fun listOrganizations_deltaFromTheLatestToken_isEmpty() = runTest {
    val token = backend.ListOrganizations(ListOrganizationsRequest()).next_sync_token
    describeOrganization("Updated description")
    val latestToken =
      backend.ListOrganizations(ListOrganizationsRequest(sync_token = token)).next_sync_token

    val idle = backend.ListOrganizations(ListOrganizationsRequest(sync_token = latestToken))

    assertEquals(emptyList(), idle.organizations)
  }

  @Test
  fun listOrganizations_malformedSyncToken_failsWithInvalidArgument() = runTest {
    val error =
      assertFailsWith<StatusException> {
        backend.ListOrganizations(ListOrganizationsRequest(sync_token = "not a token"))
      }

    assertEquals(Status.Code.INVALID_ARGUMENT, error.status.code)
  }

  @Test
  fun searchOrganizations_returnsListedUndeletedOrganizationsByName() = runTest {
    val directory = backend.SearchOrganizations(SearchOrganizationsRequest()).organizations

    // "Agroforestry Cooperative" sorts before "Forest Service"; the unlisted and deleted
    // organizations are left out.
    assertEquals(listOf(OTHER_ORGANIZATION, ORGANIZATION), directory.map { it.organization_id })
  }
}
