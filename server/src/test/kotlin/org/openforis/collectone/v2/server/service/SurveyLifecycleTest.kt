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
package org.openforis.collectone.v2.server.service

import com.squareup.wire.ofEpochSecond
import io.grpc.Status
import io.grpc.StatusException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import openforis.collectone.v1.data.AcquirePlotLockRequest
import openforis.collectone.v1.data.DataMutation
import openforis.collectone.v1.data.EntityMutation
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.MutateDataRequest
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.survey.AcceptSurveyInviteRequest
import openforis.collectone.v1.survey.AclEntry
import openforis.collectone.v1.survey.GetSurveyRequest
import openforis.collectone.v1.survey.MutateOrganizationRequest
import openforis.collectone.v1.survey.MutateSurveyRequest
import openforis.collectone.v1.survey.Organization
import openforis.collectone.v1.survey.OrganizationMutation
import openforis.collectone.v1.survey.QuotaLimits
import openforis.collectone.v1.survey.Role
import openforis.collectone.v1.survey.SurveyAcl
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyMutation
import openforis.collectone.v1.survey.SurveyOutcome
import openforis.collectone.v1.survey.SurveyState
import openforis.collectone.v1.userdata.UserDataSeed
import org.openforis.collectone.v2.server.service.ServiceTestSeed.DATASET
import org.openforis.collectone.v2.server.service.ServiceTestSeed.ENTITY_A
import org.openforis.collectone.v2.server.service.ServiceTestSeed.ORGANIZATION
import org.openforis.collectone.v2.server.service.ServiceTestSeed.SURVEY

/**
 * Survey lifecycle preconditions on [ServiceTestSeed]: a `CLOSED` or `ARCHIVED` survey refuses
 * design, ACL, invite, and data edits, but still accepts lifecycle changes, outcomes, trash, and
 * discarding its working copy; and a survey over its design quota can still change state.
 */
open class SurveyLifecycleTest {
  /** A backend loaded with [seed]; overridden to test other stores. */
  protected open fun newBackend(seed: UserDataSeed): UserDataBackend = inMemoryBackend(seed)

  private val backend by lazy { newBackend(ServiceTestSeed.seed) }

  /** When [closeSurvey] closes a survey: 2026-10-30T12:00:00Z. */
  private val closedAt = ofEpochSecond(1_793_361_600L, 0L)

  private suspend fun mutateSurvey(
    vararg mutations: SurveyMutation,
    surveyId: String = SURVEY,
    workingCopy: Boolean = false,
  ) =
    backend.MutateSurvey(
      MutateSurveyRequest(
        survey_id = surveyId,
        working_copy = workingCopy,
        mutations = mutations.toList(),
      )
    )

  private suspend fun setState(state: SurveyState, surveyId: String = SURVEY) =
    mutateSurvey(SurveyMutation(upsert = SurveyDef(state = state)), surveyId = surveyId)

  private suspend fun inviteCollector(inviteToken: String) =
    mutateSurvey(
      SurveyMutation(
        acl_upsert =
          SurveyAcl(
            entries =
              listOf(
                AclEntry(
                  email = "invitee@example.org",
                  role = Role.DATA_COLLECTOR,
                  invitation_status = AclEntry.InvitationStatus.PENDING,
                  invite_token = inviteToken,
                )
              )
          )
      )
    )

  private suspend fun closeSurvey(surveyId: String = SURVEY) =
    mutateSurvey(
      SurveyMutation(upsert = SurveyDef(state = SurveyState.CLOSED, closed_at = closedAt)),
      surveyId = surveyId,
    )

  private suspend fun archiveSurvey() {
    closeSurvey()
    setState(SurveyState.ARCHIVED)
  }

  private suspend fun failureOf(call: suspend () -> Unit): Status.Code =
    assertFailsWith<StatusException> { call() }.status.code

  private suspend fun savedSurvey(surveyId: String = SURVEY): SurveyDef? =
    backend.GetSurvey(GetSurveyRequest(survey_id = surveyId)).survey

  private fun outcome(vararg outcomes: SurveyOutcome.Outcome) =
    SurveyMutation(upsert = SurveyDef(outcome = SurveyOutcome(outcomes = outcomes.toList())))

  @Test
  fun closedSurvey_refusesTitleEdits() = runTest {
    closeSurvey()

    val failure = failureOf {
      mutateSurvey(SurveyMutation(upsert = SurveyDef(title = "Disallowed title")))
    }

    assertEquals(Status.Code.FAILED_PRECONDITION, failure)
  }

  @Test
  fun closedSurvey_refusesWorkingCopyEdits() = runTest {
    closeSurvey()

    val failure = failureOf {
      mutateSurvey(
        SurveyMutation(upsert = SurveyDef(title = "Disallowed draft")),
        workingCopy = true,
      )
    }

    assertEquals(Status.Code.FAILED_PRECONDITION, failure)
  }

  @Test
  fun closedSurvey_refusesSavingTheWorkingCopy() = runTest {
    closeSurvey()

    val failure = failureOf { mutateSurvey(SurveyMutation(save_working_copy = true)) }

    assertEquals(Status.Code.FAILED_PRECONDITION, failure)
  }

  @Test
  fun closedSurvey_refusesAclEdits() = runTest {
    closeSurvey()

    val failure = failureOf {
      mutateSurvey(SurveyMutation(acl_upsert = SurveyAcl(default_role = Role.VIEWER)))
    }

    assertEquals(Status.Code.FAILED_PRECONDITION, failure)
  }

  @Test
  fun closedSurvey_refusesInvitesSentBeforeItClosed() = runTest {
    inviteCollector("invite-token")
    closeSurvey()

    val failure = failureOf {
      backend.AcceptSurveyInvite(
        AcceptSurveyInviteRequest(
          survey_id = SURVEY,
          invite_token = "invite-token",
          user_id = "uid-late-invitee",
        )
      )
    }

    assertEquals(Status.Code.FAILED_PRECONDITION, failure)
  }

  @Test
  fun closedSurvey_refusesDataEdits() = runTest {
    closeSurvey()

    val failure = failureOf {
      backend.MutateData(
        MutateDataRequest(
          survey_id = SURVEY,
          mutations =
            listOf(
              DataMutation(
                mutation_id = "mut-closed-entity",
                entity_mutation =
                  EntityMutation(
                    entity_id = testId("closed-entity"),
                    dataset_id = DATASET,
                    upsert = EntityRecord(entity_id = testId("closed-entity"), label = "Nope"),
                  ),
              )
            ),
        )
      )
    }

    assertEquals(Status.Code.FAILED_PRECONDITION, failure)
  }

  @Test
  fun closedSurvey_refusesPlotLocks() = runTest {
    closeSurvey()

    val failure = failureOf {
      backend.AcquirePlotLock(
        AcquirePlotLockRequest(
          survey_id = SURVEY,
          dataset_id = DATASET,
          entity_id = ENTITY_A,
          user_id = "uid-collector-one",
        )
      )
    }

    assertEquals(Status.Code.FAILED_PRECONDITION, failure)
  }

  @Test
  fun closedSurvey_allowsDiscardingTheWorkingCopy() = runTest {
    closeSurvey()

    mutateSurvey(SurveyMutation(discard_working_copy = true), workingCopy = true)

    assertNull(
      backend
        .GetSurvey(
          GetSurveyRequest(survey_id = SURVEY, working_copy = GetSurveyRequest.WorkingCopy())
        )
        .survey
    )
  }

  @Test
  fun closedSurvey_allowsSavingAnOutcome() = runTest {
    closeSurvey()

    mutateSurvey(outcome(SurveyOutcome.Outcome.LAND_TITLING))

    assertEquals(
      listOf(SurveyOutcome.Outcome.LAND_TITLING),
      savedSurvey()?.outcome?.outcomes,
    )
  }

  @Test
  fun closedSurvey_canBeArchived() = runTest {
    closeSurvey()

    setState(SurveyState.ARCHIVED)

    assertEquals(SurveyState.ARCHIVED, savedSurvey()?.state)
  }

  @Test
  fun archivedSurvey_cannotBeClosedAgain() = runTest {
    archiveSurvey()

    val failure = failureOf { setState(SurveyState.CLOSED) }

    assertEquals(Status.Code.FAILED_PRECONDITION, failure)
  }

  @Test
  fun archivedSurvey_canBeTrashedAndRestored() = runTest {
    archiveSurvey()
    val trashTime = ofEpochSecond(5L, 0L)

    mutateSurvey(SurveyMutation(upsert = SurveyDef(trash_time = trashTime)))
    mutateSurvey(SurveyMutation(delete = SurveyDef(trash_time = trashTime)))

    assertNull(savedSurvey()?.trash_time)
  }

  @Test
  fun archivedSurvey_switchedBackToPublished_acceptsTitleEdits() = runTest {
    archiveSurvey()
    setState(SurveyState.PUBLISHED)

    mutateSurvey(SurveyMutation(upsert = SurveyDef(title = "Reopened title")))

    assertEquals("Reopened title", savedSurvey()?.title)
  }

  /** Creates a two-form survey, then lowers its organization's form quota to one. */
  private suspend fun createSurveyOverItsFormQuota(): String {
    val surveyId = testId("survey-over-quota")
    mutateSurvey(
      SurveyMutation(
        upsert =
          SurveyDef(
            survey_id = surveyId,
            organization_id = ORGANIZATION,
            title = "Legacy multi-form survey",
            forms = listOf(FormDef(form_id = "f1"), FormDef(form_id = "f2")),
          )
      ),
      surveyId = surveyId,
    )
    backend.MutateOrganization(
      MutateOrganizationRequest(
        organization_id = ORGANIZATION,
        mutations =
          listOf(
            OrganizationMutation(
              upsert = Organization(quota_limits = QuotaLimits(max_forms_per_survey = 1))
            )
          ),
      )
    )
    return surveyId
  }

  @Test
  fun overQuotaSurvey_canBeClosed() = runTest {
    val surveyId = createSurveyOverItsFormQuota()

    closeSurvey(surveyId)

    assertEquals(SurveyState.CLOSED, savedSurvey(surveyId)?.state)
  }

  @Test
  fun overQuotaClosedSurvey_canSaveAnOutcome() = runTest {
    val surveyId = createSurveyOverItsFormQuota()
    closeSurvey(surveyId)

    mutateSurvey(outcome(SurveyOutcome.Outcome.NOT_YET), surveyId = surveyId)

    assertEquals(listOf(SurveyOutcome.Outcome.NOT_YET), savedSurvey(surveyId)?.outcome?.outcomes)
  }

  @Test
  fun overQuotaClosedSurvey_canSwitchToDraft() = runTest {
    val surveyId = createSurveyOverItsFormQuota()
    closeSurvey(surveyId)

    setState(SurveyState.DRAFT, surveyId = surveyId)

    assertEquals(SurveyState.DRAFT, savedSurvey(surveyId)?.state)
  }
}
