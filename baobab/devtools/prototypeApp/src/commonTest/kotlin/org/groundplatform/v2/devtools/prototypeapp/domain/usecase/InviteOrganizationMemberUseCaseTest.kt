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
package org.groundplatform.v2.devtools.prototypeapp.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.runNow
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.seededStore
import org.groundplatform.v2.devtools.prototypeapp.data.repository.OrganizationRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.seed.PrototypeFakeOrganizationsData
import org.groundplatform.v2.devtools.prototypeapp.domain.model.CachedProfile
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MembershipStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationMember
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationRole

class InviteOrganizationMemberUseCaseTest {
  private val useCase = InviteOrganizationMemberUseCase(OrganizationRepositoryImpl(seededStore()))

  private val organization =
    Organization(
      id = "org-x",
      name = "X",
      members =
        listOf(
          OrganizationMember("boss@example.org", OrganizationRole.MANAGER),
          OrganizationMember(
            "amy@example.org",
            profile = CachedProfile("Amy Adams"),
          ),
          OrganizationMember("inv@example.org", status = MembershipStatus.INVITED),
          OrganizationMember(
            "req@example.org",
            status = MembershipStatus.REQUESTED,
            profile = CachedProfile("Req Person"),
          ),
        ),
    )

  @Test
  fun inviteError_rejectsInvalidAddresses_missingOrganizations_andExistingPeople() {
    assertEquals("Enter a valid email address.", useCase.inviteError(organization, "nope"))
    assertEquals("Enter a valid email address.", useCase.inviteError(organization, "a@b"))
    assertEquals("Organization not found.", useCase.inviteError(null, "new@example.org"))
    assertEquals(
      "Amy Adams is already a member.",
      useCase.inviteError(organization, " AMY@example.org "),
    )
    assertEquals(
      "inv@example.org has already been invited.",
      useCase.inviteError(organization, "inv@example.org"),
    )
    assertEquals(
      "Req Person has asked to join. Approve their request instead.",
      useCase.inviteError(organization, "req@example.org"),
    )
    assertNull(useCase.inviteError(organization, "new.person@example.org"))
  }

  @Test
  fun helpers_normalizeEmails_deriveUserIds_andValidateAcceptance() {
    assertEquals(
      "a.b@example.org",
      InviteOrganizationMemberUseCase.normalizeEmail(" A.B@Example.ORG "),
    )
    assertEquals(
      "uid-grace-njeri",
      InviteOrganizationMemberUseCase.userIdFor("grace.njeri@example.org"),
    )
    assertEquals("Enter a name.", useCase.acceptError("  "))
    assertNull(useCase.acceptError("Grace"))
  }

  @Test
  fun invoke_resetInviteLink_andAcceptInvite_walkTheInviteLifecycle() {
    val repository = OrganizationRepositoryImpl(seededStore())
    val useCase = InviteOrganizationMemberUseCase(repository)
    val orgId = PrototypeFakeOrganizationsData.KENYA_FOREST_SERVICE

    val invited =
      assertNotNull(runNow { useCase(orgId, " New.Person@Example.org ", OrganizationRole.MANAGER) })
        .member("new.person@example.org")!!
    assertEquals(MembershipStatus.INVITED, invited.status)
    assertEquals(OrganizationRole.MANAGER, invited.role)
    val token = assertNotNull(invited.inviteToken)

    // The repository's profile-update path leaves memberships untouched, so re-issuing the link is
    // currently a no-op that keeps the invite pending (see [InviteOrganizationMemberUseCase]).
    val reset =
      assertNotNull(runNow { useCase.resetInviteLink(orgId, invited.email) })
        .member(invited.email)!!
    assertEquals(MembershipStatus.INVITED, reset.status)
    assertEquals(token, reset.inviteToken)

    val accepted =
      assertNotNull(
          runNow { useCase.acceptInvite(orgId, invited.email, " New Person ", "avatar:4") }
        )
        .member(invited.email)!!
    assertEquals(MembershipStatus.ACTIVE, accepted.status)
    assertEquals("New Person", accepted.displayName)
    assertEquals("avatar:4", accepted.profile?.photoUrl)
    assertEquals("uid-new-person", accepted.userId)
    assertNull(accepted.inviteToken)
  }
}
