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
package org.openforis.collectone.v2.web.ui.formeditor

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.ui.text.labelRes
import org.openforis.collectone.v2.core.forms.ui.controls.DropdownMenuItem
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.formeditor_add_menu_section
import org.openforis.collectone.v2.web.resources.formeditor_repeated_section

/**
 * Question types that cannot be added to the current form (for example, geometry and media types
 * when **Open in Google Earth** is enabled).
 */
internal val LocalDisabledQuestionTypes = compositionLocalOf { emptySet<EditorQuestionType>() }

/** Provides which [EditorQuestionType]s are compatible with the active form's enabled variants. */
internal val LocalQuestionTypeAvailability =
  compositionLocalOf<(EditorQuestionType) -> Boolean> { { true } }

/** Dropdown menu listing all question types with their icons for insertion at [atIndex]. */
@Composable
internal fun AddQuestionMenu(
  expanded: Boolean,
  onDismiss: () -> Unit,
  onAdd: (EditorQuestionType, Int?) -> Unit,
  atIndex: Int? = null,
  disabledTypes: Set<EditorQuestionType> = LocalDisabledQuestionTypes.current,
  onAddSection: ((Int?) -> Unit)? = null,
  onAddRepeatedSection: ((Int?) -> Unit)? = null,
  isQuestionTypeEnabled: (EditorQuestionType) -> Boolean = LocalQuestionTypeAvailability.current,
) {
  DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
    EditorQuestionType.entries.forEach { type ->
      val enabled = type !in disabledTypes && isQuestionTypeEnabled(type)
      DropdownMenuItem(
        text = { Text(stringResource(type.labelRes)) },
        enabled = enabled,
        leadingIcon = {
          Icon(
            questionTypeIcon(type),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint =
              if (enabled) {
                MaterialTheme.colorScheme.primary
              } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
              },
          )
        },
        onClick = {
          onAdd(type, atIndex)
          onDismiss()
        },
      )
    }
    if (onAddSection != null || onAddRepeatedSection != null) {
      HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
      if (onAddSection != null) {
        DropdownMenuItem(
          text = { Text(stringResource(Res.string.formeditor_add_menu_section)) },
          leadingIcon = {
            Icon(
              Icons.Outlined.Layers,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
              tint = MaterialTheme.colorScheme.secondary,
            )
          },
          onClick = {
            onAddSection(atIndex)
            onDismiss()
          },
        )
      }
      if (onAddRepeatedSection != null) {
        DropdownMenuItem(
          text = { Text(stringResource(Res.string.formeditor_repeated_section)) },
          leadingIcon = {
            Icon(
              Icons.Outlined.Repeat,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
              tint = MaterialTheme.colorScheme.primary,
            )
          },
          onClick = {
            onAddRepeatedSection(atIndex)
            onDismiss()
          },
        )
      }
    }
  }
}
