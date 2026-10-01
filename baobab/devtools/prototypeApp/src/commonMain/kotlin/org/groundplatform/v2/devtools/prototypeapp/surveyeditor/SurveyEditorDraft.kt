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
package org.groundplatform.v2.devtools.prototypeapp.surveyeditor

import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.formeditor.EditorForm

/** A Form in a [SurveyEditorDraft], identified within the draft by [key]. */
data class SurveyEditorForm(val key: String, val form: EditorForm)

/**
 * Everything the Survey editor edits for one survey: details, sharing, Forms, and entity datasets
 * (Map layers and Data tables). Stored in the local data store so edits are kept when the editor is
 * closed or another survey is opened.
 */
data class SurveyEditorDraft(
  val details: SurveyDetails,
  val sharing: SharingSettings,
  val forms: List<SurveyEditorForm> = emptyList(),
  val datasets: List<EntityDataset> = emptyList(),
  /** Next number used to generate unique form, dataset, and row keys. */
  val nextKeyId: Int = 100,
) {
  companion object {
    /** An empty draft for a survey with the given ID, title, and description. */
    fun blank(surveyId: String, title: String = "", description: String = ""): SurveyEditorDraft =
      SurveyEditorDraft(
        details = SurveyDetails(surveyId = surveyId, title = title, description = description),
        sharing = SharingSettings(ownerEmail = ""),
      )

    /**
     * The editor draft of a survey: its [stored] draft (or a blank one) showing the [survey]'s
     * current title and description, which the survey list owns.
     */
    fun forSurvey(
      surveyId: String,
      stored: SurveyEditorDraft?,
      survey: SurveyPreviewItem?,
    ): SurveyEditorDraft {
      val base = stored ?: blank(surveyId)
      if (survey == null) return base
      return base.copy(
        details = base.details.copy(title = survey.title, description = survey.description)
      )
    }
  }
}
