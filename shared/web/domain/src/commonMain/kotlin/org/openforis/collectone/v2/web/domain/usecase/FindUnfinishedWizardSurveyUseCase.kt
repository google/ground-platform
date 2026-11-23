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
package org.openforis.collectone.v2.web.domain.usecase

import org.openforis.collectone.v2.app.domain.model.SurveyLifecycleState
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.app.domain.repository.SurveyEditorRepository
import org.openforis.collectone.v2.app.domain.usecase.CreateSurveyUseCase

/**
 * Finds a survey the Survey Creation Wizard created earlier but never finished, so a new wizard
 * session can offer to continue it instead of leaving an orphaned blank survey behind (for example
 * after a page reload, or on another browser once the survey has synced).
 *
 * The wizard stores its survey right away, under the placeholder title, and keeps edits in a
 * working copy until it finishes, when it saves them and gives the survey its real title. So an
 * unfinished one is a draft the user owns that still has the placeholder title and a working copy,
 * and whose saved draft has no purpose yet (the purpose is required to finish the wizard, while the
 * survey area can't tell: new surveys already start with their organization's default area).
 * Requiring the placeholder title keeps drafts made outside the wizard, which have unsaved Survey
 * editor edits but no purpose, from being taken for unfinished wizard surveys.
 */
class FindUnfinishedWizardSurveyUseCase(
  private val surveyEditorRepository: SurveyEditorRepository
) {
  /** A survey left unfinished in the wizard, with its saved draft and unsaved working copy. */
  data class UnfinishedWizardSurvey(
    val survey: SurveyPreviewItem,
    val saved: SurveyEditorDraft,
    val workingCopy: SurveyEditorDraft,
  )

  /** The first of [surveys] that [ownerEmail] (case-insensitive) left unfinished, if any. */
  suspend operator fun invoke(
    surveys: List<SurveyPreviewItem>,
    ownerEmail: String,
  ): UnfinishedWizardSurvey? {
    val candidates = surveys.filter { survey ->
      survey.state == SurveyLifecycleState.DRAFT &&
        survey.title == CreateSurveyUseCase.DEFAULT_TITLE &&
        survey.ownerEmail.equals(ownerEmail, ignoreCase = true)
    }
    for (survey in candidates) {
      val workingCopy = surveyEditorRepository.getWorkingCopyDraft(survey.id)
      if (workingCopy != null) {
        val saved = surveyEditorRepository.getDraft(survey.id)
        if (saved.details.purposeIds.isEmpty() && saved.details.otherPurpose.isBlank()) {
          return UnfinishedWizardSurvey(survey, saved, workingCopy)
        }
      }
    }
    return null
  }
}
