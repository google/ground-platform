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
package org.groundplatform.v2.devtools.prototypeapp

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Eye / eye-with-slash button that shows or hides a map layer on the map. Shared by the web
 * dashboard's left-hand panel, the mobile searchable list, and the mobile `Layers` sheet.
 */
@Composable
internal fun LayerVisibilityToggle(
  isVisible: Boolean,
  onToggle: () -> Unit,
  modifier: Modifier = Modifier,
) {
  IconButton(onClick = onToggle, modifier = modifier.size(36.dp)) {
    Icon(
      imageVector = if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
      contentDescription = if (isVisible) "Hide layer" else "Show layer",
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(20.dp),
    )
  }
}

/** Shows or hides [layer] on the map via [PrototypeAppState.toggleLayerVisibility]. */
@Composable
internal fun LayerVisibilityToggle(
  layer: MapLayerItem,
  state: PrototypeAppState,
  modifier: Modifier = Modifier,
) {
  LayerVisibilityToggle(
    isVisible = layer.isVisible,
    onToggle = { state.toggleLayerVisibility(layer.id) },
    modifier = modifier,
  )
}

/** Text color of a map layer's name: dimmed while the layer is hidden on the map. */
@Composable
internal fun layerNameColor(isVisible: Boolean): Color =
  if (isVisible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
