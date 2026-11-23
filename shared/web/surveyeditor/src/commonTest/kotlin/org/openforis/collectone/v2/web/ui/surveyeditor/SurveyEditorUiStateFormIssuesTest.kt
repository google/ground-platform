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
package org.openforis.collectone.v2.web.ui.surveyeditor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.seed.SurveyEditorSamples
import org.openforis.collectone.v2.app.domain.model.editor.EditorIssueMessage
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.app.domain.model.messageKey

class SurveyEditorUiStateFormIssuesTest {
  /** The sample draft with Form `f1`'s second question renamed to clash with its first. */
  private fun draftWithDuplicateQuestionName(): SurveyEditorDraft {
    val draft = SurveyEditorSamples.draft()
    return draft.copy(
      forms =
        draft.forms.map { entry ->
          if (entry.key != "f1") return@map entry
          val questions = entry.form.questions
          val clash = questions[1].copy(name = questions[0].name)
          entry.copy(
            form = entry.form.copy(questions = listOf(questions[0], clash) + questions.drop(2))
          )
        }
    )
  }

  @Test
  fun formIssues_reportsTheFormsValidationIssues() {
    val uiState = SurveyEditorUiState(draft = draftWithDuplicateQuestionName())

    val issues = uiState.formIssues(uiState.form("f1")!!)

    assertTrue(
      issues.any { it.message.messageKey == EditorIssueMessage.NAME_DUPLICATE },
      issues.toString(),
    )
    assertEquals(emptyList(), uiState.formIssues(uiState.form("f2")!!))
  }

  @Test
  fun formIssues_isComputedOncePerState() {
    val uiState = SurveyEditorUiState(draft = draftWithDuplicateQuestionName())
    val entry = uiState.form("f1")!!

    assertSame(uiState.formIssues(entry), uiState.formIssues(entry))
  }

  @Test
  fun formIssues_followsTheDraftOfACopiedState() {
    val invalid = SurveyEditorUiState(draft = draftWithDuplicateQuestionName())

    val fixed = invalid.copy(draft = SurveyEditorSamples.draft())

    assertEquals(emptyList(), fixed.formIssues(fixed.form("f1")!!))
  }

  @Test
  fun issueCount_includesFormIssues() {
    val valid = SurveyEditorUiState(draft = SurveyEditorSamples.draft())
    val invalid = SurveyEditorUiState(draft = draftWithDuplicateQuestionName())

    assertEquals(
      valid.issueCount + invalid.formIssues(invalid.form("f1")!!).size,
      invalid.issueCount,
    )
  }
}
