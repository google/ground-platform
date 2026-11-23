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
package org.openforis.collectone.v2.web.ui.organization

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.repository.ClientVersionRepository
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.ElevatedCard
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.version.MobileClientVersionPolicy
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.org_discard
import org.openforis.collectone.v2.web.resources.org_mobile_version_latest_help
import org.openforis.collectone.v2.web.resources.org_mobile_version_latest_label
import org.openforis.collectone.v2.web.resources.org_mobile_version_min_help
import org.openforis.collectone.v2.web.resources.org_mobile_version_min_label
import org.openforis.collectone.v2.web.resources.org_mobile_version_save
import org.openforis.collectone.v2.web.resources.org_mobile_version_saved
import org.openforis.collectone.v2.web.resources.org_mobile_version_unavailable
import org.openforis.collectone.v2.web.resources.org_mobile_version_url_help
import org.openforis.collectone.v2.web.resources.org_mobile_version_url_label

/** Provides the [ClientVersionRepository] to the platform Admin Panel's Mobile App tab. */
val LocalClientVersionRepository = compositionLocalOf<ClientVersionRepository?> { null }

/**
 * Platform admin pane for viewing and updating the server's [MobileClientVersionPolicy]:
 * `latestVersion` (suggested update), `minRequiredVersion` (mandatory update), and `updateUrl`.
 */
@Composable
internal fun MobileClientVersionPane(clientVersionRepository: ClientVersionRepository?) {
  if (clientVersionRepository == null) {
    Text(
      text = stringResource(Res.string.org_mobile_version_unavailable),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    return
  }
  val storedPolicy by clientVersionRepository.policy.collectAsState()
  var latestVersion by remember(storedPolicy) { mutableStateOf(storedPolicy.latestVersion) }
  var minRequiredVersion by
    remember(storedPolicy) { mutableStateOf(storedPolicy.minRequiredVersion) }
  var updateUrl by remember(storedPolicy) { mutableStateOf(storedPolicy.updateUrl) }
  var isSaving by remember { mutableStateOf(false) }
  var statusMessage by remember { mutableStateOf<String?>(null) }
  var isError by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  val savedText = stringResource(Res.string.org_mobile_version_saved)

  LaunchedEffect(clientVersionRepository) { clientVersionRepository.refreshPolicy() }

  val draft =
    MobileClientVersionPolicy(
        latestVersion = latestVersion,
        minRequiredVersion = minRequiredVersion,
        updateUrl = updateUrl,
      )
      .normalized()
  val validationError = draft.validationError()
  val isDirty = draft != storedPolicy.normalized()

  MobileClientVersionCard(
    latestVersion = latestVersion,
    onLatestVersionChange = {
      latestVersion = it
      statusMessage = null
    },
    minRequiredVersion = minRequiredVersion,
    onMinRequiredVersionChange = {
      minRequiredVersion = it
      statusMessage = null
    },
    updateUrl = updateUrl,
    onUpdateUrlChange = {
      updateUrl = it
      statusMessage = null
    },
    displayedMessage = validationError ?: statusMessage,
    isError = validationError != null || isError,
    isDirty = isDirty,
    isSaving = isSaving,
    canSave = isDirty && validationError == null && !isSaving,
    onDiscard = {
      latestVersion = storedPolicy.latestVersion
      minRequiredVersion = storedPolicy.minRequiredVersion
      updateUrl = storedPolicy.updateUrl
      statusMessage = null
      isError = false
    },
    onSave = {
      isSaving = true
      statusMessage = null
      isError = false
      scope.launch {
        val result = clientVersionRepository.updatePolicy(draft)
        isSaving = false
        result.fold(
          onSuccess = {
            statusMessage = savedText
            isError = false
          },
          onFailure = { error ->
            statusMessage = error.message ?: "Failed to save mobile version policy"
            isError = true
          },
        )
      }
    },
  )
}

@Suppress("LongParameterList")
@Composable
private fun MobileClientVersionCard(
  latestVersion: String,
  onLatestVersionChange: (String) -> Unit,
  minRequiredVersion: String,
  onMinRequiredVersionChange: (String) -> Unit,
  updateUrl: String,
  onUpdateUrlChange: (String) -> Unit,
  displayedMessage: String?,
  isError: Boolean,
  isDirty: Boolean,
  isSaving: Boolean,
  canSave: Boolean,
  onDiscard: () -> Unit,
  onSave: () -> Unit,
) {
  ElevatedCard(modifier = Modifier.fillMaxWidth().widthIn(max = OrganizationPaneMaxWidth)) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      MobileClientVersionFormFields(
        latestVersion = latestVersion,
        onLatestVersionChange = onLatestVersionChange,
        minRequiredVersion = minRequiredVersion,
        onMinRequiredVersionChange = onMinRequiredVersionChange,
        updateUrl = updateUrl,
        onUpdateUrlChange = onUpdateUrlChange,
      )
      if (displayedMessage != null) {
        Text(
          text = displayedMessage,
          style = MaterialTheme.typography.bodySmall,
          color =
            if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
      }
      MobileClientVersionActionRow(
        isDirty = isDirty,
        isSaving = isSaving,
        canSave = canSave,
        onDiscard = onDiscard,
        onSave = onSave,
      )
    }
  }
}

@Composable
private fun MobileClientVersionFormFields(
  latestVersion: String,
  onLatestVersionChange: (String) -> Unit,
  minRequiredVersion: String,
  onMinRequiredVersionChange: (String) -> Unit,
  updateUrl: String,
  onUpdateUrlChange: (String) -> Unit,
) {
  OutlinedTextField(
    value = latestVersion,
    onValueChange = onLatestVersionChange,
    label = { Text(stringResource(Res.string.org_mobile_version_latest_label)) },
    supportingText = { Text(stringResource(Res.string.org_mobile_version_latest_help)) },
    placeholder = { Text("0.0.2") },
    singleLine = true,
    modifier = Modifier.fillMaxWidth(),
  )
  OutlinedTextField(
    value = minRequiredVersion,
    onValueChange = onMinRequiredVersionChange,
    label = { Text(stringResource(Res.string.org_mobile_version_min_label)) },
    supportingText = { Text(stringResource(Res.string.org_mobile_version_min_help)) },
    placeholder = { Text("0.0.1") },
    singleLine = true,
    modifier = Modifier.fillMaxWidth(),
  )
  OutlinedTextField(
    value = updateUrl,
    onValueChange = onUpdateUrlChange,
    label = { Text(stringResource(Res.string.org_mobile_version_url_label)) },
    supportingText = { Text(stringResource(Res.string.org_mobile_version_url_help)) },
    placeholder = { Text("https://play.google.com/store/apps/details?id=...") },
    singleLine = true,
    modifier = Modifier.fillMaxWidth(),
  )
}

@Composable
private fun MobileClientVersionActionRow(
  isDirty: Boolean,
  isSaving: Boolean,
  canSave: Boolean,
  onDiscard: () -> Unit,
  onSave: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    OutlinedButton(onClick = onDiscard, enabled = isDirty && !isSaving) {
      Text(stringResource(Res.string.org_discard))
    }
    Button(onClick = onSave, enabled = canSave) {
      if (isSaving) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
      } else {
        Text(stringResource(Res.string.org_mobile_version_save))
      }
    }
  }
}
