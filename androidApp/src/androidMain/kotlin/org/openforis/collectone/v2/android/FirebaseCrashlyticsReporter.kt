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
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.openforis.collectone.v2.app.logging.CrashReporter

/**
 * Android [CrashReporter] backed by [FirebaseCrashlytics].
 *
 * Degrades gracefully to `isEnabled = false` when no [FirebaseApp] is initialized on the device
 * (for example, local builds without `google-services.json` or `FIREBASE_WEB_CONFIG`), and keeps
 * collection disabled in debuggable builds unless explicitly enabled via
 * `CRASHLYTICS_ENABLED=true`.
 */
class FirebaseCrashlyticsReporter(
  private val context: Context,
  private val collectionEnabled: Boolean,
) : CrashReporter {
  override val isEnabled: Boolean
    get() = collectionEnabled && isFirebaseInitialized()

  /** Applies [collectionEnabled] to the Crashlytics SDK when Firebase is initialized. */
  fun configure() {
    if (!isFirebaseInitialized()) return
    runCatching {
      FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = collectionEnabled
    }
  }

  override fun logBreadcrumb(message: String) {
    if (!isEnabled) return
    runCatching { FirebaseCrashlytics.getInstance().log(message) }
  }

  override fun recordException(throwable: Throwable, message: String?) {
    if (!isEnabled) return
    runCatching {
      val crashlytics = FirebaseCrashlytics.getInstance()
      if (!message.isNullOrBlank()) {
        crashlytics.log(message)
      }
      crashlytics.recordException(throwable)
    }
  }

  override fun setCustomKey(key: String, value: String) {
    if (!isEnabled) return
    runCatching { FirebaseCrashlytics.getInstance().setCustomKey(key, value) }
  }

  override fun setUserId(userId: String?) {
    if (!isEnabled) return
    runCatching { FirebaseCrashlytics.getInstance().setUserId(userId.orEmpty()) }
  }

  private fun isFirebaseInitialized(): Boolean = runCatching {
    FirebaseApp.getApps(context).isNotEmpty()
  }
    .getOrDefault(false)
}

/**
 * Resolves whether Crashlytics collection should be enabled: an explicit `"true"`/`"false"`
 * override from `CRASHLYTICS_ENABLED` wins; otherwise enabled in release builds and disabled in
 * debuggable development builds.
 */
internal fun resolveCrashlyticsCollectionEnabled(
  isDevelopmentBuild: Boolean,
  overrideFlag: String,
): Boolean =
  when (overrideFlag.trim().lowercase()) {
    "true" -> true
    "false" -> false
    else -> !isDevelopmentBuild
  }
