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
package org.openforis.collectone.v2.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_about
import org.openforis.collectone.v2.app.resources.app_account
import org.openforis.collectone.v2.app.resources.app_account_menu
import org.openforis.collectone.v2.app.resources.app_account_menu_for_user
import org.openforis.collectone.v2.app.resources.app_admin_panel
import org.openforis.collectone.v2.app.resources.app_close_account_menu
import org.openforis.collectone.v2.app.resources.app_manage_organizations
import org.openforis.collectone.v2.app.resources.app_sign_out
import org.openforis.collectone.v2.core.forms.ui.controls.Card
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.backdropClickable
import org.openforis.collectone.v2.core.forms.ui.controls.clickable

/** Clickable circular user avatar button that triggers an account menu or side panel. */
@Composable
fun UserAvatarButton(
  userName: String,
  userInitials: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  userPhotoUrl: String? = null,
  avatarSize: Dp = 32.dp,
  containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
  contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
  borderColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
) {
  val displayInitials = userInitials.ifBlank { "U" }
  val avatarBitmap = rememberProfileImageBitmap(userPhotoUrl)
  val accountMenuDescription =
    if (userName.isNotBlank()) stringResource(Res.string.app_account_menu_for_user, userName)
    else stringResource(Res.string.app_account_menu)
  Box(
    modifier =
      modifier
        .size(avatarSize)
        .clip(CircleShape)
        .background(containerColor)
        .border(1.5.dp, borderColor, CircleShape)
        .semantics {
          role = Role.Button
          contentDescription = accountMenuDescription
        }
        .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    if (avatarBitmap != null) {
      Image(
        bitmap = avatarBitmap,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().clip(CircleShape),
      )
    } else {
      Text(
        text = displayInitials,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = contentColor,
      )
    }
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
  userPhotoUrl: String? = null,
  avatarSize: Dp = 32.dp,
  dropdownWidth: Dp = 300.dp,
  containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
  contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
  borderColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
  onOpenOrganizations: (() -> Unit)? = null,
  onOpenAdminPanel: (() -> Unit)? = null,
  onOpenAbout: (() -> Unit)? = null,
) {
  var isProfileOpen by remember { mutableStateOf(false) }
  val displayInitials = userInitials.ifBlank { "U" }

  Box(modifier = modifier) {
    UserAvatarButton(
      userName = userName,
      userInitials = displayInitials,
      onClick = { isProfileOpen = !isProfileOpen },
      userPhotoUrl = userPhotoUrl,
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
        userPhotoUrl = userPhotoUrl,
        onOpenOrganizations =
          onOpenOrganizations?.let { openOrgs ->
            {
              isProfileOpen = false
              openOrgs()
            }
          },
        onOpenAdminPanel =
          onOpenAdminPanel?.let { openAdmin ->
            {
              isProfileOpen = false
              openAdmin()
            }
          },
        onOpenAbout =
          onOpenAbout?.let { openAbout ->
            {
              isProfileOpen = false
              openAbout()
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
  userPhotoUrl: String? = null,
  onOpenOrganizations: (() -> Unit)? = null,
  onOpenAdminPanel: (() -> Unit)? = null,
) {
  Box(modifier = modifier.fillMaxSize()) {
    // Scrim backdrop
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
          .backdropClickable(onClick = onDismissRequest)
    )

    // Right-hand M3 ModalDrawerSheet Panel
    val topInset = LocalMobileTopInset.current
    ModalDrawerSheet(
      modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(308.dp),
      drawerShape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp),
      drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .padding(start = 18.dp, end = 10.dp, top = topInset + 12.dp, bottom = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = stringResource(Res.string.app_account),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
          )
          IconButton(
            onClick = onDismissRequest,
            modifier = Modifier.size(36.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.Close,
              contentDescription = stringResource(Res.string.app_close_account_menu),
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
          userPhotoUrl = userPhotoUrl,
          onOpenOrganizations =
            onOpenOrganizations?.let { openOrgs ->
              {
                onDismissRequest()
                openOrgs()
              }
            },
          onOpenAdminPanel =
            onOpenAdminPanel?.let { openAdmin ->
              {
                onDismissRequest()
                openAdmin()
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
 * button to open the organizations page when [onOpenOrganizations] is provided), an optional About
 * button ([onOpenAbout]), and sign out.
 */
@Composable
fun UserProfileCard(
  userName: String,
  userEmail: String,
  userInitials: String,
  organization: String,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier,
  userPhotoUrl: String? = null,
  onOpenOrganizations: (() -> Unit)? = null,
  onOpenAdminPanel: (() -> Unit)? = null,
  onOpenAbout: (() -> Unit)? = null,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      ProfileCardHeader(userName, userEmail, userInitials, userPhotoUrl)

      // Organization badge
      if (organization.isNotBlank()) {
        Surface(
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
          Text(
            text = organization,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      if (onOpenOrganizations != null) {
        ProfileCardButton(
          icon = Icons.Outlined.Groups,
          text = stringResource(Res.string.app_manage_organizations),
          onClick = onOpenOrganizations,
        )
      }

      if (onOpenAdminPanel != null) {
        ProfileCardButton(
          icon = Icons.Outlined.AdminPanelSettings,
          text = stringResource(Res.string.app_admin_panel),
          onClick = onOpenAdminPanel,
        )
      }

      if (onOpenAbout != null) {
        ProfileCardButton(
          icon = Icons.Outlined.Info,
          text = stringResource(Res.string.app_about),
          onClick = onOpenAbout,
        )
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      // Sign Out Action Link
      ProfileCardButton(
        icon = Icons.AutoMirrored.Outlined.Logout,
        text = stringResource(Res.string.app_sign_out),
        onClick = onSignOut,
        contentColor = MaterialTheme.colorScheme.error,
      )
    }
  }
}

/** The header of [UserProfileCard]: the large avatar with the user's name and email. */
@Composable
private fun ProfileCardHeader(
  userName: String,
  userEmail: String,
  userInitials: String,
  userPhotoUrl: String?,
) {
  val avatarBitmap = rememberProfileImageBitmap(userPhotoUrl)
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
      if (avatarBitmap != null) {
        Image(
          bitmap = avatarBitmap,
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize().clip(CircleShape),
        )
      } else {
        Text(
          text = userInitials.ifBlank { "U" },
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }
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
}

/**
 * A full-width outlined action of [UserProfileCard]: an [icon] and a [text], in [contentColor] when
 * given (the error color for Sign out).
 */
@Composable
private fun ProfileCardButton(
  icon: ImageVector,
  text: String,
  onClick: () -> Unit,
  contentColor: Color? = null,
) {
  OutlinedButton(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth().height(40.dp),
    shape = MaterialTheme.shapes.small,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    colors =
      if (contentColor != null) ButtonDefaults.outlinedButtonColors(contentColor = contentColor)
      else ButtonDefaults.outlinedButtonColors(),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      modifier = Modifier.size(16.dp),
      tint = contentColor ?: LocalContentColor.current,
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text(
      text = text,
      style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
      color = contentColor ?: Color.Unspecified,
    )
  }
}
