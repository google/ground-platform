/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.app.data.sync

import com.squareup.wire.Instant
import com.squareup.wire.ofEpochSecond
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import openforis.collectone.v1.data.AppliedMutationWatermark
import openforis.collectone.v1.data.DataMutation
import openforis.collectone.v1.data.DataMutationResult
import openforis.collectone.v1.data.DataServiceServer
import openforis.collectone.v1.data.EntityMutation
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.GetAppliedMutationsRequest
import openforis.collectone.v1.data.GetAppliedMutationsResponse
import openforis.collectone.v1.data.MutateDataRequest
import openforis.collectone.v1.data.MutateDataResponse
import openforis.collectone.v1.survey.MutateSurveyRequest
import openforis.collectone.v1.survey.MutateSurveyResponse
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyMutation
import openforis.collectone.v1.survey.SurveyServiceServer
import org.openforis.collectone.v2.app.client.grpc.CollectOneGrpcClient
import org.openforis.collectone.v2.app.client.grpc.GrpcWebException
import org.openforis.collectone.v2.app.data.datasource.local.store.InMemoryLocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationPayload
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationTarget
import org.openforis.collectone.v2.app.data.datasource.local.store.OutboxState
import org.openforis.collectone.v2.app.data.datasource.local.store.SurveyBase
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.datasource.remote.SeedUserDataService

/** Outbox drain against a scripted service: batching, identity, confirmation, and errors. */
class OutboxDrainerTest {
  private var now: Instant = ofEpochSecond(1_800_000_000L, 0L)
  private val clock = SyncClock { now }
  private val store = InMemoryLocalStore()
  private val recorder = OutboxRecorder(clock = clock)
  private val seed = SeedUserDataService()

  private val dataRequests = mutableListOf<MutateDataRequest>()
  private val surveyRequests = mutableListOf<MutateSurveyRequest>()
  private var dataFailure: Exception? = null
  private var applied: List<AppliedMutationWatermark> = emptyList()
  private var serverRevision = 10L

  private val dataService =
    object : DataServiceServer by seed {
      override suspend fun MutateData(request: MutateDataRequest): MutateDataResponse {
        dataFailure?.let { throw it }
        dataRequests += request
        return MutateDataResponse(
          results =
            request.mutations.map { m ->
              val em = m.entity_mutation!!
              val label = em.upsert?.label
              DataMutationResult(
                entity =
                  EntityRecord(
                    entity_id = em.entity_id,
                    survey_id = request.survey_id,
                    label = label.orEmpty(),
                    revision = ++serverRevision,
                    commit_timestamp = ofEpochSecond(1_900_000_000L + serverRevision, 0L),
                  )
              )
            }
        )
      }

      override suspend fun GetAppliedMutations(
        request: GetAppliedMutationsRequest
      ): GetAppliedMutationsResponse = GetAppliedMutationsResponse(watermarks = applied)
    }

  private val surveyService =
    object : SurveyServiceServer by seed {
      override suspend fun MutateSurvey(request: MutateSurveyRequest): MutateSurveyResponse {
        surveyRequests += request
        return MutateSurveyResponse(
          survey_id = request.survey_id,
          survey =
            SurveyDef(survey_id = request.survey_id, title = "Server draft", record_version = 2L),
        )
      }
    }

  private val drainer =
    OutboxDrainer(store, CollectOneGrpcClient(surveyService, seed, seed, dataService), clock)

  private fun recordLabel(entityId: String, label: String) = runNow {
    store.transaction {
      recorder.record(
        this,
        MutationTarget.Data(SURVEY),
        Rebaser.entityKey(entityId),
        MutationPayload.Data(
          DataMutation(
            entity_mutation =
              EntityMutation(entity_id = entityId, upsert = EntityRecord(label = label))
          )
        ),
        baseRevision = 3L,
      )
    }
  }

  private fun outbox() = runNow { store.transaction { outbox() } }

  @Test
  fun drain_sendsOneBatchPerTarget_withIdentity_andAcceptsResultsAsBases() {
    runNow {
      store.transaction {
        Rebaser(this)
          .acceptData(SURVEY, listOf(EntityRecord(entity_id = "e1", label = "Old")), emptyList())
      }
    }
    recordLabel("e1", "A")
    recordLabel("e2", "B")

    val result = runNow { drainer.drain() }

    assertEquals(2, result.sent)
    val request = dataRequests.single()
    val clientId = runNow { store.transaction { syncState().clientId } }
    assertEquals(listOf(1L, 2L), request.mutations.map { it.client_seq })
    assertTrue(request.mutations.all { it.client_id == clientId && it.mutation_id.isNotBlank() })
    assertEquals(3L, request.mutations.first().entity_mutation!!.base_revision)
    assertEquals(emptyList(), outbox())
    val base = runNow { store.transaction { entityBase(SURVEY, "e1") } }!!
    assertEquals("A", base.label)
    assertEquals("A", runNow { store.transaction { entity(SURVEY, "e1") } }!!.label)
  }

  @Test
  fun transientFailure_keepsTheMutation_andBacksOff() {
    recordLabel("e1", "A")
    dataFailure = GrpcWebException(14, "/MutateData", "unavailable")

    val result = runNow { drainer.drain() }

    assertEquals(0, result.sent)
    val pending = outbox().single()
    assertEquals(OutboxState.RETRYING, pending.state)
    assertEquals(1, pending.attempts)
    assertEquals("unavailable", pending.lastError)
    assertNotNull(pending.notBefore)
    assertEquals(pending.notBefore, result.nextAttemptAt)

    // Not resent before the backoff expires.
    dataFailure = null
    runNow { drainer.drain() }
    assertTrue(dataRequests.isEmpty())
    now = ofEpochSecond(1_800_000_100L, 0L)
    assertEquals(1, runNow { drainer.drain() }.sent)
  }

  @Test
  fun permanentFailure_rejectsTheMutation_andRevertsTheLocalCopy() {
    runNow {
      store.transaction {
        Rebaser(this)
          .acceptData(SURVEY, listOf(EntityRecord(entity_id = "e1", label = "Server")), emptyList())
      }
    }
    recordLabel("e1", "Mine")
    runNow { store.transaction { Rebaser(this).rebaseData(SURVEY, listOf("e1"), emptyList()) } }
    assertEquals("Mine", runNow { store.transaction { entity(SURVEY, "e1") } }!!.label)
    dataFailure = GrpcWebException(7, "/MutateData", "permission denied")

    val result = runNow { drainer.drain() }

    assertEquals(1, result.rejected)
    assertEquals(OutboxState.REJECTED, outbox().single().state)
    assertEquals("Server", runNow { store.transaction { entity(SURVEY, "e1") } }!!.label)
  }

  @Test
  fun staleBase_asksForAPull() {
    recordLabel("e1", "A")
    dataFailure = GrpcWebException(9, "/MutateData", "stale")

    assertTrue(runNow { drainer.drain() }.needsPull)
    assertEquals(OutboxState.RETRYING, outbox().single().state)
  }

  @Test
  fun mutationsTheServerAlreadyApplied_areDroppedWithoutResending() {
    recordLabel("e1", "A")
    recordLabel("e2", "B")
    applied = listOf(AppliedMutationWatermark(scope = "surveys/$SURVEY/data", client_seq = 1L))

    runNow { drainer.drain() }

    assertEquals(listOf(2L), dataRequests.single().mutations.map { it.client_seq })
  }

  @Test
  fun debouncedMutations_waitForTheirQuietPeriod() {
    runNow {
      store.transaction {
        putSurveyBase(
          SURVEY,
          SurveyBase(saved = SurveyDef(survey_id = SURVEY, title = "Saved", record_version = 1L)),
        )
        recorder.record(
          this,
          MutationTarget.Survey(SURVEY, workingCopy = true),
          Rebaser.surveyKey(SURVEY),
          MutationPayload.Survey(SurveyMutation(upsert = SurveyDef(title = "Draft"))),
          debounceMillis = 1_500L,
        )
      }
    }

    val waiting = runNow { drainer.drain() }
    assertTrue(surveyRequests.isEmpty())
    assertNotNull(waiting.nextAttemptAt)

    now = ofEpochSecond(1_800_000_002L, 0L)
    runNow { drainer.drain() }

    val request = surveyRequests.single()
    assertTrue(request.working_copy)
    assertEquals(
      "Server draft",
      runNow { store.transaction { surveyBase(SURVEY) } }!!.workingCopy?.title,
    )
  }

  @Test
  fun surveyResponseOlderThanTheStoredBase_isIgnored() {
    runNow {
      store.transaction {
        // A poll already stored a newer copy (record version 5) than the response will carry (2).
        putSurveyBase(
          SURVEY,
          SurveyBase(saved = SurveyDef(survey_id = SURVEY, title = "Newer", record_version = 5L)),
        )
        recorder.record(
          this,
          MutationTarget.Survey(SURVEY, workingCopy = true),
          Rebaser.surveyKey(SURVEY),
          MutationPayload.Survey(SurveyMutation(upsert = SurveyDef(title = "Draft"))),
        )
      }
    }

    runNow { drainer.drain() }

    assertEquals(1, surveyRequests.size)
    val base = runNow { store.transaction { surveyBase(SURVEY) } }!!
    assertEquals("Newer", base.saved?.title)
    assertNull(base.workingCopy)
  }

  private companion object {
    const val SURVEY = "s1"
  }
}
