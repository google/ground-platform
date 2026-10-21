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
package org.openforis.collectone.v2.server.userdata

import groundplatform.v2.data.AcquirePlotLockRequest
import groundplatform.v2.data.ListEntitiesRequest
import groundplatform.v2.survey.AclEntry
import groundplatform.v2.survey.GetOrganizationRequest
import groundplatform.v2.survey.GetSurveyAclRequest
import groundplatform.v2.survey.ListOrganizationsRequest
import groundplatform.v2.survey.ListSurveyOverviewsRequest
import groundplatform.v2.survey.MutateOrganizationRequest
import groundplatform.v2.survey.MutateSurveyRequest
import groundplatform.v2.survey.OrganizationMutation
import groundplatform.v2.survey.Role
import groundplatform.v2.survey.SurveyMutation
import io.grpc.Status
import io.grpc.StatusException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Caller registration, invite binding, and access epochs
 * (`docs/technical/backend/spanner-schema.md`, "Authentication" and "Access Changes"), on the
 * bundled sample seed.
 */
open class AccessEpochTest {
  /** A backend loaded with the bundled sample seed; overridden to test other stores. */
  protected open fun newBackend(): UserDataBackend = sampleBackend()

  private val backend by lazy { newBackend() }

  /** Runs [block] as [caller], registering it first as the server does before each RPC. */
  private suspend fun <T> asCaller(caller: Caller, block: suspend () -> T): T =
    withContext(CallerElement(caller)) {
      backend.registerCaller(caller)
      block()
    }

  private suspend fun aclEntry(email: String): AclEntry? =
    backend.GetSurveyAcl(GetSurveyAclRequest(survey_id = KENYA)).acl?.entries?.firstOrNull {
      it.email == email
    }

  @Test
  fun verifiedSignIn_bindsPendingSurveyAndOrganizationInvites() =
    runBlocking<Unit> {
      asCaller(caller("uid-collector-two", COLLECTOR_TWO)) {}
      asCaller(caller("uid-wanjiku", WANJIKU)) {}

      assertEquals("uid-collector-two", aclEntry(COLLECTOR_TWO)?.user_id)
      val members =
        backend
          .GetOrganization(GetOrganizationRequest(organization_id = KENYA_FOREST_SERVICE))
          .organization
          ?.members
          .orEmpty()
      assertEquals("uid-wanjiku", members.single { it.email == WANJIKU }.user_id)
    }

  @Test
  fun unverifiedSignIn_doesNotBindInvites() =
    runBlocking<Unit> {
      asCaller(caller("uid-collector-two", COLLECTOR_TWO, verified = false)) {}

      assertEquals("", aclEntry(COLLECTOR_TWO)?.user_id)
    }

  @Test
  fun aclChanges_invalidateOnlyTheAffectedUsersOverviewTokens() =
    runBlocking<Unit> {
      val collector = caller("uid-collector-one", COLLECTOR_ONE)
      val fieldLead = caller("uid-field-lead", FIELD_LEAD)
      val collectorToken =
        asCaller(collector) { backend.ListSurveyOverviews(ListSurveyOverviewsRequest()) }
          .next_sync_token
      val fieldLeadToken =
        asCaller(fieldLead) { backend.ListSurveyOverviews(ListSurveyOverviewsRequest()) }
          .next_sync_token

      asCaller(Authenticator.DEFAULT_DEV_CALLER) {
        val acl = backend.GetSurveyAcl(GetSurveyAclRequest(survey_id = KENYA)).acl!!
        backend.MutateSurvey(
          MutateSurveyRequest(
            survey_id = KENYA,
            mutations =
              listOf(
                SurveyMutation(
                  acl_upsert =
                    acl.copy(entries = acl.entries.filterNot { it.email == COLLECTOR_ONE })
                )
              ),
          )
        )
      }

      val error =
        assertFailsWith<StatusException> {
          asCaller(collector) {
            backend.ListSurveyOverviews(ListSurveyOverviewsRequest(sync_token = collectorToken))
          }
        }
      assertEquals(Status.Code.FAILED_PRECONDITION, error.status.code)
      asCaller(fieldLead) {
        backend.ListSurveyOverviews(ListSurveyOverviewsRequest(sync_token = fieldLeadToken))
      }
    }

  @Test
  fun membershipChanges_invalidateOrganizationListTokens() =
    runBlocking<Unit> {
      val collector = caller("uid-collector-one", COLLECTOR_ONE)
      val token =
        asCaller(collector) { backend.ListOrganizations(ListOrganizationsRequest()) }
          .next_sync_token

      asCaller(Authenticator.DEFAULT_DEV_CALLER) {
        val org =
          backend
            .GetOrganization(GetOrganizationRequest(organization_id = KENYA_FOREST_SERVICE))
            .organization!!
        backend.MutateOrganization(
          MutateOrganizationRequest(
            organization_id = KENYA_FOREST_SERVICE,
            mutations =
              listOf(
                OrganizationMutation(
                  upsert = org.copy(members = org.members.filterNot { it.email == COLLECTOR_ONE })
                )
              ),
          )
        )
      }

      val error =
        assertFailsWith<StatusException> {
          asCaller(collector) {
            backend.ListOrganizations(ListOrganizationsRequest(sync_token = token))
          }
        }
      assertEquals(Status.Code.FAILED_PRECONDITION, error.status.code)
    }

  @Test
  fun addingAnEntryForAKnownUser_bindsIt_andInvalidatesTheirTokens() =
    runBlocking<Unit> {
      val ana = caller("uid-ana", "ana@example.org")
      val token =
        asCaller(ana) { backend.ListSurveyOverviews(ListSurveyOverviewsRequest()) }.next_sync_token

      asCaller(Authenticator.DEFAULT_DEV_CALLER) {
        val acl = backend.GetSurveyAcl(GetSurveyAclRequest(survey_id = KENYA)).acl!!
        backend.MutateSurvey(
          MutateSurveyRequest(
            survey_id = KENYA,
            mutations =
              listOf(
                SurveyMutation(
                  acl_upsert =
                    acl.copy(
                      entries =
                        acl.entries +
                          AclEntry(email = "Ana@Example.org", role = Role.DATA_COLLECTOR)
                    )
                )
              ),
          )
        )
      }

      assertEquals("uid-ana", aclEntry("Ana@Example.org")?.user_id)
      val error =
        assertFailsWith<StatusException> {
          asCaller(ana) {
            backend.ListSurveyOverviews(ListSurveyOverviewsRequest(sync_token = token))
          }
        }
      assertEquals(Status.Code.FAILED_PRECONDITION, error.status.code)
    }

  @Test
  fun authenticatedCallers_overrideRequestUserIds() =
    runBlocking<Unit> {
      val entities = backend.ListEntities(ListEntitiesRequest(survey_id = KENYA)).entities
      val entityId = entities.first().entity_id
      val other = entities.last().entity_id
      val collector = caller("uid-collector-one", COLLECTOR_ONE)

      val lock =
        asCaller(collector) {
          backend.AcquirePlotLock(
            AcquirePlotLockRequest(
              survey_id = KENYA,
              entity_id = entityId,
              user_id = "someone-else",
            )
          )
        }

      assertEquals("uid-collector-one", lock.lock?.locked_by_user_id)
      // The development fallback caller keeps the request's user ID, so fake clients keep working.
      val fallback =
        asCaller(Authenticator.DEFAULT_DEV_CALLER) {
          backend.AcquirePlotLock(
            AcquirePlotLockRequest(survey_id = KENYA, entity_id = other, user_id = "uid-fake")
          )
        }
      assertEquals("uid-fake", fallback.lock?.locked_by_user_id)
      assertTrue(other != entityId)
    }

  @Test
  fun switchingCallers_invalidatesPreviousCallersSyncTokens() =
    runBlocking<Unit> {
      val defaultToken =
        asCaller(Authenticator.DEFAULT_DEV_CALLER) {
            backend.ListSurveyOverviews(ListSurveyOverviewsRequest())
          }
          .next_sync_token
      val collector = caller("uid-ima-user", "ima.user@example.org")

      val error =
        assertFailsWith<StatusException> {
          asCaller(collector) {
            backend.ListSurveyOverviews(ListSurveyOverviewsRequest(sync_token = defaultToken))
          }
        }
      assertEquals(Status.Code.FAILED_PRECONDITION, error.status.code)
    }

  private fun caller(userId: String, email: String, verified: Boolean = true) =
    Caller(userId = userId, email = email, emailVerified = verified)

  companion object {
    const val KENYA = "00000033-6b50-51cd-ac94-baea2ea06d65"
    const val KENYA_FOREST_SERVICE = "00000013-31cc-5c95-90ea-379f624a5c72"
    const val COLLECTOR_ONE = "collector.one@example.org"
    const val COLLECTOR_TWO = "collector.two@example.org"
    const val FIELD_LEAD = "field.lead@example.org"
    const val WANJIKU = "wanjiku.m@example.org"
  }
}
