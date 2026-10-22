/*
 * Copyright 2026 The Collect One Authors.
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
package org.openforis.collectone.v2.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.RecordInstance
import org.openforis.collectone.v2.app.di.AppDataHolder
import org.openforis.collectone.v2.app.domain.model.AppScreen
import org.openforis.collectone.v2.app.domain.model.BasemapType
import org.openforis.collectone.v2.app.domain.model.EntityDatasetFeaturesGroup
import org.openforis.collectone.v2.app.domain.model.EntityDetailsPane
import org.openforis.collectone.v2.app.domain.model.FormPreviewItem
import org.openforis.collectone.v2.app.domain.model.FormSubmissionsGroup
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.ImagerySource
import org.openforis.collectone.v2.app.domain.model.ListFilterTab
import org.openforis.collectone.v2.app.domain.model.LocationLockState
import org.openforis.collectone.v2.app.domain.model.MainSurveyViewMode
import org.openforis.collectone.v2.app.domain.model.MapFeatureCluster
import org.openforis.collectone.v2.app.domain.model.MapLayerItem
import org.openforis.collectone.v2.app.domain.model.MapScaleBarSpec
import org.openforis.collectone.v2.app.domain.model.MeasurementUnitSystem
import org.openforis.collectone.v2.app.domain.model.MutationLogItem
import org.openforis.collectone.v2.app.domain.model.MutationSyncState
import org.openforis.collectone.v2.app.domain.model.NavigationTargetKind
import org.openforis.collectone.v2.app.domain.model.OfflineBasemapStyle
import org.openforis.collectone.v2.app.domain.model.OfflineTilePackageItem
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.SharedPdfSheetState
import org.openforis.collectone.v2.app.domain.model.StraightLineNavigationState
import org.openforis.collectone.v2.app.domain.model.StraightLineVector
import org.openforis.collectone.v2.app.domain.model.SubmissionFieldEntry
import org.openforis.collectone.v2.app.domain.model.SubmissionGeometryPolygon
import org.openforis.collectone.v2.app.domain.model.SubmissionPreviewItem
import org.openforis.collectone.v2.app.domain.model.SurveyMapAnchor
import org.openforis.collectone.v2.app.domain.model.SurveyPlaceItem
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.SyncStatus
import org.openforis.collectone.v2.app.domain.model.UploadStatusFilter
import org.openforis.collectone.v2.app.domain.model.UserSettings
import org.openforis.collectone.v2.app.domain.model.editor.SurveyAccess
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.app.domain.model.formatClusterSitesCountLabel
import org.openforis.collectone.v2.app.domain.model.relatedEntityForPropertyValue
import org.openforis.collectone.v2.app.ui.map.DraftGeometry
import org.openforis.collectone.v2.app.ui.map.EntityGeometry
import org.openforis.collectone.v2.app.ui.map.FormGeometryOverlay
import org.openforis.collectone.v2.app.ui.state.DashboardEvent
import org.openforis.collectone.v2.app.ui.state.DashboardUiState
import org.openforis.collectone.v2.app.ui.state.DataCollectionEvent
import org.openforis.collectone.v2.app.ui.state.DataCollectionUiState
import org.openforis.collectone.v2.app.ui.state.FormFocusRequest
import org.openforis.collectone.v2.app.ui.state.MapFramingRequest
import org.openforis.collectone.v2.app.ui.state.OnboardingEvent
import org.openforis.collectone.v2.app.ui.state.SettingsEvent
import org.openforis.collectone.v2.app.ui.state.SurveyMapEvent
import org.openforis.collectone.v2.app.ui.state.SurveyMapUiState
import org.openforis.collectone.v2.app.ui.viewmodel.DashboardViewModel
import org.openforis.collectone.v2.app.ui.viewmodel.DataCollectionViewModel
import org.openforis.collectone.v2.app.ui.viewmodel.OnboardingViewModel
import org.openforis.collectone.v2.app.ui.viewmodel.SettingsViewModel
import org.openforis.collectone.v2.app.ui.viewmodel.SurveyMapViewModel
import org.openforis.collectone.v2.core.forms.model.EntityState
import org.openforis.collectone.v2.core.forms.ui.FormWizardController
import org.openforis.collectone.v2.map.CameraPosition
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.map.LngLatBounds

/**
 * Shared state holder of one Ground client session (the mobile app or the web app).
 *
 * Wires the shared [AppDataHolder] (owning `LocalStore`, domain repositories, and use cases) to the
 * feature ViewModels both clients use (`OnboardingViewModel`, `SettingsViewModel`,
 * `SurveyMapViewModel`, `DashboardViewModel`, `DataCollectionViewModel`), mirrors their UI state
 * for the shared views, and routes cross-feature events between them. Client-specific reactions to
 * those events (screens, drawers, pages) go through the `protected open` hooks that
 * `MobileAppState` and `WebAppState` override. State and actions only one client uses live in that
 * client's subclass, which reads the mirrored ViewModel state through the `protected` properties
 * below.
 *
 * Several sessions may share one [AppDataHolder], in which case data changes made in one are live
 * in the other, while UI state such as selection and navigation stays per session.
 */
open class AppSession(
  val dataHolder: AppDataHolder = AppDataHolder(),
  /**
   * ViewModel of user settings, the session theme, and offline tile packages. Its screens observe
   * [SettingsViewModel.uiState] directly; this class mirrors it for the rest of the UI. Sessions
   * sharing one data holder may share it so they share one theme.
   */
  val settings: SettingsViewModel = createSettingsViewModel(dataHolder),
) {
  /**
   * Latest snapshot of everything read from the local data store (the single source of truth for
   * surveys, survey content, mutations, places, and settings). Kept current by collecting
   * [AppDataHolder.appData]; all data properties below are read-only views over it, and all data
   * changes are written through the repositories.
   */
  protected var data by mutableStateOf(dataHolder.appData.value)
    private set

  /**
   * ViewModel of the onboarding flow (Sign In → Terms of Service → Download survey). Its screens
   * observe [OnboardingViewModel.uiState] directly; this class mirrors it for the rest of the UI
   * and applies its navigation [OnboardingEvent]s to the app shell.
   */
  val onboarding: OnboardingViewModel =
    OnboardingViewModel(
      authRepository = dataHolder.authRepository,
      surveyRepository = dataHolder.surveyRepository,
      organizationRepository = dataHolder.organizationRepository,
      scope = dataHolder.scope,
      connectivityRepository = dataHolder.connectivityRepository,
      hasCompletedInitialSync = dataHolder.hasCompletedInitialSync,
      surveyDirectoryRepository = dataHolder.surveyDirectoryRepository,
    )

  protected var onboardingState by mutableStateOf(onboarding.uiState.value)
    private set

  protected var settingsState by mutableStateOf(settings.uiState.value)
    private set

  /**
   * ViewModel of the survey map viewport (camera, GPS, basemap and imagery, clustering, wayfinding,
   * and the selected map feature, cluster, or place). The map views observe
   * [SurveyMapViewModel.uiState] directly; this class mirrors it for the rest of the UI and applies
   * its [SurveyMapEvent]s to the app shell.
   */
  val surveyMap: SurveyMapViewModel =
    SurveyMapViewModel(
      surveyRepository = dataHolder.surveyRepository,
      organizationRepository = dataHolder.organizationRepository,
      settingsRepository = dataHolder.settingsRepository,
      locationRepository = dataHolder.locationRepository,
      placeRepository = dataHolder.placeRepository,
      scope = dataHolder.scope,
      connectivityRepository = dataHolder.connectivityRepository,
      clusterMapFeatures = dataHolder.clusterMapFeaturesUseCase,
      computeWayfindingNavigation = dataHolder.computeWayfindingNavigationUseCase,
    )

  protected var mapState by mutableStateOf(surveyMap.uiState.value)
    private set

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
      surveyRepository = dataHolder.surveyRepository,
      organizationRepository = dataHolder.organizationRepository,
      authRepository = dataHolder.authRepository,
      mutationRepository = dataHolder.mutationRepository,
      createSurveyUseCase = dataHolder.createSurveyUseCase,
      syncMutationsUseCase = dataHolder.syncMutationsUseCase,
      scope = dataHolder.scope,
      libraryRepository = dataHolder.libraryRepository,
      resolveLibraryUseCase = dataHolder.resolveLibraryUseCase,
      impactEventRepository = dataHolder.impactEventRepository,
      surveyEditorRepository = dataHolder.surveyEditorRepository,
      exportSurveyData = dataHolder.exportSurveyDataUseCase,
    )

  protected var dashboardState by mutableStateOf(dashboard.uiState.value)
    private set

  /**
   * ViewModel of data collection: the selected record (map feature and / or submission), the open
   * form and its `entityref` step, the web dashboard's draw-on-map session, and the GeoID QR code
   * and PDF export dialogs. It is the one owner of record selection; the map viewport mirrors it.
   * Its screens observe [DataCollectionViewModel.uiState] directly; this class mirrors it for the
   * rest of the UI and applies its [DataCollectionEvent]s to the app shell.
   */
  val dataCollection: DataCollectionViewModel =
    DataCollectionViewModel(
      surveyRepository = dataHolder.surveyRepository,
      settingsRepository = dataHolder.settingsRepository,
      authRepository = dataHolder.authRepository,
      locationRepository = dataHolder.locationRepository,
      completeFormSubmission = dataHolder.completeFormSubmissionUseCase,
      launchForm = dataHolder.launchFormUseCase,
      connectivityRepository = dataHolder.connectivityRepository,
      scope = dataHolder.scope,
      recordImpactEvent = dataHolder.recordImpactEventUseCase,
    )

  protected var dataCollectionState by mutableStateOf(dataCollection.uiState.value)
    private set

  /** Latest [DataCollectionUiState], for views that build data collection from the app shell. */
  val dataCollectionUiState: DataCollectionUiState
    get() = dataCollectionState

  init {
    dataHolder.scope.launch { dataHolder.appData.collect { data = it } }
    dataHolder.scope.launch {
      onboarding.uiState.collect {
        val wasSignedIn = onboardingState.isSignedIn
        onboardingState = it
        if (wasSignedIn && !it.isSignedIn) onSignedOutElsewhere()
      }
    }
    dataHolder.scope.launch { onboarding.events.collect(::onOnboardingEvent) }
    dataHolder.scope.launch { settings.uiState.collect { settingsState = it } }
    dataHolder.scope.launch {
      settings.events.collect { event ->
        when (event) {
          is SettingsEvent.Notice -> activeSurveyNotice = event.message
        }
      }
    }
    dataHolder.scope.launch { surveyMap.uiState.collect { mapState = it } }
    dataHolder.scope.launch { surveyMap.events.collect(::onSurveyMapEvent) }
    dataHolder.scope.launch { dashboard.uiState.collect { dashboardState = it } }
    dataHolder.scope.launch { dashboard.events.collect(::onDashboardEvent) }
    dataHolder.scope.launch { dataCollection.uiState.collect { dataCollectionState = it } }
    dataHolder.scope.launch { dataCollection.events.collect(::onDataCollectionEvent) }
  }

  /**
   * Applies a data collection outcome to the rest of the session (map highlight and framing, bottom
   * sheet, dashboard panes, notices).
   */
  private fun onDataCollectionEvent(event: DataCollectionEvent) {
    when (event) {
      is DataCollectionEvent.EntityHighlighted ->
        surveyMap.setSelectedEntity(event.entityId, bumpSelectionEpoch = event.frameAgain)
      is DataCollectionEvent.MapRecenteredOnEntity -> surveyMap.recenterMapOnEntity(event.entity)
      is DataCollectionEvent.EntityBottomSheetExpanded ->
        surveyMap.updateEntityBottomSheetExpanded(event.expanded)
      DataCollectionEvent.LayersSheetClosed -> surveyMap.updateLayersSheetOpen(false)
      DataCollectionEvent.PlaceSelectionCleared -> surveyMap.clearSelectedPlace()
      DataCollectionEvent.FormsSheetOpened -> onFormsSheetOpened()
      is DataCollectionEvent.SubmissionOpened -> dashboard.onSubmissionOpened(event.tableDatasetId)
      DataCollectionEvent.DetailsReturnedToProperties -> dashboard.showEntityProperties()
      DataCollectionEvent.ListShown -> onListShown()
      DataCollectionEvent.FormOpened -> onFormOpened()
      is DataCollectionEvent.Notice -> activeSurveyNotice = event.message
    }
  }

  /** Applies a dashboard outcome to the rest of the session (survey switch, Uploads, notices). */
  private fun onDashboardEvent(event: DashboardEvent) {
    when (event) {
      is DashboardEvent.SurveyActivated -> clearSelectionsForActivatedSurvey(event.surveyId)
      DashboardEvent.UploadsOpened -> onUploadsOpened()
      is DashboardEvent.Notice -> activeSurveyNotice = event.message
    }
  }

  /** Applies a map outcome to the rest of the session (record selection, notices). */
  private fun onSurveyMapEvent(event: SurveyMapEvent) {
    when (event) {
      is SurveyMapEvent.EntitySelected -> {
        dataCollection.onEntitySelected(event.entityId)
        onMapShown()
        dashboard.onEntitySelected(event.entityId)
      }
      is SurveyMapEvent.PlaceSelected -> {
        dataCollection.onEntitySelected(null)
        dashboard.selectLayer(null)
        onMapShown()
        activeSurveyNotice = "Centered map on ${event.place.name} (${event.place.coordinatesLabel})"
      }
      is SurveyMapEvent.ClusterSelected -> {
        dataCollection.onEntitySelected(null)
        activeSurveyNotice =
          surveyMap.uiState.value.formatClusterSitesCountLabel(event.cluster.siteCount)
      }
      SurveyMapEvent.SelectionCleared -> dataCollection.onEntitySelected(null)
      is SurveyMapEvent.NavigationStarted -> {
        dataCollection.onNavigationStarted(
          entityId = surveyMap.uiState.value.selectedEntityId,
          submissionId = event.submissionId,
        )
        onNavigationStarted()
        activeSurveyNotice = event.notice
      }
      SurveyMapEvent.NavigationStopped -> activeSurveyNotice = null
      is SurveyMapEvent.Notice -> activeSurveyNotice = event.message
    }
  }

  /** Applies an onboarding outcome to the session (active survey, notices, client hooks). */
  private fun onOnboardingEvent(event: OnboardingEvent) {
    when (event) {
      is OnboardingEvent.ShowScreen -> {
        onScreenRequested(event.screen)
        if (event.screen == AppScreen.SIGN_IN) {
          activeSurveyNotice = null
        }
      }
      is OnboardingEvent.SurveyOpened -> {
        activateSurvey(event.surveyId)
        onSurveyOpened()
        activeSurveyNotice = event.notice
      }
      OnboardingEvent.ReturnToSurveyList -> {
        activeSurveyNotice = null
        onReturnToSurveyList()
      }
      OnboardingEvent.SignedOut -> {
        onSignedOut()
        activeSurveyNotice = null
      }
      is OnboardingEvent.Notice -> activeSurveyNotice = event.message
    }
  }

  // --- Client hooks: overridden by the mobile and web sessions to update their own shells ---

  /** The onboarding flow asks to show [screen] (Sign In, Terms of Service, Download survey). */
  protected open fun onScreenRequested(screen: AppScreen) {}

  /** A survey was opened from the onboarding flow or the survey list and is now active. */
  protected open fun onSurveyOpened() {}

  /** The onboarding flow asks to return to the list of downloaded surveys. */
  protected open fun onReturnToSurveyList() {}

  /** The user signed out through this session's onboarding flow. */
  protected open fun onSignedOut() {}

  /**
   * The signed-in user was signed out without this session's involvement (for example from another
   * session sharing the same [AppDataHolder]).
   */
  protected open fun onSignedOutElsewhere() {}

  /** A map feature or place was selected, so the map should be visible. */
  protected open fun onMapShown() {}

  /** Straight-line navigation started, so the survey map should be visible. */
  protected open fun onNavigationStarted() {}

  /** The `Uploads` screen was opened. */
  protected open fun onUploadsOpened() {}

  /** The Available Forms sheet was opened. */
  protected open fun onFormsSheetOpened() {}

  /** Data collection asks to show the searchable list. */
  protected open fun onListShown() {}

  /** A data collection form was opened. */
  protected open fun onFormOpened() {}

  /** This session made the survey with [surveyId] active. */
  protected open fun onSurveyActivated(surveyId: String) {}

  /** Writes the active survey's [entities] to the local data store. */
  private fun storeEntities(entities: List<GeospatialEntityItem>) {
    dataHolder.launch { dataHolder.surveyRepository.setEntities(entities) }
  }

  val isDarkTheme: Boolean
    get() = settingsState.isDarkTheme

  val isSignedIn: Boolean
    get() = onboardingState.isSignedIn

  val signedInUserName: String
    get() = onboardingState.profile.displayName

  /** Two-letter initials of the signed-in user for avatar badges (`"ML"` for `"Maya Lin"`). */
  val signedInUserInitials: String
    get() = onboardingState.profile.initials

  val signedInUserEmail: String
    get() = onboardingState.profile.email

  /** ID of a downloaded survey pending confirmation to remove from the device, or null. */
  val pendingRemovalSurveyId: String?
    get() = onboardingState.pendingRemovalSurveyId

  /** ID of an offline tile package pending confirmation to remove from the device, or null. */
  val pendingRemovalTilePackageId: String?
    get() = settingsState.pendingRemovalTilePackageId

  /** All organizations in the local data store, in display order. */
  val organizations: List<Organization>
    get() = data.organizations

  /** Organizations the signed-in user is an active member of. */
  val signedInUserOrganizations: List<Organization>
    get() = organizations.filter { it.isMember(signedInUserEmail) }

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
   * Whether [submission]'s contributor identity (name and email) should be hidden from the
   * currently signed-in viewer. Contributor identities are always visible only to survey organizers
   * and to the contributor themselves.
   */
  fun shouldHideContributorIdentity(submission: SubmissionPreviewItem): Boolean {
    val isOrganizer =
      SurveyAccess.canManage(
        email = signedInUserEmail,
        sharing = activeSurveyEditorDraft.sharing,
        organization = activeSurveyOrganization,
      )
    if (isOrganizer) return false
    val isOwnSubmission =
      signedInUserEmail.isNotBlank() &&
        submission.collectorEmail.equals(signedInUserEmail, ignoreCase = true)
    return !isOwnSubmission
  }

  /**
   * Display name for [submission]'s contributor. Contributor identities are visible only to survey
   * organizers and to the contributor themselves.
   */
  fun displayCollectorName(submission: SubmissionPreviewItem): String {
    if (shouldHideContributorIdentity(submission)) return "Participant (identity hidden)"
    val isOrganizer =
      SurveyAccess.canManage(
        email = signedInUserEmail,
        sharing = activeSurveyEditorDraft.sharing,
        organization = activeSurveyOrganization,
      )
    val isOwnSubmission =
      signedInUserEmail.isNotBlank() &&
        submission.collectorEmail.equals(signedInUserEmail, ignoreCase = true)
    return if (!isOrganizer && isOwnSubmission) {
      "${submission.collectorName} (You)"
    } else {
      submission.collectorName
    }
  }

  /**
   * Full attribution string (`Name (email)`) for [submission]'s contributor in the detail view.
   * Contributor identities are visible only to survey organizers and to the contributor themselves.
   */
  fun displayCollectorAttribution(submission: SubmissionPreviewItem): String {
    if (shouldHideContributorIdentity(submission)) return "Participant (identity hidden)"
    val isOrganizer =
      SurveyAccess.canManage(
        email = signedInUserEmail,
        sharing = activeSurveyEditorDraft.sharing,
        organization = activeSurveyOrganization,
      )
    val isOwnSubmission =
      signedInUserEmail.isNotBlank() &&
        submission.collectorEmail.equals(signedInUserEmail, ignoreCase = true)
    return if (!isOrganizer && isOwnSubmission) {
      "${submission.collectorName} (You)"
    } else {
      "${submission.collectorName} (${submission.collectorEmail})"
    }
  }

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
    protected set

  // --- Main Survey UI State ---
  val activeSurveyId: String
    get() = data.activeSurveyId

  val isLayersSheetOpen: Boolean
    get() = mapState.isLayersSheetOpen

  val selectedBasemapType: BasemapType
    get() = mapState.selectedBasemapType

  val isOfflineBasemapVisible: Boolean
    get() = mapState.isOfflineBasemapVisible

  val offlineBasemapStyle: OfflineBasemapStyle
    get() = mapState.offlineBasemapStyle

  /** The survey's map layers (the Survey editor publishes layer styles straight to them). */
  val mapLayers: List<MapLayerItem>
    get() = data.content.mapLayers

  /** The map layer that owns [entity] (by `layerId`), whose style the entity is drawn with. */
  fun mapLayerFor(entity: GeospatialEntityItem): MapLayerItem? = mapLayers.firstOrNull {
    it.id == entity.layerId
  }

  val submissionGeometries: List<SubmissionGeometryPolygon>
    get() = data.content.submissionGeometries

  /** The survey's forms (the Survey editor publishes Form availability straight to them). */
  val forms: List<FormPreviewItem>
    get() = data.content.forms

  /** Map features in the active survey. Setting this writes them to the local data store. */
  var entities: List<GeospatialEntityItem>
    get() = data.content.entities
    set(value) = storeEntities(value)

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

  /**
   * ID of the selected map feature, owned by [DataCollectionViewModel] (the map viewport mirrors it
   * as its highlighted feature).
   */
  val selectedEntityId: String?
    get() = dataCollectionState.selectedEntityId

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

  /**
   * Whether the left-hand panel in the web dashboard is expanded (`true`, default) or collapsed
   * (`false`) to provide a full-width map view.
   */
  val isSidePanelExpanded: Boolean
    get() = dashboardState.isSidePanelExpanded

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

  /** ID of the submission open in the details surfaces, owned by [DataCollectionViewModel]. */
  val selectedSubmissionId: String?
    get() = dataCollectionState.selectedSubmissionId

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

  val unitSystem: MeasurementUnitSystem
    get() = data.userSettings.measurementUnits

  val selectedLanguageLocale: String
    get() = settingsState.selectedLanguageLocale

  val visitedWebsiteUrl: String?
    get() = settingsState.visitedWebsiteUrl

  val mediaCacheCleared: Boolean
    get() = settingsState.mediaCacheCleared

  /** Snapshot of user settings matching `UserSettings` in `github.com/google/ground-android`. */
  val userSettings: UserSettings
    get() = data.userSettings

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

  /**
   * Google Maps-style horizontal scale bar specification (`label`, `distanceMeters`, `barWidthDp`)
   * computed by `ClusterMapFeaturesUseCase.computeScaleBarSpec`.
   */
  val mapScaleBarSpec: MapScaleBarSpec
    get() = mapState.mapScaleBarSpec

  /** Number of GNSS (GPS/Galileo/GLONASS) satellites currently locked by the device receiver. */
  val gnssSatelliteCount: Int
    get() = mapState.gnssSatelliteCount

  /** Current horizontal GNSS accuracy in meters (`±2.1 m`). */
  val gnssAccuracyMeters: Double
    get() = mapState.gnssAccuracyMeters

  /**
   * Entity ID whose scannable GeoID QR code modal dialog is currently open (`null` when closed).
   */
  val activeQrCodeEntityId: String?
    get() = dataCollectionState.activeQrCodeEntityId

  /** The [GeospatialEntityItem] whose QR code modal dialog is currently open (if any). */
  val activeQrCodeEntity: GeospatialEntityItem?
    get() = dataCollectionState.activeQrCodeEntity

  /**
   * Active PDF export & app-sharing modal state for an entity or submission (`null` when closed).
   */
  val activeSharedPdfSheet: SharedPdfSheetState?
    get() = dataCollectionState.activeSharedPdfSheet

  /** Short confirmation or error after a PDF action (e.g. `"Saved …pdf"`), or `null`. */
  val pdfExportMessage: String?
    get() = dataCollectionState.pdfExportMessage

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

  /**
   * Resolves the normalized map coordinates `(normalizedX, normalizedY)` and optional
   * [SubmissionGeometryPolygon] for any [SubmissionPreviewItem].
   */
  fun resolveSubmissionTargetGeometry(
    submissionId: String
  ): Triple<Float, Float, SubmissionGeometryPolygon?>? =
    surveyMap.resolveSubmissionTargetGeometry(submissionId)

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

  /** True when straight-line navigation is currently active and targeting [placeId]. */
  fun isNavigatingToPlace(placeId: String): Boolean = mapState.isNavigatingToPlace(placeId)

  /** Currently active straight-line navigation session ([StraightLineNavigationState]). */
  val activeNavigation: StraightLineNavigationState?
    get() = mapState.activeNavigation

  /**
   * Parsed [FormDef] from [customXFormsXml] (`null` when [customXFormsXml] is blank or invalid;
   * when blank, built-in fallback XForms [FormDef]s per form ID are used automatically). Held by
   * [DataCollectionViewModel], which re-launches the open form when it changes.
   */
  val customFormDef: FormDef?
    get() = dataCollectionState.customFormDef

  // --- Data collection (see [DataCollectionViewModel]) ---

  /**
   * Active [FormWizardController] driving the embedded
   * [org.openforis.collectone.v2.core.forms.ui.MobileFormRunner] when data collection is triggered
   * for a Geospatial Entity (`null` when closed).
   */
  val activeFormWizardController: FormWizardController?
    get() = dataCollectionState.activeFormWizardController

  /**
   * Bounds the web dashboard's main map should frame next (a question card's **Zoom to fit**), or
   * `null`. Each request carries a fresh token, so framing the same geometry twice re-fires.
   */
  val webMapFramingRequest: MapFramingRequest?
    get() = dataCollectionState.webMapFramingRequest

  /**
   * Adds a main-map click at [latLng] to the geometry question being drawn (no-op when nothing is
   * being drawn). A `geopoint` is placed by its first click, which also stops the drawing.
   */
  fun addWebMapDrawingVertex(latLng: LatLng) = dataCollection.addWebMapDrawingVertex(latLng)

  /** The geometry being drawn on the main map, for its overlay, or `null` when not drawing. */
  val webMapDraftGeometry: DraftGeometry?
    get() = dataCollectionState.webMapDraftGeometry

  /**
   * Every geometry answer held by the open form (web dashboard), for the main map's in-flow
   * overlay. Empty when no form is open.
   */
  val webFormGeometries: List<FormGeometryOverlay>
    get() = dataCollectionState.webFormGeometries

  /**
   * The question the web form panel should scroll to and highlight, after its geometry was clicked
   * on the main map ([focusWebFormQuestion]).
   */
  val webFormFocusRequest: FormFocusRequest?
    get() = dataCollectionState.webFormFocusRequest

  /** Asks the web form panel to bring the question at [path] into view and highlight it. */
  fun focusWebFormQuestion(path: String) = dataCollection.focusWebFormQuestion(path)

  /**
   * Target Geospatial Entity ID for the currently active data collection form (`null` when closed).
   */
  val activeDataCollectionEntityId: String?
    get() = dataCollectionState.activeDataCollectionEntityId

  /** Target Form ID for the currently active data collection form (`null` when closed). */
  val activeDataCollectionFormId: String?
    get() = dataCollectionState.activeDataCollectionFormId

  /**
   * True when the embedded [org.openforis.collectone.v2.core.forms.ui.MobileFormRunner] is open.
   */
  val isDataCollectionFormOpen: Boolean
    get() = dataCollectionState.isDataCollectionFormOpen

  /**
   * The target [GeospatialEntityItem] for the currently active data collection session (if any).
   */
  val activeDataCollectionEntity: GeospatialEntityItem?
    get() = dataCollectionState.activeDataCollectionEntity

  /** The target [FormPreviewItem] for the currently active data collection session (if any). */
  val activeDataCollectionForm: FormPreviewItem?
    get() = dataCollectionState.activeDataCollectionForm

  /**
   * True when the Available Forms modal bottom sheet (triggered by the bottom-centered floating
   * action button on the Main Survey screen) is currently open.
   */
  val isAvailableFormsSheetOpen: Boolean
    get() = dataCollectionState.isAvailableFormsSheetOpen

  /**
   * True when the active data collection form was launched from the bottom-centered FAB without
   * pre-selecting a geospatial entity on the map (`activeDataCollectionEntityId` starts `null`, and
   * the `entityref` step presents the Map or List selector).
   */
  val wasFormLaunchedWithoutEntity: Boolean
    get() = dataCollectionState.wasFormLaunchedWithoutEntity

  /**
   * Active view mode (`MainSurveyViewMode.MAP` vs `MainSurveyViewMode.LIST`) for the in-form
   * `entityref` step selector when a form is launched without a pre-selected geospatial entity.
   */
  val entityRefSelectorViewMode: MainSurveyViewMode
    get() = dataCollectionState.entityRefSelectorViewMode

  /** Search query used to filter candidate entities in the `entityref` step's `List` mode. */
  val entityRefSearchQuery: String
    get() = dataCollectionState.entityRefSearchQuery

  /**
   * Incremented each time a map feature is picked at the `entityref` step, so the step's map frames
   * it again even when the same feature is picked twice.
   */
  val entityRefFramingEpoch: Long
    get() = dataCollectionState.entityRefFramingEpoch

  /**
   * Returns all [GeospatialEntityItem]s in the active survey belonging to [form]'s target dataset
   * (`form.targetDatasetId`).
   */
  fun allDatasetEntitiesForForm(form: FormPreviewItem): List<GeospatialEntityItem> =
    dataCollectionState.allDatasetEntitiesForForm(form)

  /**
   * Returns the eligible [GeospatialEntityItem]s in [form]'s target dataset that can accept a new
   * submission for [form] (`isFormButtonEnabled(entity, form) == true`).
   */
  fun eligibleEntitiesForForm(form: FormPreviewItem): List<GeospatialEntityItem> =
    dataCollectionState.eligibleEntitiesForForm(form)

  /**
   * Candidate entities for the active form's target dataset filtered by [entityRefSearchQuery] when
   * the collector is using the `List` view at the `entityref` step.
   */
  val filteredEntityRefCandidates: List<GeospatialEntityItem>
    get() = dataCollectionState.filteredEntityRefCandidates

  /**
   * True when the embedded form runner is open and the current step in [activeFormWizardController]
   * is an `entityref` step requiring the user to select a geospatial entity via Map or List.
   */
  val isCurrentFormStepEntityRef: Boolean
    get() = dataCollectionState.isCurrentFormStepEntityRef

  /** The currently active survey loaded in the Main Survey UI. */
  val activeSurvey: SurveyPreviewItem
    get() = surveys.firstOrNull { it.id == activeSurveyId } ?: surveys.first()

  /**
   * Layers backed by `LayerDef.entity_dataset_id` (rendered with solid outlines) when the survey
   * has geospatial entities. Returns `emptyList()` when there are no geospatial entities so the map
   * layers section of the `Layers` sheet is hidden.
   */
  val entityDatasetLayers: List<MapLayerItem>
    get() = dashboardState.entityDatasetLayers

  /** Set of currently visible map layer IDs based on the "Layers" sheet toggles. */
  val visibleLayerIds: Set<String>
    get() = mapLayers.filter { it.isVisible }.map { it.id }.toSet()

  /** Geospatial entities currently visible on the map according to active layer visibility. */
  val visibleMapEntities: List<GeospatialEntityItem>
    get() = mapState.visibleMapEntities

  /**
   * User-facing lowercase plural count noun for map counters, search hints, and empty states:
   * - When 1 entity dataset layer is visible, returns its lowercase plural domain label (e.g.
   *   `"coffee parcels"`, `"monitoring plots"`, `"washing stations"`).
   * - Otherwise falls back to `"map features"`.
   */
  val activeEntitiesCountNoun: String
    get() = mapState.activeEntitiesCountNoun

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
    get() = dataCollectionState.selectedEntity

  /** All submissions (both entity-attached and standalone) in the active survey. */
  val allSubmissions: List<SubmissionPreviewItem>
    get() = dataCollectionState.allSubmissions

  /** The currently selected individual submission for full submission detail inspection. */
  val selectedSubmission: SubmissionPreviewItem?
    get() = dataCollectionState.selectedSubmission

  /**
   * Returns the forms in the active survey that request entities of [entity]'s dataset type and are
   * available on the asking platform: the web dashboard when [onWeb], otherwise the mobile app.
   */
  fun formsForEntity(
    entity: GeospatialEntityItem,
    onWeb: Boolean = false,
  ): List<FormPreviewItem> = dataCollectionState.formsForEntity(entity, onWeb)

  /**
   * Returns whether the organizer-defined action button for [form] is enabled on [entity]. Because
   * all forms are `1:N` with entities, any form targeting [entity]'s dataset is enabled.
   */
  fun isFormButtonEnabled(entity: GeospatialEntityItem, form: FormPreviewItem): Boolean =
    dataCollectionState.isFormButtonEnabled(entity, form)

  /**
   * Whether this session ignores the simulated device connectivity and is always online. The web
   * app is always online; airplane mode is only simulated for the mobile app.
   */
  protected open val isAlwaysOnline: Boolean
    get() = false

  /**
   * Whether the session currently has an active network connection.
   *
   * When `false` (offline mobile app), Mapbox Places API search is disabled (`filteredListPlaces`
   * returns `emptyList()`) and the bottom sheet shows an offline notice explaining that search is
   * restricted to local map features and that Places search is not available offline.
   */
  val isOnline: Boolean
    get() = isAlwaysOnline || mapState.isOnline

  /** Live place results returned by [PlacesGeocoder] (Mapbox Geocoding). */
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
   * Returns `emptyList()` when [listSearchQuery] is blank or when [!isOnline] because Places
   * results only appear when the user enters a search query while online.
   */
  val filteredListPlaces: List<SurveyPlaceItem>
    get() {
      val (surveyLng, surveyLat) = activeSurveyBaseLngLat()
      return dataHolder.searchPlacesUseCase(
        query = listSearchQuery,
        isOnline = isOnline,
        listFilterTab = listFilterTab,
        localPlaces = places,
        remoteApiPlaces = mapboxPlacesApiResults,
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
   * connection: while offline (`!isOnline`) only submissions stored on the device are listed.
   */
  fun availableGroupedSubmissionsForEntity(
    entity: GeospatialEntityItem
  ): List<FormSubmissionsGroup> {
    val groups = groupedSubmissionsForEntity(entity)
    if (isOnline) return groups
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

  /** Authenticates with Google and advances to the Terms of Service screen. */
  fun signInWithGoogle() = onboarding.signInWithGoogle(ignoreOffline = isAlwaysOnline)

  /** Makes [surveyId] the active survey and clears every selection scoped to the previous one. */
  private fun activateSurvey(surveyId: String) {
    dataHolder.launch { dataHolder.surveyRepository.setActiveSurveyId(surveyId) }
    dashboard.clearLayerSelection()
    clearSelectionsForActivatedSurvey(surveyId)
  }

  /**
   * Clears every selection scoped to the survey active before [surveyId] (map feature, submission,
   * open form) and notifies [onSurveyActivated].
   */
  private fun clearSelectionsForActivatedSurvey(surveyId: String) {
    dataCollection.onSurveyActivated()
    onSurveyActivated(surveyId)
  }

  /**
   * Switches between the collapsed Map peek state (`MAP`) and the expanded Searchable List state
   * (`LIST`) of the survey's persistent bottom sheet.
   */
  open fun setMainSurveyViewMode(mode: MainSurveyViewMode) {
    if (mode == MainSurveyViewMode.LIST) {
      dataCollection.clearSelection()
      surveyMap.updateEntityBottomSheetExpanded(true)
      surveyMap.updateLayersSheetOpen(false)
    } else {
      surveyMap.updateEntityBottomSheetExpanded(false)
    }
  }

  /** Toggles the "Layers" visibility popover sheet on the Map view. */
  fun updateLayersSheetOpen(open: Boolean) = surveyMap.updateLayersSheetOpen(open)

  /**
   * Selects between `Normal` (`BasemapType.NORMAL`) and `Satellite` (`BasemapType.SATELLITE`)
   * basemap.
   */
  fun selectBasemapType(type: BasemapType) = surveyMap.selectBasemapType(type)

  /** Toggles visibility of the downloaded Mapbox offline basemap in the `Layers` dialog. */
  fun toggleOfflineBasemapVisibility() = surveyMap.toggleOfflineBasemapVisibility()

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
  fun showEntityProperties() = dataCollection.showEntityProperties()

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
      dataCollection.clearSelection()
    }
  }

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
  fun returnToBottomSheetList() = dataCollection.returnToBottomSheetList()

  /** Explicitly expands or collapses the Entity Bottom Sheet. */
  fun updateEntityBottomSheetExpanded(expanded: Boolean) =
    surveyMap.updateEntityBottomSheetExpanded(expanded)

  /** Opens full details for a specific submission (from a 1:N entity bottom sheet or List view). */
  fun selectSubmissionDetail(submissionId: String?) =
    dataCollection.selectSubmissionDetail(submissionId)

  /**
   * Updates the search query in the Main Survey searchable bottom sheet and queries the Mapbox
   * Places API (`mapbox.places`) when online ([isOnline]).
   */
  fun updateListSearchQuery(query: String) {
    dashboard.updateListSearchQuery(query)
    val trimmed = query.trim()
    if (!isOnline || trimmed.isEmpty()) {
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

  protected fun triggerMapboxPlacesApiSearch(query: String) {
    if (!isOnline || query.isBlank()) {
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
        org.openforis.collectone.v2.app.domain.model.editor.LatLng(
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
    center: org.openforis.collectone.v2.app.domain.model.editor.LatLng,
    onResults: (List<SurveyPlaceItem>) -> Unit,
  ) =
    surveyMap.searchPlaces(
      surveyId = surveyId,
      query = query,
      regionSubtitle = regionSubtitle,
      center = center,
      ignoreOffline = isAlwaysOnline,
      onResults = onResults,
    )

  /** Shows geocoder [results] for [query] unless the query changed or the device is offline. */
  fun onPlacesSearchResults(query: String, results: List<SurveyPlaceItem>) {
    if (isOnline && listSearchQuery.trim() == query.trim()) {
      mapboxPlacesApiResults = results
    }
    isMapboxPlacesSearching = false
  }

  /**
   * Switches the `entityref` step picker between `MainSurveyViewMode.MAP` and
   * `MainSurveyViewMode.LIST`.
   */
  fun updateEntityRefSelectorViewMode(mode: MainSurveyViewMode) =
    dataCollection.updateEntityRefSelectorViewMode(mode)

  /** Updates the search query in the `entityref` step's `List` selector. */
  fun updateEntityRefSearchQuery(query: String) = dataCollection.updateEntityRefSearchQuery(query)

  /** Clears the search query in the `entityref` step's `List` selector. */
  fun clearEntityRefSearchQuery() = dataCollection.clearEntityRefSearchQuery()

  /**
   * Selects a target Geospatial Entity ([entityId]) at the `entityref` step (`/data/target_entity`)
   * during data collection when the form was launched without a pre-selected entity from the map.
   */
  fun selectEntityRefForActiveForm(entityId: String) =
    dataCollection.selectEntityRefForActiveForm(entityId)

  /**
   * Launches the embedded [org.openforis.collectone.v2.core.forms.ui.MobileFormRunner] (`
   * [FormWizardController]`) for [formId] on [entityId] when the organizer-defined form button
   * (`ctaLabel`) is tapped in the entity bottom sheet or triggered from the UX Chrome tester.
   */
  fun launchFormForEntity(entityId: String, formId: String) =
    dataCollection.launchFormForEntity(entityId, formId)

  /** Accepts the pending collector informed consent prompt and opens the queued form. */
  fun acceptCollectorConsent() = dataCollection.acceptCollectorConsent()

  /** Dismisses the pending collector informed consent prompt without opening the form. */
  fun dismissCollectorConsent() = dataCollection.dismissCollectorConsent()

  /**
   * Completes the active form submission when the user clicks `Submit ✓` in `MobileFormRunner` (or
   * when invoked programmatically with a finalized [recordInstance]): records the submission,
   * updates [activeSurveyNotice], and closes the active form runner.
   */
  fun completeActiveFormSubmission(
    recordInstance: RecordInstance? = null,
    entityStates: List<EntityState>? = null,
  ) = dataCollection.completeActiveFormSubmission(recordInstance, entityStates)

  /**
   * Cycles the [SyncStatus] of a specific [GeospatialEntityItem] ([entityId]) (`Uploading` ->
   * `Synced` -> `Failed` -> `Uploading`) when the client simulates uploads (no-op by default).
   */
  open fun cycleEntitySyncStatus(entityId: String) {}

  /**
   * Cycles the [SyncStatus] of a specific [SubmissionPreviewItem] ([submissionId]) (`Uploading` ->
   * `Synced` -> `Failed` -> `Uploading`) when the client simulates uploads (no-op by default).
   */
  open fun cycleSubmissionSyncStatus(submissionId: String) {}

  /** Closes the active `MobileFormRunner` and returns to the survey map/list screen. */
  fun closeActiveFormRunner() = dataCollection.closeActiveFormRunner()

  /** Opens the scannable S2 GeoID / Entity QR code modal dialog for [entityId]. */
  fun openEntityQrCode(entityId: String) = dataCollection.openEntityQrCode(entityId)

  /** Closes the active Entity QR code modal dialog. */
  fun closeEntityQrCode() = dataCollection.closeEntityQrCode()

  /**
   * Generates the map feature's PDF and opens the Share PDF sheet to share, save, or preview it.
   */
  fun shareEntityPdf(entityId: String) = dataCollection.shareEntityPdf(entityId)

  /** Generates the submission's PDF and opens the Share PDF sheet to share, save, or preview it. */
  fun shareSubmissionPdf(submissionId: String) = dataCollection.shareSubmissionPdf(submissionId)

  /** Generates the map feature's PDF and saves it straight away (web dashboard). */
  fun downloadEntityPdf(entityId: String) =
    dataCollection.downloadEntityPdf(entityId, ignoreOffline = isAlwaysOnline)

  /** Generates the submission's PDF and saves it straight away (web dashboard). */
  fun downloadSubmissionPdf(submissionId: String) =
    dataCollection.downloadSubmissionPdf(submissionId, ignoreOffline = isAlwaysOnline)

  /** True when the platform can hand files to other apps via the system share sheet. */
  val canSharePdfFiles: Boolean
    get() = dataCollectionState.canSharePdfFiles

  /** Opens the system share sheet for the PDF in the Share PDF sheet. */
  fun shareActivePdf() = dataCollection.shareActivePdf()

  /** Saves the PDF in the Share PDF sheet to the device. */
  fun saveActivePdf() = dataCollection.saveActivePdf()

  /** Opens the PDF in the Share PDF sheet in the platform's viewer. */
  fun previewActivePdf() = dataCollection.previewActivePdf()

  /** Closes the active Share PDF modal sheet. */
  fun closeSharePdfSheet() = dataCollection.closeSharePdfSheet()

  /** Clears [pdfExportMessage] once it has been shown. */
  fun dismissPdfExportMessage() = dataCollection.dismissPdfExportMessage()

  // --- Uploads ---

  /**
   * Synchronizes all currently pending/in-progress mutations in the `Outbox`, transitioning them to
   * [MutationSyncState.UPLOADED] with completed timestamps.
   */
  fun syncAllOutboxMutations() = dashboard.syncAllOutboxMutations()

  /** Signs the user out. */
  open fun signOut() {
    onboarding.signOut()
  }

  /** Clears the live Places API results and any search in flight. */
  protected fun clearPlacesSearch() {
    isMapboxPlacesSearching = false
    mapboxPlacesApiResults = emptyList()
  }

  /**
   * Resets every ViewModel of the session and the session's own state, as when the workbench resets
   * the store to its sample data.
   */
  open fun reset() {
    onboarding.reset()
    settings.reset()
    surveyMap.reset()
    dashboard.reset()
    dataCollection.reset()
    activeSurveyNotice = null
    clearPlacesSearch()
  }

  /** Evicts uploaded media attachments from local device cache (per `00-index.md`). */
  fun evictUploadedMediaCache() = settings.evictUploadedMediaCache()

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

  /**
   * Resolves the geographic bounds of [entity]'s geometry in the active survey (see
   * [EntityGeometry]); points have zero-size bounds.
   */
  fun resolveEntityLngLatBounds(entity: GeospatialEntityItem): LngLatBounds =
    EntityGeometry.bounds(entity, activeSurveyAnchor)

  /**
   * The camera the survey map should show: the user's GPS position shifted by the pan offset
   * ([mapPanOffsetX], [mapPanOffsetY]), at the survey's zoom plus [mapZoomDelta].
   */
  fun desiredMapCamera(): CameraPosition {
    mapState
    return surveyMap.desiredMapCamera()
  }

  /**
   * Updates the pan offset and zoom delta to match where the map [camera] settled after a gesture
   * or an explicit camera move. Moving the center stops following the user's GPS location.
   */
  fun syncMapCamera(camera: CameraPosition) = surveyMap.syncMapCamera(camera)

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

  /** Toggles straight-line navigation to [entityId] on or off. */
  fun toggleNavigationToEntity(entityId: String) = surveyMap.toggleNavigationToEntity(entityId)

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

  /** Toggles between Light and Dark Ground Material 3 themes. */
  fun toggleDarkTheme() = settings.toggleDarkTheme()

  companion object {
    /** Default width of the web dashboard's left-hand panel, in dp. */
    const val DEFAULT_SIDE_PANEL_WIDTH_DP = DashboardUiState.DEFAULT_SIDE_PANEL_WIDTH_DP

    /** Narrowest the web dashboard's left-hand panel can be dragged, in dp. */
    const val MIN_SIDE_PANEL_WIDTH_DP = DashboardUiState.MIN_SIDE_PANEL_WIDTH_DP

    /** Widest the web dashboard's left-hand panel can be dragged, in dp. */
    const val MAX_SIDE_PANEL_WIDTH_DP = DashboardUiState.MAX_SIDE_PANEL_WIDTH_DP

    /** Creates the [SettingsViewModel] of a session over [dataHolder]. */
    fun createSettingsViewModel(dataHolder: AppDataHolder): SettingsViewModel =
      SettingsViewModel(
        settingsRepository = dataHolder.settingsRepository,
        surveyRepository = dataHolder.surveyRepository,
        mutationRepository = dataHolder.mutationRepository,
        scope = dataHolder.scope,
        connectivityRepository = dataHolder.connectivityRepository,
      )
  }

  /**
   * The Survey editor's draft of the active survey: its stored draft, or one derived from the
   * survey's Forms, Map layers, and map features when it has never been edited.
   */
  val activeSurveyEditorDraft: SurveyEditorDraft
    get() = data.editorDraft
}
