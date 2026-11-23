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
package org.openforis.collectone.v2.mobile.ui.permissions

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.ui.common.ProvideAppLocale
import org.openforis.collectone.v2.core.forms.ui.AppTheme
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.mobile.resources.Res
import org.openforis.collectone.v2.mobile.resources.mobile_location_rationale_continue
import org.openforis.collectone.v2.mobile.resources.mobile_location_rationale_message
import org.openforis.collectone.v2.mobile.resources.mobile_location_rationale_not_now
import org.openforis.collectone.v2.mobile.resources.mobile_location_rationale_title
import org.openforis.collectone.v2.mobile.ui.MobileAppState

/**
 * Explains why location questions need the device's location, before a host asks again for the
 * permission the user denied. [onContinue] asks with the system dialog; [onDismiss] doesn't ask.
 *
 * It's a separate window, so it applies the app's language and theme from [state]'s settings.
 */
@Composable
fun LocationPermissionRationaleDialog(
  state: MobileAppState,
  onContinue: () -> Unit,
  onDismiss: () -> Unit,
) {
  val settings by state.viewModels.settings.uiState.collectAsState()
  ProvideAppLocale(settings.languageCode) {
    AppTheme(darkTheme = settings.isDarkTheme) {
      AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.mobile_location_rationale_title)) },
        text = { Text(stringResource(Res.string.mobile_location_rationale_message)) },
        confirmButton = {
          TextButton(onClick = onContinue) {
            Text(stringResource(Res.string.mobile_location_rationale_continue))
          }
        },
        dismissButton = {
          TextButton(onClick = onDismiss) {
            Text(stringResource(Res.string.mobile_location_rationale_not_now))
          }
        },
      )
    }
  }
}
