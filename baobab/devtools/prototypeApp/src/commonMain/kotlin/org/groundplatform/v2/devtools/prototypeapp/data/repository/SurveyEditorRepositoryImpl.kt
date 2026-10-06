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
package org.groundplatform.v2.devtools.prototypeapp.data.repository

import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.LocalStore
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyConfig
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyEditorRepository
import org.groundplatform.v2.devtools.prototypeapp.surveyeditor.SurveyEditorDraft
import org.groundplatform.v2.devtools.prototypeapp.surveyeditor.publishedFormXml

/** [SurveyEditorRepository] backed by the [LocalStore]. */
class SurveyEditorRepositoryImpl(private val store: LocalStore) : SurveyEditorRepository {
  override suspend fun getDraft(surveyId: String): SurveyEditorDraft = store.transaction {
    SurveyEditorDraft.forSurvey(surveyId, surveyEditorDraft(surveyId), survey(surveyId))
  }

  override suspend fun saveDraft(surveyId: String, draft: SurveyEditorDraft) {
    store.transaction {
      val previous = surveyEditorDraft(surveyId)
      if (previous != draft) {
        putSurveyEditorDraft(surveyId, draft)
        val config = surveyConfig(surveyId) ?: SurveyConfig(primaryFormXml = "")
        val editorXml = draft.forms.associate { it.form.formId to draft.publishedFormXml(it) }
        val removedIds = previous?.forms.orEmpty().map { it.form.formId }.toSet() - editorXml.keys
        putSurveyConfig(
          surveyId,
          config.copy(formXmlById = config.formXmlById - removedIds + editorXml),
        )
      }
      // The survey list owns the title, description, and organization; copy edits there.
      updateSurvey(surveyId) { item ->
        item.copy(
          title = draft.details.title.ifBlank { item.title },
          description = draft.details.description.ifBlank { item.description },
          organizationId = draft.details.organizationId,
        )
      }
    }
  }
}
