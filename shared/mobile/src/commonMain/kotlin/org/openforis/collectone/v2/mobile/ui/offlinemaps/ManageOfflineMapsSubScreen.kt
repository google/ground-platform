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
package org.openforis.collectone.v2.mobile.ui.offlinemaps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddLocationAlt
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SatelliteAlt
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.DataSize
import org.openforis.collectone.v2.app.domain.model.OfflineAreaLayer
import org.openforis.collectone.v2.app.domain.model.OfflineAreaLayerStatus
import org.openforis.collectone.v2.app.domain.model.OfflineImageryType
import org.openforis.collectone.v2.app.domain.model.OfflineMapArea
import org.openforis.collectone.v2.app.resources.Res as AppRes
import org.openforis.collectone.v2.app.resources.app_save_an_area
import org.openforis.collectone.v2.app.ui.text.labelRes
import org.openforis.collectone.v2.app.ui.text.mediumDateText
import org.openforis.collectone.v2.app.ui.text.toUiText
import org.openforis.collectone.v2.core.forms.ui.AlertDialogOverlay
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.DropdownMenuItem
import org.openforis.collectone.v2.core.forms.ui.controls.ExtendedFloatingActionButton
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.map.LngLatBounds
import org.openforis.collectone.v2.mobile.resources.Res
import org.openforis.collectone.v2.mobile.resources.mobile_back
import org.openforis.collectone.v2.mobile.resources.mobile_cancel
import org.openforis.collectone.v2.mobile.resources.mobile_offline_maps_area_options
import org.openforis.collectone.v2.mobile.resources.mobile_offline_maps_area_summary
import org.openforis.collectone.v2.mobile.resources.mobile_offline_maps_empty_body
import org.openforis.collectone.v2.mobile.resources.mobile_offline_maps_empty_title
import org.openforis.collectone.v2.mobile.resources.mobile_offline_maps_layer_failed
import org.openforis.collectone.v2.mobile.resources.mobile_offline_maps_layer_options
import org.openforis.collectone.v2.mobile.resources.mobile_offline_maps_layer_paused
import org.openforis.collectone.v2.mobile.resources.mobile_offline_maps_layer_percent
import org.openforis.collectone.v2.mobile.resources.mobile_offline_maps_remove_area
import org.openforis.collectone.v2.mobile.resources.mobile_offline_maps_remove_area_body
import org.openforis.collectone.v2.mobile.resources.mobile_offline_maps_remove_area_title
import org.openforis.collectone.v2.mobile.resources.mobile_offline_maps_remove_layer_body
import org.openforis.collectone.v2.mobile.resources.mobile_offline_maps_remove_satellite
import org.openforis.collectone.v2.mobile.resources.mobile_offline_maps_remove_satellite_title
import org.openforis.collectone.v2.mobile.resources.mobile_offline_maps_remove_terrain
import org.openforis.collectone.v2.mobile.resources.mobile_offline_maps_remove_terrain_title
import org.openforis.collectone.v2.mobile.resources.mobile_offline_maps_show_on_map
import org.openforis.collectone.v2.mobile.resources.mobile_offline_maps_simulated_banner
import org.openforis.collectone.v2.mobile.resources.mobile_offline_maps_simulated_label
import org.openforis.collectone.v2.mobile.resources.mobile_offline_maps_title
import org.openforis.collectone.v2.mobile.resources.mobile_resume
import org.openforis.collectone.v2.mobile.resources.mobile_try_again
import org.openforis.collectone.v2.mobile.ui.settings.DeviceStorageBreakdownCard

/**
 * Offline maps, a drawer page: the map areas saved on this device for offline use, each with a row
 * per imagery layer and its progress, the measured device storage, and Save an area.
 *
 * @param onSaveArea starts framing a new area on the main map.
 * @param onShowOnMap returns to the main map, framing a saved area's bounds.
 */
@Composable
internal fun ManageOfflineMapsSubScreen(
  viewModel: OfflineMapsViewModel,
  onBack: () -> Unit,
  onSaveArea: () -> Unit,
  onShowOnMap: (LngLatBounds) -> Unit,
) {
  val uiState by viewModel.uiState.collectAsState()
  ManageOfflineMapsSubScreen(
    uiState = uiState,
    actions = viewModel,
    onBack = onBack,
    onSaveArea = onSaveArea,
    onShowOnMap = onShowOnMap,
  )
}

/** Stateless Offline maps, rendering [uiState] and forwarding intents to [actions]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManageOfflineMapsSubScreen(
  uiState: OfflineMapsUiState,
  actions: OfflineMapsActions,
  onBack: () -> Unit,
  onSaveArea: () -> Unit,
  onShowOnMap: (LngLatBounds) -> Unit,
) {
  Scaffold(
    containerColor = MaterialTheme.colorScheme.surface,
    contentWindowInsets = WindowInsets(0),
    topBar = {
      TopAppBar(
        title = { Text(stringResource(Res.string.mobile_offline_maps_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
              contentDescription = stringResource(Res.string.mobile_back),
            )
          }
        },
        windowInsets = WindowInsets(0),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
      )
    },
    floatingActionButton = {
      if (uiState.canSaveAreas && uiState.areas.isNotEmpty()) {
        ExtendedFloatingActionButton(
          onClick = onSaveArea,
          icon = { Icon(Icons.Outlined.AddLocationAlt, contentDescription = null) },
          text = { Text(stringResource(AppRes.string.app_save_an_area)) },
        )
      }
    },
  ) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (uiState.isSimulated) {
        item { SimulatedNotice(stringResource(Res.string.mobile_offline_maps_simulated_banner)) }
      }
      uiState.storage?.let { storage -> item { DeviceStorageBreakdownCard(storage = storage) } }
      if (uiState.areas.isEmpty()) {
        item { EmptyOfflineMaps(onSaveArea = onSaveArea.takeIf { uiState.canSaveAreas }) }
      }
      items(uiState.areas, key = { it.id }) { area ->
        OfflineAreaCard(
          area = area,
          isSimulated = uiState.isSimulated,
          actions = actions,
          onShowOnMap = { onShowOnMap(area.bounds) },
        )
      }
    }
  }
  uiState.pendingRemovalArea?.let { area ->
    when (val removal = uiState.pendingRemoval) {
      is PendingOfflineRemoval.Area -> RemoveAreaDialog(area, actions)
      is PendingOfflineRemoval.Layer -> RemoveLayerDialog(area, removal.type, actions)
      null -> Unit
    }
  }
}

/** No saved areas yet; on devices that can save them, what saving one does and Save an area. */
@Composable
private fun EmptyOfflineMaps(onSaveArea: (() -> Unit)?) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Icon(
      imageVector = Icons.Outlined.CloudOff,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(40.dp),
    )
    Text(
      text = stringResource(Res.string.mobile_offline_maps_empty_title),
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
      textAlign = TextAlign.Center,
    )
    if (onSaveArea != null) {
      Text(
        text = stringResource(Res.string.mobile_offline_maps_empty_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
      Button(onClick = onSaveArea) {
        Icon(
          imageVector = Icons.Outlined.AddLocationAlt,
          contentDescription = null,
          modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
        Text(stringResource(AppRes.string.app_save_an_area))
      }
    }
  }
}

/** A saved area: its name, when it was saved, what it takes, and a row per layer. */
@Composable
private fun OfflineAreaCard(
  area: OfflineMapArea,
  isSimulated: Boolean,
  actions: OfflineMapsActions,
  onShowOnMap: () -> Unit,
) {
  val name = offlineAreaNameText(area.name).resolve()
  OutlinedCard(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = name,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text =
            stringResource(
              Res.string.mobile_offline_maps_area_summary,
              mediumDateText(area.createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).date)
                .resolve(),
              area.totalSize.toUiText().resolve(),
            ),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (isSimulated) SimulatedLabel()
      OverflowMenu(
        contentDescription = stringResource(Res.string.mobile_offline_maps_area_options, name),
        items =
          listOf(
            Res.string.mobile_offline_maps_show_on_map to onShowOnMap,
            Res.string.mobile_offline_maps_remove_area to { actions.promptRemoveArea(area.id) },
          ),
      )
    }
    HorizontalDivider(
      modifier = Modifier.padding(top = 8.dp),
      color = MaterialTheme.colorScheme.outlineVariant,
    )
    area.layers.forEach { layer -> OfflineLayerRow(area.id, layer, actions) }
  }
}

/** One imagery layer of a saved area: its size, its progress, or why it stopped. */
@Composable
private fun OfflineLayerRow(areaId: String, layer: OfflineAreaLayer, actions: OfflineMapsActions) {
  val typeLabel = stringResource(layer.type.labelRes)
  Column(
    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, end = 4.dp, bottom = 4.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector =
          when (layer.type) {
            OfflineImageryType.SATELLITE -> Icons.Outlined.SatelliteAlt
            OfflineImageryType.TERRAIN -> Icons.Outlined.Terrain
          },
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
      )
      Text(
        text = typeLabel,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 12.dp).weight(1f),
      )
      LayerStatus(layer)
      when (layer.status) {
        OfflineAreaLayerStatus.SAVING ->
          TextButton(onClick = { actions.cancelLayer(areaId, layer.type) }) {
            Text(stringResource(Res.string.mobile_cancel))
          }
        OfflineAreaLayerStatus.PAUSED ->
          TextButton(onClick = { actions.resumeLayer(areaId, layer.type) }) {
            Text(stringResource(Res.string.mobile_resume))
          }
        OfflineAreaLayerStatus.FAILED ->
          TextButton(onClick = { actions.resumeLayer(areaId, layer.type) }) {
            Text(stringResource(Res.string.mobile_try_again))
          }
        OfflineAreaLayerStatus.READY -> Unit
      }
      if (layer.status != OfflineAreaLayerStatus.SAVING) {
        OverflowMenu(
          contentDescription =
            stringResource(Res.string.mobile_offline_maps_layer_options, typeLabel),
          items =
            listOf(layer.type.removeLabelRes to { actions.promptRemoveLayer(areaId, layer.type) }),
        )
      }
    }
    if (layer.status == OfflineAreaLayerStatus.SAVING) {
      LinearProgressIndicator(
        progress = { layer.progress },
        modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 12.dp, bottom = 8.dp),
      )
    }
  }
}

/** A layer's size when saved, its percentage while saving, or Paused / Couldn't save. */
@Composable
private fun LayerStatus(layer: OfflineAreaLayer) {
  val (text, color) =
    when (layer.status) {
      OfflineAreaLayerStatus.READY ->
        (layer.size ?: layer.savedSize).toUiText().resolve() to
          MaterialTheme.colorScheme.onSurfaceVariant
      OfflineAreaLayerStatus.SAVING ->
        stringResource(
          Res.string.mobile_offline_maps_layer_percent,
          (layer.progress * 100).roundToInt(),
        ) to MaterialTheme.colorScheme.onSurfaceVariant
      OfflineAreaLayerStatus.PAUSED ->
        stringResource(Res.string.mobile_offline_maps_layer_paused) to
          MaterialTheme.colorScheme.onSurfaceVariant
      OfflineAreaLayerStatus.FAILED ->
        stringResource(Res.string.mobile_offline_maps_layer_failed) to
          MaterialTheme.colorScheme.error
    }
  Text(text = text, style = MaterialTheme.typography.labelLarge, color = color)
  if (layer.status == OfflineAreaLayerStatus.READY) Spacer(modifier = Modifier.width(4.dp))
}

/** The "Simulated" label of areas saved in the browser, where downloads are only simulated. */
@Composable
private fun SimulatedLabel() {
  Surface(
    color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    shape = MaterialTheme.shapes.small,
  ) {
    Text(
      text = stringResource(Res.string.mobile_offline_maps_simulated_label),
      style = MaterialTheme.typography.labelSmall,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )
  }
}

/** A ⋮ button opening a menu of [items], each a label and what it does. */
@Composable
private fun OverflowMenu(
  contentDescription: String,
  items: List<Pair<StringResource, () -> Unit>>,
) {
  var expanded by remember { mutableStateOf(false) }
  IconButton(onClick = { expanded = true }) {
    Icon(imageVector = Icons.Outlined.MoreVert, contentDescription = contentDescription)
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      items.forEach { (label, onClick) ->
        DropdownMenuItem(
          text = { Text(stringResource(label)) },
          onClick = {
            expanded = false
            onClick()
          },
        )
      }
    }
  }
}

/** "Remove satellite" or "Remove terrain". */
private val OfflineImageryType.removeLabelRes: StringResource
  get() =
    when (this) {
      OfflineImageryType.SATELLITE -> Res.string.mobile_offline_maps_remove_satellite
      OfflineImageryType.TERRAIN -> Res.string.mobile_offline_maps_remove_terrain
    }

@Composable
private fun RemoveAreaDialog(area: OfflineMapArea, actions: OfflineMapsActions) {
  RemovalDialog(
    title = stringResource(Res.string.mobile_offline_maps_remove_area_title),
    body =
      stringResource(
        Res.string.mobile_offline_maps_remove_area_body,
        offlineAreaNameText(area.name).resolve(),
        area.totalSize.toUiText().resolve(),
      ),
    confirm = stringResource(Res.string.mobile_offline_maps_remove_area),
    actions = actions,
  )
}

@Composable
private fun RemoveLayerDialog(
  area: OfflineMapArea,
  type: OfflineImageryType,
  actions: OfflineMapsActions,
) {
  val layer = area.layer(type) ?: return
  RemovalDialog(
    title =
      stringResource(
        when (type) {
          OfflineImageryType.SATELLITE -> Res.string.mobile_offline_maps_remove_satellite_title
          OfflineImageryType.TERRAIN -> Res.string.mobile_offline_maps_remove_terrain_title
        },
        offlineAreaNameText(area.name).resolve(),
      ),
    body =
      stringResource(
        Res.string.mobile_offline_maps_remove_layer_body,
        (layer.size ?: DataSize.ZERO).toUiText().resolve(),
      ),
    confirm = stringResource(type.removeLabelRes),
    actions = actions,
  )
}

/** Asks before removing an area or a layer: [title], [body], and the [confirm] button. */
@Composable
private fun RemovalDialog(
  title: String,
  body: String,
  confirm: String,
  actions: OfflineMapsActions,
) {
  AlertDialogOverlay(
    onDismissRequest = actions::dismissRemoval,
    title = { Text(text = title, style = MaterialTheme.typography.titleMedium) },
    text = {
      Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    },
    confirmButton = {
      Button(
        onClick = actions::confirmRemoval,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
      ) {
        Text(confirm)
      }
    },
    dismissButton = {
      OutlinedButton(onClick = actions::dismissRemoval) {
        Text(stringResource(Res.string.mobile_cancel))
      }
    },
  )
}
