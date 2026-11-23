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
package org.openforis.collectone.v2.mobile.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.openforis.collectone.v2.app.di.AppDataHolder
import org.openforis.collectone.v2.app.ui.AppViewModels
import org.openforis.collectone.v2.app.ui.datacollection.DataCollectionUiState
import org.openforis.collectone.v2.app.ui.map.pluralText
import org.openforis.collectone.v2.app.ui.navigation.AppScreen
import org.openforis.collectone.v2.app.ui.navigation.DownloadSurveyEntryOrigin
import org.openforis.collectone.v2.app.ui.navigation.MainSurveyViewMode
import org.openforis.collectone.v2.app.ui.session.ActiveSurveyAccess
import org.openforis.collectone.v2.app.ui.session.NoticeController
import org.openforis.collectone.v2.app.ui.session.SessionCoordinator
import org.openforis.collectone.v2.app.ui.session.SessionEntityDetailsActions
import org.openforis.collectone.v2.app.ui.session.SessionSearchableListActions
import org.openforis.collectone.v2.app.ui.session.SessionShell
import org.openforis.collectone.v2.app.ui.text.toUiText
import org.openforis.collectone.v2.map.LngLatBounds
import org.openforis.collectone.v2.mobile.resources.Res
import org.openforis.collectone.v2.mobile.resources.mobile_notice_device_offline
import org.openforis.collectone.v2.mobile.resources.mobile_notice_device_online
import org.openforis.collectone.v2.mobile.ui.navigation.MainSurveyActions
import org.openforis.collectone.v2.mobile.ui.navigation.MainSurveyPage
import org.openforis.collectone.v2.mobile.ui.navigation.MainSurveyViewModel
import org.openforis.collectone.v2.mobile.ui.navigation.MobileNavigator
import org.openforis.collectone.v2.mobile.ui.navigation.MobileRoute
import org.openforis.collectone.v2.mobile.ui.navigation.appScreen
import org.openforis.collectone.v2.mobile.ui.navigation.toRoute
import org.openforis.collectone.v2.mobile.ui.offlinemaps.OfflineAreaSelectionViewModel
import org.openforis.collectone.v2.mobile.ui.state.MobileWorkbenchHooks

/**
 * Shell of the Collect One mobile app: where the user is (a back stack of [MobileRoute]s in
 * [navigator]), the Main Survey's map or list [mainViewMode], the navigation drawer and the user
 * profile panel, the session's notice, and reactions to device connectivity (reported by the host's
 * [org.openforis.collectone.v2.mobile.services.ConnectivityMonitor], or simulated by the
 * workbench's airplane mode).
 *
 * The feature ViewModels ([viewModels]) own the app's state, and the screens read it from them; a
 * [SessionCoordinator] wires them together and tells this shell (a [SessionShell]) which screen to
 * show.
 */
class MobileAppState(
  initialRoute: MobileRoute = MobileRoute.SignIn,
  val dataHolder: AppDataHolder = AppDataHolder(),
  /**
   * The feature ViewModels of the session. Hosts pass the ones `koinAppViewModels()` returns, so
   * they outlive the session across configuration changes; by default the session gets its own.
   */
  val viewModels: AppViewModels = AppViewModels.create(dataHolder),
  /**
   * The Main Survey's map or list view, kept across Android process death. Hosts pass the one
   * `koinViewModel()` returns; by default the session gets its own.
   */
  private val mainSurvey: MainSurveyViewModel = MainSurveyViewModel(),
  /**
   * The area selection mode, where the user frames an area of the main map to save for offline use.
   * Hosts pass the one `koinViewModel()` returns; by default the session gets its own.
   */
  val offlineAreaSelection: OfflineAreaSelectionViewModel =
    OfflineAreaSelectionViewModel(
      offlineMapRepository = dataHolder.koin.get(),
      estimateOfflineArea = dataHolder.koin.get(),
      saveOfflineArea = dataHolder.koin.get(),
    ),
  /**
   * Whether a route shown before the launch (restored after Android process death, or named by the
   * browser history on a reload) may be shown again; see [MobileNavigator.mayRestore].
   */
  mayRestoreRoute: (MobileRoute) -> Boolean = { true },
) : MainSurveyActions, SessionShell, AutoCloseable {

  /**
   * Where the user is: the back stack of routes, starting at [initialRoute]. The app's `NavHost`
   * attaches to it, so the system Back gesture and the browser history move through it too.
   */
  val navigator =
    MobileNavigator(
      initialRoute,
      onRouteChanged = ::onRouteChanged,
      mayRestoreRoute = mayRestoreRoute,
    )

  /** The route on top of the back stack, whoever navigated there. */
  val currentRoute: MobileRoute
    get() = navigator.currentRoute

  private var previousRoute = initialRoute

  /** The drawer page the area selection mode was started from, to return to when it closes. */
  private var offlineAreaSelectionOrigin: MobileRoute? = null

  /** The session's notice, which the outcomes of every feature replace or clear. */
  val notices = NoticeController()

  /** Whether the navigation drawer is open; never at the same time as the profile panel. */
  var isDrawerOpen by mutableStateOf(false)
    private set

  /** Whether the user profile panel is open; never at the same time as the drawer. */
  var isUserProfileOpen by mutableStateOf(false)
    private set

  /**
   * The workbench's hooks (survey activation and simulated uploads), or `null` when the app runs
   * outside the workbench: the sync status badges are then inert and Retry uploads for real. Set
   * once by `WorkbenchState` right after construction, before the app is composed.
   */
  var workbenchHooks: MobileWorkbenchHooks? = null

  /** Intents of the Main Survey's searchable list of map features and places. */
  val searchableListActions = SessionSearchableListActions(viewModels)

  /** Intents of the map feature details in the Main Survey's bottom sheet. */
  val entityDetailsActions =
    SessionEntityDetailsActions(
      viewModels,
      onCycleEntitySyncStatus = { workbenchHooks?.cycleEntitySyncStatus(it) },
      onCycleSubmissionSyncStatus = { workbenchHooks?.cycleSubmissionSyncStatus(it) },
    )

  /** Scope of this shell's own reactions to the ViewModels. [close] cancels it. */
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

  /** Routes the ViewModels' cross-feature events, telling this shell what to show. */
  private val coordinator = SessionCoordinator(dataHolder, viewModels, notices, shell = this)

  init {
    dataHolder.setSurveyListOpen(initialRoute == MobileRoute.DownloadSurvey)
    scope.launch {
      viewModels.dataCollection.uiState
        .map { it.isDataCollectionFormOpen && it.activeFormWizardController != null }
        .distinctUntilChanged()
        .collect(::onFormShownChanged)
    }
  }

  /** Stops following the ViewModels. Hosts call it when the session leaves composition. */
  override fun close() {
    scope.cancel()
    coordinator.close()
  }

  /** What the signed-in user may do in the active survey, from the latest data and sign-in. */
  val activeSurveyAccess: ActiveSurveyAccess
    get() = ActiveSurveyAccess.of(dataHolder.appData.value, viewModels.onboarding.uiState.value)

  /**
   * Follows the visible screen, whoever changed it: the list of surveys to download is kept current
   * only while it's shown, and leaving the form (for example with the browser's Back button) closes
   * it.
   */
  private fun onRouteChanged(route: MobileRoute) {
    dataHolder.setSurveyListOpen(route == MobileRoute.DownloadSurvey)
    val left = previousRoute
    previousRoute = route
    if (route != MobileRoute.MainSurvey && offlineAreaSelection.uiState.value.isActive) {
      // Left the map (e.g. with the browser's Back button) while framing an area.
      offlineAreaSelectionOrigin = null
      stopOfflineAreaSelection()
    }
    val dataCollection = viewModels.dataCollection
    if (
      left == MobileRoute.DataCollection &&
        route != MobileRoute.DataCollection &&
        dataCollection.uiState.value.isDataCollectionFormOpen
    ) {
      dataCollection.closeActiveFormRunner()
    }
  }

  /** Shows the data collection form over the Main Survey while it's open, and leaves it after. */
  private fun onFormShownChanged(isShown: Boolean) {
    if (isShown) {
      if (currentRoute != MobileRoute.DataCollection) {
        showMainSurvey()
        navigator.navigate(MobileRoute.DataCollection)
      }
    } else if (currentRoute == MobileRoute.DataCollection) {
      navigator.back()
    }
  }

  /**
   * Returns to the Main Survey, keeping its place on the back stack, and opens [page] over it (or
   * returns to [page] when it's already open).
   */
  private fun showMainSurvey(page: MainSurveyPage? = null) {
    if (page != null && navigator.popBackTo(page)) return
    if (!navigator.popBackTo(MobileRoute.MainSurvey)) navigator.resetTo(MobileRoute.MainSurvey)
    page?.let(navigator::navigate)
  }

  /**
   * Whether the Main Survey shows the map or the list. Kept by [MainSurveyViewModel] so it survives
   * process death; only this shell changes it, in reaction to navigation and cross-feature events.
   */
  var mainViewMode: MainSurveyViewMode
    get() = mainSurvey.viewMode
    private set(value) {
      mainSurvey.viewMode = value
    }

  // --- Uploads ---

  /**
   * Retries every failed upload of the active survey: changes the server rejected go back into the
   * upload queue and sync now. In the workbench, its [workbenchHooks] simulate the retry instead.
   */
  override fun retryFailedUploads() {
    workbenchHooks?.retryFailedUploads() ?: viewModels.dashboard.syncAllOutboxMutations()
  }

  // --- Reactions to the session's cross-feature outcomes (SessionShell) ---

  override fun onSurveyActivated(surveyId: String) {
    workbenchHooks?.onSurveyActivated(surveyId)
  }

  override fun onScreenRequested(screen: AppScreen) {
    if (screen == AppScreen.SIGN_IN) closeDrawerAndProfile()
    navigator.resetTo(screen.toRoute())
  }

  override fun onSurveyOpened() {
    closeDrawerAndProfile()
    showMainSurvey()
  }

  override fun onReturnToSurveyList() {
    closeDrawerAndProfile()
    showMainSurvey(MobileRoute.DownloadedSurveys)
  }

  override fun onSignedOut() {
    closeDrawerAndProfile()
  }

  override fun onSignedOutElsewhere() {
    if (currentRoute == MobileRoute.SignIn) return
    closeDrawerAndProfile()
    navigator.resetTo(MobileRoute.SignIn)
  }

  override fun onMapShown() {
    mainViewMode = MainSurveyViewMode.MAP
  }

  override fun onNavigationStarted() {
    closeDrawerAndProfile()
    mainViewMode = MainSurveyViewMode.MAP
    showMainSurvey()
  }

  override fun onUploadsOpened() {
    closeDrawerAndProfile()
    showMainSurvey(MobileRoute.Uploads)
  }

  override fun onFormsSheetOpened() {
    closeDrawerAndProfile()
  }

  override fun onListShown() {
    mainViewMode = MainSurveyViewMode.LIST
  }

  /** The form shows over the Main Survey as soon as data collection reports it open. */
  override fun onFormOpened() {
    closeDrawerAndProfile()
  }

  private fun closeDrawerAndProfile() {
    isDrawerOpen = false
    isUserProfileOpen = false
  }

  // --- Device connectivity and workbench tools ---

  /**
   * Applies a report from the host's
   * [org.openforis.collectone.v2.mobile.services.ConnectivityMonitor]: when the device's
   * connectivity changed, updates the app's connectivity and reacts as [onConnectivityChanged].
   */
  fun updateDeviceConnectivity(isOnline: Boolean) {
    val connectivity = dataHolder.connectivityRepository
    if (connectivity.isOnline() == isOnline) return
    connectivity.setOnline(isOnline)
    onConnectivityChanged(isOnline)
  }

  /**
   * Notices a change of the device connectivity (already applied to the app's connectivity, which
   * the list search follows on its own). Download survey shows its own offline banner, so the
   * offline and back-online notices are skipped there.
   */
  fun onConnectivityChanged(isOnline: Boolean) {
    if (currentRoute == MobileRoute.DownloadSurvey) return
    if (!isOnline) {
      val countNoun = viewModels.surveyMap.uiState.value.activeEntitiesCountNoun
      showLocalizedNotice(Res.string.mobile_notice_device_offline, countNoun.pluralText)
    } else {
      showLocalizedNotice(Res.string.mobile_notice_device_online)
    }
  }

  /**
   * Shows the notice [resource], formatted with [args] (a [UiText] argument is localized too), once
   * its text is loaded.
   */
  private fun showLocalizedNotice(resource: StringResource, vararg args: Any) {
    dataHolder.launch { notices.show(resource.toUiText(*args).resolveString()) }
  }

  /**
   * Resets the app to Sign in with every ViewModel and the shell at their defaults, as when the
   * workbench resets the store to its sample data.
   */
  fun reset() {
    navigator.resetTo(MobileRoute.SignIn)
    coordinator.reset()
    mainViewMode = MainSurveyViewMode.MAP
    isDrawerOpen = false
    isUserProfileOpen = false
    offlineAreaSelectionOrigin = null
    stopOfflineAreaSelection()
  }

  // --- Navigation ---

  /**
   * Switches between the collapsed Map peek state (`MAP`) and the expanded Searchable List state
   * (`LIST`) inside the unified persistent bottom sheet on the Main Survey screen. The list shows
   * expanded with nothing selected; the map collapses the sheet.
   */
  fun setMainSurveyViewMode(mode: MainSurveyViewMode) {
    mainViewMode = mode
    if (currentRoute is MainSurveyPage) navigator.popBackTo(MobileRoute.MainSurvey)
    val surveyMap = viewModels.surveyMap
    if (mode == MainSurveyViewMode.LIST) {
      viewModels.dataCollection.clearSelection()
      surveyMap.updateEntityBottomSheetExpanded(true)
      surveyMap.updateLayersSheetOpen(false)
    } else {
      surveyMap.updateEntityBottomSheetExpanded(false)
    }
  }

  /** Opening the drawer closes the profile panel: the two slide in over each other. */
  override fun updateDrawerOpen(open: Boolean) {
    isDrawerOpen = open
    if (open) {
      isUserProfileOpen = false
    }
  }

  /** Opening the profile panel closes the drawer: the two slide in over each other. */
  override fun updateUserProfileOpen(open: Boolean) {
    isUserProfileOpen = open
    if (open) {
      isDrawerOpen = false
    }
  }

  // --- Hamburger Navigation Drawer Actions ---

  /** Drawer option Surveys: the surveys on the device, opened over the Main Survey. */
  override fun drawerSwitchSurveys() {
    isDrawerOpen = false
    showMainSurvey(MobileRoute.DownloadedSurveys)
  }

  /**
   * Opens Download survey from the Surveys page. Records that it was opened from there, so Back
   * returns to the survey list instead of asking to sign out, as it does during the first run.
   */
  fun openDownloadMoreSurveysScreen() {
    isDrawerOpen = false
    viewModels.onboarding.setEntryOrigin(DownloadSurveyEntryOrigin.SURVEY_LIST)
    navigator.navigate(MobileRoute.DownloadSurvey)
  }

  override fun drawerManageOfflineMaps() {
    isDrawerOpen = false
    showMainSurvey(MobileRoute.OfflineMaps)
  }

  // --- Map areas saved for offline use ---

  /**
   * Starts the area selection mode on the main map, from the layers sheet or Offline maps: closes
   * the drawer, the profile panel, and any drawer page first, so the map shows (and stays mounted).
   * Does nothing on devices that can't save map areas.
   */
  override fun startOfflineAreaSelection() {
    if (!offlineAreaSelection.canSaveAreas) return
    offlineAreaSelectionOrigin = currentRoute.takeIf { it == MobileRoute.OfflineMaps }
    closeDrawerAndProfile()
    mainViewMode = MainSurveyViewMode.MAP
    showMainSurvey()
    val surveyMap = viewModels.surveyMap
    surveyMap.updateLayersSheetOpen(false)
    surveyMap.updateEntityBottomSheetExpanded(false)
    surveyMap.setOfflineAreaSelectionActive(true)
    offlineAreaSelection.start()
  }

  /**
   * Leaves the area selection mode (Close, Back, Cancel, Hide, or Done) and returns to where it was
   * started; a save in progress continues.
   */
  override fun closeOfflineAreaSelection() {
    val origin = offlineAreaSelectionOrigin
    offlineAreaSelectionOrigin = null
    stopOfflineAreaSelection()
    if (origin == MobileRoute.OfflineMaps) showMainSurvey(MobileRoute.OfflineMaps)
  }

  private fun stopOfflineAreaSelection() {
    offlineAreaSelection.stop()
    viewModels.surveyMap.setOfflineAreaSelectionActive(false)
  }

  /** Shows the saved area [bounds] on the main map, from Offline maps. */
  fun showOfflineAreaOnMap(bounds: LngLatBounds) {
    closeDrawerSubView()
    mainViewMode = MainSurveyViewMode.MAP
    viewModels.surveyMap.updateEntityBottomSheetExpanded(false)
    viewModels.surveyMap.frameBounds(bounds)
  }

  override fun drawerOpenSettings() {
    isDrawerOpen = false
    showMainSurvey(MobileRoute.Settings)
  }

  /**
   * Drawer option Uploads, with no map feature filter. The dashboard reports it and the coordinator
   * calls [onUploadsOpened], which navigates, so Uploads opened from a feature's details goes
   * through the same path.
   */
  override fun drawerOpenUploads() = viewModels.dashboard.showUploads(null)

  /**
   * Closes the open drawer page (Surveys, Uploads, Offline maps, or Settings) and returns to the
   * survey. Also drops the map feature filter Uploads may have been opened with, so the drawer's
   * Uploads opens unfiltered next time.
   */
  fun closeDrawerSubView() {
    navigator.popBackTo(MobileRoute.MainSurvey)
    viewModels.dashboard.clearUploadsEntityFilter()
  }

  /**
   * Drawer option Terms of Service. Pushed over the current route rather than opened as a drawer
   * page, so Back returns to where the user was.
   */
  override fun drawerViewTermsOfService() {
    isDrawerOpen = false
    navigator.navigate(MobileRoute.TermsOfService)
  }

  /**
   * Returns to Sign in when a signed-out user is on a survey screen, as the browser's Back and
   * Forward buttons can restore after signing out.
   */
  fun returnToSignInIfSignedOut() {
    val isSignedIn = viewModels.onboarding.uiState.value.isSignedIn
    if (!isSignedIn && currentRoute.appScreen == AppScreen.MAIN_SURVEY) {
      navigator.resetTo(MobileRoute.SignIn)
    }
  }

  /** Signs out from the account menu: closes the drawer and any page, then shows Sign in. */
  override fun signOut() {
    isDrawerOpen = false
    viewModels.onboarding.signOut()
    navigator.resetTo(MobileRoute.SignIn)
  }

  // --- Basemap visibility ---

  /**
   * Whether the current screen is the main survey map screen (with no drawer page or data
   * collection form open) given [dataCollection]. When true, the top status bar / top inset is
   * rendered transparent over the map and the map extends behind the top inset.
   */
  fun isMainMapScreenShowing(
    dataCollection: DataCollectionUiState = viewModels.dataCollection.uiState.value
  ): Boolean = currentRoute == MobileRoute.MainSurvey && !dataCollection.isDataCollectionFormOpen

  /**
   * Whether the current screen shows the survey basemap given [dataCollection]. On web the Mapbox
   * basemap renders behind the Compose canvas, so hosts keep their root surface transparent while
   * this is true.
   */
  fun isMapShowing(
    dataCollection: DataCollectionUiState = viewModels.dataCollection.uiState.value
  ): Boolean {
    val isEntityRefMapShowing =
      dataCollection.isDataCollectionFormOpen &&
        dataCollection.isCurrentFormStepEntityRef &&
        dataCollection.entityRefSelectorViewMode == MainSurveyViewMode.MAP
    val isSurveyRoute =
      currentRoute == MobileRoute.MainSurvey || currentRoute == MobileRoute.DataCollection
    return isSurveyRoute &&
      (!dataCollection.isDataCollectionFormOpen ||
        dataCollection.isCurrentFormStepGeoPoint ||
        isEntityRefMapShowing)
  }
}
