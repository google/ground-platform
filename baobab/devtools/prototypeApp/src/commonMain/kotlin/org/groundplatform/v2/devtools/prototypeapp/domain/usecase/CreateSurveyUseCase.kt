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
package org.groundplatform.v2.devtools.prototypeapp.domain.usecase

import org.groundplatform.v2.devtools.prototypeapp.domain.model.CachedProfile
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapThumbnailTheme
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyEditorDraft
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyEditorRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.TransactionRunner

/**
 * Creates a new, empty survey owned by the signed-in user, optionally in an organization, and makes
 * it the active survey.
 *
 * Building the survey ([newSurvey]) is pure, so callers can hand out its ID right away; persisting
 * it ([invoke]) writes the survey list entry and a blank Survey editor draft in one transaction.
 */
class CreateSurveyUseCase(
  private val surveyRepository: SurveyRepository,
  private val surveyEditorRepository: SurveyEditorRepository,
  private val transactionRunner: TransactionRunner,
) {
  /**
   * A new survey titled [title] (or `"Untitled survey"` when blank) owned by [ownerEmail] in
   * [organization] (or personal when `null`), with an ID unique among [existingSurveys].
   */
  fun newSurvey(
    title: String,
    existingSurveys: List<SurveyPreviewItem>,
    organization: Organization?,
    ownerEmail: String,
  ): SurveyPreviewItem {
    val trimmedTitle = title.trim().ifBlank { DEFAULT_TITLE }
    return SurveyPreviewItem(
      id = uniqueSurveyId(trimmedTitle, existingSurveys.map { it.id }.toSet()),
      title = trimmedTitle,
      description = "",
      location = organization?.name ?: "No survey area yet",
      coordinatesLabel = "",
      offlineSizeLabel = "0 MB",
      isDownloaded = false,
      thumbnailTheme =
        MapThumbnailTheme.entries[existingSurveys.size % MapThumbnailTheme.entries.size],
      entityCount = 0,
      ownerEmail = ownerEmail,
      organizationId = organization?.id,
    )
  }

  /**
   * Stores [survey] (from [newSurvey]) with a blank editor draft owned by [survey]'s owner, named
   * [ownerName], and makes it the active survey.
   */
  suspend operator fun invoke(survey: SurveyPreviewItem, ownerName: String) {
    transactionRunner {
      surveyRepository.setSurveys(surveyRepository.getSurveys() + survey)
      surveyEditorRepository.saveDraft(
        survey.id,
        SurveyEditorDraft.blank(survey.id, title = survey.title).let { draft ->
          draft.copy(
            details = draft.details.copy(organizationId = survey.organizationId),
            sharing =
              draft.sharing.copy(
                ownerEmail = survey.ownerEmail,
                ownerProfile = CachedProfile(ownerName),
              ),
          )
        },
      )
      surveyRepository.setActiveSurveyId(survey.id)
    }
  }

  companion object {
    const val DEFAULT_TITLE = "Untitled survey"

    /** A survey ID derived from [title] (`survey-<slug>`), made unique among [takenIds]. */
    fun uniqueSurveyId(title: String, takenIds: Set<String>): String {
      val slug =
        title
          .lowercase()
          .map { if (it.isLetterOrDigit()) it else '-' }
          .joinToString("")
          .trim('-')
          .replace(Regex("-+"), "-")
          .ifBlank { "survey" }
      val base = "survey-$slug"
      if (base !in takenIds) return base
      var n = 2
      while ("$base-$n" in takenIds) n++
      return "$base-$n"
    }
  }
}
