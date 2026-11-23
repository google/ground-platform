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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ScrollToBottomTest {
  @Test
  fun hasReachedBottom_isFalseBeforeFirstLayout() {
    val unmeasured = ScrollState(initial = 0)
    assertFalse(unmeasured.hasReachedBottom())
  }

  @Test
  fun overflowingContent_staysFalseUntilScrolledToBottom_andStaysLatchedWhenScrolledBackUp() =
    runDesktopComposeUiTest(width = 300, height = 400) {
      var reached = false
      lateinit var scrollState: ScrollState

      setContent {
        scrollState = remember { ScrollState(initial = 0) }
        reached = rememberHasScrolledToBottom(scrollState)
        Column(
          modifier =
            Modifier.height(100.dp).fillMaxWidth().testTag("terms").verticalScroll(scrollState)
        ) {
          repeat(20) { index ->
            Box(modifier = Modifier.height(30.dp)) { Text("Paragraph $index") }
          }
        }
      }
      waitForIdle()

      assertTrue(scrollState.maxValue > 100)
      assertFalse(reached)

      // Partial scroll keeps the latch closed.
      onNodeWithTag("terms").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100f) }
      waitForIdle()
      assertFalse(reached)

      // Scrolling to the bottom unlocks it.
      onNodeWithTag("terms").performSemanticsAction(SemanticsActions.ScrollBy) {
        it(0f, scrollState.maxValue.toFloat())
      }
      waitForIdle()
      assertTrue(reached)

      // Scrolling back to the top keeps it unlocked.
      onNodeWithTag("terms").performSemanticsAction(SemanticsActions.ScrollBy) {
        it(0f, -scrollState.maxValue.toFloat())
      }
      waitForIdle()
      assertTrue(scrollState.value == 0)
      assertTrue(reached)
    }

  @Test
  fun shortContentThatFitsWithoutScrolling_unlocksAfterLayout() =
    runDesktopComposeUiTest(width = 300, height = 400) {
      var reached = false

      setContent {
        val scrollState = remember { ScrollState(initial = 0) }
        reached = rememberHasScrolledToBottom(scrollState)
        Column(modifier = Modifier.height(200.dp).fillMaxWidth().verticalScroll(scrollState)) {
          Text("Short terms")
        }
      }
      waitForIdle()

      assertTrue(reached)
    }

  @Test
  fun changingResetKey_resetsLatchForNewAccount() =
    runDesktopComposeUiTest(width = 300, height = 400) {
      var reached = false
      var account by mutableStateOf("first@example.org")

      setContent {
        val scrollState = remember(account) { ScrollState(initial = 0) }
        reached = rememberHasScrolledToBottom(scrollState, resetKey = account)
        Column(
          modifier =
            Modifier.height(100.dp).fillMaxWidth().testTag("terms").verticalScroll(scrollState)
        ) {
          repeat(20) { index ->
            Box(modifier = Modifier.height(30.dp)) { Text("Paragraph $index") }
          }
        }
      }
      waitForIdle()
      assertFalse(reached)

      onNodeWithTag("terms").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 5_000f) }
      waitForIdle()
      assertTrue(reached)

      account = "second@example.org"
      waitForIdle()
      assertFalse(reached)
    }
}
