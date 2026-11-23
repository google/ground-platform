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

import org.openforis.collectone.v2.app.domain.model.ImpactEvent
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.OrganizationLibrary
import org.openforis.collectone.v2.app.domain.model.SurveyLifecycleState
import org.openforis.collectone.v2.app.domain.model.SurveyPlaceItem
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.editor.SurveyAccess
import org.openforis.collectone.v2.app.domain.model.editor.SurveyDetails
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.app.domain.model.hasPlatformAdmin
import org.openforis.collectone.v2.web.domain.model.editor.SampleGenerationProgress
import org.openforis.collectone.v2.web.ui.formeditor.FormLibraryContext
import org.openforis.collectone.v2.web.ui.surveyoutcome.SurveyOutcomeCardState
import org.openforis.collectone.v2.web.ui.text.EditorText

/** Everything the Survey editor reads from the local data store, for the active survey. */
internal data class SurveyEditorData(
  val surveyId: String = "",
  val liveDraft: SurveyEditorDraft = SurveyEditorDraft.blank(surveyId = ""),
  val workingCopyDraft: SurveyEditorDraft? = null,
  val organizations: List<Organization> = emptyList(),
  val signedInUserEmail: String = "",
  val localPlaces: List<SurveyPlaceItem> = emptyList(),
  val submissionCountByDatasetId: Map<String, Int> = emptyMap(),
  /** Every stored organization library, keyed by organization ID. */
  val libraries: Map<String, OrganizationLibrary> = emptyMap(),
  /** The survey's list entry (lifecycle state and outcome), or `null` if it isn't stored. */
  val survey: SurveyPreviewItem? = null,
  /** "Data was used" events of the survey, newest first. */
  val activity: List<ImpactEvent> = emptyList(),
)

/** Session (non-persisted) state of the Survey editor. */
internal data class SurveyEditorSession(
  /** Survey the session belongs to; a different active survey starts a fresh session. */
  val surveyId: String? = null,
  /** The draft the edits started from, or `null` while the editor shows the live draft. */
  val opened: SurveyEditorDraft? = null,
  /** The edited draft, or `null` while the editor shows the live draft. */
  val draft: SurveyEditorDraft? = null,
  /**
   * Whether the editor is prompting the organizer to continue editing or discard a previous unsaved
   * working copy.
   */
  val isPromptingWorkingCopy: Boolean = false,
  val section: SurveyEditorSection = SurveyEditorSection.Details,
  val sidePanelWidthDp: Float = SurveyEditorUiState.DEFAULT_SIDE_PANEL_WIDTH_DP,
  val generation: SampleGenerationProgress? = null,
  val generationErrors: Map<String, EditorText> = emptyMap(),
  val organizationNotice: EditorText? = null,
  val isSaving: Boolean = false,
)

/**
 * The Survey editor's UI state, derived from what it read ([data]), its session, and the outcome
 * card. Pure, so tests can call it with plain values.
 *
 * The session can run ahead of [data]: a survey opened with [SurveyEditorViewModel.openSurveyDraft]
 * (e.g. just created by the wizard) has a session before the store reports it as active. Until they
 * match, what [data] says about its own survey (the lifecycle state, submission counts, activity,
 * and who may manage it) belongs to the previous survey and is ignored: the session's survey is
 * treated as an editable draft.
 *
 * @param hasLiveGeocoder whether place search is online; the locally stored places are offered only
 *   when it isn't.
 * @param libraryContextFor the dictionary context of the draft's details; see
 *   [SurveyLibraryHandler.contextFor].
 */
internal fun buildSurveyEditorUiState(
  data: SurveyEditorData,
  session: SurveyEditorSession,
  outcomeCard: SurveyOutcomeCardState?,
  hasLiveGeocoder: Boolean,
  libraryContextFor:
    (
      details: SurveyDetails,
      organizations: List<Organization>,
      email: String,
      libraries: Map<String, OrganizationLibrary>,
    ) -> FormLibraryContext,
): SurveyEditorUiState {
  val activeSurveyId = session.surveyId?.takeIf { it.isNotBlank() } ?: data.surveyId
  val dataMatchesSession = session.surveyId == null || session.surveyId == data.surveyId
  val edits = session.takeIf { it.draft != null && (dataMatchesSession || it.surveyId != null) }
  val draft = edits?.draft ?: data.liveDraft
  val opened = edits?.opened ?: data.liveDraft
  val hasChanges = draft.copy(nextKeyId = 0) != opened.copy(nextKeyId = 0)
  val isReadOnly = dataMatchesSession && data.survey?.state.isReadOnly()
  val lifecycleState =
    if (dataMatchesSession) data.survey?.state ?: SurveyLifecycleState.PUBLISHED
    else SurveyLifecycleState.DRAFT
  val submissionCounts = data.submissionCountByDatasetId.takeIf { dataMatchesSession }.orEmpty()
  return SurveyEditorUiState(
    surveyId = activeSurveyId,
    opened = opened,
    draft = draft,
    library =
      libraryContextFor(
        draft.details,
        data.organizations,
        data.signedInUserEmail,
        data.libraries,
      ),
    lifecycleState = lifecycleState,
    closedAt = data.survey?.closedAt.takeIf { dataMatchesSession },
    canManageSurvey = !dataMatchesSession || data.canManage(draft),
    activity = data.activity.takeIf { dataMatchesSession }.orEmpty(),
    organizations = data.organizations,
    signedInUserEmail = data.signedInUserEmail,
    localPlaces = data.localPlaces.takeUnless { hasLiveGeocoder }.orEmpty(),
    submissionCountByDatasetId = submissionCounts,
    isDirty = edits != null,
    isPromptingWorkingCopy = edits?.isPromptingWorkingCopy == true && hasChanges && !isReadOnly,
    isSaving = session.isSaving,
    section = session.section,
    sidePanelWidthDp = session.sidePanelWidthDp,
    generation = session.generation,
    generationErrors = session.generationErrors,
    organizationNotice = session.organizationNotice,
    outcomeCard = outcomeCard?.takeIf { it.surveyId == activeSurveyId },
  )
}

/** Whether a survey in this state is closed or archived, and so can't be edited. */
private fun SurveyLifecycleState?.isReadOnly(): Boolean =
  this == SurveyLifecycleState.CLOSED || this == SurveyLifecycleState.ARCHIVED

/** Whether the signed-in user may manage the survey of [draft] (its sharing and organization). */
private fun SurveyEditorData.canManage(draft: SurveyEditorDraft): Boolean =
  SurveyAccess.canManage(
    email = signedInUserEmail,
    sharing = draft.sharing,
    organization = organizations.firstOrNull { it.id == draft.details.organizationId },
    isPlatformAdmin = organizations.hasPlatformAdmin(signedInUserEmail),
  )
