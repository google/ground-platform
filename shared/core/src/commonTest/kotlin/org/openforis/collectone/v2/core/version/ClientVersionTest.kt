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
package org.openforis.collectone.v2.core.version

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClientVersionTest {

  @Test
  fun parseValidSemVerVariants() {
    assertEquals(AppVersion(2, 1, 3), AppVersion.parseOrNull("2.1.3"))
    assertEquals(AppVersion(2, 1, 3), AppVersion.parseOrNull("v2.1.3"))
    assertEquals(AppVersion(2, 1, 3), AppVersion.parseOrNull(" V2.1.3+build.42 "))
    assertEquals(AppVersion(2, 1, 0), AppVersion.parseOrNull("2.1"))
    assertEquals(AppVersion(2, 0, 0), AppVersion.parseOrNull("2"))
    assertEquals(AppVersion(2, 0, 0, "SNAPSHOT"), AppVersion.parseOrNull("2.0.0-SNAPSHOT"))
    assertEquals(AppVersion(2, 1, 0, "beta.2"), AppVersion.parseOrNull("2.1.0-beta.2"))
  }

  @Test
  fun parseRejectsInvalidVersions() {
    assertNull(AppVersion.parseOrNull(""))
    assertNull(AppVersion.parseOrNull("   "))
    assertNull(AppVersion.parseOrNull("abc"))
    assertNull(AppVersion.parseOrNull("1.2.3.4"))
    assertNull(AppVersion.parseOrNull("1.2."))
    assertNull(AppVersion.parseOrNull("1.2.3-"))
    assertNull(AppVersion.parseOrNull("-1.0.0"))
  }

  @Test
  fun semVerComparisonOrdersCorrectly() {
    val ordered =
      listOf(
          "1.0.0-alpha",
          "1.0.0-alpha.1",
          "1.0.0-alpha.beta",
          "1.0.0-beta",
          "1.0.0-beta.2",
          "1.0.0-beta.11",
          "1.0.0-rc.1",
          "1.0.0",
          "2.0.0-SNAPSHOT",
          "2.0.0",
          "2.0.1",
          "2.1.0",
          "3.0.0",
        )
        .map { requireNotNull(AppVersion.parseOrNull(it)) }

    for (i in 0 until ordered.lastIndex) {
      assertTrue(ordered[i] < ordered[i + 1], "${ordered[i]} should be < ${ordered[i + 1]}")
    }
  }

  @Test
  fun evaluateSuggestsUpdateWhenBelowLatest() {
    val policy =
      MobileClientVersionPolicy(
        latestVersion = "2.1.0",
        minRequiredVersion = "1.5.0",
        updateUrl = "https://example.com/update",
      )
    val status = evaluateMobileClientVersion("2.0.0-SNAPSHOT", policy)
    assertEquals(
      MobileUpdateStatus.Suggested(
        currentVersion = "2.0.0-SNAPSHOT",
        latestVersion = "2.1.0",
        updateUrl = "https://example.com/update",
      ),
      status,
    )
  }

  @Test
  fun evaluateRequiresUpdateWhenBelowMinimum() {
    val policy =
      MobileClientVersionPolicy(
        latestVersion = "2.2.0",
        minRequiredVersion = "2.1.0",
        updateUrl = "https://example.com/update",
      )
    val status = evaluateMobileClientVersion("2.0.0", policy)
    assertEquals(
      MobileUpdateStatus.Required(
        currentVersion = "2.0.0",
        minRequiredVersion = "2.1.0",
        latestVersion = "2.2.0",
        updateUrl = "https://example.com/update",
      ),
      status,
    )
  }

  @Test
  fun evaluateReturnsUpToDateWhenAtOrAboveLatestAndMinimum() {
    val policy = MobileClientVersionPolicy(latestVersion = "2.1.0", minRequiredVersion = "2.0.0")
    assertEquals(MobileUpdateStatus.UpToDate, evaluateMobileClientVersion("2.1.0", policy))
    assertEquals(MobileUpdateStatus.UpToDate, evaluateMobileClientVersion("2.2.0", policy))
  }

  @Test
  fun evaluateReturnsUpToDateWhenPolicyEmptyOrClientUnparseable() {
    assertEquals(
      MobileUpdateStatus.UpToDate,
      evaluateMobileClientVersion("2.0.0", MobileClientVersionPolicy.EMPTY),
    )
    assertEquals(
      MobileUpdateStatus.UpToDate,
      evaluateMobileClientVersion("", MobileClientVersionPolicy(minRequiredVersion = "2.0.0")),
    )
  }

  @Test
  fun policyValidationChecksVersionsAndUrl() {
    assertNull(MobileClientVersionPolicy.EMPTY.validationError())
    assertNull(
      MobileClientVersionPolicy(
          latestVersion = "2.1.0",
          minRequiredVersion = "2.0.0",
          updateUrl = "https://play.google.com/store/apps/details?id=org.openforis.collectone",
        )
        .validationError()
    )
    assertNotNull(MobileClientVersionPolicy(latestVersion = "not-a-version").validationError())
    assertNotNull(MobileClientVersionPolicy(minRequiredVersion = "1.2.3.4").validationError())
    assertNotNull(MobileClientVersionPolicy(updateUrl = "ftp://example.com").validationError())
  }

  @Test
  fun policyResponseHeadersRoundTrip() {
    val policy =
      MobileClientVersionPolicy(
        latestVersion = "2.1.0",
        minRequiredVersion = "2.0.0",
        updateUrl = "https://example.com/app",
      )
    assertEquals(policy, MobileClientVersionPolicy.fromResponseHeaders(policy.toResponseHeaders()))
    assertEquals(
      MobileClientVersionPolicy.EMPTY,
      MobileClientVersionPolicy.fromResponseHeaders(
        MobileClientVersionPolicy.EMPTY.toResponseHeaders()
      ),
    )
    assertNull(MobileClientVersionPolicy.fromResponseHeaders(emptyMap()))
  }
}
