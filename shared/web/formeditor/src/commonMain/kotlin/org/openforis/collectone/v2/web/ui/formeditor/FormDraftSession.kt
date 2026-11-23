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

import org.openforis.collectone.v2.app.domain.model.editor.EditorChoice
import org.openforis.collectone.v2.app.domain.model.editor.EditorDataset
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion

/**
 * The Form editor's session as its action handlers see it: the Form, the datasets its save-to logic
 * targets, the UI state, and the one way to change the Form. [FormEditorViewModel] owns the session
 * (selection and undo history) and hands this view of it to each handler.
 */
internal interface FormDraftSession {
  /** The latest UI state, for languages, selection, and derived values. */
  val uiState: FormEditorUiState

  /** The Form as it is in the enclosing survey draft (or the standalone copy). */
  val form: EditorForm

  /** The Form as last emitted to the editor, which can trail [form] during an edit. */
  val observedForm: EditorForm

  /** The survey's Map layers and Data tables as this Form's save-to logic sees them. */
  val datasets: List<EditorDataset>

  /** Whether edits are ignored because the survey is closed or archived. */
  val isReadOnly: Boolean

  /** Key of the currently selected question, or `null` when a section or Form settings are open. */
  val selectedKey: String?

  /** Selects section [sectionId] for properties editing, or deselects when `null`. */
  fun selectSection(sectionId: String?)

  /**
   * Applies [transform] to the Form and records the change on the undo stack, selecting the
   * question returned by [selectAfter] (by default keeping [selectedKey]). A transform that changes
   * nothing is ignored, as is any edit while [isReadOnly].
   */
  fun mutateForm(
    selectAfter: (EditorForm) -> String? = { selectedKey },
    transform: (EditorForm) -> EditorForm,
  )
}

/** Replaces question [key] with [transform] of it. */
internal fun FormDraftSession.updateQuestion(
  key: String,
  transform: (EditorQuestion) -> EditorQuestion,
) = mutateForm { form ->
  form.copy(questions = form.questions.map { if (it.key == key) transform(it) else it })
}

/** Replaces choice [index] of question [key] with [transform] of it. */
internal fun FormDraftSession.updateChoiceAt(
  key: String,
  index: Int,
  transform: (EditorChoice) -> EditorChoice,
) =
  updateQuestion(key) { q ->
    q.copy(choices = q.choices.mapIndexed { i, c -> if (i == index) transform(c) else c })
  }
