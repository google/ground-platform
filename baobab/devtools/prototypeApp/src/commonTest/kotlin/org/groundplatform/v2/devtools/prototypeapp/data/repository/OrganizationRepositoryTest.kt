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
package org.groundplatform.v2.devtools.prototypeapp.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.runNow
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.seededStore
import org.groundplatform.v2.devtools.prototypeapp.data.seed.PrototypeFakeOrganizationsData
import org.groundplatform.v2.devtools.prototypeapp.domain.model.CachedProfile
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MembershipStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationRole

class OrganizationRepositoryTest {
  private val kfs = PrototypeFakeOrganizationsData.KENYA_FOREST_SERVICE
  private val maya = PrototypeFakeOrganizationsData.SIGNED_IN_EMAIL

  private fun repo() = OrganizationRepositoryImpl(seededStore())

  @Test
  fun seededMemberships_matchSampleData() = runNow {
    val repo = repo()
    val mine = repo.getOrganizationsFor(maya)
    assertEquals(
      listOf(kfs, PrototypeFakeOrganizationsData.MEKONG_MANGROVE_ALLIANCE),
      mine.map { it.id },
    )
    assertTrue(repo.getOrganization(kfs)!!.isManager(maya))
    assertTrue(
      !repo
        .getOrganization(PrototypeFakeOrganizationsData.MEKONG_MANGROVE_ALLIANCE)!!
        .isManager(maya)
    )
    // Pending requests and invites don't count as membership.
    assertTrue(repo.getOrganizationsFor("james.otieno@example.org").isEmpty())
    assertTrue(repo.getOrganizationsFor("wanjiku.m@example.org").isEmpty())

    // The synthetic "All users" organization is seeded with a real XYZ tile URL.
    val allUsers = assertNotNull(repo.getOrganization(PrototypeFakeOrganizationsData.ALL_USERS))
    assertEquals("All users", allUsers.name)
    assertTrue(allUsers.isSynthetic)
    assertTrue(allUsers.imagerySources.isNotEmpty())
    assertTrue(allUsers.imagerySources.first().isValidXyzUrl)
  }

  @Test
  fun createOrganization_makesCreatorTheFirstManager() = runNow {
    val repo = repo()
    val created =
      repo.createOrganization(
        Organization(id = "org-new", name = "New Org"),
        creatorEmail = "new.manager@example.org",
        creatorProfile = CachedProfile("New Manager"),
      )
    assertNotNull(created)
    assertEquals(listOf("new.manager@example.org"), created.managers.map { it.email })
    assertEquals("New Manager", created.managers.single().displayName)
    // IDs are unique.
    assertNull(repo.createOrganization(Organization(id = "org-new", name = "Dup"), "x@example.org"))
  }

  @Test
  fun updateOrganization_cannotChangeIdOrMembers() = runNow {
    val repo = repo()
    val before = repo.getOrganization(kfs)!!
    val after =
      repo.updateOrganization(kfs) {
        it.copy(id = "hacked", name = "Renamed", members = emptyList(), isListed = false)
      }!!
    assertEquals(kfs, after.id)
    assertEquals("Renamed", after.name)
    assertEquals(false, after.isListed)
    assertEquals(before.members, after.members)
  }

  @Test
  fun inviteThenAccept_activatesMemberAndClearsToken() = runNow {
    val repo = repo()
    val email = "new.person@example.org"
    val invited = repo.inviteMember(kfs, email, OrganizationRole.MEMBER, token = "tok-1")!!
    val pending = invited.member(email)!!
    assertEquals(MembershipStatus.INVITED, pending.status)
    assertEquals("tok-1", pending.inviteToken)
    assertTrue(!invited.isMember(email))
    // Inviting the same person twice is a no-op.
    assertNull(repo.inviteMember(kfs, email.uppercase(), OrganizationRole.MANAGER, "tok-2"))

    val accepted = repo.acceptInvite(kfs, email, "uid-new", CachedProfile("New Person"))!!
    val member = accepted.member(email)!!
    assertEquals(MembershipStatus.ACTIVE, member.status)
    assertNull(member.inviteToken)
    assertEquals("uid-new", member.userId)
    assertEquals("New Person", member.displayName)
    assertTrue(accepted.isMember(email))
    // Accepting again does nothing.
    assertNull(repo.acceptInvite(kfs, email, "uid-new", CachedProfile("New Person")))
  }

  @Test
  fun requestThenApprove_activatesMember() = runNow {
    val repo = repo()
    val email = "applicant@example.org"
    val requested = repo.requestToJoin(kfs, email)!!
    assertEquals(MembershipStatus.REQUESTED, requested.member(email)!!.status)
    assertTrue(requested.pendingRequests.any { it.email == email })
    val approved = repo.approveRequest(kfs, email)!!
    assertEquals(OrganizationRole.MEMBER, approved.roleOf(email))
    // Approving an already-active member does nothing.
    assertNull(repo.approveRequest(kfs, email))
  }

  @Test
  fun lastManager_cannotBeDemotedOrRemoved() = runNow {
    val repo = repo()
    val org = repo.getOrganization(kfs)!!
    assertEquals(2, org.managers.size)
    val (first, second) = org.managers.map { it.email }
    // With two managers, demoting one is fine.
    assertNotNull(repo.setMemberRole(kfs, first, OrganizationRole.MEMBER))
    assertEquals(listOf(second), repo.getOrganization(kfs)!!.managers.map { it.email })
    // The remaining manager can't be demoted or removed.
    assertNull(repo.setMemberRole(kfs, second, OrganizationRole.MEMBER))
    assertNull(repo.removeMember(kfs, second))
    // Promoting the first back works, after which the second can be removed.
    assertNotNull(repo.setMemberRole(kfs, first, OrganizationRole.MANAGER))
    assertNotNull(repo.removeMember(kfs, second))
    assertNull(repo.getOrganization(kfs)!!.member(second))
  }

  @Test
  fun removeMember_dropsPendingInvitesAndRequests() = runNow {
    val repo = repo()
    assertNotNull(repo.removeMember(kfs, "james.otieno@example.org"))
    assertNotNull(repo.removeMember(kfs, "wanjiku.m@example.org"))
    val org = repo.getOrganization(kfs)!!
    assertTrue(org.pendingRequests.isEmpty())
    assertTrue(org.members.none { it.status == MembershipStatus.INVITED })
    // Unknown people and organizations are reported as `null`.
    assertNull(repo.removeMember(kfs, "nobody@example.org"))
    assertNull(repo.removeMember("org-missing", maya))
  }

  @Test
  fun deleteOrganization_makesSurveysPersonal() = runNow {
    val store = seededStore()
    val repo = OrganizationRepositoryImpl(store)
    val surveys = SurveyRepositoryImpl(store)
    assertTrue(surveys.getSurveys().any { it.organizationId == kfs })
    repo.deleteOrganization(kfs)
    assertNull(repo.getOrganization(kfs))
    assertTrue(surveys.getSurveys().none { it.organizationId == kfs })
  }
}
