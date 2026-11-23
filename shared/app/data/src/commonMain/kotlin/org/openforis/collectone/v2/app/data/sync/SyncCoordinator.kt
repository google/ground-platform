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

import co.touchlab.kermit.Logger
import com.squareup.wire.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStoreTransaction
import org.openforis.collectone.v2.app.data.datasource.local.store.OutboxState
import org.openforis.collectone.v2.app.data.datasource.remote.GrpcSyncDataSource
import org.openforis.collectone.v2.app.data.error.toDomainError
import org.openforis.collectone.v2.app.data.mapper.toKotlinInstant
import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.domain.repository.ConnectivityRepository

private val log = Logger.withTag("SyncCoordinator")

/** What the [SyncCoordinator] is doing, for status indicators. */
data class SyncActivity(
  val isSyncing: Boolean = false,
  /**
   * Whether this sync is sending outbox mutations: from its first send until its drain ends, or,
   * when a send found a stale base, until the pull and the drain that resends it end. False for
   * syncs (polls, catch-ups) that send nothing, even while mutations wait in the outbox.
   */
  val isSending: Boolean = false,
  /**
   * Why the last sync attempt failed, cleared by the next successful one. The raw detail is only
   * logged.
   */
  val lastError: DomainError? = null,
  /** When the last sync finished without an error, or null if none has. */
  val lastSyncedAt: kotlin.time.Instant? = null,
)

/**
 * Schedules the sync engine (`docs/technical/sync-engine.md`): one single-flight loop that drains
 * the outbox ([OutboxDrainer]) and then pulls remote changes
 * ([GrpcSyncDataSource.pullIncrementalCatchUp] or targeted per-survey scopes), so the client's own
 * writes come back with their authoritative server versions.
 *
 * Syncs run when the outbox gets sendable mutations (after their debounce or backoff), when the
 * device comes back online or the app returns to the foreground (both skip any backoff:
 * [retryNow]), after a failed sync (with capped exponential backoff, [RetryBackoff]), on
 * [requestSync] / [requestScopedSync], and, while [setForeground] is on, on a polling timer
 * ([pollIntervalMillis]: about 10 seconds in collaborative views or while the survey list is open,
 * and 60 seconds otherwise unless [setPushSubscriptionsActive] is enabled on mobile). Nothing runs
 * while offline or while [canSync] is false (signed out).
 *
 * To keep the number of calls proportional to what's on screen:
 * - Polls in a collaborative view with a [setFocusedSurvey] catch up only that survey's data (plus
 *   the cheap account-wide scopes), not the entities and submissions of every survey.
 * - Coming online, returning to the foreground, and idle polls skip the pull when a full pull (or
 *   the initial seed) finished less than [MIN_FULL_PULL_INTERVAL_MS] ago and nothing was sent.
 */
class SyncCoordinator(
  private val store: LocalStore,
  private val syncDataSource: GrpcSyncDataSource,
  private val drainer: OutboxDrainer,
  private val connectivity: ConnectivityRepository,
  private val scope: CoroutineScope,
  private val clock: Clock = Clock.System,
  /** How long to wait before retrying after syncs failed `n` times in a row while online. */
  private val retryBackoff: RetryBackoff = RetryBackoff(),
  /**
   * Whether there is an account to sync for. While false (signed out of a real account), requested
   * syncs wait instead of calling the server without credentials.
   */
  private val canSync: () -> Boolean = { true },
) {
  private data class PendingSyncState(
    /** A full pull that must run (an explicit request, a sign-in, or a download change). */
    val fullSync: Boolean = false,
    /** A full pull that can be skipped if one finished recently (online, foreground, idle poll). */
    val catchUpSync: Boolean = false,
    /** Surveys whose data a collaborative-view poll should catch up. */
    val focusedSyncs: Set<String> = emptySet(),
    val defSyncs: Map<String, Long?> = emptyMap(),
    val entitySyncs: Set<String> = emptySet(),
  ) {
    fun withFullSync(): PendingSyncState = copy(fullSync = true)

    fun withCatchUpSync(): PendingSyncState = copy(catchUpSync = true)

    fun withFocusedSync(surveyId: String): PendingSyncState =
      copy(focusedSyncs = focusedSyncs + surveyId)

    fun withScopedSync(
      surveyId: String,
      scope: SurveySyncScope,
      minRecordVersion: Long?,
    ): PendingSyncState =
      when (scope) {
        SurveySyncScope.DEFINITION -> {
          val existing = defSyncs[surveyId]
          val merged =
            if (existing != null && minRecordVersion != null) maxOf(existing, minRecordVersion)
            else minRecordVersion ?: existing
          copy(defSyncs = defSyncs + (surveyId to merged))
        }
        SurveySyncScope.ENTITIES -> copy(entitySyncs = entitySyncs + surveyId)
      }
  }

  private val mutex = Mutex()
  private val pendingSync = MutableStateFlow(PendingSyncState())
  private val triggers = Channel<Unit>(Channel.CONFLATED)
  private val foreground = MutableStateFlow(false)
  private val collaborative = MutableStateFlow(false)
  private val surveyListOpen = MutableStateFlow(false)
  private val pushSubscriptionsActive = MutableStateFlow(false)
  private val focusedSurveyId = MutableStateFlow<String?>(null)
  private val mutableActivity = MutableStateFlow(SyncActivity())
  private var jobs: List<Job> = emptyList()

  /** Syncs that failed in a row since the last one that succeeded. */
  private var consecutiveFailures = 0

  /** Retries the last failed sync once its backoff expires. */
  private var retryJob: Job? = null

  /** Wakes the loop when the earliest debounced or backing-off mutation becomes sendable. */
  private var nextAttemptJob: Job? = null

  /** Current sync activity. */
  val activity: StateFlow<SyncActivity> = mutableActivity.asStateFlow()

  /**
   * Poll interval for the current view: shorter while a collaborative view or the survey list is
   * visible.
   */
  fun pollIntervalMillis(): Long =
    if (collaborative.value || surveyListOpen.value) COLLABORATIVE_POLL_MS else IDLE_POLL_MS

  /**
   * Whether foreground timer polling should run right now. When [pushSubscriptionsActive] is true
   * (mobile with FCM topic subscriptions enabled), the 60-second idle foreground poll is suppressed
   * because `survey_<id>_def` and `survey_<id>_entities` topic tickles wake the sync engine
   * directly; collaborative views and the open Download survey catalog continue polling every 10s.
   */
  fun shouldPollInForeground(): Boolean =
    foreground.value &&
      (!pushSubscriptionsActive.value || collaborative.value || surveyListOpen.value)

  /** Starts the sync loop, outbox watcher, connectivity watcher, and foreground polling. */
  fun start() {
    if (jobs.isNotEmpty()) return
    jobs =
      listOf(
        scope.launch { for (trigger in triggers) runSync() },
        scope.launch { watchOutbox() },
        scope.launch {
          connectivity.observeIsOnline().collectLatest { online -> if (online) retryNow() }
        },
        scope.launch { pollWhileForeground() },
      )
  }

  /** Stops all sync activity. */
  fun stop() {
    jobs.forEach { it.cancel() }
    jobs = emptyList()
    retryJob?.cancel()
    nextAttemptJob?.cancel()
  }

  /** Asks for a full incremental sync soon. Requests made while one is running coalesce. */
  fun requestSync() {
    pendingSync.update { it.withFullSync() }
    triggers.trySend(Unit)
  }

  /**
   * Asks for a full incremental sync that is skipped when a full pull finished less than
   * [MIN_FULL_PULL_INTERVAL_MS] ago and the outbox had nothing to send.
   */
  private fun requestCatchUpSync() {
    pendingSync.update { it.withCatchUpSync() }
    triggers.trySend(Unit)
  }

  /** Asks for a poll: only the focused survey's data in a collaborative view, else a catch-up. */
  private fun requestPollSync() {
    val focused = focusedSurveyId.value
    if (collaborative.value && focused != null) {
      pendingSync.update { it.withFocusedSync(focused) }
      triggers.trySend(Unit)
    } else {
      requestCatchUpSync()
    }
  }

  /**
   * Asks for a targeted per-survey sync triggered by an FCM topic tickle (`survey_<id>_def` or
   * `survey_<id>_entities`). Coalesces with other pending requests when a sync is already in
   * flight.
   */
  fun requestScopedSync(
    surveyId: String,
    scope: SurveySyncScope,
    minRecordVersion: Long? = null,
  ) {
    if (surveyId.isBlank()) return
    pendingSync.update { it.withScopedSync(surveyId, scope, minRecordVersion) }
    triggers.trySend(Unit)
  }

  /**
   * Whether FCM push topic subscriptions (`survey_<id>_def`, `survey_<id>_entities`) are active.
   * When true, idle 60-second foreground polling is suppressed outside collaborative views and the
   * survey catalog.
   */
  fun setPushSubscriptionsActive(active: Boolean) {
    pushSubscriptionsActive.value = active
  }

  /** Whether the app is in the foreground; polling only runs in the foreground. */
  fun setForeground(isForeground: Boolean) {
    val wasForeground = foreground.value
    foreground.value = isForeground
    if (isForeground && !wasForeground) retryNow()
  }

  /**
   * Syncs now without waiting out any backoff: mutations whose sends failed become sendable again
   * and earlier sync failures are forgotten. Runs when the device comes back online and when the
   * app returns to the foreground (on the web, when the tab becomes visible), where the server is
   * likely reachable again, and when the user asks to retry. With [fullSync] it always pulls;
   * otherwise the pull is skipped when one finished moments ago and nothing was sent.
   */
  fun retryNow(fullSync: Boolean = false) {
    resetRetryBackoff()
    scope.launch {
      store.transaction { clearSendBackoff() }
      if (fullSync) requestSync() else requestCatchUpSync()
    }
  }

  /**
   * The survey on screen (for example the web dashboard's or survey editor's), or null. Polls in a
   * collaborative view catch up only its data; focusing another survey there catches it up now.
   */
  fun setFocusedSurvey(surveyId: String?) {
    val focused = surveyId?.takeIf { it.isNotBlank() }
    val previous = focusedSurveyId.value
    focusedSurveyId.value = focused
    if (focused != null && focused != previous && collaborative.value) {
      pendingSync.update { it.withFocusedSync(focused) }
      triggers.trySend(Unit)
    }
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

  /** Drains the outbox and pulls all remote changes now, unless offline. Single-flight. */
  suspend fun syncNow() {
    pendingSync.update { it.withFullSync() }
    executePendingSync()
  }

  /**
   * Runs a targeted per-survey sync immediately (used by background push workers as well as tests),
   * draining the outbox first and pulling only [scope] for [surveyId] unless a full sync was also
   * queued.
   */
  suspend fun syncScopedNow(
    surveyId: String,
    scope: SurveySyncScope,
    minRecordVersion: Long? = null,
  ) {
    if (surveyId.isBlank()) return
    pendingSync.update { it.withScopedSync(surveyId, scope, minRecordVersion) }
    executePendingSync()
  }

  private suspend fun runSync() {
    executePendingSync()
  }

  private suspend fun executePendingSync() {
    if (!connectivity.isOnline() || !canSync()) return
    mutex.withLock {
      val drainedPending = pendingSync.getAndUpdate { PendingSyncState() }
      if (drainedPending == PendingSyncState()) return
      val defTargets = drainedPending.defSyncs
      val entityTargets = drainedPending.entitySyncs
      val focusedTargets = drainedPending.focusedSyncs
      mutableActivity.value = mutableActivity.value.copy(isSyncing = true)
      // Only sends count as saving: a poll or catch-up that finds every mutation waiting
      // (debounced,
      // backing off, or behind one that is) sends nothing, however long its pull takes.
      val markSending = { mutableActivity.update { it.copy(isSending = true) } }
      try {
        var drained = drainer.drain(onSend = markSending)
        // A stale base is resent after the pull below, so the save isn't over until then.
        if (!drained.needsPull) mutableActivity.update { it.copy(isSending = false) }
        uploadImpactEvents()
        val runFull =
          drainedPending.fullSync ||
            drained.needsPull ||
            drained.sent > 0 ||
            (drainedPending.catchUpSync && !pulledRecently())
        if (runFull) {
          syncDataSource.pullIncrementalCatchUp(store)
        } else {
          for ((sid, minVersion) in defTargets) {
            syncDataSource.pullSurveyDefinitionCatchUp(store, sid, minVersion)
          }
          for (sid in entityTargets) {
            syncDataSource.pullSurveyEntitiesCatchUp(store, sid)
          }
          for (sid in focusedTargets) {
            syncDataSource.pullIncrementalCatchUp(store, surveyId = sid)
          }
        }
        if (drained.needsPull) {
          store.transaction { clearSendBackoff() }
          drained = drainer.drain(onSend = markSending)
        }
        scheduleNextAttempt(drained.nextAttemptAt)
        resetRetryBackoff()
        mutableActivity.value =
          SyncActivity(
            isSyncing = false,
            isSending = false,
            lastError = null,
            lastSyncedAt = clock.now(),
          )
      } catch (e: CancellationException) {
        // A sync cancelled mid-send (the engine stopping) must not leave the status indicators on.
        mutableActivity.update { it.copy(isSyncing = false, isSending = false) }
        throw e
      } catch (e: Exception) {
        log.w { "Sync failed: $e" }
        mutableActivity.value =
          mutableActivity.value.copy(
            isSyncing = false,
            isSending = false,
            lastError = e.toDomainError(),
          )
        scheduleRetry()
      }
    }
  }

  /** Wakes the loop at [at], replacing the previous wake-up; none when [at] is null. */
  private fun scheduleNextAttempt(at: Instant?) {
    nextAttemptJob?.cancel()
    nextAttemptJob = at?.let {
      scope.launch {
        delay(it.toKotlinInstant() - clock.now())
        requestSync()
      }
    }
  }

  /**
   * Retries after a failed sync with [retryBackoff], so syncs keep failing at a falling rate while
   * the server is unreachable and resume within a minute once it's back. Retries do nothing while
   * offline; coming back online syncs right away instead.
   */
  private fun scheduleRetry() {
    consecutiveFailures++
    val waitMillis = retryBackoff.delayMillis(consecutiveFailures)
    retryJob?.cancel()
    retryJob = scope.launch {
      delay(waitMillis)
      requestSync()
    }
  }

  /** Forgets earlier failures, so the next failure retries after the shortest wait again. */
  private fun resetRetryBackoff() {
    consecutiveFailures = 0
    retryJob?.cancel()
    retryJob = null
  }

  /**
   * Requests a sync whenever the outbox has sendable mutations, waiting for the earliest scope's
   * debounce or backoff time first.
   */
  private suspend fun watchOutbox() {
    combine(store.observeOutbox(), connectivity.observeIsOnline()) { outbox, online ->
        outbox to online
      }
      .collectLatest { (outbox, online) ->
        if (!online) return@collectLatest
        val sendableScopes =
          outbox
            .filter { it.state != OutboxState.REJECTED }
            .groupBy { it.target.scope }
            .values
            .filter { mutations ->
              mutations.none { it.state == OutboxState.IN_FLIGHT } &&
                mutations.any {
                  it.state == OutboxState.QUEUED || it.state == OutboxState.RETRYING
                }
            }
        if (sendableScopes.isEmpty()) return@collectLatest
        val scopeWaitTimes = sendableScopes.map { mutations ->
          mutations.mapNotNull { it.notBefore }.maxWithOrNull(InstantOrder)
        }
        val earliest =
          if (scopeWaitTimes.any { it == null }) null
          else scopeWaitTimes.filterNotNull().minWithOrNull(InstantOrder)
        val waitMillis =
          earliest?.let { (it.toKotlinInstant() - clock.now()).inWholeMilliseconds }
            ?: ENQUEUE_DELAY_MS
        delay(waitMillis.coerceAtLeast(ENQUEUE_DELAY_MS))
        requestSync()
      }
  }

  private suspend fun pollWhileForeground() {
    combine(
        foreground,
        collaborative,
        surveyListOpen,
        pushSubscriptionsActive,
      ) { isFg, isCollab, isListOpen, isPush ->
        isFg && (!isPush || isCollab || isListOpen)
      }
      .collectLatest { shouldPoll ->
        while (shouldPoll) {
          delay(pollIntervalMillis())
          requestPollSync()
        }
      }
  }

  /**
   * Whether a full pull (or the initial seed) finished less than [MIN_FULL_PULL_INTERVAL_MS] ago.
   */
  private fun pulledRecently(): Boolean =
    syncDataSource.timeSinceLastFullPull()?.let { it < MIN_FULL_PULL_INTERVAL_MS.milliseconds } ==
      true

  /** Makes mutations waiting out a failed send's backoff sendable now (debounces still apply). */
  private fun LocalStoreTransaction.clearSendBackoff() {
    val outbox = outbox()
    if (outbox.any { it.state == OutboxState.RETRYING && it.notBefore != null }) {
      putOutbox(
        outbox.map { if (it.state == OutboxState.RETRYING) it.copy(notBefore = null) else it }
      )
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

    /**
     * How recent a full pull must be for coming online, returning to the foreground, or an idle
     * poll to skip pulling again (for example right after the initial seed).
     */
    const val MIN_FULL_PULL_INTERVAL_MS = 5_000L

    /** Short delay after an edit, so a burst of edits is sent together. */
    const val ENQUEUE_DELAY_MS = 300L
  }
}
