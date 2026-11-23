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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button as M3Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.ElevatedButton as M3ElevatedButton
import androidx.compose.material3.ExtendedFloatingActionButton as M3ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton as M3FilledIconButton
import androidx.compose.material3.FilledTonalButton as M3FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton as M3FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton as M3FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingActionButtonElevation
import androidx.compose.material3.IconButton as M3IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton as M3IconToggleButton
import androidx.compose.material3.IconToggleButtonColors
import androidx.compose.material3.OutlinedButton as M3OutlinedButton
import androidx.compose.material3.OutlinedIconButton as M3OutlinedIconButton
import androidx.compose.material3.OutlinedIconToggleButton as M3OutlinedIconToggleButton
import androidx.compose.material3.TextButton as M3TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/** Theme-backed [M3Button] applying [interactivePointerIcon] when [enabled]. */
@Composable
fun Button(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  shape: Shape = ButtonDefaults.shape,
  colors: ButtonColors = ButtonDefaults.buttonColors(),
  elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
  border: BorderStroke? = null,
  contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
  interactionSource: MutableInteractionSource? = null,
  content: @Composable RowScope.() -> Unit,
) {
  M3Button(
    onClick = onClick,
    modifier = modifier.interactivePointerIcon(enabled),
    enabled = enabled,
    shape = shape,
    colors = colors,
    elevation = elevation,
    border = border,
    contentPadding = contentPadding,
    interactionSource = interactionSource,
    content = content,
  )
}

/** Theme-backed [M3ElevatedButton] applying [interactivePointerIcon] when [enabled]. */
@Composable
fun ElevatedButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  shape: Shape = ButtonDefaults.elevatedShape,
  colors: ButtonColors = ButtonDefaults.elevatedButtonColors(),
  elevation: ButtonElevation? = ButtonDefaults.elevatedButtonElevation(),
  border: BorderStroke? = null,
  contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
  interactionSource: MutableInteractionSource? = null,
  content: @Composable RowScope.() -> Unit,
) {
  M3ElevatedButton(
    onClick = onClick,
    modifier = modifier.interactivePointerIcon(enabled),
    enabled = enabled,
    shape = shape,
    colors = colors,
    elevation = elevation,
    border = border,
    contentPadding = contentPadding,
    interactionSource = interactionSource,
    content = content,
  )
}

/** Theme-backed [M3FilledTonalButton] applying [interactivePointerIcon] when [enabled]. */
@Composable
fun FilledTonalButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  shape: Shape = ButtonDefaults.filledTonalShape,
  colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
  elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
  border: BorderStroke? = null,
  contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
  interactionSource: MutableInteractionSource? = null,
  content: @Composable RowScope.() -> Unit,
) {
  M3FilledTonalButton(
    onClick = onClick,
    modifier = modifier.interactivePointerIcon(enabled),
    enabled = enabled,
    shape = shape,
    colors = colors,
    elevation = elevation,
    border = border,
    contentPadding = contentPadding,
    interactionSource = interactionSource,
    content = content,
  )
}

/** Theme-backed [M3OutlinedButton] applying [interactivePointerIcon] when [enabled]. */
@Composable
fun OutlinedButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  shape: Shape = ButtonDefaults.outlinedShape,
  colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
  elevation: ButtonElevation? = null,
  border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
  contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
  interactionSource: MutableInteractionSource? = null,
  content: @Composable RowScope.() -> Unit,
) {
  M3OutlinedButton(
    onClick = onClick,
    modifier = modifier.interactivePointerIcon(enabled),
    enabled = enabled,
    shape = shape,
    colors = colors,
    elevation = elevation,
    border = border,
    contentPadding = contentPadding,
    interactionSource = interactionSource,
    content = content,
  )
}

/** Theme-backed [M3TextButton] applying [interactivePointerIcon] when [enabled]. */
@Composable
fun TextButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  shape: Shape = ButtonDefaults.textShape,
  colors: ButtonColors = ButtonDefaults.textButtonColors(),
  elevation: ButtonElevation? = null,
  border: BorderStroke? = null,
  contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
  interactionSource: MutableInteractionSource? = null,
  content: @Composable RowScope.() -> Unit,
) {
  M3TextButton(
    onClick = onClick,
    modifier = modifier.interactivePointerIcon(enabled),
    enabled = enabled,
    shape = shape,
    colors = colors,
    elevation = elevation,
    border = border,
    contentPadding = contentPadding,
    interactionSource = interactionSource,
    content = content,
  )
}

/** Theme-backed [M3IconButton] applying [interactivePointerIcon] when [enabled]. */
@Composable
fun IconButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
  interactionSource: MutableInteractionSource? = null,
  shape: Shape = IconButtonDefaults.standardShape,
  content: @Composable () -> Unit,
) {
  M3IconButton(
    onClick = onClick,
    modifier = modifier.interactivePointerIcon(enabled),
    enabled = enabled,
    colors = colors,
    interactionSource = interactionSource,
    shape = shape,
    content = content,
  )
}

/** Theme-backed [M3IconToggleButton] applying [interactivePointerIcon] when [enabled]. */
@Composable
fun IconToggleButton(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  colors: IconToggleButtonColors = IconButtonDefaults.iconToggleButtonColors(),
  interactionSource: MutableInteractionSource? = null,
  shape: Shape = IconButtonDefaults.standardShape,
  content: @Composable () -> Unit,
) {
  M3IconToggleButton(
    checked = checked,
    onCheckedChange = onCheckedChange,
    modifier = modifier.interactivePointerIcon(enabled),
    enabled = enabled,
    colors = colors,
    interactionSource = interactionSource,
    shape = shape,
    content = content,
  )
}

/** Theme-backed [M3FilledIconButton] applying [interactivePointerIcon] when [enabled]. */
@Composable
fun FilledIconButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  shape: Shape = IconButtonDefaults.filledShape,
  colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(),
  interactionSource: MutableInteractionSource? = null,
  content: @Composable () -> Unit,
) {
  M3FilledIconButton(
    onClick = onClick,
    modifier = modifier.interactivePointerIcon(enabled),
    enabled = enabled,
    shape = shape,
    colors = colors,
    interactionSource = interactionSource,
    content = content,
  )
}

/** Theme-backed [M3FilledTonalIconButton] applying [interactivePointerIcon] when [enabled]. */
@Composable
fun FilledTonalIconButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  shape: Shape = IconButtonDefaults.filledShape,
  colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
  interactionSource: MutableInteractionSource? = null,
  content: @Composable () -> Unit,
) {
  M3FilledTonalIconButton(
    onClick = onClick,
    modifier = modifier.interactivePointerIcon(enabled),
    enabled = enabled,
    shape = shape,
    colors = colors,
    interactionSource = interactionSource,
    content = content,
  )
}

/** Theme-backed [M3OutlinedIconButton] applying [interactivePointerIcon] when [enabled]. */
@Composable
fun OutlinedIconButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  shape: Shape = IconButtonDefaults.outlinedShape,
  colors: IconButtonColors = IconButtonDefaults.outlinedIconButtonColors(),
  border: BorderStroke = IconButtonDefaults.outlinedIconButtonBorder(enabled),
  interactionSource: MutableInteractionSource? = null,
  content: @Composable () -> Unit,
) {
  M3OutlinedIconButton(
    onClick = onClick,
    modifier = modifier.interactivePointerIcon(enabled),
    enabled = enabled,
    shape = shape,
    colors = colors,
    border = border,
    interactionSource = interactionSource,
    content = content,
  )
}

/** Theme-backed [M3OutlinedIconToggleButton] applying [interactivePointerIcon] when [enabled]. */
@Composable
fun OutlinedIconToggleButton(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  shape: Shape = IconButtonDefaults.outlinedShape,
  colors: IconToggleButtonColors = IconButtonDefaults.outlinedIconToggleButtonColors(),
  border: BorderStroke? = IconButtonDefaults.outlinedIconToggleButtonBorder(enabled, checked),
  interactionSource: MutableInteractionSource? = null,
  content: @Composable () -> Unit,
) {
  M3OutlinedIconToggleButton(
    checked = checked,
    onCheckedChange = onCheckedChange,
    modifier = modifier.interactivePointerIcon(enabled),
    enabled = enabled,
    shape = shape,
    colors = colors,
    border = border,
    interactionSource = interactionSource,
    content = content,
  )
}

/** Theme-backed [M3FloatingActionButton] applying [interactivePointerIcon]. */
@Composable
fun FloatingActionButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  shape: Shape = FloatingActionButtonDefaults.shape,
  containerColor: Color = FloatingActionButtonDefaults.containerColor,
  contentColor: Color = contentColorFor(containerColor),
  elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
  interactionSource: MutableInteractionSource? = null,
  content: @Composable () -> Unit,
) {
  M3FloatingActionButton(
    onClick = onClick,
    modifier = modifier.interactivePointerIcon(),
    shape = shape,
    containerColor = containerColor,
    contentColor = contentColor,
    elevation = elevation,
    interactionSource = interactionSource,
    content = content,
  )
}

/**
 * Theme-backed [M3ExtendedFloatingActionButton] (text + icon) applying [interactivePointerIcon].
 */
@Composable
fun ExtendedFloatingActionButton(
  text: @Composable () -> Unit,
  icon: @Composable () -> Unit,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  expanded: Boolean = true,
  shape: Shape = FloatingActionButtonDefaults.extendedFabShape,
  containerColor: Color = FloatingActionButtonDefaults.containerColor,
  contentColor: Color = contentColorFor(containerColor),
  elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
  interactionSource: MutableInteractionSource? = null,
) {
  M3ExtendedFloatingActionButton(
    text = text,
    icon = icon,
    onClick = onClick,
    modifier = modifier.interactivePointerIcon(),
    expanded = expanded,
    shape = shape,
    containerColor = containerColor,
    contentColor = contentColor,
    elevation = elevation,
    interactionSource = interactionSource,
  )
}

/** Theme-backed [M3ExtendedFloatingActionButton] (content) applying [interactivePointerIcon]. */
@Composable
fun ExtendedFloatingActionButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  shape: Shape = FloatingActionButtonDefaults.extendedFabShape,
  containerColor: Color = FloatingActionButtonDefaults.containerColor,
  contentColor: Color = contentColorFor(containerColor),
  elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
  interactionSource: MutableInteractionSource? = null,
  content: @Composable RowScope.() -> Unit,
) {
  M3ExtendedFloatingActionButton(
    onClick = onClick,
    modifier = modifier.interactivePointerIcon(),
    shape = shape,
    containerColor = containerColor,
    contentColor = contentColor,
    elevation = elevation,
    interactionSource = interactionSource,
    content = content,
  )
}
