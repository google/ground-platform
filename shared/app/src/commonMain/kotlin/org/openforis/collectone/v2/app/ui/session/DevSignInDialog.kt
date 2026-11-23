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
package org.openforis.collectone.v2.app.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.SimulatedRole
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_cancel
import org.openforis.collectone.v2.app.resources.app_continue
import org.openforis.collectone.v2.app.resources.app_dev_sign_in_footer
import org.openforis.collectone.v2.app.resources.app_dev_sign_in_real_accounts_intro
import org.openforis.collectone.v2.app.resources.app_dev_sign_in_role_administrator
import org.openforis.collectone.v2.app.resources.app_dev_sign_in_role_administrator_real_description
import org.openforis.collectone.v2.app.resources.app_dev_sign_in_role_administrator_sample_description
import org.openforis.collectone.v2.app.resources.app_dev_sign_in_role_regular_user
import org.openforis.collectone.v2.app.resources.app_dev_sign_in_role_regular_user_real_description
import org.openforis.collectone.v2.app.resources.app_dev_sign_in_role_regular_user_sample_description
import org.openforis.collectone.v2.app.resources.app_dev_sign_in_sample_users_intro
import org.openforis.collectone.v2.app.resources.app_dev_sign_in_title
import org.openforis.collectone.v2.app.ui.onboarding.DevSignInUiState
import org.openforis.collectone.v2.core.forms.ui.controls.RadioButton
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.core.forms.ui.controls.selectable

/**
 * Development sign-in dialog, shown in development builds after signing in: explains how the
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
    title = { Text(stringResource(Res.string.app_dev_sign_in_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          text =
            if (state.usesRealAccounts) {
              stringResource(Res.string.app_dev_sign_in_real_accounts_intro)
            } else {
              stringResource(Res.string.app_dev_sign_in_sample_users_intro)
            },
          style = MaterialTheme.typography.bodyMedium,
        )
        Column(
          Modifier.selectableGroup().semantics { isTraversalGroup = true },
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          RoleOption(
            title = stringResource(Res.string.app_dev_sign_in_role_administrator),
            description =
              if (state.usesRealAccounts) {
                stringResource(Res.string.app_dev_sign_in_role_administrator_real_description)
              } else {
                stringResource(Res.string.app_dev_sign_in_role_administrator_sample_description)
              },
            selected = state.selectedRole == SimulatedRole.ADMINISTRATOR,
            onSelect = { onSelectRole(SimulatedRole.ADMINISTRATOR) },
            traversalIndex = 0f,
          )
          RoleOption(
            title = stringResource(Res.string.app_dev_sign_in_role_regular_user),
            description =
              if (state.usesRealAccounts) {
                stringResource(Res.string.app_dev_sign_in_role_regular_user_real_description)
              } else {
                stringResource(Res.string.app_dev_sign_in_role_regular_user_sample_description)
              },
            selected = state.selectedRole == SimulatedRole.REGULAR_USER,
            onSelect = { onSelectRole(SimulatedRole.REGULAR_USER) },
            traversalIndex = 1f,
          )
        }
        Text(
          text = stringResource(Res.string.app_dev_sign_in_footer),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    confirmButton = {
      TextButton(onClick = onConfirm) { Text(stringResource(Res.string.app_continue)) }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(Res.string.app_cancel)) }
    },
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
