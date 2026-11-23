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
package org.openforis.collectone.v2.app.domain.model.editor

import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem

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
     * current title, description, organization, and (when the draft has none) owner, which the
     * survey list owns.
     */
    fun forSurvey(
      surveyId: String,
      stored: SurveyEditorDraft?,
      survey: SurveyPreviewItem?,
    ): SurveyEditorDraft {
      val base = stored ?: blank(surveyId)
      if (survey == null) return base
      val inheritSharingFromSurvey = stored == null || base.sharing.ownerEmail.isBlank()
      return base.copy(
        details =
          base.details.copy(
            title = survey.title,
            description = survey.description,
            organizationId = survey.organizationId,
            quotaLimits = if (stored != null) stored.details.quotaLimits else survey.quotaLimits,
          ),
        sharing =
          if (inheritSharingFromSurvey) {
            base.sharing.copy(
              ownerEmail = survey.ownerEmail,
              policy = survey.sharingPolicy,
              peerDataVisibility = survey.peerDataVisibility,
              collectorConsentRequired = survey.collectorConsentRequired,
              collectorConsentText = survey.collectorConsentText,
              defaultRole = survey.defaultRole,
            )
          } else {
            base.sharing
          },
      )
    }

    /**
     * The editor draft of a survey's unsaved [workingCopy]: like [forSurvey], but keeping the
     * working copy's own title, description, and organization. The survey list only learns of those
     * once the edits are saved, so taking them from [survey] would silently revert unsaved edits to
     * them (and hide a working copy that changes nothing else).
     */
    fun forWorkingCopy(
      surveyId: String,
      workingCopy: SurveyEditorDraft,
      survey: SurveyPreviewItem?,
    ): SurveyEditorDraft {
      val draft = forSurvey(surveyId, workingCopy, survey)
      return draft.copy(
        details =
          draft.details.copy(
            title = workingCopy.details.title,
            description = workingCopy.details.description,
            organizationId = workingCopy.details.organizationId,
          )
      )
    }
  }
}
