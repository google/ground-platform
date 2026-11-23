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
package org.openforis.collectone.v2.web.ui.timeseries

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.selectable
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.web_time_series_picker_selection

/** One choice of a [TimeSeriesPicker]. */
internal data class PickerOption<T>(
  val value: T,
  val label: String,
  val detail: String,
  val icon: ImageVector,
  val color: Color,
  /** Heading of the menu section this option starts, if any. */
  val sectionHeading: String? = null,
)

/**
 * A compact dropdown of the time series panel: an outlined button with the selected option's
 * colored icon and name, which opens a menu of every option with its icon, name, and a one-line
 * explanation.
 */
@Composable
internal fun <T> TimeSeriesPicker(
  title: String,
  options: List<PickerOption<T>>,
  selected: T,
  onSelect: (T) -> Unit,
  modifier: Modifier = Modifier,
) {
  val current = options.firstOrNull { it.value == selected } ?: options.firstOrNull() ?: return
  var expanded by remember { mutableStateOf(false) }
  var wasExpanded by remember { mutableStateOf(false) }
  val anchorFocus = remember { FocusRequester() }
  // Once the menu is gone, give focus back to its button rather than the first control on screen.
  LaunchedEffect(expanded) {
    if (expanded) {
      wasExpanded = true
    } else if (wasExpanded) {
      wasExpanded = false
      anchorFocus.requestFocus()
    }
  }
  val selection = stringResource(Res.string.web_time_series_picker_selection, title, current.label)
  Box(modifier = modifier) {
    OutlinedButton(
      onClick = { expanded = true },
      modifier =
        Modifier.height(32.dp).focusRequester(anchorFocus).semantics {
          role = Role.DropdownList
          contentDescription = selection
        },
      contentPadding = PaddingValues(start = 5.dp, end = 4.dp),
    ) {
      PickerBadge(current.icon, current.color, size = 22.dp)
      Spacer(modifier = Modifier.width(6.dp))
      Text(text = current.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
      Icon(
        imageVector = Icons.Outlined.ArrowDropDown,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
      )
    }
    DropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
      modifier = Modifier.widthIn(min = 240.dp),
    ) {
      options.forEachIndexed { index, option ->
        option.sectionHeading?.let { heading ->
          if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
          Text(
            text = heading,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
              Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 2.dp).semantics {
                heading()
              },
          )
        }
        PickerRow(option = option, isSelected = option.value == current.value) {
          expanded = false
          onSelect(option.value)
        }
      }
    }
  }
}

@Composable
private fun <T> PickerRow(option: PickerOption<T>, isSelected: Boolean, onClick: () -> Unit) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .background(
          if (isSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
          else Color.Transparent
        )
        .selectable(selected = isSelected, onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    PickerBadge(option.icon, option.color, size = 32.dp)
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = option.label,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface,
      )
      if (option.detail.isNotEmpty()) {
        Text(
          text = option.detail,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Icon(
      imageVector = Icons.Outlined.Check,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(18.dp).alpha(if (isSelected) 1f else 0f),
    )
  }
}

/** [icon] in [color] on a tinted circle: the visual key of a band or imagery source. */
@Composable
internal fun PickerBadge(icon: ImageVector, color: Color, size: Dp, modifier: Modifier = Modifier) {
  Box(
    modifier = modifier.size(size).background(color.copy(alpha = 0.16f), CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = color,
      modifier = Modifier.size(size * 0.62f),
    )
  }
}
