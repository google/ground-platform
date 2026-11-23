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
package org.openforis.collectone.v2.app.data.sync

import com.squareup.wire.ofEpochSecond
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import openforis.collectone.v1.data.DataMutation
import openforis.collectone.v1.data.ReviewStatus
import openforis.collectone.v1.data.SubmissionMutation
import openforis.collectone.v1.data.SubmissionRecord
import openforis.collectone.v1.library.ConceptDef
import openforis.collectone.v1.library.LibraryBundle
import openforis.collectone.v1.library.LibraryMutation
import openforis.collectone.v1.library.OrganizationLibraryRecord
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyMutation
import openforis.collectone.v1.survey.SurveyState
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationPayload
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationTarget
import org.openforis.collectone.v2.app.data.datasource.local.store.OutboxState
import org.openforis.collectone.v2.app.data.datasource.local.store.PendingMutation
import org.openforis.collectone.v2.app.data.datasource.local.store.SurveyBase

/**
 * [RecordFolds] previews what the server will store: pending mutations apply in order on top of the
 * base, rejected ones are skipped, server bookkeeping stays the base's, and queued mutations that
 * change nothing are reported as no-ops.
 */
class RecordFoldsTest {
  private var seq = 0L

  private fun pending(
    target: MutationTarget,
    payload: MutationPayload,
    state: OutboxState = OutboxState.QUEUED,
  ) =
    PendingMutation(
      mutationId = "m${++seq}",
      clientSeq = seq,
      target = target,
      recordKey = "key",
      payload = payload,
      state = state,
    )

  // Submissions

  private fun submissionEdit(
    delete: SubmissionRecord? = null,
    upsert: SubmissionRecord? = null,
    state: OutboxState = OutboxState.QUEUED,
  ) =
    pending(
      MutationTarget.Data("s"),
      MutationPayload.Data(
        DataMutation(
          submission_mutation =
            SubmissionMutation(
              submission_id = "sub",
              form_id = "form",
              delete = delete,
              upsert = upsert,
            )
        )
      ),
      state,
    )

  private val submissionBase =
    SubmissionRecord(
      submission_id = "sub",
      form_id = "form",
      revision = 4L,
      commit_timestamp = ofEpochSecond(40L, 0L),
    )

  @Test
  fun submission_createdOffline_startsFromItsFormWithoutARevision() {
    val folded =
      RecordFolds.submission(
        null,
        "sub",
        listOf(submissionEdit(upsert = SubmissionRecord(entity_id = "e1"))),
      )

    assertEquals(
      SubmissionRecord(submission_id = "sub", form_id = "form", entity_id = "e1"),
      folded.value,
    )
  }

  @Test
  fun submission_edits_keepTheBasesBookkeepingAndSkipRejectedOnes() {
    val folded =
      RecordFolds.submission(
        submissionBase,
        "sub",
        listOf(
          submissionEdit(
            upsert = SubmissionRecord(review_status = ReviewStatus.REVIEW_STATUS_APPROVED)
          ),
          submissionEdit(
            upsert = SubmissionRecord(entity_id = "rejected"),
            state = OutboxState.REJECTED,
          ),
        ),
      )

    assertEquals(
      submissionBase.copy(review_status = ReviewStatus.REVIEW_STATUS_APPROVED),
      folded.value,
    )
  }

  @Test
  fun submission_deleted_foldsToNothing() {
    val folded =
      RecordFolds.submission(
        submissionBase,
        "sub",
        listOf(submissionEdit(upsert = SubmissionRecord(is_deleted = true))),
      )

    assertNull(folded.value)
  }

  @Test
  fun submission_queuedEditThatChangesNothing_isANoOp() {
    val noOp = submissionEdit(upsert = SubmissionRecord(form_id = "form"))
    val inFlight =
      submissionEdit(upsert = SubmissionRecord(form_id = "form"), state = OutboxState.IN_FLIGHT)

    val folded = RecordFolds.submission(submissionBase, "sub", listOf(noOp, inFlight))

    assertEquals(setOf(noOp.mutationId), folded.noOpMutationIds)
  }

  // Libraries

  private fun libraryEdit(
    delete: OrganizationLibraryRecord? = null,
    upsert: OrganizationLibraryRecord? = null,
  ) =
    pending(
      MutationTarget.Library("org"),
      MutationPayload.Library(LibraryMutation(delete = delete, upsert = upsert)),
    )

  @Test
  fun library_addedConcept_isInTheFoldAndARepeatIsANoOp() {
    val add =
      libraryEdit(
        upsert =
          OrganizationLibraryRecord(
            bundle = LibraryBundle(concepts = listOf(ConceptDef(id = "c1")))
          )
      )
    val repeat =
      libraryEdit(
        upsert =
          OrganizationLibraryRecord(
            bundle = LibraryBundle(concepts = listOf(ConceptDef(id = "c1")))
          )
      )

    val folded =
      RecordFolds.library(
        OrganizationLibraryRecord(organization_id = "org", version = 3L),
        "org",
        listOf(add, repeat),
      )

    assertEquals(listOf("c1"), folded.value?.bundle?.concepts?.map { it.id })
    assertEquals(3L, folded.value?.version)
    assertEquals(setOf(repeat.mutationId), folded.noOpMutationIds)
  }

  // Surveys

  private fun surveyEdit(mutation: SurveyMutation, workingCopy: Boolean = false) =
    pending(MutationTarget.Survey("s", workingCopy), MutationPayload.Survey(mutation))

  private val savedSurvey =
    SurveyDef(
      survey_id = "s",
      title = "Saved",
      state = SurveyState.PUBLISHED,
      saved_revision_number = 2L,
    )

  @Test
  fun survey_workingCopyEdits_startFromTheSavedRevision_andSavingPromotesThem() {
    val edit = surveyEdit(SurveyMutation(upsert = SurveyDef(title = "Draft")), workingCopy = true)

    val edited = RecordFolds.survey(SurveyBase(saved = savedSurvey), "s", listOf(edit)).value!!
    assertEquals("Saved", edited.saved?.title)
    assertEquals("Draft", edited.workingCopy?.title)

    val saved =
      RecordFolds.survey(
          SurveyBase(saved = savedSurvey),
          "s",
          listOf(edit, surveyEdit(SurveyMutation(save_working_copy = true))),
        )
        .value!!
    assertEquals("Draft", saved.saved?.title)
    assertEquals(3L, saved.saved?.saved_revision_number)
    assertEquals("3", saved.saved?.version)
    assertNull(saved.workingCopy)
  }

  @Test
  fun survey_discardingTheWorkingCopy_keepsTheSavedRevision() {
    val folded =
      RecordFolds.survey(
        SurveyBase(saved = savedSurvey, workingCopy = savedSurvey.copy(title = "Draft")),
        "s",
        listOf(surveyEdit(SurveyMutation(discard_working_copy = true))),
      )

    assertEquals(SurveyBase(saved = savedSurvey), folded.value)
  }

  @Test
  fun survey_trashed_disappearsAtOnce_andRestoringClearsTheTrashTime() {
    val trashedAt = ofEpochSecond(50L, 0L)
    val trash = surveyEdit(SurveyMutation(upsert = SurveyDef(trash_time = trashedAt)))

    val trashed = RecordFolds.survey(SurveyBase(saved = savedSurvey), "s", listOf(trash)).value!!
    assertEquals(SurveyState.DELETED, trashed.saved?.state)
    assertEquals(trashedAt, trashed.saved?.trash_time)

    val restored =
      RecordFolds.survey(
          SurveyBase(saved = savedSurvey.copy(trash_time = trashedAt)),
          "s",
          listOf(surveyEdit(SurveyMutation(delete = SurveyDef(trash_time = trashedAt)))),
        )
        .value!!
    assertNull(restored.saved?.trash_time)
  }

  @Test
  fun survey_createdOffline_startsAtSavedRevisionOne() {
    val create = surveyEdit(SurveyMutation(upsert = SurveyDef(survey_id = "s", title = "New")))

    val folded = RecordFolds.survey(null, "s", listOf(create))

    assertEquals(1L, folded.value?.saved?.saved_revision_number)
    assertEquals(emptySet(), folded.noOpMutationIds)
  }

  @Test
  fun survey_patchWithoutABase_foldsToNothingAndIsANoOp() {
    val patch = surveyEdit(SurveyMutation(upsert = SurveyDef(title = "Orphan")))

    val folded = RecordFolds.survey(null, "s", listOf(patch))

    assertNull(folded.value)
    assertEquals(setOf(patch.mutationId), folded.noOpMutationIds)
  }

  @Test
  fun survey_mutationsOfAnotherSurvey_areIgnored() {
    val other =
      pending(
        MutationTarget.Survey("other", workingCopy = false),
        MutationPayload.Survey(SurveyMutation(upsert = SurveyDef(title = "Other"))),
      )

    assertEquals(
      SurveyBase(saved = savedSurvey),
      RecordFolds.survey(SurveyBase(saved = savedSurvey), "s", listOf(other)).value,
    )
  }
}
