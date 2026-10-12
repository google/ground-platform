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
import org.groundplatform.v2.app.domain.model.DownloadSurveyEntryOrigin
import org.groundplatform.v2.app.domain.model.ListFilterTab
import org.groundplatform.v2.app.domain.model.MainDrawerSubView
import org.groundplatform.v2.app.domain.model.MainSurveyViewMode
import org.groundplatform.v2.app.domain.model.PrototypeScreen
import org.groundplatform.v2.app.domain.model.UploadStatusFilter
import org.groundplatform.v2.app.ui.AppSession
import org.groundplatform.v2.app.ui.viewmodel.SettingsViewModel

/**
 * State holder of the Ground mobile app: an [AppSession] plus the mobile app shell (the active
 * [currentScreen], the Main Survey's map or list [mainViewMode], the navigation drawer, the user
 * profile panel, and the drawer's sub-screens), system Back handling, and reactions to the
 * simulated device connectivity (airplane mode).
 */
class MobileAppState(
  initialScreen: PrototypeScreen = PrototypeScreen.SIGN_IN,
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

  // --- Reactions to shared session events ---

  override fun onScreenRequested(screen: PrototypeScreen) {
    currentScreen = screen
    if (screen == PrototypeScreen.SIGN_IN) {
      closeDrawerAndProfile()
      activeDrawerSubView = MainDrawerSubView.NONE
    }
  }

  override fun onSurveyOpened() {
    currentScreen = PrototypeScreen.MAIN_SURVEY
    closeDrawerAndProfile()
    activeDrawerSubView = MainDrawerSubView.NONE
  }

  override fun onReturnToSurveyList() {
    closeDrawerAndProfile()
    currentScreen = PrototypeScreen.MAIN_SURVEY
    activeDrawerSubView = MainDrawerSubView.SWITCH_SURVEYS
  }

  override fun onSignedOut() {
    closeDrawerAndProfile()
    activeDrawerSubView = MainDrawerSubView.NONE
  }

  override fun onSignedOutElsewhere() {
    if (currentScreen == PrototypeScreen.SIGN_IN) return
    closeDrawerAndProfile()
    activeDrawerSubView = MainDrawerSubView.NONE
    currentScreen = PrototypeScreen.SIGN_IN
  }

  override fun onMapShown() {
    mainViewMode = MainSurveyViewMode.MAP
  }

  override fun onNavigationStarted() {
    closeDrawerAndProfile()
    activeDrawerSubView = MainDrawerSubView.NONE
    mainViewMode = MainSurveyViewMode.MAP
    currentScreen = PrototypeScreen.MAIN_SURVEY
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
    currentScreen = PrototypeScreen.MAIN_SURVEY
  }

  private fun closeDrawerAndProfile() {
    isDrawerOpen = false
    isUserProfileOpen = false
  }

  // --- Simulated device connectivity and prototype tools ---

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

  /** Shows the active survey's map with [notice] (e.g. after the workbench added map features). */
  fun showSurveyMap(notice: String?) {
    currentScreen = PrototypeScreen.MAIN_SURVEY
    mainViewMode = MainSurveyViewMode.MAP
    activeDrawerSubView = MainDrawerSubView.NONE
    showNotice(notice)
  }

  override fun reset() {
    currentScreen = PrototypeScreen.SIGN_IN
    super.reset()
    mainViewMode = MainSurveyViewMode.MAP
    isDrawerOpen = false
    isUserProfileOpen = false
    activeDrawerSubView = MainDrawerSubView.NONE
  }

  // --- Navigation ---

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
    super.signOut()
    currentScreen = PrototypeScreen.SIGN_IN
  }

  /** Signs the user out of the application and returns to the Sign In screen. */
  override fun signOut() {
    drawerSignOut()
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
        currentScreen == PrototypeScreen.DOWNLOAD_SURVEY ||
        currentScreen == PrototypeScreen.TERMS_OF_SERVICE

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
      currentScreen == PrototypeScreen.DOWNLOAD_SURVEY -> navigateBackFromDownloadSurvey()
      currentScreen == PrototypeScreen.TERMS_OF_SERVICE -> declineTermsOfService()
    }
  }
}
