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
package org.openforis.collectone.v2.mobile.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.ui.map.BasemapPreviewCard
import org.openforis.collectone.v2.app.ui.map.DownloadedImageryLayersSection
import org.openforis.collectone.v2.app.ui.map.LayersSelectorContent
import org.openforis.collectone.v2.app.ui.map.SurveyMapActions
import org.openforis.collectone.v2.app.ui.map.SurveyMapUiState
import org.openforis.collectone.v2.core.forms.ui.ModalBottomSheetOverlay
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.mobile.resources.Res
import org.openforis.collectone.v2.mobile.resources.mobile_basemap_subtitle
import org.openforis.collectone.v2.mobile.resources.mobile_basemap_title
import org.openforis.collectone.v2.mobile.resources.mobile_close_layers

/**
 * Material 3 modal bottom sheet opened by the mobile map's [BasemapPreviewCard] to select the
 * basemap (Satellite or Terrain), toggle imagery layers and the map areas saved for offline use,
 * and start saving another area ([onSaveArea]), on devices that can save areas.
 */
@Composable
internal fun LayersControlSheet(
  uiState: SurveyMapUiState,
  actions: SurveyMapActions,
  onSaveArea: () -> Unit,
) {
  ModalBottomSheetOverlay(onDismissRequest = { actions.updateLayersSheetOpen(false) }) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(
            text = stringResource(Res.string.mobile_basemap_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = stringResource(Res.string.mobile_basemap_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(onClick = { actions.updateLayersSheetOpen(false) }) {
          Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = stringResource(Res.string.mobile_close_layers),
          )
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      LayersSelectorContent(
        uiState = uiState,
        actions = actions,
        downloadedImagery =
          if (uiState.canSaveMapAreas) {
            { DownloadedImageryLayersSection(uiState, actions, onSaveArea = onSaveArea) }
          } else {
            null
          },
      )

      Spacer(modifier = Modifier.height(12.dp))
    }
  }
}
