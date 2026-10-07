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
package org.groundplatform.v2.devtools.prototypeapp.organization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.devtools.prototypeapp.PrototypeWorkbenchPage
import org.groundplatform.v2.devtools.prototypeapp.domain.model.CachedProfile
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MembershipStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationMember
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationRole

class OrganizationPagesTest {
  private val me = "me@example.org"

  private fun member(
    email: String,
    role: OrganizationRole = OrganizationRole.MEMBER,
    status: MembershipStatus = MembershipStatus.ACTIVE,
    name: String? = null,
  ) = OrganizationMember(email, role, status, profile = name?.let { CachedProfile(it) })

  private val managed =
    Organization(
      id = "managed",
      name = "Zeta Lab",
      members =
        listOf(
          member("zed@example.org", name = "Zed"),
          member(me, OrganizationRole.MANAGER, name = "Me"),
          member("amy@example.org", OrganizationRole.MANAGER, name = "Amy"),
          member("req@example.org", status = MembershipStatus.REQUESTED, name = "Req"),
          member("inv@example.org", status = MembershipStatus.INVITED),
        ),
    )
  private val joined =
    Organization(
      id = "joined",
      name = "Alpha NGO",
      members = listOf(member("boss@example.org", OrganizationRole.MANAGER), member(me)),
    )
  private val listed =
    Organization(
      id = "listed",
      name = "Open Group",
      description = "Mangroves",
      members = emptyList(),
    )
  private val requested =
    Organization(
      id = "requested",
      name = "Pending Org",
      members = listOf(member(me, status = MembershipStatus.REQUESTED)),
    )
  private val unlisted =
    Organization(id = "unlisted", name = "Secret", isListed = false, members = emptyList())
  private val all = listOf(listed, joined, unlisted, managed, requested)

  @Test
  fun mine_managersFirstThenByName() {
    assertEquals(listOf("managed", "joined"), OrganizationPages.mine(all, me).map { it.id })
  }

  @Test
  fun discoverable_listedNonMembersOnly_includingPendingRequests() {
    assertEquals(
      listOf("listed", "requested"),
      OrganizationPages.discoverable(all, me).map { it.id },
    )
  }

  @Test
  fun relation_coversEveryMembershipState() {
    assertEquals(OrganizationRelation.MANAGER, OrganizationPages.relationOf(managed, me))
    assertEquals(OrganizationRelation.MEMBER, OrganizationPages.relationOf(joined, me))
    assertEquals(OrganizationRelation.REQUESTED, OrganizationPages.relationOf(requested, me))
    assertEquals(OrganizationRelation.NONE, OrganizationPages.relationOf(listed, me))
    assertEquals(
      OrganizationRelation.INVITED,
      OrganizationPages.relationOf(managed, "inv@example.org"),
    )
  }

  @Test
  fun search_matchesNameAndDescription() {
    assertEquals(listOf(listed), OrganizationPages.search(all, "mangrove"))
    assertEquals(listOf(joined), OrganizationPages.search(all, "ALPHA"))
    assertEquals(all, OrganizationPages.search(all, "  "))
  }

  @Test
  fun canEditDetails_onlyForManagers() {
    assertEquals(true, OrganizationPages.canEditDetails(managed, me))
    assertEquals(false, OrganizationPages.canEditDetails(joined, me))
  }

  @Test
  fun tabs_detailsComesFirstAndIncludesImagerySources() {
    assertEquals(OrganizationTab.DETAILS, OrganizationTab.entries.first())
    assertTrue(OrganizationTab.IMAGERY_SOURCES in OrganizationTab.entries)
  }

  @Test
  fun syntheticAllUsers_isIncludedFirstInMineAndExcludedFromDiscoverable() {
    val allUsers =
      Organization(
        id = Organization.ALL_USERS_ID,
        name = "All users",
        isListed = false,
        isSynthetic = true,
      )
    val withAllUsers = all + allUsers
    assertEquals(
      listOf(Organization.ALL_USERS_ID, "managed", "joined"),
      OrganizationPages.mine(withAllUsers, me).map { it.id },
    )
    assertFalse(
      OrganizationPages.discoverable(withAllUsers, me).any { it.id == Organization.ALL_USERS_ID }
    )
    assertEquals(OrganizationRelation.MANAGER, OrganizationPages.relationOf(allUsers, me))
  }

  @Test
  fun membersView_groupsAndOrders_forManagers() {
    val view = OrganizationPages.membersView(managed, me)
    assertEquals(listOf("req@example.org"), view.requests.map { it.email })
    assertEquals(listOf("inv@example.org"), view.invited.map { it.email })
    // Managers first with the viewer at the top, then members by name.
    assertEquals(listOf(me, "amy@example.org", "zed@example.org"), view.active.map { it.email })
  }

  @Test
  fun membersView_hidesPendingPeopleFromMembers() {
    val view = OrganizationPages.membersView(managed, "zed@example.org")
    assertTrue(view.requests.isEmpty())
    assertTrue(view.invited.isEmpty())
    assertEquals(3, view.active.size)
  }

  @Test
  fun canChange_protectsTheLastManager() {
    val onlyManager = managed.member("amy@example.org")!!
    assertTrue(OrganizationPages.canChange(managed, onlyManager))
    val soleManagerOrg = joined
    assertFalse(OrganizationPages.canChange(soleManagerOrg, soleManagerOrg.managers.single()))
    assertTrue(OrganizationPages.canChange(soleManagerOrg, soleManagerOrg.member(me)!!))
  }

  @Test
  fun hashes_roundTripOrganizationPages() {
    assertEquals(
      PrototypeWorkbenchPage.ORGANIZATIONS,
      PrototypeWorkbenchPage.fromHash("#organizations"),
    )
    assertEquals(
      PrototypeWorkbenchPage.ORGANIZATION,
      PrototypeWorkbenchPage.fromHash("#organization/org-kfs"),
    )
    assertEquals("org-kfs", PrototypeWorkbenchPage.organizationIdFromHash("#organization/org-kfs"))
    assertNull(PrototypeWorkbenchPage.organizationIdFromHash("#organization"))
    assertNull(PrototypeWorkbenchPage.organizationIdFromHash("#surveys"))
    assertEquals(
      "organization/org-kfs",
      PrototypeWorkbenchPage.hashFor(PrototypeWorkbenchPage.ORGANIZATION, "org-kfs"),
    )
    assertEquals("surveys", PrototypeWorkbenchPage.hashFor(PrototypeWorkbenchPage.WEB_SURVEYS, "x"))
    assertEquals(
      PrototypeWorkbenchPage.WEB_DASHBOARD,
      PrototypeWorkbenchPage.fromHash("#dashboard"),
    )
  }
}
