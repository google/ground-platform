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
package org.openforis.collectone.v2.mobile.ui.version

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.ui.common.formatAppVersionBadge
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.ElevatedCard
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.core.version.MobileUpdateStatus
import org.openforis.collectone.v2.mobile.resources.Res
import org.openforis.collectone.v2.mobile.resources.mobile_update_available_body
import org.openforis.collectone.v2.mobile.resources.mobile_update_available_title
import org.openforis.collectone.v2.mobile.resources.mobile_update_check_again
import org.openforis.collectone.v2.mobile.resources.mobile_update_later
import org.openforis.collectone.v2.mobile.resources.mobile_update_now
import org.openforis.collectone.v2.mobile.resources.mobile_update_required_body
import org.openforis.collectone.v2.mobile.resources.mobile_update_required_title

/** Dismissible Material 3 banner shown when the server advertises a newer mobile client version. */
@Composable
fun MobileUpdateSuggestedBanner(
  status: MobileUpdateStatus.Suggested,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val uriHandler = LocalUriHandler.current
  val versionLabel = formatAppVersionBadge(status.latestVersion).ifEmpty { status.latestVersion }
  ElevatedCard(
    modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    colors =
      CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
      ),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          imageVector = Icons.Outlined.SystemUpdate,
          contentDescription = null,
          modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(Res.string.mobile_update_available_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = stringResource(Res.string.mobile_update_available_body, versionLabel),
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(onClick = onDismiss) {
          Text(stringResource(Res.string.mobile_update_later))
        }
        if (status.updateUrl.isNotBlank()) {
          Button(onClick = { uriHandler.openUri(status.updateUrl) }) {
            Text(stringResource(Res.string.mobile_update_now))
          }
        }
      }
    }
  }
}

/**
 * Full-screen blocking Material 3 surface shown when the server requires a newer mobile client
 * version than this build. Cannot be dismissed; the user must update or re-check once updated.
 */
@Composable
fun MobileUpdateRequiredScreen(
  status: MobileUpdateStatus.Required,
  onCheckAgain: () -> Unit,
  isChecking: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val uriHandler = LocalUriHandler.current
  val currentLabel = formatAppVersionBadge(status.currentVersion).ifEmpty { status.currentVersion }
  val requiredLabel =
    formatAppVersionBadge(status.minRequiredVersion).ifEmpty { status.minRequiredVersion }
  Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
    Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Icon(
        imageVector = Icons.Outlined.SystemUpdate,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(56.dp),
      )
      Spacer(modifier = Modifier.height(20.dp))
      Text(
        text = stringResource(Res.string.mobile_update_required_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
      )
      Spacer(modifier = Modifier.height(12.dp))
      Text(
        text = stringResource(Res.string.mobile_update_required_body, currentLabel, requiredLabel),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = 360.dp),
      )
      Spacer(modifier = Modifier.height(28.dp))
      if (status.updateUrl.isNotBlank()) {
        Button(
          onClick = { uriHandler.openUri(status.updateUrl) },
          modifier = Modifier.fillMaxWidth().widthIn(max = 280.dp),
        ) {
          Text(stringResource(Res.string.mobile_update_now))
        }
        Spacer(modifier = Modifier.height(12.dp))
      }
      OutlinedButton(
        onClick = onCheckAgain,
        enabled = !isChecking,
        modifier = Modifier.fillMaxWidth().widthIn(max = 280.dp),
      ) {
        if (isChecking) {
          CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
          Text(stringResource(Res.string.mobile_update_check_again))
        }
      }
    }
  }
}
