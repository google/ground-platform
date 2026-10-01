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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.groundplatform.v2.core.forms.ui.LocalGroundBrandFontFamily

/**
 * Top navigation and context toolbar for the Ground 2.0 Web application.
 *
 * Displays:
 * - Brand section: Ground logo ([CloudAcaciaLogo]) and app name ("Ground")
 * - Survey context: Name of the currently active survey
 * - Actions: Entry point to edit the survey ("Manage survey")
 * - User profile: Clickable avatar icon opening the standard user profile card with sign out
 */
@Composable
internal fun WebTopToolbar(
  state: PrototypeAppState,
  onOpenSurveyEditor: () -> Unit = {},
  onSignOut: () -> Unit = { state.signOut() },
  modifier: Modifier = Modifier,
) {
  val brandFont = LocalGroundBrandFontFamily.current

  Surface(
    modifier = modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 2.dp,
    shadowElevation = 1.dp,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Left: Logo, App Name, Divider, and Survey Context (Title + Location). The side panel is
      // toggled from the collapse tab on its right edge, not from the toolbar.
      Row(
        modifier = Modifier.weight(1f, fill = false),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        CloudAcaciaLogo(modifier = Modifier.size(32.dp))
        Text(
          text = "Ground",
          style =
            MaterialTheme.typography.titleLarge.copy(
              fontFamily = brandFont,
              fontWeight = FontWeight.ExtraBold,
              letterSpacing = 0.5.sp,
            ),
          color = MaterialTheme.colorScheme.onSurface,
        )
        VerticalDivider(
          modifier = Modifier.height(28.dp).padding(horizontal = 4.dp),
          color = MaterialTheme.colorScheme.outlineVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
          Text(
            text = state.activeSurvey.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.LocationOn,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(12.dp),
            )
            Text(
              text = state.activeSurvey.location,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }

      Spacer(modifier = Modifier.width(16.dp))

      // Right: "Mobile prototype" link, "Manage survey" button, Divider, and Clickable Avatar Icon
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedButton(
          onClick = { state.selectWorkbenchPage(PrototypeWorkbenchPage.MOBILE_PROTOTYPE) },
          contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
          modifier = Modifier.height(36.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.Smartphone,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "Mobile prototype",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
          )
        }

        FilledTonalButton(
          onClick = onOpenSurveyEditor,
          contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
          modifier = Modifier.height(36.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "Manage survey",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
          )
        }

        VerticalDivider(
          modifier = Modifier.height(24.dp),
          color = MaterialTheme.colorScheme.outlineVariant,
        )

        WebUserAvatarProfileWidget(state = state, onSignOut = onSignOut)

        VerticalDivider(
          modifier = Modifier.height(24.dp),
          color = MaterialTheme.colorScheme.outlineVariant,
        )

        PrototypeDebugToolsButton(state = state)
      }
    }
  }
}

/** Clickable user avatar icon that anchors and toggles the standard user profile card popup. */
@Composable
internal fun WebUserAvatarProfileWidget(
  state: PrototypeAppState,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var isProfileOpen by remember { mutableStateOf(false) }

  Box(modifier = modifier) {
    // Clickable avatar icon button
    Box(
      modifier =
        Modifier.size(36.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primaryContainer)
          .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)
          .clickable { isProfileOpen = !isProfileOpen },
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = state.signedInUserInitials,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onPrimaryContainer,
      )
    }

    // Standard User Profile Card dropdown
    DropdownMenu(
      expanded = isProfileOpen,
      onDismissRequest = { isProfileOpen = false },
      modifier =
        Modifier.width(320.dp)
          .background(MaterialTheme.colorScheme.surfaceContainerLow)
          .padding(4.dp),
    ) {
      UserProfileCard(
        userName = state.signedInUserName,
        userEmail = state.signedInUserEmail,
        userInitials = state.signedInUserInitials,
        organization = state.signedInOrganization,
        onSignOut = {
          isProfileOpen = false
          onSignOut()
        },
      )
    }
  }
}

/** Standard user profile card showing avatar, user name, email, organization, and sign out link. */
@Composable
internal fun UserProfileCard(
  userName: String,
  userEmail: String,
  userInitials: String,
  organization: String,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // Profile Header: Large Avatar + Name & Email
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Box(
          modifier =
            Modifier.size(48.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primaryContainer)
              .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), CircleShape),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = userInitials,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = userName,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = userEmail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      // Organization badge
      if (organization.isNotBlank()) {
        Surface(
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
          Text(
            text = organization,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      // Sign Out Action Link
      OutlinedButton(
        onClick = onSignOut,
        modifier = Modifier.fillMaxWidth().height(40.dp),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Outlined.Logout,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
          tint = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Sign out",
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}
