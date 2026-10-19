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

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.OutboxState
import org.openforis.collectone.v2.app.data.datasource.remote.GrpcSyncDataSource
import org.openforis.collectone.v2.app.domain.repository.ConnectivityRepository

/** What the [SyncCoordinator] is doing, for status indicators. */
data class SyncActivity(
  val isSyncing: Boolean = false,
  /** Message of the last failed sync attempt, cleared by the next successful one. */
  val lastError: String? = null,
)

/**
 * Schedules the sync engine (`docs/technical/sync-engine.md`): one single-flight loop that drains
 * the outbox ([OutboxDrainer]) and then pulls remote changes
 * ([GrpcSyncDataSource.pullIncrementalCatchUp]), so the client's own writes come back with their
 * authoritative server versions.
 *
 * Syncs run when the outbox gets sendable mutations (after their debounce or backoff), when the
 * device comes back online, on [requestSync], and, while [setForeground] is on, on a polling timer
 * ([pollIntervalMillis]: about 10 seconds in collaborative views and 60 seconds otherwise). Nothing
 * runs while offline.
 */
class SyncCoordinator(
  private val store: LocalStore,
  private val syncDataSource: GrpcSyncDataSource,
  private val drainer: OutboxDrainer,
  private val connectivity: ConnectivityRepository,
  private val scope: CoroutineScope,
  private val clock: SyncClock = SyncClock.System,
) {
  private val mutex = Mutex()
  private val triggers = Channel<Unit>(Channel.CONFLATED)
  private val foreground = MutableStateFlow(false)
  private val collaborative = MutableStateFlow(false)
  private val surveyListOpen = MutableStateFlow(false)
  private val mutableActivity = MutableStateFlow(SyncActivity())
  private var jobs: List<Job> = emptyList()

  /** Current sync activity. */
  val activity: StateFlow<SyncActivity> = mutableActivity.asStateFlow()

  /**
   * Poll interval for the current view: shorter while a collaborative view or the survey list is
   * visible.
   */
  fun pollIntervalMillis(): Long =
    if (collaborative.value || surveyListOpen.value) COLLABORATIVE_POLL_MS else IDLE_POLL_MS

  /** Starts the sync loop, outbox watcher, connectivity watcher, and foreground polling. */
  fun start() {
    if (jobs.isNotEmpty()) return
    jobs =
      listOf(
        scope.launch { for (trigger in triggers) runSync() },
        scope.launch { watchOutbox() },
        scope.launch {
          connectivity.observeIsOnline().collectLatest { online -> if (online) requestSync() }
        },
        scope.launch { pollWhileForeground() },
      )
  }

  /** Stops all sync activity. */
  fun stop() {
    jobs.forEach { it.cancel() }
    jobs = emptyList()
  }

  /** Asks for a sync soon. Requests made while one is running coalesce into one more run. */
  fun requestSync() {
    triggers.trySend(Unit)
  }

  /** Whether the app is in the foreground; polling only runs in the foreground. */
  fun setForeground(isForeground: Boolean) {
    val wasForeground = foreground.value
    foreground.value = isForeground
    if (isForeground && !wasForeground) requestSync()
  }

  /** Whether a collaborative view (survey editor, live data table) is visible. */
  fun setCollaborativeView(isCollaborative: Boolean) {
    collaborative.value = isCollaborative
  }

  /**
   * Whether the survey list is visible. With a data source that syncs only downloaded surveys, the
   * list is caught up only while it's open, starting with a sync as soon as it opens.
   */
  fun setSurveyListOpen(isOpen: Boolean) {
    val wasOpen = surveyListOpen.value
    surveyListOpen.value = isOpen
    syncDataSource.setSurveyListOpen(isOpen)
    if (isOpen && !wasOpen) requestSync()
  }

  /** Drains the outbox and pulls remote changes now, unless offline. Single-flight. */
  suspend fun syncNow() {
    if (!connectivity.isOnline()) return
    mutex.withLock {
      mutableActivity.value = mutableActivity.value.copy(isSyncing = true)
      try {
        var drained = drainer.drain()
        uploadImpactEvents()
        syncDataSource.pullIncrementalCatchUp(store)
        if (drained.needsPull) drained = drainer.drain()
        drained.nextAttemptAt?.let { at ->
          scope.launch {
            delay((at.toEpochMillis() - clock.now().toEpochMillis()).coerceAtLeast(0L))
            requestSync()
          }
        }
        mutableActivity.value = SyncActivity(isSyncing = false, lastError = null)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        mutableActivity.value = SyncActivity(isSyncing = false, lastError = e.message)
      }
    }
  }

  private suspend fun runSync() {
    syncNow()
  }

  /**
   * Requests a sync whenever the outbox has sendable mutations, waiting for the earliest debounce
   * or backoff time first.
   */
  private suspend fun watchOutbox() {
    combine(store.observeOutbox(), connectivity.observeIsOnline()) { outbox, online ->
        outbox to online
      }
      .collectLatest { (outbox, online) ->
        if (!online) return@collectLatest
        val sendable = outbox.filter {
          it.state == OutboxState.QUEUED || it.state == OutboxState.RETRYING
        }
        if (sendable.isEmpty()) return@collectLatest
        val earliest = sendable.mapNotNull { it.notBefore }.minWithOrNull(InstantOrder)
        val waitMillis =
          earliest?.let { it.toEpochMillis() - clock.now().toEpochMillis() } ?: ENQUEUE_DELAY_MS
        delay(waitMillis.coerceAtLeast(ENQUEUE_DELAY_MS))
        requestSync()
      }
  }

  private suspend fun pollWhileForeground() {
    foreground.collectLatest { isForeground ->
      while (isForeground) {
        delay(pollIntervalMillis())
        requestSync()
      }
    }
  }

  /** Sends impact events recorded on this device, then marks them uploaded. */
  private suspend fun uploadImpactEvents() {
    val pending = store.transaction { impactEvents().filter { !it.isUploaded } }
    if (pending.isEmpty()) return
    syncDataSource.pushImpactEvents(pending)
    val ids = pending.map { it.id }.toSet()
    store.transaction {
      putImpactEvents(impactEvents().map { if (it.id in ids) it.copy(isUploaded = true) else it })
    }
  }

  companion object {
    const val COLLABORATIVE_POLL_MS = 10_000L
    const val IDLE_POLL_MS = 60_000L

    /** Short delay after an edit, so a burst of edits is sent together. */
    const val ENQUEUE_DELAY_MS = 300L
  }
}
