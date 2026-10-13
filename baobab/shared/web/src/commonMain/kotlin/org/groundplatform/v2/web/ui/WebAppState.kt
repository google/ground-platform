/*
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.groundplatform.v2.web.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import org.groundplatform.v2.app.di.AppDataHolder
import org.groundplatform.v2.app.domain.model.ImagerySourceType
import org.groundplatform.v2.app.domain.model.Organization
import org.groundplatform.v2.app.domain.model.OrganizationRole
import org.groundplatform.v2.app.domain.model.SurveyPreviewItem
import org.groundplatform.v2.app.ui.AppSession
import org.groundplatform.v2.app.ui.viewmodel.SettingsViewModel
import org.groundplatform.v2.app.ui.viewmodel.SurveyOutcomePromptViewModel
import org.groundplatform.v2.web.ui.dashboard.CreateSurveyRequest
import org.groundplatform.v2.web.ui.state.CreateSurveyWizardEvent
import org.groundplatform.v2.web.ui.state.OrganizationEvent
import org.groundplatform.v2.web.ui.state.SurveyEditorEvent
import org.groundplatform.v2.web.ui.viewmodel.CreateSurveyWizardViewModel
import org.groundplatform.v2.web.ui.viewmodel.ImpactViewModel
import org.groundplatform.v2.web.ui.viewmodel.OrganizationViewModel
import org.groundplatform.v2.web.ui.viewmodel.SurveyEditorViewModel

/**
 * State holder of the Ground web app: an [AppSession] plus the web-only ViewModels (organizations,
 * Survey editor, Survey Creation Wizard, Impact, and the survey outcome prompt) and routing between
 * the web app's [WebPage]s.
 *
 * The web app is always online: airplane mode is only simulated for the mobile app.
 */
class WebAppState(
  dataHolder: AppDataHolder = AppDataHolder(),
  settings: SettingsViewModel = createSettingsViewModel(dataHolder),
  initialPage: WebPage = WebPage.WEB_SURVEYS,
) : AppSession(dataHolder, settings) {

  /**
   * ViewModel of the web organizations directory and organization page (details, surveys, members,
   * imagery sources, and library). Its pages observe [OrganizationViewModel.uiState] directly; this
   * class mirrors it for the rest of the UI and applies its [OrganizationEvent]s to the web app.
   */
  val organization: OrganizationViewModel =
    OrganizationViewModel(
      organizationRepository = dataHolder.organizationRepository,
      surveyRepository = dataHolder.surveyRepository,
      authRepository = dataHolder.authRepository,
      createOrganizationUseCase = dataHolder.createOrganizationUseCase,
      inviteMemberUseCase = dataHolder.inviteOrganizationMemberUseCase,
      manageImagerySourcesUseCase = dataHolder.manageImagerySourcesUseCase,
      libraryRepository = dataHolder.libraryRepository,
      manageLibraryUseCase = dataHolder.manageLibraryUseCase,
      resolveLibraryUseCase = dataHolder.resolveLibraryUseCase,
      searchConceptsUseCase = dataHolder.searchConceptsUseCase,
      scope = dataHolder.scope,
    )

  private var organizationState by mutableStateOf(organization.uiState.value)

  /**
   * ViewModel of the Survey editor (survey details, sharing, Forms, Map layers, Data tables, and
   * sample plots of the active survey). Its pages observe [SurveyEditorViewModel.uiState] directly;
   * this class mirrors it for the rest of the UI and applies its [SurveyEditorEvent]s to the app
   * shell.
   */
  val surveyEditor: SurveyEditorViewModel =
    SurveyEditorViewModel(
      surveyRepository = dataHolder.surveyRepository,
      surveyEditorRepository = dataHolder.surveyEditorRepository,
      organizationRepository = dataHolder.organizationRepository,
      authRepository = dataHolder.authRepository,
      placeRepository = dataHolder.placeRepository,
      generateSamplePlots = dataHolder.generateSamplePlotsUseCase,
      inviteCollaboratorUseCase = dataHolder.inviteCollaboratorUseCase,
      scope = dataHolder.scope,
      libraryRepository = dataHolder.libraryRepository,
      manageLibraryUseCase = dataHolder.manageLibraryUseCase,
      resolveLibraryUseCase = dataHolder.resolveLibraryUseCase,
      surveyLifecycle = dataHolder.surveyLifecycleUseCase,
      impactEventRepository = dataHolder.impactEventRepository,
    )

  private var surveyEditorState by mutableStateOf(surveyEditor.uiState.value)

  /**
   * ViewModel of the 6-step Survey Creation Wizard (`#create-survey`), keeping its working survey
   * synchronized with [surveyEditor].
   */
  val createSurveyWizard: CreateSurveyWizardViewModel =
    CreateSurveyWizardViewModel(
      surveyRepository = dataHolder.surveyRepository,
      surveyEditorRepository = dataHolder.surveyEditorRepository,
      organizationRepository = dataHolder.organizationRepository,
      authRepository = dataHolder.authRepository,
      libraryRepository = dataHolder.libraryRepository,
      createSurveyUseCase = dataHolder.createSurveyUseCase,
      surveyEditor = surveyEditor,
      scope = dataHolder.scope,
      resolveLibraryUseCase = dataHolder.resolveLibraryUseCase,
      exportSurveyDataUseCase = dataHolder.exportSurveyDataUseCase,
    )

  /**
   * ViewModel of the "Was this data used?" badge on the web surveys page: closed surveys whose
   * outcome question is due again, and the card opened from one. Its page observes
   * [SurveyOutcomePromptViewModel.uiState] directly.
   */
  val surveyOutcomePrompt: SurveyOutcomePromptViewModel =
    SurveyOutcomePromptViewModel(
      surveyRepository = dataHolder.surveyRepository,
      organizationRepository = dataHolder.organizationRepository,
      authRepository = dataHolder.authRepository,
      surveyLifecycle = dataHolder.surveyLifecycleUseCase,
      scope = dataHolder.scope,
    )

  /**
   * ViewModel of the Impact views: the web dashboard's survey Impact tab, the organization Impact
   * tab, and the platform-wide view for Managers of "All users". Its panes observe
   * [ImpactViewModel.uiState] directly.
   */
  val impact: ImpactViewModel =
    ImpactViewModel(
      surveyRepository = dataHolder.surveyRepository,
      organizationRepository = dataHolder.organizationRepository,
      libraryRepository = dataHolder.libraryRepository,
      impactEventRepository = dataHolder.impactEventRepository,
      authRepository = dataHolder.authRepository,
      scope = dataHolder.scope,
      surveyEditorRepository = dataHolder.surveyEditorRepository,
      computeImpact = dataHolder.computeImpactUseCase,
    )

  init {
    dataHolder.scope.launch { organization.uiState.collect { organizationState = it } }
    dataHolder.scope.launch { organization.events.collect(::onOrganizationEvent) }
    dataHolder.scope.launch { surveyEditor.uiState.collect { surveyEditorState = it } }
    dataHolder.scope.launch { surveyEditor.events.collect(::onSurveyEditorEvent) }
    dataHolder.scope.launch { createSurveyWizard.events.collect(::onCreateSurveyWizardEvent) }
  }

  override val isAlwaysOnline: Boolean
    get() = true

  /** Active page of the web app. */
  var activePage by mutableStateOf(initialPage)
    private set

  /** Listener invoked whenever a page is selected (e.g. to update the URL hash). */
  var onPageChanged: ((WebPage) -> Unit)? = null

  /**
   * Opens the prototype's mobile device preview from the web app's header, or `null` (hiding the
   * header's `Mobile prototype` button) when the web app runs on its own.
   */
  var onShowMobilePrototype: (() -> Unit)? = null

  /** Switches the active page and notifies [onPageChanged]. */
  fun selectPage(page: WebPage) {
    activePage = page
    onPageChanged?.invoke(page)
  }

  override fun reset() {
    super.reset()
    organization.reset()
    surveyEditor.reset()
    surveyOutcomePrompt.reset()
    impact.reset()
  }

  /**
   * Applies a Survey editor outcome to the web app: leaving the editor returns to the dashboard.
   */
  private fun onSurveyEditorEvent(event: SurveyEditorEvent) {
    when (event) {
      SurveyEditorEvent.Published,
      SurveyEditorEvent.Closed -> selectPage(WebPage.WEB_DASHBOARD)
    }
  }

  /**
   * Applies a Survey Creation Wizard outcome to the web app: opens the full Survey editor on
   * completion/handoff, or returns to the previous page on cancel.
   */
  private fun onCreateSurveyWizardEvent(event: CreateSurveyWizardEvent) {
    when (event) {
      is CreateSurveyWizardEvent.OpenSurveyEditor -> {
        openSurveyOnWeb(event.surveyId)
        selectPage(WebPage.SURVEY_EDITOR)
      }
      CreateSurveyWizardEvent.Cancelled -> {
        val returnPage =
          if (openOrganizationId != null) WebPage.ORGANIZATION else WebPage.WEB_SURVEYS
        selectPage(returnPage)
      }
    }
  }

  /** Applies an organization outcome to the rest of the web app (page switch, map imagery). */
  private fun onOrganizationEvent(event: OrganizationEvent) {
    when (event) {
      is OrganizationEvent.OrganizationOpened -> selectPage(WebPage.ORGANIZATION)
      OrganizationEvent.OrganizationsOpened -> selectPage(WebPage.ORGANIZATIONS)
      is OrganizationEvent.ImagerySourceRemoved ->
        if (isImagerySourceEnabled(event.sourceId)) surveyMap.toggleImagerySource(event.sourceId)
    }
  }

  // --- Organizations (web `#organizations` and `#organization/<id>` pages) ---

  /** ID of the organization shown on the web organization page, or `null`. */
  val openOrganizationId: String?
    get() = organizationState.openOrganizationId

  /** The organization shown on the web organization page, if it still exists. */
  val openOrganization: Organization?
    get() = organizationState.openOrganization

  /** Notice from the last organization action (e.g. a refused change), shown on the page. */
  val organizationNotice: String?
    get() = organizationState.notice

  fun dismissOrganizationNotice() = organization.dismissNotice()

  /** Shows [organizationId] on the web organization page. */
  fun openOrganization(organizationId: String) = organization.openOrganization(organizationId)

  /** Shows the list of organizations. */
  fun openOrganizations() = organization.openOrganizations()

  /** Surveys that belong to [organizationId]. */
  fun surveysInOrganization(organizationId: String): List<SurveyPreviewItem> =
    organizationState.surveysInOrganization(organizationId)

  /** Whether the signed-in user manages [organization]. */
  fun managesOrganization(organization: Organization): Boolean =
    organizationState.managesOrganization(organization)

  /**
   * Creates an organization managed by the signed-in user, opens it, and returns its ID. The ID is
   * a slug of [name], made unique with a numeric suffix.
   */
  fun createOrganization(name: String, description: String, isListed: Boolean): String =
    organization.createOrganization(name, description, isListed)

  /** Saves the organization's profile fields. Only Managers may call this. */
  fun updateOrganization(organizationId: String, transform: (Organization) -> Organization) =
    organization.updateOrganization(organizationId, transform)

  /** Deletes the organization; its surveys become personal surveys. Returns to the list. */
  fun deleteOrganization(organizationId: String) = organization.deleteOrganization(organizationId)

  /**
   * Invites [email] to [organizationId] with [role]. Returns an error message for an invalid or
   * already-present address, or `null` when the invite was sent.
   */
  fun inviteOrganizationMember(
    organizationId: String,
    email: String,
    role: OrganizationRole,
  ): String? = organization.inviteMember(organizationId, email, role)

  /** Issues a new invite link for a pending invite, invalidating the old one. */
  fun resetOrganizationInviteLink(organizationId: String, email: String) =
    organization.resetInviteLink(organizationId, email)

  /**
   * Simulates the invitee opening their link and accepting. Returns an error message, or `null`.
   */
  fun acceptOrganizationInvite(
    organizationId: String,
    email: String,
    displayName: String,
    photoUrl: String?,
  ): String? = organization.acceptInvite(organizationId, email, displayName, photoUrl)

  /** Asks to join a listed organization as the signed-in user. */
  fun requestToJoinOrganization(organizationId: String) = organization.requestToJoin(organizationId)

  fun approveOrganizationRequest(organizationId: String, email: String) =
    organization.approveRequest(organizationId, email)

  /** Changes a member's role. Refusals (demoting the last Manager) surface as a notice. */
  fun setOrganizationMemberRole(organizationId: String, email: String, role: OrganizationRole) =
    organization.setMemberRole(organizationId, email, role)

  /**
   * Removes a member, declines a join request, or revokes an invite. Refusals (removing the last
   * Manager) surface as a notice. Removing yourself returns to the list of organizations.
   */
  fun removeOrganizationMember(organizationId: String, email: String) =
    organization.removeMember(organizationId, email)

  /**
   * Adds an [ImagerySource] to [organizationId]. Returns a validation error message, or `null` when
   * the imagery source was added.
   */
  fun addOrganizationImagerySource(
    organizationId: String,
    name: String,
    urlTemplate: String,
    type: ImagerySourceType = ImagerySourceType.XYZ_TILES,
    allowOfflineDownload: Boolean = false,
  ): String? =
    organization.addImagerySource(organizationId, name, urlTemplate, type, allowOfflineDownload)

  /**
   * Updates an existing [ImagerySource] on [organizationId]. Returns a validation error message, or
   * `null` when saved.
   */
  fun updateOrganizationImagerySource(
    organizationId: String,
    sourceId: String,
    name: String,
    urlTemplate: String,
    allowOfflineDownload: Boolean,
  ): String? =
    organization.updateImagerySource(
      organizationId,
      sourceId,
      name,
      urlTemplate,
      allowOfflineDownload,
    )

  /** Toggles whether offline download on mobile is permitted for [sourceId] in [organizationId]. */
  fun setOrganizationImagerySourceOfflineAllowed(
    organizationId: String,
    sourceId: String,
    allowOfflineDownload: Boolean,
  ) = organization.setImagerySourceOfflineAllowed(organizationId, sourceId, allowOfflineDownload)

  /** Removes the imagery source with [sourceId] from [organizationId]. */
  fun removeOrganizationImagerySource(organizationId: String, sourceId: String) =
    organization.removeImagerySource(organizationId, sourceId)

  /**
   * Opens a survey in the web app (dashboard and Survey editor). Unlike [openSurvey], this doesn't
   * download the survey for offline use or move the mobile preview to the Main Survey UI.
   */
  fun openSurveyOnWeb(surveyId: String) = dashboard.openSurveyOnWeb(surveyId)

  /**
   * Creates a new, empty survey owned by the signed-in user, optionally in [organizationId], makes
   * it active, and returns its ID. The caller normally opens the Survey editor next.
   */
  fun createSurvey(title: String, organizationId: String? = null): String =
    dashboard.createSurvey(title, organizationId)

  /** Creates a survey from the Create survey dialog, seeded with its Purpose Packs' forms. */
  fun createSurvey(request: CreateSurveyRequest): String =
    dashboard.createSurvey(
      title = request.title,
      organizationId = request.organizationId,
      purposeIds = request.purposeIds,
      programIds = request.programIds,
    )

  /**
   * Starts a new 6-step Survey Creation Wizard session (`#create-survey`), optionally pre-selecting
   * [organizationId], and navigates to [WebPage.CREATE_SURVEY].
   */
  fun openCreateSurveyWizard(organizationId: String? = null) {
    createSurveyWizard.startWizard(organizationId)
    selectPage(WebPage.CREATE_SURVEY)
  }
}
