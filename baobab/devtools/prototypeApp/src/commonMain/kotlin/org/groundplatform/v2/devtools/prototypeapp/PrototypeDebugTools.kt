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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.AirplanemodeInactive
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
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

/**
 * Reusable debug tools button with the bug icon that pops out the workbench tools (view switcher,
 * theme toggle, offline simulation, and reset flow) previously shown in the top workbench header.
 */
@Composable
fun PrototypeDebugToolsButton(
  state: PrototypeAppState,
  modifier: Modifier = Modifier,
  iconTint: Color = MaterialTheme.colorScheme.onSurface,
) {
  var isMenuOpen by remember { mutableStateOf(false) }

  Box(modifier = modifier) {
    IconButton(
      onClick = { isMenuOpen = !isMenuOpen },
      modifier = Modifier.size(40.dp),
    ) {
      Icon(
        imageVector = Icons.Filled.BugReport,
        contentDescription = "Debug tools",
        tint = iconTint,
      )
    }

    PrototypeDebugToolsDropdown(
      state = state,
      expanded = isMenuOpen,
      onDismissRequest = { isMenuOpen = false },
    )
  }
}

/** Popout menu displaying the prototype workbench tools. */
@Composable
fun PrototypeDebugToolsDropdown(
  state: PrototypeAppState,
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
        imageVector = Icons.Filled.BugReport,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(22.dp),
      )
      Column {
        Text(
          text = "Workbench Tools",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = "devtools/prototypeApp",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

    // View Switcher Section
    Text(
      text = "SWITCH VIEW",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.primary,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )

    DropdownMenuItem(
      text = { Text("Mobile prototype") },
      leadingIcon = {
        Icon(
          imageVector = Icons.Filled.Smartphone,
          contentDescription = null,
          modifier = Modifier.size(20.dp),
        )
      },
      trailingIcon = {
        if (state.activeWorkbenchPage == PrototypeWorkbenchPage.MOBILE_PROTOTYPE) {
          Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Active view",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
          )
        }
      },
      onClick = {
        state.selectWorkbenchPage(PrototypeWorkbenchPage.MOBILE_PROTOTYPE)
        onDismissRequest()
      },
    )

    DropdownMenuItem(
      text = { Text("Web app") },
      leadingIcon = {
        Icon(
          imageVector = Icons.Filled.Map,
          contentDescription = null,
          modifier = Modifier.size(20.dp),
        )
      },
      trailingIcon = {
        if (state.activeWorkbenchPage.isWebApp) {
          Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Active view",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
          )
        }
      },
      onClick = {
        state.selectWorkbenchPage(PrototypeWorkbenchPage.WEB_DASHBOARD)
        onDismissRequest()
      },
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

    // Environment & Simulation
    Text(
      text = "ENVIRONMENT",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.primary,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )

    DropdownMenuItem(
      text = {
        Text(if (state.isDarkTheme) "Switch to Light UI" else "Switch to Dark UI")
      },
      leadingIcon = {
        Icon(
          imageVector =
            if (state.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
          contentDescription = null,
          modifier = Modifier.size(20.dp),
        )
      },
      onClick = {
        state.toggleDarkTheme()
      },
    )

    DropdownMenuItem(
      text = {
        Text(if (state.isAirplaneMode) "Airplane mode: ON" else "Airplane mode")
      },
      leadingIcon = {
        Icon(
          imageVector =
            if (state.isAirplaneMode) {
              Icons.Default.AirplanemodeActive
            } else {
              Icons.Default.AirplanemodeInactive
            },
          contentDescription = null,
          tint =
            if (state.isAirplaneMode) {
              Color(0xFFFFB74D)
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            },
          modifier = Modifier.size(20.dp),
        )
      },
      trailingIcon = {
        if (state.isAirplaneMode) {
          Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Active",
            tint = Color(0xFFFFB74D),
            modifier = Modifier.size(18.dp),
          )
        }
      },
      onClick = {
        state.toggleAirplaneMode()
      },
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

    // Actions
    Text(
      text = "ACTIONS",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.primary,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )

    DropdownMenuItem(
      text = { Text("Reset Flow") },
      leadingIcon = {
        Icon(
          imageVector = Icons.Default.Refresh,
          contentDescription = null,
          modifier = Modifier.size(20.dp),
        )
      },
      onClick = {
        state.resetPrototypeFlow()
        onDismissRequest()
      },
    )
  }
}
