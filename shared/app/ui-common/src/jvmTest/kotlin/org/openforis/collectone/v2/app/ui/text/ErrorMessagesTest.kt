/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.app.ui.text

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.openforis.collectone.v2.app.domain.error.DomainError

class ErrorMessagesTest {
  private val everyError =
    listOf(
      DomainError.Network,
      DomainError.Unauthenticated,
      DomainError.PermissionDenied,
      DomainError.NotFound,
      DomainError.Conflict,
      DomainError.NotEditable,
      DomainError.Validation,
      DomainError.QuotaExceeded,
      DomainError.Cancelled,
      DomainError.Unknown(IllegalStateException("internal detail")),
    )

  private fun englishReason(error: DomainError) = runBlocking {
    ErrorMessages.reason(error).resolveString()
  }

  @Test
  fun reason_explainsNetworkFailures() {
    assertEquals(
      "Couldn't connect. Check your internet connection and try again.",
      englishReason(DomainError.Network),
    )
  }

  @Test
  fun reason_explainsChangesToSomethingThatNoLongerAcceptsThem() {
    assertEquals(
      "This can't be changed right now. The survey may be closed or archived.",
      englishReason(DomainError.NotEditable),
    )
  }

  @Test
  fun reason_neverShowsTheCauseOfUnknownFailures() {
    assertEquals(
      "Something went wrong. Try again.",
      englishReason(DomainError.Unknown(IllegalStateException("internal detail"))),
    )
  }

  @Test
  fun reason_isDistinctForEveryError() {
    val reasons = everyError.map(::englishReason)

    assertEquals(reasons.size, reasons.toSet().size, reasons.joinToString("\n"))
  }

  @Test
  fun signInFailed_givesTheReason() {
    val notice = runBlocking {
      ErrorMessages.signInFailed(DomainError.PermissionDenied).resolveString()
    }

    assertEquals("Couldn't sign in. You don't have permission to do this.", notice)
  }

  @Test
  fun everyLocale_translatesEveryErrorMessage() {
    val resources = File("../resources/src/commonMain/composeResources")
    assertTrue(resources.isDirectory, "Run from the shared/app project directory")
    val english = stringNames(File(resources, "values/$FILE_NAME"))
    val locales = resources.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }!!

    assertTrue(locales.isNotEmpty())
    locales.forEach { locale ->
      assertEquals(english, stringNames(File(locale, FILE_NAME)), locale.name)
    }
  }

  private fun stringNames(file: File): Set<String> =
    Regex("""<string name="([^"]+)">""").findAll(file.readText()).map { it.groupValues[1] }.toSet()

  private companion object {
    const val FILE_NAME = "strings_app_errors.xml"
  }
}
