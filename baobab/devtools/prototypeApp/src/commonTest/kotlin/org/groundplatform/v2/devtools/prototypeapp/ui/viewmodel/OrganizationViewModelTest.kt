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
import org.groundplatform.v2.devtools.prototypeapp.data.repository.LibraryRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.OrganizationRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.seed.PrototypeFakeOrganizationsData
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ConceptDataType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryConcept
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LocalizedText
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MembershipStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationRelation
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationRole
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.relationTo
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.CreateOrganizationUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.InviteOrganizationMemberUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ManageImagerySourcesUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ManageLibraryUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ResolveLibraryUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.SearchConceptsUseCase
import org.groundplatform.v2.devtools.prototypeapp.ui.state.OrganizationEvent

/** See [OnboardingViewModelTest] for why fixtures are built outside `runNow`. */
class OrganizationViewModelTest {
  private class Fixture {
    val store = seededStore()
    val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    val organizationRepository = OrganizationRepositoryImpl(store)
    val libraryRepository = LibraryRepositoryImpl(store)
    val authRepository = AuthRepositoryImpl()
    val viewModel =
      OrganizationViewModel(
        organizationRepository = organizationRepository,
        surveyRepository = SurveyRepositoryImpl(store),
        authRepository = authRepository,
        createOrganizationUseCase = CreateOrganizationUseCase(organizationRepository),
        inviteMemberUseCase = InviteOrganizationMemberUseCase(organizationRepository),
        manageImagerySourcesUseCase = ManageImagerySourcesUseCase(organizationRepository),
        libraryRepository = libraryRepository,
        manageLibraryUseCase = ManageLibraryUseCase(libraryRepository),
        resolveLibraryUseCase = ResolveLibraryUseCase(),
        searchConceptsUseCase = SearchConceptsUseCase(),
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
      OrganizationType.GOVERNMENT_AGENCY,
      "KE",
    )
    val updated = f.kfs
    assertEquals("KFS", updated.name)
    assertEquals("Forests", updated.description)
    assertEquals("https://kfs.go.ke", updated.websiteUrl)
    assertFalse(updated.isListed)
  }

  @Test
  fun createOrganization_persistsTheTypeAndCountry() {
    val f = Fixture()
    val id =
      f.viewModel.createOrganization(
        name = "Sahel Growers",
        description = "",
        isListed = true,
        organizationType = OrganizationType.COOPERATIVE,
        countryCode = "sn",
      )
    val created = f.organization(id)
    assertEquals(OrganizationType.COOPERATIVE, created.organizationType)
    assertEquals("SN", created.countryCode)
    assertEquals("Senegal (SN)", created.country?.label)

    // Both are optional.
    val plain = f.organization(f.viewModel.createOrganization("Plain", "", true))
    assertNull(plain.organizationType)
    assertNull(plain.countryCode)
  }

  @Test
  fun createOrganization_withAnUnknownCountry_storesNothing_andNotices() {
    val f = Fixture()
    val before = f.uiState.organizations.size
    val id = f.viewModel.createOrganization("Nowhere", "", true, OrganizationType.OTHER, "QQ")
    assertNull(f.uiState.organization(id))
    assertEquals(before, f.uiState.organizations.size)
    assertEquals("Couldn't create \"Nowhere\". Choose a country from the list.", f.uiState.notice)
    assertNull(f.uiState.openOrganizationId)
  }

  @Test
  fun updateOrganizationDetails_persistsTypeAndCountry_andBlankClearsTheCountry() {
    val f = Fixture()
    val kfs = f.kfs
    f.viewModel.updateOrganizationDetails(
      kfs.id,
      kfs.name,
      kfs.description,
      kfs.websiteUrl,
      kfs.isListed,
      OrganizationType.RESEARCH,
      " ug ",
    )
    assertEquals(OrganizationType.RESEARCH, f.kfs.organizationType)
    assertEquals("UG", f.kfs.countryCode)

    f.viewModel.updateOrganizationDetails(
      kfs.id,
      kfs.name,
      kfs.description,
      kfs.websiteUrl,
      kfs.isListed,
      null,
      "",
    )
    assertNull(f.kfs.organizationType)
    assertNull(f.kfs.countryCode)
  }

  @Test
  fun updateOrganizationDetails_withAnUnknownCountry_savesNothing_andNotices() {
    val f = Fixture()
    val before = f.kfs
    f.viewModel.updateOrganizationDetails(
      before.id,
      "Renamed",
      before.description,
      before.websiteUrl,
      before.isListed,
      OrganizationType.NGO,
      "Kenya",
    )
    assertEquals(before, f.kfs)
    assertEquals("Couldn't save. Choose a country from the list.", f.uiState.notice)
  }

  @Test
  fun allUsers_hidesTypeAndCountry_andIgnoresEditsToThem() {
    val f = Fixture()
    val allUsers = f.organization(PrototypeFakeOrganizationsData.ALL_USERS)
    assertFalse(allUsers.hasTypeAndCountry)
    assertTrue(f.kfs.hasTypeAndCountry)

    f.viewModel.updateOrganizationDetails(
      allUsers.id,
      allUsers.name,
      "Platform-wide",
      allUsers.websiteUrl,
      allUsers.isListed,
      OrganizationType.NGO,
      "KE",
    )
    val updated = f.organization(PrototypeFakeOrganizationsData.ALL_USERS)
    assertEquals("Platform-wide", updated.description)
    assertNull(updated.organizationType)
    assertNull(updated.countryCode)
  }

  @Test
  fun excludeFromPlatformAggregates_isOffByDefault_andOnlyManagersChangeIt() {
    val f = Fixture()
    assertFalse(f.kfs.excludeFromPlatformAggregates)
    // The sample Open Foris Community keeps its data out of platform-wide numbers.
    assertTrue(
      f.organization(PrototypeFakeOrganizationsData.OPEN_FORIS_COMMUNITY)
        .excludeFromPlatformAggregates
    )

    f.viewModel.setExcludeFromPlatformAggregates(f.kfs.id, true)
    assertTrue(f.kfs.excludeFromPlatformAggregates)
    f.viewModel.setExcludeFromPlatformAggregates(f.kfs.id, false)
    assertFalse(f.kfs.excludeFromPlatformAggregates)

    // The signed-in user is only a Member of the Mekong alliance, and "All users" never opts out.
    val mekong = PrototypeFakeOrganizationsData.MEKONG_MANGROVE_ALLIANCE
    f.viewModel.setExcludeFromPlatformAggregates(mekong, true)
    assertFalse(f.organization(mekong).excludeFromPlatformAggregates)
    f.viewModel.setExcludeFromPlatformAggregates(PrototypeFakeOrganizationsData.ALL_USERS, true)
    assertFalse(
      f.organization(PrototypeFakeOrganizationsData.ALL_USERS).excludeFromPlatformAggregates
    )
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

  // --- Library tabs ---

  @Test
  fun library_ofAnOrganization_listsItsEntriesFirst_andGlobalOnesReadOnly() {
    val f = Fixture()
    f.viewModel.openOrganization(f.kfs.id)
    val library = f.uiState.library
    assertEquals(f.kfs.id, library.organizationId)
    assertFalse(library.isGlobalLibrary)
    assertTrue(library.canEdit)
    assertEquals(
      listOf(
        "org.org-kenya-forest-service.cherry_delivery_kg",
        "org.org-kenya-forest-service.shade_tree_count",
      ),
      library.organizationConcepts.map { it.entry.id },
    )
    assertTrue(library.organizationConcepts.all { it.canEdit && !it.isGlobal })
    assertTrue(library.globalConcepts.isNotEmpty())
    assertTrue(library.globalConcepts.all { it.isGlobal && !it.canEdit && !it.canHide })
    assertEquals(library.concepts.size, library.totalConceptCount)
    // Global templates and packs can be hidden (not edited) by this organization's Managers.
    val globalTemplate = library.formTemplates.first { it.isGlobal }
    assertTrue(globalTemplate.canHide && !globalTemplate.canEdit && !globalTemplate.isHidden)
    assertEquals(
      "org.org-kenya-forest-service.coop_member_plot_audit",
      library.formTemplates.first().entry.id,
    )
    assertEquals(
      "Coop member plot audit",
      library.templateTitles[library.formTemplates.first().entry.id],
    )
  }

  @Test
  fun library_ofAllUsers_isTheEditableGlobalLibrary_forPlatformAdminsOnly() {
    val f = Fixture()
    f.viewModel.openOrganization(Organization.ALL_USERS_ID)
    val library = f.uiState.library
    assertTrue(library.isGlobalLibrary)
    assertTrue(library.canEdit) // The signed-in demo user is an "All users" Manager.
    assertTrue(library.globalConcepts.isEmpty())
    assertTrue(library.organizationConcepts.all { it.isGlobal && it.canEdit })
    assertTrue(library.formTemplates.all { it.canEdit && !it.canHide })
    assertEquals("eudr.commodity", library.newConceptId("commodity", "eudr"))
  }

  @Test
  fun library_isReadOnlyForNonManagers() {
    val f = Fixture()
    val mekong = PrototypeFakeOrganizationsData.MEKONG_MANGROVE_ALLIANCE
    f.viewModel.openOrganization(mekong)
    val library = f.uiState.library
    assertFalse(library.canEdit)
    assertTrue(library.formTemplates.none { it.canEdit || it.canHide })
    assertNotNull(f.viewModel.renameTemplate(mekong, "eudr_plot_registration", "Mine", ""))
    f.viewModel.setGlobalEntryHidden(mekong, "eudr_plot_registration", true)
    assertTrue(f.uiState.library.formTemplates.none { it.isHidden })
  }

  @Test
  fun dictionaryQuery_filtersConceptsAcrossLanguages() {
    val f = Fixture()
    f.viewModel.openOrganization(f.kfs.id)
    f.viewModel.setDictionaryQuery("cultivo")
    assertEquals("eudr.commodity", f.uiState.library.concepts.first().entry.id)
    assertTrue(f.uiState.library.concepts.size < f.uiState.library.totalConceptCount)
    f.viewModel.setDictionaryQuery("coffee cherry")
    assertEquals(
      "org.org-kenya-forest-service.cherry_delivery_kg",
      f.uiState.library.concepts.first().entry.id,
    )
    f.viewModel.setDictionaryQuery("")
    assertEquals(f.uiState.library.totalConceptCount, f.uiState.library.concepts.size)
  }

  @Test
  fun saveConcept_validatesThenStoresIt_andDeleteOnlyRemovesDrafts() {
    val f = Fixture()
    f.viewModel.openOrganization(f.kfs.id)
    val id = f.uiState.library.newConceptId(f.uiState.library.suggestedName("Drying days"))
    assertEquals("org.org-kenya-forest-service.drying_days", id)
    val concept =
      LibraryConcept(id, f.kfs.id, LocalizedText.en("Drying days"), ConceptDataType.INTEGER)
    assertNotNull(f.viewModel.saveConcept(concept.copy(label = LocalizedText()), isNew = true))
    assertNotNull(f.viewModel.saveConcept(concept.copy(id = "core.drying_days"), isNew = true))
    assertNull(f.viewModel.saveConcept(concept, isNew = true))
    assertTrue(f.uiState.library.organizationConcepts.any { it.entry.id == id })

    assertNotNull(
      f.viewModel.deleteConcept(f.kfs.id, "org.org-kenya-forest-service.cherry_delivery_kg")
    )
    assertNull(f.viewModel.deleteConcept(f.kfs.id, id))
    assertTrue(f.uiState.library.organizationConcepts.none { it.entry.id == id })
  }

  @Test
  fun hidingAGlobalTemplate_marksItHidden_andCanBeUndone() {
    val f = Fixture()
    f.viewModel.openOrganization(f.kfs.id)
    f.viewModel.setGlobalEntryHidden(f.kfs.id, "ferm_monitoring_wave", true)
    assertTrue(
      f.uiState.library.formTemplates.first { it.entry.id == "ferm_monitoring_wave" }.isHidden
    )
    f.viewModel.setGlobalEntryHidden(f.kfs.id, "ferm_monitoring_wave", false)
    assertFalse(
      f.uiState.library.formTemplates.first { it.entry.id == "ferm_monitoring_wave" }.isHidden
    )
  }

  @Test
  fun renameAndDelete_organizationTemplatesAndPacks() {
    val f = Fixture()
    val templateId = "org.org-kenya-forest-service.coop_member_plot_audit"
    val packId = "org.org-kenya-forest-service.coop_certification_audit"
    f.viewModel.openOrganization(f.kfs.id)
    assertEquals("Enter a title.", f.viewModel.renameTemplate(f.kfs.id, templateId, " ", ""))
    assertNull(f.viewModel.renameTemplate(f.kfs.id, templateId, "Plot audit", ""))
    assertEquals("Plot audit", f.uiState.library.templateTitles[templateId])
    assertNull(f.viewModel.renamePurposePack(f.kfs.id, packId, "Certification", "Yearly"))
    assertEquals(
      "Certification",
      f.uiState.library.purposePacks.first { it.entry.id == packId }.entry.title.text,
    )
    f.viewModel.deleteTemplate(f.kfs.id, templateId)
    f.viewModel.deletePurposePack(f.kfs.id, packId)
    assertTrue(f.uiState.library.formTemplates.none { it.entry.id == templateId })
    assertTrue(f.uiState.library.purposePacks.none { it.entry.id == packId })
  }

  @Test
  fun allUsers_onlyInvitesManagers_andRefusesDemotionWithANotice() {
    val f = Fixture()
    val allUsers = Organization.ALL_USERS_ID
    assertNotNull(f.viewModel.inviteMember(allUsers, "new@example.org", OrganizationRole.MEMBER))
    assertNull(f.viewModel.inviteMember(allUsers, "new@example.org", OrganizationRole.MANAGER))
    f.viewModel.setMemberRole(allUsers, f.me, OrganizationRole.MEMBER)
    assertTrue(f.uiState.notice!!.startsWith("Everyone is already a member"))
  }
}
