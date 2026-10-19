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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.openforis.collectone.v2.app.di.AppDataHolder
import org.openforis.collectone.v2.app.di.requireUserDataServiceUrl
import org.openforis.collectone.v2.app.ui.common.UserDataSyncErrorBanner
import org.openforis.collectone.v2.core.forms.ui.GroundTheme
import org.openforis.collectone.v2.web.ui.WebAppState
import org.openforis.collectone.v2.web.ui.WebPage
import org.openforis.collectone.v2.web.ui.dashboard.WebDashboardPage
import org.openforis.collectone.v2.web.ui.dashboard.WebSurveysPage
import org.openforis.collectone.v2.web.ui.impact.ImpactDashboardPage
import org.openforis.collectone.v2.web.ui.onboarding.WebSignInPage
import org.openforis.collectone.v2.web.ui.organization.OrganizationPage
import org.openforis.collectone.v2.web.ui.organization.OrganizationsPage
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorPage
import org.openforis.collectone.v2.web.ui.wizard.CreateSurveyWizardPage

/**
 * Root of the Collect One web console: the [WebSurveysPage] landing page (surveys the user can
 * open, grouped by organization), organizations, the [ImpactDashboardPage] (published impact
 * numbers), the [WebDashboardPage] (survey map and data tables), the survey creation wizard, and
 * the [SurveyEditorPage].
 *
 * [initialPage] and [initialOrganizationId] come from the URL hash (see [WebPage.fromHash]), and
 * [onHashChanged] receives the new hash on every page switch.
 */
@Composable
fun WebConsoleApp(
  state: WebAppState = remember {
    WebAppState(dataHolder = AppDataHolder(userDataServiceUrl = requireUserDataServiceUrl()))
  },
  initialPage: WebPage? = null,
  initialOrganizationId: String? = null,
  onHashChanged: (String) -> Unit = {},
) {
  LaunchedEffect(initialPage, initialOrganizationId) {
    when {
      initialPage == WebPage.ADMIN -> state.openAdminPanel()
      initialPage == WebPage.ORGANIZATION && initialOrganizationId != null ->
        state.openOrganization(initialOrganizationId)
      initialPage == WebPage.IMPACT -> state.openImpactDashboard(initialOrganizationId)
      initialPage == WebPage.CREATE_SURVEY &&
        state.createSurveyWizard.uiState.value.workingSurveyId.isBlank() ->
        state.openCreateSurveyWizard(initialOrganizationId)
      initialPage != null -> state.selectPage(initialPage)
    }
  }
  state.onPageChanged = { page ->
    onHashChanged(WebPage.hashFor(page, state.hashOrganizationId(page)))
  }
  val page = state.activePage
  // The Mapbox basemap renders behind the Compose canvas, so the root surface must stay transparent
  // while the dashboard shows it.
  val isMapShowing = page == WebPage.WEB_DASHBOARD && state.isSignedIn

  GroundTheme(darkTheme = state.isDarkTheme) {
    Surface(
      modifier = Modifier.fillMaxSize(),
      color =
        if (isMapShowing) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
      Box(modifier = Modifier.fillMaxSize()) {
        WebConsolePages(state, page)
        val syncState by state.dataHolder.userDataSyncState.collectAsState()
        UserDataSyncErrorBanner(
          state = syncState,
          onRetry = state.dataHolder::retryUserDataSync,
          modifier = Modifier.align(Alignment.TopCenter),
        )
      }
    }
  }
}

/**
 * The web console's [page]. A survey page without an openable active survey falls back to the
 * surveys list, and the Survey editor falls back to the dashboard for people who can't manage the
 * survey.
 */
@Composable
internal fun WebConsolePages(web: WebAppState, page: WebPage) {
  val resolvedPage =
    when {
      page.needsActiveSurvey && !web.hasOpenableActiveSurvey -> WebPage.WEB_SURVEYS
      page == WebPage.SURVEY_EDITOR && web.isSignedIn && !web.canManageActiveSurvey ->
        WebPage.WEB_DASHBOARD
      else -> page
    }
  LaunchedEffect(resolvedPage) { if (resolvedPage != page) web.selectPage(resolvedPage) }
  val signOut = {
    web.signOut()
    web.selectPage(WebPage.WEB_SURVEYS)
  }
  when {
    !web.isSignedIn -> WebSignInPage(state = web, onSignIn = { web.signInWithGoogle() })
    resolvedPage == WebPage.ORGANIZATIONS -> OrganizationsPage(state = web, onSignOut = signOut)
    resolvedPage == WebPage.IMPACT -> ImpactDashboardPage(state = web, onSignOut = signOut)
    resolvedPage == WebPage.ADMIN || resolvedPage == WebPage.ORGANIZATION ->
      OrganizationPage(
        state = web,
        onOpenSurvey = { surveyId ->
          web.openSurveyOnWeb(surveyId)
          web.selectPage(WebPage.WEB_DASHBOARD)
        },
        onCreateSurvey = { request ->
          web.createSurvey(request)
          web.selectPage(WebPage.SURVEY_EDITOR)
        },
        onOpenCreateSurveyWizard = { orgId -> web.openCreateSurveyWizard(orgId) },
        onSignOut = signOut,
      )
    resolvedPage == WebPage.WEB_SURVEYS ->
      WebSurveysPage(
        state = web,
        onOpenSurvey = { surveyId ->
          web.openSurveyOnWeb(surveyId)
          web.selectPage(WebPage.WEB_DASHBOARD)
        },
        onCreateSurvey = { request ->
          web.createSurvey(request)
          web.selectPage(WebPage.SURVEY_EDITOR)
        },
        onOpenCreateSurveyWizard = { web.openCreateSurveyWizard() },
        onSignOut = signOut,
      )
    resolvedPage == WebPage.CREATE_SURVEY ->
      CreateSurveyWizardPage(state = web, isDarkTheme = web.isDarkTheme, onSignOut = signOut)
    resolvedPage == WebPage.SURVEY_EDITOR ->
      SurveyEditorPage(state = web, isDarkTheme = web.isDarkTheme)
    resolvedPage == WebPage.WEB_DASHBOARD ->
      WebDashboardPage(
        state = web,
        onOpenSurveyEditor = { web.selectPage(WebPage.SURVEY_EDITOR) },
        onSignOut = signOut,
      )
  }
}
