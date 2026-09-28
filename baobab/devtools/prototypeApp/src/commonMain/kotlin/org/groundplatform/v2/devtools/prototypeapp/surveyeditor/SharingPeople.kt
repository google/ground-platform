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
package org.groundplatform.v2.devtools.prototypeapp.surveyeditor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.groundplatform.v2.core.forms.ui.GroundBadgeTone
import org.groundplatform.v2.core.forms.ui.GroundTonalBadge

/** Prefix of the placeholder photo URLs the prototype draws as illustrated portraits. */
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
 * A person's profile picture: their cached photo when they've joined, otherwise initials. Pending
 * invitees (no cached profile yet) get a muted, outlined initial.
 */
@Composable
internal fun ProfileAvatar(
  nameOrEmail: String,
  photoUrl: String?,
  modifier: Modifier = Modifier,
  size: Dp = 40.dp,
  pending: Boolean = false,
) {
  val portrait = photoUrl?.takeIf { it.startsWith(AVATAR_SCHEME) }?.removePrefix(AVATAR_SCHEME)
  val index = portrait?.toIntOrNull()
  Box(modifier = modifier.size(size).clip(CircleShape), contentAlignment = Alignment.Center) {
    when {
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
 * Placeholder "photo": a simple head-and-shoulders portrait (no network images in the prototype).
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
internal fun PersonRow(
  displayName: String,
  email: String,
  photoUrl: String?,
  detail: String,
  pending: Boolean = false,
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
        if (pending) GroundTonalBadge(text = "Invited", tone = GroundBadgeTone.PRIMARY)
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
internal fun PendingInviteLinkRow(
  token: String,
  onResetLink: () -> Unit,
  onOpenAsInvitee: () -> Unit,
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
        Icons.Default.Link,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        url,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
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
        Text(if (copied) "Copied" else "Copy link", style = MaterialTheme.typography.labelMedium)
      }
      TextButton(onClick = onResetLink, contentPadding = padding) {
        Text("New link", style = MaterialTheme.typography.labelMedium)
      }
      TextButton(onClick = onOpenAsInvitee, contentPadding = padding) {
        Text("Open as invitee", style = MaterialTheme.typography.labelMedium, maxLines = 1)
      }
    }
  }
}

/**
 * Simulates what the invitee sees after opening their invite link and signing in: they confirm the
 * name and photo from their account, which Ground caches on their ACL entry.
 */
@Composable
internal fun AcceptInviteDialog(
  surveyTitle: String,
  invitee: Collaborator,
  onAccept: (displayName: String, photoUrl: String?) -> String?,
  onDismiss: () -> Unit,
) {
  var name by remember(invitee.email) { mutableStateOf(InviteLinks.suggestedName(invitee.email)) }
  var photo by
    remember(invitee.email) {
      mutableStateOf<String?>(AVATAR_SCHEME + invitee.email.hashCode().mod(PortraitCount))
    }
  var error by remember { mutableStateOf<String?>(null) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Join “$surveyTitle”") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
          "Preview of what ${invitee.email} sees after opening their invite link and signing in.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          ProfileAvatar(
            nameOrEmail = name.ifBlank { invitee.email },
            photoUrl = photo,
            size = 56.dp,
          )
          Column {
            Text(
              name.ifBlank { invitee.email },
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Medium,
            )
            Text(
              "Signed in as ${invitee.email} • ${invitee.role.label}",
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
          label = { Text("Full name") },
          singleLine = true,
          isError = error != null,
          supportingText = error?.let { { Text(it) } },
          modifier = Modifier.fillMaxWidth(),
        )
        Text("Profile photo", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          (0 until PortraitCount).forEach { i ->
            val url = AVATAR_SCHEME + i
            PhotoChoice(selected = photo == url, onClick = { photo = url }) {
              ProfileAvatar(nameOrEmail = name, photoUrl = url, size = 36.dp)
            }
          }
          PhotoChoice(selected = photo == null, onClick = { photo = null }) {
            ProfileAvatar(
              nameOrEmail = name.ifBlank { invitee.email },
              photoUrl = null,
              size = 36.dp,
            )
          }
        }
        Text(
          "Ground saves your name and photo so survey organizers can recognize you on the " +
            "Sharing page. They're refreshed whenever you sign in.",
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
        Text("Accept invite")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
