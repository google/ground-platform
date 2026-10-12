/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.app.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.groundplatform.v2.app.domain.model.ImagerySource
import org.groundplatform.v2.app.ui.state.SurveyMapUiState
import org.groundplatform.v2.app.ui.viewmodel.SurveyMapActions
import org.groundplatform.v2.core.forms.ui.GroundAlertDialogOverlay

/** Small primary-colored section heading in the basemap selector (`Type`, ...). */
@Composable
private fun BasemapSectionHeading(text: String, modifier: Modifier = Modifier) {
  Text(
    text = text,
    style =
      MaterialTheme.typography.labelSmall.copy(
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 0.5.sp,
      ),
    modifier = modifier,
  )
}

/**
 * Shared body of the basemap selector, used by the mobile layers sheet (`LayersControlSheet`) and
 * the web dashboard's dialog ([LayersControlDialog]). Map layer visibility is not set here: mobile
 * toggles it in the bottom sheet and the web dashboard in its left-hand panel.
 * 1. Basemap type options (`Satellite` and `Map`)
 * 2. Custom imagery layers (XYZ tile URLs) available for the survey
 * 3. Downloaded (offline) basemap overlay toggle, when [showOfflineBasemap] (mobile only)
 */
@Composable
internal fun LayersSelectorContent(
  uiState: SurveyMapUiState,
  actions: SurveyMapActions,
  modifier: Modifier = Modifier,
  showOfflineBasemap: Boolean = true,
) {
  val imagerySources = uiState.availableImagerySources

  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
      BasemapSectionHeading(text = "Type", modifier = Modifier.padding(bottom = 4.dp))
      BasemapOptionOrder.forEach { basemap ->
        BasemapOptionRow(
          type = basemap,
          isSelected = uiState.selectedBasemapType == basemap,
          onSelect = { actions.selectBasemapType(basemap) },
        )
      }
    }

    if (imagerySources.isNotEmpty()) {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasemapSectionHeading(text = "Imagery")
        imagerySources.forEach { source ->
          ImagerySourceLayerCard(
            source = source,
            isEnabled = uiState.isImagerySourceEnabled(source.id),
            isMobile = showOfflineBasemap,
            onToggle = { actions.toggleImagerySource(source.id) },
          )
        }
      }
    }

    if (showOfflineBasemap) {
      BasemapSectionHeading(text = "Downloaded basemaps")
      // Offline Basemap Tile Package Overlay Toggle
      OutlinedCard(
        onClick = { actions.toggleOfflineBasemapVisibility() },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors =
          CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
          ),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Offline basemap overlay (Nyeri sector tiles)",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = uiState.offlineBasemapStyle.tileDescription,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = uiState.isOfflineBasemapVisible,
            onCheckedChange = { actions.toggleOfflineBasemapVisibility() },
          )
        }
      }
    }
  }
}

@Composable
private fun ImagerySourceLayerCard(
  source: ImagerySource,
  isEnabled: Boolean,
  isMobile: Boolean,
  onToggle: () -> Unit,
) {
  val subtitle =
    if (isMobile) {
      if (source.allowOfflineDownload) "Offline download permitted"
      else "Online only · offline download disabled"
    } else {
      source.type.label
    }
  OutlinedCard(
    onClick = onToggle,
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    colors =
      CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = source.name,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Switch(checked = isEnabled, onCheckedChange = { onToggle() })
    }
  }
}

/**
 * Modal dialog opened by the web dashboard's [BasemapPreviewCard] to select the basemap type (`Map`
 * vs `Satellite`) and toggle imagery layers. Offline maps are a mobile-only feature, so their
 * toggle is left out.
 */
@Composable
internal fun LayersControlDialog(uiState: SurveyMapUiState, actions: SurveyMapActions) {
  GroundAlertDialogOverlay(
    onDismissRequest = { actions.updateLayersSheetOpen(false) },
    title = {
      Text(
        text = "Basemap",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
      )
    },
    text = {
      Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        LayersSelectorContent(uiState = uiState, actions = actions, showOfflineBasemap = false)
      }
    },
    confirmButton = {
      TextButton(onClick = { actions.updateLayersSheetOpen(false) }) { Text("Done") }
    },
  )
}
