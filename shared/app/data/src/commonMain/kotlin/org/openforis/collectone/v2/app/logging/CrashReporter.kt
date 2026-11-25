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

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import kotlin.concurrent.Volatile

/**
 * Platform sink for crash and non-fatal error reporting across Collect One clients:
 * - Android (`androidApp`): Firebase Crashlytics.
 * - iOS (`iosApp`): Firebase Crashlytics via [IosCrashReporterBridge].
 * - Web (`webApp`, `mobileWebApp`, `balloonFormApp`): browser error bridge posting to the Collect
 *   One server's `/client-errors` endpoint (which logs structured events for Cloud Error
 *   Reporting).
 *
 * Implementations degrade gracefully (`isEnabled = false`) when crash reporting is unconfigured.
 */
interface CrashReporter {
  /** Whether crash and non-fatal error collection is currently active. */
  val isEnabled: Boolean

  /** Appends a short, secret-redacted breadcrumb to the next crash or non-fatal report. */
  fun logBreadcrumb(message: String)

  /** Records a handled non-fatal exception with an optional redacted context [message]. */
  fun recordException(throwable: Throwable, message: String? = null)

  /** Attaches a string metadata pair (e.g. `environment`, `app_version`) to future reports. */
  fun setCustomKey(key: String, value: String)

  /**
   * Associates future reports with an opaque user identifier (never a raw email address), or clears
   * it when `null` on sign-out.
   */
  fun setUserId(userId: String?)
}

/** Default [CrashReporter] that discards all events when no platform reporter is wired. */
object NoOpCrashReporter : CrashReporter {
  override val isEnabled: Boolean = false

  override fun logBreadcrumb(message: String) = Unit

  override fun recordException(throwable: Throwable, message: String?) = Unit

  override fun setCustomKey(key: String, value: String) = Unit

  override fun setUserId(userId: String?) = Unit
}

/**
 * Exception wrapper whose message is redacted of emails and tokens while retaining the original
 * exception type name and redacted stack trace for crash grouping.
 */
class RedactedCrashException(
  val originalType: String,
  redactedMessage: String,
  val redactedStackTrace: String,
) : RuntimeException("$originalType: $redactedMessage")

/**
 * Process-wide holder for the active [CrashReporter], set by [configureAppLogging] at startup. All
 * helper methods redact secrets before forwarding and swallow reporter failures so telemetry never
 * disrupts the app.
 */
object CrashReporting {
  @Volatile var reporter: CrashReporter = NoOpCrashReporter

  fun logBreadcrumb(message: String) {
    val active = reporter
    if (!active.isEnabled) return
    runCatching { active.logBreadcrumb(redactSecrets(message)) }
  }

  fun recordException(throwable: Throwable, message: String? = null) {
    val active = reporter
    if (!active.isEnabled) return
    val redactedMsg = message?.let(::redactSecrets)
    val sanitized = sanitizeThrowable(throwable, redactedMsg)
    runCatching { active.recordException(sanitized, redactedMsg) }
  }

  fun setCustomKey(key: String, value: String) {
    val active = reporter
    if (!active.isEnabled) return
    runCatching { active.setCustomKey(key, redactSecrets(value)) }
  }

  fun setUserId(userId: String?) {
    val active = reporter
    if (!active.isEnabled) return
    runCatching { active.setUserId(userId?.let(::redactSecrets)) }
  }
}

/**
 * Kermit [LogWriter] that forwards [Severity.Info] and [Severity.Warn] messages as breadcrumbs and
 * [Severity.Error] / [Severity.Assert] logs as non-fatal exceptions to [reporter], after stripping
 * email addresses, bearer tokens, and JWTs via [redactSecrets].
 */
class CrashReportingLogWriter(private val reporter: CrashReporter) : LogWriter() {
  override fun isLoggable(tag: String, severity: Severity): Boolean =
    reporter.isEnabled && severity.ordinal >= Severity.Info.ordinal

  override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
    if (!isLoggable(tag, severity)) return
    val redactedMessage = redactSecrets(message)
    val breadcrumb = "[${severity.name}/$tag] $redactedMessage"
    runCatching { reporter.logBreadcrumb(breadcrumb) }
    if (severity.ordinal >= Severity.Error.ordinal) {
      val exception =
        if (throwable != null) {
          sanitizeThrowable(throwable, redactedMessage)
        } else {
          RedactedCrashException(
            originalType = "LoggedError($tag)",
            redactedMessage = redactedMessage,
            redactedStackTrace = "",
          )
        }
      runCatching { reporter.recordException(exception, redactedMessage) }
    }
  }
}

private fun sanitizeThrowable(throwable: Throwable, fallbackMessage: String?): Throwable {
  if (throwable is RedactedCrashException) return throwable
  val typeName = throwable::class.simpleName ?: "Throwable"
  val rawMessage = throwable.message ?: fallbackMessage ?: typeName
  return RedactedCrashException(
    originalType = typeName,
    redactedMessage = redactSecrets(rawMessage),
    redactedStackTrace = redactSecrets(throwable.stackTraceToString()),
  )
}
