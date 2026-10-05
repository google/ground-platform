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
package org.groundplatform.v2.devtools.prototypeapp

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Chip rows using [horizontalScrollWithMouseDrag] must click on a single, imperfect mouse click.
 */
@OptIn(ExperimentalTestApi::class)
class HorizontalScrollWithMouseDragTest {

  private fun withChipRow(block: ComposeUiTest.(ScrollState, () -> String?) -> Unit) =
    runDesktopComposeUiTest(width = 400, height = 200) {
      val scroll = ScrollState(0)
      var clicked: String? = null
      setContent {
        MaterialTheme {
          Row(
            modifier = Modifier.width(300.dp).horizontalScrollWithMouseDrag(scroll),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            (1..12).forEach { i ->
              FilterChip(
                selected = false,
                onClick = { clicked = "Chip $i" },
                label = { Text("Chip $i") },
              )
            }
          }
        }
      }
      block(scroll) { clicked }
    }

  @Test
  fun clickWithSlightMouseMovementClicksChip() = withChipRow { scroll, clicked ->
    onNodeWithText("Chip 2").performMouseInput {
      moveTo(center)
      press()
      moveBy(Offset(3f, 1f))
      release()
    }
    waitForIdle()
    assertEquals("Chip 2", clicked())
    assertEquals(0, scroll.value)
  }

  @Test
  fun mouseDragPastSlopScrollsRow() = withChipRow { scroll, clicked ->
    onNodeWithText("Chip 2").performMouseInput {
      moveTo(center)
      press()
      repeat(10) { moveBy(Offset(-15f, 0f)) }
      release()
    }
    waitForIdle()
    assertTrue(scroll.value > 0)
    assertEquals(null, clicked())
  }
}
