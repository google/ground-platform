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
@file:OptIn(ExperimentalWasmJsInterop::class)

package org.openforis.collectone.v2.app.logging

@JsFun(
  """(client, environment, enabled) => {
    if (typeof window !== 'undefined' && window.CollectOneErrorReporter) {
      window.CollectOneErrorReporter.configure({
        client: client,
        environment: environment,
        enabled: enabled,
      });
    }
  }"""
)
private external fun jsConfigureErrorReporter(
  client: String,
  environment: String,
  enabled: Boolean,
)

@JsFun(
  """() => {
    return typeof window !== 'undefined' &&
      window.CollectOneErrorReporter &&
      Boolean(window.CollectOneErrorReporter.isEnabled());
  }"""
)
private external fun jsIsErrorReporterEnabled(): Boolean

@JsFun(
  """(message) => {
    if (typeof window !== 'undefined' && window.CollectOneErrorReporter) {
      window.CollectOneErrorReporter.logBreadcrumb(message);
    }
  }"""
)
private external fun jsLogBreadcrumb(message: String)

@JsFun(
  """(typeName, message, stackTrace) => {
    if (typeof window !== 'undefined' && window.CollectOneErrorReporter) {
      window.CollectOneErrorReporter.reportError(typeName, message, stackTrace, 'kotlin.logged_error');
    }
  }"""
)
private external fun jsReportError(typeName: String, message: String, stackTrace: String)

@JsFun(
  """(key, value) => {
    if (typeof window !== 'undefined' && window.CollectOneErrorReporter) {
      window.CollectOneErrorReporter.setCustomKey(key, value);
    }
  }"""
)
private external fun jsSetCustomKey(key: String, value: String)

@JsFun(
  """(userId) => {
    if (typeof window !== 'undefined' && window.CollectOneErrorReporter) {
      window.CollectOneErrorReporter.setUserId(userId);
    }
  }"""
)
private external fun jsSetUserId(userId: String)

/**
 * Browser [CrashReporter] backed by `client-error-bridge.js`, which forwards non-fatal Kotlin
 * errors and uncaught browser errors to the Collect One server's `/client-errors` endpoint.
 */
class BrowserCrashReporter(
  client: String,
  environment: String = "",
  enabled: Boolean = true,
) : CrashReporter {
  init {
    runCatching { jsConfigureErrorReporter(client, environment, enabled) }
  }

  override val isEnabled: Boolean
    get() = runCatching { jsIsErrorReporterEnabled() }.getOrDefault(false)

  override fun logBreadcrumb(message: String) {
    if (!isEnabled) return
    runCatching { jsLogBreadcrumb(message) }
  }

  override fun recordException(throwable: Throwable, message: String?) {
    if (!isEnabled) return
    val typeName =
      (throwable as? RedactedCrashException)?.originalType
        ?: throwable::class.simpleName
        ?: "Throwable"
    val detail = message ?: throwable.message ?: typeName
    val stackTrace =
      (throwable as? RedactedCrashException)?.redactedStackTrace ?: throwable.stackTraceToString()
    runCatching { jsReportError(typeName, detail, stackTrace) }
  }

  override fun setCustomKey(key: String, value: String) {
    if (!isEnabled) return
    runCatching { jsSetCustomKey(key, value) }
  }

  override fun setUserId(userId: String?) {
    if (!isEnabled) return
    runCatching { jsSetUserId(userId.orEmpty()) }
  }
}
