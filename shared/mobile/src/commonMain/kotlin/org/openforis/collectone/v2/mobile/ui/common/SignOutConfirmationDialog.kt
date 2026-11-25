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
package org.openforis.collectone.v2.mobile.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.core.forms.ui.AlertDialogOverlay
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.mobile.resources.Res
import org.openforis.collectone.v2.mobile.resources.mobile_cancel
import org.openforis.collectone.v2.mobile.resources.mobile_sign_out
import org.openforis.collectone.v2.mobile.resources.mobile_sign_out_anyway
import org.openforis.collectone.v2.mobile.resources.mobile_sign_out_continue
import org.openforis.collectone.v2.mobile.resources.mobile_sign_out_dialog_title
import org.openforis.collectone.v2.mobile.resources.mobile_sign_out_offline_confirm_body
import org.openforis.collectone.v2.mobile.resources.mobile_sign_out_offline_confirm_title
import org.openforis.collectone.v2.mobile.resources.mobile_sign_out_warning_body

/** Steps of the mobile sign-out confirmation flow. */
enum class SignOutConfirmationStep {
  NONE,
  WARNING,
  OFFLINE_SECOND_CONFIRM,
}

/**
 * Confirmation dialog shown before signing out of the mobile app. Warns that signing back in
 * requires an internet connection and that signing out while offline may lock the user out of the
 * app; when [isOnline] is false, asks a second time to be sure.
 */
@Composable
fun SignOutConfirmationDialog(
  step: SignOutConfirmationStep,
  isOnline: Boolean,
  onStepChange: (SignOutConfirmationStep) -> Unit,
  onConfirmSignOut: () -> Unit,
) {
  if (step == SignOutConfirmationStep.NONE) return

  val isSecondConfirm = step == SignOutConfirmationStep.OFFLINE_SECOND_CONFIRM
  val icon = if (isSecondConfirm) Icons.Outlined.CloudOff else Icons.AutoMirrored.Outlined.Logout
  val title =
    if (isSecondConfirm) {
      stringResource(Res.string.mobile_sign_out_offline_confirm_title)
    } else {
      stringResource(Res.string.mobile_sign_out_dialog_title)
    }
  val body =
    if (isSecondConfirm) {
      stringResource(Res.string.mobile_sign_out_offline_confirm_body)
    } else {
      stringResource(Res.string.mobile_sign_out_warning_body)
    }
  val confirmLabel =
    when {
      isSecondConfirm -> stringResource(Res.string.mobile_sign_out_anyway)
      !isOnline -> stringResource(Res.string.mobile_sign_out_continue)
      else -> stringResource(Res.string.mobile_sign_out)
    }

  AlertDialogOverlay(
    onDismissRequest = { onStepChange(SignOutConfirmationStep.NONE) },
    icon = {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
      )
    },
    title = {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
    },
    text = {
      Text(
        text = body,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    },
    confirmButton = {
      Button(
        onClick = {
          if (!isOnline && !isSecondConfirm) {
            onStepChange(SignOutConfirmationStep.OFFLINE_SECOND_CONFIRM)
          } else {
            onStepChange(SignOutConfirmationStep.NONE)
            onConfirmSignOut()
          }
        },
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
      ) {
        Text(confirmLabel, fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      OutlinedButton(onClick = { onStepChange(SignOutConfirmationStep.NONE) }) {
        Text(stringResource(Res.string.mobile_cancel))
      }
    },
  )
}
