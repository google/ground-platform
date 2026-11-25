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
package org.openforis.collectone.v2.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.domain.model.AppBuildConfig
import org.openforis.collectone.v2.app.domain.model.AppEnvironment

@OptIn(ExperimentalTestApi::class)
class DemoBrandingTest {
  @Test
  fun formatAppVersionBadgeAddsSinglePrefixAndTrimsBlank() {
    assertEquals("v2.0.0-SNAPSHOT", formatAppVersionBadge("2.0.0-SNAPSHOT"))
    assertEquals("v2.0.0-SNAPSHOT", formatAppVersionBadge("  2.0.0-SNAPSHOT  "))
    assertEquals("v2.1.0", formatAppVersionBadge("v2.1.0"))
    assertEquals("V2.1.0", formatAppVersionBadge("V2.1.0"))
    assertEquals("", formatAppVersionBadge(""))
    assertEquals("", formatAppVersionBadge("   "))
  }

  @Test
  fun demoBadgeAndAttributionFooterRenderExpectedVersionAndBugIconInDevAndStaging() =
    runDesktopComposeUiTest {
      assertEquals("DEMO", DEMO_BADGE_TEXT)
      assertEquals("Admin only", ADMIN_ONLY_BADGE_TEXT)
      assertEquals(2026, DEMO_ATTRIBUTION_COPYRIGHT_YEAR)
      assertEquals("The Collect One Contributors", DEMO_ATTRIBUTION_CONTRIBUTORS_LABEL)
      assertEquals(
        "https://github.com/google/ground-platform/graphs/contributors?all=1",
        DEMO_ATTRIBUTION_CONTRIBUTORS_URL,
      )
      assertEquals("© 2026 The Collect One Contributors", DEMO_ATTRIBUTION_FOOTER_TEXT)
      assertEquals("Debugging tools", DEV_TOOLS_FOOTER_CONTENT_DESCRIPTION)
      assertTrue(AppBuildConfig.APP_VERSION.isNotBlank())

      var devToolsClicks = 0
      val expectedVersion = formatAppVersionBadge(AppBuildConfig.APP_VERSION)
      setContent {
        MaterialTheme {
          Column {
            DemoBadge()
            AdminOnlyBadge()
            DemoAttributionFooterBar(environment = AppEnvironment.STAGING) { devToolsClicks++ }
          }
        }
      }

      onNodeWithText("DEMO").assertExists()
      onNodeWithContentDescription("Admin only").assertExists()
      onNodeWithText("© 2026 The Collect One Contributors").assertExists()
      onNodeWithText("Dev tools").assertDoesNotExist()
      onNodeWithText(expectedVersion).assertExists()
      onNodeWithContentDescription(DEV_TOOLS_FOOTER_CONTENT_DESCRIPTION).assertExists()
      onNodeWithTag(DEV_TOOLS_FOOTER_BUTTON_TAG).assertExists().performClick()
      assertEquals(1, devToolsClicks)
    }

  @Test
  fun devToolsTriggerStillRendersBugIconWhenVersionIsBlank() = runDesktopComposeUiTest {
    var devToolsClicks = 0
    setContent {
      MaterialTheme {
        DemoAttributionFooterBar(
          environment = AppEnvironment.DEV,
          appVersion = "",
          onOpenDevTools = { devToolsClicks++ },
        )
      }
    }

    onNodeWithTag(DEV_TOOLS_FOOTER_BUTTON_TAG).assertExists()
    onNodeWithContentDescription(DEV_TOOLS_FOOTER_CONTENT_DESCRIPTION).assertExists().performClick()
    assertEquals(1, devToolsClicks)
  }

  @Test
  fun attributionFooterIsHiddenInProdEnvironment() = runDesktopComposeUiTest {
    val expectedVersion = formatAppVersionBadge(AppBuildConfig.APP_VERSION)
    setContent {
      CompositionLocalProvider(LocalAppEnvironment provides AppEnvironment.PROD) {
        MaterialTheme {
          Column {
            DemoAttributionFooterBar(onOpenDevTools = {})
          }
        }
      }
    }

    onNodeWithText("© 2026 The Collect One Contributors").assertDoesNotExist()
    onNodeWithText(expectedVersion).assertDoesNotExist()
    onNodeWithTag(DEV_TOOLS_FOOTER_BUTTON_TAG).assertDoesNotExist()
    onNodeWithContentDescription(DEV_TOOLS_FOOTER_CONTENT_DESCRIPTION).assertDoesNotExist()
  }
}
