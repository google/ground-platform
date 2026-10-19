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
package org.openforis.collectone.v2.web.ui.organization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.domain.model.CachedProfile
import org.openforis.collectone.v2.app.domain.model.MembershipStatus
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.OrganizationMember
import org.openforis.collectone.v2.app.domain.model.OrganizationRole
import org.openforis.collectone.v2.web.domain.model.OrganizationRelation
import org.openforis.collectone.v2.web.domain.model.canChangeMember
import org.openforis.collectone.v2.web.domain.model.discoverableBy
import org.openforis.collectone.v2.web.domain.model.membersViewFor
import org.openforis.collectone.v2.web.domain.model.organizationsOf
import org.openforis.collectone.v2.web.domain.model.relationTo
import org.openforis.collectone.v2.web.domain.model.searchOrganizations
import org.openforis.collectone.v2.web.ui.WebPage

/** The directory and member-grouping rules live in `domain/model/OrganizationDirectory.kt`. */
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
    assertEquals(listOf("managed", "joined"), all.organizationsOf(me).map { it.id })
  }

  @Test
  fun discoverable_listedNonMembersOnly_includingPendingRequests() {
    assertEquals(
      listOf("listed", "requested"),
      all.discoverableBy(me).map { it.id },
    )
  }

  @Test
  fun relation_coversEveryMembershipState() {
    assertEquals(OrganizationRelation.MANAGER, managed.relationTo(me))
    assertEquals(OrganizationRelation.MEMBER, joined.relationTo(me))
    assertEquals(OrganizationRelation.REQUESTED, requested.relationTo(me))
    assertEquals(OrganizationRelation.NONE, listed.relationTo(me))
    assertEquals(
      OrganizationRelation.INVITED,
      managed.relationTo("inv@example.org"),
    )
  }

  @Test
  fun search_matchesNameAndDescription() {
    assertEquals(listOf(listed), all.searchOrganizations("mangrove"))
    assertEquals(listOf(joined), all.searchOrganizations("ALPHA"))
    assertEquals(all, all.searchOrganizations("  "))
  }

  @Test
  fun canEditDetails_onlyForManagers() {
    assertEquals(true, managed.isManager(me))
    assertEquals(false, joined.isManager(me))
  }

  @Test
  fun tabs_detailsComesFirstAndIncludesImagerySources() {
    assertEquals(OrganizationTab.DETAILS, OrganizationTab.entries.first())
    assertTrue(OrganizationTab.IMAGERY_SOURCES in OrganizationTab.entries)
    assertEquals("Imagery", OrganizationTab.IMAGERY_SOURCES.label)
    // The library tabs follow Imagery.
    assertEquals(
      listOf(OrganizationTab.PURPOSES, OrganizationTab.DICTIONARY, OrganizationTab.TEMPLATES),
      OrganizationTab.entries.dropWhile { it != OrganizationTab.IMAGERY_SOURCES }.drop(1).take(3),
    )
    assertTrue(OrganizationTab.COST_CONTROLS in OrganizationTab.entries)
    assertTrue(OrganizationTab.COST_TRACKING in OrganizationTab.entries)
  }

  @Test
  fun impact_isNoLongerAnOrganizationTab() {
    // Every organization's numbers are published on the Impact page (`#impact/<id>`) instead.
    assertFalse(OrganizationTab.entries.any { it.label == "Impact" })
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
      withAllUsers.organizationsOf(me).map { it.id },
    )
    assertFalse(withAllUsers.discoverableBy(me).any { it.id == Organization.ALL_USERS_ID })
    // Everyone is implicitly a read-only member; only explicit Managers manage it.
    assertEquals(OrganizationRelation.MEMBER, allUsers.relationTo(me))
    assertFalse(allUsers.isManager(me))
    val managed =
      allUsers.copy(members = listOf(OrganizationMember(me, role = OrganizationRole.MANAGER)))
    assertEquals(OrganizationRelation.MANAGER, managed.relationTo(me))
    assertTrue(managed.isManager(me))
    assertFalse(managed.isMember(me))
  }

  @Test
  fun membersView_groupsAndOrders_forManagers() {
    val view = managed.membersViewFor(me)
    assertEquals(listOf("req@example.org"), view.requests.map { it.email })
    assertEquals(listOf("inv@example.org"), view.invited.map { it.email })
    // Managers first with the viewer at the top, then members by name.
    assertEquals(listOf(me, "amy@example.org", "zed@example.org"), view.active.map { it.email })
  }

  @Test
  fun membersView_hidesPendingPeopleFromMembers() {
    val view = managed.membersViewFor("zed@example.org")
    assertTrue(view.requests.isEmpty())
    assertTrue(view.invited.isEmpty())
    assertEquals(3, view.active.size)
  }

  @Test
  fun canChange_protectsTheLastManager() {
    val onlyManager = managed.member("amy@example.org")!!
    assertTrue(managed.canChangeMember(onlyManager))
    val soleManagerOrg = joined
    assertFalse(soleManagerOrg.canChangeMember(soleManagerOrg.managers.single()))
    assertTrue(soleManagerOrg.canChangeMember(soleManagerOrg.member(me)!!))
  }

  @Test
  fun hashes_roundTripOrganizationPages() {
    assertEquals(
      WebPage.ORGANIZATIONS,
      WebPage.fromHash("#organizations"),
    )
    assertEquals(
      WebPage.ORGANIZATION,
      WebPage.fromHash("#organization/org-kfs"),
    )
    assertEquals("org-kfs", WebPage.organizationIdFromHash("#organization/org-kfs"))
    assertNull(WebPage.organizationIdFromHash("#organization"))
    assertNull(WebPage.organizationIdFromHash("#surveys"))
    assertEquals(
      "organization/org-kfs",
      WebPage.hashFor(WebPage.ORGANIZATION, "org-kfs"),
    )
    assertEquals("surveys", WebPage.hashFor(WebPage.WEB_SURVEYS, "x"))
    // The Impact page may name the organization it shows.
    assertEquals(WebPage.IMPACT, WebPage.fromHash("#impact"))
    assertEquals(WebPage.IMPACT, WebPage.fromHash("#impact/org-kfs"))
    assertEquals("org-kfs", WebPage.organizationIdFromHash("#impact/org-kfs"))
    assertNull(WebPage.organizationIdFromHash("#impact"))
    assertEquals("impact/org-kfs", WebPage.hashFor(WebPage.IMPACT, "org-kfs"))
    assertEquals("impact", WebPage.hashFor(WebPage.IMPACT, null))
    assertEquals(
      WebPage.WEB_DASHBOARD,
      WebPage.fromHash("#dashboard"),
    )
  }
}
