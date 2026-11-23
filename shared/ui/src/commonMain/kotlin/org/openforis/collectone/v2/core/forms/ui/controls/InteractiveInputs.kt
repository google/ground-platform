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
package org.openforis.collectone.v2.core.forms.ui.controls

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip as M3AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Checkbox as M3Checkbox
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ChipColors
import androidx.compose.material3.ChipElevation
import androidx.compose.material3.DropdownMenuItem as M3DropdownMenuItem
import androidx.compose.material3.FilterChip as M3FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.InputChip as M3InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.MultiChoiceSegmentedButtonRowScope
import androidx.compose.material3.NavigationDrawerItem as M3NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemColors
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.RadioButton as M3RadioButton
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SegmentedButton as M3SegmentedButton
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.SelectableChipElevation
import androidx.compose.material3.SingleChoiceSegmentedButtonRowScope
import androidx.compose.material3.Slider as M3Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch as M3Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab as M3Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/** Theme-backed [M3AssistChip] applying [interactivePointerIcon] when [enabled]. */
@Composable
fun AssistChip(
  onClick: () -> Unit,
  label: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  leadingIcon: @Composable (() -> Unit)? = null,
  trailingIcon: @Composable (() -> Unit)? = null,
  shape: Shape = AssistChipDefaults.shape,
  colors: ChipColors = AssistChipDefaults.assistChipColors(),
  elevation: ChipElevation? = AssistChipDefaults.assistChipElevation(),
  border: BorderStroke? = AssistChipDefaults.assistChipBorder(enabled),
  interactionSource: MutableInteractionSource? = null,
) {
  M3AssistChip(
    onClick = onClick,
    label = label,
    modifier = modifier.interactivePointerIcon(enabled),
    enabled = enabled,
    leadingIcon = leadingIcon,
    trailingIcon = trailingIcon,
    shape = shape,
    colors = colors,
    elevation = elevation,
    border = border,
    interactionSource = interactionSource,
  )
}

/** Theme-backed [M3FilterChip] applying [interactivePointerIcon] when [enabled]. */
@Composable
fun FilterChip(
  selected: Boolean,
  onClick: () -> Unit,
  label: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  leadingIcon: @Composable (() -> Unit)? = null,
  trailingIcon: @Composable (() -> Unit)? = null,
  shape: Shape = FilterChipDefaults.shape,
  colors: SelectableChipColors = FilterChipDefaults.filterChipColors(),
  elevation: SelectableChipElevation? = FilterChipDefaults.filterChipElevation(),
  border: BorderStroke? = FilterChipDefaults.filterChipBorder(enabled, selected),
  interactionSource: MutableInteractionSource? = null,
) {
  M3FilterChip(
    selected = selected,
    onClick = onClick,
    label = label,
    modifier = modifier.interactivePointerIcon(enabled),
    enabled = enabled,
    leadingIcon = leadingIcon,
    trailingIcon = trailingIcon,
    shape = shape,
    colors = colors,
    elevation = elevation,
    border = border,
    interactionSource = interactionSource,
  )
}

/** Theme-backed [M3InputChip] applying [interactivePointerIcon] when [enabled]. */
@Composable
fun InputChip(
  selected: Boolean,
  onClick: () -> Unit,
  label: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  leadingIcon: @Composable (() -> Unit)? = null,
  avatar: @Composable (() -> Unit)? = null,
  trailingIcon: @Composable (() -> Unit)? = null,
  shape: Shape = InputChipDefaults.shape,
  colors: SelectableChipColors = InputChipDefaults.inputChipColors(),
  elevation: SelectableChipElevation? = InputChipDefaults.inputChipElevation(),
  border: BorderStroke? = InputChipDefaults.inputChipBorder(enabled, selected),
  interactionSource: MutableInteractionSource? = null,
) {
  M3InputChip(
    selected = selected,
    onClick = onClick,
    label = label,
    modifier = modifier.interactivePointerIcon(enabled),
    enabled = enabled,
    leadingIcon = leadingIcon,
    avatar = avatar,
    trailingIcon = trailingIcon,
    shape = shape,
    colors = colors,
    elevation = elevation,
    border = border,
    interactionSource = interactionSource,
  )
}

/** Theme-backed [M3DropdownMenuItem] applying [interactivePointerIcon] when [enabled]. */
@Composable
fun DropdownMenuItem(
  text: @Composable () -> Unit,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  leadingIcon: @Composable (() -> Unit)? = null,
  trailingIcon: @Composable (() -> Unit)? = null,
  enabled: Boolean = true,
  colors: MenuItemColors = MenuDefaults.itemColors(),
  contentPadding: PaddingValues = MenuDefaults.DropdownMenuItemContentPadding,
  interactionSource: MutableInteractionSource? = null,
) {
  M3DropdownMenuItem(
    text = text,
    onClick = onClick,
    modifier = modifier.interactivePointerIcon(enabled),
    leadingIcon = leadingIcon,
    trailingIcon = trailingIcon,
    enabled = enabled,
    colors = colors,
    contentPadding = contentPadding,
    interactionSource = interactionSource,
  )
}

/**
 * Theme-backed [M3Checkbox] applying [interactivePointerIcon] when [onCheckedChange] is non-null.
 * When [onCheckedChange] is `null` (for example inside a `toggleable` row), the modifier is left
 * untouched so the checkbox inherits the enclosing row's hover cursor.
 */
@Composable
fun Checkbox(
  checked: Boolean,
  onCheckedChange: ((Boolean) -> Unit)?,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  colors: CheckboxColors = CheckboxDefaults.colors(),
  interactionSource: MutableInteractionSource? = null,
) {
  M3Checkbox(
    checked = checked,
    onCheckedChange = onCheckedChange,
    modifier = if (onCheckedChange != null) modifier.interactivePointerIcon(enabled) else modifier,
    enabled = enabled,
    colors = colors,
    interactionSource = interactionSource,
  )
}

/**
 * Theme-backed [M3RadioButton] applying [interactivePointerIcon] when [onClick] is non-null. When
 * [onClick] is `null` (for example inside a `selectable` row), the modifier is left untouched so
 * the radio button inherits the enclosing row's hover cursor.
 */
@Composable
fun RadioButton(
  selected: Boolean,
  onClick: (() -> Unit)?,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  colors: RadioButtonColors = RadioButtonDefaults.colors(),
  interactionSource: MutableInteractionSource? = null,
) {
  M3RadioButton(
    selected = selected,
    onClick = onClick,
    modifier = if (onClick != null) modifier.interactivePointerIcon(enabled) else modifier,
    enabled = enabled,
    colors = colors,
    interactionSource = interactionSource,
  )
}

/**
 * Theme-backed [M3Switch] applying [interactivePointerIcon] when [onCheckedChange] is non-null.
 * When [onCheckedChange] is `null` (for example inside a `toggleable` row), the modifier is left
 * untouched so the switch inherits the enclosing row's hover cursor.
 */
@Composable
fun Switch(
  checked: Boolean,
  onCheckedChange: ((Boolean) -> Unit)?,
  modifier: Modifier = Modifier,
  thumbContent: (@Composable () -> Unit)? = null,
  enabled: Boolean = true,
  colors: SwitchColors = SwitchDefaults.colors(),
  interactionSource: MutableInteractionSource? = null,
) {
  M3Switch(
    checked = checked,
    onCheckedChange = onCheckedChange,
    modifier = if (onCheckedChange != null) modifier.interactivePointerIcon(enabled) else modifier,
    thumbContent = thumbContent,
    enabled = enabled,
    colors = colors,
    interactionSource = interactionSource,
  )
}

/** Theme-backed [M3Tab] (text / icon) applying [interactivePointerIcon] when [enabled]. */
@Composable
fun Tab(
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  text: @Composable (() -> Unit)? = null,
  icon: @Composable (() -> Unit)? = null,
  selectedContentColor: Color = LocalContentColor.current,
  unselectedContentColor: Color = selectedContentColor,
  interactionSource: MutableInteractionSource? = null,
) {
  M3Tab(
    selected = selected,
    onClick = onClick,
    modifier = modifier.interactivePointerIcon(enabled),
    enabled = enabled,
    text = text,
    icon = icon,
    selectedContentColor = selectedContentColor,
    unselectedContentColor = unselectedContentColor,
    interactionSource = interactionSource,
  )
}

/** Theme-backed [M3Tab] (custom content) applying [interactivePointerIcon] when [enabled]. */
@Composable
fun Tab(
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  selectedContentColor: Color = LocalContentColor.current,
  unselectedContentColor: Color = selectedContentColor,
  interactionSource: MutableInteractionSource? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  M3Tab(
    selected = selected,
    onClick = onClick,
    modifier = modifier.interactivePointerIcon(enabled),
    enabled = enabled,
    selectedContentColor = selectedContentColor,
    unselectedContentColor = unselectedContentColor,
    interactionSource = interactionSource,
    content = content,
  )
}

/**
 * Theme-backed single-choice [M3SegmentedButton] applying [interactivePointerIcon] when [enabled].
 */
@Composable
fun SingleChoiceSegmentedButtonRowScope.SegmentedButton(
  selected: Boolean,
  onClick: () -> Unit,
  shape: Shape,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  colors: SegmentedButtonColors = SegmentedButtonDefaults.colors(),
  border: BorderStroke =
    SegmentedButtonDefaults.borderStroke(colors.resolvedBorderColor(enabled, selected)),
  contentPadding: PaddingValues = SegmentedButtonDefaults.ContentPadding,
  interactionSource: MutableInteractionSource? = null,
  icon: @Composable () -> Unit = { SegmentedButtonDefaults.Icon(selected) },
  label: @Composable () -> Unit,
) {
  M3SegmentedButton(
    selected = selected,
    onClick = onClick,
    shape = shape,
    modifier = modifier.interactivePointerIcon(enabled),
    enabled = enabled,
    colors = colors,
    border = border,
    contentPadding = contentPadding,
    interactionSource = interactionSource,
    icon = icon,
    label = label,
  )
}

/**
 * Theme-backed multi-choice [M3SegmentedButton] applying [interactivePointerIcon] when [enabled].
 */
@Composable
fun MultiChoiceSegmentedButtonRowScope.SegmentedButton(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  shape: Shape,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  colors: SegmentedButtonColors = SegmentedButtonDefaults.colors(),
  border: BorderStroke =
    SegmentedButtonDefaults.borderStroke(colors.resolvedBorderColor(enabled, checked)),
  contentPadding: PaddingValues = SegmentedButtonDefaults.ContentPadding,
  interactionSource: MutableInteractionSource? = null,
  icon: @Composable () -> Unit = { SegmentedButtonDefaults.Icon(checked) },
  label: @Composable () -> Unit,
) {
  M3SegmentedButton(
    checked = checked,
    onCheckedChange = onCheckedChange,
    shape = shape,
    modifier = modifier.interactivePointerIcon(enabled),
    enabled = enabled,
    colors = colors,
    border = border,
    contentPadding = contentPadding,
    interactionSource = interactionSource,
    icon = icon,
    label = label,
  )
}

/** Theme-backed [M3NavigationDrawerItem] applying [interactivePointerIcon]. */
@Composable
fun NavigationDrawerItem(
  label: @Composable () -> Unit,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: (@Composable () -> Unit)? = null,
  badge: (@Composable () -> Unit)? = null,
  shape: Shape = CircleShape,
  colors: NavigationDrawerItemColors = NavigationDrawerItemDefaults.colors(),
  interactionSource: MutableInteractionSource? = null,
) {
  M3NavigationDrawerItem(
    label = label,
    selected = selected,
    onClick = onClick,
    modifier = modifier.interactivePointerIcon(),
    icon = icon,
    badge = badge,
    shape = shape,
    colors = colors,
    interactionSource = interactionSource,
  )
}

/** Theme-backed [M3Slider] applying [interactivePointerIcon] when [enabled]. */
@Composable
fun Slider(
  value: Float,
  onValueChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
  steps: Int = 0,
  onValueChangeFinished: (() -> Unit)? = null,
  colors: SliderColors = SliderDefaults.colors(),
  interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
  M3Slider(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier.interactivePointerIcon(enabled),
    enabled = enabled,
    valueRange = valueRange,
    steps = steps,
    onValueChangeFinished = onValueChangeFinished,
    colors = colors,
    interactionSource = interactionSource,
  )
}

private fun SegmentedButtonColors.resolvedBorderColor(enabled: Boolean, active: Boolean): Color =
  when {
    enabled && active -> activeBorderColor
    enabled && !active -> inactiveBorderColor
    !enabled && active -> disabledActiveBorderColor
    else -> disabledInactiveBorderColor
  }
