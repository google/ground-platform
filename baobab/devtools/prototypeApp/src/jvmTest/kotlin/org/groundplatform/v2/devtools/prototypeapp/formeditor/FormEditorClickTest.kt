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
package org.groundplatform.v2.devtools.prototypeapp.formeditor

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.groundplatform.v2.devtools.prototypeapp.data.seed.FormEditorSamples

/** Every interactive element of the Form editor must respond to a single click or tap. */
@OptIn(ExperimentalTestApi::class)
class FormEditorClickTest {

  private fun withEditor(block: androidx.compose.ui.test.ComposeUiTest.(FormEditorState) -> Unit) =
    runDesktopComposeUiTest(width = 1600, height = 1000) {
      val state = FormEditorState(FormEditorSamples.shadeTreeVisit())
      setContent { MaterialTheme { FormEditorPage(state = state, isDarkTheme = false) } }
      block(state)
    }

  @Test
  fun singleClickOnScreenCardSelectsQuestion() = withEditor { state ->
    onAllNodesWithText("Are shade trees present?", substring = true, useUnmergedTree = true)
      .onFirst()
      .performClick()
    waitForIdle()
    assertEquals("q3", state.selectedKey)
  }

  @Test
  fun singleClickOnStartNodeOpensFormSettings() = withEditor { state ->
    onNodeWithText("Start").performClick()
    waitForIdle()
    assertTrue(state.isFormSettingsSelected)
  }

  @Test
  fun singleClickOnFormSettingsButtonOpensFormSettings() = withEditor { state ->
    onNodeWithText("Form settings").performClick()
    waitForIdle()
    assertTrue(state.isFormSettingsSelected)
  }

  @Test
  fun singleTapOnAddQuestionAffordanceOpensMenu() = withEditor { state ->
    // Touch input has no hover, so every "+" must already be hit-testable before the first tap.
    onAllNodesWithContentDescription("Add question here")
      .assertCountEquals(state.form.questions.size + 1)
    onAllNodesWithContentDescription("Add question here").onFirst().performTouchInput { click() }
    waitForIdle()
    // The menu popup is composed last; earlier matches are screen cards.
    onAllNodesWithText("Long text").onLast().performClick()
    waitForIdle()
    assertEquals(EditorQuestionType.LONG_TEXT, state.form.questions.first().type)
  }

  @Test
  fun clickDuringAutoScrollStillSelects() = withEditor { state ->
    mainClock.autoAdvance = false
    // Selecting an off-screen screen scrolls the canvas to it; a click that lands while that
    // scroll is still running must not be swallowed by the scroll container.
    runOnIdle { state.select("q7") }
    mainClock.advanceTimeByFrame()
    mainClock.advanceTimeByFrame()
    mainClock.advanceTimeBy(32)
    onAllNodesWithText("Observed issues", substring = true, useUnmergedTree = true)
      .onFirst()
      .performTouchInput { click() }
    mainClock.advanceTimeBy(1_000)
    assertEquals("q6", state.selectedKey)
  }

  @Test
  fun mouseClickRightAfterSelectingOffscreenScreenStillSelects() = withEditor { state ->
    mainClock.autoAdvance = false
    runOnIdle { state.select("q7") }
    mainClock.advanceTimeByFrame()
    mainClock.advanceTimeByFrame()
    onNodeWithText("Form settings").performClick()
    mainClock.advanceTimeBy(1_000)
    assertTrue(state.isFormSettingsSelected)
  }

  @Test
  fun singleClickOnChoiceColorSwatchAppliesColor() = withEditor { state ->
    runOnIdle { state.select("q3") }
    onAllNodesWithContentDescription("Choice color: none").onFirst().performClick()
    waitForIdle()
    onNodeWithContentDescription("Blue").performClick()
    waitForIdle()
    assertEquals("#1A73E8", state.form.find("q3")!!.choices[0].colorHex)
  }

  @Test
  fun singleClickOnAdvancedHeaderRevealsName() = withEditor { state ->
    runOnIdle { state.select("q3") }
    waitForIdle()
    // Collapsed by default: the data column name is hidden.
    onAllNodesWithText("Name").assertCountEquals(0)
    onNodeWithContentDescription("Expand advanced").performScrollTo().performClick()
    waitForIdle()
    onNodeWithText("Name").assertExists()
    onNodeWithText("Data column name. Letters, digits, _ . -").assertExists()
    onNodeWithText("Minimum selections").assertDoesNotExist()
    onNodeWithText("No validation rules for this question type.").assertExists()
    onNodeWithContentDescription("Collapse advanced").performClick()
    waitForIdle()
    onAllNodesWithText("Name").assertCountEquals(0)
  }

  @Test
  fun advancedSectionAutoExpandsOnInvalidName() = withEditor { state ->
    runOnIdle {
      state.select("q4")
      state.updateQuestion("q4") { it.copy(name = "1 bad name") }
    }
    waitForIdle()
    onNodeWithText("Name").assertExists()
    onNodeWithContentDescription("Advanced settings have errors").assertExists()
    onNodeWithText("Minimum").assertExists()
  }

  @Test
  fun advancedSectionStaysOpenWhenSelectingAnotherQuestion() = withEditor { state ->
    runOnIdle { state.select("q3") }
    waitForIdle()
    onNodeWithContentDescription("Expand advanced").performScrollTo().performClick()
    waitForIdle()
    assertTrue(state.isAdvancedExpanded)
    runOnIdle { state.select("q5") }
    waitForIdle()
    onNodeWithText("Name").assertExists()
    onNodeWithContentDescription("Collapse advanced").assertExists()
  }

  @Test
  fun datePickerStoresIsoDateAndShowsFriendlyDate() = withEditor { state ->
    runOnIdle {
      state.select("q1")
      state.isAdvancedExpanded = true
      state.updateValidation("q1") { it.copy(dateRule = DateRule.BETWEEN) }
    }
    waitForIdle()
    onNodeWithContentDescription("Pick Earliest date").performScrollTo().performClick()
    waitForIdle()
    // The picker opens on the current month; pick its 15th.
    onAllNodesWithText(" 15, ", substring = true, useUnmergedTree = true).onFirst().performClick()
    onNodeWithText("OK").performClick()
    waitForIdle()
    val expected = java.time.LocalDate.now().withDayOfMonth(15).toString()
    assertEquals(expected, state.form.find("q1")!!.validation?.min)
    onNodeWithText(friendlyDate(expected), useUnmergedTree = true).assertExists()

    onNodeWithContentDescription("Clear Earliest date").performClick()
    waitForIdle()
    assertEquals(null, state.form.find("q1")!!.validation?.min?.ifEmpty { null })
  }

  @Test
  fun clickWithSlightMouseMovementSelectsScreen() = withEditor { state ->
    // A real click rarely keeps the mouse perfectly still between press and release.
    onAllNodesWithText("Are shade trees present?", substring = true, useUnmergedTree = true)
      .onFirst()
      .performMouseInput {
        moveTo(center)
        press()
        moveBy(Offset(2f, 1f))
        release()
      }
    waitForIdle()
    assertEquals("q3", state.selectedKey)
  }

  @Test
  fun singleClickOnScreenAfterTypingInLabelSelectsIt() = withEditor { state ->
    runOnIdle { state.select("q1") }
    waitForIdle()
    onNodeWithText("Label").performClick()
    onNodeWithText("Label").performTextInput(" today")
    waitForIdle()
    onAllNodesWithText("Are shade trees present?", substring = true, useUnmergedTree = true)
      .onFirst()
      .performClick()
    waitForIdle()
    assertEquals("q3", state.selectedKey)
  }

  @Test
  fun dragPastSlopStillReordersScreens() = withEditor { state ->
    val firstKey = state.form.questions.first().key
    onAllNodesWithText(state.form.questions.first().label, substring = true, useUnmergedTree = true)
      .onFirst()
      .performMouseInput {
        moveTo(center)
        press()
        repeat(40) { moveBy(Offset(30f, 0f)) }
        release()
      }
    waitForIdle()
    // The first screen moved later.
    assertTrue(state.form.questions.indexOfFirst { it.key == firstKey } > 0)
  }

  @Test
  fun advancedStatusMarkerToggleAndAddRuleWorkInFormProperties() = withEditor { state ->
    runOnIdle {
      AdvancedDisclosure.expanded = true
      state.selectForm()
    }
    waitForIdle()
    assertTrue(!state.form.saveTo.status.enabled)
    onNodeWithText("Set status marker").performScrollTo().performClick()
    waitForIdle()
    assertTrue(state.form.saveTo.status.enabled)
    assertEquals(1, state.form.saveTo.status.rules.size)
    assertEquals("Surveyed", state.form.saveTo.status.rules.single().badge.label)
    assertEquals("Pending", state.form.saveTo.status.defaultBadge.label)

    onNodeWithText("Add status rule").performScrollTo().performClick()
    waitForIdle()
    assertEquals(2, state.form.saveTo.status.rules.size)
    runOnIdle { AdvancedDisclosure.expanded = null }
  }
}
