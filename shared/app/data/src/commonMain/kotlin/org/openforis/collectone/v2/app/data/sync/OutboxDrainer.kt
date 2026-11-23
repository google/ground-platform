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

import co.touchlab.kermit.Logger
import com.squareup.wire.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import openforis.collectone.v1.data.GetAppliedMutationsRequest
import openforis.collectone.v1.data.MutateDataRequest
import openforis.collectone.v1.library.MutateLibraryRequest
import openforis.collectone.v1.survey.MutateOrganizationRequest
import openforis.collectone.v1.survey.MutateSurveyRequest
import org.openforis.collectone.v2.app.client.grpc.CollectOneGrpcClient
import org.openforis.collectone.v2.app.client.grpc.GrpcWebException
import org.openforis.collectone.v2.app.client.grpc.diagnosticMessage
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStoreTransaction
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationPayload
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationTarget
import org.openforis.collectone.v2.app.data.datasource.local.store.OutboxState
import org.openforis.collectone.v2.app.data.datasource.local.store.PendingMutation
import org.openforis.collectone.v2.app.data.datasource.local.store.SurveyBase
import org.openforis.collectone.v2.app.data.error.toDomainError
import org.openforis.collectone.v2.app.data.mapper.toWireInstant
import org.openforis.collectone.v2.app.domain.error.DomainError

private val log = Logger.withTag("OutboxDrainer")

/** Why sending a batch failed: [error] for the user, [detail] for logs and debugging. */
private data class OutboxFailure(val error: DomainError, val detail: String)

/**
 * This error as an outbox entry stores it: entries are persisted, so an unknown error keeps only
 * its raw detail ([PendingMutation.lastErrorDetail]), not its cause.
 */
private fun DomainError.withoutCause(): DomainError =
  if (this is DomainError.Unknown) DomainError.Unknown() else this

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
  private val clock: Clock = Clock.System,
  private val maxBatchSize: Int = 100,
  /** How long a batch waits after its attempt number `n` failed with a transient error. */
  private val backoff: RetryBackoff = RetryBackoff(),
) {
  /**
   * Sends everything sendable now and returns what happened. Mutations left `IN_FLIGHT` by an
   * interrupted drain (app killed mid-send) are made sendable again first; the server's applied-
   * mutation check then keeps them from being applied twice. Stops at the first batch of each scope
   * that can't be sent yet, so a later mutation never overtakes an earlier one. Callers serialize
   * drains (`SyncCoordinator` runs them under its lock).
   *
   * [onSend] runs before each batch is sent, so callers can tell a drain that sends from one that
   * finds nothing ready (every mutation debounced, backing off, or behind one that is).
   */
  suspend fun drain(onSend: () -> Unit = {}): DrainResult {
    resetInterruptedSends()
    confirmApplied()
    var result = DrainResult()
    val blockedScopes = mutableSetOf<String>()
    while (true) {
      val batch = nextBatch(blockedScopes) ?: break
      onSend()
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
      val now = clock.now().toWireInstant()
      val byScope = outbox().filter { it.state != OutboxState.REJECTED }.groupBy { it.target.scope }
      for ((scope, mutations) in byScope) {
        if (scope in blockedScopes) continue
        val ordered = mutations.sortedBy { it.clientSeq }
        // Debounce applies per target: a waiting mutation holds back everything after it.
        val waitUntil = ordered.mapNotNull { it.notBefore }.maxWithOrNull(InstantOrder)
        if (waitUntil != null && waitUntil.isAfter(now)) continue
        val head = ordered.first()
        if (head.state == OutboxState.IN_FLIGHT) continue
        val batch =
          ordered
            .takeWhile { it.target == head.target }
            .take(maxBatchSize)
            .map { it.copy(state = OutboxState.IN_FLIGHT, attempts = it.attempts + 1) }
        val byId = batch.associateBy { it.mutationId }
        putOutbox(outbox().map { byId[it.mutationId] ?: it })
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
      store.transaction { markBatch(batch, OutboxState.QUEUED, failure = null, notBefore = null) }
      throw e
    } catch (e: Exception) {
      val status = (e as? GrpcWebException)?.status
      log.w { "Couldn't send ${batch.size} outbox mutation(s) to ${batch.first().target}: $e" }
      val isPermanentPrecondition =
        status == FAILED_PRECONDITION_STATUS && batch.any { it.attempts >= 2 }
      // A precondition that still fails after a pull is a business rule (e.g. a closed survey).
      val error =
        if (isPermanentPrecondition) DomainError.NotEditable else e.toDomainError().withoutCause()
      val failure = OutboxFailure(error = error, detail = e.diagnosticMessage())
      when {
        status in PERMANENT_STATUSES || isPermanentPrecondition -> {
          val rejectedCount = store.transaction {
            val headTarget = batch.first().target
            val toReject =
              if (headTarget is MutationTarget.Survey && surveyBase(headTarget.surveyId) == null) {
                outbox().filter {
                  it.target.scope == headTarget.scope && it.state != OutboxState.REJECTED
                }
              } else {
                batch
              }
            markBatch(toReject, OutboxState.REJECTED, failure = failure, notBefore = null)
            if (headTarget is MutationTarget.Survey && surveyBase(headTarget.surveyId) == null) {
              deleteSurvey(headTarget.surveyId)
            } else {
              rebaseRecords(toReject)
            }
            toReject.size
          }
          SendOutcome(rejected = rejectedCount)
        }
        status in STALE_BASE_STATUSES -> {
          store.transaction {
            markBatch(batch, OutboxState.RETRYING, failure = failure, notBefore = backoff(batch))
          }
          SendOutcome(needsPull = true, blocked = true)
        }
        else -> {
          store.transaction {
            markBatch(batch, OutboxState.RETRYING, failure = failure, notBefore = backoff(batch))
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
    failure: OutboxFailure?,
    notBefore: Instant?,
  ) {
    val ids = batch.map { it.mutationId }.toSet()
    putOutbox(
      outbox().map {
        if (it.mutationId in ids) {
          it.copy(
            state = state,
            lastError = failure?.error,
            lastErrorDetail = failure?.detail,
            notBefore = notBefore,
          )
        } else {
          it
        }
      }
    )
  }

  /** When [batch] may be sent again: [RetryBackoff] from its attempt count. */
  private fun backoff(batch: List<PendingMutation>): Instant =
    (clock.now() + backoff.delayMillis(batch.maxOf { it.attempts }).milliseconds).toWireInstant()

  private suspend fun nextAttemptAt(): Instant? = store.transaction {
    val now = clock.now().toWireInstant()
    outbox()
      .filter { it.state != OutboxState.REJECTED }
      .groupBy { it.target.scope }
      .values
      .mapNotNull { mutations ->
        mutations
          .mapNotNull { it.notBefore }
          .maxWithOrNull(InstantOrder)
          ?.takeIf { it.isAfter(now) }
      }
      .minWithOrNull(InstantOrder)
  }

  companion object {
    /** gRPC status codes that reject a mutation for good. */
    val PERMANENT_STATUSES =
      setOf(
        3, // INVALID_ARGUMENT
        5, // NOT_FOUND
        6, // ALREADY_EXISTS
        7, // PERMISSION_DENIED
        8, // RESOURCE_EXHAUSTED
        12, // UNIMPLEMENTED
        16, // UNAUTHENTICATED
      )

    private const val FAILED_PRECONDITION_STATUS = 9

    /** gRPC status codes that mean a base was stale: pull, rebase, and retry. */
    val STALE_BASE_STATUSES =
      setOf(
        FAILED_PRECONDITION_STATUS, // FAILED_PRECONDITION
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
