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
package org.groundplatform.v2.devtools.prototypeapp.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Clickable circular user avatar button that triggers an account menu or side panel. */
@Composable
fun UserAvatarButton(
  userName: String,
  userInitials: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  avatarSize: Dp = 32.dp,
  containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
  contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
  borderColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
) {
  val displayInitials = userInitials.ifBlank { "U" }
  Box(
    modifier =
      modifier
        .size(avatarSize)
        .clip(CircleShape)
        .background(containerColor)
        .border(1.5.dp, borderColor, CircleShape)
        .semantics {
          role = Role.Button
          contentDescription =
            if (userName.isNotBlank()) "Account menu for $userName" else "Account menu"
        }
        .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = displayInitials,
      style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
      color = contentColor,
    )
  }
}

/**
 * Clickable circular user avatar button in the top-right corner of web app bars that anchors and
 * toggles the standard [UserProfileCard] popup menu.
 */
@Composable
fun UserAvatarProfileMenu(
  userName: String,
  userEmail: String,
  userInitials: String,
  organization: String,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier,
  avatarSize: Dp = 32.dp,
  dropdownWidth: Dp = 300.dp,
  containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
  contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
  borderColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
  onOpenOrganizations: (() -> Unit)? = null,
) {
  var isProfileOpen by remember { mutableStateOf(false) }
  val displayInitials = userInitials.ifBlank { "U" }

  Box(modifier = modifier) {
    UserAvatarButton(
      userName = userName,
      userInitials = displayInitials,
      onClick = { isProfileOpen = !isProfileOpen },
      avatarSize = avatarSize,
      containerColor = containerColor,
      contentColor = contentColor,
      borderColor = borderColor,
    )

    DropdownMenu(
      expanded = isProfileOpen,
      onDismissRequest = { isProfileOpen = false },
      modifier =
        Modifier.width(dropdownWidth)
          .background(MaterialTheme.colorScheme.surfaceContainerLow)
          .padding(4.dp),
    ) {
      UserProfileCard(
        userName = userName,
        userEmail = userEmail,
        userInitials = displayInitials,
        organization = organization,
        onOpenOrganizations =
          onOpenOrganizations?.let { openOrgs ->
            {
              isProfileOpen = false
              openOrgs()
            }
          },
        onSignOut = {
          isProfileOpen = false
          onSignOut()
        },
      )
    }
  }
}

/**
 * Right-hand slide-over user account panel overlay for mobile screens, mirroring the left-hand
 * navigation drawer and staying confined within the mobile device frame.
 */
@Composable
fun UserProfileSidePanelOverlay(
  userName: String,
  userEmail: String,
  userInitials: String,
  organization: String,
  onDismissRequest: () -> Unit,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier,
  onOpenOrganizations: (() -> Unit)? = null,
) {
  Box(modifier = modifier.fillMaxSize()) {
    // Scrim backdrop
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
          .clickable(onClick = onDismissRequest)
    )

    // Right-hand M3 ModalDrawerSheet Panel
    ModalDrawerSheet(
      modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(308.dp),
      drawerShape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp),
      drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        Row(
          modifier =
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Account",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
          )
          IconButton(
            onClick = onDismissRequest,
            modifier = Modifier.size(36.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.Close,
              contentDescription = "Close Account Menu",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(20.dp),
            )
          }
        }

        UserProfileCard(
          userName = userName,
          userEmail = userEmail,
          userInitials = userInitials.ifBlank { "U" },
          organization = organization,
          onOpenOrganizations =
            onOpenOrganizations?.let { openOrgs ->
              {
                onDismissRequest()
                openOrgs()
              }
            },
          onSignOut = {
            onDismissRequest()
            onSignOut()
          },
        )
      }
    }
  }
}

/**
 * Standard user profile card showing avatar, user name, email, organizations (with an optional
 * button to open the organizations page when [onOpenOrganizations] is provided), and sign out.
 */
@Composable
fun UserProfileCard(
  userName: String,
  userEmail: String,
  userInitials: String,
  organization: String,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier,
  onOpenOrganizations: (() -> Unit)? = null,
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
            text = userInitials.ifBlank { "U" },
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

      if (onOpenOrganizations != null) {
        OutlinedButton(
          onClick = onOpenOrganizations,
          modifier = Modifier.fillMaxWidth().height(40.dp),
          shape = MaterialTheme.shapes.small,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Icon(
            imageVector = Icons.Outlined.Groups,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Manage organizations",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
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
        colors =
          ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
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
