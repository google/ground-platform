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
import androidx.compose.material3.Card as M3Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.ElevatedCard as M3ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard as M3OutlinedCard
import androidx.compose.material3.Surface as M3Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Static [M3Card] passthrough (leaves hover cursor untouched). */
@Composable
fun Card(
  modifier: Modifier = Modifier,
  shape: Shape = CardDefaults.shape,
  colors: CardColors = CardDefaults.cardColors(),
  elevation: CardElevation = CardDefaults.cardElevation(),
  border: BorderStroke? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  M3Card(
    modifier = modifier,
    shape = shape,
    colors = colors,
    elevation = elevation,
    border = border,
    content = content,
  )
}

/** Clickable [M3Card] applying [interactivePointerIcon] when [enabled]. */
@Composable
fun Card(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  shape: Shape = CardDefaults.shape,
  colors: CardColors = CardDefaults.cardColors(),
  elevation: CardElevation = CardDefaults.cardElevation(),
  border: BorderStroke? = null,
  interactionSource: MutableInteractionSource? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  M3Card(
    onClick = onClick,
    modifier = modifier.interactivePointerIcon(enabled),
    enabled = enabled,
    shape = shape,
    colors = colors,
    elevation = elevation,
    border = border,
    interactionSource = interactionSource,
    content = content,
  )
}

/** Static [M3OutlinedCard] passthrough (leaves hover cursor untouched). */
@Composable
fun OutlinedCard(
  modifier: Modifier = Modifier,
  shape: Shape = CardDefaults.outlinedShape,
  colors: CardColors = CardDefaults.outlinedCardColors(),
  elevation: CardElevation = CardDefaults.outlinedCardElevation(),
  border: BorderStroke = CardDefaults.outlinedCardBorder(),
  content: @Composable ColumnScope.() -> Unit,
) {
  M3OutlinedCard(
    modifier = modifier,
    shape = shape,
    colors = colors,
    elevation = elevation,
    border = border,
    content = content,
  )
}

/** Clickable [M3OutlinedCard] applying [interactivePointerIcon] when [enabled]. */
@Composable
fun OutlinedCard(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  shape: Shape = CardDefaults.outlinedShape,
  colors: CardColors = CardDefaults.outlinedCardColors(),
  elevation: CardElevation = CardDefaults.outlinedCardElevation(),
  border: BorderStroke = CardDefaults.outlinedCardBorder(enabled),
  interactionSource: MutableInteractionSource? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  M3OutlinedCard(
    onClick = onClick,
    modifier = modifier.interactivePointerIcon(enabled),
    enabled = enabled,
    shape = shape,
    colors = colors,
    elevation = elevation,
    border = border,
    interactionSource = interactionSource,
    content = content,
  )
}

/** Static [M3ElevatedCard] passthrough (leaves hover cursor untouched). */
@Composable
fun ElevatedCard(
  modifier: Modifier = Modifier,
  shape: Shape = CardDefaults.elevatedShape,
  colors: CardColors = CardDefaults.elevatedCardColors(),
  elevation: CardElevation = CardDefaults.elevatedCardElevation(),
  content: @Composable ColumnScope.() -> Unit,
) {
  M3ElevatedCard(
    modifier = modifier,
    shape = shape,
    colors = colors,
    elevation = elevation,
    content = content,
  )
}

/** Clickable [M3ElevatedCard] applying [interactivePointerIcon] when [enabled]. */
@Composable
fun ElevatedCard(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  shape: Shape = CardDefaults.elevatedShape,
  colors: CardColors = CardDefaults.elevatedCardColors(),
  elevation: CardElevation = CardDefaults.elevatedCardElevation(),
  interactionSource: MutableInteractionSource? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  M3ElevatedCard(
    onClick = onClick,
    modifier = modifier.interactivePointerIcon(enabled),
    enabled = enabled,
    shape = shape,
    colors = colors,
    elevation = elevation,
    interactionSource = interactionSource,
    content = content,
  )
}

/** Static [M3Surface] passthrough (leaves hover cursor untouched). */
@Composable
fun Surface(
  modifier: Modifier = Modifier,
  shape: Shape = RectangleShape,
  color: Color = MaterialTheme.colorScheme.surface,
  contentColor: Color = contentColorFor(color),
  tonalElevation: Dp = 0.dp,
  shadowElevation: Dp = 0.dp,
  border: BorderStroke? = null,
  content: @Composable () -> Unit,
) {
  M3Surface(
    modifier = modifier,
    shape = shape,
    color = color,
    contentColor = contentColor,
    tonalElevation = tonalElevation,
    shadowElevation = shadowElevation,
    border = border,
    content = content,
  )
}

/** Clickable [M3Surface] applying [interactivePointerIcon] when [enabled]. */
@Composable
fun Surface(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  shape: Shape = RectangleShape,
  color: Color = MaterialTheme.colorScheme.surface,
  contentColor: Color = contentColorFor(color),
  tonalElevation: Dp = 0.dp,
  shadowElevation: Dp = 0.dp,
  border: BorderStroke? = null,
  interactionSource: MutableInteractionSource? = null,
  content: @Composable () -> Unit,
) {
  M3Surface(
    onClick = onClick,
    modifier = modifier.interactivePointerIcon(enabled),
    enabled = enabled,
    shape = shape,
    color = color,
    contentColor = contentColor,
    tonalElevation = tonalElevation,
    shadowElevation = shadowElevation,
    border = border,
    interactionSource = interactionSource,
    content = content,
  )
}

/** Selectable [M3Surface] applying [interactivePointerIcon] when [enabled]. */
@Composable
fun Surface(
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  shape: Shape = RectangleShape,
  color: Color = MaterialTheme.colorScheme.surface,
  contentColor: Color = contentColorFor(color),
  tonalElevation: Dp = 0.dp,
  shadowElevation: Dp = 0.dp,
  border: BorderStroke? = null,
  interactionSource: MutableInteractionSource? = null,
  content: @Composable () -> Unit,
) {
  M3Surface(
    selected = selected,
    onClick = onClick,
    modifier = modifier.interactivePointerIcon(enabled),
    enabled = enabled,
    shape = shape,
    color = color,
    contentColor = contentColor,
    tonalElevation = tonalElevation,
    shadowElevation = shadowElevation,
    border = border,
    interactionSource = interactionSource,
    content = content,
  )
}

/** Toggleable [M3Surface] applying [interactivePointerIcon] when [enabled]. */
@Composable
fun Surface(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  shape: Shape = RectangleShape,
  color: Color = MaterialTheme.colorScheme.surface,
  contentColor: Color = contentColorFor(color),
  tonalElevation: Dp = 0.dp,
  shadowElevation: Dp = 0.dp,
  border: BorderStroke? = null,
  interactionSource: MutableInteractionSource? = null,
  content: @Composable () -> Unit,
) {
  M3Surface(
    checked = checked,
    onCheckedChange = onCheckedChange,
    modifier = modifier.interactivePointerIcon(enabled),
    enabled = enabled,
    shape = shape,
    color = color,
    contentColor = contentColor,
    tonalElevation = tonalElevation,
    shadowElevation = shadowElevation,
    border = border,
    interactionSource = interactionSource,
    content = content,
  )
}
