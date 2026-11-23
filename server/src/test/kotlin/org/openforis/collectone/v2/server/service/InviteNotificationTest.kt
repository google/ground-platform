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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import openforis.collectone.v1.survey.AclEntry
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
import openforis.collectone.v1.userdata.UserDataSeed
import org.openforis.collectone.v2.server.auth.Caller
import org.openforis.collectone.v2.server.auth.CallerElement
import org.openforis.collectone.v2.server.data.InMemoryUserDataStore
import org.openforis.collectone.v2.server.messaging.InviteNotifier
import org.openforis.collectone.v2.server.messaging.OrganizationInviteNotification
import org.openforis.collectone.v2.server.messaging.SurveyInviteNotification

class InviteNotificationTest {

  private class RecordingInviteNotifier : InviteNotifier {
    val surveyInvites = mutableListOf<SurveyInviteNotification>()
    val organizationInvites = mutableListOf<OrganizationInviteNotification>()

    override fun notifySurveyInvites(invites: List<SurveyInviteNotification>) {
      surveyInvites += invites
    }

    override fun notifyOrganizationInvites(invites: List<OrganizationInviteNotification>) {
      organizationInvites += invites
    }
  }

  @Test
  fun mutateSurvey_notifiesOnlyNewlyAddedOrRotatedPendingInvites() = runTest {
    val store = InMemoryUserDataStore(UserDataSeed())
    val notifier = RecordingInviteNotifier()
    val backend = UserDataBackend(store = store, inviteNotifier = notifier)
    val caller =
      Caller(
        userId = "uid-owner",
        email = "owner@example.org",
        emailVerified = true,
        displayName = "Survey Owner",
        isAdmin = true,
      )

    withContext(CallerElement(caller)) {
      backend.registerCaller(caller)
      // Create survey and invite first collaborator.
      backend.MutateSurvey(
        MutateSurveyRequest(
          survey_id = "s-invite-1",
          mutations =
            listOf(
              SurveyMutation(
                client_id = "c1",
                client_seq = 1L,
                upsert = SurveyDef(survey_id = "s-invite-1", title = "Canopy Monitoring"),
                acl_upsert =
                  SurveyAcl(
                    entries =
                      listOf(
                        AclEntry(
                          email = "field1@example.org",
                          role = Role.DATA_COLLECTOR,
                          invitation_status = AclEntry.InvitationStatus.PENDING,
                          invite_token = "tok-initial",
                        )
                      )
                  ),
              )
            ),
        )
      )
      assertEquals(1, notifier.surveyInvites.size)
      val first = notifier.surveyInvites.single()
      assertEquals("s-invite-1", first.surveyId)
      assertEquals("Canopy Monitoring", first.surveyTitle)
      assertEquals("field1@example.org", first.recipientEmail)
      assertEquals(Role.DATA_COLLECTOR, first.role)
      assertEquals("tok-initial", first.inviteToken)
      assertEquals("owner@example.org", first.inviterEmail)
      assertEquals("Survey Owner", first.inviterDisplayName)

      // Updating the survey title without changing the ACL token does not resend the invite.
      backend.MutateSurvey(
        MutateSurveyRequest(
          survey_id = "s-invite-1",
          mutations =
            listOf(
              SurveyMutation(
                client_id = "c1",
                client_seq = 2L,
                upsert = SurveyDef(title = "Canopy Monitoring v2"),
              )
            ),
        )
      )
      assertEquals(1, notifier.surveyInvites.size)

      // Rotating the invite link token sends a new notification.
      backend.MutateSurvey(
        MutateSurveyRequest(
          survey_id = "s-invite-1",
          mutations =
            listOf(
              SurveyMutation(
                client_id = "c1",
                client_seq = 3L,
                acl_upsert =
                  SurveyAcl(
                    entries =
                      listOf(
                        AclEntry(
                          email = "field1@example.org",
                          role = Role.DATA_COLLECTOR,
                          invitation_status = AclEntry.InvitationStatus.PENDING,
                          invite_token = "tok-rotated",
                        )
                      )
                  ),
              )
            ),
        )
      )
      assertEquals(2, notifier.surveyInvites.size)
      assertEquals("tok-rotated", notifier.surveyInvites.last().inviteToken)
    }
  }

  @Test
  fun mutateOrganization_notifiesOnlyNewlyAddedOrRotatedPendingMemberInvites() = runTest {
    val store = InMemoryUserDataStore(UserDataSeed())
    val notifier = RecordingInviteNotifier()
    val backend = UserDataBackend(store = store, inviteNotifier = notifier)
    val caller =
      Caller(
        userId = "uid-admin",
        email = "admin@example.org",
        emailVerified = true,
        displayName = "Org Admin",
        isAdmin = true,
      )

    withContext(CallerElement(caller)) {
      backend.registerCaller(caller)
      backend.MutateOrganization(
        MutateOrganizationRequest(
          organization_id = "org-1",
          mutations =
            listOf(
              OrganizationMutation(
                client_id = "c1",
                client_seq = 1L,
                upsert =
                  Organization(
                    organization_id = "org-1",
                    name = "Earth Engine Partners",
                    members =
                      listOf(
                        OrganizationMember(
                          email = "partner@example.org",
                          role = OrganizationRole.MANAGER,
                          status = MembershipStatus.INVITED,
                          invite_token = "org-tok-1",
                        )
                      ),
                  ),
              )
            ),
        )
      )
      assertEquals(1, notifier.organizationInvites.size)
      val invite = notifier.organizationInvites.single()
      assertEquals("org-1", invite.organizationId)
      assertEquals("Earth Engine Partners", invite.organizationName)
      assertEquals("partner@example.org", invite.recipientEmail)
      assertEquals(OrganizationRole.MANAGER, invite.role)
      assertEquals("org-tok-1", invite.inviteToken)
      assertEquals("admin@example.org", invite.inviterEmail)

      // Unrelated organization description update does not resend the invite.
      backend.MutateOrganization(
        MutateOrganizationRequest(
          organization_id = "org-1",
          mutations =
            listOf(
              OrganizationMutation(
                client_id = "c1",
                client_seq = 2L,
                upsert = Organization(description = "Updated description"),
              )
            ),
        )
      )
      assertEquals(1, notifier.organizationInvites.size)
    }
  }
}
