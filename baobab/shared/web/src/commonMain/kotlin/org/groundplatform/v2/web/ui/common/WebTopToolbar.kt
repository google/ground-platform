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
package org.groundplatform.v2.web.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.groundplatform.v2.app.ui.common.CloudAcaciaLogo
import org.groundplatform.v2.app.ui.common.UserAvatarProfileMenu
import org.groundplatform.v2.core.forms.ui.LocalGroundBrandFontFamily
import org.groundplatform.v2.web.ui.WebAppState
import org.groundplatform.v2.web.ui.WebPage
import org.groundplatform.v2.web.ui.dashboard.WebSurveysList
import org.groundplatform.v2.web.ui.datacollection.WebCollectDataMenuButton
import org.groundplatform.v2.web.ui.surveyeditor.SurveyStatusBadgeMenu

/**
 * Top navigation and context toolbar for the Ground 2.0 Web dashboard: the shared [WebAppHeader]
 * showing the active survey (a [WebSurveySwitcher] dropdown) with its organization and location,
 * and "Mobile prototype", "Manage survey", and "Collect data" ([WebCollectDataMenuButton]) actions.
 */
@Composable
internal fun WebTopToolbar(
  state: WebAppState,
  onOpenSurveyEditor: () -> Unit = {},
  onSignOut: () -> Unit = { state.signOut() },
  modifier: Modifier = Modifier,
) {
  WebAppHeader(
    state = state,
    onSignOut = onSignOut,
    modifier = modifier,
    context = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        WebSurveySwitcher(state = state) {
          state.activeSurveyOrganization?.let { organization ->
            WebHeaderSupportingText(organization.name, color = MaterialTheme.colorScheme.primary)
            WebHeaderSupportingText("·")
          }
          Icon(
            imageVector = Icons.Outlined.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(12.dp),
          )
          WebHeaderSupportingText(state.activeSurvey.location)
        }
        SurveyStatusBadgeMenu(
          lifecycleState = state.activeSurvey.state,
          canManageSurvey = state.canManageActiveSurvey,
          hasUnpublishedChanges = state.surveyEditor.uiState.value.hasUnpublishedChanges,
          onSelectState = state.surveyEditor::setLifecycleState,
        )
      }
    },
    actions = {
      WebMobilePrototypeButton(state)
      if (state.canManageActiveSurvey) {
        WebHeaderButton(
          text = "Manage survey",
          icon = Icons.Outlined.Edit,
          onClick = onOpenSurveyEditor,
        )
      }
      // Closed and archived surveys don't collect data.
      if (!state.activeSurvey.isClosed) {
        WebCollectDataMenuButton(
          uiState = state.dataCollectionUiState,
          actions = state.dataCollection,
        )
      }
    },
  )
}

/**
 * Header context for survey pages: the active survey's title with a dropdown arrow. Clicking it
 * lists the other surveys the user can open, grouped by organization, and a link to the full
 * surveys page.
 */
@Composable
internal fun WebSurveySwitcher(
  state: WebAppState,
  supporting: @Composable RowScope.() -> Unit = {},
) {
  var isOpen by remember { mutableStateOf(false) }
  Box {
    Row(
      modifier =
        Modifier.clip(MaterialTheme.shapes.small)
          .clickable { isOpen = !isOpen }
          .padding(horizontal = 6.dp, vertical = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      WebHeaderContext(title = state.activeSurvey.title, supporting = supporting)
      Icon(
        imageVector = Icons.Outlined.ArrowDropDown,
        contentDescription = "Switch survey",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    DropdownMenu(
      expanded = isOpen,
      onDismissRequest = { isOpen = false },
      modifier = Modifier.width(360.dp),
    ) {
      val sections = WebSurveysList.sections(state.surveys, state.organizations)
      sections.forEach { section ->
        Text(
          text = section.title,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        section.surveys.forEach { survey ->
          val isActive = survey.id == state.activeSurveyId
          DropdownMenuItem(
            text = {
              Text(
                text = survey.title,
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
              if (!isActive) state.openSurveyOnWeb(survey.id)
            },
          )
        }
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      DropdownMenuItem(
        text = { Text("All surveys…") },
        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.List, contentDescription = null) },
        onClick = {
          isOpen = false
          state.selectPage(WebPage.WEB_SURVEYS)
        },
      )
    }
  }
}

/**
 * Header shared by every page of the Ground 2.0 Web application.
 *
 * Displays, left to right:
 * - An optional [navigationIcon] (e.g. a Close button)
 * - Brand section: Ground logo ([CloudAcaciaLogo]) and app name ("Ground")
 * - Page [context], typically a [WebHeaderContext]
 * - Page [actions]
 * - User profile: clickable avatar opening the user profile card with sign out, shown only when
 *   [onSignOut] is given
 * - Prototype debug tools
 */
@Composable
internal fun WebAppHeader(
  state: WebAppState,
  modifier: Modifier = Modifier,
  onSignOut: (() -> Unit)? = { state.signOut() },
  showDebugTools: Boolean = true,
  navigationIcon: (@Composable () -> Unit)? = null,
  context: @Composable () -> Unit = {},
  actions: (@Composable RowScope.() -> Unit)? = null,
) {
  val brandFont = LocalGroundBrandFontFamily.current

  Surface(
    modifier = modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 2.dp,
    shadowElevation = 1.dp,
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .height(60.dp)
          .padding(start = if (navigationIcon != null) 4.dp else 16.dp, end = 16.dp),
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
        VerticalDivider(
          modifier = Modifier.height(28.dp).padding(horizontal = 4.dp),
          color = MaterialTheme.colorScheme.outlineVariant,
        )
        context()
      }

      Spacer(modifier = Modifier.width(16.dp))

      // Right: page actions, divider, optional avatar and divider, and debug tools.
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (actions != null) {
          actions()

          if (onSignOut != null || showDebugTools) {
            VerticalDivider(
              modifier = Modifier.height(24.dp),
              color = MaterialTheme.colorScheme.outlineVariant,
            )
          }
        }

        if (onSignOut != null) {
          WebUserAvatarProfileWidget(state = state, onSignOut = onSignOut)

          if (showDebugTools) {
            VerticalDivider(
              modifier = Modifier.height(24.dp),
              color = MaterialTheme.colorScheme.outlineVariant,
            )
          }
        }

        if (showDebugTools) {
          PrototypeDebugToolsButton(state = state)
        }
      }
    }
  }
}

/**
 * Page context for [WebAppHeader]: a [title] (and optional [titleTrailing] badge) above a row of
 * [supporting] content.
 */
@Composable
internal fun WebHeaderContext(
  title: String,
  titleTrailing: (@Composable RowScope.() -> Unit)? = null,
  supporting: @Composable RowScope.() -> Unit = {},
) {
  Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false),
      )
      titleTrailing?.invoke(this)
    }
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      content = supporting,
    )
  }
}

/** Small secondary text for the supporting row of a [WebHeaderContext]. */
@Composable
internal fun WebHeaderSupportingText(text: String, color: Color = Color.Unspecified) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelSmall,
    color = color.takeOrElse { MaterialTheme.colorScheme.onSurfaceVariant },
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
  )
}

/** Compact icon-and-label button for [WebAppHeader] actions; outlined, or filled tonal. */
@Composable
internal fun WebHeaderButton(
  text: String,
  icon: ImageVector,
  onClick: () -> Unit,
  tonal: Boolean = false,
) {
  val padding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
  val content: @Composable RowScope.() -> Unit = {
    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
    Spacer(modifier = Modifier.width(6.dp))
    Text(
      text = text,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.SemiBold,
      maxLines = 1,
    )
  }
  if (tonal) {
    FilledTonalButton(
      onClick = onClick,
      contentPadding = padding,
      modifier = Modifier.height(36.dp),
      content = content,
    )
  } else {
    OutlinedButton(
      onClick = onClick,
      contentPadding = padding,
      modifier = Modifier.height(36.dp),
      content = content,
    )
  }
}

/**
 * [WebAppHeader] action that switches the workbench to the mobile prototype. Hidden when the web
 * app runs without the workbench ([WebAppState.onShowMobilePrototype] is `null`).
 */
@Composable
internal fun WebMobilePrototypeButton(state: WebAppState) {
  val onShowMobilePrototype = state.onShowMobilePrototype ?: return
  WebHeaderButton(
    text = "Mobile prototype",
    icon = Icons.Outlined.Smartphone,
    onClick = onShowMobilePrototype,
  )
}

/** Clickable user avatar icon that anchors and toggles the standard user profile card popup. */
@Composable
internal fun WebUserAvatarProfileWidget(
  state: WebAppState,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier,
) {
  UserAvatarProfileMenu(
    userName = state.signedInUserName,
    userEmail = state.signedInUserEmail,
    userInitials = state.signedInUserInitials,
    organization = state.signedInOrganization,
    onSignOut = onSignOut,
    modifier = modifier,
    avatarSize = 36.dp,
    dropdownWidth = 320.dp,
    onOpenOrganizations = state::openOrganizations,
  )
}
