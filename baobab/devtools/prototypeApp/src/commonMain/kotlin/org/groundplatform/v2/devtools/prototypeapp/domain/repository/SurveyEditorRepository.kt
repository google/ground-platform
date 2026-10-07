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
package org.groundplatform.v2.devtools.prototypeapp.domain.repository

import kotlinx.coroutines.flow.Flow
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyEditorDraft

/**
 * Loads and saves the Survey editor's per-survey drafts.
 *
 * The survey's runtime data (its Forms, Map layers, and map features) is the source of truth. A
 * survey that was never edited has no stored draft: its draft is derived from the runtime data. A
 * saved draft is stored (it also holds what the runtime data can't: Data tables, sharing people,
 * the survey area, sample designs) and its Map layer rows are refreshed from the runtime map
 * features whenever it's read.
 */
interface SurveyEditorRepository {
  /** The current draft of [surveyId]: its stored draft, refreshed, or one derived from its data. */
  suspend fun getDraft(surveyId: String): SurveyEditorDraft

  /**
   * Emits the current draft of [surveyId] (see [getDraft]) whenever the stored draft or data
   * changes.
   */
  fun observeDraft(surveyId: String): Flow<SurveyEditorDraft>

  /**
   * Saves [draft] for [surveyId] and publishes it to the survey's data: each Form's generated
   * XForms XML goes to the survey's config, Forms and Map layers replace the survey's Forms and map
   * layers, Map layer rows add, update, or remove map features (keeping collected submissions), and
   * a non-blank title and description are copied to the survey list.
   *
   * Map layer rows are removed relative to [previous], the draft the editor opened (so map features
   * collected since then are kept); when `null`, relative to the last stored draft or, before the
   * first save, the survey's current data.
   */
  suspend fun saveDraft(
    surveyId: String,
    draft: SurveyEditorDraft,
    previous: SurveyEditorDraft? = null,
  )
}
