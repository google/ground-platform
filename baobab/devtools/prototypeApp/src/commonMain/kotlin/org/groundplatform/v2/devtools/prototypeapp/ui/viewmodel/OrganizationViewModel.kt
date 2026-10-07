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
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImagerySourceType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationRole
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.AuthRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.OrganizationRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.CreateOrganizationUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.InviteOrganizationMemberUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ManageImagerySourcesUseCase
import org.groundplatform.v2.devtools.prototypeapp.ui.state.OrganizationEvent
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
   * its ID.
   */
  fun createOrganization(name: String, description: String, isListed: Boolean): String

  /** Saves the organization's profile fields. Only Managers may call this. */
  fun updateOrganizationDetails(
    organizationId: String,
    name: String,
    description: String,
    websiteUrl: String,
    isListed: Boolean,
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
}

/**
 * ViewModel of the web organizations directory (`#organizations`) and organization page
 * (`#organization/<id>`: details, surveys, members, and imagery sources).
 *
 * Organizations come from [OrganizationRepository], surveys from [SurveyRepository], and the
 * signed-in user from [AuthRepository]. Creating an organization runs through
 * [CreateOrganizationUseCase], invites through [InviteOrganizationMemberUseCase], and imagery
 * sources through [ManageImagerySourcesUseCase]; simple membership changes pass straight to the
 * repository. Page navigation and map side effects are published as [OrganizationEvent]s for the
 * app shell.
 */
class OrganizationViewModel(
  private val organizationRepository: OrganizationRepository,
  surveyRepository: SurveyRepository,
  authRepository: AuthRepository,
  private val createOrganizationUseCase: CreateOrganizationUseCase,
  private val inviteMemberUseCase: InviteOrganizationMemberUseCase,
  private val manageImagerySourcesUseCase: ManageImagerySourcesUseCase,
  private val scope: CoroutineScope,
) : OrganizationActions {
  /** Everything the organization pages read from the local data store. */
  private data class Data(
    val organizations: List<Organization> = emptyList(),
    val surveys: List<SurveyPreviewItem> = emptyList(),
    val activeSurveyId: String = "",
    val isSignedIn: Boolean = false,
    val profile: AuthProfile = AuthProfile("", "", ""),
  )

  /** Session (non-persisted) state of the organization pages. */
  private data class Session(val openOrganizationId: String? = null, val notice: String? = null)

  private val data: StateFlow<Data> =
    combine(
        organizationRepository.observeOrganizations(),
        surveyRepository.observeSurveys(),
        surveyRepository.observeActiveSurveyId(),
        authRepository.observeSession(),
      ) { organizations, surveys, activeSurveyId, auth ->
        Data(
          organizations = organizations,
          surveys = surveys,
          activeSurveyId = activeSurveyId,
          isSignedIn = auth.isSignedIn,
          profile = auth.profile,
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
    )

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

  override fun createOrganization(name: String, description: String, isListed: Boolean): String {
    val organization =
      createOrganizationUseCase.newOrganization(
        name = name,
        description = description,
        isListed = isListed,
        existingOrganizations = data.value.organizations,
      )
    scope.launch {
      createOrganizationUseCase(organization, signedInEmail, signedInProfile)
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
  ) =
    updateOrganization(organizationId) {
      it.copy(
        name = name.trim(),
        description = description.trim(),
        websiteUrl = websiteUrl.trim(),
        isListed = isListed,
      )
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
    inviteMemberUseCase.inviteError(organization(organizationId), email)?.let {
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
        session.update {
          it.copy(
            notice =
              "An organization needs at least one Manager. Make someone else a Manager first."
          )
        }
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
}
