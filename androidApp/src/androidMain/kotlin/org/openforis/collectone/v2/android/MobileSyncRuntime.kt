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
package org.openforis.collectone.v2.android

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import java.io.File
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.openforis.collectone.v2.app.client.push.AndroidPushSubscriptionRegistry
import org.openforis.collectone.v2.app.client.push.PushSubscriptionClient
import org.openforis.collectone.v2.app.client.storage.AndroidSqlDriverFactory
import org.openforis.collectone.v2.app.client.storage.SYNC_STATE_FILE_PROPERTY
import org.openforis.collectone.v2.app.di.AppDataHolder
import org.openforis.collectone.v2.app.di.platformModule
import org.openforis.collectone.v2.app.logging.configureAppLogging
import org.openforis.collectone.v2.mobile.di.mobileModules

/**
 * Process-wide owner of the Android app's Koin graph ([mobileModules], started as the global Koin
 * application) and Firebase Cloud Messaging [PushSubscriptionClient], so [MainActivity],
 * [CollectOneFirebaseMessagingService], and [ScopedSyncWorker] operate on the same local store and
 * [SyncCoordinator].
 */
object MobileSyncRuntime {
  private val lock = Any()
  private var appDataHolder: AppDataHolder? = null

  /**
   * Configures logging (debug level in debuggable builds, info level otherwise), initializes system
   * properties from Android resources/files, and registers the Firebase topic subscription client
   * when a [FirebaseApp] is configured. Every entry point calls it, since any of them (the
   * activity, a push, or a sync worker) may be the first to run in a new process; calling it again
   * is harmless.
   */
  fun initialize(context: Context) {
    val appContext = context.applicationContext
    val isDevelopmentBuild =
      appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val token = appContext.getString(R.string.mapbox_access_token).trim()
    if (token.isNotEmpty()) {
      System.setProperty("collectone.mapbox.accessToken", token)
    }
    val userDataServiceUrl = appContext.getString(R.string.user_data_service_url).trim()
    if (userDataServiceUrl.isNotEmpty()) {
      System.setProperty("collectone.userDataServiceUrl", userDataServiceUrl)
    }
    val appEnvironment = appContext.getString(R.string.app_environment).trim()
    if (appEnvironment.isNotEmpty()) {
      System.setProperty("collectone.environment", appEnvironment)
    }
    // Older versions kept the sync state in this file; the first launch moves it into the local
    // database and deletes it.
    System.setProperty(
      SYNC_STATE_FILE_PROPERTY,
      File(appContext.filesDir, "sync_state.binpb").absolutePath,
    )
    ensureFirebaseInitialized(appContext)
    val crashlyticsEnabled =
      resolveCrashlyticsCollectionEnabled(
        isDevelopmentBuild = isDevelopmentBuild,
        overrideFlag = appContext.getString(R.string.crashlytics_enabled),
      )
    val crashReporter = FirebaseCrashlyticsReporter(appContext, crashlyticsEnabled)
    crashReporter.configure()
    crashReporter.setCustomKey("client_platform", "android")
    val appVersion = appContext.getString(R.string.app_version_name).trim()
    if (appVersion.isNotEmpty()) {
      crashReporter.setCustomKey("app_version", appVersion)
    }
    if (appEnvironment.isNotEmpty()) {
      crashReporter.setCustomKey("environment", appEnvironment)
    }
    configureAppLogging(isDevelopmentBuild = isDevelopmentBuild, crashReporter = crashReporter)
    synchronized(lock) {
      (appDataHolder?.pushSubscriptionClient as? AndroidPushSubscriptionRegistry)?.delegate =
        FirebasePushSubscriptionClient(appContext)
    }
  }

  private fun ensureFirebaseInitialized(appContext: Context) {
    runCatching {
      if (FirebaseApp.getApps(appContext).isNotEmpty()) return
      val rawConfig = appContext.getString(R.string.firebase_web_config).trim()
      if (rawConfig.isEmpty() || rawConfig == "none") return
      val json = JSONObject(rawConfig)
      val appId = json.optString("appId").trim()
      val apiKey = json.optString("apiKey").trim()
      val projectId = json.optString("projectId").trim()
      if (appId.isEmpty() || apiKey.isEmpty() || projectId.isEmpty()) return
      val senderId =
        json.optString("messagingSenderId").trim().ifEmpty { json.optString("gcmSenderId").trim() }
      val storageBucket = json.optString("storageBucket").trim()
      val builder =
        FirebaseOptions.Builder().setApplicationId(appId).setApiKey(apiKey).setProjectId(projectId)
      if (senderId.isNotEmpty()) {
        builder.setGcmSenderId(senderId)
      }
      if (storageBucket.isNotEmpty()) {
        builder.setStorageBucket(storageBucket)
      }
      FirebaseApp.initializeApp(appContext, builder.build())
    }
  }

  /**
   * Returns the shared [AppDataHolder] for [serviceUrl], starting its Koin graph if needed (and
   * replacing the graph of another URL). Returns `null` when no user data service URL is
   * configured.
   */
  fun getOrCreateDataHolder(context: Context, serviceUrl: String? = null): AppDataHolder? {
    initialize(context)
    synchronized(lock) {
      val resolvedUrl =
        serviceUrl?.trim()?.takeIf { it.isNotEmpty() }
          ?: System.getProperty("collectone.userDataServiceUrl")?.trim()?.takeIf {
            it.isNotEmpty()
          }
          ?: return appDataHolder
      val existing = appDataHolder
      if (existing != null && existing.userDataServiceUrl == resolvedUrl) {
        return existing
      }
      // Stopping the old graph cancels its sync engine and closes its database (the Koin
      // modules' onClose hooks) before the new graph opens the same database.
      if (existing != null) stopKoin()
      val appContext = context.applicationContext
      val platform =
        platformModule(
          pushSubscriptionClient = {
            AndroidPushSubscriptionRegistry(FirebasePushSubscriptionClient(appContext))
          },
          localDatabase = { AndroidSqlDriverFactory(appContext) },
        )
      return startKoin { modules(mobileModules(resolvedUrl, platform)) }
        .koin
        .get<AppDataHolder>()
        .also { appDataHolder = it }
    }
  }

  /** Whether the application process currently has any Activity in the `STARTED` state. */
  fun isAppInForeground(): Boolean = runCatching {
    ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
  }
    .getOrDefault(false)
}

/**
 * Android [PushSubscriptionClient] backed by [FirebaseMessaging]. Degrades gracefully to
 * `isAvailable = false` when no [FirebaseApp] is configured on the device, allowing the sync engine
 * to fall back to foreground polling.
 */
private class FirebasePushSubscriptionClient(private val context: Context) :
  PushSubscriptionClient {
  override val isAvailable: Boolean
    get() = runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

  override suspend fun subscribe(topic: String) {
    if (!isAvailable) return
    FirebaseMessaging.getInstance().subscribeToTopic(topic).await()
  }

  override suspend fun unsubscribe(topic: String) {
    if (!isAvailable) return
    FirebaseMessaging.getInstance().unsubscribeFromTopic(topic).await()
  }
}
