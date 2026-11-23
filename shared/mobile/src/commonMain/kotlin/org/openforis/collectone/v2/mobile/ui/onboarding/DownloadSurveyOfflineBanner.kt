/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.mobile.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.mobile.resources.Res
import org.openforis.collectone.v2.mobile.resources.mobile_download_survey_offline_banner

/** Test tag of [DownloadSurveyOfflineBanner]. */
internal const val DOWNLOAD_SURVEY_OFFLINE_BANNER_TAG = "downloadSurveyOfflineBanner"

/**
 * Tells the user on Download survey that the device isn't connected, so they need to connect to
 * download surveys for offline use. Screen readers announce it when it appears.
 */
@Composable
internal fun DownloadSurveyOfflineBanner(modifier: Modifier = Modifier) {
  Surface(
    shape = MaterialTheme.shapes.small,
    color = MaterialTheme.colorScheme.errorContainer,
    contentColor = MaterialTheme.colorScheme.onErrorContainer,
    modifier =
      modifier.fillMaxWidth().testTag(DOWNLOAD_SURVEY_OFFLINE_BANNER_TAG).semantics(
        mergeDescendants = true
      ) {
        liveRegion = LiveRegionMode.Polite
      },
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // Decorative: the text says the device is offline.
      Icon(
        imageVector = Icons.Outlined.CloudOff,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
      )
      Text(
        text = stringResource(Res.string.mobile_download_survey_offline_banner),
        style = MaterialTheme.typography.bodySmall,
      )
    }
  }
}
