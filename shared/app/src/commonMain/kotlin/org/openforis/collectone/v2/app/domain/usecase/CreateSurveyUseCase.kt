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
package org.openforis.collectone.v2.app.domain.usecase

import kotlinx.coroutines.flow.first
import org.openforis.collectone.v2.app.domain.model.CachedProfile
import org.openforis.collectone.v2.app.domain.model.FormTemplate
import org.openforis.collectone.v2.app.domain.model.MapThumbnailTheme
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.SurveyLifecycleState
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.SurveyThumbnailGenerator
import org.openforis.collectone.v2.app.domain.model.editor.FormIds
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.app.domain.model.editor.SurveyFormTemplates
import org.openforis.collectone.v2.app.domain.repository.LibraryRepository
import org.openforis.collectone.v2.app.domain.repository.SurveyEditorRepository
import org.openforis.collectone.v2.app.domain.repository.SurveyRepository
import org.openforis.collectone.v2.app.domain.repository.TransactionRunner
import org.openforis.collectone.v2.core.ids.RecordIds

/**
 * Creates a new survey owned by the signed-in user, optionally in an organization, and makes it the
 * active survey. The survey starts in [SurveyLifecycleState.DRAFT] mode with the templates of the
 * Purpose Packs chosen for it, or empty.
 *
 * Building the survey ([newSurvey]) is pure, so callers can hand out its ID right away; persisting
 * it ([invoke]) writes the survey list entry and a blank Survey editor draft in one transaction.
 */
class CreateSurveyUseCase(
  private val surveyRepository: SurveyRepository,
  private val surveyEditorRepository: SurveyEditorRepository,
  private val transactionRunner: TransactionRunner,
  /** Organization libraries, whose Purpose Packs seed new surveys; `null` disables seeding. */
  private val libraryRepository: LibraryRepository? = null,
  private val resolveLibraryUseCase: ResolveLibraryUseCase = ResolveLibraryUseCase(),
  /** Form IDs for Forms copied from templates. */
  private val newFormId: () -> String = FormIds::newFormId,
  /** IDs of new surveys (random UUIDs). */
  private val newSurveyId: () -> String = RecordIds::newId,
) {
  /**
   * A new survey titled [title] (or `"Untitled survey"` when blank) owned by [ownerEmail] in
   * [organization] (or personal when `null`), with a new random ID. [existingSurveys] picks the
   * thumbnail theme.
   */
  fun newSurvey(
    title: String,
    existingSurveys: List<SurveyPreviewItem>,
    organization: Organization?,
    ownerEmail: String,
  ): SurveyPreviewItem {
    val trimmedTitle = title.trim().ifBlank { DEFAULT_TITLE }
    val id = newSurveyId()
    return SurveyPreviewItem(
      id = id,
      title = trimmedTitle,
      description = "",
      location = organization?.name ?: "No survey area yet",
      coordinatesLabel = "",
      offlineSizeLabel = "0 MB",
      isDownloaded = false,
      thumbnailTheme =
        MapThumbnailTheme.entries[existingSurveys.size % MapThumbnailTheme.entries.size],
      entityCount = 0,
      thumbnail = SurveyThumbnailGenerator.forNewSurvey(id),
      ownerEmail = ownerEmail,
      organizationId = organization?.id,
      state = SurveyLifecycleState.DRAFT,
    )
  }

  /**
   * Stores [survey] (from [newSurvey]) with an editor draft owned by [survey]'s owner, named
   * [ownerName], and makes it the active survey.
   *
   * The draft records the Purpose Packs [purposeIds] and programs [programIds] chosen in "What will
   * this data be used for?", and starts with a copy of each of the packs' templates (from the
   * survey's resolved library, so packs and templates hidden by the organization are skipped).
   * Without purposes the draft starts empty, as before.
   */
  suspend operator fun invoke(
    survey: SurveyPreviewItem,
    ownerName: String,
    purposeIds: List<String> = emptyList(),
    programIds: List<String> = emptyList(),
  ) {
    val templates =
      if (purposeIds.isEmpty() || libraryRepository == null) {
        emptyList()
      } else {
        resolveLibraryUseCase
          .forOrganization(libraryRepository.observeLibraries().first(), survey.organizationId)
          .templatesForPurposes(purposeIds)
      }
    transactionRunner {
      surveyRepository.setSurveys(surveyRepository.getSurveys() + survey)
      surveyEditorRepository.saveDraft(
        survey.id,
        newDraft(survey, ownerName, purposeIds, programIds, templates),
      )
      surveyRepository.setActiveSurveyId(survey.id)
    }
  }

  /** The first editor draft of [survey], seeded with copies of [templates]. Pure. */
  fun newDraft(
    survey: SurveyPreviewItem,
    ownerName: String,
    purposeIds: List<String>,
    programIds: List<String>,
    templates: List<FormTemplate>,
  ): SurveyEditorDraft {
    val blank = SurveyEditorDraft.blank(survey.id, title = survey.title)
    val draft =
      blank.copy(
        details =
          blank.details.copy(
            organizationId = survey.organizationId,
            purposeIds = purposeIds.distinct(),
            programIds = programIds.distinct(),
          ),
        sharing =
          blank.sharing.copy(
            ownerEmail = survey.ownerEmail,
            ownerProfile = CachedProfile(ownerName),
          ),
      )
    return templates.fold(draft) { current, template ->
      SurveyFormTemplates.addTemplate(current, template, newFormId).first
    }
  }

  companion object {
    const val DEFAULT_TITLE = "Untitled survey"
  }
}
