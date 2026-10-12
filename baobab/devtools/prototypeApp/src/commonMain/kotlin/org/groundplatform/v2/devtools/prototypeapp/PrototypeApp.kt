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

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.groundplatform.v2.app.domain.model.DeviceFormFactor
import org.groundplatform.v2.app.domain.model.DeviceOrientation
import org.groundplatform.v2.app.domain.model.MainDrawerSubView
import org.groundplatform.v2.app.domain.model.MainSurveyViewMode
import org.groundplatform.v2.app.domain.model.PrototypeScreen
import org.groundplatform.v2.app.ui.common.DevicePreviewFrame
import org.groundplatform.v2.core.forms.ui.GroundTheme
import org.groundplatform.v2.mobile.ui.onboarding.MobileScreenHost
import org.groundplatform.v2.web.ui.dashboard.WebDashboardPage
import org.groundplatform.v2.web.ui.dashboard.WebSurveysPage
import org.groundplatform.v2.web.ui.onboarding.WebSignInPage
import org.groundplatform.v2.web.ui.organization.OrganizationPage
import org.groundplatform.v2.web.ui.organization.OrganizationsPage
import org.groundplatform.v2.web.ui.surveyeditor.SurveyEditorPage
import org.groundplatform.v2.web.ui.wizard.CreateSurveyWizardPage

/** Top-level pages of the prototype web app, addressable via the URL hash (e.g. `#dashboard`). */
enum class PrototypeWorkbenchPage(val label: String, val hash: String) {
  MOBILE_PROTOTYPE("Mobile prototype", "prototype"),
  /** Web landing page: the surveys the signed-in user can open, grouped by organization. */
  WEB_SURVEYS("Surveys", "surveys"),
  /** Full-page 6-step Survey Creation Wizard (`#create-survey`). */
  CREATE_SURVEY("Create survey", "create-survey"),
  WEB_DASHBOARD("Web dashboard", "dashboard"),
  SURVEY_EDITOR("Survey editor", "survey-editor"),
  /** The organizations the signed-in user belongs to, and listed ones they can ask to join. */
  ORGANIZATIONS("Organizations", "organizations"),
  /** One organization's surveys, members, and settings (`#organization/<id>`). */
  ORGANIZATION("Organization", "organization");

  /** Whether this page belongs to the unified web application (surveys, dashboard, editor). */
  val isWebApp: Boolean
    get() = this != MOBILE_PROTOTYPE

  /** Whether this page shows one survey, and so needs an active survey to open. */
  val needsActiveSurvey: Boolean
    get() = this == WEB_DASHBOARD || this == SURVEY_EDITOR

  companion object {
    /**
     * Top-level switcher tabs shown in the workbench top bar. The web app opens on its landing
     * page.
     */
    val topBarPages: List<PrototypeWorkbenchPage> = listOf(MOBILE_PROTOTYPE, WEB_SURVEYS)

    /** Legacy hashes kept working after pages were renamed. */
    private val aliases = mapOf("form-editor" to SURVEY_EDITOR, "web" to WEB_DASHBOARD)

    /** The page for a URL hash, ignoring any `/<id>` suffix (see [organizationIdFromHash]). */
    fun fromHash(hash: String?): PrototypeWorkbenchPage {
      val h = hash?.removePrefix("#")?.substringBefore('/')
      return entries.firstOrNull { it.hash == h } ?: aliases[h] ?: MOBILE_PROTOTYPE
    }

    /** The organization ID in an `#organization/<id>` hash, or `null` for any other hash. */
    fun organizationIdFromHash(hash: String?): String? {
      val h = hash?.removePrefix("#") ?: return null
      if (fromHash(h) != ORGANIZATION) return null
      return h.substringAfter('/', "").takeIf { it.isNotBlank() }
    }

    /** The URL hash for [page], including the organization ID for the organization page. */
    fun hashFor(page: PrototypeWorkbenchPage, organizationId: String?): String =
      if (page == ORGANIZATION && !organizationId.isNullOrBlank()) "${page.hash}/$organizationId"
      else page.hash
  }
}

/**
 * Root Compose Multiplatform Web application for `baobab/devtools/prototypeApp`.
 *
 * Renders an interactive UX design workbench that embeds a live Mobile or Tablet device preview of
 * the Ground 2.0 Compose Multiplatform UI (`Sign In` -> `Terms of Service` -> `Download survey` ->
 * `Main Survey UI`) and a unified web application: the [WebSurveysPage] landing page (surveys the
 * user can open, grouped by organization), the [WebDashboardPage] (survey map and data tables), and
 * the [SurveyEditorPage] (for designing surveys, Forms, Map layers, and Data tables).
 */
@Composable
fun PrototypeApp(
  state: PrototypeAppState = remember { PrototypeAppState() },
  initialPage: PrototypeWorkbenchPage = PrototypeWorkbenchPage.MOBILE_PROTOTYPE,
  initialOrganizationId: String? = null,
  onHashChanged: (String) -> Unit = {},
) {
  var page by remember { mutableStateOf(initialPage) }
  LaunchedEffect(initialPage, initialOrganizationId) {
    page = initialPage
    if (initialPage == PrototypeWorkbenchPage.ORGANIZATION && initialOrganizationId != null) {
      state.openOrganization(initialOrganizationId)
    } else if (
      initialPage == PrototypeWorkbenchPage.CREATE_SURVEY &&
        state.createSurveyWizard.uiState.value.workingSurveyId.isBlank()
    ) {
      state.openCreateSurveyWizard(initialOrganizationId)
    } else {
      state.selectWorkbenchPage(initialPage)
    }
  }
  state.onWorkbenchPageChanged = { targetPage ->
    page = targetPage
    onHashChanged(PrototypeWorkbenchPage.hashFor(targetPage, state.openOrganizationId))
  }
  LaunchedEffect(state.activeWorkbenchPage) {
    if (page != state.activeWorkbenchPage) {
      page = state.activeWorkbenchPage
    }
  }

  val dataCollectionUiState = state.dataCollectionUiState
  val isEntityRefMapShowing =
    dataCollectionUiState.isDataCollectionFormOpen &&
      dataCollectionUiState.isCurrentFormStepEntityRef &&
      dataCollectionUiState.entityRefSelectorViewMode == MainSurveyViewMode.MAP
  val isMobileMapShowing =
    page == PrototypeWorkbenchPage.MOBILE_PROTOTYPE &&
      state.currentScreen == PrototypeScreen.MAIN_SURVEY &&
      state.activeDrawerSubView == MainDrawerSubView.NONE &&
      (!dataCollectionUiState.isDataCollectionFormOpen ||
        dataCollectionUiState.isCurrentFormStepGeoPoint ||
        isEntityRefMapShowing)
  // The Mapbox basemap renders behind the Compose canvas, so the root surface must stay transparent
  // whenever a page shows it.
  val isMapShowing =
    isMobileMapShowing || (page == PrototypeWorkbenchPage.WEB_DASHBOARD && state.isSignedIn)

  GroundTheme(darkTheme = state.isDarkTheme) {
    Surface(
      modifier = Modifier.fillMaxSize(),
      color =
        if (isMapShowing) {
          Color.Transparent
        } else {
          MaterialTheme.colorScheme.surfaceContainerLowest
        },
    ) {
      Box(modifier = Modifier.fillMaxSize()) {
        // A survey page without an openable active survey falls back to the surveys list, and the
        // Survey editor falls back to the dashboard for people who can't manage the survey.
        val resolvedPage =
          when {
            page.needsActiveSurvey && !state.hasOpenableActiveSurvey ->
              PrototypeWorkbenchPage.WEB_SURVEYS
            page == PrototypeWorkbenchPage.SURVEY_EDITOR &&
              state.isSignedIn &&
              !state.canManageActiveSurvey -> PrototypeWorkbenchPage.WEB_DASHBOARD
            else -> page
          }
        LaunchedEffect(resolvedPage) {
          if (resolvedPage != page) state.selectWorkbenchPage(resolvedPage)
        }
        when {
          resolvedPage.isWebApp && !state.isSignedIn ->
            WebSignInPage(state = state, onSignIn = { state.signInWithGoogle() })
          resolvedPage == PrototypeWorkbenchPage.ORGANIZATIONS ->
            OrganizationsPage(
              state = state,
              onSignOut = {
                state.signOut()
                state.selectWorkbenchPage(PrototypeWorkbenchPage.WEB_SURVEYS)
              },
            )
          resolvedPage == PrototypeWorkbenchPage.ORGANIZATION ->
            OrganizationPage(
              state = state,
              onOpenSurvey = { surveyId ->
                state.openSurveyOnWeb(surveyId)
                state.selectWorkbenchPage(PrototypeWorkbenchPage.WEB_DASHBOARD)
              },
              onCreateSurvey = { request ->
                state.createSurvey(request)
                state.selectWorkbenchPage(PrototypeWorkbenchPage.SURVEY_EDITOR)
              },
              onOpenCreateSurveyWizard = { orgId -> state.openCreateSurveyWizard(orgId) },
              onSignOut = {
                state.signOut()
                state.selectWorkbenchPage(PrototypeWorkbenchPage.WEB_SURVEYS)
              },
            )
          resolvedPage == PrototypeWorkbenchPage.WEB_SURVEYS ->
            WebSurveysPage(
              state = state,
              onOpenSurvey = { surveyId ->
                state.openSurveyOnWeb(surveyId)
                state.selectWorkbenchPage(PrototypeWorkbenchPage.WEB_DASHBOARD)
              },
              onCreateSurvey = { request ->
                state.createSurvey(request)
                state.selectWorkbenchPage(PrototypeWorkbenchPage.SURVEY_EDITOR)
              },
              onOpenCreateSurveyWizard = { state.openCreateSurveyWizard() },
              onSignOut = {
                state.signOut()
                state.selectWorkbenchPage(PrototypeWorkbenchPage.WEB_SURVEYS)
              },
            )
          resolvedPage == PrototypeWorkbenchPage.CREATE_SURVEY ->
            CreateSurveyWizardPage(
              state = state,
              isDarkTheme = state.isDarkTheme,
              onSignOut = {
                state.signOut()
                state.selectWorkbenchPage(PrototypeWorkbenchPage.WEB_SURVEYS)
              },
            )
          resolvedPage == PrototypeWorkbenchPage.SURVEY_EDITOR ->
            SurveyEditorPage(state = state, isDarkTheme = state.isDarkTheme)
          resolvedPage == PrototypeWorkbenchPage.WEB_DASHBOARD ->
            WebDashboardPage(
              state = state,
              onOpenSurveyEditor = {
                state.selectWorkbenchPage(PrototypeWorkbenchPage.SURVEY_EDITOR)
              },
              onSignOut = {
                state.signOut()
                state.selectWorkbenchPage(PrototypeWorkbenchPage.WEB_SURVEYS)
              },
            )
          else -> MobilePrototypePage(state, isMobileMapShowing)
        }
      }
    }
  }
}

/** Mobile and tablet device preview stage. */
@Composable
private fun MobilePrototypePage(state: PrototypeAppState, isMapShowing: Boolean) {
  val verticalScrollState = rememberScrollState()
  val horizontalScrollState = rememberScrollState()

  Box(
    modifier =
      Modifier.fillMaxSize()
        .padding(horizontal = 16.dp, vertical = 8.dp)
        .verticalScroll(verticalScrollState)
        .horizontalScroll(horizontalScrollState),
    contentAlignment = Alignment.TopCenter,
  ) {
    MobileDevicePreviewFrame(
      deviceTitle =
        "Open Foris Collect One ${state.deviceFormFactor.label} UI • ${state.currentScreen.title}",
      isDarkTheme = state.isDarkTheme,
      isScreenTransparent = isMapShowing,
      formFactor = state.deviceFormFactor,
      orientation = state.deviceOrientation,
      onSelectFormFactor = state.workbench::selectDeviceFormFactor,
      onRotateDevice = state.workbench::rotateDevice,
      onToggleDarkTheme = state::toggleDarkTheme,
      isAirplaneMode = state.workbenchUiState.isAirplaneMode,
      onToggleAirplaneMode = state.workbench::toggleAirplaneMode,
      state = state,
    ) {
      MobileScreenHost(state)
    }
  }
}

/**
 * Realistic Mobile or Tablet hardware device frame embedding the Compose Multiplatform UI preview.
 *
 * Wraps [DevicePreviewFrame] and, when [state] is provided, adds a `Web app` shortcut to the
 * workbench's web pages.
 */
@Composable
fun MobileDevicePreviewFrame(
  deviceTitle: String,
  isDarkTheme: Boolean,
  isScreenTransparent: Boolean = false,
  formFactor: DeviceFormFactor = DeviceFormFactor.MOBILE,
  orientation: DeviceOrientation = formFactor.defaultOrientation,
  onSelectFormFactor: (DeviceFormFactor) -> Unit = {},
  onRotateDevice: () -> Unit = {},
  onToggleDarkTheme: () -> Unit = {},
  isAirplaneMode: Boolean = false,
  onToggleAirplaneMode: () -> Unit = {},
  state: PrototypeAppState? = null,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  DevicePreviewFrame(
    deviceTitle = deviceTitle,
    isDarkTheme = isDarkTheme,
    isScreenTransparent = isScreenTransparent,
    formFactor = formFactor,
    orientation = orientation,
    onSelectFormFactor = onSelectFormFactor,
    onRotateDevice = onRotateDevice,
    onToggleDarkTheme = onToggleDarkTheme,
    isAirplaneMode = isAirplaneMode,
    onToggleAirplaneMode = onToggleAirplaneMode,
    modifier = modifier,
    extraControls = {
      if (state != null) {
        FilledTonalButton(
          onClick = { state.selectWorkbenchPage(PrototypeWorkbenchPage.WEB_SURVEYS) },
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          modifier = Modifier.height(32.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.Map,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "Web app",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
          )
        }
      }
    },
    content = content,
  )
}
