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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.openforis.collectone.v2.app.domain.model.SimulatedRole
import org.openforis.collectone.v2.app.ui.state.DevSignInUiState

/**
 * Development sign-in dialog, shown in development builds before signing in: explains how the
 * simulated sign-in works and picks the app role to simulate. The user data service only honors the
 * role in development mode (`AUTH_MODE=dev`).
 */
@Composable
fun DevSignInDialog(
  state: DevSignInUiState,
  onSelectRole: (SimulatedRole) -> Unit,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Development sign-in") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          text =
            if (state.usesRealAccounts) {
              "You'll sign in with your Google account. The user data service runs in development " +
                "mode, so it can treat you as either app role while you test."
            } else {
              "Firebase sign-in is off in this build, so you'll sign in as a sample user instead of " +
                "your own account."
            },
          style = MaterialTheme.typography.bodyMedium,
        )
        Column(
          Modifier.selectableGroup().semantics { isTraversalGroup = true },
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          RoleOption(
            title = "Administrator",
            description =
              if (state.usesRealAccounts) {
                "Sees and can change all sample surveys, organizations, and data."
              } else {
                "Signs in as Adam Admin, who sees and can change all sample data."
              },
            selected = state.selectedRole == SimulatedRole.ADMINISTRATOR,
            onSelect = { onSelectRole(SimulatedRole.ADMINISTRATOR) },
            traversalIndex = 0f,
          )
          RoleOption(
            title = "Regular user",
            description =
              if (state.usesRealAccounts) {
                "Sees only surveys you own, are invited to, or that your organizations or public " +
                  "links share with you."
              } else {
                "Signs in as Ima User, a data collector in the Kenya Forest Service sample " +
                  "organization."
              },
            selected = state.selectedRole == SimulatedRole.REGULAR_USER,
            onSelect = { onSelectRole(SimulatedRole.REGULAR_USER) },
            traversalIndex = 1f,
          )
        }
        Text(
          text =
            "This device remembers your choice; sign out to switch roles. Production builds sign " +
              "in normally, without this dialog.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    confirmButton = { TextButton(onClick = onConfirm) { Text("Continue") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
private fun RoleOption(
  title: String,
  description: String,
  selected: Boolean,
  onSelect: () -> Unit,
  traversalIndex: Float = 0f,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
        .semantics(mergeDescendants = true) {
          contentDescription = title
          this.traversalIndex = traversalIndex
        }
        .padding(vertical = 4.dp),
    verticalAlignment = Alignment.Top,
  ) {
    RadioButton(selected = selected, onClick = null)
    Column(Modifier.padding(start = 12.dp)) {
      Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
      Text(
        description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
