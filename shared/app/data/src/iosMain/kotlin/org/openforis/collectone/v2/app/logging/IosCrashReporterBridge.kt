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
package org.openforis.collectone.v2.app.logging

import kotlin.concurrent.Volatile

/**
 * Swift-compatible bridge for Firebase Crashlytics on iOS.
 *
 * Because the Firebase iOS SDK (`FirebaseCrashlytics`) is linked by the Swift host (`iosApp`), the
 * host implements [IosCrashReporterBridge] in Swift and assigns it to
 * [IosCrashReporterRegistry.bridge] at launch. When no bridge is registered (or
 * `GoogleService-Info.plist` is absent), [isEnabled] is `false` and reporting is a no-op.
 */
interface IosCrashReporterBridge {
  val isEnabled: Boolean

  fun logBreadcrumb(message: String)

  fun recordException(typeName: String, message: String, stackTrace: String)

  fun setCustomKey(key: String, value: String)

  fun setUserId(userId: String?)
}

/** Registry where `iosApp` plugs in its [IosCrashReporterBridge] at launch. */
object IosCrashReporterRegistry {
  @Volatile var bridge: IosCrashReporterBridge? = null
}

/** [CrashReporter] that delegates to the Swift [IosCrashReporterBridge] registered at launch. */
class IosBridgedCrashReporter : CrashReporter {
  override val isEnabled: Boolean
    get() = IosCrashReporterRegistry.bridge?.isEnabled == true

  override fun logBreadcrumb(message: String) {
    IosCrashReporterRegistry.bridge?.logBreadcrumb(message)
  }

  override fun recordException(throwable: Throwable, message: String?) {
    val active = IosCrashReporterRegistry.bridge ?: return
    val typeName =
      (throwable as? RedactedCrashException)?.originalType
        ?: throwable::class.simpleName
        ?: "Throwable"
    val detail = message ?: throwable.message ?: typeName
    val stackTrace =
      (throwable as? RedactedCrashException)?.redactedStackTrace ?: throwable.stackTraceToString()
    active.recordException(typeName = typeName, message = detail, stackTrace = stackTrace)
  }

  override fun setCustomKey(key: String, value: String) {
    IosCrashReporterRegistry.bridge?.setCustomKey(key, value)
  }

  override fun setUserId(userId: String?) {
    IosCrashReporterRegistry.bridge?.setUserId(userId)
  }
}
