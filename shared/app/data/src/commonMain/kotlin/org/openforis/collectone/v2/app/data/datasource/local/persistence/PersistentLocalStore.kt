/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.app.data.datasource.local.persistence

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import openforis.collectone.v1.userdata.ClientSyncState
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStoreTransaction
import org.openforis.collectone.v2.app.data.datasource.local.store.PendingMutation

/**
 * A [LocalStore] that keeps the sync engine's state (outbox, server bases, client identity, sync
 * tokens) in a [SyncStateStore] on the device, so offline edits and the last synced data survive
 * restarts (`docs/technical/client/local-data-store.md`).
 *
 * Reads and writes go to [memory], the working copy the UI observes. Once [startWritingThrough] is
 * called, every transaction that changes the sync state also writes those changes to
 * [syncStateStore] before it commits: if that write fails, the transaction fails and [memory] is
 * left unchanged, so an outbox append and the base update it depends on are stored together or not
 * at all. The domain collections the UI shows are not stored; on launch they are rebuilt from the
 * bases and the outbox (`SyncStatePersistence.restore`).
 */
class PersistentLocalStore(
  private val memory: LocalStore,
  private val syncStateStore: SyncStateStore,
) : LocalStore by memory {
  private var isWritingThrough = false

  /** The outbox as stored in [syncStateStore], keyed by mutation ID, while writing through. */
  private var persistedOutbox: Map<String, PendingMutation> = emptyMap()

  /** The sync state stored on the device; empty when nothing was stored. */
  suspend fun loadSyncState(): ClientSyncState = syncStateStore.load()

  /** The account the stored sync state belongs to; see [startWritingThrough]. */
  suspend fun storedAccount(): String = syncStateStore.account()

  /**
   * Replaces the sync state stored on the device with [state] of a sample or development account,
   * e.g. when migrating older storage.
   */
  suspend fun replaceSyncState(state: ClientSyncState) = syncStateStore.replace(state)

  /**
   * Stores the current sync state as [account]'s (the signed-in account's email, or blank for
   * sample and development accounts), replacing what was stored, and from then on stores every
   * change in the transaction that makes it. Until this is called, changes (including [clear]) stay
   * in memory, so the app can start from a clean working copy and restore the stored state into it.
   */
  suspend fun startWritingThrough(account: String = "") {
    trackedTransaction { tx ->
      syncStateStore.replace(ClientSyncStateCodec.capture(tx), account)
      persistedOutbox = tx.outbox().associateBy { it.mutationId }
      isWritingThrough = true
    }
  }

  override suspend fun <R> transaction(block: suspend LocalStoreTransaction.() -> R): R =
    trackedTransaction { tx ->
      tx.block()
    }

  override suspend fun clear() {
    trackedTransaction { tx ->
      tx.onCleared()
      memory.clear()
    }
  }

  private suspend fun <R> trackedTransaction(block: suspend (SyncStateChangeTracker) -> R): R {
    // Nested call inside an open transaction on this store: join it.
    val open = currentCoroutineContext()[OpenTransaction]
    if (open != null && open.store === this) return block(open.tracker)
    return memory.transaction {
      val tracker = SyncStateChangeTracker(this)
      val result =
        withContext(OpenTransaction(this@PersistentLocalStore, tracker)) { block(tracker) }
      if (isWritingThrough) writeThrough(tracker)
      result
    }
  }

  private suspend fun writeThrough(tracker: SyncStateChangeTracker) {
    val change = tracker.changes(persistedOutbox)
    if (change.isEmpty) return
    syncStateStore.apply(change)
    if (tracker.outboxChanged) persistedOutbox = tracker.outbox().associateBy { it.mutationId }
  }

  /** Marks the coroutine as running inside a transaction on [store], so nested ones join it. */
  private class OpenTransaction(
    val store: PersistentLocalStore,
    val tracker: SyncStateChangeTracker,
  ) : AbstractCoroutineContextElement(OpenTransaction) {
    companion object Key : CoroutineContext.Key<OpenTransaction>
  }
}
