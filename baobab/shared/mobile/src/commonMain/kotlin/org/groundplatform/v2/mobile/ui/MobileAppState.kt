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
package org.groundplatform.v2.mobile.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.groundplatform.v2.app.di.AppDataHolder
import org.groundplatform.v2.app.domain.model.AppScreen
import org.groundplatform.v2.app.domain.model.DeviceStorageInfo
import org.groundplatform.v2.app.domain.model.DownloadSurveyEntryOrigin
import org.groundplatform.v2.app.domain.model.FormPreviewItem
import org.groundplatform.v2.app.domain.model.FormSubmissionsGroup
import org.groundplatform.v2.app.domain.model.GeospatialEntityItem
import org.groundplatform.v2.app.domain.model.LANGUAGE_OPTIONS
import org.groundplatform.v2.app.domain.model.ListFilterTab
import org.groundplatform.v2.app.domain.model.MainDrawerSubView
import org.groundplatform.v2.app.domain.model.MainSurveyViewMode
import org.groundplatform.v2.app.domain.model.MapClusterFeatureItem
import org.groundplatform.v2.app.domain.model.MapLayerItem
import org.groundplatform.v2.app.domain.model.MeasurementUnitSystem
import org.groundplatform.v2.app.domain.model.MutationLogItem
import org.groundplatform.v2.app.domain.model.SubmissionGeometryPolygon
import org.groundplatform.v2.app.domain.model.SubmissionPreviewItem
import org.groundplatform.v2.app.domain.model.SurveyPreviewItem
import org.groundplatform.v2.app.domain.model.SyncStatus
import org.groundplatform.v2.app.domain.model.UploadStatusFilter
import org.groundplatform.v2.app.domain.model.WEBSITE_URL
import org.groundplatform.v2.app.domain.model.singularTypeLabelOf
import org.groundplatform.v2.app.domain.model.toClusterFeatures
import org.groundplatform.v2.app.ui.AppSession
import org.groundplatform.v2.app.ui.viewmodel.SettingsViewModel
import org.groundplatform.v2.mobile.ui.state.SyncStatusSimulator

/**
 * State holder of the Ground mobile app: an [AppSession] plus the mobile app shell (the active
 * [currentScreen], the Main Survey's map or list [mainViewMode], the navigation drawer, the user
 * profile panel, and the drawer's sub-screens), system Back handling, and reactions to the
 * simulated device connectivity (airplane mode).
 */
class MobileAppState(
  initialScreen: AppScreen = AppScreen.SIGN_IN,
  dataHolder: AppDataHolder = AppDataHolder(),
  settings: SettingsViewModel = createSettingsViewModel(dataHolder),
) : AppSession(dataHolder, settings) {

  var currentScreen by mutableStateOf(initialScreen)
    private set

  var mainViewMode by mutableStateOf(MainSurveyViewMode.MAP)
    private set

  var isDrawerOpen by mutableStateOf(false)
    private set

  var isUserProfileOpen by mutableStateOf(false)
    private set

  var activeDrawerSubView by mutableStateOf(MainDrawerSubView.NONE)
    private set

  /**
   * Called with the survey's ID whenever this app makes a survey active, so the workbench can load
   * the survey's primary form into its XForms editor.
   */
  var surveyActivationListener: ((surveyId: String) -> Unit)? = null

  /**
   * The workbench's upload simulator behind the sync status badges of the record views, or `null`
   * when the app runs outside the workbench (badges are then inert).
   */
  var syncStatusSimulator: SyncStatusSimulator? = null

  /** Whether online Mapbox Places API search is currently available (`isOnline`). */
  val isPlacesSearchAvailable: Boolean
    get() = mapState.isPlacesSearchAvailable

  // --- Reactions to shared session events ---

  override fun onSurveyActivated(surveyId: String) {
    surveyActivationListener?.invoke(surveyId)
  }

  override fun cycleEntitySyncStatus(entityId: String) {
    syncStatusSimulator?.cycleEntitySyncStatus(entityId)
  }

  override fun cycleSubmissionSyncStatus(submissionId: String) {
    syncStatusSimulator?.cycleSubmissionSyncStatus(submissionId)
  }

  override fun onScreenRequested(screen: AppScreen) {
    currentScreen = screen
    if (screen == AppScreen.SIGN_IN) {
      closeDrawerAndProfile()
      activeDrawerSubView = MainDrawerSubView.NONE
    }
  }

  override fun onSurveyOpened() {
    currentScreen = AppScreen.MAIN_SURVEY
    closeDrawerAndProfile()
    activeDrawerSubView = MainDrawerSubView.NONE
  }

  override fun onReturnToSurveyList() {
    closeDrawerAndProfile()
    currentScreen = AppScreen.MAIN_SURVEY
    activeDrawerSubView = MainDrawerSubView.SWITCH_SURVEYS
  }

  override fun onSignedOut() {
    closeDrawerAndProfile()
    activeDrawerSubView = MainDrawerSubView.NONE
  }

  override fun onSignedOutElsewhere() {
    if (currentScreen == AppScreen.SIGN_IN) return
    closeDrawerAndProfile()
    activeDrawerSubView = MainDrawerSubView.NONE
    currentScreen = AppScreen.SIGN_IN
  }

  override fun onMapShown() {
    mainViewMode = MainSurveyViewMode.MAP
  }

  override fun onNavigationStarted() {
    closeDrawerAndProfile()
    activeDrawerSubView = MainDrawerSubView.NONE
    mainViewMode = MainSurveyViewMode.MAP
    currentScreen = AppScreen.MAIN_SURVEY
  }

  override fun onUploadsOpened() {
    closeDrawerAndProfile()
    activeDrawerSubView = MainDrawerSubView.UPLOADS
  }

  override fun onFormsSheetOpened() {
    closeDrawerAndProfile()
  }

  override fun onListShown() {
    mainViewMode = MainSurveyViewMode.LIST
  }

  override fun onFormOpened() {
    closeDrawerAndProfile()
    activeDrawerSubView = MainDrawerSubView.NONE
    currentScreen = AppScreen.MAIN_SURVEY
  }

  private fun closeDrawerAndProfile() {
    isDrawerOpen = false
    isUserProfileOpen = false
  }

  // --- Simulated device connectivity and workbench tools ---

  /**
   * Applies a change of the simulated device connectivity: going offline stops Places search (and
   * leaves the `Places` list tab); coming back online re-runs the current list search.
   */
  fun onConnectivityChanged(isOnline: Boolean) {
    if (!isOnline) {
      clearPlacesSearch()
      if (listFilterTab == ListFilterTab.PLACES) {
        dashboard.selectListFilterTab(ListFilterTab.ALL)
      }
      showNotice(
        "Device offline: Places search disabled. Searching local $activeEntitiesCountNoun only."
      )
    } else {
      showNotice("Device online: Places API search enabled.")
      retryPlacesSearch()
    }
  }

  override fun reset() {
    currentScreen = AppScreen.SIGN_IN
    super.reset()
    mainViewMode = MainSurveyViewMode.MAP
    isDrawerOpen = false
    isUserProfileOpen = false
    activeDrawerSubView = MainDrawerSubView.NONE
  }

  // --- Navigation ---

  /** Directly switches the active mobile screen (used by both flow buttons and UX workbench). */
  fun navigateTo(screen: AppScreen) {
    if (screen == AppScreen.DOWNLOAD_SURVEY) {
      onboarding.setEntryOrigin(
        if (
          currentScreen == AppScreen.MAIN_SURVEY &&
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
    showNotice(null)
    isDrawerOpen = false
    activeDrawerSubView = MainDrawerSubView.NONE
  }

  /**
   * Switches between the collapsed Map peek state (`MAP`) and the expanded Searchable List state
   * (`LIST`) inside the unified persistent bottom sheet on the Main Survey screen.
   */
  override fun setMainSurveyViewMode(mode: MainSurveyViewMode) {
    mainViewMode = mode
    activeDrawerSubView = MainDrawerSubView.NONE
    super.setMainSurveyViewMode(mode)
  }

  /** Opens or closes the Hamburger Navigation Drawer. */
  fun updateDrawerOpen(open: Boolean) {
    isDrawerOpen = open
    if (open) {
      isUserProfileOpen = false
    }
  }

  /** Opens or closes the right-hand User Account Panel on mobile. */
  fun updateUserProfileOpen(open: Boolean) {
    isUserProfileOpen = open
    if (open) {
      isDrawerOpen = false
    }
  }

  /**
   * Launches data collection for [formId] from the bottom-centered Floating Action Button (FAB)
   * list of available forms (**with no entity pre-selected from the map**): if the form requires a
   * geospatial entity, the wizard includes the `entityref` step whose **Map or List** picker opens
   * in the current [mainViewMode].
   */
  fun launchFormFromFab(formId: String) =
    dataCollection.launchFormFromFab(formId, pickerMode = mainViewMode)

  // --- Hamburger Navigation Drawer Actions ---

  /**
   * Drawer option 1: "Surveys" — opens the dedicated screen showing only surveys that have already
   * been downloaded onto the device, with a primary button to browse & download more surveys.
   */
  fun drawerSwitchSurveys() {
    isDrawerOpen = false
    activeDrawerSubView = MainDrawerSubView.SWITCH_SURVEYS
  }

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

  /** Navigates from the "Surveys" screen to the full "Download survey" directory screen. */
  fun openDownloadMoreSurveysScreen() {
    isDrawerOpen = false
    activeDrawerSubView = MainDrawerSubView.NONE
    onboarding.setEntryOrigin(DownloadSurveyEntryOrigin.SURVEY_LIST)
    currentScreen = AppScreen.DOWNLOAD_SURVEY
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
    currentScreen = AppScreen.TERMS_OF_SERVICE
  }

  /** Drawer option 5: Sign out. */
  fun drawerSignOut() {
    isDrawerOpen = false
    activeDrawerSubView = MainDrawerSubView.NONE
    super.signOut()
    currentScreen = AppScreen.SIGN_IN
  }

  /** Signs the user out of the application and returns to the Sign In screen. */
  override fun signOut() {
    drawerSignOut()
  }

  // --- Basemap visibility ---

  /**
   * Whether the current screen shows the survey basemap. On web the Mapbox basemap renders behind
   * the Compose canvas, so hosts keep their root surface transparent while this is true.
   */
  val isMapShowing: Boolean
    get() {
      val dataCollection = dataCollectionUiState
      val isEntityRefMapShowing =
        dataCollection.isDataCollectionFormOpen &&
          dataCollection.isCurrentFormStepEntityRef &&
          dataCollection.entityRefSelectorViewMode == MainSurveyViewMode.MAP
      return currentScreen == AppScreen.MAIN_SURVEY &&
        activeDrawerSubView == MainDrawerSubView.NONE &&
        (!dataCollection.isDataCollectionFormOpen ||
          dataCollection.isCurrentFormStepGeoPoint ||
          isEntityRefMapShowing)
    }

  // --- System Back ---

  /** Whether the system Back gesture has something to close or go back from. */
  val canHandleBack: Boolean
    get() =
      dataCollectionUiState.isDataCollectionFormOpen ||
        activeDrawerSubView != MainDrawerSubView.NONE ||
        isDrawerOpen ||
        selectedSubmissionId != null ||
        selectedEntityId != null ||
        currentScreen == AppScreen.DOWNLOAD_SURVEY ||
        currentScreen == AppScreen.TERMS_OF_SERVICE

  /**
   * Handles the system Back gesture: closes the innermost open surface (form, drawer sub-screen,
   * drawer, submission, map feature) or steps back through onboarding. No-op unless
   * [canHandleBack].
   */
  fun handleBack() {
    when {
      dataCollectionUiState.isDataCollectionFormOpen -> closeActiveFormRunner()
      activeDrawerSubView != MainDrawerSubView.NONE -> closeDrawerSubView()
      isDrawerOpen -> updateDrawerOpen(false)
      selectedSubmissionId != null -> selectSubmissionDetail(null)
      selectedEntityId != null -> selectEntity(null)
      currentScreen == AppScreen.DOWNLOAD_SURVEY -> navigateBackFromDownloadSurvey()
      currentScreen == AppScreen.TERMS_OF_SERVICE -> declineTermsOfService()
    }
  }

  // --- Session state and actions only the mobile app uses ---

  /** Tracks whether the Download surveys screen was reached after ToS or from the Survey list. */
  val downloadSurveyEntryOrigin: DownloadSurveyEntryOrigin
    get() = onboardingState.entryOrigin

  /** True when the sign-out confirmation prompt is open on the Download surveys screen. */
  val isDownloadSurveySignOutPromptOpen: Boolean
    get() = onboardingState.isSignOutPromptOpen

  /** True when the Download surveys screen was opened from the Downloaded Surveys list. */
  val isDownloadSurveyAccessedFromSurveyList: Boolean
    get() = downloadSurveyEntryOrigin == DownloadSurveyEntryOrigin.SURVEY_LIST

  /** Forms collectors can start from the mobile app's entry points. */
  val mobileForms: List<FormPreviewItem>
    get() = dataCollectionState.mobileForms

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

  val selectedLanguageCode: String
    get() = data.userSettings.language

  val shouldUploadPhotosOnWifiOnly: Boolean
    get() = data.userSettings.shouldUploadPhotosOnWifiOnly

  /** Display label of the currently selected language (e.g. `"English"`, `"Français"`). */
  val selectedLanguageDisplayName: String
    get() = LANGUAGE_OPTIONS.firstOrNull { it.code == selectedLanguageCode }?.label ?: "English"

  /**
   * Device storage breakdown showing total device storage, free storage, storage occupied by
   * downloaded imagery (vector & raster basemap tiles), and space taken up by data (surveys, forms,
   * entities, submissions, mutations).
   */
  val deviceStorageInfo: DeviceStorageInfo
    get() = settingsState.storage

  // --- User GPS Location & Auto-Centering Map Camera State (see [SurveyMapViewModel]) ---

  /** Formatted current Mapbox zoom level badge (e.g. `"15.3z"`). */
  val effectiveMapZoomLabel: String
    get() = mapState.effectiveMapZoomLabel

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

  /** Formatted GPS accuracy badge displayed on the chip over the map (`±2.1 m` or `±6.8 ft`). */
  val gnssStatusChipLabel: String
    get() = mapState.gnssStatusChipLabel

  // --- GeoID QR code & PDF export dialogs (see [DataCollectionViewModel]) ---

  /** Formatted distance & compass bearing badge for [submissionId] (e.g. `"452 m • 321° NW"`). */
  fun formattedWayfindingBadgeForSubmission(submissionId: String): String =
    surveyMap.formattedWayfindingBadgeForSubmission(submissionId)

  /** True when straight-line navigation is currently active and targeting [submissionId]. */
  fun isNavigatingToSubmission(submissionId: String): Boolean =
    mapState.isNavigatingToSubmission(submissionId)

  /** Eligible [GeospatialEntityItem]s for the currently active data collection form. */
  val eligibleEntitiesForActiveForm: List<GeospatialEntityItem>
    get() = dataCollectionState.eligibleEntitiesForActiveForm

  /**
   * True when the embedded form runner is open and the current step in [activeFormWizardController]
   * contains a `geopoint` (`DataType.TYPE_GEOPOINT`) question.
   */
  val isCurrentFormStepGeoPoint: Boolean
    get() = dataCollectionState.isCurrentFormStepGeoPoint

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

  /** True when the active survey has geospatial entities to display in the `Layers` sheet. */
  val hasGeospatialEntities: Boolean
    get() = entities.isNotEmpty() && entityDatasetLayers.isNotEmpty()

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
   * Resolves the user-facing singular domain noun for [entityId] (e.g. `"Coffee Parcel"`, or
   * `"Location"`).
   */
  fun entitySingularTypeLabel(entityId: String?): String = entities.singularTypeLabelOf(entityId)

  /**
   * Form submission geometries are not displayed on the map; only entity geometries (map features)
   * are shown.
   */
  val visibleSubmissionGeometries: List<SubmissionGeometryPolygon>
    get() = emptyList()

  /**
   * All visible map features (`visibleMapEntities`) normalized into [MapClusterFeatureItem]
   * instances for clustering. Form submission geometries are not displayed on the map or in cluster
   * chips.
   */
  val visibleMapClusterFeatures: List<MapClusterFeatureItem>
    get() = visibleMapEntities.toClusterFeatures()

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
   * Form submissions are not searchable via the bottom-sheet search bar because submissions have no
   * unique key to identify them with; returns an empty list.
   */
  val filteredListSubmissions: List<SubmissionPreviewItem>
    get() = emptyList()

  /**
   * Form submissions are excluded from the searchable bottom sheet list since there is no unique
   * key to identify them with; returns an empty list.
   */
  val groupedFilteredListSubmissions: List<FormSubmissionsGroup>
    get() = emptyList()

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

  /** Asks before removing [surveyId]'s offline copy; downloads it instead if not downloaded. */
  fun promptRemoveDownloadedSurvey(surveyId: String) =
    onboarding.promptRemoveDownloadedSurvey(surveyId)

  /** Confirms removal of the pending downloaded survey from the device and dismisses the dialog. */
  fun confirmRemoveDownloadedSurvey() = onboarding.confirmRemoveDownloadedSurvey()

  /** Dismisses the remove-downloaded-survey confirmation dialog without changes. */
  fun dismissRemoveDownloadedSurvey() = onboarding.dismissRemoveDownloadedSurvey()

  // --- Main Survey UI Actions ---

  /** Toggles between `Normal` and `Satellite` basemap. */
  fun toggleBasemapType() = surveyMap.toggleBasemapType()

  /**
   * Selects a submission geometry polygon on the map and opens its submission (and parent entity if
   * attached, or standalone submission card if unattached).
   */
  fun selectSubmissionGeometry(geometryId: String) =
    dataCollection.selectSubmissionGeometry(geometryId)

  /** Toggles the Entity Bottom Sheet between expanded and collapsed (peek) state. */
  fun toggleEntityBottomSheetExpanded() = surveyMap.toggleEntityBottomSheetExpanded()

  /**
   * Updates the active category filter tab (`All`, `Places`, `Map features`).
   *
   * When offline (`!isOnline`), selecting [ListFilterTab.PLACES] is blocked and surfaces an offline
   * notice.
   */
  fun selectListFilterTab(tab: ListFilterTab) {
    if (!isOnline && tab == ListFilterTab.PLACES) {
      activeSurveyNotice =
        "Device offline: Places search is not available offline. Searching local $activeEntitiesCountNoun only."
      return
    }
    dashboard.selectListFilterTab(tab)
  }

  /** Opens the Available Forms modal bottom sheet triggered by the bottom-centered FAB. */
  fun openAvailableFormsSheet() = dataCollection.openAvailableFormsSheet()

  /** Closes the Available Forms modal bottom sheet. */
  fun closeAvailableFormsSheet() = dataCollection.closeAvailableFormsSheet()

  /**
   * Launches the active XForms `FormDef` on the currently selected entity (or as a standalone form
   * when the survey has no predefined entities).
   */
  fun launchActiveOrDefaultFormForTesting() = dataCollection.launchActiveOrDefaultFormForTesting()

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

  /**
   * Synchronizes a single Outbox mutation ([mutationId]), transitioning its state to
   * [MutationSyncState.UPLOADED] with a completed timestamp and moving it into `Uploaded`.
   */
  fun syncMutationNow(mutationId: String) = dashboard.syncMutationNow(mutationId)

  /** Shows [message] as the session's notice (or clears it when `null`). */
  fun showNotice(message: String?) {
    activeSurveyNotice = message
  }

  /** Re-runs the Places API search for the current list query, if any. */
  private fun retryPlacesSearch() {
    val trimmed = listSearchQuery.trim()
    if (trimmed.isNotEmpty()) {
      triggerMapboxPlacesApiSearch(trimmed)
    }
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
  fun visitGroundWebsite(url: String = WEBSITE_URL) = settings.visitWebsite(url)

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

  /** Resolves the geographic coordinates `(lng, lat)` for [entity] in the active survey. */
  fun resolveEntityLngLat(entity: GeospatialEntityItem): Pair<Double, Double> =
    surveyMap.resolveEntityLngLat(entity)

  /**
   * Syncs [mapZoomDelta] after the map camera changed its own zoom (e.g. fitting a selected map
   * feature), so clustering and the scale bar follow the camera.
   */
  fun syncMapZoomDelta(zoomDelta: Float) = surveyMap.syncMapZoomDelta(zoomDelta)

  /** Zooms the Mapbox map in by one step (`+0.75` zoom levels). */
  fun zoomInMap() = surveyMap.zoomInMap()

  /** Zooms the Mapbox map out by one step (`-0.75` zoom levels). */
  fun zoomOutMap() = surveyMap.zoomOutMap()

  /** Resets the Mapbox zoom level to the active survey's default (`15.3z`). */
  fun resetMapZoom() = surveyMap.resetMapZoom()

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

  /** Toggles straight-line navigation to [submissionId] on or off. */
  fun toggleNavigationToSubmission(submissionId: String) =
    surveyMap.toggleNavigationToSubmission(submissionId)

  /**
   * Advances the collector's simulated GPS blue dot along the active straight-line navigation
   * vector toward the target entity or submission by [stepFraction] (`0.40f` by default, snapping
   * directly onto the destination when within `12` meters).
   */
  fun stepUserTowardNavigationTarget(stepFraction: Float = 0.40f) =
    surveyMap.stepUserTowardNavigationTarget(stepFraction)
}
