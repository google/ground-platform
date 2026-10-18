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
package org.groundplatform.v2.web.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.groundplatform.v2.app.domain.model.LANGUAGE_OPTIONS

/**
 * Compact toolbar language dropdown showing the current language with a globe icon and dropdown
 * arrow.
 */
@Composable
fun WebLanguageSelector(
  currentLanguageCode: String,
  onLanguageChange: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }
  val currentOption =
    LANGUAGE_OPTIONS.firstOrNull { it.code == currentLanguageCode } ?: LANGUAGE_OPTIONS.first()

  Box(modifier = modifier) {
    OutlinedButton(
      onClick = { expanded = true },
      modifier = Modifier.height(36.dp),
      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    ) {
      Icon(
        Icons.Outlined.Language,
        contentDescription = "Language",
        modifier = Modifier.size(16.dp),
      )
      Spacer(Modifier.width(6.dp))
      Text(
        text = currentOption.label,
        style = MaterialTheme.typography.labelMedium,
      )
      Spacer(Modifier.width(4.dp))
      Icon(
        Icons.Outlined.ArrowDropDown,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
      )
    }
    DropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
    ) {
      LANGUAGE_OPTIONS.forEach { option ->
        val selected = option.code == currentLanguageCode
        DropdownMenuItem(
          text = {
            Text(
              text = option.label,
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
          },
          leadingIcon = {
            if (selected) {
              Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
              )
            } else {
              Spacer(Modifier.size(18.dp))
            }
          },
          onClick = {
            onLanguageChange(option.code)
            expanded = false
          },
        )
      }
    }
  }
}
