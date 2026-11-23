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
package org.openforis.collectone.v2.web.ui.formeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.FormAvailability
import org.openforis.collectone.v2.app.domain.model.editor.FormPreviewTarget
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.core.forms.ui.controls.Switch
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.core.forms.ui.controls.toggleable
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.formeditor_availability_google_earth_needs_map_selection
import org.openforis.collectone.v2.web.resources.formeditor_availability_google_earth_off
import org.openforis.collectone.v2.web.resources.formeditor_availability_google_earth_on
import org.openforis.collectone.v2.web.resources.formeditor_availability_google_earth_unavailable
import org.openforis.collectone.v2.web.resources.formeditor_availability_mobile_off
import org.openforis.collectone.v2.web.resources.formeditor_availability_mobile_on
import org.openforis.collectone.v2.web.resources.formeditor_availability_none_warning
import org.openforis.collectone.v2.web.resources.formeditor_availability_web_off
import org.openforis.collectone.v2.web.resources.formeditor_availability_web_on
import org.openforis.collectone.v2.web.resources.formeditor_available_on_mobile
import org.openforis.collectone.v2.web.resources.formeditor_available_on_web
import org.openforis.collectone.v2.web.resources.formeditor_cancel
import org.openforis.collectone.v2.web.resources.formeditor_enable_google_earth_confirm_message
import org.openforis.collectone.v2.web.resources.formeditor_enable_google_earth_confirm_title
import org.openforis.collectone.v2.web.resources.formeditor_enable_on_platform
import org.openforis.collectone.v2.web.resources.formeditor_enable_open_in_google_earth
import org.openforis.collectone.v2.web.resources.formeditor_enable_web_confirm_message
import org.openforis.collectone.v2.web.resources.formeditor_enable_web_confirm_title
import org.openforis.collectone.v2.web.resources.formeditor_find_map_feature_by
import org.openforis.collectone.v2.web.resources.formeditor_google_earth_incompatible_questions_summary
import org.openforis.collectone.v2.web.resources.formeditor_selected_on_map

/**
 * Two switches, one per platform, for where collectors can open the Form. Each row's caption spells
 * out the current effect; a warning appears when both are off, and an error with a **Fix all**
 * action when the Form is on for web but has GPS-only geometry questions web can't capture.
 */
@Composable
internal fun AvailabilityToggles(uiState: FormEditorUiState, actions: FormSettingsActions) {
  val availability = uiState.form.availability
  var confirmingEnableWeb by remember { mutableStateOf(false) }
  var confirmingEnableGoogleEarth by remember { mutableStateOf(false) }
  if (confirmingEnableWeb) {
    EnableWebConfirmDialog(
      onConfirm = {
        confirmingEnableWeb = false
        actions.updateAvailability(availability.withWeb(true))
      },
      onDismiss = { confirmingEnableWeb = false },
    )
  }
  if (confirmingEnableGoogleEarth) {
    EnableGoogleEarthConfirmDialog(
      onConfirm = {
        confirmingEnableGoogleEarth = false
        actions.updateOpenInGoogleEarth(true)
      },
      onDismiss = { confirmingEnableGoogleEarth = false },
    )
  }
  OutlinedCard(modifier = Modifier.fillMaxWidth()) {
    AvailabilityToggleRow(
      icon = previewTargetIcon(FormPreviewTarget.MOBILE),
      title = stringResource(Res.string.formeditor_available_on_mobile),
      onCaption = stringResource(Res.string.formeditor_availability_mobile_on),
      offCaption = stringResource(Res.string.formeditor_availability_mobile_off),
      checked = availability.includesMobile,
      enabled = !uiState.isReadOnly,
      onCheckedChange = { actions.updateAvailability(availability.withMobile(it)) },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    AvailabilityToggleRow(
      icon = previewTargetIcon(FormPreviewTarget.WEB),
      title = stringResource(Res.string.formeditor_available_on_web),
      onCaption = stringResource(Res.string.formeditor_availability_web_on),
      offCaption = stringResource(Res.string.formeditor_availability_web_off),
      checked = availability.includesWeb,
      enabled = !uiState.isReadOnly,
      onCheckedChange = { enabled ->
        if (enabled) {
          confirmingEnableWeb = true
        } else {
          actions.updateAvailability(availability.withWeb(false))
        }
      },
    )
    if (availability.includesWeb && !uiState.isTemplate) {
      GoogleEarthAvailabilityRow(
        uiState = uiState,
        onRequestEnable = { confirmingEnableGoogleEarth = true },
        onDisable = { actions.updateOpenInGoogleEarth(false) },
      )
    }
    AvailabilityCompatibilityRows(uiState)
  }
  if (availability == FormAvailability.NONE) {
    AvailabilityNoneWarning()
  }
}

@Composable
private fun GoogleEarthAvailabilityRow(
  uiState: FormEditorUiState,
  onRequestEnable: () -> Unit,
  onDisable: () -> Unit,
) {
  val canOpenInGoogleEarth = uiState.canOpenInGoogleEarth
  val earthOffCaption =
    when {
      canOpenInGoogleEarth -> stringResource(Res.string.formeditor_availability_google_earth_off)
      uiState.updatesMapLayerByQuestion ->
        stringResource(
          Res.string.formeditor_availability_google_earth_needs_map_selection,
          stringResource(Res.string.formeditor_find_map_feature_by),
          stringResource(Res.string.formeditor_selected_on_map),
        )
      else -> stringResource(Res.string.formeditor_availability_google_earth_unavailable)
    }
  HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
  AvailabilityToggleRow(
    icon = Icons.Outlined.Public,
    title = stringResource(Res.string.formeditor_enable_open_in_google_earth),
    onCaption = stringResource(Res.string.formeditor_availability_google_earth_on),
    offCaption = earthOffCaption,
    checked = canOpenInGoogleEarth && uiState.form.openInGoogleEarth,
    enabled = !uiState.isReadOnly && canOpenInGoogleEarth,
    unavailable = !canOpenInGoogleEarth,
    onCheckedChange = { enabled -> if (enabled) onRequestEnable() else onDisable() },
  )
}

@Composable
private fun AvailabilityCompatibilityRows(uiState: FormEditorUiState) {
  val earthIncompatible = uiState.googleEarthIncompatibleQuestions
  if (earthIncompatible.isNotEmpty()) {
    GoogleEarthIncompatibleQuestionsRow(
      count = earthIncompatible.size,
      modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
    )
  }
}

@Composable
private fun AvailabilityNoneWarning() {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(
      Icons.Outlined.Warning,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.tertiary,
      modifier = Modifier.size(16.dp),
    )
    Spacer(Modifier.width(6.dp))
    Text(
      text = stringResource(Res.string.formeditor_availability_none_warning),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.tertiary,
    )
  }
}

/**
 * Confirmation dialog shown when enabling **Available on web**, warning that some question types
 * aren't supported on web and will not be available.
 */
@Composable
internal fun EnableWebConfirmDialog(
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(Res.string.formeditor_enable_web_confirm_title)) },
    text = { Text(stringResource(Res.string.formeditor_enable_web_confirm_message)) },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text(stringResource(Res.string.formeditor_enable_on_platform))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(Res.string.formeditor_cancel))
      }
    },
  )
}

/**
 * Confirmation dialog shown when enabling **Open in Google Earth**, warning that some question
 * types aren't supported in Google Earth and will not be available.
 */
@Composable
internal fun EnableGoogleEarthConfirmDialog(
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(Res.string.formeditor_enable_google_earth_confirm_title)) },
    text = { Text(stringResource(Res.string.formeditor_enable_google_earth_confirm_message)) },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text(stringResource(Res.string.formeditor_enable_on_platform))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(Res.string.formeditor_cancel))
      }
    },
  )
}

/**
 * Error line under **Enable Open in Google Earth** when [count] questions use field types that
 * cannot be displayed in Google Earth map balloons.
 */
@Composable
internal fun GoogleEarthIncompatibleQuestionsRow(
  count: Int,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      Icons.Outlined.ErrorOutline,
      contentDescription = null,
      tint = colors.error,
      modifier = Modifier.size(16.dp),
    )
    Text(
      text =
        pluralStringResource(
          Res.plurals.formeditor_google_earth_incompatible_questions_summary,
          count,
          count,
        ),
      style = MaterialTheme.typography.bodySmall,
      color = colors.error,
      modifier = Modifier.weight(1f),
    )
  }
}

/** One platform's row in [AvailabilityToggles]: icon, title, effect caption, and switch. */
@Composable
private fun AvailabilityToggleRow(
  icon: ImageVector,
  title: String,
  onCaption: String,
  offCaption: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  enabled: Boolean = true,
  unavailable: Boolean = false,
) {
  val colors = MaterialTheme.colorScheme
  val titleAlpha = if (enabled) 1f else 0.38f
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .toggleable(
          value = checked,
          enabled = enabled,
          role = Role.Switch,
          onValueChange = onCheckedChange,
        )
        .padding(horizontal = 14.dp, vertical = 10.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint =
        when {
          checked -> colors.primary
          !enabled -> colors.onSurfaceVariant.copy(alpha = 0.38f)
          else -> colors.onSurfaceVariant
        },
      modifier = Modifier.size(20.dp),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = colors.onSurface.copy(alpha = titleAlpha),
      )
      Text(
        text = if (checked) onCaption else offCaption,
        style = MaterialTheme.typography.bodySmall,
        color =
          when {
            checked || unavailable -> colors.onSurfaceVariant
            else -> colors.tertiary
          },
      )
    }
    Switch(checked = checked, onCheckedChange = null, enabled = enabled)
  }
}
