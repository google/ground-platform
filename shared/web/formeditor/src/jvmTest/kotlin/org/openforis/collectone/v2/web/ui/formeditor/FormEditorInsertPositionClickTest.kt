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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.domain.model.FormAvailability
import org.openforis.collectone.v2.app.domain.model.editor.EditorChoice
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.domain.model.editor.EditorRelevance
import org.openforis.collectone.v2.app.domain.model.editor.FormPreviewTarget
import org.openforis.collectone.v2.app.domain.model.editor.RelevanceOperator
import org.openforis.collectone.v2.web.ui.surveyeditor.formEditorViewModel
import org.openforis.collectone.v2.web.ui.surveyeditor.ui

/**
 * End-to-end UI tests verifying `+` insertion positions across Mobile flow and Web layout modes.
 */
@OptIn(ExperimentalTestApi::class)
class FormEditorInsertPositionClickTest {

  private fun withEditor(
    form: EditorForm,
    block: androidx.compose.ui.test.ComposeUiTest.(FormEditorViewModel) -> Unit,
  ) =
    runDesktopComposeUiTest(width = 1800, height = 1100) {
      val state = formEditorViewModel(form)
      setContent {
        MaterialTheme {
          val uiState by state.uiState.collectAsState()
          FormEditorPage(
            uiState = uiState,
            actions = state,
            previewController = state.previewController.collectAsState().value,
            isDarkTheme = false,
          )
        }
      }
      block(state)
    }

  @Test
  fun clickingPlusAtStartBetweenStepsAndEndInsertsQuestionsInExactOrder() =
    withEditor(
      EditorForm(
        formId = "linear_insert",
        title = "Linear Insert",
        questions =
          listOf(
            EditorQuestion("q1", "first", EditorQuestionType.TEXT, "First step"),
            EditorQuestion("q2", "second", EditorQuestionType.TEXT, "Second step"),
            EditorQuestion("q3", "third", EditorQuestionType.TEXT, "Third step"),
          ),
      )
    ) { state ->
      // 1. Insert DATE before step 0 ("+" after Start).
      onAllNodesWithContentDescription("Add question here")[0].performTouchInput { click() }
      waitForIdle()
      onAllNodesWithText("Date").onLast().performClick()
      waitForIdle()
      assertEquals(EditorQuestionType.DATE, state.ui.form.questions[0].type)
      assertEquals("q1", state.ui.form.questions[1].key)

      // 2. Insert NUMBER between "First step" (now index 1) and "Second step" (now index 2).
      onAllNodesWithContentDescription("Add question here")[2].performTouchInput { click() }
      waitForIdle()
      onAllNodesWithText("Number").onLast().performClick()
      waitForIdle()
      assertEquals(EditorQuestionType.NUMBER, state.ui.form.questions[2].type)
      assertEquals(
        listOf(
          EditorQuestionType.DATE,
          EditorQuestionType.TEXT,
          EditorQuestionType.NUMBER,
          EditorQuestionType.TEXT,
          EditorQuestionType.TEXT,
        ),
        state.ui.form.questions.map { it.type },
      )
      assertEquals("q2", state.ui.form.questions[3].key)
      assertEquals("q3", state.ui.form.questions[4].key)

      // 3. Insert PHOTO at the very end ("+" after the last question).
      onAllNodesWithContentDescription("Add question here")[5].performScrollTo().performTouchInput {
        click()
      }
      waitForIdle()
      onAllNodesWithText("Photo").onLast().performClick()
      waitForIdle()
      assertEquals(EditorQuestionType.PHOTO, state.ui.form.questions[5].type)
      assertEquals(6, state.ui.form.questions.map { it.key }.distinct().size)
      assertEquals(6, state.ui.form.questions.map { it.name }.distinct().size)
      for (stepNum in 1..6) {
        onAllNodesWithText("Q$stepNum", useUnmergedTree = true).onFirst().assertExists()
      }
    }

  @Test
  fun clickingPlusOnTrunkIsNotHijackedByLaterConditionalBranchAffordance() =
    withEditor(
      EditorForm(
        formId = "branched_insert",
        title = "Branched Insert",
        questions =
          listOf(
            EditorQuestion(
              key = "q1",
              name = "has_trees",
              type = EditorQuestionType.SELECT_ONE,
              label = "Has trees?",
              choices = listOf(EditorChoice("yes", "Yes"), EditorChoice("no", "No")),
            ),
            EditorQuestion("q2", "plot_code", EditorQuestionType.TEXT, "Plot code"),
            EditorQuestion("q3", "observer", EditorQuestionType.TEXT, "Observer"),
            // Last question branches off q1, so FlowLayout places it in the same column as q2 on
            // row = 1. Its "+" must sit on row = 1 rather than overlapping q2's "+" on row = 0.
            EditorQuestion(
              key = "q4",
              name = "tree_notes",
              type = EditorQuestionType.LONG_TEXT,
              label = "Tree notes",
              relevance = EditorRelevance("q1", RelevanceOperator.EQUALS, "yes"),
            ),
          ),
      )
    ) { state ->
      val plusNodes = onAllNodesWithContentDescription("Add question here")
      val startPlusBounds = plusNodes[0].fetchSemanticsNode().boundsInRoot
      val trunkRowY = startPlusBounds.center.y
      val q4PlusBounds = plusNodes[4].fetchSemanticsNode().boundsInRoot
      assertTrue(
        abs(q4PlusBounds.center.y - trunkRowY) > 100f,
        "Conditional branch '+' affordance should render on its branch row instead of trunk midY",
      )

      // Click the "+" between q2 (index 1) and q3 (index 2) on the trunk row.
      plusNodes[2].performTouchInput { click() }
      waitForIdle()
      onAllNodesWithText("Date").onLast().performClick()
      waitForIdle()

      assertEquals(5, state.ui.form.questions.size)
      assertEquals(EditorQuestionType.DATE, state.ui.form.questions[2].type)
      assertEquals("q2", state.ui.form.questions[1].key)
      assertEquals("q3", state.ui.form.questions[3].key)
      assertEquals("q4", state.ui.form.questions[4].key)
    }

  @Test
  fun clickingPlusBetweenStepsToInsertSectionPlacesSectionAndQuestionsInMiddle() =
    withEditor(
      EditorForm(
        formId = "section_insert",
        title = "Section Insert",
        questions =
          listOf(
            EditorQuestion("q1", "first", EditorQuestionType.TEXT, "First step"),
            EditorQuestion("q2", "second", EditorQuestionType.TEXT, "Second step"),
          ),
      )
    ) { state ->
      // Click "+" between q1 (index 0) and q2 (index 1) and insert a Section.
      onAllNodesWithContentDescription("Add question here")[1].performTouchInput { click() }
      waitForIdle()
      onAllNodesWithText("Section").onLast().performClick()
      waitForIdle()

      val sec = state.ui.form.sections.single()
      assertEquals(1, sec.insertAtIndex)

      val q1Bounds =
        onAllNodesWithText("First step", substring = true, useUnmergedTree = true)
          .onFirst()
          .fetchSemanticsNode()
          .boundsInRoot
      val sectionGhostBounds =
        onNodeWithContentDescription("Add question inside Section")
          .fetchSemanticsNode()
          .boundsInRoot
      val q2Bounds =
        onAllNodesWithText("Second step", substring = true, useUnmergedTree = true)
          .onFirst()
          .fetchSemanticsNode()
          .boundsInRoot
      assertTrue(q1Bounds.right < sectionGhostBounds.left)
      assertTrue(sectionGhostBounds.right < q2Bounds.left)

      // Adding the first question inside that Section inserts at index 1 (between q1 and q2).
      onNodeWithContentDescription("Add question inside Section").performClick()
      waitForIdle()
      onAllNodesWithText("Number").onLast().performClick()
      waitForIdle()

      assertEquals(3, state.ui.form.questions.size)
      assertEquals("q1", state.ui.form.questions[0].key)
      assertEquals(sec.id, state.ui.form.questions[1].sectionId)
      assertEquals(EditorQuestionType.NUMBER, state.ui.form.questions[1].type)
      assertEquals("q2", state.ui.form.questions[2].key)

      // Clicking the post-section "+" (outside the section frame, before q2) inserts unsectioned at
      // index 2.
      onAllNodesWithContentDescription("Add question here")[0].performTouchInput { click() }
      waitForIdle()
      onAllNodesWithText("Date").onLast().performClick()
      waitForIdle()

      assertEquals(4, state.ui.form.questions.size)
      assertEquals("q1", state.ui.form.questions[0].key)
      assertEquals(sec.id, state.ui.form.questions[1].sectionId)
      assertEquals("", state.ui.form.questions[2].sectionId)
      assertEquals(EditorQuestionType.DATE, state.ui.form.questions[2].type)
      assertEquals("q2", state.ui.form.questions[3].key)
    }

  @Test
  fun webLayoutCanvasSupportsInsertingBetweenStepsAtStartAndAtEnd() =
    withEditor(
      EditorForm(
        formId = "web_insert",
        title = "Web Insert",
        availability = FormAvailability.BOTH,
        questions =
          listOf(
            EditorQuestion("q1", "first", EditorQuestionType.TEXT, "First step"),
            EditorQuestion("q2", "second", EditorQuestionType.TEXT, "Second step"),
          ),
      )
    ) { state ->
      runOnIdle {
        state.selectPreviewTarget(FormPreviewTarget.WEB)
        state.selectForm()
      }
      waitForIdle()

      // Insert DATE before step 0.
      onAllNodesWithContentDescription("Add question here")[0].performTouchInput { click() }
      waitForIdle()
      onAllNodesWithText("Date").onLast().performClick()
      waitForIdle()
      assertEquals(EditorQuestionType.DATE, state.ui.form.questions[0].type)

      // Insert NUMBER between "First step" (now index 1) and "Second step" (now index 2).
      onAllNodesWithContentDescription("Add question here")[2].performTouchInput { click() }
      waitForIdle()
      onAllNodesWithText("Number").onLast().performClick()
      waitForIdle()
      assertEquals(
        listOf(
          EditorQuestionType.DATE,
          EditorQuestionType.TEXT,
          EditorQuestionType.NUMBER,
          EditorQuestionType.TEXT,
        ),
        state.ui.form.questions.map { it.type },
      )
      assertEquals("q2", state.ui.form.questions[3].key)
    }
}
