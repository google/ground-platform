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

import io.grpc.Status
import io.grpc.StatusException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import openforis.collectone.v1.data.AcquirePlotLockRequest
import openforis.collectone.v1.data.DataMutation
import openforis.collectone.v1.data.EntityMutation
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.MutateDataRequest
import openforis.collectone.v1.survey.AcceptSurveyInviteRequest
import openforis.collectone.v1.survey.MutateSurveyRequest
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyMutation
import openforis.collectone.v1.survey.SurveyState

/**
 * The `FAILED_PRECONDITION` messages a closed or archived survey answers each kind of change with.
 * Clients show them to users, so their wording is part of the contract.
 */
class ClosedSurveyPreconditionTest {
  private val backend = sampleBackend()

  private suspend fun setState(state: SurveyState) {
    backend.MutateSurvey(
      MutateSurveyRequest(
        survey_id = KENYA,
        mutations = listOf(SurveyMutation(upsert = SurveyDef(state = state))),
      )
    )
  }

  private suspend fun failureOf(call: suspend () -> Unit): Status {
    val error = assertFailsWith<StatusException> { call() }
    return error.status
  }

  private suspend fun editTitle() {
    backend.MutateSurvey(
      MutateSurveyRequest(
        survey_id = KENYA,
        mutations = listOf(SurveyMutation(upsert = SurveyDef(title = "Disallowed"))),
      )
    )
  }

  private suspend fun acceptInvite() {
    backend.AcceptSurveyInvite(
      AcceptSurveyInviteRequest(survey_id = KENYA, invite_token = "any-token", user_id = "uid-1")
    )
  }

  private suspend fun addEntity() {
    backend.MutateData(
      MutateDataRequest(
        survey_id = KENYA,
        mutations =
          listOf(
            DataMutation(
              mutation_id = "m-closed",
              entity_mutation =
                EntityMutation(
                  entity_id = NEW_ENTITY,
                  dataset_id = DATASET,
                  upsert = EntityRecord(entity_id = NEW_ENTITY, label = "Nope"),
                ),
            )
          ),
      )
    )
  }

  private suspend fun lockPlot() {
    backend.AcquirePlotLock(
      AcquirePlotLockRequest(survey_id = KENYA, dataset_id = DATASET, entity_id = SEEDED_ENTITY)
    )
  }

  @Test
  fun closedSurvey_refusesDesignEdits_asReadOnly() = runTest {
    setState(SurveyState.CLOSED)

    val status = failureOf { editTitle() }

    assertEquals(Status.Code.FAILED_PRECONDITION, status.code)
    assertEquals(
      "This survey is closed and read-only. Reopen it or switch it to draft to make changes.",
      status.description,
    )
  }

  @Test
  fun archivedSurvey_refusesInvites_asReadOnly() = runTest {
    setState(SurveyState.ARCHIVED)

    val status = failureOf { acceptInvite() }

    assertEquals(Status.Code.FAILED_PRECONDITION, status.code)
    assertEquals(
      "This survey is archived and read-only. Reopen it or switch it to draft to make changes.",
      status.description,
    )
  }

  @Test
  fun closedSurvey_refusesDataChanges() = runTest {
    setState(SurveyState.CLOSED)

    val status = failureOf { addEntity() }

    assertEquals(Status.Code.FAILED_PRECONDITION, status.code)
    assertEquals("This survey is closed and no longer accepts data changes.", status.description)
  }

  @Test
  fun archivedSurvey_refusesPlotLocks() = runTest {
    setState(SurveyState.ARCHIVED)

    val status = failureOf { lockPlot() }

    assertEquals(Status.Code.FAILED_PRECONDITION, status.code)
    assertEquals("This survey is archived and no longer accepts plot locks.", status.description)
  }

  private companion object {
    const val KENYA = "00000033-6b50-51cd-ac94-baea2ea06d65"
    const val DATASET = "coffee_parcels"
    const val SEEDED_ENTITY = "00000007-640e-5ed7-9874-4d6b6717f21d"
    val NEW_ENTITY = testId("closed-survey-entity")
  }
}
