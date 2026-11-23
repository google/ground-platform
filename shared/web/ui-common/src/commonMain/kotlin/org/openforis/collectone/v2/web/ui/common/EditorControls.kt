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
package org.openforis.collectone.v2.web.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openforis.collectone.v2.core.forms.ui.controls.DropdownMenuItem

@Composable
fun SectionLabel(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelSmall,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.primary,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownSelector(
  label: String,
  selectedText: String,
  options: List<T>,
  optionText: @Composable (T) -> String,
  onSelect: (T) -> Unit,
  modifier: Modifier = Modifier,
  optionIcon: ((T) -> ImageVector)? = null,
  optionEnabled: (T) -> Boolean = { true },
  enabled: Boolean = true,
  supportingText: String? = null,
  selectedIcon: ImageVector? = null,
) {
  var expanded by remember { mutableStateOf(false) }
  // Standard M3 exposed dropdown: a read-only OutlinedTextField, so the label and value use the
  // same typography as every other input in the editors.
  ExposedDropdownMenuBox(
    expanded = expanded && enabled,
    onExpandedChange = { if (enabled) expanded = it },
    modifier = modifier.fillMaxWidth(),
  ) {
    Box {
      OutlinedTextField(
        value = selectedText,
        onValueChange = {},
        readOnly = true,
        enabled = enabled,
        singleLine = true,
        label = { Text(label) },
        leadingIcon = selectedIcon?.let { icon -> { DropdownOptionIcon(icon, enabled = true) } },
        supportingText = supportingText?.let { text -> { Text(text) } },
        trailingIcon = {
          ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled)
        },
        modifier =
          Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
      )
      // Opens the menu on the first click, even if the mouse moves slightly during it.
      DropdownFieldClickTarget(
        expanded = expanded && enabled,
        onExpandedChange = { expanded = it },
        enabled = enabled,
      )
    }
    ExposedDropdownMenu(
      expanded = expanded && enabled,
      onDismissRequest = { expanded = false },
    ) {
      options.forEach { option ->
        val itemEnabled = optionEnabled(option)
        DropdownMenuItem(
          text = { Text(optionText(option), maxLines = 1, overflow = TextOverflow.Ellipsis) },
          leadingIcon =
            optionIcon?.invoke(option)?.let { icon -> { DropdownOptionIcon(icon, itemEnabled) } },
          enabled = itemEnabled,
          onClick = {
            onSelect(option)
            expanded = false
          },
        )
      }
    }
  }
}

@Composable
private fun DropdownOptionIcon(icon: ImageVector, enabled: Boolean) {
  Icon(
    icon,
    contentDescription = null,
    modifier = Modifier.size(18.dp),
    tint =
      if (enabled) {
        MaterialTheme.colorScheme.primary
      } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
      },
  )
}
