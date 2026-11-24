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
package org.openforis.collectone.v2.web.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.InviteLinks
import org.openforis.collectone.v2.app.domain.model.editor.Collaborator
import org.openforis.collectone.v2.app.ui.common.rememberProfileImageBitmap
import org.openforis.collectone.v2.app.ui.text.labelRes
import org.openforis.collectone.v2.core.forms.ui.BadgeTone
import org.openforis.collectone.v2.core.forms.ui.TonalBadge
import org.openforis.collectone.v2.core.forms.ui.appMonospaceFontFamily
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.core.forms.ui.controls.clickable
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.surveyeditor_cancel
import org.openforis.collectone.v2.web.resources.surveyeditor_people_accept_invite
import org.openforis.collectone.v2.web.resources.surveyeditor_people_accept_invite_note
import org.openforis.collectone.v2.web.resources.surveyeditor_people_accept_invite_preview
import org.openforis.collectone.v2.web.resources.surveyeditor_people_copied
import org.openforis.collectone.v2.web.resources.surveyeditor_people_copy_link
import org.openforis.collectone.v2.web.resources.surveyeditor_people_full_name
import org.openforis.collectone.v2.web.resources.surveyeditor_people_invited
import org.openforis.collectone.v2.web.resources.surveyeditor_people_join_title
import org.openforis.collectone.v2.web.resources.surveyeditor_people_new_link
import org.openforis.collectone.v2.web.resources.surveyeditor_people_open_as_invitee
import org.openforis.collectone.v2.web.resources.surveyeditor_people_profile_photo
import org.openforis.collectone.v2.web.resources.surveyeditor_people_sharing_page
import org.openforis.collectone.v2.web.resources.surveyeditor_people_signed_in_as
import org.openforis.collectone.v2.web.ui.text.EditorText
import org.openforis.collectone.v2.web.ui.text.resolve

/** Prefix of the placeholder photo URLs drawn as illustrated portraits. */
internal const val AVATAR_SCHEME = "avatar:"

private data class PortraitPalette(
  val background: Color,
  val skin: Color,
  val hair: Color,
  val shirt: Color,
  val hairStyle: Int,
)

private val Portraits =
  listOf(
    PortraitPalette(Color(0xFFB3E5FC), Color(0xFF8D5524), Color(0xFF1B1B1B), Color(0xFF2E7D32), 0),
    PortraitPalette(Color(0xFFFFE0B2), Color(0xFFE0AC69), Color(0xFF4E342E), Color(0xFF1565C0), 1),
    PortraitPalette(Color(0xFFC8E6C9), Color(0xFFC68642), Color(0xFF212121), Color(0xFF6A1B9A), 2),
    PortraitPalette(Color(0xFFF8BBD0), Color(0xFF6B4226), Color(0xFF111111), Color(0xFF00838F), 2),
    PortraitPalette(Color(0xFFD1C4E9), Color(0xFFF1C27D), Color(0xFF8D6E63), Color(0xFFC62828), 0),
    PortraitPalette(Color(0xFFFFF59D), Color(0xFF7B4B2A), Color(0xFF1A1A1A), Color(0xFFEF6C00), 1),
    PortraitPalette(Color(0xFFB2DFDB), Color(0xFFFFDBAC), Color(0xFFD4A017), Color(0xFF37474F), 1),
    PortraitPalette(Color(0xFFCFD8DC), Color(0xFFA0522D), Color(0xFF3E2723), Color(0xFF558B2F), 0),
  )

/** Number of placeholder photos offered on the invite acceptance screen. */
internal val PortraitCount = Portraits.size

private val InitialsColors =
  listOf(
    Color(0xFF2E7D32),
    Color(0xFF1565C0),
    Color(0xFF6A1B9A),
    Color(0xFF00838F),
    Color(0xFFC62828),
    Color(0xFFEF6C00),
    Color(0xFF4E342E),
    Color(0xFF37474F),
  )

/**
 * Normalizes an organization's [logoUrl] for display and editing: trims whitespace, returns `null`
 * for blank strings, and ignores legacy `avatar:<n>` placeholder portrait keys so organizations
 * without an uploaded logo fall back to their letter badge.
 */
fun effectiveOrganizationLogoUrl(logoUrl: String?): String? =
  logoUrl?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith(AVATAR_SCHEME) }

/**
 * An organization's avatar: its uploaded or linked logo when set, otherwise a circular letter badge
 * derived from [name] matching the fallback badge used for users without avatars. Legacy
 * `avatar:<n>` placeholder portraits are ignored.
 */
@Composable
fun OrganizationAvatar(
  name: String,
  logoUrl: String?,
  modifier: Modifier = Modifier,
  size: Dp = 40.dp,
) {
  val bitmap = rememberProfileImageBitmap(effectiveOrganizationLogoUrl(logoUrl))
  Box(modifier = modifier.size(size).clip(CircleShape), contentAlignment = Alignment.Center) {
    if (bitmap != null) {
      Surface(
        color = Color.White,
        shape = CircleShape,
        modifier =
          Modifier.fillMaxSize()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
      ) {
        Image(
          bitmap = bitmap,
          contentDescription = null,
          contentScale = ContentScale.Fit,
          modifier = Modifier.fillMaxSize().clip(CircleShape),
        )
      }
    } else {
      val color = InitialsColors[name.hashCode().mod(InitialsColors.size)]
      Surface(color = color, modifier = Modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center) {
          Text(
            InviteLinks.initials(name),
            color = Color.White,
            fontWeight = FontWeight.Medium,
            fontSize = (size.value * 0.38f).sp,
          )
        }
      }
    }
  }
}

/**
 * A person's profile picture: their cached photo when they've joined, otherwise initials. Pending
 * invitees (no cached profile yet) get a muted, outlined initial.
 */
@Composable
fun ProfileAvatar(
  nameOrEmail: String,
  photoUrl: String?,
  modifier: Modifier = Modifier,
  size: Dp = 40.dp,
  pending: Boolean = false,
) {
  val portrait = photoUrl?.takeIf { it.startsWith(AVATAR_SCHEME) }?.removePrefix(AVATAR_SCHEME)
  val index = portrait?.toIntOrNull()
  val bitmap = rememberProfileImageBitmap(photoUrl)
  Box(modifier = modifier.size(size).clip(CircleShape), contentAlignment = Alignment.Center) {
    when {
      bitmap != null ->
        Image(
          bitmap = bitmap,
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize().clip(CircleShape),
        )
      index != null -> IllustratedPortrait(Portraits[index.mod(Portraits.size)])
      pending ->
        Box(
          modifier =
            Modifier.fillMaxSize()
              .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
              .padding(1.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            InviteLinks.initials(nameOrEmail).take(1),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = (size.value * 0.4f).sp,
          )
        }
      else -> {
        val color = InitialsColors[nameOrEmail.hashCode().mod(InitialsColors.size)]
        Surface(color = color, modifier = Modifier.fillMaxSize()) {
          Box(contentAlignment = Alignment.Center) {
            Text(
              InviteLinks.initials(nameOrEmail),
              color = Color.White,
              fontWeight = FontWeight.Medium,
              fontSize = (size.value * 0.38f).sp,
            )
          }
        }
      }
    }
  }
}

/**
 * Placeholder "photo": a simple head-and-shoulders portrait (used until profile photos are loaded
 * from the network).
 */
@Composable
private fun IllustratedPortrait(p: PortraitPalette) {
  Canvas(modifier = Modifier.fillMaxSize()) {
    val w = size.width
    val h = size.height
    drawRect(p.background)
    val headCenter = Offset(w * 0.5f, h * 0.42f)
    val headRadius = w * 0.19f
    // Hair behind the head (long hair / curls).
    when (p.hairStyle) {
      1 ->
        drawRoundRect(
          p.hair,
          topLeft = Offset(w * 0.29f, h * 0.24f),
          size = Size(w * 0.42f, h * 0.46f),
          cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.2f),
        )
      2 -> drawCircle(p.hair, radius = w * 0.26f, center = Offset(w * 0.5f, h * 0.38f))
    }
    // Shoulders and neck.
    drawOval(p.shirt, topLeft = Offset(w * 0.14f, h * 0.7f), size = Size(w * 0.72f, h * 0.6f))
    drawRect(p.skin, topLeft = Offset(w * 0.43f, h * 0.52f), size = Size(w * 0.14f, h * 0.2f))
    drawCircle(p.skin, radius = headRadius, center = headCenter)
    // Hair on top.
    drawArc(
      p.hair,
      startAngle = 180f,
      sweepAngle = 180f,
      useCenter = true,
      topLeft = Offset(headCenter.x - headRadius * 1.08f, headCenter.y - headRadius * 1.12f),
      size = Size(headRadius * 2.16f, headRadius * 1.7f),
    )
  }
}

/**
 * One row in "People with access". Joined people show their cached full name and photo; pending
 * invitees show their email plus their invite link and actions.
 */
@Composable
fun PersonRow(
  displayName: String,
  email: String,
  photoUrl: String?,
  detail: String,
  pending: Boolean = false,
  pendingLabel: String = stringResource(Res.string.surveyeditor_people_invited),
  footer: (@Composable () -> Unit)? = null,
  actions: @Composable () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    verticalAlignment = Alignment.Top,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    ProfileAvatar(
      nameOrEmail = displayName,
      photoUrl = photoUrl,
      pending = pending,
      modifier = Modifier.padding(top = 4.dp),
    )
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          displayName,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = if (pending) FontWeight.Normal else FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false),
        )
        if (pending) TonalBadge(text = pendingLabel, tone = BadgeTone.PRIMARY)
      }
      val secondary = if (displayName == email) detail else "$email • $detail"
      Text(
        secondary,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      footer?.invoke()
    }
    Row(verticalAlignment = Alignment.CenterVertically) { actions() }
  }
}

/** Invite link of a pending collaborator, with copy / new link / simulated acceptance. */
@Composable
fun PendingInviteLinkRow(
  token: String,
  onResetLink: (() -> Unit)? = null,
  onOpenAsInvitee: (() -> Unit)? = null,
) {
  val clipboard = LocalClipboardManager.current
  var copied by remember(token) { mutableStateOf(false) }
  val url = InviteLinks.url(token)
  Column(modifier = Modifier.padding(top = 4.dp)) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(
        Icons.Outlined.Link,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        url,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = appMonospaceFontFamily(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
      val padding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
      TextButton(
        onClick = {
          clipboard.setText(AnnotatedString(url))
          copied = true
        },
        contentPadding = padding,
      ) {
        Text(
          stringResource(
            if (copied) Res.string.surveyeditor_people_copied
            else Res.string.surveyeditor_people_copy_link
          ),
          style = MaterialTheme.typography.labelMedium,
        )
      }
      if (onResetLink != null) {
        TextButton(onClick = onResetLink, contentPadding = padding) {
          Text(
            stringResource(Res.string.surveyeditor_people_new_link),
            style = MaterialTheme.typography.labelMedium,
          )
        }
      }
      if (onOpenAsInvitee != null) {
        TextButton(onClick = onOpenAsInvitee, contentPadding = padding) {
          Text(
            stringResource(Res.string.surveyeditor_people_open_as_invitee),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
          )
        }
      }
    }
  }
}

/**
 * Simulates what the invitee sees after opening their invite link and signing in: they confirm the
 * name and photo from their account, which Collect One caches on their ACL entry.
 */
@Composable
fun AcceptInviteDialog(
  surveyTitle: String,
  invitee: Collaborator,
  onAccept: (displayName: String, photoUrl: String?) -> StringResource?,
  onDismiss: () -> Unit,
) {
  AcceptInviteDialogContent(
    title = surveyTitle,
    inviteeEmail = invitee.email,
    roleLabel = stringResource(invitee.role.labelRes),
    peopleListName = stringResource(Res.string.surveyeditor_people_sharing_page),
    onAccept = { name, photo -> onAccept(name, photo)?.let { EditorText.Resource(it) } },
    onDismiss = onDismiss,
  )
}

/**
 * [AcceptInviteDialog] for any invite: joining [title] (a survey or an organization) as
 * [inviteeEmail] with [roleLabel]. [peopleListName] names the list where organizers will see the
 * cached name and photo.
 */
@Composable
fun AcceptInviteDialog(
  title: String,
  inviteeEmail: String,
  roleLabel: String,
  peopleListName: String,
  onAccept: (displayName: String, photoUrl: String?) -> String?,
  onDismiss: () -> Unit,
) {
  AcceptInviteDialogContent(
    title = title,
    inviteeEmail = inviteeEmail,
    roleLabel = roleLabel,
    peopleListName = peopleListName,
    onAccept = { name, photo -> onAccept(name, photo)?.let { EditorText.Verbatim(it) } },
    onDismiss = onDismiss,
  )
}

/** [AcceptInviteDialog] content; [onAccept] returns why the invite can't be accepted, or `null`. */
@Composable
private fun AcceptInviteDialogContent(
  title: String,
  inviteeEmail: String,
  roleLabel: String,
  peopleListName: String,
  onAccept: (displayName: String, photoUrl: String?) -> EditorText?,
  onDismiss: () -> Unit,
) {
  var name by remember(inviteeEmail) { mutableStateOf(InviteLinks.suggestedName(inviteeEmail)) }
  var photo by
    remember(inviteeEmail) {
      mutableStateOf<String?>(AVATAR_SCHEME + inviteeEmail.hashCode().mod(PortraitCount))
    }
  var error by remember { mutableStateOf<EditorText?>(null) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(Res.string.surveyeditor_people_join_title, title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
          stringResource(Res.string.surveyeditor_people_accept_invite_preview, inviteeEmail),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          ProfileAvatar(nameOrEmail = name.ifBlank { inviteeEmail }, photoUrl = photo, size = 56.dp)
          Column {
            Text(
              name.ifBlank { inviteeEmail },
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Medium,
            )
            Text(
              stringResource(Res.string.surveyeditor_people_signed_in_as, inviteeEmail, roleLabel),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        OutlinedTextField(
          value = name,
          onValueChange = {
            name = it
            error = null
          },
          label = { Text(stringResource(Res.string.surveyeditor_people_full_name)) },
          singleLine = true,
          isError = error != null,
          supportingText = error?.let { { Text(it.resolve()) } },
          modifier = Modifier.fillMaxWidth(),
        )
        Text(
          stringResource(Res.string.surveyeditor_people_profile_photo),
          style = MaterialTheme.typography.labelLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          (0 until PortraitCount).forEach { i ->
            val url = AVATAR_SCHEME + i
            PhotoChoice(selected = photo == url, onClick = { photo = url }) {
              ProfileAvatar(nameOrEmail = name, photoUrl = url, size = 36.dp)
            }
          }
          PhotoChoice(selected = photo == null, onClick = { photo = null }) {
            ProfileAvatar(
              nameOrEmail = name.ifBlank { inviteeEmail },
              photoUrl = null,
              size = 36.dp,
            )
          }
        }
        Text(
          stringResource(Res.string.surveyeditor_people_accept_invite_note, peopleListName),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          error = onAccept(name, photo)
          if (error == null) onDismiss()
        }
      ) {
        Text(stringResource(Res.string.surveyeditor_people_accept_invite))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(Res.string.surveyeditor_cancel)) }
    },
  )
}

@Composable
private fun PhotoChoice(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
  val ring = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
  Box(
    modifier =
      Modifier.clip(CircleShape)
        .border(2.5.dp, ring, CircleShape)
        .clickable(onClick = onClick)
        .padding(3.dp)
  ) {
    content()
  }
}
