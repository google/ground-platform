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
@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.openforis.collectone.v2.core.forms.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue
import org.openforis.collectone.v2.core.forms.ui.controls.AssistChip
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.Card
import org.openforis.collectone.v2.core.forms.ui.controls.Checkbox
import org.openforis.collectone.v2.core.forms.ui.controls.DropdownMenuItem
import org.openforis.collectone.v2.core.forms.ui.controls.ElevatedButton
import org.openforis.collectone.v2.core.forms.ui.controls.ElevatedCard
import org.openforis.collectone.v2.core.forms.ui.controls.ExtendedFloatingActionButton
import org.openforis.collectone.v2.core.forms.ui.controls.FilledIconButton
import org.openforis.collectone.v2.core.forms.ui.controls.FilledTonalButton
import org.openforis.collectone.v2.core.forms.ui.controls.FilledTonalIconButton
import org.openforis.collectone.v2.core.forms.ui.controls.FilterChip
import org.openforis.collectone.v2.core.forms.ui.controls.FloatingActionButton
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.IconToggleButton
import org.openforis.collectone.v2.core.forms.ui.controls.InputChip
import org.openforis.collectone.v2.core.forms.ui.controls.NavigationDrawerItem
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedIconButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedIconToggleButton
import org.openforis.collectone.v2.core.forms.ui.controls.RadioButton
import org.openforis.collectone.v2.core.forms.ui.controls.SegmentedButton
import org.openforis.collectone.v2.core.forms.ui.controls.Slider
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.Switch
import org.openforis.collectone.v2.core.forms.ui.controls.Tab
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.core.forms.ui.controls.backdropClickable
import org.openforis.collectone.v2.core.forms.ui.controls.clickable
import org.openforis.collectone.v2.core.forms.ui.controls.selectable
import org.openforis.collectone.v2.core.forms.ui.controls.toggleable

/**
 * Verifies that [AppTheme] and the `controls` drop-in wrappers attach [PointerIcon.Hand] to enabled
 * click targets, fall back to [PointerIcon.Default] when disabled, leave static surfaces and
 * non-interactive callbacks untouched, and honor custom [LocalInteractivePointerIcon] overrides.
 */
class InteractivePointerIconTest {

  private fun SemanticsNodeInteraction.assertHasPointerHoverIcon(expected: PointerIcon) {
    val expectedModifier = Modifier.pointerHoverIcon(expected)
    val modifiers = fetchSemanticsNode().layoutInfo.getModifierInfo().map { it.modifier }
    assertTrue(
      expectedModifier in modifiers,
      "Expected $expectedModifier in modifiers, found: $modifiers",
    )
  }

  private fun SemanticsNodeInteraction.assertHasNoPointerHoverIcon() {
    val modifiers = fetchSemanticsNode().layoutInfo.getModifierInfo().map { it.modifier }
    val hoverModifiers = modifiers.filter { it.toString().contains("PointerHoverIcon") }
    assertTrue(
      hoverModifiers.isEmpty(),
      "Expected no PointerHoverIcon modifier, found: $hoverModifiers",
    )
  }

  @Test
  fun buttons_applyHandWhenEnabledAndDefaultWhenDisabled() = runComposeUiTest {
    setContent {
      AppTheme {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          Button(onClick = {}, modifier = Modifier.testTag("btn_on")) { Text("On") }
          Button(onClick = {}, enabled = false, modifier = Modifier.testTag("btn_off")) {
            Text("Off")
          }
          ElevatedButton(onClick = {}, modifier = Modifier.testTag("elevated_on")) { Text("E") }
          FilledTonalButton(onClick = {}, modifier = Modifier.testTag("tonal_on")) { Text("T") }
          OutlinedButton(onClick = {}, modifier = Modifier.testTag("outlined_on")) { Text("O") }
          TextButton(onClick = {}, modifier = Modifier.testTag("text_on")) { Text("X") }
          IconButton(onClick = {}, modifier = Modifier.testTag("icon_on")) { Text("I") }
          IconButton(onClick = {}, enabled = false, modifier = Modifier.testTag("icon_off")) {
            Text("I")
          }
          IconToggleButton(
            checked = false,
            onCheckedChange = {},
            modifier = Modifier.testTag("icon_toggle_on"),
          ) {
            Text("IT")
          }
          FilledIconButton(onClick = {}, modifier = Modifier.testTag("filled_icon_on")) {
            Text("FI")
          }
          FilledTonalIconButton(onClick = {}, modifier = Modifier.testTag("tonal_icon_on")) {
            Text("TI")
          }
          OutlinedIconButton(onClick = {}, modifier = Modifier.testTag("outlined_icon_on")) {
            Text("OI")
          }
          OutlinedIconToggleButton(
            checked = true,
            onCheckedChange = {},
            modifier = Modifier.testTag("outlined_icon_toggle_on"),
          ) {
            Text("OIT")
          }
          FloatingActionButton(onClick = {}, modifier = Modifier.testTag("fab")) { Text("+") }
          ExtendedFloatingActionButton(
            text = { Text("Add") },
            icon = { Text("+") },
            onClick = {},
            modifier = Modifier.testTag("ext_fab_text"),
          )
          ExtendedFloatingActionButton(onClick = {}, modifier = Modifier.testTag("ext_fab_body")) {
            Text("Add")
          }
        }
      }
    }

    for (tag in
      listOf(
        "btn_on",
        "elevated_on",
        "tonal_on",
        "outlined_on",
        "text_on",
        "icon_on",
        "icon_toggle_on",
        "filled_icon_on",
        "tonal_icon_on",
        "outlined_icon_on",
        "outlined_icon_toggle_on",
        "fab",
        "ext_fab_text",
        "ext_fab_body",
      )) {
      onNodeWithTag(tag).assertHasPointerHoverIcon(PointerIcon.Hand)
    }
    onNodeWithTag("btn_off").assertHasPointerHoverIcon(PointerIcon.Default)
    onNodeWithTag("icon_off").assertHasPointerHoverIcon(PointerIcon.Default)
  }

  @Test
  fun cardsAndSurfaces_applyHandOnlyOnInteractiveOverloads() = runComposeUiTest {
    setContent {
      AppTheme {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          Card(modifier = Modifier.testTag("card_static")) { Text("Static") }
          Card(onClick = {}, modifier = Modifier.testTag("card_on")) { Text("Click") }
          Card(onClick = {}, enabled = false, modifier = Modifier.testTag("card_off")) {
            Text("Disabled")
          }
          OutlinedCard(modifier = Modifier.testTag("outlined_card_static")) { Text("Static") }
          OutlinedCard(onClick = {}, modifier = Modifier.testTag("outlined_card_on")) {
            Text("Click")
          }
          ElevatedCard(modifier = Modifier.testTag("elevated_card_static")) { Text("Static") }
          ElevatedCard(onClick = {}, modifier = Modifier.testTag("elevated_card_on")) {
            Text("Click")
          }
          Surface(modifier = Modifier.testTag("surface_static")) { Text("Static") }
          Surface(onClick = {}, modifier = Modifier.testTag("surface_click")) { Text("Click") }
          Surface(selected = true, onClick = {}, modifier = Modifier.testTag("surface_select")) {
            Text("Select")
          }
          Surface(
            checked = false,
            onCheckedChange = {},
            modifier = Modifier.testTag("surface_toggle"),
          ) {
            Text("Toggle")
          }
        }
      }
    }

    for (tag in
      listOf("card_static", "outlined_card_static", "elevated_card_static", "surface_static")) {
      onNodeWithTag(tag).assertHasNoPointerHoverIcon()
    }
    for (tag in
      listOf(
        "card_on",
        "outlined_card_on",
        "elevated_card_on",
        "surface_click",
        "surface_select",
        "surface_toggle",
      )) {
      onNodeWithTag(tag).assertHasPointerHoverIcon(PointerIcon.Hand)
    }
    onNodeWithTag("card_off").assertHasPointerHoverIcon(PointerIcon.Default)
  }

  @Test
  fun inputsChipsMenusTabsAndSliders_applyExpectedPointerIcons() = runComposeUiTest {
    setContent {
      AppTheme {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          AssistChip(onClick = {}, label = { Text("A") }, modifier = Modifier.testTag("assist"))
          FilterChip(
            selected = false,
            onClick = {},
            label = { Text("F") },
            modifier = Modifier.testTag("filter"),
          )
          InputChip(
            selected = true,
            onClick = {},
            label = { Text("I") },
            modifier = Modifier.testTag("input"),
          )
          DropdownMenuItem(
            text = { Text("Item") },
            onClick = {},
            modifier = Modifier.testTag("menu_on"),
          )
          DropdownMenuItem(
            text = { Text("Disabled") },
            onClick = {},
            enabled = false,
            modifier = Modifier.testTag("menu_off"),
          )
          Checkbox(checked = false, onCheckedChange = {}, modifier = Modifier.testTag("cb_on"))
          Checkbox(
            checked = false,
            onCheckedChange = {},
            enabled = false,
            modifier = Modifier.testTag("cb_off"),
          )
          Checkbox(checked = true, onCheckedChange = null, modifier = Modifier.testTag("cb_null"))
          RadioButton(selected = false, onClick = {}, modifier = Modifier.testTag("rb_on"))
          RadioButton(selected = true, onClick = null, modifier = Modifier.testTag("rb_null"))
          Switch(checked = false, onCheckedChange = {}, modifier = Modifier.testTag("sw_on"))
          Switch(checked = true, onCheckedChange = null, modifier = Modifier.testTag("sw_null"))
          Tab(
            selected = false,
            onClick = {},
            text = { Text("T1") },
            modifier = Modifier.testTag("tab_text"),
          )
          Tab(selected = true, onClick = {}, modifier = Modifier.testTag("tab_content")) {
            Text("T2")
          }
          SingleChoiceSegmentedButtonRow {
            SegmentedButton(
              selected = true,
              onClick = {},
              shape = SegmentedButtonDefaults.itemShape(0, 1),
              modifier = Modifier.testTag("seg_single"),
            ) {
              Text("S")
            }
          }
          MultiChoiceSegmentedButtonRow {
            SegmentedButton(
              checked = false,
              onCheckedChange = {},
              shape = SegmentedButtonDefaults.itemShape(0, 1),
              modifier = Modifier.testTag("seg_multi"),
            ) {
              Text("M")
            }
          }
          NavigationDrawerItem(
            label = { Text("Nav") },
            selected = false,
            onClick = {},
            modifier = Modifier.testTag("drawer_item"),
          )
          Slider(value = 0.5f, onValueChange = {}, modifier = Modifier.testTag("slider_on"))
          Slider(
            value = 0.5f,
            onValueChange = {},
            enabled = false,
            modifier = Modifier.testTag("slider_off"),
          )
        }
      }
    }

    for (tag in
      listOf(
        "assist",
        "filter",
        "input",
        "menu_on",
        "cb_on",
        "rb_on",
        "sw_on",
        "tab_text",
        "tab_content",
        "seg_single",
        "seg_multi",
        "drawer_item",
        "slider_on",
      )) {
      onNodeWithTag(tag).assertHasPointerHoverIcon(PointerIcon.Hand)
    }
    for (tag in listOf("menu_off", "cb_off", "slider_off")) {
      onNodeWithTag(tag).assertHasPointerHoverIcon(PointerIcon.Default)
    }
    for (tag in listOf("cb_null", "rb_null", "sw_null")) {
      onNodeWithTag(tag).assertHasNoPointerHoverIcon()
    }
  }

  @Test
  fun interactiveModifiersAndBackdropClickable_applyExpectedPointerIcons() = runComposeUiTest {
    setContent {
      AppTheme {
        Column {
          Box(Modifier.size(20.dp).testTag("click_on").clickable {})
          Box(Modifier.size(20.dp).testTag("click_off").clickable(enabled = false) {})
          Box(
            Modifier.size(20.dp).testTag("click_ind").clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
            ) {}
          )
          Box(Modifier.size(20.dp).testTag("select_on").selectable(selected = false) {})
          Box(
            Modifier.size(20.dp).testTag("select_ind").selectable(
              selected = true,
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
            ) {}
          )
          Box(Modifier.size(20.dp).testTag("toggle_on").toggleable(value = false) {})
          Box(
            Modifier.size(20.dp).testTag("toggle_ind").toggleable(
              value = true,
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
            ) {}
          )
          Box(Modifier.size(20.dp).testTag("backdrop").backdropClickable {})
          Box(
            Modifier.size(20.dp).testTag("backdrop_ind").backdropClickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
            ) {}
          )
        }
      }
    }

    for (tag in
      listOf("click_on", "click_ind", "select_on", "select_ind", "toggle_on", "toggle_ind")) {
      onNodeWithTag(tag).assertHasPointerHoverIcon(PointerIcon.Hand)
    }
    onNodeWithTag("click_off").assertHasPointerHoverIcon(PointerIcon.Default)
    onNodeWithTag("backdrop").assertHasNoPointerHoverIcon()
    onNodeWithTag("backdrop_ind").assertHasNoPointerHoverIcon()
  }

  @Test
  fun appTheme_allowsOverridingInteractivePointerIcon() = runComposeUiTest {
    setContent {
      AppTheme(interactivePointerIcon = PointerIcon.Crosshair) {
        Button(onClick = {}, modifier = Modifier.testTag("custom_btn")) { Text("Custom") }
      }
    }

    onNodeWithTag("custom_btn").assertHasPointerHoverIcon(PointerIcon.Crosshair)
  }
}
