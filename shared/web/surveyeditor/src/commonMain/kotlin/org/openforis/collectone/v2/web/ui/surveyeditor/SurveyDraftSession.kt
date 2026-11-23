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

import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.SurveyDetails
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft

/**
 * The editing session of the Survey editor as its action handlers see it: the current draft and UI
 * state, and the one way to change the draft. [SurveyEditorViewModel] owns the session and hands
 * this view of it to each handler.
 */
internal interface SurveyDraftSession {
  /** The latest UI state, for read-only checks and derived values. */
  val uiState: SurveyEditorUiState

  /** The draft under edit, or the live draft until the first edit. */
  val draft: SurveyEditorDraft

  /** The section the editor shows. */
  val section: SurveyEditorSection

  /**
   * Applies [block] to a working copy of the draft and stores the result as the edited draft,
   * freezing the opened snapshot on the first edit. A block that changes nothing leaves the session
   * as it is. Ignored while the survey is closed or archived.
   */
  fun edit(block: SurveyDraftEdits.() -> Unit)

  /**
   * Runs [edit] and records the resulting draft change on Form [formKey]'s undo stack, so
   * Form-scoped actions handled at the Survey level can be undone from the Form editor.
   */
  fun editForForm(formKey: String, block: SurveyDraftEdits.() -> Unit)
}

/** Replaces the survey details with [transform] of them. */
internal fun SurveyDraftSession.updateDetails(transform: (SurveyDetails) -> SurveyDetails) = edit {
  details = transform(details)
}

/** Replaces dataset [key] with [transform] of it; Forms follow a changed dataset ID. */
internal fun SurveyDraftSession.updateDataset(
  key: String,
  transform: (EntityDataset) -> EntityDataset,
) = edit { updateDataset(key, transform) }
