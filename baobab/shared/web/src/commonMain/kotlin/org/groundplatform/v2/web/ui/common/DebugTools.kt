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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.groundplatform.v2.app.ui.AppSession

/**
 * Reusable debug tools button with the bug icon that pops out the workbench tools in the web top
 * header.
 */
@Composable
fun DebugToolsButton(
  state: AppSession,
  modifier: Modifier = Modifier,
  iconTint: Color = MaterialTheme.colorScheme.onSurface,
) {
  DebugToolsButton(
    isDarkTheme = state.isDarkTheme,
    onToggleDarkTheme = state::toggleDarkTheme,
    modifier = modifier,
    iconTint = iconTint,
  )
}

/** Stateless debug tools button driven by explicit state and callbacks. */
@Composable
fun DebugToolsButton(
  isDarkTheme: Boolean,
  onToggleDarkTheme: () -> Unit,
  modifier: Modifier = Modifier,
  iconTint: Color = MaterialTheme.colorScheme.onSurface,
) {
  var isMenuOpen by remember { mutableStateOf(false) }

  Box(modifier = modifier) {
    IconButton(onClick = { isMenuOpen = !isMenuOpen }, modifier = Modifier.size(40.dp)) {
      Icon(
        imageVector = Icons.Outlined.BugReport,
        contentDescription = "Debug tools",
        tint = iconTint,
      )
    }

    DebugToolsDropdown(
      isDarkTheme = isDarkTheme,
      onToggleDarkTheme = onToggleDarkTheme,
      expanded = isMenuOpen,
      onDismissRequest = { isMenuOpen = false },
    )
  }
}

/** Popout menu displaying the web console's debug tools. */
@Composable
fun DebugToolsDropdown(
  state: AppSession,
  expanded: Boolean,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  DebugToolsDropdown(
    isDarkTheme = state.isDarkTheme,
    onToggleDarkTheme = state::toggleDarkTheme,
    expanded = expanded,
    onDismissRequest = onDismissRequest,
    modifier = modifier,
  )
}

/** Stateless popout menu displaying the web console's debug tools. */
@Composable
fun DebugToolsDropdown(
  isDarkTheme: Boolean,
  onToggleDarkTheme: () -> Unit,
  expanded: Boolean,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  DropdownMenu(
    expanded = expanded,
    onDismissRequest = onDismissRequest,
    modifier =
      modifier
        .width(280.dp)
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .padding(vertical = 4.dp),
  ) {
    // Header
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        imageVector = Icons.Outlined.BugReport,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(22.dp),
      )
      Column {
        Text(
          text = "Workbench tools",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = "webApp",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

    // Environment & Simulation
    Text(
      text = "Environment",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.primary,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )

    DropdownMenuItem(
      text = { Text(if (isDarkTheme) "Switch to light UI" else "Switch to dark UI") },
      leadingIcon = {
        Icon(
          imageVector = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
          contentDescription = null,
          modifier = Modifier.size(20.dp),
        )
      },
      onClick = onToggleDarkTheme,
    )
  }
}
