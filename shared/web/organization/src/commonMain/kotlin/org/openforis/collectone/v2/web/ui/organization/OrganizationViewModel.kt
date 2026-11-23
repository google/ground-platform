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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
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
import org.openforis.collectone.v2.app.domain.model.AuthProfile
import org.openforis.collectone.v2.app.domain.model.CachedProfile
import org.openforis.collectone.v2.app.domain.model.Countries
import org.openforis.collectone.v2.app.domain.model.FormTemplate
import org.openforis.collectone.v2.app.domain.model.ImagerySourceType
import org.openforis.collectone.v2.app.domain.model.ImpactPillar
import org.openforis.collectone.v2.app.domain.model.LibraryConcept
import org.openforis.collectone.v2.app.domain.model.LibraryStatus
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.OrganizationLibrary
import org.openforis.collectone.v2.app.domain.model.OrganizationRole
import org.openforis.collectone.v2.app.domain.model.OrganizationType
import org.openforis.collectone.v2.app.domain.model.QuotaLimitsConfig
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.repository.AuthRepository
import org.openforis.collectone.v2.app.domain.repository.LibraryRepository
import org.openforis.collectone.v2.app.domain.repository.OrganizationRepository
import org.openforis.collectone.v2.app.domain.repository.SurveyRepository
import org.openforis.collectone.v2.app.domain.usecase.ResolveLibraryUseCase
import org.openforis.collectone.v2.app.ui.viewmodel.ViewModelDispatcher
import org.openforis.collectone.v2.app.ui.viewmodel.viewModelScopeOn
import org.openforis.collectone.v2.web.domain.usecase.CreateOrganizationUseCase
import org.openforis.collectone.v2.web.domain.usecase.InviteOrganizationMemberUseCase
import org.openforis.collectone.v2.web.domain.usecase.ManageImagerySourcesUseCase
import org.openforis.collectone.v2.web.domain.usecase.ManageLibraryUseCase
import org.openforis.collectone.v2.web.domain.usecase.SearchConceptsUseCase
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.org_error_not_found
import org.openforis.collectone.v2.web.resources.org_notice_create_failed
import org.openforis.collectone.v2.web.resources.org_notice_create_failed_country
import org.openforis.collectone.v2.web.resources.org_notice_created
import org.openforis.collectone.v2.web.resources.org_notice_deleted
import org.openforis.collectone.v2.web.resources.org_notice_last_manager_leave
import org.openforis.collectone.v2.web.resources.org_notice_last_manager_role
import org.openforis.collectone.v2.web.resources.org_notice_left
import org.openforis.collectone.v2.web.resources.org_notice_role_all_users
import org.openforis.collectone.v2.web.resources.org_notice_save_failed_country
import org.openforis.collectone.v2.web.ui.text.OrganizationMessage
import org.openforis.collectone.v2.web.ui.text.organizationMessage
import org.openforis.collectone.v2.web.ui.text.toOrganizationMessage

/**
 * User intents of the web organizations directory and organization page. Implemented by
 * [OrganizationViewModel].
 */
interface OrganizationActions : OrganizationLibraryActions {
  // --- Navigation & notices ---

  /** Shows [organizationId] on the organization page. */
  fun openOrganization(organizationId: String)

  /** Shows the platform admin panel. */
  fun openAdminPanel()

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
   * clears the country; an unknown one saves nothing and shows a notice instead. A null or blank
   * [logoUrl] clears the organization's logo so it falls back to its letter badge. Type, country,
   * and logo are ignored for the synthetic `"All users"` organization.
   */
  fun updateOrganizationDetails(
    organizationId: String,
    name: String,
    description: String,
    websiteUrl: String,
    isListed: Boolean,
    organizationType: OrganizationType?,
    countryCode: String?,
    logoUrl: String? = null,
  )

  /**
   * Keeps organization [organizationId]'s data out of platform-wide impact numbers when [exclude]
   * is true (`Organization.exclude_from_platform_aggregates`). Only Managers may call this; ignored
   * for the synthetic `"All users"` organization.
   */
  fun setExcludeFromPlatformAggregates(organizationId: String, exclude: Boolean)

  /** Updates fine-grained cost and quota limits for [organizationId]. */
  fun updateOrganizationQuotaLimits(organizationId: String, limits: QuotaLimitsConfig)

  /** Deletes the organization; its surveys become personal surveys. Returns to the directory. */
  fun deleteOrganization(organizationId: String)

  // --- Membership ---

  /**
   * Invites [email] to [organizationId] with [role]. Returns an error message for an invalid or
   * already-present address, or `null` when the invite was sent.
   */
  fun inviteMember(
    organizationId: String,
    email: String,
    role: OrganizationRole,
  ): OrganizationMessage?

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
  ): OrganizationMessage?

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
    urlTemplate: String = "",
    type: ImagerySourceType = ImagerySourceType.XYZ_TILES,
    allowOfflineDownload: Boolean = false,
    expression: google.earthengine.v1main.Expression? = null,
  ): OrganizationMessage?

  /** Edits an imagery source of [organizationId]. Returns a validation error message, or `null`. */
  fun updateImagerySource(
    organizationId: String,
    sourceId: String,
    name: String,
    urlTemplate: String,
    allowOfflineDownload: Boolean,
  ): OrganizationMessage?

  /** Toggles whether offline download on mobile is permitted for [sourceId] in [organizationId]. */
  fun setImagerySourceOfflineAllowed(
    organizationId: String,
    sourceId: String,
    allowOfflineDownload: Boolean,
  )

  /** Removes the imagery source with [sourceId] from [organizationId]. */
  fun removeImagerySource(organizationId: String, sourceId: String)
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
 * [ManageLibraryUseCase] (in [OrganizationLibraryEditor], which also derives the library tabs: the
 * Dictionary lists concepts from [ResolveLibraryUseCase] filtered by [SearchConceptsUseCase]).
 * Simple membership changes pass straight to the repository. Page navigation and map side effects
 * are published as [OrganizationEvent]s for the app shell.
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
  dispatcher: CoroutineDispatcher = ViewModelDispatcher,
) : ViewModel(viewModelScopeOn(dispatcher)), OrganizationActions {
  /** Everything the organization pages read from the local data store. */
  private data class Data(
    val organizations: List<Organization> = emptyList(),
    val surveys: List<SurveyPreviewItem> = emptyList(),
    val activeSurveyId: String = "",
    val isSignedIn: Boolean = false,
    val profile: AuthProfile = AuthProfile("", "", ""),
    val libraries: Map<String, OrganizationLibrary> = emptyMap(),
  ) {
    fun toLibraryData() =
      OrganizationLibraryEditor.LibraryData(
        organizations = organizations,
        libraries = libraries,
        isSignedIn = isSignedIn,
        signedInEmail = profile.email,
      )
  }

  /** Session (non-persisted) state of the organization pages. */
  private data class Session(
    val openOrganizationId: String? = null,
    val notice: OrganizationMessage? = null,
    val dictionaryQuery: String = "",
    val isSaving: Boolean = false,
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
      .stateIn(viewModelScope, SharingStarted.Eagerly, Data())

  private val session = MutableStateFlow(Session())

  private val libraryEditor =
    OrganizationLibraryEditor(
      manageLibraryUseCase = manageLibraryUseCase,
      resolveLibraryUseCase = resolveLibraryUseCase,
      searchConceptsUseCase = searchConceptsUseCase,
      data = { data.value.toLibraryData() },
      onDictionaryQueryChange = { query -> session.update { it.copy(dictionaryQuery = query) } },
      launchSaving = ::launchSaving,
    )

  private val _events =
    MutableSharedFlow<OrganizationEvent>(
      extraBufferCapacity = 64,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

  /** Outcomes of organization actions that the app shell applies outside this slice. */
  val events: Flow<OrganizationEvent> = _events.asSharedFlow()

  val uiState: StateFlow<OrganizationUiState> =
    combine(data, session, ::buildUiState)
      .stateIn(viewModelScope, SharingStarted.Eagerly, OrganizationUiState())

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
      library =
        libraryEditor.uiState(
          data.toLibraryData(),
          session.openOrganizationId,
          session.dictionaryQuery,
        ),
      isSaving = session.isSaving,
    )

  /**
   * Runs [block] on [viewModelScope] while toggling [Session.isSaving] so the UI can disable
   * duplicate submit actions while a repository write is in flight.
   */
  private fun launchSaving(block: suspend () -> Unit): Job = viewModelScope.launch {
    session.update { it.copy(isSaving = true) }
    try {
      block()
    } finally {
      session.update { it.copy(isSaving = false) }
    }
  }

  /** Returns session state to its defaults (used by session resets). */
  fun reset() {
    session.value = Session()
  }

  private fun organization(organizationId: String): Organization? =
    data.value.organizations.firstOrNull { it.id == organizationId }

  private val signedInEmail: String
    get() = data.value.profile.email

  /** The signed-in user's cached profile, as recorded on memberships they create or request. */
  private val signedInProfile: CachedProfile
    get() =
      CachedProfile(
        data.value.profile.displayName,
        data.value.profile.photoUrl ?: "avatar:2",
        "",
      )

  /**
   * Clears the open organization, posts an optional feedback [notice] banner on the Organizations
   * list, and emits [OrganizationEvent.OrganizationsOpened] so the router updates the browser URL.
   */
  private fun returnToOrganizations(notice: OrganizationMessage?) {
    session.update { it.copy(openOrganizationId = null, notice = notice) }
    _events.tryEmit(OrganizationEvent.OrganizationsOpened)
  }

  // --- Navigation & notices ---

  override fun openAdminPanel() {
    session.update { it.copy(openOrganizationId = Organization.ALL_USERS_ID, notice = null) }
    _events.tryEmit(OrganizationEvent.AdminPanelOpened)
  }

  override fun openOrganization(organizationId: String) {
    if (organizationId == Organization.ALL_USERS_ID) {
      openAdminPanel()
      return
    }
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
    launchSaving {
      if (createOrganizationUseCase(organization, signedInEmail, signedInProfile) == null) {
        val notice =
          if (CreateOrganizationUseCase.countryCodeError(organization.countryCode) != null) {
            organizationMessage(Res.string.org_notice_create_failed_country, organization.name)
          } else {
            organizationMessage(Res.string.org_notice_create_failed, organization.name)
          }
        session.update { it.copy(notice = notice) }
        return@launchSaving
      }
      // Open it only once it's in the store, so the organization page never sees it missing.
      openOrganization(organization.id)
      session.update {
        it.copy(notice = organizationMessage(Res.string.org_notice_created, organization.name))
      }
    }
    return organization.id
  }

  /** Applies [transform] to the stored organization with [organizationId]. */
  fun updateOrganization(organizationId: String, transform: (Organization) -> Organization) {
    launchSaving { organizationRepository.updateOrganization(organizationId, transform) }
  }

  override fun updateOrganizationDetails(
    organizationId: String,
    name: String,
    description: String,
    websiteUrl: String,
    isListed: Boolean,
    organizationType: OrganizationType?,
    countryCode: String?,
    logoUrl: String?,
  ) {
    if (CreateOrganizationUseCase.countryCodeError(countryCode) != null) {
      session.update {
        it.copy(notice = organizationMessage(Res.string.org_notice_save_failed_country))
      }
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
        logoUrl = if (it.isSynthetic) null else logoUrl?.trim()?.takeIf { url -> url.isNotEmpty() },
      )
    }
  }

  override fun setExcludeFromPlatformAggregates(organizationId: String, exclude: Boolean) {
    val organization = organization(organizationId) ?: return
    if (organization.isSynthetic || !organization.isManager(signedInEmail)) return
    updateOrganization(organizationId) { it.copy(excludeFromPlatformAggregates = exclude) }
  }

  override fun updateOrganizationQuotaLimits(organizationId: String, limits: QuotaLimitsConfig) {
    updateOrganization(organizationId) { it.copy(quotaLimits = limits) }
  }

  override fun deleteOrganization(organizationId: String) {
    val name = organization(organizationId)?.name
    launchSaving { organizationRepository.deleteOrganization(organizationId) }
    returnToOrganizations(
      notice = name?.let { organizationMessage(Res.string.org_notice_deleted, it) }
    )
  }

  // --- Membership ---

  override fun inviteMember(
    organizationId: String,
    email: String,
    role: OrganizationRole,
  ): OrganizationMessage? {
    inviteMemberUseCase.inviteError(organization(organizationId), email, role)?.let {
      return it.toOrganizationMessage()
    }
    launchSaving { inviteMemberUseCase(organizationId, email, role) }
    return null
  }

  override fun resetInviteLink(organizationId: String, email: String) {
    launchSaving { inviteMemberUseCase.resetInviteLink(organizationId, email) }
  }

  override fun acceptInvite(
    organizationId: String,
    email: String,
    displayName: String,
    photoUrl: String?,
  ): OrganizationMessage? {
    inviteMemberUseCase.acceptError(displayName)?.let {
      return it.toOrganizationMessage()
    }
    launchSaving { inviteMemberUseCase.acceptInvite(organizationId, email, displayName, photoUrl) }
    return null
  }

  override fun requestToJoin(organizationId: String) {
    launchSaving {
      organizationRepository.requestToJoin(organizationId, signedInEmail, signedInProfile)
    }
  }

  override fun approveRequest(organizationId: String, email: String) {
    launchSaving { organizationRepository.approveRequest(organizationId, email) }
  }

  override fun setMemberRole(organizationId: String, email: String, role: OrganizationRole) {
    launchSaving {
      val updated = organizationRepository.setMemberRole(organizationId, email, role)
      if (updated == null) {
        val organization = organization(organizationId)
        val notice =
          if (organization?.isSynthetic == true && role != OrganizationRole.MANAGER) {
            organizationMessage(Res.string.org_notice_role_all_users, organization.name)
          } else {
            organizationMessage(Res.string.org_notice_last_manager_role)
          }
        session.update { it.copy(notice = notice) }
      }
    }
  }

  override fun removeMember(organizationId: String, email: String) {
    val isSelf = email.equals(signedInEmail, ignoreCase = true)
    launchSaving {
      val updated = organizationRepository.removeMember(organizationId, email)
      if (updated == null) {
        session.update {
          it.copy(notice = organizationMessage(Res.string.org_notice_last_manager_leave))
        }
      } else if (isSelf) {
        returnToOrganizations(
          notice = organizationMessage(Res.string.org_notice_left, updated.name)
        )
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
    expression: google.earthengine.v1main.Expression?,
  ): OrganizationMessage? {
    manageImagerySourcesUseCase.validationError(name, urlTemplate, type)?.let {
      return OrganizationMessage.Text(it)
    }
    val organization =
      organization(organizationId) ?: return organizationMessage(Res.string.org_error_not_found)
    val source =
      manageImagerySourcesUseCase.newSource(
        organization = organization,
        allOrganizations = data.value.organizations,
        name = name,
        urlTemplate = urlTemplate,
        type = type,
        allowOfflineDownload = allowOfflineDownload,
        expression = expression,
      )
    launchSaving { manageImagerySourcesUseCase.add(organizationId, source) }
    return null
  }

  override fun updateImagerySource(
    organizationId: String,
    sourceId: String,
    name: String,
    urlTemplate: String,
    allowOfflineDownload: Boolean,
  ): OrganizationMessage? {
    // Earth Engine sources have no URL to validate; only their name is editable here.
    val type =
      organization(organizationId)?.imagerySources?.firstOrNull { it.id == sourceId }?.type
        ?: ImagerySourceType.XYZ_TILES
    manageImagerySourcesUseCase.validationError(name, urlTemplate, type)?.let {
      return OrganizationMessage.Text(it)
    }
    launchSaving {
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
    launchSaving {
      manageImagerySourcesUseCase.setOfflineAllowed(organizationId, sourceId, allowOfflineDownload)
    }
  }

  override fun removeImagerySource(organizationId: String, sourceId: String) {
    _events.tryEmit(OrganizationEvent.ImagerySourceRemoved(sourceId))
    launchSaving { manageImagerySourcesUseCase.remove(organizationId, sourceId) }
  }

  // --- Library: see OrganizationLibraryEditor ---

  override fun setDictionaryQuery(query: String) = libraryEditor.setDictionaryQuery(query)

  override fun saveConcept(concept: LibraryConcept, isNew: Boolean) =
    libraryEditor.saveConcept(concept, isNew)

  override fun deleteConcept(organizationId: String, conceptId: String) =
    libraryEditor.deleteConcept(organizationId, conceptId)

  override fun createTemplate(
    organizationId: String,
    title: String,
    description: String,
    questions: List<EditorQuestion>,
  ) = libraryEditor.createTemplate(organizationId, title, description, questions)

  override fun createTemplate(
    organizationId: String,
    title: String,
    description: String,
    questions: List<EditorQuestion>,
    purposePackIds: List<String>,
  ) = libraryEditor.createTemplate(organizationId, title, description, questions, purposePackIds)

  override fun saveTemplate(template: FormTemplate) = libraryEditor.saveTemplate(template)

  override fun saveTemplateForm(
    organizationId: String,
    templateId: String?,
    form: EditorForm,
    description: String,
    purposePackIds: List<String>,
  ) = libraryEditor.saveTemplateForm(organizationId, templateId, form, description, purposePackIds)

  override fun updateTemplateDetails(
    organizationId: String,
    templateId: String,
    title: String,
    description: String,
    purposePackIds: List<String>?,
  ) =
    libraryEditor.updateTemplateDetails(
      organizationId,
      templateId,
      title,
      description,
      purposePackIds,
    )

  override fun setTemplatePurposePacks(
    organizationId: String,
    templateId: String,
    purposePackIds: List<String>,
  ) = libraryEditor.setTemplatePurposePacks(organizationId, templateId, purposePackIds)

  override fun renameTemplate(
    organizationId: String,
    templateId: String,
    title: String,
    description: String,
  ) = libraryEditor.renameTemplate(organizationId, templateId, title, description)

  override fun deleteTemplate(organizationId: String, templateId: String) =
    libraryEditor.deleteTemplate(organizationId, templateId)

  override fun createPurposePack(
    organizationId: String,
    title: String,
    description: String,
    icon: String,
    pillar: ImpactPillar?,
    formTemplateIds: List<String>,
  ) =
    libraryEditor.createPurposePack(
      organizationId,
      title,
      description,
      icon,
      pillar,
      formTemplateIds,
    )

  override fun updatePurposePack(
    organizationId: String,
    packId: String,
    title: String,
    description: String,
    icon: String,
    pillar: ImpactPillar?,
    status: LibraryStatus,
    formTemplateIds: List<String>,
  ) =
    libraryEditor.updatePurposePack(
      organizationId,
      packId,
      title,
      description,
      icon,
      pillar,
      status,
      formTemplateIds,
    )

  override fun renamePurposePack(
    organizationId: String,
    packId: String,
    title: String,
    description: String,
  ) = libraryEditor.renamePurposePack(organizationId, packId, title, description)

  override fun deletePurposePack(organizationId: String, packId: String) =
    libraryEditor.deletePurposePack(organizationId, packId)

  override fun setGlobalEntryHidden(
    organizationId: String,
    globalEntryId: String,
    hidden: Boolean,
  ) = libraryEditor.setGlobalEntryHidden(organizationId, globalEntryId, hidden)
}
