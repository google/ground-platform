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
package org.groundplatform.v2.devtools.prototypeapp.surveyeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconToggleButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.groundplatform.v2.devtools.prototypeapp.map.LayerIcons
import org.groundplatform.v2.devtools.prototypeapp.map.contentColorOnArgb

/**
 * Picker for the icon shown inside a Point layer's map pins: a preview of the pin's icon on the
 * [layerColor], and a grid of the [LayerIcons] library plus a "No icon" option. [selectedIconName]
 * is `null` when no icon is chosen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LayerIconPicker(
  selectedIconName: String?,
  layerColor: Color,
  onSelect: (String?) -> Unit,
  modifier: Modifier = Modifier,
) {
  val selected = LayerIcons.forName(selectedIconName)
  val onLayerColor = Color(contentColorOnArgb(layerColor.toArgb().toLong() and 0xFFFFFFFFL))
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Box(
        modifier = Modifier.size(28.dp).background(layerColor, CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        if (selected != null) {
          Icon(
            selected.vector,
            contentDescription = null,
            tint = onLayerColor,
            modifier = Modifier.size(18.dp),
          )
        }
      }
      Text(
        text = "Pin icon: ${selected?.label ?: "None"}",
        style = MaterialTheme.typography.bodySmall,
      )
    }
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      IconChoice(
        vector = Icons.Outlined.Block,
        label = "No icon",
        checked = selected == null,
        layerColor = layerColor,
        onLayerColor = onLayerColor,
        onClick = { onSelect(null) },
      )
      LayerIcons.all.forEach { icon ->
        IconChoice(
          vector = icon.vector,
          label = icon.label,
          checked = icon == selected,
          layerColor = layerColor,
          onLayerColor = onLayerColor,
          onClick = { onSelect(icon.name) },
        )
      }
    }
  }
}

@Composable
private fun IconChoice(
  vector: ImageVector,
  label: String,
  checked: Boolean,
  layerColor: Color,
  onLayerColor: Color,
  onClick: () -> Unit,
) {
  OutlinedIconToggleButton(
    checked = checked,
    onCheckedChange = { onClick() },
    colors =
      IconButtonDefaults.outlinedIconToggleButtonColors(
        checkedContainerColor = layerColor,
        checkedContentColor = onLayerColor,
      ),
    modifier = Modifier.size(36.dp).semantics { contentDescription = label },
  ) {
    Icon(vector, contentDescription = null, modifier = Modifier.size(20.dp))
  }
}
