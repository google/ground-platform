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

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.ui.session.collectActiveSurveyAccess
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.web.navigation.AdminRoute
import org.openforis.collectone.v2.web.navigation.CreateSurveyRoute
import org.openforis.collectone.v2.web.navigation.ImpactRoute
import org.openforis.collectone.v2.web.navigation.OrganizationRoute
import org.openforis.collectone.v2.web.navigation.OrganizationsRoute
import org.openforis.collectone.v2.web.navigation.RouteDecision
import org.openforis.collectone.v2.web.navigation.RouteSegments
import org.openforis.collectone.v2.web.navigation.SurveyDashboardRoute
import org.openforis.collectone.v2.web.navigation.SurveyEditorRoute
import org.openforis.collectone.v2.web.navigation.SurveysRoute
import org.openforis.collectone.v2.web.navigation.WebNavigator
import org.openforis.collectone.v2.web.navigation.WebRoute
import org.openforis.collectone.v2.web.navigation.WebRouteAccess
import org.openforis.collectone.v2.web.navigation.WebRouteGuard
import org.openforis.collectone.v2.web.ui.WebAppState
import org.openforis.collectone.v2.web.ui.impact.ImpactViewModel
import org.openforis.collectone.v2.web.ui.organization.OrganizationTab
import org.openforis.collectone.v2.web.ui.organization.OrganizationViewModel
import org.openforis.collectone.v2.web.ui.sessionCreateSurveyWizardViewModel
import org.openforis.collectone.v2.web.ui.sessionViewModel
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorSection
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorUiState
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorViewModel

/**
 * The web console's pages: one `NavHost` destination per [WebRoute], starting at [startRoute].
 *
 * Every destination shows the sign-in page while nobody is signed in (keeping its URL, so the page
 * opens once they sign in) and asks [WebRouteGuard] whether the signed-in user can see it. Pages
 * switch without animation. [onSignOut] signs out and returns to the surveys list.
 */
@Composable
internal fun WebConsoleNavHost(
  web: WebAppState,
  navController: NavHostController,
  navigator: WebNavigator,
  startRoute: WebRoute,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier,
) {
  NavHost(
    navController = navController,
    startDestination = startRoute,
    modifier = modifier,
    enterTransition = { EnterTransition.None },
    exitTransition = { ExitTransition.None },
    popEnterTransition = { EnterTransition.None },
    popExitTransition = { ExitTransition.None },
  ) {
    surveyDestinations(web, navigator, onSignOut)
    organizationDestinations(web, navigator, onSignOut)
    composable<ImpactRoute> { entry ->
      val route = navigator.routeOf<ImpactRoute>(entry)
      ConsolePage(web, navigator, route) {
        val impact = sessionViewModel<ImpactViewModel>()
        // Opening the page on an organization adds it to the ones the user follows.
        LaunchedEffect(Unit) { impact.openDashboard(route.organizationId) }
        ImpactDashboardDestination(web = web, navigation = navigator, onSignOut = onSignOut)
      }
    }
  }
}

/** The surveys list, a survey's dashboard and Survey editor, and the Survey Creation Wizard. */
private fun NavGraphBuilder.surveyDestinations(
  web: WebAppState,
  navigator: WebNavigator,
  onSignOut: () -> Unit,
) {
  composable<SurveysRoute> {
    ConsolePage(web, navigator, SurveysRoute) {
      WebSurveysDestination(web = web, navigation = navigator, onSignOut = onSignOut)
    }
  }
  composable<SurveyDashboardRoute> { entry ->
    val route = navigator.routeOf<SurveyDashboardRoute>(entry)
    OpenSurveyOfRoute(web, route.surveyId)
    ConsolePage(web, navigator, route) {
      WebDashboardDestination(web = web, navigation = navigator, onSignOut = onSignOut)
    }
  }
  composable<SurveyEditorRoute> { entry ->
    val route = navigator.routeOf<SurveyEditorRoute>(entry)
    OpenSurveyOfRoute(web, route.surveyId)
    ConsolePage(web, navigator, route) {
      SyncEditorSectionWithRoute(navigator, route)
      SurveyEditorDestination(web = web, navigation = navigator, onSignOut = onSignOut)
    }
  }
  composable<CreateSurveyRoute> { entry ->
    val route = navigator.routeOf<CreateSurveyRoute>(entry)
    ConsolePage(web, navigator, route) {
      SyncWizardWithRoute(navigator, route)
      CreateSurveyWizardDestination(web = web, navigation = navigator, onSignOut = onSignOut)
    }
  }
}

/** The organizations list, an organization's page, and the admin panel. */
private fun NavGraphBuilder.organizationDestinations(
  web: WebAppState,
  navigator: WebNavigator,
  onSignOut: () -> Unit,
) {
  composable<OrganizationsRoute> {
    ConsolePage(web, navigator, OrganizationsRoute) {
      OrganizationsDestination(web = web, navigation = navigator, onSignOut = onSignOut)
    }
  }
  composable<OrganizationRoute> { entry ->
    val route = navigator.routeOf<OrganizationRoute>(entry)
    ConsolePage(web, navigator, route) {
      val organization = sessionViewModel<OrganizationViewModel>()
      LaunchedEffect(route.organizationId) {
        if (organization.uiState.value.openOrganizationId != route.organizationId) {
          organization.openOrganization(route.organizationId)
        }
      }
      OrganizationDestination(
        web = web,
        navigation = navigator,
        onSignOut = onSignOut,
        initialTab = route.tab?.let(RouteSegments::organizationTab),
        onTabChange = { tab ->
          navigator.update(route.copy(tab = RouteSegments.of(tab, OrganizationTab.DETAILS)))
        },
      )
    }
  }
  composable<AdminRoute> { entry ->
    val route = navigator.routeOf<AdminRoute>(entry)
    ConsolePage(web, navigator, route) {
      val organization = sessionViewModel<OrganizationViewModel>()
      LaunchedEffect(Unit) {
        if (organization.uiState.value.openOrganizationId != Organization.ALL_USERS_ID) {
          organization.openAdminPanel()
        }
      }
      OrganizationDestination(
        web = web,
        navigation = navigator,
        onSignOut = onSignOut,
        initialTab = route.tab?.let(RouteSegments::organizationTab),
        onTabChange = { tab ->
          navigator.update(route.copy(tab = RouteSegments.of(tab, OrganizationTab.MEMBERS)))
        },
      )
    }
  }
}

/** The [WebRoute] of [entry], including any in-place [WebNavigator.update] made since it opened. */
@Composable
private inline fun <reified T : WebRoute> WebNavigator.routeOf(entry: NavBackStackEntry): T {
  val updates by updatedRoutes.collectAsState()
  return (updates[entry.id] as? T) ?: entry.toRoute()
}

/**
 * A console page at [route]: the sign-in page while nobody is signed in, the Terms of Service until
 * the signed-in user accepts them (after every sign-in), a progress indicator while [WebRouteGuard]
 * waits for data, a redirect when the user can't see it, and else [content].
 */
@Composable
private fun ConsolePage(
  web: WebAppState,
  navigator: WebNavigator,
  route: WebRoute,
  content: @Composable () -> Unit,
) {
  val onboardingUiState by web.viewModels.onboarding.uiState.collectAsState()
  if (!onboardingUiState.isSignedIn) {
    WebSignInDestination(web = web, navigation = navigator)
    return
  }
  if (!onboardingUiState.hasAcceptedTerms) {
    WebTermsOfServiceDestination(web = web, navigation = navigator)
    return
  }
  var hasBeenShown by remember { mutableStateOf(false) }
  val decision = WebRouteGuard.decide(route, rememberWebRouteAccess(web), hasBeenShown)
  LaunchedEffect(decision) {
    if (decision is RouteDecision.Redirect) navigator.redirect(decision.route, decision.notice)
  }
  if (decision == RouteDecision.Show) {
    SideEffect { hasBeenShown = true }
    content()
  } else {
    Surface(
      modifier = Modifier.fillMaxSize(),
      color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
      Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
  }
}

/** What [web]'s signed-in user can open, for [WebRouteGuard]. */
@Composable
private fun rememberWebRouteAccess(web: WebAppState): WebRouteAccess {
  val hasCompletedInitialSync by web.dataHolder.hasCompletedInitialSync.collectAsState()
  val organizationUiState by sessionViewModel<OrganizationViewModel>().uiState.collectAsState()
  val appData by web.dataHolder.appData.collectAsState()
  val access = collectActiveSurveyAccess(web.viewModels, web.dataHolder.appData)
  return WebRouteAccess(
    hasCompletedInitialSync = hasCompletedInitialSync,
    surveyIds = appData.surveys.mapTo(mutableSetOf()) { it.id },
    activeSurveyId = appData.activeSurveyId,
    canManageActiveSurvey = access.canManageSurvey(organizationUiState.isPlatformAdmin),
    isPlatformAdmin = organizationUiState.isPlatformAdmin,
    canOpenOrganization =
      organizationUiState.organizations.associate { organization ->
        organization.id to (organization.isListed || organizationUiState.isMemberOf(organization))
      },
  )
}

/**
 * Makes [surveyId] the active survey once it's known, and again whenever another one becomes it.
 */
@Composable
private fun OpenSurveyOfRoute(web: WebAppState, surveyId: String) {
  val appData by web.dataHolder.appData.collectAsState()
  val isKnownSurvey = appData.surveys.any { it.id == surveyId }
  val activeSurveyId = appData.activeSurveyId
  LaunchedEffect(surveyId, isKnownSurvey, activeSurveyId) {
    if (isKnownSurvey && activeSurveyId != surveyId) web.openSurveyOnWeb(surveyId)
  }
}

/**
 * Opens the Survey editor on [route]'s section (when the survey has it), then keeps the route on
 * the section the organizer picks.
 */
@Composable
private fun SyncEditorSectionWithRoute(navigator: WebNavigator, route: SurveyEditorRoute) {
  val editor = sessionViewModel<SurveyEditorViewModel>()
  val currentRoute by rememberUpdatedState(route)
  LaunchedEffect(Unit) {
    val routeSection = route.section?.let(RouteSegments::editorSection)
    if (routeSection != null && editor.uiState.value.has(routeSection)) editor.select(routeSection)
    editor.uiState
      .map { it.section }
      .distinctUntilChanged()
      .collect { section ->
        navigator.update(currentRoute.copy(section = RouteSegments.of(section)))
      }
  }
}

/** Whether the survey being edited has [section]. */
private fun SurveyEditorUiState.has(section: SurveyEditorSection): Boolean =
  when (section) {
    is SurveyEditorSection.Form -> forms.any { it.key == section.key }
    is SurveyEditorSection.Dataset -> datasets.any { it.key == section.key }
    SurveyEditorSection.Details,
    SurveyEditorSection.Sharing -> true
  }

/**
 * Starts a Survey Creation Wizard session when the page opens (offering to continue an unsaved
 * one), then keeps the route on the wizard's current step.
 */
@Composable
private fun SyncWizardWithRoute(navigator: WebNavigator, route: CreateSurveyRoute) {
  val wizard = sessionCreateSurveyWizardViewModel()
  val currentRoute by rememberUpdatedState(route)
  LaunchedEffect(Unit) {
    wizard.startWizard(route.organizationId)
    wizard.uiState
      .map { it.currentStep }
      .distinctUntilChanged()
      .collect { step -> navigator.update(currentRoute.copy(step = RouteSegments.of(step))) }
  }
}
