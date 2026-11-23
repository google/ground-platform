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
package org.openforis.collectone.v2.app.di

import co.touchlab.kermit.Logger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.openforis.collectone.v2.app.client.auth.AuthClient
import org.openforis.collectone.v2.app.client.lifecycle.guardUnsentChangesOnUnload
import org.openforis.collectone.v2.app.client.lifecycle.observeAppForeground
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.OutboxState
import org.openforis.collectone.v2.app.data.datasource.local.store.PendingMutation
import org.openforis.collectone.v2.app.data.datasource.remote.GrpcSyncDataSource
import org.openforis.collectone.v2.app.data.error.toDomainError
import org.openforis.collectone.v2.app.data.sync.SurveyTopicSubscriptionManager
import org.openforis.collectone.v2.app.data.sync.SyncActivity
import org.openforis.collectone.v2.app.data.sync.SyncCoordinator
import org.openforis.collectone.v2.app.data.sync.SyncStatePersistence
import org.openforis.collectone.v2.app.domain.model.SaveStatus
import org.openforis.collectone.v2.app.domain.repository.SampleDataRepository
import org.openforis.collectone.v2.app.domain.repository.SurveyRepository

private val log = Logger.withTag("UserDataSync")

/**
 * Brings the local store in line with the user data service and keeps it there:
 * - Runs the initial sync ([state], [hasCompletedInitialSync]) when the client starts and each time
 *   the signed-in user changes, restoring the saved sync state on the device when there is one.
 * - Reopens the survey the user had open once the local data is in place, before
 *   [hasLoadedLocalData] and [hasCompletedInitialSync] turn true, and forgets it when the user
 *   signs out.
 * - Reports when the data stored on the device is in the local store ([hasLoadedLocalData]), before
 *   any download from the user data service, so hosts can choose where to start offline.
 * - Retries a failed initial sync with exponential backoff, or right away on [retry].
 * - Starts the sync engine ([SyncCoordinator]) once the initial sync has run, and connects it to
 *   the platform: foreground polling, push topics, saving sync state, and (on the web) a leave-page
 *   confirmation while changes are unsent.
 * - Relays which views are on screen ([setCollaborativeView], [setSurveyListOpen]) to the sync
 *   engine, which polls more often for them.
 *
 * Without a [syncDataSource] (tests and fixtures), it seeds the sample data once and reports
 * [UserDataSyncState.NotConfigured].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserDataSyncController(
  /** Base URL of the user data service, reported in [UserDataSyncState.Failed]. */
  private val serviceUrl: String?,
  private val syncDataSource: GrpcSyncDataSource?,
  private val authClient: AuthClient,
  private val localStore: LocalStore,
  private val sampleDataRepository: SampleDataRepository,
  private val surveyRepository: SurveyRepository,
  /** The sync engine, when connected to the user data service with it turned on. */
  syncEngine: SyncEngine?,
  private val scope: CoroutineScope,
  /** Waits between initial sync retries; tests replace it to skip the wait. */
  private val retryDelay: suspend (millis: Long) -> Unit = { delay(it) },
) {
  /**
   * The sync engine's parts: its scheduler, the push topic subscriptions of downloaded surveys
   * (mobile), and the store of the sync state on the device (created on first use; null where there
   * is none).
   */
  class SyncEngine(
    val coordinator: SyncCoordinator,
    val topicSubscriptionManager: SurveyTopicSubscriptionManager?,
    val statePersistence: () -> SyncStatePersistence?,
  )

  private val syncCoordinator: SyncCoordinator? = syncEngine?.coordinator
  private val topicSubscriptionManager: SurveyTopicSubscriptionManager? =
    syncEngine?.topicSubscriptionManager

  /** Keeps the sync state on the device between launches; created on first use. */
  private val syncStatePersistence: SyncStatePersistence? by lazy {
    syncEngine?.statePersistence?.invoke()
  }

  /** Disconnects the sync engine from platform lifecycle hooks, once started. */
  private var stopSyncLifecycle: (() -> Unit)? = null

  /** Active initial sync job, cancelled when the user or role changes or on manual retry. */
  private var initialSyncJob: Job? = null

  private val mutableState =
    MutableStateFlow(
      if (syncDataSource == null) UserDataSyncState.NotConfigured else UserDataSyncState.Syncing
    )

  /** Progress of the initial sync from the user data service, for reporting failures. */
  val state: StateFlow<UserDataSyncState> = mutableState.asStateFlow()

  private val mutableHasCompletedInitialSync = MutableStateFlow(syncDataSource == null)

  /**
   * Whether the initial sync has finished (either successfully or with a failure). True ensures UI
   * spinners only appear on the first load when data is empty, not during periodic background
   * refreshes.
   */
  val hasCompletedInitialSync: StateFlow<Boolean> = mutableHasCompletedInitialSync.asStateFlow()

  private val mutableHasLoadedLocalData = MutableStateFlow(false)

  /**
   * Whether the data stored on the device for the signed-in user (the sync state saved by an
   * earlier launch, and the survey they had open) is in the local store. It turns true before the
   * initial sync downloads anything, so hosts can choose where to start without waiting for the
   * network, and stays false while another user's stored data loads. True while signed out.
   */
  val hasLoadedLocalData: StateFlow<Boolean> = mutableHasLoadedLocalData.asStateFlow()

  /**
   * Whether the user's edits are being saved to the user data service, for save indicators:
   * [SaveStatus.SAVING] only while the sync engine sends outbox mutations
   * ([SyncActivity.isSending]), not during polls and pulls that send nothing while edits wait their
   * turn (debounced, offline, or queued behind a send that is backing off), and [SaveStatus.FAILED]
   * while edits wait to retry a failed send. Always [SaveStatus.IDLE] without the sync engine.
   */
  val saveStatus: StateFlow<SaveStatus> =
    if (syncCoordinator == null) {
      MutableStateFlow(SaveStatus.IDLE)
    } else {
      combine(syncCoordinator.activity, localStore.observeOutbox(), ::saveStatusOf)
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, SaveStatus.IDLE)
    }

  /**
   * When the sync engine last finished a sync without an error, or null when it hasn't yet or there
   * is no sync engine.
   */
  val lastSyncedAt: StateFlow<Instant?> =
    syncCoordinator?.activity?.map { it.lastSyncedAt }?.stateIn(scope, SharingStarted.Eagerly, null)
      ?: MutableStateFlow(null)

  /**
   * Runs the initial sync now and, with real accounts, again each time the signed-in user changes
   * (signing out clears the store instead). Signing out also forgets the survey the user had open.
   * Call once.
   */
  fun start() {
    forgetActiveSurveyOnSignOut()
    if (!authClient.usesRealAccounts) {
      initialSyncJob = scope.launch { syncFromUserDataService() }
      return
    }
    if (authClient.currentUser.value == null) {
      mutableHasCompletedInitialSync.value = true
      mutableHasLoadedLocalData.value = true
    }
    authClient.currentUser
      // Another account or simulated role; not a refreshed profile of the same account (for example
      // the auth service confirming the account restored offline), which would discard its data.
      .distinctUntilChangedBy { it?.email to it?.devRole }
      .onEach { user ->
        initialSyncJob?.cancel()
        localStore.clear()
        syncDataSource?.resetSyncTokens()
        if (user != null) {
          mutableHasCompletedInitialSync.value = false
          mutableHasLoadedLocalData.value = false
          initialSyncJob = scope.launch { syncFromUserDataService() }
        } else {
          mutableState.value = UserDataSyncState.Synced
          mutableHasCompletedInitialSync.value = true
          mutableHasLoadedLocalData.value = true
        }
      }
      .launchIn(scope)
  }

  /**
   * Forgets the survey the user had open when the signed-in user signs out (or the auth service
   * ends the session), so the next launch or account doesn't reopen it. The session restored at
   * launch is not a sign-out.
   */
  private fun forgetActiveSurveyOnSignOut() {
    authClient.currentUser
      .map { it != null }
      .distinctUntilChanged()
      .drop(1)
      .filter { isSignedIn -> !isSignedIn }
      .onEach { surveyRepository.forgetActiveSurvey() }
      .launchIn(scope)
  }

  /** Retries the initial sync after a [UserDataSyncState.Failed]. */
  fun retry() {
    initialSyncJob?.cancel()
    initialSyncJob = scope.launch { syncFromUserDataService() }
  }

  /**
   * Whether a collaborative view (survey editor, live data table) is on screen; the sync engine
   * polls more often while one is.
   */
  fun setCollaborativeView(isCollaborative: Boolean) {
    syncCoordinator?.setCollaborativeView(isCollaborative)
  }

  /**
   * Whether the mobile survey list is on screen. When only downloaded surveys are synced, the list
   * of surveys the user can download is only kept current while it is.
   */
  fun setSurveyListOpen(isOpen: Boolean) {
    syncCoordinator?.setSurveyListOpen(isOpen)
  }

  private suspend fun syncFromUserDataService() {
    if (syncDataSource == null) {
      // The sample data is seeded locally, so it is part of the device's data.
      sampleDataRepository.seedIfNeeded()
      restoreActiveSurvey()
      mutableHasLoadedLocalData.value = true
      mutableHasCompletedInitialSync.value = true
      return
    }
    if (isSignedOut()) {
      mutableState.value = UserDataSyncState.Synced
      mutableHasLoadedLocalData.value = true
      mutableHasCompletedInitialSync.value = true
      return
    }
    mutableState.value = UserDataSyncState.Syncing
    // Offline edits and the last synced data saved on the device replace the initial download;
    // the sync engine then catches up from the saved sync tokens.
    val coordinator = syncCoordinator
    val account = syncStateAccount()
    if (coordinator != null && syncStatePersistence?.restore(account) == true) {
      restoreActiveSurvey()
      mutableHasLoadedLocalData.value = true
      syncStatePersistence?.start(account)
      startSyncEngine(coordinator)
      mutableState.value = UserDataSyncState.Synced
      mutableHasCompletedInitialSync.value = true
      return
    }
    // Nothing is stored on the device for this user: the initial download below fills the store.
    mutableHasLoadedLocalData.value = true
    try {
      sampleDataRepository.seedIfNeeded()
      restoreActiveSurvey()
      syncStatePersistence?.start(account)
      syncCoordinator?.let(::startSyncEngine)
      mutableHasCompletedInitialSync.value = true
      mutableState.value = UserDataSyncState.Synced
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      log.w { "Couldn't sync from the user data service at $serviceUrl: $e" }
      mutableHasCompletedInitialSync.value = true
      mutableState.value =
        UserDataSyncState.Failed(serviceUrl = serviceUrl, error = e.toDomainError())
      syncStatePersistence?.start(account)
      syncCoordinator?.let(::startSyncEngine)
      retrySeedWithBackoff()
    }
  }

  /**
   * Reopens the survey the user had open ([SurveyRepository.observeOpenSurveyId]), now that the
   * local data it needs is in place; before [hasLoadedLocalData] turns true when that data was
   * stored on the device, so hosts can choose where to start.
   */
  private suspend fun restoreActiveSurvey() {
    surveyRepository.restoreActiveSurvey()
  }

  /**
   * Whose sync state the device keeps: the signed-in real account's email, or blank for sample and
   * development accounts, which share theirs.
   */
  private fun syncStateAccount(): String =
    if (authClient.usesRealAccounts) authClient.currentUser.value?.email.orEmpty() else ""

  private suspend fun retrySeedWithBackoff() {
    var delayMillis = INITIAL_SEED_RETRY_DELAY_MS
    repeat(MAX_SEED_RETRY_ATTEMPTS) {
      retryDelay(delayMillis)
      if (isSignedOut()) return
      try {
        sampleDataRepository.seedIfNeeded()
        mutableState.value = UserDataSyncState.Synced
        return
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        delayMillis = (delayMillis * 2).coerceAtMost(MAX_SEED_RETRY_DELAY_MS)
      }
    }
  }

  private fun isSignedOut(): Boolean =
    authClient.usesRealAccounts && authClient.currentUser.value == null

  /**
   * Starts [coordinator] and connects it to the platform: polling while the app is in the
   * foreground, and (on the web) a leave-page confirmation while the outbox has unsent changes.
   */
  private fun startSyncEngine(coordinator: SyncCoordinator) {
    coordinator.start()
    topicSubscriptionManager?.start()
    if (stopSyncLifecycle != null) return
    // Signing in or switching the simulated development role changes what the server returns.
    authClient.currentUser
      .drop(1)
      .distinctUntilChanged()
      .onEach { if (it != null) coordinator.requestSync() }
      .launchIn(scope)
    // Downloading a survey (or removing a download) fetches (or drops) its definition and data.
    localStore
      .observeSurveys()
      .map { surveys -> surveys.filter { it.isDownloaded }.map { it.id }.toSet() }
      .distinctUntilChanged()
      .drop(1)
      .onEach { coordinator.requestSync() }
      .launchIn(scope)
    // Polls in collaborative views catch up only the open survey's data.
    surveyRepository
      .observeActiveSurveyId()
      .distinctUntilChanged()
      .onEach(coordinator::setFocusedSurvey)
      .launchIn(scope)
    val hasUnsentChanges =
      localStore
        .observeOutbox()
        .map { outbox -> outbox.any { it.state != OutboxState.REJECTED } }
        .stateIn(scope, SharingStarted.Eagerly, false)
    val stopForeground = observeAppForeground(coordinator::setForeground)
    val stopGuard = guardUnsentChangesOnUnload { hasUnsentChanges.value }
    stopSyncLifecycle = {
      topicSubscriptionManager?.stop()
      stopForeground()
      stopGuard()
    }
  }

  companion object {
    internal const val INITIAL_SEED_RETRY_DELAY_MS = 2_000L
    internal const val MAX_SEED_RETRY_DELAY_MS = 16_000L
    internal const val MAX_SEED_RETRY_ATTEMPTS = 4
  }
}

/**
 * The [SaveStatus] of the sync engine's [activity] over [outbox]: saving while it sends, else
 * failed while a mutation waits to retry a failed send. Mutations that are queued (debounced,
 * offline, or behind a send that is backing off) or rejected for good are neither: rejections are
 * reported separately ([BackendErrorMonitor]).
 */
internal fun saveStatusOf(activity: SyncActivity, outbox: List<PendingMutation>): SaveStatus =
  when {
    activity.isSending -> SaveStatus.SAVING
    outbox.any { it.state == OutboxState.RETRYING && it.lastError != null } -> SaveStatus.FAILED
    else -> SaveStatus.IDLE
  }
