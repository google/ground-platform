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

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrashReportingTest {
  private val log = Logger.withTag("CrashReportingTest")

  @AfterTest
  fun restoreDefaults() {
    CrashReporting.reporter = NoOpCrashReporter
    Logger.setMinSeverity(Severity.Verbose)
    Logger.setLogWriters(platformLogWriter())
  }

  @Test
  fun recordsInfoAndWarnAsBreadcrumbsAndErrorsAsNonFatalExceptions() {
    val fake = FakeCrashReporter(isEnabled = true)
    configureAppLogging(
      isDevelopmentBuild = true,
      writer = RecordingLogWriter(),
      crashReporter = fake,
    )

    log.d { "debug message" }
    log.i { "sync started" }
    log.w { "retrying sync" }
    log.e(IllegalStateException("boom")) { "sync failed" }

    assertEquals(
      listOf(
        "[Info/CrashReportingTest] sync started",
        "[Warn/CrashReportingTest] retrying sync",
        "[Error/CrashReportingTest] sync failed",
      ),
      fake.breadcrumbs,
    )
    assertEquals(1, fake.recordedExceptions.size)
    val recorded = fake.recordedExceptions.single()
    assertTrue(recorded.first.message?.contains("IllegalStateException: boom") == true)
    assertEquals("sync failed", recorded.second)
  }

  @Test
  fun recordsErrorWithoutThrowableAsSyntheticNonFatalException() {
    val fake = FakeCrashReporter(isEnabled = true)
    configureAppLogging(
      isDevelopmentBuild = false,
      writer = RecordingLogWriter(),
      crashReporter = fake,
    )

    log.e { "unexpected null state" }

    assertEquals(1, fake.recordedExceptions.size)
    val exception = fake.recordedExceptions.single().first as RedactedCrashException
    assertEquals("LoggedError(CrashReportingTest)", exception.originalType)
    assertTrue(exception.message?.contains("unexpected null state") == true)
  }

  @Test
  fun redactsEmailsAndTokensFromBreadcrumbsExceptionsAndCustomKeys() {
    val fake = FakeCrashReporter(isEnabled = true)
    configureAppLogging(
      isDevelopmentBuild = false,
      writer = RecordingLogWriter(),
      crashReporter = fake,
    )
    val jwt = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMjMifQ.c2lnbmF0dXJl"

    log.i { "Signed in as ada@example.org with Bearer secret123" }
    log.e(IllegalArgumentException("Denied for ada@example.org ($jwt)")) {
      "Auth error for ada@example.org"
    }
    CrashReporting.setCustomKey("context", "owner=ada@example.org")

    assertEquals(
      "[Info/CrashReportingTest] Signed in as <email> with Bearer <token>",
      fake.breadcrumbs[0],
    )
    val recorded = fake.recordedExceptions.single()
    val exception = recorded.first as RedactedCrashException
    assertFalse(exception.message.orEmpty().contains("ada@example.org"))
    assertFalse(exception.message.orEmpty().contains(jwt))
    assertFalse(exception.redactedStackTrace.contains("ada@example.org"))
    assertEquals("Auth error for <email>", recorded.second)
    assertEquals("owner=<email>", fake.customKeys["context"])
  }

  @Test
  fun disabledReporterReceivesNothing() {
    val fake = FakeCrashReporter(isEnabled = false)
    configureAppLogging(
      isDevelopmentBuild = false,
      writer = RecordingLogWriter(),
      crashReporter = fake,
    )

    log.i { "hello" }
    log.e(IllegalStateException("fail")) { "oops" }
    CrashReporting.setCustomKey("env", "prod")
    CrashReporting.setUserId("user-1")

    assertTrue(fake.breadcrumbs.isEmpty())
    assertTrue(fake.recordedExceptions.isEmpty())
    assertTrue(fake.customKeys.isEmpty())
  }

  @Test
  fun swallowsReporterFailuresSoTelemetryNeverCrashesApp() {
    val broken =
      object : CrashReporter {
        override val isEnabled: Boolean = true

        override fun logBreadcrumb(message: String) = error("breadcrumb failure")

        override fun recordException(throwable: Throwable, message: String?) =
          error("record failure")

        override fun setCustomKey(key: String, value: String) = error("key failure")

        override fun setUserId(userId: String?) = error("user failure")
      }
    configureAppLogging(
      isDevelopmentBuild = false,
      writer = RecordingLogWriter(),
      crashReporter = broken,
    )

    log.i { "info" }
    log.e(IllegalStateException("err")) { "error" }
    CrashReporting.setCustomKey("k", "v")
    CrashReporting.setUserId("u")
  }
}

private class FakeCrashReporter(override val isEnabled: Boolean) : CrashReporter {
  val breadcrumbs = mutableListOf<String>()
  val recordedExceptions = mutableListOf<Pair<Throwable, String?>>()
  val customKeys = mutableMapOf<String, String>()
  var lastUserId: String? = null

  override fun logBreadcrumb(message: String) {
    breadcrumbs += message
  }

  override fun recordException(throwable: Throwable, message: String?) {
    recordedExceptions += throwable to message
  }

  override fun setCustomKey(key: String, value: String) {
    customKeys[key] = value
  }

  override fun setUserId(userId: String?) {
    lastUserId = userId
  }
}
