/*
 * Copyright 2026 The Collect One Authors.
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
package org.openforis.collectone.v2.web.ui.header

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.SaveStatus
import org.openforis.collectone.v2.app.domain.model.SurveyLifecycleState
import org.openforis.collectone.v2.app.ui.common.CloudAcaciaLogo
import org.openforis.collectone.v2.app.ui.common.DemoBadge
import org.openforis.collectone.v2.app.ui.common.UserAvatarProfileMenu
import org.openforis.collectone.v2.app.ui.common.platformAppEnvironment
import org.openforis.collectone.v2.app.ui.datacollection.DataCollectionActions
import org.openforis.collectone.v2.app.ui.datacollection.DataCollectionUiState
import org.openforis.collectone.v2.core.forms.ui.LocalAppBrandFontFamily
import org.openforis.collectone.v2.core.forms.ui.controls.DropdownMenuItem
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.clickable
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.web_all_surveys_menu_item
import org.openforis.collectone.v2.web.resources.web_manage_survey
import org.openforis.collectone.v2.web.resources.web_not_saved
import org.openforis.collectone.v2.web.resources.web_open_mobile_app
import org.openforis.collectone.v2.web.resources.web_recent_surveys
import org.openforis.collectone.v2.web.resources.web_saved
import org.openforis.collectone.v2.web.resources.web_saving
import org.openforis.collectone.v2.web.resources.web_switch_survey
import org.openforis.collectone.v2.web.ui.common.WebCompactLanguageSelector
import org.openforis.collectone.v2.web.ui.common.WebHeaderButton
import org.openforis.collectone.v2.web.ui.common.WebHeaderContext
import org.openforis.collectone.v2.web.ui.common.WebHeaderSupportingText
import org.openforis.collectone.v2.web.ui.common.openExternalUrl
import org.openforis.collectone.v2.web.ui.common.platformMobileWebUrl
import org.openforis.collectone.v2.web.ui.datacollection.WebCollectDataMenuButton
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyStatusBadgeMenu

/**
 * Top navigation and context toolbar for the Collect One Web dashboard: the shared [WebAppHeader]
 * showing the active survey (a [WebSurveySwitcher] dropdown) with its organization and location,
 * and "Manage survey" and "Collect data" ([WebCollectDataMenuButton]) actions.
 */
@Composable
internal fun WebTopToolbar(
  header: WebAppHeaderUiState,
  headerActions: WebAppHeaderActions,
  survey: WebSurveyToolbarUiState,
  dataCollection: DataCollectionUiState,
  dataCollectionActions: DataCollectionActions,
  onOpenSurvey: (surveyId: String) -> Unit,
  onSelectLifecycleState: (SurveyLifecycleState) -> Unit,
  onOpenSurveyEditor: () -> Unit,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier,
  onOpenFormsInSurveyEditor: () -> Unit = onOpenSurveyEditor,
  onDownloadSurveyKml: (() -> Unit)? = null,
) {
  WebAppHeader(
    uiState = header,
    headerActions = headerActions,
    onSignOut = onSignOut,
    modifier = modifier,
    context = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        WebSurveySwitcher(
          survey = survey,
          onOpenSurvey = onOpenSurvey,
          onOpenAllSurveys = headerActions::openSurveys,
        ) {
          survey.organizationName?.let { organizationName ->
            WebHeaderSupportingText(organizationName, color = MaterialTheme.colorScheme.primary)
            WebHeaderSupportingText("·")
          }
          Icon(
            imageVector = Icons.Outlined.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(12.dp),
          )
          WebHeaderSupportingText(survey.activeSurvey.location)
        }
        SurveyStatusBadgeMenu(
          lifecycleState = survey.activeSurvey.state,
          canManageSurvey = survey.canManageSurvey,
          hasUnpublishedChanges = survey.hasUnpublishedChanges,
          onSelectState = onSelectLifecycleState,
        )
      }
    },
    actions = {
      if (survey.canManageSurvey) {
        WebHeaderButton(
          text = stringResource(Res.string.web_manage_survey),
          icon = Icons.Outlined.Edit,
          onClick = onOpenSurveyEditor,
        )
      }
      // Closed and archived surveys, and surveys where the user is only a viewer, don't collect
      // data.
      if (survey.canCollectData) {
        WebCollectDataMenuButton(
          uiState = dataCollection,
          actions = dataCollectionActions,
          onManageForms = onOpenFormsInSurveyEditor.takeIf { survey.canManageSurvey },
          onDownloadSurveyKml = onDownloadSurveyKml,
        )
      }
    },
  )
}

/**
 * Header context for survey pages: the active survey's title with a dropdown arrow. Clicking it
 * lists the [WebSurveyToolbarUiState.recentSurveys] to switch to ([onOpenSurvey]) and a link to the
 * full surveys page ([onOpenAllSurveys]).
 */
@Composable
internal fun WebSurveySwitcher(
  survey: WebSurveyToolbarUiState,
  onOpenSurvey: (surveyId: String) -> Unit,
  onOpenAllSurveys: () -> Unit,
  supporting: @Composable RowScope.() -> Unit = {},
) {
  var isOpen by remember { mutableStateOf(false) }
  Box {
    Row(
      modifier =
        Modifier.clip(MaterialTheme.shapes.small)
          .clickable { isOpen = !isOpen }
          .padding(horizontal = 6.dp, vertical = 1.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      WebHeaderContext(title = survey.activeSurvey.title, supporting = supporting)
      Icon(
        imageVector = Icons.Outlined.ArrowDropDown,
        contentDescription = stringResource(Res.string.web_switch_survey),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    DropdownMenu(
      expanded = isOpen,
      onDismissRequest = { isOpen = false },
      modifier = Modifier.width(360.dp),
    ) {
      Text(
        text = stringResource(Res.string.web_recent_surveys),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
      )
      survey.recentSurveys.forEach { recentSurvey ->
        val isActive = recentSurvey.id == survey.activeSurveyId
        DropdownMenuItem(
          text = {
            Text(
              text = recentSurvey.title,
              fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          },
          leadingIcon = {
            Icon(
              imageVector = if (isActive) Icons.Outlined.Check else Icons.Outlined.Map,
              contentDescription = null,
              tint =
                if (isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          },
          onClick = {
            isOpen = false
            if (!isActive) onOpenSurvey(recentSurvey.id)
          },
        )
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      DropdownMenuItem(
        text = { Text(stringResource(Res.string.web_all_surveys_menu_item)) },
        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.List, contentDescription = null) },
        onClick = {
          isOpen = false
          onOpenAllSurveys()
        },
      )
    }
  }
}

/**
 * Small save status indicator: "Saving…" with a 14dp spinner while [saveStatus] is
 * [SaveStatus.SAVING], "Not saved" with an error icon while it is [SaveStatus.FAILED], "Saved" with
 * a check icon briefly (2.5s) after a save finishes without failing ([showsSavedConfirmation]), and
 * nothing otherwise.
 */
@Composable
fun WebSaveStatusIndicator(
  saveStatus: SaveStatus,
  modifier: Modifier = Modifier,
) {
  var showSaved by remember { mutableStateOf(false) }
  var previous by remember { mutableStateOf(saveStatus) }

  LaunchedEffect(saveStatus) {
    val finishedSaving = showsSavedConfirmation(previous, saveStatus)
    previous = saveStatus
    showSaved = finishedSaving
    if (finishedSaving) {
      delay(SAVED_CONFIRMATION_MILLIS)
      showSaved = false
    }
  }

  when {
    saveStatus == SaveStatus.SAVING ->
      SaveStatusRow(modifier, text = stringResource(Res.string.web_saving)) {
        CircularProgressIndicator(
          modifier = Modifier.size(14.dp),
          strokeWidth = 2.dp,
          color = MaterialTheme.colorScheme.primary,
        )
      }
    saveStatus == SaveStatus.FAILED ->
      SaveStatusRow(
        modifier,
        text = stringResource(Res.string.web_not_saved),
        color = MaterialTheme.colorScheme.error,
      ) {
        Icon(
          imageVector = Icons.Outlined.ErrorOutline,
          contentDescription = null,
          modifier = Modifier.size(14.dp),
          tint = MaterialTheme.colorScheme.error,
        )
      }
    showSaved ->
      SaveStatusRow(modifier, text = stringResource(Res.string.web_saved)) {
        Icon(
          imageVector = Icons.Outlined.Check,
          contentDescription = null,
          modifier = Modifier.size(14.dp),
          tint = MaterialTheme.colorScheme.primary,
        )
      }
  }
}

/** How long the header confirms "Saved" after a save finishes. */
internal const val SAVED_CONFIRMATION_MILLIS = 2_500L

/** Test tag on the non-production header button that opens the mobile web app. */
internal const val WEB_HEADER_MOBILE_LINK_TAG = "web_header_mobile_link"

/** Drop shadow of [WebAppHeader]: X=0, Y=4, Blur=12, Spread=0, #000000 at 10% alpha. */
private val HeaderShadowOffsetY = 4.dp
private val HeaderShadowBlurRadius = 12.dp
private const val HEADER_SHADOW_ALPHA = 0.1f

/** One [WebSaveStatusIndicator] state: a 14dp [icon] and its [text]. */
@Composable
private fun SaveStatusRow(
  modifier: Modifier,
  text: String,
  color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
  icon: @Composable () -> Unit,
) {
  Row(
    modifier = modifier.padding(horizontal = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    icon()
    Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
  }
}

/**
 * Header shared by every page of the Collect One Web application.
 *
 * Displays, left to right:
 * - An optional [navigationIcon] (e.g. a Close button)
 * - Brand section: Collect One logo ([CloudAcaciaLogo]) and app name
 * - Page [context], typically a [WebHeaderContext]
 * - Save status indicator ([WebSaveStatusIndicator]), when [showSaveStatus] is true
 * - Page [actions]
 * - Compact language selector, unless [showLanguageSelector] is false (e.g. when the page shows its
 *   own, like the sign-in page)
 * - User profile: clickable avatar opening the user profile card with sign out, shown only when
 *   [onSignOut] is given
 */
@Composable
internal fun WebAppHeader(
  uiState: WebAppHeaderUiState,
  headerActions: WebAppHeaderActions,
  onSignOut: (() -> Unit)?,
  modifier: Modifier = Modifier,
  onLogoClick: (() -> Unit)? = headerActions::openSurveys,
  showLanguageSelector: Boolean = true,
  showSaveStatus: Boolean = true,
  navigationIcon: (@Composable () -> Unit)? = null,
  context: @Composable () -> Unit = {},
  actions: (@Composable RowScope.() -> Unit)? = null,
) {
  val brandFont = LocalAppBrandFontFamily.current
  val mobileWebUrl = platformMobileWebUrl(platformAppEnvironment())

  Surface(
    modifier =
      modifier
        .fillMaxWidth()
        .zIndex(1f)
        .dropShadow(
          shape = RectangleShape,
          shadow =
            Shadow(
              radius = HeaderShadowBlurRadius,
              spread = 0.dp,
              color = Color.Black,
              offset = DpOffset(x = 0.dp, y = HeaderShadowOffsetY),
              alpha = HEADER_SHADOW_ALPHA,
            ),
        ),
    color = MaterialTheme.colorScheme.surfaceContainer,
  ) {
    Box(modifier = Modifier.fillMaxWidth().height(60.dp)) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .height(60.dp)
            .padding(start = if (navigationIcon != null) 4.dp else 16.dp, end = 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // Left: optional navigation icon, logo, app name, divider, and page context.
        Row(
          modifier = Modifier.weight(1f, fill = false),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          navigationIcon?.invoke()
          val brandModifier =
            if (onLogoClick != null) {
              Modifier.clip(MaterialTheme.shapes.small)
                .clickable(onClick = onLogoClick)
                .padding(horizontal = 4.dp, vertical = 2.dp)
            } else {
              Modifier
            }
          Row(
            modifier = brandModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            CloudAcaciaLogo(modifier = Modifier.size(32.dp))
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
              Text(
                text = "OPEN FORIS",
                style =
                  MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                  ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = "Collect One",
                style =
                  MaterialTheme.typography.titleMedium.copy(
                    fontFamily = brandFont,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.4.sp,
                    lineHeight = 20.sp,
                  ),
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
          VerticalDivider(
            modifier = Modifier.height(28.dp).padding(horizontal = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
          )
          context()
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Right: save status, page actions, divider, language selector, and optional avatar.
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          if (showSaveStatus) {
            WebSaveStatusIndicator(saveStatus = uiState.saveStatus)
          }

          if (actions != null) {
            actions()

            if (mobileWebUrl != null || onSignOut != null || showLanguageSelector) {
              VerticalDivider(
                modifier = Modifier.height(24.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
              )
            }
          }

          if (mobileWebUrl != null) {
            WebHeaderButton(
              text = stringResource(Res.string.web_open_mobile_app),
              icon = Icons.Outlined.Smartphone,
              onClick = { openExternalUrl(mobileWebUrl) },
              modifier = Modifier.testTag(WEB_HEADER_MOBILE_LINK_TAG),
            )
          }

          if (showLanguageSelector) {
            WebCompactLanguageSelector(
              currentLanguageCode = uiState.languageCode,
              onLanguageChange = headerActions::updateLanguage,
            )
          }

          if (onSignOut != null) {
            WebUserAvatarProfileWidget(
              uiState = uiState,
              headerActions = headerActions,
              onSignOut = onSignOut,
            )
          }
        }
      }

      DemoBadge(modifier = Modifier.align(Alignment.TopEnd))
    }
  }
}

/** Clickable user avatar icon that anchors and toggles the standard user profile card popup. */
@Composable
internal fun WebUserAvatarProfileWidget(
  uiState: WebAppHeaderUiState,
  headerActions: WebAppHeaderActions,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier,
) {
  UserAvatarProfileMenu(
    userName = uiState.userName,
    userEmail = uiState.userEmail,
    userInitials = uiState.userInitials,
    organization = uiState.userOrganizations,
    onSignOut = onSignOut,
    modifier = modifier,
    userPhotoUrl = uiState.userPhotoUrl,
    avatarSize = 36.dp,
    dropdownWidth = 320.dp,
    onOpenOrganizations = headerActions::openOrganizations,
    onOpenAdminPanel = if (uiState.isPlatformAdmin) headerActions::openAdminPanel else null,
  )
}
