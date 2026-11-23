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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.domain.model.SurveyLifecycleState
import org.openforis.collectone.v2.app.domain.model.SurveyPlaceItem
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.editor.SurveyDetails
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.web.ui.formeditor.FormLibraryContext
import org.openforis.collectone.v2.web.ui.surveyoutcome.SurveyOutcomeCardState

/** [buildSurveyEditorUiState]: how the editor's data and session combine into its UI state. */
class SurveyEditorStateTest {
  private val live =
    SurveyEditorDraft.blank(surveyId = "s1", title = "Forest plots").let {
      it.copy(sharing = it.sharing.copy(ownerEmail = "owner@example.org"))
    }
  private val edited = live.copy(details = live.details.copy(title = "Forest plots 2026"))

  private fun survey(state: SurveyLifecycleState, closedAt: String? = null) =
    SurveyPreviewItem(
      id = "s1",
      title = "Forest plots",
      description = "",
      location = "",
      isDownloaded = true,
      entityCount = 0,
      state = state,
      closedAt = closedAt,
    )

  private val data =
    SurveyEditorData(
      surveyId = "s1",
      liveDraft = live,
      signedInUserEmail = "owner@example.org",
      submissionCountByDatasetId = mapOf("plots" to 3),
      survey = survey(SurveyLifecycleState.PUBLISHED),
    )

  private fun build(
    data: SurveyEditorData = this.data,
    session: SurveyEditorSession = SurveyEditorSession(surveyId = "s1"),
    outcomeCard: SurveyOutcomeCardState? = null,
    hasLiveGeocoder: Boolean = false,
    libraryContextFor: (SurveyDetails) -> FormLibraryContext = { FormLibraryContext() },
  ) =
    buildSurveyEditorUiState(data, session, outcomeCard, hasLiveGeocoder) { details, _, _, _ ->
      libraryContextFor(details)
    }

  @Test
  fun beforeTheFirstEdit_showsTheLiveDraft() {
    val ui = build()

    assertEquals(live, ui.draft)
    assertEquals(live, ui.opened)
    assertFalse(ui.isDirty)
    assertEquals(SurveyLifecycleState.PUBLISHED, ui.lifecycleState)
    assertEquals(mapOf("plots" to 3), ui.submissionCountByDatasetId)
    assertTrue(ui.canManageSurvey, "the owner manages the survey")
  }

  @Test
  fun afterAnEdit_showsTheEditedDraftAgainstTheOpenedOne() {
    val ui = build(session = SurveyEditorSession(surveyId = "s1", opened = live, draft = edited))

    assertEquals(edited, ui.draft)
    assertEquals(live, ui.opened)
    assertTrue(ui.isDirty)
  }

  @Test
  fun sessionAheadOfTheStore_ignoresWhatTheStoreSaysAboutThePreviousSurvey() {
    val created = SurveyEditorDraft.blank(surveyId = "s2", title = "New survey")
    val ui =
      build(
        data =
          data.copy(
            survey = survey(SurveyLifecycleState.CLOSED, closedAt = "2026-01-01T00:00:00Z"),
            signedInUserEmail = "stranger@example.org",
          ),
        session = SurveyEditorSession(surveyId = "s2", opened = created, draft = created),
      )

    assertEquals("s2", ui.surveyId)
    assertEquals(created, ui.draft)
    assertEquals(SurveyLifecycleState.DRAFT, ui.lifecycleState)
    assertFalse(ui.isReadOnly)
    assertNull(ui.closedAt)
    assertTrue(ui.canManageSurvey)
    assertTrue(ui.submissionCountByDatasetId.isEmpty())
    assertTrue(ui.activity.isEmpty())
  }

  @Test
  fun closedOrArchivedSurvey_isReadOnly_andDoesNotPromptForItsWorkingCopy() {
    for (state in listOf(SurveyLifecycleState.CLOSED, SurveyLifecycleState.ARCHIVED)) {
      val ui =
        build(
          data = data.copy(survey = survey(state)),
          session =
            SurveyEditorSession(
              surveyId = "s1",
              opened = live,
              draft = edited,
              isPromptingWorkingCopy = true,
            ),
        )

      assertTrue(ui.isReadOnly, "$state is read-only")
      assertFalse(ui.isPromptingWorkingCopy, "$state doesn't prompt")
    }
  }

  @Test
  fun workingCopyPrompt_needsChangesBeyondTheNextKeyId() {
    fun prompt(draft: SurveyEditorDraft) =
      build(
          session =
            SurveyEditorSession(
              surveyId = "s1",
              opened = live,
              draft = draft,
              isPromptingWorkingCopy = true,
            )
        )
        .isPromptingWorkingCopy

    assertTrue(prompt(edited))
    assertFalse(prompt(live.copy(nextKeyId = live.nextKeyId + 5)), "only the key counter moved")
  }

  @Test
  fun canManageSurvey_followsTheDraftsSharing() {
    assertFalse(build(data = data.copy(signedInUserEmail = "stranger@example.org")).canManageSurvey)
  }

  @Test
  fun localPlaces_areOfferedOnlyWithoutALiveGeocoder() {
    val place =
      SurveyPlaceItem(
        id = "p1",
        name = "Nyeri",
        categoryLabel = "Town",
        regionSubtitle = "Kenya",
        position = LatLng(-0.42, 36.95),
        normalizedX = 0f,
        normalizedY = 0f,
      )
    val withPlaces = data.copy(localPlaces = listOf(place))

    assertEquals(listOf(place), build(data = withPlaces).localPlaces)
    assertTrue(build(data = withPlaces, hasLiveGeocoder = true).localPlaces.isEmpty())
  }

  @Test
  fun outcomeCard_isShownOnlyForTheActiveSurvey() {
    val card = SurveyOutcomeCardState(surveyId = "s1", surveyTitle = "Forest plots")

    assertEquals(card, build(outcomeCard = card).outcomeCard)
    assertNull(build(outcomeCard = card.copy(surveyId = "other")).outcomeCard)
  }

  @Test
  fun library_isResolvedFromTheShownDraftsDetails() {
    val seen = mutableListOf<SurveyDetails>()
    build(
      session = SurveyEditorSession(surveyId = "s1", opened = live, draft = edited),
      libraryContextFor = { details ->
        seen += details
        FormLibraryContext()
      },
    )

    assertEquals(listOf(edited.details), seen)
  }
}
