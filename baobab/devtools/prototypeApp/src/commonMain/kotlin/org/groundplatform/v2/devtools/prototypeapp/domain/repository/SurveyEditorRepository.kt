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

import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyEditorDraft

/** Loads and saves the Survey editor's per-survey drafts in the local data store. */
interface SurveyEditorRepository {
  /**
   * Returns the stored draft of [surveyId] (or a blank draft if it has never been edited), showing
   * the survey's current title and description.
   */
  suspend fun getDraft(surveyId: String): SurveyEditorDraft

  /**
   * Saves [draft] for [surveyId]. Also publishes each Form's generated XForms XML to the survey's
   * config, and copies a non-blank title and description to the survey list.
   */
  suspend fun saveDraft(surveyId: String, draft: SurveyEditorDraft)
}
