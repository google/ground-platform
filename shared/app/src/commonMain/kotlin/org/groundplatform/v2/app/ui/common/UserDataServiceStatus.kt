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
package org.groundplatform.v2.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.groundplatform.v2.app.di.UserDataSyncState
import org.groundplatform.v2.app.di.missingUserDataServiceMessage
import org.groundplatform.v2.app.di.platformUserDataServiceUrl
import org.groundplatform.v2.core.forms.ui.GroundTheme

/** Test tag of [UserDataSyncErrorBanner]. */
const val USER_DATA_SYNC_ERROR_BANNER_TAG = "user-data-sync-error-banner"

/**
 * Starts the app with [content] when a user data service is configured for this platform
 * ([platformUserDataServiceUrl]), passing its URL. Otherwise the app doesn't start: this shows
 * [MissingUserDataServiceScreen] and logs the setup instructions.
 */
@Composable
fun RequireUserDataService(content: @Composable (serviceUrl: String) -> Unit) {
  val serviceUrl = remember { platformUserDataServiceUrl() }
  if (serviceUrl != null) {
    content(serviceUrl)
    return
  }
  LaunchedEffect(Unit) { println(missingUserDataServiceMessage()) }
  GroundTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
      MissingUserDataServiceScreen()
    }
  }
}

/**
 * Shown instead of the app when no user data service is configured, since the app has no data to
 * show without one.
 */
@Composable
fun MissingUserDataServiceScreen(
  modifier: Modifier = Modifier,
  message: String = missingUserDataServiceMessage(),
) {
  Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
    Column(
      modifier = Modifier.widthIn(max = 560.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "The app can't start",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.error,
      )
      Text(text = message, style = MaterialTheme.typography.bodyLarge)
    }
  }
}

/**
 * Reports a failed sync from the user data service ([UserDataSyncState.Failed]) with a Retry
 * action. Shows nothing in any other state.
 */
@Composable
fun UserDataSyncErrorBanner(
  state: UserDataSyncState,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (state !is UserDataSyncState.Failed) return
  Surface(
    modifier =
      modifier
        .padding(12.dp)
        .widthIn(max = 720.dp)
        .fillMaxWidth()
        .testTag(USER_DATA_SYNC_ERROR_BANNER_TAG),
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.errorContainer,
    contentColor = MaterialTheme.colorScheme.onErrorContainer,
    shadowElevation = 4.dp,
  ) {
    Row(
      modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = "Couldn't load data from the user data service",
          style = MaterialTheme.typography.titleSmall,
        )
        Text(text = state.message, style = MaterialTheme.typography.bodySmall)
      }
      TextButton(onClick = onRetry) { Text("Retry") }
    }
  }
}

/** Shows an inline loading spinner when data is synchronizing with the remote user data service. */
@Composable
fun UserDataSyncSpinner(
  state: UserDataSyncState,
  modifier: Modifier = Modifier,
) {
  if (state !is UserDataSyncState.Syncing) return
  CircularProgressIndicator(
    modifier = modifier.size(14.dp),
    strokeWidth = 2.dp,
    color = MaterialTheme.colorScheme.primary,
  )
}
