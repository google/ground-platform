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
package org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.seededStore
import org.groundplatform.v2.devtools.prototypeapp.data.repository.AuthRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.OrganizationRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.seed.PrototypeFakeOrganizationsData
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MembershipStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationRelation
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationRole
import org.groundplatform.v2.devtools.prototypeapp.domain.model.relationTo
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.CreateOrganizationUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.InviteOrganizationMemberUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ManageImagerySourcesUseCase
import org.groundplatform.v2.devtools.prototypeapp.ui.state.OrganizationEvent

/** See [OnboardingViewModelTest] for why fixtures are built outside `runNow`. */
class OrganizationViewModelTest {
  private class Fixture {
    val store = seededStore()
    val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    val organizationRepository = OrganizationRepositoryImpl(store)
    val authRepository = AuthRepositoryImpl()
    val viewModel =
      OrganizationViewModel(
        organizationRepository = organizationRepository,
        surveyRepository = SurveyRepositoryImpl(store),
        authRepository = authRepository,
        createOrganizationUseCase = CreateOrganizationUseCase(organizationRepository),
        inviteMemberUseCase = InviteOrganizationMemberUseCase(organizationRepository),
        manageImagerySourcesUseCase = ManageImagerySourcesUseCase(organizationRepository),
        scope = scope,
      )
    val events = mutableListOf<OrganizationEvent>()

    init {
      scope.launch { viewModel.events.collect { events += it } }
      // The organization pages are only reachable once signed in.
      scope.launch { authRepository.signInWithGoogle() }
    }

    val uiState
      get() = viewModel.uiState.value

    val me
      get() = uiState.signedInUserEmail

    fun organization(id: String): Organization = uiState.organization(id)!!

    val kfs
      get() = organization(PrototypeFakeOrganizationsData.KENYA_FOREST_SERVICE)
  }

  @Test
  fun initialState_hasSeededOrganizations_andNothingOpen() {
    val f = Fixture()
    assertTrue(f.uiState.organizations.isNotEmpty())
    assertTrue(f.uiState.surveys.isNotEmpty())
    assertEquals(PrototypeFakeOrganizationsData.SIGNED_IN_EMAIL, f.me)
    assertTrue(f.uiState.isSignedIn)
    assertNull(f.uiState.openOrganizationId)
    assertNull(f.uiState.openOrganization)
    assertNull(f.uiState.notice)
    assertTrue(f.uiState.myOrganizations.first().isSynthetic)
    assertTrue(f.uiState.myOrganizations.any { it.id == f.kfs.id })
    assertTrue(f.uiState.discoverableOrganizations.none { it.isMember(f.me) || it.isSynthetic })
    assertTrue(f.uiState.managesOrganization(f.kfs))
    assertEquals(OrganizationRelation.MANAGER, f.uiState.relationTo(f.kfs))
    assertTrue(f.uiState.surveyCountInOrganization(f.kfs.id) > 0)
    assertEquals(
      f.uiState.surveys.filter { it.organizationId == f.kfs.id },
      f.uiState.surveysInOrganization(f.kfs.id),
    )
  }

  @Test
  fun openOrganization_andOpenOrganizations_publishNavigationEvents_andClearTheNotice() {
    val f = Fixture()
    f.viewModel.openOrganization(f.kfs.id)
    assertEquals(f.kfs.id, f.uiState.openOrganizationId)
    assertEquals(f.kfs, f.uiState.openOrganization)
    assertEquals(OrganizationEvent.OrganizationOpened(f.kfs.id), f.events.last())

    f.viewModel.openOrganizations()
    assertEquals(OrganizationEvent.OrganizationsOpened, f.events.last())
    // The directory keeps the last open ID so `#organization/<id>` can be restored.
    assertEquals(f.kfs.id, f.uiState.openOrganizationId)
  }

  @Test
  fun createOrganization_storesItWithTheCreatorAsManager_opensIt_andNotices() {
    val f = Fixture()
    val before = f.uiState.organizations.size
    val id = f.viewModel.createOrganization("  Mangrove Watch ", " Coastal monitoring ", false)
    assertEquals("org-mangrove-watch", id)
    val created = f.organization(id)
    assertEquals(before + 1, f.uiState.organizations.size)
    assertEquals("Mangrove Watch", created.name)
    assertEquals("Coastal monitoring", created.description)
    assertFalse(created.isListed)
    assertTrue(created.isManager(f.me))
    assertEquals(id, f.uiState.openOrganizationId)
    assertEquals("Created organization \"Mangrove Watch\".", f.uiState.notice)
    assertEquals(OrganizationEvent.OrganizationOpened(id), f.events.last())

    f.viewModel.dismissNotice()
    assertNull(f.uiState.notice)

    // A second organization with the same name gets a numeric suffix.
    assertEquals("org-mangrove-watch-2", f.viewModel.createOrganization("Mangrove Watch", "", true))
  }

  @Test
  fun updateOrganizationDetails_trimsFields() {
    val f = Fixture()
    f.viewModel.updateOrganizationDetails(
      f.kfs.id,
      " KFS ",
      " Forests ",
      " https://kfs.go.ke ",
      false,
    )
    val updated = f.kfs
    assertEquals("KFS", updated.name)
    assertEquals("Forests", updated.description)
    assertEquals("https://kfs.go.ke", updated.websiteUrl)
    assertFalse(updated.isListed)
  }

  @Test
  fun deleteOrganization_returnsToTheDirectory_andNotices() {
    val f = Fixture()
    val name = f.kfs.name
    f.viewModel.openOrganization(f.kfs.id)
    f.viewModel.deleteOrganization(f.kfs.id)
    assertNull(f.uiState.organization(PrototypeFakeOrganizationsData.KENYA_FOREST_SERVICE))
    assertNull(f.uiState.openOrganizationId)
    assertEquals("Deleted organization \"$name\".", f.uiState.notice)
    assertEquals(OrganizationEvent.OrganizationsOpened, f.events.last())
  }

  @Test
  fun inviteMember_validatesTheAddress_thenAddsAnInvite_whichCanBeResetAndAccepted() {
    val f = Fixture()
    val orgId = f.kfs.id
    assertEquals(
      "Enter a valid email address.",
      f.viewModel.inviteMember(orgId, "nope", OrganizationRole.MEMBER),
    )
    assertNotNull(f.viewModel.inviteMember(orgId, f.me, OrganizationRole.MEMBER))
    assertEquals(
      "Organization not found.",
      f.viewModel.inviteMember("org-missing", "a@b.co", OrganizationRole.MEMBER),
    )

    assertNull(
      f.viewModel.inviteMember(orgId, " New.Person@Example.org ", OrganizationRole.MANAGER)
    )
    val invited = f.kfs.member("new.person@example.org")!!
    assertEquals(MembershipStatus.INVITED, invited.status)
    assertEquals(OrganizationRole.MANAGER, invited.role)
    val token = assertNotNull(invited.inviteToken)
    assertEquals(
      "new.person@example.org has already been invited.",
      f.viewModel.inviteMember(orgId, invited.email, OrganizationRole.MEMBER),
    )

    // Re-issuing the link keeps the invite pending (the repository's profile-update path leaves
    // memberships untouched, so the token itself is unchanged; see
    // InviteOrganizationMemberUseCase).
    f.viewModel.resetInviteLink(orgId, invited.email)
    val reset = f.kfs.member(invited.email)!!
    assertEquals(MembershipStatus.INVITED, reset.status)
    assertEquals(token, reset.inviteToken)

    assertEquals("Enter a name.", f.viewModel.acceptInvite(orgId, invited.email, "  ", null))
    assertNull(f.viewModel.acceptInvite(orgId, invited.email, " New Person ", "avatar:4"))
    val accepted = f.kfs.member(invited.email)!!
    assertEquals(MembershipStatus.ACTIVE, accepted.status)
    assertEquals("New Person", accepted.displayName)
    assertEquals("uid-new-person", accepted.userId)
  }

  @Test
  fun requestToJoin_thenApprove_makesTheSignedInUserAMember() {
    val f = Fixture()
    val listed = f.uiState.discoverableOrganizations.first { it.member(f.me) == null }
    f.viewModel.requestToJoin(listed.id)
    assertEquals(MembershipStatus.REQUESTED, f.organization(listed.id).member(f.me)?.status)
    assertEquals(OrganizationRelation.REQUESTED, f.uiState.relationTo(f.organization(listed.id)))

    f.viewModel.approveRequest(listed.id, f.me)
    assertTrue(f.organization(listed.id).isMember(f.me))
    assertTrue(f.uiState.signedInUserOrganizations.any { it.id == listed.id })
  }

  @Test
  fun setMemberRole_refusesToDemoteTheLastManager_withANotice() {
    val f = Fixture()
    val orgId = f.kfs.id
    // Demote every other Manager first so the signed-in user is the last one.
    f.kfs.managers
      .filter { it.email != f.me }
      .forEach { f.viewModel.setMemberRole(orgId, it.email, OrganizationRole.MEMBER) }
    assertEquals(listOf(f.me), f.kfs.managers.map { it.email })
    assertNull(f.uiState.notice)

    f.viewModel.setMemberRole(orgId, f.me, OrganizationRole.MEMBER)
    assertEquals(OrganizationRole.MANAGER, f.kfs.roleOf(f.me))
    assertEquals(
      "An organization needs at least one Manager. Make someone else a Manager first.",
      f.uiState.notice,
    )
  }

  @Test
  fun removeMember_removesOthers_andLeavingReturnsToTheDirectory() {
    val f = Fixture()
    val orgId = f.kfs.id
    val other = f.kfs.activeMembers.first { it.email != f.me && !it.isActiveManager }
    f.viewModel.removeMember(orgId, other.email)
    assertNull(f.kfs.member(other.email))

    // Leaving as the last Manager is refused.
    f.kfs.managers
      .filter { it.email != f.me }
      .forEach { f.viewModel.setMemberRole(orgId, it.email, OrganizationRole.MEMBER) }
    f.viewModel.openOrganization(orgId)
    f.viewModel.removeMember(orgId, f.me)
    assertTrue(f.kfs.isMember(f.me))
    assertEquals(orgId, f.uiState.openOrganizationId)
    assertTrue(f.uiState.notice!!.startsWith("An organization needs at least one Manager."))

    // Hand over, then leave.
    val successor = f.kfs.activeMembers.first { it.email != f.me }
    f.viewModel.setMemberRole(orgId, successor.email, OrganizationRole.MANAGER)
    val name = f.kfs.name
    f.viewModel.removeMember(orgId, f.me)
    assertFalse(f.kfs.isMember(f.me))
    assertNull(f.uiState.openOrganizationId)
    assertEquals("You left \"$name\".", f.uiState.notice)
    assertEquals(OrganizationEvent.OrganizationsOpened, f.events.last())
  }

  @Test
  fun imagerySources_areValidated_added_edited_toggled_andRemoved() {
    val f = Fixture()
    val orgId = f.kfs.id
    val url = "https://tile.opentopomap.org/{z}/{x}/{y}.png"
    assertEquals(
      "Enter a name for the imagery source.",
      f.viewModel.addImagerySource(orgId, "  ", url),
    )
    assertEquals(
      "Enter an http(s):// XYZ tile URL containing {z}, {x}, and {y}.",
      f.viewModel.addImagerySource(orgId, "Topo", "https://example.org/tiles"),
    )
    assertEquals(
      "Organization not found.",
      f.viewModel.addImagerySource("org-missing", "Topo", url),
    )

    val before = f.kfs.imagerySources.size
    assertNull(f.viewModel.addImagerySource(orgId, " Topo ", " $url ", allowOfflineDownload = true))
    val added = f.kfs.imagerySources.last()
    assertEquals(before + 1, f.kfs.imagerySources.size)
    assertEquals("imagery-kenya-forest-service-topo", added.id)
    assertEquals("Topo", added.name)
    assertEquals(url, added.urlTemplate)
    assertTrue(added.allowOfflineDownload)

    assertNotNull(f.viewModel.updateImagerySource(orgId, added.id, "Topo", "nope", false))
    assertNull(f.viewModel.updateImagerySource(orgId, added.id, "Topo 2", url, false))
    val edited = f.kfs.imagerySources.first { it.id == added.id }
    assertEquals("Topo 2", edited.name)
    assertFalse(edited.allowOfflineDownload)

    f.viewModel.setImagerySourceOfflineAllowed(orgId, added.id, true)
    assertTrue(f.kfs.imagerySources.first { it.id == added.id }.allowOfflineDownload)

    f.viewModel.removeImagerySource(orgId, added.id)
    assertTrue(f.kfs.imagerySources.none { it.id == added.id })
    assertIs<OrganizationEvent.ImagerySourceRemoved>(f.events.last())
    assertEquals(added.id, (f.events.last() as OrganizationEvent.ImagerySourceRemoved).sourceId)
  }

  @Test
  fun reset_clearsTheOpenOrganizationAndNotice() {
    val f = Fixture()
    f.viewModel.createOrganization("Temp", "", true)
    assertNotNull(f.uiState.openOrganizationId)
    assertNotNull(f.uiState.notice)
    f.viewModel.reset()
    assertNull(f.uiState.openOrganizationId)
    assertNull(f.uiState.notice)
  }
}
