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
package org.openforis.collectone.v2.app.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddLocationAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.DataSize
import org.openforis.collectone.v2.app.domain.model.ImagerySource
import org.openforis.collectone.v2.app.domain.model.OfflineImageryType
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_basemap
import org.openforis.collectone.v2.app.resources.app_basemap_type_heading
import org.openforis.collectone.v2.app.resources.app_done
import org.openforis.collectone.v2.app.resources.app_downloaded_imagery_heading
import org.openforis.collectone.v2.app.resources.app_downloaded_imagery_none
import org.openforis.collectone.v2.app.resources.app_downloaded_imagery_summary
import org.openforis.collectone.v2.app.resources.app_downloaded_satellite
import org.openforis.collectone.v2.app.resources.app_downloaded_terrain
import org.openforis.collectone.v2.app.resources.app_imagery_heading
import org.openforis.collectone.v2.app.resources.app_offline_download_permitted
import org.openforis.collectone.v2.app.resources.app_online_only_offline_disabled
import org.openforis.collectone.v2.app.resources.app_save_an_area
import org.openforis.collectone.v2.app.ui.common.displayLabel
import org.openforis.collectone.v2.app.ui.text.toUiText
import org.openforis.collectone.v2.core.forms.ui.AlertDialogOverlay
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.core.forms.ui.controls.Switch
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton

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
 * 1. Basemap type options (`Satellite` and `Terrain`)
 * 2. Custom imagery layers (XYZ tile URLs) available for the survey; on mobile ([isMobile]) each
 *    says whether it can be used offline
 * 3. [downloadedImagery], a section only the mobile app fills ([DownloadedImageryLayersSection])
 */
@Composable
fun LayersSelectorContent(
  uiState: SurveyMapUiState,
  actions: SurveyMapActions,
  modifier: Modifier = Modifier,
  isMobile: Boolean = true,
  downloadedImagery: (@Composable () -> Unit)? = null,
) {
  val imagerySources = uiState.availableImagerySources

  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
      BasemapSectionHeading(
        text = stringResource(Res.string.app_basemap_type_heading),
        modifier = Modifier.padding(bottom = 4.dp),
      )
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
        BasemapSectionHeading(text = stringResource(Res.string.app_imagery_heading))
        imagerySources.forEach { source ->
          ImagerySourceLayerCard(
            source = source,
            isEnabled = uiState.isImagerySourceEnabled(source.id),
            isMobile = isMobile,
            onToggle = { actions.toggleImagerySource(source.id) },
          )
        }
      }
    }

    downloadedImagery?.invoke()
  }
}

/**
 * The layers sheet's Downloaded imagery section (mobile): a switch per imagery type that shows its
 * saved areas on the map, with how many there are and what they take ("2 areas · 76 MB"), and a
 * Save an area button when [onSaveArea] is set (devices that can save areas).
 */
@Composable
fun DownloadedImageryLayersSection(
  uiState: SurveyMapUiState,
  actions: SurveyMapActions,
  onSaveArea: (() -> Unit)?,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    BasemapSectionHeading(text = stringResource(Res.string.app_downloaded_imagery_heading))
    OfflineImageryType.entries.forEach { type ->
      DownloadedImageryRow(
        type = type,
        areas = uiState.savedAreas(type).size,
        size = uiState.savedSize(type),
        isVisible = uiState.isDownloadedVisible(type),
        onToggle = { actions.toggleDownloadedImagery(type) },
      )
    }
    if (onSaveArea != null) {
      TextButton(onClick = onSaveArea) {
        Icon(
          imageVector = Icons.Outlined.AddLocationAlt,
          contentDescription = null,
          modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
        Text(stringResource(Res.string.app_save_an_area))
      }
    }
  }
}

@Composable
private fun DownloadedImageryRow(
  type: OfflineImageryType,
  areas: Int,
  size: DataSize,
  isVisible: Boolean,
  onToggle: () -> Unit,
) {
  val hasAreas = areas > 0
  OutlinedCard(
    onClick = onToggle,
    enabled = hasAreas,
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
          text =
            stringResource(
              when (type) {
                OfflineImageryType.SATELLITE -> Res.string.app_downloaded_satellite
                OfflineImageryType.TERRAIN -> Res.string.app_downloaded_terrain
              }
            ),
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text =
            if (hasAreas) {
              pluralStringResource(
                Res.plurals.app_downloaded_imagery_summary,
                areas,
                areas,
                size.toUiText().resolve(),
              )
            } else {
              stringResource(Res.string.app_downloaded_imagery_none)
            },
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Switch(checked = isVisible, onCheckedChange = { onToggle() }, enabled = hasAreas)
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
      stringResource(
        if (source.allowOfflineDownload) Res.string.app_offline_download_permitted
        else Res.string.app_online_only_offline_disabled
      )
    } else {
      source.type.displayLabel()
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
fun LayersControlDialog(uiState: SurveyMapUiState, actions: SurveyMapActions) {
  AlertDialogOverlay(
    onDismissRequest = { actions.updateLayersSheetOpen(false) },
    title = {
      Text(
        text = stringResource(Res.string.app_basemap),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
      )
    },
    text = {
      Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        LayersSelectorContent(uiState = uiState, actions = actions, isMobile = false)
      }
    },
    confirmButton = {
      TextButton(onClick = { actions.updateLayersSheetOpen(false) }) {
        Text(stringResource(Res.string.app_done))
      }
    },
  )
}
