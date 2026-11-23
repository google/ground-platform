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
package org.openforis.collectone.v2.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipElevation
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openforis.collectone.v2.core.forms.ui.controls.FilterChip
import org.openforis.collectone.v2.core.forms.ui.controls.Surface

/**
 * A [FilterChip] tuned for the web and desktop targets.
 *
 * Material 3 chips raise their elevation while hovered. On the web (Skiko/wasm) renderer that
 * shadow is painted beneath the chip's transparent container and shows through as a solid grey
 * pill, hiding the outline and clashing with the translucent hover state layer. This wrapper keeps
 * the chip flat in every interaction state and defaults unselected chips to [containerColor] so
 * hover feedback is limited to the standard state layer on all platforms.
 */
@Composable
fun FlatFilterChip(
  selected: Boolean,
  onClick: () -> Unit,
  label: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  containerColor: Color = MaterialTheme.colorScheme.surface,
  leadingIcon: @Composable (() -> Unit)? = null,
  trailingIcon: @Composable (() -> Unit)? = null,
  interactionSource: MutableInteractionSource? = null,
) {
  FilterChip(
    selected = selected,
    onClick = onClick,
    label = label,
    modifier = modifier,
    enabled = enabled,
    leadingIcon = leadingIcon,
    trailingIcon = trailingIcon,
    colors = FilterChipDefaults.filterChipColors(containerColor = containerColor),
    elevation = flatChipElevation(),
    border = FilterChipDefaults.filterChipBorder(enabled = enabled, selected = selected),
    interactionSource = interactionSource,
  )
}

/** Elevation that stays flat across idle, pressed, focused, hovered, and dragged states. */
@Composable
private fun flatChipElevation(): SelectableChipElevation =
  FilterChipDefaults.filterChipElevation(
    elevation = 0.dp,
    pressedElevation = 0.dp,
    focusedElevation = 0.dp,
    hoveredElevation = 0.dp,
    draggedElevation = 0.dp,
    disabledElevation = 0.dp,
  )

/**
 * Smaller flat selectable filter chip for dense inline filter bars (such as per-dataset status
 * filters beneath a map layer or data table header).
 *
 * Suppresses Material 3's 48 dp minimum interactive footprint and Skiko/Wasm hover elevation
 * shadows while preserving selectable `Surface` semantics (`selected` state and click actions).
 */
@Composable
fun CompactFilterChip(
  selected: Boolean,
  onClick: () -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  interactionSource: MutableInteractionSource? = null,
) {
  val containerColor =
    if (selected) {
      MaterialTheme.colorScheme.secondaryContainer
    } else {
      MaterialTheme.colorScheme.surface
    }
  val contentColor =
    if (selected) {
      MaterialTheme.colorScheme.onSecondaryContainer
    } else {
      MaterialTheme.colorScheme.onSurfaceVariant
    }
  val border =
    BorderStroke(
      width = 1.dp,
      color =
        if (selected) {
          MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        } else {
          MaterialTheme.colorScheme.outlineVariant
        },
    )
  CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
    Surface(
      selected = selected,
      onClick = onClick,
      modifier = modifier,
      enabled = enabled,
      shape = RoundedCornerShape(6.dp),
      color = containerColor,
      contentColor = contentColor,
      border = border,
      shadowElevation = 0.dp,
      tonalElevation = 0.dp,
      interactionSource = interactionSource,
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = label,
          style =
            MaterialTheme.typography.labelSmall.copy(
              fontSize = 11.sp,
              lineHeight = 14.sp,
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
          maxLines = 1,
          softWrap = false,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}
