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
package org.groundplatform.v2.server.userdata

import groundplatform.v2.data.DataMutation
import groundplatform.v2.data.EntityFieldPatch
import groundplatform.v2.data.EntityMutation
import groundplatform.v2.data.EntityPatch
import groundplatform.v2.data.EntityRecord
import groundplatform.v2.data.ListEntitiesRequest
import groundplatform.v2.data.MutateDataRequest
import groundplatform.v2.survey.BatchGetSurveyOverviewsRequest
import groundplatform.v2.survey.GetSurveyAclRequest
import groundplatform.v2.survey.GetSurveyRequest
import groundplatform.v2.survey.ListOrganizationsRequest
import groundplatform.v2.survey.ListSurveyOverviewsRequest
import groundplatform.v2.survey.MutateOrganizationRequest
import groundplatform.v2.survey.MutateSurveyRequest
import groundplatform.v2.survey.OrganizationMutation
import groundplatform.v2.survey.SurveyFieldPatch
import groundplatform.v2.survey.SurveyMutation
import groundplatform.v2.survey.SurveyState
import io.grpc.Status
import io.grpc.StatusException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Per-caller authorization (`docs/technical/backend/spanner-schema.md`, "Sharing Policies and
 * Access" and "Create vs. Update Authorization"), on the bundled sample seed.
 */
open class AuthorizationTest {
  /** A backend loaded with the bundled sample seed; overridden to test other stores. */
  protected open fun newBackend(): UserDataBackend = UserDataBackend(loadSeed(null))

  private val backend by lazy { newBackend() }

  private suspend fun <T> asCaller(caller: Caller, block: suspend () -> T): T =
    withContext(CallerElement(caller)) {
      backend.registerCaller(caller)
      block()
    }

  private suspend fun assertDenied(caller: Caller, block: suspend () -> Unit) {
    val error = assertFailsWith<StatusException> { asCaller(caller) { block() } }
    assertEquals(Status.Code.PERMISSION_DENIED, error.status.code)
  }

  private suspend fun listedSurveys(caller: Caller): Set<String> =
    asCaller(caller) { backend.ListSurveyOverviews(ListSurveyOverviewsRequest()) }
      .overviews
      .map { it.survey_id }
      .toSet()

  @Test
  fun surveyReads_requireARole_andOnlyOrganizersSeeCollaborators() =
    runBlocking<Unit> {
      assertDenied(OUTSIDER) { backend.GetSurvey(GetSurveyRequest(survey_id = KENYA)) }

      val asCollector =
        asCaller(COLLECTOR) { backend.GetSurvey(GetSurveyRequest(survey_id = KENYA)) }
      assertEquals(KENYA, asCollector.survey?.survey_id)
      assertTrue(asCollector.acl?.entries.orEmpty().isEmpty())
      val asOrganizer =
        asCaller(FIELD_LEAD) { backend.GetSurveyAcl(GetSurveyAclRequest(survey_id = KENYA)) }
      assertTrue(asOrganizer.acl?.entries.orEmpty().isNotEmpty())
    }

  @Test
  fun linkAndPublicSurveys_areReadable_butNotListed() =
    runBlocking<Unit> {
      asCaller(OUTSIDER) {
        assertEquals(LINK, backend.GetSurvey(GetSurveyRequest(survey_id = LINK)).survey?.survey_id)
        assertEquals(
          PUBLIC,
          backend.GetSurvey(GetSurveyRequest(survey_id = PUBLIC)).survey?.survey_id,
        )
      }

      assertTrue(listedSurveys(OUTSIDER).isEmpty())
    }

  @Test
  fun surveyLists_followOwnershipAclEntriesAndOrganizationSharing() =
    runBlocking<Unit> {
      assertEquals(setOf(KENYA), listedSurveys(COLLECTOR))
      assertEquals(setOf(MEKONG), listedSurveys(MEKONG_MEMBER))
      assertEquals(6, listedSurveys(ADMIN).size)
    }

  @Test
  fun lostAccess_appearsAsATombstoneInTheDelta() =
    runBlocking<Unit> {
      val token =
        asCaller(MEKONG_MEMBER) { backend.ListSurveyOverviews(ListSurveyOverviewsRequest()) }
          .next_sync_token
      asCaller(ADMIN) {
        backend.MutateSurvey(
          MutateSurveyRequest(
            survey_id = MEKONG,
            mutations =
              listOf(
                SurveyMutation(
                  update_access_rules =
                    groundplatform.v2.survey.AccessRulesPatch(
                      sharing_policy = groundplatform.v2.survey.SharingPolicy.RESTRICTED
                    )
                )
              ),
          )
        )
      }

      val delta =
        asCaller(MEKONG_MEMBER) {
          backend.ListSurveyOverviews(ListSurveyOverviewsRequest(sync_token = token))
        }
      assertEquals(
        listOf(MEKONG to SurveyState.DELETED),
        delta.overviews.map { it.survey_id to it.state },
      )
      assertDenied(MEKONG_MEMBER) { backend.GetSurvey(GetSurveyRequest(survey_id = MEKONG)) }
    }

  @Test
  fun surveyListDeltas_leaveOutSurveysTheCallerNeverHad() =
    runBlocking<Unit> {
      val token =
        asCaller(COLLECTOR) { backend.ListSurveyOverviews(ListSurveyOverviewsRequest()) }
          .next_sync_token
      asCaller(ADMIN) {
        backend.MutateSurvey(
          MutateSurveyRequest(
            survey_id = MEKONG,
            mutations =
              listOf(
                SurveyMutation(
                  update_field = SurveyFieldPatch(key = "title", string_value = "Renamed")
                )
              ),
          )
        )
      }

      val delta =
        asCaller(COLLECTOR) {
          backend.ListSurveyOverviews(ListSurveyOverviewsRequest(sync_token = token))
        }
      // Survey lists read only the caller's candidate surveys, so another survey's change doesn't
      // even show up as a tombstone.
      assertTrue(delta.overviews.none { it.survey_id == MEKONG })
    }

  @Test
  fun organizationLists_showOnlyTheCallersOrganizations() =
    runBlocking<Unit> {
      val outsiderOrgs =
        asCaller(OUTSIDER) { backend.ListOrganizations(ListOrganizationsRequest()) }.organizations
      assertTrue(outsiderOrgs.isEmpty())
      val collectorOrgs =
        asCaller(COLLECTOR) { backend.ListOrganizations(ListOrganizationsRequest()) }.organizations
      assertEquals(listOf(KENYA_FOREST_SERVICE), collectorOrgs.map { it.organization_id })
    }

  @Test
  fun writes_requireTheRightRole() =
    runBlocking<Unit> {
      val retitle =
        MutateSurveyRequest(
          survey_id = KENYA,
          mutations =
            listOf(
              SurveyMutation(update_field = SurveyFieldPatch(key = "title", string_value = "Mine"))
            ),
        )
      assertDenied(COLLECTOR) { backend.MutateSurvey(retitle) }
      asCaller(FIELD_LEAD) { backend.MutateSurvey(retitle) }

      assertDenied(VIEWER) { backend.MutateData(MutateDataRequest(KENYA, listOf(create("e-v")))) }
      assertDenied(COLLECTOR) {
        backend.MutateOrganization(
          MutateOrganizationRequest(
            organization_id = KENYA_FOREST_SERVICE,
            mutations =
              listOf(
                OrganizationMutation(
                  update_field =
                    groundplatform.v2.survey.OrganizationFieldPatch(
                      key = "name",
                      string_value = "Taken",
                    )
                )
              ),
          )
        )
      }
    }

  @Test
  fun newRecords_areAttributedToTheCaller() =
    runBlocking<Unit> {
      val created =
        asCaller(COLLECTOR) { backend.MutateData(MutateDataRequest(KENYA, listOf(create("e-c")))) }

      assertEquals("uid-collector-one", created.results.single().entity?.audit_info?.created_by)
    }

  @Test
  fun ownOnlyVisibility_limitsCollectorsToTheirOwnRecords() =
    runBlocking<Unit> {
      val seedEntity =
        asCaller(ADMIN) { backend.ListEntities(ListEntitiesRequest(survey_id = LINK)) }
          .entities
          .first()
      asCaller(OUTSIDER) {
        backend.MutateData(MutateDataRequest(LINK, listOf(create("e-own"))))
        val visible = backend.ListEntities(ListEntitiesRequest(survey_id = LINK)).entities
        assertEquals(listOf(testId("e-own")), visible.map { it.entity_id })
      }
      assertDenied(OUTSIDER) {
        backend.MutateData(
          MutateDataRequest(
            LINK,
            listOf(
              DataMutation(
                client_id = "c-outsider",
                client_seq = 2L,
                entity_mutation =
                  EntityMutation(
                    entity_id = seedEntity.entity_id,
                    update =
                      EntityPatch(
                        patches = listOf(EntityFieldPatch(key = "label", label_value = "Mine"))
                      ),
                  ),
              )
            ),
          )
        )
      }
      val all = asCaller(ADMIN) { backend.ListEntities(ListEntitiesRequest(survey_id = LINK)) }
      assertTrue(all.entities.size > 1)
    }

  @Test
  fun peerDataVisibilityFlips_invalidateFilteredCollectorsDataTokens() =
    runBlocking<Unit> {
      val collectorToken =
        asCaller(COLLECTOR) { backend.ListEntities(ListEntitiesRequest(survey_id = KENYA)) }
          .next_sync_token
      val organizerToken =
        asCaller(FIELD_LEAD) { backend.ListEntities(ListEntitiesRequest(survey_id = KENYA)) }
          .next_sync_token

      asCaller(FIELD_LEAD) {
        backend.MutateSurvey(
          MutateSurveyRequest(
            survey_id = KENYA,
            mutations =
              listOf(
                SurveyMutation(
                  update_access_rules =
                    groundplatform.v2.survey.AccessRulesPatch(
                      peer_data_visibility = groundplatform.v2.survey.PeerDataVisibility.OWN_ONLY
                    )
                )
              ),
          )
        )
      }

      val error =
        assertFailsWith<StatusException> {
          asCaller(COLLECTOR) {
            backend.ListEntities(
              ListEntitiesRequest(survey_id = KENYA, sync_token = collectorToken)
            )
          }
        }
      assertEquals(Status.Code.FAILED_PRECONDITION, error.status.code)
      // Organizers see every record either way, so their tokens stay valid.
      asCaller(FIELD_LEAD) {
        backend.ListEntities(ListEntitiesRequest(survey_id = KENYA, sync_token = organizerToken))
      }
    }

  @Test
  fun batchGet_reportsPermissionDeniedPerSurvey() =
    runBlocking<Unit> {
      val results =
        asCaller(OUTSIDER) {
            backend.BatchGetSurveyOverviews(
              BatchGetSurveyOverviewsRequest(survey_ids = listOf(KENYA, LINK))
            )
          }
          .results

      assertEquals(Status.Code.PERMISSION_DENIED.value(), results[0].error_code)
      assertEquals(LINK, results[1].overview?.survey_id)
    }

  private fun create(name: String) =
    DataMutation(
      client_id = "c-$name",
      client_seq = 1L,
      entity_mutation =
        EntityMutation(
          entity_id = testId(name),
          upsert = EntityRecord(entity_id = testId(name), label = name),
        ),
    )

  companion object {
    const val KENYA = "00000033-6b50-51cd-ac94-baea2ea06d65"
    const val PUBLIC = "00000036-1a1c-5c51-997f-28b67d813aa3"
    const val LINK = "00000031-ffc6-5c8e-934c-d14ec07533ad"
    const val MEKONG = "00000035-caf4-571e-b907-f506f92666ab"
    const val KENYA_FOREST_SERVICE = "00000013-31cc-5c95-90ea-379f624a5c72"

    private fun caller(userId: String, email: String) =
      Caller(userId = userId, email = email, emailVerified = true)

    val OUTSIDER = caller("uid-outsider", "outsider@example.org")
    val COLLECTOR = caller("uid-collector-one", "collector.one@example.org")
    val FIELD_LEAD = caller("uid-field-lead", "field.lead@example.org")
    val VIEWER = caller("uid-reviewer", "reviewer@example.org")
    val MEKONG_MEMBER = caller("uid-minh-nguyen", "minh.nguyen@example.org")
    /** An administrator through the data (an active manager of the all-users organization). */
    val ADMIN = caller("uid-maya-lin", "maya.lin@groundplatform.org")
  }
}
