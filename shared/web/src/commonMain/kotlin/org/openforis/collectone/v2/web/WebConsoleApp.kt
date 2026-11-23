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
package org.openforis.collectone.v2.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.di.AppDataHolder
import org.openforis.collectone.v2.app.di.requireUserDataServiceUrl
import org.openforis.collectone.v2.app.ui.common.DemoAttributionFooterBar
import org.openforis.collectone.v2.app.ui.common.ProvideAppLocale
import org.openforis.collectone.v2.app.ui.common.currentAppEnvironment
import org.openforis.collectone.v2.app.ui.session.BackendErrorBanner
import org.openforis.collectone.v2.app.ui.session.UserDataSyncErrorBanner
import org.openforis.collectone.v2.core.forms.ui.AppTheme
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.web.navigation.BrowserHistory
import org.openforis.collectone.v2.web.navigation.BrowserHistoryBinding
import org.openforis.collectone.v2.web.navigation.MemoryBrowserHistory
import org.openforis.collectone.v2.web.navigation.NavigationNotice
import org.openforis.collectone.v2.web.navigation.OrganizationRoute
import org.openforis.collectone.v2.web.navigation.SurveyDashboardRoute
import org.openforis.collectone.v2.web.navigation.SurveyEditorRoute
import org.openforis.collectone.v2.web.navigation.SurveysRoute
import org.openforis.collectone.v2.web.navigation.WebLink
import org.openforis.collectone.v2.web.navigation.WebNavigator
import org.openforis.collectone.v2.web.navigation.WebRoute
import org.openforis.collectone.v2.web.navigation.WebUrls
import org.openforis.collectone.v2.web.navigation.navigate
import org.openforis.collectone.v2.web.navigation.toWebRoute
import org.openforis.collectone.v2.web.navigation.webNavigationRequests
import org.openforis.collectone.v2.web.ui.InviteDeepLink
import org.openforis.collectone.v2.web.ui.ProvideWebSession
import org.openforis.collectone.v2.web.ui.WebAppState
import org.openforis.collectone.v2.web.ui.common.WebDevToolsDialog
import org.openforis.collectone.v2.web.ui.common.platformBackendDebugUrl
import org.openforis.collectone.v2.web.ui.dashboard.ImpactMessageSnackbar
import org.openforis.collectone.v2.web.ui.organization.OrganizationEvent
import org.openforis.collectone.v2.web.ui.organization.OrganizationViewModel
import org.openforis.collectone.v2.web.ui.sessionCreateSurveyWizardViewModel
import org.openforis.collectone.v2.web.ui.sessionViewModel
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorViewModel
import org.openforis.collectone.v2.web.ui.text.editorDefaultNames

/**
 * Root of the Collect One web console: the pages of [WebConsoleNavHost] (the surveys list,
 * organizations, Impact, a survey's dashboard and Survey editor, and the survey creation wizard),
 * each at its own URL of [browserHistory] (see [WebUrls]).
 *
 * The first page is the one the URL names (or, after a reload, the back stack stored with the
 * history entry). A link to a page that doesn't exist opens the surveys list with a notice, and an
 * invitation link opens its survey or organization once the user signed in and accepted it.
 */
@Composable
fun WebConsoleApp(
  state: WebAppState = remember {
    WebAppState(dataHolder = AppDataHolder(userDataServiceUrl = requireUserDataServiceUrl()))
  },
  browserHistory: BrowserHistory = remember { MemoryBrowserHistory() },
) {
  ProvideWebSession(state) { WebConsoleContent(state, browserHistory) }
}

@Composable
private fun WebConsoleContent(state: WebAppState, browserHistory: BrowserHistory) {
  val organization = sessionViewModel<OrganizationViewModel>()
  val surveyEditor = sessionViewModel<SurveyEditorViewModel>()
  val createSurveyWizard = sessionCreateSurveyWizardViewModel()
  val initialLink =
    remember(browserHistory) { WebUrls.parse(browserHistory.path, browserHistory.hash) }
  val initialBackStack =
    remember(browserHistory) { BrowserHistoryBinding.initialBackStack(browserHistory, initialLink) }
  val navController = rememberNavController()
  val navigator = remember(navController) { WebNavigator(navController) }
  val currentEntry by navController.currentBackStackEntryAsState()
  val routeUpdates by navigator.updatedRoutes.collectAsState()
  val currentRoute = currentEntry?.let { routeUpdates[it.id] ?: it.toWebRoute() }

  LaunchedEffect(navigator, browserHistory) {
    // Invitations are redeemed in the background; their pages open here, on the main thread.
    val redeemedInvites = Channel<WebRoute>(Channel.UNLIMITED)
    fun openLink(link: WebLink) {
      when (link) {
        is WebLink.Page -> navigator.open(link.route)
        is WebLink.Invite ->
          state.redeemInviteDeepLink(link.invite, organization) {
            redeemedInvites.trySend(link.invite.route)
          }
        WebLink.Unknown -> navigator.redirect(SurveysRoute, NavigationNotice.LINK_UNAVAILABLE)
      }
    }
    // Navigating needs the graph the NavHost sets when it first composes. In the browser that
    // happens after this effect starts, and navigating before it fails.
    navController.currentBackStack.first { it.isNotEmpty() }
    navigator.restore(initialBackStack)
    if (initialLink !is WebLink.Page) openLink(initialLink)
    launch { for (route in redeemedInvites) navigator.open(route) }
    launch {
      webNavigationRequests(
          organizationEvents = organization.events,
          surveyEditorEvents = surveyEditor.events,
          createSurveyWizardEvents = createSurveyWizard.events,
          activeSurveyId = { state.dataHolder.appData.value.activeSurveyId },
          currentRoute = { navigator.currentRoute },
        )
        .collect(navigator::navigate)
    }
    BrowserHistoryBinding(browserHistory, navigator)
      .bind(keepInitialUrl = initialLink is WebLink.Invite, onLinkOpened = ::openLink)
  }
  SessionEffects(state, organization, surveyEditor, currentRoute)
  // The Mapbox basemap renders behind the Compose canvas, so the root surface must stay transparent
  // while the dashboard shows it.
  val onboardingUiState by state.viewModels.onboarding.uiState.collectAsState()
  val isMapShowing =
    currentRoute is SurveyDashboardRoute &&
      onboardingUiState.isSignedIn &&
      onboardingUiState.hasAcceptedTerms

  val settingsUiState by state.viewModels.settings.uiState.collectAsState()

  ProvideAppLocale(settingsUiState.languageCode) {
    // New questions and datasets are named in the language the console shows.
    val defaultNames = editorDefaultNames()
    SideEffect { surveyEditor.useDefaultNames(defaultNames) }
    AppTheme(darkTheme = settingsUiState.isDarkTheme) {
      Surface(
        modifier = Modifier.fillMaxSize(),
        color =
          if (isMapShowing) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLowest,
      ) {
        Box(modifier = Modifier.fillMaxSize()) {
          Column(modifier = Modifier.fillMaxSize()) {
            WebConsoleNavHost(
              web = state,
              navController = navController,
              navigator = navigator,
              startRoute = initialBackStack.first(),
              onSignOut = {
                state.signOut()
                navigator.resetTo(SurveysRoute)
              },
              modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            WebFooterWithDevTools(
              state = state,
              isDarkTheme = settingsUiState.isDarkTheme,
            )
          }
          SyncErrorBanners(state.dataHolder, Modifier.align(Alignment.TopCenter))
          val notice by navigator.notice.collectAsState()
          ImpactMessageSnackbar(
            message = notice?.let { stringResource(it.message) },
            onDismiss = navigator::dismissNotice,
            modifier = Modifier.align(Alignment.BottomCenter),
          )
        }
      }
    }
  }
}

/**
 * Non-production footer bar and Developer Tools dialog for the web console. Hidden in production
 * builds ([AppEnvironment.PROD]).
 */
@Composable
private fun WebFooterWithDevTools(state: WebAppState, isDarkTheme: Boolean) {
  val environment = currentAppEnvironment()
  var isDevToolsOpen by remember { mutableStateOf(false) }
  DemoAttributionFooterBar(
    environment = environment,
    onOpenDevTools = { isDevToolsOpen = true },
  )
  if (isDevToolsOpen && environment.isNonProduction) {
    WebDevToolsDialog(
      environment = environment,
      isDarkTheme = isDarkTheme,
      backendBaseUrl = platformBackendDebugUrl(state.dataHolder.userDataServiceUrl),
      onToggleDarkTheme = state.viewModels.settings::toggleDarkTheme,
      onDismiss = { isDevToolsOpen = false },
    )
  }
}

/** The banners of [dataHolder]'s sync and backend errors, each with its retry. */
@Composable
private fun SyncErrorBanners(dataHolder: AppDataHolder, modifier: Modifier = Modifier) {
  val syncState by dataHolder.userDataSyncState.collectAsState()
  val backendError by dataHolder.backendError.collectAsState()
  Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    UserDataSyncErrorBanner(state = syncState, onRetry = dataHolder::retryUserDataSync)
    BackendErrorBanner(
      error = backendError,
      onRetry = dataHolder::retryBackendSync,
      onDismiss = dataHolder::dismissBackendError,
    )
  }
}

/**
 * What the session does as pages change and its ViewModels report outcomes, with [currentRoute] the
 * page that shows.
 */
@Composable
private fun SessionEffects(
  state: WebAppState,
  organization: OrganizationViewModel,
  surveyEditor: SurveyEditorViewModel,
  currentRoute: WebRoute?,
) {
  // Turns off an imagery source on the survey map when its organization removes it.
  LaunchedEffect(organization) {
    organization.events.filterIsInstance<OrganizationEvent.ImagerySourceRemoved>().collect {
      state.onImagerySourceRemoved(it.sourceId)
    }
  }
  // Poll for collaborators' changes more often while a survey's dashboard or editor is open.
  val isCollaborativePage =
    currentRoute is SurveyDashboardRoute || currentRoute is SurveyEditorRoute
  LaunchedEffect(isCollaborativePage) {
    state.dataHolder.setCollaborativeView(isCollaborativePage)
  }
  val isSurveyEditorOpen = currentRoute is SurveyEditorRoute
  LaunchedEffect(isSurveyEditorOpen) {
    if (isSurveyEditorOpen) surveyEditor.onEditorOpened()
  }
}

/** The page an accepted invitation opens. */
private val InviteDeepLink.route: WebRoute
  get() =
    when (kind) {
      InviteDeepLink.Kind.SURVEY -> SurveyDashboardRoute(targetId)
      InviteDeepLink.Kind.ORGANIZATION -> OrganizationRoute(targetId)
    }
