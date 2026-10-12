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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AirplanemodeActive
import androidx.compose.material.icons.outlined.AirplanemodeInactive
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Tablet
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.groundplatform.v2.core.forms.ui.GroundTheme
import org.groundplatform.v2.devtools.prototypeapp.domain.model.DeviceFormFactor
import org.groundplatform.v2.devtools.prototypeapp.domain.model.DeviceOrientation
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MainDrawerSubView
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MainSurveyViewMode
import org.groundplatform.v2.devtools.prototypeapp.domain.model.PrototypeScreen
import org.groundplatform.v2.devtools.prototypeapp.ui.common.GroundFilterChip
import org.groundplatform.v2.devtools.prototypeapp.ui.dashboard.WebDashboardPage
import org.groundplatform.v2.devtools.prototypeapp.ui.dashboard.WebSurveysPage
import org.groundplatform.v2.devtools.prototypeapp.ui.onboarding.MobileScreenHost
import org.groundplatform.v2.devtools.prototypeapp.ui.onboarding.WebSignInPage
import org.groundplatform.v2.devtools.prototypeapp.ui.organization.OrganizationPage
import org.groundplatform.v2.devtools.prototypeapp.ui.organization.OrganizationsPage
import org.groundplatform.v2.devtools.prototypeapp.ui.surveyeditor.SurveyEditorPage
import org.groundplatform.v2.devtools.prototypeapp.ui.wizard.CreateSurveyWizardPage

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
 */
@OptIn(ExperimentalLayoutApi::class)
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
  val frameWidthDp = formFactor.widthForOrientation(orientation)
  val frameHeightDp = formFactor.heightForOrientation(orientation)
  val dimensionsLabel = formFactor.dimensionsLabelForOrientation(orientation)
  val isRotated = orientation != formFactor.defaultOrientation

  val titleBlock: @Composable (Modifier) -> Unit = { titleModifier ->
    Column(modifier = titleModifier) {
      if (deviceTitle.isNotBlank()) {
        Text(
          text = deviceTitle,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Bold,
        )
      }
      Text(
        text = "Viewport: ${formFactor.label} • ${orientation.label} ($dimensionsLabel)",
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        style =
          MaterialTheme.typography.labelSmall.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant
          ),
      )
    }
  }

  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalAlignment = Alignment.Top,
  ) {
    // Outer Device Hardware Bezel (dynamically sized for Mobile vs Tablet and Portrait vs
    // Landscape)
    val outerShape = RoundedCornerShape(formFactor.outerCornerRadiusDp.dp)
    val innerShape = RoundedCornerShape(formFactor.innerCornerRadiusDp.dp)
    Box(
      modifier =
        Modifier.width(frameWidthDp.dp)
          .height(frameHeightDp.dp)
          .clip(outerShape)
          .background(Color(0xFF111827))
          .border(2.dp, Color(0xFF374151), outerShape)
          .padding(if (formFactor == DeviceFormFactor.TABLET) 12.dp else 10.dp)
    ) {
      GroundTheme(darkTheme = isDarkTheme) {
        Column(
          modifier =
            Modifier.fillMaxSize()
              .clip(innerShape)
              .background(
                if (isScreenTransparent) {
                  Color.Transparent
                } else {
                  MaterialTheme.colorScheme.surface
                }
              )
        ) {
          // Device Status Bar
          Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text =
                  if (
                    formFactor == DeviceFormFactor.TABLET ||
                      orientation == DeviceOrientation.LANDSCAPE
                  ) {
                    "09:41 • Wed Sep 19"
                  } else {
                    "09:41"
                  },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
              )
              // Camera punch-hole notch (Mobile) or slim landscape bezel sensor (Tablet)
              if (formFactor == DeviceFormFactor.MOBILE) {
                Box(
                  modifier =
                    Modifier.width(68.dp)
                      .height(11.dp)
                      .clip(CircleShape)
                      .background(Color(0xFF061B12))
                )
              } else {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF061B12)))
              }
              Text(
                text = "5G • 100%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
              )
            }
          }

          // Embedded Compose Multiplatform Screen Content
          Box(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }

          // Bottom Gesture Navigation Bar
          Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Box(
              modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
              contentAlignment = Alignment.Center,
            ) {
              Box(
                modifier =
                  Modifier.width(
                      if (
                        formFactor == DeviceFormFactor.TABLET ||
                          orientation == DeviceOrientation.LANDSCAPE
                      ) {
                        160.dp
                      } else {
                        116.dp
                      }
                    )
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outline)
              )
            }
          }
        }
      }
    }

    // Side Controls Card with Device Title + Mobile / Tablet Form Factor Toggle + Rotate +
    // Dark / Light Theme Toggle + Airplane Mode Toggle
    ElevatedCard(
      modifier = Modifier.width(210.dp),
      shape = MaterialTheme.shapes.large,
      colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
      Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        titleBlock(Modifier.fillMaxWidth())

        // Segmented Form Factor Switcher
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
          DeviceFormFactor.entries.forEachIndexed { index, factor ->
            val isSelected = formFactor == factor
            val icon =
              if (factor == DeviceFormFactor.MOBILE) {
                Icons.Outlined.Smartphone
              } else {
                Icons.Outlined.Tablet
              }
            SegmentedButton(
              selected = isSelected,
              onClick = { onSelectFormFactor(factor) },
              shape =
                SegmentedButtonDefaults.itemShape(
                  index = index,
                  count = DeviceFormFactor.entries.size,
                ),
              icon = {
                Icon(
                  imageVector = icon,
                  contentDescription = factor.label,
                  modifier = Modifier.size(14.dp),
                )
              },
              label = {
                Text(
                  text = factor.label,
                  maxLines = 1,
                  softWrap = false,
                  style = MaterialTheme.typography.labelSmall,
                )
              },
            )
          }
        }

        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
          itemVerticalAlignment = Alignment.CenterVertically,
        ) {
          // Rotate Device Widget
          GroundFilterChip(
            selected = isRotated,
            onClick = { onRotateDevice() },
            leadingIcon = {
              Icon(
                imageVector = Icons.Outlined.ScreenRotation,
                contentDescription = "Rotate device",
                modifier = Modifier.size(14.dp),
              )
            },
            label = {
              Text(
                text = "Rotate",
                maxLines = 1,
                softWrap = false,
                style = MaterialTheme.typography.labelSmall,
              )
            },
          )

          // Dark / Light mode toggle for the mobile app preview
          GroundFilterChip(
            selected = isDarkTheme,
            onClick = { onToggleDarkTheme() },
            leadingIcon = {
              Icon(
                imageVector =
                  if (isDarkTheme) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                contentDescription =
                  if (isDarkTheme) "Switch to light mode" else "Switch to dark mode",
                modifier = Modifier.size(14.dp),
              )
            },
            label = {
              Text(
                text = if (isDarkTheme) "Dark" else "Light",
                maxLines = 1,
                softWrap = false,
                style = MaterialTheme.typography.labelSmall,
              )
            },
          )

          // Airplane mode (offline simulation) toggle
          GroundFilterChip(
            selected = isAirplaneMode,
            onClick = { onToggleAirplaneMode() },
            leadingIcon = {
              Icon(
                imageVector =
                  if (isAirplaneMode) {
                    Icons.Outlined.AirplanemodeActive
                  } else {
                    Icons.Outlined.AirplanemodeInactive
                  },
                contentDescription =
                  if (isAirplaneMode) "Disable airplane mode" else "Enable airplane mode",
                modifier = Modifier.size(14.dp),
              )
            },
            label = {
              Text(
                text = "Airplane mode",
                maxLines = 1,
                softWrap = false,
                style = MaterialTheme.typography.labelSmall,
              )
            },
          )
        }

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
      }
    }
  }
}
