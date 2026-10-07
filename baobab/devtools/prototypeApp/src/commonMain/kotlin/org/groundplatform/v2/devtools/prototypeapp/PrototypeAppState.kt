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
package org.groundplatform.v2.devtools.prototypeapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import groundplatform.v2.forms.FormDef
import groundplatform.v2.forms.RecordInstance
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.groundplatform.v2.core.forms.model.EntityState
import org.groundplatform.v2.core.forms.serialization.XFormsXmlSerializer
import org.groundplatform.v2.core.forms.ui.FormWizardController
import org.groundplatform.v2.core.forms.ui.WorkbenchExampleForm
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyMapAnchor
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyEditorDraft
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.withEditorFormAvailability
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.withEditorLayerStyles
import org.groundplatform.v2.devtools.prototypeapp.domain.model.relatedEntityForPropertyValue
import org.groundplatform.v2.devtools.prototypeapp.domain.model.singularTypeLabelOf
import org.groundplatform.v2.devtools.prototypeapp.domain.model.toClusterFeatures
import org.groundplatform.v2.devtools.prototypeapp.map.DraftGeometry
import org.groundplatform.v2.devtools.prototypeapp.map.EntityGeometry
import org.groundplatform.v2.devtools.prototypeapp.map.FormGeometryOverlay
import org.groundplatform.v2.devtools.prototypeapp.pdf.GeneratedPdf
import org.groundplatform.v2.devtools.prototypeapp.pdf.RecordPdfReports
import org.groundplatform.v2.devtools.prototypeapp.surveyeditor.SurveyAccess
import org.groundplatform.v2.devtools.prototypeapp.ui.state.DashboardEvent
import org.groundplatform.v2.devtools.prototypeapp.ui.state.DashboardUiState
import org.groundplatform.v2.devtools.prototypeapp.ui.state.OnboardingEvent
import org.groundplatform.v2.devtools.prototypeapp.ui.state.OrganizationEvent
import org.groundplatform.v2.devtools.prototypeapp.ui.state.PrototypeUiState
import org.groundplatform.v2.devtools.prototypeapp.ui.state.SettingsEvent
import org.groundplatform.v2.devtools.prototypeapp.ui.state.SurveyMapEvent
import org.groundplatform.v2.devtools.prototypeapp.ui.state.SurveyMapUiState
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.DashboardViewModel
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.OnboardingViewModel
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.OrganizationViewModel
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.PrototypeAppViewModel
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.SettingsViewModel
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.SurveyMapViewModel
import org.groundplatform.v2.map.CameraPosition
import org.groundplatform.v2.map.LatLng
import org.groundplatform.v2.map.LngLatBounds

/**
 * State controller for the Ground 2.0 UI Prototype workbench (`devtools/prototypeApp`).
 *
 * Manages both the onboarding screens (`Sign In` -> `Terms of Service` -> `Download survey`) and
 * the **Main Survey UI** (`Map` view with geospatial entities, `Layers` filter popover, `1:1` and
 * `1:N` entity bottom sheets, `List` view with searchable forms, entities, and submissions, and the
 * Hamburger Navigation Drawer).
 */
class PrototypeAppState(
  initialScreen: PrototypeScreen = PrototypeScreen.SIGN_IN,
  val viewModel: PrototypeAppViewModel = PrototypeAppViewModel(initialScreen = initialScreen),
) {
  /**
   * Latest snapshot of everything read from the local data store (the single source of truth for
   * surveys, survey content, mutations, places, and settings). Kept current by collecting
   * [PrototypeAppViewModel.appData]; all data properties below are read-only views over it, and all
   * data changes are written through the repositories.
   */
  private var data by mutableStateOf(viewModel.appData.value)

  /**
   * ViewModel of the onboarding flow (Sign In → Terms of Service → Download survey). Its screens
   * observe [OnboardingViewModel.uiState] directly; this class mirrors it for the rest of the UI
   * and applies its navigation [OnboardingEvent]s to the app shell.
   */
  val onboarding: OnboardingViewModel =
    OnboardingViewModel(
      authRepository = viewModel.authRepository,
      surveyRepository = viewModel.surveyRepository,
      organizationRepository = viewModel.organizationRepository,
      scope = viewModel.scope,
    )

  private var onboardingState by mutableStateOf(onboarding.uiState.value)

  /**
   * ViewModel of user settings, the session theme, and offline tile packages. Its screens observe
   * [SettingsViewModel.uiState] directly; this class mirrors it for the rest of the UI.
   */
  val settings: SettingsViewModel =
    SettingsViewModel(
      settingsRepository = viewModel.settingsRepository,
      surveyRepository = viewModel.surveyRepository,
      mutationRepository = viewModel.mutationRepository,
      scope = viewModel.scope,
    )

  private var settingsState by mutableStateOf(settings.uiState.value)

  /**
   * ViewModel of the survey map viewport (camera, GPS, basemap and imagery, clustering, wayfinding,
   * and the selected map feature, cluster, or place). The map views observe
   * [SurveyMapViewModel.uiState] directly; this class mirrors it for the rest of the UI and applies
   * its [SurveyMapEvent]s to the app shell.
   */
  val surveyMap: SurveyMapViewModel =
    SurveyMapViewModel(
      surveyRepository = viewModel.surveyRepository,
      organizationRepository = viewModel.organizationRepository,
      settingsRepository = viewModel.settingsRepository,
      locationRepository = viewModel.locationRepository,
      placeRepository = viewModel.placeRepository,
      scope = viewModel.scope,
      clusterMapFeatures = viewModel.clusterMapFeaturesUseCase,
      computeWayfindingNavigation = viewModel.computeWayfindingNavigationUseCase,
    )

  private var mapState by mutableStateOf(surveyMap.uiState.value)

  /** Latest [SurveyMapUiState], for views that build the map from the app shell. */
  val surveyMapUiState: SurveyMapUiState
    get() = mapState

  /**
   * ViewModel of the web dashboard (panel layout, data tables, layer selection, entity details
   * pane), the web Surveys page, the searchable list's filters, and the `Uploads` drawer sub-screen
   * (mutation log and sync). Its screens observe [DashboardViewModel.uiState] directly; this class
   * mirrors it for the rest of the UI and applies its [DashboardEvent]s to the app shell.
   */
  val dashboard: DashboardViewModel =
    DashboardViewModel(
      surveyRepository = viewModel.surveyRepository,
      organizationRepository = viewModel.organizationRepository,
      authRepository = viewModel.authRepository,
      mutationRepository = viewModel.mutationRepository,
      createSurveyUseCase = viewModel.createSurveyUseCase,
      syncMutationsUseCase = viewModel.syncMutationsUseCase,
      scope = viewModel.scope,
    )

  private var dashboardState by mutableStateOf(dashboard.uiState.value)

  /**
   * ViewModel of the web organizations directory and organization page (details, surveys, members,
   * imagery sources). Its pages observe [OrganizationViewModel.uiState] directly; this class
   * mirrors it for the rest of the UI and applies its [OrganizationEvent]s to the app shell.
   */
  val organization: OrganizationViewModel =
    OrganizationViewModel(
      organizationRepository = viewModel.organizationRepository,
      surveyRepository = viewModel.surveyRepository,
      authRepository = viewModel.authRepository,
      createOrganizationUseCase = viewModel.createOrganizationUseCase,
      inviteMemberUseCase = viewModel.inviteOrganizationMemberUseCase,
      manageImagerySourcesUseCase = viewModel.manageImagerySourcesUseCase,
      scope = viewModel.scope,
    )

  private var organizationState by mutableStateOf(organization.uiState.value)

  init {
    viewModel.scope.launch { viewModel.appData.collect { data = it } }
    viewModel.scope.launch { onboarding.uiState.collect { onboardingState = it } }
    viewModel.scope.launch { onboarding.events.collect(::onOnboardingEvent) }
    viewModel.scope.launch { settings.uiState.collect { settingsState = it } }
    viewModel.scope.launch {
      settings.events.collect { event ->
        when (event) {
          is SettingsEvent.Notice -> activeSurveyNotice = event.message
        }
      }
    }
    viewModel.scope.launch { surveyMap.uiState.collect { mapState = it } }
    viewModel.scope.launch { surveyMap.events.collect(::onSurveyMapEvent) }
    viewModel.scope.launch { dashboard.uiState.collect { dashboardState = it } }
    viewModel.scope.launch { dashboard.events.collect(::onDashboardEvent) }
    viewModel.scope.launch { organization.uiState.collect { organizationState = it } }
    viewModel.scope.launch { organization.events.collect(::onOrganizationEvent) }
  }

  /** Applies an organization outcome to the rest of the app shell (page switch, map imagery). */
  private fun onOrganizationEvent(event: OrganizationEvent) {
    when (event) {
      is OrganizationEvent.OrganizationOpened ->
        selectWorkbenchPage(PrototypeWorkbenchPage.ORGANIZATION)
      OrganizationEvent.OrganizationsOpened ->
        selectWorkbenchPage(PrototypeWorkbenchPage.ORGANIZATIONS)
      is OrganizationEvent.ImagerySourceRemoved ->
        if (isImagerySourceEnabled(event.sourceId)) surveyMap.toggleImagerySource(event.sourceId)
    }
  }

  /** Applies a dashboard outcome to the rest of the app shell (survey switch, drawer, notices). */
  private fun onDashboardEvent(event: DashboardEvent) {
    when (event) {
      is DashboardEvent.SurveyActivated -> clearSelectionsForActivatedSurvey(event.surveyId)
      DashboardEvent.UploadsOpened -> {
        isDrawerOpen = false
        activeDrawerSubView = MainDrawerSubView.UPLOADS
      }
      is DashboardEvent.Notice -> activeSurveyNotice = event.message
    }
  }

  /** Applies a map outcome to the rest of the app shell (list selection, drawer, notices). */
  private fun onSurveyMapEvent(event: SurveyMapEvent) {
    when (event) {
      is SurveyMapEvent.EntitySelected -> {
        selectedSubmissionId = null
        mainViewMode = MainSurveyViewMode.MAP
        dashboard.onEntitySelected(event.entityId)
      }
      is SurveyMapEvent.PlaceSelected -> {
        selectedSubmissionId = null
        dashboard.selectLayer(null)
        mainViewMode = MainSurveyViewMode.MAP
        activeSurveyNotice = "Centered map on ${event.place.name} (${event.place.coordinatesLabel})"
      }
      is SurveyMapEvent.ClusterSelected -> {
        selectedSubmissionId = null
        activeSurveyNotice =
          surveyMap.uiState.value.formatClusterSitesCountLabel(event.cluster.siteCount)
      }
      SurveyMapEvent.SelectionCleared -> selectedSubmissionId = null
      is SurveyMapEvent.NavigationStarted -> {
        selectedSubmissionId = event.submissionId
        isDrawerOpen = false
        activeDrawerSubView = MainDrawerSubView.NONE
        mainViewMode = MainSurveyViewMode.MAP
        if (currentScreen != PrototypeScreen.MAIN_SURVEY) {
          currentScreen = PrototypeScreen.MAIN_SURVEY
        }
        activeSurveyNotice = event.notice
      }
      SurveyMapEvent.NavigationStopped -> activeSurveyNotice = null
      is SurveyMapEvent.Notice -> activeSurveyNotice = event.message
    }
  }

  /** Applies an onboarding outcome to the app shell (screen, drawer, notices). */
  private fun onOnboardingEvent(event: OnboardingEvent) {
    when (event) {
      is OnboardingEvent.ShowScreen -> {
        currentScreen = event.screen
        if (event.screen == PrototypeScreen.SIGN_IN) {
          isDrawerOpen = false
          activeDrawerSubView = MainDrawerSubView.NONE
          activeSurveyNotice = null
        }
      }
      is OnboardingEvent.SurveyOpened -> {
        activateSurvey(event.surveyId)
        currentScreen = PrototypeScreen.MAIN_SURVEY
        isDrawerOpen = false
        activeDrawerSubView = MainDrawerSubView.NONE
        activeSurveyNotice = event.notice
      }
      OnboardingEvent.ReturnToSurveyList -> {
        isDrawerOpen = false
        activeSurveyNotice = null
        currentScreen = PrototypeScreen.MAIN_SURVEY
        activeDrawerSubView = MainDrawerSubView.SWITCH_SURVEYS
      }
      OnboardingEvent.SignedOut -> {
        isDrawerOpen = false
        activeDrawerSubView = MainDrawerSubView.NONE
        activeSurveyNotice = null
      }
      is OnboardingEvent.Notice -> activeSurveyNotice = event.message
    }
  }

  /** Writes [surveys] to the local data store (inserting or replacing by ID). */
  private fun storeSurveys(surveys: List<SurveyPreviewItem>) {
    viewModel.launch { viewModel.surveyRepository.setSurveys(surveys) }
  }

  /** Writes the active survey's [entities] to the local data store. */
  private fun storeEntities(entities: List<GeospatialEntityItem>) {
    viewModel.launch { viewModel.surveyRepository.setEntities(entities) }
  }

  /** Writes the active survey's standalone [submissions] to the local data store. */
  private fun storeStandaloneSubmissions(submissions: List<SubmissionPreviewItem>) {
    viewModel.launch { viewModel.surveyRepository.setStandaloneSubmissions(submissions) }
  }

  /**
   * Immutable [StateFlow] of [PrototypeUiState] exposed by the underlying MVVM
   * [PrototypeAppViewModel] per Section 6 of `docs/technical/client/architecture.md`.
   */
  val uiState: StateFlow<PrototypeUiState>
    get() {
      syncViewModelState()
      return viewModel.uiState
    }

  /**
   * Synchronizes session (non-persisted) state into [viewModel]. Data fields come from the local
   * data store and are applied by the view model itself.
   */
  fun syncViewModelState() {
    viewModel.updateUiState { current ->
      current.copy(
        currentScreen = currentScreen,
        isSignedIn = isSignedIn,
        signedInUserEmail = signedInUserEmail,
        signedInUserName = signedInUserName,
        termsCheckboxChecked = termsCheckboxChecked,
        hasAcceptedTerms = hasAcceptedTerms,
        downloadSurveyEntryOrigin = downloadSurveyEntryOrigin,
        isDownloadSurveySignOutPromptOpen = isDownloadSurveySignOutPromptOpen,
        searchQuery = searchQuery,
        activeSurveyNotice = activeSurveyNotice,
        isDarkTheme = isDarkTheme,
        deviceFormFactor = deviceFormFactor,
        deviceOrientation = deviceOrientation,
        mainViewMode = mainViewMode,
        isLayersSheetOpen = isLayersSheetOpen,
        selectedBasemapType = selectedBasemapType,
        selectedOfflineBasemapStyle = offlineBasemapStyle,
        enabledImagerySourceIds = enabledImagerySourceIds,
        isDrawerOpen = isDrawerOpen,
        activeDrawerSubView = activeDrawerSubView,
        selectedUploadStatusFilter = selectedUploadStatusFilter,
        mapboxPlacesApiResults = mapboxPlacesApiResults,
        isMapboxPlacesSearching = isMapboxPlacesSearching,
        isAirplaneMode = isAirplaneMode,
        selectedEntityId = selectedEntityId,
        selectedClusterId = selectedClusterId,
        selectedPlaceId = selectedPlaceId,
        lastSelectedPlace = selectedPlace,
        isEntityBottomSheetExpanded = isEntityBottomSheetExpanded,
        selectedSubmissionId = selectedSubmissionId,
        listSearchQuery = listSearchQuery,
        listFilterTab = listFilterTab,
        userGpsNormalizedX = userGpsNormalizedX,
        userGpsNormalizedY = userGpsNormalizedY,
        userGpsCoordinatesLabel = userGpsCoordinatesLabel,
        gnssSatelliteCount = gnssSatelliteCount,
        gnssAccuracyMeters = gnssAccuracyMeters,
        isCameraFollowingUser = isCameraFollowingUser,
        locationLockState = locationLockState,
        mapZoomDelta = mapZoomDelta,
        mapPanOffsetX = mapPanOffsetX,
        mapPanOffsetY = mapPanOffsetY,
        activeQrCodeEntityId = activeQrCodeEntityId,
        activeSharedPdfSheet = activeSharedPdfSheet,
        navigationTargetKind = navigationTargetKind,
        navigationTargetId = navigationTargetId,
        customXFormsXml = customXFormsXml,
        selectedWorkbenchExampleForm = selectedWorkbenchExampleForm,
        customFormDef = customFormDef,
        xformsXmlError = xformsXmlError,
        activeDataCollectionEntityId = activeDataCollectionEntityId,
        activeDataCollectionFormId = activeDataCollectionFormId,
        activeFormWizardController = activeFormWizardController,
        isAvailableFormsSheetOpen = isAvailableFormsSheetOpen,
        wasFormLaunchedWithoutEntity = wasFormLaunchedWithoutEntity,
        entityRefSelectorViewMode = entityRefSelectorViewMode,
        entityRefSearchQuery = entityRefSearchQuery,
      )
    }
  }

  var currentScreen by mutableStateOf(initialScreen)
    private set

  val isDarkTheme: Boolean
    get() = settingsState.isDarkTheme

  /** Active page of the prototype workbench (`Mobile prototype`, `Web app`, `Survey editor`). */
  var activeWorkbenchPage by mutableStateOf(PrototypeWorkbenchPage.MOBILE_PROTOTYPE)
    private set

  /** Listener invoked when the active workbench page changes. */
  var onWorkbenchPageChanged: ((PrototypeWorkbenchPage) -> Unit)? = null

  /** Switches the active workbench page and notifies listeners. */
  fun selectWorkbenchPage(page: PrototypeWorkbenchPage) {
    activeWorkbenchPage = page
    onWorkbenchPageChanged?.invoke(page)
  }

  /** Active hardware preview bezel form factor in the wrapper workbench (`Mobile` vs `Tablet`). */
  var deviceFormFactor by mutableStateOf(DeviceFormFactor.MOBILE)
    private set

  /** Active screen orientation of the hardware preview bezel (`Portrait` vs `Landscape`). */
  var deviceOrientation by mutableStateOf(deviceFormFactor.defaultOrientation)
    private set

  /** True when the active device is rotated away from its form factor's default orientation. */
  val isDeviceRotated: Boolean
    get() = deviceOrientation != deviceFormFactor.defaultOrientation

  /**
   * Effective width in `dp` of the hardware bezel for the current [deviceFormFactor] and
   * [deviceOrientation].
   */
  val effectiveFrameWidthDp: Int
    get() = deviceFormFactor.widthForOrientation(deviceOrientation)

  /**
   * Effective height in `dp` of the hardware bezel for the current [deviceFormFactor] and
   * [deviceOrientation].
   */
  val effectiveFrameHeightDp: Int
    get() = deviceFormFactor.heightForOrientation(deviceOrientation)

  /** Formatted `W × H dp` label for the current [deviceFormFactor] and [deviceOrientation]. */
  val effectiveDimensionsLabel: String
    get() = deviceFormFactor.dimensionsLabelForOrientation(deviceOrientation)

  val isSignedIn: Boolean
    get() = onboardingState.isSignedIn

  val signedInUserName: String
    get() = onboardingState.profile.displayName

  /** Two-letter initials of the signed-in user for avatar badges (`"ML"` for `"Maya Lin"`). */
  val signedInUserInitials: String
    get() = onboardingState.profile.initials

  val signedInUserEmail: String
    get() = onboardingState.profile.email

  /** Tracks whether the Download surveys screen was reached after ToS or from the Survey list. */
  val downloadSurveyEntryOrigin: DownloadSurveyEntryOrigin
    get() = onboardingState.entryOrigin

  /** True when the sign-out confirmation prompt is open on the Download surveys screen. */
  val isDownloadSurveySignOutPromptOpen: Boolean
    get() = onboardingState.isSignOutPromptOpen

  /** ID of a downloaded survey pending confirmation to remove from the device, or null. */
  val pendingRemovalSurveyId: String?
    get() = onboardingState.pendingRemovalSurveyId

  /** ID of an offline tile package pending confirmation to remove from the device, or null. */
  val pendingRemovalTilePackageId: String?
    get() = settingsState.pendingRemovalTilePackageId

  /** True when the Download surveys screen was opened from the Downloaded Surveys list. */
  val isDownloadSurveyAccessedFromSurveyList: Boolean
    get() = downloadSurveyEntryOrigin == DownloadSurveyEntryOrigin.SURVEY_LIST

  /** All organizations in the local data store, in display order. */
  val organizations: List<Organization>
    get() = data.organizations

  /** Organizations the signed-in user is an active member of. */
  val signedInUserOrganizations: List<Organization>
    get() = organizations.filter { it.isMember(signedInUserEmail) }

  /** Organizations the signed-in user manages. */
  val signedInUserManagedOrganizations: List<Organization>
    get() = organizations.filter { it.isManager(signedInUserEmail) }

  /**
   * Short affiliation line for profile cards: the user's organizations joined with " • ", or empty
   * if they belong to none.
   */
  val signedInOrganization: String
    get() = signedInUserOrganizations.joinToString(" • ") { it.name }

  fun organization(organizationId: String?): Organization? = organizationId?.let { id ->
    organizations.firstOrNull { it.id == id }
  }

  /** The organization the active survey belongs to, if any. */
  val activeSurveyOrganization: Organization?
    get() = organization(activeSurvey.organizationId)

  /** The synthetic `"All users"` organization, holding platform-wide imagery sources. */
  val allUsersOrganization: Organization?
    get() = organization(Organization.ALL_USERS_ID) ?: organizations.firstOrNull { it.isSynthetic }

  /** Imagery sources configured on the synthetic `"All users"` organization. */
  val allUsersImagerySources: List<ImagerySource>
    get() = mapState.allUsersImagerySources

  /**
   * Imagery sources configured on the active survey's organization (excluding the synthetic `"All
   * users"` organization so sources are never duplicated).
   */
  val activeSurveyOrganizationImagerySources: List<ImagerySource>
    get() = mapState.activeSurveyOrganizationImagerySources

  /**
   * All organization imagery sources available to toggle in the basemap layers dialog for the
   * active survey: `"All users"` sources followed by survey-specific organization sources.
   */
  val availableImagerySources: List<ImagerySource>
    get() = mapState.availableImagerySources

  /** IDs of organization imagery sources currently toggled ON in the basemap layers dialog. */
  val enabledImagerySourceIds: Set<String>
    get() = mapState.enabledImagerySourceIds

  /** Returns whether the organization imagery source with [sourceId] is currently toggled ON. */
  fun isImagerySourceEnabled(sourceId: String): Boolean = mapState.isImagerySourceEnabled(sourceId)

  /** Toggles visibility of the organization imagery source with [sourceId] on the map. */
  fun toggleImagerySource(sourceId: String) = surveyMap.toggleImagerySource(sourceId)

  /**
   * Currently enabled [ImagerySource]s for the active survey (in layer order: `"All users"` sources
   * first, then survey-specific organization sources).
   */
  val enabledImagerySources: List<ImagerySource>
    get() = mapState.enabledImagerySources

  /**
   * Whether the signed-in user may open the Survey editor for the active survey: they own it, are
   * an accepted Survey organizer on its access list, or manage its organization (see
   * [SurveyAccess]).
   */
  val canManageActiveSurvey: Boolean
    get() =
      isSignedIn &&
        SurveyAccess.canManage(
          email = signedInUserEmail,
          sharing = activeSurveyEditorDraft.sharing,
          organization = activeSurveyOrganization,
        )

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

  val termsCheckboxChecked: Boolean
    get() = onboardingState.termsCheckboxChecked

  val hasAcceptedTerms: Boolean
    get() = onboardingState.hasAcceptedTerms

  val searchQuery: String
    get() = onboardingState.searchQuery

  /** Surveys stored on the device (from the local data store). */
  val surveys: List<SurveyPreviewItem>
    get() = data.surveys

  var activeSurveyNotice by mutableStateOf<String?>(null)
    private set

  // --- Main Survey UI State ---
  val activeSurveyId: String
    get() = data.activeSurveyId

  var mainViewMode by mutableStateOf(MainSurveyViewMode.MAP)
    private set

  var isDrawerOpen by mutableStateOf(false)
    private set

  var activeDrawerSubView by mutableStateOf(MainDrawerSubView.NONE)
    private set

  val isLayersSheetOpen: Boolean
    get() = mapState.isLayersSheetOpen

  val selectedBasemapType: BasemapType
    get() = mapState.selectedBasemapType

  val isOfflineBasemapVisible: Boolean
    get() = mapState.isOfflineBasemapVisible

  val offlineBasemapStyle: OfflineBasemapStyle
    get() = mapState.offlineBasemapStyle

  /**
   * The survey's map layers, styled with the color and pin icon of the matching Map layer in the
   * published Survey editor draft (if any).
   */
  val mapLayers: List<MapLayerItem>
    get() =
      data.content.mapLayers.withEditorLayerStyles(data.content.editorDraft?.datasets.orEmpty())

  /** The map layer that owns [entity] (by `layerId`), whose style the entity is drawn with. */
  fun mapLayerFor(entity: GeospatialEntityItem): MapLayerItem? = mapLayers.firstOrNull {
    it.id == entity.layerId
  }

  val submissionGeometries: List<SubmissionGeometryPolygon>
    get() = data.content.submissionGeometries

  /**
   * The survey's forms, with the availability (mobile, web, or both) chosen for the matching Form
   * in the published Survey editor draft (if any).
   */
  val forms: List<FormPreviewItem>
    get() = data.content.forms.withEditorFormAvailability(data.content.editorDraft?.forms.orEmpty())

  /** Forms collectors can start from the mobile app's entry points. */
  val mobileForms: List<FormPreviewItem>
    get() = forms.filter { it.availability.includesMobile }

  /** Forms collectors can start from the web dashboard's entry points. */
  val webForms: List<FormPreviewItem>
    get() = forms.filter { it.availability.includesWeb }

  /** Map features in the active survey. Setting this writes them to the local data store. */
  var entities: List<GeospatialEntityItem>
    get() = data.content.entities
    internal set(value) = storeEntities(value)

  /** Geographic places and landmarks searchable via Place search in the active survey region. */
  val places: List<SurveyPlaceItem>
    get() = data.places

  /** ID of the currently selected [SurveyPlaceItem] from Place search (if any). */
  val selectedPlaceId: String?
    get() = mapState.selectedPlace?.id

  /**
   * Standalone form submissions recorded without an attached Geospatial Entity (`entityId == ""`).
   */
  val standaloneSubmissions: List<SubmissionPreviewItem>
    get() = data.content.standaloneSubmissions

  val selectedEntityId: String?
    get() = mapState.selectedEntityId

  /** Monotonically increasing counter incremented every time an entity is selected. */
  val entitySelectionEpoch: Long
    get() = mapState.entitySelectionEpoch

  /**
   * Whether the Entity Bottom Sheet is expanded (`true`) to show full properties/submissions or
   * collapsed (`false`, default) into a compact single-row peek bar at the bottom of the map so it
   * does not obscure the map viewport.
   */
  val isEntityBottomSheetExpanded: Boolean
    get() = mapState.isEntityBottomSheetExpanded

  // --- Web dashboard, searchable list filters & Uploads (see [DashboardViewModel]) ---

  /** Latest [DashboardUiState], for views that build the dashboard from the app shell. */
  val dashboardUiState: DashboardUiState
    get() = dashboardState

  /**
   * Whether the left-hand panel in the web dashboard is expanded (`true`, default) or collapsed
   * (`false`) to provide a full-width map view.
   */
  val isSidePanelExpanded: Boolean
    get() = dashboardState.isSidePanelExpanded

  /** Alias for [isSidePanelExpanded] with dashboard prefix. */
  val isDashboardSidePanelExpanded: Boolean
    get() = isSidePanelExpanded

  /**
   * Width of the web dashboard's left-hand panel when expanded, in dp. Adjusted by dragging the
   * panel's right border ([updateSidePanelWidth]); always within [MIN_SIDE_PANEL_WIDTH_DP] and
   * [MAX_SIDE_PANEL_WIDTH_DP], and kept while the panel is collapsed.
   */
  val sidePanelWidthDp: Float
    get() = dashboardState.sidePanelWidthDp

  /**
   * Whether the right-hand details panel in the web dashboard is expanded (`true`, default) or
   * collapsed (`false`) to maximize visible map space.
   */
  val isDetailsPanelExpanded: Boolean
    get() = dashboardState.isDetailsPanelExpanded

  /** Alias for [isDetailsPanelExpanded] with dashboard prefix. */
  val isDashboardDetailsPanelExpanded: Boolean
    get() = isDetailsPanelExpanded

  /** Alias for [isDetailsPanelExpanded]. */
  val isRightPanelExpanded: Boolean
    get() = isDetailsPanelExpanded

  var selectedSubmissionId by mutableStateOf<String?>(null)
    private set

  /**
   * Entity dataset ID of the map layer or data table selected in the web dashboard's left-hand
   * panel, or `null`. Mutually exclusive with [selectedEntityId] and [selectedSubmissionId].
   */
  val selectedLayerDatasetId: String?
    get() = dashboardState.selectedLayerDatasetId

  /**
   * Which pane of the selected entity's details surface is showing: its properties (default) or its
   * `1:N` submissions. Submissions are always one click away from the properties so the details
   * surface opens on the entity's current state.
   */
  val entityDetailsPane: EntityDetailsPane
    get() = dashboardState.entityDetailsPane

  /**
   * Whether the web dashboard's bottom data table is expanded. It never expands automatically; only
   * the collapsed bar's toggle or the details card's "Show in table" button expand it.
   */
  val isDashboardTableExpanded: Boolean
    get() = dashboardState.isDashboardTableExpanded

  /**
   * Entity dataset whose table is active in the web dashboard's bottom data table, or `null` for
   * the first dataset. Follows the selected entity's dataset.
   */
  val dashboardTableDatasetId: String?
    get() = dashboardState.dashboardTableDatasetId

  /**
   * Entity whose mutations the `Uploads` screen is filtered to (opened from an entity's details),
   * or `null` to show uploads for every entity.
   */
  val uploadsEntityFilterId: String?
    get() = dashboardState.uploadsEntityFilterId

  val listSearchQuery: String
    get() = dashboardState.listSearchQuery

  val listFilterTab: ListFilterTab
    get() = dashboardState.listFilterTab

  val offlineTilePackages: List<OfflineTilePackageItem>
    get() = data.offlineTilePackages

  /** Local mutation log (`DataMutation` items across all upload states) for the active survey. */
  val mutations: List<MutationLogItem>
    get() = data.mutations

  /**
   * Active status filter chip on the unified `Uploads` screen (`Pending`, `In progress`,
   * `Uploaded`, or `Failed`), or `null` when all uploads are shown.
   */
  val selectedUploadStatusFilter: UploadStatusFilter?
    get() = dashboardState.selectedUploadStatusFilter

  /**
   * All local mutations sorted in strict reverse chronological order (
   * [MutationLogItem.operationTimestamp] descending).
   */
  val allMutationsSorted: List<MutationLogItem>
    get() = dashboardState.allMutationsSorted

  /**
   * Mutations displayed in the unified `Uploads` screen filtered by [selectedUploadStatusFilter]
   * (or all mutations when [selectedUploadStatusFilter] is `null`) and by [uploadsEntityFilterId]
   * (when set), in reverse chronological order.
   */
  val filteredUploadMutations: List<MutationLogItem>
    get() = dashboardState.filteredUploadMutations

  /** Returns the total count of mutations matching [filter] on the `Uploads` screen. */
  fun uploadCountForFilter(filter: UploadStatusFilter): Int =
    dashboardState.uploadCountForFilter(filter)

  /** Entity the `Uploads` screen is currently filtered to, if any. */
  val uploadsEntityFilter: GeospatialEntityItem?
    get() = dashboardState.uploadsEntityFilter

  /** Number of local mutations (any upload state) recorded for the entity with [entityId]. */
  fun uploadCountForEntity(entityId: String): Int = dashboardState.uploadCountForEntity(entityId)

  /** Number of not-yet-uploaded mutations (`isOutbox`) recorded for the entity with [entityId]. */
  fun pendingUploadCountForEntity(entityId: String): Int =
    dashboardState.pendingUploadCountForEntity(entityId)

  /** IDs of entities with at least one not-yet-uploaded mutation (`isOutbox`). */
  val pendingUploadEntityIds: Set<String>
    get() = dashboardState.pendingUploadEntityIds

  /**
   * Opens the `Uploads` screen filtered to the entity with [entityId], so a data collector can
   * check whether their changes to that map feature went through.
   */
  fun openUploadsForEntity(entityId: String) = dashboard.openUploadsForEntity(entityId)

  /** Clears the entity filter on the `Uploads` screen. */
  fun clearUploadsEntityFilter() = dashboard.clearUploadsEntityFilter()

  /**
   * Pending, in-progress, or failed mutations (`isOutbox == true`), listed in strict reverse
   * chronological order ([MutationLogItem.operationTimestamp] descending).
   */
  val outboxMutations: List<MutationLogItem>
    get() = dashboardState.outboxMutations

  /**
   * Completed mutations (`isUploaded == true`), listed in strict reverse chronological order (
   * [MutationLogItem.operationTimestamp] descending).
   */
  val uploadedMutations: List<MutationLogItem>
    get() = dashboardState.uploadedMutations

  /** Total count of pending/active/failed mutations not yet uploaded. */
  val outboxMutationCount: Int
    get() = dashboardState.outboxMutationCount

  /** Total count of completed mutations in the `Uploaded` state. */
  val uploadedMutationCount: Int
    get() = dashboardState.uploadedMutationCount

  val unitSystem: MeasurementUnitSystem
    get() = data.userSettings.measurementUnits

  val selectedLanguageCode: String
    get() = data.userSettings.language

  val selectedLanguageLocale: String
    get() = settingsState.selectedLanguageLocale

  val shouldUploadPhotosOnWifiOnly: Boolean
    get() = data.userSettings.shouldUploadPhotosOnWifiOnly

  val visitedWebsiteUrl: String?
    get() = settingsState.visitedWebsiteUrl

  val mediaCacheCleared: Boolean
    get() = settingsState.mediaCacheCleared

  /** Display label of the currently selected language (e.g. `"English"`, `"Français"`). */
  val selectedLanguageDisplayName: String
    get() =
      GROUND_LANGUAGE_OPTIONS.firstOrNull { it.code == selectedLanguageCode }?.label ?: "English"

  /** Snapshot of user settings matching `UserSettings` in `github.com/google/ground-android`. */
  val userSettings: UserSettings
    get() = data.userSettings

  /**
   * Device storage breakdown showing total device storage, free storage, storage occupied by
   * downloaded imagery (vector & raster basemap tiles), and space taken up by data (surveys, forms,
   * entities, submissions, mutations).
   */
  val deviceStorageInfo: DeviceStorageInfo
    get() = settingsState.storage

  // --- User GPS Location & Auto-Centering Map Camera State (see [SurveyMapViewModel]) ---
  /** Normalized world X coordinate `[0, 1]` of the collector's current GPS location. */
  val userGpsNormalizedX: Float
    get() = mapState.userGpsNormalizedX

  /** Normalized world Y coordinate `[0, 1]` of the collector's current GPS location. */
  val userGpsNormalizedY: Float
    get() = mapState.userGpsNormalizedY

  /** Formatted GPS coordinates & accuracy badge for the user's current field position. */
  val userGpsCoordinatesLabel: String
    get() = mapState.userGpsCoordinatesLabel

  /**
   * Whether the map camera automatically pans to keep the user's current GPS location at the center
   * of the screen (`true` by default). Becomes `false` when the user drags/pans the map or selects
   * a place, which sets [locationLockState] to [LocationLockState.PANNED] and reveals the Google
   * Maps-style `"Recenter"` button.
   */
  val isCameraFollowingUser: Boolean
    get() = mapState.isCameraFollowingUser

  /** Current map camera lock state (`LOCKED`, `LOCKED_3D`, or `PANNED`). */
  val locationLockState: LocationLockState
    get() = mapState.locationLockState

  /** Where the active survey sits on the map. */
  private val activeSurveyAnchor: SurveyMapAnchor
    get() = mapState.anchor

  /** Where the active survey sits on the map, for the map content builders. */
  internal val mapAnchor: SurveyMapAnchor
    get() = activeSurveyAnchor

  private fun activeSurveyBaseLngLat(): Pair<Double, Double> =
    activeSurveyAnchor.center.let { it.longitude to it.latitude }

  /** Normalized horizontal viewport offset applied when the user manually drags/pans the map. */
  val mapPanOffsetX: Float
    get() = mapState.mapPanOffsetX

  /** Normalized vertical viewport offset applied when the user manually drags/pans the map. */
  val mapPanOffsetY: Float
    get() = mapState.mapPanOffsetY

  /** Zoom delta relative to the active survey's default Mapbox zoom level (`[-5.0f, +3.7f]`). */
  val mapZoomDelta: Float
    get() = mapState.mapZoomDelta

  /** Formatted current Mapbox zoom level badge (e.g. `"15.3z"`). */
  val effectiveMapZoomLabel: String
    get() = mapState.effectiveMapZoomLabel

  /**
   * Google Maps-style horizontal scale bar specification (`label`, `distanceMeters`, `barWidthDp`)
   * computed by `ClusterMapFeaturesUseCase.computeScaleBarSpec`.
   */
  val mapScaleBarSpec: MapScaleBarSpec
    get() = mapState.mapScaleBarSpec

  /**
   * Total horizontal world-to-viewport shift (`(0.50f - userGpsNormalizedX) + mapPanOffsetX`).
   * Ensures `(userGpsNormalizedX, userGpsNormalizedY)` is always centered at `(0.50f, 0.50f)`
   * whenever [isCameraFollowingUser] is `true` (`mapPanOffsetX == 0f`).
   */
  val mapWorldToScreenShiftX: Float
    get() = mapState.mapWorldToScreenShiftX

  /**
   * Total vertical world-to-viewport shift (`(0.50f - userGpsNormalizedY) + mapPanOffsetY`).
   * Ensures `(userGpsNormalizedX, userGpsNormalizedY)` is always centered at `(0.50f, 0.50f)`
   * whenever [isCameraFollowingUser] is `true` (`mapPanOffsetY == 0f`).
   */
  val mapWorldToScreenShiftY: Float
    get() = mapState.mapWorldToScreenShiftY

  /** Normalized screen X coordinate of the user's GPS blue dot (`0.50f` when centered). */
  val userScreenNormalizedX: Float
    get() = mapState.userScreenNormalizedX

  /** Normalized screen Y coordinate of the user's GPS blue dot (`0.50f` when centered). */
  val userScreenNormalizedY: Float
    get() = mapState.userScreenNormalizedY

  /** Number of GNSS (GPS/Galileo/GLONASS) satellites currently locked by the device receiver. */
  val gnssSatelliteCount: Int
    get() = mapState.gnssSatelliteCount

  /** Current horizontal GNSS accuracy in meters (`±2.1 m`). */
  val gnssAccuracyMeters: Double
    get() = mapState.gnssAccuracyMeters

  /** Formatted horizontal GNSS accuracy (`±2.1 m` or `±6.8 ft`). */
  val gnssAccuracyFormatted: String
    get() = mapState.gnssAccuracyFormatted

  /** Formatted GPS accuracy badge displayed on the chip over the map (`±2.1 m` or `±6.8 ft`). */
  val gnssStatusChipLabel: String
    get() = mapState.gnssStatusChipLabel

  /**
   * Entity ID whose scannable GeoID QR code modal dialog is currently open (`null` when closed).
   */
  var activeQrCodeEntityId by mutableStateOf<String?>(null)
    private set

  /** The [GeospatialEntityItem] whose QR code modal dialog is currently open (if any). */
  val activeQrCodeEntity: GeospatialEntityItem?
    get() = activeQrCodeEntityId?.let { id -> entities.firstOrNull { it.id == id } }

  /**
   * Active PDF export & app-sharing modal state for an entity or submission (`null` when closed).
   */
  var activeSharedPdfSheet by mutableStateOf<SharedPdfSheetState?>(null)
    private set

  /** The PDF generated for [activeSharedPdfSheet] (`null` when the sheet is closed). */
  private var activePdf: GeneratedPdf? = null

  /** Short confirmation or error after a PDF action (e.g. `"Saved …pdf"`), or `null`. */
  var pdfExportMessage by mutableStateOf<String?>(null)
    private set

  // --- Straight-Line Wayfinding Navigation State (see [SurveyMapViewModel]) ---
  /**
   * Target kind (`ENTITY`, `SUBMISSION`, or `PLACE`) for active straight-line navigation (`null`
   * when inactive).
   */
  val navigationTargetKind: NavigationTargetKind?
    get() = mapState.navigationTargetKind

  /**
   * Target ID (`entityId`, `submissionId`, or `placeId`) for active straight-line navigation
   * (`null` when inactive).
   */
  val navigationTargetId: String?
    get() = mapState.navigationTargetId

  /** Computes a [StraightLineVector] from the collector's current GPS position. */
  fun computeStraightLineVector(
    targetNormalizedX: Float,
    targetNormalizedY: Float,
  ): StraightLineVector = surveyMap.computeStraightLineVector(targetNormalizedX, targetNormalizedY)

  /**
   * Resolves the normalized map coordinates `(normalizedX, normalizedY)` and optional
   * [SubmissionGeometryPolygon] for any [SubmissionPreviewItem].
   */
  fun resolveSubmissionTargetGeometry(
    submissionId: String
  ): Triple<Float, Float, SubmissionGeometryPolygon?>? =
    surveyMap.resolveSubmissionTargetGeometry(submissionId)

  /** Returns the live [StraightLineVector] from the user's GPS position to [entityId]. */
  fun distanceAndBearingToEntity(entityId: String): StraightLineVector? =
    surveyMap.distanceAndBearingToEntity(entityId)

  /** Returns the live [StraightLineVector] from the user's GPS position to [submissionId]. */
  fun distanceAndBearingToSubmission(submissionId: String): StraightLineVector? =
    surveyMap.distanceAndBearingToSubmission(submissionId)

  /**
   * Resolves a [SurveyPlaceItem] by [placeId] across live API results, built-in places, list search
   * results, and the place selected or navigated to on the map.
   */
  fun findPlaceById(placeId: String): SurveyPlaceItem? =
    (mapboxPlacesApiResults + places + filteredListPlaces).firstOrNull { it.id == placeId }
      ?: surveyMap.findPlaceById(placeId)

  /** Returns the live [StraightLineVector] from the user's GPS position to [placeId]. */
  fun distanceAndBearingToPlace(placeId: String): StraightLineVector? =
    findPlaceById(placeId)?.let(surveyMap::distanceAndBearingToPlace)

  /** Formatted distance & compass bearing badge for [entityId] (e.g. `"495 m • 319° NW"`). */
  fun formattedWayfindingBadgeForEntity(entityId: String): String =
    surveyMap.formattedWayfindingBadgeForEntity(entityId)

  /** Formatted distance & compass bearing badge for [placeId] (e.g. `"340 m • 142° SE"`). */
  fun formattedWayfindingBadgeForPlace(placeId: String): String =
    distanceAndBearingToPlace(placeId)?.formattedBadge.orEmpty()

  /** Formatted distance & compass bearing badge for [submissionId] (e.g. `"452 m • 321° NW"`). */
  fun formattedWayfindingBadgeForSubmission(submissionId: String): String =
    surveyMap.formattedWayfindingBadgeForSubmission(submissionId)

  /** Returns `true` if [field] in [submissionId] represents a geometry question/field. */
  fun isSubmissionFieldGeometry(submissionId: String, field: SubmissionFieldEntry): Boolean =
    surveyMap.isSubmissionFieldGeometry(submissionId, field)

  /**
   * Formatted distance & compass bearing badge for a geometry [field] inside [submissionId] (e.g.
   * `"452 m • 321° NW"`), or `""` if [field] is not a geometry field.
   */
  fun formattedWayfindingBadgeForSubmissionField(
    submissionId: String,
    field: SubmissionFieldEntry,
  ): String = surveyMap.formattedWayfindingBadgeForSubmissionField(submissionId, field)

  /** True when straight-line navigation is currently active and targeting [entityId]. */
  fun isNavigatingToEntity(entityId: String): Boolean = mapState.isNavigatingToEntity(entityId)

  /** True when straight-line navigation is currently active and targeting [submissionId]. */
  fun isNavigatingToSubmission(submissionId: String): Boolean =
    mapState.isNavigatingToSubmission(submissionId)

  /** True when straight-line navigation is currently active and targeting [placeId]. */
  fun isNavigatingToPlace(placeId: String): Boolean = mapState.isNavigatingToPlace(placeId)

  /** Currently active straight-line navigation session ([StraightLineNavigationState]). */
  val activeNavigation: StraightLineNavigationState?
    get() = mapState.activeNavigation

  // --- Data Collection Form & XForms FormDef Chrome State ---
  /**
   * Currently selected swappable example form in the Prototype App Workbench (
   * [WorkbenchExampleForm]), or `null` when custom XML has been manually edited.
   */
  var selectedWorkbenchExampleForm by
    mutableStateOf<WorkbenchExampleForm?>(WorkbenchExampleForm.ALL_FIELD_TYPES)
    private set

  /**
   * Custom XForms `<h:html>` definition editable in the Prototype App Chrome
   * (`UxDesignerInspectorPanel`). Initialized to a rich EUDR / Shade-Tree Field Survey XForms XML
   * that parses cleanly with [XFormsXmlSerializer.deserializeFormDef].
   */
  var customXFormsXml by mutableStateOf(DEFAULT_PROTOTYPE_XFORMS_XML)
    private set

  /**
   * Parse error message from [XFormsXmlSerializer.deserializeFormDef] when [customXFormsXml] is
   * invalid, or `null` when valid.
   */
  var xformsXmlError by mutableStateOf<String?>(null)
    private set

  /**
   * Parsed [FormDef] from [customXFormsXml] (`null` when [customXFormsXml] is blank or invalid;
   * when blank, built-in fallback XForms [FormDef]s per form ID are used automatically).
   */
  var customFormDef by mutableStateOf<FormDef?>(parseDefaultPrototypeFormDef())
    private set

  /**
   * Active [FormWizardController] driving the embedded
   * [org.groundplatform.v2.core.forms.ui.MobileFormRunner] when data collection is triggered for a
   * Geospatial Entity (`null` when closed).
   */
  var activeFormWizardController: FormWizardController?
    get() = activeFormWizardControllerState
    private set(value) {
      // Opening, replacing, or closing a form ends any map drawing started for the previous one,
      // and drops a pending jump to one of its questions.
      if (value !== activeFormWizardControllerState) {
        webMapDrawing.stopDrawing()
        webFormFocusRequest = null
      }
      activeFormWizardControllerState = value
    }

  private var activeFormWizardControllerState by mutableStateOf<FormWizardController?>(null)

  /**
   * Web dashboard's "draw on the map" host for the compact form runner's geometry questions. Mobile
   * never starts drawing (its widgets capture the device GPS), so [WebMapDrawingHost.isDrawing]
   * stays `false` there. See [addWebMapDrawingVertex] and [webMapDraftGeometry].
   */
  val webMapDrawing =
    WebMapDrawingHost(
      controller = { activeFormWizardController },
      onFrame = { bounds, maxZoom -> requestWebMapFraming(bounds, maxZoom) },
    )

  /**
   * Bounds the web dashboard's main map should frame next (a question card's **Zoom to fit**), or
   * `null`. Each request carries a fresh token, so framing the same geometry twice re-fires.
   */
  var webMapFramingRequest by mutableStateOf<MapFramingRequest?>(null)
    private set

  private var webMapFramingToken = 0L

  /** Asks the main map to centre [bounds] in its visible area, zooming in at most to [maxZoom]. */
  fun requestWebMapFraming(bounds: LngLatBounds, maxZoom: Double) {
    webMapFramingToken += 1
    webMapFramingRequest = MapFramingRequest(bounds, maxZoom, webMapFramingToken)
  }

  /**
   * Adds a main-map click at [latLng] to the geometry question being drawn (no-op when nothing is
   * being drawn). A `geopoint` is placed by its first click, which also stops the drawing.
   */
  fun addWebMapDrawingVertex(latLng: LatLng) = webMapDrawing.addVertex(latLng)

  /** The geometry being drawn on the main map, for its overlay, or `null` when not drawing. */
  val webMapDraftGeometry: DraftGeometry?
    get() = webMapDrawing.draftGeometry

  /**
   * Every geometry answer held by the open form (web dashboard), for the main map's in-flow
   * overlay: each `geopoint` / `geotrace` / `geoshape` field with a value, except the one being
   * drawn right now (the draft overlay shows that one). Empty when no form is open.
   */
  val webFormGeometries: List<FormGeometryOverlay>
    get() {
      val controller = activeFormWizardController ?: return emptyList()
      return formGeometryOverlays(controller, excludePath = webMapDrawing.activeDrawingPath)
    }

  /**
   * The question the web form panel should scroll to and highlight, after its geometry was clicked
   * on the main map ([focusWebFormQuestion]). Each request carries a fresh token, so clicking the
   * same geometry again re-fires it.
   */
  var webFormFocusRequest by mutableStateOf<FormFocusRequest?>(null)
    private set

  private var webFormFocusToken = 0L

  /** Asks the web form panel to bring the question at [path] into view and highlight it. */
  fun focusWebFormQuestion(path: String) {
    webFormFocusToken += 1
    webFormFocusRequest = FormFocusRequest(path, webFormFocusToken)
  }

  /** Clears [webFormFocusRequest] once the form panel has handled it. */
  fun consumeWebFormFocusRequest() {
    webFormFocusRequest = null
  }

  /**
   * Target Geospatial Entity ID for the currently active data collection form (`null` when closed).
   */
  var activeDataCollectionEntityId by mutableStateOf<String?>(null)
    private set

  /** Target Form ID for the currently active data collection form (`null` when closed). */
  var activeDataCollectionFormId by mutableStateOf<String?>(null)
    private set

  /** True when the embedded [org.groundplatform.v2.core.forms.ui.MobileFormRunner] is open. */
  val isDataCollectionFormOpen: Boolean
    get() = activeFormWizardController != null

  /**
   * The target [GeospatialEntityItem] for the currently active data collection session (if any).
   */
  val activeDataCollectionEntity: GeospatialEntityItem?
    get() = activeDataCollectionEntityId?.let { id -> entities.firstOrNull { it.id == id } }

  /** The target [FormPreviewItem] for the currently active data collection session (if any). */
  val activeDataCollectionForm: FormPreviewItem?
    get() = activeDataCollectionFormId?.let { id -> forms.firstOrNull { it.id == id } }

  /**
   * True when the Available Forms modal bottom sheet (triggered by the bottom-centered floating
   * action button on the Main Survey screen) is currently open.
   */
  var isAvailableFormsSheetOpen by mutableStateOf(false)
    private set

  /**
   * True when the active data collection form was launched from the bottom-centered FAB without
   * pre-selecting a geospatial entity on the map (`activeDataCollectionEntityId` starts `null`, and
   * the `entityref` step presents the Map or List selector).
   */
  var wasFormLaunchedWithoutEntity by mutableStateOf(false)
    private set

  /**
   * Active view mode (`MainSurveyViewMode.MAP` vs `MainSurveyViewMode.LIST`) for the in-form
   * `entityref` step selector when a form is launched without a pre-selected geospatial entity.
   */
  var entityRefSelectorViewMode by mutableStateOf(MainSurveyViewMode.MAP)
    private set

  /** Search query used to filter candidate entities in the `entityref` step's `List` mode. */
  var entityRefSearchQuery by mutableStateOf("")
    private set

  /**
   * Incremented each time a map feature is picked at the `entityref` step, so the step's map frames
   * it again even when the same feature is picked twice.
   */
  var entityRefFramingEpoch by mutableStateOf(0L)
    private set

  /**
   * Returns all [GeospatialEntityItem]s in the active survey belonging to [form]'s target dataset
   * (`form.targetDatasetId`).
   */
  fun allDatasetEntitiesForForm(form: FormPreviewItem): List<GeospatialEntityItem> =
    if (form.requiresEntity) {
      entities.filter { it.datasetId == form.targetDatasetId }
    } else {
      emptyList()
    }

  /**
   * Returns the eligible [GeospatialEntityItem]s in [form]'s target dataset that can accept a new
   * submission for [form] (`isFormButtonEnabled(entity, form) == true`).
   */
  fun eligibleEntitiesForForm(form: FormPreviewItem): List<GeospatialEntityItem> =
    allDatasetEntitiesForForm(form).filter { isFormButtonEnabled(it, form) }

  /** Eligible [GeospatialEntityItem]s for the currently active data collection form. */
  val eligibleEntitiesForActiveForm: List<GeospatialEntityItem>
    get() = activeDataCollectionForm?.let { eligibleEntitiesForForm(it) } ?: emptyList()

  /**
   * Candidate entities for the active form's target dataset filtered by [entityRefSearchQuery] when
   * the collector is using the `List` view at the `entityref` step.
   */
  val filteredEntityRefCandidates: List<GeospatialEntityItem>
    get() {
      val form = activeDataCollectionForm ?: return emptyList()
      val base = allDatasetEntitiesForForm(form)
      val q = entityRefSearchQuery.trim()
      if (q.isEmpty()) return base
      return base.filter { entity ->
        entity.label.contains(q, ignoreCase = true) ||
          entity.geoId.contains(q, ignoreCase = true) ||
          entity.datasetName.contains(q, ignoreCase = true) ||
          entity.properties.values.any { it.contains(q, ignoreCase = true) }
      }
    }

  /**
   * True when the embedded form runner is open and the current step in [activeFormWizardController]
   * is an `entityref` step (`/data/target_entity` / `appearance="map-select"`) requiring the user
   * to select a geospatial entity via Map or List.
   */
  val isCurrentFormStepEntityRef: Boolean
    get() = isWizardStepEntityRef(activeFormWizardController?.currentStep)

  /**
   * True when the embedded form runner is open and the current step in [activeFormWizardController]
   * contains a `geopoint` (`DataType.TYPE_GEOPOINT`) question.
   */
  val isCurrentFormStepGeoPoint: Boolean
    get() = isWizardStepGeoPoint(activeFormWizardController?.currentStep)

  /** The currently active survey loaded in the Main Survey UI. */
  val activeSurvey: SurveyPreviewItem
    get() = surveys.firstOrNull { it.id == activeSurveyId } ?: surveys.first()

  /**
   * Surveys that have already been downloaded onto the device (shown on the `"Surveys"` screen
   * accessible from the navigation drawer).
   */
  val downloadedSurveys: List<SurveyPreviewItem>
    get() = surveys.filter { it.isDownloaded }

  /**
   * Surveys matching the current [searchQuery] on the "Download survey" screen by survey name,
   * description, or location.
   */
  val filteredSurveys: List<SurveyPreviewItem>
    get() = onboardingState.filteredSurveys

  val downloadedSurveyCount: Int
    get() = onboardingState.downloadedSurveyCount

  /**
   * Layers backed by `LayerDef.entity_dataset_id` (rendered with solid outlines) when the survey
   * has geospatial entities. Returns `emptyList()` when there are no geospatial entities so the map
   * layers section of the `Layers` sheet is hidden.
   */
  val entityDatasetLayers: List<MapLayerItem>
    get() = dashboardState.entityDatasetLayers

  /** True when the active survey has geospatial entities to display in the `Layers` sheet. */
  val hasGeospatialEntities: Boolean
    get() = entities.isNotEmpty() && entityDatasetLayers.isNotEmpty()

  /** Set of currently visible map layer IDs based on the "Layers" sheet toggles. */
  val visibleLayerIds: Set<String>
    get() = mapLayers.filter { it.isVisible }.map { it.id }.toSet()

  /** Geospatial entities currently visible on the map according to active layer visibility. */
  val visibleMapEntities: List<GeospatialEntityItem>
    get() = mapState.visibleMapEntities

  /** Currently visible entity dataset layers (`LayerSourceType.ENTITY_DATASET`). */
  val visibleEntityDatasetLayers: List<MapLayerItem>
    get() = dashboardState.visibleEntityDatasetLayers

  /**
   * User-facing plural category label for the `Map features` tab and list section header:
   * - When exactly 1 entity dataset layer is visible on the map, returns its plural domain label
   *   (e.g. `"Coffee Parcels"`, `"Monitoring Plots"`, `"Washing Stations"`).
   * - When multiple entity dataset layers are visible (or none), falls back to `"Map features"`.
   */
  val activeEntitiesTabLabel: String
    get() = dashboardState.activeEntitiesTabLabel

  /**
   * User-facing lowercase plural count noun for map counters, search hints, and empty states:
   * - When 1 entity dataset layer is visible, returns its lowercase plural domain label (e.g.
   *   `"coffee parcels"`, `"monitoring plots"`, `"washing stations"`).
   * - Otherwise falls back to `"map features"`.
   */
  val activeEntitiesCountNoun: String
    get() = mapState.activeEntitiesCountNoun

  /**
   * Resolves the user-facing singular domain noun for [entityId] (e.g. `"Coffee Parcel"`, or
   * `"Location"`).
   */
  fun entitySingularTypeLabel(entityId: String?): String = entities.singularTypeLabelOf(entityId)

  /** Resolves the dynamic display label for a [ListFilterTab] chip. */
  fun tabLabelFor(tab: ListFilterTab): String = dashboardState.tabLabelFor(tab)

  /**
   * Form submission geometries are not displayed on the map; only entity geometries (map features)
   * are shown.
   */
  val visibleSubmissionGeometries: List<SubmissionGeometryPolygon>
    get() = emptyList()

  /** ID of the currently selected map feature cluster when the map is zoomed out. */
  val selectedClusterId: String?
    get() = mapState.selectedClusterId

  /**
   * True when the map is zoomed out past the clustering threshold (about z12.95), causing visible
   * map features (`visibleMapEntities`) to be grouped into spatial clusters with cluster balloons
   * showing counts per marker symbol.
   */
  val isMapClusteringActive: Boolean
    get() = mapState.isMapClusteringActive

  /**
   * Normalized world-space clustering radius (`[0.20, 12.0]`) scaled exponentially as the user
   * zooms out (`2^(-mapZoomDelta)`). Returns `0f` when clustering is inactive.
   */
  val mapClusterRadiusNormalized: Float
    get() = viewModel.clusterMapFeaturesUseCase.clusterRadiusNormalized(mapZoomDelta)

  /**
   * All visible map features (`visibleMapEntities`) normalized into [MapClusterFeatureItem]
   * instances for clustering. Form submission geometries are not displayed on the map or in cluster
   * chips.
   */
  val visibleMapClusterFeatures: List<MapClusterFeatureItem>
    get() = visibleMapEntities.toClusterFeatures()

  /**
   * Spatial clusters of visible map features when [isMapClusteringActive] is `true`.
   *
   * Within each cluster, features are grouped by `marker-symbol` (including `""` for features with
   * no marker symbol as one group) and ordered canonically (`"✓"`, `"◐"`, `"○"`, custom symbols,
   * and `""` for no marker symbol) so the cluster balloon displays the count of each group.
   */
  val mapFeatureClusters: List<MapFeatureCluster>
    get() = mapState.mapFeatureClusters

  /** Currently selected [MapFeatureCluster] (if any and if clustering is active). */
  val selectedCluster: MapFeatureCluster?
    get() = mapState.selectedCluster

  /** The currently selected Geospatial Entity shown in the bottom sheet (if any). */
  val selectedEntity: GeospatialEntityItem?
    get() = mapState.selectedEntity

  /** All submissions (both entity-attached and standalone) in the active survey. */
  val allSubmissions: List<SubmissionPreviewItem>
    get() = entities.flatMap { it.submissions } + standaloneSubmissions

  /** The currently selected individual submission for full submission detail inspection. */
  val selectedSubmission: SubmissionPreviewItem?
    get() = selectedSubmissionId?.let { id -> allSubmissions.firstOrNull { it.id == id } }

  /**
   * Returns the forms in the active survey that request entities of [entity]'s dataset type and are
   * available on the asking platform: the web dashboard when [onWeb], otherwise the mobile app.
   */
  fun formsForEntity(
    entity: GeospatialEntityItem,
    onWeb: Boolean = false,
  ): List<FormPreviewItem> =
    (if (onWeb) webForms else mobileForms).filter { it.targetDatasetId == entity.datasetId }

  /**
   * Returns whether the organizer-defined action button for [form] is enabled on [entity]. Because
   * all forms are `1:N` with entities, any form targeting [entity]'s dataset is enabled.
   */
  fun isFormButtonEnabled(entity: GeospatialEntityItem, form: FormPreviewItem): Boolean =
    !form.requiresEntity || entity.datasetId == form.targetDatasetId

  /** Number of entities currently in [SyncStatus.UPLOADING] state. */
  val uploadingEntityCount: Int
    get() = entities.count { it.syncStatus == SyncStatus.UPLOADING }

  /** Number of entities currently in [SyncStatus.SYNCED] state. */
  val syncedEntityCount: Int
    get() = entities.count { it.syncStatus == SyncStatus.SYNCED }

  /** Number of entities currently in [SyncStatus.FAILED] state. */
  val failedEntityCount: Int
    get() = entities.count { it.syncStatus == SyncStatus.FAILED }

  /** Number of form submissions currently in [SyncStatus.UPLOADING] state. */
  val uploadingSubmissionCount: Int
    get() = allSubmissions.count { it.syncStatus == SyncStatus.UPLOADING }

  /** Number of form submissions currently in [SyncStatus.SYNCED] state. */
  val syncedSubmissionCount: Int
    get() = allSubmissions.count { it.syncStatus == SyncStatus.SYNCED }

  /** Number of form submissions currently in [SyncStatus.FAILED] state. */
  val failedSubmissionCount: Int
    get() = allSubmissions.count { it.syncStatus == SyncStatus.FAILED }

  /**
   * Simulates device Airplane mode (`Offline`) in the UX Workbench.
   *
   * When `true`, Mapbox Places API search is disabled (`filteredListPlaces` returns `emptyList()`)
   * and the bottom sheet shows an offline notice explaining that search is restricted to local map
   * features and that Places search is not available offline.
   */
  val isAirplaneMode: Boolean
    get() = mapState.isAirplaneMode

  /** Whether online Mapbox Places API search is currently available (`!isAirplaneMode`). */
  val isPlacesSearchAvailable: Boolean
    get() = mapState.isPlacesSearchAvailable

  /** Live place results returned by [PlacesGeocoder] (Mapbox Geocoding or Nominatim). */
  var mapboxPlacesApiResults by mutableStateOf<List<SurveyPlaceItem>>(emptyList())
    private set

  /** Whether an asynchronous Mapbox Places API request is currently in flight. */
  var isMapboxPlacesSearching by mutableStateOf(false)
    private set

  /** Currently selected [SurveyPlaceItem] from Mapbox Places search (if any). */
  val selectedPlace: SurveyPlaceItem?
    get() = mapState.selectedPlace

  /**
   * Filtered Places ([SurveyPlaceItem]s) in the Main Survey searchable bottom sheet returned by the
   * Mapbox Places API (`mapbox.places`) and regional place gazetteer matching [listSearchQuery].
   *
   * Returns `emptyList()` when [listSearchQuery] is blank or when [isAirplaneMode] is `true`
   * because Places results only appear when the user enters a search query while online.
   */
  val filteredListPlaces: List<SurveyPlaceItem>
    get() {
      val (surveyLng, surveyLat) = activeSurveyBaseLngLat()
      return viewModel.searchPlacesUseCase(
        query = listSearchQuery,
        isAirplaneMode = isAirplaneMode,
        listFilterTab = listFilterTab,
        localPlaces = places,
        remoteApiPlaces = mapboxPlacesApiResults,
        surveyBaseLng = surveyLng,
        surveyBaseLat = surveyLat,
        surveyLocationLabel = activeSurvey.location,
      )
    }

  /**
   * Parses a coordinate string (e.g. `"0.5012° S, 36.9324° E"`, `"0.4160°S, 36.9465°E"`, or
   * `"-0.5012, 36.9324"`) into a `(latitude, longitude)` pair.
   */
  fun parsePlaceCoordinates(coordinatesLabel: String): Pair<Double, Double>? =
    org.groundplatform.v2.devtools.prototypeapp.domain.model.parsePlaceCoordinates(coordinatesLabel)

  /**
   * Parses a decimal coordinate query (e.g. `"-0.4210, 36.9505"` or `"0.4210° S, 36.9505° E"`) into
   * an ad-hoc [SurveyPlaceItem] within the survey map bounds, or returns `null` if not a coordinate
   * pair.
   */
  private fun parseCoordinateQueryToPlace(query: String): SurveyPlaceItem? {
    val (surveyLng, surveyLat) = activeSurveyBaseLngLat()
    return viewModel.searchPlacesUseCase.parseCoordinateQueryToPlace(
      query = query,
      surveyBaseLng = surveyLng,
      surveyBaseLat = surveyLat,
      surveyLocationLabel = activeSurvey.location,
    )
  }

  /** Filtered Geospatial Entities in the Main Survey `List` view matching [listSearchQuery]. */
  val filteredListEntities: List<GeospatialEntityItem>
    get() {
      if (listFilterTab != ListFilterTab.ALL && listFilterTab != ListFilterTab.ENTITIES) {
        return emptyList()
      }
      val q = listSearchQuery.trim()
      if (q.isEmpty()) return entities
      return entities.filter { entity ->
        entity.label.contains(q, ignoreCase = true) ||
          entity.geoId.contains(q, ignoreCase = true) ||
          entity.datasetName.contains(q, ignoreCase = true) ||
          entity.syncStatus.label.contains(q, ignoreCase = true) ||
          entity.properties.values.any { it.contains(q, ignoreCase = true) }
      }
    }

  /**
   * Form submissions are not searchable via the bottom-sheet search bar because submissions have no
   * unique key to identify them with; returns an empty list.
   */
  val filteredListSubmissions: List<SubmissionPreviewItem>
    get() = emptyList()

  /**
   * Map features ([GeospatialEntityItem]s) in the Main Survey `List` view grouped by their entity
   * dataset (`Map features` category).
   */
  val groupedFilteredListEntities: List<EntityDatasetFeaturesGroup>
    get() {
      val matchingEntities = filteredListEntities
      if (matchingEntities.isEmpty()) return emptyList()
      return matchingEntities
        .groupBy { it.datasetName }
        .map { (datasetName, entities) ->
          val layer = entityDatasetLayers.firstOrNull { it.id == entities.first().layerId }
          EntityDatasetFeaturesGroup(layer = layer, datasetName = datasetName, entities = entities)
        }
    }

  /**
   * Form submissions are excluded from the searchable bottom sheet list since there is no unique
   * key to identify them with; returns an empty list.
   */
  val groupedFilteredListSubmissions: List<FormSubmissionsGroup>
    get() = emptyList()

  /**
   * Submissions for a specific [entity] grouped by their parent [FormPreviewItem], using the form's
   * title (`form.title`) as the group heading.
   */
  fun groupedSubmissionsForEntity(entity: GeospatialEntityItem): List<FormSubmissionsGroup> {
    if (entity.submissions.isEmpty()) return emptyList()
    val groupedByFormId = entity.submissions.groupBy { it.formId }
    return groupedByFormId.map { (formId, subs) ->
      val baseForm =
        forms.firstOrNull { it.id == formId }
          ?: FormPreviewItem(
            id = formId,
            title = subs.first().formTitle,
            description = "",
            version = subs.first().formVersion,
            targetDatasetId = entity.datasetId,
            targetDatasetName = entity.datasetName,
            questionCount = subs.first().fields.size,
            ctaLabel = subs.first().formTitle,
          )
      FormSubmissionsGroup(form = baseForm, submissions = subs)
    }
  }

  /**
   * True when [submission] is stored on this device, i.e. it was recorded locally and appears in
   * the local mutation log. Other collectors' submissions are only fetched when online.
   */
  fun isSubmissionStoredOnDevice(submission: SubmissionPreviewItem): Boolean =
    dashboardState.isSubmissionStoredOnDevice(submission)

  /**
   * Submissions of [entity] available to show, grouped by form. Seeing the full list requires a
   * connection: while offline ([isAirplaneMode]) only submissions stored on the device are listed.
   */
  fun availableGroupedSubmissionsForEntity(
    entity: GeospatialEntityItem
  ): List<FormSubmissionsGroup> {
    val groups = groupedSubmissionsForEntity(entity)
    if (!isAirplaneMode) return groups
    return groups
      .map { group ->
        group.copy(submissions = group.submissions.filter(::isSubmissionStoredOnDevice))
      }
      .filter { it.submissions.isNotEmpty() }
  }

  /**
   * Resolves a property [value] of [entity] that references another record in the survey (a foreign
   * key holding that record's ID or GeoID), or `null` when the value isn't a reference. Related
   * records are resolved regardless of whether they have geometry.
   */
  fun relatedEntityForPropertyValue(
    entity: GeospatialEntityItem,
    value: String,
  ): GeospatialEntityItem? = entities.relatedEntityForPropertyValue(entity, value)

  /** Directly switches the active mobile screen (used by both flow buttons and UX workbench). */
  fun navigateTo(screen: PrototypeScreen) {
    if (screen == PrototypeScreen.DOWNLOAD_SURVEY) {
      onboarding.setEntryOrigin(
        if (
          currentScreen == PrototypeScreen.MAIN_SURVEY &&
            activeDrawerSubView == MainDrawerSubView.SWITCH_SURVEYS
        ) {
          DownloadSurveyEntryOrigin.SURVEY_LIST
        } else {
          DownloadSurveyEntryOrigin.AFTER_TOS
        }
      )
    } else {
      onboarding.dismissSignOutPrompt()
    }
    currentScreen = screen
    activeSurveyNotice = null
    isDrawerOpen = false
    activeDrawerSubView = MainDrawerSubView.NONE
  }

  /** Authenticates with Google and advances to the Terms of Service screen. */
  fun signInWithGoogle() = onboarding.signInWithGoogle()

  /** Updates the Terms of Service agreement checkbox state. */
  fun setTermsChecked(checked: Boolean) = onboarding.setTermsChecked(checked)

  /** Accepts the Terms of Service and advances to the Download Survey screen. */
  fun acceptTermsOfService() = onboarding.acceptTermsOfService()

  /** Declines the Terms of Service and returns to the Sign In page. */
  fun declineTermsOfService() = onboarding.declineTermsOfService()

  /**
   * Handles the Back escape hatch on the Download surveys screen: returns to the Survey list when
   * opened from there, otherwise asks for confirmation before signing out.
   */
  fun navigateBackFromDownloadSurvey() = onboarding.navigateBackFromDownloadSurvey()

  /** Confirms signing out from the Download surveys screen back-action confirmation prompt. */
  fun confirmDownloadSurveySignOut() = onboarding.confirmSignOut()

  /** Cancels/dismisses the sign-out confirmation prompt on the Download surveys screen. */
  fun dismissDownloadSurveySignOutPrompt() = onboarding.dismissSignOutPrompt()

  /** Updates the search bar query used to filter surveys by name or location. */
  fun updateSearchQuery(query: String) = onboarding.updateSearchQuery(query)

  /** Clears the search bar query to show all shared surveys. */
  fun clearSearchQuery() = onboarding.clearSearchQuery()

  /** Marks the specified survey as downloaded onto the device for offline field use. */
  fun downloadSurvey(surveyId: String) = onboarding.downloadSurvey(surveyId)

  /** Opens a survey in the Main Survey UI (downloading it first if not already downloaded). */
  fun openSurvey(surveyId: String) = onboarding.openSurvey(surveyId)

  /**
   * Opens a survey in the web app (dashboard and Survey editor). Unlike [openSurvey], this doesn't
   * download the survey for offline use or move the mobile preview to the Main Survey UI.
   */
  fun openSurveyOnWeb(surveyId: String) = dashboard.openSurveyOnWeb(surveyId)

  /** Makes [surveyId] the active survey and clears every selection scoped to the previous one. */
  private fun activateSurvey(surveyId: String) {
    viewModel.launch { viewModel.surveyRepository.setActiveSurveyId(surveyId) }
    dashboard.clearLayerSelection()
    clearSelectionsForActivatedSurvey(surveyId)
  }

  /**
   * Clears every shell selection scoped to the survey active before [surveyId] (map feature,
   * submission, open form) and loads the survey's primary form into the XForms workbench.
   */
  private fun clearSelectionsForActivatedSurvey(surveyId: String) {
    data.surveyConfigs[surveyId]?.primaryFormXml?.let { xml ->
      selectedWorkbenchExampleForm =
        WorkbenchExampleForm.entries.firstOrNull { it.xformsXml == xml }
      customXFormsXml = xml
      customFormDef = XFormsParseCache.formDef(xml)
      xformsXmlError = null
    }
    surveyMap.setSelectedEntity(null)
    selectedSubmissionId = null
    activeDataCollectionEntityId = null
    activeDataCollectionFormId = null
    activeFormWizardController = null
    isAvailableFormsSheetOpen = false
    surveyMap.updateEntityBottomSheetExpanded(false)
  }

  /** Whether the active survey exists in the store, so survey pages can open it. */
  val hasOpenableActiveSurvey: Boolean
    get() = dashboardState.hasOpenableActiveSurvey

  /**
   * Creates a new, empty survey owned by the signed-in user, optionally in [organizationId], makes
   * it active, and returns its ID. The caller normally opens the Survey editor next.
   */
  fun createSurvey(title: String, organizationId: String? = null): String =
    dashboard.createSurvey(title, organizationId)

  /** Updates the title and description of the currently active survey. */
  fun updateActiveSurveyDetails(title: String, description: String) {
    storeSurveys(
      surveys.map { item ->
        if (item.id == activeSurveyId) {
          item.copy(
            title = title.ifBlank { item.title },
            description = description.ifBlank { item.description },
          )
        } else {
          item
        }
      }
    )
  }

  /** Toggles the downloaded status of a survey (for UX prototyping & testing). */
  fun toggleSurveyDownloaded(surveyId: String) {
    storeSurveys(
      surveys.map { item ->
        if (item.id == surveyId) {
          val nextState = !item.isDownloaded
          activeSurveyNotice =
            if (nextState) {
              "Downloaded \"${item.title}\" (${item.offlineSizeLabel}) for offline use."
            } else {
              "Removed offline copy of \"${item.title}\"."
            }
          item.copy(isDownloaded = nextState)
        } else {
          item
        }
      }
    )
  }

  /** Asks before removing [surveyId]'s offline copy; downloads it instead if not downloaded. */
  fun promptRemoveDownloadedSurvey(surveyId: String) =
    onboarding.promptRemoveDownloadedSurvey(surveyId)

  /** Confirms removal of the pending downloaded survey from the device and dismisses the dialog. */
  fun confirmRemoveDownloadedSurvey() = onboarding.confirmRemoveDownloadedSurvey()

  /** Dismisses the remove-downloaded-survey confirmation dialog without changes. */
  fun dismissRemoveDownloadedSurvey() = onboarding.dismissRemoveDownloadedSurvey()

  // --- Main Survey UI Actions ---

  /**
   * Switches between the collapsed Map peek state (`MAP`) and the expanded Searchable List state
   * (`LIST`) inside the unified persistent bottom sheet on the Main Survey screen.
   */
  fun setMainSurveyViewMode(mode: MainSurveyViewMode) {
    mainViewMode = mode
    activeDrawerSubView = MainDrawerSubView.NONE
    if (mode == MainSurveyViewMode.LIST) {
      surveyMap.setSelectedEntity(null)
      selectedSubmissionId = null
      surveyMap.updateEntityBottomSheetExpanded(true)
      surveyMap.updateLayersSheetOpen(false)
    } else {
      surveyMap.updateEntityBottomSheetExpanded(false)
    }
  }

  /** Opens or closes the Hamburger Navigation Drawer. */
  fun updateDrawerOpen(open: Boolean) {
    isDrawerOpen = open
  }

  /** Toggles the "Layers" visibility popover sheet on the Map view. */
  fun updateLayersSheetOpen(open: Boolean) = surveyMap.updateLayersSheetOpen(open)

  /**
   * Selects between `Normal` (`BasemapType.NORMAL`) and `Satellite` (`BasemapType.SATELLITE`)
   * basemap.
   */
  fun selectBasemapType(type: BasemapType) = surveyMap.selectBasemapType(type)

  /** Toggles between `Normal` and `Satellite` basemap. */
  fun toggleBasemapType() = surveyMap.toggleBasemapType()

  /** Toggles visibility of the downloaded Mapbox offline basemap in the `Layers` dialog. */
  fun toggleOfflineBasemapVisibility() = surveyMap.toggleOfflineBasemapVisibility()

  /** Updates the offline basemap rendering style (`SATELLITE_HYBRID` vs `VECTOR_TOPO`). */
  fun updateOfflineBasemapStyle(style: OfflineBasemapStyle) =
    surveyMap.updateOfflineBasemapStyle(style)

  /**
   * Selects a submission geometry polygon on the map and opens its submission (and parent entity if
   * attached, or standalone submission card if unattached).
   */
  fun selectSubmissionGeometry(geometryId: String) {
    val geom = submissionGeometries.firstOrNull { it.id == geometryId } ?: return
    clearSelectedPlace()
    surveyMap.setSelectedEntity(geom.entityId.takeIf { it.isNotBlank() })
    selectedSubmissionId = geom.submissionId
    surveyMap.updateEntityBottomSheetExpanded(true)
    surveyMap.updateLayersSheetOpen(false)
  }

  /**
   * Toggles visibility of a specific `LayerDef` on the survey map. Hiding the selected map
   * feature's layer clears the selection (see [onSurveyMapEvent]).
   */
  fun toggleLayerVisibility(layerId: String) = surveyMap.toggleLayerVisibility(layerId)

  /**
   * Selects a Geospatial Entity on the map to open its bottom sheet in collapsed/peek state. The
   * rest of the shell (list selection, details pane, dashboard table) follows via
   * [SurveyMapEvent.EntitySelected].
   */
  fun selectEntity(entityId: String?) = surveyMap.selectEntity(entityId)

  /**
   * Selects a Geospatial Entity from the bottom sheet list. The map pans and zooms to the entity,
   * and the sheet settles at its peek height showing the entity's details, so both the framed
   * entity and its details are visible. Dragging the sheet up reveals the rest of the details.
   */
  fun selectEntityFromList(entityId: String) = surveyMap.selectEntity(entityId)

  /** Shows the selected entity's `1:N` submissions in its details surface. */
  fun showEntitySubmissions() = dashboard.showEntitySubmissions()

  /** Returns the selected entity's details surface to its properties. */
  fun showEntityProperties() {
    dashboard.showEntityProperties()
    selectedSubmissionId = null
  }

  /**
   * Selects a tab of the web dashboard's entity details card: `Data`
   * ([EntityDetailsPane.PROPERTIES]) or `History` ([EntityDetailsPane.SUBMISSIONS]). Unlike
   * [showEntityProperties], an opened submission stays open, so switching back to `History` shows
   * it again.
   */
  fun selectEntityDetailsTab(pane: EntityDetailsPane) = dashboard.selectEntityDetailsTab(pane)

  /** Whether a map feature or submission is selected, so collapsing the table shows its details. */
  private val hasSelectedRecord: Boolean
    get() = selectedEntityId != null || selectedSubmissionId != null

  /** Expands or collapses the web dashboard's bottom data table. */
  fun updateDashboardTableExpanded(expanded: Boolean) =
    dashboard.updateDashboardTableExpanded(expanded, hasSelection = hasSelectedRecord)

  /**
   * Entity dataset IDs whose map features are collapsed (hidden) under their dataset header row in
   * the web dashboard's left-hand panel list. Toggled with the chevron left of the dataset name.
   */
  val collapsedListDatasetIds: Set<String>
    get() = dashboardState.collapsedListDatasetIds

  /** Collapses or expands the map features of the entity dataset with [datasetId] in the list. */
  fun toggleListDatasetCollapsed(datasetId: String) =
    dashboard.toggleListDatasetCollapsed(datasetId)

  /**
   * Whether the map features of the entity dataset with [datasetId] are currently hidden in the
   * list. While a list search query is active, every dataset is shown expanded so matches are never
   * hidden; the stored collapsed state is restored once the query is cleared.
   */
  fun isListDatasetCollapsed(datasetId: String): Boolean =
    dashboardState.isListDatasetCollapsed(datasetId)

  /**
   * Selects the map layer or data table of the entity dataset with [datasetId] in the web
   * dashboard, opening its details card; `null` clears the layer selection. Selecting a layer
   * clears any selected map feature, submission, or place.
   */
  fun selectLayer(datasetId: String?) {
    dashboard.selectLayer(datasetId)
    if (datasetId != null) {
      clearSelectedPlace()
      surveyMap.setSelectedEntity(null)
      selectedSubmissionId = null
    }
  }

  /** "Show in table": expands the web dashboard's bottom data table on the selected layer. */
  fun showSelectedLayerInTable() = dashboard.showSelectedLayerInTable()

  /** Toggles the web dashboard's bottom data table between expanded and collapsed. */
  fun toggleDashboardTableExpanded() =
    dashboard.toggleDashboardTableExpanded(hasSelection = hasSelectedRecord)

  /** Activates the bottom data table tab for the dataset with [datasetId]. */
  fun selectDashboardTable(datasetId: String) = dashboard.selectDashboardTable(datasetId)

  /**
   * "Show in table": expands the web dashboard's bottom data table on the selected entity's
   * dataset, where its row is highlighted and scrolled into view.
   */
  fun showSelectedEntityInTable() {
    val entity = selectedEntity ?: return
    dashboard.showDatasetInTable(entity.datasetId)
  }

  /**
   * Selects a [SurveyPlaceItem] from Mapbox Places search, panning and zooming the map camera to
   * fit the geographic bounds/extent of the identified place (`[targetZoom]`) in
   * [LocationLockState.PANNED] mode, flying the live `mapboxgl.Map` to the place's bounds, and
   * collapsing the bottom sheet so the map viewport is visible.
   */
  fun selectPlace(placeId: String) {
    val place = findPlaceById(placeId) ?: return
    surveyMap.selectPlace(place)
  }

  /** Clears the currently selected place on the map. */
  fun clearSelectedPlace() = surveyMap.clearSelectedPlace()

  /**
   * Clears the selected entity or submission and returns to the expanded searchable list inside the
   * persistent bottom sheet.
   */
  fun returnToBottomSheetList() {
    surveyMap.setSelectedEntity(null)
    selectedSubmissionId = null
    dashboard.showEntityProperties()
    surveyMap.updateEntityBottomSheetExpanded(true)
    mainViewMode = MainSurveyViewMode.LIST
    surveyMap.updateLayersSheetOpen(false)
  }

  /** Toggles the Entity Bottom Sheet between expanded and collapsed (peek) state. */
  fun toggleEntityBottomSheetExpanded() = surveyMap.toggleEntityBottomSheetExpanded()

  /** Explicitly expands or collapses the Entity Bottom Sheet. */
  fun updateEntityBottomSheetExpanded(expanded: Boolean) =
    surveyMap.updateEntityBottomSheetExpanded(expanded)

  /** Toggles the web dashboard's left-hand side panel between expanded and collapsed states. */
  fun toggleSidePanel() = dashboard.toggleSidePanel()

  /** Expands the web dashboard's left-hand side panel. */
  fun expandSidePanel() = dashboard.expandSidePanel()

  /** Collapses the web dashboard's left-hand side panel. */
  fun collapseSidePanel() = dashboard.collapseSidePanel()

  /** Explicitly expands or collapses the web dashboard's left-hand side panel. */
  fun updateSidePanelExpanded(expanded: Boolean) = dashboard.updateSidePanelExpanded(expanded)

  /** Alias for [toggleSidePanel]. */
  fun toggleDashboardSidePanel() = toggleSidePanel()

  /** Alias for [expandSidePanel]. */
  fun expandDashboardSidePanel() = expandSidePanel()

  /** Alias for [collapseSidePanel]. */
  fun collapseDashboardSidePanel() = collapseSidePanel()

  /** Alias for [updateSidePanelExpanded]. */
  fun updateDashboardSidePanelExpanded(expanded: Boolean) = updateSidePanelExpanded(expanded)

  /**
   * Sets the web dashboard's left-hand panel width to [widthDp], clamped to
   * [MIN_SIDE_PANEL_WIDTH_DP]..[MAX_SIDE_PANEL_WIDTH_DP]. Non-finite values are ignored.
   */
  fun updateSidePanelWidth(widthDp: Float) = dashboard.updateSidePanelWidth(widthDp)

  /** Toggles the web dashboard's right-hand details panel between expanded and collapsed states. */
  fun toggleDetailsPanel() = dashboard.toggleDetailsPanel()

  /** Expands the web dashboard's right-hand details panel. */
  fun expandDetailsPanel() = dashboard.expandDetailsPanel()

  /** Collapses the web dashboard's right-hand details panel. */
  fun collapseDetailsPanel() = dashboard.collapseDetailsPanel()

  /** Explicitly expands or collapses the web dashboard's right-hand details panel. */
  fun updateDetailsPanelExpanded(expanded: Boolean) = dashboard.updateDetailsPanelExpanded(expanded)

  /** Alias for [toggleDetailsPanel]. */
  fun toggleDashboardDetailsPanel() = toggleDetailsPanel()

  /** Alias for [expandDetailsPanel]. */
  fun expandDashboardDetailsPanel() = expandDetailsPanel()

  /** Alias for [collapseDetailsPanel]. */
  fun collapseDashboardDetailsPanel() = collapseDetailsPanel()

  /** Alias for [updateDetailsPanelExpanded]. */
  fun updateDashboardDetailsPanelExpanded(expanded: Boolean) = updateDetailsPanelExpanded(expanded)

  /** Alias for [toggleDetailsPanel]. */
  fun toggleRightPanel() = toggleDetailsPanel()

  /** Alias for [expandDetailsPanel]. */
  fun expandRightPanel() = expandDetailsPanel()

  /** Alias for [collapseDetailsPanel]. */
  fun collapseRightPanel() = collapseDetailsPanel()

  /** Alias for [updateDetailsPanelExpanded]. */
  fun updateRightPanelExpanded(expanded: Boolean) = updateDetailsPanelExpanded(expanded)

  /** Opens full details for a specific submission (from a 1:N entity bottom sheet or List view). */
  fun selectSubmissionDetail(submissionId: String?) {
    selectedSubmissionId = submissionId
    if (submissionId != null) {
      surveyMap.updateEntityBottomSheetExpanded(true)
      val parentEntity = entities.firstOrNull { e -> e.submissions.any { it.id == submissionId } }
      // Opening a submission of another map feature (e.g. from `Uploads`) frames that feature.
      val framesOtherEntity = parentEntity != null && parentEntity.id != selectedEntityId
      dashboard.onSubmissionOpened(
        tableDatasetId = if (framesOtherEntity) parentEntity?.datasetId else null
      )
      surveyMap.setSelectedEntity(parentEntity?.id, bumpSelectionEpoch = framesOtherEntity)
    }
  }

  /**
   * Updates the search query in the Main Survey searchable bottom sheet and queries the Mapbox
   * Places API (`mapbox.places`) when online (`!isAirplaneMode`).
   */
  fun updateListSearchQuery(query: String) {
    dashboard.updateListSearchQuery(query)
    val trimmed = query.trim()
    if (isAirplaneMode || trimmed.isEmpty()) {
      isMapboxPlacesSearching = false
      mapboxPlacesApiResults = emptyList()
    } else {
      triggerMapboxPlacesApiSearch(trimmed)
    }
  }

  /** Clears the search query in the Main Survey `List` view. */
  fun clearListSearchQuery() {
    dashboard.clearListSearchQuery()
    isMapboxPlacesSearching = false
    mapboxPlacesApiResults = emptyList()
  }

  /**
   * Updates the active category filter tab (`All`, `Places`, `Map features`).
   *
   * When [isAirplaneMode] is `true` (Offline), selecting [ListFilterTab.PLACES] is blocked and
   * surfaces an offline notice.
   */
  fun selectListFilterTab(tab: ListFilterTab) {
    if (isAirplaneMode && tab == ListFilterTab.PLACES) {
      activeSurveyNotice =
        "Device offline: Places search is not available offline. Searching local $activeEntitiesCountNoun only."
      return
    }
    dashboard.selectListFilterTab(tab)
  }

  /**
   * Enables or disables **Airplane mode** (`Offline` simulator) in the UX Workbench.
   *
   * When enabled (`true`):
   * - Disables Mapbox Places API search (`filteredListPlaces` becomes empty).
   * - Switches [listFilterTab] away from [ListFilterTab.PLACES] if currently selected.
   * - Shows a message in the bottom sheet that search is only in local map features and Places
   *   search is not available offline.
   */
  fun updateAirplaneMode(enabled: Boolean) {
    surveyMap.updateAirplaneMode(enabled)
    if (enabled) {
      isMapboxPlacesSearching = false
      mapboxPlacesApiResults = emptyList()
      if (listFilterTab == ListFilterTab.PLACES) {
        dashboard.selectListFilterTab(ListFilterTab.ALL)
      }
      activeSurveyNotice =
        "Device offline: Places search disabled. Searching local $activeEntitiesCountNoun only."
    } else {
      activeSurveyNotice = "Device online: Places API search enabled."
      val trimmed = listSearchQuery.trim()
      if (trimmed.isNotEmpty()) {
        triggerMapboxPlacesApiSearch(trimmed)
      }
    }
  }

  /** Toggles [isAirplaneMode] between Online (`false`) and Offline (`true`). */
  fun toggleAirplaneMode() {
    updateAirplaneMode(!isAirplaneMode)
  }

  private fun triggerMapboxPlacesApiSearch(query: String) {
    if (isAirplaneMode || query.isBlank()) {
      isMapboxPlacesSearching = false
      mapboxPlacesApiResults = emptyList()
      return
    }
    isMapboxPlacesSearching = true
    val (surveyLng, surveyLat) = activeSurveyBaseLngLat()
    searchPlaces(
      surveyId = activeSurveyId,
      query = query,
      regionSubtitle = activeSurvey.location,
      center =
        org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.LatLng(
          lat = surveyLat,
          lng = surveyLng,
        ),
    ) { results ->
      onPlacesSearchResults(query, results)
    }
  }

  /**
   * Looks up places matching [query] near [center] for [surveyId] through the place repository, for
   * callers outside the main list search (such as the Survey editor's area picker).
   */
  fun searchPlaces(
    surveyId: String,
    query: String,
    regionSubtitle: String,
    center: org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.LatLng,
    onResults: (List<SurveyPlaceItem>) -> Unit,
  ) = surveyMap.searchPlaces(surveyId, query, regionSubtitle, center, onResults)

  /** Shows geocoder [results] for [query] unless the query changed or airplane mode is on. */
  internal fun onPlacesSearchResults(query: String, results: List<SurveyPlaceItem>) {
    if (!isAirplaneMode && listSearchQuery.trim() == query.trim()) {
      mapboxPlacesApiResults = results
    }
    isMapboxPlacesSearching = false
  }

  /** Opens the Available Forms modal bottom sheet triggered by the bottom-centered FAB. */
  fun openAvailableFormsSheet() {
    isAvailableFormsSheetOpen = true
    surveyMap.updateLayersSheetOpen(false)
    isDrawerOpen = false
  }

  /** Closes the Available Forms modal bottom sheet. */
  fun closeAvailableFormsSheet() {
    isAvailableFormsSheetOpen = false
  }

  /** Toggles the Available Forms modal bottom sheet open or closed. */
  fun toggleAvailableFormsSheet() {
    if (isAvailableFormsSheetOpen) {
      closeAvailableFormsSheet()
    } else {
      openAvailableFormsSheet()
    }
  }

  /**
   * Switches the `entityref` step picker between `MainSurveyViewMode.MAP` and
   * `MainSurveyViewMode.LIST`.
   */
  fun updateEntityRefSelectorViewMode(mode: MainSurveyViewMode) {
    entityRefSelectorViewMode = mode
  }

  /** Updates the search query in the `entityref` step's `List` selector. */
  fun updateEntityRefSearchQuery(query: String) {
    entityRefSearchQuery = query
  }

  /** Clears the search query in the `entityref` step's `List` selector. */
  fun clearEntityRefSearchQuery() {
    entityRefSearchQuery = ""
  }

  /**
   * Selects a target Geospatial Entity ([entityId]) at the `entityref` step (`/data/target_entity`)
   * during data collection when the form was launched without a pre-selected entity from the map.
   *
   * Updates [activeDataCollectionEntityId], [selectedEntityId], and populates
   * [ENTITY_REF_FIELD_PATH] (`/data/target_entity`) in [activeFormWizardController]. When the map
   * feature has geometry, the map camera is centered on it and [entityRefFramingEpoch] is bumped so
   * the step's map pans and zooms to fit the feature.
   */
  fun selectEntityRefForActiveForm(entityId: String) {
    val entity = entities.firstOrNull { it.id == entityId } ?: return
    val form = activeDataCollectionForm
    if (form != null && form.requiresEntity) {
      if (entity.datasetId != form.targetDatasetId || !isFormButtonEnabled(entity, form)) return
    }
    activeDataCollectionEntityId = entity.id
    surveyMap.setSelectedEntity(entity.id)
    if (entity.hasGeometry) {
      recenterMapOnEntity(entity)
      entityRefFramingEpoch++
    }
    activeFormWizardController?.updateString(ENTITY_REF_FIELD_PATH, entity.id)
    if (
      activeFormWizardController?.formState?.fieldStates?.containsKey("/data/sample_plot_entity") ==
        true
    ) {
      activeFormWizardController?.updateString("/data/sample_plot_entity", entity.id)
    }
    if (
      activeFormWizardController?.formState?.fieldStates?.containsKey("/data/past_individual_id") ==
        true
    ) {
      activeFormWizardController?.updateString("/data/past_individual_id", entity.id)
    }
    if (
      activeFormWizardController
        ?.formState
        ?.fieldStates
        ?.containsKey("/data/primary_respondent_id") == true
    ) {
      activeFormWizardController?.updateString("/data/primary_respondent_id", entity.id)
    }
  }

  /**
   * Updates [customXFormsXml], parses [FormDef] via [XFormsXmlSerializer.deserializeFormDef], and
   * updates [customFormDef] and [xformsXmlError]. If a form runner is currently open and the new
   * [FormDef] is valid, refreshes [activeFormWizardController] with the new [FormDef].
   */
  fun updateCustomXFormsXml(xml: String) {
    customXFormsXml = xml
    selectedWorkbenchExampleForm =
      WorkbenchExampleForm.entries.firstOrNull { it.xformsXml.trim() == xml.trim() }
        ?: if (xml.trim() == DEFAULT_PROTOTYPE_XFORMS_XML.trim()) {
          WorkbenchExampleForm.ALL_FIELD_TYPES
        } else {
          null
        }
    if (xml.isBlank()) {
      customFormDef = null
      xformsXmlError = null
      if (activeFormWizardController != null) {
        val currentForm = activeDataCollectionForm ?: forms.first()
        val fallbackFormDef =
          resolveFormDefForLaunch(
            customFormDef = null,
            form = currentForm,
            candidateEntities = eligibleEntitiesForForm(currentForm),
            defaultSelectedEntityId = activeDataCollectionEntityId.orEmpty(),
            includeEntityRefStep = wasFormLaunchedWithoutEntity && currentForm.requiresEntity,
          )
        activeFormWizardController = FormWizardController(formDef = fallbackFormDef)
      }
      return
    }
    try {
      val parsed = XFormsXmlSerializer.deserializeFormDef(xml)
      customFormDef = parsed
      xformsXmlError = null
      if (activeFormWizardController != null) {
        val currentForm = activeDataCollectionForm ?: forms.first()
        val shouldIncludeEntityRefStep =
          wasFormLaunchedWithoutEntity &&
            currentForm.requiresEntity &&
            (selectedWorkbenchExampleForm == null)
        val refreshedFormDef =
          resolveFormDefForLaunch(
            customFormDef = parsed,
            form = currentForm,
            candidateEntities = eligibleEntitiesForForm(currentForm),
            defaultSelectedEntityId = activeDataCollectionEntityId.orEmpty(),
            includeEntityRefStep = shouldIncludeEntityRefStep,
          )
        activeFormWizardController = FormWizardController(formDef = refreshedFormDef)
      }
    } catch (e: Exception) {
      customFormDef = null
      xformsXmlError = e.message ?: "Invalid XForms XML"
    }
  }

  /**
   * Swaps the active example survey & form in the workbench to [example], switching to the
   * corresponding survey (`surveyIdForExampleForm(example)`) so the survey's preloaded `entities`,
   * `standaloneSubmissions`, `submissionGeometries`, `mapLayers`, and `forms` are loaded together.
   * When [launchImmediately] is `true`, opens the embedded `MobileFormRunner` wizard immediately.
   */
  fun selectWorkbenchExampleForm(
    example: WorkbenchExampleForm,
    launchImmediately: Boolean = false,
  ) {
    val targetSurveyId = surveyIdForExampleForm(example) ?: return
    openSurvey(targetSurveyId)
    selectedWorkbenchExampleForm = example
    activeSurveyNotice =
      "Switched to survey \"${activeSurvey.title}\" (${entities.size} entities, ${allSubmissions.size} preloaded submissions)."
    if (launchImmediately) {
      launchActiveOrDefaultFormForTesting()
    }
  }

  /** Restores the default sample XForms XML definition (`DEFAULT_PROTOTYPE_XFORMS_XML`). */
  fun resetDefaultXFormsXml() {
    if (activeSurveyId != "survey-kenya-coffee") {
      openSurvey("survey-kenya-coffee")
    }
    updateCustomXFormsXml(DEFAULT_PROTOTYPE_XFORMS_XML)
    selectedWorkbenchExampleForm = WorkbenchExampleForm.ALL_FIELD_TYPES
  }

  /**
   * Launches data collection for [formId] from the bottom-centered Floating Action Button (FAB)
   * list of available forms (**with no entity pre-selected from the map**).
   *
   * Because no geospatial entity was selected on the map prior to triggering the form, if the form
   * requires a geospatial entity (`form.requiresEntity == true`), [activeDataCollectionEntityId]
   * starts as `null` and the form wizard includes the required `entityref` step (`
   * [ENTITY_REF_FIELD_PATH] = "/data/target_entity"`). At that step in the data collection process,
   * the user is presented with the interactive **Map or List** selector to choose the target
   * entity.
   */
  fun launchFormFromFab(formId: String) {
    val form = forms.firstOrNull { it.id == formId } ?: return
    val candidates = eligibleEntitiesForForm(form)
    val resolvedFormDef =
      resolveFormDefForLaunch(
        customFormDef = customFormDef,
        form = form,
        candidateEntities = candidates,
        defaultSelectedEntityId = "",
        includeEntityRefStep = form.requiresEntity,
      )
    val controller = FormWizardController(formDef = resolvedFormDef)

    isAvailableFormsSheetOpen = false
    wasFormLaunchedWithoutEntity = true
    entityRefSelectorViewMode = mainViewMode
    entityRefSearchQuery = ""
    activeDataCollectionEntityId = null
    activeDataCollectionFormId = form.id
    activeFormWizardController = controller
    surveyMap.updateLayersSheetOpen(false)
    isDrawerOpen = false
    activeDrawerSubView = MainDrawerSubView.NONE
    if (currentScreen != PrototypeScreen.MAIN_SURVEY) {
      currentScreen = PrototypeScreen.MAIN_SURVEY
    }
  }

  /**
   * Launches the embedded [org.groundplatform.v2.core.forms.ui.MobileFormRunner] (`
   * [FormWizardController]`) for [formId] on [entityId] when the organizer-defined form button
   * (`ctaLabel`) is tapped in the entity bottom sheet or triggered from the UX Chrome tester.
   */
  fun launchFormForEntity(entityId: String, formId: String) {
    val entity = entities.firstOrNull { it.id == entityId } ?: return
    val form = forms.firstOrNull { it.id == formId } ?: return
    if (!isFormButtonEnabled(entity, form)) return

    val resolvedFormDef = resolveFormDefForLaunch(customFormDef, form)
    val controller = FormWizardController(formDef = resolvedFormDef)
    if (controller.formState.fieldStates.containsKey(ENTITY_REF_FIELD_PATH)) {
      controller.updateString(ENTITY_REF_FIELD_PATH, entity.id)
    }
    if (controller.formState.fieldStates.containsKey("/data/sample_plot_entity")) {
      controller.updateString("/data/sample_plot_entity", entity.id)
    }
    if (controller.formState.fieldStates.containsKey("/data/past_individual_id")) {
      controller.updateString("/data/past_individual_id", entity.id)
    }
    if (controller.formState.fieldStates.containsKey("/data/primary_respondent_id")) {
      controller.updateString("/data/primary_respondent_id", entity.id)
    }

    isAvailableFormsSheetOpen = false
    wasFormLaunchedWithoutEntity = false
    surveyMap.setSelectedEntity(entity.id)
    activeDataCollectionEntityId = entity.id
    activeDataCollectionFormId = form.id
    activeFormWizardController = controller
    surveyMap.updateLayersSheetOpen(false)
    isDrawerOpen = false
    activeDrawerSubView = MainDrawerSubView.NONE
    if (currentScreen != PrototypeScreen.MAIN_SURVEY) {
      currentScreen = PrototypeScreen.MAIN_SURVEY
    }
  }

  /**
   * Convenience trigger used by the `▶ Test / Launch Form Now` button in the Prototype App Chrome
   * (`XFormsFormDefChromeSection`) to launch the active XForms `FormDef` on the currently selected
   * entity (or as a standalone form when the survey has no predefined entities).
   */
  fun launchActiveOrDefaultFormForTesting() {
    if (entities.isEmpty()) {
      val standaloneForm = forms.firstOrNull { !it.requiresEntity } ?: forms.firstOrNull() ?: return
      launchFormFromFab(standaloneForm.id)
      return
    }
    val currentEntity =
      selectedEntity
        ?: entities.firstOrNull { it.id == "entity-shade-201" }
        ?: entities.firstOrNull()
    if (currentEntity != null) {
      val enabledFormOnCurrent =
        formsForEntity(currentEntity).firstOrNull { isFormButtonEnabled(currentEntity, it) }
      if (enabledFormOnCurrent != null) {
        launchFormForEntity(currentEntity.id, enabledFormOnCurrent.id)
        return
      }
    }
    val fallbackEntity = entities.firstOrNull { ent ->
      formsForEntity(ent).any { isFormButtonEnabled(ent, it) }
    }
    if (fallbackEntity != null) {
      val fallbackForm =
        formsForEntity(fallbackEntity).firstOrNull { isFormButtonEnabled(fallbackEntity, it) }
          ?: return
      launchFormForEntity(fallbackEntity.id, fallbackForm.id)
      return
    }
    val standaloneFallback = forms.firstOrNull() ?: return
    launchFormFromFab(standaloneFallback.id)
  }

  /**
   * Completes the active form submission when the user clicks `Submit ✓` in `MobileFormRunner` (or
   * when invoked programmatically with a finalized [recordInstance]).
   *
   * Extracts all answered fields from [recordInstance] (and
   * `activeFormWizardController?.formState`) using `formatFieldValueForDisplay` into
   * `List<SubmissionFieldEntry>`, appends a new [SubmissionPreviewItem] to the target entity's
   * `submissions` list, updates [activeSurveyNotice], and closes the active form runner.
   */
  fun completeActiveFormSubmission(
    recordInstance: RecordInstance =
      activeFormWizardController?.formState?.recordInstance ?: RecordInstance(),
    entityStates: List<EntityState> =
      activeFormWizardController?.formState?.entityStates ?: emptyList(),
  ) {
    syncViewModelState()
    val controller = activeFormWizardController
    viewModel.launch {
      val result =
        viewModel.completeFormSubmissionUseCase(
          recordInstance = recordInstance,
          entityStates = entityStates,
          controller = controller,
          activeDataCollectionFormId = activeDataCollectionFormId,
          activeDataCollectionEntityId = activeDataCollectionEntityId,
          wasFormLaunchedWithoutEntity = wasFormLaunchedWithoutEntity,
          selectedEntityId = selectedEntityId,
          customFormDef = customFormDef,
          gnssStatusChipLabel = gnssStatusChipLabel,
          signedInUserName = signedInUserName,
          signedInUserEmail = signedInUserEmail,
          userGpsCoordinatesLabel = userGpsCoordinatesLabel,
          userGpsNormalizedX = userGpsNormalizedX,
          userGpsNormalizedY = userGpsNormalizedY,
        ) ?: return@launch
      surveyMap.setSelectedEntity(result.selectedEntityId)
      if (result.updateSelectedSubmissionId) {
        selectedSubmissionId = result.selectedSubmissionId
      }
      activeSurveyNotice = result.noticeMessage
      closeActiveFormRunner()
    }
  }

  /**
   * Updates the [SyncStatus] of a specific [GeospatialEntityItem] ([entityId]) and synchronizes its
   * submissions accordingly when marked [SyncStatus.SYNCED].
   */
  fun updateEntitySyncStatus(entityId: String, newStatus: SyncStatus) {
    storeEntities(
      entities.map { item ->
        if (item.id == entityId) {
          val updatedSubmissions =
            if (newStatus == SyncStatus.SYNCED) {
              item.submissions.map { sub -> sub.copy(syncStatus = SyncStatus.SYNCED) }
            } else {
              item.submissions
            }
          item.copy(submissions = updatedSubmissions, syncStatus = newStatus)
        } else {
          item
        }
      }
    )
    val entity = entities.firstOrNull { it.id == entityId } ?: return
    activeSurveyNotice = "${entity.label}: Sync status set to ${newStatus.label}"
  }

  /**
   * Cycles the [SyncStatus] of a specific [GeospatialEntityItem] ([entityId]) (`Uploading` ->
   * `Synced` -> `Failed` -> `Uploading`), or retries failed uploads immediately.
   */
  fun cycleEntitySyncStatus(entityId: String) {
    val entity = entities.firstOrNull { it.id == entityId } ?: return
    updateEntitySyncStatus(entityId, entity.syncStatus.next())
  }

  /**
   * Updates the [SyncStatus] of a specific [SubmissionPreviewItem] ([submissionId]) and recomputes
   * the parent entity's aggregate [SyncStatus] (or updates [standaloneSubmissions]).
   */
  fun updateSubmissionSyncStatus(submissionId: String, newStatus: SyncStatus) {
    var updatedSubTitle: String? = null
    storeEntities(
      entities.map { item ->
        val hasTarget = item.submissions.any { it.id == submissionId }
        if (hasTarget) {
          val updatedSubmissions =
            item.submissions.map { sub ->
              if (sub.id == submissionId) {
                updatedSubTitle = sub.formTitle
                sub.copy(syncStatus = newStatus)
              } else {
                sub
              }
            }
          item.copy(
            submissions = updatedSubmissions,
            syncStatus =
              deriveEntitySyncStatus(
                updatedSubmissions,
                fallback =
                  if (newStatus == SyncStatus.SYNCED) SyncStatus.SYNCED else item.syncStatus,
              ),
          )
        } else {
          item
        }
      }
    )
    if (standaloneSubmissions.any { it.id == submissionId }) {
      storeStandaloneSubmissions(
        standaloneSubmissions.map { sub ->
          if (sub.id == submissionId) {
            updatedSubTitle = sub.formTitle
            sub.copy(syncStatus = newStatus)
          } else {
            sub
          }
        }
      )
    }
    if (updatedSubTitle != null) {
      activeSurveyNotice = "$updatedSubTitle: Sync status set to ${newStatus.label}"
    }
  }

  /**
   * Cycles the [SyncStatus] of a specific [SubmissionPreviewItem] ([submissionId]) (`Uploading` ->
   * `Synced` -> `Failed` -> `Uploading`).
   */
  fun cycleSubmissionSyncStatus(submissionId: String) {
    val submission = allSubmissions.firstOrNull { it.id == submissionId } ?: return
    updateSubmissionSyncStatus(submissionId, submission.syncStatus.next())
  }

  /** Closes the active `MobileFormRunner` and returns to the survey map/list screen. */
  fun closeActiveFormRunner() {
    activeFormWizardController = null
    activeDataCollectionEntityId = null
    activeDataCollectionFormId = null
    wasFormLaunchedWithoutEntity = false
    entityRefSearchQuery = ""
  }

  /** Opens the scannable S2 GeoID / Entity QR code modal dialog for [entityId]. */
  fun openEntityQrCode(entityId: String) {
    activeQrCodeEntityId = entityId
  }

  /** Closes the active Entity QR code modal dialog. */
  fun closeEntityQrCode() {
    activeQrCodeEntityId = null
  }

  /**
   * Generates a PDF report for the map feature [entityId] on the device (offline): its status,
   * details, location, properties, and submissions. Returns `null` for unknown IDs.
   */
  internal fun generateEntityPdf(entityId: String): GeneratedPdf? {
    val entity = entities.firstOrNull { it.id == entityId } ?: return null
    return RecordPdfReports.entityReport(
      entity = entity,
      surveyTitle = activeSurvey.title,
      geometry = EntityGeometry.of(entity, activeSurveyAnchor),
      unitSystem = unitSystem,
      generatedAtEpochMillis = platformEpochMillis(),
      relatedLabelFor = { value -> relatedEntityForPropertyValue(entity, value)?.label },
    )
  }

  /** Generates a PDF report for the submission [submissionId] on the device (offline). */
  internal fun generateSubmissionPdf(submissionId: String): GeneratedPdf? {
    val submission = allSubmissions.firstOrNull { it.id == submissionId } ?: return null
    return RecordPdfReports.submissionReport(
      submission = submission,
      surveyTitle = activeSurvey.title,
      entity = entities.firstOrNull { it.id == submission.entityId },
      generatedAtEpochMillis = platformEpochMillis(),
    )
  }

  /**
   * Generates the map feature's PDF and opens the Share PDF sheet to share, save, or preview it.
   */
  fun shareEntityPdf(entityId: String) {
    val entity = entities.firstOrNull { it.id == entityId } ?: return
    val pdf = generateEntityPdf(entityId) ?: return
    openPdfSheet(
      pdf,
      SharedPdfSheetState(
        targetId = entity.id,
        title = "${entity.singularTypeLabel} report",
        subtitle = "${entity.label} • GeoID ${entity.geoId}",
        pdfFileName = pdf.fileName,
        targetKindLabel = "${entity.singularTypeLabel} report",
        pageCount = pdf.pageCount,
        fileSizeLabel = pdf.sizeLabel,
      ),
    )
  }

  /** Generates the submission's PDF and opens the Share PDF sheet to share, save, or preview it. */
  fun shareSubmissionPdf(submissionId: String) {
    val sub = allSubmissions.firstOrNull { it.id == submissionId } ?: return
    val pdf = generateSubmissionPdf(submissionId) ?: return
    openPdfSheet(
      pdf,
      SharedPdfSheetState(
        targetId = sub.id,
        title = "${sub.formTitle} submission",
        subtitle = "${sub.collectorName} • ${sub.timestamp}",
        pdfFileName = pdf.fileName,
        targetKindLabel = "${sub.formTitle} submission",
        pageCount = pdf.pageCount,
        fileSizeLabel = pdf.sizeLabel,
      ),
    )
  }

  /** Generates the map feature's PDF and saves it straight away (web dashboard). */
  fun downloadEntityPdf(entityId: String) {
    generateEntityPdf(entityId)?.let(::savePdf)
  }

  /** Generates the submission's PDF and saves it straight away (web dashboard). */
  fun downloadSubmissionPdf(submissionId: String) {
    generateSubmissionPdf(submissionId)?.let(::savePdf)
  }

  /** True when the platform can hand files to other apps via the system share sheet. */
  val canSharePdfFiles: Boolean
    get() = platformCanShareFiles()

  /** Opens the system share sheet for the PDF in the Share PDF sheet. */
  fun shareActivePdf() {
    val pdf = activePdf ?: return
    val title = activeSharedPdfSheet?.title ?: pdf.fileName
    platformSharePdf(pdf.fileName, title, pdf.bytes) { result ->
      when (result) {
        PdfExportResult.SHARED -> closeSharePdfSheet()
        PdfExportResult.SAVED -> {
          closeSharePdfSheet()
          pdfExportMessage = "Saved ${pdf.fileName}"
        }
        PdfExportResult.CANCELLED -> Unit
        PdfExportResult.FAILED -> pdfExportMessage = "Couldn't share ${pdf.fileName}"
      }
    }
  }

  /** Saves the PDF in the Share PDF sheet to the device. */
  fun saveActivePdf() {
    val pdf = activePdf ?: return
    savePdf(pdf)
    closeSharePdfSheet()
  }

  /** Opens the PDF in the Share PDF sheet in the platform's viewer. */
  fun previewActivePdf() {
    val pdf = activePdf ?: return
    platformPreviewPdf(pdf.fileName, pdf.bytes)
  }

  /** Closes the active Share PDF modal sheet. */
  fun closeSharePdfSheet() {
    activeSharedPdfSheet = null
    activePdf = null
  }

  /** Clears [pdfExportMessage] once it has been shown. */
  fun dismissPdfExportMessage() {
    pdfExportMessage = null
  }

  private fun openPdfSheet(pdf: GeneratedPdf, sheet: SharedPdfSheetState) {
    activePdf = pdf
    activeSharedPdfSheet = sheet
  }

  private fun savePdf(pdf: GeneratedPdf) {
    platformSavePdf(pdf.fileName, pdf.bytes)
    pdfExportMessage = "Saved ${pdf.fileName} (${pdf.summaryLabel})"
  }

  // --- Hamburger Navigation Drawer Actions ---

  /**
   * Drawer option 1: "Surveys" — opens the dedicated screen showing only surveys that have already
   * been downloaded onto the device, with a primary button to browse & download more surveys.
   */
  fun drawerSwitchSurveys() {
    isDrawerOpen = false
    activeDrawerSubView = MainDrawerSubView.SWITCH_SURVEYS
  }

  /**
   * Drawer option: "Uploads" — opens the unified screen listing all mutations with status filter
   * chips (`Pending`, `In progress`, `Uploaded`, `Failed`).
   */
  fun drawerOpenUploads(filter: UploadStatusFilter? = null) = dashboard.showUploads(filter)

  /** Selects or clears the active [UploadStatusFilter] chip on the `Uploads` screen. */
  fun selectUploadStatusFilter(filter: UploadStatusFilter?) =
    dashboard.selectUploadStatusFilter(filter)

  /** Toggles [filter] on the `Uploads` screen (selecting it, or clearing it if already active). */
  fun toggleUploadStatusFilter(filter: UploadStatusFilter) =
    dashboard.toggleUploadStatusFilter(filter)

  /** Legacy helper: opens the `Outbox` sub-view (rendered by the unified `Uploads` screen). */
  fun drawerOpenOutbox() {
    isDrawerOpen = false
    dashboard.selectUploadStatusFilter(null)
    activeDrawerSubView = MainDrawerSubView.OUTBOX
  }

  /** Legacy helper: opens the `Uploaded` sub-view (rendered by the unified `Uploads` screen). */
  fun drawerOpenUploaded() {
    isDrawerOpen = false
    dashboard.selectUploadStatusFilter(UploadStatusFilter.UPLOADED)
    activeDrawerSubView = MainDrawerSubView.UPLOADED
  }

  /**
   * Synchronizes a single Outbox mutation ([mutationId]), transitioning its state to
   * [MutationSyncState.UPLOADED] with a completed timestamp and moving it into `Uploaded`.
   */
  fun syncMutationNow(mutationId: String) = dashboard.syncMutationNow(mutationId)

  /**
   * Synchronizes all currently pending/in-progress mutations in the `Outbox`, transitioning them to
   * [MutationSyncState.UPLOADED] with completed timestamps.
   */
  fun syncAllOutboxMutations() = dashboard.syncAllOutboxMutations()

  /** Navigates from the "Surveys" screen to the full "Download survey" directory screen. */
  fun openDownloadMoreSurveysScreen() {
    isDrawerOpen = false
    activeDrawerSubView = MainDrawerSubView.NONE
    onboarding.setEntryOrigin(DownloadSurveyEntryOrigin.SURVEY_LIST)
    currentScreen = PrototypeScreen.DOWNLOAD_SURVEY
  }

  /** Alias kept for compatibility: opens the "Surveys" sub-screen. */
  fun drawerSwitchOrDownloadSurveys() {
    drawerSwitchSurveys()
  }

  /** Drawer option: Offline maps. */
  fun drawerManageOfflineMaps() {
    isDrawerOpen = false
    activeDrawerSubView = MainDrawerSubView.MANAGE_OFFLINE_MAPS
  }

  /** Drawer option: Change settings. */
  fun drawerOpenSettings() {
    isDrawerOpen = false
    activeDrawerSubView = MainDrawerSubView.SETTINGS
  }

  /**
   * Closes the active drawer sub-view (Switch Surveys, Outbox, Uploaded, Offline Maps, or Settings)
   * and returns to the survey.
   */
  fun closeDrawerSubView() {
    activeDrawerSubView = MainDrawerSubView.NONE
    dashboard.clearUploadsEntityFilter()
  }

  /** Drawer option 4: View Terms of Service. */
  fun drawerViewTermsOfService() {
    isDrawerOpen = false
    activeDrawerSubView = MainDrawerSubView.NONE
    currentScreen = PrototypeScreen.TERMS_OF_SERVICE
  }

  /** Drawer option 5: Sign out. */
  fun drawerSignOut() {
    isDrawerOpen = false
    activeDrawerSubView = MainDrawerSubView.NONE
    onboarding.signOut()
    currentScreen = PrototypeScreen.SIGN_IN
  }

  /** Signs the user out of the application and returns to the Sign In screen. */
  fun signOut() {
    drawerSignOut()
  }

  /** Toggles download status of an offline Mapbox basemap tile package. */
  fun toggleOfflineTilePackage(packageId: String) = settings.toggleOfflineTilePackage(packageId)

  /**
   * Prompts the user before removing an offline map tile package from the device. If the package is
   * not downloaded, downloads it immediately.
   */
  fun promptRemoveOfflineTilePackage(packageId: String) =
    settings.promptRemoveOfflineTilePackage(packageId)

  /**
   * Confirms removal of the pending offline tile package from the device and dismisses the dialog.
   */
  fun confirmRemoveOfflineTilePackage() = settings.confirmRemoveOfflineTilePackage()

  /** Dismisses/cancels the pending offline tile package removal dialog. */
  fun dismissRemoveOfflineTilePackage() = settings.dismissRemoveOfflineTilePackage()

  /** Updates the measurement unit preference (`METRIC` vs `IMPERIAL`). */
  fun updateUnitSystem(system: MeasurementUnitSystem) = settings.updateMeasurementUnits(system)

  /**
   * Updates the active application & survey language using either a language code (e.g. `"en"`,
   * `"fr"`) or a formatted locale string (e.g. `"fr (Français)"`). Synchronizes both
   * [selectedLanguageCode] and [selectedLanguageLocale].
   */
  fun updateSelectedLanguage(languageCodeOrLocale: String) =
    settings.updateLanguage(languageCodeOrLocale)

  /** Updates the active in-app language locale (delegates to [updateSelectedLanguage]). */
  fun updateLanguageLocale(locale: String) = updateSelectedLanguage(locale)

  /** Updates the "Upload photos over Wi-Fi only" preference. */
  fun updateUploadMediaOverUnmeteredConnectionOnly(enabled: Boolean) =
    settings.updateUploadMediaOverUnmeteredConnectionOnly(enabled)

  /** Records a click on "Visit website" (`https://groundplatform.org/`) in Settings > Help. */
  fun visitGroundWebsite(url: String = GROUND_WEBSITE_URL) = settings.visitWebsite(url)

  /** Evicts uploaded media attachments from local device cache (per `00-index.md`). */
  fun evictUploadedMediaCache() = settings.evictUploadedMediaCache()

  /**
   * Selects the device preview form factor (`Mobile` vs `Tablet`) in the prototype wrapper page.
   */
  fun selectDeviceFormFactor(formFactor: DeviceFormFactor) {
    deviceFormFactor = formFactor
    deviceOrientation = formFactor.defaultOrientation
  }

  /** Toggles between `Mobile` and `Tablet` form factors in the prototype wrapper page. */
  fun toggleDeviceFormFactor() {
    val nextFormFactor =
      if (deviceFormFactor == DeviceFormFactor.MOBILE) {
        DeviceFormFactor.TABLET
      } else {
        DeviceFormFactor.MOBILE
      }
    selectDeviceFormFactor(nextFormFactor)
  }

  /** Rotates the simulated device between `Portrait` and `Landscape` orientation (`90°` swap). */
  fun rotateDevice() {
    deviceOrientation =
      if (deviceOrientation == DeviceOrientation.PORTRAIT) {
        DeviceOrientation.LANDSCAPE
      } else {
        DeviceOrientation.PORTRAIT
      }
  }

  /** Alias for [rotateDevice]: toggles between `Portrait` and `Landscape` device orientation. */
  fun toggleDeviceOrientation() {
    rotateDevice()
  }

  /** Explicitly sets the simulated device orientation (`Portrait` or `Landscape`). */
  fun selectDeviceOrientation(orientation: DeviceOrientation) {
    deviceOrientation = orientation
  }

  /**
   * Pans (drags) the survey map viewport by normalized deltas `(deltaNormalizedX,
   * deltaNormalizedY)`.
   *
   * Dragging the map disengages automatic GPS camera centering ([isCameraFollowingUser] = `false`,
   * [locationLockState] = [LocationLockState.PANNED]), causing the Google Maps-style `"Recenter"`
   * button to appear on the map.
   */
  fun panMap(deltaNormalizedX: Float, deltaNormalizedY: Float) =
    surveyMap.panMap(deltaNormalizedX, deltaNormalizedY)

  /**
   * Recenters the map camera on the user's current GPS location (`(0.50f, 0.50f)` screen center)
   * and re-enables automatic GPS camera following ([isCameraFollowingUser] = `true`,
   * [locationLockState] = [LocationLockState.LOCKED]).
   */
  fun recenterMapOnUser() = surveyMap.recenterMapOnUser()

  /**
   * Recenters the map camera on [entity], adjusting the normalized vertical screen center to
   * [targetScreenY] (defaults to `0.50f` screen center) to account for reduced visible viewport
   * area (such as an expanded bottom table in the web dashboard).
   */
  fun recenterMapOnEntity(entity: GeospatialEntityItem, targetScreenY: Float = 0.50f) =
    surveyMap.recenterMapOnEntity(entity, targetScreenY)

  /**
   * Recenters the map camera on the entity with [entityId], adjusting the normalized vertical
   * screen center to [targetScreenY] (defaults to `0.50f` screen center).
   */
  fun recenterMapOnEntity(entityId: String, targetScreenY: Float = 0.50f) =
    surveyMap.recenterMapOnEntity(entityId, targetScreenY)

  /** Resolves the geographic coordinates `(lng, lat)` for [entity] in the active survey. */
  fun resolveEntityLngLat(entity: GeospatialEntityItem): Pair<Double, Double> =
    surveyMap.resolveEntityLngLat(entity)

  /**
   * Resolves the geographic bounds of [entity]'s geometry in the active survey (see
   * [EntityGeometry]); points have zero-size bounds.
   */
  internal fun resolveEntityLngLatBounds(entity: GeospatialEntityItem): LngLatBounds =
    EntityGeometry.bounds(entity, activeSurveyAnchor)

  /**
   * The camera the survey map should show: the user's GPS position shifted by the pan offset
   * ([mapPanOffsetX], [mapPanOffsetY]), at the survey's zoom plus [mapZoomDelta].
   */
  fun desiredMapCamera(): CameraPosition = surveyMap.desiredMapCamera()

  /**
   * Updates the pan offset and zoom delta to match where the map [camera] settled after a gesture
   * or an explicit camera move. Moving the center stops following the user's GPS location.
   */
  fun syncMapCamera(camera: CameraPosition) = surveyMap.syncMapCamera(camera)

  /**
   * Syncs [mapZoomDelta] after the map camera changed its own zoom (e.g. fitting a selected map
   * feature), so clustering and the scale bar follow the camera.
   */
  fun syncMapZoomDelta(zoomDelta: Float) = surveyMap.syncMapZoomDelta(zoomDelta)

  /** Adjusts the Mapbox zoom level by [deltaZoom] (clamped to `[-5.0f, +3.7f]`). */
  fun zoomMapBy(deltaZoom: Float) = surveyMap.zoomMapBy(deltaZoom)

  /** Zooms the Mapbox map in by one step (`+0.75` zoom levels). */
  fun zoomInMap() = surveyMap.zoomInMap()

  /** Zooms the Mapbox map out by one step (`-0.75` zoom levels). */
  fun zoomOutMap() = surveyMap.zoomOutMap()

  /** Resets the Mapbox zoom level to the active survey's default (`15.3z`). */
  fun resetMapZoom() = surveyMap.resetMapZoom()

  /**
   * Formats the count of map features in a cluster using the active domain noun (e.g. `"5 map
   * features"`, `"1 map feature"`, or `"5 coffee parcels"`).
   */
  fun formatClusterSitesCountLabel(siteCount: Int): String =
    mapState.formatClusterSitesCountLabel(siteCount)

  /**
   * Selects a zoomed-out [MapFeatureCluster] balloon by [clusterId] (or clears selection when
   * `null`). If the same cluster is tapped a second time while already selected, zooms in toward
   * that cluster.
   */
  fun selectCluster(clusterId: String?) = surveyMap.selectCluster(clusterId)

  /**
   * Centers the map viewport on the specified [clusterId] and zooms in one step (`+0.75z`) to
   * expand the cluster.
   */
  fun zoomIntoCluster(clusterId: String) = surveyMap.zoomIntoCluster(clusterId)

  /**
   * Updates the user's current GPS location (`userGpsNormalizedX`, `userGpsNormalizedY`).
   * - When [isCameraFollowingUser] is `true` (default), [mapPanOffsetX] and [mapPanOffsetY] stay
   *   `0f`, so the map automatically pans ([mapWorldToScreenShiftX], [mapWorldToScreenShiftY]) to
   *   keep the user's GPS location at the exact center `(0.50f, 0.50f)` of the screen.
   * - When [isCameraFollowingUser] is `false` (after the map has been manually dragged/panned), the
   *   panned camera viewport remains stationary while the user's GPS blue dot moves across the map.
   */
  fun updateUserGpsLocation(
    newNormalizedX: Float,
    newNormalizedY: Float,
    coordinatesLabel: String? = null,
  ) = surveyMap.updateUserGpsLocation(newNormalizedX, newNormalizedY, coordinatesLabel)

  // --- Straight-Line Wayfinding Navigation Actions (see [SurveyMapViewModel]) ---

  /**
   * Starts straight-line navigation from the collector's current GPS position to [entityId],
   * ensuring its layer is visible, selecting the entity in collapsed bottom-sheet peek mode, and
   * switching to the Map view with GPS auto-centering enabled.
   */
  fun startNavigationToEntity(entityId: String) = surveyMap.startNavigationToEntity(entityId)

  /**
   * Starts straight-line navigation from the collector's current GPS position to [submissionId]
   * (targeting its recorded geometry polygon or parent entity location), ensuring the parent
   * entity's map layer is visible, selecting the submission, and switching to Map view.
   */
  fun startNavigationToSubmission(submissionId: String) =
    surveyMap.startNavigationToSubmission(submissionId)

  /** Toggles straight-line navigation to [entityId] on or off. */
  fun toggleNavigationToEntity(entityId: String) = surveyMap.toggleNavigationToEntity(entityId)

  /** Toggles straight-line navigation to [submissionId] on or off. */
  fun toggleNavigationToSubmission(submissionId: String) =
    surveyMap.toggleNavigationToSubmission(submissionId)

  /**
   * Starts straight-line navigation from the collector's current GPS position to [placeId] and
   * switches to the Map view with GPS auto-centering enabled.
   */
  fun startNavigationToPlace(placeId: String) {
    val place = findPlaceById(placeId) ?: return
    surveyMap.startNavigationToPlace(place)
  }

  /** Toggles straight-line navigation to [placeId] on or off. */
  fun toggleNavigationToPlace(placeId: String) {
    if (isNavigatingToPlace(placeId)) {
      stopNavigation()
    } else {
      startNavigationToPlace(placeId)
    }
  }

  /** Stops active straight-line navigation and clears the navigation line & HUD banner. */
  fun stopNavigation() = surveyMap.stopNavigation()

  /**
   * Advances the collector's simulated GPS blue dot along the active straight-line navigation
   * vector toward the target entity or submission by [stepFraction] (`0.40f` by default, snapping
   * directly onto the destination when within `12` meters).
   */
  fun stepUserTowardNavigationTarget(stepFraction: Float = 0.40f) =
    surveyMap.stepUserTowardNavigationTarget(stepFraction)

  /** Toggles between Light and Dark Ground Material 3 themes. */
  fun toggleDarkTheme() = settings.toggleDarkTheme()

  /**
   * Randomly generates and appends [count] polygon map features (`GeospatialEntityItem`) across the
   * active survey region to benchmark Mapbox GL WebGL polygon & marker scaling at 5,000, 10,000,
   * 15,000+ map features.
   */
  fun addRandomSites(count: Int = 5_000) {
    if (count <= 0) return
    syncViewModelState()
    viewModel.launch { viewModel.generateRandomSitesUseCase(count) }
    val totalCount = entities.size
    currentScreen = PrototypeScreen.MAIN_SURVEY
    mainViewMode = MainSurveyViewMode.MAP
    activeDrawerSubView = MainDrawerSubView.NONE
    activeSurveyNotice = "Added $count random polygon features ($totalCount total map features)"
  }

  /** Resets the onboarding and prototype state back to the initial Sign In screen. */
  fun resetPrototypeFlow() {
    currentScreen = PrototypeScreen.SIGN_IN
    onboarding.reset()
    settings.reset()
    surveyMap.reset()
    dashboard.reset()
    activeSurveyNotice = null
    mainViewMode = MainSurveyViewMode.MAP
    isDrawerOpen = false
    activeDrawerSubView = MainDrawerSubView.NONE
    isAvailableFormsSheetOpen = false
    viewModel.launch { viewModel.sampleDataRepository.resetToSampleData() }
    dataResetCount++
    mapboxPlacesApiResults = emptyList()
    isMapboxPlacesSearching = false
    selectedSubmissionId = null
    closeActiveFormRunner()
    resetDefaultXFormsXml()
  }

  companion object {
    /** Default width of the web dashboard's left-hand panel, in dp. */
    const val DEFAULT_SIDE_PANEL_WIDTH_DP = DashboardUiState.DEFAULT_SIDE_PANEL_WIDTH_DP

    /** Narrowest the web dashboard's left-hand panel can be dragged, in dp. */
    const val MIN_SIDE_PANEL_WIDTH_DP = DashboardUiState.MIN_SIDE_PANEL_WIDTH_DP

    /** Widest the web dashboard's left-hand panel can be dragged, in dp. */
    const val MAX_SIDE_PANEL_WIDTH_DP = DashboardUiState.MAX_SIDE_PANEL_WIDTH_DP
  }

  /**
   * ID of the stored survey whose primary XForms definition is [example]'s, or `null` if no stored
   * survey uses it.
   */
  fun surveyIdForExampleForm(example: WorkbenchExampleForm): String? =
    data.surveys.firstOrNull { data.surveyConfigs[it.id]?.primaryFormXml == example.xformsXml }?.id

  /** Incremented when the sample data is reset, so views holding local copies reload them. */
  var dataResetCount by mutableStateOf(0)
    private set

  /** The Survey editor's draft of the active survey, from the local data store. */
  val activeSurveyEditorDraft: SurveyEditorDraft
    get() =
      SurveyEditorDraft.forSurvey(
        surveyId = activeSurveyId,
        stored = data.content.editorDraft,
        survey = surveys.firstOrNull { it.id == activeSurveyId },
      )

  /** Saves the Survey editor's [draft] of [surveyId] to the local data store. */
  fun saveSurveyEditorDraft(surveyId: String, draft: SurveyEditorDraft) {
    viewModel.launch { viewModel.surveyEditorRepository.saveDraft(surveyId, draft) }
  }

  /** Number of map features stored for [surveyId]. */
  fun entityCountForSurvey(surveyId: String): Int = data.surveyStats[surveyId]?.entityCount ?: 0

  /** Number of submissions stored for [surveyId]. */
  fun submissionCountForSurvey(surveyId: String): Int =
    data.surveyStats[surveyId]?.submissionCount ?: 0
}
