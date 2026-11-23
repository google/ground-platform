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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openforis.collectone.v2.app.domain.model.AppEnvironment
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.Switch
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.core.forms.ui.controls.clickable
import org.openforis.collectone.v2.core.forms.ui.controls.toggleable

/** Title of the web Developer Tools dialog. */
const val WEB_DEV_TOOLS_DIALOG_TITLE = "Developer tools"

/** Test tag on the Dark mode toggle row in [WebDevToolsDialog]. */
const val WEB_DEV_TOOLS_DARK_MODE_TOGGLE_TAG = "web_dev_tools_dark_mode_toggle"

/** Active debug console or diagnostic route served by `server/` (`UserDataServer`). */
data class BackendDebugConsoleRoute(
  val title: String,
  val path: String,
  val subtitle: String,
  val icon: ImageVector,
)

/** Active backend debug consoles and diagnostic routes exposed in [WebDevToolsDialog]. */
val BACKEND_DEBUG_CONSOLE_ROUTES: List<BackendDebugConsoleRoute> =
  listOf(
    BackendDebugConsoleRoute(
      title = "RPC Inspector",
      path = "/debug",
      subtitle = "Live gRPC call stream, filters, and payload inspector",
      icon = Icons.Outlined.SwapHoriz,
    ),
    BackendDebugConsoleRoute(
      title = "Spanner Inspector",
      path = "/debug/spanner",
      subtitle = "Database tables, read-only SQL, schema DDL, and operations",
      icon = Icons.Outlined.TableChart,
    ),
    BackendDebugConsoleRoute(
      title = "Message Inspector",
      path = "/debug/messages",
      subtitle = "Sent and logged survey and organization invitation emails",
      icon = Icons.Outlined.Description,
    ),
    BackendDebugConsoleRoute(
      title = "Server Health",
      path = "/health",
      subtitle = "Backend HTTP health check endpoint",
      icon = Icons.Outlined.CheckCircle,
    ),
  )

/** Builds the absolute backend debug URL for [path] against [backendBaseUrl], or `""` if blank. */
fun buildBackendDebugRouteUrl(backendBaseUrl: String, path: String): String {
  val normalizedBase = backendBaseUrl.trim().trimEnd('/')
  if (normalizedBase.isEmpty()) return ""
  val normalizedPath = if (path.startsWith('/')) path else "/$path"
  return "$normalizedBase$normalizedPath"
}

/**
 * Developer tools popup for non-production builds of the Collect One web console: switches the web
 * app between light and dark themes and links directly to the configured backend server's debug
 * consoles.
 */
@Composable
fun WebDevToolsDialog(
  environment: AppEnvironment,
  isDarkTheme: Boolean,
  backendBaseUrl: String,
  onToggleDarkTheme: () -> Unit,
  onDismiss: () -> Unit,
  onOpenUrl: (String) -> Unit = ::openExternalUrl,
) {
  val normalizedBaseUrl = backendBaseUrl.trim().trimEnd('/')
  AlertDialog(
    onDismissRequest = onDismiss,
    icon = { Icon(Icons.Outlined.Tune, contentDescription = null) },
    title = { DevToolsDialogHeader(environment = environment) },
    text = {
      Column(
        modifier =
          Modifier.widthIn(min = 420.dp, max = 520.dp)
            .heightIn(max = 520.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        ThemeToggleCard(isDarkTheme = isDarkTheme, onToggleDarkTheme = onToggleDarkTheme)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        BackendDebugConsolesSection(
          normalizedBaseUrl = normalizedBaseUrl,
          onOpenUrl = onOpenUrl,
        )
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
  )
}

@Composable
private fun DevToolsDialogHeader(environment: AppEnvironment) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(text = WEB_DEV_TOOLS_DIALOG_TITLE, style = MaterialTheme.typography.headlineSmall)
    Surface(
      shape = MaterialTheme.shapes.small,
      color = MaterialTheme.colorScheme.secondaryContainer,
      contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
      Text(
        text = environment.name,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
      )
    }
  }
}

@Composable
private fun ThemeToggleCard(isDarkTheme: Boolean, onToggleDarkTheme: () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
      text = "Appearance",
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Surface(
      shape = MaterialTheme.shapes.medium,
      color = MaterialTheme.colorScheme.surfaceContainerLow,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .testTag(WEB_DEV_TOOLS_DARK_MODE_TOGGLE_TAG)
            .toggleable(
              value = isDarkTheme,
              role = Role.Switch,
              onValueChange = { desired -> if (desired != isDarkTheme) onToggleDarkTheme() },
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f),
        ) {
          Icon(
            imageVector = if (isDarkTheme) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
          )
          Column {
            Text(
              text = "Dark mode",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = if (isDarkTheme) "Dark theme active" else "Light theme active",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Switch(checked = isDarkTheme, onCheckedChange = null)
      }
    }
  }
}

@Composable
private fun BackendDebugConsolesSection(normalizedBaseUrl: String, onOpenUrl: (String) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        text = "Backend debug consoles",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text =
          normalizedBaseUrl.ifEmpty {
            "No backend server URL configured (USER_DATA_SERVICE_URL)"
          },
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    BACKEND_DEBUG_CONSOLE_ROUTES.forEach { route ->
      BackendDebugConsoleRow(
        route = route,
        targetUrl = buildBackendDebugRouteUrl(normalizedBaseUrl, route.path),
        onOpenUrl = onOpenUrl,
      )
    }
  }
}

@Composable
private fun BackendDebugConsoleRow(
  route: BackendDebugConsoleRoute,
  targetUrl: String,
  onOpenUrl: (String) -> Unit,
) {
  val enabled = targetUrl.isNotEmpty()
  Surface(
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .clickable(enabled = enabled, role = Role.Button) { onOpenUrl(targetUrl) }
          .padding(horizontal = 14.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = route.icon,
        contentDescription = null,
        tint =
          if (enabled) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
      )
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = route.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color =
              if (enabled) MaterialTheme.colorScheme.onSurface
              else MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = route.path,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Text(
          text = route.subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (enabled) {
        Icon(
          imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
          contentDescription = "Open ${route.title}",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }
}
