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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.groundplatform.v2.devtools.prototypeapp.domain.model.AuthProfile
import org.groundplatform.v2.devtools.prototypeapp.domain.model.CachedProfile
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Countries
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImagerySourceType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryConcept
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryIds
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationLibrary
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationRole
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.AuthRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.LibraryRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.OrganizationRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.CreateOrganizationUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.InviteOrganizationMemberUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ManageImagerySourcesUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ManageLibraryUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ResolveLibraryUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.SearchConceptsUseCase
import org.groundplatform.v2.devtools.prototypeapp.ui.state.LibraryEntryRow
import org.groundplatform.v2.devtools.prototypeapp.ui.state.OrganizationEvent
import org.groundplatform.v2.devtools.prototypeapp.ui.state.OrganizationLibraryUiState
import org.groundplatform.v2.devtools.prototypeapp.ui.state.OrganizationUiState

/**
 * User intents of the web organizations directory and organization page. Implemented by
 * [OrganizationViewModel].
 */
interface OrganizationActions {
  // --- Navigation & notices ---

  /** Shows [organizationId] on the organization page. */
  fun openOrganization(organizationId: String)

  /** Shows the organizations directory. */
  fun openOrganizations()

  fun dismissNotice()

  // --- Organization lifecycle ---

  /**
   * Creates an organization managed by the signed-in user, opens it once it's stored, and returns
   * its ID. [organizationType] and the ISO 3166-1 alpha-2 [countryCode] are optional (`null` or
   * blank means not specified); an unknown country code stores nothing and shows a notice instead.
   */
  fun createOrganization(
    name: String,
    description: String,
    isListed: Boolean,
    organizationType: OrganizationType? = null,
    countryCode: String? = null,
  ): String

  /**
   * Saves the organization's profile fields. Only Managers may call this. A blank [countryCode]
   * clears the country; an unknown one saves nothing and shows a notice instead. Type and country
   * are ignored for the synthetic `"All users"` organization.
   */
  fun updateOrganizationDetails(
    organizationId: String,
    name: String,
    description: String,
    websiteUrl: String,
    isListed: Boolean,
    organizationType: OrganizationType?,
    countryCode: String?,
  )

  /** Deletes the organization; its surveys become personal surveys. Returns to the directory. */
  fun deleteOrganization(organizationId: String)

  // --- Membership ---

  /**
   * Invites [email] to [organizationId] with [role]. Returns an error message for an invalid or
   * already-present address, or `null` when the invite was sent.
   */
  fun inviteMember(organizationId: String, email: String, role: OrganizationRole): String?

  /** Issues a new invite link for a pending invite, invalidating the old one. */
  fun resetInviteLink(organizationId: String, email: String)

  /**
   * Simulates the invitee opening their link and accepting. Returns an error message, or `null`.
   */
  fun acceptInvite(
    organizationId: String,
    email: String,
    displayName: String,
    photoUrl: String?,
  ): String?

  /** Asks to join a listed organization as the signed-in user. */
  fun requestToJoin(organizationId: String)

  fun approveRequest(organizationId: String, email: String)

  /** Changes a member's role. Refusals (demoting the last Manager) surface as a notice. */
  fun setMemberRole(organizationId: String, email: String, role: OrganizationRole)

  /**
   * Removes a member, declines a join request, or revokes an invite. Refusals (removing the last
   * Manager) surface as a notice. Removing yourself returns to the directory.
   */
  fun removeMember(organizationId: String, email: String)

  // --- Imagery sources ---

  /** Adds an imagery source to [organizationId]. Returns a validation error message, or `null`. */
  fun addImagerySource(
    organizationId: String,
    name: String,
    urlTemplate: String,
    type: ImagerySourceType = ImagerySourceType.XYZ_TILES,
    allowOfflineDownload: Boolean = false,
  ): String?

  /** Edits an imagery source of [organizationId]. Returns a validation error message, or `null`. */
  fun updateImagerySource(
    organizationId: String,
    sourceId: String,
    name: String,
    urlTemplate: String,
    allowOfflineDownload: Boolean,
  ): String?

  /** Toggles whether offline download on mobile is permitted for [sourceId] in [organizationId]. */
  fun setImagerySourceOfflineAllowed(
    organizationId: String,
    sourceId: String,
    allowOfflineDownload: Boolean,
  )

  /** Removes the imagery source with [sourceId] from [organizationId]. */
  fun removeImagerySource(organizationId: String, sourceId: String)

  // --- Library (Dictionary, Templates, and Purposes tabs) ---

  /** Filters the Dictionary tab by [query] (labels and keywords in every language). */
  fun setDictionaryQuery(query: String)

  /**
   * Creates ([isNew]) or updates [concept] in its organization's library. Returns a validation
   * error message, or `null` when it was saved. Only Managers may call this.
   */
  fun saveConcept(concept: LibraryConcept, isNew: Boolean): String?

  /** Deletes a draft concept. Returns an error message (e.g. it isn't a draft), or `null`. */
  fun deleteConcept(organizationId: String, conceptId: String): String?

  /** Renames a template. Returns a validation error message, or `null`. */
  fun renameTemplate(
    organizationId: String,
    templateId: String,
    title: String,
    description: String,
  ): String?

  /** Deletes a template and removes it from the organization's Purpose Packs. */
  fun deleteTemplate(organizationId: String, templateId: String)

  /** Renames a Purpose Pack. Returns a validation error message, or `null`. */
  fun renamePurposePack(
    organizationId: String,
    packId: String,
    title: String,
    description: String,
  ): String?

  /** Deletes a Purpose Pack. */
  fun deletePurposePack(organizationId: String, packId: String)

  /** Hides or shows a global template or Purpose Pack in [organizationId]'s pickers. */
  fun setGlobalEntryHidden(organizationId: String, globalEntryId: String, hidden: Boolean)
}

/**
 * ViewModel of the web organizations directory (`#organizations`) and organization page
 * (`#organization/<id>`: details, surveys, members, imagery sources, and the library's Purposes,
 * Dictionary, and Templates).
 *
 * Organizations come from [OrganizationRepository], libraries from [LibraryRepository], surveys
 * from [SurveyRepository], and the signed-in user from [AuthRepository]. Creating an organization
 * runs through [CreateOrganizationUseCase], invites through [InviteOrganizationMemberUseCase],
 * imagery sources through [ManageImagerySourcesUseCase], and library edits through
 * [ManageLibraryUseCase]; the Dictionary tab lists concepts from [ResolveLibraryUseCase] filtered
 * by [SearchConceptsUseCase]. Simple membership changes pass straight to the repository. Page
 * navigation and map side effects are published as [OrganizationEvent]s for the app shell.
 */
class OrganizationViewModel(
  private val organizationRepository: OrganizationRepository,
  surveyRepository: SurveyRepository,
  authRepository: AuthRepository,
  private val createOrganizationUseCase: CreateOrganizationUseCase,
  private val inviteMemberUseCase: InviteOrganizationMemberUseCase,
  private val manageImagerySourcesUseCase: ManageImagerySourcesUseCase,
  libraryRepository: LibraryRepository,
  private val manageLibraryUseCase: ManageLibraryUseCase,
  private val resolveLibraryUseCase: ResolveLibraryUseCase,
  private val searchConceptsUseCase: SearchConceptsUseCase,
  private val scope: CoroutineScope,
) : OrganizationActions {
  /** Everything the organization pages read from the local data store. */
  private data class Data(
    val organizations: List<Organization> = emptyList(),
    val surveys: List<SurveyPreviewItem> = emptyList(),
    val activeSurveyId: String = "",
    val isSignedIn: Boolean = false,
    val profile: AuthProfile = AuthProfile("", "", ""),
    val libraries: Map<String, OrganizationLibrary> = emptyMap(),
  )

  /** Session (non-persisted) state of the organization pages. */
  private data class Session(
    val openOrganizationId: String? = null,
    val notice: String? = null,
    val dictionaryQuery: String = "",
  )

  private val data: StateFlow<Data> =
    combine(
        organizationRepository.observeOrganizations(),
        surveyRepository.observeSurveys(),
        surveyRepository.observeActiveSurveyId(),
        authRepository.observeSession(),
        libraryRepository.observeLibraries(),
      ) { organizations, surveys, activeSurveyId, auth, libraries ->
        Data(
          organizations = organizations,
          surveys = surveys,
          activeSurveyId = activeSurveyId,
          isSignedIn = auth.isSignedIn,
          profile = auth.profile,
          libraries = libraries,
        )
      }
      .stateIn(scope, SharingStarted.Eagerly, Data())

  private val session = MutableStateFlow(Session())

  private val _events =
    MutableSharedFlow<OrganizationEvent>(
      extraBufferCapacity = 64,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

  /** Outcomes of organization actions that the app shell applies outside this slice. */
  val events: Flow<OrganizationEvent> = _events.asSharedFlow()

  val uiState: StateFlow<OrganizationUiState> =
    combine(data, session, ::buildUiState)
      .stateIn(scope, SharingStarted.Eagerly, OrganizationUiState())

  private fun buildUiState(data: Data, session: Session) =
    OrganizationUiState(
      organizations = data.organizations,
      surveys = data.surveys,
      activeSurveyId = data.activeSurveyId,
      isSignedIn = data.isSignedIn,
      signedInUserEmail = data.profile.email,
      signedInUserName = data.profile.displayName,
      openOrganizationId = session.openOrganizationId,
      notice = session.notice,
      library = buildLibraryState(data, session),
    )

  /** Library tabs of the open organization: its own entries first, then global ones. */
  private fun buildLibraryState(data: Data, session: Session): OrganizationLibraryUiState {
    val organization =
      data.organizations.firstOrNull { it.id == session.openOrganizationId }
        ?: return OrganizationLibraryUiState(dictionaryQuery = session.dictionaryQuery)
    val isGlobalLibrary = LibraryIds.isGlobalLibrary(organization.id)
    val canEdit = data.isSignedIn && manageLibraryUseCase.canEdit(organization, data.profile.email)
    val global =
      data.libraries[Organization.ALL_USERS_ID] ?: OrganizationLibrary(Organization.ALL_USERS_ID)
    val own = data.libraries[organization.id] ?: OrganizationLibrary(organization.id)
    val hidden = if (isGlobalLibrary) emptySet() else own.settings.hiddenGlobalEntryIds
    // Global entries are editable only on "All users"; other organizations can only hide them.
    fun <T> row(entry: T, isGlobal: Boolean, hideable: Boolean, id: String) =
      LibraryEntryRow(
        entry = entry,
        isGlobal = isGlobal,
        canEdit = canEdit && isGlobal == isGlobalLibrary,
        canHide = canEdit && hideable && isGlobal && !isGlobalLibrary,
        isHidden = isGlobal && id in hidden,
      )
    // Concepts: the resolved library (organization concepts, then global ones).
    val resolved =
      resolveLibraryUseCase(global = global, organization = own.takeUnless { isGlobalLibrary })
    val matches =
      searchConceptsUseCase(
        resolved.concepts,
        session.dictionaryQuery,
        SearchConceptsUseCase.SearchOptions(
          limit = Int.MAX_VALUE,
          includeDeprecated = true,
          organizationId = organization.id.takeUnless { isGlobalLibrary },
        ),
      )
    // Templates and packs: hidden global entries stay listed so they can be shown again.
    val ownTemplates = if (isGlobalLibrary) emptyList() else own.formTemplates
    val ownPacks = if (isGlobalLibrary) emptyList() else own.purposePacks
    return OrganizationLibraryUiState(
      organizationId = organization.id,
      isGlobalLibrary = isGlobalLibrary,
      canEdit = canEdit,
      dictionaryQuery = session.dictionaryQuery,
      concepts =
        matches.map { row(it.concept, it.concept.isGlobal, hideable = false, it.concept.id) },
      totalConceptCount = resolved.concepts.size,
      formTemplates =
        ownTemplates.map { row(it, isGlobal = false, hideable = false, it.id) } +
          global.formTemplates.map { row(it, isGlobal = true, hideable = true, it.id) },
      purposePacks =
        ownPacks.map { row(it, isGlobal = false, hideable = false, it.id) } +
          global.purposePacks.map { row(it, isGlobal = true, hideable = true, it.id) },
      templateTitles = (ownTemplates + global.formTemplates).associate { it.id to it.title.text },
    )
  }

  /** Returns session state to its defaults (used by the prototype's Reset). */
  fun reset() {
    session.value = Session()
  }

  private fun organization(organizationId: String): Organization? =
    data.value.organizations.firstOrNull { it.id == organizationId }

  private val signedInEmail: String
    get() = data.value.profile.email

  /** The signed-in user's cached profile, as recorded on memberships they create or request. */
  private val signedInProfile: CachedProfile
    get() = CachedProfile(data.value.profile.displayName, "avatar:2", "")

  private fun returnToOrganizations(notice: String?) {
    session.update { it.copy(openOrganizationId = null, notice = notice) }
    _events.tryEmit(OrganizationEvent.OrganizationsOpened)
  }

  // --- Navigation & notices ---

  override fun openOrganization(organizationId: String) {
    session.update { it.copy(openOrganizationId = organizationId, notice = null) }
    _events.tryEmit(OrganizationEvent.OrganizationOpened(organizationId))
  }

  override fun openOrganizations() {
    session.update { it.copy(notice = null) }
    _events.tryEmit(OrganizationEvent.OrganizationsOpened)
  }

  override fun dismissNotice() {
    session.update { it.copy(notice = null) }
  }

  // --- Organization lifecycle ---

  override fun createOrganization(
    name: String,
    description: String,
    isListed: Boolean,
    organizationType: OrganizationType?,
    countryCode: String?,
  ): String {
    val organization =
      createOrganizationUseCase.newOrganization(
        name = name,
        description = description,
        isListed = isListed,
        existingOrganizations = data.value.organizations,
        organizationType = organizationType,
        countryCode = countryCode,
      )
    scope.launch {
      if (createOrganizationUseCase(organization, signedInEmail, signedInProfile) == null) {
        val reason =
          if (CreateOrganizationUseCase.countryCodeError(organization.countryCode) != null) {
            " Choose a country from the list."
          } else {
            ""
          }
        session.update { it.copy(notice = "Couldn't create \"${organization.name}\".$reason") }
        return@launch
      }
      // Open it only once it's in the store, so the organization page never sees it missing.
      openOrganization(organization.id)
      session.update { it.copy(notice = "Created organization \"${organization.name}\".") }
    }
    return organization.id
  }

  /** Applies [transform] to the stored organization with [organizationId]. */
  fun updateOrganization(organizationId: String, transform: (Organization) -> Organization) {
    scope.launch { organizationRepository.updateOrganization(organizationId, transform) }
  }

  override fun updateOrganizationDetails(
    organizationId: String,
    name: String,
    description: String,
    websiteUrl: String,
    isListed: Boolean,
    organizationType: OrganizationType?,
    countryCode: String?,
  ) {
    if (CreateOrganizationUseCase.countryCodeError(countryCode) != null) {
      session.update { it.copy(notice = "Couldn't save. Choose a country from the list.") }
      return
    }
    updateOrganization(organizationId) {
      it.copy(
        name = name.trim(),
        description = description.trim(),
        websiteUrl = websiteUrl.trim(),
        isListed = isListed,
        organizationType = organizationType,
        countryCode = Countries.normalizeCode(countryCode),
      )
    }
  }

  override fun deleteOrganization(organizationId: String) {
    val name = organization(organizationId)?.name
    scope.launch { organizationRepository.deleteOrganization(organizationId) }
    returnToOrganizations(notice = name?.let { "Deleted organization \"$it\"." })
  }

  // --- Membership ---

  override fun inviteMember(
    organizationId: String,
    email: String,
    role: OrganizationRole,
  ): String? {
    inviteMemberUseCase.inviteError(organization(organizationId), email, role)?.let {
      return it
    }
    scope.launch { inviteMemberUseCase(organizationId, email, role) }
    return null
  }

  override fun resetInviteLink(organizationId: String, email: String) {
    scope.launch { inviteMemberUseCase.resetInviteLink(organizationId, email) }
  }

  override fun acceptInvite(
    organizationId: String,
    email: String,
    displayName: String,
    photoUrl: String?,
  ): String? {
    inviteMemberUseCase.acceptError(displayName)?.let {
      return it
    }
    scope.launch { inviteMemberUseCase.acceptInvite(organizationId, email, displayName, photoUrl) }
    return null
  }

  override fun requestToJoin(organizationId: String) {
    scope.launch {
      organizationRepository.requestToJoin(organizationId, signedInEmail, signedInProfile)
    }
  }

  override fun approveRequest(organizationId: String, email: String) {
    scope.launch { organizationRepository.approveRequest(organizationId, email) }
  }

  override fun setMemberRole(organizationId: String, email: String, role: OrganizationRole) {
    scope.launch {
      val updated = organizationRepository.setMemberRole(organizationId, email, role)
      if (updated == null) {
        val organization = organization(organizationId)
        val notice =
          if (organization?.isSynthetic == true && role != OrganizationRole.MANAGER) {
            "Everyone is already a member of ${organization.name}. Remove the Manager instead."
          } else {
            "An organization needs at least one Manager. Make someone else a Manager first."
          }
        session.update { it.copy(notice = notice) }
      }
    }
  }

  override fun removeMember(organizationId: String, email: String) {
    val isSelf = email.equals(signedInEmail, ignoreCase = true)
    scope.launch {
      val updated = organizationRepository.removeMember(organizationId, email)
      if (updated == null) {
        session.update {
          it.copy(
            notice =
              "An organization needs at least one Manager. Make someone else a Manager before " +
                "leaving."
          )
        }
      } else if (isSelf) {
        returnToOrganizations(notice = "You left \"${updated.name}\".")
      }
    }
  }

  // --- Imagery sources ---

  override fun addImagerySource(
    organizationId: String,
    name: String,
    urlTemplate: String,
    type: ImagerySourceType,
    allowOfflineDownload: Boolean,
  ): String? {
    manageImagerySourcesUseCase.validationError(name, urlTemplate, type)?.let {
      return it
    }
    val organization = organization(organizationId) ?: return "Organization not found."
    val source =
      manageImagerySourcesUseCase.newSource(
        organization = organization,
        allOrganizations = data.value.organizations,
        name = name,
        urlTemplate = urlTemplate,
        type = type,
        allowOfflineDownload = allowOfflineDownload,
      )
    scope.launch { manageImagerySourcesUseCase.add(organizationId, source) }
    return null
  }

  override fun updateImagerySource(
    organizationId: String,
    sourceId: String,
    name: String,
    urlTemplate: String,
    allowOfflineDownload: Boolean,
  ): String? {
    manageImagerySourcesUseCase
      .validationError(name, urlTemplate, ImagerySourceType.XYZ_TILES)
      ?.let {
        return it
      }
    scope.launch {
      manageImagerySourcesUseCase.update(
        organizationId,
        sourceId,
        name,
        urlTemplate,
        allowOfflineDownload,
      )
    }
    return null
  }

  override fun setImagerySourceOfflineAllowed(
    organizationId: String,
    sourceId: String,
    allowOfflineDownload: Boolean,
  ) {
    scope.launch {
      manageImagerySourcesUseCase.setOfflineAllowed(organizationId, sourceId, allowOfflineDownload)
    }
  }

  override fun removeImagerySource(organizationId: String, sourceId: String) {
    _events.tryEmit(OrganizationEvent.ImagerySourceRemoved(sourceId))
    scope.launch { manageImagerySourcesUseCase.remove(organizationId, sourceId) }
  }

  // --- Library ---

  /** Why the signed-in user can't edit [organizationId]'s library, or `null` if they can. */
  private fun libraryEditError(organizationId: String): String? {
    val organization = organization(organizationId) ?: return "Organization not found."
    return if (data.value.isSignedIn && manageLibraryUseCase.canEdit(organization, signedInEmail)) {
      null
    } else {
      "Only Managers of ${organization.name} can change its library."
    }
  }

  private fun library(organizationId: String): OrganizationLibrary =
    data.value.libraries[organizationId] ?: OrganizationLibrary(organizationId)

  override fun setDictionaryQuery(query: String) {
    session.update { it.copy(dictionaryQuery = query) }
  }

  override fun saveConcept(concept: LibraryConcept, isNew: Boolean): String? {
    libraryEditError(concept.organizationId)?.let {
      return it
    }
    manageLibraryUseCase.conceptError(concept, library(concept.organizationId), isNew)?.let {
      return it
    }
    scope.launch { manageLibraryUseCase.saveConcept(concept) }
    return null
  }

  override fun deleteConcept(organizationId: String, conceptId: String): String? {
    libraryEditError(organizationId)?.let {
      return it
    }
    manageLibraryUseCase.deleteConceptError(library(organizationId), conceptId)?.let {
      return it
    }
    scope.launch { manageLibraryUseCase.deleteConcept(organizationId, conceptId) }
    return null
  }

  override fun renameTemplate(
    organizationId: String,
    templateId: String,
    title: String,
    description: String,
  ): String? {
    (libraryEditError(organizationId) ?: manageLibraryUseCase.titleError(title))?.let {
      return it
    }
    scope.launch {
      manageLibraryUseCase.renameTemplate(organizationId, templateId, title, description)
    }
    return null
  }

  override fun deleteTemplate(organizationId: String, templateId: String) {
    if (libraryEditError(organizationId) != null) return
    scope.launch { manageLibraryUseCase.deleteTemplate(organizationId, templateId) }
  }

  override fun renamePurposePack(
    organizationId: String,
    packId: String,
    title: String,
    description: String,
  ): String? {
    (libraryEditError(organizationId) ?: manageLibraryUseCase.titleError(title))?.let {
      return it
    }
    scope.launch {
      manageLibraryUseCase.renamePurposePack(organizationId, packId, title, description)
    }
    return null
  }

  override fun deletePurposePack(organizationId: String, packId: String) {
    if (libraryEditError(organizationId) != null) return
    scope.launch { manageLibraryUseCase.deletePurposePack(organizationId, packId) }
  }

  override fun setGlobalEntryHidden(
    organizationId: String,
    globalEntryId: String,
    hidden: Boolean,
  ) {
    if (libraryEditError(organizationId) != null) return
    scope.launch {
      manageLibraryUseCase.setGlobalEntryHidden(organizationId, globalEntryId, hidden)
    }
  }
}
