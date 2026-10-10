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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.runNow
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.seededStore
import org.groundplatform.v2.devtools.prototypeapp.data.seed.PrototypeFakeOrganizationsData
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationRelation
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationRole
import org.groundplatform.v2.devtools.prototypeapp.domain.model.discoverableBy
import org.groundplatform.v2.devtools.prototypeapp.domain.model.organizationsOf
import org.groundplatform.v2.devtools.prototypeapp.domain.model.relationTo
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.InviteOrganizationMemberUseCase

/** The `"All users"` organization: explicit Managers are platform admins; everyone else reads. */
class AllUsersOrganizationTest {
  private val allUsersId = Organization.ALL_USERS_ID
  private val admin = PrototypeFakeOrganizationsData.SIGNED_IN_EMAIL
  private val someone = "field.lead@example.org"

  private fun repo() = OrganizationRepositoryImpl(seededStore())

  @Test
  fun managers_areARealRoleCheck_notEveryone() = runNow {
    val allUsers = repo().getOrganization(allUsersId)!!
    assertTrue(allUsers.isSynthetic)
    assertFalse(allUsers.isListed)
    assertEquals(listOf(admin), allUsers.managers.map { it.email })
    assertTrue(allUsers.isManager(admin))
    assertFalse(allUsers.isManager(someone))
    assertEquals(OrganizationRelation.MANAGER, allUsers.relationTo(admin))
    assertEquals(OrganizationRelation.MEMBER, allUsers.relationTo(someone))
  }

  @Test
  fun everyoneSeesIt_butNobodyIsASurveyMember() = runNow {
    val organizations = repo().getOrganizations()
    val allUsers = organizations.first { it.id == allUsersId }
    assertTrue(allUsers.isVisibleTo(someone))
    assertTrue(allUsers.isVisibleTo(admin))
    assertFalse(allUsers.isMember(admin)) // No surveys are created in "All users".
    assertTrue(organizations.organizationsOf(someone).first().isSynthetic)
    assertTrue(organizations.discoverableBy(someone).none { it.isSynthetic })
    assertTrue(repo().getOrganizationsFor(admin).none { it.isSynthetic })
  }

  @Test
  fun nobodyCanJoinAsMember_requestToJoin_orBeDemoted() = runNow {
    val repo = repo()
    assertNull(repo.requestToJoin(allUsersId, someone))
    assertNull(repo.inviteMember(allUsersId, someone, OrganizationRole.MEMBER, token = "t1"))
    val invited =
      assertNotNull(repo.inviteMember(allUsersId, someone, OrganizationRole.MANAGER, "t2"))
    assertEquals(OrganizationRole.MANAGER, invited.member(someone)!!.role)
    assertNull(repo.setMemberRole(allUsersId, admin, OrganizationRole.MEMBER))
    // The last Manager can't be removed either.
    assertNull(repo.removeMember(allUsersId, admin))
  }

  @Test
  fun inviteError_explainsThatEveryoneIsAlreadyAMember() = runNow {
    val repo = repo()
    val useCase = InviteOrganizationMemberUseCase(repo)
    val allUsers = repo.getOrganization(allUsersId)
    assertNotNull(useCase.inviteError(allUsers, "new@example.org", OrganizationRole.MEMBER))
    assertNull(useCase.inviteError(allUsers, "new@example.org", OrganizationRole.MANAGER))
    val kfs = repo.getOrganization(PrototypeFakeOrganizationsData.KENYA_FOREST_SERVICE)
    assertNull(useCase.inviteError(kfs, "new@example.org", OrganizationRole.MEMBER))
  }

  @Test
  fun itStaysUnlisted() = runNow {
    val repo = repo()
    val updated =
      repo.updateOrganization(allUsersId) { it.copy(isListed = true, name = "Everyone") }
    assertNotNull(updated)
    assertEquals("Everyone", updated.name)
    assertFalse(updated.isListed)
    assertTrue(updated.isSynthetic)
  }
}
