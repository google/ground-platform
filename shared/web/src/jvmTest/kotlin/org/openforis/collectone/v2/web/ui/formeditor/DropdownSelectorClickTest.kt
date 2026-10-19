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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** Dropdowns must open on a single click, even when the mouse moves a little during it. */
@OptIn(ExperimentalTestApi::class)
class DropdownSelectorClickTest {

  private val fruits = listOf("Apple", "Banana", "Cherry")

  private fun withDropdown(block: ComposeUiTest.(selected: () -> String) -> Unit) =
    runDesktopComposeUiTest(width = 400, height = 400) {
      var selected by mutableStateOf("Apple")
      setContent {
        MaterialTheme {
          Column(Modifier.width(300.dp).padding(16.dp)) {
            DropdownSelector(
              label = "Fruit",
              selectedText = selected,
              options = fruits,
              optionText = { it },
              onSelect = { selected = it },
            )
          }
        }
      }
      block { selected }
    }

  private fun ComposeUiTest.field() = onNode(hasText("Fruit"))

  /** A real click: press, a small mouse movement, release. */
  private fun ComposeUiTest.wobblyClick() {
    field().performMouseInput {
      moveTo(center)
      press()
      moveBy(Offset(2f, 1f))
      release()
    }
    waitForIdle()
  }

  @Test
  fun clickWithSlightMouseMovementOpensTheMenu() = withDropdown { selected ->
    wobblyClick()
    onNodeWithText("Cherry").performClick()
    waitForIdle()
    assertEquals("Cherry", selected())
  }

  @Test
  fun clickingTheFieldAgainClosesTheMenu() = withDropdown {
    wobblyClick()
    onAllNodesWithText("Banana").assertCountEquals(1)
    wobblyClick()
    onAllNodesWithText("Banana").assertCountEquals(0)
  }
}
