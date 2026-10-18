/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.app.data.sync

import com.squareup.wire.Instant
import groundplatform.v2.data.GetAppliedMutationsRequest
import groundplatform.v2.data.MutateDataRequest
import groundplatform.v2.library.MutateLibraryRequest
import groundplatform.v2.survey.MutateOrganizationRequest
import groundplatform.v2.survey.MutateSurveyRequest
import kotlin.coroutines.cancellation.CancellationException
import org.groundplatform.v2.app.client.grpc.CollectOneGrpcClient
import org.groundplatform.v2.app.client.grpc.GrpcWebException
import org.groundplatform.v2.app.data.datasource.local.store.LocalStore
import org.groundplatform.v2.app.data.datasource.local.store.LocalStoreTransaction
import org.groundplatform.v2.app.data.datasource.local.store.MutationPayload
import org.groundplatform.v2.app.data.datasource.local.store.MutationTarget
import org.groundplatform.v2.app.data.datasource.local.store.OutboxState
import org.groundplatform.v2.app.data.datasource.local.store.PendingMutation
import org.groundplatform.v2.app.data.datasource.local.store.SurveyBase

/** Outcome of one [OutboxDrainer.drain]. */
data class DrainResult(
  /** Number of mutations the server confirmed. */
  val sent: Int = 0,
  /** Number of mutations the server rejected permanently. */
  val rejected: Int = 0,
  /** True when a send failed because a base was stale; pull and rebase before retrying. */
  val needsPull: Boolean = false,
  /** Earliest time a waiting (debounced or backing off) mutation becomes sendable. */
  val nextAttemptAt: Instant? = null,
)

/**
 * Sends outbox mutations to the user data service (`docs/technical/sync-engine.md`, "Outbox Drain"
 * and "Mutation Semantics"):
 * 1. Confirms mutations the server already applied (`GetAppliedMutations`), e.g. after a lost
 *    response, and drops them.
 * 2. Sends the remaining mutations in `client_seq` order per scope, batching contiguous mutations
 *    for the same RPC target, one batch at a time per scope. A scope whose next mutation is
 *    debounced or backing off waits, so order is preserved.
 * 3. On success, removes the batch and accepts the returned records as new bases ([Rebaser]). On
 *    failure, retries transient errors with exponential backoff, asks for a pull on stale bases,
 *    and rejects mutations the server refuses.
 */
class OutboxDrainer(
  private val store: LocalStore,
  private val grpcClient: CollectOneGrpcClient,
  private val clock: SyncClock = SyncClock.System,
  private val maxBatchSize: Int = 100,
) {
  suspend fun drain(): DrainResult {
    resetInterruptedSends()
    confirmApplied()
    var result = DrainResult()
    val blockedScopes = mutableSetOf<String>()
    while (true) {
      val batch = nextBatch(blockedScopes) ?: break
      val outcome = send(batch)
      result =
        result.copy(
          sent = result.sent + outcome.sent,
          rejected = result.rejected + outcome.rejected,
          needsPull = result.needsPull || outcome.needsPull,
        )
      if (outcome.blocked) blockedScopes += batch.first().target.scope
    }
    return result.copy(nextAttemptAt = nextAttemptAt())
  }

  /** In-flight mutations left over from an interrupted send are sent again (they're idempotent). */
  private suspend fun resetInterruptedSends() = store.transaction {
    val outbox = outbox()
    if (outbox.any { it.state == OutboxState.IN_FLIGHT }) {
      putOutbox(
        outbox.map {
          if (it.state == OutboxState.IN_FLIGHT) it.copy(state = OutboxState.QUEUED) else it
        }
      )
    }
  }

  /** Drops pending mutations the server reports as applied and rebases their records. */
  private suspend fun confirmApplied() {
    val (clientId, scopes) =
      store.transaction {
        syncState().clientId to
          outbox().filter { it.state != OutboxState.REJECTED }.map { it.target.scope }.toSet()
      }
    if (clientId.isBlank() || scopes.isEmpty()) return
    val applied =
      try {
        grpcClient
          .getAppliedMutations(
            GetAppliedMutationsRequest(client_id = clientId, scopes = scopes.toList())
          )
          .watermarks
          .associate { it.scope to it.client_seq }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        return // Best effort: sending again is safe because the server skips replays.
      }
    if (applied.isEmpty()) return
    store.transaction {
      val confirmed =
        outbox().filter { m ->
          m.state != OutboxState.REJECTED && m.clientSeq <= (applied[m.target.scope] ?: 0L)
        }
      if (confirmed.isEmpty()) return@transaction
      val ids = confirmed.map { it.mutationId }.toSet()
      putOutbox(outbox().filterNot { it.mutationId in ids })
      rebaseRecords(confirmed)
    }
  }

  /**
   * The next batch to send: the first sendable scope's leading run of ready mutations for one
   * target, or `null` when nothing is ready.
   */
  private suspend fun nextBatch(blockedScopes: Set<String>): List<PendingMutation>? =
    store.transaction {
      val now = clock.now()
      val byScope = outbox().filter { it.state != OutboxState.REJECTED }.groupBy { it.target.scope }
      for ((scope, mutations) in byScope) {
        if (scope in blockedScopes) continue
        val ordered = mutations.sortedBy { it.clientSeq }
        // Debounce applies per target: a waiting mutation holds back everything after it.
        val waitUntil = ordered.mapNotNull { it.notBefore }.maxWithOrNull(InstantOrder)
        if (waitUntil != null && waitUntil.isAfter(now)) continue
        val head = ordered.first()
        if (head.state == OutboxState.IN_FLIGHT) continue
        val batch = ordered.takeWhile { it.target == head.target }.take(maxBatchSize)
        val ids = batch.map { it.mutationId }.toSet()
        putOutbox(
          outbox().map {
            if (it.mutationId in ids) {
              it.copy(state = OutboxState.IN_FLIGHT, attempts = it.attempts + 1)
            } else {
              it
            }
          }
        )
        return@transaction batch
      }
      null
    }

  private data class SendOutcome(
    val sent: Int = 0,
    val rejected: Int = 0,
    val needsPull: Boolean = false,
    val blocked: Boolean = false,
  )

  private suspend fun send(batch: List<PendingMutation>): SendOutcome {
    val clientId = store.transaction { syncState().clientId }
    return try {
      val accept = call(batch, clientId)
      store.transaction {
        val ids = batch.map { it.mutationId }.toSet()
        putOutbox(outbox().filterNot { it.mutationId in ids })
        accept(this)
        rebaseRecords(batch)
      }
      SendOutcome(sent = batch.size)
    } catch (e: CancellationException) {
      store.transaction { markBatch(batch, OutboxState.QUEUED, error = null, notBefore = null) }
      throw e
    } catch (e: Exception) {
      val status = (e as? GrpcWebException)?.status
      val message = (e as? GrpcWebException)?.description ?: e.message ?: e::class.simpleName
      when (status) {
        in PERMANENT_STATUSES -> {
          store.transaction {
            markBatch(batch, OutboxState.REJECTED, error = message, notBefore = null)
            rebaseRecords(batch)
          }
          SendOutcome(rejected = batch.size)
        }
        in STALE_BASE_STATUSES -> {
          store.transaction {
            markBatch(batch, OutboxState.RETRYING, error = message, notBefore = backoff(batch))
          }
          SendOutcome(needsPull = true, blocked = true)
        }
        else -> {
          store.transaction {
            markBatch(batch, OutboxState.RETRYING, error = message, notBefore = backoff(batch))
          }
          SendOutcome(blocked = true)
        }
      }
    }
  }

  /**
   * Sends [batch] with its identity fields stamped and returns how to accept the response into the
   * store.
   */
  private suspend fun call(
    batch: List<PendingMutation>,
    clientId: String,
  ): (LocalStoreTransaction) -> Unit =
    when (val target = batch.first().target) {
      is MutationTarget.Data -> {
        val response =
          grpcClient.mutateData(
            MutateDataRequest(
              survey_id = target.surveyId,
              mutations =
                batch.map { m ->
                  val mutation = (m.payload as MutationPayload.Data).mutation
                  mutation.copy(
                    mutation_id = m.mutationId,
                    client_id = clientId,
                    client_seq = m.clientSeq,
                    client_timestamp = m.clientTimestamp,
                    entity_mutation =
                      mutation.entity_mutation?.copy(base_revision = m.baseRevision),
                    submission_mutation =
                      mutation.submission_mutation?.copy(base_revision = m.baseRevision),
                  )
                },
            )
          )
        val handler: (LocalStoreTransaction) -> Unit = { tx ->
          Rebaser(tx)
            .acceptData(
              target.surveyId,
              response.results.mapNotNull { it.entity },
              response.results.mapNotNull { it.submission },
            )
        }
        handler
      }
      is MutationTarget.Survey -> {
        val response =
          grpcClient.mutateSurvey(
            MutateSurveyRequest(
              survey_id = target.surveyId,
              working_copy = target.workingCopy,
              mutations =
                batch.map { m ->
                  (m.payload as MutationPayload.Survey)
                    .mutation
                    .copy(
                      mutation_id = m.mutationId,
                      client_id = clientId,
                      client_seq = m.clientSeq,
                      client_timestamp = m.clientTimestamp,
                    )
                },
            )
          )
        val endsWorkingCopy = batch.any {
          val sm = (it.payload as MutationPayload.Survey).mutation
          sm.save_working_copy == true || sm.discard_working_copy == true
        }
        val handler: (LocalStoreTransaction) -> Unit = { tx ->
          val storedBase = tx.surveyBase(target.surveyId)
          val stored = storedBase ?: SurveyBase()
          val returned = response.survey
          // A poll may already have stored a newer copy than this response (or this is a replay,
          // which returns the current copy): keep the stored base then.
          val responseVersion =
            maxOf(returned?.record_version ?: 0L, response.acl?.record_version ?: 0L)
          val isStale =
            storedBase != null &&
              !Rebaser.isNewer(responseVersion, Rebaser.recordVersionOf(storedBase))
          val updated =
            when {
              isStale -> stored
              returned == null -> stored.copy(acl = response.acl ?: stored.acl)
              target.workingCopy && !endsWorkingCopy ->
                stored.copy(workingCopy = returned, acl = response.acl ?: stored.acl)
              else ->
                stored.copy(
                  saved = returned,
                  workingCopy = if (endsWorkingCopy) null else stored.workingCopy,
                  acl = response.acl ?: stored.acl,
                )
            }
          tx.putSurveyBase(target.surveyId, updated)
          Rebaser(tx).rebaseSurvey(target.surveyId)
        }
        handler
      }
      is MutationTarget.Organization -> {
        val response =
          grpcClient.mutateOrganization(
            MutateOrganizationRequest(
              organization_id = target.organizationId,
              mutations =
                batch.map { m ->
                  (m.payload as MutationPayload.Organization)
                    .mutation
                    .copy(
                      mutation_id = m.mutationId,
                      client_id = clientId,
                      client_seq = m.clientSeq,
                      client_timestamp = m.clientTimestamp,
                    )
                },
            )
          )
        val handler: (LocalStoreTransaction) -> Unit = { tx ->
          response.organization?.let { Rebaser(tx).acceptOrganization(it) }
        }
        handler
      }
      is MutationTarget.Library -> {
        val response =
          grpcClient.mutateLibrary(
            MutateLibraryRequest(
              organization_id = target.organizationId,
              mutations =
                batch.map { m ->
                  (m.payload as MutationPayload.Library)
                    .mutation
                    .copy(
                      mutation_id = m.mutationId,
                      client_id = clientId,
                      client_seq = m.clientSeq,
                      client_timestamp = m.clientTimestamp,
                    )
                },
            )
          )
        val handler: (LocalStoreTransaction) -> Unit = { tx ->
          response.library?.let { Rebaser(tx).acceptLibrary(it) }
        }
        handler
      }
    }

  private fun LocalStoreTransaction.markBatch(
    batch: List<PendingMutation>,
    state: OutboxState,
    error: String?,
    notBefore: Instant?,
  ) {
    val ids = batch.map { it.mutationId }.toSet()
    putOutbox(
      outbox().map {
        if (it.mutationId in ids) {
          it.copy(state = state, lastError = error, notBefore = notBefore)
        } else {
          it
        }
      }
    )
  }

  /** Exponential backoff with jitter from the batch's attempt count, capped at [MAX_BACKOFF_MS]. */
  private fun backoff(batch: List<PendingMutation>): Instant {
    val attempts = batch.maxOf { it.attempts }.coerceIn(1, 10)
    val base = (INITIAL_BACKOFF_MS shl (attempts - 1)).coerceAtMost(MAX_BACKOFF_MS)
    val jitter = (base / 4 * kotlin.random.Random.nextDouble()).toLong()
    return clock.now().plusMillis(base + jitter)
  }

  private suspend fun nextAttemptAt(): Instant? = store.transaction {
    outbox()
      .filter { it.state == OutboxState.QUEUED || it.state == OutboxState.RETRYING }
      .mapNotNull { it.notBefore }
      .minWithOrNull(InstantOrder)
  }

  companion object {
    const val INITIAL_BACKOFF_MS = 2_000L
    const val MAX_BACKOFF_MS = 60_000L

    /** gRPC status codes that reject a mutation for good. */
    val PERMANENT_STATUSES =
      setOf(
        3, // INVALID_ARGUMENT
        5, // NOT_FOUND
        6, // ALREADY_EXISTS
        7, // PERMISSION_DENIED
        12, // UNIMPLEMENTED
        16, // UNAUTHENTICATED
      )

    /** gRPC status codes that mean a base was stale: pull, rebase, and retry. */
    val STALE_BASE_STATUSES =
      setOf(
        9, // FAILED_PRECONDITION
        10, // ABORTED
      )
  }
}

/** Rebases every record [mutations] touch, so the local copy reflects the outbox. */
internal fun LocalStoreTransaction.rebaseRecords(mutations: List<PendingMutation>) {
  val rebaser = Rebaser(this)
  mutations
    .groupBy { it.target }
    .forEach { (target, forTarget) ->
      when (target) {
        is MutationTarget.Data -> {
          val entityIds = forTarget.mapNotNull {
            (it.payload as? MutationPayload.Data)?.mutation?.entity_mutation?.entity_id
          }
          val submissionIds = forTarget.mapNotNull {
            (it.payload as? MutationPayload.Data)?.mutation?.submission_mutation?.submission_id
          }
          rebaser.rebaseData(target.surveyId, entityIds, submissionIds)
        }
        is MutationTarget.Survey -> rebaser.rebaseSurvey(target.surveyId)
        is MutationTarget.Organization -> rebaser.rebaseOrganization(target.organizationId)
        is MutationTarget.Library -> rebaser.rebaseLibrary(target.organizationId)
      }
    }
}

/** Chronological order of [Instant]s. */
internal val InstantOrder: Comparator<Instant> =
  compareBy<Instant>({ it.getEpochSecond() }, { it.getNano() })

/** True when [this] is strictly after [other]. */
internal fun Instant.isAfter(other: Instant): Boolean =
  getEpochSecond() > other.getEpochSecond() ||
    (getEpochSecond() == other.getEpochSecond() && getNano() > other.getNano())
