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
package org.openforis.collectone.v2.mobile.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.resources.Res as AppRes
import org.openforis.collectone.v2.app.resources.app_back_to_details
import org.openforis.collectone.v2.app.resources.app_back_to_submissions
import org.openforis.collectone.v2.app.resources.app_history
import org.openforis.collectone.v2.app.ui.datacollection.EntityDetailsActions
import org.openforis.collectone.v2.app.ui.datacollection.EntityDetailsUiState
import org.openforis.collectone.v2.app.ui.datacollection.EntitySubmissionsPane
import org.openforis.collectone.v2.app.ui.datacollection.SubmissionFullDetailsCard
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface

/**
 * The history of the selected map feature as a screen of its own: the submissions collected for it
 * and, once one is opened, that submission. It covers the Main Survey's map, sheet, and search bar.
 *
 * Back goes one level at a time, from a submission to the list and from the list to the feature's
 * properties in the sheet, as the system Back gesture does. [topInset] is the height of the status
 * bar the screen draws behind.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeatureHistoryScreen(
  uiState: EntityDetailsUiState,
  actions: EntityDetailsActions,
  topInset: Dp,
  modifier: Modifier = Modifier,
) {
  val openSubmission = uiState.openSubmission
  val onBack = {
    if (openSubmission != null) actions.selectSubmissionDetail(null)
    else actions.showEntityProperties()
  }
  Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
    Column(modifier = Modifier.padding(top = topInset)) {
      TopAppBar(
        title = {
          Column {
            Text(text = stringResource(AppRes.string.app_history))
            Text(
              text = uiState.entity.displayTitle,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
              contentDescription =
                stringResource(
                  if (openSubmission != null) AppRes.string.app_back_to_submissions
                  else AppRes.string.app_back_to_details
                ),
            )
          }
        },
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors =
          TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
      )
      Column(
        modifier =
          Modifier.weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (openSubmission != null) {
          SubmissionFullDetailsCard(
            uiState = openSubmission,
            backLabel = stringResource(AppRes.string.app_back_to_submissions),
            onBack = onBack,
            onSharePdf = { actions.shareSubmissionPdf(openSubmission.submission.id) },
            onCycleSyncStatus = {
              actions.cycleSubmissionSyncStatus(openSubmission.submission.id)
            },
            showBackButton = false,
          )
        } else {
          EntitySubmissionsPane(uiState = uiState, actions = actions, isWeb = false)
        }
      }
    }
  }
}
