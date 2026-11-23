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

import co.touchlab.kermit.Severity
import com.squareup.wire.Instant
import com.squareup.wire.ofEpochSecond
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
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
import org.openforis.collectone.v2.app.data.mapper.toKotlinInstant
import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.logging.recordAppLogs

/** Outbox drain against a scripted service: batching, identity, confirmation, and errors. */
class OutboxDrainerTest {
  private var now: Instant = ofEpochSecond(1_800_000_000L, 0L)
  private val clock =
    object : Clock {
      override fun now(): kotlin.time.Instant = this@OutboxDrainerTest.now.toKotlinInstant()
    }
  private val store = InMemoryLocalStore()
  private val recorder = OutboxRecorder(clock = clock)
  private val seed = SeedUserDataService()

  private val dataRequests = mutableListOf<MutateDataRequest>()
  private val surveyRequests = mutableListOf<MutateSurveyRequest>()
  private var dataFailure: Exception? = null
  private var surveyFailure: Exception? = null
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
        surveyFailure?.let { throw it }
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
    assertEquals(DomainError.Network, pending.lastError)
    assertEquals("unavailable", pending.lastErrorDetail)
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
  fun failedSend_isLoggedAsAWarning_withoutTheAccountsEmail() {
    recordLabel("e1", "A")
    dataFailure = GrpcWebException(7, "/MutateData", "ada@example.org may not edit this survey")

    val logged = recordAppLogs { runNow { drainer.drain() } }

    val warning = logged.single()
    assertEquals(Severity.Warn, warning.severity)
    assertEquals("OutboxDrainer", warning.tag)
    assertTrue(
      warning.message.startsWith("Couldn't send 1 outbox mutation(s) to "),
      warning.message,
    )
    assertTrue("/MutateData: <email> may not edit this survey" in warning.message, warning.message)
    assertFalse("ada@example.org" in warning.message, warning.message)
  }

  @Test
  fun repeatedTransientFailures_neverBackOffForMoreThanAMinute() {
    recordLabel("e1", "A")
    dataFailure = GrpcWebException(14, "/MutateData", "unavailable")
    val waits = mutableListOf<Long>()

    repeat(12) {
      runNow { drainer.drain() }
      val notBefore = outbox().single().notBefore!!
      waits += (notBefore.toKotlinInstant() - now.toKotlinInstant()).inWholeMilliseconds
      now = notBefore
    }

    assertEquals(12, outbox().single().attempts)
    assertTrue(waits.all { it in 1L..60_000L }, "waits out of range: $waits")
    assertTrue(waits.last() >= 48_000L, "the backoff should have reached its cap: $waits")
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
  fun staleBase_asksForAPull_thenRejectsAndRevertsWhenFailedPreconditionPersists() {
    runNow {
      store.transaction {
        Rebaser(this)
          .acceptData(SURVEY, listOf(EntityRecord(entity_id = "e1", label = "Server")), emptyList())
      }
    }
    recordLabel("e1", "A")
    runNow { store.transaction { Rebaser(this).rebaseData(SURVEY, listOf("e1"), emptyList()) } }
    assertEquals("A", runNow { store.transaction { entity(SURVEY, "e1") } }!!.label)
    dataFailure = GrpcWebException(9, "/MutateData", "This survey is closed and read-only.")

    // Attempt 1: asks for a pull in case the base was merely stale.
    val first = runNow { drainer.drain() }
    assertTrue(first.needsPull)
    assertEquals(OutboxState.RETRYING, outbox().single().state)
    assertEquals(1, outbox().single().attempts)

    // Clear backoff (as SyncCoordinator does right after pullIncrementalCatchUp) and drain again:
    // permanent business-rule FAILED_PRECONDITION rejects and reverts optimistic local state.
    runNow {
      store.transaction {
        putOutbox(outbox().map { it.copy(notBefore = null) })
      }
    }
    val second = runNow { drainer.drain() }
    assertEquals(1, second.rejected)
    val rejected = outbox().single()
    assertEquals(OutboxState.REJECTED, rejected.state)
    assertEquals(DomainError.NotEditable, rejected.lastError)
    assertEquals("This survey is closed and read-only.", rejected.lastErrorDetail)
    assertEquals("Server", runNow { store.transaction { entity(SURVEY, "e1") } }!!.label)
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

  @Test
  fun resourceExhaustedOnNewSurvey_rejectsAllMutationsInScope_andRemovesLocalSurvey() {
    runNow {
      store.transaction {
        recorder.record(
          this,
          MutationTarget.Survey(SURVEY, workingCopy = false),
          Rebaser.surveyKey(SURVEY),
          MutationPayload.Survey(
            SurveyMutation(upsert = SurveyDef(survey_id = SURVEY, title = "New"))
          ),
        )
        recorder.record(
          this,
          MutationTarget.Survey(SURVEY, workingCopy = true),
          Rebaser.surveyKey(SURVEY),
          MutationPayload.Survey(SurveyMutation(upsert = SurveyDef(title = "Edited"))),
          debounceMillis = 1_500L,
        )
      }
    }
    now = ofEpochSecond(1_800_000_002L, 0L)
    surveyFailure = GrpcWebException(8, "/MutateSurvey", "Survey quota exceeded")

    val result = runNow { drainer.drain() }

    assertEquals(2, result.rejected)
    assertNull(result.nextAttemptAt)
    assertTrue(outbox().all { it.state == OutboxState.REJECTED })
  }

  @Test
  fun nextAttemptAt_usesScopeMaxAndIgnoresExpiredDebounces() {
    runNow {
      store.transaction {
        // First mutation (workingCopy = false) fails transiently and backs off for ~2s.
        recorder.record(
          this,
          MutationTarget.Survey(SURVEY, workingCopy = false),
          Rebaser.surveyKey(SURVEY),
          MutationPayload.Survey(
            SurveyMutation(upsert = SurveyDef(survey_id = SURVEY, title = "New"))
          ),
        )
      }
    }
    surveyFailure = GrpcWebException(14, "/MutateSurvey", "unavailable")
    val firstDrain = runNow { drainer.drain() }
    val retryAt = firstDrain.nextAttemptAt!!

    // While the first mutation is backing off until retryAt (~T+2s), a working-copy edit is
    // recorded with a 500ms debounce (T+0.5s).
    runNow {
      store.transaction {
        recorder.record(
          this,
          MutationTarget.Survey(SURVEY, workingCopy = true),
          Rebaser.surveyKey(SURVEY),
          MutationPayload.Survey(SurveyMutation(upsert = SurveyDef(title = "Edited"))),
          debounceMillis = 500L,
        )
      }
    }

    // Advance past the 500ms debounce (T+1s), still before retryAt.
    now = ofEpochSecond(1_800_000_001L, 0L)
    val secondDrain = runNow { drainer.drain() }

    // The scope is still held back by retryAt, so nextAttemptAt must be retryAt (not the expired
    // T+0.5s debounce, which would cause a 0ms retry loop).
    assertEquals(retryAt, secondDrain.nextAttemptAt)
  }

  private companion object {
    const val SURVEY = "s1"
  }
}
