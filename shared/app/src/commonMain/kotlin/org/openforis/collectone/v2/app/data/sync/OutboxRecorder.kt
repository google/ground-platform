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
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStoreTransaction
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationPayload
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationTarget
import org.openforis.collectone.v2.app.data.datasource.local.store.PendingMutation

/** Source of the current time for sync bookkeeping, replaceable in tests. */
fun interface SyncClock {
  fun now(): Instant

  companion object {
    /** The system clock. */
    @OptIn(kotlin.time.ExperimentalTime::class)
    val System = SyncClock {
      val now = Clock.System.now()
      ofEpochSecond(now.epochSeconds, now.nanosecondsOfSecond.toLong())
    }
  }
}

/**
 * Records local edits as [PendingMutation]s in the outbox: assigns the idempotency key, the
 * install's `client_id` (created on first use) and the next `client_seq`, and coalesces with queued
 * mutations ([OutboxCoalescer]). Call it inside the same [LocalStoreTransaction] that applies the
 * edit to the local copy.
 */
class OutboxRecorder(
  private val clock: SyncClock = SyncClock.System,
  private val newId: () -> String = ::randomId,
) {
  /**
   * Records [payload] for [recordKey] at [target]. Returns the recorded mutation, or `null` when it
   * cancelled out a queued mutation (deleting a record that was never sent).
   *
   * [debounceMillis] delays sending (for example while the organizer keeps typing in the survey
   * editor): the mutation, and queued ones for the same target, aren't sent before then.
   */
  fun record(
    tx: LocalStoreTransaction,
    target: MutationTarget,
    recordKey: String,
    payload: MutationPayload,
    baseRevision: Long = 0L,
    debounceMillis: Long = 0L,
  ): PendingMutation? {
    val state = tx.updateSyncState { s ->
      if (s.clientId.isBlank()) s.copy(clientId = newId()) else s
    }
    val now = clock.now()
    val mutation =
      PendingMutation(
        mutationId = newId(),
        clientSeq = state.nextClientSeq,
        target = target,
        recordKey = recordKey,
        payload = payload,
        baseRevision = baseRevision,
        clientTimestamp = now,
        notBefore = if (debounceMillis > 0L) now.plusMillis(debounceMillis) else null,
      )
    tx.putSyncState(state.copy(nextClientSeq = state.nextClientSeq + 1))
    val updated = OutboxCoalescer.coalesce(tx.outbox(), mutation)
    tx.putOutbox(updated)
    return mutation.takeIf { m -> updated.any { it.mutationId == m.mutationId } }
  }

  companion object {
    @OptIn(ExperimentalUuidApi::class) fun randomId(): String = Uuid.random().toString()
  }
}

/** [this] plus [millis] milliseconds. */
internal fun Instant.plusMillis(millis: Long): Instant {
  val totalNanos = getNano().toLong() + (millis % 1000L) * 1_000_000L
  return ofEpochSecond(
    getEpochSecond() + millis / 1000L + totalNanos / 1_000_000_000L,
    totalNanos % 1_000_000_000L,
  )
}

/** Milliseconds since the epoch. */
internal fun Instant.toEpochMillis(): Long = getEpochSecond() * 1000L + getNano() / 1_000_000L
