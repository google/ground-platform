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
package org.openforis.collectone.v2.mobile

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin
import org.openforis.collectone.v2.app.client.push.IosPushSubscriptionRegistry
import org.openforis.collectone.v2.app.client.push.IosPushTopicBridge
import org.openforis.collectone.v2.app.client.storage.NativeSqlDriverFactory
import org.openforis.collectone.v2.app.data.sync.parseSyncTickle
import org.openforis.collectone.v2.app.data.sync.syncTickleNow
import org.openforis.collectone.v2.app.di.AppDataHolder
import org.openforis.collectone.v2.app.di.platformModule
import org.openforis.collectone.v2.app.di.platformUserDataServiceUrl
import org.openforis.collectone.v2.app.di.requireUserDataServiceUrl
import org.openforis.collectone.v2.mobile.di.mobileModules

/**
 * Process-wide iOS sync and push notification bridge for `iosApp`, analogous to `MobileSyncRuntime`
 * on Android and `AppMapIos` in `shared/map`.
 *
 * The Swift host (`iOSApp.swift`) can:
 * - Register a Firebase Cloud Messaging topic bridge via [topicBridge].
 * - Forward silent APNs/FCM data notifications (`content-available: 1`) to
 *   [handleRemoteNotification] so `SyncCoordinator` executes a scoped delta pull before invoking
 *   UIKit's `fetchCompletionHandler`.
 * - Forward `BGAppRefreshTask` wakeups to [runBackgroundCatchUp].
 */
object AppMobileSyncIos {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private var appDataHolder: AppDataHolder? = null

  /** Optional Swift bridge backed by `FirebaseMessaging` for topic subscribe/unsubscribe calls. */
  var topicBridge: IosPushTopicBridge?
    get() = IosPushSubscriptionRegistry.bridge
    set(value) {
      IosPushSubscriptionRegistry.bridge = value
    }

  /**
   * Returns the shared [AppDataHolder] for the configured `USER_DATA_SERVICE_URL`, starting its
   * Koin graph (with the local database) on first access.
   */
  fun getOrCreateDataHolder(): AppDataHolder {
    val existing = appDataHolder
    if (existing != null) return existing
    val platform = platformModule(localDatabase = { NativeSqlDriverFactory() })
    return startKoin { modules(mobileModules(requireUserDataServiceUrl(), platform)) }
      .koin
      .get<AppDataHolder>()
      .also { appDataHolder = it }
  }

  private fun getOrCreateDataHolderIfConfigured(): AppDataHolder? {
    if (appDataHolder == null && platformUserDataServiceUrl() == null) return null
    return runCatching { getOrCreateDataHolder() }.getOrNull()
  }

  /**
   * Handles a silent remote notification payload containing `survey_id`, `scope` (`"def"` or
   * `"entities"`), and optional `record_version` ([parseSyncTickle]), executing a scoped delta
   * catch-up and invoking [onComplete] with whether it succeeded, so UIKit's
   * `fetchCompletionHandler` can report a failed fetch (as Android's `ScopedSyncWorker` retries).
   */
  fun handleRemoteNotification(userInfo: Map<String, String>, onComplete: (Boolean) -> Unit) {
    val tickle = parseSyncTickle(userInfo)
    val coordinator = getOrCreateDataHolderIfConfigured()?.syncCoordinator
    if (tickle == null || coordinator == null) {
      onComplete(false)
      return
    }
    runAndReport(onComplete) { coordinator.syncTickleNow(tickle) }
  }

  /**
   * Runs a full delta catch-up across downloaded surveys (used by `BGAppRefreshTask` wakeups) and
   * calls [onComplete] with whether it succeeded.
   */
  fun runBackgroundCatchUp(onComplete: (Boolean) -> Unit) {
    val coordinator = getOrCreateDataHolderIfConfigured()?.syncCoordinator
    if (coordinator == null) {
      onComplete(false)
      return
    }
    runAndReport(onComplete) {
      coordinator.syncNow()
      coordinator.activity.value.lastError == null
    }
  }

  /**
   * Launches [sync] and reports its result to [onComplete], also when it throws or is cancelled, so
   * the system's completion handler is always called.
   */
  private fun runAndReport(onComplete: (Boolean) -> Unit, sync: suspend () -> Boolean) {
    scope.launch {
      val succeeded =
        try {
          sync()
        } catch (e: CancellationException) {
          onComplete(false)
          throw e
        } catch (_: Exception) {
          false
        }
      onComplete(succeeded)
    }
  }
}
