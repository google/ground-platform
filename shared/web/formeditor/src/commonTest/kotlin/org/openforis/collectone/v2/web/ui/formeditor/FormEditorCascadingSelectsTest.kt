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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.domain.model.editor.EditorChoice
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EditorIssueMessage
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.domain.model.messageKey
import org.openforis.collectone.v2.web.ui.surveyeditor.formEditorViewModel
import org.openforis.collectone.v2.web.ui.surveyeditor.ui

/** Authoring cascading selects (a child question's choices filtered by a parent's answer). */
class FormEditorCascadingSelectsTest {
  private val country =
    EditorQuestion(
      key = "q1",
      name = "country",
      type = EditorQuestionType.SELECT_ONE,
      label = "Country",
      choices = listOf(EditorChoice("kenya", "Kenya"), EditorChoice("uganda", "Uganda")),
    )

  private val region =
    EditorQuestion(
      key = "q2",
      name = "region",
      type = EditorQuestionType.SELECT_ONE,
      label = "Region",
      choices =
        listOf(
          EditorChoice("nairobi", "Nairobi", parentValue = "kenya"),
          EditorChoice("central", "Central", parentValue = "uganda"),
          EditorChoice("other", "Other"),
        ),
    )

  private val form =
    EditorForm(formId = "cascade", title = "Cascade", questions = listOf(country, region))

  private fun FormEditorViewModel.question(key: String) = ui.form.questions.single { it.key == key }

  private fun FormEditorViewModel.issueKeys(key: String) =
    ui.issuesFor(key).map { it.message.messageKey }

  @Test
  fun updateQuestionParentKey_linksTheChildToItsParent() {
    val viewModel = formEditorViewModel(form)

    viewModel.updateQuestionParentKey("q2", "q1")

    assertEquals("q1", viewModel.question("q2").parentQuestionKey)
    assertEquals(emptyList(), viewModel.issueKeys("q2"))
  }

  @Test
  fun updateChoiceParentValue_tiesAChoiceToAParentChoice() {
    val viewModel = formEditorViewModel(form)
    viewModel.updateQuestionParentKey("q2", "q1")

    viewModel.updateChoiceParentValue("q2", 2, "uganda")

    assertEquals("uganda", viewModel.question("q2").choices[2].parentValue)
  }

  @Test
  fun renamingAParentChoiceValue_keepsTheChildChoicesTiedToIt() {
    val viewModel = formEditorViewModel(form)
    viewModel.updateQuestionParentKey("q2", "q1")

    viewModel.updateChoice("q1", 0, EditorChoice("ke", "Kenya"))

    assertEquals(
      listOf("ke", "uganda", ""),
      viewModel.question("q2").choices.map { it.parentValue },
    )
    assertEquals(emptyList(), viewModel.issueKeys("q2"))
  }

  @Test
  fun editingAParentLabel_followsTheAutoSluggedValueIntoTheChildChoices() {
    val slugged = country.copy(choices = listOf(EditorChoice("kenya", "kenya")))
    val viewModel =
      formEditorViewModel(
        form.copy(questions = listOf(slugged, region.copy(parentQuestionKey = "q1")))
      )

    viewModel.updateChoiceLabel("q1", 0, "Kenya Republic")

    val renamed = viewModel.question("q1").choices.first().value
    assertTrue(renamed != "kenya")
    assertEquals(renamed, viewModel.question("q2").choices.first().parentValue)
  }

  @Test
  fun renamingAChoiceOfAnUnrelatedQuestion_leavesTheChildAlone() {
    val language =
      country.copy(key = "q3", name = "language", choices = listOf(EditorChoice("kenya", "Kenya")))
    val viewModel =
      formEditorViewModel(
        form.copy(questions = listOf(country, region.copy(parentQuestionKey = "q1"), language))
      )

    viewModel.updateChoice("q3", 0, EditorChoice("swahili", "Swahili"))

    assertEquals("kenya", viewModel.question("q2").choices.first().parentValue)
  }

  @Test
  fun deletingTheParent_flagsTheChildsCascadingFilter() {
    val viewModel =
      formEditorViewModel(form.copy(questions = listOf(region.copy(parentQuestionKey = "q1"))))

    assertEquals(listOf(EditorIssueMessage.CASCADE_PARENT_DELETED), viewModel.issueKeys("q2"))
  }
}
