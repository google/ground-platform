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

import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable as foundationClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable as foundationSelectable
import androidx.compose.foundation.selection.toggleable as foundationToggleable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import org.openforis.collectone.v2.core.forms.ui.LocalInteractivePointerIcon

/**
 * Applies the theme's interactive hover cursor ([LocalInteractivePointerIcon], defaulting to
 * [PointerIcon.Hand]) when [enabled] is `true`, and [PointerIcon.Default] when disabled so a
 * disabled control inside an interactive container does not inherit a pointing hand.
 */
@Composable
fun Modifier.interactivePointerIcon(enabled: Boolean = true): Modifier {
  val icon = if (enabled) LocalInteractivePointerIcon.current else PointerIcon.Default
  return pointerHoverIcon(icon)
}

/**
 * Drop-in wrapper over [androidx.compose.foundation.clickable] that applies
 * [interactivePointerIcon] to enabled click targets.
 */
@Composable
fun Modifier.clickable(
  enabled: Boolean = true,
  onClickLabel: String? = null,
  role: Role? = null,
  onClick: () -> Unit,
): Modifier =
  interactivePointerIcon(enabled)
    .foundationClickable(
      enabled = enabled,
      onClickLabel = onClickLabel,
      role = role,
      onClick = onClick,
    )

/**
 * Drop-in wrapper over [androidx.compose.foundation.clickable] with explicit [interactionSource]
 * and [indication] that applies [interactivePointerIcon] to enabled click targets.
 */
@Composable
fun Modifier.clickable(
  interactionSource: MutableInteractionSource?,
  indication: Indication?,
  enabled: Boolean = true,
  onClickLabel: String? = null,
  role: Role? = null,
  onClick: () -> Unit,
): Modifier =
  interactivePointerIcon(enabled)
    .foundationClickable(
      interactionSource = interactionSource,
      indication = indication,
      enabled = enabled,
      onClickLabel = onClickLabel,
      role = role,
      onClick = onClick,
    )

/**
 * Drop-in wrapper over [androidx.compose.foundation.selection.selectable] that applies
 * [interactivePointerIcon] to enabled selectable targets.
 */
@Composable
fun Modifier.selectable(
  selected: Boolean,
  enabled: Boolean = true,
  role: Role? = null,
  onClick: () -> Unit,
): Modifier =
  interactivePointerIcon(enabled)
    .foundationSelectable(selected = selected, enabled = enabled, role = role, onClick = onClick)

/**
 * Drop-in wrapper over [androidx.compose.foundation.selection.selectable] with explicit
 * [interactionSource] and [indication] that applies [interactivePointerIcon] to enabled targets.
 */
@Composable
fun Modifier.selectable(
  selected: Boolean,
  interactionSource: MutableInteractionSource?,
  indication: Indication?,
  enabled: Boolean = true,
  role: Role? = null,
  onClick: () -> Unit,
): Modifier =
  interactivePointerIcon(enabled)
    .foundationSelectable(
      selected = selected,
      interactionSource = interactionSource,
      indication = indication,
      enabled = enabled,
      role = role,
      onClick = onClick,
    )

/**
 * Drop-in wrapper over [androidx.compose.foundation.selection.toggleable] that applies
 * [interactivePointerIcon] to enabled toggleable targets.
 */
@Composable
fun Modifier.toggleable(
  value: Boolean,
  enabled: Boolean = true,
  role: Role? = null,
  onValueChange: (Boolean) -> Unit,
): Modifier =
  interactivePointerIcon(enabled)
    .foundationToggleable(
      value = value,
      enabled = enabled,
      role = role,
      onValueChange = onValueChange,
    )

/**
 * Drop-in wrapper over [androidx.compose.foundation.selection.toggleable] with explicit
 * [interactionSource] and [indication] that applies [interactivePointerIcon] to enabled targets.
 */
@Composable
fun Modifier.toggleable(
  value: Boolean,
  interactionSource: MutableInteractionSource?,
  indication: Indication?,
  enabled: Boolean = true,
  role: Role? = null,
  onValueChange: (Boolean) -> Unit,
): Modifier =
  interactivePointerIcon(enabled)
    .foundationToggleable(
      value = value,
      interactionSource = interactionSource,
      indication = indication,
      enabled = enabled,
      role = role,
      onValueChange = onValueChange,
    )

/**
 * Click catcher that intentionally leaves the mouse hover cursor unchanged. Use for full-screen
 * modal dismiss scrims and canvas backgrounds where clicking empty space dismisses or deselects
 * without presenting the surface as a button.
 */
fun Modifier.backdropClickable(
  enabled: Boolean = true,
  onClickLabel: String? = null,
  role: Role? = null,
  onClick: () -> Unit,
): Modifier =
  foundationClickable(
    enabled = enabled,
    onClickLabel = onClickLabel,
    role = role,
    onClick = onClick,
  )

/**
 * Click catcher with explicit [interactionSource] and [indication] that intentionally leaves the
 * mouse hover cursor unchanged (see [backdropClickable]).
 */
fun Modifier.backdropClickable(
  interactionSource: MutableInteractionSource?,
  indication: Indication?,
  enabled: Boolean = true,
  onClickLabel: String? = null,
  role: Role? = null,
  onClick: () -> Unit,
): Modifier =
  foundationClickable(
    interactionSource = interactionSource,
    indication = indication,
    enabled = enabled,
    onClickLabel = onClickLabel,
    role = role,
    onClick = onClick,
  )
