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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.openforis.collectone.v2.app.domain.model.editor.FormPreviewTarget
import org.openforis.collectone.v2.app.ui.datacollection.BottomSheetSearchableListContent
import org.openforis.collectone.v2.app.ui.session.collectActiveSurveyAccess
import org.openforis.collectone.v2.app.ui.session.collectSurveyRecordsUiStates
import org.openforis.collectone.v2.app.ui.session.entityDetailsUiState
import org.openforis.collectone.v2.app.ui.session.searchableListUiState
import org.openforis.collectone.v2.app.ui.session.submissionDetailsUiState
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.web.domain.model.ImpactFormat
import org.openforis.collectone.v2.web.navigation.CreateSurveyRoute
import org.openforis.collectone.v2.web.navigation.ImpactRoute
import org.openforis.collectone.v2.web.navigation.RouteSegments
import org.openforis.collectone.v2.web.navigation.SurveyDashboardRoute
import org.openforis.collectone.v2.web.navigation.SurveyEditorRoute
import org.openforis.collectone.v2.web.navigation.SurveysRoute
import org.openforis.collectone.v2.web.navigation.WebPageNavigation
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.org_back_to_organizations
import org.openforis.collectone.v2.web.resources.org_back_to_surveys
import org.openforis.collectone.v2.web.resources.org_create_organization
import org.openforis.collectone.v2.web.resources.org_create_survey
import org.openforis.collectone.v2.web.resources.org_directory_summary
import org.openforis.collectone.v2.web.resources.org_impact
import org.openforis.collectone.v2.web.resources.org_organization_label
import org.openforis.collectone.v2.web.resources.org_organizations_title
import org.openforis.collectone.v2.web.resources.org_view_impact
import org.openforis.collectone.v2.web.resources.web_create_survey
import org.openforis.collectone.v2.web.resources.web_impact
import org.openforis.collectone.v2.web.resources.web_organization_count
import org.openforis.collectone.v2.web.resources.web_sign_in
import org.openforis.collectone.v2.web.resources.web_survey_count
import org.openforis.collectone.v2.web.resources.web_surveys
import org.openforis.collectone.v2.web.resources.web_surveys_header_summary
import org.openforis.collectone.v2.web.resources.web_terms_of_service_title
import org.openforis.collectone.v2.web.resources.wizard_impact_back_to_surveys
import org.openforis.collectone.v2.web.resources.wizard_impact_choose_organizations
import org.openforis.collectone.v2.web.resources.wizard_impact_page_title
import org.openforis.collectone.v2.web.resources.wizard_impact_published_by_followed
import org.openforis.collectone.v2.web.ui.WebAppState
import org.openforis.collectone.v2.web.ui.common.PlatformBeforeUnloadEffect
import org.openforis.collectone.v2.web.ui.common.WebHeaderButton
import org.openforis.collectone.v2.web.ui.common.WebHeaderContext
import org.openforis.collectone.v2.web.ui.common.WebHeaderSupportingText
import org.openforis.collectone.v2.web.ui.dashboard.DataCollectionExitGuard
import org.openforis.collectone.v2.web.ui.dashboard.DiscardDataCollectionFormDialog
import org.openforis.collectone.v2.web.ui.dashboard.GuardedDataCollectionActions
import org.openforis.collectone.v2.web.ui.dashboard.GuardedSearchableListActions
import org.openforis.collectone.v2.web.ui.dashboard.GuardedSurveyMapActions
import org.openforis.collectone.v2.web.ui.dashboard.GuardedWebPageNavigation
import org.openforis.collectone.v2.web.ui.dashboard.ImpactMessageSnackbar
import org.openforis.collectone.v2.web.ui.dashboard.WebDashboardActions
import org.openforis.collectone.v2.web.ui.dashboard.WebDashboardPage
import org.openforis.collectone.v2.web.ui.dashboard.WebDashboardUiState
import org.openforis.collectone.v2.web.ui.dashboard.isActivelyFillingForm
import org.openforis.collectone.v2.web.ui.datacollection.WebEntityDetailsCard
import org.openforis.collectone.v2.web.ui.datacollection.WebSubmissionDetailsCard
import org.openforis.collectone.v2.web.ui.header.WebAppHeader
import org.openforis.collectone.v2.web.ui.header.WebAppHeaderActions
import org.openforis.collectone.v2.web.ui.header.WebAppHeaderUiState
import org.openforis.collectone.v2.web.ui.header.WebSurveyToolbarUiState
import org.openforis.collectone.v2.web.ui.header.WebTopToolbar
import org.openforis.collectone.v2.web.ui.header.headerSaveStatus
import org.openforis.collectone.v2.web.ui.impact.ImpactDashboardFocus
import org.openforis.collectone.v2.web.ui.impact.ImpactDashboardPage
import org.openforis.collectone.v2.web.ui.impact.ImpactViewModel
import org.openforis.collectone.v2.web.ui.onboarding.WebSignInPage
import org.openforis.collectone.v2.web.ui.onboarding.WebTermsOfServicePage
import org.openforis.collectone.v2.web.ui.organization.OrganizationHeaderContext
import org.openforis.collectone.v2.web.ui.organization.OrganizationPage
import org.openforis.collectone.v2.web.ui.organization.OrganizationTab
import org.openforis.collectone.v2.web.ui.organization.OrganizationViewModel
import org.openforis.collectone.v2.web.ui.organization.OrganizationsPage
import org.openforis.collectone.v2.web.ui.sessionCreateSurveyWizardViewModel
import org.openforis.collectone.v2.web.ui.sessionTimeSeriesViewModel
import org.openforis.collectone.v2.web.ui.sessionViewModel
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorPage
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorSection
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorViewModel
import org.openforis.collectone.v2.web.ui.surveys.SurveyOutcomePromptViewModel
import org.openforis.collectone.v2.web.ui.surveys.WebSurveysPage
import org.openforis.collectone.v2.web.ui.wizard.CreateSurveyWizardPage

// Destinations of the web console's NavHost (see WebConsoleNavHost.kt): each gets its page's
// ViewModels from Koin (the ones several pages share with [sessionViewModel]) and the session's
// state from [WebAppState], renders the stateless page, and asks [WebPageNavigation] for page
// changes, so the rest of the console never sees ViewModels, [WebAppState], or the navigation
// library.

/** The [WebAppHeader] state of every console page, read from [web] and the session's ViewModels. */
@Composable
internal fun rememberWebAppHeaderUiState(web: WebAppState): WebAppHeaderUiState {
  val surveyEditorUiState by sessionViewModel<SurveyEditorViewModel>().uiState.collectAsState()
  val organizationUiState by sessionViewModel<OrganizationViewModel>().uiState.collectAsState()
  val outboxSaveStatus by web.dataHolder.saveStatus.collectAsState()
  val backendError by web.dataHolder.backendError.collectAsState()
  val settingsUiState by web.viewModels.settings.uiState.collectAsState()
  val onboardingUiState by web.viewModels.onboarding.uiState.collectAsState()
  val profile = onboardingUiState.profile
  return WebAppHeaderUiState(
    saveStatus =
      headerSaveStatus(
        isSavingLocally = surveyEditorUiState.isSaving || organizationUiState.isSaving,
        outbox = outboxSaveStatus,
        // A rejected edit isn't retried (canRetry is false); the header says so until dismissed.
        hasUnacknowledgedRejection = backendError?.canRetry == false,
      ),
    languageCode = settingsUiState.languageCode,
    userName = profile.displayName,
    userEmail = profile.email,
    userInitials = profile.initials,
    userPhotoUrl = profile.photoUrl,
    userOrganizations =
      collectActiveSurveyAccess(web.viewModels, web.dataHolder.appData).affiliation,
    isPlatformAdmin = organizationUiState.isPlatformAdmin,
  )
}

/**
 * The [WebAppHeader] intents of every console page, applied to [web], the session's organizations,
 * and [navigation].
 */
@Composable
internal fun rememberWebAppHeaderActions(
  web: WebAppState,
  navigation: WebPageNavigation,
): WebAppHeaderActions {
  val organization = sessionViewModel<OrganizationViewModel>()
  return remember(web, navigation, organization) {
    object : WebAppHeaderActions {
      override fun openSurveys() {
        navigation.returnTo(SurveysRoute)
      }

      override fun updateLanguage(languageCode: String) {
        web.viewModels.settings.updateLanguage(languageCode)
      }

      override fun openOrganizations() {
        organization.openOrganizations()
      }

      override fun openAdminPanel() {
        if (organization.uiState.value.isPlatformAdmin) organization.openAdminPanel()
      }
    }
  }
}

/**
 * The [WebAppHeader] of a console page, with its state and intents read from [web] and
 * [navigation]; the other parameters are the page's own parts of the header.
 */
@Composable
private fun WebConsoleHeader(
  web: WebAppState,
  navigation: WebPageNavigation,
  onSignOut: (() -> Unit)?,
  onLogoClick: (() -> Unit)? = { navigation.returnTo(SurveysRoute) },
  showLanguageSelector: Boolean = true,
  showSaveStatus: Boolean = true,
  navigationIcon: (@Composable () -> Unit)? = null,
  context: @Composable () -> Unit = {},
  actions: (@Composable RowScope.() -> Unit)? = null,
) {
  WebAppHeader(
    uiState = rememberWebAppHeaderUiState(web),
    headerActions = rememberWebAppHeaderActions(web, navigation),
    onSignOut = onSignOut,
    onLogoClick = onLogoClick,
    showLanguageSelector = showLanguageSelector,
    showSaveStatus = showSaveStatus,
    navigationIcon = navigationIcon,
    context = context,
    actions = actions,
  )
}

/**
 * The active survey as the dashboard's [WebTopToolbar] shows it, read from [web] and the session's
 * ViewModels.
 */
@Composable
internal fun rememberWebSurveyToolbarUiState(web: WebAppState): WebSurveyToolbarUiState {
  val surveyEditorUiState by sessionViewModel<SurveyEditorViewModel>().uiState.collectAsState()
  val organizationUiState by sessionViewModel<OrganizationViewModel>().uiState.collectAsState()
  val appData by web.dataHolder.appData.collectAsState()
  val access = collectActiveSurveyAccess(web.viewModels, web.dataHolder.appData)
  return WebSurveyToolbarUiState(
    activeSurveyId = appData.activeSurveyId,
    activeSurvey = access.activeSurvey,
    organizationName = access.organization?.name,
    recentSurveys = web.recentSurveys(appData),
    canManageSurvey = access.canManageSurvey(organizationUiState.isPlatformAdmin),
    canCollectData = access.canCollectData,
    hasUnpublishedChanges = surveyEditorUiState.hasUnpublishedChanges,
  )
}

/** Destination of the [WebSurveysPage]: reads its state from [web] and sends its intents back. */
@Composable
internal fun WebSurveysDestination(
  web: WebAppState,
  navigation: WebPageNavigation,
  onSignOut: () -> Unit,
) {
  // Only this page shows the outcome prompt, so it lives in the page's back-stack entry.
  val outcomePromptViewModel = koinViewModel<SurveyOutcomePromptViewModel>()
  val uiState by web.viewModels.dashboard.uiState.collectAsState()
  val outcomePrompt by outcomePromptViewModel.uiState.collectAsState()
  val hasCompletedInitialSync by web.dataHolder.hasCompletedInitialSync.collectAsState()
  WebSurveysPage(
    uiState = uiState,
    onOpenSurvey = { surveyId -> navigation.open(SurveyDashboardRoute(surveyId)) },
    onCreateSurvey = { request -> navigation.open(SurveyEditorRoute(web.createSurvey(request))) },
    onOpenCreateSurveyWizard = { navigation.open(CreateSurveyRoute()) },
    outcomePrompt = outcomePrompt,
    outcomeActions = outcomePromptViewModel,
    isLoading = !hasCompletedInitialSync && uiState.surveys.isEmpty(),
    header = { onCreateSurveyClick ->
      WebConsoleHeader(
        web = web,
        navigation = navigation,
        onSignOut = onSignOut,
        showSaveStatus = false,
        context = {
          WebHeaderContext(title = stringResource(Res.string.web_surveys)) {
            val surveyCount = uiState.surveys.size
            val organizationCount = uiState.signedInUserOrganizations.size
            WebHeaderSupportingText(
              stringResource(
                Res.string.web_surveys_header_summary,
                pluralStringResource(Res.plurals.web_survey_count, surveyCount, surveyCount),
                pluralStringResource(
                  Res.plurals.web_organization_count,
                  organizationCount,
                  organizationCount,
                ),
              )
            )
          }
        },
        actions = {
          WebHeaderButton(
            text = stringResource(Res.string.web_impact),
            icon = Icons.Outlined.Insights,
            onClick = { navigation.open(ImpactRoute()) },
          )
          WebHeaderButton(
            text = stringResource(Res.string.web_create_survey),
            icon = Icons.Outlined.Add,
            onClick = onCreateSurveyClick,
            tonal = true,
          )
        },
      )
    },
  )
}

/**
 * Destination of the [ImpactDashboardPage]: reads its state from [web] and sends its intents back.
 * The organization it focuses on is part of its URL.
 */
@Composable
internal fun ImpactDashboardDestination(
  web: WebAppState,
  navigation: WebPageNavigation,
  onSignOut: () -> Unit,
) {
  val impact = sessionViewModel<ImpactViewModel>()
  val organization = sessionViewModel<OrganizationViewModel>()
  val uiState by impact.uiState.collectAsState()
  val dashboardState by web.viewModels.dashboard.uiState.collectAsState()
  Box(modifier = Modifier.fillMaxSize()) {
    ImpactDashboardPage(
      uiState = uiState,
      actions = impact,
      sidePanelWidthDp = dashboardState.sidePanelWidthDp,
      onSidePanelWidthChange = web.viewModels.dashboard::updateSidePanelWidth,
      onFocus = { focus ->
        impact.focusDashboard(focus)
        navigation.update(
          ImpactRoute((focus as? ImpactDashboardFocus.OrganizationFocus)?.organizationId)
        )
      },
      onOpenOrganization = organization::openOrganization,
      header = { onChooseOrganizations ->
        WebConsoleHeader(
          web = web,
          navigation = navigation,
          onSignOut = onSignOut,
          showSaveStatus = false,
          navigationIcon = {
            IconButton(onClick = { navigation.returnTo(SurveysRoute) }) {
              Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(Res.string.wizard_impact_back_to_surveys),
              )
            }
          },
          context = {
            WebHeaderContext(title = stringResource(Res.string.wizard_impact_page_title)) {
              val followed = uiState.dashboard.selected.size
              WebHeaderSupportingText(
                pluralStringResource(
                  Res.plurals.wizard_impact_published_by_followed,
                  followed,
                  ImpactFormat.count(followed.toLong()),
                )
              )
            }
          },
          actions = {
            WebHeaderButton(
              text = stringResource(Res.string.wizard_impact_choose_organizations),
              icon = Icons.Outlined.Tune,
              onClick = onChooseOrganizations,
              tonal = true,
            )
          },
        )
      },
    )
    ImpactMessageSnackbar(
      message = uiState.message,
      onDismiss = impact::dismissMessage,
      modifier = Modifier.align(Alignment.BottomCenter),
    )
  }
}

/** Destination of the [WebSignInPage]: reads its state from [web] and sends its intents back. */
@Composable
internal fun WebSignInDestination(web: WebAppState, navigation: WebPageNavigation) {
  val onboarding = web.viewModels.onboarding
  val onboardingUiState by onboarding.uiState.collectAsState()
  val settingsUiState by web.viewModels.settings.uiState.collectAsState()
  val notice by web.notices.notice.collectAsState()
  WebSignInPage(
    uiState = onboardingUiState,
    actions = onboarding,
    languageCode = settingsUiState.languageCode,
    notice = notice,
    // The web console is always online.
    onSignIn = { onboarding.signInWithGoogle(ignoreOffline = true) },
    onLanguageChange = web.viewModels.settings::updateLanguage,
    header = { headerActions ->
      WebConsoleHeader(
        web = web,
        navigation = navigation,
        onSignOut = null,
        showLanguageSelector = false,
        showSaveStatus = false,
        context = { WebHeaderContext(title = stringResource(Res.string.web_sign_in)) },
        actions = headerActions,
      )
    },
  )
}

/**
 * Destination of the [WebTermsOfServicePage]: reads its state from [web] and sends its intents
 * back.
 */
@Composable
internal fun WebTermsOfServiceDestination(web: WebAppState, navigation: WebPageNavigation) {
  val onboarding = web.viewModels.onboarding
  val onboardingUiState by onboarding.uiState.collectAsState()
  val settingsUiState by web.viewModels.settings.uiState.collectAsState()
  WebTermsOfServicePage(
    uiState = onboardingUiState,
    actions = onboarding,
    languageCode = settingsUiState.languageCode,
    onLanguageChange = web.viewModels.settings::updateLanguage,
    header = { headerActions ->
      WebConsoleHeader(
        web = web,
        navigation = navigation,
        onSignOut = null,
        showLanguageSelector = false,
        showSaveStatus = false,
        context = {
          WebHeaderContext(title = stringResource(Res.string.web_terms_of_service_title))
        },
        actions = headerActions,
      )
    },
  )
}

/**
 * Destination of the [OrganizationsPage]: reads its state from [web] and sends its intents back.
 */
@Composable
internal fun OrganizationsDestination(
  web: WebAppState,
  navigation: WebPageNavigation,
  onSignOut: () -> Unit,
) {
  val organization = sessionViewModel<OrganizationViewModel>()
  val uiState by organization.uiState.collectAsState()
  val hasCompletedInitialSync by web.dataHolder.hasCompletedInitialSync.collectAsState()
  OrganizationsPage(
    uiState = uiState,
    actions = organization,
    isLoading = !hasCompletedInitialSync && uiState.organizations.isEmpty(),
    header = { onCreateOrganizationClick ->
      WebConsoleHeader(
        web = web,
        navigation = navigation,
        onSignOut = onSignOut,
        navigationIcon = {
          IconButton(onClick = { navigation.returnTo(SurveysRoute) }) {
            Icon(
              Icons.AutoMirrored.Outlined.ArrowBack,
              contentDescription = stringResource(Res.string.org_back_to_surveys),
            )
          }
        },
        context = {
          WebHeaderContext(title = stringResource(Res.string.org_organizations_title)) {
            WebHeaderSupportingText(
              stringResource(
                Res.string.org_directory_summary,
                uiState.signedInUserOrganizations.size,
                uiState.organizations.size,
              )
            )
          }
        },
        actions = {
          WebHeaderButton(
            text = stringResource(Res.string.org_impact),
            icon = Icons.Outlined.Insights,
            onClick = { navigation.open(ImpactRoute()) },
          )
          WebHeaderButton(
            text = stringResource(Res.string.org_create_organization),
            icon = Icons.Outlined.Add,
            onClick = onCreateOrganizationClick,
            tonal = true,
          )
        },
      )
    },
  )
}

/**
 * Destination of the [OrganizationPage] (also the admin panel): reads its state from [web] and
 * sends its intents back. It opens on [initialTab] (or the page's default tab), and reports the tab
 * the user picks to [onTabChange] so it can be part of the URL.
 */
@Composable
internal fun OrganizationDestination(
  web: WebAppState,
  navigation: WebPageNavigation,
  onSignOut: () -> Unit,
  initialTab: OrganizationTab? = null,
  onTabChange: (OrganizationTab) -> Unit = {},
) {
  val organizationViewModel = sessionViewModel<OrganizationViewModel>()
  val uiState by organizationViewModel.uiState.collectAsState()
  val dashboardState by web.viewModels.dashboard.uiState.collectAsState()
  val hasCompletedInitialSync by web.dataHolder.hasCompletedInitialSync.collectAsState()
  val isLoading = !hasCompletedInitialSync
  Box(modifier = Modifier.fillMaxSize()) {
    androidx.compose.runtime.CompositionLocalProvider(
      org.openforis.collectone.v2.web.ui.organization.LocalClientVersionRepository provides
        web.dataHolder.clientVersionRepository
    ) {
      OrganizationPage(
        uiState = uiState,
        actions = organizationViewModel,
        onOpenImpact = { organizationId -> navigation.open(ImpactRoute(organizationId)) },
        sidePanelWidthDp = dashboardState.sidePanelWidthDp,
        onSidePanelWidthChange = web.viewModels.dashboard::updateSidePanelWidth,
        onOpenSurvey = { surveyId -> navigation.open(SurveyDashboardRoute(surveyId)) },
        onCreateSurvey = { request ->
          navigation.open(SurveyEditorRoute(web.createSurvey(request)))
        },
        onOpenCreateSurveyWizard = { organizationId ->
          navigation.open(CreateSurveyRoute(organizationId))
        },
        surveyLibrary = dashboardState::surveyLibrary,
        isLoading = isLoading,
        initialTab = initialTab,
        onTabChange = onTabChange,
        header = { onCreateSurveyClick ->
          val organization = uiState.openOrganization
          WebConsoleHeader(
            web = web,
            navigation = navigation,
            onSignOut = onSignOut,
            navigationIcon = {
              IconButton(onClick = { organizationViewModel.openOrganizations() }) {
                Icon(
                  Icons.AutoMirrored.Outlined.ArrowBack,
                  contentDescription = stringResource(Res.string.org_back_to_organizations),
                )
              }
            },
            context = {
              if (organization != null) {
                OrganizationHeaderContext(
                  organization = organization,
                  surveyCount = uiState.surveyCountInOrganization(organization.id),
                )
              } else {
                WebHeaderContext(title = stringResource(Res.string.org_organization_label))
              }
            },
            actions = {
              if (organization != null && !organization.isSynthetic) {
                WebHeaderButton(
                  text = stringResource(Res.string.org_view_impact),
                  icon = Icons.Outlined.Insights,
                  onClick = { navigation.open(ImpactRoute(organization.id)) },
                )
              }
              // Nobody creates surveys in "All users": isMemberOf is false for it.
              if (organization != null && uiState.isMemberOf(organization)) {
                WebHeaderButton(
                  text = stringResource(Res.string.org_create_survey),
                  icon = Icons.Outlined.Add,
                  onClick = onCreateSurveyClick,
                  tonal = true,
                )
              }
            },
          )
        },
      )
    }
  }
}

/**
 * Destination of the [CreateSurveyWizardPage]: reads its state from [web] and sends its intents
 * back.
 */
@Composable
internal fun CreateSurveyWizardDestination(
  web: WebAppState,
  navigation: WebPageNavigation,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val wizard = sessionCreateSurveyWizardViewModel()
  val surveyEditor = sessionViewModel<SurveyEditorViewModel>()
  val wizardState by wizard.uiState.collectAsState()
  val editorState by surveyEditor.uiState.collectAsState()
  val mapState by web.viewModels.surveyMap.uiState.collectAsState()
  val settingsUiState by web.viewModels.settings.uiState.collectAsState()
  val hasCompletedInitialSync by web.dataHolder.hasCompletedInitialSync.collectAsState()

  CreateSurveyWizardPage(
    uiState = wizardState,
    isLibraryLoading = !hasCompletedInitialSync && wizardState.sortedPurposePacks.isEmpty(),
    actions = wizard,
    editorUiState = editorState,
    editorActions = surveyEditor,
    mapUiState = mapState,
    onToggleImagerySource = web.viewModels.surveyMap::toggleImagerySource,
    isDarkTheme = settingsUiState.isDarkTheme,
    modifier = modifier,
    header = { navigationIcon, context ->
      WebConsoleHeader(
        web = web,
        navigation = navigation,
        onSignOut = onSignOut,
        navigationIcon = navigationIcon,
        context = context,
        actions = null,
      )
    },
  )
}

/** Destination of the [WebDashboardPage]: reads its state from [web] and sends its intents back. */
@Composable
internal fun WebDashboardDestination(
  web: WebAppState,
  navigation: WebPageNavigation,
  onSignOut: () -> Unit,
) {
  val viewModels = web.viewModels
  val timeSeries = sessionTimeSeriesViewModel(viewModels.surveyMap)
  val impact = sessionViewModel<ImpactViewModel>()
  val surveyEditor = sessionViewModel<SurveyEditorViewModel>()
  val records =
    collectSurveyRecordsUiStates(
      viewModels = viewModels,
      data = web.dataHolder.appData,
      notices = web.notices,
      isAlwaysOnline = true,
    )
  val timeSeriesUiState by timeSeries.uiState.collectAsState()
  val impactUiState by impact.uiState.collectAsState()
  val hasCompletedInitialSync by web.dataHolder.hasCompletedInitialSync.collectAsState()
  val exitGuard =
    remember(viewModels) {
      DataCollectionExitGuard(
        currentState = { viewModels.dataCollection.uiState.value },
        closeForm = { viewModels.dataCollection.closeActiveFormRunner() },
      )
    }
  val guardedNavigation =
    remember(navigation, exitGuard) { GuardedWebPageNavigation(navigation, exitGuard) }
  val guardedSearchableListActions =
    remember(web.searchableListActions, exitGuard) {
      GuardedSearchableListActions(web.searchableListActions, exitGuard)
    }
  val guardedToolbarDataCollection =
    remember(viewModels.dataCollection, exitGuard) {
      GuardedDataCollectionActions(viewModels.dataCollection, exitGuard)
    }
  val actions =
    remember(web, timeSeries, impact, exitGuard) {
      WebDashboardActions(
        dashboard = viewModels.dashboard,
        dataCollection = viewModels.dataCollection,
        map = GuardedSurveyMapActions(viewModels.surveyMap, exitGuard),
        timeSeries = timeSeries,
        dismissImpactMessage = impact::dismissMessage,
        requestCloseForm = exitGuard::requestCloseForm,
      )
    }
  PlatformBeforeUnloadEffect(enabled = records.dataCollection.isActivelyFillingForm)
  val hasMapLayers = records.dashboard.mapLayers.isNotEmpty()
  val hasGoogleEarthForms = records.dashboard.hasGoogleEarthForms
  WebDashboardPage(
    uiState =
      WebDashboardUiState(
        dashboard = records.dashboard,
        dataCollection = records.dataCollection,
        map = records.map,
        timeSeries = timeSeriesUiState,
        impactMessage = impactUiState.message,
        hasCompletedInitialSync = hasCompletedInitialSync,
      ),
    actions = actions,
    onOpenInGoogleEarth =
      if (hasGoogleEarthForms) ({ openSurveyInGoogleEarth(viewModels.dashboard) }) else null,
    toolbar = {
      DashboardTopToolbar(
        web = web,
        navigation = guardedNavigation,
        dataCollection = records.dataCollection,
        dataCollectionActions = guardedToolbarDataCollection,
        hasMapLayers = hasMapLayers,
        onSelectLifecycleState = surveyEditor::setLifecycleState,
        onSignOut = { exitGuard.guardNavigation(onSignOut) },
      )
    },
    featureList = { modifier ->
      BottomSheetSearchableListContent(
        uiState = records.searchableListUiState(viewModels.surveyMap),
        actions = guardedSearchableListActions,
        modifier = modifier,
        isSidePanel = true,
      )
    },
    entityDetailsCard = { entity, onCollapse, modifier ->
      WebEntityDetailsCard(
        details = records.entityDetailsUiState(entity, onWeb = true, viewModels.surveyMap),
        actions = web.entityDetailsActions,
        dashboard = viewModels.dashboard,
        dataCollection = viewModels.dataCollection,
        surveyId = records.dashboard.activeSurveyId,
        onCollapse = onCollapse,
        modifier = modifier,
      )
    },
    submissionDetailsCard = { submission, onCollapse, modifier ->
      WebSubmissionDetailsCard(
        details = records.submissionDetailsUiState(submission, viewModels.surveyMap),
        dataCollection = viewModels.dataCollection,
        onCollapse = onCollapse,
        modifier = modifier,
      )
    },
  )
  DiscardDataCollectionFormDialog(guard = exitGuard)
}

@Composable
private fun DashboardTopToolbar(
  web: WebAppState,
  navigation: WebPageNavigation,
  dataCollection: org.openforis.collectone.v2.app.ui.datacollection.DataCollectionUiState,
  dataCollectionActions: org.openforis.collectone.v2.app.ui.datacollection.DataCollectionActions =
    web.viewModels.dataCollection,
  hasMapLayers: Boolean,
  onSelectLifecycleState:
    (org.openforis.collectone.v2.app.domain.model.SurveyLifecycleState) -> Unit,
  onSignOut: () -> Unit,
) {
  val viewModels = web.viewModels
  val surveyEditor = sessionViewModel<SurveyEditorViewModel>()
  val surveyToolbar = rememberWebSurveyToolbarUiState(web)
  WebTopToolbar(
    header = rememberWebAppHeaderUiState(web),
    headerActions = rememberWebAppHeaderActions(web, navigation),
    survey = surveyToolbar,
    dataCollection = dataCollection,
    dataCollectionActions = dataCollectionActions,
    onOpenSurvey = { surveyId -> navigation.open(SurveyDashboardRoute(surveyId)) },
    onSelectLifecycleState = onSelectLifecycleState,
    onOpenSurveyEditor = {
      navigation.open(SurveyEditorRoute(web.dataHolder.appData.value.activeSurveyId))
    },
    onSignOut = onSignOut,
    onOpenFormsInSurveyEditor = {
      val surveyId = web.dataHolder.appData.value.activeSurveyId
      val firstFormKey = surveyEditor.uiState.value.forms.firstOrNull()?.key
      if (firstFormKey != null) {
        surveyEditor.formEditor(firstFormKey).selectPreviewTarget(FormPreviewTarget.WEB)
      }
      navigation.open(
        SurveyEditorRoute(
          surveyId = surveyId,
          section = firstFormKey?.let { RouteSegments.of(SurveyEditorSection.Form(it)) },
        )
      )
    },
    onDownloadSurveyKml =
      if (hasMapLayers) ({ downloadAllLayersKml(viewModels.dashboard) }) else null,
  )
}

private fun openSurveyInGoogleEarth(
  dashboard: org.openforis.collectone.v2.app.ui.dashboard.DashboardActions
) {
  dashboard
    .googleEarthOpenUrl(
      balloonAppUrl = org.openforis.collectone.v2.web.ui.common.platformBalloonAppUrl(),
      serviceUrl = org.openforis.collectone.v2.web.ui.common.platformServiceUrl(),
    )
    ?.let { url ->
      org.openforis.collectone.v2.web.ui.common.openGoogleEarthDeepLink(earthUrl = url)
    }
}

private fun downloadAllLayersKml(
  dashboard: org.openforis.collectone.v2.app.ui.dashboard.DashboardActions
) {
  dashboard
    .exportSurveyKml(
      balloonAppUrl = org.openforis.collectone.v2.web.ui.common.platformBalloonAppUrl(),
      serviceUrl = org.openforis.collectone.v2.web.ui.common.platformServiceUrl(),
    )
    ?.let { file ->
      org.openforis.collectone.v2.web.ui.common.downloadTextFile(
        fileName = file.fileName,
        mimeType = file.mimeType,
        content = file.content,
      )
    }
}

/**
 * Destination of the [SurveyEditorPage] of the active survey: reads its state from the session's
 * [SurveyEditorViewModel] and renders the page with the shared [WebAppHeader].
 */
@Composable
internal fun SurveyEditorDestination(
  web: WebAppState,
  navigation: WebPageNavigation,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val surveyEditor = sessionViewModel<SurveyEditorViewModel>()
  val uiState by surveyEditor.uiState.collectAsState()
  val settingsUiState by web.viewModels.settings.uiState.collectAsState()
  val hasCompletedInitialSync by web.dataHolder.hasCompletedInitialSync.collectAsState()
  LaunchedEffect(uiState.surveyId) {
    if (uiState.surveyId.isNotBlank()) {
      surveyEditor.onEditorOpened()
    }
  }
  SurveyEditorPage(
    uiState = uiState,
    actions = surveyEditor,
    isDarkTheme = settingsUiState.isDarkTheme,
    isLoading = !hasCompletedInitialSync && uiState.forms.isEmpty(),
    modifier = modifier,
    header = { _, context, headerActions ->
      WebConsoleHeader(
        web = web,
        navigation = navigation,
        onSignOut = onSignOut,
        context = context,
        actions = { headerActions() },
      )
    },
  )
}
