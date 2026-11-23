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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import org.openforis.collectone.v2.app.domain.model.editor.EditorDataset
import org.openforis.collectone.v2.app.domain.model.editor.EditorDatasetProperty
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EditorPropertyKind
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.domain.model.editor.EntityIdSource
import org.openforis.collectone.v2.app.domain.model.editor.SaveToMode
import org.openforis.collectone.v2.web.ui.surveyeditor.formEditorViewModel
import org.openforis.collectone.v2.web.ui.surveyeditor.ui

/** How a Form that updates existing features finds the feature to update, and its status rules. */
class FormEditorSaveToLookupTest {

  private val farmers =
    EditorDataset(
      id = "farmers",
      displayName = "Farmers",
      isMapLayer = false,
      keyProperty = "farmer_id",
      labelProperty = "name",
      properties =
        listOf(
          EditorDatasetProperty("farmer_id", "Farmer ID", EditorPropertyKind.TEXT),
          EditorDatasetProperty("name", "Name", EditorPropertyKind.TEXT),
        ),
    )

  private val visitForm =
    EditorForm(
      formId = "form_visit",
      title = "Visit",
      questions =
        listOf(
          EditorQuestion(
            key = "q1",
            name = "farmer_id",
            type = EditorQuestionType.TEXT,
            label = "Farmer ID",
          ),
          EditorQuestion(
            key = "q2",
            name = "farmer_name",
            type = EditorQuestionType.TEXT,
            label = "Name",
          ),
        ),
    )

  private fun updatingFarmers(): FormEditorViewModel =
    formEditorViewModel(visitForm, listOf(farmers)).apply {
      setSaveToMode(SaveToMode.UPDATE)
      setTargetDataset("farmers")
    }

  @Test
  fun lookingUpByQuestion_picksAnIdQuestion_andTheTargetsKeyProperty() {
    val viewModel = updatingFarmers()
    viewModel.setIdSource(EntityIdSource.SELECTED_FEATURE)

    viewModel.setIdSource(EntityIdSource.QUESTION)

    val saveTo = viewModel.ui.form.saveTo
    assertEquals(EntityIdSource.QUESTION, saveTo.idSource)
    assertNotNull(saveTo.idQuestionKey)
    assertEquals("farmer_id", saveTo.idMatchProperty)
  }

  @Test
  fun setIdQuestion_andSetIdMatchProperty_storeTheChoices() {
    val viewModel = updatingFarmers()
    viewModel.setIdSource(EntityIdSource.QUESTION)

    viewModel.setIdQuestion("q2")
    viewModel.setIdMatchProperty("name")

    assertEquals("q2", viewModel.ui.form.saveTo.idQuestionKey)
    assertEquals("name", viewModel.ui.form.saveTo.idMatchProperty)
  }

  @Test
  fun choosingTheCurrentIdSourceAgain_recordsNothingToUndo() {
    val viewModel = formEditorViewModel(visitForm, listOf(farmers))
    val source = viewModel.ui.form.saveTo.idSource

    viewModel.setIdSource(source)

    assertFalse(viewModel.ui.canUndo)
  }

  @Test
  fun removeStatusRule_dropsThatRule() {
    val viewModel = updatingFarmers()
    viewModel.setStatusEnabled(true)
    viewModel.addStatusRule()
    val rules = viewModel.ui.form.saveTo.status.rules

    viewModel.removeStatusRule(0)

    assertEquals(rules.drop(1), viewModel.ui.form.saveTo.status.rules)
  }

  @Test
  fun removeStatusRule_outOfRange_changesNothing() {
    val viewModel = updatingFarmers()
    viewModel.setStatusEnabled(true)
    val status = viewModel.ui.form.saveTo.status

    viewModel.removeStatusRule(status.rules.size)

    assertEquals(status, viewModel.ui.form.saveTo.status)
  }

  @Test
  fun updateDefaultStatusBadge_changesTheBadgeShownWhenNoRuleMatches() {
    val viewModel = updatingFarmers()
    viewModel.setStatusEnabled(true)

    viewModel.updateDefaultStatusBadge { it.copy(label = "Not visited") }

    assertEquals("Not visited", viewModel.ui.form.saveTo.status.defaultBadge.label)
  }
}
