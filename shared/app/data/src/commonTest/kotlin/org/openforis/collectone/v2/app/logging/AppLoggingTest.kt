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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [configureAppLogging]: which messages reach the platform's log, and in what form. */
class AppLoggingTest {
  private val log = Logger.withTag("AppLoggingTest")

  @Test
  fun productionBuildsLogInfoAndAbove() {
    val logged =
      recordAppLogs(isDevelopmentBuild = false) {
        log.d { "debug" }
        log.i { "info" }
        log.w { "warning" }
        log.e { "error" }
      }

    assertEquals(listOf("info", "warning", "error"), logged.map { it.message })
  }

  @Test
  fun developmentBuildsLogDebugAndAbove() {
    val logged =
      recordAppLogs(isDevelopmentBuild = true) {
        log.v { "verbose" }
        log.d { "debug" }
      }

    assertEquals(listOf("debug"), logged.map { it.message })
  }

  @Test
  fun keepsTheSeverityAndTag() {
    val logged = recordAppLogs { log.w { "Sync failed" } }

    assertEquals(Severity.Warn, logged.single().severity)
    assertEquals("AppLoggingTest", logged.single().tag)
  }

  @Test
  fun removesEmailAddresses() {
    val logged = recordAppLogs { log.w { "No access for ada.lovelace+test@example.org" } }

    assertEquals("No access for <email>", logged.single().message)
  }

  @Test
  fun removesBearerTokensAndJsonWebTokens() {
    val jwt = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMjMifQ.c2lnbmF0dXJl"

    val logged = recordAppLogs {
      log.w { "Rejected Authorization: Bearer abc.DEF-123_xyz" }
      log.w { "Rejected token $jwt" }
    }

    assertEquals(
      listOf("Rejected Authorization: Bearer <token>", "Rejected token <token>"),
      logged.map { it.message },
    )
  }

  @Test
  fun appendsTheRedactedExceptionToTheMessage() {
    val error = IllegalStateException("PERMISSION_DENIED for ada@example.org")

    val logged = recordAppLogs { log.w(error) { "Couldn't sync" } }

    val message = logged.single().message
    assertTrue(message.startsWith("Couldn't sync\n"), message)
    assertTrue("IllegalStateException: PERMISSION_DENIED for <email>" in message, message)
    assertFalse("ada@example.org" in message, message)
    assertNull(logged.single().throwable)
  }

  @Test
  fun leavesMessagesWithoutSecretsAsTheyAre() {
    val message = "Couldn't send 2 outbox mutation(s) to Survey(id=survey-1): status 14"

    assertEquals(message, redactSecrets(message))
  }
}
