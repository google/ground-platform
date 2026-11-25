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
package org.openforis.collectone.v2.mobile.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import org.openforis.collectone.v2.app.ui.dashboard.DashboardViewModel
import org.openforis.collectone.v2.app.ui.datacollection.DataCollectionFormScreen
import org.openforis.collectone.v2.app.ui.datacollection.DataCollectionViewModel
import org.openforis.collectone.v2.app.ui.map.SurveyMapViewModel
import org.openforis.collectone.v2.app.ui.onboarding.OnboardingViewModel
import org.openforis.collectone.v2.app.ui.session.DevSignInDialog
import org.openforis.collectone.v2.app.ui.settings.SettingsViewModel
import org.openforis.collectone.v2.mobile.ui.MobileAppState
import org.openforis.collectone.v2.mobile.ui.collectMainSurveyUiState
import org.openforis.collectone.v2.mobile.ui.offlinemaps.ManageOfflineMapsSubScreen
import org.openforis.collectone.v2.mobile.ui.offlinemaps.OfflineMapsViewModel
import org.openforis.collectone.v2.mobile.ui.onboarding.DownloadSurveyScreen
import org.openforis.collectone.v2.mobile.ui.onboarding.SignInScreen
import org.openforis.collectone.v2.mobile.ui.onboarding.TermsOfServiceScreen
import org.openforis.collectone.v2.mobile.ui.settings.SettingsScreen
import org.openforis.collectone.v2.mobile.ui.settings.SignInLanguageSelector
import org.openforis.collectone.v2.mobile.ui.surveys.SwitchDownloadedSurveysSubScreen
import org.openforis.collectone.v2.mobile.ui.uploads.UploadsMutationsSubScreen

/**
 * The mobile app's navigation graph: one destination per [MobileRoute], over the back stack of
 * [state]'s [MobileNavigator], which [navController] holds while this host is composed. Each
 * destination gets its ViewModels from Koin ([koinSessionViewModel]) and takes only app shell state
 * and actions (the notice, the drawer, navigation between routes) from [state]. Destinations handle
 * the system Back gesture themselves where Back does more than return to the previous route.
 * Screens switch instantly, without transitions.
 */
@Composable
internal fun MobileNavHost(
  state: MobileAppState,
  navController: NavHostController,
  modifier: Modifier = Modifier,
) {
  val startRoute = remember(navController) { state.navigator.backStack.first() }
  val sessionViewModelStoreOwner =
    checkNotNull(LocalViewModelStoreOwner.current) { "MobileNavHost needs a ViewModelStoreOwner" }
  CompositionLocalProvider(LocalSessionViewModelStoreOwner provides sessionViewModelStoreOwner) {
    MobileNavGraph(state, navController, startRoute, modifier)
  }
  // After the NavHost has set its graph, so the controller can take over the back stack.
  DisposableEffect(state.navigator, navController) {
    state.navigator.attach(navController)
    onDispose { state.navigator.detach(navController) }
  }
}

@Composable
private fun MobileNavGraph(
  state: MobileAppState,
  navController: NavHostController,
  startRoute: MobileRoute,
  modifier: Modifier,
) {
  NavHost(
    navController = navController,
    startDestination = startRoute,
    modifier = modifier,
    enterTransition = { EnterTransition.None },
    exitTransition = { ExitTransition.None },
    popEnterTransition = { EnterTransition.None },
    popExitTransition = { ExitTransition.None },
    predictivePopEnterTransition = { EnterTransition.None },
    predictivePopExitTransition = { ExitTransition.None },
    sizeTransform = null,
  ) {
    composable<MobileRoute.SignIn> { SignInDestination(state) }
    composable<MobileRoute.TermsOfService> { TermsOfServiceDestination() }
    composable<MobileRoute.DownloadSurvey> { DownloadSurveyDestination(state) }
    composable<MobileRoute.MainSurvey> { MainSurveyDestination(state) }
    composable<MobileRoute.DataCollection> { DataCollectionDestination(state) }
    composable<MobileRoute.DownloadedSurveys> { DownloadedSurveysDestination(state) }
    composable<MobileRoute.Uploads> { UploadsDestination(state) }
    composable<MobileRoute.OfflineMaps> { OfflineMapsDestination(state) }
    composable<MobileRoute.Settings> { SettingsDestination(state) }
  }
}

/** Runs [onBack] on the system Back gesture while [enabled], instead of popping the back stack. */
@Composable
private fun MobileBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
  NavigationBackHandler(
    state = rememberNavigationEventState(NavigationEventInfo.None),
    isBackEnabled = enabled,
    onBackCompleted = onBack,
  )
}

@Composable
private fun SignInDestination(state: MobileAppState) {
  val onboarding = koinSessionViewModel<OnboardingViewModel>()
  val settings = koinSessionViewModel<SettingsViewModel>()
  val onboardingUiState by onboarding.uiState.collectAsState()
  val settingsUiState by settings.uiState.collectAsState()
  val notice by state.notices.notice.collectAsState()
  SignInScreen(
    onSignIn = onboarding::signInWithGoogle,
    languageSelector = {
      SignInLanguageSelector(
        languageCode = settingsUiState.languageCode,
        onLanguageChange = settings::updateLanguage,
      )
    },
    isSigningIn = onboardingUiState.isSigningIn,
    isOnline = onboardingUiState.isOnline,
    notice = notice,
  )
  onboardingUiState.devSignIn?.let { dialog ->
    DevSignInDialog(
      state = dialog,
      onSelectRole = onboarding::selectSimulatedRole,
      onConfirm = onboarding::confirmDevSignIn,
      onDismiss = onboarding::dismissDevSignIn,
    )
  }
}

/** Back declines the Terms of Service, which signs out. */
@Composable
private fun TermsOfServiceDestination() {
  val onboarding = koinSessionViewModel<OnboardingViewModel>()
  val onboardingUiState by onboarding.uiState.collectAsState()
  MobileBackHandler(onBack = onboarding::declineTermsOfService)
  TermsOfServiceScreen(uiState = onboardingUiState, actions = onboarding)
}

/** Back returns to the survey list, or asks before signing out when reached after the terms. */
@Composable
private fun DownloadSurveyDestination(state: MobileAppState) {
  val onboarding = koinSessionViewModel<OnboardingViewModel>()
  val onboardingUiState by onboarding.uiState.collectAsState()
  val settingsUiState by koinSessionViewModel<SettingsViewModel>().uiState.collectAsState()
  val notice by state.notices.notice.collectAsState()
  MobileBackHandler(onBack = onboarding::navigateBackFromDownloadSurvey)
  DownloadSurveyScreen(
    uiState = onboardingUiState,
    actions = onboarding,
    notice = notice,
    isDarkTheme = settingsUiState.isDarkTheme,
    onNoticeShown = state.notices::clear,
  )
}

/**
 * Back leaves the area selection mode, then closes the drawer, then the open submission, then the
 * selected map feature's submissions list, then the selected map feature.
 */
@Composable
private fun MainSurveyDestination(state: MobileAppState) {
  val surveyMap = koinSessionViewModel<SurveyMapViewModel>()
  val dataCollection = koinSessionViewModel<DataCollectionViewModel>()
  val mapUiState by surveyMap.uiState.collectAsState()
  val dataCollectionUiState by dataCollection.uiState.collectAsState()
  val offlineAreaUiState by state.offlineAreaSelection.uiState.collectAsState()
  val selectedSubmissionId = dataCollectionUiState.selectedSubmissionId
  val dashboardUiState by koinSessionViewModel<DashboardViewModel>().uiState.collectAsState()
  val selectedRecordBack =
    selectedRecordBack(
      hasSelectedEntity = dataCollectionUiState.selectedEntityId != null,
      hasSelectedSubmission = selectedSubmissionId != null,
      entityDetailsPane = dashboardUiState.entityDetailsPane,
    )
  MobileBackHandler(
    enabled =
      offlineAreaUiState.isActive ||
        state.isDrawerOpen ||
        selectedSubmissionId != null ||
        dataCollectionUiState.selectedEntityId != null
  ) {
    when {
      offlineAreaUiState.isActive -> state.closeOfflineAreaSelection()
      state.isDrawerOpen -> state.updateDrawerOpen(false)
      selectedSubmissionId != null -> dataCollection.selectSubmissionDetail(null)
      selectedRecordBack == SelectedRecordBack.SHOW_PROPERTIES ->
        dataCollection.showEntityProperties()
      else -> surveyMap.selectEntity(null)
    }
  }
  MainSurveyScreen(
    uiState = state.collectMainSurveyUiState(),
    mapUiState = mapUiState,
    dataCollectionUiState = dataCollectionUiState,
    actions = state,
    mapActions = surveyMap,
    dataCollectionActions = dataCollection,
    listActions = state.searchableListActions,
    detailsActions = state.entityDetailsActions,
    offlineAreaActions = state.offlineAreaSelection,
  )
}

/**
 * The open form; Back closes it. A form can't outlive the process, so a route restored without one
 * (after Android process death or a browser reload) returns to the Main Survey.
 */
@Composable
private fun DataCollectionDestination(state: MobileAppState) {
  val dataCollection = koinSessionViewModel<DataCollectionViewModel>()
  val surveyMap = koinSessionViewModel<SurveyMapViewModel>()
  val uiState by dataCollection.uiState.collectAsState()
  if (!uiState.isDataCollectionFormOpen || uiState.activeFormWizardController == null) {
    LaunchedEffect(Unit) { state.navigator.back() }
    return
  }
  MobileBackHandler(onBack = dataCollection::closeActiveFormRunner)
  val mapUiState by surveyMap.uiState.collectAsState()
  val dashboardUiState by koinSessionViewModel<DashboardViewModel>().uiState.collectAsState()
  DataCollectionFormScreen(
    uiState = uiState,
    actions = dataCollection,
    mapUiState = mapUiState,
    mapActions = surveyMap,
    pendingIds = dashboardUiState.pendingUploadEntityIds,
    distanceAndBearingToEntity = surveyMap::distanceAndBearingToEntity,
  )
}

/**
 * A drawer page over the Main Survey; Back (like its own Back button) returns to the survey. A page
 * the app launched at with no survey open ([isOverMainSurvey] false) leaves Back to the system.
 */
@Composable
private fun MainSurveyPage(
  state: MobileAppState,
  isOverMainSurvey: Boolean = true,
  content: @Composable () -> Unit,
) {
  MobileBackHandler(enabled = isOverMainSurvey, onBack = state::closeDrawerSubView)
  Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
    content()
  }
}

/**
 * The surveys on the device, opened from the drawer over the Main Survey, or where the app launches
 * when no survey is open but some are on the device: then there is no survey to return to (or to
 * highlight as the open one).
 */
@Composable
private fun DownloadedSurveysDestination(state: MobileAppState) {
  val onboarding = koinSessionViewModel<OnboardingViewModel>()
  val onboardingUiState by onboarding.uiState.collectAsState()
  val appData by state.dataHolder.appData.collectAsState()
  val isOverMainSurvey = MobileRoute.MainSurvey in state.navigator.backStack
  MainSurveyPage(state, isOverMainSurvey) {
    SwitchDownloadedSurveysSubScreen(
      downloadedSurveys = onboardingUiState.surveys.filter { it.isDownloaded },
      activeSurveyId = if (isOverMainSurvey) appData.activeSurveyId else "",
      onOpenSurvey = onboarding::openSurvey,
      onBrowseMoreSurveys = state::openDownloadMoreSurveysScreen,
      onBack = if (isOverMainSurvey) state::closeDrawerSubView else null,
    )
  }
}

@Composable
private fun UploadsDestination(state: MobileAppState) {
  val dashboard = koinSessionViewModel<DashboardViewModel>()
  val dataCollection = koinSessionViewModel<DataCollectionViewModel>()
  val surveyMap = koinSessionViewModel<SurveyMapViewModel>()
  val dashboardUiState by dashboard.uiState.collectAsState()
  MainSurveyPage(state) {
    UploadsMutationsSubScreen(
      uiState = dashboardUiState,
      actions = dashboard,
      onBack = state::closeDrawerSubView,
      onOpenSubmission = { submissionId ->
        dataCollection.selectSubmissionDetail(submissionId)
        state.closeDrawerSubView()
      },
      onOpenEntity = { entityId ->
        surveyMap.selectEntity(entityId)
        state.closeDrawerSubView()
      },
    )
  }
}

@Composable
private fun OfflineMapsDestination(state: MobileAppState) {
  val offlineMaps = koinSessionViewModel<OfflineMapsViewModel>()
  MainSurveyPage(state) {
    ManageOfflineMapsSubScreen(
      viewModel = offlineMaps,
      onBack = state::closeDrawerSubView,
      onSaveArea = state::startOfflineAreaSelection,
      onShowOnMap = state::showOfflineAreaOnMap,
    )
  }
}

@Composable
private fun SettingsDestination(state: MobileAppState) {
  val settings = koinSessionViewModel<SettingsViewModel>()
  val settingsUiState by settings.uiState.collectAsState()
  MainSurveyPage(state) {
    SettingsScreen(
      uiState = settingsUiState,
      actions = settings,
      onBack = state::closeDrawerSubView,
    )
  }
}
