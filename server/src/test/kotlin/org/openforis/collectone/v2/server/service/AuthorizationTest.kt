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
import kotlinx.coroutines.withContext
import openforis.collectone.v1.data.AcquirePlotLockRequest
import openforis.collectone.v1.data.DataMutation
import openforis.collectone.v1.data.EntityMutation
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.ImpactEvent
import openforis.collectone.v1.data.ListEntitiesRequest
import openforis.collectone.v1.data.ListMutationLogsRequest
import openforis.collectone.v1.data.MutateDataRequest
import openforis.collectone.v1.data.RecordImpactEventsRequest
import openforis.collectone.v1.data.ReleasePlotLockRequest
import openforis.collectone.v1.data.SubmissionMutation
import openforis.collectone.v1.data.SubmissionRecord
import openforis.collectone.v1.survey.AcceptSurveyInviteRequest
import openforis.collectone.v1.survey.AclEntry
import openforis.collectone.v1.survey.BatchGetSurveyOverviewsRequest
import openforis.collectone.v1.survey.GetOrganizationRequest
import openforis.collectone.v1.survey.GetSurveyAclRequest
import openforis.collectone.v1.survey.GetSurveyRequest
import openforis.collectone.v1.survey.ImagerySource
import openforis.collectone.v1.survey.JoinOrganizationRequest
import openforis.collectone.v1.survey.ListOrganizationsRequest
import openforis.collectone.v1.survey.ListSurveyOverviewsRequest
import openforis.collectone.v1.survey.MembershipStatus
import openforis.collectone.v1.survey.MutateOrganizationRequest
import openforis.collectone.v1.survey.MutateSurveyRequest
import openforis.collectone.v1.survey.Organization
import openforis.collectone.v1.survey.OrganizationMember
import openforis.collectone.v1.survey.OrganizationMutation
import openforis.collectone.v1.survey.OrganizationRole
import openforis.collectone.v1.survey.Role
import openforis.collectone.v1.survey.SurveyAcl
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyMutation
import openforis.collectone.v1.survey.SurveyState
import openforis.collectone.v1.survey.XyzTileSource
import org.openforis.collectone.v2.server.auth.Caller
import org.openforis.collectone.v2.server.auth.CallerElement

/**
 * Per-caller authorization (`docs/technical/backend/spanner-schema.md`, "Sharing Policies and
 * Access" and "Create vs. Update Authorization"), on the bundled sample seed.
 */
open class AuthorizationTest {
  /** A backend loaded with the bundled sample seed; overridden to test other stores. */
  protected open fun newBackend(): UserDataBackend = sampleBackend()

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
  fun surveyReads_requireARole_andOnlyOrganizersSeeCollaborators() = runTest {
    assertDenied(OUTSIDER) { backend.GetSurvey(GetSurveyRequest(survey_id = KENYA)) }

    val asCollector = asCaller(COLLECTOR) { backend.GetSurvey(GetSurveyRequest(survey_id = KENYA)) }
    assertEquals(KENYA, asCollector.survey?.survey_id)
    assertTrue(asCollector.acl?.entries.orEmpty().isEmpty())
    val asOrganizer =
      asCaller(FIELD_LEAD) { backend.GetSurveyAcl(GetSurveyAclRequest(survey_id = KENYA)) }
    assertTrue(asOrganizer.acl?.entries.orEmpty().isNotEmpty())
  }

  @Test
  fun linkAndPublicSurveys_areReadable_butNotListed() = runTest {
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
  fun surveyLists_followOwnershipAclEntriesAndOrganizationSharing() = runTest {
    assertEquals(setOf(KENYA), listedSurveys(COLLECTOR))
    assertEquals(setOf(MEKONG), listedSurveys(MEKONG_MEMBER))
    assertEquals(7, listedSurveys(ADMIN).size)
  }

  @Test
  fun lostAccess_appearsAsATombstoneInTheDelta() = runTest {
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
                acl_upsert =
                  SurveyAcl(
                    sharing_policy = openforis.collectone.v1.survey.SharingPolicy.RESTRICTED
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
  fun surveyListDeltas_leaveOutSurveysTheCallerNeverHad() = runTest {
    val token =
      asCaller(COLLECTOR) { backend.ListSurveyOverviews(ListSurveyOverviewsRequest()) }
        .next_sync_token
    asCaller(ADMIN) {
      backend.MutateSurvey(
        MutateSurveyRequest(
          survey_id = MEKONG,
          mutations = listOf(SurveyMutation(upsert = SurveyDef(title = "Renamed"))),
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
  fun organizationLists_showOnlyTheCallersOrganizations() = runTest {
    val outsiderOrgs =
      asCaller(OUTSIDER) { backend.ListOrganizations(ListOrganizationsRequest()) }.organizations
    assertTrue(outsiderOrgs.isEmpty())
    val collectorOrgs =
      asCaller(COLLECTOR) { backend.ListOrganizations(ListOrganizationsRequest()) }.organizations
    assertEquals(listOf(KENYA_FOREST_SERVICE), collectorOrgs.map { it.organization_id })
  }

  @Test
  fun joinRequests_returnOnlyTheRequestersOwnMembership() = runTest {
    val response =
      asCaller(OUTSIDER) {
        backend.JoinOrganization(
          JoinOrganizationRequest(
            organization_id = KENYA_FOREST_SERVICE,
            user_id = OUTSIDER.userId,
            email = OUTSIDER.email,
          )
        )
      }

    val members = response.organization!!.members
    assertEquals(listOf(OUTSIDER.email), members.map { it.email })
    assertEquals(MembershipStatus.REQUESTED, members.single().status)
    assertEquals(MembershipStatus.REQUESTED, response.member?.status)
    // The request is stored alongside the existing members, who stay hidden from the requester.
    val stored =
      asCaller(ADMIN) {
          backend.GetOrganization(GetOrganizationRequest(organization_id = KENYA_FOREST_SERVICE))
        }
        .organization!!
    assertTrue(stored.members.any { it.email == COLLECTOR.email })
    assertTrue(stored.members.any { it.email == OUTSIDER.email })
  }

  private suspend fun join(caller: Caller, inviteToken: String = "") =
    asCaller(caller) {
      backend.JoinOrganization(
        JoinOrganizationRequest(
          organization_id = KENYA_FOREST_SERVICE,
          user_id = caller.userId,
          email = caller.email,
          invite_token = inviteToken,
        )
      )
    }

  private suspend fun storedKenyaForestService(): Organization =
    asCaller(ADMIN) {
        backend.GetOrganization(GetOrganizationRequest(organization_id = KENYA_FOREST_SERVICE))
      }
      .organization!!

  @Test
  fun joinWithAnInviteToken_requiresTheMatchingPendingInvite() = runTest {
    // A made-up token, and the real token used by someone it wasn't sent to.
    assertDenied(OUTSIDER) { join(OUTSIDER, inviteToken = "made-up") }
    assertDenied(OUTSIDER) { join(OUTSIDER, inviteToken = KFS_INVITE_TOKEN) }
    // The invitee, but with the wrong token or an unverified email.
    assertDenied(KFS_INVITEE) { join(KFS_INVITEE, inviteToken = "made-up") }
    assertDenied(KFS_INVITEE.copy(emailVerified = false)) {
      join(KFS_INVITEE.copy(emailVerified = false), inviteToken = KFS_INVITE_TOKEN)
    }

    val stored = storedKenyaForestService()
    assertTrue(stored.members.none { it.email == OUTSIDER.email })
    assertEquals(
      MembershipStatus.INVITED,
      stored.members.single { it.email == KFS_INVITEE.email }.status,
    )
  }

  @Test
  fun joinWithAnInviteToken_activatesTheInviteWithItsRole() = runTest {
    // Invite the outsider as a Manager.
    val current = storedKenyaForestService()
    asCaller(ADMIN) {
      backend.MutateOrganization(
        MutateOrganizationRequest(
          organization_id = KENYA_FOREST_SERVICE,
          mutations =
            listOf(
              OrganizationMutation(
                upsert =
                  current.copy(
                    members =
                      current.members +
                        OrganizationMember(
                          email = OUTSIDER.email,
                          role = OrganizationRole.MANAGER,
                          status = MembershipStatus.INVITED,
                          invite_token = "outsider-invite",
                        )
                  )
              )
            ),
        )
      )
    }

    val response = join(OUTSIDER, inviteToken = "outsider-invite")

    val member = response.member!!
    assertEquals(MembershipStatus.ACTIVE, member.status)
    assertEquals(OrganizationRole.MANAGER, member.role)
    assertEquals(OUTSIDER.userId, member.user_id)
    assertEquals("", member.invite_token)
    // Now an active member, the caller sees the whole organization.
    assertTrue(response.organization!!.members.any { it.email == COLLECTOR.email })
    // The token is single-use.
    assertEquals(
      "",
      storedKenyaForestService().members.single { it.email == OUTSIDER.email }.invite_token,
    )
  }

  @Test
  fun joinByAnActiveMember_keepsTheirMembership() = runTest {
    val before = storedKenyaForestService()
    val membership = before.members.single { it.email == COLLECTOR.email }
    assertEquals(MembershipStatus.ACTIVE, membership.status)

    val response = join(COLLECTOR)

    assertEquals(MembershipStatus.ACTIVE, response.member?.status)
    val after = storedKenyaForestService()
    assertEquals(membership, after.members.single { it.email == COLLECTOR.email })
    assertEquals(before.version, after.version)
  }

  @Test
  fun writes_requireTheRightRole() = runTest {
    val retitle =
      MutateSurveyRequest(
        survey_id = KENYA,
        mutations = listOf(SurveyMutation(upsert = SurveyDef(title = "Mine"))),
      )
    assertDenied(COLLECTOR) { backend.MutateSurvey(retitle) }
    asCaller(FIELD_LEAD) { backend.MutateSurvey(retitle) }

    assertDenied(VIEWER) { backend.MutateData(MutateDataRequest(KENYA, listOf(create("e-v")))) }
    assertDenied(COLLECTOR) {
      backend.MutateOrganization(
        MutateOrganizationRequest(
          organization_id = KENYA_FOREST_SERVICE,
          mutations = listOf(OrganizationMutation(upsert = Organization(name = "Taken"))),
        )
      )
    }
  }

  @Test
  fun organizationWrites_returnTheAdministratorsImplicitManagerMembership() = runTest {
    // The administrator is only a plain Member of this organization in the stored data.
    val request =
      MutateOrganizationRequest(
        organization_id = ADMIN_MEMBER_ORG,
        mutations =
          listOf(
            OrganizationMutation(
              client_id = "c-admin",
              client_seq = 1L,
              upsert =
                Organization(
                  imagery_sources =
                    listOf(
                      ImagerySource(
                        imagery_source_id = "imagery-admin-topo",
                        name = "Topo",
                        xyz_tiles =
                          XyzTileSource(url_template = "https://example.org/{z}/{x}/{y}.png"),
                      )
                    )
                ),
            )
          ),
      )
    fun Organization.adminMembership() =
      members.single { it.email == ADMIN.email }.let { it.role to it.status }
    val expected = OrganizationRole.MANAGER to MembershipStatus.ACTIVE

    val written = asCaller(ADMIN) { backend.MutateOrganization(request) }.organization!!
    assertTrue(written.imagery_sources.any { it.imagery_source_id == "imagery-admin-topo" })
    assertEquals(expected, written.adminMembership())

    // A replayed (already applied) mutation returns the same view.
    val replayed = asCaller(ADMIN) { backend.MutateOrganization(request) }.organization!!
    assertEquals(expected, replayed.adminMembership())
  }

  @Test
  fun newRecords_areAttributedToTheCaller() = runTest {
    val created =
      asCaller(COLLECTOR) { backend.MutateData(MutateDataRequest(KENYA, listOf(create("e-c")))) }

    assertEquals("uid-collector-one", created.results.single().entity?.audit_info?.created_by)
  }

  @Test
  fun ownOnlyVisibility_limitsCollectorsToTheirOwnRecords() = runTest {
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
                  upsert = EntityRecord(label = "Mine"),
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
  fun peerDataVisibilityFlips_invalidateFilteredCollectorsDataTokens() = runTest {
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
                acl_upsert =
                  SurveyAcl(
                    peer_data_visibility =
                      openforis.collectone.v1.survey.PeerDataVisibility.OWN_ONLY
                  )
              )
            ),
        )
      )
    }

    val error =
      assertFailsWith<StatusException> {
        asCaller(COLLECTOR) {
          backend.ListEntities(ListEntitiesRequest(survey_id = KENYA, sync_token = collectorToken))
        }
      }
    assertEquals(Status.Code.FAILED_PRECONDITION, error.status.code)
    // Organizers see every record either way, so their tokens stay valid.
    asCaller(FIELD_LEAD) {
      backend.ListEntities(ListEntitiesRequest(survey_id = KENYA, sync_token = organizerToken))
    }
  }

  @Test
  fun batchGet_reportsPermissionDeniedPerSurvey() = runTest {
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

  @Test
  fun mutateSurvey_requiresMembershipToTargetOrganization() = runTest {
    val createInOrg =
      MutateSurveyRequest(
        survey_id = testId("outsider-survey"),
        mutations =
          listOf(
            SurveyMutation(
              upsert =
                SurveyDef(
                  title = "Outsider Survey",
                  organization_id = KENYA_FOREST_SERVICE,
                )
            )
          ),
      )
    assertDenied(OUTSIDER) { backend.MutateSurvey(createInOrg) }

    // Active member of Kenya Forest Service can create survey in it.
    val created = asCaller(COLLECTOR) { backend.MutateSurvey(createInOrg) }
    assertEquals(KENYA_FOREST_SERVICE, created.survey?.organization_id)

    // Outsider cannot transfer an existing survey to Kenya Forest Service.
    val personalSurveyId = testId("personal-survey")
    asCaller(OUTSIDER) {
      backend.MutateSurvey(
        MutateSurveyRequest(
          survey_id = personalSurveyId,
          mutations = listOf(SurveyMutation(upsert = SurveyDef(title = "Personal"))),
        )
      )
    }
    val transferToOrg =
      MutateSurveyRequest(
        survey_id = personalSurveyId,
        mutations =
          listOf(SurveyMutation(upsert = SurveyDef(organization_id = KENYA_FOREST_SERVICE))),
      )
    assertDenied(OUTSIDER) { backend.MutateSurvey(transferToOrg) }
  }

  @Test
  fun acceptSurveyInvite_rejectsBlankTokenAndClearsTokenOnAcceptance() = runTest {
    val blankError =
      assertFailsWith<StatusException> {
        asCaller(OUTSIDER) {
          backend.AcceptSurveyInvite(
            AcceptSurveyInviteRequest(survey_id = KENYA, invite_token = "")
          )
        }
      }
    assertEquals(Status.Code.INVALID_ARGUMENT, blankError.status.code)

    // Add a collaborator with invite_token = "secret-token".
    val inviteToken = "secret-token-123"
    asCaller(FIELD_LEAD) {
      backend.MutateSurvey(
        MutateSurveyRequest(
          survey_id = KENYA,
          mutations =
            listOf(
              SurveyMutation(
                acl_upsert =
                  SurveyAcl(
                    entries =
                      listOf(
                        AclEntry(
                          email = "newinvitee@example.org",
                          role = Role.DATA_COLLECTOR,
                          invitation_status = AclEntry.InvitationStatus.PENDING,
                          invite_token = inviteToken,
                        )
                      )
                  )
              )
            ),
        )
      )
    }

    val newInvitee = caller("uid-new-invitee", "newinvitee@example.org")
    val acceptResp =
      asCaller(newInvitee) {
        backend.AcceptSurveyInvite(
          AcceptSurveyInviteRequest(survey_id = KENYA, invite_token = inviteToken)
        )
      }
    val acceptedEntry =
      acceptResp.acl?.entries.orEmpty().first { it.email == "newinvitee@example.org" }
    assertEquals(AclEntry.InvitationStatus.ACCEPTED, acceptedEntry.invitation_status)
    assertEquals("", acceptedEntry.invite_token)
    assertEquals("uid-new-invitee", acceptedEntry.user_id)

    // Reusing the consumed invite token by an outsider cannot hijack the accepted ACL entry.
    val replayResp =
      asCaller(OUTSIDER) {
        backend.AcceptSurveyInvite(
          AcceptSurveyInviteRequest(survey_id = KENYA, invite_token = inviteToken)
        )
      }
    val afterReplay =
      replayResp.acl?.entries.orEmpty().first { it.email == "newinvitee@example.org" }
    assertEquals("uid-new-invitee", afterReplay.user_id)
  }

  @Test
  fun mutateData_enforcesCreatorOwnershipAndPredefinedEntityProtection() = runTest {
    val seedEntity =
      asCaller(ADMIN) { backend.ListEntities(ListEntitiesRequest(survey_id = KENYA)) }
        .entities
        .first { !it.is_field_created }

    // Non-organizer collector cannot modify or delete a predefined entity.
    assertDenied(COLLECTOR) {
      backend.MutateData(
        MutateDataRequest(
          survey_id = KENYA,
          mutations =
            listOf(
              DataMutation(
                client_id = "c-collector-predefined",
                client_seq = 100L,
                entity_mutation =
                  EntityMutation(
                    entity_id = seedEntity.entity_id,
                    upsert = EntityRecord(label = "Hacked Predefined"),
                  ),
              )
            ),
        )
      )
    }
    assertDenied(COLLECTOR) {
      backend.MutateData(
        MutateDataRequest(
          survey_id = KENYA,
          mutations =
            listOf(
              DataMutation(
                client_id = "c-collector-delete-predefined",
                client_seq = 101L,
                entity_mutation =
                  EntityMutation(
                    entity_id = seedEntity.entity_id,
                    upsert = EntityRecord(is_deleted = true),
                  ),
              )
            ),
        )
      )
    }

    // Collector CAN submit a form on a predefined entity, patching its status/properties alongside
    // the submission without being rejected with PERMISSION_DENIED.
    val predefinedSubId = testId("sub-predefined-col")
    val predefinedSubResult =
      asCaller(COLLECTOR) {
        backend.MutateData(
          MutateDataRequest(
            survey_id = KENYA,
            mutations =
              listOf(
                DataMutation(
                  client_id = "c-col-predefined-props",
                  client_seq = 1L,
                  entity_mutation =
                    EntityMutation(
                      entity_id = seedEntity.entity_id,
                      upsert =
                        EntityRecord(
                          properties =
                            mapOf(
                              "status" to
                                openforis.collectone.v1.forms.TypedValue(
                                  string_value = "Completed"
                                ),
                              "marker-symbol" to
                                openforis.collectone.v1.forms.TypedValue(string_value = "✓"),
                            )
                        ),
                    ),
                ),
                DataMutation(
                  client_id = "c-col-predefined-props",
                  client_seq = 2L,
                  submission_mutation =
                    SubmissionMutation(
                      submission_id = predefinedSubId,
                      upsert =
                        SubmissionRecord(
                          submission_id = predefinedSubId,
                          entity_id = seedEntity.entity_id,
                        ),
                    ),
                ),
              ),
          )
        )
      }
    assertEquals(
      "Completed",
      predefinedSubResult.results[0].entity?.properties?.get("status")?.string_value,
    )
    assertEquals(predefinedSubId, predefinedSubResult.results[1].submission?.submission_id)

    // Collector creates a field entity and a submission.
    val fieldEntityId = testId("field-entity-col")
    asCaller(COLLECTOR) {
      backend.MutateData(
        MutateDataRequest(
          survey_id = KENYA,
          mutations =
            listOf(
              DataMutation(
                client_id = "c-col-field-ent",
                client_seq = 102L,
                entity_mutation =
                  EntityMutation(
                    entity_id = fieldEntityId,
                    upsert =
                      EntityRecord(
                        entity_id = fieldEntityId,
                        label = "Collector Plot",
                        is_field_created = true,
                      ),
                  ),
              ),
              DataMutation(
                client_id = "c-col-sub",
                client_seq = 103L,
                submission_mutation =
                  SubmissionMutation(
                    submission_id = testId("sub-col"),
                    upsert =
                      SubmissionRecord(
                        submission_id = testId("sub-col"),
                        entity_id = fieldEntityId,
                      ),
                  ),
              ),
            ),
        )
      )
    }

    // Another collector adds to the survey.
    val otherCollector = caller("uid-collector-two", "collector.two@example.org")
    asCaller(FIELD_LEAD) {
      backend.MutateSurvey(
        MutateSurveyRequest(
          survey_id = KENYA,
          mutations =
            listOf(
              SurveyMutation(
                acl_upsert =
                  SurveyAcl(
                    entries =
                      listOf(
                        AclEntry(
                          user_id = otherCollector.userId,
                          email = otherCollector.email,
                          role = Role.DATA_COLLECTOR,
                          invitation_status = AclEntry.InvitationStatus.ACCEPTED,
                        )
                      )
                  )
              )
            ),
        )
      )
    }

    // Other collector CANNOT delete Collector's field-created entity.
    assertDenied(otherCollector) {
      backend.MutateData(
        MutateDataRequest(
          survey_id = KENYA,
          mutations =
            listOf(
              DataMutation(
                client_id = "c-col2-del-ent",
                client_seq = 1L,
                entity_mutation =
                  EntityMutation(
                    entity_id = fieldEntityId,
                    upsert = EntityRecord(is_deleted = true),
                  ),
              )
            ),
        )
      )
    }

    // Other collector CANNOT modify Collector's submission.
    assertDenied(otherCollector) {
      backend.MutateData(
        MutateDataRequest(
          survey_id = KENYA,
          mutations =
            listOf(
              DataMutation(
                client_id = "c-col2-mod-sub",
                client_seq = 2L,
                submission_mutation =
                  SubmissionMutation(
                    submission_id = testId("sub-col"),
                    upsert = SubmissionRecord(submission_id = testId("sub-col")),
                  ),
              )
            ),
        )
      )
    }

    // Survey Organizer CAN modify and delete.
    asCaller(FIELD_LEAD) {
      backend.MutateData(
        MutateDataRequest(
          survey_id = KENYA,
          mutations =
            listOf(
              DataMutation(
                client_id = "c-org-mod-sub",
                client_seq = 200L,
                submission_mutation =
                  SubmissionMutation(
                    submission_id = testId("sub-col"),
                    upsert = SubmissionRecord(submission_id = testId("sub-col")),
                  ),
              )
            ),
        )
      )
    }
  }

  @Test
  fun releasePlotLock_rejectsOtherCollectorsUnlessOrganizer() = runTest {
    val entityId = testId("plot-lock-ent")
    asCaller(COLLECTOR) {
      backend.MutateData(
        MutateDataRequest(
          survey_id = KENYA,
          mutations =
            listOf(
              DataMutation(
                client_id = "c-plot-ent",
                client_seq = 301L,
                entity_mutation =
                  EntityMutation(
                    entity_id = entityId,
                    upsert =
                      EntityRecord(
                        entity_id = entityId,
                        label = "Lock Plot",
                        is_field_created = true,
                      ),
                  ),
              )
            ),
        )
      )
      backend.AcquirePlotLock(AcquirePlotLockRequest(survey_id = KENYA, entity_id = entityId))
    }

    val anotherCollector = caller("uid-col-3", "col3@example.org")
    asCaller(FIELD_LEAD) {
      backend.MutateSurvey(
        MutateSurveyRequest(
          survey_id = KENYA,
          mutations =
            listOf(
              SurveyMutation(
                acl_upsert =
                  SurveyAcl(
                    entries =
                      listOf(
                        AclEntry(
                          user_id = anotherCollector.userId,
                          email = anotherCollector.email,
                          role = Role.DATA_COLLECTOR,
                          invitation_status = AclEntry.InvitationStatus.ACCEPTED,
                        )
                      )
                  )
              )
            ),
        )
      )
    }

    // Another collector cannot release collector's active lock.
    assertDenied(anotherCollector) {
      backend.ReleasePlotLock(ReleasePlotLockRequest(survey_id = KENYA, entity_id = entityId))
    }

    // Survey organizer CAN release the lock.
    val released =
      asCaller(FIELD_LEAD) {
        backend.ReleasePlotLock(ReleasePlotLockRequest(survey_id = KENYA, entity_id = entityId))
      }
    assertTrue(released.released)
  }

  @Test
  fun recordImpactEvents_requiresViewerAndOrgMembership() = runTest {
    val eventForKenya =
      ImpactEvent(
        event_id = testId("impact-1"),
        survey_id = KENYA,
        feature_count = 10L,
        area_ha = 5.0,
      )
    assertDenied(OUTSIDER) {
      backend.RecordImpactEvents(RecordImpactEventsRequest(events = listOf(eventForKenya)))
    }

    // Collector has survey access, so can record impact event for KENYA.
    asCaller(COLLECTOR) {
      val resp =
        backend.RecordImpactEvents(RecordImpactEventsRequest(events = listOf(eventForKenya)))
      assertEquals(1, resp.events.size)
    }

    val eventForOrg =
      ImpactEvent(
        event_id = testId("impact-2"),
        organization_id = KENYA_FOREST_SERVICE,
        feature_count = 10L,
        area_ha = 5.0,
      )
    assertDenied(OUTSIDER) {
      backend.RecordImpactEvents(RecordImpactEventsRequest(events = listOf(eventForOrg)))
    }
  }

  @Test
  fun listMutationLogs_requiresViewerAccess() = runTest {
    assertDenied(OUTSIDER) {
      backend.ListMutationLogs(ListMutationLogsRequest(survey_id = KENYA))
    }

    val logs =
      asCaller(COLLECTOR) {
        backend.ListMutationLogs(ListMutationLogsRequest(survey_id = KENYA))
      }
    assertTrue(logs.mutation_logs.isNotEmpty())
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

  @Test
  fun surveyOrganizers_canMoveASurveyToTrash_butCollectorsCant() = runTest {
    val trash =
      MutateSurveyRequest(
        survey_id = KENYA,
        mutations =
          listOf(
            SurveyMutation(upsert = SurveyDef(trash_time = com.squareup.wire.ofEpochSecond(1L, 0L)))
          ),
      )
    assertDenied(COLLECTOR) { backend.MutateSurvey(trash) }
    assertDenied(VIEWER) { backend.MutateSurvey(trash) }

    asCaller(FIELD_LEAD) { backend.MutateSurvey(trash) }

    assertTrue(KENYA !in listedSurveys(FIELD_LEAD))
    assertTrue(KENYA !in listedSurveys(ADMIN))
  }

  companion object {
    const val KENYA = "00000033-6b50-51cd-ac94-baea2ea06d65"
    const val PUBLIC = "00000036-1a1c-5c51-997f-28b67d813aa3"
    const val LINK = "00000031-ffc6-5c8e-934c-d14ec07533ad"
    const val MEKONG = "00000035-caf4-571e-b907-f506f92666ab"
    const val KENYA_FOREST_SERVICE = "00000013-31cc-5c95-90ea-379f624a5c72"
    /** An organization where [ADMIN] is stored as a plain active Member. */
    const val ADMIN_MEMBER_ORG = "00000014-1894-5118-9a64-566bbe3875bf"

    private fun caller(userId: String, email: String) =
      Caller(userId = userId, email = email, emailVerified = true)

    val OUTSIDER = caller("uid-outsider", "outsider@example.org")
    val COLLECTOR = caller("uid-collector-one", "collector.one@example.org")
    val FIELD_LEAD = caller("uid-field-lead", "field.lead@example.org")
    val VIEWER = caller("uid-reviewer", "reviewer@example.org")
    val MEKONG_MEMBER = caller("uid-minh-nguyen", "minh.nguyen@example.org")
    /** Invited to [KENYA_FOREST_SERVICE] in the seed, with [KFS_INVITE_TOKEN]. */
    val KFS_INVITEE = caller("uid-wanjiku", "wanjiku.m@example.org")
    const val KFS_INVITE_TOKEN = "kfs-7f3a9c"
    /** An administrator through the data (an active manager of the all-users organization). */
    val ADMIN = caller("uid-maya-lin", "maya.lin@collect-one.example.org")
  }
}
