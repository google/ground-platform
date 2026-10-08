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
package org.groundplatform.v2.devtools.prototypeapp.ui.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A [FilterChip] tuned for the prototype's web and desktop targets.
 *
 * Material 3 chips raise their elevation while hovered. On the web (Skiko/wasm) renderer that
 * shadow is painted beneath the chip's transparent container and shows through as a solid grey
 * pill, hiding the outline and clashing with the translucent hover state layer. This wrapper keeps
 * the chip flat in every interaction state and gives unselected chips an opaque surface container
 * so hover feedback is limited to the standard state layer on all platforms.
 */
@Composable
fun GroundFilterChip(
  selected: Boolean,
  onClick: () -> Unit,
  label: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
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
    colors =
      FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface),
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
